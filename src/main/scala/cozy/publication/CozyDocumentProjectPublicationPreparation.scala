package cozy.publication

import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}
import cozy.document.CozyDocumentProject
import scala.util.control.NonFatal

/*
 * @since   Oct.  1, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectPublicationPreparation {
  final case class Config(
    registration: CozyDocumentProjectPublicationTarget.ProjectRegistrationConfig,
    taskRoot: Path,
    destination: Path
  )

  final case class Prepared(root: Path, exportRoot: Path, registryPaths: Vector[Path])

  private val _export_files = Vector(
    "manifest.yaml", "work-products/article-review-html/article-review.html"
  )

  def prepare(config: Config): Prepared = {
    if (config == null) _invalid("publication preparation configuration is required")
    val original = CozyDocumentProjectPublicationTarget.planProjectRegistration(config.registration)
    val taskroot = _task_root(config.taskRoot)
    val destination = _destination(config.destination, taskroot)
    _require_disjoint(taskroot, destination, original)
    _generated_directory(destination)
    val exportroot = destination.resolve("export")
    _generated_directory(exportroot)
    _generated_directory(exportroot.resolve("work-products"))
    _generated_directory(exportroot.resolve("work-products/article-review-html"))
    _export_files.foreach { relative =>
      val copied = exportroot.resolve(relative)
      if (Files.isSymbolicLink(copied)) _invalid("publication preparation output must not be a symlink")
      Files.copy(original.registration.export.bundleRoot.resolve(relative), copied, StandardCopyOption.REPLACE_EXISTING)
    }
    val media = original.registration.siteBinding.config
    CozyArticleMediaSiteCommand.execute(CozyArticleMediaSiteCommand.Config(
      descriptorFile = media.descriptorFile,
      publicationRoot = destination,
      target = media.target,
      dryRun = false,
      siteRoot = media.siteRoot,
      siteConfig = media.siteConfig
    ))
    val registrynames = CozyArticleMediaRegistry.load(destination).bundleNames.sorted
      .map(name => s"$name.json")
    Prepared(destination, exportroot, registrynames.map(destination.resolve(_)))
  }

  private def _task_root(path: Path): Path = {
    val normalized = _normalized(path, "task root")
    if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _invalid("publication preparation task root must be an existing direct non-symlink directory")
    val real = try normalized.toRealPath() catch {
      case NonFatal(_) => _invalid("publication preparation task root cannot be resolved")
    }
    if (real != normalized) _invalid("publication preparation task root must be canonical")
    normalized
  }

  private def _destination(path: Path, taskroot: Path): Path = {
    val normalized = _normalized(path, "destination")
    if (normalized.getParent != taskroot)
      _invalid("publication preparation destination must be a direct child of task root")
    if (Files.isSymbolicLink(normalized) ||
      (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)))
      _invalid("publication preparation destination must be a direct directory")
    normalized
  }

  private def _require_disjoint(
    taskroot: Path,
    destination: Path,
    plan: CozyDocumentProjectPublicationTarget.ProjectRegistrationPlan
  ): Unit = {
    val registration = plan.registration
    val binding = registration.siteBinding
    val context = binding.context
    val projectpaths = context.project.toVector.flatMap { project =>
      Vector(project.root.path, project.root.identity, project.marker.path, project.marker.identity)
    }
    val configurationpaths = context.layers.flatMap(_.files).flatMap(file => Vector(file.path, file.identity))
    val profilepaths = binding.effectiveProfile.configuration.toVector.flatMap { profile =>
      Vector(profile.sourcePath, profile.baseRoot, profile.resolvedRoot) ++
        profile.resolvedidentity.toVector.flatMap(identity => Vector(identity.path, identity.identity))
    }
    val sitepaths = registration.siteContext.toVector.flatMap(site => Vector(site.root, site.config, site.source))
    val authorities = Vector(
      plan.config.project, registration.export.bundleRoot,
      binding.descriptorRoot, binding.descriptorRootIdentity,
      binding.descriptorEvidence.path, binding.descriptorEvidence.identity,
      binding.profileRoot, binding.profileRootIdentity, binding.effectiveProfile.resolvedRoot
    ) ++ projectpaths ++ configurationpaths ++ profilepaths ++ sitepaths
    authorities.foreach { authority =>
      val normalized = authority.toAbsolutePath.normalize()
      if (Vector(taskroot, destination).exists(output => output.startsWith(normalized) || normalized.startsWith(output)))
        _invalid(s"publication preparation output overlaps original authority: $normalized")
    }
  }

  private def _normalized(path: Path, label: String): Path = {
    if (path == null) _invalid(s"publication preparation $label is required")
    try path.toAbsolutePath.normalize() catch {
      case NonFatal(_) => _invalid(s"publication preparation $label path is invalid")
    }
  }

  private def _generated_directory(path: Path): Unit = {
    if (Files.isSymbolicLink(path) ||
      (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)))
      _invalid("publication preparation output directory must be direct and non-symlink")
    Files.createDirectories(path)
  }

  private def _invalid(message: String): Nothing = CozyDocumentProject._failure("DP-OP-001", message)
}
