package cozy.modeler

import scala.collection.mutable
import org.goldenport.RAISE
import org.goldenport.record.v2.{MOne => _, MZeroOne => _, MZeroMore => _, MOneMore => _, _}
import org.simplemodeling.model._
import org.simplemodeling.model.domain.MDomainValue

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
private[modeler] final class DartModelProjection private (
  private val _objects: Vector[MObject],
  private val _sealed_names: Set[String]
) {
  private val _index = _objects.map(x => x.qualifiedName -> x).toMap

  def objects: Vector[MObject] = _objects
  def isSealed(source: MObject): Boolean = _sealed_names(source.qualifiedName)
  def typeName(source: MObject): String = DartNaming.typeName(source.name, source.qualifiedName)
  def fieldName(owner: MObject, field: MAttribute): String =
    DartNaming.memberName(field.name, location(owner, field))
  def location(owner: MObject, field: MAttribute): String = s"${owner.qualifiedName}.${field.name}"
  def reference(source: MObjectRef): MObject = _index(DartModelProjection.referenceName(source))

  def isScalar(source: MObject): Boolean = source match {
    case _: MNominalDataType => true
    case _: MDomainValue => source.attributes match {
      case field :: Nil => field.name == "value" && field.multiplicity == MOne && field.attributeType.isInstanceOf[MDataType]
      case _ => false
    }
    case _ => false
  }

  def elementType(owner: MObject, field: MAttribute): String = field.attributeType match {
    case m: MDataType => DartModelProjection.primitiveType(m.datatype, location(owner, field))
    case m: MObjectAttributeType => typeName(reference(m.ref))
    case _ => RAISE.invalidArgumentFault(s"${location(owner, field)}: unsupported attribute type.")
  }
}

private[modeler] object DartModelProjection {
  def referenceName(source: MObjectRef): String =
    if (source.packageRef.isDefault) source.objectName else s"${source.packageName}.${source.objectName}"

  def primitiveType(datatype: DataType, location: String): String = datatype match {
    case XString => "String"
    case XBoolean => "bool"
    case XByte | XShort | XInt | XLong | XInteger => "int"
    case XFloat | XDouble => "double"
    case _ => RAISE.invalidArgumentFault(s"$location: unsupported primitive '${datatype.name}'.")
  }

  def integerBounds(datatype: DataType): (String, String) = datatype match {
    case XByte => ("-128", "127")
    case XShort => ("-32768", "32767")
    case XInt => ("-2147483648", "2147483647")
    case XLong | XInteger => ("-9007199254740991", "9007199254740991")
    case _ => RAISE.invalidArgumentFault(s"Not an admitted integral primitive: ${datatype.name}.")
  }

  def select(model: SimpleModel, options: DartModelGenerator.Options): DartModelProjection = {
    val index = model.elements.groupBy(_.qualifiedName)
    val selected = mutable.LinkedHashMap.empty[String, MObject]
    def _visit_(name: String, location: String): Unit = {
      if (!selected.contains(name)) {
        val source = index.get(name) match {
          case Some(Vector(value: MObject)) => value
          case Some(values) if values.size > 1 => RAISE.invalidArgumentFault(s"$location: ambiguous canonical identity '$name'.")
          case Some(_) => RAISE.invalidArgumentFault(s"$location: unsupported selected kind '$name'.")
          case None => RAISE.invalidArgumentFault(s"$location: unresolved canonical identity '$name'.")
        }
        _require_supported(source)
        selected.update(name, source)
        source.attributes.foreach { field =>
          val fieldlocation = s"$name.${field.name}"
          _require_field(field, fieldlocation)
          field.attributeType match {
            case m: MObjectAttributeType => _visit_(referenceName(m.ref), fieldlocation)
            case _: MDataType => ()
            case _ => RAISE.invalidArgumentFault(s"$fieldlocation: unsupported attribute type.")
          }
        }
      }
    }
    val roots = if (options.typeNames.nonEmpty) options.typeNames else model.elements.collect {
      case m: MObject if _is_data(m) => m.qualifiedName
    }
    roots.sorted.foreach(name => _visit_(name, name))
    val objects = selected.values.toVector.sortBy(_.qualifiedName)
    options.sealedTypeNames.toVector.sorted.foreach { name =>
      selected.get(name) match {
        case Some(m: MPowertype) if !m.isKnowledge => ()
        case _ => RAISE.invalidArgumentFault(s"$name: sealed selection requires a selected finite powertype.")
      }
    }
    val projection = new DartModelProjection(objects, options.sealedTypeNames)
    _require_symbols(projection)
    projection
  }

  private def _is_data(source: MObject): Boolean = source match {
    case _: MValue | _: MEntity | _: MPowertype => true
    case _ => false
  }

  private def _require_supported(source: MObject): Unit = {
    val location = source.qualifiedName
    if (!_is_data(source))
      RAISE.invalidArgumentFault(s"$location: unsupported selected data kind.")
    val unsupported = Vector(
      "base" -> source.base.nonEmpty, "traits" -> source.traits.nonEmpty,
      "associations" -> source.associations.nonEmpty, "powertypes" -> source.powertypes.nonEmpty,
      "stateMachines" -> source.stateMachines.nonEmpty, "ports" -> source.ports.nonEmpty,
      "roles" -> source.roles.nonEmpty, "services" -> source.services.nonEmpty,
      "rules" -> source.rules.nonEmpty, "vouchers" -> source.vouchers.nonEmpty
    ).collect { case (name, true) => name }
    if (unsupported.nonEmpty)
      RAISE.invalidArgumentFault(s"$location: unsupported semantics: ${unsupported.mkString(", ")}.")
    _require_unique(source.attributes.map(_.name), location, "source field")
    source match {
      case m: MPowertype =>
        if (!m.isKnowledge && m.kinds.isEmpty)
          RAISE.invalidArgumentFault(s"$location: finite powertype requires nonempty kinds.")
        _require_unique(m.kinds.map(_.name), location, "source kind")
      case _ => ()
    }
  }

  private def _require_field(field: MAttribute, location: String): Unit = {
    field.multiplicity match {
      case MOne | MZeroOne | MZeroMore | MOneMore => ()
      case _ => RAISE.invalidArgumentFault(s"$location: unsupported multiplicity '${field.multiplicity.mark}'.")
    }
    if (field.derived.nonEmpty)
      RAISE.invalidArgumentFault(s"$location: derived expression has no Dart evaluator.")
    field.attributeType match {
      case m: MDataType => primitiveType(m.datatype, location)
      case _: MObjectAttributeType => ()
      case _ => RAISE.invalidArgumentFault(s"$location: unsupported attribute type.")
    }
  }

  private def _require_unique(names: Seq[String], location: String, category: String): Unit = {
    names.groupBy(identity).toVector.sortBy(_._1).collectFirst {
      case (name, values) if values.size > 1 => name
    }.foreach(name => RAISE.invalidArgumentFault(s"$location: duplicate $category '$name'."))
  }

  private def _require_symbols(projection: DartModelProjection): Unit = {
    val globals = mutable.Map.empty[String, String]
    DartNaming.reservedTypes.foreach(name => globals.update(name, "Dart SDK/technical type"))
    def _reserve_(symbol: String, location: String): Unit = {
      globals.get(symbol).foreach(previous =>
        RAISE.invalidArgumentFault(s"$location: Dart symbol '$symbol' collides with $previous."))
      globals.update(symbol, location)
    }
    projection.objects.foreach(source => _reserve_(projection.typeName(source), source.qualifiedName))
    projection.objects.foreach { source =>
      val location = source.qualifiedName
      val reserved = DartNaming.objectMembers ++ DartNaming.codecMembers
      source match {
        case m: MPowertype =>
          val members = m.kinds.map(kind => DartNaming.memberName(kind.name, s"$location.${kind.name}"))
          _require_unique(members, location, "normalized kind")
          members.foreach { name =>
            if ((reserved ++ DartNaming.classificationMembers ++ DartNaming.enumMembers)(name))
              RAISE.invalidArgumentFault(s"$location.$name: reserved classification member.")
          }
          if (projection.isSealed(source)) {
            m.kinds.foreach(kind => _reserve_(projection.typeName(source) + DartNaming.typeName(kind.name, location), s"$location.${kind.name}"))
          }
        case _ =>
          val modelreserved = reserved ++ Set("int", "double", "bool")
          val members = source.attributes.map(field => projection.fieldName(source, field))
          _require_unique(members, location, "normalized field")
          members.foreach { name =>
            if (modelreserved(name)) RAISE.invalidArgumentFault(s"$location.$name: reserved model member.")
          }
      }
    }
  }
}
