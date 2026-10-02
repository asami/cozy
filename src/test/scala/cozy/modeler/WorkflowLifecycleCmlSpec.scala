package cozy.modeler

import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkflowLifecycleCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _declared_metadata = """### LIFECYCLE
    |
    |deadline-millis = 60000
    |cancellation = COOPERATIVE
    |
    |### WAIT
    |
    |#### payment-timer
    |
    |state = Pending
    |delay-millis = 250
    |
    |#### completion-signal
    |
    |state = Complete
    |signal = payment.completed
    |
    |""".stripMargin

  "Workflow lifecycle source contract" should {
    "immutable declarations and compatibility" which {
      "retain the real fixture's combined policy, ordered triggers and source correlations" in {
        Given("the actual lifecycle CML fixture with API, SPI and invocation policy declarations")
        val source = _fixture

        When("public ModelBuilder admits the CML and its Workflow is normalized")
        val workflow = _workflow(source)

        Then("policy values, both triggers, order and exact source lines are retained")
        workflow.lifecyclePolicy shouldBe Some(WorkflowLifecyclePolicy(
          Some(60000L), Some(WorkflowCancellationMode.Cooperative), WorkflowSourceIdentity(Some(_line(source, "### LIFECYCLE")))
        ))
        WorkflowCancellationMode.Cooperative.canonicalValue shouldBe "COOPERATIVE"
        workflow.waits shouldBe Vector(
          WorkflowWaitDeclaration("payment-timer", "Pending", "Pending", WorkflowWaitTrigger.Timer(250L), WorkflowSourceIdentity(Some(_line(source, "#### payment-timer")))),
          WorkflowWaitDeclaration("completion-signal", "Complete", "Complete", WorkflowWaitTrigger.Signal("payment.completed"), WorkflowSourceIdentity(Some(_line(source, "#### completion-signal"))))
        )
        workflow.source.root.line shouldBe Some(_line(source, "# WORKFLOW"))
        workflow.source.definition.line shouldBe Some(_line(source, "## LifecycleWorkflow"))
        workflow.version shouldBe "workflow-lifecycle-v3"
        workflow.invocationPolicies.head.executionTimeoutMillis shouldBe Some(30000L)
        workflow.requiredOperations.head.capability shouldBe "capture-payment-capability"
        workflow.providedOperations.head.operation.name shouldBe "capturePayment"
      }

      "leave absent metadata and old constructor calls undeclared" in {
        Given("existing source without lifecycle metadata and the original constructor arguments")
        val source = _source()

        When("the source is admitted and a Workflow is constructed with its original five arguments")
        val workflow = _workflow(source)
        val legacy = WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations)
        val previous = WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations, workflow.invocationPolicies)

        Then("all constructor forms preserve absence without synthesizing lifecycle defaults")
        workflow.lifecyclePolicy shouldBe None
        workflow.waits shouldBe Vector.empty
        legacy.lifecyclePolicy shouldBe None
        legacy.waits shouldBe Vector.empty
        previous.lifecyclePolicy shouldBe None
        previous.waits shouldBe Vector.empty
        workflow.invocationPolicies.size shouldBe 1
      }

      Vector(
        ("deadline only", "deadline-millis = 1", Some(1L), None),
        ("cancellation only", "cancellation =  COOPERATIVE  ", None, Some(WorkflowCancellationMode.Cooperative)),
        ("both with normalized field aliases", "DEADLINE_millis = 00060000\nCANCELLATION = COOPERATIVE", Some(60000L), Some(WorkflowCancellationMode.Cooperative)),
        ("maximum deadline", s"deadline-millis = ${Long.MaxValue}", Some(Long.MaxValue), None)
      ).foreach { case (description, fields, deadline, cancellation) =>
        s"normalize $description without wait declarations" in {
          Given(s"a lifecycle-only declaration with $description")
          val source = _source(_lifecycle(fields))

          When("ModelBuilder admits the lifecycle metadata")
          val workflow = _workflow(source)

          Then("declared values are lossless and omitted fields remain absent")
          workflow.lifecyclePolicy.get.deadlineMillis shouldBe deadline
          workflow.lifecyclePolicy.get.cancellation shouldBe cancellation
          workflow.waits shouldBe Vector.empty
        }
      }

      Vector(
        ("zero timer", "delay-millis = 0", WorkflowWaitTrigger.Timer(0L)),
        ("leading zero timer", "DELAY_millis =  000250  ", WorkflowWaitTrigger.Timer(250L)),
        ("maximum timer", s"delay-millis = ${Long.MaxValue}", WorkflowWaitTrigger.Timer(Long.MaxValue)),
        ("opaque signal", "signal =  Payment Completed / external:v1  ", WorkflowWaitTrigger.Signal("Payment Completed / external:v1"))
      ).foreach { case (description, fields, trigger) =>
        s"normalize a wait-only $description and retain declared State spelling" in {
          Given(s"a wait-only $description targeting a normalized State alias")
          val source = _source(_wait(_entry("pending-wait", "STATE =  PEND_ing  \n" + fields)))

          When("ModelBuilder resolves the declared State against the existing CSM")
          val workflow = _workflow(source)

          Then("the declaration retains trimmed spelling, canonical identity and closed trigger")
          workflow.lifecyclePolicy shouldBe None
          workflow.waits.head.state shouldBe "PEND_ing"
          workflow.waits.head.stateIdentity shouldBe "Pending"
          workflow.waits.head.trigger shouldBe trigger
        }
      }

      "preserve CSM semantics and determinism across successive normalization" in {
        Given("the same Workflow with and without appended lifecycle declarations")
        val baseline = _source()
        val source = _fixture

        When("both sources are admitted and the lifecycle source is normalized twice")
        val original = _workflow(baseline)
        val first = _workflow(source)
        val second = _workflow(source)

        Then("lifecycle metadata does not expand or change the CSM or invocation policies")
        first shouldBe second
        first.compositeStateMachine shouldBe original.compositeStateMachine
        first.requiredOperations shouldBe original.requiredOperations
        first.providedOperations shouldBe original.providedOperations
        first.invocationPolicies shouldBe original.invocationPolicies
      }

      Vector("Wait", "LIFECYCLE").foreach { identity =>
        Vector(
          ("Workflow definition", (source: String) => source.replace("## LifecycleWorkflow", s"## $identity"), "workflow"),
          ("constituent StateMachine", (source: String) => source.replace("PaymentLifecycle", identity), "machine"),
          ("constituent State", (source: String) => source.replace("#### Awaiting", s"#### $identity").replace("payment.Awaiting", s"payment.$identity"), "constituentstate"),
          ("CSM constituent", (source: String) => source.replace("##### payment", s"##### $identity").replace("payment.subject", s"$identity.subject").replace("payment.Awaiting", s"$identity.Awaiting").replace("payment.Paid", s"$identity.Paid"), "constituent"),
          ("CSM State", (source: String) => source.replace("##### Pending", s"##### $identity").replace("state = Pending", s"state = $identity"), "state"),
          ("CSM Action", (source: String) => source.replace("##### capture-payment", s"##### $identity").replace("action = capture-payment", s"action = $identity"), "action"),
          ("CSM derivation", (source: String) => source.replace("##### pending", s"##### $identity"), "derivation"),
          ("SPI entry", (source: String) => source.replace("#### capture-payment-capability", s"#### $identity"), "spi"),
          ("API entry", (source: String) => source.replace("capturePayment", identity), "api"),
          ("invocation entry", (source: String) => source.replace("#### capture-control", s"#### $identity"), "invocation")
        ).foreach { case (description, transform, target) =>
          s"preserve opaque $description identity $identity in existing source" in {
            Given(s"metadata-free lifecycle source with an ordinary $description identity $identity")
            val input = transform(_source())

            When("ModelBuilder admits existing identities independently of new structural keys")
            val workflow = _workflow(input)
            val actual = target match {
              case "workflow" => workflow.identity
              case "machine" => workflow.compositeStateMachine.constituents.head.stateMachine.name
              case "constituentstate" => workflow.compositeStateMachine.constituents.head.states.head
              case "constituent" => workflow.compositeStateMachine.constituents.head.role
              case "state" => workflow.compositeStateMachine.states.head.name
              case "action" => workflow.compositeStateMachine.actions.head.identity
              case "derivation" => workflow.compositeStateMachine.derivations.head.identity
              case "spi" => workflow.requiredOperations.head.capability
              case "api" => workflow.providedOperations.head.operation.name
              case "invocation" => workflow.invocationPolicies.head.identity
            }

            Then("the existing identity is retained and no lifecycle defaults are created")
            actual shouldBe identity
            workflow.lifecyclePolicy shouldBe None
            workflow.waits shouldBe Vector.empty
          }
        }
      }
    }

    "closed scalar and entry grammar" which {
      Vector("", "0", "-1", "+1", "1.0", "1e3", "1ms", "\"1\"", "'1'", "9223372036854775808").foreach { value =>
        s"reject deadline value '$value'" in {
          Given("a deadline outside the unsigned decimal 1 through Long.MaxValue contract")
          val source = _source(_lifecycle(s"deadline-millis = $value"))

          When("ModelBuilder validates the numeric declaration")
          val error = intercept[RuntimeException] { _workflow(source) }

          Then("the deadline is rejected without truncation or defaults")
          error.getMessage should include("LIFECYCLE")
        }
      }

      Vector("", "-1", "+1", "1.0", "1e3", "1ms", "\"1\"", "'1'", "9223372036854775808").foreach { value =>
        s"reject delay value '$value'" in {
          Given("a timer delay outside the unsigned decimal 0 through Long.MaxValue contract")
          val source = _source(_wait(_entry("timer", s"state = Pending\ndelay-millis = $value")))

          When("ModelBuilder validates the timer declaration")
          val error = intercept[RuntimeException] { _workflow(source) }

          Then("the delay is rejected without wrapping or defaults")
          error.getMessage should include("WAIT")
        }
      }

      Vector("", "cooperative", "Cooperative", "CO_OPERATIVE", "IMMEDIATE", "\"COOPERATIVE\"").foreach { value =>
        s"reject cancellation value '$value'" in {
          Given("a cancellation value that is not the exact trimmed canonical token")
          val source = _source(_lifecycle(s"cancellation = $value"))

          When("ModelBuilder interprets cancellation semantics")
          val error = intercept[RuntimeException] { _workflow(source) }

          Then("the unsupported mode is rejected")
          error.getMessage should include("LIFECYCLE")
        }
      }

      Vector(
        "empty lifecycle" -> _lifecycle(""),
        "duplicate deadline aliases" -> _lifecycle("deadline-millis = 1\nDEADLINE_millis = 1"),
        "duplicate cancellation aliases" -> _lifecycle("cancellation = COOPERATIVE\nCANCELLATION = COOPERATIVE"),
        "unknown lifecycle field" -> _lifecycle("deadline-millis = 1\ntimeout = 2"),
        "delay on lifecycle" -> _lifecycle("delay-millis = 1"),
        "nested lifecycle field" -> _lifecycle("#### deadline-millis\n1"),
        "empty wait section" -> _wait(),
        "direct wait field" -> _wait("delay-millis = 1"),
        "empty wait entry" -> _wait(_entry("timer", "")),
        "missing state" -> _wait(_entry("timer", "delay-millis = 1")),
        "empty state" -> _wait(_entry("timer", "state =\ndelay-millis = 1")),
        "unknown state" -> _wait(_entry("timer", "state = Unknown\ndelay-millis = 1")),
        "constituent state instead of CSM State" -> _wait(_entry("timer", "state = Awaiting\ndelay-millis = 1")),
        "missing trigger" -> _wait(_entry("timer", "state = Pending")),
        "both triggers" -> _wait(_entry("timer", "state = Pending\ndelay-millis = 1\nsignal = ready")),
        "empty signal" -> _wait(_entry("timer", "state = Pending\nsignal =")),
        "duplicate state aliases" -> _wait(_entry("timer", "state = Pending\nSTATE = Pending\ndelay-millis = 1")),
        "duplicate delay aliases" -> _wait(_entry("timer", "state = Pending\ndelay-millis = 1\nDELAY_millis = 1")),
        "duplicate signal aliases" -> _wait(_entry("timer", "state = Pending\nsignal = ready\nSIGNAL = ready")),
        "unknown wait field" -> _wait(_entry("timer", "state = Pending\ndelay-millis = 1\nunknown = x")),
        "deadline on wait entry" -> _wait(_entry("timer", "state = Pending\ndelay-millis = 1\ndeadline-millis = 1")),
        "nested wait content" -> _wait(_entry("timer", "state = Pending\ndelay-millis = 1\n##### nested\nsignal = ready")),
        "duplicate wait identity aliases" -> _wait(_entry("timer-one", "state = Pending\ndelay-millis = 1"), _entry("TIMER_one", "state = Complete\nsignal = ready")),
        "duplicate canonical State aliases" -> _wait(_entry("first", "state = Pending\ndelay-millis = 1"), _entry("second", "state = PEND_ing\nsignal = ready")),
        "duplicate lifecycle sections" -> (_lifecycle("deadline-millis = 1") + _lifecycle("cancellation = COOPERATIVE")),
        "duplicate wait sections" -> (_wait(_entry("first", "state = Pending\ndelay-millis = 1")) + _wait(_entry("second", "state = Complete\nsignal = ready")))
      ).foreach { case (description, metadata) =>
        s"reject $description" in {
          Given(s"real CML containing $description")
          val source = _source(metadata)

          When("ModelBuilder validates the closed lifecycle and wait grammar")
          val error = intercept[RuntimeException] { _workflow(source) }

          Then("the malformed declaration fails explicitly")
          error.getMessage should (include("LIFECYCLE") or include("WAIT"))
        }
      }

      "reject ambiguous normalized CSM State identities before normalizing lifecycle metadata" in {
        Given("two CSM States with equivalent names and an otherwise valid wait")
        val source = _source(_wait(_entry("timer", "state = Pending\ndelay-millis = 1"))).replace("##### Complete", "##### PEND_ing")

        When("ModelBuilder validates existing CSM identities")
        val error = intercept[RuntimeException] { _workflow(source) }

        Then("ambiguous States are rejected by the existing CSM contract")
        error.getMessage should include("STATE")
        error.getMessage should include("must be unique")
      }
    }

    "structural ownership" which {
      Vector("LIFECYCLE", "WAIT").foreach { sectionname =>
        s"require a CSM even for an empty $sectionname" in {
          Given(s"a Workflow definition declaring $sectionname without a CSM")
          val source = s"# WORKFLOW\n\n## MissingStructure\n\nversion = v1\n\n### $sectionname\n"

          When("ModelBuilder classifies the declaration as structural")
          val error = intercept[RuntimeException] { _workflow(source) }

          Then("metadata cannot disappear into a narrative-only Workflow")
          error.getMessage should include("requires a COMPOSITE-STATEMACHINE structural section")
        }

        Vector(
          "document root" -> ("# WORKFLOW", s"# $sectionname\n\n# WORKFLOW"),
          "Workflow root" -> ("## LifecycleWorkflow", s"## $sectionname\n\n## LifecycleWorkflow"),
          "CSM child" -> ("#### STATE", s"#### $sectionname\n\n#### STATE"),
          "CSM descendant" -> ("##### Pending", s"##### Pending\n\n###### $sectionname"),
          "SPI descendant" -> ("action = capture-payment", s"action = capture-payment\n\n##### $sectionname"),
          "API descendant" -> ("operation = OrderService.capturePayment", s"operation = OrderService.capturePayment\n\n##### $sectionname"),
          "invocation descendant" -> ("execution-timeout-millis = 30000", s"execution-timeout-millis = 30000\n\n##### $sectionname"),
          "other Workflow section" -> ("### INVOCATION-POLICY", s"### OTHER\n\n#### $sectionname\n\n### INVOCATION-POLICY")
        ).foreach { case (description, (before, after)) =>
          s"reject $sectionname at $description" in {
            Given(s"a $sectionname section misplaced at $description")
            val source = _source().replace(before, after)

            When("ModelBuilder validates declaration ownership")
            val error = intercept[RuntimeException] { _workflow(source) }

            Then("the misplaced section is not silently ignored")
            error.getMessage should include(sectionname)
          }
        }
      }

      Vector("deadline-millis", "cancellation", "delay-millis").foreach { fieldname =>
        Vector(
          "Workflow root" -> "# WORKFLOW",
          "Workflow definition" -> "version = workflow-lifecycle-v3",
          "CSM section" -> "### COMPOSITE-STATEMACHINE",
          "CSM State" -> "##### Pending",
          "CSM Action" -> "##### capture-payment",
          "SPI section" -> "### REQUIRED-OPERATION",
          "SPI entry" -> "#### capture-payment-capability",
          "API section" -> "### OPERATION",
          "API entry" -> "operation = OrderService.capturePayment",
          "invocation entry" -> "#### capture-control",
          "non-Workflow root" -> "# VALUE",
          "non-Workflow descendant" -> "## OrderService"
        ).foreach { case (description, anchor) =>
          s"reject reserved $fieldname at $description" in {
            Given(s"a reserved direct $fieldname field outside its owned location at $description")
            val source = _source().replace(anchor, anchor + s"\n$fieldname = 1\n")

            When("ModelBuilder validates reserved field ownership recursively")
            val error = intercept[RuntimeException] { _workflow(source) }

            Then("the reserved field cannot be discarded by another model root or subsection")
            error.getMessage should include(fieldname)
          }
        }
      }

      "reject lifecycle metadata on a standalone CSM root" in {
        Given("a standalone CSM carrying a reserved direct deadline field")
        val source = _source().replace("# WORKFLOW", "# COMPOSITE-STATEMACHINE").replace("version = workflow-lifecycle-v3", "deadline-millis = 1")

        When("ModelBuilder validates CSM ownership")
        val error = intercept[RuntimeException] { _workflow(source) }

        Then("a CSM cannot adopt Workflow lifecycle policy")
        error.getMessage should include("deadline-millis")
      }

      Vector("provider", "runtime", "script", "retry", "continuation", "invocation-binding").foreach { vocabulary =>
        Vector(
          "lifecycle field" -> _lifecycle(s"deadline-millis = 1\n$vocabulary = external"),
          "wait field" -> _wait(_entry("timer", s"state = Pending\ndelay-millis = 1\n$vocabulary = external")),
          "Workflow heading" -> s"### $vocabulary\n"
        ).foreach { case (description, metadata) =>
          s"reject execution vocabulary $vocabulary as $description" in {
            Given(s"execution-binding vocabulary $vocabulary in a $description")
            val source = _source(metadata)

            When("ModelBuilder validates the source-only contract")
            val error = intercept[RuntimeException] { _workflow(source) }

            Then("execution grammar cannot enter through lifecycle metadata")
            error.getMessage should (include("LIFECYCLE") or include("WAIT") or include("execution-binding vocabulary"))
          }
        }
      }
    }
  }

  private def _fixture: String = {
    val stream = getClass.getResourceAsStream("/modeler/workflow-lifecycle-policy.cml")
    val source = scala.io.Source.fromInputStream(stream, "UTF-8")
    try source.mkString finally source.close()
  }

  private def _source(metadata: String = ""): String = _fixture.replace(_declared_metadata, metadata)

  private def _lifecycle(fields: String): String = s"### LIFECYCLE\n\n$fields\n\n"

  private def _wait(entries: String*): String = "### WAIT\n\n" + entries.mkString("\n") + "\n"

  private def _entry(identity: String, fields: String): String = s"#### $identity\n\n$fields\n"

  private def _line(source: String, heading: String): Int = source.linesIterator.toVector.indexOf(heading) + 1

  private def _workflow(source: String): WorkflowDefinition = {
    val model = KaleidoxModel.parse(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty)
      throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    Modeler.ModelBuilder(model)
    CompositeStateMachineCml.workflowDefinitions(model).head
  }
}
