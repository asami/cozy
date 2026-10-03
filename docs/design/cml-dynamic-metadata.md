# CML Dynamic Metadata v1 Design

Current status: S54.3.1 IN_PROGRESS; parent acceptance remains pending.
Owner: Cozy Dynamic metadata supplier.
Update rule: implement only the frozen PHASE-54.3 declared-fact boundary.
Closure basis: authored DM-01–09; validation, independent review and manual
commit belong to the parent. Topology and deterministic fixtures remain later Steps.

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

Cozy owns this admitted metadata supplier. CNCF owns runtime realization and
Textus CBD Support owns views/navigation. Later S54.3.2 and S54.3.3 own topology,
fixtures and handoff; Phase 54.4 remains unstarted.
