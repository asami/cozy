# Document Project Cross-Media Receipt and Coverage Design

Status: Phase 46.1 design

The receipt is a package-local immutable value assembled from the already
typed cross-media Projection and deterministic confirmation HTML Rendered
value. Projection identity content is retained rather than reconstructed from
generic data. Source and asset declarations are copied in stable ID order so
their declared identities remain visible in receipt identity content.

The confirmation renderer declares fixed versioned renderer and profile
identities. It returns exact HTML text, its UTF-8 SHA-256 output identity, and
the input Projection identity. Receipt creation validates all of these values
against the fixed renderer output before constructing one complete immutable
receipt. Currentness compares receipt bindings in fixed order and returns a
precise stale reason without changing state.

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
