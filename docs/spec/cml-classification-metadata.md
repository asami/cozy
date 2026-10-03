# CML Classification Metadata v1 Specification

Current status: S54.2.1 validation and independent acceptance closed; manual Step commit pending.
Owner: Cozy Classification supplier.
Update rule: change only under the Phase 54.2 frozen contract; core v1 and
accepted Structure remain unchanged.
Closure basis: CL-01–09 executable behavior, parent focused validation and
independent acceptance review, followed by the parent manual Step commit.

`cozy.cml.classification.v1` is an additive extension at
`extensions.cozy.cml.classification` of `cozy.cml.semantic-metadata.v1`.
The internal `private[cozy]` supplier accepts an already admitted foundation
Catalog and explicit typed projections. The receiver consumes a versioned
Play JSON `JsValue`. Catalog facts are the sole source of truth. No parser,
CLI, file writer, currentness check, runtime enforcement, or external consumer
integration is supplied by this boundary.

## Types and binding

Classification owns `ModelReference(identity: ModelElementId, boundary:
ReferenceBoundary)` and the following exact projections:

| Type | Fields | Exact ElementRecord kind |
| --- | --- | --- |
| TraitProjection | element | trait |
| PowertypeProjection | element, members: Presence[Vector[ModelReference]] | powertype |
| GeneralizationProjection | element, subtype: Presence[ModelReference], supertype: Presence[ModelReference] | generalization |
| TraitRealizationProjection | element, subject: Presence[ModelReference], traitTarget: Presence[ModelReference] | trait-realization |
| PowertypeClassificationProjection | element, subject: Presence[ModelReference], powertype: Presence[ModelReference] | powertype-classification |

Every `element` embeds the complete foundation record: identity, kind, name,
source and references. Coverage is full-record multiset equality over all five
supported kinds, including equal anonymous occurrences. Missing, duplicated,
extra or forged records fail. Present identities resolve by the exact
case-sensitive qualified pair through `Catalog.element`; anonymous facts stay
unaddressable. Other kinds and Terms remain unchanged. Model identities never
substitute for `TermId`, `RelationId` or `ProfileId`; names, provenance,
digests and array positions never generate identities.

## References, absence and dimensions

Local generalization endpoints and realization/classification subjects resolve
to entity, value, aggregate, trait, powertype or powertype-kind nodes. Relation
declarations are not nodes. A local traitTarget resolves only to trait, a local
powertype target only to powertype, and a local member only to powertype-kind.
External references preserve both identity components and their boundary
without local resolution or type claims, even if the same pair exists locally.
Identifier components and absence details must be nonempty, unpadded by Unicode
whitespace, and free of ISO controls; valid Unicode and case remain exact.

Every endpoint and members field has a required Presence branch. Present empty
members differ from absence. Absent reasons are exactly NotDeclared,
NotRepresented, Unsupported and NotApplicable with authored details. Null,
omitted, mixed or malformed branches fail. Anonymous relation identities and
absent endpoints remain explicit; they create no inferred edges.

Each powertype-classification record identity is its independent dimension
identity. Preserve different targets for one subject and different relation IDs
for the same subject/target pair. Anonymous dimensions remain explicit records.
Authored arrays, duplicate members, multiple generalizations, cycles and
self-links remain literal. No uniqueness, exclusivity, completeness, inherited
membership, transitive closure or runtime type rule is added. Core semantic
references are independent facts and require no fabricated correspondence to
extension endpoints.

## Exact JSON shape and API

The child object has exactly `schemaVersion`, `traits`, `powertypes`,
`generalizations`, `traitRealizations`, `powertypeClassifications`. Its version
is exactly `cozy.cml.classification.v1`; all five arrays are required. Each
projection has exactly its fields from the table; `element` is exact foundation
ElementRecord JSON. A model reference is exactly
`{"identity":{"modelId":"...","elementId":"..."},"boundary":"local"}`
or the same shape with `external`.

Presence is exactly `{"status":"present","value":<typed payload>}` or
`{"status":"absent","reason":<reason>,"detail":<string>}`, where reason is
`not-declared`, `not-represented`, `unsupported` or `not-applicable`.
All typed containers recursively reject extra, missing, null, mistyped or mixed
fields and wrong enum case. Embedded records must equal admitted core JSON.

`CmlClassificationMetadata.build(catalog, traits, powertypes, generalizations,
traitRealizations, powertypeClassifications, extensions)` returns
`Either[Vector[ClassificationDiagnostic], Graph]`. Only this validated factory
creates the immutable Graph, whose constructor is private to the facade.
`toJson(graph): JsObject`, `canonicalJson(graph): String` and
`read(value: JsValue): Either[Vector[ClassificationDiagnostic], Graph]` complete
the boundary. Null/malformed factory arguments return typed diagnostics;
null Graph rendering is an internal DbC precondition.

The builder uses unchanged base publication admission and rejects a caller's
own namespace as ExtensionConflict. The reader admits the base first, requires
an object at its own namespace, checks child version before other child fields,
then follows the same factory route with only its own key removed. Opaque
peers and all core facts survive. Canonical rendering delegates to the accepted
base writer: recursively sorted object keys, authored array order and proper
JSON escaping; it is project determinism, not RFC8785.

Diagnostics are closed typed InvalidShape, UnsupportedSchemaVersion,
InvalidCoreBinding, DuplicateProjection, MissingProjection,
InvalidClassificationSemantics, PublicationFailure, ExtensionConflict,
UnknownNode and UnknownDimension. PublicationFailure retains the original
typed publication diagnostic. Paths are fixed logical schema coordinates and
details never echo hostile values, unknown keys or filesystem paths.
UnknownNode/UnknownDimension are reserved for S54.2.2 queries.

## Executable and later-Step evidence

[`CmlClassificationMetadataSpec`](../../src/test/scala/cozy/modeler/CmlClassificationMetadataSpec.scala)
authors CL-01 distinct types/source facts; CL-02 qualified/anonymous identity;
CL-03 exact multiset binding; CL-04 reference target kinds; CL-05 absence and
null-safe admission; CL-06 independent dimensions; CL-07 publication/peers and
version precedence; CL-08 recursive strict shapes/escaping; CL-09 active
non-discarded property permutations and canonical round trips.

S54.2.2 plans an integrated immutable topology over these declarations with
exact qualified node/dimension queries and explicit unresolved/absent facts;
it is not implemented in S54.2.1. S54.2.3 owns declared/absence receiver
fixtures and the Phase 54.3/54.4 supplier handoff; those remain pending.
