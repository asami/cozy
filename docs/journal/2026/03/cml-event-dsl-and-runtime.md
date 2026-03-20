CNCF Event DSL and Runtime Design
=================================

status=work-in-progress
published_at=2026-03-20

---

# Overview

This document defines:

- CML Event-related DSL
- Subscription and Dispatch model
- Routing and Topic integration
- Implementation mechanism for CNCF runtime

---

# 1. Design Principles

- Event = fact (pure meaning)
- Subscription = execution rule
- Routing = partitioning / filtering
- DispatchRoute = delivery semantics
- ActionCall = execution unit

---

# 2. CML AST Mapping

CML supports multiple syntactic forms:

- section structure (heading-based)
- dl list
- table

These are:

- interchangeable
- mapped into the same AST

Guidelines:

- use section structure for descriptive documents
- use dl/table for compact or machine-oriented definitions

---

# 3. Event DSL

# Event

## ${EventName}

### Category

ActionEvent | NonActionEvent

### Kind

domain | system | integration

### Attribute

# same format as Entity.Attribute
# actual grammar is defined in Entity specification

${Entity Attribute Definition}

### Routing（optional）

topic :: ${TopicName}
service :: ${ServiceName}
partition :: ${Expression}

---

# 4. Routing Rule DSL

# Routing

## ${RuleName}

### When

${Expression}

### Route

topic :: ${TopicName}
service :: ${ServiceName}
partition :: ${Expression}

---

# 5. Subscription DSL

## Section style

# Subscription

## ${SubscriptionName}

### Event

${EventName}

### Route

Unicast | Multicast | Broadcast

### Entity（optional）

${EntityName}

### Target（Unicast / Multicast）

${TargetExpression}

### Targets（Unicast explicit）

[${id1}, ${id2}, ...]

### Selector（Multicast）

${BooleanExpression}

### Action

${ActionName}

### DeclaredTargetUpperBound

${Number}

### Activation（optional）

KeepResident | ActivateOnReceive

---

## DL style

# Subscription

## ${SubscriptionName}

- on :: ${EventName}
- route :: Unicast | Multicast | Broadcast
- entity :: ${EntityName}
- target :: ${TargetExpression}
- targets :: [${id1}, ${id2}]
- selector :: ${BooleanExpression}
- action :: ${ActionName}
- bound :: ${Number}
- activation :: KeepResident | ActivateOnReceive

---

# 6. Dispatch Model

DispatchRoute:

- Unicast
- Multicast
- Broadcast

Definitions:

- Unicast = ID-based or deterministically resolved targets
- Multicast = selector-based targets
- Broadcast = all targets

---

# 7. Runtime Architecture

Flow:

Channel
  ↓
Reception
  ↓
RoutingEngine
  ↓
SubscriptionIndex
  ↓
Dispatch
  ↓
ActionCall
  ↓
Execution (Job)

---

# 8. Routing Mechanism

Routing is resolved by strategies:

1. Explicit routing
2. Embedded routing (event attributes)
3. Rule-based routing

These are combined:

RoutingEngine = CompositeRoutingStrategy

---

# 9. Subscription Index

Subscription lookup is performed by:

- topic
- event name
- entity type

This provides scalability.

---

# 10. Dispatch Execution

for each subscription:

  resolve targets:

    if Unicast:
      resolve explicit or deterministic IDs

    if Multicast:
      evaluate selector

    if Broadcast:
      select all

  for each target:

    create ActionCall

---

# 11. Target Resolution

TargetResolver types:

- ExplicitTargets
- FromEvent
- Deterministic

---

# 12. Validation Rules

Unicast:
  - target or targets required
  - selector not allowed

Multicast:
  - selector required

Broadcast:
  - no selector
  - no explicit targets

---

# 13. Key Separation

Event        = what happened
Routing      = where to look
Subscription = what to do
Dispatch     = how to apply
ActionCall   = execution

---

# 14. Notes

- Event does not define delivery semantics
- Dispatch is defined by Subscription
- Topic is a routing key, not domain meaning
- Attribute follows Entity schema

---

End.
