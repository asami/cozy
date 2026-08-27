# Phase 36: Common Visual Page and Cross-Media Presentation Contract

Status: COMPLETE

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

- Current status: DONE
- Owner: Cozy cross-media presentation design and specification
- Update rule: complete only when the checklist's authority, one-screen
  semantics, pattern/parameter, compatibility, and failure contracts are
  accepted.

Define the stable design and specification boundary before changing the
existing Slide IR, Storyboard, renderer, or Media Package behavior.

`P36-01A` records the completed documentation-only Visual Page
normative-contract foundation. It does not authorize implementation, executable
specifications, renderer work, receipt execution, validation, review, migration
execution, or acceptance.

### VIS36-02: Typed Presentation Semantics IR and Pattern Catalogs

Stage Status:

- Current status: DONE
- Owner: Cozy Visual Page model and adapters
- Update rule: complete only when typed parsing, canonicalization, catalog
  resolution, identity, diagnostics, and round-trip evidence pass.

Implement the normalized one-screen semantic graph, Logical/Relation/Visual
catalogs, typed parameter validation, admitted serializations, and explicit
legacy compatibility route.

`P36-02A` and `P36-02B` are accepted as DONE: P36-02A delivers the typed
Visual Page core, and P36-02B delivers the explicit legacy Slide IR migration
contract. The accepted implementation records are commits
`60ec0b0ac4c3fe9c0f97f2440be443d0859eee72` and
`e4d76b996c54cf8237c0e2b8fc1d8aa7216ef1e6`.

### VIS36-03: Presentation Projection and Business Binding

Stage Status:

- Current status: DONE
- Owner: Cozy media presentation orchestration
- Update rule: complete only when Visual Page projection, business profile
  binding, renderer handoff, and generated-artifact verification pass.

Project ordered Visual Pages into presentation slides without making a PPTX or
template example slide the semantic authority.

P36-03A is accepted in `a248fade7adaa80757ccd0ee77de46588e3e987e`; P36-03B/C
is accepted in `4d4d621fdbc1822233d49de6a618708567c02d8a`, whose final-tree
focused validation `51002-20260827T073710Z` passed 29/29 with zero review
findings. The accepted route retains PPTX/template/generated artifacts as
non-semantic evidence, and confines physical layout vocabulary to binding or
generated output. VIS36-03 is DONE; it grants no downstream-consumer approval.

### VIS36-04: Video Scene Screen Integration

Stage Status:

- Current status: DONE
- Owner: Cozy video Storyboard integration
- Update rule: complete only when scene-screen references, visual identity,
  review evidence, and unchanged video-specific semantics pass.

Allow a video Storyboard scene to use a Visual Page for its displayed screen
without moving narration, timing, transition, or audiovisual review semantics
out of the Storyboard workflow.

`P36-04-DEC-001` is consumed only for Phase 36 / VIS36-04: the user approved
the exact v2 visual-page screen shape `{kind,source,catalog,pageId}`. P36-04
implementation is limited to the JSON-only Storyboard v2 parser, direct
VisualPageSet/catalog/pageId validation, planning metadata projection, and
explicit v1-text-to-v2 migration. Focused validation invocation
`90284-20260827T085957Z` (`testOnly cozy.video.CozyVideoStoryboardSpec`) reported
16 succeeded, 0 failed/aborted, SBT/wrapper 0, and the lock released.
Independent `P36-04-REREVIEW-002` is PASS and `CB-P36-04-RR-001` is resolved.
P36-04's acceptance record preserves every Storyboard-specific semantic and
lifecycle field. P36-05 supplied the later visual evidence/identity proof and
stale-input gates. VIS36-04 is DONE; no renderer, publication, or external
consumer acceptance is claimed.

Nonblocking hygiene disposition `HYG-36-04-001`: the pre-existing unified
Storyboard parser now exceeds 1,000 lines after the v2 boundary. A safe
physical split needs a new source path outside this frozen implementation
manifest; defer that mechanical split to a separately authorized hygiene batch.

### VIS36-05: Media Package Identity and Review Integration

Stage Status:

- Current status: DONE
- Owner: Cozy media receipts and review state
- Update rule: complete only when cross-artifact input identity, receipts,
  alignment state, and stale-input rejection pass their checklist evidence.

Bind presentation and video display artifacts to the exact Visual Page,
catalog, binding, renderer, and asset generations used to create them.

`P36-05-DEC-001` is consumed only for Phase 36 / VIS36-05. It accepts a
Storyboard-v2-only review-evidence/handoff v2 route with a safe binding
declaration under `storyboardReview.visualPage`. That route must retain the
literal screen reference and prove the exact VisualPageSet, catalog, logical
page, visual page, page-asset, binding, and effective selected renderer
identities. A changed, missing, unsafe, or unresolved input fails closed.
Storyboard v1, review-evidence/handoff v1, and legacy video projects remain
unchanged. The decision record is
[`2026-08-27-phase-36-p36-05-decision.md`](../journal/2026/08/2026-08-27-phase-36-p36-05-decision.md).
The admitted v2 implementation and executable specifications are delivered.
Focused validation and independent Step re-review passed
(`54038-20260827T112424Z` ReviewSpec 12/12;
`54763-20260827T112533Z` accumulator 39/39). VIS36-05 is DONE; Phase closure
remains open.

### VIS36-06: Cross-Media Acceptance and Closure

Stage Status:

- Current status: DONE
- Owner: Cozy validation and review
- Update rule: complete only when Phase-level full validation, independent
  Phase review, and representative presentation/video evidence satisfy the
  checklist.

Prove at least one representative Visual Page through both presentation-slide
and video-scene-screen projections while keeping semantic and rendered-media
acceptance separate.

`P36-06-DEC-001` admits only the closed `cozy media cross-review build|verify`
structural evidence route and `cozy.media.cross-review.v1`. Its focused
executable-specification validation passed (2/2 in invocation
`78836-20260827T121036Z`; 30/30 cross-media/presentation/storyboard regression
in `79792-20260827T121146Z`; 31/31 after repair in
`40917-20260827T151855Z`). Independent Step review and focused re-review passed;
this local acceptance Step commit records the structural route only. The
representative article-summary fixture now supplies one same-page presentation
and Storyboard v2 projection; focused receipts `55224-20260827T154155Z` and
`56922-20260827T154423Z` passed 3/3, while final focused receipt
`68270-20260827T160345Z` passed 50/50 across 5 suites. Its retained 1280x720
slide image was separately inspected for readable text, asset presence, layout,
clipping, and cross-media identity consistency. This remains test-fixture
inspection, not semantic/visual/audiovisual approval, renderer acceptance,
or publication. The mandatory full Phase review found
`CPB-P36-REVIEW-001`: duplicate JSON object fields could be collapsed before
currentness/identity validation. Cycle 1 now strictly rejects duplicate fields
before Circe parsing in both Cross-media Review proof and Storyboard v2
evidence/handoff readers; the independent focused re-review verified the
repair, with no remaining Current Phase Blocker.

VIS36-06 and Phase 36 are COMPLETE in this release closure. The recorded
closed API verifies only presentation-review and Storyboard-v2
currentness/identity. It neither renders nor builds video, publishes, or
records semantic, visual, or audiovisual approval. Phase 37 remains NOT
STARTED, and no external-consumer acceptance is claimed.

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
