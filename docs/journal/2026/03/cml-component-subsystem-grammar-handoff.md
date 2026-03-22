CML Component/Subsystem Grammar Handoff (Phase 9)
=================================================

status=frozen
published_at=2026-03-22
owner=cozy-modeler
phase=9 (CS-01 / CS-02)

---

# 1. Scope

This handoff freezes the grammar and AST contract for:

- `COMPONENT`
- `COMPONENTLET`
- `EXTENSIONPOINT`
- `SUBSYSTEM`

It also defines deterministic behavior and invalid-case expectations.

---

# 2. Design Principles

1. `Component` is the packaging/execution unit.
2. `Subsystem` is the composition/configuration unit.
3. `Componentlet` and `ExtensionPoint` are metadata for composition/runtime selection.
4. Coordinates are explicit and validated (`group:artifact:version`).
5. Parsing and generated metadata must be deterministic.

---

# 3. Frozen Grammar

## 3.1 Top-Level Sections

Supported:

- `# COMPONENT`
- `# COMPONENTLET`
- `# EXTENSIONPOINT`
- `# SUBSYSTEM`

## 3.2 COMPONENT

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

## 3.3 COMPONENTLET

```text
# COMPONENTLET

## audit_sink
- component :: person
- kind :: infra
```

## 3.4 EXTENSIONPOINT

```text
# EXTENSIONPOINT

## observability
- component :: person
- interface :: MetricsSink
```

## 3.5 SUBSYSTEM

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

---

# 4. AST Contract

```scala
case class ComponentSubsystemModel(
  components: Vector[ComponentDefinition],
  componentlets: Vector[ComponentletDefinition],
  extensionPoints: Vector[ExtensionPointDefinition],
  subsystems: Vector[SubsystemDefinition]
)

case class Coordinate(group: String, artifact: String, version: String)

case class ComponentDefinition(
  name: String,
  coordinates: Vector[Coordinate],
  componentlets: Vector[String],
  extensionPoints: Vector[String],
  extensionBindings: Map[String, String]
)

case class SubsystemDefinition(
  name: String,
  components: Vector[Coordinate],
  extensionBindings: Map[String, String],
  config: Map[String, String]
)
```

---

# 5. Generator Contract (Cozy)

Generated `DomainComponent` includes:

- `def componentDefinitionRecords: Vector[Record]`
- `def subsystemDefinitionRecords: Vector[Record]`

Record shape:

- component: `name`, `coordinates`, `componentlets`, `extension_points`, `extension_bindings`
- subsystem: `name`, `components`, `extension_bindings`, `config`

---

# 6. Determinism Rules

1. Definitions are emitted in stable order (`name`-sorted in current implementation).
2. Duplicate names are de-duplicated by first occurrence.
3. Same input yields the same generated metadata records.

---

# 7. Invalid-Case Matrix

| Case | Expected behavior |
|---|---|
| invalid coordinate (`person-service`) | parse failure (`invalid coordinate`) |
| empty component name | parse failure |
| empty subsystem name | parse failure |
| malformed binding section | ignored unless it violates required coordinate validation |

---

# 8. Validation Commands

```bash
cd /Users/asami/src/dev2025/cozy
sbt --batch "testOnly cozy.modeler.ModelerGenerationSpec"
```

Focused subset:

```bash
cd /Users/asami/src/dev2025/cozy
sbt --batch "testOnly cozy.modeler.ModelerGenerationSpec -- -z COMPONENT/SUBSYSTEM"
```

---

# 9. Notes

- This freeze covers parser/model/generator metadata path (CS-01/CS-02).
- CAR/SAR assembly tasks are handled by `sbt-cozy` (PK-02).
- CNCF runtime intake alignment remains RT-01 scope.

---

End.
