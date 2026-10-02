# Phase 68: Workflow Failure Policy and Execution-Safety ABI

Status: active

Current status: PHASE-68 active. S68.1 SOURCE is authored under the frozen implementation manifest; the exact six-suite focused validation passed 353/353 tests. Source acceptance awaits independent review and local Step commit. S68.2 and S68.3 are planned.
Owner: Cozy Phase 68 parent.
Update rule: advance status and checklist items only with actual parent-owned validation, review and native commit evidence; synchronize the checklist with this ledger.
Completion basis: the three accepted Cozy producer Steps, exactly one comprehensive Phase review, final Cozy full suite and a local release commit. CNCF runtime implementation is a recipient responsibility.
Updated: 2026-10-02.

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
| S68.1 SOURCE | FAILURE-POLICY / EXECUTION-SAFETY CML, immutable IR, strict ownership and legacy source compatibility | Authored; focused validation passed 353/353; review/commit pending | New real-CML source suite plus the five existing source suites; parent diff checking, fresh protected source review and local Step commit |
| S68.2 ABI | Deterministic lossless v4 JSON/Scala/bootstrap, both public producer routes and exact legacy outputs | Planned | Producer specifications and fixtures, actual generated Scala 3.3.8 typed-consumer compilation, focused review and local Step commit |
| S68.3 HANDOFF | Reproducible CNCF Phase83 producer/receiver contract and evidence | Planned | Same-Phase handoff document, focused review and local Step commit |

S68.1 is defined by the normative [source contract](../spec/workflow-failure-execution-safety-source-contract.md),
paired [lowering design](../design/workflow-failure-execution-safety-source-lowering.md),
[real CML fixture](../../src/test/resources/modeler/workflow-failure-execution-safety-policy.cml)
and [executable specification](../../src/test/scala/cozy/modeler/WorkflowExecutionSafetyCmlSpec.scala).
The [Phase checklist](phase-68-checklist.md) remains the completion ledger.
The v4 ABI specification/design and `phase-68-cncf-handoff.md` are planned
same-Phase documents and have not been authored in S68.1.

## Source gate and producer boundary

The S68.1 parent runs exactly the source accumulator through the registered
serialized SBT runner:

```text
sbt --batch "testOnly cozy.modeler.WorkflowExecutionSafetyCmlSpec cozy.modeler.WorkflowLifecycleCmlSpec cozy.modeler.WorkflowRuntimeControlCmlSpec cozy.modeler.StateMachineApiSpiSpec cozy.modeler.WorkflowCmlSpec cozy.modeler.CompositeStateMachineCmlSpec"
```

P68-S681-VAL-005 passed 353/353 tests across six completed suites, with zero
aborted suites and zero failed, canceled, ignored or pending tests. Test/compile
covering Cozy Scala 2.12.18/JDK17 source and project configuration and parent
`git diff --check` succeeded; SBT/wrapper exited 0 with `lock=released`.
A fresh read-only protected focused source review and a native local Step
commit remain pending. No Step or Phase acceptance is claimed.
The final full suite and sole comprehensive Phase review are release gates
after all three Step commits.

Cozy owns source declarations, immutable producer IR and later ABI projection.
CNCF83 owns observation-to-logical-failure mapping, retry dispatch, runtime
logical execution identity/key resolution, admission duplicate protection,
storage, atomicity, replay results and attempt/timeout correlation. Those runtime
mechanisms are not unchecked Cozy requirements. Phase69 iteration/outcomes,
generic failure models, provider bindings and other repositories are outside
this Phase's source work.

The original Phase67 parser-size and lifecycle-spec organization Hygiene remain
in their existing canonical journal. This Step does not claim their resolution
or duplicate them into a new journal.
