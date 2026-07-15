# CML Text Operation Boundary Projection

Date: 2026-07-16

## Context

The notification driver already generated `I18nText` for `body` and validated
the canonical 1..8192 range per locale. Generated entity schema metadata also
retained that contract. Generated service-operation metadata, however, lowered
the same field to `XString` and dropped its inherited constraints. CNCF Schema,
Help, and OpenAPI therefore could not expose the authored domain contract.

During verification, generated `toRecord` was also found to call
`displayMessage` for every semantic I18N wrapper. That made a structural API
boundary choose one locale destructively. Changing the conversion exposed a
second issue: inherited `NameAttributes` and `DescriptiveAttributes` fields in
entity `toDataStore` used the external conversion and could persist a locale
map as display text.

## Decision

- Cozy and SimpleModeler preserve declared plus predefined-type constraints
  from the normalized CML model into operation fields.
- Generated operation metadata uses canonical datatype names such as `text`
  and projects requiredness and `WebValidationHints`.
- CNCF Schema, Help, and OpenAPI consume that generated metadata. Locale-aware
  OpenAPI text accepts a plain string or a locale map and applies length rules
  to each map value.
- `I18nString.toRecord` is the shared API locale-map projection. Generated
  `toRecord` uses it for semantic I18N wrappers and never selects a display
  locale.
- Datastore conversion continues to use the shared storage codec. SimpleEntity
  inherited attributes use `_to_data_store_value` just like direct fields.
- User-facing code obtains a typed I18N value and selects `displayMessage` with
  the active `ExecutionContext` locale. It must not call `Record.getString` on
  a structural locale map.

## Executable Evidence

- simplemodeling-lib `I18nStringSpec` and `I18nValueReaderSpec`: 10 passed.
- SimpleModeler `ValueScalaModelTransformerSpec`: 11 passed.
- Cozy `ModelerServiceOperationSpec`: 27 passed.
- CNCF `OpenApiProjectorSpec` and `GeneratedHelpProjectionSpec`: 5 passed.
- User Notification `OperationContractSpec`: 2 passed.
- User Notification `ComponentFactorySpec`: 22 passed after clean generation
  of 68 Scala sources with Scala 3.3.8.

## Remaining Work

- Verify actual generated form HTML consumes the projected validation hints.
- Complete the remaining predefined text-family ranges and locale policy.
- Repeat the operation/API comparison with the User Account regression driver.
