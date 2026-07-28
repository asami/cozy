package cozy.archive

import cozy.compatibility.{
  CarMetadataCompatibility,
  GenerationCompatibilityEvidence
}
import cozy.config.CozyProjectYamlConfig
import cozy.modeler.GenerationProvenance
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.collection.JavaConverters._

/*
 * @since   May. 20, 2026
 *  version Jun.  4, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyCarPublisher {
  def publish(args: List[String]): Unit =
    _publish(args, None)

  private[cozy] def publish(
    args: List[String],
    evidence: GenerationCompatibilityEvidence,
    executingCozyVersion: String
  ): Unit =
    _publish(
      args,
      Some(CarMetadataCompatibility.GenerationAcceptanceContext(
        evidence,
        executingCozyVersion
      ))
    )

  private def _publish(
    args: List[String],
    acceptancecontext: Option[
      CarMetadataCompatibility.GenerationAcceptanceContext
    ]
  ): Unit = {
    val compatibility = _compatibility_contract(args, acceptancecontext)
    _with_prebuilt_car_snapshot(args) { publicationargs =>
      _require_prebuilt_car_admission(publicationargs, compatibility)
      RepositoryArtifactPublisher.publish(
        publicationargs,
        _policy(compatibility)
      )
    }
  }

  private def _with_prebuilt_car_snapshot[A](
    args: List[String]
  )(body: List[String] => A): A =
    RepositoryArtifactPublisher.path(args, "car") match {
      case Some(source) =>
        if (!Files.isRegularFile(source))
          throw new IllegalArgumentException(s"CAR archive does not exist: $source")
        val snapshot = Files.createTempFile("cozy-publish-car-snapshot-", ".car")
        try {
          Files.copy(source, snapshot, StandardCopyOption.REPLACE_EXISTING)
          body(_replace_option(args, "car", snapshot.toString))
        } finally {
          Files.deleteIfExists(snapshot)
        }
      case None =>
        body(args)
    }

  private def _require_prebuilt_car_admission(
    args: List[String],
    compatibility: Option[CarMetadataCompatibility.Contract]
  ): Unit =
    for {
      contract <- compatibility
      archive <- RepositoryArtifactPublisher.path(args, "car")
    } {
      val projectdir = RepositoryArtifactPublisher.projectDir(
        args,
        "Missing project directory for publish-car"
      )
      val config = RepositoryArtifactPublisher.projectConfig(projectdir)
      val name = RepositoryArtifactPublisher.requiredValue(args, "name")
      val version = RepositoryArtifactPublisher.requiredValue(args, "version")
      val component = RepositoryArtifactPublisher.value(args, "component").
        orElse(config.value("packaging.car.manifest_metadata.component")).
        orElse(config.value("project.name")).
        getOrElse(name)
      val generatedrelease =
        !version.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT") &&
          _has_cml_source(projectdir)
      _with_validated_generation_provenance(
        projectdir,
        contract,
        generatedrelease
      ) { generationprovenance =>
        CozyCarRuntimeManifest.requireValidArchive(
          archive,
          contract,
          name,
          version,
          component,
          generationprovenance
        )
      }
    }

  private def _with_validated_generation_provenance[A](
    projectdir: Path,
    contract: CarMetadataCompatibility.Contract,
    required: Boolean
  )(body: Option[Path] => A): A =
    if (!required)
      body(None)
    else {
      val source = projectdir.resolve(GenerationProvenance.METADATA_PATH)
      if (!Files.isRegularFile(source))
        GenerationProvenance.requireValidForPackaging(
          source,
          projectdir,
          Some(contract.cncfCompileTarget.version),
          Some(contract.cozyVersion)
        )
      val snapshot =
        Files.createTempFile("cozy-publish-generation-provenance-", ".json")
      try {
        Files.copy(source, snapshot, StandardCopyOption.REPLACE_EXISTING)
        GenerationProvenance.requireValidForPackaging(
          snapshot,
          projectdir,
          Some(contract.cncfCompileTarget.version),
          Some(contract.cozyVersion)
        )
        body(Some(snapshot))
      } finally {
        Files.deleteIfExists(snapshot)
      }
    }

  private def _has_cml_source(projectdir: Path): Boolean = {
    val source = projectdir.resolve("src/main/cozy")
    if (!Files.isDirectory(source))
      false
    else {
      val stream = Files.walk(source)
      try
        stream.iterator().asScala.exists(
          path => Files.isRegularFile(path) && path.toString.endsWith(".cml")
        )
      finally
        stream.close()
    }
  }

  private def _replace_option(
    args: List[String],
    key: String,
    value: String
  ): List[String] = {
    val flag = s"--$key"
    val inlineprefix = s"$flag="
    def _go_(remaining: List[String], result: List[String]): List[String] =
      remaining match {
        case Nil =>
          result.reverse
        case head :: tail if head.startsWith(inlineprefix) =>
          _go_(tail, s"$inlineprefix$value" :: result)
        case head :: _ :: tail if head == flag =>
          _go_(tail, value :: head :: result)
        case head :: tail =>
          _go_(tail, head :: result)
      }
    _go_(args, Nil)
  }

  private def _policy(
    compatibility: Option[CarMetadataCompatibility.Contract]
  ): RepositoryArtifactPublisher.Policy =
    RepositoryArtifactPublisher.Policy(
      kind = "car",
      archiveOption = "car",
      missingProjectMessage = "Missing project directory for publish-car",
      missingArchiveMessage = "CAR archive does not exist",
      versionEntry = (version, channel, file, publishedcar, args) =>
        _version_entry(
          version,
          channel,
          file,
          publishedcar,
          args,
          compatibility
        ),
      buildArchive = _build_temp_car
    )

  private def _build_temp_car(args: List[String]): Path = {
    val projectdir = RepositoryArtifactPublisher.projectDir(args, "Missing project directory for publish-car")
    val name = RepositoryArtifactPublisher.requiredValue(args, "name")
    val version = RepositoryArtifactPublisher.requiredValue(args, "version")
    val mainjar = RepositoryArtifactPublisher.requiredPath(args, "main-jar")
    val tempcar = Files.createTempFile("cozy-publish-car-", ".car")
    val buildargs =
      RepositoryArtifactPublisher.removePublishOnlyArgs(args, _publish_only_keys) ++
        Vector(
          "--save", tempcar.toString,
          "--project-dir", projectdir.toString,
          "--name", name,
          "--version", version,
          "--main-jar", mainjar.toString
        )
    CozyArchivePackager.buildCar(buildargs.toList)
    tempcar
  }

  private def _version_entry(
    version: String,
    channel: String,
    file: String,
    publishedcar: Path,
    args: List[String],
    compatibility: Option[CarMetadataCompatibility.Contract]
  ): RepositoryArtifactCatalogVersion = {
    val projectdir = RepositoryArtifactPublisher.projectDir(args, "Missing project directory for publish-car")
    val config = RepositoryArtifactPublisher.projectConfig(projectdir)
    val component = RepositoryArtifactPublisher.value(args, "component").
      orElse(config.value("packaging.car.manifest_metadata.component")).
      orElse(config.value("project.name")).
      getOrElse(RepositoryArtifactPublisher.requiredValue(args, "name"))
    RepositoryArtifactCatalogVersion(
      version = version,
      channel = Some(channel),
      status = Some(RepositoryArtifactPublisher.value(args, "status").getOrElse("active")),
      component = Some(component),
      publishedAt = Some(RepositoryArtifactPublisher.publishedAt(args)),
      file = Some(file),
      runtime = compatibility.map(_runtime_requirement).orElse(_runtime_requirement(config)),
      checksumSha256 = Some(RepositoryArtifactPublisher.sha256(publishedcar))
    )
  }

  private def _compatibility_contract(
    args: List[String],
    acceptancecontext: Option[
      CarMetadataCompatibility.GenerationAcceptanceContext
    ]
  ): Option[CarMetadataCompatibility.Contract] = {
    val projectdir =
      RepositoryArtifactPublisher.projectDir(
        args,
        "Missing project directory for publish-car"
      )
    val metadata = CozyProjectYamlConfig.loadProjectMetadata(projectdir)
    if (CarMetadataCompatibility.isCarProject(metadata)) {
      val publishedversion =
        RepositoryArtifactPublisher.requiredValue(args, "version")
      val projectversion =
        metadata.value("project.component.version").getOrElse(
          throw new IllegalArgumentException(
            "CAR publication requires project.yaml project.component.version."
          )
        )
      if (publishedversion != projectversion)
        throw new IllegalArgumentException(
          s"CAR publication version disagrees with project.yaml: project=$projectversion publish=$publishedversion"
        )
    }
    acceptancecontext match {
      case Some(context) =>
        CarMetadataCompatibility.requireValidMetadata(metadata, context)
      case None =>
        CarMetadataCompatibility.requireValidMetadata(metadata)
    }
  }

  private def _runtime_requirement(
    contract: CarMetadataCompatibility.Contract
  ): RepositoryArtifactRuntimeRequirement = {
    val runtime = contract.runtimeCompatibility
    RepositoryArtifactRuntimeRequirement(
      runtime.minimum,
      runtime.maximum,
      runtime.excluded,
      runtime.tested
    )
  }

  private def _runtime_requirement(config: CozyProjectYamlConfig.Config): Option[RepositoryArtifactRuntimeRequirement] = {
    val minimum = config.value("packaging.car.runtime.cncf.minimum")
    val maximum = config.value("packaging.car.runtime.cncf.maximum")
    val excluded = config.list("packaging.car.runtime.cncf.excluded")
    val tested = config.list("packaging.car.runtime.cncf.tested")
    if (minimum.isEmpty && maximum.isEmpty && excluded.isEmpty && tested.isEmpty)
      None
    else
      Some(RepositoryArtifactRuntimeRequirement(minimum, maximum, excluded, tested))
  }

  private val _publish_only_keys =
    Set("warehouse", "car", "name", "version", "channel", "status", "published-at", "recommended")
}
