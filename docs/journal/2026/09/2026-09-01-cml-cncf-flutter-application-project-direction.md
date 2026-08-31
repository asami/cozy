# CML/CNCF/Flutter Application Project Direction

Date: 2026-09-01

Status: design consideration record; non-normative

## Context

The discussion considered how to connect CML and CNCF to a Flutter application
without treating Flutter Web as a collection of generated HTML pages or
generating UI directly from the domain model.  The desired approach follows
the design principles already used by Cozy Document Project, Content Core, and
the Explanation Structure Review HTML:

- accumulate and review a stable semantic core before producing delivery
  artifacts;
- visualize the complete logical structure without making the visualization
  semantic authority; and
- generate a reproducible application base from an accepted intermediate
  model while retaining explicit hand-written extension boundaries.

Flutter Web is expected to behave primarily as a routed SPA.  Browser-visible
routes remain first-class application identities even though they are rendered
inside one Flutter application runtime.

## Working direction

Introduce an Application Project that applies the Document Project pattern to
application development:

```text
CML Domain Model
  Entity / Value / Operation / StateMachine / DbC
            +
CML Business UseCase Model
  Business Actor / Goal / Business Scenario / Outcome
            +
CML/CNCF System UseCase Model
  System Actor / Operation / View / DbC / StateMachine / Workflow
            +
CNCF Executable Contract
  REST / Form API / Structured Error / Observability
            -> Application Core
               UI UseCase / Interaction Task
            -> UseCase-to-Screen Projection
            -> Logical Screen Composition
            -> Application Semantics IR
            -> Application Structure Review HTML
            -> Flutter base application
```

CML remains authority for domain meaning, Operations, state transitions, and
DbC.  CNCF remains authority for executable Operations, Views, ingress,
structured errors, and observability.  Application Core references those
identities and adds application intent; it must not duplicate or privately
reinterpret them.

## Application Core

Application Core is analogous to Document Project Content Core.  Its initial
responsibility should include:

- actors, roles, and user goals;
- selected Business UseCase, System UseCase, and UI UseCase identities;
- explicit `realized-by`, `realizes`, and `presented-by` relationships between
  the three use-case layers;
- application tasks and entry points;
- logical screens and navigation intent;
- screen-to-View and action-to-Operation bindings;
- StateMachine and Workflow relationships;
- normal, loading, empty, unavailable, and failure presentation intent;
- responsive and platform intent; and
- selected Flutter application deliverables.

Application Core is authored and reviewed authority.  Generated Flutter code,
review HTML, route tables, and derived workflow state are projections and must
not become writable semantic sources.

## Use case hierarchy and linkage

The working model requires three distinct use-case layers:

```text
Business UseCase
  what the business intends to achieve
        -> realized-by
System UseCase
  what the selected system provides independent of UI channel
        -> presented-by
UI UseCase
  how a user performs and observes it through an interactive UI
        -> projected-to
Logical Screen Composition
```

These arrows are realization and projection relations, not class inheritance.
The mappings are not necessarily one-to-one: one Business UseCase may require
multiple systems, one System UseCase may serve UI, REST, CLI, or batch clients,
and one logical screen may participate in several UI UseCases.

### Business UseCase

Business UseCase is independent of a particular application or delivery
channel.  It owns the business actor, goal, business precondition, scenario,
outcome, policy, and business exception.  For example,
`fulfill-customer-order` describes the business outcome
`OrderReadyForFulfillment`; it does not mention a screen, REST endpoint, or
Flutter widget.

### System UseCase

System UseCase defines the capability at the CML/CNCF system boundary.  It
realizes part or all of a Business UseCase and binds the system actor, input and
output, Entity/View identities, Operations, DbC, StateMachine transitions,
Workflow steps, authorization, structured errors, and observability.

Provisional shape:

```yaml
systemUseCase:
  id: confirm-sales-order
  realizes: fulfill-customer-order
  primaryActor: SalesClerk
  preconditions:
    - SalesOrder.status == Draft
  operations:
    - SalesOrderQuery.find
    - SalesOrderService.confirm
  postconditions:
    - SalesOrder.status == Confirmed
  transition:
    stateMachine: OrderStatus
    event: confirm
```

The same System UseCase remains valid when invoked from Flutter, another Web
client, CLI, an external REST client, or an admitted automated process.

### UI UseCase

UI UseCase presents one or more System UseCases through an interactive channel.
It belongs to Application Core and owns UI-meaningful interaction steps,
required input and observation, alternatives, validation feedback, failure and
retry behavior, and navigation intent.  It does not own domain Operations or
business state.

Provisional shape:

```yaml
uiUseCase:
  id: web-confirm-sales-order
  realizes: confirm-sales-order
  channel: interactive-web
  actor: SalesClerk
  steps:
    - id: search-order
      kind: query
      systemUseCaseInteraction: find-order
    - id: inspect-order
      kind: observe
      view: SalesOrderDetail
    - id: request-confirmation
      kind: invoke
      operation: SalesOrderService.confirm
    - id: observe-result
      kind: observe
      expectedState: Confirmed
  alternatives:
    - condition: validation-failed
      feedback: field-and-summary
    - condition: concurrent-update
      feedback: reload-required
```

UI-only concerns such as paging, sorting, tab selection, temporary input
discard, local preference, or retry remain `Interaction Task` values rather
than being promoted to Business UseCases.

### UseCase-to-Screen Projection

UI UseCases and screens remain separate models joined by an explicit
UseCase-to-Screen Projection.  This follows the Phase 37 Step-to-Page
Projection principle and avoids embedding presentation decisions into any of
the three use-case layers.

The projection must support:

- one UI UseCase Step projected to one or more screens;
- multiple Steps handled by one screen;
- one reusable screen participating in several UI UseCases;
- system-only steps with no screen projection;
- alternative and exception scenarios; and
- explicit entry, navigation, action, and observation interactions.

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

The primary derivation direction is:

```text
Business UseCase
  -> System UseCase
  -> UI UseCase
  -> UseCase-to-Screen Projection
  -> Logical Screen Composition
  -> Visual Structure
  -> Flutter
```

Review feedback may change Application Core or its screen projection.  A
screen-local preference must not silently rewrite a UI UseCase, System UseCase,
Business UseCase, or domain contract.  A real change returns to the appropriate
authority explicitly.

## Logical Screen Composition

Logical Screen Composition defines what a screen means, not how Flutter lays
out widgets.  It should describe:

- screen identity and purpose;
- subject Entity or View;
- semantic regions and their containment/order relationships;
- displayed information and its source;
- actions and CNCF Operation bindings;
- navigation and feedback;
- applicable StateMachine state and transition guards; and
- normal, loading, empty, not-found, validation-failed, and operation-failed
  variants.

Provisional shape:

```yaml
screen:
  id: order-detail
  purpose: inspect-and-progress-order
  subject:
    entity: SalesOrder
    view: SalesOrderDetail
  regions:
    - id: summary
      role: subject-summary
      contents: [order-number, customer, total]
    - id: lifecycle
      role: state-presentation
      stateMachine: OrderStatus
    - id: actions
      role: available-actions
      actions: [confirm-order, cancel-order]
  states: [loading, available, not-found, operation-failed]
```

Logical roles are projected separately through a Visual Pattern catalog.  For
example, `subject-summary` may become a detail header,
`state-presentation` a status stepper, and `available-actions` a bottom action
bar.  Flutter widget classes, coordinates, colors, and platform-specific layout
objects must stay below this semantic boundary.

## Distinct state models

The integration must not collapse three different kinds of state:

1. CML StateMachine state governs valid domain-object transitions.
2. CNCF Workflow state coordinates multi-Operation or cross-service work.
3. Flutter UI state governs route, draft input, loading, success, and failure
   presentation.

Application Core connects these dimensions through explicit references.  It
does not introduce a duplicate business state machine in the client.

DbC preconditions, postconditions, and invariants may be projected as client
guidance and generated validation behavior.  Server-side Operation execution
remains authoritative, and DbC failure must return through the CNCF structured
error and observability contracts.

## CNCF ingress boundary

The existing Form API and REST responsibility split should remain unchanged:

- REST v1 is the canonical JSON query/command Operation execution route;
- Form API provides Web-specific dynamic input definition, candidates, field
  ordering, and optional admission validation; and
- direct Form API execution remains compatibility-only for new applications.

Generated Flutter code should consume typed CNCF client bindings rather than
constructing endpoint strings independently.  It should also preserve
correlation identities, structured errors, authentication/CSRF policy where
applicable, and Operation observability.

## Application Structure Review HTML

Before Flutter generation, produce one deterministic, self-contained review
HTML analogous to the Phase 41 Explanation Structure Review HTML.  It should
make the following relationships visible together:

- Business Actor/Goal -> Business UseCase;
- Business UseCase -> System UseCase -> UI UseCase;
- UI UseCase Step -> Screen interaction;
- Screen -> Logical Region and state variant;
- displayed content -> CNCF View;
- action -> CNCF Operation;
- pre/postcondition -> DbC;
- Operation -> StateMachine transition or Workflow step; and
- Logical Region -> selected Visual Pattern.

The projection should detect at least:

- Business or System UseCases with no required realization mapping;
- unmapped user-visible UI UseCase Steps;
- unreachable or unused screens;
- actions without Operations;
- displayed content without Views;
- StateMachine-invalid actions;
- missing alternative/error scenario presentation;
- missing DbC feedback presentation; and
- stale CML, CNCF descriptor, projection, or visual binding identities.

The HTML is review evidence only.  It must not become an input from which CML,
Application Core, or Flutter generation semantics are scraped.

## Flutter generation boundary

The first target should generate a reproducible base application, not claim to
generate a finished application.  Candidate generated outputs are:

- route definitions;
- typed CNCF REST/Form API clients;
- DTO/View Model bindings;
- standard asynchronous operation state;
- list, detail, and editor screen skeletons;
- dynamic forms where Form API metadata is selected;
- StateMachine-based action enablement;
- structured error presentation and observability hooks; and
- contract fixtures and baseline tests.

Generated and hand-written code should have separate ownership, for example:

```text
lib/
  generated/
    routes/
    clients/
    models/
    screens/
  application/
    screens/
    widgets/
    customizations/
```

Regeneration must replace only generator-owned outputs and must fail rather
than overwrite an unrecognized hand-written delta.

## Candidate responsibility allocation

- CML: domain model, Business UseCases, System UseCase semantics, Operations,
  StateMachine, and DbC.
- CNCF: executable System UseCase contracts, Views, API descriptors, structured errors,
  security ingress, observability, runtime, and component packaging.
- Cozy: Application Project, UI UseCases, Interaction Tasks, Application Core,
  use-case/screen projections, review workflow, Application Structure Review
  HTML, and the later Flutter generation target.
- sbt-cozy/CNCF integration: build invocation, generated source/artifact
  integration, component-local Flutter deliverables, and distribution
  metadata.

## Candidate first development boundary

A first implementation phase should remain deliberately narrow and stop before
Flutter generation:

1. freeze minimal Business UseCase, System UseCase, UI UseCase, and Application
   Core reference identities;
2. define the three-layer realization relationships;
3. define UI-UseCase-to-Screen Projection;
4. define Logical Screen Composition for list and detail screens;
5. generate the integrated review HTML; and
6. prove deterministic bytes, diagnostics, and currentness behavior with one
   representative CML/CNCF application.

Flutter binding and code generation begin only after this semantic/review
boundary is accepted.  Editor/form generation, full Workflow integration,
mobile packaging, richer Visual Patterns, and multi-platform binaries remain
later phases.

## Subsequent Flutter Generation Scope Decision

The subsequent user decision fixes Flutter application generation as a
Component distribution and deployment workflow, not only a source-code
generator. The generated application is a `presentation` Subcomponent under
the accepted CNCF Component/Subcomponent contract. Its canonical authorities
are `cloud-native-component-framework:docs/design/component-subcomponent-architecture.md`
and `cloud-native-component-framework:docs/spec/component-subcomponent-architecture.md`,
especially R1, R3, R5, and R7 through R10:

```text
Accepted Logical UI Model + versioned target Policy
  -> Flutter Target UI Plan
  -> generated Flutter base + typed completion overlay
  -> Presentation Subcomponent
       |- canonical Component identity
       |- parent Component membership and presentation role
       |- independent CAR identity
       |- Flutter source/build provenance
       |- selected Flutter platform artifacts
       |- manuals, release notes, licenses, and compatibility
       `- artifact inventory, integrity, and distribution guidance
  -> Component Repository publication
       `- Presentation Subcomponent product distribution complete
  -> extract selected iPhone/Android distribution artifact from verified CAR
  -> separate official platform-distribution operation
```

### Flutter application development environment

The successor is a development environment for multiple kinds of Flutter
applications whose terminal development artifact is a verified Presentation
Subcomponent CAR. It is not a one-off SalesOrder generator and not only a
command that emits `lib/generated`.

The environment provides a reproducible lifecycle:

```text
scaffold Presentation Subcomponent project
  -> select application-kind profile and target-platform matrix
  -> bind accepted Logical UI Model and versioned Policy
  -> generate owned Flutter base and typed completion contracts
  -> implement completion-owned behavior
  -> inspect / preview / test / diagnose
  -> build selected Flutter platform artifacts
  -> generate manuals, inventory, provenance, and integrity evidence
  -> package and verify the self-contained CAR
```

Application kind and target platform are independent axes:

- an **application-kind profile** selects the application role, users,
  navigation shell, interaction policy, and required capability families;
- a **target-platform profile** selects Web, iPhone/iOS, Android, Desktop, or
  another admitted Flutter build/distribution target and its physical artifact
  policy. Desktop profiles retain their operating-system, architecture,
  package/installer, signing, and update-channel identities.

The first implementation must freeze a small closed application-kind catalog
and target-platform matrix rather than hard-code one sample. The catalog may
grow through versioned profiles without changing accepted Logical UI meaning.

Web/iPhone/Android/Desktop variants of the same logical application may be
carried by one
Presentation Subcomponent CAR when they share one accepted application
identity, Logical UI Model, release lifecycle, and documentation set. Distinct
applications with different actors, goals, UI UseCases, authority, or release
lifecycle are separate Presentation Subcomponents and therefore separate CARs,
even when they belong to the same parent Component.

The development workflow completes when it can build and verify the CAR from
declared project inputs. Component Repository publication is the subsequent
product-distribution operation. Extracting a mobile artifact and distributing
it through an official platform channel remains another downstream operation.
Desktop installer/package extraction and distribution through an admitted
desktop channel follows the same separate-operation boundary.

### Dual Flutter Web delivery

A Web-bearing Presentation Subcomponent supports two explicit delivery modes
from the verified CAR release:

```text
Presentation Subcomponent CAR / Web artifact
  |- export
  |    -> portable static Web bundle
  |    -> deployment manifest and server requirements
  |    -> deployment to an admitted external Web server/CDN
  `- cncf-hosted
       -> CAR Web resource root
       -> Component-scoped CNCF Web route
       -> same-origin CNCF REST/Form API integration
```

`export` extracts a deployable bundle without rebuilding or changing the CAR.
Its output includes the exact Web artifact identity and digest, entry point,
base-path/route requirements, asset/MIME inventory, cache/service-worker
policy, History API fallback requirement, security headers, configuration
slots, and deployment guidance. External Web-server deployment is a separate
operation and receipt, just like mobile or desktop channel delivery.

`cncf-hosted` serves the admitted Web artifact directly from the CAR through
CNCF. It owns app-scoped route fallback, asset/MIME delivery, cache/currentness,
session/CSRF, and same-origin REST/Form API policy. It must not apply an SPA
fallback to unrelated `/web` applications or bypass Operation authorization.

Both modes consume the same exact CAR release. If a physical difference such
as a non-relocatable base path requires more than one Web bundle, those bundles
are explicit target variants in the CAR with separate identities and
compatibility metadata; neither delivery operation silently rebuilds them.

When a Presentation Subcomponent is connected to its parent Component and an
explicit Web-exposure binding is admitted, the parent can publish the child's
internal Web application through its Component Web surface. The child retains
Web resource/artifact ownership; the parent owns selection, composition,
entry/alias exposure, and collision policy. Parent membership alone does not
grant exposure authority, and connecting one child must not expose another
child accidentally.

The parent Component references the Presentation Subcomponent by canonical
identity and membership; it does not embed a mutable copy of child identity or
turn the parent CAR into an aggregate binary container. The Presentation
Subcomponent remains an independent Component with its own CAR.

### Self-contained product CAR

The Presentation Subcomponent CAR is the complete product-distribution unit,
not only a wrapper around generated binaries. A consumer that obtains the
exact CAR must be able to inspect and verify the product, understand how to use
and distribute it, select the correct platform artifact, and prepare the
separate official distribution operation without recovering hidden knowledge
from the source workspace or the generating task.

At minimum, the CAR carries or exposes through its admitted metadata/resources:

- canonical Component identity, version, parent membership, and
  `presentation` role;
- supported Flutter target/platform/architecture profiles and compatibility;
- an artifact inventory with role, platform, architecture, media type, path,
  digest, size, build identity, and provenance;
- user, installation, operation, extraction, and official-distribution
  manuals appropriate to the selected targets;
- release notes, known limitations, support/diagnostic guidance, and migration
  information where applicable;
- license, third-party attribution, credit, SBOM, and applicable security or
  signing evidence;
- Component dependencies and required external platform/runtime prerequisites;
- Help/MCP-readable product, artifact, and operation information; and
- deterministic integrity/currentness evidence tying every document and
  platform artifact to the same Component release.

Documentation bundled in the CAR is an information payload and does not become
runtime or semantic authority merely by being present. A separately identified
Documentation Subcomponent is required only when that documentation needs its
own Component identity and lifecycle; ordinary product manuals can remain
resources of the Presentation Subcomponent CAR.

### CNCF runtime information and optional Web surface

The Presentation Subcomponent CAR remains runnable by CNCF even though its
mobile and desktop applications execute on external platforms. Running every
CAR exposes the Component's information surface through standard CNCF
projections:

- Component identity, version, parent membership, role, compatibility, and
  provenance;
- Help, user/installation/operation/extraction/distribution manuals, release
  notes, licenses, credits, and support information;
- platform artifact inventory, integrity/currentness, and selection guidance;
- deployment prerequisites and the discoverable description of downstream
  distribution operations; and
- admitted MCP resources and other standard read-only information
  projections.

Without an admitted Web target/exposure binding, the runtime remains
information-only. With an admitted Web target, CNCF may additionally serve the
Flutter Web UI directly, including through an explicitly connected parent
Component projection. This still does not expose the application's business
functions as Presentation Subcomponent Operations: the UI invokes authorized
Operations owned by the bound backend Component/System UseCases through CNCF
REST/Form API surfaces.

CNCF does not execute embedded iPhone/Android/Desktop applications, publish
store-facing application functions, deploy an external artifact, or perform
official channel distribution.

Inventory visibility and content disclosure remain distinct. CNCF may expose
that manuals or artifacts exist while requiring accepted authorization and
integrity checks before returning protected content. Information-only does not
mean unauthenticated or unrestricted disclosure.

`CARだけ入手すればあとはなんとかなる` therefore means that no private
workspace path, build-task memory, or undocumented command is required after
acquisition. It does not mean the CAR embeds secrets. Store credentials,
signing keys, organization policy, target accounts, and environment-specific
rollout configuration remain external authorized inputs to the downstream
official-distribution operation.

Successor acceptance must prove this in a clean consumer environment: begin
with only the published CAR plus explicitly declared external credentials and
deployment policy; inspect Help/manuals, verify integrity and compatibility,
select and extract the requested platform artifact, and construct the official
distribution operation without access to the generator workspace.
It must also run the CAR under CNCF and prove that its standard information
surface is usable; then, for a Web-bearing profile, prove direct child and
explicit parent-connected Web publication without exposing Flutter business,
platform-deployment, or official-distribution functions as Presentation
Subcomponent Operations. The export path must be accepted from the same CAR in
a clean external Web-server fixture.

The Flutter workflow is in scope for the later generation phase when it:

- provides scaffold, inspect, plan, generate, preview, test, build, package,
  and verify operations for versioned application-kind and target-platform
  profiles;
- provides Web export and CNCF-hosted delivery from an exact CAR release, plus
  explicit parent-to-Presentation-Subcomponent Web exposure binding;
- creates or updates the Presentation Subcomponent project from an accepted
  Logical UI Model and an exact versioned target Policy;
- keeps generator-owned Flutter base code separate from typed human-owned
  completion paths;
- builds the platform artifacts selected by the Subcomponent profile;
- records logical Component identity separately from physical artifact path,
  digest, platform, architecture, signing, and provenance;
- packages the admitted Flutter artifacts and metadata into the independent
  Subcomponent CAR together with the manuals, inventories, licenses,
  compatibility, provenance, and integrity evidence needed for independent
  consumption;
- verifies that a clean rebuild produces the admitted CAR identity and that
  no generator-owned or completion-owned source was silently overwritten;
- publishes that CAR through the normal Component Repository path so a
  consumer resolves it by canonical Component identity, completing product
  distribution at the CNCF Component boundary;
- extracts the selected iPhone or Android distribution artifact from an exact
  verified CAR release without rebuilding or changing its identity; and
- performs App Store, TestFlight, Google Play, or another official channel
  distribution as a separate operation with its own authorization, receipt,
  currentness, rollout, and rollback evidence.

CAR publication is the product-distribution boundary for the Presentation
Subcomponent. It must not silently perform official mobile-store distribution
or device deployment. The downstream distribution operation consumes an exact
verified CAR release and reports the selected embedded artifact, official
channel, application/package identity, configuration, outcome, and released
revision separately. It must not rebuild or mutate the CAR while extracting
the platform artifact.

The first generation Phase must freeze its supported target profile and real
acceptance driver. Web, mobile, and desktop may share the Presentation
Subcomponent contract, but extraction, target-specific signing, store
submission, hosting, and rollout behavior belong to explicit downstream
operations and must not be inferred from the generic CAR contract.

## Open questions

- Whether Application Core is encoded as a CML application model, a Cozy-owned
  DSL referencing CML identities, or a normalized IR with both authoring
  fronts.
- Which existing CML vocabulary is sufficient for Business UseCase and System
  UseCase, and which UI UseCase/projection identities need new closed
  vocabularies.
- Whether route identity belongs to Application Core or a Flutter-specific
  binding layer.
- How a reusable logical screen is parameterized without becoming a generic
  template programming language.
- Which Visual Patterns form the first closed Flutter catalog.
- Which Flutter target profile and real consumer/deployment environment supply
  the first Presentation Subcomponent acceptance driver.
- Which repository and phase own the initial cross-project contract kernel.

No implementation, phase-state change, strategy change, publication, or
commit was performed by this consideration record.
