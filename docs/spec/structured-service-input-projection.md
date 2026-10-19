# Structured service input projection

Status: normative

Executable specification:
[StructuredServiceInputProjectionSpec](../../src/test/scala/cozy/modeler/StructuredServiceInputProjectionSpec.scala).

## Selected semantic input

A normal service COMMAND whose selected INPUT Value contains a builtin Json or
SemiStructuredData attribute MUST carry that actual Value into its MOperation.
The operation-local inline INPUT Value takes precedence. Otherwise, the input
type resolves through the existing canonical bindings or normal top-level Value
resolution. Cozy MUST use ModelTypeProjector's Value projection so all ordinary
and structured attributes retain declaration order, semantic datatype identity
and declared multiplicity.

The projected Value replaces the existing Record placeholder as one MParameter.
All other operation fields, including body, access, descriptor, result and
description, MUST be preserved. A second schema-only parameter MUST NOT be
introduced. OperationField datatype strings and field names do not establish
builtin structured identity.

## Generated schema and action

SimpleModeler's normal Value input projection owns the request schema and named
COMMAND_VALUE action projection. Cozy supplies the semantic Value through the
public model API. The request parameters and action fields MUST agree on field
order, type and cardinality.

For a selected Json attribute, the request uses canonical XJson and the action
uses io.circe.Json. SemiStructuredData uses canonical XSemiStructuredData and
org.goldenport.record.Record. Multiplicity one always denotes one document:
materials:json:1 remains one Json even when the document is a JSON array.
Optional and repeated multiplicities retain their declared meanings.

The representative BookMaterial input retains version:int:1, requestId:string:1,
payload:json:1, extensions:json:1 and materials:json:1 in that order. The inline
and referenced top-level Value forms obey the same semantic contract; a
top-level Value retains the existing COMMAND compatibility marker requirement.

## Compatibility and ownership

Pure String, custom named datatype, unknown and legacy Record-only input shapes
retain their existing projection. In particular, HealthQuery retains its Record
constructor. A custom datatype or a field name MUST NOT be promoted to Json.
QUERY projection and entity create/update derivation retain their existing
owners.

The shared event-emit/event-effect-record body reads name and title from
action.request.toRecord. This is the request record represented by the former
Record constructor field; previous-value fallback and recordEventEffect behavior
remain the same when a structured action exposes typed fields.

The executable specification parses actual CML, projects actual model objects,
generates Scala products, and checks these compatibility controls. Its shrinking
property requires at least 100 successful samples and zero discards, varying
independent field names and source multiplicities through actual parsing and
projection. Generated Scala 3 compilation belongs to the downstream consumer's
normal generation and compile path.

## Named structured cardinality admission — 2026-10-19

Normal generation of a selected structured COMMAND Value MUST use keyed readers
for each named Json or SemiStructuredData leaf. Required Record reads only its
named document; the legacy opaque HealthQuery Record retains the whole-request
constructor. Optional leaves preserve missing as None and reject invalid present
values. Present Json.Null is Some(Json.Null) for Json and invalid for Record.
Zero-or-more and nonempty leaves select the existing whole Vector and
NonEmptyVector readers, preserving order and rejecting malformed or scalar
collection inputs. Missing zero-or-more values become empty; missing or empty
nonempty values fail. A Json array document does not become a collection carrier.

The mixed CML generation scenario covers required, optional, repeated and
nonempty Json and Record fields beside an unrelated String, observing semantic
identity, order, schema/cardinality and the actual generated keyed reader calls.
The server's test-only StructuredAdmissionFixture and
StructuredCommandAdmissionSpec own actual generated Scala 3 constructor behavior
through normal pinned Cozy generation. Producer source checks alone do not admit
runtime semantics; parent-owned compilation and execution remain required.
