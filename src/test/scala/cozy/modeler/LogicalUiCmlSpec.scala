package cozy.modeler

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.parser.{LogicalBlock, LogicalBlocks, LogicalLine, LogicalLines, LogicalParagraph, LogicalSection, LogicalVerbatim, StartBlock, EndBlock}
import org.goldenport.i18n.I18NElement
import LogicalUiProducer._
import LogicalUiProducer.DiagnosticCode._
import LogicalUiCncfAbiProjection._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class LogicalUiCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _config = KaleidoxConfig.default.withoutLocation
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)
  private val _opaque = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
  private val _display = Gen.zip(_opaque, Gen.choose(1, 5)).map { case (identity, slashes) =>
    "  日本語 \"" + identity + "\" " + ("\\" * slashes) + "\n\t\r\b\f  "
  }

  private def _fixture(): String = {
    val source = scala.io.Source.fromResource("logicalui/ess2-01.cml")
    try source.mkString finally source.close()
  }

  private def _model(source: String): KaleidoxModel = KaleidoxModel.parse(_config, source)

  private def _diagnostic(result: Either[Diagnostic, Vector[ProjectedComponent]]): Diagnostic = result match {
    case Left(diagnostic) => diagnostic
    case Right(_) => fail("expected an atomic ordinary CML rejection")
  }

  private def _literal(text: String): String = "\"" + text.flatMap {
    case '"' => "\\\""
    case '\\' => "\\\\"
    case '\n' => "\\n"
    case '\r' => "\\r"
    case '\t' => "\\t"
    case '\b' => "\\b"
    case '\f' => "\\f"
    case character if character < ' ' => f"\\u${character.toInt}%04x"
    case character => character.toString
  } + "\""

  private def _component(name: String, namespace: String): String =
    "# COMPONENT\n\n## " + name + "\n\n### PACKAGE\n\n" + namespace + "\n\n"

  private def _root(name: String): String =
    "# LOGICAL-UI\ncomponent = " + _literal(name) +
      "\nproducerId = \"producer\"\nsourceId = \"source\"\nsourceRevision = \"source-revision\"\n\n"

  private def _detail(id: String, text: String): String =
    "## DETAIL\nscreenId = " + _literal(id) +
      "\ndefinitionRevision = \"definition-revision\"\nviewBindingId = \"view\"\ntitle = " + _literal(text) + "\n\n"

  private def _document(section: LogicalSection): KaleidoxModel =
    _model(_component("Domain", "domain")).copy(divisions =
      _model(_component("Domain", "domain")).divisions :+ KaleidoxModel.DocumentDivision(section))

  private def _ast_root(blocks: Vector[LogicalBlock]): LogicalSection = LogicalSection("LOGICAL-UI", LogicalBlocks(blocks))

  private def _paragraph(text: String): LogicalParagraph =
    LogicalParagraph(LogicalLines(text.split("\n", -1).toVector.map(LogicalLine(_))))

  private def _properties(): LogicalParagraph = _paragraph(
    "component = \"Domain\"\nproducerId = \"producer\"\nsourceId = \"source\"\nsourceRevision = \"revision\"")

  private def _ast_detail(revision: String, binding: String): LogicalSection =
    LogicalSection("DETAIL", LogicalBlocks(Vector(_paragraph("screenId = \"detail\"\ndefinitionRevision = " +
      _literal(revision) + "\nviewBindingId = " + _literal(binding) + "\ntitle = \"Detail\""))), "#")

  "Ordinary Logical UI CML" should {
    "interpret the fixed source contract" which {
      "projects the complete ordinary ESS2-01 fixture" in {
        Given("the authored fixture read and parsed through the ordinary Kaleidox model builder")
        val source = _fixture()
        val model = KaleidoxModel.parse(KaleidoxConfig.default.withoutLocation, source)
        val expected = LogicalUiProducerSpec.expected
        When("ordinary AST extraction and atomic projection interpret the model")
        val result = LogicalUiCncfAbiProjection.fromModel(model)
        Then("the complete independently constructed reference projection matches")
        result shouldBe Right(Vector(expected))
      }

      "extracts exact static definitions before consumer projection" in {
        Given("the ordinary parsed reference fixture")
        val model = _model(_fixture())
        When("the CML source definitions are interpreted")
        val result = LogicalUiProducerCml.definitions(model)
        Then("the complete static reference has declared owner, provenance and definition order")
        result shouldBe Right(Vector(LogicalUiProducerSpec.reference))
      }

      "preserves absent Logical UI and unrelated document roots" in {
        Given("a source component and unrelated ordinary document divisions")
        val model = _model(_component("Domain", "domain") + "# ABOUT\nOrdinary prose.\n\n# NOTES\nMore prose.\n")
        When("the ordinary model is interpreted for Logical UI")
        val result = LogicalUiCncfAbiProjection.fromModel(model)
        Then("absence yields a successful empty component vector")
        result shouldBe Right(Vector.empty)
      }

      "keeps multiple explicit component roots in declaration order" in {
        Given("two distinct source component declarations and reversed explicit Logical UI ownership")
        val source = _component("First", "first") + _component("Second", "second") +
          _root("Second") + _detail("second", "Second") + _root("First") + _detail("first", "First")
        val model = _model(source)
        When("all selected ordinary roots are projected atomically")
        val result = LogicalUiCncfAbiProjection.fromModel(model)
        Then("each exact source owner is resolved without choosing the first declaration")
        result.right.get.map(_.owner) shouldBe Vector(ComponentOwner("Second", "second"), ComponentOwner("First", "first"))
        result.right.get.map(_.definitions.head.screenId) shouldBe Vector("second", "first")
      }

      "admits absent optional containers and preserves optional section titles" in {
        Given("a Detail with no actions and two sections, one untitled and one padded")
        val source = _component("Domain", "domain") + _root("Domain") + _detail("detail", "  Detail  ") +
          "### SECTIONS\n\n#### SECTION\nid = \"first\"\n\n#### SECTION\nid = \"second\"\ntitle = \"  Section  \"\n"
        val model = _model(source)
        When("the ordinary optional containers are projected")
        val result = LogicalUiCncfAbiProjection.fromModel(model)
        Then("absent fields/actions are empty and optional title bytes remain exact")
        val screen = result.right.get.head.definitions.head.screen.asInstanceOf[ProjectedDetailDefinition]
        screen shouldBe ProjectedDetailDefinition("  Detail  ", Vector(
          ProjectedSectionDefinition("first", None, Vector.empty),
          ProjectedSectionDefinition("second", Some("  Section  "), Vector.empty)), Vector.empty)
      }

      "admits actual immediate AST nesting without interpreting marker length as depth" in {
        Given("a programmatic root and immediate Detail child sharing the parser's outline marker")
        val child = _ast_detail("r", "view")
        val root = _ast_root(Vector(StartBlock, _properties(), child, EndBlock)).copy(mark = Some("#"))
        val model = _document(root)
        When("the actual immediate nesting is interpreted")
        val result = LogicalUiCncfAbiProjection.fromModel(model)
        Then("a valid Detail is projected from nesting rather than a nonexistent numeric mark level")
        result.right.get.head.definitions.head.screen shouldBe ProjectedDetailDefinition("Detail", Vector.empty, Vector.empty)
      }
    }

    "reject source ownership violations" which {
      val cases = Vector[(String, String => String)](
        "missing root owner" -> ((source: String) => source.replace("component = \"Domain\"\n", "")),
        "unknown root owner" -> ((source: String) => source.replace("component = \"Domain\"", "component = \"Other\"")),
        "padded owner identity" -> ((source: String) => source.replace("component = \"Domain\"", "component = \" Domain \"")),
        "absent source component" -> ((source: String) => source.substring(source.indexOf("# LOGICAL-UI"))),
        "missing package" -> ((source: String) => source.replace("### PACKAGE\n\ndomain\n", "")),
        "ambiguous source divisions" -> ((source: String) => _component("Domain", "other") + source),
        "ambiguous source children" -> ((source: String) => source.replace("# LOGICAL-UI", "## Domain\n\n### PACKAGE\n\nother\n\n# LOGICAL-UI")),
        "repeated Logical UI root" -> ((source: String) => source + source.substring(source.indexOf("# LOGICAL-UI"))),
        "componentlet fallback owner" -> ((source: String) => source.replace("# COMPONENT", "# COMPONENTLET")),
        "subsystem fallback owner" -> ((source: String) => source.replace("# COMPONENT", "# SUBSYSTEM")),
        "invalid namespace" -> ((source: String) => source.replace("\ndomain\n", "\na.given\n")),
        "invalid source stem" -> ((source: String) => source.replace("Domain", "domain"))
      )
      cases.foreach { case (name, transform) =>
        name in {
          Given("an ordinary fixture with " + name)
          val source = transform(_fixture())
          val model = _model(source)
          When("source ownership is interpreted before projection")
          val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
          Then("the exact source-owner contract rejects the component atomically")
          diagnostic.code shouldBe ComponentInvalid
          diagnostic.path should startWith("logicalUi")
          diagnostic.reason.trim should not be empty
        }
      }
    }

    "reject exact grammar and unsupported hierarchy" which {
      val cases = Vector[(String, String => String)](
        "near-case root" -> ((source: String) => source.replace("# LOGICAL-UI", "# logical-ui")),
        "near-case definition" -> ((source: String) => source.replace("## LIST", "## List")),
        "unknown definition" -> ((source: String) => source.replace("## LIST", "## FORM")),
        "unknown container" -> ((source: String) => source.replace("### ITEM-FIELDS", "### FIELDS")),
        "near-case container" -> ((source: String) => source.replace("### ITEM-FIELDS", "### item-fields")),
        "wrong List container child" -> ((source: String) => source.replace("#### FIELD", "#### ACTION")),
        "wrong Detail container child" -> ((source: String) => source.replace("#### SECTION", "#### FIELD")),
        "wrong section field child" -> ((source: String) => source.replace("###### FIELD", "###### SECTION")),
        "wrong action child" -> ((source: String) => source.replace("#### ACTION", "#### FIELD")),
        "wrong section container" -> ((source: String) => source.replace("##### FIELDS", "##### ACTIONS")),
        "leaf child" -> ((source: String) => source.replace("label = \"Title\"", "##### NESTED\nlabel = \"Title\"")),
        "repeated List container" -> ((source: String) => source.replace("## DETAIL", "### ITEM-FIELDS\n\n## DETAIL")),
        "repeated Detail container" -> ((source: String) => source + "\n### ACTIONS\n"),
        "repeated section container" -> ((source: String) => source.replace("### ACTIONS", "##### FIELDS\n\n### ACTIONS")),
        "nonempty container prologue" -> ((source: String) => source.replace("### ITEM-FIELDS", "### ITEM-FIELDS\ntitle = \"illegal\"")),
        "missing definition property" -> ((source: String) => source.replace("definitionRevision = \"ess-definition-1\"\n", "")),
        "near-case property" -> ((source: String) => source.replace("screenId =", "screenid =")),
        "unknown property" -> ((source: String) => source.replace("sourceRevision =", "unknown =")),
        "repeated property" -> ((source: String) => source.replace("sourceId = \"ess2-01\"", "sourceId = \"ess2-01\"\nsourceId = \"again\"")),
        "runtime-only property" -> ((source: String) => source.replace("label = \"Title\"", "label = \"Title\"\nenabled = true")),
        "ABI override property" -> ((source: String) => source.replace("sourceId = \"ess2-01\"", "sourceId = \"ess2-01\"\nabiVersion = 1")),
        "missing field property" -> ((source: String) => source.replace("bindingId = \"ess.title\"\n", "")),
        "missing action property" -> ((source: String) => source.replace("bindingId = \"ess.request-review\"\n", "")),
        "missing section identity" -> ((source: String) => source.replace("id = \"main\"\n", ""))
      )
      cases.foreach { case (name, transform) =>
        name in {
          Given("an ordinary fixture containing " + name)
          val source = transform(_fixture())
          val model = _model(source)
          When("the exact prologues and immediate parent-child hierarchy are interpreted")
          val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
          Then("the unsupported CML surface returns a structured shape diagnostic")
          diagnostic.code shouldBe CmlInvalid
          diagnostic.path should startWith("logicalUi")
        }
      }

      "rejects non-section child spill without discarding it" in {
        Given("a valid immediate Detail followed by nonempty root paragraph content")
        val child = _ast_detail("r", "v")
        val model = _document(_ast_root(Vector(_properties(), child, LogicalParagraph("sourceId = \"spill\""))))
        When("the actual ordinary AST shape is interpreted")
        val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
        Then("child spill is a CML error at the selected root")
        diagnostic.code shouldBe CmlInvalid
        diagnostic.path shouldBe "logicalUi[0]"
      }

      "rejects unsupported prologue blocks" in {
        Given("a selected root containing a verbatim block instead of direct property paragraphs")
        val verbatim = LogicalVerbatim(org.goldenport.parser.LogicalBlock.RawBackquoteMark(LogicalLine("```")), LogicalLines(Vector(LogicalLine("component = \"Domain\""))))
        val model = _document(_ast_root(Vector(verbatim)))
        When("the prologue block surface is interpreted")
        val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
        Then("verbatim input cannot act as property metadata")
        diagnostic.code shouldBe CmlInvalid
        diagnostic.path shouldBe "logicalUi[0].prologue"
      }

      "rejects a present empty root as missing definitions" in {
        Given("an ordinary Logical UI root with complete owner/provenance but no definition children")
        val model = _model(_component("Domain", "domain") + _root("Domain"))
        When("the extracted present component passes through the pure gate")
        val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
        Then("presence cannot masquerade as absent Logical UI")
        diagnostic.code shouldBe DefinitionsMissing
        diagnostic.path shouldBe "logicalUi[0].definitions"
      }
    }

    "admit only literal types and the closed vocabulary" which {
      Vector("null", "true", "123", "[]", "{}", "${value}", "\"a\" \"b\"", "\"a\", \"value\": \"b\"",
        "\"first\nsecond\"", "include \"other.conf\"", "\"a\"; sourceId = \"b\"").zipWithIndex.foreach { case (literal, index) =>
        s"reject invalid title literal family $index" in {
          Given("a single ordinary title property with a forbidden type or literal syntax")
          val source = _fixture().replace("title = \"Collecting Resources\"", "title = " + literal)
          val model = _model(source)
          When("the ordinary AST scalar is decoded without coercion or resolution")
          val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
          Then("the property is rejected atomically as CML")
          diagnostic.code shouldBe CmlInvalid
        }
      }

      Vector("null", "\"text\"", "[null]", "[1]", "[true]", "[\"text\", null]", "[[\"text\"]]").zipWithIndex.foreach { case (literal, index) =>
        s"reject wrong allowedKinds type family $index" in {
          Given("an ordinary FIELD with a wrong JSON collection or member type")
          val model = _model(_fixture().replace("allowedKinds = [\"text\"]", "allowedKinds = " + literal))
          When("the string-array property is interpreted")
          val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
          Then("wrong types cannot become inferred static kinds")
          diagnostic.code shouldBe CmlInvalid
        }
      }

      Vector("Text", "date", "choice", "reference", "structured", "image").foreach { tag =>
        s"reject unsupported kind tag $tag" in {
          Given("a FIELD whose string kind tag lies outside the five exact alternatives")
          val model = _model(_fixture().replace("allowedKinds = [\"text\"]", "allowedKinds = [" + _literal(tag) + "]"))
          When("the case-sensitive static kind is interpreted")
          val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
          Then("an unknown string tag uses the specialized kind diagnostic")
          diagnostic.code shouldBe ValueKindInvalid
          diagnostic.path should include(".prologue.allowedKinds[0]")
        }
      }

      Vector("Title", "other").foreach { tag =>
        s"reject unsupported role tag $tag" in {
          Given("a FIELD whose string role tag is not an exact shared role")
          val model = _model(_fixture().replace("role = \"title\"", "role = " + _literal(tag)))
          When("the case-sensitive shared role is interpreted")
          val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
          Then("an unknown string role has the specialized role diagnostic")
          diagnostic.code shouldBe RoleInvalid
          diagnostic.path should include(".prologue.role")
        }
      }

      "keeps the first CML diagnostic before any pure semantic failure" in {
        Given("a first validly shaped but semantically invalid definition followed by malformed CML")
        val source = _fixture().replace("screenId = \"collecting-resources\"", "screenId = \" padded \"")
          .replace("title = \"Capture Detail\"", "title = 7")
        val model = _model(source)
        When("complete source extraction precedes pure admission")
        val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
        Then("the malformed CML type prevents publication of every extracted component")
        diagnostic.code shouldBe CmlInvalid
        diagnostic.path shouldBe "logicalUi[0].sections[1].prologue.title"
      }
    }

    "reject programmatic AST null shapes safely" which {
      val roots = Vector[(String, LogicalSection)](
        "null section" -> null,
        "null title" -> _ast_root(Vector.empty).copy(title = null),
        "null title representation" -> _ast_root(Vector.empty).copy(title = I18NElement(null: org.goldenport.i18n.I18NString)),
        "null blocks" -> _ast_root(Vector.empty).copy(blocks = null),
        "null block vector" -> _ast_root(Vector.empty).copy(blocks = LogicalBlocks(null: Vector[LogicalBlock])),
        "null block element" -> _ast_root(Vector(null)),
        "null mark Option" -> _ast_root(Vector.empty).copy(mark = null),
        "Some null mark" -> _ast_root(Vector.empty).copy(mark = Some(null)),
        "null location Option" -> _ast_root(Vector.empty).copy(location = null),
        "Some null location" -> _ast_root(Vector.empty).copy(location = Some(null)),
        "null paragraph lines" -> _ast_root(Vector(LogicalParagraph(null: LogicalLines))),
        "null line vector" -> _ast_root(Vector(LogicalParagraph(LogicalLines(null: Vector[LogicalLine])))),
        "null line element" -> _ast_root(Vector(LogicalParagraph(LogicalLines(Vector(null))))),
        "null line text" -> _ast_root(Vector(LogicalParagraph(LogicalLines(Vector(LogicalLine(null: String)))))),
        "null physical lines" -> _ast_root(Vector(LogicalParagraph(LogicalLines(Vector(LogicalLine("x", null, None)))))),
        "null nested title" -> _ast_root(Vector(LogicalSection("DETAIL", LogicalBlocks.empty).copy(title = null)))
      )
      roots.foreach { case (name, root) =>
        name in {
          Given("an ordinary model carrying a programmatic DocumentDivision with " + name)
          val model = _document(root)
          When("the adapter interprets the AST before dereferencing members")
          val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
          Then("the null AST shape is a typed element rejection")
          diagnostic.code shouldBe ElementInvalid
          diagnostic.path should startWith("logicalUi[0]")
        }
      }

      "rejects a null ordinary model" in {
        Given("a null model root")
        val model: KaleidoxModel = null
        When("the model entry point is interpreted")
        val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.fromModel(model))
        Then("the model is rejected before divisions are read")
        diagnostic shouldBe Diagnostic(ElementInvalid, "logicalUi", "null model")
      }
    }

    "preserve ordinary decoded text and order for generated families" which {
      "roundtrips quotes, slash parity, Unicode, control escapes and padding" in {
        Given("generated padded text with escaped quotes, one through five backslashes and decoded controls")
        val samples = _display
        val source = _fixture()
        When("100 sources pass through ordinary Model.parse and complete projection")
        val result = Test.check(_parameters, Prop.forAll(samples) { text =>
          val input = source.replace("title = \"Collecting Resources\"", "title = " + _literal(text))
            .replace("label = \"Title\"", "label = " + _literal(text))
          LogicalUiCncfAbiProjection.fromModel(_model(input)).exists { components =>
            val definitions = components.head.definitions
            val list = definitions.head.screen.asInstanceOf[ProjectedListDefinition]
            val detail = definitions(1).screen.asInstanceOf[ProjectedDetailDefinition]
            list.title == text && list.itemFields.head.label == text && detail.sections.head.fields.head.label == text &&
              definitions.map(_.screenId) == Vector("collecting-resources", "capture-detail") &&
              components.head.definitions.head.provenance.revision == "ess2-01-cml-1"
          }
        })
        Then("decoded bytes and subsequent sibling sections survive ordinary quote grouping")
        result.passed shouldBe true
      }

      "preserves generated opaque identities and source revisions without inference" in {
        Given("generated trimmed opaque identities independently declared in source provenance and definitions")
        val samples = _opaque
        When("100 ordinary sources are parsed and projected")
        val result = Test.check(_parameters, Prop.forAll(samples) { identity =>
          val input = _component("Domain", "domain") + _root("Domain").replace("\"source-revision\"", _literal(identity)) +
            _detail(identity, "Detail").replace("\"definition-revision\"", _literal(identity + "-definition"))
          LogicalUiCncfAbiProjection.fromModel(_model(input)).exists { components =>
            val definition = components.head.definitions.head
            definition.screenId == identity && definition.definitionRevision == identity + "-definition" &&
              definition.provenance.revision == identity
          }
        })
        Then("each opaque source and definition value remains independently declared")
        result.passed shouldBe true
      }

      "preserves declaration order across ordinary sibling definitions" in {
        Given("generated vectors of distinct Detail IDs with padded display titles")
        val samples = Gen.choose(2, 10).flatMap(size => Gen.listOfN(size, Gen.zip(_opaque, _display))).map(_.zipWithIndex.map {
          case ((identity, text), index) => (identity + index, text)
        }.toVector)
        When("100 ordinary multi-definition sources are projected")
        val result = Test.check(_parameters, Prop.forAll(samples) { declarations =>
          val input = _component("Domain", "domain") + _root("Domain") + declarations.map {
            case (identity, text) => _detail(identity, text)
          }.mkString
          LogicalUiCncfAbiProjection.fromModel(_model(input)).exists { components =>
            components.head.definitions.map(_.screenId) == declarations.map(_._1) &&
              components.head.definitions.map(_.screen.asInstanceOf[ProjectedDetailDefinition].title) == declarations.map(_._2)
          }
        })
        Then("ordinary source order and decoded title bytes survive every generated vector")
        result.passed shouldBe true
      }

      "retains first-source diagnostics despite later independently malformed roots" in {
        Given("a fixed first near-case definition and generated later unsupported properties")
        val samples = _opaque
        When("100 complete ordinary documents are interpreted")
        val result = Test.check(_parameters, Prop.forAll(samples) { identity =>
          val input = _component("Domain", "domain") + _root("Domain") +
            _detail("first", "First").replace("## DETAIL", "## Detail") +
            _root("Domain") + _detail(identity, "Later") + "unknown = \"later\"\n"
          LogicalUiCncfAbiProjection.fromModel(_model(input)).left.toOption.exists(diagnostic =>
            diagnostic.code == CmlInvalid && diagnostic.path == "logicalUi[0].sections[0]")
        })
        Then("the first source rule and indexed path are deterministic")
        result.passed shouldBe true
      }
    }
  }
}
