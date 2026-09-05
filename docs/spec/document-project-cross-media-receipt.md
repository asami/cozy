# Document Project Cross-Media Receipt and Coverage

Status: Phase 46.1 normative specification

## Typed boundary

`CozyDocumentCrossMediaReceipt` accepts only the typed
`CozyDocumentCrossMediaProjection.Projection` and
`CozyDocumentCrossMediaConfirmationHtml.Rendered` values. The Projection
retains the accepted Core identity, normalized Composition and Plan
identities, explanation and presentation catalog identities, policy identity,
declared source and asset identities, semantic/currentness identity, mappings,
and its own projection identity. No generic JSON or map is admitted at this
boundary.

The renderer has one fixed versioned renderer identity and profile identity.
`Rendered` retains the input Projection identity and the output identity is
the SHA-256 of the exact UTF-8 HTML bytes. Receipt creation rejects an
incompatible renderer/profile, a mismatched Projection identity, a tampered
output hash, or HTML that is not the deterministic renderer output.

## Currentness and atomicity

A receipt binds all Projection identities, renderer/profile identities, and
the exact output identity in deterministic order. Currentness compares those
values and reports the first precise deterministic stale reason. Receipt and
coverage results are immutable; no partial mutable output or file is created.

Receipt currentness proves identity and output freshness only. It does not
prove semantic completeness.

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
