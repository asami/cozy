# Document Project Local Build Targets Specification

## Status and scope

This is the normative P600 specification for the implemented Document Project local-build operation. It is linked from the [Document Project Specification](document-project.md). It defines public command admission, target selection, target configuration, inputs, output/result reporting, prerequisite ordering, timestamp reuse, and failure preservation.

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

The result MUST report the selected target, its direct inputs, that project-relative `index.html`, and either `built` or `reused`.

## Inputs and source authority

The two Document Description targets MUST use the configured Core, localized Document Description, and localized confirmation vocabulary:

| Target ID | Direct inputs in addition to target configuration |
| --- | --- |
| `document-structure-html` | `content/core.yaml`; `content/<locale>/document.yaml`; `content/<locale>/confirmation-vocabulary.yaml` |
| `document-reader-html` | `content/core.yaml`; `content/<locale>/document.yaml`; `content/<locale>/confirmation-vocabulary.yaml` |
| `smartdox-article-html` | `index.dox` |

The SmartDox article target uses its configuration and `index.dox`. A Document, Core annotation, Visual Page, infographic, or another package file MUST NOT be made an article input merely because it exists. Only its declared, renderer-used inputs and generated prerequisites participate in article freshness.

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

If a target fails to render or install, it MUST report failure without claiming a new result. Its prior successful `index.html` and modification time MUST remain unchanged.

## Preserved contracts and exclusions

The closed `cozy.document-project.v2` descriptor schema and the document-production workflow matrix remain unchanged. The existing `document-project review --kind article` command and its article-review receipt behavior remain unchanged, as does the existing `document-confirmation` command.

This specification adds no receipt or hash management, publication, deployment, external project work, or Phase 60.1 work. It does not make a local target a site build, source mutation, acceptance record, or publication operation.
