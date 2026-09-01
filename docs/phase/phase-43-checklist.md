# Phase 43 Checklist: Logical UI Model and Review HTML

This checklist is the authoritative progress ledger for Phase 43. It is not a
normative behavior contract.

Phase Status: IN PROGRESS; LUI43-01 Authority and Contract Kernel,
LUI43-02 UseCase-to-Screen and Component Projection, and LUI43-03 Pattern,
Constraint, and State Semantics are complete; LUI43-03W Workflow Subject and
Pattern Semantics and LUI43-04 Logical UI Review HTML and Currentness are
complete; LUI43-05 SalesOrder Driver Acceptance and Closure is not started.

Predecessor: accepted Phase 42.1 closure.

## LUI43-01: Authority and Contract Kernel

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 43
- Update rule: Update this block from the checklist state below.

- [x] Freeze minimum Application Core and Business/System/UI UseCase
      references.
- [x] Freeze exact public Component FQN and exported-surface binding.
- [x] Decide and version the authoring front and normalized Logical UI IR.
- [x] Separate candidate, accepted, feedback, and consumed-input identities.
- [x] Prove generated projections remain outside semantic authority.

## LUI43-02: UseCase-to-Screen and Component Projection

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 43
- Update rule: Update this block from the checklist state below.

- [x] Support one-to-many, many-to-one, reused-screen, system-only,
      alternative, and exception mappings.
- [x] Define Logical Screen purpose, subject, regions, navigation,
      interactions, feedback states, and use-case coverage.
- [x] Bind all admitted Component vocabulary by exact public identity.
- [x] Reject unexported or unjustified model surfaces.
- [x] Preserve Aggregate mutation boundaries and public Operation authority.

## LUI43-03: Pattern, Constraint, and State Semantics

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 43
- Update rule: Update this block from the checklist state below.

- [x] Freeze the minimum Purpose, Display, and Interaction Pattern catalogs.
- [x] Bind Datatype, Value, multiplicity, Aggregate invariant, Operation DbC,
      Powertype, and StateMachine semantics.
- [x] Classify local, contextual, and server-authoritative validation.
- [x] Retain shared constraint and DetailCode identity across UI/server
      feedback.
- [x] Keep domain, Workflow, and UI interaction state distinct.
- [x] Require Operation and UI UseCase admission for transition actions.

## LUI43-03W: Workflow Subject and Pattern Semantics

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 43
- Update rule: Update this block from the checklist state below.

- [x] Admit Workflow as a closed public Component role and Logical Screen
      pattern source peer of Entity, Aggregate, and View.
- [x] Require the same exact public Component binding admission and canonical
      identity behavior for a Workflow screen subject.
- [x] Retain Workflow as a non-executing, non-rendering, non-local-authority
      UI pattern source; do not replace server Workflow, authorization, or
      observability.
- [x] Keep Domain StateMachine, opaque Workflow-state evidence, and UI
      interaction state as distinct typed domains.
- Completion evidence: Workflow public subject and pattern semantics are
  complete. Focused Executable Specification receipt
  `P43-LUI43-03W-TEST-002` reports 34 succeeded and 0 failed; focused closure
  review `P43-LUI43-03W-04J-RR-005` reports no Current Boundary Blocker.

## LUI43-04: Logical UI Review HTML and Currentness

Stage Status:

- Current status: DONE
- Owner: Cozy Phase 43
- Update rule: Update this block from the checklist state below.

- [x] Generate deterministic, atomic, self-contained, read-only review HTML.
- [x] Show the complete use-case, screen, navigation, Component, pattern,
      validation, lifecycle, action, and feedback structure.
- [x] Emit required coverage, reachability, authority, mutation, action,
      variant, and failure diagnostics.
- [x] Bind exact inputs, catalogs, renderer/profile, and output identity in a
      versioned currentness receipt.
- [x] Prove renderer-only change does not stale Logical UI semantic authority.
- [x] Close `CB-LUI43-04-RR-006` only when a `parent/link/../escaped.html`
      target is rejected before temporary output creation and its escaped
      sentinel bytes remain unchanged.
- Completion evidence: `P43-LUI43-04J-TEST-007` reports 6 succeeded and 0
  failed for the direct-child escape closure; `P43-LUI43-03W-TEST-002` reports
  34 succeeded and 0 failed for the combined Workflow/review behavior; focused
  closure review `P43-LUI43-03W-04J-RR-005` reports no Current Boundary Blocker.

## LUI43-05: SalesOrder Driver Acceptance and Closure

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase 43
- Update rule: Update this block from the checklist state below.

- [ ] Build one repository-controlled SalesOrder fixture with required
      Component and three-layer use-case vocabulary.
- [ ] Cover list/detail navigation, validation, transition, success, conflict,
      and service-unavailable behavior.
- [ ] Prove candidate acceptance, deterministic repeat output, and stale-input
      diagnosis.
- [ ] Run focused and full serialized Cozy validation.
- [ ] Complete one independent Phase review with no Current Boundary Blocker.
- [ ] Synchronize Strategy, Phase, checklist, notes, and journal records at
      closure.

Phase 43 closes only when every checkbox is done and current validation/review
evidence is bound to the accepted tree. It does not claim Flutter generation,
publication, deployment, upload, push, or external-driver acceptance.
