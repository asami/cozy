# CML Terminology Metadata

Status: S54.5.1 supplier validated and independently accepted; manual Step commit pending.
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
`UnknownElement`, `UnknownTerm`, `UnknownLink`. The last three are reserved for
the separately planned navigation boundary. Only PublicationFailure retains
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
occur under When. The following scenarios are authored; results remain pending.

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

Navigation, fixed resources, consumer handoff, Event Storming traversal, and
external CBD Support acceptance belong to the subsequent recorded boundaries.
