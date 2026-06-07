# CAR Component Project Publication Bridge

status=concept-note
published_at=2026-05-13
audience=CAR component project contributors and AI agents

---

# Purpose

This note explains the Cozy to SmartDox publication pattern for a `car` project.

A `car` project builds a CNCF component archive (`.car`). The source project owns
component implementation and packaging inputs. Cozy/sbt-cozy owns CAR packaging
and publication metadata extraction. SmartDox site owns rendering.

---

# Mental Model

```text
CAR component sbt project
  -> sbt-cozy cozyBuildCAR / cozyDistributeCAR
  -> warehouse/repository/car/<module>/<version>/<module>-<version>.car

CAR component sbt project
  -> Cozy publish-project
  -> publication registry project/source metadata

warehouse
  -> Cozy index-warehouse
  -> publication registry artifact/release metadata
  -> SmartDox site rendering
```

---

# Shape

Typical structure:

```text
component-project/
  build.sbt
  project.yaml
  conf/cozy/config.yaml
  .cozy/config.yaml
  src/
    main/
      cozy/
      scala/
      car/
        assembly-descriptor.yaml
        config/default.conf
        web/
```

CAR-root files live under `src/main/car` when using sbt-cozy defaults.

---

# Project Metadata And Configuration

Public metadata goes in `project.yaml`:

```yaml
project:
  name: textus-user-account
  title: Textus User Account Component
  path: components/textus/user-account
  kind: car
  summary: User account component for Cozy Textus.
  description: Provides the public metadata used by SmartDox and publication catalogs.
```

Git-managed operation settings go in `conf/cozy/config.yaml`; local overrides go in `.cozy/config.yaml`:

```yaml
publication:
  output: /Users/asami/src/dev2025/simplemodeling-org/src/main/publication

packaging:
  kind: car
  car:
    source_dir: src/main/car
    manifest_metadata:
      component: textus-user-account

warehouse:
  repository: /Users/asami/src/maven-repository
  maven:
    coordinates:
      - org.example:textus-user-account_3
  repository_artifacts:
    include:
      - car
    modules:
      - textus-user-account
```

Guidelines:

- `publication.name` should match the component publication identity.
- `warehouse.repository_artifacts.modules` should match the CAR repository module.
- Do not scan all CAR files in the warehouse.
- Use `publication.title` for display text.

---

# Operations

Generate project metadata:

```console
sbt cozyPublishProject
```

Build CAR for local validation:

```console
sbt cozyBuildCAR
```

Distribute a release CAR:

```console
sbt cozyDistributeCAR
```

Expected release layout:

```text
warehouse/repository/car/<module>/<version>/<module>-<version>.car
```

Generate artifact/release metadata:

```console
sbt cozyIndexWarehouse
```

---

# Expected publication registry Output

```text
src/main/publication/<name>.json
```

The bundle contains entries for project metadata, source manifest metadata,
repository artifact metadata, optional Maven artifact metadata, and release
metadata.

---

# SmartDox Interpretation

SmartDox site can render:

| Page | Source data |
|---|---|
| Component page | `metadata/catalog/projects/<name>.*` |
| Source manifest page | `metadata/source-manifest/<name>.*` |
| Download page | `metadata/artifacts/repository/<name>.*` |
| Release history page | `metadata/releases/<name>.*` |
| Maven artifact page | `metadata/artifacts/maven/<name>.*` when Maven coordinates exist |

---

# Common Mistakes

- Do not put SmartDox page templates into `src/main/car`.
- Do not hand-copy CAR files into `src/main/publication`.
- Do not use `cozyPublishProject` as a binary distribution operation.
- Do not use `cozyIndexWarehouse` before the warehouse contains the released CAR.
- Do not index unrelated CAR modules.

---

# Current Limitations

Current v1 metadata identifies project, source manifest, files, checksums,
versions, and release membership. Rich component capability pages may need
future Cozy metadata extensions.
