package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import cozy.media.CozyMedia
import cozy.media.CozyMediaPdfReviewState
import cozy.media.CozyMediaReceipt
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.{ImageReference, PdfDocumentReference, VideoPresentation, VideoStatus}

/*
 * @since   Aug. 11, 2026
 *  version Aug. 30, 2026
 *  version Sep. 22, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private object SiteBindingPart5Fixture {
  final class Data(
    val root: Path,
    val descriptor: Path,
    val profileroot: Path
  )
}

final class CozyArticleMediaSiteBindingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaSiteBinding" should {
    "prepare immutable deterministic Part 5 site-media evidence" which {
      "retain typed strict variants, original accepted URLs, and file evidence" in {
        _with_part5_fixture("deterministic") { fixture =>
          Given("a normal Part 5 package with distinct media knowledge and article identities")
          val config = CozyArticleMediaSiteBinding.Config(fixture.descriptor)

          When("the read-only site binding plan is prepared")
          val plan = CozyArticleMediaSiteBinding.plan(config)

          Then("the candidate identifiers, canonical locales, and nested roles are deterministic")
          plan.config shouldBe config
          plan.articleIdentity shouldBe "development-process/part-5"
          plan.descriptor.knowledge.id shouldBe "media-package/part-5"
          plan.context.project.map(_.root.path) shouldBe Some(fixture.root.toAbsolutePath.normalize())
          plan.context.project.map(_.marker.path) shouldBe Some(fixture.root.resolve("conf/cozy/config.yaml").toAbsolutePath.normalize())
          plan.effectiveProfile.id shouldBe "site"
          plan.effectiveProfile.layer shouldBe Some("project-conf")
          plan.effectiveProfile.sourcePath shouldBe Some(fixture.root.resolve("conf/cozy/config.yaml").toAbsolutePath.normalize())
          plan.effectiveProfile.siteKind shouldBe Some("smartdox")
          plan.candidates.map(_.resourceId) shouldBe Vector(
            "part-5-summary-en",
            "part-5-summary-ja",
            "part-5-video-en",
            "part-5-video-ja"
          )
          plan.candidates.map(x => x.resourceId -> x.locale) shouldBe Vector(
            "part-5-summary-en" -> "en",
            "part-5-summary-ja" -> "ja",
            "part-5-video-en" -> "en",
            "part-5-video-ja" -> "ja"
          )
          plan.candidates.map(_.role.serializedName) shouldBe Vector("infographic", "infographic", "video", "video")

          And("top-level production roles remain independent of registration")
          plan.descriptor.resources.find(_.id == "part-5-summary-ja").flatMap(_.role) shouldBe Some("article-summary")
          plan.descriptor.resources.find(_.id == "part-5-video-ja").flatMap(_.role) shouldBe Some("article-introduction")

          And("the strict images retain only declared SmartDox fields")
          val jaimage = _candidate(plan, "part-5-summary-ja")
          jaimage.role shouldBe CozyArticleMediaSiteBinding.Role.Infographic
          jaimage.variant.infographic shouldBe Some(ImageReference(
            new URI("/ja/development-process/images/part-5/summary.png"),
            Some("image/png"),
            Some("Part 5 summary")
          ))
          jaimage.variant.video shouldBe empty

          And("the strict videos are watch-only published YouTube links with original URL spelling")
          val javideo = _candidate(plan, "part-5-video-ja")
          val envideo = _candidate(plan, "part-5-video-en")
          javideo.role shouldBe CozyArticleMediaSiteBinding.Role.Video
          javideo.variant.video.map(_.presentation) shouldBe Some(VideoPresentation.ExternalLink)
          javideo.variant.video.map(_.status) shouldBe Some(VideoStatus.Published)
          javideo.variant.video.flatMap(_.provider) shouldBe Some("youtube")
          javideo.variant.video.flatMap(_.watchUrl).map(_.toString) shouldBe Some("https://youtu.be/ja_Part5-1")
          envideo.variant.video.flatMap(_.watchUrl).map(_.toString) shouldBe Some("https://www.youtube.com/watch?v=en_Part5-2")
          javideo.variant.video.flatMap(_.contentUrl) shouldBe empty
          envideo.variant.video.flatMap(_.contentUrl) shouldBe empty

          And("descriptor, infographic, and production evidence retains normalized lexical and real path identities")
          plan.descriptorEvidence.path shouldBe fixture.descriptor.toAbsolutePath.normalize()
          plan.descriptorEvidence.identity shouldBe plan.descriptorEvidence.path
          val imageevidence = jaimage.evidence.asInstanceOf[CozyArticleMediaSiteBinding.InfographicEvidence].destination
          imageevidence.path shouldBe fixture.profileroot.resolve("images/summary-ja.png").toAbsolutePath.normalize()
          imageevidence.identity shouldBe imageevidence.path
          val videoevidence = javideo.evidence.asInstanceOf[CozyArticleMediaSiteBinding.VideoEvidence].production
          videoevidence.path shouldBe fixture.root.resolve("video/ja/production.json").toAbsolutePath.normalize()
          videoevidence.identity shouldBe videoevidence.path

        }
      }
    }

    "bind only an explicit SmartDox project profile association" which {
      "accept configured-only and descriptor-overlay locations while rejecting implicit authorities" in {
        Given("Part 5 descriptors whose location and registration authorities vary independently")

        When("the binding planner resolves each declared boundary")
        val configuredonly = _with_part5_fixture("configured-only", _part5_yaml().replace("profiles:\n  site:\n    root: publication\n", "")) { fixture =>
          CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor))
        }
        val overlay = _with_part5_fixture("overlay") { fixture =>
          _write(fixture.root.resolve("conf/cozy/config.yaml"), _project_config_yaml("configured-publication"))
          CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor))
        }
        val wrongkind = _with_part5_fixture("wrong-project-kind") { fixture =>
          _write(fixture.root.resolve("conf/cozy/config.yaml"), _project_config_yaml("publication", projectkind = "ordinary"))
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }
        val missingkind = _with_part5_fixture("missing-project-kind") { fixture =>
          _write(fixture.root.resolve("conf/cozy/config.yaml"), _project_config_yaml().replace("  kind: smartdox-site\n", ""))
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }
        val wrongsitekind = _with_part5_fixture("wrong-site-kind") { fixture =>
          _write(fixture.root.resolve("conf/cozy/config.yaml"), _project_config_yaml("publication", sitekind = "other"))
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }
        val missingsitekind = _with_part5_fixture("missing-site-kind") { fixture =>
          _write(fixture.root.resolve("conf/cozy/config.yaml"), _project_config_yaml().replace("      site-kind: smartdox\n", ""))
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }
        val descriptoronly = _with_part5_fixture("descriptor-only") { fixture =>
          Files.delete(fixture.root.resolve("conf/cozy/config.yaml"))
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }
        val associationonly = _with_part5_fixture("association-only", _part5_yaml(association = "")) { fixture =>
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }
        val escape = _part5_plan_failure("ordinary-root-escape", _part5_yaml().replace("root: publication", "root: ../outside"))

        Then("configured definitions are reusable, but no descriptor, project, or profile substitute can authorize registration")
        configuredonly.profileRoot shouldBe configuredonly.context.project.get.root.path.resolve("publication")
        configuredonly.effectiveProfile.configuration should not be empty
        overlay.profileRoot shouldBe overlay.descriptorRoot.resolve("publication")
        overlay.effectiveProfile.configuration.map(_.resolvedRoot) shouldBe Some(overlay.context.project.get.root.path.resolve("configured-publication"))
        wrongkind.getMessage should include("project.kind")
        missingkind.getMessage should include("project.kind")
        wrongsitekind.getMessage should include("site-kind")
        missingsitekind.getMessage should include("site-kind")
        descriptoronly.getMessage should include("discovered project authority")
        associationonly.getMessage should include("top-level articleMedia")
        escape.getMessage should include("escapes descriptor root")
      }

      "admit an explicit direct external rootEnv location and reject its symlink evidence" in {
        Given("a configured SmartDox site whose descriptor explicitly chooses the canonical HOME root")
        val homeroot = Path.of(sys.env("HOME")).toRealPath()
        val externalroot = homeroot.resolve("src/dev2025/cozy/target/cozy-media-site-binding-root-env")

        try {
          When("the opted-in infographic is resolved through the external root")
          _with_part5_fixture("root-env", _part5_yaml().
            replace("root: publication", "root-env: HOME").
            replace("site: images/summary-ja.png", "site: src/dev2025/cozy/target/cozy-media-site-binding-root-env/summary-ja.png")) { fixture =>
            _write(externalroot.resolve("summary-ja.png"), "external Part 5 summary")
            val admitted = CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(
              fixture.descriptor,
              target = Some("part-5-summary-ja")
            ))
            val outside = fixture.root.resolve("outside-summary-ja.png")
            _write(outside, "outside")
            Files.delete(externalroot.resolve("summary-ja.png"))
            Files.createSymbolicLink(externalroot.resolve("summary-ja.png"), outside)
            val rejected = _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(
              fixture.descriptor,
              target = Some("part-5-summary-ja")
            )))

            Then("only the explicit direct external root is admitted and no link is followed")
            admitted.profileRoot shouldBe homeroot
            admitted.candidates.map(_.resourceId) shouldBe Vector("part-5-summary-ja")
            rejected.getMessage should include("direct regular")
          }
        } finally {
          _delete(externalroot)
        }
      }
    }

    "select declared Part 5 resources only" which {
      "narrow to one exact opted-in target without enrolling an unbound resource" in {
        _with_part5_fixture("target") { fixture =>
          Given("a Part 5 descriptor containing one unbound resource")

          When("an exact opted-in target is requested")
          val plan = CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(
            fixture.descriptor,
            target = Some("part-5-video-ja")
          ))

          Then("only that declared resource is selected")
          plan.candidates.map(_.resourceId) shouldBe Vector("part-5-video-ja")
          plan.candidates.head.role shouldBe CozyArticleMediaSiteBinding.Role.Video
          plan.descriptor.resources.find(_.id == "part-5-unbound").flatMap(_.articleMedia) shouldBe empty
        }
      }

      "reject missing association and invalid selection boundaries" in {
        Given("separate Part 5 descriptor declarations without a usable site candidate set")

        When("the binding planner receives missing association or zero declarations")
        val associationfailure = _part5_plan_failure("missing-association", _part5_yaml(association = ""))
        val zerofailure = _part5_plan_failure("zero-candidates", _part5_yaml(
          summaryjabinding = "",
          summaryenbinding = "",
          videojabinding = "",
          videoenbinding = ""
        ))
        val nonexistentfailure = _with_part5_fixture("nonexistent-target") { fixture =>
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(
            fixture.descriptor,
            target = Some("part-5-missing")
          )))
        }
        val unboundfailure = _with_part5_fixture("unbound-target") { fixture =>
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(
            fixture.descriptor,
            target = Some("part-5-unbound")
          )))
        }

        Then("a target cannot infer or create a registration association")
        associationfailure.getMessage should include("top-level articleMedia")
        zerofailure.getMessage should include("at least one")
        nonexistentfailure.getMessage should include("does not exist")
        unboundfailure.getMessage should include("has no articleMedia")
      }
    }

    "enforce exact Part 5 registration identity keys" which {
      "reject missing or noncanonical locales and duplicate locale-role candidates" in {
        Given("Part 5 descriptors with invalid candidate locale declarations")

        When("the binding planner normalizes the article identity and candidate keys")
        val noncanonical = _part5_plan_failure(
          "noncanonical-locale",
          _part5_yaml().replace("language: ja", "language: ja-jp")
        )
        val missing = _part5_plan_failure(
          "missing-locale",
          _part5_yaml().replace("    language: ja\n", "")
        )
        val duplicate = _part5_plan_failure(
          "duplicate-locale-role",
          _part5_yaml().replace(
            "id: part-5-summary-en\n    kind: infographic\n    language: en",
            "id: part-5-summary-en\n    kind: infographic\n    language: ja"
          )
        )

        Then("only exact canonical locale-role registrations are admitted")
        noncanonical.getMessage should include("locale")
        missing.getMessage should include("language")
        duplicate.getMessage should include("Duplicate")
      }
    }

    "require published Part 5 infographic evidence" which {
      "reject missing selected profile mappings, unsafe declarations, and non-direct destinations" in {
        Given("Part 5 infographic declarations with independent evidence failures")

        When("the read-only planner resolves only selected profile publication evidence")
        val missingmapping = _part5_plan_failure(
          "missing-publication-mapping",
          _part5_yaml(summaryjapublication = None)
        )
        val missingprofile = _part5_plan_failure(
          "missing-profile",
          _part5_yaml().replace("publicationProfile: site", "publicationProfile: missing")
        )
        val escape = _part5_plan_failure(
          "publication-escape",
          _part5_yaml().replace("site: images/summary-ja.png", "site: ../summary-ja.png")
        )
        val invalidpublicpath = _part5_plan_failure(
          "invalid-public-path",
          _part5_yaml().replace(
            "publicPath: /ja/development-process/images/part-5/summary.png",
            "publicPath: https://example.com/summary.png"
          )
        )
        val missingdestination = _with_part5_fixture("missing-destination") { fixture =>
          Files.delete(fixture.profileroot.resolve("images/summary-ja.png"))
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }
        val symlinkdestination = _with_part5_fixture("symlink-destination") { fixture =>
          val destination = fixture.profileroot.resolve("images/summary-ja.png")
          val outside = fixture.root.resolve("outside-summary-ja.png")
          _write(outside, "outside")
          Files.delete(destination)
          Files.createSymbolicLink(destination, outside)
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }

        Then("no inferred, escaped, missing, or symlink-backed infographic can register")
        missingmapping.getMessage should include("no selected publication profile")
        missingprofile.getMessage should include("configured publication profile")
        Vector("built-in", "user", "project-conf", "project-local", "package-conf", "package-local").foreach { layer =>
          missingprofile.getMessage should include(s"$layer:")
        }
        missingprofile.getMessage should include("site")
        missingprofile.getMessage should include("conf/cozy/config.yaml")
        escape.getMessage should include("escapes profile root")
        invalidpublicpath.getMessage should include("site-visible")
        missingdestination.getMessage should include("direct regular")
        symlinkdestination.getMessage should include("direct regular")
      }
    }

    "require accepted Part 5 video production evidence" which {
      "reject malformed production metadata, invalid lifecycle state, unsafe URLs, and symlinks" in {
        Given("Part 5 video production declarations outside the accepted evidence grammar")

        When("the planner parses the exact production file instead of a broad media scan")
        val malformed = _part5_production_failure("malformed-json", "{")
        val nonobject = _part5_production_failure("nonobject-json", "[]")
        val identity = _part5_production_failure("identity-mismatch", _part5_production_json(category = "other"))
        val language = _part5_production_failure("language-mismatch", _part5_production_json(language = "en"))
        val render = _part5_production_failure("render-mismatch", _part5_production_json(renderstatus = "pending"))
        val qa = _part5_production_failure("qa-mismatch", _part5_production_json(qastatus = "pending"))
        val youtube = _part5_production_failure("youtube-status-mismatch", _part5_production_json(youtubestatus = "pending"))
        val unsafeurls = Vector(
          "http://youtu.be/ja_Part5-1",
          "https://user@youtu.be/ja_Part5-1",
          "https://youtu.be/ja_Part5-1#fragment",
          "https://youtu.be:443/ja_Part5-1",
          "https://youtu.be/ja_Part5-1/extra",
          "https://youtu.be/ja_Part5-1?x=1",
          "https://www.youtube.com/watch?v=ja_Part5-1&x=1",
          "https://example.com/watch?v=ja_Part5-1"
        ).map(url => _part5_production_failure("unsafe-url-" + url.hashCode.abs, _part5_production_json(url = url)))
        val symlinkproduction = _with_part5_fixture("symlink-production") { fixture =>
          val production = fixture.root.resolve("video/ja/production.json")
          val outside = fixture.root.resolve("outside-production.json")
          _write(outside, _part5_production_json())
          Files.delete(production)
          Files.createSymbolicLink(production, outside)
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }

        Then("only completed technically-and-visually-approved published YouTube evidence is accepted")
        malformed.getMessage should include("JSON")
        nonobject.getMessage should include("JSON object")
        identity.getMessage should include("identity differs")
        language.getMessage should include("language differs")
        render.getMessage should include("render status")
        qa.getMessage should include("QA status")
        youtube.getMessage should include("YouTube status")
        unsafeurls.foreach(_.getMessage should include("URL"))
        symlinkproduction.getMessage should include("direct regular")
      }
    }

    "admit accepted Phase 40 PDF site resources" which {
      "capture direct output evidence and map each role to its exact strict variant" in {
        _with_part5_pdf_fixture("pdf-roles") { fixture =>
          Given("a Part 5 package with accepted article and summary PDF outputs in both canonical locales")

          When("the normal site binding resolves the accepted PDF resources")
          val plan = CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor))
          val pdfs = plan.candidates.filter(candidate =>
            candidate.role == CozyArticleMediaSiteBinding.Role.ArticlePdf ||
              candidate.role == CozyArticleMediaSiteBinding.Role.SummarySlidesPdf
          )

          Then("both PDF roles retain exact locale and deterministic direct-output evidence")
          pdfs.map(_.resourceId) shouldBe Vector(
            "part-5-article-pdf-en",
            "part-5-article-pdf-ja",
            "part-5-summary-pdf-en",
            "part-5-summary-pdf-ja"
          )
          pdfs.map(candidate => candidate.locale -> candidate.role.serializedName) shouldBe Vector(
            "en" -> "article_pdf",
            "ja" -> "article_pdf",
            "en" -> "summary_slides_pdf",
            "ja" -> "summary_slides_pdf"
          )
          val articleja = _candidate(plan, "part-5-article-pdf-ja")
          val articleen = _candidate(plan, "part-5-article-pdf-en")
          val summaryja = _candidate(plan, "part-5-summary-pdf-ja")
          val summaryen = _candidate(plan, "part-5-summary-pdf-en")
          articleja.variant.articlePdf shouldBe Some(PdfDocumentReference(
            new URI("/ja/development-process/pdf/part-5/article.pdf"),
            "application/pdf",
            Some("Article PDF ja")
          ))
          articleen.variant.articlePdf.map(_.label) shouldBe Some(None)
          summaryja.variant.summarySlidesPdf.map(_.label) shouldBe Some(Some("Summary slides PDF ja"))
          summaryen.variant.summarySlidesPdf.map(_.label) shouldBe Some(None)
          articleja.variant.summarySlidesPdf shouldBe empty
          summaryja.variant.articlePdf shouldBe empty
          val evidence = articleja.evidence.asInstanceOf[CozyArticleMediaSiteBinding.PdfEvidence].output
          evidence.path shouldBe fixture.root.resolve("target/article-ja.pdf").toAbsolutePath.normalize()
          evidence.identity shouldBe evidence.path

        }
      }

      "retain accepted PDF receipt and review-state evidence after article-only drift" in {
        _with_part5_pdf_fixture("pdf-retained-article-content") { fixture =>
          Given("accepted prebuilt article and summary PDF outputs with their exact review state")
          val initial = CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor))

          When("only the article knowledge evidence changes while the accepted PDF bytes remain retained")
          _write(fixture.root.resolve("knowledge/part-5.dox"), "Part 5 revised article authority")
          val mediaplan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(fixture.descriptor))
          val articlepdf = mediaplan.resources.find(_.resource.id == "part-5-article-pdf-ja").getOrElse(
            throw new IllegalStateException("Missing Part 5 article PDF")
          )
          val retained = CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor))

          Then("ordinary receipt and selected PDF review currentness admit unchanged prebuilt PDFs")
          CozyMediaReceipt.current(mediaplan, articlepdf) shouldBe true
          CozyMediaReceipt.currentnessFailure(mediaplan, articlepdf) shouldBe None
          CozyMediaPdfReviewState.current(mediaplan) shouldBe true
          retained.candidates.filter(_.role == CozyArticleMediaSiteBinding.Role.ArticlePdf).map(_.resourceId) shouldBe
            initial.candidates.filter(_.role == CozyArticleMediaSiteBinding.Role.ArticlePdf).map(_.resourceId)
        }
      }

      "refuse missing, malformed, newer and role-incompatible PDF evidence before admission" in {
        val cases = Vector[(String, SiteBindingPart5Fixture.Data => Unit, String)](
          ("missing-state", fixture => { Files.delete(fixture.root.resolve("target/cozy-media/pdf-review-state.json")); () }, "PDF review currentness"),
          ("newer-output", fixture => { Files.setLastModifiedTime(fixture.root.resolve("target/article-en.pdf"), java.nio.file.attribute.FileTime.fromMillis(4102444800000L)); () }, "Media receipt currentness"),
          ("invalid-format", fixture => _write(fixture.root.resolve("target/article-en.pdf"), "malformed PDF"), "PDF header"),
          ("altered-state", fixture => _write(fixture.root.resolve("target/cozy-media/pdf-review-state.json"), "{}"), "PDF review currentness")
        )
        cases.foreach { case (name, change, reason) =>
          _with_part5_pdf_fixture("pdf-" + name) { fixture =>
            Given("accepted site PDFs followed by one missing state, newer output, invalid format or malformed state")
            change(fixture)
            val receipt = fixture.root.resolve("target/cozy-media/manifest.json")
            val before = Files.readAllBytes(receipt).toVector

            When("the ordinary site binding planner evaluates the defective PDF evidence")
            val error = _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))

            Then("the concrete currentness reason identifies its resource, actual path, producer and next build operation")
            error.getMessage should include(reason)
            error.getMessage should include("part-5-article-pdf-")
            error.getMessage should include("producer prebuilt")
            error.getMessage should include(fixture.root.toString)
            error.getMessage should include(s"cozy media build ${fixture.descriptor} --target part-5-article-pdf-")
            Files.readAllBytes(receipt).toVector shouldBe before
          }
        }

        _with_part5_pdf_fixture("pdf-unrelated-source") { fixture =>
          Given("accepted site PDFs beside an unrelated infographic source")
          _write(fixture.root.resolve("input/summary-ja.png"), "changed non-article source")
          When("the site binding planner evaluates unchanged PDFs after unrelated source drift")
          val plan = CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor))
          Then("the unrelated source does not veto the selected English PDF")
          plan.candidates.exists(_.resourceId == "part-5-article-pdf-en") shouldBe true
        }

        _with_part5_fixture("pdf-role-incompatible", _part5_pdf_yaml().replace(
          "  - id: part-5-article-pdf-ja\n    kind: document", "  - id: part-5-article-pdf-ja\n    kind: infographic")) { fixture =>
          Given("a PDF role declared on an incompatible infographic resource")
          When("the site binding planner evaluates that resource role")
          val error = _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
          Then("the document-kind contract rejects admission")
          error.getMessage should include("requires kind document")
        }
      }

    }
  }

  private def _candidate(plan: CozyArticleMediaSiteBinding.Plan, resourceid: String): CozyArticleMediaSiteBinding.Candidate =
    plan.candidates.find(_.resourceId == resourceid).getOrElse(throw new IllegalStateException(s"Missing Part 5 candidate: $resourceid"))

  private def _part5_plan_failure(name: String, yaml: String): RuntimeException =
    _with_part5_fixture(name, yaml) { fixture =>
      _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
    }

  private def _part5_production_failure(name: String, json: String): RuntimeException =
    _with_part5_fixture(name) { fixture =>
      _write(fixture.root.resolve("video/ja/production.json"), json)
      _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
    }

  private def _with_part5_pdf_fixture[A](name: String)(f: SiteBindingPart5Fixture.Data => A): A =
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

  private def _failure(value: => Any): RuntimeException =
    intercept[RuntimeException](value)

  private def _with_part5_fixture[A](name: String, yaml: String = _part5_yaml())(f: SiteBindingPart5Fixture.Data => A): A = {
    val root = Files.createTempDirectory("cozy-article-media-site-binding-" + name + "-").toRealPath()
    val descriptor = root.resolve("media.yaml")
    val profile = root.resolve("publication")
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
      f(new SiteBindingPart5Fixture.Data(root, descriptor, profile))
    } finally {
      _delete(root)
    }
  }

  private def _part5_yaml(
    association: String = "articleMedia:\n  articleIdentity: development-process/part-5\n  publicationProfile: site",
    summaryjabinding: String = _part5_infographic_binding("/ja/development-process/images/part-5/summary.png", "Part 5 summary"),
    summaryenbinding: String = _part5_infographic_binding("/en/development-process/images/part-5/summary.png", "Part 5 summary"),
    videojabinding: String = _part5_video_binding("video/ja/production.json"),
    videoenbinding: String = _part5_video_binding("video/en/production.json"),
    summaryjapublication: Option[String] = Some("images/summary-ja.png")
  ): String = {
    val summarypublication = summaryjapublication.map(value => s"    publications:\n      site: $value\n").getOrElse("")
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
       |$summarypublication$summaryjabinding
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
  }

  private def _project_config_yaml(
    root: String = "publication",
    projectkind: String = "smartdox-site",
    sitekind: String = "smartdox"
  ): String =
    s"""project:
      |  id: simplemodeling-org
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
    category: String = "development-process",
    article: String = "part-5",
    language: String = "ja",
    renderstatus: String = "completed",
    qastatus: String = "technical-and-visual-qa-passed",
    youtubestatus: String = "published",
    url: String = "https://youtu.be/ja_Part5-1"
  ): String =
    s"""{
       |  "category": "$category",
       |  "article": "$article",
       |  "language": "$language",
       |  "extra": {"accepted": true},
       |  "render": {
       |    "status": "$renderstatus",
       |    "qa": {"status": "$qastatus"},
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

  private def _delete(path: Path): Unit = {
    if (Files.exists(path)) {
      val paths = Files.walk(path).iterator().asScala.toVector.sortBy(_.getNameCount).reverse
      paths.foreach(Files.deleteIfExists(_))
    }
  }
}
