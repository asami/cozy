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
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyExplanationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Explanation catalog" should {
    "normalize equivalent JSON key-order variants into one canonical catalog identity" in {
      Given("the complete accepted v1 catalog represented by generated root-key orders")
      val canonical = CozyExplanation.parseCatalogJson(_catalog_json())
      val keys = Vector("schema", "version", "id", "revision", "subjectPatterns", "explanationPatterns", "reservedNarrativeArgumentIds")
      val orders = Gen.chooseNum(0, 29).map(index => _key_order(keys, index))

      When("ScalaCheck strictly parses at least thirty admissible JSON representations")
      val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(orders) { order =>
        val parsed = CozyExplanation.parseCatalogJson(_catalog_json(order))
        CozyExplanation.canonicalCatalogJson(parsed) == CozyExplanation.canonicalCatalogJson(canonical) &&
          CozyExplanation.catalogIdentity(parsed) == CozyExplanation.catalogIdentity(canonical)
      })

      Then("input key order is not retained, while canonical catalog bytes and SHA-256 identity converge")
      check.passed shouldBe true
      check.succeeded should be >= 30
      CozyExplanation.canonicalCatalogJson(canonical) should include_text("\"subjectPatterns\"")
      CozyExplanation.catalogIdentity(canonical) should startWith("sha256:")
    }
  }

  "Cozy Explanation Composition" should {
    "validate and expand a complete explicit software-product Composition without semantic inference" in {
      _with_work("expand") { root =>
        Given("named catalog files, declared direct source and asset bytes, and one authored product-mechanism Composition")
        val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(_catalog))
        val presentationfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(_presentation_catalog))
        val sourcefile = _write(root.resolve("sources/source.txt"), "source bytes")
        val assetfile = _write(root.resolve("assets/asset.txt"), "asset bytes")
        val composition = _composition(_sha256(sourcefile), _sha256(assetfile))
        val compositionfile = _write(root.resolve("composition.json"), CozyExplanation.canonicalCompositionJson(composition))
        val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))

        When("the Composition is loaded against both explicit catalogs and deterministically expanded twice")
        val loaded = CozyExplanation.loadComposition(compositionfile, catalogfile, presentationfile, bindings)
        val first = CozyExplanation.expand(loaded)
        val second = CozyExplanation.expand(loaded)

        Then("the Plan preserves authored role, claim, P36 Logical Pattern/Relation graph, parameter provenance, and P36 logical catalog identity")
        first shouldBe second
        first.steps.map(_.semanticRole) shouldBe Vector("mechanism")
        first.steps.map(_.claims) shouldBe composition.explanation.steps.map(_.claims)
        first.steps.map(_.logical) shouldBe composition.explanation.steps.map(_.logical)
        first.steps.head.parameterProvenance.values shouldBe loaded.composition.explanation.parameters
        first.presentationCatalog.identity shouldBe CozyVisualPage.logicalCatalogIdentity(_presentation_catalog)
        first.compositionIdentity shouldBe CozyExplanation.compositionIdentity(composition)
        CozyExplanation.canonicalPlanJson(first) should not include ("\"pageId\"")
        CozyExplanation.canonicalPlanJson(first) should not include ("\"sceneId\"")
        CozyExplanation.canonicalPlanJson(first) should not include ("\"timing\"")
        CozyExplanation.canonicalPlanJson(first) should not include ("\"layout\"")
      }
    }

    "load and deterministically expand direct-file problem-solution roles with authored parameter provenance" in {
      _with_work("problem-solution") { root =>
        Given("named catalogs, explicit source and asset bindings, and an authored problem-solution Composition")
        val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(_catalog))
        val presentationfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(_presentation_catalog))
        val sourcefile = _write(root.resolve("sources/source.txt"), "problem-solution source bytes")
        val assetfile = _write(root.resolve("assets/asset.txt"), "problem-solution asset bytes")
        val composition = _problem_solution_composition(_sha256(sourcefile), _sha256(assetfile))
        val compositionfile = _write(root.resolve("problem-solution-composition.json"), CozyExplanation.canonicalCompositionJson(composition))
        val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))

        When("the direct-file Composition is loaded and expanded twice against the named catalogs")
        val loaded = CozyExplanation.loadComposition(compositionfile, catalogfile, presentationfile, bindings)
        val first = CozyExplanation.expand(loaded)
        val second = CozyExplanation.expand(loaded)

        Then("the ordered problem and solution steps preserve selected parameter values, authored logical data, and canonical identities")
        first shouldBe second
        loaded.composition shouldBe composition
        first.steps.map(_.semanticRole) shouldBe Vector("problem", "solution")
        first.steps.map(_.parameterProvenance.values.map(value => value.name -> value.value)) shouldBe Vector(
          Vector("problem" -> CozyExplanation.JsonString("Direct file provenance was previously implicit.")),
          Vector("solution" -> CozyExplanation.JsonString("Bind every declaration to explicit direct bytes."))
        )
        first.steps.map(_.logical) shouldBe composition.explanation.steps.map(_.logical)
        first.compositionIdentity shouldBe CozyExplanation.compositionIdentity(composition)
        CozyExplanation.canonicalPlanJson(first) shouldBe CozyExplanation.canonicalPlanJson(second)
        first.identity shouldBe CozyExplanation.planIdentity(first)
      }
    }

    "canonicalize recursively reordered dynamic values while preserving authored array order" in {
      Given("an accepted Composition containing ordered lists, a scenario value, and mechanism-link parameters")
      val composition = _composition(Vector.fill(64)("a").mkString, Vector.fill(64)("b").mkString)
      val originaljson = _composition_json_value(composition)
      val original = CozyExplanation.parseCompositionJson(_json_text(originaljson))
      val reorderedjson = _reorder_json_value(originaljson)

      When("the recursively reordered JSON object fields are parsed as an equivalent Composition")
      val reordered = CozyExplanation.parseCompositionJson(_json_text(reorderedjson))
      val canonicaljson = CozyExplanation.canonicalCompositionJson(reordered)

      Then("canonical JSON and Composition identity ignore nested object-key order while authored arrays stay ordered")
      _json_text(reorderedjson) should not be _json_text(originaljson)
      canonicaljson shouldBe CozyExplanation.canonicalCompositionJson(original)
      CozyExplanation.compositionIdentity(reordered) shouldBe CozyExplanation.compositionIdentity(original)
      canonicaljson should include ("\"value\":[{\"id\":\"goal-1\",\"label\":\"Reliable plans\",\"sourceRefs\":[\"source\"],\"assetRefs\":[\"asset\"]}]")
      canonicaljson should include ("\"value\":{\"id\":\"scenario-1\",\"label\":\"Expand an explanation\",\"steps\":[{\"id\":\"scenario-step-1\",\"label\":\"Validate authored inputs\",\"sourceRefs\":[\"source\"],\"assetRefs\":[\"asset\"]}]}")
      canonicaljson should include ("\"value\":[{\"id\":\"link-1\",\"goalId\":\"goal-1\",\"useCaseId\":\"use-case-1\",\"mechanismId\":\"mechanism-1\",\"sourceRefs\":[\"source\"],\"assetRefs\":[\"asset\"]}]")
      reordered.subject.facts.map(_.name) shouldBe original.subject.facts.map(_.name)
      reordered.explanation.parameters.map(_.name) shouldBe original.explanation.parameters.map(_.name)
      reordered.subject.facts shouldBe original.subject.facts
      reordered.explanation.parameters shouldBe original.explanation.parameters
    }

    "reject companion, compatibility, logical-data, stale-byte, and incomplete-expansion failures with EXPLANATION diagnostics" in {
      _with_work("rejections") { root =>
        Given("one otherwise valid direct-file Composition closure and adversarial variants")
        val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(_catalog))
        val presentationfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(_presentation_catalog))
        val alteredpresentationfile = _write(root.resolve("altered-presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(
          _presentation_catalog.copy(relations = _presentation_catalog.relations.updated(0, CozyVisualPage.RelationDefinition("not-next", "from-to")))
        ))
        val sourcefile = _write(root.resolve("sources/source.txt"), "source bytes")
        val assetfile = _write(root.resolve("assets/asset.txt"), "asset bytes")
        val composition = _composition(_sha256(sourcefile), _sha256(assetfile))
        val compositionfile = _write(root.resolve("composition.json"), CozyExplanation.canonicalCompositionJson(composition))
        val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))
        val incompatible = _write(root.resolve("incompatible.json"), CozyExplanation.canonicalCompositionJson(
          composition.copy(explanation = composition.explanation.copy(pattern = CozyExplanation.PatternReference("product-mechanism", 2)))
        ))
        val malformedlogical = _write(root.resolve("malformed-logical.json"), CozyExplanation.canonicalCompositionJson(
          composition.copy(explanation = composition.explanation.copy(steps = composition.explanation.steps.map(step => step.copy(logical = step.logical.copy(pattern = "unknown"))))
        )))
        val incomplete = _write(root.resolve("incomplete.json"), CozyExplanation.canonicalCompositionJson(
          composition.copy(explanation = composition.explanation.copy(steps = Vector.empty))
        ))

        When("operations omit or duplicate direct companions, select an incompatible pattern, provide malformed P36 logical data, stale bytes, or omit authored expansion steps")
        val omission = _failure(CozyExplanation.execute(List("validate", compositionfile.toString, "--explanation-catalog", catalogfile.toString)))
        val duplicate = _failure(CozyExplanation.execute(List(
          "validate", compositionfile.toString,
          "--explanation-catalog", catalogfile.toString,
          "--presentation-catalog", presentationfile.toString,
          "--source", s"source=$sourcefile",
          "--source", s"source=$sourcefile",
          "--asset", s"asset=$assetfile"
        )))
        val incompatiblefailure = _failure(CozyExplanation.loadComposition(incompatible, catalogfile, presentationfile, bindings))
        val logicalfailure = _failure(CozyExplanation.loadComposition(malformedlogical, catalogfile, presentationfile, bindings))
        val incompletefailure = _failure(CozyExplanation.loadComposition(incomplete, catalogfile, presentationfile, bindings))
        val alteredpresentationfailure = _failure(CozyExplanation.loadComposition(compositionfile, catalogfile, alteredpresentationfile, bindings))
        _write(sourcefile, "changed source bytes")
        val stalefailure = _failure(CozyExplanation.loadComposition(compositionfile, catalogfile, presentationfile, bindings))

        Then("every rejected input reports a structured EXPLANATION code, field path, and concise reason without inference")
        Vector(omission, duplicate, incompatiblefailure, logicalfailure, incompletefailure, stalefailure).map(_.getMessage).forall(message =>
          message.contains("EXPLANATION_") && message.contains("path=") && message.contains("reason=")
        ) shouldBe true
        omission.getMessage should include_text("EXPLANATION_COMMAND_INVALID")
        duplicate.getMessage should include_text("EXPLANATION_REFERENCE_INVALID")
        incompatiblefailure.getMessage should include_text("EXPLANATION_CATALOG_MISMATCH")
        logicalfailure.getMessage should include_text("EXPLANATION_REFERENCE_INVALID")
        incompletefailure.getMessage should include_text("EXPLANATION_EXPANSION_INCOMPLETE")
        alteredpresentationfailure.getMessage should include_text("EXPLANATION_PRESENTATION_CATALOG_INVALID")
        stalefailure.getMessage should include_text("EXPLANATION_ASSET_STALE")
      }
    }
  }

  "Cozy Explanation Plan" should {
    "round-trip, validate, inspect, and convert a deterministic Plan without media or projection inference" in {
      _with_work("plan-round-trip") { root =>
        Given("one direct-file Composition closure and its deterministically expanded Plan")
        val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(_catalog))
        val presentationfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(_presentation_catalog))
        val sourcefile = _write(root.resolve("sources/source.txt"), "source bytes")
        val assetfile = _write(root.resolve("assets/asset.txt"), "asset bytes")
        val composition = _composition(_sha256(sourcefile), _sha256(assetfile))
        val compositionfile = _write(root.resolve("composition.json"), CozyExplanation.canonicalCompositionJson(composition))
        val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))
        val plan = CozyExplanation.expand(CozyExplanation.loadComposition(compositionfile, catalogfile, presentationfile, bindings))
        val planfile = _write(root.resolve("plan.json"), CozyExplanation.canonicalPlanJson(plan))
        val output = root.resolve("converted-plan.json")

        When("the Plan is loaded, inspected, validated, and atomically converted through the direct explanation command route")
        val loaded = CozyExplanation.loadPlan(planfile, compositionfile, catalogfile, presentationfile, bindings)
        val inspect = CozyExplanation.execute(List(
          "inspect", planfile.toString, "--composition", compositionfile.toString,
          "--explanation-catalog", catalogfile.toString, "--presentation-catalog", presentationfile.toString,
          "--source", s"source=$sourcefile", "--asset", s"asset=$assetfile"
        ))
        val validate = CozyExplanation.execute(List(
          "validate", planfile.toString, "--composition", compositionfile.toString,
          "--explanation-catalog", catalogfile.toString, "--presentation-catalog", presentationfile.toString,
          "--source", s"source=$sourcefile", "--asset", s"asset=$assetfile"
        ))
        val convert = CozyExplanation.execute(List(
          "convert", planfile.toString, "--composition", compositionfile.toString,
          "--explanation-catalog", catalogfile.toString, "--presentation-catalog", presentationfile.toString,
          "--source", s"source=$sourcefile", "--asset", s"asset=$assetfile", "--save", output.toString
        ))

        Then("the serialized Plan remains current and byte-deterministic, while command output reports schema and step identity without adding ProjectionMap or Projection fields")
        loaded.plan shouldBe plan
        Files.readString(output, StandardCharsets.UTF_8) shouldBe CozyExplanation.canonicalPlanJson(plan) + "\n"
        inspect should include_text("schema: cozy.explanation-plan.v1")
        inspect should include_text("step: 1:mechanism-step")
        validate should include_text("presentationLogicalCatalogIdentity")
        convert should include_text("identity: sha256:")
        CozyExplanation.canonicalPlanJson(plan) should not include ("projectionMap")
        CozyExplanation.canonicalPlanJson(plan) should not include ("visualPageSet")
        CozyExplanation.canonicalPlanJson(plan) should not include ("storyboard")
      }
    }
  }

  private val _catalog_keys = Vector("schema", "version", "id", "revision", "subjectPatterns", "explanationPatterns", "reservedNarrativeArgumentIds")

  private val _catalog: CozyExplanation.Catalog = CozyExplanation.parseCatalogJson(_catalog_json())

  private val _presentation_catalog: CozyVisualPage.Catalog = CozyVisualPage.Catalog(
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
        CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false),
        CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false)
      )),
      CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(
        CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false),
        CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false)
      )),
      CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(
        CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false),
        CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", true),
        CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", true)
      ))
    )
  )

  private def _catalog_json(order: Vector[String] = _catalog_keys): String = {
    val values = Map(
      "schema" -> "\"cozy.explanation.catalog.v1\"",
      "version" -> "1",
      "id" -> "\"software-explanation\"",
      "revision" -> "1",
      "subjectPatterns" -> "[{\"id\":\"software-product\",\"version\":1,\"factDefinitions\":[{\"name\":\"context\",\"type\":\"text\",\"required\":true},{\"name\":\"goals\",\"type\":\"goal-list\",\"required\":true},{\"name\":\"mainScenario\",\"type\":\"scenario\",\"required\":true},{\"name\":\"mechanisms\",\"type\":\"mechanism-list\",\"required\":true},{\"name\":\"name\",\"type\":\"text\",\"required\":true},{\"name\":\"useCases\",\"type\":\"use-case-list\",\"required\":true},{\"name\":\"vision\",\"type\":\"text\",\"required\":true}]}]",
      "explanationPatterns" -> "[{\"id\":\"problem-solution\",\"version\":1,\"compatibleSubjects\":[{\"id\":\"software-product\",\"version\":1}],\"parameterDefinitions\":[{\"name\":\"problem\",\"type\":\"text\",\"required\":true},{\"name\":\"solution\",\"type\":\"text\",\"required\":true}],\"roles\":[{\"id\":\"problem\",\"order\":1,\"required\":true},{\"id\":\"solution\",\"order\":2,\"required\":true}]},{\"id\":\"product-mechanism\",\"version\":1,\"compatibleSubjects\":[{\"id\":\"software-product\",\"version\":1}],\"parameterDefinitions\":[{\"name\":\"mechanismLinks\",\"type\":\"mechanism-link-list\",\"required\":true}],\"roles\":[{\"id\":\"mechanism\",\"order\":1,\"required\":true}]},{\"id\":\"product-overview\",\"version\":1,\"compatibleSubjects\":[{\"id\":\"software-product\",\"version\":1}],\"parameterDefinitions\":[],\"roles\":[{\"id\":\"vision\",\"order\":1,\"required\":true},{\"id\":\"goal\",\"order\":2,\"required\":true},{\"id\":\"context\",\"order\":3,\"required\":true},{\"id\":\"use-case\",\"order\":4,\"required\":true},{\"id\":\"main-scenario\",\"order\":5,\"required\":true}]}]",
      "reservedNarrativeArgumentIds" -> "[\"problem-solution\",\"problem-cause-solution\",\"current-target\",\"before-after\",\"challenge-approach-result\",\"observation-insight-implication\",\"fact-interpretation-action\",\"why-what-how\",\"input-process-output\",\"concept-example\",\"claim-evidence\",\"claim-reasons\",\"question-answer\",\"principle-mechanism-effect\",\"strategy-execution-outcome\",\"past-present-future\"]"
    )
    order.map(key => "\"" + key + "\":" + values(key)).mkString("{", ",", "}")
  }

  private def _composition(sourcedigest: String, assetdigest: String): CozyExplanation.Composition = {
    val source = Vector("source")
    val asset = Vector("asset")
    val facts = Vector(
      CozyExplanation.Fact("name", CozyExplanation.JsonString("Cozy"), source, asset),
      CozyExplanation.Fact("vision", CozyExplanation.JsonString("Make composition explicit"), source, asset),
      CozyExplanation.Fact("goals", CozyExplanation.JsonArray(Vector(_labeled("goal-1", "Reliable plans", source, asset))), source, asset),
      CozyExplanation.Fact("context", CozyExplanation.JsonString("Typed media workflow"), source, asset),
      CozyExplanation.Fact("useCases", CozyExplanation.JsonArray(Vector(_labeled("use-case-1", "Plan a product explanation", source, asset))), source, asset),
      CozyExplanation.Fact("mainScenario", CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString("scenario-1"),
        "label" -> CozyExplanation.JsonString("Expand an explanation"),
        "steps" -> CozyExplanation.JsonArray(Vector(_labeled("scenario-step-1", "Validate authored inputs", source, asset)))
      )), source, asset),
      CozyExplanation.Fact("mechanisms", CozyExplanation.JsonArray(Vector(_labeled("mechanism-1", "Strict validation", source, asset))), source, asset)
    )
    val links = CozyExplanation.JsonArray(Vector(CozyExplanation.JsonObject(Vector(
      "id" -> CozyExplanation.JsonString("link-1"),
      "goalId" -> CozyExplanation.JsonString("goal-1"),
      "useCaseId" -> CozyExplanation.JsonString("use-case-1"),
      "mechanismId" -> CozyExplanation.JsonString("mechanism-1"),
      "sourceRefs" -> CozyExplanation.JsonArray(source.map(CozyExplanation.JsonString)),
      "assetRefs" -> CozyExplanation.JsonArray(asset.map(CozyExplanation.JsonString))
    ))))
    val step = CozyExplanation.CompositionStep(
      "mechanism-step", 1, "mechanism",
      Vector(CozyExplanation.Claim("claim-1", "Strict validation connects the product goal to its use case.", "primary", source, asset)),
      _logical(source), source, asset, Vector(CozyExplanation.ParameterSelection("mechanismLinks"))
    )
    CozyExplanation.Composition(
      "composition-1",
      CozyExplanation.CatalogSelector(_catalog.id, _catalog.revision, CozyExplanation.catalogIdentity(_catalog)),
      CozyExplanation.Subject(CozyExplanation.PatternReference("software-product", 1), facts),
      CozyExplanation.Explanation(
        CozyExplanation.PatternReference("product-mechanism", 1),
        Vector(CozyExplanation.Parameter("mechanismLinks", links)),
        Vector(step)
      ),
      Vector(CozyExplanation.SourceDeclaration("source", sourcedigest)),
      Vector(CozyExplanation.AssetDeclaration("asset", "text/plain", assetdigest))
    )
  }

  private def _problem_solution_composition(sourcedigest: String, assetdigest: String): CozyExplanation.Composition = {
    val base = _composition(sourcedigest, assetdigest)
    val step = base.explanation.steps.head
    val problem = step.copy(
      id = "problem-step",
      order = 1,
      semanticRole = "problem",
      claims = step.claims.map(_.copy(id = "problem-claim", text = "Implicit provenance makes explanation resources unreliable.")),
      parameterSelection = Vector(CozyExplanation.ParameterSelection("problem"))
    )
    val solution = step.copy(
      id = "solution-step",
      order = 2,
      semanticRole = "solution",
      claims = step.claims.map(_.copy(id = "solution-claim", text = "Explicit bindings make explanation resources verifiable.")),
      parameterSelection = Vector(CozyExplanation.ParameterSelection("solution"))
    )
    base.copy(
      id = "problem-solution-composition",
      explanation = CozyExplanation.Explanation(
        CozyExplanation.PatternReference("problem-solution", 1),
        Vector(
          CozyExplanation.Parameter("problem", CozyExplanation.JsonString("Direct file provenance was previously implicit.")),
          CozyExplanation.Parameter("solution", CozyExplanation.JsonString("Bind every declaration to explicit direct bytes."))
        ),
        Vector(problem, solution)
      )
    )
  }

  private def _composition_json_value(composition: CozyExplanation.Composition): CozyExplanation.JsonValue = {
    val catalogselector = CozyExplanation.JsonObject(Vector(
      "id" -> CozyExplanation.JsonString(composition.explanationCatalog.id),
      "revision" -> CozyExplanation.JsonNumber(composition.explanationCatalog.revision),
      "identity" -> CozyExplanation.JsonString(composition.explanationCatalog.identity)
    ))
    val subjectpattern = CozyExplanation.JsonObject(Vector(
      "id" -> CozyExplanation.JsonString(composition.subject.pattern.id),
      "version" -> CozyExplanation.JsonNumber(composition.subject.pattern.version)
    ))
    val facts = composition.subject.facts.map { fact =>
      CozyExplanation.JsonObject(Vector(
        "name" -> CozyExplanation.JsonString(fact.name),
        "value" -> fact.value,
        "sourceRefs" -> CozyExplanation.JsonArray(fact.sourceRefs.map(CozyExplanation.JsonString)),
        "assetRefs" -> CozyExplanation.JsonArray(fact.assetRefs.map(CozyExplanation.JsonString))
      ))
    }
    val explanationpattern = CozyExplanation.JsonObject(Vector(
      "id" -> CozyExplanation.JsonString(composition.explanation.pattern.id),
      "version" -> CozyExplanation.JsonNumber(composition.explanation.pattern.version)
    ))
    val parameters = composition.explanation.parameters.map { parameter =>
      CozyExplanation.JsonObject(Vector(
        "name" -> CozyExplanation.JsonString(parameter.name),
        "value" -> parameter.value
      ))
    }
    val steps = composition.explanation.steps.map { step =>
      val claims = step.claims.map { claim =>
        CozyExplanation.JsonObject(Vector(
          "id" -> CozyExplanation.JsonString(claim.id),
          "text" -> CozyExplanation.JsonString(claim.text),
          "emphasis" -> CozyExplanation.JsonString(claim.emphasis),
          "sourceRefs" -> CozyExplanation.JsonArray(claim.sourceRefs.map(CozyExplanation.JsonString)),
          "assetRefs" -> CozyExplanation.JsonArray(claim.assetRefs.map(CozyExplanation.JsonString))
        ))
      }
      val logical = CozyExplanation.JsonObject(Vector(
        "pattern" -> CozyExplanation.JsonString(step.logical.pattern),
        "nodes" -> CozyExplanation.JsonArray(step.logical.nodes.map { node =>
          CozyExplanation.JsonObject(Vector(
            "id" -> CozyExplanation.JsonString(node.id),
            "role" -> CozyExplanation.JsonString(node.role),
            "label" -> CozyExplanation.JsonString(node.label),
            "sourceRefs" -> CozyExplanation.JsonArray(node.sourceRefs.map(CozyExplanation.JsonString))
          ))
        }),
        "relations" -> CozyExplanation.JsonArray(step.logical.relations.map { relation =>
          CozyExplanation.JsonObject(Vector(
            "id" -> CozyExplanation.JsonString(relation.id),
            "type" -> CozyExplanation.JsonString(relation.relationType),
            "from" -> CozyExplanation.JsonString(relation.from),
            "to" -> CozyExplanation.JsonString(relation.to),
            "sourceRefs" -> CozyExplanation.JsonArray(relation.sourceRefs.map(CozyExplanation.JsonString))
          ))
        })
      ))
      CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString(step.id),
        "order" -> CozyExplanation.JsonNumber(step.order),
        "semanticRole" -> CozyExplanation.JsonString(step.semanticRole),
        "claims" -> CozyExplanation.JsonArray(claims),
        "logical" -> logical,
        "sourceRefs" -> CozyExplanation.JsonArray(step.sourceRefs.map(CozyExplanation.JsonString)),
        "assetRefs" -> CozyExplanation.JsonArray(step.assetRefs.map(CozyExplanation.JsonString)),
        "parameterSelection" -> CozyExplanation.JsonArray(step.parameterSelection.map { selection =>
          CozyExplanation.JsonObject(Vector("name" -> CozyExplanation.JsonString(selection.name)))
        })
      ))
    }
    val sources = composition.sources.map { source =>
      CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString(source.id),
        "sha256" -> CozyExplanation.JsonString(source.sha256)
      ))
    }
    val assets = composition.assets.map { asset =>
      CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString(asset.id),
        "mediaType" -> CozyExplanation.JsonString(asset.mediaType),
        "sha256" -> CozyExplanation.JsonString(asset.sha256)
      ))
    }
    CozyExplanation.JsonObject(Vector(
      "schema" -> CozyExplanation.JsonString("cozy.explanation-composition.v1"),
      "version" -> CozyExplanation.JsonNumber(1),
      "id" -> CozyExplanation.JsonString(composition.id),
      "explanationCatalog" -> catalogselector,
      "subject" -> CozyExplanation.JsonObject(Vector(
        "pattern" -> subjectpattern,
        "facts" -> CozyExplanation.JsonArray(facts)
      )),
      "explanation" -> CozyExplanation.JsonObject(Vector(
        "pattern" -> explanationpattern,
        "parameters" -> CozyExplanation.JsonArray(parameters),
        "steps" -> CozyExplanation.JsonArray(steps)
      )),
      "sources" -> CozyExplanation.JsonArray(sources),
      "assets" -> CozyExplanation.JsonArray(assets)
    ))
  }

  private def _reorder_json_value(value: CozyExplanation.JsonValue): CozyExplanation.JsonValue = value match {
    case CozyExplanation.JsonObject(fields) =>
      CozyExplanation.JsonObject(fields.reverse.map { case (fieldname, member) => fieldname -> _reorder_json_value(member) })
    case CozyExplanation.JsonArray(values) => CozyExplanation.JsonArray(values.map(_reorder_json_value))
    case scalar => scalar
  }

  private def _json_text(value: CozyExplanation.JsonValue): String = value match {
    case CozyExplanation.JsonObject(fields) => fields.map { case (fieldname, member) => _json_quote(fieldname) + ":" + _json_text(member) }.mkString("{", ",", "}")
    case CozyExplanation.JsonArray(values) => values.map(_json_text).mkString("[", ",", "]")
    case CozyExplanation.JsonString(text) => _json_quote(text)
    case CozyExplanation.JsonNumber(number) => number.toString
    case CozyExplanation.JsonBoolean(result) => result.toString
  }

  private def _json_quote(text: String): String = {
    val escaped = text.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if character < ' ' => "\\u%04x".format(character.toInt)
      case character => character.toString
    }
    "\"" + escaped + "\""
  }

  private def _labeled(id: String, label: String, source: Vector[String], asset: Vector[String]): CozyExplanation.JsonValue =
    CozyExplanation.JsonObject(Vector(
      "id" -> CozyExplanation.JsonString(id),
      "label" -> CozyExplanation.JsonString(label),
      "sourceRefs" -> CozyExplanation.JsonArray(source.map(CozyExplanation.JsonString)),
      "assetRefs" -> CozyExplanation.JsonArray(asset.map(CozyExplanation.JsonString))
    ))

  private def _logical(source: Vector[String]): CozyVisualPage.Logical = CozyVisualPage.Logical(
    "sequence",
    Vector(
      CozyVisualPage.Node("mechanism-start", "step", "Goal and use case", source),
      CozyVisualPage.Node("mechanism-end", "step", "Strict validation", source)
    ),
    Vector(CozyVisualPage.Relation("mechanism-next", "next", "mechanism-start", "mechanism-end", source))
  )

  private def _key_order(keys: Vector[String], index: Int): Vector[String] = {
    val base = if ((index / keys.size) % 2 == 0) keys else keys.reverse
    val offset = index % keys.size
    base.drop(offset) ++ base.take(offset)
  }

  private def _failure[A](body: => A): RuntimeException = intercept[RuntimeException](body)

  private def _write(path: Path, value: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(byte => f"${byte & 0xff}%02x").mkString

  private def _with_work[A](name: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-explanation-" + name + "-")
    try body(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) Files.list(path).iterator().asScala.foreach(_delete_tree)
      Files.deleteIfExists(path)
    }
}
