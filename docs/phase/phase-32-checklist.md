# Phase 32 Checklist: CML Source-Result Compatibility Naming

This checklist is the authoritative progress ledger for Phase 32. It is not a
normative contract.

## CM32-01: Canonical Naming and Compatibility

Status: PLANNED

- [ ] Make `Resolved.projectRelativePath` canonical and migrate Cozy callers.
- [ ] Retain only the reviewed deprecated accessor and named-argument
      compatibility required by the public `Resolved` surface.
- [ ] Preserve the resolved path value, CML source precedence, and CML
      metadata exactly.

## CM32-02: Consumer Evidence and Closure

Status: PLANNED

- [ ] Run `CarCmlSourceResolverSpec` and inspect all publication, review, and
      lint consumers of `Resolved`.
- [ ] Prove published output remains unchanged for the same source inputs.
- [ ] Run focused/full Cozy validation, independent review, any bounded repair,
      and ledger convergence before closure.

Phase 32 closes only after canonical naming and the explicit compatibility path
are proven without changing CML source-selection or publication behavior.
