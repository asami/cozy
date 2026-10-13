# Phase 78: DataStore Logical Type Generation and Storage Mapping Metadata

status=planned
planned_at=2026-10-05
depends_on=goldenport-cncf Phase 100 DSM-100-02
consumer=goldenport-cncf Phase 100 DSM-100-03, DSM-100-04, DSM-100-06
checklist=[Phase 78 Checklist](phase-78-checklist.md)

## Goal

Extend the canonical CML -> Cozy semantic model -> generated Scala/schema metadata route so the logical datatypes and storage-mapping contract frozen by CNCF Phase 100 are preserved into the CNCF OR-mapping boundary.

The producer must distinguish String, class/application types, XJson, XSemiStructuredData and arrays by declaration. It must not infer application meaning from payload spelling, runtime values or SQL column type.

Phase 78 replaces the active generated `DataType.Named("record")` workaround with the formal `XSemiStructuredData` route and adds the corresponding `XJson` producer route. It preserves the supported behavior of the old record path rather than merely renaming its marker.

## Cross-project handshake

```text
CNCF Phase 100 DSM-100-02
  freeze logical datatype + storage mapping + OR-mapper producer contract
        |
        v
Cozy Phase 78
  CML declaration
    -> Cozy semantic datatype
    -> generated Scala datatype/schema/storage metadata
        |
        v
CNCF Phase 100 DSM-100-03 / 04 / 06
  OR-mapper binding
    -> consumer transition
    -> provider/restart roundtrip acceptance
```

Phase 78 does not independently define CNCF runtime mapping semantics. CNCF Phase 100 DSM-100-02 is the consumer-contract authority. Conversely, CNCF Phase 100 must not hand-edit generated artifacts or replace the producer with runtime string-name dispatch.

## Scope

1. Inventory Cozy/CML producers of `DataType.Named("record")`, record/semi-structured declarations, JSON-like declarations and generated schema metadata used by the selected CNCF Phase 100 scope.
2. Add/adapt canonical CML datatype spellings for `XJson` and `XSemiStructuredData` according to the contract frozen by DSM-100-02.
3. Preserve the two identities distinctly in the Cozy semantic model; do not collapse them into String, Record, generic Named types or one common JSON marker.
4. Generate the CNCF canonical datatype identities into Scala declarations and schema/entity metadata.
5. Generate the storage-mapping/codec-selection metadata required by the Phase 100 OR mapper, without implementing the OR mapper in Cozy.
6. Preserve array element logical types and nested datatype identity where the Phase 100 contract requires them.
7. Preserve class/application datatype identity so class-specific codecs can be selected by declaration.
8. Remove `DataType.Named("record")` from active producer output in the selected scope after equivalent `XSemiStructuredData` behavior is proven.
9. Do not replace the workaround with `DataType.Named("json")`, string-name dispatch, payload-shape inference or hand-edited generated source.
10. Regenerate representative fixtures and hand them to CNCF Phase 100 for runtime/provider acceptance.

## Representation boundary

`XJson` and `XSemiStructuredData` are logical application datatypes, not storage formats.

- `XJson` denotes the JSON data model and maps to the CNCF canonical JSON value representation.
- `XSemiStructuredData` denotes format-independent semi-structured data and maps to the CNCF canonical Record representation.
- JSON/YAML/HOCON are external representations of `XSemiStructuredData` selected explicitly by the model/codec contract.
- SQL TEXT is a physical storage mapping and must not redefine the application datatype.
- JSON-looking String remains String when the declaration is String.

Cozy generates declarations and metadata. Runtime JSON parsing, Record/Json reconstruction, provider reads/writes and malformed-payload handling belong to CNCF Phase 100.

## Implementation direction

Reuse the existing Cozy datatype/modeler/generator and Entity/schema metadata paths. Do not introduce a parallel DataStore-specific type system or standalone generator.

Implementation order:

1. consume the frozen DSM-100-02 producer fixture/contract;
2. inventory current `Named("record")` behavior and assign each behavior to declaration, normalization, generation or CNCF runtime mapping;
3. implement canonical semantic datatype representation;
4. extend existing Scala/schema metadata generation;
5. regenerate selected fixtures;
6. prove no active selected producer output depends on `Named("record")`;
7. hand generated fixtures to CNCF DSM-100-03;
8. close only after CNCF DSM-100-06 accepts the producer output in the declared roundtrip route.

## Executable specifications

At minimum prove:

- CML String generates String logical metadata even when fixture content is valid JSON text;
- CML XJson generates the canonical XJson identity;
- CML XSemiStructuredData generates the canonical XSemiStructuredData identity;
- XJson and XSemiStructuredData remain distinct through semantic normalization and generated schema metadata;
- declared arrays preserve their array and element datatype identities;
- representative class/application declarations preserve the identity required for class codec selection;
- explicit JSON/YAML/HOCON external-format metadata for XSemiStructuredData is preserved where required by DSM-100-02 and is not inferred from sample payloads;
- repeated generation is deterministic;
- generated source/schema contains no active `DataType.Named("record")` or `Named("json")` substitute in the selected accepted route;
- generated artifacts compile against the CNCF Phase 100 producer-facing ABI;
- the handed-off fixture is accepted by CNCF's String/XJson/XSemiStructuredData restart roundtrip tests.

## Acceptance criteria

Phase 78 completes when:

1. the selected authoritative CML models express the admitted logical datatypes without the record workaround;
2. Cozy semantic normalization preserves their distinct identities;
3. generated Scala/schema/storage metadata carries those identities into the CNCF OR-mapper boundary;
4. no active producer output in the selected scope depends on `DataType.Named("record")` or an equivalent name-only workaround;
5. CNCF Phase 100 DSM-100-06 records acceptance of a Cozy-generated fixture through OR mapper -> physical DataStore -> restart -> OR mapper readback.

## Non-goals

- implementing or replacing CNCF OR mapper/DataStore/EntityStore;
- choosing codecs from runtime Record/Json values;
- parsing stored JSON in Cozy;
- provider-specific SQL behavior;
- database migration or recovery;
- automatic type guessing for existing data;
- changing Record implementation;
- hand-editing generated application artifacts.
