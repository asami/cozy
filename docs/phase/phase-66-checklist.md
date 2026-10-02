# Phase66 Checklist

Phase: [Workflow Retry and Timeout ABI](phase-66.md)
Current status: in-progress; S66.1/S66.2 COMMITTED, S66.3 accepted
Updated: 2026-10-02

## S66.1

- Current status: COMMITTED; accepted.

Owner: Cozy source/model producer; parent owns validation and acceptance.

Update rule: preserve accepted source evidence; parent records any successor outcomes.

Acceptance evidence: commit `358ae6d1a233557b3e45087558ecff89e420f4af`,
parent `97bf6c1e641bb245cc425341523f2c31cbcda636`;
review `P66-S661-REVIEW-001` PASS; validation `P66-S661A-VAL-002`,
166 passed / 0 failed. These receipts cover S66.1 only.

Completion rule: the complete source accumulator satisfies the canonical
grammar, typed normalization and legacy constructor compatibility, and the
parent accepts the validated tree through protected focused review.

- [x] Canonical Workflow INVOCATION-POLICY grammar and lowering design recorded.
- [x] Action/Participant targets and typed retry/timeout normalize losslessly.
- [x] Syntax, range, absence, identity and placement behavior specified through real CML.
- [x] CSM semantics and legacy Workflow construction compatibility validated.
- [x] Focused new-spec and bounded regression accumulator validation passes.
- [x] Fresh independent protected focused Step review closes; Step accepted.

## S66.2

- Current status: COMMITTED; accepted.

Owner: Cozy generated Workflow ABI/bootstrap producer.

Contract: [Workflow Retry and Timeout Generated ABI](../spec/workflow-retry-timeout-abi-contract.md)
is accepted.

S66.2 acceptance: commit `e3262754023e0afc42bb5fcd3aeecf1cca0ab78e`, parent
`358ae6d1a233557b3e45087558ecff89e420f4af`; review `P66-S662-REVIEW-001`
PASS; `P66-S662A-VAL-001` (152 passed / 0 failed); `P66-S662A-VAL-002`
(three actual generated Scala 3.3.8 files compiled into 93 classes,
SBT exit 0, lock released). These receipts cover the producer Step only;
S66.3 consumer Step acceptance is recorded in the handoff; Phase acceptance remains pending.

Update rule: freeze the generated ABI implementation boundary in the parent;
update evidence and outcomes only after its validation and independent Step review.

Completion rule: policy-bearing source yields lossless optional v2 Workflow
ABI/bootstrap, while metadata-free inputs retain byte-identical v1 output,
and the parent accepts that validated boundary.

- [x] Optional v2 Workflow ABI/bootstrap contract frozen for policy declarations.
- [x] Retry/timeout, declared targets and canonical Action identities projected losslessly.
- [x] Metadata-free inputs verified to retain byte-identical v1 output.
- [x] Producer behavior and compatibility specifications validated.
- [x] Independent Step review closes; Step accepted.

## S66.3

- Current status: accepted; this change records local Step acceptance.

Owner: goldenport-cncf metadata admission/consumer-fixture boundary; Cozy
supplies the real generated fixture. CNCF Phase81 owns runtime enforcement.

Update rule: freeze the metadata admission and fixture handoff in the parent;
record evidence after consumer validation and independent Step review.
The [fixture/receiver handoff](phase-66-cncf-handoff.md) records implementation
surfaces and the accepted consumer validation/review evidence.

Completion rule: CNCF admits generated invocation metadata from a real Cozy
CML fixture without parsing CML, records the enforcement handoff to Phase81,
and the parent accepts the validated boundary.

- [x] Versioned generated metadata admission contract frozen and implemented.
- [x] Real CML producer fixture handed to a CML-agnostic CNCF consumer.
- [x] Consumer metadata admission and existing input compatibility validated.
- [x] Phase81 enforcement responsibility recorded without Phase66 runtime enforcement.
- [x] Independent Step review closes; Step accepted.

## Phase closure

All Phase closure outcomes remain pending. The recorded S66.1 receipts assert
that Step's acceptance only; the S66.2 receipts assert producer acceptance.
S66.3 validation, independent review and its exact M0 authoring repair are accepted.
Final full validation, mandatory full Phase review and release remain pending.

- [x] All three Steps accepted with their complete accumulators.
- [ ] Final full validation completed at PHASE_RELEASE_COMMIT.
- [ ] Independent full Phase review completed once for the accepted plan epoch.
- [ ] Release commit completed for the accepted tree.
- [ ] Phase accepted and final progress recorded by the parent.
