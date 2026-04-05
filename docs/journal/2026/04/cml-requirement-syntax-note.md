Cozy Modeling Language Specification (Integrated Edition)
========================================================

# HEAD

status=draft
published_at=2026-04-06

# OVERVIEW

Cozy Modeling Language (CML) is a modeling language designed to unify natural language specifications and structured executable DSL.

This document consolidates a series of articles into a single, coherent specification covering:

- Use case structure
- Scenario description
- Use case relationships
- Formalization toward executable models

CML serves as a bridge between human-readable specifications and machine-executable models in literate model-driven development.

# SOURCES

- https://modegramming.blogspot.com/2025/03/cozy.html
- https://modegramming.blogspot.com/2025/04/cozy.html
- https://modegramming.blogspot.com/2025/05/cozy.html
- https://modegramming.blogspot.com/2025/06/cozy.html
- https://modegramming.blogspot.com/2025/07/cozy.html
- https://modegramming.blogspot.com/2025/08/cozy.html
- https://modegramming.blogspot.com/2025/09/cozy.html
- https://modegramming.blogspot.com/2025/10/cozy.html
- https://modegramming.blogspot.com/2025/11/cozy.html
- https://modegramming.blogspot.com/2025/12/cozyinclude.html
- https://modegramming.blogspot.com/2026/01/cozyextend.html
- https://modegramming.blogspot.com/2026/02/cozygeneralize.html
- https://modegramming.blogspot.com/2026/03/cozygeneralize-realization.html

# CONCEPT

## Literate Modeling

CML adopts a literate modeling approach:

- Natural language provides semantic richness and human readability
- DSL provides structure and executability
- Both coexist in a single document

The document itself becomes:

- Specification
- Design artifact
- Executable model source

---

# USE CASE MODEL

## Structure of a Use Case

A use case consists of two layers:

### Basic Information

Defines identity and contract:

- Name
- Actors
- Summary
- Preconditions
- Postconditions

### Extended Information

Defines behavior:

- Main scenario
- Alternative scenarios
- Exception scenarios

This separation enables:

- Stable contract definition
- Flexible behavioral extension

---

## Semantics

A use case represents:

- An externally observable behavior
- A unit of interaction between actor and system
- A specification boundary

---

# SCENARIO MODEL

## Scenario as Structured Flow

A scenario is defined as a sequence of steps.

Each step represents:

- An action
- A decision
- A transition

### Characteristics

- Sequential
- Branchable
- Composable

---

## Scenario as DSL

In CML, scenarios are expressed as structured DSL:

- Steps become executable units
- Control flow is explicitly modeled
- Scenario structure is preserved for execution

---

## Execution Perspective

A scenario can be interpreted as:

- A procedure
- A workflow
- An executable specification

---

# USE CASE RELATIONSHIPS

CML defines relationships between use cases as **merge semantics**.

These relationships determine how behaviors are combined.

---

## include

### Definition

Include represents mandatory reuse of another use case.

### Semantics

- The included use case is always executed
- Behavior is merged inline

### Properties

- Merge Mode: Inline
- Merge Point: Arbitrary step
- Purpose: Reuse common behavior

---

## extend

### Definition

Extend represents conditional behavior extension.

### Semantics

- Additional behavior is inserted under specific conditions
- Base use case remains unchanged

### Properties

- Merge Mode: ConditionalInline
- Merge Point: After specified step
- Purpose: Optional extension

---

## generalize

### Definition

Generalize represents a type relationship between use cases.

### Semantics

- A specialized use case refines a generalized one
- Contracts are inherited and possibly extended

### Properties

- Merge Mode: TypeRefinement
- Merge Point: Not flow-dependent
- Purpose: Specification refinement

---

## Relationship Summary

| Relationship | Merge Mode        | Nature               | Purpose                     |
|--------------|------------------|---------------------|-----------------------------|
| include      | Inline           | Structural reuse     | Common behavior reuse       |
| extend       | ConditionalInline| Behavioral extension | Conditional augmentation    |
| generalize   | TypeRefinement   | Type relationship    | Specification refinement    |

---

# GENERALIZATION AND REALIZATION

## Separation of Concerns

CML explicitly separates:

- Type definition (generalize)
- Behavioral realization

Generalization defines:

- Structural relationship
- Contract hierarchy

It does NOT define:

- Execution mechanism

---

## Realization Strategies

Generalization can be realized through:

- Template method patterns
- Scenario substitution
- Parameterized behavior

These mechanisms are external to the generalization definition.

---

# FORMALIZATION TOWARD EXECUTION

## From Narrative to DSL

CML transforms:

Natural Language → Structured DSL → Executable Model

---

## Key Principles

### 1. Structure Preservation

The structure defined in natural language must be preserved in DSL.

### 2. Executability

All scenario elements must be translatable into execution units.

### 3. Composability

Use cases must be composable via relationships.

---

## Execution Mapping

Conceptual mapping:

- Use Case → Operation / Service
- Scenario → Workflow / Procedure
- Step → Action / Instruction

---

# POSITIONING

CML sits at the intersection of:

- Requirements engineering
- Domain modeling
- Executable specification

---

## Role in AI-Assisted Development

CML enables:

- AI to interpret structured natural language
- Deterministic transformation into code
- Alignment between human intent and generated implementation

---

# SUMMARY

Cozy Modeling Language provides:

- A unified representation of specification and execution
- A structured approach to use case modeling
- A formal mechanism for composing behavior

By integrating natural language and DSL, CML enables:

- Human-readable specifications
- Machine-executable models
- Seamless transition from design to implementation
