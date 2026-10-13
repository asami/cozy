package cozy.media

import org.apache.pdfbox.pdmodel.{PDDocument, PDPage}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.FileTime
import scala.collection.JavaConverters._

/*
 * @since   Aug. 30, 2026
 *  version Sep.  2, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaPdfReviewStateSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy PDF review currentness state" should {
    "receipt-derived PDF currentness projection" which {
    "write a closed receipt-derived article-PDF state without changing receipt shapes" in {
      _with_temp_dir("article") { root =>
        Given("a package with one accepted prebuilt article PDF")
        val descriptor = _write_article_fixture(root)

        When("the article-PDF target is accepted")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("article-pdf")))
        val state = Files.readString(root.resolve("target/cozy-media/pdf-review-state.json"), StandardCharsets.UTF_8)
        val receipt = Files.readString(root.resolve("target/cozy-media/manifest.json"), StandardCharsets.UTF_8)

        Then("the exact PDF state binds the article identity while the existing manifest and receipt schemas remain unchanged")
        state should include("\"schema\" : \"cozy.media.pdf-review-state.v1\"")
        state should include("\"role\" : \"article_pdf\"")
        state should include("\"output\" : \"article.pdf\"")
        receipt should include("\"schema\" : \"cozy.media.v1\"")
        receipt should include("\"schema\" : \"cozy.media.receipt.v2\"")
        receipt should not include "pdf-review-state"
      }
    }

    "accept a prebuilt summary-slides PDF without direct-renderer evidence" in {
      _with_temp_dir("prebuilt-summary") { root =>
        Given("a package with one prebuilt summary-slides PDF and no summarySlidesPdf configuration")
        val descriptor = _write_prebuilt_summary_fixture(root)

        When("the prebuilt summary PDF target is accepted and verified")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")))
        val state = Files.readString(root.resolve("target/cozy-media/pdf-review-state.json"), StandardCharsets.UTF_8)
        val result = CozyMedia.verify(CozyMedia.CommandConfig(descriptor, target = Some("summary")))

        Then("the closed current state retains receipt-derived summary identity without renderer-manifest evidence")
        state should include("\"schema\" : \"cozy.media.pdf-review-state.v1\"")
        state should include("\"role\" : \"summary_slides_pdf\"")
        state should include("\"output\" : \"summary.pdf\"")
        result should include("status: valid")
      }
    }

    "refresh both PDF role records while retaining direct summary structural verification" in {
      _with_temp_dir("summary") { root =>
        Given("an accepted article PDF and infographic with a direct summary-slides PDF target")
        val descriptor = _write_summary_fixture(root)
        _accept_dependencies(descriptor)
        val before = Files.readString(root.resolve("target/cozy-media/pdf-review-state.json"), StandardCharsets.UTF_8)

        When("the structurally verified summary target is accepted")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer)
        val state = Files.readString(root.resolve("target/cozy-media/pdf-review-state.json"), StandardCharsets.UTF_8)
        val verified = CozyMedia.verify(CozyMedia.CommandConfig(descriptor, target = Some("summary")))

        Then("the state contains both deterministic PDF roles while the direct renderer structural proof remains authoritative")
        before should include("\"role\" : \"article_pdf\"")
        before should not include "summary_slides_pdf"
        state should include("\"role\" : \"article_pdf\"")
        state should include("\"role\" : \"summary_slides_pdf\"")
        verified should include("status: valid")
      }
    }

    "accept valid same-candidate dependencies during a normal full media build" in {
      _with_temp_dir("full-candidate") { root =>
        Given("a normal full media build containing an article PDF, infographic, and summary-slides PDF")
        val descriptor = _write_summary_fixture(root)

        When("all three valid resources are accepted in the same candidate receipt")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor), _renderer)
        val state = Files.readString(root.resolve("target/cozy-media/pdf-review-state.json"), StandardCharsets.UTF_8)

        Then("the candidate dependencies are accepted and the current state contains both public PDF roles")
        state should include("\"role\" : \"article_pdf\"")
        state should include("\"role\" : \"summary_slides_pdf\"")
        CozyMedia.verify(CozyMedia.CommandConfig(descriptor)) should include("status: valid")
      }
    }

    "reject nonexact PDF state after output, locale, public-path, receipt, or renderer evidence changes" in {
      val changes = Vector[(Path => Unit, String)](
      ((root: Path) => { Files.write(root.resolve("article.pdf"), "changed-output".getBytes(StandardCharsets.UTF_8)); () }) -> "media receipt currentness",
        ((root: Path) => _replace(root.resolve("media.json"), "\"language\": \"en\"", "\"language\": \"ja\"")) -> "summary-slides pdf slide-ir knowledge or language differs",
        ((root: Path) => _replace(root.resolve("media.json"), "/articles/example/article.pdf", "/articles/example/renamed.pdf")) -> "metadata differs",
        ((root: Path) => _replace(root.resolve("target/cozy-media/manifest.json"), "\"status\" : \"valid\"", "\"status\" : \"invalid\"")) -> "media receipt currentness",
      ((root: Path) => { Files.write(root.resolve("target/summary-renderer.json"), "changed-renderer".getBytes(StandardCharsets.UTF_8)); () }) -> "pdf review currentness"
      )
      changes.zipWithIndex.foreach { case ((change, expectedfailure), index) =>
        _with_temp_dir("stale-" + index) { root =>
          Given("a package with current state for both public PDF roles")
          val descriptor = _write_summary_fixture(root)
          _accept_dependencies(descriptor)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer)

          When("one state-bound identity or its source receipt evidence changes")
          change(root)
          val failure = intercept[RuntimeException](CozyMedia.verify(CozyMedia.CommandConfig(descriptor)))

          Then("known-field metadata and direct producer/renderer checks reject stale public-PDF evidence")
          failure.getMessage.toLowerCase(java.util.Locale.ROOT) should include(expectedfailure)
        }
      }
    }

    "verify and prepare only the selected English PDF when a sibling PDF is stale" in {
      _with_temp_dir("selected-English") { root =>
        Given("accepted English and Japanese article PDFs with a package review state containing both")
        val descriptor = _write_public_article_fixture(root)
        _write_pdf(root.resolve("article-ja.pdf"), 1, "Japanese article")
        val json = io.circe.parser.parse(Files.readString(descriptor)).fold(error => throw error, identity)
        val sibling = io.circe.Json.obj(
          "id" -> io.circe.Json.fromString("article-ja"), "kind" -> io.circe.Json.fromString("document"),
          "language" -> io.circe.Json.fromString("ja"), "source" -> io.circe.Json.fromString("article-ja.pdf"), "build" -> io.circe.Json.fromString("prebuilt"),
          "articleMedia" -> io.circe.Json.obj("role" -> io.circe.Json.fromString("article_pdf"), "publicPath" -> io.circe.Json.fromString("/ja/example/article.pdf"), "mediaType" -> io.circe.Json.fromString("application/pdf")))
        _write(descriptor, json.mapObject(fields => fields.add("resources", io.circe.Json.fromValues(fields("resources").get.asArray.get :+ sibling))).noSpaces)
        Files.createDirectories(root.resolve("publication"))
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        val statepath = root.resolve("target/cozy-media/pdf-review-state.json")
        val state = io.circe.parser.parse(Files.readString(statepath)).fold(error => throw error, identity)
        val extras = state.mapObject(fields => fields.add("arbitrary", io.circe.Json.Null).add("resources", io.circe.Json.fromValues(fields("resources").get.asArray.get.map(
          _.mapObject(_.add("sha256", io.circe.Json.arr(io.circe.Json.fromBoolean(false))).add("unknown", io.circe.Json.obj()))))))
        _write(statepath, extras.noSpaces)
        val paths = Vector(root.resolve("article.pdf"), statepath, root.resolve("target/cozy-media/manifest.json"))
        val accepted = paths.map(path => Files.readAllBytes(path).toVector -> Files.getLastModifiedTime(path))

        When("only the Japanese prebuilt PDF becomes newer than its accepted receipt")
        Files.setLastModifiedTime(root.resolve("article-ja.pdf"), FileTime.fromMillis(4102444800000L))
        val config = CozyMedia.CommandConfig(descriptor, target = Some("article-pdf"))
        val verified = CozyMedia.verify(config)
        val prepared = CozyMedia.preparePublication(config.copy(profile = Some("site")))
        val plan = CozyMedia.resolvePlan(config)
        val allcurrent = CozyMediaPdfReviewState.current(plan)
        val stale = intercept[RuntimeException](CozyMedia.verify(config.copy(target = Some("article-ja"))))

        Then("the unchanged selected English PDF succeeds with known-field state loading while selecting the stale sibling fails")
        verified should include("status: valid")
        prepared.map(_.resource.id) shouldBe Vector("article-pdf")
        allcurrent shouldBe false
        stale.getMessage should include("article-ja")
        stale.getMessage should include(root.resolve("article-ja.pdf").toString)
        stale.getMessage should include("producer prebuilt")
        stale.getMessage should include(s"cozy media build $descriptor --target article-ja")
        paths.map(path => Files.readAllBytes(path).toVector -> Files.getLastModifiedTime(path)) shouldBe accepted
        Files.exists(root.resolve("publication/articles/example/article.pdf")) shouldBe false
      }
    }

    "not bless a stale retained summary when a target article acceptance refreshes state" in {
      _with_temp_dir("retained-summary") { root =>
        Given("a current article and summary PDF state")
        val descriptor = _write_summary_fixture(root)
        _accept_dependencies(descriptor)
        CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("summary")), _renderer)
        Files.write(root.resolve("target/summary-renderer.json"), "stale-renderer".getBytes(StandardCharsets.UTF_8))

        When("only the article-PDF target is accepted again")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("article-pdf")))
        val state = Files.readString(root.resolve("target/cozy-media/pdf-review-state.json"), StandardCharsets.UTF_8)

        Then("the refreshed state retains only the current article PDF instead of blessing the stale summary")
        state should include("\"role\" : \"article_pdf\"")
        state should not include "summary_slides_pdf"
      }
    }

    "leave normal non-PDF packages independent of PDF review state" in {
      _with_temp_dir("non-pdf") { root =>
        Given("a package with only a current prebuilt PNG")
        val descriptor = _write_non_pdf_fixture(root)

        When("the package is built and verified")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        val result = CozyMedia.verify(CozyMedia.CommandConfig(descriptor))

        Then("no PDF review-state document is created or required")
        Files.exists(root.resolve("target/cozy-media/pdf-review-state.json")) shouldBe false
        result should include("status: valid")
      }
    }

    "require receipt and review state for selected public article-PDF preparation as PDF-only preflight evidence" in {
      _with_temp_dir("publication-preflight") { root =>
        Given("a selected public article PDF with neither current receipt nor PDF review-state evidence")
        val descriptor = _write_public_article_fixture(root)
        val config = CozyMedia.CommandConfig(descriptor, profile = Some("site"))
        Files.createDirectories(root.resolve("publication"))

        When("publication preparation is attempted before the selected article-PDF target is built")
        val missingreceipt = intercept[RuntimeException](CozyMedia.preparePublication(config))

        Then("the selected public PDF is rejected for absent receipt evidence")
        missingreceipt.getMessage should include("Media receipt currentness failed for resource article-pdf")

        Given("the same valid external article PDF remains available for acceptance")
        When("the selected article-PDF target is built and its PDF review state is removed")
        CozyMedia.build(config.copy(target = Some("article-pdf")))
        Files.delete(root.resolve("target/cozy-media/pdf-review-state.json"))
        val missingreviewstate = intercept[RuntimeException](CozyMedia.preparePublication(config))

        Then("the selected public PDF is rejected for absent review-state evidence")
        missingreviewstate.getMessage should include("PDF review currentness failed for resource article-pdf")

        Given("the selected PDF still has valid ordinary receipt evidence but no review state")
        When("the selected article-PDF target is rebuilt")
        CozyMedia.build(config.copy(target = Some("article-pdf")))
        val prepared = CozyMedia.preparePublication(config)

        Then("the selected PDF preflight succeeds with current receipt and review-state evidence while image-only preparation remains outside this PDF-only contract")
        prepared.map(_.resource.id) shouldBe Vector("article-pdf")
      }
    }
    }
  }

  private def _write_article_fixture(root: Path): Path = {
    _write(root.resolve("knowledge.dox"), "knowledge")
    _write_pdf(root.resolve("article.pdf"), 1, "article")
    val descriptor = root.resolve("media.json")
    _write(descriptor,
      """{
        |  "schema": "cozy.media.v1",
        |  "knowledge": {"id": "article/example", "source": "knowledge.dox"},
        |  "resources": [{
        |    "id": "article-pdf", "kind": "document", "language": "en", "source": "article.pdf", "build": "prebuilt",
        |    "articleMedia": {"role": "article_pdf", "publicPath": "/articles/example/article.pdf", "mediaType": "application/pdf"}
        |  }]
        |}
        |""".stripMargin
    )
    descriptor
  }

  private def _write_public_article_fixture(root: Path): Path = {
    _write(root.resolve("knowledge.dox"), "knowledge")
    _write_pdf(root.resolve("article.pdf"), 1, "article")
    val descriptor = root.resolve("media.json")
    _write(descriptor,
      """{
        |  "schema": "cozy.media.v1",
        |  "knowledge": {"id": "article/example", "source": "knowledge.dox"},
        |  "profiles": {"site": {"root": "publication"}},
        |  "resources": [{
        |    "id": "article-pdf", "kind": "document", "language": "en", "source": "article.pdf", "build": "prebuilt",
        |    "publications": {"site": "articles/example/article.pdf"},
        |    "articleMedia": {"role": "article_pdf", "publicPath": "/articles/example/article.pdf", "mediaType": "application/pdf"}
        |  }]
        |}
        |""".stripMargin
    )
    descriptor
  }

  private def _write_summary_fixture(root: Path): Path = {
    _write(root.resolve("knowledge.dox"), "knowledge")
    _write_pdf(root.resolve("article.pdf"), 1, "article")
    _write_png(root.resolve("assets/infographic.png"))
    _write(root.resolve("template.pptx"), "template")
    _write(root.resolve("slides.json"),
      """{"schema":"cozy.slide-ir.v1","knowledge":"article/example","language":"en","slides":[{"id":"overview","title":"Overview","layout":"content","elements":[{"role":"title","text":"Overview"},{"role":"figure","asset":"infographic"}]}]}"""
    )
    val descriptor = root.resolve("media.json")
    _write(descriptor,
      """{
        |  "schema": "cozy.media.v1",
        |  "knowledge": {"id": "article/example", "source": "knowledge.dox"},
        |  "profiles": {"business": {"presentation": {"template": "template.pptx", "renderer": {"name": "fake", "version": "1", "command": ["fake-renderer"]}}}},
        |  "resources": [{
        |    "id": "article-pdf", "kind": "document", "language": "en", "source": "article.pdf", "build": "prebuilt",
        |    "articleMedia": {"role": "article_pdf", "publicPath": "/articles/example/article.pdf", "mediaType": "application/pdf"}
        |  }, {
        |    "id": "infographic", "kind": "infographic", "language": "en", "source": "assets/infographic.png", "build": "prebuilt",
        |    "articleMedia": {"role": "infographic", "publicPath": "/articles/example/infographic.png"}
        |  }, {
        |    "id": "summary", "kind": "document", "language": "en", "source": "slides.json", "output": "target/summary.pdf", "build": "summary-slides-pdf",
        |    "articleMedia": {"role": "summary_slides_pdf", "publicPath": "/articles/example/summary.pdf", "mediaType": "application/pdf"},
        |    "summarySlidesPdf": {"contract": "slide-ir-v1", "profile": "business", "slideImages": "target/slides", "montage": "target/montage.png", "rendererManifest": "target/summary-renderer.json", "articlePdf": "article-pdf", "infographic": "infographic"}
        |  }]
        |}
        |""".stripMargin
    )
    descriptor
  }

  private def _write_prebuilt_summary_fixture(root: Path): Path = {
    _write(root.resolve("knowledge.dox"), "knowledge")
    _write_pdf(root.resolve("summary.pdf"), 1, "summary")
    val descriptor = root.resolve("media.json")
    _write(descriptor,
      """{
        |  "schema": "cozy.media.v1",
        |  "knowledge": {"id": "article/example", "source": "knowledge.dox"},
        |  "resources": [{
        |    "id": "summary", "kind": "document", "language": "en", "source": "summary.pdf", "build": "prebuilt",
        |    "articleMedia": {"role": "summary_slides_pdf", "publicPath": "/articles/example/summary.pdf", "mediaType": "application/pdf"}
        |  }]
        |}
        |""".stripMargin
    )
    descriptor
  }

  private def _write_non_pdf_fixture(root: Path): Path = {
    _write(root.resolve("knowledge.dox"), "knowledge")
    _write_png(root.resolve("asset.png"))
    val descriptor = root.resolve("media.json")
    _write(descriptor,
      """{
        |  "schema": "cozy.media.v1",
        |  "knowledge": {"id": "article/example", "source": "knowledge.dox"},
        |  "resources": [{"id": "asset", "kind": "image", "source": "asset.png", "build": "prebuilt"}]
        |}
        |""".stripMargin
    )
    descriptor
  }

  private def _accept_dependencies(descriptor: Path): Unit = {
    CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("article-pdf")))
    CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("infographic")))
  }

  private def _renderer: CozyMedia.ProcessRunner =
    new CozyMedia.ProcessRunner {
      def run(command: Vector[String], workingDirectory: Path): Int = {
        val pdf = Path.of(command(command.indexOf("--pdf") + 1))
        val images = Path.of(command(command.indexOf("--slide-images") + 1))
        val montage = Path.of(command(command.indexOf("--montage") + 1))
        val manifest = Path.of(command(command.indexOf("--manifest") + 1))
        _write_pdf(pdf, 1, "summary")
        _write_png(images.resolve("overview.png"))
        _write_png(montage)
        _write(manifest,
          """{"schema":"cozy.summary-slides.render.v1","target":"summary","profile":"business","renderer":{"name":"fake","version":"1"},"pdf":{"pageCount":1},"slides":[{"id":"overview","title":"Overview","path":"target/slides/overview.png","assets":[{"id":"infographic"}]}],"montage":{"path":"target/montage.png"}}"""
        )
        0
      }
    }

  private def _replace(path: Path, before: String, after: String): Unit =
    _write(path, Files.readString(path, StandardCharsets.UTF_8).replace(before, after))

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _write_pdf(path: Path, pages: Int, title: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val document = new PDDocument()
    try {
      (1 to pages).foreach(_ => document.addPage(new PDPage()))
      document.getDocumentInformation.setTitle(title)
      document.save(path.toFile)
    } finally document.close()
  }

  private def _write_png(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00))
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val work = Paths.get(sys.props("user.dir")).resolve("target")
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "cozy-media-pdf-review-state-" + name + "-")
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val paths = Files.walk(path)
      try paths.iterator().asScala.toVector.reverse.foreach(Files.delete)
      finally paths.close()
    }
}
