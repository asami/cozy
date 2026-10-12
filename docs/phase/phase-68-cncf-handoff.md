# Phase 68: CNCF83 Failure Policy and Execution-Safety Handoff

Semantic role: Engineering handoff (non-normative)
Current status: COMPLETE at the Cozy producer boundary. All three Steps and the sole comprehensive P68-FULL-REVIEW-001 revision1 for P68-PLAN-E1 passed, with zero CPB/HYG/DEV. P68-FINAL-VAL-001 passed: 2,587 succeeded, 0 failed, 9 canceled, 0 ignored, 0 pending; 182 suites, 0 aborted; SBT/wrapper exit0 and `lock=released`. The local release commit containing this record completes ordinary Phase68 closure. CNCF83 owns receiver integration and runtime enforcement.
Owner: Cozy owns source declarations, immutable IR and generated producer ABI; CNCF Phase83 owns receiver integration and runtime behavior.
Update rule: retain the source and ABI contracts as authorities; update evidence/status only from actual parent-owned validation, review and native commits.
Completion basis: three accepted Cozy producer Steps, exactly one comprehensive Phase review, final full Cozy suite and distinct local release commit. CNCF completion/full validation does not gate Cozy.
Updated: 2026-10-03

Navigation: [Phase 68](phase-68.md), [checklist](phase-68-checklist.md),
[normative source contract](../spec/workflow-failure-execution-safety-source-contract.md),
[source lowering design](../design/workflow-failure-execution-safety-source-lowering.md),
[normative ABI contract](../spec/workflow-failure-execution-safety-abi-contract.md),
[ABI lowering design](../design/workflow-failure-execution-safety-abi-lowering.md),
[tracked complete CML fixture](../../src/test/resources/modeler/workflow-failure-execution-safety-policy.cml),
[source executable specification](../../src/test/scala/cozy/modeler/WorkflowExecutionSafetyCmlSpec.scala),
[producer executable specification](../../src/test/scala/cozy/modeler/WorkflowExecutionSafetyAbiGenerationSpec.scala),
[ABI generator](../../src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala),
[Cozy public entrypoint](../../src/main/scala/cozy/Cozy.scala),
[independent original v3 provenance](../../src/test/resources/modeler/workflow-execution-safety-v3-baseline/provenance.md),
[old v1](../spec/statemachine-workflow-generated-abi-contract.md),
[v2](../spec/workflow-retry-timeout-abi-contract.md),
[v3](../spec/workflow-scheduling-lifecycle-abi-contract.md),
[CNCF Phase83](../../../goldenport-cncf/docs/phase/phase-83.md),
[current named receiver](../../../goldenport-cncf/src/main/scala/org/goldenport/cncf/workflow/WorkflowInvocationPolicyAbi.scala).

## Accepted producer evidence and Phase closure

| Step | Native local commit | Accepted native evidence |
| --- | --- | --- |
| S68.1 SOURCE | `0a8062750a390343f7339fa4a1af8c02a6e4a8fd` | P68-S681-VAL-005: source353/353 across six suites; P68-S681-REVIEW-001 revision1 PASS. |
| S68.2 ABI | `3e2c7605f343e7eef75bf8166f98f1c6e0d89ac4` | P68-S682-VAL-002: producer92/92 across seven suites; P68-S682-VAL-003: actual generated Scala3.3.8 normal/value/mixed 4/4/10 compilation, all eighteen .class/.tasty outputs and three separate analyses; P68-S682-REVIEW-001 revision1 PASS. |

S68.3 HANDOFF is accepted at `0180e8fd4ad0979543f8cb872577ca286380fe21` after P68-S683-STATIC-VALIDATION-001 and P68-S683-REVIEW-001 revision1 PASS.

Both producer Steps and handoff review had zero CPB/HYG/DEV. Native validation reported SBT/wrapper exit0
and `lock=released`, with zero aborted suites and zero failed, canceled,
ignored or pending tests. These accepted review/validation witnesses belong to
the producer Steps. Reproduction instructions below are instructions only,
not additional executed receipts. Actual final full validation is recorded separately below.
S68.3 static verification and independent medium Step review passed; native
Step commit `0180e8fd4ad0979543f8cb872577ca286380fe21` records handoff acceptance.
The sole comprehensive review P68-FULL-REVIEW-001 revision1 passed. P68-FINAL-VAL-001 passed: 2,587 succeeded, 0 failed, 9 canceled, 0 ignored, 0 pending; 182 suites, 0 aborted; SBT/wrapper exit0 and `lock=released`.
The nine cancellations retain the original four opt-in Docker Remotion and five
CV/SP ownership scenarios; their specification bytes are unchanged. The
containing distinct local release commit closes Cozy Phase68. CNCF runtime
acceptance remains the recipient responsibility.

The unchanged Cozy source build is version `0.3.3-SNAPSHOT`, SBT1.9.7 and
Scala2.12.18; generated target Scala is3.3.8. Native tests/compilation used Amazon
Java25.0.4. Repository release17 configuration is a source floor; no independent
runtime-JDK17 pass is established. No publication is implied.

## Complete fixture and actual correlations

The tracked fixture is a complete Service/Operation, Value, StateMachine and
Workflow model. Its Workflow is `SafetyWorkflow`, version `workflow-safety-v4`.
The following excerpt carries actual values and source line correlations from
the captured normal generated sidecar, retaining field/declaration order:

```json
{
  "identity": "SafetyWorkflow",
  "version": "workflow-safety-v4",
  "source": {
    "root": {
      "line": 25
    },
    "definition": {
      "line": 27
    }
  },
  "invocationPolicies": [
    {
      "identity": "capture-control",
      "target": {
        "kind": "ACTION",
        "identity": "capture-payment"
      },
      "actionIdentity": "capture-payment",
      "retry": {
        "maximumAttempts": 3,
        "fixedRetryDelayMillis": 250
      },
      "executionTimeoutMillis": 30000,
      "source": {
        "line": 86
      }
    }
  ],
  "lifecyclePolicy": {
    "deadlineMillis": 60000,
    "cancellation": "COOPERATIVE",
    "source": {
      "line": 93
    }
  },
  "waits": [
    {
      "identity": "payment-timer",
      "state": "Pending",
      "stateIdentity": "Pending",
      "trigger": {
        "kind": "TIMER",
        "delayMillis": 250
      },
      "source": {
        "line": 100
      }
    },
    {
      "identity": "completion-signal",
      "state": "Complete",
      "stateIdentity": "Complete",
      "trigger": {
        "kind": "SIGNAL",
        "identity": "payment.completed"
      },
      "source": {
        "line": 105
      }
    }
  ],
  "failurePolicies": [
    {
      "identity": "capture-failure",
      "target": {
        "kind": "PARTICIPANT",
        "identity": "capture-payment-capability"
      },
      "actionIdentity": "capture-payment",
      "defaultClassification": "PERMANENT",
      "rules": [
        {
          "identity": "transient.payment",
          "classification": "RETRYABLE",
          "source": {
            "line": 117
          }
        },
        {
          "identity": "payment.rejected",
          "classification": "PERMANENT",
          "source": {
            "line": 121
          }
        }
      ],
      "source": {
        "line": 112
      }
    }
  ],
  "executionSafetyPolicy": {
    "logicalExecutionIdentityRef": "order-run",
    "duplicateProtection": {
      "kind": "REQUIRED",
      "idempotencyKeyRef": "order-start"
    },
    "source": {
      "line": 125
    }
  }
}
```

The failure policy declares PARTICIPANT `capture-payment-capability` separately
from canonical Action `capture-payment`; default PERMANENT is explicit. Ordered
rules are `transient.payment` RETRYABLE then `payment.rejected` PERMANENT.
Workflow root/definition lines are25/27; invocation policy86, lifecycle93,
Timer100, Signal105, failure policy112, rules117/121 and safety125 are actual
heading correlations, not invented locations.

`order-run` and `order-start` are symbolic runtime-supplied logical identity/key
references. The separate Action has `effect = EXTERNAL`,
`transaction = OUTSIDE_UNIT_OF_WORK`, `idempotency = REQUIRED`,
`idempotency-key = payment-action-key` and
`compensation-handler = payment.refund`. Action idempotency, compensation and
UnitOfWork metadata remain independent from Workflow admission protection.
Invocation maximum-attempts3, fixed retry delay250 and timeout30000 remain
independent of failure classification. Lifecycle deadline60000/COOPERATIVE and
Timer250/Signal `payment.completed` likewise remain independent declarations.

## Public producer reproduction

From the Cozy repository root, generate each public route in order:

```sh
sbt --batch 'runMain cozy.Cozy modeler-scala src/test/resources/modeler/workflow-failure-execution-safety-policy.cml --save target/phase68-handoff/normal'
sbt --batch 'runMain cozy.Cozy modeler-scala-value src/test/resources/modeler/workflow-failure-execution-safety-policy.cml --save target/phase68-handoff/value'
```

Both output roots produce four identical ABI products, relative to each root:

| Product | Relative path |
| --- | --- |
| Sidecar | `target/cozy/statemachine-workflow-abi.json` |
| Shared ABI | `target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala` |
| Direct bootstrap | `target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala` |
| Actual member | `target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/SafetyWorkflowStateMachineWorkflow1.scala` |

The public handoff roots differ from the executable specification roots below.
All commands are portable logical SBT notation. Codex execution requires the
registered `cncf_command_runner` and shared `run-sbt-serial.sh`, first scoped
access to normal boot/dependency/local-artifact caches, and native
`lock=released` before another top-level invocation. Neither the editing worker
nor this documentary Step runs these commands.

## Unchanged authoritative ABI summary

This summary projects the linked normative contracts without redefining them.
Collection-wide selection is v4 when any `failurePolicies` is nonempty or
`executionSafetyPolicy` is Some; otherwise exact v3 lifecycle/waits, v2
invocation, v1 fallback (including empty input) remain. All descriptors, shared
ABI VERSION, root schema and direct bootstrap agree. V4 includes prior
invocation/lifecycle/waits. Failure-only, safety-only and default-only policies
are valid and manufacture no other declarations.

`WorkflowDescriptor` appends `failurePolicies: Vector[FailurePolicy] = Vector.empty`
and `executionSafetyPolicy: Option[ExecutionSafetyPolicy] = None` after waits.
Old7/8/10 source constructor arities remain compatible; no binary or unapply
compatibility is promised. The closed immutable types are:

```scala
sealed trait FailureClassification { def canonicalValue: String }
object FailureClassification {
  case object Retryable extends FailureClassification { val canonicalValue: String = "RETRYABLE" }
  case object Permanent extends FailureClassification { val canonicalValue: String = "PERMANENT" }
}
final case class FailureRule(identity: String, classification: FailureClassification, source: SourceIdentity)
final case class FailurePolicy(identity: String, target: InvocationTarget, actionIdentity: String, defaultClassification: FailureClassification, rules: Vector[FailureRule], source: SourceIdentity)
sealed trait DuplicateProtection { def canonicalValue: String }
object DuplicateProtection {
  case object NotRequired extends DuplicateProtection { val canonicalValue: String = "NOT_REQUIRED" }
  final case class Required(idempotencyKeyRef: String) extends DuplicateProtection { val canonicalValue: String = "REQUIRED" }
}
final case class ExecutionSafetyPolicy(logicalExecutionIdentityRef: String, duplicateProtection: DuplicateProtection, source: SourceIdentity)
```

JSON root order is `schemaVersion,workflows`; Workflow order is
`identity,version,source,states,actions,requiredSpi,invocationPolicies,lifecyclePolicy,waits,failurePolicies,executionSafetyPolicy`.
Failure policy order is `identity,target,actionIdentity,defaultClassification,rules,source`;
target order `kind,identity`; rule order `identity,classification,source`.
Safety order is `logicalExecutionIdentityRef,duplicateProtection,source`;
protection order `kind,idempotencyKeyRef`. REQUIRED has its exact key ref;
NOT_REQUIRED emits JSON null and has no Scala key. Absent safety is None/null;
policies and default-only rules are empty vectors/arrays. Inherited v4 absence
is invocation `[]`, lifecycle null and waits `[]`.

Declared target kind/spelling remains separate from canonical Action. Opaque
failure identities and refs remain exact and case-sensitive; source order and
actual source locations or None/null are preserved. No key or rule is generated.
Inherited Longs use exact decimal JSON integers and Scala L literals, including
zero/Long.MaxValue; no Double conversion/coercion. Established quote, backslash,
newline, carriage-return, tab, ISO-control and Unicode escaping preserves decoded
strings. No source field, runtime API, backoff/jitter or ownership contract is added.

## Focused behavior and actual Scala3 compiler reproduction

First run the accepted focused behavior command. It creates actual normal/value/
mixed products and authored typed consumer probes:

```sh
sbt --batch 'testOnly cozy.modeler.WorkflowExecutionSafetyAbiGenerationSpec cozy.modeler.WorkflowExecutionSafetyCmlSpec cozy.modeler.WorkflowLifecycleAbiGenerationSpec cozy.modeler.WorkflowInvocationPolicyAbiGenerationSpec cozy.modeler.StateMachineWorkflowAbiGenerationSpec cozy.modeler.StateMachineProvidedApiAbiGenerationSpec cozy.modeler.CompositeStateMachineActionProgramSpec'
```

All sources below reside in the listed root's
`target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow` directory.
These are the exact accepted4/4/10 source groups (eighteen total):

| Group | Actual output root | Exact source filename |
| --- | --- | --- |
| normal (4 sources) | `target/test-generated/workflow-execution-safety-normal` | `StateMachineWorkflowAbi.scala` |
| normal (4 sources) | `target/test-generated/workflow-execution-safety-normal` | `StateMachineWorkflowComponentFactoryBootstrap.scala` |
| normal (4 sources) | `target/test-generated/workflow-execution-safety-normal` | `SafetyWorkflowStateMachineWorkflow1.scala` |
| normal (4 sources) | `target/test-generated/workflow-execution-safety-normal` | `WorkflowExecutionSafetyAbiConsumerProbe.scala` |
| value (4 sources) | `target/test-generated/workflow-execution-safety-value` | `StateMachineWorkflowAbi.scala` |
| value (4 sources) | `target/test-generated/workflow-execution-safety-value` | `StateMachineWorkflowComponentFactoryBootstrap.scala` |
| value (4 sources) | `target/test-generated/workflow-execution-safety-value` | `SafetyWorkflowStateMachineWorkflow1.scala` |
| value (4 sources) | `target/test-generated/workflow-execution-safety-value` | `WorkflowExecutionSafetyAbiConsumerProbe.scala` |
| mixed (10 sources) | `target/test-generated/workflow-execution-safety-compile` | `StateMachineWorkflowAbi.scala` |
| mixed (10 sources) | `target/test-generated/workflow-execution-safety-compile` | `StateMachineWorkflowComponentFactoryBootstrap.scala` |
| mixed (10 sources) | `target/test-generated/workflow-execution-safety-compile` | `LegacyWorkflowStateMachineWorkflow1.scala` |
| mixed (10 sources) | `target/test-generated/workflow-execution-safety-compile` | `PolicyWorkflowStateMachineWorkflow2.scala` |
| mixed (10 sources) | `target/test-generated/workflow-execution-safety-compile` | `LifecycleWorkflowStateMachineWorkflow3.scala` |
| mixed (10 sources) | `target/test-generated/workflow-execution-safety-compile` | `SafetyWorkflowStateMachineWorkflow4.scala` |
| mixed (10 sources) | `target/test-generated/workflow-execution-safety-compile` | `FailureOnlyWorkflowStateMachineWorkflow5.scala` |
| mixed (10 sources) | `target/test-generated/workflow-execution-safety-compile` | `NotRequiredWorkflowStateMachineWorkflow6.scala` |
| mixed (10 sources) | `target/test-generated/workflow-execution-safety-compile` | `SafetyEdgeWorkflowStateMachineWorkflow7.scala` |
| mixed (10 sources) | `target/test-generated/workflow-execution-safety-compile` | `WorkflowExecutionSafetyAbiConsumerProbe.scala` |

The probe is an authored test consumer, not a Cozy-generated production product.
It checks old7/8/10 constructor arities, typed closed variants and target variants,
source Options, exact refs, ordered rules and inherited deadline/wait/retry/timeout
Longs against actual members. The independent original v3 four-product fixture
bytes retain original commit `cea56ab50417d3398a5f4399d2e2d9d0aa751de3`
provenance; unchanged checked-in v2 fixtures and original v1 executable pins
remain active. Expected legacy bytes are never regenerated by the edited producer.

After focused behavior, use this single sequential compiler invocation, with
Scala3.3.8 and its matching standard library only, dependencyOverrides Nil,
exact source groups and separate class/analysis directories under target.
Temporary session settings do not modify build.sbt or publish. Its ordering and
configuration reproduce native P68-S682-VAL-003; this is no new execution receipt.

```sh
sbt --batch \
  'set scalaVersion := "3.3.8"' \
  'set libraryDependencies := Seq("org.scala-lang" % "scala3-library_3" % "3.3.8")' \
  'set dependencyOverrides := Nil' \
  'set Compile / sources := Seq(file("target/test-generated/workflow-execution-safety-normal/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala"), file("target/test-generated/workflow-execution-safety-normal/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala"), file("target/test-generated/workflow-execution-safety-normal/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/SafetyWorkflowStateMachineWorkflow1.scala"), file("target/test-generated/workflow-execution-safety-normal/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/WorkflowExecutionSafetyAbiConsumerProbe.scala"))' \
  'set Compile / classDirectory := file("target/phase68-generated-compile/normal/classes")' \
  'set Compile / compileAnalysisFile := file("target/phase68-generated-compile/normal/inc_compile.zip")' \
  'Compile / compile' \
  'set Compile / sources := Seq(file("target/test-generated/workflow-execution-safety-value/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala"), file("target/test-generated/workflow-execution-safety-value/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala"), file("target/test-generated/workflow-execution-safety-value/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/SafetyWorkflowStateMachineWorkflow1.scala"), file("target/test-generated/workflow-execution-safety-value/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/WorkflowExecutionSafetyAbiConsumerProbe.scala"))' \
  'set Compile / classDirectory := file("target/phase68-generated-compile/value/classes")' \
  'set Compile / compileAnalysisFile := file("target/phase68-generated-compile/value/inc_compile.zip")' \
  'Compile / compile' \
  'set Compile / sources := Seq(file("target/test-generated/workflow-execution-safety-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala"), file("target/test-generated/workflow-execution-safety-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala"), file("target/test-generated/workflow-execution-safety-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/LegacyWorkflowStateMachineWorkflow1.scala"), file("target/test-generated/workflow-execution-safety-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/PolicyWorkflowStateMachineWorkflow2.scala"), file("target/test-generated/workflow-execution-safety-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/LifecycleWorkflowStateMachineWorkflow3.scala"), file("target/test-generated/workflow-execution-safety-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/SafetyWorkflowStateMachineWorkflow4.scala"), file("target/test-generated/workflow-execution-safety-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/FailureOnlyWorkflowStateMachineWorkflow5.scala"), file("target/test-generated/workflow-execution-safety-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/NotRequiredWorkflowStateMachineWorkflow6.scala"), file("target/test-generated/workflow-execution-safety-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/SafetyEdgeWorkflowStateMachineWorkflow7.scala"), file("target/test-generated/workflow-execution-safety-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/WorkflowExecutionSafetyAbiConsumerProbe.scala"))' \
  'set Compile / classDirectory := file("target/phase68-generated-compile/mixed/classes")' \
  'set Compile / compileAnalysisFile := file("target/phase68-generated-compile/mixed/inc_compile.zip")' \
  'Compile / compile'
```

## Dated CNCF receiver observation and resume input

Read-only observation2026-10-03 identifies repository `goldenport-cncf`, verified
origin `git@github.com:asami/goldenport-cncf.git`, native HEAD
`f7cf5bc04c11b9f74e09d61b8199452c5edb3275`. Phase83 is planned.
The named `WorkflowInvocationPolicyAbi` receiver declares only v1/v2 and admits
that closed set at its observed schema gate; v4 is unsupported there. This is
an observation of that named receiver, not all Workflow runtime code. No CNCF
edit, test, acceptance or closure is claimed.

CNCF83 resumes from the accepted source/ABI contracts and two producer commits,
tracked fixture, reproduced sidecar/shared ABI/direct bootstrap/member products,
and focused/compiler evidence above. Cozy provides no upstream receiver substitute.
CNCF owns implementation selection and its own full validation. Phase82 lifecycle
integration remains independently owned in
[CNCF Phase82](../../../goldenport-cncf/docs/phase/phase-82.md).
This handoff does not open/start CNCF83, Phase69 or another chat/Phase.

## CNCF83 implementation and executable-specification obligations

The recipient owns the following concrete behavior boundaries; it chooses its
storage, transport, algorithms and runtime API within its own Phase.

- Admit v4 with compatible old-version handling and typed lossless decoding.
  Specify unsupported schemas, unknown/duplicate keys, malformed shapes, closed
  token violations, invalid references and duplicate policies/rules. Preserve
  exact key/order/null/source/ref/Long/escape meanings, defaults/absence,
  source-free input and mixed collection schema agreement. Integrate the direct
  ordered bootstrap and actual descriptors; preserve legacy v1/v2/v3 compatibility
  and independently owned Phase82 lifecycle dependencies.
- Map provider observations and timeout outcomes to logical failure identities.
  Specify exact case-sensitive rule matching followed by the explicitly declared
  default. PERMANENT prevents technical retry; RETRYABLE is eligible only within
  separately declared existing invocation budget. Failure-only/default-only
  policy creates no retries, delay, backoff or jitter. Keep maximum attempts,
  fixed retry delay, execution timeout, deadline, cancellation and waits independent.
- Resolve symbolic logical identity and idempotency key at runtime. REQUIRED
  admission fails when protection cannot be honored. Specify one stable identity/
  key across same-execution retry and redelivery, distinct new logical executions,
  and separately correlated physical attempts. Do not treat declaration refs as
  generated runtime keys or IDs.
- Specify concurrent duplicate admission and redelivery, restart/durable history
  recovery, atomicity between admission/state/history updates, replay result
  semantics and protection guarantees across failure/restart. CNCF chooses the
  concrete durable representation and admission algorithm.
- Specify timeout and late/stale completion correlation by logical execution,
  physical attempt and existing continuation/revision contracts; duplicate or
  stale completions must not become a new logical execution or corrupt replay
  history. Cover retry/redelivery, restart and independently owned lifecycle
  cancellation/deadline/wait integration.
- Specify independent Action idempotency, compensation and UnitOfWork behavior,
  including Workflow NOT_REQUIRED with Action REQUIRED. Workflow admission
  protection must neither replace nor infer Action guarantees.

Receiver rejection/compatibility specs and runtime concurrency/restart/timeout/
replay specs belong to CNCF83. Recipient completion and CNCF full validation do
not gate Cozy. Current Phase67 HYG-P67-S671-001/002 remain original unresolved
records; this handoff creates no empty journal and promotes no note to a contract.
