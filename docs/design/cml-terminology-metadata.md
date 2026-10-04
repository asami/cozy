# CML Terminology Metadata Design

Date: 2026-10-04
Status: S54.5.1 CLOSED at commit `81cd3bbcbaaa293c1cc7a7a33901aa99b8d7c2cc`; S54.5.2 CLOSED at commit `350175851dfa267aaa35c4cae865ad67f15f0cd8`; S54.5.3 validated and independently accepted; manual Step commit pending.

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
navigation, and fixed-fixture/handoff Steps. S1 passed 195 tests in 14 suites,
independent protected review and focused closure, and is CLOSED at commit
`81cd3bbcbaaa293c1cc7a7a33901aa99b8d7c2cc`. Navigation passed 205 tests in 15 suites;
independent Step review and focused repair closure are accepted. The manual Step
acceptance commit is `350175851dfa267aaa35c4cae865ad67f15f0cd8`.
S3 fixed resources passed 214 tests in 16 suites and independent complete Step
review; the parent manual Step commit remains pending.
Event Storming belongs to Phase 54.6; final consumer fixture acceptance
belongs to Phase 54.7. No external CBD Support integration is claimed here.

## Navigation responsibility

The additive `CmlTerminologyNavigation` object consumes the private admitted
Graph directly. Navigation retains its original Graph/Catalog instances and
immutable exact qualified ModelElementId and TermId projection indexes. All
present core identities, including families beyond Entity/Event, are navigable;
anonymous occurrences get no generated identity. No names, paths, digests,
suffixes, labels, delimiter interpretation or domain coincidence become aliases.
The [specification](../spec/cml-terminology-metadata.md#exact-qualified-navigation)
defines the exact API and fixed diagnostics, linked to executable TN-01..08.

The ordered `allLinks` Vector is built from catalog records and their core
TermTarget references, preserving complete values and occurrence counts. An
immutable Set is used solely for complete link membership; it never governs
ordering or reverse multiplicity. Element queries return original projections.
Element links preserve literal absence and present-empty values. Reverse queries
first require an admitted Term and filter exact Local targets from the ordered
Vector; External equal pairs remain excluded.

Resolution separates shape from membership. Private pure closed-shape predicates
validate the full record/reference/source/origin/Presence tree before any query
dereference. They accept descriptive empty kind/name and both target families
inside element references, while a TermLink requires TermTarget. They enforce
Foundation's strict text, project-relative source path, literal lowercase digest,
positive optional line, four absence branches, explicit label language Option
and derived source vector shapes. They do not repeat admission, source-authority
cross-field checks or graph residency; structurally valid foreign values receive
UnknownLink only when their complete value is absent from `allLinks`. Complete
admitted values remain members. Malformed shape receives InvalidShape;
malformed query components receive InvalidTerminologySemantics; valid missing
qualified identities receive UnknownElement/UnknownTerm. Fixed safe coordinates,
details and publication=None preserve the boundary without input echo.

Full-value equivalent copied links are valid, including duplicates with no
occurrence identity. Local resolution returns the original full TermProjection;
External returns its original link without local endpoint substitution. Candidate
relations, localized/reference labels, supplied classifications, context and
provenance survive literally. BoK retains synonym policy. Navigation neither
selects a preferred label nor merges endpoints, computes currentness, re-admits
or serializes the Graph, reads peers, fetches provenance or calls legacy adapters.

TN-01..08 use complete raw expected carriers. The permutation property selects
remaining occurrence indices without replacement and independently permutes core
references, catalog records/Terms and both projection arrays. Both declared and
explicit absence modes retain anonymous occurrences. Prop.collect records actual
value-vector changes. TN-08 passed 100 samples with fixed seed 42, zero discards
and positive actual counts for both modes and all five permutation dimensions.

## Fixed resource oracle and handoff responsibility

The [declared](../../src/test/resources/cozy/modeler/terminology-metadata-v1-declared.json)
and [explicit-absence](../../src/test/resources/cozy/modeler/terminology-metadata-v1-absence.json)
fixtures extend the complete accepted Use Case prefix. Their 55 core occurrences
and seven Terms include two equal anonymous Event records; all occurrences need
their own Terminology projection. The only peer change is two complete new
Structure Entity projections, yielding five Structure elements. Original relations,
Classification/Dynamic/Use Case vectors and four opaque peers remain intact.
Independent peer readers retain admission responsibility for the same envelope.

The [handoff](cml-terminology-metadata-handoff.md) supplies the exact literal
identity, source, reference, group, label, classification and gap tables and the
JSON-only consumer recipe. Entity→Koto and Event→Mono deliberately demonstrate
separate classification authority. Qualified Local resolution and External
retention preserve complete carriers; synonym candidates remain opaque and BoK
curators own meaning. Modeled provenance is synthetic caller data.

TF-01..09 in [the fixture specification](../../src/test/scala/cozy/modeler/CmlTerminologyMetadataFixtureSpec.scala)
separate accepted prefix authority, raw new typed facts and a pure complete
test-owned wire renderer. No new resource read or production renderer supplies
expected declarations. Full typed/vector/JsValue/canonical equality is the oracle;
there is no saved-output or management-checksum gate. Recursive key rebuilding
preserves arrays and explicit nulls. Occurrence-index permutations without
replacement independently vary core references, Catalog/Term order and projection
orders in both modes per sample, adjust embedded records/filters consistently,
and collect actual value-vector coverage with seed42 and zero discards.
The [spec acceptance map](../spec/cml-terminology-metadata.md#fixed-fixture-and-supplier-handoff-contract)
defines the nine observable families. S3 passed 214 tests in 16 suites and
independent complete Step review with no blockers. TM-09, TN-08 and TF-09 each
passed 100 samples, zero discards and all seven positive coverage tags; TF-09
exercised both modes per sample. The manual Step commit, full Phase review,
ordinary full validation and distinct release remain pending. Successor and
external ownership remain as recorded above.
