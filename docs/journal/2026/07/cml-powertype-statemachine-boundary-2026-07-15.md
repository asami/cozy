# CML Powertype and Statemachine Boundary

date=2026-07-15
phase=16
status=decision

## Context

The Phase 16 driver inventory contains string-only wrappers whose names suggest
kind, type, provider, priority, or status. Replacing every such wrapper with a
powertype would still lose lifecycle behavior, while treating every status as
a statemachine would incorrectly make externally owned state part of the local
model.

## Decision

The deciding evidence is vocabulary closure and transition ownership:

- a closed selector without model-owned transitions is a `POWERTYPE`;
- a lifecycle whose transitions, guards, events, or actions are owned by the
  model is a `STATEMACHINE`;
- an application-, provider-, tenant-, or extension-defined registry remains
  an open identifier or constrained scalar;
- externally owned lifecycle state remains an observed external value unless
  the local model defines its own transition policy.

Localized labels, ordering, and display metadata do not turn a powertype into a
statemachine. Conversely, a finite list of states does not justify a powertype
when the model owns transition rules.

## Driver Implication

The rule confirms the structural direction for notification audience kind,
channel, and priority. It does not yet close the vocabulary of notification
type or delivery provider. Notification status remains a domain decision until
notification lifecycle and delivery-attempt lifecycle are separated and their
transition ownership is explicit.

No driver CML is changed by this decision record.
