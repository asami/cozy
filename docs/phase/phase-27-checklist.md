# Phase 27 Checklist

This checklist is the authoritative progress ledger for Phase 27: SmartDox
Site Media Registration. The normative AM27-00 implementation authority is
`docs/design/smartdox-site-media-registration.md` and
`docs/spec/smartdox-site-media-registration.md`; this checklist remains the
progress ledger.

## AM27-00: Contract Promotion and Responsibility Pinning

Status: DONE

- [x] Review and accept the frozen site-media registration design and
      specification documents before implementation.
- [x] Freeze the descriptor field, exact command grammar, explicit article
      binding, and reusable SmartDox Phase 1 / Cozy Phase 26 contracts.
- [x] Pin SmartDox ownership of provider-neutral schema/model, validation, and
      site projection, with Cozy ownership of normal-media-package
      orchestration.

## AM27-01: Normal Media Package Site Binding

Status: DONE

- [x] Require explicit article identity independent of `media.yaml`
      knowledge ID or package ID.
- [x] Validate exact canonical locale, resource/public paths, and accepted
      production video evidence without fallback.
- [x] Reject path/filename/generated-site inference, arbitrary scans, BoK
      coupling, and implicit host fallback.

## AM27-02: Site Registration Command and Registry Merge

Status: DONE

- [x] Freeze and implement the supported Cozy site-registration command with
      an explicit publication directory.
- [x] Merge owner-bundle role/locale updates deterministically under one lock,
      complete preflight, and atomic replacement while preserving unrelated
      entries.
- [x] Register infographic `public_path` and published external video
      provider/`watch_url` in provider-neutral SmartDox records.
- [x] Keep Cozy internals out of SmartDox records and keep media generation,
      upload, and deployment outside the command.

## AM27-03: Skill Workflow and Part 5 Acceptance

Status: DONE

- [x] Keep article sources registered/discovered by SmartDox normally.
- [x] Have infographic and video skills invoke supported product commands,
      never mutate a registry directly.
- [x] Verify Dox exact-locale projection, JA/EN panel links, and article-top
      video presentation where applicable.
- [x] Drive end-to-end acceptance from Part 5; retain Part 4 as incident
      evidence only.

## AM27-04: Review, Validation, and Closure

Status: DONE

- [x] Add executable specifications for binding, provider-neutral emission,
      merge preservation, locking, and security boundaries.
- [x] Run focused validation and the Part 5 end-to-end normal-package gate.
- [x] Complete independent review, repair any actionable findings, and obtain
      clean re-review.
- [x] Confirm bilingual infographic plus accepted external video registration,
      exact-locale Dox projection, and documentation convergence before phase
      closure.

Phase closure evidence recorded on 2026-08-11:

- The Part 5 normal-media-package dry-run and identical non-dry command
  selected and registered four exact EN/JA infographic/video resources;
  Dox `-publication` and Arcadia runtime succeeded with exact-locale panel
  links and labels rendered.
- Cozy full validation passed 91 suites and 1239/1239 tests (invocation
  `37212-20260811T075506Z`); SmartDox full validation passed 28 suites and
  225/225 tests (invocation `38811-20260811T075602Z`); both exited zero and
  released their locks. Arcadia full invocation `45353-20260811T080703Z` hit
  the pre-existing ScalaTest test-compile mismatch, while focused final-tree
  `OptionalTagSpec` invocation `45635-20260811T080727Z` passed 4/4; this is
  tracked as `P27-HYG-001`.
- Independent review, the M0 `_main` fix, and clean re-review converged PASS.
  The hygiene follow-up is recorded in
  `docs/journal/2026/08/2026-08-11-phase-27-hygiene-follow-up.md`.

Phase 27 is closed from this completed ledger.
