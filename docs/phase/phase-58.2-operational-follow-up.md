# Phase 58.2 Operational Follow-up

Status: CLOSED
Disposition: normal acceptance; prior operational follow-up history retained
Closure commit: `2a9ad1a961d33286bb53f345aced68543191e8ca`

Decision date: 2026-09-13

Owner: Cozy Document Project confirmation

This dedicated list preserves the relocation history for the explicit
deferrals in [the Phase 58.2 checklist](phase-58.2-checklist.md). The earlier
user decision closed development for operational handoff and refinement in
actual use; the later direct instruction resumed formal normal acceptance in
this existing Phase. DP-01 and DP-03 were accepted in the Phase 58.2 release
commit, not moved to successor phases. This list retains the deferral history;
it is not an active Phase queue and does not authorize a scope change,
responsive redesign, external edit, publication, push, deployment, or
force-release.

## DP-01: Final compatibility evidence

- Origin: P582-05 executable acceptance item.
- State: closed in existing Phase 58.2 normal acceptance.
- Retained evidence: focused invocation `COZY-CONFIRMATION-TAGS-VAL-002`
  passed all 48 tests in six suites, including strict v2 admission, v1 refusal
  to adapt v2, safe escaping/rejection, exact typed traceability, native
  selection markup, generic vocabulary, and repeated driver-render determinism.
- Closure outcome: complete v1 preservation and v2 compatibility acceptance
  were bound to the settled Phase tree; the Phase checklist and release commit
  record the full acceptance gate. Focused success alone was not substituted.
- Constraint: preserve existing receipts; reuse verified evidence where
  applicable rather than inventing results or automatically rerunning tests.

## DP-02: Operational screen evidence

- Origin: P582-05 representative desktop/mobile visual acceptance item.
- State: decided Future Development Candidate for broader long/dense
  redesign; narrow fallback characterization was accepted in Phase 58.2.
- Retained evidence: the earlier bounded Document and Summary HTML checks at a
  1500-by-950 desktop viewport remain historical characterization. They are
  not current representative-width acceptance evidence.
- Closure outcome: the selected sample/Desktop layout and functionality were
  checked on the settled tree, including narrow fallback characterization.
  No responsive redesign was required or authorized, and no mobile or
  full-responsive parity is claimed.
- Future entry condition: a concrete operational request for broader
  long/dense redesign.
- Constraint: preserve required information, selection behavior and typed
  source semantics; do not broaden into a responsive redesign.

## DP-03: Normal release assurance

- Origin: P582-05 final independent full Phase review, full Cozy validation,
  and distinct phase-release commit items.
- State: closed in existing Phase 58.2 normal acceptance.
- Closure outcome: the final review, full Cozy validation, and distinct
  release commit `2a9ad1a` are recorded in the Phase checklist. The retained
  earlier focused receipts remain separate historical evidence.
- Constraint: this operational history is not force-release authority. Do not
  rewrite prior records, treat focused tests as full validation, or infer
  permission to commit, push, publish or deploy from this list.

## DP-04: Refinement from actual use

- Origin: the user's decision to refine the screens in actual operation.
- State: selected bounded standalone implementation completed and accepted
  for operational use; other candidates remain decided Future Development
  Candidates.
- Standalone selection on 2026-09-13: the user requested remaining
  implementation. Explicit diagram-item focus and complete wording for the
  unchanged fixed Catalog (`sequence`, `step`, `next`) are selected for
  implementation and focused verification. Both are now implemented, with
  53/53 focused tests and bounded desktop browser checks recorded in
  [the standalone journal](../journal/2026/09/2026-09-13-confirmation-explicit-focus-and-catalog-coverage.md).
  This operational selection did not itself reopen Phase 58.2 or select the
  deferred assurance boundaries DP-01 through DP-03; normal acceptance
  resumed separately afterward.
- User acceptance on 2026-09-13: `受け入れる`, recorded as acceptance of the
  current working implementation for operational use in the standalone
  journal. That operational acceptance did not itself execute the deferred
  assurance items or authorize a commit; the later normal closure did.
- Remaining candidate topics: ease of source inspection and readability of
  actual complex structures. Changes should
  be selected from observed tasks rather than prototype appearance alone.
- The Article 9 Core additions currently exist in Cozy's `content-v2` driver.
  Migration of an external SimpleModeling.org article/project remains a
  separate explicitly authorized scope, not silently completed by this closure.
- Resume condition: concrete operational feedback with the target article,
  screen and expected behavior, or an explicit external-integration request.
- Constraint: no automatic successor Phase, external edit or new abstraction.

## Preserved handoff

The earlier implementation delta was retained on local checkpoint
`ebabc9f83f2c64471dde2174c546d82fe842ee8c`; that checkpoint was not a
Phase release. Normal acceptance later closed through the distinct commit
`2a9ad1a`. This follow-up remains a historical handoff record, not an active
implementation or release queue.

For chronology and exact evidence locators, see
[the operational closure record](../journal/2026/09/2026-09-13-phase-58.2-operational-closure.md).
