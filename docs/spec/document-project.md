# Document Project Specification

## Status and scope

This is the normative Phase 42 baseline for the Document Project responsibility
boundary.  Its stable design intent is [Document Project
Design](../design/document-project.md).  The [Phase 42
checklist](../phase/phase-42-checklist.md) is a progress ledger, not a behavior
contract; [Phase 42](../phase/phase-42.md) supplies the planned phase boundary.

The [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md)
are read-only, non-normative planning inputs.  They do not add behavior beyond
this specification.

## Contract identities

A conforming future implementation MUST preserve the following distinct
contract identities and roles:

| Identity | Required role |
| --- | --- |
| `cozy.document-project.v1` | Authored closed selection descriptor: identity, selected workflow, its registered profile, language, workspace, and Content Core reference. |
| `cozy.document-workflow.v1` | Reusable logical-operation DAG with Work Product roles, criteria, dependencies, and gates. |
| `cozy.document-project-state.v1` | Derived Workflow Instance Snapshot; it is never writable authority. |
| `cozy.document-operation-attempt.v1` | Append-only evidence for one attempted declared logical operation. |
| `cozy.content-core.v1` | Minimal, separately authored shared semantic authority. |

A Document Project MUST be the operational envelope, not a replacement for
Content Core.  Content Core MUST remain the shared semantic authority; it MUST
NOT become a mutable workflow-status record, rendering configuration, or
delivery declaration.

A project MUST resolve its selected `cozy.document-workflow.v1` definition by
identity.  It MUST NOT copy the reusable DAG into the authored descriptor for
private modification.  A `cozy.document-project-state.v1` snapshot MUST be
derived from the relevant authored authorities and evidence.  A cache of that
snapshot MUST NOT become a second authority.  An Operation Attempt MUST remain
inspectable after failure or supersession and MUST NOT itself make a Work
Product accepted or current.

## Initial authored descriptor grammar

The only initial Document Project descriptor is `document-project.yaml`.  It
MUST have exactly these top-level keys and no others:

| Key | Required value or closed shape |
| --- | --- |
| `schema` | Exactly `cozy.document-project.v1`. |
| `id` | A slug matching `[a-z0-9][a-z0-9._-]*`. |
| `workflow` | An object with exactly `schema` and `id`; `schema` is exactly `cozy.document-workflow.v1` and `id` is exactly `document-production`. |
| `profile` | Exactly `standard` or `standard-video`. |
| `language` | A lowercase BCP-47-shaped tag matching `[a-z]{2,8}(?:-[a-z0-9]{1,8})*`. |
| `workspace` | An object with exactly `kind`, whose value is exactly `directory` or `bok`. |
| `contentCore` | Exactly the normalized relative path `content/core-<language>.yaml`, where `<language>` is the descriptor `language`. |

`contentCore` MUST NOT be absolute and MUST NOT contain a `.` or `..` path
segment.  `workflow` is a reference to the reusable definition and MUST NOT be
a copied DAG.  `workspace.kind: bok` binds only workspace kind; it MUST NOT
imply hosting discovery, registration, source projection, or an external
action.  No unknown or additional descriptor key is admitted by this kernel or
by DP42-02.

`profile` selects a closed registered binding inside the referenced
`cozy.document-workflow.v1` definition.  `standard` and `standard-video` are
the two registered initial profiles; `standard-video` is the only video-bearing
profile for the initial skeleton.  DP42-02 MUST close and validate the
workflow-owned profile-to-Work-Product, deliverable-disposition,
criteria/gate, operation, and provider-binding model.  DP42-02 MUST NOT add a
field to `cozy.document-project.v1`.

The exact Core file is `content/core-<language>.yaml`.  It MUST have exactly
`schema`, `id`, `language`, and `accepted`, with these values:

| Key | Required value |
| --- | --- |
| `schema` | Exactly `cozy.content-core.v1`. |
| `id` | Exactly `<project-id>:core:<language>`. |
| `language` | Exactly the descriptor `language`. |
| `accepted` | An ordered sequence, which MAY be empty in a freshly scaffolded package.  Each entry is an object with exactly `id` and `text`; `id` matches `[a-z0-9][a-z0-9._-]*` and is unique inside the Core, and `text` is a non-empty trimmed UTF-8 semantic statement. |

This Core is a minimal semantic authority.  Only an explicitly accepted review
MAY add or replace an `accepted` entry.  It MUST contain no mutable status,
rendering configuration, delivery declaration, raw prompt or response, review
decision, provider binding, artifact-local source, claims/evidence/term/relation
vocabulary, external reference, or generic map or extension field.  AI
assistance is a future admitted operation only: a raw response is provenance,
never authority; eventual Phase 42.1 attempt/review evidence MUST retain
provider, model, request, and response identities and explicit review
acceptance before any Core write-back.  That evidence MUST NOT make the Core
into mutable status or a receipt.  Structured semantic vocabulary and
automation remain out of scope.  This specification does not yet define a
persisted attempt or review format.

The closed descriptor and Core fields above are permanently closed and are not
expandable.  DP42-02 closes only the workflow-owned Work Product, provider-binding,
deliverable-disposition, criteria/gate, and operation model.

## Retained authorities and Work Products

The closed Work Product role vocabulary is exactly `authority`, `plan`,
`candidate`, `review-projection`, `deliverable`, and `receipt`.  A role MUST be
declared by the workflow or project binding; it MUST NOT be inferred from a
filename, extension, or output directory.

Document Project MUST preserve, rather than reinterpret, the authorities
defined by the [Media Package specification](media-package.md), [Media Package
operation design](../design/media-package-operation.md), [Visual Page
specification](visual-page.md), and [Visual Page design](../design/visual-page.md).
SmartDox article source, editable infographic SVG, Visual Page/Slide sources,
Storyboard/video sources, existing receipts, and existing review-state
contracts remain their respective authorities.  This baseline does not migrate,
replace, broaden, or certify compatibility with any of those legacy contracts.

## Branches and lifecycle views

Every artifact branch MUST have exactly one declared disposition:

- `required` means its applicable criteria and gates participate in the
  project's completion view.
- `optional` means it is selectable and visible but does not participate until
  activated for the project.
- `disabled` means it is omitted and MUST expose an exact visible reason.

An omitted branch MUST NOT be counted as completed work.  Completion coverage,
currentness, review, readiness, and labels such as core or artifact lifecycle
states MUST be derived views over declared Work Products and evidence.  They
MUST NOT be writable project status, manually asserted percentages, or an
autonomous acceptance decision.  `complete`, `current`, and `accepted` MUST
remain distinct views.

Dashboard HTML and review HTML MUST be generated read-only projections.  They
MUST NOT become semantic, workflow, renderer, receipt, or status authority and
MUST NOT write back into authored sources.

## Public command grammar and boundaries

The complete public Document Project command grammar is:

```text
cozy document-project inspect <project>
cozy document-project plan <project>
cozy document-project dashboard <project> --save <dashboard.html>
cozy document-project verify <project>
cozy document-project run <project> --operation <logical-operation> [--dry-run]
cozy document-project scaffold <slug> --profile standard|standard-video --language <tag> --workspace directory|bok --save <parent>
```

After the CLI and Phase gates, `<project>` MUST be an existing direct
non-symlink directory whose name ends in `.dox`; it MUST NOT be an arbitrary
descriptor filename.  Within an admitted project package,
`document-project.yaml`, the `content/` directory, and the `contentCore` file
MUST each be a direct regular non-symlink entry.  The lexically exact relative
Core path MUST resolve from that package without escaping it.  A failure of any
of these path-admission requirements MUST reject with `DP-PATH-001` before
descriptor or Core parsing.

When `verify` checks the initial authored source paths, `index.dox`,
`infographic/infographic.svg`, `presentation/visual-pages.yaml`, and
`review/README.md` MUST each be direct regular non-symlink entries contained in
the admitted package.  `video/storyboard.md` has the same requirement only for
the `standard-video` profile.  Their semantic contents and workflow validation
remain deferred.  Dashboard `--save` is its fixed explicit output destination.
Phase 42 fixes that grammar and protected non-authority boundary only;
dashboard rendering and state evidence are Phase 42.1 behavior.  The command
namespace MUST remain distinct from existing software Project knowledge-package
and `cozy media` commands.  No alias or ambiguous dispatch is permitted.

- `inspect` MUST inspect and report a derived project view without mutating
  authored authority, project state, evidence, registration, or delivery.
- `plan` MUST report active, omitted, blocked, and eligible declared work
  without mutating authored authority, project state, evidence, registration,
  or delivery.
- `dashboard` MUST reject with `DP-PHASE-001` until Phase 42.1 implements its
  fixed grammar as a generated, read-only dashboard projection at its explicit
  output destination.  That rejection MUST create no destination, generated
  state, receipt, approval, attempt, registry, or delivery evidence.
- `verify` MUST inspect declared project material for conformance without
  mutating authored authority, project state, evidence, registration, or
  delivery.
- `run --operation` MUST dispatch exactly one declared logical operation.  It
  MUST NOT infer downstream publication, workspace-wide execution, aggregate
  build, registration, deployment, upload, or any undeclared operation.
- `scaffold` MUST create only the authored initial package described below.  It
  MUST NOT register, build, publish, deploy, upload, or create delivery
  evidence implicitly.

Project production, workspace integration, aggregate build, and external
delivery MUST remain separate responsibilities.  A project command MUST NOT
silently cross from one responsibility into another.

## Scaffold boundary

`scaffold` MUST atomically create a profile-sensitive `<parent>/<slug>.dox/`
authored package.  An unsafe, outside-parent, symlink, or non-direct scaffold
parent or destination is a `DP-PATH-001` rejection.  Once those path checks
pass, an existing destination, an absent or non-real parent, or inability to
complete the required atomic move is a `DP-SCAFFOLD-001` rejection.  It MUST
NOT merge or overwrite, and MUST NOT substitute fallback copy for the required
atomic move.  It MUST resolve the common workflow by identity rather than copy
the workflow DAG.

The `standard` profile MUST create exactly these authored paths:

```text
<slug>.dox/
  document-project.yaml
  index.dox
  content/
    core-<language>.yaml
  infographic/
    infographic.svg
  presentation/
    visual-pages.yaml
  review/
    README.md
```

The `standard-video` profile MUST create exactly the `standard` paths plus
`video/storyboard.md`.  No other profile is admitted.  Omitted branches MUST
NOT receive fake completed artifacts.  Scaffold MUST preserve regular
`index.dox` article-expression authority and MUST NOT perform SmartDox host
discovery or source projection.  It MUST write no `target/`, generated state,
receipt, approval, Operation Attempt, dashboard, registry, publication,
deployment, upload, or delivery evidence.

The scaffolded Core MUST initialize `accepted: []`.  It is therefore an actual
minimal semantic authority from creation, not merely an identity placeholder.

## Validation, diagnostics, and success output

A rejected command or input MUST report exactly one stable error token and one
human-readable cause.  The ordered diagnostic gates below are authoritative:
the first matching gate is the sole emitted token, and every later gate runs
only after all preceding gates have passed.  A token MUST NOT be combined with
another token.  This Slice does not prescribe an exception class or exit code.

| Order | Token | First matching rejection condition |
| --- | --- | --- |
| 1 | `DP-CLI-001` | Unknown command or subcommand, unsupported option, or extra or otherwise invalid command grammar for a command form. |
| 2 | `DP-CLI-002` | A known syntactically valid command form lacks a required positional argument or required option/value. |
| 3 | `DP-PHASE-001` | A syntactically complete `dashboard <project> --save <dashboard.html>` request in Phase 42.  It rejects before resolving or validating project or save paths and creates no destination, state, receipt, attempt, registry, or delivery evidence. |
| 4 | `DP-PATH-001` | A command whose preceding gates passed has an unsafe, outside-package, symlink, or non-direct path, including a project, descriptor/Core source, verified authored source, or scaffold parent/destination path that fails path admission. |
| 5 | `DP-SCAFFOLD-001` | `scaffold` has safe paths, but its destination already exists, its parent is absent or non-real, or the required atomic move cannot be completed. |
| 6 | `DP-DESC-001` | An inspected, planned, verified, or run project has a missing, unreadable, or malformed descriptor or Core after path admission. |
| 7 | `DP-DESC-002` | A successfully parsed descriptor or Core has an unknown field or unsupported or invalid closed value or relationship after descriptor admission. |
| 8 | `DP-OP-001` | A path- and descriptor-valid `run` request names an unknown or undeclared logical operation. |

Successful `inspect`, `plan`, `verify`, and `scaffold` output MUST begin with,
respectively, `Cozy Document Project Inspect`, `Cozy Document Project Plan`,
`Cozy Document Project Verify`, and `Cozy Document Project Scaffold`.  Each
such success output MUST identify the project and schema.  Scaffold success
output MUST also identify package, workflow, profile, and workspace.  This
Slice defines no dashboard success output because `dashboard` rejects in Phase
42.  Phase 42.1 implements its already-fixed explicit-output grammar; this
Slice does not define dashboard contents, operation-attempt persistence, or
receipts.

## Non-goals and deliberate deferrals

This baseline MUST NOT be read as authorizing autonomous acceptance, mutable
progress/status authority, a scheduler, daemon, arbitrary command execution,
dashboard write-back, implicit registration/build/publish/deploy/upload,
migration, or Phase 41 expansion.

The closed `cozy.document-project.v1` descriptor fields are not deferred or
expandable.  The following remain deferred by their designated boundaries:
workflow-owned Work Product, provider-binding, deliverable-disposition,
criteria/gate, and operation model closure (DP42-02); canonical serialization
and identity calculation; SmartDox source projection and host discovery;
executable implementation and its executable specifications; and all Phase
42.1 dashboard/state/attempt/receipt/review/driver behavior.  Phase 42.1 owns
executable dashboard content, derived state reconstruction, append-only
attempt persistence, receipts/currentness/stale propagation, review projection,
and driver acceptance; it implements the already-fixed dashboard grammar and
MUST NOT expand descriptor fields.  This specification MUST NOT be represented
as implementing, accepting, or proving compatibility for those deferred
matters.

## Related authorities

- Stable design: [Document Project Design](../design/document-project.md)
- Phase plan: [Phase 42](../phase/phase-42.md)
- Progress ledger: [Phase 42 checklist](../phase/phase-42-checklist.md)
- Retained media contract: [Media Package specification](media-package.md)
  and [Media Package operation design](../design/media-package-operation.md)
- Retained Visual Page contract: [Visual Page specification](visual-page.md)
  and [Visual Page design](../design/visual-page.md)
- Planning input only: [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
  and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md)
