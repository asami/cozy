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
- [ ] BK12-05: BoK build operational verification
- [ ] BK12-06: Publish dry-run operational verification
- [ ] BK12-07: Scaffold/document drift diagnostics
- [ ] BK12-08: Upload workflow readiness
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

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-12-checklist.md`
- `/Users/asami/src/Project2026/bok-knowlegehub`
