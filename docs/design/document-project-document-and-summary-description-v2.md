# Document Project Document and Summary Description v2 Design

Status: normative for Phase 58.2, Step P582-02, Slice P582-02A

This design defines the separate, strict `cozy.document-description.v2` and
`cozy.summary-description.v2` authorities. It is paired with
`docs/spec/document-project-document-and-summary-description-v2.md`. The
completed v1 design, specification, model, and projections remain unchanged
and authoritative for their v1 schemas.

## Authority and identity

The Content Core remains the only locale-independent authority for recursive
Steps, claims, local Structure nodes and Relations, and child-Step Flows. A v2
Document is the complete localized prose authority over that Core. A v2
Summary is the deliberate localized selection authority over the admitted v2
Document and Core. Neither v2 source is an HTML, CSS, PDF, slide, or layout
intermediate representation.

All three sources are admitted only as direct regular, non-symlink files:
`core.yaml`, `document.yaml`, and `summary.yaml`. Document and Summary files
are directly below a locale directory whose basename exactly equals their
declared BCP-47 `locale`. Their raw bytes must be strict UTF-8 before YAML is
parsed. Duplicate YAML keys, anchors, aliases, explicit tags, unsafe paths,
and lossy or malformed source reject before a semantic value is returned.

Every authority identity is `sha256:<64 lowercase hexadecimal characters>`
over its original admitted bytes, never parsed or normalized YAML. A v2
Document binds the admitted Core `id` and raw-byte identity. A v2 Summary
binds the admitted v2 Core and the original raw bytes of the admitted v2
Document. No parser or later consumer may repair stale bindings.

## v2 Document Description

The root is closed and contains exactly the v1 fields plus `labels`:

```yaml
schema: cozy.document-description.v2
id: document-id
core: { id: core-id, identity: sha256:<identity> }
locale: ja
document: <v1 closed recursive document>
labels:
  steps: [<StepLabel>]
  nodes: [<NodeLabel>]
```

`document` preserves the v1 recursive Sections, closed block vocabulary,
exact `coreRefs`, globally unique section/block/list-item identities, and full
typed Core coverage. It neither adapts nor loosens v1.

`labels` is closed and has exactly `steps` and `nodes`. A Step label is
exactly `{ stepRef, text }`; a Node label is exactly `{ nodeRef, text }`.
Both references are stable IDs resolving in their exact Core type, and text is
nonempty trimmed localized wording. Each type has unique references and one,
and only one, record for every admitted Core Step or Node. Labels cannot be
dynamic mapping keys, inferred identifier text, claim labels, generic locale
vocabulary, or layout values.

## v2 Summary Description

The root retains the v1 binding and Summary shape, with schema exactly
`cozy.summary-description.v2`. Every unit retains its v1 `id`, `heading`,
`message`, `emphasis`, and exact, nonempty resolving `coreRefs`, and adds:

```yaml
navigationLabel: Localized unit navigation wording
retainedPoints:
  - id: retained-point-id
    text: Localized retained wording
    coreRefs: <nonempty exact References>
diagram: # optional
  items: [<DiagramItem>]
  edges: [<DiagramEdge>]
  focusItem: diagram-item-id # optional explicit semantic emphasis
omissions:
  - id: omission-id
    documentKind: section | block | list-item
    documentRef: exact-v2-document-id
    disposition: omitted | condensed
    rationale: Localized explanation
```

Units retain author order and have unique stable IDs. `navigationLabel`, point
text, and omission rationale are nonempty trimmed wording. Each unit has
nonempty ordered retained points; point IDs are unique within that unit and
each point owns a nonempty resolving exact `coreRefs`. Each unit also has
nonempty ordered omissions with IDs unique within that unit.

An explicitly authored first unit may additionally declare
`overview: { stepRef: <Core Root Step id> }`. A typed `Overview` binds that
semantic scope, not a layout or guessed role. Admission allows at most one,
first only, and requires exact complete Root/direct-child reference coverage,
retained-point reference subsets, and explicit unique diagram items/edges for
Root local Structure and Root Flow. No descendant-local source is admitted in
that overview. Existing units without this field remain ordinary, unchanged
units. No automatic slide, selection, heading or wording is created.

The optional `diagram` is closed with required `items` and `edges`, plus
optional `focusItem`; it is
coordinate-free semantic selection, not layout. A DiagramItem is exactly
`{ id, kind, ref }`, where `kind` is `step` or `node`, and `ref` resolves to
that exact Core type. A DiagramEdge is exactly `{ id, kind, ref, direction }`,
where `kind` is `relation` or `flow-transition`, `ref` resolves to that exact
Core Relation or nested Flow transition, and `direction` is `forward` or
`inverse`.

The optional `focusItem` identifies exactly one selected DiagramItem by its
diagram-local ID. It allows deliberate emphasis without inferring significance
from position, Core node role or graph direction. Admission rejects unselected
IDs, edge IDs and invalid scalar values. Diagrams without it retain no item
emphasis. Partitioning and item reordering must preserve that selection by ID;
only its containing partition can display it. This additive unreleased v2
selection has no coordinate, layout representation or v1 adaptation.

For a forward Relation edge, its Core `from` and `to` Nodes must both be
selected DiagramItems; inverse reverses that reading. For a forward
Flow-transition edge, its `fromStepId` and `toStepId` Steps must both be
selected; inverse reverses that reading. The selected items are typed, so a
Node cannot stand in for a Step. A Relation edge is admitted only when its
exact Relation identity occurs in the unit `coreRefs.relations`; a
Flow-transition edge is admitted only when the Flow that owns the exact
transition occurs in `coreRefs.flows`. No edge, point, label, omission, or
generic vocabulary may be inferred.

Each Omission has exactly `{ id, documentKind, documentRef, disposition,
rationale }`. `documentKind` is one of `section`, `block`, or `list-item`,
and the `documentRef` must resolve to the exact corresponding record in the
bound v2 Document. `disposition` is only `omitted` or `condensed`.

## Consumer boundary and non-goals

The v2 loader fails closed before output or consumer use for any invalid
admission, closed field set, identity, reference, label, diagram, or omission
condition. It performs no label, edge, retained-point, omission, or wording
inference. Core semantics and generic Logical Pattern, role, Relation, Flow,
and chrome wording remain outside these sources.

Coordinates, dimensions, CSS, HTML, fonts, pages, pagination, renderer,
command, locale-resource, and Article 9 v2 driver work are excluded. Future
renderers consume only the fully admitted typed v2 values. This design does
not alter v1 behavior or make a v1 source permissive.
