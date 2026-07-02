# BoK Glossary and CML Classification Alignment

Date: 2026-07-02

This note records the working specification for aligning BoK glossary term
classification with CML and CNCF entity classification.

## Purpose

BoK glossary classification and CML classification serve different purposes.

BoK glossary classification is a semantic analysis axis. It helps contributors
extract and refine terms through mono-koto analysis.

CML classification is a modeling and runtime axis. It determines how a modeled
object is represented as an Entity, Value, Event, Operation, Rule, StateMachine,
or another model element.

The two axes should be connected by an explicit mapping. They should not be
collapsed into one shared enum.

## Mono-Koto Extraction

Mono-koto analysis starts with a rough extraction guide:

- noun-like expressions become mono candidates
- verb-like expressions become koto candidates

This is only an extraction aid. The final term type is decided by the role of
the term in the BoK knowledge space, not by grammar alone. A noun-like term may
become a koto term, and a verb-like expression may become a mono term if the
BoK role requires it.

## Glossary Classification

Glossary term types describe the semantic role of a term in the BoK.

Recommended broad grouping:

| Group | Term types | Meaning |
| --- | --- | --- |
| mono | `concept`, `entity`, `actor`, `role`, `resource`, `artifact` | thing, object, participant, responsibility, material, or structural knowledge |
| koto | `event`, `action`, `process`, `task`, `rule`, `state`, `scenario` | occurrence, activity, flow, execution unit, constraint, condition, or usage flow |

`resource` is useful as a reader-facing and dashboard-facing label, but it
should not be treated as the canonical CML runtime classification. When a
resource is modeled as a persistent object, it is a CML Entity with an
`entityKind`.

## CML Entity Classification

In CML/CNCF, an `entity` is a persistent, identifiable domain object.

The purpose and runtime handling of that persistent object are classified by
`entityKind`.

Current CNCF `EntityKind` values:

| EntityKind | Meaning |
| --- | --- |
| `master` | master data, reference data, or reader-facing resource |
| `document` | document, article, CMS content, or public content |
| `workflow` | stateful process or workflow |
| `task` | execution unit, job, or command-like task |
| `actor` | actor, agent, party, or external participant |
| `asset` | media, blob, attachment, image, video, or similar asset |
| `system` | runtime, system, or system-management target |

The older `operationKind = "resource"` / `operationKind = "task"` axis remains
a compatibility bridge. Current CNCF code treats it as legacy metadata:

- `operationKind = "resource"` maps to `EntityKind.Master`
- `operationKind = "task"` maps to `EntityKind.Task`

BoK documentation should prefer `entityKind` when explaining CML/CNCF entity
classification. It may still mention `resource` as the reader-facing label for
master/reference/resource-like persistent objects.

## Mapping Rule

The mapping should be read as:

```text
BoK term type -> CML representation -> optional CML/CNCF subtype
```

Representative mapping:

| BoK term type | CML representation | CML/CNCF subtype |
| --- | --- | --- |
| `concept` | `value`, `powertype`, `rule`, or RDF concept | selected by modeling need |
| `entity` | `entity` | `entityKind` required when runtime meaning matters |
| `actor` | `entity` | `entityKind = actor` |
| `role` | `value`, `powertype`, entity attribute, or authorization role | selected by modeling need |
| `resource` | `entity` | usually `entityKind = master`, `document`, or `asset` |
| `artifact` | `entity` | usually `entityKind = document` or `asset` |
| `event` | `event` | event category and routing metadata |
| `action` | `operation` | command or query |
| `process` | `statemachine` or `entity` | `entityKind = workflow` when persistent |
| `task` | `entity` or job model | `entityKind = task` when persistent |
| `rule` | `rule`, constraint, validation, guard, or policy | selected by modeling need |
| `state` | statemachine state or powertype | selected by modeling need |
| `scenario` | scenario, test, or documentation flow | may reference CML elements |

Important rule:

```text
Mono/koto is the extraction and semantic analysis view.
CML Entity is the persistent object view.
A persistent koto can still be a CML Entity, usually entityKind = workflow or task.
```

## Dashboard Implication

The glossary dashboard should show the mono-koto refinement workflow without
exposing every CML runtime detail:

1. Extract candidate terms from articles, scenarios, and references.
2. Use noun-like terms as mono candidates and verb-like terms as koto candidates.
3. Refine candidates into BoK term types.
4. Allow mono/koto reassignment when the BoK role is clearer than the grammar.
5. Connect curated terms to CML and RDF.

For dashboard display:

- show `resource` as a reader-facing type where useful
- explain that CML uses `entityKind` for persistent object classification
- keep detailed `entityKind` values in CML alignment or Term Hub details
- avoid making the dashboard look like a CML enum reference

## Source Boundary

Glossary source remains authoritative for BoK term type.

CML source remains authoritative for CML model classification.

Cozy should display alignment and diagnostics between the two, but it should
not rewrite glossary source or CML source automatically.
