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

## Workspace projection

The HTML has four independently named semantic regions:

1. recursive containment navigation, represented with nested lists and native
   Step buttons;
2. selected direct-child Flow, populated only from the selected Step's
   `flow.transitions`;
3. selected local Structure, populated only from the selected Step's pattern,
   nodes, node roles, and Relations; and
4. complete recursive Document prose, including sections, paragraphs, list
   blocks/items, examples, notes, and logical-structure references.

Containment has no Flow meaning. Flow is not constructed from containment.
Structure relations are not projected as Flow. A native button uses the one
`aria-pressed` selection model consistently and programmatically controls the
selected Flow panel, Structure panel, and prose region. The initial selection
is the admitted root Step; inline script changes only presentation state when
a reviewer activates another native button.

Every Document Section, Block, and List Item receives stable element identity
and exact typed `data-core-*` reference attributes. For a selected Step the
renderer builds a typed set containing that Step ID plus its claim, node,
Relation, and Flow IDs. A target is highlighted only when the same reference
category intersects that set. The initial HTML and the selection script use
this identical identity comparison. Text, document position, proximity, and
containment are never matching criteria.

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
or fixed-coordinate presentation is introduced. CSS supplies a visible
focus state and a desktop grid that reflows to a readable mobile stack.

## Non-goals

This renderer does not edit or invoke the v1 projection, Core validation,
description loader, export route, CLI, Article 9 source, or locale resource.
It adds no physical-layout field to the v2 authoring model and provides no
production vocabulary values.
