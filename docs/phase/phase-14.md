# Phase 14: SIE Integration

Status: active

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
- [ ] BK14-03: BoK KnowledgeSource manifest output
- [ ] BK14-04: SIE project metadata and publication registry integration
- [ ] BK14-05: SIE CAR/SAR repository catalog integration
- [ ] BK14-06: SIE runtime / launcher development configuration validation
- [ ] BK14-07: SIE RDF and Information metadata handoff
- [ ] BK14-08: BoK UI navigation for SIE-linked knowledge
- [ ] BK14-09: KnowledgeHub operational verification
- [ ] BK14-10: Tests and executable specs
- [ ] BK14-11: Phase closure

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

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-14-checklist.md`
- `docs/phase/phase-13.md`
- `docs/journal/2026/06/bok-sie-integration-handoff-2026-06-28.md`
- `docs/design/bok-sie-integration-contract.md`
- `docs/design/bok-rdf-1-5-hop-schema.md`
- `docs/notes/bok-rdf-1-5-hop-neighborhood.md`
