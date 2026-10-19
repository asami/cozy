# Workbench STEP-2 Hygiene follow-up

Date: 2026-10-19
Status: nonblocking maintenance records; no implementation authorized

<!-- cncf-phase-finding:HYG-TKW-S2-002:begin -->
## HYG-TKW-S2-002: Stable ownership of existing state-machine accumulator

Owner: Cozy. Status: OPEN, nonblocking. src/main/scala/cozy/modeler/Modeler.scala retains the pre-existing method-local case class Z under its instance-bearing initialization owner. Move that accumulator to an instance-independent owner when the state-machine helper is explicitly selected, preserving fields, callers and behavior. Do not reopen closed state-machine work or expand the persistence-code Step.
<!-- cncf-phase-finding:HYG-TKW-S2-002:end -->
