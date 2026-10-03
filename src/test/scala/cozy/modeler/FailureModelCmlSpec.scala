package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.kaleidox.model.ServiceModel
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.Inspectors._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class FailureModelCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML Failure Model source admission" should {
    "retain authored local execution defaults and scoped refinements" which {
      "retain declaration tokens as opaque execution and failure identities" in {
        Given("both declaration tokens occupy execution and failure identity slots in all four combinations")
        val tokens = Vector("EXECUTION-MODEL", "FAILURE-MODEL")
        val sources = for {
          execution <- tokens
          failure <- tokens
        } yield (execution, failure, _model(_minimal.replace("LocalSingleWriter", execution).replace("malformed-input", failure)))
        When("the complete authored sources are normalized")
        val results = sources.map { case (execution, failure, model) => (execution, failure, _accepted(model).head) }
        Then("each selected execution and ordered failure preserves its exact authored identity and source path")
        results.foreach { case (execution, failure, component) =>
          component.executionModel.identity shouldBe execution
          component.executionModel.source.sectionPath shouldBe s"EXECUTION-MODEL/$execution"
          component.executionModel.defaults.map(_.identity) shouldBe Vector(failure)
          component.executionModel.defaults.map(_.scope) shouldBe Vector(FailureScope.InScope)
          component.executionModel.defaults.head.source.sectionPath shouldBe s"EXECUTION-MODEL/$execution/FAILURE-MODEL/$failure"
          component.effective.entries.map(_.identity) shouldBe Vector(failure)
          component.effective.entries.map(_.scope) shouldBe Vector(FailureScope.InScope)
        }
      }

      "retain declaration tokens as resolved component service and operation identities" in {
        Given("each declaration token occupies the component, Store service and save operation identity slots in the real fixture")
        val sources = Vector("EXECUTION-MODEL", "FAILURE-MODEL").map { identity =>
          identity -> _model(_fixture.replace("## LocalStore\n", s"## $identity\n")
            .replace("## Store\n", s"## $identity\n").replace("#### save\n", s"#### $identity\n"))
        }
        When("the original authored owners and resolved model are normalized")
        val results = sources.map { case (identity, model) => identity -> _accepted(model).head }
        Then("all three owners retain exact identities, ordered effective scopes and authored source paths")
        results.foreach { case (identity, component) =>
          val service = component.services.find(_.identity == identity).get
          val operation = service.operations.find(_.identity == identity).get
          component.identity shouldBe identity
          component.source.sectionPath shouldBe s"COMPONENT/$identity"
          service.identity shouldBe identity
          service.source.sectionPath shouldBe s"SERVICE/$identity"
          operation.identity shouldBe identity
          operation.source.sectionPath shouldBe s"SERVICE/$identity/OPERATION/$identity"
          _scopes(component.effective) shouldBe Vector("IN_SCOPE", "IN_SCOPE", "OUT_OF_SCOPE")
          _scopes(service.effective) shouldBe Vector("IN_SCOPE", "OUT_OF_SCOPE", "OUT_OF_SCOPE")
          _scopes(operation.effective) shouldBe Vector("IN_SCOPE", "OUT_OF_SCOPE", "IN_SCOPE")
          operation.effective.entries.map(_.identity) shouldBe Vector("malformed-input", "concurrent-write", "network-partition")
          operation.delta.entries.head.source.sectionPath shouldBe s"SERVICE/$identity/OPERATION/$identity/FAILURE-MODEL/network-partition"
        }
      }

      "load a real single-user single-writer file without inferring defaults from names" in {
        Given("the local fixture explicitly declares three defaults and selects LocalSingleWriter")
        val input = Paths.get("src/test/resources/modeler/failure-model.cml")
        val model = KaleidoxModel.load(KaleidoxConfig.default, input.toFile)
        When("the source contract is normalized")
        val component = _accepted(model).head
        Then("the selected execution retains exactly its authored ordered defaults")
        component.identity shouldBe "LocalStore"
        component.executionModel.identity shouldBe "LocalSingleWriter"
        component.executionModel.defaults.map(x => x.identity -> x.scope.name) shouldBe
          Vector("malformed-input" -> "IN_SCOPE", "concurrent-write" -> "OUT_OF_SCOPE", "network-partition" -> "OUT_OF_SCOPE")
      }

      "merge narrower deltas in either direction with complete winning and inherited origins" in {
        Given("component, Store and save explicitly refine the selected defaults")
        val model = _model(_fixture)
        When("each scoped effective contract is projected")
        val component = _accepted(model).head
        val service = component.services.find(_.identity == "Store").get
        val operation = service.operations.find(_.identity == "save").get
        Then("ordered effective vectors reflect only that owner's chain")
        _scopes(component.effective) shouldBe Vector("IN_SCOPE", "IN_SCOPE", "OUT_OF_SCOPE")
        _scopes(service.effective) shouldBe Vector("IN_SCOPE", "OUT_OF_SCOPE", "OUT_OF_SCOPE")
        _scopes(operation.effective) shouldBe Vector("IN_SCOPE", "OUT_OF_SCOPE", "IN_SCOPE")
        val concurrent = operation.effective.entries(1)
        concurrent.origins.map(_.scope.name) shouldBe Vector("OUT_OF_SCOPE", "IN_SCOPE", "OUT_OF_SCOPE")
        concurrent.winningOrigin shouldBe service.delta.entries.head.source
        val network = operation.effective.entries(2)
        network.origins.map(_.scope.name) shouldBe Vector("OUT_OF_SCOPE", "IN_SCOPE")
        network.winningOrigin shouldBe operation.delta.entries.head.source
        operation.effective.entries.head.origins shouldBe Vector(component.executionModel.defaults.head)
      }

      "include unmodified siblings with isolated immutable inheritance" in {
        Given("remove has no delta and Archive is an independent sibling of Store")
        val model = _model(_fixture)
        When("all resolved owners inherit their own parents")
        val component = _accepted(model).head
        val store = component.services.find(_.identity == "Store").get
        val remove = store.operations.find(_.identity == "remove").get
        val archive = component.services.find(_.identity == "Archive").get
        val changed = store.copy(delta = store.delta.copy(entries = Vector.empty))
        Then("absence is retained and no sibling observes Store or save's refinements")
        remove.delta.source shouldBe None
        _scopes(remove.effective) shouldBe _scopes(store.effective)
        archive.delta.source shouldBe None
        _scopes(archive.effective) shouldBe _scopes(component.effective)
        _scopes(archive.operations.head.effective) shouldBe _scopes(component.effective)
        changed.delta.entries shouldBe Vector.empty
        store.delta.entries.size shouldBe 1
        archive.effective.entries(1).origins.size shouldBe 2
      }

      "preserve source paths, actual lines, author order and no-location compatibility" in {
        Given("the same real file with and without Kaleidox source locations")
        val input = Paths.get("src/test/resources/modeler/failure-model.cml").toFile
        val located = KaleidoxModel.load(KaleidoxConfig.default, input)
        val unlocated = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input)
        When("both source models are normalized repeatedly")
        val component = _accepted(located).head
        val absent = _accepted(unlocated).head
        val operation = component.services.find(_.identity == "Store").get.operations.find(_.identity == "save").get
        Then("full original paths and positive actual lines accompany each authored source")
        component.source.sectionPath shouldBe "COMPONENT/LocalStore"
        component.executionSelectionSource.sectionPath shouldBe "COMPONENT/LocalStore/EXECUTION-MODEL"
        operation.delta.entries.head.source.sectionPath shouldBe "SERVICE/Store/OPERATION/save/FAILURE-MODEL/network-partition"
        val sources = Vector(component.source, component.executionSelectionSource, component.executionModel.source,
          component.delta.source.get, operation.source, operation.delta.source.get) ++ component.executionModel.defaults.map(_.source)
        all(sources.map(_.line.get)) should be > 0
        absent.source.line shouldBe None
        absent.executionModel.defaults.map(_.source.line) shouldBe Vector(None, None, None)
        _accepted(located) shouldBe Vector(component)
        component.effective.entries.map(_.identity) shouldBe component.executionModel.defaults.map(_.identity)
      }
    }

    "preserve absence and explicit selection" which {
      "leave legacy and unused execution declarations without emitted component contracts" in {
        Given("a legacy multi-component model and a valid unused execution definition")
        val legacy = _model("# COMPONENT\n## First\n### PACKAGE\nfirst\n## Second\n### PACKAGE\nsecond\n")
        val unused = _model(_defaults + _component)
        When("both are normalized")
        val results = Vector(FailureModelCml.normalize(legacy), FailureModelCml.normalize(unused))
        Then("neither invents a selected Failure Model")
        results shouldBe Vector(Right(Vector.empty), Right(Vector.empty))
      }

      "emit complete defaults for a selected component with no services or deltas" in {
        Given("one component explicitly selects defaults and declares no delta")
        val model = _model(_defaults + _component + _selector)
        When("the empty delta selector is normalized")
        val component = _accepted(model).head
        Then("all defaults are effective and absent owner deltas retain None")
        component.services shouldBe Vector.empty
        component.delta shouldBe FailureModelDelta("COMPONENT", "LocalStore", None, Vector.empty)
        component.effective.entries.map(_.scope) shouldBe component.executionModel.defaults.map(_.scope)
      }

      "retain nested promoted SERVICE ownership once at its original path" in {
        Given("a selected component directly contains a SERVICE section promoted by Kaleidox")
        val fixture = _fixture
        val start = fixture.indexOf("# SERVICE")
        val end = fixture.indexOf("# COMMAND")
        val nested = fixture.take(start) + fixture.substring(start, end).linesIterator
          .map(x => if (x.startsWith("#")) "##" + x else x).mkString("\n") + "\n" + fixture.substring(end)
        val model = _model(nested)
        When("the original tree and promoted divisions are normalized")
        val component = _accepted(model).head
        Then("each service appears once and retains the component-qualified source path")
        component.services.map(_.identity) shouldBe Vector("Store", "Archive")
        component.services.head.source.sectionPath shouldBe "COMPONENT/LocalStore/SERVICE/Store"
      }
    }

    "reject closed grammar violations as typed source issues" which {
      "reject an empty execution declaration before normalization or public builder construction" in {
        Given("an empty execution root alone, with a component, and with an explicit component selector")
        val models = Vector("", _component, _component + _selector).map(source => _model("# EXECUTION-MODEL\n\n" + source))
        When("source normalization and both public builders admit each declared source")
        val results = models.map { model =>
          val issues = _issues(FailureModelCml.normalize(model))
          val normal = intercept[RuntimeException](Modeler.ModelBuilder(model).build())
          val value = intercept[RuntimeException](new Modeler().buildValueModel(model))
          (issues, normal, value)
        }
        Then("the empty root produces a typed structure issue at its declaration path and both adapters reject that code")
        results.foreach { case (issues, normal, value) =>
          issues.map(_.code) should contain("FAILURE_MODEL_INVALID_STRUCTURE")
          issues.filter(_.code == "FAILURE_MODEL_INVALID_STRUCTURE").map(_.source.sectionPath) should contain("EXECUTION-MODEL")
          normal.getMessage should include("FAILURE_MODEL_INVALID_STRUCTURE")
          value.getMessage should include("FAILURE_MODEL_INVALID_STRUCTURE")
        }
      }

      val invalidcases = Vector(
        ("equal repeated scopes", _minimal.replace("scope = IN_SCOPE", "scope = IN_SCOPE\nscope = IN_SCOPE"), "FAILURE_MODEL_DUPLICATE"),
        ("opposite repeated scopes", _minimal.replace("scope = IN_SCOPE", "scope = IN_SCOPE\nscope = OUT_OF_SCOPE"), "FAILURE_MODEL_DUPLICATE"),
        ("equal duplicate failure entries", _minimal.replace("# COMPONENT", "#### malformed-input\nscope = IN_SCOPE\n\n# COMPONENT"), "FAILURE_MODEL_DUPLICATE"),
        ("opposite duplicate failure entries", _minimal.replace("# COMPONENT", "#### malformed-input\nscope = OUT_OF_SCOPE\n\n# COMPONENT"), "FAILURE_MODEL_DUPLICATE"),
        ("duplicate execution definitions", _defaults + _minimal, "FAILURE_MODEL_DUPLICATE"),
        ("unknown execution", _minimal.replace("model = LocalSingleWriter", "model = Unknown"), "FAILURE_MODEL_UNKNOWN_EXECUTION"),
        ("unknown failure", _minimal + "### FAILURE-MODEL\n#### unknown\nscope = IN_SCOPE\n", "FAILURE_MODEL_UNKNOWN_FAILURE"),
        ("empty enum", _minimal.replace("scope = IN_SCOPE", "scope ="), "FAILURE_MODEL_INVALID_SCOPE"),
        ("invalid enum", _minimal.replace("scope = IN_SCOPE", "scope = in_scope"), "FAILURE_MODEL_INVALID_SCOPE"),
        ("unknown field", _minimal.replace("scope = IN_SCOPE", "scope = IN_SCOPE\nretry = true"), "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("nested leaf", _minimal.replace("scope = IN_SCOPE", "scope = IN_SCOPE\n##### child\nvalue = x"), "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("repeated defaults containers", _minimal.replace("# COMPONENT", "### FAILURE-MODEL\n#### other\nscope = IN_SCOPE\n# COMPONENT"), "FAILURE_MODEL_DUPLICATE"),
        ("repeated selectors", _minimal + _selector, "FAILURE_MODEL_DUPLICATE"),
        ("repeated selector fields", _minimal.replace("model = LocalSingleWriter", "model = LocalSingleWriter\nmodel = LocalSingleWriter"), "FAILURE_MODEL_DUPLICATE"),
        ("unknown selector field", _minimal + "extra = x\n", "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("empty selector", _minimal.replace("model = LocalSingleWriter", ""), "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("empty defaults", _minimal.replace("#### malformed-input\nscope = IN_SCOPE\n", ""), "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("orphan refinement", _component + "### FAILURE-MODEL\n#### malformed-input\nscope = IN_SCOPE\n", "FAILURE_MODEL_INVALID_OWNER"),
        ("wrong-level container", _minimal + "# FAILURE-MODEL\n## malformed-input\nscope = IN_SCOPE\n", "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("buried container", _minimal + "### NOTE\n#### FAILURE-MODEL\n##### malformed-input\nscope = IN_SCOPE\n", "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("ambiguous components", _minimal + "## Other\n### PACKAGE\nother\n", "FAILURE_MODEL_INVALID_OWNER"),
        ("dotted failure identity", _minimal.replace("#### malformed-input", "#### malformed.input"), "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("slash execution identity", _minimal.replace("LocalSingleWriter", "Local/SingleWriter"), "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("surrounding identity whitespace", _minimal.replace("#### malformed-input", "####  malformed-input "), "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("control character identity", _minimal.replace("#### malformed-input", "#### malformed\tinput"), "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("repeated component delta containers", _minimal + "### FAILURE-MODEL\n#### malformed-input\nscope = IN_SCOPE\n### FAILURE-MODEL\n#### malformed-input\nscope = IN_SCOPE\n", "FAILURE_MODEL_DUPLICATE"),
        ("empty component delta", _minimal + "### FAILURE-MODEL\n", "FAILURE_MODEL_INVALID_STRUCTURE"),
        ("unused invalid execution", _defaults.replace("IN_SCOPE", "MAYBE") + _component, "FAILURE_MODEL_INVALID_SCOPE"),
        ("unknown operation refinement", _fixture.replace("###### network-partition", "###### unknown"), "FAILURE_MODEL_UNKNOWN_FAILURE"),
        ("repeated service delta containers", _fixture.replace("### OPERATION", "### FAILURE-MODEL\n#### concurrent-write\nscope = IN_SCOPE\n### OPERATION"), "FAILURE_MODEL_DUPLICATE"),
        ("repeated operation delta containers", _fixture.replace("#### remove", "##### FAILURE-MODEL\n###### network-partition\nscope = OUT_OF_SCOPE\n#### remove"), "FAILURE_MODEL_DUPLICATE")
      )
      invalidcases.foreach { case (label, source, code) =>
        s"reject $label without a partial contract" in {
          Given(s"an authored source containing $label")
          val model = _model(source)
          When("strict source admission interprets the declarations")
          val issues = _issues(FailureModelCml.normalize(model))
          Then("typed issues identify the rejected source and no successful vector is returned")
          issues.map(_.code) should contain(code)
          all(issues.map(_.source.sectionPath)) should not be empty
        }
      }

      "reject unresolved promoted service owners" in {
        Given("a nested SERVICE retained in the original tree without its resolved promoted division")
        val parsed = _model(_minimal + "### SERVICE\n#### Missing\n##### FAILURE-MODEL\n###### malformed-input\nscope = IN_SCOPE\n")
        val model = parsed.copy(divisions = parsed.divisions.filterNot(_.isInstanceOf[KaleidoxModel.ServiceDivision]))
        When("normalization checks the original declaration against resolved services")
        val issues = _issues(FailureModelCml.normalize(model))
        Then("unresolved owners are typed source failures")
        issues.map(_.code) should contain("FAILURE_MODEL_INVALID_OWNER")
      }

      "reject an operation owner absent from the resolved service" in {
        Given("the original Store declares remove but the resolved owning service omits that operation")
        val parsed = _model(_fixture)
        val model = parsed.copy(divisions = parsed.divisions.map {
          case division: KaleidoxModel.ServiceDivision =>
            new KaleidoxModel.ServiceDivision(division.section) {
              override def makeModel(config: KaleidoxConfig): ServiceModel = {
                val original = super.makeModel(config)
                val services = original.classes.values.toVector.map { service =>
                  val operations = service.operations.operations.values.toVector.filterNot(_.name == "remove")
                  ServiceModel(service.copy(operations = ServiceModel.ServiceClass.Operations(operations)))
                }
                services.foldLeft(ServiceModel.empty)(_ + _)
              }
            }
          case division => division
        })
        When("source owners are checked against the actual resolved owning service")
        val issues = _issues(FailureModelCml.normalize(model))
        Then("the unresolved operation fails admission as an owner issue")
        issues.map(_.code) should contain("FAILURE_MODEL_INVALID_OWNER")
        issues.map(_.message).mkString(" ") should include("remove")
      }

      "admit the same valid source through public normal and value routes" in {
        Given("the real fixture supplies valid COMMAND input and OperationResult output values")
        val model = _model(_fixture)
        When("public normal and value builders apply source admission")
        val normal = Modeler.ModelBuilder(model).build()
        val value = new Modeler().buildValueModel(model)
        Then("both routes produce their established model shapes")
        normal.elements.nonEmpty shouldBe true
        value.elements.nonEmpty shouldBe true
      }

      "enforce identical admission before normal and value builder construction" in {
        Given("a selected source containing an invalid scope token")
        val model = _model(_minimal.replace("IN_SCOPE", "MAYBE"))
        When("the public normal and value routes request a model")
        val normal = intercept[RuntimeException](Modeler.ModelBuilder(model).build())
        val value = intercept[RuntimeException](new Modeler().buildValueModel(model))
        Then("both established adapters reject the same typed source issue before a partial model exists")
        normal.getMessage should include("FAILURE_MODEL_INVALID_SCOPE")
        value.getMessage should include("FAILURE_MODEL_INVALID_SCOPE")
      }
    }
  }

  "Ordered Failure Model projection" should {
    "obey sampled opaque identifiers and scope deltas" which {
      "preserve determinism, narrower precedence, sibling isolation and punctuation" in {
        Given("sampled valid identities containing quote and backslash punctuation and both closed scope values")
        val samples = for {
          suffix <- Gen.chooseNum(1, 100000)
          defaults <- Gen.oneOf(FailureScope.InScope, FailureScope.OutOfScope)
          component <- Gen.oneOf(FailureScope.InScope, FailureScope.OutOfScope)
          service <- Gen.oneOf(FailureScope.InScope, FailureScope.OutOfScope)
          operation <- Gen.oneOf(FailureScope.InScope, FailureScope.OutOfScope)
        } yield ("failure\"quoted\"\\" + suffix, defaults, component, service, operation)
        When("ScalaCheck normalizes authored punctuation and projects independent sibling chains")
        val property = Prop.forAll(samples) { case (identity, defaultscope, componentscope, servicescope, operationscope) =>
          val source = _minimal.replace("malformed-input", identity).replace("scope = IN_SCOPE", s"scope = ${defaultscope.name}")
          val result = FailureModelCml.normalize(_model(source))
          result match {
            case Right(Vector(model)) =>
              val defaults = model.executionModel.defaults
              val deltas = Vector(componentscope, servicescope, operationscope).zipWithIndex.map { case (scope, index) =>
                FailureModelDelta(Vector("COMPONENT", "SERVICE", "OPERATION")(index), s"owner$index", Some(model.source),
                  Vector(FailureModelEntry(identity, scope, model.source)))
              }
              val leaf = FailureModel.effective("OPERATION", "leaf", model.source, defaults, deltas)
              val sibling = FailureModel.effective("SERVICE", "sibling", model.source, defaults, deltas.take(1))
              leaf == FailureModel.effective("OPERATION", "leaf", model.source, defaults, deltas) &&
                leaf.entries.head.identity == identity && leaf.entries.head.scope == operationscope &&
                leaf.entries.head.origins.map(_.scope) == Vector(defaultscope, componentscope, servicescope, operationscope) &&
                sibling.entries.head.scope == componentscope && sibling.entries.head.origins.size == 2 &&
                model.effective.entries.head.scope == defaultscope
            case _ => false
          }
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least one hundred samples satisfy the exact authored merge contract without invented failures")
        withClue(result.toString) {
          result.passed shouldBe true
        }
        result.succeeded should be >= 100
      }
    }
  }

  private def _fixture: String =
    new String(Files.readAllBytes(Paths.get("src/test/resources/modeler/failure-model.cml")), StandardCharsets.UTF_8)

  private def _defaults: String =
    "# EXECUTION-MODEL\n## LocalSingleWriter\n### FAILURE-MODEL\n#### malformed-input\nscope = IN_SCOPE\n"

  private def _component: String = "# COMPONENT\n## LocalStore\n### PACKAGE\nlocalstore\n"
  private def _selector: String = "### EXECUTION-MODEL\nmodel = LocalSingleWriter\n"
  private def _minimal: String = _defaults + _component + _selector

  private def _model(source: String): KaleidoxModel =
    KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, source)

  private def _accepted(model: KaleidoxModel): Vector[ComponentFailureModel] =
    FailureModelCml.normalize(model) match {
      case Right(components) => components
      case Left(issues) => fail(issues.mkString("; "))
    }

  private def _issues(result: Either[Vector[FailureModelIssue], Vector[ComponentFailureModel]]): Vector[FailureModelIssue] =
    result match {
      case Left(issues) => issues
      case Right(components) => fail(s"Expected source rejection, obtained $components")
    }

  private def _scopes(contract: EffectiveFailureContract): Vector[String] = contract.entries.map(_.scope.name)
}
