Cozy Modeling Language (CML) — Component-Centric Specification
=============================================================

status=proposed
phase=7+
date=2026-03-21

---

# 1. Overview

This document defines the Component-centric DSL for CML.

CML describes:

- Component (primary unit)
- Domain model (Entity / Value)
- Execution model (Operation / Command / Query)
- Event model
- Extension model
- Internal modularity (Componentlet)

---

# 2. Design Principles

1. Component is the primary modeling unit
2. All domain elements belong to a Component
3. DSL is declarative and deterministic
4. CQRS is explicitly modeled
5. Extension is first-class

---

# 3. Top-Level Structure

CML is organized as:

```
# COMPONENT
  → Component definition
```

Multiple components may be defined in a file.

---

# 4. Component Definition

## Structure

# COMPONENT

## ${ComponentName}

### Description（optional）

${FreeText}

---

### OPERATION

### ENTITY

### VALUE

### EVENT

### EXTENSIONPOINT（optional）

### COMPONENTLET（optional）

### DEPENDENCY（optional）

---

# 5. Operation DSL

## Structure

### OPERATION

#### ${OperationName}

##### COMMAND | QUERY

---

## Example

### OPERATION

#### createUser

##### COMMAND

#### getUser

##### QUERY

---

## Semantics

- COMMAND → write (Aggregate)
- QUERY   → read (View)

---

# 6. Entity DSL

## Structure

### ENTITY

#### ${EntityName}

##### ATTRIBUTE

${name} :: ${Type}

---

##### POWERTYPE（optional）

${PowertypeName}

---

##### STATEMACHINE（optional）

${StateMachineName}

---

## Example

### ENTITY

#### UserAccount

##### ATTRIBUTE

id    :: UserId
email :: Email
status :: UserStatus

---

##### STATEMACHINE

UserStatus

---

# 7. Value DSL

## Structure

### VALUE

#### ${ValueName}

##### ATTRIBUTE

${name} :: ${Type}

---

## Example

### VALUE

#### UserId

##### ATTRIBUTE

value :: String

---

# 8. Event DSL

## Structure

### EVENT

#### ${EventName}

##### ATTRIBUTE

${name} :: ${Type}

---

## Example

### EVENT

#### UserCreated

##### ATTRIBUTE

userId :: UserId

---

# 9. ExtensionPoint DSL

## Structure

### EXTENSIONPOINT

#### ${ExtensionPointName}

---

## Example

### EXTENSIONPOINT

#### password-hash
#### transport

---

## Semantics

- defines pluggable capability
- bound at Subsystem (SAR)

---

# 10. Componentlet DSL

## Structure

### COMPONENTLET

#### ${ComponentletName}

---

## Example

### COMPONENTLET

#### auth
#### password

---

## Semantics

- internal modular unit
- optional external exposure

---

# 11. Dependency DSL

## Structure

### DEPENDENCY

${ComponentName}
${ComponentName}

---

## Example

### DEPENDENCY

textus-session

---

# 12. Semantics Summary

## Component

```
unit of distribution and execution
```

---

## Operation

```
execution contract
```

---

## Entity

```
stateful domain model
```

---

## Value

```
immutable data structure
```

---

## Event

```
state transition record
```

---

## ExtensionPoint

```
pluggable capability
```

---

## Componentlet

```
internal modular structure
```

---

# 13. AST Mapping

## Component

```scala
case class ComponentDef(
  name: String,
  operations: Vector[OperationDef],
  entities: Vector[EntityDef],
  values: Vector[ValueDef],
  events: Vector[EventDef],
  extensionPoints: Vector[String],
  componentlets: Vector[String],
  dependencies: Vector[String]
)
```

---

## Operation

```scala
case class OperationDef(
  name: String,
  opType: OperationType
)
```

---

## Entity

```scala
case class EntityDef(
  name: String,
  attributes: Vector[AttributeDef],
  powertypes: Vector[String],
  statemachines: Vector[String]
)
```

---

# 14. Constraints

- Component must define at least one Operation
- Operation must be either COMMAND or QUERY
- Entity must define at least one attribute
- ExtensionPoint names must be unique within Component
- Componentlet must belong to one Component

---

# 15. Example (Textus User Account)

# COMPONENT

## textus-user-account

### OPERATION

#### createUser
##### COMMAND

#### login
##### COMMAND

#### getUser
##### QUERY

---

### ENTITY

#### UserAccount

##### ATTRIBUTE

id    :: UserId
email :: Email
status :: UserStatus

---

### VALUE

#### UserId
#### Email

---

### EVENT

#### UserCreated

---

### EXTENSIONPOINT

#### password-hash
#### transport

---

### COMPONENTLET

#### auth
#### password

---

# 16. Key Definition

```
CML = Component-centric domain and execution modeling language
```

---

End.
