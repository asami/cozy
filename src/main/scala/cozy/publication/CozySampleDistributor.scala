package cozy.publication

import org.goldenport.RAISE
import cozy.config.CozyProjectYamlConfig
import cozy.runtime.CozyCliArgs
import org.goldenport.cli.spec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.JavaConverters._

/*
 * @since   May. 20, 2026
 * @version Jun.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozySampleDistributor {
  private val _default_excluded_segments = Set(
    "target",
    ".git",
    ".bsp",
    ".bloop",
    ".metals",
    ".idea",
    ".cache",
    ".vscode",
    ".scala-build",
    "car.d",
    "component.d",
    "component-repository.d",
    "tmprepo"
  )
  private val _slug_pattern = "^[a-z0-9][a-z0-9-]*$".r
  final case class PlannedArchive(kind: String, sampleName: Option[String], path: Path)

  def distribute(args: List[String]): Unit = {
    val projectdir = _project_dir(args)
    if (!Files.isDirectory(projectdir))
      RAISE.invalidArgumentFault(s"Project directory does not exist: ${projectdir}")
    val config = CozyProjectYamlConfig.loadOperationDefaults(projectdir)
    val projectmetadata = CozyProjectYamlConfig.load(projectdir.resolve("project.yaml"))
    val warehousedir = _value(args, "warehouse").
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      orElse(_config_path(projectdir, config.value("warehouse.repository"))).
      getOrElse(RAISE.invalidArgumentFault("Missing --warehouse for distribute-samples"))
    val name = _value(args, "name").orElse(config.value("publication.name")).map(_validate_name).getOrElse(RAISE.invalidArgumentFault("Missing --name for distribute-samples"))
    val version = _value(args, "version").orElse(_project_version(projectdir)).getOrElse(RAISE.invalidArgumentFault("Missing --version for distribute-samples"))
    val publicationpath = _value(args, "path").orElse(projectmetadata.value("project.path")).orElse(config.value("publication.path")).map(CozyPublicationPaths.validatePublicationPath)
    val samplesdir = _value(args, "samples-dir").orElse(config.value("publication.samples_dir")).
      map(p => _config_path(projectdir, Some(p)).get).
      getOrElse(projectdir.resolve("samples"))
    val excludes = _default_excluded_segments ++ config.list("publication.source_manifest.excludes")
    val samples = _sample_dirs(samplesdir)
    if (samples.isEmpty)
      RAISE.invalidArgumentFault(s"No sample projects found under: ${samplesdir}")
    val samplepairs = _validate_unique_sample_names(samples.map(sample => sample -> _validate_name(_slugify(sample.getFileName.toString))))
    val archives = _planned_archives(warehousedir, name, publicationpath, version, samplepairs)
    if (_flag(args, "dry-run")) {
      _print_plan(warehousedir, archives)
      return
    }
    _zip_sample_collection(samplesdir, archives.head.path, excludes)
    samplepairs.foreach { case (sample, samplename) =>
      val out = archives.find(_.sampleName.contains(samplename)).map(_.path).
        getOrElse(warehousedir.resolve(CozyPublicationPaths.sampleDownloadPath(name, publicationpath, samplename, version)))
      _zip_dir(sample, out, excludes)
    }
  }

  private def _planned_archives(
    warehousedir: Path,
    name: String,
    publicationpath: Option[String],
    version: String,
    samples: Vector[(Path, String)]
  ): Vector[PlannedArchive] =
    PlannedArchive(
      "sample-collection-zip",
      None,
      warehousedir.resolve(CozyPublicationPaths.collectionDownloadPath(name, publicationpath, version))
    ) +: samples.map { case (_, samplename) =>
      PlannedArchive(
        "sample-zip",
        Some(samplename),
        warehousedir.resolve(CozyPublicationPaths.sampleDownloadPath(name, publicationpath, samplename, version))
      )
    }

  private def _print_plan(warehousedir: Path, archives: Vector[PlannedArchive]): Unit = {
    println("distribute-samples dry-run")
    archives.foreach { archive =>
      val warehousepath = warehousedir.relativize(archive.path).toString.replace('\\', '/')
      val sample = archive.sampleName.map(x => s" sample=${x}").getOrElse("")
      println(s"${archive.kind}${sample} warehousePath=${warehousepath} file=${archive.path}")
    }
  }

  private def _zip_sample_collection(samplesdir: Path, out: Path, excludes: Set[String]): Unit =
    _zip_dir(samplesdir, out, excludes)

  private def _zip_dir(sourcedir: Path, out: Path, excludes: Set[String]): Unit = {
    val parent = Option(out.getParent).getOrElse(Paths.get("."))
    Files.createDirectories(parent)
    val tmp = Files.createTempFile(parent, out.getFileName.toString, ".tmp")
    val zip = new ZipOutputStream(Files.newOutputStream(tmp))
    try {
      _sample_files(sourcedir, excludes).foreach { file =>
        val rel = sourcedir.relativize(file).toString.replace('\\', '/')
        val entry = new ZipEntry(rel)
        entry.setTime(0L)
        zip.putNextEntry(entry)
        Files.copy(file, zip)
        zip.closeEntry()
      }
    } finally {
      zip.close()
    }
    _replace_file(tmp, out)
  }

  private def _replace_file(tmp: Path, out: Path): Unit =
    try {
      Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch {
      case _: java.nio.file.AtomicMoveNotSupportedException =>
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING)
      case e: Throwable =>
        Files.deleteIfExists(tmp)
        throw e
    }

  private def _sample_files(sampledir: Path, excludes: Set[String]): Vector[Path] = {
    val stream = Files.walk(sampledir)
    try {
      stream.iterator().asScala.toVector.collect {
        case p if Files.isRegularFile(p) && !_excluded(sampledir, p, excludes) => p
      }.sortBy(p => sampledir.relativize(p).toString.replace('\\', '/'))
    } finally {
      stream.close()
    }
  }

  private def _excluded(root: Path, path: Path, excludes: Set[String]): Boolean = {
    val rel = root.relativize(path).toString.replace('\\', '/')
    val segments = rel.split('/').toVector
    val normalizedexcludes = excludes.map(_.trim.stripPrefix("/").stripSuffix("/")).filter(_.nonEmpty)
    val excludedbyname = normalizedexcludes.exists(x => !x.contains("/") && segments.contains(x))
    val excludedbypath = normalizedexcludes.exists(x => x.contains("/") && (rel == x || rel.startsWith(x + "/")))
    excludedbyname || excludedbypath
  }

  private def _sample_dirs(samplesdir: Path): Vector[Path] =
    if (!Files.isDirectory(samplesdir))
      Vector.empty
    else
      Option(samplesdir.toFile.listFiles()).toVector.flatten.
        filter(f => f.isDirectory && new java.io.File(f, "build.sbt").isFile).
        map(_.toPath.toAbsolutePath.normalize()).
        sortBy(_.getFileName.toString)

  private def _validate_unique_sample_names(samples: Vector[(Path, String)]): Vector[(Path, String)] = {
    val duplicates = samples.groupBy(_._2).collect {
      case (name, xs) if xs.size > 1 =>
        s"${name}: ${xs.map(_._1.getFileName.toString).sorted.mkString(", ")}"
    }.toVector.sorted
    if (duplicates.nonEmpty)
      RAISE.invalidArgumentFault(s"Duplicate sample slug(s): ${duplicates.mkString("; ")}")
    samples
  }

  private def _project_version(projectdir: Path): Option[String] =
    if (Files.isRegularFile(projectdir.resolve("build.sbt")))
      _sbt_setting(Files.readString(projectdir.resolve("build.sbt"), StandardCharsets.UTF_8), "version")
    else
      None

  private def _sbt_setting(buildsbt: String, key: String): Option[String] = {
    val pattern = ("""(?m)^\s*(?:ThisBuild\s*/\s*)?""" + java.util.regex.Pattern.quote(key) + """\s*:=\s*"([^"]+)""").r
    pattern.findFirstMatchIn(buildsbt).map(_.group(1).trim).filter(_.nonEmpty)
  }

  private def _project_dir(args: List[String]): Path =
    _parsed(args).pathProperty("project").
      orElse(_parsed(args).argument("project").map(p => Paths.get(p).toAbsolutePath.normalize())).
      getOrElse(RAISE.invalidArgumentFault("Missing project directory for distribute-samples"))

  private def _value(args: List[String], key: String): Option[String] =
    _parsed(args).property(key)

  private def _flag(args: List[String], key: String): Boolean =
    args.exists(_ == s"--${key}") ||
      args.sliding(2).collectFirst {
        case List(flag, value) if flag == s"--${key}" => value
      }.exists(x => x.equalsIgnoreCase("true") || x == "1" || x.equalsIgnoreCase("yes"))

  private val _request_parameters = Vector(
    spec.Parameter.argumentFile("project"),
    spec.Parameter.propertyFileOption("project"),
    spec.Parameter.propertyFileOption("warehouse"),
    spec.Parameter.propertyFileOption("samples-dir"),
    spec.Parameter.property("name"),
    spec.Parameter.property("path"),
    spec.Parameter.property("version")
  )

  private def _parsed(args: List[String]): CozyCliArgs.Parsed =
    CozyCliArgs.parse(_request_parameters: _*)(args)

  private def _config_path(projectdir: Path, value: Option[String]): Option[Path] =
    value.map { p =>
      val path = Paths.get(p)
      if (path.isAbsolute)
        path.normalize()
      else
        projectdir.resolve(path).toAbsolutePath.normalize()
    }

  private def _validate_name(value: String): String = {
    val name = value.trim
    _slug_pattern.findFirstIn(name) match {
      case Some(x) if x == name => name
      case _ => RAISE.invalidArgumentFault(s"Invalid name: ${value}. Expected ${_slug_pattern.regex}")
    }
  }

  private def _slugify(value: String): String =
    value.toLowerCase(java.util.Locale.ROOT).
      replaceAll("[^a-z0-9]+", "-").
      stripPrefix("-").
      stripSuffix("-")
}
