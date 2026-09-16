# SmartDox Article and Core Confirmation Proposal

Date: 2026-09-14
Status: superseded exploratory proposal; non-normative
Tracking: [Phase 60](../phase/phase-60.md), DEV-028

Superseded on 2026-09-14 by the
[confirmation views and make-level dependency proposal](document-project-confirmation-views-and-make-dependencies-proposal.md)
under the revised Phase 60. The text below preserves the earlier article/Core
annotation idea; it is not the current implementation requirement.

## Purpose

Review the actual SmartDox article beside the recursive Content Core logic
tree. The article pane presents the text, headings, lists, figures, and links
rendered from `index.dox`. The tree pane explains how those article blocks
correspond to authored Steps, Claims, local Structure, and child-Step Flow.
Selecting either side reveals the corresponding content on the other side.

The implementation contract will be promoted into design and executable
specifications during Phase 60. This note and its journal are planning inputs,
not an implemented command or normative grammar.

## Existing surfaces and the missing connection

- Phase 58 established locale-independent recursive `content/core.yaml`.
- Phase 58.1 and Phase 58.2 established localized Document/Summary Description
  DSLs and interactive confirmation HTML. The v2 Document projection consumes
  `document.yaml` directly; it does not render an authored `index.dox`.
- `CozyDocumentWorkflow` declares `article.compose`, but the native provider
  currently admits only the legacy `article.render-review` route. That route
  produces review tables rather than normal SmartDox article rendering.
- SmartDox provides its own parser, HTML operation, and AST-to-HTML rendering.
  Cozy should reuse this boundary rather than implement a second article parser.
- The Article 9 `article-review-ja.html` created on 2026-09-14 is a local
  reading-view prototype derived from Document confirmation HTML. It is useful
  for reviewing presentation, but does not demonstrate the proposed
  `index.dox` pipeline. The project `index.dox` is still a scaffold source.

## Responsibility and source chain

```text
Core + Document Description
            |
     Codex authors/revises
            v
     annotated index.dox
          /       \
 Cozy article      SmartDox -> Antora
 confirmation      final site
      + Core
```

Core owns logical meaning. Document Description owns the localized authoring
description. Codex performs the creative article authoring and records the
correspondence when producing `index.dox`. The article source owns the actual
article expression used for review and final publication.

Cozy validates the selected sources, invokes normal SmartDox rendering, and
composes the confirmation workspace. It does not infer an article from Core,
paraphrase the prose, or automatically approve semantic equivalence. The same
`index.dox` reaches Antora; confirmation HTML is never the publication source.

## Proposed article annotation contract

Give each mapped section/block a stable article ID and typed Core references.
References use the accepted Core namespaces, including Step, Claim, Node,
Relation, and Flow-transition references where applicable. One article block
may map to multiple targets; one Core target may appear in multiple blocks.

For illustration only, a possible authoring form is:

```text
#+BLOCK_ID: operation-properties
#+CORE_REF: step=executable-elements; claim=purpose-derived-operation-defaults
用途に応じたデフォルトを用い、個別の要件に合わせて属性を調整します。
```

`BLOCK_ID` and `CORE_REF` above are proposed syntax, not confirmed existing
SmartDox features. Step P600-01 freezes the exact grammar, document-level
Core identity binding, attachment scope, and typed reference shape after
checking SmartDox. Sections, paragraphs, list items, and figures need explicit
attachment rules. Annotations are distinct from glossary terms and RDF term
identifiers; Core traceability does not redefine terminology identity.

The preferred boundary attaches non-visible metadata to meaningful SmartDox
AST blocks. Generic supported span attributes may be evaluated as an interim
representation, but must pass the same parser, HTML, and Antora contract.
Unknown Org directives, stripped comments, and empty anchor spans are not
evidence that traceability survives normal rendering.

SmartDox owns syntax admission, AST metadata, and rendering/export preservation.
Any missing support becomes an explicit upstream handoff. Cozy owns reference
resolution against the admitted Core, provenance validation, and review UI.
Cozy does not add a parallel private parser to compensate for missing upstream
support. Implementation closure requires a tested SmartDox capability/version.

The annotations are the executable article-to-Core mapping. Existing
`document.yaml` coreRefs can guide Codex authoring. If a retained sidecar or
Document binding also declares that mapping, it must agree with the annotations
rather than become an independently editable second mapping authority.

## Workspace behavior to specify

- Show readable article prose as the primary reading surface, alongside an
  expandable recursive Core tree. Provide an article-only reading toggle.
- Preserve nested Step containment, direct-child Flow, and Step-local
  Structure as separate authored relationships. Reuse accepted Phase 58.2
  semantics and labels; ordering alone does not define an edge.
- Selecting a Step highlights and navigates to its mapped article blocks.
  Selecting an article block exposes its typed targets and selects the
  corresponding Step. Multiple-Step correspondence requires an explicit choice
  or visible set rather than an arbitrary first-target selection.
- Show Claim/Node/Relation reference details on demand. Keep audit identities
  and diagnostic data secondary to the article and logic tree.
- Preserve actual SmartDox links, figures, list structure, and section hierarchy.
  Review annotations do not become reader-visible sentences.
- Support keyboard navigation and clear focus/selection. Use the existing
  locale/chrome resource contract instead of embedding Article 9-specific labels
  in the renderer. Desktop reference behavior is the initial acceptance target.

## Validation and currentness to specify

Bind the exact admitted `index.dox` and Core bytes, the annotation schema,
renderer identity/options, and any explicitly consumed Document/label sources.
The article annotation binding records the intended Core ID and identity.
Changing that Core makes the mapping stale until the author updates it.

Reject duplicate article IDs, malformed annotations, wrong target kinds,
unresolved targets, missing rendered anchors, and stale declared source bindings.
Declared unmapped editorial blocks such as references or credits are visible
in coverage diagnostics. Core coverage and deliberate omissions have an
explicit policy; they are not inferred from word matching. Preserve the v2
Document DSL's existing coverage rules unchanged.

Deterministically generate HTML from admitted sources. A failed render or
validation preserves the previous successful output and cannot mark it current
for the new inputs. Reuse applicable existing currentness/attempt contracts;
do not create a competing persisted acceptance authority. UI selection is
review navigation, not semantic approval.

The proposed CLI exposes one explicit article-confirmation operation with
Core and article inputs and a selected output path. Its exact public spelling
and compatibility boundary are frozen in P600-01. Legacy table-review and
Document/Summary confirmation commands retain their existing contracts.

## Acceptance examples

- Editing a mapped `index.dox` paragraph changes the actual article pane;
  the generated text is not copied from `document-confirmation.html`.
- A nested Step with local Structure and child Flow remains navigable without
  flattening those structures or inventing relationships.
- One paragraph mapped to two Claims and a figure mapped to a Relation retain
  stable navigation in both directions.
- A removed Core target or changed bound Core identity causes a precise
  validation failure while retaining the previous successful HTML.
- An isolated SmartDox-to-Antora fixture proves that review metadata remains
  non-visible and article content/links survive. It does not publish a site.
- The first realistic driver is an isolated copy of Article 9 sources with a
  real annotated article. Record its frozen sources and reference HTML before
  comparing generated UI; the current prototype alone is not acceptance.

## Boundary and open decisions

Phase 60 covers the producer contract, rendering connection, validation,
confirmation UI, explicit CLI entry point, and isolated fixtures. Automatic
creative authoring, article PDF, slides, video, production-site preparation,
deployment, publication, and changes to external Article 9 sources stay outside
this Phase. Phase 59 remains the separate summary-slide PDF boundary.

P600-01 resolves native annotation support versus a proven existing attribute
form, exact block attachment/coverage policy, CLI names, renderer binding, and
the reference UI snapshot. Any required SmartDox implementation stays in an
upstream-owned development boundary and is an explicit prerequisite for Cozy
integration acceptance.

## References

- [Document/Summary Description v2 spec](../spec/document-project-document-and-summary-description-v2.md)
- [Phase 58.2](../phase/phase-58.2.md)
- [Phase 59](../phase/phase-59.md)
- [Decision journal](../journal/2026/09/2026-09-14-smartdox-article-and-core-confirmation-decision.md)
- [Phase 60 checklist](../phase/phase-60-checklist.md)
