# Phase 8: Video Knowledge Pipeline

Status: in-progress

Start date: 2026-06-15

## Goal

Make `cozy video` a first-class Cozy workflow for scripted video production and
video knowledge extraction, and formalize the unified Cozy toolchain Docker
image used by BoK, PDF, and video workflows.

Phase 8 connects video production and recorded demo knowledge extraction to
Cozy's engineering knowledge compiler and publication toolchain. Cozy owns
project/script parsing, transcription orchestration, replay script generation,
manifest handling, dependency checks, RDF generation, and future BoK handoff
points. External media tools remain responsible for rendering, capture, speech
synthesis, transcription, and encoding.

The standard tool execution model is Docker-first for heavy publication and
media dependencies. Phase 8 turns the existing BoK/PDF dependency-image line
into a documented `simplemodeling/cozy-toolchain` image that covers BoK HTML
generation, SmartDox PDF generation, and video rendering. Remotion,
Playwright/Chromium, ffmpeg/ffprobe, whisper.cpp, transcription model/data,
Node/npm dependencies, and fonts should run from the configured Cozy toolchain
Docker image. VOICEVOX remains an external HTTP service and is not bundled into
the toolchain image.

## Scope

In scope:

- `cozy video inspect`
- `cozy video build`
- `cozy video synthesize`
- `cozy video render`
- `cozy video rdf`
- `cozy video transcribe`
- `cozy video demo-script`
- `cozy video replay`
- video project and script structured document parsing compatible with the
  current `videotools` project shape
- recorded demo ingestion from captured video files
- transcript, caption, and narration script generation from recorded demo
  audio
- Playwright replay script generation from recorded demo metadata
- dry-run build planning without requiring media tools to be installed
- external tool checks with install/setup hints
- Docker toolchain execution for Remotion, Playwright, ffmpeg/ffprobe, Node,
  whisper.cpp, and related video rendering/transcription dependencies
- Cozy toolchain Docker image development, documentation, and validation for
  BoK, SmartDox PDF, and video production dependencies
- migration path from the current SmartDox PDF dependency image to the unified
  Cozy toolchain image
- VOICEVOX HTTP endpoint configuration and connectivity checks
- VOICEVOX synthesis orchestration
- Remotion primary renderer invocation
- Playwright replay/dry-run invocation for generated demo scripts
- Java2D simple/fallback renderer
- ffmpeg/ffprobe process integration
- Turtle and JSON-LD RDF generation from video metadata and manifests
- runtime smoke coverage for dry-run, dependency reporting, transcription
  planning, replay script generation, and RDF generation

Out of scope:

- completed BoK registration operation
- Python/Pillow as a required standard Cozy runtime dependency
- vendoring ffmpeg, VOICEVOX, Node, Playwright, Remotion, or browser binaries
- bundling VOICEVOX Engine in the Cozy toolchain image
- removing SmartDox PDF image compatibility before a transition path exists
- production hosting, upload, CDN invalidation, or publication policy
- full visual-effect parity with every legacy Python renderer
- guaranteed fully automatic operation reconstruction from a recorded video
  when no Playwright trace, HAR, selector event log, or equivalent capture
  metadata is available

## Phase Items

- [x] VDO-01: Phase 8 documentation added
- [ ] VDO-02: `cozy video` command surface defined
- [ ] VDO-03: Video project model implemented
- [ ] VDO-04: Project/script structured document parsing implemented
- [x] VDO-05: Dry-run and inspect implemented
- [ ] VDO-06: External tool checks implemented
- [ ] VDO-06B: Docker toolchain mode implemented
- [ ] VDO-06C: Unified Cozy toolchain Docker image developed
- [ ] VDO-07: VOICEVOX synthesis implemented
- [ ] VDO-08: Remotion renderer adapter implemented
- [ ] VDO-09: Java2D simple renderer implemented
- [ ] VDO-10: ffmpeg/ffprobe integration implemented
- [ ] VDO-11: RDF generation implemented
- [ ] VDO-12: BoK registration extension point decided
- [ ] VDO-13: Runtime smoke fixture added
- [ ] VDO-14: Existing workflows preserved
- [ ] VDO-15: Recorded demo transcription implemented
- [ ] VDO-16: Playwright demo replay generation implemented
- [ ] VDO-17: Toolchain includes whisper.cpp and demo replay dependencies

## Acceptance Criteria

- `cozy video inspect <project-file>` prints project, part, script, and initial
  planning information.
- `cozy video inspect <project-file> --check-tools` reports external tool
  status with setup hints.
- `cozy video inspect <project-file> --check-tools` can report Docker
  toolchain availability separately from VOICEVOX HTTP connectivity.
- The configured Cozy toolchain Docker image has a documented build path and
  includes the BoK/PDF/video dependency set required by Cozy: SmartDox PDF
  dependencies, Antora-capable Node tooling, ffmpeg/ffprobe, Remotion runtime
  dependencies, Playwright Chromium, whisper.cpp, transcription model/data, and
  Japanese-capable fonts.
- VOICEVOX Engine remains outside the image and is checked only as an HTTP
  endpoint.
- `cozy video build <project-file> --dry-run` prints planned synthesis,
  capture, render, and concat steps without requiring media tools.
- `cozy video synthesize <script-file> --save <audio-dir>` can generate
  VOICEVOX scene WAV files, a combined WAV, and `manifest.json`.
- `cozy video render ... --renderer remotion` uses Remotion as the standard
  rendering path.
- `cozy video render ... --renderer simple-java2d` creates a simple video
  without requiring Python/Pillow on the host runtime.
- `cozy video transcribe <input-video> --save <dir>` extracts audio, runs
  whisper.cpp, and writes timestamped transcript, caption, and narration
  artifacts.
- `cozy video demo-script <input-video> --save <script-file> [--trace
  <trace.zip>] [--har <file>]` writes a Playwright replay script draft. Trace,
  HAR, or selector event metadata provides the high-precision path; video-only
  input produces a manual-review draft.
- `cozy video replay <script-file> [--save <output-video>] [--dry-run]` can
  dry-run or execute the generated Playwright replay plan.
- `cozy video rdf <project-file> --save <dir>` writes Turtle and JSON-LD with
  scene, utterance, timing, artifact, and provenance metadata.
- RDF output includes transcript, caption, replay step, source video, input
  hash, tool version, model name/version, and timing provenance when recorded
  demo inputs are present.
- Missing external tools fail with clear install/setup guidance.
- Existing `cozy bok`, publication, scaffold, and sbt-bridge behavior remains
  compatible.
- `cozy bok build` can continue to use the configured Cozy toolchain image, and
  SmartDox PDF image compatibility remains available during transition.

## Decisions

- Remotion is the primary renderer.
- Java2D is a simple/fallback renderer only; pixel-perfect legacy compatibility
  is not required.
- Video rendering dependencies run through the configured Cozy toolchain Docker
  image by default.
- Phase 8 includes the unified Cozy toolchain image definition, build
  documentation, and validation checks for BoK, SmartDox PDF, and video
  workflows. If the image definition lives outside the Cozy repository, Phase 8
  must still record the owning repository and release path.
- A single operational image is preferred over separate BoK/PDF/video images.
  Compatibility tags or configuration aliases may remain during migration.
- VOICEVOX is integrated only through HTTP, with the endpoint configured by
  project or local Cozy config.
- whisper.cpp is the standard transcription engine for recorded demo audio.
- The standard transcription model/data is part of the Cozy toolchain plan. If
  image size becomes impractical, the model/data may move to a toolchain-managed
  cache or Docker volume with the same validation and setup behavior.
- Recorded video plus Playwright trace, HAR, selector event log, or equivalent
  capture metadata is the high-precision path for replay generation.
- Recorded video alone produces a draft replay script requiring manual review;
  OCR and visual operation inference remain future extensions.
- Remotion replaces the legacy Python/Pillow renderer as the standard rendering
  direction.
- Python/Pillow may be used later for optional toolchain-contained adapters or
  specialized extensions when it is the pragmatic choice, but Phase 8 must not
  make host Python/Pillow a required dependency.
- BoK registration is deferred. Phase 8 records the RDF output handoff point for
  a later BoK/publish integration phase.

## Progress Notes

- 2026-06-18: Implemented the initial `cozy video inspect` slice. It supports
  JSON, YAML, HOCON, and XML project/script loading, mixed dialogue/storyboard
  / web-demo part summaries, unsupported part reporting, deterministic
  subscene IDs, and an injectable non-executing external-tool SPI for stubbed
  tests.
- 2026-06-18: Completed VDO-05 dry-run and artifact planning. `inspect` now
  renders a reusable artifact plan, and `video build <project-file> --dry-run`
  prints non-executing command previews for supported dialogue, storyboard, and
  web-demo parts.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-8-checklist.md`
