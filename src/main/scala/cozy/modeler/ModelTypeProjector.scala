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
private[modeler] final class ModelTypeProjector(val context: ModelBuildContext) {
  import context._
  import Modeler._
  private[modeler] def projectEntity(p: EntityClass): MEntity = _entity(p)
  private[modeler] def projectValue(p: ValueClass): MValue = _value(p)
  private[modeler] def projectDatatype(p: DataTypeClass): MElement = _datatype(p)
  private[modeler] def projectPowertype(p: PowertypeClass): MPowertype = _powertype(p)
  private[modeler] def isSimpleEntity(name: String): Boolean = _is_simple_entity(name)
  private[modeler] def serviceInlineValues: Vector[ValueClass] = _service_inline_values
    private def _entity(p: EntityClass): MEntity = {
      val packagename = _entity_package_name(p.packageName)
      val desc = Description.name(p.name)
      val affiliation = MPackageRef(packagename)
      val stereotypes = Nil
      val base = p.parents.headOption.map(_object_ref).orElse {
        p.schemaClass.features.parentsName.headOption.map(_object_ref)
      }
      val traits = _delegate_traits(p)
      val powertypes = _powertypes(affiliation, p.schemaClass)
      val attributes = _merge_attributes(
        _simple_entity_attributes(affiliation, p),
        _delegate_attributes(affiliation, p) ++ _attributes(affiliation, p.schemaClass)
      )
      val associations = _merge_associations(
        _associations(affiliation, p.schemaClass),
        _aggregate_member_associations(affiliation, p.schemaClass.aggregate)
      )
      val operations = _aggregate_methods(p)
      val statemachines = _state_machines(affiliation, p)
      MDomainResource(
        desc,
        affiliation,
        stereotypes,
        base,
        traits,
        powertypes,
        attributes,
        associations,
        operations,
        statemachines,
        usageKind = p.usageKind,
        operationKind = p.operationKind,
        applicationDomain = p.applicationDomain
      )
    }

    private def _aggregate_methods(
      p: EntityClass
    ): List[MOperation] =
      p.schemaClass.aggregate.toList.flatMap { aggregate =>
        val creates = aggregate.creates.map { create =>
          MOperation.command(create.name, MParameter.record("input"))
        }
        val commands = aggregate.commands.map { command =>
          MOperation.command(command.name, MParameter.record("input"))
        }
        creates ++ commands
      }

    private lazy val _simple_entity_template: Option[EntityClass] =
      entity.classes.values.find(c => _is_simple_entity(c.name))

    private def _simple_entity_attributes(
      pkg: MPackageRef,
      p: EntityClass
    ): List[MAttribute] = {
      val inheritssimpleentity = {
        val byparentref = p.parents.exists {
          case m: EntityClass.ParentRef.Name => _is_simple_entity(m.name)
          case EntityClass.ParentRef.EntityKlass(c) => _is_simple_entity(c.name)
        }
        val byfeatures = p.schemaClass.features.parentsName.exists(_is_simple_entity)
        byparentref || byfeatures
      }
      if (inheritssimpleentity)
        _simple_entity_template.toList.flatMap(t => _attributes(pkg, t.schemaClass))
      else
        Nil
    }

    private def _delegate_attributes(
      pkg: MPackageRef,
      p: EntityClass
    ): List[MAttribute] = {
      p.schemaClass.features.delegates.toList.flatMap { d =>
        val name = Option(d.name).map(_.trim).getOrElse("")
        if (name.isEmpty) Nil
        else {
          val isoptional = Option(d.multiplicity).map(_.trim).contains("?")
          _delegate_value_attribute(pkg, name, isoptional).toList
        }
      }
    }

    private def _delegate_traits(
      p: EntityClass
    ): List[MTraitRef] =
      p.schemaClass.features.delegates.toList.flatMap { d =>
        val simple = Option(d.name).map(_.trim).getOrElse("")
        if (simple.isEmpty || value.classes.get(simple).isDefined)
          None
        else
          Some(MTraitRef(MPackageRef("org.simplemodeling.model.value"), s"${simple}Holder"))
      }

    private def _delegate_value_attribute(
      pkg: MPackageRef,
      delegatename: String,
      isoptional: Boolean
    ): Option[MAttribute] = {
      val attrname = delegatename.head.toLower + delegatename.drop(1)
      val multiplicity = if (isoptional) MZeroOne else MOne
      _resolve_object_attribute_type(pkg, delegatename).map { atype =>
        MAttribute(
          Designation.nameLabel(attrname, None),
          atype,
          multiplicity,
          Nil,
          None,
          readonly = false,
          derived = None,
          description = Description.empty
        )
      }
    }

    private def _merge_attributes(
      inherited: List[MAttribute],
      own: List[MAttribute]
    ): List[MAttribute] = {
      val z = mutable.LinkedHashMap.empty[String, MAttribute]
      inherited.foreach(x => z.update(x.name, x))
      own.foreach(x => z.update(x.name, x))
      z.values.toList
    }

    private def _merge_associations(
      base: List[MAssociation],
      own: List[MAssociation]
    ): List[MAssociation] = {
      val z = mutable.LinkedHashMap.empty[String, MAssociation]
      base.foreach(x => z.update(x.designation.name, x))
      own.foreach(x => z.update(x.designation.name, x))
      z.values.toList
    }

    private def _state_machines(
      pkg: MPackageRef,
      p: EntityClass
    ): List[MStateMachineRef] =
      p.stateMachines.toList.map(_state_machine_ref(pkg, _))

    private def _state_machine_ref(
      pkg: MPackageRef,
      p: StateMachineClass
    ): MStateMachineRef =
      MStateMachineRef(pkg, p.name)

    private def _object_ref(p: EntityClass.ParentRef): MObjectRef =
      p match {
        case m: EntityClass.ParentRef.Name => _object_ref(m.name)
        case EntityClass.ParentRef.EntityKlass(c) => _object_ref(c.packageName, c.name)
      }

    private def _object_ref(p: String): MObjectRef = {
      val issimpleentity = _is_simple_entity(p)
      if (issimpleentity)
        MObjectRef.create("org.simplemodeling.model.SimpleEntity")
      else
        MObjectRef.create(p)
    }

    private def _object_ref(packagename: String, name: String): MObjectRef = {
      val issimpleentity = _is_simple_entity(name)
      if (issimpleentity)
        MObjectRef.create("org.simplemodeling.model.SimpleEntity")
      else
        MEntityRef.create(_entity_package_name(packagename), name)
    }

    private def _entity_package_name(
      packagename: String
    ): String =
      _component_package_override(packagename).getOrElse(packagename)

    private def _value_package_name(): String =
      _component_package_override("domain").map(_ + ".value").getOrElse("domain.value")

    private def _value_package_name(p: ValueClass): String =
      p.packageName.getOrElse(_value_package_name())

    private def _datatype_package_name(
      packagename: String
    ): String =
      if (packagename == "domain")
        _component_package_override(packagename).map(_ + ".datatype").getOrElse("domain.datatype")
      else
        _component_package_override(packagename).getOrElse(packagename)

    private def _component_package_override(
      componentname: String
    ): Option[String] =
      componentSubsystem.components.find(_.name == componentname).flatMap(_.packageName).orElse {
        if (componentSubsystem.components.lengthCompare(1) == 0)
          componentSubsystem.components.headOption.flatMap(_.packageName)
        else
          None
      }

    private def _inherits_simple_entity(p: EntityClass): Boolean = {
      val byparentref = p.parents.exists {
        case m: EntityClass.ParentRef.Name => _is_simple_entity(m.name)
        case EntityClass.ParentRef.EntityKlass(c) => _is_simple_entity(c.name)
      }
      val byfeatures = p.schemaClass.features.parentsName.exists(_is_simple_entity)
      byparentref || byfeatures
    }

    private def _is_simple_entity(p: String): Boolean =
      p.split("\\.").lastOption.map(_.trim).exists { name =>
        name.equalsIgnoreCase("SimpleEntity") || name.equalsIgnoreCase("simple_entity")
      }

    private def _powertypes(pkg: MPackageRef, p: SchemaClass): List[MPowertypeRef] =
      p.slots.flatMap(_get_powertype(pkg, _)).toList

    private def _get_powertype(pkg: MPackageRef, p: SchemaModel.Slot): Option[MPowertypeRef] =
      p match {
        case m: SchemaModel.Id => None
        case m: SchemaModel.Attribute => None
        case m: SchemaModel.Association => None
        case m: SchemaModel.PowertypeRelationship => Some(_powertype(pkg, m))
        case m: SchemaModel.StateMachineRelationship => None
        case m: SchemaModel.StateMachine => None
      }

    private def _powertype(pkg: MPackageRef, p: SchemaModel.PowertypeRelationship): MPowertypeRef = {
      val name = p.name
      val multiplicity = MMultiplicity(p.multiplicity)
      MPowertypeRef(pkg, name, multiplicity)
    }

    private def _attributes(pkg: MPackageRef, p: SchemaClass): List[MAttribute] =
      p.slots.flatMap(_get_attribute(pkg, _)).toList

    private def _get_attribute(pkg: MPackageRef, p: SchemaModel.Slot): Option[MAttribute] =
      p match {
        case m: SchemaModel.Id => Some(_attribute(m.toColumn))
        case m: SchemaModel.Attribute => Some(_attribute(pkg, m))
        case m: SchemaModel.Association => None
        case m: SchemaModel.PowertypeRelationship => None
        case _: SchemaModel.StateMachineRelationship => None
        case _: SchemaModel.StateMachine => None
      }

    private def _attribute(pkg: MPackageRef, p: SchemaModel.Attribute): MAttribute = {
      val column = p.toColumn
      val designation = Designation.nameLabel(column.name, column.i18nLabel)
      val atype = _resolve_attribute_type(pkg, p)
      val multiplicity = MMultiplicity(column.multiplicity)
      val web = _web(p.web)
      val constraints: List[MConstraint] = p.domain.constraints.map(RConstraint).toList
      val typeconstraints: List[MConstraint] = _attribute_type_constraints(p).map(RConstraint).toList
      val readonly = false
      val description = Description.empty
      MAttribute(designation, atype, multiplicity, constraints, Some(column), readonly, p.derived, web = web, description = description, confidentiality = p.confidentiality, typeConstraints = typeconstraints)
    }

    private def _web(p: SchemaModel.Attribute.Web): MAttribute.Web =
      MAttribute.Web(
        label = p.label,
        controlType = p.controlType,
        placeholder = p.placeholder,
        help = p.help,
        required = p.required,
        hidden = p.hidden,
        readonly = p.readonly
      )

    private def _attribute(p: Column): MAttribute = {
      val designation = Designation.nameLabel(p.name, p.i18nLabel)
      val atype = MDataType(p.datatype)
      val multiplicity = MMultiplicity(p.multiplicity)
      val constraints: List[MConstraint] = p.constraints.map(RConstraint)
      val readonly = false
      val description = Description.empty // p.desc
      MAttribute(designation, atype, multiplicity, constraints, Some(p), readonly, None, description = description)
    }

    private def _resolve_attribute_type(
      pkg: MPackageRef,
      p: SchemaModel.Attribute
    ): MAttributeType = {
      val predefined = p.rawTypeName.
        flatMap(PredefinedScalarCatalog.get).
        flatMap(_.runtimeclassname).
        map(x => MObjectAttributeType(MObjectRef.create(x)))
      predefined.getOrElse(if (p.domain.datatype == org.goldenport.record.v2.XString) {
        p.rawTypeName match {
          case Some(raw) =>
            _builtin_attribute_type(pkg, raw).
              orElse(if (_is_string_format_raw_type(raw)) Some(MDataType(p.domain.datatype)) else None).
              orElse(_resolve_object_attribute_type(pkg, raw)).
              getOrElse(RAISE.syntaxErrorFault(s"Unknown CML attribute type: $raw"))
          case None => MDataType(p.domain.datatype)
        }
      } else
        MDataType(p.domain.datatype))
    }

    private def _attribute_type_constraints(
      p: SchemaModel.Attribute
    ): Vector[org.goldenport.record.v2.Constraint] =
      p.rawTypeName.toVector.flatMap(_operation_type_constraints)

    private def _operation_type_constraints(
      rawtypename: String
    ): Vector[org.goldenport.record.v2.Constraint] = {
      val inherited = _datatype_constraints(rawtypename)
      val predefined = PredefinedScalarCatalog.get(rawtypename).toVector.flatMap(_.constraints)
      val inheritedkeys = inherited.map(_constraint_key).toSet
      inherited ++ predefined.filterNot(x => inheritedkeys.contains(_constraint_key(x)))
    }

    private def _datatype_constraints(
      rawtypename: String
    ): Vector[org.goldenport.record.v2.Constraint] = {
      val simple = rawtypename.split("\\.").last
      datatype.classes.get(simple).toVector.flatMap {
        case m: DataTypeClass.Plain => m.constraints
        case _: DataTypeClass.Complex => Nil
      }
    }

    private def _constraint_key(p: org.goldenport.record.v2.Constraint): String = p match {
      case _: org.goldenport.record.v2.CMinLength => "min-length"
      case _: org.goldenport.record.v2.CMaxLength => "max-length"
      case _: org.goldenport.record.v2.CFormat => "format"
      case _: org.goldenport.record.v2.CRegex => "pattern"
      case _ => p.label
    }

    private def _builtin_attribute_type(
      pkg: MPackageRef,
      p: String
    ): Option[MAttributeType] = {
      val normalized = p.trim.toLowerCase(java.util.Locale.ROOT)
      val datatype: Option[org.goldenport.record.v2.DataType] = normalized match {
        case "record" => None
        case "entityid" => Some(org.goldenport.record.v2.XEntityId)
        case "year" => Some(org.goldenport.record.v2.XYear)
        case "yearmonth" => Some(org.goldenport.record.v2.XYearMonth)
        case "month" => Some(org.goldenport.record.v2.XMonth)
        case "monthday" => Some(org.goldenport.record.v2.XMonthDay)
        case "day" => Some(org.goldenport.record.v2.XDay)
        case "duration" => Some(org.goldenport.record.v2.XDuration)
        case "date-time" => None
        case _ =>
          org.goldenport.record.v2.DataType.get(normalized).filter { datatype =>
            datatype != org.goldenport.record.v2.XString || normalized == "string"
          }
      }
      datatype.map(MDataType(_)).orElse {
        normalized match {
          case "record" => Some(MObjectAttributeType(MObjectRef.record))
          case "blob" => Some(MObjectAttributeType(MObjectRef.create("org.goldenport.bag.BinaryBag")))
          case "clob" => Some(MObjectAttributeType(MObjectRef.create("org.goldenport.bag.TextBag")))
          case "datetime" | "date_time" => Some(MObjectAttributeType(MObjectRef.create("java.time.ZonedDateTime")))
          case "instant" => Some(MObjectAttributeType(MObjectRef.create("java.time.Instant")))
          case "localdate" => Some(MObjectAttributeType(MObjectRef.create("java.time.LocalDate")))
          case "localtime" => Some(MObjectAttributeType(MObjectRef.create("java.time.LocalTime")))
          case "localdatetime" => Some(MObjectAttributeType(MObjectRef.create("java.time.LocalDateTime")))
          case _ => PredefinedScalarCatalog.get(normalized).
            flatMap(_.runtimeclassname).
            map(x => MObjectAttributeType(MObjectRef.create(x)))
        }
      }
    }

    private def _resolve_object_attribute_type(
      pkg: MPackageRef,
      p: String
    ): Option[MAttributeType] = {
      val raw = p.trim
      if (raw.isEmpty)
        None
      else if (!_is_valid_type_name(raw))
        None
      else if (raw.contains("."))
        Some(MObjectAttributeType(MObjectRef.create(raw)))
      else {
        val simple = raw.split("\\.").last
        _resolve_local_object_attribute_type(pkg, simple).
          orElse(Some(MObjectAttributeType(MObjectRef.create(raw))))
      }
    }

    private def _is_valid_type_name(p: String): Boolean =
      p.split("\\.").forall { segment =>
        segment.nonEmpty &&
          segment.headOption.exists(c => c.isLetter || c == '_') &&
          segment.forall(c => c.isLetterOrDigit || c == '_')
      }

    private def _resolve_local_object_attribute_type(
      pkg: MPackageRef,
      simple: String
    ): Option[MAttributeType] =
      value.classes.get(simple).
        map(x => MObjectAttributeType(MObjectRef(MPackageRef(_value_package_name(x)), simple))).
        orElse(powertype.classes.get(simple).map(x => MObjectAttributeType(MObjectRef(MPackageRef(x.packageName), simple)))).
        orElse(datatype.classes.get(simple).map(_datatype_attribute_type)).
        orElse(entity.classes.get(simple).map(x => MObjectAttributeType(_object_ref(x.packageName, simple))))

    private def _datatype_attribute_type(p: DataTypeClass): MAttributeType =
      p match {
        case m: DataTypeClass.Plain =>
          MObjectAttributeType(
            MObjectRef(MPackageRef(_datatype_package_name(m.packageName)), m.name)
          )
        case m: DataTypeClass.Complex =>
          MObjectAttributeType(MObjectRef(MPackageRef(_datatype_package_name(m.packageName)), m.name))
      }

    private def _is_builtin_raw_type(
      p: String
    ): Boolean = {
      val normalized = p.trim.toLowerCase(java.util.Locale.ROOT)
      _builtin_attribute_type(MPackageRef.default, normalized).isDefined ||
        org.goldenport.record.v2.DataType.get(normalized).isDefined ||
        _is_string_format_raw_type(normalized)
    }

    private def _is_string_format_raw_type(p: String): Boolean = {
      val normalized = p.trim.toLowerCase(java.util.Locale.ROOT)
      normalized == "uuid" || PredefinedScalarCatalog.get(normalized).exists(_.format.isDefined)
    }

    private def _associations(pkg: MPackageRef, p: SchemaClass): List[MAssociation] =
      p.slots.flatMap(_get_association(pkg, _)).toList

    private def _aggregate_member_associations(
      pkg: MPackageRef,
      aggregate: Option[SchemaModel.AggregateDefinition]
    ): List[MAssociation] =
      aggregate.toList.flatMap(_.members).map(_aggregate_member_association(pkg, _))

    private def _get_association(pkg: MPackageRef, p: SchemaModel.Slot): Option[MAssociation] =
      p match {
        case m: SchemaModel.Id => None
        case m: SchemaModel.Attribute => None
        case m: SchemaModel.Association => Some(_association(pkg, m))
        case m: SchemaModel.PowertypeRelationship => None
        case m: SchemaModel.StateMachineRelationship => None
        case m: SchemaModel.StateMachine => None
      }

    private def _association(pkg: MPackageRef, p: SchemaModel.Association): MAssociation = {
      val designation = Designation(p.name)
      val description = Description.empty // p.desc
      // val pkg = None
      val objectref: MObjectRef = {
        val pathname = p.objectRef.pathname
        if (pathname.tailOption.isEmpty)
          MObjectRef(pkg, pathname.head)
        else
          MObjectRef.create(pathname.v)
      }
      val kind = p match {
        case p: SchemaModel.Composition => MAssociation.Association
        case p: SchemaModel.Aggregation => MAssociation.Aggregation
        case p: SchemaModel.Association => MAssociation.Composition
      }
      val multiplicity = MMultiplicity(p.multiplicity)
      val collaborations = Nil
      MAssociation(designation, description, Some(pkg), objectref, kind, multiplicity, collaborations)
    }

    private def _aggregate_member_association(
      pkg: MPackageRef,
      p: SchemaModel.AggregateMemberDefinition
    ): MAssociation = {
      val designation = Designation(p.name)
      val description = Description.empty
      val objectref = MObjectRef(pkg, p.entity)
      val kind = p.kind.toLowerCase match {
        case "composition" => MAssociation.Composition
        case "aggregation" => MAssociation.Aggregation
        case _ => MAssociation.Association
      }
      val multiplicity = p.multiplicity.map(x => MMultiplicity.create(x.toString)).getOrElse(MOne)
      val collaborations = Nil
      MAssociation(designation, description, Some(pkg), objectref, kind, multiplicity, collaborations)
    }

    private def _datatype(p: DataTypeClass): MElement = p match {
      case m: DataTypeClass.Plain =>
        val pkg = MPackageRef(_datatype_package_name(m.packageName))
        MNominalDataType(
          description = m.description,
          affiliation = pkg,
          datatype = m.datatype,
          constraints = m.constraints.map(RConstraint)
        )
      case m: DataTypeClass.Complex =>
        val pkg = MPackageRef(_datatype_package_name(m.packageName))
        MStructuredDataType(
          description = m.description,
          affiliation = pkg,
          stereotypes = Nil,
          base = None,
          traits = Nil,
          powertypes = Nil,
          attributes = _datatype_attributes(pkg, m),
          operations = Nil
        )
    }

    private def _datatype_attributes(
      pkg: MPackageRef,
      p: DataTypeClass.Complex
    ): List[MAttribute] =
      p.constitutes.values.toList.map {
        case m: DataTypeClass.Plain =>
          val designation = m.description.designation
          MAttribute(
            designation,
            MDataType(designation, m.datatype, pkg, m.description, resolveDeclaredType = false),
            MOne,
            Nil,
            None,
            false,
            None,
            description = m.description
          )
        case m: DataTypeClass.Complex =>
          RAISE.syntaxErrorFault(s"Nested complex DATATYPE '${m.name}' is not supported yet.")
      }

    private def _value(p: ValueClass): MValue = {
      val desc = Description.name(p.name)
      val pkg = MPackageRef(_value_package_name(p))
      val stereotypes = Nil
      val base = None
      val traits = Nil
      val powertypes = _powertypes(pkg, p.schemaClass)
      val attributes = _attributes(pkg, p.schemaClass)
      val operations = Nil
      MDomainValue(
        desc,
        pkg,
        stereotypes,
        base,
        traits,
        powertypes,
        attributes,
        operations
      )
    }

    private case class LocalValueDefinition(
      servicename: String,
      operationname: String,
      direction: String,
      valueclass: ValueClass
    )

    private lazy val _service_inline_value_definitions: Vector[LocalValueDefinition] =
      service.classes.values.toVector.flatMap { svc =>
        svc.operations.operations.values.toVector.flatMap { op =>
          Vector(
            op.input.value.map(LocalValueDefinition(svc.name, op.name, "INPUT", _)),
            op.output.value.map(LocalValueDefinition(svc.name, op.name, "OUTPUT", _))
          ).flatten
        }
      }

    private lazy val _service_inline_values: Vector[ValueClass] = {
      val definitions = _service_inline_value_definitions
      definitions.groupBy(_.valueclass.name).
        collect { case (name, xs) if xs.size > 1 => name -> xs }.
        toVector.
        sortBy(_._1).
        headOption.
        foreach { case (name, xs) =>
          val owners = xs.map(_local_value_context).mkString("; ")
          RAISE.syntaxErrorFault(s"Operation-local VALUE '$name' is defined more than once: $owners.")
        }
      definitions.filter(x => value.classes.contains(x.valueclass.name)).sortBy(_.valueclass.name).headOption.foreach { definition =>
        val name = definition.valueclass.name
        val top = value.classes.get(name).map(_value_source_context).getOrElse("top-level declaration")
        RAISE.syntaxErrorFault(
          s"Operation-local VALUE '$name' conflicts with a top-level VALUE definition: ${_local_value_context(definition)}; $top."
        )
      }
      definitions.map(_.valueclass)
    }

    private def _local_value_context(p: LocalValueDefinition): String = {
      val component = componentSubsystem.components.map(_.name).distinct match {
        case Vector(name) => s"component '$name', "
        case _ => ""
      }
      s"${component}service '${p.servicename}', operation '${p.operationname}' ${p.direction}${_value_location(p.valueclass)}"
    }

    private def _value_source_context(p: ValueClass): String =
      s"top-level VALUE '${p.name}'${_value_location(p)}"

    private def _value_location(p: ValueClass): String =
      p.sourceLocation.map(_.show).filterNot(_ == "[]").map(x => s" $x").getOrElse("")

    private def _powertype(p: PowertypeClass): MPowertype = {
      val desc = p.description
      val pkg = MPackageRef(p.packageName)
      val kinds = p.kinds.toList.map { x =>
        MPowertypeKind(x.name, x.value.map(_.toString), x.label)
      }
      val stereotypes = Nil
      MPowertype(desc, pkg, kinds, stereotypes)
    }

    private[modeler] def attributeTypeConstraints(p: SchemaModel.Attribute): Vector[org.goldenport.record.v2.Constraint] =
      _attribute_type_constraints(p)
    private[modeler] def operationTypeConstraints(rawtypename: String): Vector[org.goldenport.record.v2.Constraint] =
      _operation_type_constraints(rawtypename)
    private[modeler] def constraintKey(p: org.goldenport.record.v2.Constraint): String =
      _constraint_key(p)
    private[modeler] def inheritsSimpleEntity(p: EntityClass): Boolean =
      _inherits_simple_entity(p)

}
