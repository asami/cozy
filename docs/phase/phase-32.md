# Phase 32: CML Source-Result Canonical Naming

Status: DONE

Plan date: 2026-08-19

## Goal

Make `Resolved.projectRelativePath` the canonical CML source-result field in
Cozy, retiring the former source field without a compatibility accessor or
named-argument alias.

## Boundary and invariants

- Change only the `Resolved` naming surface and its Cozy callers.
- Preserve the resolved path value, CML source precedence, CML metadata, and
  published output.
- Retire the former source field without a deprecated compatibility accessor,
  constructor, or named-argument alias.
- Do not redesign CML source selection or alter CAR metadata, publication, or
  component wiring behavior.

## Stages

### CM32-01: Canonical Naming and Compatibility

Stage Status:

- Current status: DONE
- Owner: Cozy archive/CML source resolution
- Checklist basis: `CM32-01`

Define the canonical field, migrate internal callers, and remove the retired
source compatibility surface as already authorized by the owning record.

### CM32-02: Consumer Evidence and Closure

Stage Status:

- Current status: DONE
- Owner: Cozy publication, review, and lint consumers
- Checklist basis: `CM32-02`

Prove direct resolver behavior and unaffected publication, review, and lint
consumers before the Phase may close.

## Completion criteria

- `projectRelativePath` is the canonical `Resolved` field and all Cozy callers
  use it.
- The retired source field has no compatibility accessor or named-argument
  alias.
- CML source precedence, metadata, and published output are unchanged.
- Direct resolver and full Cozy validation pass with independent review.

## Dependencies

- CNCF Phase 57.4 naming-review evidence recorded in
  `docs/journal/2026/08/2026-08-19-cncf-compatibility-naming-hygiene-follow-up.md`.

## Closure evidence

- Cozy commit `3394ad5741c3f0e071ad1559b4617299a562cd96`
  (`refactor: canonicalize CML source result naming`) is an ancestor of the
  current tree and made `projectRelativePath` canonical in the resolver and
  publication, review, and lint consumers.
- The owning record confirms the user-authorized non-compatible retirement,
  focused resolver validation (15 of 15), final Cozy validation (1,337 of
  1,337), full review, focused re-review, and acceptance of that commit.

## References

- `docs/phase/phase-32-checklist.md`
- `src/main/scala/cozy/archive/CarCmlSourceResolver.scala`
- `src/test/scala/cozy/archive/CarCmlSourceResolverSpec.scala`
