# Video Credit Profile Handoff

status=handoff
created_at=2026-07-20
area=video

---

# Overview

Cozy Video needs first-class support for credits. A video build should produce
both an in-video credit page and publication-ready credit text from the assets
and voice providers actually used by the build.

The implementation must remain generic. Cozy must not hard-code Tomoharu
Asami's characters, materials, or publication wording into its core model.
Instead, Cozy should provide a generic credit catalog and profile mechanism.
Asami's personal environment should select a user-level default profile once,
so individual video projects do not repeat the same credit configuration.

The initial operational source for the SimpleModeling.org credits is:

```text
/Users/asami/src/dev2025/simplemodeling-org/src/main/media/ATTRIBUTION.md
```

That document records the recovered material sources, terms links, derived
file hashes, and current publication wording. It is evidence for constructing
the personal profile, not a Cozy-owned global policy.

---

# Required Variations

The initial use case has three independent dimensions:

- Reimu and Marisa character materials are present or absent.
- Zundamon character material is present or absent.
- The produced narration is Japanese or English.

This creates eight baseline combinations. Credit resolution must be additive
and deterministic rather than implemented as eight manually maintained text
templates.

| Reimu / Marisa | Zundamon | Japanese production | English production |
| --- | --- | --- | --- |
| no | no | Voice credits detected from the actual audio manifest, or no credit page when no credit applies. | Voice credits detected from the actual audio manifest, or no credit page when no credit applies. |
| yes | no | Touhou derivative-work notice, Kitsune Yukkuri material, and the actual Japanese voice credits. | Touhou derivative-work notice, Kitsune Yukkuri material, and the actual English voice-provider credits when applicable. |
| no | yes | Zundamon character material and the actual Japanese voice credit. | Zundamon character material and the actual English voice-provider credit when applicable. |
| yes | yes | Union of both character-material groups and all actual Japanese voice credits. | Union of both character-material groups and all actual English voice-provider credits. |

Locale must not be used as a shortcut for the voice engine. An English video
could use VOICEVOX, and a Japanese video could use another provider. The audio
manifest is authoritative for provider and voice identity; locale controls the
language used to render labels and explanatory text.

---

# Design Boundary

Credit support has four separate concerns:

1. A credit catalog describes rights holders, materials, terms, localized
   wording, links, and applicable output surfaces.
2. A credit profile selects catalog items by semantic usage signals.
3. A resolver combines project, script, asset, and audio-manifest evidence into
   one effective credit set.
4. Renderers and publication tooling project the same effective set into an
   in-video page and publication metadata.

Do not model credits as free-form text attached only to the final page. The
structured resolved credit set is the source of truth. Text and video pages are
projections of it.

The generic Cozy model must not know that `reimu` implies Touhou or that
`speakerName=四国めたん` requires a particular sentence. Those mappings belong
to a selected credit profile.

---

# Proposed Generic Model

The exact YAML schema may be refined during implementation, but it needs the
following concepts.

## Credit Item

A catalog item should contain:

- stable `id`
- category such as `original-work`, `character-material`, `voice`, or `tool`
- localized display label and publication text
- creator or rights holder
- source URL and terms URL
- obligation level such as `required` or `recommended`
- output surfaces such as `video`, `publication`, and `rdf`
- optional grouping and ordering keys

## Usage Selector

A profile maps observed usage to credit items. Selectors should support at
least:

- character or role IDs found in a script
- semantic tags on configured assets
- asset provenance and license identifiers
- audio-manifest provider
- audio-manifest voice identity
- language or locale, only for localized projection and profile-specific rules

Selectors should match semantic identifiers, not filename substrings. Filename
matching may be supported only as a migration fallback with an inspection
warning.

## Credit Profile

A profile contains:

- profile ID
- localized labels and ordering policy
- selectors
- referenced credit items
- optional presentation defaults such as title and minimum hold duration

Illustrative shape only:

```yaml
schema: cozy.video.credits.v1
profile: asami-personal
selectors:
  - when:
      any-character-id: [reimu, marisa]
    include: [touhou-derivative, kitsune-yukkuri]
  - when:
      any-character-id: [zundamon]
    include: [zundamon-character, zundamon-sakamoto-ahiru]
  - when:
      audio-provider: voicevox
      voice-identity: 四国めたん
    include: [voicevox-shikoku-metan]
credits:
  # Structured credit items omitted from this example.
```

The schema should also permit explicit `include` and `exclude` overrides in a
video descriptor for unusual projects. Normal Asami projects should not need
those overrides.

---

# Profile Discovery And Defaults

Use the existing Cozy configuration layers rather than introducing a separate
personal configuration mechanism. `CozyProjectYamlConfig` already resolves:

1. `~/.cozy`
2. `<project>/conf/cozy`
3. `<project>/.cozy`

Credit profile discovery should use equivalent well-known directories, for
example:

```text
~/.cozy/video/credit-profiles/*.yaml
<project>/conf/cozy/video/credit-profiles/*.yaml
<project>/.cozy/video/credit-profiles/*.yaml
```

Later layers override earlier profiles with the same ID. A project may provide
an organizational profile without changing Cozy itself.

The default profile should resolve in this order:

1. explicit profile in `video.yaml`
2. project Cozy configuration
3. user Cozy configuration
4. no profile

Asami's one-time user configuration should be equivalent to:

```yaml
video:
  credits:
    default-profile: asami-personal
```

The `asami-personal` profile lives under `~/.cozy/video/credit-profiles/`.
After that setup, a video should require no credit-specific setting when its
character and voice metadata are sufficient for automatic resolution.

The user-level profile is intentionally not bundled in Cozy. A distributable
SimpleModeling.org project may later provide an organization-level equivalent
under `conf/cozy` when reproducibility on another machine is required.

---

# Usage Evidence

## Character Materials

Current scripts expose character IDs such as `reimu`, `marisa`, and
`zundamon`. This is enough for the first personal profile, but the generic
asset contract should gain semantic tags so a renamed role remains resolvable:

```yaml
assets:
  reimu:
    path: assets/reimu_closed.png
    kind: character-material
    tags: [character.reimu, material.kitsune-yukkuri]
    license: LicenseRef-Kitsune-Yukkuri
    provenance: creator:kitsune
```

The profile may initially support character-ID selectors while semantic asset
tags are added to existing packages.

## Voice Providers

`VideoAudioManifestEntry` already records:

- `provider`
- `voiceIdentity`
- `voiceId`
- `modelIdentity`
- `executionMode`

Credit resolution should consume these fields after synthesis. It must not
trust stale authored script metadata when a different synthesis path produced
the final audio.

Every synthesis provider, including external or macOS-based English synthesis,
must emit the same provenance fields. If a provider does not require a public
credit, its profile item may be omitted or marked informational.

---

# Asami Personal Profile

The first personal profile should represent the following policy.

## Reimu And Marisa Present

Include:

- a localized statement that the video is a Touhou Project derivative work
- Team Shanghai Alice as the original-work attribution
- Kitsune Yukkuri by Kitsune as the character material
- `https://ci-en.net/creator/34363/article/1770533`

For Japanese VOICEVOX production, resolve the actual voices independently:

- Reimu role with Shikoku Metan: `霊夢役：VOICEVOX:四国めたん`
- Marisa role with Kasukabe Tsumugi: `魔理沙役：VOICEVOX:春日部つむぎ`

Do not generate `VOICEVOX:霊夢` or `VOICEVOX:魔理沙`.

## Zundamon Present

Include:

- Zundamon character attribution
- standing-picture material by Sakamoto Ahiru
- `https://seiga.nicovideo.jp/seiga/im10788496`

When the actual audio manifest reports the Zundamon VOICEVOX voice, include:

```text
VOICEVOX:ずんだもん
```

The image credit is triggered by character-material usage. The voice credit is
triggered by audio-manifest evidence. Either may occur without the other.

## Japanese And English Audio

Japanese output localizes headings such as `使用素材・音声` and uses the exact
Japanese VOICEVOX credit strings required by the selected libraries.

English output localizes the presentation, but still preserves legally
significant proper names and exact credit strings. If the actual English audio
was produced by macOS system voices, do not copy Japanese VOICEVOX credits into
the English publication. If English audio was produced by VOICEVOX, include the
actual VOICEVOX speaker credits despite the English locale.

---

# Output Surfaces

One resolution pass should produce at least:

```text
<video-target>/credits/credits.json
<video-target>/credits/credits.md
<video-target>/credits/renderer-props.json
```

Meanings:

| File | Meaning |
| --- | --- |
| `credits.json` | Structured effective credit set with profile, evidence, source, and terms links. |
| `credits.md` | Localized block ready to append to YouTube or another publication description. |
| `renderer-props.json` | Renderer-neutral credit-page projection. |

The build and RDF manifests should record the selected profile and the digest
of `credits.json`.

The default composition order should be:

```text
content
-> summary when present
-> credit page when the effective set is non-empty
-> final URL page
```

The credit page should have a configurable hold duration and remain readable
without animation. If the effective set is empty, do not insert an empty page.

The full URLs may be placed in publication text when they would make the video
page unreadable. The video page must still show enough attribution to identify
the original work, material author, and voice provider.

---

# Command Behavior

`cozy video inspect` should show:

- selected credit profile and the configuration layer that selected it
- detected characters and semantic asset tags
- detected audio providers and voice identities
- included credit items and the evidence that selected each item
- warnings for ambiguous or unresolved external assets

`cozy video build` should resolve credits after audio manifests are available,
generate all credit projections, render the credit page, and assemble it before
the final URL page.

`cozy media verify` and video verification should fail when:

- an asset declares a required credit that is absent from the effective set
- a VOICEVOX audio manifest has no matching required speaker credit
- a selected profile references an unknown item
- localized required text is missing for the publication locale

Recommended credits may produce warnings rather than failures.

---

# Implementation Areas

Expected Cozy areas include:

- a new renderer-neutral credit catalog, profile, selector, and resolver model
- `VideoProject` decoding for optional credit overrides and locale
- `CozyVideoAssets` semantic tags and credit references
- synthesis manifests for all voice providers
- `VideoPlan`, inspect output, build manifests, and RDF projection
- the Remotion workspace generator and final assembly order
- scaffold examples and profile-driven video documentation

Keep profile loading and resolution separate from Remotion. Other renderers
must be able to consume the same `renderer-props.json` projection.

---

# Test Plan

Add executable specifications for:

- all eight Reimu/Marisa, Zundamon, and Japanese/English baseline combinations
- additive union and stable ordering when both character groups are present
- de-duplication of shared VOICEVOX tool credits and repeated URLs
- image-only and voice-only Zundamon usage
- English output generated by macOS voices without false VOICEVOX credits
- English output generated by VOICEVOX with the actual speaker credits
- a stale script voice overridden by authoritative audio-manifest evidence
- user default, project default, and explicit profile precedence
- explicit include and exclude overrides
- no empty credit page
- verification failure for unresolved required credits
- publication Markdown and renderer projection generated from the same
  structured effective set

Fixtures must use temporary configuration roots rather than a developer's real
`~/.cozy` directory.

---

# Non-Goals

The first implementation does not need to:

- interpret legal terms automatically
- download external assets or terms pages
- decide whether a use is commercial
- embed personal character images in Cozy
- upload a video to YouTube
- replace a rights holder's current terms with cached Cozy policy

Cozy records and projects declared obligations. The user or organization
remains responsible for maintaining the selected profile against current
rights-holder terms.

---

# Acceptance Criteria

The feature is ready when:

- Cozy has a generic structured credit model with no Asami-specific core rules
- a user-level default profile can be selected once under `~/.cozy`
- Asami's profile automatically resolves the required variations from actual
  character and audio usage
- builds generate both an in-video credit page and YouTube-ready Markdown
- the credit page is inserted before the final URL page
- inspect output explains every selected credit
- required-credit omissions fail verification
- the eight baseline combinations have executable specifications
- the guide explains how another user or organization defines its own profile
