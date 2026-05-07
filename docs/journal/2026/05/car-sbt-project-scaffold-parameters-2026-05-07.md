# car-sbt-project Scaffold Parameters

Date: 2026-05-07

Status: implemented, with follow-up items

## Context

This note records the Cozy-side implementation work based on:

- `/Users/asami/src/dev2026/textus-user-notification/docs/proposals/cozy-car-sbt-project-scaffold-parameters.md`

The target command is:

```console
cozy car-sbt-project --save=<project-dir> [scaffold options]
```

The goal is to make `car-sbt-project` usable as a real component project scaffold, not only as a fixed `Sample` starter.

## Implemented Scope

Implemented priority 1-5 items from the proposal.

### Scaffold Metadata Options

`car-sbt-project` now accepts the following options:

- `--component=<ComponentName>`
- `--package=<scala.package.name>`
- `--name=<artifact-name>`
- `--organization=<organization>`
- `--version=<version>`
- `--bounded-context=<bounded-context>`
- `--domain=<domain>`
- `--gitignore`
- `--readme`
- `--tests`

The defaults preserve the previous sample behavior when no scaffold metadata is specified.

### build.sbt Generation

`build.sbt` generation now uses scaffold metadata for:

- `organization`
- `name`
- `version`
- CAR manifest metadata
- generated Scala package paths
- ServiceLoader provider class name
- `BuildVersion.scala`

The previously invalid delimiter around:

```scala
cozyManifestMetadata ++= Map(...)
```

was corrected so the generated `build.sbt` compiles.

### Package-Aware Source Generation

Generated Scala files now follow the requested package:

- `src/main/scala/<package>/impl/ComponentFactory.scala`
- `src/main/scala/<package>/BuildVersion.scala`
- `src/test/scala/<package>/ComponentFactorySpec.scala` when `--tests` is specified

ServiceLoader provider configuration also uses the generated package-aware `ComponentFactory` class name.

### ComponentFactory Starter

The generated `ComponentFactory.scala` now uses the generated component class name derived from `--component`.

The generated source remains a starter implementation and intentionally exposes `???`-based override points where application-specific implementation is normally required.

### Optional Project Files

The scaffold can now generate:

- `.gitignore` with `--gitignore`
- `README.md` with `--readme`
- `ComponentFactorySpec.scala` with `--tests`

## Verification

Cozy verification:

```console
sbt test
```

Result:

- passed
- 107 tests completed

Focused scaffold verification:

```console
sbt "testOnly cozy.modeler.ModelerGenerationSpec -- -z scaffold"
```

Result:

- passed

Generated project verification:

```console
cd target/test-generated/car-sbt-project-scaffold-parameters
sbt compile
```

Result:

- passed
- compile still reports deprecation warnings from generated/dependency code, but no compile error remains

## Changed Files

Implementation:

- `src/main/scala/cozy/Cozy.scala`

Executable specification:

- `src/test/scala/cozy/modeler/ModelerGenerationSpec.scala`

## Remaining Work Items

### 1. Rich Model Parameterization

Not yet implemented:

- `--services`
- `--entities`
- `--operations`
- richer operation/entity selection

Current behavior still generates the Notice-oriented starter model unless a model file is provided through the existing generator path.

Desired outcome:

- scaffold metadata should be able to produce a more domain-specific starting CML model
- generated operations and starter factory overrides should match selected services/entities/operations

### 2. Web Scaffold Control

Not yet implemented:

- `--web=<mode>`
- explicit control over web descriptor generation
- richer route/page/form generation policy

Current behavior keeps the existing default web descriptor path.

Desired outcome:

- caller can select no web scaffold, minimal web scaffold, or richer web scaffold
- generated `src/main/car/web/web.yaml` should be derived from selected operations, not only fixed Notice starter operations

### 3. ComponentFactory Domain Generalization

The generated factory is package-aware and component-name-aware, but the starter body is still Notice-oriented.

Desired outcome:

- factory template should be driven by the CML model or scaffold operation parameters
- required override points should be generated for each operation that lacks an implementation binding
- placeholders should remain explicit and visible so missing implementation cannot be mistaken for completed behavior

### 4. Warning Cleanup in Generated Projects

Generated scaffold projects compile, but currently emit deprecation warnings.

Desired outcome:

- remove warnings caused by generated source where practical
- separate dependency-origin warnings from generator-origin warnings
- keep generated scaffold compile output clean enough to expose real failures

### 5. CAR/SAR Scaffold Parity

The CAR+SAR path was adjusted to receive scaffold metadata, but deeper parity has not been fully reviewed.

Desired outcome:

- `car-sar-sbt-project` should have the same documented scaffold parameter semantics where applicable
- generated SAR metadata and README should be checked against the CAR path

### 6. Documentation

The command help was updated, but full user-facing documentation is still needed.

Desired outcome:

- add a design/user guide entry for `car-sbt-project`
- document default behavior, overwrite behavior, and scaffold options
- include one realistic example such as `textus-user-notification`

## Notes

The current implementation intentionally keeps backward compatibility for the zero-option sample case.

The next useful development step is to make the starter CML and `ComponentFactory` generation model-driven instead of Notice-template-driven.
