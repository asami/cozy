# CML Account Operation Semantic Type Alignment

Date: 2026-07-16

## Context

The account entity already used domain types for login name and suspension
reason, while matching operation Values still used generic strings. That split
allowed generated operation metadata and validation to lose semantics already
owned by the entity model.

## Decision

Matching entity and operation concepts use the same CML type:

- `UserRegister.loginName` and `UserLookupByLoginName.loginName` use
  `UserAccountLoginName`;
- `AdminUpdateUserStatus.suspensionReason` uses
  `UserAccountSuspensionReason`.

The generated operation boundary continues to serialize these nominal values
to their external string representation. Handwritten action logic therefore
does not add an adapter or duplicate parser.

## Verification Contract

The generated operation metadata specification checks the datatype of all
three fields. Full account tests continue to verify registration, lookup,
suspension, restoration, and datastore behavior through the component entry
points.
