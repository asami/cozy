# CML Semantic Metadata v1 Specification

`cozy.cml.semantic-metadata.v1` is the versioned JSON publication of an
already admitted [`CmlSemanticFoundation.Catalog`](../../src/main/scala/cozy/modeler/CmlSemanticFoundation.scala).
The executable contract is
[`CmlSemanticMetadataSpec`](../../src/test/scala/cozy/modeler/CmlSemanticMetadataSpec.scala);
its JSON-only consumer fixture is
[`semantic-metadata-v1.json`](../../src/test/resources/cozy/modeler/semantic-metadata-v1.json).

## Envelope and order

The root is an object with exactly these fields:

| Field | JSON type | Meaning |
| --- | --- | --- |
| `schemaVersion` | string | Exactly `cozy.cml.semantic-metadata.v1`. |
| `elements` | array | Authored `Catalog.elements` order. |
| `terms` | array | Authored `Catalog.terms` order. |
| `extensions` | object | Required namespace-to-opaque-object map; `{}` means none. |

Core objects have exact field sets except the ordinary SourceAttribution
projection described below. The reader accepts a non-null Play `JsValue`,
not JSON text. It validates `schemaVersion` before catalog decoding, rejects an
unknown version without legacy upgrading, and does not repair absent/null
fields or ignore unknown core fields outside SourceAttribution. Parser-level text syntax and duplicate
member handling belong to the caller that produces the `JsValue`.

`toJson` and `canonicalJson` recursively sort object keys lexicographically,
including opaque extension objects. Arrays retain authored order, strings
retain their exact contents, and numbers use ordinary Play JSON rendering.
This is deterministic Cozy rendering for a fixed JSON value; it is not RFC
8785, a signature protocol, source-byte preservation, or numeric-equivalence
normalization.

## Core records

The complete v1 core field/type matrix is:

| Object | Field | Required wire type |
| --- | --- | --- |
| Element | `identity` | `Presence[ModelElementId]` |
| Element | `kind` | string |
| Element | `name` | string |
| Element | `source` | `SourceAttribution` |
| Element | `references` | `Presence[ordered array[SemanticReference]]` |
| Term | `identity` | direct `TermId`, never Presence |
| Term | `source` | `SourceAttribution` |
| SourceAttribution | `authorityId` | string |
| SourceAttribution | `path` | string |
| SourceAttribution | `line` | integral number in `Int` range, or explicit `null` |
| LocalizedLabel | `text` | string |
| LocalizedLabel | `language` | string, or explicit `null` |
| SemanticReference | `target` | `SemanticTarget` |
| SemanticReference | `boundary` | `local` or `external` string tag |
| SemanticReference | `relation` | `Presence[RelationId]` |
| SemanticReference | `profile` | `Presence[ProfileId]` |
| SemanticReference | `context` | `Presence[string]` |
| SemanticReference | `preferredLabel` | `Presence[LocalizedLabel]` |
| SemanticReference | `source` | `SourceAttribution` |
| SemanticReference | `origin` | `ReferenceOrigin` |

An element has exactly `identity`, `kind`, `name`, `source`, and `references`.
A Term has exactly `identity` and `source`. `kind` and `name` are descriptive
strings rather than identities or enum claims; empty, whitespace, escaped, and
Unicode strings remain exact wire data, while a null Scala descriptive value is
rejected by the envelope builder.

The qualified identity objects have these exact fields:

| Identity | Fields |
| --- | --- |
| Model element | `modelId`, `elementId` |
| Term | `vocabularyId`, `termId` |
| Relation | `vocabularyId`, `relationId` |
| Profile | `vocabularyId`, `profileId` |

`source` requires `authorityId`, `path`, and `line`. Unknown source-object
extras are ignored generically regardless of JSON type and are not emitted. `line` is an
integral JSON number in `Int` range or explicit `null` for `None`.
`LocalizedLabel` has exactly `text` and `language`; `language` is a string or
explicit `null`. These are observational options, not semantic absence.
Admitted values are revalidated by the foundation; reading does not inspect a
filesystem, recalculate digests, infer currentness, or derive an identity from
provenance.

## Presence and references

Every semantic `Presence[A]` is exactly one of:

```json
{"status":"present","value": <typed-value>}
{"status":"absent","reason": <reason>,"detail": <string>}
```

`reason` is one of `not-declared`, `not-represented`, `unsupported`, or
`not-applicable`. Missing fields, extra fields, mixed branches, null Presence,
and unknown status/reason tags are invalid. A present empty reference array is
still present; absence is never inferred from missing, null, or empty values.

A reference has exactly `target`, `boundary`, `relation`, `profile`, `context`,
`preferredLabel`, `source`, and `origin`.

- `target` is exactly `{"kind":"model-element","identity":<ModelElementId>}`
  or `{"kind":"term","identity":<TermId>}`.
- `boundary` is `local` or `external`.
- Relation, profile, context, and label use the Presence form above.
- `origin` is exactly `{"kind":"declared"}` or
  `{"kind":"derived","ruleId":<string>,"sources":[<SourceAttribution>]}`.

Relation and profile IDs are opaque upstream vocabulary values. The envelope
does not infer a derivation, local resolution, relation meaning, profile
meaning, or equivalence. An external target remains external even when its
identity has no local record.

## Validation and diagnostics

The reader traverses root, elements, terms, then extensions. For a core object other than SourceAttribution,
an extra field is rejected before expected fields are decoded; unknown fields
are considered in lexicographic key order and are reported only at their known
containing object coordinate. Valid coordinates contain only schema field names
and array indices. The first shape failure is returned; no partial envelope is
produced and no coercion occurs.

After all shapes decode, the reader calls `CmlSemanticFoundation.build`. Every
returned diagnostic is retained, with its original logical path and typed
foundation diagnostic, inside `InvalidSemanticCatalog`. Thus duplicate IDs,
invalid IDs/provenance/evidence, and dangling local references cannot bypass
the foundation factory. Extension namespaces are then checked in sorted-key
order.

`PublicationDiagnostic` is a typed value with closed kinds:
`InvalidShape`, `UnsupportedSchemaVersion`, `InvalidExtension`, and
`InvalidSemanticCatalog`. Only the last carries a foundation diagnostic. Its
path is logical rather than a workspace path, and generic detail text does not
echo hostile input.

## Compatibility and extensions

Each extension key must contain a dot and match
`[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*`; its value must be an object. Namespace
contents are arbitrary opaque JSON: nested keys, arrays, scalars, and null are
preserved without being promoted into the core or changing identity, reference,
or absence semantics. A namespace is not proof of ownership and this version
defines no registered extension meanings.

v1 fixes its core field names, tags, and validation semantics. Future optional
projection information uses independent namespaced extensions. Future material core semantic changes
require a new schema identifier and separately designed migration. This
contract neither reads nor upgrades `cozy.cml.model-metadata.v1` JSON/YAML and
does not change StateMachine/Workflow ABI outputs. `metadataPath` names a
dedicated future artifact destination only: this callable serializer/reader has
no filesystem writer, CML parser integration, CLI flag, or automatic emission.

## Phase 71 attribution retirement

Under [file update management](../spec/file-update-management.md), Phase 71
retires the non-integrity source digest while retaining
`cozy.cml.semantic-metadata.v1` and `cozy.cml.structure.v1`. This is an explicit
exception to historical all-core-changes-new-version wording: ordinary
SourceAttribution loading projects required `authorityId`, `path`, and `line`,
ignoring unknown source extras of any JSON type. Missing known fields remain
invalid; explicit `line: null` means no line, and supplied lines must be positive.
There is no legacy decoder, upgrade, alternate version, or fake value. All other
core shapes stay closed; identity, Presence, references, opaque extensions, and
genuine diagnostics retain their contracts. Attribution supplies authority,
canonical project-relative POSIX path, and optional line without hash, snapshot,
or currentness proof. Future material core semantics require independently
designed versioning. Canonical fixtures publish the retired field set; their
STR evidence remains synthetic supplier evidence, without external runtime or
CML integration acceptance.
