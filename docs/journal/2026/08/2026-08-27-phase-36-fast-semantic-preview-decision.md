# Phase 36 Fast Semantic Preview Decision

Date: 2026-08-27

This journal records the developer decision for Phase 36 `P36-03A`. It is a
chronological design handoff, not a normative specification or implementation
claim.

## Decision

Fast Semantic Preview provides a quick, renderer-independent review surface
for already validated Visual Pages before presentation-renderer handoff.

- The base output is an HTML page that exposes the selected ordered semantic
  pages and their logical structure for human review.
- A caller may select an additional logical-structure PNG output. The HTML-only
  and HTML-plus-PNG forms consume the same validated semantic input; the PNG
  does not introduce another Visual Page representation.
- A logical-structure PNG is a Preview-derived review artifact only. It is not
  a PPT input, is not reused by PPT generation, and does not change PPT,
  template, business-binding, renderer, receipt, or visual-acceptance
  contracts.
- The Visual Page and its resolved catalog remain the semantic authority. HTML
  and PNG outputs cannot repair, replace, or infer semantic content.
- Any physical layout details used to draw a preview PNG remain generated
  preview output. They must not be added to the Logical Pattern, Relation
  graph, or Visual Page IR.

## Deferred implementation decisions

Implementation must separately define the command/API shape, output-path and
overwrite policy, preview-generator identity, stale-output handling, and the
focused validation evidence. Reconsidering PNG reuse by the PPT route requires
a later explicit binding/renderer decision; it is outside `P36-03A` as
recorded here.

## Phase handoff

`docs/phase/phase-36.md` and `docs/phase/phase-36-checklist.md` carry the
open `P36-03A` work item. The Phase remains in progress and VIS36-03 remains
not started.
