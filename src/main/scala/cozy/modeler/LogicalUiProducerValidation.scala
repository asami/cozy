package cozy.modeler

import cozy.ui.CozyLogicalUiRuntime.{SourceProvenance, ContentRole}
import LogicalUiProducer._
import LogicalUiProducer.DiagnosticCode._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
/** Ordered static admission before any consumer constructor mapping. */
private[modeler] object LogicalUiProducerValidation {
  // Target resolution follows complete local validation, so no invalid element is dereferenced.
  def validate(component: SourceComponent): Either[Diagnostic, Unit] =
    _component(component, "logicalUi").map(Left(_)).getOrElse(Right(()))

  private val _keywords = Set(
    "abstract", "case", "catch", "class", "def", "do", "else", "enum", "export", "extends",
    "false", "final", "finally", "for", "forSome", "given", "if", "implicit", "import",
    "lazy", "match", "new", "null", "object", "override", "package", "private", "protected",
    "return", "sealed", "super", "then", "this", "throw", "trait", "true", "try", "type",
    "val", "var", "while", "with", "yield", "end", "extension", "inline", "opaque", "open",
    "transparent", "using", "derives", "infix", "erased", "as"
  )

  private def _error(code: DiagnosticCode, path: String, reason: String): Option[Diagnostic] =
    Some(Diagnostic(code, path, reason))

  private def _valid_id(value: String): Boolean =
    value != null && value.nonEmpty && value == value.trim

  private def _id(value: String, path: String): Option[Diagnostic] =
    if (_valid_id(value)) None
    else _error(IdInvalid, path, "expected nonempty exactly trimmed opaque text")

  private def _text(value: String, path: String): Option[Diagnostic] =
    if (value != null && value.trim.nonEmpty) None
    else _error(TextInvalid, path, "expected nonblank display text")

  private def _component(component: SourceComponent, path: String): Option[Diagnostic] =
    if (component == null) _error(ElementInvalid, path, "null source component")
    else _owner(component.owner, path + ".owner").orElse {
      if (component.definitions == null) _error(ElementInvalid, path + ".definitions", "null definition vector")
      else if (component.definitions.isEmpty)
        _error(DefinitionsMissing, path + ".definitions", "present component requires definitions")
      else _provenance(component.provenance, path + ".provenance").orElse {
        component.definitions.iterator.zipWithIndex.flatMap { case (definition, index) =>
          _definition(definition, s"$path.definitions[$index]")
        }.toStream.headOption
      }.orElse(_duplicate_ids(component.definitions.map(_.screenId), path + ".definitions", "screenId"))
        .orElse {
          component.definitions.iterator.zipWithIndex.flatMap { case (definition, index) =>
            definition.screen match {
              case list: ListScreen =>
                _target(list.detailTarget, definition.screenId, component.definitions,
                  s"$path.definitions[$index].screen.detailTarget")
              case _: DetailScreen => None
            }
          }.toStream.headOption
        }
    }

  private def _owner(owner: ComponentOwner, path: String): Option[Diagnostic] =
    if (owner == null) _error(ComponentInvalid, path, "explicit component owner is required")
    else if (owner.componentName == null || !owner.componentName.matches("[A-Z][A-Za-z0-9]*"))
      _error(ComponentInvalid, path + ".componentName", "expected Scala component stem")
    else if (owner.packageName == null || owner.packageName.isEmpty ||
      !owner.packageName.split("\\.", -1).forall(segment =>
        segment.matches("[A-Za-z][A-Za-z0-9]*") && !_keywords.contains(segment)))
      _error(ComponentInvalid, path + ".packageName", "expected explicit dot-separated Scala namespace")
    else None

  private def _provenance(provenance: SourceProvenance, path: String): Option[Diagnostic] =
    if (provenance == null) _error(ProvenanceInvalid, path, "source provenance is required")
    else Vector("producerId" -> provenance.producerId, "sourceId" -> provenance.sourceId,
      "revision" -> provenance.revision).collectFirst {
      case (member, value) if !_valid_id(value) =>
        Diagnostic(ProvenanceInvalid, path + "." + member, "invalid opaque source provenance")
    }

  private def _definition(definition: Definition, path: String): Option[Diagnostic] =
    if (definition == null) _error(ElementInvalid, path, "null definition")
    else _id(definition.screenId, path + ".screenId")
      .orElse(_id(definition.definitionRevision, path + ".definitionRevision"))
      .orElse(_id(definition.viewBindingId, path + ".viewBindingId"))
      .orElse {
        if (definition.screen == null) _error(ElementInvalid, path + ".screen", "null screen")
        else _screen(definition.screen, path + ".screen")
      }

  private def _screen(screen: Screen, path: String): Option[Diagnostic] = screen match {
    case list: ListScreen =>
      _text(list.title, path + ".title")
        .orElse(_field_structure(list.itemFields, path + ".itemFields"))
        .orElse(_action_structure(list.actions, path + ".actions"))
        .orElse(_action_structure(list.itemActions, path + ".itemActions"))
        .orElse(_fields(list.itemFields, path + ".itemFields"))
        .orElse(_actions(list.actions, path + ".actions"))
        .orElse(_actions(list.itemActions, path + ".itemActions"))
    case detail: DetailScreen =>
      _text(detail.title, path + ".title")
        .orElse(_section_structure(detail.sections, path + ".sections"))
        .orElse(_action_structure(detail.actions, path + ".actions"))
        .orElse(detail.sections.iterator.zipWithIndex.flatMap { case (section, index) =>
          _section(section, s"$path.sections[$index]")
        }.toStream.headOption)
        .orElse(_actions(detail.actions, path + ".actions"))
  }

  private def _duplicate_ids(ids: Vector[String], path: String, member: String): Option[Diagnostic] =
    ids.indices.find(index => ids.take(index).contains(ids(index))).map { index =>
      Diagnostic(DuplicateId, s"$path[$index].$member", "duplicate within declared identity scope")
    }

  private def _structure[A](values: Vector[A], path: String): Option[Diagnostic] =
    if (values == null) _error(ElementInvalid, path, "null collection")
    else values.indices.find(index => values(index) == null).map { index =>
      Diagnostic(ElementInvalid, s"$path[$index]", "null collection element")
    }

  private def _section_structure(sections: Vector[Section], path: String): Option[Diagnostic] =
    _structure(sections, path).orElse(_duplicate_ids(sections.map(_.id), path, "id"))

  private def _field_structure(fields: Vector[Field], path: String): Option[Diagnostic] =
    _structure(fields, path).orElse(_duplicate_ids(fields.map(_.id), path, "id")).orElse {
      fields.indices.find { index =>
        val role = fields(index).role
        role != null && role != ContentRole && fields.take(index).exists(_.role == role)
      }.map(index => Diagnostic(DuplicateRole, s"$path[$index].role", "repeated noncontent role"))
    }

  private def _action_structure(actions: Vector[Action], path: String): Option[Diagnostic] =
    _structure(actions, path).orElse(_duplicate_ids(actions.map(_.id), path, "id"))

  private def _section(section: Section, path: String): Option[Diagnostic] =
    _id(section.id, path + ".id").orElse {
      if (section.title == null || section.title.contains(null))
        _error(ElementInvalid, path + ".title", "null optional title shape")
      else section.title.flatMap(_text(_, path + ".title"))
    }.orElse(_field_structure(section.fields, path + ".fields"))
      .orElse(_fields(section.fields, path + ".fields"))

  private def _fields(fields: Vector[Field], path: String): Option[Diagnostic] =
    fields.iterator.zipWithIndex.flatMap { case (field, index) =>
      val fieldpath = s"$path[$index]"
      _id(field.id, fieldpath + ".id").orElse(_text(field.label, fieldpath + ".label"))
        .orElse {
          if (field.role == null) _error(RoleInvalid, fieldpath + ".role", "presentation role is required")
          else None
        }.orElse {
          if (field.value == null) _error(ElementInvalid, fieldpath + ".value", "null static value binding")
          else _id(field.value.bindingId, fieldpath + ".value.bindingId")
            .orElse(_kinds(field.value.allowedKinds, fieldpath + ".value.allowedKinds"))
            .orElse {
              if (field.role != ContentRole && field.value.allowedKinds != Vector(StaticValueKind.Text))
                _error(RoleInvalid, fieldpath + ".role", "noncontent role requires exactly Text")
              else None
            }
        }
    }.toStream.headOption

  private def _kinds(kinds: Vector[StaticValueKind], path: String): Option[Diagnostic] =
    if (kinds == null) _error(ElementInvalid, path, "null allowed-kind vector")
    else if (kinds.isEmpty) _error(ValueKindInvalid, path, "nonempty allowed-kind vector is required")
    else kinds.indices.collectFirst {
      case index if kinds(index) == null || kinds.take(index).contains(kinds(index)) =>
        Diagnostic(ValueKindInvalid, s"$path[$index]", "kind must be supported, nonnull and unique")
    }

  private def _actions(actions: Vector[Action], path: String): Option[Diagnostic] =
    actions.iterator.zipWithIndex.flatMap { case (action, index) =>
      val actionpath = s"$path[$index]"
      _id(action.id, actionpath + ".id").orElse(_text(action.label, actionpath + ".label"))
        .orElse(_id(action.bindingId, actionpath + ".bindingId"))
    }.toStream.headOption

  private def _target(target: DetailTarget, screenid: String, definitions: Vector[Definition],
    path: String): Option[Diagnostic] =
    if (target == null) _error(DetailTargetInvalid, path, "explicit Detail target is required")
    else if (!_valid_id(target.screenId))
      _error(DetailTargetInvalid, path + ".screenId", "invalid opaque Detail target identity")
    else if (target.screenId == screenid || !definitions.exists(definition =>
      definition.screenId == target.screenId && definition.screen.isInstanceOf[DetailScreen]))
      _error(DetailTargetInvalid, path + ".screenId", "target must resolve to same-component Detail")
    else None
}
