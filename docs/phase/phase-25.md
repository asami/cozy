# Phase 25: Entity Revision Generator Alignment

Status: in progress

Start date: 2026-07-26

Dependency: CNCF Phase 50 Entity Revision contract and Cozy Phase 23 closure

## Goal

Align Cozy's generated `SimpleEntity` CRUD adapter with CNCF's canonical
managed-revision contract, so a regenerated CAR neither refers to retired token
types nor exposes framework-owned revision as an ordinary generated operation
parameter.

This is a focused compatibility-following phase.  It is independent of the
planned Component Skill Distribution work in Phase 24 and is scheduled only
because the current Cozy development runtime must generate a CBD Support P8-45
CAR against the published CNCF development API.

## Scope

In scope:

- let `SimpleEntity.toRecord()` provide its embedded, read-only managed
  revision on generated read responses;
- generate ordinary SimpleEntity saves and updates without a revision request
  parameter or application-side revision reconstruction;
- let the Entity/UnitOfWork boundary load, compare when the declared policy
  requires it, advance, and project the managed revision;
- keep explicit detached revision only for a separately proven non-SimpleEntity
  extension surface, never as a standard generated CRUD fallback;
- update generator executable specifications and generated-source assertions;
- regenerate and compile the CBD Support driver CAR against one locally
  published Cozy development SNAPSHOT and the CNCF Phase 50 SNAPSHOT;
- prove that no generated source retains `EntityMutationExpectation` or
  `snapshot.token`.

Out of scope:

- reintroducing token aliases or compatibility adapters in CNCF;
- changing Entity revision persistence, provider, authorization, or transport
  policy;
- changing CBD Support review semantics beyond consuming generated code;
- Component Skill Distribution work from Phase 24.

## Responsibility Boundary

### CNCF

- owns `SimpleEntity.revision`, Entity mutation APIs, and the selected ordinary
  concurrency policy;
- does not restore token compatibility merely for a generator still on the old
  contract.

### Cozy

- owns the generated SimpleEntity CRUD source shape and its executable
  specifications;
- publishes the corrected development SNAPSHOT needed by downstream CARs.

### CBD Support

- regenerates from its CML model and verifies P8-45 through its Entity,
  Aggregate, View, and DataStore boundaries;
- owns review history/retention semantics, not generated Entity concurrency
  semantics.

## Stage 25.1: Contract and Generation Update

Stage Status:

- Current status: CLOSED
- Owner: Cozy
- Update rule: mark work complete only from the Phase 25 checklist.
- Checklist basis: `ER25-01`

Focus:

- make generated source rely on SimpleEntity's embedded managed revision;
- preserve generated-operation names while removing obsolete revision request
  contracts;
- add exact generated-source Executable Specifications before publication.

## Stage 25.2: Downstream Regeneration Acceptance

Stage Status:

- Current status: IN PROGRESS
- Owner: Cozy / CBD Support
- Update rule: mark work complete only from the Phase 25 checklist.
- Checklist basis: `ER25-02`

Focus:

- publish only a corrected Cozy development SNAPSHOT;
- regenerate and compile CBD Support against the current CNCF development
  SNAPSHOT;
- run the focused SQLite-backed review persistence specifications.

## Stage 25.3: Review and Handoff

Stage Status:

- Current status: PLANNED
- Owner: Cozy / CBD Support
- Update rule: mark work complete only from the Phase 25 checklist.
- Checklist basis: `ER25-03`

Focus:

- review, repair, re-review, and commit the Cozy change;
- record the published coordinate and exact downstream evidence;
- return CBD Support Phase 8 to its own review-fix and human-confirmation
  stages.

## Completion Criteria

Phase 25 closes only when executable generator specifications and a regenerated
CBD Support CAR prove that standard SimpleEntity CRUD neither accepts nor
constructs an application revision, yet retains framework-managed revision
lifecycle behavior without retired token symbols. The corrected Cozy artifact
must use a development-only SNAPSHOT coordinate and downstream validation must
include the focused CBD Support SQLite persistence boundary.

## References

- `docs/phase/phase-25-checklist.md`
- `docs/journal/2026/07/entity-revision-generator-alignment-handoff-2026-07-26.md`
- `docs/phase/phase-23.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-50.md`
