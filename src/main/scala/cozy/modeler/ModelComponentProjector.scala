package cozy.modeler

import org.simplemodeling.model._
import org.simplemodeling.model.domain._
import org.simplemodeling.SimpleModeler.generator.scala.Generator.{State => GState, _}
import org.smartdox.{Description, Dox}
import org.goldenport.RAISE
import org.goldenport.collection.VectorMap
import org.goldenport.values.Designation
import org.goldenport.values.PathName
import org.goldenport.record.v2.Column
import org.goldenport.sm._
import org.goldenport.sm.{ExecutionContext => StateMachineContext}
import org.goldenport.sm.StateMachineClass
import org.goldenport.sexpr._
import org.goldenport.util.StringUtils
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.kaleidox.lisp.Context
import org.goldenport.kaleidox.model.{SchemaModel, EntityModel, DataTypeModel}
import org.goldenport.kaleidox.model.{PowertypeModel, StateMachineModel, EventModel}
import org.goldenport.kaleidox.model.ComponentSubsystemModel
import org.goldenport.kaleidox.model.OperationModel
import org.goldenport.kaleidox.model.ServiceModel
import org.goldenport.kaleidox.model.ValueModel
import org.goldenport.kaleidox.model.actor.ActorModel
import org.goldenport.kaleidox.model.CmlExpressionGuard
import org.goldenport.kaleidox.CmlSectionFormat
import org.goldenport.kaleidox.model.SchemaModel.SchemaClass
import org.goldenport.kaleidox.model.EntityModel.EntityClass
import org.goldenport.kaleidox.model.DataTypeModel.DataTypeClass
import org.goldenport.kaleidox.model.PowertypeModel.PowertypeClass
import org.goldenport.kaleidox.model.ValueModel.ValueClass
import org.goldenport.parser.LogicalSection
import scala.collection.mutable


/*
 * @since Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author ASAMI, Tomoharu
 */
private[modeler] final class ModelComponentProjector(
  val context: ModelBuildContext,
  val stateMachine: ModelStateMachineProjector,
  val serviceOperation: ModelServiceOperationProjector,
  val runtimeDefinition: ModelRuntimeDefinitionProjector
) {
  import context._
  import Modeler._
  private[modeler] def complementComponents(p: SimpleModel): Vector[MComponent] = _complement_components(p)
    private def _complement_components(p: SimpleModel): Vector[MComponent] =
      _complement_components_package(p, p.root)

    private def _complement_components_package(
      sm: SimpleModel,
      pkg: MPackage
    ): Vector[MComponent] = {
      val a = if (pkg.components.isEmpty) {
        val entities = pkg.entities
        val isrootpackage = pkg == sm.root
        val explicitlydeclared = componentSubsystem.components.exists(_.name == pkg.name) ||
          (isrootpackage && componentSubsystem.components.lengthCompare(1) == 0)
        if (entities.nonEmpty || (isrootpackage && service.classes.nonEmpty) || explicitlydeclared) {
          val comp = _make_component(pkg, entities)
          Vector(comp)
        } else {
          Vector.empty
        }
      } else {
        Vector.empty
      }
      val subpackages = pkg.subpackages
      val xs = subpackages.flatMap(_complement_components_package(sm, _))
      a ++ xs
    }

    private def _make_component(
      pkg: MPackage,
      entities: Vector[MEntity]
    ): MComponent = {
      // val compclassname = s"${StringUtils.makeTitle(pkg.name)}Component"
      val compname = _component_name(pkg)
      val desc = _description(compname, _component_description_for(pkg))
      val componentpackagename = _component_package_name(pkg).getOrElse(pkg.name)
      val servicepkg = _service_package(pkg, componentpackagename)
      val definedservices = serviceOperation.definedServices(servicepkg, entities)
      val entityservice: MService = runtimeDefinition.makeEntityService(servicepkg, entities)
      val aggregateservice: MService = runtimeDefinition.makeAggregateService(servicepkg, entities)
      val viewservice: MService = runtimeDefinition.makeViewService(servicepkg, entities)
      val core = MObject.Core(
        affiliation = MPackageRef(componentpackagename),
        services = definedservices.toList ++ List(aggregateservice, viewservice, entityservice)
      )
      val transitionrules = stateMachine.transitionRules(entities)
      val statemachinedefs = runtimeDefinition.stateMachineDefinitions(entities)
      val eventdefs = runtimeDefinition.eventReceptionDefinitions(entities)
      val eventroutes = runtimeDefinition.eventRoutingDefinitions
      val eventsubs = runtimeDefinition.eventSubscriptionDefinitions
      val aggregates = runtimeDefinition.aggregateDefinitions(entities)
      val views = runtimeDefinition.viewDefinitions(entities)
      val operations = runtimeDefinition.operationDefinitions ++ runtimeDefinition.entityOperationDefinitions(entities)
      val components = _component_definitions(pkg)
      val subsystems = _subsystem_definitions(pkg)
      val entitydescs = runtimeDefinition.entityRuntimeDescriptors(entities)
      val ccore = MComponent.Core(
        entities = entities,
        entityRuntimeDescriptors = entitydescs,
        stateMachineTransitionRules = transitionrules,
        stateMachineDefinitions = statemachinedefs,
        eventReceptionDefinitions = eventdefs,
        eventRoutingDefinitions = eventroutes,
        eventSubscriptionDefinitions = eventsubs,
        aggregateDefinitions = aggregates,
        viewDefinitions = views,
        relationshipDefinitions = relationships,
        operationDefinitions = operations,
        componentDefinitions = components,
        subsystemDefinitions = subsystems
      )
      MDomainComponent(desc, core, ccore)
    }

    private def _service_package(
      pkg: MPackage,
      packagename: String
    ): MPackage = pkg.copy(
      designation = Designation(packagename),
      affiliation = MPackageRef.default
    )

    private def _component_package_name(
      pkg: MPackage
    ): Option[String] =
      _component_definition_for(pkg).flatMap(_.packageName)

    private def _component_name(
      pkg: MPackage
    ): String =
      _component_definition_for(pkg).map(_.name).getOrElse(pkg.name)

    private def _component_definition_for(
      pkg: MPackage
    ): Option[ComponentSubsystemModel.ComponentDefinition] =
      componentSubsystem.components.find(_.name == pkg.name).orElse {
        if (componentSubsystem.components.lengthCompare(1) == 0)
          componentSubsystem.components.headOption
        else
          None
      }

    private def _description(
      name: String,
      text: Option[String]
    ): Description =
      text.map(_.trim).filter(_.nonEmpty).
        map(s => Description.name(name, Dox.text(s))).
        getOrElse(Description.name(name))

    private def _component_description_for(
      pkg: MPackage
    ): Option[String] =
      _component_definition_for(pkg).flatMap { p =>
        _append_use_cases(p.description, p.useCases.map(_component_use_case_text))
      }
    private def _component_package_override(
      componentname: String
    ): Option[String] =
      componentSubsystem.components.find(_.name == componentname).flatMap(_.packageName).orElse {
        if (componentSubsystem.components.lengthCompare(1) == 0)
          componentSubsystem.components.headOption.flatMap(_.packageName)
        else
          None
      }
    private def _append_use_cases(
      base: Option[String],
      usecases: Vector[String]
    ): Option[String] = {
      val xs = usecases.map(_.trim).filter(_.nonEmpty)
      val usecasetext =
        if (xs.isEmpty)
          None
        else
          Some(xs.mkString("Use cases:\n", "\n", ""))
      (base.map(_.trim).filter(_.nonEmpty), usecasetext) match {
        case (Some(a), Some(b)) => Some(s"$a\n\n$b")
        case (Some(a), None) => Some(a)
        case (None, Some(b)) => Some(b)
        case _ => None
      }
    }

    private def _component_use_case_text(
      p: ComponentSubsystemModel.UseCaseDefinition
    ): String = {
      val summary = p.summary.orElse(p.description).getOrElse("")
      val actor = _use_case_actor_text(p.actor, p.primaryActor, p.secondaryActor, p.supportingActor, p.stakeholder)
      val base = if (summary.nonEmpty) s"- ${p.name}: ${summary}" else s"- ${p.name}"
      actor.map(x => s"${base} (${x})").getOrElse(base)
    }
    private def _use_case_actor_text(
      actor: Option[String],
      primaryactor: Option[String],
      secondaryactor: Option[String],
      supportingactor: Option[String],
      stakeholder: Option[String]
    ): Option[String] = {
      val xs = Vector(
        actor.map(x => s"actor=${x.trim}"),
        primaryactor.map(x => s"primary=${x.trim}"),
        secondaryactor.map(x => s"secondary=${x.trim}"),
        supportingactor.map(x => s"supporting=${x.trim}"),
        stakeholder.map(x => s"stakeholder=${x.trim}")
      ).flatten.filter(_.nonEmpty)
      if (xs.isEmpty) None else Some(xs.mkString(", "))
    }
    private def _component_definitions(
      pkg: MPackage
    ): Vector[MComponent.ComponentDefinition] = {
      val all = componentSubsystem.components.sortBy(_.name)
      val selected = {
        val matched = all.filter(_.name == pkg.name)
        if (matched.nonEmpty)
          matched
        else
          all
      }
      if (selected.nonEmpty)
        selected.map(_component_definition)
      else
        Vector(_default_component_definition(pkg))
    }

    private def _component_definition(
      p: ComponentSubsystemModel.ComponentDefinition
    ): MComponent.ComponentDefinition = {
      MComponent.ComponentDefinition(
        name = p.name,
        actors = actor.actors.map { a =>
          MComponent.ActorDefinition(a.name, a.kind, a.summary, a.description)
        },
        coordinates = p.coordinates.map { c =>
          MComponent.ComponentCoordinate(
            group = c.group,
            artifact = c.artifact,
            version = c.version
          )
        },
        componentlets = _componentlet_names_for_component(p),
        extensionPoints = _extension_point_names_for_component(p),
        extensionBindings = p.extensionBindings,
        domainVisions = Vector.empty,
        domainCapabilities = Vector.empty,
        domainQualities = Vector.empty,
        domainConstraints = Vector.empty,
        domainUseCases = Vector.empty,
        useCases = p.useCases.map(_component_use_case_definition),
        services = p.services.map(_component_service_definition)
      )
    }

    private def _default_component_definition(
      pkg: MPackage
    ): MComponent.ComponentDefinition = {
      MComponent.ComponentDefinition(
        name = pkg.name,
        actors = actor.actors.map { a =>
          MComponent.ActorDefinition(a.name, a.kind, a.summary, a.description)
        },
        coordinates = Vector.empty,
        componentlets = _unbound_componentlet_names(),
        extensionPoints = _unbound_extension_point_names(),
        extensionBindings = Map.empty,
        domainVisions = Vector.empty,
        domainCapabilities = Vector.empty,
        domainQualities = Vector.empty,
        domainConstraints = Vector.empty,
        domainUseCases = Vector.empty,
        useCases = Vector.empty,
        services = Vector.empty
      )
    }

    private def _component_service_definition(
      p: ComponentSubsystemModel.ComponentServiceDefinition
    ): MComponent.ComponentServiceDefinition = {
      if (p.spiDirection == "provides" && p.spiSocket && !service.classes.contains(p.name))
        RAISE.syntaxErrorFault(
          s"Component service '${p.name}' publishes a component API but no top-level SERVICE definition exists."
        )
      MComponent.ComponentServiceDefinition(
        name = p.name,
        spiStandard = p.spiStandard,
        spiDirection = p.spiDirection,
        spiSocket = p.spiSocket,
        spiMultiplicity = p.spiMultiplicity,
        spiRequired = p.spiRequired,
        spiApiName = p.spiApiName,
        spiComponentApi = p.spiComponentApi
      )
    }

    private def _component_capability_definition(
      p: ComponentSubsystemModel.CapabilityDefinition
    ): MComponent.CapabilityDefinition =
      MComponent.CapabilityDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description,
        actor = p.actor,
        primaryActor = p.primaryActor,
        secondaryActor = p.secondaryActor,
        supportingActor = p.supportingActor,
        stakeholder = p.stakeholder,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition
      )

  private def _component_vision_definition(
    p: ComponentSubsystemModel.VisionDefinition
  ): MComponent.VisionDefinition =
      MComponent.VisionDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition
      )

    private def _component_context_definition(
      p: ComponentSubsystemModel.ContextDefinition
    ): MComponent.ContextDefinition =
      MComponent.ContextDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description
      )

    private def _component_system_context_definition(
      p: ComponentSubsystemModel.SystemContextDefinition
    ): MComponent.SystemContextDefinition =
      MComponent.SystemContextDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description
      )

    private def _component_context_map_definition(
      p: ComponentSubsystemModel.ContextMapDefinition
    ): MComponent.ContextMapDefinition =
      MComponent.ContextMapDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description
      )

    private def _component_quality_definition(
      p: ComponentSubsystemModel.QualityDefinition
    ): MComponent.QualityDefinition =
      MComponent.QualityDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition
      )

    private def _component_constraint_definition(
      p: ComponentSubsystemModel.ConstraintDefinition
    ): MComponent.ConstraintDefinition =
      MComponent.ConstraintDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition
      )

    private def _component_use_case_definition(
      p: ComponentSubsystemModel.UseCaseDefinition
    ): MComponent.UseCaseDefinition =
      MComponent.UseCaseDefinition(
        name = p.name,
        id = p.id,
        summary = p.summary,
        description = p.description,
        actor = p.actor,
        primaryActor = p.primaryActor,
        secondaryActor = p.secondaryActor,
        supportingActor = p.supportingActor,
        stakeholder = p.stakeholder,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition,
        trigger = p.trigger,
        priority = p.priority,
        status = p.status,
        actorReferences = _actor_references(p.actor, p.primaryActor, p.secondaryActor, p.supportingActor, p.stakeholder),
        scenarios = p.scenarios.map { s =>
          MComponent.UseCaseScenario(
            name = s.name,
            kind = s.kind,
            summary = s.summary,
            description = s.description,
            steps = s.steps,
            alternates = s.alternates,
            exceptions = s.exceptions
          )
        }
      )

    private def _actor_references(
      actorref: Option[String],
      primaryactor: Option[String],
      secondaryactor: Option[String],
      supportingactor: Option[String],
      stakeholder: Option[String]
    ): Vector[MComponent.ActorReference] = {
      val localnames = actor.actors.map(_.name).toSet
      Vector(
        "actor" -> actorref,
        "primary" -> primaryactor,
        "secondary" -> secondaryactor,
        "supporting" -> supportingactor,
        "stakeholder" -> stakeholder
      ).flatMap { case (role, names) =>
        names.toVector.flatMap(_split_actor_names).map { name =>
          MComponent.ActorReference(
            name = name,
            role = role,
            targetKind = if (localnames.contains(name)) "actor" else "external"
          )
        }
      }
    }

    private def _split_actor_names(p: String): Vector[String] =
      p.split("[,\\n]").toVector.map(_.trim).filter(_.nonEmpty)

    private def _componentlet_names_for_component(
      p: ComponentSubsystemModel.ComponentDefinition
    ): Vector[String] =
      _distinct_stable(
        p.componentlets ++ componentSubsystem.componentlets.collect {
          case x if x.component.contains(p.name) => x.name
        }
      )

    private def _extension_point_names_for_component(
      p: ComponentSubsystemModel.ComponentDefinition
    ): Vector[String] =
      _distinct_stable(
        p.extensionPoints ++ componentSubsystem.extensionPoints.collect {
          case x if x.component.contains(p.name) => x.name
        }
      )

    private def _unbound_componentlet_names(
    ): Vector[String] =
      _distinct_stable(
        componentSubsystem.componentlets.collect {
          case x if x.component.isEmpty => x.name
        }
      )

    private def _unbound_extension_point_names(
    ): Vector[String] =
      _distinct_stable(
        componentSubsystem.extensionPoints.collect {
          case x if x.component.isEmpty => x.name
        }
      )

    private def _subsystem_definitions(
      pkg: MPackage
    ): Vector[MComponent.SubsystemDefinition] = {
      val explicit = componentSubsystem.subsystems.sortBy(_.name).map { p =>
        MComponent.SubsystemDefinition(
          name = p.name,
          components = p.components.map { c =>
            MComponent.ComponentCoordinate(
              group = c.group,
              artifact = c.artifact,
              version = c.version
            )
          },
          extensionBindings = p.extensionBindings,
          config = p.config
        )
      }
      val defaultname = _default_subsystem_name(pkg)
      val toplevelrequirements =
        if (_has_top_level_requirement_model)
          Some(
            MComponent.SubsystemDefinition(
              name = defaultname,
              domainVisions = componentSubsystem.visions.map(_component_vision_definition),
              domainContexts = componentSubsystem.contexts.map(_component_context_definition),
              domainSystemContexts = componentSubsystem.systemContexts.map(_component_system_context_definition),
              domainContextMaps = componentSubsystem.contextMaps.map(_component_context_map_definition),
              domainCapabilities = componentSubsystem.capabilities.map(_component_capability_definition),
              domainQualities = componentSubsystem.qualities.map(_component_quality_definition),
              domainConstraints = componentSubsystem.constraints.map(_component_constraint_definition),
              domainUseCases = componentSubsystem.useCases.map(_component_use_case_definition)
            )
          )
        else
          None
      toplevelrequirements.map { req =>
        explicit.indexWhere(_.name == req.name) match {
          case -1 => explicit :+ req
          case i =>
            explicit.updated(
              i,
              explicit(i).copy(
                domainVisions = req.domainVisions,
                domainContexts = req.domainContexts,
                domainSystemContexts = req.domainSystemContexts,
                domainContextMaps = req.domainContextMaps,
                domainCapabilities = req.domainCapabilities,
                domainQualities = req.domainQualities,
                domainConstraints = req.domainConstraints,
                domainUseCases = req.domainUseCases
              )
            )
        }
      }.getOrElse(explicit)
    }

    private def _default_subsystem_name(
      pkg: MPackage
    ): String =
      pkg.name

    private def _has_top_level_requirement_model: Boolean =
      componentSubsystem.visions.nonEmpty ||
        componentSubsystem.contexts.nonEmpty ||
        componentSubsystem.systemContexts.nonEmpty ||
        componentSubsystem.contextMaps.nonEmpty ||
        componentSubsystem.capabilities.nonEmpty ||
        componentSubsystem.qualities.nonEmpty ||
        componentSubsystem.constraints.nonEmpty ||
        componentSubsystem.useCases.nonEmpty

    private def _distinct_stable(
      p: Vector[String]
    ): Vector[String] =
      p.foldLeft(Vector.empty[String]) { (z, x) =>
        val s = Option(x).map(_.trim).getOrElse("")
        if (s.isEmpty || z.contains(s))
          z
        else
          z :+ s
      }

}
