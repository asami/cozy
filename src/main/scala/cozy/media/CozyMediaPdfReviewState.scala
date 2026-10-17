package cozy.media

import org.goldenport.RAISE
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import scala.util.control.NonFatal
import scala.collection.JavaConverters._

/*
 * @since   Aug. 30, 2026
 *  version Sep. 22, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaPdfReviewState {
  final case class Resource(
    id: String,
    role: String,
    language: String,
    publicPath: String,
    mediaType: String,
    output: String
  )

  final case class State(knowledge: String, resources: Vector[Resource])

  private val _schema = "cozy.media.pdf-review-state.v1"
  private val _path = "target/cozy-media/pdf-review-state.json"
  private val _roles = Set("article_pdf", "summary_slides_pdf")

  /** Prepares a state from the same candidate manifest that will be accepted. */
  def prepare(
    plan: CozyMedia.Plan,
    candidate: CozyMediaReceipt.Manifest,
    selected: Vector[CozyMedia.ResolvedResource],
    captured: CozyMediaReceipt.Captured
  ): Option[CozyMediaReceipt.PreparedDocument] = {
    val selectedids = selected.map(_.resource.id).toSet
    if (selectedids.size != selected.size)
      _invalid("Media PDF review-state selected resources must be unique")
    _state(plan, candidate, selectedids, Some(captured)).map { state =>
      CozyMediaReceipt.PreparedDocument(_state_path(plan), (_json(state).spaces2 + "\n").getBytes(StandardCharsets.UTF_8))
    }
  }

  def current(plan: CozyMedia.Plan): Boolean =
    try {
      val selected = plan.resources.filter(_qualifying)
      if (selected.isEmpty) false
      else { requireCurrent(plan, selected); true }
    } catch { case NonFatal(_) => false }

  def requireCurrent(plan: CozyMedia.Plan, resources: Vector[CozyMedia.ResolvedResource]): Unit =
    resources.filter(_qualifying).sortBy(_.resource.id).foreach { resolved =>
      try {
        CozyMediaReceipt.requireCurrent(plan, resolved)
        val state = _load(_state_path(plan))
        if (state.knowledge != plan.descriptor.knowledge.id) _invalid("state knowledge differs from declaration")
        val candidate = CozyMediaReceipt.manifest(_manifest_path(plan)).getOrElse(_invalid("receipt manifest is missing"))
        val entry = candidate.resources.find(_.id == resolved.resource.id).getOrElse(_invalid("accepted entry is missing"))
        val expected = _resource(plan, resolved, entry)
        val accepted = state.resources.find(_.id == resolved.resource.id).getOrElse(_invalid("selected resource metadata is missing"))
        if (accepted != expected) _invalid("selected role/language/publicPath/mediaType/output metadata differs from declaration")
        _require_summary_current(plan, resolved)
      } catch { case NonFatal(error) =>
        val output = resolved.output.orElse(resolved.source).map(_.toString).getOrElse("undeclared")
        _invalid(s"PDF review currentness failed for resource ${resolved.resource.id}, producer ${resolved.resource.build}, " +
          s"state ${_state_path(plan)}, output $output: ${error.getMessage}; next operation: cozy media build ${plan.descriptorFile} --target ${resolved.resource.id}")
      }
    }

  private def _state(
    plan: CozyMedia.Plan,
    candidate: CozyMediaReceipt.Manifest,
    selectedids: Set[String],
    captured: Option[CozyMediaReceipt.Captured]
  ): Option[State] = {
    if (candidate.knowledge != plan.descriptor.knowledge.id)
      _invalid("Media PDF review-state candidate knowledge does not match descriptor")
    val resources = plan.resources.map(value => value.resource.id -> value).toMap
    val entries = candidate.resources.flatMap { entry =>
      resources.get(entry.id).filter(_qualifying).flatMap { resolved =>
        val current =
          if (selectedids.contains(entry.id))
            _candidate_current(plan, candidate, selectedids, resolved, entry, captured.getOrElse(_invalid("Media PDF review-state candidate receipt evidence is missing")))
          else
            _current(plan, resolved)
        if (current) Some(_resource(plan, resolved, entry)) else None
      }
    }.sortBy(value => (value.role, value.id))
    if (entries.isEmpty) None else Some(State(candidate.knowledge, entries))
  }

  private def _candidate_current(
    plan: CozyMedia.Plan,
    candidate: CozyMediaReceipt.Manifest,
    selectedids: Set[String],
    resolved: CozyMedia.ResolvedResource,
    entry: CozyMediaReceipt.ManifestEntry,
    captured: CozyMediaReceipt.Captured
  ): Boolean = {
    if (!CozyMediaReceipt.candidateCurrent(plan, resolved, entry, captured))
      _invalid(s"Media PDF review-state candidate receipt is not current: ${resolved.resource.id}")
    _require_summary_candidate_current(plan, candidate, selectedids, resolved, captured)
    true
  }

  private def _current(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Boolean =
    try {
      CozyMediaReceipt.requireCurrent(plan, resolved)
      _require_summary_current(plan, resolved)
      true
    } catch {
      case NonFatal(_) => false
    }

  private def _require_summary_current(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit =
    if (_direct_summary_slides_pdf(resolved)) {
      CozyMediaSummarySlidesPdf.requireDependenciesCurrent(plan, resolved)
      CozyMediaSummarySlidesPdf.verifyStructural(plan, resolved)
    }

  private def _require_summary_candidate_current(
    plan: CozyMedia.Plan,
    candidate: CozyMediaReceipt.Manifest,
    selectedids: Set[String],
    resolved: CozyMedia.ResolvedResource,
    captured: CozyMediaReceipt.Captured
  ): Unit =
    if (_direct_summary_slides_pdf(resolved)) {
      val configuration = resolved.resource.summarySlidesPdf.getOrElse(
        _invalid(s"Summary-slides PDF resource requires configuration: ${resolved.resource.id}")
      )
      Vector(configuration.articlePdf, configuration.infographic).foreach { id =>
        val dependency = plan.resources.find(_.resource.id == id).filter(_.resource.id != resolved.resource.id).getOrElse(
          _invalid(s"Summary-slides PDF dependency is not declared: $id")
        )
        val current =
          if (selectedids.contains(dependency.resource.id))
            candidate.resources.find(_.id == dependency.resource.id).exists(entry => CozyMediaReceipt.candidateCurrent(plan, dependency, entry, captured))
          else
            CozyMediaReceipt.currentnessFailure(plan, dependency).isEmpty
        if (!current)
          _invalid(s"Summary-slides PDF target requires current dependency evidence: ${dependency.resource.id}")
      }
      CozyMediaSummarySlidesPdf.verifyStructural(plan, resolved)
    }

  private def _qualifying(resolved: CozyMedia.ResolvedResource): Boolean =
    resolved.resource.kind == "document" && resolved.resource.articleMedia.exists(media => _roles.contains(media.role))

  private def _resource(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, entry: CozyMediaReceipt.ManifestEntry): Resource = {
    val media = resolved.resource.articleMedia.getOrElse(_invalid(s"Media PDF review-state resource articleMedia is missing: ${resolved.resource.id}"))
    val role = _role(resolved)
    val language = resolved.resource.language.filter(Set("ja", "en")).getOrElse(
      _invalid(s"Media PDF review-state resource language must be ja or en: ${resolved.resource.id}")
    )
    val publicpath = _identity(media.publicPath.getOrElse(""), s"Media PDF review-state publicPath ${resolved.resource.id}")
    val mediatype = media.mediaType.filter(_ == "application/pdf").getOrElse(
      _invalid(s"Media PDF review-state mediaType must be application/pdf: ${resolved.resource.id}")
    )
    val output = resolved.output.orElse(if (resolved.resource.build == "prebuilt") resolved.source else None).getOrElse(
      _invalid(s"Media PDF review-state output is missing: ${resolved.resource.id}")
    )
    val relative = _relative(plan, output, s"Media PDF review-state output ${resolved.resource.id}")
    if (entry.path != relative)
      _invalid(s"Media PDF review-state candidate output differs: ${resolved.resource.id}")
    val receipt = entry.receipt.getOrElse(_invalid(s"Media PDF review-state receipt is missing: ${resolved.resource.id}"))
    Resource(entry.id, role, language, _public_path(publicpath), mediatype, relative)
  }

  private def _direct_summary_slides_pdf(resolved: CozyMedia.ResolvedResource): Boolean =
    resolved.resource.build == "summary-slides-pdf" && resolved.resource.summarySlidesPdf.nonEmpty

  private def _role(resolved: CozyMedia.ResolvedResource): String =
    resolved.resource.articleMedia.map(_.role).filter(_roles.contains).getOrElse(
      _invalid(s"Media PDF review-state role is invalid: ${resolved.resource.id}")
    )

  private def _json(state: State): Json =
    Json.obj(
      "schema" -> Json.fromString(_schema),
      "knowledge" -> Json.fromString(state.knowledge),
      "resources" -> Json.fromValues(state.resources.map(_resource_json))
    )

  private def _load(path: Path): State = {
    if (!_direct_regular_file(path)) _invalid("state must be a direct regular file")
    val json = io.circe.parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      error => _invalid(s"state must contain valid JSON: ${error.getMessage}"), identity)
    val root = json.hcursor
    def _string_(cursor: io.circe.HCursor, key: String): String =
      cursor.get[String](key).fold(_ => _invalid(s"state $key must be a string"), value => _identity(value, s"state $key"))
    if (_string_(root, "schema") != _schema) _invalid(s"state schema must be ${_schema}")
    val knowledge = _string_(root, "knowledge")
    val entries = root.get[Vector[Json]]("resources").fold(_ => _invalid("state resources must be an array"), identity).map { value =>
      val cursor = value.hcursor
      val role = _string_(cursor, "role")
      if (!_roles.contains(role)) _invalid("state role must be article_pdf or summary_slides_pdf")
      val language = _string_(cursor, "language")
      if (!Set("ja", "en").contains(language)) _invalid("state language must be ja or en")
      val mediatype = _string_(cursor, "mediaType")
      if (mediatype != "application/pdf") _invalid("state mediaType must be application/pdf")
      val output = _string_(cursor, "output")
      val relative = Path.of(output)
      if (relative.isAbsolute || output.contains("\\") || output.matches("[A-Za-z]:.*") ||
          relative.iterator().asScala.exists(_.toString == "..") || relative.normalize().toString.replace('\\', '/') != output)
        _invalid("state output must be a safe normalized relative path")
      Resource(_string_(cursor, "id"), role, language, _public_path(_string_(cursor, "publicPath")), mediatype, output)
    }
    if (entries.map(_.id).distinct.size != entries.size) _invalid("state resource IDs must be unique")
    State(knowledge, entries)
  }

  private def _public_path(value: String): String = {
    val uri = new java.net.URI(value)
    if (!value.startsWith("/") || value.startsWith("//") || uri.isAbsolute || uri.getRawQuery != null ||
        uri.getRawFragment != null || uri.getRawPath != value || value.split("/").exists(x => x == ".." || x == "."))
      _invalid("state publicPath must be a safe site-public path")
    value
  }

  private def _resource_json(value: Resource): Json =
    Json.obj(
      "id" -> Json.fromString(value.id),
      "role" -> Json.fromString(value.role),
      "language" -> Json.fromString(value.language),
      "publicPath" -> Json.fromString(value.publicPath),
      "mediaType" -> Json.fromString(value.mediaType),
      "output" -> Json.fromString(value.output)
    )

  private def _state_path(plan: CozyMedia.Plan): Path =
    plan.descriptorRoot.resolve(_path).toAbsolutePath.normalize()

  private def _manifest_path(plan: CozyMedia.Plan): Path =
    plan.descriptorRoot.resolve("target/cozy-media/manifest.json").toAbsolutePath.normalize()

  private def _relative(plan: CozyMedia.Plan, path: Path, label: String): String = {
    val relative = plan.descriptorRoot.toAbsolutePath.normalize().relativize(path.toAbsolutePath.normalize()).toString.replace('\\', '/')
    if (relative == ".." || relative.startsWith("../")) _invalid(s"$label escapes descriptor root")
    relative
  }

  private def _identity(value: String, label: String): String =
    if (value == null || value.isEmpty || value != value.trim)
      _invalid(s"$label must be a non-empty exact string")
    else value

  private def _direct_regular_file(path: Path): Boolean =
    path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
