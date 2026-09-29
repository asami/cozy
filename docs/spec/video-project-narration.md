# Video Project Storyboard Narration Specification

Status: normative for the explicit `video synthesize --part` route

## Input selection

`video synthesize <script-file> --save <audio-dir>` remains the legacy source
route. It loads the supplied script and retains the existing script-tool and
Cozy runtime-default precedence.

`video synthesize <project-file> --part=<id> --save <declared-audio-dir>` is a
separate explicit project route. `id` is nonblank and must exactly match one
declared part. It selects only a native `storyboard` part: dialogue, legacy
script, and web-demo parts are rejected. The selected section is projected by
the existing Storyboard planner; no legacy script is loaded as a substitute.

The projection supplies the selected ordered scene ids and narration, the
project narration, voice, characters, pronunciation notes, and text
normalization. Its Storyboard source path is the synthesis provenance and
combined-WAV basename. This preserves source compatibility for existing
no-part callers; adding `part` to `SynthesizeConfig` is source compatible for
six-argument construction, not a case-class binary ABI promise.

## Provider and output boundary

Both routes use the same selected-provider pipeline. For the project route,
`VideoExecutionConfig` resolves CLI settings before project tools and runtime
defaults; this includes `voicevoxUrl`. Tool checks receive the real project
file, root, descriptor, execution settings, and selected provider.

Before a provider call or output-directory creation, project mode requires
`--save` to equal the selected audio directory exactly. The normalized
directory must be a strict descendant of the canonical project root, must not
traverse an existing symbolic link, and cannot be an existing non-directory.
Every potentially generated scene, lead, silence, combined-WAV, and manifest
target is checked before synthesis: an existing symbolic link or nonregular
target is rejected.

Synthesis is incremental once provider work begins. Cozy makes no provider
midrun rollback guarantee, but selection, source, output, and required tool
preflight failures leave provider and output mutation unstarted.

## Executable specification links

The executable contract is
`src/test/scala/cozy/video/CozyVideoStoryboardNarrationSpec.scala`. It extends
the native Storyboard semantics in `video-storyboard.md`, the Storyboard
executable specification, and the existing narration/currentness contracts.
It is focused provider evidence only and does not claim actual video generation
or Phase acceptance.
