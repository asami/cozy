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

## BK12-11: Phase Closure

Status: OPEN

- [ ] Confirm all BK12 items are complete or explicitly deferred.
- [ ] Confirm KnowledgeHub BoK build verification passes.
- [ ] Confirm dry-run verification passes or is documented as a known issue.
- [ ] Update `docs/phase/phase-12.md` closure section.
- [ ] Set `docs/phase/README.md` active phase to none or the next phase.
- [ ] Mark Phase 12 closed in strategy.
