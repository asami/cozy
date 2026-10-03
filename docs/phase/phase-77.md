# Phase 77: CML Finding Disposition Properties

status=planned
planned_at=2026-10-04
depends_on=goldenport-cncf Phase 99
consumer=CAR lint, textus-cbd-support

## Goal

Provide the CML-native representation of CNCF Finding Disposition semantics. CML uses model properties rather than importing Scala annotation syntax.

## Principles

- CML properties are the source of truth for CML-authored/generated artifacts.
- The property vocabulary maps to the CNCF Phase 99 normalized Finding Disposition model.
- Generated Scala annotations, when required by a consumer boundary, are projections only and must not become a second authority.
- A disposition records an intentional design decision; it is not a generic warning-suppression escape hatch.
- AI may propose property insertion, but application requires the external Human-in-the-loop admission policy.

## Initial scope

Define and parse CML properties carrying at minimum:

- lint/finding rule identity;
- disposition;
- reason;
- target/scope where not already implied by the owning model element;
- compatibility/version metadata where required by CNCF Phase 99.

Support deterministic projection into the normalized CNCF contract and, where required, generated Scala annotation representation.

## Acceptance criteria

1. CML syntax is native property syntax, not Scala annotation syntax embedded in CML.
2. CML property -> normalized Finding Disposition mapping is deterministic.
3. CML remains the authority after Scala regeneration.
4. At least Accept and Defer cases are covered by executable parser/model/code-generation fixtures.
5. Invalid disposition values, missing required reasons and incompatible rule/target bindings produce explicit diagnostics.
6. No automatic property insertion is performed merely to make CAR lint clean.
