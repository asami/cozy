# CML Terminology Metadata Design

Date: 2026-10-04
Status: S54.5.1 supplier design validated and independently accepted; manual Step commit pending.

The [supplier specification](../spec/cml-terminology-metadata.md) fixes the API,
wire, diagnostics, and executable acceptance scenarios. This design assigns
responsibility for those facts without changing Foundation or peer metadata.

## Declaration responsibility

Foundation owns admitted qualified core identities, complete immutable records,
semantic references, reasoned availability, and supplied provenance. Terminology
owns the additive supplemental declarations: explicit conceptual group selection,
opaque Term type, independent BoK Mono/Koto, ordered localized labels, and a
separately supplied preferred label. BoK remains the authority for curated Term
meaning and synonym policy. Cozy publishes declarations and gaps.

[The classification alignment note](../notes/bok-glossary-cml-classification-alignment.md)
distinguishes semantic analysis from CML representation. An Entity can refer to
Koto and an Event can refer to Mono. A term-type string is not a Mono/Koto
algorithm. The legacy heuristic adapter is not imported. The separate Model,
Term, relation, and profile identity domains retain their exact qualified pairs;
case, Unicode, delimiters, names, paths, and labels never establish equivalence.

[The integration boundary](simplemodeling-model-integration-boundary.md) assigns
Cozy source metadata publication while retaining BoK meaning and external mapping
authority downstream. Glossary paths, RDF candidates, source digests, and display
names cannot establish a Term identity, synthetic anonymous ID, `sameAs`, synonym
resolution, or source-currentness claim.

## Full correspondence and complete coverage

The private Graph constructor admits only validated declarations. Four cohesive
objects separate the facade/carriers/diagnostics, typed validation, owned child
and primitive codec, and core/projection correspondence. There is no new shared
framework, dependency, or exposure of base-private codecs.

The accepted base facade renders one complete canonical core snapshot per
operation. The projection codec pairs each element and Term JSON object with its
admitted typed record, and each element's full reference JSON with its typed
references. Reader and publisher reuse this correspondence. Full JsValue equality
rejects nested tampering; partial identity/name/source joins and permissive
redecoding are unnecessary. Reference lookup is restricted to the owning element.

Typed validation consumes full-record multisets. Every catalog occurrence is
covered once, even for equal anonymous records of any kind. Projection order is
caller order; catalog order remains core order. Equal anonymous records can have
different authored supplemental payloads because occurrence coverage and payload
ordering are separate facts. Nothing makes an anonymous occurrence addressable.

## Filter and group declaration rationale

Term references reproduce the exact ordered TermTarget core filter. Complete
Local/External values and duplicate occurrences survive. Core absence is copied
literally; present model-only references produce a present empty filter.

Conceptual groups are explicitly selected full reference occurrences, in caller
order, from both target domains of the same element. Their multiset is bounded
by the core's multiplicity. This permits explicit grouping without introducing
cluster identities, relation-name heuristics, transitive traversal, or inferred
membership. Present empty is meaningful even when the core's references are
absent; nonempty selection cannot be invented from absence. A reasoned group gap
retains its own valid literal detail.

## Independent supplemental facts

Term type, BoK kind, labels, and preferred label each require their own Presence.
All four absence reasons remain available. Ordered duplicate labels and explicit
language None are meaningful supplied facts. Preferred label need not be one of
the labels, and a reference preferred label is not the Term's preferred label.
The supplier performs no language selection, BCP 47 normalization, synonym
canonicalization, trimming, case folding, or text-derived classification.

Strict Unicode edge-space and ISO-control semantics match Foundation for supplied
tokens, detail, and labels. Source authority/path/SHA-256/line, relation/profile
scope, context, boundary, declared or derived origin, rule ID, and ordered source
vectors remain literal admitted facts. No I/O, hashing, clocks, or source checks
are needed by this immutable publication boundary.

## Base and owned schema boundary

Publication admits the base before own conflict and declaration validation.
Reading admits the base before requiring the owned child and checks child version
before contradictory owned fields. The shared factory then republishes with only
the owned namespace removed from the caller extension set. All peer namespaces
are retained opaquely; independent peer readers own their own certification.

The unchanged outer version is `cozy.cml.semantic-metadata.v1`; the child namespace
is `cozy.cml.terminology` with `cozy.cml.terminology.v1`. Owned child, projection,
Presence, label, and enum shapes are closed recursively. Complete core/reference
binding rejects alterations inside embedded records. Recursive sorting and proper
JSON escaping remain responsibilities of the accepted base publisher; array
order remains authored order.

Typed diagnostics use fixed schema coordinates and literal safe details from
the supplier spec. Factory errors collect in declaration/field order. Wire
decoding can stop at its first malformed owned field. Only PublicationFailure
contains its original typed base cause; no user payload enters a new diagnostic.

## Evidence and subsequent boundaries

The independent executable specification authors complete typed/wire declarations,
rejection tables, all absence branches, anonymous multiplicity, and non-discarded
ScalaCheck permutation properties. Expected facts come from raw fixture inputs,
not supplier output or supplier reads. Production actions belong under When,
and Then observes captured results. Authorship does not establish acceptance.

[Phase 54.5](../phase/phase-54.5.md) separates the supplier, exact qualified
navigation, and fixed-fixture/handoff Steps. Navigation and resources remain
pending. Event Storming belongs to Phase 54.6; final consumer fixture acceptance
belongs to Phase 54.7. No external CBD Support integration is claimed here.
