# SAR Subsystem Project Publication Bridge

status=concept-note
published_at=2026-05-13
audience=SAR subsystem project contributors and AI agents

---

# Purpose

This note explains the Cozy to SmartDox publication pattern for a `sar` project.

A `sar` project builds a CNCF subsystem archive (`.sar`). The source project owns
subsystem descriptors and subsystem-level packaging inputs. Cozy/sbt-cozy owns
SAR packaging and publication metadata extraction. SmartDox site owns rendering.

---

# Mental Model

```text
SAR subsystem sbt project
  -> sbt-cozy cozyBuildSAR / cozyDistributeSAR
  -> warehouse/repository/sar/<module>/<version>/<module>-<version>.sar

SAR subsystem sbt project
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
subsystem-project/
  build.sbt
  project.yaml
  .cozy/config.yaml
  src/
    main/
      cozy/
      resources/
        application.conf
  subsystem-descriptor.yaml
```

The exact descriptor location depends on the project, but the publication
identity should remain repository-level.

---

# Project Metadata And Configuration

Public metadata goes in `project.yaml`:

```yaml
project:
  name: textus-tutorial-subsystem
  title: Textus Tutorial Subsystem
  path: subsystems/textus/tutorial
  kind: sar
  summary: Textus tutorial subsystem package.
  description: Public metadata for subsystem catalogs and generated documentation.
```

Local operation settings go in `.cozy/config.yaml`:

```yaml
publication:
  output: /Users/asami/src/dev2025/simplemodeling-org/src/main/publication

packaging:
  kind: sar
  sar:
    name: textus-tutorial-subsystem-0.1.0

warehouse:
  repository: /Users/asami/src/maven-repository
  maven:
    coordinates: []
  repository_artifacts:
    include:
      - sar
    modules:
      - textus-tutorial-subsystem
```

Guidelines:

- `publication.name` should match the subsystem publication identity.
- `warehouse.repository_artifacts.modules` should match the SAR repository module.
- Use `kind: sar` so Cozy and readers understand this as a subsystem publication.
- Keep subsystem runtime descriptors separate from SmartDox rendering concerns.

---

# Operations

Generate project metadata:

```console
sbt cozyPublishProject
```

Build SAR for local validation:

```console
sbt cozyBuildSAR
```

Distribute a release SAR:

```console
sbt cozyDistributeSAR
```

Expected release layout:

```text
warehouse/repository/sar/<module>/<version>/<module>-<version>.sar
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
repository artifact metadata, and release metadata.

Maven metadata is optional and only appears when configured coordinates are
present.

---

# SmartDox Interpretation

SmartDox site can render:

| Page | Source data |
|---|---|
| Subsystem page | `metadata/catalog/projects/<name>.*` |
| Source manifest page | `metadata/source-manifest/<name>.*` |
| Download page | `metadata/artifacts/repository/<name>.*` |
| Release history page | `metadata/releases/<name>.*` |

---

# Common Mistakes

- Do not treat SAR as a collection of samples.
- Do not embed SmartDox rendering logic in subsystem descriptors.
- Do not hand-edit `src/main/publication` release metadata.
- Do not index every SAR in a shared warehouse.
- Do not confuse local `repository.d` development staging with release warehouse metadata.

---

# Current Limitations

Current v1 metadata records project identity, source manifest, artifact files,
versions, and checksums. Rich subsystem topology pages may need future Cozy
metadata extensions.
