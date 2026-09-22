# Phase 72: CML Failure Model

## Goal

Add CML authoring and generation support for Execution Model-derived Failure Models.

## Scope

- Add Failure Model declarations to Component, Service, and Operation.
- Support inherited defaults from Execution Model.
- Support explicit IN_SCOPE / OUT_OF_SCOPE refinement.
- Generate metadata sufficient for deterministic ResolvedFailureModel construction.
- Preserve traceability from CML declarations to CAR development metadata.
- Make the resolved model available to downstream implementation workflows.
- Add validation for contradictory or invalid refinements.
- Add examples including local single-user/single-writer components.

## Integration

CNCF Phase 91 defines the model and resolution semantics. sm-workflow consumes the resolved result as an AI implementation constraint.

## Completion

Executable examples demonstrate that a CML component can select an Execution Model, refine failures at Component/Service/Operation scopes, and produce the expected effective failure contract.
