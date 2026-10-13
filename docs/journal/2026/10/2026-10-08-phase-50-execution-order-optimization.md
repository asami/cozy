# Phase 50 execution-order optimization

Date: 2026-10-08
Status: planning update only; implementation unstarted

## Request and observed baseline

The user requested optimization of Cozy Phase 50 execution arrangements.
The existing Phase and checklist are PLANNED with every obligation open.
Phase 43 has accepted Logical UI semantics, structural Review HTML and the
SalesOrder fixture. Phase 74 subsequently accepted shared vocabulary and a
provisional List/Detail runtime contract, including compact/expanded selection.
The original Phase 50 plan predates that reuse opportunity.

Source inspection found two early contract decisions: the existing Review
renderer takes AcceptedLogicalUi rather than a candidate, and Phase 74's
selection intent has compact/expanded but no medium. Phase 43 screen and
semantic projections also remain separately bound values. A target-ready
acceptance handoff must settle the coverage of those values before both
renderers and Phase 51 depend on it. This record does not define that new API.

## Applied planning changes

- Preserved P50-01 through P50-05 and all existing checklist obligations.
- Added stable checklist IDs and four preparation outcomes.
- Arranged four execution batches: minimum contract/IR, first compact
  walkthrough, alternatives/responsive review, feedback/acceptance/closure.
- Placed candidate preview and exact acceptance binding before renderer work.
- Assigned shared vocabulary reuse and an explicit medium-intent decision;
  no duplicated runtime authority or new CNCF/Android dependency is planned.
- Assigned one SalesOrder coverage matrix to both projections and final driver
  evidence, retaining all original failure and responsive obligations.
- Separated focused semantic tests, actual browser behavior evidence and final
  full Cozy validation. Existing per-batch review obligations remain intact.
- Kept the normal checkout as the execution source under the user's continuing
  suspension of local worktree operations pending sm-workflow support.

The current plan is [Phase 50](../../../phase/phase-50.md), with its
[completion ledger](../../../phase/phase-50-checklist.md). The strategy entry
now points to that execution order. No product source, generated artifact,
shared directive, historical acceptance or successor scope was changed.

## Verification

### Same-day refinement: main route before branches

The user requested that Cozy Phase 50 use the same main-route-first discipline
as CNCF Phase 73. The initial E1 assignment of every IR/preparation item could
otherwise delay the first working flow. The revised sequence freezes only
the compact normal-flow contract in E1 and proves candidate -> generated Web
-> actual browser list/select/detail/confirm/success in E2. Input/validation,
loading/failure/recovery, reset/replay, medium/expanded and remaining metadata
and diagnostics follow as E3 increments. Structural HTML stays aligned to each
increment rather than requiring all future sections before the compact flow.

Candidate-preview authority and exact consumed inputs remain mandatory from
the start. The full target-ready acceptance binding is completed before E4
acceptance and the Phase 51 handoff. Broad checklist rows span batches and
remain open until all their outcomes are proved. The coverage matrix grows
with the implementation, and each affected branch rechecks the working main
route. This refines the earlier planning order without removing any outcome.

Planning validation confirmed that all 55 original checklist outcomes remain
verbatim and open after removing the added ID prefixes; four preparation
outcomes give 59 unique IDs mapped to execution batches. Added local links
resolve and diff whitespace is valid. Existing cross-repository links were
not rewritten to fit this machine's checkout layout. No SBT, browser validation,
implementation, acceptance, publication, commit or push is part of this update.
