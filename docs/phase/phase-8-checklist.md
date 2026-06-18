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
- [x] Add help text for `video synthesize`.
- [x] Add help text for `video render`.
- [x] Add help text for `video rdf`.
- [ ] Add help text for `video transcribe`.
- [ ] Add help text for `video demo-script`.
- [ ] Add help text for `video replay`.

## VDO-03: Video Project Model

Status: OPEN

- [x] Define minimal production project, part, renderer, script, and scene
      structures for inspect.
- [x] Define external-tool SPI structures for provider-backed checks.
- [x] Define character voice, pronunciation, and text normalization structures.
- [x] Define audio synthesis manifest and render part manifest structures.
- [x] Define RDF artifact and provenance manifest structures.
- [ ] Define transcription/replay artifact and provenance manifest structures.

## VDO-04: Project/Script Structured Document Parsing

Status: DONE

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

Status: DONE

- [x] Add external-tool SPI and injectable stub registry for deterministic
      checks.
- [x] Add non-executing default providers for Docker toolchain, VOICEVOX,
      ffmpeg/ffprobe, Remotion/Node, Playwright, and whisper.cpp.
- [x] Check Docker availability when Docker toolchain mode is selected.
- [x] Check Docker image availability or pull guidance for the configured Cozy
      toolchain image.
- [x] Check VOICEVOX HTTP endpoint connectivity separately from Docker
      toolchain checks.
- [x] In host mode only, check `ffmpeg`, `ffprobe`, `node`, `npm`, Remotion
      dependencies, Playwright Chromium, whisper.cpp, and transcription model
      data directly.
- [x] Print installation/setup hints for missing dependencies.
- [x] Support an inspect-time tool check.

## VDO-06B: Docker Toolchain Mode

Status: DONE

- [x] Add a `video.tool-mode` setting with `docker` as the standard planned
      mode and `host` as an explicit fallback mode.
- [x] Resolve Docker image precedence from CLI `--docker-image`, video project
      `tools.dockerImage`, `video.docker-image`, `cozy.docker-image`, then the
      standard Cozy toolchain image.
- [x] Plan Docker-wrapped command previews for Remotion, Playwright/Chromium,
      ffmpeg/ffprobe, Node/npm-based video tooling, whisper.cpp, transcription
      model/data, and fonts from the configured Cozy toolchain Docker image.
- [x] Keep VOICEVOX Engine outside the image and access it through
      `video.voicevox.url`.
- [x] Provide setup hints for `docker pull simplemodeling/cozy-toolchain:latest`
      and for `host.docker.internal` or compose service URLs when VOICEVOX is
      unreachable from Docker.

## VDO-06C: Cozy Toolchain Docker Image Extension

Status: DONE

- [x] Identify or create the repository/release process that owns
      `simplemodeling/cozy-toolchain`.
- [x] Decide whether the image definition lives in Cozy, SmartDox, or a shared
      toolchain repository, and document that ownership.
- [x] Use the current SmartDox `smartdox-pdf` dependency image as the baseline
      for the unified toolchain unless a better existing owner is found.
- [x] Include the BoK/PDF dependency set already needed by Cozy: SmartDox PDF
      rendering dependencies, Kroki command/server support, Japanese TeX
      tooling, CJK/emoji fonts, Python/Pillow, Node/npm, and Antora-capable
      tooling.
- [x] Extend the unified image to include the video dependency set:
      ffmpeg/ffprobe, Remotion runtime dependencies, Playwright Chromium, and
      Japanese-capable fonts, plus Python/Pillow helper rendering support.
- [x] Add whisper.cpp and the standard transcription model/data path to the
      unified image.
- [x] Add Playwright trace/replay tooling needed for recorded demo replay
      generation and dry-run validation.
- [x] Include Python/Pillow as an intentional toolchain utility for helper
      rendering paths while keeping host Python/Pillow optional.
- [x] Do not include VOICEVOX Engine in the image.
- [x] Keep `simplemodeling/smartdox-pdf:latest` compatibility or document a
      transition path to `simplemodeling/cozy-toolchain:latest`.
- [x] Add image validation commands or documented checks that verify BoK,
      SmartDox PDF, and video dependency sets inside the image.
- [x] Document the expected image tag used by Phase 8 development and smoke
      tests, with `simplemodeling/cozy-toolchain:latest` as the standard image.

## VDO-07: VOICEVOX Synthesis

Status: DONE

- [x] Read VOICEVOX endpoint from `video.voicevox.url`, falling back to a local
      development default.
- [x] Resolve VOICEVOX speakers.
- [x] Generate audio query and synthesis requests.
- [x] Generate silent scenes.
- [x] Concatenate WAV files.
- [x] Write `manifest.json`.

## VDO-08: Remotion Renderer Adapter

Status: DONE

- [x] Invoke Remotion as the standard renderer path.
- [x] Fail with actionable setup hints when Node/Remotion prerequisites are
      missing.

## VDO-09: Java2D Simple Renderer

Status: DONE

- [x] Render a simple static-frame video without requiring host Python/Pillow.
- [x] Use ffmpeg for final MP4 encoding.
- [x] Use Python/Pillow as a toolchain-contained helper rendering path without
      requiring host Python/Pillow.

## VDO-10: ffmpeg/ffprobe Integration

Status: DONE

- [x] Run concat/mux/probe commands through a common process runner.
- [x] Report missing or failed media commands clearly.

## VDO-11: RDF Generation

Status: DONE

- [x] Generate Turtle output.
- [x] Generate JSON-LD output.
- [x] Include scene, timing, utterance, speaker, artifact, and provenance
      metadata.
- [x] Include recorded demo transcript, caption, and replay artifacts when
      available as optional RDF artifact references.
- [x] Defer input hash, tool version, model name/version, and timing
      provenance until VDO-15/VDO-16 provide recorded demo manifests.

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

Status: DONE

- [x] Include whisper.cpp binary or a documented build/install path in the
      Cozy toolchain image.
- [x] Include standard whisper model/data in the image.
- [x] Include ffmpeg/ffprobe for audio extraction and probing.
- [x] Include Playwright Chromium and runtime dependencies for replay.
- [x] Include Node/npm and Japanese-capable fonts needed by replay/rendering.
- [x] Add validation commands for whisper.cpp, model/data availability,
      ffmpeg/ffprobe, Playwright Chromium, and Node/npm.
