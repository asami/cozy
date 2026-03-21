# CML Grammar (Latest, Cozy)

status=frozen-for-phase8-op-01-op-03
updated_at=2026-03-22
target=/Users/asami/src/dev2025/cozy

## 1. Scope

This document is the canonical CML grammar contract for Cozy `modeler-scala`.
It merges:

- currently implemented grammar
- Phase 8 OP-01/OP-03 operation handoff contract

This document is normative for parser/modeler/generator behavior.
It intentionally excludes draft/WIP grammar proposals.

---

## 2. CML Meta-Grammar

CML accepts multiple syntactic forms that normalize into the same AST:

- section structure (heading-based)
- dl list
- table

Guideline:

- use section structure for descriptive documents
- use dl/table for compact machine-oriented documents

Keyword convention:

- grammar keywords are uppercase in this document (`ENTITY`, `ATTRIBUTE`, `STATEMACHINE`, `EVENT`, `OPERATION`, ...)
- parser matching remains case-insensitive unless explicitly restricted

---

## 3. Top-Level Sections

Supported top-level sections:

- `# ENTITY`
- `# EVENT` (global event definitions)
- `# OPERATION`
- `# COMMAND` (operation input value definitions)
- `# QUERY` (operation input value definitions)

Future sections may be added, but this contract focuses on accepted and frozen behavior for the current phase.

---

## 4. ENTITY

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

### 4.2 FEATURES (Inheritance)

```text
## Person

### FEATURES
EXTENDS = ["SimpleEntity"]
```

Notes:

- `FEATURES` must be a standalone heading.
- `EXTENDS = [...]` must be in body text, not heading text.
- `SimpleEntity` is treated as `org.goldenport.model.SimpleEntity`.

### 4.3 ATTRIBUTE Columns

Required:

- `name`
- `type`
- `multiplicity`

Optional:

- `db_column_name`
- `db_column_type`
- `external_name`

Default naming if omitted:

- datastore column name: `snake_case(name)`
- external output name: `snake_case(name)`

---

## 5. STATEMACHINE

`STATEMACHINE` is defined under each entity.

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

Validation:

- missing `ON`: error
- undefined `TO` target: error
- if event declarations exist, `ON` must reference declared event
- same priority conflict is resolved by declaration order (deterministic)

Guard mapping:

- single identifier -> guardRef
- other expressions -> expression guard

---

## 6. EVENT DSL

Event reception metadata is defined by `EVENT` (not `EventReception`).

Placement:

- `ENTITY > EVENT`
- top-level `# EVENT`

Both are merged.

```text
### EVENT
#### person.created
- CATEGORY :: ActionEvent
- KIND :: created
- SELECTOR :: source=crm
- ACTION_NAME :: person.sync
- PRIORITY :: 0
```

Mapped metadata:

- `name`
- `CATEGORY` (`ActionEvent` | `NonActionEvent`, default `NonActionEvent`)
- `KIND` (string)
- `SELECTOR` (`k=v`, repeatable)
- `ACTION_NAME`
- `PRIORITY` (int, default 0)

Generated output:

- `DomainComponent.eventReceptionDefinitions`

---

## 7. AGGREGATE / VIEW (AV-02)

`AGGREGATE` and `VIEW` are defined under each `ENTITY`.

AST mapping:

- `EntityDef.aggregate: Option[AggregateDef]`
- `EntityDef.view: Option[ViewDef]`

Semantic constraints:

- aggregate = command-side model
- view = read-side projection model
- view must not mutate command-side state
- event must not depend on view metadata

---

## 8. OPERATION DSL (Phase 8 Frozen Contract)

This section freezes OP-01/OP-03 grammar and normalization contract.

### 8.1 Purpose

`OPERATION` is first-class and must normalize to canonical single-input model.

### 8.2 OPERATION Kind

Operation kind is mandatory:

- `COMMAND`
- `QUERY`

### 8.3 Input Value Definition Sections

Operation input value types are defined in top-level `COMMAND` / `QUERY` sections.

```text
# COMMAND
## CreateOrder
### ATTRIBUTE
| name    | type    | multiplicity |
|---------+---------+--------------|
| orderId | OrderId | 1            |
| amount  | Money   | 1            |

# QUERY
## GetOrder
### ATTRIBUTE
| name    | type    | multiplicity |
|---------+---------+--------------|
| orderId | OrderId | 1            |
```

Input typing rule:

- `TYPE=COMMAND` requires `INPUT` to resolve to a command value type
- `TYPE=QUERY` requires `INPUT` to resolve to a query value type

### 8.4 Canonical Form

```text
# OPERATION
## createOrder
### TYPE
COMMAND
### INPUT
CreateOrder
### OUTPUT
CreateOrderResult
```

Canonical semantic shape:

`operation name(input: InputType): OutputType`

### 8.5 Convenience Form

```text
# OPERATION
## createOrder
### TYPE
COMMAND
### PARAMETER
orderId :: OrderId
amount  :: Money
### OUTPUT
CreateOrderResult
```

Convenience form is syntactic sugar and must normalize into canonical single-input form.

### 8.6 Dual Definition Form

Both `INPUT` and `PARAMETER` may coexist:

```text
# OPERATION
## createOrder
### TYPE
COMMAND
### INPUT
CreateOrder
### PARAMETER
orderId :: OrderId
amount  :: Money
```

`PARAMETER` must be field-consistent with `INPUT`.

### 8.7 Input Value Typing

Operation input value is explicitly typed:

- `COMMAND` operation -> command value input
- `QUERY` operation -> query value input

Type mismatch is invalid.

### 8.8 Normalization Rules

All operations normalize to:

`NormalizedOperation(name, kind, inputType, outputType, inputValueKind)`

Rules:

1. canonical (`INPUT`) -> use declared input type
2. convenience (`PARAMETER` only) -> auto-generate input value type with deterministic field order
3. dual (`INPUT + PARAMETER`) -> validate strict consistency, then keep declared input type

Auto-generated input type naming:

- default: `<OperationNameInPascalCase>Input`
- if name collision exists: `<OperationNameInPascalCase>Input2`, `...Input3`, ...

Determinism:

- field order follows declaration order in `PARAMETER`
- normalized output is stable for equivalent source

### 8.9 Validation Rules (Mandatory)

Rejected definitions:

1. `COMMAND` with query-value input
2. `QUERY` with command-value input
3. dual definition mismatch (`INPUT` fields vs `PARAMETER`)
4. missing required operation core fields (`TYPE`, and either `INPUT` or `PARAMETER`)
5. missing `OUTPUT`

Dual consistency criteria:

- field count must match
- field declaration order must match
- field name must match
- field type must match
- field multiplicity must match

Validation failures must be deterministic and not rely on incidental parser behavior.

### 8.10 Emitted Metadata Contract

Generator boundary must emit enough metadata for SimpleModeler/CNCF:

- operation name
- operation kind (`COMMAND` / `QUERY`)
- normalized input type
- normalized input value kind (`COMMAND_VALUE` / `QUERY_VALUE`)
- output type reference
- normalized parameter list (ordered fields)

### 8.11 PARAMETER Syntax Variants (Equivalent Forms)

Section style:

```text
### PARAMETER
orderId :: OrderId
amount  :: Money
```

DL style:

```text
### PARAMETER
- orderId :: OrderId
- amount :: Money
```

Table style:

```text
### PARAMETER
| name    | type    | multiplicity |
|---------+---------+--------------|
| orderId | OrderId | 1            |
| amount  | Money   | 1            |
```

---

## 9. Runtime Alignment Notes

Runtime expectation for integrated CNCF path:

- `COMMAND`: async job default execution path
- `QUERY`: sync read path (or equivalent ephemeral job path)

---

## 10. Accepted / Rejected Examples

Accepted:

- canonical operation with explicit `INPUT`
- convenience operation with only `PARAMETER` (auto input generation)
- dual definition with strict field consistency

Rejected:

- `COMMAND` operation bound to query-value input
- `QUERY` operation bound to command-value input
- dual definition with field name/type/order inconsistency

---

## 11. References

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/cml-operation-arg-handoff.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/cml-operation-input-command-query-value-handoff.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8-checklist.md`
- `/Users/asami/src/dev2025/cozy/docs/design/cml-grammar-draft.md` (draft/WIP only)
