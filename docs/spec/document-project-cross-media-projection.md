# Document Project Cross-Media Projection

Status: Phase 46.1 normative specification

## Input boundary

The cross-media projector MUST accept only an already validated
`cozy.content-core.presentation-semantics.v2` aggregate. It MUST NOT accept
generic renderer maps, page aliases, loose scalar parameters, or an unvalidated
Content Core document.

## Slide and scene derivation

For every Structure, the projector MUST create one slide page and one video
scene for every element of its ordered, nonempty `article.visibleText` vector.
Each derived value MUST retain:

- the stable Structure and Story Step identities;
- the Structure's Logical Pattern, nodes, and typed Relations;
- the Plan Step's claims, source references, and asset references; and
- the selected `slides` or `video` Visual Pattern and typed parameters.

The generated slide ID is `slide-<structure-id>-<one-based-ordinal>`; the
generated scene ID is `scene-<structure-id>-<one-based-ordinal>`. Projection
order MUST be Plan Step order, Structure ID, then visible-text ordinal.

Slide-page text and scene caption MUST exactly equal the corresponding visible
text. The projector MUST NOT infer narration, duration, timing, transition,
animation, layout, or production instructions.

## Mapping and coverage

The result MUST expose separate mappings from every Plan Step to its slide IDs
and to its scene IDs. One Structure may map to multiple pages and scenes;
those values share semantic authority rather than duplicating it. Coverage is
also Structure-level: every Structure MUST resolve to at least one slide page
and one storyboard scene independently of any sibling Structure bound to the
same Plan Step.

Projection MUST fail if a Plan Step lacks a Structure, a Structure does not
produce at least one page and scene, or a retained mapping does not resolve to
the exact derived value. An empty-output Structure MUST fail atomically with
`DP-PROJ-001` before a successful partial projection is returned. It MUST NOT
emit an empty mapping or a successful placeholder.

## Identity

The projection identity MUST be a SHA-256 identity over fixed-order projection
content and the consumed Phase 46 semantic/currentness identities. Equivalent
validated input produces identical projection content and identity. A change to
semantic content, currentness input, structure, visible text, selected Visual,
claim, source, asset, or mapping changes the identity.

## Later Phase 46.1 stages

This contract supplies typed inputs for confirmation HTML and receipts. The
HTML and receipt stages MUST consume this value; they may not independently
recreate mappings from an untyped source.
