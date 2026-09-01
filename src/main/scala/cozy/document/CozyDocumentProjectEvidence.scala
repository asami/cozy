package cozy.document

import cozy.media.{CozyMedia, CozyMediaReceipt}
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.security.MessageDigest
import java.util.UUID
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource

/*
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectEvidence {
  final case class FileIdentity(path: String, sha256: String)
  final case class PublicSource(identity: String, path: FileIdentity, mediaDescriptor: String)
  sealed trait Evidence {
    def kind: String
  }
  case object NoEvidence extends Evidence {
    val kind = "none"
  }
  final case class FileEvidence(kind: String, file: FileIdentity) extends Evidence
  final case class ReceiptEvidence(mediaDescriptor: String, resourceId: String) extends Evidence {
    val kind = "receipt"
  }
  case object ReceiptSetEvidence extends Evidence {
    val kind = "receipt-set"
  }
  sealed trait Review {
    def kind: String
  }
  case object NoReview extends Review {
    val kind = "none"
  }
  final case class AcceptedReview(
    provider: String,
    model: String,
    request: FileIdentity,
    response: FileIdentity,
    acceptedAuthority: FileIdentity
  ) extends Review {
    val kind = "core-dialogue"
  }
  final case class RejectedReview(
    provider: String,
    model: String,
    request: FileIdentity,
    response: FileIdentity,
    rejectionReason: String
  ) extends Review {
    val kind = "core-dialogue"
  }
  final case class ProductEvidence(id: String, evidence: Evidence, review: Review)
  final case class Sidecar(path: FileIdentity, publicSource: PublicSource, products: Vector[ProductEvidence])
  final case class Attempt(path: FileIdentity, outcome: String, products: Vector[String])
  final case class WorkProductState(
    value: CozyDocumentWorkflow.ResolvedWorkProduct,
    coverage: String,
    currentness: String,
    review: String,
    readiness: String,
    reason: Option[String]
  )
  private[cozy] final case class CriterionState(id: String, coverage: String, reason: String)
  final case class Snapshot(
    sources: Vector[FileIdentity],
    sidecar: Option[Sidecar],
    attempts: Vector[Attempt],
    products: Vector[WorkProductState],
    criteria: Vector[CriterionState]
  )

  private final case class EvidenceStatus(currentness: String, reason: Option[String])

  private val _sidecar_schema = "cozy.document-project-evidence.v1"
  private val _attempt_schema = "cozy.document-operation-attempt.v1"
  private val _hash_pattern = "[0-9a-f]{64}".r

  def snapshot(project: Path, descriptor: CozyDocumentProject.Descriptor): Snapshot = {
    val resolved = CozyDocumentWorkflow.resolve(descriptor.profile) match {
      case Right(value) => value
      case Left(cause) => CozyDocumentProject._descriptor_failure(cause)
    }
    val sources = CozyDocumentProject._state_sources(project, descriptor).map {
      case (relative, path) => FileIdentity(relative, _sha256(path))
    }
    val sidecar = _sidecar(project, descriptor, resolved, sources)
    val attempts = _attempts(project, descriptor)
    val products = _products(project, descriptor, resolved, sources, sidecar, attempts)
    val productsbycriterion = products.flatMap { product =>
      product.value.workProduct.criteria.map(_ -> product)
    }.toMap
    val criteria = resolved.definition.criteria.map { criterion =>
      productsbycriterion.get(criterion.id) match {
        case Some(product) =>
          val reason = product.coverage match {
            case "missing" => product.reason.getOrElse(s"criterion ${criterion.id} is missing")
            case "not-applicable" => product.reason.getOrElse(s"criterion ${criterion.id} is not applicable")
            case _ => product.reason.getOrElse("")
          }
          CriterionState(criterion.id, product.coverage, reason)
        case None => CozyDocumentProject._descriptor_failure(s"document-production criterion is not mapped to a Work Product: ${criterion.id}")
      }
    }
    Snapshot(sources, sidecar, attempts, products, criteria)
  }

  def stateYaml(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    val value = snapshot(project, descriptor)
    val sourceyaml = value.sources.map(identity => s"  - path: ${identity.path}\n    sha256: ${identity.sha256}")
    val evidenceyaml = value.sidecar match {
      case Some(sidecar) =>
        Vector(
          "  sidecar:",
          s"    path: ${sidecar.path.path}",
          s"    sha256: ${sidecar.path.sha256}",
          "  attempts:"
        ) ++ _attempt_yaml(value.attempts)
      case None => Vector("  sidecar: none", "  attempts:") ++ _attempt_yaml(value.attempts)
    }
    val productyaml = value.products.map { product =>
      val workproduct = product.value.workProduct
      val binding = product.value.binding
      (Vector(
        s"  - id: ${workproduct.id}",
        s"    role: ${workproduct.role.value}",
        s"    disposition: ${binding.disposition.value}",
        s"    criterion: ${workproduct.criteria.head}",
        s"    coverage: ${product.coverage}",
        s"    currentness: ${product.currentness}",
        s"    review: ${product.review}",
        s"    readiness: ${product.readiness}"
      ) ++ product.reason.map(reason => s"    reason: ${_yaml_double_quoted(reason)}").toVector).mkString("\n")
    }
    val criteriayaml = Vector(
      s"  satisfied: ${value.criteria.count(_.coverage == "satisfied")}",
      s"  total: ${value.criteria.count(_.coverage != "not-applicable")}",
      "  missing:"
    ) ++ _criterion_yaml(value.criteria.filter(_.coverage == "missing")) ++ Vector(
      "  notApplicable:"
    ) ++ _criterion_yaml(value.criteria.filter(_.coverage == "not-applicable"))
    (Vector(
      "schema: cozy.document-project-state.v1",
      s"project: ${descriptor.id}",
      s"profile: ${descriptor.profile}",
      s"workspace: ${descriptor.workspace}",
      "sources:"
    ) ++ sourceyaml ++ Vector("evidence:") ++ evidenceyaml ++ Vector("criteria:") ++ criteriayaml ++ Vector("workProducts:") ++ productyaml).mkString("\n") + "\n"
  }

  private def _criterion_yaml(criteria: Vector[CriterionState]): Vector[String] =
    if (criteria.isEmpty) Vector("    []")
    else criteria.flatMap { criterion =>
      Vector(
        s"    - id: ${criterion.id}",
        s"      reason: ${_yaml_double_quoted(criterion.reason)}"
      )
    }

  private def _attempt_yaml(attempts: Vector[Attempt]): Vector[String] =
    if (attempts.isEmpty) {
      Vector("    []")
    } else {
      attempts.flatMap { attempt =>
      Vector(
        s"    - path: ${attempt.path.path}",
        s"      sha256: ${attempt.path.sha256}",
        s"      outcome: ${attempt.outcome}"
      )
      }
    }

  private def _sidecar(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    resolved: CozyDocumentWorkflow.ResolvedWorkflow,
    sources: Vector[FileIdentity]
  ): Option[Sidecar] = {
    val evidence = project.resolve("evidence").normalize()
    val sidecarpath = evidence.resolve("document-project.yaml").normalize()
    if (!sidecarpath.startsWith(project)) {
      CozyDocumentProject._failure("DP-PATH-001", "Document Project evidence sidecar escapes the project")
    }
    if (!Files.exists(sidecarpath, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.exists(evidence, LinkOption.NOFOLLOW_LINKS)) {
        CozyDocumentProject._direct_directory(evidence, "evidence directory")
      }
      None
    } else {
      CozyDocumentProject._direct_directory(evidence, "evidence directory")
      val admittedsidecar = _direct_project_file(project, "evidence/document-project.yaml", "Document Project evidence sidecar")
      val json = _load_json(admittedsidecar, "Document Project evidence sidecar")
      _require_top_level_order(admittedsidecar, Vector("schema", "project", "publicSource", "products"), "Document Project evidence sidecar")
      val fields = _object(json, "Document Project evidence sidecar")
      if (fields.keySet != Set("schema", "project", "publicSource", "products")) {
        _invalid("Document Project evidence sidecar must have exactly schema, project, publicSource, products")
      }
      if (_string(fields, "schema", "Document Project evidence sidecar") != _sidecar_schema) {
        _invalid(s"Document Project evidence sidecar schema must be exactly ${_sidecar_schema}")
      }
      if (_string(fields, "project", "Document Project evidence sidecar") != descriptor.id) {
        _invalid("Document Project evidence sidecar project must equal the descriptor id")
      }
      val publicsource = _public_source(project, _field(fields, "publicSource", "Document Project evidence sidecar"))
      val productvalues = _field(fields, "products", "Document Project evidence sidecar").asArray.getOrElse(_invalid("Document Project evidence sidecar products must be an array"))
      val enabled = resolved.workProducts.filter(_.binding.disposition != CozyDocumentWorkflow.WorkProductDisposition.Disabled)
      if (productvalues.size != enabled.size) {
        _invalid("Document Project evidence sidecar products must enumerate every enabled Work Product exactly once")
      }
      val products = productvalues.zip(enabled).map {
        case (value, expected) => _product(project, descriptor, expected, sources.map(_.path).toSet, value)
      }.toVector
      Some(Sidecar(FileIdentity("evidence/document-project.yaml", _sha256(admittedsidecar)), publicsource, products))
    }
  }

  private def _public_source(project: Path, value: Json): PublicSource = {
    val fields = _object(value, "Document Project evidence publicSource")
    if (fields.keySet != Set("kind", "identity", "path", "sha256", "mediaDescriptor")) {
      _invalid("Document Project evidence publicSource must have exactly kind, identity, path, sha256, mediaDescriptor")
    }
    if (_string(fields, "kind", "Document Project evidence publicSource") != "smartdox") {
      _invalid("Document Project evidence publicSource kind must be smartdox")
    }
    val identity = _nonempty(_string(fields, "identity", "Document Project evidence publicSource"), "Document Project evidence publicSource identity")
    val path = _string(fields, "path", "Document Project evidence publicSource")
    if (path != "index.dox") {
      _invalid("Document Project evidence publicSource path must be index.dox")
    }
    val source = _file_identity(project, path, _string(fields, "sha256", "Document Project evidence publicSource"), "Document Project public source")
    if (source.sha256 != _sha256(_direct_project_file(project, path, "Document Project public source"))) {
      _invalid("Document Project evidence publicSource sha256 must match current index.dox bytes")
    }
    val media = _string(fields, "mediaDescriptor", "Document Project evidence publicSource")
    val mediapath = _direct_project_file(project, media, "Document Project public source media descriptor")
    val mediafields = _object(_load_json(mediapath, "Document Project public source media descriptor"), "Document Project public source media descriptor")
    if (_string(mediafields, "schema", "Document Project public source media descriptor") != "cozy.media.v1") {
      _invalid("Document Project public source media descriptor schema must be exactly cozy.media.v1")
    }
    val articlemedia = _object(_field(mediafields, "articleMedia", "Document Project public source media descriptor"), "Document Project public source media descriptor articleMedia")
    if (_string(articlemedia, "articleIdentity", "Document Project public source media descriptor articleMedia") != identity) {
      _invalid("Document Project public source articleMedia.articleIdentity must equal publicSource.identity")
    }
    PublicSource(identity, source, media)
  }

  private def _product(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    expected: CozyDocumentWorkflow.ResolvedWorkProduct,
    declaredsources: Set[String],
    value: Json
  ): ProductEvidence = {
    val fields = _object(value, "Document Project evidence product")
    if (fields.keySet != Set("id", "evidence", "review")) {
      _invalid("Document Project evidence product must have exactly id, evidence, review")
    }
    val id = _string(fields, "id", "Document Project evidence product")
    if (id != expected.workProduct.id) {
      _invalid("Document Project evidence products must follow immutable Work Product order")
    }
    val evidence = _evidence(project, id, declaredsources, _field(fields, "evidence", "Document Project evidence product"))
    val review = _review(project, descriptor, id, _field(fields, "review", "Document Project evidence product"))
    ProductEvidence(id, evidence, review)
  }

  private def _evidence(project: Path, productid: String, declaredsources: Set[String], value: Json): Evidence = {
    val fields = _object(value, s"Document Project evidence $productid")
    val kind = _string(fields, "kind", s"Document Project evidence $productid")
    kind match {
      case "none" =>
        if (fields.keySet != Set("kind")) {
          _invalid(s"Document Project evidence $productid none must contain only kind")
        }
        NoEvidence
      case "source" | "artifact" =>
        if (fields.keySet != Set("kind", "path", "sha256")) {
          _invalid(s"Document Project evidence $productid $kind must have exactly kind, path, sha256")
        }
        val path = _string(fields, "path", s"Document Project evidence $productid")
        if (kind == "artifact") {
          _require_artifact_path(project, path, s"Document Project artifact evidence $productid")
        }
        val file = _file_identity(project, path, _string(fields, "sha256", s"Document Project evidence $productid"), s"Document Project evidence $productid")
        if (kind == "source" && !declaredsources.contains(path)) {
          _invalid(s"Document Project source evidence must name a declared authored authority: $productid")
        }
        FileEvidence(kind, file)
      case "receipt" =>
        if (fields.keySet != Set("kind", "mediaDescriptor", "resourceId")) {
          _invalid(s"Document Project receipt evidence $productid must have exactly kind, mediaDescriptor, resourceId")
        }
        val media = _string(fields, "mediaDescriptor", s"Document Project receipt evidence $productid")
        _direct_project_file(project, media, s"Document Project receipt evidence $productid media descriptor")
        ReceiptEvidence(media, _nonempty(_string(fields, "resourceId", s"Document Project receipt evidence $productid"), s"Document Project receipt evidence $productid resourceId"))
      case "receipt-set" =>
        if (productid != "operation-receipt-evidence" || fields.keySet != Set("kind")) {
          _invalid("Document Project receipt-set evidence is valid only for operation-receipt-evidence and contains only kind")
        }
        ReceiptSetEvidence
      case _ => _invalid(s"Document Project evidence kind is invalid: $productid")
    }
  }

  private def _review(project: Path, descriptor: CozyDocumentProject.Descriptor, productid: String, value: Json): Review = {
    val fields = _object(value, s"Document Project review $productid")
    _string(fields, "kind", s"Document Project review $productid") match {
      case "none" =>
        if (fields.keySet != Set("kind")) {
          _invalid(s"Document Project review $productid none must contain only kind")
        }
        NoReview
      case "core-dialogue" =>
        if (productid != "content-core") {
          _invalid("Document Project core-dialogue review is valid only for content-core")
        }
        val common = Set("kind", "provider", "model", "request", "response")
        val accepted = fields.get("accepted")
        val rejected = fields.get("rejected")
        if (accepted.isDefined == rejected.isDefined || !fields.keySet.subsetOf(common ++ Set("accepted", "rejected"))) {
          _invalid("Document Project core-dialogue review requires exactly one accepted or rejected disposition branch")
        }
        if (!common.subsetOf(fields.keySet)) {
          _invalid("Document Project core-dialogue review is missing common identity fields")
        }
        val provider = _nonempty(_string(fields, "provider", "Document Project core-dialogue review"), "Document Project core-dialogue provider")
        val model = _nonempty(_string(fields, "model", "Document Project core-dialogue review"), "Document Project core-dialogue model")
        val request = _nested_file_identity(project, _field(fields, "request", "Document Project core-dialogue review"), "Document Project core-dialogue request")
        val response = _nested_file_identity(project, _field(fields, "response", "Document Project core-dialogue review"), "Document Project core-dialogue response")
        accepted match {
          case Some(branch) =>
            if (fields.keySet != common + "accepted") {
              _invalid("Document Project accepted core-dialogue review has an invalid field")
            }
            val acceptedfields = _object(branch, "Document Project accepted core-dialogue review")
            if (acceptedfields.keySet != Set("acceptedAuthority")) {
              _invalid("Document Project accepted core-dialogue review must have exactly acceptedAuthority")
            }
            val authority = _nested_file_identity(project, _field(acceptedfields, "acceptedAuthority", "Document Project accepted core-dialogue review"), "Document Project accepted core authority")
            if (authority.path != descriptor.contentCore) {
              _invalid("Document Project accepted core authority must equal the descriptor Content Core path")
            }
            AcceptedReview(provider, model, request, response, authority)
          case None =>
            if (fields.keySet != common + "rejected") {
              _invalid("Document Project rejected core-dialogue review has an invalid field")
            }
            val rejectedfields = _object(rejected.get, "Document Project rejected core-dialogue review")
            if (rejectedfields.keySet != Set("rejectionReason")) {
              _invalid("Document Project rejected core-dialogue review must have exactly rejectionReason")
            }
            RejectedReview(provider, model, request, response, _nonempty(_string(rejectedfields, "rejectionReason", "Document Project rejected core-dialogue review"), "Document Project rejected core-dialogue rejectionReason"))
        }
      case _ => _invalid(s"Document Project review kind is invalid: $productid")
    }
  }

  private def _products(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    resolved: CozyDocumentWorkflow.ResolvedWorkflow,
    sources: Vector[FileIdentity],
    sidecar: Option[Sidecar],
    attempts: Vector[Attempt]
  ): Vector[WorkProductState] = {
    val sourcepaths = sources.map(_.path).toSet
    val coreaccepted = CozyDocumentProject._core_has_accepted_entries(project, descriptor)
    val declared = sidecar.map(_.products.map(value => value.id -> value).toMap).getOrElse(Map.empty)
    val initial = resolved.workProducts.map { value =>
      val binding = value.binding
      val product = value.workProduct
      if (binding.disposition == CozyDocumentWorkflow.WorkProductDisposition.Disabled) {
        product.id -> EvidenceStatus("not-applicable", binding.reason)
      } else {
        product.id -> declared.get(product.id).map(entry => _evidence_status(project, entry.evidence, declared)).getOrElse(_legacy_status(descriptor, product.id, sourcepaths))
      }
    }.toMap
    val initiallycurrent = initial.collect { case (id, EvidenceStatus("current", _)) => id }.toSet
    val stale = _stale_products(resolved, initial.collect { case (id, EvidenceStatus("stale", _)) => id }.toSet)
    val withstale = initial.map {
      case (id, EvidenceStatus(currentness, _)) if currentness != "not-applicable" && stale.contains(id) => id -> EvidenceStatus("stale", Some("a declared dependency is stale"))
      case item => item
    }
    val failedproducts = attempts.filter(_.outcome == "failed").flatMap(_.products).toSet
    resolved.workProducts.map { value =>
      val product = value.workProduct
      val binding = value.binding
      val evidence = withstale(product.id)
      val currentness =
        if (evidence.currentness == "not-applicable") {
          "not-applicable"
        } else if (!initiallycurrent.contains(product.id) && evidence.currentness != "current" && failedproducts.contains(product.id)) {
          "failed"
        } else {
          evidence.currentness
        }
      val review = declared.get(product.id).map(entry => _review_status(project, descriptor, entry.review)).getOrElse("pending")
      val coverage =
        if (currentness == "not-applicable") {
          "not-applicable"
        } else if (currentness == "current" && (product.id != "content-core" || coreaccepted)) {
          "satisfied"
        } else {
          "missing"
        }
      val readiness =
        if (currentness == "not-applicable") {
          "omitted"
        } else if (currentness == "failed" || review == "rejected") {
          "failed"
        } else if (currentness == "current" && review != "stale") {
          "ready"
        } else {
          "blocked"
        }
      val reason =
        if (currentness == "not-applicable") {
          binding.reason
        } else if (currentness == "failed") {
          Some("a retained failed attempt has no current product evidence")
        } else if (review == "rejected") {
          declared.get(product.id).collect { case ProductEvidence(_, _, RejectedReview(_, _, _, _, reason)) => s"review rejected: $reason" }
        } else if (review == "stale") {
          Some("review evidence is stale")
        } else {
          evidence.reason.orElse(_legacy_reason(descriptor, product.id, sourcepaths, currentness))
        }
      WorkProductState(value, coverage, currentness, review, readiness, reason)
    }
  }

  private def _evidence_status(project: Path, evidence: Evidence, declared: Map[String, ProductEvidence]): EvidenceStatus = evidence match {
    case NoEvidence => EvidenceStatus("missing", Some("declared evidence is missing"))
    case FileEvidence(_, file) =>
      val path = project.resolve(file.path).normalize()
      if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        EvidenceStatus("missing", Some("declared evidence is missing"))
      } else if (_sha256(path) == file.sha256) {
        EvidenceStatus("current", None)
      } else {
        EvidenceStatus("stale", Some("declared evidence sha256 does not match current bytes"))
      }
    case ReceiptEvidence(media, resource) => _receipt_status(project, media, resource)
    case ReceiptSetEvidence =>
      val receipts = declared.values.collect { case ProductEvidence(_, receipt: ReceiptEvidence, _) => receipt }.toVector
      if (receipts.isEmpty) {
        EvidenceStatus("missing", Some("declared receipt evidence is missing"))
      } else {
        val statuses = receipts.map(receipt => _receipt_status(project, receipt.mediaDescriptor, receipt.resourceId))
        if (statuses.forall(_.currentness == "current")) {
          EvidenceStatus("current", None)
        } else if (statuses.exists(_.currentness == "stale")) {
          EvidenceStatus("stale", Some("declared receipt evidence is stale"))
        } else {
          EvidenceStatus("missing", Some("declared receipt evidence is missing"))
        }
      }
  }

  private def _receipt_status(project: Path, media: String, resourceid: String): EvidenceStatus = {
    val descriptor = _direct_project_file(project, media, "Document Project receipt media descriptor")
    try {
      val plan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor.toRealPath()))
      plan.resources.find(_.resource.id == resourceid) match {
        case None => EvidenceStatus("missing", Some("declared receipt resource is missing"))
        case Some(resource) if CozyMediaReceipt.current(plan, resource) => EvidenceStatus("current", None)
        case Some(resource) if resource.output.exists(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) => EvidenceStatus("stale", Some("declared receipt is not current"))
        case Some(_) => EvidenceStatus("missing", Some("declared receipt output is missing"))
      }
    } catch {
      case NonFatal(_) => EvidenceStatus("missing", Some("declared receipt evidence is unavailable"))
    }
  }

  private def _legacy_status(
    descriptor: CozyDocumentProject.Descriptor,
    productid: String,
    sourcepaths: Set[String]
  ): EvidenceStatus = {
    val available = productid match {
      case "content-core" => sourcepaths.contains(descriptor.contentCore)
      case "article-source" => sourcepaths.contains("index.dox")
      case "visual-pages" => sourcepaths.contains("presentation/visual-pages.yaml")
      case "infographic-svg" => sourcepaths.contains("infographic/infographic.svg")
      case "video-storyboard" => sourcepaths.contains("video/storyboard.md")
      case _ => false
    }
    if (available) {
      EvidenceStatus("current", None)
    } else {
      EvidenceStatus("missing", None)
    }
  }

  private def _legacy_reason(descriptor: CozyDocumentProject.Descriptor, productid: String, sourcepaths: Set[String], currentness: String): Option[String] = {
    if (currentness != "missing") {
      None
    } else {
      val sourcesavailable = productid match {
        case "core-review-html" => sourcepaths.contains(descriptor.contentCore)
        case "slide-review-html" => sourcepaths.contains("presentation/visual-pages.yaml")
        case "video-review" => sourcepaths.contains("presentation/visual-pages.yaml") && sourcepaths.contains("video/storyboard.md")
        case "explanation-structure-review-html" => sourcepaths.contains(descriptor.contentCore) && sourcepaths.contains("presentation/visual-pages.yaml")
        case "video-logical-chart-html" => sourcepaths.contains(descriptor.contentCore) && sourcepaths.contains("presentation/visual-pages.yaml") && sourcepaths.contains("video/storyboard.md")
        case _ => false
      }
      if (Set("core-review-html", "slide-review-html", "video-review", "explanation-structure-review-html", "video-logical-chart-html").contains(productid) && sourcesavailable) {
        Some("default review HTML is not generated")
      } else {
        Some("source or retained evidence is not present")
      }
    }
  }

  private def _review_status(project: Path, descriptor: CozyDocumentProject.Descriptor, review: Review): String = review match {
    case NoReview => "pending"
    case RejectedReview(_, _, request, response, _) =>
      if (_identity_current(project, request) && _identity_current(project, response)) {
        "rejected"
      } else {
        "stale"
      }
    case AcceptedReview(_, _, request, response, authority) =>
      if (_identity_current(project, request) && _identity_current(project, response) && authority.path == descriptor.contentCore && _identity_current(project, authority)) {
        "accepted"
      } else {
        "stale"
      }
  }

  private def _stale_products(resolved: CozyDocumentWorkflow.ResolvedWorkflow, initial: Set[String]): Set[String] = {
    @annotation.tailrec
    def _go_(stale: Set[String]): Set[String] = {
      val dependencies = resolved.workProducts.collect {
        case value if value.workProduct.dependencies.exists(stale.contains) => value.workProduct.id
      }.toSet
      val consumers = resolved.workProducts.filter(value => stale.contains(value.workProduct.id)).flatMap { value =>
        value.workProduct.consumers.flatMap { operationid =>
          resolved.definition.operations.find(_.id == operationid).map(_.produces).getOrElse(Vector.empty)
        }
      }.toSet
      val next = stale ++ dependencies ++ consumers
      if (next == stale) {
        stale
      } else {
        _go_(next)
      }
    }
    _go_(initial)
  }

  private def _attempts(project: Path, descriptor: CozyDocumentProject.Descriptor): Vector[Attempt] = {
    val evidence = project.resolve("evidence").normalize()
    val directory = evidence.resolve("attempts").normalize()
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
      Vector.empty
    } else {
      CozyDocumentProject._direct_directory(evidence, "evidence directory")
      CozyDocumentProject._direct_directory(directory, "attempts directory")
      val stream = Files.list(directory)
      try {
        stream.iterator().asScala.toVector.sortBy(path => CozyDocumentProject._project_relative(project, path)).map { path =>
          if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            CozyDocumentProject._failure("DP-PATH-001", "retained attempt must be a direct regular non-symlink file")
          }
          _attempt(project, descriptor, path)
        }
      } finally {
        stream.close()
      }
    }
  }

  private def _attempt(project: Path, descriptor: CozyDocumentProject.Descriptor, path: Path): Attempt = {
    val expectedorder = Vector("schema", "id", "operation", "provider", "profile", "inputs", "outcome", "diagnostics", "outputs", "receipt")
    _require_top_level_order(path, expectedorder, "Document Project retained attempt")
    val fields = _object(_load_json(path, "Document Project retained attempt"), "Document Project retained attempt")
    val expected = expectedorder.toSet
    if (fields.keySet != expected || _string(fields, "schema", "Document Project retained attempt") != _attempt_schema) {
      _invalid("Document Project retained attempt is not a closed cozy.document-operation-attempt.v1 document")
    }
    val attemptid = _string(fields, "id", "Document Project retained attempt")
    val canonicalid = try UUID.fromString(attemptid).toString catch {
      case NonFatal(_) => _invalid("Document Project retained attempt id must be a canonical UUID")
    }
    if (canonicalid != attemptid || path.getFileName.toString != s"$attemptid.yaml") {
      _invalid("Document Project retained attempt id must match its filename")
    }
    val operationid = _string(fields, "operation", "Document Project retained attempt")
    val operation = CozyDocumentWorkflow.declaredOperation(operationid) match {
      case Right(Some(value)) => value
      case _ => _invalid("Document Project retained attempt operation is not declared")
    }
    if (_string(fields, "provider", "Document Project retained attempt") != operation.providerBinding || _string(fields, "profile", "Document Project retained attempt") != descriptor.profile) {
      _invalid("Document Project retained attempt provider or profile is invalid")
    }
    val resolved = CozyDocumentWorkflow.resolve(descriptor.profile) match {
      case Right(value) => value
      case Left(cause) => _invalid(cause)
    }
    if (!operation.produces.exists(id => resolved.workProducts.exists(value => value.workProduct.id == id && value.binding.disposition != CozyDocumentWorkflow.WorkProductDisposition.Disabled))) {
      _invalid("Document Project retained attempt operation is disabled for the selected profile")
    }
    val inputvalues = _field(fields, "inputs", "Document Project retained attempt").asArray.getOrElse(_invalid("Document Project retained attempt inputs must be an array"))
    val expectedinputs = Vector(
      "document-project.yaml",
      descriptor.contentCore,
      "index.dox",
      "infographic/infographic.svg",
      "presentation/visual-pages.yaml",
      "review/README.md"
    ) ++ (if (CozyDocumentWorkflow.isVideoProfile(descriptor.profile)) Vector("video/storyboard.md") else Vector.empty)
    if (inputvalues.size != expectedinputs.size) {
      _invalid("Document Project retained attempt inputs must match fixed authored source order")
    }
    inputvalues.zip(expectedinputs).foreach {
      case (value, expectedpath) =>
        val inputfields = _object(value, "Document Project retained attempt input")
        if (inputfields.keySet != Set("path", "sha256")) {
          _invalid("Document Project retained attempt input must have exactly path and sha256")
        }
        val input = _file_identity(project, _string(inputfields, "path", "Document Project retained attempt input"), _string(inputfields, "sha256", "Document Project retained attempt input"), "Document Project retained attempt input")
        if (input.path != expectedpath) {
          _invalid("Document Project retained attempt inputs must match fixed authored source order")
        }
    }
    val diagnostics = _field(fields, "diagnostics", "Document Project retained attempt").asArray.getOrElse(_invalid("Document Project retained attempt diagnostics must be an array")).map { value =>
      value.asString.getOrElse(_invalid("Document Project retained attempt diagnostics must contain only strings"))
    }
    val outputs = _field(fields, "outputs", "Document Project retained attempt").asArray.getOrElse(_invalid("Document Project retained attempt outputs must be an array"))
    if (outputs.nonEmpty) {
      _invalid("Document Project retained attempt outputs must be empty")
    }
    if (_string(fields, "receipt", "Document Project retained attempt") != "none") {
      _invalid("Document Project retained attempt receipt must be none")
    }
    val outcome = _string(fields, "outcome", "Document Project retained attempt")
    if (!Set("recorded", "failed").contains(outcome)) {
      _invalid("Document Project retained attempt outcome must be recorded or failed")
    }
    if (outcome == "recorded" && diagnostics != Vector("provider execution is deferred; this dispatch was recorded only")) {
      _invalid("Document Project recorded attempt must contain the exact deferred-dispatch diagnostic")
    }
    if (outcome == "failed" && (diagnostics.isEmpty || diagnostics.exists(_.isEmpty))) {
      _invalid("Document Project failed attempt diagnostics must contain non-empty strings")
    }
    val products = if (outcome == "failed") {
      operation.produces
    } else {
      Vector.empty
    }
    Attempt(FileIdentity(CozyDocumentProject._project_relative(project, path), _sha256(path)), outcome, products)
  }

  private def _identity_current(project: Path, identity: FileIdentity): Boolean = {
    val path = project.resolve(identity.path).normalize()
    path.startsWith(project) && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && _sha256(path) == identity.sha256
  }

  private def _nested_file_identity(project: Path, value: Json, label: String): FileIdentity = {
    val fields = _object(value, label)
    if (fields.keySet != Set("path", "sha256")) {
      _invalid(s"$label must have exactly path and sha256")
    }
    _file_identity(project, _string(fields, "path", label), _string(fields, "sha256", label), label)
  }

  private def _file_identity(project: Path, relative: String, hash: String, label: String): FileIdentity = {
    _admit_evidence_file(project, relative, label)
    if (!_hash_pattern.pattern.matcher(hash).matches()) {
      _invalid(s"$label sha256 must be lowercase SHA-256")
    }
    FileIdentity(relative, hash)
  }

  private def _require_artifact_path(project: Path, relative: String, label: String): Unit = {
    if (!_relative_path(relative)) {
      CozyDocumentProject._failure("DP-PATH-001", s"$label path must be project-relative")
    }
    val target = project.resolve("target").normalize()
    val candidate = project.resolve(relative).normalize()
    val disposable = target.resolve("document-project").normalize()
    if (!candidate.startsWith(target) || candidate == target || candidate.startsWith(disposable)) {
      CozyDocumentProject._failure("DP-PATH-001", s"$label path must be under direct target output")
    }
  }

  private def _direct_project_file(project: Path, relative: String, label: String): Path = {
    if (!_relative_path(relative)) {
      CozyDocumentProject._failure("DP-PATH-001", s"$label path must be project-relative")
    }
    CozyDocumentProject._direct_file(project, relative, label)
  }

  private def _admit_evidence_file(project: Path, relative: String, label: String): Path = {
    if (!_relative_path(relative)) {
      CozyDocumentProject._failure("DP-PATH-001", s"$label path must be project-relative")
    }
    val path = project.resolve(relative).normalize()
    if (!path.startsWith(project)) {
      CozyDocumentProject._failure("DP-PATH-001", s"$label path escapes the project")
    }
    val segments = project.relativize(path)
    var parent = project
    (0 until segments.getNameCount - 1).foreach { index =>
      parent = parent.resolve(segments.getName(index))
      if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))) {
        CozyDocumentProject._failure("DP-PATH-001", s"$label parent must be a direct non-symlink directory")
      }
    }
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))) {
      CozyDocumentProject._failure("DP-PATH-001", s"$label must be a direct regular non-symlink file when present")
    }
    path
  }

  private def _relative_path(value: String): Boolean = {
    val path = try {
      Paths.get(value)
    } catch {
      case NonFatal(_) => return false
    }
    value.nonEmpty && !path.isAbsolute && path.iterator().asScala.forall(part => part.toString != "." && part.toString != "..")
  }

  private def _load_json(path: Path, label: String): Json = {
    try {
      Files.readString(path, StandardCharsets.UTF_8)
      StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
    } catch {
      case NonFatal(_) => _invalid(s"$label is missing, unreadable, or malformed")
    }
  }

  private def _require_top_level_order(path: Path, expected: Vector[String], label: String): Unit = {
    val keys = try {
      Files.readAllLines(path, StandardCharsets.UTF_8).asScala.collect {
        case line if line.nonEmpty && !line.startsWith(" ") && !line.startsWith("\t") && line.contains(":") => line.takeWhile(_ != ':').trim
      }.toVector
    } catch {
      case NonFatal(_) => _invalid("Document Project evidence sidecar cannot be read")
    }
    if (keys != expected) {
      _invalid(s"$label top-level keys must be ordered ${expected.mkString(", ")}")
    }
  }

  private def _yaml_double_quoted(value: String): String = {
    val builder = new StringBuilder("\"")
    value.foreach {
      case '\\' => builder.append("\\\\")
      case '"' => builder.append("\\\"")
      case '\r' => builder.append("\\r")
      case '\n' => builder.append("\\n")
      case '\t' => builder.append("\\t")
      case character if Character.isISOControl(character) => builder.append(f"\\u${character.toInt}%04x")
      case character => builder.append(character)
    }
    builder.append('"').result()
  }

  private def _object(value: Json, label: String): Map[String, Json] =
    value.asObject.map(_.toMap).getOrElse(_invalid(s"$label must be an object"))

  private def _field(fields: Map[String, Json], name: String, label: String): Json =
    fields.getOrElse(name, _invalid(s"$label is missing $name"))

  private def _string(fields: Map[String, Json], name: String, label: String): String =
    _field(fields, name, label).asString.getOrElse(_invalid(s"$label $name must be a string"))

  private def _nonempty(value: String, label: String): String = {
    if (value == null || value.isEmpty || value != value.trim) {
      _invalid(s"$label must be a non-empty exact string")
    }
    value
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _invalid(message: String): Nothing = CozyDocumentProject._descriptor_failure(message)
}
