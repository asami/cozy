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

Status: PLANNED

- [ ] Validate glossary metadata with its canonical decoder before staging or
      KnowledgeSource manifest declaration.
- [ ] Validate every component-reference resource before staging or manifest
      declaration, including indexes without current RDF references.
- [ ] Add Given/When/Then executable specifications for malformed glossary and
      component-reference resources and unchanged output after rejection.

## Acceptance and transfer record

`BOK34-01` acceptance evidence: `testOnly cozy.CozyBokMetadataFinalizationSpec`
succeeded with 9 tests on 2026-08-24. The BOK34-01 lightweight Step review
found no Current Boundary Blockers, Hygiene, or Development Candidates.

`BOK34-02` / `CPB-29-02` remains PLANNED. Phase 34 is not yet complete.
