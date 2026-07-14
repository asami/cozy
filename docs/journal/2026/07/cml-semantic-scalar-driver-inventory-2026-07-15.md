# CML Semantic Scalar Driver Inventory

Date: 2026-07-15

## Context

Phase 16 CML16-07 starts from `textus-user-notification` and
`textus-user-account`. Both repositories already contain unrelated dirty work,
so this investigation reads their current CML but does not modify either
driver.

An earlier text-oriented lint implementation parsed headings and Markdown
tables independently from CML. That route is not suitable for the Phase 16
inventory because CML sections may already be represented as structured AST
nodes such as description lists.

## Decision

The inventory uses the shared CML parser and normalized model only:

1. `KaleidoxModel.load` creates the CML AST/model.
2. `CmlModelInspection` reads normalized `ValueModel`, `DataTypeModel`, and
   schema attributes.
3. `CozyCmlLint` consumes the same inspection result and no longer reparses
   headings or Markdown tables.

The current AST inventory found 19 string-only Datatypes in notification and
16 in account. Neither driver has a string-only Value. Each of the 35
Datatypes now has a provisional structural classification and localization
direction in `docs/notes/cml-semantic-scalar-driver-inventory.md`.

## Remaining Decisions

The inventory does not by itself prove domain intent. The following remain
open before driver migration:

- closed versus extensible notification type/provider vocabularies;
- notification and delivery lifecycle state-machine boundaries;
- canonical message/text semantics for notification body;
- account login-name normalization;
- suspension-reason localization;
- raw versus structured account device information.

Confirmed low-ambiguity directions should be converted to executable type
catalog and constraint specifications before either dirty driver source is
edited.
