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

## 2026-10-18: Frozen contract intake

The October 5 inventory above is retained as history. The current intake consumes
CNCF Phase 100 DSM-100-02 `docs/spec/datastore-logical-type-mapping.md` R1–R12
and its frozen `entity-store-row-mapping.md`, `entity-store-persistence-stages.md`
and `entity-store-native-scalar-mapping.md` contracts. The current producer
contract is [DataStore logical type generation](../../../spec/datastore-logical-type-generation.md).

Cozy owns built-in structured semantic projection through ModelTypeProjector
and MBuiltinStructuredDataType. SimpleModeler owns typed Scala generation,
operation schema emission, declared row bindings and logical Persistent/Create/
Update hooks. Core/CNCF own external decoding, logical-to-physical conversion,
provider mapping and restart/readback. The inventory's Named("record") output
is historical: selected current output uses XJson and XSemiStructuredData;
String, application classes and recursive ordered arrays remain declaration-driven.

R1–R6 retain distinct declared value spaces, explicit codec captures, exact
String content and recursive element bindings. Explicit JSON/YAML/HOCON selection
belongs to the external decoder contract, independently of physical JSON text;
this intake adds no CML storage-format property. R7–R10 retain presence, SQL NULL,
raw Update normalization, exact numbers, one conversion per stage and structured
failure context. SimpleModeler retains generation-time diagnostics for unsupported
optional collections, unordered/unsupported containers and bounded/disjoint
declarations; missing captured codecs must fail generated compilation. R11–R12
require declared consumer coverage and actual generated compilation and runtime
acceptance before cutover or closure.

The selected runtime acceptance fixture is CNCF
`src/test/cozy/phase100-store.cml`, generated by its existing
`Test / sourceGenerators`, compiled against CNCF's generated current runtime
descriptor/version, and consumed by
`org.goldenport.cncf.entity.Phase100GeneratedStoreSpec` for typed EntityStore
SQLite reopen/readback. The parent selects the current Cozy development checkout
explicitly with `-Dcncf.cozyGeneratorRoot=<Cozy checkout>` after
`cozyExportRuntimeClasspath`; replace `<Cozy checkout>` with the selected absolute
checkout path at execution. Cozy's existing helper fixture remains on
0.5.2-SNAPSHOT as a source-output oracle; it is separate from this current-runtime
acceptance route and requires no helper version bump.

P78-PRODUCER integrates P78-01 through P78-04; P78-ACCEPTANCE owns P78-05.
All five canonical checklist sections and their unchecked obligations remain.
Inherited E7 Given/When/Then scenarios are retained without modification. Source
checks and deterministic generation do not establish generated compilation or
CNCF DSM-100-06 restart acceptance. Those gates, focused/full validation and
independent acceptance review remain parent-owned and pending.

HYG-P78-001 remains separate follow-up debt: existing LocalValueDefinition
instance ownership at `src/main/scala/cozy/modeler/ModelTypeProjector.scala:605`.
It is outside this accepted producer route; no incidental source repair is made.
This documentation intake claims no Step or Phase acceptance.

## 2026-10-18: Producer Step acceptance

P78-PRODUCER is accepted after current focused validation and independent
contract-conformance review. P78-01 through P78-04 are DONE in the canonical
checklist. P78-05 and Phase closure remain OPEN; no external Phase is closed
by this record. The Step commit owns the complete integrated producer route.
