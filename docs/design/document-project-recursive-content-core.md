# Recursive Content Core Logic Tree

## Decision

`cozy.content-core.logic-tree.v1` is a standalone semantic authority for a
recursive explanation. It does not read, migrate, or make compatible the
Document Project v1 Core or the v2 Presentation Semantics sibling.

The authority is a direct regular file named `core.yaml`. Its closed root is:

```yaml
schema: cozy.content-core.logic-tree.v1
id: stable-core-id
root: Step
```

`Step` is recursive and has exactly `id`, `semanticRole`, `claims`,
`structure`, `flow`, and `steps`. Structural containment is the only parent
relationship: a child is owned by one enclosing Step, and a repeated ancestor
is a containment cycle. The Core holds stable Step, claim, node, relation, and
transition identities plus logical roles; it holds no locale-sensitive title,
claim text, or node label.

## Local semantics

Each Step owns one local Structure and one direct-child Flow. Structure uses
the existing `CozyVisualPage.fixedCatalog` logical patterns, node roles, typed
relations, cardinalities, directions, and topologies. The Logic Tree owns no
parallel pattern or relation catalog.

A Structure relation resolves only the nodes in that Structure. A Flow
transition resolves only the immediate child Step ids of its owning Step. Flow
cannot use a self-link, an unknown relation type, a duplicate identity, or an
out-of-parent endpoint.

## Locale boundary

`cozy.content-core.logic-tree-format.v1` is a separate direct YAML authority.
It binds `coreId` and `coreIdentity` to the exact direct bytes of `core.yaml`,
declares a BCP-47 `locale`, and has one-to-one `stepBindings`,
`claimBindings`, and `nodeBindings`. This makes Japanese wording a format
choice rather than Core identity or filename state.

## Projections and command

The overview is a self-contained, deterministic nested card/tree document:
each Step card contains claims, its local Structure, direct-child Flow, and
structural children. Typed Flow edges and local typed relations remain
reader-facing content rather than diagnostic tables.

The slides projection is self-contained 16:9 HTML. It emits one depth-first
page for every parent and leaf Step. Each page shows its ancestor context,
claims, local Structure, direct children, direct-child Flow, and deterministic
previous/next keyboard navigation. Print CSS forces one Step page per printed
page.

The only public entry point is:

```text
cozy document-project logic-tree render --core <core.yaml> --format <format.yaml> --kind overview|slides --save <output.html>
```

It does not construct a Document Project or persist workflow state. It reads
only the two supplied direct files and atomically writes the explicitly named
safe HTML destination.
