# Document Project Cross-Media Semantic Coverage

Status: Phase 46.1 semantic coverage amended by Phase 71.5 S05

## Typed boundary

The existing `CozyDocumentCrossMediaReceipt` object and this document path remain
the package-local semantic-coverage boundary for existing callers and links.
`verifyCoverage` and `coverage` accept already admitted typed `Validated`,
`Projection`, and `Rendered` values. Coverage is an in-operation result with
structured `CoverageDiagnostic` values and a `CoverageResult`; it is not a
persisted receipt or currentness certificate.

The renderer's fixed semantic renderer and profile names must be compatible.
This check concerns which renderer and profile produced the supplied typed
rendering; it does not recalculate output or compare expected HTML bytes.

## Semantic completeness and association

Every declared Plan Step and Structure must be represented in slide projection,
video projection, and confirmation HTML. Every slide page and storyboard scene
carrying a valid Structure ID must retain that Structure's declared
`storyStepId`; a valid Structure assigned to another Plan Step is incompatible.
Every retained mapping must resolve the represented values, and HTML must
contain the Step, Structure, page, and scene labels required by the mapping.

Missing, ambiguous, incompatible, and unprojected values produce the existing
structured deterministic diagnostics. `DP-COV-INCOMPATIBLE` includes actual
renderer/profile incompatibility and cross-media Structure-to-Step association
mismatches. `DP-COV-MISSING`, `DP-COV-AMBIGUOUS`, and `DP-COV-UNPROJECTED` retain
their ordinary semantic meanings. Successful rendering cannot substitute for
this completeness check.

## Removed historical receipt role

Phase 46.1 formerly coupled coverage to receipt capture and digest currentness.
S05 removes those protection-only models, APIs, serializers, and output comparisons.
The selected default confirmation is ordinary HTML. Historical receipt files
are not deleted or migrated, and no replacement receipt is emitted.

## Non-goals

Coverage does not parse or write source files, invoke a production renderer,
create slide/video artifacts, expose public CLI/HTTP/SPI/MCP work, persist
state, or accept an external Article 8 artifact.

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
