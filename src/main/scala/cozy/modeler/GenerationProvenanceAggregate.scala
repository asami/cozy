package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.goldenport.RAISE
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import GenerationProvenance.{Artifact, Diagnostic, DiagnosticCode, Inputs}

/*
 * @since   Sep. 16, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object GenerationProvenanceAggregate {
  final case class DelegatedInput(
    provenancePath: Path,
    outputRoot: Path
  )

  final case class AggregateInputs(
    cncfTargetVersion: String,
    runtimeDescriptorSha256: String,
    cozyGeneratorVersion: String,
    simpleModelerBackendVersion: String,
    simpleModelingModelVersion: String
  )

  final case class AggregateSource(
    identity: String,
    sha256: String,
    artifacts: Vector[Artifact],
    generatedOutputDigest: String,
    evidenceDigest: String
  ) {
    def toJson: JsObject =
      Json.obj(
        "identity" -> identity,
        "sha256" -> sha256,
        "output" -> Json.obj(
          "artifacts" -> JsArray(artifacts.map(_.toJson)),
          "digest" -> generatedOutputDigest
        ),
        "evidenceDigest" -> evidenceDigest
      )
  }

  final case class AggregateManifest(
    inputs: AggregateInputs,
    sources: Vector[AggregateSource],
    artifacts: Vector[Artifact],
    generatedOutputDigest: String,
    evidenceDigest: String
  ) extends GenerationProvenance.ValidatedManifest {
    val schemaVersion = GenerationProvenance.AGGREGATE_SCHEMA_VERSION

    def toJson: JsObject =
      _aggregate_payload_json(inputs, sources, artifacts, generatedOutputDigest) ++
        Json.obj("evidenceDigest" -> evidenceDigest)

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
        case NonFatal(_) => Right(None)
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

  def validatePackagedEvidence(
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
    val outputdigest = _artifact_digest(artifacts)
    val manifest = AggregateManifest(
      inputs,
      sources,
      artifacts,
      outputdigest,
      _aggregate_evidence_digest(inputs, sources, artifacts, outputdigest)
    )
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
      inputs.runtimeDescriptorSha256,
      inputs.cozyGeneratorVersion,
      inputs.simpleModelerBackendVersion,
      inputs.simpleModelingModelVersion
    )

  private def _aggregate_inputs_identity(inputs: AggregateInputs): String =
    Vector(
      inputs.cncfTargetVersion,
      inputs.runtimeDescriptorSha256,
      inputs.cozyGeneratorVersion,
      inputs.simpleModelerBackendVersion,
      inputs.simpleModelingModelVersion
    ).mkString("\u0000")

  private def _aggregate_source(manifest: GenerationProvenance.Manifest): AggregateSource =
    AggregateSource(
      manifest.inputs.sourceIdentity,
      manifest.inputs.sourceSha256,
      manifest.artifacts,
      manifest.generatedOutputDigest,
      manifest.evidenceDigest
    )

  private def _union_artifacts(
    sources: Vector[AggregateSource]
  ): Either[Vector[Diagnostic], Vector[Artifact]] = {
    val claims = sources.flatMap(_.artifacts).groupBy(_.path)
    val conflicts = claims.toVector.flatMap { case (path, artifacts) =>
      val digests = artifacts.map(_.sha256).distinct.sorted
      if (digests.size > 1)
        Vector(path -> digests)
      else
        Vector.empty
    }.sortBy(_._1)
    if (conflicts.nonEmpty)
      Left(conflicts.map { case (path, digests) =>
        _diagnostic(
          DiagnosticCode.OutputConflict,
          path,
          "one shared generated-artifact digest",
          digests.mkString(","),
          "Distinct delegated sources claim different generated bytes for one artifact path.",
          "Regenerate the project so every shared generated artifact has one exact digest."
        )
      })
    else
      Right(claims.toVector.sortBy(_._1).map { case (path, artifacts) =>
        Artifact(path, artifacts.head.sha256)
      })
  }

  private def _write_aggregate(
    projectroot: Path,
    manifest: AggregateManifest,
    excludedoutputroots: Vector[Path]
  ): AggregateManifest = {
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
    _validate_aggregate_snapshot(
      manifest,
      expectedcncftargetversion,
      expectedcozygeneratorversion,
      provenancepath
    ).flatMap { normalized =>
      val sourcevalidations = normalized.sources.map { source =>
        _source_sha256(
          projectroot.resolve(source.identity).normalize(),
          source.identity
        ).flatMap { actual =>
          if (actual == source.sha256)
            Right(Vector.empty[Diagnostic])
          else
            Left(Vector(_diagnostic(
              DiagnosticCode.SourceTampered,
              source.identity,
              source.sha256,
              actual,
              "The CML source digest no longer matches aggregate generation provenance.",
              "Restore the source or regenerate the complete project output."
            )))
        }
      }
      val sourcediagnostics = sourcevalidations.foldLeft(Vector.empty[Diagnostic]) { (z, x) =>
        z ++ x.fold(identity, identity)
      }
      val artifactresult = _scala_artifacts(projectroot, excludedoutputroots)
      val outputdiagnostics = artifactresult.fold(identity, { artifacts =>
        val outputdigest = _artifact_digest(artifacts)
        if (
          normalized.artifacts == artifacts &&
          normalized.generatedOutputDigest == outputdigest
        )
          Vector.empty
        else
          Vector(_diagnostic(
            DiagnosticCode.OutputTampered,
            projectroot.toString,
            s"${normalized.generatedOutputDigest}:${_artifact_identity(normalized.artifacts)}",
            s"$outputdigest:${_artifact_identity(artifacts)}",
            "Generated Scala output no longer matches aggregate generation provenance.",
            "Restore the generated output or regenerate every selected source."
          ))
      })
      val diagnostics = sourcediagnostics ++ outputdiagnostics
      if (diagnostics.isEmpty)
        Right(normalized)
      else
        Left(diagnostics)
    }

  private def _validate_aggregate_snapshot(
    manifest: AggregateManifest,
    expectedcncftargetversion: Option[String],
    expectedcozygeneratorversion: Option[String],
    provenancepath: Path
  ): Either[Vector[Diagnostic], AggregateManifest] =
    _normalize_aggregate_inputs(manifest.inputs).flatMap { normalizedinputs =>
      val normalizedinputdiagnostics = _aggregate_input_diagnostics(
        manifest.inputs,
        normalizedinputs
      )
      val expecteddiagnostics =
        expectedcncftargetversion.toVector.flatMap { expected =>
          if (normalizedinputs.cncfTargetVersion == expected)
            Vector.empty
          else
            Vector(_diagnostic(
              DiagnosticCode.InputMismatch,
              "target.cncfVersion",
              expected,
              normalizedinputs.cncfTargetVersion,
              "Aggregate generation provenance contradicts the selected CNCF target.",
              "Regenerate every source with the accepted project target."
            ))
        } ++
          expectedcozygeneratorversion.toVector.flatMap { expected =>
            if (normalizedinputs.cozyGeneratorVersion == expected)
              Vector.empty
            else
              Vector(_diagnostic(
                DiagnosticCode.InputMismatch,
                "generator.cozyVersion",
                expected,
                normalizedinputs.cozyGeneratorVersion,
                "Aggregate generation provenance contradicts the selected Cozy generator.",
                "Regenerate every source with the accepted Cozy generator."
              ))
          }
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
        _aggregate_source_diagnostics(normalizedinputs, source, provenancepath)
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
      val outputdigest = _artifact_digest(manifest.artifacts)
      val outputdigestdiagnostics =
        if (manifest.generatedOutputDigest == outputdigest)
          Vector.empty
        else
          Vector(_diagnostic(
            DiagnosticCode.OutputTampered,
            provenancepath.toString,
            manifest.generatedOutputDigest,
            outputdigest,
            "Aggregate generation provenance has a contradictory output digest.",
            "Regenerate the aggregate evidence from validated generated output."
          ))
      val evidencedigest = _aggregate_evidence_digest(
        normalizedinputs,
        manifest.sources,
        manifest.artifacts,
        manifest.generatedOutputDigest
      )
      val evidencediagnostics =
        if (manifest.evidenceDigest == evidencedigest)
          Vector.empty
        else
          Vector(_diagnostic(
            DiagnosticCode.EvidenceTampered,
            provenancepath.toString,
            manifest.evidenceDigest,
            evidencedigest,
            "Aggregate generation provenance fields contradict their evidence digest.",
            "Regenerate aggregate provenance from trusted delegated evidence."
          ))
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
          unionoutputdiagnostics ++
          outputdigestdiagnostics ++
          evidencediagnostics
      if (diagnostics.isEmpty)
        Right(manifest.copy(inputs = normalizedinputs))
      else
        Left(diagnostics)
    }

  private def _validate_packaged_aggregate(
    provenancepath: Path,
    manifest: AggregateManifest,
    expectedcncftargetversion: String,
    expectedcozygeneratorversion: String
  ): Either[Vector[Diagnostic], AggregateManifest] =
    _validate_aggregate_snapshot(
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
      "runtimeDescriptorSha256" -> inputs.runtimeDescriptorSha256,
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
      else if (name == "runtimeDescriptorSha256" && !value.matches("[0-9a-f]{64}"))
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
    if (diagnostics.nonEmpty)
      Left(diagnostics)
    else
      Right(AggregateInputs(
        values(0)._2,
        values(1)._2,
        values(2)._2,
        values(3)._2,
        values(4)._2
      ))
  }

  private def _aggregate_input_diagnostics(
    actual: AggregateInputs,
    expected: AggregateInputs
  ): Vector[Diagnostic] = {
    val fields = Vector(
      "target.cncfVersion" -> (expected.cncfTargetVersion -> actual.cncfTargetVersion),
      "target.runtimeDescriptorSha256" ->
        (expected.runtimeDescriptorSha256 -> actual.runtimeDescriptorSha256),
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
    inputs: AggregateInputs,
    source: AggregateSource,
    provenancepath: Path
  ): Vector[Diagnostic] = {
    val artifactdiagnostics = _aggregate_artifact_diagnostics(
      source.artifacts,
      s"${provenancepath.toString}:sources:${source.identity}",
      s"source ${source.identity} output"
    )
    val outputdigest = _artifact_digest(source.artifacts)
    val outputdiagnostics =
      if (source.generatedOutputDigest == outputdigest)
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.OutputTampered,
          source.identity,
          source.generatedOutputDigest,
          outputdigest,
          "Delegated source output claims contradict their output digest.",
          "Regenerate the delegated source before project aggregation."
        ))
    val sourcedigestdiagnostics =
      if (source.sha256.matches("[0-9a-f]{64}"))
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.ProvenanceMalformed,
          source.identity,
          "64 lowercase hexadecimal SHA-256 characters",
          source.sha256,
          "Aggregate generation provenance source digest is malformed.",
          "Regenerate aggregate provenance from validated delegated evidence."
        ))
    val evidenceexpected = _evidence_digest(
      Inputs(
        inputs.cncfTargetVersion,
        inputs.runtimeDescriptorSha256,
        inputs.cozyGeneratorVersion,
        inputs.simpleModelerBackendVersion,
        inputs.simpleModelingModelVersion,
        source.identity,
        source.sha256
      ),
      source.artifacts,
      source.generatedOutputDigest
    )
    val evidencediagnostics =
      if (source.evidenceDigest == evidenceexpected)
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.EvidenceTampered,
          source.identity,
          source.evidenceDigest,
          evidenceexpected,
          "Delegated source claims contradict their recorded V1 evidence digest.",
          "Regenerate the delegated source before project aggregation."
        ))
    artifactdiagnostics ++ outputdiagnostics ++ sourcedigestdiagnostics ++ evidencediagnostics
  }

  private def _aggregate_artifact_diagnostics(
    artifacts: Vector[Artifact],
    source: String,
    description: String
  ): Vector[Diagnostic] = {
    val invalid = artifacts.filter { artifact =>
      val path = Option(artifact.path).map(_.trim.replace('\\', '/')).getOrElse("")
      path.isEmpty ||
        path != artifact.path ||
        path.startsWith("/") ||
        path.matches("^[A-Za-z]:.*") ||
        path.split('/').contains("..") ||
        !artifact.sha256.matches("[0-9a-f]{64}")
    }
    val duplicates = artifacts.groupBy(_.path).collect {
      case (path, values) if values.size > 1 => path
    }.toVector.sorted
    val sorted = artifacts.map(_.path) == artifacts.map(_.path).sorted
    val emptydiagnostics =
      if (artifacts.nonEmpty)
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.OutputTampered,
          source,
          "at least one generated Scala artifact",
          "none",
          s"$description describes no generated output.",
          "Regenerate the affected source before project aggregation."
        ))
    val sortdiagnostics =
      if (sorted)
        Vector.empty
      else
        Vector(_diagnostic(
          DiagnosticCode.ProvenanceMalformed,
          source,
          "artifact claims sorted by project-output-relative path",
          artifacts.map(_.path).mkString(","),
          s"$description artifact claims are not deterministic.",
          "Regenerate provenance from the validated generated output."
        ))
    emptydiagnostics ++
      invalid.map { artifact =>
        _diagnostic(
          DiagnosticCode.OutputTampered,
          source,
          "safe canonical project-output-relative artifact paths with SHA-256 digests",
          s"${artifact.path}:${artifact.sha256}",
          s"$description contains an invalid artifact identity.",
          "Regenerate provenance from validated generated output."
        )
      } ++
      duplicates.map { path =>
        _diagnostic(
          DiagnosticCode.OutputTampered,
          source,
          "unique generated artifact paths",
          path,
          s"$description repeats an artifact path.",
          "Regenerate provenance from validated generated output."
        )
      } ++ sortdiagnostics
  }

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
      _validate_aggregate_manifest_shape(json, path).flatMap { _ =>
        try {
          val target = json \ "target"
          val generator = json \ "generator"
          val inputs = AggregateInputs(
            (target \ "cncfVersion").as[String],
            (target \ "runtimeDescriptorSha256").as[String],
            (generator \ "cozyVersion").as[String],
            (generator \ "simpleModelerBackendVersion").as[String],
            (generator \ "simpleModelingModelVersion").as[String]
          )
          val sources = (json \ "sources").as[Vector[JsObject]].map { source =>
            val output = source \ "output"
            AggregateSource(
              (source \ "identity").as[String],
              (source \ "sha256").as[String],
              (output \ "artifacts").as[Vector[JsObject]].map { artifact =>
                Artifact(
                  (artifact \ "path").as[String],
                  (artifact \ "sha256").as[String]
                )
              },
              (output \ "digest").as[String],
              (source \ "evidenceDigest").as[String]
            )
          }
          val output = json \ "output"
          Right(AggregateManifest(
            inputs,
            sources,
            (output \ "artifacts").as[Vector[JsObject]].map { artifact =>
              Artifact(
                (artifact \ "path").as[String],
                (artifact \ "sha256").as[String]
              )
            },
            (output \ "digest").as[String],
            (json \ "evidenceDigest").as[String]
          ))
        } catch {
          case NonFatal(exception) =>
            Left(Vector(_diagnostic(
              DiagnosticCode.ProvenanceMalformed,
              path.toString,
              "all required aggregate provenance fields",
              Option(exception.getMessage).getOrElse(exception.getClass.getName),
              "Aggregate generation provenance is missing or has malformed fields.",
              "Restore or regenerate the provenance document."
            )))
        }
      }
  }

  private def _validate_aggregate_manifest_shape(
    json: JsValue,
    path: Path
  ): Either[Vector[Diagnostic], Unit] = {
    val root = json.asOpt[JsObject]
    val diagnostics = root.toVector.flatMap { rootobject =>
      val rootdiagnostics = _unexpected_field_diagnostics(
        rootobject,
        Set("schemaVersion", "target", "generator", "sources", "output", "evidenceDigest"),
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
      val sourcediagnostics = (rootobject \ "sources").asOpt[Vector[JsObject]].toVector.flatten.
        zipWithIndex.flatMap { case (source, index) =>
          val sourcepath = s"${path.toString}:sources[$index]"
          val fields = _unexpected_field_diagnostics(
            source,
            Set("identity", "sha256", "output", "evidenceDigest"),
            sourcepath
          )
          val outputdiagnostics = (source \ "output").asOpt[JsObject].toVector.flatMap { output =>
            _unexpected_field_diagnostics(
              output,
              Set("artifacts", "digest"),
              s"$sourcepath:output"
            ) ++
              (output \ "artifacts").asOpt[Vector[JsObject]].toVector.flatten.zipWithIndex.flatMap {
                case (artifact, artifactindex) =>
                  _unexpected_field_diagnostics(
                    artifact,
                    Set("path", "sha256"),
                    s"$sourcepath:output.artifacts[$artifactindex]"
                  )
              }
          }
          fields ++ outputdiagnostics
        }
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
      rootdiagnostics ++ targetdiagnostics ++ generatordiagnostics ++ sourcediagnostics ++
        outputdiagnostics
    }
    if (diagnostics.isEmpty) Right(()) else Left(diagnostics)
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

  private def _source_sha256(
    source: Path,
    sourceidentity: String
  ): Either[Vector[Diagnostic], String] =
    try {
      if (Files.isRegularFile(source))
        Right(_sha256_bytes(Files.readAllBytes(source)))
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
    excludedroots: Vector[Path]
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
        finally stream.close()
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
      "schemaVersion" -> GenerationProvenance.SCHEMA_VERSION,
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

  private def _aggregate_evidence_digest(
    inputs: AggregateInputs,
    sources: Vector[AggregateSource],
    artifacts: Vector[Artifact],
    outputdigest: String
  ): String =
    _sha256_bytes(
      Json.stringify(_aggregate_payload_json(inputs, sources, artifacts, outputdigest)).
        getBytes(StandardCharsets.UTF_8)
    )

  private def _aggregate_payload_json(
    inputs: AggregateInputs,
    sources: Vector[AggregateSource],
    artifacts: Vector[Artifact],
    outputdigest: String
  ): JsObject =
    Json.obj(
      "schemaVersion" -> GenerationProvenance.AGGREGATE_SCHEMA_VERSION,
      "target" -> Json.obj(
        "cncfVersion" -> inputs.cncfTargetVersion,
        "runtimeDescriptorSha256" -> inputs.runtimeDescriptorSha256
      ),
      "generator" -> Json.obj(
        "cozyVersion" -> inputs.cozyGeneratorVersion,
        "simpleModelerBackendVersion" -> inputs.simpleModelerBackendVersion,
        "simpleModelingModelVersion" -> inputs.simpleModelingModelVersion
      ),
      "sources" -> JsArray(sources.map(_.toJson)),
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
