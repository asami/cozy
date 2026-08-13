package cozy.compatibility

import io.circe.{Json, parser}

/*
 * @since   Jul. 28, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */

/** The exact Maven coordinate of an artifact participating in generation. */
final case class MavenCoordinate(organization: String, artifact: String, version: String)

/** The independent exact generation inputs. */
final case class GenerationPair(cncfTarget: MavenCoordinate, cozyGenerator: MavenCoordinate)

sealed trait GenerationPairStatus
object GenerationPairStatus {
  case object Proven extends GenerationPairStatus
  case object Unproven extends GenerationPairStatus
  case object Incompatible extends GenerationPairStatus
}

final case class GenerationPairEvidence(pair: GenerationPair, status: GenerationPairStatus)

/** CAR runtime compatibility is deliberately not a generation coordinate. */
final case class CncfRuntimeCompatibility(minimum: Option[String], maximum: Option[String], excluded: Vector[String], tested: Vector[String])

sealed trait GenerationLifecycle
object GenerationLifecycle {
  case object Development extends GenerationLifecycle
  case object Release extends GenerationLifecycle
}

sealed trait GenerationAdmission
object GenerationAdmission {
  case object Supported extends GenerationAdmission
  case object Unsupported extends GenerationAdmission
  case object Incompatible extends GenerationAdmission
}

sealed trait GenerationDiagnosticCode
object GenerationDiagnosticCode {
  case object MalformedEvidence extends GenerationDiagnosticCode
  case object MissingCncfTarget extends GenerationDiagnosticCode
  case object MissingCozyGenerator extends GenerationDiagnosticCode
  case object ContradictorySource extends GenerationDiagnosticCode
  case object UnsupportedCncfTarget extends GenerationDiagnosticCode
  case object UnsupportedCozyGenerator extends GenerationDiagnosticCode
  case object UnsupportedGenerationPair extends GenerationDiagnosticCode
  case object UnprovenGenerationPair extends GenerationDiagnosticCode
  case object IncompatibleGenerationPair extends GenerationDiagnosticCode
  case object SnapshotNotAllowedForRelease extends GenerationDiagnosticCode
  case object MissingPublishedDefault extends GenerationDiagnosticCode
  case object ExecutingGeneratorMismatch extends GenerationDiagnosticCode
}

final case class GenerationDiagnostic(
  code: GenerationDiagnosticCode,
  source: Option[GenerationSource],
  expected: Option[String],
  actual: Option[String],
  coordinate: Option[String],
  message: String
)

sealed trait GenerationSource
object GenerationSource {
  case object ProjectContract extends GenerationSource
  case object OwningBuildBridge extends GenerationSource
  case object Cli extends GenerationSource
  case object PublishedDefault extends GenerationSource
}

final case class GenerationSourceValues(
  projectContract: Option[GenerationPair],
  owningBuildBridge: Option[GenerationPair],
  cli: Option[GenerationPair]
)

/** The primary input keeps the two missing-coordinate cases distinguishable. */
final case class GenerationInputs(cncfTarget: Option[MavenCoordinate], cozyGenerator: Option[MavenCoordinate])

final case class GenerationEvidenceOwner(owner: String, location: String)

/** Machine-readable evidence after schema and semantic validation. */
final case class GenerationCompatibilityEvidence(
  schema: String,
  evidenceOwner: GenerationEvidenceOwner,
  entries: Vector[GenerationPairEvidence],
  publishedDefault: Option[GenerationPair]
) {
  def pairs: Vector[GenerationPair] = entries.map(_.pair)
  def provenCompatiblePairs: Vector[GenerationPair] = entries.collect { case GenerationPairEvidence(pair, GenerationPairStatus.Proven) => pair }
}

final case class GenerationResolution(pair: Option[GenerationPair], source: Option[GenerationSource], diagnostics: Vector[GenerationDiagnostic])
final case class GenerationAdmissionDecision(result: GenerationAdmission, diagnostics: Vector[GenerationDiagnostic], evidenceOwner: GenerationEvidenceOwner)

sealed trait GenerationNoticeCode
object GenerationNoticeCode {
  case object MutableDevelopmentPairAccepted extends GenerationNoticeCode
}

final case class GenerationNotice(
  code: GenerationNoticeCode,
  source: GenerationSource,
  lifecycle: GenerationLifecycle,
  pair: GenerationPair,
  evidenceOwner: GenerationEvidenceOwner,
  message: String
)

final case class GenerationAcceptanceDecision(
  pair: Option[GenerationPair],
  source: Option[GenerationSource],
  lifecycle: GenerationLifecycle,
  result: GenerationAdmission,
  diagnostics: Vector[GenerationDiagnostic],
  notices: Vector[GenerationNotice],
  evidenceOwner: GenerationEvidenceOwner
)

object GenerationCompatibilityEvidenceLoader {
  val resourceName = "META-INF/cozy/generation-compatibility.v1.json"

  def load(resourceName: String = this.resourceName): Either[Vector[GenerationDiagnostic], GenerationCompatibilityEvidence] = {
    Option(getClass.getClassLoader.getResourceAsStream(resourceName)) match {
      case None => Left(Vector(_error("Evidence resource is missing.", Some(resourceName))))
      case Some(stream) =>
        try parse(scala.io.Source.fromInputStream(stream, "UTF-8").mkString, resourceName)
        finally stream.close()
    }
  }

  def parse(text: String, location: String): Either[Vector[GenerationDiagnostic], GenerationCompatibilityEvidence] =
    parser.parse(text) match {
      case Left(error) => Left(Vector(_error(s"Evidence JSON is malformed: ${error.getMessage}", Some(location))))
      case Right(json) => _decode(json, location)
    }

  private def _decode(json: Json, location: String): Either[Vector[GenerationDiagnostic], GenerationCompatibilityEvidence] = {
    val root = json.asObject.toVector
    val rootkeys = root.flatMap(_.keys).toSet
    val errors = Vector.newBuilder[GenerationDiagnostic]
    if (root.isEmpty || rootkeys != Set("schema", "evidenceOwner", "pairs", "publishedDefault"))
      errors += _error("Evidence schema has unexpected or missing fields.", Some(location))
    val schema = json.hcursor.get[String]("schema").toOption
    if (schema != Some(GenerationCompatibility.evidenceSchema)) errors += _error("Evidence schema version is not supported.", schema.orElse(Some("missing")))
    val ownerjson = json.hcursor.downField("evidenceOwner")
    val ownerkeys = ownerjson.focus.flatMap(_.asObject).map(_.keys.toSet)
    if (ownerkeys != Some(Set("owner", "location"))) errors += _error("Evidence owner must contain exactly owner and location.", Some("evidenceOwner"))
    val owner = for { name <- ownerjson.get[String]("owner").toOption; place <- ownerjson.get[String]("location").toOption } yield GenerationEvidenceOwner(name, place)
    if (ownerkeys == Some(Set("owner", "location")) && owner.isEmpty) errors += _error("Evidence owner and location must be strings.", Some("evidenceOwner"))
    if (owner.exists(x => x.owner.trim.isEmpty || x.location.trim.isEmpty)) errors += _error("Evidence owner and location must be non-empty.", Some(location))
    val pairjson = json.hcursor.downField("pairs").focus.flatMap(_.asArray)
    if (pairjson.isEmpty) errors += _error("Evidence pairs must be a JSON array.", Some("pairs"))
    val entries = pairjson.toVector.flatMap(_.zipWithIndex.flatMap { case (value, index) => _entry(value, index, location, errors) })
    val publisheddefault = json.hcursor.downField("publishedDefault").focus match {
      case None =>
        errors += _error("Evidence publishedDefault field is required.", Some("publishedDefault"))
        None
      case Some(value) if value.isNull =>
        None
      case Some(value) =>
        _pair(value, "publishedDefault", errors)
    }
    val duplicates = entries.groupBy(_.pair).collect { case (pair, values) if values.size > 1 => pair }
    duplicates.toVector.sortBy(_pair_name).foreach(pair => errors += _error(s"Duplicate or conflicting evidence pair: ${_pair_name(pair)}", Some(_pair_name(pair))))
    if (entries.isEmpty) errors += _error("Evidence pairs must not be empty.", Some("pairs"))
    val result = errors.result().sortBy(GenerationCompatibility._diagnostic_key)
    if (result.nonEmpty) Left(result)
    else {
      val evidence =
        GenerationCompatibilityEvidence(
          schema.get,
          owner.get,
          entries,
          publisheddefault
        )
      val semanticerrors = GenerationCompatibility.validate(evidence)
      if (semanticerrors.nonEmpty) Left(semanticerrors)
      else Right(evidence)
    }
  }

  private def _entry(json: Json, index: Int, location: String, errors: scala.collection.mutable.Builder[GenerationDiagnostic, Vector[GenerationDiagnostic]]): Option[GenerationPairEvidence] = {
    val keys = json.asObject.map(_.keys.toSet)
    if (keys != Some(Set("cncfTarget", "cozyGenerator", "status"))) { errors += _error(s"Pair entry $index has an unexpected schema.", Some(s"pairs[$index]")); None }
    else {
      val cncf = _coordinate(json.hcursor.downField("cncfTarget"), s"pairs[$index].cncfTarget", errors)
      val cozy = _coordinate(json.hcursor.downField("cozyGenerator"), s"pairs[$index].cozyGenerator", errors)
      val statusvalue = json.hcursor.get[String]("status").toOption
      if (statusvalue.isEmpty) errors += _error("Pair status must be a string.", Some(s"pairs[$index].status"))
      val status = statusvalue.flatMap {
        case "proven" => Some(GenerationPairStatus.Proven)
        case "unproven" => Some(GenerationPairStatus.Unproven)
        case "incompatible" => Some(GenerationPairStatus.Incompatible)
        case value => errors += _error(s"Unknown pair status: $value", Some(s"pairs[$index].status")); None
      }
      for { target <- cncf; generator <- cozy; state <- status } yield GenerationPairEvidence(GenerationPair(target, generator), state)
    }
  }

  private def _pair(
    json: Json,
    path: String,
    errors: scala.collection.mutable.Builder[GenerationDiagnostic, Vector[GenerationDiagnostic]]
  ): Option[GenerationPair] = {
    val keys = json.asObject.map(_.keys.toSet)
    if (keys != Some(Set("cncfTarget", "cozyGenerator"))) {
      errors += _error("Generation pair must contain exactly cncfTarget and cozyGenerator.", Some(path))
      None
    } else {
      for {
        target <- _coordinate(json.hcursor.downField("cncfTarget"), s"$path.cncfTarget", errors)
        generator <- _coordinate(json.hcursor.downField("cozyGenerator"), s"$path.cozyGenerator", errors)
      } yield GenerationPair(target, generator)
    }
  }

  private def _coordinate(cursor: io.circe.ACursor, path: String, errors: scala.collection.mutable.Builder[GenerationDiagnostic, Vector[GenerationDiagnostic]]): Option[MavenCoordinate] = {
    val keys = cursor.focus.flatMap(_.asObject).map(_.keys.toSet)
    if (keys != Some(Set("organization", "artifact", "version"))) { errors += _error("Coordinate must contain exactly organization, artifact, and version.", Some(path)); None }
    else {
      val values = for { organization <- cursor.get[String]("organization").toOption; artifact <- cursor.get[String]("artifact").toOption; version <- cursor.get[String]("version").toOption } yield MavenCoordinate(organization, artifact, version)
      if (values.isEmpty) errors += _error("Coordinate fields must be strings.", Some(path))
      if (values.exists(x => Vector(x.organization, x.artifact, x.version).exists(_.trim.isEmpty))) errors += _error("Coordinate fields must be non-empty.", Some(path))
      values
    }
  }

  private def _error(message: String, coordinate: Option[String]): GenerationDiagnostic = GenerationDiagnostic(GenerationDiagnosticCode.MalformedEvidence, None, Some("valid cozy.generation-compatibility.v1 evidence"), None, coordinate, message)
  private def _pair_name(pair: GenerationPair): String = GenerationCompatibility.pairName(pair)
}

object GenerationCompatibility {
  val evidenceSchema = "cozy.generation-compatibility.v1"

  def validate(evidence: GenerationCompatibilityEvidence): Vector[GenerationDiagnostic] = {
    val errors = Vector.newBuilder[GenerationDiagnostic]
    if (evidence.schema != evidenceSchema) errors += GenerationDiagnostic(GenerationDiagnosticCode.MalformedEvidence, None, Some(evidenceSchema), Some(evidence.schema), Some("schema"), "Evidence schema version is not supported.")
    if (evidence.evidenceOwner.owner.trim.isEmpty) errors += GenerationDiagnostic(GenerationDiagnosticCode.MalformedEvidence, None, Some("non-empty owner"), Some(evidence.evidenceOwner.owner), Some("evidenceOwner.owner"), "Evidence owner must be non-empty.")
    if (evidence.evidenceOwner.location.trim.isEmpty) errors += GenerationDiagnostic(GenerationDiagnosticCode.MalformedEvidence, None, Some("non-empty location"), Some(evidence.evidenceOwner.location), Some("evidenceOwner.location"), "Evidence location must be non-empty.")
    if (evidence.entries.isEmpty) errors += GenerationDiagnostic(GenerationDiagnosticCode.MalformedEvidence, None, Some("at least one pair"), Some("empty"), Some("pairs"), "Evidence pairs must not be empty.")
    evidence.entries.groupBy(_.pair).toVector.sortBy { case (pair, _) => pairName(pair) }.foreach { case (pair, values) => if (values.size > 1) errors += GenerationDiagnostic(GenerationDiagnosticCode.MalformedEvidence, None, Some("one pair record"), Some(values.map(_.status.toString).sorted.mkString(",")), Some(pairName(pair)), "Duplicate or conflicting evidence pair.") }
    evidence.entries.sortBy(entry => pairName(entry.pair)).foreach { entry =>
      val coordinates = Vector(entry.pair.cncfTarget, entry.pair.cozyGenerator)
      if (coordinates.exists(value => Vector(value.organization, value.artifact, value.version).exists(_.trim.isEmpty))) errors += GenerationDiagnostic(GenerationDiagnosticCode.MalformedEvidence, None, Some("non-empty coordinates"), Some(pairName(entry.pair)), Some(pairName(entry.pair)), "Evidence coordinates must be non-empty.")
    }
    evidence.publishedDefault.foreach { pair =>
      val matching = evidence.entries.filter(_.pair == pair)
      if (isMutable(pair))
        errors += GenerationDiagnostic(GenerationDiagnosticCode.MalformedEvidence, Some(GenerationSource.PublishedDefault), Some("immutable published default"), Some(pairName(pair)), Some(pairName(pair)), "Published default generation coordinates must be immutable.")
      if (!matching.exists(_.status == GenerationPairStatus.Proven))
        errors += GenerationDiagnostic(GenerationDiagnosticCode.MalformedEvidence, Some(GenerationSource.PublishedDefault), Some("published default referencing one proven pair"), Some(pairName(pair)), Some(pairName(pair)), "Published default must reference a proven compatibility pair.")
    }
    errors.result().sortBy(_diagnostic_key)
  }

  def resolveSources(
    sources: GenerationSourceValues,
    evidence: GenerationCompatibilityEvidence
  ): GenerationResolution = {
    val evidenceerrors = validate(evidence)
    if (evidenceerrors.nonEmpty)
      return GenerationResolution(None, None, evidenceerrors)
    val explicit = Vector(
      GenerationSource.ProjectContract -> sources.projectContract,
      GenerationSource.OwningBuildBridge -> sources.owningBuildBridge,
      GenerationSource.Cli -> sources.cli
    ).collect { case (source, Some(pair)) => source -> pair }
    if (explicit.map(_._2).distinct.size > 1)
      GenerationResolution(None, None, Vector(GenerationDiagnostic(GenerationDiagnosticCode.ContradictorySource, None, Some("all explicit generation sources must agree"), Some(explicit.map { case (source, pair) => s"${_source_name(source)}=${pairName(pair)}" }.mkString(",")), None, "Explicit generation sources contradict one another.")))
    else explicit.headOption match {
      case Some((source, pair)) => GenerationResolution(Some(pair), Some(source), Vector.empty)
      case None => evidence.publishedDefault match {
        case Some(pair) => GenerationResolution(Some(pair), Some(GenerationSource.PublishedDefault), Vector.empty)
        case None => GenerationResolution(None, None, Vector.empty)
      }
    }
  }

  def accept(
    sources: GenerationSourceValues,
    outputVersion: String,
    executingCozyVersion: String,
    evidence: GenerationCompatibilityEvidence
  ): GenerationAcceptanceDecision = {
    val lifecycle =
      if (_is_snapshot_version(outputVersion))
        GenerationLifecycle.Development
      else
        GenerationLifecycle.Release
    val resolution = resolveSources(sources, evidence)
    val owner = evidence.evidenceOwner
    resolution.diagnostics.headOption match {
      case Some(_) =>
        GenerationAcceptanceDecision(
          None,
          None,
          lifecycle,
          GenerationAdmission.Unsupported,
          resolution.diagnostics,
          Vector.empty,
          owner
        )
      case None =>
        resolution.pair match {
          case None =>
            val diagnostic =
              GenerationDiagnostic(
                GenerationDiagnosticCode.MissingPublishedDefault,
                Some(GenerationSource.PublishedDefault),
                Some("explicit generation pair or published default"),
                None,
                None,
                "No explicit generation pair or published default is available."
              )
            GenerationAcceptanceDecision(
              None,
              None,
              lifecycle,
              GenerationAdmission.Unsupported,
              Vector(diagnostic),
              Vector.empty,
              owner
            )
          case Some(pair) =>
            val actualversion = Option(executingCozyVersion).map(_.trim).getOrElse("")
            val expectedversion = pair.cozyGenerator.version
            if (actualversion != expectedversion) {
              val diagnostic =
                GenerationDiagnostic(
                  GenerationDiagnosticCode.ExecutingGeneratorMismatch,
                  resolution.source,
                  Some(expectedversion),
                  Option(actualversion).filter(_.nonEmpty),
                  Some(_coordinate_name(pair.cozyGenerator)),
                  "The executing Cozy generator does not match the selected exact coordinate."
                )
              GenerationAcceptanceDecision(
                Some(pair),
                resolution.source,
                lifecycle,
                GenerationAdmission.Unsupported,
                Vector(diagnostic),
                Vector.empty,
                owner
              )
            } else {
              val admission = admit(pair, lifecycle, evidence)
              val notices =
                if (
                  admission.result == GenerationAdmission.Supported &&
                  lifecycle == GenerationLifecycle.Development &&
                  isMutable(pair)
                )
                  resolution.source.toVector.map { source =>
                    GenerationNotice(
                      GenerationNoticeCode.MutableDevelopmentPairAccepted,
                      source,
                      lifecycle,
                      pair,
                      owner,
                      s"Explicit mutable development generation pair accepted: ${pairName(pair)}."
                    )
                  }
                else
                  Vector.empty
              GenerationAcceptanceDecision(
                Some(pair),
                resolution.source,
                lifecycle,
                admission.result,
                admission.diagnostics,
                notices,
                owner
              )
            }
        }
    }
  }

  def admit(inputs: GenerationInputs, lifecycle: GenerationLifecycle, evidence: GenerationCompatibilityEvidence): GenerationAdmissionDecision = {
    val evidenceerrors = validate(evidence)
    if (evidenceerrors.nonEmpty) return GenerationAdmissionDecision(GenerationAdmission.Unsupported, evidenceerrors, evidence.evidenceOwner)
    val missing = Vector(inputs.cncfTarget.isEmpty -> GenerationDiagnosticCode.MissingCncfTarget, inputs.cozyGenerator.isEmpty -> GenerationDiagnosticCode.MissingCozyGenerator).collect { case (true, code) => GenerationDiagnostic(code, None, Some("exact coordinate"), None, None, "The exact generation coordinate is required.") }
    if (missing.nonEmpty) GenerationAdmissionDecision(GenerationAdmission.Unsupported, missing, evidence.evidenceOwner)
    else admit(GenerationPair(inputs.cncfTarget.get, inputs.cozyGenerator.get), lifecycle, evidence)
  }

  def admit(pair: GenerationPair, lifecycle: GenerationLifecycle, evidence: GenerationCompatibilityEvidence): GenerationAdmissionDecision = {
    val owner = evidence.evidenceOwner
    val evidenceerrors = validate(evidence)
    if (evidenceerrors.nonEmpty) return GenerationAdmissionDecision(GenerationAdmission.Unsupported, evidenceerrors, owner)
    val targetknown = evidence.entries.exists(_.pair.cncfTarget == pair.cncfTarget)
    val generatorknown = evidence.entries.exists(_.pair.cozyGenerator == pair.cozyGenerator)
    val unsupported = Vector(!targetknown -> GenerationDiagnostic(GenerationDiagnosticCode.UnsupportedCncfTarget, None, Some("evidence CNCF coordinate"), Some(pair.cncfTarget.version), Some(_coordinate_name(pair.cncfTarget)), "The exact CNCF target is not present in compatibility evidence."), !generatorknown -> GenerationDiagnostic(GenerationDiagnosticCode.UnsupportedCozyGenerator, None, Some("evidence Cozy coordinate"), Some(pair.cozyGenerator.version), Some(_coordinate_name(pair.cozyGenerator)), "The exact Cozy generator is not present in compatibility evidence.")).collect { case (true, diagnostic) => diagnostic }
    if (unsupported.nonEmpty) GenerationAdmissionDecision(GenerationAdmission.Unsupported, unsupported, owner)
    else if (lifecycle == GenerationLifecycle.Release && isMutable(pair)) GenerationAdmissionDecision(GenerationAdmission.Unsupported, Vector(GenerationDiagnostic(GenerationDiagnosticCode.SnapshotNotAllowedForRelease, None, Some("immutable release coordinates"), Some(pairName(pair)), Some(pairName(pair)), "SNAPSHOT generation coordinates are not admitted for release generation.")), owner)
    else evidence.entries.find(_.pair == pair) match {
      case None => GenerationAdmissionDecision(GenerationAdmission.Incompatible, Vector(GenerationDiagnostic(GenerationDiagnosticCode.UnsupportedGenerationPair, None, Some("an explicit pair evidence record"), Some(pairName(pair)), Some(pairName(pair)), "The coordinates are individually known, but this pair has no evidence record.")), owner)
      case Some(GenerationPairEvidence(_, GenerationPairStatus.Proven)) => GenerationAdmissionDecision(GenerationAdmission.Supported, Vector.empty, owner)
      case Some(GenerationPairEvidence(_, GenerationPairStatus.Unproven)) => GenerationAdmissionDecision(GenerationAdmission.Incompatible, Vector(GenerationDiagnostic(GenerationDiagnosticCode.UnprovenGenerationPair, None, Some("proven compatibility evidence"), Some(pairName(pair)), Some(pairName(pair)), "The exact generation pair is known but remains unproven.")), owner)
      case Some(GenerationPairEvidence(_, GenerationPairStatus.Incompatible)) => GenerationAdmissionDecision(GenerationAdmission.Incompatible, Vector(GenerationDiagnostic(GenerationDiagnosticCode.IncompatibleGenerationPair, None, Some("compatible pair evidence"), Some(pairName(pair)), Some(pairName(pair)), "The evidence explicitly marks this generation pair incompatible.")), owner)
    }
  }

  def isMutable(pair: GenerationPair): Boolean =
    _is_snapshot_version(pair.cncfTarget.version) ||
      _is_snapshot_version(pair.cozyGenerator.version)

  private def _is_snapshot_version(version: String): Boolean =
    Option(version).exists(_.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT"))
  private[compatibility] def _diagnostic_key(diagnostic: GenerationDiagnostic): (String, String, String, String, String, String) = (diagnostic.code.toString, diagnostic.source.map(_source_name).getOrElse(""), diagnostic.coordinate.getOrElse(""), diagnostic.expected.getOrElse(""), diagnostic.actual.getOrElse(""), diagnostic.message)
  private def _source_name(source: GenerationSource): String = source match { case GenerationSource.ProjectContract => "project"; case GenerationSource.OwningBuildBridge => "bridge"; case GenerationSource.Cli => "cli"; case GenerationSource.PublishedDefault => "published-default" }
  private def _coordinate_name(value: MavenCoordinate): String = s"${value.organization}:${value.artifact}:${value.version}"
  def pairName(pair: GenerationPair): String = s"${_coordinate_name(pair.cncfTarget)}|${_coordinate_name(pair.cozyGenerator)}"
}
