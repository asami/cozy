# BoK Glossary and CML Classification Alignment

Date: 2026-07-02

This journal entry records the decision trail for the current BoK glossary and
CML classification alignment. Earlier journal entries are historical assets and
should be read in their original context. They are not rewritten by this entry.

The current working note is:

- `docs/notes/bok-glossary-cml-classification-alignment.md`

The related design document is:

- `docs/design/bok-mono-koto-analysis.md`

## Context

KnowledgeHub BoK needs a glossary dashboard workflow that refines the rough
mono-koto analysis shown on the home dashboard.

The home dashboard treats the flow roughly as:

```text
articles, scenarios -> mono-koto analysis -> glossary terms
```

The glossary dashboard needs the next level:

```text
noun-like expressions -> mono candidates
verb-like expressions -> koto candidates
mono/koto candidates -> curated BoK term types
curated terms -> RDF and CML linkage
```

## Decision

BoK glossary classification and CML classification remain separate axes.

BoK term types are semantic analysis labels used by glossary authors and BoK
readers. CML classification describes modeling representation and runtime
handling.

The mapping should be explicit:

```text
BoK term type -> CML representation -> optional CML/CNCF subtype
```

## CML Entity Clarification

CML `entity` is a persistent, identifiable domain object.

CNCF now uses `EntityKind` as the canonical entity classification axis:

- `master`
- `document`
- `workflow`
- `task`
- `actor`
- `asset`
- `system`

Legacy `operationKind = "resource"` and `operationKind = "task"` remain as a
compatibility bridge. Current explanations should prefer `entityKind` and treat
reader-facing `resource` as an alias-like dashboard label for master/reference
resource objects when that wording is easier to understand.

## Implication

The glossary dashboard should show the analysis workflow, not a full CML enum
reference.

Detailed CML classification belongs in CML alignment views, project metadata,
or Term Hub details.
