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
import CmlCapabilityProductionRequest._
import CmlSemanticFoundation._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityComponentUsageProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Portable Component Usage Capability product (spec:R13)" should {
    "retain the original ordered usage model" which {
      "C64A01 export only selected owner records and exact contributing sources" in {
        _with_root { root =>
          Given("the two original source files and their independently supplied request headers")
          _copy_sources(root)
          val original = _request(multiple = true)
          val request = original.copy(packages = original.packages.updated(0,
            original.packages.head.copy(usagecapabilities = original.packages.head.usagecapabilities.reverse)))
          val prepared = _prepare(root, request)
          When("actual complete production publishes the portable Usage package")
          _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          val exported = prepared.request.packages.map(bundle => bundle -> _json(root.resolve("output/" + _usage_path(bundle))))
          Then("each package contains only its exact selected owner records with full contributing original headers")
          exported.foreach { case (bundle, json) =>
            json.keys shouldBe Set("schemaVersion", "generatedAbiVersion", "producer", "component", "package", "documents")
            (json \ "schemaVersion").as[Int] shouldBe 1
            (json \ "generatedAbiVersion").as[String] shouldBe "cozy.component-usage-capability.v1"
            (json \ "component").as[JsObject] shouldBe _coordinate_json(bundle.component)
            (json \ "package").as[JsObject] shouldBe _package_json(bundle.packagecoordinate)
            val expected = prepared.generated.validated.documents.zip(prepared.sources).flatMap { case (document, source) =>
              val definitions = document.model.componentUsageCapabilities.filter(x => bundle.usagecapabilities.contains(
                VersionedComponentUsageCapabilityReference(x.identity, x.contractVersion)))
              if (definitions.isEmpty) Vector.empty else Vector(source -> definitions)
            }
            val documents = (json \ "documents").as[Vector[JsObject]]
            documents.map(x => (x \ "source").as[JsObject]) shouldBe expected.map(x => _source_json(x._1))
            documents.zip(expected).foreach { case (document, (_, definitions)) =>
              val records = (document \ "componentUsageCapabilities").as[Vector[JsObject]]
              records.map(x => (x \ "identity").as[JsObject]) shouldBe definitions.map(x => _id_json(x.identity))
              records.foreach { record =>
                record.keys shouldBe Set("kind", "component", "identity", "contractVersion", "name", "summary", "description",
                  "source", "demandReferences", "discoveryReferences", "componentMappings", "dependencies", "grounding", "traceability")
                (record \ "kind").as[String] shouldBe "COMPONENT_USAGE"
                (record \ "component").as[JsObject] shouldBe _component_json(bundle.component.component)
              }
            }
            Files.exists(root.resolve("output/" + bundle.path + "/" + METADATA_PREFIX + "index.json")) shouldBe true
          }
          Given("the same original Generated and a package explicitly selecting only its last versioned Usage record")
          val bundle = prepared.request.packages.head
          val subset = bundle.copy(usagecapabilities = Vector(bundle.usagecapabilities.head))
          When("the existing owner projection encodes that exact subset")
          val selected = _right(CmlCapabilityComponentUsageProjection.encode(prepared.generated, prepared.sources, prepared.request.producer, subset))
          Then("unselected same-owner records need not be published and selection does not copy another owner")
          _usages(selected).map(x => (x \ "identity").as[JsObject]) shouldBe subset.usagecapabilities.map(x => _id_json(x.identity))
        }
      }
      "C64A02 retain literal identities, revisions, line values and actual producer independently" in {
        _with_root { root =>
          Given("literal Unicode authorities and distinct document and definition source revisions")
          _copy_sources(root)
          _write(root.resolve(_secondary_source.path), _secondary_text.replace("\"id\":\"Remote\"", "\"id\":\"Store\""))
          val original = _request(multiple = true)
          val bundled = original.packages.head.copy(usagecapabilities = original.packages.head.usagecapabilities ++ original.packages.last.usagecapabilities)
          val request = original.copy(packages = original.packages.updated(0, bundled).updated(2, original.packages.last.copy(usagecapabilities = Vector.empty)))
          val prepared = _prepare(root, request)
          When("the validated Usage product is encoded")
          val json = _right(CmlCapabilityComponentUsageProjection.encode(prepared.generated, prepared.sources, prepared.request.producer, prepared.request.packages.head))
          val documents = (json \ "documents").as[Vector[JsObject]]
          Then("no identity or revision becomes a filename, digest or executing package version alias")
          documents.map(x => (x \ "source" \ "modelAuthority").as[String]) shouldBe Vector("Model:能力/A", "Other:能力/B")
          documents.map(x => (x \ "source" \ "line").get) shouldBe Vector(JsNull, JsNumber(2))
          documents.map(x => (x \ "source" \ "sourceRevision").as[String]) shouldBe Vector("header:primary-r42", "header:secondary-r43")
          val definitions = (documents.head \ "componentUsageCapabilities").as[Vector[JsObject]]
          definitions.head \ "identity" shouldBe (Json.obj("identity" -> _id_json(prepared.generated.validated.documents.head.model.componentUsageCapabilities.head.identity)) \ "identity")
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
          When("the portable Usage product is encoded")
          val json = _right(CmlCapabilityComponentUsageProjection.encode(prepared.generated, prepared.sources, prepared.request.producer, prepared.request.packages.head))
          val usage = _usages(json)(1)
          Then("authored edge directions and order survive and no UseCase forward-demand projection is emitted")
          (usage \ "demandReferences" \ "value").as[Vector[JsObject]].map(x => (x \ "role").as[String]) shouldBe Vector("USE_CASE", "GOAL")
          (usage \ "discoveryReferences" \ "value").as[Vector[JsObject]].map(x => (x \ "role").as[String]) shouldBe Vector("SCENARIO", "COMPONENT")
          (usage \ "demandReferences" \ "value").as[Vector[JsObject]].map(x => (x \ "target" \ "identity" \ "elementId").as[String]) shouldBe Vector("usecase:one", "goal:two")
          (json \\ "requiredComponentUsages") shouldBe empty
          (json \\ "useCases") shouldBe empty
        }
      }
      "C64A04 retain every versioned mapping and dependency endpoint and source" in {
        _with_root { root =>
          Given("original Usage-to-Component mappings and independent Usage dependencies")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          val original = prepared.generated.validated.documents.head.model.componentUsageCapabilities(1)
          When("the whole Usage definition is exported")
          val usage = _usages(_right(CmlCapabilityComponentUsageProjection.encode(prepared.generated, prepared.sources, prepared.request.producer, prepared.request.packages.head)))(1)
          val mappings = (usage \ "componentMappings" \ "value").as[Vector[JsObject]]
          val dependencies = (usage \ "dependencies" \ "value").as[Vector[JsObject]]
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
            (json \ "target").as[JsObject] shouldBe Json.obj("kind" -> "COMPONENT_USAGE", "identity" -> _id_json(edge.target.identity), "contractVersion" -> edge.target.contractVersion)
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
            val usage = original.model.componentUsageCapabilities.head.copy(summary = Absent(reason, "Independent gap."),
              demandReferences = Present(Vector.empty), source = original.model.componentUsageCapabilities.head.source.copy(line = if (index % 2 == 0) None else Some(9)))
            val document = original.copy(model = original.model.copy(componentUsageCapabilities = usage +: original.model.componentUsageCapabilities.tail))
            _project(prepared, Vector(document))
          }
          When("the actual validated variants are encoded")
          val usages = variants.map(x => _usages(_right(CmlCapabilityComponentUsageProjection.encode(x, prepared.sources, prepared.request.producer, prepared.request.packages.head))).head)
          Then("all reasons/details, present emptiness and null or positive source line remain distinct")
          usages.zipWithIndex.foreach { case (app, index) =>
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
          When("the complete original Usage is exported")
          val app = _usages(_right(CmlCapabilityComponentUsageProjection.encode(prepared.generated, prepared.sources, prepared.request.producer, prepared.request.packages.head)))(1)
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
      "C64A07 publish a Usage-only Component and reject fabricated header coverage" in {
        _with_root { root =>
          Given("one actual Usage-only CML source, one empty source and an explicit Remote package without Component resources or contexts")
          _write(root.resolve(_secondary_source.path), _secondary_text.split("## COMPONENT-CAPABILITY")(0))
          _write(root.resolve("model/empty.cml"), "# CAPABILITY-MODEL\ncompatibility = {\"state\":\"PRESENT\",\"value\":[]}\n")
          val emptysource = CapabilitySource("Empty:document", "model/empty.cml", "empty:r1", None)
          val remote = _request(multiple = true).packages.last.copy(resources = Vector.empty)
          val request = _request().copy(sources = Vector(_secondary_source, emptysource), contexts = Vector.empty,
            packages = Vector(remote), scalaoutputs = Vector.empty)
          When("actual production publishes the selected package and the encoder receives incomplete, repeated and reordered headers")
          val prepared = _prepare(root, request)
          _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          val json = _json(root.resolve("output/" + _usage_path(remote)))
          val invalid = Vector(request.sources.tail, request.sources ++ request.sources, request.sources.reverse).map(x =>
            CmlCapabilityComponentUsageProjection.encode(prepared.generated, x, request.producer, remote))
          val emptyprojection = _right(CmlCapabilityComponentUsageProjection.encode(prepared.generated, request.sources, request.producer,
            remote.copy(usagecapabilities = Vector.empty)))
          Then("only the contributing document is exported and no Component projection, context or native declaration is invented")
          (json \ "documents").as[Vector[JsObject]].map(x => (x \ "source").as[JsObject]) shouldBe Vector(_source_json(_secondary_source))
          (emptyprojection \ "documents").as[Vector[JsObject]] shouldBe Vector.empty[JsObject]
          _json(root.resolve("output/" + remote.path + "/" + METADATA_PREFIX + "index.json")) shouldBe Json.obj("schemaVersion" -> 1, "projections" -> Json.arr())
          prepared.generated.projections shouldBe empty
          prepared.generated.validated.documents.flatMap(_.model.componentDeclarations) shouldBe empty
          invalid.foreach(_.isLeft shouldBe true)
        }
      }
      "C64A08 preserve Unicode identity, independent revisions and Presence in one hundred active exports" in {
        _with_root { root =>
          Given("the actual original Usage-only definition, unfiltered literal identity, revision and gap values and an exact per-sample versioned package selection")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          val original = prepared.generated.validated.documents.head
          val usage = original.model.componentUsageCapabilities.head
          val literals = Gen.listOf(Gen.oneOf("日本語", "é", "β", "|:=", "🙂", "\"", "\\")).map(x => "literal:" + x.mkString + ":end")
          val reasons = Gen.oneOf(NotDeclared, NotRepresented, CmlSemanticFoundation.Unsupported, NotApplicable)
          val property = Prop.forAll(Gen.zip(literals, literals, reasons, Gen.oneOf(true, false))) { case (identity, revision, reason, present) =>
            val variant = usage.copy(identity = ModelElementId(usage.identity.modelId, identity), contractVersion = "contract:" + revision,
              name = identity, source = usage.source.copy(sourceRevision = revision), summary = if (present) Present(identity) else Absent(reason, revision))
            val document = original.copy(model = CapabilityModel(Vector(variant), Vector.empty, Vector.empty, Present(Vector.empty)))
            val selection = prepared.request.packages.head.copy(usagecapabilities =
              Vector(VersionedComponentUsageCapabilityReference(variant.identity, variant.contractVersion)))
            CmlCapabilityValidation.validate(Vector(document), prepared.request.scope) match {
              case Left(_) => false
              case Right(validated) => CmlCapabilityGeneration.project(validated, Vector.empty) match {
                case Left(_) => false
                case Right(generated) => CmlCapabilityComponentUsageProjection.encode(generated, prepared.sources, prepared.request.producer, selection) match {
                  case Left(_) => false
                  case Right(json) =>
                    val result = _usages(json).head
                    (result \ "identity" \ "elementId").as[String] == identity && (result \ "name").as[String] == identity &&
                      (result \ "source" \ "sourceRevision").as[String] == revision && (result \ "contractVersion").as[String] == "contract:" + revision &&
                      (result \ "summary").as[JsObject] == (if (present) Json.obj("state" -> "PRESENT", "value" -> identity)
                        else Json.obj("state" -> "ABSENT", "reason" -> reason.toString, "detail" -> revision))
                }
              }
            }
          }
          When("ScalaCheck evaluates the real validation, projection and Usage serialization without filters")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("one hundred successful active samples retain independent original semantic values with zero discards")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }
    }
  }
  private def _usages(json: JsObject): Vector[JsObject] = (json \ "documents").as[Vector[JsObject]].flatMap(x => (x \ "componentUsageCapabilities").as[Vector[JsObject]])
  private def _items[A](presence: Presence[Vector[A]]): Vector[A] = presence match { case Present(items) => items; case _: Absent => Vector.empty }
  private def _project(prepared: CmlCapabilityProduction.Prepared, documents: Vector[CapabilityDocument]): CmlCapabilityGeneration.Generated =
    _right(CmlCapabilityGeneration.project(_right(CmlCapabilityValidation.validate(documents, prepared.request.scope)), prepared.request.contexts))
}
