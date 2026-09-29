package cozy.generation

import java.nio.file.attribute.FileTime

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyFileUpdatePolicy {
  private[cozy] sealed trait FileState

  private[cozy] object FileState {
    private[cozy] case object Absent extends FileState
    private[cozy] final case class Invalid(reason: String) extends FileState
    private[cozy] final case class Valid(timestamp: Option[FileTime]) extends FileState
  }

  private[cozy] final case class FileObservation(
    path: String,
    state: FileState
  )

  private[cozy] sealed trait Intent

  private[cozy] object Intent {
    private[cozy] case object Generation extends Intent
    private[cozy] case object ExplicitPrebuilt extends Intent
  }

  private[cozy] final case class Request(
    intent: Intent,
    producer: Option[String],
    inputs: Vector[FileObservation],
    output: FileObservation,
    force: Boolean
  )

  private[cozy] sealed trait Decision

  private[cozy] object Decision {
    private[cozy] final case class Generate(
      producer: String,
      reason: GenerationReason
    ) extends Decision

    private[cozy] final case class Reuse(path: String) extends Decision
    private[cozy] final case class Adopt(path: String) extends Decision
    private[cozy] final case class Unavailable(reason: UnavailableReason) extends Decision
  }

  private[cozy] sealed trait GenerationReason

  private[cozy] object GenerationReason {
    private[cozy] case object Forced extends GenerationReason
    private[cozy] case object MissingOutput extends GenerationReason
    private[cozy] final case class InvalidOutput(reason: String) extends GenerationReason
    private[cozy] final case class UnknownTimestamp(path: String) extends GenerationReason
    private[cozy] final case class DependencyNewer(path: String) extends GenerationReason
  }

  private[cozy] sealed trait UnavailableReason

  private[cozy] object UnavailableReason {
    private[cozy] final case class MissingProducer(path: String) extends UnavailableReason
    private[cozy] final case class MissingRequiredInput(path: String) extends UnavailableReason
    private[cozy] final case class InvalidRequiredInput(path: String, reason: String)
      extends UnavailableReason
    private[cozy] final case class MissingPrebuilt(path: String) extends UnavailableReason
    private[cozy] final case class InvalidPrebuilt(path: String, reason: String)
      extends UnavailableReason
  }

  private[cozy] def evaluate(request: Request): Decision =
    request.intent match {
      case Intent.ExplicitPrebuilt => _evaluate_prebuilt(request.output)
      case Intent.Generation => _evaluate_generation(request)
    }

  private def _evaluate_prebuilt(output: FileObservation): Decision =
    output.state match {
      case FileState.Valid(_) => Decision.Adopt(output.path)
      case FileState.Absent =>
        Decision.Unavailable(UnavailableReason.MissingPrebuilt(output.path))
      case FileState.Invalid(reason) =>
        Decision.Unavailable(UnavailableReason.InvalidPrebuilt(output.path, reason))
    }

  private def _evaluate_generation(request: Request): Decision = {
    val unavailable = request.inputs.collectFirst {
      case FileObservation(path, FileState.Absent) =>
        UnavailableReason.MissingRequiredInput(path)
      case FileObservation(path, FileState.Invalid(reason)) =>
        UnavailableReason.InvalidRequiredInput(path, reason)
    }

    unavailable.map(Decision.Unavailable).getOrElse {
      val generationreason =
        if (request.force) {
          Some(GenerationReason.Forced)
        } else {
          request.output.state match {
            case FileState.Absent => Some(GenerationReason.MissingOutput)
            case FileState.Invalid(reason) => Some(GenerationReason.InvalidOutput(reason))
            case FileState.Valid(None) =>
              Some(GenerationReason.UnknownTimestamp(request.output.path))
            case FileState.Valid(Some(outputtime)) =>
              request.inputs.collectFirst {
                case FileObservation(path, FileState.Valid(None)) =>
                  GenerationReason.UnknownTimestamp(path)
              }.orElse {
                request.inputs.collectFirst {
                  case FileObservation(path, FileState.Valid(Some(inputtime)))
                    if inputtime.compareTo(outputtime) > 0 =>
                    GenerationReason.DependencyNewer(path)
                }
              }
          }
        }

      generationreason
        .map(reason => _generate_or_unavailable(request, reason))
        .getOrElse(Decision.Reuse(request.output.path))
    }
  }

  private def _generate_or_unavailable(request: Request, reason: GenerationReason): Decision =
    request.producer match {
      case Some(producer) if producer.trim.nonEmpty => Decision.Generate(producer, reason)
      case _ => Decision.Unavailable(UnavailableReason.MissingProducer(request.output.path))
    }
}
