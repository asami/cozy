# Phase 4 Checklist

## SM-05

- [x] `Modeler._statemachine` builds full state/transition graph (states, transitions, guards, events)
- [x] Missing event (`on`) / unknown transition target / undeclared event are explicit failures in Cozy-side generation path
- [x] Guard mapping coverage validated (`guardRef`, expression, composite-like expression text)
- [x] Determinism path verified with declaration-order assertions in generation spec
- [x] Cross-repo E2E (scripted) executed with generated project + CNCF embedding (`CncfBootstrap` / `CncfHandle`)
