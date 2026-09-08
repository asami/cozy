# Phase 47.2.1 Checklist: CML Logical-Action Compiler and Generated ABI

This checklist is the authoritative progress ledger for the second unit of the
approved Phase 47.2 split. It consumes the closed Phase 47.2 handoff and does
not own final composed-fixture acceptance.

Status: planned
Depends on: Phase 47.2
Successor: Phase 47.2.2

## ACP-02: Logical-action boundary

Stage Status:

- Current status: TODO
- Owner: Cozy Phase 47.2.1.
- Update rule: mark DONE only when CML action identity and typed semantic
  metadata are frozen as producer information rather than runtime operations.

- [ ] Specify logical action identity, typing, provenance, and semantic metadata.
- [ ] Preserve existing UnitOfWork authority; prohibit a parallel Action algebra.

## ACP-03 / ACP-06: Resolver/compiler and generated ABI

Stage Status:

- Current status: TODO
- Owner: Cozy Phase 47.2.1; CNCF Phase 64.2 owns UTP-02 ABI admission.
- Update rule: mark DONE only when deterministic compilation and a versioned
  producer handoff are evidenced. CNCF Phase 64.2 UTP-02 admission remains
  pending and is neither a Cozy closure condition nor a Cozy claim.

- [ ] Compile logical actions to existing `ExecProgram[UnitOfWorkOp, A]`.
- [ ] Generate stable metadata/ABI and record the versioned UTP-02 producer
      handoff without claiming consumer admission.

## ACP-07: Static validation

Stage Status:

- Current status: TODO
- Owner: Cozy Phase 47.2.1.
- Update rule: mark DONE only when invalid bindings and semantic conflicts are
  rejected at the producer boundary.

- [ ] Validate binding, compensation, ordering, idempotency, type, and Free/UoW path.
- [ ] Bind focused validation/review evidence to the exact child tree.

## Child closure

- [ ] All three stage blocks are DONE with exact evidence.
- [ ] Deliver the frozen compiler/ABI producer handoff to Phase 47.2.2 and
      CNCF UTP-02 without claiming CNCF admission.
- [ ] Future execution records child validation, review, and release closure.
