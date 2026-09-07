# Phase 53: Mobile-first Presentation Acceptance and Productization

Status: PLANNED

Plan date: 2026-09-06

Predecessor: Phase 52 Flutter Web-first Development Loop closure.

## Goal

Make iPhone/iOS and Android the primary product acceptance targets for the
Flutter application across multiple screen sizes and form factors, then
productize the verified application as a Presentation Subcomponent/CAR.

Web remains a supported secondary product target and development surface.
Desktop is secondary unless an application profile explicitly promotes it.

## P53-01: Mobile acceptance profiles

Freeze versioned acceptance profiles based on form factor and window class
rather than commercial model names.

Initial required coverage:

- iOS phone + compact;
- admitted iOS larger-phone/medium configuration;
- Android phone + compact;
- admitted Android larger-phone/medium configuration;
- Android foldable folded + compact;
- Android foldable unfolded + expanded; and
- one tablet-like expanded configuration where admitted.

Concrete simulator/emulator names are execution evidence/configuration, not
semantic application identities.

## P53-02: Platform-specific validation

Exercise behaviors that Phase 50 Progressive Static Web and Phase 52 Flutter
Web cannot prove:

- safe-area handling;
- software-keyboard interaction;
- touch-target usability;
- platform back/navigation behavior;
- system gestures where applicable;
- modal/sheet behavior;
- orientation/window changes;
- fold/unfold responsive transitions;
- relevant lifecycle/resume behavior; and
- admitted native integration seams.

## P53-03: Mobile and secondary builds

- Build verified iOS and Android artifacts from exact accepted/generation input
  identities and selected acceptance profiles.
- Build Web artifact where selected.
- Build Desktop only when selected by profile.
- Keep signing credentials, store accounts, and rollout policy external.

## P53-04: Presentation Subcomponent/CAR productization

Productize the verified Flutter application as an independent Presentation
Subcomponent/CAR using the canonical CNCF Component/Subcomponent contract.

Include at least canonical identity/parent/role metadata, accepted Logical UI,
Flutter Target Policy, Target UI Plan and generated-source provenance, target
compatibility, digest-bound artifact inventory, manuals/guidance, release
notes, licenses/credits, required SBOM/security evidence,
dependencies/prerequisites, Help/MCP information, and deterministic
currentness/integrity evidence.

The generating workspace must not be required by a clean consumer.

## P53-05: Clean consumer verification

- Use only the produced CAR plus explicitly declared external tools/credentials.
- Verify inventory and integrity.
- Verify admitted iOS/Android artifact extraction and guidance.
- Verify selected Web export/delivery from the exact CAR where applicable.
- Verify the standard information surface without exposing application behavior,
  deployment, or official distribution as implicit CNCF Operations.

## Representative Driver

Continue the same SalesOrder application identity through Phases 50-52 and
prove the accepted use case on selected iOS and Android profiles. Responsive
behavior must remain consistent with compact/medium/expanded semantics and
Flutter Target Policy while platform-specific behavior is verified on actual
simulator/emulator profiles.

## Exclusions

- browser-width simulation as mobile acceptance;
- App Store submission/release;
- Google Play submission/release;
- Component Repository publication unless promoted to a separate downstream operation;
- general deployment orchestration;
- credentials/signing secrets inside CAR; and
- mandatory Desktop acceptance for every application.

## Completion Criteria

Phase 53 completes when the same accepted Logical UI/Flutter projection is
validated on required iOS and Android profiles, platform-specific behavior has
evidence, selected artifacts are built without semantic drift, and a
deterministic self-contained Presentation Subcomponent CAR passes clean
consumer verification.

## References

- `docs/phase/phase-53-checklist.md`
- `docs/phase/phase-52.md`
- `docs/phase/phase-51.md`
- `docs/phase/phase-50.md`
- `docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-flutter-mobile-first-web-first-development-direction.md`
