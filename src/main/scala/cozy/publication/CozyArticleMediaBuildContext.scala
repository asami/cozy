package cozy.publication

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{FileAlreadyExistsException, Files, LinkOption, Path}
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

/*
 * @since   Aug.  5, 2026
 *  version Aug. 30, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaBuildContext {
  sealed abstract class Strategy(val name: String, val policy: CozyArticleMediaPolicy.Strategy)

  object Strategy {
    case object Draft extends Strategy("draft", CozyArticleMediaPolicy.Strategy.Preview)
    case object WorkInProgress extends Strategy("work-in-progress", CozyArticleMediaPolicy.Strategy.Preview)
    case object ProductionPreview extends Strategy("production-preview", CozyArticleMediaPolicy.Strategy.Preview)
    case object Production extends Strategy("production", CozyArticleMediaPolicy.Strategy.Production)
  }

  final case class Context(
    strategy: Strategy,
    publicationPath: Path,
    repositoryPath: Path,
    omittedKeys: Vector[CozyArticleMediaAssociation.Key],
    diagnostics: Vector[CozyArticleMediaPolicy.Diagnostic]
  )

  private final case class BundleFile(filename: String)

  private var _active_leases = Map.empty[Path, Int]
  def withContext[A](
    projectRoot: Path,
    publicationRoot: Path,
    repositoryRoot: Path,
    strategy: String
  )(consumer: Context => A): A = {
    val selected = normalizeStrategy(strategy)
    if (consumer == null)
      _invalid("Article-media build-context consumer must be defined")
    val projectroot = _require_direct_directory(projectRoot, "Article-media build-context project root")
    val publicationroot = _create_direct_directory_chain(publicationRoot, "Article-media build-context publication root")
    val repositoryroot = _normalize_root(repositoryRoot, "Article-media build-context repository root")
    val current = CozyArticleMediaRegistry.readCurrentSite(publicationroot.toRealPath())
    val projection = CozyArticleMediaRegistry.canonicalProjection(CozyArticleMediaRegistry.Snapshot(current.entries))
    val context = if (CozyArticleMediaWarehouseBuildContext.hasCanonicalReferences(projection)) {
      val result = CozyArticleMediaWarehouseBuildContext.materialize(projectroot, publicationroot, repositoryroot, selected, current, projection)
      result.diagnostics.foreach { diagnostic =>
        Console.err.println(s"Article-media ${diagnostic.reason}: ${diagnostic.articleIdentity} [${diagnostic.locale}, ${diagnostic.role}] ${diagnostic.publicPath}")
      }
      result.context
    } else CozyArticleMediaRegistry.transaction(publicationroot) { transaction =>
      _materialize(projectroot, publicationroot, repositoryroot, selected, transaction.snapshot)
    }
    _lease(context)(consumer)
  }

  private[cozy] def normalizeStrategy(value: String): Strategy =
    Option(value).filter(_.nonEmpty) match {
      case Some("draft") => Strategy.Draft
      case Some("work-in-progress") | Some("wip") => Strategy.WorkInProgress
      case Some("production-preview") | Some("preview") => Strategy.ProductionPreview
      case Some("production") => Strategy.Production
      case _ => _invalid(s"Unsupported article-media build strategy: $value")
    }

  private[publication] def activeLeaseCount(publication: Path): Int = synchronized {
    if (publication == null) 0 else _active_leases.getOrElse(publication.toAbsolutePath.normalize(), 0)
  }

  private def _materialize(
    projectroot: Path,
    publicationroot: Path,
    repositoryroot: Path,
    strategy: Strategy,
    snapshot: CozyArticleMediaRegistry.Snapshot
  ): Context = {
    val projection = CozyArticleMediaRegistry.canonicalProjection(snapshot)
    val snapshots = _snapshots_directory(projectroot, strategy)
    val target = Files.createTempDirectory(snapshots, "snapshot-")
    val publication = Files.createDirectory(target.resolve("publication"))
    val bundles = _copy_snapshot_bundles(publicationroot, publication, snapshot)
    val metadata = _load_metadata(publication, bundles)
    val policy = CozyArticleMediaPolicy.evaluate(strategy.policy, metadata, projection.integrityResults, repositoryroot)
    _apply_omissions(publication, bundles, projection, policy)
    Context(strategy, publication, repositoryroot, policy.omittedKeys, policy.diagnostics)
  }

  private def _copy_snapshot_bundles(
    root: Path,
    destination: Path,
    snapshot: CozyArticleMediaRegistry.Snapshot
  ): Vector[BundleFile] = {
    if (snapshot == null || snapshot.bundleNames == null)
      _invalid("Article-media build-context snapshot must be defined")
    snapshot.bundleNames.sorted.map { name =>
      if (name == null)
        _invalid("Article-media build-context snapshot bundle must be defined")
      val filename = s"$name.json"
      val source = root.resolve(filename).normalize()
      if (!source.startsWith(root) || Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
        _invalid(s"Article-media build-context bundle must be a direct regular file: $filename")
      Files.copy(source, destination.resolve(filename))
      BundleFile(filename)
    }
  }

  private def _load_metadata(publication: Path, bundles: Vector[BundleFile]): PublishMetadata =
    if (bundles.isEmpty)
      PublishMetadata(Vector.empty)
    else
      try PublishMetadata.load(publication.toFile).get
      catch {
        case _: Throwable => _invalid("Article-media build-context publication metadata is invalid")
      }

  private def _apply_omissions(
    publication: Path,
    bundles: Vector[BundleFile],
    projection: CozyArticleMediaRegistry.CanonicalProjection,
    policy: CozyArticleMediaPolicy.Result
  ): Unit = {
    val omitted = policy.omittedKeys.toSet
    val strictreplacements = projection.strictPublications.flatMap { strict =>
      val variants = strict.publication.variants.flatMap { variant =>
        val infographic = if (omitted.contains(_key(strict.publication.articleIdentity, variant.locale, CozyArticleMediaIntegrity.Role.Infographic))) None else variant.infographic
        val video = if (omitted.contains(_key(strict.publication.articleIdentity, variant.locale, CozyArticleMediaIntegrity.Role.Video))) None else variant.video
        if (infographic.isDefined || video.isDefined || variant.articlePdf.isDefined || variant.summarySlidesPdf.isDefined)
          Some(CozyArticleMediaPublication.Variant(
            locale = variant.locale,
            infographic = infographic,
            video = video,
            articlePdf = variant.articlePdf,
            summarySlidesPdf = variant.summarySlidesPdf
          ))
        else None
      }.toVector
      val replacement = if (variants.isEmpty) None else Some(CozyArticleMediaPublication.produce(strict.publication.articleIdentity, variants).metadata)
      if (replacement == Some(strict.metadata)) None else Some(strict.entryPath -> replacement)
    }.toMap
    val replacements = strictreplacements ++ policy.excludedIntegrityEntryPaths.map(_ -> None).toMap
    if (replacements.nonEmpty)
      bundles.foreach { bundle =>
        val changed = _rewrite_bundle(publication.resolve(bundle.filename), replacements)
        if (changed.isDefined)
          Files.write(publication.resolve(bundle.filename), changed.get)
      }
  }

  private def _rewrite_bundle(path: Path, replacements: Map[String, Option[JsObject]]): Option[Array[Byte]] = {
    val bundle = Json.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).asOpt[JsObject].getOrElse(
      _invalid(s"Article-media build-context bundle is malformed: ${path.getFileName}")
    )
    val entries = bundle.value.get("entries") match {
      case Some(JsArray(values)) => values.toVector
      case _ => _invalid(s"Article-media build-context bundle entries are invalid: ${path.getFileName}")
    }
    var changed = false
    val rewritten = entries.flatMap { value =>
      val entry = value.asOpt[JsObject].getOrElse(_invalid(s"Article-media build-context bundle entry is invalid: ${path.getFileName}"))
      entry.value.get("path") match {
        case Some(JsString(name)) if replacements.contains(name) =>
          changed = true
          replacements(name).map(metadata => entry + ("metadata" -> metadata))
        case _ => Some(entry)
      }
    }.sortBy { value =>
      (value \ "path").asOpt[String].getOrElse(_invalid(s"Article-media build-context bundle entry path is invalid: ${path.getFileName}"))
    }
    if (!changed) None
    else Some(Json.stringify(bundle + ("entries" -> JsArray(rewritten))).getBytes(StandardCharsets.UTF_8))
  }

  private def _snapshots_directory(projectroot: Path, strategy: Strategy): Path =
    _create_direct_directory_chain(
      projectroot.resolve("target/cozy-bok/article-media").resolve(strategy.name).resolve("snapshots"),
      "Article-media build-context snapshot parent",
      projectroot
    )

  private def _lease[A](context: Context)(consumer: Context => A): A = {
    val identity = context.publicationPath.toAbsolutePath.normalize()
    synchronized { _active_leases = _active_leases.updated(identity, _active_leases.getOrElse(identity, 0) + 1) }
    try consumer(context)
    finally synchronized {
      val remaining = _active_leases.getOrElse(identity, 1) - 1
      _active_leases = if (remaining <= 0) _active_leases - identity else _active_leases.updated(identity, remaining)
    }
  }

  private def _key(identity: String, locale: String, role: CozyArticleMediaIntegrity.Role): CozyArticleMediaAssociation.Key =
    CozyArticleMediaAssociation.Key(identity, locale, role)

  private def _require_direct_directory(value: Path, label: String): Path = {
    val root = _normalize_root(value, label)
    _require_direct_directory_component(root, label, value)
    root
  }

  private def _create_direct_directory_chain(value: Path, label: String): Path = {
    val root = _normalize_root(value, label)
    val ancestor = _deepest_existing_ancestor(root)
    _require_direct_directory_component(ancestor, label, root)
    _create_direct_directory_descendants(root, ancestor, label)
    root
  }

  private def _create_direct_directory_chain(value: Path, label: String, anchor: Path): Path = {
    val root = _normalize_root(value, label)
    val trustedroot = _require_direct_directory(anchor, s"$label configured project root")
    if (!root.startsWith(trustedroot))
      _invalid(s"$label must remain beneath its configured project root: $root")
    _create_direct_directory_descendants(root, trustedroot, label)
    root
  }

  private def _create_direct_directory_descendants(root: Path, anchor: Path, label: String): Unit = {
    var current = anchor
    val iterator = anchor.relativize(root).iterator()
    while (iterator.hasNext) {
      current = current.resolve(iterator.next())
      _require_or_create_direct_directory_component(current, label, root)
    }
  }

  private def _deepest_existing_ancestor(root: Path): Path = {
    var current = root
    while (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
      current = Option(current.getParent).getOrElse(
        _invalid(s"Article-media build-context directory root has no existing ancestor: $root")
      )
    }
    current
  }

  private def _require_or_create_direct_directory_component(path: Path, label: String, root: Path): Unit = {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
      _require_direct_directory_component(path, label, root)
    else {
      try Files.createDirectory(path)
      catch {
        case _: FileAlreadyExistsException => ()
        case e: IOException => _invalid(s"$label cannot create directory: ${e.getMessage}")
      }
      _require_direct_directory_component(path, label, root)
    }
  }

  private def _require_direct_directory_component(path: Path, label: String, root: Path): Unit =
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"$label must have direct directory components: $root")

  private def _normalize_root(value: Path, label: String): Path =
    if (value == null) _invalid(s"$label must be defined") else value.toAbsolutePath.normalize()

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
