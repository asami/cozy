# Phase 47.2.2 - CML Composition and Cross-Repository Execution Acceptance

Status: FINAL — Cozy producer closure accepted by this final release commit;
CNCF consumer acceptance remains external pending
Planned at: 2026-09-08
Split from: [Phase 47.2](phase-47.2.md)
Depends on: Phase 47.2.1
Predecessor: [Phase 47.2.1](phase-47.2.1.md)
Cross-repository consumer work: `asami/goldenport-cncf` Phase 64.2

## Purpose

Close the Cozy producer-fixture and exact handoff boundary for the shared
Order/Payment/Shipment model. This child proves deterministic descriptor
generation, causal occurrence order, provenance, and Phase 47.1 metadata
retention without requiring CNCF to parse CML syntax.

CNCF Phase 64.1 is a prerequisite for the external Phase 64.2 consumer work.
UTP-02 and UTP-06 through UTP-09 remain external-pending Phase 64.2 work and
are never current Cozy completion claims.

It consumes the Phase 47.2.1 compiler/ABI handoff. It does not reopen logical
action semantics, the resolver/compiler, generated ABI design, or the Phase
47.2 consumer-foundation contracts.

## Provenance and structural gate

The 2026-09-08 approved split creates this third and final sequential child.
It consumes the Phase 47.2.1 compiler/ABI handoff and produces the exact Cozy
producer-fixture and handoff evidence for the original Phase 47.2 objective.

Phase Plan Gate: PROCEED

- target: 4.5–6.5h scoped producer-handoff target; within preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: Phase 47.2.1 logical-action compiler,
  generated ABI, static-validation, and UTP-02 admission evidence
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4.5–6.5h; within preferred band
- estimated_at_current_profile: 4.5–6.5h; current Terra xhigh is suitable
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: the final acceptance has one frozen producer surface and
  one frozen consumer foundation, reducing review/retry risk despite the added
  handoff overhead
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 47.2

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| ACP-04 | Cozy producer fixture preserves deterministic causal order and provenance. | DONE |
| ACP-05 | Cozy producer descriptor rendering is deterministic and property-testable. | DONE |
| ACP-08 | Cozy prepares the shared Order/Payment/Shipment fixture and exact producer handoff. | DONE |
| UTP-02 | CNCF admits and versions the received CML compilation ABI after Phase 64.1. | external pending |
| UTP-06 | CNCF proves production execution preserves logical program/plan identities and structured outcomes. | external pending |
| UTP-07 | CNCF proves simple/local StateMachine success, rejection, and abort behavior. | external pending |
| UTP-08 | CNCF proves composite/Workflow execution, compensation, duplicate handling, and recovery. | external pending |
| UTP-09 | CNCF accepts or rejects any proposed generic `UnitOfWorkOp` extension. | external pending |

`UTP-02` and `UTP-06` through `UTP-09` remain CNCF Phase 64.2
consumer-owned work after the Phase 64.1 prerequisite. This child records only
the Cozy producer evidence and exact handoff; it does not claim consumer
admission, runtime execution, compensation, recovery, or algebra closure.

## Closure criteria

- Cozy focused validation receipt `P4722-VAL-003` passed with 2 succeeded and 0
  failed; the mandatory Phase full review `P4722-PHASE-FULL-REVIEW-001` passed.
- The final Cozy repository suite `P4722-SBT-004` passed and is bound to this
  distinct Phase release commit, which is the authoritative Cozy acceptance
  record.
- The checked-in fixture generates through the real modeler-scala route twice
  with byte-identical logical-action Scala and JSON descriptors.
- The fixture retains the three payment placements before the derived shipment
  reservation, with typed provenance and Phase 47.1 metadata.
- The exact `cozy.cml.logical-action-program.v1` source/generated paths and
  consumer binding-only contract are recorded in the dated handoff.
- CNCF Phase 64.1 is recorded as the prerequisite for Phase 64.2 UTP-02 and
  UTP-06 through UTP-09; those items remain external pending and are not Cozy
  closure criteria.

## Non-goals

- Changing the frozen compiler/ABI or upstream planner semantics without a new
  authorized boundary.
- Making arbitrary external systems technically transactional.
- Hiding compensation as rollback or introducing a parallel Action algebra.
- Requiring real database, network, scheduler, or provider I/O for ordinary
  model/interpreter acceptance.

## References

- [Phase 47.2](phase-47.2.md)
- [Phase 47.2.1](phase-47.2.1.md)
- [Phase 47.2.2 checklist](phase-47.2.2-checklist.md)
- `asami/goldenport-cncf/docs/phase/phase-64.2.md`
