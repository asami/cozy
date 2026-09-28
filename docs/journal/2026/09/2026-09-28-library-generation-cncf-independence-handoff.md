# Handoff: CNCF-independent library generation for simplemodeling-model

Date: 2026-09-28
Execution host: Mac mini
Status: investigation complete at source-inspection level; reproduction and repair pending

## User request and intended result

The user expects Cozy generation for `simplemodeling-model` to be independent
of the CNCF runtime. After investigating the observed dependency, the user
selected the Mac mini for the repair and requested this handoff.

Make the library's CML-to-Scala generation and compilation work without a
`goldenport-cncf` dependency or CNCF runtime descriptor, while preserving its
model types, packages, and intended semantics. Preserve CNCF-aware generation
for projects that actually select that target. Do not weaken CAR/SAR validation
or remove necessary runtime dependencies from CNCF consumers.

This handoff authorizes investigation and the bounded repair on Mac mini. It
does not request release conversion, remote publication, deployment, or a
blanket commit/push of concurrent work.

## Repositories and observation baseline

Resolve the corresponding checkouts on Mac mini; these are the Air observation
paths, not paths to create blindly on the receiving host.

| Repository | Observed path | HEAD when handoff was written |
| --- | --- | --- |
| Cozy | `/Users/asami/src/dev2025/cozy` | `dc42281cb89f4f27bf949c3c0bfe34a90e588309` |
| sbt-cozy | `/Users/asami/src/dev2026/sbt-cozy` | `57d4c4c227da19f1df619aa1e8cffb2cdb78464b` |
| simplemodeling-model | `/Users/asami/src/dev2026/simplemodeling-model` | `a6c44e288f27379471bfab261f6ddbe7b5700893` |

These IDs are provenance, not instructions to reset or check out old commits.
Reconcile newer receiver changes before freezing the repair.

## Confirmed observations

1. `simplemodeling-model/project.yaml` declares `project.kind: library` and
   `packaging.kind: maven`, but both it and `build.sbt` explicitly depend on
   `org.goldenport::goldenport-cncf:0.5.2`. The latter selects the Cozy backend
   and a local Cozy delegate checkout. The dependency was introduced by
   `01169398d4661d33dbb2a2a771b220f7f22182fc` on 2026-08-20. Its historical
   motivation was not established.
2. Both `src/main/cozy/address.cml` and `user-profile-values.cml` explicitly
   declare `# COMPONENT`, `SimpleModelingModel`, and package
   `org.simplemodeling.model`. Thus Component emission through the general
   modeler is explainable from the input; this alone does not prove a bug.
3. In sbt-cozy's `src/main/scala/org/goldenport/cozy/CozyPlugin.scala`,
   `CozySbtBridge.resolveGenerate` selects `modeler-scala` unconditionally.
   It does not select the existing `modeler-scala-value` operation for this
   library. The `cozyGenerate` task also calls
   `CozyCncfRuntimeDescriptor.extractRequired` whenever backend is `cozy`,
   without a library/runtime-independent branch.
4. Cozy has a value-side operation: `Modeler.generateScalaValue` uses
   `ModelBuilder.buildValue`. Existing `ModelerValueGenerationSpec` scenarios
   require that this mode not emit a Component. However, normal and value-side
   operations both use `ScalaGenerator`.
5. `src/main/scala/cozy/modeler/ScalaGenerator.scala` unconditionally calls
   `CompositeStateMachineActionProgram.generate`. In
   `CompositeStateMachineActionProgram.scala`, that generator emits
   `LogicalActionCompiler.scala` even when the definitions vector is empty.
   The emitted compiler directly references
   `org.goldenport.cncf.unitofwork.ExecProgram` and `UnitOfWorkOp`.
   Merely switching to `modeler-scala-value` is therefore insufficient to
   establish CNCF independence.
6. The observed library generation provenance lists both
   `SimpleModelingModelComponent.scala` and `LogicalActionCompiler.scala`.
   Input hashes for both CML sources and recorded hashes for those output
   files matched the actual bytes. This argues against unrelated leftover
   files as the sole explanation; it is not a fresh reproduction of the
   current generator binary.
7. A search of handwritten `simplemodeling-model/src/main/scala` and
   `src/test/scala` found no direct CNCF package references. This does not prove
   absence of transitive dependencies or every generated reference.

The probable causal boundary is the missing separation between reusable
library generation and CNCF runtime generation across sbt-cozy and Cozy,
combined with the library's current Component declarations and explicit
dependency. Exact generation-mode policy remains to be settled against the
current specs before editing; `library` must not be equated with VALUE-only
for every possible project without examining the supported model categories.

## Reproduction and repair sequence

1. Read current repository directives, generation specs, existing value-mode
   tests, and the receiver's dirty state. Record actual plugin, delegate,
   modeler, and runtime artifact identities; do not assume the checked-out
   source is the binary used by SBT or the CLI.
2. Freeze a minimal reproduction using the two actual library CML files and
   a fresh task-owned output directory under `target/`. Compare the current
   delegated generation and direct value-side generation. Record the emitted
   Component/action-program files and all CNCF references. Do not clean or
   overwrite another task's generated tree.
3. Establish the intended library generation contract in rules/spec/design
   before implementation. Preserve the generated Address/profile value type
   names and packages. Determine how library mode is propagated from
   project configuration into delegated generation and how genuine runtime
   features are admitted or rejected explicitly.
4. Fix sbt-cozy's generation selection and descriptor requirement at that
   boundary. Also update Cozy's `src/main/scala/cozy/runtime/CozySbtBridge.scala`
   delegated command dispatch and compatibility/descriptor policy: its current
   generate handler accepts only `modeler-scala`, so selecting
   `modeler-scala-value` in sbt-cozy alone would be rejected before generation.
   Carry the agreed library mode through the complete bridge. Keep the
   existing strict CNCF-aware path intact.
5. Fix Cozy's runtime-support emission so a model requiring no CNCF execution
   support does not acquire it accidentally. Audit the other unconditional
   ABI/bootstrap emitters in `ScalaGenerator`; removing only the Component
   file or only one import is not sufficient. Preserve valid state-machine,
   Workflow, action-program, provided-API, and candidate-admission generation.
6. Reconcile `simplemodeling-model` configuration and CML declarations with
   the established library contract. Remove the direct CNCF dependency only
   when fresh generation and compilation prove it unnecessary. Do not remove
   Component declarations blindly if they currently carry package identity.
7. Verify the same reproduction and nearest focused regressions, then inspect
   the resulting dependency graph. If a required change belongs to the
   SimpleModeler backend or another repository, report the exact ownership
   boundary before expanding the repair. Do not introduce downstream stubs or
   hand-edited generated files as a substitute.

## Acceptance evidence

- Fresh library generation works through the actual delegated `sbt-bridge`
  path, as well as the selected direct CLI control, without a CNCF runtime
  JAR/descriptor on the selected generation path; removing the dependency does not just move the
  failure to descriptor extraction.
- A value-only library fixture emits its intended types and package names,
  with no synthetic CNCF Component or CNCF-dependent execution helper.
- The actual `simplemodeling-model` CML inputs generate and compile without a
  direct or unintended transitive `goldenport-cncf` dependency. Distinguish
  the generator tool's own classpath from the generated library's classpath;
  this task does not require removing the separately versioned
  `cncf-collaborator-api` from Cozy itself.
- Regression coverage proves both independence of the library path and
  continued CNCF-aware generation. Start with existing
  `ModelerValueGenerationSpec`, action-program generation specs, and sbt-cozy
  delegate/descriptor/version-contract specs; choose exact commands after
  inspecting the current tests. Add only missing behavioral coverage.
- Empty runtime definitions do not silently add a CNCF dependency. Genuine
  runtime definitions retain their supported behavior or receive an explicit
  unsupported-target diagnostic under the agreed contract.
- Record generated-file inventory, dependency evidence, exact commands,
  versions, exit results, and remaining limitations. Do not claim this is
  verified from source inspection alone.

Route all top-level SBT through the registered serialized command runner and
the installed `cncf-sbt-serial-execution` contract. Never run independent SBT
processes concurrently. Use the registered Cozy command route for exact CLI
reproduction. If local artifact refresh is required, scope it to the repaired
dependency and verify which artifact the consumer actually loads. Avoid broad
test suites until the narrow reproduction is understood.

## Concurrent work to preserve

At handoff creation Cozy had a modified `docs/phase/phase-71.md`, untracked
`docs/design/cml-semantic-metadata.md`, `docs/spec/cml-semantic-metadata.md`,
`src/main/scala/cozy/modeler/CmlSemanticMetadata.scala`,
`src/main/scala/cozy/modeler/CmlSemanticMetadataReader.scala`,
`src/test/resources/cozy/modeler/`, and
`docs/journal/2026/09/2026-09-28-model-harness-video-continuity-and-pronunciation.md`.
`simplemodeling-model` had an untracked `docs/phase/hygiene-ledger.md`.
sbt-cozy was clean. Re-inventory on Mini and preserve all concurrent work;
these observations are not an ownership claim over those files.

## Completed here and remaining work

Completed: read-only source/configuration/history inspection and comparison
of existing generation-provenance hashes with current input/output bytes.
No fresh generation, SBT, focused test, dependency resolution, product repair,
commit, or push was performed for this investigation. Only this handoff was
added. The document is not automatically transferred to Mac mini.

Next action on Mac mini: establish the fresh minimal reproduction, then freeze
the cross-repository repair boundary and generation-mode contract above.
