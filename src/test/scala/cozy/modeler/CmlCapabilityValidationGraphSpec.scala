package cozy.modeler

import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import CmlSemanticFoundation.{Presence, Present, Absent, NotDeclared, NotRepresented, NotApplicable, Local, External, ModelElementId, ModelTarget}
import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope._
import CmlCapabilityValidationScope.DiagnosticKind._
import CmlCapabilityValidationSpec._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityValidationGraphSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Finite Capability dependency and compatibility semantics" should {
    "check only the authored Local Application dependency graph" which {
      "C64V25 reject every edge of self, two-node and longer cross-file cycles while admitting a chain" in {
        Given("explicit Application graphs distribute one source-bearing Local dependency per file")
        val self = _graph(1, cyclic = true)
        val pair = _graph(2, cyclic = true)
        val longer = _graph(5, cyclic = true)
        val chain = _graph(5, cyclic = false)
        When("the self-loop, directed cycles and acyclic chain are validated")
        val failures = Vector(self, pair, longer).map(_validate(_))
        val valid = _validate(chain)
        Then("each cycle edge retains its own original source and the supplied finite chain succeeds")
        failures.zip(Vector(self, pair, longer)).foreach { case (result, documents) =>
          val issues = _issues(result)
          issues.map(_.kind) shouldBe Vector.fill(documents.size)(CyclicDependency)
          issues.map(_.source) shouldBe documents.map(document => Some(_items(document.model.applicationCapabilities.head.dependencies).head.source))
          issues.map(_.scope) shouldBe documents.map(document => Some(document.scope))
          issues.map(_.fieldpath) shouldBe documents.indices.map(index =>
            s"documents[$index].model.applicationCapabilities[0].dependencies.value[0]").toVector
        }
        _admitted(valid).documents shouldBe chain
      }

      "C64V26 retain External dependencies and ordinary demand, grounding and traceability relationships" in {
        Given("a two-file dependency cycle has one explicitly External edge and independent model relationships point in both directions")
        val graph = _graph(2, cyclic = true)
        val first = graph.head.model.applicationCapabilities.head
        val second = graph.last.model.applicationCapabilities.head
        val external = _items(first.dependencies).head.copy(boundary = External)
        val firstground = _ground(ModelTarget(second.identity)).copy(source = _source("ground-first", path = graph.head.scope.path))
        val secondground = _ground(ModelTarget(first.identity)).copy(source = _source("ground-second", path = graph.last.scope.path))
        val demand = _link(UseCase, "usecase", Local).copy(source = _source("demand", path = graph.head.scope.path))
        val trace = _trace(EvidenceTrace, "evidence", Local).copy(source = _source("trace", path = graph.last.scope.path))
        val documents = Vector(
          graph.head.copy(model = graph.head.model.copy(applicationCapabilities = Vector(first.copy(dependencies = Present(Vector(external)), grounding = Present(Vector(firstground)), demandReferences = Present(Vector(demand)))))),
          graph.last.copy(model = graph.last.model.copy(applicationCapabilities = Vector(second.copy(grounding = Present(Vector(secondground)), traceability = Present(Vector(trace)))))))
        val scope = _scope.copy(modelfacts = Vector(ModelFact(_id("usecase"), ModelKind.UseCase, _source("usecase-fact")),
          ModelFact(_id("evidence"), ModelKind.Evidence, _source("evidence-fact"))))
        When("the supplied graph and all independent model relationships are validated")
        val result = _validate(documents, scope)
        Then("External remains External despite a current target, and ordinary relationships do not create dependency cycles")
        _admitted(result).documents shouldBe documents
        _items(_admitted(result).documents.head.model.applicationCapabilities.head.dependencies).head.boundary shouldBe External
        _admitted(result).documents.flatMap(_.model.applicationCapabilities).flatMap(x => _items(x.grounding)).size shouldBe 2
      }

      "C64V27 retain typed dependency resolution failures instead of certifying an empty graph" in {
        Given("one Local dependency requests a missing, opposite-family, version-mismatched or ambiguous target")
        val dependency = ApplicationDependency(VersionedApplicationCapabilityReference(_id("target-app"), "v1"), Local, _source("dependency-edge"), Declared)
        val application = _application().copy(dependencies = Present(Vector(dependency)))
        val missing = _document(applications = Vector(application))
        val wrong = _document(applications = Vector(application), components = Vector(_definition("target-app")))
        val version = _document(applications = Vector(application, _application("target-app", "v2")))
        val fact = ApplicationContract(_id("target-app"), "v1", _source("catalog-target"))
        val scope = _scope.copy(applicationcontracts = Vector(fact, fact))
        When("each dependency target alternative is validated")
        val results = Vector(_validate(Vector(missing)), _validate(Vector(wrong)), _validate(Vector(version)), _validate(Vector(missing), scope))
        Then("every failed dependency remains an observable typed rejection and cannot be an admitted empty dependency graph")
        results.zip(Vector(DanglingReference, WrongKind, Incompatible, Ambiguous)).foreach { case (result, kind) =>
          _issues(result).exists(_.kind == kind) shouldBe true
          result.isLeft shouldBe true
        }
        results.take(3).foreach(result => _issues(result).exists(_.source.contains(dependency.source)) shouldBe true)
      }
    }

    "match exact admitted versions or explicitly directed compatibility" which {
      "C64V28 admit equality and direct same-identity edges without waiving reference or realization evidence" in {
        Given("three exact Component contract versions, a direct v0-to-v1 edge and an independently bound executable realization")
        val edge = CompatibilityEdge(_id("component"), "v0", "v1", _source("compatibility-edge"), Declared)
        val realization = _realization()
        val document = _document(components = Vector(_definition()), compatibility = Present(Vector(edge)),
          declarations = Vector(_declaration(realizations = Present(Vector(realization)))))
        val scope = _scope.copy(componentcontracts = Vector(ComponentContract(_id("component"), "v0", _source("history-v0")),
          ComponentContract(_id("component"), "v2", _source("history-v2"))), targetcontracts = Vector(_target()))
        val bad = document.copy(model = document.model.copy(componentDeclarations = Vector(
          _declaration(realizations = Present(Vector(realization.copy(generatedAbiVersion = "different-abi")))))))
        When("the exact model is admitted and compatibility is queried separately from a failed ABI binding")
        val validated = _admitted(_validate(Vector(document), scope))
        val equal = validated.isCompatible(_version("v1"), _version("v1"))
        val direct = validated.isCompatible(_version("v0"), _version("v1"))
        val unavailable = validated.isCompatible(_version("missing"), _version("missing"))
        val abifailure = _validate(Vector(bad), scope)
        Then("admitted equality or a direct edge matches, absent exact candidates fail and compatibility cannot waive ABI checks")
        equal shouldBe true
        direct shouldBe true
        unavailable shouldBe false
        validated.documents shouldBe Vector(document)
        validated.bindings shouldBe Vector(realization -> _target())
        _issues(abifailure).exists(x => x.kind == Incompatible && x.fieldpath.endsWith("generatedAbiVersion")) shouldBe true
      }

      "C64V29 reject inverse, transitive, SemVer, same-name and cross-identity compatibility" in {
        Given("direct a-to-b and b-to-c edges, opaque SemVer-looking versions, same labels and several provider directions")
        val edges = Vector(_compatibility("a", "b"), _compatibility("b", "c"))
        val document = _document(components = Vector(_definition(version = "b"), _definition("other", "b")),
          declarations = Vector(_declaration(version = "b"), _declaration(component = _component("Archive"), version = "b"), _declaration(direction = Required, version = "b")),
          compatibility = Present(edges))
        val scope = _scope.copy(componentcontracts = Vector("a", "c", "1.0.0", "1.0.1").map(version => ComponentContract(_id("component"), version, _source("history:" + version))))
        When("the model is admitted and each unsupported compatibility direction or equivalence is queried")
        val validated = _admitted(_validate(Vector(document), scope))
        val reordered = _admitted(_validate(Vector(document.copy(model = document.model.copy(componentDeclarations = document.model.componentDeclarations.reverse))), scope))
        val positive = Vector(validated.isCompatible(_version("a"), _version("b")), validated.isCompatible(_version("b"), _version("c")))
        val negative = Vector(validated.isCompatible(_version("b"), _version("a")), validated.isCompatible(_version("a"), _version("c")),
          validated.isCompatible(_version("1.0.0"), _version("1.0.1")), validated.isCompatible(_version("b"), VersionedComponentCapabilityReference(_id("other"), "b")))
        Then("only supplied directions match and provider order, similar versions or names add no compatibility")
        positive shouldBe Vector(true, true)
        negative shouldBe Vector.fill(4)(false)
        reordered.isCompatible(_version("a"), _version("c")) shouldBe false
        reordered.isCompatible(_version("a"), _version("b")) shouldBe true
        validated.documents.head.model.compatibility shouldBe Present(edges)
        edges.map(_.source.sourceRevision) shouldBe Vector("compatibility:a:b", "compatibility:b:c")
      }

      "C64V30 reject every unavailable compatibility identity, family, version or candidate set" in {
        Given("independently sourced compatibility edges require absent identity, opposite family, absent required or provided version, or duplicated exact catalog candidates")
        val missing = _document(compatibility = Present(Vector(_compatibility("a", "b"))))
        val wrong = _document(applications = Vector(_application("component", "b")), compatibility = Present(Vector(_compatibility("a", "b"))))
        val required = _document(components = Vector(_definition(version = "b")), compatibility = Present(Vector(_compatibility("a", "b"))))
        val provided = _document(components = Vector(_definition(version = "a")), compatibility = Present(Vector(_compatibility("a", "b"))))
        val fact = ComponentContract(_id("component"), "a", _source("history-a"))
        val scope = _scope.copy(componentcontracts = Vector(fact, fact))
        When("each supplied directed edge is semantically validated")
        val results = Vector(_validate(Vector(missing)), _validate(Vector(wrong)), _validate(Vector(required)),
          _validate(Vector(provided)), _validate(Vector(required), scope))
        Then("typed failures preserve edge sources for document resolution and original fact sources for scope ambiguity")
        results.zip(Vector(DanglingReference, WrongKind, Incompatible, Incompatible, Ambiguous)).foreach { case (result, kind) =>
          _issues(result).exists(_.kind == kind) shouldBe true
        }
        results.take(4).foreach(result => _issues(result).forall(_.source.contains(_source("compatibility:a:b"))) shouldBe true)
        _issues(results(2)).exists(_.fieldpath.contains(".required.")) shouldBe true
        _issues(results(3)).exists(_.fieldpath.contains(".provided.")) shouldBe true
        _issues(results.last).head.source shouldBe Some(fact.source)
      }
    }

    "preserve literal domains and graph policy over active generated samples" which {
      "C64V31 retain literal IDs, versions, revisions, Presence, origin and document order over 100 undiscarded samples" in {
        Given("valid literal case, Unicode composition, delimiters, quote and backslash IDs, independent version/revision values and all Presence/origin branches")
        val literals = Gen.oneOf("Case:ID", "case:id", "能力:注文|α", "e\u0301/é", "id=literal:|/", "quoted\"id\\literal")
        val samples = for {
          literal <- literals
          revision <- Gen.choose(1, 10000)
          version <- Gen.choose(1, 10000)
          branch <- Gen.choose(0, 4)
          derived <- Gen.oneOf(true, false)
          reverse <- Gen.oneOf(true, false)
        } yield (literal, revision, version, branch, derived, reverse)
        val property = Prop.forAll(samples) { case (literal, revision, version, branch, derived, reverse) =>
          val scope = SourceScope("Model:literal/能力", "model/literal.cml")
          val source = _literal_cml(scope, literal, s"revision:$revision", s"version:$version", branch, derived)
          val normalized = CmlCapabilityCml.normalize(Vector(SourceInput(scope, KaleidoxModel.parse(KaleidoxConfig.default, source))))
          normalized match {
            case Left(_) => false
            case Right(parsed) =>
              val identity = ModelElementId(scope.modelAuthority, literal)
              val target = ModelElementId("Target:literal", literal + ":target")
              val factsource = CapabilitySource("Target:literal", "catalog/target.cml", s"fact:$version", Some(3))
              val supplied = _scope.copy(modelfacts = Vector(ModelFact(target, ModelKind.UseCase, factsource)))
              val other = _document(applications = Vector(_application("other", s"v:$version", "model/other.cml")), path = "model/other.cml")
              val documents = if (reverse) Vector(other) ++ parsed else parsed :+ other
              val result = _validate(documents, supplied)
              val broken = parsed.head.model.applicationCapabilities.head.copy(demandReferences = Present(Vector(
                ModelLink(UseCase, ModelReference(target.copy(elementId = literal + ":missing"), Local),
                  CapabilitySource(scope.modelAuthority, scope.path, s"failed:$revision", None), Declared))))
              val invaliddocuments = Vector(parsed.head.copy(model = parsed.head.model.copy(applicationCapabilities = Vector(broken))))
              val invalid = _validate(invaliddocuments, supplied)
              result match {
                case Left(_) => false
                case Right(validated) =>
                  val application = parsed.head.model.applicationCapabilities.head
                  val expectedsummary = _summary(branch)
                  val expectedorigin = _literal_origin(scope, derived)
                  val links = _items(application.demandReferences)
                  validated.documents == documents && validated.scope == supplied && application.identity == identity &&
                    application.contractVersion == s"version:$version" && application.source.sourceRevision == s"revision:$revision" &&
                    application.summary == expectedsummary && links.map(_.origin) == Vector(expectedorigin) &&
                    links.map(_.target) == Vector(ModelReference(target, Local)) && links.head.source.sourceRevision == "edge:independent" &&
                    invalid.isLeft && invalid == _validate(invaliddocuments, supplied) && invalid.left.get.exists(issue =>
                      issue.kind == DanglingReference && issue.source.contains(CapabilitySource(scope.modelAuthority, scope.path, s"failed:$revision", None)))
              }
          }
        }
        When("ScalaCheck actively evaluates the real-parser and semantic equality property with 100 required successes")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("the original semantic predicates pass at least 100 times without filtering or discarded samples")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }

      "C64V32 enforce Local cycles and directed nontransitive compatibility over 100 undiscarded samples" in {
        Given("explicit acyclic or cyclic finite graphs and opaque version triples with direct compatible and incompatible pairs")
        val samples = for {
          count <- Gen.choose(1, 8)
          cyclic <- Gen.oneOf(true, false)
          external <- Gen.oneOf(true, false)
          revision <- Gen.choose(1, 10000)
        } yield (count, cyclic, external, revision)
        val property = Prop.forAll(samples) { case (count, cyclic, external, revision) =>
          val graph = _graph(count, cyclic)
          val documents = if (!external || !cyclic) graph else {
            val last = graph.last
            val application = last.model.applicationCapabilities.head
            val changed = application.copy(dependencies = Present(_items(application.dependencies).map(_.copy(boundary = External))))
            graph.init :+ last.copy(model = last.model.copy(applicationCapabilities = Vector(changed)))
          }
          val graphresult = _validate(documents)
          val graphright = if (cyclic && !external) graphresult.isLeft && graphresult.left.get.count(_.kind == CyclicDependency) == count
            else graphresult.isRight && graphresult.right.get.documents == documents
          val a = s"a:$revision"
          val b = s"b:$revision"
          val c = s"c:$revision"
          val scope = _scope.copy(componentcontracts = Vector(a, c).map(version => ComponentContract(_id("component"), version, _source("history:" + version))))
          val document = _document(components = Vector(_definition(version = b)), compatibility = Present(Vector(_compatibility(a, b), _compatibility(b, c))))
          _validate(Vector(document), scope) match {
            case Left(_) => false
            case Right(validated) => graphright && validated.isCompatible(_version(a), _version(b)) &&
              validated.isCompatible(_version(b), _version(c)) && validated.isCompatible(_version(b), _version(b)) &&
              !validated.isCompatible(_version(a), _version(c)) && !validated.isCompatible(_version(b), _version(a)) &&
              !validated.isCompatible(_version("missing:" + revision), _version("missing:" + revision)) &&
              graphresult.left.toOption == _validate(documents).left.toOption
          }
        }
        When("ScalaCheck actively evaluates graph and compatibility semantics with 100 required successes")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("every supplied graph and direction obeys the finite Local policy with no discarded samples")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }

  private def _version(version: String): VersionedComponentCapabilityReference =
    VersionedComponentCapabilityReference(_id("component"), version)
  private def _compatibility(required: String, provided: String): CompatibilityEdge =
    CompatibilityEdge(_id("component"), required, provided, _source(s"compatibility:$required:$provided"), Declared)
  private def _graph(count: Int, cyclic: Boolean): Vector[CapabilityDocument] =
    (0 until count).map { index =>
      val path = s"model/graph-$index.cml"
      val edges = if (index + 1 < count || cyclic) Vector(ApplicationDependency(
        VersionedApplicationCapabilityReference(_id(s"node:${(index + 1) % count}"), "v1"), Local,
        _source(s"dependency:$index", path = path, line = Some(index + 11)), Declared)) else Vector.empty
      _document(applications = Vector(_application(s"node:$index", path = path).copy(dependencies = Present(edges))), path = path)
    }.toVector
  private def _summary(branch: Int): Presence[String] = branch match {
    case 0 => Present("Literal summary")
    case 1 => Absent(NotDeclared, "Literal gap")
    case 2 => Absent(NotRepresented, "Literal gap")
    case 3 => Absent(CmlSemanticFoundation.Unsupported, "Literal gap")
    case 4 => Absent(NotApplicable, "Literal gap")
  }
  private def _literal_origin(scope: SourceScope, derived: Boolean): CapabilityOrigin =
    if (!derived) Declared else Derived("rule:literal", Vector(
      CapabilitySource("Foreign:literal", "foreign/evidence.cml", "foreign:r7", Some(8)),
      CapabilitySource(scope.modelAuthority, scope.path, "own:evidence-r3", None)))
  private def _literal_cml(scope: SourceScope, literal: String, revision: String, version: String,
    branch: Int, derived: Boolean): String = {
    def _json_(value: JsValue): String =
      """\\(["\\])""".r.replaceAllIn(Json.stringify(value), matched =>
        java.util.regex.Matcher.quoteReplacement(if (matched.group(1) == "\"") "\\u0022" else "\\u005c"))
    def _source_(authority: String, path: String, rev: String, line: JsValue): JsObject =
      Json.obj("modelAuthority" -> authority, "path" -> path, "sourceRevision" -> rev, "line" -> line)
    val source = _source_(scope.modelAuthority, scope.path, revision, JsNull)
    val origin = if (!derived) Json.obj("kind" -> "DECLARED") else Json.obj("kind" -> "DERIVED", "ruleId" -> "rule:literal", "inputs" -> Json.arr(
      _source_("Foreign:literal", "foreign/evidence.cml", "foreign:r7", JsNumber(8)), _source_(scope.modelAuthority, scope.path, "own:evidence-r3", JsNull)))
    val summary = _summary(branch) match {
      case Present(value) => Json.obj("state" -> "PRESENT", "value" -> value)
      case Absent(reason, detail) => Json.obj("state" -> "ABSENT", "reason" -> (reason match {
        case NotDeclared => "NotDeclared"
        case NotRepresented => "NotRepresented"
        case CmlSemanticFoundation.Unsupported => "Unsupported"
        case NotApplicable => "NotApplicable"
      }), "detail" -> detail)
    }
    val empty = Json.obj("state" -> "PRESENT", "value" -> Json.arr())
    val demand = Json.obj("state" -> "PRESENT", "value" -> Json.arr(Json.obj("role" -> "USE_CASE", "target" -> Json.obj(
      "identity" -> Json.obj("modelId" -> "Target:literal", "elementId" -> (literal + ":target")), "boundary" -> "LOCAL"),
      "source" -> _source_(scope.modelAuthority, scope.path, "edge:independent", JsNull), "origin" -> origin)))
    val fields = Vector("identity" -> Json.obj("modelId" -> scope.modelAuthority, "elementId" -> literal), "contractVersion" -> JsString(version),
      "name" -> JsString("Same presentation"), "summary" -> summary, "description" -> summary, "source" -> source,
      "demandReferences" -> demand, "discoveryReferences" -> empty, "componentMappings" -> empty, "dependencies" -> empty,
      "grounding" -> empty, "traceability" -> empty)
    "# CAPABILITY-MODEL\ncompatibility = {\"state\":\"PRESENT\",\"value\":[]}\n\n## APPLICATION-CAPABILITY\n### Literal\n" +
      fields.map { case (key, value) => s"$key = ${_json_(value)}" }.mkString("\n") + "\n"
  }
}
