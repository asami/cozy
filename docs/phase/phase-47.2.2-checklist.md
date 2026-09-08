# Phase 47.2.2 Checklist: CML Composition and Cross-Repository Execution Acceptance

This checklist is the authoritative progress ledger for the final unit of the
approved Phase 47.2 split. It consumes the frozen Phase 47.2.1 compiler/ABI
handoff and records the validated Cozy producer boundary; lightweight review,
final release, and external CNCF acceptance remain open.

Status: Cozy focused validation passed; lightweight review and final release in
progress; external consumer acceptance pending
Depends on: Phase 47.2.1
Predecessor: Phase 47.2.1

## ACP-04 / ACP-05: Composition and testability

Stage Status:

- Current status: IN-PROGRESS (focused validation passed; lightweight review and
  final release pending)
- Owner: Cozy Phase 47.2.2.
- Update rule: Focused validation covers the checked-in producer fixture,
  deterministic descriptor generation, causal order, provenance, metadata, and
  property evidence. Lightweight review and final release are still required;
  consumer execution remains external pending.

- [x] Generate the checked-in fixture twice through the real modeler-scala route
      with byte-identical logical-action Scala and JSON artifacts.
- [x] Prove payment exit/transition/entry order before derived shipment
      reservation with provenance and Phase 47.1 metadata retained.
- [x] Prove repeated canonical descriptor rendering is byte-stable with
      ScalaCheck.
- [x] Focused validation receipt `P4722-VAL-003`: 2 succeeded, 0 failed.
- [ ] Complete lightweight review and final release closure for the Cozy
      producer boundary.

## ACP-08 / UTP-06 through UTP-08: Shared execution acceptance

Stage Status:

- Current status: EXTERNAL-PENDING
- Owner: Cozy Phase 47.2.2 coordinating with CNCF Phase 64.2.
- Update rule: Cozy may mark only the producer-fixture/handoff preparation
  DONE. CNCF owns consumer compilation, runtime, compensation, recovery, and
  idempotency acceptance under its own authority.

- [x] Prepare the exact producer source/generated paths and binding-only
      consumer handoff.
- [ ] CNCF compiles and inspects the fixture without CML parsing.
- [ ] CNCF proves selected failure, abort, compensation, recovery, and
      idempotency outcomes.
- [ ] CNCF records UTP-06, UTP-07, and UTP-08 acceptance evidence.

## UTP-09: Algebra gap closure

Stage Status:

- Current status: EXTERNAL-PENDING
- Owner: CNCF Phase 64.2.
- Prerequisite: CNCF Phase 64.1 must close before Phase 64.2 work begins.
- Update rule: Cozy must not mark this stage DONE; CNCF decides whether every
  candidate new primitive is generically justified or rejected.

- [ ] Record the generic justification or rejection for each algebra gap.

## UTP-02 and UTP-06 through UTP-09: CNCF Phase 64.2 external boundary

Stage Status:

- Current status: EXTERNAL-PENDING
- Owner: CNCF Phase 64.2, after the Phase 64.1 prerequisite.
- Update rule: all items remain external-pending and are never current Cozy
  completion claims.

- [ ] UTP-02: admit and version the `cozy.cml.logical-action-program.v1` ABI.
- [ ] UTP-06: accept production execution identity and structured outcomes.
- [ ] UTP-07: accept simple/local StateMachine success, rejection, and abort.
- [ ] UTP-08: accept composite/Workflow execution, compensation, duplicate
      handling, and recovery.
- [ ] UTP-09: justify or reject any generic `UnitOfWorkOp` extension.

## Cozy producer boundary closure

- [x] The producer fixture, executable specification, and exact handoff are
      prepared within the Cozy boundary.
- [x] Cozy focused validation passed (`P4722-VAL-003`: 2 succeeded, 0 failed).
- [ ] Lightweight review and final release closure remain in progress.
- [ ] CNCF Phase 64.1 prerequisite and Phase 64.2 external acceptance close
      under CNCF authority.
