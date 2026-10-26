# Document confirmation own-claim navigation

COZY-CLAIM-NAV-01 repairs the private confirmation projection. Own claims
previously appeared only in typed Step attributes and the collapsed Structure
audit, leaving parent summary claims without visible navigation beside children.

Each selected Step now exposes native own-claim controls before child Steps.
Their snippets come only from exact Document claim references, in source order;
Section-only coverage keeps an identity-only control. Claim activation retains
the owner panels and highlights only exact claim membership. Step activation
restores existing Step selection. Full prose and authored Core semantics remain.

Regression specifications were added for ordering, escaping, shared references,
list membership deduplication, equal text from distinct sources, Section-only
coverage, empty claims, and emitted activation/reset behavior. Validation and
source-built Episode 6 confirmation generation remain parent-owned; no tests
or builds were run by the repair editor.
