# Phase 40.1 Checklist: Article and Summary Slide PDF Registration and Driver Acceptance

This checklist is the authoritative progress ledger for Phase 40.1. It is not
a normative behavior contract.

Phase Status: COMPLETE

Split from Phase 40 on 2026-08-29 under the user-approved
`$cncf-split-phase Phase 40`. This Phase owns the original registration
obligations and `PDF40-05` exactly once; Phase 40 owns generation and
receipt/currentness exactly once. No work was complete before the split.

Predecessor: Phase 40 must close with accepted PDF resource roles, exact
locale/public-path identities, current receipts, and stale-input rejection
semantics before Phase 40.1 starts.

## PDF40.1-01: Normal and WIP PDF Site Registration

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 40.1
- Update rule: Update this block from the checklist state below.

- [x] Extend normal `cozy media register-site` and distinct WIP
      `cozy media register-site-wip` only for the accepted SmartDox Phase 9
      PDF roles supplied by current Phase 40 receipts.
- [x] Preserve existing infographic/video registration, exact-locale behavior,
      atomicity, and unrelated registry entries.
- [x] Reject missing, changed, cross-locale, or role-incompatible PDFs before
      mutation; prove no partial registry or site state on failure.
- [x] Evidence bound to local Step acceptance commit
      `c41e5c7942ad4f8d12bbe8b6114cc5f7d86aefb3` and serialized receipt
      `cozy-p401-sbt-011` / invocation `99893-20260830T021021Z`: eight P401
      suites, 174 tests passed, 0 failures.

## PDF40.1-02: SimpleModeling.org Driver Acceptance and Closure

Stage Status:

- Current status: CLOSED
- Owner: Cozy / SimpleModeling.org acceptance boundary
- Update rule: Update this block from the checklist state below.

- [x] **(Future Development Candidate) `P401-DC-001`** — defer the selected
      clean static driver acceptance and Article 8 operating loop until after
      Phase 42; see
      `docs/journal/2026/08/2026-08-30-phase-40.1-driver-runtime-acceptance-handoff.md`.
      No acceptance is claimed now.
- [x] Record the selected unmodified driver as
      `src/main/media/development-process/knowledge-modeling/media.yaml`;
      existing untracked `literate-modeling` work is excluded.
- [x] Record that this Phase changed no SimpleModeling.org source/configuration,
      article/Notice metadata or links, registry record, generated output, or
      website file.

Phase 40.1 is COMPLETE. Its closure consists of the registration contract,
the explicit deferred candidate, current full Cozy validation, independent
Phase review, and this local Phase release commit. External driver operational
acceptance is not claimed. No publication, deployment, upload, push, or
downstream series backfill is claimed.
