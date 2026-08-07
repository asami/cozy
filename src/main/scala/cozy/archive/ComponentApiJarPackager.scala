package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.regex.Pattern
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import scala.collection.JavaConverters._

import org.goldenport.RAISE
import play.api.libs.json._

/*
 * @since   Jul. 12, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object ComponentApiJarPackager {
  def build(args: List[String]): Unit = {
    val save = _required_path(args, "save")
    val mainjar = _required_path(args, "main-jar")
    val descriptor = _required_path(args, "descriptor")
    _build_api_jar(mainjar, descriptor, save)
  }

  private[cozy] def _build_api_jar(mainjar: Path, descriptor: Path, save: Path): Unit = {
    val contract = _parse_descriptor(descriptor)
    val coordinate = _require_contract(contract, descriptor.toString)
    if (contract.provided.isEmpty) {
      Files.deleteIfExists(save)
    } else {
      val artifactpaths = contract.provided.map(_.artifactPath).distinct
      if (artifactpaths.size != 1)
        RAISE.invalidArgumentFault(
          s"Component API descriptor must publish one shared API artifact, but declared: ${artifactpaths.mkString(", ")}"
        )
      val expectedname = Path.of(artifactpaths.head).getFileName.toString
      CozyComponentReleaseCoordinateCodec.requireProjection(
        coordinate.apiArtifactPath,
        artifactpaths.head,
        "artifactPath",
        descriptor.toString
      )
      if (save.getFileName.toString != expectedname)
        RAISE.invalidArgumentFault(
          s"Component API output ${save.getFileName} does not match descriptor artifact ${expectedname}"
        )
      _write_api_jar(mainjar, save, contract.provided.flatMap(_.publicTypes))
    }
  }

  private[cozy] def _with_implementation_jar[A](
    mainjar: Path,
    descriptor: Path
  )(f: Path => A): A = {
    val contract = _parse_descriptor(descriptor)
    _require_contract(contract, descriptor.toString)
    val patterns = contract.provided.flatMap(_.publicTypes).flatMap(_.artifactPatterns).distinct
    if (patterns.isEmpty)
      f(mainjar)
    else {
      val temporary = Files.createTempFile(mainjar.getParent, mainjar.getFileName.toString, ".implementation.tmp")
      try {
        _write_implementation_jar(mainjar, temporary, patterns.map(_glob_pattern))
        f(temporary)
      } finally {
        Files.deleteIfExists(temporary)
      }
    }
  }

  private def _parse_descriptor(path: Path): ComponentApiContract = {
    val json = Json.parse(Files.readString(path, StandardCharsets.UTF_8))
    // Validate the raw public contract before macro decoding can discard unknown fields.
    CozyComponentReleaseCoordinateCodec.readComponent(json, path.toString)
    json.validate[ComponentApiContract] match {
      case JsSuccess(value, _) => value
      case JsError(errors) =>
        val detail = errors.map { case (p, xs) =>
          s"${p.toJsonString}: ${xs.map(_.message).mkString(", ")}"
        }.mkString("; ")
        RAISE.invalidArgumentFault(s"Invalid component API descriptor ${path}: ${detail}")
    }
  }

  private def _require_contract(
    contract: ComponentApiContract,
    source: String
  ): CozyComponentReleaseCoordinateCodec.Coordinate = {
    if (contract.schemaVersion != "cncf.component-api.v2")
      RAISE.invalidArgumentFault(s"component.api.schema.unsupported source=$source actual=${contract.schemaVersion}")
    val coordinate = CozyComponentReleaseCoordinateCodec.admit(
      contract.component.namespace,
      contract.component.id,
      contract.component.version,
      source
    )
    contract.provided.foreach { provided =>
      CozyComponentReleaseCoordinateCodec.requireProjection(
        coordinate.version,
        provided.version.trim,
        "provided.version",
        source
      )
    }
    coordinate
  }

  private def _write_api_jar(mainjar: Path, save: Path, publictypes: Vector[PublicType]): Unit = {
    val patterns = publictypes.flatMap(_.artifactPatterns).distinct
    val matchers = patterns.map(_glob_pattern)
    val requiredclasses = publictypes.map { publictype =>
      publictype.artifactPatterns.find(x => !x.contains("*") && x.endsWith(".class")).getOrElse {
        RAISE.invalidArgumentFault(s"Public type ${publictype.className} has no exact class artifact pattern")
      }
    }
    val source = new ZipFile(mainjar.toFile)
    try {
      val sourceentries = source.entries().asScala.toVector.filterNot(_.isDirectory)
      val selected = sourceentries.filter(entry => matchers.exists(_.matcher(entry.getName).matches())).sortBy(_.getName)
      val selectednames = selected.map(_.getName).toSet
      val missing = requiredclasses.filterNot(selectednames.contains)
      if (missing.nonEmpty)
        RAISE.invalidArgumentFault(s"Component API classes are missing from ${mainjar}: ${missing.mkString(", ")}")
      selected.foreach(entry => _validate_public_artifact(entry.getName))
      if (selected.isEmpty)
        RAISE.invalidArgumentFault(s"Component API descriptor selected no artifacts from ${mainjar}")

      Option(save.getParent).foreach(Files.createDirectories(_))
      val temporary = Files.createTempFile(save.getParent, save.getFileName.toString, ".tmp")
      try {
        val output = new ZipOutputStream(Files.newOutputStream(temporary))
        try {
          _write_entry(output, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n\n".getBytes(StandardCharsets.UTF_8))
          selected.foreach { entry =>
            val input = source.getInputStream(entry)
            try _write_entry(output, entry.getName, input.readAllBytes())
            finally input.close()
          }
        } finally {
          output.close()
        }
        Files.move(temporary, save, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      } finally {
        Files.deleteIfExists(temporary)
      }
    } finally {
      source.close()
    }
  }

  private def _write_implementation_jar(
    mainjar: Path,
    save: Path,
    publicmatchers: Vector[Pattern]
  ): Unit = {
    val source = new ZipFile(mainjar.toFile)
    try {
      val output = new ZipOutputStream(Files.newOutputStream(save))
      try {
        source.entries().asScala.toVector
          .filterNot(_.isDirectory)
          .filterNot(entry => publicmatchers.exists(_.matcher(entry.getName).matches()))
          .sortBy(_.getName)
          .foreach { entry =>
            val input = source.getInputStream(entry)
            try _write_entry(output, entry.getName, input.readAllBytes())
            finally input.close()
          }
      } finally {
        output.close()
      }
    } finally {
      source.close()
    }
  }

  private def _glob_pattern(glob: String): Pattern = {
    val regex = glob.split("\\*", -1).map(Pattern.quote).mkString(".*")
    Pattern.compile(s"^${regex}$$")
  }

  private def _validate_public_artifact(path: String): Unit = {
    val forbidden = Vector("/impl/", "/persistence/", "/repository/")
    if (forbidden.exists(path.contains) || path.contains("ComponentFactory"))
      RAISE.invalidArgumentFault(s"Component API artifact contains forbidden implementation class: ${path}")
  }

  private def _write_entry(output: ZipOutputStream, name: String, bytes: Array[Byte]): Unit = {
    val entry = new ZipEntry(name)
    entry.setTime(0L)
    output.putNextEntry(entry)
    output.write(bytes)
    output.closeEntry()
  }

  private def _required_path(args: List[String], key: String): Path = {
    val index = args.indexOf(s"--${key}")
    if (index < 0 || index + 1 >= args.length)
      RAISE.invalidArgumentFault(s"Missing --${key}")
    Path.of(args(index + 1)).toAbsolutePath.normalize()
  }

  private final case class ComponentCoordinate(namespace: String, id: String, version: String)
  private object ComponentCoordinate {
    implicit val reads: Reads[ComponentCoordinate] = Json.reads[ComponentCoordinate]
  }

  private final case class ComponentApiContract(
    schemaVersion: String,
    component: ComponentCoordinate,
    provided: Vector[ProvidedApi]
  )
  private object ComponentApiContract {
    implicit val reads: Reads[ComponentApiContract] = Json.reads[ComponentApiContract]
  }

  private final case class ProvidedApi(version: String, artifactPath: String, publicTypes: Vector[PublicType])
  private object ProvidedApi {
    implicit val reads: Reads[ProvidedApi] = Json.reads[ProvidedApi]
  }

  private final case class PublicType(className: String, artifactPatterns: Vector[String])
  private object PublicType {
    implicit val reads: Reads[PublicType] = Json.reads[PublicType]
  }
}
