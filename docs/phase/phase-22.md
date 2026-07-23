# Phase 22: BoK Knowledge Map Component Handoff

Status: in-progress

Start date: 2026-07-23

Dependency: Phase 21 component repository discovery and Phase 14 BoK/SIE
KnowledgeSource handoff

## Goal

Publish a source-declared relation between BoK RDF graph nodes and CAR/SAR
component-reference index entries so Textus BoK Knowledge Map can hand readers
to Textus CBD Support without inferring component identity from labels, node
ids, tags, or repository paths.

Phase 22 is a Cozy publication-contract phase. Cozy owns validation and
publication of the optional `componentRef` metadata in
`metadata/rdf/graph.json`; Textus BoK consumes that validated identity for a
read-only Knowledge Map. CBD capability, dependency, compatibility, operation,
and usage detail remains outside Cozy and outside the browser.

## Scope

In scope:

- extend the compatible `cozy.rdf-graph-summary.v1` node metadata with an
  optional `componentRef` object;
- allow `componentRef` only on graph nodes whose `node_type` is
  `component-reference`;
- validate every declared `componentRef` against the selected generation's
  `metadata/cncf/component-references/car.json` and `sar.json`;
- require exact `kind` and `name` matching and exact optional
  `organization` / `version` matching when declared;
- reject absent, ambiguous, mismatched, malformed, or wrong-node-type
  component references as deterministic build diagnostics;
- preserve graph summaries that do not declare `componentRef`;
- document the Textus BoK consumer handoff as existence-only identity.

Out of scope:

- inferring a component reference from graph node id, label, tag, term,
  repository filename, RDF edge, or rendered HTML;
- adding CBD-owned detail to Cozy metadata;
- querying CBD from generated BoK pages or from Textus BoK browser views;
- changing CAR/SAR repository discovery semantics from Phase 21;
- introducing new RDF facts, archive scans, remote fetches, or Knowledge Map
  mutations.

## Publication Contract

`metadata/rdf/graph.json` keeps the existing `cozy.rdf-graph-summary.v1`
envelope. A node may add:

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

`componentRef.kind` and `componentRef.name` are required non-empty strings.
`componentRef.organization` and `componentRef.version` are optional non-empty
strings. The matching component-reference index remains the canonical
existence source.

## Stage 22.1: Contract Documentation

Stage Status:

- Current status: DONE
- Owner: Cozy
- Update rule: mark work complete only from the Phase 22 checklist.
- Checklist basis: `KM22-01`

Focus:

- update the BoK/SIE graph-summary contract with the `componentRef` object,
  allowed node type, exact matching rules, diagnostics, and non-inference
  boundary.

## Stage 22.2: Graph Validation

Stage Status:

- Current status: DONE
- Owner: Cozy
- Update rule: mark work complete only from the Phase 22 checklist.
- Checklist basis: `KM22-02`

Focus:

- preserve declared `componentRef` metadata while rejecting malformed node
  shapes and wrong node types before publication.

## Stage 22.3: Component Index Matching

Stage Status:

- Current status: DONE
- Owner: Cozy
- Update rule: mark work complete only from the Phase 22 checklist.
- Checklist basis: `KM22-03`

Focus:

- build a deterministic lookup from generated CAR/SAR component-reference
  indexes and validate each graph `componentRef` against that lookup.

## Stage 22.4: Executable Specification

Stage Status:

- Current status: DONE
- Owner: Cozy
- Update rule: mark work complete only from the Phase 22 checklist.
- Checklist basis: `KM22-04`

Focus:

- cover valid CAR/SAR references, optional organization/version matching,
  invalid node type, missing reference, kind/version mismatch, ambiguity,
  compatibility without `componentRef`, and no label/id inference.

## Stage 22.5: KnowledgeHub Handoff Fixture

Stage Status:

- Current status: DONE
- Owner: Cozy / KnowledgeHub
- Update rule: mark work complete only from the Phase 22 checklist.
- Checklist basis: `KM22-05`

Focus:

- generate one representative KnowledgeHub BoK source where graph summary and
  component-reference index agree, then hand that source to Textus BoK Phase 6.

## Completion Criteria

Phase 22 closes when Cozy publishes `componentRef` only as validated
existence-only node metadata, invalid declarations fail deterministically, graph
summary compatibility is preserved for sites without component references, and
Textus BoK has a representative handoff fixture for Knowledge Map source-reader,
query, Static Form, and SAR agreement checks.

## References

- `docs/phase/phase-22-checklist.md`
- `docs/journal/2026/07/bok-knowledge-map-component-handoff-2026-07-23.md`
- `docs/design/bok-sie-integration-contract.md`
- `docs/design/bok-sie-information-handoff.md`
