# Cozy Arcadia Engine Contract

## Configuration ownership

`cozy.web.arcadia.Engine` takes formatting configuration from the
`CozyPlatformExecutionContext` created for each parcel. The context's
`formatContext` is wrapped in an Arcadia `FormatterContext` for that parcel.
Arcadia preserves that caller-supplied render formatter when it decorates the
render strategy. Only a parcel without a render strategy falls back to its
execution context formatter, or to Arcadia's default formatter when no
execution context is available.

There is no Engine-level formatter setting, formatter factory, HOCON key, or
other public configuration surface.

## Locale, timezone, and format behavior

Locale, timezone, and date/time formatting are owned by the platform/project
context exposed through `CozyPlatformContext`. Engine rendering uses those
values as supplied for the current parcel. Engine does not inspect the servlet
request locale and does not implicitly override the platform/project context
from an `Accept-Language` header. Query/form parsing, routing, sessions,
redirects, and response behavior remain unchanged.

## Domain-object IDs

The Engine schema represents `PROP_DOMAIN_OBJECT_ID` (`id`) as `XString`.
Arcadia `DomainObjectId` values are opaque strings at this integration
boundary: provider prefixes, punctuation, Unicode, and other text are
preserved without normalization or assumptions about Everforth IDs, numeric
IDs, UUIDs, or another provider-specific datatype. The ID column remains
present for detail/list/update/delete and console usage, and is omitted only
where the existing create/media schema matrices omit it.

## Compatibility guarantees

The public `Engine(platform, engine, name)` constructor and `execute` methods
retain their existing API shape. Existing `EngineHangar` construction,
`CozyHandler` request routing, query/form parsing, schema usage matrices,
session handling, redirects, and response writing are unchanged. The
formatter and ID policies are internal implementation details and introduce no
new public Engine configuration API. Cozy consumes Arcadia `1.0.3-SNAPSHOT`
for this coordinated formatter-ownership repair.

## Non-goals

This contract does not add per-request `Accept-Language` negotiation, a
configurable formatter factory, or a configurable ID datatype. It does not
change record dependencies, provider ID semantics, unrelated execution-context
TODOs, request handling, or Cozy strategy/phase metadata.
