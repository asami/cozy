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
- [ ] BK12-09: Development findings and follow-up backlog
- [ ] BK12-10: Phase closure

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

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-12-checklist.md`
- `/Users/asami/src/Project2026/bok-knowlegehub`
