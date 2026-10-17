# CML Use Case Metadata v1 Specification

Current status: PHASE-54.4 CLOSED; all three Steps CLOSED; final evidence below supersedes prior pending records.
Owner: Cozy Use Case metadata supplier; the Phase parent owns acceptance evidence and closure.
Update rule: preserve the accepted foundation and peer contracts; update status only from actual validation, independent review and local commit evidence.

## Supplier boundary

`private[cozy] cozy.modeler.CmlUseCaseMetadata` consumes an admitted
`CmlSemanticFoundation.Catalog` and explicit caller declarations. It publishes
`extensions.cozy.cml.use-case` with `schemaVersion: cozy.cml.use-case.v1`
in the unchanged `cozy.cml.semantic-metadata.v1` envelope. The child contains
exactly `schemaVersion`, `actors` and `useCases`.

The facade exposes `build(catalog: Catalog, actors: Vector[ActorProjection],
useCases: Vector[UseCaseProjection], extensions: JsObject):
Either[Vector[UseCaseDiagnostic], Graph]`, `read(value: JsValue)` with the same
result type, `toJson(graph): JsObject` and `canonicalJson(graph): String`.
Graph exposes read-only `catalog`, `envelope`, `actors` and `useCases`; its
constructor is private to the facade with no unchecked apply/copy path.
Null Graph rendering is an internal DbC precondition. Factory and reader input
failures use typed diagnostics.

Catalog fixes complete core truth. Actor and Use Case arrays independently
preserve caller order and are full-record multisets of all Catalog records
whose kinds are respectively `actor` and `use-case`. Every occurrence needs
one projection, including equal anonymous occurrences. Element identity,
kind, name, source and core references must all match. Strict wire admission
requires complete embedded element JSON equality with canonical admitted core
JSON. Missing, excess, forged and wrong-kind occurrences fail.

## Exact immutable carriers

Both projections start with `element: ElementRecord`. Every following field is
required `Presence[T]`, in the order shown. No field defaults or inference apply.

| ActorProjection field | T |
| --- | --- |
| actorKind | String |
| summary | String |
| description | String |
| useCases | Vector[ModelReference] |
| workflows | Vector[ModelReference] |
| operations | Vector[ModelReference] |
| events | Vector[ModelReference] |
| domainElements | Vector[ModelReference] |

| UseCaseProjection field | T |
| --- | --- |
| declaredId | String |
| summary | String |
| description | String |
| goal | String |
| trigger | DeclaredExpression |
| preconditions | Vector[DeclaredExpression] |
| postconditions | Vector[DeclaredExpression] |
| priority | String |
| lifecycleStatus | String |
| actorReferences | Vector[ActorParticipation] |
| flows | Vector[UseCaseFlow] |
| domainElements | Vector[ModelReference] |
| operations | Vector[ModelReference] |
| events | Vector[ModelReference] |
| collaborators | Vector[ModelReference] |
| realizingWorkflows | Vector[ModelReference] |

`ActorParticipation(role: ActorRole, target: ModelReference)` has exactly those
two direct required fields. ActorRole is the sealed set Actor, Primary,
Secondary, Supporting, Stakeholder with case-sensitive wire values `actor`,
`primary`, `secondary`, `supporting`, `stakeholder`. Participation order and
duplicates remain authored facts. No primary uniqueness, cardinality,
reciprocal membership or inferred participation constraint applies.

`UseCaseFlow` has direct required `kind: FlowKind`, then `name`, `summary`,
`description: Presence[String]`, `condition: Presence[DeclaredExpression]`,
`steps`, `alternates`, `exceptions: Presence[Vector[DeclaredExpression]]`.
FlowKind is MainFlow, AlternateFlow, ExceptionFlow, with exact wire values
`main`, `alternate`, `exception`. Every flow Presence is explicit. Flow names,
summaries/descriptions, conditions and legacy alternate/exception text survive
literally; no additional flows are synthesized.

ModelReference and DeclaredExpression are unchanged
[Dynamic carriers](cml-dynamic-metadata.md). ModelReference is exactly
`{identity:{modelId:String,elementId:String},boundary:local|external}`;
DeclaredExpression is exactly `{text:String,references:Presence[Vector[ModelReference]]}`.
Strings may be empty, multiline, contain control characters, escaped Unicode
or directives as literal source text. Nonnull text is never trimmed, parsed,
split, normalized or executed. Actor kind, declaredId, priority and status
remain literal strings, with no enum or identity semantics.

## Qualified targets and availability

| Reference field or site | Allowed Local Catalog kinds |
| --- | --- |
| Actor.useCases | use-case |
| Actor.workflows; UseCase.realizingWorkflows | workflow |
| operations on either projection | operation, command |
| events on either projection | event |
| domainElements on either projection | entity, value, aggregate, trait, powertype, powertype-kind |
| ActorParticipation.target | actor |
| UseCase.collaborators | actor, use-case, component, service, external-system, query, view |
| trigger, pre/postconditions, flow condition/steps/alternates/exceptions expression references | any existing Catalog ModelElementId |

Local uses the exact qualified pair, checking existence and the specified kind
set. Expression references do not impose a family type claim. A TermId never
becomes a ModelElementId. External references require valid ID components and
a valid boundary, remain opaque, and never resolve locally or acquire a type
claim, even when their pair equals a Local record. Names, declaredId, Terms,
source, paths, ordinal and concatenated pairs never supply missing identity.
Unicode normalization, case folding and delimiter rewriting are forbidden.

Presence is exactly `{status:present,value:typedPayload}` or
`{status:absent,reason:not-declared|not-represented|unsupported|not-applicable,detail:String}`.
The typed reasons are NotDeclared, NotRepresented, Unsupported and NotApplicable.
Present empty strings/vectors differ from absence. ID components and absence
details must be nonnull, nonempty, free of ISO control codepoints and unpadded
at either end according to Unicode `Character.isWhitespace` or
`Character.isSpaceChar`. Internal spaces are retained literally. Anonymous core
identity remains anonymous; equal occurrence payloads retain their own order.

Null projections, elements, Presence, present payloads, vectors/items, enum
values, references, identities and expressions reject without expected-input
exceptions. Every wire child, projection, participation, flow, reference, ID,
expression and Presence object is closed; omitted, extra, mixed, null,
wrong-type, unknown enum and wrong-case values fail recursively.

## Admission and publication order

Build first admits base publication, then rejects a caller-supplied own
namespace as ExtensionConflict, validates coverage and all payloads, and adds
only its own child. Read first admits base publication, requires its own child
object, checks version before contradictory child keys, strictly decodes,
removes only the own child, then enters the same factory. A non-object extension
value fails the unchanged base publication gate; a missing own namespace fails
the Use Case gate. Other admitted extension objects remain opaque, including
false/empty/null values within them and malformed peer semantic content. This
supplier never certifies a peer reader's semantics.

Canonical publication uses the accepted base recursive object-key writer,
preserving array order and multiplicity with Play JSON escaping. It adds no
hash/signature, current-source verification, schema adapter or RFC8785 claim.

`UseCaseDiagnostic(kind, path, detail, publication)` uses these kinds:
InvalidShape, UnsupportedSchemaVersion, InvalidCoreBinding,
DuplicateProjection, MissingProjection, InvalidUseCaseSemantics,
PublicationFailure, ExtensionConflict, UnknownElement and UnknownProjection.
Only PublicationFailure retains the original typed PublicationDiagnostic.
The last two kinds describe well-formed unknown navigation queries.
Factory failures collect deterministic diagnostics; strict decoding may stop
at the first malformed schema coordinate.

| Kind / circumstance | Fixed detail |
| --- | --- |
| InvalidShape | The Use Case value does not match the required v1 shape. |
| UnsupportedSchemaVersion | The Use Case schema version is not supported. |
| InvalidCoreBinding, typed route | The Use Case projection does not bind exactly to an admitted core record. |
| InvalidCoreBinding, wire route | The embedded Use Case record does not exactly match admitted core JSON. |
| MissingProjection | An admitted Use Case or Actor record lacks its required projection occurrence. |
| DuplicateProjection | A Use Case or Actor record has excess projection occurrences. |
| InvalidUseCaseSemantics | The declared Use Case reference or absence is invalid. |
| PublicationFailure | The foundation metadata publication is invalid. |
| ExtensionConflict | The Use Case extension namespace is already supplied by the caller. |

Paths contain fixed schema fields and array indices, such as `actors[0].summary`
or `extensions.cozy.cml.use-case.useCases[0].flows.value[0].kind`; semantic checks
after decoding use factory coordinates. Version failure uses
`extensions.cozy.cml.use-case.schemaVersion`; conflict uses `extensions`.
Publication failures retain the base diagnostic path. Diagnostics never echo
hostile supplied keys, values or source paths.

## Executable supplier evidence and next handoff

[CmlUseCaseMetadataSpec](../../src/test/scala/cozy/modeler/CmlUseCaseMetadataSpec.scala)
authors UC-01 complete typed/literal core/carrier/Term equality; UC-02 exact
qualified domains and ordered roles/flows/duplicates; UC-03 every reference
site's accepted/rejected Local kinds and opaque External values; UC-04 four
absence reasons, present-empty and equal anonymous occurrences; UC-05 full
multiset/core binding and typed null boundaries; UC-06 unchanged independently
admitted predecessor peers/base-first failures; UC-07 recursive closed shapes
and version precedence; UC-08 literal text and safe strict ID/detail rules;
UC-09 active ScalaCheck with 100 successes and zero discards over independent
Catalog/carrier permutations and recursive JSON object-key reconstruction.
The repaired suite passed all 17 tests, including 100 successful permutation
cases with zero discards and both fixture modes exercised in every case.
Independent Step review and focused repair closure accepted this supplier
boundary. The original eleven-suite, 165-test validation remains retained for
unchanged predecessor contracts. S54.4.1's manual local acceptance commit
records this evidence; final Phase acceptance is recorded below.

## Immutable navigation

`private[cozy] CmlUseCaseNavigation.build(graph: Graph)` returns
`Either[Vector[UseCaseDiagnostic], Navigation]`. Navigation has a private
constructor, no apply/copy bypass, and read-only `graph` and `catalog` retaining
the original instances. It consumes admission without repeating schema or peer
validation. `actor(id: ModelElementId)` and `useCase(id: ModelElementId)` return
`Either[UseCaseDiagnostic, ActorProjection]` and
`Either[UseCaseDiagnostic, UseCaseProjection]`; `element(id: ModelElementId)`
returns `Either[UseCaseDiagnostic, ElementRecord]`. Complete core and projection
values, Presence, sources, carrier order and equal anonymous occurrences remain
unchanged. Projection indexes include only Present ModelElementId pairs.

Every query requires the exact complete qualified pair. Null ID/components,
empty components, Unicode whitespace/space-char edge padding and ISO controls
anywhere return InvalidUseCaseSemantics at `actor.identity`, `useCase.identity`
or `element.identity`, with detail
`The declared Use Case reference or absence is invalid.`
Well-formed unknown and wrong-family projection queries return UnknownProjection
at the corresponding identity coordinate with detail
`No admitted Use Case projection has the requested qualified identity.`
Unknown core queries return UnknownElement at `element.identity` with detail
`No admitted model element has the requested qualified identity.`
Null graph returns InvalidShape at `graph` with detail
`An admitted Use Case graph is required.` All navigation diagnostics have
publication=None. No trimming, normalization, name/declaredId/Term/source,
delimiter concatenation or positional alias supplies an address.

`resolve(reference: ModelReference)` returns
`Either[UseCaseDiagnostic, ReferenceTarget]`. ReferenceTarget is sealed with
value-backed `LocalTarget(element: ElementRecord)` and
`ExternalTarget(reference: ModelReference)`. Local resolution returns the full
Catalog record of any admitted kind. External preserves the exact supplied
reference, including a pair equal to Local core. Its generic internal helper
`CmlUseCaseMetadataValidation._reference_diagnostic(catalog, reference, path)`
already validates shape, ID, boundary and Local existence; family sets stay
separate. At fixed `resolve.reference`, null reference/identity yields
InvalidShape; malformed components yield InvalidUseCaseSemantics at
`resolve.reference.identity`; null/invalid boundary yields InvalidShape at
`resolve.reference.boundary`; dangling Local yields InvalidUseCaseSemantics at
`resolve.reference`. Shape detail is
`The Use Case value does not match the required v1 shape.` Semantic detail is
`The declared Use Case reference or absence is invalid.` Diagnostics never
echo supplied values. Valid missing External remains an ExternalTarget.

[CmlUseCaseNavigationSpec](../../src/test/scala/cozy/modeler/CmlUseCaseNavigationSpec.scala)
authors UN-01 complete exact lookup and same-instance retention; UN-02 declared
links, all roles, generic expression contexts and literal target facts; UN-03
opaque External contrasts; UN-04 missing/wrong-family and descriptive alias
failures; UN-05 anonymous occurrence gaps, empty values and named survivors;
UN-06 complete typed hostile-input diagnostics and safe successes; UN-07
independently generated Catalog/carrier/query permutations for declared and
anonymous fixtures in every sample through build and strict read. The property
requires 100 successes, Passed and zero discards and reports its actual result.
S54.4.2 is CLOSED on 2026-10-04. Focused validation passed 172 tests in twelve
suites; navigation and retained Use Case metadata properties each passed 100
cases with zero discards. Both process exits were zero and the serial lock was
released. Independent Step review passed with no findings. Its parent manual
local acceptance commit binds six paths, as recorded in native Git history.

S54.4.3 supplies accepted fixed resources and same-envelope cross-view consumer proof.
Consumers separately admit [Dynamic](cml-dynamic-metadata.md),
[Structure](cml-structure-metadata.md) and
[Classification](cml-classification-metadata.md) peers before following their
declared references. After independent Dynamic admission, the preserved
realizing Workflow pair can be passed to `CmlDynamicTopology.workflow`.
Navigation invents no reverse Actor membership, links, walks or peer admission.
[Foundation](cml-semantic-foundation.md) and
[publication](cml-semantic-metadata.md) contracts remain unchanged.

## Fixed supplier resource contract

The [declared resource](../../src/test/resources/cozy/modeler/use-case-metadata-v1-declared.json)
and [absence resource](../../src/test/resources/cozy/modeler/use-case-metadata-v1-absence.json)
retain all 42 complete records, three Terms and six extensions of the accepted
declared Dynamic fixture in both modes. Seven complete `usecase.example` records
are appended in order: requester Actor, auditor Actor, complete-order Use Case,
observe-order Use Case, partner component, fulfillment service and payment
external-system. Their name is `共有`, source path is
`src/main/cml/use-case-handoff.cml`, authority is `usecase.example`,
and lines are 43–49. Core references remain
Absent(NotDeclared, "No semantic references are declared."). A separate
TermId(usecase.example, use-case:complete-order) retains the complete source at
line 45. Each file totals 49 core records/four Terms/two Actors/two Use Cases.

The fourth opaque peer has producer `synthetic-caller`, declaredFlowOrder
`[main,alternate,exception]` and absence `null`. The
[supplier handoff](../design/cml-use-case-metadata-handoff.md) fixes every Actor,
Use Case, role, expression and main/alternate/exception literal, including
Present-empty observe-order. Absence changes only requester/complete-order
identity to NotRepresented with detail
`No stable Use Case or Actor identity is represented.` and replaces all eight
Actor/16 Use Case fields with exact four-reason/detail cycles indexed by
projection-plus-field ordinal. Anonymous records retain full embedded equality;
Term identity remains independent and Dynamic fulfillment stays declared.

[CmlUseCaseMetadataFixtureSpec](../../src/test/scala/cozy/modeler/CmlUseCaseMetadataFixtureSpec.scala)
authors nine UF-01–09 scenarios with independent complete typed and recursive
wire oracles, four separate peer admissions, source/core retention, full
cross-view query facts, strict failure matrices and two-mode observable
permutations. Its active property requires Passed, at least 100 successes and
zero discards while reordering all three full-value sequences in each mode.
S54.4.3 is CLOSED on 2026-10-04. Focused validation passed 181 tests in thirteen
suites; fixture, navigation and Use Case metadata properties each passed 100
generated cases with zero discards. Both resource modes run in every fixture
sample. SBT and wrapper exited zero and the serial lock was released.
Independent complete Step review passed with no blockers, Hygiene or
Development Candidates. The parent manual local acceptance commit binds ten
paths, as recorded in native Git history. Full Phase review, ordinary full
validation and distinct local release are recorded below.

This supplier adds no CML parser/projector integration, CLI/HTTP/SPI/MCP,
generated legacy sidecar, runtime, external UI/consumer acceptance, dependency
change or publication deployment. Phases 54.5–54.7 remain unstarted.

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
