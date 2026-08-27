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
 * @version Aug. 27, 2026
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
        val firstidentity = CozyVisualPage.load(input, catalog)

        Then("the bytes, report, page order, Relation meaning, typed values, and identities are stable without raw HTML injection")
        Files.readAllBytes(firsthtml).toVector shouldBe Files.readAllBytes(secondhtml).toVector
        firstreport shouldBe secondreport
        firstreport.split("\n").map(_.takeWhile(_ != ':')).toVector shouldBe Vector(
          "schema",
          "version",
          "generator",
          "documentIdentity",
          "catalogIdentity",
          "htmlIdentity",
          "previewIdentity",
          "status"
        )
        firstreport should include_text("documentIdentity: " + firstidentity.documentIdentity)
        firstreport should include_text("catalogIdentity: " + firstidentity.catalogIdentity)
        html.indexOf("data-page-id=\"sequence-page\"") should be < html.indexOf("data-page-id=\"causal-page\"")
        html.indexOf("data-page-id=\"causal-page\"") should be < html.indexOf("data-page-id=\"dependency-page\"")
        html should include_text("&lt;Discover &amp; &quot;Plan&quot;&gt;")
        html.contains("<Discover & \"Plan\">") shouldBe false
        html should include_text("type <code>next</code>; direction <code>from-to</code>")
        html should include_text("type <code>causes</code>; direction <code>from-to</code>")
        html should include_text("type <code>depends-on</code>; direction <code>from-to</code>")
        html should include_text("<code>node-ref</code>")
      }
    }

    "generate one deterministic valid Preview-only PNG and bind it into output provenance" in {
      _with_work("preview-png") { root =>
        Given("one validated sequence page, closed catalog, and direct source")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research-a.txt"), "research-a")
        _write(root.resolve("sources/research-b.txt"), "research-b")
        val input = _write(root.resolve("page.json"), CozyVisualPage.canonicalJson(_sequence_page("sequence-page", "Discover", Vector("research-a", "research-b"))))
        val html = root.resolve("preview.html")
        val png = root.resolve("preview.png")

        When("Preview receives its optional PNG target")
        val report = CozyVisualPage.execute(List("preview", input.toString, "--catalog=" + catalog.toString, "--save=" + html.toString, "--png=" + png.toString))
        val image = ImageIO.read(png.toFile)

        Then("both generated artifacts are valid and the optional PNG identity occurs before the Preview provenance identity")
        Files.readAllBytes(png).take(8).map(_ & 0xff).toVector shouldBe Vector(137, 80, 78, 71, 13, 10, 26, 10)
        image should not be null
        image.getWidth shouldBe 960
        report.split("\n").map(_.takeWhile(_ != ':')).toVector shouldBe Vector(
          "schema",
          "version",
          "generator",
          "documentIdentity",
          "catalogIdentity",
          "htmlIdentity",
          "pngIdentity",
          "previewIdentity",
          "status"
        )
        report should include_text("pngIdentity: sha256:")
        report should include_text("previewIdentity: sha256:")
        Files.readString(html, StandardCharsets.UTF_8).toLowerCase.contains("renderer") shouldBe false
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

    "preserve deterministic logical source ordering and identity binding across generated valid semantic inputs" in {
      _with_work("preview-property") { root =>
        Given("a closed catalog and two direct declared sources for generated sequence pages")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research-a.txt"), "research-a")
        _write(root.resolve("sources/research-b.txt"), "research-b")
        val variations = Gen.zip(Gen.chooseNum(0, 99), Gen.oneOf(true, false))
        val html = root.resolve("property.html")

        When("ScalaCheck generates valid ordered node and source-reference combinations for Preview")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(variations) { case (index, reverse) =>
          val sources = if (reverse) Vector("research-b", "research-a") else Vector("research-a", "research-b")
          val page = _sequence_page("sequence-" + index, "Discover " + index, sources)
          val input = _write(root.resolve("property.json"), CozyVisualPage.canonicalJson(page))
          val firstreport = CozyVisualPage.execute(List("preview", input.toString, "--catalog", catalog.toString, "--save", html.toString))
          val secondreport = CozyVisualPage.execute(List("preview", input.toString, "--catalog", catalog.toString, "--save", html.toString))
          val rendered = Files.readString(html, StandardCharsets.UTF_8)
          val validated = CozyVisualPage.load(input, catalog)
          val firstnode = "data-node-id=\"discover-" + index + "\""
          val secondnode = "data-node-id=\"apply-" + index + "\""
          val prohibitedtokens = Vector("binding", "template", "renderer", "pptx", "receipt", "review-state", "video")
          val matchedtokens = prohibitedtokens.filter(token => rendered.toLowerCase.contains(token))
          Prop.all(Vector(
            Prop(firstreport == secondreport).label("stable"),
            Prop(rendered.indexOf(firstnode) < rendered.indexOf(secondnode)).label("node-order"),
            Prop(rendered.contains("sources <code>" + sources.mkString(",") + "</code>")).label("source-order"),
            Prop(firstreport.contains("documentIdentity: " + validated.documentIdentity)).label("document-identity"),
            Prop(firstreport.contains("catalogIdentity: " + validated.catalogIdentity)).label("catalog-identity"),
            Prop(prohibitedtokens.forall(token => !rendered.toLowerCase.contains(token))).label("prohibited-integration-token:" + matchedtokens.mkString(","))
          ): _*)
        })

        Then("the generated cases retain source order and output provenance without acquiring physical-rendering semantics")
        withClue(check.status.toString) {
          check.passed shouldBe true
        }
        check.succeeded should be >= 30
      }
    }
  }

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
