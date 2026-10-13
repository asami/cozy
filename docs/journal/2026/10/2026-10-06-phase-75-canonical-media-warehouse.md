# Phase 75 canonical media warehouse requirement

Date: 2026-10-06, Asia/Tokyo.
Status: owner-selected requirement added to Phase 75; implementation not started.

## Owner direction

The owner identified `/Users/asami/src/maven-repository` as the intended local
deployment warehouse, clarified that deployed artifacts are not pushed to
GitHub, and explicitly requested making this the supported specification for
all four article-media roles in Phase 75:

- video masters;
- infographics;
- article PDFs;
- summary-slide PDFs.

The previous physical-root selection uncertainty is resolved by this direction.
Exact participating artifact identities, editions and non-video coordinate/path
bindings still require specification and implementation. This is an addition to
COZY-75-09, not a new Phase, a completed deployment or closure of preservation gaps.

## Existing wiring and migration boundary

The existing warehouse `upload.sh` synchronizes its `repository/` subtree to
`s3://simplemodeling.org/repository/`. Public warehouse placement therefore
uses `/Users/asami/src/maven-repository/repository/` as the configured artifact
repository root and `/repository/<logical-key>` as the site-relative URL.
Copying into that local tree does not itself upload anything.

Cozy's `CozyVideoPublisher.ResolvedVideoPackage.warehousePath` already selects
`repository/video/<module>/<version>/<name>-<version>.mp4`. Preserve that
established video-publication convention. Do not turn the previous preservation
layout `simplemodeling-org/<category>/<article>/<language>/` into an independent
public hierarchy. Keep old archives as preservation/migration inputs.

Current non-video `media.yaml` declarations use article-local `_images/` public
paths and site-source publication copies. Those declarations are actual migration
inputs, not evidence that canonical non-video warehouse publication is already
supported. Article PDF and summary-slide PDF remain distinct semantic roles.
Locale and edition must participate in artifact identity without inventing a
parallel registry or a generic all-role directory.

## Development outcome selected

COZY-75-09 now includes promoting the four-role contract into the existing
article-media design/spec, freezing each role's existing-convention coordinate
and path mapping, implementing non-video warehouse placement/registration,
and migrating participating declarations and consumed registry/public URLs.
Reusable Cozy code must use configured roots and logical keys rather than a
hardcoded developer path. Focused executable specifications must cover all
roles, PDF-role separation, JA/EN, historical editions, unavailable artifacts
and unchanged existing video behavior.

All deployed payloads below the warehouse must stay Git-untracked/ignored and
must not be staged, committed or pushed to GitHub. This does not remove existing
tracking for source manuscripts, editable/SVG masters or reviewed metadata in
their owning projects. Broad untracking, history rewriting, new hash gates and
unrelated uploads are not part of this requirement.

Preservation remains first. Keep recovered copies, originals, distinct editions
and useful old links until the selected canonical placement and URL transition
are verified. Record local retrieval, public upload and remote URL verification
separately. The 2026-10-05 recovery-copy facts and explicit gaps remain retained.

Only [Phase 75](../../../phase/phase-75.md), its
[checklist](../../../phase/phase-75-checklist.md) and this journal were edited.
No design/spec promotion, product implementation, artifact placement, declaration
migration, SBT, regeneration, upload, Git commit or push occurred. The Phase
document remains an execution ledger, not design/spec authority by itself.
