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

Status: DONE

- [x] Define BoK project metadata fields for SIE-linked projects.
- [x] Register SIE projects through `src/main/doxsite/projects/<category>/<slug>`
      and publication metadata.
- [x] Link SIE project metadata to CML, glossary terms, scenarios, tags, and RDF
      where metadata exists.
- [x] Keep external SIE source paths in local config rather than public source.
- [x] Add diagnostics for unresolved SIE project refs.

## BK14-05: SIE CAR/SAR Repository Catalog Integration

Status: DONE

- [x] Reuse the generic Component Repository CAR knowledge layer from BK13-11
      for CAR catalog reading, Project-to-CAR links, and CAR artifact pages.
- [x] Read SIE CAR/SAR catalog entries through the existing repository catalog
      boundary.
- [x] Link SIE artifact versions from Project pages.
- [x] Validate recommended/latest stable SIE artifact metadata.
- [x] Preserve repository/catalog as metadata source; do not scan artifact
      directories as source.
- [x] Materialize explicitly referenced development-local CAR catalogs through
      the generic CAR knowledge surface without publishing local paths.
- [x] Reject conflicting CAR/SAR catalogs for one artifact identity instead of
      selecting a repository by filesystem order.
- [x] Add executable specs for SIE repository catalog links and diagnostics.

## BK14-06: SIE Runtime And Launcher Development Configuration Validation

Status: DONE

- [x] Document launcher development settings used for SIE integration tests.
- [x] Verify development Cozy, CNCF, Textus, and SIE runtime paths without
      mutating published release coordinates.
- [x] Use each launcher's native diagnostics to show which launcher/runtime
      path is active; Cozy runtime does not reconstruct launcher config.
- [x] Add validation commands for SIE development and release operation.
- [x] Keep release-version fixes behind SNAPSHOT version changes.

## BK14-07: SIE RDF And Information Metadata Handoff

Status: DONE

- [x] Define SIE RDF / Information metadata files consumed by Cozy.
- [x] Merge SIE RDF handoff into BoK RDF Information View without re-extraction.
- [x] Show SIE Information-centered nodes and anchors in RDF node details.
- [x] Link SIE Information nodes to terms, scenarios, projects, and tags where
      metadata exists.
- [x] Add diagnostics when SIE RDF handoff is expected but missing.
- [x] Treat `rdf_refs` in `terms.json` as supplementary evidence or
      relationship candidates, not confirmed RDF anchors by default.

## BK14-08: BoK UI Navigation For SIE-Linked Knowledge

Status: DONE

- [x] Add SIE-linked Project page sections.
- [x] Add SIE linkage to Term Hub when terms carry CML/SIE metadata.
- [x] Add SIE-linked entries to tag resource pages.
- [x] Add SIE neighborhood entry points in RDF Information View.
- [x] Keep UI fallback empty when no SIE metadata exists.

## BK14-09: KnowledgeHub Operational Verification

Status: DONE

- [x] Add or identify one KnowledgeHub SIE-linked project.
- [x] Run `cozy bok build . --strategy preview`.
- [x] Confirm `website.d/metadata/cncf/knowledge-source.json` exists.
- [x] Confirm manifest-backed SIE ingestion uses
      `/metadata/cncf/knowledge-source.json`.
- [x] Confirm SIE ingestion reports `warningCount = 0` for the generated site.
- [x] Confirm SIE ingestion `termCount` matches `terms.json`.
- [x] Confirm SIE ingestion can produce `knowledgeSpaceState = frame_only` with
      `registerKnowledgeSpace=false`.
- [x] Confirm SIE ingestion includes a knowledge frame when
      `includeKnowledgeFrame=true`.
- [x] Confirm Project page SIE linkage.
- [x] Confirm Term Hub and Tag navigation for SIE-linked metadata.
- [x] Confirm RDF Information View exposes SIE-linked nodes when metadata exists.
- [x] Confirm missing SIE metadata diagnostics are explicit.
- [x] Confirm generated directories remain ignored.

## BK14-10: Tests And Executable Specs

Status: DONE

- [x] Add focused Cozy SIE integration specs.
- [x] Add BoK KnowledgeSource manifest generation specs.
- [x] Assert manifest has `resources[].kind = glossary-terms`.
- [x] Assert manifest has
      `resources[].href = metadata/glossary/terms.json`.
- [x] Assert manifest `resources.href` values are relative paths, not absolute
      URLs.
- [x] Assert no `/.well-known/cncf-knowledge.json` output is required.
- [x] Keep existing `terms.json` specs unchanged unless the current contract is
      actually broken.
- [x] Add SIE project metadata consumption specs.
- [x] Add SIE repository catalog link specs.
- [x] Add SIE RDF handoff specs.
- [x] Add SIE UI navigation specs.
- [x] Run focused SIE specs.
- [x] Run `sbt --batch test`.
- [x] Run `git diff --check`.

## BK14-11: Phase Closure

Status: DONE

- [x] Confirm all BK14 items are complete or explicitly bounded.
- [x] Confirm validation passed.
- [x] Confirm KnowledgeHub operational verification passed.
- [x] Update `docs/phase/phase-14.md` closure section.
- [x] Set `docs/phase/README.md` active phase to the next phase or none.
- [x] Mark Phase 14 closed in strategy.
