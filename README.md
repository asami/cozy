# Cozy

Cozy is an open-source **code generation engine** for the  
SimpleModeling ecosystem.  
It transforms models written in **CML (Cozy Modeling Language)** into  
executable, strongly-typed source code — currently focusing on **Scala**.

Cozy is a key component of **Literate Model-Driven Development (LMDD)**,  
integrating documentation, domain models, and code generation into a unified,  
knowledge-centric workflow.

---

## Key Features

### 🧩 1. Code Generation from CML
Cozy currently focuses on generating high-quality **Scala** source code  
from SimpleModeling’s CML model definitions.

Features include:
- Domain-aware model extraction  
- Data model and type definitions  
- Idiomatic Scala code generation  
- Clear, maintainable code output suitable for real-world projects

---

### 📝 2. Designed for Literate Model-Driven Development (LMDD)
Cozy pairs naturally with **SmartDox**, which extracts structured  
representations (XML / JSON / JSON-LD) from literate documents.

Future versions will allow Cozy to consume these representations directly.

---

## JavaScript / TypeScript Ecosystem Integration (Future Direction)

While Cozy does **not** currently generate TypeScript, the long-term design  
anticipates integration with the JavaScript ecosystem through **Scala.js**.

Planned directions include:

- Generating Scala.js-compatible models  
- Producing JavaScript output via Scala.js  
- Providing optional TypeScript glue declarations  
  (e.g., `.d.ts` files for interoperability)  
- Integration with modern JS/TS build tools  
- Generating API bindings or data-model stubs for TypeScript applications

This approach keeps Scala as the source of truth while enabling seamless  
interaction with JS/TS environments.

---

## Vision

Cozy’s long-term direction goes beyond code generation.

The project aims to evolve into a **Knowledge Worker’s Workbench** for the  
SimpleModeling ecosystem — an environment where:

- documentation  
- domain models  
- semantic knowledge graphs  
- generated code  
- and AI-assisted workflows  

all work together.

Planned areas of exploration:

- Direct consumption of SmartDox XML/JSON/JSON-LD  
- Semantic reasoning via RDF/ontology  
- AI-assisted model refinement  
- Bidirectional editing between documents and code  
- Tooling support for knowledge workers (designers, modelers, developers)

This vision is long-term and exploratory.  
Current releases emphasize **stable Scala code generation from CML**.

---

## Repository Structure

```
cozy/
  ├─ src/               # Scala implementation
  ├─ docs/              # Documentation & specifications (CC-BY-SA)
  ├─ examples/          # CML models and generated code examples
  ├─ LICENSE            # Apache 2.0 for software
  ├─ DOC_LICENSE.md     # CC-BY-SA-4.0 for documentation
  ├─ CLA.md             # Contributor License Agreement
  ├─ NOTICE             # Apache NOTICE file
  └─ README.md
```

---

## Installation

Add Cozy to your `build.sbt`:

```scala
libraryDependencies += "org.simplemodeling" %% "cozy" % "<version>"
```

CLI tooling will be provided in future releases.

---

## Example Usage

### Generate Scala code from CML
```bash
cozy generate --input model.cml --out src/generated
```

### Scaffold a CAR project

`cozy init component` and `cozy car-sbt-project` generate CAR-local build
metadata in `project.yaml`. The generated project uses Scala 3.3.8 and records
exact compile/test dependencies separately from CNCF runtime compatibility:

```yaml
build:
  scalaVersion: "3.3.8"
  dependencies:
    compile:
      - "org.goldenport::goldenport-cncf:<development-version>"

packaging:
  car:
    runtime:
      cncf:
        minimum: "<required-version>"
```

The generated `build.sbt` reads identity and build settings from this file.
`project/ProjectYamlBuild.scala` performs dependency-coordinate conversion, and
`sbt-cozy` owns standard CAR `publish` and `publishLocal` delegation.
The scaffold does not write a source `component-descriptor.json`; CAR packaging
derives that descriptor from the current `project.yaml` identity so a version
change has only one metadata source to update.

### Publish CAR/SAR artifacts

Cozy `publish-car` and `publish-sar` accept any warehouse root and write the
standard repository layout below it:

```text
<warehouse>/repository/car/<artifact>/<version>/<artifact>-<version>.car
<warehouse>/repository/sar/<artifact>/<version>/<artifact>-<version>.sar
<warehouse>/repository/catalog/car/<artifact>.yaml
<warehouse>/repository/catalog/sar/<artifact>.yaml
```

CAR publication also writes the component specification sidecars:

```text
<warehouse>/repository/catalog/car/<artifact>.cml
<warehouse>/repository/catalog/car/<artifact>.model-metadata.json
<warehouse>/repository/catalog/car/<artifact>.model-metadata.yaml
```

Cozy resolves the CAR CML source from `cml.source` in `project.yaml`, then
`src/main/cozy/<artifact>.cml`, then a single `.cml` file under
`src/main/cozy`. A missing or ambiguous source fails publication before the
warehouse is changed. Use an explicit source when a project has multiple CML
files or intentionally uses a noncanonical file name:

```yaml
cml:
  source: src/main/cozy/ai.cml
```

The `--name` passed to `publish-car` must match `project.name` (or the legacy
top-level `name`). Cozy rejects mismatched artifact identities before changing
the warehouse.

For local CNCF development, use the sbt-cozy tasks `cozyPublishLocalCar` and
`cozyPublishLocalSar`. They call these Cozy commands with `~/.cncf/local`
as the warehouse root. Cozy itself does not provide separate
`publish-local-car` or `publish-local-sar` commands.

### Use Cozy programmatically
```scala
import org.simplemodeling.cozy.*

val model = CmlParser.parseFile("model.cml")
val scalaSources = ScalaGenerator.generate(model)
```

---

## Cozy in the SimpleModeling Workflow

```
SmartDox Document (text)
        │
        ▼
Structured Extraction (XML/JSON/JSON-LD)
        │
        ▼
Cozy (code generator)
        │
        └─ Scala sources (JVM or Scala.js)
```

Future extensions aim to integrate Cozy more deeply with SmartDox  
and the SimpleModeling knowledge ecosystem.

---

## License

Cozy is released under:

- **Apache License 2.0** — software  
- **CC-BY-SA 4.0** — documentation  

© 2025 ASAMI, Tomoharu / SimpleModeling.org  
Maintained by Asami Office Inc. ((有)浅海智晴事務所)

---

## Contributing

Contributions are welcome!

Please review:

- `CONTRIBUTING.md`  
- `CLA.md`

Submitting a Pull Request implies agreement to the Contributor License Agreement.

---

## Contact

Cozy is developed and maintained by:

**ASAMI, Tomoharu**  
https://www.simplemodeling.org  
info@simplemodeling.org
