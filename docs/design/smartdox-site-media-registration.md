# SmartDox Site Media Registration Design

## Purpose

Phase 27 defines a normal `cozy.media.v1` package boundary for registering
article media in a SmartDox publication bundle. `simplemodeling.org` is a
special BoK: SmartDox builds its site directly, rather than receiving a Cozy
BoK build. Article source discovery remains normal SmartDox work; media
registration is an additional, explicit operation after accepted media exists.

The implementation-facing contract is
`docs/spec/smartdox-site-media-registration.md`. This document fixes the
responsibility and lifecycle boundaries that the specification implements.

## Responsibilities

SmartDox owns the provider-neutral article-media schema/model, registry
validation, and article/Notice site projection. It may expose or use a generic
safe registry writer internally. That facility does not make it the primary
user-facing package orchestrator.

Cozy owns the user-facing normal-media-package command
`cozy media register-site`. It understands explicit `cozy.media.v1` resources,
their publication-profile evidence, and accepted external-video production
evidence. Skills invoke supported product commands after their respective
accepted outputs exist; they never hand-edit publication bundles. A future
standalone registration skill may orchestrate the command.

The command produces provider-neutral SmartDox registry input. A later `dox
site` consumes that input. Registration neither creates the article source nor
changes SmartDox's ordinary source discovery.

## Explicit Association Boundary

The descriptor opts into site registration through a top-level `articleMedia`
block for this command. It supplies an explicit `articleIdentity`, independent
of `knowledge.id`, and a selected `publicationProfile`. Each participating
resource separately opts in through its own `articleMedia` block. Resources
without that block are untouched. Any present top-level or resource
`articleMedia` block is a non-null object with exactly the canonical keys;
aliases, unknown keys, null/partial blocks, and empty required strings fail
preflight. If the top-level block is absent, `register-site` fails because no
site association is declared. Without `--target`, all and only complete
resource blocks are candidates; absent blocks are ignored but zero candidates
is an error. With `--target`, the named resource must exist and contain a
complete valid block; a target never opts a resource in. These failures happen
before publication mutation.

The nested `resources[*].articleMedia.role` is the registration role and maps
exactly to the resource kind: `infographic` to `kind: infographic` and `video`
to `kind: video`; other combinations fail. Existing top-level
`resources[*].role` (such as `article-summary`) remains the independent
media-package production role and is not consulted for registration.

This is a declared association, not an inference from a package name,
filesystem path, generated site, target tree, or host behavior. The selected
profile is the only evidence used to validate an infographic's published
destination. The explicitly declared resources are selected; the caller may
narrow those declarations with `--target`, which never creates an association.

## Registry and Transaction Boundary

Phase 27 reuses Phase 26's configured publication-bundle container and its
single real-publication-root lock. The strict SmartDox record belongs at
`metadata/article-media/<articleIdentity>.json`; all recognized strict and
Cozy-integrity entries beneath one article identity form the Phase 26 ownership
unit. The operation reuses the one discovered owner, otherwise the canonical
`article-media` owner, and fails rather than resolving multiple owners.

Registration is a deterministic exact-key merge by `(articleIdentity, locale,
resources[*].articleMedia.role)`. It preserves other
`resources[*].articleMedia.role` values/locales, unrelated articles, generic
bundle entries, and existing Cozy integrity entries. It performs complete requested
candidate preflight before mutation, holds the lock from snapshot through
atomic bundle replacement, and revalidates the registry snapshot and every
selected candidate's original evidence immediately before replacement. Any
descriptor/profile/resource mapping or media path, identity, bytes, or parsed
metadata drift aborts before mutation; all candidates pass together, with no
partial registration. Internal evidence hashes and path identities remain
transactional and are never emitted in the provider-neutral SmartDox record.

Site-local infographics and watch-only external videos are strict SmartDox
input. They create no `cozy.article-media-integrity.v1` entry: unlike Phase 26
artifact-path-bearing media, their record has no Cozy artifact correlation.

## Media Evidence Boundary

An infographic is eligible only when its named profile exists both in the
descriptor and selected resource publications, and its resolved destination is
an existing direct regular non-symlink file beneath the bound profile root. Its
public path is a separately explicit accepted SmartDox root-relative path; it
is never derived from that filesystem destination. Infographic and video
evidence are both captured during complete preflight and revalidated under the
same publication-root lock immediately before replacement; the specification
defines the evidence snapshot and comparison contract.

A video is eligible only when its explicit descriptor-root-relative `production`
path resolves to a direct regular non-symlink JSON file with no symlink
traversal. The production file is exact-path JSON evidence; the normative
specification fixes its object-root grammar, required statuses, accepted
YouTube URL forms, and
unchanged URL emission. Its production category/article compose the exact
declared article identity and its language equals the resource language.
Rendering requires completed technical and visual QA, and the external
publication requires a published YouTube URL. Human listening review may remain
pending; it is evidence only and is never serialized as accepted listening.

## Lifecycle and Exclusions

Infographic workflows register only after the accepted published infographic
exists. Video workflows register only after accepted published external-video
production metadata exists. The command may plan without mutation, but it does
not generate/copy media, upload a video, build/deploy a site, invoke `cozy
bok`, scan arbitrary target/site/repository trees, or fall back to host
behavior.

This boundary does not introduce `cozy bok publish-media`, a Cozy BoK,
descriptor enumeration, BoK build/repository/staging coupling, site/artifact
deployment coupling, media generation, remote publication, YouTube mutation,
or a SmartDox Phase 1 schema extension.

## Acceptance Direction

The implementation acceptance uses a dedicated synthetic Part 5 normal-media
package fixture, not a Part 4 artifact or output. Its distinct `knowledge.id`
and `articleIdentity` must exercise existing `kind: infographic` resources
with an independent top-level `resources[*].role` (such as
`article-summary`), JA/EN infographic and published external-video records,
deterministic candidate output, dry-run immutability, exact-locale output,
preservation, duplicate/failure atomicity, and pinned SmartDox/Dox article and
Notice projection. `development-process/part-5` is only a fixture identity and
does not prescribe a future Part 5 editorial title.
