# Phase 29: Project-Owned Site BoK Metadata Finalization

Status: complete

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

- Current status: COMPLETE
- Owner: Cozy BoK CLI and configuration
- Update rule: mark work complete only from the Phase 29 checklist.
- Checklist basis: `BM29-01`
- Current step: completed public finalization contract.

Define command parsing, configured input/output resolution, diagnostics,
mutation allowlist, failure atomicity, and help/manual documentation. Extract a
single finalization service from the metadata steps currently embedded in
`cozy bok build`.

### BM29-02: Canonical Metadata and Regression

Stage Status:

- Current status: COMPLETE
- Owner: Cozy BoK/SIE metadata
- Update rule: mark work complete only from the Phase 29 checklist.
- Checklist basis: `BM29-02`
- Current step: completed canonical metadata and regression coverage.

Finalize glossary, versioned RDF graph summary, KnowledgeSource manifest, and
component-reference indexes without running the site build. Prove exact
manifest membership, invalid-input rejection, deterministic repeat execution,
and unchanged `cozy bok build` behavior.

### BM29-03: Project-Owned Site Acceptance

Stage Status:

- Current status: COMPLETE
- Owner: Cozy finalization boundary; downstream generated-site acceptance is
  owned separately by SmartDox Phase 8 `LITERAL8-03`
- Update rule: mark work complete only from the Phase 29 checklist.
- Checklist basis: `BM29-03`

Cozy's public finalization boundary, executable specifications, static
WIP/production-wrapper integration, shell syntax validation, and CLI help
verification are complete. SmartDox Phase 8 `LITERAL8-02` supplies the
generator-side deterministic non-empty literal-label projection without
relaxing Cozy finalizer validation.

Scope-transfer record (2026-08-23): the developer approved completion of this
Cozy Phase while separately retaining generated-site acceptance in SmartDox
Phase 8 `LITERAL8-03`. That downstream stage owns separately authorized site
regeneration; before/after inventories and hashes; successful execution over
the regenerated tree; Textus BoK reader acceptance; and WIP/production failure
path acceptance. This Phase does not claim any of that runtime evidence. The
existing SimpleModeling.org generated graph predates the SmartDox correction,
and Cozy correctly rejected its empty literal labels before mutation.

Scope-transfer record (2026-08-23, Cozy review): independent Phase review
identified `CPB-29-01` (configured BoK source-root admission) and `CPB-29-02`
(unconditional glossary/component-reference resource validation). Under the
developer's standing instruction not to repair newly found corrections in the
current Phase, both are transferred to planned Cozy Phase 34, `BOK34-01` and
`BOK34-02`, respectively. This is an explicit acceptance-boundary change, not
a claim that either correction was implemented. The review therefore does not
clear those two findings; it records their separately owned continuation in
`docs/journal/2026/08/2026-08-23-phase-29-development-candidates.md`
(`DEV-P29-001`).

## Completion criteria

- The public `finalize-metadata` command, its allowlist, validation, atomicity,
  and shared `cozy bok build` behavior are implemented and covered by Cozy
  executable specifications.
- Missing glossary metadata, malformed/incompatible RDF graphs, unsafe paths,
  and inconsistent component references fail before publication of a partial
  handoff.
- The project-owned wrappers invoke the public command as their final metadata
  step without replacing SmartDox, Antora, Arcadia, media, or direct-asset
  processing; their static integration is validated.
- Focused and full Cozy validation pass. Independent Phase review findings are
  explicitly transferred by developer direction to Cozy Phase 34 rather than
  being described as repaired or cleared in this Phase.
- The user-approved downstream acceptance items are recorded under SmartDox
  Phase 8 `LITERAL8-03`, without describing their unrun site regeneration or
  Textus acceptance as completed here.

## Dependencies

- Phase 14 supplies the KnowledgeSource/SIE handoff contract.
- Phase 22 supplies the compatible RDF graph summary contract.
- Phase 28.2 supplies the accepted SimpleModeling.org project-owned site
  integration and regression baseline.
- Textus BoK remains the consumer contract; this Phase does not change its
  resource schemas.

## References

- `docs/phase/phase-29-checklist.md`
- `docs/spec/bok-metadata-finalization.md`
- `docs/design/bok-metadata-finalization.md`
- `docs/phase/phase-14.md`
- `docs/phase/phase-22.md`
- `docs/phase/phase-28.2.md`
- `src/main/scala/cozy/bok/CozyBokSieMetadata.scala`
- `src/main/scala/cozy/bok/CozyBokBuild.scala`
