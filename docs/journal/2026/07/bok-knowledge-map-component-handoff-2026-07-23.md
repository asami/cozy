# BoK Knowledge Map Component Handoff — 2026-07-23

## Context

Textus BoK Phase 6 adds a read-only Knowledge Map Web application over a
selected complete Cozy BoK generation. The map must keep CAR and SAR nodes
existence-only while giving a reader a portable handoff to Textus CBD Support
for capability, dependency, compatibility, operation, and usage detail.

Cozy already publishes two independent resources:

- `metadata/rdf/graph.json` with `cozy.rdf-graph-summary.v1` nodes and edges;
- `metadata/cncf/component-references/{car,sar}.json` with
  `cncf.component-reference-index.v1` existence records.

Neither resource currently declares which graph node, if any, denotes which
component existence record. Textus BoK must not infer that relation from a
node identifier or label because an inferred match is not source-attributable
knowledge.

## Direction

Cozy will extend the compatible `cozy.rdf-graph-summary.v1` node metadata with
an optional, source-declared `componentRef` object. It is permitted only when
`node_type` is `component-reference` and carries the portable identity:

```json
{
  "id": "component:textus-bok",
  "label": "Textus BoK",
  "node_type": "component-reference",
  "componentRef": {
    "kind": "car",
    "name": "textus-bok",
    "version": "0.1.0-SNAPSHOT"
  }
}
```

`componentRef.kind` and `componentRef.name` are required. `organization` and
`version` are optional. The reference is an assertion of existence only; it
does not carry CBD-owned detail.

Cozy validates every `componentRef` against the component-reference index in
the same published BoK generation. The index is the canonical existence source:

- kind and name must match exactly;
- when organization or version is present, it must match exactly;
- an absent, ambiguous, or mismatched reference is a deterministic build
  diagnostic and prevents publication of that invalid handoff;
- a node without `componentRef` remains an ordinary graph node;
- Cozy never constructs `componentRef` from a node ID, label, tag, term, or
  repository filename.

The existing graph-summary shape remains compatible: `componentRef` is
optional node metadata, while `schemaVersion`, `kind`, `sourceRef`, `nodes`,
`edges`, and `truncated` retain their current meanings.

## Consumer Handoff

After validation, Textus BoK may project the matching selected-generation
`ComponentReference` into a Knowledge Map node. Its Web view may display or
copy `kind`, `name`, optional organization/version, and evidence as a CBD
Support handoff payload. It must not query CBD from the browser, import CBD
models, or display CBD capability, dependency, compatibility, operation,
manual, or usage data.

## Implementation Plan

1. Extend the normative BoK/SIE graph-summary documentation with the
   `componentRef` object, allowed node type, index matching rule, and failure
   diagnostics.
2. Add Cozy graph-summary decoding/validation that preserves declared
   `componentRef` metadata and rejects invalid source shapes.
3. Build a deterministic lookup from the generated CAR/SAR component-reference
   indexes and validate each graph `componentRef` before writing the published
   graph summary.
4. Add executable specs for valid CAR/SAR references, optional version and
   organization matching, invalid node type, missing reference, kind/version
   mismatch, ambiguous reference, compatibility without `componentRef`, and
   no label/ID inference.
5. Produce a representative KnowledgeHub source fixture and hand it to Textus
   BoK Phase 6 for source-reader, query, Static Form, and SAR agreement checks.

## Validation

The Cozy implementation must at least pass:

```sh
sbt --batch "testOnly cozy.CozyBokKnowledgeSourceSpec"
sbt --batch test
```

The integration handoff is complete only when the generated graph summary and
component-reference index agree for a representative Cozy BoK source, and
Textus BoK displays the resulting identity without inference.

## Boundaries

- This is a Cozy publication-contract addition, not a CBD API change.
- It does not generate new factual edges or enrich an existing node from
  semantic retrieval.
- It does not scan archives, fetch remote resources, or read rendered HTML.
- It does not change MCP readiness or expose a mutation through the Knowledge
  Map.
