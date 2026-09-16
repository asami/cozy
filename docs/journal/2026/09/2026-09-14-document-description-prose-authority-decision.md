# Document Description Prose Authority and SmartDox Article Representation Decision

Date: 2026-09-14
Related development item: DEV-028 / Phase 60

## Conversation context

The user asked whether the Document Description DSL contains exactly the
same prose as the final article. The discussion distinguished the intended
authoring authority from the current Article 9 implementation state:
`content/ja/document.yaml` contains article prose, while `index.dox` is still
a scaffold, so final-article equivalence has not yet been demonstrated.

The user accepted the direction and requested this journal record:

> Cozyのjournalにも文書記述DSLが正本というのを記録しておいて。
> index.doxはSmartDox向けDSLという位置付けね？

## Recorded direction

The **Document Description DSL is the authoritative source of article prose
and document organization**. In the current Document Project convention,
this is `content/<locale>/document.yaml`. It defines the complete localized
document rather than an HTML-output IR or a formatting procedure.

`index.dox` is the **SmartDox-targeted article representation, written in the
SmartDox DSL**. It represents the accepted Document Description for the
SmartDox article/render/export route, including the subsequent Antora route.
It is a derived representation, not an independent prose authority.

The source responsibilities remain distinct:

- `content/core.yaml`: the locale-independent recursive logic tree and its
  logical meaning.
- `content/<locale>/document.yaml`: the localized article organization,
  headings, paragraphs, lists, and other authored prose.
- `index.dox`: SmartDox syntax, supported annotations, term markup, figure
  references, and the article representation of that accepted prose.
- `content/<locale>/summary.yaml`: the deliberate concise selection for
  summary explanations; its wording need not duplicate the full article.

## Prose preservation and editing loop

The intended article conversion preserves the accepted headings and prose.
SmartDox syntax, escaping, layout, supported correspondence annotations,
glossary expansion, and automatically supplied site material can make the
files or rendered surfaces differ without establishing a second authored text.

For article wording feedback, revise the Document Description and reflect
the accepted wording into `index.dox`. If an article edit is made directly
during authoring, reconcile its prose back into the Document Description
before treating the revision as accepted. Core changes are needed when the
logical meaning changes; stylistic wording changes alone do not require a
Core rewrite.

SmartDox-specific syntax or annotation repairs may remain in `index.dox`
when they preserve the accepted prose and logical correspondence. The same
article representation can then support actual article/Core review HTML and
the publication route. Summary slides and video dialogue remain separately
reviewed adaptations rather than replacements for the prose authority.

## Clarification of the earlier Phase 60 handoff

The [earlier article/Core confirmation decision](2026-09-14-smartdox-article-and-core-confirmation-decision.md)
allowed wording-only edits to remain in the article. This later conversation
clarifies that direction: unchanged Core grounding permits a wording-only
revision, but its accepted prose is also reflected in the Document Description.
It is not left solely in `index.dox` as a second authority.

The [Phase 58.1 DSL decision](2026-09-12-phase-58.1-document-summary-description-dsl-decision.md)
already assigned complete localized organization and prose to the Document
Description. This record makes that responsibility explicit for the final
SmartDox article and the companion article-authoring skill handoff.

## Follow-up and execution scope

Use this record when reconciling the Phase 60 design/spec and companion skill
contracts. Article conversion and review should demonstrate preservation of
the accepted Document Description prose and stable correspondence to Core.
The creative or deterministic mechanism used to produce `index.dox` is a
separate implementation question; this decision does not claim that Cozy
already provides automatic article composition or prose-equivalence checks.

This is a chronological decision record, not a new executable contract or
implementation acceptance. Only this journal file was added. No product code,
existing journal, phase/checklist, skill, article, media, Git stage/commit,
publication, deployment, or parent-task workspace state was changed.
