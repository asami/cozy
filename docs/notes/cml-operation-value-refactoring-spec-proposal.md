# CML Operation Value Refactoring Specification Proposal

status=implementation
updated_at=2026-07-15
target_phase=16

## 1. Scope

This note proposes the Phase 16 CML contract for operation input and output
Values. It is the implementation specification proposal used to write
executable specifications and implementation. After implementation and driver
verification, the resulting stable contract is promoted to design,
specification, and accepted grammar documents.

The proposal covers:

- reusable command/query input Values;
- anonymous and named operation-local input Values;
- anonymous and named operation-local output Results;
- predefined CML/CNCF Result types;
- normalization, validation, metadata, compatibility, and scaffold behavior.

The implemented top-level input-kind and operation-local Value boundaries use
the common CML AST. Kaleidox `ValueClass.properties` preserves direct Value
metadata parsed from SmartDox description-list nodes, and
`SchemaClass.createOption(name, section)` builds a named local schema from the
existing operation Section AST. Cozy reads those normalized models. Modeler
code must not recover operation metadata or local schemas by scanning plain
text or regular expressions.

Field-level semantic scalar, powertype, statemachine, text-range, and I18N
classification is specified separately in
`cml-semantic-scalar-modeling-spec-proposal.md`. Operation-local and reusable
Values use that accepted field type system rather than creating another scalar
catalog.

## 2. Core Model

`VALUE` is the only structural category used for operation payloads.

An operation input Value may have one of two input kinds:

- `COMMAND`
- `QUERY`

An operation output is a Result Value. A local output Value implicitly has
`OperationResult` semantics. A referenced output type must resolve to a defined
Result Value or to the CML/CNCF predefined Result catalog.

## 3. Reusable Input Value

The canonical reusable input syntax is top-level `# VALUE` with an explicit
`input-kind` property:

```cml
# VALUE

## CreateNotification
- input-kind :: COMMAND

### ATTRIBUTE
| name | type   | multiplicity |
|------|--------|--------------|
| text | string | 1            |
```

```cml
# VALUE

## SearchNotifications
- input-kind :: QUERY

### ATTRIBUTE
| name  | type   | multiplicity |
|-------|--------|--------------|
| text  | string | ?            |
| limit | int    | ?            |
```

`input-kind` is not inferred for a top-level Value. A top-level Value used as
an operation input must declare it explicitly unless the source uses a legacy
`# COMMAND` or `# QUERY` section.

An operation references a reusable input Value with `INPUT TYPE` or an existing
equivalent direct-property form:

```cml
##### INPUT

###### TYPE
SearchNotifications
```

## 4. Anonymous Local Input Value

An `ATTRIBUTE` section directly below `INPUT` defines an anonymous local input
Value:

```cml
#### searchNotifications

##### TYPE
QUERY

##### INPUT

###### ATTRIBUTE
| name  | type   | multiplicity |
|-------|--------|--------------|
| text  | string | ?            |
| limit | int    | ?            |
```

The local type name and input kind are deterministic:

| Operation kind | Local type name | Normalized input value kind |
|----------------|-----------------|-----------------------------|
| `QUERY` | `<PascalOperationName>Query` | `QUERY_VALUE` |
| `COMMAND` | `<PascalOperationName>Command` | `COMMAND_VALUE` |

For the example above, the normalized input type is
`SearchNotificationsQuery`.

The local definition must not declare `input-kind`. The enclosing operation is
the authoritative source of that property.

## 5. Named Local Input Value

`VALUE` below `INPUT` supplies an explicit local type name:

```cml
##### INPUT

###### VALUE
NotificationSearchCondition

###### ATTRIBUTE
| name  | type   | multiplicity |
|-------|--------|--------------|
| text  | string | ?            |
| limit | int    | ?            |
```

The normalized input type is `NotificationSearchCondition`. Its input kind is
still inferred from the enclosing operation.

The current nested inline form remains compatibility syntax:

```cml
##### INPUT

###### VALUE

####### NotificationSearchCondition

######## ATTRIBUTE
...
```

An existing `EXTENDS CommandAction` or `EXTENDS QueryAction` declaration in
that compatibility form is validated against the enclosing operation kind.
The canonical local form does not require it.

## 6. Input Reference and Empty Input

`INPUT TYPE` references an existing local, top-level, compatibility, or known
built-in input type. It must not be combined with a local `VALUE` or
`ATTRIBUTE` definition. The equivalent operation-level direct `input`
property is subject to the same rule, even when its value matches the local
type name.

An operation with no input uses the existing reserved pseudo-type explicitly:

```cml
##### INPUT

###### TYPE
void
```

An empty `INPUT` section does not implicitly mean `void`.

## 7. Anonymous Local Output Result

An `ATTRIBUTE` section directly below `OUTPUT` defines an anonymous local
Result Value:

```cml
##### OUTPUT

###### ATTRIBUTE
| name          | type             | multiplicity |
|---------------|------------------|--------------|
| notifications | NotificationList | 1            |
| total         | int              | 1            |
```

The normalized output type is `<PascalOperationName>Result`. For
`searchNotifications`, it is `SearchNotificationsResult`.

The generated local Value implicitly has `OperationResult` semantics. Authors
do not need to repeat `EXTENDS OperationResult` in the canonical local form.
This semantic role is expressed by the normalized operation output contract
and `resultFields`; it does not require an additional Scala inheritance marker
on the generated record class.

## 8. Named Local Output Result

`VALUE` below `OUTPUT` supplies an explicit local Result name:

```cml
##### OUTPUT

###### VALUE
NotificationPage

###### ATTRIBUTE
| name          | type             | multiplicity |
|---------------|------------------|--------------|
| notifications | NotificationList | 1            |
| total         | int              | 1            |
```

The current nested inline `OUTPUT/VALUE/<ValueName>` form remains compatibility
syntax. An explicit `EXTENDS OperationResult` remains accepted but is redundant
in the canonical local form.

## 9. Referenced and Predefined Results

`OUTPUT TYPE` may reference:

1. a top-level Result Value;
2. an operation-local Result Value;
3. a Result in the CML/CNCF predefined Result catalog.

Examples:

```cml
##### OUTPUT

###### TYPE
UnitResult
```

```cml
##### OUTPUT

###### TYPE
IntResult
```

The minimum Phase 16 predefined catalog contract is:

| Result type | Payload contract |
|-------------|------------------|
| `OperationResult` | base Result contract |
| `UnitResult` | no value payload |
| `IntResult` | `value: int` |

Additional types such as `BooleanResult`, `LongResult`, `DecimalResult`, or
`StringResult` are recognized only when the target CNCF catalog provides them.
The grammar does not maintain an independent open-ended list.

Predefined Results are Result objects, not aliases for raw Scala return types.
Consequently, raw `int`, `string`, or other scalar datatypes are not valid
operation output types.

CNCF owns runtime classes, serialization, and payload schemas. Cozy owns CML
resolution, validation, generation mapping, and emitted operation metadata.

## 10. Compatibility Grammar

Top-level input compatibility sections remain accepted:

```text
# COMMAND -> # VALUE with input-kind=COMMAND
# QUERY   -> # VALUE with input-kind=QUERY
```

They are read-compatible but are not emitted by new scaffold output.

The previous top-level `# VALUE` form with `EXTENDS CommandAction` or
`EXTENDS QueryAction` is also read-compatible. The `extends` metadata is read
from the same AST property map and lowered to the canonical input kind. New
source uses `input-kind`; compatibility `EXTENDS` does not override an explicit
`input-kind`.

The existing operation `PARAMETER` convenience syntax remains accepted and
normalizes to an anonymous local input Value named
`<OperationName>Command` or `<OperationName>Query`. The former
`<OperationName>Input` name existed only in the top-level OPERATION parser;
Cozy rejects top-level OPERATION as a generation source, so this normalization
does not change an accepted CAR generation ABI.

Existing direct operation properties such as `input`, `output`, and `result`
continue to represent type references. Existing named inline Value trees remain
accepted.

## 11. Name and Scope Rules

Automatic names use the operation name converted to PascalCase, followed by the
required suffix:

- command input: `Command`
- query input: `Query`
- output: `Result`

Local means declaration and source lookup are owned by the operation. The
current normalized model retains that ownership through the service operation
but projects the Value through its deterministic simple type name. The Scala
generator writes local Values into the component's shared `value` package. Two
local definitions that project to the same Scala type name, or a local and
top-level Value with the same name, are therefore rejected deterministically
instead of being merged or renamed. A future nested generator may introduce a
fully qualified local identity without changing the accepted source grammar.

If two source names normalize to the same generated identity, Cozy reports a
deterministic model error. It does not silently append numeric suffixes because
that would change the operation ABI.

## 12. Normalization

All accepted forms normalize before generation to an operation model containing
at least:

```text
NormalizedOperation(
  name,
  kind,
  inputType,
  inputValueKind,
  outputType,
  parameters,
  resultFields
)
```

Normalization rules:

1. `INPUT TYPE` keeps the referenced type after kind validation.
2. anonymous local input creates the deterministic command/query type.
3. named local input keeps the explicit local name.
4. anonymous local output creates `<PascalOperationName>Result`.
5. named local output keeps the explicit local name.
6. predefined output keeps its catalog type name and resolves catalog fields.
7. legacy sections lower to the same model before generator logic runs.

The generator boundary continues to emit `COMMAND_VALUE` or `QUERY_VALUE` in
`inputValueKind`. For predefined Results, `resultFields` follows the catalog
schema; `UnitResult` has no result fields and `IntResult` has `value: int`.

## 13. Required Validation

Cozy rejects:

- a `COMMAND` operation using a query input Value;
- a `QUERY` operation using a command input Value;
- a reusable top-level input Value without `input-kind`;
- an unsupported `input-kind` value;
- an `input-kind` declaration on an operation-local input Value;
- `INPUT TYPE` combined with a local input definition;
- `OUTPUT TYPE` combined with a local output definition;
- an empty `INPUT` or `OUTPUT` section;
- an undefined non-`void` input type;
- an undefined output type;
- a scalar output type that is not a Result;
- a predefined Result name absent from the selected CNCF catalog;
- incompatible legacy `EXTENDS CommandAction` or `QueryAction` metadata;
- duplicate or colliding normalized local identities.

Diagnostics include the owning component when it is uniquely available, the
service and operation, source location, direction (`INPUT` or `OUTPUT`), and
source type name. Kind-validation diagnostics additionally include the
expected and actual kind.

## 14. Scaffold and Migration Contract

New Cozy CML scaffolds:

- emit top-level `# VALUE` plus `input-kind` for reusable inputs;
- use local `INPUT/ATTRIBUTE` and `OUTPUT/ATTRIBUTE` for one-use Values;
- use a predefined Result for simple outputs;
- do not emit top-level `# COMMAND` or `# QUERY`;
- do not emit redundant local `EXTENDS CommandAction`, `QueryAction`, or
  `OperationResult` declarations.

After Cozy implements the grammar, representative CAR migration begins with
`textus-user-notification`. `textus-user-account` then validates the same
contract against a larger identity-domain operation surface and existing
literate contract metadata. Each migration must preserve generated operation
behavior and ABI unless an intentional ABI change is recorded.

The two drivers have different responsibilities:

- `textus-user-notification` proves canonical source migration, normalized
  command/query metadata, and generated API/ABI stability;
- `textus-user-account` proves that the contract scales across user and
  management services and does not discard use-case, precondition,
  postcondition, rule, or scenario metadata.

## 15. Executable Specification Matrix

Phase 16 executable specifications cover at least:

- top-level command/query `# VALUE` acceptance;
- operation/input kind mismatch rejection;
- anonymous and named local input normalization;
- anonymous and named local output normalization;
- `void` input and `UnitResult` output;
- `IntResult` field metadata;
- undefined and scalar output rejection;
- legacy top-level `# COMMAND` and `# QUERY` compatibility;
- legacy named inline Value compatibility;
- `PARAMETER` compatibility and generated-name migration diagnostics;
- deterministic scaffold output;
- notification CAR migration and generated ABI comparison;
- account CAR migration or compatibility validation, generated ABI comparison,
  and literate metadata regression.
