package cozy.document

import cozy.media.{CozyExplanation, CozyVisualPage}
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep.  4, 2026
 * @version Sep.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentCrossMediaProjection {
  final case class SlidePage(
    id: String,
    storyStepId: String,
    structureId: String,
    ordinal: Int,
    text: String,
    logical: CozyVisualPage.Logical,
    visual: CozyVisualPage.Visual,
    claims: Vector[CozyExplanation.Claim],
    sourceRefs: Vector[String],
    assetRefs: Vector[String]
  )
  final case class StoryboardScene(
    id: String,
    storyStepId: String,
    structureId: String,
    ordinal: Int,
    caption: String,
    logical: CozyVisualPage.Logical,
    visual: CozyVisualPage.Visual,
    claims: Vector[CozyExplanation.Claim],
    sourceRefs: Vector[String],
    assetRefs: Vector[String]
  )
  final case class SlideStepMapping(storyStepId: String, pageIds: Vector[String])
  final case class VideoStepMapping(storyStepId: String, sceneIds: Vector[String])
  final case class Projection(
    contentCoreId: String,
    semanticIdentity: String,
    currentnessIdentity: String,
    storyFlow: CozyDocumentPresentationSemantics.StoryFlow,
    slidePages: Vector[SlidePage],
    storyboardScenes: Vector[StoryboardScene],
    slideMappings: Vector[SlideStepMapping],
    videoMappings: Vector[VideoStepMapping],
    identity: String
  )
  final case class ProjectionFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  def project(value: CozyDocumentPresentationSemantics.Validated): Projection = {
    val steps = value.plan.steps
    val structures = value.structures.groupBy(_.storyStepId)
    val missing = steps.filter(step => structures.getOrElse(step.id, Vector.empty).isEmpty)
    if (missing.nonEmpty)
      _fail("DP-PROJ-001", "$.plan.steps", s"each Plan Step requires one or more Structures; missing ${missing.map(_.id).mkString(", ")}")
    val unknown = value.structures.filterNot(structure => steps.exists(_.id == structure.storyStepId))
    if (unknown.nonEmpty)
      _fail("DP-PROJ-001", "$.structures", s"each Structure must resolve a Plan Step; unresolved ${unknown.map(_.id).mkString(", ")}")

    val pages = steps.flatMap { step =>
      structures.getOrElse(step.id, Vector.empty).sortBy(_.id).flatMap { structure =>
        val visual = _selection(structure, "slides")
        structure.article.visibleText.zipWithIndex.map { case (text, index) =>
          SlidePage(s"slide-${structure.id}-${index + 1}", step.id, structure.id, index + 1, text, structure.logical, visual, step.claims, step.sourceRefs, step.assetRefs)
        }
      }
    }
    val scenes = steps.flatMap { step =>
      structures.getOrElse(step.id, Vector.empty).sortBy(_.id).flatMap { structure =>
        val visual = _selection(structure, "video")
        structure.article.visibleText.zipWithIndex.map { case (text, index) =>
          StoryboardScene(s"scene-${structure.id}-${index + 1}", step.id, structure.id, index + 1, text, structure.logical, visual, step.claims, step.sourceRefs, step.assetRefs)
        }
      }
    }
    val slidemappings = steps.map(step => SlideStepMapping(step.id, pages.filter(_.storyStepId == step.id).map(_.id)))
    val videomappings = steps.map(step => VideoStepMapping(step.id, scenes.filter(_.storyStepId == step.id).map(_.id)))
    _validate(value, pages, scenes, slidemappings, videomappings)
    val provisional = Projection(value.contentCore.id, value.semanticIdentity, value.currentnessIdentity, value.storyFlow, pages, scenes, slidemappings, videomappings, "")
    provisional.copy(identity = projectionIdentity(provisional))
  }

  def canonicalJson(value: Projection): String = _projection_value(value, includeidentity = true).noSpaces

  def projectionIdentity(value: Projection): String = _identity(_projection_value(value, includeidentity = false).noSpaces)

  private def _selection(structure: CozyDocumentPresentationSemantics.Structure, medium: String): CozyVisualPage.Visual =
    structure.visualSelections.filter(_.medium == medium) match {
      case Vector(value) => value.visual
      case _ => _fail("DP-PROJ-001", s"$$.structures.${structure.id}.visualSelections", s"requires exactly one $medium Visual selection")
    }

  private def _validate(
    value: CozyDocumentPresentationSemantics.Validated,
    pages: Vector[SlidePage],
    scenes: Vector[StoryboardScene],
    slidemappings: Vector[SlideStepMapping],
    videomappings: Vector[VideoStepMapping]
  ): Unit = {
    _unique(pages.map(_.id), "$.slidePages", "slide page ID")
    _unique(scenes.map(_.id), "$.storyboardScenes", "storyboard scene ID")
    _coverage(value.plan.steps.map(_.id), pages.map(page => page.storyStepId -> page.id), "$.slideMappings", "slide page")
    _coverage(value.plan.steps.map(_.id), scenes.map(scene => scene.storyStepId -> scene.id), "$.videoMappings", "storyboard scene")
    _structure_coverage(value.structures.map(_.id), pages.map(page => page.structureId -> page.id), "$.slidePages", "slide page")
    _structure_coverage(value.structures.map(_.id), scenes.map(scene => scene.structureId -> scene.id), "$.storyboardScenes", "storyboard scene")
    if (slidemappings.map(_.storyStepId) != value.plan.steps.map(_.id))
      _fail("DP-PROJ-001", "$.slideMappings", "must retain exact Plan Step order")
    if (videomappings.map(_.storyStepId) != value.plan.steps.map(_.id))
      _fail("DP-PROJ-001", "$.videoMappings", "must retain exact Plan Step order")
    if (slidemappings.flatMap(_.pageIds) != pages.map(_.id))
      _fail("DP-PROJ-001", "$.slideMappings", "must resolve every derived slide page exactly once in projection order")
    if (videomappings.flatMap(_.sceneIds) != scenes.map(_.id))
      _fail("DP-PROJ-001", "$.videoMappings", "must resolve every derived storyboard scene exactly once in projection order")
  }

  private def _coverage(stepids: Vector[String], values: Vector[(String, String)], path: String, label: String): Unit = {
    stepids.foreach { stepid =>
      if (!values.exists(_._1 == stepid))
        _fail("DP-PROJ-001", path, s"Plan Step $stepid has no $label projection")
    }
  }

  private def _structure_coverage(structureids: Vector[String], values: Vector[(String, String)], path: String, label: String): Unit = {
    structureids.foreach { structureid =>
      if (!values.exists(_._1 == structureid))
        _fail("DP-PROJ-001", path, s"Structure $structureid has no $label projection")
    }
  }

  private def _unique(values: Vector[String], path: String, label: String): Unit =
    if (values.distinct.size != values.size)
      _fail("DP-PROJ-001", path, s"$label must be unique")

  private def _projection_value(value: Projection, includeidentity: Boolean): Json = {
    val fields = Vector(
      "contentCoreId" -> Json.fromString(value.contentCoreId),
      "semanticIdentity" -> Json.fromString(value.semanticIdentity),
      "currentnessIdentity" -> Json.fromString(value.currentnessIdentity),
      "storyFlow" -> _story_flow_value(value.storyFlow),
      "slidePages" -> Json.fromValues(value.slidePages.map(_slide_page_value)),
      "storyboardScenes" -> Json.fromValues(value.storyboardScenes.map(_storyboard_scene_value)),
      "slideMappings" -> Json.fromValues(value.slideMappings.map(mapping => Json.obj("storyStepId" -> Json.fromString(mapping.storyStepId), "pageIds" -> Json.fromValues(mapping.pageIds.map(Json.fromString))))),
      "videoMappings" -> Json.fromValues(value.videoMappings.map(mapping => Json.obj("storyStepId" -> Json.fromString(mapping.storyStepId), "sceneIds" -> Json.fromValues(mapping.sceneIds.map(Json.fromString)))))
    )
    Json.fromFields(if (includeidentity) fields :+ ("identity" -> Json.fromString(value.identity)) else fields)
  }

  private def _story_flow_value(value: CozyDocumentPresentationSemantics.StoryFlow): Json = Json.obj(
    "id" -> Json.fromString(value.id),
    "transitions" -> Json.fromValues(value.transitions.map(transition => Json.obj(
      "id" -> Json.fromString(transition.id),
      "relationType" -> Json.fromString(transition.relationType),
      "fromStepId" -> Json.fromString(transition.fromStepId),
      "toStepId" -> Json.fromString(transition.toStepId)
    )))
  )

  private def _slide_page_value(value: SlidePage): Json = Json.obj(
    "id" -> Json.fromString(value.id), "storyStepId" -> Json.fromString(value.storyStepId), "structureId" -> Json.fromString(value.structureId),
    "ordinal" -> Json.fromInt(value.ordinal), "text" -> Json.fromString(value.text), "logical" -> _logical_value(value.logical), "visual" -> _visual_value(value.visual),
    "claims" -> Json.fromValues(value.claims.map(_claim_value)), "sourceRefs" -> Json.fromValues(value.sourceRefs.map(Json.fromString)), "assetRefs" -> Json.fromValues(value.assetRefs.map(Json.fromString))
  )

  private def _storyboard_scene_value(value: StoryboardScene): Json = Json.obj(
    "id" -> Json.fromString(value.id), "storyStepId" -> Json.fromString(value.storyStepId), "structureId" -> Json.fromString(value.structureId),
    "ordinal" -> Json.fromInt(value.ordinal), "caption" -> Json.fromString(value.caption), "logical" -> _logical_value(value.logical), "visual" -> _visual_value(value.visual),
    "claims" -> Json.fromValues(value.claims.map(_claim_value)), "sourceRefs" -> Json.fromValues(value.sourceRefs.map(Json.fromString)), "assetRefs" -> Json.fromValues(value.assetRefs.map(Json.fromString))
  )

  private def _logical_value(value: CozyVisualPage.Logical): Json = Json.obj(
    "pattern" -> Json.fromString(value.pattern),
    "nodes" -> Json.fromValues(value.nodes.map(node => Json.obj("id" -> Json.fromString(node.id), "role" -> Json.fromString(node.role), "label" -> Json.fromString(node.label), "sourceRefs" -> Json.fromValues(node.sourceRefs.map(Json.fromString))))),
    "relations" -> Json.fromValues(value.relations.map(relation => Json.obj("id" -> Json.fromString(relation.id), "relationType" -> Json.fromString(relation.relationType), "from" -> Json.fromString(relation.from), "to" -> Json.fromString(relation.to), "sourceRefs" -> Json.fromValues(relation.sourceRefs.map(Json.fromString)))))
  )

  private def _visual_value(value: CozyVisualPage.Visual): Json = Json.obj(
    "pattern" -> Json.fromString(value.pattern),
    "parameters" -> Json.fromValues(value.parameters.map { parameter =>
      val parametervalue = parameter.value match {
        case CozyVisualPage.NodeReference(item) => Json.fromString(item)
        case CozyVisualPage.StringParameter(item) => Json.fromString(item)
        case CozyVisualPage.BooleanParameter(item) => Json.fromBoolean(item)
      }
      Json.obj("name" -> Json.fromString(parameter.name), "value" -> parametervalue)
    })
  )

  private def _claim_value(value: CozyExplanation.Claim): Json = Json.obj(
    "id" -> Json.fromString(value.id), "text" -> Json.fromString(value.text), "emphasis" -> Json.fromString(value.emphasis),
    "sourceRefs" -> Json.fromValues(value.sourceRefs.map(Json.fromString)), "assetRefs" -> Json.fromValues(value.assetRefs.map(Json.fromString))
  )

  private def _identity(value: String): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map(item => f"${item & 0xff}%02x").mkString

  private def _fail(code: String, path: String, reason: String): Nothing = throw ProjectionFault(code, path, reason)
}
