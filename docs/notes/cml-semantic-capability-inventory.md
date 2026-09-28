# CML Semantic Capability Inventory

Status: non-normative factual inventory

Date: 2026-09-28

Scope: PHASE-54 / MMD-54-01 / MMD-54-01A, attributable to Cozy phase base
`b2e525d7f11aebb3b760a26a3bfe1899ee14f544`. This note records the admitted
current semantic IR and generated metadata at that boundary. It does not add a
model, schema, producer output, ABI, consumer projection, or runtime behavior.

## Evidence boundary

The minimum view capabilities are defined by
[`cml-analysis-view-semantic-metadata.md`](cml-analysis-view-semantic-metadata.md).
The ownership and term-reference boundary is defined by
[`simplemodeling-model-integration-boundary.md`](../design/simplemodeling-model-integration-boundary.md),
and the current component metadata surface is described by
[`component-dashboard-model-metadata.md`](component-dashboard-model-metadata.md).

The current implementation evidence is bounded to these symbols and artifacts:

- [`CmlModelMetadata`](../../src/main/scala/cozy/modeler/CmlModelMetadata.scala)
  (`ModelMetadata`, `Source`, `ComponentSurface`, `ActorSurface`,
  `ActorReferenceSurface`, `UseCaseSurface`, `Element`, `Field`,
  `_model_elements`, `_actor_surfaces`, `_use_case_surface`, and `_sections`).
  Its `cozy.cml.model-metadata.v1` JSON/YAML artifact publishes `source`,
  `surface`, `modelElements`, fields, relationships, `inputKind`, `termId`,
  `glossaryPath`, and `rdfCandidates`.
- [`ModelTypeProjector`](../../src/main/scala/cozy/modeler/ModelTypeProjector.scala)
  (`projectEntity`, `_entity`, `_delegate_traits`, `_powertypes`, attributes,
  aggregate methods, and state-machine references).
- [`ModelerRelationshipCml`](../../src/main/scala/cozy/modeler/ModelerRelationshipCml.scala)
  (`relationshipDefinitions` and `MComponent.RelationshipDefinition`).
- [`ModelStateMachineProjector`](../../src/main/scala/cozy/modeler/ModelStateMachineProjector.scala)
  (`projectStateMachine`, `_build_state_map`,
  `_build_state_machine_transitions`, `_state_machine_definitions`, and
  `_state_machine_transition_rules`).
- [`CompositeStateMachineCml`](../../src/main/scala/cozy/modeler/CompositeStateMachineCml.scala)
  (`definitions`, `workflowDefinitions`, normalized `WorkflowDefinition`,
  `CompositeStateMachineDefinition`, and source-line identities).
- [`StateMachineWorkflowAbiGenerator`](../../src/main/scala/cozy/modeler/StateMachineWorkflowAbiGenerator.scala),
  whose additive `cozy.cml.statemachine-workflow-abi.v1` artifact contains
  workflow identity/version/source correlation, states, actions, and Required
  SPI records; its canonical JSON and `WorkflowDescriptor` contain Required
  operations only, while the generated common ABI declares both Required and
  Provided operation types.
- [`CozyBokModel`](../../src/main/scala/cozy/bok/CozyBokModel.scala)
  (`TermEntry` and `TermCmlLink`) and
  [`CozyBokGlossaryAnalysis`](../../src/main/scala/cozy/bok/CozyBokGlossaryAnalysis.scala)
  for curated terminology and existing glossary analysis.

Existing executable specifications are evidence surfaces only:
[`CmlModelMetadataSpec`](../../src/test/scala/cozy/modeler/CmlModelMetadataSpec.scala),
[`CompositeStateMachineCmlSpec`](../../src/test/scala/cozy/modeler/CompositeStateMachineCmlSpec.scala),
[`WorkflowCmlSpec`](../../src/test/scala/cozy/modeler/WorkflowCmlSpec.scala),
[`ModelerStateMachineProjectionSpec`](../../src/test/scala/cozy/modeler/ModelerStateMachineProjectionSpec.scala),
and [`StateMachineWorkflowAbiGenerationSpec`](../../src/test/scala/cozy/modeler/StateMachineWorkflowAbiGenerationSpec.scala).
No new execution or acceptance claim is supplied by this documentation-only
Step.

## Classification vocabulary

The `classification` column uses only these parent-frozen strength terms:

- `guaranteed`: an explicit, narrow current producer/IR contract, not a
  complete view;
- `faithfully derivable`: a mechanical projection of admitted semantics, not
  label inference;
- `partially represented`: some required semantics are available but not the
  whole requirement; and
- `missing`: no admitted guarantee at the named publication/reference
  boundary.

## View capability inventory

| Requirement | Classification | Current IR support and evidence | Current consumer publication | Gap / owner |
| --- | --- | --- | --- | --- |
| Mono-Koto | partially represented | Curated BoK `TermEntry` term type, term references, and explicit CML links exist in `CozyBokModel`; `CozyBokGlossaryAnalysis` supplies analysis. Mono/Koto and CML classification remain different axes. | BoK analysis is published, but there is no universal stable CML semantic graph. | Admitted concept grouping/link provenance and stable cross-model reference foundation; MMD-54-02, Phase 54.5/54.7. |
| Entity | guaranteed | Guaranteed only for explicit Entity/Value/Datatype source distinctions via `ModelTypeProjector._entity`/`_value`/`_datatype` respectively and modeled attributes/aggregate members. `CmlModelMetadata.Element`/`Field` carries normalized scalar/input kinds and fields. | `cozy.cml.model-metadata.v1` publishes `Element`/`Field` kinds and fields, but complete ownership/aggregate identity is not a connected publication contract. | Stable model IDs and aggregate membership/ownership reference strength; MMD-54-02 foundation and Phase 54.1. |
| Structure | partially represented | `ModelerRelationshipCml.relationshipDefinitions` preserves declared kind, source/target, multiplicity, storage, and optional lifecycle text. Explicit kind is available; storage defaults are not independently declared business policy. | v1 `Element.relationships` is free text and fields carry type/multiplicity; there is no complete source-attributed typed relationship graph. | Roles, navigability, ownership, independent existence, reparenting, and lifecycle semantics require admitted evidence and typed stable references; Phase 54.1. |
| Classification | partially represented | `ModelTypeProjector._entity` carries parents/delegate traits and `_powertypes` traverses `SchemaModel.PowertypeRelationship` slots. | v1 publishes powertype elements, not the full stable-ID generalization/trait/independent-dimension graph. | Cross-view stable references and faithful dimension publication; Phase 54.2. |
| Workflow | guaranteed | Guaranteed only for `CompositeStateMachineCml.workflowDefinitions` normalizing `WorkflowDefinition` identity/version/source correlation and lowering Composite StateMachine data. It preserves explicit Required/Provided operations; Required SPI to Provider binding is not an ORCHESTRATION/CONTINUATION attribute. | `StateMachineWorkflowAbiGenerator` publishes `cozy.cml.statemachine-workflow-abi.v1` with Workflow states/actions/Required SPI and bootstrap metadata, not a universal CBD view with participant, control-flow, and cross-view semantic references. | Detailed faithful dynamic projection and admitted operation/event/effect links; Phase 54.3. |
| Flowchart | faithfully derivable | Only an intentionally simplified subset of admitted Workflow IR is mechanically derivable; Flowchart is not a second weaker source model. | The Workflow artifact supplies a limited subset; it makes no claim of a complete Flowchart view. | Depends on faithful Workflow semantics; Phase 54.3, consumer-owned rendering. |
| StateMachine | guaranteed | Guaranteed only for currently modeled transitions, triggers, guards, and effects in `ModelStateMachineProjector` and normalized `CompositeStateMachineDefinition` constituent/derived actions and source-line correlation. | `cozy.cml.composite-statemachine-projection.v1` supplies normalized fields; v1 model-metadata StateMachine elements alone are not the full graph. | Stable owning/affected-subject IDs and cross-view event/workflow/operation links; Phase 54.3. |
| Use Case | partially represented | `CmlModelMetadata._use_case_surface` preserves an optional `ComponentSubsystemModel.UseCaseDefinition` ID, goal, trigger, pre/postconditions, and scenario flows. | `surface.component.useCases` preserves textual flow and Actor roles, but has no universal stable references or guaranteed realizing Workflow links. | Faithful stable cross-view reference handoff; Phase 54.4. |
| Actor | partially represented | `CmlModelMetadata._actor_surfaces` and `_actor_reference_surfaces` preserve explicit Actor names, kind, descriptions, and role strings. Local/external target classification comes from admitted Actor declarations. | `ActorReferenceSurface` publishes name, role, and target kind, but there is no source-stable Actor semantic identity contract. | Stable Actor links to modeled UseCase/Workflow/operations only; never infer Actors from runtime principals or operation labels; Phase 54.4. |
| Event / Event Storming | partially represented | The Modeler uses `EventModel` reception/subscription facts and StateMachine event triggers; operation `inputKind` distinguishes command/query Value. Full causal traversal is absent at the publication boundary. | Missing the complete Actor -> Command -> Aggregate -> Event -> Reaction -> subsequent-action stable-ID causal contract. `CmlModelMetadata._sections` does not publish Event/Workflow top-level elements; Component BoK event CML links are not proof of causality. | Detailed admitted causal links and external/Query/View references; Phase 54.6 after prior handoffs. Separate Event and Event Storming rows remain useful when detail is added. |
| Terminology / BoK | guaranteed | Guaranteed only for curated `TermEntry.id` and explicitly admitted BoK links; glossary semantics remain owned upstream. | Partially represented through legacy `CmlModelMetadata._logical_element`, `_raw_element`, `_ast_datatype_element`, and `_model_elements`, which synthesize `category:slug` `termId` values and glossary navigation paths from names. These generated values are not proof of an admitted stable Term binding. | Common source-attributed semantic references, vocabulary/profile/context, and explicit absence; MMD-54-02 then Phase 54.5. `rdfCandidates` remain candidates, not `sameAs`. |

## Common foundation inventory

| Foundation contract | Classification | Current evidence and boundary |
| --- | --- | --- |
| Universal stable model-element identity | partially represented | Workflow identity and optional Use Case ID exist. `CmlModelMetadata.Element` has kind/name and generated term/navigation values, but there is no uniform rename/path-independent model-element identity contract. |
| Source provenance | partially represented | `CmlModelMetadata.Source` guarantees file path, digest, compiler, and Cozy version in legacy ModelMetadata; dynamic IR carries source-line correlation. Universal element-level admitted references remain only partially represented. A source digest or temporary path is not a stable element ID. |
| Absence | missing | Options, empty strings, and arrays are existing representation, not a uniform typed absence/gap contract. That contract is missing at the named boundary; missing semantics must never be reinterpreted as false business policy. |
| Cross-references | partially represented | Typed/local-name links exist in relationship, operation, BoK, Use Case, and StateMachine paths. Universal stable-ID references and dangling/duplicate identity behavior are missing. |
| Versioning | guaranteed | Current distinct schema constants are narrowly guaranteed by `CmlModelMetadata._schema` and `StateMachineWorkflowAbiGenerator.schemaVersion`/`bootstrapSchemaVersion`. A consumer-neutral envelope/extension and legacy-preservation policy remains missing until MMD-54-03. |
| Phase 64 vocabulary | missing | The common vocabulary is not yet implemented. It is planned for MMD-54-02, and later Phase 64 consumes it rather than defining a parallel Term identity system. This inventory records the ownership direction; it does not implement that vocabulary. |

## Ownership and non-claims

Cozy owns semantics, semantic IR, and publication. Textus CBD Support owns
projections and gap review. CNCF owns runtime behavior. No external consumer
acceptance is needed for this inventory.

This Step preserves old metadata contracts and changes no producer output or
ABI. It does not infer policy, ontology, causality, synonyms, or grouping from
labels; it does not redefine Capability as Glossary or Glossary as Capability;
and it does not provide a dashboard, detailed model projection, or test-only
metadata assertion. MMD-54-02/03/04 and child Phases 54.1–54.7/64 remain
planned.
