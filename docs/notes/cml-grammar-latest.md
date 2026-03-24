# CML Grammar (Latest, Cozy)

status=frozen-for-phase9-cs-01-cs-02
updated_at=2026-03-25
target=/Users/asami/src/dev2025/cozy

## 1. Scope

This document is the canonical CML grammar contract for Cozy `modeler-scala`.
It merges:

- currently implemented grammar
- Phase 8 OP-01/OP-03 operation handoff contract
- Phase 9 CS-01/CS-02 component-subsystem grammar contract

This document is normative for parser/modeler/generator behavior.
It intentionally excludes draft/WIP grammar proposals.
It also defines the Literate Model interpretation boundary used by Cozy.

---

## 2. CML Meta-Grammar

CML accepts multiple syntactic forms that normalize into the same AST:

- section structure (heading-based)
- dl list
- table
- hocon (section body)
- yaml (section body)

Guideline:

- use section structure for descriptive documents
- use dl/table for compact machine-oriented documents

Keyword convention:

- grammar keywords are uppercase in this document (`ENTITY`, `ATTRIBUTE`, `STATEMACHINE`, `EVENT`, `OPERATION`, ...)
- parser matching remains case-insensitive unless explicitly restricted

### 2.1 Literate Model Interpretation Layers

CML is interpreted as a Literate Model with three concurrent layers:

- Structural DSL layer: executable model structure (`ENTITY`, `EVENT`, `OPERATION`, `COMPONENT`, ...)
- Metadata DSL layer: machine-consumable modifiers (`TYPE`, `INPUT`, `OUTPUT`, `KIND`, `SELECTOR`, ...)
- Narrative layer: human/AI explanatory context (`Overview`, `Notes`, `Rationale`, ...)

Interpretation policy:

- structural + metadata layers are generation/runtime inputs
- narrative layer is non-executable context and must not silently override structural contracts

### 2.2 Section Classification Rule

Section meaning is determined by classification:

- structural section: defines AST nodes and executable model structure
- metadata section/key: enriches or constrains structural nodes
- narrative section: descriptive context only

This classification is normative for parser/modeler behavior and for future grammar extensions.

### 2.3 Normalized Interpretation Pipeline

For equivalent source expressions (heading, dl, table, hocon body, yaml body), Cozy applies the same pipeline:

1. Parse and normalize syntax forms.
2. Classify section role (structural/metadata/narrative).
3. Build structural AST.
4. Apply metadata overlays and validation.
5. Preserve narrative as non-executable context.
6. Emit deterministic generator/runtime metadata.

---

## 3. Top-Level Sections

Supported top-level sections:

- `# ENTITY`
- `# EVENT` (global event definitions)
- `# OPERATION`
- `# COMMAND` (operation input value definitions)
- `# QUERY` (operation input value definitions)
- `# COMPONENT`
- `# COMPONENTLET`
- `# EXTENSIONPOINT`
- `# SUBSYSTEM`

Future sections may be added, but this contract focuses on accepted and frozen behavior for the current phase.

`COMPONENT` is optional.
If no `# COMPONENT` section is defined, Cozy generates a default placeholder component definition at generation time.

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
- `SimpleEntity` is treated as `org.simplemodeling.model.SimpleEntity`.

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

Accepted input patterns:

1. `TYPE` section:

```text
### TYPE
COMMAND
```

2. Kind marker section (equivalent to `TYPE`):

```text
### COMMAND
enabled
```

or

```text
### QUERY
enabled
```

Note:
- marker section should include non-empty body text (e.g. `enabled`) for parser normalization stability.

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

## 9. COMPONENT / SUBSYSTEM DSL (Phase 9 Frozen Contract)

### 9.1 COMPONENT

`COMPONENT` defines packaging and composition metadata.
`COMPONENT` itself is optional at CML level (see default fallback in Section 3).

```text
# COMPONENT

## person

### COORDINATE

- coordinate :: org.simplemodeling.car:person-service:0.1.0

### COMPONENTLET

#### person_core
#### person_policy

### EXTENSIONPOINT

#### transport
#### password_hash

### EXTENSIONBINDING

#### transport
grpc

#### password_hash
bcrypt
```

Mapped metadata:

- `name`
- `coordinates` (`group:artifact:version`)
- `componentlets`
- `extensionPoints`
- `extensionBindings`

### 9.2 COMPONENTLET

Top-level `COMPONENTLET` is supported.

```text
# COMPONENTLET

## audit_sink
- component :: person
- kind :: infra
```

### 9.3 EXTENSIONPOINT

Top-level `EXTENSIONPOINT` is supported.

```text
# EXTENSIONPOINT

## observability
- component :: person
- interface :: MetricsSink
```

### 9.4 SUBSYSTEM

`SUBSYSTEM` defines composition metadata.

```text
# SUBSYSTEM

## identity

### COMPONENT
- org.simplemodeling.car:person-service:0.1.0

### EXTENSIONBINDING
#### transport
http

### CONFIG
#### profile
prod
```

Mapped metadata:

- `name`
- `components` (`group:artifact:version`)
- `extensionBindings`
- `config`

### 9.5 Validation Rules

Rejected definitions:

1. invalid component coordinate format (must be `group:artifact:version`)
2. invalid subsystem component coordinate format
3. empty component or subsystem names

Generator output contract:

- `DomainComponent.componentDefinitionRecords: Vector[Record]`
- `DomainComponent.subsystemDefinitionRecords: Vector[Record]`

If no explicit component is defined:

- Cozy emits one default component definition with package-based name.
- top-level unbound `COMPONENTLET` / `EXTENSIONPOINT` definitions are attached to that default component.

These records are deterministic and sorted by declaration-normalized order.

---

## 10. Runtime Alignment Notes

Runtime expectation for integrated CNCF path:

- `COMMAND`: async job default execution path
- `QUERY`: sync read path (or equivalent ephemeral job path)

---

## 11. Accepted / Rejected Examples

Accepted:

- canonical operation with explicit `INPUT`
- convenience operation with only `PARAMETER` (auto input generation)
- dual definition with strict field consistency

Rejected:

- `COMMAND` operation bound to query-value input
- `QUERY` operation bound to command-value input
- dual definition with field name/type/order inconsistency

---

## 12. References

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/cml-operation-arg-handoff.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/cml-operation-input-command-query-value-handoff.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/cml-component-subsystem-grammar-handoff.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/cml-component-centric-spec-handoff.md` (reference/informational)
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/cml-component-subsystem-grammar-note-handoff.md` (reference/informational)
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/literate-model-specification-latest-2026-03-24.md`
- `/Users/asami/src/dev2025/cozy/docs/journal/2026/03/literate-model-concept.md` (background concept)
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8-checklist.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-9.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-9-checklist.md`
- `/Users/asami/src/dev2025/cozy/docs/design/cml-grammar-draft.md` (draft/WIP only)

---

## 13. Address-Driven Extension Track (Partially Implemented)

status=partially-implemented
added_at=2026-03-24
updated_at=2026-03-25

This section captures extensions required to realize the Literate Model goal
with rich narrative + executable structure in one CML file (e.g. `address.cml`).

### 13.1 VALUE Compatibility Alias

Problem:
- Earlier literate authoring frequently used top-level `VALUE` semantics.
- Current frozen top-level sections are entity/operation/component-centric.

Extension proposal:
- accept top-level `VALUE` as a compatibility alias.
- normalize internally to `ENTITY` with `kind=VALUE` metadata.

Normalization contract:
- `# VALUE` + `## Name` + `### ATTRIBUTE` -> `EntityDef(name=Name, kind=VALUE)`
- generator keeps deterministic output equivalent to explicit `ENTITY` form.

Validation:
- `VALUE` must follow `ENTITY`-compatible attribute schema.

### 13.2 Attribute Constraint Metadata

Problem:
- Constraint intent (`length`, `pattern`, `format`) is often written in narrative,
  and not machine-bound.

Implemented contract:
- standardize optional attribute metadata keys:
  - `min_length`
  - `max_length`
  - `pattern`
  - `format`

Accepted syntax forms (same normalization):
- table columns
- dl list
- yaml section body

Normalization to `record.v2.Constraint`:
- `min_length` -> `CMinLength`
- `max_length` -> `CMaxLength`
- `pattern` -> `CRegex`
- `format` -> `CFormat` (currently `email` / `uuid` / `uri` / `url`)

Current implementation note:
- unsupported `format` values are ignored (no constraint emitted).

Example (yaml section body):

```text
### ATTRIBUTE
- name: value
  type: String
  multiplicity: "1"
  min_length: 2
  max_length: 2
  pattern: "^[A-Z]{2}$"
```

### 13.3 Literate Classification Diagnostics

Problem:
- Authors and AI agents cannot inspect how sections were classified
  (structural/metadata/narrative) after parsing.

Extension proposal:
- add diagnostics output (e.g. `cozyExplainModel`) that emits:
  - section path
  - classified role (`structural` | `metadata` | `narrative`)
  - normalized target node (if structural/metadata)

Purpose:
- improve author feedback and deterministic literate behavior.

### 13.4 Rollout Policy

- Section `13.2` is implemented and treated as normative behavior in the
  current parser/modeler pipeline.
- Sections `13.1` and `13.3` remain proposal-track and do not overwrite frozen
  contracts in sections 1-12.
