# Recursive Content Core Logic Tree Specification

## Authority forms

The Core has exactly `schema`, `id`, and `root`. `schema` is exactly
`cozy.content-core.logic-tree.v1`; its source is a direct regular file named
exactly `core.yaml`. Unknown root fields, a locale field, a locale-suffixed
Core filename, malformed UTF-8, and structures that cannot be admitted as the
closed Core shape are rejected.

Every Step has exactly `id`, `semanticRole`, `claims`, `structure`, `flow`,
and `steps`. `claims` contains stable claim ids. Structure has exactly
`pattern`, `nodes`, and `relations`; a node has `id` and `role`; a relation has
`id`, `relationType`, `from`, and `to`. Flow has exactly `id` and
`transitions`; a transition has `id`, `relationType`, `fromStepId`, and
`toStepId`.

Step, claim, node, relation, Flow, and transition identities are unique in the
Core. Local node/relation checks use `CozyVisualPage.fixedCatalog`. A Relation
endpoint must resolve in its declaring Structure. A Flow endpoint must resolve
to an immediate child Step and no Flow transition may self-link.

The Format requires `schema`, `id`, `coreId`, `locale`, `stepBindings`,
`claimBindings`, `nodeBindings`, and `chrome`. Its schema is exactly
`cozy.content-core.logic-tree-format.v1`; `coreId` binds the directly supplied
Core by its stable semantic id, and `locale` is a BCP-47 tag. Ordinary
additional Format-root properties are ignored without validation, preservation,
or re-emission. Every declared Core Step, claim, and node has one and only one
wording binding, and the Format has no unknown wording identity. `chrome` is a
closed, nonempty, trimmed locale-sensitive projection vocabulary containing
the overview and document-title labels, slides document-title label,
page-count wording, Claims, Local Structure, Direct children, and Direct-child
Flow headings, the two empty-state messages, Previous, Next, Deck, and the
navigation aria-label.

Before either direct YAML authority is normalized to JSON, its already
UTF-8-validated bytes are parsed with SnakeYAML duplicate keys disabled. A
duplicate mapping key is rejected as `LOGIC_TREE_SOURCE` at `$.core` or
`$.format`; `StructuredDocumentLoader` remains the JSON-normalization source.

## Projections

For either admitted input pair, repeated rendering produces equal UTF-8 HTML.
Overview and slides obtain all reader-facing fixed chrome
from the validated Format, while stable HTML ids, classes, relation types,
`data-*` values, semantic role ids, and JavaScript names remain structural.
Overview includes every Step, local Structure, typed Relation, and direct-child
Flow in nested containment order. Slides include one 16:9 depth-first page for
each Step, ancestor context, direct children, local Structure, Flow,
previous/next links, ArrowLeft/ArrowRight navigation, and a print page boundary
for each Step.

## Command boundary

`document-project logic-tree render` accepts exactly `--core`, `--format`,
`--kind`, and `--save`, with `overview` or `slides` as the only kinds. Input
files and the destination must be direct non-symlink paths; the destination
must be an absent or regular `.html` file beneath an existing direct
non-symlink directory. Publication uses a same-directory temporary file and
an atomic move. The command creates no Document Project, receipt, evidence,
provider run, publication, deployment, or external mutation.

## Executable specification

`src/test/scala/cozy/document/CozyDocumentLogicTreeSpec.scala` exercises the
real Article 9 Japanese fixture, including its localized overview and slide
chrome, and the closed rejection, duplicate-key source admission, semantic id
binding, containment, scope, wording, projection, navigation, print,
deterministic bytes, and CLI output contracts. It does not claim completion of
the separate Description Core-identity chain.

## Phase 71.1 DM08 supersession

Phase 71.1 / P710-03-DM-08 replaces the prior Description Core-identity
chain: `ValidatedCore` contains only the semantic Core and depth-first Steps.
It computes, stores, parses, compares, and emits no artifact hash. This leaves
the Core semantic maps and traversal contract unchanged; Description V2 binds
the directly loaded Core by logical ID alone.
