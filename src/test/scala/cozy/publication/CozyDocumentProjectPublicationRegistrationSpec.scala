package cozy.publication

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import cozy.document.CozyDocumentProject
import cozy.media.CozyMedia
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 1, 2026
 * @version Oct. 1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectPublicationRegistrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project publication registration snapshot" should {
    "retain the original paired descriptor, profile, context, candidates, and all fixture bytes" in {
      _with_fixture("paired") { fixture =>
        Given("a genuine portable export and original media with a configured SmartDox profile and paired site context")
        val config = _registration(fixture, paired = true)
        val binding = CozyArticleMediaSiteBinding.plan(config.media)
        val mediaplan = _media_plan(binding)
        val before = _tree_bytes(fixture.root)

        When("the registration snapshot is planned")
        val plan = CozyDocumentProjectPublicationTarget.planRegistration(config)

        Then("all original producer authorities and portable evidence are retained without writes")
        plan.config shouldBe config
        plan.siteBinding shouldBe binding
        plan.siteBinding.config shouldBe config.media
        plan.siteBinding.descriptorEvidence.path shouldBe fixture.descriptor
        plan.siteBinding.descriptorBytes shouldBe Files.readAllBytes(fixture.descriptor).toVector
        plan.siteBinding.context shouldBe mediaplan.context
        plan.siteBinding.context.project.map(_.root.path) shouldBe Some(fixture.media)
        plan.siteBinding.context.project.map(_.marker.path) shouldBe Some(fixture.media.resolve("conf/cozy/config.yaml"))
        plan.siteBinding.publicationProfile shouldBe "release"
        plan.siteBinding.effectiveProfile shouldBe mediaplan.effectiveProfile.get
        plan.siteBinding.effectiveProfile.siteKind shouldBe Some("smartdox")
        plan.siteBinding.effectiveProfile.sourcePath shouldBe Some(fixture.media.resolve("conf/cozy/config.yaml"))
        plan.siteBinding.candidates shouldBe binding.candidates
        plan.siteBinding.candidates.map(_.resourceId) shouldBe Vector("summary-en", "summary-ja")
        plan.siteContext shouldBe CozyMedia.requireSiteContext(mediaplan)
        plan.siteContext.map(_.root) shouldBe Some(fixture.site)
        plan.siteContext.map(_.config) shouldBe Some(fixture.site.resolve("site.conf"))
        plan.siteContext.map(_.source) shouldBe Some(fixture.site.resolve("content/article.dox"))
        plan.siteContext.map(_.configRoute) shouldBe Some("site.conf")
        plan.siteContext.map(_.documentRoute) shouldBe Some("content/article.dox")
        plan.export shouldBe CozyDocumentProjectPublicationTarget.admit(fixture.bundle)
        _tree_bytes(fixture.root) shouldBe before
      }
    }

    "retain the configured profile authority when the original descriptor overlays its location" in {
      _with_fixture("overlay", overlay = true) { fixture =>
        Given("a configured release profile with an original descriptor location overlay")
        val config = _registration(fixture, paired = true)
        val binding = CozyArticleMediaSiteBinding.plan(config.media)
        val before = _tree_bytes(fixture.root)

        When("the registration snapshot is planned with that original descriptor")
        val plan = CozyDocumentProjectPublicationTarget.planRegistration(config)

        Then("the configured SmartDox authority and descriptor location remain exactly producer-resolved")
        plan.siteBinding shouldBe binding
        plan.siteBinding.effectiveProfile.configuration.map(_.resolvedRoot) shouldBe Some(fixture.media.resolve("configured-publication"))
        plan.siteBinding.effectiveProfile.layer shouldBe Some("project-conf")
        plan.siteBinding.profileRoot shouldBe fixture.site
        plan.siteBinding.effectiveProfile.resolvedRoot shouldBe fixture.site
        plan.siteContext.map(_.root) shouldBe Some(fixture.site)
        plan.siteBinding.candidates.map(_.evidence.file.path).toSet shouldBe Set(
          fixture.site.resolve("images/summary-en.png"), fixture.site.resolve("images/summary-ja.png")
        )
        _tree_bytes(fixture.root) shouldBe before
      }
    }

    "preserve Phase 55 profile defaults and absent explicit site context" in {
      _with_fixture("absent") { fixture =>
        Given("original media with a configured release profile and no explicit site options")
        val config = _registration(fixture)
        val binding = CozyArticleMediaSiteBinding.plan(config.media)
        val before = _tree_bytes(fixture.root)

        When("the registration snapshot is planned without site options")
        val plan = CozyDocumentProjectPublicationTarget.planRegistration(config)

        Then("the original binding supplies the selected profile while site context remains absent")
        plan.siteBinding shouldBe binding
        plan.siteBinding.publicationProfile shouldBe "release"
        plan.siteBinding.profileRoot shouldBe fixture.site
        plan.siteContext shouldBe None
        plan.siteBinding.config.siteRoot shouldBe None
        plan.siteBinding.config.siteConfig shouldBe None
        _tree_bytes(fixture.root) shouldBe before
      }
    }

    "forward the original media target selection without broadening the candidates" in {
      _with_fixture("selection") { fixture =>
        Given("two bound infographic resources and an original exact English resource selection")
        val original = _registration(fixture, paired = true)
        val config = original.copy(media = original.media.copy(target = Some("summary-en")))
        val binding = CozyArticleMediaSiteBinding.plan(config.media)
        val before = _tree_bytes(fixture.root)

        When("the selected registration snapshot is planned")
        val plan = CozyDocumentProjectPublicationTarget.planRegistration(config)

        Then("only the producer-selected original candidate is retained")
        plan.siteBinding shouldBe binding
        plan.siteBinding.config.target shouldBe Some("summary-en")
        plan.siteBinding.candidates.map(_.resourceId) shouldBe Vector("summary-en")
        plan.siteBinding.candidates.map(_.locale) shouldBe Vector("en")
        plan.siteContext.map(_.source) shouldBe Some(fixture.site.resolve("content/article.dox"))
        _tree_bytes(fixture.root) shouldBe before
      }
    }

    "reject half-paired site options through the producer resolver without writes" in {
      _with_fixture("half-paired") { fixture =>
        Given("a valid export with each possible half of the site context pair")
        val original = _registration(fixture, paired = true)
        val configs = Vector(
          original.copy(media = original.media.copy(siteConfig = None)),
          original.copy(media = original.media.copy(siteRoot = None))
        )
        val before = _tree_bytes(fixture.root)

        When("the half-paired registration requests are planned")
        val failures = configs.map(config => intercept[RuntimeException] {
          CozyDocumentProjectPublicationTarget.planRegistration(config)
        })

        Then("Phase 55 rejects both incomplete pairs and leaves the whole fixture unchanged")
        failures.foreach(_.getMessage should include("must be supplied together"))
        _tree_bytes(fixture.root) shouldBe before
      }
    }

    "reject a site root outside the selected publication profile" in {
      _with_fixture("outside-profile") { fixture =>
        Given("an explicit direct site root outside the original release profile")
        val outside = Files.createDirectory(fixture.media.resolve("outside"))
        _write(outside.resolve("site.conf"), "site.title = Outside\n")
        val original = _registration(fixture, paired = true)
        val config = original.copy(media = original.media.copy(siteRoot = Some(outside), siteConfig = Some(outside.resolve("site.conf"))))
        val before = _tree_bytes(fixture.root)

        When("registration is planned with the outside root")
        val failure = intercept[RuntimeException] {
          CozyDocumentProjectPublicationTarget.planRegistration(config)
        }

        Then("the producer containment rule rejects the context without writes")
        failure.getMessage should include("contained by the selected SmartDox profile root")
        _tree_bytes(fixture.root) shouldBe before
      }
    }

    "reject site configuration and knowledge source outside the canonical site root" in {
      _with_fixture("outside-routes") { fixture =>
        Given("an outside configuration and a nested site root that excludes the original knowledge source")
        val outsideconfig = fixture.media.resolve("outside.conf")
        _write(outsideconfig, "site.title = Outside\n")
        val nestedroot = Files.createDirectory(fixture.site.resolve("nested-site"))
        _write(nestedroot.resolve("site.conf"), "site.title = Nested\n")
        val original = _registration(fixture, paired = true)
        val configs = Vector(
          original.copy(media = original.media.copy(siteConfig = Some(outsideconfig))),
          original.copy(media = original.media.copy(siteRoot = Some(nestedroot), siteConfig = Some(nestedroot.resolve("site.conf"))))
        )
        val before = _tree_bytes(fixture.root)

        When("registration is planned for each route escaping its canonical site root")
        val failures = configs.map(config => intercept[RuntimeException] {
          CozyDocumentProjectPublicationTarget.planRegistration(config)
        })

        Then("the producer rejects both configuration and document routes without writes")
        failures.head.getMessage should include("Media site config must be contained by the canonical site root")
        failures.last.getMessage should include("Media site document source must be contained by the canonical site root")
        _tree_bytes(fixture.root) shouldBe before
      }
    }

    "reject an aliased site root through the existing canonical context resolver" in {
      _with_fixture("alias") { fixture =>
        Given("an explicit site root that is a symlink to the original publication root")
        val alias = fixture.media.resolve("site-alias")
        Files.createSymbolicLink(alias, fixture.site)
        val original = _registration(fixture, paired = true)
        val config = original.copy(media = original.media.copy(siteRoot = Some(alias)))
        val before = _tree_bytes(fixture.root)

        When("registration is planned through the alias")
        val failure = intercept[RuntimeException] {
          CozyDocumentProjectPublicationTarget.planRegistration(config)
        }

        Then("the producer rejects the symlink and preserves all direct files and the alias")
        failure.getMessage should include("direct non-symlink directory")
        Files.readSymbolicLink(alias) shouldBe fixture.site
        _tree_bytes(fixture.root) shouldBe before
      }
    }

    "re-admit portable evidence and reject an export damaged after earlier admission" in {
      _with_fixture("damaged") { fixture =>
        Given("a genuine previously admitted export whose Article review was subsequently damaged")
        val earlier = CozyDocumentProjectPublicationTarget.admit(fixture.bundle)
        _write(earlier.articleReview, "damaged output\n")
        val config = _registration(fixture, paired = true)
        val before = _tree_bytes(fixture.root)

        When("registration is planned from the original bundle path")
        val failure = intercept[RuntimeException] {
          CozyDocumentProjectPublicationTarget.planRegistration(config)
        }

        Then("fresh portable verification rejects the damaged bundle without relying on the earlier snapshot")
        failure.getMessage should include("DP-OP-001")
        _tree_bytes(fixture.root) shouldBe before
      }
    }

    "reject null orchestration inputs with the operation diagnostic" in {
      Given("absent registration, media configuration, and bundle path")
      val configs = Vector(
        null,
        CozyDocumentProjectPublicationTarget.RegistrationConfig(Paths.get("unused"), null),
        CozyDocumentProjectPublicationTarget.RegistrationConfig(null, CozyArticleMediaSiteBinding.Config(Paths.get("unused-media.yaml")))
      )

      When("each absent orchestration input is planned")
      val failures = configs.map(config => intercept[RuntimeException] {
        CozyDocumentProjectPublicationTarget.planRegistration(config)
      })

      Then("each input fails closed with DP-OP-001")
      failures.foreach(_.getMessage should include("DP-OP-001"))
    }
  }

  private final case class Fixture(root: Path, bundle: Path, media: Path, descriptor: Path, site: Path)

  private def _registration(fixture: Fixture, paired: Boolean = false): CozyDocumentProjectPublicationTarget.RegistrationConfig =
    CozyDocumentProjectPublicationTarget.RegistrationConfig(
      fixture.bundle,
      CozyArticleMediaSiteBinding.Config(
        fixture.descriptor,
        siteRoot = if (paired) Some(fixture.site) else None,
        siteConfig = if (paired) Some(fixture.site.resolve("site.conf")) else None
      )
    )

  private def _media_plan(binding: CozyArticleMediaSiteBinding.Plan): CozyMedia.Plan =
    CozyMedia.resolvePlan(CozyMedia.CommandConfig(
      binding.descriptorEvidence.path,
      target = binding.config.target,
      profile = Some(binding.publicationProfile),
      siteRoot = binding.config.siteRoot,
      siteConfig = binding.config.siteConfig
    ), binding.descriptorBytes)

  private def _with_fixture(name: String, overlay: Boolean = false)(body: Fixture => Unit): Unit = {
    val work = Files.createDirectories(Paths.get("target/document-project-publication-registration-spec/work").toAbsolutePath.normalize())
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
      body(Fixture(root, bundle, media, descriptor, site))
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
