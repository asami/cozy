# Phase 52 Checklist: Mobile-first Presentation Productization

## Mobile acceptance profiles

- [ ] Freeze versioned iOS/Android acceptance profile contract.
- [ ] Cover iOS phone + compact.
- [ ] Cover admitted iOS larger-phone/medium configuration.
- [ ] Cover Android phone + compact.
- [ ] Cover admitted Android larger-phone/medium configuration.
- [ ] Cover Android foldable folded + compact.
- [ ] Cover Android foldable unfolded + expanded.
- [ ] Cover one admitted tablet-like expanded configuration.
- [ ] Keep simulator/emulator model names as execution evidence, not semantic
  application identity.

## Platform-specific validation

- [ ] Validate safe areas.
- [ ] Validate software-keyboard behavior.
- [ ] Validate touch targets.
- [ ] Validate platform back/navigation behavior.
- [ ] Validate admitted system gestures.
- [ ] Validate modal/sheet behavior.
- [ ] Validate orientation/window changes.
- [ ] Validate fold/unfold responsive transition where supported.
- [ ] Validate relevant lifecycle/resume behavior.
- [ ] Validate admitted native integration seams.

## Builds

- [ ] Build verified iOS artifact for selected profile.
- [ ] Build verified Android artifact for selected profile.
- [ ] Build Web artifact when selected.
- [ ] Build Desktop only when selected by profile.
- [ ] Keep signing credentials/store accounts external.
- [ ] Bind artifacts to exact source/Policy/Plan/profile identities.

## Presentation Subcomponent/CAR

- [ ] Create canonical Presentation Subcomponent identity/parent/role metadata.
- [ ] Include Logical UI/Policy/Plan/source provenance.
- [ ] Include target compatibility metadata.
- [ ] Include digest-bound artifact inventory.
- [ ] Include installation/use/operation/extraction guidance.
- [ ] Include release notes.
- [ ] Include licenses/third-party credits.
- [ ] Include required SBOM/security evidence.
- [ ] Include dependencies/prerequisites.
- [ ] Include Help/MCP information.
- [ ] Include deterministic currentness/integrity evidence.
- [ ] Do not include credentials or signing secrets.

## Clean consumer verification

- [ ] Verify CAR in a clean consumer environment.
- [ ] Verify inventory and integrity.
- [ ] Verify admitted iOS artifact extraction/guidance.
- [ ] Verify admitted Android artifact extraction/guidance.
- [ ] Verify selected Web export from exact CAR where applicable.
- [ ] Verify standard CNCF information surface is usable.
- [ ] Verify Flutter application/deployment/store behavior is not implicitly
  exposed as CNCF Component Operations.

## Representative driver

- [ ] Exercise SalesOrder list/detail/confirm flow on selected iOS profile.
- [ ] Exercise SalesOrder flow on selected Android phone profile.
- [ ] Exercise admitted foldable compact/expanded transition.
- [ ] Preserve responsive Policy behavior across platforms.
- [ ] Preserve public Operation/validation/state authority without duplication.

## Closure

- [ ] Required mobile profile tests pass.
- [ ] Platform-specific validation evidence is complete.
- [ ] Selected builds are identity-bound and reproducible as required.
- [ ] Clean consumer CAR verification passes.
- [ ] Independent Phase review has no Current Phase Blocker.
- [ ] Full serialized Cozy validation passes.
- [ ] Official App Store/Google Play and Component Repository publication remain
  explicit downstream authorized operations.
