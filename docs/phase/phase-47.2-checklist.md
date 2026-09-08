# Phase 47.2 Checklist: UnitOfWork Planning and Deterministic Test Foundation

This checklist is the authoritative progress ledger for retained Phase 47.2.
It records only the first unit of the approved 2026-09-08 split; Phase 47.2.1
and Phase 47.2.2 own the later compiler and cross-repository acceptance work.

Status: completed; final full validation and release commit pending
Depends on: Phase 47.1
Successor: Phase 47.2.1

## ACP-01 / UTP-01: Cross-repository execution inventory

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47.2 coordinating with CNCF Phase 64.2
- Update rule: mark DONE only when the producer and consumer execution seams
  are cataloged from current source and no unadmitted parallel Action algebra
  is assumed.

- [x] Record the CML action/effect, metadata, and generated-surface inventory.
- [x] Record `UnitOfWorkOp`, `ExecProgram`, `ExecUowM`, Free/UoW DSLs,
  interpreters/drivers, metadata, and existing test boundaries.

## UTP-03 / UTP-04: Classification and planner contract

Stage Status:

- Current status: DONE
- Owner: CNCF Phase 64.2; Cozy Phase 47.2 owns the coordinated evidence boundary.
- Update rule: mark DONE only when classifications and the planner contract
  preserve identity, provenance, ordering, admission, idempotency, and
  compensation semantics without a StateMachine-specific runtime algebra.

- [x] Classify all 51 existing executable intents by planning-relevant effect.
- [x] Freeze inspectable segment planning, ordered occurrence, external-boundary,
      and capability-admission semantics.

## UTP-05: Deterministic test foundation

Stage Status:

- Current status: DONE
- Owner: CNCF Phase 64.2.
- Update rule: mark DONE only when program inspection, deterministic fake
  drivers, typed results, and selected-occurrence failure injection are
  available without real provider I/O by default.

- [x] Define/implement deterministic program/test-interpreter seams.
- [x] Bind final-tree focused validation `P472-UOW-STEP-FEATURE-001` (9
      succeeded, 0 failed, 2 suites) and full review
      `P472-PHASE-FULL-REVIEW-001` (PASS) to the child tree.

## Child closure

- [x] All three stage blocks are DONE with source/evidence references.
- [x] Phase 47.2.1 receives the frozen consumer contract handoff.
- [ ] Final full validation and the distinct Phase 47.2 release commit record
      the mechanical closure; Phase 47.2.1 and Phase 47.2.2 remain planned and
      unstarted.
