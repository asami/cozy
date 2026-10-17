# Semantic ScalaDoc Generation

Date: 2026-10-16
Status: planning note
Owner: Phase 86

ScalaDoc is treated as source-level semantic information and as an input to generated HTML documentation. It is valuable to human maintainers and to AI source analysis because it can retain meaning that is not safely inferable from names and types alone.

For generated Scala, CML remains the authority. Cozy should deterministically project available model meaning into ScalaDoc for important generated public boundaries. Useful content includes role, contract, constraints, lifecycle, relationships and usage semantics. Avoid restating obvious code or manufacturing prose where the model has no useful semantic information.

The distribution target is the existing Documentation Subcomponent architecture. The existing Component Scaladoc Projection remains the basis for public ScalaDoc generation/staging; Phase 86 aligns that output with Documentation Subcomponent packaging rather than creating a new resolver or publication system.

Hand-written Component code should follow the same semantic principle, but enforcement/review belongs to the development/review tooling rather than the CML generator.
