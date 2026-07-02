# Glossary Workflow Card Specification Draft

Date: 2026-07-02

Owner: Cozy BoK

Status: draft

This journal entry proposes the Glossary Workflow card for the BoK Glossary
Dashboard. It is based on:

- `docs/notes/bok-glossary-cml-classification-alignment.md`
- `docs/design/bok-mono-koto-analysis.md`
- `docs/journal/2026/07/bok-glossary-cml-classification-alignment-2026-07-02.md`

Earlier journal entries remain historical assets. This entry records the
current draft for the dashboard card and does not rewrite older records.

## Purpose

The home dashboard already shows the BoK-level workflow at a coarse level:

```text
articles, scenarios -> mono-koto analysis -> glossary terms -> RDF / Project
```

The Glossary Dashboard needs a card that expands the "mono-koto analysis"
portion. The card should explain how glossary terms are extracted, refined, and
connected without turning the dashboard into a CML enum reference.

The card is not a term list and not a CML reference table. It is a workflow
guide for glossary curation.

## Card Name

Japanese UI:

- `用語集ワークフロー`

English UI:

- `Glossary Workflow`

## Card Role

The card shows the curation route:

```text
articles / scenarios / references
  -> candidate extraction
  -> mono/koto rough classification
  -> term type refinement
  -> Term Hub curation
  -> RDF / CML linkage
```

The primary message:

```text
Mono/koto is an extraction and semantic-analysis view.
CML classification is a modeling and runtime view.
The glossary workflow connects them without merging the classifications.
```

## Workflow Steps

### 1. Candidate Extraction

Input sources:

- articles
- scenarios
- bibliography / references
- project and CML metadata when available

Extraction aid:

- noun-like expressions become mono candidates
- verb-like expressions become koto candidates

This is an aid, not a final rule.

### 2. Mono-Koto Rough Classification

Initial candidate buckets:

| Bucket | Extraction hint | Meaning |
| --- | --- | --- |
| Mono candidate | noun-like expression | thing, object, participant, responsibility, material, or structure |
| Koto candidate | verb-like expression | occurrence, action, process, state, rule, or scenario flow |

The card should explicitly say that mono/koto assignment can change during
curation.

Japanese copy candidate:

```text
名詞をモノ候補、動詞をコト候補として拾います。これは抽出の補助線であり、最終分類はBoK内での役割に基づいて決めます。
```

### 3. Term Type Refinement

Candidate term types:

| Bucket | Representative term types |
| --- | --- |
| Mono | `concept`, `entity`, `actor`, `role`, `resource`, `artifact` |
| Koto | `event`, `action`, `process`, `task`, `rule`, `state`, `scenario` |

The dashboard may show these as compact chips. It should not require every BoK
to use every listed type.

### 4. Term Hub Curation

For each curated term, the Term Hub should be the place where concrete
knowledge is checked:

- definition
- related articles
- related scenarios
- related terms
- RDF links
- CML links
- diagnostics and missing analysis

The workflow card should point conceptually to Term Hub curation but should not
duplicate the term-type-specific Analysis Route card.

### 5. RDF / CML Linkage

Final linkage routes:

| Link target | Meaning |
| --- | --- |
| RDF | graph-facing knowledge representation and external knowledge alignment |
| CML | executable/modeling representation and project metadata alignment |

The card should mention that CML `entity` is a persistent object and that
`entityKind` classifies its purpose. Detailed `entityKind` values belong in CML
alignment details or Term Hub details, not in the workflow card body.

## Relationship to Other Glossary Dashboard Cards

### Glossary Summary

The summary card shows the current state:

- term count
- category count
- term type count
- RDF/article/CML linkage counts

It answers:

```text
How developed is this glossary now?
```

### Glossary Workflow

The workflow card shows the process:

- extract
- classify
- refine
- curate
- connect

It answers:

```text
How does this glossary turn source material into connected knowledge?
```

### Type Analysis Routes

The Analysis Route card shows type-specific checks:

- concept checks
- event checks
- actor checks
- role checks
- rule/resource checks

It answers:

```text
What should I inspect for this term type?
```

### Missing Analysis

The missing-analysis card shows concrete gaps:

- no RDF link
- no article reference
- no scenario
- no CML linkage

It answers:

```text
What should be improved next?
```

## Suggested Layout

The card can use a compact flow with five stages:

```text
[記事・シナリオ・参考資料]
        |
        v
[候補抽出]
  名詞 -> モノ候補
  動詞 -> コト候補
        |
        v
[用語タイプ詳細化]
  モノ: concept / entity / actor / role / resource / artifact
  コト: event / action / process / task / rule / state / scenario
        |
        v
[用語ハブ整備]
  定義 / 関連 / 根拠 / 診断
        |
        v
[RDF / CML 接続]
```

The UI should emphasize that the middle stage is refinement, not mechanical
grammar classification.

## Suggested Japanese Text

Lead:

```text
記事・シナリオ・参考資料から用語候補を抽出し、モノ/コトの補助線で整理した上で、BoK内での役割に基づいて用語タイプへ詳細化します。
```

Mono-koto note:

```text
名詞/動詞は抽出の補助線です。最終的な用語タイプは、文法ではなく知識空間での役割に基づいて決めます。
```

CML linkage note:

```text
CMLに接続する段階では、永続管理する対象をentityとして扱い、用途はentityKindで分類します。
```

## Suggested English Text

Lead:

```text
Extract candidate terms from articles, scenarios, and references, use mono/koto as an analysis aid, then refine each term by its role in the BoK knowledge space.
```

Mono-koto note:

```text
Noun-like and verb-like expressions are extraction hints. Final term types are based on knowledge-space roles, not grammar alone.
```

CML linkage note:

```text
When a term is connected to CML, persistent objects are modeled as entities and their purpose is classified by entityKind.
```

## Implementation Notes

The card should be generated from existing BoK metadata where possible, but the
first version can be static explanatory workflow text plus current counts.

Useful dynamic counts:

- number of glossary terms
- number of terms with `term_type`
- mono/koto derived counts
- terms with CML linkage
- terms with RDF linkage

The card should not introduce a new metadata schema by itself. If finer-grained
term types such as `entity`, `resource`, `artifact`, `action`, `process`,
`task`, `state`, and `scenario` are added later, the card can display them as
recognized route chips.

## Non-Goals

- Do not replace the Glossary Summary card.
- Do not duplicate the Type Analysis Routes card.
- Do not list all glossary terms.
- Do not show the full CML `EntityKind` enum as the primary dashboard content.
- Do not rewrite glossary or CML source from the dashboard.

## Acceptance Criteria

- The Glossary Dashboard has a card titled `用語集ワークフロー` in Japanese
  operation.
- The card shows the five-stage workflow from source material to RDF/CML
  linkage.
- The card explains noun/verb extraction as a rough aid, not a final
  classification rule.
- The card shows mono and koto representative term types.
- The card explains that CML persistent objects are entities classified by
  `entityKind`.
- The card does not include redundant primary-entry links already handled by
  other glossary dashboard cards.
