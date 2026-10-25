# Shared and local configuration at the existing locations

## Decision

The user initially requested shared root `config.yaml` plus local
`.cozy/config.yaml`, then selected the already-supported `conf/cozy` location.
Use `conf/cozy/config.yaml` for shared defaults and `.cozy/config.yaml` for local
overrides. Do not introduce root YAML loading or a new project marker.

Cozy already loads user, conf and local defaults in that order. Its current
project discovery, supported formats and nested package behavior remain intact.
sbt-cozy is aligned with the shared conf/local YAML pair.

## Implementation

Cozy now merges parsed JSON objects before rebuilding flattened views. This
preserves unspecified shared structured fields and replaces arrays as a whole,
including empty arrays, instead of retaining stale indexed values. Project
Context filters provenance to fields retained in the effective configuration.

The associated specification is
[project configuration layers](../../../spec/project-configuration-layers.md).
Existing operation defaults and Project Context tests cover this behavior.

## Verification

The final focused command was:

```text
testOnly cozy.config.CozyProjectContextSpec cozy.CozyOperationConfigSpec -- -oD -l org.scalatest.tags.Slow
```

Compilation succeeded and all 20 tests across two suites passed. Both SBT and
the wrapper exited 0; process termination and native-locking completion were
verified against the typed result.

Retained final evidence (repository-relative):

- `.codex-workflow/disposable/a-529fee4d-fcb0-438f-b0c6-df0bca6db571/command-result.json`
- `.codex-workflow/disposable/a-866038b3-cd2f-4f30-8013-b23247daa814/56837-20261022T212456Z.summary.json`

The initial, superseded root-YAML candidate had 21 successes and one failure;
its result remains at
`.codex-workflow/disposable/a-4f9e60fa-5a49-4a88-ae8d-f5fda1a69156/command-result.json`.
It is not final acceptance evidence. A subsequent runner selected an unrelated
old intake by transcription error before execution. The original corrected
Cozy intake remained unexecuted and its permission unconsumed; the existing
same-child recovery procedure completed that same request. No old result or
execution history was replaced.

## Handoff

Keep shared project defaults under `conf/cozy/config.yaml` and machine-specific
overrides under `.cozy/config.yaml`. The adapter adds `cozySharedConfigFile` and
`cozyConfigSources`, retaining `cozyConfigFile` as the local selector.
No sm-workflow configuration move, Git untracking, runtime switch, artifact
publication or commit was performed. Existing work and evidence remain intact.
