package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest

import org.goldenport.RAISE
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import scala.collection.JavaConverters._
import scala.util.Try

/*
 * @since   Aug. 25, 2026
 * @version Aug. 25, 2026
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
  final case class Entry(path: String, sha256: String)
  final case class Manifest(
    policy: Policy,
    sourcedigest: String,
    contentdigest: String,
    buildevidencedigest: String,
    buildevidence: BuildEvidence,
    entries: Vector[Entry]
  )
  final case class Verified(
    directory: Path,
    manifest: Path,
    policy: Policy,
    entries: Vector[Entry]
  ) {
    def archiveEntries: Vector[(Path, String)] =
      entries.map(entry => directory.resolve(entry.path) -> s"source/${entry.path}") :+
        (manifest -> s"source/$MANIFEST_FILE_NAME")
  }

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
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Release-source staging already exists and must be verified: $destination")
    val normalizedevidence = _normalize_evidence(buildEvidence)
    val allentries = _current_entries(
      root,
      managedMainSources,
      managedMainRoots,
      managedTestSources,
      managedTestRoots
    )
    val entries = if (releasePolicy.mode == "public") allentries else Vector.empty
    val manifest = _manifest(releasePolicy, allentries, entries, normalizedevidence)
    Files.createDirectories(destination)
    try {
      if (releasePolicy.mode == "public") {
        allentries.foreach { entry =>
          val source = _entry_source(root, entry.path, managedMainSources, managedMainRoots, managedTestSources, managedTestRoots)
            .getOrElse(root.resolve(entry.path))
          val target = destination.resolve(entry.path)
          Option(target.getParent).foreach(Files.createDirectories(_))
          Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
      }
      Files.write(
        destination.resolve(MANIFEST_FILE_NAME),
        render(manifest).getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE
      )
      verify(
        destination,
        root,
        releasePolicy,
        managedMainSources,
        managedMainRoots,
        managedTestSources,
        managedTestRoots,
        normalizedevidence
      )
      destination
    } catch {
      case error: Throwable =>
        // A failed stage must never be accidentally consumed as a partial stage.
        _delete_tree(destination)
        throw error
    }
  }

  def verify(
    stagingDirectory: Path,
    projectRoot: Path,
    releasePolicy: Policy,
    managedMainSources: Vector[Path],
    managedMainRoots: Vector[Path],
    managedTestSources: Vector[Path],
    managedTestRoots: Vector[Path],
    buildEvidence: BuildEvidence
  ): Verified = {
    val root = _normalized_root(projectRoot)
    val directory = _require_directory(_safe_output(root, stagingDirectory), "Release-source staging directory")
    val manifestPath = directory.resolve(MANIFEST_FILE_NAME)
    _require_regular_file(manifestPath, "Release-source manifest")
    val manifest = _parse_manifest(manifestPath)
    if (manifest.policy != releasePolicy)
      _invalid(s"Release-source policy differs from current project policy: $manifestPath")
    val normalizedevidence = _normalize_evidence(buildEvidence)
    val currentall = _current_entries(root, managedMainSources, managedMainRoots, managedTestSources, managedTestRoots)
    val expectedentries = if (releasePolicy.mode == "public") currentall else Vector.empty
    val expectedmanifest = _manifest(releasePolicy, currentall, expectedentries, normalizedevidence)
    if (manifest.sourcedigest != expectedmanifest.sourcedigest)
      _invalid(s"Release-source current-source digest differs from the manifest: $manifestPath")
    if (manifest.buildevidencedigest != expectedmanifest.buildevidencedigest || manifest.buildevidence != expectedmanifest.buildevidence)
      _invalid(s"Release-source build evidence differs from the current build: $manifestPath")
    if (manifest.entries != expectedentries)
      _invalid(s"Release-source manifest entries differ from current admitted sources: $manifestPath")
    if (render(manifest) != Files.readString(manifestPath, StandardCharsets.UTF_8))
      _invalid(s"Release-source manifest is not canonical: $manifestPath")
    _require_actual_entries(directory, manifest.entries)
    if (releasePolicy.mode == "public") {
      manifest.entries.foreach { entry =>
        val staged = directory.resolve(entry.path)
        if (_sha256(Files.readAllBytes(staged)) != entry.sha256)
          _invalid(s"Release-source staged entry digest differs from the manifest: ${entry.path}")
      }
    } else if (manifest.entries.nonEmpty) {
      _invalid(s"Restricted release-source staging must not contain source entries: $manifestPath")
    }
    Verified(directory, manifestPath, manifest.policy, manifest.entries)
  }

  /** Verifier used by Cozy's CAR consumer against the complete current bridge snapshot. */
  def verifyForPackaging(
    stagingDirectory: Path,
    projectRoot: Path,
    managedMainSources: Vector[Path],
    managedMainRoots: Vector[Path],
    managedTestSources: Vector[Path],
    managedTestRoots: Vector[Path],
    buildEvidence: BuildEvidence
  ): Verified = {
    val root = _normalized_root(projectRoot)
    val directory = _require_directory(_safe_output(root, stagingDirectory), "Release-source staging directory")
    val manifestPath = directory.resolve(MANIFEST_FILE_NAME)
    _require_regular_file(manifestPath, "Release-source manifest")
    val policy = _parse_manifest(manifestPath).policy
    verify(
      directory,
      root,
      policy,
      managedMainSources,
      managedMainRoots,
      managedTestSources,
      managedTestRoots,
      buildEvidence
    )
  }

  def render(manifest: Manifest): String = {
    val evidence =
      "{\"compileScalacOptions\":" + _json_array(manifest.buildevidence.compilescalacoptions) +
        ",\"testScalacOptions\":" + _json_array(manifest.buildevidence.testscalacoptions) +
        ",\"dependencies\":" + _json_array(manifest.buildevidence.dependencies) +
        ",\"generators\":" + _json_array(manifest.buildevidence.generators) + "}"
    val entries = manifest.entries.map(entry =>
      "{\"path\":" + _json(entry.path) + ",\"sha256\":" + _json(entry.sha256) + "}"
    ).mkString("[", ",", "]")
    "{\"schema\":" + _json(SCHEMA) +
      ",\"policy\":{\"mode\":" + _json(manifest.policy.mode) + ",\"license\":" + _json(manifest.policy.license) + "}," +
      "\"sourceIdentity\":" + _json(SOURCE_IDENTITY) +
      ",\"sourceDigest\":" + _json(manifest.sourcedigest) +
      ",\"contentDigest\":" + _json(manifest.contentdigest) +
      ",\"buildEvidenceDigest\":" + _json(manifest.buildevidencedigest) +
      ",\"buildEvidence\":" + evidence + ",\"entries\":" + entries + "}"
  }

  private def _manifest(
    releasepolicy: Policy,
    allentries: Vector[Entry],
    payloadentries: Vector[Entry],
    evidence: BuildEvidence
  ): Manifest = {
    val sourcedigest = _inventory_digest(allentries)
    val contentdigest = _inventory_digest(payloadentries)
    val buildevidencedigest = _build_evidence_digest(evidence)
    Manifest(releasepolicy, sourcedigest, contentdigest, buildevidencedigest, evidence, payloadentries)
  }

  private def _current_entries(
    root: Path,
    managedmainsources: Vector[Path],
    managedmainroots: Vector[Path],
    managedtestsources: Vector[Path],
    managedtestroots: Vector[Path]
  ): Vector[Entry] = {
    val authored = ComponentSourceArchiveProjection.project(root).entries.map(entry => Entry(entry.path, entry.sha256))
    val generatedmain = _generated_entries(root, managedmainsources, managedmainroots, "generated-source/main")
    val generatedtest = _generated_entries(root, managedtestsources, managedtestroots, "generated-source/test")
    val all = (authored ++ generatedmain ++ generatedtest).sortBy(_.path)
    val duplicates = all.groupBy(_.path).collect { case (path, values) if values.size > 1 => path }.toVector.sorted
    if (duplicates.nonEmpty)
      _invalid(s"Release-source normalized entries are duplicated: ${duplicates.mkString(", ")}")
    all
  }

  private def _generated_entries(root: Path, sources: Vector[Path], roots: Vector[Path], prefix: String): Vector[Entry] = {
    val normalizedRoots = roots.map(_.toAbsolutePath.normalize()).distinct
    val rootreal = root.toRealPath()
    normalizedRoots.foreach { rootpath =>
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
        val matchingRoots = normalizedRoots.filter(rootpath =>
          !Files.isSymbolicLink(rootpath) && Files.isDirectory(rootpath, LinkOption.NOFOLLOW_LINKS) && source.startsWith(rootpath)
        )
        if (matchingRoots.isEmpty)
          _invalid(s"Release-source managed source escapes its managed-source root: $source")
        if (matchingRoots.size > 1)
          _invalid(s"Release-source managed source aliases multiple managed-source roots: $source")
        val selected = matchingRoots.sortBy(_.toString).head
        val realRoot = selected.toRealPath()
        val realSource = source.toRealPath()
        if (!realSource.startsWith(rootreal))
          _invalid(s"Release-source managed source escapes the project root: $source")
        if (!realSource.startsWith(realRoot))
          _invalid(s"Release-source managed source resolves outside its managed-source root: $source")
        val relative = realRoot.relativize(realSource).toString.replace('\\', '/')
        if (!_safe_path(relative))
          _invalid(s"Release-source managed source has an unsafe normalized path: $relative")
        Some(Entry(s"$prefix/$relative", _sha256(Files.readAllBytes(realSource))))
      }
    }.sortBy(_.path).toVector
  }

  private def _entry_source(
    root: Path,
    path: String,
    mainsources: Vector[Path],
    mainroots: Vector[Path],
    testsources: Vector[Path],
    testroots: Vector[Path]
  ): Option[Path] = {
    if (path.startsWith("generated-source/main/"))
      _source_for_generated(path, "generated-source/main/", mainsources, mainroots)
    else if (path.startsWith("generated-source/test/"))
      _source_for_generated(path, "generated-source/test/", testsources, testroots)
    else Some(root.resolve(path))
  }

  private def _source_for_generated(path: String, prefix: String, sources: Vector[Path], roots: Vector[Path]): Option[Path] = {
    val relative = path.substring(prefix.length)
    sources.map(_.toAbsolutePath.normalize()).find { source =>
      if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(source))
        roots.map(_.toAbsolutePath.normalize()).exists(root => source.startsWith(root) && root.relativize(source).toString.replace('\\', '/') == relative)
      else false
    }
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
    val sourcedigest = _digest_field(json, "sourceDigest", path)
    val contentdigest = _digest_field(json, "contentDigest", path)
    val buildevidencedigest = _digest_field(json, "buildEvidenceDigest", path)
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
        (entry \ "path").asOpt[String].getOrElse(_invalid(s"Release-source manifest entry is missing path: $path")),
        _digest_value(entry, "sha256", path)
      )
    }
    val manifest = Manifest(policy, sourcedigest, contentdigest, buildevidencedigest, evidence, entries)
    if (render(manifest) != Files.readString(path, StandardCharsets.UTF_8))
      _invalid(s"Release-source manifest is not canonical: $path")
    _require_manifest_entries(manifest)
    manifest
  }

  private def _require_manifest_entries(manifest: Manifest): Unit = {
    val entries = manifest.entries
    if (entries.map(_.path) != entries.map(_.path).sorted || entries.map(_.path).distinct.size != entries.size)
      _invalid("Release-source manifest entries are not uniquely sorted")
    entries.foreach { entry =>
      if (!_safe_path(entry.path) || entry.path == MANIFEST_FILE_NAME || !_is_sha256(entry.sha256))
        _invalid(s"Release-source manifest contains an unsafe entry: ${entry.path}")
    }
    if (manifest.policy.mode == "restricted" && entries.nonEmpty)
      _invalid("Restricted release-source manifest must not declare source entries")
    if (manifest.contentdigest != _inventory_digest(entries))
      _invalid("Release-source content digest differs from the manifest")
    if (manifest.buildevidencedigest != _build_evidence_digest(manifest.buildevidence))
      _invalid("Release-source build evidence digest differs from the manifest")
  }

  private def _require_actual_entries(directory: Path, declared: Vector[Entry]): Unit = {
    val stream = Files.walk(directory)
    try {
      val actual = stream.iterator().asScala.toVector.collect {
        case path if path != directory && Files.isSymbolicLink(path) =>
          _invalid(s"Release-source staging must not contain a symbolic link: $path")
        case path if Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.getFileName.toString != MANIFEST_FILE_NAME =>
          val relative = directory.relativize(path).toString.replace('\\', '/')
          if (!_safe_path(relative)) _invalid(s"Release-source staging contains an unsafe path: $relative")
          Entry(relative, _sha256(Files.readAllBytes(path)))
      }.sortBy(_.path)
      if (actual.map(_.path) != declared.map(_.path))
        _invalid(s"Release-source staging entries differ from the manifest: $directory")
      actual.zip(declared).foreach { case (actualentry, declaredentry) =>
        if (actualentry.sha256 != declaredentry.sha256)
          _invalid(s"Release-source staged entry digest differs from the manifest: ${declaredentry.path}")
      }
    } finally stream.close()
  }

  private def _json_strings(json: JsObject, key: String, path: Path): Vector[String] =
    (json \ key).asOpt[JsArray].getOrElse(_invalid(s"Release-source build evidence is missing $key: $path")).value.toVector.map {
      case value: play.api.libs.json.JsString => value.value
      case _ => _invalid(s"Release-source build evidence $key must contain strings: $path")
    }

  private def _digest_field(json: JsValue, key: String, path: Path): String =
    _digest_value(json.asOpt[JsObject].getOrElse(Json.obj()), key, path)

  private def _digest_value(json: JsObject, key: String, path: Path): String = {
    val value = (json \ key).asOpt[String].getOrElse(_invalid(s"Release-source manifest is missing $key: $path"))
    if (!_is_sha256(value)) _invalid(s"Release-source manifest contains an invalid $key: $path")
    value
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
      val normalized = Option(value).map(_.trim).getOrElse("")
      if (normalized.isEmpty || _unsafe_metadata(normalized))
        _invalid(s"Release-source $label evidence is empty or contains a local path")
      normalized
    }
  }

  private def _build_evidence_digest(evidence: BuildEvidence): String =
    _sha256((evidence.compilescalacoptions.map("compile\t" + _).mkString +
      evidence.testscalacoptions.map("test\t" + _).mkString +
      evidence.dependencies.map("dependency\t" + _).mkString +
      evidence.generators.map("generator\t" + _).mkString).getBytes(StandardCharsets.UTF_8))

  private def _inventory_digest(entries: Vector[Entry]): String =
    _sha256(entries.map(entry => s"${entry.path}\t${entry.sha256}\n").mkString.getBytes(StandardCharsets.UTF_8))

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
    def go(candidate: Path): Option[Path] =
      if (candidate == null) None
      else if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) Some(candidate)
      else go(candidate.getParent)
    go(path)
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

  private def _is_sha256(value: String): Boolean = value.matches("[0-9a-f]{64}")

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

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
