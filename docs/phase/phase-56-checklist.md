# Phase 56 Checklist: Document Project Native Production Execution

Phase Status: PLANNED

Development item: DEV-019

## P56-01: Typed Provider Execution

- [ ] Freeze the public `document-project run` operation and provider-binding
      contract.
- [ ] Resolve one admitted logical operation and typed provider binding.
- [ ] Validate exact authorities, prerequisites, provider availability, and
      output boundary before execution.
- [ ] Return typed output identities, media types, diagnostics, and receipts.
- [ ] Keep missing-provider and empty-output/no-receipt attempts unsuccessful.

## P56-02: Atomic Evidence Closure

- [ ] Validate output path, media type, hash, and receipt before acceptance.
- [ ] Append the attempt and derive currentness from the same accepted result.
- [ ] Reject partial output/evidence closure.
- [ ] Preserve deterministic failure recovery and append-only attempt history.

## P56-03: Executable Planning State

- [ ] Define closed states for selection, prerequisite readiness, provider
      availability, output currentness, and immediate executability.
- [ ] Project those states consistently through inspect, plan, verify, and
      Dashboard.
- [ ] Preserve planning as read-only.
- [ ] Consume rather than duplicate Phase 49 presentation-semantics state.

## P56-04: Verification Policy and Closure

- [ ] Add typed `structural | visual` verification policy.
- [ ] Make structural verification the default without review rasterization.
- [ ] Require explicit user selection for minimum bounded visual verification.
- [ ] Keep public image outputs distinct from temporary review images.
- [ ] Run focused provider/evidence/planning/verification specifications.
- [ ] Run full Cozy validation.
- [ ] Complete independent focused review with no Current Phase Blocker.
