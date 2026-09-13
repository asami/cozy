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

Required internal `pageHeading` separates generic screen purpose in `h1`
from the exact authored Summary title in HTML `title` and the 12px kicker.
The sample header and workspace grid remain intact. This still-unreleased
generic presentation extension changes no domain authoring schema, public
CLI/API, or authored identity and adds no fallback or title inference.

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

The selected semantic explanation is a single renderer-owned desktop-reference 16:9
panel. It renders precisely its admitted heading, message, and emphasis.
Aspect ratio is presentation only: the Summary model has no coordinate,
dimension, slide-layout, or physical-layout field.

An explicit first `Overview` unit additionally identifies the Core Root. Its
single slide separates selected Root/direct-child containment from Root Flow
and Root local Structure. Containment renders the admitted Step-reference
order and has no arrow. The admitted diagram is partitioned by typed kinds;
each partition preserves author order, edge direction and exact provenance.
It adds no graph record. Caller Step/Flow/Node/Relation category wording names
the regions. Overview-only styles fit these regions inside the existing 16:9
slide, with the original workspace, colors, native selection and inspector.
Ordinary slides without the explicit role stay unchanged. Complete top-level
scope is an admission contract, not renderer inference or all-Core coverage.

Its primary evidence panel has exactly three readable sections: localized
Step labels, retained point texts, and authored omitted/condensed Document
content labels resolved by exact kind/reference followed by its rationale. It does
not repeat the unit heading or ID at the top. A collapsed native audit inside
the selected evidence panel retains the selection's exact five-category
`coreRefs`, every retained point with its own exact references, optional
authored diagram records, and each omission's full target content plus exact kind/reference/disposition/
rationale. Diagram items carry their admitted identity, kind, and Core ref.
Relation and Flow-transition edges carry their admitted identity, kind, ref,
and direction. A Relation reads only its exact Core Relation; a Flow edge
reads only its exact owning Core Flow transition. Declared `inverse` reverses
that source reading and does not create an edge. No containment, proximity,
traversal, order, or inferred diagram element participates. A unit without a
diagram renders the vocabulary-owned empty-diagram message.

Diagram marks follow Document confirmation, not a new diagram language:
Structure Relation `→`, direct-child Flow transition `⇢`. Both arrows retain
their localized type wording and show the caller-owned source category, so
kind is readable without relying on glyph/color alone. Original Core type,
from/to, Flow owner and direction-adjusted displayed from/to are retained as
exact attributes in slide and audit. A private projection helper reads only
the exact typed Relation or owning Flow transition; it never derives an edge
from containment, wording or diagram item order. Explicit Summary selection is
the scope of structural inheritance, not a claim of complete Core coverage.
The same marks also accompany localized diagram-region headings in both
overview and ordinary slides. Ordinary diagrams separate Step/Flow records
from Node/Relation records instead of placing different semantic scopes in one
row. Partition order follows the first authored item kind, and each partition
retains its item/edge order and exact provenance. Single-kind selection renders
only that region. A heading mark indicates scope, never another graph edge;
containment receives neither arrow. This bounded diagram grouping supersedes
the earlier ordinary-diagram layout-preservation constraint only; author text,
16:9 dimensions, colors, native selection and inspector remain intact.
Accepted workspace, colors, 16:9 dimensions, author text, primary inspector and
complete audit remain intact; no generalized framework or schema is introduced.
The named aside restores an outer `h2` from `sourcesHeading`, matching its
accessible `aria-label`. Its first primary `h3` instead uses the existing
`sourceCategories("steps")`, avoiding repeated outer wording. Retained and
omission primary lists are bullets in authored order; exactly three primary
sections still precede collapsed audit.

The exact Document target lookup carries a compact label and full content.
Paragraphs use the directly owning Section heading as their primary label;
list items use that heading only above the existing 120-character long-target
threshold. Other targets keep exact existing labels. No authored prose is
shortened, inferred, or clipped: full target content and rationale live in
audit, while primary shows the label and authored rationale paragraph.
Disposition remains exact in audit. Accepted Summary slide text,
diagram selection/edges/directions, and 16:9 CSS are untouched; only the
prescribed edge-kind marks and provenance attributes extend diagram markup.

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
HTML. The desktop sample layout is the acceptance reference for this pass;
responsive behavior is not an acceptance priority.

The returned SHA-256 identity is calculated from the deterministic document
with only every self-disclosing output-identity value normalized to empty.
The final disclosure and `Rendered.identity` therefore match without a
recursive hash dependency.

## Endpoint and tag refinement

Selected node owners supply exact Logical Pattern tags using caller-owned
wording. Diagram items stay unique and in authored DOM order; the renderer
does not select a first-item emphasis. Inline SVG connects the exact displayed
endpoint elements and uses separate lanes for explicit parallel edges. Native
selection redraws geometry after revealing the chosen slide. Forward/inverse
type wording is explicit vocabulary, shared by the slide and audit; no label
is grammatically inferred. These corrections supersede prior ordinary-layout
preservation only, without changing Core or Summary admission semantics.

## Non-goals

An admitted optional `focusItem` supplies exact diagram-local semantic
emphasis. The renderer decorates only that item with key styling, an explicit
focus attribute and existing caller-owned emphasis wording, also preserved in
audit. Typed partitioning and reverse readings never change its identity.
Omitted focus preserves the no-automatic-emphasis behavior. This bounded
follow-up consumes the paired v2 selection extension, not a renderer-side
guess or physical-layout schema.

This renderer changes no v1 model, v2 loader, sibling Document renderer,
export route, CLI, HTTP/SPI surface, locale resource, Article 9 source, Core
model, or author-controlled physical-layout representation. It has no output
route and adds no production vocabulary.
