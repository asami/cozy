# Document Project Document and Summary Description v2 Specification

Status: normative for Phase 58.2, Step P582-02, Slice P582-02A

This specification freezes the strict source contract for
`cozy.document-description.v2` and `cozy.summary-description.v2`. It is
paired with the v2 design document. Phase 58.1 v1 sources and behavior remain
unchanged.

## 1. Common admission and scalar rules

`core.yaml`, `document.yaml`, and `summary.yaml` MUST be direct regular,
non-symlink files with those exact basenames. The Document and Summary parent
directory basename MUST exactly equal the declared BCP-47 locale
`[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*`. Locale is never inferred.

Sources MUST be strict UTF-8. YAML duplicate mapping keys, anchors, aliases,
and explicit tags MUST reject before normalization. All closed objects MUST
contain each required field exactly once, contain no unknown field, and use
the declared scalar type. Stable IDs are nonempty ASCII values matching
`[A-Za-z0-9][A-Za-z0-9._-]*`. Wording is a UTF-8 string that is nonempty after
trimming. Identities match `sha256:<64 lowercase hexadecimal characters>` and
are SHA-256 of original admitted bytes only.

## 2. Document Description v2

The root MUST contain exactly `schema`, `id`, `core`, `locale`, `document`,
and `labels`; `schema` MUST equal `cozy.document-description.v2`. `core` is
exactly `{ id, identity }` and MUST bind the current directly admitted Core.
`document` retains all v1 closed recursive section/block/list-item shapes,
typed exact `coreRefs`, and complete Core coverage without adaptation.

`labels` MUST contain exactly `steps` and `nodes`. Each `steps` value MUST be
exactly `{ stepRef, text }`; each `nodes` value MUST be exactly
`{ nodeRef, text }`. The reference MUST resolve to the indicated Core type,
text MUST be nonempty and trimmed, references MUST be unique within type, and
each array MUST contain exactly one record for every admitted Core Step or
Node respectively. Claims, generic vocabulary, dynamic keys, and layout
values are not label forms.

## 3. Summary Description v2

The root MUST contain exactly `schema`, `id`, `core`, `document`, `locale`,
and `summary`; `schema` MUST equal `cozy.summary-description.v2`. `core` and
`document` are exactly `{ id, identity }` and MUST bind the directly admitted
v2 Core and v2 Document raw bytes and IDs. `summary` is exactly `{ title,
units }`.

Each unit MUST contain exactly `id`, `heading`, `message`, `emphasis`,
`coreRefs`, `navigationLabel`, `retainedPoints`, and `omissions`, plus optional
`diagram`. Unit IDs are unique and ordered. `heading`, `message`, and
`navigationLabel` are nonempty trimmed wording. `emphasis` is exactly
`primary`, `supporting`, or `conclusion`; `coreRefs` is the v1 exact
References object and contains at least one resolving value.

`retainedPoints` is a nonempty ordered array. Each point is exactly
`{ id, text, coreRefs }`; IDs are unique within its unit, text is nonempty and
trimmed, and coreRefs is exact, resolving, and nonempty.

If present, `diagram` is exactly `{ items, edges }`. A DiagramItem is exactly
`{ id, kind, ref }`, where kind is `step` or `node` and ref resolves in that
exact Core type. A DiagramEdge is exactly `{ id, kind, ref, direction }`,
where kind is `relation` or `flow-transition`, ref resolves to that exact
Core Relation or transition, and direction is `forward` or `inverse`.
Diagram item and edge identities MUST not duplicate within their diagram.

The selected typed item set MUST represent both source endpoints in declared
direction: Relation endpoints are Nodes and Flow-transition endpoints are
Steps. `inverse` uses the reverse of the Core source reading; it does not
create or mutate an edge. A Relation edge MUST occur in the unit
`coreRefs.relations`. A Flow-transition edge MUST have its owner Flow in the
unit `coreRefs.flows`.

`omissions` is a nonempty ordered array. Each Omission is exactly
`{ id, documentKind, documentRef, disposition, rationale }`. IDs are unique
within the unit. documentKind is exactly `section`, `block`, or `list-item`;
documentRef MUST resolve to that kind in the bound v2 Document.
disposition is exactly `omitted` or `condensed`; rationale is nonempty trimmed
wording.

## 4. Deterministic rejection

Before a v2 value reaches a consumer, admission MUST reject malformed or
lossy source; unsafe paths; stale Core or Document identity; unknown, missing,
duplicate, or wrongly typed fields; invalid scalar vocabulary; duplicate
identities; unresolved or mistyped Core references; incomplete Document Core
coverage; missing or duplicate labels; missing retained points or omissions;
unresolved, ungrounded, endpoint-mismatched, or invalid-direction diagram
edges; and invalid or missing omission targets. The loader MUST NOT infer
labels, edges, points, omissions, generic vocabulary, or physical-layout
values.

Coordinates, dimensions, CSS, HTML, fonts, pages, and pagination are not
admitted fields at any v2 schema level. This specification authorizes neither
renderer nor CLI changes, locale-resource work, Article 9 v2 driver changes,
or modifications to the completed v1 implementation.
