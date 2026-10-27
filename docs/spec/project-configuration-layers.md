# Shared and local project configuration

Use the existing `conf/cozy/config.yaml` for shared operation defaults and
`.cozy/config.yaml` for local overrides. Either file may be absent; existing
local-only projects keep working. `project.yaml` remains artifact metadata.
Root `config.yaml` is not a new configuration location or project marker.
No consumer configuration is moved or untracked by this change.

## Precedence and merge

Cozy retains its existing order: user `~/.cozy/config.*`, shared
`conf/cozy/config.*`, then local `.cozy/config.*`. Existing formats and their
order remain supported. Project Context retains its existing nearest
`conf/cozy/config.yaml` marker and nested package overrides.

Maps retain unspecified members. Explicit values and arrays replace matching
lower-priority values and arrays. Both the structured JSON view and flattened
value/list views must describe the merged result, without stale array entries.
Provenance must not report fields removed by an override. Publication profiles
retain their existing complete-per-layer definition rules.

## sbt-cozy adapter

sbt-cozy reads `conf/cozy/config.yaml` then `.cozy/config.yaml`.
`cozySharedConfigFile` selects the shared file; the existing `cozyConfigFile`
continues to select the local file. `cozyConfigSources` lists existing sources
in loading order (`show cozyConfigSources`). The merged `cozyProjectConfig`
feeds the existing adapter settings. Missing files are optional.

The adapter retains its existing supported YAML subset and does not newly
import user-level or multi-format settings. Local lists replace shared lists,
including an explicit empty `[]` list. An authored empty identity overrides the
shared identity so existing validation can report it rather than silently
restoring the shared value.

## Acceptance

- Shared-only, local-only, both and neither work at the existing locations.
- Local scalars override shared scalars; unrelated shared fields survive.
- Local lists replace shared lists without leaving stale entries.
- Cozy's structured and flattened views agree after overlay.
- Existing Project Context discovery and source diagnostics remain intact.
- Real sbt settings load both files and preserve custom local-file selection.
- Root `config.yaml` is ignored; no sm-workflow migration occurs.
