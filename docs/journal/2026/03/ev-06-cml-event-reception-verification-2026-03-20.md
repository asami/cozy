# EV-06 Cozy Verification (CML Event -> Reception -> ActionCall)

status=done  
date=2026-03-20  
repo=/Users/asami/src/dev2025/cozy

## Scope
- Verify Cozy-side path from CML event metadata to CNCF `EventReception`
- Confirm deterministic route/drop/failure behavior
- Keep changes additive (no boundary break)

## Added Verification Scenario
- scripted project:
  - `src/sbt-test/cozy/event-reception-routing`
- files:
  - `build.sbt`
  - `project/build.properties`
  - `test`
  - `test.dox`
  - `check-ev06.sh`

## CML -> ReceptionInput Mapping Specification
The scripted scenario defines an explicit `### Event` block in `test.dox`.

Mapping:
- `#### <event-name>` -> `CmlEventDefinition.name`
- `- kind :: <value>` -> `CmlEventDefinition.kind`
- `- selector :: <k>=<v>` -> `CmlEventDefinition.selectors(k)=v`
- `- actionName :: <value>` -> `CmlEventDefinition.actionName`
- `- priority :: <int>` -> `CmlEventDefinition.priority`

`check-ev06.sh` generates `domain.EventReceptionProbe` which:
1. loads generated `DomainComponent.eventReceptionDefinitions`,
2. registers them into `EventReception.default(EventBus.default(...), dispatcher)`,
3. executes EV-06 cases.

## Verified Cases
- A: target match (`name/kind/selector`) -> `Routed`, `dispatchedCount=1`
- B: non-target (kind mismatch) -> `Dropped`, `reason=non-target`
- C: unknown event -> `Failure`
- D: known event but no action binding -> `Failure` (subscription mismatch)
- E: authorized entry + user privilege -> `Failure` (policy denial)

Additional checks:
- deterministic behavior: same input returns same route outcome/count
- `persistent=true` writes event
- `persistent=false` does not write event

## Commands
- CNCF publishLocal (to expose EV-06 API classes in local Ivy):
  - `cd /Users/asami/src/dev2025/cloud-native-component-framework`
  - `sbt --batch publishLocal`
- Cozy verification:
  - `cd /Users/asami/src/dev2025/cozy`
  - `sbt --batch "scripted cozy/event-reception-routing"`

## Observed Result
- scripted result: pass (`EV06_RECEPTION_OK`)
- failure observations are validated by taxonomy/descriptor path, not message equality:
  - category: `Operation`
  - symptom: `Invalid` (unknown/subscription mismatch), policy-denial observed as failure via authorized path
  - facet: captured from observation descriptor facet (`toString`) in probe report

## Artifact
- probe report file produced by scripted run:
  - `out.d/target/ev06/event-reception-result.txt`
