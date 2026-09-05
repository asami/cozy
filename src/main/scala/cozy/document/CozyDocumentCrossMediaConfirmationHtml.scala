package cozy.document

import cozy.media.CozyVisualPage
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep.  5, 2026
 * @version Sep.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentCrossMediaConfirmationHtml {
  private[cozy] val _renderer_identity = "cozy.document.cross-media-confirmation-html.renderer.v1"
  private[cozy] val _profile_identity = "cozy.document.cross-media-confirmation-html.profile.v1"

  final case class Rendered(
    html: String,
    identity: String,
    projectionIdentity: String,
    rendererIdentity: String,
    profileIdentity: String
  ) {
    def outputSha256: String = identity
  }

  def render(value: CozyDocumentCrossMediaProjection.Projection): Rendered = {
    val html = _document(value)
    Rendered(
      html,
      _identity(html.getBytes(StandardCharsets.UTF_8)),
      value.identity,
      _renderer_identity,
      _profile_identity
    )
  }

  private def _document(value: CozyDocumentCrossMediaProjection.Projection): String =
    Vector(
      "<!doctype html>",
      "<html lang=\"en\">",
      "<head>",
      "<meta charset=\"UTF-8\">",
      "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
      "<title>Cozy Document Cross-Media Confirmation</title>",
      _style,
      "</head>",
      "<body>",
      "<main>",
      "<h1>Cozy Document Cross-Media Confirmation</h1>",
      _reader_facing(value),
      _reviewer_diagnostics(value),
      _production_metadata,
      "</main>",
      "</body>",
      "</html>"
    ).mkString("\n") + "\n"

  private def _reader_facing(value: CozyDocumentCrossMediaProjection.Projection): String =
    Vector(
      "<section id=\"reader-facing-content\" aria-labelledby=\"reader-facing-content-heading\">",
      "<h2 id=\"reader-facing-content-heading\">Reader-facing content</h2>",
      _story_flow(value),
      _structure_details(value),
      "</section>"
    ).mkString("\n")

  private def _story_flow(value: CozyDocumentCrossMediaProjection.Projection): String = {
    val transitionrows = value.storyFlow.transitions.map { transition =>
      Vector(transition.id, transition.relationType, transition.fromStepId, transition.toStepId)
    }
    val mappingrows = value.slideMappings.zip(value.videoMappings).map { case (slidemapping, videomapping) =>
      Vector(slidemapping.storyStepId, slidemapping.pageIds.mkString(", "), videomapping.sceneIds.mkString(", "), "complete")
    }
    Vector(
      "<section id=\"story-flow-overview\" aria-labelledby=\"story-flow-overview-heading\">",
      "<h3 id=\"story-flow-overview-heading\">Story Flow overview</h3>",
      s"<p>Story Flow: ${_escape(value.storyFlow.id)}</p>",
      _table("Story transitions", Vector("Transition ID", "Relation Type", "From Step", "To Step"), transitionrows),
      _table("Story Step media mappings", Vector("Story Step", "Slide Page IDs", "Video Scene IDs", "Coverage"), mappingrows),
      "<p>Unprojected content: none.</p>",
      "</section>"
    ).mkString("\n")
  }

  private def _structure_details(value: CozyDocumentCrossMediaProjection.Projection): String = {
    val structureids = (value.slidePages.map(_.structureId) ++ value.storyboardScenes.map(_.structureId)).distinct
    Vector(
      "<section id=\"structure-details\" aria-labelledby=\"structure-details-heading\">",
      "<h3 id=\"structure-details-heading\">Structure details</h3>",
      structureids.map(structureid => _structure(value, structureid)).mkString("\n"),
      "</section>"
    ).mkString("\n")
  }

  private def _structure(value: CozyDocumentCrossMediaProjection.Projection, structureid: String): String = {
    val pages = value.slidePages.filter(_.structureId == structureid)
    val scenes = value.storyboardScenes.filter(_.structureId == structureid)
    val logical = pages.headOption.map(_.logical).orElse(scenes.headOption.map(_.logical)).get
    val slidemappings = pages.map(page => Vector("slide page", page.id, page.text))
    val videomappings = scenes.map(scene => Vector("video scene", scene.id, scene.caption))
    val visualrows = Vector(
      pages.headOption.map(page => Vector("slides", page.visual.pattern)).toVector,
      scenes.headOption.map(scene => Vector("video", scene.visual.pattern)).toVector
    ).flatten
    val traceid = s"article-section-$structureid"
    val headingid = s"$traceid-heading"
    Vector(
      "<article id=\"" + _escape(traceid) + "\" aria-labelledby=\"" + _escape(headingid) + "\">",
      "<h4 id=\"" + _escape(headingid) + "\">" + _escape(traceid) + "</h4>",
      _table("Reader-facing text and media mapping", Vector("Medium", "Mapped ID", "Reader-facing text"), slidemappings ++ videomappings),
      s"<p><strong>Logical Pattern:</strong> ${_escape(logical.pattern)}</p>",
      _table("Logical nodes", Vector("Node ID", "Role", "Label", "Source References"), logical.nodes.map(_node_row)),
      _table("Typed Relations", Vector("Relation ID", "Relation Type", "From", "To", "Source References"), logical.relations.map(_relation_row)),
      _table("Selected Visual Patterns", Vector("Medium", "Visual Pattern"), visualrows),
      _table("Slide Visual parameters", Vector("Parameter", "Typed value"), pages.headOption.map(page => page.visual.parameters.map(_parameter_row)).getOrElse(Vector.empty)),
      _table("Video Visual parameters", Vector("Parameter", "Typed value"), scenes.headOption.map(scene => scene.visual.parameters.map(_parameter_row)).getOrElse(Vector.empty)),
      "</article>"
    ).mkString("\n")
  }

  private def _reviewer_diagnostics(value: CozyDocumentCrossMediaProjection.Projection): String = {
    val structureids = (value.slidePages.map(_.structureId) ++ value.storyboardScenes.map(_.structureId)).distinct
    val mappingrows = value.slideMappings.zip(value.videoMappings).map { case (slidemapping, videomapping) =>
      Vector(slidemapping.storyStepId, slidemapping.pageIds.mkString(", "), videomapping.sceneIds.mkString(", "), "complete")
    }
    val claimrows = structureids.flatMap { structureid =>
      (value.slidePages.filter(_.structureId == structureid).flatMap(_.claims) ++ value.storyboardScenes.filter(_.structureId == structureid).flatMap(_.claims)).distinct.map { claim =>
        Vector(structureid, claim.id, claim.emphasis, claim.text, _references(claim.sourceRefs), _references(claim.assetRefs))
      }
    }
    val sourcerows = structureids.map { structureid =>
      val refs = (value.slidePages.filter(_.structureId == structureid).flatMap(_.sourceRefs) ++ value.storyboardScenes.filter(_.structureId == structureid).flatMap(_.sourceRefs)).distinct
      Vector(structureid, _references(refs))
    }
    val assetrows = structureids.map { structureid =>
      val refs = (value.slidePages.filter(_.structureId == structureid).flatMap(_.assetRefs) ++ value.storyboardScenes.filter(_.structureId == structureid).flatMap(_.assetRefs)).distinct
      Vector(structureid, _references(refs))
    }
    Vector(
      "<section id=\"reviewer-diagnostics\" aria-labelledby=\"reviewer-diagnostics-heading\">",
      "<h2 id=\"reviewer-diagnostics-heading\">Reviewer diagnostics</h2>",
      _table("Projection identities", Vector("Identity", "Value"), Vector(
        Vector("Content Core ID", value.contentCoreId),
        Vector("Semantic identity", value.semanticIdentity),
        Vector("Currentness identity", value.currentnessIdentity),
        Vector("Projection identity", value.identity),
        Vector("Mapping/coverage state", "complete; unprojected content: none")
      )),
      _table("Mapping and coverage diagnostics", Vector("Story Step", "Slide Page IDs", "Video Scene IDs", "Coverage"), mappingrows),
      _table("Claims", Vector("Structure", "Claim ID", "Emphasis", "Claim text", "Source References", "Asset References"), claimrows),
      _table("Source references", Vector("Structure", "References"), sourcerows),
      _table("Asset references", Vector("Structure", "References"), assetrows),
      "</section>"
    ).mkString("\n")
  }

  private val _production_metadata = Vector(
    "<section id=\"production-metadata\" aria-labelledby=\"production-metadata-heading\">",
    "<h2 id=\"production-metadata-heading\">Production metadata</h2>",
    "<p>This read-only semantic confirmation projection carries no narration, timing, transition, animation, layout, or external renderer instructions.</p>",
    _table("Excluded production concerns", Vector("Concern", "Projection state"), Vector(
      Vector("Narration", "not carried by this semantic confirmation projection"),
      Vector("Timing", "not carried by this semantic confirmation projection"),
      Vector("Transition", "not carried by this semantic confirmation projection"),
      Vector("Animation", "not carried by this semantic confirmation projection"),
      Vector("Layout", "not carried by this semantic confirmation projection"),
      Vector("External renderer instructions", "not carried by this semantic confirmation projection")
    )),
    "</section>"
  ).mkString("\n")

  private def _table(caption: String, headers: Vector[String], rows: Vector[Vector[String]]): String = {
    val header = headers.map(value => "<th scope=\"col\">" + _escape(value) + "</th>").mkString
    val body = if (rows.nonEmpty) rows.map { row =>
      val cells = row.map(value => s"<td>${_escape(value)}</td>").mkString
      s"<tr>$cells</tr>"
    }.mkString("\n") else "<tr><td colspan=\"" + headers.size + "\">none</td></tr>"
    s"<table><caption>${_escape(caption)}</caption><thead><tr>$header</tr></thead><tbody>$body</tbody></table>"
  }

  private def _node_row(value: CozyVisualPage.Node): Vector[String] =
    Vector(value.id, value.role, value.label, _references(value.sourceRefs))

  private def _relation_row(value: CozyVisualPage.Relation): Vector[String] =
    Vector(value.id, value.relationType, value.from, value.to, _references(value.sourceRefs))

  private def _parameter_row(value: CozyVisualPage.VisualParameter): Vector[String] =
    Vector(value.name, _parameter_value(value.value))

  private def _parameter_value(value: CozyVisualPage.ParameterValue): String = value match {
    case CozyVisualPage.NodeReference(item) => s"node-reference:$item"
    case CozyVisualPage.StringParameter(item) => s"string:$item"
    case CozyVisualPage.BooleanParameter(item) => s"boolean:$item"
  }

  private def _references(values: Vector[String]): String = if (values.nonEmpty) values.mkString(", ") else "none"

  private def _escape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

  private def _identity(bytes: Array[Byte]): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).map(item => f"${item & 0xff}%02x").mkString

  private val _style =
    """<style>
body { font-family: sans-serif; line-height: 1.5; margin: 2rem; color: #1b1b1b; background: #fff; }
main { max-width: 70rem; margin: 0 auto; }
section, article { border: 1px solid #bbb; padding: 1rem; margin: 1rem 0; }
table { border-collapse: collapse; margin: 1rem 0; width: 100%; }
caption { font-weight: 700; text-align: left; padding: .5rem 0; }
th, td { border: 1px solid #bbb; padding: .45rem; text-align: left; vertical-align: top; }
th { background: #f1f1f1; }
strong { font-weight: 700; }
</style>"""
}
