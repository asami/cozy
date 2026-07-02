# BoK Mono-Koto Analysis

## Purpose

Mono-koto analysis is the BoK view for business-domain analysis. It organizes
knowledge around terms and scenarios, then compares that top-down domain view
with bottom-up CML model metadata.

The goal is not to auto-generate or auto-correct source in v1. The goal is to
make unconnected, mismatched, and model-only knowledge visible in Dashboard,
Term Hub, and RDF Information View surfaces.

## Core Concepts

- **Term**: The primary BoK knowledge hub. SmartDox owns term source parsing and
  emits `metadata/glossary/terms.json`.
- **Scenario**: A flow or pattern of "koto" knowledge. Cozy/Kaleidox owns
  scenario semantics after SmartDox parses source documents into Dox.
- **Mono**: Thing/object/entity-oriented knowledge.
- **Koto**: Event/fact/activity/process-oriented knowledge.
- **CML element**: Model metadata linked to a term. CML kind is not a
  `term_type`.
- **RDF Information View**: The graph-facing view where term classification and
  CML linkage are shown as Information attributes.

## Classification Rule

`term_type` is the authoring and metadata axis for glossary term kind.

BK12-15 v1 standard values:

- `concept`
- `event`
- `actor`
- `role`

Derived mono-koto classification:

| `term_type` | Classification | Meaning |
| --- | --- | --- |
| `event` | koto | event, fact, occurrence, or state-transition point |
| `concept` | mono | concept, object, entity, value, or structural knowledge |
| `actor` | mono | actor as a domain participant |
| `role` | mono | role as a responsibility/permission structure |

New `term_type` values are intentionally not introduced in v1. Candidate future
values such as `entity`, `resource`, `artifact`, `state`, `process`, `task`,
`activity`, `rule`, and `place` remain extension candidates.

Mono-koto extraction may use a rough grammar aid: noun-like expressions are
mono candidates and verb-like expressions are koto candidates. This is only a
starting point. The final `term_type` is decided by the role of the term in the
BoK knowledge space, and a term may move between mono and koto during curation.

See `docs/notes/bok-glossary-cml-classification-alignment.md` for the current
working alignment between BoK glossary classification and CML/CNCF
classification.

## Scenario Relationship

Scenarios describe "koto" flows and patterns. Scenario metadata may reference
Actor, Role, Event, and Concept terms. Term Hub uses those references to show
which scenarios exercise or explain a term.

Event terms are the strongest scenario anchor because they represent important
occurrence points, facts, or state transitions. Actor and Role terms provide the
participants and responsibilities in the scenario.

## State Machine Boundary

BoK mono-koto analysis records business-domain knowledge. It may identify an
event, state-transition point, scenario step, actor, role, and business rule at
the knowledge level, but it does not own program-level state machine modeling.

When analysis requires precise state machine modeling, the BoK should connect
the Event term or scenario to CML and let the CML model carry the exact model:

- states
- transitions
- transition events
- guards
- operations
- implementation-facing semantics

The BoK side keeps the domain explanation, evidence, scenario context, and CML
linkage metadata such as `event.cml_statemachine`, `event.cml_event`, and
`cml.element_ref`. The CML/modeler side owns the executable or code-generation
level statemachine structure.

This keeps the top-down and bottom-up loop explicit:

- Top-down: BoK terms and scenarios identify candidate events and state changes.
- Bottom-up: CML model metadata reports the actual state machine elements.
- Alignment: Cozy shows missing links, mismatches, or model-only elements as
  diagnostics instead of automatically rewriting either side.

## CML Alignment

CML elements are linked metadata, not glossary term types.

Glossary terms are expected to carry CML linkage when a term corresponds to a
model element. Typical authoring links are:

- Entity
- Value
- Powertype
- Statemachine
- Rule

In CML/CNCF, `entity` means a persistent, identifiable domain object. Its
runtime/modeling purpose is classified by `entityKind`, currently including
`master`, `document`, `workflow`, `task`, `actor`, `asset`, and `system`.
Legacy `operationKind = "resource"` / `"task"` remains a compatibility bridge;
new BoK explanations should prefer `entityKind` when describing persistent
object classification.

These CML element kinds are not added to `term_type`. A term keeps its BoK role
such as `concept`, `event`, `actor`, or `role`, and records the CML element as
linkage/provenance. For example, a concept term may link to a CML Entity, a
domain value term may link to a CML Value, a classification term may link to a
CML Powertype, and an event or state-transition term may link to a CML
Statemachine. A future rule term should link to a CML Rule when the business
rule needs precise constraint, validation, guard, calculation, policy, or flow
semantics.

The CML linkage shape is conceptually:

- `cml.kind`
- `cml.name`
- `cml.project`
- `cml.module`
- `cml.element_ref`

Current Cozy metadata already exposes event-level CML fields:

- `event.cml_event`
- `event.cml_component`
- `event.cml_statemachine`

When a koto term becomes persistent in CML, it may still be modeled as an
Entity. Typical examples are workflow-like and task-like entities. This does
not change the BoK semantic classification; it only records the CML
representation.

CML kind is interpreted for display:

| CML kind | Display classification |
| --- | --- |
| `entity`, `value`, `powertype`, `service`, `component` | mono |
| `event`, `operation`, `statemachine` | koto |
| `rule` | constraint / rule |

Hand-written glossary terms remain authoritative. CML-derived descriptions,
descriptive attributes, and narrative are displayed as supplements or
provenance. If a CML element exists without a hand-written term, Cozy may render
a generated-from-CML provisional Term Hub and mark it as needing curation.

## Diagnostics

v1 diagnostics are display-only:

- no CML linkage recorded
- event term has no related scenario
- CML linkage kind and `term_type` classification may be inconsistent
- CML-only element appears as generated-from-CML / needs-curation

Cozy must not rewrite glossary source, scenario source, or CML source in BK12-15.

## UI Contract

Glossary Dashboard:

- shows term type counts
- shows mono/koto counts
- shows a compact count of terms without CML linkage

Term Hub:

- shows `term_type`
- shows derived mono/koto classification
- shows CML linkage when metadata exists
- shows related scenarios
- shows alignment diagnostics

RDF Information View:

- uses `terms.json` to project mono/koto classification onto RDF nodes
- shows CML linkage from related term metadata
- keeps RDF graph metadata as the graph source of truth

Home and Category Dashboards:

- keep mono-koto analysis as supporting information
- continue to prioritize term, scenario, and RDF navigation

## Responsibility Boundary

SmartDox:

- parses glossary source
- handles locale filtering and glossary metadata
- emits `metadata/glossary/terms.json`

Cozy:

- derives mono/koto classification from `term_type`
- connects term metadata, scenario metadata, CML model metadata, and RDF viewer
- renders diagnostics and provisional CML-derived Term Hubs

CML/modeler:

- emits model metadata from the normalized model
- does not decide BoK source curation policy
