# CML Classification Metadata v1 Design

Current status: S54.2.1 CLOSED at `3cc21f3`; S54.2.2 validation and independent review PASS; manual Step commit pending.
Owner: Cozy Classification supplier.
Update rule: implement only the parent-frozen schema and admitted facts.
Closure basis: executable CL-01–09 and TP-01–08, parent validation/review and manual commit.

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
independent lightweight Step review is PASS; manual Step commit remains pending. Its consumer adds no schema,
topology JSON, transitive closure, producer/CLI or external wiring.

## Later consumer evidence

 S54.2.3 owns deterministic declared and absence
resources plus an actionable Phase 54.3/54.4 handoff. S54.2.3 remains pending;
this design records the settled dependency contract without claiming their
implementation, validation, acceptance or external integration.
