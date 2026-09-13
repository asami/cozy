# Phase 60: SmartDox Article and Core Confirmation

Status: PLANNED
Plan recorded: 2026-09-14
Development item: DEV-028
Plan Gate: PENDING; contracts and upstream capability evidence to be frozen in P600-01

## Purpose and boundary

Provide a normal SmartDox article review workspace that displays the actual
`index.dox` article beside its recursive Core logic tree, using explicit
annotations for bidirectional correspondence.

Codex owns creative article authoring. Cozy owns source validation, the normal
SmartDox rendering connection, correspondence validation, confirmation UI, and
an explicit CLI entry point. SmartDox owns annotation syntax/AST/export support;
missing upstream support is an integration prerequisite, not a private Cozy
parser implementation.

This is a new boundary after closed Phase 58.2. It does not replace ongoing
Phase 59, change its priority, or reopen accepted Document/Summary projections.
The Article 9 driver is an isolated fixture, not authority to edit or publish
SimpleModeling.org.

## Planning inputs and closure ledger

- [Proposal](../notes/document-project-smartdox-article-confirmation-proposal.md)
- [Chronological decision](../journal/2026/09/2026-09-14-smartdox-article-and-core-confirmation-decision.md)
- [Checklist](phase-60-checklist.md): sole completion ledger
- [Predecessor Phase 58.2](phase-58.2.md)
- [Independent Phase 59](phase-59.md)

The proposal is non-normative. P600-01 promotes the admitted contract into
design/spec documentation and executable specifications before code changes.

## Subphase 60A: Article confirmation vertical slice

| Step | Observable outcome | Status | Closure basis |
| --- | --- | --- | --- |
| P600-01 | Frozen source/annotation/renderer/CLI contracts and reference UI; explicit upstream dependency admission | OPEN | Checklist P600-01 |
| P600-02 | Normal SmartDox article rendering with validated, stable typed Core correspondence | OPEN | Checklist P600-02 |
| P600-03 | Readable article/Core workspace with recursive structure and bidirectional navigation | OPEN | Checklist P600-03 |
| P600-04 | Explicit operable CLI and deterministic, currentness-aware failure-preserving output | OPEN | Checklist P600-04 |
| P600-05 | Isolated driver, upstream compatibility evidence, full validation and independent Phase review closure | OPEN | Checklist P600-05 |

## Acceptance and exclusions

Acceptance is an actual annotated `index.dox` rendered through normal SmartDox,
with working article-to-Core navigation, strict diagnostics, repeatable output,
and an isolated Antora compatibility check. The existing HTML prototype is a
reference candidate, not completed integration evidence.

Automatic creative article generation, PDF/slides/video generation, production
site integration/preparation, publication, deployment, upload, push, and edits
to external driver projects are excluded. A future publication consumes the
same article source, not the confirmation HTML.

## Companion Codex skill

`cozy-document-project-article` was created and installed on 2026-09-14 from
the decision journal's skill-creation handoff. Its source is maintained in
`codex-customizations/skills/cozy-document-project-article/`; the installed
entry point is `$cozy-document-project-article`.

The skill accepts a Document Project, explicit locale, and authoring request
or review feedback. Codex authors/revises the actual `index.dox` from Core and
localized Document Description, maintaining stable article IDs and supported
typed Core correspondence. Semantic feedback updates Core and the affected
Document Description before deriving the article; wording-only feedback does
not require a Core rewrite.

Once the product contract is available, the skill delegates deterministic
article/Core confirmation validation and rendering to Cozy through
`cozy-command-execution`. Ordinary HTML opening uses `web-local-preview` with
verification-only lifetime and owned-server cleanup after the requested browser
checks. Only explicitly authorized continuous preview uses
`cozy-document-project-preview` and its owned-session refresh/lifetime contract.
Article authoring or HTML opening alone does not authorize a persistent server.
The skill does not add a parser, renderer, server,
annotation grammar, or competing review/acceptance authority. Article 9 is a
validation driver rather than a hard-coded project. Media generation and
publication operations are separate requests.

Skill metadata and structure have received static checks only. The user
deferred integrated authoring, CLI, rendering, and browser confirmation until
Cozy implementation is complete. At that point, adopt the promoted P600-01
contract and verify the selected installed SmartDox/Cozy capability/version;
do not assume proposed `BLOCK_ID`/`CORE_REF` syntax or legacy table review is
the implemented article confirmation operation. The skill's
`references/integration-handoff.md` retains the deferred verification scenarios.

Skill creation does not close any P600 Step or constitute product integration
acceptance. This companion workflow performs creative authoring outside Cozy's
automatic-generation implementation scope; it does not change this Phase's
completion ledger or existing exclusions.

## Current handoff

All implementation Steps are OPEN. Begin with P600-01; record the exact tested
SmartDox capability/version and any upstream handoff before integration closure.
Planning does not assert a supported annotation grammar or runnable new command.
