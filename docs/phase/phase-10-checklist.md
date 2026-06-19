# Phase 10 Checklist

This checklist is the authoritative progress tracker for one-stop BoK build and
operation integration work from 2026-06-19 onward.

## BK10-01: Phase 10 Documentation

Status: DONE

- [x] Add `docs/phase/phase-10.md`.
- [x] Add `docs/phase/phase-10-checklist.md`.
- [x] Add Phase 10 to `docs/strategy/cozy-development-strategy.md`.
- [x] Set `docs/phase/README.md` current phase to Phase 10.
- [x] Record Phase 10 as "One-Stop BoK Build and Operation Integration".

## BK10-02: BoK Publication Config Model

Status: DONE

- [x] Add BoK publication settings under `bok.*` in `conf/cozy/config.*` and `.cozy/config.*`.
- [x] Support `bok.warehouse`, `bok.publication`, `bok.source`, `bok.strategy`.
- [x] Support `bok.rdf.merge-publication-artifacts`.
- [x] Support `bok.rdf.missing-artifact-policy`.
- [x] Support `bok.video.enabled` and `bok.video.force`.
- [x] Keep CLI options higher priority than config.
- [x] Default missing RDF artifact policy to `fail` for `production`.
- [x] Default missing RDF artifact policy to `warn` for `wip`, `draft`, and `preview`.

## BK10-03: BoK Video Publication Commands

Status: DONE

- [x] Add `cozy bok publish-video <project-dir> [--warehouse <dir>] [--version <version>] [--force]`.
- [x] Add `cozy bok update-publication <project-dir> [--warehouse <dir>] [--version <version>] [--force]`.
- [x] Add `cozy bok publish <project-dir> [--warehouse <dir>] [--version <version>] [--strategy production] [--force]`.
- [x] Preserve existing `cozy bok build`, `update`, `preview`, `commit`, and `upload` behavior.
- [x] Update `cozy help` with the new BoK command surface.
- [x] Preserve existing top-level `cozy publish-video` behavior.

## BK10-04: `.video/` Package Discovery

Status: DONE

- [x] Detect `src/main/doxsite/**/*.video/` packages.
- [x] Require `video.yaml`, `video.yml`, or `video.json` inside each `.video/` package.
- [x] Exclude or reject `.video.d/` as generated/work directory naming.
- [x] Call existing `CozyVideoPublisher.publish` for each discovered package.
- [x] Write BoK-facing metadata to `src/main/publication`.
- [x] Write generated video artifacts to `warehouse/repository/video`.
- [x] Ensure generated MP4/RDF/caption/transcript artifacts are never written under `.video/` source packages.

## BK10-05: One-Stop BoK Publish Flow

Status: DONE

- [x] Implement `bok publish` as a deterministic sequence: `update-publication` -> `build --strategy production` -> configured upload workflow.
- [x] Ensure `bok publish` does not run unconfigured upload commands.
- [x] Ensure `bok publish` reports each step and failed step clearly.
- [x] Keep heavy video generation explicit through `bok publish-video` / `update-publication`.
- [x] Do not make ordinary `bok build` generate or transcode videos.

## BK10-06: SmartDox RDF Artifact Merge

Status: DONE

- [x] Extend SmartDox publication metadata consumption to resolve registered video RDF artifacts.
- [x] Resolve RDF artifacts from publication metadata plus configured repository/warehouse root.
- [x] Parse and merge Turtle RDF into `site.ttl` / `site.jsonld`.
- [x] Keep JSON-LD body merge deferred unless the existing SmartDox RDF stack already supports it safely.
- [x] Preserve current video/RDF/caption/transcript reference triples.
- [x] Do not scan `target/`, `build/`, arbitrary RDF output directories, or warehouse directories.
- [x] For preview/draft, missing RDF artifacts keep reference triples and do not fail the build. Explicit diagnostic surfacing remains a SmartDox warning-sink follow-up.
- [x] For production, missing RDF artifacts fail the build.

## BK10-07: SmartDox Video Article Enhancement

Status: DONE

- [x] Keep `.video/index.dox` rendered as the public introduction article.
- [x] Embed video player from publication metadata.
- [x] Embed caption track when registered.
- [x] Add transcript/RDF/provenance links where publication metadata provides them.
- [x] Render article even when publication metadata is missing.
- [x] Emit diagnostic when publication metadata is missing.
- [x] Ensure SmartDox does not execute video generation.

## BK10-08: BoK Build Integration

Status: DONE

- [x] Ensure `bok build` passes `src/main/publication` to SmartDox site generation.
- [x] Ensure `bok build` passes repository/warehouse root or equivalent RDF resolution context to SmartDox.
- [x] Ensure generated `website.d` includes video articles and player embeds.
- [x] Ensure generated `doxsite.d/site.ttl` contains merged video RDF when available.
- [x] Ensure generated dashboard RDF counts reflect merged RDF.
- [x] Preserve existing dashboard behavior when no publication metadata exists.

## BK10-09: Compatibility Preservation

Status: DONE

- [x] Preserve existing `CozyBokSpec` behavior.
- [x] Preserve existing `CozyVideoSpec` behavior.
- [x] Preserve existing `PublicationCompilerSpec` behavior.
- [x] Preserve existing `BridgeContractSpec` behavior.
- [x] Preserve existing SmartDox publication registry behavior.
- [x] Preserve existing `.video/` Phase 8/9 behavior.
- [x] Keep sbt-bridge compatibility for existing actions.
- [x] Do not require Docker, VOICEVOX, ffmpeg, Remotion, Playwright, or whisper.cpp for metadata-only tests.

## BK10-10: Tests And Smoke

Status: DONE

- [x] Add Cozy tests for `bok publish-video`.
- [x] Add Cozy tests for `bok update-publication`.
- [x] Add Cozy tests for `bok publish` step ordering.
- [x] Add Cozy tests for config and CLI precedence.
- [x] Add Cozy/SmartDox tests for preview/draft/production RDF missing policy.
- [x] Add SmartDox tests for Turtle RDF body merge.
- [x] Add SmartDox tests for missing RDF warning/failure policy.
- [x] Add SmartDox tests proving no repository/target/build scan is required.
- [x] Add `src/sbt-test/cozy/bok-video-publication-smoke`.
      The fixture skips older released `cozy.version` values that do not expose
      `bok publish-video`, but fails for SNAPSHOT versions missing the command.
- [x] Run `sbt --batch "testOnly cozy.CozyBokSpec"`.
- [x] Run `sbt --batch "testOnly cozy.video.CozyVideoSpec"`.
- [x] Run `sbt --batch test` in Cozy.
- [x] Run `sbt --batch "testOnly org.smartdox.generators.DoxSiteGeneratorSpec"` in SmartDox.
- [x] Run `sbt --batch test` in SmartDox.
- [x] Run `git diff --check` in changed repositories.

## BK10-11: Phase Closure

Status: DONE

- [x] Confirm all BK10 items are complete.
- [x] Confirm Cozy and SmartDox tests pass.
- [x] Confirm scripted smoke passes or document why it is deferred.
- [x] Update `docs/phase/phase-10.md` closure section.
- [x] Set `docs/phase/README.md` active phase to none.
- [x] Mark Phase 10 closed in strategy.
