# CML Dynamic Metadata v1 Specification

Current status: S54.3.1 IN_PROGRESS; authored boundary awaits parent validation,
independent acceptance review and manual Step commit.
Owner: Cozy Dynamic metadata supplier.
Update rule: change only under the PHASE-54.3 frozen contract; preserve the
accepted foundation, Structure, Classification and legacy ABI.
Closure basis: DM-01–09 executable behavior is authored; no validation, review
or commit receipt is claimed here. Topology, fixtures and handoff remain later Steps.

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
never echo hostile values, keys or filesystem paths. Unknown query kinds are
reserved for the later topology Step.

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

Existing WORKFLOW and Composite StateMachine IR remain partial sources. This
boundary adds no parser integration, projector, CLI, writer, automatic export,
runtime, external consumer acceptance or successor work.
