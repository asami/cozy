# Phase 37: Logical Explanation Composition and Media Projection

Status: planned

Plan date: 2026-08-26

## Goal

Establish two independent pattern contracts for reusable explanation
composition: a Subject Pattern describing the structure of what is explained,
and an Explanation Pattern serving as the Narrative / Argument Pattern by
which that subject is developed for an audience and purpose. Deterministically
expand one selected composition into typed Logical Patterns and semantic
Relation graphs, then project them into multiple presentation pages or video
scenes through the Visual Page contract established by Phase 36.

## Start gate

- Phase 30 must be closed and Phase 36 must have accepted the common Visual
  Page, pattern catalog, presentation projection, and video scene-screen
  integration contracts.
- Stable design and specification must separate subject semantics,
  explanation development, projection planning, and rendered-media concerns
  before implementation begins.
- This planning record does not authorize implementation, validation,
  publication, or downstream article-repository mutation.

## Boundary and invariants

- Model Subject Pattern and Explanation Pattern as separate, independently
  versioned contracts with typed parameters and stable identity.
- Treat Explanation Pattern as the Narrative / Argument layer above Logical
  Pattern, Relation graph, Visual Pattern, and renderer binding rather than as
  a synonym for a slide layout.
- Make the same subject usable with multiple explanation patterns and the same
  explanation pattern usable with multiple compatible subject patterns.
- Normalize a selected subject, explanation pattern, and parameter set into an
  ordered medium-neutral Explanation Step plan. Each Step carries claims,
  sources, assets, a Logical Pattern, semantic nodes, and typed Relations
  before visual or media projection.
- Allow presentation projection to map one Explanation Step to one or multiple
  Visual Pages and video projection to map one Explanation Step to one or
  multiple Storyboard scenes containing Visual Pages.
- Keep slide-page and video-scene cardinality independent; semantic alignment
  does not require identical page and scene counts.
- Keep AI or human responsibility for subject analysis, pattern selection, and
  parameter authoring separate from Cozy's deterministic validation,
  expansion, projection, identity, and freshness responsibilities.
- Introduce `software-product` as the first representative Subject Pattern and
  `product-overview` as the first representative Explanation Pattern with the
  ordered roles `vision`, `goal`, `context`, `use-case`, and `main-scenario`.
- Define `product-mechanism` as the explanation pattern that relates product
  goals and use cases to the mechanisms that realize them.
- Define the reusable Narrative / Argument catalog boundary for
  `problem-solution`, `problem-cause-solution`, `current-target`,
  `before-after`, `challenge-approach-result`,
  `observation-insight-implication`, `fact-interpretation-action`,
  `why-what-how`, `input-process-output`, `concept-example`,
  `claim-evidence`, `claim-reasons`, `question-answer`,
  `principle-mechanism-effect`, `strategy-execution-outcome`, and
  `past-present-future`. The design stage may select a smaller acceptance
  subset but must define deterministic versioning and extension.
- Ensure a Narrative Pattern expands to semantic roles and Relations, never
  directly to arrows, cards, coordinates, or PowerPoint Shapes.
- Allow the same accepted Narrative/Logical composition to choose different
  compatible Visual Patterns without changing its semantic identity.
- Preserve logical composition IR, Visual Page IR, Storyboard, PPTX, and MP4
  as distinct authority or artifact layers; no generated delivery artifact may
  become the semantic source.

## Stages

### LOGIC37-01: Logical Composition Design and Specification

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy logical presentation design and specification
- Update rule: complete only when the checklist's authority, compatibility,
  composition, projection, and failure contracts are accepted.

Define the Subject and Narrative / Argument pattern layers, their compatibility
relationship, Logical Pattern/Relation expansion, Explanation Step
normalization, and media-projection boundaries.

### LOGIC37-02: Typed Subject and Explanation Pattern Models

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy logical presentation models and adapters
- Update rule: complete only when typed parsing, pattern resolution,
  parameter validation, identity, diagnostics, and round-trip evidence pass.

Implement versioned Subject and Narrative / Argument-oriented Explanation
Pattern catalogs and one normalized logical composition model without semantic
inference by Cozy.

### LOGIC37-03: Deterministic Explanation Expansion

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy explanation planning
- Update rule: complete only when compatible patterns expand into stable,
  inspectable Explanation Steps with deterministic identity and diagnostics.

Expand selected patterns and parameters into an ordered medium-neutral plan of
typed Logical Patterns and Relation graphs without deciding physical page
layout, scene timing, or renderer effects.

### LOGIC37-04: Presentation and Video Projection

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy cross-media projection
- Update rule: complete only when independent slide-page and video-scene
  mappings, provenance, and stale-input behavior pass.

Project Explanation Steps into Visual Page sequences for presentations and
Storyboard scene sequences for video while preserving each medium's own
execution contract.

### LOGIC37-05: Product Explanation Pattern Acceptance

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy product-explanation acceptance
- Update rule: complete only when `software-product`, `product-overview`, and
  `product-mechanism` satisfy their checklist scenarios in both media.

Use a representative product explanation to prove the distinction among
subject structure, explanation development, visual pages, slides, and scenes.

### LOGIC37-06: Review, Validation, and Closure

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy validation and review
- Update rule: complete only when focused and full validation, independent
  review, cross-media acceptance, and final ledger synchronization satisfy the
  checklist.

## Dependencies and exclusions

- Phase 36 owns one-screen Visual Page semantics, Logical Pattern and Relation
  catalogs, Visual Pattern Catalog, template binding, and presentation/video
  display integration.
- Phase 37 does not redesign PowerPoint templates, video renderers, narration
  synthesis, timing policy, transition/effect profiles, or final media review
  gates.
- Cozy does not infer a subject model, select an explanation strategy, write
  article meaning, or make semantic acceptance decisions.
- Site publication, upload, deployment, and external article-repository
  mutation remain outside this Phase.

## References

- `docs/phase/phase-37-checklist.md`
- `docs/phase/phase-36.md`
- `docs/phase/phase-30.md`
- `docs/spec/media-package.md`
- `docs/design/media-package-operation.md`
- `docs/spec/video-storyboard.md`
- `docs/design/video-storyboard.md`
- `docs/journal/2026/08/2026-08-26-presentation-semantics-ir-phase-36-37.md`
