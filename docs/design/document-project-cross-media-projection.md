# Document Project Cross-Media Projection Design

Status: Phase 46.1 design with Phase 71.1 declarations and Phase 71.5 S05 typed semantics

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
  confirmation HTML and in-operation semantic coverage
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

## Material declaration boundary in Phase 71.1

The Projection copies typed material declarations in stable ID order. Canonical
source objects contain exactly `{id}` and asset objects exactly
`{id, mediaType}`. Material hashes are neither calculated, parsed, compared,
nor emitted by this projection. These are semantic declarations for the actual admitted direct inputs.

The actual result carries Content Core id, declarations, StoryFlow, media values,
and mappings directly. Canonical JSON serializes those values deterministically.
Upstream Explanation hash removal and remaining independent media contracts
remain separately bounded work.

## DM14 catalog proof removal

Projection contains no `presentationCatalogIdentity`,
`presentationLogicalCatalogIdentity`, `presentationCatalogValue`, or
`presentationLogicalCatalogValue`. These deleted historical catalog proofs
are not replaced by copied JSON, canonical strings, fingerprints, or alternate
identity fields. Actual named catalog validation is performed upstream on
current inputs; a valid same-ID/revision definition update is not independently
vetoed by a historical catalog copy.

## Coverage boundary

Every Plan Step and every Structure must have at least one page and one scene.
Coverage is checked at both levels: a Structure's output cannot be credited to
another Structure merely because both are bound to the same Plan Step. A
missing Structure binding or an empty derived mapping fails projection before
any output exists with `DP-PROJ-001`. This is a projection completeness error,
not a placeholder case. The coverage boundary checks actual Step/Structure/mapping/HTML presence and
association, including fixed semantic renderer/profile compatibility. It returns
an in-operation result and carries no receipt or output certificate.

## Non-goals

This initial projection does not create a Phase 36 Visual Page source file,
parse or write Storyboard source, render PowerPoint/PDF/video, assign layout
coordinates, or write a Document Project. Those remain separate medium or
operation boundaries.

## Phase 71.5 implementation and validation status

S05 implements the current semantic association, typed projection, coverage,
confirmation HTML, and direct scaffold contracts under
`P715-02-S05-PRESENTATION-SEMANTIC-COVERAGE`; its focused validation is pending.
S04's three existing suites completed with 120 successes, zero failures, zero
canceled cases, no warnings, exit 0, and terminal shared-lock release, as recorded
in `p715-02-s04-focused-validation-result.json`. The first 119-success/one-failure
run and the bounded existing MediaReceipt fixture correction remain retained
history. This is implementation progress, not Step or Phase acceptance.

External E715-01 through E715-04 reconciliation and the same full 34-scene JA
ModelHarness VOICEVOX generation, final assembly, audible correct end card, and
player EOF remain open. Repository-full validation is deferred-not-run to
Phase 71.7. Independent Alignment, upstream Explanation hashes, delegated
MediaReceipt, and Core/Description/Confirmation writer removal remain on the
finite frontier; no exception or replacement certificate is introduced.
