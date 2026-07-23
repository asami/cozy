# Scalar Entity Persistence Round-trip Implementation Proposal

Status: proposed

Date: 2026-07-23

Source:
`docs/journal/2026/07/scalar-entity-persistence-roundtrip-handoff-2026-07-23.md`

This note is non-normative. It records the proposed implementation shape for
Cozy Phase 23. Executable specifications and the generated-code contract remain
the acceptance authority.

## Purpose

Make generated nominal scalar `DATATYPE` values round-trip through generated
Entity persistence without a CAR-specific codec, cache, or raw datastore
access. A value written through `toDataStore()` must be accepted by the
generated read path and reconstructed through the nominal type's validation.

## Current Evidence

Cozy maps CML model kinds and delegates Scala source generation to
SimpleModeler:

- `src/main/scala/cozy/modeler/Modeler.scala` owns the Cozy-side model mapping;
- `src/main/scala/cozy/modeler/ScalaGenerator.scala` invokes
  `Scala3RealmTransformer`;
- SimpleModeler
  `Scala3ClassGeneratorBase.scala` emits nominal `ValueReader` instances and
  Entity persistence helpers;
- simplemodeling-lib `ValueReader` implementations already accept primitive
  scalar inputs, and `Record.getAsC` forwards a field's stored value to the
  selected reader.

The current SimpleModeler checkout already contains a nominal-reader fallback
that delegates non-`Record` input to the underlying scalar `ValueReader`.
Therefore the first Phase 23 change must not assume that the missing fallback
is the cause. The failure may instead be caused by:

- a published or locally cached SimpleModeler version behind the current
  checkout;
- a different generator branch than the nominal-scalar branch;
- model-kind classification of the failing field;
- optional-field or update/upsert datastore shape;
- generated source that was not regenerated after the generator change.

Phase 23 starts by capturing the failing generated source, resolved dependency
version, physical store value shape, and fresh-load failure. That evidence
selects the change point.

## Required Representation Matrix

| Semantic kind | Public/generated shape | Datastore shape | Restore path |
| --- | --- | --- | --- |
| Single-field nominal `DATATYPE` | nominal wrapper with constraints | underlying scalar | underlying `ValueReader`, then validated nominal construction |
| Required Entity scalar field | nominal value | underlying scalar in the Entity record | generated nominal `ValueReader` |
| Optional Entity scalar field | present nominal value or absence | underlying scalar or absence | optional field decoding plus generated nominal `ValueReader` |
| Structured `VALUE` | structured Value API | record-compatible representation | structured `createC` / record reader |
| Multi-field `DATATYPE` | structured generated type | `Record` | structured `createC` / record reader |
| Powertype/statemachine | existing generated contract | existing representation | existing reader |

The phase must not flatten a structured `VALUE` merely because it has one
field. If existing single-field `VALUE` datastore projection is scalar, the
executable specification must first reconcile that legacy projection with the
requirement that the semantic Value remains structured. The Phase 23 fix is
not allowed to silently redefine `VALUE` as `DATATYPE`.

## Proposed Change Boundary

### 1. Reproduce and align dependency versions

Add a minimal generated fixture in Cozy that uses a constrained nominal
`DATATYPE` as both a required and optional Entity property. Preserve the
generated reader and persistence source as test evidence. Record the
SimpleModeler coordinate and implementation revision that Cozy actually uses.

If the current generator source already passes the fixture but the declared
dependency fails, align and publish the correct generator version rather than
adding a second workaround.

Any modified Cozy or SimpleModeler project must first move to its
next-development `SNAPSHOT` coordinate. A published release coordinate is
immutable and must not be modified, committed, or published locally.

### 2. Correct nominal reader generation when required

The primary implementation point is SimpleModeler's nominal-scalar generation
in `Scala3ClassGeneratorBase.scala`. The generated reader should:

1. accept an already constructed nominal value without losing its type;
2. accept a compatibility `Record` only through the nominal field contract;
3. accept the underlying raw scalar through
   `ValueReader[Underlying].readC`;
4. construct the nominal value through its validated consequence-producing
   constructor;
5. return deterministic validation failure for malformed or
   constraint-violating scalar input.

Do not broaden simplemodeling-lib's generic `ValueReader` to reinterpret all
records or objects as scalars unless the reproduction proves a library-level
defect. That would risk flattening structured model kinds.

### 3. Keep Entity restoration generic

Generated Entity `toStoreRecord` and `fromStoreRecord` should continue to use
the field reader selected by the generated type. Do not add a
`ReviewRunState`, CBD Support, or datatype-name special case to Entity
persistence. The Entity layer needs only to preserve required, present
optional, and absent optional store shapes and delegate scalar reconstruction
to the nominal reader.

### 4. Verify lifecycle boundaries

The generated fixture must exercise:

- create and fresh load;
- update/upsert and fresh load from a new repository or UnitOfWork context;
- valid constrained scalar reconstruction;
- invalid scalar rejection;
- required and optional present/absent fields;
- structured `VALUE` and multi-field `DATATYPE` regression cases.

A same-process object returned directly from create or update is insufficient:
the test must cross the datastore read boundary.

### 5. Verify downstream CARs

After the Cozy/SimpleModeler contract passes locally, regenerate and run
focused lifecycle specifications in:

- `textus-user-account`;
- `textus-user-notification`;
- `textus-cbd-support`.

CBD Support then removes the temporary `PersistedReviewDiagnosis` codec and
proves P8-42 `Owner`, `Joined`, and `Reused` behavior through the generated
Entity Aggregate boundary alone.

## Executable Specification Order

1. Add the failing Cozy generated-source/runtime fixture.
2. Determine generator-version skew or the exact failing generation branch.
3. Add or amend SimpleModeler generator specifications.
4. Implement the smallest generator or dependency-alignment correction.
5. Add Entity create/update/fresh-load specifications.
6. Run the model-kind regression matrix.
7. Regenerate and verify the three downstream drivers.
8. Complete read-only review, apply findings, and run a clean re-review.

## Validation Direction

At minimum, the implementation phase should run:

```text
sbt --batch "testOnly cozy.modeler.ModelerScalaGenerationSpec"
sbt --batch "testOnly cozy.modeler.CmlModelKindContractSpec"
sbt --batch test
git diff --check
```

SimpleModeler and each downstream CAR must also run their focused generated
source or Entity lifecycle specifications and their repository-required full
validation before release.

## Non-goals

- CAR-owned SQLite, JDBC, or raw datastore access;
- a review-local cache, codec, or manually maintained View;
- CML grammar changes made only to avoid scalar restoration;
- a generic reader rule that flattens structured model kinds;
- changes to Component Skill Distribution, now scheduled unchanged as Phase
  24.

## Decision Gates

- If current Cozy plus the current SimpleModeler checkout reproduces the
  failure, change the exact generator branch proven by the fixture.
- If only an older resolved artifact fails, align, publish, and consume the
  corrected `SNAPSHOT` generator artifact.
- If a structured `VALUE` case is involved, stop and specify its datastore
  representation separately before changing nominal `DATATYPE` behavior.
- If downstream behavior requires bypassing Entity Aggregate, UnitOfWork,
  authorization, audit, CallTree, or View invalidation, reject that approach as
  outside the accepted architecture.
