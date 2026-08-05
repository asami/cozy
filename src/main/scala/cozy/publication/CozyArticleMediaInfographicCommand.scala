package cozy.publication

import java.nio.file.{Files, LinkOption, Path}
import cozy.bok.CozyBok
import cozy.media.CozyMedia
import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import scala.collection.JavaConverters._
import scala.util.Try

/*
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaInfographicCommand {
  private val _descriptor_names = Set("media.yaml", "media.yml", "media.json", "media.conf", "media.xml")

  final case class PlannedCandidate(
    descriptor: Path,
    resource: String,
    articleIdentity: String,
    locale: String,
    profile: String,
    destination: Path,
    artifact: String,
    version: String
  )

  final case class Plan(
    config: CozyBok.PublicationConfig,
    candidates: Vector[PlannedCandidate],
    prepared: Vector[CozyArticleMediaInfographicEvidence.Prepared]
  )

  def publish(config: CozyBok.PublicationConfig): Vector[CozyArticleMediaInfographicEvidence.Completion] =
    commit(config, plan(config))

  def plan(config: CozyBok.PublicationConfig): Plan = {
    plan(config, () => ())
  }

  private[publication] def plan(
    config: CozyBok.PublicationConfig,
    beforeRegistryRevalidation: () => Unit
  ): Plan = {
    _validate_config(config, rejectdryrun = false)
    if (beforeRegistryRevalidation == null)
      _invalid("Article-media infographic command before-registry-revalidation callback must be defined")
    _require_direct_directory(config.sourcepath, "BoK source")
    val candidates = _descriptor_candidates(config.sourcepath)
    val prepared = _prepare_evidence(config, candidates)
    CozyArticleMediaRegistry.validateReadOnly(config.publicationPath, prepared.map(_planned_role_update), beforeRegistryRevalidation)
    Plan(
      config,
      prepared.map { item =>
        PlannedCandidate(
          item.publication.descriptorFile,
          item.publication.resource.id,
          item.integrity.record.articleIdentity,
          item.integrity.record.locale,
          item.publication.profile,
          item.artifactPath,
          item.integrity.record.artifact.identity,
          item.integrity.record.artifact.version
        )
      },
      prepared
    )
  }

  private[publication] def publish(
    config: CozyBok.PublicationConfig,
    beforefirstartifactinstall: () => Unit
  ): Vector[CozyArticleMediaInfographicEvidence.Completion] =
    publish(config, beforefirstartifactinstall, () => ())

  private[publication] def publish(
    config: CozyBok.PublicationConfig,
    beforefirstartifactinstall: () => Unit,
    afterartifactbeforemerge: () => Unit
  ): Vector[CozyArticleMediaInfographicEvidence.Completion] =
    commit(config, plan(config), beforefirstartifactinstall, afterartifactbeforemerge)

  /* This package-private boundary commits only the immutable evidence already
   * accepted by plan.  It must never rediscover descriptors or reparse their
   * content because the one-stop command has completed its preflight. */
  private[cozy] def commit(
    config: CozyBok.PublicationConfig,
    plan: Plan
  ): Vector[CozyArticleMediaInfographicEvidence.Completion] =
    commit(config, plan, () => (), () => ())

  private[cozy] def commit(
    config: CozyBok.PublicationConfig,
    plan: Plan,
    beforefirstartifactinstall: () => Unit,
    afterartifactbeforemerge: () => Unit
  ): Vector[CozyArticleMediaInfographicEvidence.Completion] = {
    _validate_config(config, rejectdryrun = true)
    if (plan == null || plan.config == null || plan.prepared == null || plan.candidates == null)
      _invalid("Article-media infographic command plan must be defined")
    if (plan.config != config)
      _invalid("Article-media infographic command plan configuration does not match commit configuration")
    if (beforefirstartifactinstall == null)
      _invalid("Article-media infographic command before-first-artifact-install callback must be defined")
    if (afterartifactbeforemerge == null)
      _invalid("Article-media infographic command after-artifact-before-registry callback must be defined")
    val prepared = plan.prepared
    if (prepared.isEmpty)
      Vector.empty
    else {
      Files.createDirectories(config.publicationPath)
      CozyArticleMediaRegistry.transaction(config.publicationPath) { transaction =>
        transaction.validate(prepared.map(_planned_role_update))
        val results = CozyMedia.commitPublication(prepared.map(_.publication), beforefirstartifactinstall)
        val bypublication = results.map(result => result.prepared -> result).toMap
        if (bypublication.size != prepared.size)
          _invalid("Article-media infographic commit result does not cover every prepared publication")
        afterartifactbeforemerge()
        val revalidated = prepared.map(_revalidate_evidence(config, _))
        val completions = revalidated.map { item =>
          CozyArticleMediaInfographicEvidence.complete(item, bypublication.getOrElse(item.publication,
            _invalid("Article-media infographic commit result is missing prepared publication")
          ))
        }
        transaction.merge(completions.map(_.roleUpdate))
        completions
      }
    }
  }

  private def _validate_config(config: CozyBok.PublicationConfig, rejectdryrun: Boolean): Unit = {
    if (config == null)
      _invalid("Article-media infographic command configuration must be defined")
    if (rejectdryrun && config.dryRun)
      _invalid("--dry-run is only supported by bok publish")
  }

  private def _prepare_evidence(config: CozyBok.PublicationConfig, candidates: Vector[Path]): Vector[CozyArticleMediaInfographicEvidence.Prepared] = {
    val publications = candidates.flatMap(_prepare_descriptor(config, _))
    if (publications.isEmpty)
      Vector.empty
    else {
      val version = config.version.filter(x => x.nonEmpty && x == x.trim).getOrElse(
        _invalid("Article-media infographic publication requires a non-empty --version")
      )
      val evidence = publications.map { publication =>
        CozyArticleMediaInfographicEvidence.prepare(CozyArticleMediaInfographicEvidence.PreparedInput(
          projectRoot = config.project,
          buildManifest = publication.descriptorRoot.resolve("target/cozy-media/manifest.json"),
          version = version,
          repositoryRoot = config.repositoryPath,
          publication = publication
        ))
      }.sortBy(x => (x.integrity.record.articleIdentity, x.integrity.record.locale, x.integrity.record.artifact.identity))
      _validate_unique_roles(evidence)
      _validate_unique_destinations(evidence)
      evidence
    }
  }

  private def _revalidate_evidence(
    config: CozyBok.PublicationConfig,
    approved: CozyArticleMediaInfographicEvidence.Prepared
  ): CozyArticleMediaInfographicEvidence.Prepared = {
    if (approved == null || approved.publication == null || approved.integrity == null)
      _invalid("Article-media infographic approved evidence must be defined")
    val revalidated = CozyArticleMediaInfographicEvidence.prepare(CozyArticleMediaInfographicEvidence.PreparedInput(
      projectRoot = config.project,
      buildManifest = approved.publication.descriptorRoot.resolve("target/cozy-media/manifest.json"),
      version = approved.integrity.record.artifact.version,
      repositoryRoot = config.repositoryPath,
      publication = approved.publication
    ))
    if (revalidated.publication != approved.publication || revalidated.integrity != approved.integrity || revalidated.artifactPath != approved.artifactPath)
      _invalid("Article-media infographic evidence changed after artifact publication")
    revalidated
  }

  private def _prepare_descriptor(config: CozyBok.PublicationConfig, descriptorfile: Path): Vector[CozyMedia.PreparedPublication] = {
    val descriptor = Try(StructuredDocumentLoader.loadDocument[CozyMedia.Descriptor](InputSource(descriptorfile.toFile)).take).getOrElse(
      _invalid(s"Article-media infographic descriptor is malformed or invalid: $descriptorfile")
    )
    if (descriptor == null || descriptor.schema != "cozy.media.v1" || descriptor.knowledge == null || descriptor.resources == null || descriptor.profiles == null)
      _invalid(s"Article-media infographic descriptor must decode exactly as cozy.media.v1: $descriptorfile")
    val root = descriptorfile.getParent.toAbsolutePath.normalize()
    val repository = _directory_identity(config.repositoryPath, "configured repository")
    val selected = descriptor.resources.filter { resource =>
      if (resource == null)
        _invalid(s"Article-media infographic descriptor resource must be defined: $descriptorfile")
      resource.kind == "image" && resource.role == Some("detailed-infographic")
    }.sortBy(_.id)
    selected.flatMap { resource =>
      val language = resource.language.getOrElse(_invalid(s"Article-media infographic resource language must be defined: $descriptorfile"))
      if (CozyArticleMediaNormalization.normalizeLocale(language) != language)
        _invalid(s"Article-media infographic resource locale must be exact canonical: $descriptorfile")
      val profiles = descriptor.profiles.toVector.sortBy(_._1).collect {
        case (name, profile) if resource.publications != null && resource.publications.contains(name) && _profile_identity(root, name, profile) == repository => name
      }
      if (profiles.isEmpty)
        _invalid(s"Article-media infographic resource has no matching publication profile: ${resource.id}")
      if (profiles.size > 1)
        _invalid(s"Article-media infographic resource has multiple matching publication profiles: ${resource.id}")
      CozyMedia.preparePublication(CozyMedia.CommandConfig(descriptorfile, target = Some(resource.id), profile = Some(profiles.head)), config.mediaForce)
    }
  }

  private def _descriptor_candidates(source: Path): Vector[Path] = {
    val stream = Files.walk(source)
    try {
      val candidates = stream.iterator().asScala.toVector.filter { path =>
        _descriptor_names.contains(path.getFileName.toString) && _direct_regular_file(path)
      }.map(_.toAbsolutePath.normalize()).sortBy(_.toString)
      candidates.groupBy(_.getParent).toVector.sortBy(_._1.toString).collectFirst {
        case (directory, values) if values.size > 1 => directory
      }.foreach(directory => _invalid(s"Article-media infographic descriptor basename collision: $directory"))
      candidates
    } finally stream.close()
  }

  private def _planned_role_update(prepared: CozyArticleMediaInfographicEvidence.Prepared): CozyArticleMediaRegistry.RoleUpdate = {
    val record = prepared.integrity.record
    CozyArticleMediaRegistry.RoleUpdate(
      record.articleIdentity,
      CozyArticleMediaPublication.Variant(record.locale, infographic = Some(
        org.smartdox.metadata.PublishMetadata.ImageReference(record.publicPath, Some("image/png"), None)
      )),
      prepared.integrity
    )
  }

  private def _validate_unique_roles(prepared: Vector[CozyArticleMediaInfographicEvidence.Prepared]): Unit =
    prepared.groupBy(x => (x.integrity.record.articleIdentity, x.integrity.record.locale)).toVector.sortBy(_._1).collectFirst {
      case (key, values) if values.size > 1 => key
    }.foreach(key => _invalid(s"Duplicate article-media infographic role: ${key._1} [${key._2}, infographic]"))

  private def _validate_unique_destinations(prepared: Vector[CozyArticleMediaInfographicEvidence.Prepared]): Unit =
    prepared.groupBy(_.artifactPath).toVector.sortBy(_._1.toString).collectFirst {
      case (destination, values) if values.size > 1 => destination
    }.foreach(destination => _invalid(s"Article-media infographic publication destination is duplicated: $destination"))

  private def _profile_identity(root: Path, name: String, profile: CozyMedia.Profile): Path = {
    if (profile == null)
      _invalid(s"Article-media infographic profile must be defined: $name")
    val value = profile.rootEnv match {
      case Some(envname) =>
        val actual = sys.env.get(envname).filter(x => x.nonEmpty && x == x.trim).getOrElse(
          _invalid(s"Article-media infographic profile rootEnv is missing: $envname")
        )
        Path.of(actual)
      case None => profile.root match {
        case Some(relative) =>
          val path = Path.of(relative)
          if (path.isAbsolute)
            _invalid(s"Article-media infographic profile root must be relative: $name")
          root.resolve(path).normalize()
        case None => root
      }
    }
    _directory_identity(value, s"profile $name")
  }

  private def _directory_identity(path: Path, label: String): Path = {
    val lexical = Option(path).map(_.toAbsolutePath.normalize()).getOrElse(_invalid(s"Article-media infographic $label root must be defined"))
    _require_direct_directory(lexical, label)
    val real = lexical.toRealPath()
    if (real != lexical)
      _invalid(s"Article-media infographic $label root must not be a lexical alias: $path")
    lexical
  }

  private def _require_direct_directory(path: Path, label: String): Unit =
    if (path == null || Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media infographic $label must be an existing direct non-symlink directory: $path")

  private def _direct_regular_file(path: Path): Boolean =
    path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
