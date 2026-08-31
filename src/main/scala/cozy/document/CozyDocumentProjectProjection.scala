package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectProjection {
  private final case class ProjectionProduct(
    value: CozyDocumentWorkflow.ResolvedWorkProduct,
    coverage: String,
    currentness: String,
    review: String,
    readiness: String,
    reason: Option[String]
  )

  private[cozy] def dashboardHtml(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    val products = _projection_products(project, descriptor)
    val definition = CozyDocumentWorkflow.documentProduction
    val workflowrows = products.map { item =>
      val product = item.value.workProduct
      val binding = item.value.binding
      val branch = if (binding.disposition == CozyDocumentWorkflow.WorkProductDisposition.Disabled) "omitted" else "active"
      s"""<tr><th scope="row">${_html_escape(product.id)}<br/><span>${_html_escape(product.label)}</span></th><td>${_html_escape(branch)}</td><td>${_html_escape(product.role.value)}</td><td>${_html_escape(binding.disposition.value)}</td><td>${_html_escape(_provider_for(product, definition))}</td><td>${_html_list(product.gates)}</td><td>${_html_escape(item.reason.getOrElse(""))}</td></tr>"""
    }.mkString("\n")
    val matrixrows = products.map { item =>
      val product = item.value.workProduct
      s"""<tr><th scope="row">${_html_escape(product.id)}</th><td>${_html_escape(item.coverage)}</td><td>${_html_escape(item.currentness)}</td><td>${_html_escape(item.review)}</td><td>${_html_escape(item.readiness)}</td><td>${_html_escape(item.reason.getOrElse(""))}</td></tr>"""
    }.mkString("\n")
    val detailrows = products.map { item =>
      val product = item.value.workProduct
      s"""<tr><th scope="row">${_html_escape(product.id)}</th><td>${_html_list(product.dependencies)}</td><td>${_html_escape(product.producer)}</td><td>${_html_list(product.consumers)}</td><td>${_html_list(product.evidenceReferences)}</td><td>${_html_escape(_next_operation(item))}</td></tr>"""
    }.mkString("\n")
    val coreentries = _core_entries(project, descriptor)
    val coreentryrows = if (coreentries.isEmpty) "<tr><td colspan=\"2\">none accepted</td></tr>" else coreentries.map { case (id, text) =>
      s"""<tr><th scope="row">${_html_escape(id)}</th><td>${_html_escape(text)}</td></tr>"""
    }.mkString("\n")
    val attempts = _retained_attempts(project)
    val attemptrows = if (attempts.isEmpty) "<tr><td colspan=\"2\">none retained</td></tr>" else attempts.map { path =>
      s"""<tr><th scope="row">${_html_escape(CozyDocumentProject._project_relative(project, path))}</th><td>historical attempt; no receipt/currentness authority</td></tr>"""
    }.mkString("\n")
    _html_page(
      s"Cozy Document Project Dashboard - ${descriptor.id}",
      descriptor.language,
      s"""<h1>Cozy Document Project Dashboard</h1>
         |<p>Project: <code>${_html_escape(descriptor.id)}</code>; profile: <code>${_html_escape(descriptor.profile)}</code>; workspace: <code>${_html_escape(descriptor.workspace)}</code>; schema: <code>cozy.document-project.v1</code>.</p>
         |<p class="notice">Current snapshot is derived from admitted authored sources and the closed workflow. Retained attempts are historical evidence only; an initial attempt has no receipt or currentness authority.</p>
         |<h2>Workflow</h2>
         |<table aria-label="Workflow Work Products"><thead><tr><th scope="col">Work Product</th><th scope="col">Branch (active/omitted)</th><th scope="col">Role</th><th scope="col">Disposition</th><th scope="col">Provider</th><th scope="col">Gates</th><th scope="col">Omitted or blocking reason</th></tr></thead><tbody>$workflowrows</tbody></table>
         |<h2>Work Product matrix</h2>
         |<table aria-label="Work Product status matrix"><thead><tr><th scope="col">Work Product</th><th scope="col">Coverage</th><th scope="col">Currentness</th><th scope="col">Review</th><th scope="col">readiness</th><th scope="col">Omitted or blocking reason</th></tr></thead><tbody>$matrixrows</tbody></table>
         |<h2>Work Product details</h2>
         |<table aria-label="Work Product details"><thead><tr><th scope="col">Work Product</th><th scope="col">Dependencies</th><th scope="col">Producer operation</th><th scope="col">Consumer operations</th><th scope="col">Evidence references</th><th scope="col">Next operation</th></tr></thead><tbody>$detailrows</tbody></table>
         |<h2>Accepted Core entries</h2>
         |<table aria-label="Accepted Core entries"><thead><tr><th scope="col">Entry ID</th><th scope="col">Text</th></tr></thead><tbody>$coreentryrows</tbody></table>
         |<h2>Retained attempts</h2>
         |<table aria-label="Retained operation attempts"><thead><tr><th scope="col">Attempt</th><th scope="col">Authority boundary</th></tr></thead><tbody>$attemptrows</tbody></table>
         |<p class="notice">This dashboard is a deterministic, read-only projection. It does not execute providers or persist candidates, feedback, acceptance, receipts, deliverables, or workflow status.</p>""".stripMargin
    )
  }

  private[cozy] def coreReviewHtml(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    val entries = _core_entries(project, descriptor)
    val entryrows = if (entries.isEmpty)
      s"""<tr><td colspan="2">${_html_escape("No accepted Core entries are present.")}</td></tr>"""
    else entries.map { case (id, text) =>
      s"""<tr><th scope="row">${_html_escape(id)}</th><td>${_html_escape(text)}</td></tr>"""
    }.mkString("\n")
    val content =
      s"""<table aria-label="Accepted Core entries"><thead><tr><th scope="col">Entry ID</th><th scope="col">Text</th></tr></thead><tbody>$entryrows</tbody></table>"""
    _html_page(
      s"Cozy Document Project Core Review - ${descriptor.id}",
      descriptor.language,
      s"""<h1>Core Review</h1>
         |<p>Project: <code>${_html_escape(descriptor.id)}</code>; Content Core: <code>${_html_escape(descriptor.contentCore)}</code>; schema: <code>cozy.document-project.v1</code>.</p>
         |<h2>Accepted Core entries</h2>
         |$content
         |<h2>Candidate, feedback, and acceptance surface</h2>
         |<p class="notice">Non-authoritative and not yet persisted. This projection presents accepted Core entries only; it does not persist a candidate, feedback, or acceptance decision.</p>
         |<p>No provider execution, Core write-back, receipt, or state-cache update is performed.</p>""".stripMargin
    )
  }

  private[cozy] def logicalChartHtml(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    val resolved = CozyDocumentWorkflow.resolve(descriptor.profile) match {
      case Right(value) => value
      case Left(cause) => CozyDocumentProject._descriptor_failure(cause)
    }
    val logicalchart = resolved.workProducts.find(_.workProduct.id == "explanation-structure-review-html") match {
      case Some(value) if value.binding.disposition != CozyDocumentWorkflow.WorkProductDisposition.Disabled => value.workProduct
      case Some(value) => CozyDocumentProject._failure("DP-OP-001", s"logical chart Work Product ${value.workProduct.id} is disabled for profile ${descriptor.profile}")
      case None => CozyDocumentProject._descriptor_failure("document-production Work Product is missing: explanation-structure-review-html")
    }
    val entries = _core_entries(project, descriptor)
    val visualpages = _read_projection_source(CozyDocumentProject._direct_file(project, "presentation/visual-pages.yaml", "Visual Page source"))
    val storyboard = if (descriptor.profile == "standard-video")
      Some(_read_projection_source(CozyDocumentProject._direct_file(project, "video/storyboard.md", "video storyboard")))
    else None
    val entryrows = if (entries.isEmpty)
      s"""<tr><td colspan="2">${_html_escape("No accepted Core entries are present.")}</td></tr>"""
    else entries.map { case (id, text) =>
      s"""<tr><th scope="row">${_html_escape(id)}</th><td>${_html_escape(text)}</td></tr>"""
    }.mkString("\n")
    val storyboardsection = storyboard match {
      case Some(content) => _source_projection_table("Video storyboard IR source", "video/storyboard.md", content)
      case None => "<p class=\"notice\">Omitted: profile standard disables video branch</p>"
    }
    _html_page(
      "Logical Chart",
      descriptor.language,
      s"""<h1>Logical Chart</h1>
         |<p>Project: <code>${_html_escape(descriptor.id)}</code>; profile: <code>${_html_escape(descriptor.profile)}</code>; Content Core IR: <code>${_html_escape(descriptor.contentCore)}</code>.</p>
         |<h2>Logical Chart Work Product</h2>
         |<p>Work Product: <code>${_html_escape(logicalchart.id)}</code>; label: <span>${_html_escape(logicalchart.label)}</span>.</p>
         |<p class="notice">This deterministic, self-contained, read-only chart visualizes the current project-local IR. It is not an authority, provider run, receipt, state cache, feedback record, or write-back mechanism.</p>
         |<h2>Accepted Core entries</h2>
         |<table aria-label="Accepted Core entries"><thead><tr><th scope="col">Entry ID</th><th scope="col">Text</th></tr></thead><tbody>$entryrows</tbody></table>
         |<h2>Visual Page IR source</h2>
         |${_source_projection_table("Visual Page IR source", "presentation/visual-pages.yaml", visualpages)}
         |<h2>Video storyboard IR</h2>
         |$storyboardsection
         |<p class="notice">The chart uses no external Phase-41 input. No provider, receipt, state cache, feedback record, or authored-source write-back is performed.</p>""".stripMargin
    )
  }

  private[cozy] def videoReviewHtml(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    _require_video_review(descriptor)
    val storyboard = _read_projection_source(CozyDocumentProject._direct_file(project, "video/storyboard.md", "video storyboard"))
    val visualpages = _read_projection_source(CozyDocumentProject._direct_file(project, "presentation/visual-pages.yaml", "Visual Page source"))
    val storyboardtable =
      s"""<table aria-label="Storyboard source projection"><thead><tr><th scope="col">Source</th><th scope="col">Content</th></tr></thead><tbody><tr><th scope="row">${_html_escape("video/storyboard.md")}</th><td><pre><code>$storyboard</code></pre></td></tr></tbody></table>"""
    val visualpagestable =
      s"""<table aria-label="Visual-page source projection"><thead><tr><th scope="col">Source</th><th scope="col">Content</th></tr></thead><tbody><tr><th scope="row">${_html_escape("presentation/visual-pages.yaml")}</th><td><pre><code>$visualpages</code></pre></td></tr></tbody></table>"""
    _html_page(
      s"Cozy Document Project Video Review - ${descriptor.id}",
      descriptor.language,
      s"""<h1>Video Review</h1>
         |<p>Project: <code>${_html_escape(descriptor.id)}</code>; profile: <code>${_html_escape(descriptor.profile)}</code>; schema: <code>cozy.document-project.v1</code>.</p>
         |<p class="notice">These are read-only source projections. They do not claim provider execution, candidate persistence, feedback persistence, acceptance, or video delivery.</p>
         |<h2>Storyboard source projection</h2>
         |$storyboardtable
         |<h2>Visual-page source projection</h2>
         |$visualpagestable
         |<h2>Review boundary</h2>
         |<p>The storyboard and Visual Page source authorities remain unchanged. No provider, operation, receipt, or acceptance evidence is created.</p>""".stripMargin
    )
  }

  private def _projection_products(project: Path, descriptor: CozyDocumentProject.Descriptor): Vector[ProjectionProduct] = {
    val resolved = CozyDocumentWorkflow.resolve(descriptor.profile) match {
      case Right(value) => value
      case Left(cause) => CozyDocumentProject._descriptor_failure(cause)
    }
    val sourcepaths = CozyDocumentProject._state_sources(project, descriptor).map(_._1).toSet
    val coreaccepted = CozyDocumentProject._core_has_accepted_entries(project, descriptor)
    val sourceproducts = Map(
      "content-core" -> (sourcepaths.contains(descriptor.contentCore), coreaccepted),
      "article-source" -> (sourcepaths.contains("index.dox"), sourcepaths.contains("index.dox")),
      "infographic-svg" -> (sourcepaths.contains("infographic/infographic.svg"), sourcepaths.contains("infographic/infographic.svg")),
      "video-storyboard" -> (sourcepaths.contains("video/storyboard.md"), sourcepaths.contains("video/storyboard.md")),
      "explanation-structure-review-html" -> {
        val chartinputs = Vector(descriptor.contentCore, "presentation/visual-pages.yaml") ++ (if (descriptor.profile == "standard-video") Vector("video/storyboard.md") else Vector.empty)
        val chartavailable = chartinputs.forall(sourcepaths.contains)
        (chartavailable, chartavailable)
      }
    )
    resolved.workProducts.map { value =>
      val product = value.workProduct
      val binding = value.binding
      val disabled = binding.disposition == CozyDocumentWorkflow.WorkProductDisposition.Disabled
      val sourcepresent = sourceproducts.get(product.id).map(_._1).getOrElse(false)
      val sourcecovered = sourceproducts.get(product.id).map(_._2).getOrElse(false)
      val coverage = if (disabled) "not-applicable" else if (sourcecovered) "satisfied" else "missing"
      val currentness = if (disabled) "not-applicable" else if (sourcepresent) "current" else "missing"
      val readiness = if (disabled) "omitted" else if (sourcepresent) "ready" else "blocked"
      val reason = binding.reason.orElse(if (readiness == "blocked") Some("source or retained evidence is not present") else None)
      ProjectionProduct(value, coverage, currentness, "pending", readiness, reason)
    }
  }

  private def _provider_for(value: CozyDocumentWorkflow.WorkProduct, definition: CozyDocumentWorkflow.WorkflowDefinition): String =
    definition.operations.find(_.id == value.producer).map { operation =>
      definition.providerBindings.find(_.id == operation.providerBinding).map { binding =>
        s"${binding.id} (${binding.provider})"
      }.getOrElse(operation.providerBinding)
    }.getOrElse("unbound")

  private def _next_operation(product: ProjectionProduct): String = {
    if (product.readiness == "omitted") "none (omitted)"
    else if (product.readiness == "blocked" || product.coverage != "satisfied" || product.currentness != "current") product.value.workProduct.producer
    else "none (projection is current)"
  }

  private def _require_video_review(descriptor: CozyDocumentProject.Descriptor): Unit = {
    val resolved = CozyDocumentWorkflow.resolve(descriptor.profile) match {
      case Right(value) => value
      case Left(cause) => CozyDocumentProject._descriptor_failure(cause)
    }
    val activeproducts = resolved.workProducts.collect {
      case value if value.binding.disposition != CozyDocumentWorkflow.WorkProductDisposition.Disabled => value.workProduct.id
    }.toSet
    val operation = CozyDocumentWorkflow.declaredOperation("video.render-review") match {
      case Right(Some(value)) => value
      case Right(None) => CozyDocumentProject._failure("DP-OP-001", "undeclared logical operation: video.render-review")
      case Left(cause) => CozyDocumentProject._descriptor_failure(cause)
    }
    if (!operation.produces.exists(activeproducts.contains))
      CozyDocumentProject._failure("DP-OP-001", s"logical operation video.render-review is disabled for profile ${descriptor.profile}")
  }

  private def _core_entries(project: Path, descriptor: CozyDocumentProject.Descriptor): Vector[(String, String)] = {
    val accepted = CozyDocumentProject._load_json(project.resolve(descriptor.contentCore), "Content Core").hcursor.downField("accepted").focus.flatMap(_.asArray).getOrElse(Vector.empty)
    accepted.map { entry =>
      val cursor = entry.hcursor
      (cursor.downField("id").as[String].getOrElse(""), cursor.downField("text").as[String].getOrElse(""))
    }
  }

  private def _read_projection_source(path: Path): String =
    try _html_escape(Files.readString(path, StandardCharsets.UTF_8))
    catch { case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "review source cannot be read") }

  private def _source_projection_table(aria: String, path: String, content: String): String =
    s"""<table aria-label="${_html_escape(aria)}"><thead><tr><th scope="col">Source</th><th scope="col">Content</th></tr></thead><tbody><tr><th scope="row">${_html_escape(path)}</th><td><pre><code>$content</code></pre></td></tr></tbody></table>"""

  private def _retained_attempts(project: Path): Vector[Path] = {
    val evidence = project.resolve("evidence").normalize()
    val directory = project.resolve("evidence").resolve("attempts").normalize()
    if (Files.isSymbolicLink(evidence) || (Files.exists(evidence, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(evidence, LinkOption.NOFOLLOW_LINKS)))
      CozyDocumentProject._failure("DP-PATH-001", "evidence directory must be a direct non-symlink directory")
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Vector.empty
    else {
      CozyDocumentProject._direct_directory(evidence, "evidence directory")
      CozyDocumentProject._direct_directory(directory, "attempts directory")
      val stream = Files.list(directory)
      try stream.iterator().asScala.toVector.sortBy(_.toString).map { path =>
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          CozyDocumentProject._failure("DP-PATH-001", "retained attempt must be a direct regular non-symlink file")
        path
      }
      finally stream.close()
    }
  }

  private[cozy] def projectionResult(kind: String, project: Path, descriptor: CozyDocumentProject.Descriptor, destination: Path): String = {
    val reference = if (destination.startsWith(project)) CozyDocumentProject._project_relative(project, destination) else destination.toString
    s"Cozy Document Project $kind\nproject: ${descriptor.id}\nprofile: ${descriptor.profile}\nschema: cozy.document-project.v1\noutput: $reference"
  }

  private def _html_page(title: String, language: String, body: String): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(language)}">
       |<head><meta charset="UTF-8"><title>${_html_escape(title)}</title><style>body{font-family:system-ui,sans-serif;line-height:1.45;margin:2rem;color:#202124}table{border-collapse:collapse;width:100%;margin:1rem 0 2rem}th,td{border:1px solid #9aa0a6;padding:.45rem;text-align:left;vertical-align:top}th{background:#f1f3f4}.notice{background:#fff8e1;border-left:.3rem solid #f9ab00;padding:.7rem}pre{background:#f8f9fa;border:1px solid #dadce0;padding:1rem;overflow:auto}code{font-family:ui-monospace,monospace}</style></head>
       |<body>$body</body>
       |</html>
       |""".stripMargin

  private def _html_list(values: Seq[String]): String =
    if (values.isEmpty) "none" else values.map(_html_escape).mkString("<br/>")

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

  private[cozy] def admitDestination(project: Path, requested: Option[String], defaultName: String): Path = {
    val destination = try requested.map(Paths.get(_)).getOrElse(project.resolve("target").resolve("document-project").resolve(defaultName)).toAbsolutePath.normalize() catch {
      case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "projection output path is invalid")
    }
    val projectlocal = destination.startsWith(project)
    val projectionroot = project.resolve("target").resolve("document-project").normalize()
    if (projectlocal && (!destination.startsWith(projectionroot) || !destination.getFileName.toString.endsWith(".html")))
      CozyDocumentProject._failure("DP-PATH-001", "project-internal projection output must be under target/document-project and end with .html")
    if (Files.isSymbolicLink(destination) || (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)))
      CozyDocumentProject._failure("DP-PATH-001", "projection output destination must be a direct regular file or absent")
    val parent = Option(destination.getParent).getOrElse(CozyDocumentProject._failure("DP-PATH-001", "projection output has no safe parent"))
    _admit_projection_parent(parent)
    destination
  }

  private def _admit_projection_parent(parent: Path): Unit = {
    val missing = scala.collection.mutable.ArrayBuffer.empty[Path]
    var current: Option[Path] = Some(parent)
    while (current.nonEmpty && !Files.exists(current.get, LinkOption.NOFOLLOW_LINKS)) {
      val path = current.get
      if (Files.isSymbolicLink(path))
        CozyDocumentProject._failure("DP-PATH-001", "projection output parent must not contain a symbolic link")
      missing += path
      current = Option(path.getParent)
    }
    current match {
      case Some(path) if !Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) =>
        ()
      case Some(path) if Files.isSymbolicLink(path) =>
        CozyDocumentProject._failure("DP-PATH-001", "projection output parent must not contain a symbolic link")
      case Some(_) =>
        CozyDocumentProject._failure("DP-PATH-001", "projection output parent must be a direct directory")
      case None =>
        CozyDocumentProject._failure("DP-PATH-001", "projection output has no safe parent")
    }
    missing.reverseIterator.foreach { path =>
      try Files.createDirectory(path) catch { case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "projection output parent cannot be created safely") }
      if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        CozyDocumentProject._failure("DP-PATH-001", "projection output parent must be a direct directory")
    }
  }

  private[cozy] def publish(destination: Path, content: String): Unit = {
    var temporary: Option[Path] = None
    try {
      _admit_projection_parent(destination.getParent)
      if (Files.isSymbolicLink(destination) || (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)))
        CozyDocumentProject._failure("DP-PATH-001", "projection output destination must be a direct regular file")
      val temporaryfile = Files.createTempFile(destination.getParent, s".${destination.getFileName}-", ".tmp")
      temporary = Some(temporaryfile)
      Files.writeString(temporaryfile, content, StandardCharsets.UTF_8)
      if (Files.isSymbolicLink(destination) || (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)))
        CozyDocumentProject._failure("DP-PATH-001", "projection output destination became unsafe")
      Files.move(temporaryfile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      temporary = None
    } catch {
      case _: AtomicMoveNotSupportedException => CozyDocumentProject._failure("DP-PATH-001", "projection output requires an atomic move")
      case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "projection output cannot be published atomically")
    } finally {
      temporary.foreach(Files.deleteIfExists)
    }
  }
}
