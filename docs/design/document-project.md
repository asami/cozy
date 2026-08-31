# Document Project Design

## Status and authority

This document records the stable design intent for the Document Project
boundary planned in [Phase 42](../phase/phase-42.md).  It is design, not an
executable implementation or a replacement for the [Phase 42
checklist](../phase/phase-42-checklist.md), which remains the progress ledger.
The normative behavior contract is [Document Project
Specification](../spec/document-project.md).

This design promotes only the frozen boundary identified by the non-normative
planning inputs: the [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md).

## Operational envelope and semantic authority

Document Project is the operational envelope for a coherent document-production
effort.  Its authored descriptor selects a reusable workflow, one registered
workflow profile, language, workspace kind, and Content Core reference; the
workflow owns the profile's Work Products, deliverable dispositions, criteria,
gates, operations, and provider bindings.  It is not a new semantic source for
any medium.

Content Core is the separately authored shared semantic authority.  It carries
the meaning that must stay aligned across representations, while each medium
retains its own expression authority.  In particular, this design preserves:

- SmartDox article source as article-expression authority;
- editable infographic SVG as infographic-expression authority;
- Visual Page and Slide sources as presentation-expression authority;
- Storyboard and video sources as audiovisual-expression authority; and
- existing receipts and review-state documents as the authorities established
  by their own contracts.

The [Media Package specification](../spec/media-package.md), [Media Package
operation design](media-package-operation.md), [Visual Page specification](../spec/visual-page.md),
and [Visual Page design](visual-page.md) remain read-only retained authorities.
Document Project may bind or reference their results, but does not reinterpret,
replace, migrate, or expand them.

## Workflow model

The model deliberately separates reusable definition, project view, and
historical evidence:

- A **Workflow Definition** is the reusable logical-operation DAG with its
  roles, criteria, dependencies, and gates.  A project resolves it by identity;
  it never copies the DAG into a private editable descriptor.
- A **Workflow Instance Snapshot** is a derived, non-authoritative view for one
  Document Project.  It is reconstructed from authored authorities and accepted
  evidence, and may be cached only as a disposable projection.
- An **Operation Attempt** is append-only evidence of one attempt to perform a
  declared logical operation.  It is historical evidence, not mutable project
  status and not an acceptance decision by itself.

Work Products make the edges of that model visible.  Their closed role
vocabulary is `authority`, `plan`, `candidate`, `review-projection`,
`deliverable`, and `receipt`.  Branch selection distinguishes `required`,
`optional`, and `disabled`; an omitted branch has a visible reason rather than
being treated as completed work.

## Initial authored kernel

The initial Document Project package has one authored descriptor,
`document-project.yaml`, and one separately authored Content Core at
`content/core-<language>.yaml`.  The descriptor is deliberately a small,
closed selection boundary.  Its only selection inputs are identity, workflow,
profile, language, workspace, and Content Core reference.  It does not embed a
private workflow graph, artifact-local source, delivery declaration, provider
binding, or mutable lifecycle state.

The descriptor's `workflow` field is an identity reference to the reusable
`document-production` Workflow Definition.  Its `profile` field selects one
closed registered binding within that Workflow Definition: `standard` or
`standard-video`.  `standard-video` is the only video-bearing profile in the
initial skeleton.  DP42-02 closes and validates the workflow-owned
profile-to-Work-Product, deliverable-disposition, criteria/gate, operation,
and provider-binding model; it MUST NOT add a field to the closed
`cozy.document-project.v1` descriptor.  The `workspace` field chooses only
`directory` or `bok`; the latter is a kind selection, not hosting discovery,
registration, source projection, or an external action.  A relative Core path
keeps the Core inside the package without making the descriptor a source of
semantic meaning.

Content Core is intentionally minimal at this boundary.  Its closed grammar
binds a project and language and carries its accepted shared semantic
statements as one ordered `accepted` sequence.  Each entry has only its stable
`id` and non-empty trimmed UTF-8 `text`; entry identities are unique inside the
Core.  Thus the Core is an actual minimal semantic authority rather than an
identity-only placeholder.  Only an explicitly accepted review may add or
replace an accepted entry.  Raw AI output remains provenance outside the Core.
The Core contains neither status nor renderer/delivery/provider fields.  The
eventual Phase 42.1 attempt/review evidence records provider, model, request,
response identities, and acceptance, but it does not make the Core mutable
status or a receipt.  Structured semantic vocabulary and automation remain out
of scope.  This initial kernel does not define an attempt or review persistence
format.

## Command and scaffold boundary

`cozy document-project` has a distinct public namespace, deliberately
separate from the existing software Project knowledge-package commands and
`cozy media`.  Its project operands name existing `*.dox/` package directories,
not arbitrary descriptor files.  `inspect`, `plan`, and `verify` are derived
non-mutating inspections.  Phase 42 fixes the `dashboard` command syntax and
its protected non-authority boundary only: until Phase 42.1 supplies rendering
and state behavior, `dashboard` rejects with `DP-PHASE-001` and creates no
destination, state, receipt, attempt, registry, or delivery evidence.  Phase
42.1 implements the same fixed explicit-output grammar as a read-only
projection.  `run` dispatches one declared operation; and `scaffold` is the
only command that creates authored sources.  No command aliases or ambiguous
dispatch are part of this kernel.

Canonical package source traversal is direct and non-symlinked.  An admitted
package is itself a direct non-symlink directory, and its descriptor, `content/`
directory, Core, and initial authored sources are direct entries contained in
that package.  A lexical Core path is resolved only from that admitted package
and cannot escape it through a symlink-based path.  This is a contract boundary
for package admission, not a filesystem implementation prescription.

Scaffold constructs either the `standard` authoring skeleton or the
`standard-video` skeleton, which adds only `video/storyboard.md`.  It preserves
the regular `index.dox` article-expression authority and does not perform
SmartDox host discovery or source projection.  Creation is all-or-nothing: a
new package is moved atomically into an existing real parent directory only
when the destination and its relevant ancestry are safe, absent, and
non-symlinked.  It neither merges nor overwrites, and it does not substitute a
copy fallback for the atomic move.

The created skeleton contains no generated `target/`, state, receipt,
approval, Operation Attempt, dashboard, registry, publication, deployment,
upload, or fake completed deliverable.  Those are evidence or delivery
concerns, never scaffolded authority.

## Projections and delivery boundaries

When Phase 42.1 implements dashboard rendering, project dashboard HTML and
review HTML are generated, read-only projections.  They may explain evidence,
workflow, or review material, but are never semantic, workflow, renderer, or
status authority and never write back into authored sources.

Project production stops at the project boundary.  Workspace integration,
aggregate build, and external delivery are separate operations and separate
responsibilities.  A project result therefore does not imply registration,
workspace-wide build, publication, deployment, upload, or external acceptance.

## Deliberate exclusions

This boundary excludes autonomous acceptance; mutable progress or status
authority; scheduler, daemon, and arbitrary-command execution; dashboard
write-back; implicit registration, build, publish, deploy, or upload;
migration; and any Phase 41 expansion.  It is additive to the retained
authorities above and does not retrofit existing article or media packages.

## Deferred implementation boundary

The companion specification now freezes the initial descriptor/Core grammar,
public command signatures, validation diagnostics, success headings, and
atomic scaffold contents.  The descriptor and Core grammars are permanently
closed and are not expandable by a later Phase.  DP42-02 does not yet close the workflow-owned
Work Product, provider-binding, deliverable-disposition, criteria/gate, and
operation model; it MUST NOT add descriptor fields.  This design does not yet
define canonical serialization or identity algorithms; implement the commands;
or introduce their executable specifications.

Phase 42.1 retains executable dashboard content, derived-state reconstruction,
append-only attempt persistence, receipts/currentness/stale propagation,
review projection, and driver acceptance.  It implements the already-fixed
dashboard grammar rather than expanding it.  SmartDox source projection and
host discovery are also outside this kernel.  No deferred capability is
accepted or claimed by this design.

## Related authorities

- Normative contract: [Document Project Specification](../spec/document-project.md)
- Phase plan: [Phase 42](../phase/phase-42.md)
- Progress ledger: [Phase 42 checklist](../phase/phase-42-checklist.md)
- Planning input only: [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
  and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md)
