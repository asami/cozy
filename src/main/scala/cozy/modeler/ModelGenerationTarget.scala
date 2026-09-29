package cozy.modeler

import org.goldenport.RAISE

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait ModelGenerationTarget

object ModelGenerationTarget {
  case object Cncf extends ModelGenerationTarget
  case object Library extends ModelGenerationTarget

  def requireInvocation(command: String, args: List[String]): ModelGenerationTarget =
    _values(args, "generation-target") match {
      case Vector() => Cncf
      case values if values.exists(_.isEmpty) =>
        RAISE.invalidArgumentFault(
          "LIBRARY_GENERATION_INVALID_TARGET: --generation-target requires cncf or library."
        )
      case values => values.flatten.distinct match {
        case Vector("cncf") => Cncf
        case Vector("library") =>
          if (command != "modeler-scala-value")
            RAISE.invalidArgumentFault(
              "LIBRARY_GENERATION_INVALID_COMMAND: library generation requires modeler-scala-value. Use --generation-target cncf for component generation."
            )
          _require_library_arguments(args)
          Library
        case selected =>
          RAISE.invalidArgumentFault(
            s"LIBRARY_GENERATION_INVALID_TARGET: conflicting or unsupported generation targets '${selected.mkString(",")}'; expected one of cncf or library."
          )
      }
    }

  def requireLibraryModel(
    builder: Modeler.ModelBuilder,
    compositeStateMachines: Vector[CompositeStateMachineDefinition],
    workflows: Vector[WorkflowDefinition]
  ): Unit = {
    def _category_(predicate: Boolean, name: String): Option[String] =
      if (predicate) Some(name) else None
    _require_library_categories(Vector(
      _category_(builder.entity.classes.nonEmpty, "entity"),
      _category_(builder.service.classes.nonEmpty, "service"),
      _category_(builder.operation.operations.nonEmpty, "operation"),
      _category_(!builder.event.isEmpty, "event/reception/routing/subscription"),
      _category_(!builder.actor.isEmpty, "actor"),
      _category_(compositeStateMachines.nonEmpty, "composite-state-machine/action"),
      _category_(workflows.nonEmpty, "workflow/provided-operation/candidate-admission")
    ).flatten)
  }

  def requireLibrarySource(source: java.nio.file.Path): Unit = {
    val normalized = source.toAbsolutePath.normalize()
    val model = org.goldenport.kaleidox.Model.load(
      org.goldenport.kaleidox.Config.default,
      normalized.toFile
    )
    val builder = Modeler.ModelBuilder(
      model,
      PredefinedResultCatalog.empty,
      ComponentStyleCatalog.EMPTY
    )
    requireLibraryModel(
      builder,
      CompositeStateMachineCml.definitions(model),
      CompositeStateMachineCml.workflowDefinitions(model)
    )
  }

  def requireLibraryGeneratorInput(
    model: org.simplemodeling.model.SimpleModel,
    compositeStateMachines: Vector[CompositeStateMachineDefinition],
    workflows: Vector[WorkflowDefinition]
  ): Unit = {
    def _category_(predicate: Boolean, name: String): Option[String] =
      if (predicate) Some(name) else None
    _require_library_categories(Vector(
      _category_(model.elements.exists(_.isInstanceOf[org.simplemodeling.model.MEntity]), "entity"),
      _category_(model.elements.exists(_.isInstanceOf[org.simplemodeling.model.MComponent]), "component"),
      _category_(compositeStateMachines.nonEmpty, "composite-state-machine/action"),
      _category_(workflows.nonEmpty, "workflow/provided-operation/candidate-admission")
    ).flatten)
  }

  private def _require_library_categories(categories: Vector[String]): Unit =
    if (categories.nonEmpty)
      RAISE.invalidArgumentFault(
        s"LIBRARY_GENERATION_UNSUPPORTED_RUNTIME: category=${categories.mkString(",")} is a CNCF runtime feature. Use --generation-target cncf."
      )

  private def _require_library_arguments(args: List[String]): Unit = {
    val cozyversion = _single_value(args, "cozy-generator-version").getOrElse(
      RAISE.invalidArgumentFault(
        "LIBRARY_GENERATION_COZY_VERSION_REQUIRED: library generation requires --cozy-generator-version with the exact running Cozy version."
      )
    )
    if (cozyversion != org.simplemodeling.cozy.BuildInfo.version)
      RAISE.invalidArgumentFault(
        s"LIBRARY_GENERATION_COZY_VERSION_MISMATCH: requested=$cozyversion running=${org.simplemodeling.cozy.BuildInfo.version}."
      )
    Vector(
      "cncf-version",
      "cncf-runtime-descriptor",
      "cncf-runtime-descriptor-sha256",
      "cncf-collaborator-api-version"
    ).find(name => _values(args, name).nonEmpty).foreach { name =>
      RAISE.invalidArgumentFault(
        s"LIBRARY_GENERATION_RUNTIME_CONTRADICTION: --$name is not permitted for library generation. Use --generation-target cncf."
      )
    }
  }

  private def _single_value(args: List[String], name: String): Option[String] = {
    val values = _values(args, name)
    if (values.exists(_.isEmpty) || values.flatten.distinct.size > 1)
      RAISE.invalidArgumentFault(
        s"LIBRARY_GENERATION_COZY_VERSION_MISMATCH: --$name must occur once with one exact value."
      )
    values.flatten.headOption
  }

  private def _values(args: List[String], name: String): Vector[Option[String]] = {
    val prefix = s"--$name="
    args.zipWithIndex.collect {
      case (value, _) if value.startsWith(prefix) => Option(value.drop(prefix.length).trim).filter(_.nonEmpty)
      case (value, index) if value == s"--$name" =>
        args.lift(index + 1).filterNot(_.startsWith("-")).map(_.trim).filter(_.nonEmpty)
    }.toVector
  }
}
