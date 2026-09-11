# Phase 57.2: SimpleModeling.org Publication Target Binding

Status: PLANNED

Plan date: 2026-09-10
Split from: [Phase 57](phase-57.md)
Depends on: Phase 57.1
Successor: [Phase 57.3](phase-57.3.md)
Development item: DEV-020

## Goal

Bind one typed SimpleModeling.org publication target to the generic verified
public export bundle. Reuse Phase 55's site-context-preserving registration and
fail closed when an export is stale or partial, without making the target the
authority for Document Project internals.

## Provenance and structural gate

This third child consumes Phase 57.1's generic verified public export bundle.
It binds one site-aware target; it does not perform task-private preparation or
mutate the production site.

Phase Plan Gate: PROCEED

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution after protected contracts are frozen
- expensive_reasoning_kernel: none; target binding consumes frozen admission
  and manifest/currentness contracts
- frozen_profile_transition_handoff: SimpleModeling.org target binding and
  task-private preparation contract for Phase 57.3
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5–6 h; within preferred band
- incoming_handoff: Phase 57.1 generic verified public export bundle
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: Phase 57.1 + Phase 57.2 is
  10–13 h and Phase 57.2 + Phase 57.3 is 10–13 h, both above the <=8 h ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: the target validates portable evidence while later skill
  work remains unable to widen site authority or bypass currentness
- agent_reasoning_mode_policy: standard
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 57

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| P572-01 | Add one typed SimpleModeling.org target binding that consumes the generic export contract. | planned |
| P572-02 | Use Phase 55 site-aware registration rather than a compatibility descriptor. | planned |
| P572-03 | Reject stale or partial generic export evidence at the target boundary. | planned |

## Frozen handoff to Phase 57.3

- kind: authority
- input: SimpleModeling.org target binding and task-private preparation contract
- producing action: close P572-01 through P572-03
- output: target-binding contract that native publication preparation can
  orchestrate without reopening export authority
- owner: Cozy Document Project
- invalidated by: a changed target binding, effective site context, site-aware
  registration contract, or export receipt

## Closure criteria

- Exactly one typed SimpleModeling.org target consumes the generic public
  bundle.
- The target preserves Phase 55's effective site-root/site-config authority.
- Stale or partial export evidence fails closed before preparation can begin.
- The frozen handoff is sufficient for Phase 57.3 without authorizing site
  mutation or reinterpreting export currentness.

## Non-goals

- Reopening Phase 57 admission or Phase 57.1 manifest/currentness semantics.
- Task-private production-site preparation, native skill work, deployment,
  public upload, push, or production-site mutation.
- Compatibility descriptors, adapters, manual evidence adoption, or fabricated
  successful attempts.

## References

- [Phase 55](phase-55.md)
- [Phase 57.1](phase-57.1.md)
- [Phase 57.2 checklist](phase-57.2-checklist.md)
- [Phase 57.3](phase-57.3.md)
- `docs/journal/2026/09/2026-09-07-register-site-context-currentness-gap.md`
