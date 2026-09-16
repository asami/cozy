# Phase 63 Checklist: Multi-CML Provenance Contract and Cozy Aggregation

Phase status: in-progress
Updated: 2026-09-16
Ledger for: [Phase 63](phase-63.md)
Successor: [Phase 63.1](phase-63.1.md)
Development item: DEV-031
Repository-full validation: each-phase / Cozy SBT full suite

This is the sole completion ledger for the contract and Cozy aggregate/rebind
boundary. The bridge and real-driver stages moved exactly once to Phase 63.1.

## MCML-63-01: Contract admission

Stage Status:

- Current status: DONE
- Owner: Cozy
- Update rule: Retain DONE while the paired specification and design remain the accepted MCML-63-01 authority, and reopen before a change to the admitted contract boundary.
- Completion evidence: The accepted V2 aggregate contract is recorded in
  `docs/spec/generation-compatibility-contract.md` and
  `docs/design/generation-compatibility-contract.md`; it preserves V1 direct
  and package behavior, requires explicit nonempty delegated inputs, and keeps
  all source and output digests as integrity-only evidence.

- [x] Define the project-level multi-CML provenance representation and the
      explicit source-identity list.
- [x] Define aggregate generated-output semantics, including identical shared
      paths and conflicting bytes for the same path.
- [x] Define one-CML compatibility and migration/reading behavior for existing
      v1 source evidence.
- [x] Define rejection for missing, duplicate, ambiguous, stale, malformed and
      contradictory delegated evidence.
- [x] Confirm that provenance is neither disabled nor converted into a
      hash-derived identity/control mechanism.

## MCML-63-02: Cozy aggregate/rebind implementation

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: Mark DONE only after Cozy's implementation and focused
  executable specifications accept valid multi-CML evidence and reject every
  admitted invalid boundary.

- [ ] Implement the accepted aggregate/rebind operation in Cozy.
- [ ] Add focused executable specifications for two distinct CML sources and a
      deterministic project-level result.
- [ ] Add focused executable specifications for source omission, duplicate or
      ambiguous source identity, stale evidence, malformed evidence and output
      path conflicts.
- [ ] Preserve accepted one-CML behavior.

## Phase closure evidence

- [ ] MCML-63-01 and MCML-63-02 have accepted Step commits, focused validation
      and independent review evidence.
- [ ] The Cozy repository-full SBT suite passes on this Phase's frozen release
      tree under `split_full_test_policy=each-phase`.
- [ ] The committed Phase 63.1 handoff records the exact accepted contract and
      aggregate/rebind receipts without claiming bridge or downstream closure.
