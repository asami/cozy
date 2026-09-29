# Hygiene Resolution Batch Handoff

Status: COMPLETE
Completed: 2026-09-29
Created: 2026-09-29
Source Repository: /Users/asami/src/dev2026/cozy
Target Repositories: /Users/asami/src/dev2026/cozy
Suggested Invocation: `$cncf-goal-hygiene /Users/asami/src/dev2026/cozy/docs/journal/2026/09/2026-09-29-test-prerequisites-hygiene.md`

## Purpose

Restore existing full-test execution prerequisites after the Phase 74 final
validation failed. The user selected `repair-then-resume` for decision
`COZY-P74-FINAL-PREREQUISITES-01`. Preserve all existing assertions and the
accepted provisional Abstract UI contract. This subordinate batch returns to
the same Phase release gate after its own acceptance commit.

## Included Hygiene

| ID | Source | Evidence | Work Package | Required outcome |
| --- | --- | --- | --- | --- |
| HYG-P74-TEST-001 | This journal, Node prerequisite record | CozyVideoSpec could not execute `node`; Phase-base test is unchanged. | HP-001 | Node is available on the test process PATH. |
| HYG-P74-TEST-002 | This journal, Graphviz prerequisite record | Two ModelerServiceClassSpec cases reported `dot: command not found`; Phase-base test is unchanged. | HP-001 | Graphviz renders the existing model diagrams. |
| HYG-P74-TEST-003 | This journal, fixture containment record | EmbeddedValueObjectGenerationSpec references an absent external absolute CML path. | HP-002 | A repository-owned 09.a scenario fixture preserves entity/value generation expectations. |

## Source Records

### HYG-P74-TEST-001 — Node prerequisite

Hygiene Status: RESOLVED
Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P74-TEST-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-29-test-prerequisites-hygiene.md
Handed Off On: 2026-09-29

The failure was `Cannot run program "node"` (ENOENT). The installed normal
Homebrew executable is `/opt/homebrew/bin/node`, version `v26.10.0`.

### HYG-P74-TEST-002 — Graphviz prerequisite

Hygiene Status: RESOLVED
Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P74-TEST-002
Handoff Journal: cozy:docs/journal/2026/09/2026-09-29-test-prerequisites-hygiene.md
Handed Off On: 2026-09-29

The installed normal Homebrew executable is `/opt/homebrew/bin/dot`, Graphviz
16.1.0. No shell profile or SBT cache was changed.

### HYG-P74-TEST-003 — Fixture containment

Hygiene Status: RESOLVED
Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P74-TEST-003
Handoff Journal: cozy:docs/journal/2026/09/2026-09-29-test-prerequisites-hygiene.md
Handed Off On: 2026-09-29

The external cncf-samples checkout is absent and its source could not be read
from GitHub. The local fixture is reconstructed from the existing generation
specification and `src/sbt-test/cozy/aggregate-single-record-proof/check-aggregate-single-record-proof.sh`:
package `org.sample.aggregatesinglerecord`, OrderLine(name, quantity), and
Order(id, name, status, lines). Collection generation remains `Vector[OrderLine]`.
This is a contained test input, not a claim to have copied or validated the
unavailable external repository or run its scripted integration proof.

## Frozen Boundary

- Allowed repository: `/Users/asami/src/dev2026/cozy`.
- Allowed paths: `src/test/scala/cozy/modeler/EmbeddedValueObjectGenerationSpec.scala`,
  `src/test/resources/cozy/modeler/order-single-record-aggregate.cml`, and this journal.
- Preserve paths: all other paths, including the five pending Phase 74 release paths.
- Allowed behavior change: none; all existing test assertions remain unchanged.
- Prohibited expansion: product implementation, Abstract UI API, architecture,
  schema, persistence, transport, concurrency, acceptance changes, external
  repository development, publication, deployment, and push.

## HP-001 — Restore local execution prerequisites

- Hygiene IDs: HYG-P74-TEST-001, HYG-P74-TEST-002.
- Repository: `/Users/asami/src/dev2026/cozy`.
- Targets: local Node.js and Graphviz executables; prerequisite records in this journal.
- Allowed repair: normal local tooling installation, without shell-profile edits.
- Prohibited expansion: changing video/modeler assertions or product behavior.
- Focused validation: `node --version`, `dot -V`, and the unchanged CozyVideoSpec/ModelerServiceClassSpec cases in the package-focused SBT invocation.
- Dependencies: None.

## HP-002 — Contain the existing aggregate generation fixture

- Hygiene ID: HYG-P74-TEST-003.
- Repository: `/Users/asami/src/dev2026/cozy`.
- Targets: EmbeddedValueObjectGenerationSpec and the repository-owned CML resource.
- Allowed repair: use the classpath resource as the input; preserve all assertions,
  output paths and test scenarios; update only the edited Scala history header.
- Prohibited expansion: skipping tests, weakening assertions, modifying generation behavior.
- Focused validation: `sbt --batch 'testOnly cozy.modeler.EmbeddedValueObjectGenerationSpec cozy.modeler.ModelerServiceClassSpec cozy.video.CozyVideoSpec'` through the registered serialized runner.
- Dependencies: HP-001.

## Final Focused Review

- Exact target files: the two HP-002 files and this complete journal.
- Required checks: every included ID, whole-target hygiene, unchanged assertions,
  fixture meaning, consumer compatibility, focused evidence, preservation of the
  pending Phase release delta, and absence of protected expansion.
- Failure policy: stop without commit; no automatic review-fix/re-review loop.

## Final Full-Validation Gate

1. `/Users/asami/src/dev2026/cozy`: `sbt --batch test` through the registered serialized runner.

Run once on the independently reviewed tree. Stop on failure.

## Completion Contract

- Commit only these three paths after the focused review and full-validation gates pass.
- Mechanical closure fields: source Hygiene Status, batch Status, completion date,
  Work Package state, and validation evidence references only.
- Set all source records to RESOLVED and this batch to COMPLETE in the accepted tree.
- Report the acceptance commit externally, without a self-referential hash.
- Return to PHASE_RELEASE_COMMIT without another Phase full review or repair cycle.
- Do not absorb unrelated Hygiene or Development Candidates.

## Batch Ledger

| Package | State | Evidence |
| --- | --- | --- |
| HP-001 | ACCEPTED | `cozy-P74-HYG-PREREQ-FOCUSED-VAL01-A1`; Node/dot availability and unchanged consuming specs passed. |
| HP-002 | ACCEPTED | `cozy-P74-HYG-PREREQ-FOCUSED-VAL01-A1`; all existing generation assertions preserved and passed. |

## Final Validation Evidence

- Independent final focused review: CLEAN; disposition `e4e35131cd9b5d83d55a748a9b1535bb534e4f0620b5361ba3d621a6337a45f1`.
- Full test: `cozy-P74-HYG-PREREQ-FULL-VAL01-A1`; {'canceled': 8, 'failed': 0, 'ignored': 0, 'pending': 0, 'succeeded': 2053, 'total': 2053}; {'aborted': 0, 'completed': 161}.
- Command receipt: `1f321e268e8f2e792ec2e3c9a9fc1ccb9c25f25f83009386045dbb23fad2d3fc`; SBT/wrapper 0, lock released.

## Non-goals

- Running the cncf-samples integration project or Cozy scripted suite.
- Changing the provisional UI contract or claiming Android mock integration acceptance.
