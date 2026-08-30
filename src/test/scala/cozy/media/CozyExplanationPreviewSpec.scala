package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest

import scala.collection.JavaConverters._

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

import cozy.CozySpecVocabulary

/*
 * @since   Aug. 30, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyExplanationPreviewSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Explanation Preview" should {
    "render a current multi-step and multi-page closure with byte-stable HTML and an exact deterministic receipt" in {
      _with_work("current") { root =>
        Given("one current direct-file Explanation Plan, Projection Map, catalogs, resource bindings, and three mapped Visual Pages")
        val fixture = _fixture(root)
        val first = root.resolve("first.html")
        val second = root.resolve("second.html")

        When("the explanation Preview is requested once with separated options and once with equivalent equals-form options")
        val firstreport = CozyExplanation.execute(_preview_command(fixture, first))
        val secondreport = CozyExplanation.execute(_preview_command(fixture, second, equals = true))
        val html = Files.readString(first, StandardCharsets.UTF_8)
        val receiptlines = firstreport.split("\\n").toVector
        val optionforms = Gen.oneOf(true, false)
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(optionforms) { equals =>
          val propertyoutput = root.resolve("property.html")
          val report = CozyExplanation.execute(_preview_command(fixture, propertyoutput, equals = equals))
          report == firstreport && Files.readAllBytes(propertyoutput).toVector == Files.readAllBytes(first).toVector
        })

        Then("the static HTML retains ordered Step-to-Page semantics and both invocations produce identical evidence")
        Files.readAllBytes(first).toVector shouldBe Files.readAllBytes(second).toVector
        firstreport shouldBe secondreport
        check.passed shouldBe true
        check.succeeded should be >= 30
        html.indexOf("data-step-id=\"problem-step\"") should be < html.indexOf("data-step-id=\"solution-step\"")
        html.indexOf("data-page-id=\"problem-page-a\"") should be < html.indexOf("data-page-id=\"problem-page-b\"")
        html should include_text("Static explanation flow")
        html should include_text("Typed Relations")
        html should include_text("Visual Pattern")
        html should include_text("cozy.explanation-preview.renderer.v1")
        html should include_text("data-visual-pattern=\"flow-horizontal\"")
        html should include_text("data-visual-pattern=\"flow-vertical\"")
        html should not include "htmlIdentity"
        html should not include "receiptIdentity"

        And("the receipt has its frozen LF field order and hashes only the preceding receipt lines")
        receiptlines.map(_.takeWhile(_ != ':')) shouldBe Vector(
          "schema",
          "version",
          "generator",
          "rendererProfile",
          "compositionIdentity",
          "planIdentity",
          "projectionMapIdentity",
          "explanationCatalogIdentity",
          "presentationCatalogIdentity",
          "visualPageSetIdentity",
          "htmlIdentity",
          "identity"
        )
        receiptlines.head shouldBe "schema: cozy.explanation-preview.v1"
        receiptlines(2) shouldBe "generator: cozy media explanation preview"
        receiptlines(3) shouldBe "rendererProfile: cozy.explanation-preview.renderer.v1"
        receiptlines.last shouldBe "identity: " + _sha256_text(receiptlines.dropRight(1).mkString("\n"))
      }
    }

    "render a current product-overview closure with ordered overview and page semantics" in {
      _with_work("product-overview") { root =>
        Given("one current direct-file product-overview Composition, Plan, Projection Map, catalogs, bindings, and mapped Visual Pages")
        val fixture = _product_overview_fixture(root)
        val output = root.resolve("product-overview.html")
        val composition = CozyExplanation.parseCompositionJson(Files.readString(fixture.compositionfile, StandardCharsets.UTF_8))
        val presentationcatalog = _presentation_catalog()
        val plan = CozyExplanation.parsePlanJson(Files.readString(fixture.planfile, StandardCharsets.UTF_8))

        When("the product-overview Explanation Preview is requested through its explicit direct-file command")
        val report = CozyExplanation.execute(_preview_command(fixture, output))
        val html = Files.readString(output, StandardCharsets.UTF_8)
        val receiptlines = report.split("\\n").toVector

        Then("the receipt binds the current product-overview identities and the static HTML preserves authored role and page order")
        plan.explanationPattern shouldBe CozyExplanation.PatternReference("product-overview", 1)
        plan.steps.map(_.semanticRole) shouldBe Vector("vision", "goal", "context", "use-case", "main-scenario")
        fixture.projectionmap.presentation.stepMappings.map(_.stepId) shouldBe plan.steps.map(_.id)
        receiptlines.map(_.takeWhile(_ != ':')) shouldBe Vector(
          "schema",
          "version",
          "generator",
          "rendererProfile",
          "compositionIdentity",
          "planIdentity",
          "projectionMapIdentity",
          "explanationCatalogIdentity",
          "presentationCatalogIdentity",
          "visualPageSetIdentity",
          "htmlIdentity",
          "identity"
        )
        report should include_text("compositionIdentity: " + CozyExplanation.compositionIdentity(composition))
        report should include_text("planIdentity: " + plan.identity)
        report should include_text("projectionMapIdentity: " + fixture.projectionmap.identity)
        report should include_text("presentationCatalogIdentity: " + CozyVisualPage.catalogIdentity(presentationcatalog))
        report should include_text("visualPageSetIdentity: " + CozyVisualPage.visualPageSetIdentity(fixture.pageset, presentationcatalog))
        html should include_text("Explanation Pattern <code>product-overview@1</code>")
        html should include_text("vision is an authored product overview claim.")
        html should include_text("<code>sequence</code>")
        html should include_text("<code>vision-next</code>")
        html should include_text("<code>flow-horizontal</code>")
        html should include_text("data-visual-pattern=\"flow-horizontal\"")
        html should include_text("data-visual-pattern=\"flow-vertical\"")
        html should include_text("data-visual-pattern=\"mapping-columns\"")
        html should include_text("Source concepts")
        html should include_text("Target outcomes")
        html.indexOf("data-step-id=\"vision-step\"") should be < html.indexOf("data-step-id=\"goal-step\"")
        html.indexOf("data-step-id=\"goal-step\"") should be < html.indexOf("data-step-id=\"context-step\"")
        html.indexOf("data-step-id=\"context-step\"") should be < html.indexOf("data-step-id=\"use-case-step\"")
        html.indexOf("data-step-id=\"use-case-step\"") should be < html.indexOf("data-step-id=\"main-scenario-step\"")
        html.indexOf("data-page-id=\"page-vision-a\"") should be < html.indexOf("data-page-id=\"page-vision-b\"")
      }
    }

    "fail before replacing the selected output when a mapped Page is unknown or no longer agrees with its Plan Step" in {
      _with_work("preservation") { root =>
        Given("a current explanation closure, sentinel output bytes, and unknown and stale Step-to-Page variants")
        val fixture = _fixture(root)
        val output = _write(root.resolve("preserve.html"), "preserve-me")
        val unknownpresentation = fixture.projectionmap.presentation.copy(
          stepMappings = fixture.projectionmap.presentation.stepMappings.updated(0,
            fixture.projectionmap.presentation.stepMappings.head.copy(pageIds = Vector("unknown-page")))
        )
        val unknownmap = _with_mapping_identities(fixture.projectionmap.copy(presentation = unknownpresentation))
        val unknownfile = _write(root.resolve("unknown-map.json"), CozyExplanationProjection.canonicalProjectionMapJson(unknownmap))
        val changedpage = fixture.pageset.pages.head.copy(logical = fixture.pageset.pages.head.logical.copy(
          nodes = fixture.pageset.pages.head.logical.nodes.updated(0,
            fixture.pageset.pages.head.logical.nodes.head.copy(label = "Changed logical value"))
        ))
        val stalefile = _write(root.resolve("stale-pages.json"), CozyVisualPage.canonicalJson(
          fixture.pageset.copy(pages = fixture.pageset.pages.updated(0, changedpage))
        ))

        When("Preview validates the unknown mapping and the stale PageSet before it writes review evidence")
        val unknownfailure = _failure(CozyExplanation.execute(_preview_command(fixture, output, projectionmap = Some(unknownfile))))
        val stalefailure = _failure(CozyExplanation.execute(_preview_command(fixture, output, visualpageset = Some(stalefile))))

        Then("both failures are structured currentness failures and the pre-existing output remains byte-for-byte unchanged")
        Vector(unknownfailure, stalefailure).foreach { failure =>
          failure.getMessage should include_text("EXPLANATION_PREVIEW_STALE")
          failure.getMessage should include_text("path=")
          failure.getMessage should include_text("reason=")
        }
        unknownfailure.getMessage should include_text("unknown-page")
        stalefailure.getMessage should include_text("does not equal Plan step")
        Files.readString(output, StandardCharsets.UTF_8) shouldBe "preserve-me"
      }
    }

    "reject unsupported incomplete duplicated and positional Preview grammar deterministically" in {
      _with_work("grammar") { root =>
        Given("one otherwise complete direct-file explanation closure")
        val fixture = _fixture(root)
        val output = root.resolve("grammar.html")

        When("Preview receives unsupported, valueless, duplicated, and additional positional arguments")
        val failures = Vector(
          _failure(CozyExplanation.execute(List("preview", fixture.planfile.toString, "--composition", fixture.compositionfile.toString))),
          _failure(CozyExplanation.execute(_preview_command(fixture, output) :+ "--unsupported=value")),
          _failure(CozyExplanation.execute(_preview_command(fixture, output) :+ "--asset")),
          _failure(CozyExplanation.execute(_preview_command(fixture, output) ++ List("--save", root.resolve("other.html").toString))),
          _failure(CozyExplanation.execute(_preview_command(fixture, output) :+ "unexpected"))
        )

        Then("each grammar failure reports the dedicated command taxonomy without replacing an output")
        failures.foreach(_.getMessage should include_text("EXPLANATION_PREVIEW_COMMAND"))
        Files.exists(output, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "leave the established single-command Visual Page Preview contract callable" in {
      _with_work("visual-page-compatibility") { root =>
        Given("one valid ordered Visual Page Set and its presentation catalog")
        val fixture = _fixture(root)
        val output = root.resolve("visual-page-preview.html")

        When("the existing visual-page Preview command is invoked with its unchanged grammar")
        val report = CozyVisualPage.execute(List(
          "preview",
          fixture.visualpagesetfile.toString,
          "--catalog",
          fixture.presentationcatalogfile.toString,
          "--save",
          output.toString
        ))

        Then("its established receipt schema and deterministic semantic HTML remain available")
        report.split("\\n").map(_.takeWhile(_ != ':')).toVector shouldBe Vector(
          "schema",
          "version",
          "generator",
          "documentIdentity",
          "catalogIdentity",
          "htmlIdentity",
          "previewIdentity",
          "status"
        )
        report should include_text("schema: cozy.visual-page.preview.v1")
        Files.readString(output, StandardCharsets.UTF_8) should include_text("Cozy Visual Page Semantic Preview")
      }
    }
  }

  private final case class Fixture(
    catalogfile: Path,
    presentationcatalogfile: Path,
    compositionfile: Path,
    planfile: Path,
    mapfile: Path,
    visualpagesetfile: Path,
    bindings: CozyExplanation.ResourceBindings,
    pageset: CozyVisualPage.PageSet,
    projectionmap: CozyExplanationProjection.ProjectionMap
  )

  private def _fixture(root: Path): Fixture = {
    val catalog = _catalog()
    val presentationcatalog = _presentation_catalog()
    val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(catalog))
    val presentationcatalogfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(presentationcatalog))
    val sourcefile = _write(root.resolve("sources/source.txt"), "source bytes")
    val assetfile = _write(root.resolve("assets/asset.txt"), "asset bytes")
    val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))
    val composition = _composition(catalog, _sha256_file(sourcefile), _sha256_file(assetfile))
    val compositionfile = _write(root.resolve("composition.json"), CozyExplanation.canonicalCompositionJson(composition))
    val plan = CozyExplanation.expand(CozyExplanation.loadComposition(compositionfile, catalogfile, presentationcatalogfile, bindings))
    val planfile = _write(root.resolve("plan.json"), CozyExplanation.canonicalPlanJson(plan))
    val pageset = _pages(plan, presentationcatalog, _sha256_file(assetfile))
    val visualpagesetfile = _write(root.resolve("visual-pages.json"), CozyVisualPage.canonicalJson(pageset))
    val projectionmap = _projection_map(plan)
    val mapfile = _write(root.resolve("projection-map.json"), CozyExplanationProjection.canonicalProjectionMapJson(projectionmap))
    Fixture(catalogfile, presentationcatalogfile, compositionfile, planfile, mapfile, visualpagesetfile, bindings, pageset, projectionmap)
  }

  private def _product_overview_fixture(root: Path): Fixture = {
    val catalog = _catalog()
    val presentationcatalog = _presentation_catalog()
    val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(catalog))
    val presentationcatalogfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(presentationcatalog))
    val sourcefile = _write(root.resolve("sources/source.txt"), "product overview source bytes")
    val assetfile = _write(root.resolve("assets/asset.txt"), "product overview asset bytes")
    val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))
    val composition = _product_overview_composition(catalog, _sha256_file(sourcefile), _sha256_file(assetfile))
    val compositionfile = _write(root.resolve("composition.json"), CozyExplanation.canonicalCompositionJson(composition))
    val plan = CozyExplanation.expand(CozyExplanation.loadComposition(compositionfile, catalogfile, presentationcatalogfile, bindings))
    val planfile = _write(root.resolve("plan.json"), CozyExplanation.canonicalPlanJson(plan))
    val pageset = _product_overview_pages(plan, presentationcatalog, _sha256_file(assetfile))
    val visualpagesetfile = _write(root.resolve("visual-pages.json"), CozyVisualPage.canonicalJson(pageset))
    val projectionmap = _product_overview_projection_map(plan)
    val mapfile = _write(root.resolve("projection-map.json"), CozyExplanationProjection.canonicalProjectionMapJson(projectionmap))
    Fixture(catalogfile, presentationcatalogfile, compositionfile, planfile, mapfile, visualpagesetfile, bindings, pageset, projectionmap)
  }

  private def _product_overview_composition(
    catalog: CozyExplanation.Catalog,
    sourcedigest: String,
    assetdigest: String
  ): CozyExplanation.Composition = {
    val sources = Vector("source")
    val assets = Vector("asset")
    val facts = Vector(
      CozyExplanation.Fact("name", CozyExplanation.JsonString("Cozy"), sources, assets),
      CozyExplanation.Fact("vision", CozyExplanation.JsonString("Make explanation linkage explicit"), sources, assets),
      CozyExplanation.Fact("goals", CozyExplanation.JsonArray(Vector(_labeled("goal-1", "Make plans trustworthy", sources, assets))), sources, assets),
      CozyExplanation.Fact("context", CozyExplanation.JsonString("A direct-file explanation workflow"), sources, assets),
      CozyExplanation.Fact("useCases", CozyExplanation.JsonArray(Vector(_labeled("use-case-1", "Explain a product", sources, assets))), sources, assets),
      CozyExplanation.Fact("mainScenario", CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString("scenario-1"),
        "label" -> CozyExplanation.JsonString("Project explicit steps"),
        "steps" -> CozyExplanation.JsonArray(Vector(_labeled("scenario-step-1", "Validate links", sources, assets)))
      )), sources, assets),
      CozyExplanation.Fact("mechanisms", CozyExplanation.JsonArray(Vector(_labeled("mechanism-1", "Strict validation", sources, assets))), sources, assets)
    )
    val roles = Vector("vision", "goal", "context", "use-case", "main-scenario")
    val steps = roles.zipWithIndex.map { case (role, index) =>
      CozyExplanation.CompositionStep(
        s"$role-step", index + 1, role,
        Vector(CozyExplanation.Claim(s"$role-claim", s"$role is an authored product overview claim.", if (index == 0) "primary" else "supporting", sources, assets)),
        _product_overview_logical(role, sources), sources, assets, Vector.empty
      )
    }
    CozyExplanation.Composition(
      "product-overview-composition",
      CozyExplanation.CatalogSelector(catalog.id, catalog.revision, CozyExplanation.catalogIdentity(catalog)),
      CozyExplanation.Subject(CozyExplanation.PatternReference("software-product", 1), facts),
      CozyExplanation.Explanation(CozyExplanation.PatternReference("product-overview", 1), Vector.empty, steps),
      Vector(CozyExplanation.SourceDeclaration("source", sourcedigest)),
      Vector(CozyExplanation.AssetDeclaration("asset", "text/plain", assetdigest))
    )
  }

  private def _product_overview_logical(role: String, sources: Vector[String]): CozyVisualPage.Logical = CozyVisualPage.Logical(
    if (role == "main-scenario") "mapping" else "sequence",
    if (role == "main-scenario") Vector(
      CozyVisualPage.Node(s"$role-source", "source", s"$role source", sources),
      CozyVisualPage.Node(s"$role-target", "target", s"$role target", sources)
    ) else Vector(
      CozyVisualPage.Node(s"$role-start", "step", s"$role start", sources),
      CozyVisualPage.Node(s"$role-end", "step", s"$role end", sources)
    ),
    if (role == "main-scenario") Vector(
      CozyVisualPage.Relation(s"$role-maps-to", "maps-to", s"$role-source", s"$role-target", sources)
    ) else Vector(CozyVisualPage.Relation(s"$role-next", "next", s"$role-start", s"$role-end", sources))
  )

  private def _product_overview_pages(
    plan: CozyExplanation.Plan,
    catalog: CozyVisualPage.Catalog,
    assetdigest: String
  ): CozyVisualPage.PageSet = {
    val primary = plan.steps.map { step =>
      val visualpattern = if (step.semanticRole == "main-scenario") "mapping-columns" else "flow-horizontal"
      _product_overview_page(s"page-${step.semanticRole}-a", step.logical, catalog, assetdigest, visualpattern)
    }
    val secondary = _product_overview_page("page-vision-b", plan.steps.head.logical, catalog, assetdigest, "flow-vertical")
    CozyVisualPage.PageSet("product-overview-pages", primary :+ secondary)
  }

  private def _product_overview_page(
    id: String,
    logical: CozyVisualPage.Logical,
    catalog: CozyVisualPage.Catalog,
    assetdigest: String,
    visualpattern: String = "flow-horizontal"
  ): CozyVisualPage.Page = CozyVisualPage.Page(
    id,
    "product-overview",
    "en",
    CozyVisualPage.CatalogReference(catalog.id, catalog.revision),
    logical,
    CozyVisualPage.Visual(visualpattern, if (visualpattern == "mapping-columns") Vector(
      CozyVisualPage.VisualParameter("sourceColumnTitle", CozyVisualPage.StringParameter("Source concepts")),
      CozyVisualPage.VisualParameter("targetColumnTitle", CozyVisualPage.StringParameter("Target outcomes"))
    ) else Vector.empty),
    Vector(CozyVisualPage.Asset("asset", "assets/asset.txt", "text/plain", assetdigest)),
    Vector(CozyVisualPage.SourceBinding("source", "sources/source.txt"))
  )

  private def _product_overview_projection_map(plan: CozyExplanation.Plan): CozyExplanationProjection.ProjectionMap = {
    val pagemappings = plan.steps.map { step =>
      val pageids = if (step.id == "vision-step") Vector("page-vision-a", "page-vision-b") else Vector(s"page-${step.semanticRole}-a")
      CozyExplanationProjection.PresentationStepMapping(step.id, pageids)
    }
    val scenemappings = plan.steps.zipWithIndex.map { case (step, index) =>
      CozyExplanationProjection.VideoStepMapping(step.id, Vector(s"scene-${index + 1}"))
    }
    val presentation = CozyExplanationProjection.PresentationMapping(
      pagemappings,
      CozyExplanationProjection.presentationMappingIdentity(pagemappings)
    )
    val video = CozyExplanationProjection.VideoMapping(
      scenemappings,
      CozyExplanationProjection.videoMappingIdentity(scenemappings)
    )
    _with_map_identity(CozyExplanationProjection.ProjectionMap(
      plan.compositionIdentity, plan.identity, plan.explanationCatalog, plan.presentationCatalog, presentation, video, ""
    ))
  }

  private def _catalog(): CozyExplanation.Catalog = CozyExplanation.Catalog(
    "explanation", 1,
    Vector(CozyExplanation.SubjectPattern("software-product", 1, Vector(
      CozyExplanation.Definition("context", "text", required = true),
      CozyExplanation.Definition("goals", "goal-list", required = true),
      CozyExplanation.Definition("mainScenario", "scenario", required = true),
      CozyExplanation.Definition("mechanisms", "mechanism-list", required = true),
      CozyExplanation.Definition("name", "text", required = true),
      CozyExplanation.Definition("useCases", "use-case-list", required = true),
      CozyExplanation.Definition("vision", "text", required = true)
    ))),
    Vector(CozyExplanation.ExplanationPattern(
      "problem-solution", 1,
      Vector(CozyExplanation.PatternReference("software-product", 1)),
      Vector(
        CozyExplanation.Definition("problem", "text", required = true),
        CozyExplanation.Definition("solution", "text", required = true)
      ),
      Vector(CozyExplanation.Role("problem", 1, required = true), CozyExplanation.Role("solution", 2, required = true))
    ), CozyExplanation.ExplanationPattern(
      "product-mechanism", 1,
      Vector(CozyExplanation.PatternReference("software-product", 1)),
      Vector(CozyExplanation.Definition("mechanismLinks", "mechanism-link-list", required = true)),
      Vector(CozyExplanation.Role("mechanism", 1, required = true))
    ), CozyExplanation.ExplanationPattern(
      "product-overview", 1,
      Vector(CozyExplanation.PatternReference("software-product", 1)),
      Vector.empty,
      Vector(
        CozyExplanation.Role("vision", 1, required = true),
        CozyExplanation.Role("goal", 2, required = true),
        CozyExplanation.Role("context", 3, required = true),
        CozyExplanation.Role("use-case", 4, required = true),
        CozyExplanation.Role("main-scenario", 5, required = true)
      )
    )),
    CozyExplanation._reserved_ids
  )

  private def _presentation_catalog(): CozyVisualPage.Catalog = CozyVisualPage.Catalog(
    "presentation", 1,
    Vector(
      CozyVisualPage.RelationDefinition("next", "from-to"),
      CozyVisualPage.RelationDefinition("causes", "from-to"),
      CozyVisualPage.RelationDefinition("depends-on", "from-to"),
      CozyVisualPage.RelationDefinition("enables", "from-to"),
      CozyVisualPage.RelationDefinition("maps-to", "from-to")
    ),
    Vector(
      CozyVisualPage.LogicalPattern("sequence", Vector(CozyVisualPage.NodeRole("step", 2, 8)), Vector(
        CozyVisualPage.RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear")
      )),
      CozyVisualPage.LogicalPattern("causal-chain", Vector(CozyVisualPage.NodeRole("cause", 1, 7), CozyVisualPage.NodeRole("effect", 1, 7)), Vector(
        CozyVisualPage.RelationRule("causes", Vector("cause"), Vector("effect"), 1, 16, "acyclic"),
        CozyVisualPage.RelationRule("enables", Vector("cause"), Vector("effect"), 1, 16, "acyclic")
      )),
      CozyVisualPage.LogicalPattern("dependency-map", Vector(CozyVisualPage.NodeRole("dependency", 1, 7), CozyVisualPage.NodeRole("dependent", 1, 7)), Vector(
        CozyVisualPage.RelationRule("depends-on", Vector("dependent"), Vector("dependency"), 1, 16, "acyclic")
      )),
      CozyVisualPage.LogicalPattern("mapping", Vector(CozyVisualPage.NodeRole("source", 1, 7), CozyVisualPage.NodeRole("target", 1, 7)), Vector(
        CozyVisualPage.RelationRule("maps-to", Vector("source"), Vector("target"), 1, 16, "bipartite")
      ))
    ),
    Vector(
      CozyVisualPage.VisualPattern("flow-horizontal", Vector("causal-chain", "sequence"), Vector(
        CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", required = false),
        CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", required = false)
      )),
      CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(
        CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", required = false),
        CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", required = false)
      )),
      CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(
        CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", required = false),
        CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", required = true),
        CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", required = true)
      ))
    )
  )

  private def _composition(catalog: CozyExplanation.Catalog, sourcedigest: String, assetdigest: String): CozyExplanation.Composition = {
    val sources = Vector("source")
    val assets = Vector("asset")
    val facts = Vector(
      CozyExplanation.Fact("context", CozyExplanation.JsonString("An explicit explanation review workflow"), sources, assets),
      CozyExplanation.Fact("goals", CozyExplanation.JsonArray(Vector(_labeled("goal-1", "Review explainable plans", sources, assets))), sources, assets),
      CozyExplanation.Fact("mainScenario", CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString("scenario-1"),
        "label" -> CozyExplanation.JsonString("Review one explanation"),
        "steps" -> CozyExplanation.JsonArray(Vector(_labeled("scenario-step-1", "Check mappings", sources, assets)))
      )), sources, assets),
      CozyExplanation.Fact("mechanisms", CozyExplanation.JsonArray(Vector(_labeled("mechanism-1", "Currentness validation", sources, assets))), sources, assets),
      CozyExplanation.Fact("name", CozyExplanation.JsonString("Cozy"), sources, assets),
      CozyExplanation.Fact("useCases", CozyExplanation.JsonArray(Vector(_labeled("use-case-1", "Inspect a plan", sources, assets))), sources, assets),
      CozyExplanation.Fact("vision", CozyExplanation.JsonString("Make mappings reviewable"), sources, assets)
    )
    val logical = _logical(sources)
    val steps = Vector(
      CozyExplanation.CompositionStep(
        "problem-step", 1, "problem",
        Vector(CozyExplanation.Claim("problem-claim", "The problem is explicitly authored.", "primary", sources, assets)),
        logical, sources, assets, Vector(CozyExplanation.ParameterSelection("problem"))
      ),
      CozyExplanation.CompositionStep(
        "solution-step", 2, "solution",
        Vector(CozyExplanation.Claim("solution-claim", "The solution is explicitly authored.", "supporting", sources, assets)),
        logical, sources, assets, Vector(CozyExplanation.ParameterSelection("solution"))
      )
    )
    CozyExplanation.Composition(
      "problem-solution-composition",
      CozyExplanation.CatalogSelector(catalog.id, catalog.revision, CozyExplanation.catalogIdentity(catalog)),
      CozyExplanation.Subject(CozyExplanation.PatternReference("software-product", 1), facts),
      CozyExplanation.Explanation(
        CozyExplanation.PatternReference("problem-solution", 1),
        Vector(
          CozyExplanation.Parameter("problem", CozyExplanation.JsonString("Mappings are not visible")),
          CozyExplanation.Parameter("solution", CozyExplanation.JsonString("Render a static review"))
        ),
        steps
      ),
      Vector(CozyExplanation.SourceDeclaration("source", sourcedigest)),
      Vector(CozyExplanation.AssetDeclaration("asset", "text/plain", assetdigest))
    )
  }

  private def _logical(sources: Vector[String]): CozyVisualPage.Logical = CozyVisualPage.Logical(
    "causal-chain",
    Vector(
      CozyVisualPage.Node("cause", "cause", "Problem", sources),
      CozyVisualPage.Node("effect", "effect", "Solution", sources)
    ),
    Vector(
      CozyVisualPage.Relation("causes", "causes", "cause", "effect", sources),
      CozyVisualPage.Relation("enables", "enables", "cause", "effect", sources)
    )
  )

  private def _pages(plan: CozyExplanation.Plan, catalog: CozyVisualPage.Catalog, assetdigest: String): CozyVisualPage.PageSet = {
    val logical = plan.steps.head.logical
    CozyVisualPage.PageSet("review-pages", Vector(
      _page("problem-page-a", logical, catalog, assetdigest, "flow-horizontal"),
      _page("problem-page-b", logical, catalog, assetdigest, "flow-vertical"),
      _page("solution-page", logical, catalog, assetdigest, "flow-horizontal")
    ))
  }

  private def _page(
    id: String,
    logical: CozyVisualPage.Logical,
    catalog: CozyVisualPage.Catalog,
    assetdigest: String,
    visualpattern: String
  ): CozyVisualPage.Page = CozyVisualPage.Page(
    id,
    "explanation-review",
    "en",
    CozyVisualPage.CatalogReference(catalog.id, catalog.revision),
    logical,
    CozyVisualPage.Visual(visualpattern, Vector.empty),
    Vector(CozyVisualPage.Asset("asset", "assets/asset.txt", "text/plain", assetdigest)),
    Vector(CozyVisualPage.SourceBinding("source", "sources/source.txt"))
  )

  private def _projection_map(plan: CozyExplanation.Plan): CozyExplanationProjection.ProjectionMap = {
    val presentationsteps = Vector(
      CozyExplanationProjection.PresentationStepMapping("problem-step", Vector("problem-page-a", "problem-page-b")),
      CozyExplanationProjection.PresentationStepMapping("solution-step", Vector("solution-page"))
    )
    val videosteps = Vector(
      CozyExplanationProjection.VideoStepMapping("problem-step", Vector("problem-scene")),
      CozyExplanationProjection.VideoStepMapping("solution-step", Vector("solution-scene"))
    )
    val presentation = CozyExplanationProjection.PresentationMapping(
      presentationsteps,
      CozyExplanationProjection.presentationMappingIdentity(presentationsteps)
    )
    val video = CozyExplanationProjection.VideoMapping(
      videosteps,
      CozyExplanationProjection.videoMappingIdentity(videosteps)
    )
    _with_map_identity(CozyExplanationProjection.ProjectionMap(
      plan.compositionIdentity, plan.identity, plan.explanationCatalog, plan.presentationCatalog, presentation, video, ""
    ))
  }

  private def _preview_command(
    fixture: Fixture,
    output: Path,
    equals: Boolean = false,
    projectionmap: Option[Path] = None,
    visualpageset: Option[Path] = None
  ): List[String] = {
    val mapfile = projectionmap.getOrElse(fixture.mapfile)
    val pagesfile = visualpageset.getOrElse(fixture.visualpagesetfile)
    if (equals) List(
      "preview", fixture.planfile.toString,
      "--composition=" + fixture.compositionfile,
      "--projection-map=" + mapfile,
      "--explanation-catalog=" + fixture.catalogfile,
      "--presentation-catalog=" + fixture.presentationcatalogfile,
      "--visual-page-set=" + pagesfile,
      "--save=" + output,
      "--source=source=" + fixture.bindings.sources("source"),
      "--asset=asset=" + fixture.bindings.assets("asset")
    ) else List(
      "preview", fixture.planfile.toString,
      "--composition", fixture.compositionfile.toString,
      "--projection-map", mapfile.toString,
      "--explanation-catalog", fixture.catalogfile.toString,
      "--presentation-catalog", fixture.presentationcatalogfile.toString,
      "--visual-page-set", pagesfile.toString,
      "--save", output.toString,
      "--source", "source=" + fixture.bindings.sources("source"),
      "--asset", "asset=" + fixture.bindings.assets("asset")
    )
  }

  private def _with_map_identity(value: CozyExplanationProjection.ProjectionMap): CozyExplanationProjection.ProjectionMap =
    value.copy(identity = CozyExplanationProjection.projectionMapIdentity(value))

  private def _with_mapping_identities(value: CozyExplanationProjection.ProjectionMap): CozyExplanationProjection.ProjectionMap = {
    val presentation = value.presentation.copy(identity = CozyExplanationProjection.presentationMappingIdentity(value.presentation.stepMappings))
    val video = value.video.copy(identity = CozyExplanationProjection.videoMappingIdentity(value.video.stepMappings))
    _with_map_identity(value.copy(presentation = presentation, video = video))
  }

  private def _labeled(
    id: String,
    label: String,
    sources: Vector[String],
    assets: Vector[String]
  ): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "id" -> CozyExplanation.JsonString(id),
    "label" -> CozyExplanation.JsonString(label),
    "sourceRefs" -> CozyExplanation.JsonArray(sources.map(CozyExplanation.JsonString)),
    "assetRefs" -> CozyExplanation.JsonArray(assets.map(CozyExplanation.JsonString))
  ))

  private def _failure[A](body: => A): RuntimeException = intercept[RuntimeException](body)

  private def _write(path: Path, value: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _sha256_file(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(byte => f"${byte & 0xff}%02x").mkString

  private def _sha256_text(value: String): String =
    _sha256_bytes(value.getBytes(StandardCharsets.UTF_8))

  private def _sha256_bytes(bytes: Array[Byte]): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _with_work[A](name: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-explanation-preview-" + name + "-")
    try body(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) Files.list(path).iterator().asScala.foreach(_delete_tree)
      Files.deleteIfExists(path)
    }
}
