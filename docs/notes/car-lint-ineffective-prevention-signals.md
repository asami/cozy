# CAR Lint Signals for Ineffective Prevention

Status: exploratory implementation note
Date: 2026-10-22
Consumer: textus-cbd-support CAR Review

## Purpose

Cozy CAR lint should provide cheap deterministic signals for implementation
structures that may represent ineffective prevention or pseudo-guarantees.
Cozy does not decide the semantic value of the mechanism. That judgment belongs
to CBD Support Review.

The motivating case is CNCF cache capacity management that introduced
reservation files, repeated filesystem scans, and capacity accounting in the
normal I/O path. The mechanism did not reserve physical filesystem capacity and
therefore could not prevent disk-full failures. Normal IOException handling and
partial-output cleanup remained necessary.

The lesson is not "forbid disk-capacity checks". The reusable lint concern is
that an application introduces non-trivial prevention machinery around an
external authority while the underlying failure remains possible.

## Deterministic signal candidates

Initially treat these as attributable signals/findings, normally WARN/INFO, not
as unconditional errors.

- **CHECK_THEN_IO**: filesystem/resource-state check followed by an operation
  whose success is not made atomic by that check.
- **CUSTOM_RESOURCE_RESERVATION**: application-owned reservation/lease/quota
  files or ledgers around a resource whose real allocation authority remains
  external.
- **EXTERNAL_STATE_RESCAN**: repeated directory/metadata/state scans in a normal
  read/write loop to reconstruct management state.
- **MANAGEMENT_STATE_IN_HOT_PATH**: persistent management bookkeeping performed
  per chunk/read/write rather than at a justified lifecycle boundary.
- **DUPLICATE_RESOURCE_ACCOUNTING**: application-maintained counters/ledgers
  that mirror resource state already authoritatively managed by the OS,
  filesystem, database, runtime, or provider.
- **PREVENTION_WITH_UNCHANGED_FAILURE_PATH**: where deterministically knowable,
  prevention machinery is present but the same underlying failure handling is
  still mandatory.

Detection should use structure and data flow where possible. Names such as
"reservation", "lease", "capacity", or "lock" are hints only and must not become
a forbidden-word blacklist.

## Boundary with CBD Support

Cozy reports evidence and locations. It does not conclude that a reservation,
preflight check, retry, cache, lock, or integrity mechanism is unnecessary.

CBD Support asks the semantic question:

> What concrete failure responsibility or guarantee does this mechanism remove?

If the mechanism neither establishes the claimed guarantee nor materially
reduces residual failure responsibility, CBD Support may classify it as
Ineffective Prevention or Pseudo Guarantee.

## False-positive boundaries

Do not reject mechanisms merely because they resemble these signals.
Legitimate cases include an explicit infrastructure contract, actual
authoritative reservation/allocation API, measured performance requirement,
security boundary, transactional primitive, or modeled operational policy.

The existence of such a boundary is evidence for Review, not a reason for Cozy
to silently suppress the structural signal.

## Implementation direction

Keep these checks cheap and deterministic. They belong with CAR lint / Cozy
review evidence, not in generation Skills or an expanding AI prohibition list.
The output should be suitable for CBD Support's provider evidence bundle and
Human-in-the-loop disposition flow.
