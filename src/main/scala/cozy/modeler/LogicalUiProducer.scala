package cozy.modeler

import cozy.ui.CozyLogicalUiRuntime

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
/** Static source definitions; runtime values and application decisions have no representation here. */
private[modeler] object LogicalUiProducer {
  // Constructor labels are the documented static schema, retained for named arguments.
  final case class ComponentOwner(componentName: String, packageName: String)
  final case class SourceComponent(
    owner: ComponentOwner,
    provenance: CozyLogicalUiRuntime.SourceProvenance,
    definitions: Vector[Definition]
  )
  final case class Definition(
    screenId: String, definitionRevision: String, viewBindingId: String, screen: Screen
  )
  sealed abstract class Screen
  final case class ListScreen(
    title: String, itemFields: Vector[Field], detailTarget: DetailTarget,
    actions: Vector[Action], itemActions: Vector[Action]
  ) extends Screen
  final case class DetailScreen(
    title: String, sections: Vector[Section], actions: Vector[Action]
  ) extends Screen
  final case class DetailTarget(screenId: String)
  final case class Section(id: String, title: Option[String], fields: Vector[Field])
  final case class Field(
    id: String, label: String, role: CozyLogicalUiRuntime.PresentationRole, value: ValueBinding
  )
  final case class ValueBinding(bindingId: String, allowedKinds: Vector[StaticValueKind])
  final case class Action(id: String, label: String, bindingId: String)

  sealed abstract class StaticValueKind(val tag: String)
  object StaticValueKind {
    case object Text extends StaticValueKind("text")
    case object Number extends StaticValueKind("number")
    case object Boolean extends StaticValueKind("boolean")
    case object Timestamp extends StaticValueKind("timestamp")
    case object Missing extends StaticValueKind("missing")
  }

  sealed abstract class DiagnosticCode(val code: String)
  object DiagnosticCode {
    case object CmlInvalid extends DiagnosticCode("LUI76_CML_INVALID")
    case object ComponentInvalid extends DiagnosticCode("LUI76_COMPONENT_INVALID")
    case object DefinitionsMissing extends DiagnosticCode("LUI76_DEFINITIONS_MISSING")
    case object ElementInvalid extends DiagnosticCode("LUI76_ELEMENT_INVALID")
    case object IdInvalid extends DiagnosticCode("LUI76_ID_INVALID")
    case object TextInvalid extends DiagnosticCode("LUI76_TEXT_INVALID")
    case object ProvenanceInvalid extends DiagnosticCode("LUI76_PROVENANCE_INVALID")
    case object ValueKindInvalid extends DiagnosticCode("LUI76_VALUE_KIND_INVALID")
    case object RoleInvalid extends DiagnosticCode("LUI76_ROLE_INVALID")
    case object DuplicateId extends DiagnosticCode("LUI76_DUPLICATE_ID")
    case object DuplicateRole extends DiagnosticCode("LUI76_DUPLICATE_ROLE")
    case object DetailTargetInvalid extends DiagnosticCode("LUI76_DETAIL_TARGET_INVALID")
  }
  final case class Diagnostic(code: DiagnosticCode, path: String, reason: String)
}
