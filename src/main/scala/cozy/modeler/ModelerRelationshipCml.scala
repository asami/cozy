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
private[modeler] object ModelerRelationshipCml {
  import Modeler._

    private val _kind_association = "association"
    private val _kind_aggregation = "aggregation"
    private val _kind_composition = "composition"
    private val _storage_association_record = "association-record"
    private val _storage_child_parent_id_field = "child-parent-id-field"
    private val _storage_embedded_value_object = "embedded-value-object"

    def relationshipDefinitions(model: KaleidoxModel): Vector[MComponent.RelationshipDefinition] = {
      val valuenames = model.getValueModel.map(_.classes.keys.toSet).getOrElse(Set.empty[String])
      val entities = model.takeEntityModel.classes
      _division_sections(model, "RELATIONSHIP").flatMap { root =>
        _validate_blank_after_heading(root, "RELATIONSHIP")
        root.blocks.sections.toVector.map(_relationship_definition(_, valuenames, entities))
      }
    }

    def operationBindings(
      model: KaleidoxModel,
      relationships: Vector[MComponent.RelationshipDefinition]
    ): Map[String, OperationRelationshipBinding] = {
      val relmap = relationships.map(x => x.name -> x).toMap
      val pairs = _operation_sections(model).flatMap { op =>
        val childbindings = _children(op, "CHILD ENTITY BINDING").map(_child_entity_binding(_, relmap))
        val associations = _children(op, "ASSOCIATION BINDING").map(_association_binding(_, relmap))
        if (associations.size > 1)
          RAISE.syntaxErrorFault(s"Operation '${op.nameForModel}' accepts at most one ASSOCIATION BINDING.")
        val binding = OperationRelationshipBinding(
          childEntityBindings = childbindings,
          associationBinding = associations.headOption
        )
        if (binding.childEntityBindings.isEmpty && binding.associationBinding.isEmpty)
          None
        else
          Some(op.nameForModel -> binding)
      }
      pairs.toMap
    }

    private def _relationship_definition(
      section: LogicalSection,
      valuenames: Set[String],
      entities: VectorMap[String, EntityClass]
    ): MComponent.RelationshipDefinition = {
      val name = section.nameForModel
      _validate_blank_after_heading(section, s"RELATIONSHIP '$name'")
      section.blocks.sections.foreach(s => _validate_blank_after_heading(s, s"RELATIONSHIP '$name' field ${s.nameForModel}"))
      val kind = _required(section, "KIND", name).toLowerCase
      val source = _required(section, "SOURCE", name)
      val target = _required(section, "TARGET", name)
      val storage = _child_text(section, "STORAGE").map(_.toLowerCase).getOrElse {
        kind match {
          case x if x == _kind_composition => _storage_child_parent_id_field
          case _ => _storage_association_record
        }
      }
      _validate_enum(name, "KIND", kind, Set(_kind_association, _kind_aggregation, _kind_composition))
      _validate_enum(name, "STORAGE", storage, Set(_storage_association_record, _storage_child_parent_id_field, _storage_embedded_value_object))
      val parentidfield = _child_text(section, "PARENT ID FIELD")
      val valuefield = _child_text(section, "VALUE FIELD")
      if (kind == _kind_composition && storage == _storage_child_parent_id_field && parentidfield.isEmpty)
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$name' composition with child-parent-id-field requires PARENT ID FIELD.")
      if (storage == _storage_embedded_value_object && kind != _kind_composition)
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$name' embedded-value-object storage requires composition kind.")
      if (storage == _storage_embedded_value_object && valuefield.isEmpty)
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$name' composition with embedded-value-object requires VALUE FIELD.")
      if (storage == _storage_embedded_value_object && parentidfield.nonEmpty)
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$name' embedded-value-object storage does not accept PARENT ID FIELD.")
      if (storage == _storage_embedded_value_object && !valuenames.contains(target))
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$name' embedded-value-object TARGET '$target' must reference a VALUE.")
      if (storage == _storage_embedded_value_object)
        _validate_embedded_value_field(name, source, target, valuefield.get, entities)
      MComponent.RelationshipDefinition(
        name = name,
        kind = kind,
        sourceEntityName = source,
        targetEntityName = target,
        targetModelKind = if (storage == _storage_embedded_value_object) "value" else "entity",
        multiplicity = _child_text(section, "MULTIPLICITY"),
        storageMode = storage,
        parentIdField = parentidfield,
        valueField = valuefield,
        sortOrderField = _child_text(section, "SORT ORDER FIELD"),
        associationDomain = _child_text(section, "ASSOCIATION DOMAIN"),
        targetKind = _child_text(section, "TARGET KIND"),
        lifecyclePolicy = _child_text(section, "LIFECYCLE")
      )
    }

    private def _validate_embedded_value_field(
      relationshipname: String,
      sourceentityname: String,
      targetvaluename: String,
      valuefield: String,
      entities: VectorMap[String, EntityClass]
    ): Unit = {
      val entity = entities.getOrElse(sourceentityname,
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$relationshipname' embedded-value-object SOURCE '$sourceentityname' must reference an ENTITY.")
      )
      val attribute = entity.schemaClass.slots.collectFirst {
        case p: SchemaModel.Attribute if _same_key(p.name, valuefield) => p
      }.getOrElse(
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$relationshipname' VALUE FIELD '$valuefield' is not an ATTRIBUTE of SOURCE '$sourceentityname'.")
      )
      val actual = attribute.rawTypeName.map(_.split("\\.").last).getOrElse("")
      if (!_same_key(actual, targetvaluename))
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$relationshipname' VALUE FIELD '$valuefield' must have VALUE type '$targetvaluename'.")
    }

    private def _child_entity_binding(
      section: LogicalSection,
      relationships: Map[String, MComponent.RelationshipDefinition]
    ): MComponent.OperationChildEntityBinding = {
      _validate_blank_after_heading(section, s"CHILD ENTITY BINDING '${section.nameForModel}'")
      section.blocks.sections.foreach(s => _validate_blank_after_heading(s, s"CHILD ENTITY BINDING field ${s.nameForModel}"))
      val relationshipname = _required(section, "RELATIONSHIP", section.nameForModel)
      val relationship = relationships.getOrElse(relationshipname,
        RAISE.syntaxErrorFault(s"CHILD ENTITY BINDING references unknown RELATIONSHIP '$relationshipname'.")
      )
      if (relationship.storageMode != _storage_child_parent_id_field)
        RAISE.syntaxErrorFault(s"CHILD ENTITY BINDING '$relationshipname' requires child-parent-id-field storage.")
      val parentidfield = relationship.parentIdField.getOrElse(
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$relationshipname' requires PARENT ID FIELD for CHILD ENTITY BINDING.")
      )
      MComponent.OperationChildEntityBinding(
        name = relationshipname,
        entityName = relationship.targetEntityName,
        inputParameter = _required(section, "INPUT", relationshipname),
        parentIdField = parentidfield,
        relationshipName = Some(relationshipname),
        sourceEntityIdMode = _child_text(section, "SOURCE ENTITY ID").getOrElse("entity-create-result"),
        sourceEntityIdParameters = _child_vector(section, "SOURCE ENTITY ID PARAMETERS"),
        sourceEntityIdResultFields = _child_vector(section, "SOURCE ENTITY ID RESULT FIELDS", Vector("entity_id", "entityId", "id")),
        childIdField = _child_text(section, "CHILD ID FIELD").orElse(Some("id")),
        sortOrderField = _child_text(section, "SORT ORDER FIELD").orElse(relationship.sortOrderField),
        createsEntity = true,
        failurePolicy = _child_text(section, "FAILURE POLICY").getOrElse("compensate-parent-on-create")
      )
    }

    private def _association_binding(
      section: LogicalSection,
      relationships: Map[String, MComponent.RelationshipDefinition]
    ): MComponent.OperationAssociationBinding = {
      _validate_blank_after_heading(section, s"ASSOCIATION BINDING '${section.nameForModel}'")
      section.blocks.sections.foreach(s => _validate_blank_after_heading(s, s"ASSOCIATION BINDING field ${s.nameForModel}"))
      val relationshipname = _required(section, "RELATIONSHIP", section.nameForModel)
      val relationship = relationships.getOrElse(relationshipname,
        RAISE.syntaxErrorFault(s"ASSOCIATION BINDING references unknown RELATIONSHIP '$relationshipname'.")
      )
      if (relationship.storageMode != _storage_association_record)
        RAISE.syntaxErrorFault(s"ASSOCIATION BINDING '$relationshipname' requires association-record storage.")
      MComponent.OperationAssociationBinding(
        domain = relationship.associationDomain.getOrElse(relationship.name),
        targetKind = relationship.targetKind.getOrElse(relationship.targetEntityName),
        createsAssociation = true,
        detachesAssociation = false,
        roles = _child_vector(section, "ROLES"),
        parameters = _child_vector(section, "PARAMETERS"),
        sourceEntityIdMode = _child_text(section, "SOURCE ENTITY ID").getOrElse("entity-create-result"),
        sourceEntityIdParameters = _child_vector(section, "SOURCE ENTITY ID PARAMETERS"),
        sourceEntityIdResultFields = _child_vector(section, "SOURCE ENTITY ID RESULT FIELDS", Vector("entity_id", "entityId", "id")),
        targetIdParameters = _child_vector(section, "TARGET ID PARAMETERS"),
        sortOrderParameters = _child_vector(section, "SORT ORDER PARAMETERS")
      )
    }

    private def _operation_sections(model: KaleidoxModel): Vector[LogicalSection] =
      _division_sections(model, "SERVICE").flatMap { root =>
        root.blocks.sections.toVector.flatMap { service =>
          _children(service, "OPERATION").flatMap(_.blocks.sections.toVector)
        }
      }

    private def _division_sections(model: KaleidoxModel, name: String): Vector[LogicalSection] =
      model.divisions.toVector.flatMap { d =>
        _section(d).filter { s =>
          _same_key(d.name, name) || _same_key(s.keyForModel, name) || _same_key(s.nameForModel, name)
        }
      }

    private def _section(d: org.goldenport.kaleidox.Model.Division): Option[LogicalSection] =
      d match {
        case p: Product =>
          p.productIterator.collectFirst {
            case s: LogicalSection => s
          }
        case _ => None
      }

    private def _children(section: LogicalSection, name: String): Vector[LogicalSection] =
      section.blocks.sections.toVector.filter(x => _same_key(x.keyForModel, name) || _same_key(x.nameForModel, name))

    private def _required(section: LogicalSection, name: String, context: String): String =
      _child_text(section, name).getOrElse(
        RAISE.syntaxErrorFault(s"$context requires $name.")
      )

    private def _child_text(section: LogicalSection, name: String): Option[String] =
      section.blocks.sections.find(x => _same_key(x.keyForModel, name) || _same_key(x.nameForModel, name)).flatMap { s =>
        val text = s.blocks.text.trim
        if (text.isEmpty) None else Some(text)
      }

    private def _child_vector(section: LogicalSection, name: String): Vector[String] =
      _child_vector(section, name, Vector.empty)

    private def _child_vector(section: LogicalSection, name: String, default: Vector[String]): Vector[String] =
      _child_text(section, name).map(_split_values).filter(_.nonEmpty).getOrElse(default)

    private def _split_values(text: String): Vector[String] =
      text.split("[,\\n]").toVector.map(_.trim).filter(_.nonEmpty)

    private def _validate_enum(context: String, field: String, value: String, allowed: Set[String]): Unit =
      if (!allowed.contains(value))
        RAISE.syntaxErrorFault(s"$context $field must be one of ${allowed.toVector.sorted.mkString(", ")}: $value")

    private def _validate_blank_after_heading(section: LogicalSection, context: String): Unit =
      for {
        header <- _line(section)
        first <- _first_block_line(section)
        if first <= header + 1
      } RAISE.syntaxErrorFault(s"$context requires a blank line after the section heading.")

    private def _first_block_line(section: LogicalSection): Option[Int] =
      section.blocks.blocks.headOption.flatMap {
        case s: LogicalSection => _line(s)
        case p: org.goldenport.parser.LogicalParagraph =>
          p.lines.lines.headOption.flatMap(x => x.location.flatMap(_line))
        case _ => None
      }

    private def _line(section: LogicalSection): Option[Int] =
      section.location.flatMap(_line)

    private def _line(location: org.goldenport.parser.ParseLocation): Option[Int] =
      location.line

    private def _same_key(lhs: String, rhs: String): Boolean =
      _normalize_key(lhs) == _normalize_key(rhs)

    private def _normalize_key(s: String): String =
      s.toLowerCase.filter(_.isLetterOrDigit)

    def serviceOperationLeafContracts(
      p: KaleidoxModel
    ): Map[String, ServiceOperationLeafContract] = {
      p.divisions.toVector.flatMap { division =>
        _logical_section(division).filter { section =>
          division.name.equalsIgnoreCase("SERVICE") || _same_section_key(section, "SERVICE")
        }
      }.flatMap { service =>
        service.blocks.sections.toVector.flatMap { serviceclass =>
          _child_sections(serviceclass, "OPERATION").flatMap(_.blocks.sections.toVector)
        }
      }.map { operation =>
        operation.nameForModel -> ServiceOperationLeafContract(
          kind = _operation_kind_property(operation).orElse(_operation_kind_direct_property(operation)),
          inputType = _operation_direct_property(operation, "input").orElse(_child_property(operation, "INPUT", "type")),
          outputType = _operation_direct_property(operation, "output").orElse(_operation_direct_property(operation, "result")).orElse(_child_property(operation, "OUTPUT", "type"))
        )
      }.filter(_._2.nonEmpty).toMap
    }

    private def _operation_kind_property(
      operation: LogicalSection
    ): Option[OperationModel.OperationKind] =
      _service_child_text(operation, "TYPE").map {
        case x if x.equalsIgnoreCase("COMMAND") => OperationModel.OperationKind.Command
        case x if x.equalsIgnoreCase("QUERY") => OperationModel.OperationKind.Query
        case x => RAISE.syntaxErrorFault(s"Operation '${operation.nameForModel}' TYPE must be COMMAND or QUERY: $x")
      }

    private def _operation_kind_direct_property(
      operation: LogicalSection
    ): Option[OperationModel.OperationKind] =
      _operation_direct_property(operation, "type").map {
        case x if x.equalsIgnoreCase("COMMAND") => OperationModel.OperationKind.Command
        case x if x.equalsIgnoreCase("QUERY") => OperationModel.OperationKind.Query
        case x => RAISE.syntaxErrorFault(s"Operation '${operation.nameForModel}' TYPE must be COMMAND or QUERY: $x")
      }

    private def _operation_direct_property(
      operation: LogicalSection,
      propertyname: String
    ): Option[String] =
      CmlSectionFormat.directKeyValues(operation).collectFirst {
        case (key, value) if _same_property_key(key, propertyname) => value.trim
      }.filter(_.nonEmpty)

    private def _service_child_text(
      section: LogicalSection,
      childname: String
    ): Option[String] =
      _child_sections(section, childname).headOption.flatMap { child =>
        child.blocks.text.linesIterator.map(_.trim).find(_.nonEmpty)
      }

    private def _child_property(
      section: LogicalSection,
      childname: String,
      propertyname: String
    ): Option[String] =
      _child_sections(section, childname).headOption.flatMap { child =>
        _operation_direct_property(child, propertyname)
      }

    private def _child_sections(
      section: LogicalSection,
      childname: String
    ): Vector[LogicalSection] =
      section.blocks.sections.toVector.filter { child =>
        _same_section_key(child, childname)
      }

    def cmlDeclaredTypeNames(p: KaleidoxModel): Set[String] = {
      val typesections = Set("QUERY", "COMMAND", "VALUE", "ENTITY", "DATATYPE")
      val modeltypes = p.divisions.toVector.flatMap(_logical_section).filter { section =>
        typesections.contains(_normalize_section_key(section))
      }.flatMap(_.blocks.sections.toVector.map(_.nameForModel)).filter(_.nonEmpty).toSet
      modeltypes ++ p.takePowertypeModel.classes.keySet
    }

    private def _logical_section(d: org.goldenport.kaleidox.Model.Division): Option[LogicalSection] =
      d match {
        case p: Product =>
          p.productIterator.collectFirst {
            case s: LogicalSection => s
          }
        case _ => None
      }

    private def _normalize_section_key(section: LogicalSection): String =
      section.keyForModel.toUpperCase.filter(_.isLetterOrDigit)

    private def _same_section_key(section: LogicalSection, name: String): Boolean =
      _normalize_section_key(section) == name.toUpperCase.filter(_.isLetterOrDigit) ||
        section.nameForModel.toUpperCase.filter(_.isLetterOrDigit) == name.toUpperCase.filter(_.isLetterOrDigit)

    private def _same_property_key(lhs: String, rhs: String): Boolean =
      lhs.toUpperCase.filter(_.isLetterOrDigit) == rhs.toUpperCase.filter(_.isLetterOrDigit)
}
