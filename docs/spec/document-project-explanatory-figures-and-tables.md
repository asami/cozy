# Document Project Explanatory Figures and Tables

Status: normative additive unreleased v2 contract; implementation in progress for [Phase 87](../phase/phase-87.md).
Recorded: 2026-10-25 (Asia/Tokyo).

This document freezes the Phase 87 source and rendering contract. P87-02
implements runtime/source admission, asset materialization and pure confirmation
and reader projections. P87-03 implements native confirmation command and
Document local-build integration. P87-04 native article/PDF continuity is
accepted after 44/44 specifications, actual paired PDF inspection and independent
Step review PASS. Phase closure remains pending.
Implementation acceptance is tracked only in the
[Phase 87 checklist](../phase/phase-87-checklist.md).

## 1. Purpose and current boundary

A figure or table is an authored explanation of a Core claim, displayed in
the corresponding body passage. It is not a large logic-tree node or an
unrelated attachment.

At Phase 87 entry, `CozyDocumentDescriptionV2.Block` and its loader admitted
only paragraph, list, example, note and logical-structure. P87-02 extends that
existing unreleased Document v2 model and both loader routes with explicit
`figure` and `table` blocks. Existing Summary `diagram` selects Core Steps,
Nodes and edges for a concise presentation; it is not a body figure or table. It preserves existing v2 documents
and the separate closed v1 contract; it introduces no parallel Document DSL.

The existing [v2 description contract](document-project-document-and-summary-description-v2.md),
[Core/Document/article authority](document-project-goal-driven-generation.md),
[confirmation contract](document-project-document-confirmation-renderer-v2.md)
and [reader contract](document-project-reader-projection.md) remain authoritative
outside the explicit narrow extension below. Their Phase 87 amendments
supersede only closed-kind, asset and pure-input clauses. Phase 71
hash-removal policy remains.

## 2. Semantic ownership

| Authority | Responsibility |
| --- | --- |
| Core | Locale-independent claim identities, compared subjects/axes represented by existing Nodes, and declared Relations. No captions, image paths, table layout or renderer settings. |
| Localized Document | Ordered figure/table blocks, exact claim correspondence, figure asset reference/caption/alternative text, and table headings/rows/cells. |
| Confirmation and reader HTML | Display the authored explanation beside its prose, expose exact Core correspondence and retain the existing selection/navigation behavior. |
| Derived SmartDox article and article PDF | Use the same authored figures and table content through the existing article-authoring and media-rendering routes. |

Phase 87 reuses the existing five-category `coreRefs` object. Every figure
and table must explicitly reference at least one resolving Core claim in
`coreRefs.claims`; Step, Node, Relation and Flow references retain their exact
typed meanings. A comparison's subjects, axes and relationships belong in Core
when they carry logical meaning, and may be included in the block's references.
There is no automatic extraction of claims or relations from an image, caption,
column name, cell value, position or nearby paragraph. Multiple explanatory
blocks may reference the same claim.

Existing Core modeling is reused. An image-asset registry, a table-layout model,
or localized table data must not be added to Core merely to render a block.
The logic tree keeps short authored labels. Figures and tables appear in the
selected claim's associated explanation region, without enlarging tree nodes.

## 3. Frozen Document block contract

Both new kinds participate in the existing ordered recursive Section blocks,
global section/block/list-item ID uniqueness, Core coverage, Document target
collection and Summary omission target resolution. They are ordinary `block`
targets for an authored Summary omission; no Summary figure selection is inferred.

The following exact source shape is frozen by P87-01 and implemented by
P87-02 with executable specifications and both loader routes. No other fields are
admitted.

| Kind | Required fields |
| --- | --- |
| `figure` | `id`, `kind`, `asset`, `caption`, `alt`, `coreRefs` |
| `table` | `id`, `kind`, `caption`, `columns`, `rows`, `coreRefs` |

Figure `asset` is an explicit project-relative authored image reference.
`caption` and `alt` are separate, nonblank localized strings: the caption
explains the figure in the article; alternative text conveys its relevant
meaning without requiring sight. A filename is not an inferred alternative.

A table column is `{ id, heading }`; a row is `{ id, cells }`. Column and row
IDs use the existing stable-ID grammar and are unique within their respective
table-local collections. Headings and caption are nonblank localized strings.
Columns and rows are nonempty and retain author order. Each row has exactly
one string cell per declared column, in that order. An explicitly empty string
is an authored empty cell; an absent cell or implicit numeric/boolean coercion
is not. Plain text is the initial cell contract. Merged cells, formulas, nested
blocks, arbitrary HTML/CSS and inferred column/row structure are out of scope.

For example, the tier comparison figure references `claim-tier-balance`
(performance and security at each tier); the protection matrix references
`claim-protection-coverage` (protection target, mechanism and application
location). Those claims and any compared-axis Nodes must be authored in the
selected Core first. These examples specify correspondence, not technical
claims about a particular product or tier.

Japanese and English Documents explicitly declare their captions, alternative
text, headings, cells and asset references. Equivalent explanations retain
corresponding block/column/row identities and Core claims; translated image
assets may differ. There is no locale fallback, automatic translation or
byte-equality requirement. Each locale's authored order is preserved, and
semantic differences require content review rather than a hash comparison.

## 4. Assets, rendering and dependency ownership

Asset admission uses the explicit project root supplied by the selected
operation. It must resolve only declared contained image inputs, preserving
ordinary path/file/I/O checks. Absolute paths, traversal, symlinks and remote
URLs are not body-asset sources. No network fetch is implied by rendering.
Only PNG (`.png`) and JPEG (`.jpg` / `.jpeg`) are admitted. Extension,
signature and successful standard ImageIO decoding must agree. SVG is rejected
at runtime, including the fixture authoring masters. Parent directories must
be direct and contained under the explicit fixed project root; missing, unsafe,
unsupported or unreadable inputs fail explicitly.

Resolve actual image inputs before the pure HTML projections. The projections
must not acquire filesystem/network access. Confirmation and reader HTML keep
their portable, self-contained behavior by embedding admitted inert image
content through an explicit asset-materialization boundary. Materialization supplies immutable values containing exact declared references,
embedded data URIs and actual input paths. Pure renderers consume those values
and perform no filesystem or network access. Source-compatible two-argument
render calls supply an empty asset collection and fail explicitly when a
Figure needs an asset; no JVM binary overload compatibility is promised.
SVG requires a separately planned safe route; no sanitizer is introduced.

The native confirmation `render --kind document` command accepts optional
`--project-root <path>`. Its absence remains compatible for asset-free and
table-only Documents; a Figure without a root fails before output with
`CONFIRMATION_CLI` and `--project-root is required for figure assets`. A supplied
root is admitted by the asset boundary, without current-directory inference;
asset diagnostics retain their original code and path. `--kind summary`
rejects `--project-root` because it has no image projection.

P87-03 local-build supplies the explicit project root, admits the selected
Document and materializes its used images before freshness evaluation. It
retains the admitted Document and materialized values in the Plan for both
native Document renderers, without repeated parsing or materialization.

Render figures as semantic `figure`/`figcaption` with authored alternative text,
and tables as semantic `table`/`caption` with explicit column-header semantics.
Preserve text escaping, meaningful reading order, keyboard navigation, print
output and exact `data-core-*` membership on both block kinds. Large tables
must remain readable without truncating cells or the article. Core selection
must highlight/reveal the authored explanatory blocks using exact typed
references; no text/proximity matching is allowed. Reader backlinks must reach
the corresponding figure/table block.

Retain local-build configuration’s exact existing authored source inputs.
Discover only raster files used by the selected admitted Document or parsed
article AST and append them to Plan inputs before freshness evaluation. No
asset-directory globs or extra configuration input list is admitted.
Declare figure files as actual inputs to the selected Document HTML targets.
For `smartdox-article-html` and article PDF, declare the assets actually used by
their article source and carry them through existing resource/output handling.
Keep the [local-build dependency contract](document-project-local-build-targets.md):
a Document-only edit does not itself rebuild a SmartDox article whose own
source and used assets are unchanged. Updating a used image must invalidate
the affected target under its existing reuse policy; an unselected asset must
not become a dependency just because it exists in the project.

Use ordinary direct output and existing target-local reuse rules. This feature
adds no management hash, sealed asset snapshot, global lock, atomic-install
protocol or duplicate preservation gate.

## 5. Article and PDF continuity

The localized Document remains the prose/figure/table authority. Its accepted
content is reflected into `index.dox` through the existing article-authoring
route. Use SmartDox's actual supported image/table representations; do not
create a second handwritten HTML article or claim that Document confirmation
alone updates `index.dox`.

The existing native SmartDox article HTML and `article-pdf` route must consume
the reflected content and declared resources. The resulting article and PDF
must retain figure identity/meaning, the selected localized asset and caption,
table headings/cells/order, and their Core correspondence. Format-appropriate
image derivatives and PDF pagination may differ; independently authored
replacement explanations, dropped rows and invented content are not allowed.

The native article adapter prepares `index.dox` once with
`Dox2Parser.parseWithFilename(Config.default, "index.dox", source)`. It discovers
actual `Img.src` nodes by depth-first traversal of `Dox.elements`, admits only
relative local URIs without scheme, authority, query or fragment, and decodes
each URI path once. The shared PNG/JPEG materializer admits those selected
paths against the canonical explicit project root before freshness evaluation.
Repeated references produce one input in first-use order. Missing or unsafe
used images fail even when an existing article output would otherwise be
current.

`Prepared` retains the source path, native Document and immutable materialized
assets. Rendering uses SmartDox's default `Dox2DomHtmlTransformer` rule and
context, changes only native DOM image `src` attributes to the admitted data
URIs, and serializes with the existing `HtmlTransformerBase`. It preserves the
native article metadata, ordinary links, captions and complete table structure.
It does not rebuild the Dox AST, copy assets or write outputs. `Result.assets`
reports actual selected image paths. The source-compatible `render(project)`
entry delegates to preparation and rendering of that retained value.

Article PDF remains owned by `CozyMedia.build` and `CozyMediaPdf`. Its ordinary
`cozy.media.v1` descriptor declares a same-locale prebuilt infographic and a
distinct `article-pdf` resource with `articleMedia.role: article_pdf`, PDF media
type, business LaTeX format and the native SmartDox renderer. The selected
figure is an explicit `receipt.inputs` entry with role `figure` and
normalization `bytes`. This uses the existing declared-input/currentness
mechanism; it adds no PDF cache or source-digest protocol. `publicPath` values
are metadata and do not publish anything.

Phase 87 must demonstrate this complete route for one paired JA/EN tier
comparison figure and one protection-coverage table. Real files must be read
and actual selected article PDFs inspected; a source-string assertion or a
manually substituted final HTML/PDF is not acceptance. Reuse existing supported
tools. A concrete missing upstream capability must be reported with its owner
and exact gap, not worked around by silently changing another repository.

## 6. Required executable and visible acceptance

Required executable and workflow specifications cover:

- both block kinds in nested sections, source ordering and shared-claim use;
- exact claims and typed references, duplicate IDs, unknown fields/kinds,
  unresolved/mistyped references and missing claim membership;
- nonblank figure caption/alt, missing/unsafe/unsupported assets and ordinary
  read failures, without broad defensive machinery;
- ordered table columns/rows, empty strings versus missing cells, duplicate
  local IDs, malformed cell shapes and Unicode/escaping;
- figure/table coverage and omission targets without inferred Summary content;
- real asset materialization, deterministic self-contained HTML, native table
  semantics, precise highlighting/reveal and reader backlinks;
- JA/EN correspondence with explicit localized assets and wording;
- declared used-asset rebuilds and unaffected unselected assets/targets;
- actual SmartDox article and article PDF continuity with visible image/table
  inspection, plus preservation of existing documents without the new blocks.

Use the repository's Given/When/Then, `should` and appropriate property-based
specification style. Lightweight semantic/HTML regressions remain in normal
FULL; expensive native PDF or large-generation cases follow the existing
HEAVY policy but the selected representative PDF acceptance remains required.
P87-01 authored the sources only. P87-02 supplies the admission/materialization
and pure-projection specifications; P87-03 adds native command/build coverage
in `CozyDocumentFigureTableBuildSpec`, including controlled timestamps and an
actual valid raster-pixel change. Article/PDF acceptance remains assigned to
P87-04. Its authored paired articles are
[Japanese](../../src/test/resources/cozy/document/phase-87/tier-protection/articles/ja/index.dox)
and [English](../../src/test/resources/cozy/document/phase-87/tier-protection/articles/en/index.dox).
Fixture assembly copies each selected article and its media descriptor to a
fresh project root, alongside the actual Core, localized Documents and images;
the authored image paths remain relative to that assembled root.

`CozyDocumentFigureTableArticleSpec` exercises the actual parser, native DOM
rendering and used-image local-build behavior in ordinary FULL. The single
paired scenario in `CozyDocumentFigureTablePdfSpec` is tagged
`org.scalatest.tags.Slow` and requires explicit HEAVY selection. It requires
the real `target/cozy.d/runtime-classpath.txt` export and configures only the
copied media descriptors with the current Java executable, `-cp`, that actual
classpath and `org.smartdox.service.SmartDoxService pdf`. Normal
`CozyMedia.build` invokes the native service through its default process runner;
no installed PATH wrapper or substituted PDF producer is used.

The native scenario retains real JA/EN projects and PDFs under
`target/phase87/pdf-acceptance/<uuid>/`, checks pages, recursive PDF image
resources and full readable localized content with PDFBox, observes the exact
used inputs, demonstrates reuse and unrelated-source independence, and
actually rebuilds English once after a valid raster-pixel change. The ordinary
local index `target/phase87/pdf-acceptance-latest.json` records actual paths,
counts and reports for parent inspection. Missing tool/runtime prerequisites
fail normally with retained diagnostics; they are not canceled or replaced by
mock PDF bytes. On 2026-10-25 the selected HEAVY scenario passed with actual
JA/EN PDFs, one English raster rebuild and full text/image/input checks. Both
final PDFs were visually inspected by the parent and independent Step reviewer.
Native English business-format automatic 図/表 prefixes remain
[DEV-P87-001](../journal/2026/10/2026-10-25-phase-87-development-candidate-follow-up.md);
authored localized captions and cells are preserved. No English automatic-chrome
acceptance or unexecuted HEAVY combination is claimed.

Each Step's validation and acceptance are recorded by the parent in
the Phase checklist, not inferred from this capability description.

The [decided design and source inventory](../design/document-project-explanatory-figures-and-tables.md)
and [representative Core](../../src/test/resources/cozy/document/phase-87/tier-protection/content/core.yaml)
record the selected route and schematic example. Native parsing and PDF
execution remain required before article syntax or support is accepted.

## 7. Non-goals

No chart/diagram authoring engine, spreadsheet calculations, figure OCR,
automatic claim extraction, Summary/slide/video expansion, publication/upload,
external-project edits, new workflow skills, or reopening closed Phases.
