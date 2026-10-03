# Failure Model CML Authoring

CML supports Failure Model declarations at Component, Service, and Operation
level through the accepted Phase 72 SOURCE and ABI implementations. The
[SOURCE contract](../spec/failure-model-source-contract.md) and
[ABI contract](../spec/failure-model-abi-contract.md) are normative; this note is
explanatory. SOURCE commit `6c15b65cd83181fdf61aa8674c9a847f79751805` and ABI
commit `1d9c80775e74f7d0ba0dd60f1cf4730ef84c3843` record producer acceptance.

Execution Model selects the default Failure Model. CML declarations refine that default rather than forcing every operation to repeat the complete model.

```
ExecutionModel default
  -> Component
  -> Service
  -> Operation
  -> ResolvedFailureModel
```

CML generation must preserve explicit IN_SCOPE and OUT_OF_SCOPE semantics and emit enough metadata for CNCF to resolve or validate the effective model deterministically.

CAR development must make the resolved model available to implementation workflows. AI must receive the resolved contract, not be asked to infer failures from an Execution Model name.

OUT_OF_SCOPE is normative at the effective owner: implementation guidance must
exclude mechanisms justified solely by an excluded failure. A narrower explicit
IN_SCOPE can override an ancestor OUT_OF_SCOPE. IN_SCOPE identifies robustness
responsibility but does not select a mechanism without explicit requirements.
Execution names imply no failures, locks, retries, rollback, hashes, durability
or distributed guarantees.

The authored [CNCF91 and sm-workflow handoff](../phase/phase-72-cncf-handoff.md)
provides the actual local fixture, effective owner table, origin/winner chains
and public CLI reproduction instructions. CNCF owns canonical types, recipient
validation and runtime resolution. sm-workflow guidance depends on that receiver
acceptance and uses only the recipient-resolved contract at a named owner.
Missing artifacts, schema or resolution are unmet prerequisites; raw deltas or
execution names cannot fill them. HANDOFF acceptance and final Phase gates remain
pending, and recipient/runtime/workflow wiring is not claimed complete.
