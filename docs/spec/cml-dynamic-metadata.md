# CML Dynamic Metadata v1 Specification

Current status: S54.3.1 CLOSED at native commit
`feb4ba862635a56fa0519ae75cef0bdd910739b2`; S54.3.2 topology ACCEPTED
after focused validation and independent Step review.
Owner: Cozy Dynamic metadata supplier.
Update rule: change only under the PHASE-54.3 frozen contract; preserve the
accepted foundation, Structure, Classification and legacy ABI.
Closure basis: S1 retained all 122 focused/predecessor tests, protected Step
review and the parent M0 naming amendment. S2 passed all 132 tests in eight
focused/predecessor suites; P543-S5432-REVIEW-001 revision 1 independently
accepted DT-01–08 with zero current blockers. The parent records the manual
local Step commit in its native audit. S3 fixtures, handoff and full Phase
closure remain pending; Phase 54.4 is unstarted.

## Boundary and API

`private[cozy] CmlDynamicMetadata` accepts an already admitted foundation
Catalog and explicit caller declarations. Catalog is the core truth. The
additive child is `extensions.cozy.cml.dynamic`, schema
`cozy.cml.dynamic.v1`, in the unchanged `cozy.cml.semantic-metadata.v1` envelope.

`build(catalog, workflows, activities, flows, stateMachines, states,
transitions, causalLinks, extensions)` returns
`Either[Vector[DynamicDiagnostic], Graph]`. `toJson(graph): JsObject`,
`canonicalJson(graph): String` and `read(value: JsValue)` complete the API.
Graph is immutable and its constructor is private to the facade. There is no
unchecked apply or copy. Null build arguments fail as typed values; null Graph
rendering is an internal DbC precondition.

## Complete carriers

Each carrier embeds `element: ElementRecord`, retaining identity, kind, name,
source and core references exactly. The following lists are exact fields after
`element`; every listed field uses Presence except the causal kind.

| Array / core kind | Fields |
| --- | --- |
| workflows / workflow | purpose: String; activities, flows, participants, affectedElements, stateMachines, operations, events, rules, stateEffects: Vector[ModelReference] |
| activities / workflow-activity | workflow: ModelReference; activityKind: ActivityKind; participants, affectedElements, operations, events, rules, stateEffects: Vector[ModelReference] |
| flows / workflow-flow | workflow, source, target: ModelReference; guard: DeclaredExpression |
| stateMachines / state-machine | owner: ModelReference; affectedElements, states, transitions, workflows: Vector[ModelReference] |
| states / state | stateMachine: ModelReference; entryActions, exitActions: Vector[DeclaredExpression] |
| transitions / transition | stateMachine, fromState, toState: ModelReference; triggers, actions: Vector[DeclaredExpression]; guard: DeclaredExpression; activities, workflows, affectedElements, stateEffects: Vector[ModelReference] |
| causalLinks / causal-link | kind: CausalKind (required direct value); source, target, rule: ModelReference; affectedElements: Vector[ModelReference] |

All seven arrays form complete per-kind full-record multisets of the Catalog.
Missing, excess, forged and wrong-kind records fail, including equal anonymous
occurrences. Present IDs also resolve to their full record through
Catalog.element. Anonymous facts retain occurrence order and remain unaddressable.
Other kinds, Terms, RelationId/ProfileId facts and opaque peers survive unchanged.

Dynamic owns `ModelReference(identity: ModelElementId, boundary:
ReferenceBoundary)`. `DeclaredExpression(text: String, references:
Presence[Vector[ModelReference]])` retains authored text independently of qualified
reference facts. Purpose and expression text accept any nonnull string, including
empty, multiline, escaped or Unicode literals; text is never parsed or executed.
ActivityKind has Action, Branch, Merge, Fork, Join, Start, End, with exact wire
values `action`, `branch`, `merge`, `fork`, `join`, `start`, `end`. CausalKind
has Cause and Reaction, with exact wire values `cause` and `reaction`.

## Qualified references and absence

Every Local reference resolves by the exact qualified pair into its allowed
kind set. External values retain identity/boundary without local type claims,
including pairs also present locally. No name, Term, source, ordinal, path or
concatenated identity supplies a missing ID.

| Field | Allowed Local kinds |
| --- | --- |
| workflow / workflows | workflow |
| activities / flow source / flow target | workflow-activity |
| flows | workflow-flow |
| stateMachine / stateMachines | state-machine |
| states / fromState / toState | state |
| transitions | transition |
| operations | operation, command |
| events | event |
| rule / rules | rule |
| owner | entity, value, aggregate |
| affectedElements | entity, value, aggregate, trait, powertype, powertype-kind |
| participants | actor, entity, value, aggregate, component, service, external-system |
| stateEffects | state, transition, state-machine |
| expression references / causal source / causal target | operation, command, event, rule, action, workflow, workflow-activity, state-machine, state, transition |

ID components and absence details are nonempty, unpadded by Unicode
isWhitespace/isSpaceChar codepoints and ISO-control-free. Case, Unicode and
delimiters remain exact. Every Presence is required: Present(empty vector)
differs from Absent. Reasons are NotDeclared, NotRepresented, Unsupported and
NotApplicable with an explicit valid detail. Null/malformed records, presences,
vectors/items, IDs, references, expressions and enums fail as typed Left.

## Wire admission

The child has exactly schemaVersion and the seven array fields. Projections
have exactly the table's fields and element; embedded element JSON must equal
complete canonical admitted core JSON. A reference is exactly
`{identity:{modelId:String,elementId:String},boundary:local|external}`.
An expression is exactly `{text:String,references:Presence[Vector[ModelReference]]}`.
Presence is exactly `{status:present,value:typedPayload}` or
`{status:absent,reason:not-declared|not-represented|unsupported|not-applicable,detail:String}`.
All typed containers reject extra, omitted, null, mixed, wrong-type and wrong-case
values recursively, without coercion or defaults.

Build admits base publication first and rejects caller-supplied own namespace
as ExtensionConflict. Read admits the base first, requires its child object,
checks child version before other child fields, strictly decodes and enters the
same factory with only its own namespace removed. Unsupported versions fail
closed. Opaque peers are retained. One immutable core-record/JSON correspondence
is computed per operation. Rendering uses the base writer's recursive object-key
sorting, authored array order and Play JSON escaping; it is not RFC8785.

DynamicDiagnostic kinds are InvalidShape, UnsupportedSchemaVersion,
InvalidCoreBinding, DuplicateProjection, MissingProjection,
InvalidDynamicSemantics, PublicationFailure, ExtensionConflict, UnknownNode and
UnknownProjection. Only PublicationFailure retains the original typed
PublicationDiagnostic. Paths/details use safe fixed schema coordinates and
never echo hostile values, keys or filesystem paths. Topology uses UnknownNode
and UnknownProjection for well-formed unaddressable query identities.

## Literal topology API

`private[cozy] CmlDynamicTopology.build(graph: CmlDynamicMetadata.Graph)` returns
`Either[Vector[DynamicDiagnostic], Topology]`. The immutable Topology constructor
is private to CmlDynamicTopology and exposes the original `graph`,
`nodes: Vector[ElementRecord]` and `links: Vector[TopologyLink]`.
TopologyLink contains the complete `element: ElementRecord`, `kind: LinkKind`,
`source: Presence[ModelReference]` and `target: Presence[ModelReference]`.
LinkKind is the closed set Flow, Transition, Cause and Reaction.

The exact fifteen node kinds are entity, value, aggregate, trait, powertype,
powertype-kind, workflow, workflow-activity, state-machine, state, operation,
command, event, rule and action. Nodes retain Catalog declaration order and
complete core facts. Workflow-flow, transition, causal-link and every other
core kind are excluded from nodes.

Flow links use FlowProjection.source/target; Transition links use
TransitionProjection.fromState/toState. CausalLinkProjection.source/target
produce Cause or Reaction according to their declared kind. Links follow
Catalog declaration order across all three carrier families. Equal complete
anonymous ElementRecord occurrences consume one carrier occurrence each, in
that family's authored carrier order. No grouping sort, deduplication or
synthetic identity changes order or multiplicity. Original Graph carrier arrays
remain independently preserved.

`node(id: ModelElementId): Either[DynamicDiagnostic, ElementRecord]`,
`outgoing(id)` and `incoming(id): Either[DynamicDiagnostic, Vector[TopologyLink]]`
use the full present qualified pair. Incoming/outgoing first validate node(id).
Only present Local endpoints populate their respective index, including partial
links with an absent or External opposite endpoint. External equal qualified
pairs do not index. The complete opposite Presence remains in the returned
link. Known nodes with no relevant Local endpoint return Right(Vector.empty).

Each typed lookup takes ModelElementId and returns
`Either[DynamicDiagnostic, <family>Projection]`: `workflow`, `activity`, `flow`,
`stateMachine`, `state`, `transition` and `causalLink`. Present projection IDs
are family-addressable even when their records are excluded from nodes.
Consumers follow retained direct context references through these APIs or
Catalog.element. Context lists, expression references, state effects and
membership declarations produce no additional edges.

Anonymous nodes and projections remain observable through nodes, links and the
original Graph, with no addressability through names, Terms, source, path,
ordinal or manufactured identity. Query IDs must have nonnull, nonempty
components without Unicode edge whitespace or ISO controls; case, Unicode and
delimiters are never normalized.

| Condition | Kind / path | Fixed detail |
| --- | --- | --- |
| Null graph | InvalidShape / graph | An admitted Dynamic graph is required. |
| Malformed node/incoming/outgoing ID | InvalidShape / node.identity | The Dynamic query identity is invalid. |
| Unknown or non-node ID | UnknownNode / node.identity | The Dynamic node identity is unknown or unaddressable. |
| Malformed typed lookup ID | InvalidShape / queryName.identity | The Dynamic query identity is invalid. |
| Unknown or wrong-family typed lookup ID | UnknownProjection / queryName.identity | The Dynamic projection identity is unknown or unaddressable. |

Every topology diagnostic has publication None, safe fixed coordinates and
exact punctuation without echoing input. No Graph, JSON, core, codec, ABI,
parser, consumer or runtime contract is changed.

## Literal semantics and evidence

Repeated references, array order, duplicate endpoints, cycles, self-links and
independently authored links remain exact. No inferred causality, reciprocal
membership, reachability, default state, actor, branch policy, trigger/action
evaluation or runtime enforcement is introduced. Core SemanticReference facts
are independent of projection payload. Source attribution identifies admitted
declaration authority without filesystem currentness.

[CmlDynamicMetadataSpec](../../src/test/scala/cozy/modeler/CmlDynamicMetadataSpec.scala)
authors DM-01 distinct carriers/core/peer facts, DM-02 qualified and anonymous
identity, DM-03 all-seven coverage, DM-04 every reference field's Local kind
sets, DM-05 absence/null/literal behavior, DM-06 control roles and literal causal
facts, DM-07 base-first publication/version precedence, DM-08 recursive strict
shapes and safe diagnostics, and DM-09 active 100-success/zero-discard properties
with independent Catalog/carrier permutations and expected facts.

[CmlDynamicTopologySpec](../../src/test/scala/cozy/modeler/CmlDynamicTopologySpec.scala)
authors DT-01 complete fifteen-node/four-link Catalog ordering; DT-02 seven
qualified family lookups and anonymous/wrong-family rejection; DT-03 Local-only
partial endpoint indexing and direct context preservation; DT-04 literal
duplicates, self-links, cycles and cause/reaction declarations; DT-05 equal
anonymous occurrence payload order and multiplicity; DT-06 exact null/malformed,
unknown, empty and isolated diagnostics; DT-07 full supplier round trips with
admitted predecessors and opaque peers; and DT-08 active ScalaCheck with at
least 100 successes and zero discards over independent Catalog/carrier
permutations, qualified identities, boundaries and Presence. Oracles are
independently authored complete typed facts; interpretation is under When.

Existing WORKFLOW and Composite StateMachine IR remain partial sources. This
boundary adds no parser integration, projector, CLI, writer, automatic export,
runtime, external consumer acceptance or successor work.
