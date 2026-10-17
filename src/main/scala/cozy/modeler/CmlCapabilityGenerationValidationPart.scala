package cozy.modeler

import scala.util.control.NonFatal
import cozy.archive.CozyComponentReleaseCoordinateCodec
import CmlCapabilitySource._
import CmlCapabilityGenerationContext._
import CmlSemanticFoundation.{ModelElementId, Present, Absent}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityGenerationValidationPart {
  private[modeler] def _validate(validated: CmlCapabilityValidation.Validated,
    contexts: Vector[GenerationContext]): Vector[Issue] = {
    if (validated == null) return Vector(Issue(IssueKind.Malformed, "validated", None, Vector.empty, "An original Validated is required."))
    if (contexts == null) return Vector(Issue(IssueKind.Malformed, "contexts", None, Vector.empty, "Explicit contexts are required."))
    val seen = scala.collection.mutable.Map.empty[SnapshotKey, CapabilitySource]
    val covered = scala.collection.mutable.Set.empty[SnapshotKey]
    val contextissues = contexts.zipWithIndex.flatMap { case (context, index) =>
      val path = s"contexts[$index]"
      val lexical = _context(context, path)
      if (lexical.nonEmpty) lexical
      else {
        val key = context.snapshotKey
        val duplicate = seen.get(key).toVector.map(previous =>
          Issue(IssueKind.DuplicateSnapshot, s"$path.snapshotKey", Some(context.source), Vector(previous), "Repeated exact source scope and Component identity."))
        seen.getOrElseUpdate(key, context.source)
        val documents = validated.documents.filter(_.scope == key.scope)
        val selection = if (documents.size != 1)
          Vector(Issue(IssueKind.UnknownSnapshot, s"$path.source", Some(context.source), Vector.empty, "Select exactly one original document scope."))
        else if (documents.head.model.componentCapabilities.isEmpty && documents.head.model.componentDeclarations.isEmpty)
          Vector(Issue(IssueKind.UnknownSnapshot, s"$path.source", Some(context.source), Vector.empty, "The selected document has no Component facts."))
        else { covered += key; Vector.empty }
        duplicate ++ selection
      }
    }
    val coverageissues = validated.documents.zipWithIndex.flatMap { case (document, index) =>
      val declarations = document.model.componentDeclarations.map(_.component).distinct
      val keys = declarations.map(component => SnapshotKey(document.scope, component))
      val missingkeys = keys.filterNot(covered).map { key =>
        val source = document.model.componentDeclarations.find(_.component == key.component).get.source
        Issue(IssueKind.MissingCoverage, s"documents[$index].componentDeclarations", Some(source), Vector.empty, "Every declared Component requires its exact snapshot context.")
      }
      val hasfacts = document.model.componentCapabilities.nonEmpty || declarations.nonEmpty
      val documentissue = if (hasfacts && !covered.exists(_.scope == document.scope)) {
        val source = document.model.componentCapabilities.headOption.map(_.source)
          .orElse(document.model.componentDeclarations.headOption.map(_.source))
        Vector(Issue(IssueKind.MissingCoverage, s"documents[$index]", source, Vector.empty, "The Component document requires an explicit snapshot selection."))
      } else Vector.empty
      missingkeys ++ documentissue
    }
    val compatibilityissues = validated.documents.zipWithIndex.flatMap { case (document, index) =>
      if (!covered.exists(_.scope == document.scope)) Vector.empty
      else document.model.compatibility match {
        case Present(edges) =>
          val firstsources = scala.collection.mutable.Map.empty[(ModelElementId, String, String), CapabilitySource]
          edges.zipWithIndex.flatMap { case (edge, edgeindex) =>
            val key = (edge.identity, edge.requiredVersion, edge.providedVersion)
            val issues = firstsources.get(key).toVector.map(previous =>
              Issue(IssueKind.Malformed, s"documents[$index].compatibility[$edgeindex]", Some(edge.source), Vector(previous),
                "Repeated exact compatibility identity, required version and provided version."))
            firstsources.getOrElseUpdate(key, edge.source)
            issues
          }
        case _: Absent => Vector.empty
      }
    }
    contextissues ++ coverageissues ++ compatibilityissues
  }

  private def _context(context: GenerationContext, path: String): Vector[Issue] = {
    val source = Option(context).flatMap(x => Option(x.source))
    def _issue_(kind: IssueKind, field: String, detail: String): Vector[Issue] =
      Vector(Issue(kind, s"$path.$field", source, Vector.empty, detail))
    def _text_(value: String, field: String): Vector[Issue] =
      if (_valid_text(value)) Vector.empty else _issue_(IssueKind.Malformed, field, "Valid nonempty literal text is required.")
    if (context == null) return _issue_(IssueKind.Malformed, "context", "An explicit generation context is required.")
    val generation = if (context.projectiongeneration == 1) Vector.empty else _issue_(IssueKind.Unsupported, "projectiongeneration", "Only semantic generation 1 is supported.")
    val schema = if (context.schemaversion == 1) Vector.empty else _issue_(IssueKind.Unsupported, "schemaversion", "Only wire schema 1 is supported.")
    val abi = if (context.generatedabiversion == "cncf.component-capability.v1") Vector.empty else _issue_(IssueKind.Unsupported, "generatedabiversion", "Unsupported projection ABI.")
    val producer = if (context.producer == null) _issue_(IssueKind.Malformed, "producer", "An explicit producer is required.")
    else (if (context.producer.id == "cozy") Vector.empty else _issue_(IssueKind.ProducerMismatch, "producer.id", "The actual producer is cozy.")) ++
      (if (context.producer.version == org.simplemodeling.cozy.BuildInfo.version) Vector.empty else
        _issue_(IssueKind.ProducerMismatch, "producer.version", "The producer version must equal the actual Cozy build version."))
    val component = if (context.component == null || context.component.component == null)
      _issue_(IssueKind.Malformed, "component", "An explicit canonical Java Component identity and release are required.")
    else {
      val identity = context.component.component
      val identityissues = try {
        val admitted = CozyComponentReleaseCoordinateCodec.admitIdentity(identity.namespace().value(), identity.localId().value(), path)
        if (admitted.value == identity) Vector.empty else _issue_(IssueKind.Malformed, "component.component", "The canonical Component identity must remain exact.")
      } catch { case NonFatal(_) => _issue_(IssueKind.Malformed, "component.component", "Invalid canonical Component identity.") }
      val releaseissues = try {
        val admitted = CozyComponentReleaseCoordinateCodec.admit(identity.namespace().value(), identity.localId().value(), context.component.version, path)
        if (_valid_text(context.component.version) && admitted.version == context.component.version) Vector.empty
        else _issue_(IssueKind.Malformed, "component.version", "The Component release must be valid exact literal text.")
      } catch { case NonFatal(_) => _issue_(IssueKind.Malformed, "component.version", "Invalid Component release coordinate.") }
      identityissues ++ releaseissues
    }
    val packaged = if (context.packagecoordinate == null) _issue_(IssueKind.Malformed, "packagecoordinate", "An explicit package coordinate is required.")
    else _text_(context.packagecoordinate.coordinate, "packagecoordinate.coordinate") ++ _text_(context.packagecoordinate.version, "packagecoordinate.version")
    val attributed = if (context.source == null) _issue_(IssueKind.Malformed, "source", "An explicit header source is required.")
    else _text_(context.source.modelAuthority, "source.modelAuthority") ++
      (if (_valid_path(context.source.path)) Vector.empty else _issue_(IssueKind.Malformed, "source.path", "A canonical project-relative POSIX source path is required.")) ++
      _text_(context.source.sourceRevision, "source.sourceRevision") ++
      (if (context.source.line != null && context.source.line.forall(_ > 0)) Vector.empty else _issue_(IssueKind.Malformed, "source.line", "Line must be None or positive."))
    generation ++ schema ++ abi ++ producer ++ component ++ packaged ++ attributed
  }
}
