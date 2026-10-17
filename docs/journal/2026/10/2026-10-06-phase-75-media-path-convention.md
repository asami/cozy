# Phase 75 article-media path convention

The owner selected this operational convention after clarifying that the
historically named `/Users/asami/src/maven-repository` is the site's data store,
with a CAR Maven repository as one separate area:

```text
<warehouse-root>/media/<ja|en>/<category>/<article>/<deliverable>
```

Article is the logical source filename without its extension. Category/article
names are shared across languages. Simple deliverable names such as `article.pdf`,
`slides.pdf`, `infographic.svg` and `video.mp4` are sufficient: the full path, not
the basename alone, identifies the article. The owner wants this maintained for
operational convenience unless there is a concrete reason to depart from it.
No reverse-lookup feature is required. An adjacent metadata file is optional when
additional article details are useful; it is not a mandatory registry or proof.

This decision supersedes the earlier same-day proposal in
[the canonical warehouse record](2026-10-06-phase-75-canonical-media-warehouse.md)
to put article media below `repository/` and preserve the old video path as the
new canonical placement. That record remains historical evidence. Preserve
legacy originals, editions and useful URLs during migration. This convention
has no version directory; historical preservation remains a separate obligation.

The intended public path is `/media/<ja|en>/<category>/<article>/<deliverable>`,
independent of HTML article locale routing. Existing `/repository/` upload wiring
does not prove media deployment; inspect/configure that mapping in COZY-75-09
without altering the unrelated CAR Maven area.

The [implementation note](../../../notes/article-media-warehouse-path-convention.md)
collects examples, full-path association, optional metadata and migration concerns.
Updated Phase 75, its checklist and notes; design/spec promotion, implementation,
placement and upload remain open. No artifact was moved, generated or uploaded,
and no SBT, commit or push was performed for this documentation update.
