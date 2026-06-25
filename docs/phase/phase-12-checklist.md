# Phase 12 Checklist

This checklist is the authoritative progress tracker for KnowledgeHub BoK
operational onboarding from 2026-06-20 onward.

## BK12-01: Phase 12 Documentation

Status: DONE

- [x] Add `docs/phase/phase-12.md`.
- [x] Add `docs/phase/phase-12-checklist.md`.
- [x] Add Phase 12 to `docs/strategy/cozy-development-strategy.md`.
- [x] Set `docs/phase/README.md` current phase to Phase 12.
- [x] Record Phase 12 as "KnowledgeHub BoK Operational Onboarding".

## BK12-02: KnowledgeHub BoK Source Onboarding

Status: DONE

- [x] Confirm source files are intentionally tracked.
- [x] Confirm generated directories remain ignored.
- [x] Align `README.md` with actual KnowledgeHub BoK operation.
- [x] Align `STRUCTURE.md` with actual `concept/` and `technology/` categories.
- [x] Confirm `src/main/doxsite` contains the expected source categories,
      glossary, history, manual, RDF seeds, and CSS assets.
- [x] Remove legacy generated `src/main/website` from the canonical source tree.

## BK12-03: BoK Config Alignment

Status: DONE

- [x] Update KnowledgeHub `conf/cozy/config.yaml` to current Cozy BoK conventions.
- [x] Keep `.cozy/config.yaml` out of git as a local sensitive settings file.
- [x] Use canonical Docker image `ghcr.io/asami/cozy-toolchain:latest`.
- [x] Confirm `bok.source` matches `src/main/doxsite`.
- [x] Confirm `bok.website`, `bok.antora`, and `bok.doxsite` match generated
      output locations.
- [x] Keep stage and upload workflows explicit and project-owned.

## BK12-04: Local Cozy Command Synchronization

Status: DONE

- [x] Identify why PATH `cozy` still uses older launcher behavior.
- [x] Define or update the local command synchronization procedure.
- [x] Ensure `cozy bok publish` is available outside `sbt run`.
- [x] Document the operational command path used by KnowledgeHub BoK.
- [x] Keep `.cozy/launcher.yaml` out of git as a local sensitive launcher file.

## BK12-05: BoK Build Operational Verification

Status: DONE

- [x] Run `cozy bok build /Users/asami/src/Project2026/bok-knowlegehub` from the
      normal operational entrypoint.
- [x] Confirm `website.d`, `doxsite.d`, and `antora.d` are reproducible as
      generated output directories.
- [x] Confirm generated outputs remain untracked.
- [x] Record build diagnostics and usability gaps.

## BK12-06: Publish Dry-Run Operational Verification

Status: DONE

- [x] Run `cozy bok publish /Users/asami/src/Project2026/bok-knowlegehub --dry-run`
      from the normal operational entrypoint.
- [x] Confirm dry-run output is useful for operation planning.
- [x] If upload workflow is missing, decide whether current preflight behavior is
      acceptable.
- [x] Record any required Cozy improvement for dry-run usability.

## BK12-07: Scaffold / Document Drift Diagnostics

Status: DONE

- [x] Identify scaffold documentation drift found in KnowledgeHub operation.
- [x] Resolve or explicitly decide the missing `website.d/history/2026.html`
      output from the present `src/main/doxsite/history/index.dox` source.
- [x] Resolve or explicitly decide the missing `website.d/manual/index.html`
      output from the present `src/main/doxsite/manual/index.dox` source.
- [x] Decide whether the Antora `Missing component name in start page for site:
      index.adoc` warning requires scaffold, config, or documentation changes.
- [x] Decide whether `cozy bok publish --help` should render help instead of
      starting publish command parsing.
- [x] Decide whether drift should be fixed in scaffold generation, diagnostics,
      or project documentation.
- [x] Add or update tests if Cozy scaffold behavior changes.
- [x] Record residual drift as follow-up work when not fixed in Phase 12.

## BK12-08: Stage And Upload Workflow Readiness

Status: DONE

- [x] Define the KnowledgeHub stage workflow command shape.
- [x] Define the KnowledgeHub upload workflow command shape.
- [x] Keep upload provider implementation out of Cozy.
- [x] Confirm `bok.workflow.stage.command` failure diagnostics are actionable.
- [x] Confirm `bok.workflow.upload.command` failure diagnostics are actionable.
- [x] Confirm production publish cannot silently skip upload.

## BK12-09: Development Findings And Follow-Up Backlog

Status: DONE

- [x] Record operational findings from KnowledgeHub use.
- [x] Separate immediate Phase 12 fixes from future-phase candidates.
- [x] Track local command, config, scaffold, dry-run, stage, and upload workflow gaps.
- [x] Keep findings tied to reproducible BoK operation commands.
- [x] Fix the Antora empty-content `start_page` warning where it is actionable.
- [x] Add config-backed environment injection for project-owned workflow scripts.
- [x] Update the scaffolded AWS upload prototype to consume Cozy-provided environment variables.
- [x] Update KnowledgeHub upload workflow configuration and script without adding real hosting secrets.
- [x] Backlog only the real `AWS_S3_URI` / CloudFront values and production upload confirmation.

## BK12-10: Term Hub And Term-Centric RDF Navigation

Status: DONE

- [x] Treat glossary terms as the primary BoK knowledge hub, not just as a
      supporting index.
- [x] Define SmartDox-owned term metadata as the source of truth for Cozy
      rendering.
- [x] Generate or consume term metadata that connects each term to category,
      article references, related terms, RDF resources, video/publication
      references, and history where available.
- [x] Add a term-centric detail page model for
      `glossary/<category>/<term>.html`.
- [x] Render each term detail page as a hub with definition, reading, aliases,
      related articles, related terms, RDF triples/resources, video links, and
      history/provenance sections when metadata is present.
- [x] Keep `glossary/index.html` as the term dashboard and improve navigation
      from term dashboard to term detail pages.
- [x] Add dashboard/category/article links from term references to term hub
      pages.
- [x] Extend `rdf/index.html` navigation to accept `?term=<slug>` and show the
      neighborhood around the selected term.
- [x] Expose isolated terms, unreferenced terms, and weakly connected terms as
      diagnostics or quality alerts.
- [x] Do not make Cozy re-parse `.dox` source to calculate term relationships;
      SmartDox metadata remains authoritative.
- [x] Add executable specs for term hub page generation and term-centric RDF
      navigation.
- [x] Verify the behavior with KnowledgeHub using `cozy bok build . --strategy
      preview`.

## BK12-11: CAR Project Knowledge And NictKnowledgeHub Project Integration

Status: DONE

- [x] Define how CAR projects are represented as BoK knowledge.
- [x] Define the boundary between CAR project metadata, publication metadata,
      artifact repository contents, and narrative BoK articles.
- [x] Add the NictKnowledgeHub project as a concrete Phase 12 operational
      target alongside KnowledgeHub.
- [x] Confirm whether NictKnowledgeHub uses the same BoK source layout and
      operation conventions as KnowledgeHub.
- [x] Identify required Cozy changes for CAR project pages, project metadata,
      artifact links, and RDF references.
- [x] Verify the approach with a concrete NictKnowledgeHub source or fixture.
- [x] Keep generated CAR artifacts outside BoK source directories.
- [x] Generate CML `model-metadata` sidecars from the modeler generation path.
- [x] Publish CAR CML sidecars under `repository/catalog/car` for BoK
      repository-root operation and `<warehouse>/repository/catalog/car` for
      external warehouse operation.
- [x] Publish CAR model metadata sidecars as JSON and YAML.
- [x] Prefer repository model metadata over direct external project CML scan.
- [x] Keep direct CML scan only as a fallback.
- [x] Register CML model elements, descriptive attributes, and narrative in
      CAR project publication metadata.
- [x] Render CML-derived provisional term hub pages without overwriting
      hand-written glossary term pages.
- [x] Support project-local public repository root mode with `bok.repository`.
- [x] Support `bok.repository` values that point to a non-`repository`
      physical directory while preserving public `/repository/...` paths.
- [x] Keep KnowledgeHub `repository/` out of git while making it a staging and
      upload target.
- [x] Keep explicit `--repository <dir>` available for direct repository-root
      operation.
- [x] Keep explicit `--warehouse <dir>` available for parent warehouse operation
      where the public repository lives under `<warehouse>/repository`.
- [x] Avoid `repository/repository/...` paths when project-local repository mode
      is enabled.
- [x] Replace `.car-product/` with canonical
      `src/main/doxsite/projects/<category>/<slug>/` project knowledge
      packages.
- [x] Require `project.yaml`, `project.yml`, or `project.json` descriptors in
      project knowledge packages.
- [x] Reject `.car-product/` and `.car-product.d/` as obsolete source-package
      layouts.
- [x] Add `cozy bok publish-projects` and remove the obsolete product-package
      command surface.
- [x] Register project metadata under `metadata/projects/car/...` and
      `metadata/catalog/projects/car/...`.

## BK12-12: Scenario Knowledge Management

Status: DONE

- [x] Treat scenarios as a first-class BoK knowledge type.
- [x] Define the common scenario metadata model.
- [x] Keep SmartDox responsible for Dox AST / `DocumentMetaData` only.
- [x] Keep Cozy/Kaleidox responsible for scenario semantic extraction.
- [x] Support simple scenarios.
- [x] Support use case scenarios.
- [x] Support persona plus journey scenarios.
- [x] Define how scenario knowledge links to terms, articles, RDF resources,
      products, projects, and history.
- [x] Define how scenario pages appear in Dashboard, Category, Term Hub, and
      RDF navigation.
- [x] Decide whether scenario source is SmartDox, Markdown, YAML metadata, or a
      source package convention.
- [x] Use glossary-style source layout:
      `src/main/doxsite/scenario/<category>/<scenario>.dox|md|markdown`.
- [x] Add executable specs once the source and rendering contract is decided.
- [x] Add SmartDox `metadata/documents/fragments.json` as the localized
      document-fragment handoff contract.
- [x] Keep SmartDox responsible for locale filtering, glossary auto-linking,
      `site:[...]` self-site link resolution, and manual auto-link exclusions
      before fragment emission.
- [x] Make Cozy Home and Category Dashboard narrative consume SmartDox fragment
      metadata instead of re-parsing BoK source documents.
- [x] When `metadata/glossary/terms.json` is absent, render empty glossary/term
      surfaces instead of reconstructing terms from category source directories.

## BK12-13: Bibliography Knowledge Management

Status: DONE

- [x] Treat bibliography entries as first-class BoK knowledge.
- [x] Decide the canonical source layout for bibliography entries, including
      BoK-wide `src/main/doxsite/bibliography/<slug>.*` and category-local
      `src/main/doxsite/bibliography/<category>/<slug>.*`.
- [x] Support `*.bib.dox` / `*.bib.md` as curated BoK bibliography source paired with
      BibTeX supplement data and prefix-free metadata.
- [x] Decide that bibliography source is SmartDox or Markdown metadata plus
      narrative, with BibTeX as supplemental import/cache data.
- [x] Define bibliography metadata: id, type, title, authors, date, publisher,
      source URL, DOI/ISBN/URN where applicable, category, terms, summary,
      citation text, and BibTeX supplement fields.
- [x] Define how bibliography entries link to terms, Dashboard, Category,
      Term Hub, and RDF navigation.
- [x] Define SmartDox/Cozy responsibility boundaries for bibliography parsing,
      metadata generation, UI rendering, external search, and cache update.
- [x] Generate and consume bibliography metadata for Bibliography Dashboard,
      Home Dashboard, Category related knowledge, Term Hub, and metadata copy.
- [x] Support inline `bib:[citation-key]` citations in SmartDox/Markdown
      article bodies.
- [x] Render article-local References sections from inline citations and
      structured bibliography references.
- [x] Emit article-to-bibliography RDF links with `schema:citation` and
      `dcterms:references`.
- [x] Collect `bibliography.refs` / `references.bibliography` bibid references
      from BoK source documents.
- [x] Materialize undefined bibid references as unresolved `external-ref`
      bibliography entries from SmartDox metadata.
- [x] Accept `bibliography/*.bib` and `bibliography/<category>/*.bib` as
      BibTeX-only bibliography entries and mark them as requiring curation.
- [x] Resolve unresolved bibid entries and explicit `bibtex.source_url`
      entries during normal `cozy bok build`.
- [x] Use local `.bib` files under `src/main/doxsite/bibliography`,
      `repository/bibliography`, and `repository/catalog/bibliography` as
      resolver sources before external bibliography providers.
- [x] Exclude broad `<repository-root>/**/*.bib` and project-root
      `bibliography/**/*.bib` scans from the resolver.
- [x] Support offline/cache-only `cozy bok build --no-bib-service` with
      warnings instead of external bibliography fetches.
- [x] Keep explicit `cozy bok update-bibliography` cache updates and
      `--report-only` / `--no-fetch` reporting without rewriting source.
- [x] Apply cached BibTeX during later builds as effective bibliography
      metadata.
- [x] Sync effective bibliography metadata back into generated site RDF so
      resolved provider entries replace inline citation-key alias nodes.
- [x] Treat `id` as the BoK canonical bibliography ID and `key` as the prose
      citation key; keep `bibtex.*` limited to BibTeX supplement/import data.
- [x] Add executable specs for SmartDox metadata/RDF generation and Cozy
      rendering/search/cache behavior.

## BK12-14: Event Knowledge Management

Status: OPEN

- [ ] Treat events as first-class BoK knowledge.
- [ ] Define event metadata: id, title, type, time or period, actors,
      participants, location, related terms, related scenarios, and evidence.
- [ ] Decide the canonical source layout for event knowledge.
- [ ] Define how event knowledge relates to History pages without duplicating
      History responsibilities.
- [ ] Define how event knowledge relates to CML/statemachine event concepts.
- [ ] Define how events appear in Dashboard, Category, Term Hub, Scenario, and
      RDF navigation.
- [ ] Define SmartDox/Cozy responsibility boundaries for event source parsing
      and semantic extraction.
- [ ] Add executable specs after the source and rendering contract is decided.

## BK12-15: Mono-Koto Analysis Knowledge Model

Status: OPEN

- [ ] Define "mono" as thing/object/entity-oriented knowledge.
- [ ] Define "koto" as event/fact/activity/process-oriented knowledge.
- [ ] Define how mono-koto analysis organizes terms, articles, scenarios,
      events, projects, CML elements, and RDF resources.
- [ ] Define Dashboard and Term Hub views that expose mono-koto structure
      without overwhelming normal users.
- [ ] Define how mono-koto analysis maps to RDF Information View / 1.5+hop
      navigation.
- [ ] Decide whether mono-koto classification is manually authored,
      metadata-derived, model-derived, or mixed.
- [ ] Add design documentation before implementation.
- [ ] Add executable specs after the model and source contract are decided.

## BK12-16: Phase Closure

Status: OPEN

- [ ] Confirm all BK12 items are complete or explicitly deferred.
- [ ] Confirm KnowledgeHub BoK build verification passes.
- [ ] Confirm dry-run verification passes or is documented as a known issue.
- [ ] Update `docs/phase/phase-12.md` closure section.
- [ ] Set `docs/phase/README.md` active phase to none or the next phase.
- [ ] Mark Phase 12 closed in strategy.
