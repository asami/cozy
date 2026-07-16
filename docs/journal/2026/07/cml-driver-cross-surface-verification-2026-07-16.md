# Phase 16 Driver Cross-Surface Verification

Date: 2026-07-16

## Context

Phase 16 changes are not complete when generated Scala types alone are
correct. The generated operation metadata must remain the single contract
projected into validation, datastore values, HTML forms, REST/OpenAPI, and
Help. The two regression drivers therefore need executable comparisons using
their real generated components.

## Verification Profiles

User Notification uses `UserNotificationDeliveryProvider` on
`Delivery.createDeliveryAttempt.provider`:

- generated nominal scalar validation enforces length 1..64;
- datastore encoding preserves the scalar string value;
- the standard CNCF Form renderer publishes the optional field and its
  `minlength` / `maxlength` attributes;
- REST/OpenAPI publishes `UserNotificationDeliveryProvider`, the same range,
  and public confidentiality;
- Help publishes the same datatype, multiplicity, and range.

User Account uses `UserAccountExternalSubjectId` on the automatically
generated `Entity.searchUserAccount.externalSubjectId` operation:

- generated nominal scalar validation enforces length 1..512;
- datastore encoding preserves the scalar string value;
- the standard CNCF Form renderer publishes the optional field and its
  `minlength` / `maxlength` attributes;
- REST/OpenAPI publishes `UserAccountExternalSubjectId`, the same range, and
  personal confidentiality;
- Help publishes the same datatype, multiplicity, and range.

The account profile intentionally uses an automatic entity operation. This
proves that the contract is projected from generated entity metadata rather
than only from explicitly authored service operations.

## Executable Evidence

The executable specifications are:

- `textus-user-notification`:
  `org.simplemodeling.textus.usernotification.GeneratedBoundaryProjectionSpec`;
- `textus-user-account`:
  `org.simplemodeling.textus.useraccount.GeneratedBoundaryProjectionSpec`.

Both focused specifications pass against Scala 3.3.8 generated sources. The
full User Notification suite passes 34 tests and the full User Account suite
passes 96 tests. CAR lint reports no deterministic failure for either driver.
The remaining ABI-baseline, development sbt-cozy, and User Account internal-DSL
migration warnings predate this comparison and do not contradict its boundary
contract.

## Decision

The Phase 16 driver comparison is complete when both executable profiles and
the existing full driver suites pass. A fixture-only CNCF projector test is
useful as a unit test but is not sufficient evidence for a driver migration.
