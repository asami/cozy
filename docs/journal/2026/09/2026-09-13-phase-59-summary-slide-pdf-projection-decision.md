# Phase 59 Summary Slide PDF Projection Decision

Date: 2026-09-13

## Trigger

Phase 40 already provides the accepted `summary_slides_pdf` generation path.
It consumes a validated VisualPageSet, catalog, and Visual Page binding (or a
legacy Slide-IR) and owns direct PDF rendering, verification, receipt identity,
and currentness.

Phase 58.1 and Phase 58.2 subsequently established the current Document
Project v2 authoring inputs:

- locale-independent `content/core.yaml`;
- localized `content/<locale>/document.yaml`; and
- localized `content/<locale>/summary.yaml`.

The latest Summary Description is useful as the concise semantic authority and
its confirmation HTML is useful for review, but neither is an accepted direct
input to the Phase 40 PDF route. There is no strict, provenance-preserving
connection from the latest v2 Summary Description to a Phase 36 VisualPageSet
and binding.

## Decision

Create **Phase 59: Summary Description to Visual Page PDF Projection**.

Phase 59 will define and implement the missing deterministic adapter. A typed,
explicit Summary Slide Projection Profile selects a valid, source-grounded
projection from bound v2 sources to a VisualPageSet and binding. The Phase then
reuses the existing Phase 40 `summary-slides-pdf` route unchanged for PDF,
renderer-manifest, receipt, and currentness behavior.

The profile is an explicit derived-presentation selection contract, not an
extension of `summary.yaml` with physical layout. It must carry exact source
identities and page mappings, and it must reject a missing, stale, unresolved,
or semantically ungrounded input before a PDF or receipt becomes visible.

## Consequences

- The current v2 Document Project DSL becomes capable of producing a local,
  current summary-slide PDF without duplicating the Phase 40 renderer.
- `summary.yaml` continues to own concise semantic content rather than CSS,
  coordinates, fonts, pagination, or PDF settings.
- Step Flow and Step-local Structure remain distinct in generated slide
  semantics and visible marks when the Summary selected them.
- Phase 58.2 confirmation HTML remains review evidence only; it is not parsed
  as a PDF source and does not become a second semantic authority.
- Article 9 is the first local acceptance driver. This decision does not
  authorize SimpleModeling.org production mutation, publication, deployment,
  registration, upload, or push.

## Planning source

- `docs/phase/phase-40.md`
- `docs/phase/phase-58.1.md`
- `docs/phase/phase-58.2.md`
- `docs/spec/document-project-document-and-summary-description-v2.md`
- `docs/spec/visual-page.md`
- `docs/spec/media-package.md`
- `docs/phase/phase-59.md`
