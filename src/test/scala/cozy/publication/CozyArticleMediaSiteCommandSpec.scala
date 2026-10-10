package cozy.publication

import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import cozy.media.CozyMedia
import cozy.scaffold.CozyScaffold
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.{ImageReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsArray, JsNull, JsObject, JsString, Json}

/*
 * @since   Aug. 11, 2026
 *  version Aug. 30, 2026
 *  version Sep. 22, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private object SiteCommandPart5Fixture {
  final class Data(
    val root: Path,
    val descriptor: Path,
    val profileroot: Path,
    val registryroot: Path
  )
}

final class CozyArticleMediaSiteCommandSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaSiteCommand" should {
    "register declared Part 5 site media" which {
      "dispatch all exact candidates as provider-neutral strict records" in {
        _with_part5_fixture("all-candidates") { fixture =>
          Given("a normal Part 5 media package and an existing direct publication registry root")
          val expected = _expected_output(fixture, "registered")

          When("the Cozy media command dispatches register-site")
          val dispatched = _dispatch(List(
            "media",
            "register-site",
            fixture.descriptor.toString,
            "--publication",
            fixture.registryroot.toString
          ))
          val snapshot = CozyArticleMediaRegistry.load(fixture.registryroot)
          val strict = _strict(snapshot, "development-process/part-5")

          Then("the deterministic command result identifies only declared resource IDs, exact locales, and nested roles")
          dispatched._1 shouldBe true
          dispatched._2 shouldBe expected

          And("the Japanese and English infographic and video fields remain strictly provider-neutral")
          (((strict \ "variants").as[JsObject] \ "ja" \ "infographic" \ "public_path").as[String]) shouldBe
            "/ja/development-process/images/part-5/summary.png"
          (((strict \ "variants").as[JsObject] \ "ja" \ "infographic" \ "media_type").as[String]) shouldBe "image/png"
          (((strict \ "variants").as[JsObject] \ "ja" \ "infographic" \ "alt").as[String]) shouldBe "Part 5 summary"
          (((strict \ "variants").as[JsObject] \ "en" \ "infographic" \ "public_path").as[String]) shouldBe
            "/en/development-process/images/part-5/summary.png"
          (((strict \ "variants").as[JsObject] \ "ja" \ "video" \ "presentation").as[String]) shouldBe "external-link"
          (((strict \ "variants").as[JsObject] \ "ja" \ "video" \ "status").as[String]) shouldBe "published"
          (((strict \ "variants").as[JsObject] \ "ja" \ "video" \ "provider").as[String]) shouldBe "youtube"
          (((strict \ "variants").as[JsObject] \ "ja" \ "video" \ "watch_url").as[String]) shouldBe "https://youtu.be/ja_Part5-1"
          (((strict \ "variants").as[JsObject] \ "en" \ "video" \ "watch_url").as[String]) shouldBe
            "https://www.youtube.com/watch?v=en_Part5-2"

          And("registration synthesizes no Cozy integrity record or internal evidence field")
          snapshot.entries.exists(_.path.startsWith("metadata/article-media-integrity/")) shouldBe false
          Json.stringify(strict) should not include "sha256"
          Json.stringify(strict) should not include "provenance"
          Json.stringify(strict) should not include "content_url"
          Json.stringify(strict) should not include "projectRoot"
          Json.stringify(strict) should not include "projectMarker"
          Json.stringify(strict) should not include "profileLayer"
          Json.stringify(strict) should not include "profileSource"
          Json.stringify(strict) should not include "profileSiteKind"
          Json.stringify(strict) should not include "sourcePath"
          dispatched._2 should not include "production.json"
          dispatched._2 should not include "summary-ja.png"
        }
      }

      "preserve generic metadata, unrelated articles, other locales, and existing integrity evidence" in {
        _with_part5_fixture("preservation") { fixture =>
          Given("one owner with pre-existing generic, unrelated, strict other-locale, and integrity records")
          val identity = "development-process/part-5"
          val previous = CozyArticleMediaPublication.produce(identity, Vector(
            CozyArticleMediaPublication.Variant("fr", infographic = Some(_image("/fr/development-process/images/part-5/summary.png")))
          ))
          val unrelated = CozyArticleMediaPublication.produce("development-process/other", Vector(
            CozyArticleMediaPublication.Variant("ja", infographic = Some(_image("/ja/development-process/images/other.png")))
          ))
          val integrity = _integrity(identity, "ja", CozyArticleMediaIntegrity.Role.Video)
          _write_bundle(fixture.registryroot, "owner", Vector(
            _entry(previous.entryPath, previous.metadata),
            _entry(unrelated.entryPath, unrelated.metadata),
            _entry(integrity.entryPath, integrity.metadata),
            _entry("metadata/generic.json", Json.obj("kept" -> true))
          ))
          val beforeintegrity = CozyArticleMediaRegistry.load(fixture.registryroot).entries.find(_.path == integrity.entryPath).map(_.metadata)

          When("all declared Part 5 candidates are registered")
          CozyArticleMediaSiteCommand.execute(_config(fixture))
          val snapshot = CozyArticleMediaRegistry.load(fixture.registryroot)
          val strict = _strict(snapshot, identity)

          Then("the unrequested locale remains unchanged while exact declared roles are registered")
          (((strict \ "variants").as[JsObject] \ "fr" \ "infographic" \ "public_path").as[String]) shouldBe
            "/fr/development-process/images/part-5/summary.png"
          (((strict \ "variants").as[JsObject] \ "ja" \ "video" \ "watch_url").as[String]) shouldBe "https://youtu.be/ja_Part5-1"

          And("generic, unrelated, and existing integrity metadata remain untouched")
          snapshot.entries.find(_.path == "metadata/generic.json").map(_.metadata) shouldBe Some(Json.obj("kept" -> true))
          snapshot.entries.find(_.path == unrelated.entryPath).map(_.metadata) shouldBe Some(unrelated.metadata)
          snapshot.entries.find(_.path == integrity.entryPath).map(_.metadata) shouldBe beforeintegrity
        }
      }

      "register one exact declared target without enrolling another resource" in {
        _with_part5_fixture("target") { fixture =>
          Given("a Part 5 package with four declarations and one selected video target")
          val config = _config(fixture, target = Some("part-5-video-ja"))

          When("the command registers the exact target")
          val output = CozyArticleMediaSiteCommand.execute(config)
          val strict = _strict(CozyArticleMediaRegistry.load(fixture.registryroot), "development-process/part-5")

          Then("only the selected Japanese video role is present")
          output shouldBe _expected_output(fixture, "registered", Vector("part-5-video-ja"))
          (strict \ "variants").as[JsObject].keys shouldBe Set("ja")
          (((strict \ "variants").as[JsObject] \ "ja" \ "video" \ "watch_url").as[String]) shouldBe "https://youtu.be/ja_Part5-1"
          ((strict \ "variants").as[JsObject] \ "ja" \ "infographic").toOption shouldBe empty
        }
      }
    }

    "plan without mutation" which {
      "report the exact dry-run plan without creating a bundle or directory" in {
        _with_part5_fixture("dry-run") { fixture =>
          Given("an existing direct registry root containing one generic bundle")
          _write_bundle(fixture.registryroot, "owner", Vector(_entry("metadata/generic.json", Json.obj("kept" -> true))))
          val before = _registry_tree(fixture.registryroot)

          When("register-site performs its complete dry-run preflight")
          val output = CozyArticleMediaSiteCommand.execute(_config(fixture, dryrun = true))

          Then("the result reports the same deterministic candidate plan")
          output shouldBe _expected_output(fixture, "dry-run")

          And("the registry tree remains byte-for-byte unchanged without a generated strict bundle")
          _registry_tree(fixture.registryroot) shouldBe before
          Files.exists(fixture.registryroot.resolve("article-media.json")) shouldBe false
        }
      }

      "reject a missing publication root without recreating it" in {
        _with_part5_fixture("missing-publication-root") { fixture =>
          Given("a media descriptor whose configured publication root has been removed")
          _delete(fixture.registryroot)

          When("register-site attempts a dry-run against the missing root")
          val error = _failure(CozyArticleMediaSiteCommand.execute(_config(fixture, dryrun = true)))

          Then("the command fails closed without recreating the publication root")
          error.getMessage should include("existing direct non-symlink directory")
          Files.exists(fixture.registryroot) shouldBe false
        }
      }

    }

    "reject invalid registration plans before mutation" which {
      "reject zero candidates, invalid targets, and duplicate locale-role declarations" in {
        Given("three Part 5 descriptor variants that cannot produce one exact registration plan")

        When("register-site preflights each invalid declaration set")
        val zero = _with_part5_fixture("zero-candidates", _part5_yaml(
          summaryjabinding = "",
          summaryenbinding = "",
          videojabinding = "",
          videoenbinding = ""
        )) { fixture =>
          val before = _registry_tree(fixture.registryroot)
          val error = _failure(CozyArticleMediaSiteCommand.execute(_config(fixture)))
          (error, _registry_tree(fixture.registryroot), before,
            Files.exists(fixture.registryroot.resolve("article-media.json")))
        }
        val target = _with_part5_fixture("invalid-target") { fixture =>
          val before = _registry_tree(fixture.registryroot)
          val error = _failure(CozyArticleMediaSiteCommand.execute(_config(fixture, target = Some("part-5-missing"))))
          (error, _registry_tree(fixture.registryroot), before,
            Files.exists(fixture.registryroot.resolve("article-media.json")))
        }
        val duplicate = _with_part5_fixture("duplicate-role", _part5_yaml().replace(
          "  - id: part-5-summary-en\n    kind: infographic\n    language: en",
          "  - id: part-5-summary-en\n    kind: infographic\n    language: ja"
        )) { fixture =>
          val before = _registry_tree(fixture.registryroot)
          val error = _failure(CozyArticleMediaSiteCommand.execute(_config(fixture)))
          (error, _registry_tree(fixture.registryroot), before,
            Files.exists(fixture.registryroot.resolve("article-media.json")))
        }

        Then("all candidate failures leave their existing roots and strict bundles untouched")
        zero._1.getMessage should include("at least one")
        zero._2 shouldBe zero._3
        zero._4 shouldBe false
        target._1.getMessage should include("does not exist")
        target._2 shouldBe target._3
        target._4 shouldBe false
        duplicate._1.getMessage should include("Duplicate")
        duplicate._2 shouldBe duplicate._3
        duplicate._4 shouldBe false
      }

      "reject name, path, standard-BoK, credits, profile-only, and incomplete project configuration substitutes" in {
        Given("registry roots with media descriptors that lack one required registration authority")

        When("register-site preflights each non-authorizing configuration")
        val outcomes = Vector(
          _with_part5_fixture("standard-bok-config") { fixture =>
            val before = _registry_tree(fixture.registryroot)
            _write(fixture.root.resolve("conf/cozy/config.yaml"), _project_config_yaml(projectkind = "standard-bok"))
            (_failure(CozyArticleMediaSiteCommand.execute(_config(fixture))), _registry_tree(fixture.registryroot), before)
          },
          _with_part5_fixture("repository-name-config") { fixture =>
            val before = _registry_tree(fixture.registryroot)
            _write(fixture.root.resolve("conf/cozy/config.yaml"),
              "project:\n  id: smartdox-site-by-name\n  kind: smartdox-site\nrepository:\n  path: /standard-bok\n")
            (_failure(CozyArticleMediaSiteCommand.execute(_config(fixture))), _registry_tree(fixture.registryroot), before)
          },
          _with_part5_fixture("credits-config") { fixture =>
            val before = _registry_tree(fixture.registryroot)
            _write(fixture.root.resolve("conf/cozy/config.yaml"),
              "project:\n  id: simplemodeling-org\n  kind: smartdox-site\nvideo:\n  credits:\n    default-profile: site\n")
            (_failure(CozyArticleMediaSiteCommand.execute(_config(fixture))), _registry_tree(fixture.registryroot), before)
          },
          _with_part5_fixture("profile-only", _part5_yaml(association = "")) { fixture =>
            val before = _registry_tree(fixture.registryroot)
            (_failure(CozyArticleMediaSiteCommand.execute(_config(fixture))), _registry_tree(fixture.registryroot), before)
          }
        )

        Then("none of the descriptive substitutes writes a strict record")
        outcomes.foreach { outcome =>
          outcome._1.getMessage should not be empty
          outcome._2 shouldBe outcome._3
        }
      }
    }

    "register accepted Phase 40 PDF site media" which {
      "emit exact strict PDF fields while retaining existing media and unrelated state" in {
        _with_part5_pdf_fixture("pdf-registration") { fixture =>
          Given("a normal Part 5 package with current JA/EN article and summary PDF evidence")
          _write_bundle(fixture.registryroot, "owner", Vector(_entry("metadata/generic.json", Json.obj("kept" -> true))))

          When("the normal register-site command registers all declared candidates")
          val output = CozyArticleMediaSiteCommand.execute(_config(fixture))
          val snapshot = CozyArticleMediaRegistry.load(fixture.registryroot)
          val strict = _strict(snapshot, "development-process/part-5")
          val variants = (strict \ "variants").as[JsObject]

          Then("article and summary PDFs map to exact locale variants and strict fields")
          output should include("part-5-article-pdf-ja: locale=ja, role=article_pdf")
          output should include("part-5-summary-pdf-en: locale=en, role=summary_slides_pdf")
          ((variants \ "ja" \ "article_pdf").as[JsObject]).fields.map(_._1) shouldBe
            Vector("public_path", "media_type", "label")
          ((variants \ "en" \ "article_pdf").as[JsObject]).fields.map(_._1) shouldBe
            Vector("public_path", "media_type")
          ((variants \ "ja" \ "summary_slides_pdf").as[JsObject]).fields.map(_._1) shouldBe
            Vector("public_path", "media_type", "label")
          ((variants \ "en" \ "summary_slides_pdf").as[JsObject]).fields.map(_._1) shouldBe
            Vector("public_path", "media_type")
          ((variants \ "ja" \ "article_pdf" \ "public_path").as[String]) shouldBe
            "/ja/development-process/pdf/part-5/article.pdf"
          ((variants \ "ja" \ "article_pdf" \ "media_type").as[String]) shouldBe "application/pdf"
          ((variants \ "ja" \ "article_pdf" \ "label").as[String]) shouldBe "Article PDF ja"
          ((variants \ "en" \ "summary_slides_pdf" \ "public_path").as[String]) shouldBe
            "/en/development-process/pdf/part-5/summary.pdf"
          ((variants \ "en" \ "summary_slides_pdf" \ "media_type").as[String]) shouldBe "application/pdf"

          And("existing infographic/video roles and unrelated registry state remain intact")
          ((variants \ "ja" \ "infographic" \ "public_path").as[String]) shouldBe
            "/ja/development-process/images/part-5/summary.png"
          ((variants \ "ja" \ "video" \ "watch_url").as[String]) shouldBe "https://youtu.be/ja_Part5-1"
          snapshot.entries.find(_.path == "metadata/generic.json").map(_.metadata) shouldBe Some(Json.obj("kept" -> true))
          snapshot.entries.exists(_.path.startsWith("metadata/article-media-integrity/")) shouldBe false
          Json.stringify(strict) should not include "sha256"
          Json.stringify(strict) should not include "receipt"
          Json.stringify(strict) should not include "renderer"
        }
      }

      "select only the requested PDF target and keep dry-run immutable" in {
        _with_part5_pdf_fixture("pdf-target") { fixture =>
          Given("a package whose accepted PDF evidence includes four exact locale-role candidates")

          When("register-site targets only the Japanese summary PDF")
          val targetoutput = CozyArticleMediaSiteCommand.execute(_config(fixture, target = Some("part-5-summary-pdf-ja")))
          val targetstrict = _strict(CozyArticleMediaRegistry.load(fixture.registryroot), "development-process/part-5")
          val targetvariants = (targetstrict \ "variants").as[JsObject]

          Then("the target selects only its declared locale and role")
          targetoutput shouldBe _expected_pdf_output(fixture, "registered", Vector("part-5-summary-pdf-ja"))
          targetvariants.keys shouldBe Set("ja")
          (targetvariants \ "ja" \ "summary_slides_pdf").toOption should not be empty
          (targetvariants \ "ja" \ "article_pdf").toOption shouldBe empty

          And("dry-run performs complete preflight without changing the registry")
          val before = _registry_tree(fixture.registryroot)
          val dryrun = CozyArticleMediaSiteCommand.execute(_config(fixture, dryrun = true))
          dryrun shouldBe _expected_pdf_output(fixture, "dry-run")
          _registry_tree(fixture.registryroot) shouldBe before
        }
      }

      "refuse missing or stale PDF evidence before registry mutation" in {
        _with_part5_pdf_fixture("pdf-missing-evidence") { fixture =>
          Given("accepted PDF receipts with their PDF review state removed")
          val before = _registry_tree(fixture.registryroot)
          val state = fixture.root.resolve("target/cozy-media/pdf-review-state.json")
          Files.delete(state)

          When("the site command evaluates the missing PDF review-state evidence")
          val error = _failure(CozyArticleMediaSiteCommand.execute(_config(fixture)))

          Then("ordinary PDF currentness identifies the selected English resource and preserves the registry")
          error.getMessage should include("PDF review currentness")
          error.getMessage should include("part-5-article-pdf-en")
          error.getMessage should include(state.toString)
          error.getMessage should include("producer prebuilt")
          error.getMessage should include(s"cozy media build ${fixture.descriptor} --target part-5-article-pdf-en")
          _registry_tree(fixture.registryroot) shouldBe before
        }
        _with_part5_fixture(
          "pdf-command-role-incompatible",
          _part5_pdf_yaml().replace(
            "  - id: part-5-article-pdf-ja\n    kind: document",
            "  - id: part-5-article-pdf-ja\n    kind: infographic"
          )
        ) { fixture =>
          Given("an article PDF resource declared with the incompatible infographic kind")
          val before = _registry_tree(fixture.registryroot)

          When("the site command evaluates the incompatible PDF resource role")
          val error = _failure(CozyArticleMediaSiteCommand.execute(_config(fixture)))

          Then("the document-kind requirement rejects registration and preserves the registry")
          error.getMessage should include("requires kind document")
          _registry_tree(fixture.registryroot) shouldBe before
        }
      }

      "register site-aware PDF receipts only for the current paired site context" in {
        val sources = Vector(
          "ordinary-dox" -> ("development-process/domain-modeling.dox", "development-process/domain-modeling-route.dox"),
          "document-project" -> ("development-process/domain-modeling.dox/index.dox", "development-process/domain-modeling.dox/route/index.dox")
        )
        sources.foreach { case (name, routes) =>
          _with_site_context_pdf_fixture(name, routes._1) { fixture =>
            Given(s"a $name authoring descriptor whose PDF receipt was built with one paired site context")
            val siteconfig = fixture.root.resolve("site.conf")
            val context = CozyMedia.CommandConfig(
              fixture.descriptor,
              profile = Some("site"),
              siteRoot = Some(fixture.root),
              siteConfig = Some(siteconfig)
            )
            val args = List(
              "media",
              "register-site",
              fixture.descriptor.toString,
              "--publication",
              fixture.registryroot.toString,
              "--site-root",
              fixture.root.toString,
              "--site-config",
              siteconfig.toString
            )
            val descriptorbytes = Files.readAllBytes(fixture.descriptor).toVector
            val pdf = fixture.root.resolve("output/article.pdf")
            val pdfbytes = Files.readAllBytes(pdf).toVector
            val receipt = fixture.root.resolve("target/cozy-media/manifest.json")
            val receiptbytes = Files.readAllBytes(receipt).toVector
            val state = fixture.root.resolve("target/cozy-media/pdf-review-state.json")
            val statebytes = Files.readAllBytes(state).toVector

            When("the accepted PDF is registered with its paired site context")
            val currentoutput = CozyArticleMediaSiteCommand.execute(CozyArticleMediaSiteCommand.Config.create(args.drop(2)))
            val currenttree = _registry_tree(fixture.registryroot)

            Then("initial registration accepts the declared article PDF")
            currentoutput should include("article-pdf: locale=ja, role=article_pdf")

            Given("the same accepted prebuilt PDF and canonical registry bytes")
            When("the site configuration base URL changes without rebuilding the accepted PDF")
            _write(siteconfig, "site.base-url = https://changed.example.test\n")
            Files.setLastModifiedTime(siteconfig, FileTime.fromMillis(4102444800000L))
            val configoutput = CozyArticleMediaSiteCommand.execute(CozyArticleMediaSiteCommand.Config.create(args.drop(2)))

            Then("configuration content and newer mtime preserve prebuilt acceptance and every accepted artifact byte")
            configoutput should include("article-pdf: locale=ja, role=article_pdf")
            _registry_tree(fixture.registryroot) shouldBe currenttree
            Files.readAllBytes(fixture.descriptor).toVector shouldBe descriptorbytes
            Files.readAllBytes(pdf).toVector shouldBe pdfbytes
            Files.readAllBytes(receipt).toVector shouldBe receiptbytes
            Files.readAllBytes(state).toVector shouldBe statebytes

            Given("a newly declared document source route without rebuilt PDF evidence")
            _write(fixture.root.resolve(routes._2), s"${name} route")
            val descriptorafterconfig = Files.readString(fixture.descriptor, StandardCharsets.UTF_8)
            _write(fixture.descriptor, descriptorafterconfig.replace(routes._1, routes._2))
            val routetree = _registry_tree(fixture.registryroot)

            When("registration checks the changed document source route")
            val staleroute = _failure(CozyArticleMediaSiteCommand.execute(CozyArticleMediaSiteCommand.Config.create(args.drop(2))))

            Then("ordinary receipt currentness rejects the actual new route and preserves registry, PDF and receipt bytes")
            staleroute.getMessage should include("Media receipt currentness")
            staleroute.getMessage should include("article-pdf")
            staleroute.getMessage should include(fixture.root.resolve(routes._2).toString)
            staleroute.getMessage should include("producer prebuilt")
            staleroute.getMessage should include(s"cozy media build ${fixture.descriptor} --target article-pdf")
            _registry_tree(fixture.registryroot) shouldBe routetree
            Files.readAllBytes(pdf).toVector shouldBe pdfbytes
            Files.readAllBytes(receipt).toVector shouldBe receiptbytes

            Given("a regenerated PDF under the same newly declared document route")
            _write(pdf, s"%PDF-1.7\n${name} regenerated article")
            val regeneratedpdfbytes = Files.readAllBytes(pdf).toVector

            When("the route is rebuilt and registered with the same paired context")
            CozyMedia.build(context)
            val regenerateddescriptorbytes = Files.readAllBytes(fixture.descriptor).toVector
            CozyArticleMediaSiteCommand.execute(CozyArticleMediaSiteCommand.Config.create(args.drop(2)))
            val strict = _strict(CozyArticleMediaRegistry.load(fixture.registryroot), "development-process/domain-modeling")

            Then("registration succeeds with strict PDF media type and preserves the regenerated descriptor and output")
            (((strict \ "variants").as[JsObject] \ "ja" \ "article_pdf" \ "media_type").as[String]) shouldBe "application/pdf"
            Files.readAllBytes(fixture.descriptor).toVector shouldBe regenerateddescriptorbytes
            Files.readAllBytes(pdf).toVector shouldBe regeneratedpdfbytes
          }
        }
      }

      "reject unsafe or absent site context before registry replacement" in {
        val outcomes = Vector(
          "missing-pair" -> { fixture: SiteCommandPart5Fixture.Data =>
            List("media", "register-site", fixture.descriptor.toString, "--publication", fixture.registryroot.toString)
          },
          "relative-root" -> { fixture: SiteCommandPart5Fixture.Data =>
            List("media", "register-site", fixture.descriptor.toString, "--publication", fixture.registryroot.toString,
              "--site-root", "relative-site-root", "--site-config", fixture.root.resolve("site.conf").toString)
          },
          "root-symlink" -> { fixture: SiteCommandPart5Fixture.Data =>
            val alias = fixture.root.resolve("site-root-alias")
            Files.createSymbolicLink(alias, fixture.root)
            List("media", "register-site", fixture.descriptor.toString, "--publication", fixture.registryroot.toString,
              "--site-root", alias.toString, "--site-config", fixture.root.resolve("site.conf").toString)
          },
          "config-symlink" -> { fixture: SiteCommandPart5Fixture.Data =>
            val alias = fixture.root.resolve("site-config-alias.conf")
            Files.createSymbolicLink(alias, fixture.root.resolve("site.conf"))
            List("media", "register-site", fixture.descriptor.toString, "--publication", fixture.registryroot.toString,
              "--site-root", fixture.root.toString, "--site-config", alias.toString)
          },
          "config-ancestor-alias" -> { fixture: SiteCommandPart5Fixture.Data =>
            val alias = fixture.root.resolve("site-config-parent-alias")
            Files.createSymbolicLink(alias, fixture.root)
            List("media", "register-site", fixture.descriptor.toString, "--publication", fixture.registryroot.toString,
              "--site-root", fixture.root.toString, "--site-config", alias.resolve("site.conf").toString)
          },
          "config-outside-root" -> { fixture: SiteCommandPart5Fixture.Data =>
            val selectedroot = fixture.root.resolve("selected-site")
            Files.createDirectories(selectedroot)
            List("media", "register-site", fixture.descriptor.toString, "--publication", fixture.registryroot.toString,
              "--site-root", selectedroot.toString, "--site-config", fixture.root.resolve("site.conf").toString)
          },
          "source-outside-root" -> { fixture: SiteCommandPart5Fixture.Data =>
            val selectedroot = fixture.root.resolve("selected-site")
            Files.createDirectories(selectedroot)
            _write(selectedroot.resolve("site.conf"), "site.base-url = https://selected.example.test\n")
            List("media", "register-site", fixture.descriptor.toString, "--publication", fixture.registryroot.toString,
              "--site-root", selectedroot.toString, "--site-config", selectedroot.resolve("site.conf").toString)
          }
        )
        outcomes.foreach { case (name, command) =>
          _with_site_context_pdf_fixture("unsafe-" + name, "knowledge/part-5.dox") { fixture =>
            Given(s"an accepted registration and a $name unsafe or absent paired site context")
            val siteconfig = fixture.root.resolve("site.conf")
            val current = CozyArticleMediaSiteCommand.execute(CozyArticleMediaSiteCommand.Config.create(List(
              fixture.descriptor.toString, "--publication", fixture.registryroot.toString,
              "--site-root", fixture.root.toString, "--site-config", siteconfig.toString
            )))
            val unsafecommand = command(fixture).drop(2)
            val before = _registry_tree(fixture.registryroot)
            val receipt = fixture.root.resolve("target/cozy-media/manifest.json")
            val receiptbytes = Files.readAllBytes(receipt).toVector

            When(s"$name is parsed and executed")
            val error = _failure(CozyArticleMediaSiteCommand.execute(CozyArticleMediaSiteCommand.Config.create(unsafecommand)))

            Then(s"$name fails closed without replacing the accepted registry")
            error.getMessage should not be empty
            if (name == "relative-root") {
              error.getMessage should include("--site-root path must be absolute")
            }
            _registry_tree(fixture.registryroot) shouldBe before
            Files.readAllBytes(receipt).toVector shouldBe receiptbytes
            current should include("article-pdf: locale=ja, role=article_pdf")
          }
        }
      }
    }

      "expose the strict command surface" which {
      "accept equals and separated options while rejecting missing, unknown, extra, duplicate, and profile arguments" in {
        _with_part5_fixture("strict-cli") { fixture =>
          Given("an existing Part 5 descriptor and direct publication root")
          val descriptor = fixture.descriptor.toString
          val publication = fixture.registryroot.toString
          val siteroot = fixture.root.toString
          val siteconfig = fixture.root.resolve("site.conf").toString

          When("the typed command configuration parses accepted and rejected option forms")
          val equals = CozyArticleMediaSiteCommand.Config.create(List(descriptor, "--publication=" + publication, "--target=part-5-video-ja", "--site-root=" + siteroot, "--site-config=" + siteconfig, "--dry-run"))
          val separated = CozyArticleMediaSiteCommand.Config.create(List("--dry-run", descriptor, "--publication", publication, "--target", "part-5-video-ja", "--site-root", siteroot, "--site-config", siteconfig))
          val missing = _failure(CozyArticleMediaSiteCommand.Config.create(List(descriptor)))
          val unknown = _failure(CozyArticleMediaSiteCommand.Config.create(List(descriptor, "--publication", publication, "--unknown")))
          val extra = _failure(CozyArticleMediaSiteCommand.Config.create(List(descriptor, "extra", "--publication", publication)))
          val duplicate = _failure(CozyArticleMediaSiteCommand.Config.create(List(descriptor, "--publication", publication, "--publication=" + publication)))
          val duplicatesiteroot = _failure(CozyArticleMediaSiteCommand.Config.create(List(descriptor, "--publication", publication, "--site-root", siteroot, "--site-root=" + siteroot, "--site-config", siteconfig)))
          val duplicatesiteconfig = _failure(CozyArticleMediaSiteCommand.Config.create(List(descriptor, "--publication", publication, "--site-root", siteroot, "--site-config", siteconfig, "--site-config=" + siteconfig)))
          val partialsiteroot = _failure(CozyArticleMediaSiteCommand.Config.create(List(descriptor, "--publication", publication, "--site-root", siteroot)))
          val partialsiteconfig = _failure(CozyArticleMediaSiteCommand.Config.create(List(descriptor, "--publication", publication, "--site-config", siteconfig)))
          val profile = _failure(CozyArticleMediaSiteCommand.Config.create(List(descriptor, "--publication", publication, "--profile", "site")))
          val separatedleadingdash = _failure(CozyArticleMediaSiteCommand.Config.create(List(descriptor, "--publication", publication, "--target", "-part-5-video-ja")))
          val equalsleadingdash = CozyArticleMediaSiteCommand.Config.create(List(descriptor, "--publication", publication, "--target=-part-5-video-ja"))

          Then("only the established option forms create an exact typed command")
          equals.descriptorFile shouldBe fixture.descriptor.toAbsolutePath.normalize()
          equals.publicationRoot shouldBe fixture.registryroot.toAbsolutePath.normalize()
          equals.target shouldBe Some("part-5-video-ja")
          equals.dryRun shouldBe true
          equals.siteRoot shouldBe Some(fixture.root.toAbsolutePath.normalize())
          equals.siteConfig shouldBe Some(fixture.root.resolve("site.conf").toAbsolutePath.normalize())
          separated shouldBe equals

          And("all unsupported argument shapes fail before command execution")
          missing.getMessage should include("Missing --publication")
          unknown.getMessage should include("Unknown")
          extra.getMessage should include("exactly one media-file")
          duplicate.getMessage should include("Duplicate --publication")
          duplicatesiteroot.getMessage should include("Duplicate --site-root")
          duplicatesiteconfig.getMessage should include("Duplicate --site-config")
          partialsiteroot.getMessage should include("--site-root and --site-config")
          partialsiteconfig.getMessage should include("--site-root and --site-config")
          profile.getMessage should include("--profile")
          separatedleadingdash.getMessage should include("Missing value for --target")
          equalsleadingdash.target shouldBe Some("-part-5-video-ja")
        }
      }

        "execute an opted-in leading-dash target only through equals syntax" in {
          _with_part5_fixture("leading-dash-target", _part5_yaml().replace(
            "  - id: part-5-video-ja",
            "  - id: \"-part-5-video-ja\""
          )) { fixture =>
            Given("a descriptor whose declared video resource ID begins with a dash")
            val descriptor = fixture.descriptor.toString
            val publication = fixture.registryroot.toString
            val separated = _failure(_dispatch(List(
              "media",
              "register-site",
              descriptor,
              "--publication",
              publication,
              "--target",
              "-part-5-video-ja"
            )))

            When("register-site receives the same target in separated and equals forms")
            val equals = _dispatch(List(
              "media",
              "register-site",
              descriptor,
              "--publication",
              publication,
              "--target=-part-5-video-ja"
            ))
            val strict = _strict(CozyArticleMediaRegistry.load(fixture.registryroot), "development-process/part-5")

            Then("only equals syntax executes and selects the declared leading-dash resource")
            separated.getMessage should include("Missing value for --target")
            equals._1 shouldBe true
            equals._2 shouldBe Vector(
              "Cozy Media Register Site",
              s"descriptor: ${fixture.descriptor.toAbsolutePath.normalize()}",
              s"publication: ${fixture.registryroot.toAbsolutePath.normalize()}",
              "articleIdentity: development-process/part-5",
              "mode: registered",
              "  - -part-5-video-ja: locale=ja, role=video"
            ).mkString("\n")
            (strict \ "variants").as[JsObject].keys shouldBe Set("ja")
            (((strict \ "variants").as[JsObject] \ "ja" \ "video" \ "watch_url").as[String]) shouldBe "https://youtu.be/ja_Part5-1"
          }
        }
      "advertise register-site in general help without exposing internal evidence" in {
        Given("the public Cozy help text")

        When("the media registration command is described")
        val help = CozyScaffold.helpText
        val registersite = help.split("\\n").dropWhile(_ !=
          "  media register-site <media-file> --publication <dir> [--target <resource-id>] [--site-root <dir> --site-config <file>] [--dry-run]"
        ).take(2).mkString("\n")

        Then("the exact supported public syntax is visible")
        registersite should include("media register-site <media-file> --publication <dir> [--target <resource-id>] [--site-root <dir> --site-config <file>] [--dry-run]")

        And("the help does not expose evidence implementation fields")
        registersite should not include "descriptorEvidence"
        registersite should not include "sha256"
        registersite should not include "provenance"
      }
    }
  }

  private def _config(
    fixture: SiteCommandPart5Fixture.Data,
    target: Option[String] = None,
    dryrun: Boolean = false
  ): CozyArticleMediaSiteCommand.Config =
    CozyArticleMediaSiteCommand.Config(fixture.descriptor, fixture.registryroot, target, dryrun)

  private def _dispatch(args: List[String]): (Boolean, String) = {
    val bytes = new ByteArrayOutputStream()
    val output = new PrintStream(bytes, true, "UTF-8")
    try {
      val executed = Console.withOut(output)(CozyMedia.execute(args))
      output.flush()
      executed -> bytes.toString("UTF-8").trim
    }
    finally output.close()
  }

  private def _expected_output(
    fixture: SiteCommandPart5Fixture.Data,
    mode: String,
    selected: Vector[String] = Vector(
      "part-5-summary-en",
      "part-5-summary-ja",
      "part-5-video-en",
      "part-5-video-ja"
    )
  ): String = {
    val roles = Map(
      "part-5-summary-en" -> ("en", "infographic"),
      "part-5-summary-ja" -> ("ja", "infographic"),
      "part-5-video-en" -> ("en", "video"),
      "part-5-video-ja" -> ("ja", "video")
    )
    (Vector(
      "Cozy Media Register Site",
      s"descriptor: ${fixture.descriptor.toAbsolutePath.normalize()}",
      s"publication: ${fixture.registryroot.toAbsolutePath.normalize()}",
      "articleIdentity: development-process/part-5",
      s"mode: $mode"
    ) ++ selected.sorted.map { resource =>
      val role = roles(resource)
      s"  - $resource: locale=${role._1}, role=${role._2}"
    }).mkString("\n")
  }

  private def _expected_pdf_output(
    fixture: SiteCommandPart5Fixture.Data,
    mode: String,
    selected: Vector[String] = Vector(
      "part-5-article-pdf-en",
      "part-5-article-pdf-ja",
      "part-5-summary-pdf-en",
      "part-5-summary-pdf-ja",
      "part-5-summary-en",
      "part-5-summary-ja",
      "part-5-video-en",
      "part-5-video-ja"
    )
  ): String = {
    val roles = Map(
      "part-5-article-pdf-en" -> ("en", "article_pdf"),
      "part-5-article-pdf-ja" -> ("ja", "article_pdf"),
      "part-5-summary-en" -> ("en", "infographic"),
      "part-5-summary-ja" -> ("ja", "infographic"),
      "part-5-summary-pdf-en" -> ("en", "summary_slides_pdf"),
      "part-5-summary-pdf-ja" -> ("ja", "summary_slides_pdf"),
      "part-5-video-en" -> ("en", "video"),
      "part-5-video-ja" -> ("ja", "video")
    )
    (Vector(
      "Cozy Media Register Site",
      s"descriptor: ${fixture.descriptor.toAbsolutePath.normalize()}",
      s"publication: ${fixture.registryroot.toAbsolutePath.normalize()}",
      "articleIdentity: development-process/part-5",
      s"mode: $mode"
    ) ++ selected.sorted.map { resourceid =>
      val role = roles(resourceid)
      s"  - $resourceid: locale=${role._1}, role=${role._2}"
    }).mkString("\n")
  }

  private def _strict(snapshot: CozyArticleMediaRegistry.Snapshot, identity: String): JsObject =
    snapshot.entries.find(_.path == s"metadata/article-media/$identity.json").map(_.metadata.as[JsObject]).getOrElse(
      throw new IllegalStateException(s"Missing strict article-media record: $identity")
    )

  private def _image(publicpath: String): ImageReference =
    ImageReference(new URI(publicpath), Some("image/png"), Some("Existing image"))

  private def _integrity(
    identity: String,
    locale: String,
    role: CozyArticleMediaIntegrity.Role
  ): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = identity,
      locale = locale,
      role = role,
      artifact = Some(CozyArticleMediaIntegrity.Artifact("part-5", "1.0.0")),
      publicPath = new URI("/repository/video/part-5.mp4"),
      repositoryPath = "video/part-5.mp4",
      mediaType = "video/mp4",
      provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/part-5/manifest.json", "metadata/artifacts/repository/part-5.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Published
    ))

  private def _entry(path: String, metadata: JsObject): JsObject =
    Json.obj(
      "path" -> path,
      "key" -> JsString(path.stripPrefix("metadata/").stripSuffix(".json")),
      "metadata" -> metadata
    )

  private def _write_bundle(root: Path, name: String, entries: Vector[JsObject]): Unit = {
    val bundle = Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> name),
      "sourceRepository" -> "cozy",
      "sourcePath" -> ".",
      "sourceCommit" -> JsNull,
      "entries" -> JsArray(entries)
    )
    _write(root.resolve(s"$name.json"), Json.prettyPrint(bundle) + "\n")
  }

  private def _registry_tree(root: Path): Vector[(String, Vector[Byte])] = {
    val stream = Files.list(root)
    try stream.iterator().asScala.toVector.sortBy(_.getFileName.toString).map { path =>
      path.getFileName.toString -> Files.readAllBytes(path).toVector
    }
    finally stream.close()
  }

  private def _failure(value: => Any): RuntimeException =
    intercept[RuntimeException](value)

  private def _with_part5_fixture[A](name: String, yaml: String = _part5_yaml())(f: SiteCommandPart5Fixture.Data => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-site-command/part-5")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, name + "-")
    val descriptor = root.resolve("media.yaml")
    val profile = root.resolve("publication")
    val registry = root.resolve("registry")
    try {
      _write(root.resolve("conf/cozy/config.yaml"), _project_config_yaml())
      _write(descriptor, yaml)
      _write(profile.resolve("images/summary-ja.png"), "Part 5 summary ja")
      _write(profile.resolve("images/summary-en.png"), "Part 5 summary en")
      _write(root.resolve("video/ja/production.json"), _part5_production_json())
      _write(root.resolve("video/en/production.json"), _part5_production_json(
        language = "en",
        url = "https://www.youtube.com/watch?v=en_Part5-2"
      ))
      Files.createDirectories(registry)
      f(new SiteCommandPart5Fixture.Data(root, descriptor, profile, registry))
    } finally {
      _delete(root)
    }
  }

  private def _with_part5_pdf_fixture[A](name: String)(f: SiteCommandPart5Fixture.Data => A): A =
    _with_part5_fixture(name, _part5_pdf_yaml()) { fixture =>
      _write(fixture.root.resolve("knowledge/part-5.dox"), "Part 5 article authority")
      _write_png(fixture.root.resolve("input/summary-ja.png"))
      _write_png(fixture.root.resolve("input/summary-en.png"))
      _write_png(fixture.root.resolve("input/unbound.png"))
      _write(fixture.root.resolve("target/video-ja.mp4"), "Part 5 video ja")
      _write(fixture.root.resolve("target/video-en.mp4"), "Part 5 video en")
      _write(fixture.root.resolve("target/article-ja.pdf"), "%PDF-1.7\nPart 5 article ja")
      _write(fixture.root.resolve("target/article-en.pdf"), "%PDF-1.7\nPart 5 article en")
      _write(fixture.root.resolve("target/summary-ja.pdf"), "%PDF-1.7\nPart 5 summary ja")
      _write(fixture.root.resolve("target/summary-en.pdf"), "%PDF-1.7\nPart 5 summary en")
      CozyMedia.build(CozyMedia.CommandConfig(fixture.descriptor))
      f(fixture)
    }

  private def _with_site_context_pdf_fixture[A](name: String, source: String)(f: SiteCommandPart5Fixture.Data => A): A =
    _with_part5_fixture(name, _site_context_pdf_yaml(source)) { fixture =>
      _write(fixture.root.resolve("conf/cozy/config.yaml"), _project_config_yaml(root = "."))
      _write(fixture.root.resolve(source), s"= Domain Modeling\nsite:[/docs/$name]\n")
      _write(fixture.root.resolve("site.conf"), "site.base-url = https://example.test\n")
      _write(fixture.root.resolve("output/article.pdf"), "%PDF-1.7\nsite-aware article")
      CozyMedia.build(CozyMedia.CommandConfig(
        fixture.descriptor,
        profile = Some("site"),
        siteRoot = Some(fixture.root),
        siteConfig = Some(fixture.root.resolve("site.conf"))
      ))
      f(fixture)
    }

  private def _site_context_pdf_yaml(source: String): String =
    s"""schema: cozy.media.v1
       |knowledge:
       |  id: media-package/domain-modeling
       |  source: $source
       |profiles:
       |  site:
       |    root: .
       |articleMedia:
       |  articleIdentity: development-process/domain-modeling
       |  publicationProfile: site
       |resources:
       |  - id: article-pdf
       |    kind: document
       |    language: ja
       |    source: output/article.pdf
       |    build: prebuilt
       |    articleMedia:
       |      role: article_pdf
       |      publicPath: /ja/development-process/pdf/domain-modeling/article.pdf
       |      mediaType: application/pdf
       |      label: Domain Modeling PDF
       |""".stripMargin

  private def _part5_pdf_yaml(): String =
    _part5_yaml().stripSuffix("\n") +
      """
       |  - id: part-5-article-pdf-ja
       |    kind: document
       |    language: ja
       |    source: target/article-ja.pdf
       |    build: prebuilt
       |    articleMedia:
       |      role: article_pdf
       |      publicPath: /ja/development-process/pdf/part-5/article.pdf
       |      mediaType: application/pdf
       |      label: Article PDF ja
       |  - id: part-5-article-pdf-en
       |    kind: document
       |    language: en
       |    source: target/article-en.pdf
       |    build: prebuilt
       |    articleMedia:
       |      role: article_pdf
       |      publicPath: /en/development-process/pdf/part-5/article.pdf
       |      mediaType: application/pdf
       |  - id: part-5-summary-pdf-ja
       |    kind: document
       |    language: ja
       |    source: target/summary-ja.pdf
       |    build: prebuilt
       |    articleMedia:
       |      role: summary_slides_pdf
       |      publicPath: /ja/development-process/pdf/part-5/summary.pdf
       |      mediaType: application/pdf
       |      label: Summary slides PDF ja
       |  - id: part-5-summary-pdf-en
       |    kind: document
       |    language: en
       |    source: target/summary-en.pdf
       |    build: prebuilt
       |    articleMedia:
       |      role: summary_slides_pdf
       |      publicPath: /en/development-process/pdf/part-5/summary.pdf
       |      mediaType: application/pdf
      """.stripMargin

  private def _part5_yaml(
    association: String = "articleMedia:\n  articleIdentity: development-process/part-5\n  publicationProfile: site",
    summaryjabinding: String = _part5_infographic_binding("/ja/development-process/images/part-5/summary.png", "Part 5 summary"),
    summaryenbinding: String = _part5_infographic_binding("/en/development-process/images/part-5/summary.png", "Part 5 summary"),
    videojabinding: String = _part5_video_binding("video/ja/production.json"),
    videoenbinding: String = _part5_video_binding("video/en/production.json")
  ): String =
    s"""schema: cozy.media.v1
       |knowledge:
       |  id: media-package/part-5
       |  source: knowledge/part-5.dox
       |languages:
       |  - ja
       |  - en
       |profiles:
       |  site:
       |    root: publication
       |$association
       |resources:
       |  - id: part-5-summary-ja
       |    kind: infographic
       |    language: ja
       |    role: article-summary
       |    source: input/summary-ja.png
       |    build: prebuilt
       |    publications:
       |      site: images/summary-ja.png
       |$summaryjabinding
       |  - id: part-5-summary-en
       |    kind: infographic
       |    language: en
       |    role: article-summary
       |    source: input/summary-en.png
       |    build: prebuilt
       |    publications:
       |      site: images/summary-en.png
       |$summaryenbinding
       |  - id: part-5-video-ja
       |    kind: video
       |    language: ja
       |    role: article-introduction
       |    source: target/video-ja.mp4
       |    build: prebuilt
       |$videojabinding
       |  - id: part-5-video-en
       |    kind: video
       |    language: en
       |    role: article-introduction
       |    source: target/video-en.mp4
       |    build: prebuilt
       |$videoenbinding
       |  - id: part-5-unbound
       |    kind: infographic
       |    language: ja
       |    role: article-summary
       |    source: input/unbound.png
       |    build: prebuilt
       |""".stripMargin

  private def _project_config_yaml(
    root: String = "publication",
    projectkind: String = "smartdox-site",
    sitekind: String = "smartdox",
    projectid: String = "simplemodeling-org"
  ): String =
    s"""project:
      |  id: $projectid
      |  kind: $projectkind
      |media:
      |  publication-profiles:
      |    site:
      |      root: $root
      |      site-kind: $sitekind
      |""".stripMargin

  private def _part5_infographic_binding(publicpath: String, alt: String): String =
    s"""    articleMedia:
       |      role: infographic
       |      publicPath: $publicpath
       |      mediaType: image/png
       |      alt: $alt
       |""".stripMargin

  private def _part5_video_binding(production: String): String =
    s"""    articleMedia:
       |      role: video
       |      production: $production
       |""".stripMargin

  private def _part5_production_json(
    language: String = "ja",
    youtubestatus: String = "published",
    url: String = "https://youtu.be/ja_Part5-1",
    extra: String = "accepted"
  ): String =
    s"""{
       |  "category": "development-process",
       |  "article": "part-5",
       |  "language": "$language",
       |  "extra": {"state": "$extra"},
       |  "render": {
       |    "status": "completed",
       |    "qa": {"status": "technical-and-visual-qa-passed"},
       |    "listeningReview": {"status": "pending"}
       |  },
       |  "youtube": {"status": "$youtubestatus", "videoUrl": "$url"}
       |}
       |""".stripMargin

  private def _write_png(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
    javax.imageio.ImageIO.write(image, "png", path.toFile) shouldBe true
  }

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
}
