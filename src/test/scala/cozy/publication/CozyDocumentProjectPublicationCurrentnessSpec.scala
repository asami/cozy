package cozy.publication

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import cozy.document.CozyDocumentProject
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 1, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectPublicationCurrentnessSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyDocumentProjectPublicationCurrentnessSpec._
  "Cozy Document Project publication inputs" should {
    "resolve a selected project with paired or absent media context without writes" in {
      Vector(true, false).foreach { paired =>
        _with_fixture(s"project-$paired") { fixture =>
          Given("a scaffolded Document Project, its public export and original media configuration")
          val registration = _registration(fixture, paired)
          val config = CozyDocumentProjectPublicationTarget.ProjectRegistrationConfig(fixture.project.resolve("."), registration)
          val before = _tree_bytes(fixture.root)

          When("ordinary project registration resolves the selected producer inputs")
          val plan = CozyDocumentProjectPublicationTarget.planProjectRegistration(config)

          Then("the normalized project and selected media/site context are retained without writes")
          plan.config shouldBe config.copy(project = fixture.project)
          plan.registration.config shouldBe registration
          plan.registration.export.bundleRoot shouldBe fixture.bundle
          plan.registration.siteContext.map(_.root) shouldBe (if (paired) Some(fixture.site) else None)
          plan.registration.siteContext.map(_.config) shouldBe (if (paired) Some(fixture.site.resolve("site.conf")) else None)
          plan.registration.siteContext.map(_.source) shouldBe (if (paired) Some(fixture.site.resolve("content/article.dox")) else None)
          _tree_bytes(fixture.root) shouldBe before
        }
      }
    }

    "reject damaged and partial portable bundles before accepting project registration" in {
      val cases: Vector[(String, Fixture => Unit)] = Vector(
        ("manifest", fixture => _write(fixture.bundle.resolve("manifest.yaml"), "damaged manifest\n")),
        ("missing-manifest", fixture => { Files.delete(fixture.bundle.resolve("manifest.yaml")); () }),
        ("missing-output", fixture => { Files.delete(fixture.bundle.resolve("work-products/article-review-html/article-review.html")); () })
      )
      cases.foreach { case (name, mutate) =>
        _with_fixture(name) { fixture =>
          Given(s"a genuine export with $name damage or missing evidence")
          val config = _current_config(fixture)
          mutate(fixture)
          val before = _tree_bytes(fixture.root)

          When("the project planner freshly admits the complete portable bundle")
          val failure = intercept[RuntimeException] {
            CozyDocumentProjectPublicationTarget.planProjectRegistration(config)
          }

          Then("portable producer diagnostics reject the bundle without writes")
          failure.getMessage should include("DP-OP-001")
          _tree_bytes(fixture.root) shouldBe before
        }
      }
    }

    "reject unusable media and site inputs during ordinary resolution" in {
      val cases: Vector[(String, String, Fixture => Unit)] = Vector(
        ("configured-root", "selected SmartDox profile root", fixture => {
          Files.createDirectory(fixture.media.resolve("other-publication"))
          val path = fixture.media.resolve("conf/cozy/config.yaml")
          _write(path, Files.readString(path, StandardCharsets.UTF_8).replace("root: publication", "root: other-publication"))
        }),
        ("deleted-site-config", "Media site config", fixture => { Files.delete(fixture.site.resolve("site.conf")); () }),
        ("empty-site-config", "nonempty .conf file", fixture => _write(fixture.site.resolve("site.conf"), "")),
        ("missing-source", "source", fixture => { Files.delete(fixture.site.resolve("content/article.dox")); () }),
        ("aliased-source", "source", fixture => {
          val path = fixture.site.resolve("content/article.dox")
          val original = fixture.site.resolve("content/original.dox")
          Files.move(path, original)
          Files.createSymbolicLink(path, original)
          ()
        })
      )
      cases.foreach { case (name, diagnostic, mutate) =>
        _with_fixture(name) { fixture =>
          Given(s"a selected registration with unusable $name inputs")
          mutate(fixture)
          val before = _tree_bytes(fixture.root)

          When("ordinary registration resolves the selected media and site inputs")
          val failures = Vector(
            intercept[RuntimeException] { CozyDocumentProjectPublicationTarget.planRegistration(_registration(fixture, paired = true)) },
            intercept[RuntimeException] { CozyDocumentProjectPublicationTarget.planProjectRegistration(_current_config(fixture)) }
          )

          Then("the ordinary producer diagnostics reject unusable inputs without writes")
          failures.foreach(_.getMessage should include(diagnostic))
          _tree_bytes(fixture.root) shouldBe before
        }
      }
    }

    "reject null, missing, non-directory, non-dox and aliased project roots" in {
      _with_fixture("invalid-project") { fixture =>
        Given("absent inputs and project roots outside the direct canonical .dox contract")
        val alias = fixture.root.resolve("alias.dox")
        Files.createSymbolicLink(alias, fixture.project)
        val ancestor = fixture.root.resolve("ancestor")
        Files.createSymbolicLink(ancestor, fixture.project.getParent)
        val file = fixture.root.resolve("file.dox")
        _write(file, "not a directory\n")
        val configs = Vector(
          null,
          CozyDocumentProjectPublicationTarget.ProjectRegistrationConfig(null, _registration(fixture)),
          CozyDocumentProjectPublicationTarget.ProjectRegistrationConfig(fixture.project, null)
        ) ++ Vector(fixture.root.resolve("missing.dox"), fixture.root, file, alias, ancestor.resolve(fixture.project.getFileName)).map {
          path => CozyDocumentProjectPublicationTarget.ProjectRegistrationConfig(path, _registration(fixture))
        }
        val before = _tree_bytes(fixture.root)

        When("each invalid project input is resolved")
        val failures = configs.map(config => intercept[RuntimeException] {
          CozyDocumentProjectPublicationTarget.planProjectRegistration(config)
        })

        Then("the target reports DP-OP-001 without writing or following project aliases")
        failures.foreach(_.getMessage should include("DP-OP-001"))
        Files.readSymbolicLink(alias) shouldBe fixture.project
        _tree_bytes(fixture.root) shouldBe before
      }
    }

    "preserve fresh descriptor admission diagnostics" in {
      _with_fixture("invalid-descriptor") { fixture =>
        Given("a previously accepted project whose descriptor is now invalid")
        val config = _current_config(fixture)
        _write(fixture.project.resolve("document-project.yaml"), "schema: invalid\n")
        val before = _tree_bytes(fixture.root)

        When("the descriptor producer and project planner each reload the invalid descriptor")
        val producer = intercept[RuntimeException] { CozyDocumentProject._load_project(fixture.project) }
        val consumer = intercept[RuntimeException] { CozyDocumentProjectPublicationTarget.planProjectRegistration(config) }

        Then("the consumer preserves the descriptor producer diagnostic and all bytes")
        consumer.getMessage shouldBe producer.getMessage
        _tree_bytes(fixture.root) shouldBe before
      }
    }
  }

  private def _current_config(fixture: Fixture): CozyDocumentProjectPublicationTarget.ProjectRegistrationConfig =
    CozyDocumentProjectPublicationTarget.ProjectRegistrationConfig(fixture.project, _registration(fixture, paired = true))


  private def _registration(fixture: Fixture, paired: Boolean = false): CozyDocumentProjectPublicationTarget.RegistrationConfig =
    CozyDocumentProjectPublicationTarget.RegistrationConfig(
      fixture.bundle,
      CozyArticleMediaSiteBinding.Config(
        fixture.descriptor,
        siteRoot = if (paired) Some(fixture.site) else None,
        siteConfig = if (paired) Some(fixture.site.resolve("site.conf")) else None
      )
    )

  private def _with_fixture(name: String, overlay: Boolean = false)(body: Fixture => Unit): Unit = {
    val work = Files.createDirectories(Paths.get("target/document-project-publication-currentness-spec/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(work, name + "-").toRealPath()
    try {
      val parent = Files.createDirectory(root.resolve("document"))
      _execute(List("document-project", "scaffold", "registration", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
      val project = parent.resolve("registration.dox")
      val projectdescriptor = project.resolve("document-project.yaml")
      _write(projectdescriptor, Files.readString(projectdescriptor, StandardCharsets.UTF_8).replace(
        "activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - article-review-html"
      ))
      _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
      val bundle = root.resolve("portable-bundle")
      _execute(List("document-project", "export", project.toString, "--target", "simplemodeling-org", "--save", bundle.toString))
      val media = Files.createDirectory(root.resolve("original-media"))
      val configuredroot = if (overlay) "configured-publication" else "publication"
      val site = media.resolve("publication")
      _write(media.resolve("conf/cozy/config.yaml"), s"""project:
        |  id: simplemodeling-org
        |  kind: smartdox-site
        |media:
        |  publication-profiles:
        |    release:
        |      root: $configuredroot
        |      site-kind: smartdox
        |""".stripMargin)
      val descriptor = media.resolve("media.yaml")
      val profiles = if (overlay) "profiles:\n  release:\n    root: publication\n" else ""
      _write(descriptor, s"""schema: cozy.media.v1
        |knowledge:
        |  id: original-package/article
        |  source: publication/content/article.dox
        |languages:
        |  - en
        |  - ja
        |${profiles}articleMedia:
        |  articleIdentity: development-process/registration
        |  publicationProfile: release
        |resources:
        |${_infographic_yaml("en")}${_infographic_yaml("ja")}""".stripMargin)
      _write(site.resolve("site.conf"), "site.title = Registration\n")
      _write(site.resolve("content/article.dox"), "= Original media article =\n\nOriginal knowledge source.\n")
      // Existing PNG bytes are prebuilt evidence, not a fabricated build receipt.
      val image = java.util.Base64.getDecoder.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
      Vector("en", "ja").foreach { locale =>
        val path = site.resolve(s"images/summary-$locale.png")
        Files.createDirectories(path.getParent)
        Files.write(path, image)
      }
      body(Fixture(root, project, bundle, media, descriptor, site))
    } finally _delete(root)
  }

  private def _infographic_yaml(locale: String): String =
    s"""  - id: summary-$locale
       |    kind: infographic
       |    language: $locale
       |    role: article-summary
       |    source: publication/images/summary-$locale.png
       |    build: prebuilt
       |    publications:
       |      release: images/summary-$locale.png
       |    articleMedia:
       |      role: infographic
       |      publicPath: /$locale/development-process/images/registration/summary.png
       |      mediaType: image/png
       |      alt: Original summary
       |""".stripMargin

  private def _execute(args: List[String]): Unit = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentProject.execute(args) shouldBe true
    }
  }

  private def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _tree_bytes(root: Path): Map[String, Vector[Byte]] = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map { path =>
      root.relativize(path).toString.replace('\\', '/') -> Files.readAllBytes(path).toVector
    }.toMap
    finally stream.close()
  }

  private def _delete(path: Path): Unit = if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
    val stream = Files.walk(path)
    try stream.iterator.asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
      try Files.deleteIfExists(item) catch { case NonFatal(_) => () }
    }
    finally stream.close()
  }
}

object CozyDocumentProjectPublicationCurrentnessSpec {
  private[publication] final case class Fixture(root: Path, project: Path, bundle: Path, media: Path, descriptor: Path, site: Path)
}
