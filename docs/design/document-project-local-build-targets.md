# Document Project Local Build Targets Design

## Status and authority

This document is the normative P600-01B design for the local-build target
catalog. It complements the [Document Project design](document-project.md) and
does not alter the closed `cozy.document-project.v2` descriptor schema or the
document-production workflow matrix.

P600-01B freezes target contracts and the pure freshness decision vocabulary.
Actual rendering belongs to
P600-02 (Document Description confirmation views) and P600-03 (SmartDox article
HTML), while timestamp freshness and reuse belong to P600-04. This catalog is
not a renderer, file-I/O layer, CLI parser, or command dispatcher.

## Frozen target boundary

The reserved later command is:

```text
cozy document-project build <project> [--target <target-id>] [--force]
```

The target selector admits exactly these IDs:

- `document-structure-html`
- `document-reader-html`
- `smartdox-article-html`

An omitted selector resolves to `document-structure-html`. Every target writes
under the project-relative root
`target/document-project/local-build/<target-id>/`. Target definitions are
authored in `build/document-project-targets.yaml`; this file is a future
definition input and is not read or interpreted by the P600-01B catalog.

The two Document Description targets have the same direct source categories:
`config`, `content/core.yaml`, `content/<locale>/document.yaml`, and
`content/<locale>/confirmation-vocabulary.yaml`. The SmartDox article target
has `config` and `index.dox` as its direct source categories. Only configured
support files actually consumed by the renderer add article dependencies.
Changing a Document Description while leaving `index.dox` unchanged therefore
does not make an otherwise-current article HTML target stale.

The direct target-catalog input is
`build/document-project-targets.yaml`. It names the selected target and its
declared inputs; it is distinct from the two Document Description inputs and
from the article's `index.dox` input. No undeclared Document, Core annotation,
Visual Page or infographic file becomes an article dependency merely because
it exists in the project.

Each frozen target has one primary output, `index.html`, directly below its
target root. Renderer-produced assets are permitted only below the article
target root for `smartdox-article-html`; they are not shared with either
Document Description target or installed at a project/site root. A successful
operation reports its selected target, direct inputs, primary output and
whether the result was built or reused. A failed operation reports failure
before claiming a new result and preserves any prior successful output.

The operation contract is intentionally descriptive at this stage: target
selection and the pure freshness evaluator are package-private vocabulary.
No public command, parser, dispatcher, renderer invocation or filesystem
mutation is introduced by this slice. Later operations must validate required
inputs and output-root confinement before rendering, and must detect a
dependency cycle before starting any action. A generated prerequisite is
built before its dependent target; a missing producer or unresolved required
input is a failure with the blocking dependency visible.

The freshness evaluator receives immutable input/output observations and
returns exactly one decision in this order: missing required input, force,
missing output, strictly newer dependency, or current/reuse. Equal timestamps
reuse the output. This is ordinary make-style comparison: filesystem timestamp
resolution, future-dated inputs, same-time replacement and restored historical
timestamps retain the limitations of the underlying filesystem, and force is
the explicit remedy. Freshness is not content equivalence, prose reflection,
human acceptance or publication approval.

The structure-centered reference view keeps nested Step/Flow/Structure
relationships explicit and distinct, with the existing structure view as the
omitted-selector default. The document-centered reference view reads the same
Document Description in heading and prose order; Core references and diagrams
remain secondary, collapsible or side-panel information. Passage-to-Core and
Step-to-prose links use explicit source references and retain multiple targets.
Neither view paraphrases prose or treats generated HTML as approval evidence.

## Responsibilities and preserved boundaries

Core remains the authority for logical meaning. The localized Document
Description remains the authority for document organization and prose.
`index.dox` remains the SmartDox-targeted article representation. The three
targets are separate local confirmation products; rendering HTML does not
compose, revise, approve, or publish any source.

This design does not change `document-project review --kind article`, its
article-review receipt behavior, or the existing `document-confirmation`
command. It adds no target output, receipt, hash, renderer process, CLI
implementation, site build, external mutation, persistent server, or Phase
60.1 workflow. The later build command is reserved here solely to freeze its
contract.

## Related authority

The executable contract is [Document Project Local Build Targets
Specification](../spec/document-project-local-build-targets.md).
