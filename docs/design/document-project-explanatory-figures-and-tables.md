# Document Project Explanatory Figures and Tables Design

Status: decided Phase 87 boundaries; implementation in progress.
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
| [Local-build command](../../src/main/scala/cozy/document/CozyDocumentProjectLocalBuildCommand.scala) | P87-03 retains exact authored configuration inputs and appends only the selected Document's actual used raster paths before freshness/collision checks. Plan retains the admitted Document and materialized values for both native Document renderers; article Plan remains independent. |
| [Native SmartDox article HTML](../../src/main/scala/cozy/document/CozyDocumentProjectSmartDoxArticleHtml.scala) | Existing parser/HTML route; Result has asset paths but currently returns `Vector.empty`. Discover actual image nodes and embed their admitted raster content without adding Document dependencies. |
| [Existing article PDF](../../src/main/scala/cozy/media/CozyMediaPdf.scala) | Existing `article-pdf` media route and resource contract remain the PDF owner. Use this native route with reflected article content; no alternate PDF engine. |

The outside sources below are read-only dependencies in the adjacent SmartDox
repository; no upstream edits are authorized.

| Upstream source | Source capability and acceptance limit |
| --- | --- |
| [Dox model](../../../smartdox/src/main/scala/org/smartdox/Dox.scala) | Native Img, Figure and Table forms exist. Consume these forms through existing transformations. |
| [DoxLinesParser](../../../smartdox/src/main/scala/org/smartdox/parser/DoxLinesParser.scala) | `get_Image_Transition` recognizes image input; the annotation state wraps an image in Figure when a caption is present. Actual authored syntax must still be parsed through the production reader. |
| [Dox2LatexConverter](../../../smartdox/src/main/scala/org/smartdox/converters/Dox2LatexConverter.scala) | `enter_Figure` emits image/caption/optional label; `enter_Table` uses longtable. `_image_source_file` and the resource-root route resolve local raster resources for image inclusion. Actual JA/EN PDF execution and visual inspection remain unverified. |

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
P87-04 owns extending article inputs from actual parsed article image nodes. Keep exact existing authored configuration
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
mutated. Derived `index.dox`, actual article asset integration and PDF evidence
remain P87-04 responsibilities. Article reflection uses the existing authoring
route, with no second generator.
