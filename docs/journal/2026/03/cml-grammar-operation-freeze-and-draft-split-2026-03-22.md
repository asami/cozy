# CML Grammar Operation Freeze and Draft Split

Date: 2026-03-22
Status: done
Owner: cozy-modeler

## Summary

Updated CML grammar documentation to remove ambiguity between accepted grammar and draft proposals.

Changes include:

1. Operation grammar (OP-01/OP-03 contract) was merged into latest grammar contract.
2. Latest grammar was clarified as accepted parser/generator contract only.
3. Draft/WIP grammar was split into a separate design document.

## Updated Files

- `/Users/asami/src/dev2025/cozy/docs/notes/cml-grammar-latest.md`
- `/Users/asami/src/dev2025/cozy/docs/design/cml-grammar-draft.md`

## What Was Fixed

### A. Implemented vs WIP mixing

- `cml-grammar-latest.md` now explicitly excludes draft/WIP grammar.
- Draft proposals must live in `docs/design/cml-grammar-draft.md`.

### B. Top-level section consistency

Latest grammar now declares all current top-level sections used by operation contract:

- `# ENTITY`
- `# EVENT`
- `# OPERATION`
- `# COMMAND`
- `# QUERY`

### C. Operation grammar completeness

Added and frozen:

- canonical form (`INPUT`)
- convenience form (`PARAMETER`)
- dual definition (`INPUT + PARAMETER`)
- normalization to canonical single-input model
- deterministic field order and generated input naming rule
- strict validation rules
- emitted metadata contract

### D. Numbering and traceability

Section numbering in latest grammar was normalized and made consistent.

### E. EVENT kind description consistency

Latest grammar keeps `KIND` as string in accepted contract.
Enumeration-like draft expressions are not mixed into latest contract.

## Notes

This journal entry records documentation-level contract freeze.
Parser/generator implementation updates should be tracked separately when code changes land.
