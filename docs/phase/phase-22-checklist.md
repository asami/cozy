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

Status: DONE

- [x] Valid CAR `componentRef` is preserved in public graph metadata.
- [x] Valid SAR `componentRef` is preserved in public graph metadata.
- [x] Source-declared RDF graph overlays are merged before `componentRef`
      validation.
- [x] Project-backed CAR metadata can publish a component-reference index when
      no repository catalog exists.
- [x] Malformed source graph overlay containers fail instead of disappearing.
- [x] Project-backed records do not claim an unverified CAR archive file.
- [x] Duplicate project-backed CAR identities fail deterministically.
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

Status: DONE

- [x] Add or identify one representative KnowledgeHub component-reference graph
      node.
- [x] Build KnowledgeHub through the Cozy launcher with the current development
      runtime:
      `COZY_RUNTIME_DEV_DIR=/Users/asami/src/dev2025/cozy cozy bok build . --strategy preview`.
- [x] Confirm `website.d/metadata/rdf/graph.json` includes the declared
      `componentRef`.
- [x] Confirm `website.d/metadata/cncf/component-references/car.json`
      contains the matched existence record.
- [x] Confirm no graph node gained `componentRef` by id or label inference.
- [x] Record the generated source fixture path and expected handoff payload for
      Textus BoK Phase 6.

Evidence:

- Source fixture:
  `/Users/asami/src/Project2026/bok-knowledgehub/src/main/doxsite/metadata/rdf/graph.json`.
- Expected graph payload:
  `{"id":"component:nict-knowledgehub","node_type":"component-reference","componentRef":{"kind":"car","name":"nict-knowledgehub"}}`.
- Matched CAR index entry:
  `website.d/metadata/cncf/component-references/car.json` entry
  `name = nict-knowledgehub`, version `0.1.0-smoke`.
- Launcher runtime: `cozy 0.3.0-SNAPSHOT`.
- Verification output: componentRef node count `1`; CAR entries
  `[("nict-knowledgehub", ["0.1.0-smoke"])]`.

## KM22-06: Review And Closure

Status: IN PROGRESS

- [x] Complete post-implementation review.
- [x] Fix all actionable review findings, including naming and spec debt.
- [x] Validate focused and full Cozy tests after review fixes.
- [x] Commit the validated implementation.
- [ ] Update Phase 22 status and close the checklist from executable evidence.

Closure evidence recorded on 2026-07-23:

- The post-implementation review reported six actionable findings covering the
  real launcher gate, malformed graph overlays, project-backed archive claims,
  duplicate project identities, phase-ledger consistency, and naming debt.
- The review-fix stage resolved all six findings and added executable
  regression coverage.
- The clean re-review found no remaining actionable finding.
- Focused Cozy specifications passed 52 tests in two suites.
- The full Cozy suite passed 659 tests with no failure.
- The KnowledgeHub launcher build used Cozy `0.3.0-SNAPSHOT` and published one
  validated `nict-knowledgehub` component-reference node.
- Cozy commit `ef27400` contains the validated implementation.
- KnowledgeHub commit `7dde870` contains the representative source fixture.
- The final checklist-close item remains open until the shared phase index and
  strategy can move Phase 22 to closed without absorbing unrelated Phase 23/24
  planning changes.
