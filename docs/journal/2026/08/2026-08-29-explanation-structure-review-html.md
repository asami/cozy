# Explanation Structure Review HTML Direction

Date: 2026-08-29

Status: planning rationale; non-normative

## Context

Phase 36 established one-screen Visual Page semantics and the separation among
Logical Pattern, typed Relation graph, Visual Pattern, and renderer binding.
Phase 37 established Explanation Pattern, ordered Explanation Steps, and
independent Step-to-Page projection. The current fast Visual Page preview is a
valuable per-page semantic inspection artifact, but it does not present the
complete explanation flow and all page-level structures as one review surface.

The requested review experience is not another semantic IR and is not a new
slide authority. It is a generated, disposable view that lets a reviewer see
three already accepted layers together:

```text
Explanation Pattern + ordered Steps + Step-to-Page mapping
  -> per-page Logical Pattern + semantic Relations
  -> per-page Visual Pattern + typed parameters
```

## Direction selected

Plan a new `cozy media explanation preview` feature that creates one
deterministic, self-contained HTML document. The default view puts the whole
explanation and page order first, then one compact card per page, with detailed
nodes, Relations, sources, assets, parameters, and identities available below
or through local interaction.

The review HTML consumes and validates the accepted Composition, Plan,
Projection Map, Explanation Catalog, Presentation Catalog, and Visual Page Set
contracts. It must reuse their parsers and validators rather than reinterpret
or infer meaning. Step-to-Page links are explicit joins. Missing, duplicate,
unknown, incompatible, unsafe, identity-mismatched, or stale inputs fail with
structured diagnostics.

## Review artifact boundary

The HTML is review evidence only:

- it is not Composition, Plan, Projection Map, Visual Page Set, Slide IR, or a
  renderer input;
- it cannot edit or write back to any semantic authority;
- its schematic CSS/inline-SVG layout remains below the Visual Pattern
  boundary;
- it does not replace generated slide images, summary-slide PDF review,
  internal PPTX inspection, or video review; and
- it requires no CDN, remote font, external JavaScript, Web service, hosting,
  deployment, or upload.

The generated HTML and every consumed input identity belong in a dedicated
`cozy.explanation-preview.v1` receipt so later input changes make the review
artifact stale. The exact receipt grammar and preview renderer/profile binding
remain design-stage decisions rather than journal authority.

## Relationship to existing previews and delivery

The accepted `cozy media visual-page preview` remains the fast, renderer-
independent per-page semantic preview. The new Explanation Structure Review is
its multi-page, explanation-aware consumer and must not silently change the
existing command or schema.

Phase 40 remains responsible for article and summary-slide PDF media delivery.
The integrated preview is planned separately as Phase 41 so PDF publication
and review-interface concerns do not expand the same closure. The accepted
Phase 40 summary-slide package becomes Phase 41's representative driver; its
page identities and order may be compared with the preview, but its PDF and
internal PPTX remain derived artifacts rather than semantic sources.

## Initial information architecture

The first accepted HTML should contain:

1. an identity/currentness header;
2. an explanation overview with Subject Pattern, Explanation Pattern, ordered
   Steps, and Step-to-Page mappings;
3. an ordered page-card region showing semantic role, main claims, Logical
   Pattern, key Relations, Visual Pattern, and parameters; and
4. page details containing nodes, all Relations, sources, assets, typed
   parameters, and exact identities.

The complete sequence must remain understandable without JavaScript. Local
interaction may support selection or progressive detail but cannot be the only
way to see order or currentness.

## Phase allocation

Phase 41 owns design/specification, integrated review-model construction,
self-contained HTML generation, currentness receipt, coexistence with the
existing per-page preview, representative Phase 40 driver acceptance, focused
and full validation, independent review, and closure.

It is estimated at 6–8 hours. The public command and receipt kernel merits
Terra xhigh planning/review, while bounded model, renderer, and Executable
Specification work should normally use Luna xhigh. The work remains one Phase
because command grammar, joined review model, renderer, receipt, and driver
acceptance form one cohesive feature contract.
