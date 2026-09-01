# Phase 43 Hygiene Follow-up

This journal records three nonblocking Hygiene follow-ups accepted during the
independent full Phase review `P43-FULL-REVIEW-001` on 2026-09-02. The review
passed with zero Current Phase Blockers. All records below are OPEN, belong to
separate follow-up boundaries, and must not be resolved in Phase 43. No
Development Candidate journal is created for this review.

## HYG-LUI43-02-001: Split oversized Logical UI source

- Status: OPEN
- Discovery: independent full Phase review `P43-FULL-REVIEW-001`,
  2026-09-02.
- Repository/path: Cozy; `src/main/scala/cozy/ui/CozyLogicalUi.scala`.
- Evidence: the source remains 1,225 lines, above the repository's 1,000-line
  source-size threshold.
- Owner/boundary: Cozy Logical UI normalization; a separately authorized
  structural split that preserves the frozen projection contract.
- Category/risk: source organization; nonblocking Hygiene.
- Nonblocking rationale: the review found no Current Phase Blocker, and this
  structural work does not change the accepted Logical UI behavior.
- Separate follow-up: perform the physical split in a dedicated Hygiene task,
  with any new source path explicitly authorized there.
- Phase restriction: do not resolve this record in Phase 43.

## HYG-LUI43-RR-001: Deterministic review-spec fixture retention

- Status: OPEN
- Discovery: independent full Phase review `P43-FULL-REVIEW-001`,
  2026-09-02.
- Repository/path: Cozy; review-spec temporary fixtures beneath `target/`.
- Evidence: the review-spec temporary fixtures lack deterministic retention
  and cleanup beneath `target/`.
- Owner/boundary: separate review-spec fixture-maintenance boundary.
- Category/risk: test-fixture lifecycle; nonblocking Hygiene.
- Nonblocking rationale: the review found no Current Phase Blocker, and
  fixture retention/cleanup does not change LUI43-03W or LUI43-04 behavior.
- Separate follow-up: define deterministic fixture retention and cleanup in a
  dedicated review-spec maintenance task.
- Phase restriction: do not resolve this record in Phase 43.

## HYG-LUI43-RR-002: Group Logical UI review specifications

- Status: OPEN
- Discovery: independent full Phase review `P43-FULL-REVIEW-001`,
  2026-09-02.
- Repository/path: Cozy; `src/test/scala/cozy/ui/CozyLogicalUiReviewSpec.scala`.
- Evidence: the review specification needs `which` grouping for easier
  behavior navigation.
- Owner/boundary: separate executable-specification presentation boundary.
- Category/risk: specification organization; nonblocking Hygiene.
- Nonblocking rationale: the review found no Current Phase Blocker, and
  grouping changes navigation without changing scenario meaning or coverage.
- Separate follow-up: add behavior-oriented `which` grouping in a dedicated
  executable-specification maintenance task.
- Phase restriction: do not resolve this record in Phase 43.
