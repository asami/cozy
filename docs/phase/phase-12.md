# Phase 12: KnowledgeHub BoK Operational Onboarding

Status: active

Start date: 2026-06-20

## Goal

Use `/Users/asami/src/Project2026/bok-knowlegehub` as a real BoK operation
driver for `cozy bok`, identify operational friction, and convert the findings
into focused Cozy improvements.

## Target Project

- `/Users/asami/src/Project2026/bok-knowlegehub`

## Themes

- Make the KnowledgeHub BoK source tree cleanly operable by `cozy bok`.
- Align `conf/cozy/config.yaml`, generated scaffold docs, and current Cozy
  defaults.
- Verify `bok build`, `bok stage`, `bok publish --dry-run`, and future upload
  workflow readiness.
- Identify missing diagnostics or usability gaps from real operation.

## Phase Items

- [x] BK12-01: Phase 12 documentation opened
- [x] BK12-02: KnowledgeHub BoK source onboarding
- [x] BK12-03: BoK config alignment
- [x] BK12-04: Local Cozy command synchronization
- [x] BK12-05: BoK build operational verification
- [x] BK12-06: Publish dry-run operational verification
- [x] BK12-07: Scaffold/document drift diagnostics
- [x] BK12-08: Upload workflow readiness
- [x] BK12-09: Development findings and follow-up backlog
- [x] BK12-10: Term hub and term-centric RDF navigation
- [x] BK12-11: CAR product and NictKnowledgeHub project integration
- [ ] BK12-12: Scenario knowledge management
- [ ] BK12-13: Phase closure

## Acceptance Criteria

- The KnowledgeHub BoK source tree is intentionally tracked while generated
  directories remain ignored.
- KnowledgeHub `conf/cozy/config.yaml`, `README.md`, and `STRUCTURE.md` match
  the current Cozy BoK operational model, while `.cozy/config.yaml` remains
  kept as an untracked local sensitive settings file.
- The normal operational `cozy` command can run the required BoK workflow, not
  only `sbt run`.
- `cozy bok build` works for the KnowledgeHub BoK from the operational entrypoint.
- `cozy bok publish --dry-run` either produces useful planning output or records
  a concrete Phase 12 improvement for missing-upload workflow behavior.
- Any discovered scaffold/config/document drift is captured as implementation
  work or follow-up backlog.
- Term-centered BoK operation is specified and implemented enough that glossary
  terms become navigation hubs for related articles, related terms, RDF
  resources, video/publication references, and provenance instead of remaining
  only flat glossary entries.
- RDF exploration supports both whole-graph views and term-neighborhood
  navigation from the term hub.
- CAR product knowledge can be represented as BoK content and connected to the
  NictKnowledgeHub project as an operational target.
- Scenarios can be managed as first-class BoK knowledge items, starting with
  simple scenarios, use cases, and persona plus journey scenarios.

## Progress Notes

- 2026-06-20: Opened Phase 12 for KnowledgeHub BoK operational onboarding.
  Initial exploration found that the source tree is untracked, generated
  directories are ignored, `cozy bok build` works through `sbt run`, the PATH
  `cozy` launcher is older than the repository implementation, and
  optional `bok.workflow.stage.command` is not configured while required
  `bok.workflow.upload.command` is still missing.
- 2026-06-20: Completed BK12-02 source onboarding for KnowledgeHub. The
  canonical source tree is `src/main/doxsite`, `STRUCTURE.md` is kept as a
  human-readable source tree guide rather than build configuration, and legacy
  generated `src/main/website` output was removed from the source tree.
- 2026-06-20: Completed BK12-03 config alignment. KnowledgeHub now keeps main
  BoK operation settings in `conf/cozy/config.yaml`, keeps
  `.cozy/config.yaml` as an untracked local sensitive settings file, and keeps
  explicit project-owned stage/upload workflow commands.
- 2026-06-20: Completed BK12-04 local Cozy command synchronization. The
  operational PATH command is the Coursier-installed `cozy` launcher, local
  `.cozy/launcher.yaml` remains an untracked sensitive runtime override, and
  the launcher help now documents the config split between launcher settings
  and BoK operation settings. Operational probing also confirmed that
  `cozy bok publish` is available outside `sbt run`; `cozy bok publish --help`
  currently starts the publish flow instead of rendering help, so that behavior
  is left as a BK12-07/BK12-06 usability finding.
- 2026-06-20: Completed BK12-05 BoK build operational verification.
  `cozy bok build . --strategy preview` succeeded from the KnowledgeHub root
  through the normal PATH launcher. The build regenerated ignored `website.d`,
  `doxsite.d`, and `antora.d` outputs, produced the operational Home, Concept,
  Technology, Glossary, RDF, and Antora outputs, and left tracked KnowledgeHub
  source files unchanged. BK12-05 completion is limited to build execution and
  git-boundary verification. Antora emitted a non-fatal `Missing component name
  in start page for site: index.adoc` warning, and `history` / `manual` sources
  were present but not emitted as `website.d/history/2026.html` or
  `website.d/manual/index.html`; those output-shape gaps are tracked explicitly
  in BK12-07.
- 2026-06-21: Completed BK12-06 publish dry-run operational verification.
  `cozy bok publish . --dry-run` succeeded from the KnowledgeHub root through
  the normal PATH launcher. The output listed update-publication, build, stage,
  and upload plan steps, and wrote
  `target/cozy-bok/publish/latest/manifest.json` with `dryRun=true`,
  `strategy=production`, planned stage/upload commands, and a skipped
  update-publication step because no `.video/` packages are currently present.
  The dry-run did not create `src/main/publication` or `warehouse`, did not
  update `website.d`, `doxsite.d`, or `antora.d`, and left tracked KnowledgeHub
  source files unchanged. The existing `cozy bok publish --help` behavior still
  needs BK12-07 usability review because it starts command parsing instead of
  rendering help.
- 2026-06-21: Completed BK12-07 scaffold/document drift diagnostics and
  dashboard output cleanup. Home and category `index.html` pages are now
  dashboard-first pages that keep SmartDox dashboard metadata as the source of
  truth while presenting metric cards, distribution/cumulative charts, purpose
  panels, category/article/term sections, and RDF status. Cozy now emits
  `website.d/manual/index.html` and a fallback `website.d/history/index.html`
  when SmartDox does not generate a yearly history page; if a yearly history
  page exists, BoK Console links continue to prefer it. Command-specific help
  for `cozy bok publish --help`, `publish-video --help`,
  `update-publication --help`, `stage --help`, and `upload --help` now renders
  usage without entering preflight or workflow execution. The non-fatal Antora
  warning `Missing component name in start page for site: index.adoc` remains
  a SmartDox/Antora playbook follow-up candidate for BK12-09 because the build
  output is otherwise usable and Cozy should not mask the upstream diagnostic
  without a clear playbook contract change.
- 2026-06-21: Verified BK12-07 against the KnowledgeHub project through the
  normal PATH `cozy` launcher. `cozy bok build . --strategy preview` produced
  dashboard-first Home, Concept, and Technology pages plus
  `website.d/manual/index.html` and `website.d/history/index.html`; generated
  outputs remained ignored and tracked KnowledgeHub source files stayed clean.
  `cozy bok publish --help`, `publish-video --help`,
  `update-publication --help`, `stage --help`, and `upload --help` all rendered
  usage text without starting publication preflight or workflow execution.
- 2026-06-21: Refined the BK12-07 dashboard/source boundary. BoK and category
  `index.dox` files are now treated as narrative sources for the generated
  Home/Category Dashboard pages. Cozy keeps generated metric cards, charts,
  counts, and aggregation in `website.d`, while scaffolded `index.dox` files
  contain source metadata, purpose text, navigation, and operation notes only.
- 2026-06-21: Corrected the narrative rendering approach. Cozy no longer
  converts `index.dox` narrative lines with a local inline HTML parser; it
  parses source documents through SmartDox `Dox2Parser` and renders narrative
  fragments through `Dox2HtmlTransformer`. The executable contract was split
  into `CozyBokDashboardSpec` so Dashboard/narrative behavior is specified
  separately from the broader BoK workflow spec. Unit specs may validate Docker
  image configuration values, but they do not execute Docker images; Docker
  image runtime validation remains a scripted/runtime responsibility.
- 2026-06-21: Made BoK locale handling explicit for Cozy-generated Dashboard
  narrative fragments. `BuildConfig` now carries `defaultLocale` from
  `site.output.default_locale` / `bok.output.default_locale`, and Cozy renders
  narrative fragments from the single canonical `index.dox` through SmartDox's
  locale-aware `LanguageFilterTransformer`. Multi-locale BoKs render localized
  Dashboard pages under each configured locale subdirectory without adding
  per-language index source files.
- 2026-06-21: Fixed the generated Dashboard UI locale/resource boundary.
  Cozy-generated UI messages now use Java `ResourceBundle` properties with
  English as the explicit fallback and UTF-8 resource loading. Multi-locale
  Dashboard pages now compute CSS asset paths from the actual output depth, so
  root, locale-root, category, and special pages link to the shared Antora
  assets consistently.
- 2026-06-21: Completed BK12-08 stage and upload workflow readiness.
  `cozy bok stage .` copied `website.d` into the configured
  `../bok-knowlegehub-website` staging directory and kept KnowledgeHub source
  files clean, with only ignored generated directories present. `cozy bok
  upload .` intentionally failed because `etc/website-upload.sh` is still a
  project-owned placeholder; the diagnostic explains how to replace it with an
  AWS sync / cache invalidation workflow and confirms that Cozy does not embed
  hosting credentials or provider policy. Production `cozy bok publish .`
  executed build and stage, then attempted upload and failed at the upload step
  instead of silently skipping it. The publish manifest recorded
  `stage=succeeded` and `upload=failed`, so the operational boundary is ready
  while real hosting upload configuration remains a BK12-09 follow-up.
- 2026-06-21: Completed BK12-09 residual finding fixes and workflow
  environment injection. SmartDox now omits `site.start_page` from
  empty-content Antora playbooks while preserving component-qualified
  `component::page.adoc` start pages for normal component playbooks, removing
  the KnowledgeHub Antora warning at the source. Cozy workflow execution now
  passes `bok.workflow.<name>.env.*` settings from `conf/cozy/config.*` and
  `.cozy/config.*` to project-owned `stage` and `upload` scripts, with local
  `.cozy` settings overriding public project defaults. The scaffolded
  `etc/website-upload.sh.proto` and KnowledgeHub `etc/website-upload.sh` were
  updated to the same AWS-ready environment-variable contract. No AWS provider
  or credentials were added to Cozy. Remaining backlog is limited to adding
  real project-local `AWS_S3_URI` / CloudFront values and running the
  production upload confirmation. Validation passed with SmartDox
  `DoxSiteGeneratorSpec`, SmartDox `sbt test`, Cozy `CozyBokSpec`, Cozy
  `sbt test`, KnowledgeHub `cozy bok build . --strategy preview`, and the
  expected `cozy bok upload .` missing-`AWS_S3_URI` failure.
- 2026-06-22: Added BK12-10 as the next Phase 12 work item. The intent is to
  make terms the primary BoK hub: SmartDox should provide authoritative
  term-centered metadata, Cozy should render term detail hub pages, and RDF
  navigation should support `?term=<slug>` neighborhoods in addition to the
  whole graph and category views.
- 2026-06-22: Defined BoK Dashboard audience actors in
  `docs/design/bok-dashboard-audience-actors.md`. The canonical actor set is
  User, Knowledge Contributor, BoK Manager, and Site Administrator. The design
  records each actor's dashboard questions, content priorities, and rendering
  implications so future Dashboard work can separate knowledge navigation,
  contribution quality, project management, and site operation concerns.
- 2026-06-22: Reworked Cozy Dashboard rendering to match the single-dashboard
  actor priority `User > Knowledge Contributor > BoK Manager > Site Administrator`.
  Home Dashboard now places Knowledge Entry, Category Matrix, Term Dashboard,
  and RDF Graph navigation before purpose, growth, readiness, and operational
  actions. Category Dashboard now places Term Map, Article Map, and RDF entry
  before category purpose/readiness. Dashboard cards now emit
  `data-bok-actors` metadata so the rendered screen records which audience
  needs each card serves without introducing actor-specific pages or tabs.
- 2026-06-22: Added display-only Dashboard actor filtering. The default view is
  `reader` (User), and optional
  `?actor=all|reader|contributor|project_manager|site_administrator` URL state
  can change visible cards without creating actor-specific pages or any
  authorization semantics.
- 2026-06-22: Completed BK12-10. SmartDox now emits
  `metadata/glossary/terms.json` and adds term IDs to RDF graph metadata.
  Cozy consumes that metadata to render `glossary/index.html` as a term
  dashboard, preserves existing `glossary/<category>/<term>.html` URLs as Term
  Hub pages, copies term metadata into `website.d/metadata`, and adds
  `rdf/index.html?term=<term-id>` filtering. Focused SmartDox and Cozy
  executable specs passed. KnowledgeHub build verification was run with
  `cozy bok build . --strategy preview`; the current KnowledgeHub source has no
  glossary term entries yet, so operational output verified empty
  `metadata/glossary/terms.json` fallback behavior and the RDF/Glossary page
  generation boundary.
- 2026-06-23: Added BK12-11 and BK12-12 before Phase 12 closure. BK12-11 tracks
  CAR product knowledge and NictKnowledgeHub project integration as the next
  concrete BoK operation target. BK12-12 tracks scenarios as first-class BoK
  knowledge, starting with simple scenarios, use cases, and persona plus
  journey scenarios.
- 2026-06-23: Completed BK12-11 CAR product and NictKnowledgeHub integration.
  Cozy now supports `.car-product/` source packages as BoK product knowledge,
  registers CAR product metadata under `src/main/publication`, records
  repository/public CAR artifact references without building or publishing CAR
  artifacts, and keeps `cozy publish-car` as the explicit CAR artifact
  publication path. KnowledgeHub now contains a
  `nict-knowledgehub.car-product/` source package that references the external
  sibling project `../nict-knowledgehub`.
- 2026-06-23: Extended BK12-11 for loose CAR catalog coupling. A
  `.car-product/` package can now omit the CAR product version and let Cozy
  resolve the effective version and artifact file from
  `repository/catalog/car/<module>.yaml` in BoK repository-root operation, or
  `<warehouse>/repository/catalog/car/<module>.yaml` in external warehouse
  operation. The catalog is produced by the `publish-car` deployment flow. This supports three patterns: developing a
  CAR product source package inside a BoK, keeping only non-sensitive external
  linkage metadata in `.car-product/`, and using `.cozy/config.*` for local
  sensitive path overrides such as `bok.projects.<ref>.path`. BoK publication
  metadata remains a consumer of the catalog and never builds or deploys the
  CAR artifact.
- 2026-06-23: Added CML model vocabulary linkage to BK12-11. CAR product
  publication metadata now records CML-defined model elements as BoK knowledge
  hooks, starting with Entity, Value, Powertype, and Statemachine definitions.
  The default CML source is the linked CAR project
  `src/main/cozy/<module>.cml`, and `.car-product/product.yaml` can assign the
  glossary category used for generated term IDs and links. This captures the
  core Cozy technical pattern: CML model definitions are not just build input;
  they are glossary-linked BoK knowledge elements. KnowledgeHub now maps
  NictKnowledgeHub's `KnowledgeItem` entity to the `technology:knowledge-item`
  glossary term link in both publication metadata and the CAR product article.
- 2026-06-23: Extended BK12-11 to use model-compiler metadata as the primary
  CML knowledge source. `modeler-scala` generation now emits
  `target/cozy/model-metadata.json|yaml`, and `publish-car` places CML sidecars
  in `repository/catalog/car/<module>.cml` plus
  `<module>.model-metadata.json|yaml`. BoK CAR product publication now reads
  repository model metadata first, falls back to direct external-project CML scan
  only when sidecars are absent, and renders CML-derived provisional Term Hub
  pages marked `generated-from-cml` / `needs-curation` without overwriting
  hand-written glossary term pages. Focused validation passed with
  `sbt --batch "testOnly cozy.CozyBokCarProductSpec cozy.modeler.ModelerGenerationSpec"`.
- 2026-06-23: Added project-local public repository root mode for
  KnowledgeHub. `bok.repository` now makes BoK publication, video, and CAR
  product metadata resolve artifacts under the configured public repository
  root while keeping public paths as `/repository/...` and avoiding
  `repository/repository/...` duplication. The configured root may be the
  canonical project-local `repository/` directory or another local directory
  such as `public-repository`; Cozy maps physical artifact access to that
  directory, and SmartDox resolves publication metadata paths with
  `repository/...` prefix awareness instead of requiring a generated symlink.
  `repository/` is ignored by git and treated as a public artifact repository
  for staging/upload, while `src/main/publication` remains the BoK-facing
  metadata source of truth. `--repository <dir>` and `bok.repository` point
  directly to the physical repository root; `--warehouse <dir>` and
  `bok.warehouse` point to a parent warehouse whose public repository is
  `<dir>/repository`. KnowledgeHub validation passed with
  `cozy bok doctor`, `cozy bok update-publication`,
  `cozy bok build --strategy preview`, and `cozy bok stage`; the current CAR
  artifact is not deployed yet, so the generated product metadata correctly
  reports a missing artifact in the artifact repository.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-12-checklist.md`
- `/Users/asami/src/Project2026/bok-knowlegehub`
