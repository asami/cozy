# Publication Registry Metadata Specification

status=implemented
published_at=2026-05-13
schema=cozy.publish-project.v1

---

# Overview

`src/main/publication` is the git-managed publication registry consumed by SmartDox site BoK publication flows. In the final public site, this workspace is published under `metadata/`.

Cozy generates it from an sbt project with:

```console
cozy publish-project <project-dir> [--save <registry-dir>]
```

or through sbt-cozy:

```console
sbt cozyPublishProject
```

Cozy also indexes a release warehouse into download/repository release metadata:

```console
cozy index-warehouse <warehouse-dir> --save <registry-dir> --name <publication-name>
```

or through sbt-cozy:

```console
sbt cozyIndexWarehouse
```

Maven repository metadata is generated from the Maven repository itself:

```console
cozy publish-maven-repository <repository-dir> --save <registry-dir> --name <publication-name>
```

Sample archives are distributed to the warehouse separately:

```console
cozy distribute-samples <project-dir> --warehouse <warehouse> --name <publication> --version <version> [--dry-run]
```

or through sbt-cozy:

```console
sbt cozyDistributeSamples
```

`--dry-run` prints the planned collection and individual sample archive paths without writing ZIP files. Dry-run accepts SNAPSHOT versions because it is a planning operation, not a release distribution.

The registry output is deterministic and uses one JSON publication bundle per project. The bundle contains an `entries` array. Each entry has a public metadata `path`, a logical `key`, and a `metadata` object. SmartDox generates both the public `metadata/...` files and Antora pages from this bundle instead of copying registry directories directly.

`publish-project` writes project/source metadata and expected repository/download deployment metadata. `index-warehouse` writes release metadata for download/repository artifacts and checks that expected repository/download paths exist in the warehouse. `publish-maven-repository` writes the Maven repository publication bundle directly from repository contents.

`publish-project` updates one file: `${publication}.json`. On the next publish, Cozy replaces that one bundle, so removed samples, removed publication pages, and removed source entries disappear from the source of truth. Other publication bundles are not touched. Multiple projects publish into the same registry root, but each publication keeps its own file boundary. If a new bundle entry path is already provided by another publication bundle, Cozy fails instead of creating ambiguous generated metadata. `unpublish-project --save <registry-dir> --name <publication>` removes `${publication}.json`.

---

# Public Site Layout

Multilingual BoK example:

```text
www.simplemodeling.org/
  ja/textus/
  ja/textus/tutorial/
  en/textus/
  en/textus/tutorial/
  repository/maven/
  repository/car/
  repository/sar/
  repository/download/
  maven/                 # legacy compatibility only
  metadata/
```

Non-multilingual BoK example:

```text
example.org/
  textus/
  textus/tutorial/
  repository/maven/
  repository/car/
  repository/sar/
  repository/download/
  maven/                 # legacy compatibility only
  metadata/
```

Cozy's `publication.path` is the logical article path without a language prefix. SmartDox site adds language prefixes when the BoK is multilingual.

---

# Registry Output Layout

For project name `${name}`:

```text
src/main/publication/
  ${name}.json
```

`${name}` is the publication stable name. It is used as the BoK key, file-name stem, and URL-safe identity.

`publish-project` writes project/source metadata plus expected repository/download metadata as entries in `${name}.json`. `index-warehouse` replaces the release entry for download/repository artifacts and verifies that expected repository/download paths exist in the warehouse. `publish-maven-repository` owns Maven repository publication bundles and `metadata/artifacts/maven/${name}.json`.

The public site generator materializes selected entries as generated files such as:

```text
website.d/metadata/catalog/projects/${name}.json
website.d/metadata/samples/${name}/metadata.json
website.d/metadata/artifacts/download/${name}.json
website.d/metadata/source-manifest/${name}.json
```

These files are generated projections of the publication bundle. They are not copied from the registry as raw directory contents.

For `sample-multi`, each child sample is expanded as a versioned web-readable source tree under `metadata/samples/${name}/items/${sample}/${version}/files`.

---

# Project Metadata And Local Config

Public project metadata is defined by `project.yaml` at the project root:

```yaml
project:
  name: textus-tutorial
  title: Textus Tutorial
  kind: sample-multi
  path: textus/tutorial/textus-tutorial
  summary: Textus tutorial sample collection.
  description: Tutorial samples for Cozy Textus users.
```

For `sample-multi`, each child sample can define its own `project.yaml`:

```yaml
project:
  title: Hello Tutorial
  summary: Minimal Textus hello-world tutorial.
  description: Shows the smallest executable Textus sample project.
```

Meaning:

| Field | Meaning |
|-------|---------|
| `project.name` | Stable URL-safe slug used for BoK key and generated file names. |
| `project.title` | Human-readable display title. |
| `project.kind` | Project kind: `car`, `sar`, `sample-single`, or `sample-multi`. |
| `project.path` | Optional SmartDox site logical article path without language prefix. |
| `project.summary` | Short project summary for catalogs, cards, and overview pages. |
| `project.description` | Longer project description for generated pages and metadata consumers. |

The legacy top-level keys `name`, `title`, `kind`, `path`, `summary`, and `description` are also accepted for compact files.

Only `project.yaml` and `project.yml` are auto-detected. `publication.yaml` is intentionally not auto-detected in v1 because the current model is one project to one publication. If multiple publications become necessary, a separate `publications/` structure should be added explicitly.

`.cozy/config.yaml` is for local Cozy operation defaults. It can contain output paths, sample directory mapping, source manifest excludes, and warehouse indexing settings. It is intentionally not the primary place for public descriptive metadata.

Example:

```yaml
publication:
  output: /Users/asami/src/dev2025/simplemodeling-org/src/main/publication
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
  download:
    samples:
      - textus-tutorial
```

Meaning:

| Field | Meaning |
|-------|---------|
| `publication.output` | Output `src/main/publication` directory. |
| `publication.samples_dir` | Directory containing child sample projects for `sample-multi`. |
| `publication.source_manifest.excludes` | Extra source manifest exclude paths or directory names. |
| `warehouse.repository` | Warehouse root used by `index-warehouse`. |
| `warehouse.maven.coordinates` | Maven coordinates indexed by Maven repository publication operations from `${warehouse.repository}/maven`. |
| `warehouse.repository_artifacts.include` | Repository artifact types checked against warehouse, such as `car` and `sar`. |
| `warehouse.repository_artifacts.modules` | Repository artifact module directories checked under `${warehouse.repository}/repository/<type>/<module>`. |
| `warehouse.download.samples` | Sample publications checked under `${warehouse.repository}/repository/download/<publication.path>`. Legacy `${warehouse.repository}/download/samples/<publication>` remains readable only when the existing expected metadata points to that legacy path. |

Resolution priority for public metadata is CLI option, `project.yaml`, `.cozy/config.yaml` compatibility fields, sbt setting, then directory-derived default.

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

`path` is optional. When present, it must be slash-separated slug segments without a language prefix, for example:

```text
textus/tutorial/textus-tutorial
```

`metadata` and `repository` are reserved top-level path segments and cannot be used as article paths.

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
  source_manifest: metadata/source-manifest/textus-tutorial
  path: "textus/tutorial/textus-tutorial"
```

JSON form:

```json
{
  "publication": {
    "sourceManifest": "metadata/source-manifest/textus-tutorial",
    "path": "textus/tutorial/textus-tutorial"
  }
}
```

`path` is omitted when not configured.

---

# Catalog Project Metadata

Path:

```text
metadata/catalog/projects/${name}.yaml
metadata/catalog/projects/${name}.json
```

Type:

```text
catalog-project
```

Purpose:

- registers the project-level BoK item
- points to the project metadata canonical record
- stays intentionally thin for catalog aggregation

YAML shape:

```yaml
schema: "cozy.publish-project.v1"
type: "catalog-project"
project:
  name: "textus-tutorial"
  title: "Textus Tutorial"
  kind: "sample-multi"
  metadata: "metadata/projects/textus-tutorial/metadata"
```

The canonical project record is `metadata/projects/${name}/metadata`.

---

# Catalog Sample Metadata

Path:

```text
metadata/catalog/samples/${name}.yaml
metadata/catalog/samples/${name}.json
```

Type:

```text
catalog-sample
```

Purpose:

- registers the project as a sample publication entry
- points to the sample metadata canonical record
- points to the collection download artifact when sample downloads exist
- stays intentionally thin for catalog aggregation

YAML shape:

```yaml
schema: "cozy.publish-project.v1"
type: "catalog-sample"
sample:
  name: "textus-tutorial"
  title: "Textus Tutorial"
  kind: "sample-multi"
  metadata: "metadata/samples/textus-tutorial/metadata"
  download:
    artifact: "metadata/artifacts/download/textus-tutorial"
    types:
      - "sample-collection-zip"
      - "sample-zip"
```

---

# Sample Metadata

Path:

```text
metadata/samples/${name}/metadata.yaml
metadata/samples/${name}/metadata.json
```

Type:

```text
sample-metadata
```

Purpose:

- stores sample-specific publication metadata
- points to the download artifact for collection and child sample ZIPs
- links the sample to the source manifest
- carries the optional SmartDox site publication path

For `sample-multi`, Cozy also writes versioned child sample metadata and web-expanded sample files:

```text
metadata/samples/${name}/items/${sample}/${version}/metadata.yaml
metadata/samples/${name}/items/${sample}/${version}/metadata.json
metadata/samples/${name}/items/${sample}/${version}/files/...
metadata/samples/${name}/items/${sample}/latest.json
```

The version is the parent sbt project version passed through `publish-project`.

The catalog traversal is explicit:

```text
metadata/catalog/samples/${name}
  -> metadata/samples/${name}/metadata
  -> metadata/artifacts/download/${name}
  -> sample-collection-zip and sample-zip files
```

For `sample-multi`, the collection metadata also points to each child sample item metadata:

```yaml
download:
  artifact: "metadata/artifacts/download/textus-tutorial"
  types:
    - "sample-collection-zip"
    - "sample-zip"
samples:
  - name: "01-hello"
    metadata: "metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata"
```

Each child sample metadata includes a download reference for the expected sample ZIP:

```yaml
sample:
  download:
    artifact: "metadata/artifacts/download/textus-tutorial"
    type: "sample-zip"
```

`publish-project` writes this reference and the matching `metadata/artifacts/download/${name}.json` entry in `${name}.json` without checking warehouse contents. `index-warehouse` later verifies the referenced warehouse path.

---

# Repository Artifact Metadata

Path:

```text
metadata/artifacts/repository/${name}.yaml
metadata/artifacts/repository/${name}.json
```

Type:

```text
repository-artifact
```

Purpose:

- records expected CAR/SAR runtime repository artifact paths for this publication
- keeps repository download metadata separate from Maven artifact metadata
- provides stable warehouse-relative paths for SmartDox site

Current v1 shape:

```yaml
artifact:
  layer: "repository"
  status: "planned"
  kinds:
    - type: "car"
      latest_release: "0.1.0"
      versions: ["0.0.9", "0.1.0"]
    - type: "sar"
      latest_release: "0.1.0"
      versions: ["0.1.0"]
  files:
    - warehouse_path: "repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"
      public_path: "repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"
      name: "textus-tutorial-0.1.0.car"
      version: "0.1.0"
      type: "car"
      module: "textus-tutorial"
      extension: "car"
      expected: true
```

---

# Download Artifact Metadata

Path:

```text
metadata/artifacts/download/${name}.yaml
metadata/artifacts/download/${name}.json
```

Type:

```text
download-artifact
```

Purpose:

- records expected user-facing downloadable archives under `${warehouse.repository}/repository/download`
- records sample ZIP archive paths separately from CAR/SAR runtime repository artifacts
- provides stable warehouse and public download paths for SmartDox sample pages

Sample ZIP layouts:

```text
warehouse/repository/download/${publication.path}/${version}/${name}-${version}.zip
warehouse/repository/download/${publication.path}/${sample}/${version}/${sample}-${version}.zip
```

The collection archive contains all child sample directories. Individual sample archives contain one child sample project.
When `publication.path` is not configured, Cozy falls back to the legacy-compatible `samples/${name}` base.
`index-warehouse` does not rewrite planned download paths. If `publish-project`
expects the canonical `repository/download/<publication.path>/...` location but only legacy
`download/samples/<publication>/...` files exist, `index-warehouse` reports the
missing canonical artifacts so the distribution path bug is visible.

Current v1 shape:

```yaml
artifact:
  layer: "download"
  status: "planned"
  kinds:
    - type: "sample-collection-zip"
      latest_release: "0.1.0"
      versions: ["0.1.0"]
    - type: "sample-zip"
      latest_release: "0.1.0"
      versions: ["0.1.0"]
  files:
    - warehouse_path: "repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"
      public_path: "repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"
      name: "textus-tutorial-0.1.0.zip"
      version: "0.1.0"
      type: "sample-collection-zip"
      module: "textus-tutorial"
      sample: ""
      extension: "zip"
      expected: true
    - warehouse_path: "repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip"
      public_path: "repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip"
      name: "01-hello-0.1.0.zip"
      version: "0.1.0"
      type: "sample-zip"
      module: "textus-tutorial"
      sample: "01-hello"
      extension: "zip"
      expected: true
```

---

# Maven Artifact Metadata

Path:

```text
metadata/artifacts/maven/${name}.yaml
metadata/artifacts/maven/${name}.json
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
    - warehouse_path: "maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar"
      public_path: "repository/maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar"
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

`publish-maven-repository` indexes configured coordinates when `--maven-coordinates` is given. When omitted, it scans `${repository.dir}/maven` and derives coordinates from the Maven directory layout.

`index-warehouse` does not create, replace, or remove Maven artifact metadata. CAR, SAR, and sample distribution metadata are intentionally separate from Maven repository metadata.

---

# Release Metadata

Path:

```text
metadata/releases/${name}.yaml
metadata/releases/${name}.json
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
        - {"layer":"maven","groupId":"org.example","artifactId":"textus-tutorial_3","warehousePath":"maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar","publicPath":"repository/maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar"}
        - {"layer":"repository","type":"car","warehousePath":"repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car","publicPath":"repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"}
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
  repository/download/<publication-path>/<version>/<publication>-<version>.zip
  repository/download/<publication-path>/<sample>/<version>/<sample>-<version>.zip
```

Public URL mapping:

```text
warehouse/maven           -> /repository/maven
warehouse/repository/car  -> /repository/car
warehouse/repository/sar  -> /repository/sar
warehouse/repository/download -> /repository/download
```

`/repository/maven` is the canonical public location for Maven artifacts from
the next published version onward. Existing artifacts already published under
top-level `/maven` remain in place for backward compatibility, but new
`publicPath` values should use `/repository/maven`.

For repository artifacts, Cozy scans only configured modules. If no repository module is configured, the publication `name` is used as the default module. Cozy prefers a version directory when present. If no version directory is found, it falls back to parsing the version from the file name.

SmartDox site consumes the generated `src/main/publication` metadata. It should not scan warehouse directly.

---

# Source Manifest

Path:

```text
metadata/source-manifest/${name}.yaml
metadata/source-manifest/${name}.json
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
maven-repository
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

- Project-level source publication remains manifest-only.
- `sample-multi` child sample source trees are recorded in source manifests. They are not copied as registry files.
- `publish-project` generates expected repository/download metadata as bundle entries; `index-warehouse` verifies those paths against warehouse contents.
- SmartDox site rendering is a downstream consumer and is outside this metadata writer.
