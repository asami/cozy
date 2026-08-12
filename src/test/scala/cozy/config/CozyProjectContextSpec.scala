package cozy.config

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 12, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyProjectContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyProjectContext" should {
    "discover the nearest direct project marker" which {
      "retain exact project configuration and provenance for a nested package" in {
        _with_work("nearest") { root =>
          Given("a nested package below a project marker without Git metadata")
          val project = root.resolve("project")
          val packagepath = _directory(project.resolve("src/main/media/article"))
          val marker = _write(project.resolve("conf/cozy/config.yaml"), _project_yaml("project-site", ".", "smartdox"))

          When("the shared context is resolved from the package directory")
          val context = CozyProjectContext.resolve(packagepath)

          Then("the nearest marker, project values, ordered layers, and project-root profile are retained")
          context.project.map(_.root.path) shouldBe Some(project.toAbsolutePath.normalize())
          context.project.map(_.marker.path) shouldBe Some(marker)
          context.project.flatMap(_.id.map(_.value)) shouldBe Some("project-site")
          context.project.flatMap(_.kind.map(_.value)) shouldBe Some("smartdox-site")
          context.layers.map(_.name) shouldBe Vector("built-in", "user", "project-conf", "project-local", "package-conf", "package-local")
          val projectmarker = context.project.map(_.marker).get
          val projectfile = context.layers.find(_.name == "project-conf").flatMap(_.files.find(_.path == marker)).get
          (projectmarker eq projectfile) shouldBe true
          projectmarker.sha256 shouldBe projectfile.sha256
          context.publicationProfile("project-site").map(_.resolvedRoot) shouldBe Some(project.toAbsolutePath.normalize())
          context.valueProvenance("video.credits.default-profile").map(_.sourcePath) shouldBe Some(marker)
        }
      }

      "preserve standalone package and user configuration when no marker exists" in {
        _with_work("standalone") { root =>
          Given("a direct package with a package-local profile and an isolated user home")
          val home = _directory(root.resolve("home"))
          _set_home(home)
          val packagepath = _directory(root.resolve("package"))
          _write(home.resolve(".cozy/config.yaml"), _profile_yaml("user", "user-root", "user-kind"))
          val packageconfig = _write(packagepath.resolve(".cozy/config.yaml"), _profile_yaml("package", "package-root", "package-kind"))

          When("the context is resolved without a project marker")
          val context = CozyProjectContext.resolve(packagepath)

          Then("project absence remains compatible while user and package-local profiles remain effective")
          context.project shouldBe None
          context.layers.map(_.name) shouldBe Vector("built-in", "user", "package-conf", "package-local")
          context.publicationProfile("package").map(_.sourcePath) shouldBe Some(packageconfig)
          context.publicationProfile("user").map(_.baseRoot) shouldBe Some(home)
        }
      }
    }

    "apply deterministic configuration layers" which {
      "select later scalar and complete profile definitions with their exact source" in {
        _with_work("precedence") { root =>
          Given("user, project, and package layers defining one scalar and one complete profile")
          val home = _directory(root.resolve("home"))
          _set_home(home)
          val project = root.resolve("project")
          val packagepath = _directory(project.resolve("nested/package"))
          _write(home.resolve(".cozy/config.yaml"), _profile_yaml("site", "user", "user-kind") + "\nvideo:\n  credits:\n    default-profile: user\n")
          _write(project.resolve("conf/cozy/config.yaml"), _project_yaml("site-project", "project-conf", "project-kind"))
          _write(project.resolve(".cozy/config.yml"), _profile_yaml("site", "project-local", "project-local-kind") + "\nvideo:\n  credits:\n    default-profile: project-local\n")
          _write(packagepath.resolve("conf/cozy/config.yml"), _profile_yaml("site", "package-conf", "package-conf-kind") + "\nvideo:\n  credits:\n    default-profile: package-conf\n")
          val local = _write(packagepath.resolve(".cozy/config.yaml"), _profile_yaml("site", "package-local", "package-local-kind") + "\nvideo:\n  credits:\n    default-profile: package-local\n")

          When("all effective layers are resolved")
          val context = CozyProjectContext.resolve(packagepath)

          Then("the package-local definition wins as a complete definition with source provenance")
          context.value("video.credits.default-profile") shouldBe Some("package-local")
          context.valueProvenance("video.credits.default-profile").map(_.sourcePath) shouldBe Some(local)
          context.publicationProfile("site").map(_.layer) shouldBe Some("package-local")
          context.publicationProfile("site").map(_.sourcePath) shouldBe Some(local)
        }
      }

      "avoid replaying package layers when the package is the project root" in {
        _with_work("same-root") { root =>
          Given("a package directory that is itself the marked project root")
          val project = root.resolve("project")
          _write(project.resolve("conf/cozy/config.yaml"), _project_yaml("site", ".", "smartdox"))

          When("the project root is resolved as a package")
          val context = CozyProjectContext.resolve(project)

          Then("project configuration is not replayed as package configuration")
          context.layers.map(_.name) shouldBe Vector("built-in", "user", "project-conf", "project-local")
        }
      }
    }

    "enforce direct-entry safety and constrained profile grammar" which {
      "reject unsafe package, marker, layer, and supported configuration entries without higher fallback" in {
        _with_work("unsafe") { root =>
          Given("an outer marker and an unsafe nearest marker component")
          val outer = root.resolve("outer")
          val packagepath = _directory(outer.resolve("inner/package"))
          _write(outer.resolve("conf/cozy/config.yaml"), _project_yaml("outer", ".", "outer"))
          Files.createDirectories(outer.resolve("inner/conf/cozy"))
          Files.createSymbolicLink(outer.resolve("inner/conf/cozy/config.yaml"), outer.resolve("conf/cozy/config.yaml"))

          When("discovery reaches the unsafe nearest marker")
          val error = _failure(CozyProjectContext.resolve(packagepath))

          Then("it fails closed and names the unsafe marker instead of selecting the outer project")
          error.getMessage should include("project marker")
          error.getMessage should include("config.yaml")
        }
      }

      "reject duplicate, unknown, incomplete, unsafe, and escaping publication profiles" in {
        _with_work("profiles") { root =>
          Given("a package-local layer with an invalid profile definition")
          val packagepath = _directory(root.resolve("package"))
          val config = _write(packagepath.resolve(".cozy/config.yaml"),
            "media:\n  publication-profiles:\n    bad.id:\n      root: ../escape\n      unexpected: value\n")

          When("the profile grammar is resolved")
          val error = _failure(CozyProjectContext.resolve(packagepath))

          Then("the diagnostic identifies the unsafe profile definition and layer path")
          error.getMessage should include("Unsafe publication profile id")
          error.getMessage should include(config.toString)
        }
      }

      "name duplicate, unknown, missing, escaping, and symlink-backed profile failures" in {
        _with_work("profile-errors") { root =>
          Given("separate direct package layers for each forbidden profile form")
          val duplicate = _directory(root.resolve("duplicate"))
          _write(duplicate.resolve(".cozy/config.yaml"), _profile_yaml("site", "one", "kind"))
          val duplicatefile = _write(duplicate.resolve(".cozy/config.yml"), _profile_yaml("site", "two", "kind"))
          val unknown = _directory(root.resolve("unknown"))
          val unknownfile = _write(unknown.resolve(".cozy/config.yaml"), "media:\n  publication-profiles:\n    site:\n      root: one\n      site-kind: kind\n      extra: no\n")
          val missing = _directory(root.resolve("missing"))
          _write(missing.resolve(".cozy/config.yaml"), "media:\n  publication-profiles:\n    site:\n      root: one\n")
          val escaping = _directory(root.resolve("escaping"))
          _write(escaping.resolve(".cozy/config.yaml"), _profile_yaml("site", "../outside", "kind"))
          val linked = _directory(root.resolve("linked"))
          val realroot = _directory(root.resolve("real-root"))
          Files.createSymbolicLink(linked.resolve("publication"), realroot)
          _write(linked.resolve(".cozy/config.yaml"), _profile_yaml("site", "publication", "kind"))

          When("each invalid layer is resolved")
          val duplicateerror = _failure(CozyProjectContext.resolve(duplicate))
          val unknownerror = _failure(CozyProjectContext.resolve(unknown))
          val missingerror = _failure(CozyProjectContext.resolve(missing))
          val escapingerror = _failure(CozyProjectContext.resolve(escaping))
          val linkederror = _failure(CozyProjectContext.resolve(linked))

          Then("each operator diagnostic names its rejected item and relevant layer or path")
          duplicateerror.getMessage should include("Duplicate publication profile 'site'")
          duplicateerror.getMessage should include(duplicatefile.toString)
          unknownerror.getMessage should include("Unknown publication profile field 'extra'")
          unknownerror.getMessage should include(unknownfile.toString)
          missingerror.getMessage should include("site-kind")
          escapingerror.getMessage should include("escapes its base")
          linkederror.getMessage should include("publication")
        }
      }

      "reject a non-object media container with a source-aware diagnostic" in {
        _with_work("media-container") { root =>
          Given("a package-local layer with a malformed media container")
          val media = _directory(root.resolve("media"))
          val mediafile = _write(media.resolve(".cozy/config.yaml"), "media: invalid\n")

          When("the malformed media container is resolved")
          val mediaerror = _failure(CozyProjectContext.resolve(media))

          Then("the failure names the malformed field, layer, and source")
          mediaerror.getMessage should include("media field must be an object")
          mediaerror.getMessage should include("package-local")
          mediaerror.getMessage should include(mediafile.toString)
        }
      }

      "reject a non-object publication-profile container with a source-aware diagnostic" in {
        _with_work("profile-container") { root =>
          Given("a package-local layer with a malformed publication-profile container")
          val profiles = _directory(root.resolve("profiles"))
          val profilesfile = _write(profiles.resolve(".cozy/config.yaml"), "media:\n  publication-profiles: [invalid]\n")

          When("the malformed publication-profile container is resolved")
          val profileserror = _failure(CozyProjectContext.resolve(profiles))

          Then("the failure names the malformed field, layer, and source")
          profileserror.getMessage should include("media.publication-profiles field must be an object")
          profileserror.getMessage should include("package-local")
          profileserror.getMessage should include(profilesfile.toString)
        }
      }

      "replace complete profiles across layers without fieldwise fabrication" in {
        _with_work("profile-replace") { root =>
          Given("a complete project profile and a complete package replacement")
          val project = root.resolve("project")
          val packagepath = project.resolve("package")
          _write(project.resolve("conf/cozy/config.yaml"), _project_yaml("site", "project-output", "project-kind"))
          val local = _write(packagepath.resolve(".cozy/config.yaml"), _profile_yaml("site", "package-output", "package-kind"))

          When("the same profile id is selected across layers")
          val context = CozyProjectContext.resolve(packagepath)

          Then("the later complete package definition replaces the earlier definition deterministically")
          context.publicationProfile("site").map(_.root) shouldBe Some("package-output")
          context.publicationProfile("site").map(_.siteKind) shouldBe Some("package-kind")
          context.publicationProfile("site").map(_.sourcePath) shouldBe Some(local)
        }
      }
    }

    "retain revalidation evidence" which {
      "accept unchanged evidence and reject configuration, marker, and user-home drift" in {
        _with_work("revalidate") { root =>
          Given("a marked package and a stable user home")
          val home = _directory(root.resolve("home"))
          _set_home(home)
          val project = root.resolve("project")
          val marker = _write(project.resolve("conf/cozy/config.yaml"), _project_yaml("site", ".", "smartdox"))
          val packagepath = _directory(project.resolve("package"))
          val context = CozyProjectContext.resolve(packagepath)

          When("the unchanged snapshot is revalidated")
          val unchanged = CozyProjectContext.revalidate(context)

          Then("the exact context remains equal")
          unchanged shouldBe context

          And("configuration byte drift, atomic file replacement, marker removal, and user-home drift are rejected")
          _write(marker, _project_yaml("site", ".", "changed"))
          _failure(CozyProjectContext.revalidate(context)).getMessage should include("snapshot")
          val context2 = CozyProjectContext.resolve(packagepath)
          val replacement = _write(project.resolve("replacement.yaml"), _project_yaml("site", ".", "changed"))
          Files.move(replacement, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
          _failure(CozyProjectContext.revalidate(context2)).getMessage should include("snapshot")
          val context3 = CozyProjectContext.resolve(packagepath)
          Files.delete(marker)
          _failure(CozyProjectContext.revalidate(context3)).getMessage should include("snapshot")
          _write(marker, _project_yaml("site", ".", "changed"))
          val context4 = CozyProjectContext.resolve(packagepath)
          _set_home(_directory(root.resolve("other-home")))
          _failure(CozyProjectContext.revalidate(context4)).getMessage should include("snapshot")
        }
      }
    }

    "read the fixed supported configuration candidates" which {
      "load exact candidate names in declared order as their native formats without a broad scan" in {
        _with_work("formats") { root =>
          Given("all six supported configuration candidate names and an unrelated file")
          val packagepath = _directory(root.resolve("package"))
          val layer = packagepath.resolve(".cozy")
          val names = Vector("config.yaml", "config.yml", "config.json", "config.conf", "config.hocon", "config.xml")
          names.zipWithIndex.foreach { case (name, index) =>
            _write(layer.resolve(name), _format_body(name, index))
          }
          _write(layer.resolve("ignored.txt"), "video:\n  credits:\n    default-profile: ignored\n")

          When("the package-local layer is resolved")
          val context = CozyProjectContext.resolve(packagepath)

          Then("only supported names are loaded in their exact order and every native contribution is flattened")
          context.layers.find(_.name == "package-local").map(_.files.map(_.path.getFileName.toString)) shouldBe Some(names)
          context.value("formats.yaml") shouldBe Some("value-0")
          context.value("formats.yml") shouldBe Some("value-1")
          context.value("formats.json") shouldBe Some("value-2")
          context.value("formats.conf") shouldBe Some("value-3")
          context.value("formats.hocon") shouldBe Some("value-4")
          context.value("formats.xml") shouldBe Some("value-5")
          context.list("formats.yaml-list") shouldBe Vector("yaml-a", "yaml-b")
          context.list("formats.yml-list") shouldBe Vector("yml-a", "yml-b")
          context.list("formats.json-list") shouldBe Vector("json-a", "json-b")
          context.list("formats.conf-list") shouldBe Vector("conf-a", "conf-b")
          context.list("formats.hocon-list") shouldBe Vector("hocon-a", "hocon-b")
          context.list("formats.xml-list") shouldBe Vector("xml-a", "xml-b")
        }
      }
    }

    "parse captured configuration bytes safely" which {
      "use the supplied source URI as format metadata without rereading it" in {
        Given("captured JSON bytes associated with a nonexistent source URI")
        val bytes = """{"captured": {"value": "from-bytes"}}""".getBytes(StandardCharsets.UTF_8)
        val source = URI.create("file:///definitely-not-present/cozy/config.json")

        When("the captured bytes are parsed")
        val config = CozyProjectYamlConfig.parsePublic(bytes, source)

        Then("the JSON value is available despite the nonexistent URI path")
        config.value("captured.value") shouldBe Some("from-bytes")
      }

      "retain the legacy byte-only YAML behavior" in {
        Given("YAML bytes supplied through the existing overload")
        val bytes = "legacy:\n  value: yaml\n".getBytes(StandardCharsets.UTF_8)

        When("the legacy overload is parsed")
        val config = CozyProjectYamlConfig.parsePublic(bytes)

        Then("the YAML value remains available")
        config.value("legacy.value") shouldBe Some("yaml")
      }

      "reject unsupported and suffixless source identities deterministically" in {
        Given("captured bytes with unsupported and suffixless source URIs")
        val bytes = "value: ignored\n".getBytes(StandardCharsets.UTF_8)

        When("the explicit format identity is not whitelisted")
        val unsupported = _failure(CozyProjectYamlConfig.parsePublic(bytes, URI.create("memory:/runtime.txt")))
        val suffixless = _failure(CozyProjectYamlConfig.parsePublic(bytes, URI.create("memory:/runtime")))

        Then("each diagnostic names the URI and resolved suffix")
        unsupported.getMessage should include("memory:/runtime.txt")
        unsupported.getMessage should include(".txt")
        suffixless.getMessage should include("memory:/runtime")
        suffixless.getMessage should include("<none>")
      }

      "reject external HOCON includes before they can be resolved" in {
        Given("captured HOCON bodies attempting every supported external include form")
        val attempts = Vector(
          "file" -> "include file(\"file:///definitely-not-present/cozy.conf\")",
          "url" -> "include url(\"https://example.invalid/cozy.conf\")",
          "classpath" -> "include classpath(\"cozy.conf\")",
          "bare" -> "include \"cozy.conf\"",
          "required" -> "include required(file(\"file:///definitely-not-present/cozy.conf\"))",
          "nested" -> "outer { include \"cozy.conf\" }",
          "inline" -> "outer { value = safe, include \"cozy.conf\" }"
        )

        When("each captured body is parsed with a nonexistent source URI")
        val errors = attempts.map { case (kind, body) =>
          kind -> _failure(CozyProjectYamlConfig.parsePublic(body.getBytes(StandardCharsets.UTF_8), URI.create("file:///definitely-not-present/cozy/config.conf")))
        }

        Then("each is rejected by the captured-byte policy before a source can be read")
        errors.foreach { case (_, error) =>
          error.getMessage should include("HOCON include is not allowed")
          error.getMessage should include("config.conf")
        }
      }

      "reject same-size in-place mutation between captured file reads" in {
        _with_work("snapshot-race") { root =>
          Given("a direct project marker and a deterministic mutation callback")
          val project = root.resolve("project")
          val initial = _project_yaml("site", ".", "before")
          val replacement = _project_yaml("site", ".", "after!")
          initial.getBytes(StandardCharsets.UTF_8).length shouldBe replacement.getBytes(StandardCharsets.UTF_8).length
          val marker = _write(project.resolve("conf/cozy/config.yaml"), initial)
          val previous = CozyProjectContext._file_snapshot_between_reads
          CozyProjectContext._file_snapshot_between_reads = () =>
            _write(marker, replacement)

          When("project discovery captures the marker while its same-size bytes change")
          val error = try _failure(CozyProjectContext.resolve(_directory(project.resolve("package"))))
          finally CozyProjectContext._file_snapshot_between_reads = previous

          Then("the torn snapshot is rejected rather than used as configuration evidence")
          error.getMessage should include("project marker")
          error.getMessage should include("changed while being read")
        }
      }

      "reject XML document declarations and external entities before they can be resolved" in {
        Given("captured XML with a document declaration or an external entity")
        val doctype = "<!DOCTYPE config SYSTEM \"file:///definitely-not-present/cozy.xml\"><config><value>ignored</value></config>"
        val entity = "<!DOCTYPE config [<!ENTITY forbidden SYSTEM \"file:///definitely-not-present/cozy.xml\">]><config><value>&forbidden;</value></config>"
        val source = URI.create("file:///definitely-not-present/cozy/config.xml")

        When("each captured XML body is parsed")
        val doctypeerror = _failure(CozyProjectYamlConfig.parsePublic(doctype.getBytes(StandardCharsets.UTF_8), source))
        val entityerror = _failure(CozyProjectYamlConfig.parsePublic(entity.getBytes(StandardCharsets.UTF_8), source))

        Then("both are rejected by the captured-byte policy before external resolution")
        doctypeerror.getMessage should include("XML external document constructs are not allowed")
        entityerror.getMessage should include("XML external document constructs are not allowed")
        doctypeerror.getMessage should include("config.xml")
        entityerror.getMessage should include("config.xml")
      }
    }
  }

  private def _project_yaml(id: String, root: String, kind: String): String =
    s"project:\n  id: $id\n  kind: smartdox-site\n" + _profile_yaml(id, root, kind) + s"\nvideo:\n  credits:\n    default-profile: $id\n"

  private def _profile_yaml(id: String, root: String, kind: String): String =
    s"media:\n  publication-profiles:\n    $id:\n      root: $root\n      site-kind: $kind\n"

  private def _format_body(name: String, index: Int): String = name match {
    case "config.yaml" =>
      "formats:\n  yaml: value-0\n  yaml-list:\n    - yaml-a\n    - yaml-b\n"
    case "config.yml" =>
      "formats:\n  yml: value-1\n  yml-list:\n    - yml-a\n    - yml-b\n"
    case "config.json" =>
      """{"formats":{"json":"value-2","json-list":["json-a","json-b"]}}"""
    case "config.conf" =>
      "formats.conf = value-3\nformats.conf-list = [conf-a, conf-b]\n"
    case "config.hocon" =>
      "formats { hocon = value-4, hocon-list = [hocon-a, hocon-b] }\n"
    case "config.xml" =>
      "<config><formats><xml>value-5</xml><xml-list>xml-a</xml-list><xml-list>xml-b</xml-list></formats></config>"
    case _ =>
      throw new IllegalArgumentException(s"Unexpected supported configuration name: $name at index $index")
  }

  private def _directory(path: Path): Path = {
    Files.createDirectories(path)
    path.toAbsolutePath.normalize()
  }

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path.toAbsolutePath.normalize()
  }

  private def _failure[A](body: => A): Throwable = {
    try {
      body
      throw new AssertionError("Expected CozyProjectContext to reject the fixture")
    } catch {
      case e: AssertionError if e.getMessage == "Expected CozyProjectContext to reject the fixture" => throw e
      case NonFatal(e) => e
    }
  }

  private def _set_home(path: Path): Unit = System.setProperty("user.home", path.toString)

  private def _with_work(name: String)(body: Path => Unit): Unit =
    CozyProjectContext.withUserHomeLock {
      val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/cozy-project-context").resolve(name)
      val home = Option(System.getProperty("user.home"))
      _delete(root)
      Files.createDirectories(root)
      try body(root)
      finally {
        home.foreach(System.setProperty("user.home", _))
        if (home.isEmpty) System.clearProperty("user.home")
        _delete(root)
      }
    }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.reverse.foreach(Files.delete)
      finally stream.close()
    }
}
