package cozy.modeler

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep.  7, 2026
 *  version Sep.  7, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeStateMachineGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "CML Composite StateMachine generation" should {
    "normalize typed Composite StateMachine definitions" which {
      "retain ordered configuration and non-deduplicated action provenance" in {
        Given("a valid multi-constituent Composite StateMachine with repeated logical action use")
        val model = _model(_source())

        When("the CSM-07 typed normalization boundary is projected")
        val definitions = CompositeStateMachineCml.definitions(model)

        Then("the definition retains its versioned identity, source identity, and role order")
        definitions should have size 1
        val definition = definitions.head
        definition.abiVersion shouldBe CompositeStateMachineDefinition.ABI_VERSION
        definition.abiVersion shouldBe "cozy.cml.composite-statemachine.v1"
        definition.identity shouldBe "OrderProgress"
        definition.name shouldBe "OrderProgress"
        definition.source shouldBe CompositeStateMachineSourceIdentity(None)
        definition.constituents.map(_.role) shouldBe Vector("payment", "fulfillment")
        definition.constituents.map(_.stateMachine.name) shouldBe Vector("PaymentLifecycle", "FulfillmentLifecycle")
        definition.constituents.head.subject shouldBe Some(CompositeStateMachineSubject("payment", "PaymentCommand"))
        definition.initialConfiguration.map(_.bindings.map(_.role)) shouldBe Some(Vector("payment", "fulfillment"))
        definition.derivations.head.configuration.bindings.map(_.role) shouldBe Vector("payment", "fulfillment")

        And("the distinct constituent and derived occurrences retain typed action provenance")
        definition.actions.map(_.identity) shouldBe Vector("capture-payment")
        definition.constituentActions should have size 2
        definition.constituentActions.map(_.action.identity) shouldBe Vector("capture-payment", "capture-payment")
        definition.constituentActions.map(_.placement) shouldBe Vector("exit", "transition")
        definition.derivedActions.map(_.derivedTransition) shouldBe Vector("completed")
        definition.derivedActions.map(_.action.identity) shouldBe Vector("capture-payment")
      }
    }

    "generate the standalone typed Scala ABI" which {
      "emit deterministic Composite StateMachine source through both public Scala routes" in {
        Given("temporary metadata-bearing and legacy CML sources with empty output for each Scala route")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-input/composite-statemachine-generation.cml")
        val legacyinput = base.resolve("target/test-input/composite-statemachine-generation-legacy.cml")
        val normalout = base.resolve("target/test-generated/composite-statemachine-generation-normal")
        val valueout = base.resolve("target/test-generated/composite-statemachine-generation-value")
        val legacynormalout = base.resolve("target/test-generated/composite-statemachine-generation-legacy-normal")
        val legacyvalueout = base.resolve("target/test-generated/composite-statemachine-generation-legacy-value")
        delete_recursively(normalout)
        delete_recursively(valueout)
        delete_recursively(legacynormalout)
        delete_recursively(legacyvalueout)
        write_file(input, _source())
        write_file(legacyinput, _legacy_source())

        When("the public component Scala generation path is invoked twice")
        cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", normalout.toString))
        val normalfirst = tree_snapshot(normalout)
        cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", normalout.toString))
        val normalsecond = tree_snapshot(normalout)

        And("the public value-model Scala generation path is invoked twice")
        cozy.Cozy.main(Array("modeler-scala-value", input.toString, "--save", valueout.toString))
        val valuefirst = tree_snapshot(valueout)
        cozy.Cozy.main(Array("modeler-scala-value", input.toString, "--save", valueout.toString))
        val valuesecond = tree_snapshot(valueout)

        And("the public Scala routes generate a legacy source without Action producer metadata")
        cozy.Cozy.main(Array("modeler-scala", legacyinput.toString, "--save", legacynormalout.toString))
        cozy.Cozy.main(Array("modeler-scala-value", legacyinput.toString, "--save", legacyvalueout.toString))

        Then("both routes emit byte-identical fixed ABI/bootstrap sources and typed definitions in normalized source order")
        val normalroot = normalout.resolve("target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine")
        val valueroot = valueout.resolve("target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine")
        val normalabi = normalroot.resolve("CompositeStateMachineAbi.scala")
        val valueabi = valueroot.resolve("CompositeStateMachineAbi.scala")
        val normalbootstrap = normalroot.resolve("CompositeStateMachineBootstrap.scala")
        val valuebootstrap = valueroot.resolve("CompositeStateMachineBootstrap.scala")
        val normaldefinition = normalroot.resolve("OrderProgressCompositeStateMachine1.scala")
        val valuedefinition = valueroot.resolve("OrderProgressCompositeStateMachine1.scala")
        val normalprojection = normalout.resolve(CompositeStateMachineProjectionMetadata.metadataPath)
        val valueprojection = valueout.resolve(CompositeStateMachineProjectionMetadata.metadataPath)
        val producerroot = "target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/actionproducer"
        val normalproducerroot = normalout.resolve(producerroot)
        val valueproducerroot = valueout.resolve(producerroot)
        val normalproducerabi = normalproducerroot.resolve("CompositeStateMachineActionProducerMetadataAbi.scala")
        val valueproducerabi = valueproducerroot.resolve("CompositeStateMachineActionProducerMetadataAbi.scala")
        val normalproducerbootstrap = normalproducerroot.resolve("CompositeStateMachineActionProducerMetadataBootstrap.scala")
        val valueproducerbootstrap = valueproducerroot.resolve("CompositeStateMachineActionProducerMetadataBootstrap.scala")
        val normalproducerdefinition = normalproducerroot.resolve("OrderProgressActionProducerMetadata1.scala")
        val valueproducerdefinition = valueproducerroot.resolve("OrderProgressActionProducerMetadata1.scala")
        val normalproducermetadata = normalout.resolve(CompositeStateMachineActionProducerMetadata.metadataPath)
        val valueproducermetadata = valueout.resolve(CompositeStateMachineActionProducerMetadata.metadataPath)
        val actionprogramroot = "target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/actionprogram"
        val normalactionprogramroot = normalout.resolve(actionprogramroot)
        val valueactionprogramroot = valueout.resolve(actionprogramroot)
        val normalactionprogramabi = normalactionprogramroot.resolve("CompositeStateMachineActionProgramAbi.scala")
        val valueactionprogramabi = valueactionprogramroot.resolve("CompositeStateMachineActionProgramAbi.scala")
        val normalactionprogramcompiler = normalactionprogramroot.resolve("LogicalActionCompiler.scala")
        val valueactionprogramcompiler = valueactionprogramroot.resolve("LogicalActionCompiler.scala")
        val normalactionprogrambootstrap = normalactionprogramroot.resolve("CompositeStateMachineActionProgramBootstrap.scala")
        val valueactionprogrambootstrap = valueactionprogramroot.resolve("CompositeStateMachineActionProgramBootstrap.scala")
        val normalactionprogramdefinition = normalactionprogramroot.resolve("OrderProgressActionProgram1.scala")
        val valueactionprogramdefinition = valueactionprogramroot.resolve("OrderProgressActionProgram1.scala")
        val normalactionprogramjson = normalout.resolve(CompositeStateMachineActionProgram.metadataPath)
        val valueactionprogramjson = valueout.resolve(CompositeStateMachineActionProgram.metadataPath)
        Files.exists(normalabi) shouldBe true
        Files.exists(valueabi) shouldBe true
        Files.exists(normalbootstrap) shouldBe true
        Files.exists(valuebootstrap) shouldBe true
        Files.exists(normaldefinition) shouldBe true
        Files.exists(valuedefinition) shouldBe true
        Files.exists(normalprojection) shouldBe true
        Files.exists(valueprojection) shouldBe true
        Files.exists(normalproducerabi) shouldBe true
        Files.exists(valueproducerabi) shouldBe true
        Files.exists(normalproducerbootstrap) shouldBe true
        Files.exists(valueproducerbootstrap) shouldBe true
        Files.exists(normalproducerdefinition) shouldBe true
        Files.exists(valueproducerdefinition) shouldBe true
        Files.exists(normalproducermetadata) shouldBe true
        Files.exists(valueproducermetadata) shouldBe true
        Files.exists(normalactionprogramabi) shouldBe true
        Files.exists(valueactionprogramabi) shouldBe true
        Files.exists(normalactionprogramcompiler) shouldBe true
        Files.exists(valueactionprogramcompiler) shouldBe true
        Files.exists(normalactionprogrambootstrap) shouldBe true
        Files.exists(valueactionprogrambootstrap) shouldBe true
        Files.exists(normalactionprogramdefinition) shouldBe true
        Files.exists(valueactionprogramdefinition) shouldBe true
        Files.exists(normalactionprogramjson) shouldBe true
        Files.exists(valueactionprogramjson) shouldBe true
        Files.readString(normalabi) should include ("package domain.composite.statemachine")
        Files.readString(normalabi) should include ("val VERSION: String = \"cozy.cml.composite-statemachine.v1\"")
        Files.readString(normalabi) should include ("final case class ConstituentBinding")
        Files.readString(normalabi) should include ("final case class DerivedAction")
        Files.readString(valueabi) should include ("val VERSION: String = \"cozy.cml.composite-statemachine.v1\"")
        Files.readString(normalabi) shouldBe Files.readString(valueabi)
        val expectedbootstrap = """package domain.composite.statemachine
          |
          |object CompositeStateMachineBootstrap {
          |  val bootstrapAbiVersion: String = "cozy.cml.composite-statemachine-bootstrap.v1"
          |  val definitions: Vector[CompositeStateMachineAbi.Definition] = Vector(OrderProgressCompositeStateMachine1.definition)
          |}
          |""".stripMargin
        Files.readString(normalbootstrap) shouldBe expectedbootstrap
        Files.readString(valuebootstrap) shouldBe expectedbootstrap
        Files.readString(normalbootstrap) shouldBe Files.readString(valuebootstrap)
        Files.readString(normaldefinition) should include ("abiVersion = \"cozy.cml.composite-statemachine.v1\"")
        Files.readString(valuedefinition) should include ("abiVersion = \"cozy.cml.composite-statemachine.v1\"")
        Files.readString(normaldefinition) shouldBe Files.readString(valuedefinition)
        Files.readString(valuedefinition) should include ("object OrderProgressCompositeStateMachine1")
        Files.readString(valuedefinition) should include ("val definition: CompositeStateMachineAbi.Definition")
        Files.readString(valuedefinition) should include ("ConstituentBinding(\"payment\"")
        Files.readString(valuedefinition) should include ("ConstituentBinding(\"fulfillment\"")
        Files.readString(valuedefinition) should include ("Derivation(\"pending\"")
        Files.readString(valuedefinition) should include ("LogicalAction(\"capture-payment\", \"OPERATION\"")
        Files.readString(valuedefinition) should include ("ConstituentAction(\"payment-captured-exit\"")
        Files.readString(valuedefinition) should include ("DerivedAction(\"completed\"")

        And("the fixed Cozy projection document is present at its exact path with byte-identical route output")
        Files.readAllBytes(normalprojection).toVector shouldBe Files.readAllBytes(valueprojection).toVector
        Files.readString(normalprojection) should include ("\"schemaVersion\":\"cozy.cml.composite-statemachine-projection.v1\"")
        Files.readString(normalprojection) should include ("\"constituentActions\"")
        Files.readString(normalprojection) should include ("\"derivedActions\"")

        And("the separate producer surface joins existing definition and Action identities with normalized metadata")
        Files.readString(normalproducerabi) should include ("package domain.composite.statemachine.actionproducer")
        Files.readString(normalproducerabi) should include ("val VERSION: String = \"cozy.cml.action-producer-metadata.v1\"")
        Files.readString(normalproducerabi) should include ("final case class Idempotency(required: Boolean, keyRef: Option[String])")
        Files.readString(normalproducerabi) shouldBe Files.readString(valueproducerabi)
        val expectedproducerbootstrap = """package domain.composite.statemachine.actionproducer
          |
          |object CompositeStateMachineActionProducerMetadataBootstrap {
          |  val schemaVersion: String = "cozy.cml.action-producer-metadata.v1"
          |  val metadata: Vector[CompositeStateMachineActionProducerMetadataAbi.ActionMetadata] = Vector(OrderProgressActionProducerMetadata1.metadata).flatten
          |}
          |""".stripMargin
        Files.readString(normalproducerbootstrap) shouldBe expectedproducerbootstrap
        Files.readString(valueproducerbootstrap) shouldBe expectedproducerbootstrap
        Files.readString(normalproducerdefinition) shouldBe Files.readString(valueproducerdefinition)
        Files.readString(normalproducerdefinition) should include ("definitionIdentity = \"OrderProgress\"")
        Files.readString(normalproducerdefinition) should include ("actionId = \"capture-payment\"")
        Files.readString(normalproducerdefinition) should include ("effectClass = \"EXTERNAL\"")
        Files.readString(normalproducerdefinition) should include ("transactionRequirement = \"OUTSIDE_UNIT_OF_WORK\"")
        Files.readString(normalproducerdefinition) should include ("Idempotency(required = true, keyRef = Some(\"payment-command\"))")
        Files.readString(normalproducerdefinition) should include ("compensationHandlerRef = Some(\"cancel-payment\")")
        Files.readString(normalproducermetadata) shouldBe Files.readString(valueproducermetadata)
        Files.readString(normalproducermetadata) should include ("\"schemaVersion\":\"cozy.cml.action-producer-metadata.v1\"")
        Files.readString(normalproducermetadata) should include ("\"definitionIdentity\":\"OrderProgress\"")
        Files.readString(normalproducermetadata) should include ("\"actionId\":\"capture-payment\"")
        Files.readString(normalproducermetadata) should include ("\"effectClass\":\"EXTERNAL\"")
        Files.readString(normalproducermetadata) should include ("\"transactionRequirement\":\"OUTSIDE_UNIT_OF_WORK\"")
        Files.readString(normalproducermetadata) should include ("\"idempotency\":{\"required\":true,\"keyRef\":\"payment-command\"}")
        Files.readString(normalproducermetadata) should include ("\"compensationHandlerRef\":\"cancel-payment\"")

        And("the logical-action compiler surface is additive, byte-identical through both routes, and keeps occurrence descriptors separate from existing v1 output")
        Files.readString(normalactionprogramabi) should include ("package domain.composite.statemachine.actionprogram")
        Files.readString(normalactionprogramabi) should include ("val VERSION: String = \"cozy.cml.logical-action-program.v1\"")
        Files.readString(normalactionprogramabi) should include ("final case class CompiledOccurrence")
        Files.readString(normalactionprogramabi) shouldBe Files.readString(valueactionprogramabi)
        Files.readString(normalactionprogramcompiler) should include ("type ExecProgram[A] = org.goldenport.cncf.unitofwork.ExecProgram[A]")
        Files.readString(normalactionprogramcompiler) should include ("final case class MissingMetadata")
        Files.readString(normalactionprogramcompiler) should include ("final case class IncompatibleBinding")
        Files.readString(normalactionprogramcompiler) should include ("Free.pure[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit](())")
        Files.readString(normalactionprogramcompiler) shouldBe Files.readString(valueactionprogramcompiler)
        val expectedactionprogrambootstrap = """package domain.composite.statemachine.actionprogram
          |
          |object CompositeStateMachineActionProgramBootstrap {
          |  val schemaVersion: String = "cozy.cml.logical-action-program.v1"
          |  val definitions: Vector[CompositeStateMachineActionProgramAbi.Definition] = Vector(OrderProgressActionProgram1.definition)
          |}
          |""".stripMargin
        Files.readString(normalactionprogrambootstrap) shouldBe expectedactionprogrambootstrap
        Files.readString(valueactionprogrambootstrap) shouldBe expectedactionprogrambootstrap
        Files.readString(normalactionprogramdefinition) shouldBe Files.readString(valueactionprogramdefinition)
        Files.readString(normalactionprogramdefinition) should include ("occurrenceId = \"constituent:payment-captured-exit:1\"")
        Files.readString(normalactionprogramdefinition) should include ("occurrenceId = \"constituent:payment-captured-transition:2\"")
        Files.readString(normalactionprogramdefinition) should include ("occurrenceId = \"derived:completed:3\"")
        Files.readString(normalactionprogramdefinition) should include ("metadata = Some(CompositeStateMachineActionProgramAbi.ActionMetadata(")
        Files.readString(normalactionprogramjson) shouldBe Files.readString(valueactionprogramjson)
        Files.readString(normalactionprogramjson) should include ("\"schemaVersion\":\"cozy.cml.logical-action-program.v1\"")
        Files.readString(normalactionprogramjson) should include ("\"occurrenceId\":\"constituent:payment-captured-exit:1\"")
        Files.readString(normalactionprogramjson) should include ("\"occurrenceId\":\"derived:completed:3\"")

        And("legacy Actions emit no producer-metadata records while CSM v1 output remains unchanged")
        val legacynormalroot = legacynormalout.resolve("target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine")
        val legacyvalueroot = legacyvalueout.resolve("target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine")
        val legacynormalproducerroot = legacynormalout.resolve(producerroot)
        val legacyvalueproducerroot = legacyvalueout.resolve(producerroot)
        val legacynormalmetadata = legacynormalout.resolve(CompositeStateMachineActionProducerMetadata.metadataPath)
        val legacyvaluemetadata = legacyvalueout.resolve(CompositeStateMachineActionProducerMetadata.metadataPath)
        val legacynormalactionprogramroot = legacynormalout.resolve(actionprogramroot)
        val legacyvalueactionprogramroot = legacyvalueout.resolve(actionprogramroot)
        val legacynormalactionprogramdefinition = legacynormalactionprogramroot.resolve("OrderProgressActionProgram1.scala")
        val legacyvalueactionprogramdefinition = legacyvalueactionprogramroot.resolve("OrderProgressActionProgram1.scala")
        val legacynormalactionprogramjson = legacynormalout.resolve(CompositeStateMachineActionProgram.metadataPath)
        val legacyvalueactionprogramjson = legacyvalueout.resolve(CompositeStateMachineActionProgram.metadataPath)
        Files.readString(legacynormalroot.resolve("CompositeStateMachineAbi.scala")) shouldBe Files.readString(normalabi)
        Files.readString(legacyvalueroot.resolve("CompositeStateMachineAbi.scala")) shouldBe Files.readString(valueabi)
        Files.readString(legacynormalroot.resolve("CompositeStateMachineBootstrap.scala")) shouldBe Files.readString(normalbootstrap)
        Files.readString(legacyvalueroot.resolve("CompositeStateMachineBootstrap.scala")) shouldBe Files.readString(valuebootstrap)
        val legacynormaldefinition = Files.readString(legacynormalroot.resolve("OrderProgressCompositeStateMachine1.scala"))
        val legacyvaluedefinition = Files.readString(legacyvalueroot.resolve("OrderProgressCompositeStateMachine1.scala"))
        legacynormaldefinition should include ("abiVersion = \"cozy.cml.composite-statemachine.v1\"")
        legacyvaluedefinition should include ("abiVersion = \"cozy.cml.composite-statemachine.v1\"")
        legacynormaldefinition should include ("object OrderProgressCompositeStateMachine1")
        legacyvaluedefinition should include ("object OrderProgressCompositeStateMachine1")
        legacynormaldefinition should include ("val definition: CompositeStateMachineAbi.Definition")
        legacyvaluedefinition should include ("val definition: CompositeStateMachineAbi.Definition")
        legacynormaldefinition should not include ("CompositeStateMachineActionProducerMetadata")
        legacyvaluedefinition should not include ("CompositeStateMachineActionProducerMetadata")
        val legacynormalprojection = Files.readString(legacynormalout.resolve(CompositeStateMachineProjectionMetadata.metadataPath))
        val legacyvalueprojection = Files.readString(legacyvalueout.resolve(CompositeStateMachineProjectionMetadata.metadataPath))
        legacynormalprojection should include ("\"schemaVersion\":\"cozy.cml.composite-statemachine-projection.v1\"")
        legacyvalueprojection should include ("\"schemaVersion\":\"cozy.cml.composite-statemachine-projection.v1\"")
        legacynormalprojection should include ("\"definitions\"")
        legacyvalueprojection should include ("\"definitions\"")
        legacynormalprojection should not include ("cozy.cml.action-producer-metadata.v1")
        legacyvalueprojection should not include ("cozy.cml.action-producer-metadata.v1")
        Files.exists(legacynormalproducerroot.resolve("OrderProgressActionProducerMetadata1.scala")) shouldBe false
        Files.exists(legacyvalueproducerroot.resolve("OrderProgressActionProducerMetadata1.scala")) shouldBe false
        Files.readString(legacynormalmetadata) shouldBe "{\"schemaVersion\":\"cozy.cml.action-producer-metadata.v1\",\"definitions\":[]}"
        Files.readString(legacyvaluemetadata) shouldBe Files.readString(legacynormalmetadata)
        Files.readString(legacynormalproducerroot.resolve("CompositeStateMachineActionProducerMetadataBootstrap.scala")) should include ("= Vector.empty")
        Files.readString(legacyvalueproducerroot.resolve("CompositeStateMachineActionProducerMetadataBootstrap.scala")) should include ("= Vector.empty")

        And("legacy CML keeps its existing v1 output while the new compiler descriptor records missing metadata for producer-side rejection")
        Files.exists(legacynormalactionprogramdefinition) shouldBe true
        Files.exists(legacyvalueactionprogramdefinition) shouldBe true
        Files.readString(legacynormalactionprogramdefinition) shouldBe Files.readString(legacyvalueactionprogramdefinition)
        Files.readString(legacynormalactionprogramdefinition) should include ("metadata = None")
        Files.readString(legacynormalactionprogramroot.resolve("LogicalActionCompiler.scala")) should include ("MissingMetadata")
        Files.readString(legacynormalactionprogramjson) shouldBe Files.readString(legacyvalueactionprogramjson)
        Files.readString(legacynormalactionprogramjson) should include ("\"metadata\":null")

        And("the component route remains additive while the value route has no component facade")
        Files.exists(normalout.resolve("target/scala-3.3.8/src_managed/main/scala/domain/CompositeSampleComponent.scala")) shouldBe true
        Files.exists(valueout.resolve("target/scala-3.3.8/src_managed/main/scala/domain/CompositeSampleComponent.scala")) shouldBe false

        And("each public route remains deterministic")
        normalfirst shouldBe normalsecond
        valuefirst shouldBe valuesecond
      }

      "emit a stable typed empty bootstrap source without Composite StateMachine definitions" in {
        Given("an empty normalized Composite StateMachine definition collection")
        val definitions = Vector.empty[CompositeStateMachineDefinition]

        When("the typed Scala generator emits the shared Composite StateMachine sources repeatedly")
        val generated = CompositeStateMachineScalaGenerator.generate(definitions)
        val regenerated = CompositeStateMachineScalaGenerator.generate(definitions)

        Then("the fixed ABI and bootstrap are present with a typed empty definition vector")
        val generatedabi = generated
          .get("target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/CompositeStateMachineAbi.scala")
          .collect { case value: org.goldenport.realm.Realm.StringData => value.string }
          .getOrElse(fail("Missing generated Composite StateMachine ABI"))
        val generatedbootstrap = generated
          .get("target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/CompositeStateMachineBootstrap.scala")
          .collect { case value: org.goldenport.realm.Realm.StringData => value.string }
          .getOrElse(fail("Missing generated Composite StateMachine bootstrap"))
        val regeneratedbootstrap = regenerated
          .get("target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/CompositeStateMachineBootstrap.scala")
          .collect { case value: org.goldenport.realm.Realm.StringData => value.string }
          .getOrElse(fail("Missing regenerated Composite StateMachine bootstrap"))
        generatedabi should include ("val VERSION: String = \"cozy.cml.composite-statemachine.v1\"")
        val expectedbootstrap = """package domain.composite.statemachine
          |
          |object CompositeStateMachineBootstrap {
          |  val bootstrapAbiVersion: String = "cozy.cml.composite-statemachine-bootstrap.v1"
          |  val definitions: Vector[CompositeStateMachineAbi.Definition] = Vector.empty
          |}
          |""".stripMargin
        generatedbootstrap shouldBe expectedbootstrap
        regeneratedbootstrap shouldBe expectedbootstrap

        And("no per-definition source is emitted")
        generated.get("target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/CompositeStateMachine1.scala").isEmpty shouldBe true
      }

      "use locale-neutral definition names through the typed Scala generator boundary" in {
        Given("a valid normalized Composite StateMachine definition whose identity begins with i")
        val model = _model(_source().replace("## OrderProgress", "## istanbul"))
        val definitions = CompositeStateMachineCml.definitions(model)
        definitions should have size 1
        val definition = definitions.head
        definition.identity shouldBe "istanbul"
        val originallocale = Locale.getDefault

        When("the typed Scala generator runs under the Turkish default locale")
        val generated = _with_default_locale(Locale.forLanguageTag("tr-TR")) {
          CompositeStateMachineScalaGenerator.generate(definitions)
        }

        Then("the generated definition filename and object name use the locale-neutral I prefix")
        val generateddefinition = generated
          .get("target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/IstanbulCompositeStateMachine1.scala")
          .collect { case value: org.goldenport.realm.Realm.StringData => value.string }
          .getOrElse(fail("Missing generated Istanbul Composite StateMachine definition"))
        generateddefinition should include ("object IstanbulCompositeStateMachine1")

        And("the JVM default locale is restored after generation")
        Locale.getDefault shouldBe originallocale
      }
    }
  }

  private def _with_default_locale[A](locale: Locale)(body: => A): A = {
    val originallocale = Locale.getDefault
    Locale.setDefault(locale)
    try body
    finally Locale.setDefault(originallocale)
  }

  private def _model(source: String): KaleidoxModel = {
    val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty)
      throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    model
  }

  private def _source(): String =
    """# COMPONENT
      |
      |## CompositeSample
      |
      |### PACKAGE
      |
      |domain
      |
      |# VALUE
      |
      |## PaymentCommand
      |- input-kind :: COMMAND
      |
      |### ATTRIBUTE
      || name | type   | multiplicity |
      ||------+--------+--------------|
      || id   | string | 1            |
      |
      |## PaymentResult
      |
      |### EXTENDS
      |
      |OperationResult
      |
      |# COMPOSITE-STATEMACHINE
      |
      |## OrderProgress
      |
      |### CONSTITUENT
      |
      |#### payment
      |
      |state-machine = PaymentLifecycle
      |subject = payment
      |subject-type = PaymentCommand
      |
      |#### fulfillment
      |
      |state-machine = FulfillmentLifecycle
      |
      |### STATE
      |
      |#### Pending
      |
      |#### Complete
      |
      |### DERIVATION
      |
      |#### pending
      |
      |state = Pending
      |when = fulfillment.Waiting, payment.Awaiting
      |
      |#### complete
      |
      |state = Complete
      |when = fulfillment.Shipped, payment.Paid
      |
      |#### payment-paid
      |
      |state = Pending
      |when = fulfillment.Waiting, payment.Paid
      |
      |#### fulfillment-shipped
      |
      |state = Pending
      |when = fulfillment.Shipped, payment.Awaiting
      |
      |### INITIAL
      |
      |fulfillment.Waiting, payment.Awaiting
      |
      |### ACTION
      |
      |#### capture-payment
      |
      |kind = OPERATION
      |operation = capturePayment
      |input = payment.subject
      |effect = EXTERNAL
      |transaction = OUTSIDE_UNIT_OF_WORK
      |idempotency = REQUIRED
      |idempotency-key = payment-command
      |compensation-handler = cancel-payment
      |
      |### CONSTITUENT-ACTION
      |
      |#### payment-captured-exit
      |
      |role = payment
      |from = Awaiting
      |to = Paid
      |on = captured
      |placement = exit
      |action = capture-payment
      |
      |#### payment-captured-transition
      |
      |role = payment
      |from = Awaiting
      |to = Paid
      |on = captured
      |placement = transition
      |action = capture-payment
      |
      |### DERIVED-ACTION
      |
      |#### completed
      |
      |from = Pending
      |to = Complete
      |action = capture-payment
      |
      |# STATE-MACHINE
      |
      |## PaymentLifecycle
      |
      |### State
      |
      |#### Awaiting
      |
      |##### Transition
      |
      |to = Paid
      |on = captured
      |
      |#### Paid
      |
      |## FulfillmentLifecycle
      |
      |### State
      |
      |#### Waiting
      |
      |##### Transition
      |
      |to = Shipped
      |on = shipped
      |
      |#### Shipped
      |
      |# SERVICE
      |
      |## OrderService
      |
      |### OPERATION
      |
      |#### capturePayment
      |
      |##### TYPE
      |
      |COMMAND
      |
      |##### INPUT
      |
      |###### TYPE
      |
      |PaymentCommand
      |
      |##### OUTPUT
      |
      |###### TYPE
      |
      |PaymentResult
      |""".stripMargin

  private def _legacy_source(): String = _source().replace(_action_metadata_source, "")

  private val _action_metadata_source =
    """effect = EXTERNAL
      |transaction = OUTSIDE_UNIT_OF_WORK
      |idempotency = REQUIRED
      |idempotency-key = payment-command
      |compensation-handler = cancel-payment
      |""".stripMargin
}
