package cozy.archive

import cozy.compatibility.{
  CarMetadataCompatibility,
  GenerationCompatibilityEvidence
}
import cozy.config.CozyProjectYamlConfig
import cozy.modeler.GenerationProvenance
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._

/*
 * @since   May. 20, 2026
 *  version Jun.  4, 2026
 *  version Jul. 28, 2026
 *  version Aug. 28, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyCarPublisher {
  def publish(args: List[String]): Unit =
    _publish(args, None)

  private[cozy] def _publish_with_evidence(
    args: List[String],
    evidence: GenerationCompatibilityEvidence,
    executingcozyversion: String
  ): Unit =
    _publish(
      args,
      Some(CarMetadataCompatibility.GenerationAcceptanceContext(
        evidence,
        executingcozyversion
      ))
    )

  private def _publish(
    args: List[String],
    acceptancecontext: Option[
      CarMetadataCompatibility.GenerationAcceptanceContext
    ]
  ): Unit = {
    val compatibility = _compatibility_contract(args, acceptancecontext)
    val projectdir = RepositoryArtifactPublisher.projectDir(args, "Missing project directory for publish-car")
    val coordinate = CozyComponentReleaseCoordinateCodec.fromProjectMetadata(
      RepositoryArtifactPublisher.projectConfig(projectdir),
      "project.yaml"
    )
    CozyComponentReleaseCoordinateCodec.requireProjection(
      coordinate.mavenArtifactId,
      RepositoryArtifactPublisher.requiredValue(args, "name"),
      "name",
      "publish-car"
    )
    CozyComponentReleaseCoordinateCodec.requireProjection(
      coordinate.version,
      RepositoryArtifactPublisher.requiredValue(args, "version"),
      "version",
      "publish-car"
    )
    _require_prebuilt_car_admission(args, compatibility, coordinate)
    RepositoryArtifactPublisher.publish(args, _policy(compatibility, coordinate))
  }

  private def _require_prebuilt_car_admission(
    args: List[String],
    compatibility: Option[CarMetadataCompatibility.Contract],
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Unit =
    RepositoryArtifactPublisher.path(args, "car").foreach { archive =>
      compatibility match {
        case Some(contract) =>
          val projectdir = RepositoryArtifactPublisher.projectDir(
            args,
            "Missing project directory for publish-car"
          )
          val generatedrelease =
            !coordinate.version.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT") &&
              _has_cml_source(projectdir)
          if (generatedrelease)
            _require_generation_provenance(projectdir, contract)
          // Full compatibility validation already includes canonical admission.
          CozyCarRuntimeManifest.requireValidArchive(
            archive,
            contract,
            coordinate,
            requireGenerationProvenance = generatedrelease
          )
        case None =>
          CozyCarRuntimeManifest.requireCanonicalArchiveAdmission(archive, coordinate)
      }
    }

  private def _require_generation_provenance(
    projectdir: Path,
    contract: CarMetadataCompatibility.Contract
  ): Unit = {
    val source = projectdir.resolve(GenerationProvenance.METADATA_PATH)
    GenerationProvenance.requireValidForPackaging(
      source,
      projectdir,
      Some(contract.cncfCompileTarget.version),
      Some(contract.cozyVersion)
    )
    ()
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

  private def _policy(
    compatibility: Option[CarMetadataCompatibility.Contract],
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): RepositoryArtifactPublisher.Policy =
    RepositoryArtifactPublisher.Policy(
      kind = "car",
      archiveOption = "car",
      missingProjectMessage = "Missing project directory for publish-car",
      missingArchiveMessage = "CAR archive does not exist",
      versionEntry = (version, channel, file, args) =>
        _version_entry(
          version,
          channel,
          file,
          args,
          compatibility
        ),
      buildArchive = _build_temp_car,
      coordinate = Some(coordinate)
    )

  private def _build_temp_car(args: List[String]): Path = {
    val projectdir = RepositoryArtifactPublisher.projectDir(args, "Missing project directory for publish-car")
    val name = RepositoryArtifactPublisher.requiredValue(args, "name")
    val version = RepositoryArtifactPublisher.requiredValue(args, "version")
    val mainjar = RepositoryArtifactPublisher.requiredPath(args, "main-jar")
    val workroot = projectdir.resolve("target/cozy/work/publish-car")
    Files.createDirectories(workroot)
    val tempcar = Files.createTempFile(workroot, "build-", ".car")
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
    args: List[String],
    compatibility: Option[CarMetadataCompatibility.Contract]
  ): RepositoryArtifactCatalogVersion = {
    val projectdir = RepositoryArtifactPublisher.projectDir(args, "Missing project directory for publish-car")
    val config = RepositoryArtifactPublisher.projectConfig(projectdir)
    RepositoryArtifactCatalogVersion(
      version = version,
      channel = Some(channel),
      status = Some(RepositoryArtifactPublisher.value(args, "status").getOrElse("active")),
      component = None,
      publishedAt = Some(RepositoryArtifactPublisher.publishedAt(args)),
      file = Some(file),
      runtime = compatibility.map(_runtime_requirement).orElse(_runtime_requirement(config))
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
      CozyComponentReleaseCoordinateCodec.fromProjectMetadata(
        RepositoryArtifactPublisher.projectConfig(projectdir), "project.yaml"
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
    Set("warehouse", "car", "name", "version", "channel", "status", "published-at", "keep-recommended", "local-publication")
}
