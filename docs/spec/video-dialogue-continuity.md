# Legacy Video Dialogue Continuity Specification

Status: NORMATIVE contract for existing behavior; Phase 71.2 P710-F01 accepted.

This contract applies to the legacy `parts[].script` character-dialogue route.
It preserves the separate Storyboard v1/v2 contracts described in
[Video Storyboard](video-storyboard.md). Its executable chapter is
[CozyVideoDialogueContinuitySpec](../../src/test/scala/cozy/video/CozyVideoDialogueContinuitySpec.scala);
responsibility ownership is described in the [design](../design/video-dialogue-continuity.md).

## Continuing a settled visual

An answer may name its base visual with `visual.baseSceneId`. The bundled
renderer resolves that scene and presents its visual at the base scene's last
local frame. Heading, diagram or item content stay settled across the dialogue
boundary. The answer retains the necessary declared image/layout fields to
control character placement. A matching fixed `title`, `concept` or `closing`
preset keeps the palette and backdrop continuous. This contract does not claim
frame identity for arbitrary index-derived palettes or showcase backgrounds.

Speaker, caption, mouth activity and other dialogue surfaces may change. The
contract compares heading, central content and upper left/right backdrop
regions separately; it does not require equality of the whole frame.

## Timing and genuine entrances

For frame rate `fps`, same-section answers add no section transition.
`leadInFrames = round(authoredLeadSilence * fps)` preserves authored silence;
zero is guaranteed only when the authored lead is zero. Scenes are adjacent:
`answer.startFrame = base.startFrame + base.durationFrames`.

A genuine section change in compact character-dialogue with `line-sweep`
adds `round(1.2 * fps)` section-transition frames to both the effective scene
duration and lead-in. This addition is separate from authored audio timing.
A new visual retains its intended entrance. Settled heading and central
content must each differ from their initial entrance frame, avoiding a
comparison of two blank images.

Production staging preserves original script/asset bytes, modes and FileTimes.
Generated props and staged assets are the result of the declared input route;
no historical management hash establishes visual continuity.

## Executable and actual-driver evidence

The property specification exercises public `CozyVideo.render` through the
existing staging runner for twelve generated timing cases, each covering all
three named presets, frame rates 12–30 and positive durations. It checks actual
serialized visual references/content, staged image bytes, adjacent boundaries,
authored silence, genuine section transitions and source preservation.

The opt-in native scenario (`cozy.video.remotion.integration=true`) renders
four short scenes through the actual bundled Remotion renderer and Docker
runner. Declared SVG characters/diagram and deterministic PCM WAVs belong only
to its unique regression directory under `target/test-generated/video-dialogue-continuity`.
It supplements the actual 34-scene Model Harness driver and does not replace
that driver or establish listening acceptance.

Absolute frame indices include the generated opening offset. For last-base
and first-answer frames, the predeclared lossy-video limits in each region are
mean absolute RGB difference ≤ 2.0 and fraction of pixels with any channel
difference > 20 ≤ 0.01. The regions `(x1,y1,x2,y2)` are:

| Region | Bounds |
| --- | --- |
| Heading | (118,90,1150,140) |
| Central visual | (290,145,995,410) |
| Left backdrop | (0,0,70,330) |
| Right backdrop | (1210,0,1280,330) |

Each heading/central entrance comparison must have mean RGB difference > 1.0.
The actual section mid-transition frame must differ from its settled frame.
Frame indices, bounds and measurements are recorded before assertions.

The [baseline journal](../journal/2026/10/2026-10-02-phase-71.2-dialogue-continuity-baseline.md)
retains parent-observed exact boundaries for all 17 pairs of the real native
final MP4 and preservation evidence. F01 is closed with fresh selected
validation (5 suites, 170/170 tests), independent PASS complete-Step review
`22242814-2c16-418b-837d-39d24901e5ba` revision 1 and exact acceptance commit
`6511b6ef7ec3991aa6bb3c233d2ac90bab59c410`. Compound speech payload, listening and
pronunciation-only regeneration remain F02-owned; repository-full validation
remains deferred-not-run to PHASE-71.4.
