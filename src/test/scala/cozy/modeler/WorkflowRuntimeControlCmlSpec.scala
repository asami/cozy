package cozy.modeler

import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 */
final class WorkflowRuntimeControlCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _retry_fields = "maximum-attempts = 3\nfixed-retry-delay-millis = 250"

  "Workflow invocation policy source contract" should {
    "retain typed Action retry and timeout declarations in source order" in {
      Given("two declared Actions with ordered policies and real source locations")
      val source = _workflow_source(_section(
        _entry("refund-control", "action = refund-payment", "execution-timeout-millis = 50"),
        _entry("capture-control", "action = CAPTURE_payment", _retry_fields + "\nexecution-timeout-millis = 30000")
      ))
      val model = _model(source)

      When("ModelBuilder admits the real CML and its typed Workflow is normalized")
      Modeler.ModelBuilder(model)
      val workflow = CompositeStateMachineCml.workflowDefinitions(model).head

      Then("order, declared targets, canonical Actions, values and entry locations are retained")
      workflow.invocationPolicies.map(_.identity) shouldBe Vector("refund-control", "capture-control")
      val policy = workflow.invocationPolicies(1)
      policy.target shouldBe WorkflowInvocationTarget.Action("CAPTURE_payment")
      policy.actionIdentity shouldBe "capture-payment"
      policy.retry shouldBe Some(WorkflowRetryPolicy(3, 250L))
      policy.executionTimeoutMillis shouldBe Some(30000L)
      policy.source.line shouldBe Some(source.linesIterator.toVector.indexOf("#### capture-control") + 1)
      workflow.invocationPolicies.head.source.line.get should be < policy.source.line.get
      workflow.source.root.line shouldBe Some(1)
      workflow.source.definition.line shouldBe Some(3)
    }

    "resolve a Participant capability while preserving its declared target" in {
      Given("a policy targeting a normalized alias of an existing required capability")
      val model = _model(_workflow_source(_section(_entry(
        "capture-control", "participant = CAPTURE_payment_CAPABILITY", _retry_fields
      ))))

      When("ModelBuilder admits the source and the policy is normalized")
      Modeler.ModelBuilder(model)
      val workflow = CompositeStateMachineCml.workflowDefinitions(model).head
      val policy = workflow.invocationPolicies.head

      Then("the Participant resolves through the existing capability mapping to the canonical Action")
      policy.target shouldBe WorkflowInvocationTarget.Participant("CAPTURE_payment_CAPABILITY")
      policy.actionIdentity shouldBe workflow.requiredOperations.head.action.identity
      policy.actionIdentity shouldBe "capture-payment"
      policy.executionTimeoutMillis shouldBe None
    }

    Vector(
      ("retry only", _retry_fields, Some(WorkflowRetryPolicy(3, 250L)), None),
      ("timeout only", "execution-timeout-millis = 1", None, Some(1L)),
      ("one attempt and zero delay", "maximum-attempts = 1\nfixed-retry-delay-millis = 0", Some(WorkflowRetryPolicy(1, 0L)), None),
      ("maximum integer ranges", s"maximum-attempts = ${Int.MaxValue}\nfixed-retry-delay-millis = ${Long.MaxValue}\nexecution-timeout-millis = ${Long.MaxValue}", Some(WorkflowRetryPolicy(Int.MaxValue, Long.MaxValue)), Some(Long.MaxValue)),
      ("trimmed leading zeros", "maximum-attempts =  0003  \nfixed-retry-delay-millis =  0000  \nexecution-timeout-millis =  0001  ", Some(WorkflowRetryPolicy(3, 0L)), Some(1L))
    ).foreach { case (description, fields, retry, timeout) =>
      s"normalize $description without inventing omitted values" in {
        Given(s"real CML with $description")
        val model = _model(_workflow_source(_section(_entry("capture-control", "action = capture-payment", fields))))

        When("ModelBuilder admits the declaration and the Workflow is normalized")
        Modeler.ModelBuilder(model)
        val policy = CompositeStateMachineCml.workflowDefinitions(model).head.invocationPolicies.head

        Then("the numeric meaning and absent declarations are represented exactly")
        policy.retry shouldBe retry
        policy.executionTimeoutMillis shouldBe timeout
      }
    }

    "keep metadata-free legacy Workflows without invocation policies" in {
      Given("the established Workflow source with no policy section")
      val model = _model(_workflow_source())

      When("ModelBuilder admits the source and the Workflow is normalized")
      Modeler.ModelBuilder(model)
      val workflow = CompositeStateMachineCml.workflowDefinitions(model).head

      Then("no attempts, delay or timeout defaults are synthesized")
      workflow.invocationPolicies shouldBe Vector.empty
      workflow.requiredOperations.map(_.capability) shouldBe Vector("capture-payment-capability")
    }

    "normalize successively without changing CSM states, transitions or Actions" in {
      Given("the same Workflow with and without one invocation declaration")
      val baseline = _model(_workflow_source())
      val model = _model(_workflow_source(_section(_entry("capture-control", "action = capture-payment", _retry_fields))))

      When("both sources pass ModelBuilder and the policy source is normalized twice")
      Modeler.ModelBuilder(baseline)
      Modeler.ModelBuilder(model)
      val original = CompositeStateMachineCml.workflowDefinitions(baseline).head
      val first = CompositeStateMachineCml.workflowDefinitions(model).head
      val second = CompositeStateMachineCml.workflowDefinitions(model).head

      Then("normalization is deterministic and all CSM semantics remain identical")
      first shouldBe second
      first.compositeStateMachine shouldBe original.compositeStateMachine
      first.compositeStateMachine.states shouldBe original.compositeStateMachine.states
      first.compositeStateMachine.constituentActions shouldBe original.compositeStateMachine.constituentActions
      first.compositeStateMachine.derivedActions shouldBe original.compositeStateMachine.derivedActions
      first.compositeStateMachine.actions shouldBe original.compositeStateMachine.actions
    }

    Vector(
      "unknown Action" -> "action = unknown-action",
      "unknown Participant" -> "participant = unknown-capability",
      "provider as Participant" -> "participant = local-provider",
      "missing target" -> "",
      "empty Action" -> "action =",
      "empty Participant" -> "participant =",
      "both targets" -> "action = capture-payment\nparticipant = capture-payment-capability",
      "duplicate Action target" -> "action = capture-payment\nACTION = capture-payment",
      "duplicate Participant target" -> "participant = capture-payment-capability\nPARTICIPANT = capture-payment-capability"
    ).foreach { case (description, target) =>
      s"reject $description" in {
        Given(s"an invocation entry with $description")
        val model = _model(_workflow_source(_section(_entry("capture-control", target, _retry_fields))))

        When("ModelBuilder validates the source boundary")
        val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

        Then("the invalid target is rejected as policy syntax")
        error.getMessage should include("INVOCATION-POLICY")
      }
    }

    "reject case and separator equivalent policy identities" in {
      Given("two otherwise valid policies with equivalent entry identities and distinct Action targets")
      val model = _model(_workflow_source(_section(
        _entry("capture-control", "action = capture-payment", _retry_fields),
        _entry("CAPTURE_control", "action = refund-payment", _retry_fields)
      )))

      When("ModelBuilder validates identity uniqueness")
      val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

      Then("the policy identity is rejected without declaration precedence")
      error.getMessage should include("identity")
      error.getMessage should include("must be unique")
    }

    Vector("action = CAPTURE_payment", "participant = capture-payment-capability").foreach { target =>
      s"reject a duplicate canonical Action through $target" in {
        Given("two unique policy identities resolving to the same logical Action")
        val model = _model(_workflow_source(_section(
          _entry("first-control", "action = capture-payment", _retry_fields),
          _entry("second-control", target, "execution-timeout-millis = 100")
        )))

        When("ModelBuilder resolves both policy targets")
        val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

        Then("the shared resolved Action is rejected without an override rule")
        error.getMessage should include("resolved Action")
        error.getMessage should include("must be unique")
      }
    }

    val invalidnumbers = Vector("-1", "+1", "1.5", "1e3", "1ms", "", "one")
    Vector("maximum-attempts", "fixed-retry-delay-millis", "execution-timeout-millis").foreach { field =>
      val outofrange = if (field == "maximum-attempts") Vector("0", "2147483648")
        else if (field == "execution-timeout-millis") Vector("0", "9223372036854775808")
        else Vector("9223372036854775808")
      (invalidnumbers ++ outofrange).foreach { value =>
        s"reject $field value '$value'" in {
          Given(s"real CML with invalid $field numeric syntax or range '$value'")
          val validfields = _retry_fields + "\nexecution-timeout-millis = 30000"
          val fields = validfields.replace(
            s"$field = " + (if (field == "maximum-attempts") "3" else if (field == "fixed-retry-delay-millis") "250" else "30000"),
            s"$field = $value"
          )
          val model = _model(_workflow_source(_section(_entry("capture-control", "action = capture-payment", fields))))

          When("ModelBuilder validates the numeric declaration")
          val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

          Then("the value is rejected instead of truncation, wraparound or defaults")
          error.getMessage should include(field)
          error.getMessage should include("INVOCATION-POLICY")
        }
      }
    }

    Vector(
      "attempts without delay" -> "maximum-attempts = 3",
      "delay without attempts" -> "fixed-retry-delay-millis = 250",
      "attempts and timeout without delay" -> "maximum-attempts = 3\nexecution-timeout-millis = 100",
      "delay and timeout without attempts" -> "fixed-retry-delay-millis = 250\nexecution-timeout-millis = 100",
      "target only" -> "",
      "repeated maximum attempts" -> (_retry_fields + "\nMAXIMUM_ATTEMPTS = 3"),
      "repeated delay" -> (_retry_fields + "\nFIXED_RETRY_DELAY_MILLIS = 250"),
      "repeated timeout" -> "execution-timeout-millis = 100\nEXECUTION_TIMEOUT_MILLIS = 100",
      "unknown field" -> (_retry_fields + "\nbackoff = exponential"),
      "runtime field" -> (_retry_fields + "\nprovider = local"),
      "nested policy content" -> (_retry_fields + "\n\n##### timeout\nexecution-timeout-millis = 100")
    ).foreach { case (description, fields) =>
      s"reject $description" in {
        Given(s"an invocation policy declaring $description")
        val model = _model(_workflow_source(_section(_entry("capture-control", "action = capture-payment", fields))))

        When("ModelBuilder validates the policy entry shape")
        val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

        Then("the malformed entry is rejected as policy syntax")
        error.getMessage should include("INVOCATION-POLICY")
      }
    }

    Vector(
      "empty section" -> "### INVOCATION-POLICY\n",
      "empty identity" -> _section(_entry("", "action = capture-payment", _retry_fields)),
      "section-level fields" -> ("### INVOCATION-POLICY\nexecution-timeout-millis = 100\n" + _entry("capture-control", "action = capture-payment", _retry_fields)),
      "duplicate section" -> (_section(_entry("capture-control", "action = capture-payment", _retry_fields)) + "\n### INVOCATION_POLICY\n")
    ).foreach { case (description, section) =>
      s"reject a policy $description" in {
        Given(s"a Workflow with a policy $description")
        val model = _model(_workflow_source(section))

        When("ModelBuilder classifies the structural declaration")
        val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

        Then("malformed policy structure is rejected")
        error.getMessage should include("INVOCATION-POLICY")
      }
    }

    "reject policy structure without a Composite StateMachine" in {
      Given("a Workflow definition containing only invocation policy structure")
      val model = _model("# WORKFLOW\n\n## OrderProgress\n\nversion = workflow-v1\n\n" +
        _section(_entry("capture-control", "action = capture-payment", _retry_fields)))

      When("ModelBuilder classifies the Workflow")
      val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

      Then("the policy is structural and cannot disappear into narrative")
      error.getMessage should include("requires a COMPOSITE-STATEMACHINE structural section")
    }

    Vector(
      "Workflow root" -> "# WORKFLOW\n\n## INVOCATION-POLICY\n",
      "top level" -> "# INVOCATION-POLICY\n"
    ).foreach { case (description, source) =>
      s"reject an empty policy section at $description" in {
        Given(s"an empty policy section placed at $description instead of a Workflow definition")
        val model = _model(source)

        When("ModelBuilder classifies structural policy ownership")
        val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

        Then("the misplaced section cannot disappear as narrative")
        error.getMessage should include("INVOCATION-POLICY")
      }
    }

    Vector(
      "CSM child" -> ("#### STATE", "#### INVOCATION-POLICY\n\n##### capture-control\n\naction = capture-payment\nexecution-timeout-millis = 100\n\n#### STATE"),
      "CSM Action child" -> ("### REQUIRED-OPERATION", "###### INVOCATION-POLICY\n\n####### capture-control\n\naction = capture-payment\nexecution-timeout-millis = 100\n\n### REQUIRED-OPERATION"),
      "required-operation child" -> ("#### capture-payment-capability", "#### INVOCATION-POLICY\n\n##### capture-control\n\naction = capture-payment\nexecution-timeout-millis = 100\n\n#### capture-payment-capability"),
      "provided-operation child" -> ("#### capturePayment\noperation = OrderService.capturePayment", "#### INVOCATION-POLICY\n\n##### capture-control\n\naction = capture-payment\nexecution-timeout-millis = 100\n\n#### capturePayment\noperation = OrderService.capturePayment")
    ).foreach { case (description, (before, after)) =>
      s"reject policy section placement at a $description" in {
        Given(s"policy structure misplaced at a $description")
        val model = _model(_workflow_source().replace(before, after))

        When("ModelBuilder validates the source ownership boundary")
        val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

        Then("policy sections must belong directly to the Workflow definition")
        error.getMessage should include("INVOCATION-POLICY")
      }
    }

    Vector("participant", "maximum-attempts", "fixed-retry-delay-millis", "execution-timeout-millis").foreach { field =>
      Vector(
        "Workflow" -> "version = workflow-v1",
        "required section" -> "### REQUIRED-OPERATION",
        "required entry" -> "#### capture-payment-capability",
        "provided section" -> "### OPERATION",
        "provided entry" -> "#### capturePayment\noperation = OrderService.capturePayment",
        "Action" -> "##### capture-payment"
      ).foreach { case (description, anchor) =>
        s"reject direct $field at $description" in {
          Given(s"a $description with a misplaced direct $field value")
          val model = _model(_workflow_source().replace(anchor, anchor + s"\n$field = 1"))

          When("ModelBuilder validates the declaration location")
          val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

          Then("the direct policy field cannot be silently ignored")
          error.getMessage should include("INVOCATION-POLICY")
        }
      }
    }

    Vector(
      "Workflow" -> "version = workflow-v1",
      "required section" -> "### REQUIRED-OPERATION",
      "provided section" -> "### OPERATION",
      "provided entry" -> "#### capturePayment\noperation = OrderService.capturePayment",
      "Action" -> "##### capture-payment"
    ).foreach { case (description, anchor) =>
      s"reject a direct Action policy target at $description" in {
        Given(s"a $description declaring an invocation target outside the policy section")
        val model = _model(_workflow_source().replace(anchor, anchor + "\naction = capture-payment"))

        When("ModelBuilder validates the target declaration placement")
        val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

        Then("the target cannot silently introduce invocation metadata")
        error.getMessage should (include("INVOCATION-POLICY") or include("does not admit 'action'"))
      }
    }

    Vector("action = judge-payment", "participant = capture-payment-capability").foreach { target =>
      s"admit Judgment invocation through $target" in {
        Given("an existing valid Judgment and Admission pair with a policy on the Judgment invocation")
        val model = _model(_judgment_source(_section(_entry("judgment-control", target, _retry_fields))))

        When("ModelBuilder admits the candidate and local admission semantics")
        Modeler.ModelBuilder(model)
        val workflow = CompositeStateMachineCml.workflowDefinitions(model).head

        Then("the invocation policy resolves to the Judgment while Admission remains deterministic")
        workflow.invocationPolicies.head.actionIdentity shouldBe "judge-payment"
        workflow.compositeStateMachine.actions.find(_.identity == "admit-payment").get.kind shouldBe "ADMISSION"
      }
    }

    "reject an invocation policy on deterministic Admission" in {
      Given("a valid Judgment and Admission pair with a policy targeting Admission")
      val model = _model(_judgment_source(_section(_entry("admission-control", "action = admit-payment", _retry_fields))))

      When("ModelBuilder resolves the policy target kind")
      val error = intercept[RuntimeException] { Modeler.ModelBuilder(model) }

      Then("Admission cannot receive retry or timeout metadata")
      error.getMessage should include("ADMISSION is deterministic local semantics")
    }
  }

  private def _model(source: String): KaleidoxModel = {
    val model = KaleidoxModel.parse(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty)
      throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    model
  }

  private def _entry(identity: String, target: String, fields: String): String =
    s"#### $identity\n\n$target\n$fields\n"

  private def _section(entries: String*): String =
    "### INVOCATION-POLICY\n\n" + entries.mkString("\n")

  // Uses the WorkflowCmlSpec PaymentLifecycle / required-capability fixture pattern.
  private def _workflow_source(policies: String = ""): String =
    """# WORKFLOW
      |
      |## OrderProgress
      |
      |version = workflow-v1
      |
      |### COMPOSITE-STATEMACHINE
      |
      |#### CONSTITUENT
      |
      |##### payment
      |state-machine = PaymentLifecycle
      |subject = payment
      |subject-type = PaymentCommand
      |
      |#### STATE
      |
      |##### Pending
      |
      |##### Complete
      |
      |#### DERIVATION
      |
      |##### pending
      |state = Pending
      |when = payment.Awaiting
      |
      |##### complete
      |state = Complete
      |when = payment.Paid
      |
      |#### INITIAL
      |
      |payment.Awaiting
      |
      |#### ACTION
      |
      |##### capture-payment
      |kind = OPERATION
      |operation = capturePayment
      |input = payment.subject
      |
      |##### refund-payment
      |kind = OPERATION
      |operation = capturePayment
      |input = payment.subject
      |
      |#### CONSTITUENT-ACTION
      |
      |##### capture-transition
      |role = payment
      |from = Awaiting
      |to = Paid
      |on = captured
      |placement = transition
      |action = capture-payment
      |
      |#### DERIVED-ACTION
      |
      |##### complete-transition
      |from = Pending
      |to = Complete
      |action = capture-payment
      |
      |### REQUIRED-OPERATION
      |
      |#### capture-payment-capability
      |action = capture-payment
      |
      |### OPERATION
      |
      |#### capturePayment
      |operation = OrderService.capturePayment
      |
      |""".stripMargin + policies + """
      |# STATE-MACHINE
      |
      |## PaymentLifecycle
      |
      |### State
      |
      |#### Awaiting
      |
      |##### Transition
      |to = Paid
      |on = captured
      |
      |#### Paid
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

  private def _judgment_source(policies: String): String =
    _workflow_source(policies).replace(
      "##### capture-payment\nkind = OPERATION\noperation = capturePayment\ninput = payment.subject",
      """##### judge-payment
        |kind = JUDGMENT
        |operation = capturePayment
        |input = payment.subject
        |goal = payment-review
        |context = order-context
        |candidate = payment-candidate
        |alternative = approve
        |alternative = reject
        |criteria = amount-valid
        |criteria = fraud-clear
        |expected-result = decision
        |rationale = decision-rationale
        |evidence = payment-evidence
        |evidence-scope = order
        |evidence-freshness = current
        |evidence-provenance = payment-ledger
        |
        |##### admit-payment
        |kind = ADMISSION
        |operation = capturePayment
        |input = payment.subject
        |candidate-action = judge-payment
        |effect = LOCAL
        |transaction = REQUIRED
        |idempotency = NOT_REQUIRED""".stripMargin
    ).replace("action = capture-payment", "action = judge-payment")
}
