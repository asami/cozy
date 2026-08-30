# Phase 41: Explanation Structure Review HTML

Status: PLANNED; NOT STARTED

Plan date: 2026-08-29

Dependencies:

- the closed Phase 36 Visual Page and Phase 37 Explanation Composition
  contracts; and
- a deterministic repository-controlled accepted Visual Page fixture for
  representative acceptance. Article 8 runtime use is an optional
  post-Phase-42 operational follow-up, not a Phase 41 dependency.

This plan does not activate Phase 41 or alter the active Phase 38 and planned
Phase 39, Phase 39.1, Phase 40, or Phase 40.1 boundaries.

## Goal

Generate one deterministic, self-contained review HTML that makes the complete
presentation explanation structure understandable at a glance:

- the page-sequence narrative expressed by the selected Explanation Pattern,
  ordered Explanation Steps, and Step-to-Page projection;
- each page's Logical Pattern, semantic nodes, and typed Relations; and
- each page's selected Visual Pattern and typed visual parameters.

The HTML is a review projection only. Explanation Composition, Plan,
Projection Map, Visual Page Set, and their catalogs remain the semantic
authorities. The HTML, its CSS layout, and any embedded interaction never
become presentation or PowerPoint authoring inputs.

## Proposed command surface

The design stage will freeze the exact grammar around this provisional shape:

```text
cozy media explanation preview <plan> \
  --composition <composition> \
  --projection-map <projection-map> \
  --explanation-catalog <explanation-catalog> \
  --presentation-catalog <presentation-catalog> \
  --visual-page-set <visual-page-set> \
  --save <review.html>
```

The command is presentation-review-specific and must not require Storyboard,
video rendering, PPTX generation, or a running Web service. Exact source and
asset bindings continue to follow the accepted Phase 37 direct-file contract.

## Stages

### PREVIEW41-01: Review Contract and Information Architecture

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy explanation-review design and specification
- Update rule: complete only when input authority, page composition, review
  artifact status, command grammar, diagnostics, and identity boundaries are
  accepted.

- Define an overview region for Subject Pattern, Explanation Pattern, ordered
  Steps, Step-to-Page mappings, and input-currentness state.
- Define one compact card per page containing semantic role, primary claims,
  Logical Pattern, typed Relations, Visual Pattern, and parameters.
- Define an inspectable page-detail region for nodes, sources, assets,
  Relations, parameters, and identities without obscuring the overview.
- Keep HTML layout, CSS classes, inline SVG, and interaction below the Visual
  Pattern boundary and outside every semantic identity.
- Decide the versioned preview-renderer/profile binding needed to display a
  Visual Pattern schematically without leaking coordinates into Visual Page
  or presentation catalogs.

### PREVIEW41-02: Validated Integrated Review Model

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy explanation-preview normalization
- Update rule: complete only when the integrated review model and its
  diagnostics have focused Executable Specification evidence.

- Reuse accepted Phase 36/37 parsers and validators rather than defining a
  parallel explanation or Visual Page grammar.
- Join ordered Plan Steps, presentation Step mappings, and Visual Pages by
  their explicit identifiers.
- Preserve Explanation Pattern, semantic role, claims, sources, assets,
  Logical Pattern, nodes, typed Relations, Visual Pattern, typed parameters,
  and all consumed identities without semantic inference.
- Reject duplicate or missing Step/Page mappings, unknown Page IDs, catalog or
  identity mismatch, incompatible patterns, unsafe resources, and stale
  inputs with structured diagnostics.

### PREVIEW41-03: Self-Contained HTML Projection

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy deterministic review renderer
- Update rule: complete only when overview, page cards, page details,
  accessibility, deterministic bytes, and atomic-output cases pass.

- Generate one UTF-8 HTML document with embedded CSS, optional embedded
  JavaScript, and inline SVG only; do not depend on a CDN, external font,
  remote script, or Web server.
- Show the explanation flow and Step-to-Page mapping before page details so
  the complete sequence is visible without interaction.
- Render each supported Visual Pattern as a schematic review projection while
  showing its exact identity and parameters.
- Retain useful static content when JavaScript is unavailable and make the
  overview printable without becoming a print-delivery contract.
- Write output atomically and prove identical accepted inputs produce
  byte-identical HTML.

### PREVIEW41-04: Receipt, Currentness, and Workflow Integration

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy preview evidence and command integration
- Update rule: complete only when the receipt, stale-input rejection, help,
  and coexistence contracts pass.

- Define `cozy.explanation-preview.v1` as a generated-review receipt binding
  the exact Composition, Plan, Projection Map, Explanation Catalog,
  Presentation Catalog, Visual Page Set, preview renderer/profile, and HTML
  identities.
- Reject a changed, missing, unsafe, or identity-mismatched input instead of
  reporting an old HTML as current.
- Add discoverable command help and deterministic success/error output.
- Preserve the existing `cozy media visual-page preview` fast per-page
  semantic preview unchanged; the new command is its multi-page explanation
  review consumer, not a replacement.

### PREVIEW41-05: Representative Driver Acceptance and Closure

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy integrated-preview acceptance
- Update rule: complete only when representative structure review, focused and
  full validation, independent review, and Phase closure pass.

- Cover `problem-solution`, `product-overview`, and at least one Step mapped to
  multiple Pages.
- Verify visually and structurally that explanation order, page order,
  semantic roles, Logical Patterns, typed Relations, Visual Patterns, and
  parameters can be understood together.
- Use a deterministic repository-controlled accepted Visual Page fixture as
  the representative driver and verify page identity/order agreement without
  making any PDF or internal PPTX the semantic source. An Article 8 runtime
  driver may be exercised only as a separate post-Phase-42 operational
  follow-up.
- Run focused and full serialized Cozy validation and complete independent
  Phase review before closure.

## Exclusions

- Automatic subject analysis, Explanation Pattern selection, claim creation,
  Logical Pattern generation, Relation inference, or Visual Pattern selection.
- Editing Composition, Plan, Projection Map, Visual Page Set, or catalogs from
  the review HTML.
- Replacing presentation renderer output, PPTX/PDF review, video review, or
  semantic/visual/audiovisual approval.
- Treating PDF or PPTX as a semantic authority; both remain derived delivery
  or review artifacts.
- External Web hosting, site registration, deployment, upload, or publication.
- Introducing coordinates, fonts, colors, PowerPoint Shapes, CSS selectors, or
  renderer object IDs into Presentation Semantics IR.
- Requiring Phase 40 to expose its internal PPTX as a public artifact.

## Completion Criteria

Phase 41 completes only when one accepted command generates a deterministic,
self-contained HTML in which a reviewer can inspect the explanation flow,
Step-to-Page mapping, per-page logical semantics, and per-page visual pattern
together; every consumed identity is bound to a currentness receipt; invalid
or stale inputs fail closed; the existing per-page preview remains compatible;
the repository-controlled Visual Page fixture passes; full Cozy validation
succeeds; and independent Phase review closes all Current Boundary Blockers.
Any Article 8 runtime acceptance remains optional post-Phase-42 follow-up.

## Structural Phase Plan Gate

State: PROCEED

- planning demand: protected public command, receipt, and review-artifact
  contract
- recommended parent profile: `gpt-5.6-terra / xhigh`
- implementation profile target: use `gpt-5.6-luna / xhigh` for bounded model,
  renderer, and Executable Specification work; reserve Terra xhigh for the
  public-contract kernel and final independent review
- estimate: 6–8 hours
- split disposition: keep one Phase because command grammar, integrated model,
  renderer, currentness receipt, and representative acceptance form one
  cohesive review-feature closure

## References

- `docs/phase/phase-41-checklist.md`
- `docs/phase/phase-36.md`
- `docs/phase/phase-37.md`
- `docs/phase/phase-40.md`
- `docs/phase/phase-40.1.md`
- `docs/design/visual-page.md`
- `docs/spec/visual-page.md`
- `docs/design/explanation-composition.md`
- `docs/spec/explanation-composition.md`
- `docs/journal/2026/08/2026-08-29-explanation-structure-review-html.md`
