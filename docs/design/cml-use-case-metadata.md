# CML Use Case Metadata v1 Design

Current status: PHASE-54.4 CLOSED; all three Steps CLOSED; final evidence below supersedes prior pending records.
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

## Immutable navigation and consumer handoff

`CmlUseCaseNavigation.build(graph)` returns typed Navigation over an admitted
Graph without repeating supplier or peer admission. Its private constructor
has no public apply/copy path. Read-only graph/catalog retain the original
instances. Immutable projection indexes contain only Present ModelElementId
pairs; anonymous complete occurrences remain in the original carrier vectors.
`actor(id)` and
`useCase(id)` return full projections, `element(id)` returns a complete core
record, and `resolve(reference)` returns closed LocalTarget(element) or
ExternalTarget(reference). Exact query identity is required; anonymous,
unknown, case/authority/delimiter/Term/name/source mismatches never gain a
fallback. Null/malformed input and well-formed unknown queries remain separate
typed failures. The already-authored package helper `_reference_diagnostic`
validates generic reference shape/ID/boundary/Local existence without imposing
family kinds. LocalTarget carries the full Catalog record; ExternalTarget
retains the exact supplied reference even when its pair also exists locally.
ReferenceTarget is sealed with these two final value-backed cases.

Null graph uses InvalidShape at `graph` and detail
`An admitted Use Case graph is required.` Malformed queries use
InvalidUseCaseSemantics at `actor.identity`, `useCase.identity` or
`element.identity`, with detail
`The declared Use Case reference or absence is invalid.` Components are
nonnull/nonempty, Unicode whitespace/space-char unpadded and ISO-control free;
valid strings remain exact. Unknown and wrong-family projection queries use
UnknownProjection at the corresponding identity path and detail
`No admitted Use Case projection has the requested qualified identity.`
Unknown core uses UnknownElement at `element.identity` with detail
`No admitted model element has the requested qualified identity.`

Resolve returns the accepted helper's exact diagnostic: null reference/identity
at `resolve.reference` and null/invalid boundary at
`resolve.reference.boundary` use InvalidShape with detail
`The Use Case value does not match the required v1 shape.` Invalid components
at `resolve.reference.identity` and dangling Local at `resolve.reference`
use InvalidUseCaseSemantics with the fixed semantic detail above. Every
navigation diagnostic has publication=None and no supplied-value echoes.

[CmlUseCaseNavigationSpec](../../src/test/scala/cozy/modeler/CmlUseCaseNavigationSpec.scala)
authors UN-01–07 with independent complete facts, exact failures and generated
independent observable Catalog/carrier/query order changes in both declared
and equal-anonymous fixtures per sample. Build and strict-read routes preserve
complete authored values. Its 100-success/no-discard property reports the
actual ScalaCheck result. S54.4.2 is CLOSED on 2026-10-04 after focused
validation passed 172 tests in twelve suites and both navigation and retained
Use Case metadata properties passed 100 cases with zero discards. SBT and
wrapper exited zero with the serial lock released. Independent Step review
passed with no findings. Its parent manual local acceptance commit binds six
paths, as recorded in native Git history.

S54.4.3 authors declared and explicit-absence UTF-8 resources and the proof of
Actor → UseCase → Workflow → declared Dynamic activity/state/operation/event,
plus static Entity/Aggregate resolution in independently admitted peers of the
same envelope. External and absent values stop literal Local traversal. This
uses the accepted [Dynamic handoff](cml-dynamic-metadata-handoff.md),
[Structure handoff](cml-structure-metadata-handoff.md) and
[Classification handoff](cml-classification-metadata-handoff.md). Resource and
handoff Step is accepted by focused validation and independent review. Consumers independently admit Dynamic and pass
the preserved Workflow pair to `CmlDynamicTopology.workflow`; Structure and
Classification admission remain independent too. Navigation creates no links,
reverse Actor membership, graph walks, peer admission or external resolution.

## Fixed resource consumption

The [supplier handoff](cml-use-case-metadata-handoff.md) records exact literals,
identity/source authority, absence cycles and the reproducible consumer recipe.
[Declared](../../src/test/resources/cozy/modeler/use-case-metadata-v1-declared.json)
and [absence](../../src/test/resources/cozy/modeler/use-case-metadata-v1-absence.json)
resources retain the complete declared Dynamic prefix in both modes. Seven
new core records and a separate Term produce 49 core/four Terms, with two
Actors/two Use Cases and four opaque handoff peers. All new source attribution
is synthetic: usecase.example, source lines 43–49, the literal 64-character
lowercase c digest and shared descriptive name 共有 do not establish identity.
The separate Term at line 45 never becomes the same-component model identity.

The declared requester reaches complete-order, its Local fulfillment Workflow,
complete Dynamic carriers and independently admitted Structure Entity/Aggregate.
Roles and main/alternate/exception expression references retain their ordered
full targets; External workflow/payment pairs stop as opaque values. Explicit
Present-empty observe-order remains distinct from absence. In absence mode only
requester/complete-order identities become anonymous, every Actor/Use Case field
uses the fixed four-reason/detail cycle, and the declared Dynamic Workflow stays
addressable. No identity rescue or inferred outbound/reverse link is introduced.

The [UF-01–09 fixture specification](../../src/test/scala/cozy/modeler/CmlUseCaseMetadataFixtureSpec.scala)
independently authors full typed S3 facts and recursive wire constructors. It
uses the unchanged accepted Dynamic prefix as the predecessor oracle, admits
each peer from the same whole envelope and compares complete consumer values.
Build/read/render/canonical reconsumption preserve caller core/carrier order;
generated plans observably reorder Catalog/Actors/Use Cases separately in both
modes without changing Terms or opaque arrays. Complete Dynamic nodes/links
derive from the permuted independent Catalog and unchanged accepted endpoints.
S54.4.3 is CLOSED on 2026-10-04. Focused validation passed 181 tests in thirteen
suites; fixture, navigation and Use Case metadata properties each passed 100
generated cases with zero discards, with both resource modes in every fixture
sample. SBT and wrapper exited zero and the serial lock was released.
Independent complete Step review passed with no blockers, Hygiene or
Development Candidates. The parent manual local acceptance commit binds ten
paths, as recorded in native Git history. Full Phase review, ordinary full
validation and distinct local release are recorded below; external product
acceptance stays with its owner.

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
predecessor contracts. Final Phase review and release acceptance are recorded below.

## Final Phase acceptance and release — 2026-10-04

S54.4.1, S54.4.2 and S54.4.3 are CLOSED at parent manual local commits
`fcfa28582e66b948a9fe5be9f20558766e98efe4`,
`2ea61373c5e3754124e3d17a6d03b94b01a0c135` and
`8d204fdda0d3469a57a0ccf30c5c90e6475d99fb`.
The sole independent full Phase review P544-FULL-REVIEW-001 revision 1 is PASS
with zero current blockers, Hygiene or Development Candidates. Original review
history remains in P544-REVIEW-LEDGER-001 revision 6; Phase repair cycles: zero.
Ordinary final `sbt --batch test` passed 2802 tests across 195 suites,
with zero failures or aborted suites. The 9 pre-existing canceled cases
are four optional Remotion integration cases and five Phase 51 deferred cases;
their acceptance is not claimed. All Use Case scenarios passed. SBT/wrapper
exit 0 and `lock=released` are verified in
P544-FINAL-FULL-VALIDATION-ADMISSION-001 revision 1.
The distinct parent manual local release binds P544-CLOSURE-001 revision 1.
Its actual Git revision and committed-tree/clean-tree audit are recorded after
successful commit in the private closure receipt. This closure projection is
authoritative only on that verified release. No empty follow-up journal is
created. Phases 54.5–54.7 remain PLANNED and unstarted; CNCF runtime realization
and Textus CBD Support external views/navigation/acceptance retain their owners.
