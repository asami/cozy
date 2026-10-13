# Phase 75 priority: preserve historical artifacts, deploy, then restore the site

Date: 2026-10-05 (Asia/Tokyo)
Status: adopted planning update; execution and actual 71.4 pause not recorded here

The user requested a coherent pause in the long-running Phase 71.4 and priority
for SimpleModeling.org site recovery. The owning task should let its current
command finish, complete the current implementation unit's validation and
independent review, retain its Goal/base/PLAN epoch, dirty work and complete
review/repair history, and record the pause and unfinished obligations. Do not
claim Phase completion or discard its aggregate Phase 71–71.4 validation duty.

The user further clarified that video publication uses YouTube URLs, with a
repository-side backup of the master, and that PDFs, slides and infographics
must be placed at their canonical destinations first. The final clarification
puts preservation of past as well as current artifacts ahead of all placement,
regeneration and replacement.

The [Phase 75 plan](../../../phase/phase-75.md) and
[checklist](../../../phase/phase-75-checklist.md) therefore select:

1. COZY-75-10: inventory and preserve historical/current videos, PDFs, slides,
   infographics and identifying article/locale/publication records. Recover
   prior associations and URLs even when current cards omit their links. Keep
   distinct editions and originals; record missing/uncertain artifacts explicitly.
2. COZY-75-09: deploy preserved completed artifacts. Use the intended YouTube
   channel and actual URLs, retain video masters outside disposable caches,
   place other deliverables at their existing canonical public locations, and
   reflect deployment results in project-owned media declarations.
3. COZY-75-08: restore article-card links using those actual deployed URLs.
4. COZY-75-07: preserve SimpleModeling.org's intended home/category pages instead
   of overwriting them with BoK Dashboard output.

Existing Phase 75 effective-content, include, Markdown, CSS and Kroki work stays
open. Only a demonstrated dependency should precede this first delivery; fixing
the entire producer contract is not an automatic prerequisite for preserving
or deploying already completed media. Partial delivery is not full Phase closure.

The existing archive policy is owned by
`/Users/asami/src/dev2025/simplemodeling-org/tools/article-media/artifact-policy.md`.
Resolve `${SIMPLEMODELING_ARTIFACT_ROOT}` from actual configuration; do not
assume it means `/Users/asami/src/maven-repository`. `target/media` or
`target/video-build` cannot be the only approved copy. Respect existing tracking
rules for publication masters, and distinguish public URLs from local archive
locations. Preservation requires usable retained artifacts, not a new hash,
tamper-detection or unchanged-content certificate. Do not regenerate an old
artifact and describe it as the recovered original.

Select later resumption order after site recovery. Keep 71.5's dependency on
71.4 closure and 71.6's dependency on 71.5; Phase 75 is not inserted into the
sealed aggregate-validation chain. This update changes planning documents only:
no product implementation, artifact movement, upload, external publication,
Goal mutation or message to the active development task has been performed.
