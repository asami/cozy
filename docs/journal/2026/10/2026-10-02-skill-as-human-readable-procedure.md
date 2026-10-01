# Skill Specification and Skill Logic

Date: 2026-10-02
Status: corrected design direction

## Correction

The earlier formulation that SKILL itself is the human-readable top-level procedure is refined.

In the SimpleModeling/Cozy process framework:

- **Skill Specification** describes the Activity/purpose and top-level work in a form understandable to humans.
- **Skill Logic** is a thin executable semantic worker/adapter for AI-native or ambiguous work.
- **Workflow/StateMachine** owns executable sequencing, branching, iteration and deterministic control flow.

Human readability is therefore primarily a property of the Skill Specification, not a reason to duplicate Workflow control logic in the Skill implementation.

## Skill Logic responsibilities

Skill Logic should:

1. receive a bounded request/WorkOrder;
2. obtain only the context needed for the semantic task;
3. perform AI-native or ambiguous/non-routine semantic work;
4. return typed Result/Evidence.

It should not implement the overall work procedure, review/repair loops, closure progression, concurrency, exclusion, retry/recovery, or durable state.

## Formalization path

```text
Skill Specification
  human-readable work description
        |
        v
Workflow / StateMachine
  executable procedure
        |
        +-- deterministic Operation
        +-- semantic WorkOrder -> Skill Logic
        +-- Human Approval
```

As ambiguous work becomes deterministic, move it from Skill Logic into Operation/Workflow/StateMachine. Skill Logic should become thinner.

This also refines Practice -> Activity -> SKILL mapping: Activity and Skill Specification explain the work; executable control is modeled separately as Workflow where required; Skill Logic supplies semantic AI participation rather than acting as a second Workflow engine.
