# CML Accepted Domain Scalar Lint Contract

Date: 2026-07-15

## Context

Phase 16 introduced nominal Scala generation for plain CML `DATATYPE`
declarations. The first CML lint implementation still warned for every
string-backed nominal Datatype that did not match a predefined scalar. This
overstated the debt: a string-backed type with an explicit range, syntax, or
format contract is a valid narrower domain scalar.

## Decision

The v1 accepted-domain-scalar signal is the normalized Datatype constraint
model. A plain string-backed `DATATYPE` has a distinct declared contract when
its `value` attribute declares at least one of the constraints currently
represented by the Kaleidox CML AST:

- `min-length`;
- `max-length`;
- `pattern`;
- `format`.

Such a Datatype is not reported as an unclassified nominal string wrapper. It
is also not rejected merely because its name resembles a predefined scalar:
the explicit narrower contract is the reason for retaining a domain type.

An unconstrained string-backed Datatype remains a warning. A wrapper that
duplicates a predefined scalar remains a failure only when it has no distinct
declared contract.

## Boundary

The lint reads `DataTypeClass.Plain.constraints` from the normalized CML model.
It does not reparse SmartDox text and does not treat names, prose descriptions,
or naming suffixes as proof of a domain contract.

Privacy, redaction, normalization, and opaque-representation policies may also
justify a domain scalar, but they require typed CML metadata before lint can
use them. Until then, those declarations remain review warnings rather than
receiving a prose-based exemption.

## Driver Impact

This decision does not invent constraints for unresolved account or
notification Datatypes. Driver warnings remain until each domain-decision row
is resolved and the accepted contract is authored in CML.
