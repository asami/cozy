# Phase 16 Closure

Date: 2026-07-16

## Decision

Phase 16 is closed from the authoritative checklist. CML16-01 through CML16-09
contain no incomplete required item.

The final domain-scalar gap was the User Notification account subject
identifier. It now preserves the exact CNCF security subject identity with the
same 1..512 boundary as the User Account external subject contract. The
generated constructor, Record, datastore, and operation metadata contracts are
executable.

## Current Verification

- Cozy: `sbt --batch test`, 559 tests passed.
- textus-user-notification: `sbt --batch test`, 39 tests passed.
- textus-user-account: `sbt --batch test`, 101 tests passed.
- User Notification CAR lint: no deterministic FAIL.
- User Account CAR lint: no deterministic FAIL.
- Closure document diff validation: passed.

CAR lint warnings concern development SNAPSHOT tooling, release ABI baselines,
packaging state, and pre-existing internal-DSL migration debt. They do not
contradict the Phase 16 Value/Datatype contract and remain outside this phase.

## Relocation

Unimplemented policy is not hidden behind a closed checklist item. It is
enumerated in `docs/notes/cml-post-phase-16-policy-backlog.md` and requires a
new phase or a separately scoped maintenance slice.
