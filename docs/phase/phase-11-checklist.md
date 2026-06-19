# Phase 11 Checklist

This checklist is the authoritative progress tracker for BoK publication and
upload workflow productionization from 2026-06-19 onward.

## BK11-01: Phase 11 Documentation

Status: DONE

- [x] Add `docs/phase/phase-11.md`.
- [x] Add `docs/phase/phase-11-checklist.md`.
- [x] Add Phase 11 to `docs/strategy/cozy-development-strategy.md`.
- [x] Set `docs/phase/README.md` current phase to Phase 11.
- [x] Record Phase 11 as "BoK Publication and Upload Workflow Productionization".

## BK11-02: Publish Dry-Run

Status: DONE

- [x] Add `cozy bok publish <project-dir> --dry-run`.
- [x] Ensure dry-run prints planned `update-publication -> build -> upload` steps.
- [x] Ensure dry-run does not modify `src/main/publication`.
- [x] Ensure dry-run does not modify `warehouse`.
- [x] Ensure dry-run does not generate or replace `website.d` / `doxsite.d`.
- [x] Ensure dry-run does not execute upload workflow commands.
- [x] Keep dry-run independent of Docker, VOICEVOX, ffmpeg, Remotion, Playwright, and whisper.cpp.

## BK11-03: Publish Preflight

Status: DONE

- [x] Validate upload workflow configuration before publication/build/upload side effects.
- [x] Validate BoK source directory exists.
- [x] Validate publication registry path is allowed and writable.
- [x] Validate warehouse path is allowed and writable.
- [x] Validate reserved path collisions are rejected.
- [x] Validate configured upload command is present for `bok publish`.
- [x] Ensure preflight failures report the exact failed condition and no later step runs.

## BK11-04: Publish Operation Manifest

Status: DONE

- [x] Write publish operation manifest under `target/cozy-bok/publish/...`.
- [x] Include project, source, publication, warehouse, strategy, dryRun, force.
- [x] Include discovered `.video/` packages.
- [x] Include planned or actual publication artifacts.
- [x] Include planned or actual build commands.
- [x] Include upload workflow command metadata.
- [x] Include per-step status: planned, skipped, succeeded, failed.
- [x] Ensure manifest is deterministic enough for tests, avoiding unstable timestamps where not needed.

## BK11-05: Failure Diagnostics And Recovery

Status: DONE

- [x] Report step start/success/failure for `update-publication`, `build`, and `upload`.
- [x] Ensure build failure prevents upload.
- [x] Ensure upload failure is reported as upload failure, not generic publish failure.
- [x] Ensure partial completion is visible in manifest/output.
- [x] Ensure re-running with existing publication/warehouse artifacts remains safe.
- [x] Preserve existing `--force` behavior for publication replacement.

## BK11-06: Upload Workflow Boundary

Status: DONE

- [x] Keep upload implementation as configured external workflow command.
- [x] Do not add S3/rsync/GitHub Pages built-in upload providers in Phase 11.
- [x] Document standard `bok.workflow.upload.command` examples.
- [x] Ensure `bok upload` existing behavior remains compatible.
- [x] Ensure `bok publish` does not silently skip unconfigured upload.

## BK11-07: Help And Config Examples

Status: DONE

- [x] Update `cozy help` for `bok publish --dry-run`.
- [x] Update scaffolded `.cozy/config.yaml` example with upload workflow guidance.
- [x] Add docs note explaining preflight, dry-run, manifest, and retry behavior.
- [x] Keep existing Phase 10 command surface documentation valid.

## BK11-08: Compatibility Preservation

Status: DONE

- [x] Preserve existing `cozy bok build`.
- [x] Preserve existing `cozy bok publish-video`.
- [x] Preserve existing `cozy bok update-publication`.
- [x] Preserve existing `cozy bok commit`.
- [x] Preserve existing `cozy bok upload`.
- [x] Preserve existing top-level `cozy publish-video`.
- [x] Preserve existing SmartDox publication metadata consumption.
- [x] Do not require external media tools for metadata-only tests.

## BK11-09: Tests And Smoke

Status: DONE

- [x] Add Cozy tests for `bok publish --dry-run`.
- [x] Add Cozy tests for preflight failures.
- [x] Add Cozy tests for operation manifest content.
- [x] Add Cozy tests for build failure preventing upload.
- [x] Add Cozy tests for upload failure diagnostics.
- [x] Add Cozy tests for existing workflow compatibility.
- [x] Add or update scripted smoke for publish dry-run if practical.
- [x] Run `sbt --batch "testOnly cozy.CozyBokSpec"`.
- [x] Run `sbt --batch "testOnly cozy.video.CozyVideoSpec"`.
- [x] Run `sbt --batch test`.
- [x] Run `git diff --check`.

## BK11-10: Phase Closure

Status: DONE

- [x] Confirm all BK11 items are complete.
- [x] Confirm Cozy tests pass.
- [x] Confirm scripted smoke passes or document why it is deferred.
- [x] Update `docs/phase/phase-11.md` closure section.
- [x] Set `docs/phase/README.md` active phase to none.
- [x] Mark Phase 11 closed in strategy.
