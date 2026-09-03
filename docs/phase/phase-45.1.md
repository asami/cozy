# Phase 45.1: Document Project Review Projections and User Action Dashboard

Status: COMPLETE

Plan date: 2026-09-02

Split source: Phase 45

Development items: DEV-013, DEV-016

Predecessor: Phase 45 closure. Semantic dependencies: accepted Phases 41, 42,
and 42.1.

## Goal

Implement the deterministic, self-contained, read-only review projections and
the user-oriented Document Project dashboard on top of Phase 45's frozen
authoring and Content Core acceptance contract. This Phase turns the already
accepted state into a reviewable article/video presentation and a safe action
selection surface; it does not redefine semantic authority or produce the
localized deliverables themselves.

## Required Handoff

Phase 45 must be closed before this Phase starts. Its required evidence is the
accepted versioned v2 descriptor contract for Work Product roles, distinct Content
Core candidate/revision/acceptance identities, provenance, and explicit
optional-deliverable/operation selection semantics. This Phase may consume
that contract but must not reinterpret or extend it implicitly.

## P45-03: Article, Video, and Explanation Review Integration

Stage Status:

- Current status: DONE
- Owner: user-facing artifact review projections
- Update rule: complete only when every active review presents the artifact's
  meaning and visible result, not merely its source or internal descriptor.
- Closure evidence: `P451-P45-03-VAL-017` (66/66) and the converged focused
  re-review ledger `CB-P45-03-RR-001` through `CB-P45-03-RR-008`.

- Generate a self-contained `article-review.html` from the v2 Work
  Product role frozen by Phase 45. It must show article structure, narrative
  flow, terminology, media placement, and the relation to accepted Content
  Core.
- Consume the accepted Phase 41 explanation/page-flow review projection where
  applicable; do not substitute a table of Visual Page YAML.
- Generate `video-review.html` from storyboard/scene intent, narration,
  visible pages, infographic use, timing, and rendered-video evidence rather
  than raw source projection.
- Show currentness and stale reasons against the exact accepted source,
  infographic, narration, renderer, and receipt identities.
- Keep review HTML deterministic, self-contained, read-only, and outside
  semantic authority.

## P45-04: User Dashboard and Next-Action Selection

Stage Status:

- Current status: DONE
- Owner: Document Project user interaction and status projection
- Update rule: complete only when representative users can identify the latest
  state and choose a safe next action without interpreting the internal DAG.
- Closure evidence: focused executable specification `P451-P45-04-VAL-004`
  (71/71), converged focused re-review of `CPB-P45-04-001` and
  `CPB-P45-04-002`, and local Step acceptance commit
  `684ba2bb62258772e4340e12d6696a1d2db68ba3`.

- Make the default dashboard answer where the project is, what changed, what
  is blocked, what awaits review, and what the user can do next.
- Present prioritized blockers, pending approvals, current deliverables, and
  recommended or eligible next actions with Japanese and English labels.
- Allow selection of an active optional deliverable and an exact safe next
  operation through explicit commands/contracts. Preserve the generated
  dashboard itself as read-only evidence.
- Retain Workflow, Work Product, provider, gate, attempt, and receipt tables
  as secondary diagnostic views rather than the primary experience.
- Distinguish project authoring/production, workspace integration, aggregate
  build, and external delivery actions, and test user outcomes/action
  eligibility rather than table presence.

## Exclusions

- Redefining the Phase 45 versioned authoring, candidate, or human-acceptance
  contract.
- Japanese/English artifact alignment, shared-media currentness, SimpleModeling.org
  mutation, and Article 8 acceptance; those belong to Phase 45.2.
- Publishing, deployment, upload, push, or external-service mutation.

## Completion Criteria

Phase 45.1 completes only when `article-review.html` and `video-review.html`
are first-class deterministic read-only review outputs using the proper
semantic/visual inputs and exact currentness identities; the Phase 41
projection is consumed where required; the default dashboard gives an
ordinary user status, blockers, pending review, and explicit safe next action;
optional deliverables are selectable through the Phase 45 contract; focused
Executable Specifications pass; and no Current Phase Blocker remains. It
makes no localized-artifact or external-site acceptance claim.

## Closure Evidence

The independent Terra xhigh full review `P451-PHASE-FULL-REVIEW-001` found the
closure-ledger defect `CPB-P45-01-001`; its bounded checklist repair passed the
required focused closure re-review. Final serialized Cozy validation
`P451-FINAL-VAL-001` / `40446-20260903T040445Z` passed 1,707 tests in 126
suites with 0 failures; SBT and wrapper exits were 0 and the shared lock was
released. This local closure does not start Phase 45.2 or claim Article 8,
publication, deployment, upload, push, provider execution, or external
consumer acceptance.

## Structural Phase Plan Gate

State: PROCEED

- planning demand: bounded settled implementation on a frozen authored-state
  and acceptance contract
- parent profile: `gpt-5.6-terra` / `high`
- estimate: 5–7 hours
- cost role: lower-cost implementation/verification after the Phase 45
  contract and acceptance boundary close
- predecessor: Phase 45 closure
- successor: Phase 45.2 only after review/dashboard outputs close

## References

- `docs/phase/phase-45.1-checklist.md`
- `docs/phase/phase-45.md`
- `docs/phase/phase-45.2.md`
- `docs/phase/phase-41.md`
- `docs/journal/2026/09/2026-09-02-phase-45-article-review-requirements.md`
