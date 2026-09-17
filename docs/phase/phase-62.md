# Phase 62: CML WORKFLOW Source and StateMachine Lowering

status=closed
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-62.3
aggregate_validation_sequence=["PHASE-62","PHASE-62.1","PHASE-62.2","PHASE-62.3"]

Status: closed
Planned at: 2026-09-16
Split applied: 2026-09-17
Closed: 2026-09-17
Development item: DEV-030
Successor: [Phase 62.1](phase-62.1.md)

## Phase Plan Gate

Pre-split gate evidence (2026-09-17): `SPLIT_REQUIRED` from the typed Phase
Entry Gate, with a 1,560-minute expected duration and 2,100-minute conservative
bound. Its reasons were `[time-bound, reasoning-cost-isolation]`.

Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no comparable completed Workflow producer Phase; this
  6.0-7.0h source/lowering interval is the first partition of six open WFL-62 stages.
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: fix first-class `WORKFLOW` identity, lowering,
  automatic progression and external semantic boundaries without a second Workflow control language.
- frozen_profile_transition_handoff: committed normalized Workflow source and
  StateMachine correlation authority for Phase 62.1.
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6.0-7.0h; centered near target and within ceiling.
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: none
- adjacent_merge_structural_rejection_evidence: merging with Phase 62.1 restores
  a 13-15h source-and-public-ABI boundary and removes the committed authority handoff.
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: three additional reviews, ledgers and commits keep source
  semantics separate from public ABI, generator and consumer-fixture execution.
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible launch with frozen quality-first evidence.
- runtime_suitability: re-evaluate in the Phase execution task.
- source: applied split from Phase 62 on 2026-09-17.

## Purpose and boundary

Define `WORKFLOW` as a first-class CML declaration and normalize it to the existing
StateMachine / Composite StateMachine semantic model. This phase owns source identity,
versioning, constituent/reference boundaries, declared versus runtime-instance separation,
source correlation, and the distinction between automatic transition and an external SPI boundary.

It preserves existing StateMachine source compatibility and rejects ambiguous progression,
undeclared required operations, raw execution surfaces and Workflow-only control semantics.
It does not define a Workflow-specific API, provider protocol mode, runtime store or consumer engine.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| WFL-62-01 | First-class Workflow source contract and StateMachine/Composite StateMachine lowering. | CLOSED |

## Acceptance and exclusions

Acceptance is a committed source/lowering contract with executable specifications that can be
consumed without rediscovering Workflow semantics. Phase 62.1 owns generic StateMachine
Provided API / Required SPI and ActionExecution ABI; Phase 62.2 owns deterministic generation;
Phase 62.3 owns the fixture, aggregate full SBT validation and CNCF handoff.

No WorkflowRun datastore, provider dispatch, Workflow-to-Workflow proxy, REST connector, UI
Workflow, Flutter generation, raw shell, runtime persistence or CNCF `sm-workflow` runtime is implemented here.

## Applied split and handoff

The applied sequence is `PHASE-62 -> PHASE-62.1 -> PHASE-62.2 -> PHASE-62.3`.
All WFL stages were OPEN, so no completed history moved. This Phase retains WFL-62-01 and
produces the committed source-normalization handoff consumed by [Phase 62.1](phase-62.1.md).
Repository-full SBT validation is deferred to aggregate final owner Phase 62.3; focused
validation, review, release evidence and commit remain required here.

## Closure evidence

WFL-62-01 is closed with the committed `WORKFLOW` source/lowering contract,
focused executable specifications, and a clean focused re-review after the one
corrected grammar-boundary finding. Focused SBT validation was
`testOnly cozy.modeler.WorkflowCmlSpec cozy.modeler.CompositeStateMachineCmlSpec`
with receipt `55b5dfc8cc24539921e3a1b4a311333649ec948d7f471c775769fccccd5dac05`.
The final focused re-review is
`PHASE-62-WFL-62-01-FOCUSED-REREVIEW-003` with no current boundary blockers.

The repository-full SBT suite was deliberately not run in this child Phase:
the split contract assigns that aggregate validation to Phase 62.3. This
closure therefore makes no API/SPI, generated ABI, runtime, fixture, CNCF, or
`sm-workflow` consumer-acceptance claim.

## References

- [Phase 62 Checklist](phase-62-checklist.md)
- [Phase 62.1 ABI successor](phase-62.1.md)
- [Current StateMachine API/SPI clarification](phase-62-statemachine-api-spi-clarification.md)
