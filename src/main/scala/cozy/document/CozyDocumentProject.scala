package cozy.document

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import io.circe.Json
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.UUID
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Aug. 31, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProject {
  private[cozy] final case class Descriptor(
    id: String,
    profile: String,
    language: String,
    workspace: String,
    contentCore: String,
    activeOptionalWorkProducts: Vector[String],
    semanticScope: SemanticScope
  )
  private[cozy] final case class SemanticWorkProduct(id: String, identity: String)
  private[cozy] final case class LocaleVariant(project: String, language: String, contentCore: String, workProducts: Vector[SemanticWorkProduct])
  private[cozy] final case class SemanticScope(id: String, localeVariants: Vector[LocaleVariant])

  private final case class ProjectRequest(command: String, project: String, operation: Option[String], dryrun: Boolean, kind: Option[String], save: Option[String])
  private final case class FeedbackRequest(project: String, feedback: String)
  private final case class ScaffoldRequest(slug: String, profile: String, language: String, workspace: String, parent: String)
  private final case class ParsedOptions(values: Map[String, String], flags: Set[String], positionals: Vector[String])
  private val _slug_pattern = "[a-z0-9][a-z0-9._-]*".r
  private val _language_pattern = "[a-z]{2,8}(?:-[a-z0-9]{1,8})*".r
  private val _descriptor_keys = Set("schema", "id", "workflow", "profile", "language", "workspace", "contentCore", "activeOptionalWorkProducts", "semanticScope")
  private val _core_keys = Set("schema", "id", "language", "accepted")

  def execute(args: List[String]): Boolean = args match {
    case "document-project" :: rest =>
      _parse(rest) match {
        case FeedbackRequest(projectvalue, feedbackvalue) =>
          val project = _admit_project(projectvalue)
          val descriptor = _load_project(project)
          println(CozyDocumentFeedbackReflection.reflect(project, descriptor, feedbackvalue))
          true
        case ProjectRequest(command, projectvalue, operation, dryrun, kind, save) =>
          val project = _admit_project(projectvalue)
          if (command == "verify" || command == "run")
            _verify_initial_sources(project)
          val descriptor = _load_project(project)
          command match {
            case "inspect" =>
              val state = _write_state_snapshot(project, descriptor)
              println(_inspect(project, descriptor, state))
            case "plan" => println(_plan(project, descriptor))
            case "verify" =>
              val verification = _verify(project, descriptor)
              val state = _write_state_snapshot(project, descriptor)
              println(_with_state(verification, state))
            case "run" =>
              _verify(project, descriptor)
              println(_run(project, descriptor, operation.getOrElse(""), dryrun))
            case "dashboard" =>
              val destination = CozyDocumentProjectProjection.admitDestination(project, save, "project-dashboard.html")
              val html = CozyDocumentProjectProjection.dashboardHtml(project, descriptor, destination)
              CozyDocumentProjectProjection.publish(destination, html)
              println(CozyDocumentProjectProjection.projectionResult("Dashboard", project, descriptor, destination))
            case "review" =>
              val reviewkind = kind.getOrElse(_failure("DP-CLI-002", "review requires --kind core|slides|video|slide-logical-chart|video-logical-chart"))
              val html = reviewkind match {
                case "core" => CozyDocumentProjectProjection.coreReviewHtml(project, descriptor)
                case "slides" => CozyDocumentProjectProjection.slideReviewHtml(project, descriptor)
                case "video" => CozyDocumentProjectProjection.videoReviewHtml(project, descriptor)
                case "slide-logical-chart" => CozyDocumentProjectProjection.slideLogicalChartHtml(project, descriptor)
                case "video-logical-chart" => CozyDocumentProjectProjection.videoLogicalChartHtml(project, descriptor)
                case _ => _failure("DP-CLI-001", "review --kind must be core, slides, video, slide-logical-chart, or video-logical-chart")
              }
              val destination = CozyDocumentProjectProjection.admitDestination(project, save, s"$reviewkind-review.html")
              CozyDocumentProjectProjection.publish(destination, html)
              val reviewlabel = reviewkind match {
                case "core" => "Core Review"
                case "slides" => "Slide Review"
                case "video" => "Video Review"
                case "slide-logical-chart" => "Slide Logical Chart"
                case "video-logical-chart" => "Video Logical Chart"
              }
              println(CozyDocumentProjectProjection.projectionResult(reviewlabel, project, descriptor, destination))
            case _ => _failure("DP-CLI-001", s"unsupported document-project command: $command")
          }
          true
        case ScaffoldRequest(slug, profile, language, workspace, parent) =>
          val destination = _scaffold(slug, profile, language, workspace, parent)
          println(_scaffold_result(destination, slug, profile, language, workspace))
          true
      }
    case _ => false
  }

  private def _parse(args: List[String]): Product = args match {
    case "inspect" :: rest => _project_request("inspect", rest, Set.empty, Set.empty)
    case "plan" :: rest => _project_request("plan", rest, Set.empty, Set.empty)
    case "dashboard" :: rest => _project_request("dashboard", rest, Set("save"), Set.empty)
    case "review" :: rest => _project_request("review", rest, Set("kind", "save"), Set.empty)
    case "reflect-feedback" :: rest => _feedback_request(rest)
    case "verify" :: rest => _project_request("verify", rest, Set.empty, Set.empty)
    case "run" :: rest => _project_request("run", rest, Set("operation"), Set("dry-run"))
    case "scaffold" :: rest => _scaffold_request(rest)
    case value :: _ => _failure("DP-CLI-001", s"unknown document-project command: $value")
    case Nil => _failure("DP-CLI-001", "missing document-project command")
  }

  private def _project_request(
    command: String,
    args: List[String],
    valueoptions: Set[String],
    flagoptions: Set[String]
  ): ProjectRequest = {
    val parsed = _parse_options(args, valueoptions, flagoptions)
    if (parsed.positionals.size > 1)
      _failure("DP-CLI-001", s"invalid $command command grammar")
    if (command == "run" && !parsed.values.contains("operation"))
      _failure("DP-CLI-002", "run requires --operation <logical-operation>")
    if (command == "review") {
      parsed.values.get("kind") match {
        case None => _failure("DP-CLI-002", "review requires --kind core|slides|video|slide-logical-chart|video-logical-chart")
        case Some(value) if !Set("core", "slides", "video", "slide-logical-chart", "video-logical-chart").contains(value) => _failure("DP-CLI-001", "review --kind must be core, slides, video, slide-logical-chart, or video-logical-chart")
        case _ => ()
      }
    }
    if (parsed.positionals.isEmpty)
      _failure("DP-CLI-002", s"$command requires <project>")
    ProjectRequest(command, parsed.positionals.head, parsed.values.get("operation"), parsed.flags.contains("dry-run"), parsed.values.get("kind"), parsed.values.get("save"))
  }

  private def _feedback_request(args: List[String]): FeedbackRequest = {
    val parsed = _parse_options(args, Set.empty, Set.empty)
    if (parsed.positionals.size > 2)
      _failure("DP-CLI-001", "invalid reflect-feedback command grammar")
    if (parsed.positionals.size < 2)
      _failure("DP-CLI-002", "reflect-feedback requires <project> <feedback>")
    FeedbackRequest(parsed.positionals(0), parsed.positionals(1))
  }

  private def _scaffold_request(args: List[String]): ScaffoldRequest = {
    val parsed = _parse_options(args, Set("profile", "language", "workspace", "save"), Set.empty)
    parsed.values.get("profile").foreach { value =>
      if (!CozyDocumentWorkflow.isScaffoldProfile(value))
        _failure("DP-CLI-001", "scaffold profile must be standard, standard-video, bok, or bok-video")
    }
    parsed.values.get("language").foreach { value =>
      if (!_language_pattern.pattern.matcher(value).matches())
        _failure("DP-CLI-001", "scaffold language must be a lowercase BCP-47 tag")
    }
    parsed.values.get("workspace").foreach { value =>
      if (value != "directory" && value != "bok")
        _failure("DP-CLI-001", "scaffold workspace must be directory or bok")
    }
    if (parsed.positionals.size > 1)
      _failure("DP-CLI-001", "invalid scaffold command grammar")
    parsed.positionals.headOption.foreach { value =>
      if (!_slug_pattern.pattern.matcher(value).matches())
        _failure("DP-CLI-001", "scaffold slug must match [a-z0-9][a-z0-9._-]*")
    }
    if (parsed.positionals.isEmpty)
      _failure("DP-CLI-002", "scaffold requires <slug>")
    val profile = parsed.values.getOrElse("profile", _failure("DP-CLI-002", "scaffold requires --profile"))
    val language = parsed.values.getOrElse("language", _failure("DP-CLI-002", "scaffold requires --language"))
    val workspace = parsed.values.getOrElse("workspace", _failure("DP-CLI-002", "scaffold requires --workspace"))
    val parent = parsed.values.getOrElse("save", _failure("DP-CLI-002", "scaffold requires --save <parent>"))
    ScaffoldRequest(parsed.positionals.head, profile, language, workspace, parent)
  }

  private def _parse_options(args: List[String], valueoptions: Set[String], flagoptions: Set[String]): ParsedOptions = {
    val recognized = valueoptions ++ flagoptions
    if (args.exists(value => value.startsWith("-") && (!value.startsWith("--") || !recognized.contains(value.drop(2)))))
      _failure("DP-CLI-001", "unsupported option or option spelling")
    @annotation.tailrec
    def _go_(rest: List[String], values: Map[String, String], flags: Set[String], positionals: Vector[String]): ParsedOptions = rest match {
      case Nil => ParsedOptions(values, flags, positionals)
      case option :: tail if valueoptions.contains(option.drop(2)) =>
        val name = option.drop(2)
        if (values.contains(name))
          _failure("DP-CLI-001", s"duplicate --$name option")
        tail match {
          case value :: remainder if !value.startsWith("-") => _go_(remainder, values + (name -> value), flags, positionals)
          case _ => _failure("DP-CLI-002", s"--$name requires a value")
        }
      case option :: tail if flagoptions.contains(option.drop(2)) =>
        val name = option.drop(2)
        if (flags.contains(name))
          _failure("DP-CLI-001", s"duplicate --$name option")
        _go_(tail, values, flags + name, positionals)
      case value :: tail => _go_(tail, values, flags, positionals :+ value)
    }
    _go_(args, Map.empty, Set.empty, Vector.empty)
  }

  private def _admit_project(value: String): Path = {
    val project = try Paths.get(value).toAbsolutePath.normalize() catch {
      case NonFatal(_) => _failure("DP-PATH-001", "project path is invalid")
    }
    val name = Option(project.getFileName).map(_.toString).getOrElse("")
    if (!name.endsWith(".dox") || Files.isSymbolicLink(project) || !Files.isDirectory(project, LinkOption.NOFOLLOW_LINKS))
      _failure("DP-PATH-001", "project must be an existing direct non-symlink .dox directory")
    project
  }

  private def _load_project(project: Path): Descriptor = {
    val descriptorfile = _admitted_descriptor_file(project, "document-project.yaml", "descriptor")
    _admitted_content_directory(project.resolve("content"))
    val descriptorjson = _load_json(descriptorfile, "descriptor")
    _admit_raw_content_core_path(descriptorjson)
    val descriptor = _validate_descriptor(descriptorjson)
    val corefile = _admitted_descriptor_file(project, descriptor.contentCore, "Content Core")
    val corejson = _load_json(corefile, "Content Core")
    _validate_core(corejson, descriptor)
    descriptor
  }

  private def _validate_descriptor(value: Json): Descriptor = {
    val fields = _object(value, "descriptor")
    if (fields.keySet != _descriptor_keys)
      _descriptor_failure("descriptor must have exactly schema, id, workflow, profile, language, workspace, contentCore, activeOptionalWorkProducts, semanticScope")
    val schema = _string(fields, "schema", "descriptor")
    val id = _string(fields, "id", "descriptor")
    val workflow = _object(_field(fields, "workflow", "descriptor"), "workflow")
    val profile = _string(fields, "profile", "descriptor")
    val language = _string(fields, "language", "descriptor")
    val workspace = _object(_field(fields, "workspace", "descriptor"), "workspace")
    val contentcore = _string(fields, "contentCore", "descriptor")
    val activeoptionalworkproducts = _field(fields, "activeOptionalWorkProducts", "descriptor").asArray.getOrElse(_descriptor_failure("descriptor activeOptionalWorkProducts must be an array")).map { value =>
      value.asString.getOrElse(_descriptor_failure("descriptor activeOptionalWorkProducts must contain only Work Product ids"))
    }
    if (schema != "cozy.document-project.v2" || !_slug_pattern.pattern.matcher(id).matches())
      _descriptor_failure("descriptor schema or id is invalid")
    if (workflow.keySet != Set("schema", "id") || _string(workflow, "schema", "workflow") != "cozy.document-workflow.v2" || _string(workflow, "id", "workflow") != "document-production")
      _descriptor_failure("workflow must identify cozy.document-workflow.v2/document-production")
    if (!CozyDocumentWorkflow.isRegisteredProfile(profile))
      _descriptor_failure("descriptor profile is invalid")
    if (!_language_pattern.pattern.matcher(language).matches())
      _descriptor_failure("descriptor language is invalid")
    if (workspace.keySet != Set("kind") || !Set("directory", "bok").contains(_string(workspace, "kind", "workspace")))
      _descriptor_failure("descriptor workspace is invalid")
    val expectedcore = s"content/core-$language.yaml"
    if (contentcore != expectedcore || !_relative_path(contentcore))
      _descriptor_failure("descriptor contentCore is invalid")
    if (activeoptionalworkproducts.distinct.size != activeoptionalworkproducts.size)
      _descriptor_failure("descriptor activeOptionalWorkProducts must be duplicate-free")
    CozyDocumentWorkflow.resolve(profile, activeoptionalworkproducts.toVector) match {
      case Left(cause) => _descriptor_failure(cause)
      case Right(_) => ()
    }
    val semanticscope = _semantic_scope(_field(fields, "semanticScope", "descriptor"), id, language, s"$id:core:$language")
    Descriptor(id, profile, language, _string(workspace, "kind", "workspace"), contentcore, activeoptionalworkproducts.toVector, semanticscope)
  }

  private def _semantic_scope(value: Json, projectid: String, language: String, coreid: String): SemanticScope = {
    val fields = _object(value, "descriptor semanticScope")
    if (fields.keySet != Set("id", "localeVariants"))
      _descriptor_failure("descriptor semanticScope must have exactly id and localeVariants")
    val scopeid = _string(fields, "id", "descriptor semanticScope")
    if (!_slug_pattern.pattern.matcher(scopeid).matches())
      _descriptor_failure("descriptor semanticScope id is invalid")
    val values = _field(fields, "localeVariants", "descriptor semanticScope").asArray.getOrElse(_descriptor_failure("descriptor semanticScope localeVariants must be an array"))
    if (values.isEmpty)
      _descriptor_failure("descriptor semanticScope localeVariants must be non-empty")
    val variants = values.map { value =>
      val variant = _object(value, "descriptor semanticScope localeVariant")
      if (variant.keySet != Set("project", "language", "contentCore", "workProducts"))
        _descriptor_failure("descriptor semanticScope localeVariant must have exactly project, language, contentCore, workProducts")
      val project = _string(variant, "project", "descriptor semanticScope localeVariant")
      val variantlanguage = _string(variant, "language", "descriptor semanticScope localeVariant")
      val variantcore = _string(variant, "contentCore", "descriptor semanticScope localeVariant")
      if (!_slug_pattern.pattern.matcher(project).matches() || !_language_pattern.pattern.matcher(variantlanguage).matches() || !_nonempty_identity(variantcore))
        _descriptor_failure("descriptor semanticScope localeVariant identity is invalid")
      val workproducts = _field(variant, "workProducts", "descriptor semanticScope localeVariant").asArray.getOrElse(_descriptor_failure("descriptor semanticScope localeVariant workProducts must be an array")).map { value =>
        val workproduct = _object(value, "descriptor semanticScope localeVariant workProduct")
        if (workproduct.keySet != Set("id", "identity"))
          _descriptor_failure("descriptor semanticScope localeVariant workProduct must have exactly id and identity")
        val workproductid = _string(workproduct, "id", "descriptor semanticScope localeVariant workProduct")
        val identity = _string(workproduct, "identity", "descriptor semanticScope localeVariant workProduct")
        if (!CozyDocumentWorkflow.documentProduction.workProducts.exists(_.id == workproductid) || !_nonempty_identity(identity))
          _descriptor_failure("descriptor semanticScope localeVariant workProduct identity is invalid")
        SemanticWorkProduct(workproductid, identity)
      }.toVector
      if (workproducts.map(_.id).distinct.size != workproducts.size)
        _descriptor_failure("descriptor semanticScope localeVariant workProducts must be duplicate-free")
      LocaleVariant(project, variantlanguage, variantcore, workproducts)
    }.toVector
    if (variants.map(value => (value.project, value.language, value.contentCore)).distinct.size != variants.size)
      _descriptor_failure("descriptor semanticScope localeVariants must be duplicate-free")
    if (variants.count(value => value.project == projectid && value.language == language && value.contentCore == coreid) != 1)
      _descriptor_failure("descriptor semanticScope must contain the descriptor self localeVariant exactly once")
    SemanticScope(scopeid, variants)
  }

  private def _nonempty_identity(value: String): Boolean =
    value.nonEmpty && value == value.trim

  private[cozy] def _validate_core(value: Json, descriptor: Descriptor): Unit = {
    val fields = _object(value, "Content Core")
    if (fields.keySet != _core_keys)
      _descriptor_failure("Content Core must have exactly schema, id, language, accepted")
    if (_string(fields, "schema", "Content Core") != "cozy.content-core.v1")
      _descriptor_failure("Content Core schema is invalid")
    if (_string(fields, "id", "Content Core") != s"${descriptor.id}:core:${descriptor.language}")
      _descriptor_failure("Content Core id is invalid")
    if (_string(fields, "language", "Content Core") != descriptor.language)
      _descriptor_failure("Content Core language is invalid")
    val accepted = _field(fields, "accepted", "Content Core").asArray.getOrElse(_descriptor_failure("Content Core accepted must be an array"))
    val ids = accepted.map { entry =>
      val item = _object(entry, "Content Core accepted entry")
      if (item.keySet != Set("id", "text"))
        _descriptor_failure("Content Core accepted entries must have exactly id and text")
      val id = _string(item, "id", "Content Core accepted entry")
      val text = _string(item, "text", "Content Core accepted entry")
      if (!_slug_pattern.pattern.matcher(id).matches() || text.isEmpty || text.trim != text)
        _descriptor_failure("Content Core accepted entry is invalid")
      id
    }
    if (ids.distinct.size != ids.size)
      _descriptor_failure("Content Core accepted ids must be unique")
  }

  private def _verify_initial_sources(project: Path): Unit =
    Vector(
      "index.dox",
      "infographic/infographic.svg",
      "presentation/visual-pages.yaml",
      "review/README.md"
    ).foreach(_direct_file(project, _, "initial authored source"))

  private def _verify(project: Path, descriptor: Descriptor): String = {
    if (CozyDocumentWorkflow.isVideoProfile(descriptor.profile))
      _direct_file(project, "video/storyboard.md", "initial authored source")
    s"Cozy Document Project Verify\nproject: ${descriptor.id}\npackage: $project\nschema: cozy.document-project.v2"
  }

  private def _run(project: Path, descriptor: Descriptor, operationid: String, dryrun: Boolean): String = {
    val operation = CozyDocumentWorkflow.declaredOperation(operationid) match {
      case Right(Some(value)) => value
      case Right(None) => _failure("DP-OP-001", s"undeclared logical operation: $operationid")
      case Left(cause) => _descriptor_failure(cause)
    }
    val resolved = CozyDocumentWorkflow.resolve(descriptor.profile, descriptor.activeOptionalWorkProducts) match {
      case Right(value) => value
      case Left(cause) => _descriptor_failure(cause)
    }
    val activeproducts = resolved.workProducts.collect {
      case value if value.isParticipating => value.workProduct.id
    }.toSet
    if (!operation.produces.exists(activeproducts.contains))
      _failure("DP-OP-001", s"logical operation $operationid is disabled for profile ${descriptor.profile}")
    val provider = operation.providerBinding
    if (dryrun)
      s"Cozy Document Project Run\nproject: ${descriptor.id}\nschema: cozy.document-project.v2\noperation: $operationid\nprovider: $provider\nprofile: ${descriptor.profile}\nmode: dry-run\noutcome: not-recorded\nattempt: none"
    else {
      val attemptid = UUID.randomUUID().toString
      val attempt = _write_operation_attempt(project, descriptor, operationid, provider, attemptid)
      s"Cozy Document Project Run\nproject: ${descriptor.id}\nschema: cozy.document-project.v2\noperation: $operationid\nprovider: $provider\nprofile: ${descriptor.profile}\noutcome: recorded\nattempt: ${_project_relative(project, attempt)}"
    }
  }

  private def _write_operation_attempt(
    project: Path,
    descriptor: Descriptor,
    operationid: String,
    provider: String,
    attemptid: String
  ): Path = {
    val evidencedirectory = project.resolve("evidence").normalize()
    val attemptsdirectory = evidencedirectory.resolve("attempts").normalize()
    if (!evidencedirectory.startsWith(project) || !attemptsdirectory.startsWith(project))
      _failure("DP-PATH-001", "attempt evidence directory must be contained in the project")
    var createdevidence = false
    var createdattempts = false
    var temporary: Option[Path] = None
    try {
      if (Files.exists(evidencedirectory, LinkOption.NOFOLLOW_LINKS))
        _direct_directory(evidencedirectory, "attempt evidence directory")
      else {
        Files.createDirectory(evidencedirectory)
        createdevidence = true
      }
      if (Files.exists(attemptsdirectory, LinkOption.NOFOLLOW_LINKS))
        _direct_directory(attemptsdirectory, "attempts directory")
      else {
        Files.createDirectory(attemptsdirectory)
        createdattempts = true
      }
      val destination = attemptsdirectory.resolve(s"$attemptid.yaml").normalize()
      if (!destination.startsWith(attemptsdirectory) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination))
        _failure("DP-PATH-001", s"attempt evidence already exists or has an unsafe path: $attemptid")
      val temporaryfile = Files.createTempFile(attemptsdirectory, s".$attemptid-", ".tmp")
      temporary = Some(temporaryfile)
      Files.write(temporaryfile, _operation_attempt_yaml(project, descriptor, operationid, provider, attemptid).getBytes(StandardCharsets.UTF_8))
      Files.move(temporaryfile, destination, StandardCopyOption.ATOMIC_MOVE)
      temporary = None
      destination
    } catch {
      case _: AtomicMoveNotSupportedException => _failure("DP-PATH-001", "attempt evidence requires an atomic move")
      case NonFatal(_) => _failure("DP-PATH-001", "attempt evidence cannot be published without overwriting existing evidence")
    } finally {
      temporary.foreach(Files.deleteIfExists)
      if (createdattempts && Files.exists(attemptsdirectory, LinkOption.NOFOLLOW_LINKS)) {
        try Files.deleteIfExists(attemptsdirectory) catch { case NonFatal(_) => () }
      }
      if (createdevidence && Files.exists(evidencedirectory, LinkOption.NOFOLLOW_LINKS)) {
        try Files.deleteIfExists(evidencedirectory) catch { case NonFatal(_) => () }
      }
    }
  }

  private def _operation_attempt_yaml(
    project: Path,
    descriptor: Descriptor,
    operationid: String,
    provider: String,
    attemptid: String
  ): String = {
    val sources = _state_sources(project, descriptor)
    val expectedsourcecount = if (CozyDocumentWorkflow.isVideoProfile(descriptor.profile)) 7 else 6
    if (sources.size != expectedsourcecount)
      _failure("DP-PATH-001", "initial authored sources changed before attempt evidence publication")
    val inputs = sources.map { case (relative, path) =>
      s"  - path: $relative\n    sha256: ${_sha256(path)}"
    }
    (Vector(
      "schema: cozy.document-operation-attempt.v1",
      s"id: $attemptid",
      s"operation: $operationid",
      s"provider: $provider",
      s"profile: ${descriptor.profile}",
      "inputs:"
    ) ++ inputs ++ Vector(
      "outcome: recorded",
      "diagnostics:",
      "  - provider execution is deferred; this dispatch was recorded only",
      "outputs: []",
      "receipt: none"
    )).mkString("\n") + "\n"
  }

  private[cozy] def _project_relative(project: Path, path: Path): String =
    project.relativize(path).toString.replace('\\', '/')

  private def _inspect(project: Path, descriptor: Descriptor, state: Path): String =
    _with_state(s"Cozy Document Project Inspect\nproject: ${descriptor.id}\npackage: $project\nschema: cozy.document-project.v2\nworkflow: document-production\nprofile: ${descriptor.profile}\nlanguage: ${descriptor.language}\nworkspace: ${descriptor.workspace}", state)

  private def _with_state(output: String, state: Path): String = {
    val reference = Vector(state.getParent.getParent.getFileName, state.getParent.getFileName, state.getFileName).mkString("/")
    s"$output\nstate: $reference"
  }

  private def _write_state_snapshot(project: Path, descriptor: Descriptor): Path = {
    val statedirectory = project.resolve("target").resolve("document-project").normalize()
    if (!statedirectory.startsWith(project) || Files.isSymbolicLink(project.resolve("target")) || Files.isSymbolicLink(statedirectory))
      _failure("DP-PATH-001", "state cache directory must be contained in the project and must not be a symbolic link")
    val state = statedirectory.resolve("state.yaml")
    if (Files.isSymbolicLink(state))
      _failure("DP-PATH-001", "state cache file must not be a symbolic link")
    val stateyaml = _state_yaml(project, descriptor)
    try {
      Files.createDirectories(statedirectory)
      val temporary = Files.createTempFile(statedirectory, ".state-", ".tmp")
      try {
        Files.writeString(temporary, stateyaml, StandardCharsets.UTF_8)
        Files.move(temporary, state, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        state
      } finally {
        Files.deleteIfExists(temporary)
      }
    } catch {
      case NonFatal(_) => _failure("DP-PATH-001", "state cache cannot be written")
    }
  }

  private def _state_yaml(project: Path, descriptor: Descriptor): String = {
    CozyDocumentProjectEvidence.stateYaml(project, descriptor)
  }

  private[cozy] def _state_sources(project: Path, descriptor: Descriptor): Vector[(String, Path)] = {
    val declared = Vector(
      "document-project.yaml",
      descriptor.contentCore,
      "index.dox",
      "infographic/infographic.svg",
      "presentation/visual-pages.yaml",
      "review/README.md"
    ) ++ (if (CozyDocumentWorkflow.isVideoProfile(descriptor.profile)) Vector("video/storyboard.md") else Vector.empty)
    declared.flatMap { relative =>
      val path = project.resolve(relative).normalize()
      if (_is_direct_source(project, path)) Some(relative.replace('\\', '/') -> path) else None
    }
  }

  private def _is_direct_source(project: Path, path: Path): Boolean = {
    if (!path.startsWith(project) || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      false
    else {
      val relative = project.relativize(path)
      var parent = project
      (0 until relative.getNameCount - 1).forall { index =>
        parent = parent.resolve(relative.getName(index))
        !Files.isSymbolicLink(parent) && Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
      }
    }
  }

  private[cozy] def _core_has_accepted_entries(project: Path, descriptor: Descriptor): Boolean =
    _load_json(project.resolve(descriptor.contentCore), "Content Core").hcursor.downField("accepted").focus.flatMap(_.asArray).exists(_.nonEmpty)

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _plan(project: Path, descriptor: Descriptor): String = {
    val workflowplan = CozyDocumentWorkflow.plan(descriptor.profile, descriptor.activeOptionalWorkProducts) match {
      case Right(value) => value
      case Left(cause) => _descriptor_failure(cause)
    }
    val requiredlines = workflowplan.selectedWorkProducts.collect {
      case value if value.selection == CozyDocumentWorkflow.WorkProductSelection.Required => s"required: ${_plan_work_product_line(value)}"
    }
    val activeoptionallines = workflowplan.selectedWorkProducts.collect {
      case value if value.selection == CozyDocumentWorkflow.WorkProductSelection.ActiveOptional => s"active-optional: ${_plan_work_product_line(value)}"
    }
    val inactiveoptionallines = workflowplan.inactiveOptionalWorkProducts.map(value => s"inactive-optional: ${_plan_work_product_line(value)}")
    val profiledisabledlines = workflowplan.profileDisabledWorkProducts.map(value => s"profile-disabled: ${_plan_work_product_line(value)}")
    val blockedlines = workflowplan.blockedOperations.map(value => s"blocked: operation ${value.id} [${CozyDocumentWorkflow.executionReservedExplanation}]")
    val eligiblelines = workflowplan.eligibleOperations.map(value => s"eligible: operation ${value.id} [provider: ${value.providerBinding}]")
    (Vector(
      "Cozy Document Project Plan",
      s"project: ${descriptor.id}",
      s"package: $project",
      "schema: cozy.document-project.v2"
    ) ++ requiredlines ++ activeoptionallines ++ inactiveoptionallines ++ profiledisabledlines ++ blockedlines ++ eligiblelines).mkString("\n")
  }

  private def _plan_work_product_line(value: CozyDocumentWorkflow.ResolvedWorkProduct): String = {
    val product = value.workProduct
    val binding = value.binding
    val selection = value.selection match {
      case CozyDocumentWorkflow.WorkProductSelection.ActiveOptional => "; selected"
      case CozyDocumentWorkflow.WorkProductSelection.InactiveOptional => "; not-selected"
      case _ => ""
    }
    val reason = binding.reason.map(text => s": $text").getOrElse("")
    s"work-product ${product.id} [${product.role.value}, ${binding.disposition.value}$selection$reason]"
  }

  private def _scaffold(slug: String, profile: String, language: String, workspace: String, parentvalue: String): Path = {
    val parent = _scaffold_parent(parentvalue)
    val destination = parent.resolve(s"$slug.dox").normalize()
    if (!destination.startsWith(parent) || Files.isSymbolicLink(destination))
      _failure("DP-PATH-001", "scaffold destination path is unsafe")
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
      _failure("DP-SCAFFOLD-001", s"scaffold destination already exists: $destination")
    var temporary: Option[Path] = None
    try {
      val staging = Files.createTempDirectory(parent, ".cozy-document-project-")
      temporary = Some(staging)
      _write(staging.resolve("document-project.yaml"), _descriptor_yaml(slug, profile, language, workspace))
      _write(staging.resolve("index.dox"), s"$slug\n${"=" * slug.length}\n\nDocument Project source.\n")
      _write(staging.resolve(s"content/core-$language.yaml"), _core_yaml(slug, language))
      _write(staging.resolve("infographic/infographic.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"><title>Document Project infographic</title></svg>\n")
      _write(staging.resolve("presentation/visual-pages.yaml"), "pages: []\n")
      _write(staging.resolve("review/README.md"), "# Review\n\nReview material belongs here.\n")
      if (CozyDocumentWorkflow.isVideoProfile(profile))
        _write(staging.resolve("video/storyboard.md"), "# Storyboard\n\nStoryboard source belongs here.\n")
      Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
      temporary = None
      destination
    } catch {
      case _: AtomicMoveNotSupportedException => _failure("DP-SCAFFOLD-001", "atomic scaffold installation is not supported")
      case NonFatal(e) => _failure("DP-SCAFFOLD-001", s"scaffold installation failed: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}")
    } finally {
      temporary.foreach(_delete_temporary)
    }
  }

  private def _scaffold_parent(value: String): Path = {
    val parent = try Paths.get(value).toAbsolutePath.normalize() catch {
      case NonFatal(_) => _failure("DP-PATH-001", "scaffold parent path is invalid")
    }
    if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(parent))
      _failure("DP-PATH-001", "scaffold parent must not be a symbolic link")
    var ancestor = Option(parent.getParent)
    while (ancestor.nonEmpty) {
      val ancestorpath = ancestor.get
      val ancestorparent = Option(ancestorpath.getParent)
      if (!ancestorparent.exists(_.getParent == null) && Files.exists(ancestorpath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(ancestorpath)) {
        _failure("DP-PATH-001", "scaffold parent ancestry must not contain a symbolic link")
      }
      ancestor = ancestorparent
    }
    if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS))
      _failure("DP-SCAFFOLD-001", "scaffold parent must be an existing real directory")
    if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
      _failure("DP-PATH-001", "scaffold parent must be a direct non-symlink directory")
    parent
  }

  private[cozy] def _direct_directory(path: Path, label: String): Unit =
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      _failure("DP-PATH-001", s"$label must be a direct non-symlink directory")

  private def _admitted_content_directory(path: Path): Unit = {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
      _failure("DP-DESC-001", "Content Core directory is missing")
    _direct_directory(path, "content directory")
  }

  private def _admitted_descriptor_file(project: Path, relative: String, label: String): Path = {
    val candidate = project.resolve(relative).normalize()
    if (!candidate.startsWith(project))
      _failure("DP-PATH-001", s"$label escapes the project package")
    if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS))
      _failure("DP-DESC-001", s"$label is missing")
    _direct_file(project, relative, label)
  }

  private[cozy] def _direct_file(project: Path, relative: String, label: String): Path = {
    val candidate = project.resolve(relative).normalize()
    if (!candidate.startsWith(project))
      _failure("DP-PATH-001", s"$label escapes the project package")
    val path = project.relativize(candidate)
    var parent = project
    (0 until path.getNameCount - 1).foreach { index =>
      parent = parent.resolve(path.getName(index))
      _direct_directory(parent, s"$label parent")
    }
    if (Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
      _failure("DP-PATH-001", s"$label must be a direct regular non-symlink file")
    candidate
  }

  private[cozy] def _load_json(path: Path, label: String): Json =
    try {
      Files.readString(path, StandardCharsets.UTF_8)
      StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
    } catch {
      case NonFatal(_) => _failure("DP-DESC-001", s"$label is missing, unreadable, or malformed")
    }

  private def _admit_raw_content_core_path(value: Json): Unit =
    value.asObject.flatMap(_.apply("contentCore")).flatMap(_.asString).foreach { contentcore =>
      if (!_relative_path(contentcore) || contentcore.split("/", -1).exists(_.isEmpty))
        _failure("DP-PATH-001", "descriptor contentCore path is unsafe")
    }

  private[cozy] def _object(value: Json, label: String): Map[String, Json] =
    value.asObject.map(_.toMap).getOrElse(_descriptor_failure(s"$label must be an object"))

  private[cozy] def _field(fields: Map[String, Json], name: String, label: String): Json =
    fields.getOrElse(name, _descriptor_failure(s"$label is missing $name"))

  private[cozy] def _string(fields: Map[String, Json], name: String, label: String): String =
    _field(fields, name, label).asString.getOrElse(_descriptor_failure(s"$label $name must be a string"))

  private def _relative_path(value: String): Boolean = {
    val path = try Paths.get(value) catch { case NonFatal(_) => return false }
    !path.isAbsolute && path.iterator().asScala.forall(part => part.toString != "." && part.toString != "..")
  }

  private def _descriptor_yaml(slug: String, profile: String, language: String, workspace: String): String =
    s"""schema: cozy.document-project.v2
       |id: $slug
       |workflow:
       |  schema: cozy.document-workflow.v2
       |  id: document-production
       |profile: $profile
       |language: $language
       |workspace:
       |  kind: $workspace
       |contentCore: content/core-$language.yaml
       |activeOptionalWorkProducts: []
       |semanticScope:
       |  id: $slug
       |  localeVariants:
       |    - project: $slug
       |      language: $language
       |      contentCore: $slug:core:$language
       |      workProducts: []
       |""".stripMargin

  private def _core_yaml(slug: String, language: String): String =
    s"""schema: cozy.content-core.v1
       |id: $slug:core:$language
       |language: $language
       |accepted: []
       |""".stripMargin

  private def _scaffold_result(destination: Path, slug: String, profile: String, language: String, workspace: String): String =
    s"Cozy Document Project Scaffold\nproject: $slug\npackage: $destination\nschema: cozy.document-project.v2\nworkflow: document-production\nprofile: $profile\nlanguage: $language\nworkspace: $workspace"

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _delete_temporary(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }

  private[cozy] def _descriptor_failure(cause: String): Nothing = _failure("DP-DESC-002", cause)

  private[cozy] def _failure(token: String, cause: String): Nothing =
    RAISE.invalidArgumentFault(s"$token: $cause")
}
