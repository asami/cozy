package cozy.document

/*
 * @since   Sep. 14, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectLocalBuild {
  private[cozy] final case class InputObservation(
    path: String,
    modifiedTime: Option[Long]
  )

  private[cozy] final case class OutputObservation(
    modifiedTime: Option[Long]
  )

  private[cozy] final case class FreshnessObservation(
    inputs: Vector[InputObservation],
    output: OutputObservation,
    force: Boolean
  )

  private[cozy] sealed trait FreshnessDecision

  private[cozy] object FreshnessDecision {
    private[cozy] final case class MissingRequiredInput(path: String) extends FreshnessDecision
    private[cozy] case object ForcedBuild extends FreshnessDecision
    private[cozy] case object MissingOutput extends FreshnessDecision
    private[cozy] final case class DependencyNewer(path: String) extends FreshnessDecision
    private[cozy] case object ReuseCurrentOutput extends FreshnessDecision
  }

  private[cozy] final case class TargetContract(
    id: String,
    targetDefinitionPath: String,
    sourceCategories: Vector[String],
    outputRoot: String
  )

  private[cozy] final case class UnknownTarget(targetId: String)
    extends IllegalArgumentException(s"unknown document-project local-build target: $targetId")

  private[cozy] val defaultTargetId: String = "document-structure-html"

  private val _document_source_categories = Vector(
    "config",
    "content/core.yaml",
    "content/<locale>/document.yaml",
    "content/<locale>/confirmation-vocabulary.yaml"
  )
  private val _article_source_categories = Vector("config", "index.dox")
  private val _target_contracts = Vector(
    TargetContract(
      "document-structure-html",
      "build/document-project-targets.yaml",
      _document_source_categories,
      "target/document-project/local-build/document-structure-html/"
    ),
    TargetContract(
      "document-reader-html",
      "build/document-project-targets.yaml",
      _document_source_categories,
      "target/document-project/local-build/document-reader-html/"
    ),
    TargetContract(
      "smartdox-article-html",
      "build/document-project-targets.yaml",
      _article_source_categories,
      "target/document-project/local-build/smartdox-article-html/"
    )
  )

  private[cozy] def targetContracts: Vector[TargetContract] = _target_contracts

  private[cozy] def evaluate(observation: FreshnessObservation): FreshnessDecision = {
    observation.inputs.collectFirst {
      case input if input.modifiedTime.isEmpty =>
        FreshnessDecision.MissingRequiredInput(input.path)
    }.getOrElse {
      if (observation.force) {
        FreshnessDecision.ForcedBuild
      } else {
        observation.output.modifiedTime match {
          case None => FreshnessDecision.MissingOutput
          case Some(outputtime) =>
            observation.inputs.collectFirst {
              case input if input.modifiedTime.exists(_ > outputtime) =>
                FreshnessDecision.DependencyNewer(input.path)
            }.getOrElse(FreshnessDecision.ReuseCurrentOutput)
        }
      }
    }
  }

  private[cozy] def select(targetId: Option[String]): TargetContract =
    _target_contracts.find(_.id == targetId.getOrElse(defaultTargetId)).getOrElse {
      throw UnknownTarget(targetId.getOrElse(defaultTargetId))
    }
}
