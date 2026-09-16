# Phase 62 Addendum: Initial Scope and UI Workflow Roadmap

Status: planned / normative scope clarification

## Phase 62 target

Phase 62の実装ターゲットは、SkillからWorkflowを確実に利用するためのgeneric foundationまでとする。

Required:

- first-class Workflow identity and StateMachine reuse
- typed Operation / Participant
- Action-level InvocationBinding
- ORCHESTRATION / CONTINUATION mixed binding
- WorkflowInvocationContract
- ContextBundle / ContextSnapshot / ContextReference contract
- Continuation / typed Result / Completion / Evidence contract
- generated ABI sufficient for CNCF durable yield/resume and Skill Workflow Support
- deterministic generation and compatibility evidence

## Explicit non-goals for Phase 62

- UI-WORKFLOW grammar
- Workflow Connection / targetWorkflow syntax
- Flutter/Dart generation
- Flutter runtime implementation
- screen/form generation from Continuation
- client offline workflow synchronization
- Participant Workflow code generation

## Forward compatibility requirement

Phase 62 must not encode assumptions that make future Participant Workflow binding impossible. In particular, Operation input/result and Participant Invocation semantics must remain independent from concrete UI/Skill implementations.

Future phases may add:

```text
Business Workflow
  -> Continuation binding
  -> generated UI Workflow
  -> Flutter participant runtime
  -> Result
  -> Business Workflow resume
```

without redefining the core Workflow/Operation semantics established in Phase 62.

## Closure priority

Phase 62 closes when the generated ABI can support the immediate `sm-workflow` / CNCF scenario: a Skill driver receives a durable semantic continuation, performs the requested semantic work, returns a typed result, and the Workflow safely resumes while direct deterministic actions remain orchestrated internally.
