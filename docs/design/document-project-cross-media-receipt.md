# Document Project Cross-Media Receipt and Coverage Design

Status: Phase 46.1 design with the Phase 71.1 material-declaration contract

The receipt is a package-local immutable value assembled from the already
typed cross-media Projection and deterministic confirmation HTML Rendered
value. Projection identity content is retained rather than reconstructed from
generic data. Source and asset declarations are copied in stable ID order so
their declared IDs and asset media types remain visible in receipt content.
Under the Phase 71.1 material contract, canonical source objects contain
exactly `{id}` and asset objects exactly `{id, mediaType}`. Receipt processing
does not calculate, parse, compare, or emit material hashes.

DM14 removes both presentation catalog digests and the interrupted complete
`presentationCatalogValue`/`presentationLogicalCatalogValue` copies from
receipt construction, canonical content, and currentness comparisons. No JSON
snapshot or alternate proof is introduced. Current catalog validity and
operational reference resolution remain upstream responsibilities.

The confirmation renderer declares fixed versioned renderer and profile
identities. It returns exact HTML text, its UTF-8 SHA-256 output identity, and
the input Projection identity. Receipt creation validates all of these values
against the fixed renderer output before constructing one complete immutable
receipt. Currentness compares receipt bindings in fixed order and returns a
precise stale reason without changing state.

Source and asset currentness is typed declaration equality. Changed declaration
IDs retain their precise stale reasons; valid same-path byte replacement with
unchanged declarations can remain current without digest rebinding. This
partial comparison boundary does not establish file freshness or production
same-path reuse.

The Core, Explanation catalog, semantic/currentness, projection, renderer, output, and
receipt hash layers remain unfinished migration work in Phase 71.1, as does
the mandatory operation/FileTime timestamp and adoption behavior in this same
Phase. These current layers are transitional, not an accepted end state or
integrity exemptions. Semantic, safety, rendering-validation, and typed
coverage contracts continue to apply.

Coverage is a separate typed verification over Validated, Projection, and
Rendered. It checks Plan Step mappings, Structure mappings, each page and
scene's Structure-to-Step association, mapped IDs in the confirmation HTML,
duplicate or missing values, and values not declared by Validated. A valid
Structure ID on a page or scene must retain the Structure's declared
`storyStepId`; a mismatch produces a deterministic `DP-COV-INCOMPATIBLE`
diagnostic on that media association path. Its structured diagnostics
distinguish missing, ambiguous, incompatible, and unprojected content. This
separation intentionally allows receipt identity/currentness and semantic
coverage to fail independently.

The boundary does not render PowerPoint or video, read or write files, parse
untyped input, persist receipts, or make publication, Article 8, or external
renderer decisions.
