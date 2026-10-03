# Phase 76 Checklist: Logical UI Generated ABI Producer

status=IN_PROGRESS
phase=[Phase 76](phase-76.md)
development_branch=codex/phase-76-logical-ui-producer
planning_source=c6b108b8f301d4b1928e3f7f1b43d3e17f8e3739

## Ledger boundary

This ledger preserves the GitHub Phase 76 plan and all 24 obligations/owners.
P76-01's four documentary obligations are accepted after retained independent
Step review PASS. P76-02..06 remain OPEN with 20 unchecked obligations;
Phase 76 remains IN_PROGRESS and the local P76-01 Step commit is pending.
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
- Current step: P76-01A documentary contract accepted; local Step commit pending
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

- Current status: OPEN
- Owner: Cozy Logical UI producer owners
- Update rule: Advance only from explicit pure projection and validation evidence; the checklist below is the closure basis.

- [ ] The existing CML/modeler path produces a bounded normalized model and an explicit CNCF ABI projection; a second standalone generator is not introduced.
- [ ] Immutable definition/configuration carries only the identity, order, roles, typed value/converter and action binding references admitted by LUI-ABI-01; entity constraints and mutation targets remain application/runtime-owned under the received fixed v1 source contract, and any static metadata extension requires explicit consumer admission without a second validation language. Runtime resource values/revisions, authorization and capability availability remain CNCF-owned.
- [ ] Duplicate or malformed identities, unsupported vocabulary and invalid references fail with structured diagnostics before source emission, without partial generated output.
- [ ] Pure Given/When/Then and property-based specifications prove Phase 74 reference mapping, stable identities, deterministic ordering and rejected input without changing the semantic authority.

## P76-03: Deterministic Scala 3 source and provider generation

Stage Status:

- Current status: OPEN
- Owner: Cozy source-generation owners
- Update rule: Advance only from generated source and provider specifications against the admitted ABI; the checklist below is the closure basis.

- [ ] Existing generator utilities emit Scala 3 public-ABI definitions and bounded provider/bootstrap metadata in the source shape fixed by LUI-ABI-01, discoverable through CNCF ComponentFactory.
- [ ] Generated source contains no Cozy runtime, Flutter/TFAF, runtime endpoint URL, widget, callback, datastore or client-navigation dependency, and no consumer implementation classes outside the public ABI.
- [ ] Golden fixtures preserve the expected-generated shape, exact stable identities, ordering and provenance; repeated generation is byte-equivalent UTF-8 LF with final newline and no generated-time headers.
- [ ] Generator specifications exercise valid and invalid inputs, naming/escaping and provider identity so correctness is not inferred from a hand-written fixture alone.

## P76-04: Producer regression and generated handoff

Stage Status:

- Current status: OPEN
- Owner: Cozy producer validation owners
- Update rule: Advance only from reproducible generation and affected regression evidence; the checklist below is the closure basis.

- [ ] Focused executable specifications and affected compilation pass for semantic mapping, validation and source/provider generation, with commands, revisions and results recorded.
- [ ] Existing target-neutral compile-time Logical UI semantics and Phase 74 semantic fixtures remain unchanged, with affected Logical UI/runtime regression evidence; preserve Phase 51 PLANNED Flutter Target Projection without starting it or claiming Flutter source/suite execution.
- [ ] LUI-GEN-01 supplies the actual generated fixture/provider, input fixture and generation command, exact producer and consumer ABI revisions/coordinates, output identities and deterministic generation evidence.
- [ ] Producer-local text/golden checks are identified as producer evidence only; the generated artifact is available for the exact CNCF consumer acceptance without requiring a public release or intermediate full-test run.

## P76-05: CNCF consumer acceptance

Stage Status:

- Current status: OPEN
- Owner: CNCF consumer acceptance and Cozy integration owners
- Update rule: Advance only from evidence for the same actual generated fixture; the checklist below is the closure basis.

- [ ] CNCF P96-03 compiles the LUI-GEN-01 Scala 3 fixture against the agreed ABI without depending on `cozy_2.12`; the actual consumer project/fixture and command/result are recorded.
- [ ] ComponentFactory discovers and admits the generated provider and definitions, and focused evidence covers the agreed invalid/duplicate/incompatible admission diagnostics.
- [ ] The generated fixture produces the same reference List/Detail Display Projection as the LUI-ABI-01 hand-written fixture, preserving semantics, values, identities and ordering.
- [ ] LUI-ACCEPT-01 binds exact producer, consumer and fixture revisions/identities and focused results; both ledgers reference this receipt, not a whole-Phase closure assumption or producer-only text comparison.

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
