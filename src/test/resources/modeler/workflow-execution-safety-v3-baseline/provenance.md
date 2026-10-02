# Original Workflow v3 producer baseline

These four immutable fixtures are byte-for-byte copies of genuine public-route
outputs captured before the Phase68 generator edit. They are an independent
legacy oracle; never regenerate or update their expected bytes using a changed
producer. Generated fixture headers are retained unchanged.

Original native commit: `cea56ab50417d3398a5f4399d2e2d9d0aa751de3`.
Original producer: `src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala`.
Original input: `src/test/resources/modeler/workflow-lifecycle-policy.cml`.
Original executable specification:
`src/test/scala/cozy/modeler/WorkflowLifecycleAbiGenerationSpec.scala`.
The original producer, input and specification were preserved under
`target/phase68/work/legacy-v3-pre-edit/original-producer` before editing.

The parent captured the actual retained products from both
`target/test-generated/workflow-lifecycle-normal` and
`target/test-generated/workflow-lifecycle-value`; those public-route bytes agree.
The captured files in `target/phase68/work/legacy-v3-pre-edit` were copied directly
into this directory, with no producer execution or expected-byte regeneration.
The capture was made at accepted S68.1 commit
`0a8062750a390343f7339fa4a1af8c02a6e4a8fd`, while the original generator and fixture
were still unchanged. Capture record: P68-S682-LEGACY-V3-CAPTURE-001 revision1.

| Actual product | Bytes |
| --- | ---: |
| StateMachineWorkflowAbi.scala | 6736 |
| StateMachineWorkflowComponentFactoryBootstrap.scala | 334 |
| LifecycleWorkflowStateMachineWorkflow1.scala | 2548 |
| statemachine-workflow-abi.json | 1412 |

Original native execution P67-FINAL-VAL-001 retained the successful repeated
normal/value lifecycle producer behavior in
`/private/tmp/skill.cncf.d/a-8a023c1d-e4db-42bd-98d0-767fa2afd076/28550-20261002T085401Z.log`.
Its adjacent `.summary.json` records successful full tests and released lock;
the native result is
`/private/tmp/skill.cncf.d/a-a8a8dd53-cdc8-4290-93dd-44c5fe0cbd80/command-result.json`.
This is original Phase67 provenance, not new Phase68 validation evidence.

The new [producer executable specification](../../../scala/cozy/modeler/WorkflowExecutionSafetyAbiGenerationSpec.scala)
compares all four byte vectors on both public routes, each twice. Existing
v2 resources and v1 executable pins remain independent active legacy oracles.
Updated: 2026-10-03.
