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
