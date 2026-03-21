# CML Grammar (Latest, Cozy)

status=working  
updated_at=2026-03-20  
target=/Users/asami/src/dev2025/cozy

## 1. Scope of This Document

This document summarizes the **CML grammar that Cozy `modeler-scala` currently accepts and uses in practice**.  
It is not an ideal/future spec; it is an implementation- and test-based snapshot.

---

## 2. CML AST Mapping (Meta-Grammar)

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

## 3. Top-Level Structure

Minimum structure:

```text
# ENTITY

## <ENTITY_NAME>
...
```

Multiple entities can be defined in sequence.

---

## 4. ENTITY Definition

### 4.1 Basic Form

```text
# ENTITY

## Person

### ATTRIBUTE

| name | type     | multiplicity |
|------+----------+--------------|
| id   | entityid | 1            |
| name | name     | 1            |
| age  | age      | ?            |
```

### 4.2 `FEATURES` (Inheritance)

```text
## Person

### FEATURES

EXTENDS = ["SimpleEntity"]
```

Notes:
- Place `### FEATURES` as a standalone heading.
- Put `EXTENDS = [...]` directly below the heading (do not place it on the heading line).
- Due to SmartDox heading parsing behavior, a blank line after the heading is recommended.

When `SimpleEntity` is specified, it is treated as extending `org.goldenport.model.SimpleEntity`.

---

## 5. ATTRIBUTE Table

### 5.1 Required Columns

- `name`
- `type`
- `multiplicity`

`multiplicity` examples:
- `1`: required single value
- `?`: optional single value

### 5.2 Optional Columns (Currently Supported)

- `db_column_name`
- `db_column_type`
- `external_name`

Example:

```text
| name        | type     | multiplicity | db_column_name       | db_column_type | external_name    |
|-------------+----------+--------------+----------------------+----------------+------------------|
| id          | entityid | 1            | person_id            | TEXT           | external_id      |
| displayName | name     | 1            | person_display_name  | TEXT           | display_name_ext |
| age         | age      | ?            |                      | INTEGER        |                  |
```

If omitted:
- DB column name: `snake_case(name)`
- External integration attribute name: `snake_case(name)`

---

## 6. STATEMACHINE

You can place `### STATEMACHINE` inside an `ENTITY`.

```text
### STATEMACHINE

#### lifecycle

##### STATE

###### Draft

####### TRANSITION
- TO :: Published
- ON :: publish
- GUARD :: paymentConfirmed
- ACTION :: recordPayment

###### Published

##### EVENT

###### publish
```

### 6.1 Transition Keys

- Required:
  - `TO`
  - `ON`
- Optional:
  - `GUARD`
  - `ACTION` (multiple lines allowed)

### 6.2 Guard Interpretation

- Single identifier (for example, `paymentConfirmed`): treated as guardRef.
- Anything else (for example, `event.amount > 0 && reviewerApproved`): treated as expression.

### 6.3 Validation Rules (Current)

- Missing `ON`: error
- Undefined `TO` destination: error
- If an `EVENT` section exists, `ON` must reference a declared event
- For same-priority conflicts, declaration order is used (deterministic)

---

## 7. EVENT DSL (Current Implemented Subset)

Event reception metadata is defined in the `EVENT` section (not `EventReception`).

```text
### EVENT

#### person.created
- CATEGORY :: ActionEvent
- KIND :: created
- SELECTOR :: source=crm
- ACTION_NAME :: person.sync
- PRIORITY :: 0
```

Mapping to runtime (`CmlEventDefinition`):

- `name`
- `CATEGORY` (`ActionEvent` / `NonActionEvent`, default `NonActionEvent`)
- `KIND`
- `SELECTOR` (`k=v`)
- `ACTION_NAME`
- `PRIORITY`

Placement (both are supported and merged):

- `ENTITY > EVENT` (entity-local)
- top-level `# EVENT` (global)

Generated output:

- `DomainComponent.eventReceptionDefinitions`

EV-06 verified behavior:

- target event routes
- non-target event deterministically drops
- unknown event fails
- subscription mismatch fails
- policy denial fails
- persistence differs between `persistent=true` and `persistent=false`

Implementation status:

- `kaleidox` parses event metadata into schema metadata.
- Cozy emits metadata as `Vector[org.goldenport.cncf.event.CmlEventDefinition]`.
- scripted verification uses generated `DomainComponent.eventReceptionDefinitions`.

---

## 8. AGGREGATE / VIEW DSL (AV-02)

`AGGREGATE` and `VIEW` sections can be defined inside `ENTITY`.

### 8.1 AGGREGATE

```text
### AGGREGATE

#### COMMAND

##### createPerson
- INPUT.name :: name
- VALIDATE :: name.nonEmpty
- EVENT :: person.created
- NEW_STATE :: Active

#### STATE
| name | type | multiplicity |
|------+------|--------------|
| id   | entityid | 1         |
| name | name     | 1         |

#### INVARIANT

##### nameRequired
- EXPRESSION :: state.name.nonEmpty
```

AST mapping:
- `EntityDef.aggregate: Option[AggregateDef]`
- `AggregateDef(commands, state, invariants)`

### 8.2 VIEW

```text
### VIEW
- EVENTS :: person.created, person.updated
- REBUILDABLE :: true

#### ATTRIBUTE
| name | type | multiplicity |
|------+------|--------------|
| id   | entityid | 1         |
| name | name     | 1         |

#### QUERY

##### searchPublished
- EXPRESSION :: poststatus == "published"
```

AST mapping:
- `EntityDef.view: Option[ViewDef]`
- `ViewDef(attributes, queries)`

Validation:
- `VIEW/QUERY` must not include command-side mutation directives (`ACTION`, `WRITE`, `MUTATION`, `MUTATES`).
- `EVENT` must not depend on `VIEW` metadata.

---

## 9. Recommended Template

```text
# ENTITY

## SimpleEntity

### ATTRIBUTE

| name | type     | multiplicity |
|------+----------+--------------|
| id   | entityid | 1            |
| name | name     | 1            |

## Person

### FEATURES

EXTENDS = ["SimpleEntity"]

### ATTRIBUTE

| name | type | multiplicity |
|------+------|--------------|
| age  | age  | ?            |
```

---

## 10. Future Extension Points

- Promote EventReception as an official top-level modeler input
- Extend Event selectors to support composite conditions (AND/OR)
- Extend CML sections by purpose (entity/aggregate/view)

---

## 11. CNCF EVENT/SUBSCRIPTION/Runtime Design (WIP)

This section consolidates the design draft from:

- `docs/journal/2026/03/cml-event-dsl-and-runtime.md`

### 10.1 Design Principles

- Event = fact (pure meaning)
- Subscription = execution rule
- Routing = partitioning / filtering
- DispatchRoute = delivery semantics
- ActionCall = execution unit

### 10.2 DSL Normalization Rule

CML forms are interchangeable and normalized into one AST:

- section structure (heading-based)
- dl list
- table

Recommended usage:

- section style for descriptive docs
- dl/table for compact/machine-oriented docs

### 10.3 Extended EVENT DSL

```text
# EVENT

## ${EventName}

### CATEGORY
ActionEvent | NonActionEvent

### KIND
domain | system | integration

### ATTRIBUTE
${Entity Attribute Definition}

### ROUTING (optional)
TOPIC :: ${TopicName}
SERVICE :: ${ServiceName}
PARTITION :: ${Expression}
```

### 10.4 ROUTING Rule DSL

```text
# ROUTING

## ${RuleName}

### WHEN
${Expression}

### ROUTE
TOPIC :: ${TopicName}
SERVICE :: ${ServiceName}
PARTITION :: ${Expression}
```

### 10.5 Subscription DSL

Route types:

- Unicast
- Multicast
- Broadcast

Section style:

```text
# SUBSCRIPTION
## ${SubscriptionName}
### EVENT
${EventName}
### ROUTE
Unicast | Multicast | Broadcast
### ENTITY (optional)
${EntityName}
### TARGET (Unicast / Multicast)
${TargetExpression}
### TARGETS (Unicast explicit)
[${id1}, ${id2}, ...]
### SELECTOR (Multicast)
${BooleanExpression}
### ACTION
${ActionName}
### DECLARED_TARGET_UPPER_BOUND
${Number}
### ACTIVATION (optional)
KeepResident | ActivateOnReceive
```

DL style:

```text
# SUBSCRIPTION
## ${SubscriptionName}
- ON :: ${EventName}
- ROUTE :: Unicast | Multicast | Broadcast
- ENTITY :: ${EntityName}
- TARGET :: ${TargetExpression}
- TARGETS :: [${id1}, ${id2}]
- SELECTOR :: ${BooleanExpression}
- ACTION :: ${ActionName}
- BOUND :: ${Number}
- ACTIVATION :: KeepResident | ActivateOnReceive
```

### 10.6 Runtime Model

Execution flow:

```text
Channel -> Reception -> RoutingEngine -> SubscriptionIndex -> Dispatch -> ActionCall -> Execution(Job)
```

Routing strategy:

- Explicit routing
- Embedded routing (event attributes)
- Rule-based routing

Composite:

- `RoutingEngine = CompositeRoutingStrategy`

Subscription index keys:

- topic
- event name
- entity type

Dispatch semantics:

- Unicast: explicit or deterministic IDs
- Multicast: selector-based targets
- Broadcast: all targets

Target resolver types:

- ExplicitTargets
- FromEvent
- Deterministic

### 10.7 Validation Rules

Unicast:

- `target` or `targets` is required
- `selector` is not allowed

Multicast:

- `selector` is required

Broadcast:

- `selector` is not allowed
- explicit targets are not allowed

### 10.8 Separation of Responsibilities

- Event = what happened
- Routing = where to look
- Subscription = what to do
- Dispatch = how to apply
- ActionCall = execution

### 10.9 Notes

- Event does not define delivery semantics.
- Dispatch is defined by Subscription.
- Topic is a routing key, not domain meaning.
- Attribute grammar follows Entity schema.
