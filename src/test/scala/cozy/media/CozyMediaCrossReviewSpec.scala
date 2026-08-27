package cozy.media

import cozy.video.{CozyVideo, CozyVideoImplementation}
import io.circe.Json
import io.circe.parser
import java.awt.{Color, Font, RenderingHints}
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.util.zip.{ZipEntry, ZipOutputStream}
import javax.imageio.ImageIO
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Aug. 27, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaCrossReviewSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Media Cross-media Review" should {
    "reconstruct one current Visual Page across presentation and Storyboard evidence without recording approval" in {
      _with_temp_dir("cross-media") { root =>
        Given("a current Visual Page presentation and a current approved Storyboard v2 visual-page review")
        val media = _write_media_fixture(root)
        var rendererinvoked = false

        When("Cozy builds the representative article-summary presentation")
        CozyMedia.build(CozyMedia.CommandConfig(media), _presentation_runner(root, () => rendererinvoked = true))

        Then("the generated slide is a readable 1280 by 720 inspection artifact")
        rendererinvoked shouldBe true
        val slide = ImageIO.read(root.resolve("target/slides/overview.png").toFile)
        slide should not be null
        slide.getWidth shouldBe 1280
        slide.getHeight shouldBe 720

        Given("the presentation renderer state is reset before Cross-media Review")
        rendererinvoked = false
        val storyboard = _storyboard()
        val source = _write_storyboard(root, storyboard)
        val project = _write_project(root, source, storyboard, "sha256:" + "0" * 64)
        val evidence = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, root.resolve("target/cozy-video/v2-review")))
        _write_project(root, source, storyboard, evidence.evidenceIdentity)
        val output = root.resolve("target/cozy-media/cross-review.json")

        When("Cozy builds and then verifies structural Cross-media Review evidence")
        val built = CozyMediaCrossReview.build(CozyMediaCrossReview.BuildConfig(media, "slides", project, "target/cozy-media/cross-review.json"))
        val verified = CozyMediaCrossReview.verify(CozyMediaCrossReview.VerifyConfig(media, "slides", project, "target/cozy-media/cross-review.json"))
        val review = parser.parse(Files.readString(output, StandardCharsets.UTF_8)).toOption.get

        Then("the common VisualPageSet and selected page are identity-bound without self-approval or renderer execution")
        built should include("status: valid")
        verified should include("status: valid")
        review.hcursor.get[String]("schema").toOption shouldBe Some("cozy.media.cross-review.v1")
        review.hcursor.get[String]("visualPageSetIdentity").toOption.get should startWith("sha256:")
        review.hcursor.downField("slides").downArray.get[String]("id").toOption shouldBe Some("overview")
        review.hcursor.downField("visualPages").downArray.get[String]("sceneId").toOption shouldBe Some("visual")
        review.hcursor.downField("verification").get[String]("semanticApproval").toOption shouldBe Some("not-recorded")
        review.hcursor.downField("verification").get[String]("visualApproval").toOption shouldBe Some("not-recorded")
        review.hcursor.downField("verification").get[String]("audiovisualApproval").toOption shouldBe Some("not-recorded")
        rendererinvoked shouldBe false

        Given("the saved review and then a participating binding are changed after construction")
        val originalreview = Files.readString(output, StandardCharsets.UTF_8)
        Files.writeString(output, originalreview.replace("\"status\":\"valid\"", "\"status\":\"changed\""), StandardCharsets.UTF_8)
        val tampered = intercept[RuntimeException] {
          CozyMediaCrossReview.verify(CozyMediaCrossReview.VerifyConfig(media, "slides", project, "target/cozy-media/cross-review.json"))
        }
        Files.writeString(output, originalreview, StandardCharsets.UTF_8)
        val binding = root.resolve("binding.json")
        val originalbinding = Files.readString(binding, StandardCharsets.UTF_8)
        Files.writeString(binding, originalbinding.replace("nodes-slot", "revised-nodes-slot"), StandardCharsets.UTF_8)
        val stale = intercept[RuntimeException] {
          CozyMediaCrossReview.verify(CozyMediaCrossReview.VerifyConfig(media, "slides", project, "target/cozy-media/cross-review.json"))
        }

        Then("tampered saved proof and stale participant input reject before any renderer or approval action")
        tampered.getMessage should include("Cross-media Review")
        stale.getMessage.nonEmpty shouldBe true
        rendererinvoked shouldBe false
      }
    }

    "route only the closed build and verify command grammars" in {
      _with_temp_dir("command") { root =>
        Given("the Cross-media command surface")
        val media = root.resolve("media.yaml")
        Files.writeString(media, "schema: cozy.media.v1\nknowledge: {id: example, source: article.dox}\nresources: []\n", StandardCharsets.UTF_8)

        When("unsupported Cross-media Review verbs are selected")
        val failure = intercept[RuntimeException] {
          CozyMedia.execute(List("media", "cross-review", "publish", media.toString))
        }

        Then("the command rejects rather than becoming publication behavior")
        failure.getMessage should include("Unsupported media command")
      }
    }

    "reject saving over current Storyboard review evidence" in {
      _with_temp_dir("collision") { root =>
        Given("a current Visual Page presentation and current Storyboard v2 review evidence")
        val media = _write_media_fixture(root)
        CozyMedia.build(CozyMedia.CommandConfig(media), _presentation_runner(root, () => ()))
        val storyboard = _storyboard()
        val source = _write_storyboard(root, storyboard)
        val initialproject = _write_project(root, source, storyboard, "sha256:" + "0" * 64)
        val evidence = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(initialproject, root.resolve("target/cozy-video/v2-review")))
        val project = _write_project(root, source, storyboard, evidence.evidenceIdentity)
        val evidencepath = CozyVideo.storyboardReviewCurrent(project).evidencePath
        val originalbytes = Files.readAllBytes(evidencepath).toVector
        val savepath = root.relativize(evidencepath).toString.replace(java.io.File.separatorChar, '/')

        When("Cross-media Review build uses --save at the current Storyboard review-evidence.json")
        val failure = intercept[RuntimeException] {
          CozyMediaCrossReview.build(CozyMediaCrossReview.BuildConfig(media, "slides", project, savepath))
        }

        Then("the existing evidence is rejected without changing any bytes")
        failure.getMessage should include("--save")
        Files.readAllBytes(evidencepath).toVector shouldBe originalbytes
      }
    }
  }

  private def _write_media_fixture(root: Path): Path = {
    _write(root.resolve("article.dox"), "Article")
    _write(root.resolve("article.pdf"), "PDF")
    _write_png(root.resolve("assets/infographic.png"))
    _write(root.resolve("sources/research.txt"), "research")
    _write(root.resolve("template.pptx"), "template")
    val page = CozyVisualPage.Page(
      "overview",
      "article/example",
      "en",
      CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "sequence",
        Vector(CozyVisualPage.Node("discover", "step", "Discover", Vector("research")), CozyVisualPage.Node("apply", "step", "Apply", Vector("research"))),
        Vector(CozyVisualPage.Relation("next", "next", "discover", "apply", Vector("research")))
      ),
      CozyVisualPage.Visual("flow-horizontal", Vector.empty),
      Vector(CozyVisualPage.Asset("infographic", "assets/infographic.png", "image/png", _sha256(root.resolve("assets/infographic.png")))),
      Vector(CozyVisualPage.SourceBinding("research", "sources/research.txt"))
    )
    _write(root.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(_catalog))
    _write(root.resolve("visual-pages.json"), CozyVisualPage.canonicalJson(CozyVisualPage.PageSet("article-pages", Vector(page))))
    _write(root.resolve("binding.json"), _binding_json)
    val descriptor = root.resolve("media.yaml")
    _write(descriptor,
      """schema: cozy.media.v1
        |knowledge:
        |  id: article/example
        |  source: article.dox
        |profiles:
        |  business:
        |    root: publication
        |    presentation:
        |      template: template.pptx
        |      renderer:
        |        name: fake
        |        version: "1"
        |        command: [fake-renderer]
        |receipt:
        |  inputs:
        |    - id: visual-pages
        |      role: visual-page-set
        |      path: visual-pages.json
        |      normalization: structured-document
        |    - id: catalog
        |      role: catalog
        |      path: catalog.json
        |      normalization: structured-document
        |    - id: binding
        |      role: binding
        |      path: binding.json
        |      normalization: bytes
        |    - id: template
        |      role: template
        |      path: template.pptx
        |      normalization: bytes
        |  producer:
        |    profile: business
        |    renderer:
        |      name: fake
        |      version: "1"
        |resources:
        |  - id: article-pdf
        |    kind: document
        |    language: en
        |    source: article.pdf
        |    output: article.pdf
        |    build: prebuilt
        |    publications:
        |      business: article.pdf
        |  - id: infographic
        |    kind: infographic
        |    language: en
        |    source: assets/infographic.png
        |    output: assets/infographic.png
        |    build: prebuilt
        |  - id: slides
        |    kind: presentation
        |    language: en
        |    role: article-summary
        |    source: visual-pages.json
        |    output: target/rendered/article.pptx
        |    build: presentation
        |    publications:
        |      business: article.pptx
        |    presentation:
        |      contract: visual-page-v1
        |      profile: business
        |      catalog: catalog.json
        |      binding: binding.json
        |      slideImages: target/slides
        |      montage: target/rendered/montage.png
        |      rendererManifest: target/rendered/renderer-manifest.json
        |      reviewManifest: review/manifest.json
        |      reviewState: review/state.yaml
        |      articlePdf: article-pdf
        |      infographic: infographic
        |""".stripMargin
    )
    descriptor
  }

  private def _presentation_runner(root: Path, invoked: () => Unit): CozyMedia.ProcessRunner =
    new CozyMedia.ProcessRunner {
      def run(command: Vector[String], workingdirectory: Path): Int = {
        invoked()
        val pptx = Path.of(command(command.indexOf("--pptx") + 1))
        val images = Path.of(command(command.indexOf("--slide-images") + 1))
        val montage = Path.of(command(command.indexOf("--montage") + 1))
        val manifest = Path.of(command(command.indexOf("--manifest") + 1))
        val validated = CozyVisualPage.load(root.resolve("visual-pages.json"), root.resolve("catalog.json"))
        val binding = CozyVisualPageBinding.load(root.resolve("binding.json"), validated)
        _write_pptx(pptx, "Overview", root.resolve("assets/infographic.png"))
        _write_png(images.resolve("overview.png"))
        _write_png(montage)
        val pptxhash = _sha256(pptx)
        _write(manifest,
          s"""{"schema":"cozy.presentation.render.v2","target":"slides","profile":"business","renderer":{"name":"fake","version":"1"},"visualPageSetSha256":"${_semantic_sha256(validated.documentIdentity)}","catalogSha256":"${_semantic_sha256(validated.catalogIdentity)}","bindingSha256":"${_semantic_sha256(binding.bindingIdentity)}","templateSha256":"${_sha256(root.resolve("template.pptx"))}","pptx":{"path":"target/rendered/article.pptx","sha256":"$pptxhash"},"slides":[{"id":"overview","path":"target/slides/overview.png","sha256":"${_sha256(images.resolve("overview.png"))}","pptxSha256":"$pptxhash","assets":[{"id":"infographic","sha256":"${_sha256(root.resolve("assets/infographic.png"))}"}]}],"montage":{"path":"target/rendered/montage.png","sha256":"${_sha256(montage)}","pptxSha256":"$pptxhash"}}"""
        )
        0
      }
    }

  private def _storyboard(): CozyVideo.Storyboard =
    CozyVideo.Storyboard(
      "cozy.video.storyboard.v2",
      2,
      Vector(CozyVideo.StoryboardScene(
        "visual",
        1,
        "opening",
        "narrator",
        "narration",
        "Visual Page proof.",
        CozyVideoImplementation.StoryboardVisualPageScreen("visual-pages.json", "catalog.json", "overview"),
        "Visual Page",
        BigDecimal("1.0"),
        BigDecimal("0.0"),
        "cut",
        Vector.empty,
        Vector.empty,
        Vector.empty,
        Vector.empty,
        "Review the exact visual page."
      ))
    )

  private def _write_storyboard(root: Path, storyboard: CozyVideo.Storyboard): Path = {
    val source = root.resolve("storyboard.json")
    _write(source, CozyVideo.canonicalStoryboardJson(storyboard))
    source
  }

  private def _write_project(root: Path, source: Path, storyboard: CozyVideo.Storyboard, approval: String): Path = {
    val review = Json.obj(
      "source" -> Json.fromString(root.relativize(source).toString.replace(java.io.File.separatorChar, '/')),
      "approvedIdentity" -> Json.fromString(CozyVideo.storyboardIdentity(storyboard)),
      "visualPage" -> Json.obj(
        "binding" -> Json.fromString("binding.json"),
        "evidenceDirectory" -> Json.fromString("target/cozy-video/v2-review"),
        "approvedEvidenceIdentity" -> Json.fromString(approval)
      )
    )
    val project = Json.obj(
      "name" -> Json.fromString("cross-media-review"),
      "renderer" -> Json.obj("engine" -> Json.fromString("remotion"), "fps" -> Json.fromInt(30)),
      "storyboardReview" -> review,
      "parts" -> Json.arr(Json.obj("id" -> Json.fromString("storyboard"), "type" -> Json.fromString("storyboard"), "storyboard" -> Json.fromString("storyboard.json")))
    )
    val path = root.resolve("video-v2.json")
    _write(path, project.noSpaces)
    path
  }

  private def _catalog: CozyVisualPage.Catalog = CozyVisualPage.Catalog(
    "core",
    1,
    Vector(
      CozyVisualPage.RelationDefinition("next", "from-to"),
      CozyVisualPage.RelationDefinition("causes", "from-to"),
      CozyVisualPage.RelationDefinition("depends-on", "from-to"),
      CozyVisualPage.RelationDefinition("enables", "from-to"),
      CozyVisualPage.RelationDefinition("maps-to", "from-to")
    ),
    Vector(
      CozyVisualPage.LogicalPattern("sequence", Vector(CozyVisualPage.NodeRole("step", 2, 8)), Vector(CozyVisualPage.RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear"))),
      CozyVisualPage.LogicalPattern("causal-chain", Vector(CozyVisualPage.NodeRole("cause", 1, 7), CozyVisualPage.NodeRole("effect", 1, 7)), Vector(CozyVisualPage.RelationRule("causes", Vector("cause"), Vector("effect"), 1, 16, "acyclic"), CozyVisualPage.RelationRule("enables", Vector("cause"), Vector("effect"), 1, 16, "acyclic"))),
      CozyVisualPage.LogicalPattern("dependency-map", Vector(CozyVisualPage.NodeRole("dependency", 1, 7), CozyVisualPage.NodeRole("dependent", 1, 7)), Vector(CozyVisualPage.RelationRule("depends-on", Vector("dependent"), Vector("dependency"), 1, 16, "acyclic"))),
      CozyVisualPage.LogicalPattern("mapping", Vector(CozyVisualPage.NodeRole("source", 1, 7), CozyVisualPage.NodeRole("target", 1, 7)), Vector(CozyVisualPage.RelationRule("maps-to", Vector("source"), Vector("target"), 1, 16, "bipartite")))
    ),
    Vector(
      CozyVisualPage.VisualPattern("flow-horizontal", Vector("causal-chain", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false), CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", true), CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", true)))
    )
  )

  private def _binding_json: String = {
    val slots = Vector("knowledge", "nodes", "relations", "assets", "parameters").map(slot => s"""{"semanticSlot":"$slot","physicalSlot":"$slot-slot"}""").mkString("[", ",", "]")
    val patterns = Vector("flow-horizontal", "flow-vertical", "mapping-columns").map(pattern => s"""{"visualPattern":"$pattern","slots":$slots}""").mkString("[", ",", "]")
    s"""{"schema":"cozy.visual-page.binding.v1","version":1,"id":"business-default","profile":"business","catalog":{"id":"core","revision":1},"patterns":$patterns}"""
  }

  private def _write_pptx(path: Path, title: String, asset: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val output = new ZipOutputStream(Files.newOutputStream(path))
    try {
      _zip(output, "[Content_Types].xml", """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>""")
      _zip(output, "ppt/presentation.xml", """<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst></p:presentation>""")
      _zip(output, "ppt/_rels/presentation.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/></Relationships>""")
      _zip(output, "ppt/slides/slide1.xml", s"""<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:cSld><p:spTree><a:p><a:r><a:t>$title</a:t></a:r></a:p><a:blip r:embed="rId1"/></p:spTree></p:cSld></p:sld>""")
      _zip(output, "ppt/slides/_rels/slide1.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/></Relationships>""")
      output.putNextEntry(new ZipEntry("ppt/media/image1.png"))
      output.write(Files.readAllBytes(asset))
      output.closeEntry()
    } finally output.close()
  }

  private def _write_png(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    try {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      graphics.setColor(Color.WHITE)
      graphics.fillRect(0, 0, image.getWidth, image.getHeight)
      graphics.setColor(new Color(20, 49, 81))
      graphics.fillRect(0, 0, image.getWidth, 116)
      graphics.setColor(Color.WHITE)
      graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42))
      graphics.drawString("Article Summary: Visual Page", 56, 72)
      graphics.setColor(new Color(20, 49, 81))
      graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26))
      graphics.drawString("Overview", 56, 160)
      graphics.setColor(new Color(227, 242, 253))
      graphics.fillRoundRect(92, 250, 390, 170, 18, 18)
      graphics.fillRoundRect(798, 250, 390, 170, 18, 18)
      graphics.setColor(new Color(20, 49, 81))
      graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34))
      graphics.drawString("Discover", 190, 345)
      graphics.drawString("Apply", 920, 345)
      graphics.setStroke(new java.awt.BasicStroke(8f))
      graphics.drawLine(512, 335, 748, 335)
      graphics.fillPolygon(Array(748, 718, 718), Array(335, 315, 355), 3)
      graphics.setColor(new Color(0, 121, 107))
      graphics.fillRoundRect(448, 510, 384, 100, 18, 18)
      graphics.setColor(Color.WHITE)
      graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24))
      graphics.drawString("Infographic asset", 533, 570)
    } finally graphics.dispose()
    ImageIO.write(image, "png", path.toFile) shouldBe true
    _export_visual_inspection(path)
  }

  private def _export_visual_inspection(path: Path): Unit =
    Option(System.getProperty("cozy.p36.crossReviewInspection")).filter(_ == "true").foreach { _ =>
      val destination = Path.of("target/phase-36/cross-review-inspection")
        .toAbsolutePath.normalize()
        .resolve(path.getFileName.toString)
      Option(destination.getParent).foreach(Files.createDirectories(_))
      Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
    }

  private def _zip(output: ZipOutputStream, name: String, value: String): Unit = {
    output.putNextEntry(new ZipEntry(name))
    output.write(value.getBytes(StandardCharsets.UTF_8))
    output.closeEntry()
  }

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _semantic_sha256(value: String): String = value.stripPrefix("sha256:")
  private def _sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _with_temp_dir[A](name: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-media-cross-review-" + name + "-").toRealPath()
    try body(root)
    finally {
      val stream = Files.walk(root)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.delete)
      finally stream.close()
    }
  }
}
