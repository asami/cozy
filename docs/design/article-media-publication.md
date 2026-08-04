# Article Media Publication and BoK Integration Design

## Purpose

Phase 26 adds a Cozy-owned association and integrity boundary for optional
article media in a BoK. It consumes, but does not extend, the accepted SmartDox
article-media publication contract at commit
`fa21316973416c24bca7f8e366d65572c72720b7` through the development integration
coordinate `org.smartdox:smartdox_2.12:2.4.17-SNAPSHOT`.

SmartDox Phase 1 is closed. Its accepted commit and development coordinate are
the Phase 26 input contract; public/non-SNAPSHOT publication is not a Phase 26
start gate. The admitted SmartDox repository may be used for the corresponding
development `publishLocal` integration under repository rules.

## Responsibilities and Sources of Truth

SmartDox owns the provider-neutral `article-media-publication` input and its
article/Notice projection. A native record contains only the accepted fields:

```json
{
  "type": "article-media-publication",
  "article": { "identity": "development-process/example" },
  "variants": {
    "en": {
      "infographic": {
        "public_path": "/en/development-process/images/example.png",
        "media_type": "image/png",
        "alt": "Detailed infographic"
      },
      "video": {
        "presentation": "site-hosted",
        "status": "published",
        "provider": "cozy",
        "content_url": "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"
      }
    }
  }
}
```

The canonical locale variant keys are exact canonical SmartDox locale tags (for
example, `en` and `ja`). The infographic has `public_path` and optional
`media_type` and `alt`; the video has `presentation`, `status`, and optional
`provider`, `watch_url`, and `content_url`. It carries no Cozy hash, version,
artifact identity, repository path, provenance, or registry fields.

Cozy owns the separate `cozy.article-media-integrity.v1` association/integrity
projection. It is the validation source of truth only for Cozy artifact-path-
bearing media, not an extension of the SmartDox schema. Existing video evidence
remains owned by `metadata/video/<name>/<version>/manifest.json` and
`metadata/artifacts/repository/<name>.json` from `CozyVideoPublisher`; Phase
26 references that evidence and does not redefine it. An infographic instead
comes from an explicit `cozy.media.v1` descriptor/resource and its generated
manifest/publication result, never directory discovery.

## Correlation and Lifecycle

Each Cozy-produced infographic has `public_path` and exactly one Cozy integrity
record selected by the normalized `(articleIdentity, locale, infographic)` key.
Each Cozy-produced site-hosted video that has `content_url` likewise has exactly
one record under `(articleIdentity, locale, video)`. The corresponding
`public_path` or `content_url` equals the integrity record's site-visible
`publicPath`; no equality or integrity rule applies to a watch-only external
link or a URL-less nonprojectable video. The correlation is exact-locale only:
neither surface may substitute a different locale.

An accepted SmartDox external-link video with only `watch_url` passes through
the SmartDox boundary without a Cozy artifact record. SmartDox statuses are
exactly `draft`, `published`, and `withdrawn`. A draft or withdrawn video with
no `content_url` is valid and has no integrity correlation; one that carries
`content_url` remains path-bearing and must correlate. Cozy's independent
`publicationState` expresses artifact admission and must not be conflated with
SmartDox status.

The existing configured `src/main/publication` bundle is the only registry
container. A SmartDox entry is upserted at
`metadata/article-media/<articleIdentity>.json`, one normalized article identity
per entry with canonical locale variants sorted deterministically. A Cozy
integrity entry is upserted at
`metadata/article-media-integrity/<articleIdentity>/<locale>/<role>.json`, one
normalized tuple per entry. Both producers serialize deterministic field order;
duplicate normalized keys across configured bundles fail. `cozy bok build` uses
the existing registry loader to enumerate configured publication entries only.

`cozy bok publish-video` and the Phase 26 media-publication operation produce
or update Cozy integrity records after explicit descriptor and registered
metadata processing. Ordinary `cozy bok build` reads existing registry and
repository context only; it neither renders nor transcodes media. The operation
boundary forbids scanning `target`, work directories, generated site trees,
arbitrary repository directories, or SmartDox discovery paths.

## Invariants and Boundaries

- Serialized identities are source-relative or publication-relative; an
  absolute machine path is never serialized.
- A path must remain inside the configured repository and a declared
  site-visible public path. Identity conflict and path escape fail in every
  strategy.
- No association leaves ordinary articles and BoKs unchanged in preview and
  production.
- A SmartDox draft or withdrawn video remains valid but produces no article
  projection, player, or link. A published site-hosted video may project only
  with correlated Cozy state `published`.
- Normalized identities, locale tags, and roles form registry keys and paths.
  Empty, dot, dot-dot, or leading-slash segments are rejected before path
  construction. `repositoryPath` is relative to the configured artifact root;
  resolution and real-path checks reject lexical or symlink escapes.
- SmartDox `VideoPublication` compatibility remains exact: existing `.video`
  behavior continues; a complete exact-locale native article-media variant wins
  without producer-side schema extension, implicit merge, or locale fallback.
- Phase 26 composes the Phase 9 video registry, Phase 10 build boundary, and
  Phase 19 media descriptor boundary without reopening those closed phases.

## Lifecycle

This design fixes stable responsibility and boundary decisions. The
implementation-facing serialized contract and validation policies are
authoritative in `docs/spec/article-media-publication.md`. Phase status and
checklist progress remain in the Phase 26 ledger; exploratory history remains
non-normative in the associated note and journal.
