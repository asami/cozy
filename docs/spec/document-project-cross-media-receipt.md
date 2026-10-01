# Document Project Cross-Media Receipt and Coverage

Status: Phase 46.1 normative specification with the Phase 71.1 material-declaration contract

## Typed boundary

`CozyDocumentCrossMediaReceipt` accepts only the typed
`CozyDocumentCrossMediaProjection.Projection` and
`CozyDocumentCrossMediaConfirmationHtml.Rendered` values. The Projection
retains the accepted Core identity, normalized Composition and Plan
identities, Explanation catalog identity, policy identity,
declared sources and assets, semantic/currentness identity, mappings,
and its own projection identity. No generic JSON or map is admitted at this
boundary.

In Phase 71.1 the receipt MUST copy source and asset declarations in stable ID
order. Canonical JSON MUST serialize each source as exactly `{id}` and each
asset as exactly `{id, mediaType}`. Receipt processing MUST NOT calculate,
parse, compare, or emit material hashes. Source and asset currentness compares
these typed declarations; it MUST report the existing deterministic stale
reasons when declaration IDs change.

The renderer has one fixed versioned renderer identity and profile identity.
`Rendered` retains the input Projection identity and the output identity is
the SHA-256 of the exact UTF-8 HTML bytes. Receipt creation rejects an
incompatible renderer/profile, a mismatched Projection identity, a tampered
output hash, or HTML that is not the deterministic renderer output.

## DM14 deleted catalog proofs

Receipt construction, canonical output, and currentness contain none of
`presentationCatalogIdentity`, `presentationLogicalCatalogIdentity`,
`presentationCatalogValue`, or `presentationLogicalCatalogValue`.
No historical full/logical catalog snapshot equality replaces these fields.
The validated upstream current inputs still enforce catalog definitions and
operational graph/Visual references.

## Currentness and atomicity

A receipt binds all Projection identities, renderer/profile identities, and
the exact output identity in deterministic order. Currentness compares those
values and reports the first precise deterministic stale reason. Receipt and
coverage results are immutable; no partial mutable output or file is created.

Receipt currentness at this partial boundary proves typed binding and exact
output equality only. Unchanged source and asset declarations can remain
current after valid bytes are replaced at the same bound paths without digest
rebinding. Declaration equality MUST NOT prove file freshness or semantic
completeness, and it does not prove production same-path reuse.

The remaining Core, Explanation catalog, semantic/currentness, projection, renderer,
output, and receipt hash-layer migration remains unfinished and mandatory in
Phase 71.1, together with operation/FileTime timestamp and adoption behavior.
The current remaining identity layers are not integrity exemptions or an
accepted end state. All semantic, safety, rendering-validation, and typed
coverage contracts MUST remain in force during this migration.

## Independent semantic coverage

Coverage consumes the accepted typed `Validated`, Projection, and Rendered
values independently of receipt currentness. It proves every declared Plan
Step and Structure is present in slide projection, video projection, and the
confirmation HTML. Every slide page and storyboard scene carrying a valid
Structure ID MUST retain that Structure's declared `storyStepId`; a valid
Structure ID associated with another Plan Step is incompatible coverage.
Missing, ambiguous, incompatible, and unprojected values are returned as
structured deterministic diagnostics, including `DP-COV-INCOMPATIBLE` for
cross-media Structure-to-Step association mismatches. A current receipt MUST
NOT substitute for this check.

## Non-goals

This boundary does not parse or write source files, invoke a production
renderer, create a slide/video artifact, expose a public API, persist state,
perform CLI/HTTP/SPI/MCP work, or promote or accept any external Article 8
artifact.
