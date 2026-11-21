# ESS3 CAR local Value metadata repair

Date: 2026-10-22
Failure: `ESS3-CAR-INLINE-METADATA-099`
Boundary: whole Phase 3, `ESS3-04-PACKAGED-COMPOSITION`
Status: editing complete; parent validation and independent review pending

## Actual failure and fixed cause

The native TKL `cozyBuildCar001` compiled, then strict
`CozyCarAbiManifest._operation` rejected an empty `inputType` for
`searchMaterials`. The original completed failure is retained under command
`a-c1593855-ce2c-4b70-88d2-3ea4e58de3f9` with SBT/wrapper exit 1 and released
lock. This entry does not replay or replace that failure.

Accepted named `INPUT/VALUE` and `OUTPUT/VALUE` definitions were already present
in Kaleidox `ServiceModel` as canonical `tpe` and local `ValueClass` schema
slots. `CmlModelMetadata` read only rendered explicit type references for its
operation surface and only top-level Values for its model elements, dropping
the accepted local identities and schemas before CAR ABI generation.

## Selected bounded repair

The source metadata route now enriches operation type facts using exact
service-name and operation-name lookup in the already-loaded AST, while
retaining source descriptive/narrative facts. It appends actual operation-local
input/output Value elements in deterministic AST order and projects ordered
Attribute/Id slots, nominal raw type names, effective multiplicities and typed
domain constraints. The enclosing operation supplies the local input kind.

The existing metadata schema, `fromKaleidox` compatibility route, strict ABI
validation, generator duplicate/conflict rejection, target CML, coordinates and
versions remain unchanged. No duplicate `TYPE` workaround or rendered local
schema scan is introduced.

`CmlOperationLocalMetadataSpec` adds actual CML-to-JSON/YAML-to-CAR-ABI contracts
for named query/Result Values, anonymous command/Result identities, mixed
referenced and inline types under colliding operation leaves, nominal field
names and inherited optionality. A bounded ScalaCheck property requires twenty
successful generated name/field cases with zero discards. Fixture work is
owned under `target/` and cleaned by the specification.

## Pending evidence

No tests, build, CLI, runtime, publication or validation checks were executed
by the repair editor. The parent owns the selected four focused suites, any
separately admitted local dependency-artifact refresh, fresh native target CAR
build and archive inspection, independent technical review, later protected
Step review and final Phase full validation/review gates. No acceptance or
Phase closure is claimed by this entry.

## Fixture correction ESS3-CAR-LOCAL-REGEX-FIXTURE-100

The parent retained the actual four-suite result for repair 099: 67 of 68
tests passed, with one failure, under command
`a-14c098ec-5f04-4217-b61b-735cab5e2920`. The fresh single-suite diagnostic
under `a-23ebf8a4-a0bd-407b-914c-d92f0a826c15` passed four of five tests,
with one failure. Both commands completed with SBT/wrapper exit 1 and the
shared lock released; neither result is green validation.

The diagnostic XML at
`target/ess3-car-inline-metadata-diagnostic001/TEST-cozy.modeler.CmlOperationLocalMetadataSpec.xml`
shows that the first named-query scenario's authored `[a-z]+` regex brackets
were parsed as CML/SmartDox link markup, yielding
`<a href="a-z">a-z</a>+`. The AST projection preserved that actual parsed
constraint. The parent selected a source-only fixture correction: use `a+`
in that scenario's pattern table and require the exact `pattern=a+` field
constraint. This retains nonempty regex coverage alongside the exact
min-length/max-length constraints and all existing assertions.

The repair editor executed no validation. Fresh validation of the same four
focused suites, independent technical review of the combined 099 and 100
changes, and the original integrated protected Step, full Phase and final full
gates remain pending. No acceptance or closure is claimed.

## Independent technical review and M0 marker correction

Independent producer technical review
`ess3-car-inline-metadata-independent-review-001-source` returned `FINDINGS`.
It identified no current production behavioral blocker in the bounded repair,
and reported `CPB-ESS3-CAR-LOCAL-METADATA-GWT-001` as an
`AUTHORING_GATE_FAILURE`: the service-qualified lookup scenario loaded and
interpreted the compatibility model after entering its `Then`/`And`
expectation section.

`MCR-ESS3-CAR-GWT-001` applies the frozen M0 descriptive-marker correction:
`When` now introduces the existing source loading and `fromKaleidox`
interpretation, and `Then` immediately precedes the existing compatibility
assertions. Fixtures, values, actions, assertions, execution order, test
selection and expected results are unchanged. No production change is made.

The source review records actual focused100 evidence: all 68 tests across the
four selected suites succeeded, with SBT/wrapper exit 0 and the shared lock
released. That evidence retains its behavior scope; the earlier 67/68 and
4/5 failures and the original native TKL packaging failure remain preserved.
The M0 repair editor executed no tests, validation or review. Parent inspection
of the exact source/journal delta, marker positions and `git diff --check`,
and the parent M0 closure decision remain pending. No review PASS or waiver
acceptance is claimed.

Existing `HYG-P72-FULL-001`, `HYG-P78-001` and
`DEV-ESS3-CML-REGEX-LITERAL-001` remain separate and unimplemented, with their
existing history retained. The marker correction neither increments nor
resets the pre-review repair count 100, consecutive non-convergence count 0,
global Step count 1, Phase count 0 or full-review count 0. Original Goal and
control authority remain unchanged. Protected Step acceptance, full Phase
review, private CAR composition, actual Google/App behavior and final full
validation remain pending; no acceptance or closure is claimed.
