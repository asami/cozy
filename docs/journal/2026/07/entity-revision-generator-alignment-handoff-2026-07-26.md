# Entity Revision Generator Alignment Handoff — 2026-07-26

## Context

CNCF Phase 50 replaced the Entity token and mutation-expectation API with the
canonical `EntityRevision` value.  The current Cozy development runtime still
emits `snapshot.token` and `EntityMutationExpectation.parse(...)` for generated
CRUD operations.  A CAR regenerated against the current CNCF SNAPSHOT therefore
does not compile.

The discrepancy was found while CBD Support Phase 8 added its required
SQLite-backed review-history and retention acceptance specification.  The CBD
Support review implementation now uses `snapshot.revision`, but generated CML
Entity adapter code still refers to the removed API.

## Decision

Define Cozy Phase 25, **Entity Revision Generator Alignment**, as a focused
generator-following phase.  Keep Phase 24 unchanged and planned: component
skill distribution is unrelated to Entity mutation source generation.

The required SimpleEntity generated mapping is:

| Former generated shape | Canonical replacement |
| --- | --- |
| `snapshot.entity.toRecord().upsertSingle("cncfRevision", snapshot.token.print)` | `snapshot.entity.toRecord()`; `SimpleEntity` owns embedded read-only `revision` |
| `cncfRevision` request parameter | no standard CRUD revision parameter |
| `EntityMutationExpectation.parse(action.cncfRevision)` | no application-side revision reconstruction |
| `entity_save(entity, expectation)` | framework-owned ordinary SimpleEntity mutation path |
| `entity_update(id, patch, expectation)` | framework-owned ordinary SimpleEntity mutation path |

`SimpleEntity.revision` is framework-managed, embedded, and read-only. A
generated request must not accept or overwrite it as a business parameter. The
Entity/UnitOfWork boundary owns lifecycle, selected concurrency policy, and
revision advancement. Explicit detached revision remains limited to a proven
non-SimpleEntity extension surface.

## Handoff

Cozy owns the generator template, generated-source Executable Specifications,
review, and development SNAPSHOT publication.  CBD Support then regenerates and
validates its P8-45 persistence path using Entity and DataStore abstractions.
CNCF owns the revision API and must not gain a retired-token compatibility layer
to mask generator skew.

CBD Support Phase 8 remains open until its own P8-43/P8-45 evidence and the
P8-60 human confirmation gate are complete.

## Implementation Result Note

The implemented ordinary CRUD contract is now explicit:

- generated load and load-record operations call `entity_load`;
- generated save and save-record operations call `entity_save_managed` and
  return the authoritative saved Entity;
- generated patch update remains on `entity_update`;
- generated ordinary requests carry no framework revision parameter.

This common path is intentionally not limited to `SimpleEntity`. It preserves
Embedded revision for `SimpleEntity`, explicit Detached revision for an
admitted non-`SimpleEntity`, and ordinary unmanaged persistence for a
non-`SimpleEntity` without a revision binding. Strict snapshot and Detached
carrier APIs remain available only for operations that explicitly observe a
revision.
