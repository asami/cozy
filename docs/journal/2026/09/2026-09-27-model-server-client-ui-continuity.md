# Model / Server / Client / UI Continuity

Date: 2026-09-27

Status: architectural direction

## Context

Cozy is evolving toward UI Model support while Textus Flutter Application Framework (TFAF) is being developed as the executable Flutter application-UI runtime.

The NICT Editing Studio application is the first Development Driver. Its first standard pattern is Resource List + Resource Detail, initially using fake data.

The UI Model must not be developed independently from server Operations. The target is seamless continuity from model to server and client.

## Target continuity

```text
CML / Application Model
  |-- Resource / View Model
  |-- Operation Model
  `-- UI Model
          |
          v
Cozy compilation
  |-- server-side projection/contracts where applicable
  |-- typed Flutter client
  |-- Operation <-> Resource/Action binding
  `-- TFAF configuration / selective generated Dart
          |
          v
TFAF runtime
          |
          v
Adaptive Flutter UI
```

UI Model and Operation Model are complementary:

- UI Model defines semantic presentation, interaction, presentation relationships, and adaptive intent.
- Operation Model defines how information is obtained and actions are executed.
- Binding connects UI semantics to Operation semantics without embedding REST/JSON details into the UI Model.

## Expected semantic mappings

Initial mappings to validate include:

- collection Query -> Resource List data source
- single Resource/View Query -> Resource Detail data source
- Command -> UI Action
- Job -> progress/status/result presentation
- Workflow / Continuation -> workflow/human-interaction presentation

These are starting hypotheses to be validated through executable TFAF components, not a frozen metamodel.

## Generation principle

The preferred output is configuration and typed binding, not screen-specific Dart.

```text
Operation Model + UI Model
        -> generated client/binding + TFAF configuration
        -> reusable TFAF runtime
```

Generate Dart only where the framework/configuration contract is insufficient.

Generated client/binding source must remain distinct from handwritten application source and framework source.

## Co-evolution

Cozy must evolve these abstractions together with executable TFAF behavior:

```text
Editing Studio requirements
        <-> TFAF executable contracts
        <-> UI/Operation binding model
        <-> Cozy compiler
```

The fake-data stage should use the same semantic ResourceDataSource boundary that the generated Operation-backed client will later implement. Fake-to-server replacement without UI rewrite is a primary design proof.
