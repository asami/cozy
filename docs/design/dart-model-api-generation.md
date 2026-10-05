# Dart model and API generation design

Status: frozen Phase 65 design; implementation and independent acceptance pending
Date: 2026-10-05
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
`component.service.operation`. A later Cozy implementation must join these
existing sources without guessing ownership from an operation's short name.
Server `MOperation` bodies, generated Scala text, sample JSON and client-local
models are not substitutes for canonical DTO metadata.

Selection traverses the requested data and operation dependency closure before
rendering. Each reference resolves against exact source identity, and only
selected contracts/dependencies participate in Dart support validation. Missing,
ambiguous or unsupported selected contracts fail before successful Realm output.
Existing source parsing and canonical-build failures remain failures; selection
does not promise to bypass the canonical model's own admission rules.

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

The future `cozy modeler dart` entry extends the existing Cozy modeler service
file/charset parsing and structured failure boundary. The existing facade today
exposes only class/project. A Dart renderer constructs a complete Realm after
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
0.3.3-SNAPSHOT during this Slice. Dart implementation paths and executable spec
paths are prospective and will be frozen by later Step manifests; they are not
claimed as existing implementation evidence here.

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
`params.name = "CbdSupport.CbdRetrieval.status"` and typed request JSON as
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

Runtime preparation is a later, admitted S65.4 operation. Its source snapshot
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
launcher/normal resolved classpath, the resulting CBD CAR, copied standalone
SAR descriptor, isolated task-owned resource trees, loopback catalog fixture
and a free task-owned loopback port. Evidence sources are
`textus-cbd-support:examples/cbd-standalone-sar/subsystem-descriptor.yaml`,
`examples/cbd-standalone-sar/README.md`, `examples/cbd-sie-sar/fixtures` and
`scripts/check-cbd-standalone.sh` in the verified checkout.

Adapt the reviewed lifecycle rather than invoking `check-cbd-standalone.sh`
unchanged: it embeds direct SBT and removes evidence. Retain logs/results and
stop only task-started processes. Runtime/session and SBT commands use their
registered runners after exact S65.4 command/script admission. The catalog
fixture is input to the real CBD runtime, not a fake status response producer.
No SIE source, remote production service or external source edit is required.

## Prerequisites and evidence limits

Parent-recorded environment observations for this contract are that Dart SDK
3.13.4 is available inside the Flutter SDK, the CBD standalone descriptor,
fixtures and scripts exist, and local Ivy contains goldenport-cncf_3
0.5.3-SNAPSHOT, the runtime declared/tested by CBD `project.yaml`. Future Dart
validation uses the direct `dart-sdk/bin` executable without Flutter upgrades,
installation or new package dependencies.

The target CBD CAR, runtime classpath and default `cncf` executable are currently
absent in the parent inventory. These are unexecuted prerequisites, not runtime
readiness evidence. This documentation Slice has performed no build, launch,
analyzer run or live call. Source-copy build/launcher failures or actual wire
mismatches leave Phase acceptance unresolved and cannot defer or replace DART-08.

S65.4 evidence must retain actual selected source/artifact identities, command
receipts, server readiness, live status result and independent Dart analyzer/run
output. It must demonstrate sourceCount/readySourceCount/componentCount and
scalar overall/detail in the live payload, encoded request use and named typed
decode/re-encode behavior. Later CBD UI work reuses this generated SDK rather
than authoring a parallel model/API layer.

## Historical attribution and scope

The [provisional proposal](../notes/dart-model-api-code-generation-provisional-specification.md)
and [KnowledgeHub planning journal](../journal/2026/09/2026-09-15-dart-code-generation-knowledgehub-consumer.md)
retain the imported GitHub Phase 55/KnowledgeHub origin. Phase 65 and CBD Support
are the active assignment; local completed Phase 55 remains separate. The
Phase 50–53 UI roadmap and [Phase 54](../phase/phase-54.md) semantic authority
remain their own boundaries. This target does not generate Flutter widgets,
navigation, synchronization, storage policy or consumer transport implementation.
