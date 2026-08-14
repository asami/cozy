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
private[modeler] final class ModelServiceOperationProjector(
  val context: ModelBuildContext,
  val typeProjector: ModelTypeProjector
) {
  import context._
  import Modeler._
    private def _with_operation_field_type_constraints(
      p: OperationModel.FieldDefinition
    ): OperationModel.FieldDefinition = {
      val inherited = typeProjector.operationTypeConstraints(p.datatype)
      val existingkeys = p.typeConstraints.map(typeProjector.constraintKey).toSet
      p.copy(typeConstraints = p.typeConstraints ++ inherited.filterNot(x => existingkeys.contains(typeProjector.constraintKey(x))))
    }
    private[modeler] def normalizedServiceOperationDefinitions: Vector[OperationModel.NormalizedOperationDefinition] =
      _normalized_service_operation_definitions
    private[modeler] def valueInputFields(name: String): Option[Vector[OperationModel.FieldDefinition]] =
      _value_input_field_map.get(name)
    private[modeler] def definedServices(pkg: MPackage, entities: Vector[MEntity]): Vector[MService] =
      _defined_services(pkg, entities)
    private def _defined_services(
      pkg: MPackage,
      entities: Vector[MEntity]
    ): Vector[MService] =
      service.classes.values.toVector.map(_defined_service(pkg, entities, _))

    private def _defined_service(
      pkg: MPackage,
      entities: Vector[MEntity],
      p: ServiceModel.ServiceClass
    ): MService = {
      val ops = p.operations.operations.values.toVector.map(_defined_service_operation(entities, p.name, p, _))
      MService(
        pkg,
        p.name,
        ops,
        _description(p.name, _service_description(p)),
        p.useCases.map(_component_use_case_definition_for_service)
      )
    }

    private def _service_description(
      p: ServiceModel.ServiceClass
    ): Option[String] =
      _append_use_cases(p.description, p.useCases.map(_service_use_case_text))

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

    private def _service_use_case_text(
      p: ServiceModel.ServiceClass.UseCaseDefinition
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

    private lazy val _legacy_input_value_definition_map: Map[String, OperationModel.InputValueDefinition] =
      operation.values.map { value =>
        value.name -> value.copy(fields = value.fields.map(_with_operation_field_type_constraints))
      }.toMap

    private lazy val _value_input_field_map: Map[String, Vector[OperationModel.FieldDefinition]] =
      (value.classes.values.toVector ++ typeProjector.serviceInlineValues).map(x => x.name -> _operation_fields(x)).toMap

    private lazy val _service_operation_type_names: Set[String] =
      _legacy_input_value_definition_map.keySet ++
        value.classes.keySet ++
        typeProjector.serviceInlineValues.map(_.name).toSet ++
        entity.classes.keySet ++
        datatype.classes.keySet ++
        cmlDeclaredTypeNames ++
        _builtin_service_operation_input_type_names ++
        predefinedResultCatalog.names

    private lazy val _builtin_service_operation_input_type_names: Set[String] =
      Set(
        "CommandAction",
        "QueryAction"
      )

    private def _operation_fields(p: ValueClass): Vector[OperationModel.FieldDefinition] =
      p.schemaClass.slots.flatMap {
        case m: SchemaModel.Attribute => Some(_operation_field(m))
        case m: SchemaModel.Id => Some(_operation_field(m))
        case _ => None
      }

    private def _operation_field(p: SchemaModel.Attribute): OperationModel.FieldDefinition =
      OperationModel.FieldDefinition(
        name = p.name,
        datatype = p.rawTypeName.getOrElse(p.domain.datatype.name),
        multiplicity = p.domain.multiplicity.mark,
        label = p.web.label,
        controlType = p.web.controlType,
        placeholder = p.web.placeholder,
        help = p.web.help,
        required = p.web.required,
        confidentiality = p.confidentiality,
        constraints = p.domain.constraints.toVector,
        typeConstraints = typeProjector.attributeTypeConstraints(p).toVector
      )

    private def _operation_field(p: SchemaModel.Id): OperationModel.FieldDefinition =
      OperationModel.FieldDefinition(
        name = p.name,
        datatype = p.domain.datatype.name,
        multiplicity = p.domain.multiplicity.mark,
        constraints = p.domain.constraints.toVector
      )

    private lazy val _normalized_operation_map: Map[String, OperationModel.NormalizedOperationDefinition] =
      operation.normalizedOperations.map(_with_input_value_parameters).map(x => x.name -> x).toMap

    private def _with_input_value_parameters(
      p: OperationModel.NormalizedOperationDefinition
    ): OperationModel.NormalizedOperationDefinition =
      if (p.parameters.nonEmpty)
        p
      else
        _legacy_input_value_definition_map.get(p.inputType).
          map(v => p.copy(parameters = v.fields)).
          getOrElse(p)

    private def _defined_service_operation(
      entities: Vector[MEntity],
      servicename: String,
      serviceclass: ServiceModel.ServiceClass,
      p: ServiceModel.ServiceClass.Operation
    ): MOperation = {
      val opname = p.name
      val opdef = _normalized_service_operation(p).map(_merge_service_operation_metadata(_, serviceclass, p))
      val desc = _description(
        opname,
        _operation_description(
          p.description.orElse(opdef.flatMap(_.description)),
          p.precondition.orElse(opdef.flatMap(_.precondition)),
          p.postcondition.orElse(opdef.flatMap(_.postcondition)),
          if (p.rules.nonEmpty) p.rules else opdef.map(_.rules).getOrElse(Vector.empty)
        )
      )
      val implementation = opdef.flatMap(_.implementation).map(_.trim.toLowerCase)
      val entity = _entity_for_service(entities, servicename)
      val access = opdef.flatMap(_.access).map(a =>
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
      )
      opdef.map(_.kind) match {
        case Some(OperationModel.OperationKind.Query) =>
          _defined_query_service_operation(opname, desc, implementation, entity, access)
        case _ =>
          _defined_command_service_operation(opname, desc, implementation, entity, opdef.flatMap(_.execution), access)
      }
    }

    private def _merge_service_operation_metadata(
      lhs: OperationModel.NormalizedOperationDefinition,
      serviceclass: ServiceModel.ServiceClass,
      rhs: ServiceModel.ServiceClass.Operation
    ): OperationModel.NormalizedOperationDefinition =
      lhs.copy(
        summary = rhs.summary.orElse(lhs.summary),
        entityName = rhs.entityName.orElse(serviceclass.entityName).orElse(lhs.entityName),
        entityNames =
          if (rhs.entityNames.nonEmpty) rhs.entityNames
          else if (serviceclass.entityNames.nonEmpty) serviceclass.entityNames
          else lhs.entityNames,
        description = rhs.description.orElse(lhs.description),
        precondition = rhs.precondition.orElse(lhs.precondition),
        postcondition = rhs.postcondition.orElse(lhs.postcondition),
        execution = rhs.execution.orElse(lhs.execution),
        implementation = rhs.implementation.orElse(lhs.implementation),
        visibility = rhs.visibility.orElse(lhs.visibility),
        access = rhs.access.orElse(serviceclass.access).orElse(lhs.access),
        authorization = rhs.authorization.orElse(lhs.authorization),
        evaluation = rhs.evaluation.orElse(lhs.evaluation),
        rules = if (rhs.rules.nonEmpty) rhs.rules else lhs.rules,
        parameters = if (rhs.parameters.nonEmpty) rhs.parameters else lhs.parameters
      )

    private def _normalized_service_operation(
      p: ServiceModel.ServiceClass.Operation
    ): Option[OperationModel.NormalizedOperationDefinition] = {
      val leafcontract = serviceOperationLeafContracts.get(p.name)
      if (!_has_service_operation_contract(p, leafcontract)) {
        None
      } else {
        val kind = _merge_service_operation_kind(p.name, p.kind, leafcontract.flatMap(_.kind)).getOrElse(
          RAISE.syntaxErrorFault(s"Operation '${p.name}' requires TYPE (COMMAND|QUERY).")
        )
        val inputtype = _merge_service_operation_type(p.name, "INPUT", p.input.tpe, leafcontract.flatMap(_.inputType)).
          map(_.trim).filterNot(_.isEmpty).map(_canonical_service_operation_type(p.name, _)).getOrElse(
          RAISE.syntaxErrorFault(s"Operation '${p.name}' requires INPUT TYPE.")
        )
        val outputtype = _merge_service_operation_type(p.name, "OUTPUT", p.output.tpe, leafcontract.flatMap(_.outputType)).
          map(_.trim).filterNot(_.isEmpty).map(_canonical_service_operation_type(p.name, _)).getOrElse(
          RAISE.syntaxErrorFault(s"Operation '${p.name}' requires OUTPUT TYPE.")
        )
        _validate_service_operation_type_reference(p.name, "INPUT", inputtype)
        _validate_service_operation_type_reference(p.name, "OUTPUT", outputtype)
        val legacyinput = _legacy_input_value_definition_map.get(inputtype)
        val inputvaluekind = p.input.value.
          map(_local_service_operation_input_kind(p.name, kind, _)).
          getOrElse(_resolve_service_operation_input_kind(p.name, inputtype, kind, legacyinput))
        val parameters =
          if (p.parameters.nonEmpty)
            p.parameters
          else
            legacyinput.map(_.fields).orElse(_value_input_field_map.get(inputtype)).getOrElse(Vector.empty)
        _validate_service_operation_input_kind(p.name, kind, inputvaluekind)
        Some(OperationModel.NormalizedOperationDefinition(
          name = p.name,
          kind = kind,
          summary = p.summary.orElse(p.description),
          execution = p.execution,
          implementation = p.implementation,
          entityName = p.entityName,
          entityNames = p.entityNames,
          inputType = inputtype,
          inputSummary = p.input.summary,
          inputDescription = p.input.description,
          outputType = outputtype,
          outputSummary = p.output.summary,
          outputDescription = p.output.description,
          inputValueKind = inputvaluekind,
          description = p.description,
          precondition = p.precondition,
          postcondition = p.postcondition,
          visibility = p.visibility,
          access = p.access,
          authorization = p.authorization,
          evaluation = p.evaluation,
          rules = p.rules,
          parameters = parameters
        ))
      }
    }

    private def _local_service_operation_input_kind(
      operationname: String,
      operationkind: OperationModel.OperationKind,
      valueclass: ValueClass
    ): OperationModel.InputValueKind = {
      valueclass.getProperty("input-kind").foreach { _ =>
        RAISE.syntaxErrorFault(s"Operation '$operationname' local INPUT must not declare input-kind.${_value_location(valueclass)}")
      }
      val resolved = _input_value_kind(operationkind)
      _compatibility_value_input_kind(valueclass).foreach { compatibility =>
        _validate_service_operation_input_kind(operationname, operationkind, compatibility)
      }
      resolved
    }

    private def _resolve_service_operation_input_kind(
      operationname: String,
      inputtype: String,
      operationkind: OperationModel.OperationKind,
      legacyinput: Option[OperationModel.InputValueDefinition]
    ): OperationModel.InputValueKind =
      legacyinput.map(_.kind).
        orElse(value.get(inputtype).map(_top_level_value_input_kind(operationname, _))).
        getOrElse(_input_value_kind(operationkind))

    private def _top_level_value_input_kind(
      operationname: String,
      valueclass: ValueClass
    ): OperationModel.InputValueKind =
      valueclass.getProperty("input-kind") match {
        case Some(value) =>
          OperationModel.InputValueKind.parse(value).getOrElse(
            RAISE.syntaxErrorFault(s"Top-level VALUE '${valueclass.name}' input-kind must be COMMAND or QUERY: $value${_value_location(valueclass)}")
          )
        case None =>
          _compatibility_value_input_kind(valueclass).getOrElse(
            RAISE.syntaxErrorFault(s"Operation '$operationname' INPUT Value '${valueclass.name}' requires input-kind=COMMAND|QUERY.${_value_location(valueclass)}")
          )
      }

    private def _value_location(p: ValueClass): String =
      p.sourceLocation.map(_.show).filterNot(_ == "[]").map(x => s" $x").getOrElse("")

    private def _compatibility_value_input_kind(
      p: ValueClass
    ): Option[OperationModel.InputValueKind] = {
      val propertyparents = p.getProperty("extends").toVector.flatMap(_.split("[,\n]").toVector.map(_.trim).filter(_.nonEmpty))
      val kinds = (p.schemaClass.features.parentsName ++ propertyparents).flatMap {
        case name if name.equalsIgnoreCase("CommandAction") => Some(OperationModel.InputValueKind.CommandValue)
        case name if name.equalsIgnoreCase("QueryAction") => Some(OperationModel.InputValueKind.QueryValue)
        case _ => None
      }.distinct
      kinds match {
        case Nil => None
        case one :: Nil => Some(one)
        case _ => RAISE.syntaxErrorFault(s"VALUE '${p.name}' cannot extend both CommandAction and QueryAction.${_value_location(p)}")
      }
    }

    private def _input_value_kind(
      p: OperationModel.OperationKind
    ): OperationModel.InputValueKind = p match {
      case OperationModel.OperationKind.Command => OperationModel.InputValueKind.CommandValue
      case OperationModel.OperationKind.Query => OperationModel.InputValueKind.QueryValue
    }

    private def _has_service_operation_contract(
      p: ServiceModel.ServiceClass.Operation,
      leafcontract: Option[ServiceOperationLeafContract]
    ): Boolean =
      p.kind.nonEmpty ||
        p.input.tpe.exists(_.trim.nonEmpty) ||
        p.output.tpe.exists(_.trim.nonEmpty) ||
        leafcontract.exists(_.nonEmpty) ||
        p.execution.nonEmpty ||
        p.implementation.nonEmpty ||
        p.entityName.nonEmpty ||
        p.entityNames.nonEmpty ||
        p.access.nonEmpty ||
        p.authorization.nonEmpty ||
        p.parameters.nonEmpty

    private def _merge_service_operation_kind(
      opname: String,
      lhs: Option[OperationModel.OperationKind],
      rhs: Option[OperationModel.OperationKind]
    ): Option[OperationModel.OperationKind] =
      _merge_service_operation_value(opname, "TYPE", lhs, rhs)(_.toString)

    private def _merge_service_operation_type(
      opname: String,
      role: String,
      lhs: Option[String],
      rhs: Option[String]
    ): Option[String] =
      _merge_service_operation_value(opname, role, lhs, rhs)(identity)

    private def _merge_service_operation_value[A](
      opname: String,
      role: String,
      lhs: Option[A],
      rhs: Option[A]
    )(show: A => String): Option[A] =
      (lhs, rhs) match {
        case (Some(l), Some(r)) if show(l) != show(r) =>
          RAISE.syntaxErrorFault(s"Operation '$opname' direct $role '${show(r)}' conflicts with $role section '${show(l)}'.")
        case (Some(l), _) => Some(l)
        case (_, Some(r)) => Some(r)
        case _ => None
      }

    private def _canonical_service_operation_type(
      opname: String,
      tpe: String
    ): String =
      if (_is_void_service_operation_type(tpe)) {
        if (tpe != "void")
          Console.err.println(s"[cozy] warning: Operation '$opname' uses '$tpe'; use canonical 'void'.")
        "void"
      } else {
        tpe
      }

    private def _validate_service_operation_type_reference(
      opname: String,
      role: String,
      tpe: String
    ): Unit =
      if (_is_void_service_operation_type(tpe)) {
        ()
      } else if (role == "OUTPUT" && _raw_service_operation_scalar_type_names.contains(tpe.toLowerCase(java.util.Locale.ROOT))) {
        RAISE.syntaxErrorFault(
          s"Operation '$opname' OUTPUT TYPE '$tpe' is a raw scalar; use a declared Result value or CNCF predefined Result."
        )
      } else if (_service_operation_type_names.contains(tpe)) {
        ()
      } else {
        RAISE.syntaxErrorFault(s"Operation '$opname' $role TYPE '$tpe' is not defined.")
      }

    private def _is_void_service_operation_type(tpe: String): Boolean =
      tpe.equalsIgnoreCase("void")

    private lazy val _raw_service_operation_scalar_type_names: Set[String] =
      Set(
        "boolean",
        "byte",
        "short",
        "int",
        "integer",
        "long",
        "float",
        "double",
        "decimal",
        "string"
      )

    private def _validate_service_operation_input_kind(
      opname: String,
      opkind: OperationModel.OperationKind,
      valuekind: OperationModel.InputValueKind
    ): Unit =
      (opkind, valuekind) match {
        case (OperationModel.OperationKind.Command, OperationModel.InputValueKind.QueryValue) =>
          RAISE.syntaxErrorFault(s"Operation '$opname' TYPE=COMMAND cannot use query-value input.")
        case (OperationModel.OperationKind.Query, OperationModel.InputValueKind.CommandValue) =>
          RAISE.syntaxErrorFault(s"Operation '$opname' TYPE=QUERY cannot use command-value input.")
        case _ =>
          ()
      }

    private def _operation_description(
      base: Option[String],
      precondition: Option[String],
      postcondition: Option[String],
      rules: Vector[String]
    ): Option[String] = {
      val normalizedrules = rules.map(_.trim).filter(_.nonEmpty)
      val chunks = Vector(
        base.map(_.trim).filter(_.nonEmpty),
        precondition.map(x => s"Precondition: ${x.trim}").filter(_.nonEmpty),
        postcondition.map(x => s"Postcondition: ${x.trim}").filter(_.nonEmpty),
        if (normalizedrules.nonEmpty) Some(normalizedrules.mkString("Rules:\n- ", "\n- ", "")) else None
      ).flatten
      if (chunks.isEmpty) None else Some(chunks.mkString("\n\n"))
    }

    private def _defined_command_service_operation(
      opname: String,
      desc: Description,
      implementation: Option[String],
      entity: Option[MEntity],
      execution: Option[String],
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      implementation match {
        case Some("echo-record") =>
          _echo_record_command_operation(opname, desc, access)
        case Some("blocking-task") =>
          _blocking_task_command_operation(opname, desc, access)
        case Some("entity-create") =>
          entity.map(_entity_create_command_operation(opname, desc, _)).getOrElse(_not_implemented_command_operation(opname, desc, access))
        case Some("event-emit") | Some("event-effect-record") =>
          MOperation.commandBody(opname, List(MParameter.record), MResult.unit, desc, access) {
            blockFor(
              """_ <- exec_pure {
                |  val previous = core.component.map(_.loadEventEffect()).getOrElse(org.goldenport.record.Record.empty)
                |  val name = action.record.getString("name").filter(_.nonEmpty).orElse(previous.getString("name")).getOrElse("")
                |  val title = action.record.getString("title").filter(_.nonEmpty).orElse(previous.getString("title")).getOrElse("")
                |  val record = org.goldenport.record.Record.data(
                |    "cncf" -> "event-driven",
                |    "event" -> "item.changed",
                |    "name" -> name,
                |    "title" -> title
                |  )
                |  core.component.foreach(_.recordEventEffect(record))
                |  ()
                |}""".stripMargin
            )(
              "OperationResponse.void"
            )
          }
        case _ =>
          if (execution.exists(_.trim.equalsIgnoreCase("sync")))
            _echo_record_command_operation(opname, desc, access)
          else
            _not_implemented_command_operation(opname, desc, access)
      }

    private def _defined_query_service_operation(
      opname: String,
      desc: Description,
      implementation: Option[String],
      entity: Option[MEntity],
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      implementation match {
        case Some("entity-load") =>
          entity.map(_entity_load_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("entity-search") =>
          entity.map(_entity_search_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("aggregate-load") =>
          entity.map(_aggregate_load_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("aggregate-search") =>
          entity.map(_aggregate_search_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("view-load") =>
          entity.map(_view_load_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("view-search") =>
          entity.map(_view_search_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("event-effect-load") =>
          MOperation.queryBody(opname, List(MParameter.record), MResult.unit, desc, access) {
            blockFor(
              "_ <- exec_pure(())"
            )(
              """OperationResponse(core.component.map(_.loadEventEffect()).getOrElse(org.goldenport.record.Record.empty))"""
            )
          }
        case _ =>
          _not_implemented_query_operation(opname, desc, access)
      }

    private def _not_implemented_command_operation(
      opname: String,
      desc: Description,
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      MOperation.commandBody(opname, List(MParameter.record), MResult.unit, desc, access) {
        blockFor(
          "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
        )(
          "OperationResponse.void"
        )
      }

    private def _not_implemented_query_operation(
      opname: String,
      desc: Description,
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      MOperation.queryBody(opname, List(MParameter.record), MResult.unit, desc, access) {
        blockFor(
          "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
        )(
          "OperationResponse.void"
        )
      }

    private def _echo_record_command_operation(
      opname: String,
      desc: Description,
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      MOperation.commandBody(opname, List(MParameter.record), MResult.unit, desc, access) {
        blockFor(
          "r <- ConsequenceT.fromConsequence[[X] =>> org.goldenport.cncf.Program[org.goldenport.cncf.unitofwork.UnitOfWorkOp, X], org.goldenport.record.Record](Consequence.success(action.request.toRecord))"
        )(
          "OperationResponse.create(r)"
        )
      }

    private def _blocking_task_command_operation(
      opname: String,
      desc: Description,
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      MOperation.commandBody(opname, List(MParameter.record), MResult.unit, desc, access) {
        blockFor(
          "r <- exec_pure({ Thread.sleep(250L); action.request.toRecord })"
        )(
          "OperationResponse.create(r)"
        )
      }

    private def _entity_for_service(
      entities: Vector[MEntity],
      servicename: String
    ): Option[MEntity] =
      entities.find(_.name == servicename).orElse {
        entities.find(x => StringUtils.makeTitle(x.name) == StringUtils.makeTitle(servicename))
      }

    private def _entity_classes(
      entity: MEntity
    ): (String, String, String, String, String) = {
      val title = StringUtils.makeTitle(entity.name)
      val pkgname = entity.packageName
      def _qualify_(s: String) =
        if (pkgname.isEmpty) s else s"$pkgname.$s"
      val wholeclass = _qualify_(s"${_entity_package}.$title")
      val createclass = _qualify_(s"${_entity_create_package}.$title")
      val queryclass = _qualify_(s"${_entity_query_package}.$title")
      val aggregateclass = _qualify_(s"${_aggregate_package(_aggregate_name(entity))}.$title")
      val viewclass = _qualify_(s"${_view_package(_view_name(entity))}.$title")
      (wholeclass, createclass, queryclass, aggregateclass, viewclass)
    }

    private def _entity_create_command_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (_, createclass, _, _, _) = _entity_classes(entity)
      MOperation.commandBody(opname, List(MParameter.record), MResult.unit, desc) {
        blockFor(
          s"entity <- exec_pure($createclass.create(action.request.toRecord))",
          "r <- entity_create(entity)"
        )(
          "OperationResponse(r.toRecord)"
        )
      }
    }

    private def _entity_load_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (wholeclass, _, _, _, _) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          """id <- exec_pure(Consequence.successOrRecordNotFound[org.simplemodeling.model.datatype.EntityId]("id", action.request.toRecord).TAKE)""",
          s"r <- entity_load[$wholeclass](id)"
        )(
          "OperationResponse(r.toRecord())"
        )
      }
    }

    private def _entity_search_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (wholeclass, _, queryclass, _, _) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"r <- entity_search[$wholeclass]($queryclass.collectionId, fields.rewrite(Query.fromRecord(action.request.toRecord)))"
        )(
          "OperationResponse.create(r)"
        )
      }
    }

    private def _aggregate_load_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (_, _, _, aggregateclass, _) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          """id <- exec_pure(Consequence.successOrRecordNotFound[org.simplemodeling.model.datatype.EntityId]("id", action.request.toRecord).TAKE)""",
          s"r <- aggregate_load_option[$aggregateclass](id)"
        )(
          "OperationResponse.create(r.map(_.toRecord()))"
        )
      }
    }

    private def _aggregate_search_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (_, _, queryclass, aggregateclass, _) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          s"r <- aggregate_search[$aggregateclass]($queryclass.collectionId.name, Query.fromRecord(action.request.toRecord))"
        )(
          "OperationResponse.create(r)"
        )
      }
    }

    private def _view_load_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (_, _, queryclass, _, viewclass) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          """id <- exec_pure(Consequence.successOrRecordNotFound[org.simplemodeling.model.datatype.EntityId]("id", action.request.toRecord).TAKE)""",
          s"r <- view_load[$viewclass]($queryclass.collectionId.name, id)"
        )(
          "OperationResponse(r.toRecord())"
        )
      }
    }

    private def _view_search_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (_, _, queryclass, _, viewclass) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"""r <- action_property_string("view").fold(view_search[$viewclass]($queryclass.collectionId.name, fields.rewrite(Query.fromRecord(action.request.toRecord))))(viewname => view_search[$viewclass]($queryclass.collectionId.name, viewname, fields.rewrite(Query.fromRecord(action.request.toRecord))))"""
        )(
          "OperationResponse.create(r)"
        )
      }
    }
    private def _normalized_service_operation_definitions: Vector[OperationModel.NormalizedOperationDefinition] =
      service.classes.values.toVector.
        flatMap { svc =>
          svc.operations.operations.values.toVector.flatMap { p =>
          _normalized_service_operation(p).
            map(_merge_service_operation_metadata(_, svc, p))
          }
        }

    private def _merge_normalized_operation_definition(
      lhs: OperationModel.NormalizedOperationDefinition,
      rhs: OperationModel.NormalizedOperationDefinition
    ): OperationModel.NormalizedOperationDefinition =
      lhs.copy(
        summary = rhs.summary.orElse(lhs.summary),
        execution = rhs.execution.orElse(lhs.execution),
        implementation = rhs.implementation.orElse(lhs.implementation),
        entityName = rhs.entityName.orElse(lhs.entityName),
        entityNames = if (rhs.entityNames.nonEmpty) rhs.entityNames else lhs.entityNames,
        inputType = Option(rhs.inputType).filterNot(_.isEmpty).getOrElse(lhs.inputType),
        inputSummary = rhs.inputSummary.orElse(lhs.inputSummary),
        inputDescription = rhs.inputDescription.orElse(lhs.inputDescription),
        outputType = Option(rhs.outputType).filterNot(_.isEmpty).getOrElse(lhs.outputType),
        outputSummary = rhs.outputSummary.orElse(lhs.outputSummary),
        outputDescription = rhs.outputDescription.orElse(lhs.outputDescription),
        description = rhs.description.orElse(lhs.description),
        precondition = rhs.precondition.orElse(lhs.precondition),
        postcondition = rhs.postcondition.orElse(lhs.postcondition),
        visibility = rhs.visibility.orElse(lhs.visibility),
        access = rhs.access.orElse(lhs.access),
        authorization = rhs.authorization.orElse(lhs.authorization),
        rules = if (rhs.rules.nonEmpty) rhs.rules else lhs.rules,
        parameters = if (rhs.parameters.nonEmpty) rhs.parameters else lhs.parameters
      )

    private def _description(
      name: String,
      text: Option[String]
    ): Description =
      text.map(_.trim).filter(_.nonEmpty).
        map(s => Description.name(name, Dox.text(s))).
        getOrElse(Description.name(name))

    private def _component_use_case_definition_for_service(
      p: ServiceModel.ServiceClass.UseCaseDefinition
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
}
