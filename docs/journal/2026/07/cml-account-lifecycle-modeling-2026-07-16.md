# CML Account Lifecycle Modeling

Date: 2026-07-16

## Context

`textus-user-account` declared its account lifecycle as a CML state machine,
but the state type and transition table remained handwritten Scala. Matching
operation fields also accepted generic strings. The model therefore described
the lifecycle without owning its complete runtime contract.

## Decision

`UserAccountStatus` is a generated CML powertype with the external values
`provisional`, `registered`, `formal`, and `suspended`. Its explicit datastore
values remain `0`, `1`, `2`, and `3`. `UserAccount.status`, account
create/update/list Values, and the CML `status` state machine all reference the
same type.

The state machine owns these transitions:

- `provisional -> formal`
- `provisional -> suspended`
- `registered -> suspended`
- `formal -> suspended`
- `suspended -> registered`

Generated transition metadata is also the source used by the handwritten
account workflow policy. The former handwritten `UserAccountStatus` class and
duplicated transition map are removed.

Access and refresh sessions do not currently carry a finite string status.
Their issue, expiry, revocation, and rotation timestamps are the canonical
lifecycle representation, so this slice does not invent session or credential
powertypes.

## Verification Contract

Executable specifications verify the external and datastore status values,
the entity transition topology, matching operation parameter datatypes,
accepted transitions, rejected transitions, authentication policy, and
activation-status projection.
