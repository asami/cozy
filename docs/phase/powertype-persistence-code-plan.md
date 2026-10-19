# POWERTYPE persistence-code implementation plan

Date: 2026-10-19
Status: ACCEPTED under Workbench STEP-2 local binding `tkw-step2-connected-acceptance-commit-20261019-01@1`
Consumer: Workbench Phase 1 STEP-2
Contract: [CML fixed persistence codes](../spec/cml-powertype-persistence-code.md)

This is the cross-project dependency work for the current Workbench continuation,
not a reopening of closed Cozy Phase 78, CNCF Phase 100/115 or Core Phase 3.
It creates no new numbered Phase. The owning Workbench STEP-2 checklist records
its connected acceptance; closed predecessor Phases retain their histories.

## Connected delivery order

1. **Common contract and source preservation.** Define the minimal typed
   bidirectional fixed-code contract in Core. Preserve explicit/omitted CML
   values in Cozy and the SimpleModeler model. Specify structured failures for
   malformed, missing, duplicate and unknown codes. Do not add a registry,
   reflection-based codec discovery or model dependencies to Core.
2. **One generated persistence route.** Generate the fixed mapping for Workbench
   ProposalPreparationState (`prepared=1`, `unprepared=2`), wire the existing
   declared `XShort` / signed16 persistence boundary to it, and save/read/reopen through the normal
   generated Entity route. Keep external JSON/CLI symbolic values unchanged.
   Share the ordinary Short attribute path, retain Scala Short to SQL DDL,
   and select PostgreSQL/MySQL `SMALLINT` or SQLite `INTEGER`. Existing model
   Int code APIs remain correspondence APIs; arbitrary-precision exactness is
   a separate unchanged contract.
3. **Complete declaration admission and affected callers.** Require complete
   mappings for persistent uses, including nested/wrapped references; accept
   transient vocabularies without codes. Remove ordinal/default/text fallback
   in this route. Reassess affected existing declarations explicitly; do not
   mass-assign codes or edit generated sources by hand.
4. **Connected verification and review.** Author the contract's executable
   scenarios with the implementation, run relevant producer/consumer checks,
   then independently review the integrated change. Reuse relevant existing
   evidence. Record actual results and local acceptance commits in the owning
   development task; this plan does not start commands. Its checklist below
   projects the owning STEP-2 acceptance.

## Ownership

| Owner | Work |
| --- | --- |
| goldenport-core (`simplemodeling-lib`) | Typed generic fixed-code codec contract and laws |
| Cozy | CML declaration preservation, resolved persistence-use validation and diagnostics |
| SimpleModeler | Generated typed mapping/inverse and declared persistence binding |
| simplemodeling-model / each generated POWERTYPE | Model-owned fixed correspondence; no per-type SQL/storage framework |
| CNCF | Consumption at existing persistence boundary, if an actual integration change is necessary |
| Workbench Phase 1 STEP-2 | CML declarations and actual consumer save/read/reopen evidence |

The executing task must identify any required cross-component change in its
existing authority before editing that component. This planning update does not
silently enlarge an implementation task or authorize live database migration.

## Acceptance checklist

- [x] Core contract and generated model mapping have their intended dependency direction.
- [x] Both Workbench kinds round-trip through numeric storage with unchanged external strings.
- [x] Missing/duplicate/invalid codes fail before successful generation; transient omission remains valid.
- [x] Persistent codes accept signed16 boundaries and reject overflow through direct, wrapped, nested and imported uses; transient Int codes do not select a physical width.
- [x] Source reordering/label edits preserve codes, and unknown stored codes fail without fallback.
- [x] Actual generated compilation, provider reopen, relevant regressions and independent review pass.
- [x] Actual implementation/evidence/commit status and any remaining separately owned work are recorded.
