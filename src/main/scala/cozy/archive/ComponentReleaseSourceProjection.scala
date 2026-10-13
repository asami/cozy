package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}

import org.goldenport.RAISE
import play.api.libs.json.{JsArray, JsObject, Json}

import scala.collection.JavaConverters._
import scala.util.Try

/*
 * @since   Aug. 25, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * The deterministic release-source staging boundary.  This is deliberately a
 * different artifact from ComponentSourceArchiveProjection: the latter is an
 * inventory, while this projection may contain the admitted bytes.
 */
private[cozy] object ComponentReleaseSourceProjection {
  val SCHEMA = "cozy.component-release-source.v1"
  val MANIFEST_FILE_NAME = "release-source-manifest.json"
  val SOURCE_IDENTITY = "SourceCode"

  final case class Policy(mode: String, license: String)
  final case class BuildEvidence(
    compilescalacoptions: Vector[String],
    testscalacoptions: Vector[String],
    dependencies: Vector[String],
    generators: Vector[String]
  )
  final case class Entry(path: String)
  final case class Manifest(
    policy: Policy,
    buildevidence: BuildEvidence,
    entries: Vector[Entry]
  )
  final case class Verified(
    directory: Path,
    manifest: Path,
    manifestText: String,
    policy: Policy,
    entries: Vector[Entry]
  ) {
    def archiveEntries: Vector[(Path, String)] =
      entries.map(entry => directory.resolve(entry.path) -> s"source/${entry.path}")
  }

  private final case class Source(path: String, source: Path)

  def policy(mode: String, license: String): Policy = {
    val normalizedmode = Option(mode).map(_.trim).getOrElse("")
    val normalizedlicense = Option(license).map(_.trim).getOrElse("")
    if (normalizedmode != "public" && normalizedmode != "restricted")
      _invalid(s"release-source policy mode must be exactly public or restricted: $mode")
    if (normalizedlicense.isEmpty || _unsafe_metadata(normalizedlicense))
      _invalid("release-source policy requires a nonempty safe license")
    Policy(normalizedmode, normalizedlicense)
  }

  def stage(
    projectRoot: Path,
    output: Path,
    releasePolicy: Policy,
    managedMainSources: Vector[Path],
    managedMainRoots: Vector[Path],
    managedTestSources: Vector[Path],
    managedTestRoots: Vector[Path],
    buildEvidence: BuildEvidence
  ): Path = {
    val root = _normalized_root(projectRoot)
    val destination = _safe_output(root, output)
    val normalizedevidence = _normalize_evidence(buildEvidence)
    val selected = _selected_sources(
      root,
      managedMainSources,
      managedMainRoots,
      managedTestSources,
      managedTestRoots
    )
    val admittedpolicy = policy(releasePolicy.mode, releasePolicy.license)
    selected.foreach { source =>
      if (source.source.toAbsolutePath.normalize().startsWith(destination))
        _invalid(s"Release-source source overlaps staging output: ${source.source}")
    }
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
      _require_directory(destination, "Release-source staging directory")
    _delete_tree(destination)
    Files.createDirectories(destination)
    if (admittedpolicy.mode == "public") {
      selected.foreach { source =>
        val target = destination.resolve(source.path)
        Option(target.getParent).foreach(Files.createDirectories(_))
        Files.copy(source.source, target, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    val entries = if (admittedpolicy.mode == "public") selected.map(source => Entry(source.path)) else Vector.empty
    val manifest = Manifest(admittedpolicy, normalizedevidence, entries)
    Files.write(destination.resolve(MANIFEST_FILE_NAME), render(manifest).getBytes(StandardCharsets.UTF_8))
    destination
  }

  def verify(
    stagingDirectory: Path,
    projectRoot: Path,
    releasePolicy: Policy
  ): Verified = {
    val verified = verifyForPackaging(stagingDirectory, projectRoot)
    if (verified.policy != policy(releasePolicy.mode, releasePolicy.license))
      _invalid(s"Release-source policy differs from current project policy: ${verified.manifest}")
    verified
  }

  /** Admit the supplied distribution without consulting current producer inputs. */
  def verifyForPackaging(stagingDirectory: Path, projectRoot: Path): Verified = {
    val root = _normalized_root(projectRoot)
    val directory = _require_directory(_safe_output(root, stagingDirectory), "Release-source staging directory")
    val manifestpath = directory.resolve(MANIFEST_FILE_NAME)
    _require_regular_file(manifestpath, "Release-source manifest")
    val manifest = _parse_manifest(manifestpath)
    _require_selected_entries(directory, manifest.entries)
    Verified(directory, manifestpath, render(manifest), manifest.policy, manifest.entries)
  }

  def render(manifest: Manifest): String = {
    val evidence =
      "{\"compileScalacOptions\":" + _json_array(manifest.buildevidence.compilescalacoptions) +
        ",\"testScalacOptions\":" + _json_array(manifest.buildevidence.testscalacoptions) +
        ",\"dependencies\":" + _json_array(manifest.buildevidence.dependencies) +
        ",\"generators\":" + _json_array(manifest.buildevidence.generators) + "}"
    val entries = manifest.entries.map(entry =>
      "{\"path\":" + _json(entry.path) + "}"
    ).mkString("[", ",", "]")
    "{\"schema\":" + _json(SCHEMA) +
      ",\"policy\":{\"mode\":" + _json(manifest.policy.mode) + ",\"license\":" + _json(manifest.policy.license) + "}," +
      "\"sourceIdentity\":" + _json(SOURCE_IDENTITY) +
      ",\"buildEvidence\":" + evidence + ",\"entries\":" + entries + "}"
  }

  private def _selected_sources(
    root: Path,
    managedmainsources: Vector[Path],
    managedmainroots: Vector[Path],
    managedtestsources: Vector[Path],
    managedtestroots: Vector[Path]
  ): Vector[Source] = {
    val authored = ComponentSourceArchiveProjection.project(root).entries.map(entry => Source(entry.path, root.resolve(entry.path)))
    val generatedmain = _generated_sources(root, managedmainsources, managedmainroots, "generated-source/main")
    val generatedtest = _generated_sources(root, managedtestsources, managedtestroots, "generated-source/test")
    val all = (authored ++ generatedmain ++ generatedtest).sortBy(_.path)
    val duplicates = all.groupBy(_.path).collect { case (path, values) if values.size > 1 => path }.toVector.sorted
    if (duplicates.nonEmpty)
      _invalid(s"Release-source normalized entries are duplicated: ${duplicates.mkString(", ")}")
    all
  }

  private def _generated_sources(root: Path, sources: Vector[Path], roots: Vector[Path], prefix: String): Vector[Source] = {
    val normalizedroots = roots.map(_.toAbsolutePath.normalize()).distinct
    val rootreal = root.toRealPath()
    normalizedroots.foreach { rootpath =>
      if (Files.isSymbolicLink(rootpath) || !Files.isDirectory(rootpath, LinkOption.NOFOLLOW_LINKS))
        _invalid(s"Release-source managed-source root is missing, unsafe, or not a directory: $rootpath")
      if (!rootpath.toRealPath().startsWith(rootreal))
        _invalid(s"Release-source managed-source root escapes the project root: $rootpath")
    }
    sources.flatMap { source0 =>
      val source = source0.toAbsolutePath.normalize()
      if (Files.isSymbolicLink(source))
        _invalid(s"Release-source managed source must not be a symbolic link: $source")
      if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
        _invalid(s"Release-source managed source is missing or not regular: $source")
      if (!source.getFileName.toString.endsWith(".scala"))
        _invalid(s"Release-source managed source is not a Scala source: $source")
      else {
        val matchingroots = normalizedroots.filter(rootpath =>
          !Files.isSymbolicLink(rootpath) && Files.isDirectory(rootpath, LinkOption.NOFOLLOW_LINKS) && source.startsWith(rootpath)
        )
        if (matchingroots.isEmpty)
          _invalid(s"Release-source managed source escapes its managed-source root: $source")
        if (matchingroots.size > 1)
          _invalid(s"Release-source managed source aliases multiple managed-source roots: $source")
        val selected = matchingroots.sortBy(_.toString).head
        val realroot = selected.toRealPath()
        val realsource = source.toRealPath()
        if (!realsource.startsWith(rootreal))
          _invalid(s"Release-source managed source escapes the project root: $source")
        if (!realsource.startsWith(realroot))
          _invalid(s"Release-source managed source resolves outside its managed-source root: $source")
        val relative = realroot.relativize(realsource).toString.replace('\\', '/')
        if (!_safe_path(relative))
          _invalid(s"Release-source managed source has an unsafe normalized path: $relative")
        Some(Source(s"$prefix/$relative", realsource))
      }
    }.sortBy(_.path).toVector
  }

  private def _parse_manifest(path: Path): Manifest = {
    val json = Try(Json.parse(Files.readString(path, StandardCharsets.UTF_8))).getOrElse(
      _invalid(s"Release-source manifest is invalid JSON: $path")
    )
    val schema = (json \ "schema").asOpt[String].getOrElse(_invalid(s"Release-source manifest is missing schema: $path"))
    if (schema != SCHEMA)
      _invalid(s"Release-source manifest schema is unsupported: $schema")
    val policyjson = (json \ "policy").asOpt[JsObject].getOrElse(_invalid(s"Release-source manifest is missing policy: $path"))
    val policy = ComponentReleaseSourceProjection.policy(
      (policyjson \ "mode").asOpt[String].getOrElse(""),
      (policyjson \ "license").asOpt[String].getOrElse("")
    )
    if ((json \ "sourceIdentity").asOpt[String] != Some(SOURCE_IDENTITY))
      _invalid(s"Release-source manifest has unsupported source identity: $path")
    val evidencejson = (json \ "buildEvidence").asOpt[JsObject].getOrElse(_invalid(s"Release-source manifest is missing build evidence: $path"))
    val evidence = BuildEvidence(
      _json_strings(evidencejson, "compileScalacOptions", path),
      _json_strings(evidencejson, "testScalacOptions", path),
      _json_strings(evidencejson, "dependencies", path),
      _json_strings(evidencejson, "generators", path)
    )
    val entries = (json \ "entries").asOpt[JsArray].getOrElse(_invalid(s"Release-source manifest is missing entries: $path")).value.toVector.map { value =>
      val entry = value.asOpt[JsObject].getOrElse(_invalid(s"Release-source manifest contains a non-object entry: $path"))
      Entry(
        (entry \ "path").asOpt[String].getOrElse(_invalid(s"Release-source manifest entry is missing path: $path"))
      )
    }
    _normalize_evidence(evidence) // Validate required metadata while preserving the supplied values.
    val manifest = Manifest(policy, evidence, entries)
    _require_manifest_entries(manifest)
    manifest
  }

  private def _require_manifest_entries(manifest: Manifest): Unit = {
    val entries = manifest.entries
    if (entries.map(_.path) != entries.map(_.path).sorted || entries.map(_.path).distinct.size != entries.size)
      _invalid("Release-source manifest entries are not uniquely sorted")
    entries.foreach { entry =>
      if (!_safe_path(entry.path) || entry.path == MANIFEST_FILE_NAME)
        _invalid(s"Release-source manifest contains an unsafe entry: ${entry.path}")
    }
    if (manifest.policy.mode == "restricted" && entries.nonEmpty)
      _invalid("Restricted release-source manifest must not declare source entries")
  }

  private def _require_selected_entries(directory: Path, declared: Vector[Entry]): Unit = {
    declared.foreach { entry =>
      val selected = entry.path.split('/').foldLeft(directory) { (parent, segment) =>
        val path = parent.resolve(segment)
        if (Files.isSymbolicLink(path))
          _invalid(s"Release-source staging must not contain a symbolic link: $path")
        path
      }
      _require_regular_file(selected, "Release-source staged entry")
    }
  }

  private def _json_strings(json: JsObject, key: String, path: Path): Vector[String] =
    (json \ key).asOpt[JsArray].getOrElse(_invalid(s"Release-source build evidence is missing $key: $path")).value.toVector.map {
      case value: play.api.libs.json.JsString => value.value
      case _ => _invalid(s"Release-source build evidence $key must contain strings: $path")
    }

  private def _normalize_evidence(evidence: BuildEvidence): BuildEvidence = {
    val normalized = BuildEvidence(
      _normalize_values(evidence.compilescalacoptions, "compile scalacOptions"),
      _normalize_values(evidence.testscalacoptions, "test scalacOptions"),
      _normalize_values(evidence.dependencies, "dependency"),
      _normalize_values(evidence.generators, "generator")
    )
    normalized
  }

  private def _normalize_values(values: Vector[String], label: String): Vector[String] = {
    values.map { value =>
      val raw = Option(value).getOrElse("")
      val normalized = raw.trim
      if (normalized.isEmpty || _unsafe_metadata(raw) || _unsafe_metadata(normalized))
        _invalid(s"Release-source $label evidence is empty or contains a local path")
      normalized
    }
  }

  private def _safe_output(root: Path, output: Path): Path = {
    if (output == null) _invalid("Release-source staging output is required")
    val destination = output.toAbsolutePath.normalize()
    val expected = root.resolve("target/cozy/release-source").toAbsolutePath.normalize()
    if (destination != expected)
      _invalid(s"Release-source staging must reside at $expected")
    _existing_ancestor(destination).foreach { ancestor =>
      val real = ancestor.toRealPath()
      if (!real.startsWith(root.toRealPath()))
        _invalid(s"Release-source staging resolves outside the project root: $destination")
    }
    destination
  }

  private def _normalized_root(projectroot: Path): Path = {
    if (projectroot == null) _invalid("Release-source project root is required")
    val root = projectroot.toAbsolutePath.normalize()
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
      _invalid(s"Release-source project root is not a directory: $root")
    root
  }

  private def _require_directory(path: Path, label: String): Path = {
    val normalized = path.toAbsolutePath.normalize()
    if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"$label is missing, unsafe, or not a directory: $normalized")
    normalized
  }

  private def _require_regular_file(path: Path, label: String): Unit =
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"$label is missing, unsafe, or not a regular file: $path")

  private def _existing_ancestor(path: Path): Option[Path] = {
    @annotation.tailrec
    def _go_(candidate: Path): Option[Path] =
      if (candidate == null) None
      else if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) Some(candidate)
      else _go_(candidate.getParent)
    _go_(path)
  }

  private def _safe_path(path: String): Boolean = {
    val normalized = path.replace('\\', '/')
    normalized == path && path.nonEmpty && !path.startsWith("/") &&
      path.split("/", -1).forall(segment => segment.nonEmpty && segment != "." && segment != "..")
  }

  private def _unsafe_metadata(value: String): Boolean = {
    val normalized = value.toLowerCase(java.util.Locale.ROOT)
    value.exists(ch => ch == '\n' || ch == '\r' || ch == '\u0000') ||
      value.contains('\\') ||
      value.contains('/') ||
      value.startsWith("~") ||
      value.matches("^[a-zA-Z]:.*") ||
      normalized.matches("^[a-z][a-z0-9+.-]*://.*") ||
      normalized.matches("^(localhost|127(?:\\.[0-9]{1,3}){3}|0\\.0\\.0\\.0|\\[::1\\])(?::[0-9]+)?$") ||
      normalized.matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]+)?$") ||
      normalized.matches("^\\[[0-9a-f:]+\\](?::[0-9]+)?$") ||
      (normalized.matches("^[0-9a-f:]+$") && normalized.count(_ == ':') >= 2) ||
      normalized.matches("^[a-z0-9.-]+\\.[a-z]{2,}(?::[0-9]+)?$") ||
      normalized.matches(".*(^|[-_.:])(target|cache|caches|tmp|temp|temporary|download|downloads|config|configuration)([-_.:/]|$).*") ||
      normalized.matches(".*(^|[^a-z])(password|passwd|secret|credential|credentials|token|api[-_]?key|access[-_]?key|private[-_]?key|authorization|bearer|cookie|session)([^a-z]|$).*") ||
      normalized.matches(".*\\b(?:19|20)[0-9]{2}[-/.][0-9]{1,2}[-/.][0-9]{1,2}(?:[t ][0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:z|[+-][0-9]{2}:?[0-9]{2})?)?\\b.*") ||
      normalized.matches("^[0-9a-f]{64}$") ||
      normalized.matches("^[a-z0-9+/]{16,}={1,2}$") ||
      normalized.matches(".*\\.(?:scala|class|jar|zip|json|yaml|yml|conf|properties)$")
  }

  private def _json_array(values: Vector[String]): String = values.map(_json).mkString("[", ",", "]")

  private def _json(value: String): String = {
    val builder = new StringBuilder("\"")
    value.foreach {
      case '\\' => builder.append("\\\\")
      case '"' => builder.append("\\\"")
      case '\b' => builder.append("\\b")
      case '\f' => builder.append("\\f")
      case '\n' => builder.append("\\n")
      case '\r' => builder.append("\\r")
      case '\t' => builder.append("\\t")
      case c if c < ' ' => builder.append(f"\\u${c.toInt}%04x")
      case c => builder.append(c)
    }
    builder.append('"').result()
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
