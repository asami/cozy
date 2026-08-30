package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import cozy.media.CozyMedia
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.{PdfDocumentReference, VideoPresentation, VideoStatus}
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

/*
 * @since   Aug. 12, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
private object CozyArticleMediaWipBindingFixture {
  final case class Data(
    root: Path,
    descriptor: Path,
    config: Path,
    publication: Path,
    website: Path,
    sourceja: Path,
    sourceen: Path,
    productionja: Path,
    productionen: Path
  )
}

final class CozyArticleMediaWipBindingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaWipBinding" should {
    "plan provider-neutral WIP candidates" which {
      "select configured opted-in resources in exact resource-id order with strict-only infographic reuse" in {
        Given("a configured Japanese/English WIP media package without YouTube evidence")
        _with_fixture("success") { fixture =>

          When("the read-only WIP plan is built")
          val plan = CozyArticleMediaWipBinding.plan(_config(fixture))

          Then("all selector gates, exact paths, and the separate video integrity are retained")
          plan.candidates.map(_.resourceId) shouldBe Vector("infographic-ja", "video-en", "video-ja")
          plan.candidates.find(_.resourceId == "infographic-ja").flatMap(_.integrity) shouldBe None
          val japanese = _candidate(plan, "video-ja")
          japanese.variant.video.get.presentation shouldBe VideoPresentation.SiteHosted
          japanese.variant.video.get.status shouldBe VideoStatus.Published
          japanese.variant.video.get.provider shouldBe None
          japanese.variant.video.get.watchUrl shouldBe None
          japanese.variant.video.get.contentUrl.map(_.toString) shouldBe Some("/ja/development-process/videos/example.mp4")
          _candidate(plan, "video-en").variant.video.get.contentUrl.map(_.toString) shouldBe Some("/en/development-process/videos/example.mp4")
          japanese.integrity.get.record.repositoryPath shouldBe "ja/development-process/videos/example.mp4"
          japanese.integrity.get.record.provenance shouldBe CozyArticleMediaIntegrity.WipSiteVideo("media.yaml", "video-ja", "video/ja/production.json")
          plan.registry.articles.head.integrities.map(_.record.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Video, CozyArticleMediaIntegrity.Role.Video)
          _direct_names(fixture.publication) shouldBe Vector.empty
        }
      }

      "select exactly one opted-in target and reject selector or tuple ambiguity" in {
        Given("a valid configured package and descriptors with missing association or duplicate locale/role")
        _with_fixture("target") { fixture =>
          When("each deterministic selector set is planned")
          val target = CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-en")))
          _write(fixture.descriptor, _media_yaml(association = "", includeenglishvideo = false))
          val missing = _failure(CozyArticleMediaWipBinding.plan(_config(fixture)))
          _write(fixture.descriptor, _media_yaml(duplicatejapanesevideo = true))
          val duplicate = _failure(CozyArticleMediaWipBinding.plan(_config(fixture)))

          Then("the target is exact and missing/duplicate authority fails closed")
          target.candidates.map(_.resourceId) shouldBe Vector("video-en")
          missing.getMessage should include("top-level articleMedia")
          duplicate.getMessage should include("Duplicate article-media WIP binding candidate")
        }
      }

      "accept current JA and EN article and summary PDFs as strict reuse-only candidates" in {
        Given("a configured package with current Phase 40 article and summary PDF evidence")
        _with_pdf_fixture("pdf-success") { fixture =>
          val beforewebsite = _direct_names(fixture.website)

          When("the WIP binding plans all PDF candidates")
          val plan = CozyArticleMediaWipBinding.plan(_config(fixture))
          val target = CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("summary-pdf-en")))
          val articleja = _candidate(plan, "article-pdf-ja")
          val articleen = _candidate(plan, "article-pdf-en")
          val summaryja = _candidate(plan, "summary-pdf-ja")
          val summaryen = _candidate(plan, "summary-pdf-en")

          Then("each PDF maps to its exact strict role, path, media type, and optional label")
          plan.candidates.map(_.resourceId) shouldBe Vector(
            "article-pdf-en", "article-pdf-ja", "summary-pdf-en", "summary-pdf-ja"
          )
          articleja.role shouldBe CozyArticleMediaRegistry.StrictRole.ArticlePdf
          summaryja.role shouldBe CozyArticleMediaRegistry.StrictRole.SummarySlidesPdf
          articleja.variant.articlePdf shouldBe Some(PdfDocumentReference(
            new URI("/ja/development-process/pdf/example/article.pdf"),
            "application/pdf",
            Some("Article PDF ja")
          ))
          articleen.variant.articlePdf.map(_.label) shouldBe Some(None)
          summaryja.variant.summarySlidesPdf.map(_.label) shouldBe Some(Some("Summary PDF ja"))
          summaryen.variant.summarySlidesPdf.map(_.label) shouldBe Some(None)
          articleja.variant.summarySlidesPdf shouldBe empty
          summaryja.variant.articlePdf shouldBe empty
          articleja.evidence.asInstanceOf[CozyArticleMediaWipBinding.PdfEvidence].output.path shouldBe
            fixture.root.resolve("output/article-ja.pdf").toAbsolutePath.normalize()
          plan.registry.articles.head.integrities shouldBe empty
          _direct_names(fixture.website) shouldBe beforewebsite
          target.candidates.map(_.resourceId) shouldBe Vector("summary-pdf-en")
        }
      }

      "reject missing, stale, or role-incompatible PDF evidence before registry planning" in {
        Given("PDF evidence cases with missing review state, stale output, or an incompatible resource role")
        val missing = _with_pdf_fixture("pdf-missing") { fixture =>
          Files.delete(fixture.root.resolve("target/cozy-media/pdf-review-state.json"))
          When("the WIP binding planner evaluates the missing PDF review-state evidence")
          _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("article-pdf-ja"))))
        }
        val stale = _with_pdf_fixture("pdf-stale") { fixture =>
          _write(fixture.root.resolve("output/article-ja.pdf"), "%PDF-1.7\nstale")
          When("the WIP binding planner evaluates the stale PDF output evidence")
          _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("article-pdf-ja"))))
        }
        val incompatible = _with_pdf_fixture("pdf-incompatible", kind = "infographic") { fixture =>
          When("the WIP binding planner evaluates the incompatible PDF resource role")
          _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("article-pdf-ja"))))
        }

        Then("the binding fails before a WIP registry update for every invalid boundary")
        missing.getMessage should include("pdf-review-state")
        stale.getMessage should include("receipt.v2")
        incompatible.getMessage should include("requires kind document")
      }

      "reject missing configured SmartDox authority and invalid video evidence before a plan" in {
        Given("a descriptor project that is not smartdox-site and bad production variants")
        _with_fixture("gates", projectkind = "other-site") { fixture =>
          When("the four selectors and exact production digest are checked")
          val kind = _failure(CozyArticleMediaWipBinding.plan(_config(fixture)))
          _write(fixture.config, _project_config("smartdox-site", "other"))
          val sitekind = _failure(CozyArticleMediaWipBinding.plan(_config(fixture)))
          _write(fixture.config, _project_config())
          _write(fixture.productionja, _production("ja", "A" * 64))
          val digest = _failure(CozyArticleMediaWipBinding.plan(_config(fixture)))

          Then("project kind, configured site kind, and render SHA grammar remain mandatory")
          kind.getMessage should include("project.kind smartdox-site")
          sitekind.getMessage should include("configured smartdox site-kind")
          digest.getMessage should include("render sha256")
        }
      }

      "reject production identity, language, render QA, MP4, and destination hardening failures" in {
        Given("a valid package whose separate evidence is made invalid in turn")
        _with_fixture("evidence") { fixture =>
          When("production and source evidence is re-read")
          _write(fixture.productionja, _production("ja", _sha256(fixture.sourceja), category = "other"))
          val identity = _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja"))))
          _write(fixture.productionja, _production("en", _sha256(fixture.sourceja)))
          val language = _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja"))))
          _write(fixture.productionja, _production("ja", _sha256(fixture.sourceja), qa = "pending"))
          val qa = _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja"))))
          _write(fixture.productionja, _production("ja", _sha256(fixture.sourceja)))
          Files.move(fixture.sourceja, fixture.sourceja.resolveSibling("video-ja.webm"))

          val media = _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja"))))

          Then("every noncanonical evidence edge is rejected before mutation")
          identity.getMessage should include("identity differs")
          language.getMessage should include("language differs")
          qa.getMessage should include("QA status")
          media.getMessage should include("video output")
        }
      }

      "reject nonregular or symlinked source and destination evidence" in {
        Given("separate fixtures with a symlinked output, nonregular output, or nonregular destination")
        _with_fixture("symlink-source") { fixture =>
          val target = fixture.sourceja.resolveSibling("video-ja-target.mp4")
          Files.move(fixture.sourceja, target)
          Files.createSymbolicLink(fixture.sourceja, target)

          When("the direct-entry evidence checks run")
          val symlink = _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja"))))

          Then("a symlinked source is rejected")
          symlink.getMessage should include("direct regular non-symlink")
        }
        _with_fixture("nonregular-destination") { fixture =>
          Files.createDirectories(fixture.website.resolve("ja/development-process/videos/example.mp4"))

          When("a destination exists but is not a file")
          val nonregular = _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja"))))

          Then("destination evidence also fails closed")
          nonregular.getMessage should include("direct regular non-symlink")
        }
      }
    }

    "isolate current WIP tuple state" which {
      "admit a fresh tuple and then only the exact installed WIP repeat" in {
        Given("a fresh plan, its exact planned pair, and matching installed bytes")
        _with_fixture("repeat") { fixture =>
          val fresh = CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja")))
          val candidate = fresh.candidates.head
          val strict = fresh.registry.articles.head.strict
          val integrity = candidate.integrity.get
          _write_bundle(fixture.publication, "publication", Vector(_entry(strict.entryPath, strict.metadata), _entry(integrity.entryPath, integrity.metadata)))
          Files.copy(fixture.sourceja, fixture.website.resolve("ja/development-process/videos/example.mp4"), StandardCopyOption.REPLACE_EXISTING)

          When("the exact plan is repeated with current output bytes")
          val repeat = CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja")))
          _write(fixture.website.resolve("ja/development-process/videos/example.mp4"), "stale")
          val stale = _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja"))))

          Then("only the exact digest-matching WIP tuple is reusable")
          fresh.registry.videoStates(("development-process/example", "ja", "video")) shouldBe CozyArticleMediaRegistry.WipVideoState.Fresh
          repeat.registry.videoStates(("development-process/example", "ja", "video")) shouldBe CozyArticleMediaRegistry.WipVideoState.ExactRepeat
          stale.getMessage should include("repeat destination digest")
        }
      }

      "reject a pre-existing destination for a fresh tuple without mutation" in {
        Given("an empty WIP registry tuple with a direct destination that was not installed by WIP")
        _with_fixture("fresh-destination") { fixture =>
          val destination = fixture.website.resolve("ja/development-process/videos/example.mp4")
          Files.copy(fixture.sourceja, destination)
          val destinationbytes = Files.readAllBytes(destination).toVector

          When("the fresh WIP plan is built")
          val error = _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja"))))

          Then("planning rejects the ambiguous direct MP4 and leaves registry and destination bytes unchanged")
          error.getMessage should include("Article-media WIP fresh destination must be absent")
          _direct_names(fixture.publication) shouldBe Vector.empty
          Files.readAllBytes(destination).toVector shouldBe destinationbytes
        }
      }

      "reject an external or malformed existing video tuple without overwriting it" in {
        Given("a configured registry containing an external production video")
        _with_fixture("external") { fixture =>
          val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
            CozyArticleMediaPublication.Variant("ja", video = Some(org.smartdox.metadata.PublishMetadata.VideoReference(
              VideoPresentation.ExternalLink,
              VideoStatus.Published,
              Some("youtube"),
              Some(new URI("https://youtu.be/example")),
              None
            )))
          ))
          _write_bundle(fixture.publication, "publication", Vector(_entry(strict.entryPath, strict.metadata)))
          val before = Files.readAllBytes(fixture.publication.resolve("publication.json")).toVector

          When("a local WIP video is planned for that tuple")
          val error = _failure(CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja"))))

          Then("production/external state is rejected and the registry bytes are preserved")
          error.getMessage should include("existing video tuple")
          Files.readAllBytes(fixture.publication.resolve("publication.json")).toVector shouldBe before
        }
      }

      "revalidate full descriptor, source, production, root, and registry evidence" in {
        Given("a complete fresh plan")
        _with_fixture("drift") { fixture =>
          val plan = CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja")))
          _write(fixture.sourceja, "changed source")

          When("captured source evidence changes before revalidation")
          val source = _failure(CozyArticleMediaWipBinding.revalidate(plan))
          _write(fixture.sourceja, "ja source")
          _write(fixture.productionja, _production("ja", _sha256(fixture.sourceja)))
          val restored = CozyArticleMediaWipBinding.plan(_config(fixture, target = Some("video-ja")))
          _write_bundle(fixture.publication, "publication", Vector(_entry("metadata/unrelated.json", Json.obj("changed" -> true))))
          val registry = _failure(CozyArticleMediaWipBinding.revalidate(restored))

          Then("both file and complete registry snapshots are rechecked")
          source.getMessage should include("render sha256 differs")
          registry.getMessage should include("evidence has changed")
        }
      }
    }
  }

  private def _config(fixture: CozyArticleMediaWipBindingFixture.Data, target: Option[String] = None): CozyArticleMediaWipBinding.Config =
    CozyArticleMediaWipBinding.Config(fixture.descriptor, fixture.publication, fixture.website, target)

  private def _candidate(plan: CozyArticleMediaWipBinding.Plan, id: String): CozyArticleMediaWipBinding.Candidate =
    plan.candidates.find(_.resourceId == id).getOrElse(throw new IllegalStateException(s"Missing WIP candidate: $id"))

  private def _failure(value: => Any): RuntimeException =
    intercept[RuntimeException](value)

  private def _with_fixture[A](
    name: String,
    projectkind: String = "smartdox-site"
  )(f: CozyArticleMediaWipBindingFixture.Data => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-wip-binding")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, name + "-")
    val descriptor = root.resolve("media.yaml")
    val config = root.resolve("conf/cozy/config.yaml")
    val publication = root.resolve("registry")
    val website = root.resolve("website")
    val sourceja = root.resolve("output/video-ja.mp4")
    val sourceen = root.resolve("output/video-en.mp4")
    val productionja = root.resolve("video/ja/production.json")
    val productionen = root.resolve("video/en/production.json")
    try {
      _write(config, _project_config(projectkind))
      _write(descriptor, _media_yaml())
      _write(root.resolve("profile/images/summary-ja.png"), "summary")
      _write(sourceja, "ja source")
      _write(sourceen, "en source")
      _write(productionja, _production("ja", _sha256(sourceja)))
      _write(productionen, _production("en", _sha256(sourceen)))
      Files.createDirectories(publication)
      Files.createDirectories(website.resolve("ja/development-process/videos"))
      Files.createDirectories(website.resolve("en/development-process/videos"))
      f(CozyArticleMediaWipBindingFixture.Data(root, descriptor, config, publication, website, sourceja, sourceen, productionja, productionen))
    } finally {
      _delete(root)
    }
  }

  private def _with_pdf_fixture[A](name: String, kind: String = "document")(f: CozyArticleMediaWipBindingFixture.Data => A): A =
    _with_fixture(name) { fixture =>
      _write(fixture.root.resolve("knowledge/example.dox"), "Example article authority")
      _write(fixture.descriptor, _pdf_media_yaml(kind))
      _write(fixture.root.resolve("output/article-ja.pdf"), "%PDF-1.7\nArticle PDF ja")
      _write(fixture.root.resolve("output/article-en.pdf"), "%PDF-1.7\nArticle PDF en")
      _write(fixture.root.resolve("output/summary-ja.pdf"), "%PDF-1.7\nSummary PDF ja")
      _write(fixture.root.resolve("output/summary-en.pdf"), "%PDF-1.7\nSummary PDF en")
      if (kind == "document")
        CozyMedia.build(CozyMedia.CommandConfig(fixture.descriptor))
      f(fixture)
    }

  private def _project_config(projectkind: String = "smartdox-site", sitekind: String = "smartdox"): String =
    s"""project:
       |  id: simplemodeling-org
       |  kind: $projectkind
       |media:
       |  publication-profiles:
       |    site:
       |      root: profile
       |      site-kind: $sitekind
       |""".stripMargin

  private def _media_yaml(
    association: String = "articleMedia:\n  articleIdentity: development-process/example\n  publicationProfile: site",
    includeenglishvideo: Boolean = true,
    duplicatejapanesevideo: Boolean = false
  ): String = {
    val english = if (includeenglishvideo) _video_resource("video-en", "en", "output/video-en.mp4", "video/en/production.json") else ""
    val duplicate = if (duplicatejapanesevideo) _video_resource("video-ja-duplicate", "ja", "output/video-ja.mp4", "video/ja/production.json") else ""
    s"""schema: cozy.media.v1
       |knowledge:
       |  id: media-package/example
       |  source: knowledge/example.dox
       |profiles:
       |  site:
       |    root: profile
       |$association
       |resources:
       |  - id: infographic-ja
       |    kind: infographic
       |    language: ja
       |    source: input/summary.png
       |    build: prebuilt
       |    publications:
       |      site: images/summary-ja.png
       |    articleMedia:
       |      role: infographic
       |      publicPath: /ja/development-process/images/summary-ja.png
       |      mediaType: image/png
       |$english${_video_resource("video-ja", "ja", "output/video-ja.mp4", "video/ja/production.json")}$duplicate""".stripMargin
  }

  private def _pdf_media_yaml(kind: String): String =
    s"""schema: cozy.media.v1
       |knowledge:
       |  id: media-package/example
       |  source: knowledge/example.dox
       |profiles:
       |  site:
       |    root: profile
       |articleMedia:
       |  articleIdentity: development-process/example
       |  publicationProfile: site
       |resources:
       |  - id: article-pdf-ja
       |    kind: $kind
       |    language: ja
       |    source: output/article-ja.pdf
       |    build: prebuilt
       |    articleMedia:
       |      role: article_pdf
       |      publicPath: /ja/development-process/pdf/example/article.pdf
       |      mediaType: application/pdf
       |      label: Article PDF ja
       |  - id: article-pdf-en
       |    kind: document
       |    language: en
       |    source: output/article-en.pdf
       |    build: prebuilt
       |    articleMedia:
       |      role: article_pdf
       |      publicPath: /en/development-process/pdf/example/article.pdf
       |      mediaType: application/pdf
       |  - id: summary-pdf-ja
       |    kind: document
       |    language: ja
       |    source: output/summary-ja.pdf
       |    build: prebuilt
       |    articleMedia:
       |      role: summary_slides_pdf
       |      publicPath: /ja/development-process/pdf/example/summary.pdf
       |      mediaType: application/pdf
       |      label: Summary PDF ja
       |  - id: summary-pdf-en
       |    kind: document
       |    language: en
       |    source: output/summary-en.pdf
       |    build: prebuilt
       |    articleMedia:
       |      role: summary_slides_pdf
       |      publicPath: /en/development-process/pdf/example/summary.pdf
       |      mediaType: application/pdf
       |""".stripMargin

  private def _video_resource(id: String, locale: String, output: String, production: String): String =
    s"""  - id: $id
       |    kind: video
       |    language: $locale
       |    source: input/$id.mp4
       |    output: $output
       |    build: prebuilt
       |    articleMedia:
       |      role: video
       |      production: $production
       |""".stripMargin

  private def _production(
    language: String,
    sha256: String,
    category: String = "development-process",
    article: String = "example",
    qa: String = "technical-and-visual-qa-passed"
  ): String =
    s"""{
       |  "category": "$category",
       |  "article": "$article",
       |  "language": "$language",
       |  "render": {
       |    "status": "completed",
       |    "sha256": "$sha256",
       |    "qa": {"status": "$qa"},
       |    "listeningReview": {"status": "pending"}
       |  }
       |}
       |""".stripMargin

  private def _entry(path: String, metadata: JsObject): JsObject =
    Json.obj("path" -> path, "key" -> path.stripSuffix(".json").stripPrefix("metadata/"), "metadata" -> metadata)

  private def _write_bundle(root: Path, name: String, entries: Vector[JsObject]): Unit =
    _write(root.resolve(name + ".json"), Json.prettyPrint(Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> name),
      "sourceRepository" -> "fixture",
      "sourcePath" -> ".",
      "sourceCommit" -> "0123456789abcdef",
      "entries" -> JsArray(entries)
    )) + "\n")

  private def _direct_names(root: Path): Vector[String] = {
    val stream = Files.list(root)
    try stream.iterator.asScala.toVector.map(_.getFileName.toString).sorted
    finally stream.close()
  }

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(Files.readAllBytes(path))
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
