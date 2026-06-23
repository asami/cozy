# Descriptive Effective Semantics

status=design
published_at=2026-06-23

---

# Overview

CNCF, SmartDox, Cozy, and related SimpleModeling tools share one descriptive
metadata vocabulary. SmartDox is the semantic reference implementation because
it owns document metadata parsing and Dox IR normalization.

The common descriptive fields are:

- `headline`
- `brief`
- `summary`
- `description`
- `lead`
- `abstract`
- `remarks`
- `tooltip`

`org.goldenport.value.DescriptiveAttributes` in goldenport-scala-library carries
this common field set and provides the core effective accessors. SmartDox
`Explanation` / `DocumentMetaData` are the richer document-facing model and must
remain behaviorally compatible with these effective rules.

# Field Intent

| Field | Intent |
| --- | --- |
| `headline` | Short display headline. It is presentation-first and usually appears as a title-like line. |
| `brief` | Very short explanation for cards, tooltips, lists, and dashboard leads. |
| `summary` | Compact explanatory summary for catalog and metadata consumers. |
| `description` | Longer descriptive prose. It may be rendered as body-like metadata. |
| `lead` | Introductory lead paragraph for article or page rendering. |
| `abstract` | Abstract-style summary, often imported from publication or research metadata. |
| `remarks` | Supplemental remarks, not normally used as fallback text. |
| `tooltip` | UI tooltip or hover text. |

# Effective Rules

Effective values are consumer-specific. They must not collapse all descriptive
fields into one generic text.

| Effective accessor | Precedence |
| --- | --- |
| `effectiveHeadline` | `headline -> brief -> tooltip` |
| `effectiveBrief` | `brief -> summary -> lead -> abstract -> headline` |
| `effectiveSummary` | `summary -> lead -> abstract -> description -> brief` |
| `effectiveDescription` | `description -> abstract -> lead -> summary` |
| `effectiveTooltip` | `tooltip -> brief -> headline -> summary -> abstract` |

Explicit metadata wins over text distilled from body content. If a parser
creates a distilled summary from body text, consumers that need authored
metadata should first inspect the explicit metadata properties before falling
back to distilled effective values.

# Product Responsibilities

- SmartDox: parses SmartDox and Markdown sources into Dox IR, owns
  `Explanation` / `DocumentMetaData`, and is the behavioral reference.
- goldenport-scala-library: provides reusable `DescriptiveAttributes` and
  effective accessor semantics for non-Dox metadata consumers.
- CNCF: projects component, service, operation, and entity metadata using the
  same descriptive field names and effective semantics.
- Cozy: consumes SmartDox/Dox metadata for BoK dashboards, publication registry,
  term hubs, RDF views, and generated pages. Cozy should not invent a separate
  effective order.

# Glossary And Markdown

Glossary term sources may be SmartDox or Markdown. Markdown sources are parsed
through SmartDox Markdown mode and normalized into Dox IR. Term title, reading,
summary, and description metadata must be resolved through the same descriptive
metadata path as article pages.

For glossary terms:

- `title` / document title defines the term display name.
- `reading` is term-specific metadata and is not part of the generic
  descriptive field set.
- `summary` is the preferred short machine-readable term summary.
- `brief` is acceptable and may feed effective summary when explicit `summary`
  is absent.
- body content remains the term definition / descriptive body.

