package cozy.publication

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import javax.imageio.ImageIO
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cozy.bok.CozyBok
import cozy.media.CozyMedia
import play.api.libs.json.{JsArray, JsObject, Json}

/*
 * @since   Aug.  5, 2026
 * @version Aug. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaInfographicCommandSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaInfographicCommand" should {
    "discovery and complete preflight" which {
    "leave an empty descriptor command as an exact registry and artifact no-op" in {
      Given("a BoK source and repository with no exact media descriptor")
      _with_root { root =>
        val source = Files.createDirectories(root.resolve("src/main/doxsite"))
        val repository = Files.createDirectories(root.resolve("repository"))

        When("the explicit infographic command is published")
        val result = CozyArticleMediaInfographicCommand.publish(_config(root))

        Then("it returns no typed result and installs no artifact or registry bundle")
        result shouldBe Vector.empty
        _json_children(root.resolve("src/main/publication")) shouldBe Vector.empty
        _direct_children(repository) shouldBe Vector.empty
        Files.isDirectory(source) shouldBe true
      }
    }

    "publish one exact descriptor as strict and integrity evidence" in {
      Given("one cozy.media.v1 detailed-infographic resource with its exact manifest and repository profile")
      _with_root { root =>
        _media(root, "one", "development-process/example", "ja", "images/example.png", _png(0xff3366cc))

        When("the typed command prepares, commits, and merges the resource")
        val result = CozyArticleMediaInfographicCommand.publish(_config(root))

        Then("the PNG and strict/integrity registry records share the exact resolved public path")
        result.map(_.roleUpdate.integrity.record.artifact.identity) shouldBe Vector("one")
        Files.isRegularFile(root.resolve("repository/images/example.png")) shouldBe true
        _entry_paths(root.resolve("src/main/publication/article-media.json")) shouldBe Vector(
          "metadata/article-media-integrity/development-process/example/ja/infographic.json",
          "metadata/article-media/development-process/example.json"
        )
      }
    }

    "commit the exact prevalidated plan without rediscovering newly appearing descriptors" in {
      Given("one valid descriptor and an immutable plan produced before a different malformed candidate appears")
      _with_root { root =>
        _media(root, "prepared", "development-process/prepared", "ja", "images/prepared.png", _png(0xff3366cc))
        val config = _config(root)
        val plan = CozyArticleMediaInfographicCommand.plan(config)
        val appeared = Files.createDirectories(root.resolve("src/main/doxsite/appeared"))
        Files.write(appeared.resolve("media.json"), "{}".getBytes(StandardCharsets.UTF_8))

        When("the package-private commit boundary receives that prepared plan")
        val result = CozyArticleMediaInfographicCommand.commit(config, plan)

        Then("it installs only the original validated evidence without rediscovering or reparsing the newly appearing candidate")
        result.map(_.roleUpdate.integrity.record.artifact.identity) shouldBe Vector("prepared")
        Files.isRegularFile(root.resolve("repository/images/prepared.png")) shouldBe true
        Files.exists(root.resolve("repository/images/appeared.png")) shouldBe false
        _entry_paths(root.resolve("src/main/publication/article-media.json")) shouldBe Vector(
          "metadata/article-media-integrity/development-process/prepared/ja/infographic.json",
          "metadata/article-media/development-process/prepared.json"
        )
      }
    }

    "reject a prevalidated plan when its commit configuration differs" in {
      Given("one valid prepared plan and a different publication version for commit")
      _with_root { root =>
        _media(root, "mismatch", "development-process/mismatch", "ja", "images/mismatch.png", _png(0xff3366cc))
        val config = _config(root)
        val plan = CozyArticleMediaInfographicCommand.plan(config)
        val changed = config.copy(version = Some("2.0.0"))

        When("the package-private commit boundary receives mismatched configuration")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.commit(changed, plan))

        Then("it rejects the stale plan before artifact or registry mutation")
        error.getMessage should include("does not match")
        Files.exists(root.resolve("repository/images/mismatch.png")) shouldBe false
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "enumerate, decode, and publish each exact supported descriptor basename" in {
      Given("five isolated media packages written with the syntax selected by each decoder")
      _with_root { root =>
        val names = Vector("media.yaml", "media.yml", "media.json", "media.conf", "media.xml")
        names.zipWithIndex.foreach { case (name, index) =>
          _media(root, s"descriptor-$index", s"development-process/descriptor-$index", "ja", s"images/descriptor-$index.png", _png(0xff3366cc + index), name)
        }

        When("publish-media enumerates the direct recognized descriptor files")
        val result = CozyArticleMediaInfographicCommand.publish(_config(root))

        Then("every exact basename is decoded and its selected PNG is published")
        result.map(_.roleUpdate.integrity.record.artifact.identity) shouldBe Vector("descriptor-0", "descriptor-1", "descriptor-2", "descriptor-3", "descriptor-4")
        names.indices.foreach(index => Files.isRegularFile(root.resolve(s"repository/images/descriptor-$index.png")) shouldBe true)
      }
    }

    }

    "ownership and deterministic preservation" which {
    "preserve an existing other locale role and generic entry in its current owner bundle" in {
      Given("an article-media owner that already contains English infographic evidence and a generic entry")
      _with_root { root =>
        _media(root, "en", "development-process/example", "en", "images/en.png", _png(0xff3366cc))
        CozyArticleMediaInfographicCommand.publish(_config(root))
        _append_generic(root.resolve("src/main/publication/article-media.json"))
        _media(root, "ja", "development-process/example", "ja", "images/ja.png", _png(0xffcc6633))

        When("the command publishes only the Japanese infographic role")
        CozyArticleMediaInfographicCommand.publish(_config(root))

        Then("the current owner retains English evidence and generic content while adding Japanese evidence")
        _entry_paths(root.resolve("src/main/publication/article-media.json")) shouldBe Vector(
          "metadata/article-media-integrity/development-process/example/en/infographic.json",
          "metadata/article-media-integrity/development-process/example/ja/infographic.json",
          "metadata/article-media/development-process/example.json",
          "metadata/generic/kept.json"
        )
      }
    }

    "retain deterministic command order under generated descriptor creation orders" in {
      Given("two independent descriptors and an unrelated generic configured bundle")
      val names = Vector("alpha", "beta")
      val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(Gen.oneOf(names.permutations.toVector)) { order =>
        _with_root { root =>
          _write_bundle(root.resolve("src/main/publication"), "generic", "metadata/generic.json")
          order.zipWithIndex.foreach { case (name, index) =>
            _media(root, name, s"development-process/$name", "ja", s"images/$name.png", _png(0xff3366cc + index))
          }
          val results = CozyArticleMediaInfographicCommand.publish(_config(root))
          results.map(_.roleUpdate.integrity.record.artifact.identity) == names &&
            _entry_paths(root.resolve("src/main/publication/generic.json")) == Vector("metadata/generic.json")
        }
      })

      When("ScalaCheck evaluates distinct descriptor creation orders")
      val passed = check.passed

      Then("the committed artifact identities and generic owner content remain deterministic")
      passed shouldBe true
      check.succeeded should be >= 30
    }

    }

    "publication, force, and artifact failure boundaries" which {
    "leave every registry bundle unchanged when a later artifact installation fails after complete validation" in {
      Given("two valid prepared infographics and an existing generic registry bundle")
      _with_root { root =>
        _write_bundle(root.resolve("src/main/publication"), "generic", "metadata/generic.json")
        val first = _media(root, "first", "development-process/first", "ja", "images/first.png", _png(0xff3366cc))
        val second = _media(root, "second", "development-process/second", "ja", "images/second.png", _png(0xffcc6633))
        val before = _registry_tree(root.resolve("src/main/publication"))
        val interloper = _png(0xff339966)

        When("the post-validation hook creates an interloper only at the second prepared destination")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(
          _config(root),
          () => _write_bytes(root.resolve("repository/images/second.png"), interloper)
        ))

        Then("the first installation remains, the second installation actually fails, and registry bytes stay unchanged")
        error.getMessage should include("Media publication failed")
        _sha256(root.resolve("repository/images/first.png")) shouldBe _sha256(first.output)
        Files.readAllBytes(root.resolve("repository/images/second.png")).toVector shouldBe interloper.toVector
        Files.isRegularFile(second.output) shouldBe true
        _registry_tree(root.resolve("src/main/publication")) shouldBe before
      }
    }

    "reject a same-directory recognized descriptor basename collision before mutation" in {
      Given("two exact recognized descriptor basenames in one source directory")
      _with_root { root =>
        val directory = Files.createDirectories(root.resolve("src/main/doxsite/collision"))
        Files.createDirectories(root.resolve("repository"))
        Files.write(directory.resolve("media.yaml"), "{}".getBytes(StandardCharsets.UTF_8))
        Files.write(directory.resolve("media.json"), "{}".getBytes(StandardCharsets.UTF_8))

        When("the command enumerates direct descriptor candidates")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(_config(root)))

        Then("the collision is rejected before article-media bundle creation")
        error.getMessage should include("basename collision")
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "reject a selected infographic command with no explicit version before mutation" in {
      Given("one valid selected infographic resource without a command version")
      _with_root { root =>
        _media(root, "missing-version", "development-process/version", "ja", "images/version.png", _png(0xff3366cc))

        When("the command completes semantic preflight")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(_config(root, version = None)))

        Then("the missing version leaves artifact and article-media bundle absent")
        error.getMessage should include("--version")
        Files.exists(root.resolve("repository/images/version.png")) shouldBe false
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "reject duplicate normalized infographic roles across descriptors before mutation" in {
      Given("two selected resources with one article identity and locale")
      _with_root { root =>
        _media(root, "first", "development-process/duplicate", "ja", "images/first.png", _png(0xff3366cc))
        _media(root, "second", "development-process/duplicate", "ja", "images/second.png", _png(0xffcc6633))

        When("the complete command candidate set is validated")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(_config(root)))

        Then("the duplicate role leaves both artifact destinations and registry absent")
        error.getMessage should include("Duplicate article-media infographic role")
        Files.exists(root.resolve("repository/images/first.png")) shouldBe false
        Files.exists(root.resolve("repository/images/second.png")) shouldBe false
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "reject a malformed build manifest before artifact or registry mutation" in {
      Given("a selected descriptor with a malformed exact target manifest")
      _with_root { root =>
        val fixture = _media(root, "malformed", "development-process/malformed", "ja", "images/malformed.png", _png(0xff3366cc))
        Files.write(fixture.manifest, "not-json".getBytes(StandardCharsets.UTF_8))

        When("prepared evidence reads the required manifest")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(_config(root)))

        Then("the malformed manifest leaves destination and article-media bundle absent")
        error.getMessage should not be empty
        Files.exists(root.resolve("repository/images/malformed.png")) shouldBe false
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "reject a stale build-manifest SHA before artifact or registry mutation" in {
      Given("a selected descriptor whose manifest SHA differs from its output bytes")
      _with_root { root =>
        val fixture = _media(root, "stale", "development-process/stale", "ja", "images/stale.png", _png(0xff3366cc))
        val manifestjson = Json.parse(Files.readString(fixture.manifest, StandardCharsets.UTF_8)).as[JsObject]
        val resource = (manifestjson \ "resources").as[JsArray].value.find(entry => (entry \ "id").as[String] == "stale").get.as[JsObject]
        val receiptinputs = resource \ "receipt" \ "inputs"
        val stale = manifestjson + ("resources" -> JsArray(
          (manifestjson \ "resources").as[JsArray].value.map { entry =>
            val value = entry.as[JsObject]
            if ((value \ "id").as[String] == "stale") value + ("sha256" -> Json.toJson("0" * 64)) else value
          }
        ))
        Files.write(fixture.manifest, Json.stringify(stale).getBytes(StandardCharsets.UTF_8))
        val mutatedresource = (Json.parse(Files.readString(fixture.manifest, StandardCharsets.UTF_8)) \ "resources").as[JsArray].value.find(entry => (entry \ "id").as[String] == "stale").get
        (mutatedresource \ "sha256").as[String] shouldBe "0" * 64
        (mutatedresource \ "receipt" \ "inputs") shouldBe receiptinputs

        When("prepared evidence compares the exact manifest and source SHA values")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(_config(root)))

        Then("the stale SHA leaves destination and article-media bundle absent")
        error.getMessage should include("build manifest receipt is not current")
        error.getMessage should include("cozy.media.receipt.v2")
        Files.exists(root.resolve("repository/images/stale.png")) shouldBe false
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "exclude descriptor candidates below a symlink directory" in {
      Given("a source-directory symlink to an external directory containing a media descriptor")
      _with_root { root =>
        val external = Files.createTempDirectory(root.getParent, "infographic-external-")
        try {
          _media(external, "outside", "development-process/outside", "ja", "images/outside.png", _png(0xff3366cc))
          val source = Files.createDirectories(root.resolve("src/main/doxsite"))
          Files.createDirectories(root.resolve("repository"))
          try Files.createSymbolicLink(source.resolve("linked"), external.resolve("src/main/doxsite/outside"))
          catch {
            case _: UnsupportedOperationException | _: SecurityException => cancel("The platform cannot create symbolic links for this specification")
          }

          When("the command enumerates only direct files without following directory links")
          val result = CozyArticleMediaInfographicCommand.publish(_config(root))

          Then("the linked descriptor is excluded and no article-media bundle is created")
          result shouldBe Vector.empty
          Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
        } finally _delete(external)
      }
    }

    "exclude direct descriptor-file symlinks and near-name files" in {
      Given("a direct symbolic link named media.yaml and a regular near-name descriptor")
      _with_root { root =>
        val directory = Files.createDirectories(root.resolve("src/main/doxsite/excluded"))
        Files.createDirectories(root.resolve("repository"))
        val target = root.resolve("target-media.json")
        _write_bytes(target, _descriptor_text("media.json", "excluded", "development-process/excluded", "ja", "images/excluded.png").getBytes(StandardCharsets.UTF_8))
        try Files.createSymbolicLink(directory.resolve("media.yaml"), target)
        catch {
          case _: UnsupportedOperationException | _: SecurityException => cancel("The platform cannot create symbolic links for this specification")
        }
        _write_bytes(directory.resolve("media.yaml.bak"), _descriptor_text("media.yaml", "near-name", "development-process/near-name", "ja", "images/near-name.png").getBytes(StandardCharsets.UTF_8))

        When("the command considers only direct exact descriptor basenames")
        val result = CozyArticleMediaInfographicCommand.publish(_config(root))

        Then("the symlink and near-name are excluded without artifact or registry mutation")
        result shouldBe Vector.empty
        Files.exists(root.resolve("repository/images/excluded.png")) shouldBe false
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "reject a selected resource with zero matching publication profiles" in {
      Given("a selected descriptor whose declared profile root is a different direct repository")
      _with_root { root =>
        _media(root, "zero", "development-process/zero", "ja", "images/zero.png", _png(0xff3366cc))
        Files.createDirectories(root.resolve("other-repository"))
        val descriptor = root.resolve("src/main/doxsite/zero/media.json")
        val changed = Files.readString(descriptor, StandardCharsets.UTF_8).replace("../../../../repository", "../../../../other-repository")
        Files.write(descriptor, changed.getBytes(StandardCharsets.UTF_8))

        When("the command resolves profile lexical and real root identity")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(_config(root)))

        Then("zero profile selection fails before artifact and registry mutation")
        error.getMessage should include("no matching publication profile")
        Files.exists(root.resolve("repository/images/zero.png")) shouldBe false
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "reject a lexical profile-root alias of the configured repository" in {
      Given("a selected descriptor whose profile root is a symbolic-link alias of the configured repository")
      _with_root { root =>
        _media(root, "alias", "development-process/alias", "ja", "images/alias.png", _png(0xff3366cc))
        val alias = root.resolve("repository-alias")
        try Files.createSymbolicLink(alias, root.resolve("repository"))
        catch {
          case _: UnsupportedOperationException | _: SecurityException => cancel("The platform cannot create symbolic links for this specification")
        }
        val descriptor = root.resolve("src/main/doxsite/alias/media.json")
        val changed = Files.readString(descriptor, StandardCharsets.UTF_8).replace("../../../../repository", "../../../../repository-alias")
        Files.write(descriptor, changed.getBytes(StandardCharsets.UTF_8))

        When("profile selection compares the normalized lexical and canonical real identities")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(_config(root)))

        Then("the alias is rejected before artifact and registry mutation")
        error.getMessage should include("direct non-symlink directory")
        Files.exists(root.resolve("repository/images/alias.png")) shouldBe false
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "reject a selected resource with multiple matching publication profiles" in {
      Given("a selected descriptor with two declared profiles rooted at the configured repository")
      _with_root { root =>
        _media(root, "multiple", "development-process/multiple", "ja", "images/multiple.png", _png(0xff3366cc))
        val descriptor = root.resolve("src/main/doxsite/multiple/media.json")
        val changed = Files.readString(descriptor, StandardCharsets.UTF_8).
          replace("\"profiles\":{\"site\":{\"root\":\"../../../../repository\"}}", "\"profiles\":{\"site\":{\"root\":\"../../../../repository\"},\"site2\":{\"root\":\"../../../../repository\"}}").
          replace("\"publications\":{\"site\":\"images/multiple.png\"}", "\"publications\":{\"site\":\"images/multiple.png\",\"site2\":\"images/multiple.png\"}")
        Files.write(descriptor, changed.getBytes(StandardCharsets.UTF_8))

        When("the command resolves profile lexical and real root identity")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(_config(root)))

        Then("multiple profile selection fails before artifact and registry mutation")
        error.getMessage should include("multiple matching publication profiles")
        Files.exists(root.resolve("repository/images/multiple.png")) shouldBe false
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "reject a differing destination without explicit force" in {
      Given("an existing destination whose bytes differ from the declared output")
      _with_root { root =>
        _media(root, "conflict", "development-process/conflict", "ja", "images/conflict.png", _png(0xff3366cc))
        val destination = root.resolve("repository/images/conflict.png")
        _write_bytes(destination, _png(0xffcc6633))

        When("publication is attempted without force")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(_config(root)))

        Then("the existing destination remains and no article-media bundle is created")
        error.getMessage should include("use force")
        _sha256(destination) should not be _sha256(root.resolve("src/main/doxsite/conflict/target/cozy-media/conflict.png"))
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe false
      }
    }

    "replace a differing destination when explicit force is supplied" in {
      Given("an existing destination whose bytes differ from the declared output")
      _with_root { root =>
        val fixture = _media(root, "force", "development-process/force", "ja", "images/force.png", _png(0xff3366cc))
        val destination = root.resolve("repository/images/force.png")
        _write_bytes(destination, _png(0xffcc6633))

        When("publication is attempted with force")
        val completed = CozyArticleMediaInfographicCommand.publish(_config(root, force = true))

        Then("the destination is atomically replaced with the declared output and registry evidence")
        completed should have size 1
        _sha256(destination) shouldBe _sha256(fixture.output)
        Files.exists(root.resolve("src/main/publication/article-media.json")) shouldBe true
      }
    }

    }

    "CLI compatibility and configuration isolation" which {
    "derive publish-media force only from its explicit switch while preserving legacy video configuration" in {
      Given("a project configuration that enables the legacy video force property")
      _with_root { root =>
        _write_bytes(root.resolve(".cozy/config.yaml"), "bok:\n  video:\n    force: true\n".getBytes(StandardCharsets.UTF_8))

        When("publication configurations are created for media, video, and explicit media force")
        val media = CozyBok.PublicationConfig.create("publish-media", List(root.toString))
        val video = CozyBok.PublicationConfig.create("publish-video", List(root.toString))
        val forcedmedia = CozyBok.PublicationConfig.create("publish-media", List(root.toString, "--force"))

        Then("only the explicit publish-media switch enables media replacement")
        media.force shouldBe false
        video.force shouldBe true
        forcedmedia.force shouldBe true
      }
    }

    "print the exact publish-media help syntax and body" in {
      Given("the explicit publish-media help request")
      val bytes = new ByteArrayOutputStream()
      val output = new PrintStream(bytes, true, "UTF-8")

      When("the BoK CLI dispatches the help surface")
      val dispatched = Console.withOut(output)(CozyBok.execute(List("bok", "publish-media", "--help")))
      output.close()

      Then("the command advertises only its authoritative syntax and description")
      dispatched shouldBe true
      bytes.toString("UTF-8").trim shouldBe
        "Usage: cozy bok publish-media <project-dir> [--publication <dir>] [--repository <dir>] [--version <version>] [--force]\n" +
          "Publish explicit detailed-infographic media packages into the BoK article-media registry and artifact repository."
    }

    "reject dry-run at the explicit command boundary" in {
      Given("a typed publish-media configuration with dry-run enabled")
      _with_root { root =>
        Files.createDirectories(root.resolve("src/main/doxsite"))
        Files.createDirectories(root.resolve("repository"))

        When("the explicit command receives dry-run")
        val error = intercept[IllegalArgumentException](CozyArticleMediaInfographicCommand.publish(_config(root, dryrun = true)))

        Then("the command has no dry-run behavior")
        error.getMessage should include("dry-run")
      }
    }
    }
  }

  private final class MediaFixture(val manifest: Path, val output: Path)

  private def _config(root: Path, version: Option[String] = Some("1.0.0"), force: Boolean = false, dryrun: Boolean = false): CozyBok.PublicationConfig =
    CozyBok.PublicationConfig(root, "src/main/doxsite", "src/main/publication", None, "repository", version, force, true, dryrun, "production")

  private def _media(root: Path, id: String, article: String, locale: String, destination: String, png: Array[Byte], descriptorname: String = "media.json"): MediaFixture = {
    Files.createDirectories(root.resolve("repository"))
    val directory = Files.createDirectories(root.resolve(s"src/main/doxsite/$id"))
    val output = directory.resolve(s"target/cozy-media/$id.png")
    val manifest = directory.resolve("target/cozy-media/manifest.json")
    val descriptor = _descriptor_text(descriptorname, id, article, locale, destination)
    val descriptorfile = directory.resolve(descriptorname)
    Files.write(descriptorfile, descriptor.getBytes(StandardCharsets.UTF_8))
    _write_bytes(directory.resolve("article.dox"), article.getBytes(StandardCharsets.UTF_8))
    _write_bytes(directory.resolve("input.svg"), png)
    CozyMedia.build(CozyMedia.CommandConfig(descriptorfile, target = Some(id)))
    new MediaFixture(manifest, output)
  }

  private def _descriptor_text(name: String, id: String, article: String, locale: String, destination: String): String =
    name match {
      case "media.yaml" | "media.yml" =>
        s"""schema: cozy.media.v1
           |knowledge:
           |  id: $article
           |  source: article.dox
           |profiles:
           |  site:
           |    root: ../../../../repository
           |resources:
           |  - id: $id
           |    kind: image
           |    language: $locale
           |    role: detailed-infographic
           |    source: input.svg
           |    output: target/cozy-media/$id.png
           |    build: copy
           |    publications:
           |      site: $destination
           |""".stripMargin
      case "media.json" =>
        s"""{"schema":"cozy.media.v1","knowledge":{"id":"$article","source":"article.dox"},"profiles":{"site":{"root":"../../../../repository"}},"resources":[{"id":"$id","kind":"image","language":"$locale","role":"detailed-infographic","source":"input.svg","output":"target/cozy-media/$id.png","build":"copy","publications":{"site":"$destination"}}]}"""
      case "media.conf" =>
        s"""schema = "cozy.media.v1"
           |knowledge { id = "$article", source = "article.dox" }
           |profiles { site { root = "../../../../repository" } }
           |resources = [{ id = "$id", kind = "image", language = "$locale", role = "detailed-infographic", source = "input.svg", output = "target/cozy-media/$id.png", build = "copy", publications { site = "$destination" } }]
           |""".stripMargin
      case "media.xml" =>
        s"""<media schema="cozy.media.v1">
           |  <knowledge id="$article" source="article.dox"/>
           |  <profiles><site root="../../../../repository"/></profiles>
           |  <resources id="$id" kind="image" language="$locale" role="detailed-infographic" source="input.svg" output="target/cozy-media/$id.png" build="copy"><publications site="$destination"/></resources>
           |</media>
           |""".stripMargin
      case _ => throw new IllegalArgumentException(s"Unsupported descriptor fixture: $name")
    }

  private def _append_generic(path: Path): Unit = {
    val bundle = Json.parse(Files.readString(path, StandardCharsets.UTF_8)).as[JsObject]
    val entries = (bundle \ "entries").as[JsArray].value.toVector :+ Json.obj(
      "path" -> "metadata/generic/kept.json",
      "key" -> "generic/kept",
      "metadata" -> Json.obj("kept" -> true)
    )
    Files.write(path, Json.stringify(bundle + ("entries" -> JsArray(entries))).getBytes(StandardCharsets.UTF_8))
  }

  private def _write_bundle(root: Path, name: String, path: String): Unit = {
    Files.createDirectories(root)
    val json = s"""{"schema":"cozy.publish-project.v1","type":"publication-bundle","publication":{"name":"$name"},"sourceRepository":"","sourcePath":".","sourceCommit":null,"entries":[{"path":"$path","key":"${path.stripPrefix("metadata/").stripSuffix(".json")}","metadata":{"kept":true}}]}"""
    Files.write(root.resolve(s"$name.json"), json.getBytes(StandardCharsets.UTF_8))
  }

  private def _entry_paths(path: Path): Vector[String] = {
    val json = Json.parse(Files.readString(path, StandardCharsets.UTF_8))
    (json \ "entries").as[JsArray].value.toVector.map(x => (x \ "path").as[String]).sorted
  }

  private def _registry_tree(root: Path): Vector[(String, String)] =
    _json_children(root).map(path => path.getFileName.toString -> _sha256(path))

  private def _json_children(path: Path): Vector[Path] =
    if (!Files.isDirectory(path)) Vector.empty else _direct_children(path).filter(_.getFileName.toString.endsWith(".json"))

  private def _direct_children(path: Path): Vector[Path] = {
    val stream = Files.list(path)
    try stream.iterator().asScala.toVector.sortBy(_.toString)
    finally stream.close()
  }

  private def _png(color: Int): Array[Byte] = {
    val image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, color)
    val output = new ByteArrayOutputStream()
    ImageIO.write(image, "png", output)
    output.toByteArray
  }

  private def _write_bytes(path: Path, bytes: Array[Byte]): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, bytes)
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(x => f"${x & 0xff}%02x").mkString

  private def _with_root[A](f: Path => A): A = {
    val work = Paths.get("target/cozy-article-media-infographic-command/work").toAbsolutePath.normalize()
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "command-")
    try f(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
}
