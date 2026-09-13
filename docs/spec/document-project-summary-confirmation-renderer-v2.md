# Document Project Summary Confirmation Renderer v2 Specification

Status: normative for Phase 58.2, Step P582-04, Slice P582-04A

This specification defines the implementation-facing contract for the
package-private v2 Summary confirmation projection.

## Input and vocabulary

`CozySummaryConfirmationProjectionV2.render` MUST accept exactly an already
admitted `CozyDocumentDescriptionV2.ValidatedSummary` and caller-supplied
typed `Vocabulary`, returning `Rendered(html, identity)`. It MUST NOT load,
mutate, repair, or convert Summary, Document, or Core data, and MUST NOT call
or adapt a v1 renderer.

An admitted Summary with an empty `summary.units` vector MUST be rejected
before rendering with deterministic `ProjectionFault` code
`SUMMARY_CONFIRMATION_V2_UNITS`; it MUST NOT leak a collection-selection
exception.

The Summary is the sole authority for its project-specific title, heading,
message, navigation label, retained wording, ordering, emphasis, diagram
selection, omissions, and omission rationale. Visible Step and node labels
MUST come only from the nested admitted v2 Document. Generic chrome, source
categories, diagram item categories, Relation types, Flow types, Document
target kinds, omission dispositions, and displayed directions MUST come from
the caller vocabulary.

Every actually rendered generic term and every chrome field MUST have one
nonblank trimmed caller-supplied wording. Missing or unusable wording MUST
raise deterministic `ProjectionFault`. The renderer MUST NOT use English
fallbacks, IDs as generic wording, locale resources, Article 9 mappings, or
semantic inference.

The required generic `Chrome.pageHeading` supplies the screen `h1`; the exact
authored Summary title remains in the HTML `title` and 12px kicker. This is an
internal, still-unreleased presentation-vocabulary extension only, not a v2
authoring-model, semantic schema, or public CLI/API change. No fallback,
shortened title, or inferred article identity is allowed.

## Required HTML behavior

The output MUST be one safely escaped deterministic self-contained HTML
document with inline CSS and script. It MUST render a named ordered navigation
region of native unit buttons in exact `summary.units` order. The first unit
MUST be initially selected. One `aria-pressed` model MUST associate that
selection with one visible semantic panel and one visible evidence panel;
native keyboard activation and visible focus are mandatory.

The semantic panel MUST be renderer-owned with the desktop reference
`aspect-ratio:16 / 9`. It MUST render only the selected admitted unit heading,
message, and emphasis. It MUST NOT introduce a second selected slide,
auto-summary, automatic condensation, inferred unit, coordinate, or author
layout field.

An explicitly admitted first overview unit MUST render one selected 16:9
slide with three distinct semantic regions: Root/direct-child containment,
Root child-Step Flow, and Root local Node/Relation Structure. Containment
uses the explicit unit Step-reference order, exact Document labels, and no
arrow; it MUST NOT imply Flow. The authored diagram is partitioned by typed
Step/Flow versus Node/Relation records without adding or reordering records.
Flow uses `⇢`, Structure uses `→`; exact edge provenance remains in the slide
and complete audit. Root scope is explicit in region attributes. Region names
reuse caller-owned Step/Flow/Node/Relation category wording. Overview-specific
CSS MUST not change ordinary unit markup/layout/color or selection behavior.
The overview heading, message, retained points, omissions and navigation label
remain author-owned. Units without explicit overview MUST NOT acquire one.

The primary evidence view MUST contain exactly three sections, in this order:
the localized Step labels from `unit.coreRefs.steps`, retained point texts,
and authored omitted/condensed content resolved to its exact admitted
Document heading or compact target label followed by the authored rationale. It MUST
not repeat the unit heading or ID at the top. A collapsed native audit inside
the selected evidence panel MUST retain the exact five-category Core sources,
each point with its own references, authored diagram item/edge records, and
each omission's full target text and exact kind, reference, disposition, and rationale. An absent
diagram MUST render the vocabulary-owned empty-diagram message.
The named aside MUST render an outer `h2` using `chrome.sourcesHeading`, also
used by its accessible `aria-label`. The first primary `h3` uses
`sourceCategories("steps")`; retained and omission primary lists are bullets
in authored order. Exactly those three primary sections precede collapsed
audit; the outer heading does not count as a fourth section.

A paragraph omission's primary label MUST be its directly owning admitted
Section heading, resolved by the exact target kind/reference. A list-item
target longer than 120 characters likewise uses its directly owning Section
heading; shorter items and other targets retain their exact existing labels.
Full paragraph/item text and authored rationale remain in collapsed audit,
without truncation or semantic condensation. Primary presentation is the
target heading plus the authored rationale as a short paragraph; exact
disposition wording may be audit-only. Selection, typed memberships, accepted
slide text, diagram edges/directions, and 16:9 slide CSS MUST remain unchanged;
only the explicitly prescribed edge-kind marks and provenance presentation
below may extend the diagram markup.

Diagram item output MUST carry admitted identity, kind, and Core ref. Diagram
edge output MUST carry admitted identity, kind, ref, and declared direction.
A Relation edge MUST resolve only through its exact typed Core Relation and a
Flow-transition edge only through its exact owning Core Flow transition.
Containment, graph traversal, proximity, and item order MUST NOT create
diagram edges or items.

An explicitly adopted Relation edge MUST use the same solid `→` mark as
Document local Structure; an adopted Flow-transition edge MUST use the same
`⇢` mark as Document direct-child Flow. Each slide arrow MUST also show its
caller-owned source category and existing type wording. Marks MUST NOT depend
on item order, navigation position, text, or semantic inference, and MUST NOT
be the sole accessible indication of kind.

Every diagram region heading MUST visibly pair its source-category wording
with a scope mark: `⇢` for child-Step Flow and `→` for local Node/Relation
Structure. These heading marks classify the region; they are not graph edges.
Ordinary unit diagrams MUST partition authored Step/Flow-transition records
from Node/Relation records into separately headed regions. Region order follows
the first authored item kind; item and edge order within each partition remains
authored. Single-kind diagrams have only their own region. No unselected item,
edge, implicit Flow, or inferred local Structure may be added. Containment
remains unmarked by either arrow. The bounded partition/heading presentation is
an explicit exception to the diagram-layout preservation clauses above and
below; slide dimensions, colors, author text, selection and inspector remain
unchanged.

Slide and audit edge records MUST expose the exact Core edge type, original
from/to identities, displayed from/to identities after the declared direction,
and owning Core Flow identity for Flow transitions. These are projection
attributes, not authoring fields. Only authored diagram items/edges are shown;
unselected Core edges MUST NOT be added. Forward/inverse presentation MUST
preserve the original typed source and reverse only displayed endpoint order.
Existing unit order, headings/messages, 16:9 dimensions, layout, color tokens,
primary inspector sections and complete audit evidence MUST stay unchanged.

The primary status MUST say only that explicit selected Summary sources are
current/admitted and have no unresolved selected references. It MUST NOT say
complete coverage or imply all-Core coverage. Core, Document, Summary, and
output identities MUST be collapsed secondary disclosure.

The output identity MUST be SHA-256 of deterministic HTML with only the
self-disclosing output-identity value normalized to empty. Returned and
disclosed identities MUST match. The artifact MUST have no external resource,
network action, canvas, fixed authored coordinate, or pointer-only dependency.

## Typed text tags and endpoint connections

Text-bearing tags MUST expose the exact selected Structure pattern and each
selected edge type, alongside the existing scope heading. Pattern wording
comes from the caller's Logical Pattern map; the generic resource reuses its
Document pattern wording for Summary. Tags are not additional graph edges.
Each selected diagram item is rendered once, in authored DOM order, without
automatically emphasizing the first item. Inline SVG connections MUST join
the exact displayed endpoint elements; separate lanes retain parallel edges
and branches. Runtime geometry is renderer-owned and uses only those typed
endpoint attributes, never adjacency or new semantic relationships.

Forward edge labels use caller-owned canonical type wording; inverse labels
MUST use caller-owned inverse type wording in both slide and audit. Missing
inverse wording rejects before rendering. Original Core edge direction and
identities remain unchanged. This bounded correction supersedes the earlier
diagram-layout/type-wording preservation constraints only. Author text,
selection, dimensions and primary inspector stay intact.

## Executable specification coverage

The admitted optional diagram `focusItem` MUST emphasize exactly its selected
DiagramItem in slide and audit, retaining identity through partitioning,
forward/inverse display and authored item reordering. The slide uses a visible
border/background distinction and caller-owned `emphasisHeading` text, so
color alone is not the indication. The focus selection MUST NOT add, reorder
or infer items/edges, or change connections. With no focus selection there is
no emphasized item. Overview and ordinary diagrams follow the same rule.
This explicit authoring selection is the only exception to the no-automatic-
emphasis requirement; it introduces no renderer inference or physical layout.

`CozySummaryConfirmationProjectionV2Spec` MUST use `AnyWordSpec`, adjacent
Given/When/Then clauses, and `should` matchers. It MUST exercise deterministic
self-disclosing identity and escaping; ordered native selection and associated
panels; the one selected 16:9 semantic explanation; exact source, retained,
and omission evidence; authored-only typed Relation and Flow edges including
inverse direction; selective admitted primary status with secondary identities;
desktop self-contained accessibility markup; three-section readable primary
evidence, collapsed exact audit, exact omission labels, and rejection of
missing generic wording. Its fixtures use only strict v2 model data and test-local
vocabulary, never a production locale resource or Article 9 driver.
Coverage MUST prove the distinct generic screen heading, exact authored
title/kicker, outer inspector heading, three-section order, bullet ordering,
and primary/audit separation with exact full paragraph/item wording.
Coverage MUST prove mark/category agreement and exact source/type/endpoint/Flow
owner provenance in both slide and audit, including generated forward/inverse
directions and reordered diagram items without inference or extra edges.
Coverage MUST also prove scope marks on overview and ordinary region headings,
typed partition isolation and authored order under generated item ordering,
single-kind projection, and the absence of relation marks in containment.
