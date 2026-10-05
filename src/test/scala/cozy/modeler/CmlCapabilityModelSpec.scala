package cozy.modeler

import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import CmlSemanticFoundation.{ModelElementId, TermId, Presence, Present, Absent, NotDeclared, NotRepresented, Unsupported, NotApplicable, External}
import CmlCapabilityModel._
import CmlCapabilitySource._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityModelSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Immutable parsed Capability carriers" should {
    "retain distinct qualified domains and explicit facts" which {
      "keep source revision, name, contract and edge facts independent when copied" in {
        Given("a complete parsed Application with an explicit gap and a distinct equal-component Term identity")
        val identity = ModelElementId("Model:α/A", "Capability:β|B")
        val term = TermId(identity.modelId, identity.elementId)
        val source = CapabilitySource(identity.modelId, "model/input.cml", "revision:一", None)
        val original = ApplicationCapability(identity, "contract:v1", "Name", Present("Summary"), Absent(NotDeclared, "No description."), source,
          Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty))
        When("a consumer copies presentation and independent revision domains")
        val changed = original.copy(name = "Changed", source = source.copy(sourceRevision = "revision:二"))
        val reference = VersionedComponentCapabilityReference(identity, "contract:v1")
        Then("the original stays immutable and the typed family and qualified-domain distinctions remain observable")
        original.name shouldBe "Name"
        original.source.sourceRevision shouldBe "revision:一"
        changed.identity shouldBe original.identity
        changed.contractVersion shouldBe original.contractVersion
        original.demandReferences shouldBe Present(Vector.empty)
        original.description shouldBe Absent(NotDeclared, "No description.")
        identity should not equal (term)
        original.kind shouldBe ApplicationKind
        reference.kind shouldBe ComponentKind
      }

      "C64P24 preserve literal identities, independent revisions and ordered input and edge permutations" in {
        Given("valid opaque literal IDs, independent names/revisions and independently permuted documents and edges")
        val samples = for {
          suffix <- Gen.chooseNum(1, 100000)
          literal <- Gen.oneOf("Case:α/β|x", "case:é|e\u0301", "字:能力/引用", "quoted\"id\\literal")
          revision <- Gen.chooseNum(1, 100000)
          name <- Gen.oneOf("Same name", "別の表示", "Name = value")
          order <- Gen.pick(3, Vector("target:一", "target:二", "target:三")).map(_.toVector)
          reverse <- Gen.oneOf(true, false)
        } yield (suffix, literal, revision, name, order, reverse)
        When("ScalaCheck parses actual generated CML and normalizes both input orders repeatedly")
        // Unconstrained tuple shrinking leaves this positive generator domain.
        val property = Prop.forAllNoShrink(samples) { case (suffix, literal, revision, name, order, reverse) =>
          val scope = SourceScope(s"Model:$suffix|α", s"model/source$suffix.cml")
          val other = SourceScope(s"Other:$suffix|β", s"model/other$suffix.cml")
          val sources = Vector(scope -> _cml(scope, literal, s"revision:$revision", name, _present_json(JsString("Summary")), _declared_json, order),
            other -> _cml(other, literal, s"other-revision:${revision + 1}", name, _present_json(JsString("Summary")), _declared_json, order.reverse))
          val inputs = (if (reverse) sources.reverse else sources).map { case (owner, source) =>
            SourceInput(owner, KaleidoxModel.parse(KaleidoxConfig.default, source))
          }
          val result = CmlCapabilityCml.normalize(inputs)
          val changed = CmlCapabilityCml.normalize(Vector(SourceInput(scope, KaleidoxModel.parse(KaleidoxConfig.default,
            _cml(scope, literal, s"new-revision:$revision", "Changed name", _present_json(JsString("Summary")), _declared_json, order)))))
          (result, changed) match {
            case (Right(documents), Right(Vector(updated))) =>
              val first = documents.find(_.scope == scope).get.model.applicationCapabilities.head
              val second = updated.model.applicationCapabilities.head
              Prop(documents.map(_.scope) == inputs.map(_.scope) && CmlCapabilityCml.normalize(inputs) == result &&
                CmlCapabilityCml.normalize(inputs.reverse) == Right(documents.reverse) &&
                first.identity == ModelElementId(scope.modelAuthority, literal) &&
                second == first.copy(name = "Changed name", source = first.source.copy(sourceRevision = s"new-revision:$revision")) &&
                first.source.sourceRevision == s"revision:$revision" && first.contractVersion == "contract:opaque" &&
                _links(first.demandReferences).map(_.target.identity.elementId) == order &&
                _links(first.demandReferences).map(_.source.sourceRevision) == Vector("edge:r0", "edge:r1", "edge:r2") &&
                documents.forall(_.model.componentCapabilities.isEmpty))
                .label(s"normalized=$result, changed=$changed, parserErrors=${inputs.map(input => input.scope -> input.model.errors)}")
            case _ => Prop.falsified.label(s"normalized=$result, changed=$changed, parserErrors=${inputs.map(input => input.scope -> input.model.errors)}")
          }
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least one hundred active samples preserve literal domains, determinism, independence and exact ordered facts")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }

      "C64P25 preserve Presence and Origin branches while consistently rejecting invalid identity and source facts" in {
        Given("sampled absence reasons and declared/derived branches alongside invalid IDs, paths, revisions and malformed branches")
        val samples = for {
          reason <- Gen.oneOf("NotDeclared", "NotRepresented", "Unsupported", "NotApplicable")
          derived <- Gen.oneOf(true, false)
          invalidtext <- Gen.oneOf("", " leading", "trailing\u00a0", "bad\u0001control")
          invalidpath <- Gen.oneOf("/absolute", "C:/drive", "uri:scheme", "a//b", "a/./b", "a/../b", "a\\b")
        } yield (reason, derived, invalidtext, invalidpath)
        When("ScalaCheck normalizes real CML with explicit branches and one invalid boundary at a time")
        val property = Prop.forAll(samples) { case (reason, derived, invalidtext, invalidpath) =>
          val scope = SourceScope("Model:valid", "model/valid.cml")
          val gap = Json.obj("state" -> "ABSENT", "reason" -> reason, "detail" -> s"Explicit $reason.")
          val origin = if (derived) _derived_json else _declared_json
          val source = _cml(scope, "Capability:valid", "revision:valid", "Name", gap, origin, Vector("target:valid"))
          val valid = _normalize(scope, source)
          val empty = _normalize(scope, _cml(scope, "Capability:valid", "revision:valid", "Name",
            _present_json(JsString("Summary")), origin, Vector.empty))
          val invalids = Vector(
            _normalize(scope, _cml(scope, invalidtext, "revision:valid", "Name", gap, origin, Vector.empty)),
            _normalize(scope.copy(path = invalidpath), source),
            _normalize(scope, _cml(scope, "Capability:valid", invalidtext, "Name", gap, origin, Vector.empty)),
            _normalize(scope, _cml(scope, "Capability:valid", "revision:valid", "Name", JsNull, origin, Vector.empty)),
            _normalize(scope, _cml(scope, "Capability:valid", "revision:valid", "Name",
              Json.obj("state" -> "ABSENT", "reason" -> "UNKNOWN", "detail" -> "Gap"), origin, Vector.empty)),
            _normalize(scope, _cml(scope, "Capability:valid", "revision:valid", "Name", gap,
              Json.obj("kind" -> "DERIVED", "ruleId" -> "rule", "inputs" -> Json.arr()), Vector("target:valid")))
          )
          (valid, empty) match {
            case (Right(Vector(document)), Right(Vector(emptydocument))) =>
              val application = document.model.applicationCapabilities.head
              val expectedreason = reason match {
                case "NotDeclared" => NotDeclared
                case "NotRepresented" => NotRepresented
                case "Unsupported" => Unsupported
                case "NotApplicable" => NotApplicable
              }
              val expectedorigin: CapabilityOrigin = if (derived)
                Derived("rule:literal", Vector(CapabilitySource("Foreign:authority", "foreign/input.cml", "foreign:r9", None),
                  CapabilitySource("Model:valid", "model/valid.cml", "evidence:r2", Some(7)))) else Declared
              application.summary == Absent(expectedreason, s"Explicit $reason.") &&
                _links(application.demandReferences).head.origin == expectedorigin &&
                _links(application.demandReferences).head.target.boundary == External &&
                emptydocument.model.applicationCapabilities.head.demandReferences == Present(Vector.empty) &&
                invalids.forall(_.isLeft)
            case _ => false
          }
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least one hundred active samples retain exact gap/origin facts and every malformed source remains Left")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }

  private def _present_json(value: JsValue): JsObject = Json.obj("state" -> "PRESENT", "value" -> value)
  private def _declared_json: JsObject = Json.obj("kind" -> "DECLARED")
  private def _derived_json: JsObject = Json.obj("kind" -> "DERIVED", "ruleId" -> "rule:literal", "inputs" -> Json.arr(
    Json.obj("modelAuthority" -> "Foreign:authority", "path" -> "foreign/input.cml", "sourceRevision" -> "foreign:r9", "line" -> JsNull),
    Json.obj("modelAuthority" -> "Model:valid", "path" -> "model/valid.cml", "sourceRevision" -> "evidence:r2", "line" -> 7)))
  private def _cml(scope: SourceScope, element: String, revision: String, name: String, summary: JsValue, origin: JsValue, targets: Vector[String]): String = {
    def _json_(value: JsValue): String = {
      // Equivalent JSON escapes accommodate original LogicalLines quote parsing while preserving decoded opaque IDs.
      """\\(["\\])""".r.replaceAllIn(Json.stringify(value), matched =>
        java.util.regex.Matcher.quoteReplacement(if (matched.group(1) == "\"") "\\u0022" else "\\u005c"))
    }
    def _source_(rev: String): JsObject = Json.obj("modelAuthority" -> scope.modelAuthority, "path" -> scope.path, "sourceRevision" -> rev, "line" -> JsNull)
    val links = targets.zipWithIndex.map { case (target, index) => Json.obj("role" -> "USE_CASE", "target" -> Json.obj(
      "identity" -> Json.obj("modelId" -> "Target:scope", "elementId" -> target), "boundary" -> "EXTERNAL"), "source" -> _source_(s"edge:r$index"), "origin" -> origin) }
    val empty = _present_json(Json.arr())
    val fields = Vector("identity" -> Json.obj("modelId" -> scope.modelAuthority, "elementId" -> element),
      "contractVersion" -> JsString("contract:opaque"), "name" -> JsString(name), "summary" -> summary,
      "description" -> _present_json(JsString("Description")), "source" -> _source_(revision), "demandReferences" -> _present_json(JsArray(links)),
      "discoveryReferences" -> empty, "componentMappings" -> empty, "dependencies" -> empty, "grounding" -> empty, "traceability" -> empty)
    "# CAPABILITY-MODEL\ncompatibility = {\"state\":\"PRESENT\",\"value\":[]}\n\n## APPLICATION-CAPABILITY\n### Label\n" +
      fields.map { case (key, value) => s"$key = ${_json_(value)}" }.mkString("\n") + "\n"
  }
  private def _normalize(scope: SourceScope, source: String): Either[Vector[SyntaxIssue], Vector[CapabilityDocument]] =
    CmlCapabilityCml.normalize(Vector(SourceInput(scope, KaleidoxModel.parse(KaleidoxConfig.default, source))))
  private def _links(value: Presence[Vector[ModelLink]]): Vector[ModelLink] = value match {
    case Present(links) => links
    case _ => Vector.empty
  }
}
