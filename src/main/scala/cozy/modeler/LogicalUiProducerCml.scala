package cozy.modeler

import com.typesafe.config.{ConfigException, ConfigFactory, ConfigList, ConfigParseOptions, ConfigSyntax, ConfigValue, ConfigValueType}
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.parser._
import cozy.ui.CozyLogicalUiRuntime
import LogicalUiProducer._
import LogicalUiProducer.DiagnosticCode._
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
/** Interprets direct ordinary CML AST structure; JSON only decodes individual literals. */
private[modeler] object LogicalUiProducerCml {
  private final case class SectionInput(properties: Map[String, ConfigValue], children: Vector[LogicalSection])
  private val _json_options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)
  private val _property_line = "^\\s*([A-Za-z][A-Za-z0-9]*)\\s*=\\s*(.+?)\\s*$".r
  private val _json_string = "\"(?:[^\"\\\\\\p{Cntrl}]|\\\\(?:[\"\\\\/bfnrt]|u[0-9A-Fa-f]{4}))*\""
  private val _single_literal = ("(?:" + _json_string + "|\\[\\s*(?:" + _json_string +
    "(?:\\s*,\\s*" + _json_string + ")*)?\\s*\\]|null|true|false|-?[0-9]+(?:\\.[0-9]+)?)").r

  def definitions(model: KaleidoxModel): Either[Diagnostic, Vector[SourceComponent]] = {
    if (model == null) _element("logicalUi", "null model")
    else if (model.divisions == null) _element("logicalUi", "null divisions")
    else if (model.divisions.contains(null)) _element("logicalUi", "null division")
    else if (model.errors == null) _element("logicalUi", "null model error vector")
    else if (model.errors.nonEmpty) _cml("logicalUi", "ordinary model parser reported invalid input")
    else {
      val documents = model.divisions.collect { case division: KaleidoxModel.DocumentDivision => division.section }
      _sequence(documents.zipWithIndex) { case (section, index) =>
        _shape(section, s"logicalUi[$index]").map(_ => section)
      }.flatMap { checked =>
        val roots = checked.filter(section => _title(section).equalsIgnoreCase("LOGICAL-UI"))
        _sequence(roots.zipWithIndex) { case (root, index) =>
          val path = s"logicalUi[$index]"
          if (_title(root) != "LOGICAL-UI") _cml(path, "LOGICAL-UI header is case-sensitive")
          else _root(model, root, path)
        }.flatMap { components =>
          components.indices.find(index => components.take(index).exists(
            _.owner.componentName == components(index).owner.componentName)) match {
            case Some(index) => _component(s"logicalUi[$index].prologue.component", "repeated component root")
            case None => Right(components)
          }
        }
      }
    }
  }

  private def _root(model: KaleidoxModel, root: LogicalSection, path: String): Either[Diagnostic, SourceComponent] =
    for {
      input <- _input(root, path, Set("component", "producerId", "sourceId", "sourceRevision"))
      name <- _string(input, "component", path, ComponentInvalid)
      owner <- _owner(model, name, path + ".prologue.component")
      producer <- _string(input, "producerId", path)
      source <- _string(input, "sourceId", path)
      revision <- _string(input, "sourceRevision", path)
      definitions <- _sequence(input.children.zipWithIndex) { case (child, index) =>
        _definition(child, s"$path.sections[$index]")
      }
    } yield SourceComponent(owner, CozyLogicalUiRuntime.SourceProvenance(producer, source, revision), definitions)

  private def _owner(model: KaleidoxModel, name: String, path: String): Either[Diagnostic, ComponentOwner] = {
    val divisions = model.divisions.collect { case division: KaleidoxModel.ComponentDivision => division }
    _sequence(divisions.zipWithIndex) { case (division, index) =>
      _shape(division.section, s"$path.declarations[$index]").map(_ => division)
    }.flatMap { checked =>
      val declarations = checked.filter(division => _title(division.section) == "COMPONENT").flatMap { division =>
        division.section.blocks.blocks.collect { case child: LogicalSection => (division, child) }
      }.filter { case (_, child) => _title(child) == name }
      if (declarations.size != 1) _component(path, "owner must name exactly one source COMPONENT declaration")
      else if (model.config == null) _element(path, "null model configuration")
      else {
        val division = declarations.head._1
        try {
          val candidates = division.makeModel(model.config).components.filter(_.name == name)
          if (candidates.size != 1 || candidates.head.packageName == null ||
            candidates.head.packageName.isEmpty || candidates.head.packageName.contains(null))
            _component(path, "source component requires exactly one declared package")
          else Right(ComponentOwner(name, candidates.head.packageName.get))
        } catch {
          case NonFatal(_) => _component(path, "source component model cannot resolve the declared owner")
        }
      }
    }
  }

  private def _definition(section: LogicalSection, path: String): Either[Diagnostic, Definition] = {
    val header = _title(section)
    if (header != "LIST" && header != "DETAIL") _cml(path, "expected LIST or DETAIL")
    else for {
      input <- _input(section, path, Set("screenId", "definitionRevision", "viewBindingId", "title") ++
        (if (header == "LIST") Set("detailTarget") else Set.empty[String]))
      id <- _string(input, "screenId", path)
      revision <- _string(input, "definitionRevision", path)
      binding <- _string(input, "viewBindingId", path)
      title <- _string(input, "title", path)
      screen <- if (header == "LIST") _list(input, title, path) else _detail(input, title, path)
    } yield Definition(id, revision, binding, screen)
  }

  private def _list(input: SectionInput, title: String, path: String): Either[Diagnostic, Screen] =
    for {
      containers <- _containers(input, Set("ITEM-FIELDS", "ACTIONS", "ITEM-ACTIONS"), path)
      target <- _string(input, "detailTarget", path)
      fields <- _entries(containers, "ITEM-FIELDS", "FIELD", path)(_field)
      actions <- _entries(containers, "ACTIONS", "ACTION", path)(_action)
      itemactions <- _entries(containers, "ITEM-ACTIONS", "ACTION", path)(_action)
    } yield ListScreen(title, fields, DetailTarget(target), actions, itemactions)

  private def _detail(input: SectionInput, title: String, path: String): Either[Diagnostic, Screen] =
    for {
      containers <- _containers(input, Set("SECTIONS", "ACTIONS"), path)
      sections <- _entries(containers, "SECTIONS", "SECTION", path)(_section)
      actions <- _entries(containers, "ACTIONS", "ACTION", path)(_action)
    } yield DetailScreen(title, sections, actions)

  private def _section(section: LogicalSection, path: String): Either[Diagnostic, Section] =
    for {
      input <- _input(section, path, Set("id", "title"))
      id <- _string(input, "id", path)
      title <- if (input.properties.contains("title")) _string(input, "title", path).map(Some(_))
        else Right(None)
      containers <- _containers(input, Set("FIELDS"), path)
      fields <- _entries(containers, "FIELDS", "FIELD", path)(_field)
    } yield Section(id, title, fields)

  private def _field(section: LogicalSection, path: String): Either[Diagnostic, Field] =
    for {
      input <- _input(section, path, Set("id", "label", "role", "bindingId", "allowedKinds"))
      _ <- _leaf(input, path)
      id <- _string(input, "id", path)
      label <- _string(input, "label", path)
      tag <- _string(input, "role", path)
      role <- _role(tag, path + ".prologue.role")
      binding <- _string(input, "bindingId", path)
      kinds <- _kinds(input, path)
    } yield Field(id, label, role, ValueBinding(binding, kinds))

  private def _action(section: LogicalSection, path: String): Either[Diagnostic, Action] =
    for {
      input <- _input(section, path, Set("id", "label", "bindingId"))
      _ <- _leaf(input, path)
      id <- _string(input, "id", path)
      label <- _string(input, "label", path)
      binding <- _string(input, "bindingId", path)
    } yield Action(id, label, binding)

  private def _role(tag: String, path: String): Either[Diagnostic, CozyLogicalUiRuntime.PresentationRole] = tag match {
    case "content" => Right(CozyLogicalUiRuntime.ContentRole)
    case "title" => Right(CozyLogicalUiRuntime.TitleRole)
    case "subtitle" => Right(CozyLogicalUiRuntime.SubtitleRole)
    case "description" => Right(CozyLogicalUiRuntime.DescriptionRole)
    case "status" => Right(CozyLogicalUiRuntime.StatusRole)
    case _ => Left(Diagnostic(RoleInvalid, path, "unsupported case-sensitive role tag"))
  }

  private def _kinds(input: SectionInput, path: String): Either[Diagnostic, Vector[StaticValueKind]] = {
    val memberpath = path + ".prologue.allowedKinds"
    input.properties.get("allowedKinds") match {
      case Some(value) if value.valueType() == ConfigValueType.LIST =>
        _sequence(value.asInstanceOf[ConfigList].asScala.toVector.zipWithIndex) { case (member, index) =>
          val kindpath = s"$memberpath[$index]"
          if (member.valueType() != ConfigValueType.STRING) _cml(kindpath, "expected JSON string kind")
          else member.unwrapped().asInstanceOf[String] match {
            case "text" => Right(StaticValueKind.Text)
            case "number" => Right(StaticValueKind.Number)
            case "boolean" => Right(StaticValueKind.Boolean)
            case "timestamp" => Right(StaticValueKind.Timestamp)
            case "missing" => Right(StaticValueKind.Missing)
            case _ => Left(Diagnostic(ValueKindInvalid, kindpath, "unsupported case-sensitive kind tag"))
          }
        }
      case _ => _cml(memberpath, "required JSON string array")
    }
  }

  private def _containers(input: SectionInput, allowed: Set[String], path: String):
      Either[Diagnostic, Map[String, (LogicalSection, String)]] =
    input.children.zipWithIndex.foldLeft[Either[Diagnostic, Map[String, (LogicalSection, String)]]](Right(Map.empty)) {
      case (result, (child, index)) => result.flatMap { entries =>
        val title = _title(child)
        val childpath = s"$path.sections[$index]"
        if (!allowed.contains(title)) _cml(childpath, "unknown container in this immediate parent")
        else if (entries.contains(title)) _cml(childpath, "repeated container")
        else Right(entries + (title -> (child, childpath)))
      }
    }

  private def _entries[A](containers: Map[String, (LogicalSection, String)], name: String, header: String,
      path: String)(interpret: (LogicalSection, String) => Either[Diagnostic, A]): Either[Diagnostic, Vector[A]] =
    containers.get(name) match {
      case None => Right(Vector.empty)
      case Some((section, containerpath)) => _input(section, containerpath, Set.empty).flatMap { input =>
        _sequence(input.children.zipWithIndex) { case (child, index) =>
          val childpath = s"$containerpath.sections[$index]"
          if (_title(child) != header) _cml(childpath, "unsupported immediate container child")
          else interpret(child, childpath)
        }
      }
    }

  private def _leaf(input: SectionInput, path: String): Either[Diagnostic, Unit] =
    if (input.children.isEmpty) Right(()) else _cml(path + ".sections[0]", "leaf cannot contain sections")

  private def _input(section: LogicalSection, path: String, allowed: Set[String]): Either[Diagnostic, SectionInput] = {
    val blocks = section.blocks.blocks
    val firstchild = blocks.indexWhere(_.isInstanceOf[LogicalSection])
    val prologue = if (firstchild < 0) blocks else blocks.take(firstchild)
    val remainder = if (firstchild < 0) Vector.empty else blocks.drop(firstchild)
    val spill = remainder.zipWithIndex.collectFirst {
      case (paragraph: LogicalParagraph, index) if paragraph.lines.lines.exists(_.text.trim.nonEmpty) => index
      case (block, index) if !block.isInstanceOf[LogicalSection] && !block.isInstanceOf[LogicalParagraph] &&
        block != StartBlock && block != EndBlock => index
    }
    if (spill.nonEmpty) _cml(path, "non-section child spill after first child")
    else _sequence(prologue) {
      case paragraph: LogicalParagraph => Right(paragraph.lines.lines)
      case StartBlock | EndBlock => Right(Vector.empty[LogicalLine])
      case _ => _cml(path + ".prologue", "unsupported prologue block")
    }.flatMap { groups =>
      groups.flatten.filter(_.text.trim.nonEmpty).foldLeft[Either[Diagnostic, Map[String, ConfigValue]]](Right(Map.empty)) {
        case (result, line) => result.flatMap { properties =>
          line.text match {
            case _property_line(key, literal) =>
              val memberpath = path + ".prologue." + key
              if (!allowed.contains(key)) _cml(memberpath, "unknown case-sensitive property")
              else if (properties.contains(key)) _cml(memberpath, "repeated property")
              else if (line.physicalLines.size > 1 || literal.contains('\n') || literal.contains('\r'))
                _cml(memberpath, "multiline literals are not admitted")
              else _literal(literal, memberpath).map(value => properties + (key -> value))
            case _ => _cml(path + ".prologue", "expected one key = literal statement per line")
          }
        }
      }.map(properties => SectionInput(properties, remainder.collect { case child: LogicalSection => child }))
    }
  }

  private def _literal(literal: String, path: String): Either[Diagnostic, ConfigValue] =
    if (!_single_literal.pattern.matcher(literal).matches())
      _cml(path, "expected a single JSON string or string-array literal")
    else try {
      val root = ConfigFactory.parseString("{\"value\":" + literal + "}", _json_options).root()
      if (root.size() != 1 || !root.containsKey("value")) _cml(path, "expected one JSON literal")
      else Right(root.get("value"))
    } catch {
      case _: ConfigException => _cml(path, "invalid JSON scalar literal syntax")
    }

  private def _string(input: SectionInput, key: String, path: String,
      missing: DiagnosticCode = CmlInvalid): Either[Diagnostic, String] =
    input.properties.get(key) match {
      case Some(value) if value.valueType() == ConfigValueType.STRING => Right(value.unwrapped().asInstanceOf[String])
      case Some(_) => _cml(path + ".prologue." + key, "expected JSON string")
      case None => Left(Diagnostic(missing, path + ".prologue." + key, "required property is absent"))
    }

  private def _shape(section: LogicalSection, path: String): Either[Diagnostic, Unit] = {
    if (section == null || section.title == null || section.blocks == null || section.blocks.blocks == null ||
      section.mark == null || section.mark.contains(null) || section.location == null || section.location.contains(null))
      _element(path, "null section or optional AST shape")
    else _title_shape(section, path).flatMap { _ => _sequence(section.blocks.blocks.zipWithIndex) { case (block, index) =>
      val blockpath = s"$path.blocks[$index]"
      block match {
        case null => _element(blockpath, "null block")
        case child: LogicalSection => _shape(child, blockpath)
        case paragraph: LogicalParagraph =>
          if (paragraph.lines == null || paragraph.lines.lines == null) _element(blockpath, "null paragraph lines")
          else _sequence(paragraph.lines.lines) { line =>
            if (line == null || line.text == null || line.physicalLines == null || line.physicalLines.contains(null) ||
              line.location == null || line.location.contains(null)) _element(blockpath, "null logical line shape")
            else Right(())
          }.map(_ => ())
        case _ => Right(())
      }
    }.map(_ => ()) }
  }

  private def _title_shape(section: LogicalSection, path: String): Either[Diagnostic, Unit] =
    try {
      if (section.title.toI18NString == null || section.title.toI18NString.en == null)
        _element(path, "null section title")
      else Right(())
    } catch {
      // The ordinary title wrapper can itself carry a null representation.
      case _: NullPointerException => _element(path, "null section title representation")
    }

  private def _title(section: LogicalSection): String = section.title.toI18NString.en

  private def _sequence[A, B](values: Vector[A])(interpret: A => Either[Diagnostic, B]): Either[Diagnostic, Vector[B]] =
    values.foldLeft[Either[Diagnostic, Vector[B]]](Right(Vector.empty)) { (result, value) =>
      result.flatMap(elements => interpret(value).map(elements :+ _))
    }

  private def _cml[A](path: String, reason: String): Either[Diagnostic, A] = Left(Diagnostic(CmlInvalid, path, reason))
  private def _component[A](path: String, reason: String): Either[Diagnostic, A] = Left(Diagnostic(ComponentInvalid, path, reason))
  private def _element[A](path: String, reason: String): Either[Diagnostic, A] = Left(Diagnostic(ElementInvalid, path, reason))
}
