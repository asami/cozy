# Document Project Document and Summary Description DSL Design

Status: normative for Phase 71.1, P710-03-DM, DM05. This design supersedes the
Phase 58.1 V1 raw-byte identity design only; it does not alter V2 or later
Description, Reader, confirmation, vocabulary, SummarySlide, or media work.

The paired specification is the exact source for the V1 YAML examples, closed
key sets, reference object, rejection boundary, and command grammar. This
design records the responsibility boundaries and immutable intent behind those
shapes.

## Semantic responsibility boundary

The three semantic authorities are deliberately separate:

| Authority | Admitted source | Owns |
| --- | --- | --- |
| Content Core | direct `core.yaml` | Locale-independent Steps, claims, nodes, Relations, and Flows in the recursive logic tree |
| Document Description | direct `document.yaml` below a locale directory | Complete localized document title, recursive section organization, prose, and exact Core traceability |
| Summary Description | direct `summary.yaml` below a locale directory | Deliberate localized concise selection, order, wording, emphasis, and exact Core traceability |

Content Core owns locale-independent meaning. Document Description owns the
complete localized document authority. Summary Description owns a deliberate
localized selection and is not automatic Document truncation. Both are
semantic authoring DSLs, not HTML, CSS, PDF, PPTX, video, infographic, or
slide-layout intermediate representations.

V1 obtains Core through `CozyDocumentLogicTree.loadSourceCore`. `SourceCore`
supplies the existing semantic indexes used for typed reference resolution and
complete Document coverage. The V1 binding is direct logical `core.id`, and a
Summary additionally binds direct logical `document.id`. No adapter,
compatibility token, alternative model, freshness framework, or raw-byte
identity is introduced.

## Source, admission, and evolution policy

The source organization is `content/core.yaml` with
`content/<locale>/document.yaml` and `content/<locale>/summary.yaml`.
Admission takes explicitly supplied paths. Each source must be a direct
regular non-symlink file with its exact basename, and the explicit locale
must equal its direct parent directory. Strict UTF-8, duplicate-key rejection,
and rejection of YAML anchors, aliases, and explicit tags remain source
safety obligations.

At Document/Summary roots and `core`/`document` binding boundaries, only
required semantic fields are read. Additional properties—including historical
identities, opaque maps, arrays, or scalars—are ignored and never re-emitted.
This is the sole intentional open boundary. The paired specification keeps
`document`, `summary`, sections, blocks, list items, units, and `coreRefs`
closed with exact shapes; IDs, wording, typed references, uniqueness, and
Document coverage remain strict.

A valid edit to supplied Core, Document, or Summary bytes is immediately the
input for the next load/render. The loader does not compare a digest, make a
timestamp decision, persist a receipt, or reuse prior source/output content.

## Projection and CLI responsibility

Projection translates retained semantic records into self-contained
human-first Japanese review HTML. It preserves locale, hierarchy, authored
order, escaped wording, every block kind, typed reference data attributes,
selective Summary emphasis, and the visible distinction between local
Structure and direct-child Flow. Semantic IDs remain data attributes.
`Rendered` owns HTML only; no hash or identity appears in data attributes,
visible traceability, or command reporting. Determinism means equal semantic
input produces equal HTML bytes.

The closed CLI forms select Document or Summary rendering, report their
logical IDs and destination, retain initial safe destination admission, and
directly write UTF-8 HTML. Ordinary write failure reports `DESCRIPTION_PATH`;
partial output may remain and recovery is regeneration. No pre-move recheck,
staging, atomic replacement, rollback, or preservation guarantee is provided.
The command does not decide whether an output is production-current or
publish an output as an authority.

## Invariants and non-goals

Source safety remains strict: direct regular exact filenames, locale/path
agreement, strict UTF-8, duplicate-key rejection, and no YAML anchors, aliases,
or tags. Semantic validation remains strict for required fields, IDs, wording,
typed resolving references, uniqueness, block kind, emphasis, and Document
coverage. Historical extra properties at the declared open boundaries are
ignored without weakening nested grammar.

The executable specifications demonstrate the semantic load/render/CLI chain
with canonical no-hash authorities, ignored historical extras, changed valid
bytes, wrong logical-ID rejection, complete model and HTML equivalence,
source safety, escaping, deterministic HTML, initial destination admission,
and direct output semantics.

This DM05 slice is not V2 migration, stale-state architecture, timestamp or
receipt design, source generation, publication, deployment, or Phase closure.
