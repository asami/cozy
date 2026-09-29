# File Update Management and Digest Boundary

Status: approved direction; implementation and executable acceptance pending in
[Phase 71](../phase/phase-71.md) through [Phase 71.4](../phase/phase-71.4.md).
Decision: 2026-09-28.

## Hash removal

Remove application-level hash logic except digest generation and verification
with an explicit purpose of checking published/distributed artifact integrity.
Inventory each retained digest by artifact, operation, and integrity guarantee.
Publication-related placement or naming alone does not justify retention.

Removal includes internal artifact/approval identities, freshness hashes,
computation, comparisons, hash-specific models and codecs, emitted fields,
configuration, scaffolds, and tests requiring those hashes. Do not retain
disabled code, diagnostic-only calculation, compatibility hash processing, or
fallback switches for potential future use. Ordinary collection hashing is
outside this artifact-management contract.

Old document hash properties may be ignored by ordinary document loading or
removed by migration while preserving required non-hash data. They must not
require hash-specific parsing, calculation, comparison, or re-emission.

## File updates

For now, use file existence, declared producer/input dependencies, and file
modification times. Missing outputs or newer declared inputs select the real
producer. Uncertain reuse selects conservative regeneration; a missing producer
or required input is reported explicitly. Do not infer undeclared dependencies.
An explicit prebuilt adoption accepts a valid supplied file even when a copy
preserved its modification time. Validate paths, formats, and required inputs
independently of freshness.

If stricter file update management is required, introduce accurate, explicit
metadata management. Specify the tracked files, dependencies, production
operations, successful-generation state, and metadata update semantics before
implementing it. Content hashes must not substitute for those semantics.
The current Phase does not require a speculative metadata framework.

## Verification obligations

The Phase 71 sequence must update affected subsystem specifications and executable
specifications together with implementation. Verify timestamp-driven rebuild
and reuse, conservative regeneration, explicit prebuilt replacement, historical
document handling without hash processing, and preservation of prior successful
outputs on failure. Audit removal of non-integrity hash code and emitted fields.
Retained publication/distribution integrity checks must still detect actual
artifact mismatches at that boundary and must not determine local freshness.

## Implementation ownership after the 2026-09-29 split

The behavior above is unchanged by the planning split. Phase 71 owns the real
producer/graph contract, digest-purpose inventory and executed Core-rooted video
bootstrap; Phase 71.1 owns document/media and inventoried adjacent application
removal/currentness; Phase 71.2 owns remaining video removal, continuity and
pronunciation; Phase 71.3 owns local site/PDF/prebuilt adapters and retained
publication-integrity acceptance; Phase 71.4 owns media-end repair, the global
removal audit and complete combined-tree acceptance. Each canonical member
checklist is the work-state authority; this specification grants no additional
external repository/skill mutation or successor execution authority.

## Phase 71 single-operation policy boundary

The executable contract for one admitted file operation is
[CozyFileUpdatePolicySpec](../../src/test/scala/cozy/generation/CozyFileUpdatePolicySpec.scala),
backed by the package-private `CozyFileUpdatePolicy` classifier. It is a pure,
deterministic selector over already checked file observations: generation uses
declared input order, existence, independently established validity, and
`FileTime` modification timestamps; explicit prebuilt adoption is a separate
operation that examines only the supplied output. The policy has no hash,
receipt, persisted schema, filesystem I/O, CLI, graph resolver, dispatcher, or
producer invocation. A missing producer or required input is a reported
unavailable result, not an inferred dependency or an invented fallback.

This policy does not become an overall native AI dependency planner. Codex and
the declared source-authoring graph continue to own semantic dependency
planning and source updates. Passing these file-policy specifications alone is
not product acceptance: the selected private Core-rooted video driver, focused
validation, independent review, and later Phase-owned acceptance remain
separate obligations. No successor implementation is performed or implied by
this contract.
