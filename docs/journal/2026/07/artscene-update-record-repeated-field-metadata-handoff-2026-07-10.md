# ArtScene Update Record Repeated Field Metadata Handoff

Date: 2026-07-10

## Summary

ArtScene Stage 5D-1 exposed a generated REST/update metadata bug in the Cozy/CNCF generation path.

CML correctly defines `Facility.fetch_methods` as a repeated powertype field:

```cml
## Facility

| fetch_methods | FetchMethod | * |
```

The generated entity update model can represent this field correctly:

```scala
case class Facility(
  ...,
  fetch_methods: Update[Vector[FetchMethod]]
)
```

However, the generated `UpdateFacilityRecordOperation` request metadata loses both the original datatype and multiplicity:

```scala
ParameterDefinition(
  content = BaseContent.simple("fetch_methods"),
  kind = ParameterDefinition.Kind.Property,
  domain = ValueDomain(
    datatype = XString,
    multiplicity = Multiplicity.ZeroOne
  )
)
```

It should preserve the entity attribute contract:

```scala
ParameterDefinition(
  content = BaseContent.simple("fetch_methods"),
  kind = ParameterDefinition.Kind.Property,
  domain = ValueDomain(
    datatype = DataType.Named("fetchmethod"),
    multiplicity = Multiplicity.ZeroMore
  )
)
```

## Observed Effect

ArtScene Web needs to tune each facility's ordered fetch method list. The intended standard automatic REST call is:

```http
POST /rest/v1/art-scene/entity/update-facility-record
id=<facility-id>
fetch_methods=official_driver
fetch_methods=museum_or_jp
```

This should update `Facility.fetch_methods` to:

```text
official_driver -> museum_or_jp
```

Instead, the generated operation metadata says `fetch_methods` is a single optional `XString`, so the repeated form values are rejected or ignored by the automatic REST/update path. In Stage 5D-1 the facility remains unchanged after the update call.

`RegisterFacility` does not have this problem. Its generated operation metadata correctly carries:

```scala
datatype = DataType.Named("fetchmethod")
multiplicity = Multiplicity.ZeroMore
```

So the defect is specific to generated entity `update-*-record` operation metadata.

## Why ArtScene Should Not Work Around This

ArtScene briefly considered adding a bespoke command such as `TuneFacilityFetch` to bypass the generated endpoint. That is the wrong direction.

The CAR already has a legitimate entity field:

```text
fetch_methods: FetchMethod *
```

The generated update entity can already represent the mutation:

```text
Update[Vector[FetchMethod]]
```

The Web UI should be able to use the standard CNCF automatic REST entity update endpoint:

```text
/rest/v1/art-scene/entity/update-facility-record
```

Adding a CAR-local command solely to compensate for lost generated metadata would duplicate standard generated entity update behavior and make each CAR work around the same generator/runtime issue.

## ArtScene Reproduction

In `/Users/asami/src/dev2026/textus-art-scene`:

1. Confirm the CML field:

```bash
rg -n "fetch_methods" src/main/cozy/textus-art-scene.cml
```

2. Generate:

```bash
sbt --batch cozyGenerate
```

3. Inspect generated update operation metadata:

```bash
rg -n "UpdateFacilityRecordOperation|fetch_methods" \
  target/scala-3.3.8/src_managed/main/org/simplemodeling/textus/artscene/ArtSceneComponent.scala
```

Current bad shape:

```scala
datatype = org.goldenport.schema.XString
multiplicity = org.goldenport.schema.Multiplicity.ZeroOne
```

Expected fixed shape:

```scala
datatype = org.goldenport.schema.DataType.Named("fetchmethod")
multiplicity = org.goldenport.schema.Multiplicity.ZeroMore
```

4. Run the ArtScene Stage 5D-1 smoke:

```bash
scripts/check-stage5d1-facility-fetch-method-tuning.sh
```

The script currently stops early with:

```text
Generated update-facility-record metadata does not preserve fetch_methods: FetchMethod *.
Expected DataType.Named("fetchmethod") and Multiplicity.ZeroMore.
This is a Cozy/CNCF generation blocker; ArtScene should keep using the standard generated endpoint.
```

## Likely Fix Area

Primary fix likely belongs in Cozy/simple-modeler generation.

Locate generation for `Update<Entity>RecordOperation` / `update-*-record` request metadata. For each entity attribute, preserve:

- original CML datatype;
- original CML multiplicity;
- powertype/datatype names rather than collapsing everything to `XString`;
- repeated attributes as `Multiplicity.ZeroMore`.

For this concrete case, `FetchMethod *` should emit:

```scala
DataType.Named("fetchmethod")
Multiplicity.ZeroMore
```

## CNCF Runtime Check

After metadata generation is fixed, verify CNCF automatic REST form decoding preserves repeated form fields as vector/list values when metadata multiplicity is `ZeroMore`.

Since `RegisterFacility` already works with repeated `fetch_methods`, the runtime may already be correct. If the ArtScene Stage 5D-1 smoke still fails after metadata is fixed, then the remaining issue is likely in CNCF automatic REST decoding for generated entity record updates.

## Required Regression Coverage

Add Cozy/sbt-cozy regression coverage with a minimal entity containing a repeated powertype attribute:

```text
ENTITY Facility
  name: name
  fetch_methods: FetchMethod *

POWERTYPE FetchMethod
  official_driver
  museum_or_jp
  ai_web_tools
```

Expected generated metadata for `UpdateFacilityRecordOperation`:

```scala
ParameterDefinition(
  content = BaseContent.simple("fetch_methods"),
  kind = ParameterDefinition.Kind.Property,
  domain = ValueDomain(
    datatype = DataType.Named("fetchmethod"),
    multiplicity = Multiplicity.ZeroMore
  )
)
```

If feasible, add a runtime scripted check that posts repeated form fields to `update-facility-record` and confirms the generated entity search returns the updated ordered vector.

## Acceptance Criteria

- Generated `update-*-record` request metadata preserves repeated entity fields.
- `fetch_methods: FetchMethod *` updates through CNCF automatic REST using repeated form fields.
- ArtScene does not need a bespoke `TuneFacilityFetch` command for normal facility fetch method tuning.
- Existing generated entity update behavior for scalar and optional fields remains unchanged.
- A Cozy/sbt-cozy regression fixture verifies an entity with a powertype `*` attribute generates `Multiplicity.ZeroMore` metadata for `Update<Entity>RecordOperation`.
- Add or update CNCF automatic REST tests only if runtime repeated-form decoding fails after metadata generation is fixed.

## ArtScene Current State

ArtScene currently keeps the standard generated endpoint as the desired integration point.

The Stage 5D-1 Web implementation can render and serialize ordered fetch methods, and its JavaScript harness verifies that the Web layer sends repeated `fetch_methods` values to:

```text
/rest/v1/art-scene/entity/update-facility-record
```

The full Stage 5D-1 smoke remains intentionally blocked until Cozy/CNCF generation preserves repeated-field metadata for update-record operations.
