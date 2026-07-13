# Phase 14 Checklist

This checklist is the authoritative progress tracker for SIE integration after
Phase 13.

## BK14-01: Phase 14 Documentation

Status: DONE

- [x] Add `docs/phase/phase-14.md`.
- [x] Add `docs/phase/phase-14-checklist.md`.
- [x] Add Phase 14 to `docs/strategy/cozy-development-strategy.md`.
- [x] Record Phase 14 as "SIE Integration".

## BK14-02: SIE Responsibility Boundary And Integration Contract

Status: DONE

- [x] Define SIE as `textus-semantic-integration-engine` in Cozy docs.
- [x] Record the SIE baseline commit for BoK KnowledgeSource ingestion:
      `64e035d Add BoK KnowledgeSource ingestion`.
- [x] Keep Cozy responsible for BoK publication integration, validation,
      diagnostics, and UI navigation.
- [x] Keep SIE responsible for semantic integration runtime behavior.
- [x] Keep SIE responsible for Information-schema materialization.
- [x] Keep SIE responsible for reading BoK metadata resources through the
      BoK KnowledgeSource route.
- [x] Define the machine-readable metadata handoff Cozy consumes from SIE.
- [x] Define the machine-readable BoK KnowledgeSource manifest that Cozy emits
      for SIE.
- [x] Define missing, stale, and incompatible SIE handoff diagnostics.
- [x] Record that Cozy must not silently regenerate SIE semantic outputs.
- [x] Record that SIE must not scrape rendered BoK HTML for this integration.

## BK14-03: BoK KnowledgeSource Manifest Output

Status: DONE

- [x] Generate `website.d/metadata/cncf/knowledge-source.json`.
- [x] Use public URL path `/metadata/cncf/knowledge-source.json`.
- [x] Use `schemaVersion = cncf.knowledge-source.v1`.
- [x] Use `kind = bok-site`.
- [x] Resolve `sourceRef.kind = bok-site`.
- [x] Resolve manifest `id` and `sourceRef.value` from the BoK publication or
      site key.
- [x] Resolve `sourceRef.uri` from the configured public site base URL when
      available.
- [x] Include `glossary-terms` resource when
      `metadata/glossary/terms.json` is emitted.
- [x] Preserve the current `metadata/glossary/terms.json` top-level
      `{ "terms": [] }` shape for SIE v1.
- [x] Include `rdf-jsonld` only when `site.jsonld` is generated.
- [x] Include `rdf-turtle` only when `site.ttl` is generated.
- [x] Include `rdf-graph-summary` only when `metadata/rdf/graph.json` is
      generated.
- [x] Keep all `resources[].href` values relative to the site base URI.
- [x] Do not publish `/.well-known/cncf-knowledge.json` for this slice.
- [x] Do not copy rendered HTML into the manifest.
- [x] Add diagnostics if an expected required metadata resource is missing.

## BK14-04: SIE Project Metadata And Publication Registry Integration

Status: IN PROGRESS

- [x] Define BoK project metadata fields for SIE-linked projects.
- [x] Register SIE projects through `src/main/doxsite/projects/<category>/<slug>`
      and publication metadata.
- [ ] Link SIE project metadata to CML, glossary terms, scenarios, tags, and RDF
      where metadata exists.
- [ ] Keep external SIE source paths in local config rather than public source.
- [ ] Add diagnostics for unresolved SIE project refs.

## BK14-05: SIE CAR/SAR Repository Catalog Integration

Status: TODO

- [ ] Reuse the generic Component Repository CAR knowledge layer from BK13-11
      for CAR catalog reading, Project-to-CAR links, and CAR artifact pages.
- [ ] Read SIE CAR/SAR catalog entries through the existing repository catalog
      boundary.
- [ ] Link SIE artifact versions from Project pages.
- [ ] Validate recommended/latest stable SIE artifact metadata.
- [ ] Preserve repository/catalog as metadata source; do not scan artifact
      directories as source.
- [ ] Add executable specs for SIE repository catalog links and diagnostics.

## BK14-06: SIE Runtime And Launcher Development Configuration Validation

Status: TODO

- [ ] Document launcher development settings used for SIE integration tests.
- [ ] Verify development Cozy, CNCF, Textus, and SIE runtime paths without
      mutating published release coordinates.
- [ ] Add diagnostics that show which launcher/runtime path is active.
- [ ] Add validation commands for SIE development and release operation.
- [ ] Keep release-version fixes behind SNAPSHOT version changes.

## BK14-07: SIE RDF And Information Metadata Handoff

Status: TODO

- [ ] Define SIE RDF / Information metadata files consumed by Cozy.
- [ ] Merge SIE RDF handoff into BoK RDF Information View without re-extraction.
- [ ] Show SIE Information-centered nodes and anchors in RDF node details.
- [ ] Link SIE Information nodes to terms, scenarios, projects, and tags where
      metadata exists.
- [ ] Add diagnostics when SIE RDF handoff is expected but missing.
- [ ] Treat `rdf_refs` in `terms.json` as supplementary evidence or
      relationship candidates, not confirmed RDF anchors by default.

## BK14-08: BoK UI Navigation For SIE-Linked Knowledge

Status: TODO

- [x] Add SIE-linked Project page sections.
- [ ] Add SIE linkage to Term Hub when terms carry CML/SIE metadata.
- [ ] Add SIE-linked entries to tag resource pages.
- [ ] Add SIE neighborhood entry points in RDF Information View.
- [ ] Keep UI fallback empty when no SIE metadata exists.

## BK14-09: KnowledgeHub Operational Verification

Status: TODO

- [ ] Add or identify one KnowledgeHub SIE-linked project.
- [ ] Run `cozy bok build . --strategy preview`.
- [ ] Confirm `website.d/metadata/cncf/knowledge-source.json` exists.
- [ ] Confirm manifest-backed SIE ingestion uses
      `/metadata/cncf/knowledge-source.json`.
- [ ] Confirm SIE ingestion reports `warningCount = 0` for the generated site.
- [ ] Confirm SIE ingestion `termCount` matches `terms.json`.
- [ ] Confirm SIE ingestion can produce `knowledgeSpaceState = frame_only` with
      `registerKnowledgeSpace=false`.
- [ ] Confirm SIE ingestion includes a knowledge frame when
      `includeKnowledgeFrame=true`.
- [ ] Confirm Project page SIE linkage.
- [ ] Confirm Term Hub and Tag navigation for SIE-linked metadata.
- [ ] Confirm RDF Information View exposes SIE-linked nodes when metadata exists.
- [ ] Confirm missing SIE metadata diagnostics are explicit.
- [ ] Confirm generated directories remain ignored.

## BK14-10: Tests And Executable Specs

Status: TODO

- [ ] Add focused Cozy SIE integration specs.
- [ ] Add BoK KnowledgeSource manifest generation specs.
- [ ] Assert manifest has `resources[].kind = glossary-terms`.
- [ ] Assert manifest has
      `resources[].href = metadata/glossary/terms.json`.
- [ ] Assert manifest `resources.href` values are relative paths, not absolute
      URLs.
- [ ] Assert no `/.well-known/cncf-knowledge.json` output is required.
- [ ] Keep existing `terms.json` specs unchanged unless the current contract is
      actually broken.
- [x] Add SIE project metadata consumption specs.
- [ ] Add SIE repository catalog link specs.
- [ ] Add SIE RDF handoff specs.
- [ ] Add SIE UI navigation specs.
- [ ] Run focused SIE specs.
- [ ] Run `sbt --batch test`.
- [ ] Run `git diff --check`.

## BK14-11: Phase Closure

Status: TODO

- [ ] Confirm all BK14 items are complete or explicitly bounded.
- [ ] Confirm validation passed.
- [ ] Confirm KnowledgeHub operational verification passed.
- [ ] Update `docs/phase/phase-14.md` closure section.
- [ ] Set `docs/phase/README.md` active phase to the next phase or none.
- [ ] Mark Phase 14 closed in strategy.
