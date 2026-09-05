# Phase 52: Mobile-first Presentation Productization

Status: PLANNED

Plan date: 2026-09-06

Predecessor: Phase 51 Flutter Web-first Development Loop closure.

## Goal

Make iPhone/iOS and Android the primary product acceptance targets for generated
Flutter applications across multiple screen sizes and form factors, then
productize the verified application as a Presentation Subcomponent/CAR.

Web remains a supported secondary target and development surface. Desktop is
secondary unless an application profile explicitly promotes it.

## P52-01: Mobile Acceptance Profiles

Freeze versioned acceptance profiles that are based on form factor and window
class rather than commercial model names.

Initial required coverage:

- iOS phone + compact;
- iOS larger-phone/medium configuration where admitted;
- Android phone + compact;
- Android larger-phone/medium configuration where admitted;
- Android foldable folded + compact;
- Android foldable unfolded + expanded; and
- one tablet-like expanded configuration where the selected application profile
  admits it.

Profile fixtures may map to specific simulator/emulator devices for execution,
but those concrete device names are evidence/configuration and not semantic
application identities.

## P52-02: Platform-specific Validation

Exercise behaviors that Flutter Web cannot prove:

- safe-area handling;
- software-keyboard interaction;
- touch-target usability;
- platform back/navigation behavior;
- system gestures where applicable;
- modal/sheet behavior;
- orientation/window changes;
- fold/unfold responsive transitions where supported by the fixture;
- lifecycle/resume behavior relevant to the representative application; and
- admitted native integration seams.

Retain platform/profile identity and test evidence separately from Logical UI
and Flutter UI Plan authority.

## P52-03: Mobile and Secondary Builds

- Build verified iOS and Android artifacts from the accepted/generated source
  and exact target profile.
- Build Web artifact where selected by the application profile.
- Add Desktop builds only when selected; Desktop is not a mandatory first
  product target.
- Keep signing credentials, store accounts, and environment-specific rollout
  policy external to generated product documentation and CAR content.

## P52-04: Presentation Subcomponent Productization

Productize the verified Flutter application as an independent Presentation
Subcomponent/CAR using the canonical CNCF Component/Subcomponent contract.

Include at least:

- canonical identity/parent/role metadata;
- accepted Logical UI, Policy, Plan, and generated-source provenance;
- target compatibility/profile metadata;
- digest-bound artifact inventory;
- installation/use/operation/extraction guidance;
- release notes;
- licenses and third-party credits;
- SBOM/security evidence as required by the canonical CAR contract;
- dependencies/prerequisites;
- Help/MCP information; and
- deterministic currentness/integrity evidence.

The generating workspace must not be required by a clean consumer.

## P52-05: Clean Consumer Verification

- Acquire/use only the produced CAR plus explicitly declared external tools and
  credentials.
- Verify inventory and integrity.
- Verify admitted iOS/Android artifact extraction and guidance.
- Verify selected Web export/delivery from the exact CAR where applicable.
- Verify the standard information surface is usable without exposing Flutter
  application behavior, deployment, or official distribution as implicit CNCF
  Component Operations.

## Representative Driver

Continue the same SalesOrder application identity through the Phase 50/51
pipeline and prove the accepted use case on the selected iOS and Android
profiles. Responsive behavior must remain consistent with compact/medium/
expanded Policy decisions while platform-specific behavior is tested on the
actual simulator/emulator profile.

## Exclusions

- treating a browser-width simulation as mobile acceptance;
- App Store submission/release;
- Google Play submission/release;
- Component Repository publication unless explicitly promoted to a separate
  authorized downstream operation;
- general deployment orchestration;
- credentials/signing secrets inside CAR; and
- mandatory Desktop acceptance for every generated application.

## Completion Criteria

Phase 52 completes when the same accepted Logical UI/Flutter projection is
validated on the required iOS and Android acceptance profiles, platform-specific
behavior has evidence, selected artifacts are built without semantic drift, and
a deterministic self-contained Presentation Subcomponent CAR passes clean
consumer verification. Official store and repository publication remain
separate authorized operations.

## References

- `docs/phase/phase-52-checklist.md`
- `docs/phase/phase-51.md`
- `docs/notes/flutter-ui-projection-and-responsive-preview-specification-proposal.md`
- `docs/journal/2026/09/2026-09-06-flutter-mobile-first-web-first-development-direction.md`
