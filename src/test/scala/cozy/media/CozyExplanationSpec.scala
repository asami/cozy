package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import io.circe.Json
import io.circe.parser.parse

import scala.collection.JavaConverters._

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

import cozy.CozySpecVocabulary

/*
 * @since   Aug. 28, 2026
 *  version Sep. 30, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyExplanationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Explanation catalog" should {
    "normalize equivalent JSON key-order variants into one canonical catalog value" in {
      Given("the complete accepted v1 catalog represented by generated root-key orders")
      val canonical = CozyExplanation.parseCatalogJson(_catalog_json())
      val keys = Vector("schema", "version", "id", "revision", "subjectPatterns", "explanationPatterns", "reservedNarrativeArgumentIds")
      val orders = Gen.chooseNum(0, 29).map(index => _key_order(keys, index))

      When("ScalaCheck strictly parses at least thirty admissible JSON representations")
      val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(orders) { order =>
        val parsed = CozyExplanation.parseCatalogJson(_catalog_json(order))
        CozyExplanation.canonicalCatalogJson(parsed) == CozyExplanation.canonicalCatalogJson(canonical)
      })

      Then("input key order is not retained, while canonical catalog values converge")
      check.passed shouldBe true
      check.succeeded should be >= 30
      CozyExplanation.canonicalCatalogJson(canonical) should include_text("\"subjectPatterns\"")
    }
  }

  "Cozy Explanation Composition" should {
    "reject malformed required catalog selectors and Composition and Plan syntax" in {
      _with_work("catalog-selectors") { root =>
        Given("current named catalogs, direct resource bindings, and an authored Composition and expanded Plan")
        val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(_catalog))
        val presentationfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(_presentation_catalog))
        val bindings = CozyExplanation.ResourceBindings(
          Map("source" -> _write(root.resolve("source.txt"), "current source")),
          Map("asset" -> _write(root.resolve("asset.txt"), "current asset"))
        )
        val composition = _composition()
        val canonical = CozyExplanation.canonicalCompositionJson(composition)
        val compositionfile = _write(root.resolve("composition.json"), canonical)
        val plan = CozyExplanation.expand(CozyExplanation.loadComposition(compositionfile, catalogfile, presentationfile, bindings))
        val planjson = CozyExplanation.canonicalPlanJson(plan)
        val invalid = Vector(
          canonical.replace("\"revision\":1", "\"revision\":true"),
          canonical.replace("\"revision\":1", "\"revision\":0"),
          canonical.replace("\"id\":\"software-explanation\"", "\"id\":null"),
          canonical.replace("\"explanationCatalog\":{", "\"explanationCatalog\":{\"unknown\":{},"),
          canonical.replace("\"revision\":1", "\"revision\":1,\"revision\":2"),
          canonical + "{}", canonical.dropRight(1)
        )
        val invalidplans = Vector("explanationCatalog", "presentationCatalog").flatMap { name =>
          Vector(Json.obj("id" -> Json.Null, "revision" -> Json.fromInt(1)),
            Json.obj("id" -> Json.fromString("required"), "revision" -> Json.fromBoolean(true)),
            Json.obj("id" -> Json.fromString("required"), "revision" -> Json.fromInt(0)),
            Json.obj("id" -> Json.fromString("required"), "revision" -> Json.fromInt(1), "unknown" -> Json.obj()),
            Json.obj("revision" -> Json.fromInt(1))).map { selector =>
            parse(planjson).toOption.get.mapObject(_.add(name, selector)).noSpaces
          }
        } ++ Vector(planjson.replace("\"presentationCatalog\":{", "\"presentationCatalog\":{\"revision\":1,"), planjson + "{}", planjson.dropRight(1))

        When("the public parsers encounter malformed required IDs and revisions, unknown fields, duplicates, and invalid syntax")
        val failures = invalid.map(text => _failure(CozyExplanation.parseCompositionJson(text)))
        val planfailures = invalidplans.map(text => _failure(CozyExplanation.parsePlanJson(text)))

        Then("normal required-field and JSON constraints reject every malformed document")
        failures should have size invalid.size
        planfailures should have size invalidplans.size
        (failures ++ planfailures).foreach(_.getMessage should include_text("EXPLANATION_"))
        plan.presentationCatalog shouldBe CozyExplanation.CatalogSelector(_presentation_catalog.id, _presentation_catalog.revision)
      }
    }

    "validate and expand a complete explicit software-product Composition without semantic inference" in {
      _with_work("expand") { root =>
        Given("named catalog files, declared direct source and asset bytes, and one authored product-mechanism Composition")
        val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(_catalog))
        val presentationfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(_presentation_catalog))
        val sourcefile = _write(root.resolve("sources/source.txt"), "source bytes")
        val assetfile = _write(root.resolve("assets/asset.txt"), "asset bytes")
        val composition = _composition()
        val compositionfile = _write(root.resolve("composition.json"), CozyExplanation.canonicalCompositionJson(composition))
        val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))

        When("the Composition is loaded against both explicit catalogs and deterministically expanded twice")
        val loaded = CozyExplanation.loadComposition(compositionfile, catalogfile, presentationfile, bindings)
        val first = CozyExplanation.expand(loaded)
        val second = CozyExplanation.expand(loaded)

        Then("the Plan preserves authored role, claim, P36 Logical Pattern/Relation graph, parameter provenance, and named catalog ID/revision")
        first shouldBe second
        first.steps.map(_.semanticRole) shouldBe Vector("mechanism")
        first.steps.map(_.claims) shouldBe composition.explanation.steps.map(_.claims)
        first.steps.map(_.logical) shouldBe composition.explanation.steps.map(_.logical)
        first.steps.head.parameterProvenance.values shouldBe loaded.composition.explanation.parameters
        first.presentationCatalog shouldBe CozyExplanation.CatalogSelector(_presentation_catalog.id, _presentation_catalog.revision)
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
        val composition = _problem_solution_composition()
        val compositionfile = _write(root.resolve("problem-solution-composition.json"), CozyExplanation.canonicalCompositionJson(composition))
        val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))

        When("the direct-file Composition is loaded and expanded twice against the named catalogs")
        val loaded = CozyExplanation.loadComposition(compositionfile, catalogfile, presentationfile, bindings)
        val first = CozyExplanation.expand(loaded)
        val second = CozyExplanation.expand(loaded)

        Then("the ordered problem and solution steps preserve selected parameter values, authored logical data, and canonical serialization")
        first shouldBe second
        loaded.composition shouldBe composition
        first.steps.map(_.semanticRole) shouldBe Vector("problem", "solution")
        first.steps.map(_.parameterProvenance.values.map(value => value.name -> value.value)) shouldBe Vector(
          Vector("problem" -> CozyExplanation.JsonString("Direct file provenance was previously implicit.")),
          Vector("solution" -> CozyExplanation.JsonString("Bind every declaration to explicit direct bytes."))
        )
        first.steps.map(_.logical) shouldBe composition.explanation.steps.map(_.logical)
        CozyExplanation.canonicalPlanJson(first) shouldBe CozyExplanation.canonicalPlanJson(second)
      }
    }

    "load historical material metadata with current direct bindings and serialize only required declarations" in {
      _with_work("material-metadata") { root =>
        Given("hash-free declarations, exact direct binding IDs, and historical ordinary extra metadata")
        val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(_catalog))
        val presentationfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(_presentation_catalog))
        val sourcefile = _write(root.resolve("sources/source.txt"), "original source bytes")
        val assetfile = _write(root.resolve("assets/asset.txt"), "original asset bytes")
        val composition = _composition()
        val canonical = CozyExplanation.canonicalCompositionJson(composition)
        val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))
        val extras = Vector(Json.fromString("malformed obsolete digest"), Json.fromBoolean(true), Json.fromInt(7), Json.obj("ordinary" -> Json.fromString("metadata")))
        val inputs = extras.zipWithIndex.map { case (extra, index) =>
          val value = parse(canonical).toOption.get.mapObject { fields =>
            Vector("sources", "assets").foldLeft(fields) { (current, name) =>
              current.add(name, Json.fromValues(current(name).get.asArray.get.map(_.mapObject(_.add("sha256", extra).add("ordinaryMetadata", extra)))))
            }
          }
          _write(root.resolve(s"historical-$index.json"), value.noSpaces)
        }
        val invalidinputs = Vector(
          canonical.replace("\"mediaType\":\"text/plain\"", "\"mediaType\":true"),
          canonical.replace(",\"mediaType\":\"text/plain\"", ""),
          canonical.replace("\"id\":\"source\"", "\"id\":true"),
          canonical.replace("\"mediaType\":\"text/plain\"", "\"mediaType\":\"text/plain\",\"mediaType\":\"text/plain\"")
        ).zipWithIndex.map { case (text, index) => _write(root.resolve(s"invalid-material-$index.json"), text) }
        Files.createSymbolicLink(root.resolve("sources/link.txt"), sourcefile)

        When("valid current bytes replace each bound file and historical declarations are loaded alongside invalid required fields and bindings")
        _write(sourcefile, "changed valid source bytes")
        _write(assetfile, "changed valid asset bytes")
        val loaded = inputs.map(input => CozyExplanation.loadComposition(input, catalogfile, presentationfile, bindings))
        val plans = loaded.map(CozyExplanation.expand)
        val failures = invalidinputs.map(input => _failure(CozyExplanation.loadComposition(input, catalogfile, presentationfile, bindings))) ++ Vector(
          _failure(CozyExplanation.loadComposition(inputs.head, catalogfile, presentationfile, bindings.copy(sources = Map.empty))),
          _failure(CozyExplanation.loadComposition(inputs.head, catalogfile, presentationfile, bindings.copy(assets = Map("asset" -> root.resolve("assets/missing.txt"))))),
          _failure(CozyExplanation.loadComposition(inputs.head, catalogfile, presentationfile, bindings.copy(sources = Map("source" -> root.resolve("sources/link.txt")))))
        )

        Then("current file bytes remain valid, canonical declarations retain their IDs and media type, and required fields and direct binding safety still reject")
        loaded.map(_.composition).distinct shouldBe Vector(composition)
        loaded.map(value => CozyExplanation.canonicalCompositionJson(value.composition)).distinct shouldBe Vector(canonical)
        plans.foreach(_.steps.head.logical shouldBe composition.explanation.steps.head.logical)
        failures should have size 7
        failures.foreach(_.getMessage should include_text("EXPLANATION_"))
      }
    }

    "canonicalize recursively reordered dynamic values while preserving authored array order" in {
      Given("an accepted Composition containing ordered lists, a scenario value, and mechanism-link parameters")
      val composition = _composition()
      val originaljson = _composition_json_value(composition)
      val original = CozyExplanation.parseCompositionJson(_json_text(originaljson))
      val reorderedjson = _reorder_json_value(originaljson)

      When("the recursively reordered JSON object fields are parsed as an equivalent Composition")
      val reordered = CozyExplanation.parseCompositionJson(_json_text(reorderedjson))
      val canonicaljson = CozyExplanation.canonicalCompositionJson(reordered)

      Then("canonical JSON ignores nested object-key order while authored arrays stay ordered")
      _json_text(reorderedjson) should not be _json_text(originaljson)
      canonicaljson shouldBe CozyExplanation.canonicalCompositionJson(original)
      canonicaljson should include ("\"value\":[{\"id\":\"goal-1\",\"label\":\"Reliable plans\",\"sourceRefs\":[\"source\"],\"assetRefs\":[\"asset\"]}]")
      canonicaljson should include ("\"value\":{\"id\":\"scenario-1\",\"label\":\"Expand an explanation\",\"steps\":[{\"id\":\"scenario-step-1\",\"label\":\"Validate authored inputs\",\"sourceRefs\":[\"source\"],\"assetRefs\":[\"asset\"]}]}")
      canonicaljson should include ("\"value\":[{\"id\":\"link-1\",\"goalId\":\"goal-1\",\"useCaseId\":\"use-case-1\",\"mechanismId\":\"mechanism-1\",\"sourceRefs\":[\"source\"],\"assetRefs\":[\"asset\"]}]")
      reordered.subject.facts.map(_.name) shouldBe original.subject.facts.map(_.name)
      reordered.explanation.parameters.map(_.name) shouldBe original.explanation.parameters.map(_.name)
      reordered.subject.facts shouldBe original.subject.facts
      reordered.explanation.parameters shouldBe original.explanation.parameters
    }

    "reject companion, compatibility, logical-data, invalid-binding, and incomplete-expansion failures with EXPLANATION diagnostics" in {
      _with_work("rejections") { root =>
        Given("one otherwise valid direct-file Composition closure and adversarial variants")
        val catalogfile = _write(root.resolve("explanation-catalog.json"), CozyExplanation.canonicalCatalogJson(_catalog))
        val presentationfile = _write(root.resolve("presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(_presentation_catalog))
        val alteredpresentationfile = _write(root.resolve("altered-presentation-catalog.json"), CozyVisualPage.canonicalCatalogJson(
          _presentation_catalog.copy(relations = _presentation_catalog.relations.updated(0, CozyVisualPage.RelationDefinition("not-next", "from-to")))
        ))
        val sourcefile = _write(root.resolve("sources/source.txt"), "source bytes")
        val assetfile = _write(root.resolve("assets/asset.txt"), "asset bytes")
        val composition = _composition()
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

        When("operations omit or duplicate direct companions, select an incompatible pattern, provide malformed P36 logical data, invalid bindings, or omit authored expansion steps")
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
        val missingbindings = bindings.copy(sources = Map("source" -> root.resolve("sources/missing.txt")))
        val missingfailure = _failure(CozyExplanation.loadComposition(compositionfile, catalogfile, presentationfile, missingbindings))

        Then("every rejected input reports a structured EXPLANATION code, field path, and concise reason without inference")
        Vector(omission, duplicate, incompatiblefailure, logicalfailure, incompletefailure, missingfailure).map(_.getMessage).forall(message =>
          message.contains("EXPLANATION_") && message.contains("path=") && message.contains("reason=")
        ) shouldBe true
        omission.getMessage should include_text("EXPLANATION_COMMAND_INVALID")
        duplicate.getMessage should include_text("EXPLANATION_REFERENCE_INVALID")
        incompatiblefailure.getMessage should include_text("EXPLANATION_CATALOG_MISMATCH")
        logicalfailure.getMessage should include_text("EXPLANATION_REFERENCE_INVALID")
        incompletefailure.getMessage should include_text("EXPLANATION_EXPANSION_INCOMPLETE")
        alteredpresentationfailure.getMessage should include_text("EXPLANATION_PRESENTATION_CATALOG_INVALID")
        missingfailure.getMessage should include_text("EXPLANATION_REFERENCE_INVALID")
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
        val composition = _composition()
        val compositionfile = _write(root.resolve("composition.json"), CozyExplanation.canonicalCompositionJson(composition))
        val bindings = CozyExplanation.ResourceBindings(Map("source" -> sourcefile), Map("asset" -> assetfile))
        val plan = CozyExplanation.expand(CozyExplanation.loadComposition(compositionfile, catalogfile, presentationfile, bindings))
        val planfile = _write(root.resolve("plan.json"), CozyExplanation.canonicalPlanJson(plan))
        val output = root.resolve("converted-plan.json")

        When("the Plan is loaded, inspected, validated, and directly converted through the direct explanation command route")
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

        Then("the serialized Plan remains current and byte-deterministic, while command output reports schema and step facts without adding ProjectionMap or Projection fields")
        loaded.plan shouldBe plan
        Files.readString(output, StandardCharsets.UTF_8) shouldBe CozyExplanation.canonicalPlanJson(plan) + "\n"
        inspect should include_text("schema: cozy.explanation-plan.v1")
        inspect should include_text("step: 1:mechanism-step")
        validate should include_text("presentationCatalogRevision")
        convert should include_text("steps: 1")
        CozyExplanation.canonicalPlanJson(plan) should not include ("projectionMap")
        CozyExplanation.canonicalPlanJson(plan) should not include ("visualPageSet")
        CozyExplanation.canonicalPlanJson(plan) should not include ("storyboard")

        Given("Plans with incompatible catalog selectors, pattern references, or changed authored claims")
        val variants = Vector(
          plan.copy(explanationCatalog = plan.explanationCatalog.copy(id = "another-catalog")),
          plan.copy(presentationCatalog = plan.presentationCatalog.copy(revision = 2)),
          plan.copy(subjectPattern = plan.subjectPattern.copy(version = 2)),
          plan.copy(explanationPattern = plan.explanationPattern.copy(id = "problem-solution")),
          plan.copy(steps = plan.steps.updated(0, plan.steps.head.copy(
            claims = plan.steps.head.claims.map(_.copy(text = "Another authored claim"))
          )))
        ).zipWithIndex.map { case (variant, index) =>
          _write(root.resolve(s"invalid-plan-$index.json"), CozyExplanation.canonicalPlanJson(variant))
        }
        When("Plan loading compares actual selectors, pattern references, and typed expanded steps")
        val failures = variants.map(input => _failure(CozyExplanation.loadPlan(input, compositionfile, catalogfile, presentationfile, bindings)))
        Then("selector and pattern conflicts differ from an authored expansion conflict")
        failures.take(4).foreach(_.getMessage should include_text("EXPLANATION_CATALOG_MISMATCH"))
        failures.last.getMessage should include_text("EXPLANATION_EXPANSION_INCOMPLETE")
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

  private def _composition(): CozyExplanation.Composition = {
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
      CozyExplanation.CatalogSelector(_catalog.id, _catalog.revision),
      CozyExplanation.Subject(CozyExplanation.PatternReference("software-product", 1), facts),
      CozyExplanation.Explanation(
        CozyExplanation.PatternReference("product-mechanism", 1),
        Vector(CozyExplanation.Parameter("mechanismLinks", links)),
        Vector(step)
      ),
      Vector(CozyExplanation.SourceDeclaration("source")),
      Vector(CozyExplanation.AssetDeclaration("asset", "text/plain"))
    )
  }

  private def _problem_solution_composition(): CozyExplanation.Composition = {
    val base = _composition()
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
      "revision" -> CozyExplanation.JsonNumber(composition.explanationCatalog.revision)
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
        "id" -> CozyExplanation.JsonString(source.id)
      ))
    }
    val assets = composition.assets.map { asset =>
      CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString(asset.id),
        "mediaType" -> CozyExplanation.JsonString(asset.mediaType)
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
