# Phase 52 Checklist: Flutter Web-first Development Loop

## Development operations

- [ ] Add scaffold operation for the generated Flutter application surface.
- [ ] Add inspect operation exposing Logical UI/Policy/Plan/source identities.
- [ ] Add plan operation exposing blocked/stale generation steps.
- [ ] Add generate operation using Phase 51 projection contracts.
- [ ] Add preview operation for Flutter Web.
- [ ] Add test operation for focused Flutter tests.
- [ ] Add verify operation covering target currentness and provenance.

## Flutter Web responsive review

- [ ] Support compact preview.
- [ ] Support medium preview.
- [ ] Support expanded preview.
- [ ] Provide side-by-side responsive review for one Logical Screen.
- [ ] Keep viewport/profile selection Policy-driven.
- [ ] Diagnose overflow and unreachable/hidden required content.
- [ ] Diagnose missing feedback/action/navigation coverage.
- [ ] Diagnose stale Policy/Plan/generated source.
- [ ] Keep review projections read-only and non-authoritative.
- [ ] Keep Phase 50 Progressive Static Web semantic review distinct from Flutter
  Web implementation review.

## Regeneration and ownership

- [ ] Keep generator-owned source fully replaceable.
- [ ] Keep developer-owned completion surface separate.
- [ ] Freeze minimal typed completion-hook contract.
- [ ] Reject incompatible/stale completion bindings.
- [ ] Prove regeneration does not merge arbitrary human edits into generated files.

## Developer feedback/provenance

- [ ] Navigate generated Flutter element/source -> Target UI Plan node.
- [ ] Navigate Plan node -> accepted Logical UI screen/interaction.
- [ ] Navigate Logical UI interaction -> UI UseCase Step.
- [ ] Navigate binding -> public Component/Operation/constraint identity.
- [ ] Expose these mappings through inspect/review diagnostics.
- [ ] Distinguish Policy/Plan defects from Logical UI semantic defects.

## Validation

- [ ] Run `dart format` through the development flow.
- [ ] Run `flutter analyze`.
- [ ] Run Flutter unit tests.
- [ ] Run Flutter widget tests.
- [ ] Run responsive Web tests for compact/medium/expanded.
- [ ] Exercise SalesOrder list/detail/confirm/loading/result paths.
- [ ] Prove deterministic repeat generation where required.

## Closure

- [ ] Normal development loop is usable without private package-local calls.
- [ ] Web remains a development/review surface, not mobile acceptance authority.
- [ ] Independent Phase review has no Current Phase Blocker.
- [ ] Full serialized Cozy validation passes.
- [ ] Closure leaves mobile acceptance/productization to Phase 53.
