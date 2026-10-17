# CML Classification Metadata Supplier Handoff

Current status: S54.2.3 CLOSED at `036a553`; Phase 54.2 CLOSED by this distinct local release.
Owner: Cozy Classification supplier; later dynamic/usecase integration belongs to Phase 54.3/54.4.
Update rule: preserve the accepted Phase 54 foundation, Phase 54.1 Structure and frozen Classification v1 contracts; adopt the fixture semantics without name reconstruction.
Closure basis: FM-01–09 executable evidence, parent six-suite focused validation, independent lightweight PASS and parent manual Step commit. The sole full Phase review and accepted M0 cycle 1 have no remaining blockers; final ordinary full validation passed 2730 tests across 189 suites, and this distinct local release binds closure.

## Exact supplier and receiver boundary

The [Classification specification](../spec/cml-classification-metadata.md) defines the
closed wire schema and [design](cml-classification-metadata.md) assigns responsibility.
The unchanged [foundation handoff](cml-semantic-foundation-handoff.md) and accepted
[Structure specification](../spec/cml-structure-metadata.md) remain dependencies.
All APIs are `private[cozy]`: an internal Cozy consumer uses package `cozy.modeler`;
this is not a public CLI or external integration protocol implementation.

- [`CmlClassificationMetadata`](../../src/main/scala/cozy/modeler/CmlClassificationMetadata.scala):
  `build(catalog, traits, powertypes, generalizations, traitRealizations, powertypeClassifications, extensions)`
  returns `Either[Vector[ClassificationDiagnostic], Graph]`. Only its validated factory
  creates Graph. `toJson(graph): JsObject`, `canonicalJson(graph): String`, and
  `read(value: JsValue): Either[Vector[ClassificationDiagnostic], Graph]` complete
  typed production, rendering and strict consumption.
- [`CmlClassificationTopology`](../../src/main/scala/cozy/modeler/CmlClassificationTopology.scala):
  `build(graph)` returns `Either[Vector[ClassificationDiagnostic], Topology]`.
  `node(id)`, `outgoing(id)`, `incoming(id)`, `dimensions(id)` use exact local
  ModelElementId node pairs; `dimension(id)` uses each dimension declaration's
  own qualified identity. Query results are typed `Either`s with fixed diagnostics.
- [`CmlStructureMetadata`](../../src/main/scala/cozy/modeler/CmlStructureMetadata.scala):
  `read(value)` remains the strict predecessor receiver; its original projections
  are unchanged in both new fixtures.

Read the UTF-8 resource, parse with Play JSON `Json.parse`, then call
`CmlClassificationMetadata.read`. A Left contains typed diagnostics; a Right
contains the admitted Graph with all catalog, source and Presence facts. Build
Topology from that Right and handle its typed query results. Render only an
admitted Graph. A producer supplying typed facts first admits its Catalog via
`CmlSemanticFoundation.build`; Classification.build validates exact full-record
projection bindings and adds its child to supplied peer extensions.

The envelope remains `cozy.cml.semantic-metadata.v1`. Its Classification child is
`extensions.cozy.cml.classification`, version `cozy.cml.classification.v1`, with
exactly the five required arrays `traits`, `powertypes`, `generalizations`,
`traitRealizations`, `powertypeClassifications` plus `schemaVersion`. Required
projection fields and recursive Presence/reference shapes are closed; malformed,
missing or extra typed fields fail. The original `cozy.cml.structure` child and
opaque peers remain separate. Canonical object keys sort recursively; arrays
retain declared order. This determinism is not a signature/currentness protocol.

## Adoptable resources and semantic expectations

[`classification-metadata-v1-declared.json`](../../src/test/resources/cozy/modeler/classification-metadata-v1-declared.json)
and [`classification-metadata-v1-absence.json`](../../src/test/resources/cozy/modeler/classification-metadata-v1-absence.json)
both retain the accepted [`structure-metadata-v1-declared.json`](../../src/test/resources/cozy/modeler/structure-metadata-v1-declared.json)
six-record prefix, original Term, full Structure child and `org.example.structure-handoff`
peer unchanged. Each adds sixteen core records and one Term: total 22 records,
two Terms, and Classification projections 2 traits / 2 powertypes / 3 generalizations /
2 realizations / 3 dimensions. Four powertype-kind records are nodes, not an
extra Classification carrier family. Both opaque peers survive canonical consumption.
The new peer is `org.example.classification-handoff` with producer `synthetic-caller`,
ordered dimensions `[status,category,status-secondary]` and `absence: null`.

All new names are `共有`; qualified identities carry semantics. New source facts
use authority `classification.example`, canonical relative path
`src/main/cml/classification-handoff.cml` and
lines 7–22. The added Term has the audited trait's components and source line 7,
but remains a distinct TermId. These are caller-admitted synthetic source facts;
they do not verify that a real CML file exists or is current. Core references
are explicit NotDeclared with `No semantic references are declared.` and invent
no BoK relation/profile identities.

Declared topology has eleven nodes and eight links in catalog declaration order.
Order entity generalizes to the local order aggregate and an unresolved External
`external.example/entity:base`. Audited trait generalizes to labeled. Order entity
realizes both traits and has independent status, category and status-secondary
dimension IDs. Status and status-secondary share subject/target but never merge.
Powertype status members are status-open then status-closed; category members
are category-retail then category-wholesale, all explicit local kind references.
Members introduce no edges. Order's outgoing links are two generalizations,
two realizations, then three dimensions; aggregate incoming is order-aggregate,
labeled incoming is trait generalization then order-labeled realization.

Absence mode changes only the first trait's own identity to NotRepresented with
`No stable trait identity is represented.` and all members/endpoints to explicit
absence. Ordinal cycles and exact four reason/detail pairs are fixed in the
[fixture contract](../spec/cml-classification-metadata.md#s5423-frozen-fixture-contract).
Eleven nodes include the anonymous trait and eight links retain absent ends.
Every known-node incoming/outgoing/subject-dimension query returns empty;
all three dimension own-ID queries return their full absent projections.
`node(classification.example, trait:audited)` yields UnknownNode while the
separate matching Term resolves. No anonymous identity is inferred from names,
source, Terms, array offsets or dimension payloads.

## Runnable supplier evidence

From the Cozy repository root, the parent-selected logical recipe is:

```sh
sbt --batch "testOnly cozy.modeler.CmlClassificationMetadataFixtureSpec cozy.modeler.CmlClassificationTopologySpec cozy.modeler.CmlClassificationMetadataSpec cozy.modeler.CmlSemanticFoundationSpec cozy.modeler.CmlSemanticMetadataSpec cozy.modeler.CmlStructureMetadataSpec"
```

On the managed workstation the actual invocation must be routed through the
registered `cncf_command_runner` and
`/Users/asami/.agents/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh`;
wait for `lock=released` before another top-level SBT invocation. This document
records the reproducible recipe. Parent validation P542-S5423-VAL-002 passed
102 tests across all six suites, with SBT/wrapper exit 0 and lock released;
independent lightweight Step review P542-S5423-REVIEW-001 is PASS. The initial
100-success/two-failure run is retained as failed evidence; its two test-oracle
defects were corrected before this accepted run and independent review.

[`CmlClassificationMetadataFixtureSpec`](../../src/test/scala/cozy/modeler/CmlClassificationMetadataFixtureSpec.scala)
authors independent literal Classification records, projections and topology/query
oracles against the actual UTF-8 resources. FM-01–05 exercise full wire/source/absence,
independent dimensions, Term domains and Structure cohabitation. FM-06–08 mutate
actual fixture bytes to reject child shape/version, forged core binding and dangling
or wrong-kind Local references with exact safe typed diagnostics. FM-09 actively
checks at least 100 successful cases and zero discards over both modes,
independent catalog/carrier permutations and recursive key reconstruction.
Expected Classification facts never derive from resource or reader output.

## Ownership and retained acceptance

S54.2.1 is CLOSED at `3cc21f3fa99b7909400e24d2fbceca4db9460a63` with 81 focused
tests and independent review/M0 closure. S54.2.2 is CLOSED at
`1294676e5adc868670a8f72f5aa4338f60d4a33b` with 93 focused tests, independent
lightweight PASS and factual M0 document proof. Both prior manual Step commits
have verified retained native receipts. S54.2.3 has its own accepted 102-test
focused validation and independent lightweight PASS, with verified parent manual
Step commit `036a55377ea2eee04b2f95989d47c8e92f3a2b50`. The sole full Phase
review retained two authoring findings, resolved by accepted M0 cycle 1.
Final full test passed 2730 tests across 189 suites with SBT/wrapper exit 0 and the lock released. This distinct parent manual release binds closure.

[Phase 54.3](../phase/phase-54.3.md) owns future Workflow/StateMachine integration;
[Phase 54.4](../phase/phase-54.4.md) owns future Use Case/Actor integration and
fixture adoption. Both remain planned and unstarted here. CNCF owns runtime;
Textus CBD Support owns Dashboard presentation/navigation and external consumer
acceptance. This supplier authoring claims no CML parser/name projector/CLI,
external site or runtime delivery, currentness verification, or external dashboard
acceptance. The containing [Phase](../phase/phase-54.2.md) and
[checklist](../phase/phase-54.2-checklist.md) are CLOSED by this release. Their
status becomes authoritative only after the native commit and final audit succeed.
