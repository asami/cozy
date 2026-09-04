# Phase 46: Content Core Logical Presentation Semantics

Status: COMPLETE; final full validation and the local closure commit are bound
to this Phase release.

Plan date: 2026-09-04

Development item: DEV-017

Predecessor: Phase 45.2 closure

Semantic dependencies: accepted Phases 36, 37, 41, 42, and 45

## Goal

Freeze and implement one typed Content Core presentation-semantics contract
that makes the complete Story Flow and every local Explanation Structure
explicit, reuses the accepted Explanation Pattern/Step and Logical
Pattern/Relation contracts, and provides the single normalized authority that
Phase 46.1 will project into slides, video, and integrated confirmation HTML.

## Accepted contract boundary

Decision `P46-DEC-CONTRACT-001` selects a new versioned Content Core
presentation-semantics boundary, `cozy.content-core.presentation-semantics.v2`.
The adapter preserves the exact closed `cozy.content-core.v1` identity and
requires explicit v2 semantic input; it is directional and rejects unknown or
unmapped semantic values rather than creating aliases or semantic defaults.

Story Flow uses Phase 37 `CompositionStep` identities. Each Explanation
Structure is explicitly bound to one Story Step and reuses its Phase 36 logical
Pattern/node/typed-Relation graph rather than carrying a separately interpreted
graph. Story transitions are distinct typed `StoryTransition` values between
Story Step identities; their `relationType` reuses the closed Relation
vocabulary, but they are not Phase 36 local `Relation` instances. Exact field spelling and Scala types are
frozen in LOGIC46-01 before implementation.

## LOGIC46-01: Authority and requirement traceability

Stage Status:

- Current status: COMPLETE; focused validation and Phase review convergence
  accepted; final full validation bound to the local closure release.
- Owner: Content Core logical-presentation public contract
- Update rule: complete only when every admitted requirement maps to a closed
  typed field, diagnostic, identity rule, and Executable Specification target.

- Freeze Content Core as shared semantic authority, typed Explanation Plan as
  normalized projection, Visual Page as presentation authority, and Storyboard
  as audiovisual authority.
- Freeze `cozy.content-core.presentation-semantics.v2` and its directional v1
  identity adapter; do not extend the closed v1 shape or retain a permissive
  compatibility reader.
- Define Story Flow separately from per-unit Explanation Structure.
- Reuse Phase 37 Explanation Pattern and ordered Steps for Story Flow.
- Reuse Phase 36 Logical Pattern, semantic nodes, and typed Relations for each
  Explanation Structure.
- Make each Structure-to-Step binding and each Story transition Relation exact
  and reject a duplicate graph or vocabulary.
- Preserve article, slide, video, and review HTML as distinct projections and
  Work Products.

## LOGIC46-02: Typed aggregate and strict normalization

Stage Status:

- Current status: COMPLETE; focused validation and Phase review convergence
  accepted; final full validation bound to the local closure release.
- Owner: Content Core presentation-semantics normalization
- Update rule: complete only when typed parsing, cross-reference validation,
  canonical identity, and closed diagnostics pass focused specifications.

- Introduce one immutable typed aggregate carrying Story Flow, Explanation
  Structures, article bindings, and a versioned Projection Policy reference.
- Eliminate generic-map and multi-parameter handoff for this path.
- Reject unknown fields, duplicate identities, missing Step/Structure links,
  unsupported patterns, invalid Relation endpoints, and lossy scalar/array
  coercion before exposing an accepted typed value.
- Prohibit success-path placeholders for declared semantic values that could
  not be normalized.
- Define canonical bytes, semantic identity, exact source bindings, and
  structured diagnostics.

## LOGIC46-03: Projection policy and currentness kernel

Stage Status:

- Current status: COMPLETE; focused validation and Phase review convergence
  accepted; final full validation bound to the local closure release.
- Owner: deterministic logical-to-visual policy contract
- Update rule: complete only when compatible selection, override admission,
  ambiguity rejection, identity, and stale propagation pass.

- Freeze a versioned policy reference that maps logical context and medium to
  compatible Visual Patterns and typed parameters.
- Keep coordinates, fonts, colors, PowerPoint Shapes, CSS selectors, animation
  frames, narration, and timing outside semantic authority.
- Reject missing or ambiguous policy results instead of making arbitrary visual
  choices.
- Bind accepted Content Core, plan, catalogs, policy, sources, and assets into
  exact currentness inputs for Phase 46.1.

## LOGIC46-04: Executable contract acceptance and handoff

Stage Status:

- Current status: COMPLETE; focused validation and Phase review convergence
  accepted; typed handoff frozen; final full validation and local closure bound
  to this release.
- Owner: Phase 46 typed-kernel acceptance
- Update rule: complete only when canonical fixtures, real-vocabulary
  regression, focused/full validation, independent review, and handoff pass.

- Prove Story Flow and multiple Explanation Structures using different Logical
  Patterns and Relation types.
- Prove visually similar sequence and causality remain semantically distinct.
- Use the Article 8 canonical vocabulary as a regression input; do not accept a
  reduced alias-only fixture as sufficient evidence.
- Produce a frozen typed handoff for Phase 46.1 without implementing slide,
  video, or HTML projection in this Phase.

## Exclusions

- Slide, video, or integrated confirmation HTML generation.
- Layout-template implementation, PowerPoint/PDF rendering, video encoding, or
  external media generation.
- Automatic claim, graph, Story Flow, Explanation Structure, or semantic
  acceptance inference.
- A permanent permissive alias reader or a second page-oriented semantic model.
- Reopening or rewriting closed Phase 36/37/41/45.* history.
- Publication, deployment, upload, push, or external-service mutation.

## Completion Criteria

Phase 46 completes only when one closed typed Content Core presentation model
represents Story Flow and Explanation Structures; strict normalization and
diagnostics cannot silently lose declared semantics; deterministic policy and
currentness inputs are frozen; canonical and Article 8 vocabulary executable
specifications pass; and an independent Phase review plus final Cozy validation
accept the exact tree.

## Execution Profile

- planned duration: approximately 6 hours
- recommended parent profile: `gpt-5.6-terra / high`
- expensive reasoning kernel: public authority, typed contract, identity,
  diagnostic, and projection-policy boundaries
- implementation agents: use Luna xhigh for frozen local implementation where
  possible; use Terra high only for contract ambiguity or high-risk boundary
- Phase 46.1 must consume the accepted handoff and must not redefine this
  public contract.

## Primary references

- `docs/notes/document-project-logical-presentation-projection-specification-proposal.md`
- `docs/journal/2026/09/2026-09-04-document-project-logical-presentation-projection-decision.md`
- `docs/design/explanation-composition.md`
- `docs/spec/explanation-composition.md`
- `docs/phase/phase-46-checklist.md`
