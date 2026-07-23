# Phase 22 Checklist

This checklist is the authoritative progress ledger for Phase 22: BoK
Knowledge Map Component Handoff.

## KM22-01: Graph Summary Contract Documentation

Status: DONE

- [x] Define optional `componentRef` node metadata in the
      `cozy.rdf-graph-summary.v1` contract.
- [x] State that `componentRef` is allowed only when `node_type` is
      `component-reference`.
- [x] Define required `componentRef.kind` and `componentRef.name`.
- [x] Define optional `componentRef.organization` and `componentRef.version`.
- [x] Define exact matching against CAR/SAR component-reference indexes.
- [x] Document absent, ambiguous, mismatched, malformed, and wrong-node-type
      diagnostics.
- [x] Record that Cozy never infers `componentRef` from graph node id, label,
      tag, term, repository filename, RDF edge, or rendered HTML.
- [x] Record that `componentRef` is existence-only and does not carry CBD-owned
      capability, dependency, compatibility, operation, or usage detail.

## KM22-02: Cozy Graph Node Decoding And Shape Validation

Status: PLANNED

- [ ] Preserve declared `componentRef` metadata when versioning
      `metadata/rdf/graph.json`.
- [ ] Reject `componentRef` that is not a JSON object.
- [ ] Reject missing or empty `componentRef.kind`.
- [ ] Reject missing or empty `componentRef.name`.
- [ ] Reject empty optional `componentRef.organization`.
- [ ] Reject empty optional `componentRef.version`.
- [ ] Reject `componentRef` on a node whose `node_type` is not
      `component-reference`.
- [ ] Preserve existing graph-summary compatibility when no node declares
      `componentRef`.

## KM22-03: Component Reference Index Matching

Status: PLANNED

- [ ] Load the selected generation's
      `metadata/cncf/component-references/car.json`.
- [ ] Load the selected generation's
      `metadata/cncf/component-references/sar.json`.
- [ ] Build deterministic lookup keys from kind, name, optional organization,
      and optional version.
- [ ] Match required kind and name exactly.
- [ ] Match declared organization exactly when present.
- [ ] Match declared version exactly when present.
- [ ] Reject `componentRef` when the matching index file is absent.
- [ ] Reject `componentRef` when no index entry matches.
- [ ] Reject `componentRef` when more than one index entry matches.
- [ ] Keep ordinary graph nodes independent of the component-reference index.

## KM22-04: Executable Specifications

Status: PLANNED

- [ ] Valid CAR `componentRef` is preserved in public graph metadata.
- [ ] Valid SAR `componentRef` is preserved in public graph metadata.
- [ ] Optional version matching succeeds when the index contains that version.
- [ ] Optional organization matching succeeds when the index declares that
      organization.
- [ ] Invalid node type fails deterministically.
- [ ] Missing reference fails deterministically.
- [ ] Kind mismatch fails deterministically.
- [ ] Version mismatch fails deterministically.
- [ ] Ambiguous reference fails deterministically.
- [ ] Graph summaries without `componentRef` remain valid.
- [ ] Matching node id or label alone does not inject or validate
      `componentRef`.
- [ ] Run `sbt --batch "testOnly cozy.CozyBokKnowledgeSourceSpec"`.
- [ ] Run `sbt --batch test`.
- [ ] Run `git diff --check`.

## KM22-05: KnowledgeHub Operational Handoff

Status: PLANNED

- [ ] Add or identify one representative KnowledgeHub component-reference graph
      node.
- [ ] Build KnowledgeHub with `cozy bok build . --strategy preview`.
- [ ] Confirm `website.d/metadata/rdf/graph.json` includes the declared
      `componentRef`.
- [ ] Confirm `website.d/metadata/cncf/component-references/{car,sar}.json`
      contains the matched existence record.
- [ ] Confirm no graph node gained `componentRef` by id or label inference.
- [ ] Record the generated source fixture path and expected handoff payload for
      Textus BoK Phase 6.

## KM22-06: Review And Closure

Status: PLANNED

- [ ] Complete post-implementation review.
- [ ] Fix all actionable review findings, including naming and spec debt.
- [ ] Validate focused and full Cozy tests after review fixes.
- [ ] Commit the validated implementation.
- [ ] Update Phase 22 status and close the checklist from executable evidence.
