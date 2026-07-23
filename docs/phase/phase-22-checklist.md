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

Status: DONE

- [x] Preserve declared `componentRef` metadata when versioning
      `metadata/rdf/graph.json`.
- [x] Reject `componentRef` that is not a JSON object.
- [x] Reject missing or empty `componentRef.kind`.
- [x] Reject missing or empty `componentRef.name`.
- [x] Reject empty optional `componentRef.organization`.
- [x] Reject empty optional `componentRef.version`.
- [x] Reject `componentRef` on a node whose `node_type` is not
      `component-reference`.
- [x] Preserve existing graph-summary compatibility when no node declares
      `componentRef`.

## KM22-03: Component Reference Index Matching

Status: DONE

- [x] Load the selected generation's
      `metadata/cncf/component-references/car.json`.
- [x] Load the selected generation's
      `metadata/cncf/component-references/sar.json`.
- [x] Build deterministic lookup keys from kind, name, optional organization,
      and optional version.
- [x] Match required kind and name exactly.
- [x] Match declared organization exactly when present.
- [x] Match declared version exactly when present.
- [x] Reject `componentRef` when the matching index file is absent.
- [x] Reject `componentRef` when no index entry matches.
- [x] Reject `componentRef` when more than one index entry matches.
- [x] Keep ordinary graph nodes independent of the component-reference index.

## KM22-04: Executable Specifications

Status: IN PROGRESS

- [x] Valid CAR `componentRef` is preserved in public graph metadata.
- [x] Valid SAR `componentRef` is preserved in public graph metadata.
- [x] Optional version matching succeeds when the index contains that version.
- [x] Optional organization matching succeeds when the index declares that
      organization.
- [x] Invalid node type fails deterministically.
- [x] Missing reference fails deterministically.
- [x] Kind mismatch fails deterministically.
- [x] Version mismatch fails deterministically.
- [x] Ambiguous reference fails deterministically.
- [x] Graph summaries without `componentRef` remain valid.
- [x] Matching node id or label alone does not inject or validate
      `componentRef`.
- [x] Run `sbt --batch "testOnly cozy.CozyBokKnowledgeSourceSpec"`.
- [x] Run `sbt --batch test`.
- [x] Run `git diff --check`.

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
