# Business Process, Problem Domain, and Workflow Model Continuity

Date: 2026-09-19
Status: design direction

## Context

SimpleModeling Business Modeling uses Business Process as the primary Business-layer organizing construct rather than Bounded Context.

A Business Process may define a Process Capability, contain Participants with Participant Capabilities, group Use Cases, group Workflows, and operate on multiple Problem Domains.

Bounded Context appears on the Domain Modeling axis as a semantic/model boundary for a Problem Domain.

Cozy/CML should preserve these distinctions while providing model continuity toward generated Workflow and CNCF runtime artifacts.

## Model Axes

```text
Business axis
  Business Process
    +-- Process Capability
    +-- Participant
    |     +-- Participant Capability
    +-- Use Case
    +-- Workflow

Domain axis
  Problem Domain
    -> Bounded Context
    -> Domain Model
```

A Business Process may operate on multiple Problem Domains. Business Process and Bounded Context therefore have a many-to-many relationship in the general case.

Neither boundary should be inferred mechanically from the other.

## Business Process and Workflow

Business Process and Workflow are distinct model elements.

Business Process groups related Workflows but is not itself required to be executable.

Workflow remains the executable coordination model and continues to reuse StateMachine / Composite StateMachine semantics.

```text
Business Process
    |
    +-- Use Case
    |
    +-- Workflow
          |
          v
Composite StateMachine / StateMachine
          |
          v
generated typed model
          |
          v
CNCF runtime
```

This preserves the current Composite StateMachine-first Workflow direction while allowing Business Modeling to sit above it.

## Cozy/CML Responsibilities

Future Business Modeling support should be able to represent or preserve stable identities and relations for:

- Business Process;
- Process Capability;
- Business Participant;
- Participant Capability;
- related Use Cases;
- related Workflows;
- operated Problem Domains;
- related Bounded Contexts; and
- Glossary / BoK concept references.

Cozy should validate model relations without collapsing the distinct boundaries.

In particular, validation must not require:

- one Business Process to map to one Workflow;
- one Business Process to map to one Bounded Context;
- one Workflow to map to one Bounded Context; or
- Business Process ownership to determine Component/runtime ownership.

## Views

The same semantic model can support several projections:

- Business Process View;
- Participant / Capability View;
- Business Process to Use Case Trace View;
- Business Process to Workflow Trace View;
- Business Process to Problem Domain View;
- Problem Domain / Bounded Context View; and
- Business Process / Bounded Context cross-map.

Flowchart View remains a projection of Workflow / StateMachine semantics where appropriate. It must not silently redefine the Business Process as an executable Workflow.

## Generation Boundary

Generated Workflow artifacts may carry stable provenance back to Business Process, Use Case, Problem Domain, and Bounded Context when those relations exist.

CNCF consumes executable Workflow semantics. Higher-level Business Process structure remains modeling metadata and projection input unless a later explicit runtime contract says otherwise.

## Design Rule

> Business Process is the Business-layer organizing boundary; Bounded Context is the Domain-model semantic boundary; Workflow is the executable coordination model. Cozy preserves and validates the relations among them without collapsing them into one boundary.
