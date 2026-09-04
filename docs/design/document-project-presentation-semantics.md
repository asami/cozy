# Document Project Presentation Semantics Design

Status: Phase 46 design

## Ownership

The design introduces one adapter-only semantic boundary:

```text
closed Content Core v1 direct bytes
                 |
                 v
presentation-semantics v2 binding + Phase 37 Composition
                 |
                 v
Validated semantic aggregate
                 |
                 `-- Phase 46.1 projections
```

V1 stays the authority for accepted Core entries and its direct-file identity.
V2 owns only the new logical presentation semantics. The adapter checks the
v1 id, language, and direct-byte SHA-256 before accepting V2; it neither edits
nor reinterprets v1. The Document Project descriptor, CLI, scaffold, and
Content Core candidate/feedback/accept path stay outside this design.

## Reused types

Phase 37 owns `Composition`, `CompositionStep`, catalog selection, resource
declarations, Composition identity, and Plan expansion. Phase 46 calls its
package-visible pre-parsed Composition validation entry point, then retains the
normalized Plan. Phase 36 owns `Logical`, `Visual`, logical-pattern rules,
relation vocabulary, parameter checks, and Visual compatibility. Phase 46
calls its strict Visual validation entry point rather than copying those rules.

`StoryTransition` is a new Phase 46 type because its endpoints are global
CompositionStep identities. It borrows only the closed Phase 36 relation type
IDs; it is never a local `CozyVisualPage.Relation`.

Each Phase 46 `Structure` binds one CompositionStep and exposes that step's
already validated Phase 36 Logical graph. It holds canonical Article 8 text
and optional medium-local Visual overrides, not a second local graph.

## Policy decision

Projection Policy is deliberately semantic but not physical. It maps
`(medium, logicalPattern)` to a strict P36 Visual. For each Structure, all
three media resolve one base policy Visual. A compatible override may replace
the base at the same medium. Compatibility is checked against the inherited
Logical graph and the admitted presentation catalog. This makes selection
deterministic without admitting coordinates, colors, CSS, PowerPoint objects,
fonts, timing, narration, or renderer templates.

## Canonical identities

The aggregate uses field-ordered JSON values constructed from normalized typed
values. It sorts unordered transition, structure, override, policy-binding,
and Visual parameter collections, while retaining semantic sequence where the
source contract says order matters. The currentness identity joins the direct
v1 Core identity, v2 identity, Composition and Plan identities, both catalog
identities, policy identity, declared source/asset identities, and selected
Visual results. It writes no state or receipt.

## Diagnostic boundary

The adapter translates v2 boundary failures into the closed `DP-SEM-001`
through `DP-SEM-011` diagnostic family documented in the specification. This
keeps malformed or lossy input from being represented as a successful semantic
aggregate. Phase 46.1 receives only a `Validated` value, never generic maps or
independent primitive bundles.

## Explicit non-goals

This phase does not create HTML, slide, video, PDF, image, receipt, review
output, CLI grammar, scaffold output, descriptor schema, renderer material, or
external integrations. Those are bounded Phase 46.1 concerns after the Phase
46 aggregate has been validated.
