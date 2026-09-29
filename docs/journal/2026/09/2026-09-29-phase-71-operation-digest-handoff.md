# Phase 71 P710-01A: operation and digest handoff

Date: 2026-09-29

Status at implementation handoff: implementation prepared; focused validation,
independent review, and acceptance commit pending

Tracking: PHASE-71 / P710-01 / P710-01A

## Frozen handoff

This entry records the bounded implementation of the frozen P710-01A contract.
The selected request remains the Japanese `article-video-ja` product for the
`ai-development-harness` project, with the task-private Core-rooted graph and
the complete digest-purpose ownership inventory. Original SimpleModeling.org
sources, production media, history, and successor plans remain unchanged.

The operation policy is a single pure classifier. It consumes already admitted
file observations and selects generation, current-output reuse, explicit
prebuilt adoption, or an explicit unavailable reason. It does not resolve an
AI dependency graph, author DSLs, invoke producers, compute hashes, write
receipts, perform I/O, or expose a CLI.

## Planned and new implementation mapping

| Contract | Implementation mapping |
| --- | --- |
| Single-operation file currentness/adoption | `src/main/scala/cozy/generation/CozyFileUpdatePolicy.scala` defines the package-private model and ordered pure selector. |
| Given/When/Then behavior and property coverage | `src/test/scala/cozy/generation/CozyFileUpdatePolicySpec.scala` specifies missing/invalid inputs, force, missing/invalid/unknown output, strict newer-than ordering, equal/older/empty-input reuse, producer absence, explicit prebuilt adoption/rejection, declared-order determinism, and ScalaCheck properties. |
| Normative file-update boundary | `docs/spec/file-update-management.md` preserves its approved existing contract and adds the Phase 71 single-operation boundary and ownership exclusions. |
| Selected production graph | `docs/design/phase-71-selected-production-contract.md` separates original declaration facts from the approved private Core/Document/Summary/Storyboard/native route and records the four actual assets and P710-02 repairs. |
| Digest-purpose and consumer ownership | `docs/design/phase-71-digest-purpose-inventory.md` carries all 46 purpose rows, retained artifact/operation/guarantee distinctions, 156-path coverage, field/model/codec/configuration/scaffold/test consumers, external read-only edges, and successor owners. |
| Chronological handoff | This journal records the implementation mapping and the still-open proof boundary without claiming product or Phase acceptance. |

The inventory deliberately retains only named distribution or wire-format
integrity. G02 packaged generation-provenance self-hashes are removed, while
A04 protects actual CAR entry bytes. M04/A03 remain PNG/ZIP wire CRCs. The
retained renderer bundle comparison is separate from emitted local template
hashes. Release-source/Scaladoc payload integrity is separate from local
source/currentness.

## Pending evidence and ownership

No focused SBT validation was run by this implementation worker. The parent
must run the selected serialized representative and accumulator checks, inspect
the exact owned delta and documentation coverage, and obtain the independent
P710-01 review before any Step commit or transition. The actual private driver
and video proof remain pending P710-02, including Core propagation,
intermediate propagation, Storyboard-only change, all-current reuse, and
failure preservation. No validation pass, review result, commit, generated
video, or Phase closure is claimed here.

The next work may remove or reconcile the named application consumers only
under their exclusive PHASE-71.1/71.2/71.3/71.4 ownership. External CNCF/CBD
schemas remain read-only; any concrete migration need must be reported before
external mutation rather than replaced with a hash compatibility path.

## 2026-09-29 parent validation and independent acceptance review

The parent subsequently completed the frozen focused validation through the
registered serialized SBT route. The representative command
`testOnly cozy.generation.CozyFileUpdatePolicySpec` passed 16 of 16 tests.
The consumer accumulator command
`testOnly cozy.generation.CozyFileUpdatePolicySpec cozy.document.CozyDocumentProjectLocalBuildSpec cozy.document.CozyDocumentProjectLocalBuildCommandSpec`
passed 36 of 36 tests across three suites. Both commands completed with exit 0
and the shared lock released. Main/test compilation was included; no additional
compile or repository-full suite was run.

One fresh independent Terra/high protected-focused Step review returned
`SEALED_LEDGER: PASS` for all six owned files. There were no Current Phase
Blockers, Hygiene candidates, or Development Candidates. Its typed disposition
and Phase Goal state were recorded and verified against the unchanged validated
tree. This is P710-01 acceptance review, not the mandatory full Phase review.

Step commit preparation adds only this chronological evidence; code, executable
specifications, frozen graph, and digest classifications are unchanged. The
exact P710-01 Step commit is still pending at this entry's preparation. P710-02
still owns private declaration repair, native integration, and the actual
Core/intermediate/Storyboard/reuse/failure video proofs. Successor removals are
unimplemented and unaccepted. Repository-full SBT remains deferred-not-run to
PHASE-71.4, and Phase 71 closure and its distinct release commit remain pending.
