# Phase 71 Hygiene Follow-Up

Date: 2026-09-30

Source: independent PHASE-71 full review

## HYG-P71-FULL-001 — Storyboard build-mode spec grouping

- Status: OPEN
- Discovered: 2026-09-30 in the independent PHASE-71 full review.
- Repository: Cozy; path: src/test/scala/cozy/video/CozyVideoStoryboardBuildModeSpec.scala:24.
- Category/priority: executable-spec organization, P3.
- Evidence: the 1,576-line multi-facet behavior spec has one flat should group and no which subsections, although it covers mode selection, final/confirmation independence, currentness, safety, per-part artifacts, and failure preservation.
- Outside Phase 71: presentation and navigation only; behavior, coverage, and Phase acceptance remain sound.
- Proposed grouping: add which subsections in a later hygiene task without changing semantics or test coverage.
- Schedule/resume: unscheduled; reopen only in a separately authorized hygiene task.
