# Phase 14: SIE Integration

Status: closed

Start date: 2026-07-13

## Goal

Make SIE (`textus-semantic-integration-engine`) a first-class integration
surface for Cozy BoK and CAR/SAR publication workflows.

Phase 14 connects BoK knowledge, publication registry metadata, SIE component
artifacts, and RDF Information View navigation without making Cozy own SIE's
runtime semantics. Cozy should prepare, validate, publish, and consume the
metadata needed for SIE integration, while SIE remains the semantic integration
runtime.

The first integration contract is the BoK KnowledgeSource manifest that lets SIE
ingest a generated BoK site without scraping rendered HTML:

```text
metadata/cncf/knowledge-source.json
```

## Scope

In scope:

- SIE project registration in BoK project knowledge metadata
- BoK KnowledgeSource manifest output for SIE ingestion
- SIE CAR/SAR repository catalog integration
- SIE runtime and launcher configuration validation for development and release
  operation
- SIE-derived RDF and Information-schema metadata handoff into BoK
- Term Hub, Project, Tag, and RDF Information View navigation for SIE-linked
  knowledge
- KnowledgeHub operational verification using the current SIE component
- executable specs for SIE metadata consumption, diagnostics, and generated
  navigation

Out of scope:

- implementing SIE runtime semantics inside Cozy
- replacing SIE's Information model with Cozy-owned models
- automatic SIE source generation from BoK knowledge
- automatic CML or SIE source modification from diagnostics
- production SIE service hosting automation beyond validation hooks and
  publication metadata
- publishing `/.well-known/cncf-knowledge.json` for this slice
- making SIE scrape rendered BoK HTML

## Phase Items

- [x] BK14-01: Phase 14 documentation opened
- [x] BK14-02: SIE responsibility boundary and integration contract
- [x] BK14-03: BoK KnowledgeSource manifest output
- [x] BK14-04: SIE project metadata and publication registry integration
- [x] BK14-05: SIE CAR/SAR repository catalog integration
- [x] BK14-06: SIE runtime / launcher development configuration validation
- [x] BK14-07: SIE RDF and Information metadata handoff
- [x] BK14-08: BoK UI navigation for SIE-linked knowledge
- [x] BK14-09: KnowledgeHub operational verification
- [x] BK14-10: Tests and executable specs
- [x] BK14-11: Phase closure

## Acceptance Criteria

- BoK project metadata can register an SIE component without scanning external
  source repositories.
- Generated BoK sites publish
  `metadata/cncf/knowledge-source.json` with schema version
  `cncf.knowledge-source.v1`.
- The BoK KnowledgeSource manifest lists relative metadata resources such as
  `metadata/glossary/terms.json` and only lists RDF resources that were
  actually generated.
- The existing `metadata/glossary/terms.json` shape remains stable for SIE v1.
- Cozy can read SIE project/publication metadata and expose it through Project,
  Term Hub, Tag, and RDF navigation surfaces.
- SIE CAR/SAR repository catalog entries are validated and linked from BoK
  pages.
- Development operation can use launcher development settings to verify current
  SIE, Cozy, CNCF, and Textus integration without release-version mutation.
- SIE-derived RDF and Information-schema metadata can be consumed without Cozy
  reimplementing SIE semantic extraction.
- Missing or stale SIE handoff metadata produces explicit diagnostics.
- KnowledgeHub can build and preview with at least one SIE-linked project.
- SIE can ingest a generated BoK site through the manifest-backed route without
  scraping HTML.
- Existing BoK builds keep working when no SIE metadata exists.

## Progress Notes

- 2026-06-28: Opened Phase 14 as the planned SIE integration phase. The initial
  boundary is that Cozy owns BoK publication integration, diagnostics, and UI
  navigation, while SIE owns semantic integration runtime behavior and
  Information-schema materialization.
- 2026-06-28: Folded the BoK to SIE integration handoff into Phase 14. The
  first concrete contract is `metadata/cncf/knowledge-source.json`, which lets
  SIE ingest BoK glossary and RDF metadata resources without reading rendered
  HTML.
- 2026-07-13: Activated Phase 14 after Phase 13 closed its generic tag and
  Component Repository CAR knowledge layer. BK14-02 is the first active design
  boundary; SIE-specific catalog and runtime behavior must reuse rather than
  duplicate the Phase 13 infrastructure.
- 2026-07-13: Completed BK14-02 with a shared
  `cncf.knowledge-source.v1` envelope for both BoK input and SIE projection
  handoff. Cozy owns publication integration and diagnostics; SIE owns semantic
  materialization. BK14-03 is the next implementation item.
- 2026-07-13: Completed BK14-03. Cozy now publishes a deterministic
  `metadata/cncf/knowledge-source.json`, lists only generated glossary/RDF
  resources, resolves site identity from `site.metadata`, and fails explicitly
  when authored glossary terms lack the SmartDox metadata handoff.
- 2026-07-13: Started BK14-04 by defining public SIE Project metadata for a
  stable projection identifier, optional component artifact, and HTTP(S)
  KnowledgeSource handoff base. Local SIE source paths remain private Cozy
  configuration and are excluded from publication metadata.
- 2026-07-13: Added SIE linkage to Project detail pages. Cozy renders the
  registered projection, component, handoff base, and KnowledgeSource manifest
  without fetching or interpreting external SIE resources during `bok build`.
- 2026-07-13: Added static SIE component-reference diagnostics. Publication
  metadata warns with `sie.project.component.unresolved` when the declared
  component is absent from the repository CAR catalog and does not expose
  private local SIE paths.
- 2026-07-13: Completed BK14-04. SIE-linked Projects now reuse the generic
  Project CML, glossary term, scenario, tag, and RDF relationships. Project
  pages expose those relations without creating an SIE-specific knowledge
  index; SIE-owned Information/RDF resources remain in BK14-07.
- 2026-07-13: Completed BK14-05. `sie.component` resolves a CAR and optional
  `sie.subsystem` resolves a SAR through repository catalog metadata only.
  Project pages link catalog versions, referenced SARs receive index/module/
  version pages, and unresolved artifacts or missing release selectors produce
  stable diagnostics without scanning artifact directories. Explicitly
  referenced development-local CARs join the generic CAR knowledge pages
  without exposing local paths, while conflicting catalogs for one artifact
  identity fail explicitly.
- 2026-07-13: Completed BK14-06 using launcher-native diagnostics rather than a
  duplicate Cozy runtime parser. Development validation selected Cozy
  `0.2.26-SNAPSHOT`, CNCF `0.5.1-SNAPSHOT`, the Textus-to-CNCF development
  route, and the SIE component development classpath without changing release
  coordinates. Development and release commands are recorded in
  `docs/design/bok-sie-runtime-validation.md`.
- 2026-07-13: Completed BK14-07 and BK14-08. Cozy now validates the
  manifest-declared SIE projection handoff, merges Information schemas and
  instances into effective RDF graph metadata, and adds SIE navigation to
  Project, Antora-owned Term/Scenario, Tag, and RDF node pages. Invalid,
  duplicate, unsafe, missing, and stale handoff inputs produce stable
  diagnostics without leaking local paths or causing external HTTP reads.
- 2026-07-13: Completed BK14-09 against
  `/Users/asami/src/Project2026/bok-knowledgehub`. The current Cozy development
  runtime generated the KnowledgeSource manifest, and the current SIE
  operation ingested two terms with `warningCount = 0`, returned
  `knowledgeSpaceState = frame_only`, and included a KnowledgeFrame. The
  SIE-linked NICT KnowledgeHub Project then exposed two Information instances
  through Project, Term, Tag, and RDF navigation. Missing-manifest diagnostics
  were also verified before restoring the successful generated site.
- 2026-07-13: Closed Phase 14 after focused SIE/BoK specs passed and the full
  Cozy test suite completed with 490 tests and no failures. The final
  KnowledgeHub preview build published one SIE projection, two Information
  instances, effective RDF metadata, Project/Term/Tag navigation, and no SIE
  diagnostics. `git diff --check` also passed for the closing changes.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-14-checklist.md`
- `docs/phase/phase-13.md`
- `docs/journal/2026/06/bok-sie-integration-handoff-2026-06-28.md`
- `docs/design/bok-sie-integration-contract.md`
- `docs/design/bok-sie-runtime-validation.md`
- `docs/design/bok-sie-information-handoff.md`
- `docs/design/bok-sie-operational-validation.md`
- `docs/design/bok-rdf-1-5-hop-schema.md`
- `docs/notes/bok-rdf-1-5-hop-neighborhood.md`
