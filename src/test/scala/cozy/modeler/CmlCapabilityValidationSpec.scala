package cozy.modeler

import org.goldenport.cncf.component.identity.{ComponentId, ComponentNamespace, ComponentLocalId}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import CmlSemanticFoundation.{Presence, Present, Absent, AbsenceReason, NotDeclared, NotRepresented, NotApplicable, Local, External, ReferenceBoundary, ModelElementId, TermId, RelationId, ProfileId, ModelTarget, TermTarget, LocalizedLabel}
import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope._
import CmlCapabilityValidationScope.DiagnosticKind._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityValidationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CmlCapabilityValidationSpec._

  "Finite source-bearing Capability semantic admission" should {
    "preserve qualified snapshots and independent declaration keys" which {
      "C64V06 reject equal, version-changing, cross-file and cross-family current definitions" in {
        Given("repeated current qualified identities with equal or differing records, including another source file and opposite family")
        val usage = _usage("same")
        val other = usage.copy(source = _source("other-revision", path = "model/other.cml"))
        val cases = Vector(
          Vector(_document(usages = Vector(usage, usage))),
          Vector(_document(usages = Vector(usage, usage.copy(contractVersion = "v2")))),
          Vector(_document(usages = Vector(usage)), _document(usages = Vector(other), path = "model/other.cml")),
          Vector(_document(usages = Vector(usage), components = Vector(_definition("same")))))
        When("each supplied snapshot is validated")
        val results = cases.map(_validate(_))
        Then("every repeat is a DuplicateDefinition without a version or source winner")
        results.foreach { result =>
          val issues = _issues(result)
          issues.exists(_.kind == DuplicateDefinition) shouldBe true
          issues.find(_.kind == DuplicateDefinition).get.relatedsources shouldBe Vector(usage.source)
        }
      }

      "C64V07 accept distinct providers and directions but reject a full declaration repeat" in {
        Given("two providers and one requiring declaration use the same exact Component Capability version")
        val declarations = Vector(_declaration(), _declaration(component = _component("Archive")), _declaration(direction = Required))
        val document = _document(components = Vector(_definition()), declarations = declarations)
        When("the three distinct keys and a repeated full key are validated separately")
        val accepted = _validate(Vector(document))
        val repeated = _validate(Vector(document.copy(model = document.model.copy(componentDeclarations = declarations :+ declarations.head))))
        Then("all three architectural facts survive and the repeated tuple carries its previous source")
        _admitted(accepted).documents shouldBe Vector(document)
        _admitted(accepted).documents.head.model.componentDeclarations shouldBe declarations
        val issue = _issues(repeated).find(_.kind == DuplicateDeclaration).get
        issue.source shouldBe Some(declarations.head.source)
        issue.relatedsources shouldBe Vector(declarations.head.source)
      }
    }

    "resolve only exact Local role and vocabulary candidates" which {
      "C64V08 enforce all four demand and seven discovery roles with edge-specific failures" in {
        Given("every legal role has a qualified Local fact, with missing, wrong-kind and duplicate alternatives")
        val roles = Vector(Goal -> ModelKind.Goal, UseCase -> ModelKind.UseCase, Scenario -> ModelKind.Scenario,
          UseCaseSlice -> ModelKind.UseCaseSlice, Component -> ModelKind.Component, Specification -> ModelKind.Specification, Evidence -> ModelKind.Evidence)
        val links = roles.zipWithIndex.map { case ((role, _), index) => _link(role, s"role:$index") }
        val facts = roles.zipWithIndex.map { case ((_, kind), index) => ModelFact(_id(s"role:$index"), kind, _source(s"fact:$index")) }
        val usage = _usage().copy(demandReferences = Present(links.take(4)), discoveryReferences = Present(links))
        val document = _document(usages = Vector(usage))
        val cases = roles.indices.map { index =>
          val fact = facts(index)
          Vector(_scope.copy(modelfacts = facts.filterNot(_ == fact)),
            _scope.copy(modelfacts = facts.updated(index, fact.copy(kind = ModelKind.DomainConcept))),
            _scope.copy(modelfacts = facts :+ fact))
        }.toVector
        When("the exact role matrix and every failing alternative are validated")
        val valid = _validate(Vector(document), _scope.copy(modelfacts = facts))
        val failures = cases.map(_.map(scope => _validate(Vector(document), scope)))
        val illegal = _validate(Vector(_document(usages = Vector(_usage().copy(demandReferences = Present(Vector(_link(Component, "role:4"))))))), _scope.copy(modelfacts = facts))
        Then("all legal edges survive while each missing or wrong-kind edge retains its own source and ambiguity never chooses a candidate")
        _admitted(valid).documents shouldBe Vector(document)
        failures.zipWithIndex.foreach { case (results, index) =>
          _issues(results(0)).exists(x => x.kind == DanglingReference && x.source.contains(links(index).source)) shouldBe true
          _issues(results(1)).exists(x => x.kind == WrongKind && x.source.contains(links(index).source)) shouldBe true
          _issues(results(2)).exists(x => x.kind == Ambiguous && x.source.contains(links(index).source)) shouldBe true
        }
        _issues(illegal).exists(_.kind == WrongKind) shouldBe true
      }

      "C64V09 keep Specification and Evidence independent on definitions and declarations" in {
        Given("separate Local traceability lists on Usage, Component and declaration with exact role facts")
        val traces = Vector(_trace(SpecificationTrace, "spec"), _trace(EvidenceTrace, "evidence"))
        val facts = Vector(ModelFact(_id("spec"), ModelKind.Specification, _source("spec-fact")), ModelFact(_id("evidence"), ModelKind.Evidence, _source("evidence-fact")))
        val document = _document(usages = Vector(_usage().copy(traceability = Present(traces))),
          components = Vector(_definition().copy(traceability = Present(traces))), declarations = Vector(_declaration().copy(traceability = Present(traces))))
        val scopes = Vector(_scope.copy(modelfacts = facts), _scope.copy(modelfacts = facts.take(1)),
          _scope.copy(modelfacts = facts.map(_.copy(kind = ModelKind.Goal))), _scope.copy(modelfacts = facts ++ facts))
        When("the traceability lists are resolved without treating them as execution evidence")
        val results = scopes.map(_validate(Vector(document), _))
        Then("the exact successful lists remain independent and each failed role is typed and source-correlated")
        _admitted(results.head).documents shouldBe Vector(document)
        _issues(results(1)).count(_.kind == DanglingReference) shouldBe 3
        _issues(results(2)).count(_.kind == WrongKind) shouldBe 6
        _issues(results(3)).forall(_.kind == Ambiguous) shouldBe true
        _issues(results(1)).forall(_.source.contains(traces.last.source)) shouldBe true
      }

      "C64V10 resolve Model and BoK Term grounding in distinct qualified domains" in {
        Given("Local DomainConcept and Term grounding, with literal BoK vocabulary authority and missing or duplicate catalogs")
        val model = _ground(ModelTarget(_id("concept")))
        val termid = TermId("BoK:語彙", "term:注文|α")
        val term = _ground(TermTarget(termid))
        val fact = ModelFact(_id("concept"), ModelKind.DomainConcept, _source("domain-fact"))
        val termfact = TermFact(termid, _source("term-fact", authority = termid.vocabularyId, path = "catalog/term.cml"))
        val document = _document(components = Vector(_definition().copy(grounding = Present(Vector(model, term)))))
        val scope = _scope.copy(modelfacts = Vector(fact), termfacts = Vector(termfact))
        When("the exact scope and each missing or ambiguous domain are validated")
        val valid = _validate(Vector(document), scope)
        val failures = Vector(scope.copy(modelfacts = Vector.empty), scope.copy(termfacts = Vector.empty),
          scope.copy(modelfacts = Vector(fact, fact)), scope.copy(termfacts = Vector(termfact, termfact))).map(_validate(Vector(document), _))
        Then("the whole original grounding survives, absence is dangling and equal duplicates are ambiguous")
        _admitted(valid).documents shouldBe Vector(document)
        failures.take(2).foreach(x => _issues(x).exists(_.kind == DanglingReference) shouldBe true)
        failures.drop(2).foreach(x => _issues(x).exists(_.kind == Ambiguous) shouldBe true)
        _admitted(valid).scope.termfacts.head.identity.vocabularyId shouldBe "BoK:語彙"
      }

      "C64V11 resolve present Relation and Profile domains only for Local grounding" in {
        Given("Local grounding has explicit Relation and Profile references while External grounding has unknown vocabulary targets")
        val relation = RelationId("relations:α", "uses")
        val profile = ProfileId("profiles:β", "one")
        val grounding = _ground(ModelTarget(_id("concept"))).copy(relation = Present(relation), profile = Present(profile))
        val relationfact = RelationFact(relation, _source("relation-fact", authority = relation.vocabularyId))
        val profilefact = ProfileFact(profile, _source("profile-fact", authority = profile.vocabularyId))
        val scope = _scope.copy(modelfacts = Vector(ModelFact(_id("concept"), ModelKind.DomainConcept, _source("concept-fact"))),
          relationfacts = Vector(relationfact), profilefacts = Vector(profilefact))
        val document = _document(components = Vector(_definition().copy(grounding = Present(Vector(grounding)))))
        val external = document.copy(model = document.model.copy(componentCapabilities = Vector(_definition().copy(grounding = Present(Vector(grounding.copy(boundary = External)))))))
        When("Local catalog failures and the explicitly External edge are validated")
        val valid = _validate(Vector(document), scope)
        val missing = Vector(scope.copy(relationfacts = Vector.empty), scope.copy(profilefacts = Vector.empty)).map(_validate(Vector(document), _))
        val duplicate = Vector(scope.copy(relationfacts = Vector(relationfact, relationfact)), scope.copy(profilefacts = Vector(profilefact, profilefact))).map(_validate(Vector(document), _))
        val unresolved = _validate(Vector(external))
        Then("Local vocabulary is exact, duplicates fail and External grounding remains explicitly unresolved")
        _admitted(valid).documents shouldBe Vector(document)
        missing.foreach(x => _issues(x).exists(_.kind == DanglingReference) shouldBe true)
        duplicate.foreach(x => _issues(x).exists(_.kind == Ambiguous) shouldBe true)
        _admitted(unresolved).documents shouldBe Vector(external)
      }

      "C64V12 require exact family and version for Local mapping and dependency" in {
        Given("a Usage maps to a Component and depends on another Usage through two independent source-bearing edges")
        val mapping = ComponentUsageCapabilityMapping(VersionedComponentCapabilityReference(_id("component"), "v1"), Local, _source("mapping"), Declared)
        val dependency = ComponentUsageCapabilityDependency(VersionedComponentUsageCapabilityReference(_id("remote"), "v1"), Local, _source("dependency"), Declared)
        val usage = _usage().copy(componentMappings = Present(Vector(mapping)), dependencies = Present(Vector(dependency)))
        val document = _document(usages = Vector(usage, _usage("remote")), components = Vector(_definition()))
        val cases = Vector(document.copy(model = document.model.copy(componentCapabilities = Vector.empty)),
          document.copy(model = document.model.copy(componentUsageCapabilities = Vector(usage))),
          document.copy(model = document.model.copy(componentCapabilities = Vector(_definition().copy(contractVersion = "v2")))),
          document.copy(model = document.model.copy(componentUsageCapabilities = Vector(usage, _usage("remote").copy(contractVersion = "v2")))),
          _document(usages = Vector(usage, _usage("component")), components = Vector(_definition("remote"))))
        val external = _document(usages = Vector(usage.copy(componentMappings = Present(Vector(mapping.copy(boundary = External))),
          dependencies = Present(Vector(dependency.copy(boundary = External))))))
        When("exact, missing, version-changing, opposite-family and External references are validated")
        val valid = _validate(Vector(document))
        val failures = cases.map(x => _validate(Vector(x)))
        val unresolved = _validate(Vector(external))
        Then("only exact Local families and versions resolve; External links never require invented catalog facts")
        _admitted(valid).documents shouldBe Vector(document)
        failures.take(2).foreach(x => _issues(x).exists(_.kind == DanglingReference) shouldBe true)
        failures.slice(2, 4).foreach(x => _issues(x).exists(_.kind == Incompatible) shouldBe true)
        _issues(failures.last).count(_.kind == WrongKind) shouldBe 2
        _admitted(unresolved).documents shouldBe Vector(external)
      }

      "C64V13 require a unique exact Component declaration version independently of compatibility" in {
        Given("a declaration requiring historical v0 and an independently supplied direct v0-to-v1 compatibility edge")
        val declaration = _declaration(version = "v0")
        val edge = CompatibilityEdge(_id("component"), "v0", "v1", _source("compatibility"), Declared)
        val document = _document(components = Vector(_definition()), declarations = Vector(declaration), compatibility = Present(Vector(edge)))
        val history = ComponentContract(_id("component"), "v0", _source("historical"))
        val scope = _scope.copy(componentcontracts = Vector(history))
        val wrongfamily = _scope.copy(usagecontracts = Vector(ComponentUsageContract(history.identity, history.contractversion, history.source)))
        When("the same declaration is validated with and without the exact historical Component contract")
        val valid = _validate(Vector(document), scope)
        val absent = _validate(Vector(document))
        val wrong = _validate(Vector(document), wrongfamily)
        val missing = _validate(Vector(_document(declarations = Vector(declaration))))
        Then("history resolves exactly but compatibility cannot replace missing identity, family or version evidence")
        _admitted(valid).documents shouldBe Vector(document)
        _issues(absent).exists(x => x.kind == Incompatible && x.fieldpath.contains("componentDeclarations")) shouldBe true
        _issues(wrong).exists(_.kind == WrongKind) shouldBe true
        _issues(missing).exists(_.kind == DanglingReference) shouldBe true
      }
    }

    "bind complete declaration and executable target tuples" which {
      "C64V14 reject a mismatch in every repeated declaration key field" in {
        Given("a realization repeats the enclosing complete key with one changed field at a time")
        val edge = _realization()
        val key = edge.declarationKey
        val keys = Vector(key.copy(component = _component("Archive")), key.copy(direction = Required),
          key.copy(capability = key.capability.copy(identity = _id("other"))),
          key.copy(capability = key.capability.copy(identity = ModelElementId("Other:model", "component"))),
          key.copy(capability = key.capability.copy(contractVersion = "v2")))
        val documents = keys.map(changed => _document(components = Vector(_definition()), declarations = Vector(_declaration(realizations = Present(Vector(edge.copy(declarationKey = changed)))))))
        When("each repeated tuple is validated against its enclosing declaration")
        val results = documents.map(document => _validate(Vector(document), _scope.copy(targetcontracts = Vector(_target()))))
        Then("each mismatch fails at the realization key and retains the offending realization source")
        results.foreach { result =>
          val issue = _issues(result).find(_.fieldpath.endsWith("declarationKey")).get
          issue.kind shouldBe Malformed
          issue.source shouldBe Some(edge.source)
        }
      }

      "C64V15 bind all three exact target kinds and reject missing identity or owning Component for either boundary" in {
        Given("Operation, Workflow and StateMachine realizations with Local or External boundaries and supplied exact target contracts")
        val kinds = Vector(Operation, Workflow, StateMachine)
        val cases = kinds.flatMap(kind => Vector(Local, External).map(boundary =>
          (_realization(kind = kind, boundary = boundary), _target(kind = kind))))
        When("exact targets and missing or differently owned targets are validated")
        val results = cases.map { case (edge, target) =>
          val document = _document(components = Vector(_definition()), declarations = Vector(_declaration(realizations = Present(Vector(edge)))))
          (edge, target, _validate(Vector(document), _scope.copy(targetcontracts = Vector(target))),
            _validate(Vector(document)), _validate(Vector(document), _scope.copy(targetcontracts = Vector(target.copy(component = _component("Archive"))))))
        }
        Then("every exact pair binds once and External realizations require the same exact evidence as Local realizations")
        results.foreach { case (edge, target, valid, missing, owner) =>
          _admitted(valid).bindings shouldBe Vector(edge -> target)
          _issues(missing).exists(x => x.kind == DanglingReference && x.source.contains(edge.source)) shouldBe true
          _issues(owner).exists(_.kind == DanglingReference) shouldBe true
        }
      }

      "C64V16 distinguish target kind, contract version, ABI and duplicate exact candidates" in {
        Given("one realization and target facts that independently change kind, opaque version, supplied ABI or multiplicity")
        val edge = _realization()
        val target = _target()
        val document = _document(components = Vector(_definition()), declarations = Vector(_declaration(realizations = Present(Vector(edge)))))
        val scopes = Vector(_scope.copy(targetcontracts = Vector(target.copy(kind = Workflow))),
          _scope.copy(targetcontracts = Vector(target.copy(contractversion = "different-contract"))),
          _scope.copy(targetcontracts = Vector(target.copy(generatedabiversion = "different-abi"))),
          _scope.copy(targetcontracts = Vector(target, target)))
        When("each independent target mismatch is semantically validated")
        val results = scopes.map(_validate(Vector(document), _))
        val valid = _validate(Vector(document), _scope.copy(targetcontracts = Vector(target)))
        Then("kind, version, ABI and ambiguity have distinct typed or field-specific diagnostics while an arbitrary supplied ABI binds exactly")
        _issues(results(0)).exists(x => x.kind == WrongKind && x.fieldpath.endsWith("targetKind")) shouldBe true
        _issues(results(1)).exists(x => x.kind == Incompatible && x.fieldpath.endsWith("targetContractVersion")) shouldBe true
        _issues(results(2)).exists(x => x.kind == Incompatible && x.fieldpath.endsWith("generatedAbiVersion")) shouldBe true
        _issues(results(3)).exists(_.kind == Ambiguous) shouldBe true
        _admitted(valid).bindings shouldBe Vector(edge -> target)
        _admitted(valid).bindings.head._2.generatedabiversion shouldBe "independent:abi7"
      }
    }

    "retain every supplied optional fact while rejecting malformed input" which {
      "C64V17 preserve empty relationships, every absence reason, labels and ordered foreign derivation sources" in {
        Given("all optional carriers vary through four explicit gaps or present values and derivation evidence has independent foreign sources")
        val reasons = Vector(NotDeclared, NotRepresented, CmlSemanticFoundation.Unsupported, NotApplicable)
        val documents = reasons.map { reason =>
          val gap = Absent(reason, "Explicit semantic gap.")
          val usage = _usage().copy(summary = gap, description = gap, demandReferences = gap, discoveryReferences = gap,
            componentMappings = gap, dependencies = gap, grounding = gap, traceability = gap)
          val component = _definition().copy(summary = gap, description = gap, discoveryReferences = gap, grounding = gap, traceability = gap)
          _document(usages = Vector(usage), components = Vector(component), declarations = Vector(_declaration(realizations = gap).copy(traceability = gap)), compatibility = gap)
        }
        val origin = Derived("rule:literal", Vector(_source("foreign-r9", authority = "Foreign:scope", path = "foreign/input.cml", line = Some(7)), _source("own-r3")))
        val labels = Vector(None, Some("en-X-literal")).map(language => _ground(TermTarget(TermId("Unknown:BoK", "same label")), External).copy(
          relation = Absent(NotRepresented, "No relation represented."), profile = Absent(CmlSemanticFoundation.Unsupported, "No profile represented."),
          context = Absent(NotApplicable, "No context applies."), preferredLabel = Present(LocalizedLabel("注文|α", language)), origin = origin))
        val present = _document(usages = Vector(_usage()), components = Vector(_definition().copy(grounding = Present(labels))),
          declarations = Vector(_declaration(realizations = Present(Vector.empty))))
        When("all gap variants and the present-empty model with independent grounding are validated")
        val results = (documents :+ present).map(document => _validate(Vector(document)))
        Then("every supplied Presence branch, label language and evidence vector survives whole-document equality")
        results.zip(documents :+ present).foreach { case (result, document) => _admitted(result).documents shouldBe Vector(document) }
        _admitted(results.last).bindings shouldBe Vector.empty
        _items(_admitted(results.last).documents.head.model.componentCapabilities.head.grounding).map(_.origin) shouldBe Vector(origin, origin)
      }

      "C64V18 reject malformed mandatory literals, source paths, lines and empty derivation evidence" in {
        Given("mandatory identity, version, source and nested Presence values are null, empty, padded or contain controls")
        val badtexts = Vector(null, "", " padded", "padded\u00a0", "control\ntext")
        val usages = badtexts.flatMap(text => Vector(_usage().copy(identity = ModelElementId(text, "id")),
          _usage().copy(identity = ModelElementId("Model:scope", text)), _usage().copy(contractVersion = text),
          _usage().copy(name = text), _usage().copy(summary = Present(text)),
          _usage().copy(description = Absent(NotDeclared, text)), _usage().copy(source = _source(text))))
        val paths = Vector("/absolute", "C:drive", "http:uri", "a\\b", "", "a//b", "./a", "a/../b", "a\nb")
        val malformed = usages ++ paths.map(path => _usage().copy(source = _source("revision", path = path))) ++
          Vector(_usage().copy(identity = null), _usage().copy(source = null), _usage().copy(source = _source("rev", line = Some(0))),
            _usage().copy(source = _source("rev", line = Some(-2))), _usage().copy(source = _source("rev").copy(line = null)),
            _usage().copy(discoveryReferences = Present(Vector(_link(Goal, "unknown", External).copy(origin = Derived("rule", Vector.empty))))))
        When("each malformed typed input is validated without fallback or repair")
        val results = malformed.map(usage => _validate(Vector(_document(usages = Vector(usage)))))
        Then("every input yields structured Malformed diagnostics with exact supplied source context or explicit absence")
        results.foreach(result => _issues(result).exists(_.kind == Malformed) shouldBe true)
        _issues(results(usages.size + paths.size + 1)).head.source shouldBe None
        _issues(results.last).exists(_.fieldpath.endsWith("origin.inputs")) shouldBe true
      }

      "C64V19 enforce direct document and qualified identity source authority without coupling revisions" in {
        Given("definition, declaration and edge sources independently disagree with owning authority or path while Derived evidence can be foreign")
        val link = _link(Goal, "goal", External)
        val base = _document(usages = Vector(_usage().copy(discoveryReferences = Present(Vector(link)))),
          components = Vector(_definition()), declarations = Vector(_declaration()))
        val foreign = _source("foreign-rev", authority = "Foreign:scope", path = "foreign/input.cml")
        val cases = Vector(_document(usages = Vector(_usage().copy(identity = ModelElementId("Foreign:scope", "usage")))),
          _document(usages = Vector(_usage().copy(source = foreign))),
          _document(declarations = Vector(_declaration().copy(source = foreign)), components = Vector(_definition())),
          _document(usages = Vector(_usage().copy(discoveryReferences = Present(Vector(link.copy(source = foreign)))))),
          _document(usages = Vector(_usage().copy(source = _source("revision", path = "other/input.cml")))))
        val validusage = _usage().copy(discoveryReferences = Present(Vector(
          link.copy(origin = Derived("rule", Vector(foreign, _source("own-independent")))))))
        val valid = base.copy(model = base.model.copy(componentUsageCapabilities = Vector(validusage)))
        When("direct ownership violations and the independently sourced derivation are validated")
        val failures = cases.map(document => _validate(Vector(document)))
        val accepted = _validate(Vector(valid))
        Then("direct violations fail while foreign ordered evidence and independently revised direct sources remain unchanged")
        failures.foreach(result => _issues(result).exists(_.kind == Malformed) shouldBe true)
        _admitted(accepted).documents shouldBe Vector(valid)
      }

      "C64V20 reject malformed scope facts and equal duplicate candidates in every domain" in {
        Given("explicit model, Term, Relation, Profile and target facts include invalid literal identities or sources and exact duplicate candidates")
        val model = ModelFact(_id("goal"), ModelKind.Goal, _source("model-fact"))
        val term = TermFact(TermId("BoK", "term"), _source("term-fact", authority = "BoK"))
        val relation = RelationFact(RelationId("BoK", "relation"), _source("relation-fact", authority = "BoK"))
        val profile = ProfileFact(ProfileId("BoK", "profile"), _source("profile-fact", authority = "BoK"))
        val target = _target()
        val duplicate = Vector(_scope.copy(modelfacts = Vector(model, model)), _scope.copy(termfacts = Vector(term, term)),
          _scope.copy(relationfacts = Vector(relation, relation)), _scope.copy(profilefacts = Vector(profile, profile)), _scope.copy(targetcontracts = Vector(target, target)))
        val malformed = Vector(_scope.copy(modelfacts = Vector(model.copy(identity = ModelElementId("", "goal")))),
          _scope.copy(termfacts = Vector(term.copy(identity = TermId("BoK", " padded")))),
          _scope.copy(relationfacts = Vector(relation.copy(source = null))), _scope.copy(profilefacts = Vector(profile.copy(source = _source("")))),
          _scope.copy(targetcontracts = Vector(target.copy(source = _source("target", authority = "Wrong:scope")))),
          _scope.copy(usagecontracts = Vector(ComponentUsageContract(_id("app"), "", _source("catalog")))),
          _scope.copy(componentcontracts = Vector(ComponentContract(_id("component"), "v1", null))))
        When("scope facts are checked before any document can consume them")
        val duplicates = duplicate.map(scope => _validate(Vector(_document()), scope))
        val invalid = malformed.map(scope => _validate(Vector(_document()), scope))
        Then("equal candidates remain ambiguous and malformed facts retain their own source with no document context")
        duplicates.foreach { result =>
          val issue = _issues(result).head
          issue.kind shouldBe Ambiguous
          issue.scope shouldBe None
          issue.relatedsources.nonEmpty shouldBe true
        }
        invalid.foreach(result => _issues(result).exists(x => x.kind == Malformed && x.scope.isEmpty) shouldBe true)
      }

      "C64V21 allow distinct historical versions but reject exact catalog or current duplicates and family reuse" in {
        Given("a current Component version and separately admitted historical catalog versions")
        val document = _document(components = Vector(_definition()))
        val historical = ComponentContract(_id("component"), "v0", _source("historical-r1"))
        val scope = _scope.copy(componentcontracts = Vector(historical, historical.copy(contractversion = "v2", source = _source("historical-r2"))))
        val cases = Vector(scope.copy(componentcontracts = Vector(historical, historical)),
          scope.copy(componentcontracts = Vector(historical.copy(contractversion = "v1"))),
          scope.copy(usagecontracts = Vector(ComponentUsageContract(historical.identity, "other-version", historical.source))),
          _scope.copy(usagecontracts = Vector(ComponentUsageContract(_id("component"), "v0", historical.source))))
        When("current/catalog exact collisions and opposite families are validated independently of history")
        val valid = _validate(Vector(document), scope)
        val failures = cases.map(_validate(Vector(document), _))
        Then("different historical versions remain facts while exact collisions are Ambiguous and family reuse is WrongKind")
        _admitted(valid).scope shouldBe scope
        failures.take(2).foreach(result => _issues(result).exists(_.kind == Ambiguous) shouldBe true)
        failures.drop(2).foreach(result => _issues(result).exists(_.kind == WrongKind) shouldBe true)
      }

      "C64V22 collapse historical family identity candidates but preserve independent ModelFact ambiguity" in {
        Given("Local model grounding references a Capability with several historical versions and an independently supplied ModelFact at the same identity")
        val document = _document(components = Vector(_definition().copy(grounding = Present(Vector(_ground(ModelTarget(_id("component"))))))))
        val scope = _scope.copy(componentcontracts = Vector("v0", "v2", "v3").map(version => ComponentContract(_id("component"), version, _source("history:" + version))))
        val duplicate = scope.copy(modelfacts = Vector(ModelFact(_id("component"), ModelKind.ComponentCapability, _source("independent-model-fact"))))
        When("family-only and independently duplicated model identities are resolved")
        val valid = _validate(Vector(document), scope)
        val failure = _validate(Vector(document), duplicate)
        Then("history alone creates one model identity candidate and the independent model fact creates edge-correlated ambiguity")
        _admitted(valid).documents shouldBe Vector(document)
        val issue = _issues(failure).find(_.kind == Ambiguous).get
        issue.source shouldBe Some(_source("grounding"))
        issue.relatedsources should contain (_source("independent-model-fact"))
      }

      "C64V23 retain deterministic diagnostics and previous sources without successful sibling salvage" in {
        Given("an equal duplicate definition and two dangling Local edges have independent revisions and lines alongside a valid sibling")
        val first = _definition().copy(source = _source("definition-first", line = Some(13)))
        val repeat = first.copy(source = _source("definition-second", line = None))
        val edges = Vector(_link(Goal, "missing-goal").copy(source = _source("edge-one", line = Some(41))),
          _link(UseCase, "missing-usecase").copy(source = _source("edge-two", line = None)))
        val document = _document(usages = Vector(_usage().copy(discoveryReferences = Present(edges)), _usage("valid-sibling")),
          components = Vector(first, repeat))
        When("the same documents and scope are validated twice")
        val result = _validate(Vector(document))
        val again = _validate(Vector(document))
        Then("the ordered issue vector is identical and contains the original duplicate and offending edge sources")
        again shouldBe result
        val issues = _issues(result)
        issues.map(_.kind) shouldBe Vector(DuplicateDefinition, DanglingReference, DanglingReference)
        issues.map(_.source) shouldBe Vector(Some(repeat.source), Some(edges.head.source), Some(edges.last.source))
        issues.head.relatedsources shouldBe Vector(first.source)
        issues.foreach(_.scope shouldBe Some(document.scope))
        result.isLeft shouldBe true
      }

      "C64V24 distinguish an explicit Ready empty scope and parsed empty root from rejection or repeated paths" in {
        Given("a successfully parsed explicit empty Capability root and a deliberately supplied empty catalog")
        val source = "# CAPABILITY-MODEL\ncompatibility = {\"state\":\"PRESENT\",\"value\":[]}\n"
        val input = SourceInput(SourceScope("Model:scope", "model/input.cml"), KaleidoxModel.parse(KaleidoxConfig.default, source))
        val rejected = Diagnostic(Unavailable, "scope.acquire", None, None, Vector.empty, "Catalog unavailable.")
        When("the empty root is normalized and validated with Ready, invalid, Rejected or repeated original paths")
        val normalized = CmlCapabilityCml.normalize(Vector(input))
        val documents = normalized.fold(issues => fail(issues.mkString("; ")), identity)
        val valid = _validate(documents)
        val invalid = _validate(documents, _scope.copy(scopeid = ""))
        val unavailable = CmlCapabilityValidation.validate(documents, ScopeInput.Rejected(Vector(rejected)))
        val repeated = _validate(documents ++ documents)
        Then("only deliberate acquired emptiness succeeds and every failed alternative remains an observable typed failure")
        documents.size shouldBe 1
        _admitted(valid).documents shouldBe documents
        _admitted(valid).scope shouldBe _scope
        _issues(invalid).exists(_.kind == Malformed) shouldBe true
        unavailable shouldBe Left(Vector(rejected))
        _issues(repeated).exists(_.fieldpath.endsWith("scope.path")) shouldBe true
      }
    }
  }
}

// Shared authored semantic fixtures; no acquisition or target extraction is performed.
private[modeler] object CmlCapabilityValidationSpec {
  private[modeler] def _id(element: String): ModelElementId = ModelElementId("Model:scope", element)
  private[modeler] def _source(revision: String, authority: String = "Model:scope", path: String = "model/input.cml", line: Option[Int] = None): CapabilitySource =
    CapabilitySource(authority, path, revision, line)
  private[modeler] def _component(localid: String): ComponentId = ComponentId.of(
    ComponentNamespace.parse("org.simplemodeling.textus").value().get(), ComponentLocalId.parse(localid).value().get())
  private[modeler] def _scope: Scope = Scope("explicit-scope", _source("scope-revision"), Vector.empty, Vector.empty,
    Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)
  private[modeler] def _usage(element: String = "usage", version: String = "v1", path: String = "model/input.cml"): ComponentUsageCapability =
    ComponentUsageCapability(_component("Store"), _id(element), version, "Same presentation", Present("Summary"), Absent(NotRepresented, "No description represented."),
      _source("definition", path = path), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty))
  private[modeler] def _definition(element: String = "component", version: String = "v1"): ComponentCapability =
    ComponentCapability(_id(element), version, "Same presentation", Present("Summary"), Absent(NotDeclared, "No description declared."),
      _source("definition"), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty))
  private[modeler] def _declaration(component: ComponentId = _component("Store"), direction: Direction = Provided,
    version: String = "v1", realizations: Presence[Vector[RealizationEdge]] = Present(Vector.empty)): ComponentDeclaration =
    ComponentDeclaration(component, direction, VersionedComponentCapabilityReference(_id("component"), version),
      _source("declaration"), realizations, Present(Vector.empty))
  private[modeler] def _realization(kind: TargetKind = Operation, boundary: ReferenceBoundary = Local): RealizationEdge =
    RealizationEdge(DeclarationKey(_component("Store"), Provided, VersionedComponentCapabilityReference(_id("component"), "v1")), kind,
      ModelReference(_id("target"), boundary), _component("Store"), "target:v3", "independent:abi7", _source("realization", line = Some(53)), Declared)
  private[modeler] def _target(kind: TargetKind = Operation): TargetContract =
    TargetContract(kind, _id("target"), _component("Store"), "target:v3", "independent:abi7", _source("target-fact"))
  private[modeler] def _link(role: ModelRole, element: String, boundary: ReferenceBoundary = Local): ModelLink =
    ModelLink(role, ModelReference(_id(element), boundary), _source("edge:" + element), Declared)
  private[modeler] def _trace(role: TraceabilityRole, element: String, boundary: ReferenceBoundary = Local): TraceabilityReference =
    TraceabilityReference(role, ModelReference(_id(element), boundary), _source("trace:" + element), Declared)
  private[modeler] def _ground(target: CmlSemanticFoundation.SemanticTarget, boundary: ReferenceBoundary = Local): GroundingReference =
    GroundingReference(target, boundary, Absent(NotDeclared, "No relation declared."), Absent(NotApplicable, "No profile applies."),
      Present("Literal context"), Present(LocalizedLabel("Same presentation", None)), _source("grounding"), Declared)
  private[modeler] def _document(usages: Vector[ComponentUsageCapability] = Vector.empty,
    components: Vector[ComponentCapability] = Vector.empty, declarations: Vector[ComponentDeclaration] = Vector.empty,
    compatibility: Presence[Vector[CompatibilityEdge]] = Present(Vector.empty), path: String = "model/input.cml"): CapabilityDocument =
    CapabilityDocument(SourceScope("Model:scope", path), CapabilityModel(usages, components, declarations, compatibility))
  private[modeler] def _items[A](presence: Presence[Vector[A]]): Vector[A] = presence match {
    case Present(values) => values
    case _: Absent => Vector.empty
  }
  private[modeler] def _validate(documents: Vector[CapabilityDocument], scope: Scope = _scope): Either[Vector[Diagnostic], CmlCapabilityValidation.Validated] =
    CmlCapabilityValidation.validate(documents, ScopeInput.Ready(scope))
  private[modeler] def _admitted(result: Either[Vector[Diagnostic], CmlCapabilityValidation.Validated]): CmlCapabilityValidation.Validated = result match {
    case Right(value) => value
    case Left(issues) => org.scalatest.Assertions.fail(issues.mkString("; "))
  }
  private[modeler] def _issues(result: Either[Vector[Diagnostic], CmlCapabilityValidation.Validated]): Vector[Diagnostic] = result match {
    case Left(issues) => issues
    case Right(_) => org.scalatest.Assertions.fail("Expected semantic rejection, received Validated.")
  }
}
