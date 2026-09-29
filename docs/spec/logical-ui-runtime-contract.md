# Logical UI Runtime Contract v1

Document role: normative behavior contract for the provisional
`cozy.logical-ui-runtime.v1` runtime model. It supplements, and does not alter,
the accepted Logical UI authority in
[`logical-ui-model.md`](logical-ui-model.md).

## Contract and provenance

`CozyLogicalUiRuntime.minimumContract` is the one admitted descriptor:

- schema: `cozy.logical-ui-runtime.v1`;
- version: `1`;
- semantic authority schema: the exact `CozyLogicalUiSemantics.schema` value;
- assurance: `provisional-scenario`; and
- driver: `KnowledgeHubProject/nict-editing-studio-app`, revision
  `48eb43b8eb839137e17ef4c944b3db4a73d0cec3`, path
  `docs/phase/phase-2.md`, scenario `resource-list-detail`.

Every `Model` carries `Metadata(contract, provenance)`. `SourceProvenance`
names nonempty, exactly trimmed opaque `producerId`, `sourceId`, and `revision`.
It identifies the producer source only. It neither creates accepted Logical UI
authority nor proves Android execution.

Admission compares a supplied `ContractDescriptor` structurally and exactly to
`minimumContract`. A future schema, version, assurance, semantic authority, or
driver is therefore rejected rather than silently accepted by this provisional
contract.

## Public runtime model

The public, immutable model contains only target-neutral data:

```text
Model(Metadata, Screen)
Screen = ResourceList | ResourceDetail
ResourceList(id, title, items, detailTarget, actions)
ResourceDetail(id, resourceId, title, sections, actions)
ListItem(id, fields, actions)
Section(id, Option[title], fields)
Field(id, label, role, value)
Action(id, label, enabled)
```

`ListItem.id` is the opaque resource identity. It is reused by
`DetailRequest.resourceId` and by `ResourceDetail.resourceId`. A `Screen.id` is
a separate opaque presentation identity. A List model carries no eager Detail
payload: `DetailTarget.screenId` identifies the logical Detail screen, not a
server URL, callback, Widget, or target route.

`ResourceList` always has `BrowsePurpose` and `CollectionDisplay`.
`ResourceDetail` always has `InspectPurpose` and `DetailDisplay`. An `Action`
always has the shared `CommandPattern`; it is a descriptor and never executes a
domain operation.

The closed presentation roles are `content`, `title`, `subtitle`,
`description`, and `status`. The closed display values are `TextValue`,
`NumberValue`, `BooleanValue`, `TimestampValue`, and `MissingValue`. Content
accepts every supported value. The other roles require `TextValue`; a Title
also requires nonblank text. Semantic property names, raw JSON, `Any`,
Widget/framework values, network endpoints, operations, style, and target
rules are not members of this contract. Image remains deferred until a driver
justifies it.

## Deterministic admission

`CozyLogicalUiRuntimeValidation.validate` returns the supplied structural
model unchanged on success. It neither sorts, trims, repairs, normalizes, nor
loads data. Empty item, section, field, and action vectors are valid.

Ordinary IDs and provenance text are nonnull, nonempty, and exactly trimmed;
Unicode, internal punctuation, and case are opaque. Screen titles, labels,
`Some(title)` values, and `TitleRole` text are nonnull and nonblank. Successful
admission preserves their supplied display text byte for byte. `TextValue` may
be empty, but its payload may not be null. Null model roots, metadata,
descriptors, provenance, screens, target, vectors, elements, Option containers,
`Some(null)`, display values, and typed payloads are rejected without
dereferencing them.

List item IDs are unique within one List; section IDs within one Detail; field
IDs within one item or section; and action IDs within their owning screen or
item. Non-Content roles occur at most once in a field container. IDs may be
reused across those containers. A Detail target must have a valid ID and must
not identify its own List screen.

Validation first checks root and metadata shape, then the exact contract, then
provenance, screen scalar IDs/titles, structural vectors and scoped uniqueness,
nested field/action content, and finally a List Detail target. The first error
uses a stable dotted/indexed model path such as
`model.metadata.contract`, `model.screen.items[1].fields[0].role`, or
`model.screen.detailTarget.screenId`.

| Code | Meaning |
| --- | --- |
| `LUI74_MODEL_INVALID` | Null root, metadata, or screen shape |
| `LUI74_CONTRACT_INCOMPATIBLE` | Null or nonidentical descriptor at `metadata.contract` |
| `LUI74_PROVENANCE_INVALID` | Missing or invalid provenance |
| `LUI74_ELEMENT_INVALID` | Null vector, nested element, Option, or `Some(null)` |
| `LUI74_ID_INVALID` | Invalid ordinary ID |
| `LUI74_TEXT_INVALID` | Invalid label, title, optional title, or Title text |
| `LUI74_VALUE_INVALID` | Null display value or typed payload |
| `LUI74_ROLE_INVALID` | Null or value-incompatible presentation role |
| `LUI74_DUPLICATE_ID` | Duplicate ID in its declared scope |
| `LUI74_DUPLICATE_ROLE` | Repeated non-Content role in one field container |
| `LUI74_DETAIL_TARGET_INVALID` | Missing, invalid, or self Detail target |

## Adaptive selection

`CozyLogicalUiSelection.select(model, itemId, intent)` validates its Model
before requiring a List, then validates an existing item ID, and only then
accepts an adaptive intent. It returns the same `Selection(listId, itemId)` and
`DetailRequest(screenId, resourceId)` for both modes. Compact intent returns
`NavigateToDetail` with `NavigatePattern`; expanded intent returns
`UpdateDetailRegion` with `SelectPattern`. It has no mutation, I/O, server
request, callback, actual navigation, or action execution.

`LUI74_SELECTION_INVALID` reports invalid or unknown `itemId`;
`LUI74_SELECTION_SCREEN_INVALID` reports selection on a Detail; and
`LUI74_ADAPTIVE_INTENT_INVALID` reports null/unsupported `intent`. Repeated
same inputs return structurally equal transitions.

## Executable specifications and acceptance stages

`CozyLogicalUiRuntimeSpec` specifies admission, diagnostics, null resistance,
scoped identity, selection rejection, and shared vocabulary identity.
`cozyruntime.CozyLogicalUiRuntimeAcceptanceSpec` is an external-package,
planned-scenario fixture proof: multiple fake resources share compact and
expanded selection; matching Detail fixtures resolve by logical identity; and
field configuration changes visible Detail content without changing selection
logic.

This is Cozy local provisional acceptance. Actual Android mock integration is a
separate later acceptance stage. CNCF Phase 96 may consume this provisional
contract without waiting for that Android proof; CNCF Display Projection and
Protocol code remain outside this contract.
