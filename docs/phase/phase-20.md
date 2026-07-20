# Phase 20: Multi-Provider Narration and Cozy-Complete Video Build

Status: in progress

Start date: 2026-07-20

## Goal

Make narration synthesis provider-neutral and make the Japanese and English
SimpleModeling.org Overview videos reproducible through Cozy commands without
project-local rendering helpers or manually authored ffmpeg pipelines. Resolve
declared character-material and voice credits from actual build usage, then
generate both an in-video credit page and publication-ready credit text.

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
- a generic structured credit catalog, profile, selector, and resolver
  contract, independent of a specific user or character set;
- user- and project-level default credit profiles through the existing Cozy
  configuration layers;
- credit resolution from script characters, semantic asset metadata, and the
  authoritative audio manifest;
- localized in-video credit pages and publication-ready Markdown generated
  from one effective credit set;
- automatic handling of the Reimu/Marisa, Zundamon, and Japanese/English
  variations defined by the Phase 20 credit handoff;
- migration of both SimpleModeling.org Overview video packages to canonical
  Cozy Video descriptors, built-in Remotion effect profiles, and Cozy-owned
  final assembly;
- real host and Docker synthesis/render/build verification.

Out of scope:

- packaging `say`, Samantha, Karen, or other macOS system voices in a Linux
  Docker image;
- downloading TTS runtimes or voice models during video synthesis;
- automatic model-license inference;
- automatic interpretation of rights-holder terms or a decision about whether
  a particular publication is commercial;
- YouTube upload or channel operation;
- downloading, embedding, or redistributing third-party character assets;
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

- `VID20-01` is complete. Cozy resolves canonical `narration.provider` before
  creating output, defaults existing scripts to `voicevox`, and accepts
  `voice.engine` only with an explicit deprecation diagnostic.
- New scaffold scripts use:

  ```yaml
  narration:
    provider: voicevox
  ```

- The shared synthesis pipeline records provider, execution mode, voice
  identity, and model identity in each audio manifest entry.
- Provider PCM WAV output is decoded and normalized to 24 kHz, mono, 16-bit PCM
  in shared Cozy code before scene and combined audio are written. Unsupported
  encoding and bit-depth contracts fail explicitly.
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

## Stage 20.4: Credit Profiles and Publication Projection

Stage Status:

- Current status: NOT STARTED
- Owner: cozy-video / SimpleModeling.org
- Checklist basis: `VID20-07`

Focus:

- keep the Cozy credit model generic and load user or organization profiles
  through the existing Cozy configuration layers;
- allow Asami's personal environment to select one default profile under
  `~/.cozy`, without repeating credit text in each video project;
- derive character-material credits from actual character and asset usage;
- derive voice credits from actual audio-manifest provider and voice identity,
  rather than assuming a provider from the publication language;
- cover the eight Reimu/Marisa, Zundamon, and Japanese/English baseline
  combinations additively;
- generate structured credits, YouTube-ready Markdown, and a renderer-neutral
  credit page from one resolved set;
- insert a non-empty credit page before the final URL page and carry credit
  provenance into build, verification, and RDF metadata.

Primary handoff:

- `docs/journal/2026/07/video-credit-profile-handoff-2026-07-20.md`

## Completion Criteria

Phase 20 closes when Cozy can select and validate all three narration providers,
all providers produce the canonical audio and provenance contract, the Textus
toolchain snapshot image passes a real portable English synthesis smoke test,
and both Overview videos can be inspected, synthesized, rendered, assembled,
credited, and verified using Cozy commands without project-local helper
executables or manual ffmpeg commands. The same effective credit set must
produce the in-video page and publication Markdown, including all eight
baseline variations. Focused and full tests, real integration evidence, and
`git diff --check` must be recorded before closure.
