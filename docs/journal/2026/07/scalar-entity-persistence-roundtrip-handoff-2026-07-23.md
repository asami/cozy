# Scalar Entity Persistence Round-trip Handoff

date=2026-07-23
status=proposed
source=textus-cbd-support Phase 8 P8-42 review-fix
owner=Cozy

## Trigger

Textus CBD Support's `ReviewDiagnosis` Aggregate persists generated single-field
Review data types through the protected CNCF Entity DSL.  The physical Entity
record is updated correctly, but the generated scalar value-reader path does
not reliably restore the later scalar state when the Entity is re-read.

The generated shape demonstrates the contract gap:

- a scalar `DATATYPE`, such as `ReviewRunState`, exposes `toDataStore(): String`;
- its generated `ValueReader` accepts a `Record` only and rejects the scalar
  datastore value;
- generated Entity `fromStoreRecord` depends on that reader.

This is a Cozy generation/persistence contract issue, not a CBD Support
repository, cache, SQLite, JDBC, or raw-datastore issue.  CBD Support must not
work around it with a local cache or a separate persistence implementation,
because doing so would split the Entity Aggregate's source of truth and bypass
the normal UnitOfWork, authorization, CallTree, audit, and View invalidation
boundaries.

## Scheduling Decision

Complete the active Cozy Phase 22 first.  After Phase 22, define a new Cozy
Phase 23, **Scalar Entity Persistence Round-trip**.  Move the currently
defined Component Skill Distribution phase from Phase 23 to Phase 24 without
changing its scope.

The work is a small conceptual correction but a cross-cutting generator
contract change: it affects every generated scalar `DATATYPE` used by Entity
persistence and therefore requires generated-source, Entity lifecycle, and
downstream CAR driver verification.  It merits its own phase rather than being
hidden as a local follow-up or mixed with Component Skill Distribution.

## Proposed Phase 23 Scope

In scope:

- generate scalar-aware `ValueReader` behavior for single-field nominal
  `DATATYPE` values, preserving their constraints;
- make scalar datastore representation and generated Entity
  `fromStoreRecord` round-trip coherently for required and optional fields;
- keep structured `VALUE` records structured; this phase must not flatten
  multi-field Value objects into scalar values;
- cover Entity create, update/upsert, and load with the generated persistence
  contract;
- reject malformed scalar data deterministically;
- prove the behavior with generated fixtures and downstream CAR drivers:
  `textus-user-account`, `textus-user-notification`, and
  `textus-cbd-support`.

Out of scope:

- raw SQLite, JDBC, or datastore access from a CAR;
- a review-local persistence cache or manually maintained View;
- changing CML grammar merely to work around scalar restoration;
- changing the Component Skill Distribution requirements, which move intact to
  Phase 24.

## Acceptance Evidence

Phase 23 closes only when executable tests prove all of the following:

1. a valid scalar datastore value restores its generated nominal type;
2. an invalid scalar is rejected with the generated type's validation rules;
3. optional scalar fields preserve present and absent values;
4. a generated Entity survives create, update/upsert, and fresh load with
   updated scalar values;
5. structured Values retain their Record behavior;
6. the three driver CARs generate, compile, and pass focused lifecycle tests;
7. CBD Support can remove its private `PersistedReviewDiagnosis` persistence
   codec and P8-42 proves `Owner`/`Joined`/`Reused` behavior through the Entity
   Aggregate boundary alone.

## Downstream Handoff Back to CBD Support

After Cozy Phase 23 is published locally for development, CBD Support resumes
P8-42.  It regenerates its CML source, removes the temporary scalar persistence
adapter, and re-runs the completed reuse, terminal-history, and Evolution View
tests.  No CBD Support phase is numbered or created merely to represent this
generator correction.

## Implementation Clarification

The Phase 23 reproduction found that CBD Support's `ReviewRunState` is declared
as a single-field `VALUE`, not a `DATATYPE`. Its public `toRecord()` boundary is
record-shaped, while its existing `toDataStore()` boundary is scalar. The
nominal `DATATYPE` reader already accepted scalar input; the missing symmetry
was in the single-field `VALUE` reader. This clarification preserves the
original handoff as historical evidence while recording the model-kind branch
actually corrected by the implementation.
