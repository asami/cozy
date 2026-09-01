# Phase 43: Logical UI Model and Review HTML

Status: COMPLETE LOCALLY; LUI43-01 Authority and Contract Kernel,
LUI43-02 UseCase-to-Screen and Component Projection, LUI43-03 Pattern,
Constraint, and State Semantics, LUI43-03W Workflow Subject and Pattern
Semantics, LUI43-04 Logical UI Review HTML and Currentness, and LUI43-05 SalesOrder
Driver Acceptance and Closure are DONE. Independent full Phase review receipt
`P43-FULL-REVIEW-001` is `PASS` with zero Current Phase Blockers. Final
serialized Cozy validation receipt `P43-FINAL-TEST-013`, invocation
`86897-20260901T171129Z`, completed successfully with 1,677 succeeded, 126
suites completed, and 0 failed/aborted; SBT and wrapper exits were 0. The local
Phase release commit binds this accepted closure; no commit SHA is recorded
before that commit exists.

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

Phase 43 follows the accepted Phase 42.1 closure and is complete locally under
this closure documentation. The local Phase release commit binds the accepted
closure; Phase 42.1 state is unchanged.

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

- `HYG-LUI43-02-001` (OPEN): `CozyLogicalUi.scala` is 1,225 lines and exceeds
  the repository's 1,000-line source-size threshold. Owner: Cozy Logical UI
  normalization. Target: a separately authorized hygiene batch that may add a
  source path and perform the physical split. Rationale: the split is
  structural work outside this frozen behavior repair and must preserve the
  current projection contract. This disposition makes no current behavior
  change and does not resolve the hygiene item.
- `HYG-LUI43-RR-001` (OPEN; separate follow-up; not a Current Boundary
  Blocker): keep review Spec fixtures under `target/` and clean them
  deterministically. This does not change LUI43-03W/LUI43-04 behavior or the
  LUI43-04 security closure.
- `HYG-LUI43-RR-002` (OPEN; separate follow-up; not a Current Boundary
  Blocker): add `which` grouping to improve review Spec navigation. This does
  not change LUI43-03W/LUI43-04 behavior or the LUI43-04 security closure.

## LUI43-03: Pattern, Constraint, and State Semantics

Stage Status:

- Current status: DONE
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

## LUI43-03W: Workflow Subject and Pattern Semantics

Stage Status:

- Current status: DONE
- Owner: Cozy Logical UI Workflow subject boundary
- Update rule: complete only when Workflow is admitted as an exact public
  Component screen subject and pattern source without expanding client-side
  Workflow authority.

- Add `Workflow` as a closed public Component role and as a Logical Screen
  pattern source peer of Entity, Aggregate, and View.
- Retain the same exact public `ComponentBinding` admission and deterministic
  canonical identity rules used by its peer screen subjects.
- Keep Workflow out of renderers, executors, local authority, client-side
  state machines, and every replacement for server Workflow, authorization,
  and observability.
- Retain separate Domain StateMachine, opaque Workflow state evidence, and UI
  interaction-state domains; Workflow does not become an Aggregate mutation
  root or a public Operation replacement.
- Completion evidence: Workflow public subject and pattern semantics are
  complete. Focused Executable Specification receipt
  `P43-LUI43-03W-TEST-002` reports 34 succeeded and 0 failed; focused closure
  review `P43-LUI43-03W-04J-RR-005` reports no Current Boundary Blocker.

## LUI43-04: Logical UI Review HTML and Currentness

Stage Status:

- Current status: DONE
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
- Approved security decision `CB-LUI43-04-RR-006`: admit an output only when
  its parent path equals the explicit parent exactly as written; reject
  intermediate traversal, including symlink-plus-`..`, before temporary output
  creation or replacement.
- Completion evidence: focused receipt `P43-LUI43-04J-TEST-007` reports 6
  succeeded and 0 failed for the direct-child escape closure;
  `P43-LUI43-03W-TEST-002` reports 34 succeeded and 0 failed for the combined
  Workflow/review behavior; focused closure review
  `P43-LUI43-03W-04J-RR-005` reports no Current Boundary Blocker.

## LUI43-05: SalesOrder Driver Acceptance and Closure

Stage Status:

- Current status: DONE
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
- Completion evidence: SalesOrder driver acceptance is implemented. Focused
  acceptance receipt `P43-LUI43-05A-FIX-TEST-012` reports 4 succeeded and 0
  failed; the independent Step review is clean; and the local Step commit is
  `de0a7877bc04fd29cb35156dd64f77a223f0fc1a`. Independent full Phase review
  receipt `P43-FULL-REVIEW-001` is `PASS` with zero Current Phase Blockers.
  Final serialized Cozy validation receipt `P43-FINAL-TEST-013`, invocation
  `86897-20260901T171129Z`, completed successfully with 1,677 succeeded, 126
  suites completed, and 0 failed/aborted; SBT and wrapper exits were 0. The
  local Phase release commit binds this accepted closure; no commit SHA is
  recorded before that commit exists.

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
