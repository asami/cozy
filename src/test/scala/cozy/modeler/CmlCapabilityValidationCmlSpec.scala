package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import CmlSemanticFoundation.{Present, Absent, NotDeclared, NotRepresented, NotApplicable, Local, External, ModelElementId, TermId, RelationId, ProfileId}
import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope._
import CmlCapabilityValidationSpec._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityValidationCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Original Capability CML semantic validation" should {
    "consume successfully normalized original documents" which {
      "C64V01 retain both real resources and all three exact realization bindings" in {
        Given("the two unchanged parser resources and explicitly supplied semantic facts, historical versions and target contracts")
        val scopes = Vector(_primary_scope, _secondary_scope)
        val paths = Vector("src/test/resources/modeler/capability-model.cml", "src/test/resources/modeler/capability-model-secondary.cml")
        val input = paths.zip(scopes).map { case (path, scope) => SourceInput(scope,
          KaleidoxModel.load(KaleidoxConfig.default, Paths.get(path).toFile)) }
        val scope = _real_scope
        When("normalization succeeds and its original documents are semantically validated")
        val documents = _normalized(CmlCapabilityCml.normalize(input))
        val validated = _admitted(CmlCapabilityValidation.validate(documents, ScopeInput.Ready(scope)))
        val reversed = _admitted(CmlCapabilityValidation.validate(documents.reverse, ScopeInput.Ready(scope)))
        Then("whole documents, scope, independent revisions, order and executable target pairs survive unchanged")
        validated.documents shouldBe documents
        validated.scope shouldBe scope
        reversed.documents shouldBe documents.reverse
        validated.documents.map(_.scope) shouldBe scopes
        validated.bindings.map(_._1.targetKind) shouldBe Vector(Operation, Workflow, StateMachine)
        validated.bindings.map(_._2) shouldBe scope.targetcontracts
        validated.bindings.map(_._1.source.sourceRevision) shouldBe Vector("realization-r15", "realization-r16", "realization-r17")
        validated.documents(1).model.componentCapabilities.head.source.sourceRevision shouldBe "secondary-definition-r20"
        validated.documents.head.model.componentDeclarations.map(_.direction) shouldBe Vector(Provided, Provided, Required)
      }

      "C64V02 retain all four Local Use Case demand links independently of discovery and providers" in {
        Given("real CML with two Usage definitions demanding two qualified Use Cases, Local Scenario discovery and a standalone Usage")
        val source = _primary.replace("\"role\":\"GOAL\"", "\"role\":\"USE_CASE\"")
          .replace("goal:two", "usecase:two").replace("\"elementId\":\"scenario:one\"},\"boundary\":\"EXTERNAL\"",
            "\"elementId\":\"scenario:one\"},\"boundary\":\"LOCAL\"")
        val scope = _real_scope.copy(modelfacts = _real_scope.modelfacts.filterNot(_.identity == _real_id("goal:two")) ++
          Vector(ModelFact(_real_id("usecase:two"), ModelKind.UseCase, _real_source("fact:usecase2"))))
        When("the actual authored CML is parsed, normalized and validated")
        val documents = _parse(source, _primary_scope)
        val validated = _admitted(CmlCapabilityValidation.validate(documents, ScopeInput.Ready(scope)))
        val usages = validated.documents.head.model.componentUsageCapabilities
        Then("all four exact demand links remain in their supplied order without grouping or inverse edges")
        validated.documents shouldBe documents
        _items(usages.head.demandReferences) shouldBe Vector.empty
        usages.tail.map(x => _items(x.demandReferences).map(_.target.identity)) shouldBe
          Vector.fill(2)(Vector(_real_id("usecase:one"), _real_id("usecase:two")))
        usages.tail.flatMap(x => _items(x.demandReferences)).map(_.role) shouldBe Vector.fill(4)(UseCase)
        usages.map(_.name).distinct shouldBe Vector("Same presentation")
        _items(usages(1).discoveryReferences).head.role shouldBe Scenario
        _items(usages(1).componentMappings).head.target.identity shouldBe _real_id("Component:Store|α")
        validated.documents.head.model.componentDeclarations.map(x => (x.component, x.direction)).distinct.size shouldBe 3
      }

      "C64V03 retain explicit gaps and Local or unknown External grounding with foreign evidence" in {
        Given("the real CML varied over every absence reason and a Local BoK Term, with a conflicting local fact for an External link")
        val reasons = Vector("NotDeclared" -> NotDeclared, "NotRepresented" -> NotRepresented,
          "Unsupported" -> CmlSemanticFoundation.Unsupported, "NotApplicable" -> NotApplicable)
        val variants = reasons.flatMap { case (tag, reason) =>
          val source = _primary.replace("\"target\":{\"kind\":\"TERM\",\"identity\":{\"vocabularyId\":\"BoK:語彙\",\"termId\":\"term:注文|α\"}},\"boundary\":\"EXTERNAL\"",
            "\"target\":{\"kind\":\"TERM\",\"identity\":{\"vocabularyId\":\"BoK:語彙\",\"termId\":\"term:注文|α\"}},\"boundary\":\"LOCAL\"")
          val gap = Json.stringify(Json.obj("state" -> "ABSENT", "reason" -> tag, "detail" -> "Explicit authored gap."))
          val external = source.replace("\"target\":{\"kind\":\"MODEL\",\"identity\":{\"modelId\":\"Model:能力/A\",\"elementId\":\"concept:Order\"}},\"boundary\":\"LOCAL\"",
            "\"target\":{\"kind\":\"MODEL\",\"identity\":{\"modelId\":\"Model:能力/A\",\"elementId\":\"concept:Order\"}},\"boundary\":\"EXTERNAL\"")
          Vector((reason, Vector(Local, Local), _field(source, "description", gap)),
            (reason, Vector(External, Local), _field(external, "description", gap)),
            (reason, Vector(External, Local), _field(external.replace("concept:Order", "concept:Unknown"), "description", gap)))
        }
        val scope = _real_scope.copy(modelfacts = _real_scope.modelfacts :+
          ModelFact(_real_id("component:model"), ModelKind.Goal, _real_source("conflicting-external-kind")))
        When("each actual CML variant is parsed, normalized and semantically validated")
        val results = variants.map { case (reason, boundaries, source) =>
          val documents = _parse(source, _primary_scope)
          (reason, boundaries, documents, CmlCapabilityValidation.validate(documents, ScopeInput.Ready(scope)))
        }
        Then("all exact optional data, boundaries and independently ordered foreign Derived sources remain intact")
        results.foreach { case (reason, boundaries, documents, result) =>
          val validated = _admitted(result)
          validated.documents shouldBe documents
          validated.documents.head.model.componentUsageCapabilities.foreach(_.description shouldBe Absent(reason, "Explicit authored gap."))
          val usage = validated.documents.head.model.componentUsageCapabilities(1)
          val groundings = _items(usage.grounding)
          groundings.map(_.boundary) shouldBe boundaries
          groundings.head.relation shouldBe Present(RelationId("vocab:关系", "rel:uses|α"))
          groundings.head.profile shouldBe Present(ProfileId("profile:scope", "profile:one"))
          _items(usage.discoveryReferences).last.target.boundary shouldBe External
          _items(usage.componentMappings).last.boundary shouldBe External
          groundings.last.origin shouldBe Derived("rule:discovery|α", Vector(
            CapabilitySource("Other:能力/B", "model/capability-model-secondary.cml", "foreign-edge-r9", Some(8)),
            _real_source("evidence-r2", Some(91))))
        }
      }

      "C64V04 reject each forbidden realization control field as a contextual SyntaxIssue" in {
        Given("real CML realization records with procedure, order, retry, compensation, authorization or runtimeState fields")
        val fields = Vector("procedure", "order", "retry", "compensation", "authorization", "runtimeState")
        val variants = fields.map { field =>
          _primary.split("\n", -1).map { line =>
            if (line.startsWith("realizations =") && line.contains("targetKind")) {
              val json = Json.parse(line.substring(line.indexOf('=') + 1)).as[JsObject]
              val values = (json \ "value").as[Vector[JsObject]].map(_ + (field -> JsString("Forbidden control")))
              "realizations = " + Json.stringify(json + ("value" -> JsArray(values)))
            } else line
          }.mkString("\n")
        }
        When("the actual parser results are normalized without calling semantic validation on a failed input")
        val results = variants.map(source => CmlCapabilityCml.normalize(Vector(SourceInput(_primary_scope,
          KaleidoxModel.parse(KaleidoxConfig.default, source)))))
        Then("every forbidden field yields typed syntax failure in its original document and realization field context")
        results.foreach { result =>
          result.isLeft shouldBe true
          val issues = result.left.get
          issues.nonEmpty shouldBe true
          issues.foreach(_.scope shouldBe _primary_scope)
          issues.exists(_.fieldPath.contains("realizations")) shouldBe true
          issues.exists(_.sectionPath.nonEmpty) shouldBe true
        }
      }

      "C64V05 preserve parsed errors and explicit acquisition rejection instead of admitting emptiness" in {
        Given("an error-bearing real parsed model and four original unavailable, unauthorized, stale or unsupported scope diagnostics")
        val parsed = KaleidoxModel.parse(KaleidoxConfig.default, _primary.replace("\"elementId\":\"Application:Independent\"", "\"elementId\":\"broken\\\"quote\""))
        val kinds = Vector(DiagnosticKind.Unavailable, DiagnosticKind.Unauthorized, DiagnosticKind.Stale, DiagnosticKind.Unsupported)
        val diagnostics = kinds.map(kind => Diagnostic(kind, "scope.acquire", None, Some(_source("acquisition-r9")), Vector.empty, "Original acquisition failure."))
        When("normalization sees the original parser errors and semantic validation receives each explicit Rejected scope")
        val normalized = CmlCapabilityCml.normalize(Vector(SourceInput(_primary_scope, parsed)))
        val results = diagnostics.map(issue => CmlCapabilityValidation.validate(Vector.empty, ScopeInput.Rejected(Vector(issue))))
        Then("parser errors remain untouched syntax failure and every rejected scope retains its exact typed diagnostic")
        parsed.errors.nonEmpty shouldBe true
        normalized.isLeft shouldBe true
        normalized.left.get.exists(_.kind == MalformedInput) shouldBe true
        results.zip(diagnostics).foreach { case (result, issue) => result shouldBe Left(Vector(issue)) }
      }
    }
  }

  private def _primary_scope: SourceScope = SourceScope("Model:能力/A", "model/capability-model.cml")
  private def _secondary_scope: SourceScope = SourceScope("Other:能力/B", "model/capability-model-secondary.cml")
  private def _real_id(element: String): ModelElementId = ModelElementId(_primary_scope.modelAuthority, element)
  private def _real_source(revision: String, line: Option[Int] = None): CapabilitySource =
    CapabilitySource(_primary_scope.modelAuthority, _primary_scope.path, revision, line)
  private def _primary: String = new String(Files.readAllBytes(Paths.get("src/test/resources/modeler/capability-model.cml")), StandardCharsets.UTF_8)
  private def _field(source: String, name: String, value: String): String =
    source.split("\n", -1).map(line => if (line.startsWith(name + " =")) s"$name = $value" else line).mkString("\n")
  private def _parse(source: String, scope: SourceScope): Vector[CapabilityDocument] =
    _normalized(CmlCapabilityCml.normalize(Vector(SourceInput(scope, KaleidoxModel.parse(KaleidoxConfig.default, source)))))
  private def _normalized(result: Either[Vector[SyntaxIssue], Vector[CapabilityDocument]]): Vector[CapabilityDocument] = result match {
    case Right(documents) => documents
    case Left(issues) => fail(issues.mkString("; "))
  }
  private def _real_scope: Scope = {
    val models = Vector("usecase:one" -> ModelKind.UseCase, "goal:two" -> ModelKind.Goal,
      "scenario:one" -> ModelKind.Scenario, "concept:Order" -> ModelKind.DomainConcept)
      .map { case (element, kind) => ModelFact(_real_id(element), kind, _real_source("fact:" + element)) }
    val term = TermFact(TermId("BoK:語彙", "term:注文|α"), CapabilitySource("BoK:語彙", "catalog/terms.cml", "term:r8", None))
    val relation = RelationFact(RelationId("vocab:关系", "rel:uses|α"), CapabilitySource("vocab:关系", "catalog/relations.cml", "relation:r3", None))
    val profile = ProfileFact(ProfileId("profile:scope", "profile:one"), CapabilitySource("profile:scope", "catalog/profiles.cml", "profile:r7", None))
    val history = Vector("contract:一", "contract:三").map(version =>
      ComponentContract(_real_id("Component:Store|α"), version, _real_source("catalog:" + version)))
    val targets = Vector((Operation, "operation:save", "operation:v3", "operation.abi:v7"),
      (Workflow, "workflow:save", "workflow:v4", "workflow.abi:v8"), (StateMachine, "state:store", "state:v5", "state.abi:v9"))
      .map { case (kind, element, version, abi) => TargetContract(kind, _real_id(element), _component("Store"), version, abi, _real_source("target:" + element)) }
    Scope("explicit-real-parser-scope", _real_source("scope:r1"), models, Vector(term), Vector(relation), Vector(profile), Vector.empty, history, targets)
  }
}
