# Phase 40 Checklist: Article and Summary Slide PDF Generation and Currentness

This checklist is the authoritative progress ledger for Phase 40. It is not a
normative behavior contract.

Phase Status: COMPLETE

Split on 2026-08-29 by the user-approved `$cncf-split-phase Phase 40`.
Phase 40 retains `PDF40-01` through `PDF40-04` exactly once. Phase 40.1 owns
the original registration work and `PDF40-05` exactly once. No item was
complete before the split. P40-01 through P40-04 have completed their scoped
Steps. `P40-FINAL-CPB-001` source/spec repair passed focused validation and
its independent typed focused re-review; fresh final validation passed. This
distinct local Phase release commit records the completed closure.

## PDF40-01: Media Contract and SmartDox Binding

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 40
- Update rule: Update this block from the checklist state below.

- [x] Define Cozy resource roles and output paths for localized article and
      summary-slide PDFs. (`P40-01`)
- [x] Bind them explicitly to the accepted SmartDox Phase 9 roles. (`P40-01`)
- [x] Promote stable design and specification authority before implementation.
      (`P40-01`; focused receipt `P40-01-TEST-005`: 38 succeeded, 0 failed;
      focused closure re-review: PASS)

## PDF40-02: Localized Article PDF Build

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 40
- Update rule: Update this block from the checklist state below.

- [x] Generate a business-profile article PDF through the accepted locale-aware
      SmartDox boundary. (`P40-02`)
- [x] Bind the article and infographic authorities, locale, renderer/profile,
      and output hash in the receipt. (`P40-02`)
- [x] Reject missing, changed, or cross-locale inputs as stale or invalid.
      (`P40-02`; focused receipt `P40-02-TEST-003`: 42 succeeded, 0 failed;
      focused closure re-review: PASS)

## PDF40-03: Summary Slide PDF Build

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 40
- Update rule: Update this block from the checklist state below.

- [x] Accept `P40-03-DEC-001`: generate the summary-slides PDF directly as
      the standard output; make PPTX an explicitly requested internal-only
      output; freeze the presentation-owned generation, PDF artifact/receipt
      evidence, authority order, shared-infographic proof, and public-PPTX
      exclusion.
- [x] Accept `P40-03-DEC-002`: ordinary BoK publication excludes PPTX; article
      HTML, article PDF, video, summary-slides PDF, and infographic define a
      consumer operating profile; `deck.md` and `storyboard.md` HTML remain
      internal review evidence.
- [x] Accept `P40-03-DEC-003`: generate only artifacts explicitly requested by
      the descriptor; do not imply BoK publication or SmartDox registration
      from an artifact type.
- [x] Generate a summary-slides PDF from an accepted Visual Page or slide-IR
      authority. (`P40-03A`; `P40-03A-TEST-028`: 45 executable specs, 3
      suites, 0 failed; `P40-03A-RE-REVIEW-003`: PASS.)
- [x] Keep any PPTX renderer artifact internal and exclude it from public
      registration and delivery. (`P40-03A-RE-REVIEW-003`: PASS.)
- [x] Verify page count, order, legibility, shared-infographic use, and
      stale-input rejection. (`P40-03A-TEST-028`: 45 executable specs, 3
      suites, 0 failed; `P40-03A-RE-REVIEW-003`: PASS.)
- [x] Complete the P40-03 Step acceptance commit for the P40-03A direct
      descriptor-driven PDF implementation; its optional PPTX sidecar remains
      internal and neither generation route implies BoK publication or
      SmartDox registration. (Commit
      `fa7233d4cbf24dcb33b3abe69f01b975796c84b3`; final bound validation receipt
      `P40-03A-TEST-028`: 45 succeeded, 0 failed, 3 suites; focused closure
      re-review `P40-03A-RE-REVIEW-003`: PASS.)

## PDF40-04: Package Verification and Currentness

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 40
- Update rule: Update this block from the checklist state below.

- [x] Accept `P40-04-DEC-001`: record current article and summary PDF
      resources in a receipt-derived `cozy.media.pdf-review-state.v1` state
      without changing the presentation/video review-state or cross-review
      APIs.

- [x] Keep PDF currentness in receipt-derived
      `cozy.media.pdf-review-state.v1`; preserve the closed presentation/video
      `cozy.media.review-state.v1` and `cozy.media.cross-review.v1` APIs.
      (`P40-04-CONT-001`)
- [x] Record both qualifying PDFs with exact role, locale, public path,
      output hash, and receipt input-set identity; bind a direct summary PDF's
      verified renderer-manifest hash only on that route.
- [x] Reject stale output, locale, public path, receipt, or direct-summary
      renderer evidence; reject article-PDF descriptor-root escape before
      renderer invocation or output replacement. (`P40-04-TEST-007`: 63
      succeeded, 0 failed, 5 suites; `P40-04-RE-REVIEW-001`: PASS.)
- [x] Freeze the receipt/currentness handoff for Phase 40.1 normal and WIP
      registration without implementing site registration. (Step acceptance
      commit `77ab9d2dbf7aa58ca6563c33b1756396643c0566`.)

## Phase Closure Ledger

- [x] Complete the independent Phase review and close the admitted repair
      lineage. The review found `CPB-P40-001`; Phase repair cycle 1 rejects a
      symlinked article-PDF output ancestor before rendering or replacement.
      Focused receipt `p40-cpb001-val-001-20260829t213016z` passed 15
      `CozyMediaPdfSpec` scenarios, and focused closure re-review sealed the
      blocker CLOSED.
- [x] Record the nonblocking `HYG-P40-001` separately in
      `docs/journal/2026/08/2026-08-30-phase-40-hygiene-follow-up.md`; it is
      limited to future `which` chapter grouping and changes no behavior.
- [x] Clear `P40-FINAL-CPB-001` before release closure. The authorized repair
      applies receipt and PDF review-state currentness only to selected
      `article_pdf` and `summary_slides_pdf` document resources; image-only
      publication preparation and `CozyMedia.publish` remain unchanged.
      Focused receipt `89900-20260829T220058Z` passed 70 specifications in
      four suites, including all previously failing publication specifications;
      typed focused re-review bundle
      `fa82c84b46ef13787ce5f89b50f80620916a5268022d807184f0b06e4fdadc99`
      sealed it CLOSED with no new Current Phase Blocker. A fresh final suite
      `18266-20260829T230400Z` then passed all 1,569 specifications in 120
      suites with zero failures; this distinct local release commit records
      the completed closure.

Phase 40 is COMPLETE. `PDF40-01` through `PDF40-04` remain complete, the
final publication-currentness integration blocker is closed, and fresh final
validation passed. Normal/WIP registration, driver acceptance, publication,
deployment, push, and downstream series backfill remain exclusively outside
this Phase.

[DEV-012](../strategy/cozy-development-strategy.md#9-development-item-status) is
excluded from Phase 40. Its future SmartDox Markdown-image admission and any
standalone Cozy PDF-receipt contract are separate from Phase 40's scoped
media-package receipts, manifests, review state, and currentness checks.
