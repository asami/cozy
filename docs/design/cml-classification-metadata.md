# CML Classification Metadata v1 Design

Current status: S54.2.1 CLOSED at `3cc21f3`; S54.2.2 CLOSED at `1294676`; S54.2.3 validation and independent review PASS; manual Step commit pending.
Owner: Cozy Classification supplier.
Update rule: implement only the parent-frozen schema and admitted facts.
Closure basis: executable CL-01–09, TP-01–08 and FM-01–09, parent validation/review and manual commit.

The [specification](../spec/cml-classification-metadata.md) fixes one internal
typed producer and one versioned JSON receiver over the unchanged foundation.
Cozy supplies metadata; CNCF owns runtime enforcement and Textus CBD Support
owns presentation/navigation. This design adds no cross-repository wiring.

## Responsibility and construction

`CmlClassificationMetadata` owns public internal types, diagnostics, the
constructor-private immutable Graph and the four callable operations. It first
admits the base publication, rejects own-namespace conflicts, validates complete
projections, then injects exactly its owned child through the base builder.
No Graph apply/copy/auxiliary constructor bypass exists.

`CmlClassificationMetadataValidation` owns full-record coverage and declared
payload semantics. Per-kind projection sets form exact multisets of admitted
records, including anonymous duplicate occurrences. Qualified identities use
Catalog lookup; equality includes name, kind, source and every core reference.
An unmatched anonymous record is a forged binding, not a new identity. Each
reference checks exact Unicode-safe components; local resolution checks the
specified node/trait/powertype/member kind, while external resolution stays
unclaimed. Presence validation rejects null before payload access.

`CmlClassificationMetadataCodec` owns exact child JSON and strict recursive
decoding. It computes one immutable record/JSON correspondence per operation,
rather than serializing the whole catalog for each projection. Reading first
uses the accepted base reader, checks child version before child field sets,
matches embedded canonical core facts, then invokes the same facade factory
with only the owned namespace removed. Fixed paths and generic diagnostic
details contain no input-controlled names or values.

## Literal facts and compatibility

Trait and powertype declarations and three relation types remain separate
carriers. A classification relation's own model identity identifies its
dimension; neither subject/target coincidence nor name equality merges it.
Core semantic references retain independent authority/source facts. Multiple
generalizations, cycles, self-links, duplicate members and absent endpoints
require no inferred policy. Anonymous facts remain in ordered vectors and do
not enter qualified indexes. Term, Relation and Profile domains stay distinct.

All core publication and strict-reader modules remain unchanged, as do the
accepted Structure extension and fixtures. Opaque peers survive recursive
canonical key sorting; all authored arrays survive in their own order. JSON
escaping uses Play JSON and no raw string construction. Canonical rendering
is deterministic for the admitted graph and is not a signature protocol.

## Immutable topology consumer

`CmlClassificationTopology` owns sealed LinkKind, full-record TopologyLink,
constructor-private Topology and build. Topology exposes the original Graph,
literal node/link vectors and exact node/outgoing/incoming/dimensions/dimension
queries specified in the [contract](../spec/cml-classification-metadata.md).
It requires no re-admission or alteration of accepted producer/codec modules.

Construction makes three literal carrier vectors, with subtype -> supertype,
subject -> traitTarget and subject -> powertype directions. Grouping by the
complete ElementRecord retains occurrence order within each carrier group.
A single catalog traversal consumes the next matching occurrence at every
relation-kind record. Builder-local counters have no external state. The final
vectors and exact-identity indexes are immutable. This preserves mixed catalog
order and equal anonymous record multiplicity without sorting or deduplication.

Six supported node kinds retain catalog order including anonymous nodes.
Only present full model identities enter node indexes. Incoming/outgoing facts
index only Present(ModelReference(id, Local)); external equal pairs remain
literal endpoints in links. A present opposite Local endpoint indexes partial
edges despite external or absent other endpoints. Subject dimensions follow
ordered powertype links and retain full original projections. The dimension
index uses each declaration's own present model identity, preserving independent
dimensions even for equal subject/target pairs. Anonymous dimensions stay in
links and subject vectors without fabricated keys. Member references create no
edges, Terms no nodes, and cycles/duplicates no policy.

Query admission uses foundation-equivalent codepoint checks before map access,
with fixed InvalidShape/UnknownNode/UnknownDimension diagnostics, paths and
details and publication None. Known nodes return empty vectors where no local
facts exist. No names, delimiter parsing, case-folding or identity normalization
enters lookup. TP-01–08 specify literal full records/endpoints, independent
expected vectors, malformed rejection, deterministic rebuild and strict-read
round trip, plus active non-discarded 100-case properties.

S54.2.2 validation passed all 93 focused tests across five suites and its
independent lightweight Step review is PASS; parent manual Step commit is verified at `1294676e5adc868670a8f72f5aa4338f60d4a33b` with factual M0 document proof. Its consumer adds no schema,
topology JSON, transitive closure, producer/CLI or external wiring.

## Later consumer evidence

 S54.2.3 owns deterministic declared and absence
resources plus an actionable Phase 54.3/54.4 handoff. Their authoring follows the settled contract below; focused validation and independent review passed, and the manual Step commit remains pending.

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
six suites and independent lightweight Step review; manual Step commit remains
pending. Phase full review, ordinary full validation and distinct release remain pending.
