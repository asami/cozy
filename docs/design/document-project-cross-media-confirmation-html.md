# Document Project Cross-Media Confirmation HTML Design

Status: Phase 46.1 design amended by Phase 71.5 S05

`CozyDocumentCrossMediaConfirmationHtml` is a package-local immutable
projection over the accepted `CozyDocumentCrossMediaProjection.Projection`.
It has no authority to read or reconstruct Content Core, presentation
semantics, catalog data, or a Document Project directory. The projection
retains the stable Content Core ID so the renderer can bind diagnostics without
loosening the typed input boundary.

The renderer traverses Story Flow, mappings, and media values in their already
validated fixed order. It groups pages and scenes by their retained Structure
ID only for presentation, preserving every typed mapping and semantic value.
It returns `Rendered(html, rendererIdentity, profileIdentity)`. The fixed
existing renderer/profile strings designate actual semantic rendering choices.
The HTML is an ordinary deterministic string, with no aggregate identity or
output certificate. Diagnostics present semantic facts and mapping coverage.

The HTML is intentionally a confirmation artifact, not an authoring or
production artifact. Inline CSS and escaped text provide a self-contained,
accessible review surface. Narration, timing, transitions, animation, layout,
and external renderer instructions remain outside this semantic projection.

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
