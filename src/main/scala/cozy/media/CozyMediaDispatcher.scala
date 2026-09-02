package cozy.media

import org.goldenport.RAISE
import cozy.publication.{CozyArticleMediaSiteCommand, CozyArticleMediaWipCommand}

/*
 * @since   Sep. 2, 2026
 * @version Sep. 2, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaDispatcher {
  def execute(args: List[String], runner: CozyMedia.ProcessRunner): Boolean =
    args match {
      case "media" :: "inspect" :: rest =>
        println(CozyMedia.inspect(CozyMedia.CommandConfig.create(rest)))
        true
      case "media" :: "plan" :: rest =>
        println(CozyMedia.plan(CozyMedia.CommandConfig.create(rest)))
        true
      case "media" :: "build" :: rest =>
        println(CozyMedia.build(CozyMedia.CommandConfig.create(rest), runner))
        true
      case "media" :: "verify" :: rest =>
        println(CozyMedia.verify(CozyMedia.CommandConfig.create(rest)))
        true
      case "media" :: "publish" :: rest =>
        println(CozyMedia.publish(CozyMedia.CommandConfig.create(rest, requireProfile = true)))
        true
      case "media" :: "slide" :: "validate" :: rest =>
        println(CozyMedia._slide_validate(CozyMedia.CommandConfig.create(rest)))
        true
      case "media" :: "slide" :: "plan" :: rest =>
        println(CozyMedia._slide_plan(CozyMedia.CommandConfig.create(rest)))
        true
      case "media" :: "slide" :: "build" :: rest =>
        println(CozyMedia._slide_build(CozyMedia.CommandConfig.create(rest), runner))
        true
      case "media" :: "slide" :: "verify" :: rest =>
        println(CozyMedia._slide_verify(CozyMedia.CommandConfig.create(rest)))
        true
      case "media" :: "cross-review" :: "build" :: rest =>
        println(CozyMediaCrossReview.build(CozyMediaCrossReview.BuildConfig.create(rest)))
        true
      case "media" :: "cross-review" :: "verify" :: rest =>
        println(CozyMediaCrossReview.verify(CozyMediaCrossReview.VerifyConfig.create(rest)))
        true
      case "media" :: "presentation" :: "migrate" :: rest =>
        println(CozyMediaPresentationMigration.execute(rest))
        true
      case "media" :: "visual-page" :: rest =>
        println(CozyVisualPage.execute(rest))
        true
      case "media" :: "explanation" :: rest =>
        println(CozyExplanation.execute(rest))
        true
      case "media" :: "review" :: "align" :: rest =>
        println(CozyMediaReviewState.executeAlign(rest))
        true
      case "media" :: "scaffold" :: "article" :: rest =>
        println(CozyMediaArticleScaffold.execute(rest))
        true
      case "media" :: "register-site" :: rest =>
        println(CozyArticleMediaSiteCommand.execute(rest))
        true
      case "media" :: "register-site-wip" :: rest =>
        println(CozyArticleMediaWipCommand.execute(rest))
        true
      case "media" :: other :: _ =>
        RAISE.invalidArgumentFault(s"Unsupported media command: $other")
      case _ =>
        false
    }
}
