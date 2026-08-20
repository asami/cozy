package cozy.compatibility

import org.goldenport.RAISE

/*
 * @since   Jul. 28, 2026
 *  version Jul. 28, 2026
 * @version Aug. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object GenerationCompatibilityBoundary {
  val CNCF_ORGANIZATION = "org.goldenport"
  val CNCF_ARTIFACT = "goldenport-cncf_3"
  val COZY_ORGANIZATION = "org.simplemodeling"
  val COZY_ARTIFACT = "cozy_2.12"

  def requireValidInvocation(
    args: List[String],
    boundary: String
  ): Option[GenerationAcceptanceDecision] =
    if (!_is_generation_invocation(args))
      None
    else {
      val command = _generation_command(args).get
      val cncfversion = _required_option(args, "cncf-version", boundary)
      val cozyversion = _required_option(args, "cozy-generator-version", boundary)
      val outputversion =
        command match {
          case "modeler-scala-value" =>
            cncfversion
          case "modeler-scala" =>
            _required_option(args, "component-version", boundary)
          case "car-sbt-project" =>
            _required_option(args, "version", boundary)
        }
      val pair = createPair(cncfversion, cozyversion)
      Some(requireAccepted(
        GenerationSourceValues(None, None, Some(pair)),
        outputversion,
        org.simplemodeling.cozy.BuildInfo.version,
        boundary
      ))
    }

  def requireAcceptedProjectPair(
    cncfVersion: String,
    cozyVersion: String,
    outputVersion: String,
    boundary: String
  ): GenerationAcceptanceDecision = {
    val pair = createPair(cncfVersion, cozyVersion)
    requireAccepted(
      GenerationSourceValues(Some(pair), None, None),
      outputVersion,
      org.simplemodeling.cozy.BuildInfo.version,
      boundary
    )
  }

  private[cozy] def requireAcceptedProjectPair(
    cncfVersion: String,
    cozyVersion: String,
    outputVersion: String,
    executingCozyVersion: String,
    evidence: GenerationCompatibilityEvidence,
    boundary: String
  ): GenerationAcceptanceDecision = {
    val pair = createPair(cncfVersion, cozyVersion)
    _require_accepted(
      GenerationSourceValues(Some(pair), None, None),
      outputVersion,
      executingCozyVersion,
      evidence,
      boundary
    )
  }

  def requireAccepted(
    sources: GenerationSourceValues,
    outputVersion: String,
    executingCozyVersion: String,
    boundary: String
  ): GenerationAcceptanceDecision = {
    val evidence = GenerationCompatibilityEvidenceLoader.load().fold(
      diagnostics => RAISE.invalidArgumentFault(_render_failure(boundary, diagnostics)),
      identity
    )
    _require_accepted(
      sources,
      outputVersion,
      executingCozyVersion,
      evidence,
      boundary
    )
  }

  private def _require_accepted(
    sources: GenerationSourceValues,
    outputversion: String,
    executingcozyversion: String,
    evidence: GenerationCompatibilityEvidence,
    boundary: String
  ): GenerationAcceptanceDecision = {
    val decision =
      GenerationCompatibility.accept(
        sources,
        outputversion,
        executingcozyversion,
        evidence
      )
    if (decision.result != GenerationAdmission.Supported)
      RAISE.invalidArgumentFault(_render_failure(boundary, decision.diagnostics))
    decision.notices.foreach(notice => System.err.println(_render_notice(boundary, notice)))
    decision
  }

  def createPair(cncfVersion: String, cozyVersion: String): GenerationPair =
    GenerationPair(
      MavenCoordinate(CNCF_ORGANIZATION, CNCF_ARTIFACT, cncfVersion.trim),
      MavenCoordinate(COZY_ORGANIZATION, COZY_ARTIFACT, cozyVersion.trim)
    )

  private def _is_generation_invocation(args: List[String]): Boolean =
    _generation_command(args).nonEmpty &&
      _option(args, "cncf-version").nonEmpty

  private def _generation_command(args: List[String]): Option[String] =
    args.find {
      case "modeler-scala" | "modeler-scala-value" | "car-sbt-project" =>
        true
      case _ =>
        false
    }

  private def _required_option(
    args: List[String],
    name: String,
    boundary: String
  ): String =
    _option(args, name).getOrElse(
      RAISE.invalidArgumentFault(
        s"[cozy.generation.acceptance] boundary=$boundary code=Missing${_camel(name)} " +
          s"expected=--$name recovery=pass-the-exact-generation-coordinate"
      )
    )

  private def _option(args: List[String], name: String): Option[String] = {
    val inlineprefix = s"--$name="
    args.zipWithIndex.collectFirst {
      case (value, _) if value.startsWith(inlineprefix) =>
        value.drop(inlineprefix.length).trim
      case (value, index) if value == s"--$name" && index + 1 < args.length =>
        args(index + 1).trim
    }.filter(_.nonEmpty)
  }

  private def _render_failure(
    boundary: String,
    diagnostics: Vector[GenerationDiagnostic]
  ): String = {
    val details =
      diagnostics.map { diagnostic =>
        Vector[Option[String]](
          Some(s"code=${diagnostic.code}"),
          diagnostic.source.map(value => s"source=${_source_name(value)}"),
          diagnostic.expected.map(value => s"expected=$value"),
          diagnostic.actual.map(value => s"actual=$value"),
          diagnostic.coordinate.map(value => s"coordinate=$value"),
          Some(s"message=${diagnostic.message}")
        ).flatten.mkString(" ")
      }.mkString("; ")
    s"[cozy.generation.acceptance] boundary=$boundary $details " +
      "recovery=select-the-exact-generator-for-development-or-a-proven-immutable-pair-for-release"
  }

  private def _render_notice(
    boundary: String,
    notice: GenerationNotice
  ): String =
    s"[cozy.generation.acceptance] boundary=$boundary level=notice " +
      s"code=${notice.code} lifecycle=development source=${_source_name(notice.source)} " +
      s"pair=${GenerationCompatibility.pairName(notice.pair)} " +
      s"evidence=${notice.evidenceOwner.location} message=${notice.message}"

  private def _source_name(source: GenerationSource): String =
    source match {
      case GenerationSource.ProjectContract => "project"
      case GenerationSource.OwningBuildBridge => "owning-build-bridge"
      case GenerationSource.Cli => "cli"
      case GenerationSource.PublishedDefault => "published-default"
    }

  private def _camel(name: String): String =
    name.split('-').toVector.filter(_.nonEmpty).map(_.capitalize).mkString
}
