# Codex Skill Bundle Packaging Direction — 2026-07-21

## Context

CARs need a generic way to distribute Codex skills. Textus Launcher will
install a bundle from released or locally published CARs, while CNCF Launcher
will use the matching declaration from a component development tree.

## Direction

Cozy will be the compiler/packager for a CAR's declared Codex skill bundle. It
will validate the CNCF `SkillBundleManifest`, collect only declared skill files,
calculate deterministic digests, and project the same content into the CAR's
canonical bundle location. It will also make the corresponding source/output
location discoverable for CNCF Launcher development installation.

The intended source convention is a component-local Codex skill workspace,
with the exact source path and archive location to be fixed by the CNCF
normative contract. Cozy must not define its own competing manifest schema.

## Boundaries

- Cozy validates and packages; it does not write to `~/.codex`, merge Codex
  configuration, or install skills.
- Cozy must reject path traversal, undeclared files, duplicate identities,
  invalid names, digest mismatch, and unsupported manifest versions before
  packaging.
- Packaging must not execute skill scripts or infer permissions from their
  contents.
- Existing CAR/CML/runtime packaging behavior remains unchanged until the
  optional skill bundle is declared.

## Follow-up

After CNCF promotes the manifest contract, add Cozy source-model validation,
deterministic CAR projection, and source/archive equivalence specifications.
Then CBD Support can publish its catalog, review, and development skill set as
the first real consumer without a CBD-specific packaging path.
