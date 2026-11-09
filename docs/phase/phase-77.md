# Phase 77: CML Finding Disposition Properties

status=closed
closed_at=2026-11-09
planned_at=2026-10-04
started_at=2026-11-05
depends_on=goldenport-cncf Phase 99
consumer=CAR lint, textus-cbd-support

## Goal

Provide the CML-native representation of CNCF Finding Disposition semantics. CML uses model properties rather than importing Scala annotation syntax.

## Principles

- CML properties are the source of truth for CML-authored/generated artifacts.
- The property vocabulary maps to the CNCF Phase 99 normalized Finding Disposition model.
- Generated Scala annotations, when required by a consumer boundary, are projections only and must not become a second authority.
- A disposition records an intentional design decision; it is not a generic warning-suppression escape hatch.
- AI may propose property insertion, but application requires the external Human-in-the-loop admission policy.

## Initial scope

Define and parse CML properties carrying at minimum:

- lint/finding rule identity;
- disposition;
- reason;
- target/scope where not already implied by the owning model element;
- compatibility/version metadata where required by CNCF Phase 99.

Support deterministic projection into the normalized CNCF contract and, where required, generated Scala annotation representation.

## Acceptance criteria

1. CML syntax is native property syntax, not Scala annotation syntax embedded in CML.
2. CML property -> normalized Finding Disposition mapping is deterministic.
3. CML remains the authority after Scala regeneration.
4. At least Accept and Defer cases are covered by executable parser/model/code-generation fixtures.
5. Invalid disposition values, missing required reasons and incompatible rule/target bindings produce explicit diagnostics.
6. No automatic property insertion is performed merely to make CAR lint clean.

## Execution and producer delivery

The CBD Phase 13 dependency request
`cbd-phase13-cozy-cml-disposition-delivery-20261031` initiated this producer work.
On 2026-11-05 the user directed retirement of DelegateTask and continuation in
this chat as GoalPhase. The original destination was force-closed with
`normal_completion=false` and `NotAccepted`; its complete history, including
four consumed repair batches and the last 22/24 focused result, remains retained.
This is not a successful Task Return or CBD adoption.

The current GoalPhase Session `cbaf31b621d3865b9b6a82045103501a` adopts the existing
eight-file implementation from base `f04d90115190fbae54b331f0f46f2fb12cd80f6f`.
S77.1 completes the same producer boundary, including the fixture's explicitly
declared output Value. The runtime owns its current checks, repair accounting,
independent Phase review and local closure. Existing Task counters have not been
rewritten or imported as fabricated GoalPhase events.

The frozen [source contract](../spec/finding-disposition.md) and
[design](../design/finding-disposition.md) retain CNCF Phase 99 semantics.
Selected validation is Compile, `CmlFindingDispositionSpec`, and generation
regression suites `LibraryGenerationSpec` and `ModelerScalaGenerationSpec`.
No generated consumer activation or actual CAR lint coverage is inferred from
producer source-generation checks. CBD owns its integration and activation.

Implementation, actual validation, independent review, local commit, local
publication, requester adoption and GitHub synchronization remain distinct
outcomes. Runtime completion evidence is appended only after its actual gates.

The report-repair continuation retained the original SuiteMismatch receipt and
reserved one native repair. Plan revision 2 adds only `build.sbt` to S77.1 so
focused ScalaTest emits its standard console report, including suite names.
The suite selection, assertions, original producer scope and prior history are
unchanged; fresh native checks are required for the new candidate.

<!-- sm-workflow completion cbaf31b621d3865b9b6a82045103501a phase 77 -->
Accepted phase 77.
Local evidence archive: `mac-mini-2026:~/backup/journal/2026/11/cozy/phase-77/cbaf31b621d3865b9b6a82045103501a/`.
Retention review: `2026-12-09`; originals remain available for CBD integration.
Evidence: cbaf31b621d3865b9b6a82045103501a:ordered-validation:82:validation
Evidence: file:///Users/asami/src/dev2026/cozy/target/sm-workflow/phase77-goalphase-20261105/target/sm-workflow/tasks/phase77-goalphase-20261105/phase-review-result-20261109-01/result.json
Phase source accepted; normal local record/commit closure follows. No later synchronization outcome is inferred.


Local record/commit closure is runtime-owned; later publication, return and synchronization outcomes are recorded separately.

## Verified local completion

GoalPhase returned `Terminal / completed` after native Compile, 24 focused tests,
34 generation regression tests and independent `ReviewPhase / accept`.
The final native closure commit is `9d4c9c3ea1a96eb5d0cc3fe43023326d9a41b907`.
The original Task NotAccepted and report-repair history remain retained.
Local artifact publication, CBD integration/adoption and GitHub synchronization
are separate outcomes; this completion record does not claim them.
