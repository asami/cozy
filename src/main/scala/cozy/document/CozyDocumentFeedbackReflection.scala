package cozy.document

import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.Locale
import scala.util.control.NonFatal

/*
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentFeedbackReflection {
  private final case class FeedbackChange(target: String, replacement: Json, applicability: String, disposition: String, reason: Option[String])
  private final case class ParsedFeedback(reason: String, changes: Vector[FeedbackChange])
  private final case class ReflectionWrite(target: String, relative: String, destination: Path, content: String)

  private val _slug_pattern = "[a-z0-9][a-z0-9._-]*".r
  private val _feedback_targets = Set("core", "article", "slides", "infographic", "video")
  private val _feedback_applicabilities = Set("applicable", "not-applicable")
  private val _feedback_dispositions = Set("accepted", "rejected", "not-applicable")

  private[cozy] def reflect(project: Path, descriptor: CozyDocumentProject.Descriptor, feedbackValue: String): String = {
    val feedbackpath = _admit_feedback_file(feedbackValue)
    val feedback = _parse_feedback(feedbackpath)
    val writes = _prepare_reflection(project, descriptor, feedback)
    _publish_reflections(project, writes)
    _reflection_result(project, descriptor, feedback.changes)
  }

  private def _admit_feedback_file(value: String): Path = {
    val path = try Paths.get(value).toAbsolutePath.normalize() catch {
      case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "feedback input path is invalid")
    }
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      CozyDocumentProject._failure("DP-PATH-001", "feedback input must be a direct regular non-symlink file")
    val parent = Option(path.getParent).getOrElse(CozyDocumentProject._failure("DP-PATH-001", "feedback input parent must be a direct non-symlink directory"))
    if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
      CozyDocumentProject._failure("DP-PATH-001", "feedback input parent must be a direct non-symlink directory")
    val suffix = Option(path.getFileName).map(_.toString.toLowerCase(Locale.ROOT)).flatMap { name =>
      val index = name.lastIndexOf('.')
      if (index >= 0 && index < name.length - 1) Some(name.substring(index)) else None
    }
    if (!Set(".json", ".yaml", ".yml").contains(suffix.getOrElse("")))
      CozyDocumentProject._failure("DP-CLI-001", "feedback input must have a .json, .yaml, or .yml suffix")
    path
  }

  private def _parse_feedback(path: Path): ParsedFeedback = {
    val value = _load_feedback(path)
    val fields = value.asObject.map(_.toMap).getOrElse(CozyDocumentProject._failure("DP-CLI-001", "feedback batch must be an object"))
    if (fields.keySet != Set("reason", "changes"))
      CozyDocumentProject._failure("DP-CLI-001", "feedback batch requires exactly reason and changes")
    val reason = fields("reason").asString match {
      case Some(text) if text.nonEmpty && text == text.trim => text
      case _ => CozyDocumentProject._failure("DP-CLI-001", "feedback batch reason must be a non-empty trimmed string")
    }
    val changes = fields("changes").asArray match {
      case Some(values) if values.nonEmpty => values.map(_parse_feedback_change)
      case _ => CozyDocumentProject._failure("DP-CLI-001", "feedback batch changes must be a non-empty array")
    }
    val targets = changes.map(_.target)
    if (targets.distinct.size != targets.size)
      CozyDocumentProject._failure("DP-CLI-001", "feedback batch targets must be unique")
    ParsedFeedback(reason, changes)
  }

  private def _load_feedback(path: Path): Json =
    try {
      StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
    } catch {
      case NonFatal(_) => CozyDocumentProject._failure("DP-CLI-001", "feedback input is unreadable or malformed JSON/YAML")
    }

  private def _parse_feedback_change(value: Json): FeedbackChange = {
    val fields = value.asObject.map(_.toMap).getOrElse(CozyDocumentProject._failure("DP-CLI-001", "feedback change must be an object"))
    val target = fields.get("target").flatMap(_.asString).getOrElse(CozyDocumentProject._failure("DP-CLI-001", "feedback change target must be a string"))
    if (!_feedback_targets.contains(target))
      CozyDocumentProject._failure("DP-CLI-001", "feedback change target is unknown")
    val applicability = fields.get("applicability").flatMap(_.asString).getOrElse(CozyDocumentProject._failure("DP-CLI-001", "feedback change applicability must be a string"))
    if (!_feedback_applicabilities.contains(applicability))
      CozyDocumentProject._failure("DP-CLI-001", "feedback change applicability is unknown")
    val disposition = fields.get("disposition").flatMap(_.asString).getOrElse(CozyDocumentProject._failure("DP-CLI-001", "feedback change disposition must be a string"))
    if (!_feedback_dispositions.contains(disposition))
      CozyDocumentProject._failure("DP-CLI-001", "feedback change disposition is unknown")
    val reasonkey = (applicability, disposition) match {
      case ("applicable", "accepted") => None
      case ("applicable", "rejected") => Some("rejectionReason")
      case ("not-applicable", "not-applicable") => Some("notApplicableReason")
      case _ => CozyDocumentProject._failure("DP-CLI-001", "feedback change applicability and disposition are incompatible")
    }
    val expected = Set("target", "replacement", "applicability", "disposition") ++ reasonkey.toSet
    if (fields.keySet != expected)
      CozyDocumentProject._failure("DP-CLI-001", "feedback change has missing or extra fields")
    val reason = reasonkey.map { key =>
      fields(key).asString match {
        case Some(text) if text.nonEmpty && text == text.trim => text
        case _ => CozyDocumentProject._failure("DP-CLI-001", s"$key must be a non-empty trimmed string")
      }
    }
    val replacement = fields("replacement")
    _validate_feedback_replacement(target, replacement)
    FeedbackChange(target, replacement, applicability, disposition, reason)
  }

  private def _validate_feedback_replacement(target: String, replacement: Json): Unit = {
    if (target == "core") {
      val fields = replacement.asObject.map(_.toMap).getOrElse(CozyDocumentProject._failure("DP-DESC-001", "Core replacement must be an object"))
      if (fields.keySet != Set("accepted"))
        CozyDocumentProject._failure("DP-DESC-001", "Core replacement must contain exactly accepted")
      val accepted = fields("accepted").asArray.getOrElse(CozyDocumentProject._failure("DP-DESC-001", "Core replacement accepted must be an array"))
      val ids = accepted.map { value =>
        val entry = value.asObject.map(_.toMap).getOrElse(CozyDocumentProject._failure("DP-DESC-001", "Core replacement entry must be an object"))
        if (entry.keySet != Set("id", "text"))
          CozyDocumentProject._failure("DP-DESC-001", "Core replacement entries must contain exactly id and text")
        val id = entry("id").asString.getOrElse(CozyDocumentProject._failure("DP-DESC-001", "Core replacement entry id must be a string"))
        val text = entry("text").asString.getOrElse(CozyDocumentProject._failure("DP-DESC-001", "Core replacement entry text must be a string"))
        if (!_slug_pattern.pattern.matcher(id).matches() || text.isEmpty || text.trim != text)
          CozyDocumentProject._failure("DP-DESC-001", "Core replacement entry is invalid")
        id
      }
      if (ids.distinct.size != ids.size)
        CozyDocumentProject._failure("DP-DESC-001", "Core replacement ids must be unique")
    } else replacement.asString match {
      case Some(text) if text.trim.nonEmpty => ()
      case _ => CozyDocumentProject._failure("DP-DESC-001", s"$target replacement must be a non-empty string")
    }
  }

  private def _prepare_reflection(project: Path, descriptor: CozyDocumentProject.Descriptor, feedback: ParsedFeedback): Vector[ReflectionWrite] = {
    if (!CozyDocumentWorkflow.isVideoProfile(descriptor.profile)) {
      feedback.changes.find(_.target == "video") match {
        case Some(change) if change.applicability == "not-applicable" && change.disposition == "not-applicable" => ()
        case Some(_) => CozyDocumentProject._failure("DP-OP-001", s"${descriptor.profile} profile requires video feedback to be not-applicable")
        case None => CozyDocumentProject._failure("DP-OP-001", s"${descriptor.profile} profile requires a not-applicable video feedback item")
      }
    }
    val currentcore = if (feedback.changes.exists(change => change.target == "core" && change.disposition == "accepted"))
      Some(CozyDocumentProject._load_json(project.resolve(descriptor.contentCore), "Content Core"))
    else None
    feedback.changes.collect {
      case change if change.disposition == "accepted" =>
        val relative = _reflection_source(descriptor, change.target)
        val destination = CozyDocumentProject._direct_file(project, relative, "accepted feedback authority")
        val content = if (change.target == "core")
          _core_reflection_content(currentcore.get, change.replacement, descriptor)
        else change.replacement.asString.getOrElse(CozyDocumentProject._failure("DP-DESC-001", s"$change.target replacement must be a string"))
        ReflectionWrite(change.target, relative, destination, content)
    }
  }

  private def _reflection_source(descriptor: CozyDocumentProject.Descriptor, target: String): String = target match {
    case "core" => descriptor.contentCore
    case "article" => "index.dox"
    case "slides" => "presentation/visual-pages.yaml"
    case "infographic" => "infographic/infographic.svg"
    case "video" => "video/storyboard.md"
    case _ => CozyDocumentProject._failure("DP-CLI-001", "feedback change target is unknown")
  }

  private def _core_reflection_content(current: Json, replacement: Json, descriptor: CozyDocumentProject.Descriptor): String = {
    val currentfields = CozyDocumentProject._object(current, "Content Core")
    val replacementfields = CozyDocumentProject._object(replacement, "Core replacement")
    val merged = Json.obj(
      "schema" -> CozyDocumentProject._field(currentfields, "schema", "Content Core"),
      "id" -> CozyDocumentProject._field(currentfields, "id", "Content Core"),
      "language" -> CozyDocumentProject._field(currentfields, "language", "Content Core"),
      "accepted" -> CozyDocumentProject._field(replacementfields, "accepted", "Core replacement")
    )
    CozyDocumentProject._validate_core(merged, descriptor)
    val mergedfields = CozyDocumentProject._object(merged, "Content Core")
    val accepted = CozyDocumentProject._field(mergedfields, "accepted", "Content Core").asArray.get
    val envelope = Vector(
      s"schema: ${_yaml_string(CozyDocumentProject._string(currentfields, "schema", "Content Core"))}",
      s"id: ${_yaml_string(CozyDocumentProject._string(currentfields, "id", "Content Core"))}",
      s"language: ${_yaml_string(CozyDocumentProject._string(currentfields, "language", "Content Core"))}"
    )
    val entries = if (accepted.isEmpty) Vector("accepted: []") else
      Vector("accepted:") ++ accepted.map { value =>
        val fields = CozyDocumentProject._object(value, "Core replacement entry")
        s"  - id: ${_yaml_string(CozyDocumentProject._string(fields, "id", "Core replacement entry"))}\n    text: ${_yaml_string(CozyDocumentProject._string(fields, "text", "Core replacement entry"))}"
      }
    (envelope ++ entries).mkString("\n") + "\n"
  }

  private def _yaml_string(value: String): String = Json.fromString(value).noSpaces

  private def _publish_reflections(project: Path, writes: Vector[ReflectionWrite]): Unit = {
    writes.foreach { write =>
      var temporary: Option[Path] = None
      try {
        CozyDocumentProject._direct_file(project, write.relative, "accepted feedback authority")
        val temporaryfile = Files.createTempFile(write.destination.getParent, s".${write.destination.getFileName}-", ".tmp")
        temporary = Some(temporaryfile)
        Files.writeString(temporaryfile, write.content, StandardCharsets.UTF_8)
        CozyDocumentProject._direct_file(project, write.relative, "accepted feedback authority")
        Files.move(temporaryfile, write.destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        temporary = None
      } catch {
        case _: AtomicMoveNotSupportedException => CozyDocumentProject._failure("DP-PATH-001", "feedback authority replacement requires an atomic move")
        case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "feedback authority replacement cannot be published atomically")
      } finally {
        temporary.foreach(Files.deleteIfExists)
      }
    }
  }

  private def _reflection_result(project: Path, descriptor: CozyDocumentProject.Descriptor, changes: Vector[FeedbackChange]): String = {
    val lines = changes.map { change =>
      change.disposition match {
        case "accepted" => s"reflected: ${change.target} ${_reflection_source(descriptor, change.target)}"
        case "rejected" => s"rejected: ${change.target} — ${change.reason.get}"
        case "not-applicable" => s"not-applicable: ${change.target} — ${change.reason.get}"
      }
    }
    (Vector(
      "Cozy Document Project Feedback Reflection",
      s"project: ${descriptor.id}",
      s"package: $project",
      "schema: cozy.document-project.v1"
    ) ++ lines).mkString("\n")
  }
}
