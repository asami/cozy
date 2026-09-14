package cozy.document

import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, StandardCopyOption}
import scala.collection.mutable
import scala.util.control.NonFatal

/*
 * @since   Sep. 14, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectLocalBuildCommand {
  final case class Result(
    targetId: String,
    inputs: Vector[String],
    output: String,
    outcome: String
  ) {
    def report: String = {
      val inputlines = inputs.map(value => s"  - $value").mkString("\n")
      s"Cozy Document Project Local Build\ntarget: $targetId\ninputs:\n$inputlines\noutput: $output\noutcome: $outcome"
    }
  }

  private final case class TargetDefinition(inputs: Vector[String], prerequisites: Vector[String])
  private final case class Configuration(path: Path, definitions: Map[String, TargetDefinition])
  private final case class Plan(
    project: Path,
    contract: CozyDocumentProjectLocalBuild.TargetContract,
    definition: TargetDefinition,
    inputs: Vector[(String, Path)],
    output: Path,
    locale: Option[String]
  )

  private val _schema = "cozy.document-project.local-build-targets.v1"
  private val _config_relative = "build/document-project-targets.yaml"
  private val _locale_pattern = "[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*".r

  def execute(project: Path, targetId: Option[String], force: Boolean): Result = {
    val selected = try CozyDocumentProjectLocalBuild.select(targetId) catch {
      case value: CozyDocumentProjectLocalBuild.UnknownTarget =>
        CozyDocumentProject._failure("DP-CLI-001", value.getMessage)
    }
    val configuration = _load_configuration(project)
    val closure = _closure(project, configuration, selected.id)
    val plans = closure.map(value => value.contract.id -> value).toMap
    val outcomes = mutable.Map.empty[String, String]

    closure.foreach { plan =>
      val generated = plan.definition.prerequisites.map { prerequisite =>
        val prerequisiteplan = plans.getOrElse(prerequisite, _failure("missing generated prerequisite producer", prerequisite))
        _project_relative(project, prerequisiteplan.output) -> prerequisiteplan.output
      }
      val observation = CozyDocumentProjectLocalBuild.FreshnessObservation(
        (plan.inputs ++ generated).map { case (relative, path) =>
          CozyDocumentProjectLocalBuild.InputObservation(relative, Some(_modified_time(path)))
        },
        CozyDocumentProjectLocalBuild.OutputObservation(_output_time(plan.output)),
        force
      )
      CozyDocumentProjectLocalBuild.evaluate(observation) match {
        case CozyDocumentProjectLocalBuild.FreshnessDecision.ReuseCurrentOutput =>
          outcomes += plan.contract.id -> "reused"
        case _ =>
          _install(plan, _render(plan))
          outcomes += plan.contract.id -> "built"
      }
    }

    val plan = plans(selected.id)
    Result(selected.id, plan.inputs.map(_._1), _project_relative(project, plan.output), outcomes(selected.id))
  }

  private def _load_configuration(project: Path): Configuration = {
    val path = CozyDocumentProject._direct_file(project, _config_relative, "local-build target definition")
    val fields = _object(CozyDocumentProject._load_json(path, "local-build target definition"), "local-build target definition")
    if (fields.keySet != Set("schema", "targets"))
      _failure("target definition must have exactly schema and targets", "")
    if (_string(fields, "schema", "local-build target definition") != _schema)
      _failure("target definition schema is invalid", "")
    val targets = _object(_field(fields, "targets", "local-build target definition"), "local-build target definition targets")
    val contracts = CozyDocumentProjectLocalBuild.targetContracts.map(value => value.id -> value).toMap
    if (targets.keySet != contracts.keySet)
      _failure("target definition must declare exactly the frozen target IDs", "")
    val definitions = contracts.map { case (id, contract) =>
      id -> _definition(id, contract, _field(targets, id, "local-build target definition targets"))
    }
    Configuration(path, definitions)
  }

  private def _definition(
    id: String,
    contract: CozyDocumentProjectLocalBuild.TargetContract,
    value: Json
  ): TargetDefinition = {
    val fields = _object(value, s"target definition $id")
    if (!Set(Set("inputs"), Set("inputs", "prerequisites")).contains(fields.keySet))
      _failure(s"target definition $id must have exactly inputs and optional prerequisites", "")
    val inputs = _strings(_field(fields, "inputs", s"target definition $id"), s"target definition $id inputs")
    val prerequisites = fields.get("prerequisites").map(_strings(_, s"target definition $id prerequisites")).getOrElse(Vector.empty)
    if (inputs.distinct.size != inputs.size || prerequisites.distinct.size != prerequisites.size)
      _failure(s"target definition $id inputs and prerequisites must be duplicate-free", "")
    inputs.foreach(value => _require_relative(value, s"target definition $id input"))
    val expectedids = CozyDocumentProjectLocalBuild.targetContracts.map(_.id).toSet
    prerequisites.foreach { prerequisite =>
      if (!expectedids.contains(prerequisite))
        _failure(s"target definition $id has unknown prerequisite", prerequisite)
    }
    _locale_for(id, contract, inputs)
    TargetDefinition(inputs, prerequisites)
  }

  private def _closure(project: Path, configuration: Configuration, selectedid: String): Vector[Plan] = {
    val contracts = CozyDocumentProjectLocalBuild.targetContracts.map(value => value.id -> value).toMap
    val plans = mutable.Map.empty[String, Plan]
    val complete = mutable.Set.empty[String]
    val visiting = mutable.ArrayBuffer.empty[String]
    val order = mutable.ArrayBuffer.empty[Plan]

    def _plan_(id: String): Plan = plans.getOrElseUpdate(id, {
      val contract = contracts.getOrElse(id, _failure("missing target producer", id))
      val definition = configuration.definitions.getOrElse(id, _failure("missing target definition", id))
      val locale = _locale_for(id, contract, definition.inputs)
      val inputs = Vector(_config_relative -> configuration.path) ++ definition.inputs.map { relative =>
        relative -> CozyDocumentProject._direct_file(project, relative, s"local-build $id input")
      }
      val output = _output(project, contract)
      if (inputs.exists { case (_, input) => output == input || output.startsWith(input) })
        _failure("target output collides with a declared source", id)
      Plan(project, contract, definition, inputs, output, locale)
    })

    def _visit_(id: String): Unit = {
      if (visiting.contains(id)) {
        val cycle = (visiting.dropWhile(_ != id) :+ id).mkString(" -> ")
        _failure("target prerequisite graph contains a cycle", cycle)
      }
      if (!complete.contains(id)) {
        visiting += id
        val plan = _plan_(id)
        plan.definition.prerequisites.foreach(_visit_)
        visiting.remove(visiting.size - 1)
        complete += id
        order += plan
      }
    }

    _visit_(selectedid)
    val values = order.toVector
    values.combinations(2).foreach {
      case Vector(left, right) if left.output == right.output => _failure("two targets share one output", left.contract.id)
      case _ => ()
    }
    values.foreach { plan =>
      values.filterNot(_ == plan).foreach { other =>
        if (plan.inputs.exists { case (_, input) => other.output == input || other.output.startsWith(input) })
          _failure("target output collides with a declared source", other.contract.id)
      }
    }
    values
  }

  private def _output(project: Path, contract: CozyDocumentProjectLocalBuild.TargetContract): Path = {
    val root = project.resolve(contract.outputRoot).normalize()
    val output = root.resolve("index.html").normalize()
    if (!root.startsWith(project) || !output.startsWith(root) || output.getFileName.toString != "index.html")
      _failure("target output escapes its project root", contract.id)
    _admit_output_directory(project, root)
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(output) || !Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)))
      _failure("target output must be a direct regular file or absent", contract.id)
    output
  }

  private def _admit_output_directory(project: Path, directory: Path): Unit = {
    val relative = project.relativize(directory)
    var current = project
    val iterator = relative.iterator()
    while (iterator.hasNext) {
      val part = iterator.next()
      current = current.resolve(part)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
        _failure("target output parent must be a direct directory", current.toString)
    }
  }

  private def _render(plan: Plan): String = plan.contract.id match {
    case "document-structure-html" =>
      val validated = _document(plan)
      val vocabulary = _vocabulary(plan, validated)
      CozyDocumentConfirmationProjectionV2.render(validated, vocabulary).html
    case "document-reader-html" =>
      val validated = _document(plan)
      val vocabulary = _vocabulary(plan, validated)
      CozyDocumentReaderProjectionV2.render(validated, vocabulary).html
    case "smartdox-article-html" =>
      CozyDocumentProjectSmartDoxArticleHtml.render(_input(plan, "index.dox").getParent).html
    case value => _failure("target renderer is missing", value)
  }

  private def _document(plan: Plan): CozyDocumentDescriptionV2.ValidatedDocument = {
    val core = _input(plan, "content/core.yaml")
    val locale = plan.locale.getOrElse(_failure("document target locale is missing", plan.contract.id))
    val document = _input(plan, s"content/$locale/document.yaml")
    CozyDocumentDescriptionV2.loadDocument(core, document)
  }

  private def _vocabulary(
    plan: Plan,
    document: CozyDocumentDescriptionV2.ValidatedDocument
  ): CozyDocumentConfirmationProjectionV2.Vocabulary = {
    val locale = plan.locale.getOrElse(_failure("document target locale is missing", plan.contract.id))
    CozyDocumentConfirmationVocabulary.loadDocument(_input(plan, s"content/$locale/confirmation-vocabulary.yaml"), document.description.locale)
  }

  private def _input(plan: Plan, relative: String): Path =
    plan.inputs.find(_._1 == relative).map(_._2).getOrElse(_failure("declared target input is missing", relative))

  private def _install(plan: Plan, html: String): Unit = {
    val directory = plan.output.getParent
    _ensure_output_directory(plan.project, directory)
    var temporary: Option[Path] = None
    try {
      val path = Files.createTempFile(directory, ".cozy-document-project-local-build-", ".tmp")
      temporary = Some(path)
      Files.writeString(path, html, StandardCharsets.UTF_8)
      Files.move(path, plan.output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      temporary = None
    } catch {
      case _: AtomicMoveNotSupportedException => _failure("atomic target installation is not supported", plan.contract.id)
      case NonFatal(value) => _failure("target installation failed", Option(value.getMessage).getOrElse(value.getClass.getSimpleName))
    } finally {
      temporary.foreach { path =>
        try Files.deleteIfExists(path)
        catch { case NonFatal(_) => () }
      }
    }
  }

  private def _ensure_output_directory(root: Path, directory: Path): Unit = {
    var current = root
    val relative = root.relativize(directory)
    val iterator = relative.iterator()
    while (iterator.hasNext) {
      val part = iterator.next()
      current = current.resolve(part)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
          _failure("target output parent must be a direct directory", current.toString)
      } else {
        try Files.createDirectory(current)
        catch { case NonFatal(value) => _failure("target output directory cannot be created", Option(value.getMessage).getOrElse(current.toString)) }
        if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
          _failure("target output parent must be a direct directory", current.toString)
      }
    }
  }

  private def _locale_for(
    id: String,
    contract: CozyDocumentProjectLocalBuild.TargetContract,
    inputs: Vector[String]
  ): Option[String] = id match {
    case "smartdox-article-html" =>
      if (inputs != Vector("index.dox") || contract.sourceCategories != Vector("config", "index.dox"))
        _failure("article target inputs must exactly declare index.dox", "")
      None
    case "document-structure-html" | "document-reader-html" =>
      val documents = inputs.collect {
        case value if value.startsWith("content/") && value.endsWith("/document.yaml") => value
      }
      if (documents.size != 1)
        _failure(s"document target $id must declare one localized document", "")
      val document = documents.head.split("/", -1)
      if (document.length != 3 || !_locale_pattern.pattern.matcher(document(1)).matches())
        _failure(s"document target $id locale path is invalid", documents.head)
      val locale = document(1)
      val expected = Set("content/core.yaml", s"content/$locale/document.yaml", s"content/$locale/confirmation-vocabulary.yaml")
      if (inputs.toSet != expected || inputs.size != expected.size || contract.sourceCategories != Vector("config", "content/core.yaml", "content/<locale>/document.yaml", "content/<locale>/confirmation-vocabulary.yaml"))
        _failure(s"document target $id inputs do not match the frozen source categories", "")
      Some(locale)
    case value => _failure("target contract is unknown", value)
  }

  private def _output_time(path: Path): Option[Long] =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Some(_modified_time(path)) else None

  private def _modified_time(path: Path): Long =
    Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis

  private def _project_relative(project: Path, path: Path): String =
    project.relativize(path).toString.replace('\\', '/')

  private def _require_relative(value: String, label: String): Unit = {
    val path = try java.nio.file.Paths.get(value) catch { case NonFatal(_) => _failure(s"$label path is invalid", value) }
    val iterator = path.iterator()
    var unsafe = value.isEmpty || path.isAbsolute
    while (iterator.hasNext && !unsafe) {
      val part = iterator.next().toString
      unsafe = part == "." || part == ".."
    }
    if (unsafe)
      _failure(s"$label path is unsafe", value)
  }

  private def _object(value: Json, label: String): Map[String, Json] =
    value.asObject.map(_.toMap).getOrElse(_failure(s"$label must be an object", ""))

  private def _field(fields: Map[String, Json], name: String, label: String): Json =
    fields.getOrElse(name, _failure(s"$label is missing $name", ""))

  private def _string(fields: Map[String, Json], name: String, label: String): String =
    _field(fields, name, label).asString.getOrElse(_failure(s"$label $name must be a string", ""))

  private def _strings(value: Json, label: String): Vector[String] =
    value.asArray.getOrElse(_failure(s"$label must be an array", "")).map { item =>
      item.asString.getOrElse(_failure(s"$label must contain only strings", ""))
    }.toVector

  private def _failure(cause: String, value: String): Nothing = {
    val suffix = if (value.isEmpty) "" else s": $value"
    CozyDocumentProject._failure("DP-OP-001", cause + suffix)
  }
}
