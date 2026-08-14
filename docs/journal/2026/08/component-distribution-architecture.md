# CAR, Component, Subcomponent, and Subsystem Architecture

status=reference, non-normative
date=2026-08-14
canonical_phase58_proposal=cloud-native-component-framework:docs/notes/component-subcomponent-architecture-implementation.md

This journal records the architectural input that led to the expanded CNCF
Phase 58 work. It is reference information only and does not define a Cozy or
CNCF contract. The canonical working proposal is
`cloud-native-component-framework:docs/notes/component-subcomponent-architecture-implementation.md`.
At Phase 58 closure, its promoted CNCF design and specification supersede this
reference wherever the documents differ.

## Design Philosophy

A **Component** is the fundamental unit of Component-Based Development (CBD).

A **CAR (Component Archive)** is the canonical artifact representing a Component.

Every top-level Component contains its primary CNCF Runtime implementation and can be executed directly by the CNCF Runtime.

A Component may also own one or more **Subcomponents**.

Each Subcomponent is itself a Component, packaged as an independent CAR, but structurally belongs to a parent Component.

A **Subsystem** is an executable composition of one or more Components.

A single Component is implicitly treated as a single-Component Subsystem.

---

# Core Structure

```text
Order Component
│
├── CNCF Runtime Implementation
│      └── Primary implementation of Order
│
└── Subcomponents
       ├── Order React Presentation
       ├── Order KMP Presentation
       ├── Order Batch
       ├── Order Cloud Function
       ├── Order Documentation
       └── Order Source
```

The parent Component is not merely a container.

It is itself an executable CNCF Component with its own primary runtime implementation.

Subcomponents extend or complement the parent Component.

---

# Component and CAR

A Component is represented by a CAR.

```text
Component
    │
    ▼
   CAR
```

A CAR is:

- The canonical representation of a Component
- Directly executable by the CNCF Runtime
- Self-describing
- AI-readable
- Versioned
- Distributable

For example:

```text
order.car
```

contains the primary CNCF Runtime implementation of the Order Component.

---

# Parent Component

The parent Component contains:

- Component definition
- Primary CNCF Runtime implementation
- Metadata
- Dependency information
- Subcomponent registry
- Standard documentation and introspection metadata

Conceptually:

```text
order.car

├── component definition
├── CNCF runtime implementation
├── metadata
├── dependencies
└── subcomponent registry
```

The CNCF Runtime can execute the parent CAR directly.

```bash
cncf run order.car
```

---

# Subcomponent Registry

The parent Component manages the list of Subcomponents that belong to it.

Example:

```yaml
subcomponents:
  - name: order-react
    role: presentation
    implementation: react

  - name: order-mobile
    role: presentation
    implementation: kmp

  - name: order-batch
    role: batch
    implementation: scala-jvm

  - name: order-function
    role: function
    implementation: scala-js

  - name: order-document
    role: documentation
    implementation: markdown

  - name: order-source
    role: source
```

This registry defines the structural relationship between the parent Component and its Subcomponents.

---

# Subcomponents

A Subcomponent is itself a Component.

The difference is structural:

> A Subcomponent is a Component that belongs to a parent Component and fulfills a specific role.

A Subcomponent has:

- Its own CAR
- Its own CNCF Runtime representation
- Its own metadata
- Its own documentation
- Its own MCP-accessible information
- A parent Component reference
- A role
- An implementation type

For example:

```text
order-react.car

parent: order
role: presentation
implementation: react
```

Another example:

```text
order-function.car

parent: order
role: function
implementation: scala-js
```

---

# Component Hierarchy

```text
Order Component
│
├── Primary CNCF Runtime Implementation
│
├── Subcomponent: Presentation
│      └── order-react.car
│
├── Subcomponent: Presentation
│      └── order-mobile.car
│
├── Subcomponent: Batch
│      └── order-batch.car
│
├── Subcomponent: Function
│      └── order-function.car
│
├── Subcomponent: Documentation
│      └── order-document.car
│
└── Subcomponent: Source
       └── order-source.car
```

The parent Component owns the Subcomponent relationship.

The Subcomponents remain independently executable Components.

---

# Subcomponent Roles

Typical Subcomponent roles include:

- Presentation
- Batch
- Function
- Integration
- Documentation
- Source
- Test
- AI
- Resource

Roles describe what responsibility a Subcomponent fulfills relative to its parent Component.

---

# Implementation Technologies

Role and implementation technology are separate concepts.

For example:

```text
role = presentation
implementation = react
```

or:

```text
role = presentation
implementation = kmp
```

or:

```text
role = function
implementation = scala-js
```

or:

```text
role = batch
implementation = scala-jvm
```

This separation allows multiple implementation technologies to fulfill the same role.

---

# Example Role and Implementation Matrix

```text
Role             Implementation

Presentation     React
Presentation     KMP
Presentation     CNCF Built-in Web

Function         Scala.js
Function         JavaScript
Function         WASM

Batch            Scala/JVM
Batch            Kubernetes Job

Documentation    Markdown
Documentation    HTML

Source           Scala
Source           Kotlin
Source           TypeScript
```

---

# Independent Execution

Every Subcomponent is packaged as a CAR and can be executed independently by CNCF.

Examples:

```bash
cncf run order-react.car

cncf run order-mobile.car

cncf run order-function.car

cncf run order-document.car
```

This does not necessarily mean that the target implementation itself executes inside CNCF.

For example, a React Subcomponent may contain a React application intended for browser deployment.

When its CAR is executed by CNCF, the Component provides access to:

- Metadata
- Documentation
- Usage information
- Build information
- Deployment information
- Artifact information
- MCP resources

The actual React application is deployed using Web platform tooling.

---

# Primary Runtime vs External Platform Artifact

The parent Component has a primary CNCF Runtime implementation.

```text
order.car
    │
    ▼
CNCF Runtime
```

A Subcomponent may carry an implementation artifact for another execution platform.

For example:

```text
order-react.car
    │
    ├── CNCF-side Component representation
    │
    └── React implementation artifact
             │
             ▼
        Web deployment
```

Similarly:

```text
order-mobile.car
    │
    ├── CNCF-side Component representation
    │
    └── KMP implementation artifact
             │
             ▼
        iOS / Android build
```

And:

```text
order-function.car
    │
    ├── CNCF-side Component representation
    │
    └── Scala.js implementation artifact
             │
             ▼
        Cloud Function deployment
```

---

# Platform-Specific Deployment

CAR is responsible for distribution.

The target platform remains responsible for deployment.

Examples:

```text
React
  → npm / pnpm / Vite / Web hosting

KMP
  → Gradle / Xcode / App Store / Play Store

Scala.js
  → Cloud Function tooling

Kubernetes Job
  → Kubernetes deployment tooling
```

CNCF does not need to abstract or replace these platform-native deployment mechanisms.

---

# Documentation

Documentation may be represented as a Subcomponent.

Example:

```text
order-document.car
```

It can expose operations such as:

- document.list
- document.read
- document.search
- document.examples

This allows documentation to be distributed, versioned, inspected, and queried using the same Component infrastructure.

---

# MCP Integration

Every Component CAR, including Subcomponent CARs, can expose information through MCP.

Typical information includes:

- component.describe
- component.operations
- component.documentation
- component.examples
- component.dependencies
- component.usage
- component.build
- component.deployment
- component.artifacts
- component.parent
- component.role

This enables AI systems such as Codex or OpenClaw to inspect Components directly.

---

# AI-Friendly Components

A Component should be able to answer questions such as:

- What is this Component?
- What is its parent Component?
- What role does it fulfill?
- What implementation technology does it use?
- How should it be used?
- How should it be built?
- How should it be deployed?
- Which artifacts does it contain?
- Which Components does it depend on?
- Which Subcomponents belong to it?
- Which examples are available?

This makes CAR a complete knowledge package for both humans and AI agents.

---

# Subsystem

A Subsystem is an executable composition of Components.

```text
Subsystem
│
├── Component A
│     └── A.car
│
├── Component B
│     └── B.car
│
└── Component C
      └── C.car
```

A single CAR behaves as an implicit Subsystem.

```text
A.car
   │
   ▼
Implicit Single-Component Subsystem
   │
   ▼
Execution
```

---

# Relationship Between Component, Subcomponent, and Subsystem

```text
Subsystem
    │
    ├── Component
    │      │
    │      ├── Primary CNCF Runtime Implementation
    │      │
    │      └── Subcomponent Registry
    │             │
    │             ├── Subcomponent CAR
    │             ├── Subcomponent CAR
    │             └── Subcomponent CAR
    │
    └── Component
           │
           └── ...
```

The concepts have different responsibilities:

- **Component**: Fundamental CBD unit
- **CAR**: Canonical artifact of a Component
- **Subcomponent**: Component belonging to a parent Component with a specific role
- **Subsystem**: Executable composition of one or more Components

---

# Versioning

The parent Component and its Subcomponents are managed under the Component versioning model defined by CAR.

A CAR represents one immutable Component snapshot.

Documentation, source, implementation artifacts, and metadata associated with that CAR belong to the same version.

Subcomponent relationships are also part of the versioned Component definition.

---

# Overall Architecture

```text
                         Subsystem
                             │
              ┌──────────────┴──────────────┐
              │                             │
         Order Component              Other Component
              │
              ├── CNCF Runtime
              │
              └── Subcomponents
                    │
        ┌───────────┼───────────┬──────────────┐
        │           │           │              │
     React         KMP      Scala.js       Document
        │           │           │              │
        ▼           ▼           ▼              ▼
     Web App     Mobile     Cloud Func      Documentation
```

Each Subcomponent remains a normal Component with its own CAR.

---

# Design Principles

1. **Component is the fundamental CBD unit.**
2. **CAR is the canonical artifact of a Component.**
3. **A top-level Component contains its primary CNCF Runtime implementation.**
4. **A Component manages the list of Subcomponents that belong to it.**
5. **A Subcomponent is itself a Component and therefore has its own CAR.**
6. **A Subcomponent relationship consists of at least a parent Component and a role.**
7. **Role and implementation technology are separate concepts.**
8. **Every CAR can be executed independently by the CNCF Runtime.**
9. **Every CAR is self-describing and can expose usage information through MCP.**
10. **Non-CNCF implementation artifacts are distributed through CAR but deployed using platform-native mechanisms.**
11. **A Subsystem is an executable composition of Components.**
12. **A single CAR implicitly acts as a single-Component Subsystem.**
