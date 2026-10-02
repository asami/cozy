package cozy.modeler

import java.util.Locale
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.sm.NameTransitionTo
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
/** INTERFACE-driven specification of real Workflow source admission and immutable IR. */
final class WorkflowSemanticOutcomeCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _revision_fields = "participant = review-capability\noutcome = NEEDS_REVISION\nrole = review\nfrom = Reviewing\nto = Revising\non = needsRevision"
  private val _input_fields = "action = ReviewChange\noutcome = NEEDS_INPUT\nrole = review\nfrom = Reviewing\nto = AwaitingInput\non = needsInput"
  private val _rejected_fields = "action = ReviewChange\noutcome = REJECTED\nrole = review\nfrom = Reviewing\nto = Rejected\non = rejected"

  "Workflow semantic outcome source admission" should {
    "immutable declarations and compatibility" which {
      "retain ordered exact outcomes, declared targets and actual source lines" in {
        Given("a typed provider-neutral review loop with three outcome declarations")
        val source = _fixture

        When("ModelBuilder admits the source and Workflow extraction normalizes declarations")
        val workflow = _workflow(source)

        Then("ordered declarations retain canonical values, actual edges and entry heading lines")
        workflow.semanticOutcomes shouldBe Vector(
          WorkflowSemanticOutcomeDeclaration("revision-needed", WorkflowInvocationTarget.Participant("review-capability"), "ReviewChange",
            WorkflowSemanticOutcome.NeedsRevision, WorkflowSemanticOutcomeTransition("review", "ReviewLifecycle", "Reviewing", "Revising", "needsRevision"),
            WorkflowSourceIdentity(Some(_line(source, "#### revision-needed")))),
          WorkflowSemanticOutcomeDeclaration("input-needed", WorkflowInvocationTarget.Action("ReviewChange"), "ReviewChange",
            WorkflowSemanticOutcome.NeedsInput, WorkflowSemanticOutcomeTransition("review", "ReviewLifecycle", "Reviewing", "AwaitingInput", "needsInput"),
            WorkflowSourceIdentity(Some(_line(source, "#### input-needed")))),
          WorkflowSemanticOutcomeDeclaration("review-rejected", WorkflowInvocationTarget.Action("ReviewChange"), "ReviewChange",
            WorkflowSemanticOutcome.Rejected, WorkflowSemanticOutcomeTransition("review", "ReviewLifecycle", "Reviewing", "Rejected", "rejected"),
            WorkflowSourceIdentity(Some(_line(source, "#### review-rejected"))))
        )
        workflow.semanticOutcomes.map(_.outcome.canonicalValue) shouldBe Vector("NEEDS_REVISION", "NEEDS_INPUT", "REJECTED")
        workflow.compositeStateMachine.initialConfiguration.get.bindings shouldBe Vector(CompositeStateMachineConfigurationBinding("review", "Reviewing"))
        workflow.compositeStateMachine.derivations.map(_.state) shouldBe Vector("Reviewing", "AwaitingInput", "Revising", "Rejected", "Approved")
        workflow.requiredOperations.map(_.capability) shouldBe Vector("review-capability", "input-capability")
        workflow.providedOperations.map(_.operation.name) shouldBe Vector("beginReview", "submitReview")
        workflow.compositeStateMachine.actions.map(_.operation.inputType).distinct shouldBe Vector(Some("ReviewContext"))
        workflow.compositeStateMachine.actions.map(_.operation.outputType).distinct shouldBe Vector(Some("ReviewResult"))
      }

      "remain deterministic without changing established CSM and independent metadata" in {
        Given("identical review source with and without the trailing semantic declaration")
        val source = _fixture
        val baseline = _source()

        When("ModelBuilder normalizes the baseline and repeatedly admits the same outcome source")
        val original = _workflow(baseline)
        val first = _workflow(source)
        val second = _workflow(source)
        val repeated = first.copy(semanticOutcomes = first.semanticOutcomes)

        Then("normalization and immutable copies are equality-idempotent and all old fields remain equal")
        first shouldBe second
        repeated shouldBe first
        first.copy(semanticOutcomes = Vector.empty) shouldBe original
        first.invocationPolicies.head.retry shouldBe Some(WorkflowRetryPolicy(3, 250L))
        first.invocationPolicies.head.executionTimeoutMillis shouldBe Some(30000L)
        first.lifecyclePolicy.get.deadlineMillis shouldBe Some(60000L)
        first.lifecyclePolicy.get.cancellation shouldBe Some(WorkflowCancellationMode.Cooperative)
        first.waits.head.trigger shouldBe WorkflowWaitTrigger.Signal("review.input")
        first.failurePolicies.head.defaultClassification shouldBe WorkflowFailureClassification.Permanent
        first.failurePolicies.head.rules.head.classification shouldBe WorkflowFailureClassification.Retryable
        first.executionSafetyPolicy.get.duplicateProtection shouldBe WorkflowDuplicateProtection.NotRequired
        first.compositeStateMachine.actions.head.metadata.get.idempotency shouldBe CompositeStateMachineIdempotency.NotRequired
      }

      "preserve absent declarations and every positional and named constructor arity through Phase 68" in {
        Given("existing Workflow source without semantic outcomes")
        val source = _source()

        When("source admission and all old positional and named constructors create Workflows")
        val workflow = _workflow(source)
        val legacy = Vector(
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations),
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations),
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations, workflow.invocationPolicies),
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations, workflow.invocationPolicies, workflow.lifecyclePolicy),
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations, workflow.invocationPolicies, workflow.lifecyclePolicy, workflow.waits),
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations, workflow.invocationPolicies, workflow.lifecyclePolicy, workflow.waits, workflow.failurePolicies),
          WorkflowDefinition(workflow.identity, workflow.version, workflow.source, workflow.compositeStateMachine, workflow.requiredOperations, workflow.providedOperations, workflow.invocationPolicies, workflow.lifecyclePolicy, workflow.waits, workflow.failurePolicies, workflow.executionSafetyPolicy),
          WorkflowDefinition(identity = workflow.identity, version = workflow.version, source = workflow.source, compositeStateMachine = workflow.compositeStateMachine, requiredOperations = workflow.requiredOperations),
          WorkflowDefinition(identity = workflow.identity, version = workflow.version, source = workflow.source, compositeStateMachine = workflow.compositeStateMachine, requiredOperations = workflow.requiredOperations, providedOperations = workflow.providedOperations),
          WorkflowDefinition(identity = workflow.identity, version = workflow.version, source = workflow.source, compositeStateMachine = workflow.compositeStateMachine, requiredOperations = workflow.requiredOperations, providedOperations = workflow.providedOperations, invocationPolicies = workflow.invocationPolicies),
          WorkflowDefinition(identity = workflow.identity, version = workflow.version, source = workflow.source, compositeStateMachine = workflow.compositeStateMachine, requiredOperations = workflow.requiredOperations, providedOperations = workflow.providedOperations, invocationPolicies = workflow.invocationPolicies, lifecyclePolicy = workflow.lifecyclePolicy),
          WorkflowDefinition(identity = workflow.identity, version = workflow.version, source = workflow.source, compositeStateMachine = workflow.compositeStateMachine, requiredOperations = workflow.requiredOperations, providedOperations = workflow.providedOperations, invocationPolicies = workflow.invocationPolicies, lifecyclePolicy = workflow.lifecyclePolicy, waits = workflow.waits),
          WorkflowDefinition(identity = workflow.identity, version = workflow.version, source = workflow.source, compositeStateMachine = workflow.compositeStateMachine, requiredOperations = workflow.requiredOperations, providedOperations = workflow.providedOperations, invocationPolicies = workflow.invocationPolicies, lifecyclePolicy = workflow.lifecyclePolicy, waits = workflow.waits, failurePolicies = workflow.failurePolicies),
          WorkflowDefinition(identity = workflow.identity, version = workflow.version, source = workflow.source, compositeStateMachine = workflow.compositeStateMachine, requiredOperations = workflow.requiredOperations, providedOperations = workflow.providedOperations, invocationPolicies = workflow.invocationPolicies, lifecyclePolicy = workflow.lifecyclePolicy, waits = workflow.waits, failurePolicies = workflow.failurePolicies, executionSafetyPolicy = workflow.executionSafetyPolicy)
        )

        Then("every old arity defaults the new vector to empty and preserves the original required fields")
        (workflow +: legacy).foreach { value =>
          value.semanticOutcomes shouldBe Vector.empty
          value.identity shouldBe workflow.identity
          value.version shouldBe workflow.version
          value.source shouldBe workflow.source
          value.compositeStateMachine shouldBe workflow.compositeStateMachine
          value.requiredOperations shouldBe workflow.requiredOperations
        }
        legacy(6) shouldBe workflow
        legacy.last shouldBe workflow
      }

      "retain old real source models without manufacturing outcomes or changing their semantics" in {
        Given("the established invocation, lifecycle, failure/safety, producer and CandidateAdmission fixtures")
        val names = Vector("workflow-invocation-policy-legacy.cml", "workflow-invocation-policy.cml", "workflow-lifecycle-policy.cml",
          "workflow-failure-execution-safety-policy.cml", "skill-driven-workflow-producer.cml", "candidate-admission-workflow.cml")
        val sources = names.map(name => name -> _resource(name))

        When("ModelBuilder and both existing extraction routes admit each unchanged fixture")
        val values = sources.map { case (name, source) =>
          val model = _model(source)
          Modeler.ModelBuilder(model)
          val composites = CompositeStateMachineCml.definitions(model)
          val workflows = CompositeStateMachineCml.workflowDefinitions(model)
          (name, composites, workflows)
        }

        Then("all legacy Workflow metadata remains admitted and no outcome is inferred")
        values.foreach { case (name, _, workflows) => withClue(name + ": ") {
          workflows should not be empty
          workflows.foreach(_.semanticOutcomes shouldBe Vector.empty)
        } }
        val candidate = values.find(_._1 == "candidate-admission-workflow.cml").get._3.head
        candidate.compositeStateMachine.actions.map(_.kind) shouldBe Vector("JUDGMENT", "ADMISSION")
        candidate.compositeStateMachine.actions.head.candidateAdmission.get shouldBe a[CompositeStateMachineJudgmentAction]
        candidate.compositeStateMachine.actions.last.candidateAdmission.get shouldBe a[CompositeStateMachineAdmissionAction]
        val safety = values.find(_._1 == "workflow-failure-execution-safety-policy.cml").get._3.head
        safety.failurePolicies.head.rules.map(_.classification) shouldBe Vector(WorkflowFailureClassification.Retryable, WorkflowFailureClassification.Permanent)
        safety.executionSafetyPolicy.get.duplicateProtection shouldBe WorkflowDuplicateProtection.Required("order-start")
        safety.compositeStateMachine.actions.head.metadata.get.idempotency shouldBe CompositeStateMachineIdempotency.Required("payment-action-key")
      }
    }

    "target and canonical pair resolution" which {
      Vector("action = ReviewChange", "ACTION = REVIEW_change", "participant = review-capability", "PARTICIPANT = REVIEW_CAPABILITY").foreach { target =>
        s"resolve declared target $target while retaining its kind and trimmed spelling" in {
          Given("one declared target using an existing Action or capability alias")
          val source = _source(_section(_entry("input", _input_fields.replace("action = ReviewChange", target + "  "))))
          val spelling = target.split("=", 2)(1).trim

          When("ModelBuilder resolves the source target against existing Actions and required capabilities")
          val declaration = _workflow(source).semanticOutcomes.head

          Then("canonical Action identity is independent of the declared target form")
          declaration.actionIdentity shouldBe "ReviewChange"
          declaration.target shouldBe (if (target.toLowerCase(Locale.ROOT).startsWith("action")) WorkflowInvocationTarget.Action(spelling) else WorkflowInvocationTarget.Participant(spelling))
          declaration.outcome shouldBe WorkflowSemanticOutcome.NeedsInput
        }
      }

      "admit outcome subsets without retry and the same value on distinct Actions" in {
        Given("single-outcome sources without invocation policies and distinct targets sharing NEEDS_INPUT")
        val subsets = Vector(_revision_fields, _input_fields, _rejected_fields).map { fields =>
          _without_invocation(_source(_section(_entry("only", fields))))
        }
        val distinct = _without_invocation(_source(_section(_entry("review-input", _input_fields),
          _entry("supply-input", _input_fields.replace("action = ReviewChange", "participant = input-capability")))))

        When("ModelBuilder admits these partial declarative result mappings")
        val singles = subsets.map(_workflow)
        val shared = _workflow(distinct)

        Then("there is no result enumeration or retry requirement and distinct canonical targets remain separate")
        singles.map(_.semanticOutcomes.head.outcome.canonicalValue) shouldBe Vector("NEEDS_REVISION", "NEEDS_INPUT", "REJECTED")
        singles.foreach { workflow =>
          workflow.semanticOutcomes.size shouldBe 1
          workflow.invocationPolicies shouldBe Vector.empty
        }
        shared.semanticOutcomes.map(_.actionIdentity) shouldBe Vector("ReviewChange", "SupplyInput")
        shared.semanticOutcomes.map(_.outcome).distinct shouldBe Vector(WorkflowSemanticOutcome.NeedsInput)
        shared.invocationPolicies shouldBe Vector.empty
      }

      "admit a JUDGMENT target with its valid paired ADMISSION and preserve CandidateAdmission" in {
        Given("real candidate/admission source and Action or Participant declarations targeting Judgment")
        val fields = "outcome = NEEDS_REVISION\nrole = payment\nfrom = Awaiting\nto = Paid\non = captured"
        val sources = Vector("action = judge-payment", "participant = capture-payment-capability").map { target =>
          _candidate_source(_section(_entry("revise", target + "\n" + fields)))
        }
        val baseline = _resource("candidate-admission-workflow.cml")

        When("ModelBuilder resolves semantic outcomes without changing candidate or local admission semantics")
        val original = _workflow(baseline)
        val workflows = sources.map(_workflow)

        Then("Judgment qualifies and the paired Admission and Action metadata are unchanged")
        workflows.foreach { workflow =>
          workflow.semanticOutcomes.head.actionIdentity shouldBe "judge-payment"
          workflow.semanticOutcomes.head.outcome shouldBe WorkflowSemanticOutcome.NeedsRevision
          workflow.compositeStateMachine shouldBe original.compositeStateMachine
          workflow.requiredOperations shouldBe original.requiredOperations
          workflow.compositeStateMachine.actions.last.kind shouldBe "ADMISSION"
          workflow.compositeStateMachine.actions.last.metadata.get.effectClass shouldBe CompositeStateMachineEffectClass.Local
          workflow.compositeStateMachine.actions.last.metadata.get.transactionRequirement shouldBe CompositeStateMachineTransactionRequirement.Required
        }
      }

      "reject deterministic ADMISSION and unknown or provider targets" in {
        Given("valid sources whose semantic declarations target Admission, missing names or provider identities")
        val admission = _candidate_source(_section(_entry("rejected", "action = admit-payment\noutcome = REJECTED\nrole = payment\nfrom = Awaiting\nto = Paid\non = captured")))
        val targets = Vector("action = Missing", "participant = Missing", "action = Codex", "participant = Human", "action = ReviewService.submitReview")
        val sources = Vector("ADMISSION" -> admission) ++ targets.map(target => target -> _source(_section(_entry("input", _input_fields.replace("action = ReviewChange", target)))))

        When("ModelBuilder checks target identity and kind")
        val errors = sources.map { case (name, source) => name -> intercept[RuntimeException] { _workflow(source) } }

        Then("only existing OPERATION or JUDGMENT Actions can own semantic outcomes")
        errors.foreach { case (name, error) => withClue(name + ": ") { error.getMessage should include("SEMANTIC-OUTCOME") } }
        errors.head._2.getMessage should include("ADMISSION is deterministic local semantics")
      }

      "reject ambiguous source Action and capability identities before outcome lowering" in {
        Given("real models with duplicate normalized Action or required capability names")
        val sources = Vector(
          "Action aliases" -> _fixture.replace("##### SupplyInput", "##### REVIEW_change"),
          "capability aliases" -> _fixture.replace("#### input-capability", "#### REVIEW_CAPABILITY")
        )

        When("ModelBuilder admits the canonical evidence used by semantic target resolution")
        val errors = sources.map { case (name, source) => name -> intercept[RuntimeException] { _workflow(source) } }

        Then("ambiguous target evidence fails instead of selecting an arbitrary declaration")
        errors.foreach { case (name, error) => withClue(name + ": ") { error.getMessage should include("unique") } }
      }

      "reject identical or conflicting canonical Action/outcome duplicates and alias entry identities" in {
        Given("duplicate entry identities and duplicate Action/outcome pairs through either target form")
        val duplicates = Vector(
          "entry aliases" -> _section(_entry("input-needed", _input_fields), _entry("INPUT_NEEDED", _rejected_fields)),
          "identical pair" -> _section(_entry("one", _input_fields), _entry("two", _input_fields)),
          "Action aliases" -> _section(_entry("one", _input_fields), _entry("two", _input_fields.replace("ReviewChange", "REVIEW_change"))),
          "Action/Participant aliases" -> _section(_entry("one", _input_fields), _entry("two", _input_fields.replace("action = ReviewChange", "participant = REVIEW_CAPABILITY"))),
          "conflicting edge" -> _section(_entry("one", _input_fields), _entry("two", _input_fields.replace("to = AwaitingInput\non = needsInput", "to = Revising\non = needsRevision")))
        )
        val sources = duplicates.map { case (name, metadata) => name -> _source(metadata) }

        When("ModelBuilder resolves complete declarations before checking semantic uniqueness")
        val errors = sources.map { case (name, source) => name -> intercept[RuntimeException] { _workflow(source) } }

        Then("each duplicate fails regardless of whether its declarations agree")
        errors.foreach { case (name, error) => withClue(name + ": ") { error.getMessage should include("unique") } }
      }
    }

    "closed scalar and container grammar" which {
      "accept only exact trimmed canonical outcome values" in {
        Given("each closed outcome value surrounded by whitespace on an otherwise valid declaration")
        val fields = Vector(_revision_fields, _input_fields, _rejected_fields)
        val sources = fields.map { value => _source(_section(_entry("only", value.replace("outcome = ", "outcome =   ") + "\n"))) }

        When("ModelBuilder admits values with trimming but no case folding")
        val outcomes = sources.map(source => _workflow(source).semanticOutcomes.head.outcome)

        Then("each canonical token maps to its exact immutable alternative")
        outcomes shouldBe Vector(WorkflowSemanticOutcome.NeedsRevision, WorkflowSemanticOutcome.NeedsInput, WorkflowSemanticOutcome.Rejected)
      }

      "reject aliases, technical classifications and unsupported result variants" in {
        Given("closed-value counterexamples including lower case, aliases, failures and inferred runtime results")
        val tokens = Vector("needs_input", "NeedsInput", "needs-input", "needs_revision", "NeedsRevision", "rejected", "Rejected",
          "Retryable", "Permanent", "RETRYABLE", "PERMANENT", "SUCCESS", "APPROVED", "FAILED", "RETRY", "CANCELLED", "TIMEOUT", "")
        val sources = tokens.map(token => token -> _source(_section(_entry("input", _input_fields.replace("NEEDS_INPUT", token)))))

        When("ModelBuilder matches each declaration against the exact closed vocabulary")
        val errors = sources.map { case (token, source) => token -> intercept[RuntimeException] { _workflow(source) } }

        Then("none becomes a default, alias or technical failure classification")
        errors.foreach { case (token, error) => withClue(token + ": ") { error.getMessage should include("SEMANTIC-OUTCOME") } }
      }

      "reject missing, empty and duplicate fields before a map can erase them" in {
        Given("every mandatory field removed, emptied or repeated using normalized aliases")
        val lines = _input_fields.linesIterator.toVector
        val sources = lines.flatMap { line =>
          val field = line.takeWhile(_ != '=').trim
          val alias = field.toUpperCase(Locale.ROOT).mkString("_")
          Vector(
            s"missing $field" -> _source(_section(_entry("input", lines.filterNot(_ == line).mkString("\n")))),
            s"empty $field" -> _source(_section(_entry("input", _input_fields.replace(line, s"$field = ")))),
            s"duplicate $field" -> _source(_section(_entry("input", _input_fields + "\n" + line))),
            s"alias duplicate $field" -> _source(_section(_entry("input", _input_fields + s"\n$alias = ${line.split("=", 2)(1).trim}")))
          )
        } ++ Vector(
          "both target forms" -> _source(_section(_entry("input", _input_fields + "\nparticipant = review-capability"))),
          "empty prohibited target" -> _source(_section(_entry("input", _input_fields + "\nparticipant = "))),
          "duplicate participant aliases" -> _source(_section(_entry("revision", _revision_fields + "\nPART_ICIPANT = review-capability")))
        )

        When("ModelBuilder reads duplicate-aware direct scalars and admits the complete field set")
        val errors = sources.map { case (name, source) => name -> intercept[RuntimeException] { _workflow(source) } }

        Then("every malformed field set is a syntax failure rather than last-value precedence")
        errors.foreach { case (name, error) => withClue(name + ": ") { error.getMessage should include("SEMANTIC-OUTCOME") } }
      }

      "reject unknown fields including termination, escalation, iteration and execution grammar" in {
        Given("unknown additions to otherwise complete semantic entries")
        val fields = Vector("unknown", "provider", "ai", "human", "script", "runtime", "binding", "retry", "maximum-attempts",
          "termination", "escalation", "iteration", "iteration-counter", "iteration-budget", "scheduler", "timeout", "classification")
        val sources = fields.map(field => field -> _source(_section(_entry("input", _input_fields + s"\n$field = external"))))

        When("ModelBuilder enforces the entry's complete scalar vocabulary")
        val errors = sources.map { case (field, source) => field -> intercept[RuntimeException] { _workflow(source) } }

        Then("no extension can introduce undeclared runtime or iteration semantics")
        errors.foreach { case (field, error) => withClue(field + ": ") { error.getMessage should not be empty } }
      }

      "reject empty containers, container fields, empty identities, duplicate sections and nested entries" in {
        Given("malformed structural shapes using canonical and separator-alias sections")
        val shapes = Vector(
          "empty container" -> "### SEMANTIC-OUTCOME\n",
          "container fields" -> ("### SEMANTIC-OUTCOME\nrole = review\n" + _entry("input", _input_fields)),
          "container outcome" -> ("### SEMANTIC-OUTCOME\noutcome = NEEDS_INPUT\n" + _entry("input", _input_fields)),
          "empty identity" -> _section(_entry("", _input_fields)),
          "duplicate section" -> (_section(_entry("one", _input_fields)) + _section(_entry("two", _rejected_fields)).replace("SEMANTIC-OUTCOME", "Semantic_Outcome")),
          "entry child" -> _section(_entry("input", _input_fields) + "##### child\nunknown = value\n"),
          "nested field" -> _section(_entry("input", _input_fields.replace("outcome = NEEDS_INPUT", "")) + "##### outcome\nNEEDS_INPUT\n"),
          "nested safety" -> _section(_entry("input", _input_fields) + "##### EXECUTION-SAFETY\n"),
          "nested lifecycle" -> _section(_entry("input", _input_fields) + "##### LIFECYCLE\n")
        )
        val sources = shapes.map { case (name, metadata) => name -> _source(metadata) }

        When("ModelBuilder treats the declaration as structural and checks its closed depth")
        val errors = sources.map { case (name, source) => name -> intercept[RuntimeException] { _workflow(source) } }

        Then("empty and malformed structures cannot disappear into narrative or inherited fields")
        errors.foreach { case (name, error) => withClue(name + ": ") { error.getMessage should not be empty } }
      }

      "require a valid CSM even for an empty outcome declaration" in {
        Given("Workflow definitions with absent or incomplete CSM and empty or populated outcome sections")
        val sources = Vector("", "### COMPOSITE-STATEMACHINE\n").flatMap { composite =>
          Vector("### SEMANTIC-OUTCOME\n", _section(_entry("input", _input_fields))).map { metadata =>
            s"# WORKFLOW\n## MissingStructure\nversion = v1\n$composite$metadata"
          }
        }

        When("ModelBuilder classifies structural Workflow metadata")
        val errors = sources.map(source => intercept[RuntimeException] { _workflow(source) })

        Then("the missing or malformed CSM is rejected before metadata can be ignored")
        errors.foreach(_.getMessage should include("COMPOSITE-STATEMACHINE"))
      }

      "normalize bounded generated key aliases to actual canonical target and graph values" in {
        Given("Locale.ROOT-safe case and separator variants of structural, field, target and edge keys")
        val aliases = for {
          upper <- Gen.oneOf(true, false)
          separator <- Gen.oneOf("", "_", "-")
        } yield (upper, separator)

        When("ScalaCheck submits real generated CML variants to ModelBuilder")
        val property = Prop.forAll(aliases) { case (upper, separator) =>
          def _alias_(value: String): String = {
            val normalized = value.toLowerCase(Locale.ROOT).filter(_.isLetterOrDigit)
            val letters = if (upper) normalized.toUpperCase(Locale.ROOT) else normalized
            letters.mkString(separator)
          }
          val fields = _input_fields.linesIterator.map { line =>
            val parts = line.split("=", 2)
            val name = parts.head.trim
            val value = parts(1).trim
            _alias_(name) + " = " + (if (name == "outcome") value else _alias_(value))
          }.mkString("\n")
          val source = _source(_section(_entry("generated", fields)).replace("SEMANTIC-OUTCOME", _alias_("SEMANTIC-OUTCOME")))
          val declaration = _workflow(source).semanticOutcomes.head
          declaration.actionIdentity == "ReviewChange" && declaration.outcome == WorkflowSemanticOutcome.NeedsInput &&
            declaration.target == WorkflowInvocationTarget.Action(_alias_("ReviewChange")) &&
            declaration.transition == WorkflowSemanticOutcomeTransition("review", "ReviewLifecycle", "Reviewing", "AwaitingInput", "needsInput")
        }
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24), property)

        Then("every generated alias retains actual canonical result and edge names")
        checked.passed shouldBe true
      }
    }

    "actual StateMachine graph references" which {
      "reject unknown roles, states, events, reversed edges and mismatched targets" in {
        Given("references that do not identify the declared constituent edge")
        val variants = Vector(
          "unknown role" -> _input_fields.replace("role = review", "role = absent"),
          "unknown from" -> _input_fields.replace("from = Reviewing", "from = absent"),
          "unknown to" -> _input_fields.replace("to = AwaitingInput", "to = absent"),
          "unknown event" -> _input_fields.replace("on = needsInput", "on = absent"),
          "wrong target" -> _input_fields.replace("to = AwaitingInput", "to = Approved"),
          "wrong event" -> _input_fields.replace("on = needsInput", "on = approved"),
          "reversed edge" -> _input_fields.replace("from = Reviewing", "from = AwaitingInput").replace("to = AwaitingInput", "to = Reviewing"),
          "nonexistent edge" -> _input_fields.replace("from = Reviewing", "from = Rejected")
        )
        val sources = variants.map { case (name, fields) => name -> _source(_section(_entry("input", fields))) }

        When("ModelBuilder resolves actual state names and source-state transition/event pairs")
        val errors = sources.map { case (name, source) => name -> intercept[RuntimeException] { _workflow(source) } }

        Then("every inconsistent reference fails source admission")
        errors.foreach { case (name, error) => withClue(name + ": ") { error.getMessage should include("SEMANTIC-OUTCOME") } }
      }

      "reject another real role's event when its declared constituent has no matching edge" in {
        Given("two valid constituent graphs whose events differ")
        val source = _source(_section(_entry("input", _input_fields)))
          .replace("#### STATE", "##### audit\nstate-machine = AuditLifecycle\n\n#### STATE")
          .replace("when = review.", "when = audit.Ready, review.")
          .replace("\nreview.Reviewing\n", "\nreview.Reviewing, audit.Ready\n")
          .replace("# SERVICE", "## AuditLifecycle\n### State\n#### Ready\n##### Transition\nto = Ready\non = needsInput\n\n# SERVICE")
          .replace("role = review\nfrom = Reviewing\nto = AwaitingInput\non = needsInput", "role = audit\nfrom = Ready\nto = Ready\non = needsRevision")

        When("ModelBuilder checks the declared role's actual source-state edge")
        val error = intercept[RuntimeException] { _workflow(source) }

        Then("the other role's event cannot satisfy the outcome reference")
        error.getMessage should include("SEMANTIC-OUTCOME")
        error.getMessage should include("exactly one declared constituent transition")
      }

      "require the actual edge rather than a same-named event, composite derivation or attached Action" in {
        Given("a declared event and derived/constituent Action mappings that do not create the referenced edge")
        val baseline = _source(_section(_entry("input", _input_fields.replace("on = needsInput", "on = phantom"))))
        val eventonly = baseline.replace("# SERVICE", "### Event\n#### phantom\n\n# SERVICE")
        val derived = baseline.replace("### REQUIRED-OPERATION", "#### DERIVED-ACTION\n##### inferred\nfrom = Reviewing\nto = AwaitingInput\naction = ReviewChange\n\n### REQUIRED-OPERATION")
        val attached = baseline.replace("### REQUIRED-OPERATION", "#### CONSTITUENT-ACTION\n##### actual-input\nrole = review\nfrom = Reviewing\nto = AwaitingInput\non = needsInput\nplacement = transition\naction = ReviewChange\n\n### REQUIRED-OPERATION")
        val sources = Vector("same-named event" -> eventonly, "derived Action" -> derived, "constituent Action" -> attached)

        When("ModelBuilder resolves semantic declarations against actual StateMachine transitions only")
        val errors = sources.map { case (name, source) => name -> intercept[RuntimeException] { _workflow(source) } }

        Then("declarations and action mappings cannot manufacture the missing event/edge pair")
        errors.foreach { case (name, error) => withClue(name + ": ") { error.getMessage should include("SEMANTIC-OUTCOME") } }
      }

      "retain a declared self-loop without requiring a transition Action" in {
        Given("a real self-loop with no attached Action and an outcome referring to it")
        val source = _source(_section(_entry("again", _revision_fields.replace("to = Revising", "to = Reviewing"))))
          .replace("to = Revising\non = needsRevision", "to = Reviewing\non = needsRevision")

        When("ModelBuilder admits the explicit source-state self-loop")
        val workflow = _workflow(source)

        Then("equal from/to is preserved and no Action placement is synthesized")
        workflow.semanticOutcomes.head.transition shouldBe WorkflowSemanticOutcomeTransition("review", "ReviewLifecycle", "Reviewing", "Reviewing", "needsRevision")
        workflow.compositeStateMachine.constituentActions shouldBe Vector.empty
        workflow.compositeStateMachine.derivedActions shouldBe Vector.empty
      }

      "reject an ambiguous matching edge when the actual source model retains both transitions" in {
        Given("two explicitly declared source transitions matching the same event and target")
        val source = _source(_section(_entry("input", _input_fields)))
          .replace("to = AwaitingInput\non = needsInput\n\n##### Transition", "to = AwaitingInput\non = needsInput\n\n##### Transition\nto = AwaitingInput\non = needsInput\n\n##### Transition")
        val model = _model(source)

        When("both extraction routes resolve the actual model's matching transition collection")
        val machine = Modeler._select_state_machine(model, "ReviewLifecycle").get
        val state = machine.rule.states.find(_.name == "Reviewing").get
        val count = (state.transitions.call ++ state.transitions.global).count { transition =>
          transition.to == NameTransitionTo("AwaitingInput") && transition.getEventName.contains("needsInput")
        }
        if (count < 2) cancel("The actual StateMachine model coalesces duplicate transitions; ambiguity is not exposed by this model.")
        val error = intercept[RuntimeException] { CompositeStateMachineCml.workflowDefinitions(model) }

        Then("multiple actual matches cannot be reduced to an arbitrary edge")
        count shouldBe 2
        error.getMessage should include("exactly one declared constituent transition")
      }

      "retain Entity-qualified references through real existing model resolution" in {
        Given("a real Entity-owned ReviewLifecycle referenced by its qualified source name")
        val source = _entity_source(_fixture)

        When("ModelBuilder resolves the Entity-contained machine and admits semantic outcome edges")
        val model = _model(source)
        val resolved = Modeler._select_state_machine(model, "ReviewRecord.ReviewLifecycle").get
        Modeler.ModelBuilder(model)
        val workflow = CompositeStateMachineCml.workflowDefinitions(model).head

        Then("outcome edges preserve the qualified reference and actual names without changing public CSM values")
        resolved.name shouldBe "ReviewLifecycle"
        workflow.compositeStateMachine.constituents.head.stateMachine shouldBe CompositeStateMachineReference("ReviewLifecycle")
        workflow.semanticOutcomes.map(_.transition.stateMachine).distinct shouldBe Vector("ReviewRecord.ReviewLifecycle")
        workflow.semanticOutcomes.map(_.transition.on) shouldBe Vector("needsRevision", "needsInput", "rejected")
      }

      "preserve input and revision return edges and approval without manufacturing outcomes" in {
        Given("the complete graph including ordinary return, approval and rejection edges")
        val model = _model(_fixture)

        When("ModelBuilder normalizes the graph and outcome metadata independently")
        Modeler.ModelBuilder(model)
        val machine = Modeler._select_state_machine(model, "ReviewLifecycle").get
        val edges = machine.rule.states.toVector.flatMap { state =>
          (state.transitions.call ++ state.transitions.global).flatMap { transition => transition.to match {
            case NameTransitionTo(to) => transition.getEventName.map(on => (state.name, to, on))
            case _ => None
          } }
        }
        val workflow = CompositeStateMachineCml.workflowDefinitions(model).head

        Then("all six ordinary edges survive and only the three explicit review outcomes are retained")
        edges should contain allOf (("Reviewing", "AwaitingInput", "needsInput"), ("Reviewing", "Revising", "needsRevision"),
          ("Reviewing", "Rejected", "rejected"), ("Reviewing", "Approved", "approved"),
          ("AwaitingInput", "Reviewing", "inputSupplied"), ("Revising", "Reviewing", "revised"))
        workflow.semanticOutcomes.size shouldBe 3
        workflow.semanticOutcomes.map(_.outcome.canonicalValue) should not contain "APPROVED"
      }
    }

    "structural ownership and opaque identity names" which {
      "reject misplaced structural containers across roots and existing declaration positions on both extraction routes" in {
        Given("SEMANTIC-OUTCOME outside its direct Workflow-definition position")
        val anchors = Vector(
          "document root" -> ("# WORKFLOW", "# SEMANTIC-OUTCOME\n\n# WORKFLOW"),
          "Workflow root" -> ("## ReviewWorkflow", "## SEMANTIC-OUTCOME\n\n## ReviewWorkflow"),
          "CSM child" -> ("#### STATE", "#### SEMANTIC-OUTCOME\n\n#### STATE"),
          "constituent child" -> ("subject-type = ReviewContext", "subject-type = ReviewContext\n###### SEMANTIC-OUTCOME"),
          "CSM State child" -> ("##### Reviewing", "##### Reviewing\n###### SEMANTIC-OUTCOME"),
          "derivation child" -> ("when = review.Reviewing", "when = review.Reviewing\n###### SEMANTIC-OUTCOME"),
          "Action child" -> ("idempotency = NOT_REQUIRED", "idempotency = NOT_REQUIRED\n###### SEMANTIC-OUTCOME"),
          "SPI child" -> ("action = SupplyInput", "action = SupplyInput\n##### SEMANTIC-OUTCOME"),
          "API child" -> ("operation = ReviewService.beginReview", "operation = ReviewService.beginReview\n##### SEMANTIC-OUTCOME"),
          "invocation child" -> ("execution-timeout-millis = 30000", "execution-timeout-millis = 30000\n##### SEMANTIC-OUTCOME"),
          "lifecycle child" -> ("cancellation = COOPERATIVE", "cancellation = COOPERATIVE\n#### SEMANTIC-OUTCOME"),
          "wait child" -> ("signal = review.input", "signal = review.input\n##### SEMANTIC-OUTCOME"),
          "failure-rule child" -> ("classification = RETRYABLE", "classification = RETRYABLE\n###### SEMANTIC-OUTCOME"),
          "safety child" -> ("duplicate-protection = NOT_REQUIRED", "duplicate-protection = NOT_REQUIRED\n#### SEMANTIC-OUTCOME"),
          "Service structural child" -> ("## ReviewService", "## ReviewService\n### SEMANTIC-OUTCOME"),
          "Operation child" -> ("#### supplyInput", "#### supplyInput\n##### SEMANTIC-OUTCOME"),
          "StateMachine child" -> ("### State", "### SEMANTIC-OUTCOME\n\n### State"),
          "state child" -> ("#### Approved", "#### Approved\n##### SEMANTIC-OUTCOME"),
          "transition child" -> ("on = approved", "on = approved\n###### SEMANTIC-OUTCOME"),
          "ordinary Workflow child" -> ("### INVOCATION-POLICY", "### OTHER\n#### SEMANTIC-OUTCOME\n\n### INVOCATION-POLICY")
        )
        val sources = anchors.map { case (name, (before, after)) => name -> _source().replace(before, after) } :+
          ("standalone CSM" -> "# COMPOSITE-STATEMACHINE\n## Standalone\n### SEMANTIC-OUTCOME\n")
        val models = sources.map { case (name, source) => name -> _model(source) }

        When("both CSM and Workflow extraction routes perform structural ownership admission")
        val errors = models.flatMap { case (name, model) => Vector(
          name -> intercept[RuntimeException] { CompositeStateMachineCml.definitions(model) },
          name -> intercept[RuntimeException] { CompositeStateMachineCml.workflowDefinitions(model) }
        ) }

        Then("every misplaced container fails before either extraction can silently discard it")
        errors.size shouldBe sources.size * 2
        errors.foreach { case (name, error) => withClue(name + ": ") { error.getMessage should include("SEMANTIC-OUTCOME") } }
      }

      "reserve generic outcome only in Workflow and CSM contexts" in {
        Given("unrelated domain outcome fields and the same field in unowned Workflow/CSM positions")
        val legal = _source().replace("## ReviewContext", "## ReviewContext\noutcome = domain-value").replace("## ReviewService", "## ReviewService\noutcome = service-value")
        val anchors = Vector("# WORKFLOW", "version = workflow-semantic-outcome-source", "### COMPOSITE-STATEMACHINE", "#### STATE", "##### Reviewing", "##### ReviewChange",
          "### REQUIRED-OPERATION", "#### review-capability", "### OPERATION", "operation = ReviewService.beginReview", "#### review-control", "### LIFECYCLE", "#### awaiting-input", "#### review-failure", "##### transient.review", "### EXECUTION-SAFETY")
        val illegal = anchors.map(anchor => anchor -> _source().replace(anchor, anchor + "\noutcome = NEEDS_INPUT\n")) :+
          ("standalone CSM" -> "# COMPOSITE-STATEMACHINE\noutcome = NEEDS_INPUT\n")
        val models = illegal.map { case (name, source) => name -> _model(source) }

        When("real source admission and both extraction routes apply contextual field ownership")
        val workflow = _workflow(legal)
        val errors = models.flatMap { case (name, model) => Vector(
          name -> intercept[RuntimeException] { CompositeStateMachineCml.definitions(model) },
          name -> intercept[RuntimeException] { CompositeStateMachineCml.workflowDefinitions(model) }
        ) }

        Then("unrelated generic outcome remains legal and every unowned contextual occurrence fails")
        workflow.semanticOutcomes shouldBe Vector.empty
        workflow.compositeStateMachine.actions.head.identity shouldBe "ReviewChange"
        errors.foreach { case (name, error) => withClue(name + ": ") { error.getMessage should include("outcome") } }
      }

      "preserve SemanticOutcome as every established opaque identity" in {
        Given("source variants naming existing definitions and entries SemanticOutcome")
        val identity = "SemanticOutcome"
        val baseline = _fixture
        val variants = Vector(
          "Workflow with CSM" -> baseline.replace("## ReviewWorkflow", s"## $identity"),
          "Workflow with VERSION" -> _source().replace("## ReviewWorkflow", s"## $identity"),
          "StateMachine" -> baseline.replace("ReviewLifecycle", identity),
          "Service" -> baseline.replace("ReviewService", identity),
          "constituent State" -> baseline.replace("Reviewing", identity),
          "constituent" -> baseline.replace("##### review\n", s"##### $identity\n").replace("review.subject", s"$identity.subject").replace("when = review.", s"when = $identity.").replace("\nreview.Reviewing\n", s"\n$identity.Reviewing\n").replace("role = review", s"role = $identity"),
          "CSM State" -> baseline.replace("##### Reviewing", s"##### $identity").replace("state = Reviewing", s"state = $identity"),
          "Action" -> baseline.replace("ReviewChange", identity),
          "derivation" -> baseline.replace("##### reviewing", s"##### $identity"),
          "SPI" -> baseline.replace("review-capability", identity),
          "API and Operation" -> baseline.replace("beginReview", identity),
          "invocation" -> baseline.replace("#### review-control", s"#### $identity"),
          "failure policy" -> baseline.replace("#### review-failure", s"#### $identity"),
          "failure rule" -> baseline.replace("##### transient.review", s"##### $identity"),
          "wait" -> baseline.replace("#### awaiting-input", s"#### $identity"),
          "outcome" -> baseline.replace("#### revision-needed", s"#### $identity")
        )

        When("ModelBuilder recognizes the original identity positions")
        val workflows = variants.map { case (name, source) => name -> _workflow(source) }

        Then("the opaque name survives without becoming a structural declaration")
        workflows.foreach { case (name, workflow) =>
          val actual = name match {
            case "Workflow with CSM" | "Workflow with VERSION" => workflow.identity
            case "StateMachine" => workflow.compositeStateMachine.constituents.head.stateMachine.name
            case "Service" => workflow.providedOperations.head.operation.service
            case "constituent State" => workflow.compositeStateMachine.constituents.head.states.head
            case "constituent" => workflow.compositeStateMachine.constituents.head.role
            case "CSM State" => workflow.compositeStateMachine.states.head.name
            case "Action" => workflow.compositeStateMachine.actions.head.identity
            case "derivation" => workflow.compositeStateMachine.derivations.head.identity
            case "SPI" => workflow.requiredOperations.head.capability
            case "API and Operation" => workflow.providedOperations.head.operation.name
            case "invocation" => workflow.invocationPolicies.head.identity
            case "failure policy" => workflow.failurePolicies.head.identity
            case "failure rule" => workflow.failurePolicies.head.rules.head.identity
            case "wait" => workflow.waits.head.identity
            case "outcome" => workflow.semanticOutcomes.head.identity
          }
          withClue(name + ": ") { actual shouldBe identity }
        }
      }

      "preserve standalone CSM and VERSION-shaped Workflow identity names" in {
        Given("an existing standalone CSM definition and a VERSION-shaped Workflow with opaque SemanticOutcome identity")
        val standalone = _source().replace("# WORKFLOW\n\n## ReviewWorkflow\n\nversion = workflow-semantic-outcome-source\n\n### COMPOSITE-STATEMACHINE", "# COMPOSITE-STATEMACHINE\n\n## SemanticOutcome")
        val csmend = standalone.indexOf("### REQUIRED-OPERATION")
        val machine = standalone.indexOf("# STATE-MACHINE", csmend)
        val csmsource = standalone.substring(0, csmend) + standalone.substring(machine)
        val workflowmodel = _model("# WORKFLOW\n## SemanticOutcome\nversion = opaque-only\n")
        val csmmodel = _model(csmsource)

        When("both extraction routes interpret definition positions before structural spelling")
        val definitions = CompositeStateMachineCml.definitions(csmmodel)
        val workflows = CompositeStateMachineCml.workflowDefinitions(workflowmodel)
        val other = CompositeStateMachineCml.definitions(workflowmodel)

        Then("the standalone definition is retained and the narrative Workflow identity is not rejected")
        definitions.head.identity shouldBe "SemanticOutcome"
        workflows shouldBe Vector.empty
        other shouldBe Vector.empty
      }

      Vector("Lifecycle", "Wait", "FailurePolicy", "ExecutionSafety").foreach { identity =>
        s"retain $identity as an outcome entry while keeping its prior ownership checks" in {
          Given(s"a complete declaration whose opaque entry identity is $identity")
          val source = _source(_section(_entry(identity, _input_fields)))

          When("ModelBuilder applies outcome and legacy safety/lifecycle ownership together")
          val declaration = _workflow(source).semanticOutcomes.head

          Then("the entry name remains an identity and its exact outcome and edge survive")
          declaration.identity shouldBe identity
          declaration.outcome shouldBe WorkflowSemanticOutcome.NeedsInput
          declaration.transition.on shouldBe "needsInput"
        }
      }

      "retain legacy reserved-field and nested-metadata rejection inside outcome entries" in {
        Given("reserved invocation, lifecycle, failure and safety fields or nested metadata inside an outcome entry")
        val fields = Vector("maximum-attempts", "deadline-millis", "delay-millis", "default-classification", "logical-execution-identity-ref", "duplicate-protection", "idempotency-key-ref")
        val sources = fields.map(field => field -> _source(_section(_entry("input", _input_fields + s"\n$field = 1")))) ++
          Vector("LIFECYCLE", "WAIT", "FAILURE-POLICY", "EXECUTION-SAFETY", "INVOCATION-POLICY").map { name =>
            name -> _source(_section(_entry("input", _input_fields) + s"##### $name\n"))
          }

        When("ModelBuilder checks existing owners as well as closed outcome entry shape")
        val errors = sources.map { case (name, source) => name -> intercept[RuntimeException] { _workflow(source) } }

        Then("recognizing an outcome identity container grants no exemption to fields or deeper structure")
        errors.foreach { case (name, error) => withClue(name + ": ") { error.getMessage should not be empty } }
      }
    }
  }

  private def _fixture: String = _resource("workflow-semantic-outcome.cml")

  private def _resource(name: String): String = {
    val stream = getClass.getResourceAsStream(s"/modeler/$name")
    val source = scala.io.Source.fromInputStream(stream, "UTF-8")
    try { source.mkString } finally { source.close() }
  }

  private def _source(metadata: String = ""): String = {
    val source = _fixture
    val start = source.indexOf("### SEMANTIC-OUTCOME")
    val end = source.indexOf("# STATE-MACHINE", start)
    source.substring(0, start) + metadata + "\n" + source.substring(end)
  }

  private def _section(entries: String*): String = "### SEMANTIC-OUTCOME\n\n" + entries.mkString("\n") + "\n"

  private def _entry(identity: String, fields: String): String = s"#### $identity\n$fields\n\n"

  private def _without_invocation(source: String): String = {
    val start = source.indexOf("### INVOCATION-POLICY")
    val end = source.indexOf("### LIFECYCLE", start)
    source.substring(0, start) + source.substring(end)
  }

  private def _candidate_source(metadata: String): String =
    _resource("candidate-admission-workflow.cml").replace("# STATE-MACHINE", metadata + "\n# STATE-MACHINE")

  private def _entity_source(source: String): String = {
    val start = source.indexOf("# STATE-MACHINE")
    val end = source.indexOf("# SERVICE", start)
    val body = source.substring(start, end).linesIterator.drop(1).map { line =>
      if (line.startsWith("#")) "##" + line else line
    }.mkString("\n")
    val entity = "# ENTITY\n\n## ReviewRecord\n\n### Attribute\n| name | type | multiplicity |\n|------+------|--------------|\n| id | entityid | 1 |\n\n### StateMachine\n" + body + "\n\n"
    (source.substring(0, start) + entity + source.substring(end)).replace("state-machine = ReviewLifecycle", "state-machine = ReviewRecord.ReviewLifecycle")
  }

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
