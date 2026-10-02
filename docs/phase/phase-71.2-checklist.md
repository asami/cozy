# Phase 71.2 Checklist

Phase status: IN_PROGRESS
Updated: 2026-10-02
Ledger for: [Phase 71.2](phase-71.2.md)
Primary owner: Cozy
Repository-full validation: aggregate-deferred to PHASE-71.4

Implementation started. P710-03-V-S01 removes local renderer-template digests
and is accepted after focused validation (3 suites, 138 tests) and independent
Slice review. P710-03-V-S02 is accepted after focused validation
(5 suites, 188 tests) and independent Slice review. P710-03-V-S03 is accepted
after focused validation (6 suites, 219 tests) and independent Slice review.
P710-03-V-S04 is accepted after focused validation (11 suites, 297 tests) and
independent Slice review 8235032a-399d-4ff9-b456-3cf30d373f22 revision 1,
persisted review ledger revision 5. P710-03-V-S05 is implemented and focused validation passed
(15 suites, 331 tests); independent Step acceptance review remains pending. Its source-metadata
and retained-purpose boundary is recorded in the
[video purpose audit](../journal/2026/10/2026-10-02-phase-71.2-video-digest-boundary-audit.md).
All owned Step and release obligations remain incomplete; F01/F02 actual-driver
evidence, exact Step commit, full Phase review and release remain pending.
`repository_full_suite=deferred-not-run`, aggregate owner PHASE-71.4.
The original P710-03/P710-04 labels are partitioned, not additional work.

## P710-03-V: Remaining video identities and scaffold/consumer migration

Stage Status:

- Current status: IN_PROGRESS
- Current step: P710-03-V-S01 accepted after focused validation (3 suites, 138 tests) and independent Slice review; P710-03-V-S02 accepted after focused validation (5 suites, 188 tests) and independent Slice review; P710-03-V-S03 accepted after focused validation (6 suites, 219 tests) and independent Slice review; P710-03-V-S04 accepted after focused validation (11 suites, 297 tests) and independent Slice review 8235032a-399d-4ff9-b456-3cf30d373f22 revision 1, persisted review ledger revision 5; P710-03-V-S05 implemented and focused validation passed (15 suites, 331 tests); independent Step acceptance review pending.
- Owner: Cozy
- Update rule: close only against the following checklist and accepted executable evidence

- [~] P710-03-V-BEHAVIOR: The corresponding owned scope in Phase 71.2 satisfies its observable behavior and produces the declared frozen handoff; record actual driver/output evidence where required, not a configuration-only claim.
- [~] P710-03-V-SPEC: Affected subsystem specification and Given/When/Then executable specifications, including changed consumers and relevant failure behavior, are reconciled and focused validation succeeds.
- [ ] P710-03-V-ACCEPT: Independent Step acceptance review and the exact Step acceptance commit bind this owned result without claiming successor implementation.

## P710-F01: Same-visual dialogue continuity

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: close only against the following checklist and accepted executable evidence

- [ ] P710-F01-BEHAVIOR: The corresponding owned scope in Phase 71.2 satisfies its observable behavior and produces the declared frozen handoff; record actual driver/output evidence where required, not a configuration-only claim.
- [ ] P710-F01-SPEC: Affected subsystem specification and Given/When/Then executable specifications, including changed consumers and relevant failure behavior, are reconciled and focused validation succeeds.
- [ ] P710-F01-ACCEPT: Independent Step acceptance review and the exact Step acceptance commit bind this owned result without claiming successor implementation.

## P710-F02: Compound-term provider reading and audio regeneration

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: close only against the following checklist and accepted executable evidence

- [ ] P710-F02-BEHAVIOR: The corresponding owned scope in Phase 71.2 satisfies its observable behavior and produces the declared frozen handoff; record actual driver/output evidence where required, not a configuration-only claim.
- [ ] P710-F02-SPEC: Affected subsystem specification and Given/When/Then executable specifications, including changed consumers and relevant failure behavior, are reconciled and focused validation succeeds.
- [ ] P710-F02-ACCEPT: Independent Step acceptance review and the exact Step acceptance commit bind this owned result without claiming successor implementation.

## Closure

Stage Status:

- Current status: OPEN
- Owner: Cozy
- Update rule: close only when all owned Step items and the following release ledger are satisfied

- [ ] RELEASE-71.2-HANDOFF: Every owned Step is accepted; the frozen contract/driver/audit handoff and exact predecessor dependency are recorded, with zero unpersisted accepted review findings.
- [ ] RELEASE-71.2-REVIEW: The mandatory Phase review and any bounded closure repair complete for this exact Phase tree; closure journals and checklist agree.
- [ ] RELEASE-71.2-VALIDATION: Focused/Step validation succeeds; final-only repository-full validation remains explicitly deferred-not-run to PHASE-71.4, without an ordinary full-suite claim.
- [ ] RELEASE-71.2-COMMIT: The distinct Phase release commit includes its final closure ledger and correct aggregate validation disposition; no successor is started.
