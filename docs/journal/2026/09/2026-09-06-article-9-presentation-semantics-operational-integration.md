# Article 9 Presentation Semantics Operational Integration

Date: 2026-09-06

## Trigger

The user plans to create SimpleModeling.org Article 9 with the new Document
Project presentation-semantics capability introduced after the Article 8
confirmation-HTML failure.

Before starting Article 9, the current Cozy state was reviewed. Phase 46 and
Phase 46.1 have closed the typed Story Flow / Explanation Structure semantic
kernel, deterministic slide/video projections, integrated confirmation HTML,
receipts, currentness, and semantic-coverage checks. However, those Phases
intentionally did not change Document Project scaffold or CLI surfaces.

The remaining gap is therefore operational integration rather than another
semantic-model redesign.

## Decision

Make **Document Project Presentation Semantics Operational Integration** the
next Cozy development item.

The work is split so the scaffold entry point is implemented first as
**Phase 48: Document Project Presentation Semantics Scaffold**. Phase 47 is
already reserved for CML Composite StateMachine and Workflow Modeling and is
unrelated to this work.

Phase 48 owns only the scaffold and generated presentation-semantics authoring
surface. Workflow/state registration, inspect/plan/verify/dashboard integration,
and public cross-media confirmation routing remain successor work under the
same development item.

The implementation must reuse the closed
`cozy.content-core.presentation-semantics.v2` contract and the Phase 46.1
cross-media projection kernel. It must not create a new semantic schema or a
second confirmation renderer.

## Scaffold direction

For an applicable profile, scaffold creates a sibling semantic authoring
surface such as:

```text
content/
|- core-<language>.yaml
`- presentation-semantics-<language>.yaml
```

The semantic file binds the exact current Content Core identity and provides
the strict Phase 46 authoring structure. Scaffold creates the workspace, not
the semantic decisions.

A fresh project may therefore be `authoring incomplete`. Cozy must not invent
Story Flow, Explanation Structures, reader text, or logical graphs merely to
make strict validation pass.

## Workflow direction

Presentation Semantics becomes a first-class authority Work Product between
Content Core and article/slide/video production in successor work after Phase
48. Existing `inspect`, `plan`, `verify`, and Dashboard surfaces should then
expose its readiness, stale state, diagnostics, and next action.

The closed Phase 46 `DP-SEM-*` diagnostics remain authoritative once semantic
content is present. No permissive compatibility validator is introduced.

The Phase 46.1 integrated cross-media confirmation also becomes reachable
through a normal Document Project operation/review surface in successor work.
It remains distinct from the article-specific `article-review.html`.

## Article 9 driver

SimpleModeling.org Article 9 is the first intended real production driver.
Article 8 remains the defect-discovery/reference case and does not need to be
retrofitted merely to validate the successor workflow.

Phase 48 uses an Article 9-style project only to prove scaffold readiness. Full
Article 9 acceptance should later prove the complete path:

```text
scaffold
  -> Content Core authoring
  -> Story Flow / Explanation Structure authoring
  -> strict semantic validation
  -> cross-media confirmation
  -> article / slide / video production
  -> semantic coverage and currentness
  -> human review / revision
```

The practical success criterion is that Codex can produce and review Article 9
through Cozy without creating an out-of-band hand-built confirmation HTML.

## Non-goals

This next item does not reopen Phase 46/46.1, change their schemas, automate
semantic acceptance, add final renderer capabilities, publish content, or
migrate Article 8 and earlier articles.

## Planning input

Detailed proposal:

`docs/notes/document-project-presentation-semantics-operational-integration-specification-proposal.md`

Phase 48 records:

- `docs/phase/phase-48.md`
- `docs/phase/phase-48-checklist.md`
