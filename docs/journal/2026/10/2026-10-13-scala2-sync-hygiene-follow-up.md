# Cozy Scala 2 repository synchronization hygiene — 2026-10-13

Nonblocking independent merge-review findings. Preserve prior accepted Phase records.

<!-- cncf-phase-finding:HYG-COZY-SYNC-UI-SPEC-PROSE-001:begin -->
## HYG-COZY-SYNC-UI-SPEC-PROSE-001

Status: OPEN. Priority: P3. Discovery: 2026-10-13 repository-sync review.

HYG-COZY-SYNC-UI-SPEC-PROSE-001 — OPEN. Discovery date: 2026-10-13. Owner repository: Cozy (cozy). Priority: P3. Location/evidence: src/test/scala/cozy/ui/CozyLogicalUiCompactWebSpec.scala:85-102,305,325; src/test/scala/cozy/ui/CozyLogicalUiReadinessSpec.scala:430; src/test/scala/cozy/ui/CozyLogicalUiReadinessWebSpec.scala:94. Root cause: older foreign-authority, hashed-identity and currentness narrative survived the user-authorized protection removal while actual null/domain/data assertions were retained. Risk: readers may infer protections that the current ordinary IR deliberately no longer promises. Outside-boundary status: nonblocking prose maintenance, separate from CB-COZY-SYNC-COMPACT-WRITER-GWT-001 and CB-COZY-SYNC-READINESS-ACCEPT-GWT-001; it does not reopen a Phase or authorize protection restoration. Proposed task: separately align retained UI spec headings and descriptive clauses with ordinary declarations and explicit decision/reviewer metadata, preserve meaningful behavior assertions, and introduce no digest/currentness substitute. No cleanup is admitted by this review.

<!-- cncf-phase-finding:HYG-COZY-SYNC-UI-SPEC-PROSE-001:end -->
