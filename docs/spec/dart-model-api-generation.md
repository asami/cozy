# Dart model and API generation

Status: CLOSED; frozen Phase 65 contract; four native Steps accepted, sole full Phase review PASS, repository-full tests PASS; closure binding P65-RELEASE-CLOSURE-001 r1
Date: 2026-10-06
Scope: Cozy's Dart model, JSON and operation projection

This specification defines the public generated contract for
[Phase 65](../phase/phase-65.md). The twelve numbered sections retain the parent
contract's decision numbering. They describe required behavior; implementation
correspondence and evidence status are recorded below. The [design](../design/dart-model-api-generation.md)
inventories actual source evidence and responsibility boundaries.

Documentation roles and authority follow the Core Rule Map in
[AGENTS.md](../../AGENTS.md), [RULE.md](../../RULE.md),
[document lifecycle](../../ai/directive/core/document-lifecycle.md) and
[spec style](../../ai/directive/core/spec-style.md). Current progress belongs in
the [checklist](../phase/phase-65-checklist.md). The imported
[provisional proposal](../notes/dart-model-api-code-generation-provisional-specification.md)
and [KnowledgeHub journal](../journal/2026/09/2026-09-15-dart-code-generation-knowledgehub-consumer.md)
remain historical evidence: the current assignment is Phase 65 and Textus CBD
Support; completed local Phase 55 is the site-context media-registration closure.

## 1. Canonical inputs and generator ownership

The generator belongs to Cozy and consumes canonical `SimpleModel` data plus
canonical normalized operation metadata and component/service ownership.
[Modeler.ModelBuilder.buildValue](../../src/main/scala/cozy/modeler/Modeler.scala)
and [ModelTypeProjector](../../src/main/scala/cozy/modeler/ModelTypeProjector.scala)
supply the data projection.
[ModelServiceOperationProjector.normalizedServiceOperationDefinitions](../../src/main/scala/cozy/modeler/ModelServiceOperationProjector.scala)
retains declared DTO names through
`OperationModel.NormalizedOperationDefinition.inputType/outputType`.

Private transient render indexes may key exact qualified source identities.
They must not define a second authored domain model, a second CML, or a new
semantic authority. Generated Scala execution bodies are not a source of client
DTO identity. `MOperation` alone is insufficient because record-based server
parameters/results do not preserve all declared DTO identities.

The Dart package entry also admits original top-level `QUERY`/`COMMAND` DTOs
without requiring a duplicate `VALUE` declaration. Its package-private
[canonical input projection](../../src/main/scala/cozy/modeler/DartCanonicalInputProjection.scala)
parses unchanged typed `KaleidoxModel.OperationDivision` LogicalSections with
the existing `Dox2Parser` configuration. It follows `OperationModel` structural
semantics: immediate DTO children of `query`/`command` Sections are candidates;
`operation` Sections are not descended into, and ATTRIBUTE descendants are
not independent input declarations. The admitted inventory remains
`builder.operation.values`, keyed by its exact original name. Duplicate legacy
names fail deterministically before indexing, including COMMAND/QUERY overlap
and equal names with different package properties.

For each legacy name absent from the ordinary VALUE map, exactly one unchanged
original DTO Section must supply `ValueModel.ValueClass.create(Section)`. The
canonical factory retains schema, package/package-name properties, source
metadata and location. Missing/ambiguous source or an unsuccessful factory
fails with the input name; neither normalized parameters nor generated Scala,
sample JSON or a rewritten VALUE model supplies a replacement schema. Empty
objects are admitted only through the same factory and ordinary generator rules.
Existing VALUE declarations remain authoritative for rendered fields and
properties; legacy kind and explicit operation parameter precedence remain
the existing normalizer's contract. Default packages retain the component
package plus `.value` fallback; exact qualified references never fall back to
short names. Both canonical data projection and operation bindings use the
same enriched builder. This transient copy affects only `DartPackageGenerator`;
original `buildValue`, Scala/CNCF/library and direct `DartModelGenerator` behavior
remain unchanged.

The [legacy-input executable specification](../../src/test/scala/cozy/modeler/DartLegacyInputGenerationSpec.scala)
authors parsed QUERY/COMMAND, identity/codec, duplicate/source/kind rejection,
VALUE/parameter precedence, non-Dart compatibility and meaningful ScalaCheck
source-order/multiplicity observations. Historical S65.4B validation passed all
13 suites with 217/217 tests and sixteen SDK command reports, zero SBT/wrapper
exits and `lock=released`;
these scenarios do not replace live real CBD evidence.

## 2. Selection and dependency closure

Options select a package name, qualified type roots, qualified operation roots,
and finite powertypes to render as sealed unit alternatives. Selection changes
projection, not source type definitions. Empty selection selects all supported
data types. Selected operations include their canonical request/response DTOs
and transitive type dependencies; declared error DTOs use the same projection.

Resolve exact qualified identities before emission. Missing or ambiguous roots
and unresolved references in the selected closure produce structured generation
failure. Unrelated unselected model types must not contaminate the closure.
The generator must never choose the first ambiguous short name or silently drop
a selected field or dependency.

## 3. Supported shapes and rejection boundary

| Canonical shape | Dart projection | Admission boundary |
| --- | --- | --- |
| String, boolean, integral or floating primitive | `String`, `bool`, `int`, `double` | Scalar constraints in section 4 apply. |
| `MNominalDataType` over an admitted primitive | Named immutable wrapper | Primitive wire form in section 6. |
| `MDomainValue` | Named immutable value | Scalar wrapper only for the exact section 6 shape; otherwise object. |
| `MStructuredDataType` | Named immutable structured class | Every selected constituent must resolve to an admitted type. |
| `MEntity`, including modeled identifiers | Named immutable class with typed fields | No guessed identifier shape or relationship semantics. |
| Resolved typed reference | Referenced generated type | Must resolve uniquely inside dependency closure. |
| Standard `1`, `?`, `*`, `+` multiplicity | Section 5 constructor/storage mapping | Arbitrary range multiplicities are rejected. |
| Finite or knowledge/open `MPowertype` | Section 7 classification mapping | Closure comes from canonical classification semantics. |

Unsupported scalar kinds, arbitrary multiplicity ranges, unsupported
relationship or trait semantics, ambiguous inherited fields and unsupported
source inheritance fail structurally. Observed descendants do not authorize a
sealed inheritance hierarchy. Failure must not become a `dynamic`/`Map` domain
fallback, placeholder success, or omitted selected fields. Existing source
parsing/projection restrictions, such as unsupported nested complex datatypes,
remain explicit failures rather than a Dart-specific reinterpretation.

## 4. Primitive types and numeric wire constraints

| Admitted primitive | Dart type | JSON wire | Required rejection |
| --- | --- | --- | --- |
| string | `String` | String | Non-string input |
| boolean | `bool` | Boolean | Non-boolean input |
| byte/short/int/other admitted integral type | `int` | Integral number | Fractional input, unsafe JSON integer, or narrower declared range violation |
| admitted floating type | `double` | Finite number | Non-number, NaN or either infinity |

Integral values must satisfy `abs(value) <= 9007199254740991` on the JSON
boundary. Preserve narrower declared byte, short and int constraints as well;
the JSON-safe bound does not widen a declared type. Do not coerce a fractional
number into an integer or turn a number into a string.

Decimal, date/time, opaque record and unsupported numerical semantics fail
explicitly in this initial target. No precision change, timezone assumption,
stringification or alternate wire representation may silently admit them.

## 5. Constructors, multiplicity and immutable storage

All generated model state is immutable. Fields retain their declared typed
identity, including references and wrapper types.

| Source multiplicity | Field type | Named constructor parameter | Missing JSON key | Explicit JSON null |
| --- | --- | --- | --- | --- |
| `1` | Nonnullable `T` | Required | Decoding failure | Decoding failure |
| `?` (`0..1`) | Nullable `T?` | Optional, null default | Null | Null |
| `*` (`0..*`) | Nonnullable `List<T>` | Optional, empty default | Empty immutable list | Decoding failure |
| `+` (`1..*`) | Nonnullable `List<T>` | Required, nonempty | Decoding failure | Decoding failure |

Collection elements must decode as their declared nonnullable element type;
`+` rejects an empty array as well as a missing array. Constructors enforce
requiredness and represented cardinality. Copy supplied collection inputs into
unmodifiable storage so mutation of the caller's list cannot mutate generated
state. Generated lists cannot be modified through the exposed field.

Wire object keys retain original source field names under the default `source`
profile, even when Dart identifiers are normalized or escaped. Explicit
`cncf-snake-case` changes only object field keys and field `wireName` metadata
through the source-derived profile below. `toJson` emits optional null explicitly and emits
lists as arrays. Unknown object fields are ignored for additive producer
evolution; unknown finite variant names are rejected. Optional missing and
explicit-null inputs both represent the same null state.

Non-scalar data objects additionally expose
`Map<String, Object?> toJsonOmittingNullFields()`. This explicit method omits
only `MZeroOne` fields whose value is null through generated collection-if
entries. Required fields, nonnull optionals and empty list keys remain. It
recursively selects the same method for non-scalar data references in required,
optional and list fields. Empty objects return a typed empty map. Scalar wrappers
and enum/sealed/open `MPowertype` classifications retain their scalar or original
discriminator `toJson()` and receive no omission method. The zero-argument
`Object? toJson()` signature, expression and default recursive explicit-null
behavior remain unchanged before and after omission calls; no map is filtered
or mutated. Decoding omitted JSON retains the existing canonical null state:
omission re-encoding preserves absence, while default re-encoding restores null
keys. Both field profiles, constructor/decode validation and wire metadata remain
unchanged. This additive method needs no generator or CLI option.

## 6. Object and scalar wrapper wire forms

Multi-field values, entities and structured datatypes encode as JSON objects.
Nominal primitive values encode directly as their primitive. A domain value
whose sole field is a required primitive named literally `value` also encodes
as that primitive and decodes into its named immutable wrapper. A single field
with any other name remains an object; an optional `value` field does not meet
the scalar-wrapper rule. Nested references use the referenced type's codec.

| Source shape | Named Dart model | JSON form |
| --- | --- | --- |
| Nominal string datatype | Named string wrapper | `"text"` |
| Domain value with sole `value:string:1` | Named string wrapper | `"text"` |
| Domain value with sole `code:string:1` | Named object class | `{"code":"text"}` |
| Domain value with multiple fields | Named object class | Object with source keys by default, selected profile keys when explicit |
| CBD response's `overall:CbdResultStatus:1` | `CbdResultStatus` field | String under `overall` |
| CBD request/response's `detail:CbdStatusDetail:?` | `CbdStatusDetail?` field | String or explicit null under `detail` |

The CBD wrappers are open strings. Values observed in runtime status output
must not be used to infer an enum.

## 7. Finite, open and selected sealed classifications

| Canonical classification | Default representation | Sealed option | Wire discriminator |
| --- | --- | --- | --- |
| Finite non-knowledge `MPowertype` with named kinds | Enhanced Dart enum | Sealed base and final unit alternatives | Exact source kind name as a string |
| Knowledge/open `MPowertype` | Extensible typed string wrapper | Rejected | String; unknown names allowed |
| Ordinary inheritance with observed children | No inferred closed union | Unsupported inheritance rejected | No invented discriminator |

Keep `MPowertypeKind.name`, `value` and label metadata. Numeric values and labels
are not alternate wire discriminators. Known kind metadata remains available on
open classifications without constraining their extension names.

`--sealed-types` selects qualified finite powertypes only. Its alternatives
represent the same finite unit cases as the enum. It must not invent payload
cases or add a discriminator field absent from source semantics. Compiler
exhaustiveness must be demonstrated for both enum and sealed forms. Empty
finite sets, duplicate kind names and ambiguous normalized symbols fail.

Canonical IR currently has no general exhaustive payload-union definition.
Do not fabricate one from object descendants, labels or sample JSON. General
payload union support awaits an actual canonical closure contract; this does
not remove the finite classification support required here.

## 8. Symbols, collisions and deterministic output

| Element | Generated convention | Keyword handling |
| --- | --- | --- |
| Public Dart type | UpperCamel | Stable `Value` suffix |
| Dart field or operation method | lowerCamel | Trailing `_` |
| File or package name | snake_case | Package must satisfy section 11 validation |
| JSON field key | Exact source spelling by default; explicit profile transforms keys | No Dart keyword substitution |
| Finite/open kind discriminator value | Exact source spelling under every profile | No field-profile transformation |

Normalize deterministically and reject symbol/path collisions before emission,
including field/method/variant collisions and distinct qualified source types
that normalize to the same public Dart type. Do not silently select one source
or rename away its identity. Keyword escaping participates in collision checks.
Scala implementation helpers follow repository naming rules, including private
member `_snake_case`; Dart conventions do not override Scala authoring rules.

Non-scalar data-object member admission reserves `toJsonOmittingNullFields`
before rendering. A conflicting canonical field fails through structured
`invalidArgumentFault` with its original identity under either profile. Scalar
wrappers and classifications retain exactly their existing reservations.

Identical canonical input and options must produce identical sorted paths and
contents, encoded as UTF-8 with LF. Output must contain no timestamps or
machine-specific checkout paths.

## 9. Generated package and imports

The complete generated package has this layout:

```text
pubspec.yaml
lib/<package>.dart
lib/src/models.dart
lib/src/api.dart
lib/src/json.dart
README.md
```

`pubspec.yaml` declares SDK `>=3.0.0 <4.0.0`. Use SDK libraries only, with no
third-party dependencies. The barrel and named imports expose public model,
API and codec contracts to a separate consumer package. Serialization boundaries
may use `Object?` and `Map<String, Object?>`; domain fields must remain typed
and must not become dynamic maps. Package README material records source
identities and usage without machine checkout paths.

## 10. Operation identity, DTO reuse and error boundaries

Generate abstract interface classes with typed
`Future<Response> operation(Request request)` signatures, using `void` where
the source declares void. Reuse the canonical `inputType`/`outputType` DTO
identity; emit each shared DTO once through the same data projection rather
than constructing a second operation-local shape from record fields.

Document component, service, operation name and operation kind as source
identity. Operation selection uses `component.service.operation` identities.
Missing DTO metadata and unresolved or ambiguous ownership/DTO identity fail.
Declared error DTOs use the same canonical data projection. Do not create an
undeclared domain error hierarchy or a synthetic success/error envelope.

JSON decoding failures use a typed technical exception, distinct from modeled
operation failures. Generation failures use Cozy's structured response failure
boundary; they are not generated domain failures. Consumer transport and domain
policy remain outside the generator. No HTTP path, authentication rule or
transport implementation is inferred from an operation name.

For the real CBD source, the required typed operation is
`CbdSupport.CbdRetrieval.status`, kind `QUERY`, with `CbdStatusRequest` and
`CbdStatusResponse`. It returns `Future<CbdStatusResponse>` and accepts the
canonical request; the consumer supplies its implementation.

## 11. CLI contract and compatibility

The entry authored in Cozy's
[ModelerServiceClass](../../src/main/scala/cozy/modeler/ModelerServiceClass.scala), is:

```text
cozy modeler dart <model-file> --package=<dart_package> --save=<output-dir>
  [--types=<comma-separated-qualified-types>]
  [--operations=<comma-separated-component.service.operation-identities>]
  [--sealed-types=<comma-separated-qualified-finite-powertypes>]
  [--charset=UTF-8]
  [--json-field-style=source|cncf-snake-case]
```

The model file, package name and output directory are required. Validate the
package as a Dart pub package identifier using the snake_case convention.
Charset defaults to UTF-8 and uses the existing model file parsing boundary.
Invalid package names, missing arguments, invalid charsets, unreadable or
malformed files and invalid selected contracts return structured failure.

`DartModelGenerator.Options` and `DartPackageGenerator.Options` append
`jsonFieldStyle: DartJsonFieldStyle = DartJsonFieldStyle.Source`. The public
sealed Scala 2 profile accepts only exact identifiers `source` and
`cncf-snake-case`; empty, whitespace, aliases and unknown names fail through
structured `invalidArgumentFault`. Both `--json-field-style=value` and
`--json-field-style value` work only on the Dart request. Omission and explicit
`source` preserve existing source keys. Invalid styles fail before model loading,
generation or saving and preserve absent/existing output destinations.

The CNCF profile mirrors the selected runtime: trim; when `-` or `_` occurs,
split on either separator, discard empty segments, lowercase only the first
character of the first segment and uppercase only the first character of later
segments; otherwise lowercase only the first character. Partition the normalized
ASCII name before every uppercase character after index zero, lowercase segments
with `Locale.ROOT` and join with `_`. Thus `sourceCount`, `source-count`,
`source_count` and `source__count` become `source_count`, `readySourceCount`
becomes `ready_source_count`, `URLValue` becomes `u_r_l_value`, `class` remains
`class`, and `some-key` becomes `some_key`. This is independent of Dart filename
acronym grouping. Source keeps its exact original name, including whitespace.

The projection retains immutable original canonical objects, Dart fields/types,
source identities, types, multiplicities and constraint metadata. Before symbol
admission or rendering, each object's transformed keys must be unique; collision
failure identifies the canonical object, original conflicting fields and wire key.
Encode/decode and field `wireName` metadata use the same selected key. Scalar
wrapper form, classification discriminator values, API signatures, constructor,
null/list/numeric checks and the technical JSON runtime retain their contracts.
The generated README records the selected profile. There is no runtime key adapter.

Validate every selected contract and construct the whole output `Realm` before
returning success. Invalid input must not write a partial package. `--save`
uses existing `Environment`/response-owned file writing after the successful
typed file-realm response; the generator/facade must not write individual files
as it validates them.

`ModelerServiceClass.DartOperationClass` passed focused validation attempt 004
and was accepted by independent protected Step review 001. Existing `modeler class` and `modeler project`
retain their loader, charset, response and failure contracts in
[modeler service operations](modeler-service-operations.md). Direct
`modeler-scala`, `modeler-scala-value` and
[ModelGenerationTarget.Cncf/Library](../../src/main/scala/cozy/modeler/ModelGenerationTarget.scala)
retain their current semantics. `Cncf`/`Library` remain Scala/CNCF choices and
are not repurposed as Dart target selectors. Scala 2.12.18 and Cozy
0.3.3-SNAPSHOT remain unchanged in this contract Slice.

## 12. Source-derived validation and semantic limits

Retain admitted source validation constraints as source-derived metadata.
Enforce cardinality, requiredness, primitive decoding and represented finite
membership. Do not promise complete [Phase 54](../phase/phase-54.md) semantic
validation, invent its failure taxonomy, or add HTTP paths, authorization or UI
navigation. Consumer-specific semantics do not enter the reusable generator.

## Real CBD acceptance contract

The source is `textus-cbd-support:src/main/cozy/textus-cbd-support.cml`, package
`org.simplemodeling.textus.cbdsupport`. The design contains its read-only source
locators and runtime evidence route.

| DTO | Canonical field | Multiplicity | Generated field / wire |
| --- | --- | --- | --- |
| `CbdStatusRequest` | `detail:CbdStatusDetail` | `?` | `CbdStatusDetail?` / string or null |
| `CbdStatusResponse` | `overall:CbdResultStatus` | `1` | `CbdResultStatus` / string |
| `CbdStatusResponse` | `sourceCount:int` | `1` | `int` / integral number |
| `CbdStatusResponse` | `readySourceCount:int` | `1` | `int` / integral number |
| `CbdStatusResponse` | `componentCount:int` | `1` | `int` / integral number |
| `CbdStatusResponse` | `detail:CbdStatusDetail` | `?` | `CbdStatusDetail?` / string or null |
| `CbdResultStatus`, `CbdStatusDetail` | sole `value:string` | `1` | Named immutable wrapper / string |

DART-08 requires a Cozy-owned, independently compiled Dart consumer importing
the SDK generated from that real CML and consuming a live real CBD status
response. Its injected transport boundary obtains the existing MCP `tools/call`
output; generated interfaces/codecs supply request encoding and typed response
decoding/re-encoding. The consumer accesses named wrappers and counts and owns
no second request/response/domain model. A toy fixture, copied JSON or generated
text assertion does not satisfy this requirement.

## Observable acceptance scenarios and evidence status

Every row below remains a required executable acceptance scenario. S65.2's
DA-01..12 evidence is accepted at native `716d208ce8505746ac03a3cc3606299f5c4e10e4`;
S65.3A's DA-13..19 programs/specifications passed focused validation attempt 004:
Main and Test compilation and all twelve suites succeeded, with 195/195 tests,
zero failures or aborted/canceled/ignored/pending tests, SBT/wrapper exit 0 and
the serial lock released. Actual SDK evidence contains sixteen direct command
reports across the typed positive consumer, intended negative type diagnostics
and all-void package scenarios; the negative analyzer was intentionally rejected.
Dart SDK 3.13.4 exercised `@dart=3.0` source mode, without a separately installed
Dart 3.0 SDK claim. The first complete independent protected Step review 001
returned PASS, with no Current Boundary Blockers, Hygiene or Development
Candidates. It accepted the malformed canonical-model error guard and
valid-empty/CLI conformance. S65.3 is independently accepted at native
`770bf6b380a292c027fa059c7f50cd58dfcbd00c`. The executable specifications use `AnyWordSpec`,
`Matchers` and `GivenWhenThen` at actual setup/action/expectation boundaries.
Compilation/analyzer/consumer evidence must exercise emitted packages. S65.4A's
independent consumer and private source-copy/live acceptance scripts are
authored. S65.4 preparation has
produced the private CAR and dependency-only classpath successfully. The actual
Dart generation attempt failed because the legacy QUERY request was absent from
canonical data projection; an earlier `newSpec` compilation failed and remains
historical evidence. With S65.4B's original-Section bridge, current validation
passed all 13 suites and 217/217 tests, retaining sixteen SDK command reports,
zero SBT/wrapper exits and `lock=released`. Fresh actual-source SDK generation
passed with all six products. The first live attempt failed before readiness,
ran zero scenarios and completed owned cleanup. S65.4C's CAR/SAR behavioral
regression passed 12/12 with zero failures/errors in
`target/phase65/s654c-unit-result.json`. Historical live001 retains its
pre-readiness deployment failure. Live002 (`s654/runs/live-2bqktlyp`) reached
the owned successful bind, HTTP 200 OpenAPI and successful `tools/list` with
exactly one `org.simplemodeling.textus.CbdSupport.CbdRetrieval.status`.
VAL-P65-S654C-005 failed before scenarios because the private locator omitted
the namespace; zero scenarios ran and owned Java/fixture cleanup succeeded.
The corrected private MCP `params.name` uses that fully qualified identity;
canonical operation selection `CbdSupport.CbdRetrieval.status` and generated
API types remain unchanged. Live003 (`s654/runs/live-1ymsxpks`) reached owned
readiness, SDK offline pub/analysis and consumer pub, then failed consumer
analysis on generated `Object?` request encoding at the typed transport
boundary. Zero scenarios ran. Cleanup recorded TERM then `Operation not
permitted`, `success=false`; its failing syscall is unknown. Later scoped probes
observed PID/group 9834 absent as separate evidence only, without repairing the
cleanup receipt or identifying its cause. All failed runs/raw evidence limits
remain retained. S65.4D changes below subsequently passed unit18 and the live004
prerequisites described under S65.4E; complete real acceptance remained unresolved
at that attempt, before S65.4G's product proof below.
At that historical attempt, independent S65.4 review/commit remained pending;
no Step acceptance or Phase closure was claimed then.

| ID / Step | Given | When | Required observable result |
| --- | --- | --- | --- |
| DA-01 / S65.2 | Admitted scalar, wrapper, structured, entity and typed-reference shapes | Generate and compile | Typed immutable classes; source identity and fields preserved |
| DA-02 / S65.2 | Missing/ambiguous selected root or unresolved selected reference | Generate | Structured failure; no partial output |
| DA-03 / S65.2 | Unsupported scalar/range/relationship/trait/inheritance or ambiguous inherited field | Generate | Explicit failure; no map fallback or omitted selected field |
| DA-04 / S65.2 | Required and optional fields, including explicit null | Construct and decode missing/null inputs | Required missing/null rejected; optional absent/null retained as null and emitted explicitly |
| DA-05 / S65.2 | `*`/`+` lists and a mutable supplied list | Decode and mutate lists | Absent `*` becomes empty; absent/null/empty `+` rejected; list storage remains immutable and copied |
| DA-06 / S65.2 | Admitted/narrow integral limits, fractional numbers and nonfinite floating input | Construct/encode/decode | Safe integers and declared ranges retained; invalid numeric forms rejected |
| DA-07 / S65.2 | Sole required primitive `value`, sole differently named field and multi-field objects | Round-trip JSON | Named wrapper uses scalar; other shapes use objects; default source wire keys unchanged |
| DA-08 / S65.2 | Finite powertype with name/value/label | Generate default enum; compile exhaustive switch; decode unknown name | Exhaustiveness demonstrated; exact name wire; metadata retained; unknown name rejected |
| DA-09 / S65.2 | Same finite powertype selected as sealed | Generate final unit alternatives and compile exhaustive switch | Same finite wire semantics; no added payload or discriminator field |
| DA-10 / S65.2 | Open classification and unknown name | Decode and try sealed selection | Typed extensible wrapper accepts name; known metadata available; sealed selection fails |
| DA-11 / S65.2 | Empty finite set, duplicate kind names, normalized type/field/variant/path collision | Generate | Structured failure; no arbitrary winner or identity renaming |
| DA-12 / S65.2 | Object with unknown additive fields and nested typed values | Decode/re-encode | Known typed state round-trips; unknown fields ignored |
| DA-13 / S65.3 | Identical input/options; selected closure with unrelated unselected unsupported type | Generate repeatedly | Identical sorted UTF-8/LF paths/content; unselected type does not contaminate closure |
| DA-14 / S65.3 | Shared canonical input/output/error DTO identities and source void | Generate and independently compile typed interfaces | DTO reused once; typed Future signatures and void retained; no synthetic envelope |
| DA-15 / S65.3 | Missing DTO metadata or ambiguous operation ownership | Generate | Structured failure; no inferred DTO from Scala body |
| DA-16 / S65.3 | Keyword identifiers and original source wire names | Generate, compile and round-trip | Stable escapes compile; default wire keys and all discriminator values retain source spelling |
| DA-17 / S65.3 | Complete generated SDK-only package | Analyze and import barrel from a separate package | Analyzer-clean output and successful independent consumer compilation |
| DA-18 / S65.3 | Missing CLI arguments, invalid package/charset/file or invalid selected contract | Invoke proposed entry | Structured failure before success/file writing; no partial package |
| DA-19 / S65.3 | Existing class/project, direct Scala and Cncf/Library cases | Run focused compatibility specifications | Existing targets and response/failure contracts preserved |
| DA-20 / S65.4 | Explicit CNCF-profile SDK generated from unchanged real CBD status CML and actual running CBD | Use generated omission encoding to send exactly {"detail": marker} and {} through tools/call; compile/run independent Dart consumer | Marker echoed; absent detail returns actual fixture-catalog=ready summary; named wrappers/counts decoded from actual snake keys; complete live response re-encoded unchanged; no shadow model |
| DA-21 / S65.4 | Source-copy build, launcher or actual wire failure | Attempt real acceptance route | DART-08 remains unresolved; no fixture/canned-output substitute or success claim |
| DA-22 / S65.4E | Default/explicit Source and selected CNCF profile over parsed canonical closure | Generate directly/package/CLI; independently analyze/run codecs; attempt invalid styles/colliding keys | Default compatibility, exact runtime naming, deterministic immutable identities, matching transformed encode/decode/metadata; structured pre-output failure; real CBD proof still requires DA-20 |
| DA-23 / S65.4F | Both field profiles, optional nulls in top/required nested/list data, nonnull scalar/finite values and a colliding method field | Independently analyze/run both generated methods and decode; attempt direct/package collision | Complete recursive omission only for null optionals; unchanged default explicit-null form before/after; required/empty-list/nonnull/scalar/discriminator continuity; pre-output canonical-identity collision failure; actual251/14/31 and six real-CML products/unit20 passed; live006 fixture degradation left acceptance false at that attempt; S65.4G live007 product proof PASS below |

S65.2 accepted both Dart model suites with 21/21 tests in validation attempt 006,
actual emitted SDK analysis/runtime/compiler checks and independent focused
re-review 006. S65.3 is independently accepted by protected Step review 001.
The real CBD checklist box is now checked after independent S65.4 acceptance;
the final Phase closure box is checked. Phase 65 is CLOSED.
Sole full Phase review P65-FULL-REVIEW-001 r1 PASS and repository-full tests PASS provide the final release closure basis P65-RELEASE-CLOSURE-001 r1.
The
real CBD source fields, `ComponentFactory.StatusActionCallImpl` and MCP envelope
remain read-only source evidence; live consumer results remain S65.4 evidence.
The design retains the historical preparation failure, current successful
private CAR/classpath export, successful fresh SDK generation and failed first
live001 attempt, plus actual unit12/12 and live002 bind/tool-registration
evidence, followed by live003's analyzer failure/zero scenarios/cleanupfalse.
Live004 subsequently passed SDK/consumer analysis, first MCP200 and strict cleanup
but failed typed decode. S65.4E subsequently passed the actual profile accumulator
and both typed real response roundtrips; live005 still failed genuine omission
as recorded below. S65.4F validation passed as recorded below; complete real
acceptance remained pending at that attempt after live006 fixture-catalog degradation;
S65.4G's product proof is recorded below and independently accepted in S65.4.

## Authored implementation correspondence

This section records source correspondence and does not change the numbered
contract or acceptance matrix.

The [real CBD consumer](../../examples/dart-cbd-consumer/consumer/bin/contract.dart)
imports the generated `phase65_cbd_sdk` barrel through its sole
[path dependency](../../examples/dart-cbd-consumer/consumer/pubspec.yaml). It
implements `CbdSupportCbdRetrievalApi` with an injected function, encodes
`CbdStatusRequest` and decodes/re-encodes `CbdStatusResponse` through generated
codecs. It accesses `CbdResultStatus.value`, optional `CbdStatusDetail.value`
and all three typed counts without declaring DTOs or an error hierarchy.
The [preparation program](../../scripts/test/phase65/prepare-cbd-consumer.py) records
actual source HEAD/dirt/copy attribution and appends private build settings;
the [live acceptance program](../../scripts/test/phase65/run-cbd-consumer.py) authors
DA-20 observations at actual setup/action/expectation boundaries for explicit
and omitted detail. Each scenario must compare sent generated arguments and
injected request, full live JSON and typed re-encoding, wrappers and counts,
fixture-fed readiness and actual detail behavior. Its retained command/HTTP
receipts and owned cleanup are acceptance inputs when executed; authoring is
not successful live evidence. DA-21 failures return nonzero and retain evidence
without a fixture/canned-output success substitute. The
[example procedure](../../examples/dart-cbd-consumer/README.md) records DART-09's
later package/API/codec reuse boundary without claiming UI work.

S65.4C admits the actual CAR's schemaVersion integer 3 and canonical component
`namespace=org.simplemodeling.textus`, `id=CbdSupport`, `version=0.1.0-SNAPSHOT`
before Java/fixture startup. Absent, malformed, legacy-only and conflicting
identity must fail with a named admission error; no filename/coordinate fallback
is permitted. A Cozy-owned private SAR contains only `subsystem-descriptor.json`
derived from that identity. The original copied standalone descriptor remains
read-only provenance and the real CAR is copied unchanged. The descriptor keeps
the existing `convention=disabled`, `fallback_privilege=disabled`, fixed local
subject `cbd-standalone-review`, `roles=[reviewer]`, `security_level=user`, and
`attributes.installation=cbd-standalone`. Explicit
`textus.subsystem.user-mode=standalone` retains
`textus.subsystem=textus-cbd-standalone`; artifact derivation and effective mode
are recorded in process/run observations. This changes no runtime security
policy or generator contract. The
[behavioral regression](../../scripts/test/phase65/test_run_cbd_consumer.py)
observes actual admission and decoded SAR identity/policy with target-owned
fixtures, independently of DART-08 proof; its actual execution passed 12/12.

S65.4D retains generated `Object?` codec signatures and the typed transport.
The consumer assigns request encoding to local final `encoded`, requires
`Map<String, Object?>` with the named `FormatException` on a non-object, then
passes the promoted value to transport and the generated response decoder.
No cast, dynamic domain map, shadow model or generator change is introduced.

After canonical CAR/SAR admission and before Java version/fixture acquisition,
the live harness refreshes exactly the current authored consumer pubspec and
bin/contract.dart into `target/phase65/s654/consumer`. Inputs, outputs and
ancestors reject symlinks; existing output must be regular. Only consumer/bin
directories are created. SDK, `.dart_tool`, original source preparation and old
runs are preserved. S65.4D used `../sdk`; S65.4E explicitly selects
`../sdk-cncf-snake` while retaining the original SDK. Actual two-file locators
are recorded as `consumerPreparation` in result/Given observations; original
`preparation.json` remains source provenance, not refreshed consumer attribution.

The selected macOS owned-group observation is exactly
`/bin/ps -g <native-owned-group> -o pid=,pgid=`, no shell, capture/text, timeout2s,
preserved PATH and `LC_ALL=C`. Positive integer group IDs and actual query
argv/start/finish/exit/stdout/stderr or timeout/OSError evidence are required.
Only exit1 with both streams empty proves absence. Exit0 requires empty stderr
and nonempty rows of two positive integers whose pgid matches the owned group.
Every malformed/mismatched/other-exit/stderr/timeout/permission result raises
`ValueError('Owned process group observation failed: ...')`; inability to
observe never proves absence. No universal Linux interpretation is asserted.
Cleanup always attempts owned TERM, polls/reaps the leader during 15s grace,
attempts owned KILL unless absence was observed, always attempts wait10s and
observes final absence within 5s. ESRCH races record absent signal targets only.
Query failures leave the current loop, retain named stage/errors and continue
bounded cleanup; no failed query is retried in that loop. Later successful
observations never erase errors. Success requires observed native wait exit,
observed groupGone and no errors, plus fixture shutdown/close/join.
`groupObservations` never supplies signal targets. No killpg(group,0) probes,
permission fallback or unrelated PID discovery are permitted.

The Python behavioral specification retains twelve CAR/SAR methods and authors
exactly six methods for empty group absence; leader/child and child-only presence;
malformed/mismatched query failures; timeout/permission raw evidence; owned
cleanup denial/query failure with best-effort wait; and current two-file refresh,
retained unrelated output, actual provenance and input/output symlink rejection.
Each method/subtest places Given/When/Then beside setup/action/assertions and
mocks native boundaries without real launches/signals/network. Managed Python
>=3.12, dependencies=[] and import safety remain. The next execution records
actual counts/status in `s654d-unit-result.json` and fails on zero tests; the
historical unit12 receipt remains. Actual S65.4D execution passed all 18 methods
in `target/phase65/s654d-unit-result.json`. Unit mocks do not establish DART-08
or Step/Phase closure.

## Authored S65.4E profile and real acceptance

Actual live004 in [the retained result](../../target/phase65/s654/runs/live-nm1w2a4r/result.json)
passed SDK and independent consumer offline pub/analysis, generated request,
first real MCP HTTP200 and strict owned Java/fixture cleanup. Its unchanged payload
contained `source_count=5`, `ready_source_count=3`, `component_count=1` and scalar
overall/detail. Generated Source decoding required `sourceCount` and failed;
zero complete scenarios passed and the omitted-detail second call did not run.
Selected CNCF 0.5.3-SNAPSHOT formatter bytecode calls context property naming,
whose default SnakeCase path partitions every uppercase character. CML and the
Record producer retain their original camel names; source/JAR byte equivalence
is not claimed.

S65.4E authors [DartJsonFieldStyleSpec](../../src/test/scala/cozy/modeler/DartJsonFieldStyleSpec.scala),
the [CLI profile scenarios](../../src/test/scala/cozy/modeler/DartModelerServiceSpec.scala)
and [independent Dart profile consumer](../../src/test/scala/cozy/modeler/DartPackageRuntimeSpec.scala).
These fixtures cover DA-22; they are not real CBD proof. The
[real acceptance procedure](../../examples/dart-cbd-consumer/README.md) requires
fresh generation with `--json-field-style cncf-snake-case` into
`target/phase65/s654/sdk-cncf-snake`, retaining original `sdk` and every failed run.
The consumer's only path dependency is `../sdk-cncf-snake`; its original generated
Dart field APIs remain camel case. The live harness records that exact SDK/profile
in Given provenance, maps each typed count to its fixed actual snake payload key,
and still requires complete `decodedResponse == actual payload` equality. No
payload mutation, transport/DTO/codec substitute or SDK rewrite is admitted.
The S65.4E accumulator actually passed 249/249 tests in 14 suites with 21 SDK
command reports, zero SBT/wrapper exits and `lock=released`. Fresh generation
from unchanged real CBD CML passed with all six products. These results precede
the S65.4F change and do not validate the new omission method.

## Authored S65.4F optional absence and actual evidence

Actual live005 [result](../../target/phase65/s654/runs/live-96ligvfe/result.json)
retains both real MCP HTTP200 calls, complete generated typed decode/default
re-encode equality with both full actual payloads, explicit marker echo and strict
owned Java/fixture cleanup PASS. Its second request was {"detail":null}; the
runtime returned detail string "null", so the required omitted-detail
fixture-summary observation failed (VAL-P65-S654E-009). This is partial proof,
not DART-08 success. Earlier preparation, S65.4B/S65.4E compilation and
live001..004 failures remain retained. Original raw-submission gaps, SDK CLI
platform-input absence and corrected native attribution remain evidence limits;
historical missing tool input is not reconstructed.

S65.4F authors the additive method in
[DartModelRenderer](../../src/main/scala/cozy/modeler/DartModelRenderer.scala),
using private `omitnullfields: Boolean = false` encoding parameters and resolved
canonical references, plus data-only reservation in
[DartModelProjection](../../src/main/scala/cozy/modeler/DartModelProjection.scala).
The [collision specification](../../src/test/scala/cozy/modeler/DartJsonFieldStyleSpec.scala)
adds one direct/package behavior under both profiles. The
[DA-23 independent consumer](../../src/test/scala/cozy/modeler/DartPackageRuntimeSpec.scala)
adds one behavior with separate Source/CNCF scratch packages, a local optional
itemNote fixture extension and complete independently spelled JSON expectations.
It observes default encoding before/after omission, recursive required/list
absence, nonnull scalar/finite values, empty lists and both decode forms.

The real consumer selects `toJsonOmittingNullFields()` in request and injected
status modes; its map return removes the former redundant object-type guard.
Response decoding and default `toJson()` stay unchanged. The fresh dependency
and execution/scenario/Given SDK path are `sdk-cncf-snake-omit`, with profile
`cncf-snake-case` and `requestEncoding=toJsonOmittingNullFields`. Before HTTP the
harness requires exactly {"detail": marker} or {}, rejecting present-null and
additional keys, retaining generated evidence and comparing injected/sent maps.
Two new mock-only Python methods specify provided/absent envelopes, full typed
controlled payloads/summary and pre-HTTP present-null rejection. The S65.4F unit
receipt is fresh `target/phase65/s654f-unit-result.json`; unit18's receipt remains.

DA-23/S65.4F actual validation passed 251/251 tests in 14 suites, 31 SDK commands
across six independent consumers and one controlled negative, six actual real-CML
SDK products and unit20 PASS. Actual live006 (`target/phase65/s654/runs/live-5epmn_uo`)
passed both real MCP HTTP200 calls, full typed raw-response equality, genuine {}
omission, marker echo and strict cleanup. Its omitted summary was
`fixture-catalog=degraded`, with actual CAR index200/SAR index404; live acceptance
remained false at that attempt (VAL-P65-S654F-010). Historical249/21/unit18/live005 and earlier
failures remain evidence.

S65.4G prepares a fresh per-run `fixture-input` copy from the original fixture
tree, rejecting recursive/ancestor symlinks, existing destination and missing
nonempty regular CAR index/model metadata before fixture binding. An absent SAR
index is generated only in the copy as {"entries":[],"diagnostics":[]}: zero SAR
profiles, without schema/schemaVersion or invented component metadata. Existing
nonempty regular SAR index bytes/entries and all original fixture bytes remain.
This is catalog input to the real producer, never a canned status response.
Result, server-process and Given `fixturePreparation` expose absolute
`sourceFixtures`, `servedFixtures`, `sarIndex` and boolean `sarIndexGenerated`,
separately from source preparation. The server serves that private directory.
Before socket selection/Java acquisition, existing bounded JSON HTTP checks run
CAR then SAR with labels `fixture-car-readiness` and `fixture-sar-readiness`;
both must succeed, failure propagates, and actual receipts/raw responses remain.

Four new adjacent Given/When/Then methods specify missing-SAR copy/generation,
existing-SAR retention, named admission failures and mock-only endpoint order/
SAR failure. All twenty existing methods remain. Relevant251/14/31 and six
real-CML SDK evidence is reused. Actual [unit24](../../target/phase65/s654g-unit-result.json)
passed 24 tests with zero failures/errors, success true and runner exit0.
Actual [live007](../../target/phase65/s654/runs/live-gru8e1bo/result.json) completed
both original scenarios with owned session007 exit0, no timeout/signal/forced
termination, CAR/SAR readiness HTTP200 and both real MCP HTTP200. Private SAR
entries/diagnostics are empty; source fixtures stay read-only with provenance intact.
Exact generated marker and genuinely omitted {} requests passed full actual
typed-response equality and marker echo. Counts are source_count=5,
ready_source_count=4, component_count=1; the exact summary is simplemodeling=degraded,
fixture-catalog=ready, empty=ready, local-car=ready, cache-car=ready, retaining
overall degraded. Java28739/group28739 TERM, no KILL/errors, wait143 and two
native group-absence observations, fixture close/thread join and strict cleanup
passed. Complete submitted/native initial/terminal evidence remains after one
evidence-only continuation without rerender/replay; mistaken progress literals
were superseded and historical raw retention limits remain. Product live proof
PASS supplies the real consumer evidence for independently accepted S65.4.
All eight original checklist boxes are checked; all four original groups are CLOSED.
Sole full Phase review P65-FULL-REVIEW-001 r1 PASS and repository-full tests PASS provide the final release closure basis P65-RELEASE-CLOSURE-001 r1.

S65.4 is independently accepted and was manually committed at native
`0237579262fd8b86c7b904ecca6cb49f18fd78e1`.
P65-S654-REVIEW-001 r1 from `/root/p65_s654_review_001` returned PASS with zero
Current Boundary Blockers and one separate nonblocking Hygiene item,
HYG-P65-S654-MODELER-LOCAL-NAMING-001, recorded in the
[Hygiene follow-up](../journal/2026/10/2026-10-05-phase-65-hygiene-follow-up.md).
At S65.4 Step acceptance on 2026-10-06, the historical snapshot recorded eight review entries and FULL0. After the sole full Phase review, P65-REVIEW-LEDGER-001 revision 11 records ten entries and FULL1, before the parent's following release M0 amendment. DART-08 and DART-09
are completed in accepted S65.4; no external UI integration is claimed.
Native acceptance history records local Step commits.

- [DartPackageGenerator](../../src/main/scala/cozy/modeler/DartPackageGenerator.scala)
  enriches only its builder through the original-Section canonical input factory,
  then joins `Modeler.ModelBuilder.buildValue()` with selected canonical operations,
  reuses the accepted data projection/renderers/runtime and builds all six Realm
  paths after selected contracts and symbols validate.
- [DartCanonicalInputProjection](../../src/main/scala/cozy/modeler/DartCanonicalInputProjection.scala)
  joins exact legacy names to original DTO Sections, preserves existing VALUE
  entries, and merges only missing factory-created ValueClasses into the same
  builder consumed by data and bindings. The
  [new executable specification](../../src/test/scala/cozy/modeler/DartLegacyInputGenerationSpec.scala)
  authors this compatibility boundary and passed current focused validation.
- [DartOperationProjection](../../src/main/scala/cozy/modeler/DartOperationProjection.scala)
  retains original owner/service/operation identities, existing normalized
  definitions and canonical input/output objects. Opt-in immutable canonical
  names/ValueClass bindings in
  [ModelServiceOperationProjector](../../src/main/scala/cozy/modeler/ModelServiceOperationProjector.scala)
  preserve default two-argument behavior; the selected-service leaf helper in
  [ModelerRelationshipCml](../../src/main/scala/cozy/modeler/ModelerRelationshipCml.scala)
  retains exact source metadata ownership and duplicate diagnostics.
- [DartApiRenderer](../../src/main/scala/cozy/modeler/DartApiRenderer.scala)
  emits the typed interfaces and escaped source metadata; package admission is
  additive in [DartNaming](../../src/main/scala/cozy/modeler/DartNaming.scala).
- [ModelerServiceClass](../../src/main/scala/cozy/modeler/ModelerServiceClass.scala)
  owns the explicit strict Dart request, shared loader/charset/failure boundary
  and complete `FileRealmResponse`. Environment owns successful saving.
- [DartPackageGenerationSpec](../../src/test/scala/cozy/modeler/DartPackageGenerationSpec.scala),
  [DartPackageRuntimeSpec](../../src/test/scala/cozy/modeler/DartPackageRuntimeSpec.scala)
  and [DartModelerServiceSpec](../../src/test/scala/cozy/modeler/DartModelerServiceSpec.scala)
  author representative canonical, compiler/consumer and CLI observations for
  DA-13..19. The [CML fixture](../../src/test/resources/modeler/dart-api.cml) and
  [independent Dart consumer](../../src/test/resources/modeler/dart-api-consumer.dart)
  are representative evidence exercised in focused attempt 004 and accepted by
  independent protected Step review 001; they do not replace the frozen real CBD route.

## Final acceptance — 2026-10-06

Phase 65 is CLOSED: all nine original DART-01..09 outcomes and all eight
original checkbox texts in four original groups are satisfied, as recorded in
[Phase 65](../phase/phase-65.md) and its [checklist](../phase/phase-65-checklist.md).
The four actual native Step commits are S65.1
`f4a6e3b0f41fd929fba085664ae958330b82cff0`, S65.2
`716d208ce8505746ac03a3cc3606299f5c4e10e4`, S65.3
`770bf6b380a292c027fa059c7f50cd58dfcbd00c` and manually committed S65.4
`0237579262fd8b86c7b904ecca6cb49f18fd78e1`.

The sole full Phase review P65-FULL-REVIEW-001 r1 passed over 35 owned targets
and 58 reviewed paths, with zero Current Phase Blockers. Its retained admission
and receipt are [full-review-admission-001.json](../../.codex-workflow/phases/PHASE-65/full-review-admission-001.json)
and [full-review-receipt-001.json](../../.codex-workflow/phases/PHASE-65/full-review-receipt-001.json).
Review ledger revision 11 records ten entries/FULL1 before the parent's following
release M0 amendment; eight entries/FULL0 is the historical S65.4 Step acceptance
snapshot on 2026-10-06.

Actual repository-full `sbt --batch test` passed 3,074 tests in 211 completed
suites, with zero failures or aborted suites and nine pre-existing canceled
cases. SBT/wrapper exit 0 and `lock=released` are retained in
[release-full-validation-001.json](../../.codex-workflow/phases/PHASE-65/release-full-validation-001.json),
invocation P65-RELEASE-FULL-ATTEMPT-001. The actual summary is
`/private/tmp/skill.cncf.d/a-5604920c-ea8e-4321-80bc-8596e01d2550/34334-20261006T034510Z.summary.json`;
the corresponding log is `34334-20261006T034510Z.log` in that same directory.

Accepted product evidence remains unit24, all six real-CML SDK products and
live007's two original MCP HTTP200 calls, complete actual typed-response
roundtrips, genuine {} omission, marker echo, counts 5/4/1, CAR/SAR readiness
and strict owned cleanup. Fixture-catalog is ready; overall remains degraded.
The full review could not independently inspect the historical S65.3 raw16
SDK report files: retained native metadata and SBT logs remain, and current31
S65.4F reports plus live007 were inspected. No retrospective replacement
success is claimed. Earlier failures and original raw retention limitations
remain historical evidence. No clean rebuild, separately installed Dart 3.0,
clean upstream release/source-JAR equivalence, all-source readiness, external
UI completion, push, publication, deployment or version conversion is claimed.
The exact existing HYG-P65-S654-MODELER-LOCAL-NAMING-001 record remains in its
committed canonical [Hygiene journal](../journal/2026/10/2026-10-05-phase-65-hygiene-follow-up.md).

Closure binding is P65-RELEASE-CLOSURE-001 r1. The distinct parent manual release commit records
this closure; its native revision is recorded in Git history. The final commit
must carry `Phase-Closure-Binding: P65-RELEASE-CLOSURE-001@1`; no release SHA
is invented in this projection.
