# Explanation Preview Specification

## Scope

This specification defines the `cozy media explanation preview` command and
its generated-review success report. It consumes authoritative Explanation and Visual
Page documents but does not define a new semantic model.

## Invocation

```text
cozy media explanation preview <plan> \
  --composition <composition> \
  --projection-map <projection-map> \
  --explanation-catalog <explanation-catalog> \
  --presentation-catalog <presentation-catalog> \
  --visual-page-set <visual-page-set> \
  --save <review.html> \
  [--source <id>=<file>] [--asset <id>=<file>]
```

`<plan>` precedes all options. Each required option occurs exactly once.
Every value option accepts both `--option <value>` and `--option=<value>`.
`--source` and `--asset` follow the existing Explanation direct-binding
contract and may repeat only for distinct declared identifiers. `--save` is
required and names a direct `.html` file. Unsupported, duplicated, valueless,
or positional options fail deterministically. `--save` MUST NOT alias any
named direct document, explicit source or asset binding, or resolved mapped-Page
descriptor resource. Normalized same-path aliases and existing filesystem
aliases MUST be rejected before output replacement.

## Required Input Agreement

Before writing output, Cozy MUST validate all of the following:

- Composition and Plan preserve the selected authored meaning and actual
  catalog ID/revision and pattern references under the Explanation contracts.
- Projection Map agrees with the named Plan and catalog selectors;
  its presentation mappings contain every Plan Step exactly once in Plan
  order, and every mapping has at least one Page ID.
- Visual Page Set is `cozy.visual-page-set.v1`; its named catalog ID and revision
  equal the Plan's presentation catalog selector, and its typed graphs, visual
  parameters, and references validate against that catalog.
- Every mapped Page ID exists exactly once; a mapped Page's Logical Pattern is
  exactly the Plan Step's Logical Pattern; and source and asset provenance
  required by the Step are present on that Page.
- Every required source ID is declared in the Composition and resolves to an
  explicit safe direct source binding and a safe descriptor-relative resource.
- Every required asset ID is declared in the Composition and resolves to an
  explicit asset binding; its descriptor mediaType equals the AssetDeclaration
  mediaType; the binding and descriptor-relative resource independently resolve
  to safe direct files.

Missing, duplicate, unsafe, malformed, unknown, incompatible,
semantically mismatched, or stale input MUST fail before output replacement.

## HTML Requirements

The output MUST be one UTF-8, self-contained HTML document. It MUST contain
the provenance header, the full ordered explanation flow and
Step-to-Page mapping, and every mapped Page's semantic role, claims, Logical
Pattern, typed Relations, Visual Pattern, typed parameters, sources, assets,
and source/asset references. The header MUST show the renderer profile,
Composition ID, Explanation Catalog ID and revision, Presentation Catalog ID
and revision, Visual Page Set ID, Step count, and reviewed Page count. Catalog
IDs and revisions refer to the declared catalog revisions. String
values MUST be HTML-escaped. Asset details contain ID, mediaType, and path.
Static HTML MUST retain that information without
JavaScript. The page MUST use no CDN, remote asset, external font, external
script, or Web service.

The output MUST use an ordinary direct UTF-8 write to the admitted selected path. Identical accepted inputs and preview
renderer/profile MUST yield byte-identical output.

## Success Report

The success report MUST declare, in this exact order:

```text
schema: cozy.explanation-preview.v1
version: 1
generator: cozy media explanation preview
rendererProfile: cozy.explanation-preview.renderer.v1
compositionId: <Composition ID>
explanationCatalogId: <Explanation Catalog ID>
explanationCatalogRevision: <Explanation Catalog revision>
presentationCatalogId: <Presentation Catalog ID>
presentationCatalogRevision: <Presentation Catalog revision>
visualPageSetId: <Visual Page Set ID>
stepCount: <Plan Step count>
pageCount: <rendered mapped-Page occurrence count>
html: generated
status: generated
```

The fields MUST be joined by LF in the order above. `pageCount` counts rendered
mapped-Page occurrences, including a Page reviewed for more than one Step;
unmapped Pages do not contribute. The report MUST contain no output target
path, generated time, or artifact digest. It remains identical when a valid
presentation change preserves the reported semantic IDs, revisions, and counts,
even though the actual HTML bytes change. The command always renders the
validated current inputs and computes HTML and report before the selected direct
write. Validation or rendering errors before writing preserve the prior selected
output. Ordinary write failures may leave partial output; regeneration is recovery. Generated HTML is review evidence only and is never an input to Composition, Plan,
Projection Map, Visual Page Set, catalog, PDF, PPTX, or video generation.

## Compatibility

`cozy media visual-page preview` retains its current grammar, schemas, semantic
HTML, and current success report with `schema`, `version`, `generator`,
`documentId`, `catalogId`, `catalogRevision`, `pageCount`, `html`, and `status`
in order for HTML-only output. This command is an additional multi-page
explanation-aware consumer and does not replace it.

## Phase 71.5 bounded migration

S07 removes Preview's byte-copy agreement and redundant whole-catalog equality
certificate, and reconciles the Explanation/Projection models and direct writer.
Named catalog ID/revision, typed graphs, required declaration IDs, asset media
types, direct descriptor/binding resolution, actual input/output alias ownership,
HTML escaping, and the independent success report facts remain required.
On 2026-10-08 S07's four existing focused suites completed with 41 successes,
zero failures, canceled, ignored, or pending cases, no warnings, SBT/wrapper
exit 0, and terminal shared serial-lock release. Independent review and Step/Phase
acceptance remain pending. S08 implements the bounded VisualPage conversion and direct semantic Preview
writers. On 2026-10-08 its two existing focused suites completed with 14 successes,
zero failures, canceled, ignored, or pending cases, zero warnings/errors,
SBT/wrapper exit 0, and terminal shared serial-lock release. S09 implements bounded
direct Article/Summary PDF generation and immediate build-input capture removal.
On 2026-10-08 its six existing focused suites completed with 114 successes,
zero failed/canceled/ignored/pending cases, zero aborted suites, zero
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
S10 removes Presentation copied-image byte/multiset checks and shared acceptance
staging/backup/rollback, retaining initial asset/target ownership and actual
structural semantics with ordered direct review-state then receipt writes.
On 2026-10-08 its six existing focused suites completed with 113 successes,
zero failed/canceled/ignored/pending cases, zero aborted suites, zero SBT
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
Five hostile-XML/DOCTYPE diagnostics are intentional existing negative-fixture
output, not SBT compilation or test failures.
S11 implements direct PresentationMigration and CrossReview output writes and
direct creation of the absent article source package. Its five-suite focused
validation completed on 2026-10-08 with 58 successes, zero
failed/canceled/ignored/pending cases, zero aborted suites, zero SBT
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
S11 emitted five intentional hostile-XML/DOCTYPE negative-fixture diagnostics,
separate from SBT compilation or test failures. S12's current JA native route is
planned/not-run; generation, assembly, native inspection, browser EOF and current
listening remain OPEN ([journal](../journal/2026/10/2026-10-08-phase-71.5-ja-main-route.md)).
Remaining Receipt publication/prepared-input
observations and finite publication/runtime/CAR groups, independent
review/Step/Phase acceptance, the same full 34-scene native route, and external
E715-01..04 consumer reconciliation remain pending without an exception or
whole-Phase closure claim.
