# Phase 34 Checklist: BoK Metadata Input Admission Hardening

This checklist is the authoritative progress ledger for Phase 34. It is not a
normative contract.

## BOK34-01: Configured Source-Root Admission

Status: PLANNED

- [ ] Define canonical project-root and symlink-safe admission for configured
      BoK source paths.
- [ ] Reject an unsafe, outside-root, non-directory, or symlinked source before
      it can influence glossary or RDF finalization.
- [ ] Add Given/When/Then executable specifications for admitted and rejected
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

Phase 29 full review on 2026-08-23 reported `CPB-29-01` (configured
source-root admission) and `CPB-29-02` (unconditional glossary and
component-reference validation). The developer directed that required
corrections be added as a separate Phase rather than repaired in Phase 29.

No implementation, validation, or acceptance is claimed by this planned Phase.
