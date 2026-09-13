# Document Confirmation Renderer v2 Design

Status: normative for Phase 58.2, Step P582-03, Slice P582-03A

This design defines the bounded presentation seam for a strict v2 Document
confirmation workspace. It consumes an already admitted
`CozyDocumentDescriptionV2.ValidatedDocument`; it neither loads content nor
adapts the closed v1 Document authority.

## Authority and input seam

`CozyDocumentConfirmationProjectionV2` receives exactly two typed inputs:

1. the admitted v2 Document, which owns the recursive Core, localized
   Document prose, project-specific Step labels, project-specific node labels,
   and exact reference membership; and
2. caller-supplied `Vocabulary`, which owns only generic review chrome and
   wording for Logical Patterns, node roles, Relation types, and Flow types.

`Vocabulary` has no project-specific Step, node, heading, or prose entries.
The renderer obtains every Step and node label exclusively from the admitted
Document description. It requires generic wording for every type actually
present in the admitted Core and every chrome field. A missing, blank, or
unusable term rejects rendering with `ProjectionFault`; no default wording,
identifier-as-label fallback, locale lookup, or semantic inference is
permitted. P582-05 owns the production Cozy locale-resource projection and
the Article 9 driver.

The internal generic chrome includes required `pageHeading`, separating the
screen purpose in `h1` from the exact authored title in HTML `title` and the
12px kicker. The outer article `h2` reads the exact localized root Step label,
not a repeated full title or a synthesized shortened title. The sample's 38px
clamped header, border/gap, and desktop three-slot column widths and gaps remain
reference-shaped; top-aligned columns keep the left Core panel and decorative
binding independent of article length, with the binding's minimum presentation
height set to 320px. Shorter generic screen wording supplies the compact
header without ellipsis.

## Workspace projection

The HTML has four independently named semantic regions:

1. recursive containment navigation, represented with one `li` per Step in
   one `ul` per Core parent and native Step buttons showing small Logical
   Pattern wording above bold localized Step labels;
2. selected local Structure, whose primary view contains only typed Relation
   concept-arrow-concept diagrams and unconnected node labels, with raw IDs,
   pattern, roles, and source inventories in a collapsed native audit;
3. selected direct-child Flow, initially collapsed after Structure and
   populated only from the selected Step's `flow.transitions`; and
4. complete recursive Document prose, including sections, paragraphs, list
   blocks/items, examples, notes, and logical-structure references.

Containment has no Flow meaning. Flow is not constructed from containment.
Structure relations are not projected as Flow.

The two edge kinds share the confirmation-screen reading convention: a local
Structure Relation uses `→`, and a direct-child Flow transition uses `⇢`.
The mark accompanies existing localized type wording, without replacing it;
native Flow disclosure, original sources and workspace placement remain intact.
The same scope marks accompany Structure/Flow region headings. Exact prose
Relation chips and collapsed LogicalStructure reference summaries carry `→`,
making their local-Structure scope visible in the reading pane too. These marks
are labels, not additional edges; containment Step buttons remain unmarked and
all localized wording, typed references and native behavior are preserved.

Readable text tags additionally expose the exact caller-owned pattern in Step
buttons and selected Structure, and exact type wording in edges and prose
references. A pattern tag is classification, not a containment edge.

The native button uses the one
`aria-pressed` selection model consistently and programmatically controls the
selected Flow panel, Structure panel, and prose region. The initial selection
is the admitted root Step; inline script changes only presentation state when
a reviewer activates another native button. Activation is the only reveal
trigger: the initial render never moves the viewport, while click, Enter, and
Space follow the same native button path. After the exact identity highlights
are applied, the first highlighted visual Block or List Item in source DOM
order is the destination candidate; aggregate Section wrappers are excluded.
An absent or already wholly visible candidate causes no movement. An offscreen
candidate is automatically centered when it fits, or aligned to the top when
it is taller than the viewport. Focus remains on the activating control and
disclosures remain unchanged. DOM order orders destinations only among typed
hits; it never derives semantic membership.

Every Document Section, Block, and List Item receives stable element identity
and exact typed `data-core-*` reference attributes. For a selected Step the
renderer builds a typed set containing that Step ID plus its claim, node,
Relation, and Flow IDs. A target is highlighted only when the same reference
category intersects that set. The initial HTML and the selection script use
this identical identity comparison. Text, document position, proximity, and
containment are never matching criteria. Section wrappers render only their
own heading and blocks before closing; recursive children follow as sibling
wrappers. Only Block and List Item wrappers receive the category-preserving
visual highlight, so aggregate Section references cannot wash an entire
reading pane. Ordinary visible prose chips contain only exact Step and
Relation identities, while all five typed memberships stay in `data-core-*`
attributes. LogicalStructure blocks are collapsed native details with
localized wording and exact stable identity and reference.

Ordinary spacing, heading/reference fonts, and full Section hierarchy remain
the sample presentation, with 6px vertical whitespace for containment-tree
items. Tree bold labels add no top margin. Example/Note
blocks use ordinary article text with normal foreground, transparent
background and left border until exact selection highlights them.
LogicalStructure remains native initially collapsed details, but its summary
is inline 10px text without a filled box or large padding. All typed
attributes, reference chips, and full content remain available.

At desktop screen widths of at least 851px, the containment Core panel remains
sticky at 16px from the viewport top with `max-height: calc(100vh - 32px)` and
bounded vertical scrolling, and Document blocks/List Items use a 16px scroll
margin. This is a persistent desktop Core-panel navigation aid, not a fixed
reading pane: the complete Document article remains natural height. The rule is
screen-only, leaving print and narrow fallback behavior unchanged.

## Status, identity, and deterministic output

The normal workspace makes four admitted facts primary: complete Document
coverage, current sources, admitted state, and no unresolved references. They
are direct projections of `ValidatedDocument`, not renderer diagnostics or a
new universal Summary assertion. Core and Document identities, plus the
rendered output identity, live in a collapsed secondary disclosure.

The rendered artifact identity is the SHA-256 identity of the deterministic
HTML with only the self-disclosing output-identity value normalized to empty.
This makes the identity sensitive to every other HTML byte while avoiding an
impossible recursive hash of a document containing its own final hash. The
returned identity and the disclosed output identity are therefore identical.

All author-controlled text and attributes pass through HTML escaping. The page
contains only inline CSS and script: no resource URL, network request, canvas,
or fixed-coordinate presentation is introduced. CSS supplies a visible focus
state. The desktop sample layout is the acceptance reference for this pass;
the article remains full natural-height content, with no sticky controls beyond
the desktop containment Core-panel exception above, no responsive redesign, and
no claim of complete reference equality.

## Non-goals

This renderer does not edit or invoke the v1 projection, Core validation,
description loader, export route, CLI, Article 9 source, or locale resource.
It adds no physical-layout field to the v2 authoring model and provides no
production vocabulary values.
The new required screen-heading field extends only the private,
still-unreleased presentation vocabulary; it changes no authoring model,
semantic schema, public API, or title authority and adds no fallback.
