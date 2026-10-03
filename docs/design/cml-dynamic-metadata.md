# CML Dynamic Metadata v1 Design

Current status: PHASE-54.3 CLOSED; all three Steps CLOSED.
Owner: Cozy Dynamic metadata supplier; Phase 54.4 owns later Use Case/Actor adoption.
Update rule: preserve the accepted foundation, Structure, Classification, Dynamic v1 and legacy ABI; use only declared facts and explicit gaps.

Closure basis:
S54.3.1, S54.3.2 and S54.3.3 are CLOSED at native parent manual commits
`feb4ba862635a56fa0519ae75cef0bdd910739b2`,
`4a57f37244be8f0bb5238bb92bb951ca615c8930` and
`2ef722dda75eaadf07b224b4e9548fc367025b06`.
The sole independent full Phase review P543-FULL-REVIEW-001 revision 1 is PASS
with zero current blockers. Original typed review history remains in
P543-REVIEW-LEDGER-001 revision 6; Phase repair cycles consumed: zero.
Ordinary final `sbt --batch test` passed 2769 tests across 192 suites; failures and aborted suites were zero. The 9 pre-existing canceled cases retain their documented optional/deferred scope. SBT/wrapper exit 0 and `lock=released` were verified under P543-FINAL-FULL-VALIDATION-ADMISSION-001 revision 1.
The distinct parent manual local release binds P543-CLOSURE-001 revision 1;
its actual Git revision and committed-tree/clean-tree audit are recorded after
successful commit in the private closure receipt. This committed closure
projection becomes authoritative only on that verified release.
HYG-P543-S5431-001 is persisted once in its
[canonical follow-up](../journal/2026/10/2026-10-04-phase-54.3-hygiene-follow-up.md);
it remains separate legacy projector maintenance. No Development Candidate
exists and no empty candidate journal is created. Phase 54.4 remains PLANNED
and unstarted; runtime and external consumer adoption retain their owners.

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
accepted and CLOSED. S54.3.3 deterministic fixtures and handoff are ACCEPTED
after focused validation and independent Step review. Phase 54.4 is unstarted.

## Fixed fixture and successor handoff boundary

The [supplier handoff](cml-dynamic-metadata-handoff.md) binds the exact internal
`private[cozy]` Catalog, Dynamic build/read/render and seven-family navigation APIs
to two fixed UTF-8 declared/absence resources. Each retains the accepted
Classification 22-record/two-Term prefix, complete predecessor children and
opaque peers, then adds twenty independently authored Dynamic records, one
separate Term and seventeen carriers. Synthetic caller admission supplies
source authority/path/digest/lines; fixture evidence claims neither a real source
file nor parser/currentness evidence.

The cohesive FixtureSpec authors DF-01–09 against actual bytes, independent
complete typed expected facts and a prefix-only read oracle. Its full wire,
source, absence, 23-node/eight-link topology and seven-family query expectations
remain independent of supplier/query output. Recursive negative cases retain
accepted exact safe diagnostics. Active ScalaCheck requests at least 100
successes and zero discards; every plan exercises both modes, independently
permutes 42 Catalog records and seven carrier vectors, reconstructs recursive
object keys and verifies complete facts and Catalog order. Every interpreting
prerequisite occurs under When.

Declared activity control roles, Transition expression/reference facts and
Cause/Reaction edges stay literal. Context/membership/state effects produce no
new edges; absence mode retains anonymous Workflow and its independent Term
without fallback identity. All three opaque peers survive intact.

The handoff records the exact runnable nine-suite focused recipe: all 141 tests
passed, and P543-S5433-REVIEW-001 revision 1 accepted the Step with zero blockers.
The parent records the manual commit in its native audit. Phase 54.4
consumes explicit declarations and gaps and remains unstarted. Phase 54.6 causal
traversal follows admitted edges only. Cozy supplier, CNCF runtime and Textus
CBD Support view/acceptance responsibilities remain distinct. Full Phase
review/test/release/closure remains pending.
