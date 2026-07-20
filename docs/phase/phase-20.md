# Phase 20: Multi-Provider Narration and Cozy-Complete Video Build

Status: in progress

Start date: 2026-07-20

## Goal

Make narration synthesis provider-neutral and make the Japanese and English
SimpleModeling.org Overview videos reproducible through Cozy commands without
project-local rendering helpers or manually authored ffmpeg pipelines.

The portable Docker route does not attempt to package macOS system voices.
Cozy selects and validates the narration provider, while the Textus toolchain
image supplies a pinned Piper runtime and license-audited voice models.

## Scope

In scope:

- a first-class narration-provider contract in Cozy Video;
- canonical `narration.provider` authoring and explicit provider diagnostics;
- compatibility input for existing `voice.engine` declarations, with an
  explicit deprecation diagnostic and new scaffold/documentation output using
  `narration.provider`; this compatibility read does not produce legacy output
  and may be removed in a later phase after source migration;
- VOICEVOX as an external HTTP provider;
- macOS `say` as a host-only provider;
- Piper as the portable English TTS provider in the Textus toolchain Docker
  image;
- common pronunciation conversion, scene timing, canonical WAV output,
  combined audio, manifest generation, and provider provenance;
- `video inspect --check-tools` validation for the selected provider;
- provider and execution-mode propagation through synthesis, RDF, and video
  publication metadata;
- migration of both SimpleModeling.org Overview video packages to canonical
  Cozy Video descriptors, built-in Remotion effect profiles, and Cozy-owned
  final assembly;
- real host and Docker synthesis/render/build verification.

Out of scope:

- packaging `say`, Samantha, Karen, or other macOS system voices in a Linux
  Docker image;
- downloading TTS runtimes or voice models during video synthesis;
- automatic model-license inference;
- YouTube upload or channel operation;
- resolving or redistributing the third-party character assets currently used
  by the local Overview renders;
- preserving byte-identical output when the English provider or voice model is
  intentionally changed.

## Stage 20.1: Narration Provider Contract

Stage Status:

- Current status: IN PROGRESS
- Owner: cozy-video
- Checklist basis: `VID20-01` and `VID20-02`

Focus:

- separate provider-specific synthesis from Cozy-owned scene and WAV handling;
- define provider selection, configuration precedence, diagnostics, and
  provenance;
- preserve the existing VOICEVOX authoring path while making new authoring
  provider-explicit.

Verified implementation status:

- The provider-boundary portion of `VID20-01` is implemented. Cozy resolves
  canonical `narration.provider` before creating output, defaults existing
  scripts to `voicevox`, and accepts `voice.engine` only with an explicit
  deprecation diagnostic.
- New scaffold scripts use:

  ```yaml
  narration:
    provider: voicevox
  ```

- The shared synthesis pipeline records provider, execution mode, voice
  identity, and model identity in each audio manifest entry.
- `VID20-01` remains open until provider WAV output is normalized through one
  canonical sample-rate/channel contract in shared Cozy code.
- `VID20-02` remains open for CLI execution settings, provider-aware tool
  checks, RDF/publication propagation, and user-facing help.

## Stage 20.2: Host and Docker Providers

Stage Status:

- Current status: NOT STARTED
- Owner: cozy-video / textus-toolchain-runner
- Checklist basis: `VID20-03` and `VID20-04`

Focus:

- run macOS `say` only through a validated host provider;
- add a pinned Piper runtime and two reviewed English voice models to the Textus
  toolchain image;
- record model source, version, license, and SHA-256 in a machine-readable
  manifest;
- normalize every provider result to the same Cozy audio contract.

## Stage 20.3: Cozy-Complete Overview Migration

Stage Status:

- Current status: NOT STARTED
- Owner: cozy-video / SimpleModeling.org
- Checklist basis: `VID20-05` and `VID20-06`

Focus:

- remove absolute project-local effect-tool references and manual ffmpeg
  assembly from both Overview packages;
- represent opening, section-start, summary, and final-page behavior through
  Cozy-owned composition/effect contracts;
- verify the Japanese VOICEVOX route, English macOS route, and English portable
  Docker route from source to final MP4.

## Completion Criteria

Phase 20 closes when Cozy can select and validate all three narration providers,
all providers produce the canonical audio and provenance contract, the Textus
toolchain snapshot image passes a real portable English synthesis smoke test,
and both Overview videos can be inspected, synthesized, rendered, assembled,
and verified using Cozy commands without project-local helper executables or
manual ffmpeg commands. Focused and full tests, real integration evidence, and
`git diff --check` must be recorded before closure.
