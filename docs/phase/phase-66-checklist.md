# Phase66 Checklist

Phase: [Workflow Retry and Timeout ABI](phase-66.md)
Current status: in-progress; all Steps unaccepted
Updated: 2026-10-02

## S66.1

Current status: implementation in-progress; acceptance pending.

Owner: Cozy source/model producer; parent owns validation and acceptance.

Update rule: record implementation evidence without checking outcomes until
the parent completes the frozen focused validation and independent Step review.

Completion rule: the complete source accumulator satisfies the canonical
grammar, typed normalization and legacy constructor compatibility, and the
parent accepts the validated tree through protected focused review.

- [ ] Canonical Workflow INVOCATION-POLICY grammar and lowering design recorded.
- [ ] Action/Participant targets and typed retry/timeout normalize losslessly.
- [ ] Syntax, range, absence, identity and placement behavior specified through real CML.
- [ ] CSM semantics and legacy Workflow construction compatibility validated.
- [ ] Focused new-spec and bounded regression accumulator validation passes.
- [ ] Fresh independent protected focused Step review closes; Step accepted.

## S66.2

Current status: pending; unaccepted.

Owner: Cozy generated Workflow ABI/bootstrap producer.

Update rule: freeze the generated ABI implementation boundary in the parent;
update evidence and outcomes only after its validation and independent Step review.

Completion rule: policy-bearing source yields lossless optional v2 Workflow
ABI/bootstrap, while metadata-free inputs retain byte-identical v1 output,
and the parent accepts that validated boundary.

- [ ] Optional v2 Workflow ABI/bootstrap contract frozen for policy declarations.
- [ ] Retry/timeout, declared targets and canonical Action identities projected losslessly.
- [ ] Metadata-free inputs verified to retain byte-identical v1 output.
- [ ] Producer behavior and compatibility specifications validated.
- [ ] Independent Step review closes; Step accepted.

## S66.3

Current status: pending; unaccepted.

Owner: goldenport-cncf metadata admission/consumer-fixture boundary; Cozy
supplies the real generated fixture. CNCF Phase81 owns runtime enforcement.

Update rule: freeze the metadata admission and fixture handoff in the parent;
record evidence after consumer validation and independent Step review.

Completion rule: CNCF admits generated invocation metadata from a real Cozy
CML fixture without parsing CML, records the enforcement handoff to Phase81,
and the parent accepts the validated boundary.

- [ ] Versioned generated metadata admission contract frozen and implemented.
- [ ] Real CML producer fixture handed to a CML-agnostic CNCF consumer.
- [ ] Consumer metadata admission and existing input compatibility validated.
- [ ] Phase81 enforcement responsibility recorded without Phase66 runtime enforcement.
- [ ] Independent Step review closes; Step accepted.

## Phase closure

All closure outcomes remain pending. No test, review, commit, Step or Phase
acceptance is asserted by this checklist.

- [ ] All three Steps accepted with their complete accumulators.
- [ ] Final full validation completed at PHASE_RELEASE_COMMIT.
- [ ] Independent full Phase review completed once for the accepted plan epoch.
- [ ] Release commit completed for the accepted tree.
- [ ] Phase accepted and final progress recorded by the parent.
