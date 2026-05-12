# publish.d Metadata Specification

status=implemented
published_at=2026-05-13
schema=cozy.publish-project.v1

---

# Overview

`publish.d` is the generated metadata workspace consumed by SmartDox site BoK publication flows.

Cozy generates it from an sbt project with:

```console
cozy publish-project <project-dir> [--save=<publish.d>]
```

or through sbt-cozy:

```console
sbt cozyPublishProject
```

Cozy also indexes a release warehouse into artifact and release metadata:

```console
cozy index-warehouse <warehouse-dir> --save=<publish.d> --name=<publication-name>
```

or through sbt-cozy:

```console
sbt cozyIndexWarehouse
```

The output is deterministic and consists of YAML/JSON pairs. YAML is for human inspection and SmartDox-oriented workflows. JSON is for machine consumers.

---

# Output Layout

For project name `${name}`:

```text
publish.d/
  catalog/
    projects/${name}.yaml
    projects/${name}.json
    samples/${name}.yaml
    samples/${name}.json
  samples/
    ${name}/
      metadata.yaml
      metadata.json
  repository/
    artifacts/${name}.yaml
    artifacts/${name}.json
  maven/
    artifacts/${name}.yaml
    artifacts/${name}.json
  releases/
    ${name}.yaml
    ${name}.json
  source-manifest/
    ${name}.yaml
    ${name}.json
```

`${name}` is the publication stable name. It is used as the BoK key, file-name stem, and URL-safe identity.

---

# Publication Config Mapping

Project-local defaults can be supplied by `.cozy/config.yaml`:

```yaml
publication:
  name: textus-tutorial
  title: Textus Tutorial
  path: samples/textus/tutorial
  kind: sample-multi
  output: /Users/asami/src/dev2025/simplemodeling-org/publish.d
  samples_dir: samples
  source_manifest:
    excludes:
      - target
      - .git
warehouse:
  repository: /Users/asami/src/maven-repository
  maven:
    coordinates:
      - org.example:textus-tutorial_3
  repository_artifacts:
    include:
      - car
      - sar
    modules:
      - textus-tutorial
```

Meaning:

| Field | Meaning |
|-------|---------|
| `publication.name` | Stable slug used for BoK key and generated file names. |
| `publication.title` | Human-readable display title. |
| `publication.path` | Optional SmartDox site publication path. |
| `publication.kind` | Project kind: `car`, `sar`, `sample-single`, or `sample-multi`. |
| `publication.output` | Output `publish.d` directory. |
| `publication.samples_dir` | Directory containing child sample projects for `sample-multi`. |
| `publication.source_manifest.excludes` | Extra source manifest exclude paths or directory names. |
| `warehouse.repository` | Warehouse root used by `index-warehouse`. |
| `warehouse.maven.coordinates` | Maven coordinates indexed from `${warehouse.repository}/maven`. |
| `warehouse.repository_artifacts.include` | Repository artifact types indexed from warehouse, such as `car` and `sar`. |
| `warehouse.repository_artifacts.modules` | Repository artifact module directories indexed from `${warehouse.repository}/repository/<type>/<module>`. |

CLI options override `.cozy/config.yaml`.

---

# Name And Path Rules

`name` must be a URL-safe slug:

```text
^[a-z0-9][a-z0-9-]*$
```

Examples:

```text
textus-tutorial
cncf-samples
sample-04-crud
```

`path` is optional. When present, it must be slash-separated slug segments, for example:

```text
samples/textus/tutorial
```

`title` is display text and may contain spaces or localized text.

---

# Common Fields

Every generated file has:

```yaml
schema: "cozy.publish-project.v1"
type: "..."
```

Every generated file also contains a `project` object:

```yaml
project:
  name: "textus-tutorial"
  title: "Textus Tutorial"
  kind: "sample-multi"
  organization: "org.example.textussamples"
  version: "0.1.0-SNAPSHOT"
  scala_version: "3.3.6"
  sbt_version: "1.11.7"
```

JSON uses camelCase for Scala/sbt version fields:

```json
{
  "project": {
    "name": "textus-tutorial",
    "title": "Textus Tutorial",
    "kind": "sample-multi",
    "organization": "org.example.textussamples",
    "version": "0.1.0-SNAPSHOT",
    "scalaVersion": "3.3.6",
    "sbtVersion": "1.11.7"
  }
}
```

Local absolute project paths are intentionally not included in public metadata.

---

# Publication Object

Files that describe publication placement contain:

```yaml
publication:
  source_manifest: source-manifest/textus-tutorial
  path: "samples/textus/tutorial"
```

JSON form:

```json
{
  "publication": {
    "sourceManifest": "source-manifest/textus-tutorial",
    "path": "samples/textus/tutorial"
  }
}
```

`path` is omitted when not configured.

---

# Catalog Project Metadata

Path:

```text
catalog/projects/${name}.yaml
catalog/projects/${name}.json
```

Type:

```text
catalog-project
```

Purpose:

- registers the project-level BoK item
- links the project to its source manifest
- carries publication placement when configured

YAML shape:

```yaml
schema: "cozy.publish-project.v1"
type: "catalog-project"
project:
  name: "textus-tutorial"
  title: "Textus Tutorial"
  kind: "sample-multi"
  organization: "org.example.textussamples"
  version: "0.1.0-SNAPSHOT"
  scala_version: "3.3.6"
  sbt_version: "1.11.7"
publication:
  source_manifest: source-manifest/textus-tutorial
  path: "samples/textus/tutorial"
```

---

# Catalog Sample Metadata

Path:

```text
catalog/samples/${name}.yaml
catalog/samples/${name}.json
```

Type:

```text
catalog-sample
```

Purpose:

- registers the project as a sample publication entry
- records the project/sample relation for BoK sample navigation

YAML shape:

```yaml
schema: "cozy.publish-project.v1"
type: "catalog-sample"
project:
  name: "textus-tutorial"
  title: "Textus Tutorial"
  kind: "sample-multi"
sample:
  name: "textus-tutorial"
  project_name: "textus-tutorial"
  kind: "sample-multi"
```

JSON uses `projectName` in the `sample` object.

---

# Sample Metadata

Path:

```text
samples/${name}/metadata.yaml
samples/${name}/metadata.json
```

Type:

```text
sample-metadata
```

Purpose:

- stores sample-specific publication metadata
- links the sample to the source manifest
- carries the optional SmartDox site publication path

---

# Repository Artifact Metadata

Path:

```text
repository/artifacts/${name}.yaml
repository/artifacts/${name}.json
```

Type:

```text
repository-artifact
```

Purpose:

- records CAR/SAR/ZIP style release artifacts found in the warehouse
- keeps repository artifact history separate from Maven artifact metadata
- provides file-level checksums and download paths for SmartDox site

Current v1 shape:

```yaml
artifact:
  layer: "repository"
  status: "available"
  kinds:
    - type: "car"
      latest_release: "0.1.0"
      versions: ["0.0.9", "0.1.0"]
    - type: "sar"
      latest_release: "0.1.0"
      versions: ["0.1.0"]
  files:
    - path: "repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"
      name: "textus-tutorial-0.1.0.car"
      version: "0.1.0"
      type: "car"
      extension: "car"
      classifier: ""
      size: 1234
      sha256: "..."
      sha1: ""
      md5: ""
```

---

# Maven Artifact Metadata

Path:

```text
maven/artifacts/${name}.yaml
maven/artifacts/${name}.json
```

Type:

```text
maven-artifact
```

Purpose:

- records configured Maven coordinates found in `${warehouse.repository}/maven`
- records versions, latest non-SNAPSHOT release, classifiers, extensions, sizes, and checksums
- excludes unrelated Maven coordinates unless explicitly configured

Current v1 shape:

```yaml
artifact:
  layer: "maven"
  status: "available"
  coordinates:
    - group_id: "org.example"
      artifact_id: "textus-tutorial_3"
      latest_release: "0.1.0"
      versions: ["0.1.0", "0.2.0-SNAPSHOT"]
  files:
    - path: "maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar"
      name: "textus-tutorial_3-0.1.0.jar"
      version: "0.1.0"
      type: "jar"
      extension: "jar"
      classifier: ""
      size: 1234
      sha256: "..."
      sha1: "..."
      md5: "..."
```

Maven scan root:

```text
${warehouse.repository}/maven
```

Only configured coordinates are indexed in v1.

---

# Release Metadata

Path:

```text
releases/${name}.yaml
releases/${name}.json
```

Type:

```text
release-history
```

Purpose:

- builds a cross-layer release history from Maven, CAR, SAR, and future repository artifacts
- uses warehouse contents as the binary artifact source of truth
- gives SmartDox site a single release metadata input without scanning warehouse directly

Current v1 shape:

```yaml
release:
  name: "textus-tutorial"
  latest: "0.1.0"
  versions:
    - version: "0.1.0"
      artifacts:
        - {"layer":"maven","groupId":"org.example","artifactId":"textus-tutorial_3","path":"maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar"}
        - {"layer":"repository","type":"car","path":"repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"}
```

`latest` prefers the latest non-SNAPSHOT version. SNAPSHOT versions are included in metadata but are not selected as `latest` unless no release version exists.

---

# Warehouse Layout

The warehouse remains the fact source for distributed binaries.

Default warehouse:

```text
/Users/asami/src/maven-repository
```

Current v1 conventions:

```text
warehouse/
  maven/<group path>/<artifactId>/<version>/<files>
  repository/car/<module>/<version>/<name>-<version>.car
  repository/sar/<module>/<version>/<name>-<version>.sar
```

For repository artifacts, Cozy scans only configured modules. If no repository module is configured, the publication `name` is used as the default module. Cozy prefers a version directory when present. If no version directory is found, it falls back to parsing the version from the file name.

SmartDox site consumes the generated `publish.d` metadata. It should not scan warehouse directly.

---

# Source Manifest

Path:

```text
source-manifest/${name}.yaml
source-manifest/${name}.json
```

Type:

```text
source-manifest
```

Purpose:

- records source files included in the project knowledge extraction scope
- provides deterministic checksums for future change detection and knowledge refresh

Each file entry contains:

| Field | Meaning |
|-------|---------|
| `path` | Project-relative source path using `/`. |
| `size` | File size in bytes. |
| `sha256` | SHA-256 checksum in lowercase hex. |

YAML shape:

```yaml
files:
  - path: "build.sbt"
    size: 1234
    sha256: "..."
```

JSON shape:

```json
{
  "files": [
    {
      "path": "build.sbt",
      "size": 1234,
      "sha256": "..."
    }
  ]
}
```

Default excluded path segments:

```text
target
.git
.bsp
.bloop
.metals
.idea
.cache
.vscode
repository.d
```

Additional excludes can be configured with `publication.source_manifest.excludes`.

---

# Project Kind Detection

If `publication.kind` or `--kind` is not specified, Cozy detects the kind:

| Kind | Detection |
|------|-----------|
| `sar` | SAR marker or subsystem descriptor exists. |
| `car` | CozyPlugin project with CAR source markers. |
| `sample-multi` | Multiple child sbt projects or multiple `samples/*/build.sbt`. |
| `sample-single` | Fallback for an sbt project. |

Supported kinds:

```text
car
sar
sample-single
sample-multi
```

---

# Determinism

Generation is deterministic for the same input tree and options:

- output file paths are derived from `project.name`
- source manifest entries are sorted by relative path
- file checksums are SHA-256
- generated YAML/JSON pairs carry equivalent core fields

---

# Current Limitations

- v1 source publication is manifest-only; it does not copy source trees into `publish.d`.
- repository and Maven artifact metadata are placeholders.
- individual child sample metadata for `sample-multi` is not expanded yet.
- SmartDox site rendering is a downstream consumer and is outside this metadata writer.
