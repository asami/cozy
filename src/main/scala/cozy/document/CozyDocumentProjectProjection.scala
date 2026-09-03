package cozy.document

import cozy.video.{CozyVideo, CozyVideoImplementation}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import scala.util.control.NonFatal
import org.smartdox.{Dox, Document, Paragraph, Section}
import org.smartdox.parser.Dox2Parser

/*
 * @since   Sep. 1, 2026
 * @version Sep.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectProjection {
  private[cozy] def dashboardHtml(project: Path, descriptor: CozyDocumentProject.Descriptor, dashboardDestination: Path): String = {
    val snapshot = CozyDocumentProjectEvidence.snapshot(project, descriptor)
    val alignment = CozyDocumentProjectAlignment.dashboardHtml(project, descriptor)
    val products = snapshot.products
    val definition = CozyDocumentWorkflow.documentProduction
    val workflowrows = products.map { item =>
      val product = item.value.workProduct
      val binding = item.value.binding
      val branch = item.value.selection.value
      s"""<tr><th scope="row">${_html_escape(product.id)}<br/><span>${_html_escape(product.label)}</span></th><td>${_html_escape(branch)}</td><td>${_html_escape(product.role.value)}</td><td>${_html_escape(binding.disposition.value)}</td><td>${_html_escape(_provider_for(product, definition))}</td><td>${_html_list(product.gates)}</td><td>${_html_escape(item.reason.getOrElse(""))}</td></tr>"""
    }.mkString("\n")
    val matrixrows = products.map { item =>
      val product = item.value.workProduct
      s"""<tr><th scope="row">${_html_escape(product.id)}</th><td>${_html_escape(item.coverage)}</td><td>${_html_escape(item.currentness)}</td><td>${_html_escape(item.review)}</td><td>${_html_escape(item.readiness)}</td><td>${_html_escape(item.reason.getOrElse(""))}</td></tr>"""
    }.mkString("\n")
    val satisfiedcriteria = snapshot.criteria.count(_.coverage == "satisfied")
    val applicablecriteria = snapshot.criteria.count(_.coverage != "not-applicable")
    val criteriarows = snapshot.criteria.map { criterion =>
      s"""<tr><th scope="row">${_html_escape(criterion.id)}</th><td>${_html_escape(criterion.coverage)}</td><td>${_html_escape(criterion.reason)}</td></tr>"""
    }.mkString("\n")
    val detailrows = products.map { item =>
      val product = item.value.workProduct
      s"""<tr><th scope="row">${_html_escape(product.id)}</th><td>${_html_list(product.dependencies)}</td><td>${_html_escape(product.producer)}</td><td>${_html_list(product.consumers)}</td><td>${_html_list(product.evidenceReferences)}</td><td>${_html_escape(_next_action(item))}</td></tr>"""
    }.mkString("\n")
    val coreentries = _core_entries(project, descriptor)
    val coreentryrows = if (coreentries.isEmpty) "<tr><td colspan=\"2\">none accepted</td></tr>" else coreentries.map { case (id, text) =>
      s"""<tr><th scope="row">${_html_escape(id)}</th><td>${_html_escape(text)}</td></tr>"""
    }.mkString("\n")
    val attemptrows = if (snapshot.attempts.isEmpty) "<tr><td colspan=\"2\">none retained</td></tr>" else snapshot.attempts.map { attempt =>
      s"""<tr><th scope="row">${_html_escape(attempt.path.path)}</th><td>${_html_escape(attempt.outcome)}; historical attempt; no receipt/currentness authority</td></tr>"""
    }.mkString("\n")
    val publicsource = snapshot.sidecar match {
      case Some(sidecar) =>
        s"""<h2>Safe public source</h2><table aria-label="Safe public source"><thead><tr><th scope="col">Kind</th><th scope="col">Identity</th><th scope="col">Project-local path</th><th scope="col">SHA-256</th></tr></thead><tbody><tr><td>smartdox</td><td>${_html_escape(sidecar.publicSource.identity)}</td><td>${_html_escape(sidecar.publicSource.path.path)}</td><td>${_html_escape(sidecar.publicSource.path.sha256)}</td></tr></tbody></table><p class="notice">This safe mapping intentionally excludes Content Core, media source, review material, receipt content, and target files.</p>"""
      case None => ""
    }
    val dashboardparent = dashboardDestination.getParent
    val reviewtargets = Vector(
      ("Core Review", "target/document-project/core-review.html"),
      ("Article Review", "target/document-project/article-review.html"),
      ("Slide Review", "target/document-project/slides-review.html"),
      ("Slide Logical Chart", "target/document-project/slide-logical-chart-review.html"),
      ("Infographic final artifact", "infographic/infographic.svg")
    ) ++ (if (CozyDocumentWorkflow.isVideoProfile(descriptor.profile)) Vector(
      ("Video Review", "target/document-project/video-review.html"),
      ("Video Logical Chart", "target/document-project/video-logical-chart-review.html")
    ) else Vector.empty)
    val reviewrows = reviewtargets.map { case (label, relative) =>
      val target = project.resolve(relative).normalize()
      val reference = dashboardparent.relativize(target).toString.replace('\\', '/')
      val destination = if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
        s"""<a href="${_html_escape(reference)}">${_html_escape(reference)}</a>"""
      else "not generated"
      s"""<tr><th scope="row">${_html_escape(label)}</th><td>$destination</td></tr>"""
    }.mkString("\n")
    val participatingproducts = products.filter(_.value.isParticipating)
    val blockingproducts = participatingproducts.filter(item => Set("blocked", "failed").contains(item.readiness))
    val reviewproducts = participatingproducts.filter(item => item.value.workProduct.role == CozyDocumentWorkflow.WorkProductRole.ReviewProjection && item.review == "pending")
    val currentdeliverables = participatingproducts.filter { item =>
      Set[CozyDocumentWorkflow.WorkProductRole](
        CozyDocumentWorkflow.WorkProductRole.ReviewProjection,
        CozyDocumentWorkflow.WorkProductRole.SiteDeliverable,
        CozyDocumentWorkflow.WorkProductRole.Deliverable
      ).contains(item.value.workProduct.role) && item.currentness == "current" && item.readiness == "ready"
    }
    val optionalproducts = products.filter(_.value.binding.disposition == CozyDocumentWorkflow.WorkProductDisposition.Optional)
    val stage = _dashboard_stage(participatingproducts)
    val latestchange = _dashboard_latest_change(participatingproducts)
    val blockerlist = _dashboard_product_list(blockingproducts, "No prioritized blockers / 優先ブロッカーはありません。")
    val reviewlist = _dashboard_product_list(reviewproducts, "No pending review / 保留中のレビューはありません。")
    val deliverablelist = _dashboard_deliverable_list(currentdeliverables)
    val recommended = blockingproducts.headOption.map(_dashboard_recommended_action).getOrElse("No recommended action: all participating Work Products are current and ready. / 推奨される次のアクションはありません。参加中の成果物はすべて現在の状態で準備済みです。")
    val eligibleactions = _dashboard_safe_action_list(participatingproducts)
    val optionallist = _dashboard_optional_list(optionalproducts)
    _html_page(
      s"Cozy Document Project Dashboard - ${descriptor.id}",
      descriptor.language,
      s"""<h1>Cozy Document Project Dashboard</h1>
         |<p>Project: <code>${_html_escape(descriptor.id)}</code>; profile: <code>${_html_escape(descriptor.profile)}</code>; workspace: <code>${_html_escape(descriptor.workspace)}</code>; schema: <code>cozy.document-project.v2</code>.</p>
         |<p class="notice">Current snapshot is derived from admitted authored sources and the closed workflow. Retained attempts are historical evidence only; an initial attempt has no receipt or currentness authority.</p>
         |<main id="primary-action-surface">
         |<h2>Current production stage / 現在の制作段階</h2>
         |<p>$stage</p>
         |<h2>Latest observed change / 最新に確認された変化</h2>
         |<p>$latestchange</p>
         |<h2>Prioritized blockers / 優先ブロッカー</h2>
         |<ul>$blockerlist</ul>
         |<h2>Pending review / レビュー待ち</h2>
         |<ul>$reviewlist</ul>
         |<h2>Current deliverables / 現在の成果物</h2>
         |<ul>$deliverablelist</ul>
         |<h2>Recommended next action / 推奨される次のアクション</h2>
         |<p>$recommended</p>
         |<h2>Eligible safe actions / 実行可能な安全なアクション</h2>
         |<p class="notice">Selected-by-contract previews / 契約上選択済みのプレビューです。These are not proof of runtime readiness / 実行時の準備完了を証明しません。 The dashboard only displays these commands.</p>
         |<ul>$eligibleactions</ul>
         |<h2>Optional deliverable selection / オプション成果物の選択</h2>
         |<p>Activation is an authoring change to the descriptor's closed <code>activeOptionalWorkProducts</code> list / 有効化は記述子の閉じた <code>activeOptionalWorkProducts</code> リストを編集する作成者向け契約です。 Dashboard generation performs no descriptor write / ダッシュボード生成は記述子を書き換えません。</p>
         |<ul>$optionallist</ul>
         |<p class="notice">Inactive optional Work Products retain existing artifacts and evidence as nonparticipating / 非選択のオプション成果物は既存の成果物・証拠を非参加として保持します。 Profile-disabled Work Products cannot be activated / プロファイルで無効な成果物は有効化できません。</p>
         |</main>
         |<details id="secondary-diagnostics">
         |<summary>Secondary diagnostics / 二次診断</summary>
         |<h2>Workflow</h2>
         |<table aria-label="Workflow Work Products"><thead><tr><th scope="col">Work Product</th><th scope="col">Selection</th><th scope="col">Role</th><th scope="col">Disposition</th><th scope="col">Provider</th><th scope="col">Gates</th><th scope="col">Nonparticipating or blocking reason</th></tr></thead><tbody>$workflowrows</tbody></table>
         |<h2>Work Product matrix</h2>
         |<table aria-label="Work Product status matrix"><thead><tr><th scope="col">Work Product</th><th scope="col">Coverage</th><th scope="col">Currentness</th><th scope="col">Review</th><th scope="col">readiness</th><th scope="col">Omitted or blocking reason</th></tr></thead><tbody>$matrixrows</tbody></table>
         |<h2>Criterion coverage</h2>
         |<p>$satisfiedcriteria/$applicablecriteria applicable criteria satisfied</p>
         |<table aria-label="Criterion coverage"><thead><tr><th scope="col">Criterion</th><th scope="col">Coverage</th><th scope="col">Reason</th></tr></thead><tbody>$criteriarows</tbody></table>
         |<h2>Work Product details</h2>
         |<table aria-label="Work Product details"><thead><tr><th scope="col">Work Product</th><th scope="col">Dependencies</th><th scope="col">Producer operation</th><th scope="col">Consumer operations</th><th scope="col">Evidence references</th><th scope="col">Next action</th></tr></thead><tbody>$detailrows</tbody></table>
         |<h2>Review and final artifact links</h2>
         |<table aria-label="Review and final artifact links"><thead><tr><th scope="col">Review or artifact</th><th scope="col">Output</th></tr></thead><tbody>$reviewrows</tbody></table>
         |<p class="notice">A link to a review page appears after that page has been generated at its default Project-local destination. The infographic link is its required final SVG artifact, used directly for review. Dashboard generation does not generate additional review pages or artifacts.</p>
         |<h2>Accepted Core entries</h2>
         |<table aria-label="Accepted Core entries"><thead><tr><th scope="col">Entry ID</th><th scope="col">Text</th></tr></thead><tbody>$coreentryrows</tbody></table>
         |<h2>Retained attempts</h2>
         |<table aria-label="Retained operation attempts"><thead><tr><th scope="col">Attempt</th><th scope="col">Authority boundary</th></tr></thead><tbody>$attemptrows</tbody></table>
         |$publicsource
         |$alignment
         |<h2>Responsibility boundary</h2>
         |<table aria-label="Document Project responsibility boundary"><thead><tr><th scope="col">Responsibility</th><th scope="col">Dashboard disposition</th></tr></thead><tbody><tr><th scope="row">Project production</th><td>Read-only state projection; no provider is invoked.</td></tr><tr><th scope="row">Workspace integration</th><td>Read-only and non-invoked.</td></tr><tr><th scope="row">Aggregate build</th><td>Read-only and non-invoked.</td></tr><tr><th scope="row">External delivery</th><td>Read-only and non-invoked; no publication, deployment, upload, or registration is performed.</td></tr></tbody></table>
         |<p class="notice">This dashboard is a deterministic, read-only projection. It does not execute providers or persist candidates, feedback, acceptance, receipts, deliverables, or workflow status.</p>
         |</details>""".stripMargin
    )
  }

  private[cozy] def coreReviewHtml(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    _require_work_product(descriptor, "core-review-html", "content-core.render-review")
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
         |<p>Project: <code>${_html_escape(descriptor.id)}</code>; Content Core: <code>${_html_escape(descriptor.contentCore)}</code>; schema: <code>cozy.document-project.v2</code>.</p>
         |<h2>Accepted Core entries</h2>
         |$content
         |<h2>Candidate, feedback, and acceptance surface</h2>
         |<p class="notice">Non-authoritative and not yet persisted. This projection presents accepted Core entries only; it does not persist a candidate, feedback, or acceptance decision.</p>
         |<p>No provider execution, Core write-back, receipt, or state-cache update is performed.</p>""".stripMargin
    )
  }

  private[cozy] def articleReviewHtml(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    _require_work_product(descriptor, "article-review-html", "article.render-review")
    val articlepath = CozyDocumentProject._direct_file(project, "index.dox", "article source")
    val visualpagespath = CozyDocumentProject._direct_file(project, "presentation/visual-pages.yaml", "Visual Page source")
    val infographicpath = CozyDocumentProject._direct_file(project, "infographic/infographic.svg", "infographic source")
    val sections = _article_sections(_read_source(articlepath))
    val sectionrows = if (sections.isEmpty)
      "<tr><td colspan=\"2\">No narrative section is present.</td></tr>"
    else sections.map { case (heading, narrative) =>
      s"""<tr><th scope="row">${_html_escape(heading)}</th><td>${_html_escape(narrative)}</td></tr>"""
    }.mkString("\n")
    val coreentries = _core_entries(project, descriptor)
    val corerows = if (coreentries.isEmpty)
      "<tr><td colspan=\"2\">No accepted Content Core correspondence is available.</td></tr>"
    else coreentries.map { case (id, text) =>
      s"""<tr><th scope="row">${_html_escape(id)}</th><td>${_html_escape(text)}</td></tr>"""
    }.mkString("\n")
    val pages = _article_review_pages(visualpagespath)
    val pagesummary = _article_structure_summary(sections)
    val pagesections = if (pages.isEmpty)
      "<p class=\"notice\">No Visual Page is declared.</p>"
    else pages.zipWithIndex.map { case (page, index) =>
      val hidden = if (index == 0) "" else " hidden"
      s"""<section class="review-page" data-page-index="${index + 1}" data-page-id="${_html_escape(page.id)}"$hidden><h3>${_html_escape(page.title)}</h3><dl><dt>Stable page ID</dt><dd>${_html_escape(page.id)}</dd><dt>Reader-facing text</dt><dd>${_html_escape(page.readertext)}</dd><dt>Visual summary</dt><dd>${_html_escape(page.visualsummary)}</dd><dt>Relationship summary</dt><dd>${_html_escape(page.relationshipsummary)}</dd></dl></section>"""
    }.mkString("\n")
    val terminology = _article_declaration(sections, "Terminology")
    val mediaplacement = _article_declaration(sections, "Media placement")
    _html_page(
      s"Cozy Document Project Article Review - ${descriptor.id}",
      descriptor.language,
      s"""<h1>Article Review</h1>
         |<p>Project: <code>${_html_escape(descriptor.id)}</code>; profile: <code>${_html_escape(descriptor.profile)}</code>; schema: <code>cozy.document-project.v2</code>.</p>
         |<p class="notice">This deterministic, self-contained review projects authored article structure and selected workflow inputs. It is not article site HTML, a dashboard, or raw Dox or Visual Page source presentation.</p>
         |<h2>Article structure and narrative</h2>
         |<table aria-label="Article structure and narrative"><thead><tr><th scope="col">Section</th><th scope="col">Narrative</th></tr></thead><tbody>$sectionrows</tbody></table>
         |<h2>Accepted Content Core correspondence</h2>
         |<table aria-label="Accepted Content Core correspondence"><thead><tr><th scope="col">Core entry</th><th scope="col">Accepted correspondence</th></tr></thead><tbody>$corerows</tbody></table>
         |<h2>Page review</h2>
         |<p id="article-structure-summary">Article structure: ${_html_escape(pagesummary)}</p>
         |<div id="article-review-pages" data-page-count="${pages.size}" data-current-page="${if (pages.isEmpty) 0 else 1}"><div class="page-navigation"><button type="button" data-page-action="previous" aria-controls="article-review-pages">Previous page</button><span id="article-review-page-state" aria-live="polite">${if (pages.isEmpty) "No pages" else s"Page 1 of ${pages.size}"}</span><button type="button" data-page-action="next" aria-controls="article-review-pages">Next page</button></div>$pagesections</div>
         |<p class="notice">Phase-41 Projection selector: unavailable; the v2 descriptor declares no accepted Phase-41 selector. Phase-41 page-flow review evidence is unavailable and is not claimed.</p>
         |<h2>Terminology and media placement</h2>
         |<table aria-label="Article terminology and media placement"><thead><tr><th scope="col">Article semantic information</th><th scope="col">Declared result</th></tr></thead><tbody><tr><th scope="row">Terminology</th><td>${_html_escape(terminology)}</td></tr><tr><th scope="row">Media placement</th><td>${_html_escape(mediaplacement)}</td></tr></tbody></table>
         |<h2>Infographic relationship</h2>
         |<p>The editable infographic remains a separate expression authority related to the article and its visual flow; this review does not render or alter it.</p>
         |<h2>Current verified input identities</h2>
         |${_input_identity_table(project, Vector(articlepath, visualpagespath, infographicpath, project.resolve(descriptor.contentCore)))}
         |<p class="notice">No provider execution, renderer input, production receipt, rendered frame, candidate, feedback, acceptance, state-cache persistence, or authored-input mutation occurs. The default generated-review receipt is local output evidence only, not a renderer or production receipt.</p>
         |<script>(function(){var root=document.getElementById('article-review-pages');if(!root){return;}var pages=Array.prototype.slice.call(root.querySelectorAll('.review-page'));var state=document.getElementById('article-review-page-state');var current=0;function show(index){if(!pages.length){return;}current=Math.max(0,Math.min(index,pages.length-1));pages.forEach(function(page,position){page.hidden=position!==current;});root.setAttribute('data-current-page',String(current+1));state.textContent='Page '+(current+1)+' of '+pages.length;}root.querySelector('[data-page-action="previous"]').addEventListener('click',function(){show(current-1);});root.querySelector('[data-page-action="next"]').addEventListener('click',function(){show(current+1);});root.addEventListener('keydown',function(event){if(event.key==='ArrowLeft'){event.preventDefault();show(current-1);}if(event.key==='ArrowRight'){event.preventDefault();show(current+1);}if(event.key==='Home'){event.preventDefault();show(0);}if(event.key==='End'){event.preventDefault();show(pages.length-1);}});root.tabIndex=0;show(0);}());</script>""".stripMargin
    )
  }

  private[cozy] def slideReviewHtml(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    _require_work_product(descriptor, "slide-review-html", "visual-pages.render-review")
    val visualpages = _read_projection_source(CozyDocumentProject._direct_file(project, "presentation/visual-pages.yaml", "Visual Page source"))
    _html_page(
      s"Cozy Document Project Slide Review - ${descriptor.id}",
      descriptor.language,
      s"""<h1>Slide Review</h1>
         |<p>Project: <code>${_html_escape(descriptor.id)}</code>; profile: <code>${_html_escape(descriptor.profile)}</code>; schema: <code>cozy.document-project.v2</code>.</p>
         |<p class="notice">This is a deterministic, read-only projection of the Visual Page IR. It does not execute a provider, render a PDF, or persist acceptance.</p>
         |${_source_projection_table("Visual Page IR source", "presentation/visual-pages.yaml", visualpages)}
         |<p>No provider, receipt, state cache, feedback record, or authored-source write-back is performed.</p>""".stripMargin
    )
  }

  private[cozy] def slideLogicalChartHtml(project: Path, descriptor: CozyDocumentProject.Descriptor): String =
    _logical_chart_html(project, descriptor, "explanation-structure-review-html", "Slide Logical Chart", None)

  private[cozy] def videoLogicalChartHtml(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    _require_work_product(descriptor, "video-logical-chart-html", "video-logical-chart.render-review")
    val storyboard = _read_projection_source(CozyDocumentProject._direct_file(project, "video/storyboard.md", "video storyboard"))
    _logical_chart_html(project, descriptor, "video-logical-chart-html", "Video Logical Chart", Some(storyboard))
  }

  private def _logical_chart_html(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    workproductid: String,
    title: String,
    storyboard: Option[String]
  ): String = {
    val resolved = CozyDocumentWorkflow.resolve(descriptor.profile, descriptor.activeOptionalWorkProducts) match {
      case Right(value) => value
      case Left(cause) => CozyDocumentProject._descriptor_failure(cause)
    }
    val logicalchart = resolved.workProducts.find(_.workProduct.id == workproductid) match {
      case Some(value) if value.isParticipating => value.workProduct
      case Some(value) if value.selection == CozyDocumentWorkflow.WorkProductSelection.InactiveOptional => CozyDocumentProject._failure("DP-OP-001", s"logical chart Work Product ${value.workProduct.id} is not selected for profile ${descriptor.profile}")
      case Some(value) => CozyDocumentProject._failure("DP-OP-001", s"logical chart Work Product ${value.workProduct.id} is disabled for profile ${descriptor.profile}")
      case None => CozyDocumentProject._descriptor_failure(s"document-production Work Product is missing: $workproductid")
    }
    val entries = _core_entries(project, descriptor)
    val visualpages = _read_projection_source(CozyDocumentProject._direct_file(project, "presentation/visual-pages.yaml", "Visual Page source"))
    val entryrows = if (entries.isEmpty)
      s"""<tr><td colspan="2">${_html_escape("No accepted Core entries are present.")}</td></tr>"""
    else entries.map { case (id, text) =>
      s"""<tr><th scope="row">${_html_escape(id)}</th><td>${_html_escape(text)}</td></tr>"""
    }.mkString("\n")
    val storyboardsection = storyboard match {
      case Some(content) => _source_projection_table("Video storyboard IR source", "video/storyboard.md", content)
      case None => "<p class=\"notice\">Not included in the Slide Logical Chart.</p>"
    }
    _html_page(
      s"$title - ${descriptor.id}",
      descriptor.language,
      s"""<h1>$title</h1>
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
    val storyboardpath = CozyDocumentProject._direct_file(project, "video/storyboard.md", "video storyboard")
    val visualpagespath = CozyDocumentProject._direct_file(project, "presentation/visual-pages.yaml", "Visual Page source")
    val infographicpath = CozyDocumentProject._direct_file(project, "infographic/infographic.svg", "infographic source")
    val storyboard = CozyVideo.loadStoryboard(storyboardpath)
    if (!storyboard.isValid)
      CozyDocumentProject._descriptor_failure(s"video storyboard is invalid: ${storyboard.diagnostics.map(_.render).mkString("; ")}")
    val scenes = storyboard.storyboard.get.scenes.sortBy(_.order)
    val scenrows = scenes.map { scene =>
      s"""<tr><th scope="row">${scene.order}: ${_html_escape(scene.id)}</th><td>${_html_escape(scene.section)} / ${_html_escape(scene.role)}</td><td>${_html_escape(scene.narration)}</td><td>${_html_escape(_speaker_notes(scene))}</td><td>${_html_escape(_scene_visuals(scene))}</td><td>${_html_escape(_scene_timing(scene))}</td><td>${_html_escape(scene.transition)}</td><td>${_html_escape(scene.direction)}</td></tr>"""
    }.mkString("\n")
    val infographicuse = _storyboard_infographic_use(project, infographicpath, scenes)
    _html_page(
      s"Cozy Document Project Video Review - ${descriptor.id}",
      descriptor.language,
      s"""<h1>Video Review</h1>
         |<p>Project: <code>${_html_escape(descriptor.id)}</code>; profile: <code>${_html_escape(descriptor.profile)}</code>; schema: <code>cozy.document-project.v2</code>.</p>
         |<p class="notice">This deterministic semantic scene projection consumes the typed CozyVideo Storyboard result. It is not raw Storyboard or Visual Page source presentation and does not claim a rendered video.</p>
         |<h2>Ordered scene intent</h2>
         |<table aria-label="Ordered semantic video scenes"><thead><tr><th scope="col">Scene</th><th scope="col">Intent and role</th><th scope="col">Narration</th><th scope="col">Speaker and pronunciation</th><th scope="col">Screen, diagram, and assets</th><th scope="col">Timing</th><th scope="col">Transition</th><th scope="col">Direction</th></tr></thead><tbody>$scenrows</tbody></table>
         |<h2>Storyboard infographic use</h2>
         |<p>${_html_escape(infographicuse)}</p>
         |<h2>Currentness evidence</h2>
         |${_input_identity_table(project, Vector(storyboardpath, visualpagespath, infographicpath, project.resolve(descriptor.contentCore)))}
         |<table aria-label="Unavailable video evidence"><thead><tr><th scope="col">Evidence input</th><th scope="col">Status</th></tr></thead><tbody><tr><th scope="row">Renderer input</th><td>unavailable; no renderer input is admitted or executed</td></tr><tr><th scope="row">Production receipt</th><td>unavailable; no renderer or production receipt is present or claimed</td></tr><tr><th scope="row">Rendered frame</th><td>unavailable; no rendered frame or video is present or claimed</td></tr></tbody></table>
         |<p class="notice">No provider execution, candidate persistence, feedback persistence, acceptance, production receipt, state-cache persistence, or authored-input mutation occurs. The default generated-review receipt is local output evidence only.</p>""".stripMargin
    )
  }

  private def _provider_for(value: CozyDocumentWorkflow.WorkProduct, definition: CozyDocumentWorkflow.WorkflowDefinition): String =
    definition.operations.find(_.id == value.producer).map { operation =>
      definition.providerBindings.find(_.id == operation.providerBinding).map { binding =>
        s"${binding.id} (${binding.provider})"
      }.getOrElse(operation.providerBinding)
    }.getOrElse("unbound")

  private def _article_sections(source: String): Vector[(String, String)] = {
    val document: Document = Dox.toDocument(Dox2Parser.parseWithFilename("index.dox", source))
    val titlerows = document.head.distillTitleStringDefault.toVector.map { title =>
      "Article title" -> title.replaceAll("\\s+", " ").trim
    }
    val rows = document.body.contents.toVector.flatMap {
      case section: Section => _article_section_rows(section)
      case paragraph: Paragraph => Vector("Article narrative" -> _article_narrative_text(Vector(paragraph)))
      case dox => Vector("Article narrative" -> _article_narrative_text(Vector(dox)))
    }
    titlerows ++ rows
  }

  private def _article_section_rows(section: Section): Vector[(String, String)] = {
    val narrative = _article_narrative_text(section.contents.filterNot(_.isInstanceOf[Section]))
    val heading = if (section.titleName.trim.nonEmpty) section.titleName.trim else "Untitled section"
    val row = heading -> narrative
    row +: section.contents.collect { case child: Section => child }.toVector.flatMap(_article_section_rows)
  }

  private def _article_narrative_text(contents: Seq[Dox]): String = {
    val text = contents.map(_.toPlainText).mkString(" ").replaceAll("\\s+", " ").trim
    if (text.nonEmpty) text else "No narrative text is present."
  }

  private final case class ArticleReviewPage(
    id: String,
    title: String,
    readertext: String,
    visualsummary: String,
    relationshipsummary: String
  )

  private def _article_review_pages(path: Path): Vector[ArticleReviewPage] = {
    val pages = CozyDocumentProject._load_json(path, "Visual Page source").hcursor.downField("pages").focus
    pages.flatMap(_.asArray).map(_.toVector.zipWithIndex.map {
      case (page, index) =>
        val fields = page.asObject.map(_.toMap).getOrElse(Map.empty)
        val id = _page_text(fields, Vector("id")).getOrElse(s"page-${index + 1}")
        val title = _page_text(fields, Vector("title", "heading")).getOrElse(s"Untitled page ${index + 1}")
        val reader = _page_text(fields, Vector("readerText", "text", "content", "intent", "summary")).getOrElse("No reader-facing text is declared.")
        val visual = _page_summary(fields, Vector("visual", "intent", "emphasis"), "No visual summary is declared.")
        val relationship = _page_summary(fields, Vector("logical", "media", "assets", "sources"), "No relationship summary is declared.")
        ArticleReviewPage(id, title, reader, visual, relationship)
    }).getOrElse(Vector.empty)
  }

  private def _page_text(fields: Map[String, io.circe.Json], names: Vector[String]): Option[String] =
    names.iterator.flatMap(name => fields.get(name).flatMap(_.asString)).map(_.trim).find(_.nonEmpty)

  private def _page_summary(fields: Map[String, io.circe.Json], names: Vector[String], empty: String): String = {
    val values = names.flatMap(name => fields.get(name).toVector.flatMap(_semantic_fragments)).map(_.trim).filter(_.nonEmpty)
    if (values.isEmpty) empty else _bounded_text(values.distinct.mkString("; "))
  }

  private def _article_structure_summary(sections: Vector[(String, String)]): String = {
    val headings = sections.map(_._1).filterNot(_.equalsIgnoreCase("Article title")).take(8)
    if (headings.isEmpty) "no named article sections" else headings.mkString(", ")
  }

  private def _bounded_text(value: String): String = {
    val normalized = value.replaceAll("\\s+", " ").trim
    if (normalized.length <= 280) normalized else normalized.take(277) + "..."
  }

  private def _semantic_fragments(value: io.circe.Json): Vector[String] =
    value.asString.map(item => Vector(item)).orElse(value.asNumber.map(number => Vector(number.toString))).orElse(value.asBoolean.map(item => Vector(item.toString))).getOrElse {
      value.asArray.map(_.toVector.flatMap(_semantic_fragments)).orElse {
        value.asObject.map(_.toMap.toVector.sortBy(_._1).flatMap { case (name, item) =>
          _semantic_fragments(item).map(fragment => s"$name: $fragment")
        })
      }.getOrElse(Vector.empty)
    }

  private def _article_declaration(sections: Vector[(String, String)], label: String): String =
    sections.collect { case (heading, narrative) if heading.equalsIgnoreCase(label) => narrative } match {
      case Vector() => s"No $label declaration is present in the admitted article source."
      case declarations => declarations.mkString(" ")
    }

  private def _storyboard_infographic_use(
    project: Path,
    infographicpath: Path,
    scenes: Vector[CozyVideo.StoryboardScene]
  ): String = {
    val reference = CozyDocumentProject._project_relative(project, infographicpath)
    val uses = scenes.flatMap { scene =>
      scene.diagramRefs.filter(_ == reference).map(_ => s"scene ${scene.order}: ${scene.id} through diagram-refs") ++
        scene.assetRefs.filter(_ == reference).map(_ => s"scene ${scene.order}: ${scene.id} through asset-refs")
    }
    if (uses.isEmpty)
      s"No exact current infographic source use is declared through storyboard diagram-refs or asset-refs for $reference."
    else
      s"Exact current infographic source use is declared through storyboard references for $reference: ${uses.mkString("; ")}."
  }

  private def _speaker_notes(scene: CozyVideo.StoryboardScene): String = {
    val pronunciations = if (scene.pronunciationNotes.isEmpty) "no pronunciation notes" else scene.pronunciationNotes.map(note => s"${note.surface}: ${note.reading}").mkString("; ")
    s"${scene.speaker}; $pronunciations"
  }

  private def _scene_visuals(scene: CozyVideo.StoryboardScene): String = {
    val screen = scene.screen match {
      case CozyVideo.StoryboardScreen(heading, content) => s"screen: $heading — $content"
      case CozyVideoImplementation.StoryboardTextScreen(heading, content) => s"text screen: $heading — $content"
      case CozyVideoImplementation.StoryboardVisualPageScreen(source, catalog, pageid) => s"Visual Page: $pageid ($source; $catalog)"
    }
    val diagrams = if (scene.diagramRefs.isEmpty) "no diagram references" else s"diagrams: ${scene.diagramRefs.mkString(", ")}"
    val assets = if (scene.assetRefs.isEmpty) "no asset references" else s"assets: ${scene.assetRefs.mkString(", ")}"
    val inserts = if (scene.productionInserts.isEmpty) "no production inserts" else s"inserts: ${scene.productionInserts.map(insert => s"${insert.kind}: ${insert.value}").mkString(", ")}"
    Vector(screen, diagrams, assets, inserts).mkString("; ")
  }

  private def _scene_timing(scene: CozyVideo.StoryboardScene): String =
    s"duration ${scene.duration.bigDecimal.toPlainString}s; lead silence ${scene.leadSilence.bigDecimal.toPlainString}s"

  private def _input_identity_table(project: Path, paths: Vector[Path]): String = {
    val rows = paths.map { path =>
      val relative = CozyDocumentProject._project_relative(project, path)
      val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString
      s"""<tr><th scope="row">${_html_escape(relative)}</th><td>current verified input</td><td>${_html_escape(digest)}</td></tr>"""
    }.mkString("\n")
    s"""<table aria-label="Current verified input identities"><thead><tr><th scope="col">Project-local input</th><th scope="col">Currentness</th><th scope="col">SHA-256</th></tr></thead><tbody>$rows</tbody></table>"""
  }

  private def _next_action(product: CozyDocumentProjectEvidence.WorkProductState): String = {
    val workproduct = product.value.workProduct
    if (product.readiness == "omitted") "No action: omitted by this profile"
    else if (product.readiness == "not-selected") "No action: optional Work Product is not selected"
    else if (product.readiness != "blocked" && product.coverage == "satisfied" && product.currentness == "current") "No action: current"
    else workproduct.role match {
      case CozyDocumentWorkflow.WorkProductRole.Authority => s"Author or accept ${workproduct.label}"
      case CozyDocumentWorkflow.WorkProductRole.Plan => s"Author ${workproduct.label}"
      case CozyDocumentWorkflow.WorkProductRole.Candidate => s"Prepare ${workproduct.label}"
      case CozyDocumentWorkflow.WorkProductRole.ReviewProjection => s"Generate ${workproduct.label}"
      case CozyDocumentWorkflow.WorkProductRole.SiteDeliverable => s"Generate ${workproduct.label} with Cozy Site"
      case CozyDocumentWorkflow.WorkProductRole.Deliverable => s"Generate ${workproduct.label}"
      case CozyDocumentWorkflow.WorkProductRole.Receipt => s"Record ${workproduct.label}"
    }
  }

  private def _dashboard_stage(products: Vector[CozyDocumentProjectEvidence.WorkProductState]): String = {
    products.find(item => Set("blocked", "failed").contains(item.readiness)) match {
      case Some(item) =>
        val product = item.value.workProduct
        val reason = item.reason.getOrElse("no blocking reason is recorded")
        s"<strong>${_html_escape(product.label)}</strong> <code>${_html_escape(product.id)}</code> — ${_html_escape(item.readiness)} / ${_html_escape(reason)}"
      case None =>
        "All participating Work Products are current and ready. / 参加中の成果物はすべて現在の状態で準備済みです。"
    }
  }

  private def _dashboard_latest_change(products: Vector[CozyDocumentProjectEvidence.WorkProductState]): String = {
    val stale = products.find(_.currentness == "stale")
    val missing = products.find(item => Set("missing", "failed").contains(item.currentness))
    stale.orElse(missing) match {
      case Some(item) =>
        val product = item.value.workProduct
        val reason = item.reason.getOrElse("no reason is recorded")
        s"<strong>${_html_escape(product.label)}</strong> <code>${_html_escape(product.id)}</code> — ${_html_escape(item.currentness)}: ${_html_escape(reason)}"
      case None =>
        "Current identities show no stale or missing participating Work Product. / 現在の識別情報では、参加中の成果物に古いものも欠落したものもありません。"
    }
  }

  private def _dashboard_product_list(
    products: Vector[CozyDocumentProjectEvidence.WorkProductState],
    empty: String
  ): String = {
    if (products.isEmpty) {
      s"<li>${_html_escape(empty)}</li>"
    } else {
      products.map { item =>
        val product = item.value.workProduct
        val reason = item.reason.map(value => s"; ${_html_escape(value)}").getOrElse("")
        s"<li><strong>${_html_escape(product.label)}</strong> <code>${_html_escape(product.id)}</code> — ${_html_escape(item.readiness)} / ${_html_escape(item.currentness)}$reason</li>"
      }.mkString("\n")
    }
  }

  private def _dashboard_deliverable_list(products: Vector[CozyDocumentProjectEvidence.WorkProductState]): String =
    _dashboard_product_list(products, "No current/ready deliverables are available. / 現在かつ準備済みの成果物はありません。")

  private def _dashboard_recommended_action(item: CozyDocumentProjectEvidence.WorkProductState): String = {
    val product = item.value.workProduct
    val reason = item.reason.map(value => s"; ${_html_escape(value)}").getOrElse("")
    val command = _dashboard_safe_action_command(product)
    s"<strong>${_html_escape(product.label)}</strong> <code>${_html_escape(product.id)}</code> — ${_html_escape(item.readiness)}$reason<br/><span>Safe contract / 安全な契約:</span> <code>${_html_escape(command)}</code>"
  }

  private def _dashboard_safe_action_list(products: Vector[CozyDocumentProjectEvidence.WorkProductState]): String = {
    if (products.isEmpty) {
      "<li>No participating Work Product has a safe action preview. / 安全なアクションのプレビューがある参加中の成果物はありません。</li>"
    } else {
      products.map { item =>
        val product = item.value.workProduct
        s"""<li><strong>${_html_escape(product.label)}</strong> <code>${_html_escape(product.id)}</code> — selected-by-contract preview / 契約上選択済みのプレビュー<br/><code>${_html_escape(_dashboard_safe_action_command(product))}</code></li>"""
      }.mkString("\n")
    }
  }

  private def _dashboard_safe_action_command(product: CozyDocumentWorkflow.WorkProduct): String = product.id match {
    case "content-core-candidate" => "cozy document-project content-core candidate <project> <dialogue>"
    case "content-core" => "cozy document-project content-core candidate <project> <dialogue>"
    case "core-review-html" => "cozy document-project review <project> --kind core"
    case "article-review-html" => "cozy document-project review <project> --kind article"
    case "slide-review-html" => "cozy document-project review <project> --kind slides"
    case "video-review" => "cozy document-project review <project> --kind video"
    case "explanation-structure-review-html" => "cozy document-project review <project> --kind slide-logical-chart"
    case "video-logical-chart-html" => "cozy document-project review <project> --kind video-logical-chart"
    case _ => s"cozy document-project run <project> --operation ${product.producer} --dry-run"
  }

  private def _dashboard_optional_list(products: Vector[CozyDocumentProjectEvidence.WorkProductState]): String = {
    if (products.isEmpty) {
      "<li>No optional Work Products are declared by this profile. / このプロファイルにオプション成果物はありません。</li>"
    } else {
      products.map { item =>
        val product = item.value.workProduct
        val selection = item.value.selection match {
          case CozyDocumentWorkflow.WorkProductSelection.ActiveOptional => "active-optional / 有効なオプション"
          case CozyDocumentWorkflow.WorkProductSelection.InactiveOptional => "inactive-optional / 無効なオプション"
          case _ => item.value.selection.value
        }
        s"<li><strong>${_html_escape(product.label)}</strong> <code>${_html_escape(product.id)}</code> — ${_html_escape(selection)}</li>"
      }.mkString("\n")
    }
  }

  private def _require_video_review(descriptor: CozyDocumentProject.Descriptor): Unit = {
    _require_work_product(descriptor, "video-review", "video.render-review")
  }

  private def _require_work_product(
    descriptor: CozyDocumentProject.Descriptor,
    workproductid: String,
    operationid: String
  ): Unit = {
    val resolved = CozyDocumentWorkflow.resolve(descriptor.profile, descriptor.activeOptionalWorkProducts) match {
      case Right(value) => value
      case Left(cause) => CozyDocumentProject._descriptor_failure(cause)
    }
    val activeproducts = resolved.workProducts.collect { case value if value.isParticipating => value.workProduct.id }.toSet
    val operation = CozyDocumentWorkflow.declaredOperation(operationid) match {
      case Right(Some(value)) => value
      case Right(None) => CozyDocumentProject._failure("DP-OP-001", s"undeclared logical operation: $operationid")
      case Left(cause) => CozyDocumentProject._descriptor_failure(cause)
    }
    if (!operation.produces.contains(workproductid))
      CozyDocumentProject._descriptor_failure(s"logical operation $operationid does not produce Work Product $workproductid")
    if (!activeproducts.contains(workproductid)) {
      val selected = resolved.workProducts.find(_.workProduct.id == workproductid)
      selected match {
        case Some(value) if value.selection == CozyDocumentWorkflow.WorkProductSelection.InactiveOptional => CozyDocumentProject._failure("DP-OP-001", s"logical operation $operationid is not selected for profile ${descriptor.profile}")
        case _ => CozyDocumentProject._failure("DP-OP-001", s"logical operation $operationid is disabled for profile ${descriptor.profile}")
      }
    }
  }

  private def _core_entries(project: Path, descriptor: CozyDocumentProject.Descriptor): Vector[(String, String)] = {
    val accepted = CozyDocumentProject._load_json(project.resolve(descriptor.contentCore), "Content Core").hcursor.downField("accepted").focus.flatMap(_.asArray).getOrElse(Vector.empty)
    accepted.map { entry =>
      val cursor = entry.hcursor
      (cursor.downField("id").as[String].getOrElse(""), cursor.downField("text").as[String].getOrElse(""))
    }
  }

  private def _read_source(path: Path): String =
    try Files.readString(path, StandardCharsets.UTF_8)
    catch { case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "review source cannot be read") }

  private def _read_projection_source(path: Path): String =
    _html_escape(_read_source(path))

  private def _source_projection_table(aria: String, path: String, content: String): String =
    s"""<table aria-label="${_html_escape(aria)}"><thead><tr><th scope="col">Source</th><th scope="col">Content</th></tr></thead><tbody><tr><th scope="row">${_html_escape(path)}</th><td><pre><code>$content</code></pre></td></tr></tbody></table>"""

  private[cozy] def projectionResult(kind: String, project: Path, descriptor: CozyDocumentProject.Descriptor, destination: Path): String = {
    val reference = if (destination.startsWith(project)) CozyDocumentProject._project_relative(project, destination) else destination.toString
    s"Cozy Document Project $kind\nproject: ${descriptor.id}\nprofile: ${descriptor.profile}\nschema: cozy.document-project.v2\noutput: $reference"
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
