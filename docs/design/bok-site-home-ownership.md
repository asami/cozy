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

Only home writing is conditional. Special/category writers and canonical
machine metadata copying continue normally, followed by the existing
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
