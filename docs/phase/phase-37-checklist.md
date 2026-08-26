# Phase 37 Checklist: Logical Explanation Composition and Media Projection

This checklist is the authoritative progress ledger for Phase 37. It is not a
normative behavior contract.

Phase Status: PLANNED

## LOGIC37-01: Logical Composition Design and Specification

Status: NOT STARTED

- [ ] Define Subject Pattern as the typed structure of what is explained and
      Explanation Pattern as the typed Narrative / Argument strategy by which
      it is developed.
- [ ] Specify the authority chain `Narrative / Argument Pattern -> Logical
      Pattern + Relation graph -> Visual Pattern -> renderer binding` and keep
      physical Shape/layout data below the Visual Pattern boundary.
- [ ] Specify independent versioning, identities, parameter ownership, and
      compatibility rules for the two pattern layers.
- [ ] Specify the normalized Explanation Step plan and its authority
      relationship to subject input, explanation input, Visual Pages,
      Storyboard scenes, and generated media.
- [ ] Specify deterministic failure for incompatible patterns, unknown or
      ill-typed parameters, incomplete expansion, and lossy projection.
- [ ] Record the accepted design and specification amendments outside the
      Phase work-ledger layer.

## LOGIC37-02: Typed Subject and Explanation Pattern Models

Status: NOT STARTED

- [ ] Parse versioned Subject and Explanation Pattern definitions and
      instances into separate typed models.
- [ ] Define versioning and deterministic extension for the candidate
      Narrative / Argument catalog, including problem/solution,
      observation/insight/implication, claim/evidence, why/what/how, and
      strategy/execution/outcome families.
- [ ] Validate exact parameters and pattern compatibility without semantic
      inference, summarization, or AI generation inside Cozy.
- [ ] Provide deterministic validate, inspect, and convert behavior with
      canonical identities and structured diagnostics.
- [ ] Prove representation round trips, pattern compatibility, stable
      identity, and strict rejection through Given/When/Then Executable
      Specifications with appropriate property-based coverage.

## LOGIC37-03: Deterministic Explanation Expansion

Status: NOT STARTED

- [ ] Expand one selected Subject Pattern, Explanation Pattern, and typed
      parameter set into a stable ordered Explanation Step plan.
- [ ] Require every Explanation Step to retain its semantic roles, claims,
      Logical Pattern, typed Relations, sources, assets, and provenance before
      Visual Pattern selection.
- [ ] Preserve source claims, assets, citations, emphasis, and
      pattern/parameter provenance through every generated Explanation Step.
- [ ] Make expansion independent of slide numbers, scene numbers, physical
      coordinates, timing, transitions, and renderer-specific identifiers.
- [ ] Reject ambiguous, incomplete, non-deterministic, or unsupported
      expansions with structured diagnostics.
- [ ] Prove `problem-solution` or another representative Narrative Pattern
      expands to semantic roles and Relations rather than directly to arrows,
      cards, coordinates, or Shape kinds.

## LOGIC37-04: Presentation and Video Projection

Status: NOT STARTED

- [ ] Project Explanation Steps into one or multiple Visual Pages for a
      presentation without requiring one-step/one-slide correspondence.
- [ ] Project Explanation Steps into one or multiple video Storyboard
      scenes containing Visual Pages without requiring presentation/video
      count equality.
- [ ] Preserve video narration, speaker, timing, transition, and audiovisual
      review semantics in the Storyboard and video workflow.
- [ ] Bind subject, explanation, step-plan, Visual Page, Storyboard, renderer,
      and output identities into cross-media receipts and stale-input
      rejection.
- [ ] Prove the same accepted semantic composition can use two compatible
      Visual Patterns while retaining its Narrative, Logical, Relation, claim,
      and provenance identities.

## LOGIC37-05: Product Explanation Pattern Acceptance

Status: NOT STARTED

- [ ] Define and validate the representative `software-product` Subject
      Pattern.
- [ ] Define and validate `product-overview` with ordered `vision`, `goal`,
      `context`, `use-case`, and `main-scenario` explanation roles.
- [ ] Define and validate `product-mechanism` relationships from product goals
      and use cases to their realization mechanisms.
- [ ] Prove that the same product subject supports both explanation patterns
      and that each pattern projects coherently to multiple slides and multiple
      video scenes.
- [ ] Keep human/AI-authored subject facts and selected parameters
      distinguishable from Cozy-generated deterministic expansion evidence.

## LOGIC37-06: Review, Validation, and Closure

Status: NOT STARTED

- [ ] Produce one representative product-overview presentation and video
      projection from the same accepted logical composition inputs.
- [ ] Verify semantic-step, Visual Page, slide-page, scene, asset, citation,
      receipt, and stale-input consistency across the representative package.
- [ ] Complete separate semantic, visual, and audiovisual reviews without
      treating deterministic generation as acceptance.
- [ ] Run focused and full Cozy validation, independent review, and final
      ledger synchronization before changing Phase 37 from planned/open.

Phase 37 remains planned. No checklist item is complete and no implementation,
validation, publication, or downstream consumer mutation is claimed.
