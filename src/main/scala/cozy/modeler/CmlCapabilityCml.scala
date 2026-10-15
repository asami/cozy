package cozy.modeler

import CmlCapabilitySource._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityCml {
  def normalize(inputs: Vector[SourceInput]): Either[Vector[SyntaxIssue], Vector[CapabilityDocument]] = {
    val seen = scala.collection.mutable.Set.empty[String]
    val results = inputs.map { input =>
      val context = CmlCapabilityJsonPart.Context(input.scope, Vector.empty, None, "input")
      val scopeissues = _scope_issues(input.scope, context)
      val parserissues = if (input.model.errors.nonEmpty)
        Vector(context._issue(MalformedInput, "The original CML parser reported errors.")) else Vector.empty
      val duplicateissues = if (input.scope != null && !seen.add(input.scope.path))
        Vector(context._child("path")._issue(InvalidSource, "Repeated input path makes source ownership ambiguous.")) else Vector.empty
      val issues = parserissues ++ scopeissues ++ duplicateissues
      if (issues.nonEmpty) Left(issues) else CmlCapabilityCmlSectionsPart._document(input)
    }
    CmlCapabilityJsonPart._sequence(results).map(_.flatten)
  }

  private def _scope_issues(scope: SourceScope, context: CmlCapabilityJsonPart.Context): Vector[SyntaxIssue] =
    if (scope == null) Vector(context._issue(InvalidSource, "An explicit SourceScope is required."))
    else Vector(
      if (_valid_text(scope.modelAuthority)) None else Some(context._child("modelAuthority")._issue(InvalidSource, "A valid literal model authority is required.")),
      if (_valid_path(scope.path)) None else Some(context._child("path")._issue(InvalidSource, "A canonical project-relative POSIX input path is required."))
    ).flatten
}
