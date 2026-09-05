# Document Project Cross-Media Projection Design

Status: Phase 46.1 design

## Ownership

Phase 46.1 consumes `CozyDocumentPresentationSemantics.Validated` as its only
semantic input. It does not parse Content Core, Composition, catalog, or
Projection Policy files; Phase 46 has already admitted and normalized them.

```text
Validated presentation semantics
             |
             v
cross-media projection
  |                     |
  v                     v
SlidePage          StoryboardScene
  |                     |
  +----------+----------+
             v
  confirmation HTML and receipt (later Phase 46.1 stages)
```

The projection is an immutable, package-local value. It is neither a new
authoring schema nor a replacement for Phase 36 Visual Page or the existing
Storyboard production authority.

## Projection rule

The Phase 46 aggregate binds each Structure to one Plan Step and preserves the
ordered `article.visibleText` vector. For every Structure text item, the
projection deterministically creates one SlidePage and one StoryboardScene.
This gives one Structure a one-to-many mapping when it has multiple visible
text items without copying or inventing the Structure's Logical graph.

The projection orders values by Plan Step order, then Structure ID, then the
one-based visible-text ordinal. Its IDs are derived from those stable inputs:

```text
slide-<structure-id>-<ordinal>
scene-<structure-id>-<ordinal>
```

Both outputs retain the same Structure ID, Story Step ID, Logical graph,
claims, source/asset references, and selected medium-specific Visual. Slide
pages use the `slides` Visual selection; scenes use the `video` selection.
Reader-facing visible text becomes slide text and scene caption. Narration,
timing, transition, animation, and production instructions are deliberately
absent: the accepted input provides none, so Phase 46.1 must not infer them.

## Coverage boundary

Every Plan Step and every Structure must have at least one page and one scene.
Coverage is checked at both levels: a Structure's output cannot be credited to
another Structure merely because both are bound to the same Plan Step. A
missing Structure binding or an empty derived mapping fails projection before
any output exists with `DP-PROJ-001`. This is a projection completeness error,
not a placeholder case. The later receipt stage records identities and output
bytes; it does not replace this semantic-coverage check.

## Non-goals

This initial projection does not create a Phase 36 Visual Page source file,
parse or write Storyboard source, render PowerPoint/PDF/video, assign layout
coordinates, or write a Document Project. Those remain separate medium or
operation boundaries.
