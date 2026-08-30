# Phase 41 Checklist: Explanation Structure Review HTML

This checklist is the authoritative progress ledger for Phase 41. It is not a
normative behavior contract.

Phase Status: COMPLETE

## PREVIEW41-01: Review Contract and Information Architecture

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 41
- Update rule: Update this block from the checklist state below.

- [x] Specify the exact input authority, command grammar, output boundary,
      failure contract, and `cozy.explanation-preview.v1` receipt role.
- [x] Specify the overview, page-card, and page-detail information structure.
- [x] Keep HTML/CSS/inline-SVG/interaction data outside semantic identities.
- [x] Define the preview renderer/profile binding for schematic Visual Pattern
      display without adding physical layout vocabulary to Visual Page IR.

## PREVIEW41-02: Validated Integrated Review Model

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 41
- Update rule: Update this block from the checklist state below.

- [x] Reuse the accepted Composition, Plan, Projection Map, Visual Page Set,
      and catalog parsers and validators.
- [x] Join ordered Steps, presentation mappings, and Pages by explicit IDs.
- [x] Preserve claims, sources, assets, Logical Patterns, nodes, typed
      Relations, Visual Patterns, parameters, and consumed identities.
- [x] Reject missing/duplicate mappings, unknown Pages, incompatibility,
      unsafe resources, identity mismatch, and stale inputs diagnostically.

## PREVIEW41-03: Self-Contained HTML Projection

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 41
- Update rule: Update this block from the checklist state below.

- [x] Generate one self-contained UTF-8 HTML with embedded CSS, optional
      embedded JavaScript, and inline SVG only.
- [x] Show explanation flow and Step-to-Page mapping without interaction.
- [x] Show every page's semantic role, Logical Pattern, Relations, Visual
      Pattern, parameters, and available identity/currentness state.
- [x] Preserve useful static and printable content when JavaScript is disabled.
- [x] Prove atomic output and byte-identical repeat generation.

## PREVIEW41-04: Receipt, Currentness, and Workflow Integration

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 41
- Update rule: Update this block from the checklist state below.

- [x] Bind every semantic input, preview renderer/profile, and generated HTML
      identity in the preview receipt.
- [x] Reject old output after any bound input changes.
- [x] Add command help and deterministic success/error reporting.
- [x] Preserve `cozy media visual-page preview` behavior and schemas unchanged.

## PREVIEW41-05: Representative Driver Acceptance and Closure

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 41
- Update rule: This block is complete when its final validation, review, and
  local Phase-release commit are recorded below.

- [x] Cover `problem-solution`, `product-overview`, and one Step-to-multiple-
      Pages projection.
- [x] Confirm that flow, page mapping, logical semantics, and visual structure
      are understandable together in the default overview.
- [x] Use a deterministic repository-controlled accepted Visual Page fixture as
      the representative driver and verify page identity/order agreement
      without treating PDF/PPTX as authority. Article 8 runtime acceptance is
      optional post-Phase-42 operational follow-up only.
- [x] Complete the mandatory independent Phase review and two bounded focused
      closure reviews; `CPB-P41-001` and `CPB-P41-002` are closed.
- [x] Run full serialized Cozy validation: invocation
      `91007-20260830T053008Z`, `sbt --batch test`, 1,597 succeeded, 0 failed,
      121 suites, SBT/wrapper exit 0, lock released.
- [x] Create the local Phase-release commit and synchronize Strategy, Phase,
      and checklist ledgers; no push, publish, deployment, or downstream
      consumer acceptance is claimed.

Phase 41 is COMPLETE. The local closure records the final full validation, one
mandatory Phase review, two focused closure reviews, and the separate open
Hygiene record `HYG-P41-001`; no publication, deployment, push, or downstream
consumer acceptance is claimed.
