# Phase 47.1 Checklist: CML Action Producer Handoff

This checklist is the authoritative non-normative progress ledger for Phase
47.1. The governing contract is [`docs/phase/phase-47.1.md`](phase-47.1.md).

Phase Status: IN PROGRESS

Scope: Phase 47.1 is a Cozy producer-side metadata, validation, deterministic
generation, and handoff phase. Phase 47.2 and unrelated phase or strategy
projections are outside this ledger.

Acceptance ownership: Cozy records producer-contract evidence and the handoff.
Only `goldenport-cncf` Phase 64.1 can prove UnitOfWork execution, optional-2PC
capability/admission behavior, compensation execution, durable
`RecoveryRequired` behavior, and the corresponding runtime outcomes. This
ledger makes no consumer-runtime acceptance claim.

## ACTX-01: Existing execution inventory
Stage Status:
- Current status: DONE
- Owner: Cozy Phase 47.1 producer-side current-source inventory owner
- Update rule: This stage is DONE only when all checklist items in this block are checked; this checklist block is the closure basis.

- [x] Mark the Phase contract in progress and record the explicit producer vs.
      consumer ownership boundary.
- [x] Record the current CML Action parser/model fields and rejection of
      transaction, retry, and compensation syntax.
- [x] Record current logical action occurrence provenance, CSM-07 generation
      deferrals, CSM-04 pure ActionProgram semantics, and current UnitOfWorkOp
      projection placeholders.
- [x] Record the durable `producer-handoff` decision and its non-claims.
- [x] Parent completes focused diff and local Markdown-link/path checks for
      this documentation slice.

## ACTX-02: Minimal metadata contract
Stage Status:
- Current status: DONE
- Owner: Cozy Phase 47.1 minimal metadata contract owner
- Update rule: This stage is DONE because its sole checklist item is complete;
  the closure basis is the accepted [CML Action Producer Metadata](../design/cml-action-producer-metadata.md)
  authority, including its additive authored surface, exact v1 values, and
  explicit current-grammar and consumer-runtime boundaries.

- [x] Freeze the v1 semantic action metadata contract in the [CML Action
      Producer Metadata](../design/cml-action-producer-metadata.md) authority.

## ACTX-03: Compensation handler binding
Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 47.1 compensation-handler binding owner
- Update rule: This stage remains OPEN while any checklist item in this block is unchecked; this checklist block is the closure basis for marking the stage DONE.

- [ ] Define stable producer-side association and validation for an application
      compensation-handler reference without embedding handler implementation.

## ACTX-04: Static validation
Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 47.1 static-validation owner
- Update rule: This stage remains OPEN while any checklist item in this block is unchecked; this checklist block is the closure basis for marking the stage DONE.

- [ ] Implement tractable producer-side handler, transaction, idempotency,
      recovery-boundary, and ordering validation.

## ACTX-05: Generation
Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 47.1 deterministic-generation owner
- Update rule: This stage remains OPEN while any checklist item in this block is unchecked; this checklist block is the closure basis for marking the stage DONE.

- [ ] Generate the accepted metadata and handler references deterministically
      alongside the existing producer contract.

## ACTX-06: Cozy producer handoff
Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 47.1 producer-handoff owner
- Update rule: This stage remains OPEN while any checklist item in this block is unchecked; this checklist block is the closure basis for marking the stage DONE.

- [ ] Record the completed Cozy producer evidence and hand it to the declared
      consumer boundary without claiming runtime acceptance.

## ACTX-07: Producer-contract acceptance
Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 47.1 producer-contract acceptance owner
- Update rule: This stage remains OPEN while any checklist item in this block is unchecked; this checklist block is the closure basis for marking the stage DONE.

- [ ] Verify producer-contract evidence and handoff completeness within Cozy.
- [ ] Leave all runtime proof to `goldenport-cncf` Phase 64.1; no Cozy runtime
      acceptance is recorded here.
