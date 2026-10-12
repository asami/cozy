# Phase 57.3: Native Publication-Preparation Skill Boundary

Status: CLOSED

Plan date: 2026-09-10
Split from: [Phase 57](phase-57.md)
Depends on: Phase 57.2
Development item: DEV-020

## Goal

Define the native publication-preparation skill as a client of completed
Document Project run, verification, export, and typed target contracts. Its
task-private preparation consumes only verified public export evidence and
reports missing capabilities rather than adopting evidence manually or
reintroducing the legacy article-media workflow.

## Provenance and structural gate

This final child consumes Phase 57.2's typed target binding. It replaces the
legacy orchestration assumption. Phase 57.3 entry has explicitly admitted the
canonical publication-preparation skill source root
`/Users/asami/src/development-workstation/common/codex/skills/smorg-publication-prep`,
consumed through the installed `.agents` symlink, with persisted update authority.

Phase Plan Gate: PROCEED; explicit update-root admission recorded at Phase
execution entry.

- target: approximate-six-hour packing target; preferred 4–8 h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution after all producer and consumer
  contracts are frozen
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: Phase 57.2 target-binding contract and
  validated current public export bundle
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5–7 h; within preferred band
- incoming_handoff: Phase 57.2 target-binding contract
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: Phase 57.2 + Phase 57.3 is
  10–13 h, above the <=8 h ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: a native client-only skill prevents a downstream task from
  recreating exporter, currentness, or target authority
- agent_reasoning_mode_policy: standard
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 57

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| P573-01 | Define the publication-preparation skill as an orchestrator of completed native run, verification, export, and target contracts only. | complete |
| P573-02 | Exercise task-private preparation from the exact verified public export bundle and target binding. | complete |
| P573-03 | Report missing provider, blocked Work Product, or invalid currentness without hand edits or retrospective evidence adoption. | complete |

## Scope-admission precondition

The Phase 57.3 entry has admitted the exact external canonical skill source root
`/Users/asami/src/development-workstation/common/codex/skills/smorg-publication-prep`
and recorded its update authority; the installed `.agents` symlink consumes that
source. This records the completed entry-admission fact, not Step acceptance.
This Phase document neither grants that authority
nor authorizes a SimpleModeling.org repository edit, deployment, upload, push,
or production-site mutation.

## Closure criteria

- The skill is a client only of frozen native run, verify, export, and target
  contracts.
- Task-private preparation accepts only the typed target binding and current,
  verified public export evidence.
- No `cozy-article-media` adapter assumption, manual evidence adoption, or
  fabricated successful attempt remains in the skill contract.
- Missing capability and invalid evidence are reported precisely and fail
  closed.

## Non-goals

- Reopening producer admission, manifest/currentness, or target-binding
  authority.
- Editing the external skill root before its explicit entry admission.
- Publication, deployment, upload, push, or production-site mutation.

## Accepted local execution

P573-01 accepted the admitted canonical skill boundary (`f3814b8`,
`ef30925`); P573-02 accepted native task-private preparation (`8d06d7c`);
P573-03 accepted the native sequence and failure proof (`68af4c0`, `049683f`).
The final child proof passed 50 focused tests across four suites, including
12 preparation scenarios and six CLI property cases. The single epoch-1 full
Phase review passed without a Current Phase Blocker on 2026-10-01. Execution
used the user-selected `gpt-6.1-sol / high` parent profile.

The [checklist](phase-57.3-checklist.md) is the completion ledger. The
[Hygiene follow-up](../journal/2026/10/2026-10-01-phase-57.3-hygiene-follow-up.md)
retains two nonblocking maintenance records verbatim; no Development Candidate
was admitted. Final local release acceptance requires the Cozy full test suite,
the canonical skill acceptance driver, and a distinct closure commit. Source
acceptance does not establish capability in an actually invoked installed Cozy;
the skill checks native command availability at invocation.

## References

- [Phase 57.2](phase-57.2.md)
- [Phase 57.3 checklist](phase-57.3-checklist.md)
- `docs/spec/document-project.md`
