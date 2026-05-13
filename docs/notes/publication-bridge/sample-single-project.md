# Single-Sample Project Publication Bridge

status=concept-note
published_at=2026-05-13
audience=sample-single project contributors and AI agents

---

# Purpose

This note explains the Cozy to SmartDox publication pattern for a
`sample-single` project.

A `sample-single` project is one sbt project that represents one executable
sample or tutorial. It is smaller than `sample-multi`: there is no child sample
collection under `samples/`.

The repository is still not a website generator. Cozy extracts publication
metadata and SmartDox site renders it.

---

# Mental Model

```text
single sample sbt project
  -> Cozy publish-project
  -> publish.d metadata
  -> SmartDox site rendering
```

If release artifacts exist:

```text
warehouse artifacts
  -> Cozy index-warehouse
  -> publish.d artifact/release metadata
  -> SmartDox site rendering
```

---

# Shape

Typical structure:

```text
sample-project/
  build.sbt
  project.yaml
  .cozy/config.yaml
  README.md
  src/
    main/
    test/
  run.sh
```

The project is the publication unit.

There is no collection-level `samples/` scan unless the project intentionally
has source files under that path.

---

# Project Metadata And Configuration

Public metadata goes in `project.yaml`:

```yaml
project:
  name: hello-component-sample
  title: Hello Component Sample
  path: textus/samples/hello-component
  kind: sample-single
  summary: Minimal Textus component sample.
  description: Public metadata for the sample page and generated catalogs.
```

Local operation settings go in `.cozy/config.yaml`:

```yaml
publication:
  output: /Users/asami/src/dev2025/simplemodeling-org/publish.d

warehouse:
  repository: /Users/asami/src/maven-repository
  maven:
    coordinates: []
  repository_artifacts:
    include: []
```

Guidelines:

- Use `kind: sample-single` when the repository itself is one sample.
- Use `name` as the stable slug.
- Use `title` as the human-facing display text.
- Use `path` for the SmartDox site location.
- Do not create a `samples/` directory just to satisfy `sample-multi` habits.

---

# Operations

Generate project metadata:

```console
sbt cozyPublishProject
```

or:

```console
cozy publish-project . --kind=sample-single --save=/Users/asami/src/dev2025/simplemodeling-org/publish.d
```

Expected output:

```text
publish.d/
  metadata/catalog/projects/<name>.yaml|json
  metadata/catalog/samples/<name>.yaml|json
  metadata/samples/<name>/metadata.yaml|json
  metadata/source-manifest/<name>.yaml|json
```

Artifact indexing is optional. Use it only when the sample has distributed
artifacts that should appear in download/release pages.

---

# SmartDox Interpretation

SmartDox site should render a single sample page or small sample section from
`publish.d`.

Expected page roles:

| Page | Source data |
|---|---|
| Sample page | `metadata/samples/<name>/metadata.*` |
| Project metadata page | `metadata/catalog/projects/<name>.*` |
| Source manifest page | `metadata/source-manifest/<name>.*` |
| Optional download page | `metadata/artifacts/maven/<name>.*`, `metadata/artifacts/repository/<name>.*` |

---

# Common Mistakes

- Do not model one sample repository as `sample-multi` unless it actually owns many child samples.
- Do not hand-edit `publish.d`.
- Do not add SmartDox rendering scripts to the sample project.
- Do not use a human title as `name`; keep `name` URL-safe.

---

# Current Limitations

Current v1 metadata is project-level. Rich sample-specific teaching metadata may
need future Cozy extensions.
