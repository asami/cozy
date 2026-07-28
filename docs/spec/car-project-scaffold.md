# CAR Project Scaffold Specification

## Generated Files

For a `car` layout, `cozy init component` and `cozy car-sbt-project` must
generate:

- `project.yaml`;
- `build.sbt`;
- `project/ProjectYamlBuild.scala`;
- `project/build.properties`;
- `project/plugins.sbt`;
- the requested CML, Scala, test, CAR, Web, and script surfaces.

For a `car-sar` layout, the same metadata contract is generated at
`component/project.yaml`, while `ProjectYamlBuild.scala` remains under the root
SBT `project/` directory.

## Metadata Contract

The generated `project.yaml` must declare:

```yaml
build:
  scalaVersion: "3.3.8"
  cozyVersion: "<exact-generator-version>"
  dependencies:
    compile:
      - "org.goldenport::goldenport-cncf:<development-version>"
    test:
      - "org.scalatest::scalatest:3.2.10"

packaging:
  kind: car
  car:
    abi:
      dependencies: []
    manifest_metadata:
      boundedContext: "<bounded-context>"
      domain: "<domain>"
    runtime:
      cncf:
        minimum: "<required-version>"
        excluded: []
        tested:
          - "<tested-version>"
```

`build.cozyVersion` is the exact Cozy generator coordinate used by the
generated build. The CNCF build dependency is the exact generated-code compile
coordinate. `minimum`, `excluded`, and `tested` independently describe runtime
compatibility and validation.

`packaging.car.abi.dependencies` is the canonical declaration of component ABI
dependencies. Each non-empty entry declares `name` and `abiRange`; Maven/JVM
libraries remain under build or CAR-local dependency declarations and must not
be copied into the component ABI dependency surface.

## Build Projection

The generated build must read organization, name, component version, Scala
version, exact Cozy generator version, dependencies, component name, and
descriptor metadata from `project.yaml`.

The generated build must not:

- hardcode CAR identity or dependency versions;
- use `packaging.car.runtime.cncf.minimum` as the compile dependency version;
- generate `BuildVersion.scala`;
- generate `src/main/car/component-descriptor.json`; packaging must derive it
  from the current `project.yaml` identity;
- redefine standard CAR `publish` or `publishLocal` tasks.

These properties are executable in `ModelerScaffoldSpec`.

## Package Admission

For `packaging.kind: car`, packaging must use the unmerged `project.yaml` as
the authority for `build.cozyVersion`, the unique CNCF compile dependency, and
`packaging.car.runtime.cncf`. Merged operation defaults must not replace those
values.

`cozy package-car` must require `--project-dir`. The referenced
`project.yaml` must explicitly classify the project as a CAR; missing or
non-CAR classification must fail before archive output. Optional generic
metadata inspection is not an archive-admission decision.

If `assembly-descriptor.yaml` is packaged, its root `version` and the
`components` entry matching the primary component must both equal
`project.component.version`. Missing or stale assembly self coordinates must
fail before archive output; packaging does not infer or rewrite them.

Packaging must resolve exactly one `META-INF/cncf/runtime.yaml` descriptor from
the actual main/library JAR set. Its root `runtime`, `module`, and `version`
must identify the exact CNCF compile dependency. The resolved version must be
inside the declared minimum/maximum range, absent from `excluded`, and present
in `tested`. Missing, multiple, malformed, mismatched, excluded, or untested
evidence must fail before the CAR archive is accepted.

If `target/cozy/generation-provenance.json` exists, packaging must revalidate
its source, generated Scala identities and bytes, aggregate digest, evidence
digest, CNCF target, and Cozy generator. It must copy accepted bytes unchanged
from the validated immutable snapshot to `generation-provenance.json` in the
CAR and reject contradictory evidence before writing the archive. Provenance
remains optional for non-generated and legacy CAR sources and must not become
a runtime dependency.

Packaging an accepted CAR project contract must also generate
`car-runtime-manifest.json` after final staging. It must preserve the declared
CNCF runtime range and hash every other regular CAR entry. A scaffold source
directory must not provide that reserved filename.

These properties are executable in `CarMetadataCompatibilitySpec` and
`CozyArchivePackagerCv06Spec`; `CozyCarRuntimeManifestSpec` fixes the
deterministic range/digest projection property.

## Review and Publication Admission

Integrated CAR lint and the Review Provider must evaluate the same unmerged
project-only compatibility decision. An accepted report must identify the
exact generator, CNCF compile coordinate, and runtime range. A rejected report
must retain the package gate's typed diagnostic code and structured detail.

CAR publication must evaluate that decision before creating warehouse output.
For an accepted declared CAR, the catalog runtime requirement must be projected
from the accepted contract even when operation defaults disagree. Resolved-JAR
identity remains package evidence and is not required again by Review or
publication.

The publication version must equal `project.component.version`. CAR/SAR
generation must read `build.cozyVersion` and exactly one CNCF compile dependency
from the same unmerged project metadata before launching the delegated
generator. Missing authorities fail; the plugin version and ambient settings
are not substitutes.

These properties are executable in `CozyCarLintSpec`,
`CozyCarReviewProviderSpec`, and `CozyCarPublisherSpec`.
