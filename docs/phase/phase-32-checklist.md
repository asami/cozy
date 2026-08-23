# Phase 32 Checklist: CML Source-Result Compatibility Naming

This checklist is the authoritative progress ledger for Phase 32. It is not a
normative contract.

## CM32-01: Canonical Naming and Retired-Name Boundary

Status: DONE

- [x] Make `Resolved.projectRelativePath` canonical and migrate Cozy callers;
      accepted in Cozy commit `3394ad5741c3f0e071ad1559b4617299a562cd96`.
- [x] Retire the former source field without a deprecated accessor,
      constructor, or named-argument compatibility alias, as explicitly
      authorized in the owning implementation record.
- [x] Preserve the resolved path value, CML source precedence, and CML
      metadata exactly.

## CM32-02: Consumer Evidence and Closure

Status: DONE

- [x] Run `CarCmlSourceResolverSpec` and inspect all publication, review, and
      lint consumers of `Resolved`; focused validation passed 15 of 15.
- [x] Prove published output remains unchanged for the same source inputs.
- [x] Run focused/full Cozy validation, independent review, and ledger
      convergence; final Cozy validation passed 1,337 of 1,337 and the
      accepted implementation record documents full and focused re-review.

Phase 32 closes only after canonical naming and the explicit retired-name
boundary are proven without changing CML source-selection or publication
behavior.
