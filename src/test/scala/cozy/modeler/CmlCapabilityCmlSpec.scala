package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.cncf.component.identity.{ComponentId, ComponentNamespace, ComponentLocalId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import CmlSemanticFoundation.{ModelElementId, TermId, RelationId, ProfileId, Presence, Present, Absent, NotDeclared, NotRepresented, Unsupported, NotApplicable, Local, External, ModelTarget, TermTarget, LocalizedLabel}
import CmlCapabilityModel._
import CmlCapabilitySource._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Per-document Capability CML normalization" should {
    "retain authored definitions and relationships" which {
      "C64P01 load every primary R3 field through the actual CML parser" in {
        Given("a real primary CML file and an independently authored complete typed expectation")
        val scope = _primary_scope
        val expected = _expected_model
        When("Kaleidox loads the file and the private entry normalizes its original sections")
        val parsed = KaleidoxModel.load(KaleidoxConfig.default, Paths.get(_primary_resource).toFile)
        val result = CmlCapabilityCml.normalize(Vector(SourceInput(scope, parsed)))
        Then("the whole typed model, including every field and ordered edge, equals the authored expectation")
        result shouldBe Right(Vector(CapabilityDocument(scope, expected)))
      }

      "C64P02 retain an independent Application with explicitly empty demand and mappings" in {
        Given("the primary source has an independent Application with no Use Cases")
        val source = _primary
        When("the actual source is parsed and normalized")
        val application = _documents(_normalize(source)).head.model.applicationCapabilities.head
        Then("no target, group or relationship is invented")
        application.identity shouldBe _id("Application:Independent")
        application.demandReferences shouldBe Present(Vector.empty)
        application.componentMappings shouldBe Present(Vector.empty)
        application.dependencies shouldBe Absent(NotDeclared, "No dependency declared.")
      }

      "C64P03 preserve four authored many-to-many demand edges" in {
        Given("two Applications each explicitly require the same two qualified demand targets")
        val source = _primary
        When("the source is normalized")
        val applications = _documents(_normalize(source)).head.model.applicationCapabilities.tail
        Then("the exact role, source, target and order are retained per enclosing Application")
        applications.map(_.identity) shouldBe Vector(_id("Application:One"), _id("Application:Two"))
        applications.map(_.demandReferences) shouldBe Vector.fill(2)(Present(Vector(
          _link(UseCase, "usecase:one", Local, "demand-r1", Declared),
          _link(Goal, "goal:two", Local, "demand-r2", _derived))))
      }

      "C64P04 retain two providers and both directions on one canonical Component" in {
        Given("three complete declaration tuples in the actual source")
        val source = _primary
        When("their Component fields pass the shared Java identity parser")
        val declarations = _documents(_normalize(source)).head.model.componentDeclarations
        Then("the tuples remain distinct without provider selection")
        declarations.map(x => (x.component, x.direction, x.capability)) shouldBe Vector(
          (_component("Store"), Provided, _capability), (_component("Archive"), Provided, _capability),
          (_component("Store"), Required, _capability))
      }

      "C64P05 retain all realization keys, target contracts and independent ABIs" in {
        Given("one provider explicitly declares Operation, Workflow and StateMachine realization edges")
        val source = _primary
        When("the three edges are normalized")
        val edges = _present(_documents(_normalize(source)).head.model.componentDeclarations.head.realizations)
        Then("complete authored edge values survive without target admission")
        edges shouldBe _expected_realizations
        edges.map(_.targetKind) shouldBe Vector(Operation, Workflow, StateMachine)
      }

      "C64P06 keep External links external when their targets occur in another input" in {
        Given("two separately authored sources include the target of an External mapping")
        val sources = Vector(_primary -> _primary_scope, _secondary -> _secondary_scope)
        When("each original document is parsed and normalized before any aggregate merge")
        val documents = _documents(_normalize_all(sources))
        val mappings = _present(documents.head.model.applicationCapabilities(1).componentMappings)
        val grounding = _present(documents.head.model.componentCapabilities.head.grounding)
        Then("explicit boundaries and qualified Model/Term domains remain distinct")
        mappings.map(_.boundary) shouldBe Vector(Local, External)
        mappings.last.target.identity shouldBe documents(1).model.componentCapabilities.head.identity
        grounding.map(_.target) shouldBe Vector(ModelTarget(_id("concept:Order")), TermTarget(TermId("BoK:語彙", "term:注文|α")))
        grounding.map(_.boundary) shouldBe Vector(Local, External)
      }

      "C64P07 preserve present emptiness and all four absence branches at every optional field" in {
        Given("each optional scalar and vector field is explicitly varied across the four reasons and present values")
        val reasons = Vector("NotDeclared" -> NotDeclared, "NotRepresented" -> NotRepresented, "Unsupported" -> Unsupported, "NotApplicable" -> NotApplicable)
        val fields = Vector("compatibility", "summary", "description", "demandReferences", "discoveryReferences", "componentMappings", "dependencies", "grounding", "traceability", "realizations")
        val sources = reasons.map { case (tag, reason) =>
          val detail = s"Explicit $tag gap."
          val absence = Json.stringify(Json.obj("state" -> "ABSENT", "reason" -> tag, "detail" -> detail))
          (reason, detail, fields.foldLeft(_primary)((source, field) => _field(source, field, absence)))
        }
        val empties = fields.filterNot(x => x == "summary" || x == "description")
          .foldLeft(_primary)((source, field) => _field(source, field, "{\"state\":\"PRESENT\",\"value\":[]}"))
        When("all explicitly varied CML inputs are normalized")
        val outcomes = sources.map { case (reason, detail, source) => (reason, detail, _documents(_normalize(source)).head.model) }
        val empty = _documents(_normalize(empties)).head.model
        Then("no optional carrier drops its exact reason/detail or conflates empty with absence")
        outcomes.foreach { case (reason, detail, model) =>
          val expected = Absent(reason, detail)
          model.compatibility shouldBe expected
          model.applicationCapabilities.foreach { application =>
            Vector(application.summary, application.description, application.demandReferences, application.discoveryReferences,
              application.componentMappings, application.dependencies, application.grounding, application.traceability).foreach(_ shouldBe expected)
          }
          model.componentCapabilities.foreach { component =>
            Vector(component.summary, component.description, component.discoveryReferences, component.grounding, component.traceability).foreach(_ shouldBe expected)
          }
          model.componentDeclarations.foreach { declaration =>
            declaration.realizations shouldBe expected
            declaration.traceability shouldBe expected
          }
        }
        empty.compatibility shouldBe Present(Vector.empty)
        empty.applicationCapabilities.foreach(x => Vector(x.demandReferences, x.discoveryReferences, x.componentMappings,
          x.dependencies, x.grounding, x.traceability).foreach(_ shouldBe Present(Vector.empty)))
        empty.componentCapabilities.foreach(x => Vector(x.discoveryReferences, x.grounding, x.traceability).foreach(_ shouldBe Present(Vector.empty)))
        empty.componentDeclarations.foreach(x => Vector(x.realizations, x.traceability).foreach(_ shouldBe Present(Vector.empty)))
      }

      "C64P08 retain independently revised foreign and own derivation evidence" in {
        Given("a Derived discovery edge has two ordered explicit sources from different authorities")
        val source = _primary
        When("the source and origin are normalized")
        val edge = _present(_documents(_normalize(source)).head.model.applicationCapabilities(1).discoveryReferences).head
        Then("the primary source and the complete ordered input evidence remain independent")
        edge.source shouldBe _source("discover-r7")
        edge.origin shouldBe _derived
        edge.origin should not be (Declared)
      }

      "C64P09 retain separate real resources and deterministic input order" in {
        Given("two actual files with different source authorities and project-relative paths")
        val paths = Vector(_primary_resource -> _primary_scope, _secondary_resource -> _secondary_scope)
        When("the files are loaded separately and normalized repeatedly in both orders")
        val inputs = paths.map { case (path, scope) => SourceInput(scope, KaleidoxModel.load(KaleidoxConfig.default, Paths.get(path).toFile)) }
        val first = CmlCapabilityCml.normalize(inputs)
        val repeated = CmlCapabilityCml.normalize(inputs)
        val reversed = CmlCapabilityCml.normalize(inputs.reverse)
        Then("only document order reverses; internal vectors, revisions and compatibility stay unchanged")
        repeated shouldBe first
        reversed shouldBe Right(_documents(first).reverse)
        _documents(first).map(_.scope) shouldBe Vector(_primary_scope, _secondary_scope)
        _documents(first)(1).model.componentCapabilities.head.source.sourceRevision shouldBe "secondary-definition-r20"
      }

      "C64P10 keep identity independent of labels, names, versions and revisions" in {
        Given("four separate edits change one authored domain at a time")
        val original = _primary
        val sources = Vector(original.replace("### Application one", "### CAPABILITY-MODEL"),
          original.replace("Same presentation", "Different name"), original.replace("app:v1", "app:v99"),
          original.replace("definition-r1", "definition-r999"))
        When("each edit is parsed and normalized")
        val baseline = _documents(_normalize(original)).head.model
        val changed = sources.map(x => _documents(_normalize(x)).head.model)
        Then("identities never derive from any changed presentation or provenance domain")
        changed.head shouldBe baseline
        changed.foreach(_.applicationCapabilities.map(_.identity) shouldBe baseline.applicationCapabilities.map(_.identity))
        changed(1).applicationCapabilities(1) shouldBe baseline.applicationCapabilities(1).copy(name = "Different name")
        changed(2).applicationCapabilities(1) shouldBe baseline.applicationCapabilities(1).copy(contractVersion = "app:v99",
          dependencies = Present(_present(baseline.applicationCapabilities(1).dependencies).map(x => x.copy(target = x.target.copy(contractVersion = "app:v99")))))
        changed(3).componentCapabilities.head shouldBe baseline.componentCapabilities.head.copy(source = _source("definition-r999", Some(12)))
      }

      "C64P11 retain literal Unicode qualified vocabulary and localized labels" in {
        Given("opaque qualified IDs contain Unicode, case, slash and delimiter characters")
        val source = _primary
        When("Foundation-domain grounding records are normalized")
        val grounds = _present(_documents(_normalize(source)).head.model.componentCapabilities.head.grounding)
        Then("the exact domain types, literal identities and explicit label languages survive")
        grounds shouldBe _expected_grounding
        grounds.head.relation shouldBe Present(RelationId("vocab:关系", "rel:uses|α"))
        grounds.head.profile shouldBe Present(ProfileId("profile:scope", "profile:one"))
        grounds.map(_.preferredLabel) shouldBe Vector(Present(LocalizedLabel("注文", None)), Present(LocalizedLabel("Order", Some("en-X-literal"))))
      }

      "C64P12 keep authored source lines separate from actual optional diagnostic locations" in {
        Given("the same source contains authored null and positive lines and an invalid field for diagnostic comparison")
        val source = _primary
        val invalid = source.replace("name = \"Store capability\"", "name = null")
        When("located and withoutLocation CML parsers supply original sections")
        val located = _normalize(source)
        val unlocated = _normalize(source, _primary_scope, false)
        val locatedissues = _issues(_normalize(invalid))
        val unlocatedissues = _issues(_normalize(invalid, _primary_scope, false))
        Then("source lines are unchanged while diagnostics report actual section-header locations only")
        unlocated shouldBe located
        _documents(located).head.model.componentCapabilities.head.source.line shouldBe Some(12)
        _present(_documents(located).head.model.componentDeclarations.head.realizations).map(_.source.line) shouldBe Vector(Some(56), None, Some(58))
        locatedissues.head.sectionPath shouldBe Vector("CAPABILITY-MODEL", "COMPONENT-CAPABILITY", "Component meaning")
        locatedissues.head.line.get should be > 0
        unlocatedissues.head.line shouldBe None
      }

      "C64P13 distinguish successful legacy absence from an explicit empty new document" in {
        Given("legacy CAPABILITY/style and COMPONENT inputs, plus an explicitly empty new root")
        val legacy = Vector("# CAPABILITY\n", "# COMPONENT\n## Legacy\n### PACKAGE\nlegacy\n", "# STYLE\nlegacy\n")
        val empty = "# CAPABILITY-MODEL\ncompatibility = {\"state\":\"PRESENT\",\"value\":[]}\n"
        When("the actual parser inputs pass through normalization")
        val results = legacy.map(_normalize(_))
        val document = _normalize(empty)
        Then("successful legacy input yields no new document and explicit emptiness yields one complete envelope")
        results.foreach(_ shouldBe Right(Vector.empty))
        document shouldBe Right(Vector(CapabilityDocument(_primary_scope, CapabilityModel(Vector.empty, Vector.empty, Vector.empty, Present(Vector.empty)))))
      }
    }

    "reject closed structural and field syntax" which {
      "C64P14 reject misplaced or repeated containers while accepting reserved-looking entry labels" in {
        Given("each structure mutation violates one container role, alongside legal entry presentation labels")
        val invalid = Vector(_primary + "\n# CAPABILITY-MODEL\ncompatibility = {\"state\":\"PRESENT\",\"value\":[]}\n",
          _primary.replace("## APPLICATION-CAPABILITY", "## APPLICATION-CAPABILITY\n## APPLICATION-CAPABILITY"),
          _primary.replace("## APPLICATION-CAPABILITY", "## APPLICATION-CAPABILITY\nextra = true"),
          _primary.replace("### Independent", "### Independent\n#### Child"),
          _primary.replace("# CAPABILITY-MODEL", "# capability-model"),
          _primary.replace("## APPLICATION-CAPABILITY", "## application-capability"),
          _primary.replace("# CAPABILITY-MODEL", "# NOTE\n## CAPABILITY-MODEL"),
          _primary.replace("compatibility =", "unexpected ="), "# APPLICATION-CAPABILITY\n")
        val labels = Vector("CAPABILITY-MODEL", "APPLICATION-CAPABILITY", "COMPONENT-CAPABILITY", "COMPONENT-CAPABILITY-DECLARATION")
          .map(label => _primary.replace("### Independent", s"### $label"))
        When("the source-section roles are interpreted")
        val errors = invalid.map(x => _issues(_normalize(x)))
        val parsed = labels.map(_normalize(_))
        Then("violations return source-contextual Left and entry labels supply no identity facts")
        errors.foreach(_.nonEmpty shouldBe true)
        errors.flatten.foreach(_.scope shouldBe _primary_scope)
        parsed.foreach(_ shouldBe Right(Vector(CapabilityDocument(_primary_scope, _expected_model))))
      }

      "C64P15 reject missing, unknown, repeated and malformed JSON fields without overwrite" in {
        Given("one physical field line or nested JSON object is mutated at a time")
        val invalid = Vector(_primary.replace("name = \"Store capability\"\n", ""),
          _primary.replace("name = \"Store capability\"", "name = \"Store capability\"\nname = \"Other\""),
          _primary.replace("name = \"Store capability\"", "name = \"Store capability\"\nretry = true"),
          _primary.replace("name = \"Store capability\"", "name = bare"),
          _primary.replace("name = \"Store capability\"", "name = \"first\" \"second\""),
          _primary.replace("name = \"Store capability\"", "name = {"),
          _primary.replace("\"elementId\":\"Component:Store|α\"", "\"elementId\":\"Component:Store|α\",\"elementId\":\"forged\""),
          _primary.replace("\"kind\":\"DECLARED\"", "\"kind\":\"DECLARED\",\"kind\":\"DERIVED\""),
          _primary.replace("name = \"Store capability\"", "name = \"line\ncontinued\""),
          _primary.replace("name = \"Store capability\"", "```json\nname = \"Store capability\"\n```"),
          _primary.replace("name = \"Store capability\"", "name = \"Store capability\"\n// comment"))
        When("each actual CML parse is strictly normalized")
        val results = invalid.map(_normalize(_))
        val repeated = invalid.map(_normalize(_))
        Then("all failures are deterministic and carry fields and sections instead of partial successful output")
        repeated shouldBe results
        results.foreach { result =>
          _issues(result).nonEmpty shouldBe true
          _issues(result).foreach(_.scope shouldBe _primary_scope)
        }
        _issues(results(4)).map(_.kind) should contain(MalformedInput)
        _issues(results(6)).map(_.kind) should contain(MalformedInput)
      }

      "C64P16 reject invalid literal text, paths and nonpositive or nonintegral source lines" in {
        Given("invalid text spans identity, versions, names, source revisions, labels and absence details")
        val textvalues = Vector("null", "\"\"", "\" padded\"", "\"padded\\u00a0\"", "\"bad\\u0000text\"")
        val targets = Vector("\"elementId\":\"Component:Store|α\"", "\"sourceRevision\":\"definition-r1\"",
          "\"detail\":\"No detailed prose.\"", "\"text\":\"注文\"", "\"contractVersion\":\"contract:二\"")
        val texts = targets.flatMap { target => textvalues.map(value => _primary.replace(target, target.take(target.indexOf(':') + 1) + value)) } ++
          textvalues.map(value => _field(_primary, "contractVersion", value))
        val paths = Vector("/absolute", "C:/drive", "uri:thing", "a\\b", "a//b", "a/./b", "a/../b", "a/", "a\u0001b")
          .map(path => _primary.replace("\"path\":\"model/capability-model.cml\"", "\"path\":" + Json.stringify(JsString(path))))
        val lines = Vector("0", "-1", "1.5", "2147483648", "true", "\"12\"")
          .map(value => _primary.replace("\"line\":12", "\"line\":" + value))
        When("the closed scalar and source decoders interpret each variant")
        val results = (texts ++ paths ++ lines).map(_normalize(_))
        val sourceresults = results.drop(texts.size)
        Then("every invalid authored scalar fails without trimming, normalizing or revising it")
        results.foreach(x => _issues(x).nonEmpty shouldBe true)
        sourceresults.foreach(x => _issues(x).map(_.kind) should contain(InvalidSource))
      }

      "C64P16a retain literal edge spaces in scope, direct source and foreign Derived paths" in {
        Given("canonical relative POSIX paths contain literal ASCII or Unicode edge spaces")
        val paths = Vector(" path/能力.cml ", "\u00a0path/能力.cml\u00a0")
        val sources = paths.map { path =>
          val encoded = Json.stringify(JsString(path))
          val owned = _primary.replace("\"path\":\"model/capability-model.cml\"", "\"path\":" + encoded)
          val foreign = _primary.replace("\"path\":\"model/capability-model-secondary.cml\"", "\"path\":" + encoded)
          (path, owned, foreign, _primary_scope.copy(path = path))
        }
        When("the actual CML parser and normalization interpret matching owned scopes and independent foreign inputs")
        val outcomes = sources.map { case (path, owned, foreign, scope) =>
          (path, _documents(_normalize(owned, scope)).head, _documents(_normalize(foreign)).head)
        }
        Then("the exact path literals survive without trimming and foreign evidence retains its independent source facts")
        outcomes.foreach { case (path, owned, foreign) =>
          owned.scope.path shouldBe path
          owned.model.applicationCapabilities.foreach(_.source.path shouldBe path)
          owned.model.componentCapabilities.foreach(_.source.path shouldBe path)
          owned.model.componentDeclarations.foreach(_.source.path shouldBe path)
          _present(owned.model.componentCapabilities.head.grounding).last.source.path shouldBe path
          _present(owned.model.componentCapabilities.head.grounding).last.origin shouldBe
            Derived("rule:discovery|α", Vector(
              CapabilitySource("Other:能力/B", "model/capability-model-secondary.cml", "foreign-edge-r9", Some(8)),
              CapabilitySource("Model:能力/A", path, "evidence-r2", Some(91))))
          foreign.scope shouldBe _primary_scope
          foreign.model.componentCapabilities.head.source shouldBe _source("definition-r1", Some(12))
          _present(foreign.model.componentCapabilities.head.grounding).last.origin shouldBe
            Derived("rule:discovery|α", Vector(
              CapabilitySource("Other:能力/B", path, "foreign-edge-r9", Some(8)), _source("evidence-r2", Some(91))))
        }
      }

      "C64P16b reject URI prefixes followed by Unicode line or paragraph separators in every source role" in {
        Given("URI-prefixed paths contain U+2028 or U+2029 after the scheme and remain single-line JSON fields")
        val paths = Vector("urn:x\u2028y", "urn:x\u2029y")
        val sources = paths.map { path =>
          val encoded = Json.stringify(JsString(path)).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
          val owned = _primary.replace("\"path\":\"model/capability-model.cml\"", "\"path\":" + encoded)
          val foreign = _primary.replace("\"path\":\"model/capability-model-secondary.cml\"", "\"path\":" + encoded)
          (owned, foreign, _primary_scope.copy(path = path))
        }
        When("the actual CML parser feeds invalid scopes, matching direct sources and foreign Derived inputs to normalization")
        val results = sources.flatMap { case (owned, foreign, scope) =>
          Vector(_normalize(_primary, scope), _normalize(owned, scope), _normalize(foreign))
        }
        Then("every complete result is Left with InvalidSource instead of admitting a URI-prefixed path")
        results.foreach(x => _issues(x).map(_.kind) should contain(InvalidSource))
      }

      "C64P17 reject noncanonical Component namespace and local ID" in {
        Given("Component identity uses the shared namespace and local-ID domain")
        val invalid = Vector(_primary.replace("org.simplemodeling.textus", "bad namespace"),
          _primary.replace("\"id\":\"Store\"", "\"id\":\"invalid local\""),
          _primary.replace("\"id\":\"Store\"", "\"id\":\"\""))
        When("the canonical identity parsers decode the authored Component references")
        val failures = invalid.map(_normalize(_))
        val valid = _documents(_normalize(_primary)).head.model.componentDeclarations.head.component
        Then("invalid domain values fail and the valid carrier is the existing canonical Java ComponentId")
        failures.foreach(x => _issues(x).nonEmpty shouldBe true)
        valid shouldBe _component("Store")
        valid.namespace().value() shouldBe "org.simplemodeling.textus"
        valid.localId().value() shouldBe "Store"
      }

      "C64P18 reject wrong reference domains and unknown closed branches" in {
        Given("each wrong-kind or unknown tag violates its role-specific authored schema")
        val mutations = Vector("\"kind\":\"COMPONENT\"" -> "\"kind\":\"APPLICATION\"",
          "\"kind\":\"APPLICATION\"" -> "\"kind\":\"COMPONENT\"", "\"direction\":\"PROVIDED\"" -> "\"direction\":\"provided\"",
          "direction = \"PROVIDED\"" -> "direction = \"MAYBE\"", "\"role\":\"USE_CASE\"" -> "\"role\":\"COMPONENT\"",
          "\"role\":\"SPECIFICATION\"" -> "\"role\":\"GOAL\"", "\"targetKind\":\"OPERATION\"" -> "\"targetKind\":\"TASK\"",
          "\"reason\":\"NotRepresented\"" -> "\"reason\":\"UNKNOWN\"", "\"state\":\"PRESENT\"" -> "\"state\":\"present\"",
          "\"kind\":\"DECLARED\"" -> "\"kind\":\"INFERRED\"", "\"boundary\":\"LOCAL\"" -> "\"boundary\":\"local\"",
          "\"kind\":\"MODEL\"" -> "\"kind\":\"UNKNOWN\"",
          "\"targetKind\":\"OPERATION\"" -> "\"targetKind\":\"OPERATION\",\"authorization\":true",
          "\"targetKind\":\"WORKFLOW\"" -> "\"targetKind\":\"WORKFLOW\",\"retry\":3",
          "\"targetKind\":\"STATE_MACHINE\"" -> "\"targetKind\":\"STATE_MACHINE\",\"order\":1")
        val mixed = _field(_primary, "summary", "{\"state\":\"PRESENT\",\"value\":\"text\",\"reason\":\"NotDeclared\"}")
        val originmixed = _primary.replace("\"kind\":\"DECLARED\"", "\"kind\":\"DECLARED\",\"ruleId\":\"rule\"")
        val emptyinputs = _primary.replace("\"kind\":\"DECLARED\"", "\"kind\":\"DERIVED\",\"ruleId\":\"rule\",\"inputs\":[]")
        When("role-sensitive typed decoders normalize these source variants")
        val results = mutations.map { case (before, after) => _normalize(_primary.replace(before, after)) } ++
          Vector(mixed, originmixed, emptyinputs).map(_normalize(_))
        Then("none substitutes a reference domain or supplies an enum or origin default")
        results.foreach(x => _issues(x).nonEmpty shouldBe true)
      }

      "C64P19 enforce own authority and path while retaining independent revisions and foreign Derived inputs" in {
        Given("direct sources and definition model IDs must match the explicit SourceInput scope")
        val invalid = Vector(_primary.replace("\"modelAuthority\":\"Model:能力/A\"", "\"modelAuthority\":\"wrong\""),
          _primary.replace("\"path\":\"model/capability-model.cml\"", "\"path\":\"wrong.cml\""),
          _primary.replace("\"modelId\":\"Model:能力/A\"", "\"modelId\":\"wrong\""))
        val changed = _primary.replace("definition-r1", "definition:changed")
        When("owned source facts and independent origin inputs are normalized")
        val failures = invalid.map(_normalize(_))
        val valid = _documents(_normalize(changed)).head.model
        Then("direct ownership mismatches fail but revision equality and current-file Derived ownership are never required")
        failures.foreach(x => _issues(x).map(_.kind) should contain(InvalidSource))
        valid.componentCapabilities.head.source.sourceRevision shouldBe "definition:changed"
        _present(valid.componentCapabilities.head.grounding).last.source.sourceRevision shouldBe "ground-term-r6"
        _present(valid.componentCapabilities.head.grounding).last.origin shouldBe _derived
      }

      "C64P20 refuse parser failure, malformed scope and repeated input paths without sibling salvage" in {
        Given("an actual malformed ENV parse, malformed explicit scopes and duplicate paths alongside a valid sibling")
        val invalidscopes = Vector(SourceScope("", "model/valid.cml"), SourceScope(" authority", "model/valid.cml"),
          SourceScope("valid", "../bad.cml"), null)
        When("actual Kaleidox parser outcomes are supplied to the normalization entry")
        val malformed = KaleidoxModel.parse(KaleidoxConfig.default, "# ENV\nvalue = ]\n")
        val valid = SourceInput(_primary_scope, KaleidoxModel.parse(KaleidoxConfig.default, _primary))
        val parserfailure = CmlCapabilityCml.normalize(Vector(SourceInput(_secondary_scope, malformed), valid))
        val scopefailures = invalidscopes.map(scope => CmlCapabilityCml.normalize(Vector(SourceInput(scope,
          KaleidoxModel.parse(KaleidoxConfig.default, "# NOTE\nlegacy\n")), valid)))
        val duplicate = CmlCapabilityCml.normalize(Vector(valid, valid.copy(scope = _primary_scope.copy(modelAuthority = "other"))))
        Then("failure precedes absence detection and the complete result remains Left")
        malformed.errors.nonEmpty shouldBe true
        _issues(parserfailure).head.kind shouldBe MalformedInput
        _issues(parserfailure).head.scope shouldBe _secondary_scope
        scopefailures.foreach(x => _issues(x).map(_.kind) should contain(InvalidSource))
        _issues(duplicate).map(_.kind) should contain(InvalidSource)
      }
    }

    "preserve parsed facts for later semantic validation" which {
      "C64P21 retain duplicate identities and keys, cycles, dangling Local targets and repeated-key disagreement" in {
        Given("structurally complete authored facts intentionally violate later semantic constraints")
        val source = _primary.replace("Application:Two", "Application:One")
          .replace("\"modelId\":\"Other:能力/B\",\"elementId\":\"Application:Remote\"", "\"modelId\":\"Model:能力/A\",\"elementId\":\"Application:One\"")
          .replace("\"direction\":\"PROVIDED\"", "\"direction\":\"REQUIRED\"")
          .replace("\"id\":\"Archive\"", "\"id\":\"Store\"")
          .replace("\"boundary\":\"EXTERNAL\"", "\"boundary\":\"LOCAL\"")
        When("syntax normalization preserves these facts without semantic admission")
        val model = _documents(_normalize(source)).head.model
        Then("duplicates and unresolved graph facts remain observable for CAP-64-03 with no winner selected")
        model.applicationCapabilities.tail.map(_.identity) shouldBe Vector.fill(2)(_id("Application:One"))
        model.componentDeclarations.take(2).map(x => (x.component, x.direction, x.capability)).distinct.size shouldBe 1
        val first = model.componentDeclarations.head
        _present(first.realizations).head.declarationKey.direction shouldBe Required
        first.direction shouldBe Provided
        _present(model.applicationCapabilities(1).dependencies).head.target.identity shouldBe _id("Application:One")
        _present(model.applicationCapabilities(1).dependencies).head.boundary shouldBe Local
        _present(first.realizations).head.target.identity shouldBe _id("operation:save")
      }

      "C64P22 retain enclosing Application endpoints and independent edge roles" in {
        Given("Application mappings, dependencies, discovery, grounding and traceability are independent lists")
        val source = _primary
        When("the original enclosing records are normalized")
        val application = _documents(_normalize(source)).head.model.applicationCapabilities(1)
        Then("full independent edges retain their source and origin without traceability inference")
        application shouldBe _expected_model.applicationCapabilities(1)
        _present(application.discoveryReferences).map(_.role) shouldBe Vector(Scenario, Component)
        _present(application.traceability).map(_.role) shouldBe Vector(SpecificationTrace, EvidenceTrace)
        _present(application.componentMappings).size shouldBe 2
        _present(application.dependencies).size shouldBe 1
        _present(application.traceability).size shouldBe 2
      }

      "C64P23 retain directed compatibility and explicit per-document absence" in {
        Given("the first document authors two directed edges and the second explicitly lacks compatibility representation")
        val sources = Vector(_primary -> _primary_scope, _secondary -> _secondary_scope)
        When("each document's compatibility Presence is normalized independently")
        val documents = _documents(_normalize_all(sources))
        Then("only authored edge directions survive, with no reverse or transitive closure")
        documents.head.model.compatibility shouldBe _expected_model.compatibility
        _present(documents.head.model.compatibility).map(x => x.requiredVersion -> x.providedVersion) shouldBe
          Vector("contract:一" -> "contract:二", "contract:二" -> "contract:三")
        documents(1).model.compatibility shouldBe Absent(NotRepresented, "Compatibility unavailable in this document.")
      }
    }
  }

  private val _primary_resource = "src/test/resources/modeler/capability-model.cml"
  private val _secondary_resource = "src/test/resources/modeler/capability-model-secondary.cml"
  private def _primary: String = new String(Files.readAllBytes(Paths.get(_primary_resource)), StandardCharsets.UTF_8)
  private def _secondary: String = new String(Files.readAllBytes(Paths.get(_secondary_resource)), StandardCharsets.UTF_8)
  private def _primary_scope: SourceScope = SourceScope("Model:能力/A", "model/capability-model.cml")
  private def _secondary_scope: SourceScope = SourceScope("Other:能力/B", "model/capability-model-secondary.cml")
  private def _id(element: String): ModelElementId = ModelElementId("Model:能力/A", element)
  private def _source(revision: String, line: Option[Int] = None): CapabilitySource =
    CapabilitySource("Model:能力/A", "model/capability-model.cml", revision, line)
  private def _derived: CapabilityOrigin = Derived("rule:discovery|α", Vector(
    CapabilitySource("Other:能力/B", "model/capability-model-secondary.cml", "foreign-edge-r9", Some(8)), _source("evidence-r2", Some(91))))
  private def _component(localid: String): ComponentId = ComponentId.of(
    ComponentNamespace.parse("org.simplemodeling.textus").value().get(), ComponentLocalId.parse(localid).value().get())
  private def _capability: VersionedComponentCapabilityReference = VersionedComponentCapabilityReference(_id("Component:Store|α"), "contract:二")
  private def _link(role: ModelRole, element: String, boundary: CmlSemanticFoundation.ReferenceBoundary, revision: String, origin: CapabilityOrigin): ModelLink =
    ModelLink(role, ModelReference(_id(element), boundary), _source(revision), origin)
  private def _expected_traceability: Vector[TraceabilityReference] = Vector(
    TraceabilityReference(SpecificationTrace, ModelReference(_id("spec:1"), External), _source("spec-r3"), Declared),
    TraceabilityReference(EvidenceTrace, ModelReference(_id("evidence:1"), External), _source("evidence-r4"), _derived))
  private def _expected_grounding: Vector[GroundingReference] = Vector(
    GroundingReference(ModelTarget(_id("concept:Order")), Local, Present(RelationId("vocab:关系", "rel:uses|α")),
      Present(ProfileId("profile:scope", "profile:one")), Present("Domain context"), Present(LocalizedLabel("注文", None)), _source("ground-model-r5", Some(37)), Declared),
    GroundingReference(TermTarget(TermId("BoK:語彙", "term:注文|α")), External, Absent(NotDeclared, "No declared relation."),
      Absent(Unsupported, "No profile representation."), Absent(NotApplicable, "No context applies."),
      Present(LocalizedLabel("Order", Some("en-X-literal"))), _source("ground-term-r6"), _derived))
  private def _expected_realizations: Vector[RealizationEdge] = Vector(
    RealizationEdge(DeclarationKey(_component("Store"), Provided, _capability), Operation, ModelReference(_id("operation:save"), Local),
      _component("Store"), "operation:v3", "operation.abi:v7", _source("realization-r15", Some(56)), Declared),
    RealizationEdge(DeclarationKey(_component("Store"), Provided, _capability), Workflow, ModelReference(_id("workflow:save"), External),
      _component("Store"), "workflow:v4", "workflow.abi:v8", _source("realization-r16"), _derived),
    RealizationEdge(DeclarationKey(_component("Store"), Provided, _capability), StateMachine, ModelReference(_id("state:store"), Local),
      _component("Store"), "state:v5", "state.abi:v9", _source("realization-r17", Some(58)), Declared))
  private def _expected_application(element: String): ApplicationCapability = ApplicationCapability(
    _id(element), "app:v1", "Same presentation", Present("Summary = literal"), Absent(NotRepresented, "No detailed prose."), _source("definition-r1", Some(12)),
    Present(Vector(_link(UseCase, "usecase:one", Local, "demand-r1", Declared), _link(Goal, "goal:two", Local, "demand-r2", _derived))),
    Present(Vector(_link(Scenario, "scenario:one", External, "discover-r7", _derived), _link(Component, "component:model", External, "discover-r8", Declared))),
    Present(Vector(ApplicationComponentMapping(_capability, Local, _source("mapping-r10"), Declared),
      ApplicationComponentMapping(VersionedComponentCapabilityReference(ModelElementId("Other:能力/B", "Component:Remote"), "contract:二"), External, _source("mapping-r11"), _derived))),
    Present(Vector(ApplicationDependency(VersionedApplicationCapabilityReference(ModelElementId("Other:能力/B", "Application:Remote"), "app:v1"), External, _source("dependency-r12"), Declared))),
    Present(_expected_grounding), Present(_expected_traceability))
  private def _expected_model: CapabilityModel = {
    val independent = _expected_application("Application:Independent").copy(demandReferences = Present(Vector.empty), componentMappings = Present(Vector.empty),
      dependencies = Absent(NotDeclared, "No dependency declared."), discoveryReferences = Absent(Unsupported, "Discovery not represented."),
      grounding = Absent(NotApplicable, "Grounding does not apply."), traceability = Present(Vector.empty))
    val component = ComponentCapability(_id("Component:Store|α"), "contract:二", "Store capability", Present("Summary = literal"),
      Absent(NotRepresented, "No detailed prose."), _source("definition-r1", Some(12)),
      Present(Vector(_link(UseCaseSlice, "slice:1", External, "component-discovery-r13", Declared))), Present(_expected_grounding), Present(_expected_traceability))
    val declarations = Vector(ComponentDeclaration(_component("Store"), Provided, _capability, _source("declaration-r14", Some(44)), Present(_expected_realizations), Present(_expected_traceability)),
      ComponentDeclaration(_component("Archive"), Provided, _capability, _source("declaration-r14", Some(44)), Present(Vector.empty), Present(_expected_traceability)),
      ComponentDeclaration(_component("Store"), Required, _capability, _source("declaration-r14", Some(44)), Absent(NotRepresented, "No required realization representation."), Present(_expected_traceability)))
    CapabilityModel(Vector(independent, _expected_application("Application:One"), _expected_application("Application:Two")), Vector(component), declarations,
      Present(Vector(CompatibilityEdge(_id("Component:Store|α"), "contract:一", "contract:二", _source("compatibility-r18"), Declared),
        CompatibilityEdge(_id("Component:Store|α"), "contract:二", "contract:三", _source("compatibility-r19"), _derived))))
  }
  private def _field(source: String, name: String, value: String): String =
    source.split("\n", -1).map(line => if (line.startsWith(name + " =")) s"$name = $value" else line).mkString("\n")
  private def _normalize(source: String, scope: SourceScope = _primary_scope, located: Boolean = true): Either[Vector[SyntaxIssue], Vector[CapabilityDocument]] = {
    val config = if (located) KaleidoxConfig.default else KaleidoxConfig.default.withoutLocation
    CmlCapabilityCml.normalize(Vector(SourceInput(scope, KaleidoxModel.parse(config, source))))
  }
  private def _normalize_all(sources: Vector[(String, SourceScope)]): Either[Vector[SyntaxIssue], Vector[CapabilityDocument]] =
    CmlCapabilityCml.normalize(sources.map { case (source, scope) => SourceInput(scope, KaleidoxModel.parse(KaleidoxConfig.default, source)) })
  private def _documents(result: Either[Vector[SyntaxIssue], Vector[CapabilityDocument]]): Vector[CapabilityDocument] = result match {
    case Right(documents) => documents
    case Left(issues) => fail(issues.mkString("; "))
  }
  private def _issues(result: Either[Vector[SyntaxIssue], Vector[CapabilityDocument]]): Vector[SyntaxIssue] = result match {
    case Left(issues) => issues
    case Right(documents) => fail(s"Expected syntax failure, received $documents")
  }
  private def _present[A](presence: Presence[A]): A = presence match {
    case Present(value) => value
    case absence => fail(s"Expected explicit presence, received $absence")
  }
}
