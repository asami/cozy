# Dart model and API generation

Status: frozen Phase 65 contract; implementation and independent acceptance pending
Date: 2026-10-05
Scope: Cozy's Dart model, JSON and operation projection

This specification defines the public generated contract for
[Phase 65](../phase/phase-65.md). The twelve numbered sections retain the parent
contract's decision numbering. They describe required behavior, not an existing
Dart implementation. The [design](../design/dart-model-api-generation.md)
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

Wire object keys are the original source field names, even when Dart identifiers
are normalized or escaped. `toJson` emits optional null explicitly and emits
lists as arrays. Unknown object fields are ignored for additive producer
evolution; unknown finite variant names are rejected. Optional missing and
explicit-null inputs both represent the same null state.

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
| Domain value with multiple fields | Named object class | Object with source keys |
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
| JSON field key or finite kind discriminator | Exact source spelling | No Dart keyword substitution |

Normalize deterministically and reject symbol/path collisions before emission,
including field/method/variant collisions and distinct qualified source types
that normalize to the same public Dart type. Do not silently select one source
or rename away its identity. Keyword escaping participates in collision checks.
Scala implementation helpers follow repository naming rules, including private
member `_snake_case`; Dart conventions do not override Scala authoring rules.

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

The proposed entry, to be implemented in Cozy's
[ModelerServiceClass](../../src/main/scala/cozy/modeler/ModelerServiceClass.scala), is:

```text
cozy modeler dart <model-file> --package=<dart_package> --save=<output-dir>
  [--types=<comma-separated-qualified-types>]
  [--operations=<comma-separated-component.service.operation-identities>]
  [--sealed-types=<comma-separated-qualified-finite-powertypes>]
  [--charset=UTF-8]
```

The model file, package name and output directory are required. Validate the
package as a Dart pub package identifier using the snake_case convention.
Charset defaults to UTF-8 and uses the existing model file parsing boundary.
Invalid package names, missing arguments, invalid charsets, unreadable or
malformed files and invalid selected contracts return structured failure.

Validate every selected contract and construct the whole output `Realm` before
returning success. Invalid input must not write a partial package. `--save`
uses existing `Environment`/response-owned file writing after the successful
typed file-realm response; the generator/facade must not write individual files
as it validates them.

The entry does not yet exist. Existing `modeler class` and `modeler project`
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

Every row below is a required future executable acceptance scenario. None is
claimed executed by this documentation Slice. Later executable specifications
use `AnyWordSpec`, `Matchers` and `GivenWhenThen` with clauses at the actual
setup/action/assertion boundaries; compilation/analyzer/consumer execution must
exercise emitted packages. Planned source/test paths are to be frozen in later
Step manifests, not represented here as existing files or evidence links.

| ID / Step | Given | When | Required observable result |
| --- | --- | --- | --- |
| DA-01 / S65.2 | Admitted scalar, wrapper, structured, entity and typed-reference shapes | Generate and compile | Typed immutable classes; source identity and fields preserved |
| DA-02 / S65.2 | Missing/ambiguous selected root or unresolved selected reference | Generate | Structured failure; no partial output |
| DA-03 / S65.2 | Unsupported scalar/range/relationship/trait/inheritance or ambiguous inherited field | Generate | Explicit failure; no map fallback or omitted selected field |
| DA-04 / S65.2 | Required and optional fields, including explicit null | Construct and decode missing/null inputs | Required missing/null rejected; optional absent/null retained as null and emitted explicitly |
| DA-05 / S65.2 | `*`/`+` lists and a mutable supplied list | Decode and mutate lists | Absent `*` becomes empty; absent/null/empty `+` rejected; list storage remains immutable and copied |
| DA-06 / S65.2 | Admitted/narrow integral limits, fractional numbers and nonfinite floating input | Construct/encode/decode | Safe integers and declared ranges retained; invalid numeric forms rejected |
| DA-07 / S65.2 | Sole required primitive `value`, sole differently named field and multi-field objects | Round-trip JSON | Named wrapper uses scalar; other shapes use objects; wire keys unchanged |
| DA-08 / S65.2 | Finite powertype with name/value/label | Generate default enum; compile exhaustive switch; decode unknown name | Exhaustiveness demonstrated; exact name wire; metadata retained; unknown name rejected |
| DA-09 / S65.2 | Same finite powertype selected as sealed | Generate final unit alternatives and compile exhaustive switch | Same finite wire semantics; no added payload or discriminator field |
| DA-10 / S65.2 | Open classification and unknown name | Decode and try sealed selection | Typed extensible wrapper accepts name; known metadata available; sealed selection fails |
| DA-11 / S65.2 | Empty finite set, duplicate kind names, normalized type/field/variant/path collision | Generate | Structured failure; no arbitrary winner or identity renaming |
| DA-12 / S65.2 | Object with unknown additive fields and nested typed values | Decode/re-encode | Known typed state round-trips; unknown fields ignored |
| DA-13 / S65.3 | Identical input/options; selected closure with unrelated unselected unsupported type | Generate repeatedly | Identical sorted UTF-8/LF paths/content; unselected type does not contaminate closure |
| DA-14 / S65.3 | Shared canonical input/output/error DTO identities and source void | Generate and independently compile typed interfaces | DTO reused once; typed Future signatures and void retained; no synthetic envelope |
| DA-15 / S65.3 | Missing DTO metadata or ambiguous operation ownership | Generate | Structured failure; no inferred DTO from Scala body |
| DA-16 / S65.3 | Keyword identifiers and original source wire names | Generate, compile and round-trip | Stable escapes compile; wire keys and discriminators retain source spelling |
| DA-17 / S65.3 | Complete generated SDK-only package | Analyze and import barrel from a separate package | Analyzer-clean output and successful independent consumer compilation |
| DA-18 / S65.3 | Missing CLI arguments, invalid package/charset/file or invalid selected contract | Invoke proposed entry | Structured failure before success/file writing; no partial package |
| DA-19 / S65.3 | Existing class/project, direct Scala and Cncf/Library cases | Run focused compatibility specifications | Existing targets and response/failure contracts preserved |
| DA-20 / S65.4 | SDK generated from the real CBD status CML and actual running CBD | Send encoded request through tools/call; compile/run independent Dart consumer | Named wrappers/counts decoded from live response and re-encoded; no shadow model |
| DA-21 / S65.4 | Source-copy build, launcher or actual wire failure | Attempt real acceptance route | DART-08 remains unresolved; no fixture/canned-output substitute or success claim |

Existing evidence is source inspection only: canonical builder/projector symbols,
the current class/project facade, the real CBD CML fields and
`ComponentFactory.StatusActionCallImpl` plus the existing MCP envelope script.
Generated Dart, analyzer results, round trips, compatibility execution and live
consumer results remain planned evidence. The design records unexecuted runtime
prerequisites and reproducible evidence requirements.
