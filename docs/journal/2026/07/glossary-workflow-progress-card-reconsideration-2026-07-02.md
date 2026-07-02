# Glossary Workflow Progress Card Reconsideration

Date: 2026-07-02

Owner: Cozy BoK

Status: draft

This journal entry revisits the Glossary Workflow card with a progress-first
display model. The earlier draft remains as historical context:

- `docs/journal/2026/07/glossary-workflow-card-spec-draft-2026-07-02.md`

This entry refines that idea by requiring each workflow step to show progress
as:

```text
actual / planned
```

Earlier journal entries remain historical assets and are not rewritten.

## Override Note

Action-route details for this workflow card are superseded by:

- `docs/journal/2026/07/glossary-dashboard-card-action-routes-override-2026-07-02.md`

This document remains the historical draft for the `actual / planned` progress
model. Use the override journal for work-driving route requirements.

## Design Change

The workflow card should not be only an explanatory flow diagram.

Each workflow step should be a measurable stage with an immediately visible
progress indicator. The reader should be able to see whether glossary curation
is moving from extraction to knowledge connection.

The card should answer:

```text
Where is the glossary curation workflow progressing, and where is it thin?
```

## Core Principle

Use `actual / planned` for each workflow stage.

Examples:

```text
候補抽出       18 / 30
モノ/コト分類  14 / 18
用語タイプ詳細化 10 / 14
用語ハブ整備   8 / 10
RDF接続        6 / 8
CML接続        3 / 8
```

`actual` must be derived from available BoK metadata.

`planned` must come from one of the following, in order:

1. explicit BoK planning metadata;
2. derived upstream stage count;
3. a conservative fallback such as total glossary term count;
4. `-` when no defensible denominator exists.

The card must not invent planned counts.

## Revised Card Role

The Glossary Workflow card becomes a workflow progress card.

It still explains the curation route:

```text
source material
  -> candidate extraction
  -> mono/koto rough classification
  -> term type refinement
  -> Term Hub curation
  -> RDF / CML linkage
```

But every stage is shown as a progress unit.

The card should combine:

- short workflow explanation;
- stage-by-stage `actual / planned`;
- compact progress bars;
- stage status labels;
- optional links to the related detailed card.

## Stage Model

### Stage 1: Candidate Extraction

Purpose:

```text
Identify candidate glossary terms from articles, scenarios, references, and
project/CML metadata.
```

Actual count:

- number of candidate terms if candidate metadata exists;
- otherwise number of curated glossary terms.

Planned count:

- planned candidate count from BoK planning metadata;
- otherwise source-derived expected candidate count when available;
- otherwise `-`.

Fallback display:

```text
候補抽出 18 / -
```

This stage may initially show curated terms as the actual count because many
BoKs do not preserve pre-curation candidate metadata.

### Stage 2: Mono-Koto Rough Classification

Purpose:

```text
Use noun-like expressions as mono candidates and verb-like expressions as koto
candidates, then allow reassignment during curation.
```

Actual count:

- number of glossary terms with a derived mono/koto classification.

Planned count:

- number of candidate terms from Stage 1 when available;
- otherwise total glossary term count.

A term has mono/koto classification when its `term_type` can be mapped to mono
or koto.

Display examples:

```text
モノ/コト分類 14 / 18
モノ 10 / 14
コト 4 / 14
```

### Stage 3: Term Type Refinement

Purpose:

```text
Refine mono/koto candidates into BoK term types such as concept, entity, actor,
role, resource, event, action, process, task, rule, state, or scenario.
```

Actual count:

- number of terms with explicit `term_type`.

Planned count:

- number of mono/koto-classified terms;
- otherwise total glossary term count.

Optional breakdown:

```text
concept 6
actor 2
role 2
event 3
rule 1
```

The card should not require all possible term types to be present.

### Stage 4: Term Hub Curation

Purpose:

```text
Check whether each curated term has enough knowledge context to behave as a
Term Hub.
```

Actual count:

- number of terms that satisfy the minimum Term Hub completeness rule.

Planned count:

- number of terms with explicit `term_type`;
- otherwise total glossary term count.

Minimum Term Hub completeness rule for v1:

- term has title;
- term has definition or summary;
- term has at least one of:
  - related article;
  - related scenario;
  - related term;
  - RDF link;
  - CML link.

This is intentionally modest. A term can be useful before it is fully connected
to RDF and CML.

### Stage 5: RDF Connection

Purpose:

```text
Connect terms to graph-facing knowledge.
```

Actual count:

- number of terms with at least one RDF reference.

Planned count:

- number of Term Hub curated terms;
- otherwise total glossary term count.

Detailed RDF metrics belong to the RDF card. The workflow card should show
only the stage progress and a compact handoff to the RDF card.

### Stage 6: Project / CML Connection

Purpose:

```text
Connect terms to CML models, project metadata, and implementation-side
knowledge.
```

Actual count:

- number of terms with at least one CML link or project metadata link.

Planned count:

- number of Term Hub curated terms;
- otherwise total glossary term count.

Detailed project and source-only operation information belongs to the Project
card. The workflow card should show only the stage progress and a compact
handoff to the Project card.

## Planned Count Sources

The implementation should support these denominator sources, from strongest to
weakest:

### Explicit Planning Metadata

Future BoK metadata may define planned counts:

```yaml
glossary:
  workflow:
    planned:
      candidates: 30
      mono_koto: 30
      term_types: 24
      term_hubs: 24
      rdf_links: 20
      cml_links: 12
```

This is the most explicit and should win when present.

### Derived Upstream Count

When explicit planning metadata is absent, the planned count for a stage should
usually be the actual count of the previous stage.

Example:

```text
term type refinement planned = mono/koto classified actual
RDF connection planned = Term Hub curated actual
```

This makes the workflow self-explanatory and avoids arbitrary targets.

### Total Glossary Count

When the upstream stage is unavailable, total glossary term count can be used
as a conservative denominator for term-level stages.

### Unknown Planned Count

If no denominator is defensible, show:

```text
actual / -
```

Do not show `actual / 0` unless the planned count is explicitly zero.

## UI Shape

Recommended layout:

```text
用語集ワークフロー

記事・シナリオ・参考資料から用語候補を抽出し、モノ/コトの補助線で整理した上で、BoK内での役割に基づいて用語タイプへ詳細化します。

[候補抽出]        18 / 30  ██████----
[モノ/コト分類]   14 / 18  ███████---
[用語タイプ詳細化] 10 / 14  ███████---
[用語ハブ整備]     8 / 10  ████████--
[RDF接続]          6 /  8  ███████---
[CML接続]          3 /  8  ████------
```

Each stage should include a concise checkpoint line:

```text
候補抽出: 記事・シナリオ・参考資料から候補を拾う
モノ/コト分類: 名詞/動詞を補助線にして候補を整理する
用語タイプ詳細化: BoK内での役割に基づいてterm_typeを決める
用語ハブ整備: 定義・関連・根拠・診断を確認する
RDF接続: グラフ知識へ接続する
CML接続: プロジェクト/CML知識へ接続する
```

## Relationship to Other Cards

### Glossary Summary

The Summary card should remain a static state summary:

- total terms;
- categories;
- term types;
- total RDF/CML linkage counts.

It should not show the workflow progression in detail.

### Workflow Progress

The Workflow card owns stage-by-stage `actual / planned` progress.

### RDF Card

The RDF card owns RDF-specific detail:

- RDF triples;
- RDF node linkage;
- 1.5+hop Information status;
- external URI/provenance breakdown.

The Workflow card may show only:

```text
RDF接続 6 / 8
```

### Project Card

The Project card owns project-specific detail:

- CML element breakdown;
- project references;
- source-only operation;
- artifact distribution state.

The Workflow card may show only:

```text
CML接続 3 / 8
```

### Missing Analysis

Missing Analysis owns the list of terms that need attention.

The Workflow card may show aggregate progress, but not the full issue list.

## Japanese Copy Revision

Lead:

```text
記事・シナリオ・参考資料から用語候補を抽出し、モノ/コトの補助線で整理した上で、BoK内での役割に基づいて用語タイプへ詳細化します。各工程は 実数/予定数 で進捗を確認します。
```

Progress note:

```text
予定数は明示設定を優先し、未設定の場合は前工程の実数や用語総数から導出します。根拠のない予定数は表示しません。
```

Mono-koto note:

```text
名詞/動詞は抽出の補助線です。最終的な用語タイプは、文法ではなく知識空間での役割に基づいて決めます。
```

## English Copy Revision

Lead:

```text
Extract candidate terms from articles, scenarios, and references, use mono/koto as an analysis aid, then refine each term by its role in the BoK knowledge space. Each stage shows actual/planned progress.
```

Progress note:

```text
Planned counts prefer explicit configuration. When unavailable, they are derived from upstream stage counts or total term counts. Unjustified planned counts are not displayed.
```

## Data Contract Draft

No new metadata is required for the first implementation.

Existing metadata can support:

- total terms;
- `term_type`;
- derived mono/koto classification;
- summary/definition presence;
- article refs;
- scenario refs;
- term refs;
- RDF refs;
- CML refs.

Optional future metadata:

```yaml
glossary:
  workflow:
    planned:
      candidates: 30
      mono_koto: 30
      term_types: 24
      term_hubs: 24
      rdf_links: 20
      cml_links: 12
```

If this metadata is added, it should live in site or BoK configuration rather
than inside generated output.

## Acceptance Criteria

- The Glossary Workflow card shows each workflow stage as `actual / planned`.
- Unknown planned counts are shown as `-`, not guessed.
- Each stage has a short checkpoint explanation.
- RDF and CML stages are included as workflow stages but delegate detailed
  analysis to the RDF and Project cards.
- Mono/koto remains a rough analysis aid, not a rigid grammar rule.
- Planned count derivation is deterministic and explainable.

## Open Questions

- Where should explicit planned counts live: `site.conf`, BoK config, or a
  dedicated workflow metadata file?
- Should planned counts be global, category-specific, or both?
- Should "candidate extraction" preserve raw candidates before curation, or is
  curated term count enough for v1?
- Should Term Hub completeness be configurable per BoK?
