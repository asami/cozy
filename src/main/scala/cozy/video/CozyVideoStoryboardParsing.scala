package cozy.video

import io.circe.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep. 2, 2026
 * @version Sep. 2, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoStoryboardParsing { self: CozyVideoStoryboard =>
  private[cozy] final def _storyboard_json(storyboard: Storyboard): Json =
    Json.obj(
      "schema" -> Json.fromString(storyboard.schema),
      "version" -> Json.fromInt(storyboard.version),
      "scenes" -> Json.fromValues(storyboard.scenes.map(_scene_json))
    )

  private[cozy] final def _scene_json(scene: StoryboardScene): Json =
    Json.obj(
      "id" -> Json.fromString(scene.id),
      "order" -> Json.fromInt(scene.order),
      "section" -> Json.fromString(scene.section),
      "speaker" -> Json.fromString(scene.speaker),
      "role" -> Json.fromString(scene.role),
      "narration" -> Json.fromString(scene.narration),
      "screen" -> _screen_json(scene.screen),
      "caption" -> Json.fromString(scene.caption),
      "duration" -> Json.fromBigDecimal(_normalized_decimal(scene.duration)),
      "leadSilence" -> Json.fromBigDecimal(_normalized_decimal(scene.leadSilence)),
      "transition" -> Json.fromString(scene.transition),
      "productionInserts" -> Json.fromValues(scene.productionInserts.map(_production_insert_json)),
      "diagramRefs" -> Json.fromValues(scene.diagramRefs.map(Json.fromString)),
      "assetRefs" -> Json.fromValues(scene.assetRefs.map(Json.fromString)),
      "pronunciationNotes" -> Json.fromValues(scene.pronunciationNotes.map(_pronunciation_note_json)),
      "direction" -> Json.fromString(scene.direction)
    )

  private[cozy] final def _production_insert_json(insert: StoryboardProductionInsert): Json =
    Json.obj(
      "id" -> Json.fromString(insert.id),
      "kind" -> Json.fromString(insert.kind),
      "value" -> Json.fromString(insert.value)
    )

  private[cozy] final def _pronunciation_note_json(note: StoryboardPronunciationNote): Json =
    Json.obj(
      "surface" -> Json.fromString(note.surface),
      "reading" -> Json.fromString(note.reading)
    )

  private[cozy] final def _screen_json(screen: StoryboardScreenValue): Json = screen match {
    case StoryboardScreen(heading, content) =>
      Json.obj(
        "heading" -> Json.fromString(heading),
        "content" -> Json.fromString(content)
      )
    case StoryboardTextScreen(heading, content) =>
      Json.obj(
        "kind" -> Json.fromString("text"),
        "heading" -> Json.fromString(heading),
        "content" -> Json.fromString(content)
      )
    case StoryboardVisualPageScreen(source, catalog, pageid) =>
      Json.obj(
        "kind" -> Json.fromString("visual-page"),
        "source" -> Json.fromString(source),
        "catalog" -> Json.fromString(catalog),
        "pageId" -> Json.fromString(pageid)
      )
  }

  private[cozy] final def _literal_lines(name: String, value: String, indent: String = ""): Vector[String] =
    Vector(s"$name: |") ++ (
      if (value.isEmpty) Vector.empty
      else value.split("\\n", -1).toVector.map(indent + "  " + _)
    )

  private[cozy] final def _json_string(value: String): String = Json.fromString(value).noSpaces

  private[cozy] final def _decimal_text(value: BigDecimal): String = _normalized_decimal(value).bigDecimal.toPlainString

  private[cozy] final def _normalized_decimal(value: BigDecimal): BigDecimal = {
    val normalized = value.bigDecimal.stripTrailingZeros()
    if (normalized.scale() < 0) BigDecimal(normalized.setScale(0)) else BigDecimal(normalized)
  }

  private[cozy] final def _canonical_sha256_hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(x => f"${x & 0xff}%02x").mkString

  private[cozy] final def _is_integer(value: BigDecimal): Boolean =
    value.bigDecimal.stripTrailingZeros().scale() <= 0

  private[cozy] final def _timing_precision(value: BigDecimal): Boolean =
    value.bigDecimal.scale() <= 6
}
