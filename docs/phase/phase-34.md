# Phase 34: BoK Metadata Input Admission Hardening

Status: planned

Plan date: 2026-08-23

## Goal

Harden the public BoK metadata finalization boundary so every configured source
and every published glossary or component-reference resource is admitted and
validated before it can affect a KnowledgeSource manifest.

This is the explicitly separated correction Phase for Phase 29 review findings
`CPB-29-01` and `CPB-29-02`. No correction is implemented by this planning
record.

## Boundary and invariants

- Canonically resolve configured BoK source paths relative to the selected
  project and reject unsafe, outside-root, or symlinked source paths before
  glossary or RDF decisions are made.
- Validate glossary metadata with its canonical decoder before staging or
  declaring the resource.
- Validate every component-reference resource before staging or declaring it,
  even when no current RDF graph node refers to a component.
- Preserve the public `cozy bok finalize-metadata` command, its metadata
  allowlist, deterministic output, and failure-atomic update behavior.
- Preserve the Phase 29 boundary: do not invoke SmartDox, Dox, Antora,
  Arcadia, Docker, media processing, project workflows, publication, upload,
  or deployment.

## Stages

### BOK34-01: Configured Source-Root Admission

Stage Status:

- Current status: PLANNED
- Owner: Cozy BoK configuration and SIE metadata finalization
- Update rule: mark work complete only from the Phase 34 checklist.
- Checklist basis: `BOK34-01`

Specify, implement, and cover canonical project-root source admission so an
outside-root or symlinked configured source cannot influence finalization.

### BOK34-02: Published Resource Validation

Stage Status:

- Current status: PLANNED
- Owner: Cozy BoK/SIE metadata
- Update rule: mark work complete only from the Phase 34 checklist.
- Checklist basis: `BOK34-02`

Specify, implement, and cover unconditional glossary and component-reference
resource validation before staging or manifest declaration.

## Completion criteria

- Unsafe, outside-root, or symlinked configured sources fail before any
  metadata mutation.
- Malformed glossary and component-reference resources fail even when no graph
  node currently references them.
- Every failure remains atomic and produces a diagnostic that identifies the
  rejected source or resource.
- Executable specifications cover success, failure, and unchanged-output cases
  with Given/When/Then structure.
- Focused/full Cozy validation and review converge without weakening Phase 29
  finalization or its non-site-build boundary.

## Dependencies

- Phase 29 public finalization contract and full-review findings
  `CPB-29-01` / `CPB-29-02`.
- SmartDox Phase 8 `LITERAL8-03` remains a separate generated-site acceptance
  dependency and is not an input-admission workaround.

## References

- `docs/phase/phase-34-checklist.md`
- `docs/phase/phase-29.md`
- `docs/spec/bok-metadata-finalization.md`
- `docs/design/bok-metadata-finalization.md`
- `src/main/scala/cozy/bok/CozyBokSieMetadata.scala`
