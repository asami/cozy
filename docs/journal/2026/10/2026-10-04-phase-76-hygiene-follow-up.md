<!-- cncf-phase-finding:HYG-COZY76-P7603-MODELER-LOCAL-NAMING-001:begin -->
### HYG-COZY76-P7603-MODELER-LOCAL-NAMING-001

- Kind: hygiene
- Owner repository: /Users/asami/src/dev2025/cozy-phase76
- Status: OPEN
- Evidence: src/main/scala/cozy/modeler/Modeler.scala:215-216 binds and uses ordinary local resourceId instead of flatcase resourceid. Both lines are unchanged from accepted HEAD d9e106ca7d154402487274e189063f4276d0d94a.
- Risk: inconsistent declaration-scope naming in historical StateMachine guard rendering; no Logical UI behavior or validation risk was found.
- Maintenance boundary: a later Cozy naming-maintenance task may rename only the local binder and its interpolation use in _guard_mark_, preserving generated guard semantics. This review does not admit that cleanup into P76-03A.
<!-- cncf-phase-finding:HYG-COZY76-P7603-MODELER-LOCAL-NAMING-001:end -->

<!-- cncf-phase-finding:HYG-COZY76-P7603-MODELER-RESPONSIBILITY-001:begin -->
### HYG-COZY76-P7603-MODELER-RESPONSIBILITY-001

- Kind: hygiene
- Owner repository: /Users/asami/src/dev2025/cozy-phase76
- Status: OPEN
- Evidence: Modeler.scala is 860 lines versus 836 at accepted HEAD d9e106ca7d154402487274e189063f4276d0d94a, above the 800-line split-evaluation trigger. The file already combines StateMachine projection, generation routes, Explain, Help, Linkage and ModelBuilder; ModelBuilder at line 755 carries seventeen constructor inputs and projector wiring at lines 774-778.
- Review disposition: nonblocking historical responsibility-maintenance follow-up. P76-03A adds bounded two-route preflight and delegates rendering to a separate 127-line LogicalUiScalaGenerator. No current correctness or reviewability failure was found.
- Maintenance boundary: evaluate decomposition in a separately admitted Cozy maintenance task alongside ModelBuildContext, projector dependencies and callers, preserving package visibility, public API and construction semantics. Do not split unrelated responsibilities merely to reduce line count during P76-03A.
<!-- cncf-phase-finding:HYG-COZY76-P7603-MODELER-RESPONSIBILITY-001:end -->

<!-- cncf-phase-finding:HYG-COZY76-P7603-README-CLI-PROJECTION-001:begin -->
### HYG-COZY76-P7603-README-CLI-PROJECTION-001

- Kind: hygiene
- Owner repository: /Users/asami/src/dev2025/cozy-phase76
- Status: OPEN
- Evidence: unchanged README.md:107 says CLI tooling will be provided in future releases; line 115 shows cozy generate --input model.cml --out src/generated. Current Cozy.scala:311-312 implements ordinary modeler-scala and modeler-scala-value commands. The README statements are present at accepted HEAD d9e106ca7d154402487274e189063f4276d0d94a.
- Risk: the general overview can direct readers toward an outdated generation workflow. The P76-03A owned producer documents correctly identify the ordinary routes.
- Maintenance boundary: reconcile CLI availability and the generation example in a separately admitted README maintenance task, using current command/help syntax. README.md remains outside this Step's owned mutation scope.
<!-- cncf-phase-finding:HYG-COZY76-P7603-README-CLI-PROJECTION-001:end -->

<!-- cncf-phase-finding:HYG-COZY76-REASSESSMENT-MODELER-DATA-OWNERSHIP-001:begin -->
### HYG-COZY76-REASSESSMENT-MODELER-DATA-OWNERSHIP-001

- Kind: hygiene
- Owner repository: /Users/asami/src/dev2025/cozy-phase76
- Status: OPEN
- Evidence: src/main/scala/cozy/modeler/Modeler.scala:251 declares local case class Z inside _normalize_init of instance-bearing class Modeler. The same declaration exists unchanged at comparison base c6b108b8f301d4b1928e3f7f1b43d3e17f8e3739. ai/directive/core/type-modeling.md requires data declarations to have instance-independent ownership and does not exempt private or local case classes.
- Risk: historical data-declaration ownership does not follow the shared type-modeling rule. No Logical UI producer correctness, ABI, generated-output or validation impact was found; Phase 76 introduces no such declaration.
- Maintenance boundary: a separately admitted Cozy StateMachine modeling maintenance task may move this independent accumulator data to a stable package-level or companion-object owner, preserving initialization ordering, state selection and caller behavior. This comparison review admits no implementation or unrelated refactor.
<!-- cncf-phase-finding:HYG-COZY76-REASSESSMENT-MODELER-DATA-OWNERSHIP-001:end -->
