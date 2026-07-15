# Notification Operation Value Migration

## Context

Phase 16 uses `textus-user-notification` as the first downstream CAR migration
from legacy top-level `COMMAND` and `QUERY` definitions to canonical top-level
`VALUE` definitions with explicit `input-kind` metadata. The migration must not
change the authored service operation contract.

The initial comparison exposed a packaging gap rather than a CML normalization
regression. Generated component source and model metadata contained the full
operation surface, but the default CAR `abi-manifest.json` contained no
operations. An empty manifest could not provide compatibility evidence.

## ABI Packaging Decision

Generated `cozy.cml.model-metadata.v1` metadata is now the source for a default
CAR ABI surface when no explicit ABI manifest is supplied. The precedence is:

1. explicit `--abi-manifest`;
2. current `src/main/car/abi-manifest.json`;
3. generated CML model metadata;
4. the existing non-CML fallback.

The generated ABI includes the packaging component identifier, authored CML
service operation name/kind/input/output signatures, and entity identities.
Framework-generated entity update operations are runtime operations, not
authored CML service exports, and are not added to this service ABI surface.

`sbt-cozy` installs generated `model-metadata.json` under `target/cozy`, passes
it to Cozy packaging, and asks the packager to write the selected final ABI to
`target/cozy/abi-manifest.json`. The CAR entry and sidecar therefore come from
one selected manifest rather than separate generation paths.

Incremental generation reuses generated Scala only when the complete model
metadata side output is also present. A CML CAR without generated metadata and
without an explicit or source-managed ABI now fails packaging instead of
silently publishing a skeletal ABI. The same metadata option is accepted by
the strict `publish-car` boundary when publication builds the CAR internally.

## Driver Migration

The 13 notification input definitions now use canonical `VALUE` sections:

- six commands declare `input-kind :: COMMAND`;
- seven queries declare `input-kind :: QUERY`;
- result Values remain ordinary output Values.

The migration did not change service operation names, kinds, input types, or
output types. It intentionally adds top-level generated Scala Value classes for
the canonical input Values; no generated JVM class was removed.

## Comparison Evidence

The pre-migration generated artifacts were retained before editing and compared
with a clean post-migration generation:

- generated `UserNotificationComponent.scala` was byte-for-byte identical;
- the 13 model-metadata operation signatures were identical;
- a rich ABI reconstructed from the pre-migration metadata was identical to
  the current CAR ABI export surface;
- the current CAR contains 13 authored operations and four entity identities;
- the current `target/cozy/abi-manifest.json` is identical to the manifest in
  the CAR.

`OperationContractSpec` now executes against the generated component and fixes
all 13 authored signatures plus their `COMMAND_VALUE` or `QUERY_VALUE` input
roles. This keeps the regression boundary in executable code rather than in a
generated-source substring assertion.

The driver verification also fixed the preference ownership boundary exposed
by the migration. Ordinary users can create and search only their own
preferences, manager contexts can administer another subject, and repeated
writes for one subject/type/channel identity use a deterministic EntityId and
the generated create shape through CNCF's internal `entity_upsert` DSL. The
upsert preserves managed security and lifecycle fields, applies create defaults
only for a new row, and updates only domain fields for an existing row.

CNCF now persists `DataStore.save` through one dialect-level atomic upsert
statement for SQLite and MySQL. A 16-writer executable specification fixes the
driver requirement: every concurrent preference write succeeds, every response
returns the same EntityId, and one persistent row remains.

Create/update authorization is selected from the existing row while holding
the same process-local EntityStore identity lock used by the write. This closes
the in-process authorization race where two writers could both observe a
missing row. Publication of the saved row into EntitySpace memory also
completes inside that lock, preventing the earlier writer from publishing a
stale cache value after a later datastore write. Cross-process coordination
remains the responsibility of the
database transaction and deployment policy; this slice does not claim a
distributed authorization lock.

Notification creation and lifecycle updates now read time from the CNCF
`ExecutionContext` clock. A fixed-clock executable specification verifies the
persisted `createdAt`, `readAt`, and `dismissedAt` values.

## Verification

The completed slice passed:

- Cozy `CozyArchivePackagerSpec`: 30 tests;
- Cozy full suite: 526 tests;
- sbt-cozy full suite: 73 tests;
- CNCF full suite: 1,703 tests;
- notification focused operation contract spec: 1 test;
- notification full suite: 19 tests;
- notification clean `cozyBuildCar` under Scala 3.3.8;
- CNCF CAR lint with no deterministic FAIL finding; strict release lint keeps
  the development-state warnings visible for the SNAPSHOT sbt-cozy plugin,
  unpublished dependency, absent release ABI baseline, and non-standard
  ServiceLoader declaration;
- `git diff --check` for the touched repositories.

CAR lint still reports the expected Phase 16 semantic-scalar warnings for raw
string fields in the newly canonical input Values. Those warnings are the next
driver-migration work and are not hidden by this operation grammar slice.
