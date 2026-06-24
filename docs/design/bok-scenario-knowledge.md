# BoK Scenario Knowledge

## Summary
Scenario knowledge is a first-class BoK knowledge type. It describes how
people, systems, and knowledge artifacts move through a situation.

Scenario source is authored as SmartDox `.dox` or Markdown `.md` /
`.markdown`. SmartDox parses both forms into Dox IR and `DocumentMetaData`.
Cozy owns the scenario semantic model and converts that Dox IR into
machine-readable scenario metadata for Dashboard, Category, Term Hub, and RDF
navigation.

## Source Contract
Scenario source uses the same category-centered source layout as Glossary:

```text
src/main/doxsite/
  scenario/
    <category>/
      <scenario>.dox
      <scenario>.md
      <scenario>.markdown
```

Only documents under `scenario/<category>/` are interpreted as scenario
knowledge. A normal article that contains `scenario.*` metadata outside this
tree remains an article and is not added to `metadata/scenarios/scenarios.json`.

Markdown uses YAML front matter. SmartDox uses HEAD/properties. Both forms
project to the same metadata keys.

Common metadata:

- `scenario.type`: `simple`, `use-case`, or `persona-journey`
- `scenario.id`: stable scenario identifier
- `scenario.terms`: glossary term IDs or labels related to the scenario
- `title`, `brief`, `status`, and `published_at`: normal SmartDox descriptive
  and lifecycle metadata

Markdown example:

```markdown
---
title: Reserve a meeting room
brief: Meeting room reservation use case.
scenario:
  type: use-case
  id: UC-ROOM-RESERVE
  terms:
    - reservation
---

# UseCase

## Reserve a meeting room

### MainFlow

- [open] Employee: Open the reservation page
- [select] Employee: Select a room and time
- end
```

SmartDox example:

```dox
# HEAD

title = "Reserve a meeting room"
brief = "Meeting room reservation use case."
scenario.type = "use-case"
scenario.id = "UC-ROOM-RESERVE"
scenario.terms = ["reservation"]
```

## Scenario Types
`simple` is a lightweight free-form scenario. SmartDox list items are exposed as
steps when possible, and unstructured content remains narrative.

`use-case` follows the OFAD-derived use case model. It records actors, goal,
trigger, preconditions, postconditions, priority, status, and Main /
Alternate / Exception flows. Flow directives such as `goto`, `call`, `include`,
`extend`, `generalize`, and `precedes` are recorded as metadata; v1 does not
merge or execute flows.

`persona-journey` records persona, journey stages, goals, pain points, and
touchpoints. Deeper journey analysis remains a later extension.

## Metadata Boundary
SmartDox emits Dox AST and `DocumentMetaData` only. UseCase flow, directive,
step, simple scenario, and persona journey interpretation belongs to Cozy's
Kaleidox-oriented scenario model layer.

Cozy emits:

```text
doxsite.d/metadata/scenarios/scenarios.json
```

Cozy copies this metadata into the generated website and renders scenario
navigation from it. Cozy does not implement a raw Markdown or SmartDox parser;
it always receives source documents through SmartDox parser output.

## BoK Navigation
Scenario knowledge appears in:

- BoK menu: `Scenarios`
- Home Dashboard: scenario KPI card
- Category Dashboard: category scenario links
- Term Hub: related scenarios whose `scenario.terms` match the term ID, slug,
  or title

Markdown is intended for general single-locale contributors. Multilingual
scenario authoring remains SmartDox-only.
