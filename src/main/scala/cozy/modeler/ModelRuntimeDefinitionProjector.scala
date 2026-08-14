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
private[modeler] final class ModelRuntimeDefinitionProjector(
  val context: ModelBuildContext,
  val typeProjector: ModelTypeProjector,
  val stateMachine: ModelStateMachineProjector,
  val serviceOperation: ModelServiceOperationProjector
) {
  import context._
  import Modeler._
    private[modeler] def entityRuntimeDescriptors(entities: Vector[MEntity]): Vector[MComponent.EntityRuntimeDescriptor] = _entity_runtime_descriptors(entities)
    private[modeler] def eventReceptionDefinitions(entities: Vector[MEntity]): Vector[MComponent.EventReceptionDefinition] = _event_reception_definitions(entities)
    private[modeler] def eventRoutingDefinitions: Vector[MComponent.EventRoutingDefinition] = _event_routing_definitions()
    private[modeler] def eventSubscriptionDefinitions: Vector[MComponent.EventSubscriptionDefinition] = _event_subscription_definitions()
    private[modeler] def aggregateDefinitions(entities: Vector[MEntity]): Vector[MComponent.AggregateDefinition] = _aggregate_definitions(entities)
    private[modeler] def viewDefinitions(entities: Vector[MEntity]): Vector[MComponent.ViewDefinition] = _view_definitions(entities)
    private[modeler] def operationDefinitions: Vector[MComponent.OperationDefinition] = _operation_definitions()
    private[modeler] def entityOperationDefinitions(entities: Vector[MEntity]): Vector[MComponent.OperationDefinition] = _entity_operation_definitions(entities)
    private[modeler] def makeEntityService(pkg: MPackage, entities: Vector[MEntity]): MService = _make_entity_service(pkg, entities)
    private[modeler] def makeAggregateService(pkg: MPackage, entities: Vector[MEntity]): MService = _make_aggregate_service(pkg, entities)
    private[modeler] def makeViewService(pkg: MPackage, entities: Vector[MEntity]): MService = _make_view_service(pkg, entities)
    private[modeler] def stateMachineDefinitions(entities: Vector[MEntity]): Vector[MComponent.StateMachineDefinition] = stateMachine.definitions(entities)
    private def _entity_runtime_descriptors(
      entities: Vector[MEntity]
    ): Vector[MComponent.EntityRuntimeDescriptor] =
      entities.map { entity =>
        val sourceentity = _source_entity_class(entity)
        val issimpleentity = sourceentity.exists(typeProjector.inheritsSimpleEntity)
        MComponent.EntityRuntimeDescriptor(
          entityName = StringUtils.makeTitle(entity.name),
          packageName = _entity_runtime_package_name(entity),
          usageKind = entity.usageKind,
          operationKind = entity.operationKind,
          applicationDomain = entity.applicationDomain,
          viewNames = _view_names(entity),
          revisionModelKind = sourceentity.map(_ =>
            if (issimpleentity) "simple-entity" else "non-simple-entity"
          ),
          revisionRepresentation =
            if (issimpleentity) Some("embedded") else None
        )
      }

    private def _entity_runtime_package_name(entity: MEntity): String =
      entity.packageName match {
        case "" => "entity"
        case x if x.endsWith(".entity") => x
        case x => s"${x}.entity"
      }
    private def _event_reception_definitions(
      entities: Vector[MEntity]
    ): Vector[MComponent.EventReceptionDefinition] = {
      val a = entities.flatMap(_entity_event_reception_definitions) ++ _global_event_reception_definitions()
      a.foldLeft(Vector.empty[MComponent.EventReceptionDefinition]) { (z, x) =>
        if (z.contains(x)) z else z :+ x
      }
    }

    private def _global_event_reception_definitions(
    ): Vector[MComponent.EventReceptionDefinition] =
      event.receptionDefinitions.map { e =>
        MComponent.EventReceptionDefinition(
          name = e.name,
          category = e.category,
          kind = e.kind,
          selectors = e.selectors,
          actionName = e.actionName,
          priority = e.priority
        )
      }

    private def _entity_event_reception_definitions(
      p: MEntity
    ): Vector[MComponent.EventReceptionDefinition] =
      entity.get(p.name).toVector.flatMap { klass =>
        klass.schemaClass.events.map { e =>
          MComponent.EventReceptionDefinition(
            name = e.name,
            category = e.category,
            kind = e.kind,
            selectors = e.selectors,
            actionName = e.actionName,
            priority = e.priority
          )
        }
      }

    private def _event_routing_definitions(
    ): Vector[MComponent.EventRoutingDefinition] =
      event.routingDefinitions.map { r =>
        MComponent.EventRoutingDefinition(
          name = r.name,
          when = r.when,
          topic = r.topic,
          service = r.service,
          partition = r.partition
        )
      }

    private def _event_subscription_definitions(
    ): Vector[MComponent.EventSubscriptionDefinition] =
      event.subscriptionDefinitions.flatMap { s =>
        for {
          eventname <- s.eventName
          actionname <- s.actionName
        } yield
          MComponent.EventSubscriptionDefinition(
            name = s.name,
            eventName = eventname,
            route = s.route.getOrElse("Unicast"),
            entityName = s.entityName,
            target = s.target,
            targets = s.targets,
            selector = s.selector,
            actionName = actionname,
            declaredTargetUpperBound = s.declaredTargetUpperBound.getOrElse(1),
            activation = s.activation
          )
      }

    private def _aggregate_definitions(
      entities: Vector[MEntity]
    ): Vector[MComponent.AggregateDefinition] =
      entities.map { entity =>
        val entityname = _package_token(entity.name)
        val name = _aggregate_name(entity).flatMap(_token_opt) match {
          case Some(x) => s"${x}_$entityname"
          case None => entityname
        }
        val aggregate = context.entity.classes.get(entity.name).flatMap(_.schemaClass.aggregate)
        val members = _aggregate_member_definitions(entity, entities, aggregate)
        MComponent.AggregateDefinition(
          name = name,
          entityName = entityname,
          members = members,
          creates = aggregate.toVector.flatMap(_.creates).map { c =>
            MComponent.AggregateCreateDefinition(
              name = c.name,
              input = c.input,
              validations = c.validations,
              events = c.events,
              initialState = c.initialState,
              implementation = c.implementation
            )
          },
          commands = aggregate.toVector.flatMap(_.commands).map { c =>
            MComponent.AggregateCommandDefinition(
              name = c.name,
              input = c.input,
              validations = c.validations,
              events = c.events,
              newState = c.newState,
              implementation = c.implementation
            )
          },
          state = aggregate.toVector.flatMap(_.state).map { s =>
            MComponent.AggregateStateDefinition(
              name = s.name,
              datatype = s.datatype,
              multiplicity = s.multiplicity
            )
          },
          invariants = aggregate.toVector.flatMap(_.invariants).map { i =>
            MComponent.AggregateInvariantDefinition(
              name = i.name,
              expression = i.expression
            )
          }
        )
      }

    private def _aggregate_member_definitions(
      root: MEntity,
      entities: Vector[MEntity],
      aggregate: Option[SchemaModel.AggregateDefinition]
    ): Vector[MComponent.AggregateMemberDefinition] = {
      val explicit = aggregate.toVector.flatMap(_.members).flatMap { member =>
        val entityname = _package_token(member.entity)
        if (entityname.nonEmpty)
          Some(MComponent.AggregateMemberDefinition(
            name = _package_token(member.name),
            entityName = entityname,
            kind = Some(member.kind),
            boundary = Some(member.boundary),
            join = member.join,
            joinFieldName = member.joinField,
            multiplicity = member.multiplicity
          ))
        else
          None
      }
      if (explicit.nonEmpty)
        explicit
      else {
        val joinfieldname = s"${root.name.head.toLower}${root.name.drop(1)}Id"
        entities.filterNot(_ == root).flatMap { entity =>
          entity.attributes.find(_.name.equalsIgnoreCase(joinfieldname)).map { attr =>
            MComponent.AggregateMemberDefinition(
              name = _package_token(entity.name),
              entityName = _package_token(entity.name),
              kind = Some("composition"),
              boundary = Some("internal"),
              join = Some("reverse"),
              joinFieldName = Some(attr.name),
              multiplicity = Some("*")
            )
          }
        }
      }
    }

    private def _view_definitions(
      entities: Vector[MEntity]
    ): Vector[MComponent.ViewDefinition] =
      entities.map { entity =>
        val entityname = _package_token(entity.name)
        val name = _view_name(entity).flatMap(_token_opt) match {
          case Some(x) => s"${x}_$entityname"
          case None => entityname
        }
        MComponent.ViewDefinition(
          name = name,
          entityName = entityname,
          viewNames = _view_names(entity),
          viewFields = _view_fields(entity),
          queries = _view_queries(entity),
          sourceEvents = _view_source_events(entity),
          rebuildable = _view_rebuildable(entity)
        )
      }

    private def _operation_definitions(
    ): Vector[MComponent.OperationDefinition] =
      serviceOperation.normalizedServiceOperationDefinitions.
        sortBy(_.name).
        map { x =>
        MComponent.OperationDefinition(
          name = x.name,
          kind = x.kind.toString.toUpperCase,
          summary = x.summary,
          execution = x.execution,
          implementation = x.implementation,
          entityName = x.entityName,
          entityNames = x.entityNames,
          inputType = x.inputType,
          inputSummary = x.inputSummary,
          inputDescription = x.inputDescription,
          outputType = x.outputType,
          outputSummary = x.outputSummary,
          outputDescription = x.outputDescription,
          inputValueKind = x.inputValueKind match {
            case OperationModel.InputValueKind.CommandValue => "COMMAND_VALUE"
            case OperationModel.InputValueKind.QueryValue => "QUERY_VALUE"
          },
          visibility = x.visibility,
          access = x.access.map(a =>
            MComponent.OperationAccess(
              policy = a.policy,
              resource = a.resource,
              target = a.target,
              mode = a.mode,
              relation = a.relation,
              operationModel = a.operationModel,
              entityUsage = a.entityUsage,
              entityOperationKind = a.entityOperationKind,
              entityApplicationDomain = a.entityApplicationDomain,
              condition = a.condition
            )
          ),
          operationAuthorization = x.authorization.map(a =>
            MComponent.OperationAuthorization(
              operationModes = a.operationModes,
              allowAnonymous = a.allowAnonymous,
              anonymousOperationModes = a.anonymousOperationModes
            )
          ),
          evaluation = x.evaluation.map { e =>
            MComponent.OperationEvaluation(
              corpus = e.corpus.map { c =>
                MComponent.CorpusOperationEvaluation(
                  capture = c.capture,
                  profile = c.profile,
                  admission = c.admission,
                  outcomes = c.outcomes,
                  sampling = c.sampling,
                  redaction = c.redaction
                )
              },
              experiment = e.experiment.map { x =>
                MComponent.ExperimentOperationEvaluation(
                  eligible = x.eligible,
                  purpose = x.purpose,
                  admission = x.admission,
                  variantProfile = x.variantProfile
                )
              }
            )
          },
          childEntityBindings = operationRelationshipBindings.get(x.name).map(_.childEntityBindings).getOrElse(Vector.empty),
          associationBinding = operationRelationshipBindings.get(x.name).flatMap(_.associationBinding),
          parameters = x.parameters.map { p =>
            _operation_field(p)
          },
          resultFields = _operation_result_fields(x.outputType)
        )
      }

    private def _operation_result_fields(outputtype: String): Vector[MComponent.OperationField] =
      serviceOperation.valueInputFields(outputtype).map(_.map(_result_operation_field)).orElse {
        predefinedResultCatalog.get(outputtype).map(_.resultfields.map { field =>
          MComponent.OperationField(
            name = field.name,
            datatype = field.datatype,
            multiplicity = field.multiplicity
          )
        })
      }.getOrElse(Vector.empty)

    private def _operation_field(
      p: OperationModel.FieldDefinition
    ): MComponent.OperationField =
      MComponent.OperationField(
        name = p.name,
        datatype = p.datatype,
        multiplicity = p.multiplicity,
        label = p.label.orElse(Some(_humanize_field_name(p.name))),
        controlType = p.controlType.orElse(_operation_control_type(p.name, p.datatype)),
        placeholder = p.placeholder,
        help = p.help,
        required = p.required,
        confidentiality = p.confidentiality,
        constraints = p.constraints.map(RConstraint).toList,
        typeConstraints = p.typeConstraints.map(RConstraint).toList
      )

    private def _result_operation_field(
      p: OperationModel.FieldDefinition
    ): MComponent.OperationField =
      MComponent.OperationField(
        name = p.name,
        datatype = p.datatype,
        multiplicity = p.multiplicity,
        label = p.label,
        controlType = p.controlType,
        placeholder = p.placeholder,
        help = p.help,
        required = p.required,
        confidentiality = p.confidentiality,
        constraints = p.constraints.map(RConstraint).toList,
        typeConstraints = p.typeConstraints.map(RConstraint).toList
      )

    private def _entity_operation_definitions(
      entities: Vector[MEntity]
    ): Vector[MComponent.OperationDefinition] =
      entities.flatMap(_entity_operation_definitions).sortBy(_.name)

    private def _entity_operation_definitions(
      entity: MEntity
    ): Vector[MComponent.OperationDefinition] = {
      val title = StringUtils.makeTitle(entity.name)
      val fields = _entity_update_operation_fields(entity)
      Vector(
        _entity_update_operation_definition(s"update$title", entity, fields),
        _entity_update_operation_definition(s"update${title}Record", entity, fields)
      )
    }

    private def _entity_update_operation_definition(
      name: String,
      entity: MEntity,
      fields: Vector[MComponent.OperationField]
    ): MComponent.OperationDefinition =
      MComponent.OperationDefinition(
        name = name,
        kind = "COMMAND",
        entityName = Some(entity.name),
        inputType = entity.name,
        outputType = "unit",
        inputValueKind = "ENTITY_UPDATE",
        parameters = fields
      )

    private def _entity_update_operation_fields(
      entity: MEntity
    ): Vector[MComponent.OperationField] =
      entity.attributes.
        map(_entity_operation_field).
        toVector

    private def _entity_operation_field(
      p: MAttribute
    ): MComponent.OperationField =
      MComponent.OperationField(
        name = p.name,
        datatype = _operation_field_datatype(p.attributeType),
        multiplicity = _entity_update_operation_multiplicity(p),
        label = p.web.label.orElse(p.designation.labelI18N.map(_.c)),
        controlType = p.web.controlType,
        placeholder = p.web.placeholder,
        help = p.web.help,
        required = p.web.required,
        confidentiality = p.confidentiality,
        constraints = p.constraints,
        typeConstraints = p.typeConstraints,
        update = _entity_operation_update_field(p)
      )

    private def _entity_operation_update_field(
      p: MAttribute
    ): Option[MComponent.OperationUpdateField] =
      if (p.name == "id")
        None
      else
        Some(MComponent.OperationUpdateField(
          sourceMultiplicity = p.multiplicity.mark,
          nullAllowed = p.multiplicity == MZeroOne
        ))

    private def _entity_update_operation_multiplicity(
      p: MAttribute
    ): String =
      if (p.name == "id")
        p.multiplicity.mark
      else
        p.multiplicity.mark match {
          case "1" => "?"
          case "+" => "*"
          case mark => mark
        }

    private def _operation_field_datatype(
      p: MAttributeType
    ): String =
      p match {
        case m: MDataType =>
          m.datatype match {
            case org.goldenport.record.v2.XString =>
              Option(m.name).map(_.trim).filter(_.nonEmpty).getOrElse("string")
            case datatype => datatype.name
          }
        case m: MObjectAttributeType => m.ref.objectName
        case m: MObject => m.name
        case m: MObjectRef => m.objectName
        case _ => Option(p.name).map(_.trim).filter(_.nonEmpty).getOrElse("string")
      }

    private val _entity_package = "entity"
    private val _entity_create_package = s"${_entity_package}.create"
    private val _entity_query_package = s"${_entity_package}.query"

    private def _aggregate_package(name: Option[String]): String =
      name.flatMap(_token_opt).fold(s"${_entity_package}.aggregate")(x => s"${_entity_package}.aggregate.$x")

    private def _view_package(name: Option[String]): String =
      name.flatMap(_token_opt).fold(s"${_entity_package}.view")(x => s"${_entity_package}.view.$x")

    // NOTE: Aggregate DSL is not available yet. Keep default package for now.
    // Future: return Some(aggregateName) from model metadata.
    private def _aggregate_name(entity: MEntity): Option[String] = None

    // NOTE: View DSL package scoping is not available yet. Keep default package for now.
    // Named views are exposed through generated viewDefinitions.viewNames.
    private def _view_name(entity: MEntity): Option[String] = None

    private def _view_names(mentity: MEntity): Vector[String] = {
      val source = _source_entity_class(mentity)
      val declared = source.flatMap(_.view).toVector.flatMap(_.viewNames).map(_package_token(_)).filterNot(_.isEmpty)
      val standard =
        if (source.exists(typeProjector.inheritsSimpleEntity))
          Vector("summary", "detail")
        else
          Vector.empty
      (standard ++ declared).distinct
    }

    private def _humanize_field_name(
      name: String
    ): String = {
      val spaced = name.replace('_', ' ').replace('-', ' ').
        replaceAll("([a-z0-9])([A-Z])", "$1 $2").
        trim
      if (spaced.isEmpty)
        name
      else
        spaced.split("\\s+").map(_.capitalize).mkString(" ")
    }

    private def _operation_control_type(
      name: String,
      datatype: String
    ): Option[String] = {
      val n = name.toLowerCase(java.util.Locale.ROOT)
      val t = Option(datatype).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
      if (t == "text" || Vector("body", "content", "description", "comment", "message").exists(n.contains))
        Some("textarea")
      else
        None
    }

    private def _view_fields(mentity: MEntity): Map[String, Vector[String]] = {
      val source = _source_entity_class(mentity)
      val names =
        if (source.exists(typeProjector.inheritsSimpleEntity))
          (_view_names(mentity) :+ "create").distinct
        else
          _view_names(mentity)
      val fields = _entity_value_display_fields(mentity)
      if (names.isEmpty || fields.isEmpty)
        Map.empty
      else
        names.map(name => name -> _entity_value_display_fields(name, fields)).toMap
    }

    private def _entity_value_display_fields(
      view: String,
      fields: Vector[String]
    ): Vector[String] =
      view match {
        case "create" => fields.filterNot(_ == "id")
        case _ => fields
      }

    private def _entity_value_display_fields(
      mentity: MEntity
    ): Vector[String] = {
      val attrs = mentity.attributes.toVector.map(_.name).filterNot(_.isEmpty)
      if (attrs.contains("id"))
        attrs
      else
        "id" +: attrs
    }

    private def _view_queries(mentity: MEntity): Vector[MComponent.ViewQueryDefinition] =
      _source_entity_class(mentity).flatMap(_.view).toVector.flatMap(_.queries).map { q =>
        MComponent.ViewQueryDefinition(
          name = _package_token(q.name),
          expression = q.expression
        )
      }

    private def _view_source_events(mentity: MEntity): Vector[String] =
      _source_entity_class(mentity).flatMap(_.view).toVector.flatMap(_.sourceEvents).map(_package_token(_)).filterNot(_.isEmpty).distinct

    private def _view_rebuildable(mentity: MEntity): Option[Boolean] =
      _source_entity_class(mentity).flatMap(_.view).flatMap(_.rebuildable)

    private def _source_entity_class(mentity: MEntity): Option[EntityClass] =
      entity.classes.values.find { c =>
        c.name == mentity.name || _package_token(c.name) == _package_token(mentity.name)
      }

    private def _token_opt(name: String): Option[String] =
      Option(name).map(_.trim).filter(_.nonEmpty).map(_package_token)

    private def _package_token(name: String): String = {
      val b = new StringBuilder
      name.zipWithIndex.foreach { case (c, i) =>
        if (
          c.isUpper && i > 0 &&
          (name.charAt(i - 1).isLower || (i + 1 < name.length && name.charAt(i + 1).isLower))
        ) {
          b.append('_')
        }
        b.append(c.toLower)
      }
      b.toString
    }

    private def _scala_string_literal(p: String): String =
      "\"" + p.flatMap {
        case '\\' => "\\\\"
        case '"' => "\\\""
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c => c.toString
      } + "\""
    private def _make_entity_service(
      pkg: MPackage,
      entities: Vector[MEntity]
    ): MService = {
      val ops = entities.flatMap(_make_entity_operations)
      MService(pkg, "entity", ops)
    }

    private def _make_entity_operations(entity: MEntity): Vector[MOperation] = {
      val title = StringUtils.makeTitle(entity.name)
      val pkgname = entity.packageName
      def _qualify_(s: String) =
        if (pkgname.isEmpty) s else s"$pkgname.$s"
      val wholeclass = _qualify_(s"${_entity_package}.$title")
      val createclass = _qualify_(s"${_entity_create_package}.$title")
      val queryclass = _qualify_(s"${_entity_query_package}.$title")
      val entityparam = MParameter("entity", MEntityValue.create(entity))
      val updateparam = MParameter("entity", MEntityValue.update(entity))
      val queryparam = MParameter.query("q", MEntityValue.query(entity))
      val queryrecparam = MParameter.query("q", MObjectRef.record)
      val idparam = MParameter.entityId
      val recordparam = MParameter.record
      val createrecordparam = MParameter("record", MEntityValue.create(entity))
      val recordresult = MResult(MObjectRef.record)
      val searchresult = MResult.search(MEntityValue.whole(entity))
      val create = MOperation.commandBody(s"create$title", entityparam) {
        blockFor(
          "r <- entity_create(action.entity)"
        )(
          "OperationResponse(r.toRecord)"
        )
      }
      val createrec = MOperation.commandBody(s"create${title}Record", createrecordparam) {
        blockFor(
          "r <- entity_create(action.record)"
        )(
          "OperationResponse(r.toRecord)"
        )
      }
      val load = MOperation.queryBody(s"load$title", idparam, recordresult) {
        blockFor(
          s"entity <- entity_load[$wholeclass](action.id)"
        )(
          "OperationResponse(entity.toRecord())"
        )
      }
      val loadrec = MOperation.queryBody(s"load${title}Record", idparam, recordresult) {
        blockFor(
          s"entity <- entity_load[$wholeclass](action.id)"
        )(
          "OperationResponse(entity.toRecord())"
        )
      }
      val save = MOperation.commandBody(s"save$title", List(entityparam), recordresult) {
        blockFor(
          s"entity <- exec_pure($wholeclass.create(action.entity.toRecord()))",
          "saved <- entity_save_managed(entity)"
        )(
          "OperationResponse(saved.toRecord())"
        )
      }
      val saverec = MOperation.commandBody(s"save${title}Record", List(entityparam), recordresult) {
        blockFor(
          s"entity <- exec_pure($wholeclass.create(action.entity.toRecord()))",
          "saved <- entity_save_managed(entity)"
        )(
          "OperationResponse(saved.toRecord())"
        )
      }
      val update = MOperation.commandBody(s"update$title", List(updateparam), recordresult) {
        blockFor(
          """id <- exec_pure(Consequence.successOrRecordNotFound[EntityId]("id", action.request.toRecord).TAKE)""",
          "record <- entity_update(id, action.entity)"
        )(
          "OperationResponse(record)"
        )
      }
      val updaterec = MOperation.commandBody(s"update${title}Record", List(updateparam), recordresult) {
        blockFor(
          """id <- exec_pure(Consequence.successOrRecordNotFound[EntityId]("id", action.request.toRecord).TAKE)""",
          "record <- entity_update(id, action.entity)"
        )(
          "OperationResponse(record)"
        )
      }
      val delete = MOperation.commandBody(s"delete$title", idparam) {
        blockFor(
          "_ <- entity_delete(action.id)"
        )(
          "OperationResponse.void"
        )
      }
      val deletehard = MOperation.commandBody(s"delete${title}Hard", idparam) {
        blockFor(
          "_ <- entity_delete_hard(action.id)"
        )(
          "OperationResponse.void"
        )
      }
      val search = MOperation.queryBody(s"search$title", queryparam, searchresult) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"r <- entity_search[$wholeclass]($queryclass.collectionId, fields.rewrite(Query.withControls(Query(action.q), action.request.toRecord)))"
        )(
          "OperationResponse.create(r)"
        )
      }
      val searchrec = MOperation.queryBody(s"search${title}Record", queryrecparam, searchresult) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"r <- entity_search[$wholeclass]($queryclass.collectionId, fields.rewrite(Query.withControls(action.q, action.request.toRecord)))"
        )(
          "OperationResponse.create(r)"
        )
      }
      Vector(
        create,
        createrec,
        load,
        loadrec,
        save,
        saverec,
        update,
        updaterec,
        delete,
        deletehard,
        search,
        searchrec
      )
    }

    private def _make_aggregate_service(
      pkg: MPackage,
      entities: Vector[MEntity]
    ): MService = {
      val ops = entities.flatMap(_make_aggregate_operations)
      MService(pkg, "aggregate", ops)
    }

    private def _make_aggregate_operations(entity: MEntity): Vector[MOperation] = {
      val title = StringUtils.makeTitle(entity.name)
      val pkgname = entity.packageName
      def _qualify_(s: String) =
        if (pkgname.isEmpty) s else s"$pkgname.$s"
      // NOTE: Aggregate-specific DSL/model is not available yet.
      // Default is aggregate.<Entity>. Non-default is aggregate.<aggregate-name>.<Entity>.
      val aggregateclass = _qualify_(s"${_aggregate_package(_aggregate_name(entity))}.$title")
      val wholeclass = _qualify_(s"${_entity_package}.$title")
      val queryclass = _qualify_(s"${_entity_query_package}.$title")
      val entityname = _package_token(entity.name)
      val createparam = MParameter("entity", MEntityValue.create(entity))
      val saveparam = MParameter("entity", MEntityValue.save(entity))
      val updateparam = MParameter("entity", MEntityValue.update(entity))
      val searchparam = MParameter.query("q", MEntityValue.query(entity))
      val idparam = MParameter.entityId
      val loadresult = MResult.option(MEntityValue.aggregate(entity))
      val searchresult = MResult.search(MEntityValue.aggregate(entity))
      val aggregate = context.entity.classes.get(entity.name).flatMap(_.schemaClass.aggregate)
      val createmethod = s"create$title"
      val updatemethod = s"update$title"
      val hascreatemethod = aggregate.exists(_.creates.exists(_.name == createmethod))
      val hasupdatemethod = aggregate.exists(_.commands.exists(_.name == updatemethod))
      val create = MOperation.commandBody(s"create$title", createparam) {
        if (hascreatemethod)
          blockFor(
            s"r <- aggregate_create(${_scala_string_literal(entityname)}, ${_scala_string_literal(createmethod)}, $aggregateclass.$createmethod(action.entity.toRecord())(using executionContext))"
          )(
            "OperationResponse.create(r.toRecord())"
          )
        else
          blockFor(
            "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
          )(
            "OperationResponse.void"
          )
      }
      val load = MOperation.queryBody(s"load$title", idparam, loadresult) {
        blockFor(
          s"r <- aggregate_load_option[$aggregateclass](action.id)"
        )(
          "OperationResponse.create(r.map(_.toRecord()))"
        )
      }
      val save = MOperation.commandBody(s"save$title", saveparam) {
        blockFor(
          "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
        )(
          "OperationResponse.void"
        )
      }
      val update = MOperation.commandBody(s"update$title", updateparam) {
        if (hasupdatemethod)
          blockFor(
            """id <- exec_from(Consequence.successOrRecordNotFound[EntityId]("id", action.request.toRecord))""",
            s"current <- aggregate_load[$aggregateclass](id)",
            s"r <- aggregate_update(${_scala_string_literal(entityname)}, id, ${_scala_string_literal(updatemethod)}, current.$updatemethod(action.entity.toRecord())(using executionContext))"
          )(
            "OperationResponse.create(r.toRecord())"
          )
        else
          blockFor(
            "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
          )(
            "OperationResponse.void"
          )
      }
      val delete = MOperation.commandBody(s"delete$title", idparam) {
        blockFor(
          "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
        )(
          "OperationResponse.void"
        )
      }
      val search = MOperation.queryBody(s"search$title", searchparam, searchresult) {
        blockFor(
          s"r <- aggregate_search[$aggregateclass]($queryclass.collectionId.name, Query.withControls(Query(action.q), action.request.toRecord))"
        )(
          "OperationResponse.create(r)"
        )
      }
      Vector(
        create,
        load,
        save,
        update,
        delete,
        search
      )
    }

    private def _make_view_service(
      pkg: MPackage,
      entities: Vector[MEntity]
    ): MService = {
      val ops = entities.flatMap(_make_view_operations)
      MService(pkg, "view", ops)
    }

    private def _make_view_operations(entity: MEntity): Vector[MOperation] = {
      val title = StringUtils.makeTitle(entity.name)
      val pkgname = entity.packageName
      def _qualify_(s: String) =
        if (pkgname.isEmpty) s else s"$pkgname.$s"
      val queryclass = _qualify_(s"${_entity_query_package}.$title")
      val searchparam = MParameter.query("q", MEntityValue.query(entity))
      val viewparam = MParameter(
        Description.name("view"),
        MParameter.MDataTypeParameterType(MDataType.string),
        MZeroOne
      )
      val searchrecparam = MParameter.query("q", MObjectRef.record)
      val idparam = MParameter.entityId
      // NOTE: View-specific DSL/model is not available yet.
      // Default is view.<Entity>. Non-default is view.<view-name>.<Entity>.
      val viewvalue = MEntityValue.view(entity)
      val viewclass = _qualify_(s"${_view_package(_view_name(entity))}.$title")
      val loadresult = MResult.option(viewvalue)
      val searchresult = MResult.search(viewvalue)
      val load = MOperation.queryBody(s"load$title", idparam, loadresult) {
        blockFor(
          s"r <- view_load[$viewclass]($queryclass.collectionId.name, action.id)"
        )(
          "OperationResponse(r.toViewRecord(using core.executionContext))"
        )
      }
      val loadbyview = MOperation.queryBody(s"load${title}ByView", idparam, loadresult) {
        blockFor(
          s"""r <- view_load[$viewclass]($queryclass.collectionId.name, action_required_property_string("view").TAKE, action.id)"""
        )(
          "OperationResponse(r.toViewRecord(using core.executionContext))"
        )
      }
      val search = MOperation.queryBody(s"search$title", List(searchparam, viewparam), searchresult) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"r <- action.view.fold(view_search[$viewclass]($queryclass.collectionId.name, fields.rewrite(Query.withControls(Query(action.q), action.request.toRecord))))(viewname => view_search[$viewclass]($queryclass.collectionId.name, viewname, fields.rewrite(Query.withControls(Query(action.q), action.request.toRecord))))"
        )(
          "OperationResponse.create(org.goldenport.cncf.directive.SearchResult(query = r.query, data = r.data.map(_.toViewRecord(using core.executionContext)), totalCount = r.totalCount, offset = r.offset, limit = r.limit, fetchedCount = r.fetchedCount))"
        )
      }
      val searchrec = MOperation.queryBody(s"search${title}Record", searchrecparam, searchresult) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"""r <- action_property_string("view").fold(view_search[$viewclass]($queryclass.collectionId.name, fields.rewrite(Query.withControls(action.q, action.request.toRecord))))(viewname => view_search[$viewclass]($queryclass.collectionId.name, viewname, fields.rewrite(Query.withControls(action.q, action.request.toRecord))))"""
        )(
          "OperationResponse.create(org.goldenport.cncf.directive.SearchResult(query = r.query, data = r.data.map(_.toViewRecord(using core.executionContext)), totalCount = r.totalCount, offset = r.offset, limit = r.limit, fetchedCount = r.fetchedCount))"
        )
      }
      val named = _view_names(entity).flatMap { viewname =>
        _token_opt(viewname).toVector.flatMap { token =>
          val projectiontitle = StringUtils.makeTitle(token)
          val projectionvalue = MEntityValue.projection(entity, Some(viewname))
          val projectionclass = _qualify_(s"${_view_package(Some(viewname))}.$title")
          val projectionloadresult = MResult.option(projectionvalue)
          val projectionsearchresult = MResult.search(projectionvalue)
          val loadprojection = MOperation.queryBody(s"load${title}${projectiontitle}", idparam, projectionloadresult) {
            blockFor(
              s"""r <- view_load[$projectionclass]($queryclass.collectionId.name, "${viewname}", action.id)"""
            )(
              "OperationResponse(r.toViewRecord(using core.executionContext))"
            )
          }
          val searchprojection = MOperation.queryBody(s"search${title}${projectiontitle}", searchrecparam, projectionsearchresult) {
            blockFor(
              s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
              s"""r <- view_search[$projectionclass]($queryclass.collectionId.name, "${viewname}", fields.rewrite(Query.withControls(action.q, action.request.toRecord)))"""
            )(
              "OperationResponse.create(org.goldenport.cncf.directive.SearchResult(query = r.query, data = r.data.map(_.toViewRecord(using core.executionContext)), totalCount = r.totalCount, offset = r.offset, limit = r.limit, fetchedCount = r.fetchedCount))"
            )
          }
          val searchprojectionrecord = MOperation.queryBody(s"search${title}${projectiontitle}Record", searchrecparam, projectionsearchresult) {
            blockFor(
              s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
              s"""r <- view_search[$projectionclass]($queryclass.collectionId.name, "${viewname}", fields.rewrite(Query.withControls(action.q, action.request.toRecord)))"""
            )(
              "OperationResponse.create(org.goldenport.cncf.directive.SearchResult(query = r.query, data = r.data.map(_.toViewRecord(using core.executionContext)), totalCount = r.totalCount, offset = r.offset, limit = r.limit, fetchedCount = r.fetchedCount))"
            )
          }
          Vector(
            loadprojection,
            searchprojection,
            searchprojectionrecord
          )
        }
      }
      Vector(
        load,
        loadbyview,
        search,
        searchrec
      ) ++ named
    }
}
