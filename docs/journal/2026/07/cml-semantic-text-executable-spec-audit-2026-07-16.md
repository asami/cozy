# CML Semantic Text Executable Specification Audit

Date: 2026-07-16

## Purpose

Close the Phase 16 executable-specification checklist item for semantic text
classification, predefined text types, boundary lengths, and invalid values by
mapping each requirement to the executable specification that proves it.

The audit does not add another aggregate specification. The required behavior
is already exercised at the layer that owns each contract, and duplicating it
in one repository would weaken ownership rather than add coverage.

## Evidence Map

### Classification Diagnostics

- `cozy.lint.CozyCmlLintSpec` executes CML lint against normalized Kaleidox AST
  models.
- It rejects raw Entity strings and redundant wrappers around predefined
  scalars, warns about unconstrained nominal string wrappers, and accepts
  constrained domain scalars.
- `cozy.modeler.CmlModelInspectionSpec` verifies that declaration kinds,
  normalized constraints, predefined-type suggestions, and source locations
  are retained without reparsing CML text.

### Predefined Text Types

- `cozy.modeler.PredefinedScalarCatalogSpec` verifies canonical runtime types,
  localization, attribute-group ownership, aliases, and default ranges.
- `cozy.modeler.PredefinedScalarGenerationSpec` generates Scala 3.3.8 sources
  and verifies that predefined scalar types and their constraints are projected
  into generated entity and schema contracts.
- The datatype specs in `simplemodeling-lib`, including `I18nTitleSpec`,
  `I18nLabelSpec`, `I18nBriefSpec`, `I18nSummarySpec`,
  `I18nDescriptionSpec`, and `I18nTextSpec`, execute plain and structured
  locale-aware construction and round-trip behavior.

### Boundary Lengths and Invalid Values

- `org.goldenport.datatype.TextSpec` executes current-runtime minimum/maximum
  boundary handling and rejects overlong or control-character input.
- `org.simplemodeling.SimpleModeler.transformers.scala.ValueScalaModelTransformerSpec`
  verifies canonical `min_length` and `max_length` generation and keeps numeric
  `min` and `max` separate.
- The `cozy/text-constraint-runtime` scripted fixture compiles generated Scala
  3.3.8 and executes `GeneratedTextConstraintSpec` against Create/Update locale
  entries and `DescriptiveAttributes` values, including valid boundaries and
  invalid input.

## Decision

The Phase 16 executable-specification requirement is complete. Future text
policy decisions, including locale policy, predefined-family default ranges,
and secret redaction, remain separate open design items and must extend their
own executable specifications when accepted.
