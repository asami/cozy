package cozy.modeler

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.parser.{LogicalBlock, LogicalBlocks, LogicalLine, LogicalLines, LogicalParagraph, LogicalSection}
import play.api.libs.json.{JsString, Json}
import ValidationMetadataCml._

/*
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
class ValidationMetadataCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)
  private def _model(source: String): KaleidoxModel =
    KaleidoxModel.parse(KaleidoxConfig.default.withoutLocation, source)
  private def _source(properties: String, provider: String = "org.example.MetadataDeclaration"): String =
    "# VALIDATION-METADATA\n\n## DECLARATION\nprovider = " + Json.stringify(JsString(provider)) +
      "\nproperties = " + properties + "\n"
  private def _fixture(): String = {
    val source = scala.io.Source.fromResource("validation/metadata.cml")(scala.io.Codec.UTF8)
    try source.mkString finally source.close()
  }
  private def _diagnostic(result: Either[Diagnostic, Vector[SourceDeclaration]]): Diagnostic = result match {
    case Left(diagnostic) => diagnostic
    case Right(value) => fail("expected atomic rejection, received " + value)
  }
  private def _ast(blocks: Vector[LogicalBlock]): KaleidoxModel =
    _model("").copy(divisions = Vector(KaleidoxModel.DocumentDivision(
      LogicalSection("VALIDATION-METADATA", LogicalBlocks(blocks)))))
  private def _paragraph(text: String): LogicalParagraph =
    LogicalParagraph(LogicalLines(text.split("\n", -1).toVector.map(LogicalLine(_))))

  "Ordinary validation metadata CML" should {
    "decode the full fixture and explicit empty declaration" in {
      Given("the ordinary fixture with a full declaration followed by an explicit empty one")
      val model = _model(_fixture())
      val expected = Properties(abiVersion = 1, purposes = Some(Set("FOCUSED")),
        features = Set(FeatureReference("component:domain", "feature-a")),
        expectedDuration = Some(DurationValue(500L, DurationUnit.Milliseconds)), durationClass = Some("NORMAL"),
        executionRequirement = Some(ExecutionRequirement(Vector("metadata-fixture"), "local",
          ReasoningLevel.Standard, false, Some("application.analysis"),
          Some(ExecutionDisposition(ExecutionPlacement.Delegated, ExecutionIndependence.Required, "context-a")))),
        requiredSemantics = Set("validation.metadata.v1"))
      When("the ordinary AST is interpreted")
      val result = ValidationMetadataCml.fromModel(model)
      Then("all typed fields and provider order equal their explicit source declarations")
      result shouldBe Right(Vector(
        SourceDeclaration(ProviderName("org.example.validation", "FocusedDeclaration"), expected),
        SourceDeclaration(ProviderName("org.example.validation", "EmptyDeclaration"), Properties())))
    }

    "distinguish absent purposes from an explicit empty purpose set" in {
      Given("an explicit empty properties object and one with an empty purposes array")
      val absent = _model(_source("{}"))
      val empty = _model(_source("{\"purposes\":[]}"))
      When("both properties objects are decoded")
      val results = Vector(absent, empty).map(ValidationMetadataCml.fromModel)
      Then("declaration presence is retained while purpose inheritance differs")
      results shouldBe Vector(
        Right(Vector(SourceDeclaration(ProviderName("org.example", "MetadataDeclaration"), Properties()))),
        Right(Vector(SourceDeclaration(ProviderName("org.example", "MetadataDeclaration"), Properties(purposes = Some(Set.empty))))))
    }

    "leave semantic declaration validity with CNCF" in {
      Given("well-shaped values outside CNCF semantic policy, including a negative duration")
      val model = _model(_source("{\"abiVersion\":-1,\"purposes\":[\"unknown\"]," +
        "\"features\":[{\"scope\":\" \",\"identity\":\"\"}],\"expectedDuration\":{\"amount\":-1,\"unit\":\"DAYS\"}," +
        "\"durationClass\":\"other\",\"requiredSemantics\":[\"unknown\"]}"))
      When("Cozy interprets only the source shape")
      val result = ValidationMetadataCml.fromModel(model)
      Then("the exact semantic values remain available to the normalized CNCF mapping")
      result.isRight shouldBe true
      result.toOption.get.head.properties.purposes shouldBe Some(Set("unknown"))
      result.toOption.get.head.properties.features shouldBe Set(FeatureReference(" ", ""))
      result.toOption.get.head.properties.expectedDuration shouldBe Some(DurationValue(-1L, DurationUnit.Days))
    }

    "accept exact Long endpoints without numeric truncation" in {
      Given("the two exact Long endpoint JSON numbers")
      val values = Vector(Long.MinValue, Long.MaxValue)
      val models = values.map(value => _model(_source(
        "{\"expectedDuration\":{\"amount\":" + value + ",\"unit\":\"NANOSECONDS\"}}")))
      When("the duration amounts are decoded by the ordinary JSON library")
      val results = models.map(ValidationMetadataCml.fromModel)
      Then("both exact endpoint values survive source interpretation")
      results.map(_.toOption.get.head.properties.expectedDuration.get.amount) shouldBe values
    }

    "reject malformed selected inputs atomically" which {
      val cases = Vector(
        ("unknown property", _source("{\"other\":1}"), ".properties.other"),
        ("null properties", _source("null"), ".properties"),
        ("array properties", _source("[]"), ".properties"),
        ("executable properties", _source("Properties()"), ".properties"),
        ("invalid JSON", _source("{bad}"), ".properties"),
        ("missing provider", _source("{}").replace("provider = \"org.example.MetadataDeclaration\"\n", ""), ".provider"),
        ("missing properties", _source("{}").replace("properties = {}\n", ""), ".properties"),
        ("direct duplicate", _source("{}") + "properties = {}\n", ".properties"),
        ("duplicate provider", _source("{}") + _source("{}"), ".provider"),
        ("unknown direct property", _source("{}") + "body = \"value\"\n", ".body"),
        ("root properties", "# VALIDATION-METADATA\nprovider = \"org.example.Other\"\n", ".provider"),
        ("unknown child", _source("{}").replace("## DECLARATION", "## Declaration"), ".sections[0]"),
        ("nested child", _source("{}") + "\n### CHILD\n", ".sections[0]"),
        ("provider without package", _source("{}", "Declaration"), ".provider"),
        ("keyword package", _source("{}", "example.given.Declaration"), ".provider"),
        ("keyword class", _source("{}", "example.class"), ".provider"),
        ("lowercase class", _source("{}", "example.declaration"), ".provider"),
        ("path provider", _source("{}", "../Declaration"), ".provider"),
        ("injected provider", _source("{}", "example.Declaration { evil }"), ".provider"),
        ("null provider", _source("{}").replace("\"org.example.MetadataDeclaration\"", "null"), ".provider"),
        ("nonstring provider", _source("{}").replace("\"org.example.MetadataDeclaration\"", "7"), ".provider"),
        ("fractional ABI", _source("{\"abiVersion\":1.1}"), ".abiVersion"),
        ("overflow ABI", _source("{\"abiVersion\":2147483648}"), ".abiVersion"),
        ("fractional amount", _source("{\"expectedDuration\":{\"amount\":1.1,\"unit\":\"SECONDS\"}}"), ".amount"),
        ("overflow amount", _source("{\"expectedDuration\":{\"amount\":9223372036854775808,\"unit\":\"SECONDS\"}}"), ".amount"),
        ("near-case unit", _source("{\"expectedDuration\":{\"amount\":1,\"unit\":\"seconds\"}}"), ".unit"),
        ("extra feature key", _source("{\"features\":[{\"scope\":\"s\",\"identity\":\"i\",\"other\":0}]}"), ".other"),
        ("missing feature key", _source("{\"features\":[{\"scope\":\"s\"}]}"), ".identity"),
        ("string array member", _source("{\"purposes\":[1]}"), ".purposes[0]"),
        ("object feature member", _source("{\"features\":[\"f\"]}"), ".features[0]"),
        ("wrong duration shape", _source("{\"expectedDuration\":[]}"), ".expectedDuration"),
        ("wrong duration class", _source("{\"durationClass\":1}"), ".durationClass"),
        ("wrong semantics shape", _source("{\"requiredSemantics\":\"x\"}"), ".requiredSemantics"))
      cases.foreach { case (name, source, suffix) =>
        name in {
          Given("ordinary CML containing " + name)
          val model = _model(source)
          When("the selected source is interpreted before generation")
          val diagnostic = _diagnostic(ValidationMetadataCml.fromModel(model))
          Then("a structured path identifies the rejected source member without partial declarations")
          diagnostic.path should endWith(suffix)
          diagnostic.code should not be empty
          diagnostic.reason should not be empty
        }
      }
    }

    "reject explicit nulls at selected properties and members" which {
      val properties = Vector("abiVersion", "purposes", "features", "expectedDuration", "durationClass",
        "executionRequirement", "requiredSemantics").map(key => "{\"" + key + "\":null}") ++ Vector(
        "{\"purposes\":[null]}", "{\"features\":[null]}", "{\"features\":[{\"scope\":null,\"identity\":\"i\"}]}",
        "{\"features\":[{\"scope\":\"s\",\"identity\":null}]}", "{\"requiredSemantics\":[null]}",
        "{\"expectedDuration\":{\"amount\":null,\"unit\":\"SECONDS\"}}",
        "{\"expectedDuration\":{\"amount\":1,\"unit\":null}}")
      properties.foreach { properties =>
        properties in {
          Given("an explicitly null selected field or collection member")
          val model = _model(_source(properties))
          When("the properties object is structurally interpreted")
          val diagnostic = _diagnostic(ValidationMetadataCml.fromModel(model))
          Then("null never acts as absence or a default")
          diagnostic.path should include(".properties.")
          diagnostic.reason should not be empty
        }
      }
    }

    "interpret the finite execution requirement source ABI" which {
      val base = Json.parse("{\"capabilities\":[\"cap\"],\"risk\":\" risk \",\"reasoning\":\"ROUTINE\",\"reviewRequired\":true}")
        .as[play.api.libs.json.JsObject]
      val invalid = Vector(
        base - "capabilities", base - "risk", base - "reasoning", base - "reviewRequired",
        base ++ Json.obj("other" -> 1), base ++ Json.obj("capabilities" -> "cap"),
        base ++ Json.obj("capabilities" -> Json.arr(play.api.libs.json.JsNull)),
        base ++ Json.obj("risk" -> 1), base ++ Json.obj("reasoning" -> "routine"),
        base ++ Json.obj("reviewRequired" -> "true"), base ++ Json.obj("reasoningClass" -> 1),
        base ++ Json.obj("disposition" -> Json.obj("placement" -> "inline", "independence" -> "OPTIONAL", "originContextIdentity" -> "ctx")),
        base ++ Json.obj("disposition" -> Json.obj("placement" -> "INLINE", "independence" -> "required", "originContextIdentity" -> "ctx")),
        base ++ Json.obj("disposition" -> Json.obj("placement" -> "INLINE", "independence" -> "OPTIONAL")),
        base ++ Json.obj("disposition" -> Json.obj("placement" -> "INLINE", "independence" -> "OPTIONAL", "originContextIdentity" -> 1)),
        base ++ Json.obj("disposition" -> Json.obj("placement" -> "INLINE", "independence" -> "OPTIONAL", "originContextIdentity" -> "ctx", "other" -> 1))) ++
        Vector("capabilities", "risk", "reasoning", "reviewRequired", "reasoningClass", "disposition")
          .map(key => base ++ Json.obj(key -> play.api.libs.json.JsNull)) ++
        Vector("placement", "independence", "originContextIdentity").map { key =>
          base ++ Json.obj("disposition" -> (Json.obj("placement" -> "INLINE", "independence" -> "OPTIONAL",
            "originContextIdentity" -> "ctx") ++ Json.obj(key -> play.api.libs.json.JsNull)))
        }
      invalid.zipWithIndex.foreach { case (requirement, index) =>
        s"reject execution requirement shape $index" in {
          Given("a requirement with a missing, unknown, null or wrongly typed member")
          val model = _model(_source(Json.stringify(Json.obj("executionRequirement" -> requirement))))
          When("the public source requirement shape is interpreted")
          val diagnostic = _diagnostic(ValidationMetadataCml.fromModel(model))
          Then("the failure stays within the execution requirement path")
          diagnostic.path should include(".properties.executionRequirement.")
        }
      }
      Vector("ROUTINE", "STANDARD", "DEEP", "CRITICAL").foreach { label =>
        s"admit exact reasoning $label" in {
          Given("a complete requirement with an exact finite reasoning label and opaque risk")
          val requirement = base ++ Json.obj("reasoning" -> label)
          val model = _model(_source(Json.stringify(Json.obj("executionRequirement" -> requirement))))
          When("the source requirement is interpreted")
          val result = ValidationMetadataCml.fromModel(model)
          Then("the typed constructor and untrimmed semantic risk survive")
          result.isRight shouldBe true
          result.toOption.get.head.properties.executionRequirement.get.risk shouldBe " risk "
        }
      }
    }

    "reject invalid programmatic ordinary AST shapes" which {
      val models = Vector(
        "null model" -> null,
        "null block" -> _ast(Vector(null)),
        "null paragraph lines" -> _ast(Vector(LogicalParagraph(null: LogicalLines))),
        "null line text" -> _ast(Vector(_paragraph("x").copy(lines = LogicalLines(Vector(LogicalLine(null: String)))))),
        "null section title" -> _model("").copy(divisions = Vector(KaleidoxModel.DocumentDivision(
          LogicalSection("VALIDATION-METADATA", LogicalBlocks.empty).copy(title = null)))))
      models.foreach { case (name, model) =>
        name in {
          Given("an ordinary programmatic AST containing " + name)
          val input = model
          When("source shape is checked before member access")
          val diagnostic = _diagnostic(ValidationMetadataCml.fromModel(input))
          Then("an element diagnostic represents the malformed AST")
          diagnostic.code shouldBe "VALIDATION_METADATA_ELEMENT_INVALID"
        }
      }
    }

    "preserve single-line Unicode string data while rejecting physical multiline assignments" in {
      Given("a padded Japanese duration class containing NEL, C1, line separator and paragraph separator")
      val text = " 日本語 \u0085\u009f\u2028\u2029 "
      val model = _model(_source(Json.stringify(Json.obj("durationClass" -> text))))
      When("the ordinary AST is interpreted with normal JSON decoding")
      val result = ValidationMetadataCml.fromModel(model)
      Then("the exact original duration class string is preserved")
      result shouldBe Right(Vector(SourceDeclaration(
        ProviderName("org.example", "MetadataDeclaration"), Properties(durationClass = Some(text)))))

      Given("a duration class JSON string split by an actual LF in its assignment")
      val multiline = _model(_source("{\"durationClass\":\"日本語\nvalue\"}"))
      When("the physically multiline source is interpreted")
      val rejected = ValidationMetadataCml.fromModel(multiline)
      Then("the assignment is rejected atomically")
      rejected.isLeft shouldBe true
    }

    "roundtrip exact JSON strings through ordinary CML parsing" in {
      Given("generated strings with padding, Unicode, quotes, slash parity and escaped controls")
      val samples = Gen.zip(Gen.nonEmptyListOf(Gen.alphaNumChar), Gen.choose(1, 5)).map { case (text, count) =>
        " 日本語 😀 $value ${literal} \"" + text.mkString + "\" " + ("\\" * count) + "\n\r\t\b\f "
      }
      When("100 ordinary CML models decode the JSON string literals")
      val result = Test.check(_parameters, Prop.forAll(samples) { text =>
        val model = _model(_source(Json.stringify(Json.obj("durationClass" -> text))))
        ValidationMetadataCml.fromModel(model).exists(_.head.properties.durationClass.contains(text))
      })
      Then("exact decoded bytes reach the typed source model")
      result.passed shouldBe true
    }

    "preserve declaration order and absence for generated documents" in {
      Given("generated distinct providers with unrelated roots and optional empty metadata roots")
      val samples = Gen.choose(0, 12)
      When("100 ordinary documents are interpreted")
      val result = Test.check(_parameters, Prop.forAll(samples) { count =>
        val providers = (0 until count).reverse.map(index => "example.Declaration" + index).toVector
        val source = "# OTHER\nignored = arbitrary\n\n# VALIDATION-METADATA\n\n" +
          providers.map(provider => _source("{}", provider)).mkString("\n")
        ValidationMetadataCml.fromModel(_model(source)).exists(_.map(_.provider.fullName) == providers) &&
          ValidationMetadataCml.fromModel(_model("# Validation-Metadata\nanything = ignored\n")) == Right(Vector.empty)
      })
      Then("only exact roots contribute, without sorting providers or inventing absent declarations")
      result.passed shouldBe true
    }
  }
}
