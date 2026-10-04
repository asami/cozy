# CML Use Case Metadata v1 Design

Current status: PHASE-54.4 IN_PROGRESS; S54.4.1 / S54.4.1A CLOSED. Navigation and fixed-resource handoff remain planned.
Owner: Cozy Use Case metadata supplier; the Phase parent owns validation, review and closure.
Update rule: preserve the frozen qualified identity and peer contracts; record acceptance only from actual evidence and keep later consumer work with its owner.

The [specification](../spec/cml-use-case-metadata.md) fixes one additive serialized
child over the accepted [foundation](cml-semantic-foundation.md) and
[publication](cml-semantic-metadata.md). Catalog is complete core truth.
Caller-declared Actor and Use Case payloads provide additional literal facts
with retained source attribution. The metadata boundary makes no claim that a
synthetic source path/digest is a real or current filesystem CML declaration.

## Immutable responsibility split

`CmlUseCaseMetadata` owns immutable ActorProjection, UseCaseProjection,
ActorParticipation and UseCaseFlow values, sealed role/flow/diagnostic kinds,
the facade-private Graph constructor and build/read/render API. Its only child
namespace is `cozy.cml.use-case`. Base admission precedes conflict and payload
checks; only fully admitted declarations can reach the renderer. The Graph
retains independent Catalog and carrier order without an unchecked copy path.

`CmlUseCaseMetadataValidation` owns the complete per-kind full-record multiset
gate and required Presence/payload validation. Binding by the full record
prevents forged kind, name, source or core references from passing an ID-only
check. Counting each equal anonymous record retains complete coverage and
separate payload occurrence order without inventing identity. Catalog's
qualified element index supplies Local existence; each family separately
enforces its frozen kind set. Generic expression references admit any existing
model record. External remains an exact opaque value even when its pair equals
a Local record. All expected null/shape failures are typed before payload use.

`CmlUseCaseMetadataCodec` owns outer/child admission and strict common primitives.
It creates one immutable complete core JSON correspondence per operation,
checks the child version first, closes every container and decodes without
defaults. The receiver removes only its own namespace and reuses the same
validated factory. The base gate remains responsible for publication failures;
the original typed PublicationDiagnostic is retained only by that diagnostic
kind. Fixed schema paths/details never incorporate hostile keys or text.

`CmlUseCaseProjectionCodec` owns the exact Actor and Use Case fields and their
rendering. `CmlUseCaseFlowCodec` owns direct role/kind enums, participations and
flow fields. Shared helpers are private to the modeler package; this split adds
no dependency or generic codec framework. ModelReference and DeclaredExpression
reuse unchanged [Dynamic declarations](cml-dynamic-metadata.md); their existing
validator/codecs keep their existing consumer contract.

## Declared text, identity and compatibility

Business Actors are caller declarations, never inferred from runtime,
implementation or same-named elements. declaredId, Actor kind, labels, Terms
and source are descriptive facts, independent of qualified ModelElementId.
Full Unicode/case/delimiter distinctions remain exact. Gaps are explicit at
each required field; present-empty content is an authored value. Anonymous
records remain observable but unaddressable by descriptive aliases.

Role and flow order, repeated participation, duplicate steps and explicit
references remain literal. No primary uniqueness, reciprocity, include/extend,
goto/end execution, directive parsing, scenario composition or inferred
workflow relationship is imposed. Condition/step/alternate/exception text and
separate reference Presence are preserved independently. Source authority
supplies evidence of declaration without a currentness protocol.

The base writer recursively orders object keys and preserves arrays. Existing
Foundation, Structure, Classification and Dynamic children/core/Terms remain
exact; admitted opaque peer objects retain false/empty/null values and receive
no automatic semantic validation. Each consumer independently admits the peer
it wants to traverse. The existing `CmlModelMetadata` legacy sidecar and parser,
build/dependency/version, CLI and runtime contracts are unchanged.

## Forthcoming consumer handoff

S54.4.2 will add immutable navigation over this Graph: `actor(id)` and
`useCase(id)` return full projections, `element(id)` returns a complete core
record, and `resolve(reference)` returns closed LocalTarget(element) or
ExternalTarget(reference). Exact query identity is required; anonymous,
unknown, case/authority/delimiter/Term/name/source mismatches never gain a
fallback. Null/malformed input and well-formed unknown queries remain separate
typed failures. The already-authored package helper `_reference_diagnostic`
validates generic reference shape/ID/boundary/Local existence without imposing
family kinds. No query or navigation module is implemented in S54.4.1.

S54.4.3 will freeze declared and explicit-absence UTF-8 resources and prove
Actor → UseCase → Workflow → declared Dynamic activity/state/operation/event,
plus static Entity/Aggregate resolution in independently admitted peers of the
same envelope. External and absent values stop literal Local traversal. This
uses the accepted [Dynamic handoff](cml-dynamic-metadata-handoff.md),
[Structure handoff](cml-structure-metadata-handoff.md) and
[Classification handoff](cml-classification-metadata-handoff.md). Resource and
navigation Steps remain planned.

Cozy owns the metadata supplier, CNCF owns runtime realization, and Textus CBD
Support owns its views and external acceptance. No cross-repository producer
or generated output changes. Terminology/BoK, Event Storming and final fixture
acceptance in Phases 54.5–54.7 remain unstarted. Executable UC-01–09 supplier
specifications are authored with adjacent Given/When/Then and independent
typed/literal oracles. The repaired suite passed all 17 tests and 100 generated
cases with zero discards, with declared and anonymous-gap permutations checked
in every case. Independent Step review and focused repair closure accepted the
supplier boundary, and its manual local acceptance commit records that
evidence. The original eleven-suite validation remains retained for unchanged
predecessor contracts. Full Phase review and release acceptance remain pending.
