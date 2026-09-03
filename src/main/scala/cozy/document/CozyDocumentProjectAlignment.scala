package cozy.document

import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 3, 2026
 * @version Sep.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectAlignment {
  final case class FileIdentity(path: String, identity: String, sha256: String)
  final case class Provider(id: String, operation: String, profile: String)
  final case class Review(reviewer: String, decision: String, rationale: String)
  final case class SharedInfographicUse(identity: String, consumer: String, evidence: FileIdentity)
  final case class Artifact(
    kind: String,
    locale: String,
    authority: FileIdentity,
    output: FileIdentity,
    receipt: FileIdentity,
    provider: Provider,
    renderer: String,
    review: Review,
    sharedinfographicuse: SharedInfographicUse
  )
  final case class Peer(locale: String, contentcore: String)
  final case class Parity(decision: String, peers: Vector[Peer])
  final case class ArtifactStatus(
    artifact: Artifact,
    currentness: String,
    reason: String,
    visualusecurrentness: String,
    visualusereason: String
  )
  final case class ParityStatus(decision: String, currentness: String, reason: String)
  final case class Snapshot(
    ledgerpresent: Boolean,
    coreaccepted: Boolean,
    artifacts: Vector[ArtifactStatus],
    parity: ParityStatus
  )

  private final case class Ledger(
    project: String,
    semanticscope: String,
    locale: String,
    contentcore: FileIdentity,
    sharedinfographic: FileIdentity,
    artifacts: Vector[Artifact],
    parity: Parity
  )
  private final case class ArtifactRequirement(kind: String, authoritypath: String, provider: String, operation: String, renderer: String)

  private val _schema = "cozy.content-alignment.v1"
  private val _ledger_path = "evidence/content-alignment.yaml"
  private val _top_level_keys = Vector("schema", "project", "semanticScope", "locale", "contentCore", "sharedInfographic", "artifacts", "parity")
  private val _file_identity_keys = Vector("path", "identity", "sha256")
  private val _file_identity_parent_counts = Map(
    "contentCore" -> 1,
    "sharedInfographic" -> 1,
    "authority" -> 5,
    "output" -> 5,
    "receipt" -> 5,
    "evidence" -> 5
  )
  private val _hash_pattern = "[0-9a-f]{64}".r
  private val _decisions = Set("accepted", "changes-requested", "rejected", "pending", "not-applicable")
  private val _parity_decisions = Set("accepted", "pending", "not-applicable")
  private val _requirements = Vector(
    ArtifactRequirement("article-html", "index.dox", "cozy-site", "article.publish-site", "smartdox-site"),
    ArtifactRequirement("article-pdf", "index.dox", "smartdox-rendering", "article.render-pdf", "smartdox-pdf"),
    ArtifactRequirement("summary-slides-pdf", "presentation/visual-pages.yaml", "cozy-visual-page", "summary-slides.render-pdf", "cozy-pdf"),
    ArtifactRequirement("infographic-png", "infographic/infographic.svg", "cozy-infographic", "infographic.render-png", "cozy-png"),
    ArtifactRequirement("video-deliverable", "video/storyboard.md", "cozy-video", "video.render-deliverable", "cozy-video")
  )

  private[cozy] def snapshot(project: Path, descriptor: CozyDocumentProject.Descriptor): Snapshot = {
    val ledgerpath = project.resolve(_ledger_path).normalize()
    if (!ledgerpath.startsWith(project))
      CozyDocumentProject._failure("DP-PATH-001", "content alignment ledger escapes the project")
    if (!Files.exists(ledgerpath, LinkOption.NOFOLLOW_LINKS)) {
      _admit_absent_ledger_parent(project)
      Snapshot(
        ledgerpresent = false,
        coreaccepted = CozyDocumentProject._core_has_accepted_entries(project, descriptor),
        artifacts = Vector.empty,
        parity = ParityStatus("pending", "pending", "no authored content alignment record is present")
      )
    } else {
      val ledger = _ledger(project, descriptor, ledgerpath)
      val coreaccepted = CozyDocumentProject._core_has_accepted_entries(project, descriptor)
      val corereason = _identity_reason(project, ledger.contentcore, "Content Core")
      val sharedreason = _identity_reason(project, ledger.sharedinfographic, "shared infographic")
      val artifactstatuses = ledger.artifacts.zip(_requirements).map { case (artifact, requirement) =>
        _artifact_status(project, descriptor, artifact, requirement, coreaccepted, corereason, sharedreason, ledger.sharedinfographic)
      }
      Snapshot(true, coreaccepted, artifactstatuses, _parity_status(ledger.parity))
    }
  }

  private[cozy] def dashboardHtml(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    val value = snapshot(project, descriptor)
    if (!value.ledgerpresent) {
      s"""<h2>Content alignment</h2><p>pending: ${_html_escape(value.parity.reason)}. Accepted Content Core entries: ${if (value.coreaccepted) "present" else "absent"}.</p><p class="notice">Alignment is private review evidence. It is not a public article source, site registration, publication, or delivery action.</p>"""
    } else {
      val rows = value.artifacts.map { status =>
        val artifact = status.artifact
        s"""<tr><th scope="row">${_html_escape(artifact.kind)}</th><td>${_html_escape(artifact.review.decision)}</td><td>${_html_escape(artifact.review.reviewer)}</td><td>${_html_escape(status.currentness)}</td><td>${_html_escape(status.reason)}</td><td>${_html_escape(status.visualusecurrentness)}</td><td>${_html_escape(status.visualusereason)}</td></tr>"""
      }.mkString("\n")
      s"""<h2>Content alignment</h2><p>Accepted Content Core entries: ${if (value.coreaccepted) "present" else "absent"}.</p><table aria-label="Content alignment"><thead><tr><th scope="col">Artifact</th><th scope="col">Decision</th><th scope="col">Reviewer</th><th scope="col">Currentness</th><th scope="col">Currentness reason</th><th scope="col">Shared-infographic visual use</th><th scope="col">Visual-use reason</th></tr></thead><tbody>$rows</tbody></table><h3>Locale parity</h3><p>decision: <code>${_html_escape(value.parity.decision)}</code>; state: <code>${_html_escape(value.parity.currentness)}</code>; ${_html_escape(value.parity.reason)}</p><p class="notice">Alignment is private review evidence. It is not a public article source, site registration, publication, or delivery action.</p>"""
    }
  }

  private def _ledger(project: Path, descriptor: CozyDocumentProject.Descriptor, path: Path): Ledger = {
    _direct_project_file(project, _ledger_path, "content alignment ledger")
    _require_top_level_order(path, _top_level_keys, "content alignment ledger")
    _require_file_identity_order(path, "content alignment ledger")
    val fields = _object(CozyDocumentProject._load_json(path, "content alignment ledger"), "content alignment ledger")
    if (fields.keySet != _top_level_keys.toSet)
      _invalid("content alignment ledger must have exactly schema, project, semanticScope, locale, contentCore, sharedInfographic, artifacts, parity")
    if (_string(fields, "schema", "content alignment ledger") != _schema)
      _invalid(s"content alignment ledger schema must be exactly ${_schema}")
    val projectid = _nonempty(_string(fields, "project", "content alignment ledger"), "content alignment ledger project")
    if (projectid != descriptor.id)
      _invalid("content alignment ledger project must equal the descriptor id")
    val scope = _nonempty(_string(fields, "semanticScope", "content alignment ledger"), "content alignment ledger semanticScope")
    if (scope != descriptor.semanticScope.id)
      _invalid("content alignment ledger semanticScope must equal descriptor semanticScope.id")
    val locale = _nonempty(_string(fields, "locale", "content alignment ledger"), "content alignment ledger locale")
    if (locale != descriptor.language)
      _invalid("content alignment ledger locale must equal descriptor language")
    val core = _file_identity(project, _field(fields, "contentCore", "content alignment ledger"), "content alignment Content Core")
    if (core.path != descriptor.contentCore)
      _invalid("content alignment Content Core path must equal descriptor contentCore")
    val ownvariant = descriptor.semanticScope.localeVariants.find(value => value.project == descriptor.id && value.language == descriptor.language)
      .getOrElse(_invalid("descriptor semanticScope has no self locale variant"))
    if (core.identity != ownvariant.contentCore)
      _invalid("content alignment Content Core identity must equal descriptor self locale identity")
    val infographic = _file_identity(project, _field(fields, "sharedInfographic", "content alignment ledger"), "content alignment shared infographic")
    if (infographic.path != "infographic/infographic.svg")
      _invalid("content alignment shared infographic path must be infographic/infographic.svg")
    val artifactsvalue = _field(fields, "artifacts", "content alignment ledger").asArray.getOrElse(_invalid("content alignment artifacts must be an array"))
    if (artifactsvalue.size != _requirements.size)
      _invalid("content alignment artifacts must enumerate every declared consumer exactly once")
    val artifacts = artifactsvalue.zip(_requirements).map { case (value, requirement) =>
      _artifact(project, value, requirement)
    }.toVector
    val parity = _parity(descriptor, _field(fields, "parity", "content alignment ledger"))
    Ledger(projectid, scope, locale, core, infographic, artifacts, parity)
  }

  private def _artifact(project: Path, value: Json, requirement: ArtifactRequirement): Artifact = {
    val fields = _object(value, "content alignment artifact")
    val expected = Set("kind", "locale", "authority", "output", "receipt", "provider", "renderer", "review", "sharedInfographicUse")
    if (fields.keySet != expected)
      _invalid("content alignment artifact must have exactly kind, locale, authority, output, receipt, provider, renderer, review, sharedInfographicUse")
    val kind = _string(fields, "kind", "content alignment artifact")
    if (kind != requirement.kind)
      _invalid("content alignment artifacts must follow the closed consumer order")
    val locale = _nonempty(_string(fields, "locale", s"content alignment $kind"), s"content alignment $kind locale")
    val authority = _file_identity(project, _field(fields, "authority", s"content alignment $kind"), s"content alignment $kind authority")
    val output = _file_identity(project, _field(fields, "output", s"content alignment $kind"), s"content alignment $kind output")
    val receipt = _file_identity(project, _field(fields, "receipt", s"content alignment $kind"), s"content alignment $kind receipt")
    val provider = _provider(_field(fields, "provider", s"content alignment $kind"), kind)
    val renderer = _nonempty(_string(fields, "renderer", s"content alignment $kind"), s"content alignment $kind renderer")
    val review = _review(_field(fields, "review", s"content alignment $kind"), kind)
    val use = _shared_infographic_use(project, _field(fields, "sharedInfographicUse", s"content alignment $kind"), kind)
    Artifact(kind, locale, authority, output, receipt, provider, renderer, review, use)
  }

  private def _provider(value: Json, kind: String): Provider = {
    val fields = _object(value, s"content alignment $kind provider")
    if (fields.keySet != Set("id", "operation", "profile"))
      _invalid(s"content alignment $kind provider must have exactly id, operation, profile")
    Provider(
      _nonempty(_string(fields, "id", s"content alignment $kind provider"), s"content alignment $kind provider id"),
      _nonempty(_string(fields, "operation", s"content alignment $kind provider"), s"content alignment $kind provider operation"),
      _nonempty(_string(fields, "profile", s"content alignment $kind provider"), s"content alignment $kind provider profile")
    )
  }

  private def _review(value: Json, kind: String): Review = {
    val fields = _object(value, s"content alignment $kind review")
    if (fields.keySet != Set("reviewer", "decision", "rationale"))
      _invalid(s"content alignment $kind review must have exactly reviewer, decision, rationale")
    val reviewer = _nonempty(_string(fields, "reviewer", s"content alignment $kind review"), s"content alignment $kind reviewer")
    val decision = _nonempty(_string(fields, "decision", s"content alignment $kind review"), s"content alignment $kind decision")
    if (!_decisions.contains(decision))
      _invalid(s"content alignment $kind review decision is invalid")
    val rationale = _nonempty(_string(fields, "rationale", s"content alignment $kind review"), s"content alignment $kind rationale or feedback reference")
    Review(reviewer, decision, rationale)
  }

  private def _shared_infographic_use(project: Path, value: Json, kind: String): SharedInfographicUse = {
    val fields = _object(value, s"content alignment $kind sharedInfographicUse")
    if (fields.keySet != Set("identity", "consumer", "evidence"))
      _invalid(s"content alignment $kind sharedInfographicUse must have exactly identity, consumer, evidence")
    val identity = _nonempty(_string(fields, "identity", s"content alignment $kind sharedInfographicUse"), s"content alignment $kind sharedInfographicUse identity")
    val consumer = _nonempty(_string(fields, "consumer", s"content alignment $kind sharedInfographicUse"), s"content alignment $kind sharedInfographicUse consumer")
    val evidence = _file_identity(project, _field(fields, "evidence", s"content alignment $kind sharedInfographicUse"), s"content alignment $kind visual review evidence")
    if (!evidence.path.startsWith("evidence/visual-review/"))
      _invalid(s"content alignment $kind visual review evidence must be private evidence/visual-review content")
    SharedInfographicUse(identity, consumer, evidence)
  }

  private def _parity(descriptor: CozyDocumentProject.Descriptor, value: Json): Parity = {
    val fields = _object(value, "content alignment parity")
    if (fields.keySet != Set("decision", "peers"))
      _invalid("content alignment parity must have exactly decision, peers")
    val decision = _nonempty(_string(fields, "decision", "content alignment parity"), "content alignment parity decision")
    if (!_parity_decisions.contains(decision))
      _invalid("content alignment parity decision is invalid")
    val peers = _field(fields, "peers", "content alignment parity").asArray.getOrElse(_invalid("content alignment parity peers must be an array")).map { value =>
      val peer = _object(value, "content alignment parity peer")
      if (peer.keySet != Set("locale", "contentCore"))
        _invalid("content alignment parity peer must have exactly locale, contentCore")
      Peer(
        _nonempty(_string(peer, "locale", "content alignment parity peer"), "content alignment parity peer locale"),
        _nonempty(_string(peer, "contentCore", "content alignment parity peer"), "content alignment parity peer Content Core identity")
      )
    }.toVector
    val expected = descriptor.semanticScope.localeVariants.filterNot(value => value.project == descriptor.id && value.language == descriptor.language).map(value => Peer(value.language, value.contentCore))
    if (peers != expected)
      _invalid("content alignment parity peers must exactly enumerate descriptor semanticScope peer locale identities")
    if (expected.isEmpty && decision != "not-applicable")
      _invalid("content alignment parity without peers must be not-applicable")
    if (expected.nonEmpty && decision == "not-applicable")
      _invalid("content alignment parity with declared peers must be accepted or pending")
    Parity(decision, peers)
  }

  private def _artifact_status(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    artifact: Artifact,
    requirement: ArtifactRequirement,
    coreaccepted: Boolean,
    corereason: Option[String],
    sharedreason: Option[String],
    sharedinfographic: FileIdentity
  ): ArtifactStatus = {
    val visual = _visual_use_status(project, artifact.sharedinfographicuse, sharedinfographic, sharedreason)
    val outputreason = _identity_reason(project, artifact.output, "output")
    val reasons = Vector(
      if (artifact.locale == descriptor.language) None else Some("locale changed"),
      if (artifact.authority.path == requirement.authoritypath) None else Some("locale authority path changed"),
      if (artifact.provider.profile == descriptor.profile) None else Some("profile changed"),
      if (artifact.provider.id == requirement.provider) None else Some("provider changed"),
      if (artifact.provider.operation == requirement.operation) None else Some("provider logical operation changed"),
      if (artifact.renderer == requirement.renderer) None else Some("renderer changed"),
      corereason,
      sharedreason,
      _identity_reason(project, artifact.authority, "locale authority"),
      outputreason,
      _identity_reason(project, artifact.receipt, "receipt"),
      if (visual._1 == "current") None else Some(visual._2)
    ).flatten
    val (currentness, reason) = artifact.review.decision match {
      case "accepted" if !coreaccepted => "pending" -> "Content Core has no accepted entry"
      case "accepted" if outputreason.contains("output is missing") => "missing" -> "output is missing"
      case "accepted" if reasons.nonEmpty => "stale" -> reasons.head
      case "accepted" => "current" -> "all recorded identities and human acceptance are current"
      case "pending" => "pending" -> "human review is pending"
      case "not-applicable" => "not-applicable" -> "human review is not applicable"
      case decision => "not-accepted" -> s"human review decision is $decision"
    }
    ArtifactStatus(artifact, currentness, reason, visual._1, visual._2)
  }

  private def _visual_use_status(
    project: Path,
    use: SharedInfographicUse,
    shared: FileIdentity,
    sharedreason: Option[String]
  ): (String, String) = {
    val reason =
      if (use.identity != shared.identity) Some("shared infographic identity changed")
      else sharedreason.orElse(_identity_reason(project, use.evidence, "shared infographic visual-review evidence"))
    reason match {
      case Some(value) => "stale" -> value
      case None => "current" -> "direct visual-review evidence is current"
    }
  }

  private def _parity_status(parity: Parity): ParityStatus = parity.decision match {
    case "accepted" => ParityStatus("accepted", "current", "declared locale parity is accepted; locale text is independently authored")
    case "pending" => ParityStatus("pending", "pending", "declared locale parity is pending")
    case "not-applicable" => ParityStatus("not-applicable", "not-applicable", "no peer locale is declared in the local semantic scope")
    case _ => _invalid("content alignment parity decision is invalid")
  }

  private def _identity_reason(project: Path, identity: FileIdentity, label: String): Option[String] = {
    val path = project.resolve(identity.path).normalize()
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Some(s"$label is missing")
    else if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) Some(s"$label is no longer a direct regular file")
    else if (_sha256(path) != identity.sha256) Some(s"$label SHA-256 changed")
    else None
  }

  private def _file_identity(project: Path, value: Json, label: String): FileIdentity = {
    val fields = _object(value, label)
    if (fields.keySet != Set("path", "identity", "sha256"))
      _invalid(s"$label must have exactly path, identity, sha256")
    val path = _relative_path(_nonempty(_string(fields, "path", label), s"$label path"))
    val identity = _nonempty(_string(fields, "identity", label), s"$label identity")
    val sha256 = _string(fields, "sha256", label)
    if (!_hash_pattern.pattern.matcher(sha256).matches())
      _invalid(s"$label SHA-256 must be lowercase hexadecimal")
    _admit_identity_path(project, path, label)
    FileIdentity(path, identity, sha256)
  }

  private def _admit_absent_ledger_parent(project: Path): Unit = {
    val evidence = project.resolve("evidence").normalize()
    if (Files.exists(evidence, LinkOption.NOFOLLOW_LINKS))
      CozyDocumentProject._direct_directory(evidence, "evidence directory")
  }

  private def _admit_identity_path(project: Path, relative: String, label: String): Unit = {
    val candidate = project.resolve(relative).normalize()
    if (!candidate.startsWith(project))
      CozyDocumentProject._failure("DP-PATH-001", s"$label escapes the project")
    val relativepath = project.relativize(candidate)
    var parent = project
    (0 until relativepath.getNameCount - 1).foreach { index =>
      parent = parent.resolve(relativepath.getName(index))
      if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)))
        CozyDocumentProject._failure("DP-PATH-001", s"$label parent must be a direct directory")
    }
    if (Files.isSymbolicLink(candidate) || (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)))
      CozyDocumentProject._failure("DP-PATH-001", s"$label must be a direct regular file or a missing generated output")
  }

  private def _direct_project_file(project: Path, relative: String, label: String): Path = {
    val candidate = project.resolve(relative).normalize()
    _admit_identity_path(project, relative, label)
    if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
      CozyDocumentProject._failure("DP-PATH-001", s"$label must be a direct regular file")
    candidate
  }

  private def _relative_path(value: String): String = {
    val path = try Paths.get(value) catch { case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "content alignment path is invalid") }
    if (path.isAbsolute || path.iterator().asScala.exists(part => part.toString == "." || part.toString == ".."))
      CozyDocumentProject._failure("DP-PATH-001", "content alignment path must be project-relative without dot segments")
    value
  }

  private def _require_top_level_order(path: Path, expected: Vector[String], label: String): Unit = {
    val keys = try {
      Files.readAllLines(path, StandardCharsets.UTF_8).asScala.collect {
        case line if line.nonEmpty && !line.startsWith(" ") && !line.startsWith("\t") && line.contains(":") => line.takeWhile(_ != ':').trim
      }.toVector
    } catch {
      case NonFatal(_) => _invalid(s"$label cannot be read")
    }
    if (keys != expected)
      _invalid(s"$label top-level keys must be ordered ${expected.mkString(", ")}")
  }

  private def _require_file_identity_order(path: Path, label: String): Unit = {
    val lines = try {
      Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
    } catch {
      case NonFatal(_) => _invalid(s"$label cannot be read")
    }
    val parents = lines.zipWithIndex.flatMap { case (line, index) =>
      _yaml_mapping_key(line).collect {
        case (indentation, key, value) if _file_identity_parent_counts.contains(key) && value.trim.isEmpty =>
          (index, indentation, key)
      }
    }
    val counts = parents.groupBy(_._3).map { case (key, values) => key -> values.size }
    val invalid = counts != _file_identity_parent_counts || parents.exists { case (index, indentation, _) =>
      _file_identity_child_keys(lines, index, indentation) != _file_identity_keys
    }
    if (invalid)
      _invalid(s"$label FileIdentity keys must be ordered ${_file_identity_keys.mkString(", ")}")
  }

  private def _file_identity_child_keys(lines: Vector[String], parentindex: Int, parentindentation: Int): Vector[String] = {
    val children = lines.drop(parentindex + 1).takeWhile { line =>
      val indentation = line.takeWhile(_ == ' ').length
      val content = line.drop(indentation)
      content.isEmpty || content.startsWith("#") || indentation > parentindentation
    }
    val mappings = children.flatMap(_yaml_mapping_key)
    mappings.headOption match {
      case Some((indentation, _, _)) => mappings.collect { case (candidateindentation, key, _) if candidateindentation == indentation => key }
      case None => Vector.empty
    }
  }

  private def _yaml_mapping_key(line: String): Option[(Int, String, String)] = {
    val indentation = line.takeWhile(_ == ' ').length
    val content = line.drop(indentation)
    val separator = content.indexOf(':')
    if (content.startsWith("#") || content.startsWith("- ") || separator <= 0) None
    else Some((indentation, content.take(separator), content.drop(separator + 1)))
  }

  private def _object(value: Json, label: String): Map[String, Json] =
    value.asObject.map(_.toMap).getOrElse(_invalid(s"$label must be an object"))

  private def _field(fields: Map[String, Json], name: String, label: String): Json =
    fields.getOrElse(name, _invalid(s"$label is missing $name"))

  private def _string(fields: Map[String, Json], name: String, label: String): String =
    _field(fields, name, label).asString.getOrElse(_invalid(s"$label $name must be a string"))

  private def _nonempty(value: String, label: String): String = {
    if (value.isEmpty || value != value.trim)
      _invalid(s"$label must be a non-empty exact string")
    value
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _html_escape(value: String): String = {
    val builder = new StringBuilder
    value.foreach {
      case '&' => builder.append("&amp;")
      case '<' => builder.append("&lt;")
      case '>' => builder.append("&gt;")
      case '"' => builder.append("&quot;")
      case '\'' => builder.append("&#39;")
      case character => builder.append(character)
    }
    builder.toString
  }

  private def _invalid(message: String): Nothing = CozyDocumentProject._descriptor_failure(message)
}
