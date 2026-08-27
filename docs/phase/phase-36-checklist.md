# Phase 36 Checklist: Common Visual Page and Cross-Media Presentation Contract

This checklist is the authoritative progress ledger for Phase 36. It is not a
normative behavior contract.

Phase Status: IN PROGRESS

## VIS36-01: Visual Page Design and Specification

Status: DONE

- [x] Define Visual Page as the one-screen semantic authority shared by a
      presentation slide and a video Storyboard scene screen.
- [x] Specify Presentation Semantics IR as a typed one-screen graph containing
      Logical Pattern identity, semantic nodes and Relations, typed parameter
      schemas, page identity, language, assets, and sources.
- [x] Specify a closed, versioned Relation vocabulary with directionality,
      node-role/cardinality constraints, canonical identity, extension rules,
      and strict unknown-relation rejection.
- [x] Specify separate Logical Pattern and Visual Pattern catalogs and prove
      that a Visual Pattern is a compatible projection choice rather than the
      semantic authority.
- [x] Specify restricted Markdown, YAML, and JSON as lossless serializations
      of one normalized Visual Page identity.
- [x] Specify the authority boundaries among Visual Page,
      Logical/Relation/Visual catalogs, profile/template binding, renderer,
      and generated artifacts.
- [x] Specify an explicit compatibility or migration path from
      `cozy.slide-ir.v1` without silent inference or field loss.
- [x] Record the accepted design and specification amendments outside the
      Phase work-ledger layer.

## VIS36-02: Typed Presentation Semantics IR and Pattern Catalogs

Status: DONE

- [x] Parse every admitted representation into one typed Visual Page semantic
      graph with representation-independent canonical identity.
- [x] Resolve versioned Logical Pattern, Relation, and Visual Pattern Catalog
      entries and validate exact required, optional, typed, directional, and
      cardinality-constrained parameters.
- [x] Provide deterministic validate, inspect, and convert behavior with
      canonical output and structured diagnostics.
- [x] Prove that `next`, `causes`, and `depends-on` keep distinct semantic
      identities even when projected by an identical arrow-bearing Visual
      Pattern.
- [x] Prove one logical graph can select two compatible Visual Patterns without
      changing its logical identity or source/provenance binding.
- [x] Prove admitted representation round trips, stable identity, catalog and
      Relation resolution, and parameter validation through Given/When/Then
      Executable Specifications with appropriate property-based coverage.
- [x] Reject duplicate identities, unsupported schemas, unknown patterns,
      unknown Relations, incompatible logical/visual pairings, unknown or
      ill-typed parameters, unsafe paths, missing assets, and lossy
      compatibility conversions.

Acceptance evidence: `P36-02A` (typed Visual Page core) is accepted in commit
`60ec0b0ac4c3fe9c0f97f2440be443d0859eee72`; `P36-02B` (explicit legacy Slide
IR migration contract) is accepted in commit
`e4d76b996c54cf8237c0e2b8fc1d8aa7216ef1e6`.

## VIS36-03: Presentation Projection and Business Binding

Status: IN PROGRESS

- [x] `P36-03B` define and implement a versioned business binding from logical
      Visual Pattern slots to opaque physical template/renderer slots without
      exposing coordinates in Visual Page IR; validate its closed JSON grammar
      against the resolved catalog and expose canonical binding identity.
- [x] Add `P36-03A` Fast Semantic Preview: renderer-independent HTML semantic
      review before presentation-renderer handoff, with optional
      logical-structure PNG output that remains Preview-only and is neither a
      PPT input nor a reuse contract; do not change Visual Page, binding,
      receipt, or visual-acceptance authority.
- [x] `P36-03C` implement the separately closed `visual-page-v1` presentation
      resource, its fixed `--visual-page-set --catalog --binding` renderer
      handoff, and strict `cozy.presentation.render.v2` evidence while leaving
      the untagged `cozy.slide-ir.v1` route unchanged.
- [x] Bind canonical VisualPageSet/catalog/binding identity plus template,
      renderer, page-ordered assets, PPTX, slide images, and montage into v2
      renderer and deterministic review evidence; retain receipt v2 and
      review-state v1 schemas while requiring their explicit existing inputs.
- [x] Verify v2 PPTX slide order, slide images, montage, embedded assets, and
      collision/OOXML structure against the selected Visual Pages.
- [ ] Preserve PPTX and template example slides as generated or design assets,
      never semantic authorities.
- [ ] Verify that coordinates, fonts, colors, PowerPoint Shape kinds, and
      renderer object identifiers occur only in binding/output evidence and
      never in the Logical Pattern or Relation graph.
- [x] P36-03C/P36-03B Step accepted in local acceptance commit
      `4d4d621fdbc1822233d49de6a618708567c02d8a`; final-tree focused validation
      invocation `51002-20260827T073710Z` passed 29/29 with 0 failures across
      3 suites; independent Step review passed with zero findings.

Decision record: `P36-03C-DEC-001` is consumed only for Phase 36 / P36-03C.
At phase base `955601e3cd211900d6f2db3ae0c680a29a7957b2` and the current
P36-03B binding/design/docs accumulator, the user approved this exact route
and evidence grammar and authorized IMPLEMENT. It is not global or downstream
consumer approval.

Acceptance evidence: `P36-03A` is accepted in commit
`a248fade7adaa80757ccd0ee77de46588e3e987e`; focused validation succeeded
10/10 with 0 failures, and sealed lightweight review passed. See the
[`2026-08-27-phase-36-fast-semantic-preview-decision.md`](../journal/2026/08/2026-08-27-phase-36-fast-semantic-preview-decision.md)
decision journal. The remaining VIS36-03 items stay open.

## VIS36-04: Video Scene Screen Integration

Status: IN PROGRESS

`P36-04-DEC-001` authorizes only the exact v2
`{kind,source,catalog,pageId}` screen contract. `P36-04-DEC-001` is consumed.
Focused validation invocation `90284-20260827T085957Z`
(`testOnly cozy.video.CozyVideoStoryboardSpec`) reported 16 succeeded, 0
failed/aborted, SBT/wrapper 0, and the lock released. Independent
`P36-04-REREVIEW-002` is PASS and `CB-P36-04-RR-001` is resolved.
P36-04's implementation/validation record is included in this local acceptance
Step commit. No push, publish, or publication is claimed. VIS36-04 and Phase 36 remain IN PROGRESS because
renderer scene identity binding and evidence identity/proof remain open for
P36-05+. P36-05 implementation, focused validation, and independent Step
re-review passed (`54038-20260827T112424Z` ReviewSpec 12/12;
`54763-20260827T112533Z` accumulator 39/39); its acceptance evidence is
recorded in this local Step commit. VIS36-05 and Phase 36 remain IN PROGRESS;
Phase closure remains open. VIS36-06 and Phase 37 remain NOT STARTED.

- [ ] Allow a Storyboard scene to reference or contain a normalized Visual
      Page as its displayed screen contract.
- [ ] Preserve narration, speaker, duration, silence, transition, animation,
      confirmation/final separation, and audiovisual review in the video
      Storyboard workflow.
- [ ] Bind each rendered scene screen to the exact Visual Page, catalog,
      binding, renderer, and asset identities used to create it.
- [ ] Prove that screen-only changes invalidate visual evidence while
      unchanged narration/timing semantics retain their existing authority and
      diagnostics.
- [ ] Preserve Phase 30 compatibility through explicit migration and
      regression evidence rather than implicit reinterpretation.

## VIS36-05: Media Package Identity and Review Integration

Status: IN PROGRESS (P36-05 focused validation and independent Step re-review
passed; acceptance evidence recorded in this local Step commit; Phase closure
open)

`P36-05-DEC-001` is consumed only for `VIS36-05`. It admits a
Storyboard-v2-only review-evidence/handoff v2 contract with a safe
`storyboardReview.visualPage` binding declaration. The v2 proof must retain
the literal screen reference and the exact VisualPageSet, catalog, logical
page, visual page, selected page-asset, binding, and effective selected video
renderer identities. Any missing, unsafe, unresolved, or changed input fails
closed. Storyboard v1, review-evidence/handoff v1, and legacy video projects
remain unchanged. The admitted v2 implementation and executable specifications
are delivered; focused validation and independent Step re-review passed
(`54038-20260827T112424Z` ReviewSpec 12/12;
`54763-20260827T112533Z` accumulator 39/39); acceptance evidence is recorded
in this local Step commit. VIS36-05 and Phase 36 remain IN PROGRESS; Phase
closure remains open. VIS36-06 and Phase 37 remain NOT STARTED.

- [ ] Admit Visual Page and catalog/binding resources through explicit Media
      Package identities and safe descriptor-relative paths.
- [ ] Bind normalized Visual Page identity into presentation and video visual
      receipts, review manifests, and alignment state.
- [ ] Reject stale outputs when a Visual Page, catalog, parameter set, binding,
      template, renderer, article, infographic, or selected asset changes.
- [ ] Preserve separate semantic, visual, and audiovisual acceptance states;
      deterministic build verification must not self-approve meaning or visual
      quality.

## VIS36-06: Cross-Media Acceptance and Closure

Status: NOT STARTED

- [ ] Migrate or scaffold one representative article-summary presentation to
      the Visual Page path without changing accepted article meaning.
- [ ] Use at least one identical normalized Visual Page through a generated
      presentation slide and a video Storyboard scene screen.
- [ ] Build and verify the representative PPTX, slide images, scene-screen
      evidence, manifests, receipts, and review state through Cozy-owned
      orchestration.
- [ ] Complete visual inspection for text, assets, layout, clipping, and
      cross-media consistency while keeping semantic acceptance separate from
      renderer verification.
- [ ] Run focused and full Cozy validation, independent review, and final
      ledger synchronization before changing Phase 36 from planned/open.

Phase 36 remains IN PROGRESS; VIS36-01 and VIS36-02 are DONE; VIS36-03 is IN
PROGRESS because its remaining checklist items stay open, while VIS36-04 is IN
PROGRESS after its P36-04 implementation record. `P36-04-DEC-001` is consumed;
focused validation invocation `90284-20260827T085957Z`
(`testOnly cozy.video.CozyVideoStoryboardSpec`) reported 16 succeeded, 0
failed/aborted, SBT/wrapper 0, and the lock released; independent
`P36-04-REREVIEW-002` is PASS and `CB-P36-04-RR-001` is resolved.
P36-04's implementation/validation record is included in this local acceptance
Step commit. No push, publish, or publication is claimed. VIS36-04 and Phase 36 remain IN PROGRESS because
renderer scene identity binding and evidence identity/proof remain open for
P36-05+. The
P36-03B/P36-03C Step is accepted in local acceptance commit
`4d4d621fdbc1822233d49de6a618708567c02d8a` after final-tree focused validation
invocation `51002-20260827T073710Z` passed 29/29 with 0 failures across 3 suites
and independent Step review passed with zero findings; P36-05 focused
validation and independent Step re-review passed (`54038-20260827T112424Z`
ReviewSpec 12/12; `54763-20260827T112533Z` accumulator 39/39); acceptance
evidence is recorded in this local Step commit. VIS36-05 and Phase 36 remain
IN PROGRESS; Phase closure remains open. VIS36-06 and Phase 37 remain NOT STARTED. No
publication or downstream consumer mutation is claimed.
