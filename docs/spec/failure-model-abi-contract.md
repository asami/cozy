# Failure Model generated ABI contract

*Since / Version:* Oct. 3, 2026

This contract is specified by `FailureModelAbiGenerationSpec`. It projects the
accepted [SOURCE contract](failure-model-source-contract.md) into additive,
immutable development metadata. CNCF Phase 91 owns the canonical runtime
`ResolvedFailureModel`, recipient validation and runtime resolution. sm-workflow
uses the recipient-resolved result. Cozy copies authored defaults and refinements;
an Execution Model name introduces no failures or defensive mechanisms.

## Products and admission

Both `modeler-scala` and `modeler-scala-value` derive Failure Model definitions
from the actual `SModel.model` through `FailureModelCml.requireDefinitions`.
For the CNCF target, a nonempty selected component vector emits:

| Product | Exact relative output path |
|---|---|
| JSON sidecar | `target/cozy/failure-model.json` |
| Self-contained Scala 3 metadata | `target/scala-3.3.8/src_managed/main/scala/cozy/generated/failuremodel/FailureModelMetadata.scala` |

The Scala package is `cozy.generated.failuremodel`. Its object
`FailureModelMetadata` exposes `schemaVersion: String = "cozy.failure-model.v1"`
and `components: Vector[ComponentFailureModel]`. It depends only on Scala standard
types. The private Cozy emitter exposes `schemaVersion`, `metadataPath`,
`generate(definitions: Vector[ComponentFailureModel]): Realm` and
`canonicalJson(definitions: Vector[ComponentFailureModel]): String`.

An empty vector emits an empty Failure Model Realm. Integration adds neither
product in that case; direct canonical JSON projection remains valid with empty
`components`. Valid unused execution definitions produce no component contract.
Existing Workflow v1–v5 products, component API metadata, CAR descriptors and
runtime bootstrap contracts retain their established shape and selection.

## Exact public shapes

All generated classes below are immutable final case classes inside
`FailureModelMetadata`. The field order is also the exact recursive JSON object
field order. JSON root order is `schemaVersion`, `components`.

| Shape | Constructor fields, in order |
|---|---|
| `FailureModelSourceIdentity` | `declarationKind: String`, `sectionPath: String`, `line: Option[Int]` |
| `FailureModelEntry` | `identity: String`, `scope: FailureScope`, `source: FailureModelSourceIdentity` |
| `ExecutionFailureModel` | `identity: String`, `source: FailureModelSourceIdentity`, `defaults: Vector[FailureModelEntry]` |
| `FailureModelDelta` | `ownerKind: String`, `ownerIdentity: String`, `source: Option[FailureModelSourceIdentity]`, `entries: Vector[FailureModelEntry]` |
| `EffectiveFailureEntry` | `identity: String`, `scope: FailureScope`, `origins: Vector[FailureModelEntry]`, `winningOrigin: FailureModelSourceIdentity` |
| `EffectiveFailureContract` | `ownerKind: String`, `ownerIdentity: String`, `source: FailureModelSourceIdentity`, `entries: Vector[EffectiveFailureEntry]` |
| `OperationFailureModel` | `identity: String`, `source: FailureModelSourceIdentity`, `delta: FailureModelDelta`, `effective: EffectiveFailureContract` |
| `ServiceFailureModel` | `identity: String`, `source: FailureModelSourceIdentity`, `delta: FailureModelDelta`, `effective: EffectiveFailureContract`, `operations: Vector[OperationFailureModel]` |
| `ComponentFailureModel` | `identity: String`, `source: FailureModelSourceIdentity`, `executionSelectionSource: FailureModelSourceIdentity`, `executionModel: ExecutionFailureModel`, `delta: FailureModelDelta`, `effective: EffectiveFailureContract`, `services: Vector[ServiceFailureModel]` |

`FailureScope` is sealed with `def name: String`. Its companion exposes
`InScope` and `OutOfScope`, whose names and JSON tokens are exactly `IN_SCOPE`
and `OUT_OF_SCOPE`. `FailureModelIssue` is an admission diagnostic and is excluded
from this generated contract.

## Serialization and meaning

Products are deterministic UTF-8. Arrays retain declaration/default order and
every effective level retains the default identities in their original order.
Origins retain the authored execution → component → service → operation chain
applicable to that owner; `winningOrigin` is the final applicable source.
Sibling owners retain independent chains. Explicit `OUT_OF_SCOPE` is retained
as a negative implementation constraint. A narrower explicit scope wins in
either direction according to the accepted source semantics.

Missing line and delta source are JSON `null` and Scala `None`; present values
are the actual source identity or line and Scala `Some`. Absent deltas retain
their owner fields and empty entries. No timestamp or machine path is invented.
Source `sectionPath` remains the original logical declaration path.

Quotes, backslashes, control characters, Unicode and surrogate code units are
escaped as safe quoted literals. Authored identities are values and are never
interpolated into a Scala class or package identifier. Both products project
exactly the same accepted immutable IR, without extra inferred semantics.

## Caller and library compatibility

`ScalaGenerator` appends
`failureModels: Vector[ComponentFailureModel] = Vector.empty` after the existing
`generationTarget`. `ModelGenerationTarget.requireLibraryModel` and
`requireLibraryGeneratorInput` append the same defaulted parameter after
`workflows`. All original argument positions, labels and defaults remain valid
for positional and named Scala callers.

Both direct library policies, direct library generation, `requireLibrarySource`
and the public value CLI reject nonempty Failure Model input through the existing
`LIBRARY_GENERATION_UNSUPPORTED_RUNTIME` diagnostic with category `failure-model`.
If other runtime categories are present, their original order is retained and
the Failure Model category is appended. Empty vectors and original pure library
fixtures remain accepted.

## Executable evidence and compilation gate

The spec authors seven adjacent Given/When/Then behaviors: repeated real
normal/value products and decoded complete IR; all four original fixture/route
groups and every byte; optional and escaped pure IR; a seeded 100-sample
ScalaCheck roundtrip/order property with deliberately wrong order/owner examples;
old positional/named callers and library fixtures; all library rejection routes;
and real mixed Workflow v5 output.

The independent read-only `failure-model-legacy-baseline.json` records all 80
original UTF-8 files from SOURCE commit
`6c15b65cd83181fdf61aa8674c9a847f79751805`. It is never regenerated by the spec.
The same original fixture paths and public CLI routes are regenerated and their
complete path-to-byte maps compared with the captured products.

The historical capture remains unchanged and is never regenerated or derived
from the products under test. For validation against the currently configured
dependencies, its independent expected map binds the two metadata `source.path`
values to the actual input fixture path passed to the CLI. The existing
description-capable SimpleModeler contract also requires each Operation
Specification builder to carry its own `BaseContent.Builder(operationName)`,
including operations without authored descriptions. For the original workflow
fixture on `modeler-scala`, the expected DomainComponent therefore includes
that content for `beginReview`, `submitReview`, `supplyInput`, and `reviseChange`.
This is the existing upstream change from SimpleModeler commit
`549a38c4184d428dfefb6e96ca876902e847fe44`, not a Failure Model product change.

The projection changes only those two expected metadata paths in each of the
four groups and the four named content lines in that one expected Scala file.
It preserves all 80 product paths, every other byte, source hashes, compiler
and version fields, request/response contracts and empty-input checks. Actual
generated products are not normalized, filtered or used to construct expected
data; every actual path-to-byte map still must equal the complete independently
bound expected map. Unknown dependency changes continue to fail comparison.
The original Phase 72 acceptance and its historical source capture remain
historical evidence, without rewriting their completed records.

The mixed CML is derived only under `target/test-generated` from the original
`workflow-semantic-outcome.cml`: Domain selects LocalSingleWriter and refines
concurrent-write; ReviewService narrows that scope; submitReview refines
network-partition. Explicit execution defaults are appended from the accepted
local fixture. Original Workflow, state-machine, service and value declarations
and callable identities remain. The spec checks source metadata, complete Failure
Model IR, unchanged Workflow semantics with actual updated source locations,
unchanged value bytes and retained Workflow v5 schema/products.

Actual generated source and typed probes are materialized in these groups:

| Group | Output/source root | Exact selected compilation source inventory |
|---|---|---|
| normal | `target/test-generated/failure-model-normal` | generated `FailureModelMetadata.scala`, `FailureModelConsumerProbe.scala` |
| value | `target/test-generated/failure-model-value` | generated `FailureModelMetadata.scala`, `FailureModelConsumerProbe.scala` |
| mixed | `target/test-generated/failure-model-mixed` | generated `FailureModelMetadata.scala`, generated `StateMachineWorkflowAbi.scala`, generated `ReviewWorkflowStateMachineWorkflow1.scala`, generated `StateMachineWorkflowComponentFactoryBootstrap.scala`, `FailureModelConsumerProbe.scala` |

Probes construct every public shape and read scope, source, Option, authored
defaults, deltas, all effective levels, origins and winners from actual generated
components. The mixed probe also uses the actual old Workflow descriptor,
factory metadata, bootstrap and defaulted old positional constructor.

After focused behavior success, the parent freezes the actual selected inventory
and compiles those 2/2/5 sources with Scala 3.3.8/library only, `dependencyOverrides`
Nil, in one sequential registered serialized SBT invocation. Each group uses
`target/phase72-generated-compile/<group>/classes` and a separate
`target/phase72-generated-compile/<group>/inc_compile.zip`. Source materialization,
JSON properties and string expectations are not compilation acceptance.
The parent accepted six-suite focused validation with 105/105 tests and actual
Scala 3.3.8 compilation of the normal/value/mixed 2/2/5 source groups, with
successful SBT/wrapper exits and released serial locks. Independent protected ABI
review passed with zero current blockers; parent manual local ABI commit
`1d9c80775e74f7d0ba0dd60f1cf4730ef84c3843` closes S72.2.
The authored [recipient handoff](../phase/phase-72-cncf-handoff.md) supplies
reproduction paths, the fixture oracle and recipient acceptance prerequisites.
HANDOFF is accepted at commit `783a48552de5fa6a4b34a8dfe9c1184f005de62f`
and the sole Phase-wide review passed. Final full validation passed; the containing distinct parent manual local release commit closes Cozy Phase 72; canonical receiver/runtime resolution and workflow
integration remain recipient-owned.
