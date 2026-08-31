# Logical UI Model Direction

Date: 2026-09-01

Status: design consideration record; non-normative

## Context

This record isolates the Logical UI discussion from the broader CML/CNCF/
Flutter Application Project direction.  The immediate objective is not Flutter
generation.  It is to establish an accepted, platform-neutral Logical UI Model
that connects use-case intent to the public vocabulary of CNCF Components and
can be inspected through one deterministic review HTML.

The later target-generation direction is:

```text
Accepted Logical UI Model + versioned Policy
  -> deterministic generated base
  + typed completion work
```

That later step must consume rather than redefine the Logical UI authority.

## Core proposition

Use cases select the Component model elements required by the UI.  The selected
elements are organized into purpose-bearing logical screens, reviewed, and
accepted as one Logical UI Model:

```text
Component public vocabulary
          +
Business UseCase -> System UseCase -> UI UseCase
          ↓
Use-case-driven model selection
          ↓
Logical UI Model candidate
          ↓
Logical UI Review HTML and feedback
          ↓
Accepted Logical UI Model
```

The Logical UI Model does not create a parallel domain model.  It records
which public Component identities are selected, why each is required by a UI
UseCase, and how they are composed into UI meaning.

## Authority boundaries

| Authority | Responsibility |
| --- | --- |
| Component model | Domain and system meaning: Entity, Aggregate, Service/Operation, Value, Datatype, Powertype, StateMachine, DbC, and their public identities. |
| Business UseCase | Channel-independent business actor, goal, scenario, policy, and outcome. |
| System UseCase | System-bound capability, input/output, Operations, View requirements, DbC, state transitions, Workflow, authorization, errors, and observability. |
| UI UseCase | Interactive realization of System UseCases: input, observation, invocation, alternatives, feedback, retry, and navigation intent. |
| Logical UI Model | Accepted UI semantics: screen purpose, logical composition, Component bindings, interaction, variants, navigation, and use-case coverage. |
| Logical UI Review HTML | Deterministic read-only projection for inspection; never writable semantic authority. |
| Policy | Later realization decisions such as target, standard layout, feedback timing, responsive behavior, and implementation binding. |

Generated HTML, route tables, Flutter code, and other target artifacts must not
be scraped or edited as substitutes for these authorities.

## Use-case hierarchy

The required hierarchy is:

```text
Business UseCase
  what the business intends to achieve
        -> realized-by
System UseCase
  what the system provides independent of UI channel
        -> presented-by
UI UseCase
  how a user performs and observes it through an interactive UI
        -> projected-to
Logical Screen Interaction
```

These are realization and projection relationships, not class inheritance and
not necessarily one-to-one mappings.

Business UseCase must not contain screens or endpoints.  System UseCase is the
main bridge to CML/CNCF Operations, Views, DbC, StateMachine, and Workflow.  UI
UseCase owns interaction steps but does not redefine business state or domain
Operations.  UI-only activities such as paging, sorting, tab selection,
temporary-input discard, and local retry remain Interaction Tasks rather than
being promoted to Business UseCases.

## Component vocabulary used by Logical UI

The Logical UI Model describes the UI by referencing the vocabulary exported
by Components:

```text
Entity
Aggregate
Service / Operation
Value
Datatype
Powertype
StateMachine
```

References must use the current public Component FQN contract and bind the
selected Component/version identity.  A short model name alone is insufficient
because another Component may export the same local name.  Internal or
non-exported vocabulary must never become UI-visible merely because it is
discoverable in source code.

The UI model adds only a thin relation vocabulary:

- `subject-of`;
- `displays` and `edits`;
- `selects`;
- `invokes` and `observes`;
- `classified-by` and `variant-for`;
- `observes-state`, `available-in`, and `invokes-transition`;
- `guarded-by`;
- `navigates-to`; and
- `reports`.

### Entity

Entity supplies identifiable collection, search, selection, reference, and
detail subjects.  It does not imply that every Entity receives a screen.
Internal Entities and Aggregate-owned parts with no independent public UI
contract remain unexposed.

### Aggregate

Aggregate is the principal consistency and mutation boundary for logical
screens.  Its root is normally the screen subject, its parts become logical
regions or child collections, its public commands become candidate actions,
and its StateMachine supplies lifecycle meaning.  A child Entity must not be
updated directly when the public Aggregate Operation is authoritative.

### Service and Operation

Service is a system-capability boundary; Operation is the executable target of
an interaction.  The UI UseCase selects only the Operations it requires.  An
Operation becomes an action candidate from structured semantics such as query,
command, state transition, asynchronous job, or Workflow step, never by
guessing from its name.

An Operation binding may contribute input, output, idempotency, authorization,
DbC, Cause/DetailCode, StateMachine transition, and observability identities.
A public Operation does not automatically become a button.

### Value

Value is a semantic display/edit unit rather than only a set of primitive
fields.  Money, Address, Period, PersonName, and GeoPoint can select standard
logical presentation or editing compositions while retaining their Component
identity.

### Datatype

Datatype supplies display/input semantics and machine-projectable constraints.
Length, pattern, range, precision/scale, format, enumeration, normalization,
and related facets can be reused without separately rewriting validation rules
in the UI model.

### Powertype

Powertype supplies semantic classification rather than an arbitrary string or
widget option list.  It can drive type labels, selection, classified
collections, type-specific logical regions, type-specific actions, and
Logical Screen variants.  Powertype remains distinct from a Datatype enum.

### StateMachine

StateMachine supplies lifecycle state, transition meaning, and candidate
action availability.  It can drive state badges, lifecycle projections,
state-specific regions, and transition interactions.  A transition is exposed
as a UI action only when a public Operation and UI UseCase binding also admit
it.  Server-side state and transition validation remains authoritative.

## UseCase-to-Screen Projection

UI UseCases and screens remain separate models joined by an explicit
UseCase-to-Screen Projection.  It must support:

- one UI UseCase Step mapped to one or more screen interactions;
- multiple Steps handled by one screen;
- one screen reused by several UI UseCases;
- system-only Steps with no screen;
- alternative and exception paths; and
- entry, input, query, selection, invocation, observation, feedback, and
  navigation interactions.

Provisional shape:

```yaml
schema: cozy.usecase-screen-projection.v1
uiUseCase: web-confirm-sales-order
mappings:
  - step: search-order
    screen: order-list
    interaction: search
  - step: inspect-order
    screen: order-detail
    interaction: observe
  - step: request-confirmation
    screen: order-detail
    interaction: invoke
    action: confirm-order
  - step: observe-result
    screen: order-detail
    interaction: observe
    variant: confirmed
```

Every accepted Logical Screen and interaction should retain the UI UseCase Step
identities it satisfies.  This makes missing coverage and unnecessary UI
surface mechanically visible.

## Logical UI Model

The Logical UI Model is an application-level accepted envelope containing:

```text
Logical UI Model
  ├─ application and consumed Component identities
  ├─ use-case realization and coverage
  ├─ logical navigation
  ├─ Logical Screen Set
  │    └─ Logical Screen Composition
  ├─ Component model bindings
  ├─ Screen Purpose selections
  ├─ Display Pattern selections
  ├─ Interaction Pattern selections
  ├─ Powertype variants
  ├─ StateMachine variants
  ├─ validation bindings
  └─ currentness and diagnostic evidence
```

It should have separate candidate and accepted identities.  AI- or
rule-generated candidates may be revised through feedback.  Only the accepted
identity can become an input to target Policy and code generation.

## Logical Screen Composition

A Logical Screen defines what a screen means, not Flutter widgets, CSS, pixel
coordinates, or URL syntax.  It contains:

- screen identity and purpose;
- subject Entity, Aggregate, or admitted read projection;
- semantic regions and containment/order relations;
- displayed or edited Component elements;
- Service/Operation actions;
- Powertype and StateMachine variants;
- logical navigation;
- normal, loading, empty, unavailable, validation-failed, conflict, and
  operation-failed states; and
- UI UseCase coverage.

Provisional example:

```yaml
logicalUi:
  schema: cozy.logical-ui.v1
  application: sales-order-management
  screens:
    - id: order-detail
      satisfies:
        uiUseCase: web-confirm-sales-order
        steps: [inspect-order, request-confirmation, observe-result]
      purpose:
        primary: inspect
        secondary: [progress]
      subject:
        kind: aggregate
        ref: sales::SalesOrder
      classification:
        powertype: sales::OrderKind
      lifecycle:
        stateMachine: sales::OrderStatus
      display:
        pattern: entity-detail
        elements:
          - sales::SalesOrder.orderNumber
          - sales::SalesOrder.customer
          - sales::SalesOrder.totalAmount
          - sales::SalesOrder.status
      interactions:
        - pattern: confirm-and-invoke
          operation: sales::SalesOrderService.confirm
          transition: sales::OrderStatus.confirm
```

The exact FQN spelling in the normative contract must follow the current
Component FQN specification; the compact `sales::` notation above is only an
illustrative placeholder.

## Pattern catalogs

Purpose, display, and interaction must remain three orthogonal closed catalogs
rather than one opaque screen template.

### Screen Purpose Pattern

Initial candidates include:

- `orient`, `find`, `select`, `inspect`, `create`, `edit`, and `progress`;
- `review`, `approve`, and `compare`; and
- `monitor` and `configure`.

A screen should normally have one primary purpose and may have explicit
secondary purposes.

### Display Pattern

Initial candidates include:

- `collection-list`, `collection-table`, and `card-collection`;
- `entity-detail`, `master-detail`, and `related-collection`;
- `input-form` and `step-form`;
- `summary-dashboard`, `status-timeline`, and `activity-feed`;
- `comparison`, `hierarchy`, and `matrix`; and
- `empty-state` and `result-summary`.

Each pattern declares required semantic regions.  It does not name a target
widget class.

### Interaction Pattern

Initial candidates include:

- `search-and-refine`, `filter-sort-page`, and `select-and-navigate`;
- `drill-down`, `form-edit`, `validate-and-submit`, and `cancel-edit`;
- `invoke-command`, `confirm-and-invoke`, and `approve-or-reject`;
- `bulk-select-and-invoke` and `progressive-disclosure`; and
- `refresh`, `retry`, `undo`, and `save-draft`.

An interaction pattern records trigger, inputs, Operation, availability,
running/success/failure feedback, and result observation.  Screen Archetypes
may recommend common combinations but do not replace the three selections as
semantic authority.

## Validation projection

CML object-model definitions should be reused directly as validation meaning:

```text
CML Object Model
  Datatype / Value / Attribute / Multiplicity
  Entity / Aggregate / Operation / DbC
                 ↓
Normalized Constraint Model
                 ↓
Logical UI Validation Binding
                 ↓
Policy timing and presentation
```

Validation sources include:

| Source | Logical UI use |
| --- | --- |
| Datatype constraint | Field-level format, length, range, precision, and normalization. |
| Attribute multiplicity | Required/optional and collection cardinality. |
| Value constraint | Multi-field semantic validation. |
| Entity constraint | Entity editor validation. |
| Aggregate invariant | Aggregate form and action validation. |
| Operation input/DbC | Submit validation and precondition feedback. |
| Powertype | Selection and type-variant validation. |
| StateMachine guard | Action availability and transition feedback. |

Constraints must be classified as:

- `local-deterministic`, safe to project exactly into a client validator;
- `context-dependent`, requiring a bound current View/Aggregate/type/state
  identity; or
- `server-authoritative`, including uniqueness, authorization, concurrency,
  cross-Aggregate invariants, Workflow readiness, external-service checks, and
  other current runtime facts.

Client validation is guidance and immediate feedback.  CNCF Operation execution
must revalidate authoritative constraints.  Client and server errors should
share stable Constraint identity, model path, structured code/DetailCode, and
parameters; localized message text is not an identity.

UI Policy decides validation timing and presentation, such as change, blur, or
submit timing and inline, region-summary, form-summary, retry-panel, or
notification display.  Policy must not change the truth condition defined by
CML.

## Distinct state dimensions

The model must not collapse:

1. domain Entity/Aggregate StateMachine state;
2. CNCF Workflow state across Operations or services; and
3. UI interaction state such as route, draft, loading, success, and failure.

Logical UI connects these dimensions through references and feedback behavior.
It does not create a client-owned duplicate business state machine.

## Candidate construction and acceptance

The candidate workflow should be:

```text
1. Read Business, System, and UI UseCases.
2. Resolve required public Component FQNs.
3. Select the Component elements needed by each UI UseCase Step.
4. Form screen candidates and logical navigation.
5. Select Purpose, Display, and Interaction Pattern candidates.
6. Add Powertype, StateMachine, and validation bindings.
7. Diagnose gaps, overexposure, and incompatible combinations.
8. Generate the Logical UI Review HTML.
9. Reflect accepted semantic feedback into the candidate.
10. Freeze one accepted Logical UI Model identity.
```

The process generates candidates, not automatically accepted UI.  It must not
expose every Entity or public Operation, bypass Aggregate boundaries, infer an
Operation kind solely from its name, or invent missing domain semantics.

## Logical UI Review HTML

The first delivery target is one deterministic, self-contained review HTML
that exposes:

- Business Actor/Goal -> Business UseCase;
- Business UseCase -> System UseCase -> UI UseCase;
- UI UseCase Step -> logical screen interaction;
- navigation and screen reachability;
- each screen's purpose and semantic regions;
- Entity/Aggregate/Value/Datatype bindings;
- Powertype classification and variants;
- StateMachine lifecycle, transition, and action availability;
- Service/Operation bindings;
- validation and structured-feedback bindings;
- selected Purpose, Display, and Interaction Patterns; and
- exact consumed identities and currentness.

Required diagnostics should include:

- missing use-case realization or UI Step coverage;
- unreachable or unused screens;
- internal/non-exported Component references;
- display elements without an admissible source;
- actions without a public Operation;
- direct child-Entity mutation that bypasses an Aggregate operation;
- StateMachine transitions without Operation/UI UseCase admission;
- invalid or unreachable Powertype/state variants;
- missing failure, conflict, DbC, or service-unavailable feedback; and
- stale Component, use-case, catalog, or projection identities.

The HTML is never an authoring input.  Its layout, CSS, optional interaction,
and schematic diagrams are renderer decisions below the Logical UI boundary.

## Currentness

At minimum:

- Entity/Aggregate/Value/Datatype changes stale the bound content or validation;
- Operation signature or DbC changes stale the bound interaction;
- Powertype changes stale classification and type variants;
- StateMachine changes stale lifecycle, availability, and state variants;
- System UseCase changes stale model selection and executable bindings;
- UI UseCase changes stale coverage, navigation, and screen composition;
- Pattern catalog changes stale the corresponding selection/projection; and
- review-renderer-only changes stale only the HTML evidence.

The accepted model and its review receipt must bind exact consumed identities,
not only timestamps or filenames.

## Policy and later generation

After the Logical UI Model is accepted, a versioned Policy can determine target
realization without changing UI semantics:

```text
Accepted Logical UI Model
          +
Common / organization / application / target Policy
          ↓
Target UI Plan
          ↓
Deterministic generated base
          +
typed Completion Overlay
```

Policy may select standard layout, navigation realization, responsive behavior,
validation timing, feedback presentation, target framework, and pattern-to-
implementation bindings.  It cannot change Component references, use-case
coverage, business constraints, or authoritative Operation semantics.

Generation should classify every target element as `GENERATED`,
`COMPLETION_REQUIRED`, `COMPLETED`, `UNRESOLVED`, or `UNSUPPORTED`.
Completion must occur through typed extension contracts in generator-external
paths, never by editing generator-owned outputs.  This later direction is not
part of the initial Logical UI implementation boundary.

## Initial development boundary

The first implementation should stop after Logical UI acceptance and HTML
review:

1. freeze minimal three-layer use-case references and Component-FQN bindings;
2. define Logical UI candidate and accepted identities;
3. define UseCase-to-Screen Projection;
4. define Logical Screen Composition;
5. establish small closed Purpose, Display, and Interaction catalogs;
6. bind Datatype/Value constraints, Powertype variants, and StateMachine
   lifecycle semantics;
7. generate deterministic Logical UI Review HTML and a currentness receipt; and
8. prove one representative Entity/Aggregate/Service application end to end.

Flutter, URL routing, target widgets, target state-management libraries, REST/
Form API client generation, mobile packaging, and generated binaries remain a
later phase.

## Open questions

- Which repository owns the normalized three-layer use-case identities.
- Whether Logical UI authoring is a CML extension, a Cozy DSL referencing CML,
  or two authoring fronts normalized into one IR.
- The exact current Component FQN syntax and public-surface descriptor consumed
  by Logical UI.
- The minimum closed Purpose, Display, and Interaction vocabularies for the
  first representative driver.
- Whether a read projection/View should remain a CNCF binding identity outside
  the Component vocabulary list or receive an explicit Logical UI relation.
- How Aggregate invariants and Operation DbC are normalized into portable
  Constraint identities.
- How AI-produced candidates and feedback decisions are recorded without
  making raw prompts semantic authority.
- Which project and phase own the cross-project contract kernel and which real
  application supplies the first driver.

## Development Candidate Disposition

The subsequent user decision promoted this direction to Cozy Development Item
`DEV-014` and planned Phase 43 after Phase 42.1. The provisional specification
is recorded in
`docs/notes/logical-ui-model-specification-proposal.md`. This promotion changes
planning records only; it does not start Phase 43 or alter Phase 42.1 state.

Candidate Triage: COMPLETED
Canonical ID: DEV-014
Disposition: NEW_PHASE
Strategy Record: docs/strategy/cozy-development-strategy.md#9-development-item-status
Target Phase: docs/phase/phase-43.md
Triaged On: 2026-09-01
