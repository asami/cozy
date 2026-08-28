# Phase 37: Logical Explanation Composition and Media Projection

Status: IN PROGRESS

Plan date: 2026-08-26

## Goal

Establish two independent pattern contracts for reusable explanation
composition: a Subject Pattern describing the structure of what is explained,
and an Explanation Pattern serving as the Narrative / Argument Pattern by
which that subject is developed for an audience and purpose. Deterministically
validate, normalize, and copy one selected Composition's explicitly authored
ordered steps, typed Logical Patterns, and semantic Relation graphs into an
ordered Plan, then project them into multiple presentation pages or video
scenes through the Visual Page contract established by Phase 36.

## Start gate

- Phase 30 must be closed and Phase 36 must have accepted the common Visual
  Page, pattern catalog, presentation projection, and video scene-screen
  integration contracts.
- Stable design and specification must separate subject semantics,
  explanation development, projection planning, and rendered-media concerns
  before implementation begins.
- The user authorized one single-phase execution with the internal
  Phase/Step/Slice boundary `Phase 37 / P37-01 / P37-01B`. This phase record
  authorizes only the frozen P37-01B documentation repair boundary;
  validation, review, publication, and downstream article-repository mutation
  remain separately gated.

## Boundary and invariants

- Model Subject Pattern and Explanation Pattern as separate, independently
  versioned contracts with typed parameters and stable identity.
- Treat Explanation Pattern as the Narrative / Argument layer above Logical
  Pattern, Relation graph, Visual Pattern, and renderer binding rather than as
  a synonym for a slide layout.
- Make the same subject usable with multiple explanation patterns and the same
  explanation pattern usable with multiple compatible subject patterns.
- Deterministically validate, normalize, and copy a selected subject,
  explanation pattern, parameter set, and explicitly authored ordered steps
  into an ordered medium-neutral Explanation Step plan. Each Step retains
  authored claims, sources, assets, a Logical Pattern, semantic nodes, and
  typed Relations before visual or media projection.
- Allow presentation projection to map one Explanation Step to one or multiple
  Visual Pages and video projection to map one Explanation Step to one or
  multiple Storyboard scenes containing Visual Pages.
- Keep slide-page and video-scene cardinality independent; semantic alignment
  does not require identical page and scene counts.
- Keep AI or human responsibility for subject analysis, pattern selection, and
  parameter authoring separate from Cozy's deterministic validation,
  normalization/copying, projection, identity, and freshness responsibilities.
- Add the closed authored `cozy.explanation-projection-map.v1` between Plan and
  final Projection, with independent presentation/page and video/scene
  mappings and no resource, renderer, layout, timing, narration, or approval
  state.
- Require every operation to receive explicitly named explanation and P36
  presentation catalog files. The Plan binds the P36 logical-catalog identity,
  while the final Projection receipt also binds the full presentation-catalog
  identity; no discovery configuration is used.
- Keep Composition claims, emphasis, typed Logical Pattern/Relation graphs,
  ordered authored steps, and parameter selections explicit; Plan copies them
  and records ordered `{name,value}` parameter provenance plus its identity.
- Introduce `software-product` as the first representative Subject Pattern and
  `product-overview` as the first representative Explanation Pattern with the
  ordered roles `vision`, `goal`, `context`, `use-case`, and `main-scenario`.
- Define `product-mechanism` as the explanation pattern that relates product
  goals and use cases to the mechanisms that realize them.
- Require `product-overview`, `product-mechanism`, and `problem-solution` to
  preserve each explicitly authored step's typed semantic role and Logical
  Pattern/Relation graph through `expand`; no accepted case generates or
  infers those values.
- Define the reusable Narrative / Argument catalog boundary for
  `problem-solution`, `problem-cause-solution`, `current-target`,
  `before-after`, `challenge-approach-result`,
  `observation-insight-implication`, `fact-interpretation-action`,
  `why-what-how`, `input-process-output`, `concept-example`,
  `claim-evidence`, `claim-reasons`, `question-answer`,
  `principle-mechanism-effect`, `strategy-execution-outcome`, and
  `past-present-future`. The design stage may select a smaller acceptance
  subset but must define deterministic versioning and extension.
- Ensure `expand` deterministically validates, normalizes, and copies each
  explicitly authored Narrative Pattern step's semantic role and typed
  Relation graph, never generating or inferring them and never mapping
  directly to arrows, cards, coordinates, or PowerPoint Shapes.
- Allow the same accepted Narrative/Logical composition to choose different
  compatible Visual Patterns without changing its semantic identity.
- Preserve logical composition IR, Visual Page IR, Storyboard, PPTX, and MP4
  as distinct authority or artifact layers; no generated delivery artifact may
  become the semantic source.

## Stages

### LOGIC37-01: Logical Composition Design and Specification

Stage Status:

- Current status: COMPLETE
- Owner: Cozy logical presentation design and specification
- Update rule: complete only when the checklist's authority, compatibility,
  composition, projection, and failure contracts are accepted.

Define the Subject and Narrative / Argument pattern layers, their compatibility
relationship, authored Logical Pattern/Relation validation and copying,
Explanation Step normalization, and media-projection boundaries.

`P37-01B` is the authorized documentation repair Slice for the accepted
`cozy.explanation.catalog.v1`, `cozy.explanation-composition.v1`,
`cozy.explanation-plan.v1`, `cozy.explanation-projection-map.v1`, and
`cozy.explanation-projection.v1` contract. Its design and specification
documents define the closed v1 fields, canonical identities, explicit
catalog companions, authored Composition steps, independent mapping request,
deterministic projection receipt, and later command matrix. The P37-01
design/specification contract is review-clean and closed as a Step; its local
accepted documentation commit is `5946eb6dcb18ac421d41c56919e981a338e16437`.
Phase 37 remains IN PROGRESS: no full Phase validation or Phase review,
publication, push, or downstream consumer acceptance is claimed.

### LOGIC37-02: Typed Subject and Explanation Pattern Models

Stage Status:

- Current status: COMPLETE
- Owner: Cozy logical presentation models and adapters
- Update rule: complete only when typed parsing, pattern resolution,
  parameter validation, identity, diagnostics, and round-trip evidence pass.

Implement versioned Subject and Narrative / Argument-oriented Explanation
Pattern catalogs and one normalized logical composition model without semantic
inference by Cozy.

The local accepted composition/catalog/plan implementation commit
`73130f0d7111cdef3f2988ee041ad8e97fdb4362` completes deterministic direct-file
parsing of the accepted v1 inputs, typed pattern and parameter resolution,
canonical identities, and strict structured diagnostics. Its accepted focused
Executable Specifications cover this direct-file behavior without semantic
inference, rendering, publication, or external-consumer acceptance.

### LOGIC37-03: Deterministic Explanation Expansion

Stage Status:

- Current status: COMPLETE
- Owner: Cozy explanation planning
- Update rule: complete only when explicitly authored compatible-pattern steps
  and their Logical Pattern/Relation graphs are deterministically validated,
  normalized, and copied into stable, inspectable Explanation Steps with
  deterministic identity and diagnostics.

`expand` means deterministic validation, normalization, and copying of
explicitly authored Composition steps, typed Logical Patterns, and Relation
graphs into an ordered medium-neutral plan; it does not generate or infer
those roles or graphs. The operation does not decide physical page layout,
scene timing, or renderer effects.

The local accepted composition/catalog/plan implementation commit
`73130f0d7111cdef3f2988ee041ad8e97fdb4362` and its accepted focused
Executable Specifications complete deterministic direct-file expansion,
normalization, copying, stable identity, and strict diagnostics for the
accepted inputs. They do not generate or infer semantic roles or Relation
graphs.

### LOGIC37-04: Presentation and Video Projection

Stage Status:

- Current status: COMPLETE
- Owner: Cozy cross-media projection
- Update rule: complete only when independent slide-page and video-scene
  mappings, provenance, and stale-input behavior pass.

Project Explanation Steps into Visual Page sequences for presentations and
Storyboard scene sequences for video while preserving each medium's own
execution contract.

The local accepted projection implementation commit
`ed427a209c3bade9fb87c2be1a068deee280afdb`, with focused validation
`testOnly cozy.media.CozyExplanationSpec cozy.media.CozyExplanationProjectionSpec`
(8 succeeded / 0 failed), completes independent page and scene mapping,
v2 Visual Page Storyboard checks, identity-only Storyboard selection,
direct-file routing, and projection receipts/currentness. It does not claim
rendering, publication, PPTX or MP4 delivery, external-consumer acceptance,
or semantic, visual, or audiovisual approval.

### LOGIC37-05: Product Explanation Pattern Acceptance

Stage Status:

- Current status: IN PROGRESS
- Owner: Cozy product-explanation acceptance
- Update rule: complete only when `software-product`, `product-overview`, and
  `product-mechanism` satisfy their checklist scenarios in both media.

Use a representative product explanation to prove the distinction among
subject structure, explanation development, visual pages, slides, and scenes.

The local accepted projection implementation commit
`ed427a209c3bade9fb87c2be1a068deee280afdb` and its focused validation
`testOnly cozy.media.CozyExplanationSpec cozy.media.CozyExplanationProjectionSpec`
(8 succeeded / 0 failed) cover accepted page and scene projection for the
representative `software-product` / `product-overview` direct-file case. The
explicitly authored `product-mechanism` relationships are accepted at the
logical composition/plan boundary, but their page and scene projection remains
unproven and open. This is deterministic Cozy coverage only; it does not
constitute semantic, visual, audiovisual, or external-consumer approval.

### LOGIC37-06: Review, Validation, and Closure

Stage Status:

- Current status: IN PROGRESS
- Owner: Cozy validation and review
- Update rule: complete only when full Cozy validation, exactly one independent
  full Phase review, required closure ledger/status synchronization, and a
  distinct Phase release commit are complete; separate semantic, visual, and
  audiovisual approval remains a non-Cozy boundary.

Focused projection validation is accepted, but full Cozy validation, exactly
one independent full Phase review, required closure ledger/status
synchronization, and a distinct Phase release commit remain incomplete.

Phase 37 is IN PROGRESS. LOGIC37-01 through LOGIC37-04 are COMPLETE;
LOGIC37-05 and LOGIC37-06 are IN PROGRESS.

## Dependencies and exclusions

- Phase 36 owns one-screen Visual Page semantics, Logical Pattern and Relation
  catalogs, Visual Pattern Catalog, template binding, and presentation/video
  display integration.
- Phase 37 does not redesign PowerPoint templates, video renderers, narration
  synthesis, timing policy, transition/effect profiles, or final media review
  gates.
- Cozy does not infer a subject model, select an explanation strategy, write
  article meaning, generate a Logical Pattern or Relation graph, or make
  semantic acceptance decisions.
- Site publication, upload, deployment, and external article-repository
  mutation remain outside this Phase.

## References

- `docs/phase/phase-37-checklist.md`
- `docs/design/explanation-composition.md`
- `docs/spec/explanation-composition.md`
- `docs/phase/phase-36.md`
- `docs/phase/phase-30.md`
- `docs/spec/media-package.md`
- `docs/design/media-package-operation.md`
- `docs/spec/video-storyboard.md`
- `docs/design/video-storyboard.md`
- `docs/journal/2026/08/2026-08-26-presentation-semantics-ir-phase-36-37.md`
