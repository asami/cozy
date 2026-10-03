# CML Classification Metadata v1 Design

Current status: S54.2.1 validation and independent acceptance closed; manual Step commit pending.
Owner: Cozy Classification supplier.
Update rule: implement only the parent-frozen schema and admitted facts.
Closure basis: executable CL-01–09, parent validation/review and manual commit.

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

## Planned consumers

S54.2.2 owns topology source/query behavior: one integrated view of admitted
nodes, declared generalization/realization/dimension edges, qualified lookup
and preserved external/absence evidence. It will consume this complete schema
without names or invented IDs. S54.2.3 owns deterministic declared and absence
resources plus an actionable Phase 54.3/54.4 handoff. Both Steps are pending;
this design records the settled dependency contract without claiming their
implementation, validation, acceptance or external integration.
