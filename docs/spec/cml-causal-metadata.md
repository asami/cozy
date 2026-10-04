# CML Causal Metadata v1

The S54.6.1 supplier derives a Causal consumer view solely from an admitted
`CmlSemanticMetadata.Envelope`. It adds `cozy.cml.causal` with schema version
`cozy.cml.causal.v1` to the unchanged `cozy.cml.semantic-metadata.v1` envelope.
The paired working specification is
[`CmlCausalMetadataSpec`](../../src/test/scala/cozy/modeler/CmlCausalMetadataSpec.scala).
Responsibility and exclusions are in the [design](../design/cml-causal-metadata.md).
Traversal is pending S54.6.2; fixed resources and final handoff are pending S54.6.3.

## Supplier API

`private[cozy] CmlCausalMetadata` supplies:

```scala
def build(envelope: CmlSemanticMetadata.Envelope): Either[Vector[CausalDiagnostic], Graph]
def read(value: JsValue): Either[Vector[CausalDiagnostic], Graph]
def toJson(graph: Graph): JsObject
def canonicalJson(graph: Graph): String
```

Graph is immutable, with a constructor private to the facade and no public
`apply` or `copy`. Its read-only fields are `catalog`, `envelope`, `causalLinks`,
`actorOperations`, `aggregateBoundaries`, `ruleContexts`, `collaboratorContexts`,
`modelReferences`, and `capabilityGaps`. Build retains the supplied Catalog
instance and the complete ordered core, Terms and unrelated object extensions.
Foundation publication is re-admitted before deriving data. Canonical JSON uses
the accepted recursive object-key sorting; arrays and duplicate occurrences are
literal semantic values.

## Exact facts and availability

Dynamic, Use Case and Structure are consumed only through their strict accepted
readers over the complete root. Supplied malformed peers fail; they never become
missing capabilities. Classification, Terminology and other object namespaces
are preserved opaque.

| Graph field | Admitted evidence |
| --- | --- |
| `causalLinks` | Complete Dynamic `CausalLinkProjection` values, including kind, owner, source, target, rule and affectedElements. |
| `actorOperations` | `ActorOperations(element, operations)` from each Use Case Actor. |
| `aggregateBoundaries` | `AggregateMembership(element, aggregateBoundary)` from each Structure ElementProjection. |
| `ruleContexts` | `RuleContext(element, rules)` from each Dynamic Workflow and Activity. |
| `collaboratorContexts` | `CollaboratorContext(element, collaborators)` from each Use Case element. |
| `modelReferences` | `ModelReferences(element, references)` for each core occurrence, retaining only complete ordered ModelTarget references. |
| `capabilityGaps` | `CapabilityGap(owner: Option[ElementRecord], field: String, absence: Absent)` in the order below. |

Carriers reuse exact Foundation ElementRecord, Presence, SemanticReference,
qualified ModelElementId, SourceAttribution, origin and Local/External types;
Dynamic ModelReference/CausalLinkProjection and Structure AggregateBoundary
are unchanged. TermTarget references remain in core but are excluded from the
modelReferences view. Model-only, term-only and empty Present references yield
their exact filtered Present vectors; an absent core references value is copied.

Derived vectors follow complete core-record occurrence order. The nth equal
anonymous peer owner matches the nth equal core owner, preserving authored
payload order. ID-only, first-match, kind/name/source-only or concatenated-ID
binding is forbidden. Case and Unicode composition remain distinct; no ordinal,
name, Term, Relation, Profile or source hash supplies identity.

Missing Dynamic yields absent causalLinks and ruleContexts with
`Absent(NotDeclared, "No admitted Dynamic metadata is supplied.")`.
Missing Use Case yields absent actorOperations and collaboratorContexts with
`Absent(NotDeclared, "No admitted Use Case metadata is supplied.")`.
Missing Structure yields absent aggregateBoundaries with
`Absent(NotDeclared, "No admitted Structure metadata is supplied.")`.
Present peers produce Present vectors, including Present(empty). All four
absence reasons and their exact details survive.

Only Dynamic Cause and Reaction assert direct causation. Actor operations,
affected subjects, rules, collaborators, aggregate membership and generic
references remain their respective declarations. A cause from an operation to
an event affecting an Aggregate does not assert that the Aggregate caused the
event. Workflow co-membership, names and expression text add no causal carriers.
Local Query/View and External dependency references retain complete supplied
relation, profile, context, label, source and declared/derived provenance with
ordered duplicate input sources. An External pair stays External even when
resident locally; no query-update or executable dependency claim is added.

## Exact owned child

There are exactly eight fields, without defaults or extra fields:

```text
schemaVersion: "cozy.cml.causal.v1"
causalLinks: Presence[Array of complete Dynamic causal carrier JSON]
actorOperations: Presence[Array {element:<complete core JSON>, operations:<ModelReference Presence>}]
aggregateBoundaries: Presence[Array {element:<complete core JSON>, aggregateBoundary:<Structure Presence>}]
ruleContexts: Presence[Array {element:<complete core JSON>, rules:<ModelReference Presence>}]
collaboratorContexts: Presence[Array {element:<complete core JSON>, collaborators:<ModelReference Presence>}]
modelReferences: Array {element:<complete core JSON>, references:<filtered SemanticReference Presence>}
capabilityGaps: Array {owner:<complete core JSON or null>, field:<fixed coordinate>, absence:<Absent JSON>}
```

Every embedded core, identity, reference, source, Presence and aggregate value
uses its accepted v1 schema. Rendering pairs full typed values with admitted
core/peer JSON; it does not decode private Foundation fields or expose base helpers.

## CapabilityGap order

1. Missing-family gaps, owner None: causalLinks, actorOperations,
   aggregateBoundaries, ruleContexts, collaboratorContexts.
2. In core occurrence order, absent identity then absent references, with fields
   `identity` and `modelReferences.references` and Some(complete owner).
3. For each present causal carrier, absent source, target, rule, affectedElements,
   with fields `causalLinks.source`, `causalLinks.target`, `causalLinks.rule`,
   `causalLinks.affectedElements`.
4. In family and derived occurrence order: `actorOperations.operations`,
   `aggregateBoundaries.aggregateBoundary`, then nested
   `aggregateBoundaries.aggregate` and `aggregateBoundaries.membership`,
   `ruleContexts.rules`, `collaboratorContexts.collaborators`.
5. Exactly one global final gap:
   `CapabilityGap(None, "causalInference", Absent(Unsupported,
   "No causation is inferred from names, membership, generic relations or expression text."))`.

Duplicate anonymous owners produce duplicate gap occurrences. Present-empty
fields produce no gap. An absent aggregateBoundary produces only its outer gap.

## Strict receiver and safe diagnostics

The reader admits Foundation first, requires an object owned child and exact
v1 version, strips only the owned namespace, admits the remaining base and
known peers, and rebuilds the deterministic summary. The supplied child must
equal the complete derived child as JsValue. Object-key order is immaterial;
array order, multiplicity, all fields and absence details are exact. This is
publication correspondence, with no source-currentness or transport certificate.
Malformed Foundation, including null JSON references, returns a safe value.
For a nonobject owned child, the remaining Foundation must first be admitted
before reporting the fixed owned-shape diagnostic.

The closed CausalDiagnosticKind alternatives are InvalidShape,
UnsupportedSchemaVersion, PublicationFailure, InvalidSupplier, ExtensionConflict,
InvalidProjection, UnknownElement and UnknownLink. A diagnostic has exactly
`kind`, `path`, `detail`, without raw peer or user payload.

| Condition | Kind / path / exact detail |
| --- | --- |
| Null build envelope | InvalidShape / `envelope` / `An admitted semantic metadata envelope is required.` |
| Invalid Foundation publication | PublicationFailure / `publication` / `The semantic metadata publication is invalid.` |
| Caller supplies own namespace to build | ExtensionConflict / `extensions` / `The Causal extension namespace is already supplied by the caller.` |
| Malformed consumed peer | InvalidSupplier / `extensions.<namespace>` / `The supplied Dynamic metadata is not admitted.` or `The supplied Use Case metadata is not admitted.` or `The supplied Structure metadata is not admitted.` |
| Missing/nonobject owned child | InvalidShape / `extensions.cozy.cml.causal` / `The Causal value does not match the required v1 shape.` |
| Missing/nonstring owned version | InvalidShape / `extensions.cozy.cml.causal.schemaVersion` / same shape detail |
| Any other string version | UnsupportedSchemaVersion / `extensions.cozy.cml.causal.schemaVersion` / `The Causal schema version is unsupported.` |
| Any child mismatch, extra/missing/wrong field, forged owner/provenance, absence/order/count or peer disagreement | InvalidProjection / `extensions.cozy.cml.causal` / `The Causal projection does not match its admitted semantic evidence.` |

Null Graph rendering is internal DbC, requiring
`An admitted Causal graph is required.`. Diagnostics never echo malicious
keys, IDs, source paths or upstream diagnostic payloads.

## Executable acceptance

CM-01 specifies complete independent typed/wire correspondence and retention;
CM-02 qualified identity; CM-03 missing, empty and four absence reasons with
ordered gaps; CM-04 anonymous duplicates and full-record binding; CM-05 strict
known suppliers and opaque peers; CM-06 owned shape/version and recursive
tampering; CM-07 declaration strength; CM-08 Local/External generic reference
provenance; CM-09 null/conflict safe diagnostics; CM-10 active ScalaCheck with
at least 100 successes and zero discards for independent pair, peer-carrier,
duplicate and recursive key-order properties. The suite checks Test.Result
status/counts and complete independent expected values, beyond round trips.
