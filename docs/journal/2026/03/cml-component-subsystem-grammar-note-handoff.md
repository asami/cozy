CML Grammar Proposal for Component and Subsystem (Handoff)
=========================================================

status=proposed
phase=7+
date=2026-03-21

---

# 1. Overview

This document defines the CML DSL for:

- Component
- Componentlet
- Subsystem (wiring)
- Extension and configuration binding

The DSL is designed to:

- keep Component as the primary unit
- allow internal modularity via Componentlet
- support Subsystem composition and extension injection (SAR)

---

# 2. Core Principles

1. Component = distribution and execution unit (CAR)
2. Subsystem = composition and configuration unit (SAR)
3. Componentlet = internal modular unit (optional external exposure)
4. Extension = pluggable capability via ExtensionPoint
5. CML must be declarative and deterministic

---

# 3. Component DSL

## 3.1 Structure

# Component

## ${ComponentName}

### Description（optional）

${FreeText}

---

### Entity

${EntityName}

---

### Operation

${OperationName}
${OperationName}

---

### ExtensionPoint（optional）

${ExtensionPointName}
${ExtensionPointName}

---

### Componentlet（optional）

${ComponentletName}
${ComponentletName}

---

### Dependency（optional）

${ComponentName}

---

## 3.2 Example

# Component

## textus-user-account

### Entity

UserAccount

---

### Operation

createUser
changePassword
login
getUser

---

### ExtensionPoint

password-hash
transport

---

### Componentlet

auth
password
validation

---

# 4. Componentlet DSL

## 4.1 Structure

# Componentlet

## ${Component}.${ComponentletName}

### Operation

${OperationName}

---

### Visibility（optional）

internal | public

---

## 4.2 Example

# Componentlet

## textus-user-account.auth

### Operation

login

---

### Visibility

public

---

# Componentlet

## textus-user-account.password

### Operation

changePassword

---

### Visibility

internal

---

# 5. ExtensionPoint DSL

## 5.1 Structure

# ExtensionPoint

## ${Component}.${ExtensionPointName}

### Type（optional）

${string | enum | interface}

---

### Description（optional）

${FreeText}

---

## 5.2 Example

# ExtensionPoint

## textus-user-account.password-hash

### Type

string

---

# ExtensionPoint

## textus-user-account.transport

### Type

string

---

# 6. Subsystem DSL

## 6.1 Structure

# Subsystem

## ${SubsystemName}

### Description（optional）

${FreeText}

---

### Component

${ComponentCoordinate}
${ComponentCoordinate}

---

### Extension（optional）

${ExtensionBinding}

---

### Config（optional）

${ConfigKey} :: ${Value}

---

# 6.2 Component Coordinate

```
groupId:artifactId:version
```

Example:

```
org.simplemodeling.car:textus-user-account:0.1.0
```

---

# 6.3 Extension Binding

```
<Component>.<ExtensionPoint> = <value>
```

---

## 6.4 Example

# Subsystem

## textus-identity

### Component

org.simplemodeling.car:textus-user-account:0.1.0

---

### Extension

textus-user-account.password-hash = bcrypt
textus-user-account.transport = grpc

---

### Config

password.hash.cost :: 12

---

# 7. Semantics

## 7.1 Component

- defines capabilities
- exposes operations
- defines extension points

---

## 7.2 Componentlet

- optional internal module
- may be exposed externally
- shares execution model with Component

---

## 7.3 Subsystem

- composes Components
- selects Extensions
- overrides configuration

---

## 7.4 Extension Resolution

```
priority:
  SAR > CAR
```

---

# 8. Execution Model

```
Subsystem
  → Component
    → Componentlet
      → Operation
        → ActionCall
          → Task
            → Job
```

---

# 9. Validation Rules

- Component must define at least one Operation
- Componentlet must belong to exactly one Component
- Extension binding must match defined ExtensionPoint
- Subsystem must include at least one Component
- Component coordinate must be valid

---

# 10. AST Mapping

## Component

```scala
case class ComponentDef(
  name: String,
  entities: Vector[String],
  operations: Vector[String],
  extensionPoints: Vector[String],
  componentlets: Vector[String],
  dependencies: Vector[String]
)
```

---

## Subsystem

```scala
case class SubsystemDef(
  name: String,
  components: Vector[Coordinate],
  extensions: Map[String, String],
  config: Map[String, String]
)
```

---

# 11. Design Notes

- Component DSL is capability-oriented
- Subsystem DSL is composition-oriented
- Extension is late-bound
- Config is environment-specific

---

# 12. Key Definition

```
Component = defines "what can be done"
Subsystem = defines "how it is used"
```

---

End.
