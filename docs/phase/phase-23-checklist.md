# Phase 23 Checklist

This checklist is the authoritative progress ledger for Phase 23: Scalar Entity
Persistence Round-trip.

## SR23-01: Reproduction and Dependency Alignment

Status: PLANNED

- [ ] Add a minimal constrained nominal `DATATYPE` used by required and
      optional Entity properties.
- [ ] Reproduce create or update/upsert followed by a fresh datastore load.
- [ ] Capture the generated nominal reader and Entity persistence source.
- [ ] Capture the physical required, present optional, and absent optional
      store shapes.
- [ ] Record the SimpleModeler coordinate and implementation revision resolved
      by Cozy.
- [ ] Determine whether the defect is version skew, generation branch,
      model-kind classification, or field/store decoding.
- [ ] Keep the failing executable specification before implementing the fix.

## SR23-02: Scalar Reader Generation Contract

Status: PLANNED

- [ ] Specify already-typed nominal, compatible record, and underlying scalar
      reader inputs.
- [ ] Restore an underlying scalar through its primitive `ValueReader`.
- [ ] Construct the nominal result through the generated validated
      consequence-producing constructor.
- [ ] Reject malformed and constraint-violating scalar values
      deterministically.
- [ ] Avoid a generic simplemodeling-lib change unless the reproduction proves
      a library-level defect.
- [ ] Align and publish the corrected SimpleModeler artifact if the defect is
      resolved-version skew.
- [ ] Verify the generated source shape in Cozy.

## SR23-03: Entity Persistence Round-trip

Status: PLANNED

- [ ] Prove a required nominal scalar survives create and fresh load.
- [ ] Prove a required nominal scalar survives update/upsert and fresh load.
- [ ] Prove a present optional nominal scalar survives create, update/upsert,
      and fresh load.
- [ ] Prove an absent optional nominal scalar remains absent.
- [ ] Ensure the fresh load crosses a new repository or UnitOfWork read
      boundary.
- [ ] Keep generated Entity restoration generic and delegated to the generated
      field reader.

## SR23-04: Model-kind and Failure Regression Matrix

Status: PLANNED

- [ ] Preserve structured `VALUE` record behavior.
- [ ] Preserve multi-field `DATATYPE` record behavior.
- [ ] Preserve powertype and statemachine persistence behavior.
- [ ] Verify valid constrained scalar reconstruction.
- [ ] Verify malformed primitive input fails deterministically.
- [ ] Verify a well-typed but constraint-violating scalar fails through the
      nominal validation rule.
- [ ] Reconcile any legacy single-field `VALUE` scalar datastore projection
      without changing its structured semantic kind.
- [ ] Run focused Cozy and SimpleModeler generated-source/runtime tests.
- [ ] Run full Cozy and required SimpleModeler tests.

## SR23-05: Driver Verification and CBD Support Handback

Status: PLANNED

- [ ] Move every corrected Cozy/SimpleModeler project to its next-development
      `SNAPSHOT` coordinate before modifying or publishing it locally.
- [ ] Publish only corrected `SNAPSHOT` Cozy/SimpleModeler artifacts locally for
      development.
- [ ] Regenerate, compile, and run focused Entity lifecycle tests in
      `textus-user-account`.
- [ ] Regenerate, compile, and run focused Entity lifecycle tests in
      `textus-user-notification`.
- [ ] Regenerate, compile, and run focused Entity lifecycle tests in
      `textus-cbd-support`.
- [ ] Remove CBD Support's temporary `PersistedReviewDiagnosis` codec.
- [ ] Prove P8-42 `Owner`, `Joined`, and `Reused` behavior through the Entity
      Aggregate boundary alone.
- [ ] Confirm no driver CAR added raw datastore access or a private source of
      truth.

## SR23-06: Review, Publication, and Closure

Status: PLANNED

- [ ] Complete a read-only review after implementation.
- [ ] Fix every actionable finding, including naming and executable-spec debt.
- [ ] Complete a clean re-review after the fixes.
- [ ] Run focused and full validation in every modified repository.
- [ ] Run `git diff --check` in every modified repository.
- [ ] Commit validated changes with required version updates.
- [ ] Publish only the corrected `SNAPSHOT` development artifacts needed by
      downstream CARs.
- [ ] Record downstream evidence and close Phase 23 from checklist results.
