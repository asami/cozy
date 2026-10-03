# CML Dynamic Metadata v1 Design

Current status: S54.3.1 CLOSED at native commit
`feb4ba862635a56fa0519ae75cef0bdd910739b2`; S54.3.2 topology ACCEPTED
after focused validation and independent Step review.
Owner: Cozy Dynamic metadata supplier.
Update rule: implement only the frozen PHASE-54.3 declared-fact boundary.
Closure basis: accepted S1 DM-01–09 with retained 122-test evidence, protected
Step review and parent M0 naming amendment. S2 passed all 132 tests in eight
focused/predecessor suites; P543-S5432-REVIEW-001 revision 1 independently
accepted DT-01–08 with zero current blockers. The parent records the manual
local Step commit in its native audit. S3 fixtures/handoff and final Phase
closure remain pending.

The [specification](../spec/cml-dynamic-metadata.md) fixes the complete additive
child. Catalog remains truth; Dynamic declarations supply explicit additional
payload. The qualified model identity domain is separate from Terms and from
source attribution. WORKFLOW lowering does not invent the IDs this API requires.

## Five cohesive modules

`CmlDynamicMetadata` owns typed carriers, sealed control/causal/diagnostic
alternatives, the immutable constructor-private Graph and build/read/render API.
It first admits the unchanged base publication, rejects an own-namespace
conflict, validates all declarations and injects exactly its child. No unchecked
construction path exists.

`CmlDynamicMetadataValidation` owns complete per-kind multiset coverage and
payload semantics. Complete-record equality binds every occurrence, including
anonymous duplicates. Reference checks admit exact qualified components,
resolve only Local pairs in the field's prescribed kind set and retain External
pairs without local claims. Required Presence is validated before payload access.
Literal purpose/expression text is independent of identity validation.

`CmlDynamicMetadataCodec` owns root/child admission, common exact-field,
Presence/reference/expression/enum codecs and causal links. It computes one
immutable admitted core JSON correspondence, then delegates three workflow and
three state-machine carriers. Reading uses the base reader first, checks child
version before shape and passes decoded values through the same validated
facade. Fixed diagnostic paths contain only schema fields and indices.

`CmlDynamicWorkflowCodec` owns only workflow/activity/flow field admission and
rendering. `CmlDynamicStateMachineCodec` owns only state-machine/state/transition
field admission and rendering. Common helpers are package-private to these
modules; there is no generic shared codec framework.

## Preserved declarations

Workflow overview and StateMachine lifecycle detail remain distinct. Branch,
merge, fork, join, start, end and action labels describe authored roles.
Expression text remains literal; separate qualified references carry admitted
links. Cause/reaction links retain their own core identities and source records.
They do not define BoK relations or synthesize missing causality.

All carrier/reference arrays retain authored order and repetition. Anonymous
facts retain multiplicity and never gain guessed lookup keys. Cycles, self-links
and independent membership lists need no inferred consistency or execution
policy. Core references remain independent facts, with no fabricated
correspondence to the child payload.

The unchanged base writer sorts object keys recursively while preserving array
order and proper Play JSON escaping. Opaque Structure, Classification and other
namespaces survive. Existing core, parser, Workflow/StateMachine ABI and
publication contracts remain untouched. Source evidence never claims currentness.

## One literal topology module

`CmlDynamicTopology` consumes the admitted immutable Graph without changing
supplier construction, wire schema or predecessor code. Its closed LinkKind
and full-record TopologyLink preserve declaration sources and endpoint Presence.
Constructor-private Topology retains the original Graph alongside Catalog-ordered
nodes and links; it exposes node, incoming/outgoing and all seven typed projection
lookups specified in the [contract](../spec/cml-dynamic-metadata.md#literal-topology-api).

The node predicate is exactly the frozen fifteen kinds. Only Flow source/target,
Transition fromState/toState and Cause/Reaction source/target become edges.
Complete-record carrier groups and per-record occurrence counters bind equal
anonymous records without dropping or sorting payload occurrences. The mutable
counters exist only during construction; observable data and indexes are
immutable. Every family index uses full present ModelElementId, independently
of node membership. Anonymous payloads remain in the Graph and vectors.

Incoming/outgoing indexes admit each present Local endpoint independently of
the opposite endpoint. External equal pairs remain literal unresolved facts,
and known nodes without Local edges have successful empty results. Direct
workflow/activity/machine/state context references and lists remain callable
through exact family lookup or Catalog.element; no membership, expression,
reachability, execution or causal-policy edge is synthesized.

The topology validates only query identity shape and the null Graph boundary;
admission remains the supplier's responsibility. Fixed InvalidShape,
UnknownNode and UnknownProjection coordinates/details have publication None
and never echo input. There is no name/Term/source/ordinal fallback, normalization
or additional publication schema. DT-01–08 author full independent expected
facts with adjacent Given/When/Then and active 100-success/zero-discard property
checks; all supplier/build/read/render/query calls are under When.

Cozy owns this admitted metadata supplier and literal topology. CNCF owns runtime
realization and Textus CBD Support owns views/navigation. S54.3.2 navigation is
accepted. S54.3.3 deterministic fixtures and handoff remain pending; Phase 54.4
is unstarted.
