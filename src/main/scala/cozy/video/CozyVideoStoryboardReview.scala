package cozy.video

import cozy.runtime.CozyCliArgs
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import org.goldenport.RAISE
import org.goldenport.cli.spec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoStoryboardReview {
  self: CozyVideoTypes with CozyVideoStoryboard with CozyVideoCommand =>

  final case class StoryboardReviewConfig(projectFile: Path, saveDir: Path) {
    def projectRoot: Path = projectFile.getParent
  }
  object StoryboardReviewConfig {
    def create(args: List[String]): StoryboardReviewConfig = {
      val parsed = CozyCliArgs.parseStrict(
        _p_project_file,
        _p_save
      )(_normalize_property_args(args))
      val projectfile = parsed.argument("project-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing project file for video storyboard review-evidence")
      )
      StoryboardReviewConfig(projectfile, parsed.requiredPathProperty("save"))
    }
  }

  final case class StoryboardReviewResult(
    projectFile: Path,
    saveDir: Path,
    evidencePath: Path,
    handoffPath: Path,
    evidenceIdentity: String,
    storyboardIdentity: String,
    visualInputIdentities: Vector[String]
  )

  private final case class ApprovedStoryboard(
    sourcepath: Path,
    relativepath: String,
    storyboard: Storyboard,
    identity: String
  )

  private final case class VisualInput(
    reference: String,
    sourcepath: Path,
    relativepath: String,
    copiedpath: Path,
    identity: String
  )

  private val _review_evidence_schema = "cozy.video.storyboard-review-evidence.v1"
  private val _storyboard_handoff_schema = "cozy.video.storyboard-handoff.v1"
  private val _sha256_pattern = "sha256:[0-9a-f]{64}".r

  def storyboardReviewEvidence(config: StoryboardReviewConfig): StoryboardReviewResult =
    _write_storyboard_review_evidence(config)

  def storyboardReview(config: StoryboardReviewConfig): String = {
    val result = storyboardReviewEvidence(config)
    Vector(
      s"evidence: ${result.evidencePath}",
      s"handoff: ${result.handoffPath}",
      s"identity: ${result.evidenceIdentity}",
      s"storyboardIdentity: ${result.storyboardIdentity}"
    ).mkString("\n")
  }

  private[video] def _validate_storyboard_review_current(plan: VideoPlan): Unit =
    plan.project.storyboardReview.foreach { review =>
      val projectroot = _canonical_project_root(plan.projectRoot)
      val approved = _approved_storyboard(projectroot, review)
      review.visualStory.foreach { visualstory =>
        val evidencedirectory = _safe_evidence_directory(projectroot, visualstory.evidenceDirectory)
        val inputs = _admitted_visual_inputs(projectroot, evidencedirectory, approved.storyboard, visualstory)
        val approvedevidenceidentity = visualstory.approvedEvidenceIdentity match {
          case Some(value) if _is_identity(value) => value
          case Some(value) =>
            _review_failure("STORYBOARD_REVIEW_APPROVED_EVIDENCE_IDENTITY_INVALID", s"approvedEvidenceIdentity is not a lowercase SHA-256 identity: $value")
          case None =>
            _review_failure("STORYBOARD_REVIEW_APPROVED_EVIDENCE_MISSING", "visualStory.approvedEvidenceIdentity is required before video build")
        }
        val evidencepath = evidencedirectory.resolve("review-evidence.json").normalize()
        _require_direct_output_file(evidencepath, evidencedirectory, "Storyboard review evidence")
        val evidence = _read_json(evidencepath, "Storyboard review evidence")
        val expected = _review_evidence_payload(approved, inputs, visualreview = true)
        _validate_current_evidence(evidence, expected, approved, inputs, approvedevidenceidentity)
      }
    }

  private[video] def _write_storyboard_review_evidence(config: StoryboardReviewConfig): StoryboardReviewResult = {
    val projectfile = _verified_project_file(config.projectFile)
    val projectroot = projectfile.getParent.toRealPath()
    val project = _load_project(projectfile)
    val review = project.storyboardReview.getOrElse(
      _review_failure("STORYBOARD_REVIEW_DECLARATION_MISSING", "video project does not declare storyboardReview")
    )
    val approved = _approved_storyboard(projectroot, review)
    val expectedvisualdirectory = review.visualStory.map { visualstory =>
      _safe_evidence_directory(projectroot, visualstory.evidenceDirectory)
    }
    val savedir = _prepare_save_directory(config.saveDir)
    val inputs = review.visualStory match {
      case Some(visualstory) =>
        val evidencedirectory = expectedvisualdirectory.get
        if (savedir != evidencedirectory)
          _review_failure(
            "STORYBOARD_REVIEW_SAVE_MISMATCH",
            s"--save must equal visualStory.evidenceDirectory: expected $evidencedirectory, found $savedir"
          )
        _admitted_visual_inputs(projectroot, savedir, approved.storyboard, visualstory).map { input =>
          _copy_visual_input(input, savedir)
        }
      case None =>
        Vector.empty
    }
    val visualdirectory = review.visualStory.map(_ => _ensure_direct_directory(savedir.resolve("visual-inputs").normalize(), savedir, "Storyboard visual-inputs"))
    val payload = _review_evidence_payload(approved, inputs, review.visualStory.isDefined)
    val evidenceidentity = _json_identity(payload)
    val evidencejson = payload.deepMerge(Json.obj("identity" -> Json.fromString(evidenceidentity)))
    val evidencepath = savedir.resolve("review-evidence.json").normalize()
    _write_json(evidencepath, evidencejson, savedir)
    val handoffpath = savedir.resolve("handoff.json").normalize()
    val handofffields = Vector(
      Some("schema" -> Json.fromString(_storyboard_handoff_schema)),
      Some("evidencePath" -> Json.fromString(_display_path(projectroot, evidencepath))),
      Some("evidenceIdentity" -> Json.fromString(evidenceidentity)),
      Some("storyboardIdentity" -> Json.fromString(approved.identity)),
      if (review.visualStory.isDefined) Some(
        "visualInputIdentities" -> Json.fromValues(inputs.map { input =>
          Json.obj(
            "reference" -> Json.fromString(input.reference),
            "identity" -> Json.fromString(input.identity)
          )
        })
      ) else None
    ).flatten
    _write_json(handoffpath, Json.obj(handofffields: _*), savedir)
    visualdirectory.foreach(_ => ())
    StoryboardReviewResult(
      projectfile,
      savedir,
      evidencepath,
      handoffpath,
      evidenceidentity,
      approved.identity,
      inputs.map(_.identity)
    )
  }

  private def _approved_storyboard(projectroot: Path, review: StoryboardReview): ApprovedStoryboard = {
    val approvedidentity = _validate_identity(review.approvedIdentity, "STORYBOARD_REVIEW_APPROVAL_IDENTITY_INVALID")
    val sourcepath = _safe_project_relative_file(projectroot, review.source, "Storyboard review source")
    val result = loadStoryboard(sourcepath)
    if (!result.isValid)
      _review_failure(
        "STORYBOARD_REVIEW_SOURCE_INVALID",
        result.diagnostics.map(_.render).mkString("; ")
      )
    val storyboard = result.storyboard.get
    val identity = storyboardIdentity(storyboard)
    if (identity != approvedidentity)
      _review_failure(
        "STORYBOARD_REVIEW_APPROVAL_MISMATCH",
        s"current Storyboard identity $identity does not equal approvedIdentity $approvedidentity"
      )
    ApprovedStoryboard(sourcepath, _display_path(projectroot, sourcepath), storyboard, identity)
  }

  private def _admitted_visual_inputs(
    projectroot: Path,
    evidencedirectory: Path,
    storyboard: Storyboard,
    visualstory: StoryboardReviewVisualStory
  ): Vector[VisualInput] = {
    if (visualstory.inputRefs.distinct.size != visualstory.inputRefs.size)
      _review_failure("STORYBOARD_REVIEW_INPUT_DUPLICATE", "visualStory.inputRefs must not contain duplicates")
    val admitted = storyboard.scenes.flatMap(scene => scene.diagramRefs ++ scene.assetRefs).toSet
    visualstory.inputRefs.zipWithIndex.map { case (reference, index) =>
      if (reference.trim.isEmpty || !admitted.contains(reference))
        _review_failure(
          "STORYBOARD_REVIEW_INPUT_UNDECLARED",
          s"visualStory.inputRefs[$index] is not an exact Storyboard diagramRefs/assetRefs member: $reference"
        )
      val sourcepath = _safe_project_relative_file(
        projectroot,
        reference,
        s"Storyboard visual input $reference",
        symlinkfailurecode = "STORYBOARD_REVIEW_INPUT_UNSAFE"
      )
      val relative = _display_path(projectroot, sourcepath)
      val destination = evidencedirectory.resolve("visual-inputs").resolve(_relative_path(reference)).normalize()
      _require_contained_path(evidencedirectory, destination, s"Storyboard visual input destination $reference")
      VisualInput(reference, sourcepath, relative, destination, _file_identity(sourcepath))
    }
  }

  private def _copy_visual_input(input: VisualInput, savedir: Path): VisualInput = {
    val visualinputs = _ensure_direct_directory(savedir.resolve("visual-inputs").normalize(), savedir, "Storyboard visual-inputs")
    val destination = input.copiedpath
    _require_contained_path(savedir, destination, s"Storyboard visual input destination ${input.reference}")
    _ensure_direct_directory(destination.getParent, savedir, s"Storyboard visual input parent ${input.reference}")
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(destination))
      _review_failure("STORYBOARD_REVIEW_INPUT_DESTINATION_SYMLINK", s"visual input destination is a symbolic link: $destination")
    try {
      Files.copy(input.sourcepath, destination, StandardCopyOption.REPLACE_EXISTING)
    } catch {
      case NonFatal(error) =>
        _review_failure("STORYBOARD_REVIEW_INPUT_COPY_FAILED", s"cannot copy ${input.reference}: ${error.getMessage}")
    }
    if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination))
      _review_failure("STORYBOARD_REVIEW_INPUT_COPY_INVALID", s"copied visual input is not a direct regular file: $destination")
    if (_file_identity(destination) != input.identity)
      _review_failure("STORYBOARD_REVIEW_INPUT_COPY_CHANGED", s"visual input changed while copying: ${input.reference}")
    input.copy(copiedpath = destination)
  }

  private def _review_evidence_payload(approved: ApprovedStoryboard, inputs: Vector[VisualInput], visualreview: Boolean): Json = {
    val scenejson = approved.storyboard.scenes.map { scene =>
      Json.obj(
        "section" -> Json.fromString(scene.section),
        "speaker" -> Json.fromString(scene.speaker),
        "role" -> Json.fromString(scene.role),
        "narration" -> Json.fromString(scene.narration),
        "screen" -> Json.obj(
          "heading" -> Json.fromString(scene.screen.heading),
          "content" -> Json.fromString(scene.screen.content)
        ),
        "caption" -> Json.fromString(scene.caption),
        "duration" -> Json.fromBigDecimal(scene.duration),
        "leadSilence" -> Json.fromBigDecimal(scene.leadSilence),
        "transition" -> Json.fromString(scene.transition),
        "direction" -> Json.fromString(scene.direction)
      )
    }
    val fields = Vector(
      Some("schema" -> Json.fromString(_review_evidence_schema)),
      Some("status" -> Json.fromString("validated")),
      Some("source" -> Json.fromString(approved.relativepath)),
      Some("storyboardIdentity" -> Json.fromString(approved.identity)),
      Some("scenes" -> Json.fromValues(scenejson)),
      if (visualreview)
        Some("visualInputs" -> Json.fromValues(inputs.map { input =>
          Json.obj(
            "reference" -> Json.fromString(input.reference),
            "source" -> Json.fromString(input.relativepath),
            "path" -> Json.fromString(_slash_path(Paths.get("visual-inputs").resolve(_relative_path(input.reference)))),
            "identity" -> Json.fromString(input.identity)
          )
        }))
      else None
    ).flatten
    Json.obj(fields: _*)
  }

  private def _validate_current_evidence(
    evidence: Json,
    expected: Json,
    approved: ApprovedStoryboard,
    inputs: Vector[VisualInput],
    approvedevidenceidentity: String
  ): Unit = {
    val actualidentity = evidence.hcursor.get[String]("identity").toOption.getOrElse(
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_IDENTITY_MISSING", "review-evidence.json has no identity")
    )
    if (!_is_identity(actualidentity))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_IDENTITY_INVALID", s"review-evidence.json identity is invalid: $actualidentity")
    val payload = evidence.asObject.map(_.remove("identity")).map(Json.fromJsonObject).getOrElse(
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_MALFORMED", "review-evidence.json must be a JSON object")
    )
    if (evidence.hcursor.get[String]("schema").toOption != Some(_review_evidence_schema))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_SCHEMA_INVALID", "review-evidence.json schema is not supported")
    if (evidence.hcursor.get[String]("status").toOption != Some("validated"))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_STATUS_INVALID", "review-evidence.json status is not validated")
    if (_json_identity(payload) != actualidentity)
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_IDENTITY_MISMATCH", "review-evidence.json identity does not match its canonical payload")
    if (payload != expected)
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_STALE", s"review-evidence.json is stale for approved Storyboard ${approved.identity}")
    if (actualidentity != approvedevidenceidentity)
      _review_failure(
        "STORYBOARD_REVIEW_APPROVED_EVIDENCE_MISMATCH",
        s"approvedEvidenceIdentity $approvedevidenceidentity does not match current evidence $actualidentity"
      )
    inputs.foreach { input =>
      _require_direct_output_file(input.copiedpath, input.copiedpath.getParent, s"Storyboard visual input ${input.reference}")
      if (_file_identity(input.copiedpath) != input.identity)
        _review_failure("STORYBOARD_REVIEW_INPUT_STALE", s"visual input changed since evidence generation: ${input.reference}")
    }
  }

  private def _safe_project_relative_file(
    projectroot: Path,
    raw: String,
    label: String,
    symlinkfailurecode: String = "STORYBOARD_REVIEW_SYMLINK_COMPONENT"
  ): Path = {
    val relative = _safe_relative_path(raw, label)
    val normalizedroot = projectroot.toAbsolutePath.normalize()
    val candidate = normalizedroot.resolve(relative).normalize()
    _require_contained_path(normalizedroot, candidate, label)
    _assert_no_symlink_components(normalizedroot, candidate, label, symlinkfailurecode)
    if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
      _review_failure("STORYBOARD_REVIEW_INPUT_UNSAFE", s"$label must be an existing direct regular non-symlink file: $candidate")
    val canonicalroot = try normalizedroot.toRealPath() catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_PROJECT_ROOT_INVALID", s"project root cannot be resolved: ${error.getMessage}")
    }
    val canonical = try candidate.toRealPath() catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_INPUT_UNREADABLE", s"$label cannot be resolved: ${error.getMessage}")
    }
    if (Files.isSymbolicLink(canonical) || !Files.isRegularFile(canonical, LinkOption.NOFOLLOW_LINKS) || !canonical.startsWith(canonicalroot))
      _review_failure("STORYBOARD_REVIEW_INPUT_OUTSIDE_PROJECT", s"$label resolves outside the project root: $canonical")
    canonical
  }

  private def _safe_evidence_directory(projectroot: Path, raw: String): Path = {
    val relative = _safe_relative_path(raw, "Storyboard visual evidenceDirectory")
    val canonicalprojectroot = _canonical_project_root(projectroot)
    val targetroot = canonicalprojectroot.resolve("target").resolve("cozy-video").normalize()
    val directory = canonicalprojectroot.resolve(relative).normalize()
    _require_contained_path(targetroot, directory, "Storyboard visual evidenceDirectory")
    if (directory == targetroot)
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_DIRECTORY_INVALID", "visualStory.evidenceDirectory must be below target/cozy-video")
    _assert_no_symlink_components(canonicalprojectroot, directory, "Storyboard visual evidenceDirectory")
    directory
  }

  private def _canonical_project_root(path: Path): Path =
    try path.toAbsolutePath.normalize().toRealPath() catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_PROJECT_ROOT_INVALID", s"project root cannot be resolved: ${error.getMessage}")
    }

  private def _prepare_save_directory(path: Path): Path = {
    val save = Option(path).map(_.toAbsolutePath.normalize()).getOrElse(
      _review_failure("STORYBOARD_REVIEW_SAVE_INVALID", "--save directory must be defined")
    )
    if (save.getParent == null)
      _review_failure("STORYBOARD_REVIEW_SAVE_INVALID", "--save directory must not be the filesystem root")
    if (Files.exists(save, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(save))
      _review_failure("STORYBOARD_REVIEW_SAVE_SYMLINK", s"--save directory must not be a symbolic link: $save")
    if (Files.exists(save, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(save, LinkOption.NOFOLLOW_LINKS))
      _review_failure("STORYBOARD_REVIEW_SAVE_NOT_DIRECTORY", s"--save is not a directory: $save")
    _assert_no_save_symlink_components(save, "Storyboard review --save")
    try Files.createDirectories(save) catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_SAVE_CREATE_FAILED", s"cannot create --save directory: ${error.getMessage}")
    }
    _assert_no_save_symlink_components(save, "Storyboard review --save")
    if (!Files.isDirectory(save, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(save))
      _review_failure("STORYBOARD_REVIEW_DIRECTORY_INVALID", s"Storyboard review --save must be a direct directory: $save")
    try save.toRealPath() catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_SAVE_UNREADABLE", s"cannot resolve --save directory: ${error.getMessage}")
    }
  }

  private def _ensure_direct_directory(path: Path, boundary: Path, label: String): Path = {
    val normalized = path.toAbsolutePath.normalize()
    _require_contained_path(boundary.toAbsolutePath.normalize(), normalized, label)
    _assert_no_symlink_components(boundary.toAbsolutePath.normalize(), normalized, label)
    if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)))
      _review_failure("STORYBOARD_REVIEW_DIRECTORY_INVALID", s"$label must be a direct directory: $normalized")
    try Files.createDirectories(normalized) catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_DIRECTORY_CREATE_FAILED", s"cannot create $label: ${error.getMessage}")
    }
    _assert_no_symlink_components(boundary.toAbsolutePath.normalize(), normalized, label)
    if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _review_failure("STORYBOARD_REVIEW_DIRECTORY_INVALID", s"$label must be a direct directory: $normalized")
    normalized
  }

  private def _require_direct_output_file(path: Path, boundary: Path, label: String): Unit = {
    val normalized = path.toAbsolutePath.normalize()
    _require_contained_path(boundary.toAbsolutePath.normalize(), normalized, label)
    _assert_no_symlink_components(boundary.toAbsolutePath.normalize(), normalized.getParent, label)
    if (Files.isSymbolicLink(normalized) || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_MISSING", s"$label is missing or not a direct regular file: $normalized")
  }

  private def _write_json(path: Path, json: Json, boundary: Path): Unit = {
    val normalized = path.toAbsolutePath.normalize()
    _require_contained_path(boundary.toAbsolutePath.normalize(), normalized, "Storyboard review output")
    _ensure_direct_directory(normalized.getParent, boundary, "Storyboard review output parent")
    if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(normalized))
      _review_failure("STORYBOARD_REVIEW_OUTPUT_SYMLINK", s"output must not be a symbolic link: $normalized")
    try Files.writeString(normalized, json.noSpaces, StandardCharsets.UTF_8) catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_OUTPUT_WRITE_FAILED", s"cannot write $normalized: ${error.getMessage}")
    }
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _review_failure("STORYBOARD_REVIEW_OUTPUT_INVALID", s"output is not a direct regular file: $normalized")
  }

  private def _read_json(path: Path, label: String): Json = {
    try {
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
        error => _review_failure("STORYBOARD_REVIEW_EVIDENCE_MALFORMED", s"$label is not valid JSON: ${error.getMessage}"),
        identity
      )
    } catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_EVIDENCE_READ_FAILED", s"cannot read $label: ${error.getMessage}")
    }
  }

  private def _validate_identity(value: String, code: String): String = {
    val normalized = Option(value).getOrElse("")
    if (!_is_identity(normalized))
      _review_failure(code, s"invalid SHA-256 identity: $value")
    normalized
  }

  private def _is_identity(value: String): Boolean =
    _sha256_pattern.pattern.matcher(Option(value).getOrElse("")).matches()

  private def _json_identity(json: Json): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    "sha256:" + _sha256_hex(digest.digest(json.noSpaces.getBytes(StandardCharsets.UTF_8)))
  }

  private def _file_identity(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var count = input.read(buffer)
      while (count >= 0) {
        if (count > 0) digest.update(buffer, 0, count)
        count = input.read(buffer)
      }
    } finally input.close()
    "sha256:" + _sha256_hex(digest.digest())
  }

  private def _sha256_hex(bytes: Array[Byte]): String =
    bytes.map(value => f"${value & 0xff}%02x").mkString

  private def _safe_relative_path(raw: String, label: String): Path = {
    val value = Option(raw).map(_.trim).getOrElse("")
    if (value.isEmpty)
      _review_failure("STORYBOARD_REVIEW_PATH_INVALID", s"$label must be nonempty")
    val path = try Paths.get(value) catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_PATH_INVALID", s"$label is not a valid path: ${error.getMessage}")
    }
    if (path.isAbsolute || path.normalize().startsWith(Paths.get("..")))
      _review_failure("STORYBOARD_REVIEW_PATH_INVALID", s"$label must be project-relative: $raw")
    path.normalize()
  }

  private def _relative_path(raw: String): Path =
    Paths.get(raw).normalize()

  private def _display_path(root: Path, path: Path): String = {
    val normalizedroot = root.toAbsolutePath.normalize()
    val normalized = path.toAbsolutePath.normalize()
    if (normalized.startsWith(normalizedroot))
      _slash_path(normalizedroot.relativize(normalized))
    else
      _slash_path(normalized)
  }

  private def _slash_path(path: Path): String =
    path.iterator().asScala.map(_.toString).mkString("/")

  private def _require_contained_path(boundary: Path, path: Path, label: String): Unit = {
    val base = boundary.toAbsolutePath.normalize()
    val candidate = path.toAbsolutePath.normalize()
    if (!(candidate == base || candidate.startsWith(base)))
      _review_failure("STORYBOARD_REVIEW_PATH_OUTSIDE_BOUNDARY", s"$label escapes its allowed directory: $candidate")
  }

  private def _assert_no_symlink_components(
    boundary: Path,
    path: Path,
    label: String,
    failurecode: String = "STORYBOARD_REVIEW_SYMLINK_COMPONENT"
  ): Unit = {
    if (path == null)
      return
    val base = boundary.toAbsolutePath.normalize()
    val candidate = path.toAbsolutePath.normalize()
    _require_contained_path(base, candidate, label)
    val relative = base.relativize(candidate)
    var current = base
    relative.iterator().asScala.foreach { component =>
      current = current.resolve(component.toString)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
        _review_failure(failurecode, s"$label contains a symbolic-link path component: $current")
    }
  }

  private def _assert_no_save_symlink_components(path: Path, label: String): Unit = {
    val save = path.toAbsolutePath.normalize()
    var current = save.getRoot
    save.iterator().asScala.foreach { component =>
      current = current.resolve(component.toString)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current) && !_is_platform_temp_alias(current))
        _review_failure("STORYBOARD_REVIEW_SYMLINK_COMPONENT", s"$label contains a symbolic-link path component: $current")
    }
  }

  private def _is_platform_temp_alias(path: Path): Boolean = {
    val alias = Paths.get("/var")
    val physical = Paths.get("/private/var")
    try {
      path.toAbsolutePath.normalize() == alias &&
      alias.toRealPath() == physical.toRealPath()
    } catch {
      case NonFatal(_) => false
    }
  }

  private def _review_failure(code: String, message: String): Nothing =
    RAISE.invalidArgumentFault(s"$code: $message")
}
