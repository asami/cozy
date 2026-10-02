# Phase 68 checklist

Status: active

Current status: PHASE-68 active. S68.1 SOURCE accepted after source353/353, P68-S681-REVIEW-001 revision1 PASS and native commit 0a8062750a390343f7339fa4a1af8c02a6e4a8fd; S68.2 ABI accepted after producer92/92, actual Scala3.3.8 normal/value/mixed 4/4/10 compilation, P68-S682-REVIEW-001 revision1 PASS and native commit 3e2c7605f343e7eef75bf8166f98f1c6e0d89ac4. S68.3 [CNCF83 handoff](phase-68-cncf-handoff.md) authored; parent static verification, medium Step review and native Step commit pending. PHASE-68 remains active; sole full Phase review, final full suite and release pending.
Owner: Cozy Phase 68 parent.
Update rule: check acceptance items only against actual validation/review/native commit evidence; update this ledger with the Phase document. Authoring alone does not accept a Step or close the Phase.
Completion basis: all three Cozy producer Step commits, exactly one comprehensive Phase review after them, final Cozy full suite and local release commit.
Updated: 2026-10-03.

- [x] Freeze three-Step producer scope and source failure/identity/key semantics in the parent implementation manifest.
- [x] Author S68.1 SOURCE immutable IR, closed normalization, shared metadata ownership traversal, complete CML fixture, real-CML executable specification and paired source specification/design.
- [x] S68.1: pass the exact six-suite source accumulator through serialized SBT and parent `git diff --check`; P68-S681-VAL-005 passed 353/353 tests across six completed suites, zero aborted suites, and parent diff checking succeeded.
- [x] S68.1: pass the fresh protected focused source acceptance review P68-S681-REVIEW-001 revision1 PASS and record native local Step commit 0a8062750a390343f7339fa4a1af8c02a6e4a8fd.
- [x] S68.2: accept deterministic v4 JSON/Scala/bootstrap, lossless projection, producer executable specification and independent original v3 baseline after producer92/92, actual Scala3.3.8 4/4/10 compilation and P68-S682-REVIEW-001 revision1 PASS.
- [x] S68.2: P68-S682-VAL-002 passed the focused producer accumulator92/92 across seven completed suites, zero aborted suites and zero failed, canceled, ignored or pending tests, including relevant Scala2.12.18 Test/compile.
- [x] S68.2: P68-S682-VAL-003 compiled actual generated Scala3.3.8 normal/value/mixed 4/4/10 sources, with native .class/.tasty outputs for all eighteen sources and three separate analyses.
- [x] S68.2: pass independent protected producer review P68-S682-REVIEW-001 revision1 PASS and record native local Step commit 3e2c7605f343e7eef75bf8166f98f1c6e0d89ac4.
- [x] S68.3: author the [same-Phase CNCF83 handoff](phase-68-cncf-handoff.md) with producer evidence, portable reproduction and receiver/runtime obligations.
- [ ] S68.3: pass parent static verification and medium focused Step review, then record the native local Step commit.
- [ ] Pass exactly one comprehensive Phase review after the three Step commits.
- [ ] Pass the final Cozy full suite at the Phase release boundary.
- [ ] Synchronize current Phase records from actual evidence and record the local release commit.

The [Phase document](phase-68.md), normative
[source contract](../spec/workflow-failure-execution-safety-source-contract.md) and
[lowering design](../design/workflow-failure-execution-safety-source-lowering.md)
are current same-Phase documents. S68.2's accepted [ABI contract](../spec/workflow-failure-execution-safety-abi-contract.md),
[ABI lowering design](../design/workflow-failure-execution-safety-abi-lowering.md),
[producer executable specification](../../src/test/scala/cozy/modeler/WorkflowExecutionSafetyAbiGenerationSpec.scala)
and [original v3 baseline provenance](../../src/test/resources/modeler/workflow-execution-safety-v3-baseline/provenance.md)
passed focused producer validation92/92 in P68-S682-VAL-002 and actual generated
Scala3.3.8 normal/value/mixed 4/4/10 source compilation in P68-S682-VAL-003.
Both admitted native records report SBT/wrapper exit0 and `lock=released`;
independent protected producer review P68-S682-REVIEW-001 revision1 PASS and
native Step commit `3e2c7605f343e7eef75bf8166f98f1c6e0d89ac4` record S68.2 acceptance.
The [CNCF83 handoff](phase-68-cncf-handoff.md) is authored; S68.3 parent static
verification, medium review and native Step commit remain pending. No Phase
closure is claimed.

CNCF83 runtime implementation, admission enforcement, storage, replay and retry
execution are recipient responsibilities and are not unchecked Cozy completion
items. Existing Phase67 HYG-P67-S671-001 and HYG-P67-S671-002 Hygiene remains in its original canonical journal and
is neither duplicated nor marked resolved here.
