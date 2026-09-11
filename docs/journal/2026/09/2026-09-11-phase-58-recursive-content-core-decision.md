# Phase 58 Recursive Content Core Decision

Date: 2026-09-11

## Trigger

SimpleModeling.org Article 9 used the completed Document Project Presentation
Semantics path to produce a confirmation HTML. The authored
`presentation-semantics-ja.yaml` was structurally close to the intended
information, but the generated HTML exposed too much diagnostic tabular data
and did not make the explanation logic visually understandable.

Review then identified a more fundamental mismatch: `core-ja.yaml` was only a
flat accepted-content list. The desired Content Core is the logic tree itself.

## Recovered prior intent

The 2026-09-04 journal and Phase 46 planning already distinguished:

- Story Flow between explanation Steps; and
- Explanation Structure inside each Step.

The completed Phase 46 contract kept the historical flat Content Core v1 and
introduced a separately authored Presentation Semantics v2 sibling. Phase
46.1 then specified a table-oriented integrated confirmation. Those completed
Phases remain valid history, but their accepted boundary does not provide the
newly clarified authoring and visual-review model.

Recursive Step nesting was not closed by the earlier contract. It is a new
explicit requirement.

## Decision

Create **Phase 58: Recursive Content Core Logic Tree Vertical Slice** rather
than reopen Phase 46 or Phase 46.1.

Phase 58 will:

1. use `content/core.yaml` with no locale suffix;
2. make that file the recursive logic-tree authority;
3. make every Step own its local Structure, child Steps, and child Flow;
4. define nesting through structural child ownership rather than a flat
   parent-ID list;
5. keep locale and reader-facing format information outside Core and declare
   locale in the format boundary;
6. generate one visual overview HTML showing Step nesting, Step Flow, and
   Step-local Structure together;
7. generate one slide-style HTML with one page per Step; and
8. use the SimpleModeling.org Article 9 Document Project as the first real
   acceptance driver.

## Phase boundary

The first Phase 58 slice ends when the real `core.yaml` can be strictly loaded
and both deterministic self-contained HTML forms show every declared Step,
child Flow, and local Structure without using diagnostic tables as their main
reader-facing representation.

Existing v1 Core files, Presentation Semantics v2, cross-media receipts,
scaffolds, and all Document Project profiles are not migrated in this slice.
Their replacement or retirement is a later decision based on the vertical
slice.

Publication, deployment, upload, push, and external-service mutation are not
authorized by this decision.

## Planning sources

- `docs/notes/document-project-recursive-content-core-logic-tree-proposal.md`
- `docs/journal/2026/09/2026-09-04-document-project-logical-presentation-projection-decision.md`
- `docs/notes/document-project-logical-presentation-projection-specification-proposal.md`
- `docs/phase/phase-58.md`
