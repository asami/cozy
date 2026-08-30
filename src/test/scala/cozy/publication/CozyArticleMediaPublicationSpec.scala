package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata
import org.smartdox.metadata.PublishMetadata.{ImageReference, PdfDocumentReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsObject, Json}

/*
 * @since   Aug.  4, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaPublicationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaPublication" should {
    "serialize strict publication metadata" which {
      "emit a deterministic bilingual strict SmartDox record" in {
        Given("two canonical locale variants with optional media fields")
        val result: CozyArticleMediaPublication.Result = CozyArticleMediaPublication.produce(
          articleIdentity = "development-process/example",
          variants = Vector(
            CozyArticleMediaPublication.Variant(
              locale = "ja",
              infographic = Some(ImageReference(new URI("/ja/development-process/images/example.png"), Some("image/png"), Some("詳細インフォグラフィック"))),
              video = Some(VideoReference(VideoPresentation.SiteHosted, VideoStatus.Published, Some("cozy"), None, Some(new URI("/repository/video/example.mp4")))),
              articlePdf = Some(PdfDocumentReference(new URI("/ja/development-process/pdf/example-article.pdf"), "application/pdf", Some("記事 PDF"))),
              summarySlidesPdf = Some(PdfDocumentReference(new URI("/ja/development-process/pdf/example-summary.pdf"), "application/pdf", Some("要約スライド PDF")))
            ),
            CozyArticleMediaPublication.Variant(
              locale = "en",
              video = Some(VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, None, Some(new URI("https://example.com/watch")), None))
            )
          )
        )

        When("the strict publication metadata is produced")
        val metadata = result.metadata
        val metadatavariants = (metadata \ "variants").as[JsObject]
        val en = (metadatavariants \ "en").as[JsObject]
        val ja = (metadatavariants \ "ja").as[JsObject]

        Then("only the accepted field sets are serialized in canonical locale order")
        metadata.fields.map(_._1) shouldBe Vector("type", "article", "variants")
        (metadata \ "article").as[JsObject].fields.map(_._1) shouldBe Vector("identity")
        metadatavariants.fields.map(_._1) shouldBe Vector("en", "ja")
        en.fields.map(_._1) shouldBe Vector("video")
        (en \ "video").as[JsObject].fields.map(_._1) shouldBe Vector("presentation", "status", "watch_url")
        ja.fields.map(_._1) shouldBe Vector("infographic", "article_pdf", "summary_slides_pdf", "video")
        (ja \ "infographic").as[JsObject].fields.map(_._1) shouldBe Vector("public_path", "media_type", "alt")
        (ja \ "article_pdf").as[JsObject].fields.map(_._1) shouldBe Vector("public_path", "media_type", "label")
        (ja \ "summary_slides_pdf").as[JsObject].fields.map(_._1) shouldBe Vector("public_path", "media_type", "label")
        (ja \ "video").as[JsObject].fields.map(_._1) shouldBe Vector("presentation", "status", "provider", "content_url")

        And("the deterministic source-relative entry path has no Cozy integrity leakage")
        result.entryPath shouldBe "metadata/article-media/development-process/example.json"
        _field_names(metadata) should not contain "sha256"
        _field_names(metadata) should not contain "publicationState"
        _field_names(metadata) should not contain "provenance"
        _field_names(metadata) should not contain "repositoryPath"
        _field_names(metadata) should not contain "version"
      }
    }

    "interoperate with the pinned SmartDox parser" which {
      "produce a bundle consumable by the pinned SmartDox parser" in {
        Given("exact-locale SmartDox variants with every media field and omitted optionals")
        val result = CozyArticleMediaPublication.produce(
          "development-process/example",
          Vector(
            CozyArticleMediaPublication.Variant(
              "en",
              infographic = Some(ImageReference(new URI("/en/development-process/images/example.png"), Some("image/png"), Some("Example infographic"))),
              articlePdf = Some(PdfDocumentReference(new URI("/en/development-process/pdf/example-article.pdf"), "application/pdf", Some("Article PDF"))),
              video = Some(VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, Some("vimeo"), Some(new URI("https://example.com/watch")), Some(new URI("/repository/video/example.mp4"))))
            ),
            CozyArticleMediaPublication.Variant(
              "ja",
              summarySlidesPdf = Some(PdfDocumentReference(new URI("/ja/development-process/pdf/example-summary.pdf"), "application/pdf", None)),
              video = Some(VideoReference(VideoPresentation.SiteHosted, VideoStatus.Published, None, None, Some(new URI("/repository/video/example-ja.mp4"))))
            )
          )
        )

        When("the generated publication bundle is loaded by SmartDox")
        val metadata = _with_bundle(result)(x => PublishMetadata.load(x.toFile).get)
        val parsedpublication = metadata.articleMedia.publications.find(_.articleIdentity == result.publication.articleIdentity).get
        val parsedvariants = result.publication.variants.map(variant => metadata.resolveArticleMedia(result.publication.articleIdentity, variant.locale).get)

        Then("the parsed publication and exact locale variants retain the producer's typed expectations")
        parsedpublication shouldBe result.publication
        parsedvariants shouldBe result.publication.variants
        parsedvariants.head.infographic.map(_.publicPath.toString) shouldBe Some("/en/development-process/images/example.png")
        parsedvariants.head.infographic.flatMap(_.mediaType) shouldBe Some("image/png")
        parsedvariants.head.infographic.flatMap(_.alt) shouldBe Some("Example infographic")
        parsedvariants.head.articlePdf.map(_.publicPath.toString) shouldBe Some("/en/development-process/pdf/example-article.pdf")
        parsedvariants.head.articlePdf.map(_.mediaType) shouldBe Some("application/pdf")
        parsedvariants.head.articlePdf.flatMap(_.label) shouldBe Some("Article PDF")
        parsedvariants.head.video.flatMap(_.provider) shouldBe Some("vimeo")
        parsedvariants.head.video.map(_.presentation) shouldBe Some(VideoPresentation.ExternalLink)
        parsedvariants.head.video.map(_.status) shouldBe Some(VideoStatus.Published)
        parsedvariants.head.video.flatMap(_.watchUrl).map(_.toString) shouldBe Some("https://example.com/watch")
        parsedvariants.head.video.flatMap(_.contentUrl).map(_.toString) shouldBe Some("/repository/video/example.mp4")
        parsedvariants(1).infographic shouldBe empty
        parsedvariants(1).articlePdf shouldBe empty
        parsedvariants(1).summarySlidesPdf.map(_.publicPath.toString) shouldBe Some("/ja/development-process/pdf/example-summary.pdf")
        parsedvariants(1).summarySlidesPdf.flatMap(_.label) shouldBe empty
        parsedvariants(1).video.flatMap(_.provider) shouldBe empty
        parsedvariants(1).video.flatMap(_.watchUrl) shouldBe empty
        parsedvariants(1).video.flatMap(_.contentUrl).map(_.toString) shouldBe Some("/repository/video/example-ja.mp4")
        metadata.resolveArticleMedia("development-process/example", "en-US") shouldBe empty
      }
    }

    "project publication lifecycle variants" which {
      "preserve watch-only published external links" in {
        Given("a published external SmartDox-compatible variant")
        val external = _external_published("development-process/external", "en")

        When("the generated bundle is loaded by SmartDox")
        val externalvideo = _with_bundle(external)(x => PublishMetadata.load(x.toFile).get.resolveArticleMedia("development-process/external", "en").flatMap(_.projectableVideo))

        Then("the published watch-only video projects")
        externalvideo.flatMap(_.watchUrl).map(_.toString) shouldBe Some("https://example.com/watch")
      }

      "keep draft videos valid but unavailable" in {
        Given("a draft external SmartDox-compatible variant")
        val draft = _video("development-process/draft", "en", VideoPresentation.ExternalLink, VideoStatus.Draft)

        When("the generated bundle is loaded by SmartDox")
        val draftvideo = _with_bundle(draft)(x => PublishMetadata.load(x.toFile).get.resolveArticleMedia("development-process/draft", "en").flatMap(_.projectableVideo))

        Then("the draft video remains unavailable for projection")
        draftvideo shouldBe empty
      }

      "keep withdrawn videos valid but unavailable" in {
        Given("a withdrawn site-hosted SmartDox-compatible variant")
        val withdrawn = _video("development-process/withdrawn", "ja", VideoPresentation.SiteHosted, VideoStatus.Withdrawn)

        When("the generated bundle is loaded by SmartDox")
        val withdrawnvideos = _with_bundle(withdrawn)(x => PublishMetadata.load(x.toFile).get.resolveArticleMedia("development-process/withdrawn", "ja").flatMap(_.projectableVideo))

        Then("the withdrawn video remains unavailable for projection")
        withdrawnvideos shouldBe empty
      }
    }

    "reject invalid publication inputs" which {
      "reject a leading-slash article identity" in {
        Given("an invalid strict producer article identity")
        val invalididentity = () => _external_published("/development-process/example", "en")

        When("the producer validates the identity")
        val identityerror = intercept[IllegalArgumentException](invalididentity())

        Then("the record is rejected deterministically")
        identityerror.getMessage should include("article identity")
      }

      "reject a noncanonical locale" in {
        Given("an invalid strict producer locale")
        val invalidlocale = () => _external_published("development-process/example", "en-us")

        When("the producer validates the locale")
        val localeerror = intercept[IllegalArgumentException](invalidlocale())

        Then("the record is rejected deterministically")
        localeerror.getMessage should include("locale")
      }

      "reject duplicate locale variants" in {
        Given("two strict producer variants with the same locale")
        val duplicate = () => CozyArticleMediaPublication.produce(
          "development-process/example",
          Vector(_external_variant("en"), _external_variant("en"))
        )

        When("the producer validates the locale variants")
        val duplicateerror = intercept[IllegalArgumentException](duplicate())

        Then("the duplicate record is rejected deterministically")
        duplicateerror.getMessage should include("Duplicate article-media locale variant")
      }

      "reject a published site-hosted video without content URL" in {
        Given("a published site-hosted video without content URL")
        val missingurl = () => _video("development-process/example", "en", VideoPresentation.SiteHosted, VideoStatus.Published)

        When("the producer validates the publication")
        val urlerror = intercept[IllegalArgumentException](missingurl())

        Then("the record is rejected deterministically")
        urlerror.getMessage should include("requires content_url")
      }

      "reject a published external video without watch URL" in {
        Given("a published external video without watch URL")
        val missingexternalurl = () => _video("development-process/example", "en", VideoPresentation.ExternalLink, VideoStatus.Published)

        When("the producer validates the publication")
        val externalurlerror = intercept[IllegalArgumentException](missingexternalurl())

        Then("the record is rejected deterministically")
        externalurlerror.getMessage should include("requires watch_url")
      }

      "reject a relative external watch URL" in {
        Given("a published external video with a relative watch URL")
        val relativeexternalurl = () => CozyArticleMediaPublication.produce(
          "development-process/example",
          Vector(CozyArticleMediaPublication.Variant(
            "en",
            video = Some(VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, None, Some(new URI("/relative")), None))
          ))
        )

        When("the producer validates the external URL")
        val relativeurlerror = intercept[IllegalArgumentException](relativeexternalurl())

        Then("the record is rejected deterministically")
        relativeurlerror.getMessage should include("must be an absolute URI")
      }

      "reject a null variants collection" in {
        Given("a null article-media variants collection")
        val nullvariants = () => CozyArticleMediaPublication.produce(
          "development-process/example",
          null
        )

        When("the producer validates the variants collection")
        val variantserror = intercept[IllegalArgumentException](nullvariants())

        Then("the record is rejected with a stable variants error")
        variantserror.getMessage should include("variants must not be null")
      }

      "reject a null Variant element" in {
        Given("an article-media variants collection containing a null variant")
        val nullvariant = () => CozyArticleMediaPublication.produce(
          "development-process/example",
          Vector[CozyArticleMediaPublication.Variant](null)
        )

        When("the producer validates the variant elements")
        val varianterror = intercept[IllegalArgumentException](nullvariant())

        Then("the record is rejected with a stable variant error")
        varianterror.getMessage should include("variant must not be null")
      }

      "reject a null infographic inside Some" in {
        Given("an article-media variant containing Some null infographic")
        val nullinfographic = () => CozyArticleMediaPublication.produce(
          "development-process/example",
          Vector(CozyArticleMediaPublication.Variant(
            "en",
            infographic = Some(null.asInstanceOf[ImageReference])
          ))
        )

        When("the producer validates the infographic")
        val infographicerror = intercept[IllegalArgumentException](nullinfographic())

        Then("the record is rejected with a stable infographic error")
        infographicerror.getMessage should include("infographic must not be null")
      }

      "reject a null video inside Some" in {
        Given("an article-media variant containing Some null video")
        val nullvideo = () => CozyArticleMediaPublication.produce(
          "development-process/example",
          Vector(CozyArticleMediaPublication.Variant(
            "en",
            video = Some(null.asInstanceOf[VideoReference])
          ))
        )

        When("the producer validates the video")
        val videoerror = intercept[IllegalArgumentException](nullvideo())

        Then("the record is rejected with a stable video error")
        videoerror.getMessage should include("video must not be null")
      }

      "reject a PDF with a non-PDF media type" in {
        Given("an article PDF reference with an image media type")
        val invalidpdf = () => CozyArticleMediaPublication.produce(
          "development-process/example",
          Vector(CozyArticleMediaPublication.Variant(
            "en",
            articlePdf = Some(PdfDocumentReference(new URI("/en/development-process/pdf/example.pdf"), "application/pdfx", None))
          ))
        )

        When("the producer validates the PDF reference")
        val pdferror = intercept[IllegalArgumentException](invalidpdf())

        Then("the PDF role requires the accepted application/pdf media type")
        pdferror.getMessage should include("media_type must be application/pdf")
      }

      "reject a PDF with a blank label" in {
        Given("a summary-slides PDF reference with a whitespace-only label")
        val invalidlabel = () => CozyArticleMediaPublication.produce(
          "development-process/example",
          Vector(CozyArticleMediaPublication.Variant(
            "en",
            summarySlidesPdf = Some(PdfDocumentReference(new URI("/en/development-process/pdf/example-summary.pdf"), "application/pdf", Some("  ")))
          ))
        )

        When("the producer validates the optional PDF label")
        val labelerror = intercept[IllegalArgumentException](invalidlabel())

        Then("a supplied PDF label must be nonblank")
        labelerror.getMessage should include("label must be nonblank")
      }
    }

    "preserve deterministic generated identities" which {
      "keep generated article identities and locale ordering deterministic" in {
        Given("generated source-relative terminal identity segments")
        val segmentgenerator = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
        val property = Prop.forAll(segmentgenerator) { segment =>
          Given("one generated source-relative terminal identity and reversed canonical locale inputs")
          val identity = s"development-process/$segment"

          When("the producer constructs the strict article-media entry")
          val result = CozyArticleMediaPublication.produce(identity, Vector(_external_variant("ja"), _external_variant("en")))

          Then("the entry and canonical locale fields remain stable")
          result.entryPath shouldBe s"metadata/article-media/$identity.json"
          ((result.metadata \ "variants").as[JsObject]).fields.map(_._1) shouldBe Vector("en", "ja")
          true
        }

        When("the producer checks each generated identity")
        val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every generated identity remains canonical and project-relative")
        propertyresult.passed shouldBe true
      }
    }
  }

  private def _external_published(identity: String, locale: String): CozyArticleMediaPublication.Result =
    CozyArticleMediaPublication.produce(identity, Vector(_external_variant(locale)))

  private def _external_variant(locale: String): CozyArticleMediaPublication.Variant =
    CozyArticleMediaPublication.Variant(
      locale,
      video = Some(VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, None, Some(new URI("https://example.com/watch")), None))
    )

  private def _video(identity: String, locale: String, presentation: VideoPresentation, status: VideoStatus): CozyArticleMediaPublication.Result =
    CozyArticleMediaPublication.produce(identity, Vector(CozyArticleMediaPublication.Variant(locale, video = Some(VideoReference(presentation, status)))) )

  private def _with_bundle[A](result: CozyArticleMediaPublication.Result)(f: Path => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target").resolve("cozy-article-media-publication").resolve("work")
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, "bundle-")
    try {
      val bundle = Json.obj(
        "type" -> "publication-bundle",
        "entries" -> Json.arr(Json.obj("path" -> result.entryPath, "metadata" -> result.metadata))
      )
      Files.write(directory.resolve("publication.json"), Json.stringify(bundle).getBytes(StandardCharsets.UTF_8))
      f(directory)
    } finally {
      _delete(directory)
    }
  }

  private def _field_names(value: JsObject): Vector[String] =
    value.fields.toVector.flatMap {
      case (name, nested: JsObject) => name +: _field_names(nested)
      case (name, _) => Vector(name)
    }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
