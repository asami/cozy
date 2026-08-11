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

Status: PLANNED

- [ ] Require explicit article identity independent of `media.yaml`
      knowledge ID or package ID.
- [ ] Validate exact canonical locale, resource/public paths, and accepted
      production video evidence without fallback.
- [ ] Reject path/filename/generated-site inference, arbitrary scans, BoK
      coupling, and implicit host fallback.

## AM27-02: Site Registration Command and Registry Merge

Status: PLANNED

- [ ] Freeze and implement the supported Cozy site-registration command with
      an explicit publication directory.
- [ ] Merge owner-bundle role/locale updates deterministically under one lock,
      complete preflight, and atomic replacement while preserving unrelated
      entries.
- [ ] Register infographic `public_path` and published external video
      provider/`watch_url` in provider-neutral SmartDox records.
- [ ] Keep Cozy internals out of SmartDox records and keep media generation,
      upload, and deployment outside the command.

## AM27-03: Skill Workflow and Part 5 Acceptance

Status: PLANNED

- [ ] Keep article sources registered/discovered by SmartDox normally.
- [ ] Have infographic and video skills invoke supported product commands,
      never mutate a registry directly.
- [ ] Verify Dox exact-locale projection, JA/EN panel links, and article-top
      video presentation where applicable.
- [ ] Drive end-to-end acceptance from Part 5; retain Part 4 as incident
      evidence only.

## AM27-04: Review, Validation, and Closure

Status: PLANNED

- [ ] Add executable specifications for binding, provider-neutral emission,
      merge preservation, locking, and security boundaries.
- [ ] Run focused validation and the Part 5 end-to-end normal-package gate.
- [ ] Complete independent review, repair any actionable findings, and obtain
      clean re-review.
- [ ] Confirm bilingual infographic plus accepted external video registration,
      exact-locale Dox projection, and documentation convergence before phase
      closure.
