# Phase 31: Video Encoding Policy Profiles

Status: planned

Plan date: 2026-08-18

Phase Plan Gate: PROCEED

- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4-6h
- recommended_minimum_effort: high
- runtime_suitability: suitable for a focused implementation phase
- source: KnowledgeHub comparison-video production follow-up

## Goal

Make Cozy's lightweight video settings the explicit baseline and add a small
renderer policy vocabulary for selecting an encoding intent without repeating
individual frame-rate, resolution, and CRF settings in every `video.yaml`.

The intended configuration is:

```yaml
renderer:
  engine: remotion
  policy: lightweight
```

The initial policies are:

| Policy | Resolution | FPS | CRF | Purpose |
| --- | ---: | ---: | ---: | --- |
| `lightweight` | 1280x720 | 18 | 32 | Default presentation and dialogue video |
| `standard` | 1280x720 | 30 | 23 | Motion-sensitive ordinary publication |
| `quality` | 1920x1080 | 30 | 18 | High-quality master output |

Explicit `fps`, `width`, `height`, `crf`, and `x264Preset` values override the
selected policy. If neither a policy nor explicit values are present,
`lightweight` is used.

## Boundary and invariants

- Keep encoding policy separate from composition profile, visual-effect
  profile, narration provider, and renderer engine selection.
- Make `renderer.policy` the canonical encoding-policy property.
- Keep `renderer.strategy` as the canonical composition strategy property.
- Remove the current decoder interpretation of `renderer.policy` as a legacy
  alias for `renderer.strategy`; the two concepts must not share one property.
- Resolve effective renderer settings once and expose the resolved policy and
  values in inspect, plan, renderer props, generated manifests, RDF, and review
  evidence.
- Apply precedence in this order: explicit field, selected policy, lightweight
  default.
- Validate unknown policy names and invalid FPS, dimensions, CRF, or x264 preset
  before rendering starts.
- Ensure Remotion receives the resolved CRF and x264 preset as actual CLI
  options; parsing settings without applying them is not sufficient.
- Keep 1280x720 output readable for character dialogue, captions, and diagrams.
- Do not change audio synthesis, scene duration, semantic content, or
  publication behavior.

## Stages

### VP31-01: Encoding Policy Contract

Stage Status:

- Current status: PLANNED
- Owner: Cozy video model and configuration
- Checklist basis: `VP31-01`

Define the policy vocabulary, resolved settings model, precedence, validation,
and the separation between encoding policy and composition strategy.

### VP31-02: Renderer and Metadata Integration

Stage Status:

- Current status: PLANNED
- Owner: Cozy Remotion adapter and video metadata
- Checklist basis: `VP31-02`

Apply resolved settings to Remotion execution and project them consistently to
inspection, manifests, RDF, and review evidence.

### VP31-03: Runtime Acceptance

Stage Status:

- Current status: PLANNED
- Owner: Cozy video executable specifications and runtime smoke
- Checklist basis: `VP31-03`

Compare lightweight, standard, quality, and explicit-override outputs and prove
the lightweight default with a representative character-dialogue video.

## Completion criteria

- A renderer without encoding fields resolves to 1280x720, 18 fps, and CRF 32.
- Each named policy resolves deterministically to its contracted settings.
- Explicit fields override only their corresponding policy values.
- Unknown or invalid settings fail before Remotion starts.
- Inspect, render props, final manifest, RDF, and review evidence agree on the
  effective policy and encoding values.
- Runtime evidence confirms the lightweight output is 1280x720 at 18 fps and
  materially smaller than the same fixture rendered with `standard`.
- Focused and full Cozy tests pass and the phase ledger converges.

## Dependencies

- Phase 8 supplies the Cozy video execution pipeline.
- Phase 17 supplies composition profiles and Remotion rendering.
- Phase 20 supplies the current provider-neutral production workflow.
- Phase 30 may consume the policy contract but does not own encoding policy.

## References

- `docs/phase/phase-31-checklist.md`
- `src/main/scala/cozy/video/CozyVideoModel.scala`
- `src/main/scala/cozy/video/CozyVideoRenderTemplates.scala`
