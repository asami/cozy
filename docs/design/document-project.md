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

## Phase 42.1 evidence and attempt boundary

This design records the Phase 42.1 evidence boundary.  DP42-03B implements the
disposable snapshot cache; DP42-03C implements recorded single-operation
dispatch and append-only attempt persistence.  Evidence-based stale
propagation remains a later follow-up.

The `cozy.document-project-state.v1` Workflow Instance Snapshot is disposable
derived YAML at `<project>/target/document-project/state.yaml`.  It is never
authored authority and cannot be edited as a state override.  Removing
`<project>/target/document-project` and inspecting unchanged admitted project
inputs reconstructs the identical snapshot.

Successful `inspect` and `verify` regenerate the cache only after their
descriptor, Core, and command-specific source validation succeeds.  Its
canonical UTF-8 YAML keys are ordered `schema`, `project`, `profile`,
`workspace`, `sources`, and `workProducts`.  The fixed-order `sources` entries
contain only project-relative direct authored paths and lowercase hexadecimal
SHA-256 content identities: descriptor, exact Core, `index.dox`, infographic
SVG, Visual Page source, review README, and directly present standard-video
storyboard.  No absolute path, timestamp, random value, discovery order,
generated output, receipt, or attempt is serialized.

The fixed-order Work Product projection contains `id`, `role`, `disposition`,
`criterion`, `coverage`, `currentness`, `review`, and `readiness`.  It uses only
the closed status vocabularies.  Existing source assets may be
`satisfied`/`current`; absent output or receipt-dependent evidence remains
`missing`/`blocked`/`pending`.  Standard's disabled video products explicitly
use `coverage: not-applicable`, `readiness: omitted`, and the profile reason
`profile standard disables video branch`, never completion.  DP42-03C
implements immutable append-only attempts and recorded dispatch only.  This
Slice does not yet derive `stale`; receipt evidence and evidence-based
dependency/stale propagation remain later Phase 42.1 work.

A `cozy.document-operation-attempt.v1` Operation Attempt is durable,
append-only evidence at `<project>/evidence/attempts/<attempt-id>.yaml`; it is
never scaffolded.  Existing attempt bytes are never overwritten, so failures
and superseded attempts remain inspectable.  Its existence alone does not make
a Work Product current, complete, reviewed, or accepted.  Canonical UTF-8
attempt YAML has exactly the ordered top-level keys `schema`, `id`,
`operation`, `provider`, `profile`, `inputs`, `outcome`, `diagnostics`,
`outputs`, and `receipt`.  It records the newly generated stable id, declared
operation, frozen provider binding, selected profile, fixed-order direct
project-relative source identities with lowercase SHA-256 values,
`outcome: recorded`, one diagnostic stating that provider execution is
deferred and the dispatch was recorded only, `outputs: []`, and `receipt:
none`.  The dispatch invokes no provider, creates no output or receipt, writes
no Core or state cache, and infers no downstream operation.  Accepted review
evidence is separate from an attempt and remains required before a Content Core
write-back.

DP42-03C creates `evidence/attempts` only for an eligible normal run; the
directories and files must be direct, non-symlinked project entries.  Each
attempt is written to a same-directory temporary file and published with
`ATOMIC_MOVE` without replacement.  Collisions or publication failures leave
existing bytes untouched, remove the temporary file, and return a stable
path/evidence diagnostic.  `--dry-run` performs the same admission without
creating evidence or a cache.

The snapshot independently derives coverage (`satisfied`, `missing`, and
`not-applicable` criteria), currentness (`missing`, `current`, `stale`, or
`failed`) from declared identities rather than timestamps, review (`pending`,
`accepted`, `rejected`, or `stale`), and readiness (`blocked`, `ready`,
`running`, `succeeded`, `failed`, or `omitted`).  `omitted` is visible with its
declared profile reason and is not completion.

Reconstruction uses the closed `document-production` Work Product definition,
descriptor and Core identities, retained source/receipt/review/attempt evidence,
and declared producer/consumer/dependency identities.  It does not infer state
from filenames or timestamps.  A changed dependency makes every declared
consumer stale; a changed shared infographic therefore stales its declared
article, slides, and video consumers.

`inspect` and `verify` remain non-authoritative: they may write only that
disposable snapshot cache, never an attempt, receipt, acceptance, authored
source, registry, workspace integration, aggregate build, publication,
deployment, upload, or downstream operation.  DP42-03C `run` dispatches
exactly one declared registered operation enabled for the selected profile and
creates one append-only attempt, while `--dry-run` reports the same selected
operation, provider, and profile without persistence.  It does not infer
downstream execution.  This boundary
does not alter the closed descriptor fields, common workflow DAG, profiles,
Work Product roles, criteria, gates, operation IDs, provider bindings, retained
media/SmartDox/Visual Page/Phase-41 authorities, public command grammar, or
Phase-42 compatibility behavior.

## Closed DP42-02 workflow definition

DP42-02 closes the initial reusable `document-production` definition in code.
It is immutable, resolves only `standard` and `standard-video`, and is not
serialized into, copied by, or editable through a Document Project descriptor.
The definition validates itself before a plan projection or operation-admission
lookup: identifiers are unique; producer, consumer, criterion, dependency,
gate, evidence-reference, and provider-binding references are closed; product
dependencies are acyclic; required metadata is non-empty; and every profile
binds only the closed Work Product set.

The definition's stable initial Work Products are:

| Work Product id | Role | `standard` | `standard-video` |
| --- | --- | --- | --- |
| `content-core-candidate` | candidate | optional | optional |
| `content-core` | authority | required | required |
| `article-source` | authority | required | required |
| `article-html` | deliverable | optional | optional |
| `article-pdf` | deliverable | required | required |
| `summary-slides-pdf` | deliverable | optional | optional |
| `infographic-svg` | authority | required | required |
| `infographic-png` | deliverable | optional | optional |
| `video-storyboard` | plan | disabled: `profile standard disables video branch` | required |
| `video-review` | review-projection | disabled: `profile standard disables video branch` | required |
| `video-deliverable` | deliverable | disabled: `profile standard disables video branch` | required |
| `explanation-structure-review-html` | review-projection | optional | optional |
| `operation-receipt-evidence` | receipt | optional | optional |

`required`, `optional`, and `disabled` are visibly different dispositions.
The exact disabled reason belongs only to the three video Work Products in the
`standard` profile; `standard-video` activates the same reusable branch
without a disabled reason.

Each Work Product declares its producer and consumer logical operations, its
criteria, Work Product dependencies, gate, and evidence reference.  Each
logical operation has a stable id and one static provider binding.  The initial
bindings are `content-core.compose`/`content-core.review`, article compose and
render operations, `summary-slides.render-pdf`, infographic compose and PNG
render operations, the three video operations,
`explanation-structure.render-review`, and `operation-receipt.record`.  Their
providers identify the fixed Cozy, SmartDox, Visual Page, infographic, video,
Phase-41 Explanation Structure Review, or receipt adapter responsibility; they
do not discover a provider or execute an adapter in DP42-02.  Criteria, gates,
and evidence references are static identity links, not completion, currentness,
review, receipt, or lifecycle fields.

`plan` resolves this definition read-only.  It emits deterministic static
`active` and `omitted` Work Product lines, plus `blocked` and `eligible`
logical-operation lines.  `eligible` means that an operation is declared for
the selected profile, not that it is runtime-ready.  In Phase 42 every such
operation is simultaneously blocked from execution because execution and
Operation Attempts are reserved for Phase 42.1.  The command creates no
target, dashboard, state, attempt, receipt, registry, delivery, or output file.

`run` validates the descriptor, Core, and command-admitted initial sources,
then admits exactly one declared operation enabled by the selected profile.  A
normal eligible run records one attempt without invoking its provider or
creating output, receipt, Core write-back, state cache, or downstream work.
`--dry-run` reports the selected operation, provider, and profile without
creating evidence.  An unknown or profile-disabled operation rejects with
`DP-OP-001`; malformed or unsafe input retains its earlier diagnostic
precedence and neither rejection creates evidence.

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
Phase 42.1 evidence/attempt contract records provider, model, request, and
response identities and acceptance, but it does not make the Core mutable
status or a receipt.  Structured semantic vocabulary and automation remain out
of scope.  The evidence/attempt boundary does not add a Core field or make
attempt evidence a review authority.

## Command and scaffold boundary

`cozy document-project` has a distinct public namespace, deliberately
separate from the existing software Project knowledge-package commands and
`cozy media`.  Its project operands name existing `*.dox/` package directories,
not arbitrary descriptor files.  `inspect`, `plan`, and `verify` are derived
inspections; successful `inspect` and `verify` regenerate only the disposable
state cache specified above.  Phase 42 fixes the `dashboard` command syntax
and its protected non-authority boundary: dashboard still rejects with
`DP-PHASE-001` and creates no destination, state, receipt, attempt, registry,
or delivery evidence until a later Slice supplies rendering.  Phase 42.1
implements the same fixed explicit-output grammar as a read-only projection.
`run` dispatches one declared operation; and `scaffold` is the only command
that creates authored sources.  No command aliases or ambiguous dispatch are
part of this kernel.

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

The companion specification freezes the initial descriptor/Core grammar,
public command signatures, validation diagnostics, success headings, atomic
scaffold contents, and the DP42-02 reusable workflow definition above.  The
descriptor and Core grammars are permanently closed and are not expandable by
a later Phase.  DP42-02 MUST NOT add descriptor fields, canonical workflow
serialization, or identity calculation beyond the closed in-code Work Product
identities and references.

Phase 42.1 retains executable dashboard content, receipt evidence,
evidence-based stale propagation, review projection, and driver acceptance.
DP42-03B implements deterministic disposable state reconstruction and
DP42-03C implements recorded append-only attempts as described above; the
remaining capabilities are later Slices.  Phase 42.1 implements the already-fixed dashboard grammar
rather than expanding it.  It consumes the closed DP42-02 definition without
redefining profiles, Work Product roles, criteria, gates, operations, or
provider bindings.  SmartDox source projection and host discovery are also
outside this kernel.  No deferred capability is accepted or claimed by this
design.

## Related authorities

- Normative contract: [Document Project Specification](../spec/document-project.md)
- Phase plan: [Phase 42](../phase/phase-42.md)
- Progress ledger: [Phase 42 checklist](../phase/phase-42-checklist.md)
- Planning input only: [workflow-management proposal](../notes/document-project-workflow-management-specification-proposal.md)
  and [Content Core direction](../journal/2026/08/2026-08-30-document-project-content-core-direction.md)
