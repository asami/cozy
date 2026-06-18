# Phase 8 Checklist

This checklist is the authoritative progress tracker for Video Knowledge
Pipeline work from 2026-06-15 onward.

## VDO-01: Phase 8 Documentation

Status: DONE

- [x] Add `docs/phase/phase-8.md`.
- [x] Add `docs/phase/phase-8-checklist.md`.
- [x] Add Phase 8 to `docs/strategy/cozy-development-strategy.md`.
- [x] Set `docs/phase/README.md` current phase to Phase 8.

## VDO-02: `cozy video` Command Surface

Status: OPEN

- [x] Add CLI delegation following the `cozy.bok.CozyBok` pattern for
      `video inspect`.
- [x] Add help text for `video inspect`.
- [x] Add help text for `video build --dry-run`.
- [ ] Add help text for `video synthesize`, `video render`, and `video rdf`.
- [ ] Add help text for `video transcribe`, `video demo-script`, and
      `video replay`.

## VDO-03: Video Project Model

Status: OPEN

- [x] Define minimal production project, part, renderer, script, and scene
      structures for inspect.
- [x] Define external-tool SPI structures for provider-backed checks.
- [ ] Define full character, artifact, timing, and manifest data structures.

## VDO-04: Project/Script Structured Document Parsing

Status: OPEN

- [x] Add shared structured document loading foundation through
      `org.goldenport.config.StructuredDocumentLoader`.
- [x] Accept JSON, YAML, HOCON, and XML as equivalent structured document
      formats for video project fixtures.
- [x] Accept JSON, YAML, HOCON, and XML as equivalent structured document
      formats for video script fixtures.
- [x] Keep XML singleton-object-to-array recovery scoped to XML typed decoding
      only.
- [x] Parse the current `videotools` `video_project` shape through the
      production `cozy video` model.
- [x] Parse dialogue/storyboard/web-demo script shapes through the production
      `cozy video` model.

## VDO-05: Dry-Run and Inspect

Status: DONE

- [x] Print video project and part/script plans.
- [x] Print complete artifact plans.
- [x] Print planned external commands without executing them.
- [x] Keep dry-run independent of installed media tools.

## VDO-06: External Tool Checks

Status: OPEN

- [x] Add external-tool SPI and injectable stub registry for deterministic
      checks.
- [x] Add non-executing default providers for Docker toolchain, VOICEVOX,
      ffmpeg/ffprobe, Remotion/Node, Playwright, and whisper.cpp.
- [ ] Check Docker availability when Docker toolchain mode is selected.
- [ ] Check Docker image availability or pull guidance for the configured Cozy
      toolchain image.
- [ ] Check VOICEVOX HTTP endpoint connectivity separately from Docker
      toolchain checks.
- [ ] In host mode only, check `ffmpeg`, `ffprobe`, `node`, `npm`, Remotion
      dependencies, Playwright Chromium, whisper.cpp, and transcription model
      data directly.
- [ ] Print installation/setup hints for missing dependencies.
- [x] Support an inspect-time tool check.

## VDO-06B: Docker Toolchain Mode

Status: OPEN

- [ ] Add a `video.tool-mode` setting with `docker` as the standard planned
      mode and `host` as an explicit fallback mode.
- [ ] Resolve Docker image precedence from CLI `--docker-image`,
      `video.docker-image`, `cozy.docker-image`, then the standard Cozy
      toolchain image.
- [ ] Run Remotion, Playwright/Chromium, ffmpeg/ffprobe, Node/npm-based video
      tooling, whisper.cpp, transcription model/data, and fonts from the
      configured Cozy toolchain Docker image.
- [ ] Keep VOICEVOX Engine outside the image and access it through
      `video.voicevox.url`.
- [ ] Provide setup hints for `docker pull simplemodeling/cozy-toolchain:latest`
      and for `host.docker.internal` or compose service URLs when VOICEVOX is
      unreachable from Docker.

## VDO-06C: Cozy Toolchain Docker Image Extension

Status: OPEN

- [ ] Identify or create the repository/release process that owns
      `simplemodeling/cozy-toolchain`.
- [ ] Decide whether the image definition lives in Cozy, SmartDox, or a shared
      toolchain repository, and document that ownership.
- [ ] Use the current SmartDox `smartdox-pdf` dependency image as the baseline
      for the unified toolchain unless a better existing owner is found.
- [ ] Include the BoK/PDF dependency set already needed by Cozy: SmartDox PDF
      rendering dependencies, Kroki command/server support, Japanese TeX
      tooling, CJK/emoji fonts, Node/npm, and Antora-capable tooling.
- [ ] Extend the unified image to include the video dependency set:
      ffmpeg/ffprobe, Remotion runtime dependencies, Playwright Chromium, and
      Japanese-capable fonts.
- [ ] Add whisper.cpp and the standard transcription model/data path to the
      unified image or documented toolchain-managed cache/volume.
- [ ] Add Playwright trace/replay tooling needed for recorded demo replay
      generation and dry-run validation.
- [ ] Keep Python available only as a tolerated toolchain utility if already
      present or useful, not as the standard `cozy video` renderer dependency.
- [ ] Do not include VOICEVOX Engine in the image.
- [ ] Keep `simplemodeling/smartdox-pdf:latest` compatibility or document a
      transition path to `simplemodeling/cozy-toolchain:latest`.
- [ ] Add image validation commands or documented checks that verify BoK,
      SmartDox PDF, and video dependency sets inside the image.
- [ ] Document the expected image tag used by Phase 8 development and smoke
      tests, with `simplemodeling/cozy-toolchain:latest` as the standard image.

## VDO-07: VOICEVOX Synthesis

Status: OPEN

- [ ] Read VOICEVOX endpoint from `video.voicevox.url`, falling back to a local
      development default.
- [ ] Resolve VOICEVOX speakers.
- [ ] Generate audio query and synthesis requests.
- [ ] Generate silent scenes.
- [ ] Concatenate WAV files.
- [ ] Write `manifest.json`.

## VDO-08: Remotion Renderer Adapter

Status: OPEN

- [ ] Invoke Remotion as the standard renderer path.
- [ ] Fail with actionable setup hints when Node/Remotion prerequisites are
      missing.

## VDO-09: Java2D Simple Renderer

Status: OPEN

- [ ] Render a simple static-frame video without requiring host Python/Pillow.
- [ ] Use ffmpeg for final MP4 encoding.
- [ ] Keep Python/Pillow available only as a future optional toolchain-contained
      extension path, not as the Phase 8 standard renderer.

## VDO-10: ffmpeg/ffprobe Integration

Status: OPEN

- [ ] Run concat/mux/probe commands through a common process runner.
- [ ] Report missing or failed media commands clearly.

## VDO-11: RDF Generation

Status: OPEN

- [ ] Generate Turtle output.
- [ ] Generate JSON-LD output.
- [ ] Include scene, timing, utterance, speaker, artifact, and provenance
      metadata.
- [ ] Include recorded demo transcript, caption, replay step, source video,
      input hash, tool version, model name/version, and timing provenance when
      available.

## VDO-12: BoK Registration Extension Point

Status: OPEN

- [ ] Document the RDF output handoff to future BoK/publish integration.
- [ ] Keep BoK registration out of Phase 8 completion.

## VDO-13: Runtime Smoke Fixture

Status: OPEN

- [ ] Add `src/sbt-test/cozy/video-runtime-smoke`.
- [ ] Validate inspect/dry-run behavior.
- [ ] Validate RDF generation.
- [ ] Validate dependency-check reporting without requiring every external tool.
- [ ] Validate recorded demo transcription dry-run behavior.
- [ ] Validate missing whisper.cpp or missing model setup hints.
- [ ] Validate Playwright trace-backed replay script generation.
- [ ] Validate video-only replay script generation produces a manual-review
      draft marker.

## VDO-14: Existing Workflow Preservation

Status: OPEN

- [ ] Existing `cozy bok` smoke remains compatible.
- [ ] Existing publication, scaffold, and sbt-bridge tests remain compatible.

## VDO-15: Recorded Demo Transcription

Status: OPEN

- [ ] Accept a recorded demo video input for transcription planning.
- [ ] Extract audio with ffmpeg.
- [ ] Run whisper.cpp with the configured transcription model/data.
- [ ] Write timestamped `transcript.json`.
- [ ] Write caption output such as `captions.srt`.
- [ ] Write a narration script artifact suitable for review or reuse.
- [ ] Record input video hash, whisper.cpp version, model name/version, and
      transcript timing provenance.
- [ ] Fail with setup hints when whisper.cpp or model/data is unavailable.

## VDO-16: Playwright Demo Replay Generation

Status: OPEN

- [ ] Generate `demo-script.json` from recorded demo metadata.
- [ ] Convert Playwright trace, HAR, selector event log, or equivalent capture
      metadata into replay steps when available.
- [ ] Include selectors, URL, viewport, timing hints, and captured input text
      when available.
- [ ] For video-only input, produce a draft script with a manual-review marker
      instead of claiming complete reconstruction.
- [ ] Dry-run generated replay scripts with Playwright without recording.
- [ ] Optionally record replay output when `--save <output-video>` is provided.

## VDO-17: Toolchain Includes whisper.cpp and Demo Replay Dependencies

Status: OPEN

- [ ] Include whisper.cpp binary or a documented build/install path in the
      Cozy toolchain image.
- [ ] Include standard whisper model/data in the image, or document the
      toolchain-managed cache/volume fallback if image size becomes too large.
- [ ] Include ffmpeg/ffprobe for audio extraction and probing.
- [ ] Include Playwright Chromium and runtime dependencies for replay.
- [ ] Include Node/npm and Japanese-capable fonts needed by replay/rendering.
- [ ] Add validation commands for whisper.cpp, model/data availability,
      ffmpeg/ffprobe, Playwright Chromium, and Node/npm.
