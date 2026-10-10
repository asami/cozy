# Document Project Document and Summary Description DSL Specification

Status: normative for Phase 71.1, P710-03-DM, DM05. This specification is
paired with `docs/design/document-project-document-and-summary-description-dsl.md`.
It supersedes the Phase 58.1 raw-byte identity requirements for the V1
Description load, render, and CLI chain.

## 1. Authorities and source admission

Exactly three semantic authorities participate:

1. direct `core.yaml` is the locale-independent recursive Content Core;
2. direct `document.yaml` below a locale directory is the complete localized
   Document Description; and
3. direct `summary.yaml` below a locale directory is the localized concise
   Summary Description.

The source layout is an organization convention:

```text
content/
  core.yaml
  <locale>/
    document.yaml
    summary.yaml
```

Each input path must resolve directly to a regular non-symlink file with the
exact basename `core.yaml`, `document.yaml`, or `summary.yaml`. A Document or
Summary input must be directly contained by a locale directory whose basename
exactly equals its explicit `locale` field. A locale is never inferred from a
directory, filename, or omitted field; a declared/path mismatch rejects.

Input bytes must be strict UTF-8 and parsed as YAML with duplicate mapping
keys rejected before normalization. Malformed YAML, malformed or lossy UTF-8,
anchors, aliases, explicit tags, and unsafe source admission reject
deterministically. The V1 model has no raw-byte digest or identity field.
Parsed objects and regenerated text are not freshness or receipt authorities.

## 2. Scalar vocabulary and root admission

Stable IDs use the existing Core grammar exactly:

```text
[A-Za-z0-9][A-Za-z0-9._-]*
```

An ID is nonempty, ASCII, and contains no whitespace. Wording fields are
nonempty UTF-8 strings after trimming. The allowed locale shape is
`[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*`, and its exact source value must equal
the direct locale-directory basename.

Document requires `schema`, `id`, `core`, `locale`, and `document`. Summary
requires `schema`, `id`, `core`, `document`, `locale`, and `summary`. Their
schemas are respectively `cozy.document-description.v1` and
`cozy.summary-description.v1`.

The root and `core`/`document` binding objects use generic required-field
admission: additional properties of any YAML type are ignored without being
read, retained, or emitted. This includes historical `identity` or hash
properties. Missing, malformed, or non-string required semantic fields
reject. The `document`, `summary`, section, block, item, unit, and
`coreRefs` objects below remain closed exact shapes.

## 3. Document Description exact schema

The canonical no-hash root shape is:

```yaml
schema: cozy.document-description.v1
id: document-id
core:
  id: core-id
locale: ja
document:
  title: A complete localized document
  sections:
    - id: section-id
      heading: Section heading
      coreRefs:
        steps: [step-id]
        claims: []
        nodes: []
        relations: []
        flows: []
      blocks:
        - id: paragraph-id
          kind: paragraph
          text: Paragraph wording.
          coreRefs:
            steps: [step-id]
            claims: []
            nodes: []
            relations: []
            flows: []
        - id: list-id
          kind: list
          items:
            - id: list-item-id
              text: Item wording.
              coreRefs:
                steps: [step-id]
                claims: []
                nodes: []
                relations: []
                flows: []
          coreRefs:
            steps: [step-id]
            claims: []
            nodes: []
            relations: []
            flows: []
        - id: example-id
          kind: example
          title: Example title
          text: Example wording.
          coreRefs:
            steps: [step-id]
            claims: []
            nodes: []
            relations: []
            flows: []
        - id: note-id
          kind: note
          title: Note title
          text: Note wording.
          coreRefs:
            steps: [step-id]
            claims: []
            nodes: []
            relations: []
            flows: []
        - id: logical-structure-id
          kind: logical-structure
          stepRef: step-id
      sections: []
```

`document` requires exactly `title` and `sections`. Every recursive section
has exactly `id`, `heading`, `coreRefs`, `blocks`, and `sections`; `sections`
is an ordered recursive array. A section's five reference arrays collectively
contain at least one resolved Core reference.

The block vocabulary is closed. The exact key sets are:

| `kind` | Exact key set |
| --- | --- |
| `paragraph` | `id`, `kind`, `text`, `coreRefs` |
| `list` | `id`, `kind`, `items`, `coreRefs` |
| `example` | `id`, `kind`, `title`, `text`, `coreRefs` |
| `note` | `id`, `kind`, `title`, `text`, `coreRefs` |
| `logical-structure` | `id`, `kind`, `stepRef` |

No sixth block kind is admitted. Paragraph, list, example, and note are
prose-bearing blocks with nonempty wording and at least one resolving
reference. A list item has exactly `id`, `text`, and `coreRefs`; each item and
the list-level `coreRefs` must be nonempty and resolving. A
`logical-structure` has exactly one resolving `stepRef` and no prose,
reference object, layout name, or renderer option.

All section, block, and list-item IDs form one globally unique set across the
recursive Document Description. Reusing a stable Core ID in multiple
`coreRefs` objects is allowed and is how authored traceability is represented.

## 4. Summary Description exact schema

The canonical no-hash Summary root and representative unit are:

```yaml
schema: cozy.summary-description.v1
id: summary-id
core:
  id: core-id
document:
  id: document-id
locale: ja
summary:
  title: Concise explanation
  units:
    - id: unit-id
      heading: Unit heading
      message: Deliberately concise message.
      emphasis: primary
      coreRefs:
        steps: [step-id]
        claims: []
        nodes: []
        relations: []
        flows: []
```

`summary` requires exactly `title` and `units`. Each Summary unit has exactly
`id`, `heading`, `message`, `emphasis`, and `coreRefs`; unit IDs are unique
within the Summary. `emphasis` is exactly `primary`, `supporting`, or
`conclusion`, and every unit has at least one resolving Core reference.
Summary is deliberate authored selection, order, wording, and emphasis, not
automatic Document truncation or slide-layout IR.

## 5. Exact References object and resolution

Every field named `coreRefs` is one exact object with exactly these five
arrays and no other keys:

```yaml
steps: [<stable Step id>, ...]
claims: [<stable claim id>, ...]
nodes: [<stable node id>, ...]
relations: [<stable Relation id>, ...]
flows: [<stable Flow id>, ...]
```

Each array contains unique stable IDs. Every ID resolves to the matching
typed identity declared by the bound Core; a claim cannot resolve as a node,
a Relation cannot resolve as a Flow, and so on. References do not authorize
inference of prose, headings, structure, or missing relationships.

Document coverage is explicit and complete. The union of section
`coreRefs`, prose-block and list-item `coreRefs`, and logical-structure
`stepRef` values must contain every Core Step, claim, node, Relation, and Flow
at least once. Summary coverage is deliberately selective, but each declared
reference must resolve and each unit must be nonempty.

## 6. Currentness and rejection boundary

Admission and validation fail closed and deterministically for malformed or
lossy source, unsafe or wrong paths, locale mismatch, duplicate keys, absent
or malformed required semantic fields, wrong logical bindings, closed nested
field violations, invalid IDs, empty wording, invalid kinds or emphasis,
unresolved typed references, incomplete Document coverage, and unsafe output
paths. Changed but valid current Core, Document, or Summary bytes are
loadable and renderable. Digest staleness, persisted receipts, timestamps,
and prior output content are not admission rules.

## 7. Rendering and command contract

The only V1 render grammars are:

```text
cozy document-project description render --core <core.yaml> --document <document.yaml> --kind document --save <output.html>
cozy document-project description render --core <core.yaml> --document <document.yaml> --summary <summary.yaml> --kind summary --save <output.html>
```

HTML is deterministic: equal semantic input yields equal HTML bytes. Document
and Summary roots and their records retain semantic `data-*-id` and typed
reference attributes; output has no digest, identity attribute, or visible
hash traceability. Japanese authored content, locale, order, closed blocks,
Summary emphasis, Structure/Flow distinction, escaping, and responsive/print
styling remain projection obligations.

The CLI report retains title, kind, logical Core/Document/Summary IDs, and
output path, but no identity line. Initial safe output admission remains required. The command then directly
writes UTF-8 HTML and reports ordinary `DESCRIPTION_PATH` write failures.
Semantic and path rejection precede output writing; an I/O failure may leave
partial output with no staging, rollback, or prior-output preservation promise.

## 8. Executable evidence and non-goals

`CozyDocumentDescriptionSpec` and
`CozyDocumentDescriptionProjectionSpec` are the executable Given/When/Then
coverage for canonical no-hash sources, ignored historical extras, complete
model and HTML equivalence, logical binding rejection, changed valid bytes,
source safety, rendering, CLI/output safety, escaping, and deterministic
varying authored prose. V2 and later media/confirmation boundaries are
outside this specification. This DM05 slice is not V2 migration, stale-state
architecture, source generation, publication, deployment, or Phase closure.
