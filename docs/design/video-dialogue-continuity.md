# Legacy Video Dialogue Continuity Design

Status: NORMATIVE description of existing behavior; no production correction selected.

The [continuity specification](../spec/video-dialogue-continuity.md) describes
one use case: an author retains a settled visual while a dialogue speaker
changes. The [executable chapter](../../src/test/scala/cozy/video/CozyVideoDialogueContinuitySpec.scala)
consumes existing production behavior without changing API, schema or renderer
resources.

## Responsibility boundary

The legacy script owns `visual.baseSceneId`, visual content and necessary
image/layout declarations, named fixed presets, sections and authored silence.
`CozyVideo.render` plans this declared input, stages read-only source assets,
and serializes actual scene timing and props. The bundled `DialogueVideo.jsx`
resolves the base scene and renders its visual at the last base local frame.
The answer's own speaker/caption/mouth use its current timing independently.
Matching named `title`, `concept` and `closing` presets keep the fixed palette;
arbitrary index-derived palettes and showcase motion are outside this guarantee.

Compact section transitions remain a separate production concern. A real
section change with `line-sweep` adds 1.2 seconds rounded to frames to duration
and lead-in; same-section answers preserve only authored lead silence. Opening
offset belongs to composition timing and must be included when extracting
absolute rendered boundaries. Genuine new visuals retain entrances.

## Specification responsibilities

The property scenario uses the existing package staging runner to observe
public rendering output across frame rates, durations, silence and named
presets. It interprets actual props and staged image bytes before checking
continuity references, content, timing and source preservation.

The native regression uses `VideoProcessRunner.default`, the existing Docker
image, the bundled renderer and Docker ffmpeg frame extraction. Java ImageIO
measures heading, central content and both upper backdrop regions while
excluding permitted dialogue motion. Measurements precede assertions and use
the predeclared lossy-video limits. Positive heading and central entrance
comparisons prevent vacuous blank-frame success; a distinct mid-section sweep
preserves real section-start behavior.

Each scenario owns a unique directory beneath
`target/test-generated/video-dialogue-continuity`, retaining its outputs and
measurements without deleting shared or private acceptance artifacts. Its
four-scene deterministic audio fixture is solely a renderer regression.
The actual unchanged 34-scene Model Harness native driver and all 17 exact
final-MP4 boundary observations remain separate diagnostic evidence in the
[baseline journal](../journal/2026/10/2026-10-02-phase-71.2-dialogue-continuity-baseline.md).

No same-visual failure was reproduced at those actual boundaries, so there is
no production renderer correction. F01 acceptance still requires fresh
selected validation, independent complete-Step review and an exact local
commit. F02 owns actual compound-term payload, listening and pronunciation-only
regeneration. Phase 71.2 remains IN_PROGRESS and full-suite validation remains
deferred-not-run to PHASE-71.4.
