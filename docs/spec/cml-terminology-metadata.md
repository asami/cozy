# CML Terminology Metadata

Status: S54.5.1 CLOSED at commit `81cd3bbcbaaa293c1cc7a7a33901aa99b8d7c2cc`; S54.5.2 CLOSED at commit `350175851dfa267aaa35c4cae865ad67f15f0cd8`; S54.5.3 validated and independently accepted; manual Step commit pending.
Date: 2026-10-04

This specification defines the internal `private[cozy]` Terminology supplier in
`cozy.modeler`. It adds `cozy.cml.terminology` to the accepted Semantic Metadata
envelope without changing Foundation, the legacy model metadata API, or peer
schemas. [Design](../design/cml-terminology-metadata.md) fixes the declaration
responsibilities. [Phase 54.5](../phase/phase-54.5.md) tracks acceptance separately.

## Typed API

The facade is `CmlTerminologyMetadata`. Every carrier field and factory argument
is required; no defaults select a classification, label, reference, or gap.

```scala
sealed abstract class BokKind
case object Mono extends BokKind
case object Koto extends BokKind

ElementProjection(
  element: ElementRecord,
  termReferences: Presence[Vector[SemanticReference]],
  conceptualGroups: Presence[Vector[SemanticReference]])
TermProjection(
  term: TermRecord,
  termType: Presence[String],
  bokKind: Presence[BokKind],
  labels: Presence[Vector[LocalizedLabel]],
  preferredLabel: Presence[LocalizedLabel])

build(catalog: Catalog, elements: Vector[ElementProjection],
  terms: Vector[TermProjection], extensions: JsObject):
  Either[Vector[TerminologyDiagnostic], Graph]
read(value: JsValue): Either[Vector[TerminologyDiagnostic], Graph]
toJson(graph: Graph): JsObject
canonicalJson(graph: Graph): String
```

`Graph` has a private facade constructor and read-only `catalog`, `envelope`,
`elements`, and `terms`. There is no unchecked `apply` or `copy`. A successful
build retains the original catalog instance and ordered declaration vectors.
Null user input produces typed diagnostics. Rendering a null Graph violates an
internal `require` precondition: `An admitted Terminology graph is required.`

All core carriers are reused unchanged from `CmlSemanticFoundation`: qualified
`ModelElementId`, `TermId`, `RelationId`, `ProfileId`, `SemanticReference`,
`LocalizedLabel`, `SourceAttribution`, `ReferenceBoundary`, `ReferenceOrigin`,
`Presence`, and its four reasons. `BokKind` is a separate closed conceptual enum.
`termType` is an opaque supplied token; it never determines Mono/Koto. Entity
may reference a Koto Term and Event may reference a Mono Term.

## Complete core and reference authority

Each catalog element occurrence of every kind needs one full-value matching
ElementProjection, and each catalog Term needs one full-value matching
TermProjection. Equal anonymous occurrences each consume a declaration; they
do not acquire inferred IDs. Missing, excess, foreign, or forged occurrences
fail. Identity, kind, name, source, and references all participate in element
binding. Every Term identity and source field participates in Term binding.
Catalog order and independently supplied projection order both survive.

For present core references, `termReferences` equals the ordered full-reference
TermTarget filter, including repeated Local and External references. Empty or
model-only core references yield `Present(Vector.empty)`. For absent core
references the projected presence must equal the exact core reason and detail.
An alteration, omission, addition, or reorder fails. Local/External stays
declared even when an External pair also exists in the catalog.

`conceptualGroups` declares an ordered sub-multiset of that element's complete
core references. Both ModelTarget and TermTarget may be selected. A reference
may occur at most as many times as its full core value occurs. Selection order
is independent of core order. An absent core admits a present empty group,
but cannot admit a nonempty group. A valid explicit group absence is retained.
Relation names, source paths, display labels, and positions never infer groups.

Qualified domains remain separate. Model pairs, Term pairs, relation pairs,
and profile pairs never substitute for each other. Vocabulary and profile scope
may differ from target scope. Case, Unicode composition, and delimiters remain
literal. Same names never merge records or resolve synonym candidates.

## Availability, labels, and supplied provenance

Every supplemental Presence is either `Present(payload)` or
`Absent(reason, detail)`, with exactly `NotDeclared`, `NotRepresented`,
`Unsupported`, or `NotApplicable`. Detail is explicit and valid text.
Null presence, reason, payload vectors, items, labels, or enum values fail.
Present empty vectors differ from absence.

Term type, absence detail, label text, and supplied label language are nonnull,
nonempty, Unicode-edge-unpadded, and free of ISO controls. Edge whitespace uses
`Character.isWhitespace` or `Character.isSpaceChar`; nothing is trimmed or
case-folded. Label language is explicitly `None` or `Some(valid text)`. Language
tags are not normalized. Label order and duplicates are preserved. Preferred
label is supplied independently and need not occur in `labels`. Reference
preferred labels and Term preferred labels have separate authority.

Reference relation, profile, context, preferred label, source, boundary, origin,
derived rule ID, and ordered source vector remain complete literal core facts.
Source SHA-256 is supplied provenance; this supplier computes no digest,
currentness, identity, equivalence, synonym resolution, I/O, or lookup fallback.

## Publication and strict wire contract

The unchanged outer version is `cozy.cml.semantic-metadata.v1`. The exact owned
extension object is:

```json
{
  "schemaVersion": "cozy.cml.terminology.v1",
  "elements": [],
  "terms": []
}
```

Element items have exactly `element`, `termReferences`, `conceptualGroups`.
Term items have exactly `term`, `termType`, `bokKind`, `labels`, `preferredLabel`.
Embedded element/Term JSON must equal the entire admitted canonical base JSON
object. Each embedded reference must equal a complete admitted reference of
that element. Core and reference decoding is an exact correspondence lookup,
not a second permissive Foundation reader. Tampered nested fields fail binding.

Every owned Presence is exactly one of:

```json
{"status":"present","value":[]}
{"status":"absent","reason":"not-declared","detail":"Explicit gap."}
```

Absent reason wire values are `not-declared`, `not-represented`, `unsupported`,
`not-applicable`. Present value uses its field's required string, array, enum,
or label shape. BokKind payload is exactly string `mono` or `koto`. Labels are
exactly `{"text":"Literal","language":null}` or the same two fields with a
string language. None requires explicit null; omitted language fails.
Missing, extra, mixed, null, wrong-type, wrong-case, or unknown-case owned
containers fail recursively. Peer objects remain opaque.

Build first calls the accepted base factory and retains original typed failures.
It then rejects a caller-supplied own namespace, validates declarations, and
adds the child through the base facade. Read first admits the base, requires the
own object, checks the child version before contradictory child fields, decodes
strictly, and shares the factory with only its own namespace removed. Unsupported
child versions fail closed. Complete core and per-element reference JSON
correspondence is computed once per operation and reused during read publication.

Canonical output delegates to the base recursive object-key sort and Play JSON
escaping. Array order is authored order. This contract makes no RFC 8785 claim.
Opaque Structure, Classification, Dynamic, Use Case, and other peer objects
survive unchanged; Terminology admission does not certify their semantics.

## Safe diagnostics

`TerminologyDiagnostic(kind, path, detail, publication)` uses a closed
`TerminologyDiagnosticKind`: `InvalidShape`, `UnsupportedSchemaVersion`,
`InvalidCoreBinding`, `DuplicateProjection`, `MissingProjection`,
`InvalidTerminologySemantics`, `PublicationFailure`, `ExtensionConflict`,
`UnknownElement`, `UnknownTerm`, `UnknownLink`. The last three serve
the navigation boundary below. Only PublicationFailure retains
`Some(original PublicationDiagnostic)`; every other kind carries None.

| Condition | Fixed detail |
| --- | --- |
| Malformed required shape | The Terminology value does not match the required v1 shape. |
| Unsupported child version | The Terminology schema version is not supported. |
| Typed full core mismatch | The Terminology projection does not bind exactly to an admitted core record. |
| Wire core mismatch | The embedded Terminology record does not exactly match admitted core JSON. |
| Wire reference mismatch | The Terminology reference does not exactly match an admitted core reference. |
| Missing projection | An admitted core record lacks its required Terminology projection occurrence. |
| Excess projection | A core record has excess Terminology projection occurrences. |
| Invalid declared semantics | The declared Terminology reference, label, classification or absence is invalid. |
| Base failure | The foundation metadata publication is invalid. |
| Own namespace conflict | The Terminology extension namespace is already supplied by the caller. |

Paths use `graph`, `extensions`, `extensions.cozy.cml.terminology` for the missing
child or malformed child container, `schemaVersion`, `elements`, `terms`,
`elements[i].element`, `.termReferences`, `.conceptualGroups`, `terms[i].term`,
`.termType`, `.bokKind`, `.labels[j]`, `.preferredLabel`, and known schema field
suffixes. Indices come from supplied array positions. Coverage errors use
`elements` or `terms`. PublicationFailure retains the base's fixed logical path.
No hostile keys, values, names, source paths, or language contents are echoed.
Factory diagnostics collect deterministically in declaration/field order;
wire decoding may stop at the first malformed owned coordinate.

## Authored executable acceptance map

The independent executable specification is
[CmlTerminologyMetadataSpec](../../src/test/scala/cozy/modeler/CmlTerminologyMetadataSpec.scala).
Every behavior block has adjacent Given setup, When production interpretation,
and Then assertions. Raw fixture declarations and the complete wire oracle do
not call the supplier. Build, read, render, base admission, and ScalaCheck actions
occur under When. S54.5.1 passed 195 tests in 14 suites and independent protected
review with focused closure; its acceptance commit is
`81cd3bbcbaaa293c1cc7a7a33901aa99b8d7c2cc`.

| Scenario | Required observable behavior |
| --- | --- |
| TM-01 | Complete typed and wire catalog/source/envelope equality with independent projection order. |
| TM-02 | Entity→Koto, Event→Mono, independent termType, exact qualified domains and ordered duplicate labels. |
| TM-03 | Exact repeated Local/External Term filter, model-only empty filter, bounded ordered model/Term groups and contradictions. |
| TM-04 | Every reason/detail branch at all fields, empty vectors, explicit language None and equal anonymous occurrence payload order. |
| TM-05 | Full record/source/identity/reference mutations, missing/excess/foreign occurrences and recursive null typed boundaries. |
| TM-06 | Original base failures before child checks, own conflict, opaque peers and unchanged outer API shape. |
| TM-07 | Recursive closed shape mutation table, exact full core/reference binding, safe paths/details and child-version precedence. |
| TM-08 | Invalid text/detail/language/qualified identities and preservation of valid literal supplied provenance. |
| TM-09 | Independent catalog/projection/reference permutations, explicit groups, declared/anonymous absence modes and recursive JSON key reconstruction; minimum 100 successes, zero discards. |

## Exact qualified navigation

`CmlTerminologyNavigation` consumes an already admitted Graph. Its immutable
Navigation has a private constructor, retains the original `graph` and `catalog`
instances, and exposes `allLinks: Vector[TermLink]`. It does not read, render,
re-admit, or fetch the graph, inspect peer namespaces, or compute source currentness.

```scala
final case class TermLink(element: ElementRecord, reference: SemanticReference)
sealed abstract class ReferenceTarget
final case class LocalTarget(link: TermLink, term: TermProjection) extends ReferenceTarget
final case class ExternalTarget(link: TermLink) extends ReferenceTarget

build(graph: Graph): Either[Vector[TerminologyDiagnostic], Navigation]
element(id: ModelElementId): Either[TerminologyDiagnostic, ElementProjection]
term(id: TermId): Either[TerminologyDiagnostic, TermProjection]
links(id: ModelElementId): Either[TerminologyDiagnostic, Presence[Vector[TermLink]]]
linksForTerm(id: TermId): Either[TerminologyDiagnostic, Vector[TermLink]]
resolve(link: TermLink): Either[TerminologyDiagnostic, ReferenceTarget]
```

Queries require the entire literal typed qualified identity. Every core family
with a present identity is addressable; anonymous records remain observable but
unaddressable. Names, labels, paths, digests, delimiter splitting, suffixes,
same-named peers, and coincident Model/Term pairs do not create aliases. Supplied
termType and Mono/Koto stay independent of core kind.

`allLinks` follows catalog element order, then each element's full authored
reference order, retaining TermTarget references only. Projection array order
does not reorder links. Complete element and reference values, duplicate
references and equal anonymous occurrences survive. `links` first finds the
exact projection and returns its ordered complete links; each Absent reason and
detail is returned verbatim. Present-empty and model-only references return
Present-empty. `linksForTerm` first requires an admitted Term, then returns only
exact Local matches in the same order with multiplicity. Registered External
equal pairs are excluded; known unused Terms return empty; External-only
unregistered pairs remain UnknownTerm.

`resolve` first validates the full input tree's shape, then requires full-value
membership in `allLinks`. Equivalent immutable copies are members. Altered or
foreign values fail with `UnknownLink` only when their complete value is absent
from `allLinks`; an alteration that produces another complete admitted value
remains a member. Duplicate occurrences need no fabricated occurrence ID.
External yields the unchanged link even when its pair is registered; Local
yields the exact original TermProjection. Candidate relations, labels, derived
rules and source vectors remain opaque facts. Resolution never merges Terms,
selects labels or substitutes endpoints.

### Shape and safe query diagnostics

Shape checks allow nonnull empty kind/name because those descriptive fields
have no stronger accepted contract. They require nonnull closed Presence branches,
strict qualified components, reference boundary, relation/profile/context/label,
source and origin. Element reference vectors allow both target domains; the
independent TermLink reference specifically requires TermTarget. Absence reasons
are exactly the four Foundation reasons with strict detail; label language is a
nonnull None/Some(strict text). Derived requires a strict rule and a nonempty
nonnull vector of valid sources. Source authority is strict text, path is a
nonempty project-relative POSIX path without controls, scheme, backslash,
leading slash or empty/dot/dot-dot segments, digest is 64 literal lowercase
hex characters, and line is None or a positive Some(Int).

Shape does not certify residency, Local endpoint registration, source-authority
cross-field agreement, or record/reference membership. A structurally valid
foreign or altered link whose complete value is absent from `allLinks` fails
UnknownLink rather than InvalidShape; a complete admitted value remains a member.
Queries validate before dereferencing and never echo values or throwable causes.
Unicode edge space uses `Character.isWhitespace || Character.isSpaceChar`;
ISO controls are checked over code points. Case, interior Unicode and delimiters
remain literal. Every diagnostic below has `publication=None`.

| Condition | Kind / coordinate | Exact detail |
| --- | --- | --- |
| Null Graph | InvalidShape / graph | An admitted Terminology graph is required. |
| Null/malformed query ID or either component | InvalidTerminologySemantics / element.identity, term.identity, links.identity or linksForTerm.identity | The declared Terminology reference, label, classification or absence is invalid. |
| Valid unknown model identity | UnknownElement / element.identity or links.identity | No admitted Terminology element has the requested qualified identity. |
| Valid unknown Term identity | UnknownTerm / term.identity or linksForTerm.identity | No admitted Terminology Term has the requested qualified identity. |
| Null/malformed complete TermLink | InvalidShape / resolve.link | The Terminology value does not match the required v1 shape. |
| Well-formed full-value nonmember | UnknownLink / resolve.link | The Terminology link is not admitted by this graph. |

### Navigation executable acceptance map

[CmlTerminologyNavigationSpec](../../src/test/scala/cozy/modeler/CmlTerminologyNavigationSpec.scala)
authors these scenarios with raw Given facts, all build/read/render/query actions
under When, and observable Then assertions. S2 passed 205 tests in 15 suites;
independent Step review and focused repair closure are accepted. TN-08 passed
100 fixed-seed samples with zero discards and all seven coverage tags positive.
S54.5.2 is CLOSED at the parent manual local acceptance commit
`350175851dfa267aaa35c4cae865ad67f15f0cd8`.

| Scenario | Observable behavior |
| --- | --- |
| TN-01 | Qualified model/Term scopes, coincident domains, original instances, complete supplemental facts, typed/wire navigation and an empty graph. |
| TN-02 | Full Local/External endpoints, registered/unregistered External targets, copied value membership and opaque candidates/provenance. |
| TN-03 | Equal anonymous Event occurrences and repeated complete links without fabricated lookup aliases. |
| TN-04 | Catalog/reference order, independent projection order, duplicate-preserving Local-only reverse links. |
| TN-05 | All four exact gap reasons/details, present-empty/model-only references and known unused Terms. |
| TN-06 | Fixed unknown diagnostics and complete altered/foreign full-value membership. |
| TN-07 | Null and malformed IDs on both components of all query families and recursively malformed complete link trees. |
| TN-08 | Independent occurrence-index permutations in declared/absence modes; minimum 100 successes, zero discards, fixed seed 42, collected positive actual value-change coverage. |

## Fixed fixture and supplier handoff contract

The fixed UTF-8 [declared](../../src/test/resources/cozy/modeler/terminology-metadata-v1-declared.json)
and [explicit-absence](../../src/test/resources/cozy/modeler/terminology-metadata-v1-absence.json)
resources and [supplier handoff](../design/cml-terminology-metadata-handoff.md)
extend the accepted declared Use Case predecessor's complete 49-record/four-Term
prefix to 55 core occurrences and seven Terms. The new child covers every core
and Term occurrence; two complete equal anonymous Event records retain duplicate
multiplicity. Exactly two new Structure Entity projections are appended to the
original three. Other prefix carriers and all four opaque peers retain every
field and order. Event requires no new Dynamic family projection.

New typed expected facts are independent literal declarations. Prefix authority
comes only from the separately parsed accepted predecessor and its independent
peer readers. A pure test-owned renderer encodes every full source, reference,
core and projection field, assembling the complete expected JSON; recursive
test-owned key sorting supplies its canonical string. New fixture decoding and
production renderers never provide expectations. Whole JsValue equality guards
every build/read/render/canonical-reconsumption route. Structure, Classification,
Dynamic and Use Case independently admit the same expanded resource and retain
their full catalogs/carrier vectors. Legacy CmlModelMetadataSpec remains its
separate accumulator gate.

[CmlTerminologyMetadataFixtureSpec](../../src/test/scala/cozy/modeler/CmlTerminologyMetadataFixtureSpec.scala)
authors these independently identifiable scenario families:

| Scenario | Observable fixture contract |
| --- | --- |
| TF-01 | Real declared resource; complete 55/7 core, projection, source, reference, envelope and independent canonical equality. |
| TF-02 | Real absence resource; all four link gaps, cyclic supplemental gaps, Unsupported groups and anonymous present-empty records. |
| TF-03 | JSON-only qualified Entity→Koto/Event→Mono questions with full Local/External carriers and opaque candidate/provenance facts. |
| TF-04 | Authored link/reverse order and duplicates, 11 declared links, TA6/TB2 Local reverse links, known unused Term, exact absence and safe unknown/member boundaries. |
| TF-05 | Independent prefix and all four peer gates; exact two Structure additions and qualified Use Case→Dynamic→static continuation. |
| TF-06 | Recursive single-mutation strict shapes, enum/label/Presence/core/reference failures, version precedence and original base PublicationDiagnostic. |
| TF-07 | Typed/wire missing/excess/forged full records and Terms, equal anonymous occurrences, exact filters and bounded complete groups. |
| TF-08 | Independent recursive object-key reconstruction, whole wire/canonical equality, authored arrays and explicit nulls. |
| TF-09 | Seed42 property with both modes per sample, minimum100 successes, zero discards, independent occurrence-index permutations and positive actual coverage. |

Given owns raw literals and mutation/permutation plans; When owns resource I/O,
predecessor/supplier/peer admission, rendering, queries and Test.check; Then
observes captured outcomes. Arrays retain authored order and duplicates, with
reference permutations adjusting exact term filters and embedded peer records.
S3 passed 214 tests in 16 suites, including TF-01..09. TM-09, TN-08 and TF-09
each passed 100 samples with zero discards and all seven actual coverage tags
positive; TF-09 exercised both modes in every sample. Independent complete Step
review passed with no blockers. The parent manual Step commit remains pending.
Phase 54.6
owns Event Storming and Phase 54.7 final consumer fixture acceptance. CNCF runtime
and external Textus CBD Support integration retain their owners; full Phase
review, ordinary full validation and distinct release remain pending.
