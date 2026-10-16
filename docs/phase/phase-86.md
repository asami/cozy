# Phase 86: Semantic ScalaDoc and Documentation Subcomponent Packaging

status=planned
planned_at=2026-10-16
depends_on=[Phase 71.5]
runtime_consumer=[goldenport-cncf Phase 112]
related=[goldenport-cncf Component Scaladoc Projection, Resource Subcomponent architecture]

## Goal

Preserve model and Component implementation meaning in Scala source ScalaDoc and publish generated ScalaDoc through the existing Documentation Subcomponent model.

## Work

1. Define a bounded semantic ScalaDoc generation policy for Cozy-generated Scala.
2. Project CML-known descriptions, roles, contracts, constraints and relevant relationships into ScalaDoc deterministically where the generated Scala element is an appropriate source-level carrier.
3. Do not invent prose merely to satisfy comment coverage. Do not emit comments that only restate syntax or implementation steps.
4. Keep CML authoritative: generated ScalaDoc is a projection and must be regenerated from model meaning rather than edited as a second source of truth.
5. Retain the useful existing public ScalaDoc generation/staging contract from the Component Scaladoc Projection.
6. Align packaging so the normal release destination for ScalaDoc is Documentation Subcomponent content associated with the same parent Component release. Avoid a new ScalaDoc-specific artifact identity/resolver.
7. Keep public ScalaDoc free of private/source-page disclosure according to the existing projection policy.
8. Provide representative generated Component/Service/Operation/domain types with useful ScalaDoc and verify that ordinary ScalaDoc HTML preserves it.

## Hand-written Component guidance

Cozy-generated code cannot own hand-written Component documentation, but generated templates/guidance should establish the same semantic convention: important public Component, Service, Operation, extension and domain boundaries document meaning/contract where code alone is insufficient. Blanket private-method documentation is not required.

## Acceptance

A representative CML model produces Scala source whose important generated API carries deterministic semantic ScalaDoc. Ordinary ScalaDoc generation renders that meaning, and the resulting documentation is packageable as the matching Documentation Subcomponent consumed by CNCF Phase 112.

## Non-goals

No AI-generated filler comments, no documentation-per-method quota, no duplicate CML authority, no second Component Repository protocol, and no custom HTML documentation engine.
