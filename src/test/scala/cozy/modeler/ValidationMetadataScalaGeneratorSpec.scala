package cozy.modeler

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
import play.api.libs.json.{JsString, Json}
import ValidationMetadataCml.Diagnostic

/*
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
class ValidationMetadataScalaGeneratorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)
  private val _root = "target/scala-3.3.8/src_managed/test/scala/"
  private val _empty_path = _root + "org/example/validation/EmptyDeclaration.scala"
  private val _focused_path = _root + "org/example/validation/FocusedDeclaration.scala"
  private val _routes = Vector("modeler-scala", "modeler-scala-value")
  private val _component = "# COMPONENT\n\n## Domain\n\n### PACKAGE\n\ndomain\n\n"

  private def _model(source: String): KaleidoxModel =
    KaleidoxModel.parse(KaleidoxConfig.default.withoutLocation, source)
  private def _fixture(): String = {
    val source = scala.io.Source.fromResource("validation/metadata.cml")(scala.io.Codec.UTF8)
    try source.mkString finally source.close()
  }
  private def _source(properties: String, provider: String = "org.example.validation.EmptyDeclaration"): String =
    "# VALIDATION-METADATA\n\n## DECLARATION\nprovider = " + Json.stringify(JsString(provider)) +
      "\nproperties = " + properties + "\n"
  private def _realm(result: Either[Diagnostic, Option[Realm]]): Realm = result match {
    case Right(Some(realm)) => realm
    case other => fail("expected generated provider Realm: " + other)
  }
  private def _entries(node: TreeNode[Realm.Data]): Vector[(String, String)] = {
    val content = node.getContent.collect {
      case data: Realm.StringData => node.pathnameRelative -> data.string
    }.toVector
    content ++ node.children.toVector.flatMap(_entries)
  }
  private def _files(realm: Realm): Map[String, String] = _entries(realm.backendRoot).toMap
  private def _tree(expr: SExpr): Realm = expr match {
    case STree(realm) => realm
    case error: SError => fail("expected ordinary generated STree: " + error, error.conclusion.toException)
    case other => fail("expected ordinary generated STree: " + other)
  }
  private def _error(expr: SExpr): Conclusion = expr match {
    case error: SError => error.conclusion
    case other => fail("expected structured SError: " + other)
  }
  private def _route(model: KaleidoxModel, function: String, target: ModelGenerationTarget): SExpr = {
    val environment = cozy.Cozy.build(Array.empty[String]).environment
    val config = KaleidoxConfig.create(environment).setModeler(new Modeler(generationTarget = target))
    new Kaleidox(config, environment).createEngine().applyModelScript(
      model, SList(SAtom(function), SModel(model)))
  }
  private def _strings(source: String): Vector[String] =
    "\"(?:[^\"\\\\]|\\\\.)*\"".r.findAllIn(source).toVector.map(literal => Json.parse(literal).as[String])

  "Validation metadata typed source generation" should {
    "emit the exact public no-arg empty provider in the Test source tree" in {
      Given("an explicitly empty properties declaration in the ordinary source fixture")
      val model = _model(_source("{}"))
      val expected = """package org.example.validation
        |
        |import org.goldenport.cncf.validation.{ValidationCmlMappingV1, ValidationDeclarationProvider, ValidationMetadataV1}
        |import org.goldenport.cncf.workflow.WorkflowProtocolV1
        |
        |class EmptyDeclaration extends ValidationDeclarationProvider {
        |  override def declaration: ValidationMetadataV1.Declaration =
        |    ValidationCmlMappingV1.Properties(
        |      abiVersion = 1,
        |      purposes = None,
        |      features = Set(),
        |      expectedDuration = None,
        |      durationClass = None,
        |      executionRequirement = None,
        |      requiredSemantics = Set()
        |    ).declarationC.take
        |}
        |""".stripMargin
      When("the ordinary AST is projected to provider source")
      val files = _files(_realm(ValidationMetadataScalaGenerator.fromModel(model)))
      Then("the only output is the exact public concrete no-arg provider using the normalized mapping")
      files shouldBe Map(_empty_path -> expected)
    }

    "render every normalized property as an ordinary typed constructor" in {
      Given("the full ordinary CML fixture with explicit execution disposition and reasoning class")
      val model = _model(_fixture())
      When("the AST is projected to source without consulting CNCF semantic policy")
      val files = _files(_realm(ValidationMetadataScalaGenerator.fromModel(model)))
      val source = files(_focused_path)
      Then("every field references the existing public CNCF source ABI")
      files.keySet shouldBe Set(_focused_path, _empty_path)
      source should include("class FocusedDeclaration extends ValidationDeclarationProvider")
      source should include("abiVersion = 1")
      source should include("purposes = Some(Set(\"FOCUSED\"))")
      source should include("features = Set(ValidationMetadataV1.FeatureReference(\"component:domain\", \"feature-a\"))")
      source should include("expectedDuration = Some(ValidationCmlMappingV1.DurationValue(500L, ValidationCmlMappingV1.DurationUnit.MILLISECONDS))")
      source should include("durationClass = Some(\"NORMAL\")")
      source should include("WorkflowProtocolV1.ExecutionRequirement(capabilities = Vector(WorkflowProtocolV1.CapabilityRequirement(\"metadata-fixture\")), risk = WorkflowProtocolV1.RiskLevel(\"local\"), reasoning = WorkflowProtocolV1.ReasoningLevel.Standard, reviewRequired = false, disposition = Some(WorkflowProtocolV1.ExecutionDisposition(WorkflowProtocolV1.ExecutionPlacement.Delegated, WorkflowProtocolV1.ExecutionIndependence.Required, \"context-a\")), reasoningClass = Some(WorkflowProtocolV1.ReasoningClassReference(\"application.analysis\")))")
      source should include("requiredSemantics = Set(\"validation.metadata.v1\")")
      source should include(").declarationC.take")
    }

    "retain an explicit empty purpose set and delayed semantic rejection" in {
      Given("well-shaped semantic values which CNCF must validate when interpreting the provider")
      val model = _model(_source("{\"abiVersion\":99,\"purposes\":[],\"durationClass\":\"unknown\"," +
        "\"expectedDuration\":{\"amount\":-1,\"unit\":\"SECONDS\"},\"requiredSemantics\":[\"unknown\"]}"))
      When("Cozy emits typed source for the normalized properties")
      val source = _files(_realm(ValidationMetadataScalaGenerator.fromModel(model)))(_empty_path)
      Then("source preserves all values and delegates declaration validity to declarationC.take")
      source should include("abiVersion = 99")
      source should include("purposes = Some(Set())")
      source should include("durationClass = Some(\"unknown\")")
      source should include("DurationValue(-1L, ValidationCmlMappingV1.DurationUnit.SECONDS)")
      source should include("requiredSemantics = Set(\"unknown\")")
      source should include(").declarationC.take")
    }

    "render every duration unit and finite execution constructor" in {
      Given("all exact source duration units, reasoning levels, placements and independence labels")
      val units = Vector("NANOSECONDS", "MICROSECONDS", "MILLISECONDS", "SECONDS", "MINUTES", "HOURS", "DAYS")
      val levels = Vector("ROUTINE" -> "Routine", "STANDARD" -> "Standard", "DEEP" -> "Deep", "CRITICAL" -> "Critical")
      val placements = Vector("INLINE" -> "Inline", "DELEGATED" -> "Delegated")
      val independences = Vector("OPTIONAL" -> "Optional", "REQUIRED" -> "Required")
      val cases = for {
        unit <- units
        level <- levels
        placement <- placements
        independence <- independences
      } yield (unit, level, placement, independence)
      val models = cases.map { case (unit, level, placement, independence) =>
        _model(_source(Json.stringify(Json.obj(
          "expectedDuration" -> Json.obj("amount" -> 1, "unit" -> unit),
          "executionRequirement" -> Json.obj("capabilities" -> Json.arr(), "risk" -> "opaque",
            "reasoning" -> level._1, "reviewRequired" -> true,
            "disposition" -> Json.obj("placement" -> placement._1, "independence" -> independence._1,
              "originContextIdentity" -> "ctx"))))))
      }
      When("each finite source alternative is rendered")
      val sources = models.map(model => _files(_realm(ValidationMetadataScalaGenerator.fromModel(model)))(_empty_path))
      Then("the emitted finite references use the existing ABI constructor spellings")
      cases.zip(sources).foreach { case ((unit, level, placement, independence), source) =>
        source should include("ValidationCmlMappingV1.DurationUnit." + unit)
        source should include("WorkflowProtocolV1.ReasoningLevel." + level._2)
        source should include("WorkflowProtocolV1.ExecutionPlacement." + placement._2)
        source should include("WorkflowProtocolV1.ExecutionIndependence." + independence._2)
        source should include("reasoningClass = None")
      }
    }

    "roundtrip escaped text without executable interpolation" in {
      Given("generated padded Unicode, dollars, quotes, slashes and all control characters")
      val samples = Gen.zip(Gen.nonEmptyListOf(Gen.alphaNumChar), Gen.choose(1, 5)).map { case (text, count) =>
        " 日本語 e\u0301 😀 $value ${literal} \"" + text.mkString + "\" " + ("\\" * count) +
          "\n\r\t\b\f" + (0 to 31).map(_.toChar).mkString + "\u007f\u0085\u009f "
      }
      When("100 ordinary inputs are rendered twice and their ordinary literals are decoded")
      val result = Test.check(_parameters, Prop.forAllNoShrink(samples) { text =>
        val properties = Json.obj("purposes" -> Json.arr(text), "features" -> Json.arr(Json.obj("scope" -> text, "identity" -> text)),
          "durationClass" -> text, "requiredSemantics" -> Json.arr(text),
          "executionRequirement" -> Json.obj("capabilities" -> Json.arr(text), "risk" -> text, "reasoning" -> "STANDARD",
            "reviewRequired" -> false, "reasoningClass" -> text,
            "disposition" -> Json.obj("placement" -> "INLINE", "independence" -> "OPTIONAL", "originContextIdentity" -> text)))
        val model = _model(_source(Json.stringify(properties)))
        val first = _files(_realm(ValidationMetadataScalaGenerator.fromModel(model)))
        val second = _files(_realm(ValidationMetadataScalaGenerator.fromModel(model)))
        val source = first(_empty_path)
        Prop(_strings(source) == Vector.fill(9)(text) && first == second && !source.contains("s\"") &&
          !source.contains('\r') && source.contains("$value ${literal}")) :| Json.stringify(Json.obj(
          "input" -> text,
          "decodedLiterals" -> _strings(source),
          "length" -> _strings(source).length,
          "nineExactLiterals" -> (_strings(source) == Vector.fill(9)(text)),
          "deterministicOutput" -> (first == second),
          "noInterpolationPrefix" -> !source.contains("s\""),
          "noRawCarriageReturn" -> !source.contains('\r'),
          "literalDollarText" -> source.contains("$value ${literal}")))
      })
      Then("all nine textual positions retain exact content in deterministic ordinary Scala literals")
      withClue(result.toString) {
        result.passed shouldBe true
      }
    }

    "preserve provider declaration order and absent output" in {
      Given("generated provider families in reverse lexical order, including the absent family")
      val samples = Gen.choose(0, 12)
      When("100 ordinary documents are rendered")
      val result = Test.check(_parameters, Prop.forAll(samples) { count =>
        val providers = (0 until count).reverse.map(index => "example.Declaration" + index).toVector
        val source = "# OTHER\nignored = arbitrary\n\n# VALIDATION-METADATA\n\n" +
          providers.map(provider => _source("{}", provider)).mkString("\n")
        ValidationMetadataScalaGenerator.fromModel(_model(source)).exists {
          case None => providers.isEmpty
          case Some(realm) =>
            _entries(realm.backendRoot).map(_._1) == providers.map(provider => _root + provider.replace('.', '/') + ".scala")
        }
      })
      Then("file order follows declarations and an absent family has no Realm")
      result.passed shouldBe true
    }

    _routes.foreach { function =>
      function + " composes providers through normal Modeler generation" in {
        Given("the full metadata-only source from the ordinary fixture")
        val metadata = _model(_fixture().stripPrefix(_component))
        When("ordinary Composite and Workflow extraction interpret the separately owned metadata root")
        val composites = CompositeStateMachineCml.definitions(metadata)
        val workflows = CompositeStateMachineCml.workflowDefinitions(metadata)
        Then("both extractors return no definitions for validation metadata")
        composites shouldBe empty
        workflows shouldBe empty

        Given("the ordinary full fixture and the normal CNCF Modeler route")
        val model = _model(_fixture())
        val expected = _files(_realm(ValidationMetadataScalaGenerator.fromModel(model)))
        When("the ordinary Modeler Scala entry point interprets the source")
        val files = _files(_tree(_route(model, function, ModelGenerationTarget.Cncf)))
        Then("the provider Realm accompanies the normal generation output")
        files.filter { case (path, _) => path.startsWith(_root) } shouldBe expected
        files.keys should contain("target/cozy/statemachine-workflow-abi.json")
        files.keys should not contain "build.sbt"
        if (function == "modeler-scala")
          files.keys should contain("target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala")
      }

      function + " preserves ordinary output when metadata is absent" in {
        Given("a normal source model and variants with unrelated or empty metadata roots")
        val sources = Vector(_component, _component + "# OTHER\nignored = arbitrary\n",
          _component + "# VALIDATION-METADATA\n")
        val models = sources.map(_model)
        When("normal CNCF Modeler generation interprets each source")
        val files = models.map(model => _files(_tree(_route(model, function, ModelGenerationTarget.Cncf))))
        Then("absence leaves the existing generated output byte-for-byte unchanged")
        files.distinct.size shouldBe 1
        files.head.keys.exists(_.startsWith(_root)) shouldBe false
      }

      function + " rejects actual metadata for the Library target" in {
        Given("an explicit empty declaration, which remains a real runtime metadata provider")
        val model = _model(_source("{}"))
        When("the ordinary Library Modeler route interprets the declaration")
        val conclusion = _error(_route(model, function, ModelGenerationTarget.Library))
        Then("structured unsupported-runtime data identifies validation-metadata")
        conclusion shouldBe ValidationMetadataScalaGenerator.libraryConclusion
        conclusion.data shouldBe Some(Diagnostic("LIBRARY_GENERATION_UNSUPPORTED_RUNTIME", "validationMetadata",
          "category=validation-metadata is a CNCF runtime feature. Use --generation-target cncf."))
        conclusion.faults.nonEmpty shouldBe true
      }

      function + " returns a structured syntax failure before any output" in {
        Given("a valid first declaration followed by a malformed selected declaration")
        val model = _model(_component + _source("{}") + "\n" + _source("{\"purposes\":null}", "example.BadDeclaration"))
        When("the ordinary Modeler route extracts all selected source metadata")
        val conclusion = _error(_route(model, function, ModelGenerationTarget.Cncf))
        Then("the result is an argument diagnostic rather than a partial STree")
        conclusion.data shouldBe Some(Diagnostic("VALIDATION_METADATA_CML_INVALID",
          "validationMetadata[1].sections[0].properties.purposes", "expected JSON array"))
        conclusion.faults.nonEmpty shouldBe true
      }
    }

    "preserve the existing ScalaGenerator default option" in {
      Given("an existing caller that supplies no optional metadata Realm")
      val environment = cozy.Cozy.build(Array.empty[String]).environment
      val model = SimpleModel(Vector.empty)
      val generator = new ScalaGenerator(environment, model)
      When("the unchanged constructor call generates the ordinary root package")
      val files = _files(_tree(generator.generate(model.root)))
      Then("the default absence contributes no Test provider source")
      files.keys.exists(_.startsWith(_root)) shouldBe false
      files.keys should contain("target/cozy/statemachine-workflow-abi.json")
    }

    "preserve Library output when metadata is absent" in {
      Given("ordinary Library models with no declaration, including an empty metadata root")
      val models = Vector(_model(""), _model("# VALIDATION-METADATA\n"))
      When("the normal value Modeler route generates Library output")
      val files = models.map(model => _files(_tree(_route(model, "modeler-scala-value", ModelGenerationTarget.Library))))
      Then("an empty root leaves the existing Library output unchanged")
      files.distinct.size shouldBe 1
      files.head.keys.exists(_.startsWith(_root)) shouldBe false
    }

    "reject metadata passed directly to a Library ScalaGenerator" in {
      Given("a generated declaration Realm passed to the Library generator constructor")
      val environment = cozy.Cozy.build(Array.empty[String]).environment
      val model = SimpleModel(Vector.empty)
      val metadata = _realm(ValidationMetadataScalaGenerator.fromModel(_model(_source("{}"))))
      val generator = new ScalaGenerator(environment, model, generationTarget = ModelGenerationTarget.Library,
        validationMetadata = Some(metadata))
      When("the Library generator is invoked directly")
      val exception = intercept[ConclusionException](generator.generate(model.root))
      Then("the direct call retains the same structured unsupported-runtime category")
      exception.conclusion shouldBe ValidationMetadataScalaGenerator.libraryConclusion
      exception.conclusion.data shouldBe Some(Diagnostic("LIBRARY_GENERATION_UNSUPPORTED_RUNTIME", "validationMetadata",
        "category=validation-metadata is a CNCF runtime feature. Use --generation-target cncf."))
    }
  }
}
