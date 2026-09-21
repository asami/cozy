# Candidate-Admission Model and Cozy Workflow

- Date: 2026-09-21
- Status: Design direction

The sm-workflow evidence-driven Step-close design revealed a general pattern for AI and StateMachine cooperation: a semantic actor autonomously constructs a candidate state, the StateMachine evaluates admission, missing requirements become semantic/deterministic work, and the runtime commits only after admission.

This pattern is named **Candidate-Admission Model (CAM)**.

For Cozy this does not create a new language alongside StateMachine/Workflow. It clarifies how semantic Actions, typed Results/Evidence, deterministic admission and generated ABI should relate.

JudgmentAction is the Action-level reference. sm-workflow Step closure is the larger Workflow-level proving case. Once validated there, Cozy can generalize only the stable language/ABI concepts required to express CAM without importing software-development Phase/Checklist semantics.

The intended cross-project direction is: sm-workflow proves the application pattern; CNCF proves runtime admission/Continuation/commitment; Cozy preserves the declarative semantics and producer ABI.
