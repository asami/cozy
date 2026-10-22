# Ineffective prevention: CAR lint signal boundary

**Date:** 2026-10-22
**Status:** decision / follow-up

## Incident

A CNCF cache-capacity implementation used reservation files and repeated
filesystem scans to reduce the chance of disk-full failures. The mechanism did
not reserve physical filesystem capacity, so disk-full remained possible and
ordinary I/O failure handling plus partial-output cleanup remained mandatory.

Improving the mechanism into a more elaborate reservation abstraction would
not address the primary issue: the prevention machinery itself lacks a concrete
requirement proportional to its cost.

## Decision

Do not add another generation-Skill rule for this pattern. Repeated experience
shows that literal prohibitions can cause equivalent or more elaborate
workarounds and make Skills larger.

There is a stronger reason than Skill size alone. Mentioning a defensive topic
in a generation rule can increase its salience for the model. A rule such as
"do not implement capacity reservation" may cause the model to treat capacity
as an important design concern and invent a different preflight check,
re-check, consistency check, ledger, or synchronization mechanism. The rule can
therefore induce the very family of unnecessary machinery that it was intended
to suppress.

For recurring generation tendencies, avoid teaching the implementation model an
incident catalog. Prefer post-generation observation from an external harness:
let ordinary implementation proceed without making the suspicious topic
artificially salient, then detect structural signals in CAR lint and evaluate
their necessity in CBD Review.

Cozy CAR lint will instead provide cheap deterministic structural signals.
Semantic necessity is delegated to textus-cbd-support Review.

Initial signal family:

- CHECK_THEN_IO
- CUSTOM_RESOURCE_RESERVATION
- EXTERNAL_STATE_RESCAN
- MANAGEMENT_STATE_IN_HOT_PATH
- DUPLICATE_RESOURCE_ACCOUNTING
- PREVENTION_WITH_UNCHANGED_FAILURE_PATH where statically supportable

These are signals, not blanket prohibitions.

See [CAR Lint Signals for Ineffective Prevention](../../notes/car-lint-ineffective-prevention-signals.md).

## Responsibility

- Cozy: deterministic evidence/signal extraction.
- textus-cbd-support: Ineffective Prevention / Pseudo Guarantee semantic review.
- Skills: remain compact; do not accumulate incident-specific prohibitions or
  make defensive topics salient merely because a previous model overengineered
  them.
