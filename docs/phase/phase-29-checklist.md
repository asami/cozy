# Phase 29 Checklist: Project-Owned Site BoK Metadata Finalization

This checklist is the authoritative progress ledger for Phase 29. It is not a
normative contract.

Phase Plan Gate: PROCEED

- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: high
- runtime_suitability: re-evaluate when this Phase starts

## BM29-01: Public Finalization Contract

Status: COMPLETE

- [x] Add `cozy bok finalize-metadata [<project-dir>]` with the standard BoK
      strategy and project-configuration resolution contract.
- [x] Define an exact metadata mutation allowlist and reject unsafe,
      non-directory, symlinked, or unexpectedly overlapping configured paths.
- [x] Extract/reuse one canonical finalization service shared with
      `cozy bok build`; do not fork manifest or graph normalization logic.
- [x] Ensure the command never invokes SmartDox, Antora, Arcadia, media,
      publication, upload, deployment, or project workflow commands.
- [x] Document command purpose, prerequisites, generated paths, diagnostics,
      and the distinction from `cozy bok build`.

## BM29-02: Canonical Metadata and Regression

Status: COMPLETE

- [x] Copy/retain required SmartDox glossary metadata and fail when declared
      terminology metadata is missing rather than reconstructing it.
- [x] Validate and version RDF graph metadata as
      `cozy.rdf-graph-summary.v1` with canonical kind and source attribution.
- [x] Generate and validate CAR/SAR component-reference indexes from admitted
      repository/publication metadata without building or publishing CAR/SAR.
- [x] Generate `cncf.knowledge-source.v1` from the exact validated resources
      that exist and verify every declared relative child reference.
- [x] Make the handoff failure-atomic and deterministic across repeated runs.
- [x] Add executable specifications for glossary-only, glossary plus RDF,
      component references, missing/invalid inputs, unsafe paths, rollback,
      repeat execution, and no external command execution.
- [x] Regress ordinary `cozy bok build` and production build behavior against
      the shared finalization service.

## BM29-03: Project-Owned Site Acceptance

Status: COMPLETE

- [x] Integrate the public command as the final metadata step of the existing
      SimpleModeling.org WIP and production wrappers without replacing their
      special SmartDox/Antora/Arcadia/media/direct-asset processing.
- [x] Transfer the required before/after inventory and hash evidence to
      SmartDox Phase 8 `LITERAL8-03`; do not claim it from the pre-correction
      SimpleModeling.org generated tree.
- [x] Transfer successful finalizer execution over regenerated output and
      Textus BoK reader acceptance to SmartDox Phase 8 `LITERAL8-03`.
- [x] Transfer WIP/production failure-path acceptance over regenerated output
      to SmartDox Phase 8 `LITERAL8-03`; retain Cozy's static wrapper review
      and failure-atomic executable specifications as Phase 29 evidence.
- [x] Run focused and full Cozy validation, independent Phase review, and
      record the developer-approved transfer of `CPB-29-01` and `CPB-29-02` to
      Cozy Phase 34. Project-owned runtime acceptance is separately owned by
      SmartDox Phase 8 `LITERAL8-03`.

Scope-transfer record (2026-08-23): the developer approved closing Cozy Phase
29 with generated-site runtime acceptance retained as separately verified
SmartDox work. SmartDox Phase 8 `LITERAL8-03` must obtain separately authorized
site-regeneration evidence, prove all graph labels are non-empty (including
`literal::en` and `literal::ja`), run `cozy bok finalize-metadata`, record the
metadata-only inventory/hash delta, and verify the Textus consumer and wrapper
failure paths. No such runtime evidence is claimed by this checklist.

Cozy review-transfer record (2026-08-23): full Phase review reported
`CPB-29-01` for configured source-root admission and `CPB-29-02` for
unconditional glossary/component-reference validation. In accordance with the
developer's instruction to add required corrections as a Phase rather than
repairing them here, planned Cozy Phase 34 owns these corrections. This
checklist does not claim that either finding is resolved. The durable
development-candidate record is `DEV-P29-001` in
`docs/journal/2026/08/2026-08-23-phase-29-development-candidates.md`.

Phase 29 is complete under the developer-approved acceptance change: all
implemented Cozy boundary work is accepted here, while downstream generated-site
acceptance is traceably transferred to SmartDox Phase 8 `LITERAL8-03` and
`CPB-29-01`/`CPB-29-02` are traceably transferred to Cozy Phase 34.
