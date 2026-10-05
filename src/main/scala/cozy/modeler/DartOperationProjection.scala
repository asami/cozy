package cozy.modeler

import org.goldenport.RAISE
import org.goldenport.collection.VectorMap
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.kaleidox.model.{ComponentSubsystemModel, OperationModel, ServiceModel}
import org.simplemodeling.model.{MObject, SimpleModel}

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
private[modeler] object DartOperationProjection {
  // Only source identities, the existing normalized contract and canonical objects
  // are joined here. Parameters never become an independently authored DTO schema.
  case class Operation(
    component: String,
    service: String,
    definition: OperationModel.NormalizedOperationDefinition,
    input: Option[MObject],
    output: Option[MObject]
  ) {
    def identity: String = s"$component.$service.${definition.name}"
    def interfaceName: String =
      DartNaming.typeName(component, identity) + DartNaming.typeName(service, identity) + "Api"
    def methodName: String = DartNaming.memberName(definition.name, identity)
  }

  def canonicalBindings(
    builder: Modeler.ModelBuilder,
    model: SimpleModel
  ): ModelServiceOperationProjector.CanonicalTypeBindings = {
    val objects = model.elements.collect { case source: MObject => source }
    val qualified = objects.groupBy(_.qualifiedName)
    val unqualified = objects.groupBy(_.name)
    val names = qualified.collect { case (name, entries) if entries.size == 1 => name }.toSet ++
      unqualified.collect { case (name, entries) if entries.size == 1 => name }.toSet
    val projector = new ModelTypeProjector(builder)
    val sources = builder.value.classes.values.toVector ++ projector.serviceInlineValues
    val projected = sources.map(source => projector.projectValue(source).qualifiedName -> source)
    val values = projected.groupBy(_._1).toVector.collect {
      case (name, Vector((_, source))) if qualified.get(name).exists(_.size == 1) => name -> source
    }.toMap
    val shortvalues = sources.groupBy(_.name).toVector.collect {
      case (name, Vector(source)) if unqualified.get(name).exists(_.size == 1) &&
          values.get(projector.projectValue(source).qualifiedName).contains(source) => name -> source
    }.toMap
    ModelServiceOperationProjector.CanonicalTypeBindings(names, values ++ shortvalues)
  }

  def select(
    model: KaleidoxModel,
    builder: Modeler.ModelBuilder,
    data: SimpleModel,
    operationNames: Vector[String]
  ): Vector[Operation] = {
    _require_unique(operationNames, "operation selection")
    val components = builder.componentSubsystem.components
    _require_unique(components.map(_.name), "component identity")
    val bindings = canonicalBindings(builder, data)
    val requested = operationNames.toSet
    val selected = builder.service.classes.values.toVector.sortBy(_.name).flatMap { service =>
      val candidates = service.operations.operations.values.toVector.filter { operation =>
        requested.isEmpty || requested.exists(_.endsWith(s".${service.name}.${operation.name}"))
      }
      if (candidates.isEmpty) Vector.empty else {
        val owner = _owner(components, service.name)
        val operations = candidates.filter(operation => requested.isEmpty || requested(s"${owner.name}.${service.name}.${operation.name}"))
        if (operations.isEmpty) Vector.empty else {
          _require_unique(operations.map(_.name), s"service '${service.name}' operation identity")
          val source = service.copy(operations = ServiceModel.ServiceClass.Operations(operations))
          val view = builder.copy(
            service = ServiceModel(source),
            serviceOperationLeafContracts = ModelerRelationshipCml.serviceOperationLeafContractsForService(
              model, service.name, operations.map(_.name).toSet)
          )
          val projector = new ModelServiceOperationProjector(view, new ModelTypeProjector(view), Some(bindings))
          val definitions = projector.normalizedServiceOperationDefinitions.groupBy(_.name)
          operations.sortBy(_.name).map { operation =>
            val identity = s"${owner.name}.${service.name}.${operation.name}"
            val definition = definitions.get(operation.name) match {
              case Some(Vector(one)) => one
              case Some(_) => RAISE.invalidArgumentFault(s"$identity: ambiguous normalized operation.")
              case None => RAISE.invalidArgumentFault(s"$identity: incomplete operation kind/input/output metadata.")
            }
            Operation(owner.name, service.name, definition,
              _resolve(data, definition.inputType, identity + " INPUT"),
              _resolve(data, definition.outputType, identity + " OUTPUT"))
          }
        }
      }
    }.sortBy(_.identity)
    _require_unique(selected.map(_.identity), "selected operation identity")
    (requested -- selected.map(_.identity).toSet).toVector.sorted.headOption.foreach { name =>
      RAISE.invalidArgumentFault(s"Unknown selected operation '$name'.")
    }
    selected
  }

  private def _owner(
    components: Vector[ComponentSubsystemModel.ComponentDefinition],
    servicename: String
  ): ComponentSubsystemModel.ComponentDefinition = {
    val owners = components.filter(_.services.exists(_.name == servicename))
    owners match {
      case Vector(one) => one
      case entries if entries.size > 1 =>
        RAISE.invalidArgumentFault(s"Service '$servicename' has multiple explicit component owners.")
      case _ if components.exists(_.services.nonEmpty) =>
        RAISE.invalidArgumentFault(s"Service '$servicename' has no explicit component owner.")
      case _ => components match {
        case Vector(one) => one
        case Vector() => RAISE.invalidArgumentFault(s"Service '$servicename' requires a component owner.")
        case _ => RAISE.invalidArgumentFault(s"Service '$servicename' has ambiguous implicit component ownership.")
      }
    }
  }

  private def _resolve(data: SimpleModel, name: String, location: String): Option[MObject] = {
    if (name == "void") None else {
      val objects = data.elements.collect { case source: MObject => source }
      val exact = objects.filter(_.qualifiedName == name)
      val matches = if (exact.nonEmpty || name.contains('.')) exact else objects.filter(_.name == name)
      matches match {
        case Vector(one) => Some(one)
        case Vector() => RAISE.invalidArgumentFault(s"$location: unresolved canonical DTO '$name'.")
        case _ => RAISE.invalidArgumentFault(s"$location: ambiguous canonical DTO '$name'.")
      }
    }
  }

  private def _require_unique(names: Vector[String], location: String): Unit =
    names.groupBy(identity).toVector.sortBy(_._1).collectFirst {
      case (name, entries) if entries.size > 1 => name
    }.foreach(name => RAISE.invalidArgumentFault(s"$location: duplicate '$name'."))
}
