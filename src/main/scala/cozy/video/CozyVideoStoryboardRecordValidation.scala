package cozy.video

import io.circe.Json

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[video] object CozyVideoStoryboardRecordValidation {
  def handoffPayloadShape(payload: Json): Boolean = {
    val cursor = payload.hcursor
    _json_nonempty_string(payload, "sourcePath") &&
      cursor.downField("storyboardSection").focus.exists(value => value.isNull || value.asString.exists(_.nonEmpty)) &&
      cursor.downField("storyboard").focus.exists(_valid_handoff_storyboard_shape)
  }

  def nonemptyString(json: Json, name: String): Boolean = _json_nonempty_string(json, name)

  def contains(actual: Json, required: Json): Boolean =
    required.asObject match {
      case Some(fields) => actual.asObject.exists { values =>
        fields.toVector.forall { case (name, value) => values(name).exists(contains(_, value)) }
      }
      case None => required.asArray match {
        case Some(values) => actual.asArray.exists(actualvalues =>
          actualvalues.size == values.size && actualvalues.zip(values).forall { case (left, right) => contains(left, right) }
        )
        case None => actual == required
      }
    }

  def ffprobeSummaryValid(summary: Json): Boolean = {
    val format = summary.hcursor.downField("format").focus.filter(_.isObject)
    val duration = format.flatMap(_.hcursor.downField("duration").focus).flatMap { value =>
      value.asNumber.map(_.toDouble).orElse(value.asString.flatMap(text => scala.util.Try(text.toDouble).toOption))
    }
    val video = summary.hcursor.downField("streams").focus.flatMap(_.asArray).exists(_.exists(_.hcursor.get[String]("codec_type").toOption.contains("video")))
    duration.exists(value => java.lang.Double.isFinite(value) && value > 0.0) && video
  }

  private def _valid_handoff_storyboard_shape(storyboard: Json): Boolean = {
    val schema = storyboard.hcursor.get[String]("schema").toOption
    val version = storyboard.hcursor.get[Int]("version").toOption
    val scenes = storyboard.hcursor.downField("scenes").focus.flatMap(_.asArray).map(_.toVector)
    (schema.contains("cozy.video.storyboard.v1") && version.contains(1) || schema.contains("cozy.video.storyboard.v2") && version.contains(2)) &&
      scenes.exists(_.nonEmpty) && scenes.exists(_.forall { scene =>
        _valid_handoff_scene_shape(scene, schema.contains("cozy.video.storyboard.v2"))
      })
  }

  private def _valid_handoff_scene_shape(scene: Json, v2: Boolean): Boolean = {
    val strings = Vector("id", "section", "speaker", "role", "narration", "caption", "transition", "direction")
    val arrays = Vector("pronunciationNotes", "productionInserts", "diagramRefs", "assetRefs")
    scene.asObject.nonEmpty && strings.forall(name => _json_nonempty_string(scene, name)) &&
      scene.hcursor.get[Int]("order").toOption.exists(_ > 0) &&
      Vector("duration", "leadSilence").forall(name => _json_number(scene, name)) &&
      arrays.forall(name => scene.hcursor.downField(name).focus.flatMap(_.asArray).nonEmpty) &&
      _valid_handoff_notes(scene) && _valid_handoff_inserts(scene) &&
      _valid_handoff_string_array(scene, "diagramRefs") && _valid_handoff_string_array(scene, "assetRefs") &&
      scene.hcursor.downField("screen").focus.exists(_valid_handoff_screen_shape(_, v2))
  }

  private def _valid_handoff_notes(scene: Json): Boolean =
    scene.hcursor.downField("pronunciationNotes").focus.flatMap(_.asArray).exists(_.forall(note =>
      _json_nonempty_string(note, "surface") && _json_nonempty_string(note, "reading")
    ))

  private def _valid_handoff_inserts(scene: Json): Boolean =
    scene.hcursor.downField("productionInserts").focus.flatMap(_.asArray).exists(_.forall(insert =>
      Vector("id", "kind", "value").forall(name => _json_nonempty_string(insert, name))
    ))

  private def _valid_handoff_string_array(scene: Json, name: String): Boolean =
    scene.hcursor.downField(name).focus.flatMap(_.asArray).exists(_.forall(_.asString.exists(_.nonEmpty)))

  private def _valid_handoff_screen_shape(screen: Json, v2: Boolean): Boolean =
    if (!v2)
      _json_nonempty_string(screen, "heading") && _json_nonempty_string(screen, "content")
    else screen.hcursor.get[String]("kind").toOption match {
      case Some("text") => _json_nonempty_string(screen, "heading") && _json_nonempty_string(screen, "content")
      case Some("visual-page") => Vector("source", "catalog", "pageId").forall(name => _json_nonempty_string(screen, name))
      case _ => false
    }

  private def _json_nonempty_string(json: Json, name: String): Boolean =
    json.hcursor.get[String](name).toOption.exists(_.nonEmpty)

  private def _json_number(json: Json, name: String): Boolean =
    json.hcursor.downField(name).focus.exists(_.asNumber.nonEmpty)
}
