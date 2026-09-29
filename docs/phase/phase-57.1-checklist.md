# Phase 57.1 Checklist: Document Project Export Manifest and Currentness

Phase Status: COMPLETE

Development item: DEV-020
phase=[Phase 57.1](phase-57.1.md)

Planning rule: approximate-six-hour packing target; preferred 4–8 h band.

## Export acceptance precondition

Evaluate every export entry below under the single-operator conditions in
[Phase 57.1](phase-57.1.md#export-operating-conditions): no destination or parent
directory changes during export, refusal of an existing destination, and one
atomic installation of the completed bundle. External concurrent filesystem
changes are outside this acceptance boundary.

## P571-01: Versioned export manifest and receipt

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Document Project
- Update rule: Update this block from the P571-01 checklist entries only; they
  are the sole closure basis.

- [x] Emit a versioned target/Work Product/hash/role/media/path manifest.
- [x] Bind the exact manifest and output bytes in an export receipt.

## P571-02: Export currentness

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Document Project
- Update rule: Update this block from the P571-02 checklist entries only; they
  are the sole closure basis.

- [x] Derive export stale/current state from all authoritative inputs.
- [x] Invalidate prior export currentness on source, selection, production
      evidence, manifest-authority, or exported-byte change.

## P571-03: Consumer-verifiable handoff

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Document Project
- Update rule: Update this block from the P571-03 checklist entries only; they
  are the sole closure basis.

- [x] Let a consumer validate the bundle without Document Project internals.
- [x] Freeze the generic verified public export-bundle handoff for Phase 57.2.
