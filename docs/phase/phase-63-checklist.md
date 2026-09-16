# Phase 63 Checklist: Multi-CML Provenance Contract and Cozy Aggregation

Phase status: closed
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

- Current status: DONE
- Owner: Cozy
- Update rule: Reopen before changing the accepted aggregate/rebind contract,
  V1 compatibility behavior, or the represented executable-specification
  boundary.
- Current implementation evidence: `GenerationProvenance` publishes validated atomic V2
  aggregate evidence, preserves V1 direct and legacy one-input behavior, and
  validates V1 or V2 at package admission. The focused
  `Phase63McmlGenerationProvenanceSpec` represents accepted two-source and
  one-source behavior plus all frozen invalid-input diagnostics. Fresh
  representative and accumulator SBT receipts, and the clean focused re-review
  are accepted.

- [x] Implement the accepted aggregate/rebind operation in Cozy.
- [x] Add focused executable specifications for two distinct CML sources and a
      deterministic project-level result.
- [x] Add focused executable specifications for source omission, duplicate or
      ambiguous source identity, stale evidence, malformed evidence and output
      path conflicts.
- [x] Preserve accepted one-CML behavior.

## Phase closure evidence

- [x] MCML-63-01 and MCML-63-02 have accepted Step commits, focused validation
      and independent review evidence: `2322c9057470cf339fa9028a37a4fcf9094d54a8`,
      `ba0912e1335c0b5c788b66b53fa052c6d6392ca6`,
      `P63-MCML-63-02-VAL-009`, `P63-MCML-63-02-VAL-010`,
      `P63-MCML-63-02-FOCUSED-REREVIEW-001`, and
      `P63-FULL-REVIEW-001`.
- [x] The Cozy repository-full SBT suite passes on this Phase's frozen release
      tree under `split_full_test_policy=each-phase`: `P63-FULL-VALIDATION-001`
      receipt `593d37205c74c26372213bd48a59abe8563635e584cc8dd204900028f8f9d926`
      accepted at tree `5c520eccc63bca2a037ee8b6fb2196b8e97b8859582f6cb93a9cb0793bcf445b`.
- [x] This release's committed Phase 63 handoff records the exact accepted
      aggregate/rebind contract and receipts that Phase 63.1 may consume without
      reopening the contract. Phase 63.1 remains planned-not-started; no bridge
      or downstream closure is claimed or staged here.
