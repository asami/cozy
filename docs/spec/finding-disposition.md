# CML Finding Disposition properties

Cozy maps native CML properties to the accepted CNCF Phase 99
`FindingDispositionRepresentation.Properties` / `FindingDispositionV1.Declaration`
contract. CML is authoritative; generation never inserts decisions or reads
generated Scala back as source. CNCF owns interpretation, rule aliases, backlog,
reinspection and the distinction between accepted, deferred and corrected findings.

## Source syntax and exact binding

```text
# COMPONENT

## Orders

### PACKAGE

org.example

### SERVICE

#### OrderService

# VALUE

## SubmitOrder
- input-kind :: COMMAND

### ATTRIBUTE

| name | type | multiplicity |
|------+------|--------------|
| requestId | string | 1 |

## SubmitOrderResult

### ATTRIBUTE

| name | type | multiplicity |
|------+------|--------------|
| accepted | boolean | 1 |

# SERVICE

## OrderService

### OPERATION

#### submit

##### TYPE
COMMAND
##### INPUT
###### TYPE
SubmitOrder
##### OUTPUT
###### TYPE
SubmitOrderResult

# FINDING-DISPOSITION

## Orders

### missing-submit-followup

abiVersion = 1
rule = "car.required-operation"
finding = "missing-submit"
target = "operation:OrderService.submit"
kind = "defer"
reason = "Implement submission in the next milestone"
provenance = "issue:orders:42"
```

Each second-level heading names exactly one actual COMPONENT. Each third-level
heading is a stable source declaration identity, independent of line numbers or
list order. This source projection requires an ASCII UpperCamel component name
and dotted ASCII identifier package segments; invalid source identifiers are
diagnosed rather than used as generated paths. Properties are case-sensitive single-line `name = JSON literal`
assignments. `abiVersion` defaults to 1. `scope` defaults to
`component:<declared package>.<component name>`; an explicit scope must equal it.
All other fields shown are required. Strings retain their exact nonblank,
trimmed, control-free value. Unknown/repeated properties, malformed values,
unsupported ABI versions and kinds, absent reasons and duplicate exact finding
keys are diagnosed. Kinds are CNCF V1 `fix`, `accept`, `defer`, `configure`.

Supported targets are the actual component
`component:<declared package>.<component name>`, a declared `service:<name>`, or
declared `operation:<service name>.<operation name>` in the same CML model.
The service must also be explicitly linked by the owning component's native
SERVICE section; a service belonging to another component cannot satisfy it.
There is no prefix, wildcard, inherited or neighboring-target matching. Unknown
members in these supported categories fail; other target categories are
explicitly unsupported. Multi-component CML must group each decision under its
own explicit component heading. A declaration's key remains the exact CNCF tuple
`(rule, finding, scope, target)`; its source heading does not replace `finding`.

## Public producer API and generation

`cozy.modeler.CmlFindingDisposition.inspectCml(source)` uses the ordinary Kaleidox
CML parser. `inspect(model)` accepts its ordinary AST. The result contains source
declarations, structured diagnostic findings and per-declaration coverage.
`declaration.properties.toJson` exposes the eight exact portable CNCF fields.

Both ordinary `modeler-scala` and `modeler-scala-value` routes project valid
declarations into `<package>.finding.<Component>FindingDispositions`, under the
normal generated Scala 3 main source tree. The generated object's `properties`
retains the portable values; `declarationsC` invokes the existing CNCF
`FindingDispositionRepresentation.decodeC` and returns
`Consequence[Vector[FindingDispositionV1.Declaration]]`.

For the example, a CNCF consumer calls
`org.example.finding.OrdersFindingDispositions.declarationsC`. It supplies those
declarations to its own actual `FindingDispositionV1.interpretC` inspection.
No Scala annotation representation is required by this normalized consumer
boundary. Generated declarations do not become an independent approval record.

Generation rejects diagnosed input atomically and emits nothing for a model
without this feature. The pure-library generation target diagnoses the unsupported
CNCF runtime feature. The ordinary development coordinate remains
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`; generated consumers require the
qualified CNCF Phase 99 ABI. Source-generation tests alone do not establish that a
consumer has selected or activated that artifact.

## Findings and coverage

The inspection is specifically a **CML disposition syntax/binding** check.
Its `completed`, `unsupported`, and `failed` coverage describes that check per
source declaration. Invalid input has structured code, source declaration,
property and message fields. A valid source property is not an observed CAR
finding and does not prove that any CAR rule ran. Absent properties yield no
coverage claim. CAR lint coverage, skipped/failed CAR checks, actual change
evidence and Human admission remain with their respective external owners.

Executable specification: `cozy.modeler.CmlFindingDispositionSpec`.
