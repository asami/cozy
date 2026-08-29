package cozy.media

import org.goldenport.RAISE
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import scala.util.control.NonFatal

/*
 * @since   Aug. 30, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaPdfReviewState {
  final case class Resource(
    id: String,
    role: String,
    language: String,
    publicPath: String,
    mediaType: String,
    output: String,
    sha256: String,
    inputSetSha256: String,
    rendererManifestSha256: Option[String]
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
      val candidate = CozyMediaReceipt.manifest(_manifest_path(plan)).getOrElse(return false)
      _state(plan, candidate, Set.empty, None).exists { state =>
        val path = _state_path(plan)
        _direct_regular_file(path) &&
          Files.readAllBytes(path).sameElements((_json(state).spaces2 + "\n").getBytes(StandardCharsets.UTF_8))
      }
    } catch {
      case NonFatal(_) => false
    }

  def requireCurrent(plan: CozyMedia.Plan, resources: Vector[CozyMedia.ResolvedResource]): Unit = {
    val selected = resources.filter(_qualifying)
    if (selected.nonEmpty && !current(plan))
      _invalid("Selected public PDF resources lack current cozy.media.pdf-review-state.v1 evidence: " + selected.map(_.resource.id).sorted.mkString(", "))
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
      if (!CozyMediaReceipt.current(plan, resolved)) false
      else {
        _require_summary_current(plan, resolved)
        true
      }
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
            CozyMediaReceipt.current(plan, dependency)
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
    if (entry.path != relative || entry.sha256 != _sha256(output))
      _invalid(s"Media PDF review-state candidate output differs: ${resolved.resource.id}")
    val receipt = entry.receipt.getOrElse(_invalid(s"Media PDF review-state receipt is missing: ${resolved.resource.id}"))
    val renderer =
      if (_direct_summary_slides_pdf(resolved)) Some(_renderer_manifest_sha256(plan, resolved))
      else None
    Resource(entry.id, role, language, publicpath, mediatype, relative, entry.sha256, receipt.inputSetSha256, renderer)
  }

  private def _direct_summary_slides_pdf(resolved: CozyMedia.ResolvedResource): Boolean =
    resolved.resource.build == "summary-slides-pdf" && resolved.resource.summarySlidesPdf.nonEmpty

  private def _role(resolved: CozyMedia.ResolvedResource): String =
    resolved.resource.articleMedia.map(_.role).filter(_roles.contains).getOrElse(
      _invalid(s"Media PDF review-state role is invalid: ${resolved.resource.id}")
    )

  private def _renderer_manifest_sha256(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): String = {
    val configuration = resolved.resource.summarySlidesPdf.getOrElse(
      _invalid(s"Summary-slides PDF review-state configuration is missing: ${resolved.resource.id}")
    )
    val path = plan.descriptorRoot.resolve(configuration.rendererManifest).normalize()
    if (!path.startsWith(plan.descriptorRoot) || !_direct_regular_file(path))
      _invalid(s"Summary-slides PDF review-state renderer manifest must be a direct regular file: ${resolved.resource.id}")
    _sha256(path)
  }

  private def _json(state: State): Json =
    Json.obj(
      "schema" -> Json.fromString(_schema),
      "knowledge" -> Json.fromString(state.knowledge),
      "resources" -> Json.fromValues(state.resources.map(_resource_json))
    )

  private def _resource_json(value: Resource): Json =
    Json.fromFields(Vector(
      "id" -> Json.fromString(value.id),
      "role" -> Json.fromString(value.role),
      "language" -> Json.fromString(value.language),
      "publicPath" -> Json.fromString(value.publicPath),
      "mediaType" -> Json.fromString(value.mediaType),
      "output" -> Json.fromString(value.output),
      "sha256" -> Json.fromString(value.sha256),
      "inputSetSha256" -> Json.fromString(value.inputSetSha256)
    ) ++ value.rendererManifestSha256.map(hash => "rendererManifestSha256" -> Json.fromString(hash)).toVector)

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

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
