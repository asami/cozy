# Logical UI Generated Producer v1

Document role: normative Phase 76 producer behavior contract, transcribed from
parent-decided contract `phase76-cycle-plan-001` revision 1 for P76-01A.
Status: documentary contract independently reviewed PASS and accepted for
implementation. P76-01 documentary obligations are accepted at local Step commit
`0224d56cd5a1a94ab110b7e776e6922264eb7950`. P76-02 pure-producer executable
verification and fresh independent Step review are accepted; its
[pure producer specification](../../src/test/scala/cozy/modeler/LogicalUiProducerSpec.scala)
and [ordinary CML specification](../../src/test/scala/cozy/modeler/LogicalUiCmlSpec.scala)
passed parent focused validation. The local
P76-02 Step acceptance commit is
`d9e106ca7d154402487274e189063f4276d0d94a`. P76-03A source generation and
ordinary in-process wiring are accepted after 322/322 focused tests across
nine suites, including three ScalaCheck properties with at least 100 successful
cases, and independent Step review `phase96-cozy76-p76-03a-step-review-001`
r1 PASS by `/root/phase96_cozy76_p76_03a_step_review_001`, attributed in
original CNCF primary ledger `phase96-review-ledger-001` r14. The local
P76-03 Step acceptance commit is
`8a4c1794b7ad4557af5b4260d1233acd07520c6e`. P76-04A's
[generated handoff journal](../journal/2026/10/2026-10-04-phase-76-generated-handoff.md)
records actual twice-generated CLI LUI-GEN-01 delivery and 254/254 focused
producer/runtime tests across six suites, with SBT/wrapper exit 0 and shared
lock released. P76-04 producer generation handoff is accepted under
`phase96-cozy76-p76-04-acceptance-handoff-001` r1. Independent Step review
`phase96-cozy76-p76-04a-step-review-001` r1 by
`/root/phase96_cozy76_p76_04a_step_review_001` retains original FINDINGS;
its sole `CPB-COZY76-P7604-PRODUCER-COORDINATE-001` is resolved by accepted
parent M0 amendment `phase96-cozy76-p76-04a-coordinate-m0-amendment-001` r1,
recorded in original CNCF ledger `phase96-review-ledger-001` r17. The local
P76-04 Step commit is PREPARED/PENDING, not executed. Same-generated Scala 3
consumer compilation/factory/bootstrap/Display LUI-ACCEPT-01 and whole-Phase
verification/release remain pending.

## Authority and supported boundary

The [Phase 74 runtime contract](logical-ui-runtime-contract.md), its
[design](../design/logical-ui-runtime-contract.md),
[CozyLogicalUiRuntime](../../src/main/scala/cozy/ui/CozyLogicalUiRuntime.scala)
and [shared vocabulary](../../src/main/scala/cozy/ui/CozyLogicalUiVocabulary.scala)
remain semantic authority. The fixed consumer surface is CNCF's
[Logical UI generated ABI specification](../../../cloud-native-component-framework-phase96/docs/spec/logical-ui-generated-abi.md)
and [LUI-ABI-01 source handoff](../../../cloud-native-component-framework-phase96/docs/journal/2026/10/2026-10-04-phase-96-logical-ui-abi-handoff.md).
The [producer design](../design/logical-ui-generated-producer.md) fixes layering
and responsibilities. Source coordinates and historical proof belong in the
[producer contract journal](../journal/2026/10/2026-10-04-phase-76-producer-contract.md).

Cozy is a Scala 2.12 compiler/tool emitting public CNCF Scala 3 source. It must
not introduce a second semantic authority, import a Cozy runtime type in output,
copy ABI type definitions, cross-build a shared runtime library, or generate
runtime Display instances. Only static List/Detail definitions are supported.
Date, choice, reference, structured and image kinds, editable/constraint maps,
Entity schemas and mutation targets are outside generated v1. Runtime resource
values/revisions, authorization, capability decisions and Operation execution
remain application/CNCF-owned. Bindings are explicit registration keys, never
property expressions, guessed setters, routes or inferred inverse writes.

## LUI76-S01: Identity, descriptor and public constructor shape

The target package is `org.goldenport.cncf.display`; ABI identity is exactly
`cncf.logical-ui-generated.v1` and ABI integer version is `1`. All constructors
below are public members of `GeneratedLogicalUiAbi`, with these exact names,
types and order. This is shape documentation, not an executable artifact:

```text
Definition(abiIdentity: String, abiVersion: Int,
  semanticContract: SemanticContract, provenance: SourceProvenance,
  screenId: String, definitionRevision: String, viewBindingId: String,
  screen: ScreenDefinition)
sealed abstract class ScreenDefinition
ListDefinition(title: String, itemFields: Vector[FieldDefinition],
  detailTarget: DetailTarget, actions: Vector[ActionDefinition],
  itemActions: Vector[ActionDefinition])
DetailDefinition(title: String, sections: Vector[SectionDefinition],
  actions: Vector[ActionDefinition])
DetailTarget(screenId: String)
SectionDefinition(id: String, title: Option[String], fields: Vector[FieldDefinition])
FieldDefinition(id: String, label: String, role: PresentationRole, value: ValueDefinition)
ValueDefinition(bindingId: String, allowedKinds: Vector[ValueKind])
ActionDefinition(id: String, label: String, bindingId: String)
SemanticContract(schema: String, version: Int, semanticAuthoritySchema: String,
  assurance: String, driver: DriverReference)
DriverReference(repository: String, revision: String, path: String, scenarioId: String)
SourceProvenance(producerId: String, sourceId: String, revision: String)
```

Copy the complete `CozyLogicalUiRuntime.minimumContract` into the constructor
fields of `SemanticContract` and `DriverReference`, structurally and exactly:

| Member | Required value |
| --- | --- |
| schema | `cozy.logical-ui-runtime.v1` |
| version | `1` |
| semanticAuthoritySchema | `cozy.logical-ui-semantics.v1` |
| assurance | `provisional-scenario` |
| driver.repository | `KnowledgeHubProject/nict-editing-studio-app` |
| driver.revision | `48eb43b8eb839137e17ef4c944b3db4a73d0cec3` |
| driver.path | `docs/phase/phase-2.md` |
| driver.scenarioId | `resource-list-detail` |

The ABI version, historical semantic driver descriptor, `definitionRevision`,
declared `SourceProvenance.revision`, receipt producer Git revision, runtime
resource revision and Display wire revision are distinct. No clock, resource
ID or proof revision supplies declared provenance implicitly. CML cannot
override ABI identity/version or the semantic descriptor in this producer.
Future vocabulary/ABI changes require explicit consumer contract admission.

## LUI76-S02: Static representation and semantic mapping

The internal producer model is immutable Scala 2.12 static definitions, using
the shared Phase 74 `PresentationRole` values and a closed static value-kind
counterpart. It is not a collection of exemplar `ResourceList`/`ResourceDetail`
instances. Pure consumer-shape projection is separate from source rendering.

| Phase 74 concept | Generated definition mapping and exclusions |
| --- | --- |
| ResourceList | ListDefinition; BrowsePurpose/CollectionDisplay are fixed semantics, without extra ABI fields |
| ListItem | Ordered itemFields/itemActions template; resource item identity and eager values omitted |
| ResourceDetail | DetailDefinition; InspectPurpose/DetailDisplay fixed; runtime resourceId omitted |
| Section | SectionDefinition preserving id, optional title and field order |
| Field | FieldDefinition preserving id, label and role; explicit bindingId/ordered allowedKinds replace runtime value |
| Action | ActionDefinition id/label/application bindingId; CommandPattern implicit; enabled/auth/capability/invocation omitted |
| DetailTarget | Same-component Detail screenId; no URL, navigation mode or runtime region payload |
| Metadata | Complete fixed semantic descriptor plus separately declared SourceProvenance |

All emitted alternatives use the exact CNCF companion-qualified names under
`GeneratedLogicalUiAbi`. CML tags are case-sensitive:

| Phase 74 shared role | CML tag | CNCF role |
| --- | --- | --- |
| ContentRole | `content` | PresentationRole.Content |
| TitleRole | `title` | PresentationRole.Title |
| SubtitleRole | `subtitle` | PresentationRole.Subtitle |
| DescriptionRole | `description` | PresentationRole.Description |
| StatusRole | `status` | PresentationRole.Status |

| Phase 74 value alternative | CML kind tag | CNCF static kind |
| --- | --- | --- |
| TextValue | `text` | ValueKind.Text |
| NumberValue | `number` | ValueKind.Number |
| BooleanValue | `boolean` | ValueKind.Boolean |
| TimestampValue | `timestamp` | ValueKind.Timestamp |
| MissingValue | `missing` | ValueKind.Missing |

`allowedKinds` is nonempty, supported, nonnull, duplicate-free and ordered.
Content admits any nonempty unique supported subset. Every noncontent role
requires exactly `Vector(ValueKind.Text)`. Runtime title-role text must be
nonblank under Phase 74; a static definition has no eager title-field value to
validate. Never infer a binding or allowed kind from a sample runtime value.

## LUI76-S03: Ordinary CML grammar and component ownership

Use the ordinary Kaleidox parsed `LogicalSection` AST through Modeler. Retain
`# LOGICAL-UI` as a `DocumentDivision`. Do not create an independent executable,
raw-file replacement parser, JSON runtime bridge or consumer-side CML parser.
One explicit component has one LOGICAL-UI root and one selected definition
vector. A present root must supply definitions; absence preserves old behavior.

The root's required prologue keys are `component`, `producerId`, `sourceId`,
`sourceRevision`. `component` must exactly name an existing COMPONENT
declaration. Resolve its existing declared package through the source component
model. Missing/ambiguous ownership or invalid emitted namespace/stem is a
structured component error. Never choose the first component implicitly or
infer ownership from screen ID or View key.

Ordered immediate `## LIST` / `## DETAIL` sections require `screenId`,
`definitionRevision`, `viewBindingId`, `title`. LIST additionally requires
`detailTarget`, a logical screen ID. The only containers are:

| Screen/scope | Optional container | Immediate children |
| --- | --- | --- |
| LIST | `### ITEM-FIELDS` | `#### FIELD` |
| LIST | `### ACTIONS` | `#### ACTION` |
| LIST | `### ITEM-ACTIONS` | `#### ACTION` |
| DETAIL | `### SECTIONS` | `#### SECTION` with required id and optional title |
| SECTION | `##### FIELDS` | `###### FIELD` |
| DETAIL | `### ACTIONS` | `#### ACTION` |

At most one of each container exists in a scope; absent containers mean empty
vectors. FIELD requires `id`, `label`, `role`, `bindingId`, `allowedKinds`.
ACTION requires `id`, `label`, `bindingId`. A SECTION without title maps to
`None`; an explicit title maps to `Some(title)` and must be nonblank.

Properties occur only in the direct section prologue, one `key = literal` per
nonblank line, with exact camelCase keys. Strings are JSON-compatible
double-quoted literals; allowedKinds is a JSON-compatible array of those
strings. Use `com.typesafe.config` literal parsing with JSON syntax to retain
quotes, Unicode and escapes. Lexical whitespace around key/operator/literal
is syntax; decoded string bytes are preserved without trim. For example,
`allowedKinds = ["text", "missing"]` retains that order for a content field.

Reject malformed syntax, null/wrong-type values, unknown properties, repeated
property keys, unknown case-sensitive role/kind tags, runtime-only metadata,
invalid hierarchy and child spill. Includes, substitutions, concatenation and
multiline literals are forbidden. Do not use silently trimming
`CmlSectionFormat` scalar extraction. Input cannot supply ABI/descriptor
extensions or runtime resource values, action enabled flags or capability data.

## LUI76-S04: Scalars, scopes and deterministic admission

Opaque IDs, binding IDs, definition revisions and provenance satisfy
`value != null && value.nonEmpty && value == value.trim`. Display titles,
labels and `Some(title)` satisfy `value != null && value.trim.nonEmpty` while
preserving the supplied bytes. Programmatic inputs reject null roots,
collections, nested elements, Option containers and `Some(null)` before
 dereference. No sorting, repair, coercion, trimming, Unicode normalization or
unknown-kind fallback occurs after parsing.

| Identity/role | Uniqueness scope |
| --- | --- |
| Screen ID | One explicit component's selected vector |
| Section ID | One Detail |
| Field ID | One List item template or Detail section |
| Action ID | One screen or List item template |
| Noncontent role | One field container; multiple content fields permitted |

Valid ID/binding reuse across scopes is retained. Empty nested vectors are
valid. Resolve every List target against the complete same-component vector
and require a Detail. Missing, malformed, self, wrong-kind, unresolved and
cross-component targets fail. Unreferenced Detail definitions are valid.

The pure gate returns `Either[Diagnostic, admitted immutable projection]`.
`Diagnostic` retains typed code/path/reason. First-error order is fixed:

1. CML shape, types and owner.
2. Root/definition-vector shape and source provenance.
3. Definitions in declaration order: scalar checks, nested shape and scoped
   uniqueness, then nested fields/actions.
4. Duplicate screen IDs.
5. Complete same-vector List target resolution.

Return a stable named-member/indexed path and reason without leaking runtime
values. Reject every invalid input before returning a generated STree; an
error cannot yield a generated Scala prefix or partially admitted vector.

The producer's closed diagnostics are:

| Code | Boundary |
| --- | --- |
| LUI76_CML_INVALID | CML hierarchy, property syntax/types or unsupported input surface |
| LUI76_COMPONENT_INVALID | Missing/ambiguous declared owner or invalid Scala stem/namespace |
| LUI76_DEFINITIONS_MISSING | Present root with empty definition vector |
| LUI76_ELEMENT_INVALID | Null root, vector, nested element or optional shape |
| LUI76_ID_INVALID | Invalid opaque ID, binding or definition revision |
| LUI76_TEXT_INVALID | Invalid display title/label/optional title |
| LUI76_PROVENANCE_INVALID | Missing/invalid source provenance |
| LUI76_VALUE_KIND_INVALID | Empty/unsupported/null/duplicate kind member |
| LUI76_ROLE_INVALID | Invalid role or role-kind mismatch |
| LUI76_DUPLICATE_ID | Repeated ID in its declared scope |
| LUI76_DUPLICATE_ROLE | Repeated noncontent role in a field container |
| LUI76_DETAIL_TARGET_INVALID | Invalid or unresolved same-component Detail target |

Null allowedKinds collection is element-invalid; invalid members are
value-kind-invalid. Provenance and Detail targets keep their specific codes.
At the CLI boundary retain typed code/path/reason through structured
Consequence/Conclusion using Observation, Taxonomy and Cause. Do not collapse
it into string-only `Consequence.failure` or introduce new `failXxx` utilities.

## LUI76-S05: Generation routes, artifacts and application binding

Both ordinary CNCF routes, `modeler-scala` and `modeler-scala-value` with CNCF
target, extract and validate definitions from source component declarations
before building generated STree. Absent Logical UI preserves both routes'
existing output. `--generation-target library` rejects a present Logical UI
runtime definition before output, preserving ordinary library behavior when
absent. `ScalaGenerator` appends `logicalUi: Option[Realm] = None` after
`generationTarget`, preserving existing constructor arguments and defaults;
deterministic Realm artifacts merge only in the CNCF target.
Keep Modeler plumbing small; parsing, validation, projection and rendering
reside in separate files.

[LogicalUiScalaGenerator](../../src/main/scala/cozy/modeler/LogicalUiScalaGenerator.scala)
reuses complete `LogicalUiCncfAbiProjection` admission before constructing a
Realm. Its ordinary-model entry returns `Right(None)` on absence, a present
Realm for admitted roots, or the unchanged first typed Diagnostic on failure.
Both Modeler routes preflight this source before ModelBuilder/ScalaGenerator
and return `SError` carrying
`Conclusion.invalidArgumentFault("<code>: path=<path>; reason=<reason>").withData(diagnostic)`.
Library source preflight performs ordinary Model.load and the same source
admission before ModelBuilder and existing category checks, using `RAISEC`
where its exception-required interface retains the structured Conclusion.

The optional-input guard rejects a null Option or `Some(null)` with structured
invalid-argument text `logicalUi must be a non-null Option containing a non-null Realm.`.
Library first applies that guard, admits None, and rejects every valid Some,
including an empty Realm, with
`LIBRARY_GENERATION_UNSUPPORTED_RUNTIME: category=logical-ui is a CNCF runtime feature. Use --generation-target cncf.`.
Neither library entry silently drops admitted Logical UI. CNCF folds a present
Realm into the complete existing output after Workflow/ProvidedApi/candidate
admission generation; None retains existing artifacts.

Emit into the existing component package plus `.logicalui`:

- `<ComponentStem>LogicalUiDefinitions.scala`: public object
  `<ComponentStem>LogicalUiDefinitions` exposes
  `val definitions: Vector[GeneratedLogicalUiAbi.Definition]`, constructed
  directly from public CNCF types and an explicit fixed descriptor.
- `<ComponentStem>LogicalUiMetadataProvider.scala`: public trait
  `<ComponentStem>LogicalUiMetadataProvider` extends
  `org.goldenport.cncf.display.GeneratedLogicalUiMetadataProvider`;
  `override def generatedLogicalUiDefinitions` returns that definitions vector.

The application component or its actual factory explicitly mixes in the
provider trait and supplies its own View/value/action registration bindings.
Do not rewrite user factory files, reflect on application classes or create a
disconnected standalone generator. CNCF selects component provider first,
factory only if absent; invalid selected providers never fall back. Complete
static admission precedes a post-View atomic typed binding join and List/Detail
projection. Source output alone does not establish that runtime acceptance.

Render LF UTF-8 with final newline, retaining vector declaration order and
safe direct Scala string literal escaping for quotes, backslashes, control
characters and Unicode. Component stem matches `[A-Z][A-Za-z0-9]*`; every
dot-separated package segment matches `[A-Za-z][A-Za-z0-9]*` and excludes
Scala 3 reserved keywords. Invalid namespace/stem returns
`LUI76_COMPONENT_INVALID` before output. Never sanitize opaque semantic IDs
into Scala identifiers. No generated-time headers, clocks, machine paths,
JSON sidecar substitute, copied ABI definitions, Cozy/Flutter/TFAF imports,
endpoints, callbacks, widgets or runtime values belong in generated artifacts.

## LUI76-S06: Reference fixture and evidence ownership

The producer reference declares COMPONENT `Domain`, package `domain`, producing
`domain.logicalui.DomainLogicalUiDefinitions` and
`domain.logicalui.DomainLogicalUiMetadataProvider`. Declared provenance is
`cozy.logical-ui-generated.v1` / `ess2-01` / `ess2-01-cml-1`; both definitions
use revision `ess-definition-1` and viewBindingId `ess.reference`.

The ordered List is `collecting-resources`, title `Collecting Resources`,
target `capture-detail`. The Detail is `capture-detail`, title `Capture Detail`,
with section `main`, title `None`. Its ordered field mapping is:

| Container/order | ID | Label | Role tag | bindingId | allowedKinds |
| --- | --- | --- | --- | --- | --- |
| List item 1; Detail main 1 | title | Title | title | ess.title | text |
| List item 2; Detail main 2 | stage | Capture stage | status | ess.stage | text |
| Detail main 3 | description | Description | description | ess.description | text |

List actions/itemActions are empty. Detail's sole action is
`request-review` / `Request Review` / `ess.request-review`. This is inert static
metadata; no inverse setter, availability decision or Business Operation
execution is generated. The consumer's expected fixture has different sample
provenance (`cncf.expected-generated-fixture` / `ess2-01` / `fixture-1`) and is
handwritten, not producer output. Golden files are expectations only.

LUI-GEN-01 must identify actual output generated twice by ordinary CLI, its
input, exact command, producer/consumer revisions and dirty deltas, ABI target
and deterministic output evidence. LUI-ACCEPT-01 must compile and admit those
same actual files via normal ComponentFactory and prove equivalent reference
List/Detail values, identities and order, including negative admission cases.
A handwritten substitute or producer text comparison cannot satisfy that
receipt. No intermediate public publication or full test is required; Phase
release validation remains separate.

## LUI76-S07: Required future Executable Specifications

P76-01A authors no executable specification or fixture. Later behavior slices
must use AnyWordSpec with Matchers, adjacent GivenWhenThen clauses at actual
setup/action/expectation boundaries, should/which/in grouping and active
ScalaCheck. This matrix describes required future proof, not passed tests:

| Owner/spec | Given | When | Then / property |
| --- | --- | --- | --- |
| LogicalUiProducerSpec | Shared descriptor/roles and explicit static reference definitions | Pure projection admits the vector | Exact CNCF mapping, separate revisions, no eager runtime data; byte/order preservation properties |
| LogicalUiProducerSpec | Null/invalid scalars, nested structures, kinds and scoped duplicates | Pure gate interprets definitions | Closed code/path/reason, no dereference/prefix; valid scoped/binding reuse; stable first-diagnostic properties |
| LogicalUiProducerSpec | Complete List/Detail vectors with valid and invalid targets | Target resolution interprets the entire vector | Same-component Detail required; self/wrong-kind/missing/cross-component rejected; unreferenced Detail retained |
| LogicalUiCmlSpec | Ordinary LogicalSection AST and explicit COMPONENT declaration | CML extraction interprets prologues/hierarchy | Exact owner/grammar/types; absent containers empty; decoded bytes/order preserved |
| LogicalUiCmlSpec | Bad syntax/types, repeated/unknown keys, hierarchy spill and runtime-only metadata | CML extraction interprets input | Deterministic structured rejection, no trim/coercion or implicit owner |
| LogicalUiAbiGenerationSpec | Admitted reference input and safe/unsafe names plus escaped display text | Each ordinary CNCF modeler route generates Realm/STree | Exact public object/trait signature, safe escaping, golden shape, repeated byte-equivalent trees and no runtime imports |
| LogicalUiAbiGenerationSpec / LibraryGenerationSpec | Present or absent Logical UI input | Library target or existing route interprets input | Present library input rejected before Scala prefix; absent behavior unchanged |
| CNCF LUI-ACCEPT-01 | Same actual LUI-GEN-01 generated files and application bindings | Scala 3 compilation, factory discovery/admission and post-View typed join interpret them | Equivalent List/Detail projection and negative admission proof; no handwritten replacement |

Affected regression is existing Logical UI/runtime, Workflow, ProvidedApi and
library-generation specifications. Phase 51 Flutter Target Projection is
PLANNED in this checkout; no Flutter source/suites exist. Preserve that plan
and target-neutral compile-time semantics. Do not start Phase 51 or claim
executed Flutter generator/widget tests. Producer and consumer receipts,
independent review, Step commits and final release proof remain separate from
this documentary scope.

The authored [LogicalUiAbiGenerationSpec](../../src/test/scala/cozy/modeler/LogicalUiAbiGenerationSpec.scala)
now covers pure renderer and both ordinary in-process Modeler routes, exact
two-file goldens, all closed role/kind references, ownership/order/literal
properties, atomic rejection, typed Modeler/Conclusion data, source preflight,
direct generator library/null guards and absent-input compatibility. Each
created scenario places Given/When/Then at its behavior boundaries; three
ScalaCheck properties require at least 100 successful cases. Parent
representative validation passed 56/56 tests across one suite and accumulator
validation passed 266/266 across eight suites: 322/322 across nine suites.
Independent Step review `phase96-cozy76-p76-03a-step-review-001` r1 by
`/root/phase96_cozy76_p76_03a_step_review_001` is PASS, attributed in original
CNCF primary ledger `phase96-review-ledger-001` r14. This accepts the P76-03
source/provider contract and ordinary in-process wiring. The local Step
acceptance commit is `8a4c1794b7ad4557af5b4260d1233acd07520c6e`. P76-04 now
records actual twice-generated CLI output and 254/254 producer/runtime tests
across six suites in the generated handoff journal. P76-04 producer handoff
is accepted by the original independent Step review FINDINGS plus accepted
parent coordinate M0 amendment r1 in original CNCF ledger r17; its local
Step commit is PREPARED/PENDING, not executed. Same-generated Scala 3 compilation/factory/bootstrap/Display
LUI-ACCEPT-01 and whole-Phase verification/release remain pending.
