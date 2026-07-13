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
  dependencies:
    compile:
      - "org.goldenport::goldenport-cncf:<development-version>"
    test:
      - "org.scalatest::scalatest:3.2.10"

packaging:
  kind: car
  car:
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

The build dependency is the exact development coordinate. `minimum`,
`excluded`, and `tested` describe runtime compatibility and validation.

## Build Projection

The generated build must read organization, name, component version, Scala
version, dependencies, component name, and descriptor metadata from
`project.yaml`.

The generated build must not:

- hardcode CAR identity or dependency versions;
- use `packaging.car.runtime.cncf.minimum` as the compile dependency version;
- generate `BuildVersion.scala`;
- generate `src/main/car/component-descriptor.json`; packaging must derive it
  from the current `project.yaml` identity;
- redefine standard CAR `publish` or `publishLocal` tasks.

These properties are executable in `ModelerScaffoldSpec`.
