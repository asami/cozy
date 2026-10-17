package cozy.document


/*
 * @since   Sep.  6, 2026
 *  version Sep. 30, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentCrossMediaReceipt {
  final case class CoverageDiagnostic(code: String, path: String, reason: String)

  final case class CoverageResult(satisfied: Boolean, diagnostics: Vector[CoverageDiagnostic]) {
    def isComplete: Boolean = satisfied
  }

  def verifyCoverage(
    validated: CozyDocumentPresentationSemantics.Validated,
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): CoverageResult = {
    val diagnostics =
      _rendering_diagnostics(rendered) ++
        _step_diagnostics(validated, projection, rendered) ++
        _structure_diagnostics(validated, projection, rendered) ++
        _unprojected_diagnostics(validated, projection)
    CoverageResult(diagnostics.isEmpty, diagnostics)
  }

  def coverage(
    validated: CozyDocumentPresentationSemantics.Validated,
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): CoverageResult = verifyCoverage(validated, projection, rendered)

  private def _rendering_diagnostics(
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Vector[CoverageDiagnostic] = Vector(
    if (rendered.rendererIdentity != CozyDocumentCrossMediaConfirmationHtml._renderer_identity) Some(_diagnostic("DP-COV-INCOMPATIBLE", "$.rendered.rendererIdentity", "confirmation HTML uses an incompatible renderer")) else None,
    if (rendered.profileIdentity != CozyDocumentCrossMediaConfirmationHtml._profile_identity) Some(_diagnostic("DP-COV-INCOMPATIBLE", "$.rendered.profileIdentity", "confirmation HTML uses an incompatible renderer profile")) else None
  ).flatten

  private def _step_diagnostics(
    validated: CozyDocumentPresentationSemantics.Validated,
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Vector[CoverageDiagnostic] = validated.plan.steps.flatMap { step =>
    val slidemappings = projection.slideMappings.filter(_.storyStepId == step.id)
    val videomappings = projection.videoMappings.filter(_.storyStepId == step.id)
    val mappingdiagnostics =
      _mapping_diagnostics(slidemappings.map(_.pageIds), projection.slidePages.filter(_.storyStepId == step.id).map(_.id), s"$$.slideMappings.${step.id}", "slide page") ++
        _mapping_diagnostics(videomappings.map(_.sceneIds), projection.storyboardScenes.filter(_.storyStepId == step.id).map(_.id), s"$$.videoMappings.${step.id}", "video scene")
    val stepdiagnostics =
      (if (slidemappings.isEmpty) Vector(_diagnostic("DP-COV-MISSING", s"$$.slideMappings.${step.id}", s"Plan Step ${step.id} is missing from slide coverage"))
       else if (slidemappings.size > 1) Vector(_diagnostic("DP-COV-AMBIGUOUS", s"$$.slideMappings.${step.id}", s"Plan Step ${step.id} has ambiguous slide mappings"))
       else if (slidemappings.head.pageIds.isEmpty) Vector(_diagnostic("DP-COV-MISSING", s"$$.slideMappings.${step.id}", s"Plan Step ${step.id} has no slide page IDs")) else Vector.empty) ++
        (if (videomappings.isEmpty) Vector(_diagnostic("DP-COV-MISSING", s"$$.videoMappings.${step.id}", s"Plan Step ${step.id} is missing from video coverage"))
         else if (videomappings.size > 1) Vector(_diagnostic("DP-COV-AMBIGUOUS", s"$$.videoMappings.${step.id}", s"Plan Step ${step.id} has ambiguous video mappings"))
         else if (videomappings.head.sceneIds.isEmpty) Vector(_diagnostic("DP-COV-MISSING", s"$$.videoMappings.${step.id}", s"Plan Step ${step.id} has no video scene IDs")) else Vector.empty)
    val htmlmissing = if (rendered.html.contains(step.id)) Vector.empty else Vector(_diagnostic("DP-COV-MISSING", s"$$.rendered.html.${step.id}", s"Plan Step ${step.id} is missing from confirmation HTML"))
    stepdiagnostics ++ mappingdiagnostics ++ htmlmissing
  }

  private def _mapping_diagnostics(
    mappings: Vector[Vector[String]],
    projectedids: Vector[String],
    path: String,
    label: String
  ): Vector[CoverageDiagnostic] = mappings.headOption.toVector.flatMap { mapping =>
    val duplicated = mapping.groupBy(identity).collect { case (id, values) if values.size > 1 => id }.toVector.sorted
    val unknown = mapping.filterNot(projectedids.contains).distinct.sorted
    val missing = projectedids.filterNot(mapping.contains)
    (if (duplicated.nonEmpty) Vector(_diagnostic("DP-COV-AMBIGUOUS", path, s"$label mapping repeats ${duplicated.mkString(", ")}")) else Vector.empty) ++
      (if (unknown.nonEmpty) Vector(_diagnostic("DP-COV-UNPROJECTED", path, s"$label mapping names unknown values ${unknown.mkString(", ")}")) else Vector.empty) ++
      (if (missing.nonEmpty) Vector(_diagnostic("DP-COV-MISSING", path, s"$label mapping omits ${missing.mkString(", ")}")) else Vector.empty)
  }

  private def _structure_diagnostics(
    validated: CozyDocumentPresentationSemantics.Validated,
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Vector[CoverageDiagnostic] = validated.structures.sortBy(_.id).flatMap { structure =>
    val pages = projection.slidePages.filter(_.structureId == structure.id)
    val scenes = projection.storyboardScenes.filter(_.structureId == structure.id)
    val marker = "id=\"article-section-" + structure.id + "\""
    val mappedids = pages.map(_.id) ++ scenes.map(_.id)
    val mappingdiagnostics = mappedids.filterNot(rendered.html.contains).distinct.map { id =>
      _diagnostic("DP-COV-MISSING", s"$$.rendered.html.$id", s"Structure ${structure.id} mapping $id is missing from confirmation HTML")
    }
    val markercount = _occurrences(rendered.html, marker)
    val markerdiagnostics = if (markercount == 0)
      Vector(_diagnostic("DP-COV-MISSING", s"$$.rendered.html.${structure.id}", s"Structure ${structure.id} is missing from confirmation HTML"))
    else if (markercount > 1)
      Vector(_diagnostic("DP-COV-AMBIGUOUS", s"$$.rendered.html.${structure.id}", s"Structure ${structure.id} occurs ambiguously in confirmation HTML"))
    else Vector.empty
    val pageassociationdiagnostics = pages.collect {
      case page if page.storyStepId != structure.storyStepId =>
        _diagnostic(
          "DP-COV-INCOMPATIBLE",
          s"$$.slidePages.${page.id}.structureId",
          s"slide page ${page.id} associates Structure ${structure.id} with Plan Step ${page.storyStepId}; declared Structure Plan Step is ${structure.storyStepId}"
        )
    }
    val sceneassociationdiagnostics = scenes.collect {
      case scene if scene.storyStepId != structure.storyStepId =>
        _diagnostic(
          "DP-COV-INCOMPATIBLE",
          s"$$.storyboardScenes.${scene.id}.structureId",
          s"video scene ${scene.id} associates Structure ${structure.id} with Plan Step ${scene.storyStepId}; declared Structure Plan Step is ${structure.storyStepId}"
        )
    }
    val pagediagnostics = if (pages.isEmpty)
      Vector(_diagnostic("DP-COV-MISSING", s"$$.slidePages.${structure.id}", s"Structure ${structure.id} is missing from slide coverage")) else Vector.empty
    val scenediagnostics = if (scenes.isEmpty)
      Vector(_diagnostic("DP-COV-MISSING", s"$$.storyboardScenes.${structure.id}", s"Structure ${structure.id} is missing from video coverage")) else Vector.empty
    pagediagnostics ++ scenediagnostics ++ markerdiagnostics ++ mappingdiagnostics ++ pageassociationdiagnostics ++ sceneassociationdiagnostics
  }

  private def _unprojected_diagnostics(
    validated: CozyDocumentPresentationSemantics.Validated,
    projection: CozyDocumentCrossMediaProjection.Projection
  ): Vector[CoverageDiagnostic] = {
    val stepids = validated.plan.steps.map(_.id).toSet
    val structureids = validated.structures.map(_.id).toSet
    val pageitems = projection.slidePages.collect {
      case page if !stepids.contains(page.storyStepId) => _diagnostic("DP-COV-UNPROJECTED", s"$$.slidePages.${page.id}.storyStepId", s"slide page ${page.id} names unprojected Plan Step ${page.storyStepId}")
      case page if !structureids.contains(page.structureId) => _diagnostic("DP-COV-UNPROJECTED", s"$$.slidePages.${page.id}.structureId", s"slide page ${page.id} names unprojected Structure ${page.structureId}")
    }
    val sceneitems = projection.storyboardScenes.collect {
      case scene if !stepids.contains(scene.storyStepId) => _diagnostic("DP-COV-UNPROJECTED", s"$$.storyboardScenes.${scene.id}.storyStepId", s"video scene ${scene.id} names unprojected Plan Step ${scene.storyStepId}")
      case scene if !structureids.contains(scene.structureId) => _diagnostic("DP-COV-UNPROJECTED", s"$$.storyboardScenes.${scene.id}.structureId", s"video scene ${scene.id} names unprojected Structure ${scene.structureId}")
    }
    val duplicatepages = projection.slidePages.groupBy(_.id).collect { case (id, values) if values.size > 1 => _diagnostic("DP-COV-AMBIGUOUS", s"$$.slidePages.$id", s"slide page ID $id occurs more than once") }.toVector.sortBy(_.path)
    val duplicatescenes = projection.storyboardScenes.groupBy(_.id).collect { case (id, values) if values.size > 1 => _diagnostic("DP-COV-AMBIGUOUS", s"$$.storyboardScenes.$id", s"video scene ID $id occurs more than once") }.toVector.sortBy(_.path)
    val slidemappingitems = projection.slideMappings.collect {
      case mapping if !stepids.contains(mapping.storyStepId) => _diagnostic("DP-COV-UNPROJECTED", s"$$.slideMappings.${mapping.storyStepId}.storyStepId", s"slide mapping names unprojected Plan Step ${mapping.storyStepId}")
    }
    val videomappingitems = projection.videoMappings.collect {
      case mapping if !stepids.contains(mapping.storyStepId) => _diagnostic("DP-COV-UNPROJECTED", s"$$.videoMappings.${mapping.storyStepId}.storyStepId", s"video mapping names unprojected Plan Step ${mapping.storyStepId}")
    }
    pageitems ++ sceneitems ++ duplicatepages ++ duplicatescenes ++ slidemappingitems ++ videomappingitems
  }

  private def _diagnostic(code: String, path: String, reason: String): CoverageDiagnostic = CoverageDiagnostic(code, path, reason)

  private def _occurrences(value: String, token: String): Int = {
    if (token.isEmpty) 0
    else Iterator.iterate(value.indexOf(token))(index => if (index < 0) -1 else value.indexOf(token, index + token.length)).takeWhile(_ >= 0).size
  }

}
