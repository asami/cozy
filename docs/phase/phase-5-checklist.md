# Phase 5 Checklist

## EPC-01: `publish.d` Output Contract

- [x] Define top-level output directories
- [x] Define project identity metadata
- [x] Define catalog entry shape
- [x] Define sample metadata shape
- [x] Define source snapshot manifest shape
- [x] Define repository/artifact metadata shape

## EPC-02: Cozy Command

- [x] Add command name and help entry
- [x] Accept source sbt project path
- [x] Accept `--save=<publish.d>` output path
- [x] Keep output deterministic
- [x] Fail explicitly for missing project roots

## EPC-03: sbt Project Metadata Extraction

- [x] Read `build.sbt`
- [x] Read `project/build.properties`
- [x] Detect artifact name
- [x] Detect organization
- [x] Detect version
- [x] Detect Scala version
- [ ] Detect Cozy/CNCF-related settings when present

## EPC-04: Catalog and Sample Metadata Generation

- [x] Generate `/catalog/projects/<id>.yaml`
- [x] Generate `/catalog/samples/<id>.yaml` when sample metadata is available
- [x] Generate `/samples/<id>/metadata.yaml`
- [x] Align fields with AI-facing discovery use cases
- [ ] Preserve relationship to BoK articles when known

## EPC-05: Source Snapshot Manifest

- [x] Generate stable source file inventory
- [x] Exclude build outputs and local caches
- [x] Record checksums or equivalent stable identity
- [x] Support future `/samples/<id>/source` publication

## EPC-06: Tests

- [x] Add minimal sbt fixture project
- [x] Verify deterministic `publish.d` generation
- [x] Verify generated catalog paths
- [x] Verify generated sample metadata
- [x] Verify missing project failure
- [x] Verify warehouse Maven metadata generation
- [x] Verify warehouse CAR/SAR metadata generation
- [x] Verify unrelated repository artifacts are excluded

## EPC-07: SmartDox Site Handoff

- [x] Document how SmartDox site consumes generated `publish.d`
- [x] Document which generated files map to `/catalog`
- [x] Document which generated files map to `/samples`
- [x] Document which generated files map to `/repository`
- [x] Document what remains owned by SmartDox rendering

## EPC-08: Warehouse Indexing

- [x] Add `index-warehouse` command
- [x] Add sbt-bridge `index-warehouse` action
- [x] Add sbt-cozy `cozyIndexWarehouse`
- [x] Read configured Maven coordinates only
- [x] Read configured CAR/SAR repository artifact modules only
- [x] Generate `metadata/artifacts/maven/<name>.yaml|json`
- [x] Generate `metadata/artifacts/repository/<name>.yaml|json`
- [x] Generate `metadata/releases/<name>.yaml|json`
- [x] Compute SHA-256 and read `.sha1` / `.md5` sidecars
- [x] Prefer non-SNAPSHOT `latestRelease`
