# Phase 71.5 Checklist

Phase status: PLANNED
Updated: 2026-10-03
Ledger for: [Phase 71.5](phase-71.5.md)
Primary owner: Cozy
Predecessor: Phase 71.4

Implementation not started. Phase 71.5 is removal-only: no replacement
tamper/contamination protection is authorized.

## P715-01: Complete protection-mechanism inventory

- [ ] Inventory all Cozy-owned tamper/contamination/artifact-protection behavior by purpose across source, tests, design/spec, help, configuration and scaffolds.
- [ ] Include all Phase 71 `retain-only-integrity` rows; publication/distribution is not an exception.
- [ ] Identify callers and supporting models/codecs/diagnostics/tests for every removal target.
- [ ] Confirm the inventory is not a retention taxonomy and grants no preservation exception.

## P715-02: Generated/intermediate protection removal

- [ ] Remove tamper/integrity/change-detection protection from generated and intermediate artifact paths.
- [ ] Remove supporting hash/digest/snapshot/receipt/provenance/duplicate-state protection semantics.
- [ ] Remove protection-only models, fields, codecs, diagnostics, configuration, scaffolds and tests.
- [ ] Confirm recovery semantics rely on regeneration from authoritative source, with no replacement protection mechanism.

## P715-03: Publication/distribution protection removal

- [ ] Remove Cozy-owned publication/WIP/repository/export/CAR/SAR/subcomponent-release artifact-integrity protection.
- [ ] Remove the Phase 71 retained-integrity implementations rather than migrating them.
- [ ] Remove integrity/admission evidence whose product purpose is proving unchanged artifact bytes.
- [ ] Confirm no signature/checksum/snapshot/metadata/receipt/provenance replacement was introduced.

## P715-04: Protection-oriented filesystem machinery removal

- [ ] Remove backup/restore/rollback machinery whose purpose is generated-artifact protection.
- [ ] Remove atomic replacement/fsync/staging/lock/CAS/post-write verification whose purpose is tamper/contamination/partial-artifact protection.
- [ ] Remove copy/install/restore byte revalidation and change-during-operation protection.
- [ ] Confirm ordinary filesystem I/O and failure reporting remain without a replacement safety protocol.

## P715-05: Contract and test simplification

- [ ] Remove design/spec/help promises for deleted tamper/contamination protection.
- [ ] Delete protection-behavior tests rather than translating them into equivalent alternate protection tests.
- [ ] Preserve tests for independent remaining product semantics only.
- [ ] Add bounded structural checks that detect reintroduction or semantic substitution of the removed protection.

## P715-06: Full-codebase removal audit

- [ ] Audit every Cozy subsystem by purpose, not only by protection-related keywords.
- [ ] Confirm no Cozy-owned tamper-prevention, contamination-prevention or generated-artifact integrity logic remains.
- [ ] Confirm publication/distribution has no special integrity exception.
- [ ] Confirm no removed protection semantics survive under hash-free or renamed mechanisms.
- [ ] Confirm no stale protection-only schema/model/codec/field/diagnostic/config/help/spec/test remains.

## Closure

- [ ] RELEASE-71.5-REVIEW: Independent Phase review explicitly checks semantic substitution and reports zero current Phase blockers.
- [ ] RELEASE-71.5-VALIDATION: Focused remaining-behavior validation and repository-full Cozy validation succeed.
- [ ] RELEASE-71.5-AUDIT: The complete purpose-based removal audit is accepted with no retained or replacement protection mechanism.
- [ ] RELEASE-71.5-COMMIT: A distinct Phase release commit records the final simplified contract and closure ledger; no successor starts automatically.
