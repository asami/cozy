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
the local accepted documentation commit is
`5946eb6dcb18ac421d41c56919e981a338e16437`. No full Phase validation or Phase
review, publication, push, or downstream consumer acceptance is claimed.

## LOGIC37-02: Typed Subject and Explanation Pattern Models

Status: COMPLETE

- [x] Parse versioned Subject and Explanation Pattern definitions and
      instances from explicitly named direct files into separate typed models.
- [x] Define versioned direct-file catalog resolution and deterministic
      extension for the candidate
      Narrative / Argument catalog, including problem/solution,
      observation/insight/implication, claim/evidence, why/what/how, and
      strategy/execution/outcome families.
- [x] Validate exact parameters and pattern compatibility without semantic
      inference, summarization, or AI generation inside Cozy.
- [x] Provide deterministic direct-file validation and conversion behavior
      with canonical identities and structured diagnostics.
- [x] Prove direct-file parsing, pattern compatibility, stable identity, and
      strict rejection through accepted Given/When/Then Executable
      Specifications.

Local accepted composition/catalog/plan implementation commit
`73130f0d7111cdef3f2988ee041ad8e97fdb4362` supplies the accepted focused
evidence for deterministic direct-file parsing, strict diagnostics, and
Executable Specification coverage. No semantic inference, rendering,
publication, or external-consumer acceptance is claimed.

## LOGIC37-03: Deterministic Explanation Expansion

Status: COMPLETE

- [x] Define `expand` as deterministic validation, normalization, and copying
      of one selected Subject Pattern, Explanation Pattern, typed parameter
      set, and explicitly authored ordered Composition steps into a stable
      ordered Explanation Step plan; it does not generate or infer graphs.
- [x] Require every Explanation Step to retain its semantic roles, claims,
      explicitly authored Logical Pattern, typed Relations, sources, assets,
      and provenance before Visual Pattern selection.
- [x] Preserve source claims, assets, citations, emphasis, and
      pattern/parameter provenance through every normalized/copied Plan step.
- [x] Make expansion independent of slide numbers, scene numbers, physical
      coordinates, timing, transitions, and renderer-specific identifiers.
- [x] Reject ambiguous, incomplete, non-deterministic, or unsupported
      expansions with structured diagnostics.
- [x] Prove representative direct-file product explanation inputs preserve
      their explicitly authored typed semantic roles and Relations in
      normalized Plan steps rather than generating arrows, cards, coordinates,
      or Shape kinds.

Local accepted composition/catalog/plan implementation commit
`73130f0d7111cdef3f2988ee041ad8e97fdb4362` supplies the accepted focused
evidence for deterministic direct-file expansion, normalization/copying,
identity, strict diagnostics, and Executable Specification coverage.

## LOGIC37-04: Presentation and Video Projection

Status: COMPLETE

- [x] Project Explanation Steps into one or multiple Visual Pages for a
      presentation without requiring one-step/one-slide correspondence.
- [x] Project Explanation Steps into one or multiple video Storyboard
      scenes containing Visual Pages without requiring presentation/video
      count equality.
- [x] Check v2 Visual Pages through the identity-only Storyboard selector,
      leaving narration, speaker, timing, transition, and audiovisual approval
      to their separate downstream contracts.
- [x] Bind direct-file request, plan, P36 presentation catalog, selected
      Visual Page, identity-only Storyboard selector, and projection receipt
      identities with currentness rejection.
- [x] Prove representative direct-file product explanations map independently
      to Visual Pages and Storyboard scenes while retaining their accepted
      logical composition identities.

Local accepted projection implementation commit
`ed427a209c3bade9fb87c2be1a068deee280afdb`, with focused validation
`testOnly cozy.media.CozyExplanationSpec cozy.media.CozyExplanationProjectionSpec`
(8 succeeded / 0 failed), supplies the accepted evidence. It does not claim
rendering, publication, PPTX or MP4 delivery, external-consumer acceptance,
or semantic, visual, or audiovisual approval.

## LOGIC37-05: Product Explanation Pattern Acceptance

Status: COMPLETE

- [x] Define and validate the representative `software-product` Subject
      Pattern.
- [x] Define and validate `product-overview` with ordered `vision`, `goal`,
      `context`, `use-case`, and `main-scenario` explanation roles.
- [x] Define and validate explicitly authored `product-mechanism` relationships
      from product goals and use cases to their realization mechanisms.
- [x] Prove the representative `software-product` / `product-overview`
      direct-file case through the accepted independent page and scene
      mappings.
- [x] Prove the representative `product-mechanism` direct-file case through
      independent page and scene mappings.
- [x] Keep human/AI-authored subject facts, selected parameters, typed roles,
      and Relations distinguishable from Cozy's deterministic validation,
      normalization, and copying evidence.

The local accepted product-overview projection implementation commit
`ed427a209c3bade9fb87c2be1a068deee280afdb`, with focused validation
`testOnly cozy.media.CozyExplanationSpec cozy.media.CozyExplanationProjectionSpec`
(8 succeeded / 0 failed), supplies accepted page and scene projection
evidence for the representative `software-product` / `product-overview`
direct-file case. P37-05A's accepted product-mechanism projection
implementation commit `ca68f59a9b513d4d718a523d6617fd1f41bc66c7`, with focused
validation `testOnly cozy.media.CozyExplanationProjectionSpec` (4 succeeded /
0 failed), supplies accepted independent page and scene projection evidence
for the representative `product-mechanism` direct-file case. This is
deterministic Cozy coverage only; it does not constitute semantic, visual,
audiovisual, or external-consumer approval.

## LOGIC37-06: Review, Validation, and Closure

Status: IN PROGRESS

- [ ] Produce one representative product-overview presentation and video
      projection from the same accepted logical composition inputs.
- [ ] Verify semantic-step, Visual Page, slide-page, scene, asset, citation,
      receipt, and stale-input consistency across the representative package.
- [ ] Complete separate semantic, visual, and audiovisual reviews without
      treating deterministic validation, normalization, copying, or delivery
      generation as acceptance. This remains a separate non-Cozy approval
      boundary.
- [ ] Run full Cozy validation, exactly one independent full Phase review, and
      required closure ledger/status synchronization before closing Phase 37
      from its in-progress/open state with a distinct Phase release commit.

Focused projection validation
`testOnly cozy.media.CozyExplanationSpec cozy.media.CozyExplanationProjectionSpec`
has succeeded with 8 succeeded / 0 failed, but every closure checkbox above
remains open. Full Cozy validation, exactly one independent full Phase review,
required closure ledger/status synchronization, and a distinct Phase release
commit remain incomplete.

Phase 37 is IN PROGRESS. LOGIC37-01 through LOGIC37-05 are COMPLETE;
LOGIC37-06 is IN PROGRESS. No full Phase validation or Phase review,
publication, push, downstream consumer acceptance, Phase release commit,
rendering, PPTX or MP4 delivery, external article-repository mutation, or
Phase 38 work is claimed.
