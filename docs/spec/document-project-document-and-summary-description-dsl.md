# Document Project Document and Summary Description DSL Specification

Status: normative for Phase 58.1, Step P581-01, Slice P581-01A

This specification freezes the closed source contracts for
`cozy.document-description.v1` and `cozy.summary-description.v1`. It is paired
with the design document of the same basename under `docs/design/`.

## 1. Authority and admission

Exactly three semantic authorities participate:

1. direct `core.yaml` is the locale-independent recursive Content Core;
2. direct `document.yaml` below a locale directory is the complete localized
   Document Description; and
3. direct `summary.yaml` below a locale directory is the localized concise
   Summary Description.

Phase 58 `CozyDocumentLogicTree` and `format-ja.yaml` are historical
predecessors only. They are not adapted, renamed, mutated, or admitted as a
second authority.

Each input path must resolve directly to a regular non-symlink file with the
exact basename `core.yaml`, `document.yaml`, or `summary.yaml`. A Document or
Summary input must be directly contained by a locale directory whose basename
exactly equals its explicit `locale` field. A locale is never inferred from a
directory, filename, or omitted field. A declared/path mismatch rejects.

Input bytes must be strict UTF-8 and must be parsed as YAML with duplicate
mapping keys rejected before any normalization. Malformed YAML, malformed or
lossy UTF-8, and unsafe source admission reject deterministically.

The source identity of every admitted authority is computed as SHA-256 over
its original admitted UTF-8 bytes and represented as
`sha256:<64 lowercase hexadecimal characters>`. Parsed objects, normalized
YAML, and regenerated text are never identity inputs. The Core identity is
used by Document and Summary; the Document identity is used by Summary; the
Summary identity is computed for the admitted Summary itself.

## 2. Scalar vocabulary

Stable IDs use the existing Core grammar exactly:

```text
[A-Za-z0-9][A-Za-z0-9._-]*
```

An ID is nonempty, ASCII, and contains no whitespace. All wording fields are
UTF-8 strings that are nonempty after trimming. The allowed locale shape is a
well-formed BCP-47 language tag with the established admission form
`[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*`; its exact source value must equal the
direct locale-directory basename.

The following constraints apply to every closed object: all required fields
are present exactly once; no unknown field is accepted; duplicate mapping
keys are rejected at source admission; and scalar values have the declared
type. YAML aliases, tags, or parser conveniences may not introduce fields or
values outside the closed model.

## 3. Document Description exact schema

The root has exactly the fields `schema`, `id`, `core`, `locale`, and
`document`:

```yaml
schema: cozy.document-description.v1
id: document-id
core:
  id: core-id
  identity: sha256:<64 lowercase hexadecimal characters>
locale: ja
document:
  title: A complete localized document
  sections: []
```

The root `schema` is exactly `cozy.document-description.v1`. Root `id` and
`core.id` are stable IDs. `core` has exactly `id` and `identity`, and its
identity must equal the raw-byte identity of the directly admitted Core whose
stable ID equals `core.id`. `document` has exactly `title` and `sections`;
there is no optional root metadata, abstract, chrome, or layout field.

### 3.1 Section

Every recursive section object has exactly:

```yaml
id: section-id
heading: Section heading
coreRefs:
  steps: []
  claims: []
  nodes: []
  relations: []
  flows: []
blocks: []
sections: []
```

`sections` is an ordered recursive array. `heading` is nonempty trimmed
wording. `coreRefs` is the exact References object in Section 5. A section's
five arrays collectively contain at least one resolved Core reference.

### 3.2 Blocks and list items

The block object is closed by `kind`, and the exact object shape depends on
that value:

```yaml
# paragraph
id: paragraph-id
kind: paragraph
text: Paragraph wording.
coreRefs: { steps: [], claims: [], nodes: [], relations: [], flows: [] }

# list
id: list-id
kind: list
items:
  - id: list-item-id
    text: Item wording.
    coreRefs: { steps: [], claims: [], nodes: [], relations: [], flows: [] }
coreRefs: { steps: [], claims: [], nodes: [], relations: [], flows: [] }

# example or note
id: example-or-note-id
kind: example # or note
title: Example or note title
text: Example or note wording.
coreRefs: { steps: [], claims: [], nodes: [], relations: [], flows: [] }

# logical structure
id: logical-structure-id
kind: logical-structure
stepRef: one-resolving-step-id
```

The exact shapes are:

| `kind` | Exact key set |
| --- | --- |
| `paragraph` | `id`, `kind`, `text`, `coreRefs` |
| `list` | `id`, `kind`, `items`, `coreRefs` |
| `example` | `id`, `kind`, `title`, `text`, `coreRefs` |
| `note` | `id`, `kind`, `title`, `text`, `coreRefs` |
| `logical-structure` | `id`, `kind`, `stepRef` |

No sixth block kind is admitted. A paragraph, list, example, or note is a
prose-bearing block and must have nonempty wording and at least one resolving
reference across its five `coreRefs` arrays. A list's `items` array contains
objects with exactly `id`, `text`, and `coreRefs`; each item is prose-bearing,
and its wording and aggregate references are nonempty and resolving. The
list-level `coreRefs` is also exact and must be resolving and nonempty.

A logical-structure block has exactly one `stepRef`, which resolves to one
Core Step. It has no prose or reference object and does not name a layout,
HTML element, or renderer option.

All section IDs, block IDs, and list-item IDs form one globally unique set
across the entire recursive Document Description. Reusing a stable Core ID in
multiple `coreRefs` objects is allowed and is how prose traceability is
represented.

## 4. Summary Description exact schema

The Summary root has exactly `schema`, `id`, `core`, `document`, `locale`, and
`summary`:

```yaml
schema: cozy.summary-description.v1
id: summary-id
core:
  id: core-id
  identity: sha256:<exact current Core bytes>
document:
  id: document-id
  identity: sha256:<exact current Document bytes>
locale: ja
summary:
  title: Concise explanation
  units: []
```

The root `schema` is exactly `cozy.summary-description.v1`. `core` and
`document` each have exactly `id` and `identity`; both IDs and both raw-byte
identities must match the current admitted upstream authorities. `summary` has
exactly `title` and `units`.

Each unit has exactly `id`, `heading`, `message`, `emphasis`, and `coreRefs`:

```yaml
id: unit-id
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

Unit IDs are stable and unique within the Summary. `heading` and `message`
are nonempty trimmed wording. `emphasis` is exactly one of `primary`,
`supporting`, or `conclusion`; it is not a physical layout instruction. Every
unit has at least one unique, resolving Core reference across the exact
References object.

A Summary Description records deliberate selection, order, wording, and
emphasis. It is not automatic Document truncation and not slide-layout IR.
Summary reference coverage is selective by design; no undeclared reference or
missing unit may be inferred from the Document Description.

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

Each array has unique IDs. Every ID resolves to the matching stable identity
declared by the bound Core. Reference resolution is exact and typed: a claim
ID cannot resolve as a node, a Relation ID cannot resolve as a Flow, and so on.
References do not authorize inference of prose, headings, structure, or
missing relationships.

The Document coverage set is the union of all section `coreRefs`, prose-block
and list-item `coreRefs`, and logical-structure `stepRef` values. It must
contain every Core Step, claim, node, Relation, and Flow at least once. This
is explicit coverage, not graph or wording inference. Summary units need only
cover their authored selection, but each selection must resolve and each unit
must be nonempty as specified above.

## 6. Currentness and rejection

Admission and validation fail closed and deterministically for:

- duplicate YAML mapping keys;
- malformed YAML or malformed/lossy UTF-8;
- non-direct, symlinked, non-regular, wrong-name, or locale-scope-violating
  source paths;
- unknown, missing, repeated, or wrong-typed fields;
- invalid BCP-47 locale or a locale-directory mismatch;
- IDs outside the stable-ID grammar or duplicate section, block, list-item, or
  summary-unit IDs;
- an invalid `kind`, `emphasis`, or block/list-item shape;
- unresolved or incorrectly typed Core IDs, including a logical-structure
  `stepRef` that is not a Step;
- a Document `core.id`/`core.identity` that does not bind the admitted Core;
- Summary Core or Document IDs/identities that are stale relative to the
  directly admitted upstream bytes;
- empty wording, empty required references, incomplete Document Core coverage,
  or incomplete Summary unit bindings; and
- any attempt to infer missing content, reference, locale, identity, or
  coverage from a renderer, directory, filename, or historical Phase 58 file.

The validator must not silently drop unknown fields, collapse duplicate keys,
repair lossy text, normalize source bytes for identity, or accept a partial
binding as current.

## 7. Rendering and command contract

The only later command grammar for a Document review is:

```text
cozy document-project description render --core <core.yaml> --document <document.yaml> --kind document --save <output.html>
```

The only later command grammar for a Summary review is:

```text
cozy document-project description render --core <core.yaml> --document <document.yaml> --summary <summary.yaml> --kind summary --save <output.html>
```

The Document form validates the Core binding, full Document schema, exact
references, and complete Core coverage. The Summary form additionally
validates the Summary schema and exact current Document binding. Both outputs
are self-contained deterministic review HTML evidence only. Renderer-owned
HTML/CSS/chrome/navigation/physical layout may change evidence presentation,
but cannot change authority or semantic identity.

## 8. Article 9 acceptance obligations

Later Article 9 sources and review projections must use multiple available
Logical Patterns and typed Relation kinds where the meaning warrants them.
They must not manufacture diversity solely for a count, nor flatten every
local Structure and child-Step Flow to `sequence` and `next`. The review must
visibly distinguish local Structure inside a Step from Flow between child
Steps, with reader-facing semantic labels rather than raw identifiers alone.

## 9. Slice gate and deferred work

This documentation-only Slice changes no executable behavior. The executable
specification gate is therefore **not applicable**: no Scala behavior or test
scenario is created or modified here. Future codec, projection, Article 9
fixture, and determinism work must open a fresh applicable gate.

This Slice does not change Scala, test resources, CLI, review HTML, Article 9
Core, `document.yaml`, `summary.yaml`, Phase 58 history, `format-ja.yaml`,
SmartDox, media outputs, publication, deployment, upload, push, or any other
pre-existing user change.
