# Phase 47.1 Checklist: CML Action Producer Handoff

This checklist is the authoritative non-normative progress ledger for Phase
47.1. The governing contract is [`docs/phase/phase-47.1.md`](phase-47.1.md).

Phase Status: DONE

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
- [x] Record the baseline CML Action parser/model fields and the pre-ACTX-04
      rejection of transaction, retry, and compensation syntax.
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
- Current status: DONE
- Owner: Cozy Phase 47.1 compensation-handler binding owner
- Update rule: This stage is DONE because its sole checklist item is complete;
  the closure basis is the accepted [CML Action Compensation Handler
  Binding](../design/cml-action-compensation-handler-binding.md) authority,
  including its action-level association, occurrence-provenance, and explicit
  parser/runtime boundaries.

- [x] Define the stable producer-side association and its semantic applicability
      boundary for an application compensation-handler reference without
      embedding handler implementation; ACTX-04 now implements direct parser and
      static applicability validation without resolving handler implementation.

## ACTX-04: Static validation
Stage Status:
- Current status: DONE
- Owner: Cozy Phase 47.1 static-validation owner
- Update rule: This stage is DONE because every checklist item in this block is checked; this checklist block is the closure basis for the stage.

- [x] Implement direct Action metadata parsing and typed normalized source
      metadata for effect class, transaction requirement, idempotency, and the
      opaque compensation-handler reference.
- [x] Reject partial base metadata, empty or duplicate authored metadata,
      invalid enum/key pairings, and compensation handlers outside the
      `EXTERNAL` plus `OUTSIDE_UNIT_OF_WORK` applicability boundary.
- [x] Preserve legacy Action validity, logical Action identity, occurrence
      provenance, and generated ABI/projection/output boundaries.

## ACTX-05: Generation
Stage Status:
- Current status: DONE
- Owner: Cozy Phase 47.1 deterministic-generation owner
- Update rule: This stage is DONE because its sole checklist item is checked;
  this checklist block is the closure basis for the completed additive
  producer-metadata generation surface.

- [x] Generate the accepted metadata and handler references deterministically
      in an additive Scala/JSON producer surface while preserving CSM v1 ABI,
      bootstrap, generated definition, identity/provenance, and projection
      output unchanged.

## ACTX-06: Cozy producer handoff
Stage Status:
- Current status: DONE
- Owner: Cozy Phase 47.1 producer-handoff owner
- Update rule: This stage is DONE because its sole checklist item is complete;
  the closure basis is the completed [Phase 47.1 Action Producer
  Handoff](../journal/2026/09/2026-09-08-phase-47.1-action-producer-handoff.md)
  record, which fixes the Cozy evidence and consumer-owned non-claims.

- [x] Record the completed Cozy producer evidence and hand it to the declared
      consumer boundary without claiming runtime acceptance.

## ACTX-07: Producer-contract acceptance
Stage Status:
- Current status: DONE
- Owner: Cozy Phase 47.1 producer-contract acceptance owner
- Update rule: This stage is DONE because both checklist items are complete;
  the closure basis is the final [Phase 47.1 Producer-Contract
  Acceptance](../journal/2026/09/2026-09-08-phase-47.1-producer-contract-acceptance.md)
  record, which binds Cozy evidence and preserves the consumer-runtime
  exclusion.

- [x] Verify producer-contract evidence and handoff completeness within Cozy.
- [x] Leave all runtime proof to `goldenport-cncf` Phase 64.1; no Cozy runtime
      acceptance is recorded here.

## Phase closure

Closure basis: ACTX-01 through ACTX-07 are DONE. The mandatory Phase full
review found `CPB-P47.1-001`; its test-only repair passed focused validation
and focused closure re-review. `HYG-P47.1-001` is resolved in the final
producer-contract acceptance record. This closure records only Cozy producer
metadata, validation, generation, and handoff; it does not claim the
consumer-owned runtime outcomes.
