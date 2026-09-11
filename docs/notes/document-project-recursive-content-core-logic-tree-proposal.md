# Document Project Recursive Content Core Logic Tree Proposal

Date: 2026-09-11

Status: exploratory proposal; non-normative; Phase 58 planning input

## Purpose

Explore a Document Project Content Core that records the explanation itself as
one recursive logic tree instead of a flat accepted-content list plus a
separate presentation-semantics document.

The first implementation target is intentionally concrete: author one real
`content/core.yaml` for SimpleModeling.org Article 9 and project it into two
human-reviewable HTML representations.

## Problem

The Phase 46/46.1 direction distinguished two important semantic levels:

1. Story Flow across the complete explanation; and
2. Explanation Structure inside each explanation unit.

The accepted implementation retained `cozy.content-core.v1` as a localized
flat `accepted` list and placed the two-level logic in the sibling
`presentation-semantics-<language>.yaml`. Its confirmation renderer then
expressed that information primarily as diagnostic tables. The resulting
files preserve identities and mappings but do not provide the intended logic
tree that a human can understand at a glance.

The current `core-<language>.yaml` name also assigns locale to semantic
authority. Locale belongs to a selected output format, not to the identity or
filename of the logic tree.

## Proposed authority

The proposed source authority is exactly:

```text
content/core.yaml
```

It contains one recursive root Step. A Step owns:

- a stable identity and semantic role;
- claims and semantic references;
- one local Logical Structure;
- zero or more nested child Steps; and
- one child-flow definition relating its direct children.

Conceptually:

```text
LogicTree
`- Step
   |- local Structure
   |- child Flow
   `- child Steps
      `- Step ...
```

Nesting is represented structurally by child Steps, not by a flat collection
with optional parent IDs. Child Flow is scoped to one parent and normally
connects only that parent's direct children. Cross-level semantic references,
if later required, remain distinct from child Flow.

## Provisional recursive shape

```yaml
schema: cozy.content-core.logic-tree.v1
id: application-modeling
root:
  id: application-modeling
  semanticRole: explanation
  claims: []
  structure:
    pattern: decomposition
    nodes: []
    relations: []
  flow:
    pattern: sequence
    transitions:
      - id: scenario-next-realization
        relationType: next
        fromStepId: use-case-scenario
        toStepId: use-case-realization
  steps:
    - id: use-case-scenario
      semanticRole: input
      claims: []
      structure: ...
      flow: ...
      steps: []
    - id: use-case-realization
      semanticRole: realization
      claims: []
      structure: ...
      flow: ...
      steps: []
```

Exact field names and the relationship to the existing Phase 36 Logical
Pattern/node/typed-Relation vocabulary remain Phase 58 design and
specification work. The recursive ownership and scope above are the planning
constraint.

## Locale and format boundary

`core.yaml` has no locale suffix and does not make locale part of its semantic
identity. Reader-facing wording, locale-sensitive terminology, and medium
presentation are selected through format input that binds stable Core IDs.

The first slice needs one Japanese format for Article 9. The exact format path
and schema remain a Phase 58 design output, but locale must be declared there,
not inferred from a Core filename and not stored as a root Core language.

## Required HTML projections

### Logic-tree overview HTML

One self-contained HTML makes all three relationships visible together:

- nested Step containment;
- Flow among direct child Steps; and
- local Structure inside every Step.

The primary representation is a visual tree of nested Step cards and relation
edges. Diagnostic tables are not the reader-facing representation.

### Step-slide HTML

One self-contained 16:9 slide-like HTML provides one page for every Step,
including parent Steps. Each page shows:

- its ancestor path;
- its local Structure;
- its child Flow and direct children; and
- deterministic previous/next navigation in depth-first tree order.

## Validation hypotheses

The Phase 58 specification should reject at least:

- an empty or missing root;
- duplicate Step, node, relation, or transition IDs;
- recursive containment cycles;
- one Step appearing under multiple parents;
- unresolved local Relation endpoints;
- unresolved child Flow endpoints;
- child Flow whose endpoint is not a direct child of its owning Step;
- locale-dependent Core filenames or a root Core locale field; and
- HTML projections that omit a declared Step, Flow, or local Structure.

## Vertical-slice boundary

Phase 58 should prove the new authority and both HTML projections with the real
Article 9 driver before deciding the wider Document Project migration. It does
not need to migrate every profile, replace every Phase 46/46.1 consumer, or
publish any artifact.

The result should be sufficient to decide whether the recursive Core becomes
the next stable Document Project authority and which existing presentation
semantics and review products it supersedes.
