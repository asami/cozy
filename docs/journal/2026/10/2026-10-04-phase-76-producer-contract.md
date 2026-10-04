# 2026-10-04 — Phase 76 producer contract

Document role: non-normative source-contract and authoring history.
Scope: PHASE-76 / P76-01 / P76-01A, parent PLAN epoch 1.
Manifest: `phase76-p76-01a-implementation-001` revision 1.

## Received source contract

LUI-ABI-01 is the received fixed CNCF v1 source contract, as recorded in the
[consumer handoff](../../../../../cloud-native-component-framework-phase96/docs/journal/2026/10/2026-10-04-phase-96-logical-ui-abi-handoff.md)
and [normative consumer specification](../../../../../cloud-native-component-framework-phase96/docs/spec/logical-ui-generated-abi.md).
The read-only consumer checkout is branch `codex/phase-96-display-service`,
HEAD `fed26152045b1326e87de8a6929dc84e2fc05b18`. Its dirty P96-03 A/B1/B2
accumulator is required: HEAD alone is insufficient. The handoff describes
A static bootstrap, B1 typed projection and B2 contextual bindings/publication
as authored and focused-validated, without independent acceptance or a B2
commit. LUI-GEN-01 and LUI-ACCEPT-01 are still pending.

Read-only Git inspection matched that HEAD and observed modified runtime
design/spec, Component, ComponentFactory, ComponentFactoryEntityPlanPart and
ViewSpace, plus untracked Display bootstrap/binding/projection/runtime sources,
typed consumer fixtures, focused specifications and A/B1/B2/handoff journals.
This is disclosed working-tree source context, not a clean committed artifact.

Source-build coordinates are
`org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`, Scala `3.3.8`; they do not
assert publication. Public package is `org.goldenport.cncf.display`, ABI
identity `cncf.logical-ui-generated.v1`, integer version `1`. The exact
SemanticContract/DriverReference and provider signature are linked from the
[new producer spec](../../../spec/logical-ui-generated-producer.md).

## Existing focused source receipt

The consumer journal records actual command request
`phase96-p96-03b2-command-request-005` r1, result
`phase96-p96-03b2-command-result-005` r1 and validation subject
`phase96-p96-03b2-validation-subject-001` r5. Its exact testOnly argv and
operational result/log/summary paths remain in the linked handoff. It reports
15 completed suites, 133/133 tests passed, 0 aborted, SBT and wrapper exit 0,
serial lock released, total time 71 s, completion 2026-10-04 01:39:31 JST.
Compilation was that command's prerequisite. This entry cites that source
receipt; no test was run by P76-01A.

Coverage includes external handwritten public-ABI fixture compilation/admission,
factory selection and late atomic typed join, typed projection/contextual
execution and the retained Workflow/Aggregate/View/StateMachine/Continuation/
runtime-plan regressions. It proves no Cozy round-trip, repository full test,
independent P96-03 acceptance, Step commit or publication.

## Parent-decided producer mapping

Parent `phase76-cycle-plan-001` r1 freezes the complete descriptor, shared roles,
static kind mapping, explicit COMPONENT ownership, strict ordinary CML prologue
grammar, scoped uniqueness, deterministic diagnostics and both CNCF generation
routes with library isolation. P76-01A transcribes those decisions in the
[spec](../../../spec/logical-ui-generated-producer.md) and
[design](../../../design/logical-ui-generated-producer.md); it selects no new
syntax, vocabulary or runtime authority. Their independent Step review and
implementation verification remain pending.

The consumer
[ExpectedGeneratedLogicalUiFixture](../../../../../cloud-native-component-framework-phase96/src/test/scala/logicalui/ExpectedGeneratedLogicalUiFixture.scala)
is HAND-WRITTEN, not Cozy-generated. The producer reference uses COMPONENT
Domain, package domain, definition revision ess-definition-1, View ess.reference
and provenance cozy.logical-ui-generated.v1 / ess2-01 / ess2-01-cml-1. Consumer
sample provenance cncf.expected-generated-fixture / ess2-01 / fixture-1 is not
actual producer provenance.

Both references map List collecting-resources / Collecting Resources to Detail
capture-detail / Capture Detail, section main with None title. List fields are
title / Title / Title role / ess.title / Text, then stage / Capture stage /
Status role / ess.stage / Text. Detail adds description / Description /
Description role / ess.description / Text after those fields. List actions and
itemActions are empty; Detail action is request-review / Request Review /
ess.request-review. Bindings stay application-owned and imply no setter,
current value, capability or Operation invocation.

## Documentary scope and remaining evidence

Existing planning changes and the
[2026-10-03 reconciliation](2026-10-03-phase-76-cncf96-plan-reconciliation.md)
remain chronological history. Phase 76 and P76-01 are IN_PROGRESS. All 24
obligations remain unchecked; generation, consumer and release stages remain
OPEN. The proposal remains non-normative; the fixed source contract supersedes
its earlier candidate wording without asserting a public ABI release.

Phase 51 Flutter Target Projection is PLANNED and no Flutter source/suites
exist in this checkout. Preserve its plan and target-neutral compile-time
Logical UI semantics; later regression uses existing Logical UI/runtime suites.
No Flutter generator/widget execution or Phase 51 implementation is claimed.

No executable source, Executable Specification or fixture is authored in this
Slice. The spec enumerates future Given/When/Then and ScalaCheck obligations;
Markdown scenarios are not executable proof. Parent validation, independent
review, acceptance and commit remain pending. LUI-GEN-01 must record actual
output generated twice by ordinary CLI with input/command/revisions/deltas;
LUI-ACCEPT-01 must consume those same files. No handwritten fixture, golden
comparison or this document closes those obligations.

## 2026-10-04 — Documentary acceptance

The retained Cozy Step review `phase76-p76-01-review-001` r1 by
`/root/phase76_p76_01_review` records PASS with zero blockers/findings. Its
original source evidence remains at
`.codex-workflow/phases/PHASE-76/reviews/phase76-p76-01-review-001-source.json`,
retained by Cozy ledger `phase76-review-ledger-001` r3. The parent admitted
the four P76-01 documentary obligations under
`phase96-cozy76-p76-01-acceptance-handoff-001` r1. The
[current checklist](../../../phase/phase-76-checklist.md) records P76-01 CLOSED
with 4/24 obligations accepted; Phase 76 remains IN_PROGRESS and P76-02..06
remain OPEN with the other 20 obligations unchecked. Local Step commit is
pending.

This status-only bookkeeping preserves the producer contracts and earlier
historical entries. Cozy and CNCF retain separate ledgers and the original
review/source evidence; it creates no new primary CNCF review credit and does
not activate the original blocked Cozy native Goal. Implementation verification,
actual generation, LUI-GEN-01, LUI-ACCEPT-01, full Phase acceptance and release
remain pending. No command, test or commit execution is claimed by this edit.

## 2026-10-04 — P76-02A implementation continuation

The P76-01 documentary Step was committed locally as
`0224d56cd5a1a94ab110b7e776e6922264eb7950`. Under frozen manifest
`phase96-cozy76-p76-02a-implementation-manifest-002` r1, the interrupted
implementation continues with immutable static definitions, ordered pure
validation, ordinary AST/source-COMPONENT extraction and complete consumer
constructor projection. The existing drafts were preserved and accommodated.

[LogicalUiProducerSpec](../../../../src/test/scala/cozy/modeler/LogicalUiProducerSpec.scala)
and [LogicalUiCmlSpec](../../../../src/test/scala/cozy/modeler/LogicalUiCmlSpec.scala)
author adjacent Given/When/Then behavior and active ScalaCheck properties
with at least 100 successful cases requested. The ordinary
[ESS2-01 fixture](../../../../src/test/resources/logicalui/ess2-01.cml) carries
declared producer provenance separately from definition revisions and the
fixed semantic descriptor.

P76-02 is IN_PROGRESS with all four obligations unchecked; 4/24 obligations
remain accepted. Parent focused validation, independent review and acceptance
remain pending. No test, generation, runtime activation, LUI-GEN-01,
LUI-ACCEPT-01, commit or Phase closure is claimed by this authoring entry.
The original Cozy Goal/history and both separate Phase ledgers are retained.

## 2026-10-04 — P76-02A validation and independent acceptance

Parent representative validation request
`phase96-cozy76-p76-02a-representative-request-003` r1 and result
`phase96-cozy76-p76-02a-representative-result-003` r1 passed 176/176 tests
across two suites. Accumulator request
`phase96-cozy76-p76-02a-accumulator-request-001` r1 and result
`phase96-cozy76-p76-02a-accumulator-result-001` r1 passed 14/14 tests across
two suites. Both use common subject `phase96-cozy76-p76-02a-subject-001` r3;
the current typed readers accepted both results. Total focused evidence is
176 + 14 = 190/190 PASS across four suites, including nine ScalaCheck
properties with at least 100 successful cases. Both commands completed with
SBT/wrapper exit 0 and serial lock release. This entry records those actual
parent results; no new validation execution is claimed.

The first P76-02A independent Step review
`phase96-cozy76-p76-02a-step-review-001` r1 by
`/root/phase96_cozy76_p76_02a_step_review_001` is PASS with zero CPB/HYG/DEV
findings. Original CNCF primary ledger `phase96-review-ledger-001` r12
attributes this coordinated review explicitly to Cozy PHASE-76/P76-02/P76-02A.
Historic separate Cozy ledger `phase76-review-ledger-001` r3 remains unchanged
under its original blocked Goal. The three bounded IMPLEMENT prevalidation
repairs remain retained history; review repair cycles are 0 and full Phase
reviews are 0. Neither Goal nor history is rebound or reset.

Under `phase96-cozy76-p76-02-acceptance-handoff-001` r1, all four P76-02
pure-producer criteria are accepted. The [current checklist](../../../phase/phase-76-checklist.md)
records P76-01 and P76-02 CLOSED with 8/24 obligations accepted and 16 unchecked;
P76-03..06 remain OPEN and Phase 76 remains IN_PROGRESS. P76-01 retains local
commit `0224d56cd5a1a94ab110b7e776e6922264eb7950`; the local P76-02 Step
acceptance commit is PREPARED/PENDING, not executed.

Actual Scala 3 rendering/generation/wiring, runtime activation, LUI-GEN-01,
LUI-ACCEPT-01, generated consumer acceptance, repository full tests, public
publication and either Phase closure are not claimed. This documentary
acceptance projection preserves the normative contracts and prior journal
entries; it supplies no whole-Phase acceptance or release evidence.

## 2026-10-04 — P76-03A source generation authoring

P76-02's accepted local Step commit is now
`d9e106ca7d154402487274e189063f4276d0d94a`, following the retained 190/190
focused tests and independent PASS recorded above. Earlier PREPARED/PENDING
entries remain their chronological snapshots. Cozy remains on
`codex/phase-76-logical-ui-producer`; the CNCF source head remains
`fed26152045b1326e87de8a6929dc84e2fc05b18` and the quote prerequisite head is
`e2119e3b6e69d4564ee8a3c5a280be4753654fa0`.

Frozen manifest `phase96-cozy76-p76-03a-implementation-manifest-001` revision 1
authors the separate renderer, bounded preflight in both ordinary Modeler
routes, appended defaulted optional Realm integration and structured library
source/direct-generator guards. Whole-vector producer projection precedes
Realm construction; generated definitions/provider refer only to the public
CNCF ABI. Diagnostic code/path/reason remains typed Conclusion data. Ordinary
Scala literals preserve declared strings and vector order without generated
time or machine-path metadata.

The [generation specification](../../../../src/test/scala/cozy/modeler/LogicalUiAbiGenerationSpec.scala)
authors adjacent Given/When/Then scenarios for pure renderer and both ordinary
in-process Modeler routes, exact ESS2-01 artifacts, all closed roles/kinds,
multiple explicit owners, repeatable UTF-8 bytes, safe decoded literals,
whole-vector/late rejection, typed failures, absent behavior and library/null
guards. Three ScalaCheck properties request at least 100 successful cases.
The [definitions golden](../../../../src/test/resources/logicalui/golden/DomainLogicalUiDefinitions.scala.txt)
and [provider golden](../../../../src/test/resources/logicalui/golden/DomainLogicalUiMetadataProvider.scala.txt)
are expectations, not actual producer handoff files or CNCF compilation proof.
The ordinary [ESS2-01 CML](../../../../src/test/resources/logicalui/ess2-01.cml)
is reused without modification.

P76-03 is IN_PROGRESS with all four obligations unchecked; P76-01/02 remain
CLOSED, eight obligations accepted and sixteen unchecked overall. P76-04..06
remain OPEN and Phase 76 remains IN_PROGRESS. Parent focused validation,
fresh independent Step review/acceptance and P76-03 commit are pending.
This entry claims no new test PASS, CLI generation, runtime activation,
LUI-GEN-01, LUI-ACCEPT-01, consumer acceptance, full Phase validation,
publication or Phase closure. Phase 74 authority, Phase 51 PLANNED and the
original CNCF/Cozy Goals and historical ledgers remain retained.

## 2026-10-04 — P76-03A focused validation and independent Step acceptance

Parent representative request
`phase96-cozy76-p76-03a-representative-request-003` r1 and result
`phase96-cozy76-p76-03a-representative-result-003` r1 passed 56/56 tests across
one suite. Accumulator request
`phase96-cozy76-p76-03a-accumulator-request-001` r1 and result
`phase96-cozy76-p76-03a-accumulator-result-001` r1 passed 266/266 tests across
eight suites. Both use common subject `phase96-cozy76-p76-03a-subject-001` r3;
both actual typed readers accepted the terminal results. Total focused
evidence is 56 + 266 = 322/322 PASS across nine suites, including three active
ScalaCheck properties with at least 100 successful cases in the generation
specification. Both commands completed with SBT/wrapper exit 0 and serial
lock release. This entry records existing parent results.

Independent Step review `phase96-cozy76-p76-03a-step-review-001` r1 by
`/root/phase96_cozy76_p76_03a_step_review_001` is PASS with zero CPB, zero DEV
and three nonblocking Hygiene items:

- `HYG-COZY76-P7603-MODELER-LOCAL-NAMING-001`;
- `HYG-COZY76-P7603-MODELER-RESPONSIBILITY-001`;
- `HYG-COZY76-P7603-README-CLI-PROJECTION-001`.

Original CNCF primary typed ledger `phase96-review-ledger-001` r14 records
these items and attributes the Step review to Cozy PHASE-76/P76-03/P76-03A.
Their dedicated Hygiene journal materialization remains later work; this
entry performs no cleanup. The original CNCF Goal, binding, bases, PLAN epoch,
review history and coordinated repository additions remain retained. Historic
separate Cozy ledger `phase76-review-ledger-001` r3 remains read-only under its
original blocked Goal.

The two actual prevalidation repairs remain retained:
`phase96-cozy76-p76-03a-import-fix-001` resolved the admitted import ambiguity,
and `phase96-cozy76-p76-03a-literal-fix-001` repaired fixture serialization
without changing decoded expectations or product grammar. Cumulative P76-03
repair cycles remain 2 and consecutive non-converging repairs remain 0. This
deterministic documentary projection neither increments nor resets those
counts and creates no new repair cycle or full Phase review.

Under `phase96-cozy76-p76-03-acceptance-handoff-001` r1, all four P76-03
source-contract criteria are accepted: renderer/public provider source shape,
both ordinary in-process routes, optional Realm integration and library
guards. The [current checklist](../../../phase/phase-76-checklist.md) records
P76-01..03 CLOSED with 12/24 obligations accepted and 12 unchecked;
P76-04..06 remain OPEN and Phase 76 remains IN_PROGRESS. P76-01 retains local
commit `0224d56cd5a1a94ab110b7e776e6922264eb7950` and P76-02 retains
`d9e106ca7d154402487274e189063f4276d0d94a`. The local P76-03 Step acceptance
commit is PREPARED/PENDING, not executed.

This is source/provider acceptance only. Actual twice-generated ordinary CLI
LUI-GEN-01 remains P76-04; Scala 3 compilation, ComponentFactory discovery/
admission, bootstrap and equivalent Display projection of those same generated
files through LUI-ACCEPT-01 remain P76-05/P96-03C. Full-suite validation,
publication, push, full Phase acceptance/release and either Phase closure
remain pending. Phase 74 authority and Phase 51 PLANNED are preserved.
