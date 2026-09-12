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

## Required HTML behavior

The output MUST be one deterministic self-contained HTML document with inline
CSS and inline script. It MUST have a page heading plus named accessible
regions for containment, Flow, Structure, and prose. No external resource,
network activity, fixed-coordinate canvas, or pointer-only control is
allowed.

The containment region MUST recursively represent admitted Steps with native
`button` controls. Each button MUST expose the admitted localized Step label
and stable ID, use `aria-pressed`, and programmatically associate itself with
the selected Flow, Structure, and prose regions. Keyboard activation follows
the native button contract; a visible focus style is required.

The selected Flow region MUST contain exactly the selected Step's direct-child
typed transitions and their Flow-type wording. The selected Structure region
MUST contain exactly the selected Step's Logical Pattern, nodes, node roles,
and Relations with their corresponding generic wording. The regions MUST stay
separate from each other and from recursive containment.

The prose region MUST recursively render all admitted Section and Block forms.
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

All author-controlled text and attributes MUST be HTML escaped. Identical
typed inputs MUST produce byte-identical HTML and the same identity. CSS MUST
provide responsive desktop-to-mobile reflow without removing information,
selection semantics, or keyboard access.

## Executable specification coverage

`CozyDocumentConfirmationProjectionV2Spec` MUST use `AnyWordSpec`, adjacent
Given/When/Then clauses, and `should` matchers to prove deterministic identity
and escaping; recursive localized native selection controls; separation of
containment, Flow, and Structure; identity-only Section/Block/List Item
highlight membership; primary status with secondary identities; responsive
self-contained accessibility markup; and rejection of a missing generic term.
Its vocabulary is test-only and is not a Cozy locale resource or Article 9
driver.
