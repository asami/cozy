# Account Operation Value Migration

## Context

Phase 16 uses `textus-user-account` as the larger downstream regression driver
for canonical operation input Values. Its 20 authored operation inputs formerly
used legacy top-level `COMMAND` and `QUERY` sections. They now use top-level
`VALUE` sections with explicit `input-kind` metadata.

The migration must preserve the generated component operation contract, CAR
ABI, and literate CML metadata. It may add generated classes for the canonical
Values, but it must not remove or change the service operation surface.

## Migration

The account input definitions now use canonical Values:

- 17 command inputs declare `input-kind :: COMMAND`;
- three query inputs declare `input-kind :: QUERY`;
- result Values remain ordinary output Values.

`OperationContractSpec` executes against the generated component and fixes all
20 operation names, kinds, input/output types, and `COMMAND_VALUE` or
`QUERY_VALUE` input roles.

## Framework Corrections

The comparison exposed two normalization defects that were fixed in Cozy:

1. Legacy command/query input fields did not inherit predefined or custom
   datatype constraints through the same path as canonical Value fields.
2. Legacy metadata exposed `command` and `query` as model element kinds, while
   canonical metadata exposed `value`; structural `input-kind` metadata could
   also leak into the rendered narrative.

Cozy now normalizes both grammars through the same Value model. The normalized
metadata uses `kind = value` plus `inputKind = command|query`, and the CAR ABI
canonicalizes legacy metadata to the same public Value contract. Structural
properties are removed through the CML/SmartDox AST rather than reparsing
rendered text.

## Comparison Evidence

The legacy and canonical source trees were generated from the same account
commit with the same current Cozy implementation and Scala 3.3.8. Only the CML
operation-input grammar differed.

The generated evidence is exact:

- `UserAccountComponent.scala` is byte-for-byte identical; both files have
  SHA-256
  `250565e9bd0699a19a7eb09f6d865176c68efbf983558d9ffc3e817c57803881`;
- `target/cozy/abi-manifest.json` is byte-for-byte identical; both files have
  SHA-256
  `e67f43c94be802301d54329281b0be717e32470937bd41dbdda52fa7a43011ca`;
- `model-metadata.json` is exactly equal after removing only
  `source.path` and `source.sha256`, which necessarily identify the different
  source files;
- the normalized model contains 10 Datatypes, six Entities, and 33 Values,
  including 17 command inputs and three query inputs;
- the CAR-embedded ABI manifest is identical to its `target/cozy` sidecar;
- the only generated source-set addition is the 20 top-level Value classes;
  no generated source was removed.

Because the normalized `modelElements` are otherwise exactly equal, component,
service, operation, use-case, precondition, postcondition, rule, scenario,
descriptive, narrative, relationship, constraint, and implementation metadata
survives the migration unchanged.

## Verification

The completed slice passed:

- Cozy focused operation/metadata/archive specifications: 37 tests;
- Cozy full suite: 550 tests;
- Cozy `text-constraint-runtime` scripted specification: seven tests compiling
  and executing generated Scala 3.3.8 Create, Update, multilingual text,
  `DescriptiveAttributes`, nominal scalar, codec, datastore, and schema
  boundaries;
- SimpleModeler full suite: 34 tests, including rejection of numeric `min` and
  `max` constraints on text and acceptance of `min_length` and `max_length`;
- User Account full suite: 87 tests;
- clean User Account CAR generation and packaging under Scala 3.3.8;
- CAR lint with no deterministic FAIL finding; remaining findings are the
  recorded development/release and later Phase 16 modeling debt;
- `git diff --check` for the touched repositories.
