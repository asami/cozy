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
final class WorkflowExecutionSafetyCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _default_fields = "action = capture-payment\ndefault-classification = PERMANENT"
  private val _required_fields = "logical-execution-identity-ref = order-run\nduplicate-protection = REQUIRED\nidempotency-key-ref = order-start"
  private val _action_fields = "kind = OPERATION\noperation = capturePayment\ninput = payment.subject"

  "Workflow failure and execution safety source contract" should {
    "immutable declarations and existing contracts" which {
      "retain the complete fixture's declarations and actual source correlation" in {
        Given("the full CML fixture with retry, timeout, lifecycle, waits and independent Action idempotency")
        val source = _fixture

        When("ModelBuilder admits real CML and normalizes its Workflow")
        val workflow = _workflow(source)

        Then("failure rules, safety references, existing bounds and source lines are retained")
        workflow.failurePolicies shouldBe Vector(WorkflowFailurePolicy(
          "capture-failure", WorkflowInvocationTarget.Participant("capture-payment-capability"), "capture-payment",
          WorkflowFailureClassification.Permanent,
          Vector(
            WorkflowFailureRule("transient.payment", WorkflowFailureClassification.Retryable, WorkflowSourceIdentity(Some(_line(source, "##### transient.payment")))),
            WorkflowFailureRule("payment.rejected", WorkflowFailureClassification.Permanent, WorkflowSourceIdentity(Some(_line(source, "##### payment.rejected"))))
          ), WorkflowSourceIdentity(Some(_line(source, "#### capture-failure")))
        ))
        workflow.executionSafetyPolicy shouldBe Some(WorkflowExecutionSafetyPolicy(
          "order-run", WorkflowDuplicateProtection.Required("order-start"), WorkflowSourceIdentity(Some(_line(source, "### EXECUTION-SAFETY")))
        ))
        workflow.invocationPolicies.head.retry shouldBe Some(WorkflowRetryPolicy(3, 250L))
        workflow.invocationPolicies.head.executionTimeoutMillis shouldBe Some(30000L)
        workflow.lifecyclePolicy.get.deadlineMillis shouldBe Some(60000L)
        workflow.waits.map(_.trigger) shouldBe Vector(WorkflowWaitTrigger.Timer(250L), WorkflowWaitTrigger.Signal("payment.completed"))
        workflow.compositeStateMachine.actions.head.metadata.get.idempotency shouldBe CompositeStateMachineIdempotency.Required("payment-action-key")
        workflow.source.root.line shouldBe Some(_line(source, "# WORKFLOW"))
        workflow.source.definition.line shouldBe Some(_line(source, "## SafetyWorkflow"))
        WorkflowFailureClassification.Retryable.canonicalValue shouldBe "RETRYABLE"
        WorkflowFailureClassification.Permanent.canonicalValue shouldBe "PERMANENT"
        WorkflowDuplicateProtection.Required("order-start").canonicalValue shouldBe "REQUIRED"
        WorkflowDuplicateProtection.NotRequired.canonicalValue shouldBe "NOT_REQUIRED"
      }

      "keep absence and every previous constructor arity source compatible" in {
        Given("existing Workflow source without either new declaration")
        val source = _source()

        When("source normalization and old positional constructor calls create Workflows")
        val workflow = _workflow(source)
        val legacy = Vector(
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations),
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations),
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations, workflow.invocationPolicies),
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations, workflow.invocationPolicies, workflow.lifecyclePolicy),
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations, workflow.invocationPolicies, workflow.lifecyclePolicy, workflow.waits)
        )

        Then("old fields keep their meaning and the new trailing metadata remains undeclared")
        (workflow +: legacy).foreach { value =>
          value.failurePolicies shouldBe Vector.empty
          value.executionSafetyPolicy shouldBe None
          value.compositeStateMachine shouldBe workflow.compositeStateMachine
          value.requiredOperations shouldBe workflow.requiredOperations
        }
        workflow.providedOperations.head.operation.name shouldBe "capturePayment"
        workflow.invocationPolicies.head.retry shouldBe Some(WorkflowRetryPolicy(3, 250L))
        workflow.waits.size shouldBe 2
      }

      Vector(
        ("failure only", _failure(_policy("capture", _default_fields)), true, false),
        ("safety only", _safety(_required_fields), false, true),
        ("both", _failure(_policy("capture", _default_fields)) + _safety(_required_fields), true, true)
      ).foreach { case (description, metadata, hasfailure, hassafety) =>
        s"admit $description while preserving all existing normalized data" in {
          Given(s"identical existing CML with $description appended after its old declarations")
          val baseline = _source()
          val source = _source(metadata)

          When("ModelBuilder normalizes both sources and repeats normalization")
          val original = _workflow(baseline)
          val first = _workflow(source)
          val second = _workflow(source)

          Then("new metadata is independent and CSM, Action, API, SPI, invocation, lifecycle and waits remain equal")
          first shouldBe second
          first.failurePolicies.nonEmpty shouldBe hasfailure
          first.executionSafetyPolicy.nonEmpty shouldBe hassafety
          first.compositeStateMachine shouldBe original.compositeStateMachine
          first.requiredOperations shouldBe original.requiredOperations
          first.providedOperations shouldBe original.providedOperations
          first.invocationPolicies shouldBe original.invocationPolicies
          first.lifecyclePolicy shouldBe original.lifecyclePolicy
          first.waits shouldBe original.waits
          first.version shouldBe original.version
          first.source shouldBe original.source
        }
      }
    }

    "failure target and identity meaning" which {
      Vector(
        ("Action", "ACTION =  CAPTURE_payment  ", WorkflowInvocationTarget.Action("CAPTURE_payment")),
        ("Participant", "PARTICIPANT =  CAPTURE_payment_CAPABILITY  ", WorkflowInvocationTarget.Participant("CAPTURE_payment_CAPABILITY"))
      ).foreach { case (description, target, expected) =>
        s"resolve a canonical $description alias and preserve declared spelling" in {
          Given(s"a failure policy with a case and separator alias for its $description target")
          val source = _source(_failure(_policy("capture", target + "\nDEFAULT_classification =  RETRYABLE  ")))

          When("ModelBuilder resolves the policy against existing Actions and required capabilities")
          val policy = _workflow(source).failurePolicies.head

          Then("the target kind and trimmed spelling survive separately from the canonical Action")
          policy.target shouldBe expected
          policy.actionIdentity shouldBe "capture-payment"
          policy.defaultClassification shouldBe WorkflowFailureClassification.Retryable
          policy.rules shouldBe Vector.empty
        }
      }

      Vector("PERMANENT" -> WorkflowFailureClassification.Permanent, "RETRYABLE" -> WorkflowFailureClassification.Retryable).foreach { case (token, expected) =>
        s"admit a default-only $token policy without manufacturing retry declarations" in {
          Given(s"a default-only $token failure policy and no INVOCATION-POLICY section")
          val source = _without_invocation(_source(_failure(_policy("capture", s"action = capture-payment\ndefault-classification = $token"))))

          When("ModelBuilder normalizes source classification independently of retry budgets")
          val workflow = _workflow(source)

          Then("the explicit default and an empty rule vector coexist with absent invocation metadata")
          workflow.failurePolicies.head.defaultClassification shouldBe expected
          workflow.failurePolicies.head.rules shouldBe Vector.empty
          workflow.invocationPolicies shouldBe Vector.empty
        }
      }

      "retain policy and rule order and permit identical rule names on different targets" in {
        Given("two logical Actions with ordered policies, ordered rules and a shared opaque failure identity")
        val metadata = _failure(
          _policy("refund-first", "action = refund-payment\ndefault-classification = RETRYABLE", _rule("payment.rejected", "PERMANENT"), _rule("payment.pending", "RETRYABLE")),
          _policy("capture-second", _default_fields, _rule("payment.rejected", "RETRYABLE"))
        )
        val source = _with_second_action(_source(metadata))

        When("ModelBuilder normalizes policies in their declared order")
        val policies = _workflow(source).failurePolicies

        Then("identities, defaults, classifications and source lines remain ordered without global rule resolution")
        policies.map(_.identity) shouldBe Vector("refund-first", "capture-second")
        policies.map(_.actionIdentity) shouldBe Vector("refund-payment", "capture-payment")
        policies.map(_.defaultClassification) shouldBe Vector(WorkflowFailureClassification.Retryable, WorkflowFailureClassification.Permanent)
        policies.head.rules.map(_.identity) shouldBe Vector("payment.rejected", "payment.pending")
        policies.head.rules.map(_.classification) shouldBe Vector(WorkflowFailureClassification.Permanent, WorkflowFailureClassification.Retryable)
        policies(1).rules.head.classification shouldBe WorkflowFailureClassification.Retryable
        policies.map(_.source.line) shouldBe Vector(Some(_line(source, "#### refund-first")), Some(_line(source, "#### capture-second")))
        policies.head.rules.map(_.source.line) shouldBe Vector(Some(_line(source, "##### payment.rejected")), Some(_line(source, "##### payment.pending")))
      }

      "preserve case-sensitive, escaped and Unicode logical failure identities verbatim" in {
        Given("opaque rule headings distinct by case, separators, punctuation, backslash, quotes and Unicode")
        val identities = Vector("payment.rejected", "Payment.Rejected", "payment_rejected", "決済失敗 / \"理由\" \\v1")
        val source = _source(_failure(_policy("capture", _default_fields, identities.map(_rule(_, "RETRYABLE")): _*)))

        When("ModelBuilder admits logical failure identities without exception or catalog lookup")
        val rules = _workflow(source).failurePolicies.head.rules

        Then("each exact trimmed identity and its actual heading line are retained separately")
        rules.map(_.identity) shouldBe identities
        rules.map(_.classification).distinct shouldBe Vector(WorkflowFailureClassification.Retryable)
        rules.map(_.source.line) shouldBe identities.map(name => Some(_line(source, s"##### $name")))
      }

      "admit JUDGMENT targets through both Action and Participant declarations" in {
        Given("the established full candidate/admission CML with either valid Judgment target form")
        val sources = Vector("action = JUDGE_payment", "participant = CAPTURE_payment_CAPABILITY").map { target =>
          _candidate_source(_failure(_policy("judge-failure", target + "\ndefault-classification = PERMANENT")))
        }

        When("ModelBuilder resolves each failure target after existing candidate/admission validation")
        val workflows = sources.map(_workflow)

        Then("both policies resolve to Judgment without changing its linked deterministic Admission")
        workflows.foreach { workflow =>
          workflow.failurePolicies.head.actionIdentity shouldBe "judge-payment"
          workflow.compositeStateMachine.actions.map(_.kind) shouldBe Vector("JUDGMENT", "ADMISSION")
          workflow.requiredOperations.head.action.identity shouldBe "judge-payment"
        }
      }

      "reject ADMISSION failure targets and unresolved or ambiguous logical targets" in {
        Given("a table of invalid Action, capability, provider and canonical ambiguity declarations")
        val sources = Vector(
          "unknown Action" -> _source(_failure(_policy("capture", "action = missing\ndefault-classification = PERMANENT"))),
          "unknown capability" -> _source(_failure(_policy("capture", "participant = missing\ndefault-classification = PERMANENT"))),
          "provider identity" -> _source(_failure(_policy("capture", "participant = local-provider\ndefault-classification = PERMANENT"))),
          "ambiguous Action" -> _with_second_action(_source(_failure(_policy("capture", _default_fields))), "CAPTURE_payment"),
          "ambiguous capability" -> _source(_failure(_policy("capture", _default_fields))).replaceFirst("### OPERATION", "#### CAPTURE_payment_CAPABILITY\n\naction = capture-payment\n\n### OPERATION"),
          "ADMISSION Action" -> _candidate_source(_failure(_policy("admission", "action = admit-payment\ndefault-classification = PERMANENT"))),
          "ADMISSION capability" -> _candidate_source(_failure(_policy("admission", "participant = capture-payment-capability\ndefault-classification = PERMANENT"))).replace("action = judge-payment", "action = admit-payment")
        )

        When("ModelBuilder admits each real source at the shared CSM and Workflow boundary")
        val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

        Then("every unresolved, ambiguous or deterministic-local target is rejected explicitly")
        errors.foreach { case (description, error) =>
          withClue(description + ": ") {
            if (description == "ADMISSION Action") {
              error.getMessage should (include("FAILURE-POLICY") and include("ADMISSION") and include("OPERATION or JUDGMENT"))
            } else {
              error.getMessage should (include("Action") or include("ACTION") or include("REQUIRED-OPERATION"))
            }
          }
        }
      }
    }

    "symbolic execution safety and independent Action idempotency" which {
      Vector(
        ("REQUIRED", _required_fields, WorkflowDuplicateProtection.Required("order-start")),
        ("NOT_REQUIRED", "logical-execution-identity-ref = order-run\nduplicate-protection = NOT_REQUIRED", WorkflowDuplicateProtection.NotRequired)
      ).foreach { case (description, fields, expected) =>
        s"retain $description protection independently of Action REQUIRED idempotency" in {
          Given(s"a Workflow $description safety declaration and an Action with its own distinct required key")
          val source = _source(_safety(fields))

          When("ModelBuilder normalizes the two independent declaration levels")
          val workflow = _workflow(source)

          Then("Workflow protection is explicit and Action idempotency, compensation and transaction metadata survive")
          workflow.executionSafetyPolicy.get.logicalExecutionIdentityRef shouldBe "order-run"
          workflow.executionSafetyPolicy.get.duplicateProtection shouldBe expected
          workflow.compositeStateMachine.actions.head.metadata shouldBe Some(CompositeStateMachineActionMetadata(
            CompositeStateMachineEffectClass.External, CompositeStateMachineTransactionRequirement.OutsideUnitOfWork,
            CompositeStateMachineIdempotency.Required("payment-action-key"), Some("payment.refund")
          ))
        }
      }

      "retain opaque symbolic identity and key references without expression or registry interpretation" in {
        Given("trimmed Unicode and escaped symbolic references with normalized field aliases")
        val identity = "受注 / \"論理\" \\execution:v1"
        val key = "重複 / \"安定\" \\key:v1"
        val source = _source(_safety(s"LOGICAL_execution_IDENTITY_ref =  $identity  \nDUPLICATE_protection =  REQUIRED  \nIDEMPOTENCY_key_REF =  $key  "))

        When("ModelBuilder retains references as provider-neutral source values")
        val safety = _workflow(source).executionSafetyPolicy.get

        Then("the symbolic reference strings survive verbatim after trimming")
        safety.logicalExecutionIdentityRef shouldBe identity
        safety.duplicateProtection shouldBe WorkflowDuplicateProtection.Required(key)
      }
    }

    "closed fields, exact tokens and ordered entry grammar" which {
      "reject every missing, empty, repeated and unknown policy field" in {
        Given("a rejection table covering both target forms and the complete policy field set")
        val fields = Vector(
          "missing target" -> "default-classification = PERMANENT",
          "empty Action" -> "action =\ndefault-classification = PERMANENT",
          "empty Participant" -> "participant =\ndefault-classification = PERMANENT",
          "both targets" -> (_default_fields + "\nparticipant = capture-payment-capability"),
          "repeated Action aliases" -> (_default_fields + "\nACTION = capture-payment"),
          "repeated Participant aliases" -> "participant = capture-payment-capability\nPARTICIPANT = capture-payment-capability\ndefault-classification = PERMANENT",
          "missing default" -> "action = capture-payment",
          "empty default" -> "action = capture-payment\ndefault-classification =",
          "repeated default aliases" -> (_default_fields + "\nDEFAULT_classification = RETRYABLE"),
          "unknown field" -> (_default_fields + "\nunknown = x"),
          "rule field on policy" -> (_default_fields + "\nclassification = PERMANENT")
        )
        val sources = fields.map { case (description, value) => description -> _source(_failure(_policy("capture", value))) }

        When("ModelBuilder validates policy fields before constructing immutable metadata")
        val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

        Then("each malformed policy fails at the failure-policy boundary")
        errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should include("FAILURE-POLICY") } }
      }

      "reject every missing, empty, repeated and unknown failure-rule field" in {
        Given("failure rules whose complete direct field set is violated")
        val fields = Vector(
          "missing classification" -> "", "empty classification" -> "classification =",
          "repeated aliases" -> "classification = RETRYABLE\nCLASSIFICATION = PERMANENT",
          "unknown field" -> "classification = RETRYABLE\nunknown = x",
          "policy default on rule" -> "classification = RETRYABLE\ndefault-classification = PERMANENT",
          "target on rule" -> "classification = RETRYABLE\nparticipant = capture-payment-capability"
        )
        val sources = fields.map { case (description, value) => description -> _source(_failure(_policy("capture", _default_fields, s"##### failure\n$value\n"))) }

        When("ModelBuilder validates direct rule fields")
        val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

        Then("no malformed rule can inherit or override undeclared source semantics")
        errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should include("FAILURE-POLICY") } }
      }

      "reject every missing, empty, repeated and unknown safety field and invalid key pairing" in {
        Given("safety declarations violating mandatory fields, scalar uniqueness or the exact protection-key pairing")
        val fields = Vector(
          "empty section" -> "",
          "missing identity" -> "duplicate-protection = NOT_REQUIRED",
          "empty identity" -> "logical-execution-identity-ref =\nduplicate-protection = NOT_REQUIRED",
          "repeated identity aliases" -> (_required_fields + "\nLOGICAL_execution_identity_REF = other"),
          "missing protection" -> "logical-execution-identity-ref = order-run",
          "empty protection" -> "logical-execution-identity-ref = order-run\nduplicate-protection =",
          "repeated protection aliases" -> (_required_fields + "\nDUPLICATE_protection = NOT_REQUIRED"),
          "missing required key" -> "logical-execution-identity-ref = order-run\nduplicate-protection = REQUIRED",
          "empty required key" -> "logical-execution-identity-ref = order-run\nduplicate-protection = REQUIRED\nidempotency-key-ref =",
          "repeated key aliases" -> (_required_fields + "\nIDEMPOTENCY_key_ref = other"),
          "prohibited key" -> "logical-execution-identity-ref = order-run\nduplicate-protection = NOT_REQUIRED\nidempotency-key-ref = stable",
          "prohibited empty key" -> "logical-execution-identity-ref = order-run\nduplicate-protection = NOT_REQUIRED\nidempotency-key-ref =",
          "unknown field" -> (_required_fields + "\nunknown = x")
        )
        val sources = fields.map { case (description, value) => description -> _source(_safety(value)) }

        When("ModelBuilder validates the closed safety field set")
        val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

        Then("every malformed safety declaration is rejected without a default or weakened protection")
        errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should include("EXECUTION-SAFETY") } }
      }

      "reject noncanonical enum values at both classification positions and duplicate protection" in {
        Given("lowercase, mixed-case, separator aliases, quotes, empty and unknown enum values")
        val failuretokens = Vector("", "retryable", "Retryable", "permanent", "Permanent", "RE_TRYABLE", "PER_MANENT", "UNKNOWN", "\"RETRYABLE\"", "'PERMANENT'")
        val safetytokens = Vector("", "required", "Required", "not_required", "NOT-REQUIRED", "NOTREQUIRED", "UNKNOWN", "\"REQUIRED\"", "'NOT_REQUIRED'")
        val sources = failuretokens.flatMap { token => Vector(
          s"default '$token'" -> _source(_failure(_policy("capture", s"action = capture-payment\ndefault-classification = $token"))),
          s"rule '$token'" -> _source(_failure(_policy("capture", _default_fields, _rule("failure", token))))
        ) } ++ safetytokens.map(token => s"protection '$token'" -> _source(_safety(s"logical-execution-identity-ref = run\nduplicate-protection = $token")))

        When("ModelBuilder compares trimmed enum values without case folding")
        val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

        Then("only canonical values can enter the immutable closed alternatives")
        errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should (include("FAILURE-POLICY") or include("EXECUTION-SAFETY")) } }
      }

      "reject empty identities, direct section fields, nested fields and child structures" in {
        Given("a table violating section-entry-rule depth and safety's direct-only grammar")
        val metadata = Vector(
          "empty failure section" -> _failure(),
          "direct failure field" -> _failure(_default_fields),
          "empty policy entry" -> _failure(_policy("capture", "")),
          "empty policy identity" -> _failure(_policy("", _default_fields)),
          "empty rule identity" -> _failure(_policy("capture", _default_fields, _rule("", "RETRYABLE"))),
          "target nested as heading" -> _failure(_policy("capture", "default-classification = PERMANENT", "##### action\ncapture-payment\n")),
          "default nested as heading" -> _failure(_policy("capture", "action = capture-payment", "##### default-classification\nPERMANENT\n")),
          "classification nested as heading" -> _failure(_policy("capture", _default_fields, "##### failure\n###### classification\nRETRYABLE\n")),
          "rule child structure" -> _failure(_policy("capture", _default_fields, _rule("failure", "RETRYABLE") + "###### other\nclassification = PERMANENT\n")),
          "safety child" -> _safety(_required_fields + "\n#### nested\nunknown = x"),
          "identity reference nested" -> _safety("duplicate-protection = NOT_REQUIRED\n#### logical-execution-identity-ref\norder-run"),
          "protection nested" -> _safety("logical-execution-identity-ref = run\n#### duplicate-protection\nNOT_REQUIRED"),
          "key reference nested" -> _safety("logical-execution-identity-ref = run\nduplicate-protection = REQUIRED\n#### idempotency-key-ref\nstable")
        )
        val sources = metadata.map { case (description, value) => description -> _source(value) }

        When("ModelBuilder admits each structural declaration")
        val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

        Then("malformed depth and empty entries cannot disappear into narrative or inherited fields")
        errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should not be empty } }
      }

      "reject normalized duplicate sections, policy identities, target aliases and exact rule identities" in {
        Given("duplicate declarations expressed with canonical and normalized aliases")
        val metadata = Vector(
          "failure sections" -> (_failure(_policy("capture", _default_fields)) + _failure(_policy("other", _default_fields)).replace("FAILURE-POLICY", "Failure_Policy")),
          "safety sections" -> (_safety(_required_fields) + _safety(_required_fields).replace("EXECUTION-SAFETY", "Execution_Safety")),
          "policy identities" -> _failure(_policy("capture-failure", _default_fields), _policy("CAPTURE_failure", "action = refund-payment\ndefault-classification = PERMANENT")),
          "canonical Action targets" -> _failure(_policy("one", _default_fields), _policy("two", "action = CAPTURE_payment\ndefault-classification = RETRYABLE")),
          "Action and Participant aliases" -> _failure(_policy("one", _default_fields), _policy("two", "participant = CAPTURE_payment_CAPABILITY\ndefault-classification = PERMANENT")),
          "exact rule identities" -> _failure(_policy("capture", _default_fields, _rule("failure", "RETRYABLE"), _rule("failure", "PERMANENT")))
        )
        val sources = metadata.map { case (description, value) => description -> _with_second_action(_source(value)) }

        When("ModelBuilder retains duplicates until uniqueness checks run")
        val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

        Then("duplicates fail instead of being overwritten by maps or precedence")
        errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should (include("unique") or include("at most one")) } }
      }
    }

    "structural ownership and opaque identity compatibility" which {
      "require a valid CSM even when either new declaration is empty" in {
        Given("structural Workflow definitions with no CSM or an incomplete CSM")
        val sources = Vector("FAILURE-POLICY", "EXECUTION-SAFETY").flatMap { name => Vector(
          s"$name missing CSM" -> s"# WORKFLOW\n## MissingStructure\nversion = v1\n### $name\n",
          s"$name malformed CSM" -> s"# WORKFLOW\n## MissingStructure\nversion = v1\n### COMPOSITE-STATEMACHINE\n### $name\n"
        ) }

        When("ModelBuilder interprets these sections as structural metadata")
        val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

        Then("missing or malformed CSM structure is rejected without narrative demotion")
        errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should include("COMPOSITE-STATEMACHINE") } }
      }

      Vector("FAILURE-POLICY", "EXECUTION-SAFETY", "LIFECYCLE", "WAIT").foreach { name =>
        s"reject misplaced structural $name while preserving the failure-rule identity position" in {
          Given(s"$name misplaced at roots, CSM, API/SPI, invocation, lifecycle, waits and beneath failure rules")
          val anchors = Vector(
            "document root" -> ("# WORKFLOW", s"# $name\n\n# WORKFLOW"),
            "Workflow root" -> ("## SafetyWorkflow", s"## $name\n\n## SafetyWorkflow"),
            "CSM child" -> ("#### STATE", s"#### $name\n\n#### STATE"),
            "CSM State child" -> ("##### Pending", s"##### Pending\n###### $name"),
            "CSM Action child" -> ("compensation-handler = payment.refund", s"compensation-handler = payment.refund\n###### $name"),
            "SPI child" -> ("action = capture-payment\n\n### OPERATION", s"action = capture-payment\n##### $name\n\n### OPERATION"),
            "API child" -> ("operation = OrderService.capturePayment", s"operation = OrderService.capturePayment\n##### $name"),
            "invocation child" -> ("execution-timeout-millis = 30000", s"execution-timeout-millis = 30000\n##### $name"),
            "lifecycle child" -> ("cancellation = COOPERATIVE", s"cancellation = COOPERATIVE\n#### $name"),
            "wait child" -> ("delay-millis = 250", s"delay-millis = 250\n##### $name"),
            "failure-rule child" -> ("classification = RETRYABLE", s"classification = RETRYABLE\n###### $name"),
            "ordinary Workflow child" -> ("### INVOCATION-POLICY", s"### OTHER\n#### $name\n\n### INVOCATION-POLICY")
          )
          val sources = anchors.map { case (description, (before, after)) => description -> _fixture.replace(before, after) }

          When("ModelBuilder traverses structural positions with legacy and new ownership guards")
          val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

          Then("misplaced structures fail before they can be silently ignored")
          errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should include(name) } }
        }
      }

      Vector("default-classification", "logical-execution-identity-ref", "duplicate-protection", "idempotency-key-ref").foreach { fieldname =>
        s"reject globally reserved $fieldname at every unowned depth and model division" in {
          Given(s"a $fieldname direct field outside its exact owner in Workflow, standalone CSM and unrelated divisions")
          val anchors = Vector(
            "# WORKFLOW", "version = workflow-safety-v4", "### COMPOSITE-STATEMACHINE", "#### STATE", "##### Pending", "##### capture-payment",
            "### REQUIRED-OPERATION", "#### capture-payment-capability", "### OPERATION", "operation = OrderService.capturePayment",
            "#### capture-control", "### LIFECYCLE", "#### payment-timer", "# VALUE", "## PaymentCommand", "## OrderService", "###### TYPE"
          )
          val misplaced = anchors.map(anchor => anchor -> _source().replace(anchor, anchor + s"\n$fieldname = x\n"))
          val rulesource = _source(_failure(_policy("capture", _default_fields, _rule("failure", "RETRYABLE") + s"$fieldname = x\n")))
          val standalone = _source().replace("# WORKFLOW", "# COMPOSITE-STATEMACHINE").replace("version = workflow-safety-v4", s"$fieldname = x")
          val sources = misplaced ++ Vector("failure-rule field" -> rulesource, "standalone CSM" -> standalone)

          When("ModelBuilder recursively checks globally reserved direct fields")
          val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

          Then("the reserved field fails at its original spelling rather than being discarded by another parser")
          errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should include(fieldname) } }
        }
      }

      "reserve classification only in Workflow and CSM contexts" in {
        Given("a generic classification field in unrelated VALUE and SERVICE divisions and in unowned Workflow positions")
        val legal = _source().replace("## PaymentCommand", "## PaymentCommand\nclassification = domain-value").replace("## OrderService", "## OrderService\nclassification = service-category")
        val illegal = Vector("# WORKFLOW", "version = workflow-safety-v4", "### COMPOSITE-STATEMACHINE", "##### Pending", "##### capture-payment", "#### capture-payment-capability", "#### capture-control", "### LIFECYCLE", "#### payment-timer").map { anchor =>
          anchor -> _source().replace(anchor, anchor + "\nclassification = RETRYABLE\n")
        }

        When("ModelBuilder applies contextual ownership instead of globally reserving a generic domain word")
        val workflow = _workflow(legal)
        val errors = illegal.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

        Then("unrelated models remain valid and unowned Workflow classification fields fail")
        workflow.failurePolicies shouldBe Vector.empty
        workflow.compositeStateMachine.actions.head.identity shouldBe "capture-payment"
        errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should include("classification") } }
      }

      Vector("FailurePolicy", "ExecutionSafety", "Wait", "Lifecycle").foreach { identity =>
        s"preserve opaque identity $identity in all established and failure-rule positions" in {
          Given(s"valid source variants using $identity as an identity rather than a structural section")
          val metadata = _failure(_policy("capture", _default_fields, _rule(identity, "RETRYABLE")))
          val baseline = _source(metadata)
          val variants = Vector(
            "Workflow" -> baseline.replace("## SafetyWorkflow", s"## $identity"),
            "StateMachine" -> baseline.replace("PaymentLifecycle", identity),
            "constituent State" -> baseline.replace("#### Awaiting", s"#### $identity").replace("payment.Awaiting", s"payment.$identity"),
            "constituent" -> baseline.replace("##### payment", s"##### $identity").replace("payment.subject", s"$identity.subject").replace("payment.Awaiting", s"$identity.Awaiting").replace("payment.Paid", s"$identity.Paid"),
            "CSM State" -> baseline.replace("##### Pending", s"##### $identity").replace("state = Pending", s"state = $identity"),
            "Action" -> baseline.replace("##### capture-payment", s"##### $identity").replace("action = capture-payment", s"action = $identity"),
            "derivation" -> baseline.replace("##### pending", s"##### $identity"),
            "SPI" -> baseline.replace("#### capture-payment-capability", s"#### $identity"),
            "API" -> baseline.replace("capturePayment", identity),
            "invocation" -> baseline.replace("#### capture-control", s"#### $identity"),
            "failure policy" -> baseline.replace("#### capture\n", s"#### $identity\n"),
            "wait" -> baseline.replace("#### payment-timer", s"#### $identity")
          )

          When("ModelBuilder normalizes each legal identity position")
          val workflows = variants.map { case (description, source) => description -> _workflow(source) }

          Then("the named identity and opaque failure-rule heading survive without structural reinterpretation")
          workflows.foreach { case (description, workflow) =>
            val actual = description match {
              case "Workflow" => workflow.identity
              case "StateMachine" => workflow.compositeStateMachine.constituents.head.stateMachine.name
              case "constituent State" => workflow.compositeStateMachine.constituents.head.states.head
              case "constituent" => workflow.compositeStateMachine.constituents.head.role
              case "CSM State" => workflow.compositeStateMachine.states.head.name
              case "Action" => workflow.compositeStateMachine.actions.head.identity
              case "derivation" => workflow.compositeStateMachine.derivations.head.identity
              case "SPI" => workflow.requiredOperations.head.capability
              case "API" => workflow.providedOperations.head.operation.name
              case "invocation" => workflow.invocationPolicies.head.identity
              case "failure policy" => workflow.failurePolicies.head.identity
              case "wait" => workflow.waits.head.identity
            }
            withClue(description + ": ") {
              actual shouldBe identity
              workflow.failurePolicies.head.rules.head.identity shouldBe identity
              workflow.failurePolicies.head.rules.head.classification shouldBe WorkflowFailureClassification.Retryable
            }
          }
        }
      }

      "permit every reserved structural word as an opaque failure-rule identity" in {
        Given("logical failures named after source structures and execution-binding words")
        val identities = Vector("FAILURE-POLICY", "EXECUTION-SAFETY", "LIFECYCLE", "WAIT", "ACTION", "INVOCATION-POLICY", "PROVIDER", "SCRIPT", "RUNTIME", "CLASSIFICATION")
        val source = _source(_failure(_policy("capture", _default_fields, identities.map(_rule(_, "PERMANENT")): _*)))

        When("ModelBuilder recognizes failure-rule heading positions rather than reserving identity spellings")
        val rules = _workflow(source).failurePolicies.head.rules

        Then("all nonempty provider-neutral identities remain exact ordered logical names")
        rules.map(_.identity) shouldBe identities
        rules.map(_.classification).distinct shouldBe Vector(WorkflowFailureClassification.Permanent)
      }

      "reject forbidden execution vocabulary throughout the new closed declarations" in {
        Given("provider, script, runtime, retry, orchestration and continuation fields and nested structures")
        val vocabulary = Vector("provider", "script", "runtime", "retry", "execution", "control", "orchestration", "continuation", "invocation-binding", "implementation", "binding", "action-execution")
        val sources = vocabulary.flatMap { word => Vector(
          s"policy field $word" -> _source(_failure(_policy("capture", _default_fields + s"\n$word = external"))),
          s"rule field $word" -> _source(_failure(_policy("capture", _default_fields, _rule("failure", "RETRYABLE") + s"$word = external\n"))),
          s"safety field $word" -> _source(_safety(_required_fields + s"\n$word = external")),
          s"rule child $word" -> _source(_failure(_policy("capture", _default_fields, _rule("failure", "RETRYABLE") + s"###### $word\n"))),
          s"safety child $word" -> _source(_safety(_required_fields + s"\n#### $word\n"))
        ) }

        When("ModelBuilder validates source-only metadata's complete fields and nesting")
        val errors = sources.map { case (description, source) => description -> intercept[RuntimeException] { _workflow(source) } }

        Then("execution-binding grammar cannot enter through failure or safety declarations")
        errors.foreach { case (description, error) => withClue(description + ": ") { error.getMessage should (include("FAILURE-POLICY") or include("EXECUTION-SAFETY")) } }
      }

      "apply ownership admission on both extraction routes and standalone CSM roots" in {
        Given("real CML with a reserved field outside Workflow ownership and a misplaced standalone CSM section")
        val sources = Vector(
          _source().replace("## PaymentCommand", "## PaymentCommand\nlogical-execution-identity-ref = unowned"),
          _source().replace("# WORKFLOW", "# COMPOSITE-STATEMACHINE").replace("### COMPOSITE-STATEMACHINE", "### FAILURE-POLICY")
        )
        val models = sources.map(_model)

        When("each public model extraction route is asked to normalize those models")
        val errors = models.flatMap(model => Vector(
          intercept[RuntimeException] { CompositeStateMachineCml.definitions(model) },
          intercept[RuntimeException] { CompositeStateMachineCml.workflowDefinitions(model) }
        ))

        Then("both routes reject the same unowned metadata before consumer extraction")
        errors.size shouldBe 4
        errors.foreach(_.getMessage should (include("logical-execution-identity-ref") or include("FAILURE-POLICY")))
      }
    }
  }

  private def _fixture: String = _resource("workflow-failure-execution-safety-policy.cml")

  private def _resource(name: String): String = {
    val stream = getClass.getResourceAsStream(s"/modeler/$name")
    val source = scala.io.Source.fromInputStream(stream, "UTF-8")
    try { source.mkString } finally { source.close() }
  }

  private def _source(metadata: String = ""): String = {
    val source = _fixture
    val start = source.indexOf("### FAILURE-POLICY")
    val end = source.indexOf("# STATE-MACHINE", start)
    source.substring(0, start) + metadata + "\n" + source.substring(end)
  }

  private def _candidate_source(metadata: String): String = {
    _resource("candidate-admission-workflow.cml").replace("# STATE-MACHINE", metadata + "\n# STATE-MACHINE")
  }

  private def _without_invocation(source: String): String = {
    val start = source.indexOf("### INVOCATION-POLICY")
    val end = source.indexOf("### LIFECYCLE", start)
    source.substring(0, start) + source.substring(end)
  }

  private def _with_second_action(source: String, identity: String = "refund-payment"): String = {
    source.replace("### REQUIRED-OPERATION", s"##### $identity\n\n${_action_fields}\n\n### REQUIRED-OPERATION")
  }

  private def _failure(entries: String*): String = "### FAILURE-POLICY\n\n" + entries.mkString("\n") + "\n"

  private def _policy(identity: String, fields: String, rules: String*): String = {
    s"#### $identity\n\n$fields\n\n" + rules.mkString("\n") + "\n"
  }

  private def _rule(identity: String, classification: String): String = s"##### $identity\n\nclassification = $classification\n"

  private def _safety(fields: String): String = s"### EXECUTION-SAFETY\n\n$fields\n\n"

  private def _line(source: String, heading: String): Int = source.linesIterator.toVector.indexOf(heading) + 1

  private def _model(source: String): KaleidoxModel = {
    val model = KaleidoxModel.parse(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty) {
      throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    }
    model
  }

  private def _workflow(source: String): WorkflowDefinition = {
    val model = _model(source)
    Modeler.ModelBuilder(model)
    CompositeStateMachineCml.workflowDefinitions(model).head
  }
}
