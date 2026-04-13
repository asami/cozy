# Minimum Essence-Based Development Process Definition

status=note
published_at=2026-04-12

## Source Materials

Primary source:

- `/Users/asami/Dropbox/doc2024/0924.marulabo.ofad.process-overview/process-overview_fixed.pptx`

Related follow-up sources:

- `/Users/asami/Dropbox/doc2024/1219.marulabo.ofad.process-usecase/process-usecase_fixed.pptx`
- `/Users/asami/Dropbox/doc2025/0201.marulabo.process-analysis/process-analysis_fixed.pptx`
- `/Users/asami/Dropbox/doc2025/0504.marulabo.process-test/process-text_fixed.pptx`
- `/Users/asami/Dropbox/doc2025/0401.marulabo.process-implementation/process-implementation_fixed.pptx`

Notes:

- `blog.ofad` dox files under `/Users/asami/Dropbox/doc2025` were searched, but no direct `Essence` article was found.
- The main definition appears in the Marulabo slide deck `process-overview_fixed.pptx`.
- Later 2025 decks reuse the process as `Practice の Activity (マイ開発プロセス版改 2)`.

## Purpose

The process is a personal, minimum viable development process for object-oriented and cloud-native application development.

The core intent is:

- use UML/UP as the base mental model for object-oriented development work and model construction,
- tune the work procedure per project as a personal development process,
- support agile and cloud-native development,
- keep the process lightweight enough for a solo or very small-team project,
- use a virtual mini-project, Book Cafe Pieris Books, as the running case study.

The process is explicitly framed as defining the minimum process required to realize agile development and cloud-native development.

## Essence Basis

Essence is used as the process description framework.

The source material identifies these Essence concepts as relevant:

- `Alpha`: measurable key items in software development.
- `Alpha State`: state of an Alpha.
- `Area of Concern`: concern area grouping Alphas and work.
- `Activity Space`: space of work that should be performed.
- `Activity`: concrete software development activity.
- `Work Product`: output produced by activities.
- `Competency`: capability required to perform activities.
- `Pattern`: elements that do not fit directly into the core Essence structure, such as roles or checkpoints.
- `Practice`: mechanism for extending Essence; mainly composed from Alpha, Activity, Work Product, Competency, and Pattern.

The process uses the Essence kernel plus a small set of standard, modified, and course-specific practices.

## Kernel Alphas

The source material lists the following Essence kernel Alphas:

- `Opportunity`: what the software development effort wants to achieve.
- `Stakeholders`: interested parties.
- `Requirements`: requirement specification.
- `Software System`: target software system.
- `Work`: development work.
- `Team`: development team.
- `Way of Working`: how development proceeds.

For the minimum process, these Alphas are used as a checklist for whether the work is sufficiently grounded, not as a heavyweight governance model.

## Process Structure

The process is defined by combining three groups of practices.

Standard practice:

- `Use Case`

Modified practice:

- `Scrum Solo`

Course-specific extension practices:

- `Business Modeling Solo`
- `Cloud Native CBD`
- `User Environment Solo`
- `CI/CD Pipeline`
- `DevOpsSec Solo`

The resulting process is a minimum personal process for:

- identifying context and business intent,
- deriving requirements around use cases and use-case slices,
- implementing cloud-native components,
- verifying behavior through tests,
- preparing and operating the delivery environment.

## Practice Summary

### Business Modeling Solo

Purpose:

- organize the usage context of the target IT system with lightweight business modeling.

Typical concerns:

- business context,
- business goals,
- actors and stakeholders,
- business process or flow,
- external systems and constraints.

Expected work products:

- system context model,
- business context notes,
- business flow or process model,
- stakeholder/actor list.

### Use Case / Use Case Lite

Purpose:

- capture requirements around user-visible interactions and useful system services.

Follow-up material refines this into `Use Case Lite`.

Typical concerns:

- use cases,
- use-case slices,
- relation from use cases to backlog items,
- relation from use-case slices to sprint backlog items.

Expected work products:

- use-case model,
- use-case slice list,
- requirement notes,
- acceptance or BDD-style test cases.

### Scrum Solo

Purpose:

- adapt Scrum Lite for solo development.

Activities:

- `Sprint Planning Solo`: decide the increment to provide and register it in a solo sprint backlog.
- `Daily Scrum Solo`: decide the day's work at the beginning of the day.
- `Sprint Review Solo`: discuss or review results with a stakeholder and reflect results into the product backlog.
- `Sprint Retrospective Solo`: reflect on the development process after the sprint and feed improvements into the next sprint.

Expected work products:

- product backlog,
- sprint backlog solo,
- review notes,
- retrospective notes.

### Cloud Native CBD

Purpose:

- analyze, design, implement, and evolve cloud-native application components.

Typical concerns:

- component-based development,
- domain component boundaries,
- cloud-native application architecture,
- event-driven or API-driven component interaction,
- deployability and testability.

Expected work products:

- component model,
- domain model,
- interface or operation definitions,
- implementation source,
- deployable component package.

### User Environment Solo

Purpose:

- prepare the user or stakeholder environment required to use and validate the system.

Typical concerns:

- local or staging environment,
- demo environment,
- user-facing documents,
- manual or operational notes.

Expected work products:

- environment setup notes,
- sample data,
- usage guide,
- manual or runbook.

### CI/CD Pipeline

Purpose:

- build and operate the pipeline required to continuously build, test, package, and deploy the system.

Typical concerns:

- build automation,
- test automation,
- package generation,
- deployment automation,
- pipeline artifact management.

Expected work products:

- build definition,
- test scripts/specs,
- CI/CD pipeline definition,
- package artifacts,
- release notes.

### DevOpsSec Solo

Purpose:

- provide a lightweight solo-development form of DevOpsSec.

Typical concerns:

- operations,
- security checks,
- observability,
- release and maintenance feedback loop.

Expected work products:

- operational checklist,
- security checklist,
- monitoring/logging notes,
- issue and improvement backlog items.

## Minimum Flow

A compact operational flow can be read as follows:

1. Frame the opportunity and context.
2. Identify stakeholders and actors.
3. Sketch the business context and business process.
4. Define use cases and use-case slices.
5. Convert use cases to product backlog items.
6. Convert use-case slices to sprint backlog items.
7. Plan a solo sprint.
8. Implement cloud-native component slices.
9. Test the implemented use-case slice.
10. Review results with stakeholders.
11. Reflect on the way of working.
12. Update backlog, process notes, and pipeline/environment as needed.

## Minimum Activity Set

The practical minimum activity set is:

- `Explore Context`: clarify business context, opportunity, and stakeholders.
- `Model Business Flow`: model the lightweight business process needed to understand the target system.
- `Define Use Case`: define useful services from actor/system interactions.
- `Slice Use Case`: create small implementable and testable requirement slices.
- `Plan Sprint Solo`: choose the next increment and sprint backlog items.
- `Implement Component Slice`: implement a cloud-native component slice.
- `Test Use-Case Slice`: verify the slice against expected behavior.
- `Review Increment`: confirm results with a stakeholder or by stakeholder-facing evidence.
- `Retrospect Solo`: improve the way of working.
- `Maintain Pipeline and Environment`: keep build/test/package/deploy and user environment usable.

## Minimum Work Products

The minimum process can be operated with the following work products:

- opportunity/context note,
- stakeholder/actor list,
- business context or process model,
- use-case list,
- use-case slice list,
- product backlog,
- sprint backlog solo,
- component/domain model,
- source code,
- test cases and execution results,
- build/package artifact,
- environment/run notes,
- review/retrospective notes.

## Interpretation for Cozy / CML Work

For Cozy and CML, this process suggests the following modeling direction:

- CML should be able to capture context, requirements, use cases, components, operations, values/entities, and state/event behavior as linked work products.
- A minimum development process does not require a full enterprise process model, but it benefits from traceability across context, use case, slice, component, operation, implementation, and tests.
- `car-sbt-project` and generated component scaffolds can be treated as outputs of the `Cloud Native CBD` and `CI/CD Pipeline` practices.
- Executable specs and scripted tests can represent `Test Use-Case Slice` work products.
- Journals and generated manuals can represent lightweight review and runbook work products.

## Open Follow-Up

Potential next actions:

- Extract the Activity diagram from `process-overview_fixed.pptx` slide 34 and convert it into a structured CML/process model.
- Extract `Practice の Activity (マイ開発プロセス版改 2)` from the 2025 decks and compare it with the 2024 base definition.
- Define a CML syntax for `PRACTICE`, `ACTIVITY`, `WORKPRODUCT`, and `ALPHA` only if it directly supports Cozy/CNCF development workflow automation.
- Map the minimum process work products to existing CML top-level sections, especially `REQUIREMENT`, `COMPONENT`, `OPERATION`, `ENTITY`, `VALUE`, `EVENT`, and `STATEMACHINE`.
