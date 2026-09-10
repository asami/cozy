# Document Project Production Workflow Greenfield Decision

Date: 2026-09-07

## Context

The first review of the proposed SimpleModeling.org Document Project
publication-preparation skill incorrectly treated its future replacement of
the current article workflow as a compatibility transition. That framing is
rejected. Preserving any legacy workflow, receipt layout, provider adapter,
artifact placement, or execution sequence would introduce two authorities and
make the new workflow harder to reason about.

The intended system is a new Document Project production workflow. A Document
Project owns its Content Core, selected Work Products, typed operations,
provider bindings, attempts, receipts, currentness, review projections, and
publication export. SimpleModeling.org is one downstream publication target;
it is not the authority for the production workflow.

## Decision

Design and implement the Document Project production path as a greenfield
contract. Do not use the current publication-preparation workflow or
`cozy-article-media` as a transitional provider adapter. Do not preserve their
internal operation sequence, review evidence, receipt representation, or
artifact layout.

The only downstream obligation is an explicit publication boundary: a
Document Project must be able to export its selected, current public Work
Products in a form consumed by the declared publication target. That is a new
producer/consumer contract, not compatibility with an earlier workflow.

The target structure is:

```text
Document Project
  |- Content Core
  |- Workflow Definition
  |- derived Workflow Instance
  |- Work Products
  |- typed Provider Bindings
  |- append-only Operation Attempts
  |- outputs, receipts, and currentness
  |- review projections
  `- Publication Export
             |
             `-> SimpleModeling.org production-site build
```

## Native execution contract

Every production operation must run through a Document Project-owned public
command such as:

```text
cozy document-project run <project> --operation <logical-operation>
```

The implementation must:

1. resolve one admitted logical operation and one typed provider binding;
2. validate its exact input authorities and prerequisite Work Products;
3. execute the provider;
4. receive a typed result containing output identities and diagnostics;
5. validate output paths, media types, hashes, and receipts;
6. append the Operation Attempt; and
7. derive the new Workflow Instance currentness from that evidence.

These steps form one owned execution boundary. A subordinate skill must not
generate files and then hand-edit or retrospectively adopt Document Project
evidence. If no provider exists, the operation remains explicitly blocked.
Recording an attempt with `outcome: recorded`, empty outputs, and no receipt is
not successful execution.

## Publication Export

Introduce a first-class export operation rather than exposing the project
directory or teaching a publication target about project internals. Its exact
grammar remains a later design decision; the conceptual form is:

```text
cozy document-project export <project> \
  --target <publication-target> \
  --save <destination>
```

Export includes only selected public Work Products that are current and have
valid production receipts. It excludes Content Core internals, AI dialogue,
candidate history, Operation Attempts, private review evidence, raw media, and
generated state caches. Export records its own input identities, output
manifest, and receipt so that the publication target can reject stale or
partial input without reconstructing Document Project state.

## Verification policy

Content verification must not render PDF pages, PowerPoint slides, or video
frames to PNG/JPEG by default. Raster review significantly increases elapsed
time and AI usage, and it is not a normal prerequisite for production
currentness.

Use a closed verification policy:

```text
structural  # default
visual      # only by explicit user request
```

`structural` verifies semantic authority, Work Product dependencies, hashes,
receipts, page or slide counts, text presence, video duration, dimensions,
streams, codecs, and other lightweight machine-readable properties. It does
not create PDF page images, slide previews, montages, or extracted video
frames.

`visual` may create the minimum review representations needed for the
explicitly selected artifact. An agent must not promote `structural` to
`visual` merely because a visual defect might exist. It reports the suspected
defect and waits for an explicit visual-verification request.

An image that is itself a selected public Work Product, such as an infographic
PNG, remains a normal production output. This is distinct from temporary
inspection rasterization.

## Confirmed current Cozy capability

The existing implementation already supplies much of the workflow kernel:

- `cozy.document-project.v2` authored state;
- Content Core candidate, feedback, revision, and explicit acceptance;
- Workflow Definition, Work Products, criteria, gates, and provider-binding
  vocabulary;
- evidence-derived state and append-only attempt storage;
- `inspect`, `plan`, `verify`, `dashboard`, and review projections; and
- Content Core presentation semantics and cross-media confirmation machinery.

## Missing capability at the 2026-09-07 decision point

The following gaps were the decision-time inventory that prevented the new
production workflow from replacing the current publication path. They are not
a current implementation-status gate.

1. **Provider dispatch**: `document-project run` currently records deferred
   execution and produces no outputs or receipt.
2. **Typed provider result and atomic evidence closure**: there is no native
   end-to-end path from provider execution to validated output, receipt,
   attempt, and derived currentness.
3. **Executable planning state**: logical selection, prerequisite readiness,
   provider availability, output currentness, and immediate executability must
   be distinct closed states.
4. **Verification mode**: `structural` and explicitly requested `visual`
   verification need a typed policy propagated to providers and reviewers.
5. **Presentation-semantics workflow integration**: the accepted semantic
   authority and cross-media confirmation still need the planned Phase 49
   integration into normal Work Product state and operations.
6. **Publication Export**: no first-class public-output bundle and receipt are
   currently produced for a target such as SimpleModeling.org.

The presence of a `simplemodeling-org` workflow profile is not, by itself,
end-to-end production support. A profile selects workflow structure; it does
not prove that every selected provider can execute or that a publication
export exists.

## Current status after Phases 49.3, 56, and 56.1

- Phase 49.3 is closed for presentation-semantics currentness and operational
  driver acceptance.
- Phase 56 is complete for typed native `run` provider dispatch and result
  vocabulary; Phase 56.1 is complete for validated atomic output, receipt,
  append-only-attempt, and derived-currentness closure.
- Phase 56.2 remains the current child for closed executability-state
  projections and structural-by-default, explicitly selected visual
  verification.
- Publication Export and the SimpleModeling.org target binding remain separate
  Phase 57 work.

## Phase 56.2 implementation progress on 2026-09-10

P562-01A implemented the closed derived executability projection and the
structural-by-default verification grammar. It exposes distinct logical
selection, prerequisite readiness, provider availability, accepted-output
currentness, and immediate executability values through `inspect`, `plan`,
structural `verify`, and Dashboard; the existing Presentation Semantics state
is consumed rather than duplicated. Explicit visual verification is limited to
one selected current native Article review output and its bounded temporary
representation. It creates no provider invocation, attempt, accepted evidence,
production receipt, currentness change, or public-image substitute.

This is an implementation progress record only. Focused validation, review,
full Cozy validation, and release closure remain unchecked in the Phase 56.2
checklist; the Phase is not closed.

## Initial implementation order at the 2026-09-07 decision point

Use the following dependency order:

1. typed provider execution and result vocabulary;
2. atomic attempt/output/receipt/currentness closure;
3. closed executable-planning states;
4. typed `structural` / `visual` verification policy;
5. Phase 49 presentation-semantics workflow integration;
6. first-class Publication Export and SimpleModeling.org target binding; and
7. a new Document Project publication-preparation skill that orchestrates only
   these native contracts.

The skill must not compensate for missing product behavior with an adapter to
the previous workflow. Until the required native operation exists, it reports
the exact blocked Work Product and missing Cozy capability.

## Non-goals

- no compatibility mode, migration reader, bridge, or transitional adapter;
- no retrofit of earlier articles;
- no dual authority between Document Project and a separate media workflow;
- no manual evidence adoption or fabricated success attempt;
- no implicit publication, deployment, upload, push, or commit; and
- no default raster-based visual inspection.

## Consequence for the proposed skill

The current `smorg-document-project-publication-prep` draft should be rewritten
as a client of the completed native Document Project production and export
contracts. Its references to staged succession, transitional
`cozy-article-media` execution, externally returning receipts to the evidence
model, and an "equivalent publication input" do not belong in the greenfield
design.

Candidate Triage: COMPLETED
Canonical ID: DEV-019
Disposition: NEW_PHASE
Strategy Record: docs/strategy/cozy-development-strategy.md#9-development-item-status
Target Phase: docs/phase/phase-56.md
Triaged On: 2026-09-07

Candidate Triage: COMPLETED
Canonical ID: DEV-020
Disposition: NEW_PHASE
Strategy Record: docs/strategy/cozy-development-strategy.md#9-development-item-status
Target Phase: docs/phase/phase-57.md
Triaged On: 2026-09-07
