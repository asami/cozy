package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import javax.imageio.ImageIO
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Aug. 27, 2026
 *  version Sep. 30, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVisualPagePreviewSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Visual Page semantic Preview" should {
    "generate deterministic ordered HTML and provenance while escaping supplied semantic text" in {
      _with_work("preview-html") { root =>
        Given("one ordered Visual Page Set with next, causes, and depends-on Relations plus escaped labels")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research-a.txt"), "research-a")
        _write(root.resolve("sources/research-b.txt"), "research-b")
        val escaped = _sequence_page("sequence-page", "<Discover & \"Plan\">", Vector("research-a", "research-b"))
        val set = CozyVisualPage.PageSet("preview-set", Vector(escaped, _causal_page(), _dependency_page()))
        val input = _write(root.resolve("pages.json"), CozyVisualPage.canonicalJson(set))
        val firsthtml = root.resolve("first.html")
        val secondhtml = root.resolve("second.html")

        When("Preview is generated twice from the same validated document and catalog")
        val firstreport = CozyVisualPage.execute(List("preview", input.toString, "--catalog", catalog.toString, "--save", firsthtml.toString))
        val secondreport = CozyVisualPage.execute(List("preview", input.toString, "--catalog", catalog.toString, "--save", secondhtml.toString))
        val html = Files.readString(firsthtml, StandardCharsets.UTF_8)
        val firstbytes = Files.readAllBytes(firsthtml).toVector
        val secondbytes = Files.readAllBytes(secondhtml).toVector

        Then("the bytes, semantic provenance, page and node order, Relation meaning, and typed values are stable without raw HTML injection")
        firstbytes shouldBe secondbytes
        firstreport shouldBe secondreport
        firstreport shouldBe _expected_report("preview-set", 3, false)
        firstreport.split("\n").map(_.takeWhile(_ != ':')).toVector shouldBe Vector(
          "schema",
          "version",
          "generator",
          "documentId",
          "catalogId",
          "catalogRevision",
          "pageCount",
          "html",
          "status"
        )
        html should include_text("<dt>Document schema</dt><dd><code>cozy.visual-page-set.v1</code></dd>")
        html should include_text("<dt>Document ID</dt><dd><code>preview-set</code></dd>")
        html should include_text("<dt>Catalog ID</dt><dd><code>core</code></dd>")
        html should include_text("<dt>Catalog revision</dt><dd><code>1</code></dd>")
        html.contains("<dt>Document identity</dt>") shouldBe false
        html.contains("<dt>Catalog identity</dt>") shouldBe false
        html.indexOf("data-page-id=\"sequence-page\"") should be < html.indexOf("data-page-id=\"causal-page\"")
        html.indexOf("data-page-id=\"causal-page\"") should be < html.indexOf("data-page-id=\"dependency-page\"")
        html.indexOf("data-node-id=\"discover-page\"") should be < html.indexOf("data-node-id=\"apply-page\"")
        html should include_text("&lt;Discover &amp; &quot;Plan&quot;&gt;")
        html.contains("<Discover & \"Plan\">") shouldBe false
        html should include_text("type <code>next</code>; direction <code>from-to</code>")
        html should include_text("type <code>causes</code>; direction <code>from-to</code>")
        html should include_text("type <code>depends-on</code>; direction <code>from-to</code>")
        html should include_text("<code>emphasisNode</code>: <code>node-ref</code> <code>discover-page</code>")
        html should include_text("<code>showRelationLabels</code>: <code>boolean</code> <code>true</code>")
      }
    }

    "generate deterministic valid Preview-only HTML and PNG with semantic provenance" in {
      _with_work("preview-png") { root =>
        Given("one validated sequence page, closed catalog, and direct source")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research-a.txt"), "research-a")
        _write(root.resolve("sources/research-b.txt"), "research-b")
        val input = _write(root.resolve("page.json"), CozyVisualPage.canonicalJson(_sequence_page("sequence-page", "Discover", Vector("research-a", "research-b"))))
        val html = root.resolve("preview.html")
        val png = root.resolve("preview.png")

        When("Preview receives its optional PNG target twice for the same inputs")
        val report = CozyVisualPage.execute(List("preview", input.toString, "--catalog=" + catalog.toString, "--save=" + html.toString, "--png=" + png.toString))
        val firsthtml = Files.readAllBytes(html).toVector
        val firstpng = Files.readAllBytes(png).toVector
        val repeatedreport = CozyVisualPage.execute(List("preview", input.toString, "--catalog=" + catalog.toString, "--save=" + html.toString, "--png=" + png.toString))
        val repeatedhtml = Files.readAllBytes(html).toVector
        val repeatedpng = Files.readAllBytes(png).toVector
        val rendered = Files.readString(html, StandardCharsets.UTF_8)
        val image = ImageIO.read(png.toFile)

        Then("both artifacts repeat byte for byte and the report records optional PNG generation with no digest fields")
        firsthtml shouldBe repeatedhtml
        firstpng shouldBe repeatedpng
        report shouldBe repeatedreport
        report shouldBe _expected_report("sequence-page", 1, true)
        firstpng.take(8).map(_ & 0xff) shouldBe Vector(137, 80, 78, 71, 13, 10, 26, 10)
        image should not be null
        image.getWidth shouldBe 960
        image.getHeight shouldBe 176
        report.split("\n").map(_.takeWhile(_ != ':')).toVector shouldBe Vector(
          "schema",
          "version",
          "generator",
          "documentId",
          "catalogId",
          "catalogRevision",
          "pageCount",
          "html",
          "png",
          "status"
        )
        rendered.contains("<dt>Document identity</dt>") shouldBe false
        rendered.contains("<dt>Catalog identity</dt>") shouldBe false
        rendered.toLowerCase.contains("renderer") shouldBe false
      }
    }

    "preserve selected output bytes when semantic inputs or Preview arguments are invalid" in {
      _with_work("preview-preservation") { root =>
        Given("pre-existing HTML and PNG outputs, one valid source, and one invalid legacy source")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research-a.txt"), "research-a")
        _write(root.resolve("sources/research-b.txt"), "research-b")
        val valid = _write(root.resolve("valid.json"), CozyVisualPage.canonicalJson(_sequence_page("sequence-page", "Discover", Vector("research-a", "research-b"))))
        val invalid = _write(root.resolve("invalid.json"), "{\"schema\":\"cozy.slide-ir.v1\"}")
        val html = _write(root.resolve("preserve.html"), "preserve-html")
        val png = _write(root.resolve("preserve.png"), "preserve-png")

        When("invalid semantic input, duplicate output switches, valueless switches, and unknown switches are rejected")
        val failures = Vector(
          _failure(CozyVisualPage.execute(List("preview", invalid.toString, "--catalog", catalog.toString, "--save", html.toString, "--png", png.toString))),
          _failure(CozyVisualPage.execute(List("preview", valid.toString, "--catalog", catalog.toString, "--save", html.toString, "--save", root.resolve("duplicate.html").toString))),
          _failure(CozyVisualPage.execute(List("preview", valid.toString, "--catalog", catalog.toString, "--save", html.toString, "--png"))),
          _failure(CozyVisualPage.execute(List("preview", valid.toString, "--catalog", catalog.toString, "--save", html.toString, "--other", "value")))
        )

        Then("every rejection is structured and both pre-existing selected outputs retain their exact bytes")
        failures.map(_.getMessage).forall(_.contains("VISUAL_PAGE_")) shouldBe true
        Files.readString(html, StandardCharsets.UTF_8) shouldBe "preserve-html"
        Files.readString(png, StandardCharsets.UTF_8) shouldBe "preserve-png"
      }
    }

    "preserve deterministic semantic provenance and logical source ordering across generated valid inputs" in {
      _with_work("preview-property") { root =>
        Given("a closed catalog and two direct declared sources for generated sequence pages")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research-a.txt"), "research-a")
        _write(root.resolve("sources/research-b.txt"), "research-b")
        val variations = Gen.zip(Gen.chooseNum(0, 99), Gen.oneOf(true, false))
        val html = root.resolve("property.html")
        val png = root.resolve("property.png")

        When("ScalaCheck generates valid ordered node and source-reference combinations for Preview")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(variations) { case (index, reverse) =>
          val sources = if (reverse) Vector("research-b", "research-a") else Vector("research-a", "research-b")
          val page = _sequence_page("sequence-" + index, "Discover " + index, sources)
          val input = _write(root.resolve("property.json"), CozyVisualPage.canonicalJson(page))
          val firstreport = CozyVisualPage.execute(List("preview", input.toString, "--catalog", catalog.toString, "--save", html.toString, "--png", png.toString))
          val firsthtml = Files.readAllBytes(html).toVector
          val firstpng = Files.readAllBytes(png).toVector
          val secondreport = CozyVisualPage.execute(List("preview", input.toString, "--catalog", catalog.toString, "--save", html.toString, "--png", png.toString))
          val secondhtml = Files.readAllBytes(html).toVector
          val secondpng = Files.readAllBytes(png).toVector
          val rendered = Files.readString(html, StandardCharsets.UTF_8)
          val validated = CozyVisualPage.load(input, catalog)
          val firstnode = "data-node-id=\"discover-" + index + "\""
          val secondnode = "data-node-id=\"apply-" + index + "\""
          val prohibitedtokens = Vector("binding", "template", "renderer", "pptx", "receipt", "review-state", "video")
          val matchedtokens = prohibitedtokens.filter(token => rendered.toLowerCase.contains(token))
          Prop.all(Vector(
            Prop(firstreport == secondreport).label("stable-report"),
            Prop(firsthtml == secondhtml).label("stable-html-bytes"),
            Prop(firstpng == secondpng).label("stable-png-bytes"),
            Prop(firstreport == _expected_report(page.id, 1, true)).label("exact-report"),
            Prop(rendered.indexOf(firstnode) < rendered.indexOf(secondnode)).label("node-order"),
            Prop(rendered.contains("sources <code>" + sources.mkString(",") + "</code>")).label("source-order"),
            Prop(firstreport.contains("documentId: " + validated.document.id)).label("document-id"),
            Prop(firstreport.contains("catalogId: " + validated.catalog.id)).label("catalog-id"),
            Prop(firstreport.contains("catalogRevision: " + validated.catalog.revision)).label("catalog-revision"),
            Prop(firstreport.contains("pageCount: " + validated.count)).label("page-count"),
            Prop(rendered.contains("<dt>Document ID</dt><dd><code>" + page.id + "</code></dd>")).label("html-document-id"),
            Prop(rendered.contains("<dt>Catalog ID</dt><dd><code>core</code></dd>")).label("html-catalog-id"),
            Prop(rendered.contains("<dt>Catalog revision</dt><dd><code>1</code></dd>")).label("html-catalog-revision"),
            Prop(!rendered.contains("<dt>Document identity</dt>") && !rendered.contains("<dt>Catalog identity</dt>")).label("no-digest-metadata-rows"),
            Prop(prohibitedtokens.forall(token => !rendered.toLowerCase.contains(token))).label("prohibited-integration-token:" + matchedtokens.mkString(","))
          ): _*)
        })

        Then("at least thirty generated cases retain semantic provenance, source order, and repeated HTML/PNG bytes without physical-rendering semantics")
        withClue(check.status.toString) {
          check.passed shouldBe true
        }
        check.succeeded should be >= 30
      }
    }

    "render a changed valid label to the same outputs without rebinding semantic identifiers" in {
      _with_work("preview-label-change") { root =>
        Given("one valid sequence page with stable IDs, catalog, Relations, typed parameters, and direct sources")
        val catalog = _write_catalog(root)
        val catalogbytes = Files.readAllBytes(catalog).toVector
        val firstsource = _write(root.resolve("sources/research-a.txt"), "research-a")
        val secondsource = _write(root.resolve("sources/research-b.txt"), "research-b")
        val sourcebytes = Vector(firstsource, secondsource).map(path => Files.readAllBytes(path).toVector)
        val page = _sequence_page("sequence-page", "Discover", Vector("research-a", "research-b"))
        val originaljson = CozyVisualPage.canonicalJson(page)
        val input = _write(root.resolve("page.json"), originaljson)
        val changedpage = page.copy(logical = page.logical.copy(nodes = page.logical.nodes.updated(
          0, page.logical.nodes.head.copy(label = "<Discover & \"Replan\">")
        )))
        val changedjson = CozyVisualPage.canonicalJson(changedpage)
        val html = root.resolve("preview.html")
        val png = root.resolve("preview.png")
        val args = List("preview", input.toString, "--catalog", catalog.toString, "--save", html.toString, "--png", png.toString)

        When("Preview is generated, only the node label is edited, and Preview is generated again to the same selected targets")
        val firstreport = CozyVisualPage.execute(args)
        val originalvalidated = CozyVisualPage.load(input, catalog)
        val originalpage = originalvalidated.document.pages.head
        val originalinput = Files.readAllBytes(input).toVector
        val originalhtml = Files.readAllBytes(html).toVector
        val originalimage = ImageIO.read(png.toFile)
        _write(input, changedjson)
        val changedreport = CozyVisualPage.execute(args)
        val validated = CozyVisualPage.load(input, catalog)
        val renderedpage = validated.document.pages.head
        val changedinput = Files.readAllBytes(input).toVector
        val changedcatalog = Files.readAllBytes(catalog).toVector
        val changedsources = Vector(firstsource, secondsource).map(path => Files.readAllBytes(path).toVector)
        val changedhtml = Files.readAllBytes(html).toVector
        val rendered = Files.readString(html, StandardCharsets.UTF_8)
        val pngbytes = Files.readAllBytes(png).toVector
        val image = ImageIO.read(png.toFile)

        Then("the escaped new label reaches HTML while semantic IDs, catalog, relation and parameter meaning, and source bytes remain stable and PNG remains valid")
        firstreport shouldBe _expected_report("sequence-page", 1, true)
        changedreport shouldBe firstreport
        originalinput shouldBe originaljson.getBytes(StandardCharsets.UTF_8).toVector
        changedinput shouldBe changedjson.getBytes(StandardCharsets.UTF_8).toVector
        changedcatalog shouldBe catalogbytes
        changedsources shouldBe sourcebytes
        renderedpage.copy(logical = originalpage.logical) shouldBe originalpage
        renderedpage.logical.pattern shouldBe originalpage.logical.pattern
        renderedpage.logical.nodes.head.label shouldBe "<Discover & \"Replan\">"
        renderedpage.logical.nodes.map(_.id) shouldBe originalpage.logical.nodes.map(_.id)
        renderedpage.logical.nodes.head.copy(label = originalpage.logical.nodes.head.label) shouldBe originalpage.logical.nodes.head
        renderedpage.logical.nodes.tail shouldBe originalpage.logical.nodes.tail
        renderedpage.logical.relations shouldBe originalpage.logical.relations
        validated.catalog shouldBe originalvalidated.catalog
        changedhtml should not be originalhtml
        rendered should include_text("&lt;Discover &amp; &quot;Replan&quot;&gt;")
        rendered.contains("<Discover & \"Replan\">") shouldBe false
        rendered.contains("<q>Discover</q>") shouldBe false
        rendered should include_text("<dt>Document ID</dt><dd><code>sequence-page</code></dd>")
        rendered should include_text("<dt>Catalog ID</dt><dd><code>core</code></dd>")
        rendered should include_text("<dt>Catalog revision</dt><dd><code>1</code></dd>")
        rendered should include_text("type <code>next</code>; direction <code>from-to</code>; from <code>discover-page</code>; to <code>apply-page</code>")
        rendered should include_text("<code>emphasisNode</code>: <code>node-ref</code> <code>discover-page</code>")
        rendered should include_text("<code>showRelationLabels</code>: <code>boolean</code> <code>true</code>")
        rendered.contains("<dt>Document identity</dt>") shouldBe false
        rendered.contains("<dt>Catalog identity</dt>") shouldBe false
        pngbytes.take(8).map(_ & 0xff) shouldBe Vector(137, 80, 78, 71, 13, 10, 26, 10)
        originalimage should not be null
        image should not be null
        image.getWidth shouldBe originalimage.getWidth
        image.getHeight shouldBe originalimage.getHeight
        image.getWidth shouldBe 960
        image.getHeight shouldBe 176
      }
    }
  }

  private def _expected_report(documentid: String, pagecount: Int, pnggenerated: Boolean): String =
    (Vector(
      "schema: cozy.visual-page.preview.v1",
      "version: 1",
      "generator: cozy media visual-page preview",
      "documentId: " + documentid,
      "catalogId: core",
      "catalogRevision: 1",
      "pageCount: " + pagecount,
      "html: generated"
    ) ++ (if (pnggenerated) Vector("png: generated") else Vector.empty) ++ Vector("status: generated")).mkString("\n")

  private def _sequence_page(id: String, label: String, sources: Vector[String]): CozyVisualPage.Page =
    CozyVisualPage.Page(
      id,
      "knowledge-1",
      "en",
      CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "sequence",
        Vector(
          CozyVisualPage.Node("discover-" + id.stripPrefix("sequence-"), "step", label, sources),
          CozyVisualPage.Node("apply-" + id.stripPrefix("sequence-"), "step", "Apply", sources)
        ),
        Vector(CozyVisualPage.Relation("transition-" + id, "next", "discover-" + id.stripPrefix("sequence-"), "apply-" + id.stripPrefix("sequence-"), sources))
      ),
      CozyVisualPage.Visual("flow-horizontal", Vector(
        CozyVisualPage.VisualParameter("emphasisNode", CozyVisualPage.StringParameter("discover-" + id.stripPrefix("sequence-"))),
        CozyVisualPage.VisualParameter("showRelationLabels", CozyVisualPage.BooleanParameter(true))
      )),
      Vector.empty,
      sources.map(source => CozyVisualPage.SourceBinding(source, "sources/" + source + ".txt"))
    )

  private def _causal_page(): CozyVisualPage.Page =
    CozyVisualPage.Page(
      "causal-page", "knowledge-1", "en", CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "causal-chain",
        Vector(
          CozyVisualPage.Node("cause", "cause", "Cause", Vector("research-a")),
          CozyVisualPage.Node("effect", "effect", "Effect", Vector("research-a"))
        ),
        Vector(
          CozyVisualPage.Relation("cause-edge", "causes", "cause", "effect", Vector("research-a")),
          CozyVisualPage.Relation("enable-edge", "enables", "cause", "effect", Vector("research-a"))
        )
      ),
      CozyVisualPage.Visual("flow-horizontal", Vector.empty),
      Vector.empty,
      Vector(CozyVisualPage.SourceBinding("research-a", "sources/research-a.txt"))
    )

  private def _dependency_page(): CozyVisualPage.Page =
    CozyVisualPage.Page(
      "dependency-page", "knowledge-1", "en", CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "dependency-map",
        Vector(
          CozyVisualPage.Node("dependency", "dependency", "Dependency", Vector("research-a")),
          CozyVisualPage.Node("dependent", "dependent", "Dependent", Vector("research-a"))
        ),
        Vector(CozyVisualPage.Relation("dependency-edge", "depends-on", "dependent", "dependency", Vector("research-a")))
      ),
      CozyVisualPage.Visual("flow-vertical", Vector.empty),
      Vector.empty,
      Vector(CozyVisualPage.SourceBinding("research-a", "sources/research-a.txt"))
    )

  private def _catalog(): CozyVisualPage.Catalog = CozyVisualPage.Catalog(
    "core", 1,
    Vector(
      CozyVisualPage.RelationDefinition("next", "from-to"),
      CozyVisualPage.RelationDefinition("causes", "from-to"),
      CozyVisualPage.RelationDefinition("depends-on", "from-to"),
      CozyVisualPage.RelationDefinition("enables", "from-to"),
      CozyVisualPage.RelationDefinition("maps-to", "from-to")
    ),
    Vector(
      CozyVisualPage.LogicalPattern("sequence", Vector(CozyVisualPage.NodeRole("step", 2, 8)), Vector(CozyVisualPage.RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear"))),
      CozyVisualPage.LogicalPattern("causal-chain", Vector(CozyVisualPage.NodeRole("cause", 1, 7), CozyVisualPage.NodeRole("effect", 1, 7)), Vector(
        CozyVisualPage.RelationRule("causes", Vector("cause"), Vector("effect"), 1, 16, "acyclic"),
        CozyVisualPage.RelationRule("enables", Vector("cause"), Vector("effect"), 1, 16, "acyclic")
      )),
      CozyVisualPage.LogicalPattern("dependency-map", Vector(CozyVisualPage.NodeRole("dependency", 1, 7), CozyVisualPage.NodeRole("dependent", 1, 7)), Vector(CozyVisualPage.RelationRule("depends-on", Vector("dependent"), Vector("dependency"), 1, 16, "acyclic"))),
      CozyVisualPage.LogicalPattern("mapping", Vector(CozyVisualPage.NodeRole("source", 1, 7), CozyVisualPage.NodeRole("target", 1, 7)), Vector(CozyVisualPage.RelationRule("maps-to", Vector("source"), Vector("target"), 1, 16, "bipartite")))
    ),
    Vector(
      CozyVisualPage.VisualPattern("flow-horizontal", Vector("causal-chain", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false), CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", true), CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", true)))
    )
  )

  private def _write_catalog(root: Path): Path = _write(root.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(_catalog()))

  private def _write(path: Path, value: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _failure[A](body: => A): RuntimeException = intercept[RuntimeException](body)

  private def _with_work[A](name: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-visual-page-preview-" + name + "-")
    try body(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) Files.list(path).iterator().asScala.foreach(_delete_tree)
      Files.deleteIfExists(path)
    }
}
