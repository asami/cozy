# Logical UI Model Specification Proposal

Date: 2026-09-01

Status: specification proposal; non-normative; Phase 43 planning input

## Purpose

Define the first Cozy Logical UI boundary that turns application intent and
public CML/CNCF Component vocabulary into a reviewable, platform-neutral UI
model before Flutter or another target-framework generator is introduced.

```text
Application Core
  + public Component vocabulary
  + Business/System/UI UseCase references
      -> UseCase-to-Screen Projection
      -> Logical Screen Composition
      -> Logical UI Model candidate
      -> Logical UI Review HTML
      -> accepted Logical UI Model
```

The later generation direction is:

```text
Accepted Logical UI Model + versioned Policy
  -> Target UI Plan
  -> deterministic Flutter base + typed completion overlay
  -> Presentation Subcomponent with independent CAR
  -> Component Repository publication (product distribution complete)
  -> exact iPhone/Android artifact extraction from verified CAR
  -> separate official platform-distribution operation
```

That target-generation flow is not part of Phase 43. It is the required scope
of the subsequent Flutter generation work rather than an optional packaging
follow-up.

## Authority model

Logical UI must not create a second domain or system model.

| Authority | Responsibility |
| --- | --- |
| CML/CNCF Component | Entity, Aggregate, Service/Operation, Value, Datatype, Powertype, StateMachine, DbC, View, structured error, and observability meaning. |
| Business UseCase | Channel-independent actor, goal, scenario, policy, exception, and outcome. |
| System UseCase | System capability, inputs/outputs, Operations, Views, DbC, transitions, Workflow, authorization, errors, and observability. |
| UI UseCase | Interactive realization of a System UseCase: input, observation, invocation, alternatives, feedback, retry, and navigation intent. |
| Application Core | Selected use cases, actors, tasks, entry points, logical UI intent, and requested application deliverables. |
| Logical UI Model | Accepted screen purposes, composition, Component bindings, interaction, navigation, variants, validation, and use-case coverage. |
| Logical UI Review HTML | Deterministic read-only inspection projection; never semantic authority. |
| Policy | Later target, layout, responsive, feedback-timing, navigation, and implementation choices that do not change accepted semantics. |

Every Component reference binds the current public Component FQN and consumed
Component/version identity. A local name or source-code discovery is not
sufficient. Internal or non-exported model elements are inadmissible.

## Use-case hierarchy

```text
Business UseCase
  -> realized-by System UseCase
  -> presented-by UI UseCase
  -> projected-to Logical Screen Interaction
```

The relations are not inheritance and need not be one-to-one.

- Business UseCase must not contain screens, routes, endpoints, or widgets.
- System UseCase is the main bridge to Operations, Views, DbC, StateMachine,
  Workflow, authorization, structured errors, and observability.
- UI UseCase owns interactive steps but does not redefine business state,
  domain Operations, or server-authoritative constraints.
- Paging, sorting, tab selection, local draft discard, and similar UI-only
  behavior remains an Interaction Task rather than a Business UseCase.

Phase 43 must freeze the source, owner, and versioned identities of these
layers before implementing projection behavior.

## Application Core boundary

The minimum Application Core input contains:

- application identity and accepted source identity;
- actor and role references;
- selected Business, System, and UI UseCase identities and realization
  relations;
- application tasks and entry points;
- selected Component identities;
- logical navigation intent;
- normal, loading, empty, unavailable, validation-failed, conflict, and
  operation-failed intent; and
- candidate and accepted Logical UI Model references.

Application Core is authored and reviewed authority. Generated HTML, route
tables, target code, and cached derived state are projections.

## Component vocabulary projection

| Component concept | Logical UI use |
| --- | --- |
| Entity | Identity-bearing collection, search, selection, reference, and detail subject. |
| Aggregate | Principal consistency and mutation boundary; root, parts, public commands, and lifecycle. |
| Service/Operation | Executable capability and action target selected by a UI UseCase. |
| Value | Semantic display/edit unit such as Money, Address, or Period. |
| Datatype | Display/input meaning and projectable format, length, range, precision, and normalization constraints. |
| Powertype | Semantic classification, selection, label, region, action, and screen-variant source. |
| StateMachine | Lifecycle state, transition meaning, state-specific visibility, and candidate action availability. |
| DbC/constraint | Validation meaning and stable constraint identity shared with server execution. |
| View/read projection | Admitted observation source when the Aggregate or Entity is not the appropriate query shape. |

An Entity or Operation does not automatically receive a screen or action.
Selection is justified by UI UseCase coverage. Aggregate boundaries remain
authoritative: Logical UI must not invent a direct child-Entity mutation when
a public Aggregate Operation owns the change.

The initial thin relation vocabulary is closed and versioned. Candidate terms
are `subject-of`, `displays`, `edits`, `selects`, `invokes`, `observes`,
`classified-by`, `variant-for`, `observes-state`, `available-in`,
`invokes-transition`, `guarded-by`, `navigates-to`, and `reports`.

## UseCase-to-Screen Projection

UI UseCases and screens remain independent models joined by a versioned
projection. It supports:

- one UI UseCase Step mapped to one or more screen interactions;
- multiple Steps handled by one screen;
- one screen reused by multiple UI UseCases;
- system-only Steps with no screen;
- alternatives and exception paths; and
- entry, input, query, selection, invocation, observation, feedback, and
  navigation interactions.

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

Every accepted screen and interaction retains the UI UseCase Step identities
it satisfies. Coverage exposes both missing behavior and unnecessary surface.

## Logical UI Model and Logical Screen Composition

The application-level envelope contains:

```text
Logical UI Model
  |- application and consumed Component identities
  |- use-case realization and coverage
  |- logical navigation
  |- Logical Screen Set
  |    `- Logical Screen Composition
  |- Component model bindings
  |- Purpose, Display, and Interaction Pattern selections
  |- Powertype and StateMachine variants
  |- validation bindings
  `- currentness and diagnostic evidence
```

Candidate and accepted identities are distinct. AI- or rule-produced output is
a candidate until an explicit review decision accepts its exact identity.
Raw prompts, generated HTML, and renderer state never become authority.

A Logical Screen records what a screen means, not Flutter widgets, CSS,
coordinates, or URL syntax. It contains:

- identity and primary/secondary purpose;
- subject Entity, Aggregate, or admitted View;
- semantic regions and containment/order relations;
- displayed and edited Component elements;
- public Service/Operation actions;
- Powertype and StateMachine variants;
- logical navigation;
- normal, loading, empty, unavailable, validation-failed, conflict, and
  operation-failed states; and
- UI UseCase coverage.

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

The compact `sales::` spelling is illustrative only. Normative design must use
the current Component FQN contract without inventing a competing notation.

## Pattern catalogs

Purpose, display, and interaction are three orthogonal closed catalogs. A
Screen Archetype may recommend combinations but does not replace the semantic
selections.

Initial candidate Purpose Patterns include `orient`, `find`, `select`,
`inspect`, `create`, `edit`, `progress`, `review`, `approve`, `compare`,
`monitor`, and `configure`.

Initial candidate Display Patterns include `collection-list`,
`collection-table`, `card-collection`, `entity-detail`, `master-detail`,
`related-collection`, `input-form`, `step-form`, `summary-dashboard`,
`status-timeline`, `activity-feed`, `comparison`, `hierarchy`, `matrix`,
`empty-state`, and `result-summary`.

Initial candidate Interaction Patterns include `search-and-refine`,
`filter-sort-page`, `select-and-navigate`, `drill-down`, `form-edit`,
`validate-and-submit`, `cancel-edit`, `invoke-command`,
`confirm-and-invoke`, `approve-or-reject`, `bulk-select-and-invoke`,
`progressive-disclosure`, `refresh`, `retry`, `undo`, and `save-draft`.

Phase 43 admits only the minimum subset required by its representative driver.
The larger lists remain proposal candidates.

## Validation projection

```text
Datatype / Value / Attribute / Multiplicity
Entity / Aggregate / Operation / DbC
        -> normalized constraint identity
        -> Logical UI validation binding
        -> Policy-controlled timing and presentation
```

Each binding is classified as:

- `local-deterministic`: safe to project exactly into a client validator;
- `context-dependent`: requires a bound View, Aggregate, type, or state; or
- `server-authoritative`: uniqueness, authorization, concurrency,
  cross-Aggregate invariants, Workflow readiness, external-service checks,
  and other runtime facts.

Client validation provides immediate guidance. CNCF Operation execution must
revalidate authoritative constraints. Client and server feedback share stable
constraint identity, model path, structured code/DetailCode, and parameters;
localized text is not an identity. Policy may choose timing and presentation,
but cannot change the CML truth condition.

## State dimensions

Logical UI keeps three dimensions separate:

1. domain Entity/Aggregate StateMachine state;
2. CNCF Workflow state across Operations or services; and
3. UI interaction state such as route, draft, loading, success, and failure.

References connect them, but the client does not acquire a duplicate
authoritative business state machine. A transition becomes a UI action only
when both a public Operation and a UI UseCase admit it.

## Candidate construction and acceptance

1. Read Business, System, and UI UseCases.
2. Resolve required public Component FQNs.
3. Select Component elements for each UI UseCase Step.
4. Form screen candidates and logical navigation.
5. Select Purpose, Display, and Interaction Pattern candidates.
6. Bind constraints, Powertype variants, and StateMachine lifecycle meaning.
7. Diagnose gaps, overexposure, and incompatible combinations.
8. Generate the Logical UI Review HTML.
9. Reflect accepted semantic feedback into the candidate.
10. Freeze one accepted Logical UI Model identity.

The process must not expose all model elements by default, infer Operation kind
from a name, bypass Aggregate boundaries, or invent missing domain semantics.

## Logical UI Review HTML

The first visible output is one deterministic self-contained HTML document
showing:

- Business Actor/Goal through Business, System, and UI UseCases;
- UI UseCase Step-to-screen interaction coverage;
- navigation and screen reachability;
- each screen's purpose, semantic regions, and selected patterns;
- Entity, Aggregate, Value, Datatype, View, Powertype, and StateMachine
  bindings;
- Service/Operation actions and transition admission;
- validation and structured-feedback bindings; and
- exact consumed identities and currentness.

Diagnostics include missing coverage, unreachable/unused screens, internal or
non-exported references, display elements without an admitted source, actions
without a public Operation, Aggregate-boundary violations, transitions without
Operation/UI UseCase admission, invalid Powertype/state variants, missing
validation/conflict/DbC/service-unavailable feedback, and stale identities.

The HTML uses embedded CSS and optional local interaction only. It is
read-only, deterministic, atomic, and usable without a running Web service.

## Currentness and evidence

- Entity/Aggregate/Value/Datatype changes stale bound content or validation.
- Operation signature or DbC changes stale bound interaction.
- Powertype changes stale classification and type variants.
- StateMachine changes stale lifecycle, availability, and state variants.
- System UseCase changes stale selection and executable bindings.
- UI UseCase changes stale coverage, navigation, and screen composition.
- Catalog changes stale the corresponding selection/projection.
- Renderer-only changes stale only the HTML evidence.

The accepted model and review receipt bind exact consumed identities, catalog
versions, renderer/profile identity, and output hash. Timestamps and filenames
alone are insufficient.

## Representative acceptance driver

Phase 43 uses one repository-controlled SalesOrder fixture containing:

- a `SalesOrder` Aggregate and at least one contained element;
- a Value and constrained Datatype;
- an `OrderKind` Powertype;
- an `OrderStatus` StateMachine with an admitted transition;
- a query/View and a command Operation;
- DbC plus structured failure/observability identities;
- Business, System, and UI UseCases for finding, inspecting, and confirming an
  order; and
- list/detail navigation, validation, transition action, success, conflict,
  and service-unavailable feedback.

The fixture is acceptance evidence, not a new public sample application.

## Initial implementation boundary

Phase 43 includes the minimal Application Core/use-case references, public
Component-FQN binding, candidate/accepted Logical UI identities,
UseCase-to-Screen Projection, Logical Screen Composition, minimum pattern
catalogs, constraint/Powertype/StateMachine bindings, deterministic review
HTML, diagnostics/currentness receipt, Executable Specifications, and the
representative driver.

It excludes Flutter, target widgets, URL routing, target state-management
libraries, REST/Form API client generation, mobile/desktop packaging,
generated binaries, and autonomous semantic acceptance.

The successor Flutter boundary consumes the accepted model and must generate a
`presentation` Subcomponent as an independent Component/CAR. It owns Flutter
base/completion separation, selected platform artifact production, CAR
packaging and Component Repository publication. CAR publication completes
product distribution at the Component boundary. Extracting an iPhone/Android
artifact from that exact verified CAR and distributing it through an official
platform channel are separate downstream operations with independent
authorization and receipts; they must not rebuild or mutate the CAR. This
boundary consumes the canonical CNCF Component/Subcomponent design and
specification rather than defining a Cozy-local parent/child or CAR contract.

The successor product is a reusable Flutter application development
environment, not a single application generator. It supports a closed,
versioned application-kind profile catalog independently from a Web,
iPhone/iOS, Android, and Desktop target-platform matrix, then provides
scaffold, inspect, plan, generate, preview, test, build, package, and verify
operations. Its terminal development result is a deterministic self-contained
Presentation Subcomponent CAR.

One logical application may carry Web/iPhone/Android/Desktop variants in one
CAR when they share application identity, accepted Logical UI, release
lifecycle, and documentation. Different actors, goals, UI UseCases, authority,
or release lifecycle require distinct Presentation Subcomponent identities and
CARs. Component Repository publication, mobile official-channel distribution,
and desktop installer/channel distribution remain operations after the CAR
build/verification boundary.

The Presentation Subcomponent CAR must be independently usable as a
self-contained product package. In addition to Flutter artifacts, it carries
canonical identity/parent/role metadata, target compatibility, a digest-bound
artifact inventory, user/installation/operation/extraction/distribution
manuals, release notes, licenses and third-party credits, SBOM/security
evidence, dependencies/prerequisites, Help/MCP information, and deterministic
provenance/currentness evidence. A consumer must not need the generating
workspace or undocumented task context after acquiring the CAR. Credentials,
signing keys, store accounts, and environment-specific rollout policy remain
external authorized inputs and are never bundled as product documentation.
Acceptance must exercise a clean consumer environment with only the published
CAR and those explicitly declared external inputs; the generating workspace is
not an admissible dependency.

When run by CNCF, the Presentation Subcomponent provides an information-only
runtime surface by default. Standard Help, Manual, MCP, identity, compatibility,
provenance, artifact inventory, integrity, and distribution-guidance
projections are available subject to authorization. Flutter business or
interactive functions, embedded platform execution, deployment, and official
channel distribution are not exposed as CNCF Component Operations. Inventory
visibility does not by itself authorize protected manual or artifact content.

A Web-bearing profile adds two delivery modes from the verified CAR:

- export a portable static Web bundle plus deployment manifest for an admitted
  external Web server/CDN; and
- serve the Web bundle directly through a CNCF Component-scoped Web route.

Both modes retain exact artifact identity and do not rebuild during delivery.
CNCF-hosted mode must define app-scoped History fallback, base path, asset/MIME,
cache/service-worker, session/CSRF, and same-origin REST/Form API behavior.
External deployment is a separate operation and receipt.

When an explicit Web-exposure binding connects a Presentation Subcomponent to
its parent, the parent may publish the child's internal Web application through
its Web surface. The child owns the bundle; the parent owns selection,
composition, entry/alias exposure, and collision policy. Membership alone does
not authorize exposure. The Web UI consumes backend Operations; it does not
turn them into Presentation Subcomponent Operations.

## Decisions to freeze in Phase 43

- canonical owner and representation of three-layer use-case identities;
- first authoring front and normalized IR;
- exact Component FQN and public-surface descriptor syntax;
- minimum admitted pattern catalogs;
- explicit View/read-projection binding;
- portable normalization of Aggregate invariants and Operation DbC;
- candidate feedback and acceptance evidence; and
- exact command grammar and output/receipt schemas.
- initial application-kind profile catalog and Web/iPhone/Android/Desktop
  target-platform matrix.
- Web export manifest, CNCF-hosted SPA behavior, and parent/child Web-exposure
  binding.

These are contract-freeze tasks, not permission to expand into Flutter or a
general application generator.

## References

- `docs/journal/2026/09/2026-09-01-logical-ui-model-direction.md`
- `docs/journal/2026/09/2026-09-01-cml-cncf-flutter-application-project-direction.md`
- `docs/notes/document-project-workflow-management-specification-proposal.md`
- `docs/phase/phase-41.md`
- `docs/phase/phase-42.md`
- `docs/phase/phase-42.1.md`
