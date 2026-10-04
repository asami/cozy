# Phase 72: CNCF91 and sm-workflow Failure Model handoff

*Updated:* Oct. 3, 2026

This is the authored S72.3 HANDOFF for [Phase 72](phase-72.md). SOURCE is
accepted at `6c15b65cd83181fdf61aa8674c9a847f79751805`; ABI is accepted at
`1d9c80775e74f7d0ba0dd60f1cf4730ef84c3843`. HANDOFF static validation is
accepted and independent selected Step review is PASS with zero blockers. Parent manual local HANDOFF commit
`783a48552de5fa6a4b34a8dfe9c1184f005de62f` records acceptance. The sole
Phase-wide review passed. Final full validation passed; the containing distinct parent manual local release commit closes Cozy Phase 72 in the [checklist](phase-72-checklist.md).

## Authority and ownership

Recipient authority is repository `goldenport-cncf`, repository-relative
`docs/phase/phase-91.md` and `docs/notes/failure-model-and-execution-model.md`.
These define the defaults → Component → Service → Operation resolution boundary
and the chain Execution Model → Failure Model → Required Robustness → Mechanism.

| Owner | Responsibility | Acceptance boundary |
|---|---|---|
| Cozy / CML Phase 72 | Strict source admission; immutable source-correlated defaults and deltas; deterministic JSON/Scala development metadata and effective comparison oracle | SOURCE, ABI and HANDOFF accepted; sole Phase-wide review PASS |
| CNCF Phase 91 | Canonical `ExecutionModel`, `FailureModel`, `ResolvedFailureModel` types; recipient validation; runtime resolver and exposure to CAR/development consumers | Receiver construction, ingestion hookup and runtime integration remain recipient-owned and pending |
| sm-workflow | Implementation guidance using only CNCF recipient-resolved effective contracts at the named owner | Depends on CNCF receiver acceptance; workflow/AI wiring is unverified |

The current CNCF source search found no canonical definition of these three
types; the `ProcessExecutionModelSpec` substring was unrelated. This limits
verification and does not establish receiver completion. The sm-workflow
checkout and code remain unverified after current workspace inventory. No runtime
API, transport, registry, wrapper, descriptor flag or publication hookup is
specified here.

## Accepted artifacts and recipient reproduction

The normative [SOURCE contract](../spec/failure-model-source-contract.md) fixes
admission and ordered refinement. The normative
[ABI contract](../spec/failure-model-abi-contract.md) fixes schema
`cozy.failure-model.v1`, recursive field order, nine immutable metadata shapes,
`FailureScope` tokens and Option encoding. The
[source design](../design/failure-model-source-lowering.md) and
[ABI design](../design/failure-model-abi-lowering.md) explain lowering; the
[authoring note](../notes/failure-model-cml-authoring.md) is explanatory.

Durable source and executable evidence:

- [failure-model.cml](../../src/test/resources/modeler/failure-model.cml): actual
  local single-user/single-writer authored fixture.
- [FailureModelCmlSpec.scala](../../src/test/scala/cozy/modeler/FailureModelCmlSpec.scala):
  admission, owner resolution, ordering, inheritance and invalid-source examples.
- [FailureModelAbiGenerationSpec.scala](../../src/test/scala/cozy/modeler/FailureModelAbiGenerationSpec.scala):
  real normal/value/mixed generation, full decoded contracts, legacy bytes,
  library rejection, old callers, escaped/optional source identities and seeded
  100-sample order/origin/winner comparisons.
- [failure-model-legacy-baseline.json](../../src/test/resources/modeler/failure-model-legacy-baseline.json):
  independent accepted SOURCE baseline, four routes and 80 original files.

Accepted ABI validation passed 105/105 tests across six suites. Actual generated
normal/value/mixed consumer groups compiled with Scala 3.3.8, with exact source
inventories 2/2/5. SBT/wrapper exits were 0 and serial locks released; independent
protected ABI review passed with zero current blockers. Existing executed evidence
is under `target/test-generated/failure-model-normal`,
`target/test-generated/failure-model-value` and
`target/test-generated/failure-model-mixed`. These generated artifacts are local
evidence; the fixture and specifications above are durable.

From the Cozy repository root, recipients can reproduce the accepted fixture
through the public CLI:

```sh
sbt --batch 'runMain cozy.Cozy modeler-scala src/test/resources/modeler/failure-model.cml --save target/phase72-handoff/normal'
sbt --batch 'runMain cozy.Cozy modeler-scala-value src/test/resources/modeler/failure-model.cml --save target/phase72-handoff/value'
```

These are recipient reproduction instructions, not commands newly executed for
this document. Follow the registered serialized SBT execution policy for this
workspace. Both routes use distinct save roots; each product path below is
relative to its `--save` root, including the nested `target` directory:

| Product | Path relative to each save root |
|---|---|
| JSON sidecar | `target/cozy/failure-model.json` |
| Scala metadata | `target/scala-3.3.8/src_managed/main/scala/cozy/generated/failuremodel/FailureModelMetadata.scala` |

For example, normal JSON is
`target/phase72-handoff/normal/target/cozy/failure-model.json`, and value Scala is
`target/phase72-handoff/value/target/scala-3.3.8/src_managed/main/scala/cozy/generated/failuremodel/FailureModelMetadata.scala`.
The Scala package/object is `cozy.generated.failuremodel.FailureModelMetadata`.
JSON and Scala encode the same ordered source IR and effective development data;
the latter is comparison evidence, not CNCF's canonical runtime contract.

## Concrete effective comparison oracle

Every row retains this exact failure order: `malformed-input`, `concurrent-write`,
`network-partition`. These are explicit authored identities, not inferred defaults
from the `LocalSingleWriter` name.

| Owner | malformed-input | concurrent-write | network-partition |
|---|---|---|---|
| EXECUTION-MODEL/LocalSingleWriter defaults | IN_SCOPE | OUT_OF_SCOPE | OUT_OF_SCOPE |
| COMPONENT/LocalStore | IN_SCOPE | IN_SCOPE | OUT_OF_SCOPE |
| SERVICE/Store | IN_SCOPE | OUT_OF_SCOPE | OUT_OF_SCOPE |
| OPERATION/Store/save | IN_SCOPE | OUT_OF_SCOPE | IN_SCOPE |
| OPERATION/Store/remove | IN_SCOPE | OUT_OF_SCOPE | OUT_OF_SCOPE |
| SERVICE/Archive | IN_SCOPE | IN_SCOPE | OUT_OF_SCOPE |
| OPERATION/Archive/archive | IN_SCOPE | IN_SCOPE | OUT_OF_SCOPE |

All winners below have `declarationKind = FAILURE-MODEL`; lines refer to the
actual durable fixture. For all effective owners, malformed-input has the single
IN_SCOPE origin and winner
`EXECUTION-MODEL/LocalSingleWriter/FAILURE-MODEL/malformed-input`, line 7.

| Owners / entry | Ordered origins | Winning sectionPath / line |
|---|---|---|
| LocalStore, Archive, Archive/archive: concurrent-write | OUT_OF_SCOPE → IN_SCOPE | `COMPONENT/LocalStore/FAILURE-MODEL/concurrent-write`, 30 |
| Store, Store/save, Store/remove: concurrent-write | OUT_OF_SCOPE → IN_SCOPE → OUT_OF_SCOPE | `SERVICE/Store/FAILURE-MODEL/concurrent-write`, 39 |
| Store/save: network-partition | OUT_OF_SCOPE → IN_SCOPE | `SERVICE/Store/OPERATION/save/FAILURE-MODEL/network-partition`, 55 |
| All other effective owners: network-partition | OUT_OF_SCOPE | `EXECUTION-MODEL/LocalSingleWriter/FAILURE-MODEL/network-partition`, 13 |

The first concurrent-write origin is
`EXECUTION-MODEL/LocalSingleWriter/FAILURE-MODEL/concurrent-write`, line 10;
the second is the component declaration at line 30; Store adds line 39.
The network-partition chain for save begins at line 13 and adds line 55.
Thus remove inherits Store's concurrent-write exclusion without inheriting save's
network-partition delta. Archive and Archive/archive follow their own component
chain, independently of Store's service override and save's operation override.

OUT_OF_SCOPE constrains the effective owner. A narrower explicit IN_SCOPE can
override an ancestor OUT_OF_SCOPE, just as Store's OUT_OF_SCOPE overrides the
component's IN_SCOPE. Neither scope nor execution names choose locks, retries,
rollback, hashes, durability or distributed behavior.

## CNCF91 recipient acceptance work

The CNCF owner must admit the explicit `cozy.failure-model.v1` schema and its
scope tokens, Option encoding, original source identities, owners, ordered
execution defaults and per-owner deltas. It must construct its own canonical
resolved contract by applying defaults → component → service → operation, then
compare every owner, entry, order, origin chain and winning source against the
development oracle above and the complete generated JSON/Scala data.

Recipient fixtures must cover both override directions, inherited remove and
independent Archive chains, invalid structure/scope, unknown execution/failure,
duplicates including equal repeated values, orphan or mismatched owners and
source identity/optional-location cases. Preserve the accepted SOURCE rejection
boundary: invalid source has no partial successful contract. Test legacy absence
separately: no declarations yield no new generated Failure Model products, not an
inferred contract. Preserve original defaulted callers, explicit library-target
rejection and Workflow v5 coexistence as accepted ABI compatibility evidence.

Receiver acceptance must record canonical type/resolver evidence, generated-input
admission, fixture comparisons and the resolved contract made available to its
consumers. Those deliverables are unmet prerequisites here. Missing source
artifacts, unsupported/missing schema or absent recipient resolution must be
reported as unmet prerequisites, never repaired by guessing defaults from an
execution name or treating raw deltas as implementation guidance.

## sm-workflow guidance after receiver acceptance

After CNCF acceptance, sm-workflow must use only the recipient-resolved effective
contract at the named Component, Service or Operation. For example, Store/save
excludes concurrent-write and includes network-partition; Store/remove excludes
both; Archive/archive includes concurrent-write and excludes network-partition.
Guidance must retain these owner distinctions and source traceability.

At an effective owner, OUT_OF_SCOPE excludes defensive mechanisms justified
solely by that excluded failure. IN_SCOPE identifies a robustness responsibility
but chooses no mechanism without explicit requirements. Design of mechanism
catalogs, wrappers, transport, registries and descriptor flags is outside this
handoff. Workflow acceptance and AI wiring remain pending until the recipient
resolution prerequisite and workflow evidence exist.

## Existing separate follow-up

The accepted nonblocking naming-maintenance record is preserved in the
[Phase 72 hygiene follow-up](../journal/2026/10/2026-10-03-phase-72-hygiene-follow-up.md).
It remains a separately authorized maintenance boundary and does not change
Failure Model acceptance or authorize program edits in this HANDOFF.

## Cozy producer closure

P72-FINAL-VAL-001 passed: 2,688 succeeded, 0 failed, 9 canceled, 0 ignored, 0 pending; 186 suites, 0 aborted; SBT/wrapper exit 0 and `lock=released`. The containing distinct parent manual local release closes Cozy Phase 72 after accepted SOURCE, ABI and HANDOFF commits and its sole comprehensive Phase review. Existing cancellations retain the unchanged original specifications. CNCF91 receiver admission/runtime resolution and sm-workflow wiring remain recipient-owned prerequisites; the recipient acceptance checklist above remains unchecked.
