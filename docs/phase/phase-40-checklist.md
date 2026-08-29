# Phase 40 Checklist: Article and Summary Slide PDF Generation and Currentness

This checklist is the authoritative progress ledger for Phase 40. It is not a
normative behavior contract.

Phase Status: IN PROGRESS

Split on 2026-08-29 by the user-approved `$cncf-split-phase Phase 40`.
Phase 40 retains `PDF40-01` through `PDF40-04` exactly once. Phase 40.1 owns
the original registration work and `PDF40-05` exactly once. No item was
complete before the split. P40-01 completed `PDF40-01`; P40-02 completed
`PDF40-02`; `PDF40-03` and `PDF40-04` remain open.

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
- Current status: IN PROGRESS
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
      authority. (`P40-03A`; `P40-03A-TEST-027`: 45 executable specs, 3
      suites, 0 failed; `P40-03A-RE-REVIEW-003`: PASS.)
- [x] Keep any PPTX renderer artifact internal and exclude it from public
      registration and delivery. (`P40-03A-RE-REVIEW-003`: PASS.)
- [x] Verify page count, order, legibility, shared-infographic use, and
      stale-input rejection. (`P40-03A-TEST-027`: 45 executable specs, 3
      suites, 0 failed; `P40-03A-RE-REVIEW-003`: PASS.)
- [ ] Complete the P40-03 Step acceptance commit for the P40-03A direct
      descriptor-driven PDF implementation; its optional PPTX sidecar remains
      internal and neither generation route implies BoK publication or
      SmartDox registration.

## PDF40-04: Package Verification and Currentness

Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 40
- Update rule: Update this block from the checklist state below.

- [ ] Keep PDF currentness inside the media-package receipt and presentation
      review-state boundary; preserve the closed video-oriented
      `cozy.media.cross-review.v1` API unchanged. (`P40-04-CONT-001`)
- [ ] Record both PDFs in manifests, receipts, review state, and cross-artifact
      verification.
- [ ] Verify deterministic receipt identity, exact locale, public-PDF-only
      delivery, and stale-input rejection without site mutation.
- [ ] Freeze the PDF resource/receipt/currentness handoff for Phase 40.1
      normal and WIP registration.

Phase 40 is IN PROGRESS. `PDF40-01` and `PDF40-02` are complete; PDF40-03
and PDF40-04, Phase 40.1 registration, driver acceptance, publication,
deployment, push, and downstream series backfill are not claimed.

[DEV-012](../strategy/cozy-development-strategy.md#9-development-item-status) is
excluded from Phase 40. Its future SmartDox Markdown-image admission and any
standalone Cozy PDF-receipt contract are separate from Phase 40's scoped
media-package receipts, manifests, review state, and currentness checks.
