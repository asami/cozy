# Phase 62 Addendum: Workflow SPI and Suspended Action

Status: planned / normative refinement

## Requirements

- Do not model Orchestration/Continuation as mutually exclusive Workflow modes.
- Preserve `State -> Action -> Result -> Transition` as the core Workflow/StateMachine semantics.
- Define generated ABI support for `ActionExecution = Completed | Suspended | Failed` or an equivalent typed contract.
- `Suspended` carries a durable Continuation sufficient for typed resume.
- Identify/project external required typed Actions as Workflow SPI operations.
- Workflow SPI operation specification includes identity, typed input/result and generic Context/Completion/Evidence requirements.
- Provider placement/binding must not require duplication of Workflow StateMachine semantics.
- Permit direct, test/mock, and external provider implementations of the same required operation.
- Keep the initial Phase 62 target focused on reliable Skill-driven Workflow execution; Workflow Connection/UI Workflow/Flutter generation remain future work.

## Acceptance shape

```text
State
 -> BuildProject
 -> Completed(BuildResult)
 -> transition
 -> ReviewChange
 -> Suspended(Continuation[ReviewContext, ReviewResult])
 -> resume(ReviewResult)
 -> transition
 -> CommitChanges
 -> Completed(CommitResult)
 -> terminal
```

The generated Workflow SPI for this example exposes `ReviewChange : ReviewContext -> ReviewResult` as a required external contract without exposing internal build/commit actions as required external interfaces.
