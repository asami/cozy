package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.goldenport.RAISE
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

/*
 * @since   Jul. 27, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object GenerationProvenance {
  val SCHEMA_VERSION = "cozy.generation-provenance.v1"
  val METADATA_PATH = "target/cozy/generation-provenance.json"

  final case class Inputs(
    cncfTargetVersion: String,
    runtimeDescriptorSha256: String,
    cozyGeneratorVersion: String,
    simpleModelerBackendVersion: String,
    simpleModelingModelVersion: String,
    sourceIdentity: String,
    sourceSha256: String
  )

  final case class Artifact(
    path: String,
    sha256: String
  ) {
    def toJson: JsObject =
      Json.obj(
        "path" -> path,
        "sha256" -> sha256
      )
  }

  final case class SourceSnapshot(
    path: Path,
    identity: String,
    bytes: Vector[Byte],
    sha256: String
  )

  final case class Manifest(
    inputs: Inputs,
    artifacts: Vector[Artifact],
    generatedOutputDigest: String,
    evidenceDigest: String
  ) {
    def toJson: JsObject =
      _payload_json(inputs, artifacts, generatedOutputDigest) ++
        Json.obj("evidenceDigest" -> evidenceDigest)

    def toJsonString: String =
      Json.prettyPrint(toJson) + "\n"
  }

  sealed trait DiagnosticCode {
    def name: String
  }
  object DiagnosticCode {
    case object ProvenanceMissing extends DiagnosticCode {
      val name = "GENERATION_PROVENANCE_MISSING"
    }
    case object ProvenanceMalformed extends DiagnosticCode {
      val name = "GENERATION_PROVENANCE_MALFORMED"
    }
    case object SchemaMismatch extends DiagnosticCode {
      val name = "GENERATION_PROVENANCE_SCHEMA_MISMATCH"
    }
    case object InputMismatch extends DiagnosticCode {
      val name = "GENERATION_PROVENANCE_INPUT_MISMATCH"
    }
    case object SourceTampered extends DiagnosticCode {
      val name = "GENERATION_PROVENANCE_SOURCE_TAMPERED"
    }
    case object OutputTampered extends DiagnosticCode {
      val name = "GENERATION_PROVENANCE_OUTPUT_TAMPERED"
    }
    case object EvidenceTampered extends DiagnosticCode {
      val name = "GENERATION_PROVENANCE_EVIDENCE_TAMPERED"
    }
  }

  final case class Diagnostic(
    code: DiagnosticCode,
    source: String,
    expected: String,
    actual: String,
    message: String,
    correctiveAction: String
  ) {
    def render: String =
      Json.stringify(Json.obj(
        "code" -> code.name,
        "source" -> source,
        "expected" -> expected,
        "actual" -> actual,
        "message" -> message,
        "correctiveAction" -> correctiveAction
      ))
  }

  def stableSourceIdentity(value: String): Either[Vector[Diagnostic], String] = {
    val normalized = Option(value).map(_.trim.replace('\\', '/')).getOrElse("")
    val segments = normalized.split('/').toVector.
      filterNot(x => x.isEmpty || x == ".")
    val identity = segments.mkString("/")
    if (
      identity.isEmpty ||
      normalized.startsWith("/") ||
      normalized.matches("^[A-Za-z]:.*") ||
      segments.contains("..")
    )
      Left(Vector(_diagnostic(
        DiagnosticCode.InputMismatch,
        "generation-source-identity",
        "a non-empty project-relative logical path",
        normalized,
        "Generation source identity is not reproducible across machines.",
        "Pass a stable project-relative CML source identity."
      )))
    else
      Right(identity)
  }

  def requireSourceIdentity(value: Option[String], source: String): String = {
    val identity = value.map(_.trim).filter(_.nonEmpty).getOrElse {
      _raise(Vector(_diagnostic(
        DiagnosticCode.InputMismatch,
        source,
        "--generation-source-identity <project-relative-path>",
        "missing",
        "CNCF-aware generation requires an explicit stable CML source identity.",
        "Pass the owning project's canonical project-relative CML path."
      )))
    }
    stableSourceIdentity(identity) match {
      case Right(normalized) =>
        normalized
      case Left(diagnostics) =>
        _raise(diagnostics)
    }
  }

  def requireValidInvocation(args: Seq[String], source: String): Option[String] = {
    val commands = Set("modeler-scala", "modeler-scala-value")
    val command = args.find(commands.contains)
    val selected =
      _option(args, "cncf-runtime-descriptor").nonEmpty ||
        _option(args, "cncf-runtime-descriptor-sha256").nonEmpty
    if (command.nonEmpty && selected)
      Some(requireSourceIdentity(_option(args, "generation-source-identity"), source))
    else
      None
  }

  def requireSourceSnapshot(
    sourcePath: Path,
    sourceIdentity: String
  ): SourceSnapshot =
    captureSourceSnapshot(sourcePath, sourceIdentity).fold(_raise, identity)

  def captureSourceSnapshot(
    sourcePath: Path,
    sourceIdentity: String
  ): Either[Vector[Diagnostic], SourceSnapshot] =
    stableSourceIdentity(sourceIdentity).flatMap { identity =>
      try {
        val source = sourcePath.toAbsolutePath.normalize()
        _source_bytes(source, identity).map { bytes =>
          SourceSnapshot(source, identity, bytes, _sha256_bytes(bytes.toArray))
        }
      } catch {
        case NonFatal(exception) =>
          Left(Vector(_diagnostic(
            DiagnosticCode.SourceTampered,
            identity,
            "a readable CML source available before generation",
            Option(exception.getMessage).getOrElse(exception.getClass.getName),
            "The CML source cannot be captured for generation provenance.",
            "Restore source readability before starting generation."
          )))
      }
    }

  def prepareOutput(outputRoot: Path): Unit = {
    val path = outputRoot.toAbsolutePath.normalize().resolve(METADATA_PATH)
    try
      Files.deleteIfExists(path)
    catch {
      case NonFatal(exception) =>
        _raise(Vector(_diagnostic(
          DiagnosticCode.EvidenceTampered,
          path.toString,
          "no stale provenance before generation",
          Option(exception.getMessage).getOrElse(exception.getClass.getName),
          "Existing generation provenance cannot be cleared before generation.",
          "Restore metadata-directory write access before retrying generation."
        )))
    }
  }

  def withCapturedSource[A](snapshot: SourceSnapshot)(body: Path => A): A = {
    val directory = Files.createTempDirectory("cozy-generation-source-")
    val filename = Option(snapshot.path.getFileName).
      map(_.toString).
      filter(_.nonEmpty).
      getOrElse("source.cml")
    val source = directory.resolve(filename)
    try {
      Files.write(source, snapshot.bytes.toArray)
      body(source)
    } finally {
      Files.deleteIfExists(source)
      Files.deleteIfExists(directory)
    }
  }

  def validateSourceSnapshot(
    snapshot: SourceSnapshot
  ): Either[Vector[Diagnostic], SourceSnapshot] =
    _source_sha256(snapshot.path, snapshot.identity).flatMap { actual =>
      if (actual == snapshot.sha256)
        Right(snapshot)
      else
        Left(Vector(_diagnostic(
          DiagnosticCode.SourceTampered,
          snapshot.identity,
          snapshot.sha256,
          actual,
          "The CML source changed while Scala generation was running.",
          "Retry generation from one stable source revision."
        )))
    }

  def write(
    outputRoot: Path,
    sourceSnapshot: SourceSnapshot,
    inputs: Inputs
  ): Manifest =
    _write(outputRoot, sourceSnapshot, inputs, Vector.empty)

  private def _write(
    outputroot: Path,
    sourcesnapshot: SourceSnapshot,
    inputs: Inputs,
    excludedoutputroots: Vector[Path]
  ): Manifest = {
    val root = outputroot.toAbsolutePath.normalize()
    val snapshot = validateSourceSnapshot(sourcesnapshot).fold(_raise, identity)
    val source = snapshot.path
    val normalizedinputs = _normalize_inputs(inputs).fold(_raise, identity)
    if (
      normalizedinputs.sourceIdentity != snapshot.identity ||
      normalizedinputs.sourceSha256 != snapshot.sha256
    )
      _raise(Vector(_diagnostic(
        DiagnosticCode.InputMismatch,
        "source-snapshot",
        s"${snapshot.identity}:${snapshot.sha256}",
        s"${normalizedinputs.sourceIdentity}:${normalizedinputs.sourceSha256}",
        "Generation provenance inputs do not identify the captured CML source.",
        "Build provenance inputs directly from the pre-generation source snapshot."
      )))
    val artifacts = _scala_artifacts(root, excludedoutputroots).fold(_raise, identity)
    if (artifacts.isEmpty)
      _raise(Vector(_diagnostic(
        DiagnosticCode.OutputTampered,
        root.toString,
        "at least one generated Scala artifact",
        "none",
        "Generation provenance cannot describe an empty output.",
        "Complete Scala source generation before writing provenance."
      )))
    val outputdigest = _artifact_digest(artifacts)
    val evidencedigest = _evidence_digest(normalizedinputs, artifacts, outputdigest)
    val manifest = Manifest(normalizedinputs, artifacts, outputdigest, evidencedigest)
    val path = root.resolve(METADATA_PATH)
    val directory = path.getParent
    Files.createDirectories(directory)
    val temporary = Files.createTempFile(directory, ".generation-provenance-", ".json")
    try {
      Files.writeString(temporary, manifest.toJsonString, StandardCharsets.UTF_8)
      _validate(
        temporary,
        root,
        source,
        normalizedinputs,
        excludedoutputroots
      ) match {
        case Right(_) =>
          validateSourceSnapshot(snapshot).fold(_raise, identity)
          Files.move(
            temporary,
            path,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
          )
          manifest
        case Left(diagnostics) =>
          _raise(diagnostics)
      }
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  def validate(
    provenancePath: Path,
    outputRoot: Path,
    sourcePath: Path,
    expectedInputs: Inputs
  ): Either[Vector[Diagnostic], Manifest] =
    _validate(
      provenancePath,
      outputRoot,
      sourcePath,
      expectedInputs,
      Vector.empty
    )

  private def _validate(
    provenancepath: Path,
    outputroot: Path,
    sourcepath: Path,
    expectedinputs: Inputs,
    excludedoutputroots: Vector[Path]
  ): Either[Vector[Diagnostic], Manifest] = {
    val provenance = provenancepath.toAbsolutePath.normalize()
    val root = outputroot.toAbsolutePath.normalize()
    val source = sourcepath.toAbsolutePath.normalize()
    if (!Files.isRegularFile(provenance))
      Left(Vector(_diagnostic(
        DiagnosticCode.ProvenanceMissing,
        provenance.toString,
        "a readable generation provenance document",
        "missing",
        "Generation provenance does not exist.",
        "Regenerate the output with the selected Cozy generator."
      )))
    else
      for {
        manifest <- _read_manifest(provenance)
        expected <- _normalize_inputs(expectedinputs)
        sourcesha <- _source_sha256(source, manifest.inputs.sourceIdentity)
        artifacts <- _scala_artifacts(root, excludedoutputroots)
        validated <- _validate_manifest(
          manifest,
          expected,
          sourcesha,
          artifacts,
          provenance,
          root
        )
      } yield validated
  }

  def requireValidGeneratedOutput(
    outputRoot: Path,
    sourcePath: Path,
    expectedInputs: Inputs
  ): Manifest = {
    val root = outputRoot.toAbsolutePath.normalize()
    validate(
      root.resolve(METADATA_PATH),
      root,
      sourcePath,
      expectedInputs
    ).fold(_raise, identity)
  }

  def requireValidForPackaging(
    provenancePath: Path,
    projectRoot: Path,
    expectedCncfTargetVersion: Option[String],
    expectedCozyGeneratorVersion: Option[String]
  ): Manifest = {
    val provenance = provenancePath.toAbsolutePath.normalize()
    val root = projectRoot.toAbsolutePath.normalize()
    val manifest =
      if (Files.isRegularFile(provenance))
        _read_manifest(provenance).fold(_raise, identity)
      else
        _raise(Vector(_diagnostic(
          DiagnosticCode.ProvenanceMissing,
          provenance.toString,
          "a readable generation provenance document",
          "missing",
          "Generation provenance does not exist.",
          "Regenerate the output with the selected Cozy generator."
        )))
    val expected = manifest.inputs.copy(
      cncfTargetVersion =
        expectedCncfTargetVersion.getOrElse(manifest.inputs.cncfTargetVersion),
      cozyGeneratorVersion =
        expectedCozyGeneratorVersion.getOrElse(manifest.inputs.cozyGeneratorVersion)
    )
    val source = root.resolve(manifest.inputs.sourceIdentity).normalize()
    validate(provenance, root, source, expected).fold(_raise, identity)
  }

  def requireValidPackagedEvidence(
    provenancePath: Path,
    expectedCncfTargetVersion: String,
    expectedCozyGeneratorVersion: String
  ): Manifest = {
    val provenance = provenancePath.toAbsolutePath.normalize()
    val manifest =
      if (Files.isRegularFile(provenance))
        _read_manifest(provenance).fold(_raise, identity)
      else
        _raise(Vector(_diagnostic(
          DiagnosticCode.ProvenanceMissing,
          provenance.toString,
          "a packaged generation provenance document",
          "missing",
          "Packaged generation provenance does not exist.",
          "Rebuild the release CAR with the selected Cozy generator."
        )))
    val normalizedinputs = _normalize_inputs(manifest.inputs).fold(_raise, identity)
    val inputdiagnostics = _input_diagnostics(
      normalizedinputs,
      normalizedinputs.copy(
        cncfTargetVersion = expectedCncfTargetVersion,
        cozyGeneratorVersion = expectedCozyGeneratorVersion
      )
    )
    val artifactdiagnostics = {
      val invalid = manifest.artifacts.filter { artifact =>
        val path =
          Option(artifact.path).map(_.trim.replace('\\', '/')).getOrElse("")
        path.isEmpty ||
          path.startsWith("/") ||
          path.split('/').contains("..") ||
          !artifact.sha256.matches("[0-9a-f]{64}")
      }
      val duplicates =
        manifest.artifacts.groupBy(_.path).collect {
          case (path, xs) if xs.size > 1 => path
        }.toVector.sorted
      val emptydiagnostics =
        if (manifest.artifacts.nonEmpty)
          Vector.empty
        else
          Vector(_diagnostic(
            DiagnosticCode.OutputTampered,
            provenance.toString,
            "at least one generated Scala artifact",
            "none",
            "Packaged generation provenance describes no generated output.",
            "Rebuild the CAR after generating Scala sources."
          ))
      emptydiagnostics ++
        invalid.map { artifact =>
          _diagnostic(
            DiagnosticCode.OutputTampered,
            provenance.toString,
            "safe project-relative artifact paths with SHA-256 digests",
            s"${artifact.path}:${artifact.sha256}",
            "Packaged generation provenance contains an invalid artifact identity.",
            "Regenerate the CAR and its provenance."
          )
        } ++
        duplicates.map { path =>
          _diagnostic(
            DiagnosticCode.OutputTampered,
            provenance.toString,
            "unique generated artifact paths",
            path,
            "Packaged generation provenance repeats an artifact path.",
            "Regenerate the CAR and its provenance."
          )
        }
    }
    val outputdigest = _artifact_digest(manifest.artifacts)
    val outputdiagnostics =
      if (manifest.generatedOutputDigest == outputdigest)
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.OutputTampered,
          provenance.toString,
          manifest.generatedOutputDigest,
          outputdigest,
          "Packaged generation provenance has a contradictory output digest.",
          "Rebuild the CAR from validated generated output."
        ))
    val evidencedigest = _evidence_digest(
      normalizedinputs,
      manifest.artifacts,
      manifest.generatedOutputDigest
    )
    val evidencediagnostics =
      if (manifest.evidenceDigest == evidencedigest)
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.EvidenceTampered,
          provenance.toString,
          manifest.evidenceDigest,
          evidencedigest,
          "Packaged generation provenance fields contradict their evidence digest.",
          "Rebuild the CAR from validated generation evidence."
        ))
    val diagnostics =
      inputdiagnostics ++
        artifactdiagnostics ++
        outputdiagnostics ++
        evidencediagnostics
    if (diagnostics.nonEmpty)
      _raise(diagnostics)
    manifest.copy(inputs = normalizedinputs)
  }

  def rebindForPackaging(
    delegatedProvenancePath: Path,
    delegatedOutputRoot: Path,
    projectRoot: Path
  ): Manifest = {
    val delegatedprovenance = delegatedProvenancePath.toAbsolutePath.normalize()
    val delegatedroot = delegatedOutputRoot.toAbsolutePath.normalize()
    val projectroot = projectRoot.toAbsolutePath.normalize()
    val parsedmanifest = _read_manifest(delegatedprovenance).fold(_raise, identity)
    val source = projectroot.resolve(parsedmanifest.inputs.sourceIdentity).normalize()
    val manifest = validate(
      delegatedprovenance,
      delegatedroot,
      source,
      parsedmanifest.inputs
    ).fold(_raise, identity)
    val snapshot = requireSourceSnapshot(source, manifest.inputs.sourceIdentity)
    if (snapshot.sha256 != manifest.inputs.sourceSha256)
      _raise(Vector(_diagnostic(
        DiagnosticCode.SourceTampered,
        manifest.inputs.sourceIdentity,
        manifest.inputs.sourceSha256,
        snapshot.sha256,
        "The owning project CML source no longer matches delegated generation provenance.",
        "Restore the source or rerun delegated generation from one stable revision."
      )))
    _write(
      projectroot,
      snapshot,
      manifest.inputs,
      Vector(delegatedroot)
    )
  }

  private def _normalize_inputs(inputs: Inputs): Either[Vector[Diagnostic], Inputs] = {
    val sourceidentity = stableSourceIdentity(inputs.sourceIdentity)
    val values = Vector(
      "cncfTargetVersion" -> inputs.cncfTargetVersion,
      "runtimeDescriptorSha256" -> inputs.runtimeDescriptorSha256,
      "cozyGeneratorVersion" -> inputs.cozyGeneratorVersion,
      "simpleModelerBackendVersion" -> inputs.simpleModelerBackendVersion,
      "simpleModelingModelVersion" -> inputs.simpleModelingModelVersion,
      "sourceSha256" -> inputs.sourceSha256
    ).map { case (name, value) =>
      name -> Option(value).map(_.trim).getOrElse("")
    }
    val diagnostics = values.flatMap { case (name, value) =>
      if (value.isEmpty)
        Vector(_diagnostic(
          DiagnosticCode.InputMismatch,
          name,
          "a non-empty exact generation input",
          "missing",
          s"Generation provenance input $name is required.",
          "Resolve the exact generation input before source generation."
        ))
      else if (
        Set("runtimeDescriptorSha256", "sourceSha256").contains(name) &&
        !value.matches("[0-9a-f]{64}")
      )
        Vector(_diagnostic(
          DiagnosticCode.InputMismatch,
          name,
          "64 lowercase hexadecimal SHA-256 characters",
          value,
          s"Generation provenance input $name is not a valid SHA-256.",
          "Pass the digest computed from the exact selected bytes."
        ))
      else
        Vector.empty
    }
    val alldiagnostics = sourceidentity.left.toOption.getOrElse(Vector.empty) ++ diagnostics
    if (alldiagnostics.nonEmpty)
      Left(alldiagnostics)
    else
      Right(inputs.copy(
        cncfTargetVersion = values(0)._2,
        runtimeDescriptorSha256 = values(1)._2,
        cozyGeneratorVersion = values(2)._2,
        simpleModelerBackendVersion = values(3)._2,
        simpleModelingModelVersion = values(4)._2,
        sourceIdentity = sourceidentity.toOption.get,
        sourceSha256 = values(5)._2
      ))
  }

  private def _validate_manifest(
    manifest: Manifest,
    expected: Inputs,
    sourcesha: String,
    artifacts: Vector[Artifact],
    provenance: Path,
    root: Path
  ): Either[Vector[Diagnostic], Manifest] = {
    val inputdiagnostics = _input_diagnostics(manifest.inputs, expected)
    val sourcediagnostics =
      if (manifest.inputs.sourceSha256 == sourcesha)
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.SourceTampered,
          manifest.inputs.sourceIdentity,
          manifest.inputs.sourceSha256,
          sourcesha,
          "The CML source digest no longer matches generation provenance.",
          "Restore the source or regenerate the output and provenance together."
        ))
    val outputdigest = _artifact_digest(artifacts)
    val outputdiagnostics =
      if (
        manifest.artifacts == artifacts &&
        manifest.generatedOutputDigest == outputdigest
      )
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.OutputTampered,
          root.toString,
          s"${manifest.generatedOutputDigest}:${_artifact_identity(manifest.artifacts)}",
          s"$outputdigest:${_artifact_identity(artifacts)}",
          "Generated Scala output no longer matches generation provenance.",
          "Restore the generated output or regenerate it from the recorded inputs."
        ))
    val evidencedigest = _evidence_digest(
      manifest.inputs,
      manifest.artifacts,
      manifest.generatedOutputDigest
    )
    val evidencediagnostics =
      if (manifest.evidenceDigest == evidencedigest)
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.EvidenceTampered,
          provenance.toString,
          manifest.evidenceDigest,
          evidencedigest,
          "Generation provenance fields contradict their evidence digest.",
          "Restore the provenance document or regenerate it from trusted inputs."
        ))
    val diagnostics =
      inputdiagnostics ++ sourcediagnostics ++ outputdiagnostics ++ evidencediagnostics
    if (diagnostics.isEmpty)
      Right(manifest)
    else
      Left(diagnostics)
  }

  private def _read_manifest(path: Path): Either[Vector[Diagnostic], Manifest] =
    try {
      val json = Json.parse(Files.readString(path, StandardCharsets.UTF_8))
      _parse_manifest(json, path)
    } catch {
      case NonFatal(exception) =>
        Left(Vector(_diagnostic(
          DiagnosticCode.ProvenanceMalformed,
          path.toString,
          SCHEMA_VERSION,
          Option(exception.getMessage).getOrElse(exception.getClass.getName),
          "Generation provenance cannot be parsed.",
          "Restore or regenerate the provenance document."
        )))
    }

  private def _parse_manifest(
    json: JsValue,
    path: Path
  ): Either[Vector[Diagnostic], Manifest] = {
    val schema = (json \ "schemaVersion").asOpt[String]
    if (schema != Some(SCHEMA_VERSION))
      Left(Vector(_diagnostic(
        DiagnosticCode.SchemaMismatch,
        path.toString,
        SCHEMA_VERSION,
        schema.getOrElse("missing"),
        "Generation provenance schema is unsupported.",
        "Regenerate provenance with the selected Cozy generator."
      )))
    else
      _validate_manifest_shape(json, path).flatMap { _ =>
        try {
          val target = json \ "target"
          val generator = json \ "generator"
          val source = json \ "source"
          val output = json \ "output"
          val inputs = Inputs(
            (target \ "cncfVersion").as[String],
            (target \ "runtimeDescriptorSha256").as[String],
            (generator \ "cozyVersion").as[String],
            (generator \ "simpleModelerBackendVersion").as[String],
            (generator \ "simpleModelingModelVersion").as[String],
            (source \ "identity").as[String],
            (source \ "sha256").as[String]
          )
          val artifacts = (output \ "artifacts").as[Vector[JsObject]].map { artifact =>
            Artifact(
              (artifact \ "path").as[String],
              (artifact \ "sha256").as[String]
            )
          }
          Right(Manifest(
            inputs,
            artifacts,
            (output \ "digest").as[String],
            (json \ "evidenceDigest").as[String]
          ))
        } catch {
          case NonFatal(exception) =>
            Left(Vector(_diagnostic(
              DiagnosticCode.ProvenanceMalformed,
              path.toString,
              "all required provenance fields",
              Option(exception.getMessage).getOrElse(exception.getClass.getName),
              "Generation provenance is missing or has malformed fields.",
              "Restore or regenerate the provenance document."
            )))
        }
      }
  }

  private def _validate_manifest_shape(
    json: JsValue,
    path: Path
  ): Either[Vector[Diagnostic], Unit] = {
    val root = json.asOpt[JsObject]
    val diagnostics = root.toVector.flatMap { rootobject =>
      val rootdiagnostics = _unexpected_field_diagnostics(
        rootobject,
        Set("schemaVersion", "target", "generator", "source", "output", "evidenceDigest"),
        path.toString
      )
      val targetdiagnostics = (rootobject \ "target").asOpt[JsObject].toVector.flatMap(
        _unexpected_field_diagnostics(
          _,
          Set("cncfVersion", "runtimeDescriptorSha256"),
          s"${path.toString}:target"
        )
      )
      val generatordiagnostics = (rootobject \ "generator").asOpt[JsObject].toVector.flatMap(
        _unexpected_field_diagnostics(
          _,
          Set("cozyVersion", "simpleModelerBackendVersion", "simpleModelingModelVersion"),
          s"${path.toString}:generator"
        )
      )
      val sourcediagnostics = (rootobject \ "source").asOpt[JsObject].toVector.flatMap(
        _unexpected_field_diagnostics(
          _,
          Set("identity", "sha256"),
          s"${path.toString}:source"
        )
      )
      val outputdiagnostics = (rootobject \ "output").asOpt[JsObject].toVector.flatMap { output =>
        _unexpected_field_diagnostics(
          output,
          Set("artifacts", "digest"),
          s"${path.toString}:output"
        ) ++
          (output \ "artifacts").asOpt[Vector[JsObject]].toVector.flatten.zipWithIndex.flatMap {
            case (artifact, index) =>
              _unexpected_field_diagnostics(
                artifact,
                Set("path", "sha256"),
                s"${path.toString}:output.artifacts[$index]"
              )
          }
      }
      rootdiagnostics ++
        targetdiagnostics ++
        generatordiagnostics ++
        sourcediagnostics ++
        outputdiagnostics
    }
    if (diagnostics.isEmpty)
      Right(())
    else
      Left(diagnostics)
  }

  private def _unexpected_field_diagnostics(
    json: JsObject,
    expected: Set[String],
    source: String
  ): Vector[Diagnostic] = {
    val unexpected = json.keys.diff(expected).toVector.sorted
    if (unexpected.isEmpty)
      Vector.empty
    else
      Vector(_diagnostic(
        DiagnosticCode.ProvenanceMalformed,
        source,
        expected.toVector.sorted.mkString(","),
        json.keys.toVector.sorted.mkString(","),
        s"Generation provenance contains unsupported fields: ${unexpected.mkString(",")}.",
        "Remove unsupported fields or regenerate the provenance document."
      ))
  }

  private def _input_diagnostics(
    actual: Inputs,
    expected: Inputs
  ): Vector[Diagnostic] = {
    val fields = Vector(
      "target.cncfVersion" -> (expected.cncfTargetVersion -> actual.cncfTargetVersion),
      "target.runtimeDescriptorSha256" ->
        (expected.runtimeDescriptorSha256 -> actual.runtimeDescriptorSha256),
      "generator.cozyVersion" ->
        (expected.cozyGeneratorVersion -> actual.cozyGeneratorVersion),
      "generator.simpleModelerBackendVersion" ->
        (expected.simpleModelerBackendVersion -> actual.simpleModelerBackendVersion),
      "generator.simpleModelingModelVersion" ->
        (expected.simpleModelingModelVersion -> actual.simpleModelingModelVersion),
      "source.identity" -> (expected.sourceIdentity -> actual.sourceIdentity),
      "source.sha256" -> (expected.sourceSha256 -> actual.sourceSha256)
    )
    fields.flatMap { case (name, (expectedvalue, actualvalue)) =>
      if (expectedvalue == actualvalue)
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.InputMismatch,
          name,
          expectedvalue,
          actualvalue,
          s"Generation provenance contradicts the selected $name.",
          "Regenerate with one consistent owning-build input contract."
        ))
    }
  }

  private def _source_sha256(
    source: Path,
    sourceidentity: String
  ): Either[Vector[Diagnostic], String] =
    _source_bytes(source, sourceidentity).map(bytes => _sha256_bytes(bytes.toArray))

  private def _source_bytes(
    source: Path,
    sourceidentity: String
  ): Either[Vector[Diagnostic], Vector[Byte]] =
    try {
      if (Files.isRegularFile(source))
        Right(Files.readAllBytes(source).toVector)
      else
        Left(Vector(_diagnostic(
          DiagnosticCode.SourceTampered,
          sourceidentity,
          "a readable CML source matching the recorded digest",
          "missing",
          "The recorded CML source no longer exists.",
          "Restore the source or regenerate the output and provenance together."
        )))
    } catch {
      case NonFatal(exception) =>
        Left(Vector(_diagnostic(
          DiagnosticCode.SourceTampered,
          sourceidentity,
          "a readable CML source matching the recorded digest",
          Option(exception.getMessage).getOrElse(exception.getClass.getName),
          "The recorded CML source cannot be read.",
          "Restore source readability or regenerate the output and provenance together."
        )))
    }

  private def _scala_artifacts(
    root: Path,
    excludedroots: Vector[Path] = Vector.empty
  ): Either[Vector[Diagnostic], Vector[Artifact]] = {
    val target = root.resolve("target")
    val excluded = excludedroots.map(_.toAbsolutePath.normalize())
    if (!Files.isDirectory(target))
      Right(Vector.empty)
    else {
      try {
        val stream = Files.walk(target)
        try
          Right(stream.iterator().asScala.
            filter(path => Files.isRegularFile(path) && path.getFileName.toString.endsWith(".scala")).
            filterNot(path =>
              excluded.exists(excludedroot =>
                path.toAbsolutePath.normalize().startsWith(excludedroot)
              )
            ).
            map { path =>
              val relative = root.relativize(path.toAbsolutePath.normalize()).toString.
                replace(java.io.File.separatorChar, '/')
              Artifact(relative, _sha256(path))
            }.
            toVector.
            sortBy(_.path))
        finally
          stream.close()
      } catch {
        case NonFatal(exception) =>
          Left(Vector(_diagnostic(
            DiagnosticCode.OutputTampered,
            root.toString,
            "readable generated Scala output",
            Option(exception.getMessage).getOrElse(exception.getClass.getName),
            "Generated Scala output cannot be enumerated or read.",
            "Restore output readability or regenerate the output and provenance together."
          )))
      }
    }
  }

  private def _option(args: Seq[String], name: String): Option[String] = {
    val inlineprefix = s"--$name="
    args.zipWithIndex.collectFirst {
      case (value, _) if value.startsWith(inlineprefix) =>
        value.drop(inlineprefix.length).trim
      case (value, index) if value == s"--$name" && index + 1 < args.length =>
        args(index + 1).trim
    }.filter(_.nonEmpty)
  }

  private def _artifact_digest(artifacts: Vector[Artifact]): String =
    _sha256_bytes(
      artifacts.map(artifact => s"${artifact.path}\u0000${artifact.sha256}\n").
        mkString.
        getBytes(StandardCharsets.UTF_8)
    )

  private def _artifact_identity(artifacts: Vector[Artifact]): String =
    artifacts.map(artifact => s"${artifact.path}:${artifact.sha256}").mkString(",")

  private def _evidence_digest(
    inputs: Inputs,
    artifacts: Vector[Artifact],
    outputdigest: String
  ): String =
    _sha256_bytes(
      Json.stringify(_payload_json(inputs, artifacts, outputdigest)).
        getBytes(StandardCharsets.UTF_8)
    )

  private def _payload_json(
    inputs: Inputs,
    artifacts: Vector[Artifact],
    outputdigest: String
  ): JsObject =
    Json.obj(
      "schemaVersion" -> SCHEMA_VERSION,
      "target" -> Json.obj(
        "cncfVersion" -> inputs.cncfTargetVersion,
        "runtimeDescriptorSha256" -> inputs.runtimeDescriptorSha256
      ),
      "generator" -> Json.obj(
        "cozyVersion" -> inputs.cozyGeneratorVersion,
        "simpleModelerBackendVersion" -> inputs.simpleModelerBackendVersion,
        "simpleModelingModelVersion" -> inputs.simpleModelingModelVersion
      ),
      "source" -> Json.obj(
        "identity" -> inputs.sourceIdentity,
        "sha256" -> inputs.sourceSha256
      ),
      "output" -> Json.obj(
        "artifacts" -> JsArray(artifacts.map(_.toJson)),
        "digest" -> outputdigest
      )
    )

  private def _sha256(path: Path): String =
    _sha256_bytes(Files.readAllBytes(path))

  private def _sha256_bytes(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").
      digest(bytes).
      map(byte => f"${byte & 0xff}%02x").
      mkString

  private def _diagnostic(
    code: DiagnosticCode,
    source: String,
    expected: String,
    actual: String,
    message: String,
    correctiveaction: String
  ): Diagnostic =
    Diagnostic(code, source, expected, actual, message, correctiveaction)

  private def _raise(diagnostics: Vector[Diagnostic]): Nothing =
    RAISE.invalidArgumentFault(diagnostics.map(_.render).mkString("[", ",", "]"))
}
