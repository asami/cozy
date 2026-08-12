package cozy.publication

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{AtomicMoveNotSupportedException, FileAlreadyExistsException, Files, LinkOption, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal
import org.goldenport.RAISE

/*
 * @since   Aug. 12, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaWipTransaction {
  final case class Hooks(
    beforelockedrevalidation: () => Unit = () => (),
    afterinstall: Path => Unit = _ => (),
    beforeregistryreplace: () => Unit = () => (),
    afterregistryreplace: () => Unit = () => (),
    lockevent: (String, Path) => Unit = (_, _) => ()
  )

  final case class Result(
    plan: CozyArticleMediaWipBinding.Plan,
    dryRun: Boolean,
    installedPaths: Vector[Path],
    registry: CozyArticleMediaRegistry.WipReadOnlyPlan,
    merge: Option[CozyArticleMediaRegistry.TransactionMergeResult]
  )

  private final case class Prepared(
    candidate: CozyArticleMediaWipBinding.Candidate,
    evidence: CozyArticleMediaWipBinding.VideoEvidence,
    temporary: Path,
    backup: Option[Path]
  )

  private val _root_monitors = new ConcurrentHashMap[String, Object]()

  def execute(plan: CozyArticleMediaWipBinding.Plan, dryRun: Boolean): Result =
    execute(plan, dryRun, Hooks())

  private[publication] def execute(
    plan: CozyArticleMediaWipBinding.Plan,
    dryRun: Boolean,
    hooks: Hooks
  ): Result = {
    if (plan == null || hooks == null || hooks.beforelockedrevalidation == null || hooks.afterinstall == null ||
      hooks.beforeregistryreplace == null || hooks.afterregistryreplace == null || hooks.lockevent == null)
      _invalid("Article-media WIP transaction inputs must be defined")
    _validate_roots(plan)
    if (dryRun) {
      val current = CozyArticleMediaWipBinding.revalidate(plan)
      Result(current, dryRun = true, Vector.empty, current.registry, None)
    } else {
      val roots = Vector(plan.publicationRoot, plan.websiteRoot).sortBy(_.identity.toString)
      _with_root_locks(roots, hooks) {
        hooks.beforelockedrevalidation()
        val lockedplan = CozyArticleMediaWipBinding.revalidate(plan)
        val updates = _updates(lockedplan)
        val rootevidence = CozyPublicationCompiler.SiteRootEvidence(
          lockedplan.publicationRoot.path,
          lockedplan.publicationRoot.identity,
          lockedplan.publicationRoot.fileKey
        )
        val prepared = ArrayBuffer.empty[Prepared]
        val installed = ArrayBuffer.empty[Prepared]
        try {
          val merged = CozyArticleMediaRegistry.transactionExisting(rootevidence) { transaction =>
            transaction.mergeWip(
              updates,
              beforeReplace = () => {
                val revalidated = CozyArticleMediaWipBinding.revalidate(lockedplan)
                prepared ++= _prepare(revalidated)
                prepared.sortBy(_.evidence.destination.toString).foreach { value =>
                  installed += value
                  _install(value, revalidated.registry)
                  hooks.afterinstall(value.evidence.destination)
                }
                CozyArticleMediaWipBinding.revalidateInstalled(lockedplan)
                hooks.beforeregistryreplace()
              },
              afterReplace = result => {
                val committed = CozyArticleMediaWipBinding.revalidateInstalled(lockedplan)
                _validate_registry_result(result, lockedplan)
                if (committed.registry.articles != lockedplan.registry.articles)
                  _invalid("Article-media WIP committed registry projection differs")
                installed.foreach(_validate_installed)
                hooks.afterregistryreplace()
              }
            )
          }
          _cleanup(prepared.toVector)
          Result(lockedplan, dryRun = false, installed.map(_.evidence.destination).toVector, lockedplan.registry, Some(merged))
        } catch {
          case application: Throwable =>
            try {
              _rollback(installed.toVector.reverse, lockedplan)
              _cleanup(prepared.toVector)
            } catch {
              case rollback: Throwable =>
                val failure = new IllegalArgumentException(s"Article-media WIP rollback failed: ${rollback.getMessage}", rollback)
                failure.addSuppressed(application)
                throw failure
            }
            throw application
        }
      }
    }
  }

  private def _updates(plan: CozyArticleMediaWipBinding.Plan): Vector[CozyArticleMediaRegistry.WipRoleUpdate] =
    plan.candidates.map(candidate =>
      CozyArticleMediaRegistry.WipRoleUpdate(plan.articleIdentity, candidate.variant, candidate.integrity)
    )

  private def _prepare(plan: CozyArticleMediaWipBinding.Plan): Vector[Prepared] = {
    val result = ArrayBuffer.empty[Prepared]
    val temporaries = ArrayBuffer.empty[Path]
    try {
      plan.candidates.foreach { candidate =>
        candidate.evidence match {
          case evidence: CozyArticleMediaWipBinding.VideoEvidence =>
            _validate_parent(evidence)
            val name = evidence.destination.getFileName.toString
            val temporary = Files.createTempFile(evidence.destinationParent.path, s".$name.", ".cozy-wip.tmp")
            temporaries += temporary
            _copy_force(evidence.source, temporary)
            if (_sha256(temporary) != evidence.source.sha256)
              _invalid(s"Article-media WIP staged digest differs: ${candidate.resourceId}")
            val backup = evidence.installedDestination.map { original =>
              val path = Files.createTempFile(evidence.destinationParent.path, s".$name.", ".cozy-wip.backup")
              temporaries += path
              _copy_force(original, path)
              if (_sha256(path) != original.sha256)
                _invalid(s"Article-media WIP backup digest differs: ${candidate.resourceId}")
              path
            }
            result += Prepared(candidate, evidence, temporary, backup)
          case _ =>
        }
      }
      result.toVector.sortBy(_.evidence.destination.toString)
    } catch {
      case application: Throwable =>
        try temporaries.reverse.foreach(Files.deleteIfExists)
        catch {
          case cleanup: Throwable =>
            cleanup.addSuppressed(application)
            throw cleanup
        }
        throw application
    }
  }

  private def _install(value: Prepared, registry: CozyArticleMediaRegistry.WipReadOnlyPlan): Unit = {
    val key = (value.candidate.variant.locale, value.candidate.role.name)
    val state = registry.videoStates.get((value.candidate.integrity.get.record.articleIdentity, key._1, key._2)).getOrElse(
      _invalid(s"Article-media WIP video state is missing: ${value.candidate.resourceId}")
    )
    try state match {
      case CozyArticleMediaRegistry.WipVideoState.Fresh =>
        Files.move(value.temporary, value.evidence.destination, StandardCopyOption.ATOMIC_MOVE)
      case CozyArticleMediaRegistry.WipVideoState.ExactRepeat =>
        Files.move(value.temporary, value.evidence.destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch {
      case _: AtomicMoveNotSupportedException => _invalid(s"Article-media WIP installation requires atomic move: ${value.evidence.destination}")
      case _: FileAlreadyExistsException => _invalid(s"Article-media WIP fresh destination already exists: ${value.evidence.destination}")
      case NonFatal(e) => _invalid(s"Article-media WIP installation failed: ${e.getMessage}")
    }
    _validate_installed(value)
  }

  private def _rollback(values: Vector[Prepared], plan: CozyArticleMediaWipBinding.Plan): Unit = {
    values.foreach { value =>
      value.backup match {
        case Some(backup) =>
          Files.move(backup, value.evidence.destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
          val expected = value.evidence.installedDestination.get
          if (_sha256(value.evidence.destination) != expected.sha256)
            _invalid(s"Article-media WIP restored destination digest differs: ${value.evidence.destination}")
        case None =>
          Files.deleteIfExists(value.evidence.destination)
          if (Files.exists(value.evidence.destination, LinkOption.NOFOLLOW_LINKS))
            _invalid(s"Article-media WIP new destination rollback failed: ${value.evidence.destination}")
      }
    }
    _validate_directory(plan.publicationRoot, "publication root")
    _validate_directory(plan.websiteRoot, "website root")
    CozyArticleMediaWipBinding.revalidate(plan)
  }

  private def _cleanup(values: Vector[Prepared]): Unit =
    values.foreach { value =>
      Files.deleteIfExists(value.temporary)
      value.backup.foreach(Files.deleteIfExists)
    }

  private def _validate_installed(value: Prepared): Unit = {
    val destination = value.evidence.destination
    if (Files.isSymbolicLink(destination) || !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) ||
      destination.toRealPath() != destination || _sha256(destination) != value.evidence.source.sha256)
      _invalid(s"Article-media WIP installed destination is invalid: $destination")
  }

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
    _validate_directory(plan.publicationRoot, "publication root")
    _validate_directory(plan.websiteRoot, "website root")
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

  private def _validate_parent(evidence: CozyArticleMediaWipBinding.VideoEvidence): Unit = {
    _validate_directory(evidence.destinationParent, "destination parent")
    if (evidence.destination.getParent != evidence.destinationParent.identity)
      _invalid(s"Article-media WIP destination parent identity differs: ${evidence.destination}")
  }

  private def _validate_directory(value: CozyArticleMediaWipBinding.DirectoryEvidence, label: String): Unit = {
    val path = value.path
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || path.toRealPath() != value.identity)
      _invalid(s"Article-media WIP $label evidence has changed: $path")
    val attributes = Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
    if (attributes.fileKey() == null || attributes.fileKey() != value.fileKey)
      _invalid(s"Article-media WIP $label identity has changed: $path")
  }

  private def _with_root_locks[A](
    roots: Vector[CozyArticleMediaWipBinding.DirectoryEvidence],
    hooks: Hooks
  )(f: => A): A = {
    def acquire(index: Int): A =
      if (index >= roots.size) f
      else {
        val root = roots(index)
        val monitor = _root_monitors.computeIfAbsent(root.identity.toString, new java.util.function.Function[String, Object] {
          override def apply(value: String): Object = new Object()
        })
        monitor.synchronized {
          _validate_directory(root, "lock root")
          val lockpath = root.identity.resolve(".cozy-article-media-wip.lock")
          if (!Files.exists(lockpath, LinkOption.NOFOLLOW_LINKS))
            try Files.createFile(lockpath) catch { case _: FileAlreadyExistsException => () }
          if (Files.isSymbolicLink(lockpath) || !Files.isRegularFile(lockpath, LinkOption.NOFOLLOW_LINKS))
            _invalid(s"Article-media WIP lock must be a direct regular file: $lockpath")
          val before = Files.readAttributes(lockpath, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
          if (before.fileKey() == null)
            _invalid(s"Article-media WIP lock has no stable identity: $lockpath")
          val channel = try FileChannel.open(lockpath, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS) catch {
            case NonFatal(e) => _invalid(s"Article-media WIP lock cannot be opened directly: ${e.getMessage}")
          }
          try {
            val opened = Files.readAttributes(lockpath, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
            if (Files.isSymbolicLink(lockpath) || !Files.isRegularFile(lockpath, LinkOption.NOFOLLOW_LINKS) ||
              opened.fileKey() == null || opened.fileKey() != before.fileKey())
              _invalid(s"Article-media WIP lock identity has changed: $lockpath")
            val lock = channel.lock()
            val locked = Files.readAttributes(lockpath, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
            if (Files.isSymbolicLink(lockpath) || !Files.isRegularFile(lockpath, LinkOption.NOFOLLOW_LINKS) ||
              locked.fileKey() == null || locked.fileKey() != before.fileKey()) {
              lock.release()
              _invalid(s"Article-media WIP lock identity has changed: $lockpath")
            }
            hooks.lockevent("acquire", root.identity)
            try acquire(index + 1)
            finally {
              hooks.lockevent("release", root.identity)
              lock.release()
            }
          } finally channel.close()
        }
      }
    acquire(0)
  }

  private def _copy_force(source: CozyArticleMediaWipBinding.FileEvidence, destination: Path): Unit = {
    val before = Files.readAttributes(source.path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
    if (Files.isSymbolicLink(source.path) || !Files.isRegularFile(source.path, LinkOption.NOFOLLOW_LINKS) ||
      before.fileKey() == null || before.fileKey() != source.fileKey || before.size() != source.size)
      _invalid(s"Article-media WIP copy source evidence has changed: ${source.path}")
    val input = try FileChannel.open(source.path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS) catch {
      case NonFatal(e) => _invalid(s"Article-media WIP copy source cannot be opened directly: ${e.getMessage}")
    }
    val output = FileChannel.open(destination, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    try {
      val buffer = ByteBuffer.allocate(8192)
      var size = input.read(buffer)
      while (size >= 0) {
        if (size > 0) {
          buffer.flip()
          while (buffer.hasRemaining) output.write(buffer)
          buffer.clear()
        }
        size = input.read(buffer)
      }
      output.force(true)
    } finally {
      try input.close() finally output.close()
    }
    val after = Files.readAttributes(source.path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
    if (Files.isSymbolicLink(source.path) || !Files.isRegularFile(source.path, LinkOption.NOFOLLOW_LINKS) ||
      after.fileKey() == null || after.fileKey() != source.fileKey || after.size() != source.size ||
      after.lastModifiedTime() != before.lastModifiedTime())
      _invalid(s"Article-media WIP copy source evidence has changed: ${source.path}")
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = Files.newInputStream(path)
    try {
      val bytes = new Array[Byte](8192)
      var size = input.read(bytes)
      while (size >= 0) {
        if (size > 0) digest.update(bytes, 0, size)
        size = input.read(bytes)
      }
    } finally input.close()
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
