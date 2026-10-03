# CML Classification Metadata v1 Specification

Current status: S54.2.1 CLOSED at `3cc21f3`; S54.2.2 CLOSED at `1294676`; S54.2.3 CLOSED at `036a553`; Phase 54.2 CLOSED by this distinct local release.
Owner: Cozy Classification supplier.
Update rule: change only under the Phase 54.2 frozen contract; core v1 and
accepted Structure remain unchanged.
Closure basis: CL-01–09, TP-01–08 and FM-01–09 executable behavior, parent focused validation and
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
UnknownNode/UnknownDimension are the S54.2.2 query diagnostics.

## Executable and later-Step evidence

[`CmlClassificationMetadataSpec`](../../src/test/scala/cozy/modeler/CmlClassificationMetadataSpec.scala)
authors CL-01 distinct types/source facts; CL-02 qualified/anonymous identity;
CL-03 exact multiset binding; CL-04 reference target kinds; CL-05 absence and
null-safe admission; CL-06 independent dimensions; CL-07 publication/peers and
version precedence; CL-08 recursive strict shapes/escaping; CL-09 active
non-discarded property permutations and canonical round trips.

## Internal integrated topology

`private[cozy] object CmlClassificationTopology` consumes only an admitted
Classification Graph. `build(graph: CmlClassificationMetadata.Graph)` returns
`Either[Vector[ClassificationDiagnostic], Topology]`. `Topology` is a final
class with constructor `private[CmlClassificationTopology]`, exposing original
`graph`, `nodes: Vector[ElementRecord]` and `links: Vector[TopologyLink]`.
`TopologyLink(element: ElementRecord, kind: LinkKind, source:
Presence[ModelReference], target: Presence[ModelReference])` retains complete
records and authored endpoints. `LinkKind` is sealed, with Generalization,
TraitRealization and PowertypeClassification case objects in the topology object.

Nodes retain catalog order for entity, value, aggregate, trait, powertype and
powertype-kind, including anonymous occurrences. Terms and all other core
kinds do not become nodes. Links retain catalog relation declaration order
across all three kinds: subtype -> supertype, subject -> traitTarget and
subject -> powertype. Members remain payload references, with no membership
edges. Per-carrier order never groups or sorts the published links. Equal
anonymous full records bind to successive carrier occurrences, preserving
multiplicity and distinct payloads without synthetic identities.

| Query on Topology | Result |
| --- | --- |
| node(id: ModelElementId) | Either[ClassificationDiagnostic, ElementRecord] |
| outgoing(id: ModelElementId) | Either[ClassificationDiagnostic, Vector[TopologyLink]] |
| incoming(id: ModelElementId) | Either[ClassificationDiagnostic, Vector[TopologyLink]] |
| dimensions(id: ModelElementId) | Either[ClassificationDiagnostic, Vector[PowertypeClassificationProjection]] |
| dimension(id: ModelElementId) | Either[ClassificationDiagnostic, PowertypeClassificationProjection] |

Indexes use exact full present ModelElementId pairs, without trimming,
case-folding, delimiter parsing or name reconstruction. Node queries address
only supported local catalog nodes; Local endpoints alone populate incoming,
outgoing and subject dimensions indexes. External equal pairs and unresolved
external references remain in links, never local indexes. An opposite Local
endpoint still indexes a partial edge. Absent endpoints retain all four reason
and detail values. Known nodes with no matching local facts return Right(empty).
Cycles, self-links and duplicate declarations stay literal. dimensions follows
catalog link order; dimension addresses each relation's own present identity.
Different targets and different relation IDs for equal subject/target pairs
remain independent. Anonymous nodes and dimensions remain in vectors/links,
without guessed map entries. Core Term identity collisions do not create nodes.

Null graph returns Left(Vector(InvalidShape, path `graph`, detail
`An admitted Classification graph is required.`)). Null identity or component,
empty component, Unicode leading/trailing whitespace (isWhitespace or
isSpaceChar codepoint checks), or any ISO control yields InvalidShape before
lookup. Diagnostic publication is always None. Node, outgoing, incoming and
dimensions share path `node.identity`: malformed detail is
`The Classification query identity is invalid.`; unknown/unaddressable/non-node
identities yield UnknownNode with detail
`The Classification node identity is unknown or unaddressable.`.
dimension uses path `dimension.identity`, the same malformed detail, and
UnknownDimension for unknown/non-dimension identities with detail
`The Classification dimension identity is unknown or unaddressable.`.
No supplied identity or path is echoed.

The topology leaves Graph, core facts, schema and canonical envelope unchanged.
It adds no JSON, admission bypass, transitive closure, runtime policy or view
rendering. The executable
[`CmlClassificationTopologySpec`](../../src/test/scala/cozy/modeler/CmlClassificationTopologySpec.scala)
authors TP-01 node/link facts and mixed order; TP-02 exact node-family queries
and unknowns; TP-03 independent dimension identities; TP-04 external/absence,
partial/cyclic/duplicate facts and Local isolation; TP-05 anonymous occurrence
binding; TP-06 empty, deterministic and malformed behavior; TP-07 unchanged
strict-read round trip; TP-08 active ScalaCheck with at least 100 successful
cases and zero discards over qualified identities, presence/boundaries and
independent carrier/catalog permutations.

S54.2.2 supplies this internal topology: 93 focused tests across five suites passed
and independent lightweight Step review is PASS. Parent manual Step commit is verified at `1294676e5adc868670a8f72f5aa4338f60d4a33b`; factual M0 document proof is retained. S54.2.3 authoring follows the frozen contract below.

## S54.2.3 frozen fixture contract

The [declared](../../src/test/resources/cozy/modeler/classification-metadata-v1-declared.json)
and [absence](../../src/test/resources/cozy/modeler/classification-metadata-v1-absence.json)
fixtures each retain the accepted Structure declared resource's six-record prefix,
original Term, Structure namespace and opaque peer unchanged. They add sixteen
Classification core records (two traits, two powertypes, four powertype-kind nodes,
three generalizations, two trait realizations and three dimensions), one distinct
Term and the Classification child. Total: 22 core records, two Terms; projection
counts: 2/2/3/2/3. All new names are `共有`. Source authority is
`classification.example`, path `src/main/cml/classification-handoff.cml`, digest
64 lowercase `a` characters, and explicit lines 7–22. This is caller-admitted
synthetic source attribution, not verification of a real CML file or currentness.

Declared order entity has generalizations to local order aggregate and unresolved
External(external.example, entity:base), realizes both traits, and has three
independent dimensions: status, category, status-secondary. Status dimensions
share subject/target but retain distinct relation IDs. The audited trait
generalizes to labeled. Powertype members retain explicit ordered local kind
references. Core semantic references remain explicit NotDeclared facts without
fabricated BoK identities. The additional opaque peer is
`org.example.classification-handoff`, with producer `synthetic-caller`, ordered
dimensions `[status,category,status-secondary]` and `absence: null`.

In absence mode only the first trait's own identity changes to
Absent(NotRepresented, `No stable trait identity is represented.`). All endpoints
and members are explicit absence. Carrier ordinal cycles NotDeclared,
NotRepresented, Unsupported, NotApplicable; opposite endpoints use ordinal+1.
Details respectively are `No Classification reference is declared.`,
`The Classification reference is not represented.`,
`The Classification reference is unsupported.`,
`The Classification reference is not applicable.`. The anonymous trait remains
visible among eleven nodes, but its matching Term never provides a model lookup.
All eight links remain literal; known nodes have empty Local indexes and all
three dimension own IDs remain addressable.

[`CmlClassificationMetadataFixtureSpec`](../../src/test/scala/cozy/modeler/CmlClassificationMetadataFixtureSpec.scala)
authors FM-01/03 full literal catalog/projection and wire oracles, FM-02/04 full
node/link/query oracles, FM-05 unchanged Structure cohabitation, FM-06 recursive
shape/version failures, FM-07 forged binding failures, FM-08 Local semantics
failures and FM-09 100 successful zero-discard property cases over both modes,
independent catalog/carrier permutations and recursive object-key reconstruction.
Every real read/build/render/parse/query/property action belongs under When.
The independent Classification expectations never decode their own fixture or
output; only the separately accepted Structure baseline supplies its prefix oracle.

The [supplier handoff](../design/cml-classification-metadata-handoff.md) records
exact APIs and a runnable six-suite recipe. S54.2.3 passed 102 focused tests across
six suites and independent lightweight Step review; its parent manual Step commit is
`036a55377ea2eee04b2f95989d47c8e92f3a2b50`. The sole full Phase review is retained
as FINDINGS, with both authoring blockers resolved by accepted M0 cycle 1.
Ordinary full validation passed 2730 tests across 189 suites; this distinct parent manual release binds the accepted closure.
