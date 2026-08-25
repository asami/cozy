package cozy.media

import org.goldenport.RAISE
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, StandardCopyOption, StandardOpenOption}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal
import CozyMedia._

/*
 * @since   Aug. 25, 2026
 * @version Aug. 25, 2026
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
    val temporaries = ArrayBuffer.empty[Path]
    try {
      beforeFirstInstall()
      ordered.foreach(CozyMediaReceipt.requirePreparedInputSet)
      ordered.map { publication =>
        val outcome = publication.disposition match {
          case PublicationDisposition.Reuse =>
            PublicationOutcome.Reused
          case PublicationDisposition.Create =>
            _install_publication(publication, replace = false, temporaries)
            PublicationOutcome.Created
          case PublicationDisposition.Replace =>
            _install_publication(publication, replace = true, temporaries)
            PublicationOutcome.Replaced
        }
        PublicationResult(publication, outcome)
      }
    } finally {
      temporaries.foreach { path =>
        try Files.deleteIfExists(path)
        catch {
          case NonFatal(_) => ()
        }
      }
    }
  }

  def prepare(
    plan: Plan,
    candidates: Vector[ResolvedResource],
    profile: String,
    target: Option[String],
    force: Boolean
  ): Vector[PreparedPublication] = {
    if (!_is_direct_regular_file(plan.descriptorFile))
      RAISE.invalidArgumentFault(s"Media descriptor must be a direct regular non-symlink file: ${plan.descriptorFile}")
    val descriptorhash = _sha256(plan.descriptorFile)
    val inputsethash = CozyMediaReceipt.capture(plan).inputSetSha256
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
      val destinationidentity = _destination_identity(destination, profileroot)
      val sourcehash = _sha256(publishable)
      val destinationstate = _destination_state(destination)
      val disposition = _publication_disposition(sourcehash, destinationstate, force, destination)
      PreparedPublication(
        plan.descriptorFile,
        plan.descriptorRoot,
        plan.descriptor,
        descriptorhash,
        inputsethash,
        resolved.resource,
        target,
        plan.context,
        effectiveprofile,
        profile,
        profileroot,
        profileroot,
        publishable,
        destination,
        destinationidentity,
        sourcehash,
        destinationstate,
        force,
        disposition
      )
    }
    _validate_unique_destinations(publications)
    _ordered_publications(publications)
  }

  def prepareLegacy(
    plan: Plan,
    candidates: Vector[ResolvedResource],
    profile: String,
    target: Option[String],
    force: Boolean
  ): Vector[PreparedPublication] = {
    val effectiveprofile = effectiveProfile(plan, profile)
    val profileroot = effectiveprofile.resolvedRoot
    if (effectiveprofile.externalRoot && !Files.exists(profileroot, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Media external publication profile root must already exist: $profileroot")
    if (Files.exists(profileroot, LinkOption.NOFOLLOW_LINKS))
      prepare(plan, candidates, profile, target, force)
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
    publications.groupBy(publication => (publication.descriptorFile, publication.profile, publication.target, publication.force)).foreach {
      case ((descriptorfile, profile, target, force), values) =>
        val plan = CozyMedia.resolvePlan(CommandConfig(descriptorfile, target = target, profile = Some(profile)))
        val candidates = _selected(plan, target).filter(_.publications.contains(profile))
        if (candidates.isEmpty)
          RAISE.invalidArgumentFault(s"Prepared media publication candidate set has changed: $descriptorfile")
        val reconstructed = prepare(plan, candidates, profile, target, force)
        if (_ordered_publications(values) != reconstructed)
          RAISE.invalidArgumentFault(s"Prepared media publication candidate vector has changed: $descriptorfile")
    }
  }

  private def _validate_prepared_publication(publication: PreparedPublication): Unit = {
    if (publication == null)
      RAISE.invalidArgumentFault("Prepared media publication must not be null")
    if (publication.descriptorFile == null || publication.descriptorRoot == null || publication.descriptor == null || publication.resource == null)
      RAISE.invalidArgumentFault("Prepared media publication descriptor evidence must not be null")
    if (publication.profile == null || publication.profile.trim.isEmpty || publication.context == null || publication.effectiveProfile == null || publication.profileRoot == null || publication.profileRootIdentity == null || publication.publishablePath == null || publication.destination == null || publication.destinationIdentity == null)
      RAISE.invalidArgumentFault("Prepared media publication path evidence must not be null or empty")
    if (publication.destinationState == null || publication.disposition == null)
      RAISE.invalidArgumentFault("Prepared media publication state must not be null")
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
    if (!_is_direct_regular_file(publication.descriptorFile) || !_is_sha256(publication.descriptorSha256) || _sha256(publication.descriptorFile) != publication.descriptorSha256)
      RAISE.invalidArgumentFault(s"Prepared media descriptor has changed: ${publication.descriptorFile}")
    if (!_is_sha256(publication.inputSetSha256))
      RAISE.invalidArgumentFault("Prepared media publication input-set identity is invalid")
    val currentplan = CozyMedia.resolvePlan(CommandConfig(publication.descriptorFile, target = publication.target, profile = Some(publication.profile)))
    val expectedprofile = CozyMedia.effectiveProfile(currentplan, publication.profile)
    if (publication.effectiveProfile != expectedprofile)
      RAISE.invalidArgumentFault("Prepared media publication effective profile evidence has changed")
    val expectedroot = _bind_profile_root(expectedprofile.resolvedRoot)
    if (expectedroot != publication.profileRoot || expectedroot != publication.profileRootIdentity)
      RAISE.invalidArgumentFault("Prepared media publication profile root has changed")
    val currentresource = currentplan.resources.find(_.resource.id == publication.resource.id).getOrElse(
      RAISE.invalidArgumentFault(s"Prepared media publication resource has changed: ${publication.resource.id}")
    )
    val expectedpublishable = currentresource.output.orElse(currentresource.source).getOrElse(
      RAISE.invalidArgumentFault(s"Prepared media publication has no publishable path: ${publication.resource.id}")
    )
    if (expectedpublishable != publication.publishablePath)
      RAISE.invalidArgumentFault("Prepared media publication source path is invalid")
    val expecteddestination = currentresource.publications.getOrElse(publication.profile,
      RAISE.invalidArgumentFault(s"Prepared media publication has no profile destination: ${publication.resource.id}")
    )
    if (expecteddestination != publication.destination)
      RAISE.invalidArgumentFault("Prepared media publication destination path is invalid")
    if (_destination_identity(publication.destination, publication.profileRoot) != publication.destinationIdentity)
      RAISE.invalidArgumentFault("Prepared media publication destination identity has changed")
    if (!_is_direct_regular_file(publication.publishablePath))
      RAISE.invalidArgumentFault(s"Prepared media source is no longer a direct regular non-symlink file: ${publication.publishablePath}")
    if (!_is_sha256(publication.sourceSha256) || _sha256(publication.publishablePath) != publication.sourceSha256)
      RAISE.invalidArgumentFault(s"Prepared media source has changed: ${publication.publishablePath}")
    CozyMediaReceipt.requirePreparedInputSet(publication)
    val actualstate = _destination_state(publication.destination)
    if (actualstate != publication.destinationState)
      RAISE.invalidArgumentFault(s"Prepared media destination has changed: ${publication.destination}")
    val expected = _publication_disposition(publication.sourceSha256, publication.destinationState, publication.force, publication.destination)
    if (expected != publication.disposition)
      RAISE.invalidArgumentFault(s"Prepared media disposition is invalid: ${publication.destination}")
  }

  private def _validate_unique_destinations(publications: Vector[PreparedPublication]): Unit = {
    val duplicates = publications.groupBy { publication =>
      if (publication == null || publication.destination == null || publication.destinationIdentity == null)
        RAISE.invalidArgumentFault("Prepared media publication destination must not be null")
      publication.destinationIdentity
    }.collect {
      case (destination, values) if values.size > 1 => destination
    }.toVector.sortBy(_.toString)
    duplicates.headOption.foreach(destination =>
      RAISE.invalidArgumentFault(s"Media publication destinations must be unique: $destination")
    )
  }

  private def _destination_identity(destination: Path, profileroot: Path): Path = {
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
    normalizedroot.resolve(normalizedroot.relativize(normalizeddestination)).normalize()
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

  private def _destination_state(destination: Path): DestinationState = {
    if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
      DestinationState.Absent
    else if (_is_direct_regular_file(destination))
      DestinationState.Existing(_sha256(destination))
    else
      RAISE.invalidArgumentFault(s"Media publication destination must be absent or a direct regular non-symlink file: $destination")
  }

  private def _publication_disposition(
    sourcehash: String,
    destinationstate: DestinationState,
    force: Boolean,
    destination: Path
  ): PublicationDisposition =
    destinationstate match {
      case DestinationState.Absent => PublicationDisposition.Create
      case DestinationState.Existing(destinationhash) if sourcehash == destinationhash => PublicationDisposition.Reuse
      case DestinationState.Existing(_) if force => PublicationDisposition.Replace
      case DestinationState.Existing(_) =>
        RAISE.invalidArgumentFault(s"Media publication destination differs; use force to replace: $destination")
    }

  private def _is_direct_regular_file(path: Path): Boolean =
    path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  private def _is_sha256(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")

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

  private def _ensure_destination_parent(publication: PreparedPublication): Path = {
    val root = _bind_profile_root(publication.profileRoot)
    if (root != publication.profileRootIdentity)
      RAISE.invalidArgumentFault("Prepared media publication profile root identity has changed")
    val relative = root.relativize(publication.destination)
    val components = relative.iterator.asScala.toVector
    if (components.isEmpty)
      RAISE.invalidArgumentFault(s"Media publication destination has no leaf: ${publication.destination}")
    val parent = components.dropRight(1).foldLeft(root) { (current, component) =>
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
    if (_destination_identity(publication.destination, root) != publication.destinationIdentity)
      RAISE.invalidArgumentFault("Prepared media publication destination identity has changed")
    parent
  }

  private def _install_publication(
    publication: PreparedPublication,
    replace: Boolean,
    temporaries: ArrayBuffer[Path]
  ): Unit = {
    val parent = _ensure_destination_parent(publication)
    _destination_state(publication.destination)
    val name = publication.destination.getFileName.toString
    val temporary = Files.createTempFile(parent, s".$name.", ".cozy-media.tmp")
    temporaries += temporary
    try {
      _copy_and_force(publication.publishablePath, temporary)
      if (_sha256(temporary) != publication.sourceSha256)
        RAISE.invalidArgumentFault(s"Media temporary publication hash differs: $temporary")
      if (replace)
        _atomic_move_replace(temporary, publication.destination)
      else
        _atomic_create(temporary, publication.destination)
      temporaries -= temporary
    } catch {
      case e: AtomicMoveNotSupportedException =>
        RAISE.invalidArgumentFault(s"Media publication requires atomic move: ${e.getMessage}")
      case e: java.io.IOException =>
        RAISE.invalidArgumentFault(s"Media publication failed: ${e.getMessage}")
      case e: UnsupportedOperationException =>
        RAISE.invalidArgumentFault(s"Media publication hard-link installation is unsupported: ${e.getMessage}")
    }
  }

  private def _copy_and_force(source: Path, temporary: Path): Unit = {
    val input = Files.newInputStream(source)
    val channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    try {
      val bytes = new Array[Byte](8192)
      var size = input.read(bytes)
      while (size >= 0) {
        if (size > 0) {
          val buffer = ByteBuffer.wrap(bytes, 0, size)
          while (buffer.hasRemaining)
            channel.write(buffer)
        }
        size = input.read(bytes)
      }
      channel.force(true)
    } finally {
      try input.close()
      finally channel.close()
    }
  }

  private def _atomic_move_replace(temporary: Path, destination: Path): Unit =
    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

  private def _atomic_create(temporary: Path, destination: Path): Unit = {
    Files.createLink(destination, temporary)
    Files.delete(temporary)
  }

  private def _selected(plan: Plan, target: Option[String]): Vector[ResolvedResource] =
    target match {
      case Some(id) =>
        plan.resources.filter(_.resource.id == id) match {
          case values if values.nonEmpty => values
          case _ => RAISE.invalidArgumentFault(s"Unknown media target: $id")
        }
      case None => plan.resources
    }

  private def _sha256(path: Path): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val stream = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var size = stream.read(buffer)
      while (size >= 0) {
        if (size > 0) digest.update(buffer, 0, size)
        size = stream.read(buffer)
      }
    } finally stream.close()
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }
}
