# Phase 38 Hygiene Follow-up

This journal records the accepted Phase 38 nonblocking Hygiene records
`HYG-P38-001` through `HYG-P38-005` from the BOK38-01A lightweight review, the
BOK38-01 combined Step review, and the BOK38-04 independent Step review. It is
non-normative and does not change the paired Phase 38 design/specification
contract.

## HYG-P38-001: Phase status snapshot synchronization

Status: RESOLVED

The BOK38-01A lightweight review discovered status drift: the active Phase 38
record and checklist had entered progress while the Phase index and development
strategy still described Phase 38 as planned and not started. The affected
repository-relative paths were `docs/phase/phase-38.md`,
`docs/phase/phase-38-checklist.md`, `docs/phase/README.md`, and
`docs/strategy/cozy-development-strategy.md`.

This was nonblocking and outside the frozen BOK38-01A design/specification
content because it concerned work-ledger and strategy status snapshots only;
the normative paired documents
`docs/design/bok-generated-knowledge-boundary.md` and
`docs/spec/bok-generated-knowledge-boundary.md` were already the fixed
source-boundary contract. BOK38-01B synchronized the then-current Phase
record, checklist, Phase index, and strategy snapshots, recording BOK38-02
through BOK38-07 as not started at that time. That outcome was historical; it
did not make a perpetual claim that BOK38-02 remains not started. No
development candidate record is admitted by this journal.

## HYG-P38-002: Phase status snapshot correction reference

Status: OPEN

Discovery: BOK38-01 combined Step review.

Path/evidence: `docs/phase/README.md` still reports a Phase 34 correction as
awaiting validation despite Phase 34's canonical closure.

This is nonblocking status-snapshot hygiene outside Phase 38 source-boundary
behavior. The proposed later boundary is a documentation-status hygiene pass;
do not fix this record in BOK38-02.

## HYG-P38-003: Generated-boundary reference discoverability

Status: OPEN

Discovery: BOK38-01 combined Step review.

Evidence: Phase and strategy references do not enumerate the new paired
generated-boundary design/specification documents.

This is discoverability-only, nonblocking hygiene outside BOK38-02 behavior.
The proposed later boundary is a documentation-reference hygiene pass; do not
fix this record in BOK38-02.

## HYG-P38-004: Unreferenced history-index helper

Status: OPEN

Discovery: BOK38-04 independent Step review.

Path/evidence: `CozyBokSiteDocument.scala` `_history_index()` has no repository
references.

This is low-risk dead-code cleanup and nonblocking hygiene. It is outside
BOK38-04 because the output now comes from SitePages, and deletion is not
needed for the implemented contract. Handle it in a later bounded cleanup.

## HYG-P38-005: Unreferenced manual-dashboard helper

Status: OPEN

Discovery: BOK38-04 independent Step review.

Path/evidence: `CozyBokGlossaryPages.scala` `_manual_dashboard_body()` has no
repository references.

This is low-risk dead-code cleanup and nonblocking hygiene. It is outside
BOK38-04 because deletion is not needed for the implemented contract. Handle
it in a later bounded cleanup.
