# SimpleModeling Development Process (Minimum Integrated Workflow)

status=note
published_at=2026-04-12

## Overview

This document defines a minimum integrated development workflow for SimpleModeling.

The workflow combines four core elements:

- BoK (Body of Knowledge)
- Cozy (Model Compiler / DSL Processor)
- CNCF (Execution Platform)
- SKILL (Executable Process Knowledge)

The goal is to establish a development process that is:

- literate (knowledge-driven),
- model-centric,
- executable,
- AI-compatible.

This process is inspired by an Essence-based minimum development process,
but restructured for executable modeling and AI-assisted development.

---

## Core Concept

The SimpleModeling process is structured as a transformation pipeline:

BoK → Cozy → CNCF → Feedback → BoK

Additionally, SKILL acts as a meta-layer that:

- defines how the process is executed,
- bridges natural language and executable actions,
- enables AI agents to perform development tasks.

---

## Process Layers

### 1. Knowledge Layer (BoK)

BoK is the source of truth.

It includes:

- context description,
- domain knowledge,
- requirements,
- use cases,
- design rationale.

Characteristics:

- written in natural language (SmartDox),
- human-readable,
- AI-interpretable.

Outputs:

- structured knowledge,
- literate model fragments.

---

### 2. Modeling Layer (Cozy / CML)

Cozy transforms knowledge into executable models.

Responsibilities:

- convert BoK into structured DSL (CML),
- define:
  - COMPONENT
  - ENTITY
  - VALUE
  - OPERATION
  - EVENT
  - STATEMACHINE

Characteristics:

- precise,
- machine-processable,
- code-generatable.

Outputs:

- CML models,
- generated Scala code,
- scaffolding (e.g., car-sbt-project).

---

### 3. Execution Layer (CNCF)

CNCF executes the system.

Responsibilities:

- run components,
- execute operations,
- manage:
  - Entity lifecycle,
  - Action execution,
  - Event propagation.

Characteristics:

- component-based,
- cloud-native,
- execution-oriented.

Outputs:

- running system,
- logs,
- events,
- runtime state.

---

### 4. Process Layer (SKILL)

SKILL defines how development is performed.

Structure:

- natural language description,
- compiled Action definitions.

Responsibilities:

- define development workflow,
- orchestrate:
  - BoK creation,
  - modeling,
  - implementation,
  - testing,
  - deployment.

Mapping to Essence:

- SKILL ≈ Practice
- Action ≈ Activity
- ActionCall ≈ Activity Execution

Outputs:

- executable development procedures,
- reusable development knowledge.

---

## Integrated Workflow

### Step 1: Explore Context (BoK)

- describe opportunity,
- identify stakeholders,
- define context.

Output:

- context notes,
- stakeholder list.

---

### Step 2: Define Use Case (BoK → CML)

- define use cases,
- derive use-case slices.

Output:

- use-case model,
- requirement notes.

---

### Step 3: Model System (Cozy)

- define component structure,
- define entities and values,
- define operations.

Output:

- CML models,
- generated code.

---

### Step 4: Implement Component (CNCF)

- implement logic,
- bind Action/Behavior,
- integrate components.

Output:

- executable component.

---

### Step 5: Test Slice (CNCF + Spec)

- execute test cases,
- validate behavior.

Output:

- test results,
- verification logs.

---

### Step 6: Review & Feedback (BoK)

- reflect results,
- update knowledge.

Output:

- updated BoK,
- refined requirements.

---

### Step 7: Process Improvement (SKILL)

- refine workflow,
- update SKILL definitions.

Output:

- improved development process.

---

## Role of SKILL

SKILL is the key integration mechanism.

It enables:

- converting natural-language workflow into executable steps,
- automating development activities,
- enabling AI agents to perform development tasks.

SKILL structure:

- description (natural language),
- compiled form (Action sequence).

---

## Essence Mapping

This workflow can be interpreted in Essence terms:

- Alpha:
  - BoK artifacts (context, requirements)
  - System (CNCF components)

- Activity:
  - SKILL Actions

- Work Product:
  - BoK documents
  - CML models
  - code
  - test results

- Competency:
  - SKILL definitions

---

## Key Characteristics

- Literate:
  Knowledge and implementation are unified.

- Executable:
  Process and system are both executable.

- Traceable:
  From context → use case → model → code → runtime.

- AI-Compatible:
  SKILL enables AI-driven execution.

---

## Open Points

- Formal definition of SKILL DSL
- Integration of SKILL with CNCF runtime
- Mapping between SKILL and CML constructs
- Automation boundary between AI and human
