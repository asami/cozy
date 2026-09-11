# Document Project Document and Summary Description DSL Proposal

Date: 2026-09-12

Status: exploratory proposal; non-normative; Phase 58.1 planning input

## Purpose

Explore two reader-facing authoring DSLs layered on the recursive Content Core
introduced by Phase 58:

1. a Document Description DSL for the complete prose document; and
2. a Summary Description DSL for a summary-slide-level condensation.

The proposal separates semantic authoring from HTML formatting. It uses the
real SimpleModeling.org Article 9 Document Project as the first driver.

## Problem

Phase 58 correctly made `content/core.yaml` the locale-independent recursive
logic-tree authority, but its `format-ja.yaml` combines three different
concerns:

- localized wording for Steps, claims, and nodes;
- HTML review chrome such as headings and navigation labels; and
- binding and identity information needed by one projection command.

That file is therefore closer to a localized HTML-projection input than a DSL
for writing a document. It cannot express section organization, paragraphs,
lists, examples, notes, terminology use, or exact prose-to-Core traceability.
It also provides no distinct authority for the concise content selected for
summary slides.

The Article 9 Phase 58 driver further reduces every local Structure to
`sequence` and every Relation and child-Flow transition to `next`. Although
the reused catalog admits other logical and relation meanings, the acceptance
driver and generic HTML projection do not demonstrate that the authored
meaning is retained and made visually understandable.

## Proposed authority layout

```text
content/
  core.yaml
  ja/
    document.yaml
    summary.yaml
  en/
    document.yaml
    summary.yaml
```

The filenames express semantic roles rather than output formats or locale
suffixes. Each localized DSL declares and validates its own locale; the
directory is an organization convention and must not silently override the
declared locale.

Conceptually:

```text
                         content/core.yaml
                     recursive logic-tree authority
                        /                     \
                       v                       v
      content/<locale>/document.yaml   content/<locale>/summary.yaml
          complete document prose       selected concise explanation
                       \                       /
                        v                     v
                 medium-specific deterministic projections
             SmartDox / HTML / article PDF / summary-slide PDF
```

## Document Description DSL

The provisional schema identity is `cozy.document-description.v1`. The DSL
should describe the document rather than an HTML page. It needs at least:

- an exact Core id and byte identity;
- one explicit BCP-47 locale;
- a stable document identity, title, and optional abstract;
- recursive sections with stable identities and headings;
- ordered content blocks such as paragraph, list, example, note, and logical
  structure reference;
- exact references from sections and blocks to Core Steps, claims, nodes,
  Relations, and child Flow where used;
- terminology and source references where the selected profile requires
  them; and
- closed validation that rejects unknown Core references or untraceable
  declared content.

Illustrative shape:

```yaml
schema: cozy.document-description.v1
id: application-modeling-document
locale: ja
core:
  id: application-modeling
  identity: sha256:...
document:
  title: SimpleModeling 第9回 アプリケーションモデリング
  sections:
    - id: application-foundation-section
      coreRefs:
        steps: [application-foundation]
        claims: [application-modeling-purpose]
      heading: アプリケーションモデリングの目的
      blocks:
        - id: application-purpose-paragraph
          kind: paragraph
          claimRefs: [application-modeling-purpose]
          text: |
            アプリケーションモデリングは……
        - id: application-foundation-logic
          kind: logical-structure
          stepRef: application-foundation
```

Exact field names and block vocabulary remain design/specification work.
Coordinates, CSS, fonts, HTML element names, PDF pagination, and PowerPoint
shape identities do not belong in this DSL.

## Summary Description DSL

The provisional schema identity is `cozy.summary-description.v1`. A Summary
Description is not an automatically truncated Document Description and not a
slide-renderer IR. It records the deliberate selection, order, wording, and
emphasis used for a concise explanation.

It needs at least:

- exact Core and Document Description identities;
- one explicit BCP-47 locale;
- a stable summary identity and title;
- ordered summary units with concise headings and messages;
- exact Step, claim, node, Relation, and Flow references selected by each
  unit;
- explicit emphasis without physical layout coordinates; and
- deterministic coverage and stale-input checks against both upstream
  authorities.

One summary unit may normally project to one summary slide, but pagination is
a projection decision. The Summary Description remains usable by another
concise medium without becoming a PPTX, PDF, video, or infographic IR.

Illustrative shape:

```yaml
schema: cozy.summary-description.v1
id: application-modeling-summary
locale: ja
core:
  id: application-modeling
  identity: sha256:...
document:
  id: application-modeling-document
  identity: sha256:...
summary:
  title: アプリケーションモデリング
  units:
    - id: use-case-realization-summary
      stepRefs:
        - use-case-realization
        - collaboration-and-interaction
        - executable-elements
      claimRefs:
        - use-case-realization
      heading: ユースケース実現モデル
      message: 協調と相互作用から実行可能な要素を導く。
```

## Projection and locale boundary

Project-authored reader wording belongs in Document or Summary Description.
Renderer-owned chrome belongs in a Cozy locale resource or an explicitly
selected presentation profile. CSS, layout, navigation, print rules, and
medium-specific rendering parameters remain below the two authoring DSLs.

The Phase 58 `format-ja.yaml` remains valid historical vertical-slice input.
Phase 58.1 should not rename it in place, infer a compatibility conversion, or
make it a second authority. The new DSL route is admitted explicitly and any
later retirement or migration is a separate decision.

## Logic and relationship acceptance

The real Article 9 driver must use the available logical patterns and typed
Relations where they express actual meaning. Coverage must not be satisfied
by converting every Structure and transition to `sequence` and `next`.

At minimum, acceptance should demonstrate semantically warranted examples of:

- explanation order between child Steps;
- mapping from a use-case-oriented source to application-model targets;
- dependency or enablement where one model element requires another; and
- distinct visual treatment of Step Flow and Step-local meaning.

The renderer must not reduce different patterns to identical pill lists with
only raw internal identifiers distinguishing them. Localized reader-facing
labels and pattern-compatible visual projections are required.

## First vertical slice

Phase 58.1 should end with the Article 9 Core, Japanese Document Description,
and Japanese Summary Description admitted as three exact authorities and with
two self-contained review HTMLs:

1. a document review showing the composed prose and Core traceability; and
2. a summary review showing summary units and their selected Core meaning.

SmartDox generation, public article/PDF production, PPTX generation, video,
infographic, publication, deployment, upload, and migration of other Document
Projects remain outside this first slice.
