# Phase 67: CNCF82 Scheduling and Lifecycle Handoff

Semantic role: Engineering handoff (non-normative)
Current status: Cozy producer delivery COMPLETE through S67.1-S67.3 and handoff commit 72918083e7f6e7a165b5599a01d8bc459c339c0a; sole comprehensive review P67-FULL-REVIEW-001 PASS. Final repository validation/release evidence is recorded in the Phase 67 checklist. CNCF Phase 82 receiver/runtime work remains separately owned.
Owner: Cozy owns the producer; CNCF Phase 82 owns receiver integration and runtime behavior.
Update rule: keep the source and ABI contracts authoritative; update evidence and acceptance status only after the corresponding parent-owned review, validation and local commit.
Updated: 2026-10-02

Navigation: [Phase 67](phase-67.md), [checklist](phase-67-checklist.md),
[source contract](../spec/workflow-scheduling-lifecycle-source-contract.md),
[generated ABI contract](../spec/workflow-scheduling-lifecycle-abi-contract.md),
[lowering design](../design/workflow-scheduling-lifecycle-abi-lowering.md),
[CNCF Phase 82](../../../goldenport-cncf/docs/phase/phase-82.md),
[current receiver](../../../goldenport-cncf/src/main/scala/org/goldenport/cncf/workflow/WorkflowInvocationPolicyAbi.scala).

## Accepted producer evidence and remaining gates

| Step | Native local commit | Accepted evidence |
| --- | --- | --- |
| S67.1 SOURCE | `10548ef5ad332327bf4f3f2105a2a8ba1bcbc61d` | `P67-S671-VAL-001`: 291 tests in four suites; independent protected review `P67-S671-REVIEW-001`: PASS, zero current blockers. |
| S67.2 PRODUCER | `43af3f1cfe3fd37ae922cfe984b94c8fd6147452` | `P67-S672-VAL-007`: 195 tests in five suites; `P67-S672-VAL-009`: ten actual generated / typed-consumer sources compiled with Scala 3.3.8 and its compiler-matching standard library; independent protected review `P67-S672-REVIEW-001`: PASS, zero current blockers. |

These are existing accepted receipts. The instructions below supply reproduction,
not an additional execution receipt. S67.3 independent medium Step review
`P67-S673-REVIEW-001` passed with zero blockers and no new Hygiene or Development
Candidate; local Step commit `72918083e7f6e7a165b5599a01d8bc459c339c0a` records its acceptance.
The sole comprehensive high Phase review `P67-FULL-REVIEW-001` for
`P67-PLAN-E1` passed with zero current blockers and no new findings. Final Cozy
full-suite and manual local release evidence is tracked in the
[Phase 67 checklist](phase-67-checklist.md). CNCF82 receiver/runtime completion
is outside this producer delivery boundary.
CNCF runtime implementation and CNCF full-suite completion remain separately
owned and are not Cozy acceptance gates.

## Input and public producer reproduction

The tracked [fixture](../../src/test/resources/modeler/workflow-lifecycle-policy.cml)
defines `LifecycleWorkflow`, version `workflow-lifecycle-v3`. Its current source
correlations are Workflow root line 25, definition line 27, invocation entry
line 81, lifecycle section line 86, Timer entry line 93 and Signal entry line 98.
These are actual declaration locations, not runtime timestamps. The accepted IR
contains:

```scala
WorkflowLifecyclePolicy(Some(60000L), Some(WorkflowCancellationMode.Cooperative),
  WorkflowSourceIdentity(Some(86)))
Vector(
  WorkflowWaitDeclaration("payment-timer", "Pending", "Pending",
    WorkflowWaitTrigger.Timer(250L), WorkflowSourceIdentity(Some(93))),
  WorkflowWaitDeclaration("completion-signal", "Complete", "Complete",
    WorkflowWaitTrigger.Signal("payment.completed"), WorkflowSourceIdentity(Some(98)))
)
```

The existing `capture-control` invocation targets `capture-payment` with
`execution-timeout-millis = 30000`; lifecycle projection preserves it.
The implementation entry points are [Cozy.main](../../src/main/scala/cozy/Cozy.scala),
the [ABI generator](../../src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala)
and the paired [generation specification](../../src/test/scala/cozy/modeler/WorkflowLifecycleAbiGenerationSpec.scala).

Run the following logical SBT invocations from the Cozy repository root. Paths
are relative to that root, independent of the machine's checkout directory.
Cozy itself uses SBT 1.9.7 / Scala 2.12.18; the emitted source target is Scala
3.3.8. Do not change the source build's Scala version for public generation.
For Codex execution, delegate each invocation to the registered
`cncf_command_runner` through the shared `cncf-sbt-serial-execution` wrapper,
`run-sbt-serial.sh`; wait for `lock=released` before the next top-level invocation.
The `sbt` notation here describes reproduction arguments only.

```sh
sbt --batch 'runMain cozy.Cozy modeler-scala src/test/resources/modeler/workflow-lifecycle-policy.cml --save target/phase67-handoff/normal'
sbt --batch 'runMain cozy.Cozy modeler-scala-value src/test/resources/modeler/workflow-lifecycle-policy.cml --save target/phase67-handoff/value'
```

Each route produces the same four ABI products relative to its own `--save` root:

- `target/cozy/statemachine-workflow-abi.json`
- `target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala`
- `target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala`
- `target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/LifecycleWorkflowStateMachineWorkflow1.scala`

The existing specifications retain tested route outputs under
`target/test-generated/workflow-lifecycle-normal` and
`target/test-generated/workflow-lifecycle-value`. Those roots are distinct from
the optional public handoff reproduction roots above.

## ABI integration summary

The full [normative ABI contract](../spec/workflow-scheduling-lifecycle-abi-contract.md)
owns field order, types and compatibility. Receiver implementation must use it
together with the [source contract](../spec/workflow-scheduling-lifecycle-source-contract.md).
The summary here introduces no new semantics.

Schema selection covers the entire ordered collection: any lifecycle policy or
nonempty waits selects ABI/bootstrap v3; otherwise any invocation policy selects
v2; otherwise v1, including empty input. Mixed collections give every member
the selected schema. Lifecycle-free v1/v2 products retain their exact bytes.
Bootstrap retains ordered member references and directly wraps descriptors in
`componentFactoryMetadata`, without provider selection.

Every v3 member explicitly carries `invocationPolicies`, `lifecyclePolicy` and
`waits`. Closed lifecycle object keys are `deadlineMillis`, `cancellation`,
`source`; closed wait keys are `identity`, `state`, `stateIdentity`, `trigger`,
`source`. Closed Timer trigger keys are `kind`, `delayMillis`, with kind `TIMER`;
closed Signal trigger keys are `kind`, `identity`, with kind `SIGNAL`.
Cancellation is the exact token `COOPERATIVE`. Absence is JSON `null` for the
policy or its optional deadline/cancellation, and `[]` for invocation policies
or waits. Source is `{ "line": N }`, or `{ "line": null }` for sourceless IR.
Scala equivalents are `None` and `Vector.empty`, with closed
`CancellationMode.Cooperative`, `WaitTrigger.Timer` and `WaitTrigger.Signal`.

`WorkflowDescriptor` retains its seven original fields and appends defaulted
`invocationPolicies: Vector[InvocationPolicy] = Vector.empty`,
`lifecyclePolicy: Option[LifecyclePolicy] = None`, and
`waits: Vector[WaitDeclaration] = Vector.empty`. Existing seven-argument v1 and
eight-argument v2 constructor clients remain source compatible against v3;
this does not assert binary compatibility. Source `WorkflowDefinition` likewise
has trailing defaulted lifecycle policy and waits after invocation policies.

Source numbers accept trimmed `[0-9]+`, including leading zeros, with lossless
Long bounds: deadline 1..9223372036854775807 and delay
0..9223372036854775807. Zero Timer delay is valid. Signed, quoted, fractional,
exponent, unit-suffixed and overflowing source values are rejected. JSON uses
exact decimal integers; Scala uses decimal Long literals with `L`. Receiver
decoding must preserve these values without Double conversion or coercion.
Quote, backslash, newline, carriage-return, tab and ISO-control escaping must
preserve decoded strings. Source lines must remain actual correlations or
absent, without inferred locations.

Wait `state` preserves trimmed declared spelling; `stateIdentity` preserves the
canonical CSM State resolved with the existing case/separator-insensitive key.
Wait identities and canonical target States are each unique under that key.
Wait order remains source order. Signal identity is a trimmed nonempty logical
identity, with no invented registry or value case normalization.
Existing context, provider, Action, execution and continuation contracts remain
unchanged.

## Focused specifications and generated-source compiler reproduction

The accepted five-suite focused command is:

```sh
sbt --batch 'testOnly cozy.modeler.WorkflowLifecycleAbiGenerationSpec cozy.modeler.WorkflowInvocationPolicyAbiGenerationSpec cozy.modeler.StateMachineWorkflowAbiGenerationSpec cozy.modeler.SkillDrivenWorkflowProducerFixtureSpec cozy.modeler.WorkflowLifecycleCmlSpec'
```

Besides the two public route roots, the lifecycle generation specification emits
a mixed collection to `target/test-generated/workflow-lifecycle-compile`.
Its Scala source directory, relative to that output root, is
`target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow`.
The compiler input is exactly these ten sources:

| Output root | Filename in that Scala source directory |
| --- | --- |
| `target/test-generated/workflow-lifecycle-compile` | `StateMachineWorkflowAbi.scala` |
| same | `StateMachineWorkflowComponentFactoryBootstrap.scala` |
| same | `LegacyWorkflowStateMachineWorkflow1.scala` |
| same | `PolicyWorkflowStateMachineWorkflow2.scala` |
| same | `LifecycleEdgeWorkflowStateMachineWorkflow3.scala` |
| same | `WaitOnlyWorkflowStateMachineWorkflow4.scala` |
| same | `DeadlineOnlyWorkflowStateMachineWorkflow5.scala` |
| same | `CancellationOnlyWorkflowStateMachineWorkflow6.scala` |
| same | `WorkflowLifecycleAbiConsumerProbe.scala` |
| `target/test-generated/workflow-lifecycle-normal` | `LifecycleWorkflowStateMachineWorkflow1.scala` |

The probe is an authored typed test consumer, not a generated Cozy product. It
constructs old seven/eight-argument descriptors, reads optional lifecycle,
waits and invocation vectors, matches closed Timer/Signal and Cooperative
variants, and accesses typed Long and Signal values.

After reproducing the focused specifications, the following is the portable
equivalent of the accepted compiler invocation. Its temporary session settings
select only those actual sources and the compiler-matching Scala 3.3.8 standard
library, clear dependency overrides, and isolate classes and compile analysis
from the normal Cozy source build. These settings do not edit `build.sbt` or
publish an artifact. This is an instruction, not a new compilation receipt.

```sh
sbt --batch \
  'set scalaVersion := "3.3.8"' \
  'set libraryDependencies := Seq("org.scala-lang" % "scala3-library_3" % "3.3.8")' \
  'set dependencyOverrides := Nil' \
  'set Compile / sources := Seq(file("target/test-generated/workflow-lifecycle-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala"), file("target/test-generated/workflow-lifecycle-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala"), file("target/test-generated/workflow-lifecycle-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/LegacyWorkflowStateMachineWorkflow1.scala"), file("target/test-generated/workflow-lifecycle-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/PolicyWorkflowStateMachineWorkflow2.scala"), file("target/test-generated/workflow-lifecycle-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/LifecycleEdgeWorkflowStateMachineWorkflow3.scala"), file("target/test-generated/workflow-lifecycle-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/WaitOnlyWorkflowStateMachineWorkflow4.scala"), file("target/test-generated/workflow-lifecycle-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/DeadlineOnlyWorkflowStateMachineWorkflow5.scala"), file("target/test-generated/workflow-lifecycle-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/CancellationOnlyWorkflowStateMachineWorkflow6.scala"), file("target/test-generated/workflow-lifecycle-compile/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/WorkflowLifecycleAbiConsumerProbe.scala"), file("target/test-generated/workflow-lifecycle-normal/target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/LifecycleWorkflowStateMachineWorkflow1.scala"))' \
  'set Compile / classDirectory := file("target/phase67-generated-compile/classes")' \
  'set Compile / compileAnalysisFile := file("target/phase67-generated-compile/inc_compile.zip")' \
  'Compile / compile'
```

## CNCF82 receiver baseline and implementation handoff

The dated read-only observation is CNCF native HEAD
`f7cf5bc04c11b9f74e09d61b8199452c5edb3275` on 2026-10-02.
`WorkflowInvocationPolicyAbi` declares v1/v2 constants at lines 14–15 and
admits only that closed version set at lines 88–92; v3 is unsupported there.
The frozen evidence found no v3 ABI token in the current main Scala sources.
Phase 82 remains planned. This observation describes that receiver boundary
at that head, not the absence of all existing Workflow runtime functionality.
It does not accept or modify CNCF.

CNCF82 owns the following implementation and executable-specification work:

- Admit v3 alongside compatible v1/v2 input; implement a typed, lossless
  decoder for lifecycle policy, waits and closed variants/keys. Specify
  rejection of unsupported schemas, unknown/duplicate keys, malformed shapes,
  invalid ranges/tokens/references and lossy values, alongside v1/v2
  compatibility, null/empty handling and mixed-member behavior.
- Consume the ordered generated bootstrap/discovery metadata and preserve
  Workflow version, State identity, declaration order and source correlation.
  Integrate existing typed continuation, semantic event and `advance` behavior
  under the runtime owner's contracts.
- Compute deadline from actual Workflow run start and Timer due time separately
  for each entry into its target State. Specify zero delay, repeated State
  entry, logical Signal identity delivery and cooperative cancellation.
- Supply durable run/wait/continuation identity, revision, due-time, state and
  history storage. Specify restart-safe scheduling and stale/duplicate
  completion guards across restart, cancellation and resumed continuations.

Static producer declaration metadata supplies Workflow identity/version,
root/definition/source location, State identity and wait identity. Runtime
instance data supplies run/continuation/wait instance IDs, revisions, due
timestamps, stored state and history. Relative declarations alone do not
create a scheduled or persisted run.

CNCF82 chooses its persistence schema, transport and runtime API in its own
scope. This handoff requires no new public suspend/resume CML operation or
API, and Cozy supplies no receiver adapter, timer execution, signal transport
or persistence workaround. Receiver rejection/compatibility tests and runtime
behavior tests belong to CNCF82; they do not add Cozy closure-only tests.
