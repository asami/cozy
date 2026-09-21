# Failure Model CML Authoring

CML should allow Failure Model declarations at Component, Service, and Operation level.

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

OUT_OF_SCOPE is normative: generated implementation guidance must not encourage defensive mechanisms for excluded failures.
