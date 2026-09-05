# Document Project Cross-Media Confirmation HTML Design

Status: Phase 46.1 design

`CozyDocumentCrossMediaConfirmationHtml` is a package-local immutable
projection over the accepted `CozyDocumentCrossMediaProjection.Projection`.
It has no authority to read or reconstruct Content Core, presentation
semantics, catalog data, or a Document Project directory. The projection
retains the stable Content Core ID so the renderer can bind diagnostics without
loosening the typed input boundary.

The renderer traverses Story Flow, mappings, and media values in their already
validated fixed order. It groups pages and scenes by their retained Structure
ID only for presentation, preserving every typed mapping and semantic value.
It returns `Rendered(html, identity)`, where identity is SHA-256 over the
UTF-8 bytes of the exact HTML string.

The HTML is intentionally a confirmation artifact, not an authoring or
production artifact. Inline CSS and escaped text provide a self-contained,
accessible review surface. Narration, timing, transitions, animation, layout,
and external renderer instructions remain outside this semantic projection.
