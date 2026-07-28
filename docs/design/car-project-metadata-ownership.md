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
- the exact Cozy generator version used by the generated build;
- exact compile and test dependencies used for development;
- CAR descriptor metadata;
- CNCF runtime compatibility requirements.

`build.cozyVersion` is the exact build-time generator coordinate. Generated
SBT wiring reads that value from `project.yaml`; it does not substitute the
Cozy version running the package command or an operation default.

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

## Package Gate

For a CAR, package admission reads the unmerged `project.yaml` contract.
Operation defaults may still configure unrelated packaging behavior, but they
cannot replace `build.cozyVersion`, the CNCF compile dependency, or
`packaging.car.runtime.cncf`.

The concrete `package-car` command therefore requires `--project-dir` and an
explicit CAR classification in that project's `project.yaml`. Generic metadata
inspection may treat a non-CAR project as out of scope, but an archive-producing
boundary must not emit a manifest-less CAR by applying that optional result.

The package gate resolves exactly one CNCF runtime descriptor from the actual
input JARs. Its runtime, module organization/artifact/version, and root
descriptor version must identify the exact CNCF compile dependency. That same
resolved version must lie within the declared minimum and optional maximum,
must not be excluded, and must be present in `tested`. Missing, duplicated, or
contradictory evidence is rejected before archive acceptance.

When generation produced `target/cozy/generation-provenance.json`, the package
gate revalidates that document against the current project source, generated
Scala output, and accepted CNCF/Cozy coordinates. It then copies the unchanged
byte snapshot to the top-level CAR entry `generation-provenance.json`. This
provenance is inspectable build metadata and is not a runtime dependency.

After all CAR files and placeholder entries are staged, Cozy projects the
accepted CNCF runtime range into CNCF-owned
`car-runtime-manifest.json` and records the SHA-256 of every other regular
entry. The name is reserved from `src/main/car`; generated runtime evidence
cannot be replaced by source content. This manifest is runtime input, whereas
generation provenance remains build-time metadata.

## Review and Publication Gates

CAR lint and the Review Provider evaluate the same unmerged project contract
before resolved-JAR admission. An accepted report exposes the exact generator,
compile coordinate, and runtime range; a rejected report preserves the same
typed diagnostic code and structured detail as the package gate.

CAR publication applies that project-only decision before creating repository
output. Its catalog runtime projection comes from the accepted contract, not
from merged operation defaults. Resolved-JAR identity remains a package-only
responsibility. Projects that do not declare a CAR retain the legacy partial
publication behavior.

The normal sbt-cozy path publishes the archive produced by `cozyBuildCar` as a
prebuilt CAR. Prebuilt is an archive transport mode, not an admission bypass.
For a declared CAR, publication revalidates the package-generated runtime
manifest against the project-owned identity and runtime contract, requires an
exact archive path set, and verifies every recorded SHA-256 digest. A generated
release CAR must also carry bytes equal to an immutable snapshot of the owning
project's generation provenance after that evidence has been revalidated
against current CML/generated Scala output and the accepted exact CNCF/Cozy
pair. Cozy snapshots the prebuilt CAR before admission and copies the same
snapshot into the repository. Validation completes before repository writes.

For a declared CAR, the project component version also owns publication
lifecycle. The explicit publication version must equal it; a command-line
value cannot turn a SNAPSHOT project into release output or publish a different
release identity. CAR/SAR generation likewise requires the project-owned exact
Cozy and CNCF compile coordinates before delegation.

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

When a CAR supplies `assembly-descriptor.yaml`, its subsystem version and its
primary component entry must equal the CAR name/component version projected
from `project.yaml`. Packaging validates this self coordinate before writing
the archive. Assembly dependency coordinates remain independently authored
runtime inputs.
