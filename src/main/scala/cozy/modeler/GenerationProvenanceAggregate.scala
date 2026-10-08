package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.goldenport.RAISE
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import GenerationProvenance.{Artifact, Diagnostic, DiagnosticCode, Inputs}

/*
 * @since   Sep. 16, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object GenerationProvenanceAggregate {
  final case class DelegatedInput(
    provenancePath: Path,
    outputRoot: Path
  )

  final case class AggregateInputs(
    cncfTargetVersion: String,
    cozyGeneratorVersion: String,
    simpleModelerBackendVersion: String,
    simpleModelingModelVersion: String
  )

  final case class AggregateSource(
    identity: String,
    artifacts: Vector[Artifact]
  ) {
    def toJson: JsObject =
      Json.obj(
        "identity" -> identity,
        "output" -> Json.obj("artifacts" -> JsArray(artifacts.map(_.toJson)))
      )
  }

  final case class AggregateManifest(
    inputs: AggregateInputs,
    sources: Vector[AggregateSource],
    artifacts: Vector[Artifact]
  ) extends GenerationProvenance.ValidatedManifest {
    val schemaVersion = GenerationProvenance.AGGREGATE_SCHEMA_VERSION

    def toJson: JsObject =
      _aggregate_payload_json(inputs, sources, artifacts)

    def toJsonString: String =
      Json.prettyPrint(toJson) + "\n"
  }

  def readIfAggregate(
    provenancePath: Path
  ): Either[Vector[Diagnostic], Option[AggregateManifest]] = {
    val provenance = provenancePath.toAbsolutePath.normalize()
    if (!Files.isRegularFile(provenance) || !Files.isReadable(provenance))
      Right(None)
    else
      try {
        val json = Json.parse(Files.readString(provenance, StandardCharsets.UTF_8))
        (json \ "schemaVersion").asOpt[String] match {
          case Some(GenerationProvenance.AGGREGATE_SCHEMA_VERSION) =>
            _parse_aggregate_manifest(json, provenance).map(Some(_))
          case _ =>
            Right(None)
        }
      } catch {
        case NonFatal(exception) =>
          Left(Vector(_diagnostic(
            DiagnosticCode.ProvenanceMalformed, provenance.toString,
            "a readable JSON provenance object",
            Option(exception.getMessage).getOrElse(exception.getClass.getName),
            "Generation provenance cannot be loaded.",
            "Restore or regenerate the selected metadata."
          )))
      }
  }

  def validateForPackaging(
    manifest: AggregateManifest,
    projectRoot: Path,
    expectedCncfTargetVersion: Option[String],
    expectedCozyGeneratorVersion: Option[String],
    provenancePath: Path
  ): AggregateManifest =
    _validate_aggregate(
      manifest,
      projectRoot.toAbsolutePath.normalize(),
      expectedCncfTargetVersion,
      expectedCozyGeneratorVersion,
      provenancePath.toAbsolutePath.normalize(),
      Vector.empty
    ).fold(_raise, identity)

  def validatePackagedMetadata(
    provenancePath: Path,
    manifest: AggregateManifest,
    expectedCncfTargetVersion: String,
    expectedCozyGeneratorVersion: String
  ): AggregateManifest =
    _validate_packaged_aggregate(
      provenancePath.toAbsolutePath.normalize(),
      manifest,
      expectedCncfTargetVersion,
      expectedCozyGeneratorVersion
    ).fold(_raise, identity)

  def aggregateForPackaging(
    delegatedInputs: Vector[DelegatedInput],
    projectRoot: Path
  ): AggregateManifest = {
    val projectroot = projectRoot.toAbsolutePath.normalize()
    if (delegatedInputs.isEmpty)
      _raise(Vector(_diagnostic(
        DiagnosticCode.InputMismatch,
        "delegated-inputs",
        "a non-empty explicit list of delegated V1 provenance inputs",
        "empty",
        "Project provenance aggregation requires at least one explicitly selected input.",
        "Pass every delegated manifest/output-root pair selected by the owning build."
      )))
    val validated = delegatedInputs.map(_validate_delegated_input(_, projectroot))
    val inputvalues = validated.map(x => _aggregate_inputs(x.inputs)).distinct.
      sortBy(_aggregate_inputs_identity)
    if (inputvalues.size != 1)
      _raise(Vector(_diagnostic(
        DiagnosticCode.InputMismatch,
        "delegated-inputs",
        "one exact target and generator identity",
        inputvalues.map(_aggregate_inputs_identity).mkString(","),
        "Delegated generation provenance disagrees about the target or generator identity.",
        "Regenerate every selected source with one consistent owning-build contract."
      )))
    val sources = validated.map(_aggregate_source).sortBy(_.identity)
    val duplicates = sources.groupBy(_.identity).collect {
      case (identity, values) if values.size > 1 => identity
    }.toVector.sorted
    if (duplicates.nonEmpty)
      _raise(Vector(_diagnostic(
        DiagnosticCode.SourceAmbiguous,
        "delegated-inputs",
        "one unique canonical project-relative source identity per delegated input",
        duplicates.mkString(","),
        "Delegated generation provenance contains duplicate or ambiguous source identities.",
        "Select one validated delegated input for each canonical CML source identity."
      )))
    val artifacts = _union_artifacts(sources).fold(_raise, identity)
    val inputs = inputvalues.head
    val manifest = AggregateManifest(inputs, sources, artifacts)
    _write_aggregate(
      projectroot,
      manifest,
      delegatedInputs.map(_.outputRoot.toAbsolutePath.normalize())
    )
  }

  private def _validate_delegated_input(
    delegatedinput: DelegatedInput,
    projectroot: Path
  ): GenerationProvenance.Manifest = {
    val provenance = delegatedinput.provenancePath.toAbsolutePath.normalize()
    val outputroot = delegatedinput.outputRoot.toAbsolutePath.normalize()
    if (!Files.isRegularFile(provenance) || !Files.isReadable(provenance)) {
      val actual = if (Files.isRegularFile(provenance)) "unreadable" else "missing"
      _raise(Vector(_diagnostic(
        DiagnosticCode.ProvenanceMissing,
        provenance.toString,
        "a readable delegated V1 generation provenance document",
        actual,
        "Delegated generation provenance does not exist or cannot be read.",
        "Restore or regenerate the selected delegated source before project aggregation."
      )))
    }
    if (!Files.isDirectory(outputroot) || !Files.isReadable(outputroot))
      _raise(Vector(_diagnostic(
        DiagnosticCode.ProvenanceMissing,
        outputroot.toString,
        "a readable delegated generation output root",
        "missing",
        "Delegated generation output does not exist.",
        "Restore or regenerate the selected delegated output before project aggregation."
      )))
    val parsed = GenerationProvenance._read_v1_for_aggregate(provenance).fold(_raise, identity)
    val source = projectroot.resolve(parsed.inputs.sourceIdentity).normalize()
    GenerationProvenance.validate(provenance, outputroot, source, parsed.inputs).
      fold(_raise, identity)
  }

  private def _aggregate_inputs(inputs: Inputs): AggregateInputs =
    AggregateInputs(
      inputs.cncfTargetVersion,
      inputs.cozyGeneratorVersion,
      inputs.simpleModelerBackendVersion,
      inputs.simpleModelingModelVersion
    )

  private def _aggregate_inputs_identity(inputs: AggregateInputs): String =
    Vector(
      inputs.cncfTargetVersion,
      inputs.cozyGeneratorVersion,
      inputs.simpleModelerBackendVersion,
      inputs.simpleModelingModelVersion
    ).mkString("\u0000")

  private def _aggregate_source(manifest: GenerationProvenance.Manifest): AggregateSource =
    AggregateSource(manifest.inputs.sourceIdentity, manifest.artifacts)

  private def _union_artifacts(
    sources: Vector[AggregateSource]
  ): Either[Vector[Diagnostic], Vector[Artifact]] =
    Right(sources.flatMap(_.artifacts).distinct.sortBy(_.path))

  private def _write_aggregate(
    projectroot: Path,
    manifest: AggregateManifest,
    excludedoutputroots: Vector[Path]
  ): AggregateManifest = {
    _validate_aggregate(
      manifest,
      projectroot,
      Some(manifest.inputs.cncfTargetVersion),
      Some(manifest.inputs.cozyGeneratorVersion),
      projectroot.resolve(GenerationProvenance.METADATA_PATH),
      excludedoutputroots
    ).fold(_raise, identity)
    val path = projectroot.resolve(GenerationProvenance.METADATA_PATH)
    val directory = path.getParent
    Files.createDirectories(directory)
    val temporary = Files.createTempFile(directory, ".generation-provenance-", ".json")
    try {
      Files.writeString(temporary, manifest.toJsonString, StandardCharsets.UTF_8)
      val serialized = _read_aggregate(temporary).fold(_raise, identity)
      _validate_aggregate(
        serialized,
        projectroot,
        Some(manifest.inputs.cncfTargetVersion),
        Some(manifest.inputs.cozyGeneratorVersion),
        temporary,
        excludedoutputroots
      ).fold(_raise, identity)
      Files.move(
        temporary,
        path,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
      )
      serialized
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  private def _validate_aggregate(
    manifest: AggregateManifest,
    projectroot: Path,
    expectedcncftargetversion: Option[String],
    expectedcozygeneratorversion: Option[String],
    provenancepath: Path,
    excludedoutputroots: Vector[Path]
  ): Either[Vector[Diagnostic], AggregateManifest] =
    _validate_aggregate_metadata(
      manifest,
      expectedcncftargetversion,
      expectedcozygeneratorversion,
      provenancepath
    ).flatMap { normalized =>
      val sourcediagnostics = normalized.sources.flatMap { source =>
        GenerationProvenance._source_readable(
          projectroot.resolve(source.identity).normalize(),
          source.identity
        ).left.toOption.getOrElse(Vector.empty)
      }
      val outputdiagnostics = _scala_artifacts(projectroot, normalized.artifacts, excludedoutputroots).fold(identity, { artifacts =>
        if (normalized.artifacts == artifacts)
          Vector.empty
        else
          Vector(_diagnostic(
            DiagnosticCode.OutputTampered,
            projectroot.toString,
            _artifact_identity(normalized.artifacts),
            _artifact_identity(artifacts),
            "Installed Scala path inventory does not match aggregate generation provenance.",
            "Restore the required paths or regenerate every selected source."
          ))
      })
      val diagnostics = sourcediagnostics ++ outputdiagnostics
      if (diagnostics.isEmpty)
        Right(normalized)
      else
        Left(diagnostics)
    }

  private def _validate_aggregate_metadata(
    manifest: AggregateManifest,
    expectedcncftargetversion: Option[String],
    expectedcozygeneratorversion: Option[String],
    provenancepath: Path
  ): Either[Vector[Diagnostic], AggregateManifest] =
    _normalize_aggregate_inputs(manifest.inputs).flatMap { normalizedinputs =>
      _normalize_aggregate_inputs(normalizedinputs.copy(
        cncfTargetVersion = expectedcncftargetversion.getOrElse(normalizedinputs.cncfTargetVersion),
        cozyGeneratorVersion = expectedcozygeneratorversion.getOrElse(normalizedinputs.cozyGeneratorVersion)
      )).flatMap { expectedinputs =>
        val normalizedinputdiagnostics = _aggregate_input_diagnostics(
          manifest.inputs,
          normalizedinputs
        )
        val expecteddiagnostics = _aggregate_input_diagnostics(normalizedinputs, expectedinputs)
        val identities = manifest.sources.map { source =>
          source -> GenerationProvenance.stableSourceIdentity(source.identity)
      }
      val identitydiagnostics = identities.flatMap { case (source, normalized) =>
        normalized match {
          case Left(diagnostics) => diagnostics
          case Right(identity) if identity == source.identity => Vector.empty
          case Right(identity) =>
            Vector(_diagnostic(
              DiagnosticCode.InputMismatch,
              "sources.identity",
              identity,
              source.identity,
              "Aggregate generation provenance source identity is not canonical.",
              "Regenerate aggregate provenance from canonical project-relative identities."
            ))
        }
      }
      val canonicalidentities = identities.collect {
        case (_, Right(identity)) => identity
      }
      val duplicatedidentities = canonicalidentities.groupBy(identity).collect {
        case (identity, values) if values.size > 1 => identity
      }.toVector.sorted
      val ambiguitydiagnostics =
        if (duplicatedidentities.isEmpty)
          Vector.empty
        else
          Vector(_diagnostic(
            DiagnosticCode.SourceAmbiguous,
            "sources",
            "one unique canonical project-relative source identity per source entry",
            duplicatedidentities.mkString(","),
            "Aggregate generation provenance contains duplicate or ambiguous source identities.",
            "Regenerate from one validated delegated input for each CML source."
          ))
      val orderdiagnostics =
        if (canonicalidentities == canonicalidentities.sorted)
          Vector.empty
        else
          Vector(_diagnostic(
            DiagnosticCode.ProvenanceMalformed,
            "sources",
            "sources sorted by canonical project-relative identity",
            canonicalidentities.mkString(","),
            "Aggregate generation provenance sources are not deterministic.",
            "Regenerate aggregate provenance from the explicit selected inputs."
          ))
      val emptysourcediagnostics =
        if (manifest.sources.nonEmpty)
          Vector.empty
        else
          Vector(_diagnostic(
            DiagnosticCode.InputMismatch,
            "sources",
            "a non-empty explicit set of aggregate source evidence",
            "empty",
            "Aggregate generation provenance must retain every selected source.",
            "Regenerate from the explicit delegated source set."
          ))
      val sourcediagnostics = manifest.sources.flatMap { source =>
        _aggregate_source_diagnostics(source, provenancepath)
      }
      val unionresult = _union_artifacts(manifest.sources)
      val uniondiagnostics = unionresult.left.toOption.getOrElse(Vector.empty)
      val union = unionresult.toOption.getOrElse(Vector.empty)
      val artifactdiagnostics = _aggregate_artifact_diagnostics(
        manifest.artifacts,
        provenancepath.toString,
        "aggregate output"
      )
      val unionoutputdiagnostics =
        if (unionresult.isRight && manifest.artifacts == union)
          Vector.empty
        else if (unionresult.isRight)
          Vector(_diagnostic(
            DiagnosticCode.OutputTampered,
            provenancepath.toString,
            _artifact_identity(union),
            _artifact_identity(manifest.artifacts),
            "Aggregate output claims do not equal the union of source output claims.",
            "Regenerate aggregate provenance from the validated delegated evidence."
          ))
        else
          Vector.empty
      val diagnostics =
        normalizedinputdiagnostics ++
          expecteddiagnostics ++
          identitydiagnostics ++
          ambiguitydiagnostics ++
          orderdiagnostics ++
          emptysourcediagnostics ++
          sourcediagnostics ++
          uniondiagnostics ++
          artifactdiagnostics ++
          unionoutputdiagnostics
      if (diagnostics.isEmpty)
        Right(manifest.copy(inputs = normalizedinputs))
      else
        Left(diagnostics)
      }
    }

  private def _validate_packaged_aggregate(
    provenancepath: Path,
    manifest: AggregateManifest,
    expectedcncftargetversion: String,
    expectedcozygeneratorversion: String
  ): Either[Vector[Diagnostic], AggregateManifest] =
    _validate_aggregate_metadata(
      manifest,
      Some(expectedcncftargetversion),
      Some(expectedcozygeneratorversion),
      provenancepath
    )

  private def _normalize_aggregate_inputs(
    inputs: AggregateInputs
  ): Either[Vector[Diagnostic], AggregateInputs] = {
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
    if (diagnostics.nonEmpty)
      Left(diagnostics)
    else
      Right(AggregateInputs(
        values(0)._2,
        values(1)._2,
        values(2)._2,
        values(3)._2
      ))
  }

  private def _aggregate_input_diagnostics(
    actual: AggregateInputs,
    expected: AggregateInputs
  ): Vector[Diagnostic] = {
    val fields = Vector(
      "target.cncfVersion" -> (expected.cncfTargetVersion -> actual.cncfTargetVersion),
      "generator.cozyVersion" -> (expected.cozyGeneratorVersion -> actual.cozyGeneratorVersion),
      "generator.simpleModelerBackendVersion" ->
        (expected.simpleModelerBackendVersion -> actual.simpleModelerBackendVersion),
      "generator.simpleModelingModelVersion" ->
        (expected.simpleModelingModelVersion -> actual.simpleModelingModelVersion)
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
          s"Aggregate generation provenance contradicts the selected $name.",
          "Regenerate with one consistent owning-build input contract."
        ))
    }
  }

  private def _aggregate_source_diagnostics(
    source: AggregateSource,
    provenancepath: Path
  ): Vector[Diagnostic] =
    _aggregate_artifact_diagnostics(
      source.artifacts,
      s"${provenancepath.toString}:sources:${source.identity}",
      s"source ${source.identity} output"
    )

  private def _aggregate_artifact_diagnostics(
    artifacts: Vector[Artifact],
    source: String,
    description: String
  ): Vector[Diagnostic] =
    GenerationProvenance._artifact_diagnostics(artifacts, s"$source:$description")

  private def _read_aggregate(
    path: Path
  ): Either[Vector[Diagnostic], AggregateManifest] = {
    val provenance = path.toAbsolutePath.normalize()
    if (!Files.isRegularFile(provenance) || !Files.isReadable(provenance))
      Left(Vector(_diagnostic(
        DiagnosticCode.ProvenanceMissing,
        provenance.toString,
        "a readable V2 generation provenance document",
        "missing",
        "Aggregate generation provenance does not exist.",
        "Regenerate the aggregate output with the selected Cozy generator."
      )))
    else
      try {
        val json = Json.parse(Files.readString(provenance, StandardCharsets.UTF_8))
        _parse_aggregate_manifest(json, provenance)
      } catch {
        case NonFatal(exception) =>
          Left(Vector(_diagnostic(
            DiagnosticCode.ProvenanceMalformed,
            provenance.toString,
            GenerationProvenance.AGGREGATE_SCHEMA_VERSION,
            Option(exception.getMessage).getOrElse(exception.getClass.getName),
            "Generation provenance cannot be parsed.",
            "Restore or regenerate the aggregate provenance document."
          )))
      }
  }

  private def _parse_aggregate_manifest(
    json: JsValue,
    path: Path
  ): Either[Vector[Diagnostic], AggregateManifest] = {
    val schema = (json \ "schemaVersion").asOpt[String]
    if (schema != Some(GenerationProvenance.AGGREGATE_SCHEMA_VERSION))
      Left(Vector(_diagnostic(
        DiagnosticCode.SchemaMismatch,
        path.toString,
        GenerationProvenance.AGGREGATE_SCHEMA_VERSION,
        schema.getOrElse("missing"),
        "Generation provenance schema is unsupported.",
        "Regenerate provenance with the selected Cozy generator."
      )))
    else
      try {
        val document = json.as[JsObject]
        val target = (document \ "target").as[JsObject]
        val generator = (document \ "generator").as[JsObject]
        val inputs = AggregateInputs(
          (target \ "cncfVersion").as[String],
          (generator \ "cozyVersion").as[String],
          (generator \ "simpleModelerBackendVersion").as[String],
          (generator \ "simpleModelingModelVersion").as[String]
        )
        val sources = (document \ "sources").as[Vector[JsObject]].map { source =>
          val output = (source \ "output").as[JsObject]
          AggregateSource(
            (source \ "identity").as[String],
            (output \ "artifacts").as[Vector[JsObject]].map { artifact =>
              Artifact((artifact \ "path").as[String])
            }
          )
        }
        val output = (document \ "output").as[JsObject]
        val manifest = AggregateManifest(
          inputs,
          sources,
          (output \ "artifacts").as[Vector[JsObject]].map { artifact =>
            Artifact((artifact \ "path").as[String])
          }
        )
        _validate_aggregate_metadata(manifest, None, None, path)
      } catch {
        case NonFatal(exception) =>
          Left(Vector(_diagnostic(
            DiagnosticCode.ProvenanceMalformed, path.toString,
            "all required aggregate fields with their declared types",
            Option(exception.getMessage).getOrElse(exception.getClass.getName),
            "Aggregate generation provenance is missing or has malformed fields.",
            "Restore or regenerate the selected metadata."
          )))
      }
  }

  private def _scala_artifacts(
    root: Path,
    declared: Vector[Artifact],
    excludedroots: Vector[Path]
  ): Either[Vector[Diagnostic], Vector[Artifact]] = {
    val target = root.resolve("target")
    val excluded = excludedroots.map(_.toAbsolutePath.normalize())
    if (!Files.isDirectory(target))
      Right(Vector.empty)
    else {
      try {
        // Aggregate provenance owns the selected delegated artifact union, not
        // every Scala file produced by tests or other tasks under target.
          Right(declared.iterator.map(artifact => root.resolve(artifact.path).normalize()).
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

  private def _artifact_identity(artifacts: Vector[Artifact]): String =
    artifacts.map(_.path).mkString(",")

  private def _aggregate_payload_json(
    inputs: AggregateInputs,
    sources: Vector[AggregateSource],
    artifacts: Vector[Artifact]
  ): JsObject =
    Json.obj(
      "schemaVersion" -> GenerationProvenance.AGGREGATE_SCHEMA_VERSION,
      "target" -> Json.obj(
        "cncfVersion" -> inputs.cncfTargetVersion
      ),
      "generator" -> Json.obj(
        "cozyVersion" -> inputs.cozyGeneratorVersion,
        "simpleModelerBackendVersion" -> inputs.simpleModelerBackendVersion,
        "simpleModelingModelVersion" -> inputs.simpleModelingModelVersion
      ),
      "sources" -> JsArray(sources.map(_.toJson)),
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
