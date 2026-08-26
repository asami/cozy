# Phase 36: Common Visual Page and Cross-Media Presentation Contract

Status: planned

Plan date: 2026-08-26

## Goal

Establish one versioned Visual Page contract that carries a one-screen
Presentation Semantics IR: a typed semantic graph with a named Logical Pattern,
semantic nodes and Relations, and a separately selected Visual Pattern. The
same normalized Visual Page can be projected as one presentation slide or used
as the displayed screen of one video Storyboard scene.

This Phase replaces the former Article Summary Slide Storyboard plan. It does
not preserve `cozy.slide-ir.v1` as the future semantic authority. The existing
Slide IR remains the operational baseline until this Phase accepts an explicit,
diagnostic compatibility or migration path.

## Start gate

- Phase 30 must first close its unified video Storyboard contract. Phase 36
  must not change Phase 30's active schema, Steps, review gates, or completion
  boundary.
- Design and specification authority for Visual Page, pattern catalogs,
  parameter schemas, renderer binding, and compatibility must be accepted
  before implementation begins.
- Existing `cozy.slide-ir.v1`, `cozy.video.storyboard.v1`, `cozy.media.v1`,
  presentation renderer, review state, and receipt behavior remain the
  baseline until the new contracts are accepted.
- This planning record does not authorize implementation, validation,
  publication, or downstream article-repository mutation.

## Boundary and invariants

- Define the one-screen Visual Page envelope as a typed logical graph plus a
  separately selected visual-pattern identity, pattern-specific typed
  parameters, stable page identity, language, assets, and source references.
- Separate the overall Logical Pattern from each edge's semantic Relation so
  `next`, `causes`, `depends-on`, `enables`, and `maps-to` remain distinct even
  when a renderer draws the same arrow.
- Define versioned, closed Logical Pattern and Relation catalogs with exact
  directionality, node-role/cardinality constraints, diagnostics, and
  extension behavior. Unknown values fail rather than degrade to generic
  arrows.
- Keep logical pattern names, Relations, and parameter meaning independent of
  PowerPoint slide numbers, coordinates, fonts, colors, Shape kinds,
  transitions, and renderer-specific object identifiers.
- Define a versioned Visual Pattern Catalog with exact parameter types,
  required and optional fields, compatible logical structures, cardinality,
  diagnostics, and identity. A Visual Pattern is a projection choice, not the
  semantic source.
- Permit one logical graph to select different compatible Visual Patterns
  without changing logical identity, and require Relation changes to change
  semantic identity even when the rendered form is unchanged.
- Provide human-reviewable restricted Markdown and structured YAML/JSON as
  lossless serializations of the same normalized Visual Page identity.
- Allow article-summary presentation pages and video Storyboard scene screens
  to consume the same normalized Visual Page model.
- Keep narration, speaker, duration, silence, transition, animation, and final
  audiovisual review in the video Storyboard and video workflow.
- Keep template/profile binding responsible for mapping Visual Page patterns
  to physical renderer slots. Cozy validates, orchestrates, verifies, and
  records identity; it does not become a PowerPoint layout engine.
- Keep PPTX, slide PNGs, video frames, montage, renderer manifests, and video
  outputs as generated delivery or review artifacts rather than semantic
  authorities.
- Bind Visual Page, Logical/Relation/Visual catalogs, parameter set,
  template/profile binding, renderer, and selected assets into receipts and
  stale-input rejection.
- Reject unknown patterns, unknown or ill-typed parameters, missing required
  parameters, invalid Relations, incompatible logical/visual patterns, unsafe
  asset references, and any silent or lossy conversion.
- Defer explanation-subject patterns, explanation-development patterns, and
  deterministic expansion into multiple slides or scenes to Phase 37.

## Stages

### VIS36-01: Visual Page Design and Specification

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy cross-media presentation design and specification
- Update rule: complete only when the checklist's authority, one-screen
  semantics, pattern/parameter, compatibility, and failure contracts are
  accepted.

Define the stable design and specification boundary before changing the
existing Slide IR, Storyboard, renderer, or Media Package behavior.

### VIS36-02: Typed Presentation Semantics IR and Pattern Catalogs

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy Visual Page model and adapters
- Update rule: complete only when typed parsing, canonicalization, catalog
  resolution, identity, diagnostics, and round-trip evidence pass.

Implement the normalized one-screen semantic graph, Logical/Relation/Visual
catalogs, typed parameter validation, admitted serializations, and explicit
legacy compatibility route.

### VIS36-03: Presentation Projection and Business Binding

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy media presentation orchestration
- Update rule: complete only when Visual Page projection, business profile
  binding, renderer handoff, and generated-artifact verification pass.

Project ordered Visual Pages into presentation slides without making a PPTX or
template example slide the semantic authority.

### VIS36-04: Video Scene Screen Integration

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy video Storyboard integration
- Update rule: complete only when scene-screen references, visual identity,
  review evidence, and unchanged video-specific semantics pass.

Allow a video Storyboard scene to use a Visual Page for its displayed screen
without moving narration, timing, transition, or audiovisual review semantics
out of the Storyboard workflow.

### VIS36-05: Media Package Identity and Review Integration

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy media receipts and review state
- Update rule: complete only when cross-artifact input identity, receipts,
  alignment state, and stale-input rejection pass their checklist evidence.

Bind presentation and video display artifacts to the exact Visual Page,
catalog, binding, renderer, and asset generations used to create them.

### VIS36-06: Cross-Media Acceptance and Closure

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy validation and review
- Update rule: complete only when focused and full validation, independent
  review, and representative presentation/video evidence satisfy the
  checklist.

Prove at least one representative Visual Page through both presentation-slide
and video-scene-screen projections while keeping semantic and rendered-media
acceptance separate.

## Dependencies and exclusions

- Phase 30 owns its active video Storyboard and three-gate video review
  behavior. Phase 36 starts only after Phase 30 closes.
- The current Media Package and Video Storyboard behavior remains governed by
  its accepted design and specification until Phase 36 promotes explicit
  amendments.
- Explanation-subject modeling, explanation-development modeling, automatic
  multi-page or multi-scene expansion, and AI selection or generation of
  patterns and parameters remain outside this Phase and are planned for Phase
  37.
- Cozy performs deterministic validation and projection of already selected
  logical/visual patterns, Relations, and parameters. It does not infer
  article meaning or choose an explanation strategy.
- HTML presentations, infographics, article figures, Mermaid/PlantUML, and SVG
  are architectural consumers of the same boundary but are not Phase 36
  implementation or acceptance requirements.
- New renderer engines, manual PPTX editing, article rewriting, site
  publication, upload, and deployment remain outside this Phase.

## References

- `docs/phase/phase-36-checklist.md`
- `docs/phase/phase-30.md`
- `docs/phase/phase-37.md`
- `docs/spec/media-package.md`
- `docs/design/media-package-operation.md`
- `docs/spec/video-storyboard.md`
- `docs/design/video-storyboard.md`
- `docs/journal/2026/08/2026-08-26-presentation-semantics-ir-phase-36-37.md`
