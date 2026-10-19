package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import org.goldenport.cncf.component.identity.{ComponentId, ComponentLocalId, ComponentNamespace}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope._
import CmlCapabilityGenerationContext._
import CmlSemanticFoundation.{ModelElementId, TermId, RelationId, ProfileId, Presence, Present, Absent, NotDeclared, NotRepresented, NotApplicable, Local, External, LocalizedLabel}
import CmlCapabilityGenerationSpec._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Validated Component Capability generation" should {
    "retain exact original models and the closed R11 wire" which {
      "C64G01 generate all explicitly selected snapshots from two original resources" in {
        Given("two separately loaded unchanged CML resources and explicit finite facts and three contexts")
        val inputs = _inputs
        val contexts = _contexts
        val documents = _documents(inputs)
        val validated = _validated(documents)
        When("the production composition normalizes, validates and projects those inputs")
        val generated = _right(CmlCapabilityGeneration.generate(inputs, ScopeInput.Ready(_scope), contexts))
        Then("all original Usage records, scope and bindings survive alongside canonical Component snapshots")
        generated.validated.documents shouldBe documents
        generated.validated.scope shouldBe validated.scope
        generated.validated.bindings shouldBe validated.bindings
        generated.validated.documents.flatMap(_.model.componentUsageCapabilities).size shouldBe 4
        generated.projections.map(_.snapshotKey) shouldBe Vector(contexts(2).snapshotKey, contexts(1).snapshotKey, contexts(0).snapshotKey)
        generated.projections.foreach { projection =>
          projection.json.fields.map(_._1) shouldBe Vector("schemaVersion", "generatedAbiVersion", "producer", "component", "package", "source", "capabilities", "declarations", "compatibility")
          Json.parse(projection.projectionJson) shouldBe projection.json
        }
      }

      "C64G02 preserve provided and required declarations and repeated realization keys" in {
        Given("the authored Store and Archive declarations with their independent provided and required directions")
        val inputs = _inputs
        When("the generator projects the selected Components")
        val generated = _generate(inputs)
        val store = _store(generated)
        val declarations = (store.json \ "declarations").as[Vector[JsObject]]
        Then("both directions, exact Java identity and versioned references remain visible")
        declarations.map(x => (x \ "direction").as[String]) shouldBe Vector("PROVIDED", "REQUIRED")
        declarations.foreach { declaration =>
          (declaration \ "component").as[JsObject] shouldBe Json.obj("namespace" -> "org.simplemodeling.textus", "id" -> "Store")
          (declaration \ "capability").as[JsObject] shouldBe Json.obj("kind" -> "COMPONENT", "identity" ->
            Json.obj("modelId" -> _primary_scope.modelAuthority, "elementId" -> "Component:Store|α"), "contractVersion" -> "contract:二")
        }
        val provided = declarations.head
        (provided \ "realizations" \ "value").as[Vector[JsObject]].foreach { edge =>
          (edge \ "declarationKey").as[JsObject] shouldBe Json.obj("component" -> (provided \ "component").as[JsObject],
            "direction" -> "PROVIDED", "capability" -> (provided \ "capability").as[JsObject])
        }
        _archive(generated).declarations.map(_.direction) shouldBe Vector(Provided)
      }

      "C64G03 retain all three realization target families and independent edge attribution" in {
        Given("Operation, Workflow and StateMachine target facts with separate contracts and ABIs")
        val inputs = _inputs
        When("the production generator projects the real declaration")
        val edges = (_store(_generate(inputs)).json \ "declarations").as[Vector[JsObject]].head
        val realizations = (edges \ "realizations" \ "value").as[Vector[JsObject]]
        Then("every target tuple, explicit Local or External boundary, source and origin survives")
        realizations.map(x => (x \ "targetKind").as[String]) shouldBe Vector("OPERATION", "WORKFLOW", "STATE_MACHINE")
        realizations.map(x => (x \ "target" \ "boundary").as[String]) shouldBe Vector("LOCAL", "EXTERNAL", "LOCAL")
        realizations.map(x => (x \ "target" \ "identity" \ "elementId").as[String]) shouldBe Vector("operation:save", "workflow:save", "state:store")
        realizations.map(x => (x \ "targetContractVersion").as[String]) shouldBe Vector("operation:v3", "workflow:v4", "state:v5")
        realizations.map(x => (x \ "generatedAbiVersion").as[String]) shouldBe Vector("operation.abi:v7", "workflow.abi:v8", "state.abi:v9")
        realizations.map(x => (x \ "source" \ "sourceRevision").as[String]) shouldBe Vector("realization-r15", "realization-r16", "realization-r17")
        realizations.foreach(x => (x \ "targetComponent").as[JsObject] shouldBe Json.obj("namespace" -> "org.simplemodeling.textus", "id" -> "Store"))
        realizations.map(x => (x \ "origin" \ "kind").as[String]) shouldBe Vector("DECLARED", "DERIVED", "DECLARED")
      }

      "C64G04 retain every definition and relationship record with exact closed keys" in {
        Given("the real definition with summary, absence, discovery, grounding and independent traces")
        val inputs = _inputs
        When("the generator produces the Component definition")
        val generated = _generate(inputs)
        val definition = _definition(_store(generated))
        val discovery = (definition \ "discoveryReferences" \ "value").as[Vector[JsObject]]
        val grounding = (definition \ "grounding" \ "value").as[Vector[JsObject]]
        Then("the complete closed records and authored values are retained without synthesizing edges")
        definition.fields.map(_._1) shouldBe Vector("kind", "identity", "contractVersion", "name", "summary", "description", "source", "discoveryReferences", "grounding", "traceability")
        (definition \ "kind").as[String] shouldBe "COMPONENT"
        (definition \ "name").as[String] shouldBe _store(generated).capabilities.head.name
        (definition \ "summary" \ "value").as[String] shouldBe "Summary = literal"
        (definition \ "description").as[JsObject] shouldBe Json.obj("state" -> "ABSENT", "reason" -> "NotRepresented", "detail" -> "No detailed prose.")
        discovery.map(x => (x \ "role").as[String]) shouldBe Vector("USE_CASE_SLICE")
        discovery.foreach(_.fields.map(_._1) shouldBe Vector("role", "target", "source", "origin"))
        grounding.size shouldBe 2
        grounding.foreach(_.fields.map(_._1) shouldBe Vector("target", "boundary", "relation", "profile", "context", "preferredLabel", "source", "origin"))
        (definition \ "traceability" \ "value").as[Vector[JsObject]].size shouldBe 2
      }

      "C64G05 preserve Specification and Evidence as independent descriptive traces" in {
        Given("the real independently attributed Specification and Evidence links")
        val inputs = _inputs
        When("the generator emits those trace records")
        val traces = (_definition(_store(_generate(inputs))) \ "traceability" \ "value").as[Vector[JsObject]]
        Then("roles, identities, boundaries and distinct revisions remain separate")
        traces.map(x => (x \ "role").as[String]) shouldBe Vector("SPECIFICATION", "EVIDENCE")
        traces.map(x => (x \ "target" \ "identity" \ "elementId").as[String]) shouldBe Vector("spec:1", "evidence:1")
        traces.map(x => (x \ "target" \ "boundary").as[String]) shouldBe Vector("EXTERNAL", "EXTERNAL")
        traces.map(x => (x \ "source" \ "sourceRevision").as[String]) shouldBe Vector("spec-r3", "evidence-r4")
        traces.map(x => (x \ "origin" \ "kind").as[String]) shouldBe Vector("DECLARED", "DERIVED")
        traces.foreach(_.fields.map(_._1) shouldBe Vector("role", "target", "source", "origin"))
        Given("an explicitly Local Specification fact retains its authored boundary")
        val documents = _documents(inputs)
        val definition = documents.head.model.componentCapabilities.head
        val localtrace = _items(definition.traceability).head.copy(target = ModelReference(_id("spec:1"), Local))
        val localdocuments = _replace_definition(documents, definition.copy(traceability = Present(Vector(localtrace))))
        val localscope = _scope.copy(modelfacts = _scope.modelfacts :+ ModelFact(_id("spec:1"), ModelKind.Specification, _source("spec-fact")))
        When("the Local variant is validated and projected")
        val local = _project(localdocuments, localscope)
        Then("the Local trace stays Local and supplies no executed Evidence result")
        (_definition(_store(local)) \ "traceability" \ "value").as[Vector[JsObject]].map(x => (x \ "target" \ "boundary").as[String]) shouldBe Vector("LOCAL")
      }

      "C64G06 preserve literal qualified model, Term, Relation and Profile identities" in {
        Given("real grounding with Unicode authorities, case and delimiter-bearing IDs")
        val inputs = _inputs
        When("the generator serializes the typed grounding")
        val groundings = (_definition(_store(_generate(inputs))) \ "grounding" \ "value").as[Vector[JsObject]]
        Then("each identity domain remains distinct and exact")
        groundings.map(x => (x \ "target" \ "kind").as[String]) shouldBe Vector("MODEL", "TERM")
        (groundings.head \ "target" \ "identity").as[JsObject] shouldBe Json.obj("modelId" -> "Model:能力/A", "elementId" -> "concept:Order")
        (groundings.last \ "target" \ "identity").as[JsObject] shouldBe Json.obj("vocabularyId" -> "BoK:語彙", "termId" -> "term:注文|α")
        (groundings.head \ "relation" \ "value").as[JsObject] shouldBe Json.obj("vocabularyId" -> "vocab:关系", "relationId" -> "rel:uses|α")
        (groundings.head \ "profile" \ "value").as[JsObject] shouldBe Json.obj("vocabularyId" -> "profile:scope", "profileId" -> "profile:one")
      }

      "C64G07 preserve every Presence branch and explicit null or supplied line and language" in {
        Given("four valid absence reasons, present empty relationships and explicit optional source and label values")
        val documents = _documents(_inputs)
        val definition = documents.head.model.componentCapabilities.head
        val reasons = Vector(NotDeclared, NotRepresented, CmlSemanticFoundation.Unsupported, NotApplicable)
        val variants = reasons.zipWithIndex.map { case (reason, index) =>
          val grounding = _items(definition.grounding).head.copy(preferredLabel = Present(LocalizedLabel("label:日本語", if (index % 2 == 0) None else Some("ja"))))
          _replace_definition(documents, definition.copy(summary = Absent(reason, "Independent gap."), discoveryReferences = Present(Vector.empty),
            grounding = Present(Vector(grounding)), source = definition.source.copy(line = if (index % 2 == 0) None else Some(7))))
        }
        When("each valid variant is validated and projected")
        val results = variants.map(x => _definition(_store(_project(x))))
        Then("absence detail, present emptiness and explicit null or values remain observable")
        results.zipWithIndex.foreach { case (json, index) =>
          (json \ "summary").as[JsObject] shouldBe Json.obj("state" -> "ABSENT", "reason" -> reasons(index).toString, "detail" -> "Independent gap.")
          (json \ "discoveryReferences").as[JsObject] shouldBe Json.obj("state" -> "PRESENT", "value" -> JsArray())
          (json \ "source" \ "line").get shouldBe (if (index % 2 == 0) JsNull else JsNumber(7))
          val label = (json \ "grounding" \ "value").as[Vector[JsObject]].head \ "preferredLabel" \ "value"
          (label \ "language").get shouldBe (if (index % 2 == 0) JsNull else JsString("ja"))
          (label \ "text").as[String] shouldBe "label:日本語"
        }
      }

      "C64G08 preserve declared and ordered foreign derived sources independently of the header" in {
        Given("a header revision different from the authored definition, edge and foreign derivation revisions")
        val inputs = _inputs
        When("the generator emits the selected snapshot")
        val store = _store(_generate(inputs))
        val definition = _definition(store)
        val traces = (definition \ "traceability" \ "value").as[Vector[JsObject]]
        Then("the supplied header and all original record revisions remain independent")
        (store.json \ "source" \ "sourceRevision").as[String] shouldBe "header:Store-r42"
        (definition \ "source" \ "sourceRevision").as[String] shouldBe "definition-r1"
        (traces.head \ "origin").as[JsObject] shouldBe Json.obj("kind" -> "DECLARED")
        (traces.last \ "origin" \ "ruleId").as[String] shouldBe "rule:discovery|α"
        (traces.last \ "origin" \ "inputs").as[Vector[JsObject]] shouldBe Vector(
          Json.obj("modelAuthority" -> "Other:能力/B", "path" -> _secondary_scope.path, "sourceRevision" -> "foreign-edge-r9", "line" -> 8),
          Json.obj("modelAuthority" -> "Model:能力/A", "path" -> _primary_scope.path, "sourceRevision" -> "evidence-r2", "line" -> 91))
      }

      "C64G09 retain independent producer, release, contract and ABI domains" in {
        Given("actual Cozy BuildInfo and explicitly different Component and package releases")
        val contexts = _contexts
        When("the generator composes the actual two-CML inputs")
        val store = _store(_right(CmlCapabilityGeneration.generate(_inputs, ScopeInput.Ready(_scope), contexts)))
        val edges = (store.json \ "declarations").as[Vector[JsObject]].head \ "realizations" \ "value"
        Then("all version domains retain the supplied values rather than aliases")
        (store.json \ "producer").as[JsObject] shouldBe Json.obj("id" -> "cozy", "version" -> org.simplemodeling.cozy.BuildInfo.version)
        (store.json \ "component" \ "version").as[String] shouldBe "1.2.3-SNAPSHOT"
        (store.json \ "package").as[JsObject] shouldBe Json.obj("coordinate" -> "pkg:Store|日本語", "version" -> "package:r9")
        (store.json \ "generatedAbiVersion").as[String] shouldBe "cncf.component-capability.v1"
        (_definition(store) \ "contractVersion").as[String] shouldBe "contract:二"
        edges.as[Vector[JsObject]].map(x => (x \ "generatedAbiVersion").as[String]) shouldBe Vector("operation.abi:v7", "workflow.abi:v8", "state.abi:v9")
        (store.json \ "schemaVersion").as[Int] shouldBe 1
        store.context.projectiongeneration shouldBe 1
      }
    }

    "reject incomplete or incompatible generation contexts" which {
      "C64G10 reject semantic generation, schema and projection ABI disagreements" in {
        Given("three otherwise valid contexts with one unsupported wire domain each")
        val validated = _validated(_documents(_inputs))
        val context = _contexts.head
        val variants = Vector(context.copy(projectiongeneration = 2), context.copy(schemaversion = 2), context.copy(generatedabiversion = "target.abi:v99"))
        When("projection checks each entire supplied context batch")
        val results = variants.map(x => CmlCapabilityGeneration.project(validated, _contexts.updated(0, x)))
        Then("each unsupported field yields a source-correlated failure with no Generated")
        results.zip(Vector("projectiongeneration", "schemaversion", "generatedabiversion")).foreach { case (result, field) =>
          result.isLeft shouldBe true
          result.left.get.head.kind shouldBe IssueKind.Unsupported
          result.left.get.head.fieldpath shouldBe s"contexts[0].$field"
          result.left.get.head.source shouldBe Some(context.source)
        }
      }

      "C64G11 reject supplied producer identity or version disagreement" in {
        Given("explicit producer identities and versions differing from actual Cozy")
        val validated = _validated(_documents(_inputs))
        val variants = Vector(Producer("handcrafted", org.simplemodeling.cozy.BuildInfo.version), Producer("cozy", "different-build"))
        When("the context gate checks producer truth")
        val results = variants.map(x => CmlCapabilityGeneration.project(validated, _contexts.updated(0, _contexts.head.copy(producer = x))))
        Then("the supplied disagreement is rejected with its exact field rather than rewritten")
        results.zip(Vector("id", "version")).foreach { case (result, field) =>
          result.isLeft shouldBe true
          result.left.get.head.kind shouldBe IssueKind.ProducerMismatch
          result.left.get.head.fieldpath shouldBe s"contexts[0].producer.$field"
        }
      }

      "C64G12 reject malformed context lexical and canonical Component release fields" in {
        Given("valid snapshots varied over malformed source authority/path/revision/line, Component and package fields")
        val context = _contexts.head
        val variants = Vector("source.modelAuthority" -> context.copy(source = context.source.copy(modelAuthority = " padded")),
          "source.path" -> context.copy(source = context.source.copy(path = "../escape.cml")),
          "source.sourceRevision" -> context.copy(source = context.source.copy(sourceRevision = "")),
          "source.line" -> context.copy(source = context.source.copy(line = Some(0))),
          "component" -> context.copy(component = ComponentCoordinate(null, "1.2.3")),
          "component.version" -> context.copy(component = context.component.copy(version = "bad/release")),
          "packagecoordinate.coordinate" -> context.copy(packagecoordinate = context.packagecoordinate.copy(coordinate = "")),
          "packagecoordinate.version" -> context.copy(packagecoordinate = context.packagecoordinate.copy(version = " padded")))
        val validated = _validated(_documents(_inputs))
        val invalidcomponentinputs = Vector("\"namespace\":\" invalid\"", "\"namespace\":\"org.simplemodeling.textus\"").zip(
          Vector("\"id\":\"Store\"", "\"id\":\"bad/id\"")).map { case (namespace, localid) =>
          SourceInput(_primary_scope, KaleidoxModel.parse(KaleidoxConfig.default,
            _primary_text.replace("\"namespace\":\"org.simplemodeling.textus\"", namespace).replace("\"id\":\"Store\"", localid)))
        }
        When("each malformed context is submitted to projection")
        val results = variants.map { case (field, candidate) => field -> CmlCapabilityGeneration.project(validated, _contexts.updated(0, candidate)) }
        val componentresults = invalidcomponentinputs.map(x => CmlCapabilityGeneration.generate(Vector(x), ScopeInput.Ready(_scope), _contexts.take(2)))
        Then("every invalid field is reported as malformed without partial successful projection")
        results.foreach { case (field, result) =>
          result.isLeft shouldBe true
          result.left.get.exists(x => x.kind == IssueKind.Malformed && x.fieldpath == s"contexts[0].$field") shouldBe true
        }
        componentresults.foreach { result =>
          result match {
            case Left(SyntaxFailure(issues)) => issues.exists(_.fieldPath.contains("component")) shouldBe true
            case other => fail(s"Expected original canonical Component syntax failure: $other")
          }
        }
      }

      "C64G13 reject unknown scopes and repeated exact snapshot keys without a winner" in {
        Given("unknown authority/path contexts and a repeated Component key with changed release and header revision")
        val validated = _validated(_documents(_inputs))
        val context = _contexts.head
        val unknowns = Vector(context.copy(source = context.source.copy(modelAuthority = "Unknown:model")), context.copy(source = context.source.copy(path = "model/unknown.cml")))
        val repeated = context.copy(component = context.component.copy(version = "9.8.7"), source = context.source.copy(sourceRevision = "different-header"))
        When("the projection gate checks scope ownership and the full supplied key batch")
        val unknownresults = unknowns.map(x => CmlCapabilityGeneration.project(validated, _contexts.updated(0, x)))
        val duplicate = CmlCapabilityGeneration.project(validated, _contexts :+ repeated)
        Then("unknown documents and duplicate exact keys fail with original and related sources")
        unknownresults.foreach { result => result.isLeft shouldBe true; result.left.get.head.kind shouldBe IssueKind.UnknownSnapshot }
        duplicate.isLeft shouldBe true
        duplicate.left.get.head.kind shouldBe IssueKind.DuplicateSnapshot
        duplicate.left.get.head.source shouldBe Some(repeated.source)
        duplicate.left.get.head.relatedsources shouldBe Vector(context.source)
      }

      "C64G14 require all declaration keys and definition documents while rejecting Usage-only Component selection" in {
        Given("a missing Store selection, missing definitions-only remote selection and a Usage-only document")
        val documents = _documents(_inputs)
        val validated = _validated(documents)
        val usagedocument = documents.head.copy(model = documents.head.model.copy(componentCapabilities = Vector.empty, componentDeclarations = Vector.empty, compatibility = Present(Vector.empty)))
        val usagevalidated = _validated(Vector(usagedocument), _usage_scope)
        When("projection checks missing coverage and an extra Usage-only Component context")
        val missingdecl = CmlCapabilityGeneration.project(validated, _contexts.tail)
        val missingdoc = CmlCapabilityGeneration.project(validated, _contexts.take(2))
        val usageonly = CmlCapabilityGeneration.project(usagevalidated, Vector(_contexts.head))
        val complete = _right(CmlCapabilityGeneration.project(validated, _contexts))
        Then("missing keys/documents fail and explicit definitions-only selection retains its definition without claiming a provider")
        missingdecl.left.get.exists(_.fieldpath.endsWith("componentDeclarations")) shouldBe true
        missingdoc.left.get.exists(_.fieldpath == "documents[1]") shouldBe true
        usageonly.left.get.head.kind shouldBe IssueKind.UnknownSnapshot
        val remote = complete.projections.find(_.context.source.path == _secondary_scope.path).get
        remote.capabilities.map(_.identity.elementId) shouldBe Vector("Component:Remote")
        remote.declarations shouldBe Vector.empty
      }

      "C64G33 reject repeated compatibility tuples without altering the original Validated" in {
        Given("two original documents and three contexts, with exact repetitions or equal tuples carrying distinct source and origin")
        val documents = _documents(_inputs)
        val edges = _items(documents.head.model.compatibility)
        val first = edges.head
        val attributed = first.copy(source = _source("repeated-compatibility:r33", Some(33)),
          origin = Derived("repeated-compatibility-rule", Vector(first.source)))
        val variants = Vector(first, attributed).map { repeated =>
          documents.updated(0, documents.head.copy(model = documents.head.model.copy(
            compatibility = Present(edges ++ Vector(repeated, repeated)))))
        }
        When("the preceding validator accepts each original model and generation checks the entire selected snapshot batch")
        val validated = variants.map(x => _validated(x))
        val bindings = validated.map(_.bindings)
        val results = validated.map(x => CmlCapabilityGeneration.project(x, _contexts))
        Then("each batch fails once per repeated authored edge in order with its own source and the first source, retaining all original evidence")
        results.zipWithIndex.foreach { case (result, index) =>
          result.isLeft shouldBe true
          val issues = result.left.get
          issues.map(_.kind) shouldBe Vector(IssueKind.Malformed, IssueKind.Malformed)
          issues.map(_.fieldpath) shouldBe Vector(s"documents[0].compatibility[${edges.size}]", s"documents[0].compatibility[${edges.size + 1}]")
          issues.map(_.source) shouldBe Vector.fill(2)(Some(if (index == 0) first.source else attributed.source))
          issues.map(_.relatedsources) shouldBe Vector.fill(2)(Vector(first.source))
          validated(index).documents shouldBe variants(index)
          validated(index).bindings shouldBe bindings(index)
          validated(index).scope shouldBe _scope
        }
      }

      "C64G15 retain Usage data upstream and permit zero external snapshots" in {
        Given("original Usage demand, discovery, mappings and dependencies and a Usage-only valid document")
        val documents = _documents(_inputs)
        val usagedocuments = documents.map(x => x.copy(model = x.model.copy(componentCapabilities = Vector.empty, componentDeclarations = Vector.empty, compatibility = Present(Vector.empty))))
        val usagevalidated = _validated(usagedocuments, _usage_scope)
        When("Component generation handles full inputs and an explicit empty context batch for Usage-only data")
        val full = _generate(_inputs)
        val usageonly = _right(CmlCapabilityGeneration.project(usagevalidated, Vector.empty))
        Then("the complete upstream records survive while the external closed shape contains no Usage branch")
        full.validated.documents shouldBe documents
        full.validated.documents.head.model.componentUsageCapabilities(1).demandReferences shouldBe documents.head.model.componentUsageCapabilities(1).demandReferences
        full.validated.documents.head.model.componentUsageCapabilities(1).dependencies shouldBe documents.head.model.componentUsageCapabilities(1).dependencies
        full.projections.foreach(x => x.json.keys shouldBe Set("schemaVersion", "generatedAbiVersion", "producer", "component", "package", "source", "capabilities", "declarations", "compatibility"))
        usageonly.validated shouldBe usagevalidated
        usageonly.projections shouldBe Vector.empty
      }

      "C64G16 preserve original syntax, target and acquisition failure vectors" in {
        Given("an actual parser error, a missing or wrong-kind target fact and an ordered rejected acquisition vector")
        val broken = KaleidoxModel.parse(KaleidoxConfig.default, "# ENV\nvalue = ]\n")
        val brokeninputs = Vector(SourceInput(_primary_scope, broken))
        val syntax = CmlCapabilityCml.normalize(brokeninputs).left.get
        val inputs = _inputs
        val documents = _documents(inputs)
        val scopes = Vector(_scope.copy(targetcontracts = _scope.targetcontracts.tail),
          _scope.copy(targetcontracts = _scope.targetcontracts.updated(0, _scope.targetcontracts.head.copy(kind = Workflow))))
        val expected = scopes.map(x => CmlCapabilityValidation.validate(documents, ScopeInput.Ready(x)).left.get)
        val acquisition = Vector(Diagnostic(DiagnosticKind.Unavailable, "scope.acquire[0]", None, Some(_source("acquire-r1")), Vector.empty, "Original unavailable."),
          Diagnostic(DiagnosticKind.Unauthorized, "scope.acquire[1]", None, Some(_source("acquire-r2")), Vector.empty, "Original unauthorized."))
        When("the production composition consumes the original failed inputs")
        val syntaxresult = CmlCapabilityGeneration.generate(brokeninputs, ScopeInput.Ready(_scope), _contexts)
        val semanticresults = scopes.map(x => CmlCapabilityGeneration.generate(inputs, ScopeInput.Ready(x), _contexts))
        val rejected = CmlCapabilityGeneration.generate(inputs, ScopeInput.Rejected(acquisition), _contexts)
        Then("the closed wrappers carry the exact original ordered vectors with no Generated")
        broken.errors.nonEmpty shouldBe true
        syntaxresult shouldBe Left(SyntaxFailure(syntax))
        semanticresults.zip(expected).foreach { case (result, diagnostics) => result shouldBe Left(SemanticFailure(diagnostics)) }
        expected.foreach(_.exists(x => x.kind == DiagnosticKind.DanglingReference || x.kind == DiagnosticKind.WrongKind) shouldBe true)
        rejected shouldBe Left(SemanticFailure(acquisition))
      }
    }

    "preserve authored order under canonical outer enumeration" which {
      "C64G17 retain authored definitions, traces, Derived inputs and compatibility order" in {
        Given("an original document with two independently identified definitions and reversed authored trace and compatibility vectors")
        val documents = _documents(_inputs)
        val definition = documents.head.model.componentCapabilities.head
        val altered = definition.copy(traceability = Present(_items(definition.traceability).reverse))
        val second = altered.copy(identity = _id("Component:Second"), name = "Second authored definition")
        val model = documents.head.model.copy(componentCapabilities = Vector(second, altered), compatibility = Present(_items(documents.head.model.compatibility).reverse))
        val variants = documents.updated(0, documents.head.copy(model = model))
        val validated = _validated(variants)
        When("both outer enumerations are projected using independently permuted contexts")
        val normal = _right(CmlCapabilityGeneration.project(validated, _contexts))
        val reversed = _right(CmlCapabilityGeneration.project(_validated(variants.reverse), _contexts.reverse))
        Then("canonical snapshots match while authored inner order remains exactly supplied")
        normal.projections.map(_.projectionJson) shouldBe reversed.projections.map(_.projectionJson)
        val store = _store(normal)
        store.capabilities.map(_.identity) shouldBe Vector(second.identity, altered.identity)
        (_definition(store) \ "traceability" \ "value").as[Vector[JsObject]].map(x => (x \ "role").as[String]) shouldBe Vector("EVIDENCE", "SPECIFICATION")
        val origin = (_definition(store) \ "traceability" \ "value").as[Vector[JsObject]].head \ "origin"
        (origin \ "inputs").as[Vector[JsObject]].map(x => (x \ "sourceRevision").as[String]) shouldBe Vector("foreign-edge-r9", "evidence-r2")
        (store.json \ "compatibility" \ "value").as[Vector[JsObject]].map(x => (x \ "source" \ "sourceRevision").as[String]) shouldBe Vector("compatibility-r19", "compatibility-r18")
      }

      "C64G18 keep same-authority documents with distinct paths as separate snapshots" in {
        Given("two valid definitions-only documents sharing an authority with independently absent or present-empty compatibility")
        val original = _documents(_inputs)(1).model.componentCapabilities.head
        val scopes = Vector(SourceScope(_primary_scope.modelAuthority, "model/one.cml"), SourceScope(_primary_scope.modelAuthority, "model/two.cml"))
        val documents = scopes.zipWithIndex.map { case (scope, index) =>
          val definition = original.copy(identity = _id(s"Component:Path$index"), source = CapabilitySource(scope.modelAuthority, scope.path, s"definition:path$index", None))
          CapabilityDocument(scope, CapabilityModel(Vector.empty, Vector(definition), Vector.empty,
            if (index == 0) Present(Vector.empty) else Absent(NotRepresented, "Second document gap.")))
        }
        val contexts = scopes.zipWithIndex.map { case (scope, index) => _context(scope, s"Selected$index") }
        When("the validated documents and contexts are projected in reverse supplied order")
        val generated = _right(CmlCapabilityGeneration.project(_validated(documents.reverse), contexts.reverse))
        Then("path-qualified sources and separate compatibility states survive without merging")
        generated.projections.map(_.context.source.path) shouldBe Vector("model/one.cml", "model/two.cml")
        generated.projections.map(x => (x.json \ "compatibility" \ "state").as[String]) shouldBe Vector("PRESENT", "ABSENT")
        generated.projections.map(_.capabilities.head.source.sourceRevision) shouldBe Vector("definition:path0", "definition:path1")
        generated.validated.documents shouldBe documents.reverse
      }

      "C64G19 round-trip literal descriptive values in at least one hundred active generator samples" in {
        Given("valid typed definitions and unfiltered literals containing quotes, backslashes, Japanese, supplementary Unicode and delimiters")
        val documents = _documents(_inputs)
        val definition = documents.head.model.componentCapabilities.head
        val literals = Gen.listOf(Gen.oneOf("Alpha", "β", "日本語", "é", "|:=", "\\", "\"", "🙂")).map(x => "literal:\"\\日本語🙂|=" + x.mkString + ":end")
        val property = Prop.forAll(literals) { literal =>
          val variant = _replace_definition(documents, definition.copy(name = literal, description = Present(literal)))
          CmlCapabilityValidation.validate(variant, ScopeInput.Ready(_scope)) match {
            case Left(_) => false
            case Right(validated) => CmlCapabilityGeneration.project(validated, _contexts) match {
              case Left(_) => false
              case Right(generated) =>
                val projection = _store(generated)
                val json = Json.parse(projection.projectionJson)
                val value = (json \ "capabilities").as[Vector[JsObject]].head
                (value \ "name").as[String] == literal && (value \ "description" \ "value").as[String] == literal && json == projection.json
            }
          }
        }
        When("ScalaCheck checks the actual validator-to-projector JSON round trip")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("all required active samples preserve the literal values without filters or discards")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }

      "C64G20 keep JSON and rendered artifacts invariant under independent source and context permutations" in {
        Given("two actual SourceInputs, three explicit contexts and complete fixed output selections")
        val inputs = _inputs
        val contexts = _contexts
        val baseline = _generate(inputs)
        val baselineartifacts = _right(CmlCapabilityGenerationScala.render(baseline, _selections(baseline))).artifacts
        val orders = Gen.zip(Gen.oneOf(inputs.permutations.toVector), Gen.oneOf(contexts.permutations.toVector))
        val property = Prop.forAll(orders) { case (inputorder, contextorder) =>
          CmlCapabilityGeneration.generate(inputorder, ScopeInput.Ready(_scope), contextorder) match {
            case Left(_) => false
            case Right(generated) => CmlCapabilityGenerationScala.render(generated, _selections(generated).reverse) match {
              case Left(_) => false
              case Right(batch) => generated.projections.map(x => (x.snapshotKey, x.projectionJson)) == baseline.projections.map(x => (x.snapshotKey, x.projectionJson)) && batch.artifacts == baselineartifacts
            }
          }
        }
        When("ScalaCheck evaluates the real production and rendering contract over independent permutations")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least one hundred active samples retain exact canonical snapshots, JSON and artifacts")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }
}

private[modeler] object CmlCapabilityGenerationSpec {
  private[modeler] def _right[A, B](value: Either[A, B]): B = value.fold(x => org.scalatest.Assertions.fail(x.toString), identity)
  private[modeler] def _component(id: String): ComponentId = ComponentId.of(
    ComponentNamespace.parse("org.simplemodeling.textus").value().get(), ComponentLocalId.parse(id).value().get())
  private[modeler] def _primary_scope: SourceScope = SourceScope("Model:能力/A", "model/capability-model.cml")
  private[modeler] def _secondary_scope: SourceScope = SourceScope("Other:能力/B", "model/capability-model-secondary.cml")
  private[modeler] def _id(element: String): ModelElementId = ModelElementId(_primary_scope.modelAuthority, element)
  private[modeler] def _source(revision: String, line: Option[Int] = None): CapabilitySource =
    CapabilitySource(_primary_scope.modelAuthority, _primary_scope.path, revision, line)
  private[modeler] def _primary_text: String = new String(Files.readAllBytes(Paths.get("src/test/resources/modeler/capability-model.cml")), StandardCharsets.UTF_8)
  private[modeler] def _inputs: Vector[SourceInput] = Vector(
    SourceInput(_primary_scope, KaleidoxModel.load(KaleidoxConfig.default, Paths.get("src/test/resources/modeler/capability-model.cml").toFile)),
    SourceInput(_secondary_scope, KaleidoxModel.load(KaleidoxConfig.default, Paths.get("src/test/resources/modeler/capability-model-secondary.cml").toFile)))
  private[modeler] def _documents(inputs: Vector[SourceInput]): Vector[CapabilityDocument] = _right(CmlCapabilityCml.normalize(inputs))
  private[modeler] def _validated(documents: Vector[CapabilityDocument], scope: Scope = _scope): CmlCapabilityValidation.Validated =
    _right(CmlCapabilityValidation.validate(documents, ScopeInput.Ready(scope)))
  private[modeler] def _context(scope: SourceScope, component: String): GenerationContext = GenerationContext(1, 1, "cncf.component-capability.v1",
    Producer("cozy", org.simplemodeling.cozy.BuildInfo.version), ComponentCoordinate(_component(component), "1.2.3-SNAPSHOT"),
    PackageCoordinate(s"pkg:$component|日本語", "package:r9"), CapabilitySource(scope.modelAuthority, scope.path, s"header:$component-r42", None))
  private[modeler] def _contexts: Vector[GenerationContext] = Vector(_context(_primary_scope, "Store"), _context(_primary_scope, "Archive"), _context(_secondary_scope, "Remote"))
  private[modeler] def _generate(inputs: Vector[SourceInput]): CmlCapabilityGeneration.Generated =
    _right(CmlCapabilityGeneration.generate(inputs, ScopeInput.Ready(_scope), _contexts))
  private[modeler] def _project(documents: Vector[CapabilityDocument], scope: Scope = _scope): CmlCapabilityGeneration.Generated =
    _right(CmlCapabilityGeneration.project(_validated(documents, scope), _contexts))
  private[modeler] def _store(generated: CmlCapabilityGeneration.Generated): CmlCapabilityGeneration.Projection =
    generated.projections.find(_.context.component.component == _component("Store")).get
  private[modeler] def _archive(generated: CmlCapabilityGeneration.Generated): CmlCapabilityGeneration.Projection =
    generated.projections.find(_.context.component.component == _component("Archive")).get
  private[modeler] def _definition(projection: CmlCapabilityGeneration.Projection): JsObject = (projection.json \ "capabilities").as[Vector[JsObject]].head
  private[modeler] def _items[A](value: Presence[Vector[A]]): Vector[A] = value match { case Present(items) => items; case _: Absent => Vector.empty }
  private[modeler] def _replace_definition(documents: Vector[CapabilityDocument], definition: ComponentCapability): Vector[CapabilityDocument] =
    documents.updated(0, documents.head.copy(model = documents.head.model.copy(componentCapabilities = Vector(definition))))
  private[modeler] def _selections(generated: CmlCapabilityGeneration.Generated): Vector[ScalaOutput] = generated.projections.map { projection =>
    val id = projection.context.component.component.localId().value()
    ScalaOutput(projection.snapshotKey, s"target/generated/$id.scala", Vector("generated", "capability"), s"${id}Metadata")
  }
  private[modeler] def _scope: Scope = {
    val models = Vector("usecase:one" -> ModelKind.UseCase, "goal:two" -> ModelKind.Goal, "scenario:one" -> ModelKind.Scenario,
      "concept:Order" -> ModelKind.DomainConcept).map { case (element, kind) => ModelFact(_id(element), kind, _source("fact:" + element)) }
    val term = TermFact(TermId("BoK:語彙", "term:注文|α"), CapabilitySource("BoK:語彙", "catalog/terms.cml", "term:r8", None))
    val relation = RelationFact(RelationId("vocab:关系", "rel:uses|α"), CapabilitySource("vocab:关系", "catalog/relations.cml", "relation:r3", None))
    val profile = ProfileFact(ProfileId("profile:scope", "profile:one"), CapabilitySource("profile:scope", "catalog/profiles.cml", "profile:r7", None))
    val history = Vector("contract:一", "contract:三").map(version => ComponentContract(_id("Component:Store|α"), version, _source("catalog:" + version)))
    val targets = Vector((Operation, "operation:save", "operation:v3", "operation.abi:v7"), (Workflow, "workflow:save", "workflow:v4", "workflow.abi:v8"),
      (StateMachine, "state:store", "state:v5", "state.abi:v9")).map { case (kind, element, version, abi) =>
      TargetContract(kind, _id(element), _component("Store"), version, abi, _source("target:" + element)) }
    Scope("explicit-generation-fixture-scope", _source("scope:r1"), models, Vector(term), Vector(relation), Vector(profile), Vector.empty, history, targets)
  }
  private[modeler] def _usage_scope: Scope = _scope.copy(componentcontracts = _scope.componentcontracts :+
    ComponentContract(_id("Component:Store|α"), "contract:二", _source("explicit-usage-mapping-catalog:r2")))
}
