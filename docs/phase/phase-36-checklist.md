# Phase 36 Checklist: Common Visual Page and Cross-Media Presentation Contract

This checklist is the authoritative progress ledger for Phase 36. It is not a
normative behavior contract.

Phase Status: PLANNED

## VIS36-01: Visual Page Design and Specification

Status: NOT STARTED

- [ ] Define Visual Page as the one-screen semantic authority shared by a
      presentation slide and a video Storyboard scene screen.
- [ ] Specify Presentation Semantics IR as a typed one-screen graph containing
      Logical Pattern identity, semantic nodes and Relations, typed parameter
      schemas, page identity, language, assets, and sources.
- [ ] Specify a closed, versioned Relation vocabulary with directionality,
      node-role/cardinality constraints, canonical identity, extension rules,
      and strict unknown-relation rejection.
- [ ] Specify separate Logical Pattern and Visual Pattern catalogs and prove
      that a Visual Pattern is a compatible projection choice rather than the
      semantic authority.
- [ ] Specify restricted Markdown, YAML, and JSON as lossless serializations
      of one normalized Visual Page identity.
- [ ] Specify the authority boundaries among Visual Page,
      Logical/Relation/Visual catalogs, profile/template binding, renderer,
      and generated artifacts.
- [ ] Specify an explicit compatibility or migration path from
      `cozy.slide-ir.v1` without silent inference or field loss.
- [ ] Record the accepted design and specification amendments outside the
      Phase work-ledger layer.

## VIS36-02: Typed Presentation Semantics IR and Pattern Catalogs

Status: NOT STARTED

- [ ] Parse every admitted representation into one typed Visual Page semantic
      graph with representation-independent canonical identity.
- [ ] Resolve versioned Logical Pattern, Relation, and Visual Pattern Catalog
      entries and validate exact required, optional, typed, directional, and
      cardinality-constrained parameters.
- [ ] Provide deterministic validate, inspect, and convert behavior with
      canonical output and structured diagnostics.
- [ ] Prove that `next`, `causes`, and `depends-on` keep distinct semantic
      identities even when projected by an identical arrow-bearing Visual
      Pattern.
- [ ] Prove one logical graph can select two compatible Visual Patterns without
      changing its logical identity or source/provenance binding.
- [ ] Prove admitted representation round trips, stable identity, catalog and
      Relation resolution, and parameter validation through Given/When/Then
      Executable Specifications with appropriate property-based coverage.
- [ ] Reject duplicate identities, unsupported schemas, unknown patterns,
      unknown Relations, incompatible logical/visual pairings, unknown or
      ill-typed parameters, unsafe paths, missing assets, and lossy
      compatibility conversions.

## VIS36-03: Presentation Projection and Business Binding

Status: NOT STARTED

- [ ] Define a versioned business binding from logical Visual Pattern slots to
      physical template/renderer slots without exposing coordinates in Visual
      Page IR.
- [ ] Project ordered Visual Pages into presentation slides through the
      configured presentation renderer.
- [ ] Bind Visual Page, catalog, business binding, template, renderer, and
      asset identities into renderer and review evidence.
- [ ] Verify PPTX slide order, slide images, montage, embedded assets, and
      structural output against the selected Visual Pages.
- [ ] Preserve PPTX and template example slides as generated or design assets,
      never semantic authorities.
- [ ] Verify that coordinates, fonts, colors, PowerPoint Shape kinds, and
      renderer object identifiers occur only in binding/output evidence and
      never in the Logical Pattern or Relation graph.

## VIS36-04: Video Scene Screen Integration

Status: NOT STARTED

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

Status: NOT STARTED

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

Phase 36 remains planned. No checklist item is complete and no implementation,
validation, publication, or downstream consumer mutation is claimed.
