# Phase 47.2.1 Checklist: CML Logical-Action Compiler and Generated ABI

This checklist is the authoritative progress ledger for the second unit of the
approved Phase 47.2 split. It consumes the closed Phase 47.2 handoff and does
not own final composed-fixture acceptance.

Status: DONE
Depends on: Phase 47.2
Successor: Phase 47.2.2

## ACP-02: Logical-action boundary

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47.2.1.
- Update rule: DONE because the committed versioned producer ABI freezes CML
  action identity and typed semantic metadata without runtime operations.

- [x] Specify logical action identity, typing, provenance, and semantic metadata.
- [x] Preserve existing UnitOfWork authority; prohibit a parallel Action algebra.

## ACP-03 / ACP-06: Resolver/compiler and generated ABI

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47.2.1; CNCF Phase 64.2 owns UTP-02 ABI admission.
- Update rule: DONE because deterministic compilation and the versioned producer
  handoff are evidenced. CNCF Phase 64.2 UTP-02 admission remains pending and
  is neither a Cozy closure condition nor a Cozy claim.

- [x] Compile logical actions to existing `ExecProgram[UnitOfWorkOp, A]`.
- [x] Generate stable metadata/ABI and record the versioned UTP-02 producer
      handoff without claiming consumer admission.

## ACP-07: Static validation

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 47.2.1.
- Update rule: DONE because invalid bindings and semantic conflicts are rejected
  at the producer boundary by the committed compiler and executable specs.

- [x] Validate binding, compensation, ordering, idempotency, type, and Free/UoW path.
- [x] Bind focused validation/review evidence to the exact child tree.

## Child closure

- [x] All three stage blocks are DONE with exact evidence.
- [x] Deliver the frozen compiler/ABI producer handoff to Phase 47.2.2 and
      CNCF UTP-02 without claiming CNCF admission.
- [x] Record child validation, review, and release closure.
