# Document Project Explanatory Figures and Tables Design

Status: decided Phase 87 boundaries; implementation accepted.
The [normative contract](../spec/document-project-explanatory-figures-and-tables.md)
owns behavior; the [Phase checklist](../phase/phase-87-checklist.md) owns acceptance.
This inventory records current source capabilities and remaining integration
ownership; it does not claim Phase closure or article/PDF acceptance. The
five-kind inventory at Phase entry is historical, not the current loader limit.

## Source inventory and responsibility

| Source | Existing boundary and required extension |
| --- | --- |
| [Document v2 model/loader](../../src/main/scala/cozy/document/CozyDocumentDescriptionV2.scala) | At Phase entry: five closed block variants. P87-02 implements Figure/Table and their recursive traversal, exact typed correspondence, coverage and both runtime/source loader routes under the existing unreleased v2 schema. |
| [Confirmation projection](../../src/main/scala/cozy/document/CozyDocumentConfirmationProjectionV2.scala) | P87-02 implements pure admitted-value Figure/Table HTML with immutable materialized assets, exact Core selection and shared selection/reveal behavior. |
| [Reader projection](../../src/main/scala/cozy/document/CozyDocumentReaderProjectionV2.scala) | P87-02 implements pure Figure/Table HTML with the same asset values, native semantics, complete cells and exact backlinks. |
| [Confirmation command](../../src/main/scala/cozy/document/CozyDocumentConfirmationCommand.scala) | P87-03 implements optional `--project-root` for Document rendering, asset materialization and the pure renderer call. Missing roots diagnose Figures before output; table-only/asset-free calls remain source compatible. Summary rejects the unused root. |
| [Local-build command](../../src/main/scala/cozy/document/CozyDocumentProjectLocalBuildCommand.scala) | P87-03 retains exact authored configuration inputs and appends only the selected Document's actual used raster paths before freshness/collision checks. P87-04 adds the prepared native article model and its actual used images to the article Plan, independently of Document inputs. |
| [Native SmartDox article HTML](../../src/main/scala/cozy/document/CozyDocumentProjectSmartDoxArticleHtml.scala) | P87-04 prepares the native `Dox` once, materializes actual depth-first `Img.src` references, attaches admitted data URIs only to native DOM image sources, and uses the existing HTML serializer. Result reports actual used paths without copying assets. |
| [Existing article PDF](../../src/main/scala/cozy/media/CozyMediaPdf.scala) | Existing `article-pdf` media route and resource contract remain the PDF owner. Use this native route with reflected article content; no alternate PDF engine. |

The outside sources below are read-only dependencies in the adjacent SmartDox
repository; no upstream edits are authorized.

| Upstream source | Source capability and acceptance limit |
| --- | --- |
| [Dox model](../../../smartdox/src/main/scala/org/smartdox/Dox.scala) | Native Img, Figure and Table forms exist. Consume these forms through existing transformations. |
| [DoxLinesParser](../../../smartdox/src/main/scala/org/smartdox/parser/DoxLinesParser.scala) | `get_Image_Transition` recognizes image input; the annotation state wraps an image in Figure when a caption is present. Actual authored syntax must still be parsed through the production reader. |
| [Dox2LatexConverter](../../../smartdox/src/main/scala/org/smartdox/converters/Dox2LatexConverter.scala) | `enter_Figure` emits image/caption/optional label; `enter_Table` uses longtable. `_image_source_file` and the resource-root route resolve local raster resources for image inclusion. Actual JA/EN PDF execution and final visual inspection passed in P87-04 (44/44 selected specifications; independent Step review PASS). |

No final syntax or end-to-end support claim follows from commented upstream
tests. P87-04 must parse the actual sources and render the actual PDFs. A real
upstream gap stops at its owning repository rather than authorizing a Cozy
workaround.

## Decided model and materialization boundary

Core contains the existing semantic Steps, claims, Nodes, Relations and Flows.
Localized Documents contain ordered Figure/Table blocks and their exact claims,
asset references, captions, alternative text and cells. Summary diagrams retain
their separate explicit semantic selection. No presentation fields enter Core.

Only project-contained PNG and JPEG runtime inputs are admitted, with matching
extension/signature and successful ImageIO decoding. Use a fixed explicit root,
direct contained parents and regular non-symlink files. Reject absolute,
traversal, symlink and remote references and SVG. SVG fixture files are authoring
masters only; a future safe SVG runtime route requires separate planning.

P87-02 implements materialization before pure projection, returning immutable
exact declared references, inert embedded data URIs and actual input paths.
Pure renderers do no filesystem or network work. Source-compatible two-argument
render calls use empty assets and fail explicitly for missing Figure values;
no JVM binary overload compatibility is promised. Figure/Table branches cover
the existing Document consumers. P87-03 implements native command and Document
local-build integration; validation and acceptance remain parent-owned.

Used Document assets extend only selected Document targets. The P87-03 Plan
admits and materializes once, before freshness even for a reusable output, and
renders from retained values. Table-only Documents add no image inputs.
P87-04 extends article inputs from actual parsed article image nodes. Keep exact existing authored configuration
inputs; append used raster paths before freshness evaluation without globs,
extra configuration asset lists or Document-to-article dependency leakage.
Ordinary output/reuse policy remains unchanged, without new management hashes
or output-preservation protocols.

## Representative authored source

The [Core](../../src/test/resources/cozy/document/phase-87/tier-protection/content/core.yaml)
uses a root mapping: both tier sources map to the performance/security target,
and a protection target maps to mechanism and location. It has two claims,
one empty root Flow and no child Steps. This is a meaningful schematic of
comparison dimensions and responsibility coverage, not measured performance,
actual product-tier descriptions or a protection guarantee.

The paired [Japanese Document](../../src/test/resources/cozy/document/phase-87/tier-protection/content/ja/document.yaml)
and [English Document](../../src/test/resources/cozy/document/phase-87/tier-protection/content/en/document.yaml)
share section/block/column/row identities and exact Core references. Each has
explicit localized wording and a selected complete PNG diagram; corresponding
SVG masters remain outside runtime dependencies. Each locale's vocabulary
retains the full existing confirmation schema without fallback. The root
section references every Core identity; Figure and Table each reference their
own resolving claim and the shared root Step.

These authored fixture sources now feed P87-02 admission/materialization and
pure-projection specifications and P87-03 native command/build specifications.
The latter use copied JA/EN fixtures under `target/phase87/spec`, exact selected
input reports, controlled timestamps and changed valid raster pixels. The
adopted diagrams remain authored locale content; only work-fixture images are
mutated. P87-04 adds paired authored `articles/<locale>/index.dox` and
`media.yaml`. The sources retain the Document's full localized disclaimer,
figure caption, nested headings, complete table caption, column order and two
rows. Source Figure/Table `#+LABEL` declarations retain origin block identities
for authored correspondence. The native parser retains the Table label but does
not propagate the Figure label; no extra HTML metadata or PDF anchors are
promised. Ordinary paragraphs use no annotation.
The introduction paragraph marks its complete localized text with native
`<span lang="ja">` or `<span lang="en">` body inline syntax for the existing
native `--locale` selection. Section titles do not parse inline HTML markup.
Locale directories and media language alone do not satisfy that source contract;
actual PDF execution and visual acceptance remain required.
Core correspondence remains compositional through admitted Document claims and
these same explanations. Article reflection uses the existing authoring route,
with no second generator or correspondence DSL.

## Native article attachment and PDF acceptance boundary

`Prepared(sourcePath, document, assets)` is owned by the native article adapter
object. Preparation preserves the existing filename-aware parser and discovers
image references from actual `Dox.elements` in depth-first first-use order.
URI admission rejects schemes, authority, absolute paths, queries and fragments;
`URI.getPath` decodes once before the shared canonical-root PNG/JPEG materializer.
The original native Dox model is retained, without generic `copyV` AST rewrites.

Rendering creates the DOM with the same default SmartDox context and
`Dox2HtmlTransformer.Rule.default.toDox2DomHtmlTransformerRule`. It attaches
admitted data URIs to each native image using its exact source URI mapping,
then calls `HtmlTransformerBase.to_html`. Other DOM content remains native.
The local-build Plan retains this prepared article before freshness, appends
only actual used raster paths and renders from those values. This is independent
of Core, localized Document, unselected images and SVG masters.

PDF resource declarations use ordinary `cozy.media.v1`, the existing
`CozyMediaPdf` producer and explicit selected-figure `receipt.inputs`. No PDF
projection, producer, receipt ABI or cache is added. The checked-in descriptors
use `dox pdf`; the explicit HEAVY acceptance changes only the copied descriptor
renderer command to current Java plus the actual exported development classpath
and `org.smartdox.service.SmartDoxService pdf`. This resolves the native runtime
explicitly without depending on the installed legacy PATH wrapper.

Fast article specifications assemble projects under
`target/phase87/spec/<uuid>/<locale>.dox`. The single paired native PDF scenario
is tagged Slow, requires `cozyExportRuntimeClasspath`, invokes normal
`CozyMedia.build` with its default process runner and retains actual JA/EN PDFs
under `target/phase87/pdf-acceptance/<uuid>/`. PDFBox observes real page/image
counts and readable complete content. The scenario demonstrates current reuse,
Document/unselected-asset independence and one actual English raster rebuild.
`target/phase87/pdf-acceptance-latest.json` records paths, counts and producer
reports for visual inspection, without hashes or copied-source evidence.
Native external tool failures remain test failures with retained diagnostics.
On 2026-10-25 the complete selected run passed 44/44 specifications across six
suites, including actual paired PDFs and one English used-raster rebuild.
The parent and independent Step reviewer inspected both final pages: complete
localized content and diagrams, readable captions/cells, no overlap or clipping.
The native business format retains Japanese automatic figure/table prefixes in
English; this separately owned SmartDox follow-up is DEV-P87-001. Duplicate
dependency logging resources are HYG-P87-002. Neither is hidden or patched in
Cozy. P87-05 integrated acceptance passed 47/47; the sole comprehensive Phase
review passed and final ordinary FULL passed 3,972 tests with zero failures
(23 existing cancellations). The Phase checklist and distinct local release
binding record final closure; these are reporting updates, not new contracts.
