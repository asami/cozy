package cozy.document

import cozy.media.{CozyExplanation, CozyVisualPage}
import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Sep.  4, 2026
 * @version Sep.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentPresentationSemanticsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Presentation Semantics" should {
    "admit a v1-bound v2 document with a real Composition and distinct local patterns" in {
      Given("a direct v1 Core, a problem-solution Composition, and sequence and causal local graphs")
      val fixture = _fixture()

      When("the strict presentation-semantics sibling document is normalized")
      val validated = _validate(fixture)

      Then("the v1 binding, normalized Plan, local graphs, and canonical Article 8 vocabulary are retained")
      validated.contentCore.id shouldBe "article-8:core:en"
      validated.plan.steps.map(_.id) shouldBe Vector("problem-step", "solution-step")
      validated.structures.map(_.logical.pattern) shouldBe Vector("sequence", "causal-chain")
      validated.structures.head.article shouldBe CozyDocumentPresentationSemantics.Article(
        "Problem", Vector("The prior reader path was permissive."), "Show the reader-facing problem."
      )
      validated.structures.head.visualSelections.map(_.medium) shouldBe Vector("article", "slides", "video")
    }

    "closed Article and binding input" which {
      "reject unknown and renderer-alias fields, malformed and conflicting Cores, and a mismatched direct v1 Core identity" in {
        Given("otherwise valid canonical Article 8 fields, direct Core A bytes, malformed UTF-8 embedded in an otherwise-valid Core JSON value, and a v1-bound sibling document")
        val fixture = _fixture()
        val unknown = _with_field(fixture.semantics, "unknown", Json.fromString("no"))
        val alias = _replace_article_field(fixture.semantics, "visualIntent", "intent")
        val conflictingcore = Json.obj(
          "schema" -> Json.fromString("cozy.content-core.v1"),
          "id" -> Json.fromString("article-8:core:en"),
          "language" -> Json.fromString("en"),
          "accepted" -> Json.arr(Json.obj("id" -> Json.fromString("core"), "text" -> Json.fromString("different accepted Core B content")))
        )
        val changedcore = fixture.coreBytes ++ Array[Byte](1)
        val malformedcoreprefix = """{"schema":"cozy.content-core.v1","id":"article-8:core:en","language":"en","accepted":[{"id":"core","text":"accepted Core """.getBytes(StandardCharsets.UTF_8)
        val malformedcoresuffix = "\"}]}".getBytes(StandardCharsets.UTF_8)
        val malformedcore = malformedcoreprefix ++ Array[Byte](0xc3.toByte, 0x28.toByte) ++ malformedcoresuffix
        val malformedcorevalue = Json.obj(
          "schema" -> Json.fromString("cozy.content-core.v1"),
          "id" -> Json.fromString("article-8:core:en"),
          "language" -> Json.fromString("en"),
          "accepted" -> Json.arr(Json.obj("id" -> Json.fromString("core"), "text" -> Json.fromString("accepted Core \uFFFD(")))
        )
        val malformedsemantics = _semantics(malformedcore, fixture.composition, fixture.semantics)

        When("an unknown field, an intent alias, embedded malformed UTF-8 Core bytes, conflicting parsed Core B, and unbound Core bytes are submitted")
        val failures = Vector(
          _failure(_validate(fixture.copy(semantics = unknown))),
          _failure(_validate(fixture.copy(semantics = alias))),
          _failure(CozyDocumentPresentationSemantics.validate(malformedcore, malformedcorevalue, malformedsemantics, fixture.catalogs, fixture.bindings)),
          _failure(CozyDocumentPresentationSemantics.validate(fixture.coreBytes, conflictingcore, fixture.semantics, fixture.catalogs, fixture.bindings)),
          _failure(CozyDocumentPresentationSemantics.validate(changedcore, fixture.coreValue, fixture.semantics, fixture.catalogs, fixture.bindings))
        )

        Then("each form is rejected through the closed DP-SEM boundary rather than accepted as a placeholder")
        failures.map(_.code) should contain allOf ("DP-SEM-001", "DP-SEM-002", "DP-SEM-005")
        failures(2).code shouldBe "DP-SEM-001"
        failures(3).code shouldBe "DP-SEM-005"
        failures.foreach(_.code should startWith ("DP-SEM-"))
      }

      "reject duplicate or missing Structure-Step bindings and invalid Story Transition endpoints" in {
        Given("a valid CompositionStep set and three structural binding variants")
        val fixture = _fixture()
        val duplicate = _duplicate_structure_id(fixture.semantics)
        val missing = _replace_structure_step(fixture.semantics, "missing-step")
        val invalidtransition = _replace_transition_endpoint(fixture.semantics, "missing-step")

        When("the duplicate, unresolved Structure-Step, and unresolved Story Transition forms are normalized")
        val failures = Vector(
          _failure(_validate(fixture.copy(semantics = duplicate))),
          _failure(_validate(fixture.copy(semantics = missing))),
          _failure(_validate(fixture.copy(semantics = invalidtransition)))
        )

        Then("the typed Structure and StoryTransition contracts reject them")
        failures.map(_.code) should contain allOf ("DP-SEM-007", "DP-SEM-008")
      }
    }

    "Projection Policy selection" should {
      "select one compatible base Visual per medium and reject missing, ambiguous, and incompatible override forms" in {
        Given("a policy with complete compatible selections for every inherited Logical Pattern")
        val fixture = _fixture()
        val accepted = _validate(fixture)
        val missing = _remove_policy_binding(fixture.semantics, "article", "sequence")
        val ambiguous = _duplicate_policy_binding(fixture.semantics, "article", "sequence")
        val incompatible = _replace_first_override_visual(fixture.semantics, "mapping-columns")

        When("the policy resolves and each invalid policy form is normalized")
        val failures = Vector(
          _failure(_validate(fixture.copy(semantics = missing))),
          _failure(_validate(fixture.copy(semantics = ambiguous))),
          _failure(_validate(fixture.copy(semantics = incompatible)))
        )

        Then("selection remains deterministic and invalid base or override choices are rejected")
        accepted.structures.head.visualSelections.map(_.visual.pattern) shouldBe Vector("flow-vertical", "flow-horizontal", "flow-horizontal")
        failures.map(_.code) should contain allOf ("DP-SEM-009", "DP-SEM-010", "DP-SEM-011")
      }
    }

    "cross-media typed projection" should {
      "derive independently ordered one-to-many slide pages and Storyboard scenes without copying semantic authority" in {
        Given("a valid typed aggregate whose problem Structure has two reader-facing text items")
        val fixture = _fixture()
        val semantics = _replace_visible_text(fixture.semantics, "problem-structure", Vector(
          "The prior reader path was permissive.",
          "The typed boundary must retain declared semantics."
        ))
        val validated = _validate(fixture.copy(semantics = semantics))

        When("the accepted aggregate is projected into slide pages and Storyboard scenes")
        val projection = CozyDocumentCrossMediaProjection.project(validated)

        Then("both media retain the same Step, Structure, Logical, claim, reference, and selected medium Visual identities")
        projection.slidePages.map(_.id) shouldBe Vector("slide-problem-structure-1", "slide-problem-structure-2", "slide-solution-structure-1")
        projection.storyboardScenes.map(_.id) shouldBe Vector("scene-problem-structure-1", "scene-problem-structure-2", "scene-solution-structure-1")
        projection.slideMappings.map(_.pageIds) shouldBe Vector(
          Vector("slide-problem-structure-1", "slide-problem-structure-2"),
          Vector("slide-solution-structure-1")
        )
        projection.videoMappings.map(_.sceneIds) shouldBe Vector(
          Vector("scene-problem-structure-1", "scene-problem-structure-2"),
          Vector("scene-solution-structure-1")
        )
        projection.slidePages.head.logical shouldBe projection.storyboardScenes.head.logical
        projection.slidePages.head.claims shouldBe projection.storyboardScenes.head.claims
        projection.slidePages.head.claims.head.emphasis shouldBe "primary"
        projection.slidePages.head.visual.pattern shouldBe "flow-horizontal"
        projection.storyboardScenes.head.visual.pattern shouldBe "flow-horizontal"
        projection.storyFlow shouldBe validated.storyFlow

        And("the fixed-order result has a stable identity without inferred narration or timing")
        CozyDocumentCrossMediaProjection.project(validated).identity shouldBe projection.identity
        projection.storyboardScenes.head.caption shouldBe "The prior reader path was permissive."
      }

      "reject a Plan Step that has no Structure rather than emitting an empty medium mapping" in {
        Given("an otherwise valid aggregate that intentionally binds a Structure only to the solution Step")
        val fixture = _fixture()
        val structures = fixture.semantics.asObject.get("structures").get.asArray.get.drop(1)
        val validated = _validate(fixture.copy(semantics = _with_field(fixture.semantics, "structures", Json.fromValues(structures))))

        When("the incomplete aggregate is projected")
        val failure = _projection_failure(CozyDocumentCrossMediaProjection.project(validated))

        Then("projection completeness fails before a page, scene, or placeholder can be produced")
        failure.code shouldBe "DP-PROJ-001"
        failure.reason should include("problem-step")
      }

      "reject an empty-output Structure even when a sibling Structure covers the same Plan Step" in {
        Given("a validated aggregate with sibling Structures bound to one Plan Step, one of which has no visible text")
        val fixture = _fixture()
        val validated = _validate(fixture)
        val problemstructure = validated.structures.find(_.id == "problem-structure").get
        val emptysibling = problemstructure.copy(
          id = "empty-problem-structure",
          article = problemstructure.article.copy(visibleText = Vector.empty)
        )
        val withsibling = validated.copy(structures = validated.structures :+ emptysibling)

        When("the aggregate is projected into slide pages and Storyboard scenes")
        val failure = _projection_failure(CozyDocumentCrossMediaProjection.project(withsibling))

        Then("the empty Structure fails atomically instead of being covered by its sibling or replaced with a placeholder")
        failure.code shouldBe "DP-PROJ-001"
        failure.path shouldBe "$.slidePages"
        failure.reason should include("empty-problem-structure")
      }
    }

    "canonical and currentness identity" should {
      "change for v1 Core, v2 semantics, Composition, catalog, policy, and declared source or asset identity changes" in {
        Given("a self-contained fixture and independently changed contract inputs")
        val fixture = _fixture()
        val baseline = _validate(fixture)
        val changedcore = _fixture(coreText = "accepted Core revision")
        val changedsemantic = fixture.copy(semantics = _replace_article_heading(fixture.semantics, "Changed problem"))
        val changedcomposition = fixture.copy(composition = fixture.composition.copy(id = "changed-composition"))
        val changedcatalog = fixture.copy(catalogs = _catalogs("presentation-2", 2))
        val changedpolicy = fixture.copy(semantics = _replace_policy_visual(fixture.semantics, "slides", "sequence", "flow-vertical"))
        val changedresources = _fixture(sourceText = "changed source", assetText = "changed asset")

        When("each admitted currentness input is changed and rebound through the v2 document")
        val results = Vector(
          _validate(changedcore),
          _validate(changedsemantic),
          _validate(changedcomposition),
          _validate(changedcatalog),
          _validate(changedpolicy),
          _validate(changedresources)
        )

        Then("the affected canonical or aggregate currentness identities are not reused")
        results.map(_.currentnessIdentity) should not contain baseline.currentnessIdentity
        _changed_semantic_result(results, 1).semanticIdentity should not be baseline.semanticIdentity
        _changed_composition_result(results, 2).composition.identity should not be baseline.composition.identity
        _changed_catalog_result(results, 3).presentationCatalogIdentity should not be baseline.presentationCatalogIdentity
        _changed_policy_result(results, 4).policy.identity should not be baseline.policy.identity
        _changed_resources_result(results, 5).sources should not be baseline.sources
        _changed_resources_result(results, 5).assets should not be baseline.assets
      }
    }
  }

  private final case class Fixture(
    root: Path,
    coreBytes: Array[Byte],
    coreValue: Json,
    semantics: Json,
    composition: CozyExplanation.Composition,
    catalogs: CozyDocumentPresentationSemantics.Catalogs,
    bindings: CozyExplanation.ResourceBindings
  )

  private def _changed_semantic_result(values: Vector[CozyDocumentPresentationSemantics.Validated], index: Int): CozyDocumentPresentationSemantics.Validated = values(index)
  private def _changed_composition_result(values: Vector[CozyDocumentPresentationSemantics.Validated], index: Int): CozyDocumentPresentationSemantics.Validated = values(index)
  private def _changed_catalog_result(values: Vector[CozyDocumentPresentationSemantics.Validated], index: Int): CozyDocumentPresentationSemantics.Validated = values(index)
  private def _changed_policy_result(values: Vector[CozyDocumentPresentationSemantics.Validated], index: Int): CozyDocumentPresentationSemantics.Validated = values(index)
  private def _changed_resources_result(values: Vector[CozyDocumentPresentationSemantics.Validated], index: Int): CozyDocumentPresentationSemantics.Validated = values(index)

  private def _fixture(coreText: String = "accepted Core", sourceText: String = "source", assetText: String = "asset"): Fixture = {
    val root = Files.createTempDirectory("cozy-document-presentation-semantics-")
    val source = _write(root.resolve("source.txt"), sourceText)
    val asset = _write(root.resolve("asset.txt"), assetText)
    val corevalue = Json.obj(
      "schema" -> Json.fromString("cozy.content-core.v1"),
      "id" -> Json.fromString("article-8:core:en"),
      "language" -> Json.fromString("en"),
      "accepted" -> Json.arr(Json.obj("id" -> Json.fromString("core"), "text" -> Json.fromString(coreText)))
    )
    val corefile = _write(root.resolve("core.json"), corevalue.noSpaces)
    val corebytes = Files.readAllBytes(corefile)
    val composition = _composition(_sha256(source), _sha256(asset))
    Fixture(
      root,
      corebytes,
      corevalue,
      _semantics(corebytes, composition),
      composition,
      _catalogs(),
      CozyExplanation.ResourceBindings(Map("source" -> source), Map("asset" -> asset))
    )
  }

  private def _validate(fixture: Fixture): CozyDocumentPresentationSemantics.Validated =
    CozyDocumentPresentationSemantics.validate(fixture.coreBytes, fixture.coreValue, _semantics(fixture.coreBytes, fixture.composition, fixture.semantics), fixture.catalogs, fixture.bindings)

  private def _failure(body: => Any): CozyDocumentPresentationSemantics.PresentationSemanticsFault =
    intercept[CozyDocumentPresentationSemantics.PresentationSemanticsFault](body)

  private def _projection_failure(body: => Any): CozyDocumentCrossMediaProjection.ProjectionFault =
    intercept[CozyDocumentCrossMediaProjection.ProjectionFault](body)

  private def _semantics(corebytes: Array[Byte], composition: CozyExplanation.Composition, original: Json = Json.Null): Json = {
    val base = Json.fromFields(Vector(
      "schema" -> Json.fromString("cozy.content-core.presentation-semantics.v2"),
      "id" -> Json.fromString("article-8-presentation"),
      "contentCore" -> Json.obj(
        "id" -> Json.fromString("article-8:core:en"),
        "language" -> Json.fromString("en"),
        "identity" -> Json.fromString("sha256:" + _sha256(corebytes))
      ),
      "composition" -> _composition_json(composition),
      "storyFlow" -> Json.obj(
        "id" -> Json.fromString("article-story"),
        "transitions" -> Json.arr(Json.obj(
          "id" -> Json.fromString("problem-causes-solution"),
          "relationType" -> Json.fromString("causes"),
          "fromStepId" -> Json.fromString("problem-step"),
          "toStepId" -> Json.fromString("solution-step")
        ))
      ),
      "structures" -> Json.arr(
        _structure("problem-structure", "problem-step", "Problem", "The prior reader path was permissive.", "Show the reader-facing problem.", Some("flow-vertical")),
        _structure("solution-structure", "solution-step", "Solution", "The typed path preserves the declared content.", "Show the reader-facing solution.", None)
      ),
      "projectionPolicy" -> _policy()
    ))
    if (original.isNull) base else _retain_semantic_edits(base, original)
  }

  private def _retain_semantic_edits(base: Json, original: Json): Json = {
    val basefields = base.asObject.get
    val originalfields = original.asObject.get
    Json.fromFields(basefields.toVector.map { case (name, value) =>
      if (Set("contentCore", "composition").contains(name)) name -> value else name -> originalfields(name).getOrElse(value)
    } ++ originalfields.toVector.filterNot { case (name, _) => basefields(name).nonEmpty })
  }

  private def _structure(id: String, stepid: String, heading: String, text: String, intent: String, overridepattern: Option[String]): Json = Json.obj(
    "id" -> Json.fromString(id),
    "storyStepId" -> Json.fromString(stepid),
    "article" -> Json.obj(
      "articleHeading" -> Json.fromString(heading),
      "visibleText" -> Json.arr(Json.fromString(text)),
      "visualIntent" -> Json.fromString(intent)
    ),
    "visualOverrides" -> Json.fromValues(overridepattern.toVector.map(pattern => Json.obj(
      "medium" -> Json.fromString("article"),
      "visual" -> _visual(pattern)
    )))
  )

  private def _policy(): Json = Json.obj(
    "schema" -> Json.fromString("cozy.content-core.projection-policy.v1"),
    "id" -> Json.fromString("article-policy"),
    "revision" -> Json.fromInt(1),
    "bindings" -> Json.fromValues(for {
      medium <- Vector("article", "slides", "video")
      pattern <- Vector("sequence", "causal-chain")
    } yield Json.obj(
      "medium" -> Json.fromString(medium),
      "logicalPattern" -> Json.fromString(pattern),
      "visual" -> _visual("flow-horizontal")
    ))
  )

  private def _visual(pattern: String): Json = Json.obj(
    "pattern" -> Json.fromString(pattern),
    "parameters" -> Json.obj("showRelationLabels" -> Json.fromBoolean(true))
  )

  private def _composition(sourcehash: String, assethash: String): CozyExplanation.Composition = {
    val none = Vector.empty[String]
    val facts = Vector(
      CozyExplanation.Fact("name", CozyExplanation.JsonString("Cozy"), none, none),
      CozyExplanation.Fact("vision", CozyExplanation.JsonString("Make presentation semantics explicit"), none, none),
      CozyExplanation.Fact("goals", CozyExplanation.JsonArray(Vector(_labeled("goal", "Reliable plans"))), none, none),
      CozyExplanation.Fact("context", CozyExplanation.JsonString("Typed document workflow"), none, none),
      CozyExplanation.Fact("useCases", CozyExplanation.JsonArray(Vector(_labeled("use-case", "Explain document semantics"))), none, none),
      CozyExplanation.Fact("mainScenario", CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString("scenario"),
        "label" -> CozyExplanation.JsonString("Normalize a document"),
        "steps" -> CozyExplanation.JsonArray(Vector(_labeled("scenario-step", "Validate semantics")))
      )), none, none),
      CozyExplanation.Fact("mechanisms", CozyExplanation.JsonArray(Vector(_labeled("mechanism", "Typed validation"))), none, none)
    )
    val problem = CozyExplanation.CompositionStep(
      "problem-step", 1, "problem", Vector(CozyExplanation.Claim("problem-claim", "The prior reader path was permissive.", "primary", none, none)),
      _sequence(), none, none, Vector(CozyExplanation.ParameterSelection("problem"))
    )
    val solution = CozyExplanation.CompositionStep(
      "solution-step", 2, "solution", Vector(CozyExplanation.Claim("solution-claim", "The typed path preserves declared content.", "primary", none, none)),
      _causal(), none, none, Vector(CozyExplanation.ParameterSelection("solution"))
    )
    CozyExplanation.Composition(
      "article-composition",
      CozyExplanation.CatalogSelector(_explanation_catalog.id, _explanation_catalog.revision, CozyExplanation.catalogIdentity(_explanation_catalog)),
      CozyExplanation.Subject(CozyExplanation.PatternReference("software-product", 1), facts),
      CozyExplanation.Explanation(
        CozyExplanation.PatternReference("problem-solution", 1),
        Vector(
          CozyExplanation.Parameter("problem", CozyExplanation.JsonString("The reader-facing path was permissive.")),
          CozyExplanation.Parameter("solution", CozyExplanation.JsonString("The sibling schema is typed and closed."))
        ),
        Vector(problem, solution)
      ),
      Vector(CozyExplanation.SourceDeclaration("source", sourcehash)),
      Vector(CozyExplanation.AssetDeclaration("asset", "text/plain", assethash))
    )
  }

  private def _sequence(): CozyVisualPage.Logical = CozyVisualPage.Logical(
    "sequence",
    Vector(CozyVisualPage.Node("first", "step", "Reader input", Vector.empty), CozyVisualPage.Node("second", "step", "Typed boundary", Vector.empty)),
    Vector(CozyVisualPage.Relation("next", "next", "first", "second", Vector.empty))
  )

  private def _causal(): CozyVisualPage.Logical = CozyVisualPage.Logical(
    "causal-chain",
    Vector(CozyVisualPage.Node("cause", "cause", "Closed schema", Vector.empty), CozyVisualPage.Node("effect", "effect", "Faithful projection", Vector.empty)),
    Vector(
      CozyVisualPage.Relation("causes", "causes", "cause", "effect", Vector.empty),
      CozyVisualPage.Relation("enables", "enables", "cause", "effect", Vector.empty)
    )
  )

  private def _catalogs(id: String = "presentation", revision: Int = 1): CozyDocumentPresentationSemantics.Catalogs = {
    val presentation = CozyVisualPage.Catalog(
      id,
      revision,
      Vector(
        CozyVisualPage.RelationDefinition("next", "from-to"), CozyVisualPage.RelationDefinition("causes", "from-to"),
        CozyVisualPage.RelationDefinition("depends-on", "from-to"), CozyVisualPage.RelationDefinition("enables", "from-to"), CozyVisualPage.RelationDefinition("maps-to", "from-to")
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
    val explanation = CozyExplanation.ValidatedCatalog(_explanation_catalog, CozyExplanation.canonicalCatalogJson(_explanation_catalog), CozyExplanation.catalogIdentity(_explanation_catalog))
    CozyDocumentPresentationSemantics.Catalogs(explanation, CozyExplanation.PresentationCatalog(presentation, CozyVisualPage.catalogIdentity(presentation), CozyVisualPage.logicalCatalogIdentity(presentation)))
  }

  private val _explanation_catalog = CozyExplanation.Catalog(
    "software-explanation",
    1,
    Vector(CozyExplanation.SubjectPattern("software-product", 1, Vector(
      CozyExplanation.Definition("context", "text", required = true), CozyExplanation.Definition("goals", "goal-list", required = true),
      CozyExplanation.Definition("mainScenario", "scenario", required = true), CozyExplanation.Definition("mechanisms", "mechanism-list", required = true),
      CozyExplanation.Definition("name", "text", required = true), CozyExplanation.Definition("useCases", "use-case-list", required = true), CozyExplanation.Definition("vision", "text", required = true)
    ))),
    Vector(
      CozyExplanation.ExplanationPattern("problem-solution", 1, Vector(CozyExplanation.PatternReference("software-product", 1)), Vector(CozyExplanation.Definition("problem", "text", true), CozyExplanation.Definition("solution", "text", true)), Vector(CozyExplanation.Role("problem", 1, true), CozyExplanation.Role("solution", 2, true))),
      CozyExplanation.ExplanationPattern("product-mechanism", 1, Vector(CozyExplanation.PatternReference("software-product", 1)), Vector(CozyExplanation.Definition("mechanismLinks", "mechanism-link-list", true)), Vector(CozyExplanation.Role("mechanism", 1, true))),
      CozyExplanation.ExplanationPattern("product-overview", 1, Vector(CozyExplanation.PatternReference("software-product", 1)), Vector.empty, Vector(CozyExplanation.Role("vision", 1, true), CozyExplanation.Role("goal", 2, true), CozyExplanation.Role("context", 3, true), CozyExplanation.Role("use-case", 4, true), CozyExplanation.Role("main-scenario", 5, true)))
    ),
    Vector("problem-solution", "problem-cause-solution", "current-target", "before-after", "challenge-approach-result", "observation-insight-implication", "fact-interpretation-action", "why-what-how", "input-process-output", "concept-example", "claim-evidence", "claim-reasons", "question-answer", "principle-mechanism-effect", "strategy-execution-outcome", "past-present-future")
  )

  private def _composition_json(value: CozyExplanation.Composition): Json =
    parse(CozyExplanation.canonicalCompositionJson(value)).fold(error => throw error, identity)

  private def _labeled(id: String, label: String): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "id" -> CozyExplanation.JsonString(id), "label" -> CozyExplanation.JsonString(label),
    "sourceRefs" -> CozyExplanation.JsonArray(Vector.empty), "assetRefs" -> CozyExplanation.JsonArray(Vector.empty)
  ))

  private def _replace_article_heading(value: Json, heading: String): Json = _map_structures(value) { structure =>
    val fields = structure.asObject.get
    Json.fromFields(fields.toVector.map {
      case ("article", article) => "article" -> _with_field(article, "articleHeading", Json.fromString(heading))
      case field => field
    })
  }

  private def _replace_article_field(value: Json, oldname: String, newname: String): Json = _map_structures(value) { structure =>
    val fields = structure.asObject.get
    Json.fromFields(fields.toVector.map {
      case ("article", article) =>
        "article" -> Json.fromFields(article.asObject.get.toVector.map { case (name, item) => if (name == oldname) newname -> item else name -> item })
      case field => field
    })
  }

  private def _replace_visible_text(value: Json, structureid: String, text: Vector[String]): Json = _map_structures(value) { structure =>
    if (structure.asObject.get("id").flatMap(_.asString).contains(structureid)) {
      val fields = structure.asObject.get
      Json.fromFields(fields.toVector.map {
        case ("article", article) => "article" -> _with_field(article, "visibleText", Json.fromValues(text.map(Json.fromString)))
        case field => field
      })
    } else structure
  }

  private def _duplicate_structure_id(value: Json): Json = _map_structures(value) { structure => _with_field(structure, "id", Json.fromString("problem-structure")) }

  private def _replace_structure_step(value: Json, step: String): Json = _map_structures(value) { structure => _with_field(structure, "storyStepId", Json.fromString(step)) }

  private def _replace_transition_endpoint(value: Json, endpoint: String): Json = _with_field(value, "storyFlow", {
    val story = value.asObject.get("storyFlow").get
    _with_field(story, "transitions", Json.fromValues(story.asObject.get("transitions").get.asArray.get.map { transition => _with_field(transition, "toStepId", Json.fromString(endpoint)) }))
  })

  private def _remove_policy_binding(value: Json, medium: String, pattern: String): Json = _map_policy_bindings(value) { bindings =>
    bindings.filterNot(binding => binding.hcursor.get[String]("medium").toOption.contains(medium) && binding.hcursor.get[String]("logicalPattern").toOption.contains(pattern))
  }

  private def _duplicate_policy_binding(value: Json, medium: String, pattern: String): Json = _map_policy_bindings(value) { bindings =>
    bindings ++ bindings.find(binding => binding.hcursor.get[String]("medium").toOption.contains(medium) && binding.hcursor.get[String]("logicalPattern").toOption.contains(pattern)).toVector
  }

  private def _replace_policy_visual(value: Json, medium: String, pattern: String, visual: String): Json = _map_policy_bindings(value) { bindings =>
    bindings.map { binding =>
      if (binding.hcursor.get[String]("medium").toOption.contains(medium) && binding.hcursor.get[String]("logicalPattern").toOption.contains(pattern)) _with_field(binding, "visual", _visual(visual)) else binding
    }
  }

  private def _replace_first_override_visual(value: Json, visual: String): Json = _map_structures(value) { structure =>
    val overrides = structure.asObject.get("visualOverrides").get.asArray.get
    val changed = overrides.zipWithIndex.map { case (overridevalue, index) => if (index == 0) _with_field(overridevalue, "visual", _visual(visual)) else overridevalue }
    _with_field(structure, "visualOverrides", Json.fromValues(changed))
  }

  private def _map_structures(value: Json)(f: Json => Json): Json = _with_field(value, "structures", Json.fromValues(value.asObject.get("structures").get.asArray.get.map(f)))

  private def _map_policy_bindings(value: Json)(f: Vector[Json] => Vector[Json]): Json = _with_field(value, "projectionPolicy", {
    val policy = value.asObject.get("projectionPolicy").get
    _with_field(policy, "bindings", Json.fromValues(f(policy.asObject.get("bindings").get.asArray.get)))
  })

  private def _with_field(value: Json, name: String, replacement: Json): Json = {
    val fields = value.asObject.get.toVector
    val replaced = fields.map { case (field, item) => if (field == name) name -> replacement else field -> item }
    Json.fromFields(if (fields.exists(_._1 == name)) replaced else replaced :+ (name -> replacement))
  }

  private def _write(path: Path, value: String): Path = {
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString
}
