# Phase 21: Component Repository Discovery

Status: active

Start date: 2026-07-21

## Goal

Make CAR and SAR artifacts discoverable as first-class Component Repository
entries without relying on HTTP directory listing, repository crawling, or
prior knowledge of every artifact ID.

Phase 21 introduces a versioned public repository index over the existing
per-artifact CAR/SAR catalogs. Cozy publishes and validates that index, Textus
Launcher retrieves and caches it for released-artifact discovery, CNCF
Launcher presents the matching development/local view, and Cozy BoK consumes
the same contract for Component Repository knowledge pages.

## Scope

In scope:

- a CNCF-owned, versioned Component Repository index contract;
- explicit CAR and SAR entries with kind, artifact identity, catalog link,
  lifecycle status, and recommended/stable selection summaries;
- preservation of the existing detailed catalogs under
  `repository/catalog/car/<artifact-id>.*` and
  `repository/catalog/sar/<artifact-id>.*`;
- deterministic and atomic index updates from `cozy publish-car` and
  `cozy publish-sar`;
- index/detail consistency validation in Cozy repository and CAR lint paths;
- local, cache, and configured-public repository source precedence;
- Textus Launcher list, show, and refresh commands over explicit repository
  indexes;
- CNCF Launcher development/local artifact listing without arbitrary source
  tree scanning;
- Cozy BoK CAR/SAR dashboards and detail navigation based on the index;
- source, cache, timestamp, and diagnostic provenance for every listed entry;
- compatibility with repositories that only support known-artifact catalog
  resolution and do not yet publish an index.

Out of scope:

- HTTP directory listing or recursive public repository crawling;
- arbitrary scanning of a user's home or source directories;
- downloading CAR/SAR archives merely to produce a list;
- process discovery, start/stop operations, or runtime health ownership;
- Control Center favorites, ownership, labels, or user-specific policy;
- silently treating a known but inactive CAR as an unhealthy runtime instance;
- component skill installation, which belongs to Phase 22.

## Contract Direction

The canonical public discovery resource is:

```text
repository/catalog/index.json
```

The CNCF specification owns its final schema and compatibility rules. The v1
shape must include at least:

```text
schemaVersion
generatedAt
artifacts[]
  kind: car | sar
  artifactId
  catalog
  status
  recommended
  latestStable
  latestSnapshot
```

The index is a discovery summary, not the source of detailed version metadata.
After selecting an entry, a consumer reads the referenced per-artifact catalog
and validates that its kind and artifact identity agree with the index.

An index entry never grants execution authority. It records artifact
availability only. Runtime registration and health remain separate facts.

## Command Direction

Textus Launcher provides the released-artifact surface:

```text
textus repository list [--kind car|sar] [--source <source>]
textus repository show <artifact>
textus repository refresh [<source>]
```

CNCF Launcher provides the development/local surface using the same normalized
entry model:

```text
cncf repository list [--kind car|sar] [--include-development]
cncf repository show <artifact-or-component-dir>
```

Command names are finalized by each launcher specification, but the output
identity and source-provenance contract must remain equivalent.

## Stage 21.1: Repository Index Contract

Stage Status:

- Current status: DONE
- Owner: CNCF
- Checklist basis: `CR21-01`

Focus:

- define the schema, identity, path, lifecycle status, and compatibility rules;
- define safe source/snapshot diagnostics separately from runtime health;
- publish shared fixtures for Cozy and both launchers.

Verified implementation status:

- CNCF now owns `ComponentRepositoryIndex`, its strict JSON codec, and the
  bundled `component-repository-index.schema.json` resource.
- The codec normalizes entries by `(kind, artifactId)`, rejects duplicate
  identities, unsupported schema/status/kind values, unknown fields, malformed
  timestamps, unsafe paths, and catalog kind/identity mismatches.
- Shared valid, duplicate, and traversal fixtures establish the consumer
  contract for Cozy and both launchers. Focused CNCF executable specifications
  pass for deterministic rendering, malformed input, and availability/runtime
  separation.

## Stage 21.2: Cozy Publication and Validation

Stage Status:

- Current status: DONE
- Owner: Cozy
- Checklist basis: `CR21-02`

Focus:

- update the public index during CAR/SAR publication;
- preserve unrelated entries while replacing only the published identity;
- atomically publish canonical JSON after validating every detailed catalog;
- provide network-free `cozy lint repository` validation and include the same
  check in CAR lint when a repository index is present.

Verified implementation status:

- `publish-car` and `publish-sar` now merge or remove their index entry and
  retain unrelated CAR/SAR entries in deterministic identity order.
- Cozy rejects malformed, duplicate, traversing, identity-conflicting, or
  selector-stale indexes before replacing the public index.
- `cozy lint repository <repository-root>` validates the local index/detail
  boundary in text or JSON form without network access.
- Focused executable specifications cover canonical rendering, invalid input,
  atomic persistence, CAR and SAR publication, unrelated-entry preservation,
  stale selectors, CLI help, and integrated CAR lint regression.

## Stage 21.3: Launcher Discovery

Stage Status:

- Current status: DONE
- Owner: Textus Launcher / CNCF Launcher
- Checklist basis: `CR21-03` and `CR21-04`

Focus:

- retrieve configured public indexes through bounded refresh;
- combine public, cache, local, and admitted development entries according to
  explicit precedence;
- expose source provenance and diagnostics without leaking credentials or
  private paths.

Verified Textus Launcher status:

- `textus repository list`, `show`, and bounded `refresh` consume the strict
  CNCF index contract without changing known-artifact resolution.
- Listing combines live local and validated cached public indexes without
  network or archive access; local entries take precedence and equal-priority
  conflicts are diagnosed deterministically.
- Refresh caches source, successful retrieval time, last-attempt time, schema,
  and stale diagnostics. Invalid index/detail responses do not replace the last
  validated cache.
- Detailed YAML and JSON catalogs are checked for identity, lifecycle status,
  and selectors before selection output is accepted.
- Credential-bearing URL parts and local absolute paths are removed from
  user-facing source diagnostics.

Verified CNCF Launcher status:

- `cncf repository list` and `show` read the machine-local repository index
  without loading the CNCF runtime or coupling discovery to process lifecycle.
- Development checkouts are admitted only by `--include-development`, explicit
  `--development-dir`, or a directory passed directly to `show`; identity and
  version come from `project.yaml` rather than directory names.
- Development and local artifacts use the same lifecycle and selector columns
  as Textus Launcher, with development precedence represented separately by
  origin.
- Malformed local indexes and development descriptors are ignored with safe
  diagnostics, and detailed output reports source freshness without exposing
  absolute local paths.

## Stage 21.4: BoK Component Repository

Stage Status:

- Current status: PLANNED
- Owner: Cozy BoK
- Checklist basis: `CR21-05`

Focus:

- extend the existing CAR knowledge surface to complete CAR and SAR lists;
- link repository entries to Project, descriptor, ABI, CML, model metadata,
  Help, Manual, OpenAPI, MCP, and later skill-bundle knowledge when present;
- preserve explicit empty and unavailable-source diagnostics.

## Completion Criteria

Phase 21 closes when a published repository can explicitly enumerate CAR and
SAR catalog entries, Cozy can update and validate the index deterministically,
Textus Launcher can list and resolve public/local artifacts, CNCF Launcher can
show the equivalent development/local identities, and Cozy BoK can render CAR
and SAR Component Repository pages from the same contract. All routes must be
covered by executable specifications, and no list operation may depend on
directory crawling or archive download.

## References

- `docs/phase/phase-21-checklist.md`
- `docs/phase/phase-13.md`
- `docs/phase/phase-14.md`
- `docs/journal/2026/07/managed-car-catalog-model-and-packaging-handoff-2026-07-21.md`
- CNCF journal: `2026-07-21-managed-car-catalog-contract.md`
