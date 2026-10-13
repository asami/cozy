package cozy.media

import org.goldenport.RAISE
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}
import scala.collection.JavaConverters._
import CozyMedia._

/*
 * @since   Aug. 25, 2026
 *  version Sep.  8, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaPublicationTransaction {
  def commit(
    prepared: Vector[PreparedPublication],
    beforeFirstInstall: () => Unit
  ): Vector[PublicationResult] = {
    val publications = Option(prepared).getOrElse(
      RAISE.invalidArgumentFault("Prepared media publications must not be null")
    )
    _validate_prepared_publications(publications)
    val ordered = _ordered_publications(publications)
    beforeFirstInstall()
    ordered.map { publication =>
      _ensure_destination_parent(publication)
      if (!_is_direct_regular_file(publication.publishablePath))
        RAISE.invalidArgumentFault(s"Media publishable source must be a direct regular non-symlink file: ${publication.publishablePath}")
      _validate_destination_file(publication.destination)
      val outcome =
        if (Files.exists(publication.destination, LinkOption.NOFOLLOW_LINKS)) PublicationOutcome.Replaced
        else PublicationOutcome.Created
      try Files.copy(publication.publishablePath, publication.destination, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case e: java.io.IOException =>
          RAISE.invalidArgumentFault(s"Media publication failed: ${e.getMessage}")
      }
      PublicationResult(publication, outcome)
    }
  }

  def prepare(
    plan: Plan,
    candidates: Vector[ResolvedResource],
    profile: String,
    target: Option[String]
  ): Vector[PreparedPublication] = {
    if (!_is_direct_regular_file(plan.descriptorFile))
      RAISE.invalidArgumentFault(s"Media descriptor must be a direct regular non-symlink file: ${plan.descriptorFile}")
    val effectiveprofile = effectiveProfile(plan, profile)
    val profileroot = _bind_profile_root(effectiveprofile.resolvedRoot)
    val publications = candidates.map { resolved =>
      val publishable = resolved.output.orElse(resolved.source).getOrElse(
        RAISE.invalidArgumentFault(s"Media resource has no publishable file: ${resolved.resource.id}")
      )
      if (!_is_direct_regular_file(publishable))
        RAISE.invalidArgumentFault(s"Media publishable source must be a direct regular non-symlink file: $publishable")
      val destination = resolved.publications.getOrElse(profile,
        RAISE.invalidArgumentFault(s"Media resource has no publication for profile $profile: ${resolved.resource.id}")
      )
      _validate_destination(destination, profileroot)
      _validate_destination_file(destination)
      PreparedPublication(
        plan.descriptorFile,
        plan.descriptorRoot,
        plan.descriptor,
        resolved.resource,
        target,
        plan.context,
        plan.siteContext,
        effectiveprofile,
        profile,
        profileroot,
        publishable,
        destination
      )
    }
    _validate_unique_destinations(publications)
    _ordered_publications(publications)
  }

  def prepareLegacy(
    plan: Plan,
    candidates: Vector[ResolvedResource],
    profile: String,
    target: Option[String]
  ): Vector[PreparedPublication] = {
    val effectiveprofile = effectiveProfile(plan, profile)
    val profileroot = effectiveprofile.resolvedRoot
    if (effectiveprofile.externalRoot && !Files.exists(profileroot, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Media external publication profile root must already exist: $profileroot")
    if (Files.exists(profileroot, LinkOption.NOFOLLOW_LINKS))
      prepare(plan, candidates, profile, target)
    else {
      val destinations = candidates.map { resolved =>
        val publishable = resolved.output.orElse(resolved.source).getOrElse(
          RAISE.invalidArgumentFault(s"Media resource has no publishable file: ${resolved.resource.id}")
        )
        if (!_is_direct_regular_file(publishable))
          RAISE.invalidArgumentFault(s"Media publishable source must be a direct regular non-symlink file: $publishable")
        val destination = resolved.publications.getOrElse(profile,
          RAISE.invalidArgumentFault(s"Media resource has no publication for profile $profile: ${resolved.resource.id}")
        )
        _validate_lexical_destination(destination, profileroot)
        destination
      }
      destinations.groupBy(identity).collectFirst { case (destination, values) if values.size > 1 => destination }.foreach { destination =>
        RAISE.invalidArgumentFault(s"Media publication destinations must be unique: $destination")
      }
      Vector.empty
    }
  }

  private def _ordered_publications(publications: Vector[PreparedPublication]): Vector[PreparedPublication] =
    publications.sortBy(publication => (
      publication.descriptorFile.toString,
      publication.resource.id,
      publication.profile,
      publication.destination.toString
    ))

  private def _validate_prepared_publications(publications: Vector[PreparedPublication]): Unit = {
    if (publications.isEmpty)
      RAISE.invalidArgumentFault("Prepared media publications must not be empty")
    publications.foreach(_validate_prepared_publication)
    _validate_unique_destinations(publications)
  }

  private def _validate_prepared_publication(publication: PreparedPublication): Unit = {
    if (publication == null)
      RAISE.invalidArgumentFault("Prepared media publication must not be null")
    if (publication.descriptorFile == null || publication.descriptorRoot == null || publication.descriptor == null || publication.resource == null)
      RAISE.invalidArgumentFault("Prepared media publication descriptor evidence must not be null")
    if (publication.profile == null || publication.profile.trim.isEmpty || publication.context == null || publication.siteContext == null || publication.effectiveProfile == null || publication.profileRoot == null || publication.publishablePath == null || publication.destination == null)
      RAISE.invalidArgumentFault("Prepared media publication path evidence must not be null or empty")
    if (publication.resource.id == null || publication.resource.id.trim.isEmpty)
      RAISE.invalidArgumentFault("Prepared media publication resource id must not be empty")
    if (publication.descriptor.schema == null || publication.descriptor.knowledge == null || publication.descriptor.resources == null || publication.descriptor.profiles == null)
      RAISE.invalidArgumentFault("Prepared media publication descriptor structure must not be null")
    if (publication.resource.publications == null || publication.resource.language == null || publication.resource.role == null || publication.resource.source == null || publication.resource.output == null)
      RAISE.invalidArgumentFault("Prepared media publication resource structure must not be null")
    if (publication.descriptor.schema != "cozy.media.v1" || !publication.descriptor.resources.contains(publication.resource))
      RAISE.invalidArgumentFault("Prepared media publication descriptor evidence is invalid")
    if (publication.descriptorFile != publication.descriptorFile.toAbsolutePath.normalize() || publication.descriptorRoot != publication.descriptorRoot.toAbsolutePath.normalize() || Option(publication.descriptorFile.getParent).forall(_ != publication.descriptorRoot))
      RAISE.invalidArgumentFault("Prepared media publication descriptor paths are invalid")
    if (publication.effectiveProfile.id != publication.profile || publication.effectiveProfile.resolvedRoot != publication.profileRoot)
      RAISE.invalidArgumentFault("Prepared media publication effective profile selection is invalid")
    _validate_destination(publication.destination, publication.profileRoot)
  }

  private def _validate_unique_destinations(publications: Vector[PreparedPublication]): Unit = {
    val duplicates = publications.groupBy { publication =>
      if (publication == null || publication.destination == null)
        RAISE.invalidArgumentFault("Prepared media publication destination must not be null")
      publication.destination.toAbsolutePath.normalize()
    }.collect {
      case (destination, values) if values.size > 1 => destination
    }.toVector.sortBy(_.toString)
    duplicates.headOption.foreach(destination =>
      RAISE.invalidArgumentFault(s"Media publication destinations must be unique: $destination")
    )
  }

  private def _validate_destination(destination: Path, profileroot: Path): Unit = {
    val normalizedroot = _bind_profile_root(profileroot)
    val normalizeddestination = destination.toAbsolutePath.normalize()
    if (normalizeddestination != destination || !normalizeddestination.startsWith(normalizedroot) || normalizeddestination == normalizedroot)
      RAISE.invalidArgumentFault(s"Media publication path escapes profile root: $destination")
    val components = normalizedroot.relativize(normalizeddestination).iterator.asScala.toVector
    components.dropRight(1).foldLeft(normalizedroot) { (current, component) =>
      val next = current.resolve(component)
      if (Files.exists(next, LinkOption.NOFOLLOW_LINKS))
        _validate_direct_directory(next, "Media publication destination parent")
      next
    }
    ()
  }

  private def _validate_lexical_destination(destination: Path, profileroot: Path): Unit = {
    val normalizedroot = profileroot.toAbsolutePath.normalize()
    val normalizeddestination = destination.toAbsolutePath.normalize()
    if (normalizedroot != profileroot || normalizeddestination != destination || !normalizeddestination.startsWith(normalizedroot) || normalizeddestination == normalizedroot)
      RAISE.invalidArgumentFault(s"Media publication path escapes profile root: $destination")
  }

  private def _bind_profile_root(profileroot: Path): Path = {
    val normalizedroot = Option(profileroot).map(_.toAbsolutePath.normalize()).getOrElse(
      RAISE.invalidArgumentFault("Media publication profile root must not be null")
    )
    if (normalizedroot != profileroot)
      RAISE.invalidArgumentFault(s"Media publication profile root must be normalized: $profileroot")
    _validate_direct_directory(normalizedroot, "Media publication profile root")
    normalizedroot
  }

  private def _validate_direct_directory(path: Path, label: String): Unit = {
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"$label must be an existing direct non-symlink directory: $path")
    val realpath =
      try path.toRealPath()
      catch {
        case e: java.io.IOException => RAISE.invalidArgumentFault(s"$label cannot be resolved: ${e.getMessage}")
      }
    if (realpath != path.toAbsolutePath.normalize())
      RAISE.invalidArgumentFault(s"$label must not be a lexical or real-path alias: $path")
  }

  private def _validate_destination_file(destination: Path): Unit = {
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !_is_direct_regular_file(destination))
      RAISE.invalidArgumentFault(s"Media publication destination must be absent or a direct regular non-symlink file: $destination")
  }

  private def _is_direct_regular_file(path: Path): Boolean =
    path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  def createAndBindLegacyProfileRoot(plan: Plan, profile: String): Path = {
    val effectiveprofile = effectiveProfile(plan, profile)
    val profileroot = effectiveprofile.resolvedRoot
    if (effectiveprofile.externalRoot && !Files.exists(profileroot, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Media external publication profile root must already exist: $profileroot")
    if (Files.exists(profileroot, LinkOption.NOFOLLOW_LINKS))
      _bind_profile_root(profileroot)
    else {
      val normalizedroot = profileroot.toAbsolutePath.normalize()
      val ancestry = Iterator.iterate(normalizedroot)(_.getParent).takeWhile(_ != null).toVector.reverse
      var current = ancestry.head
      _validate_direct_directory(current, "Media legacy publication root ancestor")
      ancestry.tail.foreach { component =>
        current = component
        if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
          try Files.createDirectory(current)
          catch {
            case _: java.nio.file.FileAlreadyExistsException => ()
          }
        }
        _validate_direct_directory(current, "Media legacy publication root")
      }
      _bind_profile_root(normalizedroot)
    }
  }

  private def _ensure_destination_parent(publication: PreparedPublication): Unit = {
    val root = _bind_profile_root(publication.profileRoot)
    val relative = root.relativize(publication.destination)
    val components = relative.iterator.asScala.toVector
    if (components.isEmpty)
      RAISE.invalidArgumentFault(s"Media publication destination has no leaf: ${publication.destination}")
    components.dropRight(1).foldLeft(root) { (current, component) =>
      val next = current.resolve(component)
      if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
        try Files.createDirectory(next)
        catch {
          case _: java.nio.file.FileAlreadyExistsException => ()
        }
      }
      _validate_direct_directory(next, "Media publication destination parent")
      next
    }
    ()
  }

}
