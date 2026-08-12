# Handoff: simplemodeling.org Project Configuration Support

## Authority

User direction.

Add explicit Cozy project configuration for `simplemodeling-org` and enhance
Cozy so nested article-media packages inherit shared project policy without
confusing it with standard BoK processing.

## Problem

`simplemodeling.org` is a special BoK:

- SmartDox builds the site directly.
- Cozy produces article media and registers it into the SmartDox publication
  bundle.
- Standard Cozy BoKs are built and integrated through the normal Cozy BoK
  workflow.

Current support is incomplete:

1. Video credit profiles are discovered relative to each media package.
2. Every article package must duplicate:

   ```text
   conf/cozy/video/credit-profiles/simplemodeling-org.yaml
   ```

3. Media publication profiles are repeated in every `media.yaml`.
4. A repository-level `simplemodeling-org/conf/cozy/config.yaml` is not
   reliably discovered from deeply nested media/video descriptors.
5. New packages can contain `profiles.simplemodeling-org` without the
   `articleMedia` declaration required to activate SmartDox registration.
6. `credits.profile` can be mistaken for the selector between standard BoK and
   simplemodeling.org processing, although it only controls audiovisual
   attribution.

The current Part 5 package exposes this gap:

```text
src/main/media/development-process/object-modeling/
```

Its video descriptor selected:

```yaml
credits:
  profile: simplemodeling-org
```

but Cozy initially failed because the package-local credit profile was absent.

## Required Conceptual Separation

Three independent concerns must remain separate.

| Concern | Selector | Responsibility |
|---------|----------|----------------|
| Standard versus SmartDox site registration | `articleMedia.publicationProfile` | Select the simplemodeling.org SmartDox registration workflow |
| Media publication destination | media publication profile | Resolve repository/site-local output roots |
| Video attribution | `credits.profile` | Select audiovisual credits and license presentation |

`credits.profile` must never select the site-building or article-registration
workflow.

## Target Configuration

Add this project-owned configuration:

```text
/Users/asami/src/dev2025/simplemodeling-org/conf/cozy/config.yaml
```

Recommended contents:

```yaml
project:
  id: simplemodeling-org
  kind: smartdox-site

media:
  publication-profiles:
    simplemodeling-org:
      root: .
      site-kind: smartdox

video:
  credits:
    default-profile: simplemodeling-org
```

Add the shared credit profile once:

```text
/Users/asami/src/dev2025/simplemodeling-org/conf/cozy/video/credit-profiles/simplemodeling-org.yaml
```

Move or copy the accepted existing profile from an article package, preserving
its exact schema, selectors, credit identities, and attribution text.

## Package Contract

Project configuration supplies shared defaults and profile definitions. It
must not infer article identity or register every resource automatically.

Each participating `media.yaml` must retain an explicit association:

```yaml
articleMedia:
  articleIdentity: development-process/object-modeling
  publicationProfile: simplemodeling-org
```

Each registered resource must opt in independently.

Infographic:

```yaml
- id: web-ja
  kind: infographic
  language: ja
  role: article-summary
  articleMedia:
    role: infographic
    publicPath: /ja/development-process/images/object-modeling/summary-ja.png
    mediaType: image/png
    alt: Object Modelingの要約インフォグラフィック
```

Video:

```yaml
- id: article-video-ja
  kind: video
  language: ja
  role: article-introduction
  articleMedia:
    role: video
    production: video/ja/production.json
```

Video registration remains unavailable until `production.json` contains the
accepted render, completed technical and visual QA, and a published YouTube URL
required by the existing Phase 27 contract. Human listening review may remain
pending; it is retained as evidence only and is never serialized as accepted
listening.

Phase 28 baseline correction to the earlier handoff wording: migration video
binding depends on accepted Phase 27 production evidence (technical/visual QA
and a published YouTube URL), while listening is separately retained as
non-gating evidence. This journal remains a non-normative chronology.

Approved Phase split (2026-08-12): project configuration and profile
resolution remain Phase 28; WIP local artifact staging and provider-neutral
registration are Phase 28.1; and SimpleModeling.org Part 5 integration and
regression are Phase 28.2. This journal remains pre-split source evidence and
is not normative authority for any of the three Phase contracts.

## Cozy Enhancements

### 1. Project-root discovery

Starting from a media descriptor or video project, Cozy must discover the
nearest enclosing Cozy project configuration.

Canonical marker:

```text
conf/cozy/config.yaml
```

Discovery must not depend on Git.

Requirements:

- Walk lexical parent directories from the descriptor package.
- Select the nearest valid project configuration outside the package-local
  layer.
- Require each traversed directory and selected configuration file to be
  direct, non-symlink filesystem entries.
- Normalize and verify the resulting project root.
- Stop at the filesystem root.
- Reject ambiguous or unsafe roots rather than silently selecting one.

An explicit internal `projectRoot` or configuration-root value is preferable
once discovery is complete. Individual subsystems should not repeat their own
ancestor search.

### 2. Configuration layers

Use deterministic precedence:

```text
built-in defaults
  < ~/.cozy
  < project conf/cozy
  < project .cozy
  < package conf/cozy
  < package .cozy
  < explicit descriptor/CLI selection
```

Project-local `.cozy` remains untracked local policy. Package-local overrides
remain supported for exceptional articles.

Diagnostics must report:

- selected project root;
- configuration layer;
- selected publication profile;
- selected credit profile;
- source file for each selected profile.

### 3. Video credit discovery

Update `CozyVideoCredits` so that a nested video package can resolve:

```text
<simplemodeling-project>/conf/cozy/video/credit-profiles/simplemodeling-org.yaml
```

Expected behavior:

- An explicit `credits.profile` wins over a default selection.
- `video.credits.default-profile` selects the default when the video descriptor
  omits it.
- Package-local profiles can override project profiles by exact ID.
- Duplicate profile IDs within one layer fail.
- Unknown profiles report every searched configuration layer.
- Existing standalone video projects continue to resolve package-local
  profiles.

### 4. Media publication profiles

Extend media profile resolution so project configuration can define reusable
publication profiles.

Expected merge:

```text
project media publication profiles
  < descriptor profiles
```

A descriptor-local profile of the same name is an intentional override.

Profile roots declared in project configuration are resolved relative to the
discovered project root. Descriptor-local roots remain relative to the
descriptor root.

Do not silently default `articleMedia.publicationProfile`. The package must
select it explicitly.

### 5. SmartDox registration boundary

Preserve the Phase 27 contract:

- `articleMedia` absent: no SmartDox article-media association.
- `articleMedia` present: validate exact article identity and publication
  profile.
- Resource-level `articleMedia` remains mandatory for every candidate.
- `register-site` remains explicit.
- Standard BoK build behavior must not change.
- `source-type: smartdox` alone must not authorize registration.
- `profiles.simplemodeling-org` alone must not authorize registration.
- `credits.profile: simplemodeling-org` must not authorize registration.

### 6. Inspect and plan output

`cozy media plan` and `cozy video inspect` should expose sufficient
provenance:

```text
projectRoot: /Users/asami/src/dev2025/simplemodeling-org
projectConfig: .../conf/cozy/config.yaml
publicationProfile: simplemodeling-org (project-conf)
creditProfile: simplemodeling-org (project-conf)
siteKind: smartdox
```

Do not serialize internal filesystem evidence into SmartDox public registry
records.

## Security Requirements

- Do not follow symlinks while discovering the project root.
- Project and package configuration files must be direct regular files.
- Profile directories must be direct directories.
- Reject profile roots escaping the discovered project root unless the
  existing explicit external-root contract permits them.
- Preserve current no-fallback behavior for missing or invalid profiles.
- Never infer `articleIdentity` from paths, `knowledge.id`, filenames, or
  repository names.
- Do not automatically register all media in a simplemodeling.org repository.
- Do not broaden Docker mounts or publication roots.

## Compatibility Requirements

The following must remain unchanged:

- Standard Cozy BoK build and publication behavior.
- Standalone media packages using only descriptor-local profiles.
- Existing package-local video credit profiles.
- Explicit video `credits.profile` selection.
- Host and Docker video argv.
- `cozy media build`, `publish`, `verify`, and `register-site` command syntax.
- Provider-neutral SmartDox registry schema.
- Existing Phase 27 atomicity and evidence-revalidation guarantees.

## Likely Cozy Files

Keep implementation scope focused. Likely targets include:

```text
src/main/scala/cozy/video/CozyVideoCredits.scala
src/main/scala/cozy/media/CozyMedia.scala
src/main/scala/cozy/publication/CozyArticleMediaSiteBinding.scala
```

Prefer adding one shared project-configuration/root resolver rather than
implementing ancestor discovery independently in all three files.

Likely tests:

```text
src/test/scala/cozy/video/CozyVideoCreditsSpec.scala
src/test/scala/cozy/media/CozyMediaSpec.scala
src/test/scala/cozy/publication/CozyArticleMediaSiteBindingSpec.scala
src/test/scala/cozy/publication/CozyArticleMediaSiteCommandSpec.scala
```

If a shared resolver requires additional source/test files, freeze that
expansion explicitly before implementation.

## Required Tests

### Project discovery

1. A deeply nested video descriptor discovers repository
   `conf/cozy/config.yaml`.
2. Discovery works without a `.git` directory.
3. Nearest project configuration wins.
4. Symlinked project configuration is rejected.
5. Symlinked ancestor substitution is rejected.
6. No project configuration preserves legacy package-local behavior.

### Credit profiles

1. Project-level default credit profile resolves.
2. Explicit descriptor selection overrides the project default.
3. Package-local profile overrides the project profile.
4. Unknown profile diagnostics list searched layers.
5. Duplicate IDs in one layer fail.
6. Existing standalone/package-local specs remain green.

### Media profiles

1. A descriptor selects a project-defined publication profile.
2. Its root resolves relative to project root.
3. Descriptor-local profile overrides the project definition.
4. Unknown publication profiles fail before mutation.
5. Standard media build without project configuration is unchanged.

### SmartDox boundary

1. Project `kind: smartdox-site` without `articleMedia` does not register
   media.
2. `credits.profile: simplemodeling-org` alone does not register media.
3. `profiles.simplemodeling-org` alone does not register media.
4. Explicit top-level and resource-level `articleMedia` registers
   successfully.
5. Standard BoK fixture remains on standard processing.
6. Dry-run and non-dry registration plans remain identical.
7. Evidence drift still aborts before registry replacement.

### Part 5 acceptance fixture

Use:

```text
development-process/object-modeling
```

Verify:

- article identity is exact;
- Japanese and English infographic candidates are selected separately;
- credit profile comes from project configuration;
- publication profile comes from project configuration;
- media registration still requires package-level opt-in;
- no duplicated package-local credit profile is required.

## simplemodeling-org Migration

1. Add root `conf/cozy/config.yaml`.
2. Add the shared credit profile.
3. Add `articleMedia` to the Part 5 `media.yaml`.
4. Add resource-level infographic registration declarations.
5. Add video registration declarations only after production evidence is
   accepted.
6. Migrate earlier article packages incrementally.
7. Keep existing package-local credit files during compatibility validation.
8. Remove duplicates only after project-level discovery is proven for all
   existing Japanese and English videos.

## Validation

Run focused Cozy tests through the serialized SBT wrapper:

```console
run-sbt-serial.sh --batch \
  'testOnly cozy.video.CozyVideoCreditsSpec cozy.media.CozyMediaSpec cozy.publication.CozyArticleMediaSiteBindingSpec cozy.publication.CozyArticleMediaSiteCommandSpec'
```

Then run:

```console
cozy video inspect src/main/media/development-process/object-modeling/video-ja.yaml --check-tools
cozy video inspect src/main/media/development-process/object-modeling/video-en.yaml --check-tools --tool-mode=host
cozy media plan src/main/media/development-process/object-modeling/media.yaml
cozy media register-site src/main/media/development-process/object-modeling/media.yaml \
  --publication <temporary-smartdox-publication> --dry-run
```

Finally verify that an ordinary Cozy BoK fixture still follows its original
build and publication path.

## Acceptance Criteria

The work is complete when:

- Part 5 resolves `simplemodeling-org` credits without a package-local copy.
- The selected project root and configuration provenance are visible in
  inspect/plan output.
- SmartDox registration occurs only through explicit `articleMedia`.
- Standard BoK behavior remains unchanged.
- Existing package-local configuration remains compatible.
- Focused Cozy tests pass.
- A Part 5 dry-run produces the exact locale/role registration plan without
  mutating the SmartDox publication bundle.
