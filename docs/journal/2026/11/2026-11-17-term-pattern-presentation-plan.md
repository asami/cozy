# Glossary preferred labels and term/pattern presentation plan

Date: 2026-11-17
Disposition: ADOPTED / Phase 93 PLANNED / DEV-043

## User request

During DDD guide comment collection in textus-technical-documentation, the user requested a new Cozy Phase for glossary definitions/display preferences, original-language pattern names and HTML/LaTeX color and typography. The document should adopt the capability after that Phase is complete.

The reviewed glossary format currently stores canonical Japanese/English labels but requires Japanese labels in Japanese public artifacts, without a per-term preferred-display field. The user reports existing HTML term coloring; exact current styling and the missing capability boundary are to be confirmed in P93-01. No implementation diagnosis or renderer acceptance is claimed by registration.

## Registration

[Phase 93](../../../phase/phase-93.md), its [checklist](../../../phase/phase-93-checklist.md) and [requirements](../../../notes/term-pattern-presentation-requirements.md) record contracts/ownership, metadata and label resolution, HTML/LaTeX styling, actual output acceptance and a downstream handoff. Strategy records DEV-043; the Phase index includes the plan.

Keep glossary authority in SimpleModeling.org and parsing/rendering ownership in SmartDox. Coordinate exact required producer work with Phase 92, without reopening Phase 91 explanation-pattern work or treating all knowledge-form patterns as the same concept.

## Execution boundary

This is plan registration only: planned, execution_started=false, all checklist criteria OPEN. Existing dirty work and Phase priorities remain. No product implementation, tests, glossary/public-writing-rule change, DDD manuscript/output generation, commit, upload or deployment is performed. DDD adoption waits for completion and usable accepted delivery.

## Three-profile output and HTML reader switching refinement — 2026-11-17

The user refined article use into three levels: prefer the original definition language (commonly English); prefer established Japanese translations when available and otherwise original-language names; or prefer Japanese labels as far as possible. Select the PDF profile at generation time. For HTML, select the default at generation time and allow readers to change it through a menu or equivalent control.

The requirements, Phase, checklist, index and Strategy now include these outcomes. Glossary metadata must distinguish established translations from other Japanese labels. P93-01 settles precedence among output profile, entry preference and the original-language pattern baseline, with explicit absent-label behavior. Label switching retains identity, scope, links, styling and unchanged surrounding prose/code.

This refines the earlier entry-preference baseline; it does not silently apply the old unconditional precedence to the new profiles. All criteria remain OPEN and implementation remains unstarted. DDD document adoption still follows Phase 93 completion and usable delivery.

## Core-side capability refinement — 2026-11-17

The user requested English/bold pattern names and color where supported, then clarified that the current Core lacks the capability: use English names only in the current document and include Core-side functionality in Phase 93.

The requirements, Phase/checklist, index and Strategy now explicitly include supported versioned Core terminology/presentation modeling, typed glossary identity and term/pattern roles, label-selection/style intent and propagation into localized Documents and HTML/LaTeX output. English names, bold emphasis and optional configured color are acceptance cases. Concrete fonts/colors remain renderer configuration; existing Core compatibility and the current closed schema are preserved until the supported extension/successor is delivered.

The three profiles and output-time/HTML-reader selection remain in this same Phase. Implementation remains unstarted and all criteria OPEN; the current DDD source uses plain English labels, not the unimplemented style contract.

## Explicit LexiDox grammar scope — 2026-11-18

The user asked whether the LexiDox grammar extension had been instructed. The existing Phase required glossary/producer schema and Core-side changes, but did not explicitly name LexiDox grammar implementation. The Phase, requirements, checklist, index and Strategy now state this requirement.

Include source syntax/schema for original-language names, localized labels/preferences, established translations and ordinary-term/pattern semantics, exact contracts and examples, parser/validator diagnostics, serialization/metadata and executable compatibility specifications. Reuse relevant Phase 92 work and assign the additional producer delivery to its actual SmartDox owner. Acceptance starts from authored LexiDox and reaches Core/Document and native HTML/LaTeX output.

This corrects planning specificity; no syntax is invented or implemented by the clarification. All Phase 93 criteria remain OPEN. No developer-chat message or implementation/publication action was issued by this plan update.
