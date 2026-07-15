# CML Actor and Use Case Model

status=active-note
updated_at=2026-07-16

## Purpose

This note defines the current typed CML Actor and UseCase contract. It aligns
the executable model with the Modegramming Style UseCase series while keeping
existing component and service CML source compatible.

## Actor

`# ACTOR` is a top-level requirement-model division. Each child section defines
one actor with:

- stable model name;
- optional `KIND`, such as `human` or `system`;
- optional `SUMMARY`;
- optional `DESCRIPTION`.

Actor definitions are model elements. They are not narrative sections and must
be preserved in generated component metadata.

The public CML sidecar preserves them under
`surface.component.actors`. Each entry carries `name`, `kind`, `summary`, and
`description` so CAR repositories and BoK project knowledge do not need to
reparse CML source.

## UseCase Contract

A UseCase has stable basic information and behavioral flows.

Basic contract:

- model name and optional `ID`;
- one primary actor in canonical authoring;
- zero or more secondary, supporting, and stakeholder references;
- summary and goal;
- trigger;
- preconditions and postconditions;
- optional priority and lifecycle status.

Actor references resolve to a local Actor definition when one exists. External
systems and components remain explicit external references; they are not
silently converted into local actors.

The same contract is published under `surface.component.useCases` in
`model-metadata.json` and `model-metadata.yaml`. It includes Actor reference
roles and target kinds plus the typed flow collection.

## Flow

Canonical flow headings are:

- `MAIN FLOW`;
- `ALTERNATE FLOW`;
- `EXCEPTION FLOW`.

The compatibility heading `SCENARIO` is interpreted as a main flow. Generated
metadata records the flow kind independently of the flow name.

Canonical steps follow the Modegramming Style shape:

```text
- [step-id] Actor: action
- include OtherUseCase
- extend OtherUseCase when condition
- goto step-id
- end
```

The current implementation preserves existing step text. A later slice will
project step ID, actor, action, and directive into typed step records and will
validate flow references.

## Relationship Boundary

- `include` is mandatory inline behavior composition at an explicit step.
- `extend` is conditional behavior composition at an explicit step.
- `generalize` is contract refinement over preconditions and postconditions;
  it does not prescribe flow inheritance.

This slice does not execute or merge those relationships. It establishes the
Actor and UseCase model needed before relationship semantics are implemented.

## Compatibility

- Existing `ACTOR`, `PRIMARY ACTOR`, `SECONDARY ACTOR`, `SUPPORTING ACTOR`, and
  `STAKEHOLDER` text fields remain available in generated metadata.
- Comma- or newline-separated actor names are normalized into distinct actor
  references.
- Existing `SCENARIO` sections remain valid as main flows.
- `triger` is accepted as a compatibility spelling, but new source uses
  `TRIGGER`.

## Sources

- https://modegramming.blogspot.com/2025/08/cozy.html
- https://modegramming.blogspot.com/2025/09/cozy.html
- https://modegramming.blogspot.com/2025/10/cozy.html
- https://modegramming.blogspot.com/2025/11/cozy.html
- https://modegramming.blogspot.com/2025/12/cozyinclude.html
- https://modegramming.blogspot.com/2026/01/cozyextend.html
- https://modegramming.blogspot.com/2026/02/cozygeneralize.html
- https://modegramming.blogspot.com/2026/03/cozygeneralize-realization.html
