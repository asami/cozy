# Article-media warehouse path convention

Recorded: 2026-10-06. Non-normative implementation note for
[Phase 75](../phase/phase-75.md), COZY-75-09.
Owner decision: [journal](../journal/2026/10/2026-10-06-phase-75-media-path-convention.md).
Promote the placement/URL behavior into the existing article-media design/spec
as part of that work; this note does not claim implementation or deployment.

## Store and layout

`/Users/asami/src/maven-repository` retains its historical directory name but is
the SimpleModeling.org data store. Its CAR Maven repository is one independent
area; article deliverables need not use Maven coordinates or its `repository/`
subtree. Configure the store root rather than hardcoding this machine path.

Use this convention unless a concrete operational reason requires an exception:

```text
<store-root>/media/<ja|en>/<category>/<article>/
  article.pdf
  slides.pdf
  infographic.svg
  video.mp4
  metadata.json  # optional
```

- Language is explicit under `media`, including Japanese.
- Category and article names are shared across languages.
- Article is the logical source filename without the extension. For a Document
  Project such as `domain-modeling.dox/index.dox`, use `domain-modeling`, not `index`.
- Category/article identifies one article; language selects the localized form.
- Deliverable names identify roles. They need not repeat category/article/language.
  Actual declared formats determine extensions; the example does not require SVG
  conversion or creation of unavailable deliverables.

Example:

```text
media/ja/modeling/domain-modeling/article.pdf
media/en/modeling/domain-modeling/article.pdf
```

Both paths identify the same article, with different language editions. The
full path provides the operational association even if no program ever uses it
for reverse lookup. A reverse-lookup API, filename-embedded identity, hash ID,
UUID allocation or additional registry is not needed for this convention.

## Optional details and publication

If useful, an adjacent metadata file can carry article URL, title, source
association or deliverable details. Its schema is not frozen by this note.
Prefer the existing project declarations as the source of those facts and avoid
creating another independently maintained source of truth. Metadata is optional
for ordinary placement; no integrity or currentness proof is implied.

The intended public mapping is
`https://simplemodeling.org/media/<ja|en>/<category>/<article>/<file>`.
The HTML site's `/en/` routing does not determine the store hierarchy. Verify
and configure the media upload mapping separately from the existing CAR Maven
upload; local placement alone is not remote availability. The existing Phase 75
YouTube destination requirement for site video links remains unchanged.

The directory convention has no version segment. Preserve historical originals
and distinct editions through the existing preservation policy before updating
current deliverables. Migrate legacy video and `_images/` declarations without
discarding originals or useful public URLs. Deployed payloads remain outside Git
under the existing Phase 75 policy; this note authorizes no move, upload or cleanup.
