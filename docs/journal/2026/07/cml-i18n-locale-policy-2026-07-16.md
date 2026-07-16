# CML I18N Locale Policy

Date: 2026-07-16

## Context

The Phase 16 I18N baseline preserved ordered locale entries but did not yet
define locale identity, duplicate handling, deployment restrictions, or the
complete display fallback order. The runtime converted locale tags with
`Locale.forLanguageTag`, accepted duplicate canonical locales, and later built
a `Map` for display lookup. A duplicate could therefore become an implicit
last-value-wins update.

## Decision

- Locale identity is a well-formed BCP 47 language tag canonicalized through
  `java.util.Locale`; serialized identity uses `Locale.toLanguageTag`.
- `und` is the canonical language-neutral `Locale.ROOT` identity.
- Direct construction from an untagged string creates one language-neutral
  entry. Context-aware `StringCodex` decoding binds an untagged string to the
  current `ExecutionContext.locale`.
- Duplicate canonical locale identities are invalid. Construction, structured
  JSON, and locale-map Record input reject them rather than choosing a value.
- `I18nContext.allowedLocales` is optional deployment policy. No configured set
  means every well-formed locale is accepted. A configured set uses exact
  canonical locale identity and rejects nonmembers, including `und` unless it
  is explicitly listed.
- Display lookup uses this fixed order: requested exact locales, requested
  language-only locales, `und`, English, Japanese, then the first authored
  entry. Lookup never mutates or collapses stored entries.

## Consequences

Malformed tags such as `en_US` and duplicates such as `en` plus `EN` fail at
the value boundary. Locale restrictions can be supplied through the execution
context without embedding deployment policy in CML models. Existing
language-neutral literals remain concise, while request-aware decoding can
retain the active locale explicitly.
