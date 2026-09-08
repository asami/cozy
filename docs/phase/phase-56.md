# Phase 56: Document Project Native Production Execution

Status: PLANNED

Plan date: 2026-09-07

Development item: DEV-019

## Goal

Turn the existing Document Project workflow kernel into a native production
execution boundary. `cozy document-project run` must dispatch one admitted
logical operation through one typed provider binding and atomically close its
validated outputs, receipt, append-only attempt, and derived Workflow Instance
currentness.

The implementation is greenfield. It does not preserve or wrap the internal
operation sequence, receipt layout, artifact placement, or provider adapter of
`cozy-article-media` or the current publication-preparation workflow.

## P56-01: Typed Provider Execution Contract

Stage Status: PLANNED

- Resolve exactly one logical operation and one typed provider binding.
- Validate exact input authorities, prerequisite Work Products, provider
  availability, and bounded output destinations before execution.
- Replace deferred/record-only execution with a typed provider result carrying
  output identities, media types, diagnostics, and receipt evidence.
- Keep a missing provider explicitly blocked; an attempt with empty outputs and
  no receipt is not successful execution.

## P56-02: Atomic Evidence Closure

Stage Status: PLANNED

- Validate output paths, media types, hashes, and receipts before accepting an
  operation result.
- Append the Operation Attempt and derive Work Product/Workflow Instance state
  from the same accepted evidence boundary.
- Prevent partial success in which files exist but evidence/currentness is not
  closed, or evidence claims outputs that were not produced.
- Preserve append-only attempts and deterministic recovery after provider
  failure or invalid output.

## P56-03: Closed Executable Planning State

Stage Status: PLANNED

- Distinguish logical selection, prerequisite readiness, provider availability,
  output currentness, and immediate executability as closed typed states.
- Project the same state through `inspect`, `plan`, `verify`, and Dashboard.
- Keep planning read-only and require `run` for execution.
- Integrate Phase 49 presentation-semantics Work Product state rather than
  duplicating its semantic/currentness model.

## P56-04: Typed Verification Policy

Stage Status: PLANNED

- Introduce the closed policy `structural | visual` and propagate it through
  provider and review boundaries.
- Make `structural` the default. It verifies semantic authority, dependencies,
  hashes, receipts, counts, text, duration, dimensions, streams, codecs, and
  other lightweight machine-readable properties without rasterizing review
  representations.
- Admit `visual` only by explicit user selection and create only the minimum
  representations required for the selected artifact.
- Keep selected public image Work Products distinct from temporary visual
  inspection artifacts.

## Dependencies

- completed Document Project v2 kernel and evidence/currentness work in Phases
  45 through 45.2;
- completed logical-presentation contracts in Phases 46 and 46.1; and
- planned Phase 49 presentation-semantics workflow integration.

## Exclusions

- Publication Export or a SimpleModeling.org target binding, owned by Phase 57.
- Compatibility adapters for `cozy-article-media` or the current publication
  preparation workflow.
- Manual evidence adoption or retrospective fabricated attempts.
- Default PDF/slide/video raster review.
- Provider-specific semantic authority outside typed bindings.
- Implicit publication, deployment, upload, push, or commit.

## Completion Criteria

Phase 56 completes when a public Document Project `run` command executes typed
providers, validates and atomically records outputs/receipts/attempts/currentness,
exposes closed executable planning states consistently, defaults to structural
verification, supports explicitly selected visual verification, and passes
focused specifications, full Cozy validation, and independent review.

## References

- `docs/phase/phase-56-checklist.md`
- `docs/journal/2026/09/2026-09-07-document-project-production-workflow-greenfield-decision.md`
- `docs/phase/phase-49.md`
- `docs/spec/document-project.md`
