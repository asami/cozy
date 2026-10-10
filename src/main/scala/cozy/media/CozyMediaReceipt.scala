package cozy.media

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.nio.file.attribute.FileTime
import java.time.Instant
import scala.util.control.NonFatal
import scala.collection.JavaConverters._
import cozy.generation.CozyFileUpdatePolicy
import cozy.video.CozyVideo

/*
 * @since   Aug. 25, 2026
 *  version Sep. 22, 2026
 * @version Oct.  9, 2026
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
    normalization: String
  )

  final case class Producer(
    cozyVersion: String,
    profile: Option[String],
    renderer: Option[Renderer]
  )

  final case class Captured(
    inputs: Vector[Evidence],
    producer: Producer
  )

  final case class ResourceReceipt(
    inputs: Vector[Evidence],
    producer: Producer,
    operationTarget: Option[String],
    operationProfile: Option[String],
    acceptedAt: String
  )

  final case class ManifestEntry(
    id: String,
    path: String,
    receipt: Option[ResourceReceipt],
    artifacts: Vector[Artifact] = Vector.empty
  )

  final case class Artifact(
    id: String,
    role: String,
    path: String
  )

  final case class Manifest(knowledge: String, resources: Vector[ManifestEntry])
  /** A fully validated acceptance document which has not yet been made visible. */
  final case class PreparedDocument(path: Path, bytes: Array[Byte])
  /** Candidate receipt manifest and its final acceptance document. */
  final case class PreparedAcceptance(manifest: Manifest, document: PreparedDocument)

  private val _schema = "cozy.media.v1"
  private val _receipt_schema = "cozy.media.receipt.v2"
  private val _reserved_prefix = "cozy:"
  private val _site_config_evidence_id = _reserved_prefix + "site-config"
  private val _site_document_route_evidence_id = _reserved_prefix + "site-document-route"
  private val _pdf_header = "%PDF-".getBytes(StandardCharsets.US_ASCII)
  private val _png_header = Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

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
    val site = CozyMedia.requireSiteContext(descriptor).toVector.flatMap { context =>
      Vector(
        _site_evidence(_site_config_evidence_id, "site-configuration", context.config, context.configRoute),
        _site_evidence(_site_document_route_evidence_id, "site-document-route", context.source, context.documentRoute)
      )
    }
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
    val inputs = (automatic ++ site ++ explicit).sortBy(_.id)
    val duplicates = inputs.groupBy(_.id).collect { case (id, values) if values.size > 1 => id }.toVector.sorted
    duplicates.headOption.foreach(id => _invalid(s"Media receipt input ids must be unique: $id"))
    val producer = _producer(config)
    Captured(inputs, producer)
  }

  def receipt(captured: Captured, target: Option[String], profile: Option[String]): ResourceReceipt =
    ResourceReceipt(
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
    _freshness_failure(plan, resolved).isEmpty

  private[cozy] def videoAdditionalInputs(plan: CozyMedia.Plan): Vector[Path] =
    plan.descriptor.receipt.toVector.flatMap(_.inputs).map(input =>
      _relative_path(plan.descriptorRoot, input.path, s"Media receipt input ${input.id} path"))

  private[cozy] def requireVideoOutput(resolved: CozyMedia.ResolvedResource): Unit = {
    val projectfile = resolved.project.getOrElse(_invalid(s"Missing Video project for ${resolved.resource.id}"))
    val project = StructuredDocumentLoader.loadDocument[CozyVideo.VideoProject](InputSource(projectfile.toFile)).take
    val output = projectfile.getParent.resolve(project.output.getOrElse("build/final.mp4")).toAbsolutePath.normalize()
    if (!resolved.output.exists(_.toAbsolutePath.normalize() == output))
      _invalid(s"Media output for ${resolved.resource.id} must match resolved Video output: $output")
  }

  private def _producer_paths(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Vector[Path] =
    if (Set("copy", "prebuilt").contains(resolved.resource.build)) {
      val production = if (resolved.resource.build == "prebuilt" && plan.descriptor.articleMedia.nonEmpty)
        resolved.resource.articleMedia.filter(_.role == "video").toVector.flatMap(_.production)
          .map(value => _relative_path(plan.descriptorRoot, value, "Media site-video production"))
      else Vector.empty
      resolved.source.toVector ++ production
    } else _declared_paths(plan, Vector(resolved), generated = true)

  private def _freshness_failure(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Option[String] =
    try {
      val output = _output(resolved).getOrElse(return Some("output is not declared"))
      if (!Files.isRegularFile(output)) return Some(s"output must be a regular file: $output")
      _validate_output(plan, resolved, output)
      if (resolved.resource.build == "video-project") {
        requireVideoOutput(resolved)
        return CozyVideo.mediaCurrentnessFailure(resolved.project.get, videoAdditionalInputs(plan))
      }
      if (!_generation_outputs_current(plan, resolved))
        return Some(s"presentation companion outputs are missing or newer than their review manifest: $output")
      val inputs = _producer_paths(plan, resolved)
      if (inputs.isEmpty) return Some(s"producer source is not declared: $output")
      inputs.find(path => !Files.isRegularFile(path)).foreach(path => return Some(s"producer input must be a regular file: $path"))
      if (resolved.resource.build == "prebuilt") {
        val entry = manifest(_manifest_path(plan)).flatMap(_.resources.find(_.id == resolved.resource.id))
        val accepted = entry.flatMap(_.receipt).getOrElse(return Some(s"accepted receipt is missing: ${_manifest_path(plan)}"))
        val time = FileTime.from(Instant.parse(accepted.acceptedAt))
        (inputs :+ output).find(path => Files.getLastModifiedTime(path).compareTo(time) > 0)
          .map(path => s"prebuilt input/output is newer than acceptedAt: $path")
      } else {
        import CozyFileUpdatePolicy._
        def _observe_(path: Path): FileObservation =
          FileObservation(path.toString, FileState.Valid(Some(Files.getLastModifiedTime(path))))
        if (evaluate(Request(Intent.Generation, Some(resolved.resource.build), inputs.map(_observe_),
            _observe_(output), force = false)).isInstanceOf[Decision.Reuse]) None
        else Some(inputs.find(path => Files.getLastModifiedTime(path).compareTo(Files.getLastModifiedTime(output)) > 0)
          .map(path => s"producer input is newer than output $output: $path").getOrElse(s"producer output requires generation: $output"))
      }
    } catch { case NonFatal(error) => Some(s"${_manifest_path(plan)}: ${error.getMessage}") }

  /** Current-command observations, never serialized into receipt evidence. */
  def observeInputs(plan: CozyMedia.Plan): Vector[(Path, FileTime)] =
    _declared_paths(plan, plan.resources).map { path =>
      if (!Files.isRegularFile(path)) _invalid(s"Media required input must be a regular file: $path")
      path -> Files.getLastModifiedTime(path)
    }

  private def _declared_paths(plan: CozyMedia.Plan, resources: Vector[CozyMedia.ResolvedResource], generated: Boolean = false): Vector[Path] = {
    val explicit = plan.descriptor.receipt.toVector.flatMap(_.inputs).map(input =>
      _relative_path(plan.descriptorRoot, input.path, s"Media receipt input ${input.id} path"))
    (Vector(plan.descriptorFile, plan.knowledgeSource) ++
      resources.flatMap(resource => resource.source.toVector ++ resource.project.toVector ++ _dependency_paths(plan, resource, generated)) ++
      explicit ++ plan.effectiveProfile.toVector.flatMap(_.sourcePath) ++
      CozyMedia.requireSiteContext(plan).toVector.flatMap(site => Vector(site.config, site.source)))
      .distinct.sortBy(_.toString)
  }

  private def _dependency_paths(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, generated: Boolean): Vector[Path] = {
    val resource = resolved.resource
    val ids = resource.articlePdf.toVector.map(_.infographic) ++
      resource.presentation.toVector.flatMap(value => Vector(value.articlePdf, value.infographic)) ++
      resource.summarySlidesPdf.toVector.flatMap(value => Vector(value.articlePdf, value.infographic))
    val dependencies = ids.distinct.flatMap(id => plan.resources.find(_.resource.id == id)).flatMap(value =>
      value.source.toVector ++ value.project.toVector ++ (if (generated) value.output.toVector else Vector.empty))
    val profiles = resource.presentation.toVector.map(_.profile) ++ resource.summarySlidesPdf.toVector.map(_.profile)
    val templates = profiles.distinct.flatMap(id => plan.descriptor.profiles.get(id).flatMap(_.presentation))
      .map(value => _relative_path(plan.descriptorRoot, value.template, "Media template dependency"))
    val configured = resource.presentation.toVector.flatMap(value => value.catalog.toVector ++ value.binding.toVector) ++
      resource.summarySlidesPdf.toVector.flatMap(value => value.catalog.toVector ++ value.binding.toVector)
    val video = if (resource.build == "video-project") resolved.project.toVector.flatMap(CozyVideo.mediaProducerInputs) else Vector.empty
    dependencies ++ templates ++ configured.map(value => _relative_path(plan.descriptorRoot, value, "Media declared dependency")) ++ video
  }

  /** A presentation review manifest is written last; newer/missing companion
    * outputs select regeneration without reconstructing content identities. */
  private def _generation_outputs_current(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Boolean =
    resolved.resource.presentation.forall { configuration =>
      val review = _relative_path(plan.descriptorRoot, configuration.reviewManifest, "Presentation review manifest")
      val renderer = _relative_path(plan.descriptorRoot, configuration.rendererManifest, "Presentation renderer manifest")
      val directory = _relative_path(plan.descriptorRoot, configuration.slideImages, "Presentation slide images")
      if (!Files.isRegularFile(review) || !Files.isRegularFile(renderer) || !Files.isDirectory(directory)) false
      else {
        val schema = io.circe.parser.parse(Files.readString(renderer)).toOption.flatMap(_.hcursor.get[String]("schema").toOption)
        val stream = Files.list(directory)
        val images = try stream.iterator().asScala.toVector finally stream.close()
        val outputs = images ++ Vector(renderer, _relative_path(plan.descriptorRoot, configuration.montage, "Presentation montage")) ++ resolved.output.toVector
        schema.exists(Set("cozy.presentation.render.v1", "cozy.presentation.render.v2")) && images.nonEmpty &&
          outputs.forall(path => Files.isRegularFile(path) && Files.getLastModifiedTime(path).compareTo(Files.getLastModifiedTime(review)) <= 0)
      }
    }

  /** Accepted identity/path evidence, independent of package-wide race observations. */
  def evidenceCurrent(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Boolean =
    _evidence_failure(plan, resolved).isEmpty

  private def _entry_evidence_failure(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, entry: ManifestEntry): Option[String] = {
    val output = _output(resolved).getOrElse(return Some("output is not declared"))
    if (!Files.isRegularFile(output)) return Some(s"output must be a regular file: $output")
    _validate_output(plan, resolved, output)
    if (entry.id != resolved.resource.id || entry.path != _relative_from_path(plan.descriptorRoot, output, "Media receipt output"))
      return Some(s"accepted resource id/output path differs from declaration: $output")
    val accepted = entry.receipt.getOrElse(return Some("accepted receipt is missing"))
    val mappings = Vector("source" -> resolved.source, "project" -> resolved.project).flatMap { case (role, path) =>
      path.map(value => Evidence(_automatic_id(s"resource:${resolved.resource.id}:$role"), role,
        _relative_from_path(plan.descriptorRoot, value, "Media receipt resource input"), "bytes"))
    }
    mappings.find(value => !accepted.inputs.contains(value)).foreach(value =>
      return Some(s"accepted ${value.role} path differs from declaration: ${plan.descriptorRoot.resolve(value.path)}"))
    val sites = accepted.inputs.filter(value => Set(_site_config_evidence_id, _site_document_route_evidence_id).contains(value.id))
    if (sites.nonEmpty) {
      val context = plan.siteContext.getOrElse(return Some("paired site context is required by the accepted receipt"))
      val expected = Vector(
        Evidence(_site_config_evidence_id, "site-configuration", context.configRoute, "bytes"),
        Evidence(_site_document_route_evidence_id, "site-document-route", context.documentRoute, "bytes"))
      if (!expected.forall(accepted.inputs.contains)) return Some(s"accepted site path mapping differs: ${context.config}, ${context.source}")
    }
    if (resolved.resource.build == "presentation") {
      if (!CozyMediaPresentation.currentArtifactEvidence(plan, resolved, entry)) return Some(s"presentation artifact evidence differs: $output")
    } else if (entry.artifacts.nonEmpty) return Some(s"resource must not have presentation artifacts: $output")
    None
  }

  private def _evidence_failure(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Option[String] =
    try {
      val accepted = manifest(_manifest_path(plan)).getOrElse(return Some("manifest is missing or is not a direct regular file"))
      if (accepted.knowledge != plan.descriptor.knowledge.id) return Some("manifest knowledge differs from declaration")
      val entry = accepted.resources.find(_.id == resolved.resource.id).getOrElse(return Some("accepted resource entry is missing"))
      _entry_evidence_failure(plan, resolved, entry)
    } catch { case NonFatal(error) => Some(error.getMessage) }

  def currentnessFailure(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Option[String] =
    _freshness_failure(plan, resolved).orElse(_evidence_failure(plan, resolved)).map { reason =>
      s"Media receipt currentness failed for resource ${resolved.resource.id}, producer ${resolved.resource.build}, " +
        s"manifest ${_manifest_path(plan)}: $reason; next operation: cozy media build ${plan.descriptorFile} --target ${resolved.resource.id}"
    }

  def requireCurrent(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit =
    currentnessFailure(plan, resolved).foreach(_invalid)

  def requireCurrent(plan: CozyMedia.Plan, resources: Vector[CozyMedia.ResolvedResource]): Unit = {
    val failures = resources.sortBy(_.resource.id).flatMap(currentnessFailure(plan, _))
    if (failures.nonEmpty) _invalid("Media publication receipt pre-commit validation failed: " + failures.mkString("; "))
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
  ): PreparedDocument =
    prepareAcceptance(plan, selected, captured, target).document

  /** Prepares the candidate manifest and its receipt without modifying either target. */
  def prepareAcceptance(
    plan: CozyMedia.Plan,
    selected: Vector[CozyMedia.ResolvedResource],
    captured: Captured,
    target: Option[String]
  ): PreparedAcceptance = {
    val receiptvalue = receipt(captured, target, plan.effectiveProfile.map(_.id))
    val selectedids = selected.map(_.resource.id).toSet
    val declaredids = plan.resources.map(_.resource.id).toSet
    if (selectedids.size != selected.size)
      _invalid("Media selected resources must be unique")
    val existing = manifest(_manifest_path(plan)).getOrElse(Manifest(plan.descriptor.knowledge.id, Vector.empty))
    if (existing.knowledge != plan.descriptor.knowledge.id)
      _invalid("Media build manifest knowledge does not match descriptor")
    _validate_prebuilt_adoption(plan, selected)
    val replacements = selected.map { resolved =>
      val output = _output(resolved).getOrElse(
        _invalid(s"Media resource has no receipt output: ${resolved.resource.id}")
      )
      if (!Files.isRegularFile(output))
        _invalid(s"Media receipt output must be a current regular file: $output")
      ManifestEntry(
        resolved.resource.id,
        _relative_from_path(plan.descriptorRoot, output, "Media receipt output"),
        Some(receiptvalue),
        if (resolved.resource.build == "presentation") CozyMediaPresentation.artifacts(plan, resolved) else Vector.empty
      )
    }
    val retained = if (target.isDefined || selectedids != declaredids) existing.resources.filterNot(x => selectedids.contains(x.id)) else Vector.empty
    val merged = (retained ++ replacements).sortBy(_.id)
    val ids = merged.map(_.id)
    if (ids.distinct.size != ids.size)
      _invalid("Media build manifest resource ids must be unique")
    val json = Json.obj(
      "schema" -> Json.fromString(_schema),
      "knowledge" -> Json.fromString(plan.descriptor.knowledge.id),
      "resources" -> Json.fromValues(merged.map(_entry_json))
    )
    PreparedAcceptance(
      Manifest(plan.descriptor.knowledge.id, merged),
      PreparedDocument(_manifest_path(plan), (json.spaces2 + "\n").getBytes(StandardCharsets.UTF_8))
    )
  }

  private[cozy] def candidateCurrent(
    plan: CozyMedia.Plan,
    resolved: CozyMedia.ResolvedResource,
    entry: ManifestEntry,
    captured: Captured
  ): Boolean =
    try _entry_evidence_failure(plan, resolved, entry).isEmpty
    catch { case NonFatal(_) => false }

  /**
   * Writes prepared review states directly in order, followed by the receipt.
   * An I/O failure propagates and may leave earlier or partial documents;
   * successful acceptance requires every write to complete.
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
    documents.zip(paths).foreach { case (document, targetpath) =>
      val parent = Option(targetpath.getParent).getOrElse(_invalid(s"Media acceptance target requires parent: $targetpath"))
      Files.createDirectories(parent)
      Files.write(targetpath, document.bytes)
    }
  }

  def prebuiltAcceptanceAllowed(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    _validate_prebuilt_adoption(plan, Vector(resolved))
  }

  private def _validate_prebuilt_adoption(plan: CozyMedia.Plan, selected: Vector[CozyMedia.ResolvedResource]): Unit =
    selected.foreach { resolved =>
      val output = _output(resolved).getOrElse(_invalid(s"Media resource has no output: ${resolved.resource.id}"))
      if (!Files.isRegularFile(output)) _invalid(s"Media output must be a current regular file: $output")
      _validate_output(plan, resolved, output)
    }

  private def _validate_output(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, output: Path): Unit = {
    val pdf = Set("article-pdf", "summary-slides-pdf").contains(resolved.resource.build) ||
      (resolved.resource.kind == "document" && resolved.resource.articleMedia.exists(media => Set("article_pdf", "summary_slides_pdf").contains(media.role)))
    if (pdf && !_has_header(output, _pdf_header)) _invalid(s"Media PDF output must have a PDF header: $output")
    if (resolved.resource.build == "prebuilt" && plan.descriptor.articleMedia.nonEmpty)
      resolved.resource.articleMedia.foreach { media =>
        media.role match {
          case "video" =>
            try _validate_site_video_prebuilt(plan, resolved, output)
            catch { case NonFatal(error) =>
              _invalid(s"${media.production.map(plan.descriptorRoot.resolve(_)).getOrElse(output)}: ${error.getMessage}") }
          case "infographic" =>
            val svg = media.mediaType.contains("image/svg+xml") || (media.mediaType.isEmpty && media.publicPath.exists(_.endsWith(".svg")))
            if (svg) _validate_svg_prebuilt(output, resolved.resource.id)
            else if (!_has_header(output, _png_header)) _invalid(s"Media prebuilt infographic must have a PNG header: $output")
          case _ => ()
        }
      }
  }

  private def _validate_svg_prebuilt(output: Path, resourceid: String): Unit = {
    try {
      val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
      factory.setNamespaceAware(true)
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      // Reject DTDs and external entities without requiring newer JAXP attributes.
      val builder = factory.newDocumentBuilder()
      builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler {
        override def error(exception: org.xml.sax.SAXParseException): Unit = throw exception
        override def fatalError(exception: org.xml.sax.SAXParseException): Unit = throw exception
      })
      val root = builder.parse(output.toFile).getDocumentElement
      if (root.getLocalName != "svg" ||
          (Option(root.getNamespaceURI).exists(_ != "http://www.w3.org/2000/svg")))
        _invalid(s"Media prebuilt infographic must have an SVG root: $resourceid")
    } catch {
      case NonFatal(_) => _invalid(s"Media prebuilt infographic must be valid SVG XML: $resourceid")
    }
  }

  private def _has_header(path: Path, header: Array[Byte]): Boolean = {
    val bytes = Files.readAllBytes(path)
    bytes.length >= header.length && bytes.take(header.length).sameElements(header)
  }

  private def _validate_site_video_prebuilt(
    plan: CozyMedia.Plan,
    resolved: CozyMedia.ResolvedResource,
    output: Path
  ): Unit = {
    val resource = resolved.resource
    val articlemedia = resource.articleMedia.filter(_.role == "video")
    if (resource.kind != "video" || articlemedia.isEmpty)
      _invalid(s"Media site-video prebuilt requires a video resource: ${resource.id}")
    else {
      val article = plan.descriptor.articleMedia.getOrElse(
        _invalid(s"Media site-video prebuilt requires top-level articleMedia: ${resource.id}")
      )
      val locale = resource.language.getOrElse(
        _invalid(s"Media site-video prebuilt requires language: ${resource.id}")
      )
      if (!_direct_regular_file(output) || !output.getFileName.toString.endsWith(".mp4"))
        _invalid(s"Media site-video prebuilt output must be a direct MP4 file: ${resource.id}")
      val production = _site_video_production_file(
        plan.descriptorRoot,
        articlemedia.flatMap(_.production).getOrElse(
          _invalid(s"Media site-video prebuilt requires production evidence: ${resource.id}")
        ),
        resource.id
      )
      val root = parse(Files.readString(production, StandardCharsets.UTF_8)).fold(
        _ => _invalid(s"Media site-video production must contain valid JSON: ${resource.id}"),
        _.asObject.getOrElse(_invalid(s"Media site-video production must be a JSON object: ${resource.id}"))
      )
      val category = _site_video_production_string(root, "category", resource.id)
      val articleid = _site_video_production_string(root, "article", resource.id)
      if (s"$category/$articleid" != article.articleIdentity)
        _invalid(s"Media site-video production identity differs: ${resource.id}")
      if (_site_video_production_string(root, "language", resource.id) != locale)
        _invalid(s"Media site-video production language differs: ${resource.id}")
      val render = root("render").flatMap(_.asObject).getOrElse(
        _invalid(s"Media site-video production render must be an object: ${resource.id}")
      )
      if (_site_video_production_string(render, "status", resource.id) != "completed")
        _invalid(s"Media site-video production render status must be completed: ${resource.id}")
      val qa = render("qa").flatMap(_.asObject).getOrElse(
        _invalid(s"Media site-video production render QA must be an object: ${resource.id}")
      )
      if (_site_video_production_string(qa, "status", resource.id) != "technical-and-visual-qa-passed")
        _invalid(s"Media site-video production render QA status is invalid: ${resource.id}")
    }
  }

  private def _site_video_production_file(root: Path, value: String, resourceid: String): Path = {
    val production = _relative_path(root, value, s"Media site-video production for $resourceid")
    if (!production.startsWith(root) || !_direct_regular_file(production))
      _invalid(s"Media site-video production must be a direct regular file: $resourceid")
    production
  }

  private def _site_video_production_string(
    value: io.circe.JsonObject,
    key: String,
    resourceid: String
  ): String =
    value(key).flatMap(_.asString).filter(x => x.nonEmpty && x == x.trim).getOrElse(
      _invalid(s"Media site-video production $key must be a non-empty exact string: $resourceid")
    )

  private def _manifest(path: Path): Manifest = {
    val root = parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      _ => _invalid("Media build manifest must contain valid JSON"),
      _.asObject.getOrElse(_invalid("Media build manifest must be a JSON object"))
    )
    _known_keys(root, Set("schema", "knowledge", "resources"), "Media build manifest")
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
    _known_keys(objectvalue, Set("id", "path"), "Media build manifest resource")
    val id = _require_identity(_exact_string(objectvalue, "id", "Media build manifest resource"), "Media build manifest resource id")
    val path = _receipt_relative_path(_exact_string(objectvalue, "path", "Media build manifest resource"), "Media build manifest resource path")
    val receipt = objectvalue("receipt").map(_receipt)
    val artifacts = objectvalue("artifacts").map(_.asArray.getOrElse(_invalid("Media build manifest artifacts must be an array")).toVector.map(_artifact)).getOrElse(Vector.empty)
    if (artifacts.map(_.id).distinct.size != artifacts.size)
      _invalid("Media build manifest artifact ids must be unique")
    ManifestEntry(id, path, receipt, artifacts)
  }

  private def _artifact(value: Json): Artifact = {
    val objectvalue = value.asObject.getOrElse(_invalid("Media build manifest artifact must be an object"))
      .filterKeys(Set("id", "role", "path").contains)
    _known_keys(objectvalue, Set("id", "role", "path"), "Media build manifest artifact")
    Artifact(
      _require_identity(_exact_string(objectvalue, "id", "Media build manifest artifact"), "Media build manifest artifact id"),
      _require_identity(_exact_string(objectvalue, "role", "Media build manifest artifact"), "Media build manifest artifact role"),
      _receipt_relative_path(_exact_string(objectvalue, "path", "Media build manifest artifact"), "Media build manifest artifact path")
    )
  }

  private def _receipt(value: Json): ResourceReceipt = {
    val objectvalue = value.asObject.getOrElse(_invalid("Media receipt must be an object"))
    _known_keys(objectvalue, Set("schema", "inputs", "producer", "operation", "acceptedAt", "verification"), "Media receipt")
    if (_exact_string(objectvalue, "schema", "Media receipt") != _receipt_schema)
      _invalid(s"Media receipt schema must be exactly ${_receipt_schema}")
    val inputs = objectvalue("inputs").flatMap(_.asArray).getOrElse(_invalid("Media receipt inputs must be an array")).toVector.map(_evidence_from_json)
    if (inputs.map(_.id) != inputs.map(_.id).sorted)
      _invalid("Media receipt inputs must be sorted by id")
    val ids = inputs.map(_.id)
    if (ids.distinct.size != ids.size)
      _invalid("Media receipt input ids must be unique")
    val producer = _producer_from_json(objectvalue("producer").getOrElse(_invalid("Media receipt producer is required")))
    val operation = objectvalue("operation").flatMap(_.asObject).getOrElse(_invalid("Media receipt operation must be an object"))
    val operationkeys = operation.keys.toSet
    if (!operationkeys.contains("name"))
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
    _known_keys(verification, Set("status", "findings"), "Media receipt verification")
    if (_exact_string(verification, "status", "Media receipt verification") != "valid" || verification("findings").flatMap(_.asArray).forall(_.nonEmpty))
      _invalid("Media receipt verification must be {status:valid,findings:[]}")
    ResourceReceipt(inputs, producer, target, profile, acceptedat)
  }

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
    if (!keys.contains("cozyVersion") || keys.contains("profile") != keys.contains("renderer"))
      _invalid("Media receipt producer requires cozyVersion and optional profile with renderer only")
    val cozyversion = _require_identity(_exact_string(objectvalue, "cozyVersion", "Media receipt producer"), "Media receipt cozyVersion")
    val profile = _optional_identity(objectvalue, "profile", "Media receipt producer profile")
    val renderer = objectvalue("renderer").map { item =>
      val rendererobject = item.asObject.getOrElse(_invalid("Media receipt renderer must be an object"))
      _known_keys(rendererobject, Set("name", "version"), "Media receipt renderer")
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
      "path" -> Json.fromString(value.path)
    ) ++ value.receipt.map(x => "receipt" -> _receipt_json(x)).toVector ++
      (if (value.artifacts.nonEmpty) Vector("artifacts" -> Json.fromValues(value.artifacts.map(_artifact_json))) else Vector.empty))

  private def _artifact_json(value: Artifact): Json =
    Json.obj(
      "id" -> Json.fromString(value.id),
      "role" -> Json.fromString(value.role),
      "path" -> Json.fromString(value.path)
    )

  private def _receipt_json(value: ResourceReceipt): Json =
    Json.obj(
      "schema" -> Json.fromString(_receipt_schema),
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
    normalization match {
      case "bytes" => ()
      case "structured-document" => _validate_structured_document(path)
      case _ => _invalid(s"Unsupported media receipt normalization: $normalization")
    }
    Evidence(id, role, relative, normalization)
  }

  private def _site_evidence(id: String, role: String, path: Path, route: String): Evidence = {
    if (!_direct_regular_file(path))
      _invalid(s"Media site receipt input must be a current direct non-symlink regular file: $path")
    Evidence(id, role, _receipt_relative_path(route, "Media site receipt route"), "bytes")
  }

  private def _evidence_json(value: Evidence): Json =
    Json.obj(
      "id" -> Json.fromString(value.id),
      "role" -> Json.fromString(value.role),
      "path" -> Json.fromString(value.path),
      "normalization" -> Json.fromString(value.normalization)
    )

  private def _evidence_from_json(value: Json): Evidence = {
    val objectvalue = value.asObject.getOrElse(_invalid("Media receipt input evidence must be an object"))
    _known_keys(objectvalue, Set("id", "role", "path", "normalization"), "Media receipt input evidence")
    val id = _require_identity(_exact_string(objectvalue, "id", "Media receipt input evidence"), "Media receipt input evidence id")
    val role = _require_identity(_exact_string(objectvalue, "role", "Media receipt input evidence"), "Media receipt input evidence role")
    val path = _receipt_relative_path(_exact_string(objectvalue, "path", "Media receipt input evidence"), "Media receipt input evidence path")
    val normalization = _exact_string(objectvalue, "normalization", "Media receipt input evidence") match {
      case "bytes" | "structured-document" => _exact_string(objectvalue, "normalization", "Media receipt input evidence")
      case _ => _invalid("Media receipt input evidence normalization must be bytes or structured-document")
    }
    val evidence = Evidence(id, role, path, normalization)
    if (evidence.id == _site_config_evidence_id && (evidence.role != "site-configuration" || evidence.normalization != "bytes"))
      _invalid("Media site configuration receipt evidence must use site-configuration bytes identity")
    if (evidence.id == _site_document_route_evidence_id && (evidence.role != "site-document-route" || evidence.normalization != "bytes"))
      _invalid("Media site document-route receipt evidence must use site-document-route bytes identity")
    evidence
  }

  private def _validate_structured_document(path: Path): Unit = {
    try StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take catch {
      case NonFatal(_) => _invalid(s"Media receipt structured-document input is invalid: $path")
    }
  }

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

  private def _known_keys(value: io.circe.JsonObject, required: Set[String], label: String): Unit =
    if (!required.subsetOf(value.keys.toSet)) _invalid(s"$label requires ${required.toVector.sorted.mkString(", ")}")

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
