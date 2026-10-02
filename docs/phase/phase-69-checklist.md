# Phase 69 Checklist: Goal-Oriented Workflow Iteration Semantics

Semantic role: Canonical Phase/Step closure ledger.
Current status: IN_PROGRESS.
Owner: Cozy Phase 69 parent.
Update rule: check artifacts only when present; check acceptance gates only from parent validation, fresh independent review and manual local commit evidence. Preserve unchecked ABI/handoff/final gates until accepted.
Closure basis: all three Step ledgers and the final ledger below; authoring is not acceptance.
Updated: 2026-10-03.

[Phase 69](phase-69.md) owns Cozy producer completion.
[CNCF Phase 84](../../../goldenport-cncf/docs/phase/phase-84.md) owns receiver
integration, result mapping, runtime/history/diagnostics and enforcement.
P69-PLAN-E1 retains the original immutable base
`de3b03530b163ede8c11440093b1a71c0a9fb034` and one full Phase review.

## S69.1 SOURCE

Stage Status:
- Current status: IN_PROGRESS.
- Owner: Cozy source parent and admitted source implementation worker.
- Update rule: synchronize only authored source artifacts now; parent owns focused validation, independent protected review and manual Step commit acceptance.
- Closure basis: every S69.1 item below, including authoring reconciliation and acceptance evidence.

- [x] [Normative source contract](../spec/workflow-semantic-outcome-source-contract.md) authored with exact NEEDS_INPUT/NEEDS_REVISION/REJECTED, closed fields and contextual ownership.
- [x] [Frozen source design](../design/workflow-semantic-outcome-source-lowering.md) authored with cohesive helper extraction and already-resolved constituent evidence.
- [x] [Immutable outcome types](../../src/main/scala/cozy/modeler/WorkflowSemanticOutcome.scala) and defaulted trailing [WorkflowDefinition field](../../src/main/scala/cozy/modeler/CompositeStateMachineDefinition.scala) authored, preserving previous source constructor contracts.
- [x] [Outcome normalizer](../../src/main/scala/cozy/modeler/WorkflowSemanticOutcomeCml.scala), [ownership helper](../../src/main/scala/cozy/modeler/WorkflowDeclarationOwnershipCml.scala), [CSM integration](../../src/main/scala/cozy/modeler/CompositeStateMachineCml.scala) and [safety identity-container recognition](../../src/main/scala/cozy/modeler/WorkflowExecutionSafetyPolicyCml.scala) authored.
- [x] [Real typed review-loop fixture](../../src/test/resources/modeler/workflow-semantic-outcome.cml) authored with six actual edges, complete derivations, explicit initial state and independent retry/lifecycle/wait/failure/safety declarations.
- [x] [Interface executable specification](../../src/test/scala/cozy/modeler/WorkflowSemanticOutcomeCmlSpec.scala) authored with adjacent Given/When/Then, real ModelBuilder/Modeler admission, actual graph/Entity reference cases and bounded alias property.
- [x] Minimum abstraction decision recorded: ordinary graph revision/input backedges and terminal/escalation states suffice for supplied evidence; no Iteration type, counter/budget, termination/escalation policy or scheduler. Unavailable sm-workflow is a verification limitation; future evidence has separate scope.
- [ ] Parent reconciles every scenario/table family's authoring evidence and affected-consumer symbols before validation.
- [ ] Parent mechanical preflight: exact twelve-path inventory, diff check, actual Oct.3 headers/naming/grouping, cohesive CSM parser no longer than original 1,037 lines, working new links and truthful pending ledgers.
- [ ] Registered serialized SBT focused accumulator passes with exit 0 and terminal lock=released: `--batch` and `testOnly cozy.modeler.WorkflowSemanticOutcomeCmlSpec cozy.modeler.WorkflowExecutionSafetyCmlSpec cozy.modeler.WorkflowLifecycleCmlSpec cozy.modeler.WorkflowRuntimeControlCmlSpec cozy.modeler.StateMachineApiSpiSpec cozy.modeler.WorkflowCmlSpec cozy.modeler.CompositeStateMachineCmlSpec`. This includes Test/compile; no redundant compile or Step full suite.
- [ ] Fresh independent step-protected-focused gpt-6.1-sol/high source conformance review accepted.
- [ ] Parent manual local S69.1 Step commit recorded from the accepted source accumulator.

## S69.2 ABI

Stage Status:
- Current status: OPEN.
- Owner: Cozy generated Workflow ABI parent.
- Update rule: enter only after accepted S69.1; check projection/compilation/review/commit gates from actual producer evidence.
- Closure basis: every S69.2 item below; source acceptance alone supplies no generated v5 contract.

- [ ] Additive semantic outcome ABI spec/design and generated JSON/Scala/bootstrap projection authored under a frozen ABI manifest.
- [ ] Ordered canonical semantic outcomes, actual edge references and source correlation appear consistently in all generated representations.
- [ ] Phase69 absence preserves legacy v1/v2/v3/v4 behavior and retained actual v4 baseline products.
- [ ] Generated ABI executable specifications and selected proportional focused validation pass, including the affected producer/consumer symbol map and compile coverage.
- [ ] Actual generated Scala 3 normal/value/mixed consumer compilation passes through registered serialized SBT; generated source is not merely inspected as strings.
- [ ] Fresh independent step-protected-focused gpt-6.1-sol/high ABI conformance review accepted.
- [ ] Parent manual local S69.2 Step commit recorded from the accepted ABI accumulator.

## S69.3 HANDOFF

Stage Status:
- Current status: OPEN.
- Owner: Cozy producer handoff parent; CNCF84 receives runtime responsibilities.
- Update rule: update after accepted ABI with actionable producer evidence and receiver ownership; no runtime implementation claim from Cozy.
- Closure basis: every S69.3 item below, including independent handoff review and manual Step commit.

- [ ] Actionable CNCF84 handoff authored with source/generated symbols, exact producer examples and receiver integration obligations.
- [ ] Runtime result mapping/event admission, guards/current-state/concurrency, execution history and diagnostics are explicitly CNCF84-owned.
- [ ] Retry/failure/lifecycle/wait, ActionExecution/CandidateAdmission and UnitOfWork boundaries are explicit; semantic rejection has no implicit technical classification or terminality.
- [ ] Handoff states the evidence-backed no-Iteration/no-termination/escalation-extension decision and route for future concrete evidence.
- [ ] Selected focused handoff static/link/ownership validation passes against actual accepted producer artifacts.
- [ ] Fresh independent gpt-6.1-sol/medium handoff review accepted.
- [ ] Parent manual local S69.3 Step commit recorded.

## Final Phase Closure

Stage Status:
- Current status: OPEN.
- Owner: Cozy Phase 69 parent.
- Update rule: update only from accepted Step accumulators, sole full review, normal final suite and distinct release commit evidence.
- Closure basis: every final item below and all three accepted Step ledgers; CNCF runtime completion remains outside Cozy closure.

- [ ] All S69.1/S69.2/S69.3 acceptance evidence and separate manual Step commits recorded.
- [ ] Exactly one comprehensive gpt-6.1-sol/high full Phase review for P69-PLAN-E1 accepted across original base, all Step accumulators, actual generated products/compilation, consumer map and current closure.
- [ ] Current blocker/fix dispositions are closed; Hygiene/development-candidate follow-ups are explicit and separately owned. HYG-P67-S671-001/HYG-P67-S671-002 are not silently marked resolved.
- [ ] Normal final full Cozy suite passes through registered serialized SBT with terminal lock release; no extended/heavy substitute.
- [ ] Canonical Phase/checklist/index/strategy, accepted source/ABI/handoff documents and journals synchronized from actual evidence.
- [ ] Distinct parent manual local release commit recorded.
- [ ] Cozy producer Phase69 completion recorded after all required gates; CNCF84 receiver/runtime work remains recipient-owned.
