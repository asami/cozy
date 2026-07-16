# CML Post-Phase-16 Policy Backlog

status=backlog
updated_at=2026-07-16
source_phase=16

## Purpose

This note is the explicit relocation target for CML Value, Datatype, scalar,
and I18N policy that is useful but not required by the accepted Phase 16
contract. Phase 16 does not remain open for these items.

## Relocated Work

- Define syntax, namespace, and normalization policy only for driver-owned
  nominal scalars whose current accepted contract deliberately leaves those
  concerns to an application or provider registry.
- Define control-character policy for technical text such as user-agent and
  provider diagnostic values where length and confidentiality are already
  enforced.
- Extend semantic text roles only when a new role needs a range different from
  the accepted label/title, headline/brief, summary-family,
  description/text baseline.
- Design a rich multilingual document-body profile without reintroducing an
  implicit `I18nText` to `ContentBody` display-locale conversion.
- Consider nested generated packages for operation-local Values if shared
  generated type names become an actual limitation; current deterministic
  collision rejection remains canonical.
- Refine secret no-display policy only for domain-owned presentation surfaces;
  generic controls and diagnostics already obey CML confidentiality and
  redaction.

Each item requires a new phase or an explicitly scoped maintenance slice before
implementation. This note is not an extension of the Phase 16 checklist.
