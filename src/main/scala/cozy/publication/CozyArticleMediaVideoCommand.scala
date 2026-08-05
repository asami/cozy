package cozy.publication

import java.nio.file.{Files, Path}
import org.goldenport.RAISE
import cozy.video.CozyVideoPublisher

/*
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/* The command boundary owns no discovery.  It receives resolved immutable
 * plans, renders them after shared preflight, and commits admission through
 * one registry transaction. */
private[cozy] object CozyArticleMediaVideoCommand {
  final case class PlannedCandidate(
    articleIdentity: String,
    locale: String,
    role: String,
    video: String,
    version: String,
    destination: Path,
    destinationState: String,
    forceRequested: Boolean,
    owner: String
  )

  /* The identical immutable value used by dry-run reporting and the real
   * command.  It contains only render-independent destination and ownership
   * facts; output SHA/evidence remains a post-render responsibility. */
  final case class Preflight(
    publicationRoot: Path,
    repositoryRoot: Path,
    plans: Vector[CozyVideoPublisher.PublicationPlan],
    candidates: Vector[PlannedCandidate]
  )

  def preflight(
    publicationRoot: Path,
    repositoryRoot: Path,
    plans: Vector[CozyVideoPublisher.PublicationPlan]
  ): Preflight = {
    if (publicationRoot == null || repositoryRoot == null)
      _invalid("Article-media video command roots must be defined")
    if (plans == null)
      _invalid("Article-media video command plans must be defined")
    val publicationroot = publicationRoot.toAbsolutePath.normalize()
    val repositoryroot = repositoryRoot.toAbsolutePath.normalize()
    val ordered = _ordered_plans(plans).map(CozyVideoPublisher.validatePublicationPlan)
    _validate_plan_identities(ordered)
    val bound = ordered.flatMap { plan =>
      plan.video.articleMedia.map(binding => plan -> binding)
    }
    val intents = bound.map { case (_, binding) =>
      CozyArticleMediaRegistry.RoleIntent(binding.articleIdentity, binding.locale, CozyArticleMediaIntegrity.Role.Video)
    }
    val ownership =
      if (intents.isEmpty) Vector.empty
      else CozyArticleMediaRegistry.validateReadOnlyRoleIntents(publicationroot, intents).owners
    val owners = ownership.map(value =>
      (value.intent.articleIdentity, value.intent.locale, value.intent.role.name) -> value.owner
    ).toMap
    val candidates = bound.map { case (plan, binding) =>
      if (!plan.target.startsWith(repositoryroot))
        _invalid(s"Article-media video destination escapes the configured repository root: ${plan.target}")
      val owner = owners.getOrElse(
        (binding.articleIdentity, binding.locale, CozyArticleMediaIntegrity.Role.Video.name),
        _invalid(s"Article-media video ownership preflight is missing: ${binding.articleIdentity} [${binding.locale}, video]")
      )
      PlannedCandidate(
        binding.articleIdentity,
        binding.locale,
        CozyArticleMediaIntegrity.Role.Video.name,
        plan.video.name,
        plan.video.version,
        plan.target,
        plan.destination.state,
        plan.destination.forceRequested,
        owner
      )
    }.sortBy(x => (x.articleIdentity, x.locale, x.role, x.video, x.version, x.destination.toString))
    Preflight(publicationroot, repositoryroot, ordered, candidates)
  }

  /* The one-stop command supplies only already resolved plans.  It renders
   * after command-wide preflight, then admits the exact prepared artifacts
   * inside this one registry transaction. */
  def publish(
    publicationRoot: Path,
    repositoryRoot: Path,
    plans: Vector[CozyVideoPublisher.PublicationPlan],
    voicevox: cozy.video.CozyVideo.VoicevoxClient,
    runner: cozy.video.CozyVideo.VideoProcessRunner
  ): Vector[CozyVideoPublisher.PublishVideoResult] = {
    publish(preflight(publicationRoot, repositoryRoot, plans), voicevox, runner)
  }

  def publish(
    planned: Preflight,
    voicevox: cozy.video.CozyVideo.VoicevoxClient,
    runner: cozy.video.CozyVideo.VideoProcessRunner
  ): Vector[CozyVideoPublisher.PublishVideoResult] = {
    if (planned == null || voicevox == null || runner == null)
      _invalid("Article-media video command preflight and renderer dependencies must be defined")
    if (planned.plans.isEmpty)
      Vector.empty
    else {
      /* The shared registry lock begins before the frozen roots are
       * revalidated.  This keeps source revalidation, deterministic workspace
       * use, rendering, post-render plan validation, admission, and metadata
       * replacement in one producer transaction. */
      Files.createDirectories(planned.publicationRoot)
      CozyArticleMediaRegistry.transaction(planned.publicationRoot) { transaction =>
        val current = preflight(planned.publicationRoot, planned.repositoryRoot, planned.plans)
        val prepared = current.plans.map { plan =>
          CozyVideoPublisher.preparePublication(CozyVideoPublisher.renderPublication(plan, voicevox, runner))
        }
        val publications = prepared.map(_.result.metadataPublication.getOrElse(
          _invalid("Article-media video command metadata publication plan is missing")
        ))
        var admitted = Vector.empty[CozyVideoPublisher.PublishVideoResult]
        transaction.publish(publications) { projected =>
          prepared.flatMap { publication =>
            CozyArticleMediaVideoRegistration.prepare(publication, projected, current.repositoryRoot).map(_.roleUpdate)
          }
        } {
          () => admitted = prepared.map(CozyVideoPublisher.admitPublication)
        }
        admitted
      }
    }
  }

  def publish(
    publicationRoot: Path,
    repositoryRoot: Path,
    results: Vector[CozyVideoPublisher.PublishVideoResult]
  ): Vector[CozyVideoPublisher.PublishVideoResult] = {
    if (publicationRoot == null || repositoryRoot == null)
      _invalid("Article-media video command roots must be defined")
    if (results == null)
      _invalid("Article-media video command prepared results must be defined")
    if (results.isEmpty)
      Vector.empty
    else {
      val ordered = results.zipWithIndex.map { case (result, index) =>
        if (result == null || result.video == null)
          _invalid(s"Article-media video command prepared result must be defined: $index")
        val plan = result.metadataPublication.getOrElse(
          _invalid(s"Article-media video command metadata publication plan is missing: ${result.video.name}")
        )
        if (plan.name != result.video.name)
          _invalid(s"Article-media video command result/bundle identity mismatch: ${result.video.name} != ${plan.name}")
        result
      }.sortBy(_.video.name)
      Files.createDirectories(publicationRoot)
      CozyArticleMediaRegistry.transaction(publicationRoot) { transaction =>
        transaction.publish(ordered.map(_.metadataPublication.get)) { projected =>
          ordered.flatMap { result =>
            CozyArticleMediaVideoRegistration.prepare(CozyArticleMediaVideoRegistration.Input(
              publication = result,
              snapshot = projected,
              repositoryRoot = repositoryRoot
            )).map(_.roleUpdate)
          }
        }(() => ())
      }
      ordered
    }
  }

  private def _ordered_plans(
    plans: Vector[CozyVideoPublisher.PublicationPlan]
  ): Vector[CozyVideoPublisher.PublicationPlan] =
    plans.zipWithIndex.map { case (plan, index) =>
      if (plan == null || plan.video == null)
        _invalid(s"Article-media video command publication plan must be defined: $index")
      plan
    }.sortBy(_.video.name)

  private def _validate_plan_identities(plans: Vector[CozyVideoPublisher.PublicationPlan]): Unit = {
    plans.groupBy(_.video.name).toVector.sortBy(_._1).collectFirst {
      case (name, values) if values.size > 1 => name
    }.foreach { name =>
      _invalid(s"Duplicate article-media video publication plan: $name")
    }
    plans.foreach { plan =>
      if (plan.video.version.trim.isEmpty)
        _invalid(s"Article-media video publication version must be non-empty: ${plan.video.name}")
    }
  }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
