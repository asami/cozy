# Phase 68: Workflow Failure Policy and Execution-Safety ABI

Status: COMPLETE

Current status: COMPLETE at the Cozy producer boundary. All three Steps and the sole comprehensive P68-FULL-REVIEW-001 revision1 for P68-PLAN-E1 passed, with zero CPB/HYG/DEV. P68-FINAL-VAL-001 passed: 2,587 succeeded, 0 failed, 9 canceled, 0 ignored, 0 pending; 182 suites, 0 aborted; SBT/wrapper exit0 and `lock=released`. The local release commit containing this record completes ordinary Phase68 closure. CNCF83 owns receiver integration and runtime enforcement.
Owner: Cozy Phase 68 parent.
Update rule: advance status and checklist items only with actual parent-owned validation, review and native commit evidence; synchronize the checklist with this ledger.
Completion basis: the three accepted Cozy producer Steps, exactly one comprehensive Phase review, final Cozy full suite and a local release commit. CNCF runtime implementation is a recipient responsibility.
Updated: 2026-10-03.

## Goal

failure の意味と安全な再実行を Workflow ABI で表現できるようにする。

## Scope

- retryable / permanent failure classification
- FailurePolicy
- logical execution / idempotency metadata
- duplicate-protection contract metadata
- 既存の fixed retry delay を保持する。追加の backoff mode / jitter grammar は導入しない。

runtime deduplication implementation は CNCF の責務とする。

## Planned Steps and current acceptance

| Step | Scope | Current status | Acceptance basis |
| --- | --- | --- | --- |
| S68.1 SOURCE | FAILURE-POLICY / EXECUTION-SAFETY CML, immutable IR, strict ownership and legacy source compatibility | Accepted: source353/353, P68-S681-REVIEW-001 PASS, commit 0a8062750a390343f7339fa4a1af8c02a6e4a8fd | New real-CML source suite plus the five existing source suites; parent diff checking, fresh protected source review and native local Step commit |
| S68.2 ABI | Deterministic lossless v4 JSON/Scala/bootstrap, both public producer routes and exact legacy outputs | Accepted: producer92/92, actual Scala3.3.8 normal/value/mixed 4/4/10 compilation, P68-S682-REVIEW-001 revision1 PASS, commit 3e2c7605f343e7eef75bf8166f98f1c6e0d89ac4 | Producer specifications and independent original fixtures, actual generated Scala 3.3.8 typed-consumer compilation, focused review and native local Step commit |
| S68.3 HANDOFF | Reproducible CNCF Phase83 producer/receiver contract and evidence | Accepted: P68-S683-STATIC-VALIDATION-001, P68-S683-REVIEW-001 revision1 PASS, native commit 0180e8fd4ad0979543f8cb872577ca286380fe21 | Same-Phase handoff document, parent static verification, focused review and local Step commit |

S68.1 is defined by the normative [source contract](../spec/workflow-failure-execution-safety-source-contract.md),
paired [lowering design](../design/workflow-failure-execution-safety-source-lowering.md),
[real CML fixture](../../src/test/resources/modeler/workflow-failure-execution-safety-policy.cml)
and [executable specification](../../src/test/scala/cozy/modeler/WorkflowExecutionSafetyCmlSpec.scala).
The [Phase checklist](phase-68-checklist.md) remains the completion ledger.
S68.2 is defined by the accepted [ABI contract](../spec/workflow-failure-execution-safety-abi-contract.md),
[ABI lowering design](../design/workflow-failure-execution-safety-abi-lowering.md),
[producer executable specification](../../src/test/scala/cozy/modeler/WorkflowExecutionSafetyAbiGenerationSpec.scala)
and [independent original v3 baseline provenance](../../src/test/resources/modeler/workflow-execution-safety-v3-baseline/provenance.md).
The same-Phase [CNCF83 handoff](phase-68-cncf-handoff.md) is accepted at native Step commit `0180e8fd4ad0979543f8cb872577ca286380fe21` after P68-S683-REVIEW-001 revision1 PASS.

## Source gate and producer boundary

The S68.1 parent runs exactly the source accumulator through the registered
serialized SBT runner:

```text
sbt --batch "testOnly cozy.modeler.WorkflowExecutionSafetyCmlSpec cozy.modeler.WorkflowLifecycleCmlSpec cozy.modeler.WorkflowRuntimeControlCmlSpec cozy.modeler.StateMachineApiSpiSpec cozy.modeler.WorkflowCmlSpec cozy.modeler.CompositeStateMachineCmlSpec"
```

P68-S681-VAL-005 passed 353/353 tests across six completed suites, with zero
aborted suites and zero failed, canceled, ignored or pending tests. Test/compile
covering Cozy Scala 2.12.18 with repository release17 source configuration and
parent `git diff --check` succeeded; the actual SBT JVM was Amazon Java25.0.4.
No independent JDK17 execution was established. SBT/wrapper exited 0 with `lock=released`.
Fresh protected focused source review P68-S681-REVIEW-001 revision1 passed,
and native local S68.1 Step commit 0a8062750a390343f7339fa4a1af8c02a6e4a8fd
records source acceptance. S68.2 and S68.3 are also accepted. The sole
comprehensive Phase review passed after all three Step commits; final full
validation is recorded below.

The S68.2 authored behavior specification projects all four actual Workflow
products through both normal/value public routes twice, checks original v3
byte fixtures, independent complete JSON meanings and seven ordered mixed
members, and writes normal/value/mixed typed consumer compiler inputs.
Parent-admitted P68-S682-VAL-002 (subject revision2) passed the producer
accumulator92/92 across seven completed suites, with zero failed, canceled,
ignored or pending tests and zero aborted suites, including relevant
Scala2.12.18 Test/compile. Its native log is
`/private/tmp/skill.cncf.d/a-da11fdb5-d2a8-4da0-9e25-0151ae867de7/19837-20261002T191625Z.log`
and summary is
`/private/tmp/skill.cncf.d/a-da11fdb5-d2a8-4da0-9e25-0151ae867de7/19837-20261002T191625Z.summary.json`.
Parent-admitted P68-S682-VAL-003 (subject revision3) compiled the distinct actual
Scala3.3.8 normal/value/mixed 4/4/10 source groups sequentially in one serial
invocation, with native .class/.tasty outputs for all eighteen sources and
three separate analyses. Its native log is
`/private/tmp/skill.cncf.d/a-e89ba2cc-4f50-4cc7-9f17-2ca0e625c375/20609-20261002T191906Z.log`
and summary is
`/private/tmp/skill.cncf.d/a-e89ba2cc-4f50-4cc7-9f17-2ca0e625c375/20609-20261002T191906Z.summary.json`.
Both records report SBT/wrapper exit0 and `lock=released`. Fresh independent
producer review P68-S682-REVIEW-001 revision1 PASS and native local Step commit
`3e2c7605f343e7eef75bf8166f98f1c6e0d89ac4` record S68.2 acceptance, with zero
CPB/HYG/DEV. S68.3 handoff is accepted after static verification, P68-S683-REVIEW-001 revision1
PASS and native commit `0180e8fd4ad0979543f8cb872577ca286380fe21`. The sole comprehensive Phase review and final full suite also passed.

Cozy owns source declarations, immutable producer IR and static ABI projection.
CNCF83 owns observation-to-logical-failure mapping, retry dispatch, runtime
logical execution identity/key resolution, admission duplicate protection,
storage, atomicity, replay results and attempt/timeout correlation. Those runtime
mechanisms are not unchecked Cozy requirements. Phase69 iteration/outcomes,
generic failure models, provider bindings and other repositories are outside
this Phase's source work.

The original Phase67 parser-size HYG-P67-S671-001 and lifecycle-spec organization
HYG-P67-S671-002 Hygiene remain
in their existing canonical journal. This Step does not claim their resolution
or duplicate them into a new journal.

## Final validation and closure

P68-FINAL-VAL-001 passed: 2,587 succeeded, 0 failed, 9 canceled, 0 ignored, 0 pending; 182 suites, 0 aborted; SBT/wrapper exit0 and `lock=released`. The nine cancellations are the original four opt-in Docker Remotion integrations and five existing CV/SP ownership scenarios; both specifications remain byte-identical to the original Phase68 base. Native SBT1.9.7 used Amazon Java25.0.4; release17 is the source floor, not a separate runtime-JDK17 pass.

The final native log is `/private/tmp/skill.cncf.d/a-20973fb8-f418-4671-9e54-52794b2f2a80/45591-20261002T205316Z.log` and summary is `/private/tmp/skill.cncf.d/a-20973fb8-f418-4671-9e54-52794b2f2a80/45591-20261002T205316Z.summary.json`. All 1,556 tracked validation inputs, the ai/directive Gitlink, accepted producer code, generated compiler inputs, fixtures and build settings are unchanged after the run. The final documentation only records these actual results; P68-RELEASE-SUBJECT revision1 and its original native receipt are reused.

The three Step commits and sole comprehensive review accepted the complete source/IR and v4 JSON/Scala/bootstrap producer. The containing distinct local release commit closes Phase68 and synchronizes this record, checklist, handoff, contract status and Phase68 index/strategy projections. No new Hygiene or Development Candidate was accepted, so no empty Phase68 journal is created. Original Phase67 Hygiene remains unresolved in its existing journal. CNCF83 owns runtime consumption, deduplication and execution enforcement. No successor Phase is started.
