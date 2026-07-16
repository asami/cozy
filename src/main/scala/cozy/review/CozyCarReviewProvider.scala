package cozy.review

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest

import cozy.archive.CarCmlSourceResolver
import cozy.config.CozyProjectYamlConfig
import cozy.lint.CozyCarLint
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}
import scala.collection.JavaConverters._
import scala.util.Try

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Emits the transport-neutral v1 CAR Review Provider documents from Cozy's
 * own static CAR analysis. This API deliberately has no CBD Support
 * dependency: CBD supplies the admitted target and request identity, while
 * Cozy supplies only provider-owned evidence and observations.
 */
object CozyCarReviewProvider {
  final case class Target(
    kind: String,
    organization: Option[String],
    name: String,
    version: Option[String],
    digest: String
  )

  final case class Request(
    reviewId: String,
    target: Target,
    requestDigest: String,
    limits: Limits,
    requestedCapabilities: Vector[String],
    requestedEvidenceKinds: Vector[String],
    includeRules: Vector[String],
    excludeRules: Vector[String]
  )

  final case class Limits(
    maxEvidenceItems: Int,
    maxObservations: Int,
    maxInputBytes: Long,
    timeoutMillis: Long
  )

  val schemaVersion: String = "textus.cbd.review-provider.v1"
  val providerId: String = "cozy"
  val ruleSetId: String = "cozy.car-review"
  val capabilityId: String = "cozy.car-analysis"

  def descriptor(providerVersion: String): JsObject =
    _canonical_object(Json.obj(
      "schemaVersion" -> schemaVersion,
      "documentType" -> "provider-descriptor",
      "provider" -> Json.obj("id" -> providerId, "version" -> providerVersion),
      "ruleSet" -> Json.obj("id" -> ruleSetId, "version" -> "1.0.0"),
      "supportedSchemaVersions" -> Json.arr(schemaVersion),
      "capabilities" -> Json.arr(Json.obj(
        "id" -> capabilityId,
        "version" -> "1.0",
        "evidenceKinds" -> Json.arr("car-project", "cml-model", "build", "car-package", "abi", "documentation"),
        "observationKinds" -> Json.arr("finding", "assurance", "unknown")
      )),
      "limitations" -> Json.arr(_runtime_limitation)
    ))

  def evidenceBundle(
    request: Request,
    projectroot: Path,
    providerVersion: String
  ): JsObject = {
    val root = projectroot.toAbsolutePath.normalize()
    val metadata = CozyProjectYamlConfig.loadProjectMetadata(root)
    val cml = CarCmlSourceResolver.resolve(root).toOption
    val inputbytes = _input_bytes(root, cml)
    val selected = _selected(request)
    val inputlimited = !_valid_limits(request.limits) || inputbytes > request.limits.maxInputBytes
    val rawevidence = if (selected && !inputlimited) _evidence(root, metadata, cml) else Vector.empty
    val kindselected = rawevidence.filter(x => request.requestedEvidenceKinds.isEmpty || request.requestedEvidenceKinds.contains((x \ "kind").as[String]))
    val evidence = kindselected.take(request.limits.maxEvidenceItems.max(0))
    val rawobservations = if (selected) _observations(metadata, evidence) else Vector.empty
    val selectedobservations = rawobservations.filter(x => _rule_selected((x \ "ruleId").as[String], request.includeRules, request.excludeRules))
    val observations = selectedobservations.take(request.limits.maxObservations.max(0))
    val limitations = _limitations(metadata) ++ _selection_limitations(request) ++ _limit_limitations(request.limits, inputbytes, kindselected.size, selectedobservations.size)
    val provisional = Json.obj(
      "schemaVersion" -> schemaVersion,
      "documentType" -> "evidence-bundle",
      "reviewId" -> request.reviewId,
      "target" -> _target(request.target),
      "provider" -> Json.obj("id" -> providerId, "version" -> providerVersion),
      "ruleSet" -> Json.obj("id" -> ruleSetId, "version" -> "1.0.0"),
      "requestDigest" -> request.requestDigest,
      "evidence" -> JsArray(evidence),
      "observations" -> JsArray(observations),
      "limitations" -> JsArray(limitations)
    )
    _canonical_object(provisional + ("bundleDigest" -> JsString(_digest(provisional))))
  }

  def bundleDigest(bundle: JsObject): String =
    _digest(bundle - "bundleDigest")

  /**
   * Admits the provider-request JSON at Cozy's public boundary.  The request
   * remains a CBD-owned contract, but Cozy validates the fields it consumes
   * and recomputes the binding digest before running local analysis.
   */
  def request(value: String): Either[String, Request] =
    Try(Json.parse(value).as[JsObject]).toEither.left.map(_ => "provider-request-invalid-json").flatMap { json =>
      for {
        _ <- _required_string(json, "schemaVersion").filter(_ == schemaVersion).toRight("provider-request-schema-invalid")
        _ <- _required_string(json, "documentType").filter(_ == "provider-request").toRight("provider-request-document-type-invalid")
        reviewid <- _required_string(json, "reviewId").toRight("provider-request-review-id-missing")
        targetjson <- (json \ "target").asOpt[JsObject].toRight("provider-request-target-missing")
        target <- _target_request(targetjson)
        limitsjson <- (json \ "limits").asOpt[JsObject].toRight("provider-request-limits-missing")
        limits <- _limits_request(limitsjson)
        capabilities <- _string_vector(json, "requestedCapabilities")
        kinds <- _string_vector(json, "requestedEvidenceKinds")
        rules <- (json \ "rules").asOpt[JsObject].toRight("provider-request-rules-missing")
        include <- _string_vector(rules, "include")
        exclude <- _string_vector(rules, "exclude")
      } yield Request(reviewid, target, _digest(_canonical_object(json)), limits, capabilities, kinds, include, exclude)
    }

  /**
   * Executes the neutral provider boundary from a parsed v1 request.  Neither
   * the request nor this result has any dependency on CBD Support classes.
   */
  def execute(
    providerRequest: String,
    projectroot: Path,
    providerVersion: String
  ): Either[String, JsObject] =
    request(providerRequest).map(evidenceBundle(_, projectroot, providerVersion))

  private def _evidence(
    root: Path,
    metadata: CozyProjectYamlConfig.Config,
    cml: Option[CarCmlSourceResolver.Resolved]
  ): Vector[JsObject] = {
    val component = metadata.value("project.component.name").orElse(metadata.value("project.name")).getOrElse("unknown-component")
    val projectkind = metadata.value("project.kind").getOrElse("unknown")
    val cncfversions = _cncf_versions(metadata)
    val project = _evidence_item(
      "evidence-project-yaml",
      "car-project",
      "file",
      "project.yaml",
      Some("project.yaml"),
      Json.obj("projectKind" -> projectkind, "componentName" -> component, "supportedCncfVersions" -> JsArray(cncfversions.map(JsString)))
    )
    val model = cml.toVector.map { source =>
      _evidence_item(
        "evidence-cml-model",
        "cml-model",
        "component",
        component,
        Some(source.projectrelativepath),
        Json.obj("modelFormat" -> "cml", "componentName" -> component)
      )
    }
    val build = _file_evidence(root, "build.sbt", "build", "evidence-build")
    val packageevidence = _car_package_evidence(root, component)
    val lint = CozyCarLint.lint(root, None, noabi = false).zipWithIndex.map { case (finding, index) =>
      _evidence_item(
        s"evidence-lint-${index + 1}",
        _lint_kind(finding.category),
        "lint-rule",
        finding.code,
        Some(_relative(root, finding.path)),
        Json.obj(
          "category" -> finding.category,
          "code" -> finding.code,
          "level" -> finding.level.name,
          "line" -> finding.line,
          "message" -> finding.message
        )
      )
    }
    (Vector(project) ++ model ++ build.toVector ++ packageevidence ++ lint).sortBy(x => (x \ "id").as[String])
  }

  private def _observations(
    metadata: CozyProjectYamlConfig.Config,
    evidence: Vector[JsObject]
  ): Vector[JsObject] = {
    val component = metadata.value("project.component.name").orElse(metadata.value("project.name")).getOrElse("unknown-component")
    val lint = evidence.filter(x => (x \ "subject" \ "kind").as[String] == "lint-rule")
    val findings = lint.collect {
      case item if (item \ "facts" \ "level").as[String] == "FAIL" =>
        _observation(
          s"observation-${(item \ "subject" \ "id").as[String]}",
          "finding",
          (item \ "subject" \ "id").as[String],
          component,
          (item \ "facts" \ "message").as[String],
          Vector((item \ "id").as[String]),
          Some("high")
        )
    }
    val identity =
      if (evidence.exists(x => (x \ "id").as[String] == "evidence-cml-model"))
        Vector(_observation("observation-component-identity", "assurance", "cozy.car.identity-input", component, "Project metadata and CML source are available for Cozy analysis.", Vector("evidence-project-yaml", "evidence-cml-model"), None))
      else
        Vector(_observation("observation-component-model-unknown", "unknown", "cozy.car.cml-source", component, "No admitted CML source is available for Cozy analysis.", Vector("evidence-project-yaml"), None))
    val runtime = Vector(_observation("observation-runtime-unknown", "unknown", "cozy.car.runtime-evidence", component, "No admitted runtime evidence is available from Cozy static analysis.", Vector.empty, None))
    (findings ++ identity ++ runtime).sortBy(x => (x \ "id").as[String])
  }

  private def _file_evidence(root: Path, relative: String, kind: String, id: String): Option[JsObject] = {
    val path = root.resolve(relative)
    if (Files.isRegularFile(path))
      Some(_evidence_item(id, kind, "file", relative, Some(relative), Json.obj("present" -> true)))
    else
      None
  }

  private def _car_package_evidence(root: Path, component: String): Vector[JsObject] = {
    _car_archives(root).zipWithIndex.map { case (path, index) =>
      _evidence_item(s"evidence-car-package-${index + 1}", "car-package", "car", component, Some(_relative(root, path)), Json.obj("present" -> true, "archiveName" -> path.getFileName.toString))
    }
  }

  private def _car_archives(root: Path): Vector[Path] = {
    val target = root.resolve("target")
    if (!Files.isDirectory(target))
      Vector.empty
    else {
      val stream = Files.list(target)
      try {
        stream.iterator().asScala
          .filter(path => Files.isRegularFile(path) && path.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(".car"))
          .toVector
          .sortBy(_.getFileName.toString)
      } finally {
        stream.close()
      }
    }
  }

  private def _evidence_item(id: String, kind: String, subjectkind: String, subjectid: String, path: Option[String], facts: JsObject): JsObject = {
    val locationrecord = path.filter(_.nonEmpty).map(x => Json.obj("location" -> Json.obj("path" -> x))).getOrElse(Json.obj())
    _canonical_object(Json.obj(
      "id" -> id,
      "kind" -> kind,
      "subject" -> Json.obj("kind" -> subjectkind, "id" -> subjectid),
      "origin" -> Json.obj("providerId" -> providerId, "sourceType" -> "file"),
      "facts" -> facts
    ) ++ locationrecord)
  }

  private def _observation(id: String, kind: String, ruleid: String, component: String, message: String, evidenceids: Vector[String], severity: Option[String]): JsObject = {
    val severityvalue = severity.map(x => Json.obj("severity" -> x)).getOrElse(Json.obj())
    _canonical_object(Json.obj(
      "id" -> id,
      "type" -> kind,
      "ruleId" -> ruleid,
      "subject" -> Json.obj("kind" -> "component", "id" -> component),
      "message" -> message,
      "confidence" -> "high",
      "evidenceIds" -> JsArray(evidenceids.map(JsString))
    ) ++ severityvalue)
  }

  private def _target(target: Target): JsObject =
    _canonical_object(Json.obj("kind" -> target.kind, "name" -> target.name, "digest" -> target.digest) ++ target.organization.map(x => Json.obj("organization" -> x)).getOrElse(Json.obj()) ++ target.version.map(x => Json.obj("version" -> x)).getOrElse(Json.obj()))

  private def _runtime_limitation: JsObject =
    Json.obj("code" -> "runtime-evidence-not-supported", "scope" -> "capability", "subjectId" -> capabilityId, "message" -> "Cozy static analysis does not supply runtime evidence.", "retryable" -> false)

  private def _limitations(metadata: CozyProjectYamlConfig.Config): Vector[JsObject] =
    Vector(_runtime_limitation) ++
      (if (_cncf_versions(metadata).nonEmpty) Vector.empty else Vector(Json.obj("code" -> "cncf-version-not-declared", "scope" -> "target", "message" -> "CAR project does not declare a supported CNCF version.", "retryable" -> false)))

  private def _cncf_versions(metadata: CozyProjectYamlConfig.Config): Vector[String] =
    (metadata.value("packaging.car.runtime.cncf.minimum").toVector ++ metadata.list("packaging.car.runtime.cncf.tested")).distinct.sorted

  private def _input_bytes(root: Path, cml: Option[CarCmlSourceResolver.Resolved]): Long = {
    val paths =
      Vector(root.resolve("project.yaml"), root.resolve("build.sbt"), root.resolve("README.md")) ++
        cml.toVector.map(_.source) ++
        _files_under(root.resolve("src/main/cozy")) ++
        _files_under(root.resolve("src/main/car")) ++
        _files_under(root.resolve("docs")) ++
        _car_archives(root)
    paths.distinct.filter(Files.isRegularFile(_)).foldLeft(0L) { (z, path) =>
      try Math.addExact(z, Files.size(path))
      catch { case _: ArithmeticException => Long.MaxValue }
    }
  }

  private def _files_under(path: Path): Vector[Path] =
    if (!Files.isDirectory(path))
      Vector.empty
    else {
      val stream = Files.walk(path, 12)
      try stream.iterator().asScala.filter(Files.isRegularFile(_)).toVector.sortBy(_.toString)
      finally stream.close()
    }

  private def _limit_limitations(limits: Limits, inputbytes: Long, evidencecount: Int, observationcount: Int): Vector[JsObject] = {
    val invalid =
      if (_valid_limits(limits)) Vector.empty
      else Vector(Json.obj("code" -> "provider-invalid-limits", "scope" -> "provider", "message" -> "Provider request limits must be positive.", "retryable" -> false))
    val input =
      if (_valid_limits(limits) && inputbytes > limits.maxInputBytes) Vector(Json.obj("code" -> "provider-input-byte-limit", "scope" -> "target", "message" -> "Cozy static input exceeds the admitted byte limit.", "retryable" -> false))
      else Vector.empty
    val evidence =
      if (_valid_limits(limits) && evidencecount > limits.maxEvidenceItems) Vector(Json.obj("code" -> "provider-evidence-limit", "scope" -> "evidence", "message" -> "Cozy evidence was truncated at the admitted item limit.", "retryable" -> false))
      else Vector.empty
    val observations =
      if (_valid_limits(limits) && observationcount > limits.maxObservations) Vector(Json.obj("code" -> "provider-observation-limit", "scope" -> "observation", "message" -> "Cozy observations were truncated at the admitted item limit.", "retryable" -> false))
      else Vector.empty
    invalid ++ input ++ evidence ++ observations
  }

  private def _valid_limits(limits: Limits): Boolean =
    limits.maxEvidenceItems > 0 && limits.maxObservations > 0 && limits.maxInputBytes > 0 && limits.timeoutMillis > 0

  private def _selected(request: Request): Boolean =
    request.requestedCapabilities.distinct == request.requestedCapabilities &&
      request.requestedCapabilities.contains(capabilityId) &&
      request.includeRules.distinct == request.includeRules &&
      request.excludeRules.distinct == request.excludeRules &&
      request.includeRules.intersect(request.excludeRules).isEmpty

  private def _selection_limitations(request: Request): Vector[JsObject] = {
    val capability =
      if (request.requestedCapabilities.contains(capabilityId)) Vector.empty
      else Vector(Json.obj("code" -> "provider-capability-not-requested", "scope" -> "capability", "subjectId" -> capabilityId, "message" -> "Cozy CAR analysis was not requested.", "retryable" -> false))
    val kinds = request.requestedEvidenceKinds.distinct.filterNot(_supported_evidence_kinds.contains).map { kind =>
      Json.obj("code" -> "provider-evidence-kind-not-supported", "scope" -> "capability", "subjectId" -> kind, "message" -> s"Cozy does not provide requested evidence kind: ${kind}.", "retryable" -> false)
    }
    val rules =
      if (request.includeRules.distinct == request.includeRules && request.excludeRules.distinct == request.excludeRules && request.includeRules.intersect(request.excludeRules).isEmpty) Vector.empty
      else Vector(Json.obj("code" -> "provider-rule-selection-invalid", "scope" -> "rule", "message" -> "Provider rule selectors must be distinct and non-conflicting.", "retryable" -> false))
    capability ++ kinds ++ rules
  }

  private def _rule_selected(ruleid: String, include: Vector[String], exclude: Vector[String]): Boolean =
    (include.isEmpty || include.exists(_rule_matches(_, ruleid))) && !exclude.exists(_rule_matches(_, ruleid))

  private def _rule_matches(selector: String, ruleid: String): Boolean =
    if (selector.endsWith(".*")) ruleid.startsWith(selector.dropRight(1)) else selector == ruleid

  private val _supported_evidence_kinds = Set("car-project", "cml-model", "build", "car-package", "abi", "documentation")

  private def _lint_kind(category: String): String =
    category match {
      case "build" => "build"
      case "abi" => "abi"
      case "documentation" => "documentation"
      case "cml" => "cml-model"
      case _ => "car-project"
    }

  private def _relative(root: Path, path: Path): String =
    root.relativize(path.toAbsolutePath.normalize()).iterator().asScala.map(_.toString).mkString("/")

  private def _target_request(value: JsObject): Either[String, Target] =
    for {
      kind <- _required_string(value, "kind").toRight("provider-request-target-kind-missing")
      name <- _required_string(value, "name").toRight("provider-request-target-name-missing")
      digest <- _required_string(value, "digest").toRight("provider-request-target-digest-missing")
    } yield Target(kind, _optional_string(value, "organization"), name, _optional_string(value, "version"), digest)

  private def _limits_request(value: JsObject): Either[String, Limits] =
    for {
      evidence <- (value \ "maxEvidenceItems").asOpt[Int].filter(_ > 0).toRight("provider-request-evidence-limit-invalid")
      observations <- (value \ "maxObservations").asOpt[Int].filter(_ > 0).toRight("provider-request-observation-limit-invalid")
      inputbytes <- (value \ "maxInputBytes").asOpt[Long].filter(_ > 0).toRight("provider-request-input-byte-limit-invalid")
      timeout <- (value \ "timeoutMillis").asOpt[Long].filter(_ > 0).toRight("provider-request-timeout-invalid")
    } yield Limits(evidence, observations, inputbytes, timeout)

  private def _string_vector(value: JsObject, name: String): Either[String, Vector[String]] =
    (value \ name).asOpt[Vector[String]].filter(_.forall(_.trim.nonEmpty)).toRight(s"provider-request-$name-invalid")

  private def _required_string(value: JsObject, name: String): Option[String] =
    (value \ name).asOpt[String].map(_.trim).filter(_.nonEmpty)

  private def _optional_string(value: JsObject, name: String): Option[String] =
    (value \ name).asOpt[String].map(_.trim).filter(_.nonEmpty)

  private def _digest(value: JsObject): String = {
    val root = _canonical(value).as[JsObject] - "bundleDigest"
    val bytes = _canonical_json(root).getBytes(StandardCharsets.UTF_8)
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString
  }

  private def _canonical_object(value: JsObject): JsObject = _canonical(value).as[JsObject]

  private def _canonical(value: JsValue): JsValue =
    value match {
      case objectvalue: JsObject => JsObject(objectvalue.fields.sortBy(_._1).map { case (key, item) => key -> _canonical(item) })
      case JsArray(values) => JsArray(values.map(_canonical))
      case other => other
    }

  private def _canonical_json(value: JsValue): String =
    value match {
      case objectvalue: JsObject => objectvalue.fields.toVector.sortBy(_._1).map { case (key, item) => s"${Json.stringify(JsString(key))}:${_canonical_json(item)}" }.mkString("{", ",", "}")
      case JsArray(values) => values.map(_canonical_json).mkString("[", ",", "]")
      case other => Json.stringify(other)
    }
}
