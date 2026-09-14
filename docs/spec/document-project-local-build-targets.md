# Document Project Local Build Targets Specification

## Status and scope

This is the normative P600-01B specification for the Document Project local
build target catalog. It is linked from the [Document Project
Specification](document-project.md) and is limited to target identity, direct
source categories, project-relative output roots, and pure freshness decisions.

P600-01B does not implement rendering. Document Description confirmation
rendering belongs to P600-02, SmartDox article rendering belongs to P600-03,
and ordinary filesystem timestamp dependency resolution and output reuse belong
to P600-04. The catalog itself performs no file I/O, timestamp comparison, CLI
parsing, command dispatch, renderer invocation, output installation, receipt
creation, or hash management. Its package-private evaluator compares supplied
observations only; it does not read paths or mutate output times.

## Reserved command and target selection

The reserved later command grammar is exactly:

```text
cozy document-project build <project> [--target <target-id>] [--force]
```

The selector admits exactly three target IDs:

1. `document-structure-html`
2. `document-reader-html`
3. `smartdox-article-html`

When `--target` is omitted, selection MUST resolve to
`document-structure-html`. Any other selector MUST be rejected as unknown;
selection MUST NOT fall back to a nearby target. `--force` is reserved for the
P600-04 timestamp/reuse contract and has no meaning in this catalog.

## Target contracts

Every target output root MUST be project-relative and have this form:

```text
target/document-project/local-build/<target-id>/
```

The target-definition file is:

```text
build/document-project-targets.yaml
```

The catalog freezes the following direct source categories and no others:

| Target ID | Direct source categories |
| --- | --- |
| `document-structure-html` | `config`; `content/core.yaml`; `content/<locale>/document.yaml`; `content/<locale>/confirmation-vocabulary.yaml` |
| `document-reader-html` | `config`; `content/core.yaml`; `content/<locale>/document.yaml`; `content/<locale>/confirmation-vocabulary.yaml` |
| `smartdox-article-html` | `config`; `index.dox` |

Here `config` denotes the selected project configuration category. The two
Document targets consume the same validated Core, localized Document
Description, and localized confirmation vocabulary. The SmartDox article
target consumes the project configuration and the existing `index.dox`.
Only renderer-consumed configured support files add dependencies to the article
target. A Document Description change alone MUST NOT mark unchanged `index.dox`
stale; article freshness is relative to the article target's actual inputs,
not proof of Document-to-article reflection.

The direct target-catalog input MUST be
`build/document-project-targets.yaml`. It declares the selected target and its
inputs; it MUST NOT be expanded implicitly with Document, Core annotation,
Visual Page or infographic files that the article renderer does not consume.

Each target's primary output MUST be `index.html` below its own frozen target
root. Renderer assets for the SmartDox article MUST remain below that article
root; the two Document targets MUST NOT depend on or install article assets.

## Operation and freshness contract

The admitted operation result identifies the selected target, its declared
inputs, its `index.html` output, and a built or reused result. A failure MUST
remain a failure and MUST preserve a prior successful output and its timestamp.
Required inputs and output-root confinement are checked before rendering. A
dependency graph MUST build generated prerequisites before dependents and MUST
reject a missing producer or dependency cycle before any renderer action.

The pure evaluator receives immutable observations for required input paths,
their optional modification times, the output modification time, and the force
flag. It returns one typed decision with this exact priority:

1. missing required input;
2. force build;
3. missing output;
4. strictly newer dependency;
5. current output/reuse.

An equal input/output timestamp MUST reuse the output. Comparison is ordinary
make-style strict `>` and inherits filesystem timestamp resolution, future-time
inputs, same-time replacement, and restored historical timestamp limitations.
Force is the explicit remedy. Timestamp freshness MUST NOT be described as
prose equivalence, latest-Document reflection, human acceptance, or publication
approval.

The structure reference UI MUST preserve the existing structure-centered
default and show nested Step/Flow/Structure relationships as distinct
navigation. The document reference UI MUST read the same prose in document
order while keeping Core references and diagrams secondary or collapsible.
Explicit passage/Core and Step/prose references MUST retain multiple targets;
the renderer MUST NOT infer edges or paraphrase prose.

## Preserved contracts and exclusions

The closed `cozy.document-project.v2` descriptor schema and the
document-production workflow matrix remain unchanged. The existing
`document-project review --kind article` command and its article-review receipt
behavior remain unchanged. The existing `document-confirmation` command
remains unchanged.

This specification admits no target output or receipt, hash or digest
management, renderer process, public CLI parser or dispatcher, site build,
external mutation, publication, deployment, persistent server, or Phase 60.1
work. It does not claim that the reserved build command is callable yet.
