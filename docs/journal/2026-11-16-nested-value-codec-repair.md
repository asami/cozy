# Nested value codec repair

Cozy delegates Scala generation to SimpleModeler. The previous default,
`1.1.26-SNAPSHOT`, predates SimpleModeler commit `8c3893a`, which adds typed
nested-value codec support. Cozy now selects `1.1.28-SNAPSHOT`, the current
corrected upstream development coordinate. The local 1.1.28 artifact was
identified as containing that support; this change does not republish or alter
SimpleModeler and does not alter Hub schemas or generated Hub sources.

The regression fixture sends CML through Cozy's generator and compiles the
resulting Scala 3 products, including the entity persistence mapping. Its
round-trip specification exercises transitive products, optional values, ordered
collections, and non-ASCII text. The supported upstream boundary excludes
cycles, external and entity-valued references, and Record-bearing products.
The root focused test task depends on the fixture's test task. A real Scalafix
check of handwritten Compile sources is configured for the selected lint gate.

Native compile, focused tests and fixCheck, independent review, local commit,
and local publication have not been executed by this implementation assignment.
The downstream candidate is `org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`
with SimpleModeler `1.1.28-SNAPSHOT`; its usability remains subject to those
validation and publication steps.
