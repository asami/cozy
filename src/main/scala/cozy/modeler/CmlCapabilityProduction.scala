package cozy.modeler

import java.nio.file.{Path, Paths}
import CmlCapabilityProductionRequest.ProductionRequest
import CmlCapabilityGenerationContext.{Issue, IssueKind, PublicationFailure}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityProduction {
  sealed abstract class Failure
  final case class Core(failure: CmlCapabilityGenerationContext.Failure) extends Failure
  final case class RequestOrAcquisition(issues: Vector[Issue]) extends Failure
  final case class Publication(failure: PublicationFailure) extends Failure
  final class Prepared private[CmlCapabilityProduction] (
    val request: ProductionRequest, val generated: CmlCapabilityGeneration.Generated,
    val sources: Vector[CmlCapabilitySource.CapabilitySource], val rendered: CmlCapabilityGenerationScala.RenderedBatch,
    val batch: CmlCapabilityPackagePublication.Batch
  )

  def readRequest(path: Path): Either[Failure, ProductionRequest] =
    CmlCapabilityProductionSourcePart._request(path).left.map(RequestOrAcquisition)
  private[cozy] def preflightSource(request: ProductionRequest, sourceRoot: Path,
    sourcePath: Path, sourceIdentity: String): Either[Failure, Unit] = {
    val issues = _request_issues(request)
    if (issues.nonEmpty) Left(RequestOrAcquisition(issues))
    else for {
      _ <- CmlCapabilityProductionSourcePart._preflight_source(request.sources, sourceRoot, sourcePath, sourceIdentity).left.map(RequestOrAcquisition)
      _ <- CmlCapabilityPackagePublication.preflightNormalDestinations(request).left.map(RequestOrAcquisition)
    } yield ()
  }
  def prepare(request: ProductionRequest, sourceRoot: Path, outputRoot: Path,
    selectedSource: Option[GenerationProvenance.SourceInput]): Either[Failure, Prepared] = {
    val issues = _request_issues(request)
    if (issues.nonEmpty) return Left(RequestOrAcquisition(issues))
    for {
      inputs <- CmlCapabilityProductionSourcePart._acquire(request.sources, sourceRoot, selectedSource).left.map(RequestOrAcquisition)
      generated <- CmlCapabilityGeneration.generate(inputs, request.scope, request.contexts).left.map(Core)
      rendered <- CmlCapabilityGenerationScala.render(generated, request.scalaoutputs).left.map(RequestOrAcquisition)
      batch <- CmlCapabilityPackagePublication.prepare(outputRoot, generated, request, rendered).left.map(RequestOrAcquisition)
    } yield new Prepared(request, generated, request.sources, rendered, batch)
  }
  def publish(outputRoot: Path, prepared: Prepared): Either[Failure, Vector[Path]] =
    if (prepared == null) Left(RequestOrAcquisition(Vector(_issue("prepared", "A successful Prepared is required."))))
    else CmlCapabilityPackagePublication.publish(outputRoot, prepared.batch).left.map(Publication)

  def invocation(args: List[String]): Either[Vector[Issue], (List[String], Option[(Path, Path)])] = {
    val names = Set("capability-request", "capability-source-root")
    val values = scala.collection.mutable.Map.empty[String, String]
    val remaining = List.newBuilder[String]
    var rest = args
    while (rest.nonEmpty) {
      val head = rest.head
      val name = head.stripPrefix("--").takeWhile(_ != '=')
      if (head.startsWith("--") && names(name)) {
        if (values.contains(name)) return Left(Vector(_issue(name, "Duplicate Capability option.")))
        val pair = if (head.contains("=")) (head.substring(head.indexOf('=') + 1), rest.tail)
          else if (rest.tail.nonEmpty && !rest.tail.head.startsWith("--")) (rest.tail.head, rest.tail.tail)
          else return Left(Vector(_issue(name, "Capability option requires exactly one literal value.")))
        if (!CmlCapabilitySource._valid_text(pair._1)) return Left(Vector(_issue(name, "Capability option value must be nonempty, unpadded and control-free.")))
        values += name -> pair._1
        rest = pair._2
      } else { remaining += head; rest = rest.tail }
    }
    if (values.isEmpty) Right(remaining.result() -> None)
    else if (values.keySet != names) Left(Vector(_issue("capability", "Both Capability options must be supplied together.")))
    else Right(remaining.result() -> Some(Paths.get(values("capability-request")) -> Paths.get(values("capability-source-root"))))
  }
  def diagnostic(failure: Failure): String = failure match {
    case RequestOrAcquisition(issues) => issues.map(_render_issue).mkString("[", ",", "]")
    case Publication(value) => value.issues.map(_render_issue).mkString("[", ",", "]")
    case Core(CmlCapabilityGenerationContext.GenerationFailure(issues)) => issues.map(_render_issue).mkString("[", ",", "]")
    case Core(CmlCapabilityGenerationContext.SyntaxFailure(issues)) => issues.map(x =>
      s"${x.kind} location=${x.scope.path}:${x.fieldPath} detail=${x.message}").mkString("[", ",", "]")
    case Core(CmlCapabilityGenerationContext.SemanticFailure(diagnostics)) => diagnostics.map(x =>
      s"${x.kind} location=${x.source.map(_.path).getOrElse(x.fieldpath)}:${x.fieldpath} detail=${x.detail}").mkString("[", ",", "]")
  }
  private def _render_issue(issue: Issue): String = s"${issue.kind} location=${issue.fieldpath} detail=${issue.detail}"
  private def _request_issues(request: ProductionRequest): Vector[Issue] =
    if (request == null || request.producer == null || request.sources == null || request.scope == null ||
      request.contexts == null || request.packages == null || request.scalaoutputs == null)
      Vector(_issue("request", "The complete explicit request is required."))
    else if (request.producer != CmlCapabilityGenerationContext.Producer("cozy", org.simplemodeling.cozy.BuildInfo.version))
      Vector(Issue(IssueKind.ProducerMismatch, "producer", None, Vector.empty,
        "Producer must equal cozy and the actual executing BuildInfo.version, including app-only input."))
    else Vector.empty
  private def _issue(field: String, detail: String): Issue = Issue(IssueKind.Malformed, field, None, Vector.empty, detail)
}
