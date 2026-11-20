# LexiDox knowledge/presentation grammar alignment

Date: 2026-11-18
Disposition: coordinated Phase 92/93 planning; implementation unstarted

## Decision

The user requested that knowledge-processing and presentation requirements be aligned so Phases 92 and 93 cannot define incompatible LexiDox grammars. The SimpleModeling.org canonical proposal now states combined LX-J01–LX-J08 requirements. The [joint delivery plan](../../../notes/lexidox-phase92-93-integration.md) maps those requirements to the two existing Phases.

Use one SmartDox producer grammar/schema and serialized metadata lineage. Resolve Space-specific term/concept identity before selecting a display label. Concept classification, knowledge form and presentation role remain independent; display changes retain reference targets, definitions, relations, provenance and RDF meaning.

## Phase allocation

Phase 92 owns the semantic baseline and BoK integration. Phase 93 adds compatible source metadata, Core/Document presentation intent and HTML/LaTeX behavior. The two first Steps share a coordinated producer design/spec decision record and source fixture family. Phase 92 closure need not wait for all presentation implementation; Phase 93 verifies the accepted baseline with its extension.

Both Phase plans/checklists, the original LexiDox proposal, presentation requirements, Phase index and Strategy refer to the joint plan. Existing requirements, open identity questions and histories remain; exact grammar and producer versions are design/spec deliverables rather than fabricated by this reconciliation.

## Scope

Planning/requirements reconciliation only. Both Phases remain planned and unstarted, with all criteria OPEN. No code, parser, runtime schema, public glossary definitions, DDD manuscript/output, release, commit or publication changes were selected.
