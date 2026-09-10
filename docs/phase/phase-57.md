# Phase 57: Document Project Publication Export

Status: PLANNED

Plan date: 2026-09-07

Development item: DEV-020

## Goal

Add a first-class Document Project Publication Export that emits only selected,
current, public Work Products through a versioned producer/consumer bundle and
receipt. SimpleModeling.org is the representative downstream target, not the
authority for Document Project internals.

## P57-01: Export Contract

Stage Status: PLANNED

- Freeze a public Document Project-owned export operation equivalent to
  `cozy document-project export <project> --target <target> --save <destination>`.
- Select only public Work Products explicitly admitted for the target and prove
  that each is current with valid production receipt evidence.
- Exclude Content Core internals, AI dialogue, candidate history, Operation
  Attempts, private review evidence, raw media, and generated state caches.
- Preserve deterministic normalized output paths and fail closed on stale,
  partial, private, or unreceipted inputs.

## P57-02: Export Manifest, Receipt, and Currentness

Stage Status: PLANNED

- Emit a versioned manifest with target identity, selected Work Product
  identities, exact input hashes, public roles, media types, and output paths.
- Record an export receipt binding the manifest and exported bytes.
- Make source authority, selection, production receipt, or exported-byte
  changes invalidate prior export currentness.
- Let the consumer verify the bundle without reading or reconstructing the
  Document Project's private state.

## P57-03: SimpleModeling.org Target Binding

Stage Status: PLANNED

- Add one typed SimpleModeling.org publication-target binding consuming the
  generic export contract.
- Use Phase 55 site-context-preserving registration for site-aware article
  media rather than a compatibility descriptor.
- Prove a task-private production-site preparation consumes exported public
  Work Products and rejects stale or partial export evidence.
- Keep deployment, public upload, and production-site mutation outside this
  Phase.

## P57-04: Native Publication-Preparation Skill Boundary

Stage Status: PLANNED

- Define the new Document Project publication-preparation skill only as an
  orchestrator of completed native `run`, verification, export, and target
  contracts.
- Do not use `cozy-article-media` or the previous publication-preparation
  workflow as a transitional provider adapter.
- Report the exact missing provider or blocked Work Product when native
  capability is unavailable; do not hand-edit or retrospectively adopt
  evidence.

## Dependencies

- the Phase 49 sequence through Phase 49.3 presentation-semantics workflow
  integration;
- Phase 55 site-context-preserving media registration; and
- the full Phase 56 sequence through Phase 56.2: native provider execution,
  atomic evidence closure, closed executable state, and typed verification
  policy.

## Exclusions

- Compatibility readers, migration modes, bridges, or dual workflow authority.
- Retrofitting earlier articles.
- Export of private project/evidence/history content.
- Manual evidence adoption or fabricated successful attempts.
- Deployment, publication upload, push, or production-site mutation.

## Completion Criteria

Phase 57 completes when a Document Project deterministically exports only
selected current public Work Products with a verifiable manifest and receipt;
the SimpleModeling.org target consumes that bundle without project-internal
knowledge or compatibility staging; the native skill boundary requires no
manual evidence adoption; focused and full Cozy validation pass; and
independent review finds no Current Phase Blocker.

## References

- `docs/phase/phase-57-checklist.md`
- `docs/journal/2026/09/2026-09-07-document-project-production-workflow-greenfield-decision.md`
- `docs/journal/2026/09/2026-09-07-register-site-context-currentness-gap.md`
- `docs/phase/phase-55.md`
- `docs/phase/phase-56.2.md`
- `docs/spec/document-project.md`
