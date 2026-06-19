# BoK Publish Operation

Status: current
Published at: 2026-06-19

## Overview

`cozy bok publish` is the production-facing BoK publication orchestration
command. It coordinates publication registry updates, SmartDox site build, and a
project-owned upload workflow.

Cozy does not implement a hosting provider in this phase. Upload remains an
external workflow command configured by the BoK project.

## Command

```console
cozy bok publish <project-dir> \
  [--publication <dir>] \
  [--warehouse <dir>] \
  [--version <version>] \
  [--strategy production] \
  [--force] \
  [--dry-run]
```

## Preflight

Before any publication, build, or upload side effects, Cozy validates:

- `bok.workflow.upload.command` is configured.
- BoK source directory exists.
- publication registry path is writable and outside the BoK source tree.
- warehouse path is writable and outside the BoK source tree.
- publication, warehouse, `website.d`, and `doxsite.d` do not overlap.
- `.video.d` directories are not used as source packages.

A preflight failure stops the command before later steps run.

## Dry-Run

`--dry-run` prints the planned operation and writes a manifest under
`target/cozy-bok/publish/latest/manifest.json`.

Dry-run does not:

- modify `src/main/publication`
- modify `warehouse`
- generate or replace `website.d`
- generate or replace `doxsite.d`
- execute upload workflow commands

## Manifest

The publish manifest records:

- project, source, publication, and warehouse paths
- strategy, `dryRun`, `force`, and video enablement
- discovered `.video/` packages
- planned publication artifact roots
- build commands
- upload workflow command
- per-step status

The manifest is intended for operator diagnostics and safe retry decisions.

## Retry Behavior

The operation order is:

1. `update-publication`
2. `build`
3. `upload`

If `build` fails, upload is not run. If upload fails, publication and build may
already have completed; the manifest shows the last successful step and failed
step.

Re-running is expected to be safe when the project's publication registry and
warehouse replacement policy is safe. Use `--force` when the underlying
publication artifacts need intentional replacement.
