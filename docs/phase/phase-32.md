# Phase 32: CML Source-Result Compatibility Naming

Status: planned

Plan date: 2026-08-19

## Goal

Make `Resolved.projectRelativePath` the canonical CML source-result field in
Cozy while preserving the explicitly required deprecated source compatibility
surface for existing callers and named arguments.

## Boundary and invariants

- Change only the `Resolved` naming surface and its Cozy callers.
- Preserve the resolved path value, CML source precedence, CML metadata, and
  published output.
- Retain a deprecated compatibility accessor and named-argument compatibility
  only where the `Resolved` surface requires it.
- Do not redesign CML source selection or alter CAR metadata, publication, or
  component wiring behavior.

## Stages

### CM32-01: Canonical Naming and Compatibility

Stage Status:

- Current status: PLANNED
- Owner: Cozy archive/CML source resolution
- Checklist basis: `CM32-01`

Define the canonical field, migrate internal callers, and preserve only the
reviewed compatibility path required by public source use.

### CM32-02: Consumer Evidence and Closure

Stage Status:

- Current status: PLANNED
- Owner: Cozy publication, review, and lint consumers
- Checklist basis: `CM32-02`

Prove direct resolver compatibility and unaffected publication, review, and
lint consumers before the Phase may close.

## Completion criteria

- `projectRelativePath` is the canonical `Resolved` field and all Cozy callers
  use it.
- The exact required deprecated compatibility surface remains explicit and
  source-compatible.
- CML source precedence, metadata, and published output are unchanged.
- Direct resolver and full Cozy validation pass with independent review.

## Dependencies

- CNCF Phase 57.4 naming-review evidence recorded in
  `docs/journal/2026/08/2026-08-19-cncf-compatibility-naming-hygiene-follow-up.md`.

## References

- `docs/phase/phase-32-checklist.md`
- `src/main/scala/cozy/archive/CarCmlSourceResolver.scala`
- `src/test/scala/cozy/archive/CarCmlSourceResolverSpec.scala`
