# Dart model and API generation design

Status: CLOSED; frozen Phase 65 design; four native Steps accepted, sole full Phase review PASS, repository-full tests PASS; closure binding P65-RELEASE-CLOSURE-001 r1
Date: 2026-10-06
Scope: canonical source inventory and Cozy projection/consumer responsibilities

The [specification](../spec/dart-model-api-generation.md) owns public type, wire,
failure and CLI behavior. This document records the stable rationale and source
boundaries for that contract. Progress and closure belong to
[Phase 65](../phase/phase-65.md) and its
[checklist](../phase/phase-65-checklist.md), under the Core Rule Map in
[AGENTS.md](../../AGENTS.md), [RULE.md](../../RULE.md) and
[document lifecycle](../../ai/directive/core/document-lifecycle.md).

## Canonical source inventory

The following are actual source symbols, not proposed Dart implementation paths.
Cozy is the only mutable product owner. SimpleModeler, Kaleidox and Textus CBD
Support are read-only source evidence. Repository-relative external identities
remain meaningful across checkout environments; local locators below identify
the checkouts inspected for this contract and do not redefine shared links.

| Source / symbol | Existing responsibility | Dart design consequence |
| --- | --- | --- |
| [Modeler.ModelBuilder](../../src/main/scala/cozy/modeler/Modeler.scala), `buildValue`, `_build` | Builds `SimpleModel` from entities, values, inline service values, datatypes, powertypes and state machines; `buildValue` excludes component supplementation | Reuse canonical data; do not assume it contains typed service client signatures. |
| [ModelTypeProjector](../../src/main/scala/cozy/modeler/ModelTypeProjector.scala), `projectEntity`, `projectValue`, `projectDatatype`, `projectPowertype` | Produces `MEntity`, `MDomainValue`, `MNominalDataType`, `MStructuredDataType`, `MPowertype`; resolves source fields and constraints | Projection consumes these identities and metadata; unsupported selected semantics fail. |
| `ModelTypeProjector.serviceInlineValues` | Carries operation-local value definitions and rejects duplicate/conflicting definitions | Reuse these DTOs; no independently authored operation DTO schema. |
| [ModelBuildContext](../../src/main/scala/cozy/modeler/ModelBuildContext.scala) and `Modeler.ModelBuilder` | Retain source service/component and operation model context | Source ownership accompanies normalized operations. |
| [ModelServiceOperationProjector](../../src/main/scala/cozy/modeler/ModelServiceOperationProjector.scala), `normalizedServiceOperationDefinitions`, `_normalized_service_operation`, `_merge_service_operation_metadata` | Retains `OperationModel.NormalizedOperationDefinition`, declared input/output types, kind, parameters and source semantic metadata | Combine these canonical definitions with source ownership; preserve exact DTO identity. |
| `simple-modeler:src/main/scala/org/simplemodeling/model/SimpleModel.scala`, `elements`, `getObject`, `getPowertype`, `takeDerivedObjects` | Resolves canonical objects; takeDerivedObjects returns matching elements' resolved base objects, not the child elements | Use canonical resolution for dependencies; this helper does not enumerate children or establish a closed union. |
| `simple-modeler:src/main/scala/org/simplemodeling/model/MOperation.scala`, `Core` | Server execution descriptor, parameters, result, optional body and access | Record-based execution shape alone cannot determine declared client DTO identity. |
| `simple-modeler:src/main/scala/org/simplemodeling/model/MPowertype.scala`, `kinds`, `isKnowledge` | Explicit finite kinds plus knowledge/open classification flag | Finite non-knowledge kinds permit enum/sealed unit projection; knowledge remains extensible. |
| `simple-modeler:src/main/scala/org/simplemodeling/model/MPowertypeKind.scala`, `name`, `value`, `labelText`, `label` | Retains kind identity, optional value and label with name fallback | Exact name is the wire discriminator; value/label remain metadata. |
| `kaleidox:src/main/scala/org/goldenport/kaleidox/model/PowertypeModel.scala`, `PowertypeClass`, `PowertypeKind` | CML names, optional integer values and labels | Cozy `_powertype` preserves values as optional string metadata in `MPowertypeKind`; no numeric discriminator inference. |
| `kaleidox:src/main/scala/org/goldenport/kaleidox/model/OperationModel.scala`, `NormalizedOperationDefinition`, `FieldDefinition` | Canonical DTO names, operation kind, field multiplicity, constraints/typeConstraints and source-declared operation metadata | Preserve canonical DTO/field semantics without inventing HTTP/domain policy. |
| [ModelerServiceClass](../../src/main/scala/cozy/modeler/ModelerServiceClass.scala), `_load_model`, `_parse_model`, `_apply`, `ClassOperationClass`, `ProjectOperationClass` | Existing file/charset loader, class/project facade and structured response failure boundary | New dart operation belongs here in Cozy; no new external Kaleidox function is necessary. |
| [ModelGenerationTarget](../../src/main/scala/cozy/modeler/ModelGenerationTarget.scala), `Cncf`, `Library`; [ScalaGenerator](../../src/main/scala/cozy/modeler/ScalaGenerator.scala) | Existing Scala/CNCF/library generation choices and restrictions | Preserve these target contracts; Dart is a separate Cozy facade operation. |

Verified local read-only upstream locators are
`/Users/asami/src/dev2026/simple-modeler` and
`/Users/asami/src/dev2026/kaleidox`. Their repository-relative source identities
above are the stable evidence references. No external source change is part of
this design. `ModelTypeProjector._datatype_attributes` currently rejects nested
complex datatypes; source support must not be invented by rendering them loosely.

## Data and operation projection boundary

The data side is `SimpleModel`; the operation side is the already normalized
Kaleidox operation definition plus its component/service ownership. They serve
different responsibilities. A transient rendering view can join exact qualified
identities, index dependencies and sort output. It cannot author replacement
DTO definitions or become a persistent Dart semantic model.

The current normalized-definition vector is exposed inside the modeler package;
the source service/component context retains the ownership needed to distinguish
`component.service.operation`. The authored Cozy operation projection joins
these existing sources using explicit component service declarations or the
canonical single-component implicit ownership boundary.
Server `MOperation` bodies, generated Scala text, sample JSON and client-local
models are not substitutes for canonical DTO metadata.

Selection traverses the requested data and operation dependency closure before
rendering. Each reference resolves against exact source identity, and only
selected contracts/dependencies participate in Dart support validation. Missing,
ambiguous or unsupported selected contracts fail before successful Realm output.
Existing source parsing and canonical-build failures remain failures; selection
does not promise to bypass the canonical model's own admission rules.

## Dart-local original input source bridge

[DartCanonicalInputProjection](../../src/main/scala/cozy/modeler/DartCanonicalInputProjection.scala)
fills the missing canonical data edge for original top-level QUERY/COMMAND DTOs.
The existing legacy inventory contains operation fields and kind but is not an
authored DTO schema. Typed `KaleidoxModel.OperationDivision` objects retain the
original LogicalSections. Parsing each with
`Dox2Parser.parse(model.config.doxConfig, section)` supplies the unchanged Dox
tree used by `OperationModel.create`. Its structural walk takes immediate
QUERY/COMMAND DTO children, stops at OPERATION Sections, and traverses other
Sections/elements without treating ATTRIBUTE descendants or operation markers
as declarations.

Inventory names are grouped before indexing: duplicate exact legacy names fail
with deterministic source diagnostics, even when their package properties
differ. A missing ordinary VALUE must join exactly one original DTO Section by
`Section.nameForModel.trim`. The adapter delegates to the existing
`ValueModel.ValueClass.create(originalSection)` source factory, preserving
canonical schema, package/package-name, properties and source location. It
never constructs fields from legacy parameters, wire data, Scala text or a
replacement CML. Missing/ambiguous Sections and unsuccessful factories reject
the named input; empty objects require canonical factory/generator admission.

Only missing factory-created classes merge into a copy of `builder.value`;
its description and existing entries survive. An existing VALUE's schema,
kind properties, package and location stay authoritative for rendering.
Legacy input kind/field and explicit parameter precedence remain in the
existing normalizer. Ordinary VALUE/local VALUE conflict checks remain in
`ModelTypeProjector`. Its existing component-package `.value` fallback and
explicit package rules establish identity. Both `buildValue` data and
`DartOperationProjection` bindings consume the same enriched builder; exact
qualified references, ambiguous names and mismatched kinds retain their
existing rejection rules. No operation-kind property is injected.

Enrichment is invoked only in `DartPackageGenerator.generate`. The original
ModelBuilder, direct data generator and Scala/CNCF/library contracts are
unchanged. The
[legacy-input executable specification](../../src/test/scala/cozy/modeler/DartLegacyInputGenerationSpec.scala)
authors actual parsed models/public package generation, codec/identity and
compatibility observations plus a ScalaCheck property varying kinds, input
spellings, field multiplicity and independent source order. Current parent
S65.4B validation passed all 13 suites with 217/217 tests and sixteen SDK command
reports, zero SBT/wrapper exits and `lock=released`; fresh real CBD SDK generation
passed with all six products. This suite is not live evidence.

## Classification closure and wire continuity

`SimpleModel.takeDerivedObjects` returns each matching element's resolved
base object rather than the child element. It therefore does not enumerate
direct children. Ordinary inheritance also does not declare that future
children are impossible and cannot justify a sealed object hierarchy. The
bounded classification source is instead `MPowertype.kinds` with
`isKnowledge == false`; it represents finite unit alternatives. The sealed
option changes only their Dart representation.

Knowledge/open classifications remain typed string wrappers with known metadata
and extension names. CML powertype numeric values and labels describe kinds;
they do not replace exact source names on the wire. General payload unions
require a canonical definition that actually supplies exhaustive payload cases;
the current sources do not provide one. No descendant scan fabricates that IR.

Immutable typed state and explicit scalar-wrapper rules preserve model identity
across Scala/server and Dart/client representations. A named wrapper need not
be a JSON object: the sole required primitive field named `value` encodes as a
scalar. This is necessary for the real CBD status wire shape. Unknown additive
object fields are ignored while finite classification membership stays strict.
These are stable public behaviors in the specification, not consumer heuristics.

## Cozy facade, output and failure ownership

The authored `cozy modeler dart` entry extends the existing Cozy modeler service
file/charset parsing and structured failure boundary, alongside class/project.
Its package renderer constructs a complete Realm after
selection, support, numeric, symbol/path and DTO validation; response-owned
file writing then handles `--save`. This keeps invalid contracts from emitting
a partial successful package.

Canonical modeled operation errors project through the same data generator.
Technical JSON decoding exceptions remain distinct from modeled failures.
Cozy generation/loader failures remain structured response failures. Consumer
transport owns MCP/HTTP mechanics and application policy. No transport policy,
authorization or rich Phase 54 validation is inferred from source names.

Compatibility covers class/project, direct modeler-scala/modeler-scala-value
and `ModelGenerationTarget.Cncf/Library`, with unchanged Scala 2.12.18 and Cozy
0.3.3-SNAPSHOT during this Slice. S65.3A implementation/source correspondence is
recorded below; focused validation attempt 004 passed and independent protected
Step review 001 accepted the complete Step.

## Authored S65.2/S65.3 source correspondence

S65.2 is accepted at native `716d208ce8505746ac03a3cc3606299f5c4e10e4`, with the
accepted data generator, projection, renderer and technical JSON runtime. Its
two executable suites passed 21/21 tests in attempt 006 with actual emitted Dart
SDK/runtime/compiler evidence and independent focused re-review 006.

[DartPackageGenerator](../../src/main/scala/cozy/modeler/DartPackageGenerator.scala)
is the pure complete-package entry. It uses the Dart-local original input bridge
before canonical `buildValue` objects and
the accepted data projection for selected closure, rendering and wire codecs.
[DartOperationProjection](../../src/main/scala/cozy/modeler/DartOperationProjection.scala)
joins original owner/service identities, normalized canonical definitions and
resolved canonical DTO objects. Its view contains no authored field schema.
The optional immutable canonical binding in
[ModelServiceOperationProjector](../../src/main/scala/cozy/modeler/ModelServiceOperationProjector.scala)
admits qualified identities and retains original Value input-kind/field
semantics. Existing callers use the unchanged default branch. Temporary
selected-service builders use the additive exact-service leaf selector in
[ModelerRelationshipCml](../../src/main/scala/cozy/modeler/ModelerRelationshipCml.scala),
preserving direct/section conflict checks and selected duplicate diagnostics.

[DartApiRenderer](../../src/main/scala/cozy/modeler/DartApiRenderer.scala) owns
typed abstract interfaces, source metadata and API/public symbol admission.
[DartNaming](../../src/main/scala/cozy/modeler/DartNaming.scala) adds exact pub
package admission while preserving existing type/member naming and wire codecs.
[ModelerServiceClass.DartOperationClass](../../src/main/scala/cozy/modeler/ModelerServiceClass.scala)
uses its own strict typed request, the existing loader/charset and Conclusion
boundary, and complete response-owned saving; it creates no generation-side
filesystem output or external Kaleidox function.

The [package-generation specification](../../src/test/scala/cozy/modeler/DartPackageGenerationSpec.scala)
authors canonical selection, kind, ownership, namespace and determinism
observations. The [runtime specification](../../src/test/scala/cozy/modeler/DartPackageRuntimeSpec.scala)
authors offline direct-SDK analysis and independent positive/negative consumer
observations, with task-owned command/timeout receipts. The
[CLI specification](../../src/test/scala/cozy/modeler/DartModelerServiceSpec.scala)
authors actual typed CLI response/saved-Realm and failure observations. Their
[representative CML](../../src/test/resources/modeler/dart-api.cml) and
[independent consumer](../../src/test/resources/modeler/dart-api-consumer.dart)
contain canonical shared/error/inline/void contracts and generated typed use.
Focused validation attempt 004 passed Main and Test compilation and all twelve
suites: 195/195 tests succeeded, with zero failures or
aborted/canceled/ignored/pending tests, SBT/wrapper exit 0 and the serial lock
released. Actual SDK evidence contains sixteen direct command reports across
the typed positive consumer, intended negative type diagnostics and all-void
package scenarios; the negative analyzer was intentionally rejected. Dart SDK
3.13.4 exercised `@dart=3.0` source mode; no separately installed Dart 3.0 SDK is
claimed. The first complete independent protected Step review 001 returned
PASS, with no Current Boundary Blockers, Hygiene or Development Candidates.
It accepted the malformed canonical-model error guard and valid-empty/CLI
conformance. S65.3 is independently accepted at native
`770bf6b380a292c027fa059c7f50cd58dfcbd00c`.
Historically, the two remaining real CBD/Phase closure
checklist boxes stayed unchecked at S65.3; now all eight original boxes are checked and
all four groups and Phase 65 are CLOSED. At S65.3 acceptance, successful live real CBD/S65.4
proof remained pending; actual S65.4G [live007 product proof](../../target/phase65/s654/runs/live-gru8e1bo/result.json)
subsequently passed. S65.4 is independently accepted and was manually committed at native
`0237579262fd8b86c7b904ecca6cb49f18fd78e1`.
Sole full Phase review P65-FULL-REVIEW-001 r1 PASS and repository-full tests PASS provide the final release closure basis P65-RELEASE-CLOSURE-001 r1.
The S65.4 route below records the real consumer
obligation and its evidence.

## Real CBD source and runtime producer

Canonical consumer evidence is
`textus-cbd-support:src/main/cozy/textus-cbd-support.cml`, inspected at
`/Users/asami/src/dev2026/textus-cbd-support/src/main/cozy/textus-cbd-support.cml`.
The source component is `CbdSupport`, package
`org.simplemodeling.textus.cbdsupport`, service `CbdRetrieval`, operation
`status`, kind `QUERY`, input `CbdStatusRequest`, output `CbdStatusResponse`.

| Source declaration | Actual fields |
| --- | --- |
| `CbdStatusRequest` | `detail:CbdStatusDetail:?` |
| `CbdStatusResponse` | `overall:CbdResultStatus:1`, `sourceCount:int:1`, `readySourceCount:int:1`, `componentCount:int:1`, `detail:CbdStatusDetail:?` |
| `CbdResultStatus` | sole required `value:string:1` |
| `CbdStatusDetail` | sole required `value:string:1` |

The runtime producer is
`textus-cbd-support:src/main/scala/org/simplemodeling/textus/cbdsupport/impl/ComponentFactory.scala`,
`StatusActionCallImpl.build_Program` (around line 1466), inspected in that same
read-only checkout. It calls `runtime.ensureInputsReady`, emits
`runtime.overallStatus` as scalar `overall`, three integer counts and optional
scalar `detail` from the request or source-state summary. The status/detail
domain values are open strings; runtime spellings do not define an enum.
`CbdHttp` supplies an outbound provider client used by readiness work; it is not
the incoming CBD service route.

The real incoming route is documented by
`textus-cbd-support:scripts/run-mcp-tool.sh`. It posts a JSON-RPC `tools/call`
envelope to the configured MCP endpoint with
`params.name = "org.simplemodeling.textus.CbdSupport.CbdRetrieval.status"` and typed request JSON as
`params.arguments`. It distinguishes protocol/tool error results and extracts
the successful tool payload. The generated SDK does not own this envelope.

## Frozen consumer acceptance route

The acceptance client is owned by Cozy and imports the SDK generated from the
real read-only CBD CML. It contains no second request/response/domain model.
A tiny injected transport boundary obtains actual tools/call output; the
example implements/exercises the generated operation interface and uses only
generated codecs for request encoding and response decoding/re-encoding.
An independent Dart package must compile and run, access the named
`CbdResultStatus`/`CbdStatusDetail` wrappers and three count fields, and round-trip
a live real CBD response. Static emitted text, toy fixtures and copied JSON
cannot satisfy DART-08.

Runtime preparation is an admitted S65.4 operation. Its source snapshot
lives in Cozy's ignored `target/phase65/cbd-runtime-source`, with native source
revision and dirty-source attribution retained. Copy no `.git`, caches,
credentials or target trees. Build products remain in Cozy-owned scratch.
The CBD checkout remains read-only; its CAR is not published to a shared
warehouse. The derived project build uses the registered serialized SBT runner
and normal caches, with a reviewed task-owned launcher/configuration pointing
generation to the current Cozy build. Only configuration necessary to locate
that compiled Cozy executable is overridden; source semantics, dependency
versions and compatibility declarations are preserved.

The runtime uses the declared CNCF 0.5.3-SNAPSHOT main class through the existing
launcher/normal resolved classpath, the resulting CBD CAR, a Cozy-owned canonical
JSON SAR derived from its validated real identity, isolated task-owned resource trees, loopback catalog fixture
and a free task-owned loopback port. Evidence sources are
`textus-cbd-support:examples/cbd-standalone-sar/subsystem-descriptor.yaml`,
`examples/cbd-standalone-sar/README.md`, `examples/cbd-sie-sar/fixtures` and
`scripts/check-cbd-standalone.sh` in the verified checkout.

The first live attempt packaged the copied YAML descriptor unchanged and failed
before readiness on its legacy component/coordinate binding. Its local subject
already existed inside the rejected SAR; a subsequent execution-guard message
is not independent proof of an authentication defect. Preserve that original
descriptor/SAR and failed run read-only. The selected CNCF descriptor bytecode
admits `subsystem-descriptor.json`, requires namespace/id/version, and reads
`authentication.local_subject`; its subsystem bytecode maps standalone plus that
subject to Fixed. These observations establish the private fixture contract;
they do not claim source-byte equivalence for the SNAPSHOT JAR.

Adapt the reviewed lifecycle rather than invoking `check-cbd-standalone.sh`
unchanged: it embeds direct SBT and removes evidence. Retain logs/results and
stop only task-started processes. Runtime/session and SBT commands use their
registered runners after exact S65.4 command/script admission. The catalog
fixture is input to the real CBD runtime, not a fake status response producer.
No SIE source, remote production service or external source edit is required.

## Authored S65.4A consumer and lifecycle correspondence

The [independent consumer](../../examples/dart-cbd-consumer/consumer/bin/contract.dart)
implements only the generated `CbdSupportCbdRetrievalApi` contract through a
`Future<Map<String, Object?>> Function(Map<String, Object?>)` injection. Request
mode emits generated `CbdStatusRequest.toJsonOmittingNullFields`; response mode reconstructs the
same typed request, captures its encoded injection input and consumes the
current extracted live payload through `CbdStatusResponse.fromJson`. Its
report exposes full typed re-encoding, named wrapper values and integer counts.
Only the adjacent generated SDK is a
[package dependency](../../examples/dart-cbd-consumer/consumer/pubspec.yaml).

The [preparation script](../../scripts/test/phase65/prepare-cbd-consumer.py) requires
fresh exact ignored destinations, rejects copied symlinks/escaping paths and
copies actual selected tracked files while excluding output/cache/credential
trees. `preparation.json` records observed source HEAD, full porcelain dirt,
selected paths and exact derived locations. It does not assert immutability.
Only separate top-level copied-build settings point the existing Cozy launcher
to current compiled Cozy `0.3.3-SNAPSHOT` and add `phase65WriteCncfClasspath`.
That task exports `Runtime / externalDependencyClasspath`, requires regular dependency
JARs and exactly the declared CNCF `0.5.3-SNAPSHOT`, and excludes copied CBD
classes. Preparation executes no build or generator.

The [live script](../../scripts/test/phase65/run-cbd-consumer.py) consumes the actual
CAR, readable original standalone descriptor as provenance, catalog fixtures and
dependency-only classpath. Before Java/fixture acquisition it reads the CAR's
`component-descriptor.json`, requires schemaVersion integer 3 (excluding bool)
and exact `namespace=org.simplemodeling.textus`, `id=CbdSupport`,
`version=0.1.0-SNAPSHOT`. Missing/malformed/legacy-only/conflicting identity raises
a named admission failure without guessed fallback. It writes
`run/subsystem-descriptor.json` and exclusively creates
`run/component.d/textus-cbd-standalone.sar` with that sole canonical JSON entry.
The CAR is copied unchanged; no original descriptor is rewritten.

The private descriptor retains `convention=disabled`,
`fallback_privilege=disabled` and local subject `cbd-standalone-review`, role
`reviewer`, security level `user`, installation `cbd-standalone`.
`textus.subsystem.user-mode=standalone` is explicit alongside the unchanged
`textus.subsystem=textus-cbd-standalone`. A derivation receipt records selected
CAR/original descriptor locators, canonical identity, generated JSON/SAR paths
and that existing fixed-user policy in `server-process.json` and run observations,
together with effective mode. No administrator escalation, runtime test
descriptor, `textus.test=true` or authentication-bypass flag is introduced.
The [behavioral regression](../../scripts/test/phase65/test_run_cbd_consumer.py)
imports the harness safely and observes admission and decoded SAR contents with
owned temporary inputs under `target/phase65`. Its `s654c-unit-result.json`
records actual unittest counts/status when the parent runs it; it is separate
from real MCP evidence. Actual execution passed 12/12 with zero failures/errors.

The live script continues to observe the exact runtime version and owns a fresh Java process
group, fixture thread, loopback ports, isolated product home/resource trees
and retained run directory. Readiness requires the child-only successful Ember
bind observation from `goldenport-cncf:Http4sHttpServer`, actual OpenAPI and the
exact MCP status tool. The post-bind log line establishes that the owned child
bound the selected endpoint; a racing listener cannot supply that observation.
Explicit supported `textus.test.home.*`, dependency-cache and port-assignment
configuration keep product state in the run directory while normal build
dependency caches remain intact.

Offline pub resolution/strict analysis and both explicit/omitted detail
scenarios retain bounded command receipts. Given actual source/artifacts/runtime,
When generated request JSON travels through live `tools/call` and the generated
interface consumes its actual text payload, Then captured request/full JSON
roundtrip/wrapper/count/detail observations must match. The terminal result
separates live proof, process completion and cleanup; its exact printed run
locator and pointer identify the invocation. Signals/failure stop only the
owned Java group with bounded TERM/KILL, shut down/join the fixture server and
retain all evidence. Failure leaves DA-21/DART-08 unresolved without substituted
success. The first attempt failed before readiness with zero scenarios and
complete owned cleanup. Later namespaced live006 passed both typed roundtrips
but failed fixture-catalog readiness; independent S65.4 acceptance remained pending
at that historical attempt. The
[README](../../examples/dart-cbd-consumer/README.md) gives the native runner
sequence and later UI generated-contract reuse without asserting UI integration.

## Prerequisites and evidence limits

Parent-recorded environment observations for this contract are that Dart SDK
3.13.4 is available inside the Flutter SDK, the CBD standalone descriptor,
fixtures and scripts exist, and local Ivy contains goldenport-cncf_3
0.5.3-SNAPSHOT, the runtime declared/tested by CBD `project.yaml`. Future Dart
validation uses the direct `dart-sdk/bin` executable without Flutter upgrades,
installation or new package dependencies.

The initial S65.4A inventory lacked the target CBD CAR, runtime classpath and
default `cncf` executable. Historical preparation first failed because
`Runtime / dependencyClasspath` included a copied-project classes directory;
that failed record is retained. The separately repaired private export now uses
`Runtime / externalDependencyClasspath`. Private CAR generation and export of
111 dependency JARs, including the declared CNCF runtime, passed. The original
preparation's raw-submission gap remains an evidence limitation.

Actual Dart generation from the unchanged copied CBD CML then failed: the
legacy QUERY `CbdStatusRequest` did not appear in canonical data projection.
An earlier `newSpec` compilation failed and remains historical evidence.
S65.4B's original-Section canonical factory bridge subsequently passed current
focused validation: 13 suites, 217/217 tests, sixteen SDK command reports,
zero SBT/wrapper exits and `lock=released`. Fresh generation from the unchanged
real CBD source passed with all six SDK products. The first live attempt failed
before readiness, executed zero scenarios and completed owned cleanup.
S65.4C's CAR/SAR behavioral regression passed 12/12 with zero failures/errors in
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
analysis on generated `Object?` encoding at the typed transport boundary.
Zero scenarios ran. TERM then `Operation not permitted` produced cleanupfalse;
the failing syscall is unknown. Later scoped probes observed PID/group 9834
absent, separately from the failed cleanup receipt, without establishing cause.
Failed runs and raw-retention/disclosure limits remain intact. Subsequent S65.4D
unit18/live004 prerequisite success and remaining decode failure are recorded below.
Source-copy build/launcher failures or actual wire mismatches leave
Phase acceptance unresolved and cannot defer or replace DART-08. At that historical
attempt, independent S65.4 Step review/commit, repository-full validation, the sole
full Phase review and final release remained separate outstanding obligations.

S65.4 evidence must retain actual selected source/artifact identities, command
receipts, server readiness, live status result and independent Dart analyzer/run
output. It must demonstrate typed sourceCount/readySourceCount/componentCount
against actual source_count/ready_source_count/component_count and scalar
overall/detail in the live payload, encoded request use and named typed
decode/re-encode behavior. Later CBD UI work reuses this generated SDK rather
than authoring a parallel model/API layer.

## Authored S65.4D consumer and owned cleanup

The consumer keeps the admitted generated `Object?` codec and typed transport
by assigning encoding to local final `encoded`, rejecting a non-object with
`FormatException('Generated CBD request must be a JSON object')` and passing
the promoted `Map<String, Object?>` to the existing transport/response decoder.
It preserves generated wrapper/int types without a cast or shadow model.

After canonical CAR/SAR admission and before Java version/fixture acquisition,
`prepare_consumer` copies only the two current authored pubspec/bin source files
into the exact Cozy `target/phase65/s654/consumer` workspace. Safe-path/regular
admission covers inputs, outputs and ancestors. Only consumer/bin directories
are created; existing outputs must be regular. Original `sdk`, `.dart_tool`, old runs,
source snapshot and original preparation remain intact. Actual two-file
source/destination locators populate `consumerPreparation` in result/Given
observations; old preparation describes source provenance rather than refresh.

Native ownership remains Popen/start_new_session. Exact group observation uses
the selected macOS `/bin/ps -g <group> -o pid=,pgid=` contract with no shell,
capture/text, timeout2s, preserved PATH and `LC_ALL=C`; Linux universality is
not claimed. Every query retains argv/start/finish/exit/stdout/stderr or native
timeout/OSError. Exit1 with empty streams means absent; exit0 with empty stderr
requires nonempty two-positive-integer rows all in the owned pgid. Malformed,
mismatched, stderr, other exits and observation errors fail by name, never by
inventing absence. Query rows never provide signal targets.

Cleanup always attempts TERM on the native owned group; 15s grace polls/reaps
the leader and observes that group. Poll/query errors are retained and leave
grace, followed by KILL if absence was not observed, always wait10s, then final
absence5s. A failed final query stops that loop; later success cannot erase
earlier errors. ESRCH records a signal race rather than observed groupGone.
Success requires native wait exit, observed groupGone and no stage/errors,
with fixture shutdown/close/join retained. There is no killpg(group,0) query,
unrelated PID discovery or permission recovery.

The behavioral specification retains all twelve CAR/SAR methods and authors
six methods: empty-group absence; owned leader/child or child-only presence;
malformed/mismatched/unexpected query failure; native timeout/permission evidence;
cleanup signal denial/query failure and mandatory wait; current two-file refresh
with retained output/provenance and source/destination symlink rejection.
Adjacent Given/When/Then and native mocks isolate these target-owned fixtures
from actual live proof. The next unit execution writes actual counts/status to
fresh `s654d-unit-result.json`, fails on no tests and retains the unit12 receipt.
Actual execution passed all 18 methods; live004 passed SDK/consumer pub/analysis,
first MCP200 and strict cleanup, then failed decoding before a complete scenario.
At S65.4D, two real typed roundtrips remained pending; live006 later passed
both, while fixture-catalog readiness and independent Step/Phase gates remained
open at that historical attempt.

## Explicit source-derived JSON field profile (S65.4E)

The selected CNCF 0.5.3-SNAPSHOT `OperationResponseFormatter._output_record`
calls `RuntimeContext.Context.transformRecord`; its default property naming is
SnakeCase. Formatter format/shape configuration supplies no property-name option.
Selected naming bytecode first normalizes separators/camel case, then selected
`StringUtils.camelToSnake` partitions before each uppercase ASCII character.
Thus acronyms differ from `DartNaming.fileName`: `URLValue` becomes `u_r_l_value`.
The frozen [public profile contract](../spec/dart-model-api-generation.md)
defines the exact deterministic `Locale.ROOT` transformation and strict parser.

[DartJsonFieldStyle](../../src/main/scala/cozy/modeler/DartJsonFieldStyle.scala)
owns sealed Scala 2 `Source` (`source`) and `CncfSnakeCase` (`cncf-snake-case`).
Both generator Options append typed `jsonFieldStyle` with Source default.
Package selection forwards it into the unchanged canonical closure. Projection
stores original objects, computes `wireName(owner, field)` separately from Dart
field/type naming and checks per-object transformed-key collisions before existing
symbol admission. Renderer uses that name for field encode/decode/metadata only.
Canonical identity/type/multiplicity/constraints, scalar wrappers, enum/sealed/open
discriminator values, signatures and runtime checks retain their source contracts.
The package README names the policy. This uses no destructive attribute copy,
adapter, payload normalization or second domain projection.

Only the Dart CLI request admits `--json-field-style`; omission/explicit Source
preserve source keys, both property syntaxes work and invalid identifiers fail
through structured ConclusionResponse before model loading/saving. Class/project
request surfaces and selector/charset/strict parsing remain intact. The
[profile specification](../../src/test/scala/cozy/modeler/DartJsonFieldStyleSpec.scala),
[CLI scenarios](../../src/test/scala/cozy/modeler/DartModelerServiceSpec.scala) and
[independent runtime consumer](../../src/test/scala/cozy/modeler/DartPackageRuntimeSpec.scala)
author DA-22 compatibility, exact naming, canonical closure, collision and
determinism observations with Given/When/Then. Runtime fixtures exercise actual
generated constructors/codecs and separate offline SDK/consumer analysis; they
cannot replace real CBD proof.

Actual live004 [result](../../target/phase65/s654/runs/live-nm1w2a4r/result.json)
retains SDK/consumer offline pub/analysis, generated request, first real MCP HTTP200
and strict owned cleanup PASS. The actual payload had source_count=5,
ready_source_count=3, component_count=1 and scalar overall/detail. Default Source
decoding required sourceCount and failed; zero full scenarios completed and the
second omitted-detail call did not run. The read-only CML/Record producer and
selected runtime/dependencies remain unchanged; source/JAR byte equivalence is
not claimed. Original SDK and all failed receipts remain.

The [real procedure](../../examples/dart-cbd-consumer/README.md) now explicitly
generates with `--json-field-style cncf-snake-case` into fresh
`target/phase65/s654/sdk-cncf-snake`. The authored consumer depends solely on
`../sdk-cncf-snake` and still reports original camel Dart API names. Execution and
scenario/Given provenance identify that SDK and exact profile; fixed count
correspondence uses actual snake payload keys while complete typed re-encoding
must equal the unchanged actual payload. Consumer source, MCP messages,
CAR/SAR/configuration and resource ownership/cleanup retain their contracts.
The S65.4E accumulator actually passed 249/249 tests in 14 suites with 21 SDK
command reports, zero SBT/wrapper exits and `lock=released`. Fresh actual-source
profile generation passed with all six products. Historical 217/217 remains
S65.4B evidence; none of these results validates the new S65.4F method.

## Additive optional absence (S65.4F)

Actual live005 [result](../../target/phase65/s654/runs/live-96ligvfe/result.json)
retains both actual MCP HTTP200 calls and complete generated typed
decode/default re-encode equality with both full raw payloads, explicit marker
echo and strict owned Java/fixture cleanup PASS. The second request actually
contained {"detail":null}; the runtime returned detail string "null" and
omitted-detail fixture-summary admission failed (VAL-P65-S654E-009). Complete
DART-08 proof remained unresolved at that historical attempt. Earlier preparation, S65.4B/S65.4E compile
and live001..004 failures remain retained, along with raw-submission gaps,
SDK CLI platform-input absence and corrected native attribution limitations.
No missing historical tool input is reconstructed.

The [renderer](../../src/main/scala/cozy/modeler/DartModelRenderer.scala) authors
`Map<String, Object?> toJsonOmittingNullFields()` only for non-scalar data
objects. It emits a separate typed map with collection-if entries for null
`MZeroOne` fields, preserving required/nonnull/empty-list entries. Private
`_encode`/`_encode_element` receive `omitnullfields: Boolean = false`, propagated
through list callbacks. When true, each object reference resolves through the
canonical projection; non-scalar data calls the new method, while scalar wrappers
and enum/sealed/open classifications keep scalar/discriminator `toJson()`.
Empty data objects return a typed empty map. The default zero-argument
`Object? toJson()` expression and recursive explicit-null encoding remain exact,
independent of omission calls. There is no runtime map filter, adapter or option.
Constructor/decode validation, canonical identities, Dart symbols, both wire
profiles and metadata remain unchanged. Omitted decoding has the same canonical
optional state: omission preserves absent keys; default encoding restores nulls.

The [projection](../../src/main/scala/cozy/modeler/DartModelProjection.scala)
reserves the added method only in non-scalar data-member admission before
rendering. Scalar/classification reservations stay exact, with structured
canonical-identity collision failures. DA-23 authors one both-profile
direct/package collision behavior and one independent runtime behavior with
separate Source/CNCF consumers, local optional itemNote extension and complete
independent JSON expectations. Adjacent Given/When/Then covers recursive
required/list nulls, nonnull string/scalar/finite values, required/empty-list
continuity, default before/after and decode/restoration. Existing fixtures remain.

The consumer explicitly selects the generated omission method in both request
output and injected status. Its typed map removes the prior redundant type guard;
response.fromJson/default toJson remain. Fresh SDK dependency/execution/scenario/
Given paths select `sdk-cncf-snake-omit`; provenance retains `cncf-snake-case`
and names `requestEncoding=toJsonOmittingNullFields`. Before any HTTP call the
harness requires exactly {"detail": marker} or {}, retaining generated evidence,
rejecting present-null/additional keys and comparing injected/sent maps unchanged.
Actual snake counts, full raw response equality, marker/fixture summary and
strict ownership/cleanup remain mandatory. Two new mock-only Python methods
specify exact request/envelope/report correspondence and pre-HTTP null rejection;
they provide no real CBD evidence. New unit output is fresh
`target/phase65/s654f-unit-result.json`, preserving unit18's receipt.

S65.4F/DA-23 actual validation passed 251/251 tests in 14 suites, 31 SDK commands
across six independent consumers and one controlled negative, six actual SDK
products from unchanged real CBD CML and unit20 PASS. Actual live006
(`target/phase65/s654/runs/live-5epmn_uo`) passed both real MCP HTTP200 calls,
full typed raw-response equality, genuine {} omission, marker echo and strict
cleanup. The omitted summary was `fixture-catalog=degraded`, with actual CAR
index200/SAR index404, so live acceptance remained false at that attempt (VAL-P65-S654F-010).
Historical249/21/unit18/live005 and all earlier failures remain retained.

## Private catalog inputs and two-endpoint readiness (S65.4G)

The selected runtime reads both CAR and SAR catalog indexes; an unavailable
index adds a snapshot warning and degrades the catalog. A private empty SAR
index legitimately describes zero SAR profiles. The harness copies the original
`examples/cbd-sie-sar/fixtures` to a fresh per-run `fixture-input`, rejecting
recursive/ancestor symlinks, existing destination and absent nonempty regular
CAR index/model metadata before fixture binding. Original fixture bytes stay
unchanged. Only an absent copied SAR index is generated as
{"entries":[],"diagnostics":[]}, without schema/schemaVersion or invented
component metadata; an existing nonempty regular SAR index retains its exact
bytes/entries. This input still feeds the unchanged real CBD producer.

Result, server-process and Given `fixturePreparation` record absolute
`sourceFixtures`, `servedFixtures`, `sarIndex` and boolean `sarIndexGenerated`,
separately from original source preparation. The fixture server serves the
private `servedFixtures`. Sequential `fixture-car-readiness` and
`fixture-sar-readiness` use the existing bounded JSON HTTP boundary before socket
selection/Java acquisition. Both must succeed; failures propagate and actual
receipts/raw responses remain evidence. HTTP numbers reflect recording order;
labels and locators identify the two checks.

Four new adjacent Given/When/Then methods cover missing-SAR generation only in
the copy, existing-SAR retention, named admission failures and mock-only CAR/SAR
order/failure. All twenty existing methods remain. Relevant251/14/31 and six
real-CML SDK evidence is reused. Actual [unit24](../../target/phase65/s654g-unit-result.json)
passed 24 tests, zero failures/errors, success true and runner exit0.
Actual [live007](../../target/phase65/s654/runs/live-gru8e1bo/result.json) completed
both original scenarios with owned session007 exit0 and no timeout/signal/forced
termination. CAR/SAR readiness and both real MCP calls returned HTTP200; private
SAR entries/diagnostics are empty, source fixtures read-only and provenance intact.
Exact generated marker and genuinely omitted {} requests passed full actual
typed-response equality and marker echo. Counts are source_count=5,
ready_source_count=4, component_count=1; the exact summary remains
simplemodeling=degraded, fixture-catalog=ready, empty=ready, local-car=ready,
cache-car=ready, with overall degraded. Java28739/group28739 TERM, no KILL/errors,
wait143 and two native group-absence observations, fixture close/thread join
and strict cleanup passed. Complete submitted/native initial/terminal records
remain after one evidence-only continuation, without rerender/replay; mistaken
progress literals were superseded, retaining historical raw retention limits.
Product live proof PASS supplies the real consumer evidence for independently
accepted S65.4. All eight original checklist boxes are checked; all four original groups are CLOSED.
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

## Historical attribution and scope

The [provisional proposal](../notes/dart-model-api-code-generation-provisional-specification.md)
and [KnowledgeHub planning journal](../journal/2026/09/2026-09-15-dart-code-generation-knowledgehub-consumer.md)
retain the imported GitHub Phase 55/KnowledgeHub origin. Phase 65 and CBD Support
are the active assignment; local completed Phase 55 remains separate. The
Phase 50–53 UI roadmap and [Phase 54](../phase/phase-54.md) semantic authority
remain their own boundaries. This target does not generate Flutter widgets,
navigation, synchronization, storage policy or consumer transport implementation.

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
