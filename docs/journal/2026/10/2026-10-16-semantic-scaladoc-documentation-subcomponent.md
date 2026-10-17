# Semantic ScalaDoc and Documentation Subcomponent decision

Date: 2026-10-16

The existing implementation already has substantial ScalaDoc distribution machinery from Phase 71.5 and the CNCF Component Scaladoc Projection. The new work should not replace it.

Phase 86 is added to make two refinements: generated Scala should retain useful CML meaning as semantic ScalaDoc, and generated ScalaDoc HTML should be aligned with the existing Documentation Subcomponent distribution model.

The key rule is semantic rather than quantitative. ScalaDoc is expected where it carries non-obvious meaning useful to humans or AI source analysis; there is no blanket requirement to document every method. CML remains authoritative for generated code.

CNCF Phase 112 is the paired runtime/Web consumer and must use the existing resource Subcomponent resolver/projection.
