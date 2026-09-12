# Document Project Summary Confirmation Renderer v2 Design

Status: normative for Phase 58.2, Step P582-04, Slice P582-04A

This design defines the bounded presentation seam for an admitted v2 Summary
confirmation workspace. It consumes a strict
`CozyDocumentDescriptionV2.ValidatedSummary`; it neither loads, repairs,
mutates, converts, nor adapts Summary, Document, Core, or a v1 renderer.

## Authority and vocabulary seam

The Summary owns title, unit order, headings, messages, emphasis,
navigation labels, retained-point wording and references, diagram selection,
omission rationale, and omission disposition. Project Step and node visible
labels come only from the nested admitted v2 Document. Where no localized
Document label exists, Core identities remain visible as exact stable IDs; an
ID is never made into a synthesized project label.

`CozySummaryConfirmationProjectionV2.Vocabulary` owns only generic review
chrome and controlled generic wording: source categories, diagram item
categories, Relation and Flow types, Document target kinds, omission
dispositions, and displayed directions. Every rendered chrome field and
generic term is required to be nonblank and trimmed. Missing wording raises a
deterministic `ProjectionFault`; there is no English fallback, identifier
fallback for generic wording, locale lookup, Article 9 mapping, or semantic
inference. Production vocabulary and the Article 9 driver remain P582-05
authority.

The renderer deterministically rejects a typed admitted Summary with no units
as `SUMMARY_CONFIRMATION_V2_UNITS` before attempting initial selection. This
consumer-side preflight preserves a stable projection failure even when an
upstream typed boundary has admitted an empty selection.

## Workspace projection

The page has a named ordered Summary navigation region containing native unit
buttons. The first admitted Summary unit is initially selected and source
order exactly follows `summary.units`. One `aria-pressed` model controls the
visible semantic panel and evidence panel for that selection; native buttons
retain keyboard activation and CSS provides visible focus.

The selected semantic explanation is a single renderer-owned responsive 16:9
panel. It renders precisely its admitted heading, message, and emphasis.
Aspect ratio is presentation only: the Summary model has no coordinate,
dimension, slide-layout, or physical-layout field.

Its evidence panel renders only the selection's exact `coreRefs`, retained
points with their own exact references, optional authored diagram, and
omissions. Diagram items carry their admitted identity, kind, and Core ref.
Relation and Flow-transition edges carry their admitted identity, kind, ref,
and direction. A Relation reads only its exact Core Relation; a Flow edge
reads only its exact owning Core Flow transition. Declared `inverse` reverses
that source reading and does not create an edge. No containment, proximity,
traversal, order, or inferred diagram element participates. A unit without a
diagram renders the vocabulary-owned empty-diagram message.

The primary status is deliberately selective: it says only that the selected,
explicit Summary sources are current/admitted and have no unresolved selected
references. It neither claims complete coverage nor makes a loader diagnostic.
Core, Document, Summary, and output identities are collapsed secondary
disclosure.

## Deterministic safe artifact

The projection emits one safely escaped self-contained HTML document with
inline CSS and script. The script changes only selected presentation state.
There is no external resource, network activity, canvas, fixed authored
coordinate, or pointer-only control. Equal typed inputs render byte-identical
HTML.

The returned SHA-256 identity is calculated from the deterministic document
with only every self-disclosing output-identity value normalized to empty.
The final disclosure and `Rendered.identity` therefore match without a
recursive hash dependency.

## Non-goals

This renderer changes no v1 model, v2 loader, sibling Document renderer,
export route, CLI, HTTP/SPI surface, locale resource, Article 9 source, Core
model, or author-controlled physical-layout representation. It has no output
route and adds no production vocabulary.
