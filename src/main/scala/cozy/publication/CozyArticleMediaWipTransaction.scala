package cozy.publication

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.collection.mutable.ArrayBuffer
import org.goldenport.RAISE

/*
 * @since   Aug. 12, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaWipTransaction {
  final case class Hooks(afterinstall: Path => Unit = _ => ())

  final case class Result(
    plan: CozyArticleMediaWipBinding.Plan,
    dryRun: Boolean,
    installedPaths: Vector[Path],
    registry: CozyArticleMediaRegistry.WipReadOnlyPlan,
    merge: Option[CozyArticleMediaRegistry.TransactionMergeResult]
  )

  def execute(plan: CozyArticleMediaWipBinding.Plan, dryRun: Boolean): Result =
    execute(plan, dryRun, Hooks())

  private[publication] def execute(
    plan: CozyArticleMediaWipBinding.Plan,
    dryRun: Boolean,
    hooks: Hooks
  ): Result = {
    if (plan == null || hooks == null || hooks.afterinstall == null)
      _invalid("Article-media WIP transaction inputs must be defined")
    _validate_roots(plan)
    if (dryRun)
      Result(plan, dryRun = true, Vector.empty, plan.registry, None)
    else {
      val installed = ArrayBuffer.empty[Path]
      val merged = CozyArticleMediaRegistry.transactionExisting(plan.publicationRoot.identity) { transaction =>
        transaction.mergeWip(
          _updates(plan),
          beforeReplace = () => {
            plan.candidates.collect {
              case candidate if candidate.evidence.isInstanceOf[CozyArticleMediaWipBinding.VideoEvidence] =>
                candidate -> candidate.evidence.asInstanceOf[CozyArticleMediaWipBinding.VideoEvidence]
            }.sortBy(_._2.destination.toString).foreach { case (_, evidence) =>
              Files.copy(evidence.source.path, evidence.destination, StandardCopyOption.REPLACE_EXISTING)
              installed += evidence.destination
              hooks.afterinstall(evidence.destination)
            }
          },
          afterReplace = result => _validate_registry_result(result, plan)
        )
      }
      Result(plan, dryRun = false, installed.toVector, plan.registry, Some(merged))
    }
  }

  private def _updates(plan: CozyArticleMediaWipBinding.Plan): Vector[CozyArticleMediaRegistry.WipRoleUpdate] =
    plan.candidates.map(candidate =>
      CozyArticleMediaRegistry.WipRoleUpdate(plan.articleIdentity, candidate.variant, candidate.integrity)
    )

  private def _validate_registry_result(
    result: CozyArticleMediaRegistry.TransactionMergeResult,
    plan: CozyArticleMediaWipBinding.Plan
  ): Unit = {
    if (result == null || result.snapshot == null)
      _invalid("Article-media WIP registry result must be defined")
    val expected = plan.registry.articles.flatMap(article =>
      article.strict.entryPath +: article.integrities.map(_.entryPath)
    ).distinct.sorted
    if (!expected.forall(result.entryPaths.contains))
      _invalid("Article-media WIP registry result is incomplete")
  }

  private def _validate_roots(plan: CozyArticleMediaWipBinding.Plan): Unit = {
    val publication = plan.publicationRoot.identity
    val website = plan.websiteRoot.identity
    val project = plan.projectRoot.identity
    val home = Path.of(sys.props.getOrElse("user.home", "")).toAbsolutePath.normalize()
    Vector(publication -> "publication root", website -> "website root").foreach { case (root, label) =>
      if (root.getParent == null || root == home || project == root || project.startsWith(root))
        _invalid(s"Article-media WIP $label is too broad: $root")
    }
    if (publication == website || publication.startsWith(website) || website.startsWith(publication))
      _invalid("Article-media WIP publication and website roots must be independent")
    if (publication.startsWith(project))
      _invalid("Article-media WIP publication root must be external to the project root")
  }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
