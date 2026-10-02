# Phase 69: Goal-Oriented Workflow Iteration Semantics

Semantic role: Phase work ledger.
Status: active (in-progress).
Current status: IN_PROGRESS.
Owner: Cozy Phase 69 parent.
Update rule: synchronize acceptance from parent validation, independent reviews and manual local commits with the canonical checklist; authoring alone cannot close a Step or Phase.
Updated: 2026-10-03.

## Goal

技術的 Retry と意味的 Iteration を分離し、AI / Human participant を含む
Goal-oriented loop を必要最小限の宣言意味論として表現する。Cozy は source、
immutable IR、generated producer ABI と actionable handoff を完成させる。
CNCF Phase 84 は receiver integration、runtime/history/diagnostics と enforcement
を所有する。

## Scope

- S69.1 SOURCE: exact NEEDS_INPUT / NEEDS_REVISION / REJECTED declarations,
  strict structural ownership, immutable IR and actual StateMachine-edge references.
- S69.2 ABI: deterministic additive semantic-outcome JSON/Scala/bootstrap projection
  and unchanged v1/v2/v3/v4 products when metadata is absent; actual generated
  Scala 3 consumer compilation.
- S69.3 HANDOFF: actionable producer evidence and CNCF84 receiver/runtime ownership.
- Final closure: sole high full Phase review, ordinary full Cozy suite, current
  canonical ledgers and a distinct manual local release commit.

The [canonical checklist](phase-69-checklist.md) is the closure authority.
S69.1 artifacts are authored and pending parent focused validation, independent
protected review and manual Step commit. ABI, handoff and final gates remain
open. Source authoring does not promise generated v5 output.

## Minimum semantics and abstraction evaluation

The [normative source contract](../spec/workflow-semantic-outcome-source-contract.md)
and [frozen lowering design](../design/workflow-semantic-outcome-source-lowering.md)
declare semantic results referring to existing ordinary graph edges. Outcomes
do not execute, emit events, suspend or mutate Workflow state. Rejected is
distinct from PERMANENT/RETRYABLE and terminal only as authored by the graph.
Invocation retry budgets, lifecycle, waits, failure classification,
ActionExecution, CandidateAdmission, ActionMetadata/idempotency and UnitOfWork
retain their meanings.

The [real review-loop fixture](../../src/test/resources/modeler/workflow-semantic-outcome.cml)
declares review → revision → review, input → review, approval and rejection.
Ordinary graph states/edges also express authored escalation. The actual
[CNCF Phase 84 receiver plan](../../../goldenport-cncf/docs/phase/phase-84.md)
requires separation of Retry and semantic iteration and limits a dedicated
Iteration runtime abstraction to concrete evidence of ordinary graph
insufficiency. No such concrete runtime evidence is supplied. The sm-workflow
checkout is unavailable in this workspace and is a verification limitation,
not a stale-reference finding. This Phase introduces no Iteration type,
iteration counter/budget, termination/escalation field or policy, scheduler
or synthetic retry extension. Future concrete evidence requires separately
owned work. AI review and Human input remain consumer binding examples in
prose, without provider execution grammar.

## Source and ownership evidence

- [Immutable Workflow/CSM IR](../../src/main/scala/cozy/modeler/CompositeStateMachineDefinition.scala)
  and [closed outcome types](../../src/main/scala/cozy/modeler/WorkflowSemanticOutcome.scala).
- [Existing CSM parser integration](../../src/main/scala/cozy/modeler/CompositeStateMachineCml.scala),
  [source normalization](../../src/main/scala/cozy/modeler/WorkflowSemanticOutcomeCml.scala),
  [declaration ownership](../../src/main/scala/cozy/modeler/WorkflowDeclarationOwnershipCml.scala)
  and [legacy safety/lifecycle ownership](../../src/main/scala/cozy/modeler/WorkflowExecutionSafetyPolicyCml.scala).
- [Interface executable specification](../../src/test/scala/cozy/modeler/WorkflowSemanticOutcomeCmlSpec.scala)
  with real model admission, all previous constructor arities, resolved
  Entity-qualified references, closed grammar and both ownership extraction routes.

Existing HYG-P67-S671-001 parser-size and HYG-P67-S671-002 follow-up debt remain
separate; the cohesive invocation ownership extraction is not a Hygiene closure.

## Acceptance ownership

The parent reconciles executable-spec authoring evidence and whole-file
compliance before the seven-suite S69.1 focused accumulator (including Test/compile).
Source and ABI each require an independent protected focused gpt-6.1-sol/high
review; handoff requires an independent medium review. Exactly one high full
Phase review covers all accepted Steps in P69-PLAN-E1. Parent manual local
commits remain distinct per Step and for release. Final normal full Cozy test
and actual generated Scala 3 compilation are required, not inferred from source
authoring. CNCF runtime completion is a receiver responsibility and not a Cozy
producer Phase closure requirement. No validation, review or commit is claimed
by this authored record.
