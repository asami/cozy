# Phase 37 Checklist: Logical Explanation Composition and Media Projection

This checklist is the authoritative progress ledger for Phase 37. It is not a
normative behavior contract.

Phase Status: IN PROGRESS

## LOGIC37-01: Logical Composition Design and Specification

Status: COMPLETE

- [x] Define Subject Pattern as the typed structure of what is explained and
      Explanation Pattern as the typed Narrative / Argument strategy by which
      it is developed.
- [x] Specify the authority chain `Narrative / Argument Pattern -> Logical
      Pattern + Relation graph -> Visual Pattern -> renderer binding` and keep
      physical Shape/layout data below the Visual Pattern boundary.
- [x] Specify independent versioning, identities, parameter ownership, and
      compatibility rules for the two pattern layers.
- [x] Specify the closed authored `cozy.explanation-projection-map.v1` between
      Plan and final Projection, including independent presentation/page and
      video/scene mappings and its own identity without resource or media
      state.
- [x] Specify the normalized Explanation Step plan as deterministic validation,
      normalization, and copying of explicitly authored Composition steps and
      graphs, with its authority relationship to subject input, explanation
      input, Visual Pages, Storyboard scenes, and generated media.
- [x] Specify explicit explanation-catalog and P36 presentation-catalog file
      companions, the P36 logical-catalog selector, the full presentation
      catalog receipt identity, and the direct-file command/input matrix.
- [x] Specify authored ordered Composition steps with nonempty claims,
      closed `supporting|primary` emphasis, typed Logical Pattern/Relation
      values, references, and parameter selections copied into ordered Plan
      parameter provenance values.
- [x] Specify deterministic failure for incompatible patterns, unknown or
      ill-typed parameters, incomplete authored-step normalization/copying,
      and lossy projection.
- [x] Record the accepted design and specification amendments outside the
      Phase work-ledger layer.

P37-01B is the authorized documentation repair Slice for this stage. Its
P37-01 design/specification contract is review-clean and closed as a Step;
the acceptance commit is pending and has no recorded commit hash.
Implementation and executable specifications remain P37-02 onward. No full
Phase validation or Phase review, publication, push, or downstream consumer
acceptance is claimed.

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

- [ ] Define `expand` as deterministic validation, normalization, and copying
      of one selected Subject Pattern, Explanation Pattern, typed parameter
      set, and explicitly authored ordered Composition steps into a stable
      ordered Explanation Step plan; it does not generate or infer graphs.
- [ ] Require every Explanation Step to retain its semantic roles, claims,
      explicitly authored Logical Pattern, typed Relations, sources, assets,
      and provenance before Visual Pattern selection.
- [ ] Preserve source claims, assets, citations, emphasis, and
      pattern/parameter provenance through every normalized/copied Plan step.
- [ ] Make expansion independent of slide numbers, scene numbers, physical
      coordinates, timing, transitions, and renderer-specific identifiers.
- [ ] Reject ambiguous, incomplete, non-deterministic, or unsupported
      expansions with structured diagnostics.
- [ ] Prove `problem-solution` or another representative Narrative Pattern
      preserves its explicitly authored typed semantic roles and Relations in
      normalized Plan steps rather than generating arrows, cards, coordinates,
      or Shape kinds.

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
- [ ] Define and validate explicitly authored `product-mechanism` relationships
      from product goals and use cases to their realization mechanisms.
- [ ] Prove that the same product subject supports both explanation patterns
      and that each pattern projects coherently to multiple slides and multiple
      video scenes.
- [ ] Keep human/AI-authored subject facts, selected parameters, typed roles,
      and Relations distinguishable from Cozy's deterministic validation,
      normalization, and copying evidence.

## LOGIC37-06: Review, Validation, and Closure

Status: NOT STARTED

- [ ] Produce one representative product-overview presentation and video
      projection from the same accepted logical composition inputs.
- [ ] Verify semantic-step, Visual Page, slide-page, scene, asset, citation,
      receipt, and stale-input consistency across the representative package.
- [ ] Complete separate semantic, visual, and audiovisual reviews without
      treating deterministic validation, normalization, copying, or delivery
      generation as acceptance.
- [ ] Run focused and full Cozy validation, independent review, and final
      ledger synchronization before closing Phase 37 from its in-progress/open
      state.

Phase 37 is IN PROGRESS. LOGIC37-01 is COMPLETE; LOGIC37-02 through LOGIC37-06
remain NOT STARTED/open. P37-01B is documentation-only, and implementation and
executable specifications remain P37-02 onward. No full Phase validation or
Phase review, publication, push, downstream consumer acceptance, or acceptance
commit receipt is claimed.
