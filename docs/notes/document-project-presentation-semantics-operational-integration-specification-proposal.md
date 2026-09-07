# Document Project Presentation Semantics Operational Integration Specification Proposal

Date: 2026-09-06

Status: specification proposal; non-normative; successor development input

## Purpose

Connect the closed Phase 46/46.1 typed presentation-semantics and cross-media
projection kernel to the normal Document Project authoring workflow before the
SimpleModeling.org Article 9 production starts.

The implementation must reuse the accepted contracts rather than introduce a
new semantic schema:

- `cozy.content-core.v1`;
- `cozy.content-core.presentation-semantics.v2`;
- `cozy.content-core.projection-policy.v1`;
- `cozy.explanation-composition.v1`;
- Phase 36 Logical/Visual values;
- Phase 37 Composition/Plan values; and
- the Phase 46.1 cross-media projection, confirmation HTML, receipt, and
  semantic-coverage kernel.

The central principle is:

```text
Scaffold creates the semantic workspace, not the semantics.
```

## Problem

Phase 46 and Phase 46.1 intentionally closed the typed semantic and projection
kernel without changing the Document Project descriptor, CLI, or scaffold.
The kernel can validate `CozyDocumentPresentationSemantics.Validated` and
project it into slide pages, storyboard scenes, integrated confirmation HTML,
receipts, and semantic coverage, but a newly scaffolded Document Project does
not yet expose that path as its normal authoring workflow.

Article 9 should be the first new production driver that starts with this
stronger contract. It should not need a hand-built confirmation HTML or a
project-private invocation of package-local Cozy implementation code.

## Scaffold extension

Keep the existing command shape:

```text
cozy document-project scaffold <project-id> \
  --profile <profile> \
  --language <language> \
  --workspace <kind> \
  --parent <directory>
```

Do not add a new semantic-model selection option. A profile that participates
in the presentation-semantics workflow causes scaffold to create the authoring
surface required by the already accepted Phase 46 contract.

For SimpleModeling.org Article 9, continue to use the existing hidden
`simplemodeling-org-video` profile. This proposal does not make that profile a
public scaffold/help selection and does not require a new Article-9-specific
profile.

## Generated authoring surface

The intended package shape includes the existing v2 artifacts plus the strict
presentation-semantics sibling authority:

```text
<project>.dox/
|- document-project.yaml
|- content/
|  |- core-<language>.yaml
|  `- presentation-semantics-<language>.yaml
|- index.dox
|- presentation/
|  `- visual-pages.yaml
|- infographic/
|- video/
`- target/
```

`content/presentation-semantics-<language>.yaml` uses exactly
`cozy.content-core.presentation-semantics.v2`. The scaffold must not extend
`cozy.content-core.v1`, invent a parallel page grammar, or introduce renderer
aliases.

The generated surface should contain the fixed schema/binding structure needed
for authoring, including the exact current v1 Core binding and the existing
projection-policy vocabulary. It must not invent meaningful Story Steps,
Explanation Structures, claims, reader text, logical graphs, or review
acceptance merely to make the strict semantic validator succeed.

Because the Phase 46 contract requires nonempty valid structures, a freshly
scaffolded semantic authoring surface may be intentionally incomplete. This is
an expected authoring state, not successful semantic validation and not a
placeholder-success path.

## Workflow integration

Add a first-class Document Project Work Product representing the existing
presentation-semantics authority. Recommended stable id and role:

```text
presentation-semantics : authority
```

Its direct upstream semantic dependency is `content-core`. Article, slide,
video, and cross-media review work consume its accepted typed projection rather
than reparsing independent loose representations.

Conceptually:

```text
content-core
    |
    v
presentation-semantics
    |------------------+------------------+
    v                  v                  v
article             slides             video
    \                  |                  /
     +-----------------+-----------------+
                       v
          cross-media confirmation
```

The immutable `document-production` workflow remains the owner of the Work
Product and operation graph. Do not copy workflow definitions into
`document-project.yaml`.

## Authoring-incomplete state

Document Project inspection/state derivation must distinguish a freshly
scaffolded but incomplete semantic authoring surface from malformed or stale
accepted semantics.

The user-facing state should communicate approximately:

```text
Presentation Semantics
  status: authoring incomplete
  next: define Story Flow and Explanation Structures
```

This state must not create an accepted semantic identity, current projection
receipt, or successful coverage result.

Once sufficient content exists, the existing strict `DP-SEM-*` validation is
the authority. Do not introduce a second permissive validator.

## Core binding and stale behavior

Scaffold binds the exact generated `content/core-<language>.yaml` bytes through
the Phase 46 `contentCore.identity` rule. Later Core changes must not silently
rewrite presentation semantics. They make the dependent semantic authority and
projections stale until an explicit authoring update rebinds and revalidates
them.

## Inspect, plan, verify, and dashboard

Extend the existing Document Project surfaces rather than add a separate
management tool.

`inspect` should expose at least:

- presentation-semantics schema and Core binding state;
- Story Step and transition counts when valid;
- Explanation Structure count;
- slide/video projection availability; and
- semantic-coverage status.

`plan` should show the dependency ordering and block article/slide/video
projection when presentation semantics are incomplete or invalid.

`verify` should route the semantic authority through the existing Phase 46
loader/validator and preserve `DP-SEM-*` diagnostics. When semantics are valid,
it should also prove that the Phase 46.1 projection boundary is available.

The Dashboard should treat Presentation Semantics as a normal production stage
and recommend authoring it when it is the first blocker. It must not generate
semantic content or execute the suggested edit.

## Cross-media confirmation operation

Expose the closed Phase 46.1 integrated confirmation path through Document
Project workflow. The preferred operation identity is conceptually
`presentation.render-confirmation`; the exact public command may use the
existing review surface, for example:

```text
cozy document-project review <project> --kind presentation
```

The selected public grammar is a design output, but it must not require callers
to invoke package-private Scala objects.

Default generated artifacts should be distinct from the existing
article-specific review projection, for example:

```text
target/document-project/presentation-confirmation.html
target/document-project/presentation-confirmation.receipt.yaml
```

The distinction is semantic:

- `article-review.html` reviews the article-specific expression;
- the cross-media confirmation reviews shared Story Flow and Explanation
  Structures plus their article/slide/video mappings.

Reuse the Phase 46.1 renderer, identity, receipt, currentness, and semantic
coverage behavior. Do not implement a second confirmation renderer.

## Codex authoring contract

A scaffolded project should make the intended authoring order obvious to a
Codex task or a human author:

```text
1. Content Core
2. Story Flow
3. Explanation Structures
4. semantic validation / cross-media confirmation
5. article expression
6. slide/video projection
7. human review
8. explicit revision
```

Cozy supplies structure, validation, projection, coverage, currentness, and
review evidence. Codex or a human supplies semantic authoring decisions. No AI
response becomes authority merely because it was generated.

## Article 9 acceptance driver

SimpleModeling.org Article 9 is the first intended real production driver for
this operational integration.

Acceptance should prove that a newly scaffolded Article 9 Document Project can
complete the path from semantic authoring through integrated confirmation
without a hand-built HTML workaround. At minimum verify:

- no unsupported semantic value becomes successful placeholder content;
- the complete Story Flow is reviewable;
- every Explanation Structure is reviewable;
- article/slide/video semantic mappings are visible;
- Core changes propagate stale state;
- semantic changes propagate to projection and confirmation identities;
- stale review evidence is not treated as current;
- semantic coverage proves every selected Story Step and Structure; and
- Codex does not need to create an out-of-band confirmation HTML.

Article 8 remains the defect-discovery/reference case. This work does not
retrofit or migrate Article 8 or earlier articles merely to prove the new
workflow.

## Non-goals

- changing the closed Phase 46 semantic schema;
- changing `cozy.content-core.v1`;
- automatic semantic authoring or acceptance;
- inventing placeholder Story Flow or Explanation Structures;
- a new SimpleModeling.org profile solely for Article 9;
- final PowerPoint, PDF, or video renderer expansion;
- publication, deployment, registration, upload, or push; or
- migration of Article 8 or earlier articles.

## Proposed development boundary

Create the next Cozy development item for **Document Project Presentation
Semantics Operational Integration**. It should own scaffold integration,
first-class workflow/state exposure, inspect/plan/verify/dashboard integration,
and the public Document Project route to the already closed Phase 46.1
cross-media confirmation kernel.

Do not reopen Phase 46 or Phase 46.1. Their semantic and projection contracts
are predecessors of this operational integration work.
