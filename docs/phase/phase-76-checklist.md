# Phase 76 Checklist: Logical UI Generated ABI Producer

status=IN_PROGRESS
phase=[Phase 76](phase-76.md)
development_branch=codex/phase-76-logical-ui-producer
planning_source=c6b108b8f301d4b1928e3f7f1b43d3e17f8e3739

## Ledger boundary

This ledger preserves the GitHub Phase 76 plan and all 24 obligations/owners.
P76-01's four documentary obligations are accepted after retained independent
Step review PASS. P76-02's four pure-producer obligations are accepted after
focused validation and fresh independent Step review PASS. P76-03's four
source-generation obligations are accepted after 56 + 266 = 322/322 focused
tests across nine suites, three ScalaCheck properties with at least 100
successful cases and independent Step review
`phase96-cozy76-p76-03a-step-review-001` r1 PASS by
`/root/phase96_cozy76_p76_03a_step_review_001`, attributed to Cozy P76-03 in
original CNCF primary ledger `phase96-review-ledger-001` r14. P76-04's four
producer-regression/generated-handoff obligations are also accepted after the
original independent Step review and accepted parent coordinate M0 amendment:
20/24 obligations accepted, 4 unchecked after P76-05 independent acceptance.
P76-01..05 are CLOSED and P76-06 remains OPEN;
Phase 76 remains IN_PROGRESS. The local P76-01 Step commit is
`0224d56cd5a1a94ab110b7e776e6922264eb7950`.
The local P76-02 Step acceptance commit is
`d9e106ca7d154402487274e189063f4276d0d94a`.
The local P76-03 Step acceptance commit is
`8a4c1794b7ad4557af5b4260d1233acd07520c6e`.
P76-03 acceptance covers the renderer, both ordinary in-process routes,
optional Realm integration and library guards under the source/provider
contract only. P76-04 now supplies actual twice-generated CLI LUI-GEN-01
delivery and 254/254 focused tests across six suites, with SBT/wrapper exit 0
and shared lock released. Independent review
`phase96-cozy76-p76-04a-step-review-001` r1 by
`/root/phase96_cozy76_p76_04a_step_review_001` retains FINDINGS; its sole
`CPB-COZY76-P7604-PRODUCER-COORDINATE-001` is resolved by accepted parent M0
amendment `phase96-cozy76-p76-04a-coordinate-m0-amendment-001` r1, recorded
in original CNCF ledger `phase96-review-ledger-001` r17. The local P76-04
Step commit is `ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`. CNCF P96-03's
same-generated Scala 3 compilation/factory/bootstrap/Display LUI-ACCEPT-01
proof is accepted at local commit `bef59e729834addd3ac00c1930034bb23a8b428f`.
The [reciprocal receipt](../journal/2026/10/2026-10-04-phase-76-consumer-acceptance.md)
maps the four unchanged P76-05 criteria. Own independent Step review
`phase96-cozy76-p76-05a-step-review-001` r1 by
`/root/phase96_cozy76_p76_05a_step_review_001` is PASS with all four MET and
zero new CPB/HYG/DEV, recorded in original CNCF ledger
`phase96-review-ledger-001` r21. All four boxes are checked; the local P76-05
Step commit is PREPARED/PENDING, not executed. Full Phase
validation/review, acceptance and release remain pending.
Phase 74 remains the semantic
authority; CNCF Phase 96 owns the Scala 3 consumer ABI and runtime admission.
The [producer ABI proposal](../notes/logical-ui-cncf-producer-abi-proposal.md)
is non-normative. The [reconciliation journal](../journal/2026/10/2026-10-03-phase-76-cncf96-plan-reconciliation.md)
records this planning change.

Attach design/spec, Executable Specification, validation and review evidence
appropriate to each checked item. A stage closes only from its checklist,
not from availability of a draft or another Phase's completion. Any admitted
deferral must retain a candidate identity and relocation target.

## Cross-repository handoffs and validation

| Handoff | Evidence owner | Required result |
| --- | --- | --- |
| LUI-ABI-01 | CNCF P96-01..03 -> P76-01 | Fixed ABI/provider contract, supported semantic subset, expected-generated Scala 3 fixture and focused admission/projection proof |
| LUI-GEN-01 | P76-02..04 -> CNCF P96-03 | Actual generated source/provider and deterministic producer evidence pinned to that ABI and fixture |
| LUI-ACCEPT-01 | CNCF P96-03 -> P76-05 | The same generated fixture compiles, is admitted through ComponentFactory and produces equivalent List/Detail projection |

Producer implementation consumes LUI-ABI-01, not whole-Phase 96 closure.
Cozy closure requires LUI-ACCEPT-01 and its own ledger, not CNCF's later
REST/mutation work or Editing Studio app Phase 5 E2E. The two repositories
may reference the same unchanged consumer evidence.

Intermediate Steps and child/dependency handoffs use focused specifications
and affected compilation, without full-test or public-publication prerequisites.
Repository-required release validation and the admitted Phase review/closure
remain separate; no additional full review is introduced by a handoff.

## P76-01: Consumer input and semantic mapping

Stage Status:

- Current status: CLOSED
- Current step: P76-01A documentary contract accepted at local commit 0224d56cd5a1a94ab110b7e776e6922264eb7950
- Owner: Cozy producer and CNCF ABI contract owners
- Update rule: Advance only from admitted consumer input and a reconciled mapping; the checklist below is the closure basis.

P76-01A documentary evidence: [producer spec](../spec/logical-ui-generated-producer.md),
[design](../design/logical-ui-generated-producer.md) and
[contract journal](../journal/2026/10/2026-10-04-phase-76-producer-contract.md).
The fixed source handoff is received with its dirty A/B1/B2 context disclosed;
independent Step review `phase76-p76-01-review-001` r1 by
`/root/phase76_p76_01_review` is PASS with zero blockers. The parent accepted
the four P76-01 documentary obligations. Implementation verification, actual
generation, LUI-GEN-01, LUI-ACCEPT-01, full Phase acceptance and release remain
pending; documentary acceptance does not establish those results.

- [x] LUI-ABI-01 records exact CNCF source/artifact coordinates and ABI version, provider/discovery contract, expected-generated fixture identity, structured diagnostics and focused admission/projection evidence.
- [x] Phase 74 List/Detail/Section/Field/Value/Action and presentation-role semantics map one-to-one or explicitly by derivation to the admitted CNCF subset; unsupported concepts and the reference fixture are explicit.
- [x] The contract distinguishes semantic provenance, generated ABI version, definition identity/version and runtime Display protocol/source revisions; no Cozy Scala 2.12 binary dependency or new semantic authority is introduced.
- [x] Accepted producer design/spec and Given/When/Then specification scope fix the source-generation boundary and ownership without requiring whole-Phase CNCF completion.

## P76-02: Normalized producer and ABI projection

Stage Status:

- Current status: CLOSED
- Current step: P76-02A pure producer accepted after 190 focused tests and fresh independent Step review PASS; local Step acceptance commit d9e106ca7d154402487274e189063f4276d0d94a
- Owner: Cozy Logical UI producer owners
- Update rule: Advance only from explicit pure projection and validation evidence; the checklist below is the closure basis.

Accepted executable evidence: [LogicalUiProducerSpec](../../src/test/scala/cozy/modeler/LogicalUiProducerSpec.scala)
and [LogicalUiCmlSpec](../../src/test/scala/cozy/modeler/LogicalUiCmlSpec.scala).
Representative validation passed 176/176 tests across two suites; accumulator
validation passed 14/14 across two suites, for 190/190 across four suites.
Nine ScalaCheck properties require at least 100 successful cases. Fresh Step
review `phase96-cozy76-p76-02a-step-review-001` r1 by
`/root/phase96_cozy76_p76_02a_step_review_001` is PASS with zero CPB/HYG/DEV
findings, attributed explicitly to Cozy P76-02 in original CNCF ledger
`phase96-review-ledger-001` r12. Actual rendering, generation, generated
consumer acceptance and full Phase acceptance remain pending.

- [x] The existing CML/modeler path produces a bounded normalized model and an explicit CNCF ABI projection; a second standalone generator is not introduced.
- [x] Immutable definition/configuration carries only the identity, order, roles, typed value/converter and action binding references admitted by LUI-ABI-01; entity constraints and mutation targets remain application/runtime-owned under the received fixed v1 source contract, and any static metadata extension requires explicit consumer admission without a second validation language. Runtime resource values/revisions, authorization and capability availability remain CNCF-owned.
- [x] Duplicate or malformed identities, unsupported vocabulary and invalid references fail with structured diagnostics before source emission, without partial generated output.
- [x] Pure Given/When/Then and property-based specifications prove Phase 74 reference mapping, stable identities, deterministic ordering and rejected input without changing the semantic authority.

## P76-03: Deterministic Scala 3 source and provider generation

Stage Status:

- Current status: CLOSED
- Current step: P76-03A renderer, both ordinary in-process routes, optional Realm integration and library guards accepted after 322/322 focused tests and independent Step review PASS; local Step acceptance commit 8a4c1794b7ad4557af5b4260d1233acd07520c6e
- Owner: Cozy source-generation owners
- Update rule: Advance only from generated source and provider specifications against the admitted ABI; the checklist below is the closure basis.

Accepted source implementation: [LogicalUiScalaGenerator](../../src/main/scala/cozy/modeler/LogicalUiScalaGenerator.scala),
bounded ordinary Modeler preflight and trailing optional Realm integration.
The [generation specification](../../src/test/scala/cozy/modeler/LogicalUiAbiGenerationSpec.scala)
uses adjacent Given/When/Then and three ScalaCheck properties with at least
100 successful cases;
[definitions](../../src/test/resources/logicalui/golden/DomainLogicalUiDefinitions.scala.txt)
and [provider](../../src/test/resources/logicalui/golden/DomainLogicalUiMetadataProvider.scala.txt)
goldens are source expectations. Representative validation passed 56/56 tests
across one suite; accumulator validation passed 266/266 across eight suites:
322/322 across nine suites. Independent Step review
`phase96-cozy76-p76-03a-step-review-001` r1 by
`/root/phase96_cozy76_p76_03a_step_review_001` is PASS with zero CPB/DEV and
three nonblocking Hygiene items, attributed to Cozy P76-03 in original CNCF
primary ledger `phase96-review-ledger-001` r14. All four source-contract
criteria below are accepted, with current totals of 20/24 accepted and
4 unchecked overall after P76-05 independent acceptance.
The local P76-03 Step acceptance commit is
`8a4c1794b7ad4557af5b4260d1233acd07520c6e`.
P76-04 now records actual twice-generated CLI LUI-GEN-01 delivery and 254/254
focused tests across six suites. P76-04 is accepted by the retained independent
Step review FINDINGS plus accepted coordinate M0 amendment r1 in original CNCF
ledger r17; its local Step commit is
`ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`.
CNCF's same-generated LUI-ACCEPT-01 proof is accepted at local Step commit
`bef59e729834addd3ac00c1930034bb23a8b428f`; P76-05's four unchanged criteria
are independently accepted with its local Step commit PREPARED/PENDING,
not executed. P76-06 remains OPEN. Full
Phase validation/review, acceptance and release remain pending.

- [x] Existing generator utilities emit Scala 3 public-ABI definitions and bounded provider/bootstrap metadata in the source shape fixed by LUI-ABI-01, discoverable through CNCF ComponentFactory.
- [x] Generated source contains no Cozy runtime, Flutter/TFAF, runtime endpoint URL, widget, callback, datastore or client-navigation dependency, and no consumer implementation classes outside the public ABI.
- [x] Golden fixtures preserve the expected-generated shape, exact stable identities, ordering and provenance; repeated generation is byte-equivalent UTF-8 LF with final newline and no generated-time headers.
- [x] Generator specifications exercise valid and invalid inputs, naming/escaping and provider identity so correctness is not inferred from a hand-written fixture alone.

## P76-04: Producer regression and generated handoff

Stage Status:

- Current status: CLOSED
- Current step: P76-04A actual generated LUI-GEN-01 and producer regression accepted by original independent Step review plus parent coordinate M0 amendment; local Step commit ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3
- Owner: Cozy producer validation owners
- Update rule: Advance only from reproducible generation and affected regression evidence; the checklist below is the closure basis.

The [generated handoff journal](../journal/2026/10/2026-10-04-phase-76-generated-handoff.md)
records the ordinary input, exact two CLI commands, inspected two-run/golden
byte parity and actual first-run two-file delivery. Parent-admitted terminal
receipts have SBT/wrapper exit 0 and shared lock released; the frozen six-suite
accumulator passed 254/254 tests with no failures or aborts. The second CLI
invocation reported a Logback JoranException, disclosed in the journal.
Delivered [definitions](../../src/test/resources/logicalui/generated/ess2-01/DomainLogicalUiDefinitions.scala.txt)
and [provider](../../src/test/resources/logicalui/generated/ess2-01/DomainLogicalUiMetadataProvider.scala.txt)
are producer output consumed by accepted CNCF same-source proof. Independent
Step review `phase96-cozy76-p76-04a-step-review-001` r1 by
`/root/phase96_cozy76_p76_04a_step_review_001` retains original FINDINGS, with
sole `CPB-COZY76-P7604-PRODUCER-COORDINATE-001` resolved by accepted parent
M0 amendment `phase96-cozy76-p76-04a-coordinate-m0-amendment-001` r1 in
original CNCF ledger `phase96-review-ledger-001` r17. Under
`phase96-cozy76-p76-04-acceptance-handoff-001` r2, all four criteria below
are accepted; current totals are 20/24 accepted and 4 unchecked after P76-05
independent acceptance.
Local Step commit is `ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`.
CNCF P96-03's same-generated LUI-ACCEPT-01 proof is accepted at local commit
`bef59e729834addd3ac00c1930034bb23a8b428f`; P76-05 own independent Step
acceptance is PASS, with its local commit PREPARED/PENDING, not executed.
P76-06 and full Phase validation/review/acceptance/release remain pending.

- [x] Focused executable specifications and affected compilation pass for semantic mapping, validation and source/provider generation, with commands, revisions and results recorded.
- [x] Existing target-neutral compile-time Logical UI semantics and Phase 74 semantic fixtures remain unchanged, with affected Logical UI/runtime regression evidence; preserve Phase 51 PLANNED Flutter Target Projection without starting it or claiming Flutter source/suite execution.
- [x] LUI-GEN-01 supplies the actual generated fixture/provider, input fixture and generation command, exact producer and consumer ABI revisions/coordinates, output identities and deterministic generation evidence.
- [x] Producer-local text/golden checks are identified as producer evidence only; the generated artifact is available for the exact CNCF consumer acceptance without requiring a public release or intermediate full-test run.

## P76-05: CNCF consumer acceptance

Stage Status:

- Current status: CLOSED
- Current step: P76-05A four unchanged criteria accepted after own independent Step review PASS; local Step commit PREPARED/PENDING, not executed
- Owner: CNCF consumer acceptance and Cozy integration owners
- Update rule: Advance only from evidence for the same actual generated fixture; the checklist below is the closure basis.

The non-normative [LUI-ACCEPT-01 reciprocal receipt](../journal/2026/10/2026-10-04-phase-76-consumer-acceptance.md)
binds producer delivery commit `ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`
and consumer accepted commit `bef59e729834addd3ac00c1930034bb23a8b428f`.
The [CNCF consumer ledger](../../../cloud-native-component-framework-phase96/docs/phase/phase-96-checklist.md#p96-03-componentfactory-admission-and-display-projection)
already accepts its P96-03 criterion 7 and references the durable consumer journal.
Representative and accumulator results total 140/140 focused tests across
16 completed suites with SBT/wrapper exit 0 and both shared locks released.

| Unchanged source criterion | Receipt evidence |
| --- | --- |
| Scala 3 compilation without `cozy_2.12` | Unmodified delivered Test sources, successful Compile/Test and inspected Test/fullClasspath |
| ComponentFactory discovery/admission and negative diagnostics | GC1/GC4 normal bootstrap; GC5 ABI/duplicate/revision/target diagnostics; GC6 atomic binding rejection; ABI regression specs |
| Equivalent reference List/Detail Projection | GC2 structural model equality with separate provenance; GC3 exact Unicode/order/empty/context; GCP1 minimum 100 successful cases and zero discards |
| Exact reciprocal revision/fixture/result binding | LUI-ACCEPT-01 receipt, committed producer/consumer identities and both ledger references |

Own independent Step review `phase96-cozy76-p76-05a-step-review-001` r1 by
`/root/phase96_cozy76_p76_05a_step_review_001` is PASS: all four unchanged
criteria MET, zero new CPB/HYG/DEV, admitted in original CNCF ledger
`phase96-review-ledger-001` r21. All four boxes below are checked and form
the P76-05 closure basis. Totals are 20 checked / 4 unchecked; P76-01..05
CLOSED, P76-06 OPEN and Phase 76 IN_PROGRESS. The local P76-05 Step commit
is PREPARED/PENDING, not executed. Full Phase validation/review/release and
both Phase closures remain pending.

- [x] CNCF P96-03 compiles the LUI-GEN-01 Scala 3 fixture against the agreed ABI without depending on `cozy_2.12`; the actual consumer project/fixture and command/result are recorded.
- [x] ComponentFactory discovers and admits the generated provider and definitions, and focused evidence covers the agreed invalid/duplicate/incompatible admission diagnostics.
- [x] The generated fixture produces the same reference List/Detail Display Projection as the LUI-ABI-01 hand-written fixture, preserving semantics, values, identities and ordering.
- [x] LUI-ACCEPT-01 binds exact producer, consumer and fixture revisions/identities and focused results; both ledgers reference this receipt, not a whole-Phase closure assumption or producer-only text comparison.

## P76-06: Compatibility, handoff and Phase closure

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 76 release and integration owners
- Update rule: Advance only when this checklist and preceding stages have complete evidence or admitted dispositions; this checklist is the closure basis.

- [ ] Producer/consumer compatibility and future additive vocabulary evolution document ABI-version policy, supported subset, rejection behavior and definition/provenance ownership against the accepted CNCF contract.
- [ ] Developer documentation explains the ordinary CML -> generated source/provider -> CNCF admission path, reproducible fixtures and all three handoff receipts without runtime CML parsing or a shared binary runtime library.
- [ ] Repository-required release validation and the admitted Phase review/closure evidence are recorded separately from focused intermediate handoffs; any admitted fixes have bounded closure evidence.
- [ ] Every Phase 76 scope and acceptance criterion maps to completed ledger evidence or explicit admitted candidate/relocation records; journal, Phase status and release records agree without claiming CNCF REST/mutation or app E2E completion.

## Phase scope coverage

| Phase 76 scope | Evidence-owning stages |
| --- | --- |
| Semantic mapping and normalized versioned projection (1, 2) | P76-01, P76-02 |
| Deterministic generation and provider/bootstrap (3, 4, 5, 6) | P76-02, P76-03, P76-04 |
| Real generated consumer compile/admission/projection (7, 8) | P76-04, P76-05 |
| Phase 51 plan and existing target-neutral compile-time semantics preserved (9) | P76-04 |
| Compatibility/versioning and completion (10) | P76-06 |
