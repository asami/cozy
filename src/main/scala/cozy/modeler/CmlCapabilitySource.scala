package cozy.modeler

import org.goldenport.kaleidox.{Model => KaleidoxModel}
import CmlCapabilityModel.CapabilityModel

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilitySource {
  final case class CapabilitySource(modelAuthority: String, path: String, sourceRevision: String, line: Option[Int])
  sealed abstract class CapabilityOrigin
  case object Declared extends CapabilityOrigin
  final case class Derived(ruleId: String, inputs: Vector[CapabilitySource]) extends CapabilityOrigin

  final case class SourceScope(modelAuthority: String, path: String)
  final case class SourceInput(scope: SourceScope, model: KaleidoxModel)
  // A document is parsed input, not an admission certificate.
  final case class CapabilityDocument(scope: SourceScope, model: CapabilityModel)

  sealed abstract class SyntaxIssueKind
  case object MalformedInput extends SyntaxIssueKind
  case object InvalidStructure extends SyntaxIssueKind
  case object InvalidField extends SyntaxIssueKind
  case object InvalidSource extends SyntaxIssueKind
  case object UnsupportedSyntax extends SyntaxIssueKind
  final case class SyntaxIssue(
    kind: SyntaxIssueKind,
    fieldPath: String,
    scope: SourceScope,
    sectionPath: Vector[String],
    line: Option[Int],
    message: String
  )

  private[modeler] def _valid_text(value: String): Boolean = {
    if (value == null || value.isEmpty) false
    else {
      val points = value.codePoints().toArray
      def _space_(point: Int): Boolean = Character.isWhitespace(point) || Character.isSpaceChar(point)
      !_space_(points.head) && !_space_(points.last) && !points.exists(Character.isISOControl)
    }
  }

  private[modeler] def _valid_path(value: String): Boolean =
    value != null && value.nonEmpty && !value.codePoints().toArray.exists(Character.isISOControl) &&
      !value.startsWith("/") && !value.contains("\\") &&
      !value.matches("(?s)^[A-Za-z][A-Za-z0-9+.-]*:.*") &&
      value.split("/", -1).forall(x => x.nonEmpty && x != "." && x != "..")
}
