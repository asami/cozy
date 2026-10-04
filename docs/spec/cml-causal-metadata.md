# CML Causal Metadata v1

The S54.6.1 supplier derives a Causal consumer view solely from an admitted
`CmlSemanticMetadata.Envelope`. It adds `cozy.cml.causal` with schema version
`cozy.cml.causal.v1` to the unchanged `cozy.cml.semantic-metadata.v1` envelope.
The paired working specification is
[`CmlCausalMetadataSpec`](../../src/test/scala/cozy/modeler/CmlCausalMetadataSpec.scala).
Responsibility and exclusions are in the [design](../design/cml-causal-metadata.md).
S54.6.2 traversal and CT-01..12 are accepted after selected validation: all 239
tests in 18 suites passed. CT-12, CM-10, TM-09, TN-08 and TF-09 each recorded
100 successes, zero discards and passed status. Fresh independent
protected-focused S2 review is PASS; the parent verified manual native local
Step commit `661e11b848102faf143bf5f4dc173e5ab7bcafdd` and its receipt.
S2 is CLOSED. S54.6.3 fixed declared/absence resources, CF-01..08 and supplier
handoff are accepted after parent mechanical preflight and selected validation:
all 247 tests in 19 suites passed. Fresh independent lightweight S3 review is
PASS with no CPB, HYG or DEV items. S3 is CLOSED at parent-verified manual
native commit `2360d78ac1ee24e4b9e51465d41f669a861283f6` and its actual receipt,
whose sole parent is S2 `661e11b848102faf143bf5f4dc173e5ab7bcafdd`.
[Phase 54.6](../phase/phase-54.6.md) is CLOSED with all five original obligations,
all three native Steps and all four MMD outcomes accepted.
The sole independent full Phase review passed with no Current Phase Blockers,
Hygiene or Development Candidates. Ordinary `sbt --batch test` passed 2868
tests in 201 suites, zero failures/aborted suites, 9 pre-existing optional/deferred
canceled cases. SBT and wrapper exited zero; the serial lock was released.
The distinct parent manual local release binds `P546-CLOSURE-001@1`; its native
commit and closure receipt are verified after committing these final records.

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

## Qualified causal and context traversal

`private[cozy] CmlCausalTraversal` consumes only an admitted Causal Graph.
Its final `Traversal` has a constructor private to the facade, retains the exact
`graph`, and exposes `nodes` as every catalog ElementRecord occurrence, including
anonymous, Query, View, Component and unconnected records, and ordered `allLinks`.
The paired working specification is
[`CmlCausalTraversalSpec`](../../src/test/scala/cozy/modeler/CmlCausalTraversalSpec.scala).

```scala
def build(graph: CmlCausalMetadata.Graph): Either[Vector[CausalDiagnostic], Traversal]
// Methods on Traversal:
def node(id: ModelElementId): Either[CausalDiagnostic, ElementRecord]
def outgoing(id: ModelElementId): Either[CausalDiagnostic, Vector[TraversalLink]]
def incoming(id: ModelElementId): Either[CausalDiagnostic, Vector[TraversalLink]]
def causes(id: ModelElementId): Either[CausalDiagnostic, Presence[Vector[TraversalLink]]]
def consequences(id: ModelElementId): Either[CausalDiagnostic, Presence[Vector[TraversalLink]]]
def affecting(id: ModelElementId): Either[CausalDiagnostic, Presence[Vector[CausalLinkProjection]]]
def resolve(link: TraversalLink): Either[CausalDiagnostic, ReferenceTarget]
```

`TraversalLink` is the immutable complete value `(owner: ElementRecord,
kind: LinkKind, source: Presence[CmlDynamicMetadata.ModelReference],
target: Presence[CmlDynamicMetadata.ModelReference], sourceAttribution:
SourceAttribution, origin: ReferenceOrigin, evidenceField: String,
evidenceIndex: Int, semanticReference: Option[SemanticReference])`. Evidence
coordinates describe occurrences; they do not supply node or anonymous identity.
The closed LinkKind alternatives have these disjoint meanings and emission order
within each complete catalog owner occurrence:

| Kind | Source / target and evidence |
| --- | --- |
| Cause, Reaction | Exact causal carrier endpoints, even absent; `causalLinks`, index 0; owner.source, Declared, None. These alone assert direct causation. |
| AffectedSubject | Exact causal source to each Present affected subject; `causalLinks.affectedElements`, original array index. |
| RuleInput, RuleOutput | Exact causal source to Present rule, then rule to exact target; `causalLinks.rule`, indices 0 and 1. Declared rule context, without execution inference. |
| ActorOperation | Owner identity source to each Present operation; `actorOperations.operations`, original array index. |
| AggregateMember | Owner identity source to nested aggregate Presence, or exact outer boundary absence; `aggregateBoundaries.aggregate`, index 0. One link per supplied Structure element; membership text remains in Graph. |
| WorkflowRule | Owner identity source to each Present Workflow or Activity rule; `ruleContexts.rules`, original array index. |
| Collaborator | Owner identity source to each Present collaborator; `collaboratorContexts.collaborators`, original array index. |
| ModelReferenceLink | Owner identity source to full ModelTarget pair/boundary; `modelReferences.references`, filtered ModelTarget index; exact reference.source, reference.origin and Some(full SemanticReference). Leading TermTarget values do not advance this index. |

An owner source is Present(Local qualified identity), or the owner's exact
anonymous identity absence. All context kinds except ModelReferenceLink use
owner.source, None semanticReference and
`Derived("cozy.cml.causal.<token>.v1", Vector(owner.source))`, with exact tokens
`affected-subject`, `rule-input`, `rule-output`, `actor-operation`,
`aggregate-member`, `workflow-rule`, and `collaborator`. ModelReferenceLink keeps
complete relation/profile/context/preferredLabel and ordered duplicate Derived
sources. Absent or empty affected, rule-list, operations, collaborators or
references emit no extra references; absent causal rule emits neither rule link.
Direct carriers and supplied Structure member links retain their stated exceptions.

Catalog owner order precedes family order. Each family independently associates
the nth equal full-record carrier with the nth catalog occurrence; anonymous
duplicates and authored arrays remain ordered and complete. No ID-only,
source-only, name-based or first-match correspondence is allowed.

Outgoing and incoming index only Present Local endpoint pairs, independently
on each side. One absent endpoint leaves the opposite Local adjacency intact.
Resident External references never enter Local adjacency. Causes and consequences
select only incoming/outgoing Cause and Reaction respectively. Affecting returns
complete original causal carrier occurrences whose affectedElements contain the
exact Local pair, once per matching carrier even with repeated affected entries.
Equal duplicate carriers remain separate. Aggregate membership never asserts
aggregate-caused-event; no transitive, reciprocal or policy-execution edge is added.
Consumers follow direct chains with separate requests; direct cycles do not recurse.

Every ID API first validates and looks up the exact qualified pair. Each component
must be nonnull, nonempty, Unicode edge-unpadded (including NBSP), and free of ISO
controls anywhere; internal whitespace remains literal. Missing Dynamic copies
the exact global causalLinks Absent through causes, consequences and affecting.
Available empty Dynamic yields Present(empty). Known noncausal/unconnected nodes
have empty adjacency; unknown nodes fail before global availability is considered.

ReferenceTarget is closed: `LocalTarget(link, element)` returns the exact full
admitted record, `ExternalTarget(link)` never performs local lookup even for a
resident pair, and `AbsentTarget(link, absence)` preserves the complete absence.
Resolve checks full-value allLinks membership before inspecting nested input;
equivalent immutable copies, including owner.copy, are members. Every nonnull
malformed or foreign value is UnknownLink; it cannot obtain Local access by
forging coordinates, provenance or semantic fields.

| Traversal failure | Kind / fixed path / exact detail |
| --- | --- |
| Null Graph | InvalidShape / `graph` / `An admitted Causal graph is required.` |
| Invalid query pair | InvalidShape / `<API>.identity` / `The Causal query identity is invalid.` |
| Well-formed unknown/unaddressable pair | UnknownElement / `<API>.identity` / `The Causal element identity is unknown or unaddressable.` |
| Null resolve link | InvalidShape / `resolve.link` / `The Causal traversal link is invalid.` |
| Any nonnull malformed/foreign link | UnknownLink / `resolve.link` / `The Causal traversal link is not admitted by this graph.` |

These diagnostics never echo supplied IDs, keys or source paths. Build retains
the admitted Graph without rebuilding publication or inferring absent peers.
All capability gaps, full carriers and canonical peer/core facts remain available.

CT-01..12 specify the complete ten-kind/nine-field literal oracle, qualified
identity, one-sided Local adjacency, direct chains/cycles, affected occurrence
joins, missing versus empty, four absence reasons and gaps, exact provenance,
full membership, all six ID diagnostics, malformed/null boundaries and active
ScalaCheck permutations. CT-12 compares independent full links, both qualified
pair directions, carriers, context and Local/External resolutions after actual
canonical Causal read, and inspects the actual >=100 successful/0 discarded/passed
result with an info receipt. S54.6.2 selected validation passed all 239 tests in
18 suites, including all 12 CT behavior blocks. CT-12, CM-10, TM-09, TN-08 and
TF-09 each recorded 100 successes, zero discards and passed status. Fresh
independent protected-focused S2 review is PASS with no CPB, HYG or DEV items;
S2 is CLOSED at parent-verified manual native commit
`661e11b848102faf143bf5f4dc173e5ab7bcafdd` and its actual receipt.

## Fixed resources and reusable handoff

The top-down working specification
[`CmlCausalMetadataFixtureSpec`](../../src/test/scala/cozy/modeler/CmlCausalMetadataFixtureSpec.scala)
owns CF-01..08. Its actual UTF-8 inputs are
[`causal-metadata-v1-declared.json`](../../src/test/resources/cozy/modeler/causal-metadata-v1-declared.json)
and [`causal-metadata-v1-absence.json`](../../src/test/resources/cozy/modeler/causal-metadata-v1-absence.json).
Each retains the complete accepted Terminology declared prefix of fifty-five
core occurrences, seven Terms, five known peer namespaces and four opaque
namespaces. Twenty synthetic core occurrences and required peer carriers are
appended. The resulting resources have seventy-five core occurrences, seven
Terms, eight causal carriers and the additional opaque
`org.example.causal-handoff` object, including its literal JSON-null absence.
Classification has no added carriers and no new Term is declared.

Every added named element uses the exact qualified `causal.example` pair and
display name `共有`. Source attribution is supplied literal data:
authority `causal.example`, path `fixtures/causal-model.cml`, sixty-four `a`
characters as sha256 and occurrence lines 1..19. Both anonymous full core
records are exactly equal with line 19, absent stable identity and present-empty
references; their admitted Cause and Reaction payloads differ. No source file,
computed digest, source-currentness certification or ordinal identity is implied.

The declared fixture supplies buyer operations in command/confirm/command order;
placement and confirmation cause order-placed, reservation reacts to order-placed
with reserve-stock rule context, and reserve-stock causes stock-reserved.
Aggregate/Entity affecting results return the original complete carriers.
Dependency, Query/View and collaborator references retain exact Local/External
boundaries and complete relation/profile/context/source/origin values. The
resident External inventory pair remains external. Duplicate view references
retain ordered Derived owner/inventory sources. These references never assert
read-model updates, dependency execution or additional causation.

The absence fixture keeps the declared prefix. The new buyer has no stable
identity, new nonanonymous core references cycle through the four literal gaps,
and all new supplemental Presence fields cycle by projection and field ordinal.
Anonymous cores retain present-empty references and no addressability fallback.
Missing supplemental fields never erase complete raw gap details, and absence
never upgrades label, membership, expression or Workflow lists to causal evidence.

CF-01 compares full independent publication, typed families, gaps, complete wire
and canonical reconsumption. CF-02..04 ask qualified grounded questions using
actual resource JSON alone. CF-05 preserves all accepted peer facts and prefix
navigation. CF-06 fixes exact safe mutation diagnostics. CF-07 fixes full-value
membership and finite direct-cycle questions. CF-08 independently varies
catalog/Terms, peer carriers, references and recursive object keys, reconstructs
the entire expected child independently and consumes both actual modes per case.
It requires actual pass, at least one hundred successes, zero discards, both mode
counts equal successes and seven positive observed coverage categories.
Selected S3 validation passed all 247 tests in 19 suites. CF-08, CT-12, CM-10,
TM-09, TN-08 and TF-09 each recorded 100 successes, zero discards and passed
status; baseline category counts were positive. CF-08 exercised both actual
modes per case: declared and absence counts were each 100, with all seven
observed categories positive. SBT and wrapper exited 0 and the serialization
lock was released. Fresh independent lightweight S3 review is PASS with no CPB,
HYG or DEV items. S3 is CLOSED at parent-verified manual native commit
`2360d78ac1ee24e4b9e51465d41f669a861283f6` and its actual receipt, whose sole
parent is S2 `661e11b848102faf143bf5f4dc173e5ab7bcafdd`.
[Phase 54.6](../phase/phase-54.6.md) is CLOSED with all five original obligations,
all three native Steps and all four MMD outcomes accepted.
The sole independent full Phase review passed with no Current Phase Blockers,
Hygiene or Development Candidates. Ordinary `sbt --batch test` passed 2868
tests in 201 suites, zero failures/aborted suites, 9 pre-existing optional/deferred
canceled cases. SBT and wrapper exited zero; the serial lock was released.
The distinct parent manual local release binds `P546-CLOSURE-001@1`; its native
commit and closure receipt are verified after committing these final records.

The [supplier handoff](../design/cml-causal-metadata-handoff.md) records schemas,
source limits, query recipes and observed Step evidence. Phase 54.7 remains
PLANNED and owns final all-family Cozy supplier/fixture closure. External
CNCF/Textus CBD runtime/UI integration and consumer acceptance are separate
downstream responsibilities. Current production, CLI and runtime consumers are
not migrated by these resources.
