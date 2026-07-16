# CML Value and Datatype Boundaries

status=implemented
updated_at=2026-07-16
target_phase=16

## Purpose

This document fixes the stable design responsibilities established by Phase
16 for CML operation Values, Datatypes, semantic scalars, and locale-aware
text. Detailed accepted syntax belongs in the CML grammar and externally
observable behavior belongs in specification documents.

## Ownership

The CML AST is the source of structural and typed model metadata. Operation
input kind, local schemas, datatype constraints, and Web metadata are read from
the Kaleidox/SmartDox AST. Cozy and downstream generators must not reconstruct
that information from rendered text or regular expressions.

The implementation boundary is divided as follows:

- Kaleidox/SmartDox preserves the authored CML AST and typed properties.
- Cozy normalizes CML model kinds and resolves operation input/output roles.
- SimpleModeler preserves those kinds and contracts in its generator model.
- Generated Scala and CNCF metadata expose the runtime type, multiplicity,
  constraints, confidentiality, and presentation hints.
- simplemodeling-lib and CNCF own shared runtime datatypes, schema projection,
  request validation, datastore conversion, and presentation behavior.

No layer may silently replace a more precise upstream contract with `String`
or infer a domain constraint from a Web-only hint.

## Operation Value Boundary

`VALUE` is the structural model for operation payloads. A reusable top-level
input Value declares whether it is a command or query input. An operation-local
input derives that role from its enclosing operation. Anonymous local inputs
receive deterministic generated names; named local inputs retain their
authored names.

Operation output is a Result Value. A local output has operation-result
semantics, while referenced output resolves to a declared Result or the CNCF
predefined Result catalog. Raw scalar outputs are not an alternative Result
model.

The normalized operation contract preserves:

- command/query input role;
- input and output type identity;
- field order, datatype, multiplicity, and constraints;
- deterministic Result fields;
- use-case, rule, scenario, precondition, postcondition, and narrative
  metadata.

Compatibility syntax may normalize into this model, but canonical scaffolds
author reusable inputs as Values and one-use schemas as local Values.

## Distinct Model Kinds

CML model kinds remain semantically distinct even when their serialized
representation is similar:

- `VALUE` models an immutable payload or composite domain concept.
- A plain `DATATYPE` models a validated nominal scalar.
- A complex `DATATYPE` models structured data.
- `POWERTYPE` models a closed vocabulary.
- `STATEMACHINE` models lifecycle state and owned transitions.

A plain Datatype generates a nominal Scala type with construction, parsing,
`ValueReader`, scalar codec, Record/datastore projection, and its authored
constraints. It must not collapse to primitive `String` in required, optional,
repeated, Create, Read, Query, Update, operation, schema, Help, form, or
REST/OpenAPI boundaries.

A finite vocabulary uses a Powertype only when membership is the domain
contract. Lifecycle state uses a Statemachine when transition ownership is the
contract. Open provider registries and externally owned state are not closed
merely because current values can be enumerated.

## Semantic Text and I18N

Text locality is selected by semantic role rather than by storage shape.
Stable names, identifiers, protocol values, hashes, tokens, and other identity
text are nonlocalized. User-visible semantic text uses an accepted
locale-aware type when that type's contract is established.

The implemented baseline is:

- `name` is the nonlocalized `Name` contract;
- `label`, `title`, `headline`, `brief`, `summary`, `lead`, `abstract`,
  `remarks`, `description`, and `text` are locale-aware descriptive or
  narrative roles;
- `title` accepts either one or multiple locale entries without changing type;
- `text` is locale-aware `I18nText`, with a 1..8192 range applied independently
  to each locale entry;
- `name`, `identifier`, `token`, `url`, `uri`, `urn`, `locale`, `timezone`,
  `ip-address`, `email`, and `phone` are intentionally nonlocalized identity,
  protocol, locator, selector, or technical roles;
- `ContentBody` remains the single-document-body contract and is not replaced
  by `I18nText`;
- descriptive families remain connected to `DescriptiveAttributes` and retain
  all locale entries.

`DescriptiveAttributes.effective*` methods select display fallback without
changing the stored values or making headline, brief, summary, description,
and related roles interchangeable. Structural API and datastore boundaries
preserve locale maps; display locale selection happens only at a presentation
boundary with the active execution locale.

Driver-owned registry keys, state values, identifiers, hashes, secrets,
session references, client IDs, provider codes, and source diagnostics remain
nonlocalized by the same semantic rule. Localized display metadata for those
values is modeled separately and never rewrites their stored identity.

## Constraint Projection

`min-length`, `max-length`, `pattern`, and other typed CML constraints are
domain constraints. For locale-aware values, length applies to every locale
entry. Web controls project those constraints as validation hints; Web
metadata does not create or override domain constraints.

One normalized constraint contract must reach:

- generated Scala construction and decoding;
- Record and datastore boundaries;
- generated operation request validation;
- generated entity Create, Query, and Update schemas;
- Help, form, automatic REST, and OpenAPI projection.

Multiplicity is part of the same contract. Scalar, optional, repeated, and
non-empty repeated fields remain distinct in generated operation metadata.

## Deliberately Open Policy

Phase 16 does not freeze policy that lacks an implemented and executable
contract. The following remain outside this design baseline:

- default and allowed locale policy, duplicate-locale handling, and complete
  fallback ordering;
- redaction and display policy for hashes, secrets, and tokens;
- normalization rules for driver-specific nominal scalars;
- final ranges for semantic text families other than accepted baselines;
- the future status of legacy display-projection overloads between
  `I18nText` and `ContentBody`.

These decisions remain in notes and the Phase 16 checklist until implemented
and verified.

## Evidence

The design is enforced by Phase 16 executable specifications covering parser
normalization, model-kind preservation, nominal scalar generation, Scala
3.3.8 compilation, localized length validation, generated operation metadata,
automatic request construction, datastore/Record projection, Help, forms,
REST/OpenAPI, and both User Notification and User Account driver CARs.
