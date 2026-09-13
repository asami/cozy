# Document Project Confirmation Vocabulary and Driver v2 Design

Status: normative for Phase 58.2, Step P582-05, Slice P582-05A

This design introduces the production boundary that supplies generic review
wording to the already accepted v2 Document and Summary confirmation renderers.
It does not alter v1 descriptions, their loader/projection/CLI route, the v2
semantic admission model. The bounded heading and inspector refinements
follow the paired renderer presentation contracts only; accepted slide
presentation remains unchanged.

## Authority and vocabulary seam

`cozy.document-confirmation-vocabulary.v1` is a direct regular YAML resource
named `confirmation-vocabulary.yaml`. Its closed shape contains an explicit
locale, generic Document review chrome and controlled terms, and generic
Summary review chrome and controlled terms. It has no project identifier,
Step label, node label, editorial wording, Summary unit, layout, coordinate,
HTML, or CSS field.

`CozyDocumentConfirmationVocabulary` admits only UTF-8, non-symlink direct
files without YAML aliases, anchors, explicit tags, duplicate keys, unknown
fields, missing controlled terms, or blank wording. The resource locale must
equal the admitted v2 Document or Summary locale. It adapts the resource only
to the two renderer `Vocabulary` types; it never changes the admitted model,
supplies project wording, substitutes an ID, or falls back to English.

Both private chrome adapters require `pageHeading`. Japanese generic screen
labels are `文書確認` and `要約確認`, while Summary's existing
`sourcesHeading` becomes `元情報と編集判断`. These two required keys extend
only the still-unreleased internal generic presentation vocabulary under its
unchanged exact resource schema identity. Closed missing/unknown-field
rejection remains; no compatibility adapter, optional fallback, protocol
migration, domain authoring change, or public CLI/API extension is added.

Summary also receives existing Document pattern wording and required closed
inverse Relation/Flow wording maps. The resource schema remains the internal
still-unreleased v1 identity; all newly required keys reject when missing.
This supersedes the prior two-key-only restriction, without locale fallback,
semantic schema changes, or Article-specific terms.

## Command and publication seam

All four existing fixed-Catalog Logical Patterns, seven node roles, and five
Relation/Flow types reach the generic adapter, including `sequence`, `step`
and `next`. Both canonical and inverse maps require that complete type set.
This corrects the prior three-pattern/four-type subset without changing the
Catalog, source meaning, fallback rules, or the closed v1 authoring behavior.

`document-project confirmation render` is a closed command with `--core`,
`--document`, `--vocabulary`, `--kind`, and `--save`; `--summary` is required
only for `--kind summary`. It first admits strict v2 source identities, then
the locale-compatible generic vocabulary, and calls the matching accepted v2
renderer. The command publishes only an absent or direct regular non-symlink
`.html` file under an existing direct non-symlink directory, using one atomic
move. No command or renderer infers semantics.

## Article 9 driver boundary

The `content-v2` test fixture is a real Japanese Article 9 v2 source bundle.
Its Document owns all localized Step/node labels and prose; its Summary owns
all unit editorial content, ordering, retained points, diagrams, and
omissions. The separate generic Japanese resource contains no Article 9
labels or prose. The driver exercises repeated deterministic renderings,
identity traceability/currentness, native selection DOM, strict vocabulary
rejection, command closure, and unsafe-output rejection.

## Non-goals

This boundary adds no Core field, source semantic field, v1 conversion,
export/API/SPI route, physical-layout authoring input, external publication,
or generated-HTML authority.
