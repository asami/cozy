# Phase 13 Checklist

This checklist is the authoritative progress tracker for BoK tag knowledge
navigation from 2026-06-28 onward.

## BK13-01: Phase 13 Documentation

Status: DONE

- [x] Add `docs/phase/phase-13.md`.
- [x] Add `docs/phase/phase-13-checklist.md`.
- [x] Add Phase 13 to `docs/strategy/cozy-development-strategy.md`.
- [x] Set `docs/phase/README.md` current phase to Phase 13.
- [x] Record Phase 13 as "BoK Tag Knowledge Navigation".

## BK13-02: Tag Model And Responsibility Boundary

Status: DONE

- [x] Define tag as a lightweight cross-cutting label.
- [x] Keep tags distinct from categories.
- [x] Keep tags distinct from glossary terms.
- [x] Define canonical tag ID / slug normalization.
- [x] Define `.` separated canonical hierarchical tag keys.
- [x] Define `/tags/<segment>/<leaf>.html` hierarchical public URLs.
- [x] Define namespace pages such as `/tags/<namespace>/index.html`.
- [x] Normalize short category-scoped tags such as `yyy` to `category.yyy`.
- [x] Treat dotted tags such as `xxx.yyy` as explicit hierarchical tags
      without category prefix injection.
- [x] Record that the first tag segment can be either a category slug or a
      functional namespace.
- [x] Define display label, category context, and counts.
- [x] Define duplicate and collision handling.
- [x] Decide how tags attach to articles, terms, scenarios, projects,
      bibliography entries, history entries, and publication metadata.
- [x] Record SmartDox and Cozy responsibility boundaries.

## BK13-03: SmartDox Tag Metadata Handoff

Status: DONE

- [x] Confirm SmartDox source metadata key for document tags.
- [x] Ensure Markdown front matter and SmartDox HEAD properties expose tags
      through machine-readable metadata.
- [x] Ensure localized document fragments preserve source tag context.
- [x] Emit dedicated `metadata/tags/tags.json` as the tag handoff contract.
- [x] Define optional tag definition sources under `src/main/doxsite/tags/**`.
- [x] Merge tag definitions with usage-derived tags without requiring every tag
      to have a definition source.
- [x] Add SmartDox executable specs if SmartDox metadata emission changes.

## BK13-04: Cozy Tag Index Reader

Status: DONE

- [x] Add Cozy tag metadata model.
- [x] Read document tags from SmartDox metadata.
- [x] Read dedicated `metadata/tags/tags.json` when the SmartDox handoff exists.
- [x] Fall back to usage-derived tags when `tags.json` is absent.
- [x] Read Cozy-owned tags from scenario metadata.
- [x] Read Cozy-owned tags from project metadata.
- [x] Read bibliography tags from bibliography metadata.
- [x] Read glossary term tags from `terms.json` when present.
- [x] Preserve empty-tag fallback behavior.
- [x] Keep Cozy from re-parsing `.dox` or Markdown source bodies.

## BK13-05: Tag Dashboard And Tag Hub Rendering

Status: DONE

- [x] Generate `tags/index.html`.
- [x] Generate `tags/<tag>.html`.
- [x] Generate hierarchical tag pages such as `tags/<namespace>/<leaf>.html`.
- [x] Generate namespace pages such as `tags/<namespace>/index.html`.
- [x] Show counts by knowledge type.
- [x] Show related articles.
- [x] Show related terms.
- [x] Show related scenarios.
- [x] Show related projects.
- [x] Show related bibliography entries.
- [x] Show related RDF links.
- [x] Keep page layout usable on desktop and mobile.

## BK13-06: Dashboard And Knowledge-Page Tag Links

Status: DONE

- [x] Add compact tag summary to Home Dashboard.
- [x] Add compact tag summary to Category Dashboard.
- [x] Add tag marks / tag chips to Cozy-owned knowledge page headers when
      metadata exists.
- [x] Add tags to article pages when metadata exists.
- [x] Add tags to Term Hub pages when metadata exists.
- [x] Add tags to Scenario pages when metadata exists.
- [x] Add tags to Project pages when metadata exists.
- [x] Add tags to Bibliography pages when metadata exists.
- [x] Avoid noisy empty sections when no tag metadata exists.

## BK13-07: RDF Tag Navigation

Status: DONE

- [x] Add tag filter or tag-neighborhood entry point to RDF Information View.
- [x] Define tag RDF node or relationship representation.
- [x] Link tag pages to RDF filtered views.
- [x] Ensure existing category and term RDF filters remain compatible.
- [x] Add executable specs for tag RDF navigation.

## BK13-08: KnowledgeHub Operational Verification

Status: DONE

- [x] Add representative tags to KnowledgeHub source.
- [x] Run `cozy bok build . --strategy preview --no-bib-service` with the
      current development runtime.
- [x] Confirm Home Dashboard tag summary.
- [x] Confirm Category Dashboard tag summary.
- [x] Confirm `tags/index.html`.
- [x] Confirm at least one `tags/<tag>.html`.
- [x] Confirm `technology.embedding` and `technology.rdf` from
      category-scoped short tags.
- [x] Confirm `knowledge.search` from an explicit hierarchical tag.
- [x] Confirm `tags/technology/index.html`,
      `tags/technology/embedding.html`, and `tags/technology/rdf.html`.
- [x] Confirm `tags/knowledge/index.html` and `tags/knowledge/search.html`.
- [x] Confirm tag links from article and term surfaces where tagged metadata
      exists.
- [x] Confirm tag links from a scenario detail page with representative tagged
      KnowledgeHub metadata.
- [x] Confirm tag links from project and bibliography detail pages with
      representative tagged KnowledgeHub metadata.
- [x] Confirm generated directories remain ignored.

## BK13-09: Tests And Executable Specs

Status: DONE

- [x] Add focused Cozy tag specs.
- [x] Add SmartDox specs if SmartDox metadata changes.
- [x] Add specs for hierarchical tag URLs.
- [x] Add specs for category prefix normalization.
- [x] Add specs for explicit namespace tags.
- [x] Add specs for tag marks / tag chips on Cozy-owned knowledge pages.
- [x] Add specs for tag resource list pages.
- [x] Run focused tag specs.
- [x] Run `sbt --batch test`.
- [x] Run `git diff --check`.

## BK13-11: Component Repository CAR Knowledge

Status: DONE

- [x] Define Project as the BoK knowledge item for program development and CAR
      provider responsibility.
- [x] Define repository CAR knowledge as published CAR artifact knowledge
      sourced from repository catalog metadata.
- [x] Keep `src/main/doxsite/projects/<category>/<slug>/project.yaml|yml|json`
      as the Project knowledge source of truth.
- [x] Read CAR catalog metadata from `repository/catalog/car/*.yaml|json` in
      BoK repository-root operation.
- [x] Read CAR catalog metadata from
      `<warehouse>/repository/catalog/car/*.yaml|json` in warehouse operation.
- [x] Preserve repository catalog metadata as the artifact source of truth;
      do not scan CAR artifact directories as source.
- [x] Generate `metadata/repository/car/index.json`.
- [x] Generate `metadata/repository/car/<module>.json`.
- [x] Generate `repository/car/index.html`.
- [x] Generate `repository/car/<module>/index.html`.
- [x] Generate `repository/car/<module>/<version>.html`.
- [x] Link Project pages to related published CAR versions.
- [x] Link CAR pages back to related Project pages when `project_ref` or
      descriptor metadata can resolve the relationship.
- [x] Show CAR catalog path, artifact path, version, latest/recommended status,
      checksum, and publication status where metadata exists.
- [x] Show component descriptor metadata when available.
- [x] Show ABI manifest metadata when available.
- [x] Show CML sidecar links when available.
- [x] Show model metadata sidecar links when available.
- [x] Inherit or merge tags and terms from Project metadata into related CAR
      entries without replacing explicit CAR metadata.
- [x] Add tag resource page links to related CAR entries.
- [x] Add Term Hub / RDF navigation hooks for CAR-related terms where metadata
      exists.
- [x] Diagnose catalog entries without Project links as unlinked published CARs.
- [x] Diagnose Project CAR references without catalog entries as unpublished or
      unresolved CAR artifacts.
- [x] Diagnose existing CAR archives without component descriptor or ABI
      manifest metadata.
- [x] Diagnose component descriptor and ABI manifest coordinates that differ
      from the repository catalog coordinate.
- [x] Keep CAR build and `publish-car` out of `bok build`; BoK build only
      consumes existing repository metadata.
- [x] Add `CozyBokComponentRepositorySpec`.
- [x] Extend `CozyBokProjectSpec` for Project-to-CAR page links.
- [x] Extend `CozyBokTagSpec` for tag pages that include CAR resources.
- [x] Verify with KnowledgeHub using at least one repository CAR catalog entry.
- [x] Record that Phase 14 SIE CAR/SAR catalog integration builds on this
      generic CAR knowledge layer rather than replacing it.

## BK13-12: Phase Closure

Status: DONE

- [x] Confirm all BK13 items are complete or explicitly bounded.
- [x] Confirm validation passed.
- [x] Confirm KnowledgeHub operational verification passed.
- [x] Update `docs/phase/phase-13.md` closure section.
- [x] Set `docs/phase/README.md` active phase to Phase 14.
- [x] Mark Phase 13 closed in strategy.
