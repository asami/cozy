package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import com.typesafe.config.{ConfigFactory, ConfigParseOptions, ConfigSyntax}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.context.{Conclusion, ConclusionException}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel, Kaleidox}
import org.goldenport.realm.Realm
import org.goldenport.sexpr.{SAtom, SError, SExpr, SList, SModel, STree}
import org.goldenport.tree.TreeNode
import org.simplemodeling.model.SimpleModel
import cozy.ui.CozyLogicalUiRuntime.{ContentRole, TitleRole, SubtitleRole, DescriptionRole, StatusRole}
import LogicalUiProducer._
import LogicalUiProducer.DiagnosticCode._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class LogicalUiAbiGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)
  private val _reference = LogicalUiProducerSpec.reference
  private val _root = "target/scala-3.3.8/src_managed/main/scala/"
  private val _definitions_path = _root + "domain/logicalui/DomainLogicalUiDefinitions.scala"
  private val _provider_path = _root + "domain/logicalui/DomainLogicalUiMetadataProvider.scala"
  private val _routes = Vector("modeler-scala", "modeler-scala-value")
  private val _opaque = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
  private val _display = Gen.zip(_opaque, Gen.choose(1, 5)).map { case (identity, slashes) =>
    "  日本語 e\u0301 😀 $value ${literal} \"" + identity + "\" " + ("\\" * slashes) +
      "\n\r\t\b\f" + (0 to 31).map(_.toChar).mkString + "\u007f\u0085\u009f  "
  }
  private val _library_message =
    "LIBRARY_GENERATION_UNSUPPORTED_RUNTIME: category=logical-ui is a CNCF runtime feature. Use --generation-target cncf."
  private val _null_message = "logicalUi must be a non-null Option containing a non-null Realm."

  private def _resource(path: String): String = {
    val source = scala.io.Source.fromResource(path)(scala.io.Codec.UTF8)
    try source.mkString finally source.close()
  }

  private def _fixture(): String = _resource("logicalui/ess2-01.cml")

  private def _goldens(): Map[String, String] = Map(
    _definitions_path -> _resource("logicalui/golden/DomainLogicalUiDefinitions.scala.txt"),
    _provider_path -> _resource("logicalui/golden/DomainLogicalUiMetadataProvider.scala.txt")
  )

  private def _model(source: String): KaleidoxModel =
    KaleidoxModel.parse(KaleidoxConfig.default.withoutLocation, source)

  private def _route(model: KaleidoxModel, function: String, target: ModelGenerationTarget): SExpr = {
    val environment = cozy.Cozy.build(Array.empty[String]).environment
    val config = KaleidoxConfig.create(environment).setModeler(new Modeler(generationTarget = target))
    new Kaleidox(config, environment).createEngine().applyModelScript(
      model, SList(SAtom(function), SModel(model))
    )
  }

  private def _realm(result: Either[Diagnostic, Realm]): Realm = result match {
    case Right(realm) => realm
    case Left(diagnostic) => fail("expected admitted generation: " + diagnostic)
  }

  private def _tree(expr: SExpr): Realm = expr match {
    case STree(realm) => realm
    case other => fail("expected ordinary generated STree: " + other)
  }

  private def _error(expr: SExpr): Conclusion = expr match {
    case error: SError => error.conclusion
    case other => fail("expected structured SError without STree: " + other)
  }

  private def _files(realm: Realm): Map[String, String] = {
    def _entries_(node: TreeNode[Realm.Data]): Vector[(String, String)] = {
      val content = node.getContent.collect {
        case data: Realm.StringData => node.pathnameRelative -> data.string
      }.toVector
      content ++ node.children.toVector.flatMap(child => _entries_(child))
    }
    _entries_(realm.backendRoot).toMap
  }

  private def _bytes(files: Map[String, String]): Map[String, Vector[Byte]] =
    files.map { case (path, text) => path -> text.getBytes(StandardCharsets.UTF_8).toVector }

  private def _strings(text: String): Vector[String] = {
    val literals = "\"(?:[^\"\\\\]|\\\\.)*\"".r.findAllIn(text).toVector
    val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)
    literals.map(literal => ConfigFactory.parseString("{\"value\":" + literal + "}", options).getString("value"))
  }

  private def _component(name: String, namespace: String): String =
    "# COMPONENT\n\n## " + name + "\n\n### PACKAGE\n\n" + namespace + "\n\n"

  private def _detail_source(name: String, identity: String): String =
    "# LOGICAL-UI\ncomponent = \"" + name + "\"\nproducerId = \"producer\"\n" +
      "sourceId = \"source\"\nsourceRevision = \"source-revision\"\n\n## DETAIL\n" +
      "screenId = \"" + identity + "\"\ndefinitionRevision = \"definition\"\n" +
      "viewBindingId = \"view\"\ntitle = \"Detail\"\n"

  private def _detail(screen: DetailScreen): SourceComponent =
    _reference.copy(definitions = Vector(_reference.definitions(1).copy(screen = screen)))

  private def _invalid_cases(): Vector[(String, Vector[SourceComponent], Diagnostic)] = {
    val list = _reference.definitions.head.screen.asInstanceOf[ListScreen]
    val detail = _reference.definitions(1).screen.asInstanceOf[DetailScreen]
    val section = detail.sections.head
    val field = list.itemFields.head
    val elementreason = "null collection"
    val nullkinds = field.copy(value = field.value.copy(allowedKinds = null))
    val nullkindlist = list.copy(itemFields = Vector(nullkinds))
    val nullkindcomponent = _reference.copy(definitions = _reference.definitions.updated(0,
      _reference.definitions.head.copy(screen = nullkindlist)))
    Vector(
      ("null component vector", null,
        Diagnostic(ElementInvalid, "logicalUi", "null component vector")),
      ("null component", Vector(null),
        Diagnostic(ElementInvalid, "logicalUi[0]", "null source component")),
      ("null owner", Vector(_reference.copy(owner = null)),
        Diagnostic(ComponentInvalid, "logicalUi[0].owner", "explicit component owner is required")),
      ("invalid stem", Vector(_reference.copy(owner = ComponentOwner("domain-id", "domain"))),
        Diagnostic(ComponentInvalid, "logicalUi[0].owner.componentName", "expected Scala component stem")),
      ("reserved namespace", Vector(_reference.copy(owner = ComponentOwner("Domain", "foo.given"))),
        Diagnostic(ComponentInvalid, "logicalUi[0].owner.packageName", "expected explicit dot-separated Scala namespace")),
      ("null definitions", Vector(_reference.copy(definitions = null)),
        Diagnostic(ElementInvalid, "logicalUi[0].definitions", "null definition vector")),
      ("empty definitions", Vector(_reference.copy(definitions = Vector.empty)),
        Diagnostic(DefinitionsMissing, "logicalUi[0].definitions", "present component requires definitions")),
      ("late null definition", Vector(_reference.copy(definitions = _reference.definitions :+ null)),
        Diagnostic(ElementInvalid, "logicalUi[0].definitions[2]", "null definition")),
      ("null List fields", Vector(_reference.copy(definitions = _reference.definitions.updated(0,
        _reference.definitions.head.copy(screen = list.copy(itemFields = null))))),
        Diagnostic(ElementInvalid, "logicalUi[0].definitions[0].screen.itemFields", elementreason)),
      ("null nested field", Vector(_reference.copy(definitions = _reference.definitions.updated(0,
        _reference.definitions.head.copy(screen = list.copy(itemFields = Vector(field, null)))))),
        Diagnostic(ElementInvalid, "logicalUi[0].definitions[0].screen.itemFields[1]", "null collection element")),
      ("null section title Option", Vector(_detail(detail.copy(sections = Vector(section.copy(title = null))))),
        Diagnostic(ElementInvalid, "logicalUi[0].definitions[0].screen.sections[0].title", "null optional title shape")),
      ("Some null section title", Vector(_detail(detail.copy(sections = Vector(section.copy(title = Some(null)))))),
        Diagnostic(ElementInvalid, "logicalUi[0].definitions[0].screen.sections[0].title", "null optional title shape")),
      ("null kind vector", Vector(nullkindcomponent),
        Diagnostic(ElementInvalid, "logicalUi[0].definitions[0].screen.itemFields[0].value.allowedKinds", "null allowed-kind vector")),
      ("duplicate screen", Vector(_detail(detail).copy(definitions = Vector(
        _reference.definitions(1), _reference.definitions(1)))),
        Diagnostic(DuplicateId, "logicalUi[0].definitions[1].screenId", "duplicate within declared identity scope")),
      ("unresolved reference", Vector(_reference.copy(definitions = _reference.definitions.updated(0,
        _reference.definitions.head.copy(screen = list.copy(detailTarget = DetailTarget("other")))))),
        Diagnostic(DetailTargetInvalid, "logicalUi[0].definitions[0].screen.detailTarget.screenId", "target must resolve to same-component Detail")),
      ("late invalid component", Vector(_reference, _reference.copy(owner = ComponentOwner("Other", "other"),
        definitions = Vector(_reference.definitions(1).copy(definitionRevision = " padded ")))),
        Diagnostic(IdInvalid, "logicalUi[1].definitions[0].definitionRevision", "expected nonempty exactly trimmed opaque text")),
      ("repeated owner", Vector(_reference, _reference),
        Diagnostic(ComponentInvalid, "logicalUi[1].owner.componentName", "one Logical UI root per explicit component"))
    )
  }

  "Logical UI public ABI source generation" should {
    "emit the complete ESS2-01 contract" which {
      "matches both public source goldens through the pure renderer" in {
        Given("the admitted static reference and independent two-file golden expectations")
        val components = Vector(_reference)
        val expected = _goldens()
        When("the complete vector is rendered")
        val files = _files(_realm(LogicalUiScalaGenerator.generate(components)))
        Then("all constructor fields and the auxiliary provider equal the exact expectations")
        files shouldBe expected
        files(_definitions_path) should not include "cncf.expected-generated-fixture"
        files(_definitions_path) should not include "fixture-1"
      }

      _routes.foreach { function =>
        function + " merges the same golden artifacts through ordinary Modeler" in {
          Given("the ordinary parsed ESS2-01 CML and independent two-file expectations")
          val model = _model(_fixture())
          val expected = _goldens()
          When("the in-process ordinary CNCF Modeler route interprets the source")
          val files = _files(_tree(_route(model, function, ModelGenerationTarget.Cncf)))
          Then("both exact Logical UI artifacts accompany existing generation output")
          files.filter { case (path, _) => path.contains("/logicalui/") } shouldBe expected
          files.keys should contain("target/cozy/statemachine-workflow-abi.json")
          if (function == "modeler-scala")
            files.keys should contain(_root + "domain/DomainComponent.scala")
          else
            files.keys should not contain (_root + "domain/DomainComponent.scala")
          files.keys should not contain "build.sbt"
        }
      }

      "retains every closed role, kind, ordered field and optional container" in {
        Given("a List and Detail with all five roles/kinds, ordered actions and both section title forms")
        val kinds = Vector(StaticValueKind.Missing, StaticValueKind.Timestamp, StaticValueKind.Boolean,
          StaticValueKind.Number, StaticValueKind.Text)
        val fields = Vector(
          Field("content", " Content ", ContentRole, ValueBinding("value-content", kinds)),
          Field("title", " Title ", TitleRole, ValueBinding("value-title", Vector(StaticValueKind.Text))),
          Field("subtitle", " Subtitle ", SubtitleRole, ValueBinding("value-subtitle", Vector(StaticValueKind.Text))),
          Field("description", " Description ", DescriptionRole, ValueBinding("value-description", Vector(StaticValueKind.Text))),
          Field("status", " Status ", StatusRole, ValueBinding("value-status", Vector(StaticValueKind.Text))))
        val actions = Vector(Action("z-action", " Z action ", "bind-z"), Action("a-action", " A action ", "bind-a"))
        val component = _reference.copy(definitions = Vector(
          Definition("list-z", "list-revision", "list-view", ListScreen(" List ", fields,
            DetailTarget("detail-a"), actions, actions.reverse)),
          Definition("detail-a", "detail-revision", "detail-view", DetailScreen(" Detail ", Vector(
            Section("z-section", Some(" Section "), fields.reverse), Section("a-section", None, Vector.empty)), actions))))
        When("the fully admitted definitions are rendered")
        val text = _files(_realm(LogicalUiScalaGenerator.generate(Vector(component))))(_definitions_path)
        val strings = _strings(text)
        Then("identities and display bytes retain declaration order without inferred values")
        strings should contain inOrder ("list-z", "list-revision", "list-view", " List ", "content", " Content ",
          "value-content", "title", " Title ", "value-title", "subtitle", " Subtitle ", "value-subtitle",
          "description", " Description ", "value-description", "status", " Status ", "value-status")
        strings should contain inOrder ("z-action", " Z action ", "bind-z", "a-action", " A action ", "bind-a")
        strings should contain inOrder ("detail-a", "detail-revision", "detail-view", " Detail ",
          "z-section", " Section ", "status", "description", "subtitle", "title", "content", "a-section")
        text should include("Vector(GeneratedLogicalUiAbi.ValueKind.Missing, GeneratedLogicalUiAbi.ValueKind.Timestamp, GeneratedLogicalUiAbi.ValueKind.Boolean, GeneratedLogicalUiAbi.ValueKind.Number, GeneratedLogicalUiAbi.ValueKind.Text)")
        Vector("Content", "Title", "Subtitle", "Description", "Status").foreach { role =>
          text should include("GeneratedLogicalUiAbi.PresentationRole." + role)
        }
        text should include("Some(\" Section \")")
        text should include("GeneratedLogicalUiAbi.SectionDefinition(\"a-section\", None, Vector())")
        text should include("Vector(GeneratedLogicalUiAbi.ActionDefinition(\"a-action\", \" A action \", \"bind-a\"), GeneratedLogicalUiAbi.ActionDefinition(\"z-action\", \" Z action \", \"bind-z\"))")
      }
    }

    "retain explicit component ownership" which {
      "emits only declared namespace and stem artifacts for all selected components" in {
        Given("two explicitly owned components in reverse lexical order with a shared opaque screen ID")
        val components = Vector(_reference.copy(owner = ComponentOwner("Zeta", "org.example.zeta")),
          _reference.copy(owner = ComponentOwner("Alpha", "alpha")))
        When("the whole vector is rendered")
        val files = _files(_realm(LogicalUiScalaGenerator.generate(components)))
        Then("each owner supplies exactly its own two artifacts without first-component fallback")
        files.keySet shouldBe Set(
          _root + "org/example/zeta/logicalui/ZetaLogicalUiDefinitions.scala",
          _root + "org/example/zeta/logicalui/ZetaLogicalUiMetadataProvider.scala",
          _root + "alpha/logicalui/AlphaLogicalUiDefinitions.scala",
          _root + "alpha/logicalui/AlphaLogicalUiMetadataProvider.scala")
        files(_root + "org/example/zeta/logicalui/ZetaLogicalUiDefinitions.scala") should startWith("package org.example.zeta.logicalui\n")
        files(_root + "alpha/logicalui/AlphaLogicalUiMetadataProvider.scala") should include("AlphaLogicalUiDefinitions.definitions")
      }

      _routes.foreach { function =>
        function + " resolves every ordinary source owner" in {
          Given("ordinary COMPONENT declarations and separately selected roots in reverse declaration order")
          val source = _component("Alpha", "alpha") + _component("Zeta", "org.example.zeta") +
            _detail_source("Zeta", "shared") + _detail_source("Alpha", "shared")
          val model = _model(source)
          When("the ordinary Modeler route interprets all roots")
          val files = _files(_tree(_route(model, function, ModelGenerationTarget.Cncf)))
          Then("only explicit producer namespaces and stems appear in Logical UI artifacts")
          files.keys.filter(_.contains("/logicalui/")).toSet shouldBe Set(
            _root + "org/example/zeta/logicalui/ZetaLogicalUiDefinitions.scala",
            _root + "org/example/zeta/logicalui/ZetaLogicalUiMetadataProvider.scala",
            _root + "alpha/logicalui/AlphaLogicalUiDefinitions.scala",
            _root + "alpha/logicalui/AlphaLogicalUiMetadataProvider.scala")
        }
      }
    }

    "encode deterministic ordinary Scala literals" which {
      "preserves supplied text, dollars, supplementary Unicode and every control character" in {
        Given("generated padded labels containing quotes, slash parity, controls, Unicode and literal interpolation text")
        val samples = _display
        When("100 admitted static inputs are rendered twice and decoded as ordinary literals")
        val result = Test.check(_parameters, Prop.forAll(samples) { text =>
          val component = _detail(DetailScreen(text, Vector(Section("main", Some(text), Vector(
            Field("field", text, ContentRole, ValueBinding("binding", Vector(StaticValueKind.Text)))))),
            Vector(Action("action", text, "action-binding"))))
          val first = _files(_realm(LogicalUiScalaGenerator.generate(Vector(component))))
          val second = _files(_realm(LogicalUiScalaGenerator.generate(Vector(component))))
          val source = first(_definitions_path)
          _strings(source).count(_ == text) == 4 && source.contains("$value ${literal}") &&
            !source.contains("s\"") && !source.contains('\r') && source.endsWith("\n") &&
            _bytes(first) == _bytes(second) && first.keySet == Set(_definitions_path, _provider_path)
        })
        Then("ordinary literals roundtrip decoded bytes without trimming, normalization or interpolation")
        result.passed shouldBe true
      }

      "retains generated identity and definition order with stable UTF-8 source bytes" in {
        Given("generated distinct Detail identity vectors, separate provenance and definition revisions")
        val samples = Gen.choose(1, 12).flatMap(size => Gen.listOfN(size, _opaque)).map(_.zipWithIndex.map {
          case (identity, index) => identity + "-" + index
        }.toVector)
        When("100 whole vectors are rendered repeatedly")
        val result = Test.check(_parameters, Prop.forAll(samples) { identities =>
          val component = _reference.copy(definitions = identities.map(identity =>
            Definition(identity, identity + "-definition", "binding", DetailScreen(" Detail ", Vector.empty, Vector.empty))))
          val first = _files(_realm(LogicalUiScalaGenerator.generate(Vector(component))))
          val second = _files(_realm(LogicalUiScalaGenerator.generate(Vector(component))))
          val strings = _strings(first(_definitions_path))
          strings.filter(identities.contains) == identities && _bytes(first) == _bytes(second) &&
            first.values.forall(text => text.endsWith("\n") && !text.contains('\r') &&
              !text.contains("generated-time") && !text.contains("cozy.modeler") && !text.contains("cozy.ui"))
        })
        Then("ordered opaque IDs and independent revisions are stable across every generated family")
        result.passed shouldBe true
      }

      "has only public CNCF dependencies and reproducible ordinary-route Logical UI bytes" in {
        Given("the ordinary ESS2-01 model and both CNCF routes")
        val model = _model(_fixture())
        When("each ordinary route generates its Logical UI files twice")
        val generated = _routes.map { function =>
          val first = _files(_tree(_route(model, function, ModelGenerationTarget.Cncf)))
            .filter { case (path, _) => path.contains("/logicalui/") }
          val second = _files(_tree(_route(model, function, ModelGenerationTarget.Cncf)))
            .filter { case (path, _) => path.contains("/logicalui/") }
          (first, second)
        }
        Then("paths, text and UTF-8 bytes agree and emitted imports use only the public ABI")
        generated.foreach { case (first, second) =>
          first shouldBe second
          _bytes(first) shouldBe _bytes(second)
          first.values.foreach { text =>
            text should endWith("\n")
            text should not include "\r"
            text.split("\n", -1).toVector.filter(_.startsWith("import ")) shouldBe
              Vector("import org.goldenport.cncf.display.GeneratedLogicalUiAbi")
            Vector("cozy.ui", "cozy.modeler", "Flutter", "TFAF", "http://", "https://", "generated-time").foreach { dependency =>
              text should not include dependency
            }
          }
        }
      }
    }

    "preserve ordinary CML display bytes" which {
      _routes.foreach { function =>
        function + " preserves decoded ordinary CML literals" in {
          Given("ordinary CML text containing quotes, slash parity, controls, Unicode and dollars")
          val text = "  日本語 e\u0301 😀 $value ${literal} \"quoted\" " + ("\\" * 3) + "\n\r\t\b\f\u0001\u007f  "
          val literal = play.api.libs.json.Json.stringify(play.api.libs.json.Json.toJson(text)).replace("\u007f", "\\u007f")
          val model = _model(_fixture().replace("title = \"Collecting Resources\"", "title = " + literal)
            .replace("label = \"Title\"", "label = " + literal))
          When("the ordinary CNCF route renders the parsed display strings")
          val source = _files(_tree(_route(model, function, ModelGenerationTarget.Cncf)))(_definitions_path)
          val strings = _strings(source)
          Then("the title and both repeated field labels retain their decoded display bytes")
          strings.count(_ == text) shouldBe 3
          source should include("$value ${literal}")
          source should not include "\r"
          source should endWith("\n")
        }
      }
    }

    "reject the complete source before exposing any generated prefix" which {
      _invalid_cases().foreach { case (name, components, expected) =>
        name in {
          Given("a complete source vector containing " + name)
          val input = components
          When("whole-vector admission precedes source construction")
          val result = LogicalUiScalaGenerator.generate(input)
          val repeated = LogicalUiScalaGenerator.generate(input)
          Then("the exact existing first code, indexed path and reason replace the entire Realm")
          result shouldBe Left(expected)
          repeated shouldBe Left(expected)
        }
      }

      "keeps a late first diagnostic stable across generated otherwise valid prefixes" in {
        Given("a generated valid owner prefix and a late component with a malformed definition revision")
        val samples = _opaque
        val expected = Diagnostic(IdInvalid, "logicalUi[1].definitions[0].definitionRevision",
          "expected nonempty exactly trimmed opaque text")
        When("100 complete component vectors are admitted")
        val result = Test.check(_parameters, Prop.forAll(samples) { identity =>
          val prefix = _detail(DetailScreen("Detail", Vector.empty, Vector.empty)).copy(
            definitions = Vector(Definition(identity, "revision", "view", DetailScreen("Detail", Vector.empty, Vector.empty))))
          val invalid = prefix.copy(owner = ComponentOwner("Other", "other"), definitions =
            Vector(prefix.definitions.head.copy(definitionRevision = " padded ")))
          LogicalUiScalaGenerator.generate(Vector(prefix, invalid)) == Left(expected)
        })
        Then("a valid prefix never changes the late code/path/reason or escapes as a Realm")
        result.passed shouldBe true
      }

      _routes.foreach { function =>
        Vector[(String, String => String, Diagnostic)](
          ("late malformed definition", source => source.replace("screenId = \"capture-detail\"", "screenId = \" padded \""),
            Diagnostic(IdInvalid, "logicalUi[0].definitions[1].screenId", "expected nonempty exactly trimmed opaque text")),
          ("missing explicit owner", source => source.replace("component = \"Domain\"\n", ""),
            Diagnostic(ComponentInvalid, "logicalUi[0].prologue.component", "required property is absent")),
          ("late malformed property", source => source.replace("label = \"Description\"", "label = null"),
            Diagnostic(CmlInvalid, "logicalUi[0].sections[1].sections[0].sections[0].sections[0].sections[2].prologue.label", "expected JSON string"))
        ).foreach { case (name, change, expected) =>
          function + " preserves a typed " + name in {
            Given("ordinary parsed CML carrying " + name)
            val model = _model(change(_fixture()))
            When("the ordinary CNCF route interprets the whole source")
            val expr = _route(model, function, ModelGenerationTarget.Cncf)
            val conclusion = _error(expr)
            Then("structured invalid-argument failure retains the exact typed producer diagnostic")
            conclusion.data shouldBe Some(expected)
            conclusion shouldBe LogicalUiScalaGenerator.conclusion(expected)
            conclusion.faults.nonEmpty shouldBe true
            conclusion.status shouldBe Conclusion.invalidArgumentFault("expected").status
            conclusion.message shouldBe s"${expected.code.code}: path=${expected.path}; reason=${expected.reason}"
            expr should not be a [STree]
          }
        }
      }
    }

    "preserve absence and isolate library output" which {
      _routes.foreach { function =>
        function + " retains old output for absent Logical UI" in {
          Given("an ordinary VALUE fixture without Logical UI and unrelated document prose")
          val source = _resource("modeler/library/address.cml")
          val plain = _model(source)
          val prose = _model(source + "\n# ABOUT\nUnrelated ordinary prose.\n")
          When("the ordinary CNCF route interprets both absent-input models")
          val absent = LogicalUiScalaGenerator.fromModel(plain)
          val first = _files(_tree(_route(plain, function, ModelGenerationTarget.Cncf)))
          val second = _files(_tree(_route(prose, function, ModelGenerationTarget.Cncf)))
          Then("absence is None and existing generated value files and metadata are retained")
          absent shouldBe Right(None)
          first shouldBe second
          first.keys.exists(_.endsWith("Address.scala")) shouldBe true
          first.keys.filter(_.contains("/logicalui/")).toVector shouldBe empty
        }

        function + " rejects present Logical UI at the library Modeler boundary" in {
          Given("the ordinary valid reference model with the library target")
          val model = _model(_fixture())
          When("the direct Modeler route receives present Logical UI")
          val expr = _route(model, function, ModelGenerationTarget.Library)
          Then("the structured logical-ui category stops before any generated STree")
          _error(expr) shouldBe Conclusion.invalidArgumentFault(_library_message)
          expr should not be a [STree]
        }

        function + " retains malformed Logical UI diagnostics for the library target" in {
          Given("a malformed ordinary root with no explicit source owner")
          val model = _model(_fixture().replace("component = \"Domain\"\n", ""))
          val expected = Diagnostic(ComponentInvalid, "logicalUi[0].prologue.component", "required property is absent")
          When("the direct library Modeler route interprets source admission")
          val expr = _route(model, function, ModelGenerationTarget.Library)
          Then("the typed source fault precedes library category rejection and generated output")
          _error(expr) shouldBe LogicalUiScalaGenerator.conclusion(expected)
          _error(expr).data shouldBe Some(expected)
        }
      }

      "rejects library source preflight before other runtime category checks" in {
        Given("the valid ordinary reference source containing a COMPONENT and Logical UI")
        val path = Paths.get("src/test/resources/logicalui/ess2-01.cml")
        When("the ordinary library source preflight interprets that source")
        val error = intercept[ConclusionException] { ModelGenerationTarget.requireLibrarySource(path) }
        Then("logical-ui is the structured first unsupported category before ModelBuilder output")
        error.conclusion shouldBe Conclusion.invalidArgumentFault(_library_message)
      }

      "rejects malformed library source through the typed conclusion bridge" in {
        Given("an ordinary malformed source whose Logical UI owner is absent")
        val path = Paths.get("target/test-input/logicalui/malformed-library.cml")
        Files.createDirectories(path.getParent)
        Files.writeString(path, _fixture().replace("component = \"Domain\"\n", ""), StandardCharsets.UTF_8)
        val expected = Diagnostic(ComponentInvalid, "logicalUi[0].prologue.component", "required property is absent")
        When("the actual ordinary library source preflight interprets the malformed file")
        val error = intercept[ConclusionException] { ModelGenerationTarget.requireLibrarySource(path) }
        Then("the exception-required boundary preserves typed data, fault and message")
        error.conclusion shouldBe LogicalUiScalaGenerator.conclusion(expected)
        error.conclusion.data shouldBe Some(expected)
        error.conclusion.faults.nonEmpty shouldBe true
      }

      "admits an absent library Realm" in {
        Given("an explicitly absent Logical UI Realm")
        val logicalui: Option[Realm] = None
        When("the library-specific guard interprets that absence")
        val result = ModelGenerationTarget.requireLibraryLogicalUi(logicalui)
        Then("the existing library input remains admitted")
        result shouldBe (())
      }

      "retains ordinary value/library generation without Logical UI" in {
        Given("the existing pure Address VALUE model and explicit library target")
        val path = Paths.get("src/test/resources/modeler/library/address.cml")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, path.toFile)
        When("library preflight and ordinary value generation interpret absent Logical UI")
        ModelGenerationTarget.requireLibrarySource(path)
        val files = _files(_tree(_route(model, "modeler-scala-value", ModelGenerationTarget.Library)))
        Then("the library retains value source and excludes CNCF runtime artifacts")
        files.keys.exists(_.endsWith("Address.scala")) shouldBe true
        files.values.foreach(text => text should not include "org.goldenport.cncf")
        files.keys.filter(_.contains("/logicalui/")).toVector shouldBe empty
      }

      Vector(None, Some(Realm.create())).foreach { logicalui =>
        "input guard accepts " + (if (logicalui.isEmpty) "None" else "a non-null empty Realm") in {
          Given("a valid optional Realm container")
          val input = logicalui
          When("the public optional-input precondition is interpreted")
          val result = ModelGenerationTarget.requireLogicalUiInput(input)
          Then("the valid shape succeeds even when the Realm has no files")
          result shouldBe (())
        }
      }

      Vector[Option[Realm]](null, Some(null)).foreach { logicalui =>
        val name = if (logicalui == null) "null Option" else "Some(null)"
        name + " is structured at both public input guards" in {
          Given("a malformed optional Realm shape: " + name)
          val input = logicalui
          When("both guards interpret the malformed input before dereference")
          val first = intercept[ConclusionException] { ModelGenerationTarget.requireLogicalUiInput(input) }
          val second = intercept[ConclusionException] { ModelGenerationTarget.requireLibraryLogicalUi(input) }
          Then("both guards retain the exact invalid-argument fault instead of an NPE")
          first.conclusion shouldBe Conclusion.invalidArgumentFault(_null_message)
          second.conclusion shouldBe first.conclusion
        }
      }

      "rejects any present Realm including empty at direct library generator entry" in {
        Given("valid empty and rendered Realms and an otherwise valid empty SimpleModel")
        val environment = cozy.Cozy.build(Array.empty[String]).environment
        val model = SimpleModel(Vector.empty)
        val inputs = Vector(Realm.create(), _realm(LogicalUiScalaGenerator.generate(Vector(_reference))))
        When("each direct library generator interprets its optional Logical UI before transformation")
        val errors = inputs.map { realm =>
          intercept[ConclusionException] {
            new ScalaGenerator(environment, model, generationTarget = ModelGenerationTarget.Library,
              logicalUi = Some(realm)).generate(model.root)
          }
        }
        Then("every present Realm fails before any STree with the logical-ui category")
        errors.map(_.conclusion) shouldBe Vector.fill(2)(Conclusion.invalidArgumentFault(_library_message))
      }

      Vector(ModelGenerationTarget.Cncf, ModelGenerationTarget.Library).foreach { target =>
        Vector[Option[Realm]](null, Some(null)).foreach { logicalui =>
          val name = if (logicalui == null) "null Option" else "Some(null)"
          target.toString + " direct generator rejects " + name + " before transformation" in {
            Given("an otherwise valid SimpleModel and malformed optional Logical UI")
            val environment = cozy.Cozy.build(Array.empty[String]).environment
            val model = SimpleModel(Vector.empty)
            When("the direct generator interprets its precondition")
            val error = intercept[ConclusionException] {
              new ScalaGenerator(environment, model, generationTarget = target, logicalUi = logicalui).generate(model.root)
            }
            Then("the public constructor input is a structured invalid-argument failure")
            error.conclusion shouldBe Conclusion.invalidArgumentFault(_null_message)
          }
        }
      }

      "retains the default constructor and explicit None source compatibility" in {
        Given("an ordinary pure value model and the preexisting positional generator constructor")
        val environment = cozy.Cozy.build(Array.empty[String]).environment
        val source = _model(_resource("modeler/library/address.cml"))
        val model = new Modeler().buildValueModel(source)
        When("defaulted and explicitly absent library generators interpret the same model")
        val old = _files(new ScalaGenerator(environment, model, Vector.empty, Vector.empty,
          ModelGenerationTarget.Library).generate(model.root).tree)
        val absent = _files(new ScalaGenerator(environment, model, Vector.empty, Vector.empty,
          ModelGenerationTarget.Library, logicalUi = None).generate(model.root).tree)
        Then("the appended optional parameter preserves exact old value/library output")
        old shouldBe absent
        old.keys.exists(_.endsWith("Address.scala")) shouldBe true
      }
    }
  }
}
