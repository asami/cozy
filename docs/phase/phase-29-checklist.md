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

Status: PLANNED

- [ ] Integrate the public command as the final metadata step of the existing
      SimpleModeling.org WIP and production wrappers without replacing their
      special SmartDox/Antora/Arcadia/media/direct-asset processing.
- [ ] Record before/after inventories and hashes proving that only the admitted
      machine-metadata paths change during finalization.
- [ ] Verify the finalized manifest declares generated glossary and compatible
      RDF graph resources and that Textus BoK accepts the handoff.
- [ ] Verify the WIP and production wrappers fail clearly when required BoK
      metadata is missing or incompatible.
- [ ] Run focused and full Cozy validation, project-owned wrapper acceptance,
      independent review, any bounded repair/re-review, and ledger convergence
      before closing the Phase.

Phase 29 closes only after all three stages are complete and the accepted
SimpleModeling.org evidence demonstrates preservation of its special site
workflow.
