# 2026-10-04 — Phase 76 reciprocal CNCF consumer receipt

Document role: non-normative chronological LUI-ACCEPT-01 receipt.
Scope: PHASE-76 / P76-05 / P76-05A, receiving CNCF PHASE-96 / P96-03.
Manifest: `phase96-cozy76-p76-05a-implementation-manifest-001` revision 1.
Current status: own independent P76-05 Step review PASS accepts all four
unchanged criteria; P76-05 CLOSED on its checklist basis, with local Step
commit PREPARED/PENDING, not executed. P76-01..05 CLOSED, 20/24 checked and
4 unchecked; P76-06 OPEN and Phase 76 IN_PROGRESS. P76-06, full Phase
validation/review/release and both Phase closures remain pending.

## Durable evidence and revision binding

The [CNCF consumer journal](../../../../../cloud-native-component-framework-phase96/docs/journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md)
records actual execution, source/classpath inspection, diagnostics, model
correspondence, the disclosed accumulator and independent P96-03 acceptance.
Its leading PREPARED/PENDING commit wording is retained historical commit
preparation. The subsequently executed consumer commit is bound below by
the parent-admitted handoff, not inferred from that wording.

| Receipt identity | Exact revision and role |
| --- | --- |
| Generation source | Cozy P76-03 commit `8a4c1794b7ad4557af5b4260d1233acd07520c6e`; ordinary ESS2-01 CLI generation source HEAD |
| Delivered producer | Cozy P76-04 local Step commit `ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`; actual first-run two-file LUI-GEN-01 delivery |
| Tested consumer | CNCF HEAD `fed26152045b1326e87de8a6929dc84e2fc05b18` plus the explicitly disclosed 28-path P96-03 accumulator; HEAD alone does not identify tested inputs |
| Accepted consumer | CNCF P96-03 local Step commit `bef59e729834addd3ac00c1930034bb23a8b428f`; accepted accumulator and unchanged generated sources |
| Producer coordinate | `org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`, Scala `2.12.18`, branch `codex/phase-76-logical-ui-producer` |
| Consumer coordinate | `org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`, Scala `3.3.8`, branch `codex/phase-96-display-service` |
| Fixed public ABI | `cncf.logical-ui-generated.v1`, integer version `1`, package `org.goldenport.cncf.display` |

Parent-admitted [producer acceptance handoff](../../../../../cloud-native-component-framework-phase96/.codex-workflow/phases/PHASE-96/planning/cozy76-p76-04-acceptance-handoff-001.json)
`phase96-cozy76-p76-04-acceptance-handoff-001` r2 binds the producer commit.
Parent-admitted [consumer acceptance handoff](../../../../../cloud-native-component-framework-phase96/.codex-workflow/phases/PHASE-96/planning/phase96-p96-03-acceptance-handoff-001.json)
`phase96-p96-03-acceptance-handoff-001` r2 binds the consumer commit and
unchanged focused results. The durable journals and exact product sources
remain the evidence; these control references are supplementary.

Producer independent review `phase96-cozy76-p76-04a-step-review-001` r1
retains original FINDINGS. Its coordinate blocker was resolved by accepted
parent amendment `phase96-cozy76-p76-04a-coordinate-m0-amendment-001` r1;
it is not relabeled PASS. Consumer independent whole-Step review
`phase96-p96-03-step-review-001` r1 by
`/root/phase96_p96_03_step_review_001` returned PASS for all 28 paths and
19 target programs. Accepted parent M0
`phase96-p96-03-step-commit-m0-001` r1 and original CNCF ledger
`phase96-review-ledger-001` r20 retain that review and actual local commit.
These Step results do not establish either full Phase review or closure.

## Same actual generated sources and independent provenance

The [ordinary ESS2-01 CML](../../../../src/test/resources/logicalui/ess2-01.cml)
declares COMPONENT `Domain`, package `domain`. The
[producer generation journal](2026-10-04-phase-76-generated-handoff.md)
retains both actual ordinary CLI commands, two-run/golden/delivery byte
correspondence, terminal locks and 254/254 producer/runtime tests in six
suites. Its second-run Logback JoranException remains an unresolved
observation; this receipt makes no logger repair or cause claim.

| Actual committed producer resource | Unmodified ordinary CNCF Test source |
| --- | --- |
| [DomainLogicalUiDefinitions.scala.txt](../../../../src/test/resources/logicalui/generated/ess2-01/DomainLogicalUiDefinitions.scala.txt) | [DomainLogicalUiDefinitions.scala](../../../../../cloud-native-component-framework-phase96/src/test/scala/domain/logicalui/DomainLogicalUiDefinitions.scala) |
| [DomainLogicalUiMetadataProvider.scala.txt](../../../../src/test/resources/logicalui/generated/ess2-01/DomainLogicalUiMetadataProvider.scala.txt) | [DomainLogicalUiMetadataProvider.scala](../../../../../cloud-native-component-framework-phase96/src/test/scala/domain/logicalui/DomainLogicalUiMetadataProvider.scala) |

The public identities are `domain.logicalui.DomainLogicalUiDefinitions` and
`domain.logicalui.DomainLogicalUiMetadataProvider`. Definitions and provider
are respectively 2771 and 329 bytes, UTF-8/LF with final newline according
to accepted producer evidence. Both consumer source comparisons exited 0;
no added header, surrogate, copied ABI implementation or generator adapter
changes delivered text. Golden files remain expectations, not source delivery.

Actual declared provenance is `cozy.logical-ui-generated.v1` / `ess2-01` /
`ess2-01-cml-1`; both definitions retain `ess-definition-1` and
`ess.reference`. The independent handwritten
[TypedDisplayProjectionFixture](../../../../../cloud-native-component-framework-phase96/src/test/scala/logicalui/TypedDisplayProjectionFixture.scala)
retains `cncf.expected-generated-fixture` / `ess2-01` / `fixture-1`.
GC2 compares structural `Display.model` equality and separately compares
ABI, semantic descriptor, screen IDs/order, definition revisions, View keys
and screen structure while asserting distinct provenance. It does not assert
full Definition equality or normalize provenance. Git revisions, declared
source/definition revisions and runtime EntityId/EntityRevision retain
separate meanings.

## Actual focused consumer commands and terminal results

Commands below are relative to the owning CNCF repository
`cloud-native-component-framework-phase96`. They record previously executed
logical SBT argv, routed sequentially through the registered serial runner;
this documentary slice runs no validation.

Representative request `phase96-p96-03c-representative-request-001` r1,
attempt `phase96-p96-03c-representative-001`, result
`phase96-p96-03c-representative-result-001` r1 used logical argv `--batch`
followed, in order, by:

```text
show Test / fullClasspath
testOnly org.goldenport.cncf.component.ComponentFactoryGeneratedLogicalUiAcceptanceSpec
```

Ordinary Compile/Test succeeded; GC1..GC6/GCP1 passed 7/7 tests in one
completed suite. Complete Test/fullClasspath inspection at actual stdout
lines 14–136 found no `cozy_2.12` runtime jar. Executor, SBT and wrapper
exits were 0; the shared serial lock was released. There were zero failed,
aborted, canceled, ignored or pending tests/suites.

Accumulator request `phase96-p96-03c-accumulator-request-001` r1,
attempt `phase96-p96-03c-accumulator-001`, result
`phase96-p96-03c-accumulator-result-001` r1 used logical argv `--batch`
followed by:

```text
testOnly org.goldenport.cncf.display.DisplayRuntimeSpec org.goldenport.cncf.component.ComponentFactoryDisplayRuntimeSpec org.goldenport.cncf.entity.view.ViewSpaceSpec org.goldenport.cncf.component.ComponentFactoryLogicalUiBootstrapSpec org.goldenport.cncf.display.DisplayProjectionSpec org.goldenport.cncf.display.DisplayProjectionPropertySpec org.goldenport.cncf.display.GeneratedLogicalUiAbiSpec org.goldenport.cncf.display.GeneratedLogicalUiAbiPropertySpec org.goldenport.cncf.display.DisplayModelSpec org.goldenport.cncf.display.DisplayModelPropertySpec org.goldenport.cncf.workflow.GeneratedWorkflowAbiSpec org.goldenport.cncf.component.ComponentFactoryAggregateViewBootstrapSpec org.goldenport.cncf.component.ComponentFactoryStateMachineProviderBootstrapSpec org.goldenport.cncf.component.ComponentFactoryContinuationRuntimeBootstrapSpec org.goldenport.cncf.component.ComponentFactoryRuntimePlanActivationSpec
```

All 133/133 tests passed across 15 completed suites, with zero failed,
aborted, canceled, ignored or pending tests/suites. Executor, SBT and wrapper
exits were 0; the shared serial lock was released. Combined focused evidence
is 140/140 tests across 16 completed suites, not a repository full-suite run.
The [durable actual-execution entry](../../../../../cloud-native-component-framework-phase96/docs/journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md#2026-10-04--actual-focused-execution-and-sourceclasspath-inspection)
retains actual logs, intakes, summaries and source comparisons for both
portable request/result identities. Validation subject
`phase96-p96-03c-validation-subject-001` r1 identifies the tested accumulator.

Ordinary CNCF Compile/Test also ran its existing
`src/main/cozy/information.cml` build hook under `target/cncf-information-cml`.
That is distinct from rerunning the Cozy Phase76 ESS2-01 Logical UI producer.
No ESS2-01 regeneration, dependency refresh or publication occurred in this
consumer receipt. Unchanged program/generated/dependency inputs allow reuse
of these accepted results for this documentary slice.

## Mapping the four unchanged P76-05 source criteria

The [consumer acceptance specification](../../../../../cloud-native-component-framework-phase96/src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedLogicalUiAcceptanceSpec.scala)
uses the [actual/expected consumer fixture](../../../../../cloud-native-component-framework-phase96/src/test/scala/logicalui/GeneratedDisplayConsumerFixture.scala)
and normal ComponentFactory bootstrap. The retained
[GeneratedLogicalUiAbiSpec](../../../../../cloud-native-component-framework-phase96/src/test/scala/org/goldenport/cncf/display/GeneratedLogicalUiAbiSpec.scala)
and [GeneratedLogicalUiAbiPropertySpec](../../../../../cloud-native-component-framework-phase96/src/test/scala/org/goldenport/cncf/display/GeneratedLogicalUiAbiPropertySpec.scala)
supply additional incompatible-descriptor and duplicate-scope regression proof.
The [LUI-ABI-01 journal](../../../../../cloud-native-component-framework-phase96/docs/journal/2026/10/2026-10-04-phase-96-logical-ui-abi-handoff.md)
and [fixed ABI specification](../../../../../cloud-native-component-framework-phase96/docs/spec/logical-ui-generated-abi.md)
retain the agreed contract.

| Unchanged P76-05 source criterion | Received actual evidence |
| --- | --- |
| CNCF P96-03 compiles the LUI-GEN-01 Scala 3 fixture against the agreed ABI without depending on `cozy_2.12`; the actual consumer project/fixture and command/result are recorded. | The two unmodified delivered ordinary Test sources compile under Scala3.3.8; both comparisons exit0; representative classpath inspection excludes a Cozy runtime jar; exact commands/results are above. |
| ComponentFactory discovers and admits the generated provider and definitions, and focused evidence covers the agreed invalid/duplicate/incompatible admission diagnostics. | GC1/GC4 normal bootstrap discovers and publishes the complete vector once, retaining identities and idle sources. GC5 actual-derived ABI2, duplicate screen index2, malformed revision and invalid target preserve typed code/path/reason without fallback, runtime evaluation or publication. GC6 wrong final Detail View binding retains its typed diagnostic and publishes no Display prefix. ABI specs additionally cover incompatible descriptor and duplicate scopes. |
| The generated fixture produces the same reference List/Detail Display Projection as the LUI-ABI-01 hand-written fixture, preserving semantics, values, identities and ordering. | GC2 structural List/Detail model equality and separate ABI/descriptor/IDs/order/revisions/provenance assertions; GC3 preserves Unicode/whitespace, reverse supplied order, empty List/snapshot None and caller context with independent semantic calls counted separately. GCP1 passed executed assertions requiring at least100 successful generated cases and zero discards; no exact separately printed case count is claimed. |
| LUI-ACCEPT-01 binds exact producer, consumer and fixture revisions/identities and focused results; both ledgers reference this receipt, not a whole-Phase closure assumption or producer-only text comparison. | Producer delivery and consumer accepted commits, tested context, declared provenance, source mapping and request/result identities are bound above. CNCF P96-03 criterion7 already accepts and links the durable consumer journal; the Cozy ledger references this reciprocal receipt with its own independent Step review accepted; local Step commit remains PREPARED/PENDING, not executed. |

This receipt connects the [Cozy P76-05 ledger](../../../phase/phase-76-checklist.md#p76-05-cncf-consumer-acceptance)
and [CNCF P96-03 ledger](../../../../../cloud-native-component-framework-phase96/docs/phase/phase-96-checklist.md#p96-03-componentfactory-admission-and-display-projection)
to shared LUI-ACCEPT-01 proof. CNCF P96-01..03 are CLOSED; P96-04..11 are
OPEN. Cozy P76-01..05 are CLOSED after own independent P76-05 Step review
PASS accepts all four unchanged criteria; P76-06 remains OPEN, with 20 checked /
4 unchecked obligations and Phase 76 IN_PROGRESS. P76-05's local Step commit
is PREPARED/PENDING, not executed. P76-06, final aggregate/release validation,
each admitted full Phase review, release and both Phase closures remain pending.
No CNCF later REST/mutation or app E2E completion is inferred.

## Debt and executable-specification boundary

No executable behavior, Scala source, generated text, metadata or closure test
changes in this slice; executable-spec authoring and same-file target-program
debt are not applicable. Current documentary commit/consumer-pending projections
are updated in the six owned documents; chronological earlier statements
remain history. Known Hygiene is retained for separate follow-up:
`HYG-P96-DOC-001`, `HYG-P96-02-001`, `HYG-P96-PARSER-SIZE-001`,
`HYG-COZY76-P7603-MODELER-LOCAL-NAMING-001`,
`HYG-COZY76-P7603-MODELER-RESPONSIBILITY-001`,
`HYG-COZY76-P7603-README-CLI-PROJECTION-001` and
`HYG-P96-03-SPEC-TRACEABILITY-001`. Two accepted records are already persisted;
five await the separate documentation/release gate. No Hygiene repair,
materialization or new Development Candidate is admitted by this receipt.
Phase74 semantic authority and Phase51 PLANNED remain unchanged; no Flutter
execution is claimed.

## 2026-10-04 — P76-05A own independent Step acceptance

Independent own Step review `phase96-cozy76-p76-05a-step-review-001` r1 by
`/root/phase96_cozy76_p76_05a_step_review_001` returned PASS for the complete
six-document P76-05A boundary. Actual sealed source
`phase96-cozy76-p76-05a-step-review-source-001` r1 records all four unchanged
criteria as MET: same-source Scala 3 compilation without `cozy_2.12`, normal
ComponentFactory admission and negative diagnostics, equivalent reference
List/Detail models with separate provenance, and exact reciprocal
revision/fixture/result binding. It reports zero CPB, no new Hygiene and no
Development Candidate. The parent admitted/read back this result in original
CNCF ledger `phase96-review-ledger-001` r21.

Accepted evidence remains the source/diagnostic/model/revision proof and
7 + 133 = 140/140 focused tests across 16 completed suites recorded above;
both requests retain SBT/wrapper exit 0 and shared lock released. This entry
adds no execution, regeneration or publication. All four existing P76-05
checklist marks are now checked as the closure basis: P76-01..05 CLOSED,
20/24 checked and 4 unchecked, P76-06 OPEN and Phase 76 IN_PROGRESS. The
local P76-05 Step commit is PREPARED/PENDING, not executed. P76-06, final
aggregate/release validation, each admitted full Phase review, release and
both Phase closures remain pending.

Original producer FINDINGS/coordinate M0, actual commits, commands, fixtures,
coordinates, declared provenance, independent model claims, logger observation
and all preceding historical entries remain retained. The seven known Hygiene
items above remain separate follow-up; none is fixed or materialized here.
Original Goal/binding/bases/PLAN epoch and repair history are unchanged;
P76-05 cumulative repair and consecutive non-convergence remain zero, neither
incremented nor reset by this documentary projection.
