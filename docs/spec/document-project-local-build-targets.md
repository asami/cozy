# Document Project Local Build Targets Specification

## Status and scope

This is the normative P600 specification for the implemented Document Project local-build operation. It is linked from the [Document Project Specification](document-project.md). It defines public command admission, target selection, target configuration, inputs, output/result reporting, prerequisite ordering, timestamp reuse, and ordinary failure reporting.

The public grammar is exactly:

```text
cozy document-project build <project> [--target <target-id>] [--force]
```

`<project>` MUST name an existing direct, non-symlink `.dox` package. This operation MUST admit that package without a descriptor: it MUST NOT load or require `document-project.yaml`.

## Target selection and configuration

The selector admits exactly three target IDs:

1. `document-structure-html`
2. `document-reader-html`
3. `smartdox-article-html`

When `--target` is omitted, selection MUST resolve to `document-structure-html`. Any other selector MUST be rejected; selection MUST NOT fall back to a nearby target. `--force` MUST request a rebuild.

The command MUST read this direct target-configuration input:

```text
build/document-project-targets.yaml
```

It MUST validate the configured schema and an exact, closed declaration of the three target IDs. Each declaration MUST conform to the frozen direct-input and generated-prerequisite contract; invalid, unsafe, missing, or unknown declared entries MUST fail closed.

Every primary output MUST be project-relative and have this form:

```text
target/document-project/local-build/<target-id>/index.html
```

The result MUST report the selected target, its direct inputs including the selected used-asset closure, that project-relative `index.html`, and either `built` or `reused`.

## Inputs and source authority

The two Document Description targets MUST use the configured Core, localized Document Description, and localized confirmation vocabulary:

| Target ID | Direct inputs in addition to target configuration |
| --- | --- |
| `document-structure-html` | `content/core.yaml`; `content/<locale>/document.yaml`; `content/<locale>/confirmation-vocabulary.yaml` |
| `document-reader-html` | `content/core.yaml`; `content/<locale>/document.yaml`; `content/<locale>/confirmation-vocabulary.yaml` |
| `smartdox-article-html` | `index.dox` |

These are the exact authored source inputs admitted by the existing target
configuration. The targets additionally own a dynamic used-asset
closure: after validating those direct sources, the command MUST admit the
selected Core and localized Document for either Document target, or prepare
the actual native SmartDox `index.dox` Document for the article target. It MUST
materialize only the selected Document's explicit figure references or the
article's actual parsed `Img.src` references against the project root. It MUST append the actual
project-relative raster paths to the reported Plan inputs in first-used order,
without duplicates, before freshness evaluation and output/source collision
checks. The Plan retains the admitted Document and immutable materialized
values for rendering; the article Plan retains its prepared native model and
materialized assets. Rendering MUST NOT parse or materialize them again.

Only selected used PNG/JPEG files participate. SVG authoring masters, another
locale's images and unrelated files MUST NOT become inputs. Table-only and
asset-free Documents append no asset inputs and retain the existing input
order. No asset-directory glob, configured asset list or target-catalog schema
change is admitted. Missing, unsafe, unsupported or unreadable used assets MUST
fail before reuse, even when a prior output is current.

The native confirmation and reader targets consume these materialized values
through their pure projections. This command integration is implemented in
P87-03. P87-04 extends the same preparation-before-freshness rule to actual
article image nodes. Article URI paths are decoded once, admitted as contained
local PNG/JPEG references, and embedded by attaching data URIs to native
SmartDox DOM image elements before existing HTML serialization. Native article
metadata, links, captions and tables retain their original transformer route.

The SmartDox article target uses its configuration, `index.dox` and the actual
used article image paths. A Document, Core annotation, Visual Page, infographic,
or another package file MUST NOT be made an article input merely because it
exists. Only its declared, renderer-used inputs and generated prerequisites
participate in article freshness. This adds no source-category or target
configuration fields: the exact three target declarations and their authored
source inputs remain unchanged.

Core remains authoritative for logical meaning, the localized Document Description for document organization and prose, and `index.dox` for the SmartDox-targeted article. A Document-only change MUST NOT make a current article output stale when `index.dox` and the article's actual inputs are current. Local Document rendering MUST NOT claim reflection into `index.dox`.

## Prerequisites, reuse, and failures

The command MUST resolve generated prerequisites from the closed target catalog and build each prerequisite before its dependent. It MUST reject a missing producer or dependency cycle before the affected renderer runs. Required inputs and output-root confinement MUST be checked before rendering.

Build versus reuse MUST use ordinary file modification times with this exact priority:

1. missing required input;
2. `--force` rebuild;
3. missing output;
4. strictly newer direct input or generated prerequisite;
5. current output/reuse.

Equal input/output timestamps MUST reuse the output. This is ordinary make-style strict `>` comparison and inherits filesystem timestamp resolution, future-time inputs, same-time replacement, and restored historical timestamp limitations. Freshness MUST NOT be described as prose equivalence, latest-Document reflection, human acceptance, or publication approval.

Rendering MUST complete before output writing. A renderer failure therefore leaves the prior output untouched. The command MUST create missing admitted output directories and directly write rendered UTF-8 HTML to the selected target. An ordinary directory/write failure MUST report failure without claiming a new result; partial output may remain. No staging, atomic install, post-create race check, cleanup, rollback, or prior-output preservation on I/O failure is promised. Recovery is regeneration.

## Preserved contracts and exclusions

The closed `cozy.document-project.v2` descriptor schema and the document-production workflow matrix remain unchanged. The existing `document-project review --kind article` command and its article-review receipt behavior remain unchanged, as does the existing `document-confirmation` command.

This specification adds no receipt or hash management, publication, deployment, external project work, or Phase 60.1 work. It does not make a local target a site build, source mutation, acceptance record, or publication operation.
