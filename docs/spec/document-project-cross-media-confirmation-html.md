# Document Project Cross-Media Confirmation HTML

Status: Phase 46.1 normative specification

## Boundary

The confirmation renderer is a read-only Cozy-local value projection. Its only
input is the typed `CozyDocumentCrossMediaProjection.Projection`; it does not
accept generic JSON or maps, untyped Content Core, a Document Project
directory, or any production-renderer input.

The output is a deterministic self-contained UTF-8 HTML string and a
`sha256:` identity computed over those exact UTF-8 bytes. Rendering has no file,
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
separate accessible HTML sections. Diagnostics MUST bind the Content Core ID,
semantic identity, currentness identity, projection identity, mapping and
coverage state, claims, source references, and asset references.

Production metadata MUST explicitly say that narration, timing, transition,
animation, layout, and external renderer instructions are not carried by this
semantic confirmation projection. HTML MUST use escaped values, semantic
headings, table headers with `scope`, inline CSS, and no external dependency.
