# Document Project Presentation Semantics Workflow Integration Specification Proposal

Date: 2026-09-06

Status: specification proposal; non-normative; Phase 49 planning input

## Purpose

Complete the operational plumbing that follows Phase 48. Phase 48 owns only the
Document Project scaffold and generated
`content/presentation-semantics-<language>.yaml` authoring surface. This
proposal connects that authority to the existing Document Project workflow,
state, inspection, planning, verification, dashboard, review, receipt, and
currentness machinery.

No new presentation-semantic schema is introduced. The implementation consumes
the closed Phase 46 `cozy.content-core.presentation-semantics.v2` contract and
the closed Phase 46.1 cross-media projection / confirmation / receipt / coverage
kernel.

## Authority and dependency model

Register the existing presentation-semantics document as a first-class Work
Product in the immutable `document-production` workflow.

Recommended stable identity and role:

```text
presentation-semantics : authority
```

Its direct semantic predecessor is `content-core`.

The intended dependency shape is:

```text
content-core
    |
    v
presentation-semantics
    |------------------+------------------+
    v                  v                  v
article-source      visual-pages      video-storyboard
    \                  |                  /
     +-----------------+-----------------+
                       v
          presentation-confirmation
```

The workflow definition remains Cozy-owned immutable code. Do not copy the DAG
or provider bindings into `document-project.yaml`.

## State model

The derived Document Project state must distinguish at least these semantic
conditions:

- `missing`: the required presentation-semantics authority does not exist;
- `authoring-incomplete`: the Phase 48 authoring surface exists but does not yet
  form an accepted Phase 46 semantic aggregate;
- `invalid`: authored semantics are present but fail strict `DP-SEM-*`
  validation;
- `current`: strict validation succeeds and all direct identities match;
- `stale`: a bound upstream identity, especially Content Core, changed after
  the semantic authority was authored/accepted.

`authoring-incomplete` is not successful semantic validation. It must not
create a valid semantic identity, projection receipt, or coverage success.

## Verify integration

Extend

```text
cozy document-project verify <project>
```

so the selected presentation-semantics authority is loaded and validated by the
existing Phase 46 implementation.

Verification must reuse the closed `DP-SEM-*` diagnostics. It must not add a
second permissive validator or compatibility alias reader.

When strict semantic validation succeeds, verification must prove that the
accepted `CozyDocumentPresentationSemantics.Validated` value is admissible to
the Phase 46.1 cross-media projection boundary. Projection or coverage failure
must be reported rather than converted into placeholder success.

## Inspect integration

`inspect` should expose a compact semantic summary without requiring YAML
interpretation. At minimum show:

- schema identity;
- bound Content Core identity/currentness;
- semantic status;
- Story Step count;
- Story Transition count;
- Explanation Structure count;
- slide projection availability;
- video projection availability; and
- semantic coverage state when available.

For incomplete authoring, counts or projections that cannot yet be derived are
reported as unavailable rather than invented.

## Plan integration

`plan` must place Presentation Semantics between Content Core and the dependent
article/slide/video work.

Example:

```text
1. Content Core
   current

2. Presentation Semantics
   authoring incomplete
   next: define Story Flow and Explanation Structures

3. Article Source
   blocked by Presentation Semantics

4. Slide Projection
   blocked by Presentation Semantics

5. Video Storyboard
   blocked by Presentation Semantics

6. Cross-media Confirmation
   blocked
```

The plan is a deterministic projection of the workflow/state contract; it does
not execute authoring or provider work.

## Dashboard integration

The Dashboard treats Presentation Semantics as a normal production stage.
When it is the first blocker, the primary action surface should explain the
required authoring authority and next safe action, for example:

```text
Define Story Flow and Explanation Structures in
content/presentation-semantics-ja.yaml
```

The Dashboard must not generate semantic content, mutate the file, or imply that
AI-generated semantics are accepted authority.

## Public cross-media confirmation operation

Expose the accepted Phase 46.1 integrated cross-media confirmation through the
Document Project public workflow. Prefer reuse of the existing review command
surface, for example:

```text
cozy document-project review <project> --kind presentation
```

The exact public spelling is a Phase 49 design output, but callers must not need
to invoke package-private Scala implementation objects.

Recommended generated outputs:

```text
target/document-project/presentation-confirmation.html
target/document-project/presentation-confirmation.receipt.yaml
```

The confirmation artifact is distinct from `article-review.html`:

- `article-review.html` reviews the article-specific expression;
- `presentation-confirmation.html` reviews shared Story Flow and Explanation
  Structures plus article/slide/video mappings.

Reuse the Phase 46.1 renderer and receipt implementation. Do not create a
parallel renderer.

## Currentness and stale propagation

Document Project state must project the already accepted identity boundaries.
At minimum:

```text
Content Core change
  -> presentation-semantics stale
  -> article/slide/video semantic projections stale
  -> presentation-confirmation stale
```

and:

```text
presentation-semantics change
  -> dependent projection identities change
  -> prior confirmation receipt stale
```

No automatic write-back is performed merely to make stale content current.
The author or Codex explicitly revises the authority and reruns verification /
review.

Receipt currentness remains distinct from semantic completeness. A current
receipt cannot substitute for Phase 46.1 semantic-coverage verification.

## Article 9 acceptance boundary

Article 9 is the first intended end-to-end operational driver after Phase 48
and Phase 49.

Phase 49 acceptance should prove that a Phase-48 scaffolded project can move
through normal Document Project operations without private implementation calls:

```text
scaffolded semantic workspace
  -> semantic authoring
  -> verify
  -> inspect / plan / dashboard
  -> public cross-media confirmation
  -> stale/currentness propagation
```

Full editorial completion or publication of Article 9 is not required to close
Phase 49. The driver must be sufficient to demonstrate the operational path and
remove the need for an out-of-band hand-built confirmation HTML.

## Non-goals

- changing Phase 46 semantic schemas or diagnostics;
- changing `cozy.content-core.v1`;
- changing the Phase 46.1 semantic projection algorithm merely for workflow
  convenience;
- automatic Story Flow or Explanation Structure acceptance;
- an Article-9-specific profile;
- new PowerPoint/PDF/video renderer features;
- migration of Article 8 or earlier articles;
- publication, deployment, registration, upload, push, or external-service
  mutation.

## Phase relationship

```text
Phase 46    typed presentation semantics
    |
Phase 46.1  cross-media projection / confirmation kernel
    |
Phase 48    scaffold authoring surface
    |
Phase 49    Document Project workflow integration
    |
Article 9   first production use
```

Phase 49 consumes all three predecessors and must not reopen their accepted
contracts.
