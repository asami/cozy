# Phase 43: Logical UI Model and Review HTML

Status: IN PROGRESS; LUI43-01 Authority and Contract Kernel is complete;
LUI43-02 UseCase-to-Screen and Component Projection is complete.

Plan date: 2026-09-01

Development item: DEV-014

Dependencies:

- Phase 42.1 closure for the accepted Document Project evidence/review
  projection and driver-acceptance patterns;
- Phase 41 closure for deterministic self-contained logical review HTML;
- the current CML/CNCF public Component FQN and exported-vocabulary contract;
  and
- stable public identities for Entity, Aggregate, Service/Operation, Value,
  Datatype, Powertype, StateMachine, DbC, structured failures, and
  observability.

Phase 43 is the next Cozy development item after Phase 42.1. Planning it does
not start it or change Phase 42.1 state.

## Goal

Establish a platform-neutral Logical UI Model that projects Application Core,
Business/System/UI UseCases, and public Component vocabulary into accepted
Logical Screen Compositions; then generate one deterministic self-contained
review HTML exposing use-case coverage, navigation, Component bindings,
patterns, validation, lifecycle, feedback, diagnostics, and currentness.

## LUI43-01: Authority and Contract Kernel

Stage Status:

- Current status: DONE
- Owner: Cozy Logical UI contract
- Update rule: complete only when authority, identity, authoring-front,
  candidate/accepted, and non-authority boundaries pass focused Executable
  Specifications.

- Freeze the minimum Application Core and Business/System/UI UseCase reference
  model needed by Logical UI.
- Freeze exact public Component FQN and exported-surface binding without local
  name guessing or private-source discovery.
- Decide and version the first authoring front and normalized Logical UI IR.
- Define distinct candidate, accepted, feedback-decision, and consumed-input
  identities.
- Keep review HTML, route tables, target code, and cached state outside
  semantic authority.

## LUI43-02: UseCase-to-Screen and Component Projection

Stage Status:

- Current status: DONE
- Owner: Cozy Logical UI normalization
- Update rule: complete only when mapping cardinalities, public-surface
  admission, Aggregate boundaries, and coverage diagnostics pass focused
  Executable Specifications.

- Project UI UseCase Steps into explicit screen interactions while supporting
  one-to-many, many-to-one, reuse, system-only, alternative, and exception
  mappings.
- Define Logical Screen identity, purpose, subject, semantic regions,
  navigation, interaction, feedback states, and use-case coverage.
- Bind Entity, Aggregate, Service/Operation, Value, Datatype, View,
  Powertype, and StateMachine identities without creating a parallel domain
  model.
- Reject unexported references, unjustified surfaces, missing Operation
  bindings, and direct child mutation that bypasses an Aggregate Operation.

## Phase Hygiene Ledger

- `HYG-LUI43-02-001` (OPEN): `CozyLogicalUi.scala` is 1,221 lines and exceeds
  the repository's 1,000-line source-size threshold. Owner: Cozy Logical UI
  normalization. Target: a separately authorized hygiene batch that may add a
  source path and perform the physical split. Rationale: the split is
  structural work outside this frozen behavior repair and must preserve the
  current projection contract. This disposition makes no current behavior
  change and does not resolve the hygiene item.

## LUI43-03: Pattern, Constraint, and State Semantics

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy Logical UI semantic catalogs
- Update rule: complete only when the minimum closed vocabularies and
  constraint/state classifications pass focused Executable Specifications.

- Freeze the minimum closed Purpose, Display, and Interaction Pattern sets
  required by the representative driver.
- Project Datatype, Value, multiplicity, Aggregate invariant, Operation DbC,
  Powertype, and StateMachine meaning into typed Logical UI bindings.
- Classify validation as local-deterministic, context-dependent, or
  server-authoritative while retaining shared constraint/DetailCode identity.
- Keep domain StateMachine, CNCF Workflow, and UI interaction state distinct.
- Require both public Operation and UI UseCase admission before exposing a
  StateMachine transition as an action.

## LUI43-04: Logical UI Review HTML and Currentness

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy deterministic Logical UI review projection
- Update rule: complete only when overview/detail projection, diagnostics,
  accessibility, deterministic bytes, atomic output, and currentness receipts
  pass.

- Generate one self-contained HTML showing use-case realization, Step-to-
  screen coverage, navigation/reachability, per-screen purpose/composition,
  Component bindings, patterns, validation, lifecycle, actions, and feedback.
- Diagnose missing coverage, unused/unreachable screens, inadmissible model
  references, Aggregate-boundary violations, missing executable actions,
  invalid Powertype/state variants, and missing failure behavior.
- Bind the accepted model, exact consumed Component/use-case/catalog
  identities, renderer/profile, and output hash into a versioned receipt.
- Keep HTML/CSS/inline-SVG/optional interaction read-only and below the
  Logical UI semantic boundary.

## LUI43-05: SalesOrder Driver Acceptance and Closure

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy Logical UI acceptance
- Update rule: complete only when the representative driver, focused/full
  validation, independent Phase review, and ledgers converge.

- Accept one repository-controlled SalesOrder fixture spanning Aggregate,
  Value, Datatype, Powertype, StateMachine, View/query, command Operation,
  DbC, structured failures/observability, and all three use-case layers.
- Cover list/detail navigation, input validation, admitted transition action,
  success, conflict, and service-unavailable feedback.
- Prove deterministic repeat output, candidate-to-accepted identity, stale
  input diagnosis, and read-only review projection.
- Run focused and full serialized Cozy validation and complete one independent
  Phase review before closure.

## Exclusions

- Flutter, HTML application, or another target-framework code generation.
- Target widgets, CSS layout, URL routing, target state-management libraries,
  REST/Form API client generation, packaging, or generated binaries.
- A second domain/system model or exposure of every Component element.
- Client-owned replacement of server-authoritative validation, StateMachine,
  Workflow, authorization, or observability.
- Automatic acceptance of AI-generated candidates or raw prompts as semantic
  authority.
- Publication, deployment, upload, push, or external-driver mutation.
- Reopening Phase 41, Phase 42, or Phase 42.1 accepted contracts.

## Completion Criteria

Phase 43 completes only when one accepted Logical UI Model binds exact
Application Core, three-layer use-case, public Component, catalog, and
constraint identities; its UseCase-to-Screen Projection and Logical Screen
Compositions expose complete and non-redundant interaction coverage; Aggregate,
Powertype, StateMachine, DbC, structured failure, and observability semantics
remain correctly bounded; the SalesOrder driver covers required normal and
failure behaviors; deterministic self-contained review HTML and a currentness
receipt are reproducible; focused and full Cozy validation succeeds; and an
independent Phase review has no Current Boundary Blocker.

## Structural Phase Plan Gate

State: PROCEED

- planning demand: new public semantic IR, cross-authority identity binding,
  closed pattern vocabularies, deterministic review projection, and one
  repository-controlled acceptance driver
- parent profile: `gpt-5.6-terra / high`
- implementation profile target: Luna/xhigh for bounded implementation and
  Executable Specifications; Terra/xhigh only for the LUI43-01 contract freeze
  or a high-risk cross-CML/CNCF authority decision
- estimate: 6–8 hours
- predecessor: accepted Phase 42.1 closure
- successor: Presentation Subcomponent generation from accepted Logical UI and
  target Policy as a multi-application-kind Flutter development environment,
  including scaffold/inspect/plan/generate/preview/test/build/package/verify,
  Web/iPhone/Android/Desktop artifact build, independent CAR packaging,
  self-contained manuals/metadata/inventory/provenance resources, and
  deterministic verified CAR output as the development boundary;
  Web targets support external-server export and CNCF-hosted direct execution,
  including explicit parent-to-Presentation-Subcomponent Web exposure;
  Component Repository publication remains the product-distribution operation;
  CNCF execution always exposes authorized Help/Manual/MCP and artifact
  information, and exposes the Web UI only for an admitted Web binding, without
  creating Flutter business, platform-deployment, or official-distribution
  Presentation Subcomponent Operations;
  exact mobile artifact or desktop installer/package extraction and official
  channel distribution remain separate downstream operations under the
  successor plan; it begins
  only after Phase 43 closure and a separate Phase plan

## References

- `docs/phase/phase-43-checklist.md`
- `docs/notes/logical-ui-model-specification-proposal.md`
- `docs/journal/2026/09/2026-09-01-logical-ui-model-direction.md`
- `docs/journal/2026/09/2026-09-01-cml-cncf-flutter-application-project-direction.md`
- `docs/phase/phase-41.md`
- `docs/phase/phase-42.md`
- `docs/phase/phase-42.1.md`
