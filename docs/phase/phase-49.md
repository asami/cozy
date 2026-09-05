# Phase 49: Document Project Presentation Semantics Workflow Integration

Status: PLANNED

Plan date: 2026-09-06

Development item: DEV-018

Predecessors: Phase 46, Phase 46.1, and Phase 48

## Goal

Connect the Phase-48 presentation-semantics authoring surface to the normal
Document Project workflow by making the existing Phase-46 semantic authority a
first-class Work Product, exposing its readiness and diagnostics through normal
Document Project surfaces, routing the accepted Phase-46.1 cross-media
confirmation through a public operation, and projecting stale/currentness
through the existing identity graph.

Phase 49 owns operational integration only. It does not redefine the semantic
schema, scaffold contract, or cross-media renderer.

## P49-01: Workflow Work Product and State

Stage Status: PLANNED

- Register `presentation-semantics` as a first-class `authority` Work Product in
  the immutable `document-production` workflow.
- Bind its direct dependency to `content-core` and its downstream dependencies
  to article/slide/video semantic production and cross-media confirmation.
- Keep workflow definition and provider bindings Cozy-owned; do not copy the
  DAG into `document-project.yaml`.
- Derive deterministic states sufficient to distinguish `missing`,
  `authoring-incomplete`, `invalid`, `current`, and `stale` semantic authority.
- Do not treat `authoring-incomplete` as successful Phase-46 validation.
- Do not create accepted semantic identity, coverage success, or confirmation
  receipt before strict semantic validation succeeds.

## P49-02: Verify, Inspect, Plan, and Dashboard

Stage Status: PLANNED

### Verify

- Route the selected semantic authority through the existing Phase-46 loader
  and validator.
- Preserve the closed `DP-SEM-*` diagnostics.
- Add no permissive validator, alias reader, or scaffold-specific semantic
  compatibility path.
- When semantics validate, prove admission to the Phase-46.1 projection
  boundary and report projection/coverage failure explicitly.

### Inspect

Expose at least:

- schema identity;
- Content Core binding/currentness;
- semantic state;
- Story Step count;
- Story Transition count;
- Explanation Structure count;
- slide/video projection availability; and
- semantic coverage state when available.

Unavailable data must remain unavailable rather than being replaced by
placeholder values.

### Plan

- Place Presentation Semantics after Content Core and before dependent
  article/slide/video semantic production.
- Block dependent work when semantics are missing, incomplete, invalid, or
  stale according to the accepted workflow rules.
- Project deterministic next-action guidance without executing it.

### Dashboard

- Treat Presentation Semantics as a normal production stage.
- When it is the first blocker, expose the exact authority and required next
  authoring action.
- Do not generate or mutate semantic content from the Dashboard.

## P49-03: Public Cross-Media Confirmation Route

Stage Status: PLANNED

- Expose the accepted Phase-46.1 integrated confirmation through a normal
  Document Project operation/review surface.
- Prefer extension of the existing review command family; exact grammar is a
  Phase-49 design output.
- Callers must not need package-private Scala implementation APIs.
- Keep article-specific review and shared cross-media semantic confirmation
  distinct Work Products.
- Recommended default outputs are:

```text
target/document-project/presentation-confirmation.html
target/document-project/presentation-confirmation.receipt.yaml
```

- Reuse the existing Phase-46.1 renderer, projection identity, receipt,
  currentness, and semantic-coverage implementation.
- Do not implement a parallel confirmation renderer.

## P49-04: Currentness and Stale Propagation

Stage Status: PLANNED

At minimum preserve these dependency effects:

```text
Content Core change
  -> presentation-semantics stale
  -> dependent article/slide/video semantic projections stale
  -> presentation-confirmation stale
```

and:

```text
presentation-semantics change
  -> dependent projection identity changes
  -> prior confirmation receipt stale
```

- No automatic write-back may rewrite stale authorities merely to make them
  current.
- Receipt currentness remains distinct from semantic completeness.
- A current receipt must not substitute for Phase-46.1 coverage verification.

## P49-05: Article 9 Operational Driver

Stage Status: PLANNED

Use an Article-9-shaped local or safely authorized driver to prove the normal
Document Project route:

```text
Phase-48 scaffolded semantic workspace
  -> semantic authoring
  -> verify
  -> inspect / plan / dashboard
  -> public cross-media confirmation
  -> stale/currentness propagation
```

Acceptance must prove that an out-of-band hand-built confirmation HTML is not
required.

Full editorial completion, publication, deployment, upload, or push of Article
9 is not required for Phase 49 closure.

## Exclusions

- Scaffold generation or generated semantic skeleton changes owned by Phase 48.
- Changes to `cozy.content-core.v1`.
- Changes to `cozy.content-core.presentation-semantics.v2`.
- New or permissive semantic validators.
- A second cross-media renderer or receipt implementation.
- Automatic Story Flow / Explanation Structure authoring or acceptance.
- New PowerPoint, PDF, or video renderer capability.
- Article 8 migration or retrofit.
- Article-9-specific profile creation.
- Publication, deployment, registration, upload, push, or external mutation.

## Completion Criteria

Phase 49 completes when a Phase-48 scaffolded Document Project can use
presentation semantics through the normal immutable workflow, derive and expose
its readiness/currentness state, validate it through the accepted Phase-46
boundary, inspect and plan dependent work, reach the Phase-46.1 integrated
confirmation through a public Document Project route, and propagate stale state
deterministically through exact identities with semantic coverage kept separate
from receipt currentness.

## Primary References

- `docs/phase/phase-48.md`
- `docs/notes/document-project-presentation-semantics-workflow-integration-specification-proposal.md`
- `docs/notes/document-project-presentation-semantics-operational-integration-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-presentation-semantics-workflow-integration-phase-49.md`
- `docs/journal/2026/09/2026-09-06-article-9-presentation-semantics-operational-integration.md`
- `docs/spec/document-project-presentation-semantics.md`
- `docs/spec/document-project.md`
- `docs/phase/phase-46.md`
- `docs/phase/phase-46.1.md`
