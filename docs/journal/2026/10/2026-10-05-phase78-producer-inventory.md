# Phase 78 producer inventory

Date: 2026-10-05
Status: initial inventory; P78-01 remains OPEN pending DSM-100-02 intake.
Branch: `codex/workbench-cozy-phase78`
Original base: `be780917fffb2b71cf10d452a40baad416cd482b`

## Actual generation chain

`cozy.modeler.Modeler._make_scala` builds a SimpleModel through ModelBuilder
and constructs `org.simplemodeling.SimpleModeler.generator.scala.ScalaGenerator`.
`ModelTypeProjector.scala` projects the CML/Kaleidox entity, value and datatype
models into SimpleModeler semantic types. Cozy's build explicitly selects the
SimpleModeler dependency through `simplemodelerVersion` (default
`1.1.26-SNAPSHOT`).

The canonical generator is implemented in the separate SimpleModeler repository:

| Owner source | Current responsibility |
| --- | --- |
| `model/MDataType.scala` | Semantic datatype representation |
| `SimpleModeler/generator/scala/ComponentPart.scala` | Operation schema datatype expressions; record currently emits DataType.Named("record") |
| `SimpleModeler/generator/scala/Scala3ClassGeneratorBase.scala` | Persistent record attributes; current metadata emits EntityStoreAttribute.scalarString |

All paths in this table are below SimpleModeler's
`src/main/scala/org/simplemodeling/`. The initial inspection was read-only. The user subsequently added dedicated
Core and SimpleModeler checkouts; both are now admitted prerequisite owners.
Their branches are `codex/workbench-core-types` and
`codex/workbench-simplemodeler-types` in Workbench's private checkout area.

`ModelerScalaGenerationSpec.scala` already asserts emitted scalarString
metadata (including displayName storage naming). Extend this real-output
coverage after the DSM-100-02 ABI is fixed; do not implement a second generator
in Cozy or patch generated Scala.

SimpleModeler's `_store_record_projection_metadata` currently filters attributes
with `_is_store_scalar_string_type`, including optional String types. Structured
fields therefore need generated declared mapping metadata as well as distinct
schema type expressions. Replacing only the operation schema's Named marker
would leave the persistence producer incomplete.

## Expected fixture intake

CNCF now has a proposed `docs/spec/datastore-logical-type-mapping.md` (R1–R12)
on its selected Phase 100 branch. It records behavioral requirements and the
fixture matrix. Exact Scala producer signatures and DSM-100-02 acceptance are
still pending; this proposal is not a completed ABI intake.

The generated acceptance model must keep these declarations distinct:

- String originalProposal: a formatted JSON object stored as exact String;
- XJson payload: JSON value, including scalar/array/object/null shapes as
  specified by DSM-100-02;
- XSemiStructuredData evidence: Record with explicitly selected external
  JSON/YAML/HOCON format and a separate physical storage mapping;
- array and class/application fields: declared element or application codec
  identity preserved into the consumer's mapping contract.

For each field, preserve logical datatype, Scala representation, physical name,
storage mapping and any declared codec/format identity. The precise producer
API is not frozen by this inventory. CNCF DSM-100-02 owns that decision.

## Next action and acceptance

Canonical datatypes require goldenport-core support; semantic model and Scala
schema generation require SimpleModeler support. Both need selected dedicated
checkouts before implementation. Cozy then projects the admitted declarations,
generates fixtures with explicitly selected dependency versions, and hands the
actual output to CNCF DSM-100-03/04. Phase 78 closure still requires CNCF
DSM-100-06 provider/restart acceptance and its own validation/review.

No implementation, generation, tests, Step acceptance or Phase closure occurred
as part of this inventory.

## Current coordinated development

The retained Workbench Phase 1 Goal is active. Dedicated Core and SimpleModeler
checkouts joined the existing review context through RepositoryAddition. Core
canonical JSON types and conversion are being validated first; explicit
JSON/YAML/HOCON intake follows before the producer/storage contract is connected.
This update does not close this Phase's inventory, ABI, fixture or acceptance
checklist items.
