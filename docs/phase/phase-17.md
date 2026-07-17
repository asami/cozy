# Phase 17: Profile-Driven Video Scaffolding

Status: closed

Start date: 2026-07-18

## Goal

Make a video source package a first-class Cozy scaffold. Authors select a
composition profile and independent visual-effect profiles, then replace
license-safe placeholders with project-owned assets before rendering and
publication.

The scaffold must not depend on or redistribute media under
`0714.techfirst.lt/assets`. Reusable behavior demonstrated by that project is
represented as renderer-neutral effect profiles and primitives, not copied
project-local scripts or media.

## Scope

In scope:

- `cozy video scaffold <slug>` and deterministic `<slug>.video/` output;
- `explanation` and `explanation-demo-explanation` composition profiles;
- independent section-start, summary, and final-page effect profiles;
- `index.dox`, `video.yaml`, initial script/demo steps, and `assets/` contract;
- generated license-safe placeholder frames and project-owned asset mapping;
- effect-profile expansion to renderer-neutral primitives;
- renderer capability diagnostics and missing-asset diagnostics;
- executable specifications, CLI help, and authoring documentation.

Out of scope:

- downloading external assets during scaffold or build;
- bundling media whose redistribution license is unknown;
- automatic license inference;
- renderer implementations dedicated to one composition profile.

## Stage 17.1: Scaffold Contract

Stage Status:

- Current status: DONE
- Owner: cozy-video
- Checklist basis: `VID17-01` through `VID17-03`

Focus:

- establish the phase ledger and command contract;
- generate deterministic source packages for both initial composition profiles;
- persist independent visual-effect selections;
- generate only license-safe placeholder assets by default.

Verification evidence:

- `cozy video scaffold` generates both initial composition profiles without
  external media;
- generated asset slots carry explicit license and provenance metadata;
- `docs/guide/profile-driven-video-scaffold.md` documents authoring, local
  assets, rendering, RDF, and publication.

## Stage 17.2: Effect Primitive Expansion

Stage Status:

- Current status: DONE
- Owner: cozy-video renderer
- Checklist basis: `VID17-04`

Focus:

- resolve named visual-effect profiles to deterministic renderer-neutral
  primitives;
- add section-start, summary, and final-page composition semantics;
- diagnose unknown profiles and unsupported renderer capabilities.

Verification evidence:

- `CozyVideoEffects` expands section-start, summary, and final-page profiles in
  fixed role and primitive order;
- `video inspect` exposes the expansion and selected renderer capability;
- render rejects unsupported primitive work before invoking external tools;
- `CozyVideoEffectsSpec`, `CozyVideoScaffoldSpec`, and `CozyVideoSpec`: 62 tests
  passed.

## Stage 17.3: Asset Resolution and Rendering

Stage Status:

- Current status: DONE
- Owner: cozy-video renderer
- Checklist basis: `VID17-05` and `VID17-06`

Focus:

- resolve configured project-owned assets and placeholder fallback;
- fail clearly for configured required assets that are absent or unreadable;
- implement renderer adapter support before declaring any primitive capability;
- verify rendering and publication without network asset access.

Asset-resolution evidence:

- scaffolded asset slots persist `path`, `kind`, `required`, `license`, and
  `provenance` in `video.yaml`;
- `CozyVideoAssets` resolves project-relative files, uses generated placeholder
  fallback for optional slots, and rejects required missing files, URLs,
  absolute paths, root escapes, and symlink escapes;
- `video inspect` exposes effective paths, fallback status, license, provenance,
  and the originally requested path when fallback occurs;
- `CozyVideoAssetsSpec`, `CozyVideoEffectsSpec`, `CozyVideoScaffoldSpec`, and
  `CozyVideoSpec`: 67 tests passed.

Rendering evidence:

- the Remotion adapter consumes every initial renderer-neutral primitive and
  copies resolved assets into its isolated public workspace;
- only the final renderable composition part receives the two-second end-card
  hold, while section-start and summary windows remain frame-deterministic;
- successful Remotion execution must create the declared part output;
- part manifests and video RDF retain profile, effect, asset, and timing
  contracts;
- `publish-video` retains authored parts, profile settings, and resolved
  project assets in its publication workspace;
- `CozyVideoScaffoldSpec`, `CozyVideoAssetsSpec`, `CozyVideoEffectsSpec`,
  `CozyVideoProfileRenderSpec`, and `CozyVideoSpec`: 70 tests passed.
- `CozyVideoRemotionIntegrationSpec` rendered one `explanation` part, all
  three `explanation-demo-explanation` parts, and one explanation part using a
  required project-owned SVG into non-empty MP4 files through the real Remotion
  CLI in the local
  `textus-toolchain:phase17-remotion` snapshot image. Run the repeatable smoke
  route with:

  ```console
  sbt --batch \
    -Dcozy.video.remotion.integration=true \
    -Dcozy.video.remotion.image=textus-toolchain:phase17-remotion \
    "testOnly cozy.video.CozyVideoRemotionIntegrationSpec"
  ```

- the integration run exposed and fixed the Remotion public-directory contract,
  Docker IPv4 localhost resolution, tool probe startup timeout, and missing
  `@remotion/cli` dependency in the Textus toolchain image;
- publication workspace preparation retains arbitrary legacy `assets/**`
  content before overlaying normalized profile asset slots;
- the pinned toolchain image passed `textus-toolchain check video`; its
  `remotion`, `@remotion/cli`, and `@remotion/renderer` packages all reported
  version `4.0.490`;
- the full Cozy test suite passed 578 tests, the two gated real-render tests
  passed explicitly, and `git diff --check` completed without errors on July
  18, 2026;
- the Textus Toolchain Runner suite passed 8 tests, built
  `textus-toolchain-runner-0.2.1-SNAPSHOT.car`, and passed integrated CAR ABI
  patch compatibility lint against the preserved `0.2.0` baseline.

## Completion Criteria

Phase 17 closes when both initial composition profiles can be scaffolded,
inspected, rendered with placeholder-only inputs, rendered with configured
project assets, and rejected deterministically for invalid effect capability or
required-asset contracts. Focused and full Cozy tests must pass and the phase
checklist must contain executable evidence for every completed item.

These criteria were satisfied on July 18, 2026. Phase 17 is closed.
