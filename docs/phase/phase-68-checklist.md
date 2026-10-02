# Phase 68 checklist

Status: active

Current status: PHASE-68 active; S68.1 source implementation/specification is authored and focused source validation passed. Acceptance awaits independent review and local Step commit. S68.2 and S68.3 remain planned.
Owner: Cozy Phase 68 parent.
Update rule: check acceptance items only against actual validation/review/native commit evidence; update this ledger with the Phase document. Authoring alone does not accept a Step or close the Phase.
Completion basis: all three Cozy producer Step commits, exactly one comprehensive Phase review after them, final Cozy full suite and local release commit.
Updated: 2026-10-02.

- [x] Freeze three-Step producer scope and source failure/identity/key semantics in the parent implementation manifest.
- [x] Author S68.1 SOURCE immutable IR, closed normalization, shared metadata ownership traversal, complete CML fixture, real-CML executable specification and paired source specification/design.
- [x] S68.1: pass the exact six-suite source accumulator through serialized SBT and parent `git diff --check`; P68-S681-VAL-005 passed 353/353 tests across six completed suites, zero aborted suites, and parent diff checking succeeded.
- [ ] S68.1: pass the fresh protected focused source acceptance review and record the native local Step commit.
- [ ] S68.2: author deterministic v4 JSON/Scala/bootstrap, lossless projection and exact legacy output compatibility; this ABI work is planned.
- [ ] S68.2: validate both public producers and actual generated Scala 3.3.8 typed-consumer compilation, pass focused review and record the native local Step commit.
- [ ] S68.3: author the planned same-Phase CNCF83 handoff document with producer evidence and receiver obligations; pass focused review and record the native local Step commit.
- [ ] Pass exactly one comprehensive Phase review after the three Step commits.
- [ ] Pass the final Cozy full suite at the Phase release boundary.
- [ ] Synchronize current Phase records from actual evidence and record the local release commit.

The [Phase document](phase-68.md), normative
[source contract](../spec/workflow-failure-execution-safety-source-contract.md) and
[lowering design](../design/workflow-failure-execution-safety-source-lowering.md)
are current same-Phase documents. v4 ABI documents and `phase-68-cncf-handoff.md`
are explicitly planned; S68.1 supplies no generated v4 acceptance claim.

CNCF83 runtime implementation, admission enforcement, storage, replay and retry
execution are recipient responsibilities and are not unchecked Cozy completion
items. Existing Phase67 Hygiene remains in its original canonical journal and
is neither duplicated nor marked resolved here.
