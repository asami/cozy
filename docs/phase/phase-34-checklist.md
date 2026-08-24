# Phase 34 Checklist: BoK Metadata Input Admission Hardening

This checklist is the authoritative progress ledger for Phase 34. It is not a
normative contract.

## BOK34-01: Configured Source-Root Admission

Status: COMPLETE

- [x] Define canonical project-root and symlink-safe admission for configured
      BoK source paths.
- [x] Reject an unsafe, outside-root, non-directory, or symlinked source before
      it can influence glossary or RDF finalization.
- [x] Add Given/When/Then executable specifications for admitted and rejected
      source-path variants, including failure atomicity.

## BOK34-02: Published Resource Validation

Status: COMPLETE

- [x] Validate glossary metadata with its canonical decoder before staging or
      KnowledgeSource manifest declaration.
- [x] Validate every component-reference resource before staging or manifest
      declaration, including indexes without current RDF references.
- [x] Add Given/When/Then executable specifications for malformed glossary and
      component-reference resources and unchanged output after rejection.

## Acceptance and transfer record

`BOK34-01` acceptance evidence: `testOnly cozy.CozyBokMetadataFinalizationSpec`
succeeded with 9 tests on 2026-08-24. The BOK34-01 lightweight Step review
found no Current Boundary Blockers, Hygiene, or Development Candidates.

`BOK34-02` acceptance evidence: the focused metadata finalization spec had 12
passing tests on 2026-08-24. Initial lightweight review found
`CB-BOK34-02-001` in the ordinary direct-copy build path; the frozen review-fix
plus focused closure re-review closed it with zero Current Boundary Blockers.
`HYG-BOK34-02-001` (pre-existing flat executable-spec organization) remains
nonblocking and is not changed. Phase 34 remains in progress pending its final
full validation/release closure.
