package cozy.media

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, StandardCopyOption}
import java.security.MessageDigest
import java.time.Instant
import scala.util.control.NonFatal

/*
 * @since   Aug. 25, 2026
 * @version Aug. 25, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaReceipt {
  final case class InputConfig(
    id: String,
    role: String,
    path: String,
    normalization: String
  )
  object InputConfig {
    implicit val decoder: Decoder[InputConfig] = (c: HCursor) =>
      for {
        _ <- _require_exact_keys(c, Set("id", "role", "path", "normalization"), "receipt input")
        id <- c.downField("id").as[String]
        role <- c.downField("role").as[String]
        path <- c.downField("path").as[String]
        normalization <- c.downField("normalization").as[String]
      } yield InputConfig(id, role, path, normalization)
  }

  final case class Renderer(name: String, version: String)
  object Renderer {
    implicit val decoder: Decoder[Renderer] = (c: HCursor) =>
      for {
        _ <- _require_exact_keys(c, Set("name", "version"), "receipt producer renderer")
        name <- c.downField("name").as[String]
        version <- c.downField("version").as[String]
      } yield Renderer(name, version)
  }

  final case class ProducerConfig(profile: String, renderer: Renderer)
  object ProducerConfig {
    implicit val decoder: Decoder[ProducerConfig] = (c: HCursor) =>
      for {
        _ <- _require_exact_keys(c, Set("profile", "renderer"), "receipt producer")
        profile <- c.downField("profile").as[String]
        renderer <- c.downField("renderer").as[Renderer]
      } yield ProducerConfig(profile, renderer)
  }

  final case class Config(inputs: Vector[InputConfig], producer: Option[ProducerConfig])
  object Config {
    implicit val decoder: Decoder[Config] = (c: HCursor) =>
      for {
        _ <- _require_allowed_keys(c, Set("inputs", "producer"), "receipt")
        inputs <- c.downField("inputs").as[Option[Vector[InputConfig]]]
        producer <- c.downField("producer").as[Option[ProducerConfig]]
      } yield Config(inputs.getOrElse(Vector.empty), producer)
  }

  final case class Evidence(
    id: String,
    role: String,
    path: String,
    normalization: String,
    sha256: String
  )

  final case class Producer(
    cozyVersion: String,
    profile: Option[String],
    renderer: Option[Renderer]
  )

  final case class Captured(
    inputSetSha256: String,
    inputs: Vector[Evidence],
    producer: Producer
  )

  final case class ResourceReceipt(
    inputSetSha256: String,
    inputs: Vector[Evidence],
    producer: Producer,
    operationTarget: Option[String],
    operationProfile: Option[String],
    acceptedAt: String
  )

  final case class ManifestEntry(
    id: String,
    path: String,
    sha256: String,
    receipt: Option[ResourceReceipt],
    artifacts: Vector[Artifact] = Vector.empty
  )

  final case class Artifact(
    id: String,
    role: String,
    path: String,
    sha256: String,
    sourceSha256: Option[String]
  )

  final case class Manifest(knowledge: String, resources: Vector[ManifestEntry])
  /** A fully validated acceptance document which has not yet been made visible. */
  final case class PreparedDocument(path: Path, bytes: Array[Byte])

  private val _schema = "cozy.media.v1"
  private val _receipt_schema = "cozy.media.receipt.v2"
  private val _reserved_prefix = "cozy:"

  def validateConfig(config: Config): Unit = {
    val ids = config.inputs.map { input =>
      if (input == null)
        RAISE.invalidArgumentFault("Media receipt inputs must not contain null")
      if (input.id == null || input.id.isEmpty || input.id != input.id.trim || input.id.startsWith("cozy:"))
        RAISE.invalidArgumentFault("Media receipt input ids must be non-empty exact values outside the reserved cozy: prefix")
      if (input.role == null || input.role.isEmpty || input.role != input.role.trim)
        RAISE.invalidArgumentFault(s"Media receipt input role must be a non-empty exact value: ${input.id}")
      if (input.path == null || input.path.isEmpty || input.path != input.path.trim)
        RAISE.invalidArgumentFault(s"Media receipt input path must be a non-empty exact value: ${input.id}")
      val inputpath = try Path.of(input.path) catch {
        case NonFatal(_) => RAISE.invalidArgumentFault(s"Media receipt input path is invalid: ${input.id}")
      }
      if (inputpath.isAbsolute || input.path.matches("[A-Za-z]:[\\\\/].*") || input.path.startsWith("\\\\"))
        RAISE.invalidArgumentFault(s"Media receipt input path must be relative: ${input.id}")
      if (input.path.exists(x => x == '*' || x == '?' || x == '[' || x == ']' || x == '{' || x == '}'))
        RAISE.invalidArgumentFault(s"Media receipt input path must not contain a glob pattern: ${input.id}")
      if (!Set("bytes", "structured-document").contains(input.normalization))
        RAISE.invalidArgumentFault(s"Media receipt input normalization must be bytes or structured-document: ${input.id}")
      input.id
    }
    if (ids.distinct.size != ids.size)
      RAISE.invalidArgumentFault("Media receipt input ids must be unique")
    config.producer.foreach { producer =>
      if (producer == null || producer.profile == null || producer.profile.isEmpty || producer.profile != producer.profile.trim || producer.renderer == null || producer.renderer.name == null || producer.renderer.name.isEmpty || producer.renderer.name != producer.renderer.name.trim || producer.renderer.version == null || producer.renderer.version.isEmpty || producer.renderer.version != producer.renderer.version.trim)
        RAISE.invalidArgumentFault("Media receipt producer profile and renderer name/version must be non-empty exact values")
    }
  }

  def action(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): CozyMedia.Action =
    resolved.resource.build match {
      case "video-project" =>
        if (!resolved.project.exists(_direct_regular_file)) CozyMedia.Action.MissingSource
        else if (current(plan, resolved)) CozyMedia.Action.Current
        else CozyMedia.Action.DelegateVideo
      case "prebuilt" =>
        if (!resolved.source.exists(Files.isRegularFile(_))) CozyMedia.Action.MissingSource
        else if (current(plan, resolved)) CozyMedia.Action.Current
        else CozyMedia.Action.AdoptPrebuilt
      case _ if !resolved.source.exists(Files.isRegularFile(_)) =>
        CozyMedia.Action.MissingSource
      case _ if current(plan, resolved) =>
        CozyMedia.Action.Current
      case _ =>
        CozyMedia.Action.Build
    }

  def capture(plan: CozyMedia.Plan): Captured = {
    val descriptor = Option(plan).getOrElse(_invalid("Media receipt plan must be defined"))
    val root = descriptor.descriptorRoot
    val config = descriptor.descriptor.receipt.getOrElse(Config(Vector.empty, None))
    val automatic = Vector(
      _evidence(_automatic_id("descriptor"), "descriptor", descriptor.descriptorFile, root, "bytes", direct = false),
      _evidence(_automatic_id("knowledge"), "knowledge", descriptor.knowledgeSource, root, "bytes", direct = false)
    ) ++ descriptor.resources.flatMap { resolved =>
      val id = _require_identity(resolved.resource.id, "Media resource id")
      Vector(
        resolved.source.map(path => _evidence(_automatic_id(s"resource:$id:source"), "source", path, root, "bytes", direct = false)),
        resolved.project.map(path => _evidence(_automatic_id(s"resource:$id:project"), "project", path, root, "bytes", direct = false))
      ).flatten
    } ++ descriptor.effectiveProfile.toVector.flatMap { profile =>
      profile.sourcePath.map(path => _evidence(_automatic_id(s"profile:${profile.id}"), "publication-profile", path, root, "bytes", direct = false))
    }
    val explicit = config.inputs.map { input =>
      val id = _require_identity(input.id, "Media receipt input id")
      if (id.startsWith(_reserved_prefix))
        _invalid(s"Media receipt input id uses reserved prefix ${_reserved_prefix}: $id")
      val role = _require_identity(input.role, s"Media receipt input $id role")
      val normalization = input.normalization match {
        case "bytes" | "structured-document" => input.normalization
        case _ => _invalid(s"Media receipt input $id normalization must be bytes or structured-document")
      }
      val path = _relative_path(root, input.path, s"Media receipt input $id path")
      _evidence(id, role, path, root, normalization, direct = true)
    }
    val inputs = (automatic ++ explicit).sortBy(_.id)
    val duplicates = inputs.groupBy(_.id).collect { case (id, values) if values.size > 1 => id }.toVector.sorted
    duplicates.headOption.foreach(id => _invalid(s"Media receipt input ids must be unique: $id"))
    val producer = _producer(config)
    val digestjson = _canonical(Json.obj(
      "producer" -> _producer_json(producer),
      "inputs" -> Json.fromValues(inputs.map(_evidence_json))
    ))
    Captured(_sha256_bytes(digestjson.noSpaces.getBytes(StandardCharsets.UTF_8)), inputs, producer)
  }

  def receipt(captured: Captured, target: Option[String], profile: Option[String]): ResourceReceipt =
    ResourceReceipt(
      captured.inputSetSha256,
      captured.inputs,
      captured.producer,
      target.map(value => _require_identity(value, "Media receipt operation target")),
      profile.map(value => _require_identity(value, "Media receipt operation profile")),
      Instant.now().toString
    )

  def manifest(path: Path): Option[Manifest] = {
    if (!_direct_regular_file(path)) None
    else Some(_manifest(path))
  }

  def current(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Boolean =
    try {
      val output = _output(resolved).getOrElse(return false)
      if (!Files.isRegularFile(output)) return false
      val relative = _relative_from_path(plan.descriptorRoot, output, "Media receipt output")
      val entry = manifest(_manifest_path(plan)).flatMap(_.resources.find(_.id == resolved.resource.id))
      entry.exists { value =>
        value.path == relative && value.sha256 == _sha256(output) && value.receipt.exists { receipt =>
          val captured = capture(plan)
          _receipt_current(receipt, captured) &&
            (if (resolved.resource.build == "presentation") CozyMediaPresentation.currentArtifactEvidence(plan, resolved, value) else value.artifacts.isEmpty)
        }
      }
    } catch {
      case NonFatal(_) => false
    }

  def requireCurrent(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    if (!current(plan, resolved))
      _invalid(s"Media resource lacks current cozy.media.receipt.v2 evidence: ${resolved.resource.id}")
  }

  def requireCurrent(plan: CozyMedia.Plan, resources: Vector[CozyMedia.ResolvedResource]): Unit = {
    val stale = resources.filterNot(resolved => current(plan, resolved)).map(_.resource.id).sorted
    if (stale.nonEmpty)
      _invalid("Media publication receipt pre-commit validation failed: " + stale.mkString(", "))
  }

  def requireCurrent(descriptorFile: Path, resourceId: String, manifestFile: Path, profile: Option[String] = None): Unit = {
    val plan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptorFile, profile = profile))
    val resource = plan.resources.find(_.resource.id == resourceId).getOrElse(
      _invalid(s"Media receipt resource is not declared: $resourceId")
    )
    if (_manifest_path(plan) != manifestFile.toAbsolutePath.normalize())
      _invalid("Media receipt manifest must be the descriptor target/cozy-media/manifest.json")
    requireCurrent(plan, resource)
  }

  def requirePreparedInputSet(publication: CozyMedia.PreparedPublication): Unit = {
    val currentinputset = capture(
      CozyMedia.resolvePlan(CozyMedia.CommandConfig(
        publication.descriptorFile,
        target = publication.target,
        profile = Some(publication.profile)
      ))
    ).inputSetSha256
    if (currentinputset != publication.inputSetSha256)
      _invalid(s"Prepared media publication inputs have changed: ${publication.resource.id}")
  }

  def write(
    plan: CozyMedia.Plan,
    selected: Vector[CozyMedia.ResolvedResource],
    captured: Captured,
    target: Option[String]
  ): Unit = commit(Vector.empty, prepare(plan, selected, captured, target))

  /** Prepares the receipt without creating its parent or modifying its target. */
  def prepare(
    plan: CozyMedia.Plan,
    selected: Vector[CozyMedia.ResolvedResource],
    captured: Captured,
    target: Option[String]
  ): PreparedDocument = {
    val receiptvalue = receipt(captured, target, plan.effectiveProfile.map(_.id))
    val selectedids = selected.map(_.resource.id).toSet
    if (selectedids.size != selected.size)
      _invalid("Media selected resources must be unique")
    val existing = manifest(_manifest_path(plan)).getOrElse(Manifest(plan.descriptor.knowledge.id, Vector.empty))
    if (existing.knowledge != plan.descriptor.knowledge.id)
      _invalid("Media build manifest knowledge does not match descriptor")
    _validate_prebuilt_adoption(plan, selected, existing)
    val replacements = selected.map { resolved =>
      val output = _output(resolved).getOrElse(
        _invalid(s"Media resource has no receipt output: ${resolved.resource.id}")
      )
      if (!Files.isRegularFile(output))
        _invalid(s"Media receipt output must be a current regular file: $output")
      ManifestEntry(
        resolved.resource.id,
        _relative_from_path(plan.descriptorRoot, output, "Media receipt output"),
        _sha256(output),
        Some(receiptvalue),
        if (resolved.resource.build == "presentation") CozyMediaPresentation.artifacts(plan, resolved) else Vector.empty
      )
    }
    val retained = if (target.isDefined) existing.resources.filterNot(x => selectedids.contains(x.id)) else Vector.empty
    val merged = (retained ++ replacements).sortBy(_.id)
    val ids = merged.map(_.id)
    if (ids.distinct.size != ids.size)
      _invalid("Media build manifest resource ids must be unique")
    val json = Json.obj(
      "schema" -> Json.fromString(_schema),
      "knowledge" -> Json.fromString(plan.descriptor.knowledge.id),
      "resources" -> Json.fromValues(merged.map(_entry_json))
    )
    PreparedDocument(_manifest_path(plan), (json.spaces2 + "\n").getBytes(StandardCharsets.UTF_8))
  }

  /**
   * Makes prepared review states visible before the receipt, which is the sole
   * acceptance visibility record.  This is rollback-safe for in-process errors;
   * it deliberately makes no process-crash atomicity claim across directories.
   */
  private[cozy] def commit(reviewStates: Vector[PreparedDocument], receipt: PreparedDocument): Unit = {
    val documents = reviewStates ++ Vector(receipt)
    if (documents.isEmpty || documents.last.path != receipt.path) _invalid("Media acceptance requires a final receipt document")
    val paths = documents.map(value => _target_path(value.path))
    if (paths.distinct.size != paths.size) _invalid("Media acceptance targets must be unique and non-aliased")
    paths.foreach { path =>
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !_direct_regular_file(path))
        _invalid(s"Media acceptance target must be a direct regular file: $path")
    }
    paths.combinations(2).foreach { pair =>
      if (Files.exists(pair.head, LinkOption.NOFOLLOW_LINKS) && Files.exists(pair(1), LinkOption.NOFOLLOW_LINKS))
        try {
          if (Files.isSameFile(pair.head, pair(1))) _invalid("Media acceptance targets must be unique and non-aliased")
        } catch {
          case _: java.io.IOException => _invalid("Media acceptance target alias check failed")
        }
    }
    val staged = scala.collection.mutable.ArrayBuffer.empty[(Path, Path)]
    val backups = scala.collection.mutable.ArrayBuffer.empty[(Path, Option[Path])]
    val installed = scala.collection.mutable.ArrayBuffer.empty[Path]
    try {
      documents.zip(paths).foreach { case (document, targetpath) =>
        val parent = Option(targetpath.getParent).getOrElse(_invalid(s"Media acceptance target requires parent: $targetpath"))
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".cozy-media-accept-", ".tmp")
        Files.write(temporary, document.bytes)
        staged += targetpath -> temporary
      }
      staged.foreach { case (targetpath, temporary) =>
        val backup = if (Files.exists(targetpath, LinkOption.NOFOLLOW_LINKS)) {
          if (!_direct_regular_file(targetpath)) _invalid(s"Media acceptance target must be a direct regular file: $targetpath")
          val parent = targetpath.getParent
          val saved = Files.createTempFile(parent, ".cozy-media-before-", ".tmp")
          Files.copy(targetpath, saved, StandardCopyOption.REPLACE_EXISTING)
          Some(saved)
        } else None
        backups += targetpath -> backup
        _replace(temporary, targetpath)
        installed += targetpath
      }
    } catch {
      case error: Throwable =>
        installed.reverse.foreach { targetpath =>
          backups.find(_._1 == targetpath).foreach {
            case (_, Some(saved)) => _replace(saved, targetpath)
            case (_, None) => Files.deleteIfExists(targetpath)
          }
        }
        throw error
    } finally {
      staged.foreach { case (_, temporary) => try Files.deleteIfExists(temporary) catch { case NonFatal(_) => () } }
      backups.foreach { case (_, saved) => saved.foreach(path => try Files.deleteIfExists(path) catch { case NonFatal(_) => () }) }
    }
  }

  def prebuiltAcceptanceAllowed(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    val existing = manifest(_manifest_path(plan)).getOrElse(Manifest(plan.descriptor.knowledge.id, Vector.empty))
    _validate_prebuilt_adoption(plan, Vector(resolved), existing)
  }

  private def _validate_prebuilt_adoption(
    plan: CozyMedia.Plan,
    selected: Vector[CozyMedia.ResolvedResource],
    existing: Manifest
  ): Unit = {
    selected.filter(_.resource.build == "prebuilt").foreach { resolved =>
      val output = _output(resolved).getOrElse(_invalid(s"Media prebuilt resource has no output: ${resolved.resource.id}"))
      if (!Files.isRegularFile(output))
        _invalid(s"Media prebuilt output must be a current regular file: $output")
      existing.resources.find(_.id == resolved.resource.id).foreach { previous =>
        if (previous.sha256 == _sha256(output) && !current(plan, resolved))
          _invalid(s"Media prebuilt resource is stale and its output was not externally refreshed: ${resolved.resource.id}")
      }
    }
  }

  private def _manifest(path: Path): Manifest = {
    val root = parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      _ => _invalid("Media build manifest must contain valid JSON"),
      _.asObject.getOrElse(_invalid("Media build manifest must be a JSON object"))
    )
    _exact_keys(root, Set("schema", "knowledge", "resources"), "Media build manifest")
    _exact_string(root, "schema", "Media build manifest") match {
      case `_schema` => ()
      case _ => _invalid(s"Media build manifest schema must be exactly ${_schema}")
    }
    val knowledge = _exact_string(root, "knowledge", "Media build manifest")
    val resources = root("resources").flatMap(_.asArray).getOrElse(
      _invalid("Media build manifest resources must be an array")
    ).toVector.map(_entry)
    val ids = resources.map(_.id)
    if (ids.distinct.size != ids.size)
      _invalid("Media build manifest resource ids must be unique")
    Manifest(knowledge, resources)
  }

  private def _entry(value: Json): ManifestEntry = {
    val objectvalue = value.asObject.getOrElse(_invalid("Media build manifest resource must be an object"))
    val keys = objectvalue.keys.toSet
    if (!keys.subsetOf(Set("id", "path", "sha256", "receipt", "artifacts")) || !Set("id", "path", "sha256").subsetOf(keys))
      _invalid("Media build manifest resource requires id, path, sha256 and optional receipt/artifacts only")
    val id = _require_identity(_exact_string(objectvalue, "id", "Media build manifest resource"), "Media build manifest resource id")
    val path = _receipt_relative_path(_exact_string(objectvalue, "path", "Media build manifest resource"), "Media build manifest resource path")
    val sha256 = _sha256_string(_exact_string(objectvalue, "sha256", "Media build manifest resource"))
    val receipt = objectvalue("receipt").map(_receipt)
    val artifacts = objectvalue("artifacts").map(_.asArray.getOrElse(_invalid("Media build manifest artifacts must be an array")).toVector.map(_artifact)).getOrElse(Vector.empty)
    if (artifacts.map(_.id).distinct.size != artifacts.size)
      _invalid("Media build manifest artifact ids must be unique")
    ManifestEntry(id, path, sha256, receipt, artifacts)
  }

  private def _artifact(value: Json): Artifact = {
    val objectvalue = value.asObject.getOrElse(_invalid("Media build manifest artifact must be an object"))
    val keys = objectvalue.keys.toSet
    if (!keys.subsetOf(Set("id", "role", "path", "sha256", "sourceSha256")) || !Set("id", "role", "path", "sha256").subsetOf(keys))
      _invalid("Media build manifest artifact requires id, role, path, sha256 and optional sourceSha256 only")
    Artifact(
      _require_identity(_exact_string(objectvalue, "id", "Media build manifest artifact"), "Media build manifest artifact id"),
      _require_identity(_exact_string(objectvalue, "role", "Media build manifest artifact"), "Media build manifest artifact role"),
      _receipt_relative_path(_exact_string(objectvalue, "path", "Media build manifest artifact"), "Media build manifest artifact path"),
      _sha256_string(_exact_string(objectvalue, "sha256", "Media build manifest artifact")),
      objectvalue("sourceSha256").map(value => _sha256_string(value.asString.getOrElse(_invalid("Media build manifest artifact sourceSha256 must be a string"))))
    )
  }

  private def _receipt(value: Json): ResourceReceipt = {
    val objectvalue = value.asObject.getOrElse(_invalid("Media receipt must be an object"))
    _exact_keys(objectvalue, Set("schema", "inputSetSha256", "inputs", "producer", "operation", "acceptedAt", "verification"), "Media receipt")
    if (_exact_string(objectvalue, "schema", "Media receipt") != _receipt_schema)
      _invalid(s"Media receipt schema must be exactly ${_receipt_schema}")
    val inputset = _sha256_string(_exact_string(objectvalue, "inputSetSha256", "Media receipt"))
    val inputs = objectvalue("inputs").flatMap(_.asArray).getOrElse(_invalid("Media receipt inputs must be an array")).toVector.map(_evidence_from_json)
    if (inputs.map(_.id) != inputs.map(_.id).sorted)
      _invalid("Media receipt inputs must be sorted by id")
    val ids = inputs.map(_.id)
    if (ids.distinct.size != ids.size)
      _invalid("Media receipt input ids must be unique")
    val producer = _producer_from_json(objectvalue("producer").getOrElse(_invalid("Media receipt producer is required")))
    val operation = objectvalue("operation").flatMap(_.asObject).getOrElse(_invalid("Media receipt operation must be an object"))
    val operationkeys = operation.keys.toSet
    if (!operationkeys.subsetOf(Set("name", "target", "profile")) || !operationkeys.contains("name"))
      _invalid("Media receipt operation permits name, target, and profile only")
    if (_exact_string(operation, "name", "Media receipt operation") != "cozy media build")
      _invalid("Media receipt operation.name must be cozy media build")
    val target = _optional_identity(operation, "target", "Media receipt operation target")
    val profile = _optional_identity(operation, "profile", "Media receipt operation profile")
    val acceptedat = _exact_string(objectvalue, "acceptedAt", "Media receipt")
    if (!acceptedat.endsWith("Z"))
      _invalid("Media receipt acceptedAt must be UTC ISO-8601")
    try Instant.parse(acceptedat)
    catch { case NonFatal(_) => _invalid("Media receipt acceptedAt must be UTC ISO-8601") }
    val verification = objectvalue("verification").flatMap(_.asObject).getOrElse(_invalid("Media receipt verification must be an object"))
    _exact_keys(verification, Set("status", "findings"), "Media receipt verification")
    if (_exact_string(verification, "status", "Media receipt verification") != "valid" || verification("findings").flatMap(_.asArray).forall(_.nonEmpty))
      _invalid("Media receipt verification must be {status:valid,findings:[]}")
    ResourceReceipt(inputset, inputs, producer, target, profile, acceptedat)
  }

  private def _receipt_current(receipt: ResourceReceipt, captured: Captured): Boolean =
    receipt.inputSetSha256 == captured.inputSetSha256 &&
      receipt.inputs == captured.inputs &&
      receipt.producer == captured.producer

  private def _producer(config: Config): Producer = {
    val configured = config.producer.map { value =>
      val profile = _require_identity(value.profile, "Media receipt producer profile")
      val renderer = Renderer(
        _require_identity(value.renderer.name, "Media receipt renderer name"),
        _require_identity(value.renderer.version, "Media receipt renderer version")
      )
      profile -> renderer
    }
    Producer(org.simplemodeling.cozy.BuildInfo.version, configured.map(_._1), configured.map(_._2))
  }

  private def _producer_json(value: Producer): Json =
    Json.fromFields(Vector("cozyVersion" -> Json.fromString(value.cozyVersion)) ++
      value.profile.map(x => "profile" -> Json.fromString(x)).toVector ++
      value.renderer.map(x => "renderer" -> Json.obj("name" -> Json.fromString(x.name), "version" -> Json.fromString(x.version))).toVector)

  private def _producer_from_json(value: Json): Producer = {
    val objectvalue = value.asObject.getOrElse(_invalid("Media receipt producer must be an object"))
    val keys = objectvalue.keys.toSet
    if (!keys.subsetOf(Set("cozyVersion", "profile", "renderer")) || !keys.contains("cozyVersion") || keys.contains("profile") != keys.contains("renderer"))
      _invalid("Media receipt producer requires cozyVersion and optional profile with renderer only")
    val cozyversion = _require_identity(_exact_string(objectvalue, "cozyVersion", "Media receipt producer"), "Media receipt cozyVersion")
    val profile = _optional_identity(objectvalue, "profile", "Media receipt producer profile")
    val renderer = objectvalue("renderer").map { item =>
      val rendererobject = item.asObject.getOrElse(_invalid("Media receipt renderer must be an object"))
      _exact_keys(rendererobject, Set("name", "version"), "Media receipt renderer")
      Renderer(
        _require_identity(_exact_string(rendererobject, "name", "Media receipt renderer"), "Media receipt renderer name"),
        _require_identity(_exact_string(rendererobject, "version", "Media receipt renderer"), "Media receipt renderer version")
      )
    }
    Producer(cozyversion, profile, renderer)
  }

  private def _entry_json(value: ManifestEntry): Json =
    Json.fromFields(Vector(
      "id" -> Json.fromString(value.id),
      "path" -> Json.fromString(value.path),
      "sha256" -> Json.fromString(value.sha256)
    ) ++ value.receipt.map(x => "receipt" -> _receipt_json(x)).toVector ++
      (if (value.artifacts.nonEmpty) Vector("artifacts" -> Json.fromValues(value.artifacts.map(_artifact_json))) else Vector.empty))

  private def _artifact_json(value: Artifact): Json =
    Json.fromFields(Vector(
      "id" -> Json.fromString(value.id),
      "role" -> Json.fromString(value.role),
      "path" -> Json.fromString(value.path),
      "sha256" -> Json.fromString(value.sha256)
    ) ++ value.sourceSha256.map(x => "sourceSha256" -> Json.fromString(x)).toVector)

  private def _receipt_json(value: ResourceReceipt): Json =
    Json.obj(
      "schema" -> Json.fromString(_receipt_schema),
      "inputSetSha256" -> Json.fromString(value.inputSetSha256),
      "inputs" -> Json.fromValues(value.inputs.map(_evidence_json)),
      "producer" -> _producer_json(value.producer),
      "operation" -> Json.fromFields(Vector("name" -> Json.fromString("cozy media build")) ++
        value.operationTarget.map(x => "target" -> Json.fromString(x)).toVector ++
        value.operationProfile.map(x => "profile" -> Json.fromString(x)).toVector),
      "acceptedAt" -> Json.fromString(value.acceptedAt),
      "verification" -> Json.obj("status" -> Json.fromString("valid"), "findings" -> Json.arr())
    )

  private def _evidence(id: String, role: String, path: Path, root: Path, normalization: String, direct: Boolean): Evidence = {
    if (!(if (direct) _direct_regular_file(path) else Files.isRegularFile(path)))
      _invalid(s"Media receipt input must be a current ${if (direct) "direct non-symlink " else ""}regular file: $path")
    val relative = _relative_from_path(root, path, "Media receipt input")
    val hash = normalization match {
      case "bytes" => _sha256(path)
      case "structured-document" => _structured_document_sha256(path)
      case _ => _invalid(s"Unsupported media receipt normalization: $normalization")
    }
    Evidence(id, role, relative, normalization, hash)
  }

  private def _evidence_json(value: Evidence): Json =
    Json.obj(
      "id" -> Json.fromString(value.id),
      "role" -> Json.fromString(value.role),
      "path" -> Json.fromString(value.path),
      "normalization" -> Json.fromString(value.normalization),
      "sha256" -> Json.fromString(value.sha256)
    )

  private def _evidence_from_json(value: Json): Evidence = {
    val objectvalue = value.asObject.getOrElse(_invalid("Media receipt input evidence must be an object"))
    _exact_keys(objectvalue, Set("id", "role", "path", "normalization", "sha256"), "Media receipt input evidence")
    val id = _require_identity(_exact_string(objectvalue, "id", "Media receipt input evidence"), "Media receipt input evidence id")
    val role = _require_identity(_exact_string(objectvalue, "role", "Media receipt input evidence"), "Media receipt input evidence role")
    val path = _receipt_relative_path(_exact_string(objectvalue, "path", "Media receipt input evidence"), "Media receipt input evidence path")
    val normalization = _exact_string(objectvalue, "normalization", "Media receipt input evidence") match {
      case "bytes" | "structured-document" => _exact_string(objectvalue, "normalization", "Media receipt input evidence")
      case _ => _invalid("Media receipt input evidence normalization must be bytes or structured-document")
    }
    Evidence(id, role, path, normalization, _sha256_string(_exact_string(objectvalue, "sha256", "Media receipt input evidence")))
  }

  private def _structured_document_sha256(path: Path): String = {
    val structured = try StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take catch {
      case NonFatal(_) => _invalid(s"Media receipt structured-document input is invalid: $path")
    }
    val json = _canonical(structured)
    _sha256_bytes(json.noSpaces.getBytes(StandardCharsets.UTF_8))
  }

  private def _canonical(value: Json): Json =
    value.asObject.map(objectvalue => Json.fromFields(objectvalue.toIterable.toVector.sortBy(_._1).map { case (key, item) => key -> _canonical(item) })).orElse(
      value.asArray.map(array => Json.fromValues(array.map(_canonical)))
    ).getOrElse(value)

  private def _output(resolved: CozyMedia.ResolvedResource): Option[Path] =
    resolved.output.orElse(if (resolved.resource.build == "prebuilt") resolved.source else None)

  private def _manifest_path(plan: CozyMedia.Plan): Path =
    plan.descriptorRoot.resolve("target/cozy-media/manifest.json").toAbsolutePath.normalize()

  private def _target_path(path: Path): Path = {
    val target = Option(path).map(_.toAbsolutePath.normalize()).getOrElse(_invalid("Media acceptance target must be defined"))
    var parent = target.getParent
    while (parent != null) {
      if (Files.isSymbolicLink(parent)) _invalid(s"Media acceptance target parent must not be a symlink: $parent")
      parent = parent.getParent
    }
    target
  }

  private def _replace(source: Path, target: Path): Unit =
    try Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    catch {
      case _: AtomicMoveNotSupportedException =>
        _invalid(s"cozy.media.receipt.v2 acceptance requires same-parent ATOMIC_MOVE: $target")
    }

  private def _relative_path(root: Path, value: String, label: String): Path = {
    val exact = _require_identity(value, label)
    val path = try Path.of(exact) catch { case NonFatal(_) => _invalid(s"$label is not a valid path") }
    if (path.isAbsolute || exact.matches("[A-Za-z]:[\\\\/].*") || exact.startsWith("\\\\"))
      _invalid(s"$label must be relative: $value")
    if (exact.exists(x => x == '*' || x == '?' || x == '[' || x == ']' || x == '{' || x == '}'))
      _invalid(s"$label must not contain a glob pattern: $value")
    root.resolve(path).normalize()
  }

  private def _receipt_relative_path(value: String, label: String): String = {
    val path = try Path.of(value) catch { case NonFatal(_) => _invalid(s"$label is not a valid path") }
    if (path.isAbsolute || value.matches("[A-Za-z]:[\\\\/].*") || value.startsWith("\\\\"))
      _invalid(s"$label must be relative: $value")
    value.replace('\\', '/')
  }

  private def _relative_from_path(root: Path, path: Path, label: String): String = {
    val normalizedroot = root.toAbsolutePath.normalize()
    val normalizedpath = path.toAbsolutePath.normalize()
    normalizedroot.relativize(normalizedpath).toString.replace('\\', '/')
  }

  private def _automatic_id(suffix: String): String = _reserved_prefix + suffix

  private def _require_identity(value: String, label: String): String =
    if (value == null || value.isEmpty || value != value.trim)
      _invalid(s"$label must be a non-empty exact trimmed string")
    else value

  private def _direct_regular_file(path: Path): Boolean =
    path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  private def _sha256(path: Path): String = _sha256_bytes(Files.readAllBytes(path))

  private def _sha256_bytes(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(x => f"${x & 0xff}%02x").mkString

  private def _sha256_string(value: String): String =
    if (value.matches("[0-9a-f]{64}")) value
    else _invalid(s"Media SHA-256 must be 64 lowercase hexadecimal characters: $value")

  private def _exact_keys(value: io.circe.JsonObject, expected: Set[String], label: String): Unit =
    if (value.keys.toSet != expected)
      _invalid(s"$label requires exactly: ${expected.toVector.sorted.mkString(", ")}")

  private def _require_exact_keys(c: HCursor, expected: Set[String], label: String): Decoder.Result[Unit] =
    c.value.asObject match {
      case Some(value) if value.keys.toSet == expected => Right(())
      case Some(_) => Left(io.circe.DecodingFailure(s"$label requires exactly: ${expected.toVector.sorted.mkString(", ")}", c.history))
      case None => Left(io.circe.DecodingFailure(s"$label must be an object", c.history))
    }

  private def _require_allowed_keys(c: HCursor, allowed: Set[String], label: String): Decoder.Result[Unit] =
    c.value.asObject match {
      case Some(value) if value.keys.toSet.subsetOf(allowed) => Right(())
      case Some(_) => Left(io.circe.DecodingFailure(s"$label permits only: ${allowed.toVector.sorted.mkString(", ")}", c.history))
      case None => Left(io.circe.DecodingFailure(s"$label must be an object", c.history))
    }

  private def _exact_string(value: io.circe.JsonObject, field: String, label: String): String =
    value(field).flatMap(_.asString).filter(x => x.nonEmpty && x == x.trim).getOrElse(
      _invalid(s"$label.$field must be a non-empty exact string")
    )

  private def _optional_identity(value: io.circe.JsonObject, field: String, label: String): Option[String] =
    value(field).map(_.asString.filter(x => x.nonEmpty && x == x.trim).getOrElse(
      _invalid(s"$label must be a non-empty exact string")
    ))

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
