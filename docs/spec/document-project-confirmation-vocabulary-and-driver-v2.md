# Document Project Confirmation Vocabulary and Driver v2 Specification

Status: normative for Phase 58.2, Step P582-05, Slice P582-05A

## Strict generic resource

The resource schema is exactly `cozy.document-confirmation-vocabulary.v1`.
It MUST be a direct non-symlink `confirmation-vocabulary.yaml` UTF-8 file in
an explicit locale directory. Its top-level structure is exactly `schema`,
`locale`, `document`, and `summary`; nested fields and controlled vocabulary
maps are closed. Every wording is nonblank and trimmed. YAML aliases,
anchors, explicit tags, duplicate/lossy input, unknown fields, unsafe paths,
or a locale unequal to the bound admitted v2 source MUST reject before
rendering.

The resource MUST contain only generic chrome and generic controlled wording.
It MUST NOT contain Article/project identity, localized Step/node labels,
Document/Summary prose, Summary units, HTML, CSS, layouts, or coordinates.
The loader adapts it only to the accepted v2 renderer vocabulary types and
MUST NOT modify inputs or invent fallback wording.

Both closed private chrome maps MUST require nonblank trimmed `pageHeading`.
The Japanese generic values are `文書確認` and `要約確認`; Summary
`sourcesHeading` is `元情報と編集判断`. Only these two required keys extend
the still-unreleased generic presentation resource. The resource schema
identity remains exact, with missing and unknown keys rejected; no optional
fallback, compatibility adapter, protocol migration, domain authoring schema,
or public CLI/API change is introduced.

## Closed confirmation command

The only admitted grammar is:

`document-project confirmation render --core <core.yaml> --document <document.yaml> --vocabulary <confirmation-vocabulary.yaml> --kind <document|summary> --save <output.html>`

For `summary`, exactly one `--summary <summary.yaml>` is additionally
required; for `document`, `--summary` is rejected. Unknown, duplicate,
missing, cross-kind, or positional command forms reject without publication.
The command MUST use strict v2 source admission and current byte identities,
then locale-compatible vocabulary and the matching v2 renderer. It MUST
publish only an atomically moved direct regular non-symlink HTML destination
under a direct existing directory.

## Executable acceptance evidence

`CozyDocumentConfirmationVocabularySpec` proves strict schema/path/YAML/
locale/wording admission and the generic-only adapter. `CozyDocumentConfirmationDriverSpec`
uses the Article 9 v2 Japanese bundle to prove both confirmation kinds render
twice to identical bytes and disclosed identities; exact v2 currentness and
traceability; accepted native selection markup; strict generic-resource and
command rejection; safe publication rejection; and absence of Article 9
Step/node/prose maps from the production generic resource. These specs use
`AnyWordSpec`, adjacent Given/When/Then clauses, and `should` matchers.

## Compatibility boundary

No behavior of `document-project description render`, the v1 schema/loader/
projection, export code, or generated HTML authority is changed. The v2
renderers' generic heading and readable primary/audit presentation change
only as specified by their paired presentation contracts; admitted source
models, authored identities, selection, and accepted Summary slides remain
unchanged.
