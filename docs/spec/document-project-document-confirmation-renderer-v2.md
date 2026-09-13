# Document Confirmation Renderer v2 Specification

Status: normative for Phase 58.2, Step P582-03, Slice P582-03A

This specification defines the implementation-facing contract for the
package-private v2 Document confirmation projection.

## Input and vocabulary

`CozyDocumentConfirmationProjectionV2.render` MUST accept only an admitted
`CozyDocumentDescriptionV2.ValidatedDocument` and a typed caller-supplied
`Vocabulary`, and MUST return `Rendered(html, identity)`.

`Vocabulary` MUST contain:

- all generic chrome fields used by the rendered workspace;
- a Logical Pattern wording map;
- a node-role wording map;
- a Relation-type wording map; and
- a Flow-type wording map.

For every type used by the admitted Core, its corresponding map MUST contain
one nonblank trimmed wording. Every chrome field MUST be nonblank and trimmed.
Missing or unusable wording MUST raise a deterministic `ProjectionFault`. The
projection MUST NOT invent English wording, expose an ID as a substitute for a
missing generic wording, infer a label, consult a locale resource, or contain
Article-specific production wording. Step and node visible labels MUST come
only from the v2 Document description.

The required generic `Chrome.pageHeading` supplies the screen `h1`, distinct
from the exact authored Document title in the HTML `title` and 12px kicker.
The article's outer `h2` MUST use the exact localized root Step label. This is
an internal, still-unreleased presentation-vocabulary extension, not an
authoring-model, semantic schema, or public CLI/API change; no fallback or
title shortening is permitted.

## Required HTML behavior

The output MUST be one deterministic self-contained HTML document with inline
CSS and inline script. It MUST have a page heading plus named accessible
regions for containment, Flow, Structure, and prose. No external resource,
network activity, fixed-coordinate canvas, or pointer-only control is
allowed.

The containment region MUST recursively represent admitted Steps with native
`button` controls. Each containing `ul` MUST contain exactly one `li` per
Step, with one indentation level per Core parent. Each button MUST expose the
caller-supplied localized Logical Pattern wording in small text and the exact
admitted localized Step label in bold text. Its stable Step ID MUST remain in
typed attributes or title rather than a default visible code row. Buttons use
`aria-pressed` and programmatically associate themselves with the selected
Flow, Structure, and prose regions. Keyboard activation follows the native
button contract; a visible focus style is required.

The selected Structure region MUST precede the Flow region in the Core panel.
Its primary view MUST contain only exact typed Relation concept-arrow-concept
diagrams and unconnected localized node labels. Raw IDs, Logical Pattern,
roles, and source inventories MUST remain in a collapsed native audit inside
Structure. The selected Flow region MUST be an initially collapsed native
details after Structure and contain exactly the selected Step's direct-child
typed transitions and their Flow-type wording. The regions MUST stay separate
from each other and from recursive containment.

Local Structure Relation arrows MUST use the solid `→` mark; direct-child
Flow transitions MUST use the distinct `⇢` mark. Both marks MUST retain their
caller-supplied type wording and exact typed source attributes. The mark is a
presentation of edge kind, not a new relation type or an inferred edge. Summary
confirmation MUST use the same marks for its explicitly adopted typed edges.

The prose region MUST recursively render all admitted Section and Block forms.
Each Section wrapper renders its own heading and blocks, then closes before
its recursive child Sections, which follow as siblings. Section wrappers MUST
not receive visual background or border highlights; block and List Item
highlights remain the category-preserving intersection of exact memberships.
Every Section, Block, and List Item MUST carry its stable typed identity and
its exact `data-core-steps`, `data-core-claims`, `data-core-nodes`,
`data-core-relations`, and `data-core-flows` membership. Selected highlighting
MUST be the category-preserving intersection of those attributes and the
selected Step's own ID, claims, nodes, Relations, and Flow ID. It MUST NOT be
derived from content text, order, proximity, or parentage.

The primary view MUST visibly state complete coverage, current sources,
admitted state, and no unresolved references. Core, Document, and output
identities MUST be secondary disclosure. The `Rendered.identity` and the
disclosed output identity are SHA-256 values calculated from deterministic
HTML with the self-disclosing output-identity value normalized to empty.

Visible ordinary prose reference chips MUST show only exact Step and Relation
identities; all five typed membership categories remain in the exact
`data-core-*` attributes. LogicalStructure blocks MUST be initially collapsed
native details using the localized logical-structure reference wording and
the exact stable identity/reference. All author-controlled text and
attributes MUST be HTML escaped. Identical typed inputs MUST produce
byte-identical HTML and the same identity. The desktop sample layout is the
acceptance reference for this pass; keyboard focus and selection semantics
remain mandatory.

The sample header, three-slot workspace column widths and gaps, heading fonts,
and reference-chip fonts MUST remain intact. The desktop three-slot placement
remains reference-shaped, while the left Core panel and decorative binding are
independent of article length; the binding MUST have a 320px minimum
presentation height and the containment tree MUST use 6px vertical item
whitespace. At desktop screen widths of at least 851px, the containment Core
panel MUST remain sticky at 16px from the viewport top with a maximum height of
`calc(100vh - 32px)` and bounded vertical scrolling, while Document blocks and
List Items MUST use a 16px scroll margin. This persistent desktop Core-panel
navigation MUST NOT truncate the full natural-height article or create a second
fixed-height reading pane. The screen-only rule MUST leave print and narrow
fallback behavior untouched. No other sticky controls, responsive redesign, or
claim of complete reference equality is introduced. Tree button bold labels have no added top margin. Example and Note
blocks use ordinary article text, normal
foreground, transparent background, and transparent left border except for
exact selection highlighting. LogicalStructure keeps its native initially
collapsed details and full typed audit, with an inline 10px summary and no
filled box or large padding. No authored Section hierarchy, heading, block,
prose, or reference chip may be removed or condensed.

Native activation MUST be the only trigger for reveal: initial root rendering
MUST NOT move the viewport, while click, Enter, and Space use the same native
button activation path. After exact category-preserving highlight updates, the
first highlighted visual Block or List Item in source DOM order MUST be the
candidate destination; aggregate Section wrappers MUST be excluded. A missing
or already wholly visible candidate MUST cause no viewport movement. An
offscreen candidate MUST be revealed with automatic scrolling centered when it
fits within the viewport and aligned to the top when taller than the viewport.
Focus MUST remain on the activating control, disclosures MUST remain otherwise
unchanged, and DOM order MUST only order destinations among already-typed hits,
never derive membership.

## Executable specification coverage

`CozyDocumentConfirmationProjectionV2Spec` MUST use `AnyWordSpec`, adjacent
Given/When/Then clauses, and `should` matchers to prove deterministic identity
and escaping; recursive localized native selection controls; separation of
containment, Flow, and Structure; identity-only Section/Block/List Item
highlight membership; one-`ul`-per-parent containment; selected Structure
before collapsed Flow; flat Section wrappers; collapsed LogicalStructure
blocks; primary status with secondary identities; desktop self-contained
accessibility markup; and rejection of a missing generic term.
It MUST also assert the exact emitted desktop Core-panel CSS and activation-only
inline-script reveal gates, including the visual target selector, viewport
bounds guard, long-target alignment choice, native activation argument, and
unchanged initial-selection call; the emitted script MUST contain no
`.focus(` call.
It MUST distinguish the generic screen heading from authored title/kicker,
prove the exact localized root Step article heading, and preserve full text
and compact native logical-structure disclosure.
Its vocabulary is test-only and is not a Cozy locale resource or Article 9
driver.
