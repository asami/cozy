# Phase66 Checklist

Phase: [Workflow Retry and Timeout ABI](phase-66.md)
Current status: COMPLETE at Cozy producer delivery; prior S66.1/S66.2/S66.3 Steps preserved
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
Historical S66.3 fixture/receiver Step acceptance, Cozy producer closure and
CNCF-owned integration follow-up are recorded in the handoff and Phase checklist.

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

- Current status: COMMITTED; accepted.

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

Phase66 is COMPLETE at the Cozy CML and generated ABI producer-delivery
boundary, following the user's 2026-10-02 instruction. Cozy supplies the
Retry / Timeout declarations, strict source validation, deterministic lossless
v2 JSON/Scala/bootstrap metadata, metadata-free v1 compatibility and the
documented consumer handoff. CNCF owns connecting these products to its
ComponentFactory/runtime, execution enforcement and remaining CNCF validation.

All prior Step commits are preserved: Cozy S66.1
`358ae6d1a233557b3e45087558ecff89e420f4af`, S66.2
`e3262754023e0afc42bb5fcd3aeecf1cca0ab78e`, and the historical S66.3
fixture/receiver evidence in Cozy `a8a78a23492a7aed40a6cb11239fcdaa23afa240`
and CNCF `f7cf5bc04c11b9f74e09d61b8199452c5edb3275`. S66.3 proves the
pure metadata receiver and generated fixtures; production runtime integration
remains CNCF-owned work.

Exactly one independent full review, `P66-FULL-REVIEW-E1-001`, covered the
25-file accumulator. Its producer-specification authoring blocker was closed
by `P66-PHASE-E1-C1-M0-WAIVER-001`: six action/IO relocations in four existing
scenarios, preserving requests, expectations and fixtures. Normal final Cozy
`sbt --batch test`, `P66-FINAL-COZY-VAL-001`, passed after this repair:
2,367 succeeded / 0 failed / 9 canceled; 178 suites completed, none aborted;
SBT and wrapper exit 0, lock released. Subsequent closure edits affect
documentation only and preserve that tested program and specification.

The actual CNCF full run, `P66-FINAL-CNCF-VAL-001`, had 4,240 succeeded /
1 failed. The audit-viewer Job-list failure is recorded in the
[CNCF handoff](phase-66-cncf-handoff.md#cncf-owned-connection-and-validation-work)
for CNCF follow-up. CNCF full-suite success and runtime connection are outside
the user-selected Cozy closure boundary.

The [Hygiene journal](../journal/2026/10/2026-10-02-phase-66-hygiene-follow-up.md)
preserves two accepted nonblocking records exactly. There are no accepted
Development Candidates or unpersisted review records. This producer closure
is finalized by the distinct local Cozy Phase release commit.

- [x] All three Steps accepted with their complete accumulators.
- [x] Final Cozy full validation passes on the repaired producer specifications.
- [x] CNCF integration, runtime and remaining validation handed to CNCF under the user-selected boundary.
- [x] Independent full Phase review completed once for the accepted plan epoch.
- [x] Distinct final local release commit closes the accepted tree.
- [x] Phase accepted and final progress recorded by the parent.
