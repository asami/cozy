package cozy.modeler

import org.goldenport.RAISE
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.kaleidox.model.ServiceModel
import org.goldenport.parser.LogicalSection

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object FailureModelCml {
  private case class Located(section: LogicalSection, path: Vector[String]) {
    def source(kind: String): FailureModelSourceIdentity =
      FailureModelSourceIdentity(kind, path.mkString("/"), section.location.flatMap(_.line))
  }
  private case class Owner(kind: String, identity: String, node: Located, service: Option[String])

  def requireDefinitions(model: KaleidoxModel): Vector[ComponentFailureModel] =
    normalize(model) match {
      case Right(components) => components
      case Left(issues) => RAISE.syntaxErrorFault(issues.map(x => s"${x.code}: ${x.message} (${x.source.sectionPath})").mkString("; "))
    }

  def normalize(model: KaleidoxModel): Either[Vector[FailureModelIssue], Vector[ComponentFailureModel]] = {
    val roots = _original_roots(model)
    def _has_declaration_(section: LogicalSection): Boolean =
      Set("EXECUTION-MODEL", "FAILURE-MODEL").contains(section.nameForModel) ||
        section.blocks.sections.exists(_has_declaration_)
    if (!roots.exists(_has_declaration_))
      return Right(Vector.empty)
    val issues = Vector.newBuilder[FailureModelIssue]
    val executions = Vector.newBuilder[Located]
    val owners = Vector.newBuilder[Owner]
    val selectors = Vector.newBuilder[(Owner, Located)]
    val containers = Vector.newBuilder[(Owner, Located)]

    def _issue_(code: String, message: String, node: Located, kind: String): Unit =
      issues += FailureModelIssue(code, message, node.source(kind))

    def _identity_(node: Located, kind: String): String = {
      val identity = node.section.nameForModel
      if (!_valid_identity(node.section.title.toI18NString.en))
        _issue_("FAILURE_MODEL_INVALID_STRUCTURE", s"Invalid $kind identity '$identity'", node, kind)
      identity
    }

    def _scan_(node: Located, role: String, parent: Option[Owner]): Unit = {
      val name = node.section.nameForModel
      val children = _children(node)
      val reserved = name == "EXECUTION-MODEL" || name == "FAILURE-MODEL"
      val admitted = (role, name) match {
        case ("ROOT", "EXECUTION-MODEL") =>
          _empty_prologue_(node)
          if (children.isEmpty)
            _issue_("FAILURE_MODEL_INVALID_STRUCTURE", "Execution declaration requires at least one definition", node, "EXECUTION-MODEL")
          children.foreach(x => _scan_(x, "EXECUTION-DEFINITION", None))
          true
        case ("ROOT", "COMPONENT") =>
          children.foreach(x => _scan_(x, "COMPONENT-OWNER", None))
          true
        case ("ROOT", "SERVICE") | ("COMPONENT-CHILD", "SERVICE") =>
          children.foreach(x => _scan_(x, "SERVICE-OWNER", None))
          true
        case ("EXECUTION-DEFINITION", _) =>
          _identity_(node, "EXECUTION-MODEL")
          executions += node
          _empty_prologue_(node)
          children.foreach(x => _scan_(x, "EXECUTION-CHILD", None))
          true
        case ("COMPONENT-OWNER", _) =>
          val owner = Owner("COMPONENT", _identity_(node, "COMPONENT"), node, None)
          owners += owner
          children.foreach(x => _scan_(x, "COMPONENT-CHILD", Some(owner)))
          true
        case ("SERVICE-OWNER", _) =>
          val owner = Owner("SERVICE", _identity_(node, "SERVICE"), node, None)
          owners += owner
          children.foreach(x => _scan_(x, "SERVICE-CHILD", Some(owner)))
          true
        case ("SERVICE-CHILD", "OPERATION") =>
          children.foreach(x => _scan_(x, "OPERATION-OWNER", parent))
          true
        case ("OPERATION-OWNER", _) =>
          val owner = Owner("OPERATION", _identity_(node, "OPERATION"), node, parent.map(_.identity))
          owners += owner
          children.foreach(x => _scan_(x, "OPERATION-CHILD", Some(owner)))
          true
        case ("COMPONENT-CHILD", "EXECUTION-MODEL") =>
          parent.foreach(x => selectors += x -> node)
          children.foreach(x => _scan_(x, "UNSUPPORTED", None))
          true
        case ("EXECUTION-CHILD", "FAILURE-MODEL") =>
          children.foreach(x => _scan_(x, "FAILURE-LEAF", None))
          true
        case ("COMPONENT-CHILD", "FAILURE-MODEL") | ("SERVICE-CHILD", "FAILURE-MODEL") | ("OPERATION-CHILD", "FAILURE-MODEL") =>
          parent.foreach(x => containers += x -> node)
          children.foreach(x => _scan_(x, "FAILURE-LEAF", None))
          true
        case ("FAILURE-LEAF", _) =>
          _identity_(node, "FAILURE-MODEL")
          children.foreach(x => _scan_(x, "UNSUPPORTED", None))
          true
        case _ => false
      }
      if (!admitted) {
        if (reserved)
          _issue_("FAILURE_MODEL_INVALID_STRUCTURE", s"Unsupported $name placement", node, name)
        children.foreach(x => _scan_(x, "UNSUPPORTED", None))
      }
    }

    def _empty_prologue_(node: Located): Unit =
      if (node.section.blocks.prologue.text.trim.nonEmpty)
        _issue_("FAILURE_MODEL_INVALID_STRUCTURE", "Unexpected container fields or text", node, "FAILURE-MODEL")

    def _unique_(nodes: Vector[Located], kind: String): Unit = {
      val seen = scala.collection.mutable.Set.empty[String]
      nodes.foreach { node =>
        if (!seen.add(node.section.nameForModel))
          _issue_("FAILURE_MODEL_DUPLICATE", s"Repeated $kind '${node.section.nameForModel}'", node, kind)
      }
    }

    def _field_(node: Located, key: String, kind: String): Option[String] = {
      if (node.section.blocks.sections.nonEmpty)
        _issue_("FAILURE_MODEL_INVALID_STRUCTURE", "Leaf declarations cannot contain children", node, kind)
      val lines = node.section.blocks.prologue.text.linesIterator.map(_.trim).filter(_.nonEmpty).toVector
      val fields = lines.flatMap { line =>
        val separator = line.indexOf('=')
        if (separator < 0) {
          _issue_("FAILURE_MODEL_INVALID_STRUCTURE", s"Expected $key = value", node, kind)
          None
        } else {
          val field = line.substring(0, separator).trim
          if (field != key)
            _issue_("FAILURE_MODEL_INVALID_STRUCTURE", s"Unknown field '$field'; only $key is admitted", node, kind)
          Some(field -> line.substring(separator + 1).trim)
        }
      }
      val seen = scala.collection.mutable.Set.empty[String]
      fields.foreach { case (field, _) =>
        if (!seen.add(field))
          _issue_("FAILURE_MODEL_DUPLICATE", s"Repeated field '$field'", node, kind)
      }
      val values = fields.collect { case (`key`, value) => value }
      if (values.isEmpty)
        _issue_("FAILURE_MODEL_INVALID_STRUCTURE", s"Missing $key field", node, kind)
      values.headOption
    }

    def _entries_(node: Located): Vector[FailureModelEntry] = {
      _empty_prologue_(node)
      val leaves = _children(node)
      if (leaves.isEmpty)
        _issue_("FAILURE_MODEL_INVALID_STRUCTURE", "FAILURE-MODEL must contain failures", node, "FAILURE-MODEL")
      _unique_(leaves, "FAILURE-MODEL")
      leaves.flatMap { leaf =>
        val identity = _identity_(leaf, "FAILURE-MODEL")
        _field_(leaf, "scope", "FAILURE-MODEL").flatMap { value =>
          val scope = value match {
            case "IN_SCOPE" => Some(FailureScope.InScope)
            case "OUT_OF_SCOPE" => Some(FailureScope.OutOfScope)
            case _ =>
              _issue_("FAILURE_MODEL_INVALID_SCOPE", s"Invalid scope '$value'", leaf, "FAILURE-MODEL")
              None
          }
          scope.map(FailureModelEntry(identity, _, leaf.source("FAILURE-MODEL")))
        }
      }
    }

    def _single_(nodes: Vector[Located], kind: String): Option[Located] = {
      nodes.drop(1).foreach(x => _issue_("FAILURE_MODEL_DUPLICATE", s"Repeated $kind container", x, kind))
      nodes.headOption
    }

    roots.foreach(x => _scan_(Located(x, Vector(x.nameForModel)), "ROOT", None))
    val executionnodes = executions.result()
    val ownernodes = owners.result()
    val selectornodes = selectors.result()
    val deltanodes = containers.result()
    val seenowners = scala.collection.mutable.Set.empty[(String, Option[String], String)]
    ownernodes.foreach { owner =>
      if (!seenowners.add((owner.kind, owner.service, owner.identity)))
        _issue_("FAILURE_MODEL_DUPLICATE", s"Repeated ${owner.kind} owner '${owner.identity}'", owner.node, owner.kind)
    }
    val bearing = executionnodes.nonEmpty || selectornodes.nonEmpty || deltanodes.nonEmpty
    if (!bearing) {
      val errors = issues.result()
      return if (errors.isEmpty) Right(Vector.empty) else Left(errors)
    }
    _unique_(executionnodes, "EXECUTION-MODEL")
    val definitions = executionnodes.map { node =>
      val children = _children(node)
      children.filterNot(_.section.nameForModel == "FAILURE-MODEL").foreach { child =>
        _issue_("FAILURE_MODEL_INVALID_STRUCTURE", "Execution definitions admit only FAILURE-MODEL", child, "EXECUTION-MODEL")
      }
      val container = _single_(children.filter(_.section.nameForModel == "FAILURE-MODEL"), "FAILURE-MODEL")
      if (container.isEmpty)
        _issue_("FAILURE_MODEL_INVALID_STRUCTURE", "Execution definition requires FAILURE-MODEL", node, "EXECUTION-MODEL")
      ExecutionFailureModel(node.section.nameForModel, node.source("EXECUTION-MODEL"), container.toVector.flatMap(_entries_))
    }
    val structureissues = issues.result()
    if (structureissues.nonEmpty)
      return Left(structureissues)
    val components = model.takeComponentSubsystemModel.components
    if (components.size != 1 || ownernodes.count(_.kind == "COMPONENT") != 1)
      roots.headOption.foreach(x => _issue_("FAILURE_MODEL_INVALID_OWNER", "Failure Model source requires exactly one explicit resolved component", Located(x, Vector(x.nameForModel)), "COMPONENT"))
    val services = model.getServiceModel.getOrElse(ServiceModel.empty).classes.values.toVector
    ownernodes.foreach { owner =>
      val resolved = owner.kind match {
        case "COMPONENT" => components.exists(_.name == owner.identity)
        case "SERVICE" => services.exists(_.name == owner.identity)
        case "OPERATION" => services.find(x => owner.service.contains(x.name)).exists(_.operations.operations.values.exists(_.name == owner.identity))
      }
      if (!resolved)
        _issue_("FAILURE_MODEL_INVALID_OWNER", s"Unresolved ${owner.kind} '${owner.identity}'", owner.node, owner.kind)
    }
    services.foreach { service =>
      val source = ownernodes.find(x => x.kind == "SERVICE" && x.identity == service.name)
      if (source.isEmpty)
        roots.headOption.foreach(x => _issue_("FAILURE_MODEL_INVALID_OWNER", s"Resolved service '${service.name}' has no original source owner", Located(x, Vector(x.nameForModel)), "SERVICE"))
      service.operations.operations.values.foreach { operation =>
        if (!ownernodes.exists(x => x.kind == "OPERATION" && x.service.contains(service.name) && x.identity == operation.name))
          source.foreach(x => _issue_("FAILURE_MODEL_INVALID_OWNER", s"Resolved operation '${operation.name}' has no original source owner", x.node, "OPERATION"))
      }
    }
    val selections = ownernodes.filter(_.kind == "COMPONENT").flatMap { owner =>
      _single_(selectornodes.collect { case (`owner`, node) => node }, "EXECUTION-MODEL").flatMap { node =>
        _field_(node, "model", "EXECUTION-MODEL").flatMap { identity =>
          if (!_valid_identity(identity))
            _issue_("FAILURE_MODEL_INVALID_STRUCTURE", s"Invalid execution reference '$identity'", node, "EXECUTION-MODEL")
          definitions.find(_.identity == identity) match {
            case Some(definition) => Some((owner, node, definition))
            case None =>
              _issue_("FAILURE_MODEL_UNKNOWN_EXECUTION", s"Unknown execution '$identity'", node, "EXECUTION-MODEL")
              None
          }
        }
      }
    }
    val deltas = ownernodes.map { owner =>
      val container = _single_(deltanodes.collect { case (`owner`, node) => node }, "FAILURE-MODEL")
      val entries = container.toVector.flatMap(_entries_)
      if (container.nonEmpty && selections.isEmpty)
        _issue_("FAILURE_MODEL_INVALID_OWNER", "Refinements require a component execution selector", owner.node, owner.kind)
      selections.headOption.foreach { case (_, _, definition) =>
        entries.filterNot(x => definition.defaults.exists(_.identity == x.identity)).foreach { entry =>
          issues += FailureModelIssue("FAILURE_MODEL_UNKNOWN_FAILURE", s"Unknown selected failure '${entry.identity}'", entry.source)
        }
      }
      owner -> FailureModelDelta(owner.kind, owner.identity, container.map(_.source("FAILURE-MODEL")), entries)
    }
    val errors = issues.result()
    if (errors.nonEmpty) Left(errors)
    else {
      def _delta_(owner: Owner): FailureModelDelta = deltas.find(_._1 == owner).get._2
      def _effective_(owner: Owner, execution: ExecutionFailureModel, chain: Vector[FailureModelDelta]): EffectiveFailureContract =
        FailureModel.effective(owner.kind, owner.identity, owner.node.source(owner.kind), execution.defaults, chain)
      Right(selections.map { case (owner, selector, execution) =>
        val delta = _delta_(owner)
        val servicecontracts = services.map { service =>
          val serviceowner = ownernodes.find(x => x.kind == "SERVICE" && x.identity == service.name).get
          val servicedelta = _delta_(serviceowner)
          val chain = Vector(delta, servicedelta)
          val operations = service.operations.operations.values.toVector.map { operation =>
            val operationowner = ownernodes.find(x => x.kind == "OPERATION" && x.service.contains(service.name) && x.identity == operation.name).get
            val operationdelta = _delta_(operationowner)
            OperationFailureModel(operation.name, operationowner.node.source("OPERATION"), operationdelta,
              _effective_(operationowner, execution, chain :+ operationdelta))
          }
          ServiceFailureModel(service.name, serviceowner.node.source("SERVICE"), servicedelta,
            _effective_(serviceowner, execution, chain), operations)
        }
        ComponentFailureModel(owner.identity, owner.node.source("COMPONENT"), selector.source("EXECUTION-MODEL"), execution,
          delta, _effective_(owner, execution, Vector(delta)), servicecontracts)
      })
    }
  }

  private def _valid_identity(identity: String): Boolean =
    identity.nonEmpty && identity == identity.trim && !identity.exists(x => Character.isISOControl(x) || x == '.' || x == '/')

  private def _children(node: Located): Vector[Located] =
    node.section.blocks.sections.toVector.map(x => Located(x, node.path :+ x.nameForModel))

  private def _original_roots(model: KaleidoxModel): Vector[LogicalSection] = {
    val sections = model.divisions.flatMap {
      case product: Product => product.productIterator.collectFirst { case section: LogicalSection => section }
      case _ => None
    }
    def _contains_(parent: LogicalSection, child: LogicalSection): Boolean =
      parent.blocks.sections.exists(x => (x eq child) || _contains_(x, child))
    sections.filterNot(x => sections.exists(y => !(x eq y) && _contains_(y, x)))
  }
}
