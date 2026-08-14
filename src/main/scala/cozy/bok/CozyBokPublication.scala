package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.bok.scenario.ScenarioMetadata
import cozy.bok.BibliographyEntry._
import cozy.config.CozyProjectYamlConfig
import cozy.publication.{CozyArticleMediaBuildContext, CozyArticleMediaInfographicCommand, CozyArticleMediaInfographicEvidence, CozyArticleMediaVideoCommand}
import cozy.video.{CozyVideo, CozyVideoPublisher}
import org.smartdox.{Body, Document, Dox}
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.Dox2HtmlTransformer
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.metadata.DocumentMetaData
import org.goldenport.i18n.I18NContext
import java.net.URLEncoder
import java.time.{Instant, LocalDate, LocalDateTime, YearMonth, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.control.NonFatal
import scala.sys.process._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import io.circe.syntax._

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokPublication {
  self: CozyBokImplementation.type =>
  def publishVideo(
    config: PublicationConfig,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Vector[CozyVideoPublisher.PublishVideoResult] =
    if (!config.videoEnabled) Vector.empty
    else _publish_video_packages(
      config,
      CozyArticleMediaVideoCommand.preflight(config.publicationPath, config.repositoryPath, _video_publication_plans(config)),
      voicevox,
      videorunner
    )

  def publishMedia(config: PublicationConfig): Vector[CozyArticleMediaInfographicEvidence.Completion] =
    CozyArticleMediaInfographicCommand.commit(config, CozyArticleMediaInfographicCommand.plan(config))

  def publishProjects(config: PublicationConfig): Vector[CozyBokProjectPublisher.PublishProjectResult] =
    _publish_project_packages(config)

  def updatePublication(
    config: PublicationConfig,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Vector[String] =
    {
      val videoplans = if (config.videoEnabled) _video_publication_plans(config) else Vector.empty
      val videopreflight = CozyArticleMediaVideoCommand.preflight(config.publicationPath, config.repositoryPath, videoplans)
      val infographicplan = CozyArticleMediaInfographicCommand.plan(config)
      _publish_video_packages(config, videopreflight, voicevox, videorunner).map(_.video.name) ++
      CozyArticleMediaInfographicCommand.commit(config, infographicplan).map(_.roleUpdate.integrity.record.artifact.identity) ++
      publishProjects(config).map(_.project.name)
    }

  def publish(
    config: PublicationConfig,
    runner: Runner,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Unit = {
    val preflight = _publish_preflight(config)
    val planned = Vector(
      PublishStep("preflight", "succeeded", "Publish preflight passed."),
      PublishStep("update-publication", if (preflight.packageCount == 0) "skipped" else if (config.dryRun) "planned" else "pending", _publication_package_message(preflight)),
      PublishStep("build", if (config.dryRun) "planned" else "pending", s"strategy=${preflight.build.strategy}"),
      PublishStep("stage", preflight.stage.map(_ => if (config.dryRun) "planned" else "pending").getOrElse("skipped"), preflight.stage.map(_.command.mkString(" ")).getOrElse("No stage workflow configured.")),
      PublishStep("upload", if (config.dryRun) "planned" else "pending", preflight.upload.command.mkString(" "))
    )
    if (config.dryRun) {
      _write_publish_manifest(config, preflight, planned)
      _print_publish_plan(config, preflight)
    } else {
      var steps = Vector(PublishStep("preflight", "succeeded", "Publish preflight passed."))
      try {
        if (preflight.packageCount == 0) {
          steps :+= PublishStep("update-publication", "skipped", "No publication packages to publish.")
          _publish_status("update-publication", "skipped")
        } else {
          _publish_status("update-publication", "start")
          val results = _update_publication(config, preflight, voicevox, videorunner)
          steps :+= PublishStep("update-publication", "succeeded", s"${results.size} package(s) published.")
          _publish_status("update-publication", "succeeded")
        }
        _write_publish_manifest(config, preflight, steps)

        _publish_status("build", "start")
        build(preflight.build, runner)
        steps :+= PublishStep("build", "succeeded", s"strategy=${preflight.build.strategy}")
        _publish_status("build", "succeeded")
        _write_publish_manifest(config, preflight, steps)

        preflight.stage match {
          case Some(stage) =>
            _publish_status("stage", "start")
            runWorkflow(stage, runner)
            steps :+= PublishStep("stage", "succeeded", stage.command.mkString(" "))
            _publish_status("stage", "succeeded")
          case None =>
            steps :+= PublishStep("stage", "skipped", "No stage workflow configured.")
            _publish_status("stage", "skipped")
        }
        _write_publish_manifest(config, preflight, steps)

        _publish_status("upload", "start")
        runWorkflow(preflight.upload, runner)
        steps :+= PublishStep("upload", "succeeded", preflight.upload.command.mkString(" "))
        _publish_status("upload", "succeeded")
        _write_publish_manifest(config, preflight, steps)
      } catch {
        case e: Throwable =>
          val failed = _failed_step(steps)
          steps :+= PublishStep(failed, "failed", Option(e.getMessage).getOrElse(e.toString))
          _publish_status(failed, "failed")
          _write_publish_manifest(config, preflight, steps)
          throw e
      }
    }
  }

  private def _publish_preflight(config: PublicationConfig): PublishPreflight = {
    val stage = WorkflowConfig.create("stage", List(config.project.toString))
    val upload = WorkflowConfig.create("upload", List(config.project.toString))
    val optionalstage = if (stage.command.nonEmpty) Some(stage) else None
    _require_workflow(upload)
    val buildargs = List(
      config.project.toString,
      "--strategy", config.strategy,
      "--repository", config.repositoryPath.toString,
      "--publication", config.publicationPath.toString
    )
    val buildconfig = BuildConfig.create(buildargs)
    val videoplans = if (config.videoEnabled) _video_publication_plans(config) else Vector.empty
    val videopreflight = CozyArticleMediaVideoCommand.preflight(config.publicationPath, config.repositoryPath, videoplans)
    val videopackages = videoplans.map(_.video.packageDir)
    val projects = _project_packages(config)
    val infographicplan = CozyArticleMediaInfographicCommand.plan(config)
    val infographics = infographicplan.candidates
    _validate_publish_path("publication", config.publicationPath, config.project, config.sourcepath)
    config.warehousePath match {
      case Some(warehousepath) if config.repositoryPath == warehousepath.resolve("repository").toAbsolutePath.normalize() =>
        _validate_publish_path("warehouse", warehousepath, config.project, config.sourcepath)
      case Some(warehousepath) =>
        _validate_publish_path("warehouse", warehousepath, config.project, config.sourcepath)
        _validate_publish_path("repository", config.repositoryPath, config.project, config.sourcepath)
      case None =>
        _validate_publish_path("repository", config.repositoryPath, config.project, config.sourcepath)
    }
    _reject_path_overlap("publication", config.publicationPath, "artifact repository", config.repositoryPath)
    _reject_path_overlap("publication", config.publicationPath, "website", buildconfig.websitePath)
    _reject_path_overlap("publication", config.publicationPath, "doxsite", buildconfig.doxsitePath)
    _reject_path_overlap("artifact repository", config.repositoryPath, "website", buildconfig.websitePath)
    _reject_path_overlap("artifact repository", config.repositoryPath, "doxsite", buildconfig.doxsitePath)
    PublishPreflight(optionalstage, upload, buildconfig, videopackages, projects, infographics, videopreflight, infographicplan)
  }

  private def _validate_publish_path(name: String, path: Path, project: Path, source: Path): Unit = {
    if (path == source || path.startsWith(source) || source.startsWith(path))
      RAISE.invalidArgumentFault(s"Invalid ${name} path overlaps BoK source: ${path}")
    val parent = Option(path.getParent).getOrElse(project)
    if (Files.exists(path) && !Files.isDirectory(path))
      RAISE.invalidArgumentFault(s"Invalid ${name} path is not a directory: ${path}")
    if (!Files.exists(path) && !Files.exists(parent))
      RAISE.invalidArgumentFault(s"Invalid ${name} path parent does not exist: ${parent}")
    if (Files.exists(parent) && !Files.isWritable(parent))
      RAISE.invalidArgumentFault(s"Invalid ${name} path parent is not writable: ${parent}")
    if (Files.exists(path) && !Files.isWritable(path))
      RAISE.invalidArgumentFault(s"Invalid ${name} path is not writable: ${path}")
  }

  private def _reject_path_overlap(leftname: String, left: Path, rightname: String, right: Path): Unit =
    if ({
      val leftpath = left.toAbsolutePath.normalize()
      val rightpath = right.toAbsolutePath.normalize()
      leftpath == rightpath || leftpath.startsWith(rightpath) || rightpath.startsWith(leftpath)
    })
      RAISE.invalidArgumentFault(s"Invalid ${leftname}/${rightname} path overlap: ${left.toAbsolutePath.normalize()} / ${right.toAbsolutePath.normalize()}")

  private def _failed_step(steps: Vector[PublishStep]): String =
    if (!steps.exists(_.name == "update-publication"))
      "update-publication"
    else if (!steps.exists(_.name == "build"))
      "build"
    else if (!steps.exists(_.name == "stage"))
      "stage"
    else if (!steps.exists(_.name == "upload"))
      "upload"
    else
      "publish"

  private def _publish_status(step: String, status: String): Unit =
    println(s"bok publish: ${step}: ${status}")

  private def _print_publish_plan(config: PublicationConfig, preflight: PublishPreflight): Unit = {
    println("bok publish dry-run")
    println(s"project: ${config.project}")
    println(s"publication: ${config.publicationPath}")
    config.warehousePath.foreach(path => println(s"warehouse: ${path}"))
    println(s"repository: ${config.repositoryPath}")
    println(s"strategy: ${config.strategy}")
    println("steps:")
    println(s"- update-publication: ${_publication_package_message(preflight)}")
    preflight.infographicpackages.foreach { candidate =>
      println(s"  - infographic: ${candidate.articleIdentity} [${candidate.locale}, ${candidate.artifact}, ${candidate.version}] -> ${candidate.destination}")
    }
    preflight.videopreflight.candidates.foreach { candidate =>
      println(s"  - video: ${candidate.articleIdentity} [${candidate.locale}, ${candidate.role}, ${candidate.video}, ${candidate.version}] -> ${candidate.destination} (${candidate.destinationState}, force=${candidate.forceRequested})")
    }
    println(s"- build: strategy=${preflight.build.strategy}")
    println(s"- stage: ${preflight.stage.map(_.command.mkString(" ")).getOrElse("skipped")}")
    println(s"- upload: ${preflight.upload.command.mkString(" ")}")
    println(s"manifest: ${config.manifestPath}")
  }

  private def _write_publish_manifest(
    config: PublicationConfig,
    preflight: PublishPreflight,
    steps: Vector[PublishStep]
  ): Unit = {
    Files.createDirectories(config.manifestPath.getParent)
    val json = Json.obj(
      "schema" -> Json.fromString("cozy.bok.publish-manifest.v1"),
      "project" -> Json.fromString(config.project.toString),
      "source" -> Json.fromString(config.sourcepath.toString),
      "publication" -> Json.fromString(config.publicationPath.toString),
      "warehouse" -> Json.fromString(config.warehousePath.map(_.toString).getOrElse("")),
      "repository" -> Json.fromString(config.repositoryPath.toString),
      "strategy" -> Json.fromString(config.strategy),
      "dryRun" -> Json.fromBoolean(config.dryRun),
      "force" -> Json.fromBoolean(config.force),
      "videoEnabled" -> Json.fromBoolean(config.videoEnabled),
      "mediaForce" -> Json.fromBoolean(config.mediaForce),
      "videoPackages" -> Json.fromValues(preflight.videopackages.map(x => Json.fromString(x.toString))),
      "videoCandidates" -> Json.fromValues(preflight.videopreflight.candidates.map(x => Json.obj(
        "articleIdentity" -> Json.fromString(x.articleIdentity),
        "locale" -> Json.fromString(x.locale),
        "role" -> Json.fromString(x.role),
        "video" -> Json.fromString(x.video),
        "version" -> Json.fromString(x.version),
        "destination" -> Json.fromString(x.destination.toString),
        "destinationState" -> Json.fromString(x.destinationState),
        "forceRequested" -> Json.fromBoolean(x.forceRequested),
        "owner" -> Json.fromString(x.owner)
      ))),
      "infographicPackages" -> Json.fromValues(preflight.infographicpackages.map(x => Json.obj(
        "descriptor" -> Json.fromString(x.descriptor.toString),
        "resource" -> Json.fromString(x.resource),
        "articleIdentity" -> Json.fromString(x.articleIdentity),
        "locale" -> Json.fromString(x.locale),
        "profile" -> Json.fromString(x.profile),
        "destination" -> Json.fromString(x.destination.toString),
        "artifact" -> Json.fromString(x.artifact),
        "version" -> Json.fromString(x.version)
      ))),
      "projectPackages" -> Json.fromValues(preflight.projectpackages.map(x => Json.fromString(x.toString))),
      "publicationArtifacts" -> Json.fromValues(_legacy_publication_packages(preflight).map(x => Json.obj(
        "sourcePackage" -> Json.fromString(x.toString),
        "registryRoot" -> Json.fromString(config.publicationPath.toString),
        "warehouseRoot" -> Json.fromString(config.warehousePath.map(_.toString).getOrElse("")),
        "repositoryRoot" -> Json.fromString(config.repositoryPath.toString)
      ))),
      "buildCommands" -> Json.arr(
        Json.fromValues(_planned_dox_antora_command(preflight.build).map(Json.fromString)),
        Json.fromValues(_planned_dox_site_command(preflight.build).map(Json.fromString))
      ),
      "buildCommand" -> Json.fromValues(_planned_dox_site_command(preflight.build).map(Json.fromString)),
      "stageCommand" -> Json.fromValues(preflight.stage.toVector.flatMap(_.command).map(Json.fromString)),
      "uploadCommand" -> Json.fromValues(preflight.upload.command.map(Json.fromString)),
      "steps" -> Json.fromValues(steps.map(_publish_step_json))
    )
    Files.writeString(config.manifestPath, json.spaces2, StandardCharsets.UTF_8)
  }

  private def _publish_step_json(step: PublishStep): Json =
    Json.obj(
      "name" -> Json.fromString(step.name),
      "status" -> Json.fromString(step.status),
      "message" -> Json.fromString(step.message)
    )

  private def _publish_video_packages(
    config: PublicationConfig,
    preflight: CozyArticleMediaVideoCommand.Preflight,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Vector[CozyVideoPublisher.PublishVideoResult] =
    if (!config.videoEnabled)
      Vector.empty
    else
      CozyArticleMediaVideoCommand.publish(preflight, voicevox, videorunner)

  private def _update_publication(
    config: PublicationConfig,
    preflight: PublishPreflight,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Vector[String] =
    _publish_video_packages(config, preflight.videopreflight, voicevox, videorunner).map(_.video.name) ++
      CozyArticleMediaInfographicCommand.commit(config, preflight.infographicplan).map(_.roleUpdate.integrity.record.artifact.identity) ++
      _publish_project_packages(config).map(_.project.name)

  private def _video_publication_plans(config: PublicationConfig): Vector[CozyVideoPublisher.PublicationPlan] = {
    val plans = _video_packages(config).map { packagedir =>
      CozyVideoPublisher.planPublication(CozyVideoPublisher.PublishVideoConfig(
        packagedir,
        config.publicationPath,
        config.artifactBasePath,
        config.version,
        config.force,
        Some(config.repositoryPath)
      ))
    }
    plans.sortBy(_.video.name)
  }

  private def _publish_project_packages(config: PublicationConfig): Vector[CozyBokProjectPublisher.PublishProjectResult] = {
    val bokconfig = _load_config(config.project)
    _project_packages(config).map { packagedir =>
      CozyBokProjectPublisher.publish(
        CozyBokProjectPublisher.PublishProjectConfig(
          packagedir,
          config.publicationPath,
          config.artifactBasePath,
          config.version,
          config.force,
          config.project,
          bokconfig,
          Some(config.repositoryPath)
        )
      )
    }
  }

  private def _video_packages(config: PublicationConfig): Vector[Path] = {
    if (!Files.isDirectory(config.sourcepath))
      RAISE.invalidArgumentFault(s"Missing BoK source directory: ${config.sourcepath}")
    val stream = Files.walk(config.sourcepath)
    try {
      val dirs = stream.iterator.asScala.toVector.filter(Files.isDirectory(_)).map(_.toAbsolutePath.normalize())
      dirs.find(_.getFileName.toString.endsWith(".video.d")).foreach { path =>
        RAISE.invalidArgumentFault(s"*.video.d is reserved for generated/work directories: $path")
      }
      dirs.filter(_.getFileName.toString.endsWith(".video")).sortBy(_.toString).map { path =>
        if (!_has_video_descriptor(path))
          RAISE.invalidArgumentFault(s"Missing video descriptor in .video package: $path")
        path
      }
    } finally {
      stream.close()
    }
  }

  private def _has_video_descriptor(path: Path): Boolean =
    Vector("video.yaml", "video.yml", "video.json").exists(x => Files.isRegularFile(path.resolve(x)))

  private def _project_packages(config: PublicationConfig): Vector[Path] = {
    if (!Files.isDirectory(config.sourcepath))
      RAISE.invalidArgumentFault(s"Missing BoK source directory: ${config.sourcepath}")
    _project_package_dirs(config.sourcepath)
  }

  private[bok] def _project_package_dirs(sourcepath: Path): Vector[Path] = {
    if (!Files.isDirectory(sourcepath))
      return Vector.empty
    val projects = sourcepath.resolve("projects").toAbsolutePath.normalize()
    val stream = Files.walk(sourcepath)
    try {
      val dirs = stream.iterator.asScala.toVector.filter(Files.isDirectory(_)).map(_.toAbsolutePath.normalize())
      dirs.find(path => path.getFileName.toString.endsWith(".car-product") || path.getFileName.toString.endsWith(".car-product.d")).foreach { path =>
        RAISE.invalidArgumentFault(s".car-product source packages are no longer supported. Use src/main/doxsite/projects/<category>/<slug>: $path")
      }
      if (!Files.isDirectory(projects)) {
        Vector.empty
      } else {
        val projectdirs = dirs.filter { path =>
          path.startsWith(projects) && path != projects && projects.relativize(path).getNameCount == 2
        }.sortBy(_.toString)
        dirs.filter(path => path.startsWith(projects) && _has_project_descriptor(path)).foreach { path =>
          val relative = projects.relativize(path)
          if (relative.getNameCount != 2)
            RAISE.invalidArgumentFault(s"Project knowledge package must be src/main/doxsite/projects/<category>/<slug>: $path")
        }
        projectdirs.map { path =>
          if (!_has_project_descriptor(path))
            RAISE.invalidArgumentFault(s"Missing project descriptor in project knowledge package: $path")
          val relative = projects.relativize(path)
          val category = relative.getName(0).toString
          if (category.endsWith(".d"))
            RAISE.invalidArgumentFault(s"Generated/work project category directories are not supported: $path")
          path
        }
      }
    } finally {
      stream.close()
    }
  }

  private def _has_project_descriptor(path: Path): Boolean =
    Vector("project.yaml", "project.yml", "project.json").exists(x => Files.isRegularFile(path.resolve(x)))

  private def _legacy_publication_packages(preflight: PublishPreflight): Vector[Path] =
    preflight.videopackages ++ preflight.projectpackages

  private def _publication_package_message(preflight: PublishPreflight): String =
    s"${preflight.videopackages.size} .video package(s)" +
      (if (preflight.infographicpackages.nonEmpty) s", ${preflight.infographicpackages.size} infographic candidate(s)" else "") +
      s", ${preflight.projectpackages.size} project package(s)"

}
