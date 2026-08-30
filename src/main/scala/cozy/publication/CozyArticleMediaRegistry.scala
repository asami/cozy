package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.Try
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata.{ImageReference, PdfDocumentReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

/*
 * @since   Aug.  4, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaRegistry {
  private final case class Bundle(
    filename: String,
    name: String,
    entries: Vector[Entry],
    rawdigest: String
  )

  private final case class ReadOnlyRoot(
    lexical: Path,
    real: Path
  )

  private final case class ReadOnlySnapshot(
    root: Option[ReadOnlyRoot],
    snapshot: Snapshot
  )

  final case class Entry(
    bundleName: String,
    path: String,
    key: String,
    metadata: JsValue
  )

  final case class Snapshot(
    entries: Vector[Entry],
    bundleDigests: Map[String, String] = Map.empty
  )

  /* Read-only, canonical view for a consumer that has already obtained a
   * locked snapshot.  Keeping this projection here prevents a second parser
   * (and, more importantly, a second interpretation of strict metadata) at
   * the build-context boundary. */
  final case class CanonicalProjection(
    strictPublications: Vector[CozyArticleMediaPublication.Result],
    integrityResults: Vector[CozyArticleMediaIntegrity.Result]
  )

  sealed trait StrictRole {
    def name: String
  }

  object StrictRole {
    case object Infographic extends StrictRole {
      val name = "infographic"
    }

    case object Video extends StrictRole {
      val name = "video"
    }

    case object ArticlePdf extends StrictRole {
      val name = "article_pdf"
    }

    case object SummarySlidesPdf extends StrictRole {
      val name = "summary_slides_pdf"
    }
  }

  final case class UpsertResult(
    bundlePath: Path,
    entryPaths: Vector[String],
    snapshot: Snapshot
  )

  final case class RoleUpdate(
    articleIdentity: String,
    variant: CozyArticleMediaPublication.Variant,
    integrity: CozyArticleMediaIntegrity.Result
  )

  /* Site registration owns strict SmartDox metadata only.  It deliberately
   * has no integrity input: a site-visible infographic or external watch URL
   * must not synthesize Cozy artifact-correlation evidence. */
  final case class SiteRoleUpdate(
    articleIdentity: String,
    variant: CozyArticleMediaPublication.Variant
  )

  /* WIP planning carries the public strict update and its optional separate
   * integrity record together.  It is deliberately read-only here: S2-C owns
   * the two-root installation and the eventual registry replacement. */
  final case class WipRoleUpdate(
    articleIdentity: String,
    variant: CozyArticleMediaPublication.Variant,
    integrity: Option[CozyArticleMediaIntegrity.Result]
  )

  sealed trait WipVideoState
  object WipVideoState {
    case object Fresh extends WipVideoState
    case object ExactRepeat extends WipVideoState
  }

  final case class WipArticlePlan(
    articleIdentity: String,
    owner: String,
    strict: CozyArticleMediaPublication.Result,
    integrities: Vector[CozyArticleMediaIntegrity.Result]
  )

  final case class WipReadOnlyPlan(
    snapshot: Snapshot,
    articles: Vector[WipArticlePlan],
    videoStates: Map[(String, String, String), WipVideoState]
  )

  /* A producer can reserve the ownership shape of a role before it has
   * rendered bytes from which integrity evidence can honestly be made. */
  final case class RoleIntent(
    articleIdentity: String,
    locale: String,
    role: CozyArticleMediaIntegrity.Role
  )

  final case class RoleIntentOwner(intent: RoleIntent, owner: String)

  final case class ReadOnlyRoleIntentValidation(
    snapshot: Snapshot,
    owners: Vector[RoleIntentOwner]
  )

  final case class TransactionMergeResult(
    bundlePaths: Vector[Path],
    entryPaths: Vector[String],
    snapshot: Snapshot
  )

  sealed trait Transaction {
    def snapshot: Snapshot
    def validate(roleUpdates: Vector[RoleUpdate]): Unit
    def validateProjectedMetadata(
      metadataPublications: Vector[CozyPublicationCompiler.MetadataPublication]
    ): Snapshot
    def merge(roleUpdates: Vector[RoleUpdate]): TransactionMergeResult
    def mergeSite(
      roleUpdates: Vector[SiteRoleUpdate],
      beforeReplace: () => Unit
    ): TransactionMergeResult
    def mergeWip(
      roleUpdates: Vector[WipRoleUpdate],
      beforeReplace: () => Unit,
      afterReplace: TransactionMergeResult => Unit
    ): TransactionMergeResult
    def publish(
      metadataPublications: Vector[CozyPublicationCompiler.MetadataPublication]
    )(
      roleUpdates: Snapshot => Vector[RoleUpdate]
    )(
      admitArtifacts: () => Unit
    ): TransactionMergeResult
  }

  private final class TransactionImpl private (
    private val _capability: CozyPublicationCompiler.RegistryLockCapability,
    private val _initial_snapshot: Snapshot
  ) extends Transaction {
    private val _owner_thread = Thread.currentThread
    private var _active = true
    private var _committed = false

    def snapshot: Snapshot = {
      _require_callback_owner()
      _initial_snapshot
    }

    /*
     * A producer that owns an external artifact transaction needs the exact
     * owner/role projection checked before installing artifacts.  This remains
     * non-mutating; merge repeats the check immediately before replacement.
     */
    def validate(roleUpdates: Vector[RoleUpdate]): Unit = {
      _require_callback_owner()
      if (_committed)
        _invalid("Article-media registry transaction permits one merge commit")
      val updates = _normalize_role_updates(roleUpdates)
      val plans = _plan_role_updates(_initial_snapshot, updates)
      _validate_plans(_initial_snapshot, plans)
      _capability.validateSnapshot(_initial_snapshot.bundleDigests)
    }

    /* Validate the metadata-owner projection while the transaction owns the
     * registry lock.  Producers use this before admitting external artifacts;
     * publish repeats snapshot validation immediately before replacement. */
    def validateProjectedMetadata(
      metadataPublications: Vector[CozyPublicationCompiler.MetadataPublication]
    ): Snapshot = {
      _require_callback_owner()
      if (_committed)
        _invalid("Article-media registry transaction permits one merge commit")
      val publications = _normalize_metadata_publications(metadataPublications)
      val projected = _project_metadata_publications(_initial_snapshot, publications)
      _capability.validateSnapshot(_initial_snapshot.bundleDigests)
      projected
    }

    def merge(roleUpdates: Vector[RoleUpdate]): TransactionMergeResult = {
      _require_callback_owner()
      if (_committed)
        _invalid("Article-media registry transaction permits one merge commit")
      val realroot = _capability.realRoot
      val updates = _normalize_role_updates(roleUpdates)
      val plans = _plan_role_updates(_initial_snapshot, updates)
      _validate_plans(_initial_snapshot, plans)
      _capability.validateSnapshot(_initial_snapshot.bundleDigests)
      _committed = true
      plans.foreach { plan =>
        _capability.replaceMetadata(
          plan.owner,
          plan.entries.map(x => x.path -> x.metadata),
          plan.removeprefixes,
          Map.empty,
          createArticleMedia = plan.createarticlemedia
        )
      }
      val result = load(realroot)
      TransactionMergeResult(
        bundlePaths = plans.map(x => realroot.resolve(s"${x.owner}.json")),
        entryPaths = plans.flatMap(_.entries.map(_.path)).distinct.sorted,
        snapshot = result
      )
    }

    /* Site registration is a strict-only replacement.  Integrity entries are
     * observed for ownership and consistency, but never placed in the
     * replacement/removal set. */
    def mergeSite(
      roleUpdates: Vector[SiteRoleUpdate],
      beforeReplace: () => Unit
    ): TransactionMergeResult = {
      _require_callback_owner()
      if (_committed)
        _invalid("Article-media registry transaction permits one merge commit")
      if (beforeReplace == null)
        _invalid("Article-media registry before-replace callback must be defined")
      _capability.validateRootEvidence()
      val realroot = _capability.realRoot
      val updates = _normalize_site_role_updates(roleUpdates)
      val plans = _plan_site_role_updates(_initial_snapshot, updates)
      _validate_plans(_initial_snapshot, plans)
      _capability.validateSnapshot(_initial_snapshot.bundleDigests)
      beforeReplace()
      _capability.validateRootEvidence()
      _capability.validateSnapshot(_initial_snapshot.bundleDigests)
      _committed = true
      plans.foreach { plan =>
        _capability.replaceMetadata(
          plan.owner,
          plan.entries.map(x => x.path -> x.metadata),
          Vector.empty,
          Map.empty,
          createArticleMedia = plan.createarticlemedia
        )
      }
      _capability.validateRootEvidence()
      val result = load(realroot)
      TransactionMergeResult(
        bundlePaths = plans.map(x => realroot.resolve(s"${x.owner}.json")),
        entryPaths = plans.flatMap(_.entries.map(_.path)).distinct.sorted,
        snapshot = result
      )
    }

    def mergeWip(
      roleUpdates: Vector[WipRoleUpdate],
      beforeReplace: () => Unit,
      afterReplace: TransactionMergeResult => Unit
    ): TransactionMergeResult = {
      _require_callback_owner()
      if (_committed)
        _invalid("Article-media registry transaction permits one merge commit")
      if (beforeReplace == null || afterReplace == null)
        _invalid("Article-media registry WIP callbacks must be defined")
      _capability.validateRootEvidence()
      val realroot = _capability.realRoot
      val updates = _normalize_wip_role_updates(roleUpdates)
      val plan = _plan_wip_role_updates(_initial_snapshot, updates)
      val owners = plan.articles.map(_.owner).distinct
      if (owners.size != 1)
        _invalid("Article-media registry WIP transaction requires exactly one owner bundle")
      val owner = owners.head
      val integrityarticles = updates.filter(_.integrity.nonEmpty).map(_.articleidentity).toSet
      val entries = plan.articles.flatMap { article =>
        val integrities = if (integrityarticles.contains(article.articleIdentity)) article.integrities else Vector.empty
        _publication_entry(article.strict) +: integrities.map { integrity =>
          Entry(owner, integrity.entryPath, _canonical_key(integrity.entryPath), integrity.metadata)
        }
      }.sortBy(_.path)
      val removals = plan.articles.filter(article => integrityarticles.contains(article.articleIdentity)).flatMap(article => _integrity_entries_for_identity(_initial_snapshot, article.articleIdentity).map(_.path)).distinct.sorted
      val createarticlemedia = owner == "article-media" && !_initial_snapshot.bundleDigests.contains(owner)
      _validate_plans(_initial_snapshot, Vector(OwnerPlan(owner, entries, removals, createarticlemedia)))
      _capability.validateSnapshot(_initial_snapshot.bundleDigests)
      val originalbytes = _capability.bundleBytes(owner)
      beforeReplace()
      _capability.validateRootEvidence()
      _capability.validateSnapshot(_initial_snapshot.bundleDigests)
      _committed = true
      var replaced = false
      try {
        replaced = true
        _capability.replaceMetadata(
          owner,
          entries.map(x => x.path -> x.metadata),
          removals,
          Map.empty,
          createArticleMedia = createarticlemedia
        )
        _capability.validateRootEvidence()
        val result = load(realroot)
        val merged = TransactionMergeResult(
          bundlePaths = Vector(realroot.resolve(s"$owner.json")),
          entryPaths = entries.map(_.path).distinct.sorted,
          snapshot = result
        )
        afterReplace(merged)
        merged
      } catch {
        case application: Throwable if replaced =>
          try _capability.restoreBundle(owner, originalbytes, _initial_snapshot.bundleDigests)
          catch {
            case rollback: Throwable =>
              val failure = new IllegalArgumentException(
                s"Article-media WIP registry rollback failed: ${rollback.getMessage}",
                rollback
              )
              failure.addSuppressed(application)
              throw failure
          }
          throw application
      }
    }

    /*
     * Role planning observes the exact metadata-replacement projection before
     * artifact admission.  Both callbacks run under this owner-thread lock:
     * a valid initial snapshot is required before admission and again before
     * metadata replacement, so an admission failure cannot replace a bundle.
     */
    def publish(
      metadataPublications: Vector[CozyPublicationCompiler.MetadataPublication]
    )(
      roleUpdates: Snapshot => Vector[RoleUpdate]
    )(
      admitArtifacts: () => Unit
    ): TransactionMergeResult = {
      _require_callback_owner()
      if (_committed)
        _invalid("Article-media registry transaction permits one merge commit")
      if (roleUpdates == null)
        _invalid("Article-media registry role-update callback must be defined")
      if (admitArtifacts == null)
        _invalid("Article-media registry artifact-admission callback must be defined")
      val publications = _normalize_metadata_publications(metadataPublications)
      val projected = _project_metadata_publications(_initial_snapshot, publications)
      val updates = _normalize_role_updates(roleUpdates(projected))
      val plans = _plan_role_updates(projected, updates)
      _validate_plans(projected, plans)
      _capability.validateSnapshot(_initial_snapshot.bundleDigests)
      admitArtifacts()
      _capability.validateSnapshot(_initial_snapshot.bundleDigests)
      _committed = true
      publications.foreach { publication =>
        if (_initial_snapshot.bundleDigests.contains(publication.name))
          _capability.replaceMetadata(
            publication.name,
            publication.entries,
            Vector.empty,
            Map.empty,
            createArticleMedia = false
          )
        else
          _capability.publishMetadata(publication)
      }
      plans.foreach { plan =>
        _capability.replaceMetadata(
          plan.owner,
          plan.entries.map(x => x.path -> x.metadata),
          plan.removeprefixes,
          Map.empty,
          createArticleMedia = plan.createarticlemedia
        )
      }
      val result = load(_capability.realRoot)
      TransactionMergeResult(
        bundlePaths = (publications.map(x => _capability.realRoot.resolve(s"${x.name}.json")) ++
          plans.map(x => _capability.realRoot.resolve(s"${x.owner}.json"))).distinct.sortBy(_.toString),
        entryPaths = (publications.flatMap(_.entries.map(_._1)) ++ plans.flatMap(_.entries.map(_.path))).distinct.sorted,
        snapshot = result
      )
    }

    private[CozyArticleMediaRegistry] def _close(): Unit = {
      _require_callback_owner()
      _active = false
    }

    private def _require_callback_owner(): Unit = {
      if (Thread.currentThread ne _owner_thread)
        _invalid("Article-media registry transaction must be used by its callback owner thread")
      if (!_active)
        _invalid("Article-media registry transaction is no longer active")
      _capability.realRoot
    }
  }

  private object TransactionImpl {
    private[CozyArticleMediaRegistry] def _create(
      capability: CozyPublicationCompiler.RegistryLockCapability,
      initialsnapshot: Snapshot
    ): TransactionImpl =
      new TransactionImpl(capability, initialsnapshot)
  }

  def transaction[A](root: Path)(f: Transaction => A): A = {
    _validate_root(root)
    if (f == null)
      _invalid("Article-media registry transaction callback must be defined")
    CozyPublicationCompiler.withRegistryLockCapability(root) { capability =>
      val state = TransactionImpl._create(capability, load(capability.realRoot))
      try f(state) finally state._close()
    }
  }

  private[publication] def transactionExisting[A](
    evidence: CozyPublicationCompiler.SiteRootEvidence
  )(f: Transaction => A): A = {
    if (evidence == null)
      _invalid("Article-media registry existing-root evidence must be defined")
    if (f == null)
      _invalid("Article-media registry transaction callback must be defined")
    CozyPublicationCompiler.withExistingRegistryLockCapability(evidence) { capability =>
      val state = TransactionImpl._create(capability, load(capability.realRoot))
      try f(state) finally state._close()
    }
  }

  /*
   * Read-only producer planning seam.  A one-stop publish preflight must run
   * the same owner planning and conflict checks as the locked transaction,
   * while an as-yet-uncreated publication root is treated as an empty
   * snapshot.  This method never creates or mutates the configured root.
   */
  private[publication] def validateReadOnly(root: Path, roleUpdates: Vector[RoleUpdate]): Unit = {
    validateReadOnly(root, roleUpdates, () => ())
  }

  private[publication] def validateReadOnly(
    root: Path,
    roleUpdates: Vector[RoleUpdate],
    beforeRevalidation: () => Unit
  ): Unit = {
    if (beforeRevalidation == null)
      _invalid("Article-media registry before-revalidation callback must be defined")
    val initial = _read_only_snapshot(root)
    val updates = _normalize_role_updates(roleUpdates)
    val plans = _plan_role_updates(initial.snapshot, updates)
    _validate_plans(initial.snapshot, plans)
    beforeRevalidation()
    val revalidated = Try(_read_only_snapshot(root)).getOrElse(
      _invalid("Article-media registry stale read-only snapshot")
    )
    if (revalidated != initial)
      _invalid("Article-media registry stale read-only snapshot")
  }

  /* Site registration has no integrity input.  Its dry-run still performs the
   * exact strict-owner merge planning and complete snapshot revalidation that
   * a real site transaction performs, without acquiring a registry lock or
   * creating a bundle. */
  private[publication] def validateSiteReadOnly(
    root: Path,
    roleUpdates: Vector[SiteRoleUpdate]
  ): Unit =
    validateSiteReadOnly(root, roleUpdates, () => ())

  private[publication] def validateSiteReadOnly(
    root: Path,
    roleUpdates: Vector[SiteRoleUpdate],
    beforeRevalidation: () => Unit
  ): Unit = {
    if (beforeRevalidation == null)
      _invalid("Article-media registry before-revalidation callback must be defined")
    val initial = _read_only_snapshot(root)
    val updates = _normalize_site_role_updates(roleUpdates)
    val plans = _plan_site_role_updates(initial.snapshot, updates)
    _validate_plans(initial.snapshot, plans)
    beforeRevalidation()
    val revalidated = Try(_read_only_snapshot(root)).getOrElse(
      _invalid("Article-media registry stale read-only snapshot")
    )
    if (revalidated != initial)
      _invalid("Article-media registry stale read-only snapshot")
  }

  /* WIP preflight plans one mixed strict/integrity projection against one
   * snapshot, then proves that the complete configured snapshot is unchanged.
   * It takes no registry lock and creates no bundle or directory. */
  private[publication] def validateWipReadOnly(
    root: Path,
    roleUpdates: Vector[WipRoleUpdate]
  ): WipReadOnlyPlan =
    validateWipReadOnly(root, roleUpdates, () => ())

  private[publication] def validateWipReadOnly(
    root: Path,
    roleUpdates: Vector[WipRoleUpdate],
    beforeRevalidation: () => Unit
  ): WipReadOnlyPlan = {
    if (beforeRevalidation == null)
      _invalid("Article-media registry before-revalidation callback must be defined")
    val initial = _read_only_existing_snapshot(root)
    val updates = _normalize_wip_role_updates(roleUpdates)
    val plan = _plan_wip_role_updates(initial.snapshot, updates)
    beforeRevalidation()
    val revalidated = Try(_read_only_existing_snapshot(root)).getOrElse(
      _invalid("Article-media registry stale read-only snapshot")
    )
    if (revalidated != initial)
      _invalid("Article-media registry stale read-only snapshot")
    plan
  }

  private[publication] def validateSiteReadOnly(
    evidence: CozyPublicationCompiler.SiteRootEvidence,
    roleUpdates: Vector[SiteRoleUpdate],
    beforeRevalidation: () => Unit
  ): Unit = {
    if (evidence == null)
      _invalid("Article-media registry existing-root evidence must be defined")
    if (beforeRevalidation == null)
      _invalid("Article-media registry before-revalidation callback must be defined")
    CozyPublicationCompiler.captureSiteRootEvidence(evidence.lexical) match {
      case actual if actual != evidence => _invalid("Publication registry root evidence has changed")
      case _ => ()
    }
    val initial = _read_only_snapshot(evidence.real)
    val updates = _normalize_site_role_updates(roleUpdates)
    val plans = _plan_site_role_updates(initial.snapshot, updates)
    _validate_plans(initial.snapshot, plans)
    beforeRevalidation()
    if (CozyPublicationCompiler.captureSiteRootEvidence(evidence.lexical) != evidence)
      _invalid("Publication registry root evidence has changed")
    val revalidated = Try(_read_only_snapshot(evidence.real)).getOrElse(
      _invalid("Article-media registry stale read-only snapshot")
    )
    if (revalidated != initial)
      _invalid("Article-media registry stale read-only snapshot")
  }

  private[publication] def validateReadOnlyRoleIntents(
    root: Path,
    intents: Vector[RoleIntent]
  ): ReadOnlyRoleIntentValidation =
    validateReadOnlyRoleIntents(root, intents, () => ())

  /* This seam deliberately validates only the ownership intent.  It neither
   * synthesizes integrity records nor writes a registry bundle, and reloads
   * the complete configured snapshot before returning its canonical owners. */
  private[publication] def validateReadOnlyRoleIntents(
    root: Path,
    intents: Vector[RoleIntent],
    beforeRevalidation: () => Unit
  ): ReadOnlyRoleIntentValidation = {
    if (beforeRevalidation == null)
      _invalid("Article-media registry before-revalidation callback must be defined")
    val initial = _read_only_snapshot(root)
    val normalized = _normalize_role_intents(intents)
    val owners = normalized.map { intent =>
      val owner = _article_owner(initial.snapshot, intent.articleIdentity)
      _validate_role_intent_path(initial.snapshot, intent, owner)
      RoleIntentOwner(intent, owner)
    }
    beforeRevalidation()
    val revalidated = Try(_read_only_snapshot(root)).getOrElse(
      _invalid("Article-media registry stale read-only snapshot")
    )
    if (revalidated != initial)
      _invalid("Article-media registry stale read-only snapshot")
    ReadOnlyRoleIntentValidation(initial.snapshot, owners)
  }

  private def _read_only_snapshot(root: Path): ReadOnlySnapshot = {
    if (root == null)
      _invalid("Configured publication root must be defined")
    val normalized = root.toAbsolutePath.normalize()
    if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS))
      ReadOnlySnapshot(None, Snapshot(Vector.empty))
    else {
      if (!Files.isDirectory(normalized))
        _invalid(s"Configured publication root must resolve to an existing directory: $root")
      val real = normalized.toRealPath()
      ReadOnlySnapshot(Some(ReadOnlyRoot(normalized, real)), load(real))
    }
  }

  private def _read_only_existing_snapshot(root: Path): ReadOnlySnapshot = {
    if (root == null)
      _invalid("Configured publication root must be defined")
    val normalized = root.toAbsolutePath.normalize()
    if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Configured publication root must be an existing direct non-symlink directory: $root")
    val real = Try(normalized.toRealPath()).getOrElse(
      _invalid(s"Configured publication root cannot be resolved: $root")
    )
    if (real != normalized)
      _invalid(s"Configured publication root must not use a lexical or symlink alias: $root")
    ReadOnlySnapshot(Some(ReadOnlyRoot(normalized, real)), load(real))
  }

  def load(root: Path): Snapshot = {
    _validate_root(root)
    val stream = Files.list(root)
    try {
      val bundles = stream.iterator().asScala.toVector
        .filter(_is_direct_json_filename)
        .sortBy(_.getFileName.toString)
        .map(_load_bundle)
      val entries = bundles.flatMap(_.entries)
      _validate_duplicates(entries)
      Snapshot(
        entries.sortBy(x => (x.path, x.bundleName)),
        bundles.map(x => x.name -> x.rawdigest).toMap
      )
    } finally {
      stream.close()
    }
  }

  private[publication] def canonicalProjection(snapshot: Snapshot): CanonicalProjection = {
    if (snapshot == null || snapshot.entries == null || snapshot.bundleDigests == null)
      _invalid("Article-media registry snapshot must be defined")
    if (snapshot.entries.exists(_ == null))
      _invalid("Article-media registry snapshot entry must be defined")
    _validate_duplicates(snapshot.entries)
    val strict = snapshot.entries.collect {
      case entry if entry != null && entry.path.startsWith("metadata/article-media/") =>
        _strict_result(entry.metadata)
    }.sortBy(_.entryPath)
    val integrities = snapshot.entries.collect {
      case entry if entry != null && entry.path.startsWith("metadata/article-media-integrity/") =>
        _integrity_result(entry.metadata)
    }.sortBy(x => (x.record.articleIdentity, x.record.locale, x.record.role.name))
    CanonicalProjection(strict, integrities)
  }

  def upsert(
    root: Path,
    bundleName: String,
    publicationResult: CozyArticleMediaPublication.Result,
    integrityResults: Vector[CozyArticleMediaIntegrity.Result],
    expectedBundleDigests: Map[String, String] = Map.empty
  ): UpsertResult = {
    _validate_root(root)
    if (expectedBundleDigests == null)
      _invalid("Publication bundle expected configured digests must be defined")
    val name = _validate_bundle_name(bundleName)
    val bundlepath = root.resolve(s"$name.json")
    _validate_target_bundle(bundlepath, name)
    val snapshot = load(root)
    if (expectedBundleDigests.nonEmpty && expectedBundleDigests != snapshot.bundleDigests)
      _invalid("Publication bundle stale configured snapshot")
    val publication = _publication_entry(publicationResult)
    val integrities = _integrity_entries(publicationResult, integrityResults)
    val requested = (publication +: integrities).sortBy(x => (x.path, x.key))
    _validate_requested_duplicates(requested)
    _validate_requested_owners(snapshot, name, requested)
    _validate_article_owners(snapshot, name, publication.path)
    snapshot.bundleDigests.getOrElse(name,
      _invalid(s"Publication bundle digest is missing from configured snapshot: $name")
    )
    val articleidentity = publication.path.stripPrefix("metadata/article-media/").stripSuffix(".json")
    val staleintegritypaths = _integrity_entries_for_identity(snapshot, articleidentity).map(_.path)
    CozyPublicationCompiler.replaceMetadata(
      root,
      name,
      requested.map(x => x.path -> x.metadata),
      staleintegritypaths,
      snapshot.bundleDigests
    )
    UpsertResult(bundlepath, requested.map(_.path), load(root))
  }

  private final case class NormalizedRoleUpdate(
    articleidentity: String,
    variant: CozyArticleMediaPublication.Variant,
    integrity: Option[CozyArticleMediaIntegrity.Result],
    role: StrictRole
  ) {
    def key: (String, String, String) = (articleidentity, variant.locale, role.name)
  }

  private final case class NormalizedSiteRoleUpdate(
    articleidentity: String,
    variant: CozyArticleMediaPublication.Variant,
    role: StrictRole
  ) {
    def key: (String, String, String) = (articleidentity, variant.locale, role.name)
  }

  private final case class NormalizedWipRoleUpdate(
    articleidentity: String,
    variant: CozyArticleMediaPublication.Variant,
    integrity: Option[CozyArticleMediaIntegrity.Result],
    role: StrictRole
  ) {
    def key: (String, String, String) = (articleidentity, variant.locale, role.name)
  }

  private final case class OwnerPlan(
    owner: String,
    entries: Vector[Entry],
    removeprefixes: Vector[String],
    createarticlemedia: Boolean
  )

  private def _normalize_metadata_publications(
    values: Vector[CozyPublicationCompiler.MetadataPublication]
  ): Vector[CozyPublicationCompiler.MetadataPublication] = {
    if (values == null)
      _invalid("Article-media registry metadata publications must be defined")
    val normalized = values.map { value =>
      if (value == null || value.projectDir == null || value.entries == null)
        _invalid("Article-media registry metadata publication must be defined")
      val canonical = CozyPublicationCompiler.validateMetadataPublication(value)
      val name = _validate_bundle_name(canonical.name)
      canonical.copy(name = name, entries = canonical.entries.sortBy(_._1))
    }.sortBy(_.name)
    normalized.groupBy(_.name).collectFirst { case (name, xs) if xs.size > 1 => name }.foreach { name =>
      _invalid(s"Duplicate article-media metadata publication bundle: $name")
    }
    normalized
  }

  private def _project_metadata_publications(
    snapshot: Snapshot,
    publications: Vector[CozyPublicationCompiler.MetadataPublication]
  ): Snapshot = {
    val replacements = publications.flatMap { publication =>
      val entries = publication.entries.map { case (path, metadata) =>
        val validpath = _validate_path(path, publication.name)
        if (metadata == null)
          _invalid(s"Article-media registry metadata entry must be defined: $validpath")
        val key = _canonical_key(validpath)
        _validate_recognized_article_media(validpath, key, metadata, publication.name)
        Entry(publication.name, validpath, key, metadata)
      }
      _validate_requested_duplicates(entries)
      val requestedpaths = entries.map(_.path).toSet
      snapshot.entries.filter(_.bundleName == publication.name).filterNot { old =>
        requestedpaths.contains(old.path)
      } ++ entries
    }
    val names = publications.map(_.name).toSet
    val projected = snapshot.entries.filterNot(x => names.contains(x.bundleName)) ++ replacements
    _validate_duplicates(projected)
    Snapshot(projected.sortBy(x => (x.path, x.bundleName)), snapshot.bundleDigests)
  }

  private def _normalize_role_updates(roleupdates: Vector[RoleUpdate]): Vector[NormalizedRoleUpdate] = {
    if (roleupdates == null)
      _invalid("Article-media registry role updates must be defined")
    val normalized = roleupdates.map(_normalize_role_update).sortBy(_.key)
    normalized.groupBy(_.key).toVector.sortBy(_._1).collectFirst {
      case (key, values) if values.size > 1 => key
    }.foreach { key =>
      _invalid(s"Duplicate article-media role update: ${key._1} [${key._2}, ${key._3}]")
    }
    normalized
  }

  private def _normalize_site_role_updates(
    roleupdates: Vector[SiteRoleUpdate]
  ): Vector[NormalizedSiteRoleUpdate] = {
    if (roleupdates == null)
      _invalid("Article-media registry site role updates must be defined")
    val normalized = roleupdates.map(_normalize_site_role_update).sortBy(_.key)
    if (normalized.isEmpty)
      _invalid("Article-media registry site role updates must not be empty")
    normalized.groupBy(_.key).toVector.sortBy(_._1).collectFirst {
      case (key, values) if values.size > 1 => key
    }.foreach { key =>
      _invalid(s"Duplicate article-media site role update: ${key._1} [${key._2}, ${key._3}]")
    }
    normalized
  }

  private def _normalize_wip_role_updates(
    roleupdates: Vector[WipRoleUpdate]
  ): Vector[NormalizedWipRoleUpdate] = {
    if (roleupdates == null)
      _invalid("Article-media registry WIP role updates must be defined")
    val normalized = roleupdates.map(_normalize_wip_role_update).sortBy(_.key)
    if (normalized.isEmpty)
      _invalid("Article-media registry WIP role updates must not be empty")
    normalized.groupBy(_.key).toVector.sortBy(_._1).collectFirst {
      case (key, values) if values.size > 1 => key
    }.foreach { key =>
      _invalid(s"Duplicate article-media WIP role update: ${key._1} [${key._2}, ${key._3}]")
    }
    normalized
  }

  private def _normalize_role_intents(intents: Vector[RoleIntent]): Vector[RoleIntent] = {
    if (intents == null)
      _invalid("Article-media registry role intents must be defined")
    val normalized = intents.map { intent =>
      if (intent == null)
        _invalid("Article-media registry role intent must be defined")
      val identity = CozyArticleMediaNormalization.normalizeArticleIdentity(intent.articleIdentity)
      val locale = CozyArticleMediaNormalization.normalizeLocale(intent.locale)
      intent.role match {
        case CozyArticleMediaIntegrity.Role.Infographic | CozyArticleMediaIntegrity.Role.Video =>
          RoleIntent(identity, locale, intent.role)
        case _ => _invalid("Article-media registry role intent role is invalid")
      }
    }.sortBy(x => (x.articleIdentity, x.locale, x.role.name))
    normalized.groupBy(x => (x.articleIdentity, x.locale, x.role.name)).toVector.sortBy(_._1).collectFirst {
      case (key, values) if values.size > 1 => key
    }.foreach { key =>
      _invalid(s"Duplicate article-media role intent: ${key._1} [${key._2}, ${key._3}]")
    }
    normalized
  }

  private def _normalize_role_update(value: RoleUpdate): NormalizedRoleUpdate = {
    if (value == null || value.variant == null)
      _invalid("Article-media registry role update must be defined")
    val supplied = value.variant
    val strict = CozyArticleMediaPublication.produce(value.articleIdentity, Vector(supplied))
    val canonicalvariant = strict.publication.variants.head
    val variant = CozyArticleMediaPublication.Variant(
      locale = canonicalvariant.locale,
      infographic = canonicalvariant.infographic,
      video = canonicalvariant.video,
      articlePdf = canonicalvariant.articlePdf,
      summarySlidesPdf = canonicalvariant.summarySlidesPdf
    )
    val role = _strict_role(variant)
    val integrity = role match {
      case StrictRole.Infographic | StrictRole.Video =>
        val canonical = _canonical_integrity(Option(value.integrity).getOrElse(
          _invalid("Article-media registry role update integrity must be defined")
        ))
        val publicpath = _integrity_public_path(variant, role)
        val record = canonical.record
        val expectedrole = role match {
          case StrictRole.Infographic => CozyArticleMediaIntegrity.Role.Infographic
          case StrictRole.Video => CozyArticleMediaIntegrity.Role.Video
          case _ => _invalid("Article-media registry role update integrity role is invalid")
        }
        if (record.articleIdentity != strict.publication.articleIdentity || record.locale != variant.locale ||
          record.role != expectedrole || record.publicPath.toString != publicpath.toString)
          _invalid("Article-media registry role update strict and integrity evidence must have the same identity, locale, role, and public path")
        _validate_strict_integrities(strict, Vector(canonical))
        Some(canonical)
      case StrictRole.ArticlePdf | StrictRole.SummarySlidesPdf =>
        if (value.integrity != null)
          _invalid("Article-media registry PDF role update must not carry integrity")
        None
    }
    NormalizedRoleUpdate(strict.publication.articleIdentity, variant, integrity, role)
  }

  private def _strict_role(value: CozyArticleMediaPublication.Variant): StrictRole = {
    val roles = Vector(
      value.infographic.map(_ => StrictRole.Infographic),
      value.video.map(_ => StrictRole.Video),
      value.articlePdf.map(_ => StrictRole.ArticlePdf),
      value.summarySlidesPdf.map(_ => StrictRole.SummarySlidesPdf)
    ).flatten
    if (roles.size != 1)
      _invalid("Article-media registry role update variant must contain exactly one medium")
    roles.head
  }

  private def _integrity_public_path(
    value: CozyArticleMediaPublication.Variant,
    strictrole: StrictRole
  ): URI = strictrole match {
    case StrictRole.Infographic => value.infographic.map(_.publicPath).getOrElse(
      _invalid("Article-media registry infographic role update medium is missing")
    )
    case StrictRole.Video => value.video.flatMap { video =>
      if (video.presentation != VideoPresentation.SiteHosted || video.contentUrl.isEmpty)
        _invalid("Article-media registry video role update must be site-hosted with content_url")
      video.contentUrl
    }.getOrElse(_invalid("Article-media registry video role update medium is missing"))
    case StrictRole.ArticlePdf => value.articlePdf.map(_.publicPath).getOrElse(
      _invalid("Article-media registry article PDF role update medium is missing")
    )
    case StrictRole.SummarySlidesPdf => value.summarySlidesPdf.map(_.publicPath).getOrElse(
      _invalid("Article-media registry summary-slides PDF role update medium is missing")
    )
  }

  private def _normalize_site_role_update(value: SiteRoleUpdate): NormalizedSiteRoleUpdate = {
    if (value == null || value.variant == null)
      _invalid("Article-media registry site role update must be defined")
    val supplied = value.variant
    val strict = CozyArticleMediaPublication.produce(value.articleIdentity, Vector(supplied))
    val canonicalvariant = strict.publication.variants.head
    val variant = CozyArticleMediaPublication.Variant(
      locale = canonicalvariant.locale,
      infographic = canonicalvariant.infographic,
      video = canonicalvariant.video,
      articlePdf = canonicalvariant.articlePdf,
      summarySlidesPdf = canonicalvariant.summarySlidesPdf
    )
    val role = _strict_role(variant)
    variant.video.foreach { video =>
      if (video.presentation != VideoPresentation.ExternalLink)
        _invalid("Article-media registry site video role update must be an external-link")
    }
    NormalizedSiteRoleUpdate(strict.publication.articleIdentity, variant, role)
  }

  private def _normalize_wip_role_update(value: WipRoleUpdate): NormalizedWipRoleUpdate = {
    if (value == null || value.variant == null || value.integrity == null)
      _invalid("Article-media registry WIP role update must be defined")
    val supplied = value.variant
    val strict = CozyArticleMediaPublication.produce(value.articleIdentity, Vector(supplied))
    val canonicalvariant = strict.publication.variants.head
    val variant = CozyArticleMediaPublication.Variant(
      locale = canonicalvariant.locale,
      infographic = canonicalvariant.infographic,
      video = canonicalvariant.video,
      articlePdf = canonicalvariant.articlePdf,
      summarySlidesPdf = canonicalvariant.summarySlidesPdf
    )
    val role = _strict_role(variant)
    role match {
      case StrictRole.Infographic =>
        if (value.integrity.nonEmpty)
          _invalid("Article-media registry WIP infographic update must not carry integrity")
      case StrictRole.ArticlePdf | StrictRole.SummarySlidesPdf =>
        if (value.integrity.nonEmpty)
          _invalid("Article-media registry WIP PDF update must not carry integrity")
      case StrictRole.Video =>
        val video = variant.video.getOrElse(_invalid("Article-media registry WIP video update is missing"))
        if (video.presentation != VideoPresentation.SiteHosted || video.status != VideoStatus.Published ||
          video.provider.nonEmpty || video.watchUrl.nonEmpty || video.contentUrl.isEmpty)
          _invalid("Article-media registry WIP video update must be provider-neutral site-hosted published content_url only")
        val integrity = value.integrity.getOrElse(
          _invalid("Article-media registry WIP video update requires integrity")
        )
        val canonical = _canonical_integrity(integrity)
        canonical.record.provenance match {
          case _: CozyArticleMediaIntegrity.WipSiteVideo =>
          case _ => _invalid("Article-media registry WIP video integrity requires wip-site-video provenance")
        }
        if (canonical.record.articleIdentity != strict.publication.articleIdentity ||
          canonical.record.locale != variant.locale ||
          canonical.record.role != CozyArticleMediaIntegrity.Role.Video ||
          canonical.record.publicPath.toString != video.contentUrl.get.toString ||
          canonical.record.publicationState != CozyArticleMediaIntegrity.PublicationState.Published)
          _invalid("Article-media registry WIP video strict and integrity evidence must have the same published tuple")
      case _ => _invalid("Article-media registry WIP role is invalid")
    }
    val integrity = value.integrity.map(_canonical_integrity)
    NormalizedWipRoleUpdate(strict.publication.articleIdentity, variant, integrity, role)
  }

  private def _canonical_integrity(value: CozyArticleMediaIntegrity.Result): CozyArticleMediaIntegrity.Result = {
    if (value == null || value.record == null || value.metadata == null)
      _invalid("Article-media integrity result must be defined")
    val record = value.record
    val canonical = CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = record.articleIdentity,
      locale = record.locale,
      role = record.role,
      artifact = record.artifact,
      publicPath = record.publicPath,
      repositoryPath = record.repositoryPath,
      mediaType = record.mediaType,
      sha256 = record.sha256,
      provenance = record.provenance,
      publicationState = record.publicationState
    ))
    if (value != canonical)
      _invalid("Article-media integrity result must be canonical")
    canonical
  }

  private def _plan_role_updates(snapshot: Snapshot, updates: Vector[NormalizedRoleUpdate]): Vector[OwnerPlan] = {
    val byarticle = updates.groupBy(_.articleidentity).toVector.sortBy(_._1)
    val plans = byarticle.map { case (identity, articleupdates) =>
      val owner = _article_owner(snapshot, identity)
      val existingstrict = snapshot.entries.find(_.path == s"metadata/article-media/$identity.json").map(x => _strict_result(x.metadata))
      val existingintegrities = _integrity_entries_for_identity(snapshot, identity).map(x => _integrity_result(x.metadata))
      val strict = _merge_strict(identity, existingstrict, articleupdates)
      val integrities = _merge_integrities(existingintegrities, articleupdates)
      val integrityupdates = articleupdates.exists(_.integrity.nonEmpty)
      _validate_strict_integrities(strict, integrities)
      val entries = (_publication_entry(strict) +: (if (integrityupdates) integrities else Vector.empty).map(x => Entry("", x.entryPath, _canonical_key(x.entryPath), x.metadata))).sortBy(_.path)
      owner -> OwnerPlan(
        owner = owner,
        entries = entries,
        removeprefixes = if (integrityupdates) _integrity_entries_for_identity(snapshot, identity).map(_.path) else Vector.empty,
        createarticlemedia = owner == "article-media" && !snapshot.bundleDigests.contains("article-media")
      )
    }
    plans.groupBy(_._1).toVector.sortBy(_._1).map { case (owner, values) =>
      OwnerPlan(
        owner = owner,
        entries = values.flatMap(_._2.entries).sortBy(_.path),
        removeprefixes = values.flatMap(_._2.removeprefixes).distinct.sorted,
        createarticlemedia = values.exists(_._2.createarticlemedia)
      )
    }
  }

  private def _plan_site_role_updates(
    snapshot: Snapshot,
    updates: Vector[NormalizedSiteRoleUpdate]
  ): Vector[OwnerPlan] = {
    val plans = updates.groupBy(_.articleidentity).toVector.sortBy(_._1).map { case (identity, articleupdates) =>
      val owner = _article_owner(snapshot, identity)
      val existingstrict = snapshot.entries.find(_.path == s"metadata/article-media/$identity.json").map(x => _strict_result(x.metadata))
      val existingintegrities = _integrity_entries_for_identity(snapshot, identity).map(x => _integrity_result(x.metadata))
      val strict = _merge_site_strict(identity, existingstrict, articleupdates)
      _validate_strict_integrities(strict, existingintegrities)
      OwnerPlan(
        owner = owner,
        entries = Vector(_publication_entry(strict)),
        removeprefixes = Vector.empty,
        createarticlemedia = owner == "article-media" && !snapshot.bundleDigests.contains("article-media")
      )
    }
    plans.groupBy(_.owner).toVector.sortBy(_._1).map { case (owner, values) =>
      OwnerPlan(
        owner = owner,
        entries = values.flatMap(_.entries).sortBy(_.path),
        removeprefixes = Vector.empty,
        createarticlemedia = values.exists(_.createarticlemedia)
      )
    }
  }

  private def _plan_wip_role_updates(
    snapshot: Snapshot,
    updates: Vector[NormalizedWipRoleUpdate]
  ): WipReadOnlyPlan = {
    val videostates = updates.collect {
      case update if update.role == StrictRole.Video =>
        val existingstrict = snapshot.entries.find(_.path == s"metadata/article-media/${update.articleidentity}.json").map(x => _strict_result(x.metadata))
        val existingintegrities = _integrity_entries_for_identity(snapshot, update.articleidentity).map(x => _integrity_result(x.metadata))
        update.key -> _wip_video_state(existingstrict, existingintegrities, update)
    }.toMap
    val articles = updates.groupBy(_.articleidentity).toVector.sortBy(_._1).map { case (identity, articleupdates) =>
      val owner = _article_owner(snapshot, identity)
      val existingstrict = snapshot.entries.find(_.path == s"metadata/article-media/$identity.json").map(x => _strict_result(x.metadata))
      val existingintegrities = _integrity_entries_for_identity(snapshot, identity).map(x => _integrity_result(x.metadata))
      val strict = _merge_wip_strict(identity, existingstrict, articleupdates)
      val integrities = _merge_wip_integrities(existingintegrities, articleupdates)
      val integrityupdates = articleupdates.exists(_.integrity.nonEmpty)
      _validate_strict_integrities(strict, integrities)
      val entries = (_publication_entry(strict) +: (if (integrityupdates) integrities else Vector.empty).map(x => Entry("", x.entryPath, _canonical_key(x.entryPath), x.metadata))).sortBy(_.path)
      _validate_plans(snapshot, Vector(OwnerPlan(
        owner = owner,
        entries = entries,
        removeprefixes = if (integrityupdates) _integrity_entries_for_identity(snapshot, identity).map(_.path) else Vector.empty,
        createarticlemedia = owner == "article-media" && !snapshot.bundleDigests.contains("article-media")
      )))
      WipArticlePlan(identity, owner, strict, integrities)
    }
    WipReadOnlyPlan(snapshot, articles, videostates)
  }

  private def _wip_video_state(
    existingstrict: Option[CozyArticleMediaPublication.Result],
    existingintegrities: Vector[CozyArticleMediaIntegrity.Result],
    update: NormalizedWipRoleUpdate
  ): WipVideoState = {
    val existingvideo = existingstrict.flatMap(_.publication.variants.find(_.locale == update.variant.locale)).flatMap(_.video)
    val existingintegrity = existingintegrities.filter { value =>
      value.record.locale == update.variant.locale && value.record.role == CozyArticleMediaIntegrity.Role.Video
    }
    (existingvideo, existingintegrity) match {
      case (None, Vector()) => WipVideoState.Fresh
      case (Some(video), Vector(integrity)) =>
        val expectedvideo = update.variant.video.getOrElse(
          _invalid("Article-media registry WIP video update is missing")
        )
        val expectedintegrity = update.integrity.getOrElse(
          _invalid("Article-media registry WIP video integrity is missing")
        )
        if (video != expectedvideo || integrity != expectedintegrity)
          _invalid(s"Article-media registry existing video tuple is not the exact current WIP pair: ${update.articleidentity} [${update.variant.locale}]")
        WipVideoState.ExactRepeat
      case _ =>
        _invalid(s"Article-media registry existing video tuple is partial or not WIP-owned: ${update.articleidentity} [${update.variant.locale}]")
    }
  }

  private def _merge_wip_strict(
    identity: String,
    existing: Option[CozyArticleMediaPublication.Result],
    updates: Vector[NormalizedWipRoleUpdate]
  ): CozyArticleMediaPublication.Result = {
    val initial = existing.map(_.publication.variants.map { variant =>
      variant.locale -> CozyArticleMediaPublication.Variant(
        locale = variant.locale,
        infographic = variant.infographic,
        video = variant.video,
        articlePdf = variant.articlePdf,
        summarySlidesPdf = variant.summarySlidesPdf
      )
    }.toMap).getOrElse(Map.empty[String, CozyArticleMediaPublication.Variant])
    val merged = updates.foldLeft(initial) { case (state, update) =>
      val previous = state.getOrElse(update.variant.locale, CozyArticleMediaPublication.Variant(update.variant.locale))
      val replacement = update.role match {
        case StrictRole.Infographic => previous.copy(infographic = update.variant.infographic)
        case StrictRole.Video => previous.copy(video = update.variant.video)
        case StrictRole.ArticlePdf => previous.copy(articlePdf = update.variant.articlePdf)
        case StrictRole.SummarySlidesPdf => previous.copy(summarySlidesPdf = update.variant.summarySlidesPdf)
        case _ => _invalid("Article-media registry WIP role is invalid")
      }
      state + (replacement.locale -> replacement)
    }
    CozyArticleMediaPublication.produce(identity, merged.values.toVector)
  }

  private def _merge_wip_integrities(
    existing: Vector[CozyArticleMediaIntegrity.Result],
    updates: Vector[NormalizedWipRoleUpdate]
  ): Vector[CozyArticleMediaIntegrity.Result] = {
    val replacements = updates.collect {
      case update if update.role == StrictRole.Video =>
        (update.variant.locale, update.role.name) -> update.integrity.getOrElse(
          _invalid("Article-media registry WIP video integrity is missing")
        )
    }.toMap
    (existing.filterNot { value => replacements.contains((value.record.locale, value.record.role.name)) } ++ replacements.values).sortBy { value =>
      (value.record.locale, value.record.role.name)
    }
  }

  private def _article_owner(snapshot: Snapshot, identity: String): String = {
    val strictpath = s"metadata/article-media/$identity.json"
    val owners = (
      snapshot.entries.collect {
        case entry if entry.path == strictpath => entry.bundleName
      } ++ _integrity_entries_for_identity(snapshot, identity).map(_.bundleName)
    ).distinct.sorted
    if (owners.size > 1)
      _invalid(s"Article-media registry identity has multiple bundle owners: $identity (${owners.mkString(", ")})")
    owners.headOption.getOrElse("article-media")
  }

  private def _validate_role_intent_path(snapshot: Snapshot, intent: RoleIntent, owner: String): Unit = {
    val strictpath = s"metadata/article-media/${intent.articleIdentity}.json"
    val rolepath = s"metadata/article-media-integrity/${intent.articleIdentity}/${intent.locale}/${intent.role.name}.json"
    snapshot.entries.filter(x => x.path == strictpath || x.path == rolepath).foreach { entry =>
      if (entry.bundleName != owner)
        _invalid(s"Publication registry entry is owned by another bundle: ${entry.path} (${entry.bundleName})")
    }
  }

  private def _merge_strict(
    identity: String,
    existing: Option[CozyArticleMediaPublication.Result],
    updates: Vector[NormalizedRoleUpdate]
  ): CozyArticleMediaPublication.Result = {
    val initial = existing.map(_.publication.variants.map { variant =>
      variant.locale -> CozyArticleMediaPublication.Variant(
        locale = variant.locale,
        infographic = variant.infographic,
        video = variant.video,
        articlePdf = variant.articlePdf,
        summarySlidesPdf = variant.summarySlidesPdf
      )
    }.toMap).getOrElse(Map.empty[String, CozyArticleMediaPublication.Variant])
    val merged = updates.foldLeft(initial) { case (state, update) =>
      val previous = state.getOrElse(update.variant.locale, CozyArticleMediaPublication.Variant(update.variant.locale))
      val replacement = update.role match {
        case StrictRole.Infographic => previous.copy(infographic = update.variant.infographic)
        case StrictRole.Video => previous.copy(video = update.variant.video)
        case StrictRole.ArticlePdf => previous.copy(articlePdf = update.variant.articlePdf)
        case StrictRole.SummarySlidesPdf => previous.copy(summarySlidesPdf = update.variant.summarySlidesPdf)
        case _ => _invalid("Article-media registry role update role is invalid")
      }
      state + (replacement.locale -> replacement)
    }
    CozyArticleMediaPublication.produce(identity, merged.values.toVector)
  }

  private def _merge_site_strict(
    identity: String,
    existing: Option[CozyArticleMediaPublication.Result],
    updates: Vector[NormalizedSiteRoleUpdate]
  ): CozyArticleMediaPublication.Result = {
    val initial = existing.map(_.publication.variants.map { variant =>
      variant.locale -> CozyArticleMediaPublication.Variant(
        locale = variant.locale,
        infographic = variant.infographic,
        video = variant.video,
        articlePdf = variant.articlePdf,
        summarySlidesPdf = variant.summarySlidesPdf
      )
    }.toMap).getOrElse(Map.empty[String, CozyArticleMediaPublication.Variant])
    val merged = updates.foldLeft(initial) { case (state, update) =>
      val previous = state.getOrElse(update.variant.locale, CozyArticleMediaPublication.Variant(update.variant.locale))
      val replacement = update.role match {
        case StrictRole.Infographic => previous.copy(infographic = update.variant.infographic)
        case StrictRole.Video => previous.copy(video = update.variant.video)
        case StrictRole.ArticlePdf => previous.copy(articlePdf = update.variant.articlePdf)
        case StrictRole.SummarySlidesPdf => previous.copy(summarySlidesPdf = update.variant.summarySlidesPdf)
        case _ => _invalid("Article-media registry site role update role is invalid")
      }
      state + (replacement.locale -> replacement)
    }
    CozyArticleMediaPublication.produce(identity, merged.values.toVector)
  }

  private def _merge_integrities(
    existing: Vector[CozyArticleMediaIntegrity.Result],
    updates: Vector[NormalizedRoleUpdate]
  ): Vector[CozyArticleMediaIntegrity.Result] = {
    val replacements = updates.collect {
      case update if update.integrity.nonEmpty =>
        (update.variant.locale, update.role.name) -> update.integrity.get
    }.toMap
    (existing.filterNot { value => replacements.contains((value.record.locale, value.record.role.name)) } ++ replacements.values).sortBy { value =>
      (value.record.locale, value.record.role.name)
    }
  }

  private def _validate_plans(snapshot: Snapshot, plans: Vector[OwnerPlan]): Unit = {
    val owners = plans.map(_.owner)
    if (owners.distinct.size != owners.size)
      _invalid("Article-media registry owner plan is not deterministic")
    plans.foreach { plan =>
      _validate_bundle_name(plan.owner)
      if (plan.createarticlemedia && plan.owner != "article-media")
        _invalid("Article-media registry creation is limited to article-media")
      _validate_requested_duplicates(plan.entries)
      plan.entries.foreach { entry =>
        snapshot.entries.find(x => x.path == entry.path && x.bundleName != plan.owner).foreach { current =>
          _invalid(s"Publication registry entry is owned by another bundle: ${entry.path} (${current.bundleName})")
        }
      }
    }
  }

  private def _validate_strict_integrities(
    strict: CozyArticleMediaPublication.Result,
    integrities: Vector[CozyArticleMediaIntegrity.Result]
  ): Unit = {
    CozyArticleMediaAssociation.inspectPublication(strict, integrities)
    strict.publication.variants.foreach { variant =>
      variant.video.foreach { video =>
        if (video.presentation == VideoPresentation.SiteHosted && video.status == VideoStatus.Published && video.contentUrl.nonEmpty) {
          val integrity = integrities.find(x => x.record.locale == variant.locale && x.record.role == CozyArticleMediaIntegrity.Role.Video).getOrElse(
            _invalid(s"Missing article-media integrity: ${strict.publication.articleIdentity} [${variant.locale}, video]")
          )
          if (integrity.record.publicationState != CozyArticleMediaIntegrity.PublicationState.Published)
            _invalid(s"Published site-hosted article-media video requires published Cozy integrity: ${strict.publication.articleIdentity} [${variant.locale}]")
        }
      }
    }
  }

  private def _is_direct_json_filename(path: Path): Boolean =
    path.getFileName.toString.endsWith(".json")

  private def _load_bundle(path: Path): Bundle = {
    val filename = path.getFileName.toString
    if (Files.isSymbolicLink(path))
      _invalid(s"Configured publication bundle must not be a symbolic link: $filename")
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Configured publication bundle must be a direct regular file: $filename")
    val bytes = Files.readAllBytes(path)
    val json = Try(Json.parse(new String(bytes, StandardCharsets.UTF_8))).getOrElse(
      _invalid(s"Configured publication bundle is malformed JSON: $filename")
    )
    val bundle = json.asOpt[JsObject].getOrElse(
      _invalid(s"Configured publication bundle must be a JSON object: $filename")
    )
    if ((bundle \ "type").asOpt[String].forall(_ != "publication-bundle"))
      _invalid(s"Configured publication bundle has invalid type: $filename")
    val name = _bundle_name(bundle, filename)
    val stem = _filename_stem(filename)
    if (name != stem)
      _invalid(s"Configured publication bundle publication.name must match filename stem: $filename")
    val entries = bundle.value.get("entries") match {
      case Some(JsArray(values)) => values.toVector
      case _ => _invalid(s"Configured publication bundle entries must be an array: $name")
    }
    Bundle(filename, name, entries.map(_entry(name, _)), _sha256(bytes))
  }

  private def _bundle_name(bundle: JsObject, filename: String): String = {
    val publication = (bundle \ "publication").asOpt[JsObject].getOrElse(
      _invalid(s"Configured publication bundle has invalid publication: $filename")
    )
    publication.value.get("name") match {
      case Some(JsString(name)) if name.matches("[A-Za-z0-9][A-Za-z0-9._-]*") && name != "." && name != ".." &&
        !name.contains('/') && !name.contains('\\') && !name.contains('\u0000') => _validate_bundle_name(name)
      case _ => _invalid(s"Configured publication bundle has invalid publication.name: $filename")
    }
  }

  private def _entry(bundlename: String, value: JsValue): Entry = {
    val entry = value.asOpt[JsObject].getOrElse(
      _invalid(s"Configured publication bundle entry must be an object: $bundlename")
    )
    val path = entry.value.get("path") match {
      case Some(JsString(value)) => _validate_path(value, bundlename)
      case _ => _invalid(s"Configured publication bundle entry path is invalid: $bundlename")
    }
    val key = _canonical_key(path)
    entry.value.get("key").foreach {
      case JsString(value) if value == key =>
      case _ => _invalid(s"Configured publication bundle entry key is invalid for $path in $bundlename")
    }
    val metadata = entry.value.getOrElse("metadata", _invalid(s"Configured publication bundle entry metadata is missing for $path in $bundlename"))
    _validate_recognized_article_media(path, key, metadata, bundlename)
    Entry(bundlename, path, key, metadata)
  }

  private def _validate_recognized_article_media(path: String, key: String, metadata: JsValue, bundlename: String): Unit =
    if (path.startsWith("metadata/article-media/")) {
      val result = _guarded("Article-media publication metadata is invalid", _strict_result(metadata))
      _validate_canonical_recognized_entry(path, key, metadata, result.entryPath, result.metadata, bundlename)
    } else if (path.startsWith("metadata/article-media-integrity/")) {
      val result = _guarded("Article-media integrity metadata is invalid", _integrity_result(metadata))
      _validate_canonical_recognized_entry(path, key, metadata, result.entryPath, result.metadata, bundlename)
    }

  private def _strict_result(metadata: JsValue): CozyArticleMediaPublication.Result = {
    val value = _json_object(metadata)
    _validate_fields(value, Set("type", "article", "variants"), Set.empty)
    if (_required_string(value, "type") != "article-media-publication")
      _invalid("Article-media publication metadata type is invalid")
    val article = _json_object(_required(value, "article"))
    _validate_fields(article, Set("identity"), Set.empty)
    val variants = _json_object(_required(value, "variants"))
    val parsedvariants = variants.fields.toVector.map { case (locale, variant) =>
      _strict_variant(locale, variant)
    }
    CozyArticleMediaPublication.produce(_required_string(article, "identity"), parsedvariants)
  }

  private def _strict_variant(locale: String, value: JsValue): CozyArticleMediaPublication.Variant = {
    val variant = _json_object(value)
    _validate_fields(variant, Set.empty, Set("infographic", "article_pdf", "summary_slides_pdf", "video"))
    CozyArticleMediaPublication.Variant(
      locale = locale,
      infographic = variant.value.get("infographic").map(_strict_infographic),
      video = variant.value.get("video").map(_strict_video),
      articlePdf = variant.value.get("article_pdf").map(_strict_article_pdf),
      summarySlidesPdf = variant.value.get("summary_slides_pdf").map(_strict_summary_slides_pdf)
    )
  }

  private def _strict_article_pdf(value: JsValue): PdfDocumentReference =
    _strict_pdf(value, "article_pdf")

  private def _strict_summary_slides_pdf(value: JsValue): PdfDocumentReference =
    _strict_pdf(value, "summary_slides_pdf")

  private def _strict_pdf(value: JsValue, role: String): PdfDocumentReference = {
    val pdf = _json_object(value)
    _validate_fields(pdf, Set("public_path", "media_type"), Set("label"))
    val mediatype = _required_string(pdf, "media_type")
    if (mediatype != "application/pdf")
      _invalid(s"Article-media $role media_type must be application/pdf")
    val label = pdf.value.get("label").map {
      case JsString(string) if string.trim.nonEmpty => string
      case _ => _invalid(s"Article-media $role label must be nonblank")
    }
    val publicpath = _uri(_required_string(pdf, "public_path"))
    CozyArticleMediaNormalization.validateSiteVisibleUri(publicpath, s"Article-media $role public_path")
    PdfDocumentReference(publicpath, mediatype, label)
  }

  private def _strict_infographic(value: JsValue): ImageReference = {
    val infographic = _json_object(value)
    _validate_fields(infographic, Set("public_path"), Set("media_type", "alt"))
    ImageReference(
      publicPath = _uri(_required_string(infographic, "public_path")),
      mediaType = _optional_string(infographic, "media_type"),
      alt = _optional_string(infographic, "alt")
    )
  }

  private def _strict_video(value: JsValue): VideoReference = {
    val video = _json_object(value)
    _validate_fields(video, Set("presentation", "status"), Set("provider", "watch_url", "content_url"))
    VideoReference(
      presentation = _video_presentation(_required_string(video, "presentation")),
      status = _video_status(_required_string(video, "status")),
      provider = _optional_string(video, "provider"),
      watchUrl = _optional_string(video, "watch_url").map(_uri),
      contentUrl = _optional_string(video, "content_url").map(_uri)
    )
  }

  private def _integrity_result(metadata: JsValue): CozyArticleMediaIntegrity.Result = {
    val value = _json_object(metadata)
    _validate_fields(value, Set("schema", "articleIdentity", "locale", "role", "artifact", "publicPath", "repositoryPath", "mediaType", "sha256", "provenance", "publicationState"), Set.empty)
    if (_required_string(value, "schema") != "cozy.article-media-integrity.v1")
      _invalid("Article-media integrity metadata schema is invalid")
    val role = _integrity_role(_required_string(value, "role"))
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = _required_string(value, "articleIdentity"),
      locale = _required_string(value, "locale"),
      role = role,
      artifact = _integrity_artifact(_required(value, "artifact")),
      publicPath = _uri(_required_string(value, "publicPath")),
      repositoryPath = _required_string(value, "repositoryPath"),
      mediaType = _required_string(value, "mediaType"),
      sha256 = _required_string(value, "sha256"),
      provenance = _integrity_provenance(_required(value, "provenance")),
      publicationState = _publication_state(_required_string(value, "publicationState"))
    ))
  }

  private def _integrity_artifact(value: JsValue): CozyArticleMediaIntegrity.Artifact = {
    val artifact = _json_object(value)
    _validate_fields(artifact, Set("identity", "version"), Set.empty)
    CozyArticleMediaIntegrity.Artifact(_required_string(artifact, "identity"), _required_string(artifact, "version"))
  }

  private def _integrity_provenance(value: JsValue): CozyArticleMediaIntegrity.Provenance = {
    val provenance = _json_object(value)
    _required_string(provenance, "kind") match {
      case "video-publication" =>
        _validate_fields(provenance, Set("kind", "videoManifest", "repositoryRegistry"), Set.empty)
        CozyArticleMediaIntegrity.VideoPublication(
          _required_string(provenance, "videoManifest"),
          _required_string(provenance, "repositoryRegistry")
        )
      case "media-package" =>
        _validate_fields(provenance, Set("kind", "descriptor", "resourceId", "buildManifest"), Set.empty)
        CozyArticleMediaIntegrity.MediaPackage(
          _required_string(provenance, "descriptor"),
          _required_string(provenance, "resourceId"),
          _required_string(provenance, "buildManifest")
        )
      case "wip-site-video" =>
        _validate_fields(provenance, Set("kind", "descriptor", "resourceId", "production"), Set.empty)
        CozyArticleMediaIntegrity.WipSiteVideo(
          _required_string(provenance, "descriptor"),
          _required_string(provenance, "resourceId"),
          _required_string(provenance, "production")
        )
      case _ => _invalid("Article-media integrity provenance kind is invalid")
    }
  }

  private def _video_presentation(value: String): VideoPresentation =
    value match {
      case x if x == VideoPresentation.ExternalLink.name => VideoPresentation.ExternalLink
      case x if x == VideoPresentation.SiteHosted.name => VideoPresentation.SiteHosted
      case _ => _invalid("Article-media video presentation is invalid")
    }

  private def _video_status(value: String): VideoStatus =
    value match {
      case x if x == VideoStatus.Draft.name => VideoStatus.Draft
      case x if x == VideoStatus.Published.name => VideoStatus.Published
      case x if x == VideoStatus.Withdrawn.name => VideoStatus.Withdrawn
      case _ => _invalid("Article-media video status is invalid")
    }

  private def _integrity_role(value: String): CozyArticleMediaIntegrity.Role =
    value match {
      case x if x == CozyArticleMediaIntegrity.Role.Infographic.name => CozyArticleMediaIntegrity.Role.Infographic
      case x if x == CozyArticleMediaIntegrity.Role.Video.name => CozyArticleMediaIntegrity.Role.Video
      case _ => _invalid("Article-media integrity role is invalid")
    }

  private def _publication_state(value: String): CozyArticleMediaIntegrity.PublicationState =
    value match {
      case x if x == CozyArticleMediaIntegrity.PublicationState.Registered.name => CozyArticleMediaIntegrity.PublicationState.Registered
      case x if x == CozyArticleMediaIntegrity.PublicationState.Published.name => CozyArticleMediaIntegrity.PublicationState.Published
      case x if x == CozyArticleMediaIntegrity.PublicationState.Withdrawn.name => CozyArticleMediaIntegrity.PublicationState.Withdrawn
      case _ => _invalid("Article-media integrity publication state is invalid")
    }

  private def _validate_canonical_recognized_entry(
    path: String,
    key: String,
    metadata: JsValue,
    canonicalpath: String,
    canonicalmetadata: JsObject,
    bundlename: String
  ): Unit =
    if (path != canonicalpath || key != _canonical_key(canonicalpath) || metadata != canonicalmetadata)
      _invalid(s"Configured publication bundle article-media entry is not canonical: $path in $bundlename")

  private def _guarded[A](message: String, f: => A): A =
    Try(f).getOrElse(_invalid(message))

  private def _json_object(value: JsValue): JsObject =
    value.asOpt[JsObject].getOrElse(_invalid("Article-media metadata value must be an object"))

  private def _validate_fields(value: JsObject, required: Set[String], optional: Set[String]): Unit = {
    val keys = value.keys
    if (!required.subsetOf(keys) || !keys.subsetOf(required ++ optional))
      _invalid("Article-media metadata fields are invalid")
  }

  private def _required(value: JsObject, name: String): JsValue =
    value.value.getOrElse(name, _invalid(s"Article-media metadata field is required: $name"))

  private def _required_string(value: JsObject, name: String): String =
    _required(value, name) match {
      case JsString(string) => string
      case _ => _invalid(s"Article-media metadata field must be a string: $name")
    }

  private def _optional_string(value: JsObject, name: String): Option[String] =
    value.value.get(name).map {
      case JsString(string) => string
      case _ => _invalid(s"Article-media metadata field must be a string: $name")
    }

  private def _uri(value: String): URI =
    new URI(value)

  private def _publication_entry(result: CozyArticleMediaPublication.Result): Entry = {
    if (result == null || result.publication == null || result.metadata == null)
      _invalid("Article-media publication result must be defined")
    val variants = Option(result.publication.variants).getOrElse(
      _invalid("Article-media publication result variants must be defined")
    ).map { variant =>
      if (variant == null || variant.infographic == null || variant.video == null || variant.articlePdf == null || variant.summarySlidesPdf == null)
        _invalid("Article-media publication result variant must be defined")
      CozyArticleMediaPublication.Variant(
        locale = variant.locale,
        infographic = variant.infographic,
        video = variant.video,
        articlePdf = variant.articlePdf,
        summarySlidesPdf = variant.summarySlidesPdf
      )
    }
    val canonical = CozyArticleMediaPublication.produce(result.publication.articleIdentity, variants)
    if (result != canonical)
      _invalid("Article-media publication result must be canonical")
    Entry("", canonical.entryPath, _canonical_key(canonical.entryPath), canonical.metadata)
  }

  private def _integrity_entries(
    publicationresult: CozyArticleMediaPublication.Result,
    integrityresults: Vector[CozyArticleMediaIntegrity.Result]
  ): Vector[Entry] = {
    if (publicationresult == null || publicationresult.publication == null)
      _invalid("Article-media publication result must be defined")
    if (integrityresults == null)
      _invalid("Article-media integrity results must be defined")
    val publication = _publication_entry(publicationresult)
    val identity = publication.path.stripPrefix("metadata/article-media/").stripSuffix(".json")
    integrityresults.map { result =>
      if (result == null || result.record == null || result.metadata == null)
        _invalid("Article-media integrity result must be defined")
      val record = result.record
      val canonical = CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
        articleIdentity = record.articleIdentity,
        locale = record.locale,
        role = record.role,
        artifact = record.artifact,
        publicPath = record.publicPath,
        repositoryPath = record.repositoryPath,
        mediaType = record.mediaType,
        sha256 = record.sha256,
        provenance = record.provenance,
        publicationState = record.publicationState
      ))
      if (result != canonical)
        _invalid("Article-media integrity result must be canonical")
      val recordidentity = canonical.record.articleIdentity
      if (recordidentity != identity)
        _invalid(s"Article-media integrity identity must match strict publication identity: $recordidentity")
      Entry("", canonical.entryPath, _canonical_key(canonical.entryPath), canonical.metadata)
    }
  }

  private def _validate_target_bundle(bundlepath: Path, bundlename: String): Unit = {
    if (Files.isSymbolicLink(bundlepath))
      _invalid(s"Publication bundle target must not be a symbolic link: $bundlename")
    if (!Files.isRegularFile(bundlepath, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Publication bundle not found: $bundlename")
  }

  private def _validate_root(root: Path): Unit =
    if (root == null || !Files.isDirectory(root))
      _invalid(s"Configured publication root must be an existing directory: $root")

  private def _validate_bundle_name(bundlename: String): String = {
    val name = Option(bundlename).getOrElse("")
    if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*") || name == "." || name == ".." ||
      name.contains('/') || name.contains('\\') || name.contains('\u0000'))
      _invalid(s"Publication bundle name must be a safe filename segment: $bundlename")
    name
  }

  private def _filename_stem(filename: String): String = {
    if (!filename.endsWith(".json"))
      _invalid(s"Configured publication bundle filename must end with .json: $filename")
    _validate_bundle_name(filename.stripSuffix(".json"))
  }

  private def _validate_path(value: String, bundlename: String): String = {
    val path = Option(value).getOrElse("")
    val segments = path.split("/", -1).toVector
    if (path.isEmpty || path != path.trim || path.startsWith("/") || path.contains('\\') || path.contains('\u0000') ||
      !path.startsWith("metadata/") || !path.endsWith(".json") || segments.exists(_.isEmpty) ||
      segments.contains(".") || segments.contains(".."))
      _invalid(s"Configured publication bundle entry path is invalid in $bundlename: $value")
    val key = _canonical_key(path)
    if (key.isEmpty || key.split("/", -1).exists(_.isEmpty))
      _invalid(s"Configured publication bundle entry path has an empty key in $bundlename: $value")
    path
  }

  private def _canonical_key(path: String): String =
    path.stripSuffix(".json").stripPrefix("metadata/")

  private def _validate_duplicates(entries: Vector[Entry]): Unit = {
    _duplicate(entries, _.path, "path")
    _duplicate(entries, _.key, "logical key")
  }

  private def _validate_requested_duplicates(entries: Vector[Entry]): Unit = {
    _duplicate(entries, _.path, "requested path")
    _duplicate(entries, _.key, "requested logical key")
  }

  private def _duplicate(entries: Vector[Entry], selector: Entry => String, label: String): Unit =
    entries.groupBy(selector).toVector.sortBy(_._1).collectFirst {
      case (value, owners) if owners.size > 1 =>
        val names = owners.map(_.bundleName).filter(_.nonEmpty).sorted
        val ownerlabel = if (names.nonEmpty) names.mkString(", ") else "requested entries"
        _invalid(s"Duplicate publication bundle $label '$value': $ownerlabel")
    }

  private def _validate_requested_owners(snapshot: Snapshot, bundlename: String, requested: Vector[Entry]): Unit =
    requested.foreach { entry =>
      snapshot.entries.find(x => (x.path == entry.path || x.key == entry.key) && x.bundleName != bundlename).foreach { owner =>
        _invalid(s"Publication registry entry is owned by another bundle: ${entry.path} (${owner.bundleName})")
      }
    }

  private def _validate_article_owners(snapshot: Snapshot, bundlename: String, publicationpath: String): Unit = {
    val identity = publicationpath.stripPrefix("metadata/article-media/").stripSuffix(".json")
    val current = snapshot.entries.filter(_.path == publicationpath) ++ _integrity_entries_for_identity(snapshot, identity)
    current.find(_.bundleName != bundlename).foreach { owner =>
      _invalid(s"Publication registry article-media identity is owned by another bundle: ${owner.path} (${owner.bundleName})")
    }
  }

  private def _integrity_entries_for_identity(snapshot: Snapshot, identity: String): Vector[Entry] =
    snapshot.entries.filter(_.path.startsWith("metadata/article-media-integrity/")).filter { entry =>
      _integrity_result(entry.metadata).record.articleIdentity == identity
    }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(x => f"${x & 0xff}%02x").mkString

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
