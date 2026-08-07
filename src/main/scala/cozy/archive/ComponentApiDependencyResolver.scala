package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.zip.ZipFile

import scala.collection.JavaConverters._

import cozy.config.CozyProjectYamlConfig
import org.goldenport.RAISE
import play.api.libs.json._

/*
 * @since   Jul. 12, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object ComponentApiDependencyResolver {
  def resolve(args: List[String]): Unit = {
    val consumerdescriptor = _required_path(args, "consumer-descriptor")
    val outputdir = _required_path(args, "output-dir")
    val assemblydescriptor = _path(args, "assembly-descriptor")
    val dependencies = _values(args, "dependency").map(_parse_dependency)
    _resolve_dependencies(consumerdescriptor, dependencies, outputdir, assemblydescriptor)
  }

  private[cozy] def _resolve_dependencies(
    consumerdescriptor: Path,
    dependencies: Vector[Dependency],
    outputdir: Path,
    assemblydescriptor: Option[Path]
  ): Vector[Path] = {
    val consumersource = s"consumer-descriptor:${consumerdescriptor}"
    val consumer = _load_descriptor(Files.readString(consumerdescriptor, StandardCharsets.UTF_8), consumersource)
    consumer.coordinate(consumersource)
    val providers = dependencies.flatMap(_load_provider)
    assemblydescriptor.foreach(_validate_assembly_dependencies(_, dependencies))
    val selected = consumer.required.flatMap { requirement =>
      val apimatches = providers.filter(_.provided.apiClass == requirement.apiClass)
      val matches = requirement.abiHash.map(hash => apimatches.filter(_.provided.abiHash == hash)).getOrElse(apimatches)
      if (apimatches.nonEmpty && matches.isEmpty)
        RAISE.invalidArgumentFault(
          s"Required component API ABI is incompatible: ${requirement.apiClass}:${requirement.abiHash.getOrElse("<unspecified>")}"
        )
      matches match {
        case Vector(provider) => Vector(provider)
        case Vector() if !requirement.required => Vector.empty
        case Vector() =>
          RAISE.invalidArgumentFault(s"Required component API is not provided by declared CAR dependencies: ${requirement.apiClass}")
        case xs =>
          RAISE.invalidArgumentFault(
            s"Required component API is provided ambiguously: ${requirement.apiClass} (${xs.map(_.dependency.coordinate.dependencyKey).mkString(", ")})"
          )
      }
    }.foldLeft(Vector.empty[Provider]) { (z, provider) =>
      if (z.exists(x => x.provided.apiClass == provider.provided.apiClass && x.provided.abiHash == provider.provided.abiHash)) z
      else z :+ provider
    }
    _replace_directory(outputdir)
    selected.map(_extract_api_jar(_, outputdir))
  }

  private def _load_provider(dependency: Dependency): Vector[Provider] = {
    val archive = new ZipFile(dependency.archive.toFile)
    try {
      val entry = Option(archive.getEntry("component-api-descriptor.json")).getOrElse {
        RAISE.invalidArgumentFault(s"Dependency CAR has no component-api-descriptor.json: ${dependency.archive}")
      }
      val input = archive.getInputStream(entry)
      val source = s"dependency-car:${dependency.archive}"
      val descriptor = try _load_descriptor(new String(input.readAllBytes(), StandardCharsets.UTF_8), source)
      finally input.close()
      CozyComponentReleaseCoordinateCodec.requireExact(
        dependency.coordinate,
        descriptor.coordinate(source),
        source
      )
      descriptor.provided.map { provided =>
        if (provided.abiHash.trim.isEmpty)
          RAISE.invalidArgumentFault(s"Provided component API has no ABI hash: ${provided.apiClass}")
        CozyComponentReleaseCoordinateCodec.requireProjection(
          dependency.coordinate.version,
          provided.version.trim,
          "provided.version",
          source
        )
        CozyComponentReleaseCoordinateCodec.requireProjection(
          dependency.coordinate.apiArtifactPath,
          provided.artifactPath,
          "artifactPath",
          source
        )
        Provider(dependency, provided)
      }
    } finally {
      archive.close()
    }
  }

  private def _extract_api_jar(provider: Provider, outputdir: Path): Path = {
    val archive = new ZipFile(provider.dependency.archive.toFile)
    try {
      val entry = Option(archive.getEntry(provider.provided.artifactPath)).getOrElse {
        RAISE.invalidArgumentFault(
          s"Dependency CAR API artifact is missing: ${provider.dependency.coordinate.dependencyKey}:${provider.provided.artifactPath}"
        )
      }
      val coordinate = provider.dependency.coordinate
      val destination = outputdir.resolve(coordinate.groupPath).resolve(coordinate.mavenArtifactId).resolve(coordinate.version).resolve(Path.of(provider.provided.artifactPath).getFileName)
      Files.createDirectories(destination.getParent)
      val input = archive.getInputStream(entry)
      try Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
      finally input.close()
      destination
    } finally {
      archive.close()
    }
  }

  private def _validate_assembly_dependencies(path: Path, dependencies: Vector[Dependency]): Unit = {
    val config = CozyProjectYamlConfig.load(path)
    val components = config.json.flatMap(_.hcursor.downField("components").focus).flatMap(_.asArray).getOrElse(Vector.empty)
    val coordinates = components.flatMap { component =>
      val cursor = component.hcursor
      for {
        namespace <- cursor.get[String]("namespace").toOption
        id <- cursor.get[String]("id").toOption
        version <- cursor.get[String]("version").toOption
      } yield CozyComponentReleaseCoordinateCodec.admit(namespace, id, version, "assembly-descriptor").dependencyKey
    }.toSet
    val missing = dependencies.filterNot(dependency => coordinates.contains(dependency.coordinate.dependencyKey))
    if (missing.nonEmpty)
      RAISE.invalidArgumentFault(
        s"assembly-descriptor.yaml must contain declared CAR dependencies: ${missing.map(_.coordinate.dependencyKey).mkString(", ")}"
      )
  }

  private def _load_descriptor(text: String, source: String): Descriptor = {
    val json = Json.parse(text)
    // Validate the raw public contract before macro decoding can discard unknown fields.
    CozyComponentReleaseCoordinateCodec.readComponent(json, source)
    json.validate[Descriptor] match {
      case JsSuccess(value, _) if value.schemaVersion == "cncf.component-api.v2" => value
      case JsSuccess(value, _) => RAISE.invalidArgumentFault(s"component.api.schema.unsupported source=$source actual=${value.schemaVersion}")
      case JsError(errors) =>
        RAISE.invalidArgumentFault(s"Invalid component API descriptor ${source}: ${errors.mkString("; ")}")
    }
  }

  private def _replace_directory(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount)(Ordering[Int].reverse).foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
    Files.createDirectories(path)
  }

  private def _parse_dependency(value: String): Dependency =
    value.split("\\t", -1).toVector match {
      case Vector(namespace, id, version, archive) if archive.trim.nonEmpty =>
        Dependency(namespace, id, version, Path.of(archive).toAbsolutePath.normalize())
      case Vector(_, _, _) =>
        RAISE.invalidArgumentFault("component.api.dependency.payload.v2.required expected=namespace<TAB>id<TAB>version<TAB>archive")
      case _ => RAISE.invalidArgumentFault(s"component.api.dependency.payload.v2.required actual=$value")
    }

  private def _values(args: List[String], key: String): Vector[String] =
    args.zipWithIndex.collect {
      case (value, index) if value == s"--${key}" && index + 1 < args.length => args(index + 1)
    }.toVector

  private def _path(args: List[String], key: String): Option[Path] =
    _values(args, key).headOption.map(value => Path.of(value).toAbsolutePath.normalize())

  private def _required_path(args: List[String], key: String): Path =
    _path(args, key).getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private final case class ComponentCoordinate(namespace: Option[String], id: Option[String], version: Option[String])
  private object ComponentCoordinate { implicit val reads: Reads[ComponentCoordinate] = Json.reads[ComponentCoordinate] }
  private final case class RequiredApi(apiClass: String, required: Boolean, abiHash: Option[String])
  private object RequiredApi { implicit val reads: Reads[RequiredApi] = Json.reads[RequiredApi] }
  private final case class ProvidedApi(apiClass: String, version: String, artifactPath: String, abiHash: String)
  private object ProvidedApi { implicit val reads: Reads[ProvidedApi] = Json.reads[ProvidedApi] }
  private final case class Descriptor(
    schemaVersion: String,
    component: ComponentCoordinate,
    provided: Vector[ProvidedApi],
    required: Vector[RequiredApi]
  ) {
    def coordinate(source: String): CozyComponentReleaseCoordinateCodec.Coordinate =
      CozyComponentReleaseCoordinateCodec.admit(component.namespace.orNull, component.id.orNull, component.version.orNull, source)
  }
  private object Descriptor { implicit val reads: Reads[Descriptor] = Json.reads[Descriptor] }

  private[cozy] final case class Dependency(coordinate: CozyComponentReleaseCoordinateCodec.Coordinate, archive: Path)
  private[cozy] object Dependency {
    def apply(namespace: String, id: String, version: String, archive: Path): Dependency =
      Dependency(CozyComponentReleaseCoordinateCodec.admit(namespace, id, version, "component-api-dependency"), archive)

    /** Retained only to keep external callers source-compatible until CID-04D/CID-06 migration. */
    def apply(name: String, version: String, archive: Path): Dependency =
      RAISE.invalidArgumentFault(
        s"component.api.dependency.payload.v2.required legacy-name=$name version=$version"
      )
  }
  private final case class Provider(dependency: Dependency, provided: ProvidedApi)
}
