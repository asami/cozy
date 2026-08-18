# Phase 31 Checklist: Video Encoding Policy Profiles

This checklist is the authoritative progress ledger for Phase 31. It is not a
normative contract.

Phase Plan Gate: PROCEED

- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4-6h
- recommended_minimum_effort: high

## VP31-01: Encoding Policy Contract

Status: COMPLETE

- [x] Define `lightweight`, `standard`, and `quality` as typed encoding
      policies with exact resolution, FPS, and CRF values.
- [x] Make `lightweight` the default when no policy or explicit encoding value
      is configured.
- [x] Define field-level precedence as explicit value, selected policy, then
      lightweight default.
- [x] Separate canonical `renderer.policy` from composition
      `renderer.strategy` and remove their current decoder alias collision.
- [x] Add structured validation for unknown policy names and invalid effective
      encoding settings.

## VP31-02: Renderer and Metadata Integration

Status: PLANNED

- [ ] Resolve effective encoding settings once and use them for every video
      part.
- [ ] Pass effective CRF and x264 preset to the Remotion CLI rather than only
      retaining them in parsed configuration.
- [ ] Show effective policy, FPS, dimensions, CRF, and preset in inspect and
      dry-run output.
- [ ] Record the same effective settings in renderer props, generated
      manifests, RDF, publication metadata, and review evidence.
- [ ] Update video scaffolds and current guide examples to use policy-level
      configuration where appropriate.

## VP31-03: Runtime Acceptance

Status: PLANNED

- [ ] Add executable specifications for all policies, default resolution,
      partial explicit overrides, and invalid settings.
- [ ] Prove Remotion command construction includes the effective CRF and
      optional x264 preset.
- [ ] Render one representative character-dialogue fixture with lightweight
      and standard policies and verify dimensions, FPS, duration, and codec.
- [ ] Record output sizes and confirm lightweight is materially smaller without
      unreadable captions or diagrams.
- [ ] Run focused/full Cozy validation, independent review, any bounded repair,
      and ledger convergence before closure.

Phase 31 closes only after policy resolution, renderer execution, metadata,
runtime output, and review evidence agree on the same effective settings.
