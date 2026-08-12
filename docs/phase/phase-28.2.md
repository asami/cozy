# Phase 28.2: SimpleModeling.org Part 5 Integration and Regression

Status: closed

Start date: 2026-08-12

Close date: 2026-08-12

## Split provenance

Phase 28.2 was split from the original Phase 28 scope on 2026-08-12 after the
source Phase received `Phase Plan Gate: SPLIT_REQUIRED`. It owns the former
`AM28-03` and `AM28-04` exactly once. Phase 28 owns project configuration and
profile resolution; Phase 28.1 owns reusable WIP local staging/registration.

Phase Plan Gate: PROCEED

- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 3–5h
- recommended_minimum_effort: high
- runtime_suitability: re-evaluate when this Phase starts
- source: approved split from Phase 28

## Goal

Integrate the completed Phase 28 and Phase 28.1 capabilities into the special
SimpleModeling.org SmartDox build. Use article five,
`development-process/object-modeling`, and its JA/EN infographics and final
videos as the required end-to-end test case. Running
`simplemodeling-org/etc/runweb-wip.sh` must produce introduction cards that
link to the infographic and to playable exact-locale local video under the
generated `website.d` tree.

## Boundary and invariants

- Add project-owned Cozy configuration and shared profiles using the exact
  Phase 28 grammar. Add explicit article/resource bindings for Part 5.
- Invoke supported Cozy/Dox product commands from `etc/runweb-wip.sh`; do not
  edit registry JSON directly or infer the target from incidental card rank.
- WIP uses admitted local MP4s and requires no YouTube publication. It does
  not promote WIP records into production evidence.
- Production regression uses accepted external-publication evidence. The
  actual current Part 5 production records may remain YouTube not-published
  and listening pending; listening is non-gating evidence only.
- Standard BoK video remains in its BoK repository/publication path and never
  receives SimpleModeling.org `smartdox-site` staging.
- Acceptance deterministically targets Part 5 even if it falls beyond five
  visible card slots. EN and JA must each expose only their own infographic and
  video links, with no opposite-locale leakage.

## Stages

### AM28-03: SimpleModeling.org Integration and Part 5 Acceptance

Stage Status:

- Current status: DONE
- Owner: Cozy / SmartDox / Dox workflow
- Update rule: mark work complete only from the Phase 28.2 checklist.
- Checklist basis: `AM28-03`

Add root project configuration and shared credit/publication profiles, bind
Part 5 explicitly, wire `runweb-wip.sh` through supported commands, and prove
the rendered bilingual introduction-card contract against admitted artifacts.
Implementation and accepted runtime/review evidence are recorded in the
checklist and SimpleModeling.org commit
`fd460312d992b15086225341360e7586a9acbe35`.

### AM28-04: Standard BoK Regression, Review, and Closure

Stage Status:

- Current status: DONE
- Owner: Cozy
- Update rule: mark work complete only from the Phase 28.2 checklist.
- Checklist basis: `AM28-04`

Regress standard BoK, standalone/package, and Phase 27 production paths. Run
focused and full validation, inspect the generated Part 5 site, complete
independent review/re-review, and close the split sequence.

## Completion evidence

- SimpleModeling.org commit
  `fd460312d992b15086225341360e7586a9acbe35` supplies the accepted project
  configuration, Part 5 binding, launcher, localized widgets, and category
  integration. Cozy commit
  `afe0acaa66904988e6ad5ee1e048241ba8035cef` records the completed AM28-03
  integration stage.
- The final independent Phase 28.2 review passed with no findings across the
  nine SimpleModeling.org target programs and the Cozy WIP, Phase 27
  production, standalone/package, and standard-BoK integration edges. No
  repair or focused re-review was required after this clean review.
- The final Cozy full suite passed 95 suites and 1300 tests, with 8 canceled
  and no failures, in serialized invocation `95101-20260812T122608Z`; SBT and
  wrapper exited zero and the shared lock was released.
- The final `etc/runweb-wip.sh` gate and Bash syntax check exited zero. The
  guarded publication root was removed, and generated JA/EN local MP4 and
  summary PNG hashes matched their admitted sources. Both MP4s probe as
  H264/AAC at 1280x720 and both PNGs as 1600x900.
- Generated Part 5 metadata and category cards use exact-locale infographic
  and `content_url` video paths with no opposite-locale leakage or unresolved
  widgets. The source widgets retain the separate external `watch_url`
  branch.
- Standard BoK remains on its repository/publication path, standalone and
  package-local behavior remains compatible, and Phase 27 production
  registration retains completed technical/visual QA plus published YouTube
  evidence with pending listening accepted as non-gating. WIP used no network,
  upload, publish, or deploy operation.
- No Phase 28.2 hygiene item was admitted. The pre-existing Arcadia test
  dependency item remains separately tracked as `P27-HYG-001` and does not
  affect this Phase's validated boundary.

## Completion criteria

- `etc/runweb-wip.sh` succeeds from a clean accepted fixture state and places
  the JA/EN videos under deterministic `website.d` locations.
- The generated JA and EN Part 5 introduction cards each contain the correct
  localized infographic and playable local-video links and no opposite-locale
  links.
- Generated registry/site identities and hashes match the admitted Part 5
  source artifacts, and repeat generation is deterministic.
- WIP performs no upload/network/deploy and production/standard-BoK behavior
  remains unchanged.
- Focused/full tests, runtime acceptance, independent review, and all Phase
  ledgers/design/spec/evidence converge.

## Dependencies

- Predecessor: Phase 28.1, which itself requires closed Phase 28.
- This is the final child in the approved Phase 28 split.

## References

- `docs/phase/phase-28.2-checklist.md`
- `docs/phase/phase-28.md`
- `docs/phase/phase-28.1.md`
- `docs/design/simplemodeling-org-wip-article-media.md`
- `docs/spec/simplemodeling-org-wip-article-media.md`
- `docs/design/smartdox-site-media-registration.md`
- `docs/spec/smartdox-site-media-registration.md`
