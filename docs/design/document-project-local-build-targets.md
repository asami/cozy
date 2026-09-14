# Document Project Local Build Targets Design

## Status and authority

This document is the normative P600 design for the implemented Document Project local-build operation. It complements the [Document Project design](document-project.md); it does not alter the closed `cozy.document-project.v2` descriptor schema or the document-production workflow matrix.

The public operation is exactly:

```text
cozy document-project build <project> [--target <target-id>] [--force]
```

Its dispatcher admits a direct, non-symlink `.dox` package. This local-build operation is descriptor-free: it does not load or require `document-project.yaml`. The command reads `build/document-project-targets.yaml`, validates its schema and the closed target catalog, resolves the selected target's generated prerequisites, and then renders or reuses each target in prerequisite order.

## Target and renderer boundary

The target selector admits exactly these IDs:

- `document-structure-html`
- `document-reader-html`
- `smartdox-article-html`

An omitted selector resolves to `document-structure-html`; any other selector is rejected. The configuration must define exactly this closed catalog and must conform each target's declared input and prerequisite contract. Every target writes its primary `index.html` under the project-relative root:

```text
target/document-project/local-build/<target-id>/index.html
```

The command reports the selected target, its direct inputs, its project-relative `index.html`, and whether that target was built or reused. It performs the renderer/install boundary locally; it neither installs a site-root artifact nor shares target output with another target.

Generated prerequisites are closed to the catalog. A prerequisite target is planned and completed before its dependent target. A missing producer, unknown target, invalid input, output-root escape, or dependency cycle fails before the affected renderer is invoked.

## Inputs and source authority

The direct target-configuration input is always `build/document-project-targets.yaml`. The two Document Description targets use the configured Core, localized Document Description, and localized confirmation vocabulary:

```text
content/core.yaml
content/<locale>/document.yaml
content/<locale>/confirmation-vocabulary.yaml
```

The SmartDox article target uses its configuration and `index.dox`. It does not gain a Document, Core annotation, Visual Page, infographic, or other dependency merely because that file exists in the package. Core remains the authority for logical meaning, the localized Document Description for document organization and prose, and `index.dox` for the SmartDox-targeted article representation.

Consequently, changing only the Document Description does not make a current article output stale while `index.dox` and the article target's actual inputs remain current. A local Document build neither reflects content into `index.dox` nor claims that such reflection happened.

## Freshness and failure boundary

The command uses ordinary file modification times to select build or reuse, in this order: missing required input, `--force`, missing output, a strictly newer input or generated prerequisite, then current/reuse. Equal timestamps reuse. This is make-style timestamp comparison and therefore retains filesystem timestamp-resolution, future-time, same-time-replacement, and restored-time limitations. `--force` explicitly rebuilds even a current target. Timestamp freshness is not content equivalence, prose reflection, human approval, or publication approval.

Rendering completes before installation. If a target fails to render or install, the command reports failure without reporting a new result, and that target's prior successful `index.html` remains unchanged.

## Preserved exclusions

This design changes neither the descriptor schema nor receipt or hash management. It does not change `document-project review --kind article`, its article-review receipt behavior, the existing `document-confirmation` command, publication, deployment, external projects, or Phase 60.1. The local build operation is a local confirmation product, not a source revision, acceptance, or publishing workflow.

## Related authority

The executable contract is [Document Project Local Build Targets Specification](../spec/document-project-local-build-targets.md).
