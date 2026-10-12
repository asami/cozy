# Semantic Message Flow ownership and review integration

Date: 2026-10-09

Discussion established Semantic Message Flow as a complementary projection for component architecture review.

The key decision is to keep the normative specification in Cozy because Cozy owns CML. The model may start from UML Component Diagram relationships and stereotypes, but CML is allowed to extend UML where the required semantics do not fit directly.

Message Flow is not promoted to the primary dynamic design model. Workflow and State Machine remain the deductive models. Message Flow provides a cross-cutting view of component communication and is particularly useful for review.

The diagram is informative. The semantic model is normative and machine-readable. This enables SAR/CAR-derived message-flow component diagrams, AI review over structured data, and later infographic decoration without making generated images the source of truth.

The notation discussion also established one-relationship/one-line as a core rule, with endpoint symbols carrying control/data semantics and Continuation/IoC represented as an interaction attribute on the same relationship.

See `docs/notes/semantic-message-flow-model.md`.
