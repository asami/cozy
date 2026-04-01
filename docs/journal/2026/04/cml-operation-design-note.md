# CML Operation Syntax — Design Note

- date: 2026-04-01
- status: draft

---

## Purpose

This note consolidates the discussion on the syntax and semantics of OPERATION in CML.

The goal is to:

- define a consistent and minimal syntax
- align with the existing CML type system (VALUE, ENTITY)
- support code generation (Cozy → runtime)
- enable clear contracts for CLI / HTTP / component wiring

---

## Core Design Principle

An OPERATION is a typed interaction boundary.

Operation
  Input  = Action (CommandAction | QueryAction)
  Output = OperationResult

---

## Key Decisions

### 1. Operation is Type-Based

- OPERATION does not define structure directly
- it references or defines VALUE types

---

### 2. Command / Query are Types

- Command and Query are not syntax modifiers
- they are expressed via inheritance

VALUE <: CommandAction | QueryAction

---

### 3. VALUE is the Only Structural Unit

- input/output structure must be expressed as VALUE
- no alternative structural syntax is introduced

---

### 4. No New Meta Syntax

The following are not introduced:

- CreateItem : Command
- INPUT (Command)
- @command / @query annotations

All semantics must be derived from existing constructs.

---

## Syntax Overview

### Basic Operation

# OPERATION

## createItem

### INPUT

CreateItemCommand

### OUTPUT

CreateItemResult

---

### VALUE Definition (Top-Level)

# VALUE

## CreateItemCommand

### EXTENDS

CommandAction

### PROPERTIES

| name | type | multiplicity |
|------|------|--------------|
| name | name | 1            |

## CreateItemResult

### EXTENDS

OperationResult

### PROPERTIES

| id | entityid | 1 |

---

## Operation-Local VALUE Definition

### Motivation

Some input/output types are used only within a single operation.

To support locality without breaking the type system,
CML allows nested VALUE definitions inside OPERATION.

---

### Syntax

# OPERATION

## createItem

### INPUT

#### VALUE

##### CreateItem

###### EXTENDS

CommandAction

###### PROPERTIES

| name | type | multiplicity |
|------|------|--------------|
| name | name | 1            |

---

### Output Example

### OUTPUT

#### VALUE

##### CreateItemResult

###### EXTENDS

OperationResult

###### PROPERTIES

| id | entityid | 1 |

---

## Semantics of Nested VALUE

Nested VALUE definitions are not local-only structures.

They are internally treated as regular VALUE types.

### Expansion Rule

OperationName + ValueName → Qualified VALUE

Example:

createItem.CreateItem
createItem.CreateItemResult

---

### Resolution Rules

- inside the operation:
  - short name is allowed (CreateItem)
- outside the operation:
  - qualified name must be used if referenced

---

## Command / Query Determination

The type of operation is inferred from INPUT:

if Input extends CommandAction → Command
if Input extends QueryAction   → Query

No explicit syntax exists at the OPERATION level.

---

## Constraints

### INPUT

- must resolve to a VALUE
- must extend:
  - CommandAction or QueryAction

---

### OUTPUT

- must resolve to a VALUE
- should extend:
  - OperationResult

---

## Inline Definition vs Top-Level Definition

| Aspect        | Top-Level VALUE | Nested VALUE |
|---------------|----------------|--------------|
| Reusability   | High           | Low          |
| Locality      | Low            | High         |
| Readability   | Good           | Good         |
| Generator     | Simple         | Simple       |

---

## Design Rationale

### Why not inline structure without VALUE?

Rejected:

INPUT

| name | type |

Reason:

- no type identity
- cannot generate DTO
- cannot support wiring or OpenAPI

---

### Why not syntax modifiers for Command/Query?

Rejected:

INPUT (Command)
CreateItem @command

Reason:

- breaks type-based design
- weak for validation and generation

---

### Why allow nested VALUE?

Accepted because:

- preserves type system
- improves locality
- avoids unnecessary top-level noise
- keeps DSL expressive

---

## Output Design Guidance

### Preferred

OUTPUT = CreateItemResult

→ strongly typed result

---

### Discouraged

OUTPUT = Record

Reason:

- loses semantics
- weak for component wiring
- should be fallback only

---

## Execution Mapping

### Internal Model

OperationResult (typed)

---

### External Representation

- JSON
- YAML
- XML

Serialization is handled at the boundary (CLI / server).

---

## Summary

- Operation is a typed boundary
- Input = Action (Command / Query via inheritance)
- Output = OperationResult
- VALUE is the only structural unit
- Nested VALUE is allowed and promoted to top-level semantics
- No new syntax is introduced for Command/Query

---

## Future Work

- Action hierarchy design (CommandAction / QueryAction)
- OperationResult type system
- Async command / Job model
- CLI / OpenAPI mapping specification
