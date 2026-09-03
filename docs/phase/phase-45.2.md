# Phase 45.2: Document Project Alignment and Article 8 Local Acceptance

Status: COMPLETE

Plan date: 2026-09-02

Split source: Phase 45

Development items: DEV-013, DEV-016

Predecessor: Phase 45.1 closure. Semantic dependencies: Phase 45, Phase 45.1,
and accepted Phases 40, 40.1, 41, 42, and 42.1.

## Goal

Complete the localized-artifact semantic alignment and shared-media
currentness contract, then accept one selected Article 8 Document Project
locally through the normal SimpleModeling.org source projection. This is the
only split child that may require an explicit addition of
`/Users/asami/src/dev2025/simplemodeling-org` as an update root.

## Required Handoff

Phase 45 and Phase 45.1 must both be closed before this Phase starts. Required
evidence is the accepted v2 authoring/candidate/acceptance contract and
the deterministic read-only review/dashboard outputs with their input and
currentness identities. A separate explicit update-root decision is required
before mutating SimpleModeling.org.

## Decision Resolution Records

### P452-DEC-PROTECTED-CLOSURE-001

- Attributable answer: user instruction on 2026-09-03:
  `P452-DEC-PROTECTED-CLOSURE-001: one-protected-phase-closure を許可する`.
- Selected option: `one-protected-phase-closure`.
- Resolution: P45-05 and P45-06 are one protected Phase 45.2 closure unit;
  the ordinary lightweight Step review is omitted. After both boundaries have
  completed focused validation, one Terra xhigh full Phase review and one
  local closure commit decide acceptance.
- Non-effects: this decision does not authorize a push, publication,
  deployment, upload, or external-service mutation.
- Consumed: `true`.

### P452-DEC-ROOT-001

- Attributable answer: user instruction on 2026-09-03:
  `P452-DEC-ROOT-001: add-simplemodeling-root を許可する`.
- Selected option: `add-simplemodeling-root`.
- Resolution: add `/Users/asami/src/dev2025/simplemodeling-org` as a Phase
  45.2 update root only for the selected Article 8 driver source/configuration
  and the smallest necessary local generated artifacts. No earlier article,
  series backfill, deploy, upload, push, or publication is authorized.
- Replan consequence: the initial Phase-base authority contained Cozy only;
  before any implementation it must be replaced during the next parent PLAN
  with an authority bundle covering both admitted repositories.
- Consumed: `true`.

## P45-05: Localized Artifact Alignment and Shared Media Currentness

Stage Status:

- Current status: DONE
- Owner: localized deliverable and shared-media synchronization
- Update rule: complete only when selected outputs can be traced to accepted
  meaning and shared assets, with exact stale propagation and review evidence.

Implementation evidence: `cozy.content-alignment.v1` provides the closed,
private alignment ledger; its executable specifications cover accepted Japanese
and English localized artifacts, declared shared-infographic use, exact stale
reasons, and non-acceptance/pending-parity outcomes.  The real Article 8
driver intentionally has no accepted Content Core and therefore demonstrates
the complementary pending boundary rather than fabricating an accepted record.

- Bind article HTML/PDF, summary-slide PDF, infographic PNG, and video variants
  to one accepted Content Core revision and their locale-specific authorities.
- Verify that the accepted shared infographic is visibly used by every selected
  article, PDF, slide, and video consumer that declares that use.
- Record per-artifact semantic-alignment decisions, reviewer identity, and
  accepted source/output identities.
- Propagate staleness through Content Core, locale variant, shared infographic,
  source, provider/profile, renderer, and output receipt changes.
- Make Japanese/English divergence and pending parity review visible without
  requiring byte-identical localized content; retain independent
  artifact-local feedback while routing shared corrections to the candidate
  loop.

Closure evidence: focused executable specification `P452-VAL-011` (74/74),
the retained independent full-review baseline `P452-PHASE-FULL-REVIEW-001`,
and focused closure review `Phase 45.2 / narrow closure replan /
focused-rereview-007` (PASS).

## P45-06: SimpleModeling.org Article 8 Driver Acceptance and Closure

Stage Status:

- Current status: DONE
- Owner: Cozy Document Project and SimpleModeling.org local integration
- Update rule: complete only when the Article 8 driver exercises every
  currently admissible local review/dashboard branch, reports unaccepted
  delivery branches as blocked, and the public-source projection is
  demonstrably safe.

- Use `src/main/doxsite/<article>.dox/` as the Article 8 Document Project and
  project only its normal public `index.dox` source through the existing
  SimpleModeling.org Doxsite contract.  This Phase does not register or deliver
  public media.
- Exclude Content Core internals, AI dialogue, attempts, review evidence, raw
  media, receipts, and generated target trees from public article source.
- Exercise accepted bilingual alignment and stale propagation through the Cozy
  executable specifications.  On the real Article 8 package, exercise article
  review, video review, Slide Logical Chart, dashboard, and normal Doxsite
  projection; confirm HTML/PDF, summary-slide PDF, infographic PNG, and video
  delivery branches remain safely blocked pending real Content Core acceptance.
- Prove Article 7 and earlier articles remain unchanged and do not require
  retrofit into Document Projects.
- Run focused and full serialized Cozy validation, then complete independent
  Phase review and required focused re-review. Record local acceptance only.

### Article 8 Draft-Driver Boundary

The selected Article 8 package currently has no accepted Content Core entry.
Phase 45.2 MUST NOT fabricate a human semantic acceptance, a receipt, or a
public delivery merely to make the driver appear complete.  The executable
specification owns accepted localized-alignment cases.  The real Article 8
driver proves the complementary boundary: the v2 package is admitted by Cozy,
its locally generated reviews/dashboard report the actual pending state, and
the normal SimpleModeling.org Doxsite build projects only its `index.dox`.
When the Article 8 manuscript is accepted later, its real acceptance evidence
uses this same contract; it is not a Phase 45.2 prerequisite.

Closure evidence: `P452-DRIVER-006` local Article 8 dashboard generation,
`P452-VAL-011` (74/74), focused closure review `Phase 45.2 / narrow closure
replan / focused-rereview-007` (PASS), and final serialized Cozy validation
`P452-VAL-012` / `50285-20260903T122237Z` (1,710 succeeded, 0 failed, 8
canceled, 126 suites; SBT and wrapper 0; lock released).

## Exclusions

- Redefining the Phase 45 authoring/candidate/acceptance contract or the Phase
  45.1 review/dashboard behavior.
- Retrofitting Article 7 or earlier SimpleModeling.org articles.
- Publishing, deployment, upload, push, external-service mutation, or series
  backfill. A local Article 8 acceptance is not a publication authorization.

## Completion Criteria

Phase 45.2 completes only when executable specifications prove accepted
localized alignment and shared-infographic use without assuming byte-identical
locale text; currentness and stale reasons are exact; one real Article 8 driver
uses a safe public `index.dox` projection without leaking project internals or
changing earlier articles; its absent Content Core remains visibly pending and
all public-delivery branches remain blocked; focused and full serialized Cozy
validation succeeds; independent review closes every Current Phase Blocker;
and all acceptance remains local.

## Closure Evidence

The retained Terra xhigh full review `P452-PHASE-FULL-REVIEW-001` was recovered
against its exact two-root review-diff identity and repaired only through the
admitted convergence ledger. The final focused closure review found no Current
Phase Blocker. `P452-VAL-012` / `50285-20260903T122237Z` completed the final
serialized Cozy suite with 1,710 succeeded, 0 failed, 8 canceled, and 126
suites; SBT and wrapper exits were 0 and the shared lock was released. This
local closure does not publish, deploy, upload, push, register, or otherwise
perform an external action.

The release preflight additionally applies `MCR-P452-HEADER-001` (M0) to the
private `CozyDocumentProjectAlignment` source-history comment so its date uses
the mandatory canonical `%b. %e, %Y` format. The final serialized Cozy suite
`P452-VAL-013` / `32080-20260903T200559Z` reran after this comment-only repair
with 1,710 succeeded, 0 failed, 8 canceled, 126 suites, SBT/wrapper exit 0,
and a released shared lock. `HYG-P452-RR3-001` remains verbatim in the
canonical Hygiene journal as the focused-review-time record.

## Structural Phase Plan Gate

State: PROCEED

- planning demand: protected decision over a public-source projection and a
  cross-repository local acceptance boundary
- parent profile: `gpt-5.6-terra` / `xhigh`
- estimate: 6–8 hours
- expensive reasoning kernel: preserve the complete negative boundary between
  private Document Project evidence and public Article 8 source while proving
  output/currentness alignment
- predecessor: Phase 45.1 closure
- update-root rule: request a separate explicit addition of
  `/Users/asami/src/dev2025/simplemodeling-org` before mutating that repository

## References

- `docs/phase/phase-45.2-checklist.md`
- `docs/phase/phase-45.1.md`
- `docs/phase/phase-45.md`
- `docs/phase/phase-40.md`
- `docs/phase/phase-40.1.md`
- `docs/journal/2026/09/2026-09-02-phase-45-document-project-requirement-reconciliation.md`
