# Phase 48: Document Project Presentation Semantics Scaffold

Status: PLANNED

Plan date: 2026-09-06

Development item: DEV-018

Predecessors: Phase 46 and Phase 46.1

## Goal

Extend `cozy document-project scaffold` so a newly created Document Project can
start authoring against the closed Phase 46
`cozy.content-core.presentation-semantics.v2` contract without introducing a
new semantic schema, permissive aliases, or placeholder-success semantics.

Phase 48 owns only the scaffold entry point and generated authoring surface.
Workflow/state integration, inspect/plan/verify/dashboard integration, and a
public cross-media confirmation operation remain successor work under DEV-018.

## P48-01: Scaffold Contract

Stage Status: PLANNED

- Keep the existing `document-project scaffold` command grammar.
- Do not add a semantic-model selection option.
- Reuse the existing profile model.
- Preserve the hidden status of `simplemodeling-org` and
  `simplemodeling-org-video` unless separately authorized.
- For an applicable profile, generate
  `content/presentation-semantics-<language>.yaml` beside
  `content/core-<language>.yaml`.
- Use exactly `cozy.content-core.presentation-semantics.v2` and the existing
  Phase 46 projection-policy vocabulary.
- Bind the exact scaffolded v1 Content Core identity using the accepted Phase
  46 byte-identity rule.
- Preserve deterministic output and existing safe scaffold path behavior.

## P48-02: Authoring-Incomplete Skeleton

Stage Status: PLANNED

The governing rule is:

```text
Scaffold creates the semantic workspace, not the semantics.
```

- Do not invent meaningful Story Steps, Story Transitions, Explanation
  Structures, reader-facing text, Logical graphs, claims, or acceptance.
- If the strict Phase 46 schema cannot yet be semantically valid, generate an
  intentionally authoring-incomplete surface rather than fake valid content.
- Do not emit retired renderer aliases such as `intent`, `media`, or
  `emphasis` as compatibility shortcuts.
- Do not weaken the existing `DP-SEM-*` validator or add a scaffold-only
  permissive semantic reader.

## P48-03: Scaffold Evidence

Stage Status: PLANNED

Required executable evidence:

- Existing standard/bok scaffold behavior remains stable except for an
  explicitly selected presentation-semantics participation rule.
- An applicable profile scaffold contains the strict sibling semantic
  authority.
- The generated Core binding identity matches the exact generated Core bytes.
- Repeated generation with identical inputs is deterministic.
- Existing unsafe parent/path and existing-file protections remain unchanged.
- A representative SimpleModeling.org Article 9-style project can be
  scaffolded without hand-writing the initial presentation-semantics file.

Article 9 is a scaffold-readiness driver only in this Phase. End-to-end Article
9 production is not claimed.

## Exclusions

- Registering `presentation-semantics` as a first-class workflow Work Product.
- New Workflow Instance state for incomplete/stale presentation semantics.
- `inspect`, `plan`, `verify`, or Dashboard integration.
- Public routing to the Phase 46.1 cross-media confirmation HTML.
- Document Project-level receipt/currentness propagation for this new authority.
- Automatic semantic authoring or acceptance.
- Changes to `cozy.content-core.v1` or the Phase 46 semantic schema.
- PowerPoint, PDF, or video renderer expansion.
- Article 8 migration or retrofit.
- Publication, deployment, registration, upload, push, or external mutation.

## Completion Criteria

Phase 48 completes when an applicable Document Project scaffold deterministically
creates the strict Phase 46 presentation-semantics authoring surface, correctly
binds the generated Content Core identity, avoids fabricated semantic content,
and passes focused plus required full Cozy validation without changing the
closed Phase 46/46.1 contracts.

## Primary References

- `docs/notes/document-project-presentation-semantics-operational-integration-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-article-9-presentation-semantics-operational-integration.md`
- `docs/spec/document-project-presentation-semantics.md`
- `docs/spec/document-project.md`
- `docs/phase/phase-46.md`
- `docs/phase/phase-46.1.md`
