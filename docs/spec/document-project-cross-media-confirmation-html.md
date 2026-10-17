# Document Project Cross-Media Confirmation HTML

Status: Phase 46.1 normative specification amended by Phase 71.5 S05

## Boundary

The confirmation renderer is a read-only Cozy-local value projection. Its only
input is the typed `CozyDocumentCrossMediaProjection.Projection`; it does not
accept generic JSON or maps, untyped Content Core, a Document Project
directory, or any production-renderer input.

The output is `Rendered(html, rendererIdentity, profileIdentity)`: a
deterministic self-contained HTML string and the fixed existing semantic
renderer/profile names. These names identify the renderer and profile, not an
artifact. Rendering has no file,
network, clock, script, service, or state-mutation boundary.

## Required content

The HTML MUST place a Story Flow overview before structure, page, and scene
details. The overview MUST show every typed transition and each fixed-order
Story Step slide/video mapping. It MUST state `Unprojected content: none.` for
the complete projection boundary.

Each represented Structure MUST have the stable local trace label
`article-section-<structure-id>`. Its details MUST show all mapped slide page
and video scene IDs, reader-facing text, Logical Pattern, nodes, typed
Relations, selected slide/video Visual Patterns, and typed parameters.

Reader-facing content, reviewer diagnostics, and production metadata MUST be
separate accessible HTML sections. Diagnostics MUST show semantic facts: Content Core ID, mapping and coverage
state, claims, source references, and asset references.

Production metadata MUST explicitly say that narration, timing, transition,
animation, layout, and external renderer instructions are not carried by this
semantic confirmation projection. HTML MUST use escaped values, semantic
headings, table headers with `scope`, inline CSS, and no external dependency.

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
