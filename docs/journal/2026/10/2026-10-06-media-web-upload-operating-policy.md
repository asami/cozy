# SimpleModeling.org independent media placement and Web build

Date: 2026-10-06, Asia/Tokyo.
Status: owner-selected operating direction recorded during active Phase 75;
script separation and end-to-end verification are not claimed.

## Owner direction

The owner selected the following initial operating sequence and clarified that
`runweb-production.sh` is the **Web-build-only** entry point, not a combined
media-placement-and-Web-build entry point:

1. Place prepared article-media artifacts into the canonical media warehouse
   using a separate script, then inspect the placement result.
2. Run `runweb-production.sh` independently to construct the normal production
   Web tree in `website.d`.
3. After operator review, explicitly invoke the relevant upload script(s).

For now, local media placement and construction of `website.d` are the selected
operating boundary. Neither operation implicitly authorizes remote upload.
Each operation can stop for inspection; operation 2 can be repeated without
rerunning operation 1.

## Media-placement boundary

The separate script places already prepared, explicitly selected artifacts.
It does not generate the Web site, start a new media render, or upload remotely.
Artifact authoring/rendering remains a separate prerequisite when needed.

The existing canonical mapping remains unchanged:

```text
Local:  /Users/asami/src/maven-repository/media/<ja|en>/<category>/<article>/<file>
Public: /media/<ja|en>/<category>/<article>/<file>
```

The workstation store root is configured, not hardcoded into reusable Cozy
behavior. This is the store's `media/` area, not the CAR Maven `repository/`
area. Prepared infographics, article PDFs and summary-slide PDFs use their
declared role/locale paths. Video masters retain the existing local-backup
policy; site video links retain the declared YouTube URLs.

Preservation, distinct editions and recorded gaps remain protected. An absent
artifact is reported, not silently rendered or substituted. Deployed media
payloads stay outside GitHub; source/master ownership is unchanged.

## Web-build boundary

`runweb-production.sh` retains the established SimpleModeling.org composition
of **Cozy-, Arcadia- and Antora-generated pages**. Separating media operations
does not replace that pipeline with a different site generator, static copy,
or BoK-only presentation.

Its output is the normal Web tree in `website.d`: generated pages, navigation,
CSS, JavaScript and ordinary site images/assets. The existing Arcadia page
ownership, locale routing and Antora asset behavior remain applicable.

Article-media links consume the previously configured declarations and
canonical references. Reading or projecting those references is not media
generation or placement. The Web-build entry point does not call the separate
media-placement script, invoke article-media rendering/deployment, copy the
warehouse media payloads into `website.d`, or perform remote upload.

A localhost review of `/media/...` needs a separately configured mapping to
the local warehouse. Serving `website.d` alone does not supply that mapping;
an intended public path is not proof of remote availability.

## Coordination with active Phase 75

This side conversation records the owner's direction in this new journal only.
It does not edit the active Phase 75/75.1 plans, checklists, design/spec files,
implementation, scripts, execution records or acceptance state. It neither
starts Phase 75.1 nor performs placement, generation, testing, upload, commit
or push. Existing full-test policy is unchanged.

The owning development task can use this record when reconciling the affected
design/spec and script integration at its next suitable boundary. Current
`runweb-production.sh` still mixes article-media generation and Web construction;
the selected separation is not yet an implemented-script claim. Local placement,
reference registration, Web construction, remote upload and public retrieval
remain separate outcomes with separate evidence. This journal does not redefine
Phase closure or turn a planned URL into a verified public URL.

Related records: [Phase 75](../../../phase/phase-75.md),
[Phase 75.1](../../../phase/phase-75.1.md), and the existing
[warehouse path convention](../../../notes/article-media-warehouse-path-convention.md).
