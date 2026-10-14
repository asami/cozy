package cozy.modeler

import java.nio.file.Files
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import cozy.CozyCapabilityProductionSpec._
import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope.ScopeInput
import CmlSemanticFoundation._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityApplicationProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Portable Application Capability product (spec:R13)" should {
    "retain the original ordered application model" which {
      "C64A01 export every original definition and source alongside independent Component metadata" in {
        _with_root { root =>
          Given("the two original source files and their independently supplied request headers")
          _copy_sources(root)
          val prepared = _prepare(root, _request(multiple = true))
          When("actual complete production publishes the portable Application artifact")
          _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          val json = _json(root.resolve("output/" + prepared.request.applicationoutput))
          val documents = (json \ "documents").as[Vector[JsObject]]
          Then("every original document and ordered Application definition survives in the closed v1 product")
          json.keys shouldBe Set("schemaVersion", "generatedAbiVersion", "producer", "documents")
          (json \ "schemaVersion").as[Int] shouldBe 1
          (json \ "generatedAbiVersion").as[String] shouldBe "cozy.application-capability.v1"
          documents.size shouldBe prepared.generated.validated.documents.size
          documents.zip(prepared.generated.validated.documents).foreach { case (exported, original) =>
            (exported \ "applicationCapabilities").as[Vector[JsObject]].map(x => (x \ "identity").as[JsObject]) shouldBe original.model.applicationCapabilities.map(x => _id_json(x.identity))
            (exported \ "applicationCapabilities").as[Vector[JsObject]].foreach(_.keys shouldBe Set("kind", "identity", "contractVersion", "name", "summary",
              "description", "source", "demandReferences", "discoveryReferences", "componentMappings", "dependencies", "grounding", "traceability"))
          }
          documents.map(x => (x \ "source").as[JsObject]) shouldBe prepared.request.sources.map(_source_json)
          prepared.request.packages.foreach(x => Files.exists(root.resolve("output/" + x.path + "/meta/component-capability/index.json")) shouldBe true)
        }
      }
      "C64A02 retain literal identities, revisions, line values and actual producer independently" in {
        _with_root { root =>
          Given("literal Unicode authorities and distinct document and definition source revisions")
          _copy_sources(root)
          val prepared = _prepare(root, _request(multiple = true))
          When("the validated Application product is encoded")
          val json = _right(CmlCapabilityApplicationProjection.encode(prepared.generated, prepared.sources, prepared.request.producer))
          val documents = (json \ "documents").as[Vector[JsObject]]
          Then("no identity or revision becomes a filename, digest or executing package version alias")
          documents.map(x => (x \ "source" \ "modelAuthority").as[String]) shouldBe Vector("Model:能力/A", "Other:能力/B")
          documents.map(x => (x \ "source" \ "line").get) shouldBe Vector(JsNull, JsNumber(2))
          documents.map(x => (x \ "source" \ "sourceRevision").as[String]) shouldBe Vector("header:primary-r42", "header:secondary-r43")
          val definitions = (documents.head \ "applicationCapabilities").as[Vector[JsObject]]
          definitions.head \ "identity" shouldBe (Json.obj("identity" -> _id_json(prepared.generated.validated.documents.head.model.applicationCapabilities.head.identity)) \ "identity")
          (definitions.head \ "source" \ "line").as[Int] shouldBe 12
          (definitions.head \ "source" \ "sourceRevision").as[String] shouldBe "definition-r1"
          (json \ "producer").as[JsObject] shouldBe Json.obj("id" -> "cozy", "version" -> org.simplemodeling.cozy.BuildInfo.version)
        }
      }
      "C64A03 preserve authored demand and discovery without inverse UseCase edges" in {
        _with_root { root =>
          Given("original independent demand and discovery edge sets with explicit UseCase and Goal roles")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          When("the portable Application product is encoded")
          val json = _right(CmlCapabilityApplicationProjection.encode(prepared.generated, prepared.sources, prepared.request.producer))
          val application = _applications(json)(1)
          Then("authored edge directions and order survive and no UseCase forward-demand projection is emitted")
          (application \ "demandReferences" \ "value").as[Vector[JsObject]].map(x => (x \ "role").as[String]) shouldBe Vector("USE_CASE", "GOAL")
          (application \ "discoveryReferences" \ "value").as[Vector[JsObject]].map(x => (x \ "role").as[String]) shouldBe Vector("SCENARIO", "COMPONENT")
          (application \ "demandReferences" \ "value").as[Vector[JsObject]].map(x => (x \ "target" \ "identity" \ "elementId").as[String]) shouldBe Vector("usecase:one", "goal:two")
          (json \\ "requiredApplications") shouldBe empty
          (json \\ "useCases") shouldBe empty
        }
      }
      "C64A04 retain every versioned mapping and dependency endpoint and source" in {
        _with_root { root =>
          Given("original Application-to-Component mappings and independent Application dependencies")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          val original = prepared.generated.validated.documents.head.model.applicationCapabilities(1)
          When("the whole Application definition is exported")
          val application = _applications(_right(CmlCapabilityApplicationProjection.encode(prepared.generated, prepared.sources, prepared.request.producer)))(1)
          val mappings = (application \ "componentMappings" \ "value").as[Vector[JsObject]]
          val dependencies = (application \ "dependencies" \ "value").as[Vector[JsObject]]
          Then("each full target record preserves its family, identity, contract, boundary, source and origin")
          mappings.zip(_items(original.componentMappings)).foreach { case (json, edge) =>
            json.keys shouldBe Set("target", "boundary", "source", "origin")
            (json \ "target").as[JsObject] shouldBe Json.obj("kind" -> "COMPONENT", "identity" -> _id_json(edge.target.identity), "contractVersion" -> edge.target.contractVersion)
            (json \ "boundary").as[String] shouldBe (if (edge.boundary == Local) "LOCAL" else "EXTERNAL")
            (json \ "source").as[JsObject] shouldBe _source_json(edge.source)
            (json \ "origin" \ "kind").as[String] shouldBe (if (edge.origin == CmlCapabilitySource.Declared) "DECLARED" else "DERIVED")
          }
          dependencies.zip(_items(original.dependencies)).foreach { case (json, edge) =>
            json.keys shouldBe Set("target", "boundary", "source", "origin")
            (json \ "target").as[JsObject] shouldBe Json.obj("kind" -> "APPLICATION", "identity" -> _id_json(edge.target.identity), "contractVersion" -> edge.target.contractVersion)
            (json \ "source").as[JsObject] shouldBe _source_json(edge.source)
          }
          mappings.size shouldBe _items(original.componentMappings).size
          dependencies.size shouldBe _items(original.dependencies).size
        }
      }
    }
    "preserve gaps and independently grounded evidence" which {
      "C64A05 distinguish Present empty, all four absence reasons and explicit nulls" in {
        _with_root { root =>
          Given("actual validated original documents with four separately supplied absence variants")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          val reasons = Vector(NotDeclared, NotRepresented, CmlSemanticFoundation.Unsupported, NotApplicable)
          val variants = reasons.zipWithIndex.map { case (reason, index) =>
            val original = prepared.generated.validated.documents.head
            val app = original.model.applicationCapabilities.head.copy(summary = Absent(reason, "Independent gap."),
              demandReferences = Present(Vector.empty), source = original.model.applicationCapabilities.head.source.copy(line = if (index % 2 == 0) None else Some(9)))
            val document = original.copy(model = original.model.copy(applicationCapabilities = app +: original.model.applicationCapabilities.tail))
            _project(prepared, Vector(document))
          }
          When("the actual validated variants are encoded")
          val applications = variants.map(x => _applications(_right(CmlCapabilityApplicationProjection.encode(x, prepared.sources, prepared.request.producer))).head)
          Then("all reasons/details, present emptiness and null or positive source line remain distinct")
          applications.zipWithIndex.foreach { case (app, index) =>
            (app \ "summary").as[JsObject] shouldBe Json.obj("state" -> "ABSENT", "reason" -> reasons(index).toString, "detail" -> "Independent gap.")
            (app \ "demandReferences").as[JsObject] shouldBe Json.obj("state" -> "PRESENT", "value" -> JsArray())
            (app \ "source" \ "line").get shouldBe (if (index % 2 == 0) JsNull else JsNumber(9))
          }
        }
      }
      "C64A06 retain MODEL and TERM grounding with independent vocabulary and evidence sources" in {
        _with_root { root =>
          Given("real grounding, foreign ordered derivation inputs and independent Specification/Evidence edges")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          When("the complete original Application is exported")
          val app = _applications(_right(CmlCapabilityApplicationProjection.encode(prepared.generated, prepared.sources, prepared.request.producer)))(1)
          val grounding = (app \ "grounding" \ "value").as[Vector[JsObject]]
          val trace = (app \ "traceability" \ "value").as[Vector[JsObject]]
          Then("all domain tags, relations, profiles, labels and ordered attributed evidence remain independent")
          grounding.map(x => (x \ "target" \ "kind").as[String]) shouldBe Vector("MODEL", "TERM")
          (grounding.head \ "relation" \ "value").as[JsObject] shouldBe Json.obj("vocabularyId" -> "vocab:关系", "relationId" -> "rel:uses|α")
          (grounding.head \ "profile" \ "value").as[JsObject] shouldBe Json.obj("vocabularyId" -> "profile:scope", "profileId" -> "profile:one")
          (grounding.head \ "preferredLabel" \ "value").as[JsObject] shouldBe Json.obj("text" -> "注文", "language" -> JsNull)
          (grounding.last \ "origin" \ "inputs").as[Vector[JsObject]].map(x => (x \ "sourceRevision").as[String]) shouldBe Vector("foreign-edge-r9", "evidence-r2")
          trace.map(x => (x \ "role").as[String]) shouldBe Vector("SPECIFICATION", "EVIDENCE")
          trace.map(x => (x \ "source" \ "sourceRevision").as[String]) shouldBe Vector("spec-r3", "evidence-r4")
        }
      }
      "C64A07 retain app-only and genuinely empty documents and reject fabricated header coverage" in {
        _with_root { root =>
          Given("one actual app-only CML document and one genuinely empty CML document")
          _write(root.resolve(_secondary_source.path), _secondary_text.split("## COMPONENT-CAPABILITY")(0))
          _write(root.resolve("model/empty.cml"), "# CAPABILITY-MODEL\ncompatibility = {\"state\":\"PRESENT\",\"value\":[]}\n")
          val emptysource = CapabilitySource("Empty:document", "model/empty.cml", "empty:r1", None)
          val request = _request().copy(sources = Vector(_secondary_source, emptysource), contexts = Vector.empty, packages = Vector.empty, scalaoutputs = Vector.empty)
          When("actual typed acquisition prepares and publishes both documents and the encoder checks bad header selections")
          val prepared = _prepare(root, request)
          _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          val json = _json(root.resolve("output/" + request.applicationoutput))
          val invalid = Vector(request.sources.tail, request.sources ++ request.sources, request.sources.reverse).map(x =>
            CmlCapabilityApplicationProjection.encode(prepared.generated, x, request.producer))
          Then("both original documents survive and missing, repeated or reordered headers cannot fabricate coverage")
          (json \ "documents").as[Vector[JsObject]].map(x => (x \ "applicationCapabilities").as[Vector[JsObject]].size) shouldBe Vector(1, 0)
          (json \ "documents").as[Vector[JsObject]].map(x => (x \ "source").as[JsObject]) shouldBe request.sources.map(_source_json)
          prepared.generated.projections shouldBe empty
          invalid.foreach(_.isLeft shouldBe true)
        }
      }
      "C64A08 preserve Unicode identity, independent revisions and Presence in one hundred active exports" in {
        _with_root { root =>
          Given("the actual original app-only definition and unfiltered independent literal identity, revision and gap values")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          val original = prepared.generated.validated.documents.head
          val app = original.model.applicationCapabilities.head
          val literals = Gen.listOf(Gen.oneOf("日本語", "é", "β", "|:=", "🙂", "\"", "\\")).map(x => "literal:" + x.mkString + ":end")
          val reasons = Gen.oneOf(NotDeclared, NotRepresented, CmlSemanticFoundation.Unsupported, NotApplicable)
          val property = Prop.forAll(Gen.zip(literals, literals, reasons, Gen.oneOf(true, false))) { case (identity, revision, reason, present) =>
            val variant = app.copy(identity = ModelElementId(app.identity.modelId, identity), contractVersion = "contract:" + revision,
              name = identity, source = app.source.copy(sourceRevision = revision), summary = if (present) Present(identity) else Absent(reason, revision))
            val document = original.copy(model = CapabilityModel(Vector(variant), Vector.empty, Vector.empty, Present(Vector.empty)))
            CmlCapabilityValidation.validate(Vector(document), prepared.request.scope) match {
              case Left(_) => false
              case Right(validated) => CmlCapabilityGeneration.project(validated, Vector.empty) match {
                case Left(_) => false
                case Right(generated) => CmlCapabilityApplicationProjection.encode(generated, prepared.sources, prepared.request.producer) match {
                  case Left(_) => false
                  case Right(json) =>
                    val result = _applications(json).head
                    (result \ "identity" \ "elementId").as[String] == identity && (result \ "name").as[String] == identity &&
                      (result \ "source" \ "sourceRevision").as[String] == revision && (result \ "contractVersion").as[String] == "contract:" + revision &&
                      (result \ "summary").as[JsObject] == (if (present) Json.obj("state" -> "PRESENT", "value" -> identity)
                        else Json.obj("state" -> "ABSENT", "reason" -> reason.toString, "detail" -> revision))
                }
              }
            }
          }
          When("ScalaCheck evaluates the real validation, projection and Application serialization without filters")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("one hundred successful active samples retain independent original semantic values with zero discards")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }
    }
  }
  private def _applications(json: JsObject): Vector[JsObject] = (json \ "documents").as[Vector[JsObject]].flatMap(x => (x \ "applicationCapabilities").as[Vector[JsObject]])
  private def _items[A](presence: Presence[Vector[A]]): Vector[A] = presence match { case Present(items) => items; case _: Absent => Vector.empty }
  private def _project(prepared: CmlCapabilityProduction.Prepared, documents: Vector[CapabilityDocument]): CmlCapabilityGeneration.Generated =
    _right(CmlCapabilityGeneration.project(_right(CmlCapabilityValidation.validate(documents, prepared.request.scope)), prepared.request.contexts))
}
