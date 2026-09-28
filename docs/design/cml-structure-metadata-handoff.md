# CML Structure Metadata Supplier Handoff

This is the derivative supplier handoff for the frozen `cozy.cml.structure.v1`
extension. It is grounded in the [semantic foundation handoff](cml-semantic-foundation-handoff.md),
the [Structure design](cml-structure-metadata.md), the [Structure specification](../spec/cml-structure-metadata.md),
the internal [Structure API](../../src/main/scala/cozy/modeler/CmlStructureMetadata.scala),
and the [fixture executable specification](../../src/test/scala/cozy/modeler/CmlStructureMetadataSpec.scala).
The checked-in JSON resources are the [declared fixture](../../src/test/resources/cozy/modeler/structure-metadata-v1-declared.json)
and [explicit-absence fixture](../../src/test/resources/cozy/modeler/structure-metadata-v1-absence.json).

## Authority and compatibility

Cozy supplies neutral Structure metadata for CBD Support. `Catalog` remains the
caller-admitted source of truth, the unchanged foundation owns validation of
identity, provenance, Terms, Presence, and semantic references, and the
Structure facade owns only its typed projections. The external handoff is
versioned JSON; `private[cozy]` Scala helpers are internal implementation
details. A Structure `ModelElementId` identifies a modeled element or relation
declaration. It is not a Textus BoK `RelationId`, a Term identity, a display
name, an array position, or a provenance-derived identifier. Model and Term
identity domains remain distinct even when components or names coincide.

The extension is additive over the unchanged
`cozy.cml.semantic-metadata.v1` envelope. Core records, schema identity,
opaque extension preservation, Presence branches, and canonical writer
behavior remain compatible. Unknown Structure child versions fail closed.

## Supplied fixture matrix

Both resources are synthetic admitted facts with the same six core records,
the same name `共有`, source evidence at
`src/main/cml/structure-handoff.cml`, one Term in the separate
`structure.example` vocabulary, and the opaque
`org.example.structure-handoff` peer. The six ordered core kinds and IDs are:

| Kind | ModelElementId |
| --- | --- |
| Entity | `structure.example/entity:order` |
| Value | `structure.example/value:amount` |
| Aggregate | `structure.example/aggregate:order` |
| Composition | `structure.example/composition:line` |
| Aggregation | `structure.example/aggregation:tag` |
| Association | `structure.example/association:partner` |

The declared fixture supplies aggregate boundaries, local and external
endpoint references, explicit roles and cardinalities, false Boolean values,
an empty lifecycle vector, literal policies, and ordered lifecycle values.
The absence fixture retains the same core kinds and records while the first
Entity identity is `Absent(NotRepresented, "No stable identity is represented.")`.
Its Entity, Value, and Aggregate boundaries are respectively
`NotDeclared`, `NotRepresented`, and `Unsupported`; every one of the sixteen
endpoint/semantics carriers on each relation is absent. For relation index `r`
and carrier index `c`, the reason is `reasons((r + c) % 4)` and the detail is
`Absent[<kind>.<carrier>]: 条件Aa`. No identity or policy is inferred.

## Executable supplier evidence

Slice `MMD-541-04A` authors the following fixture-consumer scenarios in
`CmlStructureMetadataSpec`:

| Scenario | Supplier evidence |
| --- | --- |
| STR-25 | Reads the declared resource as UTF-8 `JsValue` and compares every catalog/projection record with independently authored typed values, including qualified lookup, external-boundary retention, and the opaque peer. |
| STR-26 | Reads the absence resource and compares all sixteen cyclic absence carriers for all three relations, anonymous identity preservation, Term-domain lookup, and unchanged core facts. |
| STR-27 | Builds both independently authored typed fixtures, compares `toJson` with the complete resource, and proves canonical parse/read/re-render stability. |
| STR-28 | Mutates each actual resource through five fixed cases: unsupported child version, unknown child key, embedded core contradiction, wrong relation kind, and missing namespace; each expects a fixed typed diagnostic. |
| STR-29 | Uses non-discarded ScalaCheck permutations of the six core records and the three static and relation projections, recursively reorders JSON object construction, and proves identity, absence, opaque-peer, and canonical stability. |

The resources are evidence of a JSON consumer and supplier boundary. They are
not validation receipts, Step acceptance, or Phase closure claims.

## Consumer recipe

1. Parse the supplied UTF-8 bytes to a `JsValue`.
2. Call `CmlStructureMetadata.read`, which first validates the unchanged base
   envelope and then strictly decodes `cozy.cml.structure.v1`.
3. Resolve present model identities through `Catalog.element`; retain absent
   identities as absent and retain external references without local-resolution
   claims.
4. Use the typed projections and explicit Presence branches as authored.
5. When a canonical wire value is required, use `canonicalJson` and preserve
   authored array order. This is not an RFC 8785 claim.

## Later-child boundary

Phase 54.2 Classification may consume the six Structure IDs, kinds, Presence,
and declared policies. Phase 54.3 dynamic metadata may consume those same
qualified links and explicit gaps. Phase 54.4 cross-view metadata may consume
the fixed IDs, kinds, policies, and presence matrix. These are supplier inputs
only; no successor implementation or authorization is included here.

This handoff does not claim CML parsing, source-currentness, automatic export,
file generation, CLI behavior, runtime enforcement, external consumer
acceptance, publication, deployment, UI registration, or a new schema.
