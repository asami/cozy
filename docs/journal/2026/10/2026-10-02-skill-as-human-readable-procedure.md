# Skill as Human-Readable Procedure

Date: 2026-10-02
Status: design direction

## Position

In the SimpleModeling/Cozy process framework, SKILL is not only an executable AI instruction. It is also a human-readable procedural description of how an Activity is carried out.

The preferred SKILL form is an ordinary sequential procedure that a human can read and an AI can execute.

SKILL should remain conceptually single-threaded. Concurrency control, exclusion, lease, coordination, retry/recovery, and durable state progression are not SKILL concerns. When required, they are supplied by CNCF Workflow/StateMachine/runtime mechanisms outside the SKILL.

## Three roles of SKILL

1. express AI-native semantic work;
2. execute ambiguous or non-routine work before it is sufficiently formalized;
3. preserve the top-level work procedure in a form understandable to humans.

The third role remains useful even after lower-level behavior becomes deterministic. A SKILL may describe the overall procedure while individual steps delegate to generated Operations, Workflow/StateMachine, human approval, or sub-Skills.

This supports gradual formalization:

```text
human-readable / AI-executable procedure
        |
        +-- ambiguous semantic step -> SKILL / AI
        +-- stabilized step          -> Operation
        +-- deterministic process    -> Workflow / StateMachine
        +-- approval                 -> Human
```

Formalization should move deterministic execution semantics downward rather than turning SKILL itself into a state machine or concurrency language.

This refines the existing Practice -> Activity -> SKILL mapping: Activity remains the abstract unit of work; SKILL is its concrete procedural realization when such a procedure is useful.
