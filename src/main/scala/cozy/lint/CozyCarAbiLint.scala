package cozy.lint

import org.goldenport.RAISE
import play.api.libs.json._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.zip.ZipFile
import scala.collection.JavaConverters._
import scala.util.Try

/*
 * @since   Jul.  7, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCarAbiLint {
  sealed trait Level {
    def name: String
  }
  object Level {
    case object Ok extends Level {
      val name = "OK"
    }
    case object Fail extends Level {
      val name = "FAIL"
    }
    case object Warn extends Level {
      val name = "WARN"
    }
  }

  final case class Finding(
    level: Level,
    code: String,
    message: String,
    path: Path,
    line: Int
  )

  final case class AbiManifest(
    car: CarCoordinate,
    abi: AbiSurface
  )
  final case class CarCoordinate(name: String, version: String)
  final case class AbiSurface(
    version: Int,
    exports: AbiExports,
    dependencies: Vector[AbiDependency]
  )
  final case class AbiExports(
    components: Vector[AbiComponent],
    operations: Vector[AbiOperation],
    entities: Vector[AbiEntity]
  )
  final case class AbiComponent(
    name: String
  )
  final case class AbiOperation(
    name: String,
    kind: String,
    input: Option[String],
    output: Option[String],
    execution: Option[String]
  )
  final case class AbiEntity(
    name: String,
    fields: Vector[AbiField]
  )
  final case class AbiField(
    name: String,
    typeName: String,
    required: Boolean
  )
  final case class AbiDependency(
    name: String,
    abiRange: Option[String]
  )

  def execute(args: List[String]): Int = {
    val config = Config.create(args)
    val findings = lint(config.path, config.baseline)
    _render(config, findings)
  }

  private[cozy] def execute(args: List[String], baseline: Option[Path]): Int = {
    val config = Config.create(args, baseline)
    val findings = lint(config.path, config.baseline)
    _render(config, findings)
  }

  private def _render(config: Config, findings: Vector[Finding]): Int = {
    config.format match {
      case "json" => println(toJson(findings))
      case "text" => println(toText(config.path, findings))
      case other => RAISE.invalidArgumentFault(s"Unsupported lint format: ${other}")
    }
    if (findings.exists(_.level == Level.Fail) || (config.strict && findings.exists(_strict_warning))) 1 else 0
  }

  private def _strict_warning(finding: Finding): Boolean =
    finding.level == Level.Warn && finding.code != "abi.baseline.missing"

  private[cozy] def lint(path: Path, baseline: Option[Path]): Vector[Finding] = {
    val current = _manifest(path)
    current match {
      case Left(finding) => Vector(_project_missing_manifest_warning(path).getOrElse(finding))
      case Right((currentpath, currentmanifest)) =>
        val basepath = baseline.orElse(_baseline_manifest_path(path, currentpath, currentmanifest))
        val manifestfinding = Finding(
          Level.Ok,
          "abi.manifest",
          s"CAR ABI manifest ${currentmanifest.car.name}:${currentmanifest.car.version} is readable.",
          currentpath,
          1
        )
        val surfacewarnings = _surface_warnings(currentmanifest, currentpath)
        basepath match {
          case Some(p) =>
            _manifest(p) match {
              case Left(finding) => Vector(manifestfinding, finding) ++ surfacewarnings
              case Right((baselinepath, baselinemanifest)) =>
                (manifestfinding +: compare(baselinemanifest, currentmanifest, baselinepath, currentpath)) ++ surfacewarnings
            }
          case None =>
            Vector(
              manifestfinding,
              Finding(
                Level.Warn,
                "abi.baseline.missing",
                "No CAR ABI baseline manifest was provided or found; compatibility against the previously released CAR was not checked.",
                currentpath,
                1
              )
            ) ++ surfacewarnings
        }
    }
  }

  private[cozy] def lintBuildProject(projectroot: Path): Vector[Finding] = {
    val root = projectroot.toAbsolutePath.normalize()
    _current_manifest_path(root) match {
      case Some(path) => lint(path, None)
      case None if _is_car_project(root) =>
        Vector(Finding(
          Level.Warn,
          "abi.manifest.missing",
          "CAR project has no ABI manifest. Run package-car, provide src/main/car/abi-manifest.json, or provide target/cozy/abi-manifest.json before release lint.",
          root,
          1
        ))
      case None =>
        Vector.empty
    }
  }

  private def _project_missing_manifest_warning(path: Path): Option[Finding] = {
    val normalized = path.toAbsolutePath.normalize()
    if (Files.isDirectory(normalized) && _is_car_project(normalized))
      Some(Finding(
        Level.Warn,
        "abi.manifest.missing",
        "CAR project has no ABI manifest. Run package-car, provide src/main/car/abi-manifest.json, or provide target/cozy/abi-manifest.json before release lint.",
        normalized,
        1
      ))
    else
      None
  }

  private[cozy] def compare(
    baseline: AbiManifest,
    current: AbiManifest,
    baselinepath: Path,
    currentpath: Path
  ): Vector[Finding] = {
    val policy = VersionPolicy.create(baseline.car.version, current.car.version, currentpath)
    policy.finding.toVector ++ {
      policy.mode match {
        case VersionMode.Major => _breaking_changes(baseline, current, baselinepath, currentpath).map { f =>
          f.copy(level = Level.Ok, message = s"Major version permits breaking ABI change: ${f.message}")
        }
        case VersionMode.Minor =>
          _breaking_changes(baseline, current, baselinepath, currentpath) ++
            _minor_additions(baseline, current, currentpath)
        case VersionMode.Patch =>
          val breaking = _breaking_changes(baseline, current, baselinepath, currentpath)
          val additions = _minor_additions(baseline, current, currentpath)
          val dependencychanges = _dependency_changes(baseline, current, currentpath)
          val allchanges = breaking ++ additions ++ dependencychanges
          if (allchanges.isEmpty)
            Vector(Finding(
              Level.Ok,
              "abi.compatibility.patch",
              "Patch version keeps the CAR ABI unchanged.",
              currentpath,
              1
            ))
          else
            allchanges.map(f => f.copy(level = Level.Fail, code = s"abi.patch.${f.code.stripPrefix("abi.")}"))
        case VersionMode.Invalid =>
          Vector.empty
      }
    }
  }

  private def _breaking_changes(
    baseline: AbiManifest,
    current: AbiManifest,
    baselinepath: Path,
    currentpath: Path
  ): Vector[Finding] = {
    val basecomponents = baseline.abi.exports.components.map(x => x.name -> x).toMap
    val currentcomponents = current.abi.exports.components.map(x => x.name -> x).toMap
    val removedcomponents = basecomponents.keys.toVector.filterNot(currentcomponents.contains).sorted.map { name =>
      Finding(Level.Fail, "abi.component.removed", s"Exported component '${name}' was removed.", currentpath, 1)
    }

    val baseops = baseline.abi.exports.operations.map(x => x.name -> x).toMap
    val currentops = current.abi.exports.operations.map(x => x.name -> x).toMap
    val removedops = baseops.keys.toVector.filterNot(currentops.contains).sorted.map { name =>
      Finding(Level.Fail, "abi.operation.removed", s"Exported operation '${name}' was removed.", currentpath, 1)
    }
    val changedops = baseops.keys.toVector.flatMap { name =>
      for {
        b <- baseops.get(name)
        c <- currentops.get(name)
        if _operation_signature(b) != _operation_signature(c)
      } yield Finding(
        Level.Fail,
        "abi.operation.changed",
        s"Exported operation '${name}' changed signature (${_operation_signature_changes(b, c).mkString(", ")}).",
        currentpath,
        1
      )
    }

    val baseentities = baseline.abi.exports.entities.map(x => x.name -> x).toMap
    val currententities = current.abi.exports.entities.map(x => x.name -> x).toMap
    val removedentities = baseentities.keys.toVector.filterNot(currententities.contains).sorted.map { name =>
      Finding(Level.Fail, "abi.entity.removed", s"Exported entity '${name}' was removed.", currentpath, 1)
    }
    val changedfields = baseentities.keys.toVector.sorted.flatMap { name =>
      for {
        b <- baseentities.get(name)
        c <- currententities.get(name)
      } yield _entity_breaking_changes(name, b, c, currentpath)
    }.flatten

    removedcomponents ++ removedops ++ changedops ++ removedentities ++ changedfields ++ _dependency_narrowing(baseline, current, currentpath)
  }

  private def _entity_breaking_changes(
    entityname: String,
    baseline: AbiEntity,
    current: AbiEntity,
    currentpath: Path
  ): Vector[Finding] = {
    val basefields = baseline.fields.map(x => x.name -> x).toMap
    val currentfields = current.fields.map(x => x.name -> x).toMap
    val removed = basefields.keys.toVector.filterNot(currentfields.contains).sorted.map { name =>
      Finding(Level.Fail, "abi.entity.field.removed", s"Exported entity '${entityname}' field '${name}' was removed.", currentpath, 1)
    }
    val changed = basefields.keys.toVector.flatMap { name =>
      for {
        b <- basefields.get(name)
        c <- currentfields.get(name)
        if b.typeName != c.typeName || b.required != c.required
      } yield Finding(Level.Fail, "abi.entity.field.changed", s"Exported entity '${entityname}' field '${name}' changed type or required contract.", currentpath, 1)
    }
    val requiredadded = currentfields.values.toVector.filter(x => !basefields.contains(x.name) && x.required).sortBy(_.name).map { field =>
      Finding(Level.Fail, "abi.entity.field.required-added", s"Exported entity '${entityname}' added required field '${field.name}'.", currentpath, 1)
    }
    removed ++ changed ++ requiredadded
  }

  private def _minor_additions(
    baseline: AbiManifest,
    current: AbiManifest,
    currentpath: Path
  ): Vector[Finding] = {
    val basecomponents = baseline.abi.exports.components.map(_.name).toSet
    val addedcomponents = current.abi.exports.components.filterNot(x => basecomponents.contains(x.name)).sortBy(_.name).map { component =>
      Finding(Level.Ok, "abi.component.added", s"Exported component '${component.name}' was added.", currentpath, 1)
    }

    val baseops = baseline.abi.exports.operations.map(_.name).toSet
    val addedops = current.abi.exports.operations.filterNot(x => baseops.contains(x.name)).sortBy(_.name).map { op =>
      Finding(Level.Ok, "abi.operation.added", s"Exported operation '${op.name}' was added.", currentpath, 1)
    }
    val baseentities = baseline.abi.exports.entities.map(x => x.name -> x).toMap
    val addedentities = current.abi.exports.entities.filterNot(x => baseentities.contains(x.name)).sortBy(_.name).map { entity =>
      Finding(Level.Ok, "abi.entity.added", s"Exported entity '${entity.name}' was added.", currentpath, 1)
    }
    val optionalfields = current.abi.exports.entities.flatMap { entity =>
      baseentities.get(entity.name).toVector.flatMap { base =>
        val basefields = base.fields.map(_.name).toSet
        entity.fields.filter(x => !basefields.contains(x.name) && !x.required).sortBy(_.name).map { field =>
          Finding(Level.Ok, "abi.entity.field.optional-added", s"Exported entity '${entity.name}' added optional field '${field.name}'.", currentpath, 1)
        }
      }
    }
    addedcomponents ++ addedops ++ addedentities ++ optionalfields ++ _dependency_changes(baseline, current, currentpath).map(_.copy(level = Level.Ok))
  }

  private def _surface_warnings(manifest: AbiManifest, currentpath: Path): Vector[Finding] = {
    val exports = manifest.abi.exports
    val entityfields = exports.entities.flatMap(_.fields)
    if (exports.operations.isEmpty && exports.entities.nonEmpty && entityfields.isEmpty)
      Vector(Finding(
        Level.Warn,
        "abi.surface.skeletal",
        "CAR ABI manifest has entity names but no operation signatures or entity fields; provide an explicit abi-manifest.json for release ABI enforcement.",
        currentpath,
        1
      ))
    else
      Vector.empty
  }

  private def _dependency_narrowing(
    baseline: AbiManifest,
    current: AbiManifest,
    currentpath: Path
  ): Vector[Finding] =
    _dependency_changes(baseline, current, currentpath).filter(_.message.contains("changed"))

  private def _dependency_changes(
    baseline: AbiManifest,
    current: AbiManifest,
    currentpath: Path
  ): Vector[Finding] = {
    val base = baseline.abi.dependencies.map(x => x.name -> x.abiRange).toMap
    val now = current.abi.dependencies.map(x => x.name -> x.abiRange).toMap
    (base.keySet ++ now.keySet).toVector.sorted.flatMap { name =>
      (base.get(name), now.get(name)) match {
        case (Some(l), Some(r)) if l != r =>
          Some(Finding(Level.Warn, "abi.dependency.range-changed", s"Dependency ABI range '${name}' changed from '${l.getOrElse("")}' to '${r.getOrElse("")}'.", currentpath, 1))
        case (Some(_), None) =>
          Some(Finding(Level.Warn, "abi.dependency.removed", s"Dependency ABI range '${name}' was removed.", currentpath, 1))
        case (None, Some(_)) =>
          Some(Finding(Level.Warn, "abi.dependency.added", s"Dependency ABI range '${name}' was added.", currentpath, 1))
        case _ =>
          None
      }
    }
  }

  private def _operation_signature(op: AbiOperation): (String, Option[String], Option[String], Option[String]) =
    (op.kind, op.input, op.output, op.execution)

  private def _operation_signature_changes(baseline: AbiOperation, current: AbiOperation): Vector[String] =
    Vector(
      if (baseline.kind != current.kind) Some(s"kind: '${baseline.kind}' -> '${current.kind}'") else None,
      if (baseline.input != current.input) Some(s"input: '${_signature_value(baseline.input)}' -> '${_signature_value(current.input)}'") else None,
      if (baseline.output != current.output) Some(s"output: '${_signature_value(baseline.output)}' -> '${_signature_value(current.output)}'") else None,
      if (baseline.execution != current.execution) Some(s"execution: '${_signature_value(baseline.execution)}' -> '${_signature_value(current.execution)}'") else None
    ).flatten

  private def _signature_value(value: Option[String]): String =
    value.getOrElse("<none>")

  private def _manifest(path: Path): Either[Finding, (Path, AbiManifest)] = {
    val normalized = path.toAbsolutePath.normalize()
    _manifest_text(normalized).flatMap { case (actualpath, text) =>
      parseManifest(text) match {
        case Right(manifest) => Right(actualpath -> manifest)
        case Left(message) => Left(Finding(Level.Fail, "abi.manifest.invalid", message, actualpath, 1))
      }
    }
  }

  private def _manifest_text(path: Path): Either[Finding, (Path, String)] =
    if (Files.isDirectory(path))
      _current_manifest_path(path) match {
        case Some(p) => _manifest_text(p)
        case None => Left(Finding(Level.Fail, "abi.manifest.missing", s"No ABI manifest found under ${path}.", path, 1))
      }
    else if (Files.isRegularFile(path) && path.getFileName.toString.endsWith(".car"))
      _car_manifest_text(path)
    else if (Files.isRegularFile(path))
      Right(path -> Files.readString(path, StandardCharsets.UTF_8))
    else
      Left(Finding(Level.Fail, "abi.manifest.missing", s"ABI manifest path does not exist: ${path}.", path, 1))

  private def _car_manifest_text(path: Path): Either[Finding, (Path, String)] =
    Try {
      val zip = new ZipFile(path.toFile)
      try {
        _car_manifest_entries.toStream.flatMap(name => Option(zip.getEntry(name)).map(name -> _)).headOption.map {
          case (name, entry) =>
            val in = zip.getInputStream(entry)
            try path.resolveSibling(s"${path.getFileName}!${name}") -> scala.io.Source.fromInputStream(in, "UTF-8").mkString
            finally in.close()
        }
      } finally {
        zip.close()
      }
    }.toOption.flatten match {
      case Some(result) => Right(result)
      case None => Left(Finding(Level.Fail, "abi.manifest.missing", s"CAR archive ${path} does not contain abi-manifest.json.", path, 1))
    }

  private val _car_manifest_entries = Vector(
    "abi-manifest.json",
    "META-INF/cozy/abi-manifest.json",
    "metadata/abi-manifest.json"
  )

  private def _current_manifest_path(root: Path): Option[Path] = {
    val normalized = root.toAbsolutePath.normalize()
    val candidates =
      if (Files.isDirectory(normalized))
        Vector(
          normalized.resolve("src/main/car/abi-manifest.json"),
          normalized.resolve("target/cozy/abi-manifest.json"),
          normalized.resolve("target/abi-manifest.json"),
          normalized.resolve("abi-manifest.json")
        )
      else
        Vector(normalized)
    candidates.find(Files.isRegularFile(_))
  }

  private def _baseline_manifest_path(
    root: Path,
    currentpath: Path,
    currentmanifest: AbiManifest
  ): Option[Path] = {
    val normalized = _project_root_for(root).orElse(_project_root_for(currentpath)).getOrElse {
      if (Files.isDirectory(root)) root.toAbsolutePath.normalize() else Option(root.getParent).getOrElse(root).toAbsolutePath.normalize()
    }
    _versioned_baseline_manifest_path(normalized, currentmanifest.car.version).orElse {
    Vector(
      normalized.resolve("target/cozy/abi-baseline.json"),
      normalized.resolve("target/abi-baseline.json")
    ).find(Files.isRegularFile(_))
    }
  }

  private def _project_root_for(path: Path): Option[Path] = {
    val normalized = path.toAbsolutePath.normalize()
    val start = if (Files.isDirectory(normalized)) normalized else Option(normalized.getParent).getOrElse(normalized)
    Iterator.iterate[Option[Path]](Some(start))(_.flatMap(p => Option(p.getParent))).
      takeWhile(_.isDefined).
      flatten.
      find(_is_project_root)
  }

  private def _is_project_root(path: Path): Boolean =
    Files.isRegularFile(path.resolve("project.yaml")) ||
      Files.isRegularFile(path.resolve("build.sbt")) ||
      Files.isDirectory(path.resolve("src/main/car"))

  private def _versioned_baseline_manifest_path(root: Path, currentversion: String): Option[Path] = {
    val cardir = root.resolve("src/main/car")
    if (!Files.isDirectory(cardir))
      None
    else {
      val current = _semver(currentversion)
      val stream = Files.list(cardir)
      try {
        stream.iterator().asScala.toVector.flatMap { dir =>
          val dirname = dir.getFileName.toString
          val manifest = dir.resolve("abi-manifest.json")
          for {
            version <- _release_semver(dirname)
            currentversion <- current
            if version < currentversion
            if Files.isRegularFile(manifest)
          } yield version -> manifest
        }.sortBy(_._1).lastOption.map(_._2)
      } finally {
        stream.close()
      }
    }
  }

  private def _is_car_project(root: Path): Boolean =
    _is_car_project_yaml(root.resolve("project.yaml")) ||
      Files.isRegularFile(root.resolve("build.sbt")) && Files.isDirectory(root.resolve("src/main/cozy")) ||
      Files.isDirectory(root.resolve("src/main/car"))

  private def _is_car_project_yaml(path: Path): Boolean =
    if (Files.isRegularFile(path)) {
      val text = Files.readString(path, StandardCharsets.UTF_8)
      text.linesIterator.map(_.trim).exists { line =>
        line == "kind: car" ||
          line == "type: car" ||
          line == "project.type: car" ||
          line == "packaging.kind: car" ||
          line.startsWith("car:")
      }
    } else {
      false
    }

  private[cozy] def parseManifest(text: String): Either[String, AbiManifest] =
    Try {
      val json = Json.parse(text)
      val car = (json \ "car").getOrElse(Json.obj())
      val abi = (json \ "abi").getOrElse(Json.obj())
      AbiManifest(
        CarCoordinate(
          _string(car, "name").getOrElse(_string(json, "name").getOrElse("unknown")),
          _string(car, "version").getOrElse(_string(json, "version").getOrElse("0.0.0"))
        ),
        AbiSurface(
          _int(abi, "version").getOrElse(1),
          _exports((abi \ "exports").getOrElse(Json.obj())),
          _dependencies((abi \ "dependencies").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty))
        )
      )
    }.toEither.left.map(_.getMessage)

  private def _exports(json: JsValue): AbiExports =
    AbiExports(
      (json \ "components").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).map(_component),
      (json \ "operations").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).map(_operation),
      (json \ "entities").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).map(_entity)
    )

  private def _component(json: JsValue): AbiComponent =
    AbiComponent(
      _string(json, "name").orElse(_string(json, "component")).getOrElse("")
    )

  private def _operation(json: JsValue): AbiOperation =
    AbiOperation(
      _string(json, "name").getOrElse(""),
      _string(json, "kind").getOrElse("operation"),
      _string(json, "input"),
      _string(json, "output"),
      _string(json, "execution")
    )

  private def _entity(json: JsValue): AbiEntity =
    AbiEntity(
      _string(json, "name").orElse(_string(json, "entity")).getOrElse(""),
      (json \ "fields").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).map(_field)
    )

  private def _field(json: JsValue): AbiField =
    AbiField(
      _string(json, "name").getOrElse(""),
      _string(json, "type").orElse(_string(json, "typeName")).getOrElse("Any"),
      _boolean(json, "required").getOrElse(true)
    )

  private def _dependencies(values: Vector[JsValue]): Vector[AbiDependency] =
    values.map { json =>
      AbiDependency(
        _string(json, "name").getOrElse(""),
        _string(json, "abiRange").orElse(_string(json, "abi_range"))
      )
    }

  private def _string(json: JsValue, key: String): Option[String] =
    (json \ key).asOpt[String].map(_.trim).filter(_.nonEmpty)

  private def _int(json: JsValue, key: String): Option[Int] =
    (json \ key).asOpt[Int]

  private def _boolean(json: JsValue, key: String): Option[Boolean] =
    (json \ key).asOpt[Boolean]

  private[cozy] def toJson(findings: Seq[Finding]): String =
    s"""{"findings":[${findings.map(_finding_json).mkString(",")}]}"""

  private[cozy] def toText(path: Path, findings: Seq[Finding]): String =
    if (findings.isEmpty)
      s"OK abi.no-findings [${path.toAbsolutePath.normalize()}]: No CAR ABI lint findings."
    else
      findings.map { f =>
        s"${f.level.name} ${f.code} [${f.path}:${f.line}]: ${f.message}"
      }.mkString("\n")

  private def _finding_json(f: Finding): String =
    s"""{"level":"${f.level.name}","code":"${_json(f.code)}","message":"${_json(f.message)}","path":"${_json(f.path.toString)}","line":${f.line}}"""

  private def _json(p: String): String =
    p.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c < ' ' => "\\u%04x".format(c.toInt)
      case c => c.toString
    }

  private sealed trait VersionMode
  private object VersionMode {
    case object Patch extends VersionMode
    case object Minor extends VersionMode
    case object Major extends VersionMode
    case object Invalid extends VersionMode
  }
  private final case class VersionPolicy(mode: VersionMode, finding: Option[Finding])
  private object VersionPolicy {
    def create(baseline: String, current: String, currentpath: Path): VersionPolicy =
      (_semver(baseline), _semver(current)) match {
        case (Some(b), Some(c)) if c.major > b.major => VersionPolicy(VersionMode.Major, None)
        case (Some(b), Some(c)) if c.major == b.major && c.minor > b.minor => VersionPolicy(VersionMode.Minor, None)
        case (Some(b), Some(c)) if c.major == b.major && c.minor == b.minor && c.patch >= b.patch => VersionPolicy(VersionMode.Patch, None)
        case (Some(_), Some(_)) =>
          VersionPolicy(VersionMode.Invalid, Some(Finding(Level.Fail, "abi.version.regression", s"Current version ${current} is not a SemVer-compatible upgrade from baseline ${baseline}.", currentpath, 1)))
        case _ =>
          VersionPolicy(VersionMode.Invalid, Some(Finding(Level.Fail, "abi.version.invalid", s"Could not parse baseline/current versions as SemVer: ${baseline} -> ${current}.", currentpath, 1)))
      }
  }
  private def _semver(value: String): Option[Version] = {
    val pattern = """^(\d+)\.(\d+)\.(\d+).*$""".r
    value match {
      case pattern(major, minor, patch) => Some(Version(major.toInt, minor.toInt, patch.toInt))
      case _ => None
    }
  }

  private def _release_semver(value: String): Option[Version] =
    if (value.toUpperCase.contains("SNAPSHOT"))
      None
    else {
      val pattern = """^(\d+)\.(\d+)\.(\d+)$""".r
      value match {
        case pattern(major, minor, patch) => Some(Version(major.toInt, minor.toInt, patch.toInt))
        case _ => None
      }
    }

  private final case class Version(major: Int, minor: Int, patch: Int) extends Ordered[Version] {
    def compare(that: Version): Int =
      Ordering.Tuple3[Int, Int, Int].compare((major, minor, patch), (that.major, that.minor, that.patch))
  }

  private final case class Config(path: Path, baseline: Option[Path], format: String, strict: Boolean)
  private object Config {
    def create(args: List[String]): Config =
      create(args, None)

    def create(args: List[String], defaultbaseline: Option[Path]): Config = {
      var format = "text"
      var strict = false
      var baseline = defaultbaseline
      val paths = Vector.newBuilder[String]
      @annotation.tailrec
      def go(xs: List[String]): Unit = xs match {
        case Nil =>
          ()
        case "--abi" :: rest =>
          go(rest)
        case "--baseline" :: value :: rest =>
          baseline = Some(Paths.get(value))
          go(rest)
        case x :: rest if x.startsWith("--baseline=") =>
          baseline = Some(Paths.get(x.substring("--baseline=".length)))
          go(rest)
        case "--format" :: value :: rest =>
          format = value
          go(rest)
        case x :: rest if x.startsWith("--format=") =>
          format = x.substring("--format=".length)
          go(rest)
        case "--strict" :: rest =>
          strict = true
          go(rest)
        case x :: _ if x.startsWith("-") =>
          RAISE.invalidArgumentFault(s"Unsupported lint option: ${x}")
        case x :: rest =>
          paths += x
          go(rest)
      }
      go(args)
      val values = paths.result()
      if (values.size != 1)
        RAISE.invalidArgumentFault("Usage: cozy lint abi <car|manifest|project> [--baseline <car|manifest>] [--format text|json] [--strict]")
      if (format != "text" && format != "json")
        RAISE.invalidArgumentFault(s"Unsupported lint format: ${format}")
      Config(Paths.get(values.head), baseline.map(_.toAbsolutePath.normalize()), format, strict)
    }
  }
}
