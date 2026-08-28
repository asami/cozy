package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.bok.scenario.ScenarioMetadata
import cozy.bok.BibliographyEntry._
import cozy.config.CozyProjectYamlConfig
import cozy.publication.{CozyArticleMediaBuildContext, CozyArticleMediaInfographicCommand, CozyArticleMediaInfographicEvidence, CozyArticleMediaVideoCommand}
import cozy.video.{CozyVideo, CozyVideoPublisher}
import org.smartdox.{Body, Document, Dox}
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.Dox2HtmlTransformer
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.metadata.DocumentMetaData
import org.goldenport.i18n.I18NContext
import java.net.URLEncoder
import java.time.{Instant, LocalDate, LocalDateTime, YearMonth, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.control.NonFatal
import scala.sys.process._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import io.circe.syntax._

/*
 * @since   Aug. 14, 2026
 * @version Aug. 29, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokSieMetadata {
  self: CozyBokImplementation.type =>
  private final case class FinalizationBackup(target: Path, backup: Option[Path])
  private final case class RdfExtensionDeclaration(
    id: String,
    kind: String,
    nodes: Vector[Json],
    edges: Vector[Json],
    logicalpath: String
  )

  private val _finalization_file_allowlist = Vector(
    "rdf/site.ttl",
    "rdf/site.jsonld",
    "metadata/rdf/graph.json",
    "metadata/glossary/terms.json",
    "metadata/bibliography/bibliography.json",
    "metadata/scenarios/scenarios.json",
    "metadata/tags/tags.json",
    "metadata/sie/integration.json",
    "metadata/cncf/knowledge-source.json"
  )

  private val _finalization_directory_allowlist = Vector(
    "metadata/repository/car",
    "metadata/cncf/component-references",
    "metadata/catalog/projects",
    "metadata/projects",
    "metadata/artifacts/repository",
    "metadata/releases"
  )

  def finalizeMetadata(config: BuildConfig): Unit = {
    val projectroot = _finalization_project_root(config.project)
    val admittedsource = _admit_finalization_source(config.sourcepath, projectroot)
    val source = _finalization_root(config.doxsitePath, projectroot, "BoK generated metadata root")
    _validate_published_resources(source)
    val target = _finalization_root(config.websitePath, projectroot, "BoK website root")
    if (source.startsWith(target) || target.startsWith(source))
      RAISE.invalidArgumentFault("BoK generated metadata root and website root must be distinct, non-overlapping directories.")
    val stage = Files.createTempDirectory(projectroot, ".cozy-bok-finalize-")
    try {
      _copy_machine_metadata_artifacts(config, source, stage, admittedsource)
      _validate_staged_metadata(stage)
      _commit_staged_metadata(stage, target, projectroot)
    } finally {
      _delete_directory(stage)
    }
  }

  private[bok] def _copy_machine_metadata_artifacts(config: BuildConfig, target: Path): Unit =
    {
      val projectroot = _finalization_project_root(config.project)
      val admittedsource = _admit_finalization_source(config.sourcepath, projectroot)
      val source = _finalization_root(config.doxsitePath, projectroot, "BoK generated metadata root")
      _validate_published_resources(source)
      _copy_machine_metadata_artifacts(config, source, target, admittedsource)
    }

  private def _copy_machine_metadata_artifacts(
      config: BuildConfig,
      source: Path,
      target: Path,
      admittedsource: Path
  ): Unit = {
    _copy_finalization_file(source, target, "site.ttl", "rdf/site.ttl")
    _copy_finalization_file(source, target, "site.jsonld", "rdf/site.jsonld")
    _copy_finalization_file(source, target, "metadata/rdf/graph.json", "metadata/rdf/graph.json")
    _copy_finalization_file(source, target, "metadata/glossary/terms.json", "metadata/glossary/terms.json")
    _copy_finalization_file(source, target, "metadata/bibliography/bibliography.json", "metadata/bibliography/bibliography.json")
    _copy_finalization_file(source, target, "metadata/scenarios/scenarios.json", "metadata/scenarios/scenarios.json")
    _copy_finalization_file(source, target, "metadata/tags/tags.json", "metadata/tags/tags.json")
    _copy_finalization_directory(source, target, "metadata/repository/car")
    _copy_finalization_directory(source, target, "metadata/cncf/component-references")
    _copy_finalization_directory(source, target, "metadata/catalog/projects")
    _copy_finalization_directory(source, target, "metadata/projects")
    _copy_finalization_directory(source, target, "metadata/artifacts/repository")
    _copy_finalization_directory(source, target, "metadata/releases")
    _sync_sie_metadata(config, target)
    _apply_rdf_extensions(config, target)
    _version_graph_summary(config, target)
    _write_knowledge_source_manifest(config, admittedsource, target)
  }

  private[bok] def _finalization_project_root(project: Path): Path = {
    val normalized = project.toAbsolutePath.normalize()
    if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"BoK project root must be an existing non-symbolic-link directory: $project")
    normalized
  }

  private def _finalization_root(path: Path, projectroot: Path, label: String): Path = {
    val normalized = path.toAbsolutePath.normalize()
    if (!normalized.startsWith(projectroot))
      RAISE.invalidArgumentFault(s"$label must be a direct directory inside the project root: $path")
    _validate_finalization_parent(projectroot, Option(normalized.getParent).getOrElse(projectroot))
    if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"$label must be an existing non-symbolic-link directory inside the project root: $path")
    normalized
  }

  private[bok] def _admit_finalization_source(source: Path, projectroot: Path): Path = {
    val normalized = source.toAbsolutePath.normalize()
    if (!normalized.startsWith(projectroot))
      RAISE.invalidArgumentFault(s"BoK configured source root must be inside the project root: $source")
    if (normalized != projectroot)
      _validate_finalization_parent(
        projectroot,
        Option(normalized.getParent).getOrElse(projectroot),
        "BoK configured source root"
      )
    if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(
        s"BoK configured source root must be an existing non-symbolic-link directory inside the project root: $source"
      )
    val canonicalprojectroot = projectroot.toRealPath()
    val canonical = normalized.toRealPath()
    if (!canonical.startsWith(canonicalprojectroot))
      RAISE.invalidArgumentFault(s"BoK configured source root must resolve below the project root: $source")
    canonical
  }

  private def _validate_published_resources(source: Path): Unit = {
    _validate_generated_glossary(source)
    Vector("car", "sar").foreach(kind => _validate_generated_component_reference_index(source, kind))
  }

  private def _validate_generated_glossary(source: Path): Unit = {
    val relative = "metadata/glossary/terms.json"
    val path = _finalization_input(source, relative)
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        RAISE.invalidArgumentFault(s"BoK generated metadata input must be a regular file when present: $path")
      val json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
        error => RAISE.invalidArgumentFault(
          s"Invalid BoK glossary metadata $relative: ${error.message}"
        ),
        identity
      )
      json.as[TermIndex].fold(
        error => RAISE.invalidArgumentFault(
          s"Invalid BoK glossary metadata $relative: ${error.message}"
        ),
        _ => ()
      )
    }
  }

  private def _validate_generated_component_reference_index(source: Path, kind: String): Unit = {
    val relative = s"metadata/cncf/component-references/$kind.json"
    val path = _finalization_input(source, relative)
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        RAISE.invalidArgumentFault(s"BoK generated metadata input must be a regular file when present: $path")
      _load_graph_component_reference_index(source, kind)
    }
  }

  private def _copy_finalization_file(source: Path, target: Path, input: String, output: String): Unit = {
    val sourcepath = _finalization_input(source, input)
    if (Files.exists(sourcepath, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isSymbolicLink(sourcepath) || !Files.isRegularFile(sourcepath, LinkOption.NOFOLLOW_LINKS))
        RAISE.invalidArgumentFault(s"BoK generated metadata input must be a regular file when present: $sourcepath")
      val targetpath = target.resolve(output).normalize()
      if (!targetpath.startsWith(target))
        RAISE.invalidArgumentFault(s"BoK metadata staging output escapes its root: $output")
      Option(targetpath.getParent).foreach(Files.createDirectories(_))
      Files.copy(sourcepath, targetpath, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def _copy_finalization_directory(source: Path, target: Path, relative: String): Unit = {
    val sourcepath = _finalization_input(source, relative)
    if (Files.exists(sourcepath, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isSymbolicLink(sourcepath) || !Files.isDirectory(sourcepath, LinkOption.NOFOLLOW_LINKS))
        RAISE.invalidArgumentFault(s"BoK generated metadata input must be a directory when present: $sourcepath")
      val stream = Files.walk(sourcepath)
      try {
        stream.iterator.asScala.toVector.sortBy(_.toString).foreach { path =>
          val targetpath = target.resolve(relative).resolve(sourcepath.relativize(path)).normalize()
          if (!targetpath.startsWith(target))
            RAISE.invalidArgumentFault(s"BoK metadata staging output escapes its root: $path")
          if (Files.isSymbolicLink(path))
            RAISE.invalidArgumentFault(s"BoK generated metadata must not contain symbolic links: $path")
          else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            Files.createDirectories(targetpath)
          else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            Option(targetpath.getParent).foreach(Files.createDirectories(_))
            Files.copy(path, targetpath, StandardCopyOption.REPLACE_EXISTING)
          } else
            RAISE.invalidArgumentFault(s"BoK generated metadata input must contain only files and directories: $path")
        }
      } finally {
        stream.close()
      }
    }
  }

  private def _finalization_input(root: Path, relative: String): Path = {
    val input = root.resolve(relative).normalize()
    if (!input.startsWith(root))
      RAISE.invalidArgumentFault(s"BoK generated metadata input escapes its root: $relative")
    _validate_finalization_parent(root, Option(input.getParent).getOrElse(root))
    input
  }

  private def _validate_staged_metadata(stage: Path): Unit = {
    val stream = Files.walk(stage)
    try {
      stream.iterator().asScala.foreach { path =>
        if (Files.isSymbolicLink(path))
          RAISE.invalidArgumentFault(s"BoK generated metadata must not contain symbolic links: $path")
        else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
            !_is_finalization_output(stage.relativize(path)))
          RAISE.invalidArgumentFault(s"BoK metadata staging output is not allowlisted: $path")
        else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          RAISE.invalidArgumentFault(s"BoK metadata staging output must contain only files and directories: $path")
      }
    } finally {
      stream.close()
    }
  }

  private def _commit_staged_metadata(stage: Path, target: Path, projectroot: Path): Unit = {
    val staged = _staged_metadata_files(stage).map { source =>
      source -> _finalization_target(target, stage.relativize(source))
    }
    val stagedtargets = staged.map(_._2).toSet
    val stale = _existing_finalization_outputs(target).filterNot(stagedtargets)
    val destinations = (staged.map(_._2) ++ stale).distinct.sortBy(_.toString)
    val backuproot = Files.createTempDirectory(projectroot, ".cozy-bok-finalize-backup-")
    try {
      val backups = destinations.map(_backup_finalization_file(_, target, backuproot))
      var createdparents = Vector.empty[Path]
      try {
        staged.foreach { case (source, destination) =>
          createdparents = createdparents ++ _create_finalization_parent(target, Option(destination.getParent).getOrElse(target))
          val temporary = Files.createTempFile(destination.getParent, ".cozy-bok-finalize-", ".tmp")
          try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING)
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
          } catch {
            case error: Throwable =>
              Files.deleteIfExists(temporary)
              throw error
          }
        }
        stale.foreach(Files.deleteIfExists)
      } catch {
        case error: Throwable =>
          _restore_finalization_backups(backups, target)
          _delete_empty_finalization_directories(createdparents)
          throw error
      }
    } finally {
      _delete_directory(backuproot)
    }
  }

  private def _backup_finalization_file(destination: Path, root: Path, backuproot: Path): FinalizationBackup = {
    _validate_finalization_parent(root, Option(destination.getParent).getOrElse(root))
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isSymbolicLink(destination) || !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))
        RAISE.invalidArgumentFault(s"BoK metadata output must be a regular file when present: $destination")
      val backup = backuproot.resolve(root.relativize(destination))
      Option(backup.getParent).foreach(Files.createDirectories(_))
      Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING)
      FinalizationBackup(destination, Some(backup))
    } else {
      FinalizationBackup(destination, None)
    }
  }

  private def _staged_metadata_files(stage: Path): Vector[Path] = {
    val stream = Files.walk(stage)
    try {
      stream.iterator().asScala.toVector.
        filter(Files.isRegularFile(_, LinkOption.NOFOLLOW_LINKS)).
        filter { path =>
          val relative = stage.relativize(path)
          if (!_is_finalization_output(relative))
            RAISE.invalidArgumentFault(s"BoK metadata staging output is not allowlisted: $path")
          true
        }.
        sortBy(path => stage.relativize(path).toString)
    } finally {
      stream.close()
    }
  }

  private def _existing_finalization_outputs(root: Path): Vector[Path] = {
    val files = _finalization_file_allowlist.flatMap { relative =>
      val path = _finalization_target(root, Paths.get(relative))
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          RAISE.invalidArgumentFault(s"BoK metadata output must be a regular file when present: $path")
        Vector(path)
      } else {
        Vector.empty
      }
    }
    val directories = _finalization_directory_allowlist.flatMap { relative =>
      val directory = _finalization_target_directory(root, relative)
      if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
        Vector.empty
      } else if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        RAISE.invalidArgumentFault(s"BoK metadata output directory must be a non-symbolic-link directory when present: $directory")
      } else {
        val stream = Files.walk(directory)
        try {
          stream.iterator.asScala.toVector.flatMap { path =>
            if (Files.isSymbolicLink(path))
              RAISE.invalidArgumentFault(s"BoK metadata output must not contain symbolic links: $path")
            else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
              Vector(path)
            else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
              Vector.empty
            else
              RAISE.invalidArgumentFault(s"BoK metadata output must contain only files and directories: $path")
          }
        } finally {
          stream.close()
        }
      }
    }
    (files ++ directories).distinct.sortBy(_.toString)
  }

  private def _finalization_target(root: Path, relative: Path): Path = {
    val normalized = relative.normalize()
    if (normalized.isAbsolute || !_is_finalization_output(normalized))
      RAISE.invalidArgumentFault(s"BoK metadata output is not allowlisted: $relative")
    val target = root.resolve(normalized).normalize()
    if (!target.startsWith(root))
      RAISE.invalidArgumentFault(s"BoK metadata output escapes website root: $relative")
    _validate_finalization_parent(root, Option(target.getParent).getOrElse(root))
    target
  }

  private def _finalization_target_directory(root: Path, relative: String): Path = {
    val target = root.resolve(relative).normalize()
    if (!target.startsWith(root))
      RAISE.invalidArgumentFault(s"BoK metadata output directory escapes website root: $relative")
    _validate_finalization_parent(root, Option(target.getParent).getOrElse(root))
    target
  }

  private def _is_finalization_output(relative: Path): Boolean = {
    val path = relative.toString.replace('\\', '/')
    _finalization_file_allowlist.contains(path) ||
      _finalization_directory_allowlist.exists(x => path.startsWith(x + "/"))
  }

  private def _validate_finalization_parent(
      root: Path,
      parent: Path,
      label: String = "BoK metadata output parent"
  ): Unit = {
    if (!parent.startsWith(root))
      RAISE.invalidArgumentFault(s"$label escapes its root: $parent")
    var current = root
    root.relativize(parent).iterator.asScala.foreach { segment =>
      val next = current.resolve(segment.toString)
      if (Files.exists(next, LinkOption.NOFOLLOW_LINKS) &&
          (Files.isSymbolicLink(next) || !Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)))
        RAISE.invalidArgumentFault(s"$label is unsafe: $next")
      current = next
    }
  }

  private def _create_finalization_parent(root: Path, parent: Path): Vector[Path] = {
    _validate_finalization_parent(root, parent)
    var current = root
    var created = Vector.empty[Path]
    root.relativize(parent).iterator.asScala.foreach { segment =>
      val next = current.resolve(segment.toString)
      if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
        Files.createDirectory(next)
        created :+= next
      }
      current = next
    }
    created
  }

  private def _delete_empty_finalization_directories(paths: Vector[Path]): Unit =
    paths.reverse.distinct.foreach { path =>
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        val stream = Files.newDirectoryStream(path)
        try {
          if (!stream.iterator.hasNext)
            Files.delete(path)
        } finally {
          stream.close()
        }
      }
    }
  private def _restore_finalization_backups(backups: Vector[FinalizationBackup], root: Path): Unit =
    backups.reverse.foreach { item =>
      item.backup match {
        case Some(saved) =>
          _create_finalization_parent(root, Option(item.target.getParent).getOrElse(root))
          Files.copy(saved, item.target, StandardCopyOption.REPLACE_EXISTING)
        case None =>
          Files.deleteIfExists(item.target)
      }
    }

  private[bok] def _sie_index(config: BuildConfig): CozyBokSieHandoff.Index =
    CozyBokSieHandoff.load(config.project, _load_config(config.project), _safe_resolved_project_packages(config))

  private def _sync_sie_metadata(config: BuildConfig, target: Path): Unit = {
    val index = _sie_index(config)
    if (!index.isEmpty) {
      _write_text(target.resolve("metadata/sie/integration.json"), index.toJson.spaces2 + "\n")
      val graphpath = target.resolve("metadata/rdf/graph.json")
      if (Files.isRegularFile(graphpath)) {
        val graph = parser.parse(Files.readString(graphpath, StandardCharsets.UTF_8)).fold(
          error => RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: ${error.message}"),
          identity
        )
        val merged = CozyBokSieHandoff.mergeGraph(graph, index).fold(
          message => RAISE.invalidArgumentFault(s"Invalid SIE RDF handoff: ${message}"),
          identity
        )
        _write_text(graphpath, merged.spaces2 + "\n")
      }
    }
  }

  private def _apply_rdf_extensions(config: BuildConfig, target: Path): Unit = {
    val entries = _load_config(config.project).list("bok.extensions.rdf")
    if (entries.nonEmpty) {
      val projectroot = _finalization_project_root(config.project)
      val root = _rdf_extension_root(projectroot)
      val declarations = entries.map(_load_rdf_extension(root, _)).sortBy(_.id)
      _require_unique_extension_identities(declarations)
      val graphpath = target.resolve("metadata/rdf/graph.json")
      if (!Files.isRegularFile(graphpath, LinkOption.NOFOLLOW_LINKS))
        _extension_override_forbidden("src/main/extensions/rdf", "a generated graph summary is required before extensions can be applied")
      val graph = parser.parse(Files.readString(graphpath, StandardCharsets.UTF_8)).fold(
        error => RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: ${error.message}"),
        identity
      )
      val graphobject = graph.asObject.getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: graph summary must be a JSON object.")
      )
      val generatednodes = graphobject("nodes").flatMap(_.asArray).getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: nodes must be an array.")
      )
      val generatededges = graphobject("edges").flatMap(_.asArray).getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: edges must be an array.")
      )
      val extensionnodes = declarations.flatMap(_.nodes).sortBy(_extension_node_id)
      val extensionedges = declarations.flatMap(_.edges).sortBy(_extension_edge_identity)
      _require_no_generated_extension_overrides(generatednodes, generatededges, extensionnodes, extensionedges)
      val merged = Json.fromJsonObject(
        graphobject.
          add("nodes", Json.fromValues(generatednodes ++ extensionnodes)).
          add("edges", Json.fromValues(generatededges ++ extensionedges))
      )
      _write_text(graphpath, merged.spaces2 + "\n")
    }
  }

  private def _rdf_extension_root(projectroot: Path): Path = {
    val root = projectroot.resolve("src/main/extensions/rdf").normalize()
    _validate_rdf_extension_ancestors(projectroot, root, "src/main/extensions/rdf")
    val canonicalprojectroot = projectroot.toRealPath()
    val canonicalroot = root.toRealPath()
    if (!canonicalroot.startsWith(canonicalprojectroot))
      _extension_path_invalid("src/main/extensions/rdf", "extension root resolves outside the project root")
    root
  }

  private def _load_rdf_extension(root: Path, entry: String): RdfExtensionDeclaration = {
    val logicalroot = "src/main/extensions/rdf"
    if (entry.isEmpty || entry.contains('\\') || entry.contains('\u0000') || entry.matches("^[A-Za-z]:/.*") || !entry.endsWith(".json") ||
        entry.split("/", -1).exists(x => x.isEmpty || x == "." || x == ".."))
      _extension_path_invalid(logicalroot, "declaration must be a non-empty relative .json path")
    val relative = try {
      Paths.get(entry)
    } catch {
      case NonFatal(_) => _extension_path_invalid(logicalroot, "declaration must be a non-empty relative .json path")
    }
    if (relative.isAbsolute)
      _extension_path_invalid(logicalroot, "declaration must be a non-empty relative .json path")
    val logicalpath = s"$logicalroot/$entry"
    val path = root.resolve(relative).normalize()
    if (!path.startsWith(root))
      _extension_path_invalid(logicalpath, "declaration escapes the admitted extension root")
    _validate_rdf_extension_ancestors(root, Option(path.getParent).getOrElse(root), logicalpath)
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _extension_path_invalid(logicalpath, "declaration must be an existing non-symbolic-link regular file")
    val canonicalroot = root.toRealPath()
    val canonicalpath = path.toRealPath()
    if (!canonicalpath.startsWith(canonicalroot))
      _extension_path_invalid(logicalpath, "declaration resolves outside the admitted extension root")
    val json = try {
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
        error => _extension_schema_invalid(logicalpath, error.message),
        identity
      )
    } catch {
      case NonFatal(error) => _extension_schema_invalid(logicalpath, error.getMessage)
    }
    val declaration = json.asObject.getOrElse(
      _extension_schema_invalid(logicalpath, "declaration must be a JSON object")
    )
    val schema = _required_extension_field(declaration, "schemaVersion", logicalpath)
    if (schema != "cozy.bok.rdf-extension.v1")
      _extension_schema_invalid(logicalpath, "schemaVersion must be cozy.bok.rdf-extension.v1")
    val id = _required_extension_field(declaration, "id", logicalpath)
    val kind = _required_extension_field(declaration, "kind", logicalpath)
    if (!Set("ontology", "schema", "supplemental-graph").contains(kind))
      _extension_schema_invalid(logicalpath, "kind must be ontology, schema, or supplemental-graph")
    val nodes = declaration("nodes").flatMap(_.asArray).getOrElse(
      _extension_schema_invalid(logicalpath, "nodes must be an array")
    )
    val edges = declaration("edges").flatMap(_.asArray).getOrElse(
      _extension_schema_invalid(logicalpath, "edges must be an array")
    )
    nodes.zipWithIndex.foreach { case (node, index) =>
      val nodeobject = node.asObject.getOrElse(
        _extension_schema_invalid(logicalpath, s"nodes[$index] must be an object")
      )
      Vector("id", "label", "node_type").foreach(_required_extension_field(nodeobject, _, s"$logicalpath.nodes[$index]"))
      if (nodeobject("componentRef").nonEmpty)
        _extension_schema_invalid(logicalpath, s"nodes[$index].componentRef is not allowed in an extension")
    }
    edges.zipWithIndex.foreach { case (edge, index) =>
      val edgeobject = edge.asObject.getOrElse(
        _extension_schema_invalid(logicalpath, s"edges[$index] must be an object")
      )
      Vector("source", "predicate", "target").foreach(_required_extension_field(edgeobject, _, s"$logicalpath.edges[$index]"))
    }
    RdfExtensionDeclaration(id, kind, nodes, edges, logicalpath)
  }

  private def _validate_rdf_extension_ancestors(root: Path, path: Path, logicalpath: String): Unit = {
    if (!path.startsWith(root))
      _extension_path_invalid(logicalpath, "declaration parent escapes its admitted root")
    var current = root
    root.relativize(path).iterator.asScala.foreach { segment =>
      current = current.resolve(segment.toString)
      if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
        _extension_path_invalid(logicalpath, "extension root and declaration parents must be existing non-symbolic-link directories")
    }
    if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      _extension_path_invalid(logicalpath, "extension root and declaration parents must be existing non-symbolic-link directories")
  }

  private def _require_unique_extension_identities(declarations: Vector[RdfExtensionDeclaration]): Unit = {
    declarations.groupBy(_.id).collect { case (id, items) if items.size > 1 => id }.toVector.sorted.headOption.foreach { id =>
      _extension_identity_collision("src/main/extensions/rdf", s"duplicate declaration id $id")
    }
    val nodes = declarations.flatMap(_.nodes)
    nodes.groupBy(_extension_node_id).collect { case (id, items) if items.size > 1 => id }.toVector.sorted.headOption.foreach { id =>
      _extension_identity_collision("src/main/extensions/rdf", s"duplicate extension node id $id")
    }
    val edges = declarations.flatMap(_.edges)
    edges.groupBy(_extension_edge_identity).collect { case (identity, items) if items.size > 1 => identity }.toVector.sorted.headOption.foreach { identity =>
      _extension_identity_collision("src/main/extensions/rdf", s"duplicate extension edge ${identity.productIterator.mkString("(", ",", ")")}")
    }
  }

  private def _require_no_generated_extension_overrides(
      generatednodes: Vector[Json],
      generatededges: Vector[Json],
      extensionnodes: Vector[Json],
      extensionedges: Vector[Json]
  ): Unit = {
    val generatednodeids = generatednodes.map(_generated_node_id).toSet
    extensionnodes.map(_extension_node_id).find(generatednodeids.contains).foreach { id =>
      _extension_override_forbidden("src/main/extensions/rdf", s"extension node $id collides with generated authority")
    }
    val generatededgeidentities = generatededges.map(_generated_edge_identity).toSet
    extensionedges.map(_extension_edge_identity).find(generatededgeidentities.contains).foreach { identity =>
      _extension_override_forbidden("src/main/extensions/rdf", s"extension edge ${identity.productIterator.mkString("(", ",", ")")} collides with generated authority")
    }
  }

  private def _extension_node_id(node: Json): String =
    _required_extension_field(node.asObject.getOrElse(
      _extension_schema_invalid("src/main/extensions/rdf", "node must be an object")
    ), "id", "src/main/extensions/rdf")

  private def _extension_edge_identity(edge: Json): (String, String, String) = {
    val edgeobject = edge.asObject.getOrElse(
      _extension_schema_invalid("src/main/extensions/rdf", "edge must be an object")
    )
    (
      _required_extension_field(edgeobject, "source", "src/main/extensions/rdf"),
      _required_extension_field(edgeobject, "predicate", "src/main/extensions/rdf"),
      _required_extension_field(edgeobject, "target", "src/main/extensions/rdf")
    )
  }

  private def _generated_node_id(node: Json): String =
    _required_graph_field(node.asObject.getOrElse(
      RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: node must be an object.")
    ), "id", "nodes")

  private def _generated_edge_identity(edge: Json): (String, String, String) = {
    val edgeobject = edge.asObject.getOrElse(
      RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: edge must be an object.")
    )
    (
      _required_graph_field(edgeobject, "source", "edges"),
      _required_graph_field(edgeobject, "predicate", "edges"),
      _required_graph_field(edgeobject, "target", "edges")
    )
  }

  private def _required_extension_field(
      jsonobject: io.circe.JsonObject,
      field: String,
      location: String
  ): String =
    jsonobject(field).flatMap(_.asString).map(_.trim).filter(_.nonEmpty).getOrElse(
      _extension_schema_invalid(location, s"$field must be a non-empty string")
    )

  private def _extension_path_invalid(logicalpath: String, message: String): Nothing =
    RAISE.invalidArgumentFault(s"bok.extension.path.invalid: $logicalpath: $message")

  private def _extension_schema_invalid(logicalpath: String, message: String): Nothing =
    RAISE.invalidArgumentFault(s"bok.extension.schema.invalid: $logicalpath: $message")

  private def _extension_identity_collision(logicalpath: String, message: String): Nothing =
    RAISE.invalidArgumentFault(s"bok.extension.identity.collision: $logicalpath: $message")

  private def _extension_override_forbidden(logicalpath: String, message: String): Nothing =
    RAISE.invalidArgumentFault(s"bok.extension.override.forbidden: $logicalpath: $message")

  private def _version_graph_summary(config: BuildConfig, target: Path): Unit = {
    val graphpath = target.resolve("metadata/rdf/graph.json")
    if (Files.isRegularFile(graphpath)) {
      val graph = parser.parse(Files.readString(graphpath, StandardCharsets.UTF_8)).fold(
        error => RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: ${error.message}"),
        identity
      )
      val graphobject = graph.asObject.getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: graph summary must be a JSON object.")
      )
      graphobject("schemaVersion").flatMap(_.asString).foreach { version =>
        if (version != "cozy.rdf-graph-summary.v1")
          RAISE.invalidArgumentFault(s"Unsupported BoK RDF graph summary schema: $version")
      }
      graphobject("kind").flatMap(_.asString).foreach { kind =>
        if (kind != "rdf-graph-summary")
          RAISE.invalidArgumentFault(s"Unsupported BoK RDF graph summary kind: $kind")
      }
      val nodes = graphobject("nodes").flatMap(_.asArray).getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: nodes must be an array.")
      )
      val edges = graphobject("edges").flatMap(_.asArray).getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: edges must be an array.")
      )
      val truncated = graphobject("truncated").flatMap(_.asBoolean).getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: truncated must be a boolean.")
      )
      val componentrefs = _validate_graph_nodes(nodes)
      _validate_graph_edges(edges)
      _validate_graph_component_refs(target, componentrefs)
      val sourceref = Json.obj(
        (Vector(
          "kind" -> Json.fromString("bok-site"),
          "value" -> Json.fromString(config.siteId)
        ) ++ config.siteUrl.map(x => "uri" -> Json.fromString(x))).toSeq: _*
      )
      val versioned = Json.fromJsonObject(
        graphobject.
          add("schemaVersion", Json.fromString("cozy.rdf-graph-summary.v1")).
          add("kind", Json.fromString("rdf-graph-summary")).
          add("sourceRef", sourceref).
          add("nodes", Json.fromValues(nodes)).
          add("edges", Json.fromValues(edges)).
          add("truncated", Json.fromBoolean(truncated))
      )
      _write_text(graphpath, versioned.spaces2 + "\n")
    }
  }

  private final case class GraphComponentRef(
    kind: String,
    name: String,
    organization: Option[String],
    version: Option[String],
    location: String
  )

  private final case class GraphComponentReferenceEntry(
    kind: String,
    name: String,
    organization: Option[String],
    versions: Vector[String],
    location: String
  )

  private def _validate_graph_nodes(nodes: Vector[Json]): Vector[GraphComponentRef] =
    nodes.zipWithIndex.flatMap { case (node, index) =>
      val nodeobject = node.asObject.getOrElse(
        RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: nodes[$index] must be an object.")
      )
      Vector("id", "label", "node_type").foreach { field =>
        _required_graph_field(nodeobject, field, s"nodes[$index]")
      }
      nodeobject("componentRef").map { componentref =>
        val location = s"nodes[$index].componentRef"
        val nodetype = nodeobject("node_type").flatMap(_.asString).map(_.trim).getOrElse("")
        if (nodetype != "component-reference")
          RAISE.invalidArgumentFault(
            s"Invalid BoK RDF graph metadata: $location is allowed only when node_type is component-reference."
          )
        val refobject = componentref.asObject.getOrElse(
          RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: $location must be a JSON object.")
        )
        val kind = _required_graph_field(refobject, "kind", location)
        if (!Set("car", "sar").contains(kind))
          RAISE.invalidArgumentFault(
            s"Invalid BoK RDF graph metadata: $location.kind must be car or sar."
          )
        GraphComponentRef(
          kind,
          _required_graph_field(refobject, "name", location),
          _optional_graph_field(refobject, "organization", location),
          _optional_graph_field(refobject, "version", location),
          location
        )
      }
    }

  private def _validate_graph_edges(edges: Vector[Json]): Unit =
    edges.zipWithIndex.foreach { case (edge, index) =>
      val edgeobject = edge.asObject.getOrElse(
        RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: edges[$index] must be an object.")
      )
      Vector("source", "predicate", "target").foreach { field =>
        _required_graph_field(edgeobject, field, s"edges[$index]")
      }
    }

  private def _required_graph_field(graphobject: io.circe.JsonObject, field: String, location: String): String =
    graphobject(field).flatMap(_.asString).map(_.trim).filter(_.nonEmpty).getOrElse(
      RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: $location.$field must be a non-empty string.")
    )

  private def _optional_graph_field(graphobject: io.circe.JsonObject, field: String, location: String): Option[String] =
    graphobject(field).map { value =>
      value.asString.map(_.trim).filter(_.nonEmpty).getOrElse(
        RAISE.invalidArgumentFault(
          s"Invalid BoK RDF graph metadata: $location.$field must be a non-empty string when present."
        )
      )
    }

  private def _validate_graph_component_refs(target: Path, refs: Vector[GraphComponentRef]): Unit =
    refs.groupBy(_.kind).foreach { case (kind, kindrefs) =>
      val entries = _load_graph_component_reference_index(target, kind)
      kindrefs.foreach { ref =>
        val matches = entries.filter { entry =>
          entry.kind == ref.kind &&
            entry.name == ref.name &&
            ref.organization.forall(x => entry.organization.contains(x)) &&
            ref.version.forall(x => entry.versions.contains(x))
        }
        if (matches.isEmpty)
          RAISE.invalidArgumentFault(
            s"Invalid BoK RDF graph metadata: ${ref.location} does not match any $kind component-reference index entry."
          )
        else if (matches.size > 1)
          RAISE.invalidArgumentFault(
            s"Invalid BoK RDF graph metadata: ${ref.location} matches multiple $kind component-reference index entries."
          )
      }
    }

  private def _load_graph_component_reference_index(target: Path, kind: String): Vector[GraphComponentReferenceEntry] = {
    val path = target.resolve("metadata/cncf/component-references").resolve(s"$kind.json")
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(
        s"Invalid BoK RDF graph metadata: component-reference index metadata/cncf/component-references/$kind.json is missing."
      )
    val json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      error => RAISE.invalidArgumentFault(
        s"Invalid BoK component-reference index metadata/cncf/component-references/$kind.json: ${error.message}"
      ),
      identity
    )
    val indexobject = json.asObject.getOrElse(
      RAISE.invalidArgumentFault(
        s"Invalid BoK component-reference index metadata/cncf/component-references/$kind.json: index must be a JSON object."
      )
    )
    indexobject("schemaVersion").flatMap(_.asString).foreach { version =>
      if (version != "cncf.component-reference-index.v1")
        RAISE.invalidArgumentFault(
          s"Invalid BoK component-reference index metadata/cncf/component-references/$kind.json: unsupported schemaVersion $version."
        )
    }
    indexobject("kind").flatMap(_.asString).foreach { indexkind =>
      if (indexkind != kind)
        RAISE.invalidArgumentFault(
          s"Invalid BoK component-reference index metadata/cncf/component-references/$kind.json: kind must be $kind."
        )
    }
    val entries = indexobject("entries").flatMap(_.asArray).getOrElse(
      RAISE.invalidArgumentFault(
        s"Invalid BoK component-reference index metadata/cncf/component-references/$kind.json: entries must be an array."
      )
    )
    entries.zipWithIndex.map { case (entry, index) =>
      val location = s"metadata/cncf/component-references/$kind.json.entries[$index]"
      val entryobject = entry.asObject.getOrElse(
        RAISE.invalidArgumentFault(s"Invalid BoK component-reference index: $location must be a JSON object.")
      )
      GraphComponentReferenceEntry(
        _required_graph_field(entryobject, "kind", location),
        _required_graph_field(entryobject, "name", location),
        _optional_graph_field(entryobject, "organization", location),
        _component_reference_entry_versions(entryobject, location),
        location
      )
    }
  }

  private def _component_reference_entry_versions(
      entryobject: io.circe.JsonObject,
      location: String
  ): Vector[String] =
    entryobject("versions").flatMap(_.asArray).getOrElse(Vector.empty).zipWithIndex.map { case (version, index) =>
      val versionlocation = s"$location.versions[$index]"
      val versionobject = version.asObject.getOrElse(
        RAISE.invalidArgumentFault(s"Invalid BoK component-reference index: $versionlocation must be a JSON object.")
      )
      _required_graph_field(versionobject, "version", versionlocation)
    }

  private def _write_knowledge_source_manifest(config: BuildConfig, sourcepath: Path, target: Path): Unit = {
    val terms = target.resolve("metadata/glossary/terms.json")
    if (_source_declares_glossary_terms(sourcepath) && !Files.isRegularFile(terms))
      RAISE.invalidArgumentFault(
        "SmartDox glossary metadata was not generated even though BoK source declares glossary terms. " +
          "Update the dox/SmartDox runtime used by cozy bok build; Cozy does not reconstruct the missing terms.json handoff."
      )
    val resources = Vector(
      KnowledgeSourceResource("glossary-terms", "metadata/glossary/terms.json", "application/json"),
      KnowledgeSourceResource("rdf-jsonld", "rdf/site.jsonld", "application/ld+json"),
      KnowledgeSourceResource("rdf-turtle", "rdf/site.ttl", "text/turtle"),
      KnowledgeSourceResource("rdf-graph-summary", "metadata/rdf/graph.json", "application/json"),
      KnowledgeSourceResource("component-reference-index", "metadata/cncf/component-references/car.json", "application/json"),
      KnowledgeSourceResource("component-reference-index", "metadata/cncf/component-references/sar.json", "application/json")
    ).filter(x => Files.isRegularFile(target.resolve(x.href))) ++
      _knowledge_source_component_resources(target)
    val sourceref = Json.obj(
      (Vector(
        "kind" -> Json.fromString("bok-site"),
        "value" -> Json.fromString(config.siteId)
      ) ++ config.siteUrl.map(x => "uri" -> Json.fromString(x))).toSeq: _*
    )
    val manifest = Json.obj(
      "schemaVersion" -> Json.fromString("cncf.knowledge-source.v1"),
      "kind" -> Json.fromString("bok-site"),
      "id" -> Json.fromString(config.siteId),
      "label" -> Json.fromString(config.siteTitle),
      "sourceRef" -> sourceref,
      "resources" -> Json.fromValues(resources.map(_.toJson))
    )
    _write_text(
      target.resolve("metadata/cncf/knowledge-source.json"),
      manifest.spaces2 + "\n"
    )
  }

  private def _knowledge_source_component_resources(target: Path): Vector[KnowledgeSourceResource] = {
    val projectroot = target.resolve("metadata/projects")
    if (!Files.isDirectory(projectroot))
      Vector.empty
    else {
      val stream = Files.walk(projectroot)
      try {
        stream.iterator.asScala.toVector.
          filter(path =>
            Files.isRegularFile(path) &&
              path.getFileName.toString == "metadata.json" &&
              Option(path.getParent).flatMap(x => Option(x.getParent)).contains(projectroot)
          ).
          flatMap(_knowledge_source_component_identity).
          sorted.
          flatMap { name =>
            Vector(
              KnowledgeSourceResource("component-catalog-project", s"metadata/catalog/projects/$name.json", "application/json"),
              KnowledgeSourceResource("component-project-metadata", s"metadata/projects/$name/metadata.json", "application/json"),
              KnowledgeSourceResource("component-repository-artifact", s"metadata/artifacts/repository/$name.json", "application/json"),
              KnowledgeSourceResource("component-release-history", s"metadata/releases/$name.json", "application/json")
            ).filter(x => Files.isRegularFile(target.resolve(x.href)))
          }
      } finally {
        stream.close()
      }
    }
  }

  private def _knowledge_source_component_identity(path: Path): Option[String] = {
    val json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      error => RAISE.invalidArgumentFault(s"Invalid Cozy component project metadata JSON: $path: ${error.message}"),
      identity
    )
    val cursor = json.hcursor
    val schema = cursor.get[String]("schema").toOption
    val metadatatype = cursor.get[String]("type").toOption
    val project = cursor.downField("project")
    val name = project.get[String]("name").toOption.map(_.trim).filter(_.nonEmpty)
    val kind = project.get[String]("kind").toOption.map(_.trim.toLowerCase(Locale.ROOT))
    val canonicalname = Option(path.getParent).flatMap(x => Option(x.getFileName)).map(_.toString)
    (schema, metadatatype, name, kind) match {
      case (Some("cozy.publish-project.v1"), Some("project-metadata"), Some(componentname), Some(componentkind))
          if canonicalname.contains(componentname) && (componentkind == "car" || componentkind == "sar") =>
        Some(componentname)
      case _ =>
        None
    }
  }

  private def _source_declares_glossary_terms(sourcepath: Path): Boolean = {
    val root = sourcepath.resolve("glossary")
    if (!Files.isDirectory(root))
      false
    else {
      val stream = Files.walk(root)
      try {
        stream.iterator.asScala.exists { path =>
          Files.isRegularFile(path) &&
          _is_source_document(path) &&
          !_is_index_source_document(path)
        }
      } finally {
        stream.close()
      }
    }
  }

  private[bok] def _copy_if_exists(source: Path, target: Path): Unit =
    if (Files.isRegularFile(source)) {
      Option(target.getParent).foreach(Files.createDirectories(_))
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

}
