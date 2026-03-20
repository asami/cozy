# Event Reception Integration Handoff (CNCF -> Cozy)

Date: 2026-03-20
Status: handoff-ready
Owner: cozy-modeler

## Purpose

Align Cozy generation with the latest CNCF Event Reception runtime behavior.

Reference implementation note:
- /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/event-reception-latest-processing-spec.md

## Runtime Summary (CNCF)

Current reception path:
1. `ReceptionInput` arrives.
2. `CmlEventDefinition` is matched (`name/kind/selectors`).
3. Event is published through `EventBus` (`persistent` policy preserved).
4. Routing executes through listener surfaces:
   - `StateMachineEventListener`
   - `DirectEventListener`
   - `EntityEventSubscription`

`ReceptionResult` is returned with deterministic `Routed`/`Dropped` and count.

## Event Category Model

`CmlEventDefinition.category`:
- `ActionEvent`
- `NonActionEvent`

Interpretation:
- `ActionEvent`: listener-side action execution path.
- `NonActionEvent`: listener-owned handling (no required direct action route).

## Security Contract

Ingress security resolution is now explicit:
- Operation ingress: resolved from request properties.
- Reception ingress:
  - `receiveAuthorized`: uses provided `ExecutionContext`
  - `receiveSecured`: resolves from `ReceptionInput.attributes`

Cozy should provide enough metadata for privilege/capability resolution attributes.

## Entity Event Handling

### Route Type

`EntityEventRoute`:
- `Direct`
- `PubSub`

### Activation

`EntityActivationMode`:
- `ActivateOnReceive`
- `KeepResident`

Resolution behavior:
- `ActivateOnReceive`: load-through via `EntityCollection.resolve(id)`.
- `KeepResident`: memory-only; miss is failure.

### Registration-Time Guard Rails

`EntitySubscriptionLimit` is validated at registration:
- `maxTotalSubscriptions`
- `maxSubscriptionsPerEntity`
- `maxDeclaredTargetUpperBound`

`declaredTargetUpperBound` is mandatory.

Rule:
- `PubSub` requires `KeepResident`.

## Working Set Coupling

CNCF now passes `workingSetEntities` into `EventReception` via `ComponentFactory.createEventReception(...)`.

Normalization rule:
- If `entityName` is in `workingSetEntities` and route is `PubSub`, activation is forced to `KeepResident`.

Implication:
- working-set pub/sub subscriptions are treated as memory-resident routes.

## Cozy Required Generation Inputs

For each event definition:
1. `name`
2. `category` (`ActionEvent`/`NonActionEvent`)
3. `kind` (optional)
4. `selectors`

For each entity subscription:
1. `entityName`
2. `route` (`Direct`/`PubSub`)
3. `declaredTargetUpperBound`
4. activation intent (`KeepResident`/`ActivateOnReceive`)
5. target resolver expression/model
6. handler binding target

For each entity runtime plan:
1. working-set marker
2. memory policy and partition strategy

## Suggested Cozy Implementation Order

1. Emit `CmlEventDefinition.category` in generated metadata.
2. Emit entity subscription metadata (`route`, `declaredTargetUpperBound`, activation intent).
3. Ensure working-set entities are emitted consistently with pub/sub definitions.
4. Add generated resolver stubs for entity target resolution.
5. Add verification scenario:
   - ActionEvent route
   - NonActionEvent route
   - PubSub keep-resident behavior
   - registration-time upper-bound rejection

## Verification Checklist (Cozy Side)

- [x] Generated metadata includes event category.
- [ ] Generated metadata includes entity route type.
- [ ] `declaredTargetUpperBound` emitted for each entity subscription.
- [ ] Working-set entity + pub/sub normalizes to keep-resident behavior in integrated runtime.
- [ ] Integration scenario confirms deterministic route/drop/failure semantics.
