package cozy.document

import cozy.media.CozyExplanation
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import scala.util.control.NonFatal

/*
 * @since   Sep.  9, 2026
 * @version Sep.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectPresentationSemanticsState {
  final case class State(
    sourcePath: String,
    semanticState: String,
    schemaIdentity: String,
    semanticIdentity: String,
    contentCoreId: String,
    contentCoreLanguage: String,
    contentCoreIdentity: String,
    contentCoreCurrentness: String,
    storyStepCount: String,
    storyTransitionCount: String,
    structureCount: String,
    slideProjectionAvailability: String,
    videoProjectionAvailability: String,
    coverageState: String,
    reason: String,
    failure: Option[CozyDocumentPresentationSemantics.PresentationSemanticsFault],
    validated: Option[CozyDocumentPresentationSemantics.Validated]
  ) {
    def status: String = semanticState
    def schema: String = schemaIdentity
    def projectionAvailability: String =
      if (slideProjectionAvailability == "available" && videoProjectionAvailability == "available") "available"
      else if (slideProjectionAvailability == "failed" || videoProjectionAvailability == "failed") "failed"
      else "unavailable"
  }

  final case class CoreBinding(id: String, language: String, identity: String, currentness: String)

  private final case class ParsedSource(value: Json, text: String)

  private val _unavailable = "unavailable"
  def sourcePath(descriptor: CozyDocumentProject.Descriptor): String =
    s"content/presentation-semantics-${descriptor.language}.yaml"

  def derive(project: Path, descriptor: CozyDocumentProject.Descriptor): State = {
    val relative = sourcePath(descriptor)
    val source = project.resolve(relative).normalize()
    val core = project.resolve(descriptor.contentCore).normalize()
    val coreidentity = _core_identity(core)
    _direct_source(project, source) match {
      case None =>
        _state(
          relative,
          "missing",
          _unavailable,
          _unavailable,
          CoreBinding(_unavailable, _unavailable, _unavailable, _unavailable),
          _unavailable,
          _unavailable,
          _unavailable,
          _unavailable,
          _unavailable,
          "presentation-semantics source is missing",
          Some(_fault("DP-SEM-001", "$", "presentation-semantics source must be an existing direct regular non-symlink file")),
          None
        )
      case Some(path) =>
        val parsed = _parse_source(path)
        val schema = parsed.flatMap(_.value.asObject.flatMap(_.apply("schema")).flatMap(_.asString)).getOrElse(_unavailable)
        val binding = parsed.flatMap(value => _binding(value.value, descriptor, coreidentity)).getOrElse(
          CoreBinding(_unavailable, _unavailable, _unavailable, _unavailable)
        )
        val result = try {
          Right(CozyDocumentPresentationSemantics.load(core, path, CozyDocumentPresentationSemantics.fixedCatalogs, CozyExplanation.ResourceBindings()))
        } catch {
          case fault: CozyDocumentPresentationSemantics.PresentationSemanticsFault => Left(fault)
          case NonFatal(error) => Left(_fault("DP-SEM-001", "$", _message(error)))
        }
        result match {
          case Right(validated) => _current(relative, schema, binding, validated)
          case Left(fault) if binding.currentness == "stale" && fault.code == "DP-SEM-005" && fault.path == "$.contentCore" =>
            _state(relative, "stale", schema, _unavailable, binding, _unavailable, _unavailable, _unavailable, _unavailable, _unavailable, fault.getMessage, Some(fault), None)
          case Left(fault) if parsed.exists(value => _is_authoring_incomplete(descriptor, value, coreidentity)) =>
            _state(relative, "authoring-incomplete", schema, _unavailable, binding, _unavailable, _unavailable, _unavailable, _unavailable, _unavailable, "presentation-semantics authoring is incomplete", Some(fault), None)
          case Left(fault) =>
            _state(relative, "invalid", schema, _unavailable, binding, _unavailable, _unavailable, _unavailable, _unavailable, _unavailable, fault.getMessage, Some(fault), None)
        }
    }
  }

  def requireCurrent(project: Path, descriptor: CozyDocumentProject.Descriptor): State = {
    val state = derive(project, descriptor)
    state.failure.foreach(fault => throw fault)
    state
  }

  def summary(state: State): String = {
    val values = Vector(
      s"presentationSemantics.source: ${state.sourcePath}",
      s"presentationSemantics.schema: ${state.schemaIdentity}",
      s"presentationSemantics.schemaIdentity: ${state.schemaIdentity}",
      s"presentationSemantics.semanticIdentity: ${state.semanticIdentity}",
      s"presentationSemantics.state: ${state.semanticState}",
      s"presentationSemantics.contentCore.id: ${state.contentCoreId}",
      s"presentationSemantics.contentCore.language: ${state.contentCoreLanguage}",
      s"presentationSemantics.contentCore.identity: ${state.contentCoreIdentity}",
      s"presentationSemantics.contentCore.currentness: ${state.contentCoreCurrentness}",
      s"presentationSemantics.storyStepCount: ${state.storyStepCount}",
      s"presentationSemantics.storyTransitionCount: ${state.storyTransitionCount}",
      s"presentationSemantics.structureCount: ${state.structureCount}",
      s"presentationSemantics.projectionAvailability: ${state.projectionAvailability}",
      s"presentationSemantics.slideProjectionAvailability: ${state.slideProjectionAvailability}",
      s"presentationSemantics.videoProjectionAvailability: ${state.videoProjectionAvailability}",
      s"presentationSemantics.coverage: ${state.coverageState}",
      s"presentationSemantics.reason: ${state.reason}"
    )
    values.mkString("\n")
  }

  def evidenceCurrentness(state: State): String = state.semanticState match {
    case "current" => "current"
    case "stale" => "stale"
    case "invalid" => "failed"
    case _ => "missing"
  }

  def evidenceCoverage(state: State): String = state.semanticState match {
    case "current" => if (state.coverageState == "satisfied") "satisfied" else "missing"
    case _ => "missing"
  }

  def evidenceReadiness(state: State): String = state.semanticState match {
    case "current" if state.coverageState == "satisfied" => "ready"
    case "invalid" => "failed"
    case _ => "blocked"
  }

  private def _current(
    relative: String,
    schema: String,
    binding: CoreBinding,
    validated: CozyDocumentPresentationSemantics.Validated
  ): State = {
    val state = try {
      val value = CozyDocumentCrossMediaProjection.project(validated)
      val rendered = CozyDocumentCrossMediaConfirmationHtml.render(value)
      val coverage = CozyDocumentCrossMediaReceipt.verifyCoverage(validated, value, rendered)
      if (coverage.satisfied) {
        _state(relative, "current", schema, validated.semanticIdentity, binding, validated.plan.steps.size.toString, validated.storyFlow.transitions.size.toString, validated.structures.size.toString, "available", "available", "strict semantics, projection, and semantic coverage are current", None, Some(validated), coveragestate = "satisfied")
      } else {
        val reason = coverage.diagnostics.map(value => s"${value.code} path=${value.path} reason=${value.reason}").mkString("; ")
        _state(relative, "current", schema, validated.semanticIdentity, binding, validated.plan.steps.size.toString, validated.storyFlow.transitions.size.toString, validated.structures.size.toString, "available", "available", reason, None, Some(validated), coveragestate = "failed")
      }
    } catch {
      case fault: CozyDocumentCrossMediaProjection.ProjectionFault =>
        _state(relative, "current", schema, validated.semanticIdentity, binding, validated.plan.steps.size.toString, validated.storyFlow.transitions.size.toString, validated.structures.size.toString, "failed", "failed", fault.getMessage, None, Some(validated))
      case NonFatal(error) =>
        _state(relative, "current", schema, validated.semanticIdentity, binding, validated.plan.steps.size.toString, validated.storyFlow.transitions.size.toString, validated.structures.size.toString, "failed", "failed", _message(error), None, Some(validated))
    }
    state
  }

  private def _state(
    relative: String,
    semanticstate: String,
    schema: String,
    semanticidentity: String,
    binding: CoreBinding,
    plansteps: String,
    transitions: String,
    structures: String,
    slideprojection: String,
    videoprojection: String,
    reason: String,
    failure: Option[CozyDocumentPresentationSemantics.PresentationSemanticsFault],
    validated: Option[CozyDocumentPresentationSemantics.Validated],
    coveragestate: String = _unavailable
  ): State = State(
    relative,
    semanticstate,
    schema,
    semanticidentity,
    binding.id,
    binding.language,
    binding.identity,
    binding.currentness,
    plansteps,
    transitions,
    structures,
    slideprojection,
    videoprojection,
    coveragestate,
    reason,
    failure,
    validated
  )

  private def _binding(value: Json, descriptor: CozyDocumentProject.Descriptor, coreidentity: String): Option[CoreBinding] =
    value.asObject.flatMap(_.apply("contentCore")).flatMap(_.asObject).map { fields =>
      val id = fields.apply("id").flatMap(_.asString).getOrElse(_unavailable)
      val language = fields.apply("language").flatMap(_.asString).getOrElse(_unavailable)
      val identity = fields.apply("identity").flatMap(_.asString).getOrElse(_unavailable)
      val expectedid = s"${descriptor.id}:core:${descriptor.language}"
      val valididentity = identity.matches("sha256:[0-9a-f]{64}")
      val currentness =
        if (id == _unavailable || language == _unavailable || identity == _unavailable || !valididentity) "unavailable"
        else if (id == expectedid && language == descriptor.language && identity == coreidentity) "current"
        else if (id == expectedid && language == descriptor.language && coreidentity.matches("sha256:[0-9a-f]{64}")) "stale"
        else "unavailable"
      CoreBinding(id, language, identity, currentness)
    }

  private def _parse_source(path: Path): Option[ParsedSource] = try {
    val text = Files.readString(path, StandardCharsets.UTF_8)
    Some(ParsedSource(StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take, text))
  } catch {
    case NonFatal(_) => None
  }

  private def _is_authoring_incomplete(
    descriptor: CozyDocumentProject.Descriptor,
    source: ParsedSource,
    coreidentity: String
  ): Boolean =
    source.text == CozyDocumentProject._presentation_semantics_yaml(descriptor.id, descriptor.language, coreidentity.drop("sha256:".length))

  private def _direct_source(project: Path, path: Path): Option[Path] = {
    if (!path.startsWith(project) || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) None
    else {
      val relative = project.relativize(path)
      var parent = project
      val direct = (0 until relative.getNameCount - 1).forall { index =>
        parent = parent.resolve(relative.getName(index))
        !Files.isSymbolicLink(parent) && Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
      }
      if (direct) Some(path) else None
    }
  }

  private def _core_identity(path: Path): String = try {
    "sha256:" + _sha256(Files.readAllBytes(path))
  } catch {
    case NonFatal(_) => _unavailable
  }

  private def _fault(code: String, path: String, reason: String): CozyDocumentPresentationSemantics.PresentationSemanticsFault =
    CozyDocumentPresentationSemantics.PresentationSemanticsFault(code, path, reason)

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString

  private def _message(error: Throwable): String =
    Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
}
