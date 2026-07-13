# CAR Project Metadata Ownership

## Purpose

Keep each CAR independently buildable while making `project.yaml` the canonical
project metadata source. Generated SBT files project that metadata into the
build; they do not redefine it.

## Ownership

`project.yaml` owns:

- project organization and artifact name;
- component name, class name, display name, and version;
- Scala version;
- exact compile and test dependencies used for development;
- CAR descriptor metadata;
- CNCF runtime compatibility requirements.

The exact CNCF dependency under `build.dependencies.compile` records the
version used to compile and test the CAR. The
`packaging.car.runtime.cncf` block independently records the runtime versions
that can execute the CAR. Those values may coincide, but they express different
contracts and must not be derived from each other.

`build.sbt` owns only SBT and Cozy wiring:

- enabling `sbt-cozy`;
- projecting required `project.yaml` values into SBT settings;
- configuring project-specific resolvers and Cozy execution settings.

The generated `project/ProjectYamlBuild.scala` converts dependency coordinates
to SBT `ModuleID` values. A single colon selects an exact artifact name. A
double colon selects the Scala binary-cross artifact.

`project/plugins.sbt` remains a bootstrap exception because `sbt-cozy` must be
resolved before `project.yaml` can be projected into the loaded build.

## Scaffold Boundary

Both `cozy init component` and `cozy car-sbt-project` generate the same metadata
contract for `car` and `car-sar` layouts. The CAR+SAR root build reads the
component's `component/project.yaml`; it does not create a second CAR identity
or dependency-version source.

Generated projects do not create `BuildVersion.scala` and do not duplicate
standard `publish` or `publishLocal` delegation. Those publication tasks are
owned by `sbt-cozy` when `packaging.kind` is `car` or `sar`.

Generated projects also do not create
`src/main/car/component-descriptor.json`. CAR packaging derives the descriptor
name, version, component identity, and extensions from the SBT settings
projected from `project.yaml`. A source descriptor would duplicate identity and
make a `project.yaml`-only version update fail descriptor validation.
