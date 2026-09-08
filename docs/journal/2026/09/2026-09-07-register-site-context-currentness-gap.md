# `register-site` Site Context Currentness Gap

Date: 2026-09-07

Status: implementation in progress; the paired registration context is now
available, while downstream acceptance and removal evidence remains open

## Context

The completed SmartDox PDF site-link work made site-aware article PDF builds
explicit. An article containing `site:[...]` must be built with both
`--site-root` and `--site-config`. Cozy passes that context to SmartDox and
binds the site configuration and document-route evidence into the resulting
`cozy.media.receipt.v2` input set.

SimpleModeling.org publication preparation exposed a follow-on Cozy gap. The
article PDFs for Part 7 (`literate-modeling`) and Part 8 (`domain-modeling`)
were generated correctly and had valid receipts for their site-aware build,
but their authoring descriptors could not be consumed directly by:

```text
cozy media register-site <media.yaml> --publication <temporary-registry>
```

Registration failed with a currentness diagnostic such as:

```text
Media resource lacks current cozy.media.receipt.v2 evidence: article-pdf-en
```

The artifact was not stale. The registration command reconstructed a
different input set because it had no way to receive or recover the site
context used by the build.

## Implementation update — 2026-09-08

`media register-site` and `media register-site-wip` now accept paired
`--site-root` and `--site-config` options, propagate them through their binding
plans, and retain the resolved context through prepared-publication
currentness. SimpleModeling.org production now registers literate-modeling
directly from its authoring descriptor. The domain-modeling adapter remains
only because that article has no authoring media descriptor; it is no longer a
workaround for missing registration-time site context.

## Confirmed implementation boundary

`CozyMediaSiteContext` and the normal `cozy media build` command accept the
paired `site-root` and `site-config` inputs. `CozyMediaPdf` passes the resolved
context to the renderer, and `CozyMediaReceipt` records site-configuration and
route evidence.

In contrast:

- `CozyArticleMediaSiteCommand.Config` contains only the descriptor,
  publication root, optional target, and dry-run flag;
- the `register-site` parser rejects unknown options and advertises no
  `--site-root` or `--site-config` parameters; and
- `CozyArticleMediaSiteBinding.plan` reconstructs the media plan with
  `CozyMedia.CommandConfig(descriptorFile, target = ...)`, without site
  context.

`CozyMediaReceipt.current` then captures the context-free plan and compares it
with the receipt created from the site-aware plan. The two input sets cannot
match. This is a command/context propagation defect at the registration
boundary, not a malformed article, missing PDF, or invalid SmartDox link.

## Impact

Any article-media package whose accepted artifact receipt depends on explicit
site context can build successfully but fail direct site registration. The
problem currently affects at least:

- article PDFs containing `site:[...]` links;
- packages whose receipt binds `site.conf` and localized document-route
  evidence; and
- ordinary and Document Project-backed SimpleModeling.org articles using the
  same article-media registration contract.

The failure is fail-closed, which is preferable to accepting stale evidence,
but it makes the normal build-to-registration workflow incomplete.

## Required Cozy capability

`media register-site` must validate currentness with the same effective site
publication context used to create the receipt. The public contract should use
one of the following equivalent designs, with one canonical implementation:

1. accept paired `--site-root` and `--site-config` options and propagate the
   resolved context into `CozyMedia.CommandConfig`; or
2. resolve a descriptor/project-owned site-context selection deterministically
   and use it for both build and registration without requiring repeated CLI
   arguments.

The second form is preferable when the selected publication profile already
provides an unambiguous project authority. Whichever surface is chosen, build,
verify, publish, `register-site`, and `register-site-wip` must not reconstruct
different receipt input sets for the same resource.

The fix must preserve these properties:

- `site-root` and `site-config` remain an inseparable pair;
- both paths are direct, normalized, non-symlink authorities;
- the article source belongs to the selected site;
- the site configuration and localized route mapping remain receipt inputs;
- a base URL, route, configuration, source, descriptor, or artifact change
  makes the old receipt stale; and
- packages that do not require site context retain their current behavior.

## Acceptance criteria

1. Build a Japanese or English article PDF containing a `site:[...]` link with
   explicit site context.
2. Register the original authoring descriptor directly into a task-private
   publication registry; no compatibility descriptor or copied artifact is
   required.
3. Confirm that the registered candidate uses the exact accepted PDF bytes and
   the original current `cozy.media.receipt.v2`.
4. Repeat for both an ordinary DoxSite article and a Document Project-backed
   article.
5. Change `site.conf`, the canonical base URL, or the localized route and
   confirm that direct registration rejects the old receipt.
6. Regenerate the PDF and confirm that direct registration succeeds again.
7. Confirm deterministic rejection of a missing half of the site-context pair,
   a symlink, a path outside the site, and an article outside the selected site.
8. Confirm unchanged direct registration for media packages without
   site-aware resources.
9. Cover both `register-site` and `register-site-wip` if both commands validate
   the same article-media receipt contract.

## Temporary SimpleModeling.org boundary

Publication could not wait for the Cozy change, so SimpleModeling.org uses the
explicit temporary identifier:

```text
COZY-GAP-REGISTER-SITE-CONTEXT-001
```

The workaround is implemented under:

```text
etc/media-registration/literate-modeling/
etc/media-registration/domain-modeling/
```

It does not edit or delete receipts and does not pretend to regenerate the
accepted PDFs or videos. Normal Cozy descriptors first copy the canonical
final artifacts and metadata into bounded adapter roots. Registration adapters
then generate ordinary receipts over the exact deliverable and authority
bytes. Any change to those bytes makes the adapter receipt stale. A
task-private preflight successfully registered all eight SimpleModeling.org
article packages with this arrangement.

This is compatibility staging, not the desired product contract. It adds
descriptor duplication, extra copy/build steps, duplicate receipt evidence,
and publication-script complexity. It should not become the standard article
workflow.

## Removal condition

After the acceptance criteria above pass in Cozy:

1. replace the Part 7 and Part 8 adapter registrations in
   `simplemodeling-org/etc/runweb-production.sh` with their original authoring
   descriptors;
2. remove the four adapter build steps;
3. remove both `etc/media-registration/...` adapter trees after confirming no
   other consumer uses them; and
4. run the full task-private eight-package registration preflight again.

The existing completion-marker and non-redirect homepage guards are unrelated
production-safety improvements and must remain.

## Relationship to prior work

This gap follows the completed
`2026-09-07-smartdox-pdf-site-link-fqn-handoff.md`. That work correctly added
site context to PDF generation and its receipt. This record covers the missing
downstream propagation of the same context when Cozy later validates that
receipt for site registration.

Candidate Triage: COMPLETED
Canonical ID: DEV-021
Source ID: COZY-GAP-REGISTER-SITE-CONTEXT-001
Disposition: NEW_PHASE
Strategy Record: docs/strategy/cozy-development-strategy.md#9-development-item-status
Target Phase: docs/phase/phase-55.md
Triaged On: 2026-09-07
