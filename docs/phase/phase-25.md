# Phase 25: Entity Revision Generator Alignment

Status: closed

Start date: 2026-07-26
Close date: 2026-08-03

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
- prove that no generated source retains `EntityMutationExpectation` or
  `snapshot.token`; and
- transfer the CBD Support driver-CAR execution acceptance to its owning
  Phase 8 `P8-61` when launcher runtime selection prevents that acceptance
  from exercising the declared Cozy development SNAPSHOT.

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

## Stage 25.2: Downstream Regeneration Acceptance Transfer

Stage Status:

- Current status: CLOSED
- Owner: CBD Support
- Update rule: mark work complete only from the Phase 25 checklist.
- Checklist basis: `ER25-02`

Focus:

- record the exact locally installed Cozy source coordinate and the effective
  launcher runtime used by a clean CBD Support acceptance attempt;
- retain the CNCF descriptor mismatch rather than weakening its version check;
- transfer the regeneration, compile, and focused SQLite-backed persistence
  acceptance to CBD Support Phase 8 `P8-61`.

Completion evidence:

- `cozy --runtime 0.3.0-SNAPSHOT version` reported `0.3.1-SNAPSHOT` because
  the enabled launcher development runtime selected
  `/Users/asami/src/dev2025/cozy` first;
- the clean CBD Support attempt therefore stopped at
  `CNCF_DESCRIPTOR_TARGET_MISMATCH` (`expected 0.5.1`,
  `actual 0.5.1-SNAPSHOT`) before compile or persistence execution;
- no CBD Support source was changed, and the external acceptance is explicitly
  tracked by CBD Support Phase 8 `P8-61`.

## Stage 25.3: Review and Handoff

Stage Status:

- Current status: CLOSED
- Owner: Cozy / CBD Support
- Update rule: mark work complete only from the Phase 25 checklist.
- Checklist basis: `ER25-03`

Focus:

- review, repair, re-review, and commit the Cozy change;
- record the source coordinate, exact downstream execution evidence, and
  ownership transfer; and
- preserve CBD Support Phase 8 `P8-60` human confirmation while its separate
  `P8-61` acceptance remains on hold.

Completion evidence:

- the read-only review found a missing exact
  `EntityMutationExpectation`-absence assertion; the assertion was added to
  the common generated-source mutation specification and its focused
  regression suite passed;
- the clean re-review found no remaining actionable generator or handoff
  finding;
- this closure does not claim successful downstream regeneration. That result
  belongs to CBD Support `P8-61`.

## Completion Criteria

Phase 25 closes when executable generator specifications prove that standard
SimpleEntity CRUD neither accepts nor constructs an application revision, yet
retains framework-managed revision lifecycle behavior without retired token
symbols. The source coordinate is development-only `0.3.0-SNAPSHOT`. CBD
Support owns the separately pending runtime-selection, regenerated-CAR, and
SQLite persistence acceptance in Phase 8 `P8-61`; it is not implied by this
closure.

## References

- `docs/phase/phase-25-checklist.md`
- `docs/journal/2026/07/entity-revision-generator-alignment-handoff-2026-07-26.md`
- `docs/journal/2026/08/entity-revision-generator-downstream-acceptance-transfer-2026-08-03.md`
- `docs/phase/phase-23.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-50.md`
