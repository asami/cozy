# CML Structure Metadata v1 Specification

`cozy.cml.structure.v1` is an additive, namespaced extension of the unchanged
`cozy.cml.semantic-metadata.v1` envelope. Cozy is its neutral typed metadata
supplier: callers admit a `CmlSemanticFoundation.Catalog` and authored
projections, while the Structure reader consumes an actual JSON `JsValue`.
Neither path parses CML, writes a file, infers source currentness, nor claims
runtime or downstream-consumer enforcement.

## Envelope

The unchanged foundation root remains exactly `schemaVersion`, `elements`,
`terms`, and `extensions`. The `extensions` member contains a required
`cozy.cml.structure` object with exactly:

| Field | Value |
| --- | --- |
| `schemaVersion` | `cozy.cml.structure.v1` |
| `elements` | ordered element projections |
| `relations` | ordered relation projections |

All Structure-owned objects have exact field sets; unknown, absent, null, or
mixed branch fields fail closed. Other valid namespaced extension objects stay
opaque and are preserved by the foundation renderer. A missing Structure
namespace is an error, while an explicitly present empty extension over an
empty admitted Catalog is valid.

## Typed projections

`ElementProjection` is `element`, `kind`, and `aggregateBoundary`; `kind` is
one of `entity`, `value`, or `aggregate`. `RelationProjection` is `element`,
`kind`, `sourceEndpoint`, `targetEndpoint`, and `semantics`; `kind` is one of
`composition`, `aggregation`, or `association`. The six tags remain distinct
and every projection embeds the full existing v1 `ElementRecord` JSON.

An embedded record must exactly equal an admitted core record. A present
qualified identity resolves through `Catalog.element` and must agree on every
fact; identity-absent records retain their exact `Presence` and are checked by
full-record multiplicity only. Each supported core kind has exactly one
matching projection. Reordering either core or projection arrays changes no
qualified identity, and Terms remain a different identity domain.

## Declared fields and absence

`Endpoint` contains `target`, `role`, `cardinality`, and `navigable`.
`RelationSemantics` contains `ownership`, `independentExistence`,
`createPolicy`, `deletePolicy`, `reassignment`, `reparenting`,
`lifecyclePropagation`, and `aggregateBoundary`. `AggregateBoundary` contains
`aggregate` and `membership`; a model reference contains `identity` and
`boundary`; cardinality has `lower` and `upper`.

All semantic fields use the existing exact Presence branches. No policy,
role, cardinality, navigability, lifecycle, ownership, or aggregate membership
is inferred. `Present(false)` and a present empty lifecycle vector are not
absence. The four foundation absence reasons and their detail text are retained
at every nesting level. Present policy, role, membership, and lifecycle strings
are nonempty, unpadded, and control-free, but their Unicode and case are not
normalized.

`upper` is an integer or explicit JSON `null`: null means unbounded and a
missing field is invalid. `lower` is nonnegative and finite `upper >= lower`.
Local endpoint references resolve only to admitted Entity, Value, or Aggregate
records. Local aggregate-boundary references resolve only to admitted
Aggregate records. External references retain their qualified IDs and boundary
without a local-definition assertion.

## Publication and consumption

The builder first calls the unchanged foundation metadata builder for Catalog
and unrelated extensions, wraps its failures as `PublicationFailure`, and
rejects a caller-supplied Structure namespace as `ExtensionConflict`. It then
validates bindings and declared Structure fields before injecting its extension
through the base facade. `toJson` and `canonicalJson` delegate to that
validated foundation envelope and canonical writer.

The reader calls the foundation reader first, strictly decodes only the
Structure namespace, removes that key, and invokes the same builder. It never
implements a permissive core decoder, name/order fallback, BoK `RelationId`
substitution, or source/provenance-derived identity. Failures are closed typed
diagnostics: `InvalidShape`, `UnsupportedSchemaVersion`, `InvalidCoreBinding`,
`DuplicateProjection`, `MissingProjection`, `InvalidRelationSemantics`,
`PublicationFailure`, or `ExtensionConflict`. Diagnostic paths are fixed
logical coordinates and details never echo hostile payloads or unknown keys.

The executable contract is
[`CmlStructureMetadataSpec`](../../src/test/scala/cozy/modeler/CmlStructureMetadataSpec.scala).

## Executable acceptance map for relation semantics

Slice `MMD-541-02A` records the following authored acceptance scenarios over
the unchanged `cozy.cml.structure.v1` contract. They are executable
specification scope, not validation or closure evidence.

| Scenario | Acceptance focus |
| --- | --- |
| STR-11 | Preserve `Present(false)`, present empty lifecycle, and all four absence reasons/details through nested carriers. |
| STR-14 | Preserve directed source/target endpoint identity, role, cardinality, navigability, relation kind, and external boundary independently. |
| STR-15 | Preserve literal ownership, create/delete policies, roles, and aggregate memberships without trimming, case folding, or ontology defaults. |
| STR-16 | Preserve all 32 independent assignments of endpoint and relation Boolean carriers across Composition, Aggregation, and Association. |
| STR-17 | Preserve finite and explicitly unbounded cardinalities, reject invalid ordering, and reject out-of-range or fractional JSON numbers. |
| STR-18 | Preserve lifecycle vector emptiness, order, duplicates, Unicode/case, and member-level failure categories. |
| STR-19 | Preserve every present outer aggregate-boundary variant and its nested aggregate/membership presence without catalog mutation. |
| STR-20 | Property-test mixed endpoint, policy, reference, cardinality, lifecycle, and nested-presence payloads through canonical JSON and strict consumption. |

The authored scenarios do not claim passed validation, Step acceptance, or
Phase closure; those dispositions remain with the parent workflow.
