package cozy.compatibility

import cozy.config.CozyProjectYamlConfig
import org.goldenport.RAISE

/*
 * @since   Jul. 27, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CarMetadataCompatibility {
  sealed trait DiagnosticCode {
    def name: String
  }
  object DiagnosticCode {
    case object CarClassificationRequired extends DiagnosticCode {
      val name = "CAR_METADATA_CAR_CLASSIFICATION_REQUIRED"
    }
    case object CozyVersionMissing extends DiagnosticCode {
      val name = "CAR_METADATA_COZY_VERSION_MISSING"
    }
    case object CncfCompileTargetMissing extends DiagnosticCode {
      val name = "CAR_METADATA_CNCF_COMPILE_TARGET_MISSING"
    }
    case object CncfCompileTargetMultiple extends DiagnosticCode {
      val name = "CAR_METADATA_CNCF_COMPILE_TARGET_MULTIPLE"
    }
    case object RuntimeMinimumMissing extends DiagnosticCode {
      val name = "CAR_METADATA_CNCF_RUNTIME_MINIMUM_MISSING"
    }
    case object RuntimeTestedMissing extends DiagnosticCode {
      val name = "CAR_METADATA_CNCF_RUNTIME_TESTED_MISSING"
    }
    case object RuntimeRangeInvalid extends DiagnosticCode {
      val name = "CAR_METADATA_CNCF_RUNTIME_RANGE_INVALID"
    }
    case object CompileTargetBelowMinimum extends DiagnosticCode {
      val name = "CAR_METADATA_CNCF_COMPILE_TARGET_BELOW_MINIMUM"
    }
    case object CompileTargetAboveMaximum extends DiagnosticCode {
      val name = "CAR_METADATA_CNCF_COMPILE_TARGET_ABOVE_MAXIMUM"
    }
    case object CompileTargetExcluded extends DiagnosticCode {
      val name = "CAR_METADATA_CNCF_COMPILE_TARGET_EXCLUDED"
    }
    case object CompileTargetUntested extends DiagnosticCode {
      val name = "CAR_METADATA_CNCF_COMPILE_TARGET_UNTESTED"
    }
    case object ResolvedCncfVersionMissing extends DiagnosticCode {
      val name = "CAR_METADATA_RESOLVED_CNCF_VERSION_MISSING"
    }
    case object ResolvedCncfVersionMismatch extends DiagnosticCode {
      val name = "CAR_METADATA_RESOLVED_CNCF_VERSION_MISMATCH"
    }
    case object ResolvedCncfArtifactMultiple extends DiagnosticCode {
      val name = "CAR_METADATA_RESOLVED_CNCF_ARTIFACT_MULTIPLE"
    }
    case object ResolvedCncfIdentityMismatch extends DiagnosticCode {
      val name = "CAR_METADATA_RESOLVED_CNCF_IDENTITY_MISMATCH"
    }
    case object ReleaseGenerationPairRejected extends DiagnosticCode {
      val name = "CAR_METADATA_RELEASE_GENERATION_PAIR_REJECTED"
    }
  }

  final case class Contract(
    cozyVersion: String,
    cncfCompileCoordinate: String,
    cncfCompileTarget: MavenCoordinate,
    runtimeCompatibility: CncfRuntimeCompatibility
  ) {
    def render: String =
      Vector(
        s"cozyVersion=${cozyVersion}",
        s"cncfCompileCoordinate=${cncfCompileCoordinate}",
        s"runtimeMinimum=${runtimeCompatibility.minimum.getOrElse("missing")}",
        s"runtimeMaximum=${runtimeCompatibility.maximum.getOrElse("unbounded")}",
        s"runtimeExcluded=${runtimeCompatibility.excluded.mkString(",")}",
        s"runtimeTested=${runtimeCompatibility.tested.mkString(",")}"
      ).mkString(",")
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
      Vector(
        "code" -> code.name,
        "source" -> source,
        "expected" -> expected,
        "actual" -> actual,
        "message" -> message,
        "correctiveAction" -> correctiveAction
      ).map { case (key, value) =>
        s""""${_escape(key)}":"${_escape(value)}""""
      }.mkString("{", ",", "}")

    private def _escape(value: String): String =
      value.flatMap {
        case '"' => "\\\""
        case '\\' => "\\\\"
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c => c.toString
      }
  }

  final case class Decision(
    contract: Option[Contract],
    diagnostics: Vector[Diagnostic]
  ) {
    def isAccepted: Boolean =
      contract.nonEmpty && diagnostics.isEmpty
  }

  final case class ResolvedCncfArtifact(
    source: String,
    runtime: Option[String],
    moduleCoordinate: Option[String],
    version: Option[String]
  )

  final case class GenerationAcceptanceContext(
    evidence: GenerationCompatibilityEvidence,
    executingCozyVersion: String
  )

  private final case class ParsedDependency(
    coordinate: String,
    organization: String,
    artifact: String,
    version: String
  )

  def isCarProject(metadata: CozyProjectYamlConfig.Config): Boolean =
    Vector(
      metadata.value("project.kind"),
      metadata.value("packaging.kind")
    ).flatten.
      exists(_.equalsIgnoreCase("car"))

  def evaluateProject(metadata: CozyProjectYamlConfig.Config): Decision =
    _evaluate(
      metadata,
      Vector.empty,
      requireresolvedartifact = false,
      acceptancecontext = None
    )

  private[cozy] def evaluateProject(
    metadata: CozyProjectYamlConfig.Config,
    acceptanceContext: GenerationAcceptanceContext
  ): Decision =
    _evaluate(
      metadata,
      Vector.empty,
      requireresolvedartifact = false,
      acceptancecontext = Some(acceptanceContext)
    )

  def evaluate(
    metadata: CozyProjectYamlConfig.Config,
    resolvedCncfArtifacts: Vector[ResolvedCncfArtifact]
  ): Decision =
    _evaluate(
      metadata,
      resolvedCncfArtifacts,
      requireresolvedartifact = true,
      acceptancecontext = None
    )

  private[cozy] def evaluate(
    metadata: CozyProjectYamlConfig.Config,
    resolvedCncfArtifacts: Vector[ResolvedCncfArtifact],
    acceptanceContext: GenerationAcceptanceContext
  ): Decision =
    _evaluate(
      metadata,
      resolvedCncfArtifacts,
      requireresolvedartifact = true,
      acceptancecontext = Some(acceptanceContext)
    )

  private def _evaluate(
    metadata: CozyProjectYamlConfig.Config,
    resolvedcncfartifacts: Vector[ResolvedCncfArtifact],
    requireresolvedartifact: Boolean,
    acceptancecontext: Option[GenerationAcceptanceContext]
  ): Decision = {
    if (!isCarProject(metadata))
      Decision(None, Vector.empty)
    else {
      val diagnostics = Vector.newBuilder[Diagnostic]
      val cozyversion = metadata.value("build.cozyVersion")
      if (cozyversion.isEmpty)
        diagnostics += _diagnostic(
          DiagnosticCode.CozyVersionMissing,
          "project.yaml build.cozyVersion",
          "one exact Cozy generator version",
          "missing",
          "CAR generation metadata does not declare its exact Cozy generator.",
          "Set project.yaml build.cozyVersion to the exact generator version used by the build."
        )

      val dependencies = metadata.list("build.dependencies.compile").flatMap(_parse_dependency)
      val cncfdependencies = dependencies.filter(_is_cncf_dependency)
      if (cncfdependencies.isEmpty)
        diagnostics += _diagnostic(
          DiagnosticCode.CncfCompileTargetMissing,
          "project.yaml build.dependencies.compile",
          "one org.goldenport goldenport-cncf dependency",
          "missing",
          "CAR build metadata does not declare the exact CNCF compile target.",
          "Add exactly one org.goldenport::goldenport-cncf:<version> compile dependency."
        )
      else if (cncfdependencies.size > 1)
        diagnostics += _diagnostic(
          DiagnosticCode.CncfCompileTargetMultiple,
          "project.yaml build.dependencies.compile",
          "one org.goldenport goldenport-cncf dependency",
          cncfdependencies.map(_.coordinate).sorted.mkString(","),
          "CAR build metadata declares multiple CNCF compile targets.",
          "Retain exactly one CNCF compile dependency matching generated source."
        )

      val minimum = metadata.value("packaging.car.runtime.cncf.minimum")
      val maximum = metadata.value("packaging.car.runtime.cncf.maximum")
      val excluded = metadata.list("packaging.car.runtime.cncf.excluded").distinct.sorted
      val tested = metadata.list("packaging.car.runtime.cncf.tested").distinct.sorted
      if (minimum.isEmpty)
        diagnostics += _diagnostic(
          DiagnosticCode.RuntimeMinimumMissing,
          "project.yaml packaging.car.runtime.cncf.minimum",
          "a minimum runtime compatible with the compile target",
          "missing",
          "CAR runtime compatibility does not declare a minimum CNCF version.",
          "Set packaging.car.runtime.cncf.minimum and keep it compatible with the exact compile target."
        )
      if (tested.isEmpty)
        diagnostics += _diagnostic(
          DiagnosticCode.RuntimeTestedMissing,
          "project.yaml packaging.car.runtime.cncf.tested",
          "the exact CNCF compile target",
          "missing",
          "CAR runtime compatibility does not record a tested CNCF version.",
          "Include the exact CNCF compile target in packaging.car.runtime.cncf.tested."
        )
      (minimum, maximum) match {
        case (Some(minimumversion), Some(maximumversion))
            if compareVersions(minimumversion, maximumversion) > 0 =>
          diagnostics += _diagnostic(
            DiagnosticCode.RuntimeRangeInvalid,
            "project.yaml packaging.car.runtime.cncf",
            "minimum less than or equal to maximum",
            s"${minimumversion}..${maximumversion}",
            "CAR runtime compatibility has an inverted CNCF version range.",
            "Set minimum and maximum so the declared runtime range is ordered."
          )
        case _ =>
      }

      val selected = cncfdependencies match {
        case Vector(dependency) => Some(dependency)
        case _ => None
      }
      selected.foreach { dependency =>
        minimum.foreach { minimumversion =>
          if (compareVersions(dependency.version, minimumversion) < 0)
            diagnostics += _diagnostic(
              DiagnosticCode.CompileTargetBelowMinimum,
              "project.yaml packaging.car.runtime.cncf.minimum",
              s"at most compile target ${dependency.version}",
              minimumversion,
              "The exact CNCF compile target is below the declared runtime minimum.",
              "Lower the minimum or select a compile target inside the runtime range."
            )
        }
        maximum.foreach { maximumversion =>
          if (compareVersions(dependency.version, maximumversion) > 0)
            diagnostics += _diagnostic(
              DiagnosticCode.CompileTargetAboveMaximum,
              "project.yaml packaging.car.runtime.cncf.maximum",
              s"at least compile target ${dependency.version}",
              maximumversion,
              "The exact CNCF compile target exceeds the declared runtime maximum.",
              "Raise the maximum or select a compile target inside the runtime range."
            )
        }
        if (excluded.contains(dependency.version))
          diagnostics += _diagnostic(
            DiagnosticCode.CompileTargetExcluded,
            "project.yaml packaging.car.runtime.cncf.excluded",
            s"values other than compile target ${dependency.version}",
            excluded.mkString(","),
            "The exact CNCF compile target is excluded by the CAR runtime range.",
            "Remove the compile target from excluded or select another exact compile target."
          )
        if (!tested.contains(dependency.version))
          diagnostics += _diagnostic(
            DiagnosticCode.CompileTargetUntested,
            "project.yaml packaging.car.runtime.cncf.tested",
            dependency.version,
            if (tested.isEmpty) "missing" else tested.mkString(","),
            "The exact CNCF compile target is absent from the tested runtime set.",
            "Include the compile target in packaging.car.runtime.cncf.tested."
          )
        if (requireresolvedartifact)
          resolvedcncfartifacts match {
            case Vector() =>
              diagnostics += _diagnostic(
                DiagnosticCode.ResolvedCncfVersionMissing,
                "package-car resolved CNCF runtime descriptor",
                dependency.version,
                "missing",
                "Packaging cannot prove which CNCF artifact compiled the generated source.",
                "Provide the resolved CNCF dependency JAR containing META-INF/cncf/runtime.yaml."
              )
            case Vector(artifact) if !_artifact_identity_matches(artifact, dependency) =>
              diagnostics += _diagnostic(
                _artifact_diagnostic_code(artifact, dependency),
                "package-car resolved CNCF runtime descriptor",
                _expected_artifact_identity(dependency),
                _artifact_identity(artifact),
                "The resolved JAR descriptor does not identify the exact CNCF compile artifact.",
                "Resolve the project.yaml CNCF compile dependency before packaging."
              )
            case Vector(_) =>
            case artifacts =>
              diagnostics += _diagnostic(
                DiagnosticCode.ResolvedCncfArtifactMultiple,
                "package-car resolved CNCF runtime descriptor",
                "one exact CNCF artifact descriptor",
                artifacts.map(_.source).sorted.mkString(","),
                "Packaging resolved multiple JARs that claim CNCF runtime identity.",
                "Retain exactly one resolved CNCF dependency JAR for package admission."
              )
          }
      }

      val contract = for {
        generator <- cozyversion
        dependency <- selected
      } yield Contract(
        generator,
        dependency.coordinate,
        MavenCoordinate(dependency.organization, dependency.artifact, dependency.version),
        CncfRuntimeCompatibility(minimum, maximum, excluded, tested)
      )
      val releasediagnostics =
        (metadata.value("project.component.version"), contract) match {
          case (Some(outputversion), Some(value))
              if !_is_snapshot_version(outputversion) =>
            _release_generation_diagnostics(
              value,
              outputversion,
              acceptancecontext
            )
          case _ =>
            Vector.empty
        }
      Decision(contract, diagnostics.result() ++ releasediagnostics)
    }
  }

  private def _release_generation_diagnostics(
    contract: Contract,
    outputversion: String,
    acceptancecontext: Option[GenerationAcceptanceContext]
  ): Vector[Diagnostic] = {
    val pair =
      GenerationCompatibilityBoundary.createPair(
        contract.cncfCompileTarget.version,
        contract.cozyVersion
      )
    val loaded =
      acceptancecontext.
        map(context => Right(context.evidence)).
        getOrElse(GenerationCompatibilityEvidenceLoader.load())
    loaded match {
      case Left(values) =>
        values.map(_generation_diagnostic)
      case Right(evidence) =>
        val decision =
          GenerationCompatibility.accept(
            GenerationSourceValues(Some(pair), None, None),
            outputversion,
            acceptancecontext.
              map(_.executingCozyVersion).
              getOrElse(org.simplemodeling.cozy.BuildInfo.version),
            evidence
          )
        if (decision.result == GenerationAdmission.Supported)
          Vector.empty
        else
          decision.diagnostics.map(_generation_diagnostic)
    }
  }

  private def _generation_diagnostic(
    diagnostic: GenerationDiagnostic
  ): Diagnostic =
    _diagnostic(
      DiagnosticCode.ReleaseGenerationPairRejected,
      diagnostic.source.map(_.toString).getOrElse("generation compatibility evidence"),
      diagnostic.expected.getOrElse("one proven immutable generation pair"),
      diagnostic.actual.orElse(diagnostic.coordinate).getOrElse("missing"),
      s"${diagnostic.code}: ${diagnostic.message}",
      "Publish and prove the exact immutable CNCF/Cozy pair before release output, or retain a SNAPSHOT output for explicit development."
    )

  private def _is_snapshot_version(version: String): Boolean =
    Option(version).exists(
      _.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT")
    )

  def requireValidMetadata(
    metadata: CozyProjectYamlConfig.Config
  ): Option[Contract] =
    _require_valid(evaluateProject(metadata))

  private[cozy] def requireValidMetadata(
    metadata: CozyProjectYamlConfig.Config,
    acceptanceContext: GenerationAcceptanceContext
  ): Option[Contract] =
    _require_valid(evaluateProject(metadata, acceptanceContext))

  def requireValidProject(
    metadata: CozyProjectYamlConfig.Config,
    resolvedCncfArtifacts: Vector[ResolvedCncfArtifact]
  ): Option[Contract] =
    _require_valid(evaluate(metadata, resolvedCncfArtifacts))

  def requireValidCarProject(
    metadata: CozyProjectYamlConfig.Config,
    resolvedCncfArtifacts: Vector[ResolvedCncfArtifact]
  ): Contract = {
    val decision =
      if (isCarProject(metadata))
        evaluate(metadata, resolvedCncfArtifacts)
      else
        _car_classification_required(metadata)
    _require_car_contract(decision)
  }

  private def _car_classification_required(
    metadata: CozyProjectYamlConfig.Config
  ): Decision =
    Decision(
      None,
      Vector(
        _diagnostic(
          DiagnosticCode.CarClassificationRequired,
          "project.yaml project.kind / packaging.kind",
          "project.kind: car or packaging.kind: car",
          _car_classification(metadata),
          "CAR packaging requires project metadata that explicitly classifies the project as a CAR.",
          "Set project.kind or packaging.kind to car before invoking CAR packaging."
        )
      )
    )

  private def _require_car_contract(decision: Decision): Contract =
    _require_valid(decision) match {
      case Some(contract) => contract
      case None =>
        RAISE.invalidArgumentFault(
          "CAR metadata validation completed without a compatibility contract."
        )
    }

  private[cozy] def requireValidCarProject(
    metadata: CozyProjectYamlConfig.Config,
    resolvedCncfArtifacts: Vector[ResolvedCncfArtifact],
    acceptanceContext: GenerationAcceptanceContext
  ): Contract = {
    val decision =
      if (isCarProject(metadata))
        evaluate(metadata, resolvedCncfArtifacts, acceptanceContext)
      else
        _car_classification_required(metadata)
    _require_car_contract(decision)
  }

  private def _car_classification(
    metadata: CozyProjectYamlConfig.Config
  ): String = {
    val values = Vector(
      "project.kind" -> metadata.value("project.kind"),
      "packaging.kind" -> metadata.value("packaging.kind")
    ).collect {
      case (key, Some(value)) => s"${key}=${value}"
    }
    if (values.isEmpty) "missing" else values.mkString(",")
  }

  private def _require_valid(decision: Decision): Option[Contract] = {
    if (decision.diagnostics.nonEmpty)
      RAISE.invalidArgumentFault(decision.diagnostics.map(_.render).mkString("[", ",", "]"))
    decision.contract
  }

  def compareVersions(left: String, right: String): Int = {
    def _parts_(value: String): Vector[String] =
      value.split("[.\\-+_]").toVector.map(_.trim).filter(_.nonEmpty)
    def _number_(value: String): Option[BigInt] =
      if (value.forall(_.isDigit)) Some(BigInt(value)) else None
    def _compare_part_(l: String, r: String): Int =
      (_number_(l), _number_(r)) match {
        case (Some(a), Some(b)) => a.compare(b)
        case (Some(_), None) => 1
        case (None, Some(_)) => -1
        case (None, None) => l.compareToIgnoreCase(r)
      }
    def _remaining_(parts: Vector[String], index: Int): Int =
      parts.drop(index).find(_.nonEmpty).map { x =>
        _number_(x) match {
          case Some(n) => n.signum
          case None => -1
        }
      }.getOrElse(0)

    val leftparts = _parts_(left)
    val rightparts = _parts_(right)
    val size = math.max(leftparts.length, rightparts.length)
    (0 until size).foldLeft(0) {
      case (0, index) if index >= leftparts.length => -_remaining_(rightparts, index)
      case (0, index) if index >= rightparts.length => _remaining_(leftparts, index)
      case (0, index) => _compare_part_(leftparts(index), rightparts(index))
      case (result, _) => result
    }
  }

  private def _parse_dependency(coordinate: String): Option[ParsedDependency] =
    coordinate.split(":", -1).toList match {
      case organization :: "" :: artifact :: version :: Nil
          if Vector(organization, artifact, version).forall(_.trim.nonEmpty) =>
        Some(ParsedDependency(coordinate, organization.trim, artifact.trim, version.trim))
      case organization :: artifact :: version :: Nil
          if Vector(organization, artifact, version).forall(_.trim.nonEmpty) =>
        Some(ParsedDependency(coordinate, organization.trim, artifact.trim, version.trim))
      case _ =>
        None
    }

  private def _is_cncf_dependency(dependency: ParsedDependency): Boolean =
    dependency.organization == "org.goldenport" &&
      (
        dependency.artifact == "goldenport-cncf" ||
          dependency.artifact == "goldenport-cncf_3"
      )

  private def _artifact_identity_matches(
    artifact: ResolvedCncfArtifact,
    dependency: ParsedDependency
  ): Boolean =
    artifact.runtime.contains("cncf") &&
      artifact.version.contains(dependency.version) &&
      artifact.moduleCoordinate.flatMap(_parse_dependency).exists { module =>
        module.organization == dependency.organization &&
          _artifact_matches(dependency.artifact, module.artifact) &&
          module.version == dependency.version
      }

  private def _artifact_matches(expected: String, actual: String): Boolean =
    if (expected == "goldenport-cncf")
      actual == expected || actual == "goldenport-cncf_3"
    else
      actual == expected

  private def _artifact_diagnostic_code(
    artifact: ResolvedCncfArtifact,
    dependency: ParsedDependency
  ): DiagnosticCode =
    if (
      artifact.runtime.contains("cncf") &&
      artifact.moduleCoordinate.flatMap(_parse_dependency).exists { module =>
        module.organization == dependency.organization &&
          _artifact_matches(dependency.artifact, module.artifact)
      } &&
      (
        !artifact.version.contains(dependency.version) ||
          !artifact.moduleCoordinate.flatMap(_parse_dependency).exists(_.version == dependency.version)
      )
    )
      DiagnosticCode.ResolvedCncfVersionMismatch
    else
      DiagnosticCode.ResolvedCncfIdentityMismatch

  private def _expected_artifact_identity(dependency: ParsedDependency): String =
    s"runtime=cncf,module=${dependency.organization}:${dependency.artifact}:${dependency.version},version=${dependency.version}"

  private def _artifact_identity(artifact: ResolvedCncfArtifact): String =
    Vector(
      s"source=${artifact.source}",
      s"runtime=${artifact.runtime.getOrElse("missing")}",
      s"module=${artifact.moduleCoordinate.getOrElse("missing")}",
      s"version=${artifact.version.getOrElse("missing")}"
    ).mkString(",")

  private def _diagnostic(
    code: DiagnosticCode,
    source: String,
    expected: String,
    actual: String,
    message: String,
    correctiveaction: String
  ): Diagnostic =
    Diagnostic(code, source, expected, actual, message, correctiveaction)
}
