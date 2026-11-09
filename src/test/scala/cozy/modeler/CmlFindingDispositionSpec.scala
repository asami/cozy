package cozy.modeler

import org.scalatest.{Failed, GivenWhenThen, Outcome}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Kaleidox, Model => KaleidoxModel}
import org.goldenport.realm.Realm
import org.goldenport.sexpr.{SAtom, SError, SExpr, SList, SModel, STree}
import org.goldenport.tree.TreeNode
import play.api.libs.json._
import CmlFindingDisposition._

/** Executable CML-source, exact-binding and ordinary-generation contract.
 * @since   Nov.  5, 2026
 * @version Nov.  9, 2026
 */
class CmlFindingDispositionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  override protected def withFixture(test: NoArgTest): Outcome = {
    val outcome = super.withFixture(test)
    outcome match {
      case Failed(error) => Console.err.println(test.name + ": " + error)
      case _ => ()
    }
    outcome
  }

  private val _component = """# COMPONENT
    |
    |## Orders
    |
    |### PACKAGE
    |
    |org.example
    |
    |### SERVICE
    |
    |#### OrderService
    |
    |# VALUE
    |
    |## SubmitOrder
    |- input-kind :: COMMAND
    |
    |### ATTRIBUTE
    |
    || name | type | multiplicity |
    ||------+------|--------------|
    || requestId | string | 1 |
    |
    |## SubmitOrderResult
    |
    |### ATTRIBUTE
    |
    || name | type | multiplicity |
    ||------+------|--------------|
    || accepted | boolean | 1 |
    |
    |# SERVICE
    |
    |## OrderService
    |
    |### OPERATION
    |
    |#### submit
    |
    |##### TYPE
    |
    |COMMAND
    |
    |##### INPUT
    |###### TYPE
    |SubmitOrder
    |##### OUTPUT
    |###### TYPE
    |SubmitOrderResult
    |
    |""".stripMargin
  private val _header = "# FINDING-DISPOSITION\n\n## Orders\n\n"
  private val _generated_path = "target/scala-3.3.8/src_managed/main/scala/org/example/finding/OrdersFindingDispositions.scala"
  private val _target = "operation:OrderService.submit"
  private val _defaults: Vector[(String, JsValue)] = Vector(
    "abiVersion" -> JsNumber(1), "rule" -> JsString("car.required-operation"),
    "finding" -> JsString("missing-submit"), "target" -> JsString(_target),
    "kind" -> JsString("defer"), "reason" -> JsString("Implement submission next milestone"),
    "provenance" -> JsString("issue:orders:42"))

  private def _declaration(identity: String = "submit-followup", updates: Map[String, JsValue] = Map.empty,
      omitted: Set[String] = Set.empty): String = {
    val fields = _defaults.map { case (key, value) => key -> updates.getOrElse(key, value) } ++
      updates.toVector.filterNot(pair => _defaults.exists(_._1 == pair._1)).sortBy(_._1)
    "### " + identity + "\n\n" + fields.filterNot(pair => omitted.contains(pair._1))
      .map { case (key, value) => key + " = " + Json.stringify(value) }.mkString("\n") + "\n\n"
  }
  private def _source(declaration: String): String = _component + _header + declaration
  private def _model(source: String): KaleidoxModel = KaleidoxModel.parse(KaleidoxConfig.default.withoutLocation, source)
  private def _entries(node: TreeNode[Realm.Data]): Vector[(String, String)] =
    node.getContent.collect { case data: Realm.StringData => node.pathnameRelative -> data.string }.toVector ++
      node.children.toVector.flatMap(_entries)
  private def _files(result: Either[Vector[Diagnostic], Option[Realm]]): Map[String, String] = result match {
    case Right(Some(realm)) => _entries(realm.backendRoot).toMap
    case other => fail("expected admitted source Realm: " + other)
  }
  private def _route(source: String, operation: String, target: ModelGenerationTarget): SExpr = {
    val environment = cozy.Cozy.build(Array.empty[String]).environment
    val config = KaleidoxConfig.create(environment).setModeler(new Modeler(generationTarget = target))
    val model = _model(source)
    new Kaleidox(config, environment).createEngine().applyModelScript(model, SList(SAtom(operation), SModel(model)))
  }
  private def _route_files(source: String, operation: String): Map[String, String] =
    _route(source, operation, ModelGenerationTarget.Cncf) match {
      case STree(realm) => _entries(realm.backendRoot).toMap
      case other => fail("expected ordinary generated STree: " + other)
    }

  "CML Finding Disposition properties" should {
    "retain Accept and Defer as exact portable CNCF values" in {
      Given("two explicitly authored decisions for distinct stable occurrences on the actual operation")
      val source = _source(_declaration("accepted", Map("kind" -> JsString("accept"), "finding" -> JsString("accepted-occurrence"))) +
        _declaration("deferred", Map("finding" -> JsString("deferred-occurrence"))))
      When("the ordinary CML AST is inspected")
      val result = CmlFindingDisposition.inspectCml(source)
      Then("the exact rule, occurrence, owner, target, reason and provenance reach the portable representation")
      result.findings shouldBe empty
      result.declarations.map(_.properties.kind) shouldBe Vector("accept", "defer")
      result.declarations.map(_.properties.finding) shouldBe Vector("accepted-occurrence", "deferred-occurrence")
      val properties = result.declarations.head.properties.toJson
      properties shouldBe Json.obj("abiVersion" -> 1, "rule" -> "car.required-operation", "finding" -> "accepted-occurrence",
        "scope" -> "component:org.example.Orders", "target" -> _target, "kind" -> "accept",
        "reason" -> "Implement submission next milestone", "provenance" -> "issue:orders:42")
      result.coverage.map(_.status) shouldBe Vector(CoverageStatus.Completed, CoverageStatus.Completed)
    }

    "preserve extensible rules and escaped source values without semantic reinterpretation" in {
      Given("arbitrary nonblank rule/occurrence data and reasons containing quotes and backslashes")
      val words = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
      val property = Prop.forAll(words, words, words) { (rule, finding, text) =>
        val reason = "Decision " + text + """ with \ and "quotes"."""
        val source = _source(_declaration(updates = Map("rule" -> JsString("future." + rule),
          "finding" -> JsString(finding), "reason" -> JsString(reason))))
        val inspected = CmlFindingDisposition.inspectCml(source)
        inspected.isValid && inspected.declarations.head.properties.rule == "future." + rule &&
          inspected.declarations.head.properties.finding == finding && inspected.declarations.head.properties.reason == reason
      }
      When("one hundred ordinary CML property round trips are checked")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("the source values remain exact and no known-rule enumeration is imposed")
      result.passed shouldBe true
    }

    "accept explicit exact scope and the default V1 version" in {
      Given("an exact owner scope and an omitted ABI version")
      val source = _source(_declaration(updates = Map("scope" -> JsString("component:org.example.Orders")), omitted = Set("abiVersion")))
      When("the declaration is mapped")
      val result = CmlFindingDisposition.inspectCml(source)
      Then("the CNCF V1 default is explicit in the portable result")
      result.isValid shouldBe true
      result.declarations.head.properties.abiVersion shouldBe 1
    }

    "retain Fix and Configure as declarations without inventing correction evidence" in {
      Given("explicit fix and configure declarations with distinct finding occurrences")
      val source = _source(_declaration("fix", Map("kind" -> JsString("fix"), "finding" -> JsString("fix-me"))) +
        _declaration("configure", Map("kind" -> JsString("configure"), "finding" -> JsString("configure-me"))))
      When("only source syntax and binding are inspected")
      val result = CmlFindingDisposition.inspectCml(source)
      Then("the kinds remain portable data; no corrected/configured CAR result is manufactured")
      result.isValid shouldBe true
      result.declarations.map(_.properties.kind) shouldBe Vector("fix", "configure")
    }

    "diagnose invalid source values and exact target mismatches" which {
      val cases = Vector(
        ("missing reason", Map.empty[String, JsValue], Set("reason"), "FD77_REASON_REQUIRED"),
        ("blank reason", Map("reason" -> JsString(" ")), Set.empty[String], "FD77_REASON_REQUIRED"),
        ("unknown kind", Map("kind" -> JsString("ignore")), Set.empty[String], "FD77_KIND_INVALID"),
        ("near-case kind", Map("kind" -> JsString("Accept")), Set.empty[String], "FD77_KIND_INVALID"),
        ("new ABI", Map("abiVersion" -> JsNumber(2)), Set.empty[String], "FD77_ABI_UNSUPPORTED"),
        ("fractional ABI", Map("abiVersion" -> JsNumber(BigDecimal("1.1"))), Set.empty[String], "FD77_ABI_UNSUPPORTED"),
        ("other target", Map("target" -> JsString("operation:OrderService.cancel")), Set.empty[String], "FD77_TARGET_INCOMPATIBLE"),
        ("wildcard target", Map("target" -> JsString("operation:OrderService.*")), Set.empty[String], "FD77_TARGET_INCOMPATIBLE"),
        ("other owner", Map("scope" -> JsString("component:org.other.Orders")), Set.empty[String], "FD77_TARGET_INCOMPATIBLE"),
        ("missing occurrence", Map.empty[String, JsValue], Set("finding"), "FD77_PROPERTY_INVALID"),
        ("numeric rule", Map("rule" -> JsNumber(1)), Set.empty[String], "FD77_PROPERTY_INVALID"),
        ("control provenance", Map("provenance" -> JsString("issue:\n42")), Set.empty[String], "FD77_PROPERTY_INVALID"),
        ("unknown property", Map("silent" -> JsBoolean(true)), Set.empty[String], "FD77_PROPERTY_UNKNOWN"))
      cases.foreach { case (name, updates, omitted, code) =>
        name in {
          Given("a declaration with " + name)
          val source = _source(_declaration(updates = updates, omitted = omitted))
          When("ordinary source inspection and generation run")
          val result = CmlFindingDisposition.inspectCml(source)
          val generation = CmlFindingDispositionScalaGenerator.fromModel(_model(source))
          Then("a structured failure is retained and no partial generated authority is produced")
          result.findings.map(_.code) should contain(code)
          result.coverage.map(_.status) shouldBe Vector(CoverageStatus.Failed)
          generation.isLeft shouldBe true
        }
      }
    }

    "keep unsupported and failed coverage distinct from completed work" in {
      Given("one valid binding, an unsupported target category and an invalid binding")
      val source = _source(_declaration("valid") +
        _declaration("unsupported", Map("target" -> JsString("entity:Order"))) +
        _declaration("failed", Map("target" -> JsString("operation:OrderService.missing"))))
      When("the selected CML source declarations are inspected")
      val result = CmlFindingDisposition.inspectCml(source)
      Then("only the successful CML check is completed, and no CAR execution coverage is implied")
      result.coverage.map(_.status) shouldBe Vector(CoverageStatus.Completed, CoverageStatus.Unsupported, CoverageStatus.Failed)
      result.findings.map(_.code) shouldBe Vector("FD77_TARGET_UNSUPPORTED", "FD77_TARGET_INCOMPATIBLE")
    }

    "reject exact duplicate finding keys and duplicate source identities" in {
      Given("a repeated key with another source heading, and distinct keys sharing one source heading")
      val duplicatekey = _source(_declaration("a") + _declaration("b"))
      val duplicateidentity = _source(_declaration("a") + _declaration("a", Map("finding" -> JsString("other-occurrence"))))
      When("the two ambiguous source models are inspected")
      val results = Vector(duplicatekey, duplicateidentity).map(CmlFindingDisposition.inspectCml)
      Then("neither repetition is allowed to suppress a neighboring occurrence")
      results.map(_.findings.head.code) shouldBe Vector("FD77_FINDING_DUPLICATE", "FD77_DECLARATION_DUPLICATE")
    }

    "reject an operation not linked to its actual component" in {
      Given("an otherwise existing service that the component does not declare")
      val source = _source(_declaration()).replace("### SERVICE\n\n#### OrderService\n", "")
      When("the exact owner and operation binding are checked")
      val result = CmlFindingDisposition.inspectCml(source)
      Then("the neighboring service cannot satisfy the target")
      result.findings.map(_.code) should contain("FD77_TARGET_INCOMPATIBLE")
    }

    "diagnose Scala annotation syntax and repeated properties as CML errors" in {
      Given("embedded Scala syntax and a second kind property")
      val annotation = _source("### invalid\n@FindingDisposition(kind = \"accept\")\n")
      val repeated = _source(_declaration() + "kind = \"accept\"\n")
      When("the native CML property grammar is applied")
      val results = Vector(annotation, repeated).map(CmlFindingDisposition.inspectCml)
      Then("the representation cannot introduce another decision authority")
      results.map(_.findings.head.code) shouldBe Vector("FD77_CML_INVALID", "FD77_PROPERTY_DUPLICATE")
    }

    "generate deterministic normalized CNCF calls from the current CML source" in {
      Given("the same declarations in two orders and then an explicitly changed decision")
      val a = _declaration("a", Map("finding" -> JsString("a"), "kind" -> JsString("accept")))
      val b = _declaration("b", Map("finding" -> JsString("b")))
      val first = _files(CmlFindingDispositionScalaGenerator.fromModel(_model(_source(a + b))))
      When("source order changes and the current CML kind is changed")
      val reordered = _files(CmlFindingDispositionScalaGenerator.fromModel(_model(_source(b + a))))
      val changed = _files(CmlFindingDispositionScalaGenerator.fromModel(_model(_source(a.replace("\"accept\"", "\"defer\"") + b))))
      Then("ordering is deterministic, source edits govern regeneration, and the existing CNCF decoder owns semantics")
      first shouldBe reordered
      first(_generated_path) should not be changed(_generated_path)
      first(_generated_path) should include("FindingDispositionRepresentation.decodeC(value)")
      first(_generated_path) should include("Consequence[Vector[FindingDispositionV1.Declaration]]")
      first(_generated_path) should not include "@FindingDisposition"
    }

    "compose the feature through both ordinary modeler routes and reject pure-library output" in {
      Given("one valid CML decision and both ordinary generation entry points")
      val source = _source(_declaration())
      Vector("modeler-scala", "modeler-scala-value").foreach { operation =>
        When(operation + " generates the CNCF artifact and then the library artifact")
        val files = _route_files(source, operation)
        val library = _route(source, operation, ModelGenerationTarget.Library)
        Then("the normalized producer is in the normal source Realm and the library reports its explicit unsupported boundary")
        files.keySet should contain(_generated_path)
        library match {
          case error: SError => error.conclusion.toString should include("LIBRARY_GENERATION_UNSUPPORTED_RUNTIME")
          case other => fail("expected structured library-target diagnostic: " + other)
        }
      }
    }

    "leave absent properties absent without inserting a decision or claiming coverage" in {
      Given("an ordinary component with no disposition declarations")
      When("inspection and both ordinary generators run")
      val inspection = CmlFindingDisposition.inspectCml(_component)
      val outputs = Vector("modeler-scala", "modeler-scala-value").map(_route_files(_component, _))
      Then("no declaration, finding, coverage or generated disposition authority is introduced")
      inspection shouldBe Inspection(Vector.empty, Vector.empty, Vector.empty)
      outputs.foreach(_.contains(_generated_path) shouldBe false)
    }
  }
}
