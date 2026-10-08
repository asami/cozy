package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.goldenport.RAISE
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

/*
 * @since   Jul. 27, 2026
 *  version Jul. 28, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object GenerationProvenance {
  val SCHEMA_VERSION = "cozy.generation-provenance.v1"
  val AGGREGATE_SCHEMA_VERSION = "cozy.generation-provenance.v2"
  val METADATA_PATH = "target/cozy/generation-provenance.json"

  final case class Inputs(
    cncfTargetVersion: String,
    cozyGeneratorVersion: String,
    simpleModelerBackendVersion: String,
    simpleModelingModelVersion: String,
    sourceIdentity: String
  )

  final case class Artifact(path: String) {
    def toJson: JsObject = Json.obj("path" -> path)
  }

  final case class SourceInput(path: Path, identity: String)

  trait ValidatedManifest {
    def schemaVersion: String
    def toJson: JsObject
    def toJsonString: String
  }

  type DelegatedInput = GenerationProvenanceAggregate.DelegatedInput
  val DelegatedInput = GenerationProvenanceAggregate.DelegatedInput

  type AggregateInputs = GenerationProvenanceAggregate.AggregateInputs
  val AggregateInputs = GenerationProvenanceAggregate.AggregateInputs

  type AggregateSource = GenerationProvenanceAggregate.AggregateSource
  val AggregateSource = GenerationProvenanceAggregate.AggregateSource

  final case class Manifest(
    inputs: Inputs,
    artifacts: Vector[Artifact]
  ) extends ValidatedManifest {
    val schemaVersion = SCHEMA_VERSION

    def toJson: JsObject =
      _payload_json(inputs, artifacts)

    def toJsonString: String =
      Json.prettyPrint(toJson) + "\n"
  }

  type AggregateManifest = GenerationProvenanceAggregate.AggregateManifest
  val AggregateManifest = GenerationProvenanceAggregate.AggregateManifest

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
    case object SourceAmbiguous extends DiagnosticCode {
      val name = "GENERATION_PROVENANCE_SOURCE_AMBIGUOUS"
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

  def requireSourceInput(
    sourcePath: Path,
    sourceIdentity: String
  ): SourceInput =
    captureSourceInput(sourcePath, sourceIdentity).fold(_raise, identity)

  def captureSourceInput(
    sourcePath: Path,
    sourceIdentity: String
  ): Either[Vector[Diagnostic], SourceInput] =
    stableSourceIdentity(sourceIdentity).flatMap { identity =>
      try {
        val source = sourcePath.toAbsolutePath.normalize()
        _source_readable(source, identity).map(_ => SourceInput(source, identity))
      } catch {
        case NonFatal(exception) =>
          Left(Vector(_diagnostic(
            DiagnosticCode.SourceTampered,
            identity,
            "a readable regular CML source",
            Option(exception.getMessage).getOrElse(exception.getClass.getName),
            "The selected CML source cannot be accessed.",
            "Restore source readability before starting generation."
          )))
      }
    }

  def write(
    outputRoot: Path,
    sourceInput: SourceInput,
    inputs: Inputs
  ): Manifest =
    _write(outputRoot, sourceInput, inputs, Vector.empty)

  private def _write(
    outputroot: Path,
    sourceinput: SourceInput,
    inputs: Inputs,
    excludedoutputroots: Vector[Path]
  ): Manifest = {
    val root = outputroot.toAbsolutePath.normalize()
    val selected = captureSourceInput(sourceinput.path, sourceinput.identity).fold(_raise, identity)
    val source = selected.path
    val normalizedinputs = _normalize_inputs(inputs).fold(_raise, identity)
    if (normalizedinputs.sourceIdentity != selected.identity)
      _raise(Vector(_diagnostic(
        DiagnosticCode.InputMismatch,
        "source-input",
        selected.identity,
        normalizedinputs.sourceIdentity,
        "Generation provenance inputs do not identify the selected CML source.",
        "Use the explicit canonical identity of the selected source."
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
    val manifest = Manifest(normalizedinputs, artifacts)
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
    if (!Files.isRegularFile(provenance) || !Files.isReadable(provenance))
      Left(Vector(_diagnostic(
        DiagnosticCode.ProvenanceMissing,
        provenance.toString,
        "a readable generation provenance document",
        "missing",
        "Generation provenance does not exist.",
        "Regenerate the output with the selected Cozy generator."
      )))
    else
      _read_manifest(provenance).flatMap(
        _validate_v1_manifest(
          _,
          root,
          source,
          expectedinputs,
          provenance,
          excludedoutputroots
        )
      )
  }

  private def _validate_v1_manifest(
    manifest: Manifest,
    outputroot: Path,
    sourcepath: Path,
    expectedinputs: Inputs,
    provenancepath: Path,
    excludedoutputroots: Vector[Path]
  ): Either[Vector[Diagnostic], Manifest] =
    for {
      expected <- _normalize_inputs(expectedinputs)
      _ <- _source_readable(sourcepath, manifest.inputs.sourceIdentity)
      artifacts <- _scala_artifacts(outputroot, excludedoutputroots)
      validated <- _validate_manifest(
        manifest,
        expected,
        artifacts,
        provenancepath,
        outputroot
      )
    } yield validated

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
  ): ValidatedManifest = {
    val provenance = provenancePath.toAbsolutePath.normalize()
    val root = projectRoot.toAbsolutePath.normalize()
    GenerationProvenanceAggregate.readIfAggregate(provenance).fold(_raise, identity) match {
      case Some(v2) =>
        GenerationProvenanceAggregate.validateForPackaging(
          v2,
          root,
          expectedCncfTargetVersion,
          expectedCozyGeneratorVersion,
          provenance
        )
      case None =>
        val v1 =
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
        val expected = v1.inputs.copy(
          cncfTargetVersion =
            expectedCncfTargetVersion.getOrElse(v1.inputs.cncfTargetVersion),
          cozyGeneratorVersion =
            expectedCozyGeneratorVersion.getOrElse(v1.inputs.cozyGeneratorVersion)
        )
        val source = root.resolve(v1.inputs.sourceIdentity).normalize()
        _validate_v1_manifest(
          v1,
          root,
          source,
          expected,
          provenance,
          Vector.empty
        ).fold(_raise, identity)
    }
  }

  def requireValidPackagedMetadata(
    provenancePath: Path,
    expectedCncfTargetVersion: String,
    expectedCozyGeneratorVersion: String
  ): ValidatedManifest = {
    val provenance = provenancePath.toAbsolutePath.normalize()
    GenerationProvenanceAggregate.readIfAggregate(provenance).fold(_raise, identity) match {
      case Some(v2) =>
        GenerationProvenanceAggregate.validatePackagedMetadata(
          provenance,
          v2,
          expectedCncfTargetVersion,
          expectedCozyGeneratorVersion
        )
      case None =>
        val v1 =
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
        _require_valid_packaged_v1(
          provenance,
          v1,
          expectedCncfTargetVersion,
          expectedCozyGeneratorVersion
        )
    }
  }

  private def _require_valid_packaged_v1(
    provenance: Path,
    manifest: Manifest,
    expectedcncftargetversion: String,
    expectedcozygeneratorversion: String
  ): Manifest = {
    val normalizedinputs = _normalize_inputs(manifest.inputs).fold(_raise, identity)
    val expected = _normalize_inputs(normalizedinputs.copy(
      cncfTargetVersion = expectedcncftargetversion,
      cozyGeneratorVersion = expectedcozygeneratorversion
    )).fold(_raise, identity)
    val diagnostics = _input_diagnostics(manifest.inputs, normalizedinputs) ++
      _input_diagnostics(normalizedinputs, expected) ++
      _artifact_diagnostics(manifest.artifacts, provenance.toString)
    if (diagnostics.nonEmpty)
      _raise(diagnostics)
    manifest.copy(inputs = normalizedinputs)
  }

  def rebindForPackaging(
    delegatedProvenancePath: Path,
    delegatedOutputRoot: Path,
    projectRoot: Path
  ): AggregateManifest =
    GenerationProvenanceAggregate.aggregateForPackaging(
      Vector(DelegatedInput(delegatedProvenancePath, delegatedOutputRoot)),
      projectRoot
    )

  def aggregateForPackaging(
    delegatedInputs: Vector[DelegatedInput],
    projectRoot: Path
  ): AggregateManifest =
    GenerationProvenanceAggregate.aggregateForPackaging(delegatedInputs, projectRoot)

  private def _normalize_inputs(inputs: Inputs): Either[Vector[Diagnostic], Inputs] = {
    val sourceidentity = stableSourceIdentity(inputs.sourceIdentity)
    val values = Vector(
      "cncfTargetVersion" -> inputs.cncfTargetVersion,
      "cozyGeneratorVersion" -> inputs.cozyGeneratorVersion,
      "simpleModelerBackendVersion" -> inputs.simpleModelerBackendVersion,
      "simpleModelingModelVersion" -> inputs.simpleModelingModelVersion
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
      else
        Vector.empty
    }
    val alldiagnostics = sourceidentity.left.toOption.getOrElse(Vector.empty) ++ diagnostics
    if (alldiagnostics.nonEmpty)
      Left(alldiagnostics)
    else
      Right(inputs.copy(
        cncfTargetVersion = values(0)._2,
        cozyGeneratorVersion = values(1)._2,
        simpleModelerBackendVersion = values(2)._2,
        simpleModelingModelVersion = values(3)._2,
        sourceIdentity = sourceidentity.toOption.get
      ))
  }

  private def _validate_manifest(
    manifest: Manifest,
    expected: Inputs,
    artifacts: Vector[Artifact],
    provenance: Path,
    root: Path
  ): Either[Vector[Diagnostic], Manifest] = {
    val inputdiagnostics = _input_diagnostics(manifest.inputs, expected)
    val artifactdiagnostics = _artifact_diagnostics(manifest.artifacts, provenance.toString)
    val outputdiagnostics =
      if (manifest.artifacts == artifacts)
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.OutputTampered,
          root.toString,
          manifest.artifacts.map(_.path).mkString(","),
          artifacts.map(_.path).mkString(","),
          "Generated Scala path inventory does not match generation provenance.",
          "Restore the required paths or regenerate output and metadata together."
        ))
    val diagnostics = inputdiagnostics ++ artifactdiagnostics ++ outputdiagnostics
    if (diagnostics.isEmpty) Right(manifest) else Left(diagnostics)
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

  private[modeler] def _read_v1_for_aggregate(
    path: Path
  ): Either[Vector[Diagnostic], Manifest] =
    _read_manifest(path)

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
      try {
        val document = json.as[JsObject]
        val target = (document \ "target").as[JsObject]
        val generator = (document \ "generator").as[JsObject]
        val source = (document \ "source").as[JsObject]
        val output = (document \ "output").as[JsObject]
        val inputs = Inputs(
          (target \ "cncfVersion").as[String],
          (generator \ "cozyVersion").as[String],
          (generator \ "simpleModelerBackendVersion").as[String],
          (generator \ "simpleModelingModelVersion").as[String],
          (source \ "identity").as[String]
        )
        val artifacts = (output \ "artifacts").as[Vector[JsObject]].map { artifact =>
          Artifact((artifact \ "path").as[String])
        }
        _normalize_inputs(inputs).flatMap { normalized =>
          val diagnostics = _input_diagnostics(inputs, normalized) ++
            _artifact_diagnostics(artifacts, path.toString)
          if (diagnostics.isEmpty) Right(Manifest(normalized, artifacts)) else Left(diagnostics)
        }
      } catch {
        case NonFatal(exception) =>
          Left(Vector(_diagnostic(
            DiagnosticCode.ProvenanceMalformed,
            path.toString,
            "all required provenance fields with their declared types",
            Option(exception.getMessage).getOrElse(exception.getClass.getName),
            "Generation provenance is missing or has malformed fields.",
            "Restore or regenerate the provenance document."
          )))
      }
  }

  private[modeler] def _artifact_diagnostics(
    artifacts: Vector[Artifact],
    source: String
  ): Vector[Diagnostic] = {
    val invalid = artifacts.filter { artifact =>
      stableSourceIdentity(artifact.path) != Right(artifact.path) ||
        artifact.path.contains('\\')
    }
    val duplicates = artifacts.groupBy(_.path).collect {
      case (path, values) if values.size > 1 => path
    }.toVector.sorted
    val emptydiagnostics =
      if (artifacts.nonEmpty) Vector.empty
      else Vector(_diagnostic(
        DiagnosticCode.OutputTampered, source,
        "at least one generated Scala artifact", "none",
        "Generation provenance describes no generated output.",
        "Generate the required Scala output before writing metadata."
      ))
    val orderdiagnostics =
      if (artifacts.map(_.path) == artifacts.map(_.path).sorted) Vector.empty
      else Vector(_diagnostic(
        DiagnosticCode.ProvenanceMalformed, source,
        "artifact paths sorted by canonical relative path", artifacts.map(_.path).mkString(","),
        "Generation provenance artifact paths are not deterministic.",
        "Regenerate metadata from the selected output inventory."
      ))
    emptydiagnostics ++ invalid.map { artifact =>
      _diagnostic(
        DiagnosticCode.OutputTampered, source,
        "a safe canonical project-output-relative artifact path", artifact.path,
        "Generation provenance contains an invalid artifact path.",
        "Regenerate metadata with canonical relative artifact paths."
      )
    } ++ duplicates.map { path =>
      _diagnostic(
        DiagnosticCode.OutputTampered, source,
        "unique generated artifact paths", path,
        "Generation provenance repeats an artifact path.",
        "Regenerate metadata from the selected output inventory."
      )
    } ++ orderdiagnostics
  }

  private def _input_diagnostics(
    actual: Inputs,
    expected: Inputs
  ): Vector[Diagnostic] = {
    val fields = Vector(
      "target.cncfVersion" -> (expected.cncfTargetVersion -> actual.cncfTargetVersion),
      "generator.cozyVersion" ->
        (expected.cozyGeneratorVersion -> actual.cozyGeneratorVersion),
      "generator.simpleModelerBackendVersion" ->
        (expected.simpleModelerBackendVersion -> actual.simpleModelerBackendVersion),
      "generator.simpleModelingModelVersion" ->
        (expected.simpleModelingModelVersion -> actual.simpleModelingModelVersion),
      "source.identity" -> (expected.sourceIdentity -> actual.sourceIdentity)
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

  private[modeler] def _source_readable(
    source: Path,
    sourceidentity: String
  ): Either[Vector[Diagnostic], Unit] =
    try {
      if (Files.isRegularFile(source) && Files.isReadable(source)) {
        val stream = Files.newInputStream(source)
        stream.close()
        Right(())
      } else
        Left(Vector(_diagnostic(
          DiagnosticCode.SourceTampered, sourceidentity,
          "a readable regular CML source", "missing or unreadable",
          "The selected CML source is unavailable.",
          "Restore the selected source and its read access."
        )))
    } catch {
      case NonFatal(exception) =>
        Left(Vector(_diagnostic(
          DiagnosticCode.SourceTampered, sourceidentity,
          "a readable regular CML source",
          Option(exception.getMessage).getOrElse(exception.getClass.getName),
          "The selected CML source cannot be accessed.",
          "Restore source readability before retrying."
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
              if (!Files.isReadable(path))
                throw new java.io.IOException(s"Generated Scala file is unreadable: $relative")
              val input = Files.newInputStream(path)
              input.close()
              Artifact(relative)
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

  private def _payload_json(
    inputs: Inputs,
    artifacts: Vector[Artifact]
  ): JsObject =
    Json.obj(
      "schemaVersion" -> SCHEMA_VERSION,
      "target" -> Json.obj(
        "cncfVersion" -> inputs.cncfTargetVersion
      ),
      "generator" -> Json.obj(
        "cozyVersion" -> inputs.cozyGeneratorVersion,
        "simpleModelerBackendVersion" -> inputs.simpleModelerBackendVersion,
        "simpleModelingModelVersion" -> inputs.simpleModelingModelVersion
      ),
      "source" -> Json.obj(
        "identity" -> inputs.sourceIdentity
      ),
      "output" -> Json.obj(
        "artifacts" -> JsArray(artifacts.map(_.toJson))
      )
    )

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
