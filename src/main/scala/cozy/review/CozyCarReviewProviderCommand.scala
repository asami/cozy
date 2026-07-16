package cozy.review

import java.nio.file.{Files, Path}

import play.api.libs.json.Json
/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Narrow command boundary for a CBD-owned CAR Review invocation.
 *
 * This command knows only the neutral provider request and evidence bundle.
 * Its caller supplies project-root and provider-version through a fixed local
 * command template; Cozy never imports or calls CBD Support.
 */
object CozyCarReviewProviderCommand {
  def execute(args: List[String], providerRequest: String): Either[String, String] =
    _config(args).flatMap { case (projectroot, providerversion) =>
      CozyCarReviewProvider.execute(providerRequest, projectroot, providerversion).map(Json.stringify)
    }

  private def _config(args: List[String]): Either[String, (Path, String)] =
    args match {
      case "--project-root" :: root :: "--provider-version" :: version :: "--request-stdin" :: Nil =>
        val path = Path.of(root).toAbsolutePath.normalize()
        if (!Files.isDirectory(path)) Left("provider-project-root-invalid")
        else if (version.trim.isEmpty) Left("provider-version-invalid")
        else Right(path -> version.trim)
      case _ =>
        Left("provider-command-arguments-invalid")
    }
}
