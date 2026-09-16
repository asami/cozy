# Capability Model IR Support Proposal

Status: exploratory
Date: 2026-09-16
Development item: DEV-032

## Purpose

Add authoring, normalization, validation, and generation support for Capability
Model IR. Capability is a non-instantiated model element, like Use Case Model.
It connects requirements and scenarios to Component architecture and executable
realizations without becoming a runtime entity or executable Operation.

Cozy owns the source-to-generated boundary. CNCF owns the externally admitted
Component Capability contract, and Textus CBD Support owns its catalog and
presentation.

## Proposed model

```text
Use Case set
  -> Application Capability

Use Case Scenario
  -> Component Capability

Application Capability
  -> realization mapping
  -> Component Capability set

Component Capability
  -> realization reference
  -> Operation / Workflow / StateMachine
```

Application Capability expresses what the Application must be able to do.
Component Capability expresses what a Component provides or requires. Neither
record contains control-flow ordering, retries, compensation, authorization,
or runtime state.

## Cozy responsibilities

- define or adopt the eventual CML syntax after the semantic contract is
  accepted;
- parse Capability declarations and references into an explicit internal IR;
- preserve stable identity, source location, description, model references,
  provided/required direction, and realization references;
- validate duplicate, missing, ambiguous, cyclic, or incompatible references;
- reject procedure/order semantics inside Capability realization declarations;
- generate the CNCF-admitted versioned Component Capability projection;
- preserve multi-CML source provenance and deterministic output identity;
- provide source-correlated diagnostics and executable specifications.

## Boundary rules

- Cozy does not define CNCF runtime meaning or cbd-support query semantics.
- Capability does not alias Operation, Workflow, Permission, Guard, endpoint,
  Skill, or Agent Tool.
- `realization` is a model mapping, not instantiation or execution.
- Availability, Authorization, and Guard remain separate models/metadata.
- Provisional keywords in SimpleModeling journals are working notation, not an
  accepted parser contract.

## Development slices

1. Consume the accepted CNCF Capability identity/projection contract.
2. Freeze Capability IR and its relation to Use Case, Component, Operation,
   Workflow, and StateMachine identities.
3. Admit CML syntax and parser/metamodel support.
4. Add semantic validation and diagnostics.
5. Generate the versioned CNCF projection with source provenance.
6. Prove a real multi-model fixture through CNCF and cbd-support consumers.

## Open design questions

- Whether Application Capability and Component Capability use one construct
  with an explicit scope or two source constructs.
- Whether `provides`, `requires`, and `realizes` are declarations or reference
  edges in the normalized IR.
- Whether input/output model references belong to Capability v1 or only to
  executable realizations.
- How Capability references participate in existing generation provenance and
  cross-file identity resolution.
