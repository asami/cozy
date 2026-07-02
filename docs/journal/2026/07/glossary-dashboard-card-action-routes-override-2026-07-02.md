# Glossary Dashboard Card Action Routes Override

Date: 2026-07-02

Owner: Cozy BoK

Status: draft

This journal entry overrides the action-route parts of these earlier drafts:

- `docs/journal/2026/07/glossary-workflow-progress-card-reconsideration-2026-07-02.md`
- `docs/journal/2026/07/glossary-rdf-and-project-cards-spec-draft-2026-07-02.md`

Those documents remain historical drafts for progress metrics, RDF card
structure, and Project card structure. This document owns the work-driving
route requirements added after those drafts.

Earlier journal entries remain historical assets. They should not be rewritten
except for override notes that point here.

## Purpose

Glossary Dashboard cards must not stop at visualization. Each card should help
contributors move to the next useful work surface.

The Dashboard should answer both:

```text
What is the current state?
What should I open next to improve it?
```

## Workflow Card Routes

The Glossary Workflow progress card should keep the `actual / planned` view
from the earlier progress draft and add compact action routes for each stage.

Recommended layout:

```text
[候補抽出]        18 / 30  -> 候補を探す
[モノ/コト分類]   14 / 18  -> 分類する
[用語タイプ詳細化] 10 / 14  -> 用語タイプを確認
[用語ハブ整備]     8 / 10  -> 用語ハブを整備
[RDF接続]          6 /  8  -> RDFへ接続
[CML接続]          3 /  8  -> CMLへ接続
```

Recommended routes:

| Stage | Primary action route | Purpose |
| --- | --- | --- |
| Candidate extraction | articles / scenarios / references | find source material that should yield terms |
| Mono-koto classification | glossary term list filtered by unclassified terms | classify extracted terms as mono/koto candidates |
| Term type refinement | Type Analysis Routes | choose or correct the term type |
| Term Hub curation | Term Hub pages needing curation | add definition, related knowledge, and evidence |
| RDF connection | RDF card / RDF graph filtered by terms without RDF refs | connect terms to graph knowledge |
| CML connection | Project card / project CML alignment view | connect terms to CML and project metadata |

The route label should be visible as a compact command, not hidden inside a
long explanation.

## RDF Card Routes

The RDF card should lead contributors from RDF status to RDF work.

Recommended Japanese labels:

- `RDF未接続用語を見る`
- `RDFグラフを開く`
- `1.5+hop情報を確認`
- `外部URI接続を確認`

Route intent:

| Route | Purpose |
| --- | --- |
| RDF-unlinked terms | identify terms that need RDF refs |
| RDF graph | inspect graph-facing knowledge |
| 1.5+hop Information | inspect explanatory neighborhood quality |
| external URI links | inspect outside knowledge alignment |

The RDF card should not duplicate Missing Analysis lists. It may link to
filtered views owned by Missing Analysis or the RDF graph surface.

## Project Card Routes

The Project card should lead contributors from CML/project status to concrete
project-side work.

Recommended Japanese labels:

- `CML未接続用語を見る`
- `プロジェクト一覧を開く`
- `CML要素を確認`
- `source-only projectを確認`

Route intent:

| Route | Purpose |
| --- | --- |
| CML-unlinked terms | identify terms that need project/CML links |
| project list | inspect available project metadata |
| CML elements | inspect model-side candidates |
| source-only project | confirm intentional path-based development operation |

The Project card should not link to commit/upload workflows. Those are BoK
operation workflows, not glossary analysis surfaces.

## Missing Analysis Routes

Missing Analysis should be the strongest work-driving card. It should group
issues by work type and link directly to the relevant work surface.

Recommended mapping:

| Issue group | Action route |
| --- | --- |
| no term type | Type Analysis Routes or unclassified term list |
| no definition / summary | Term Hub curation list |
| no article / scenario refs | article and scenario relation work |
| no RDF refs | RDF card / RDF unlinked terms |
| no CML refs | Project card / CML unlinked terms |

## Acceptance Criteria

- Each Glossary Workflow stage has an action route that helps the contributor
  move the work forward.
- The RDF card links to RDF work surfaces such as RDF-unlinked terms, filtered
  RDF graph views, or 1.5+hop Information views.
- The Project card links to project/CML work surfaces such as CML-unlinked
  terms, project pages, CML element views, or source-only project status.
- Missing Analysis groups issues by work type and links to relevant work
  surfaces.
- Existing journal drafts keep only override notes and remain readable as
  historical context.
