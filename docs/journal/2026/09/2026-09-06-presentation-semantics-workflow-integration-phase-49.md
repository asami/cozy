# Presentation Semantics Workflow Integration Phase 49

Date: 2026-09-06

## Context

Phase 48 was created to own only the scaffold extension that generates the
Phase-46 presentation-semantics authoring surface for a new Document Project.
The remaining operational plumbing must remain separate so the scaffold change
can be implemented and reviewed without simultaneously changing workflow state,
review routing, and currentness behavior.

## Decision

Create **Phase 49: Document Project Presentation Semantics Workflow
Integration** as the ordered successor to Phase 48.

Phase 49 owns the remaining integration required before SimpleModeling.org
Article 9 can use the new semantic path as a normal Document Project workflow:

- register presentation semantics as a first-class authority Work Product;
- derive missing / authoring-incomplete / invalid / current / stale state;
- integrate the authority into `verify`, `inspect`, and `plan`;
- expose its blockers and next action in the Dashboard;
- expose the closed Phase 46.1 integrated cross-media confirmation through a
  normal public Document Project review/operation route; and
- propagate Content Core and presentation-semantic identity changes into
  dependent projection and confirmation stale/currentness state.

## Boundary

Phase 49 does not own scaffold generation; that is Phase 48. It consumes the
Phase-48 generated authoring surface.

Phase 49 also does not redefine the Phase 46 semantic schema, introduce a
second semantic validator, or implement a second confirmation renderer. The
closed Phase 46 `DP-SEM-*` validation and Phase 46.1 projection / confirmation /
receipt / semantic-coverage implementations remain the technical authorities.

## Article 9 relationship

Article 9 remains the first intended production use after the two integration
Phases.

The ordered path is:

```text
Phase 48 scaffold
  -> Phase 49 workflow integration
  -> Article 9 production
```

Phase 49 may use an Article-9-shaped local driver to prove the path, but it does
not need to complete or publish the article to close.

## Planning input

- `docs/notes/document-project-presentation-semantics-workflow-integration-specification-proposal.md`
- `docs/notes/document-project-presentation-semantics-operational-integration-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-article-9-presentation-semantics-operational-integration.md`
- `docs/phase/phase-48.md`
