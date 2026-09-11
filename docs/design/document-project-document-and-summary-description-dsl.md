# Document Project Document and Summary Description DSL Design

Status: normative for Phase 58.1, Step P581-01, Slice P581-01A

This design defines the paired `cozy.document-description.v1` and
`cozy.summary-description.v1` authoring authorities. It is paired with
`docs/spec/document-project-document-and-summary-description-dsl.md`; the
specification gives the same contract as exact admission and validation rules.

## Authority boundary

The three semantic authorities are deliberately separate.

| Authority | Admitted source | Owns |
| --- | --- | --- |
| Content Core | direct `core.yaml` | Locale-independent Steps, claims, nodes, Relations, and Flows in the recursive logic tree |
| Document Description | direct `document.yaml` below a locale directory | The complete localized document title, recursive section organization, prose, and exact Core traceability |
| Summary Description | direct `summary.yaml` below a locale directory | The deliberate localized concise selection, order, wording, emphasis, and exact Core traceability for a summary explanation |

The Core remains the Phase 58 `cozy.content-core.logic-tree.v1` authority.
The Document Description and Summary Description are semantic authoring DSLs,
not HTML, CSS, PDF, PPTX, video, infographic, or slide-layout intermediate
representations. A renderer may project all three authorities into review
evidence, but it may not add, infer, or replace semantic content.

The following are renderer-owned and non-authoritative: localized chrome,
navigation, CSS, coordinates, fonts, page or slide dimensions, pagination,
print rules, and other physical layout. Generated HTML is self-contained,
deterministic review evidence only.

Phase 58's `CozyDocumentLogicTree` and `format-ja.yaml` remain historical
predecessors. This design neither adapts nor renames them, mutates them, nor
admits them as a second authority.

## Source and identity boundary

The source layout is an organization convention:

```text
content/
  core.yaml
  <locale>/
    document.yaml
    summary.yaml
```

Admission takes explicitly supplied paths. `core.yaml`, `document.yaml`, and
`summary.yaml` must each be a direct regular non-symlink file, with the exact
basename shown above. A Document or Summary file's direct parent directory is
the locale directory. Its basename must exactly equal that file's explicit
BCP-47 `locale` value. Neither a filename suffix nor an ancestor directory
supplies an implicit locale; a declared/path mismatch is rejected.

The admitted bytes are checked as strict UTF-8 before YAML normalization.
Duplicate mapping keys are rejected before normalization. The source bytes,
not parsed or normalized data, are the identity input:

```text
sha256:<64 lowercase hexadecimal characters>
```

The Core identity is the SHA-256 of the original admitted `core.yaml` UTF-8
bytes. The Document identity is the SHA-256 of the original admitted
`document.yaml` UTF-8 bytes. The Summary identity is likewise computed from
its original bytes. No identity is computed from a parsed tree, normalized
key order, or regenerated YAML. A Document's `core.identity` binds the Core
bytes. A Summary's `core.identity` and `document.identity` bind the exact
current bytes of both upstream authorities.

Stable semantic IDs use the existing Core grammar:

```text
[A-Za-z0-9][A-Za-z0-9._-]*
```

They are nonempty ASCII stable values; whitespace and all other characters
are rejected. Reader wording is original UTF-8 text and must be nonempty
after trimming. The explicit locale is a well-formed nonempty BCP-47 tag
(using the established language-tag shape
`[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*`) and is compared exactly with the direct
parent directory name.

## Document Description

The Document Description root is closed and has exactly these fields, in the
semantic model (source key ordering is not a source of meaning):

```yaml
schema: cozy.document-description.v1
id: <stable document id>
core:
  id: <stable Core id>
  identity: sha256:<64 lowercase hexadecimal characters>
locale: <BCP-47 locale>
document:
  title: <nonempty trimmed UTF-8 wording>
  sections: [<Section>]
```

`core` has exactly `id` and `identity`. `document` has exactly `title` and
`sections`; there is no abstract, chrome, layout, or implicit metadata field.
The document `id` identifies the localized authoring authority. Its byte
identity is computed at admission and is not duplicated as a root field.

Sections are recursive and have exactly:

```yaml
id: <stable section id>
heading: <nonempty trimmed UTF-8 wording>
coreRefs: <References>
blocks: [<Block>]
sections: [<Section>]
```

Every section has a nonempty `coreRefs` reference set whose IDs resolve in the
bound Core. Recursion is ordered and has no implicit section semantics.

`coreRefs` is the one and only references object used by these DSLs. It has
exactly the following five arrays and no other keys:

```yaml
steps: [<stable Step id>, ...]
claims: [<stable claim id>, ...]
nodes: [<stable node id>, ...]
relations: [<stable Relation id>, ...]
flows: [<stable Flow id>, ...]
```

Each array contains unique stable IDs and every ID resolves to the matching
Core identity. An array may be empty where the containing record's aggregate
reference requirement permits it; a section and each prose-bearing record
must have at least one reference across all five arrays.

The block vocabulary is closed. Every block has one of exactly these `kind`
values: `paragraph`, `list`, `example`, `note`, or `logical-structure`.

| Kind | Exact fields | Semantic rule |
| --- | --- | --- |
| `paragraph` | `id`, `kind`, `text`, `coreRefs` | Complete prose paragraph with nonempty wording and nonempty resolved references |
| `list` | `id`, `kind`, `items`, `coreRefs` | Ordered list; list-level and every item reference resolved Core meaning |
| `example` | `id`, `kind`, `title`, `text`, `coreRefs` | Complete authored example with nonempty title, text, and references |
| `note` | `id`, `kind`, `title`, `text`, `coreRefs` | Complete authored note with nonempty title, text, and references |
| `logical-structure` | `id`, `kind`, `stepRef` | Exactly one resolving Core Step; this names structure to project, not prose |

The exact list item shape is `id`, `text`, and `coreRefs`, with no `kind` or
other field. Item IDs are globally unique with every section and block ID in
the complete recursive Document Description. Item wording and references are
nonempty and resolving. A logical-structure block has no `text`, `coreRefs`,
title, or layout field; its `stepRef` names exactly one existing Step.

All section, block, and list-item IDs are globally unique across the complete
document. The same Core ID may be referenced by multiple authored records,
but no unresolved or inferred reference is accepted.

Document coverage is explicit and complete. The union of all section
`coreRefs`, prose-block and list-item `coreRefs`, and logical-structure
`stepRef` values must represent every Core Step, claim, node, Relation, and
Flow at least once. Coverage is checked against declared Core identities; a
renderer cannot infer coverage from prose, section order, or a graph edge.

## Summary Description

The Summary Description root is closed and has exactly:

```yaml
schema: cozy.summary-description.v1
id: <stable summary id>
core:
  id: <matching stable Core id>
  identity: sha256:<exact current Core bytes>
document:
  id: <matching stable Document id>
  identity: sha256:<exact current Document bytes>
locale: <BCP-47 locale>
summary:
  title: <nonempty trimmed UTF-8 wording>
  units: [<SummaryUnit>]
```

Both `core` and `document` have exactly `id` and `identity`. Their IDs and
raw-byte identities must match the admitted upstream Core and Document
authorities. The Summary locale uses the same explicit locale/path rule as a
Document. `summary` has exactly `title` and `units`.

Each summary unit has exactly:

```yaml
id: <stable summary-unit id>
heading: <nonempty trimmed UTF-8 wording>
message: <nonempty trimmed UTF-8 wording>
emphasis: primary | supporting | conclusion
coreRefs: <References>
```

Units are ordered authored content. Unit IDs are unique within the Summary;
their reference object is exact and every unit has at least one resolving
Core reference. `emphasis` is a semantic reading emphasis, not a position,
size, color, or slide template. A summary is deliberate condensation: its
selection, order, wording, and emphasis are authored values. It is never
automatic truncation of the Document Description and never a slide-layout IR.

Summary reference coverage is intentionally selective, but every declared
reference must resolve and every unit must be traceable. No renderer may
invent a missing unit, reference, or message from the Document Description.

## Projection boundary

The only later command grammar admitted by this design is:

```text
cozy document-project description render --core <core.yaml> --document <document.yaml> --kind document --save <output.html>
```

For a Summary Description, the only grammar is:

```text
cozy document-project description render --core <core.yaml> --document <document.yaml> --summary <summary.yaml> --kind summary --save <output.html>
```

The first form consumes Core and Document Description. The second additionally
consumes Summary Description and verifies both upstream bindings. Each form
produces self-contained deterministic review HTML evidence only. The output
does not become an authority, receipt, layout source, or replacement for the
authored YAML.

## Fail-closed and consumer invariants

Future codecs and the two review projections must retain every semantic field
as an exact typed value. They must reject deterministically, before producing
accepted semantic output, for duplicate keys; malformed or lossy UTF-8/YAML;
unknown fields; invalid or path-mismatched locale; non-direct, symlinked, or
wrongly named source files; malformed IDs or wording; duplicate section,
block, list-item, or summary-unit IDs; unresolved Core IDs; invalid logical
structure Step references; stale Core or Document byte identities; incomplete
Document coverage; and incomplete Summary bindings or empty unit references.
No parser, validator, or renderer may repair, normalize away, infer, or
silently ignore these conditions.

The future Article 9 driver must use semantically warranted diversity of
available Logical Patterns and typed Relation kinds. It must visibly
distinguish local Step Structure from the Flow among child Steps; rendering
both as an undifferentiated sequence/next list is not acceptance evidence.

## Historical and non-goal boundary

This Slice creates only this paired design and specification. It does not
change Scala, test resources, CLI behavior, review HTML, Article 9 Core,
`document.yaml`, `summary.yaml`, Phase 58 history, `format-ja.yaml`, or any
pre-existing user change. Strict codecs, Article 9 authored sources, review
projections, executable specifications, and all publication or deployment
activities are later work.
