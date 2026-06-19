# Phase 11: BoK Publication and Upload Workflow Productionization

Status: active

Start date: 2026-06-19

## Goal

Make `cozy bok publish` safe and repeatable for production BoK publication
operation.

Phase 11 keeps upload policy external. Cozy coordinates preflight, dry-run,
manifest generation, step ordering, and failure diagnostics, while the project
continues to provide its own `bok.workflow.upload.command`.

## Scope

In scope:

- `cozy bok publish --dry-run`
- publication/upload preflight validation
- publish operation manifest under `target/cozy-bok/publish`
- step diagnostics for `update-publication`, `build`, and `upload`
- scaffold/help guidance for upload workflow configuration
- compatibility with existing BoK and video publication commands

Out of scope:

- built-in S3, rsync, GitHub Pages, or CDN providers
- SmartDox execution of video generation
- changes to `bok build` semantics
- production credential management

## Phase Items

- [x] BK11-01: Phase 11 documentation
- [x] BK11-02: Publish dry-run
- [x] BK11-03: Publish preflight
- [x] BK11-04: Publish operation manifest
- [x] BK11-05: Failure diagnostics and recovery
- [x] BK11-06: Upload workflow boundary
- [x] BK11-07: Help and config examples
- [x] BK11-08: Compatibility preservation
- [x] BK11-09: Tests and smoke
- [ ] BK11-10: Phase closure

## Acceptance Criteria

- `cozy bok publish --dry-run` reports the planned publication/build/upload
  steps without modifying publication registry, warehouse, or site output.
- `cozy bok publish` validates upload workflow and core paths before any
  publication/build/upload side effect.
- `cozy bok publish` writes a manifest that records project paths, options,
  discovered `.video/` packages, build/upload commands, and per-step status.
- Build failure prevents upload, and upload failure is reported as an upload
  failure.
- Existing `bok build`, `publish-video`, `update-publication`, `commit`, and
  `upload` behavior remains compatible.

## Progress Notes

- 2026-06-19: Opened Phase 11 for BoK publication and upload workflow
  productionization.

- 2026-06-19: Implemented the Phase 11 productionization slice.
  `cozy bok publish --dry-run` now prints planned steps and writes a target
  manifest without changing publication, warehouse, or site output. Publish
  preflight validates upload configuration and path boundaries before side
  effects. Non-dry-run publish records step status for publication update,
  build, and upload, including build/upload failure diagnostics.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-11-checklist.md`
- `docs/phase/phase-10.md`
