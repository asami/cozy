# Phase 8 Checklist

This checklist is the authoritative progress tracker for Video Knowledge
Pipeline work from 2026-06-15 onward.

## VDO-01: Phase 8 Documentation

Status: OPEN

- [ ] Add `docs/phase/phase-8.md`.
- [ ] Add `docs/phase/phase-8-checklist.md`.
- [ ] Add Phase 8 to `docs/strategy/cozy-development-strategy.md`.

## VDO-02: `cozy video` Command Surface

Status: OPEN

- [ ] Add CLI delegation following the `cozy.bok.CozyBok` pattern.
- [ ] Add help text for `video inspect`, `video build`, `video synthesize`,
      `video render`, and `video rdf`.

## VDO-03: Video Project Model

Status: OPEN

- [ ] Define project, part, script, scene, character, artifact, timing, and
      manifest data structures.

## VDO-04: Project/Script JSON Parsing

Status: OPEN

- [ ] Parse the current `videotools` `video_project.json` shape.
- [ ] Parse dialogue/storyboard/web-demo script JSON shapes used by current
      video projects.

## VDO-05: Dry-Run and Inspect

Status: OPEN

- [ ] Print video project and artifact plans.
- [ ] Print planned external commands without executing them.
- [ ] Keep dry-run independent of installed media tools.

## VDO-06: External Tool Checks

Status: OPEN

- [ ] Check Docker availability when Docker toolchain mode is selected.
- [ ] Check Docker image availability or pull guidance for the configured Cozy
      toolchain image.
- [ ] Check VOICEVOX HTTP endpoint connectivity separately from Docker
      toolchain checks.
- [ ] In host mode only, check `ffmpeg`, `ffprobe`, `node`, `npm`, Remotion
      dependencies, and Playwright Chromium directly.
- [ ] Print installation/setup hints for missing dependencies.
- [ ] Support an inspect-time tool check.

## VDO-06B: Docker Toolchain Mode

Status: OPEN

- [ ] Add a `video.tool-mode` setting with `docker` as the standard planned
      mode and `host` as an explicit fallback mode.
- [ ] Resolve Docker image precedence from CLI `--docker-image`,
      `video.docker-image`, `cozy.docker-image`, then the standard Cozy
      toolchain image.
- [ ] Run Remotion, Playwright/Chromium, ffmpeg/ffprobe, Node/npm-based video
      tooling, and fonts from the configured Cozy toolchain Docker image.
- [ ] Keep VOICEVOX Engine outside the image and access it through
      `video.voicevox.url`.
- [ ] Provide setup hints for `docker pull simplemodeling/cozy-toolchain:latest`
      and for `host.docker.internal` or compose service URLs when VOICEVOX is
      unreachable from Docker.

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

- [ ] Render a simple static-frame video without Python/Pillow.
- [ ] Use ffmpeg for final MP4 encoding.

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

## VDO-14: Existing Workflow Preservation

Status: OPEN

- [ ] Existing `cozy bok` smoke remains compatible.
- [ ] Existing publication, scaffold, and sbt-bridge tests remain compatible.
