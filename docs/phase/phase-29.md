# Phase 29: Project-Owned Site BoK Metadata Finalization

Status: planned

Plan date: 2026-08-16

Phase Plan Gate: PROCEED

- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: high
- runtime_suitability: re-evaluate when this Phase starts
- source: SimpleModeling.org production/WIP BoK operation follow-up

## Goal

Add a public Cozy boundary that finalizes Textus BoK machine metadata for an
already generated project-owned site. A site such as SimpleModeling.org must be
able to retain its own SmartDox, Antora, Arcadia, media-registration, and direct
asset workflow, then invoke Cozy only for canonical KnowledgeSource, RDF graph
summary, and component-reference metadata.

The intended command surface is:

```text
cozy bok finalize-metadata [<project-dir>] [--strategy wip|draft|preview|production]
```

The command reads the configured BoK source and generated-output paths. It does
not run a site build and does not replace project-owned orchestration.

## Boundary and invariants

- Operate only on an existing generated `doxsite.d`/`website.d`-equivalent
  tree selected through normal Cozy project configuration.
- Reuse the same canonical metadata normalization and validation contract as
  `cozy bok build`; do not create a second KnowledgeSource implementation.
- Preserve every rendered HTML, media, Antora, Arcadia, JavaScript, CSS, and
  direct-asset file outside the explicit machine-metadata output set.
- Never delete or recreate the site root, invoke SmartDox, Antora, Arcadia,
  media generation/registration, upload, publish, deploy, or a project-owned
  workflow command.
- Require SmartDox-owned glossary metadata when the source declares glossary
  terms. Cozy normalizes and validates metadata but does not reconstruct
  missing terminology content.
- Version a compatible RDF graph as `cozy.rdf-graph-summary.v1`, add the
  canonical `rdf-graph-summary` kind and source attribution, and reject an
  incompatible graph instead of silently publishing it.
- Generate `cncf.knowledge-source.v1` from files that actually exist after
  finalization. Include glossary, RDF, and component-reference resources only
  when their corresponding validated artifacts exist.
- Generate and validate CAR/SAR component-reference indexes from admitted
  repository/publication evidence without building or publishing components.
- Make the metadata update failure-atomic: validation failure must not leave a
  partially updated KnowledgeSource handoff.
- Repeated finalization over unchanged inputs must be deterministic.
- Keep `cozy bok build` behavior compatible and implement it through the same
  shared finalization service where practical.

## Non-goals

- Moving SimpleModeling.org-specific site generation into Cozy.
- Replacing `runweb-wip.sh` or `runweb-production.sh` with `cozy bok build`.
- Reading rendered article HTML as a new Textus BoK resource kind.
- Inventing glossary terms, graph relationships, or component identities that
  are absent from source/repository metadata.
- Uploading or activating the finalized generation in Textus BoK.

## Stages

### BM29-01: Public Finalization Contract

Stage Status:

- Current status: PLANNED
- Owner: Cozy BoK CLI and configuration
- Update rule: mark work complete only from the Phase 29 checklist.
- Checklist basis: `BM29-01`

Define command parsing, configured input/output resolution, diagnostics,
mutation allowlist, failure atomicity, and help/manual documentation. Extract a
single finalization service from the metadata steps currently embedded in
`cozy bok build`.

### BM29-02: Canonical Metadata and Regression

Stage Status:

- Current status: PLANNED
- Owner: Cozy BoK/SIE metadata
- Update rule: mark work complete only from the Phase 29 checklist.
- Checklist basis: `BM29-02`

Finalize glossary, versioned RDF graph summary, KnowledgeSource manifest, and
component-reference indexes without running the site build. Prove exact
manifest membership, invalid-input rejection, deterministic repeat execution,
and unchanged `cozy bok build` behavior.

### BM29-03: Project-Owned Site Acceptance

Stage Status:

- Current status: PLANNED
- Owner: Cozy with SimpleModeling.org as the acceptance driver
- Update rule: mark work complete only from the Phase 29 checklist.
- Checklist basis: `BM29-03`

Invoke the public finalizer after the existing SimpleModeling.org WIP and
production site workflows. Verify that Textus BoK receives the generated
glossary and compatible graph handoff while all project-owned pages, media,
Arcadia output, and Knowledge Graph UI remain unchanged outside the metadata
allowlist.

## Completion criteria

- The public finalization command succeeds on a supported existing site tree
  without invoking or deleting any site-generation output.
- The resulting KnowledgeSource manifest and every declared child resource are
  present, compatible, and accepted by the Textus BoK reader contract.
- Missing glossary metadata, malformed/incompatible RDF graphs, unsafe paths,
  and inconsistent component references fail before publication of a partial
  handoff.
- Exact before/after path and hash evidence shows that non-metadata
  SimpleModeling.org output is preserved.
- Repeated execution is deterministic, focused/full Cozy tests pass, the
  existing `cozy bok build` path regresses cleanly, and independent review
  converges with no actionable findings.

## Dependencies

- Phase 14 supplies the KnowledgeSource/SIE handoff contract.
- Phase 22 supplies the compatible RDF graph summary contract.
- Phase 28.2 supplies the accepted SimpleModeling.org project-owned site
  integration and regression baseline.
- Textus BoK remains the consumer contract; this Phase does not change its
  resource schemas.

## References

- `docs/phase/phase-29-checklist.md`
- `docs/phase/phase-14.md`
- `docs/phase/phase-22.md`
- `docs/phase/phase-28.2.md`
- `src/main/scala/cozy/bok/CozyBokSieMetadata.scala`
- `src/main/scala/cozy/bok/CozyBokBuild.scala`
