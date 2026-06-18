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
Python/Pillow helper rendering, Node/npm dependencies, and fonts should run
from the configured Cozy toolchain Docker image. VOICEVOX remains an external
HTTP service and is not bundled into the toolchain image.

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
  Python/Pillow, whisper.cpp, and related video rendering/transcription
  dependencies
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
- host Python/Pillow as a required standard Cozy runtime dependency
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
- [x] VDO-04: Project/script structured document parsing implemented
- [x] VDO-05: Dry-run and inspect implemented
- [x] VDO-06: External tool checks implemented
- [x] VDO-06B: Docker toolchain mode implemented
- [x] VDO-06C: Unified Cozy toolchain Docker image developed
- [x] VDO-07: VOICEVOX synthesis implemented
- [x] VDO-08: Remotion renderer adapter implemented
- [x] VDO-09: Java2D simple renderer implemented
- [x] VDO-10: ffmpeg/ffprobe integration implemented
- [x] VDO-11: RDF generation implemented
- [ ] VDO-12: BoK registration extension point decided
- [ ] VDO-13: Runtime smoke fixture added
- [ ] VDO-14: Existing workflows preserved
- [ ] VDO-15: Recorded demo transcription implemented
- [ ] VDO-16: Playwright demo replay generation implemented
- [x] VDO-17: Toolchain includes whisper.cpp and demo replay dependencies

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
  dependencies, Playwright Chromium, Python/Pillow, whisper.cpp, transcription
  model/data, and Japanese-capable fonts.
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
- Remotion is the standard renderer direction; Python/Pillow remains available
  inside the toolchain for helper rendering and pragmatic adapters.
- Python/Pillow is an intentional Cozy toolchain utility for helper rendering
  paths, but Phase 8 must not make host Python/Pillow a required dependency.
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
- 2026-06-18: Completed VDO-06 external tool checks. `--check-tools` now uses
  probe-backed providers for Docker daemon/image availability, VOICEVOX HTTP,
  ffmpeg/ffprobe, Node/npm, Remotion dependency detection, Playwright Chromium,
  and whisper.cpp/model data while preserving stub-injectable deterministic
  tests and keeping normal inspect/dry-run independent of installed tools.
- 2026-06-18: Completed VDO-06B Docker toolchain mode planning/execution
  surface. `cozy video inspect/build --dry-run` now resolves `tool-mode` and
  Docker image precedence, defaults to Docker mode, wraps planned Remotion,
  Playwright, ffmpeg, and Python/Pillow helper steps in the configured Cozy
  toolchain image, and keeps VOICEVOX as an external HTTP service.
- 2026-06-18: Completed VDO-06C unified Cozy toolchain image definition.
  Cozy now owns `docker/cozy-toolchain`, using the SmartDox PDF image line as
  the baseline and extending it with Antora, ffmpeg/ffprobe, Remotion,
  Playwright Chromium, Python/Pillow, whisper.cpp, and the standard
  `ggml-base.bin` model. Docker-mode `--check-tools` validates the image
  contents with `cozy-toolchain check video`; VOICEVOX remains external.
- 2026-06-18: Completed VDO-07 VOICEVOX synthesis. `cozy video synthesize`
  now reads structured script files, resolves VOICEVOX speakers through the
  external HTTP service, generates scene WAV files, inserts lead/tail silence,
  concatenates a combined WAV with JDK WAV handling, and writes
  `manifest.json`. Video render and final mux execution remain deferred to
  VDO-08 and VDO-10.
- 2026-06-18: Completed VDO-08 Remotion renderer adapter. `cozy video render`
  now renders renderable parts through Cozy-generated Remotion workspaces under
  `target/cozy-video/remotion`, supports Docker-first and host execution through
  a runner SPI, validates VDO-07 audio manifests, and writes part manifests.
  Final project concat/mux remains deferred to VDO-10.
- 2026-06-18: Reconciled VDO-02 through VDO-04 status against the current
  implementation. Structured document parsing is complete for project and
  script inputs, while the command surface and model work remain open for the
  later RDF, transcription, and replay slices.
- 2026-06-18: Completed VDO-09 Java2D simple renderer. `cozy video render`
  now accepts `--renderer=simple-java2d`, generates a Python/Pillow static
  frame workspace, muxes the frame with VDO-07 combined audio through ffmpeg,
  and writes renderer-specific part manifests. Final project concat/mux
  remains deferred to VDO-10.
- 2026-06-18: Completed VDO-10 ffmpeg/ffprobe final assembly.
  `cozy video build` without `--dry-run` now assembles already-rendered part
  MP4 files into the project final output, validates the output with ffprobe,
  and writes a deterministic project manifest. RDF, transcription, and replay
  remained open for later VDO slices at this point.
- 2026-06-18: Completed VDO-11 SmartDox semanticweb based Video RDF
  generation. `cozy video rdf` now projects video project, script, audio,
  render, and build manifests into Turtle and JSON-LD through SmartDox
  `Rdf.Graph` / `RdfRenderer`, while recorded demo transcript/replay
  provenance remains open for VDO-15/VDO-16 manifest inputs.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-8-checklist.md`
