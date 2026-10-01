package cozy.media

import org.goldenport.RAISE
import org.goldenport.cli.spec
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import io.circe.{Json, JsonObject}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.time.Instant
import cozy.runtime.CozyCliArgs
import scala.util.control.NonFatal

/*
 * @since   Aug. 25, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaReviewState {
  final case class Snapshot(acceptedAt: String)
  final case class State(current: Option[Snapshot], lastAligned: Option[Snapshot], selectedAuthority: Option[String], sync: String)

  private val _schema = "cozy.media.review-state.v1"
  private val _p_media_file = spec.Parameter.argumentFile("media-file")
  private val _p_target = spec.Parameter.property("target")
  private val _p_authority = spec.Parameter.property("authority")

  def executeAlign(args: List[String]): String = {
    val parsed = CozyCliArgs.parseStrict(_p_media_file, _p_target, _p_authority)(args)
    val descriptor = parsed.argument("media-file").map(CozyCliArgs.toPath).getOrElse(_invalid("Missing media descriptor"))
    val target = parsed.requiredProperty("target")
    val authority = parsed.requiredProperty("authority")
    val plan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor, target = Some(target)))
    val resource = plan.resources.find(_.resource.id == target).filter(_.resource.build == "presentation").getOrElse(
      _invalid(s"Media review target is not a presentation resource: $target")
    )
    align(plan, resource, authority)
    s"Cozy Media Review Align\nstatus: aligned\ntarget: $target\nauthority: $authority"
  }

  def refresh(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    val path = _path(plan, resolved)
    write(path, _refreshed_state(plan, resolved, load(path)))
  }

  /** Validates and serializes a refreshed state without modifying its target. */
  private[cozy] def prepareRefresh(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): CozyMediaReceipt.PreparedDocument = {
    val path = _path(plan, resolved)
    CozyMediaReceipt.PreparedDocument(path, (_json(_refreshed_state(plan, resolved, load(path))).spaces2 + "\n").getBytes(StandardCharsets.UTF_8))
  }

  def align(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, authority: String): Unit = {
    if (!Set("article", "slide-ir").contains(authority))
      _invalid("Media review authority must be article or slide-ir")
    CozyMediaPresentation.requireCurrent(plan, resolved, requireReviewState = false)
    val path = _path(plan, resolved)
    val current = load(path).current.getOrElse(_invalid("Media review state has no current deterministic evidence"))
    val expected = CozyMediaPresentation.reviewSnapshot(plan, resolved)
    if (!_not_after(expected, current))
      _invalid("Media review state current generation is stale")
    val accepted = current.copy(acceptedAt = Instant.now().toString)
    write(path, State(Some(current), Some(accepted), Some(authority), "aligned"))
  }

  def requireAligned(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    val path = _path(plan, resolved)
    val state = load(path)
    val current = state.current.getOrElse(_invalid("Media review state has no current evidence"))
    val aligned = state.lastAligned.getOrElse(_invalid("Media review state has no explicit alignment"))
    val expected = CozyMediaPresentation.reviewSnapshot(plan, resolved)
    if (state.sync != "aligned" || state.selectedAuthority.isEmpty || !_not_after(expected, current) || !_not_after(current, aligned))
      _invalid("Media review state is stale or has not been explicitly aligned")
  }

  def scaffold(path: Path): Unit =
    write(path, State(None, None, None, "stale"))

  def load(path: Path): State = {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
      State(None, None, None, "stale")
    else if (!_direct_regular_file(path))
      _invalid(s"Media review state must be a direct regular non-symlink file: $path")
    else {
      val json = try StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take catch {
        case NonFatal(_) => _invalid(s"Media review state must be a supported YAML or JSON document: $path")
      }
      _state(json)
    }
  }

  def write(path: Path, state: State): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, _json(state).spaces2 + "\n", StandardCharsets.UTF_8)
  }

  private def _path(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Path = {
    val value = resolved.resource.presentation.getOrElse(_invalid(s"Presentation resource requires configuration: ${resolved.resource.id}")).reviewState
    val path = plan.descriptorRoot.resolve(value).normalize()
    if (!path.startsWith(plan.descriptorRoot))
      _invalid(s"Media review state escapes descriptor root: ${resolved.resource.id}")
    path
  }

  private def _refreshed_state(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, previous: State): State = {
    val current = CozyMediaPresentation.reviewSnapshot(plan, resolved)
    val aligned = previous.lastAligned.exists(snapshot => _not_after(current, snapshot))
    State(Some(current), previous.lastAligned, previous.selectedAuthority, if (aligned) "aligned" else "stale")
  }

  private def _state(value: Json): State = {
    val objectvalue = _object(value, "Media review state")
    _exact_keys(objectvalue, Set("schema", "current", "last_aligned", "selected_authority", "sync"), "Media review state")
    if (_string(objectvalue, "schema", "Media review state") != _schema)
      _invalid(s"Media review state schema must be exactly ${_schema}")
    val current = _nullable_snapshot(objectvalue, "current")
    val aligned = _nullable_snapshot(objectvalue, "last_aligned")
    val authority = _nullable_string(objectvalue, "selected_authority")
    authority.foreach { value =>
      if (!Set("article", "slide-ir").contains(value)) _invalid("Media review selected_authority must be article or slide-ir")
    }
    val sync = _string(objectvalue, "sync", "Media review state")
    if (!Set("aligned", "stale").contains(sync)) _invalid("Media review sync must be aligned or stale")
    if (sync == "aligned" && (current.isEmpty || aligned.isEmpty || authority.isEmpty || !_not_after(current.get, aligned.get)))
      _invalid("Media review aligned state requires current generation not newer than last_aligned, and selected_authority")
    State(current, aligned, authority, sync)
  }

  private def _snapshot(value: Json): Snapshot = {
    val objectvalue = _object(value, "Media review snapshot")
    _exact_keys(objectvalue.remove("inputSetSha256").remove("reviewManifestSha256").remove("artifactSetSha256"), Set("acceptedAt"), "Media review snapshot")
    val accepted = _string(objectvalue, "acceptedAt", "Media review snapshot")
    try Instant.parse(accepted) catch { case NonFatal(_) => _invalid("Media review acceptedAt must be UTC ISO-8601") }
    if (!accepted.endsWith("Z")) _invalid("Media review acceptedAt must be UTC ISO-8601")
    Snapshot(accepted)
  }

  private def _nullable_snapshot(value: JsonObject, field: String): Option[Snapshot] =
    value(field) match {
      case Some(json) if json.isNull => None
      case Some(json) => Some(_snapshot(json))
      case None => _invalid(s"Media review state requires $field")
    }

  private def _nullable_string(value: JsonObject, field: String): Option[String] =
    value(field) match {
      case Some(json) if json.isNull => None
      case Some(json) => Some(json.asString.filter(x => x.nonEmpty && x == x.trim).getOrElse(_invalid(s"Media review state.$field must be a non-empty exact string")))
      case None => _invalid(s"Media review state requires $field")
    }

  private def _json(state: State): Json =
    Json.obj(
      "schema" -> Json.fromString(_schema),
      "current" -> state.current.map(_snapshot_json).getOrElse(Json.Null),
      "last_aligned" -> state.lastAligned.map(_snapshot_json).getOrElse(Json.Null),
      "selected_authority" -> state.selectedAuthority.map(Json.fromString).getOrElse(Json.Null),
      "sync" -> Json.fromString(state.sync)
    )

  private def _snapshot_json(value: Snapshot): Json =
    Json.obj(
      "acceptedAt" -> Json.fromString(value.acceptedAt)
    )

  private def _not_after(left: Snapshot, right: Snapshot): Boolean =
    !Instant.parse(left.acceptedAt).isAfter(Instant.parse(right.acceptedAt))

  private def _object(value: Json, label: String): JsonObject = value.asObject.getOrElse(_invalid(s"$label must be an object"))

  private def _exact_keys(value: JsonObject, expected: Set[String], label: String): Unit =
    if (value.keys.toSet != expected) _invalid(s"$label requires exactly: ${expected.toVector.sorted.mkString(", ")}")

  private def _string(value: JsonObject, field: String, label: String): String =
    value(field).flatMap(_.asString).filter(x => x.nonEmpty && x == x.trim).getOrElse(_invalid(s"$label.$field must be a non-empty exact string"))

  private def _direct_regular_file(path: Path): Boolean =
    path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
