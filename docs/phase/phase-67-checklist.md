# Phase 67 checklist

Status: active

Current status: all three Steps have clean independent reviews; the local Step commit containing this checklist accepts S67.3. Comprehensive Phase review, final Cozy full validation and release remain unchecked.
Owner: Cozy Phase 67.
Update rule: checked items require the named validation/review/commit evidence; receiver runtime work is recorded in the handoff instead of as Cozy completion debt.

- [x] Producer-only scope, strict declaration semantics and compatibility contract frozen in Phase 67.
- [x] S67.1: Lifecycle/Wait CML and immutable IR; 291 tests passed under P67-S671-VAL-001, independent review P67-S671-REVIEW-001 passed, accepted by local Step commit 10548ef5ad332327bf4f3f2105a2a8ba1bcbc61d.
- [x] S67.2: deterministic lossless v3 JSON/Scala/bootstrap; exact v1/v2 compatibility and both public routes validated by 195 tests in P67-S672-VAL-007, ten generated / typed-consumer Scala sources compiled under Scala 3.3.8 in P67-S672-VAL-009, independent review P67-S672-REVIEW-001 passed, accepted by local Step commit 43af3f1cfe3fd37ae922cfe984b94c8fd6147452.
- [x] S67.3: CNCF Phase 82 admission/runtime obligations and [reproducible producer handoff](phase-67-cncf-handoff.md); independent medium review P67-S673-REVIEW-001 passed with zero blockers and no new Hygiene or Development Candidate, accepted by the local Step commit containing this checklist.
- [ ] Exactly one comprehensive Phase review for P67-PLAN-E1 accepted; all blockers closed and findings journaled.
- [ ] Final Cozy full suite passes; Phase/index/strategy status synchronized; local manual release commit complete.

CNCF receiver admission, scheduling, cancellation, signal delivery and durable runtime work are the recipient's responsibility and are not unchecked Cozy completion items.
