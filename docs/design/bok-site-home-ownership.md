# BoK Site Home Ownership Design

Status: normative PHASE-71.3 / P710-04-SITE / S17 design

The [home ownership specification](../spec/bok-site-home-ownership.md) assigns
the home to the producer selected by existing `bok.arcadia.enabled` in
[BOK-UC-07](bok-use-cases.md#bok-uc-07-build-the-local-generated-site).
`CozyBok.build` already runs configured Arcadia and copies its output into
`website.d` before Cozy page generation. The two locale branches therefore
call the normal home writer only when Arcadia is disabled.

This selection applies to `website.d/index.html` in `SingleLocaleRoot` and
`website.d/<language>/index.html` in `MultiLocaleSubdirs`. Arcadia-enabled
homes retain their authored bytes. Arcadia-disabled builds retain the existing
[Dashboard design](bok-dashboard-audience-actors.md). There is no
existence-based fallback, alternate Dashboard path, public configuration
addition or lifecycle change.

The original Phase 71 contract makes home writing conditional. Special writers
and canonical machine metadata copying continue normally, followed by the existing
[metadata finalizer](bok-metadata-finalization.md), whose allowlist does not
own rendered HTML. The [generated-knowledge boundary](bok-generated-knowledge-boundary.md)
continues to separate durable source from generated resources.

The two scenarios in [CozyBokSpec](../../src/test/scala/cozy/CozyBokSpec.scala)
exercise real `BuildConfig.create` and `CozyBok.build` in both locale modes.
A recording runner emulates only external Arcadia home output and preserves
existing SmartDox metadata fixtures. Generated UTF-8 sentinels specify exact
home preservation; a disabled matrix specifies the normal Dashboard and
continued special pages/metadata. This observation adds no production
equality, digest or currentness mechanism. Native acceptance remains separate.

## Phase 75.1 category extension (2026-10-07)

P751-02D extends conditional writing to individual categories while retaining
the original configuration-only home semantics. The build normalizes the
external producer's fixed `arcadiasite.d` output into the configured Arcadia
destination and copies that destination into the configured website before
the BoK page writer runs.

Inside the category writer, normalize the website root and category page to
absolute paths, relativize the page against that website root, and resolve
the relative path beneath `config.arcadiaSitePath`. Skip only that category's
existing write when Arcadia is enabled and ordinary `Files.isRegularFile`
holds for this exact producer counterpart. This follows existing directory
copy semantics without a new symlink policy. The final website's existence
and source templates are not ownership evidence. Missing counterparts use
the unchanged category renderer, independently for each locale/category.

The [category executable specification](../../src/test/scala/cozy/bok/CozyBokArcadiaCategoryOwnershipSpec.scala)
uses real `BuildConfig.create` and `CozyBok.build`, with a local runner
emulating only external products. Authored content/anchors, fallback article
links and three real CSS targets, special pages and canonical metadata are
observable composition evidence. Native production and browser acceptance
remain separate obligations.
