# Phase 24: Component Skill Distribution

Status: planned

Start date: TBD

Dependency: Phase 23 scalar entity persistence round-trip, Phase 21 repository
discovery, and the currently planned CNCF Phase 66 CAR Skill Bundle Contract

## Goal

Distribute a component's Codex skills with its CAR and install the same
validated bundle from either a development checkout or a published artifact.

Phase 24 spans CNCF, Cozy, CNCF Launcher, and Textus Launcher while keeping
their responsibilities separate. CNCF owns the transport-neutral manifest and
validation contract. Cozy validates source and projects it into a CAR. CNCF
Launcher installs an unreleased development bundle. Textus Launcher resolves a
released/local/cache CAR and installs its packaged bundle. Cozy Launcher only
selects and starts the Cozy runtime; it does not become a component skill
installer.

## CNCF Phase 66 Handoff

- CNCF Phase 66 owns the versioned `SkillBundleManifest` model, schema,
  canonical source/archive locations, path and digest rules, compatibility and
  MCP-requirement semantics, deterministic codec/validator, and normative
  valid/invalid fixtures.
- This Phase remains planned and blocked at `SK24-01` until CNCF Phase 66 is
  closed and its exact commit/artifact, design/specification, executable-test,
  and fixture evidence is accepted in the Cozy ledger.
- `SK24-01` is dependency acceptance, not a second CNCF contract
  implementation. Cozy must not redefine any Phase 66-owned rule.
- After the handoff, `SK24-02` is the first Cozy implementation stage and owns
  source validation, `cozy lint skill`, deterministic CAR projection, package
  provenance, and real source/archive packaging equivalence.
- `SK24-03` and `SK24-04` remain CNCF Launcher and Textus Launcher integration
  stages. CNCF Phase 66 does not implement or close them.
- Closing CNCF Phase 66 unblocks this Phase but does not automatically mark
  `SK24-01` DONE; Cozy must verify and record the item-by-item evidence first.

Repository-qualified CNCF references:

- `cloud-native-component-framework:docs/phase/phase-66.md`
- `cloud-native-component-framework:docs/phase/phase-66-checklist.md`

## Scope

In scope:

- a CNCF-owned, versioned `SkillBundleManifest` contract;
- bundle and skill identity, descriptions, relative files, SHA-256 digests,
  compatibility requirements, and optional MCP requirements;
- canonical development-source and CAR archive locations;
- Cozy source validation, deterministic CAR projection, lint, and package
  provenance;
- explicit user/project installation scopes;
- staged, non-destructive install, update, status, and uninstall operations;
- development-source freshness and source/package equivalence diagnostics;
- published/local/cache CAR resolution through Textus Launcher;
- optional, explicit MCP configuration merge with conflict refusal;
- installation provenance sufficient for safe update and uninstall;
- guidance from Component Repository entries to available component skills.

Out of scope:

- automatic installation merely because a CAR is downloaded or executed;
- executing bundled scripts during validation or installation;
- granting filesystem, process, network, MCP, or external-AI authority through
  a manifest declaration;
- silently installing transitive skill dependencies;
- silently rewriting existing Codex skills or MCP configuration;
- starting a CAR server or invoking MCP tools during skill installation;
- using Cozy Launcher as a second skill installer.

## Responsibility Boundary

### CNCF

- owns `SkillBundleManifest`, schema evolution, identity, path, digest,
  compatibility, MCP-requirement, and deterministic validation semantics;
- publishes shared valid/invalid fixtures;
- does not install user files as a runtime side effect.

### Cozy and Cozy Launcher

- Cozy validates declared source files and creates a deterministic CAR bundle;
- `cozy lint` reports missing, undeclared, unsafe, incompatible, or
  digest-mismatched content;
- CAR packaging embeds only manifest-declared files and provenance;
- Cozy Launcher selects the requested release/development Cozy runtime and
  delegates build/lint/package commands; it does not write to Codex scopes.

### CNCF Launcher

- resolves an admitted development directory or explicit CAR;
- defaults development installation to project scope;
- reports source digest, generated/package freshness, and divergence;
- uses the common staged installation behavior and records development
  provenance.

### Textus Launcher

- resolves a CAR from local, cache, or configured public repositories;
- defaults released installation to user scope;
- validates the packaged manifest and all file digests before activation;
- owns released bundle status, update, uninstall, and installed provenance.

## Command Direction

Cozy:

```text
cozy lint skill [<project-dir|car>]
cozy car build
```

CNCF Launcher:

```text
cncf skill list [<component-dir|car>]
cncf skill install [<component-dir|car>] [--scope project|user] [--configure-mcp]
cncf skill status <bundle-or-component>
cncf skill update <bundle-or-component> [--configure-mcp]
cncf skill uninstall <bundle-or-component>
```

Textus Launcher:

```text
textus skill list <artifact>
textus skill install <artifact> [--scope user|project] [--configure-mcp]
textus skill status <bundle-or-artifact>
textus skill update <bundle-or-artifact> [--configure-mcp]
textus skill uninstall <bundle-or-artifact>
```

## Stage 24.1: CNCF Skill Bundle Contract

Stage Status:

- Current status: PLANNED
- Owner: CNCF Phase 66 / Cozy dependency acceptance
- Update rule: mark work complete only from the Phase 24 checklist.
- Checklist basis: `SK24-01`

Focus:

- accept the CNCF Phase 66 schema and canonical source/archive paths after its
  closure and evidence handoff;
- verify its digest, compatibility, collision, dependency, MCP requirement,
  codec, and structured validation outcomes against every `SK24-01` item;
- record the exact CNCF contract, artifact, test, and normative fixture
  identities without reimplementing them in Cozy.

## Stage 24.2: Cozy Validation and CAR Projection

Stage Status:

- Current status: PLANNED
- Owner: Cozy
- Update rule: mark work complete only from the Phase 24 checklist.
- Checklist basis: `SK24-02`

Focus:

- validate source declarations and package only admitted files;
- project a relocation-stable manifest and digests into the CAR;
- prove source/archive equivalence without executing bundle content.

## Stage 24.3: Development Installation

Stage Status:

- Current status: PLANNED
- Owner: CNCF Launcher
- Update rule: mark work complete only from the Phase 24 checklist.
- Checklist basis: `SK24-03`

Focus:

- install admitted development bundles into project or explicit user scope;
- detect stale generated output and source/package divergence;
- preserve unrelated Codex state and recover from interrupted installation.

## Stage 24.4: Published Installation

Stage Status:

- Current status: PLANNED
- Owner: Textus Launcher
- Update rule: mark work complete only from the Phase 24 checklist.
- Checklist basis: `SK24-04`

Focus:

- resolve bundles through Phase 21 repository/local/cache artifact selection;
- stage and activate only completely validated bundles;
- support safe status, update, uninstall, and optional MCP merge.

## Stage 24.5: Component Repository and End-to-End Use

Stage Status:

- Current status: PLANNED
- Owner: Cozy BoK / component driver project
- Update rule: mark work complete only from the Phase 24 checklist.
- Checklist basis: `SK24-05` and `SK24-06`

Focus:

- expose declared skills, requirements, and install guidance from Component
  Repository pages;
- verify one real CAR from development source through Cozy packaging, CNCF
  Launcher development install, publication/local publication, and Textus
  Launcher install;
- prove that both launcher routes install equivalent skill content.

## Completion Criteria

Phase 24 closes when CNCF has a stable executable manifest contract, Cozy can
lint and package a component skill bundle deterministically, CNCF Launcher can
install and diagnose the development bundle, Textus Launcher can install the
same bundle from a resolved CAR, and both paths produce equivalent installed
content and safe provenance. Invalid, stale, colliding, incompatible, or
digest-mismatched input must fail before changing a Codex scope, and MCP
configuration must change only after an explicit conflict-checked request.

## References

- `docs/phase/phase-24-checklist.md`
- `docs/journal/2026/07/codex-skill-bundle-packaging-contract-2026-07-21.md`
- CNCF journal: `2026-07-21-codex-skill-bundle-contract.md`
- CNCF Phase 66: `docs/phase/phase-66.md` and
  `docs/phase/phase-66-checklist.md` in `cloud-native-component-framework`
- Textus Launcher: `docs/phase/phase-1.md`
- CNCF Launcher: `docs/phase/phase-1.md`
