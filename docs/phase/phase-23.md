# Phase 23: Scalar Entity Persistence Round-trip

Status: in progress

Start date: 2026-07-23

Dependency: Phase 22 closure and the Phase 16 model-kind/scalar generation
contract

## Goal

Make generated nominal scalar `DATATYPE` values and scalar-projected
single-field `VALUE` objects round-trip coherently through generated Entity
create, update/upsert, and fresh load, including required and optional fields,
while preserving validation and each model kind's public representation.

Cozy coordinates the executable model and generated-code contract.
SimpleModeler owns any required Scala generator correction, simplemodeling-lib
keeps the generic primitive/record conversion boundary unless a library defect
is proven, and downstream CARs verify the published behavior without private
persistence adapters.

## Scope

In scope:

- reproduce the failing generated source and record Cozy's resolved
  SimpleModeler implementation/version;
- use only next-development `SNAPSHOT` coordinates for any corrected local
  Cozy or SimpleModeler publication; published release coordinates remain
  immutable;
- generate scalar-aware `ValueReader` behavior for single-field nominal
  `DATATYPE` values when the current path is incomplete;
- make a single-field `VALUE` reader accept the scalar representation emitted
  by its existing `toDataStore()` while keeping `toRecord()` record-shaped;
- preserve nominal constraints during scalar reconstruction;
- round-trip required and optional scalar Entity properties through create,
  update/upsert, and fresh load;
- reject malformed or constraint-violating scalar input deterministically;
- preserve multi-field `VALUE` and multi-field `DATATYPE` record behavior;
- verify generated fixtures and the `textus-user-account`,
  `textus-user-notification`, and `textus-cbd-support` driver CARs;
- hand the corrected contract back to CBD Support so its temporary
  `PersistedReviewDiagnosis` codec can be removed.

Out of scope:

- raw SQLite, JDBC, or datastore access from a CAR;
- a CAR-local persistence cache, codec, or manually maintained View as the
  permanent solution;
- CML grammar changes made only to bypass scalar restoration;
- flattening structured `VALUE` objects into nominal scalar datatypes;
- changing Component Skill Distribution requirements, which remain intact in
  Phase 24.

## Responsibility Boundary

### Cozy

- owns the CML model-kind mapping, generated fixture, dependency alignment, and
  end-to-end Entity persistence acceptance;
- proves the store boundary with a fresh read rather than a same-process
  returned object.

### SimpleModeler

- owns emitted nominal `ValueReader` and Entity persistence source;
- reconstructs a nominal scalar through its validated constructor;
- keeps structured model-kind generation distinct.

### simplemodeling-lib

- continues to provide primitive scalar readers and `Record` field dispatch;
- changes only if the reproduction proves the defect is below generation.

### Driver CARs

- regenerate against the corrected contract and run focused lifecycle tests;
- do not bypass Entity Aggregate, UnitOfWork, authorization, CallTree, audit,
  or View invalidation.

## Stage 23.1: Reproduction and Dependency Alignment

Stage Status:

- Current status: IN PROGRESS
- Owner: Cozy
- Update rule: mark work complete only from the Phase 23 checklist.
- Checklist basis: `SR23-01`

Focus:

- capture the failing single-field `VALUE` reader, the nominal `DATATYPE`
  control, Entity persistence source, scalar store shape, and fresh-load
  failure;
- determine whether the gap is generator behavior, resolved-version skew,
  model-kind selection, or optional/update store shape.

## Stage 23.2: Scalar Reader Generation Contract

Stage Status:

- Current status: IN PROGRESS
- Owner: SimpleModeler / Cozy
- Update rule: mark work complete only from the Phase 23 checklist.
- Checklist basis: `SR23-02`

Focus:

- accept already-typed, compatible `Record`, and underlying scalar inputs;
- reconstruct through the generated validated constructor;
- keep a single-field `VALUE` record-shaped at its public boundary while
  accepting its scalar datastore projection;
- preserve deterministic rejection and avoid generic multi-field
  structured-record flattening.

## Stage 23.3: Entity Persistence Round-trip

Stage Status:

- Current status: IN PROGRESS
- Owner: Cozy / SimpleModeler
- Update rule: mark work complete only from the Phase 23 checklist.
- Checklist basis: `SR23-03`

Focus:

- prove required and optional scalar fields across create, update/upsert, and
  fresh load;
- keep Entity restoration generic and reader-driven.

## Stage 23.4: Model-kind Regression Matrix

Stage Status:

- Current status: PLANNED
- Owner: Cozy
- Update rule: mark work complete only from the Phase 23 checklist.
- Checklist basis: `SR23-04`

Focus:

- preserve structured `VALUE`, multi-field `DATATYPE`, powertype, and
  statemachine behavior;
- cover valid, absent, malformed, and constraint-violating data.

## Stage 23.5: Driver Verification and CBD Support Handback

Stage Status:

- Current status: PLANNED
- Owner: Cozy / driver CAR owners
- Update rule: mark work complete only from the Phase 23 checklist.
- Checklist basis: `SR23-05`

Focus:

- regenerate and verify User Account, User Notification, and CBD Support;
- remove CBD Support's temporary persistence codec and prove P8-42
  `Owner`/`Joined`/`Reused` behavior through the Entity Aggregate.

## Stage 23.6: Review, Publication, and Closure

Stage Status:

- Current status: PLANNED
- Owner: Cozy
- Update rule: mark work complete only from the Phase 23 checklist.
- Checklist basis: `SR23-06`

Focus:

- perform read-only review, apply findings, re-review cleanly, validate, commit,
  and publish only corrected `SNAPSHOT` development artifacts before downstream
  handback.

## Completion Criteria

Phase 23 closes only when executable tests prove that valid scalar datastore
values restore their generated types, invalid values retain generated
validation, optional presence is preserved, and generated Entities survive
create, update/upsert, and fresh load. Single-field `VALUE` objects must retain
their public Record representation while accepting their scalar datastore
projection; multi-field Values must retain record datastore behavior.
All three driver CARs must generate, compile, and pass focused lifecycle tests,
and CBD Support must prove P8-42 without its private persistence codec.

## References

- `docs/phase/phase-23-checklist.md`
- `docs/notes/scalar-entity-persistence-roundtrip-implementation-proposal.md`
- `docs/journal/2026/07/scalar-entity-persistence-roundtrip-handoff-2026-07-23.md`
- `docs/phase/phase-16.md`
