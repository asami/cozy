package cozy.document

import cozy.media.CozyExplanation
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep.  6, 2026
 * @version Sep.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentCrossMediaReceipt {
  final case class Receipt(
    contentCoreId: String,
    contentCoreIdentity: String,
    compositionIdentity: String,
    planIdentity: String,
    explanationCatalogIdentity: String,
    presentationCatalogIdentity: String,
    presentationLogicalCatalogIdentity: String,
    policyIdentity: String,
    sources: Vector[CozyExplanation.SourceDeclaration],
    assets: Vector[CozyExplanation.AssetDeclaration],
    semanticIdentity: String,
    currentnessIdentity: String,
    projectionIdentity: String,
    rendererIdentity: String,
    profileIdentity: String,
    outputIdentity: String,
    identity: String
  ) {
    def outputSha256: String = outputIdentity
  }

  final case class Currentness(isCurrent: Boolean, reason: String)

  final case class CoverageDiagnostic(code: String, path: String, reason: String)

  final case class CoverageResult(satisfied: Boolean, diagnostics: Vector[CoverageDiagnostic]) {
    def isComplete: Boolean = satisfied
  }

  final case class ReceiptFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  def capture(
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Receipt = {
    _require_rendered(projection, rendered)
    val provisional = Receipt(
      projection.contentCoreId,
      projection.contentCoreIdentity,
      projection.compositionIdentity,
      projection.planIdentity,
      projection.explanationCatalogIdentity,
      projection.presentationCatalogIdentity,
      projection.presentationLogicalCatalogIdentity,
      projection.policyIdentity,
      projection.sources.sortBy(_.id),
      projection.assets.sortBy(_.id),
      projection.semanticIdentity,
      projection.currentnessIdentity,
      projection.identity,
      rendered.rendererIdentity,
      rendered.profileIdentity,
      rendered.identity,
      ""
    )
    provisional.copy(identity = _identity(_receipt_value(provisional, includeidentity = false).noSpaces))
  }

  def receipt(
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Receipt = capture(projection, rendered)

  def create(
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Receipt = capture(projection, rendered)

  def canonicalJson(value: Receipt): String = _receipt_value(value, includeidentity = true).noSpaces

  def receiptIdentity(value: Receipt): String = _identity(_receipt_value(value, includeidentity = false).noSpaces)

  def currentness(
    receipt: Receipt,
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Currentness = {
    if (receiptIdentity(receipt) != receipt.identity)
      Currentness(isCurrent = false, "receipt identity changed")
    else {
      val projectionchecks = Vector(
        (receipt.contentCoreId == projection.contentCoreId) -> "Content Core id changed",
        (receipt.contentCoreIdentity == projection.contentCoreIdentity) -> "accepted Core identity changed",
        (receipt.sources == projection.sources.sortBy(_.id)) -> "declared source identities changed",
        (receipt.assets == projection.assets.sortBy(_.id)) -> "declared asset identities changed",
        (receipt.compositionIdentity == projection.compositionIdentity) -> "Composition identity changed",
        (receipt.planIdentity == projection.planIdentity) -> "Plan identity changed",
        (receipt.explanationCatalogIdentity == projection.explanationCatalogIdentity) -> "explanation catalog identity changed",
        (receipt.presentationCatalogIdentity == projection.presentationCatalogIdentity) -> "presentation catalog identity changed",
        (receipt.presentationLogicalCatalogIdentity == projection.presentationLogicalCatalogIdentity) -> "presentation logical catalog identity changed",
        (receipt.policyIdentity == projection.policyIdentity) -> "projection policy identity changed",
        (receipt.semanticIdentity == projection.semanticIdentity) -> "semantic identity changed",
        (receipt.currentnessIdentity == projection.currentnessIdentity) -> "currentness identity changed",
        (receipt.projectionIdentity == projection.identity) -> "projection identity changed"
      )
      projectionchecks.collectFirst { case (false, reason) => Currentness(isCurrent = false, reason) }.getOrElse {
        val renderingchecks = Vector(
          (receipt.rendererIdentity == rendered.rendererIdentity) -> "renderer identity changed",
          (receipt.profileIdentity == rendered.profileIdentity) -> "renderer profile identity changed",
          (receipt.projectionIdentity == rendered.projectionIdentity) -> "rendered projection identity changed",
          (receipt.outputIdentity == rendered.identity) -> "rendered output identity changed",
          (rendered.identity == _identity(rendered.html.getBytes(StandardCharsets.UTF_8))) -> "rendered output SHA-256 does not match exact UTF-8 bytes",
          (rendered.html == CozyDocumentCrossMediaConfirmationHtml.render(projection).html) -> "rendered HTML bytes changed"
        )
        renderingchecks.collectFirst { case (false, reason) => Currentness(isCurrent = false, reason) }
          .getOrElse(Currentness(isCurrent = true, "receipt inputs and exact UTF-8 output are current"))
      }
    }
  }

  def isCurrent(
    receipt: Receipt,
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Boolean = currentness(receipt, projection, rendered).isCurrent

  def verifyCurrent(
    receipt: Receipt,
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Currentness = currentness(receipt, projection, rendered)

  def verifyCoverage(
    validated: CozyDocumentPresentationSemantics.Validated,
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): CoverageResult = {
    val diagnostics =
      _rendering_diagnostics(projection, rendered) ++
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

  private def _require_rendered(
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Unit = {
    if (rendered.rendererIdentity != CozyDocumentCrossMediaConfirmationHtml._renderer_identity)
      _fail("DP-REC-001", "$.rendered.rendererIdentity", "rendering uses an incompatible renderer identity")
    if (rendered.profileIdentity != CozyDocumentCrossMediaConfirmationHtml._profile_identity)
      _fail("DP-REC-001", "$.rendered.profileIdentity", "rendering uses an incompatible renderer profile identity")
    if (rendered.projectionIdentity != projection.identity)
      _fail("DP-REC-002", "$.rendered.projectionIdentity", "rendering does not retain the input Projection identity")
    val outputidentity = _identity(rendered.html.getBytes(StandardCharsets.UTF_8))
    if (rendered.identity != outputidentity)
      _fail("DP-REC-003", "$.rendered.identity", "rendering identity does not equal the exact UTF-8 output SHA-256")
    val expected = CozyDocumentCrossMediaConfirmationHtml.render(projection)
    if (rendered.html != expected.html)
      _fail("DP-REC-004", "$.rendered.html", "rendering is not the deterministic output of the fixed renderer/profile")
    if (rendered.identity != expected.identity)
      _fail("DP-REC-004", "$.rendered.identity", "rendering output identity is incompatible with the fixed renderer/profile")
  }

  private def _rendering_diagnostics(
    projection: CozyDocumentCrossMediaProjection.Projection,
    rendered: CozyDocumentCrossMediaConfirmationHtml.Rendered
  ): Vector[CoverageDiagnostic] = {
    val identitydiagnostics = Vector(
      if (rendered.projectionIdentity != projection.identity) Some(_diagnostic("DP-COV-INCOMPATIBLE", "$.rendered.projectionIdentity", "confirmation HTML does not retain the Projection identity")) else None,
      if (rendered.rendererIdentity != CozyDocumentCrossMediaConfirmationHtml._renderer_identity) Some(_diagnostic("DP-COV-INCOMPATIBLE", "$.rendered.rendererIdentity", "confirmation HTML uses an incompatible renderer identity")) else None,
      if (rendered.profileIdentity != CozyDocumentCrossMediaConfirmationHtml._profile_identity) Some(_diagnostic("DP-COV-INCOMPATIBLE", "$.rendered.profileIdentity", "confirmation HTML uses an incompatible renderer profile identity")) else None,
      if (rendered.identity != _identity(rendered.html.getBytes(StandardCharsets.UTF_8))) Some(_diagnostic("DP-COV-INCOMPATIBLE", "$.rendered.identity", "confirmation HTML identity does not match exact UTF-8 bytes")) else None
    ).flatten
    identitydiagnostics
  }

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
    val pagediagnostics = if (pages.isEmpty)
      Vector(_diagnostic("DP-COV-MISSING", s"$$.slidePages.${structure.id}", s"Structure ${structure.id} is missing from slide coverage")) else Vector.empty
    val scenediagnostics = if (scenes.isEmpty)
      Vector(_diagnostic("DP-COV-MISSING", s"$$.storyboardScenes.${structure.id}", s"Structure ${structure.id} is missing from video coverage")) else Vector.empty
    pagediagnostics ++ scenediagnostics ++ markerdiagnostics ++ mappingdiagnostics
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

  private def _receipt_value(value: Receipt, includeidentity: Boolean): Json = {
    val fields = Vector(
      "contentCoreId" -> Json.fromString(value.contentCoreId),
      "contentCoreIdentity" -> Json.fromString(value.contentCoreIdentity),
      "compositionIdentity" -> Json.fromString(value.compositionIdentity),
      "planIdentity" -> Json.fromString(value.planIdentity),
      "explanationCatalogIdentity" -> Json.fromString(value.explanationCatalogIdentity),
      "presentationCatalogIdentity" -> Json.fromString(value.presentationCatalogIdentity),
      "presentationLogicalCatalogIdentity" -> Json.fromString(value.presentationLogicalCatalogIdentity),
      "policyIdentity" -> Json.fromString(value.policyIdentity),
      "sources" -> Json.fromValues(value.sources.sortBy(_.id).map(_source_value)),
      "assets" -> Json.fromValues(value.assets.sortBy(_.id).map(_asset_value)),
      "semanticIdentity" -> Json.fromString(value.semanticIdentity),
      "currentnessIdentity" -> Json.fromString(value.currentnessIdentity),
      "projectionIdentity" -> Json.fromString(value.projectionIdentity),
      "rendererIdentity" -> Json.fromString(value.rendererIdentity),
      "profileIdentity" -> Json.fromString(value.profileIdentity),
      "outputIdentity" -> Json.fromString(value.outputIdentity)
    )
    Json.fromFields(if (includeidentity) fields :+ ("identity" -> Json.fromString(value.identity)) else fields)
  }

  private def _source_value(value: CozyExplanation.SourceDeclaration): Json = Json.obj(
    "id" -> Json.fromString(value.id),
    "sha256" -> Json.fromString(value.sha256)
  )

  private def _asset_value(value: CozyExplanation.AssetDeclaration): Json = Json.obj(
    "id" -> Json.fromString(value.id),
    "mediaType" -> Json.fromString(value.mediaType),
    "sha256" -> Json.fromString(value.sha256)
  )

  private def _identity(value: String): String = _identity(value.getBytes(StandardCharsets.UTF_8))

  private def _identity(bytes: Array[Byte]): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).map(item => f"${item & 0xff}%02x").mkString

  private def _fail(code: String, path: String, reason: String): Nothing = throw ReceiptFault(code, path, reason)
}
