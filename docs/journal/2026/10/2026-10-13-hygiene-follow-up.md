# Hygiene follow-up — 2026-10-13

<!-- cncf-phase-finding:HYG-COZY-SYNC-UI-STATUS-001:begin -->
HYG-COZY-SYNC-UI-STATUS-001 — OPEN. Discovery: 2026-10-13, documentation preservation during repository synchronization. Owner repository: Cozy. Category: derived documentation status. Priority: P3. Evidence: the preserved 2026-10-08 UI split additions in docs/phase/README.md:6 and docs/strategy/cozy-development-strategy.md:1811,1825 still project Phase 50/50.1 as PLANNED and the 59 successor outcomes as open, while the canonical Phase 50 and Phase 50.1 documents/checklists record closure. Risk: readers of the derived index or strategy can mistake dated planning for current execution status. This is nonblocking for the documentation preservation boundary: individual Phase/checklist files remain authoritative, and these commits grant no Phase acceptance or successor execution. Maintenance boundary: separately reconcile current index/strategy projections with canonical closure records; preserve the dated split decision and chronological journal. No maintenance task or commit is scheduled by this record.
<!-- cncf-phase-finding:HYG-COZY-SYNC-UI-STATUS-001:end -->
# Repository synchronization hygiene follow-up

Date: 2026-10-13
Workflow: cncf-validated-commit preparation for cncf-repository-sync

## HYG-SYNC-PHASE717-INDEX-01 — OPEN

- Owner: Cozy.
- Evidence: pre-existing Phase 71.7 navigation prose in `docs/phase/README.md`
  still says PLANNED and unstarted; older strategy projections retain that state.
  The canonical Phase/checklist are closed, and the new dated strategy result
  names the actual Phase 71.7 and 71.8 release commits.
- Category: documentation status projection; priority: low.
- Risk: readers of the old navigation prose can mistake completed work for
  planned work. Canonical closure evidence remains authoritative.
- Deferred boundary: reconcile the historical index/strategy projections in a
  separate documentation maintenance task. Preserve dated planning history.
- Independent review: `cozy_sync_docs_review01`, PASS for the selected commits;
  this inherited observation is nonblocking and does not reopen either Phase.

## Repository-sync merge review — 2026-10-13

Independent reviewer: `/root/cozy_sync_merge_review09`, GPT-6.1 Sol / high.
The reviewed product merge passed all nine selected suites (121 tests) and the
independent review has no Current Boundary Blockers. The following exact review
records are OPEN nonblocking maintenance items; this synchronization does not
implement or suppress them. Existing HYG-P57302-001 remains with its original
record in `2026-10-01-phase-57.3-hygiene-follow-up.md`; it is not duplicated.

- **HYG-SYNC-20261013-GIT-DIAGNOSTIC-001 — optional Git provenance emits misleading fatal diagnostics.** Evidence: successful T4 log lines 8–13; `CozyPublicationCompiler.scala:1290–1292`, `1620–1636`; `CozyArticleMediaRegistry.scala:331,639`; `CozyBokSpec.scala:2809,4026–4027`. The legacy video-publication scenario creates a non-Git temporary source tree. `CozyBokPublication.publishVideo` delegates through `CozyArticleMediaVideoCommand.publish`; registry normalization invokes `validateMetadataPublication`, and first publication invokes `publishMetadata`. Each constructs `_metadata_bundle`, issuing two `--show-toplevel` probes and one `HEAD` probe: two constructions explain all six diagnostics. `Try(...).toOption` explicitly treats unavailable provenance as absent; repository name falls back to the source directory name, source path to `"."`, and source commit to `None`. These helpers, scenario and call chain are unchanged by this merge. The observed effect is diagnostic noise and absent optional Git provenance, not failed export admission, registry installation or merge validation. Owner: Cozy publication/compiler maintainers. Follow-up: a separately admitted optional-provenance diagnostic maintenance task, preserving genuine Git failures and existing fallback semantics. This review authorizes no suppression or edit.

- **HYG-SYNC-20261013-TEMP-001 — legacy BoK fixtures use system temporary roots.** Evidence: `CozyBokSpec.scala:4026–4032` calls `Files.createTempDirectory(prefix)` without a `target/` parent. This predates the merge; the newly added Kroki assertion’s own fixture remains under `target/`. Risk: inherited nonconformance with work-artifact placement and less discoverable fixture ownership. Owner: Cozy BoK specification maintainers. Follow-up: a dedicated fixture-maintenance task that preserves all scenario semantics and cleanup.

## Generation compatibility and portable Dart SDK maintenance — 2026-10-13

These pre-existing observations were retained by the standalone repair's
independent focused review. They are nonblocking maintenance items, not
implementation or acceptance requirements for either completed repair.
This journal records their disposition without changing any Phase status or
embedding execution logs or review payloads.

<!-- cncf-phase-finding:HYG-COZY-DART-RUNTIME-REPORT-OWNERSHIP:begin -->
### HYG-COZY-DART-RUNTIME-REPORT-OWNERSHIP — OPEN

The two Dart runtime specifications retain pre-existing private CommandReport case classes owned by their test instances. Shared type-modeling rules require instance-independent data declarations. Move these reports to package-level or companion objects under a separately admitted ownership-maintenance scope; preserve command fields, observations and runtime acceptance.

- Discovery: 2026-10-13, standalone generation compatibility and portable SDK
  repair review, followed by validated-commit preparation.
- Owner repository: Cozy; category: test-support type ownership; priority: P3.
- Evidence: `src/test/scala/cozy/modeler/DartModelRuntimeSpec.scala:142` and
  `src/test/scala/cozy/modeler/DartPackageRuntimeSpec.scala:333` declare private
  instance-owned `CommandReport` case classes. Both declarations predate and
  are unchanged by the SDK discovery repair.
- Risk: test-only report identity remains tied to an enclosing instance rather
  than a stable namespace. Neither report crosses the production ABI boundary.
- Deferred boundary: a separate test-support ownership maintenance task;
  neither SDK discovery nor generated product compatibility requires this move.
- Scheduling: no task or implementation commit is scheduled by this record.
<!-- cncf-phase-finding:HYG-COZY-DART-RUNTIME-REPORT-OWNERSHIP:end -->

<!-- cncf-phase-finding:HYG-COZY-FAILURE-MODEL-ABI-SPEC-GROUPING:begin -->
### HYG-COZY-FAILURE-MODEL-ABI-SPEC-GROUPING — OPEN

Consider shallow which grouping for generation, legacy compatibility, serialization and library admission in FailureModelAbiGenerationSpec. Preserve all behavioral expectations; handle in a separate specification-maintenance task.

- Discovery: 2026-10-13, standalone generation compatibility repair review,
  retained in portable SDK repair review and validated-commit preparation.
- Owner repository: Cozy; category: executable-spec organization; priority: P3.
- Evidence: `src/test/scala/cozy/modeler/FailureModelAbiGenerationSpec.scala:31`
  retains one pre-existing flat group covering several acceptance concerns.
- Risk: navigation and maintenance readability only; this is not a defective
  behavioral assertion or an unverified generation contract.
- Deferred boundary: a separate shallow specification-grouping maintenance
  task preserving every fixture, generated product and behavioral expectation.
- Scheduling: no task or implementation commit is scheduled by this record.
<!-- cncf-phase-finding:HYG-COZY-FAILURE-MODEL-ABI-SPEC-GROUPING:end -->
