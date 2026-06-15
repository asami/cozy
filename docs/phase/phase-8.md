# Phase 8: Video Knowledge Pipeline

Status: in-progress

Start date: 2026-06-15

## Goal

Make `cozy video` a first-class Cozy workflow for scripted video production and
video knowledge extraction.

Phase 8 connects video production to Cozy's engineering knowledge compiler and
publication toolchain. Cozy owns project/script parsing, orchestration,
manifest handling, dependency checks, RDF generation, and future BoK handoff
points. External media tools remain responsible for rendering, capture, speech
synthesis, and encoding.

The standard tool execution model is Docker-first for video production
dependencies. Remotion, Playwright/Chromium, ffmpeg/ffprobe, Node/npm
dependencies, and fonts should run from the configured Cozy toolchain Docker
image. VOICEVOX remains an external HTTP service and is not bundled into the
toolchain image.

## Scope

In scope:

- `cozy video inspect`
- `cozy video build`
- `cozy video synthesize`
- `cozy video render`
- `cozy video rdf`
- video project and script JSON parsing compatible with the current
  `videotools` project shape
- dry-run build planning without requiring media tools to be installed
- external tool checks with install/setup hints
- Docker toolchain execution for Remotion, Playwright, ffmpeg/ffprobe, Node,
  and related video rendering dependencies
- VOICEVOX HTTP endpoint configuration and connectivity checks
- VOICEVOX synthesis orchestration
- Remotion primary renderer invocation
- Java2D simple/fallback renderer
- ffmpeg/ffprobe process integration
- Turtle and JSON-LD RDF generation from video metadata and manifests
- runtime smoke coverage for dry-run, dependency reporting, and RDF generation

Out of scope:

- completed BoK registration operation
- Python/Pillow as a standard Cozy dependency
- vendoring ffmpeg, VOICEVOX, Node, Playwright, Remotion, or browser binaries
- bundling VOICEVOX Engine in the Cozy toolchain image
- production hosting, upload, CDN invalidation, or publication policy
- full visual-effect parity with every legacy Python renderer

## Phase Items

- [ ] VDO-01: Phase 8 documentation added
- [ ] VDO-02: `cozy video` command surface defined
- [ ] VDO-03: Video project model implemented
- [ ] VDO-04: Project/script JSON parsing implemented
- [ ] VDO-05: Dry-run and inspect implemented
- [ ] VDO-06: External tool checks implemented
- [ ] VDO-06B: Docker toolchain mode implemented
- [ ] VDO-07: VOICEVOX synthesis implemented
- [ ] VDO-08: Remotion renderer adapter implemented
- [ ] VDO-09: Java2D simple renderer implemented
- [ ] VDO-10: ffmpeg/ffprobe integration implemented
- [ ] VDO-11: RDF generation implemented
- [ ] VDO-12: BoK registration extension point decided
- [ ] VDO-13: Runtime smoke fixture added
- [ ] VDO-14: Existing workflows preserved

## Acceptance Criteria

- `cozy video inspect <project-json>` prints project, part, script, and artifact
  planning information.
- `cozy video inspect <project-json> --check-tools` reports external tool
  status with setup hints.
- `cozy video inspect <project-json> --check-tools` can report Docker
  toolchain availability separately from VOICEVOX HTTP connectivity.
- `cozy video build <project-json> --dry-run` prints planned synthesis,
  capture, render, and concat steps without requiring media tools.
- `cozy video synthesize <script-json> --save <audio-dir>` can generate
  VOICEVOX scene WAV files, a combined WAV, and `manifest.json`.
- `cozy video render ... --renderer remotion` uses Remotion as the standard
  rendering path.
- `cozy video render ... --renderer simple-java2d` creates a simple video
  without requiring Python/Pillow.
- `cozy video rdf <project-json> --save <dir>` writes Turtle and JSON-LD with
  scene, utterance, timing, artifact, and provenance metadata.
- Missing external tools fail with clear install/setup guidance.
- Existing `cozy bok`, publication, scaffold, and sbt-bridge behavior remains
  compatible.

## Decisions

- Remotion is the primary renderer.
- Java2D is a simple/fallback renderer only; pixel-perfect legacy compatibility
  is not required.
- Video rendering dependencies run through the configured Cozy toolchain Docker
  image by default.
- VOICEVOX is integrated only through HTTP, with the endpoint configured by
  project or local Cozy config.
- Python/Pillow is not part of the standard `cozy video` dependency path.
- BoK registration is deferred. Phase 8 records the RDF output handoff point for
  a later BoK/publish integration phase.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-8-checklist.md`
