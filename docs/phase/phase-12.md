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
- Align `.cozy/config.yaml`, generated scaffold docs, and current Cozy defaults.
- Verify `bok build`, `bok publish --dry-run`, and future upload workflow
  readiness.
- Identify missing diagnostics or usability gaps from real operation.

## Phase Items

- [x] BK12-01: Phase 12 documentation opened
- [ ] BK12-02: KnowledgeHub BoK source onboarding
- [ ] BK12-03: BoK config alignment
- [ ] BK12-04: Local Cozy command synchronization
- [ ] BK12-05: BoK build operational verification
- [ ] BK12-06: Publish dry-run operational verification
- [ ] BK12-07: Scaffold/document drift diagnostics
- [ ] BK12-08: Upload workflow readiness
- [ ] BK12-09: Development findings and follow-up backlog
- [ ] BK12-10: Phase closure

## Acceptance Criteria

- The KnowledgeHub BoK source tree is intentionally tracked while generated
  directories remain ignored.
- KnowledgeHub `.cozy/config.yaml`, `README.md`, and `STRUCTURE.md` match the
  current Cozy BoK operational model.
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
  `bok.workflow.upload.command` is not configured.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-12-checklist.md`
- `/Users/asami/src/Project2026/bok-knowlegehub`
