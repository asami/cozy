# CML Notification Body Text Migration

date=2026-07-16
status=decided

## Context

`textus-user-notification` modeled its body as `UserNotificationBody`, a
single-field string Datatype. The wrapper added no validation or identity
semantics and prevented the ordinary CML model from becoming locale-aware by
default.

The existing `I18nText` runtime already accepts a plain string as one root
locale entry and preserves structured multi-locale entries through the shared
`I18nString` codec. `ContentBody` remains the separate rich document-body
boundary.

## Decision

- canonical CML `text` resolves to `org.goldenport.datatype.I18nText`;
- its default domain range is 1 through 8192 characters per locale entry;
- the historical nonlocalized `org.goldenport.datatype.Text` is not a
  compatibility alias for CML `text`;
- `UserNotificationBody` is removed and notification entity/command fields use
  `text` directly;
- generated Value, Create, and Update validation applies inherited predefined
  constraints;
- constrained nominal scalar attributes do not duplicate their nominal type's
  validation;
- notification display projection uses the CNCF `ExecutionContext` locale and
  does not overwrite or discard stored locale entries.

## Evidence

- `PredefinedScalarCatalogSpec` fixes the `I18nText`, locale-aware, 1..8192
  catalog contract;
- SimpleModeler generation specs verify inherited per-locale validation;
- Cozy scripted Scala 3.3.8 coverage verifies plain/multi-locale Create and
  Update behavior;
- `ComponentFactorySpec` verifies generated datastore round-trip, execution
  locale display selection, and empty/overlong rejection for notification
  bodies.

## Remaining Work

The canonical `message` family, notification lifecycle statemachine, delivery
attempt result vocabulary, and notification JSON-in-string fields remain
separate Phase 16 decisions.
