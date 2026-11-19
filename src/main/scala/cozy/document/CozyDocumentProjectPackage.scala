package cozy.document

import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.util.control.NonFatal

/** Explicit, non-destructive initial placement of a reviewed source closure. */
private[cozy] object CozyDocumentProjectPackage {
  final case class Candidate(path: Path, report: String)
  final case class Product(product: String, locale: String, destination: String)
  final case class Bundle(from: String, to: String)
  final case class Reference(from: String, to: String)
  final case class Placement(projectPath: String, sources: Vector[String], products: Vector[Product], bundles: Vector[Bundle] = Vector.empty, references: Vector[Reference] = Vector.empty)

  def place(project: Path, packageRoot: Path, workRoot: Path, synchronizationRoots: Vector[Path], placement: Placement): Path = {
    val original = _physical(project)
    val destination = _physical(packageRoot)
    if (!Files.isDirectory(original) || _overlaps(original, destination))
      _failure("project and package must be distinct non-overlapping directories")
    val projectpath = if (placement.projectPath == ".") Paths.get("") else _relative(placement.projectPath, "project path")
    if (placement.sources.isEmpty || placement.products.isEmpty)
      _failure("placement requires explicit sources and products")
    val sources = placement.sources.map(value => _relative(value, "source"))
    if (sources.distinct.size != sources.size) _failure("duplicate source")
    val inputs = sources.map { value =>
      val path = original.resolve(value)
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !_physical(path).startsWith(original))
        _failure(s"source is not an admitted regular file: $value")
      path
    }
        val bundled = placement.bundles.map { value =>
      val input = original.resolve(value.from).normalize()
      if (value.from.isEmpty || !Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(input))
        _failure(s"bundled source is unavailable: ${value.from}")
      input -> destination.resolve("source").resolve(_relative(value.to, "bundle destination"))
    }
    if (placement.references.map(_.from).distinct.size != placement.references.size) _failure("duplicate reference mapping")
    val products = placement.products.map { value =>
      if (!Set("article-pdf", "infographic", "summary-slides", "audio", "video", "article-review", "core-review", "slide-review", "video-review").contains(value.product) || !Set("ja", "en").contains(value.locale))
        _failure(s"unsupported product or locale: ${value.product}/${value.locale}")
      val relative = _relative(value.destination, "product destination")
      if (relative.startsWith(Paths.get("source"))) _failure("product destination is inside source")
      destination.resolve(relative)
    }
    if (placement.products.map(value => value.product -> value.locale).distinct.size != placement.products.size)
      _failure("duplicate product and locale")
    val sourcepaths = sources.map(value => destination.resolve("source").resolve(projectpath).resolve(value))
    val manifest = destination.resolve("source/cozy-package.yaml")
    val allpaths = sourcepaths ++ bundled.map(_._2) ++ Vector(manifest) ++ products
    val physical = allpaths.map(_physical)
    physical.zipWithIndex.foreach { case (path, index) =>
      if (!path.startsWith(destination) || path == destination || physical.take(index).exists(_overlaps(_, path)))
        _failure(s"placement destination collides or escapes package: $path")
      if (Files.exists(allpaths(index), LinkOption.NOFOLLOW_LINKS))
        _failure(s"placement destination already exists and is not owned: ${allpaths(index)}")
      _check_parents(destination, allpaths(index).getParent)
    }
    val mediaindex = sources.indexWhere(_.toString == "media.yaml")
    val media = if (mediaindex >= 0) Some(_portable_media(inputs(mediaindex), destination.resolve("source"), destination.resolve("source").resolve(projectpath), sourcepaths ++ bundled.map(_._2), placement.references)) else None
    if (placement.references.nonEmpty && media.isEmpty) _failure("reference mappings require a declared media.yaml")
    // Allocate only after the complete source and destination preflight.
    val placementrun = CozyDocumentProjectWorkRoot.allocate(workRoot, original, Some(destination), synchronizationRoots)
    val declaration = Json.obj(
      "schema" -> Json.fromString("cozy.document-package.v1"),
      "project" -> Json.fromString(placement.projectPath),
      "sources" -> Json.fromValues(sources.map(value => Json.fromString(value.toString.replace('\\', '/')))),
      "products" -> Json.fromValues(placement.products.map(value => Json.obj(
        "product" -> Json.fromString(value.product), "locale" -> Json.fromString(value.locale),
        "destination" -> Json.fromString(value.destination)
      )))
    )
    val outputs = sourcepaths ++ bundled.map(_._2) :+ manifest
    val stage = Files.createDirectory(placementrun.root.resolve("placement-staged"))
    inputs.zipWithIndex.foreach { case (input, index) =>
      val staged = stage.resolve(index.toString)
      if (index == mediaindex && media.nonEmpty) Files.writeString(staged, media.get.noSpaces + "\n", StandardCharsets.UTF_8)
      else Files.copy(input, staged, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES)
    }
    bundled.zipWithIndex.foreach { case ((input, _), index) =>
      Files.copy(input, stage.resolve((inputs.size + index).toString), java.nio.file.StandardCopyOption.COPY_ATTRIBUTES)
    }
    Files.writeString(stage.resolve((outputs.size - 1).toString), declaration.spaces2 + "\n", StandardCharsets.UTF_8)
    // Reconstruct the explicit closure under WorkRoot and admit the selected
    // native inputs before a package destination can be touched.
    val validationroot = Files.createDirectory(placementrun.root.resolve("placement-validation"))
    outputs.zipWithIndex.foreach { case (output, index) =>
      val stagedoutput = validationroot.resolve(destination.relativize(output))
      Files.createDirectories(stagedoutput.getParent)
      Files.copy(stage.resolve(index.toString), stagedoutput, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES)
    }
    val stagedproject = validationroot.resolve("source").resolve(projectpath)
    placement.products.zipWithIndex.foreach { case (product, index) =>
      val child = Files.createDirectory(placementrun.root.resolve(s"placement-validation-$index"))
      val workspace = CozyDocumentProjectWorkRoot.Run(child, Files.createDirectory(child.resolve("tmp")), Files.createDirectory(child.resolve("cache")))
      product.product match {
        case "article-pdf" | "infographic" | "summary-slides" | "slide-review" =>
          val selected = if (product.product == "slide-review") "summary-slides" else product.product
          CozyDocumentProjectMediaWorkspace.buildCandidate(stagedproject, workspace, selected, product.locale, validateOnly = true)
        case "audio" | "video" =>
          CozyDocumentProjectVideoWorkspace.buildCandidate(stagedproject, workspace, product.product, product.locale, validateOnly = true)
        case "article-review" =>
          CozyDocumentProjectLocalBuildCommand.validateTargetConfiguration(stagedproject, "smartdox-article-html")
          val references = CozyDocumentProjectSmartDoxArticleHtml.articleImageReferences(stagedproject, Some(product.locale))
          val media = stagedproject.resolve("media.yaml")
          val resources = if (Files.isRegularFile(media, LinkOption.NOFOLLOW_LINKS))
            CozyDocumentProject._load_json(media, "media descriptor").hcursor.downField("resources").focus.flatMap(_.asArray).getOrElse(Vector.empty)
          else Vector.empty
          references.zipWithIndex.foreach { case (reference, referenceindex) =>
            val relative = _relative(reference, "article image")
            val source = stagedproject.resolve(relative)
            val producers = resources.filter(_.hcursor.get[String]("output").toOption.contains(reference))
            if (producers.size > 1) _failure(s"article image has multiple producers: $reference")
            producers.headOption match {
              case Some(value) =>
                val cursor = value.hcursor
                if (cursor.get[String]("build").toOption != Some("svg-to-png")) _failure(s"unsupported article image producer: $reference")
                val id = cursor.get[String]("id").toOption.getOrElse(_failure("article image producer id is missing"))
                val childrun = Files.createDirectory(child.resolve(s"article-image-$referenceindex"))
                val childworkspace = CozyDocumentProjectWorkRoot.Run(childrun, Files.createDirectory(childrun.resolve("tmp")), Files.createDirectory(childrun.resolve("cache")))
                CozyDocumentProjectMediaWorkspace.buildCandidate(stagedproject, childworkspace, id, product.locale, validateOnly = true)
              case None =>
                if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || !_physical(source).startsWith(validationroot))
                  _failure(s"article image is missing from package closure: $reference")
            }
          }
        case "core-review" =>
          val inputs = CozyDocumentProjectLocalBuildCommand.validateInputs(stagedproject, "document-structure-html", workspace.root)
          if (!inputs.contains(s"content/${product.locale}/document.yaml"))
            _failure(s"configured core review does not match selected locale: ${product.locale}")
        case "video-review" =>
          val descriptor = CozyDocumentProject._load_project(stagedproject)
          if (descriptor.language != product.locale) _failure(s"video review does not match selected locale: ${product.locale}")
          CozyDocumentProjectProjection.videoReviewHtml(stagedproject, descriptor)
        case _ => _failure(s"unsupported selected product: ${product.product}")
      }
    }
    val placementreceipt = placementrun.root.resolve("package-placement.json")
    val placementrecord = Json.obj("schema" -> Json.fromString("cozy.package-placement.v2"), "package" -> Json.fromString(destination.toString),
      "destinations" -> Json.fromValues(outputs.map(value => Json.fromString(destination.relativize(value).toString))))
    Files.writeString(placementreceipt, placementrecord.spaces2 + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)
    try {
      _resume_placement(destination, placementrun.root)
      manifest
    } catch { case NonFatal(error) => _failure(s"placement incomplete; resume using external run ${placementrun.root}: ${error.getMessage}") }
  }

  /** Resume only an explicitly retained placement, preserving unexpected edits. */
  private def _resume_placement(root: Path, run: Path): Unit = {
    val receipt = run.resolve("package-placement.json")
    if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) _failure("placement receipt is unavailable")
    val data = CozyDocumentProject._load_json(receipt, "placement receipt").hcursor
    if (data.get[String]("schema").toOption != Some("cozy.package-placement.v2") || data.get[String]("package").toOption != Some(root.toString))
      _failure("placement receipt does not match package")
    val values = data.downField("destinations").focus.flatMap(_.asArray).getOrElse(_failure("placement destinations missing"))
    if (values.isEmpty) _failure("placement destinations missing")
    val outputs = values.zipWithIndex.map { case (value, index) =>
      val name = value.asString.getOrElse(_failure("invalid placement destination"))
      val destination = root.resolve(_relative(name, "placement destination"))
      val staged = run.resolve("placement-staged").resolve(index.toString)
      if (!_physical(destination).startsWith(root) || !Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS))
        _failure("placement destination or staged bytes unavailable")
      _check_parents(root, destination.getParent)
      val present = Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
      if (present && (!Files.isRegularFile(run.resolve(s"placement-started-$index"), LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || Files.mismatch(destination, staged) != -1L))
        _failure(s"placement destination has conflicting or incomplete bytes: $destination")
      (destination, staged, present)
    }
    val paths = outputs.map(value => _physical(value._1))
    paths.zipWithIndex.foreach { case (path, index) =>
      if (paths.take(index).exists(_overlaps(_, path))) _failure("placement destinations overlap")
    }
    outputs.zipWithIndex.foreach { case ((destination, staged, present), index) =>
      if (!present) {
        val started = run.resolve(s"placement-started-$index")
        if (!Files.exists(started, LinkOption.NOFOLLOW_LINKS))
          Files.writeString(started, "started\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)
        Files.createDirectories(destination.getParent)
        Files.copy(staged, destination, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES)
      }
    }
    val completed = run.resolve("package-placement-completed")
    if (!Files.exists(completed, LinkOption.NOFOLLOW_LINKS))
      Files.writeString(completed, "completed\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)
  }

  final case class Selection(product: String, locale: String)

  /** Generate every selected candidate before updating any owned product. */
  def refresh(packageRoot: Path, workRoot: Path, synchronizationRoots: Vector[Path], selections: Vector[Selection], producer: Option[(Path, CozyDocumentProjectWorkRoot.Run, Selection) => Candidate] = None): Path = {
    val root = _physical(packageRoot)
    val manifest = root.resolve("source/cozy-package.yaml")
    if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) _failure("package declaration is unavailable")
    val cursor = CozyDocumentProject._load_json(manifest, "package declaration").hcursor
    if (cursor.get[String]("schema").toOption != Some("cozy.document-package.v1")) _failure("invalid package schema")
    val projectvalue = cursor.get[String]("project").toOption.getOrElse(_failure("package project is missing"))
    val relativeproject = if (projectvalue == ".") Paths.get("") else _relative(projectvalue, "project path")
    val project = root.resolve("source").resolve(relativeproject).normalize()
    if (!Files.isDirectory(project, LinkOption.NOFOLLOW_LINKS) || !_physical(project).startsWith(root.resolve("source")))
      _failure("package project is unavailable")
    val entries = cursor.downField("products").focus.flatMap(_.asArray).getOrElse(_failure("package products are missing"))
    if (selections.isEmpty || selections.distinct.size != selections.size) _failure("selections must be nonempty and unique")
    val destinations = entries.map { value =>
      val name = value.hcursor.get[String]("destination").toOption.getOrElse(_failure("product destination is missing"))
      val relative = _relative(name, "product destination")
      if (relative.startsWith(Paths.get("source"))) _failure("product destination is inside source")
      root.resolve(relative)
    }
    val physical = destinations.map(_physical)
    physical.zipWithIndex.foreach { case (path, index) =>
      if (!path.startsWith(root) || path == root || physical.take(index).exists(_overlaps(_, path)))
        _failure(s"product destination collides or escapes package: $path")
      _check_parents(root, destinations(index).getParent)
      if (Files.exists(destinations(index), LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(destinations(index), LinkOption.NOFOLLOW_LINKS))
        _failure(s"product destination is not a direct regular file: ${destinations(index)}")
    }
    val selected = selections.map { selection =>
      if (!Set("article-pdf", "infographic", "summary-slides", "audio", "video", "article-review", "core-review", "slide-review", "video-review").contains(selection.product) || !Set("ja", "en").contains(selection.locale))
        _failure(s"unsupported product or locale: ${selection.product}/${selection.locale}")
      val matches = entries.zipWithIndex.filter { case (value, _) =>
        value.hcursor.get[String]("product").toOption.contains(selection.product) && value.hcursor.get[String]("locale").toOption.contains(selection.locale)
      }
      if (matches.size != 1) _failure("selected product and locale are not uniquely owned")
      val (entry, index) = matches.head
      (selection, entry.hcursor.get[String]("destination").toOption.get, destinations(index))
    }
    val run = CozyDocumentProjectWorkRoot.allocate(workRoot, project, Some(root), synchronizationRoots)
    val candidates = selected.zipWithIndex.map { case ((selection, _, _), index) =>
      val child = Files.createDirectory(run.root.resolve(s"product-$index"))
      val workspace = CozyDocumentProjectWorkRoot.Run(child, Files.createDirectory(child.resolve("tmp")), Files.createDirectory(child.resolve("cache")))
      val candidate = producer.map(_(project, workspace, selection)).getOrElse {
        if (Set("article-review", "core-review").contains(selection.product)) {
          val target = if (selection.product == "article-review") "smartdox-article-html" else "document-structure-html"
          val buildproject = if (selection.product == "article-review") _article_review_project(root, relativeproject, workspace, selection.locale) else project
          val result = CozyDocumentProjectLocalBuildCommand.execute(buildproject, Some(target), force = true, workRoot = Some(workspace.root), articleLocale = if (selection.product == "article-review") Some(selection.locale) else None)
          if (selection.product == "core-review" && !result.inputs.contains(s"content/${selection.locale}/document.yaml"))
            _failure(s"configured core review does not match selected locale: ${selection.locale}")
          Candidate(Paths.get(result.output), result.report)
        } else if (selection.product == "video-review") {
          val descriptor = CozyDocumentProject._load_project(project)
          if (descriptor.language != selection.locale) _failure(s"video review does not match selected locale: ${selection.locale}")
          val html = CozyDocumentProjectProjection.videoReviewHtml(project, descriptor)
          val output = Files.writeString(workspace.root.resolve("video-review.html"), html, StandardCharsets.UTF_8)
          Candidate(output, "generated video storyboard review")
        } else if (selection.product == "slide-review") {
          val result = CozyDocumentProjectMediaWorkspace.buildCandidate(project, workspace, "summary-slides", selection.locale)
          val descriptor = CozyDocumentProject._load_json(project.resolve("media.yaml"), "media descriptor")
          val resources = descriptor.hcursor.downField("resources").focus.flatMap(_.asArray).getOrElse(_failure("media resources are missing"))
          val matches = resources.filter(_.hcursor.get[String]("id").toOption.contains(s"summary-slides-${selection.locale}"))
          if (matches.size != 1) _failure("summary review resource is not unique")
          val montage = matches.head.hcursor.downField("summarySlidesPdf").get[String]("montage").toOption.getOrElse(_failure("summary montage is missing"))
          val relative = _relative(montage, "summary montage")
          val candidate = workspace.root.resolve("project.dox").resolve(relative)
          if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) _failure("summary montage was not generated")
          Candidate(candidate, result.report)
        } else if (Set("audio", "video").contains(selection.product))
          CozyDocumentProjectVideoWorkspace.buildCandidate(project, workspace, selection.product, selection.locale)
        else CozyDocumentProjectMediaWorkspace.buildCandidate(project, workspace, selection.product, selection.locale)
      }
      if (!Files.isRegularFile(candidate.path, LinkOption.NOFOLLOW_LINKS) || !_physical(candidate.path).startsWith(child.toRealPath()))
        _failure("candidate must be a direct file inside its owned product run")
      candidate
    }
    val existed = selected.zipWithIndex.map { case ((_, _, destination), index) =>
      val present = Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
      if (present) Files.copy(destination, run.root.resolve(s"backup-$index"))
      present
    }
    val items = selected.zipWithIndex.map { case ((selection, name, _), index) => Json.obj(
      "product" -> Json.fromString(selection.product), "locale" -> Json.fromString(selection.locale),
      "destination" -> Json.fromString(name), "candidate" -> Json.fromString(run.root.relativize(candidates(index).path).toString),
      "previouslyExisted" -> Json.fromBoolean(existed(index))
    ) }
    val receipt = run.root.resolve("package-refresh.json")
    val record = Json.obj("schema" -> Json.fromString("cozy.package-refresh.v2"), "package" -> Json.fromString(root.toString), "items" -> Json.fromValues(items))
    Files.writeString(receipt, record.spaces2 + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)
    selected.zipWithIndex.foreach { case ((_, _, destination), index) =>
      val backup = run.root.resolve(s"backup-$index")
      if (existed(index) && (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || Files.mismatch(destination, backup) != -1L))
        _failure(s"destination changed; inspect completed, uncertain and untouched states in $receipt")
      if (!existed(index) && Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
        _failure(s"destination appeared; inspect completed, uncertain and untouched states in $receipt")
      Files.writeString(run.root.resolve(s"uncertain-$index"), "uncertain\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)
      try {
        Files.createDirectories(destination.getParent)
        Files.copy(candidates(index).path, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        if (Files.mismatch(candidates(index).path, destination) != -1L)
          _failure(s"refresh uncertain; inspect $receipt")
        Files.writeString(run.root.resolve(s"completed-$index"), "completed\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)
      } catch { case NonFatal(error) => _failure(s"refresh interrupted; inspect completed, uncertain and untouched states in $receipt: ${error.getMessage}") }
    }
    receipt
  }

  private def _article_review_project(root: Path, projectpath: Path, workspace: CozyDocumentProjectWorkRoot.Run, locale: String): Path = {
    val source = root.resolve("source")
    val copy = workspace.root.resolve("review-source")
    val paths = Files.walk(source)
    try {
      val iterator = paths.iterator()
      while (iterator.hasNext) {
        val path = iterator.next()
        if (Files.isSymbolicLink(path)) _failure(s"package source contains a symbolic link: $path")
        val target = copy.resolve(source.relativize(path))
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(target)
        else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) Files.copy(path, target, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES)
        else _failure(s"package source contains an unsupported entry: $path")
      }
    } finally paths.close()
    val project = copy.resolve(projectpath)
    CozyDocumentProjectLocalBuildCommand.validateTargetConfiguration(project, "smartdox-article-html")
    val media = project.resolve("media.yaml")
    val resources = if (Files.isRegularFile(media, LinkOption.NOFOLLOW_LINKS))
      CozyDocumentProject._load_json(media, "media descriptor").hcursor.downField("resources").focus.flatMap(_.asArray).getOrElse(Vector.empty)
    else Vector.empty
    CozyDocumentProjectSmartDoxArticleHtml.articleImageReferences(project, Some(locale)).zipWithIndex.foreach { case (reference, index) =>
      val destination = project.resolve(_relative(reference, "article image"))
      if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        val matches = resources.filter(_.hcursor.get[String]("output").toOption.contains(reference))
        if (matches.size != 1 || matches.head.hcursor.get[String]("build").toOption != Some("svg-to-png"))
          _failure(s"article image has no unique admitted producer: $reference")
        val id = matches.head.hcursor.get[String]("id").toOption.getOrElse(_failure("article image producer id is missing"))
        val child = Files.createDirectory(workspace.root.resolve(s"article-image-$index"))
        val run = CozyDocumentProjectWorkRoot.Run(child, Files.createDirectory(child.resolve("tmp")), Files.createDirectory(child.resolve("cache")))
        val generated = CozyDocumentProjectMediaWorkspace.buildCandidate(project, run, id, locale)
        Files.createDirectories(destination.getParent)
        Files.copy(generated.path, destination)
      }
    }
    project
  }

  /** Restore touched items only when their current bytes match an observed state. */
  def recover(packageRoot: Path, workRoot: Path, runRoot: Path): Unit = {
    val root = _physical(packageRoot)
    val work = _physical(workRoot)
    val run = _physical(runRoot)
    val runs = work.resolve("target/document-project/runs")
    if (!run.startsWith(runs) || run.getParent != runs || _overlaps(root, work)) _failure("recovery run is outside the admitted work root")
    if (Files.isRegularFile(run.resolve("package-placement.json"), LinkOption.NOFOLLOW_LINKS)) {
      _resume_placement(root, run)
      return
    }
    val receipt = run.resolve("package-refresh.json")
    if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) _failure("refresh receipt is unavailable")
    val data = CozyDocumentProject._load_json(receipt, "refresh receipt").hcursor
    if (data.get[String]("schema").toOption != Some("cozy.package-refresh.v2") || data.get[String]("package").toOption != Some(root.toString))
      _failure("refresh receipt does not match package")
    val declaration = CozyDocumentProject._load_json(root.resolve("source/cozy-package.yaml"), "package declaration").hcursor
    val entries = declaration.downField("products").focus.flatMap(_.asArray).getOrElse(_failure("package products missing"))
    val items = data.downField("items").focus.flatMap(_.asArray).getOrElse(_failure("receipt items missing"))
    val touched = items.zipWithIndex.filter { case (_, index) => Files.exists(run.resolve(s"uncertain-$index"), LinkOption.NOFOLLOW_LINKS) }
    val restore = touched.map { case (item, index) =>
      val cursor = item.hcursor
      val product = cursor.get[String]("product").toOption.getOrElse(_failure("receipt product missing"))
      val locale = cursor.get[String]("locale").toOption.getOrElse(_failure("receipt locale missing"))
      val name = cursor.get[String]("destination").toOption.getOrElse(_failure("receipt destination missing"))
      if (entries.count(value => value.hcursor.get[String]("product").toOption.contains(product) &&
          value.hcursor.get[String]("locale").toOption.contains(locale) && value.hcursor.get[String]("destination").toOption.contains(name)) != 1)
        _failure("receipt destination is no longer owned")
      val destination = root.resolve(_relative(name, "product destination"))
      if (!_physical(destination).startsWith(root) || destination.startsWith(root.resolve("source"))) _failure("destination escapes package")
      _check_parents(root, destination.getParent)
      val candidate = run.resolve(_relative(cursor.get[String]("candidate").toOption.getOrElse(_failure("candidate missing")), "candidate"))
      if (!_physical(candidate).startsWith(run) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) _failure("candidate unavailable")
      val existed = cursor.get[Boolean]("previouslyExisted").toOption.getOrElse(_failure("backup status missing"))
      val backup = run.resolve(s"backup-$index")
      if (existed && !Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) _failure("backup unavailable")
      val present = Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
      if (present && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) _failure("destination changed shape")
      if (present) {
        val fromcandidate = Files.mismatch(destination, candidate) == -1L
        val frombackup = existed && Files.mismatch(destination, backup) == -1L
        if (!fromcandidate && !frombackup) _failure("destination has ambiguous changes and requires reconciliation")
      } else if (existed) _failure("previous destination is missing and requires reconciliation")
      (destination, backup, existed, present)
    }
    restore.foreach { case (destination, backup, existed, present) =>
      if (existed) Files.copy(backup, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      else if (present) Files.delete(destination)
    }
  }

  private def _portable_media(path: Path, source: Path, project: Path, planned: Vector[Path], references: Vector[Reference]): Json = {
    val mappings = references.map(value => value.from -> value.to).toMap
    val used = scala.collection.mutable.Set.empty[String]
    def _reference_(value: String): String = {
      val replacement = mappings.get(value).map { name => used += value; name }.getOrElse(value)
      val relative = try Paths.get(replacement) catch { case NonFatal(_) => _failure(s"invalid media reference: $replacement") }
      val resolved = project.resolve(relative).normalize()
      if (relative.isAbsolute || replacement.isEmpty || !resolved.startsWith(source) || !planned.contains(resolved))
        _failure(s"media reference is not included in the explicit package closure: $value")
      replacement
    }
    def _walk_(value: Json): Json = value.arrayOrObject(
      value,
      values => Json.fromValues(values.map(_walk_)),
      fields => Json.fromFields(fields.toVector.map {
        case ("command", command) =>
          val values = command.asArray.getOrElse(_failure("renderer command must be an array"))
          if (values.isEmpty) _failure("renderer command is empty")
          val executable = values.head.asString.getOrElse(_failure("renderer command executable must be text"))
          val pathflags = Set("--asset", "--config", "--input", "--style", "--template")
          val arguments = values.tail.zipWithIndex.map { case (item, index) =>
            val argument = item.asString.getOrElse(_failure("renderer command argument must be text"))
            val separator = argument.indexOf('=')
            val prefix = if (argument.startsWith("--") && separator >= 0) argument.substring(0, separator + 1) else ""
            val value = if (prefix.nonEmpty) argument.substring(separator + 1) else argument
            val path = try Paths.get(value) catch { case NonFatal(_) => _failure(s"invalid renderer argument: $argument") }
            val prior = if (index > 0) values.tail(index - 1).asString.getOrElse("") else ""
            val ispath = pathflags.contains(prior) || (prefix.nonEmpty && pathflags.contains(prefix.dropRight(1)))
            val portable = if (ispath || mappings.contains(value)) _reference_(value)
              else if (path.isAbsolute || (path.normalize().iterator().hasNext && path.normalize().getName(0).toString == ".."))
                _failure(s"renderer path argument requires an explicit portable reference: $argument")
              else value
            Json.fromString(prefix + portable)
          }
          "command" -> Json.fromValues(Vector(Json.fromString(_reference_(executable))) ++ arguments)
        case (key, child) => key -> _walk_(child)
      })
    )
    val input = CozyDocumentProject._load_json(path, "media descriptor")
    val walked = _walk_(input)
    val receipt = walked.hcursor.downField("receipt").focus.map { value =>
      val inputs = value.hcursor.downField("inputs").focus.flatMap(_.asArray).getOrElse(Vector.empty).map { item =>
        val raw = item.hcursor.get[String]("path").toOption.getOrElse(_failure("receipt input path missing"))
        item.mapObject(_.add("path", Json.fromString(_reference_(raw))))
      }
      value.mapObject(_.add("inputs", Json.fromValues(inputs)))
    }
    if (references.exists(value => !used.contains(value.from))) _failure("reference mapping was not used by a renderer command or receipt input")
    receipt.map(value => walked.mapObject(_.add("receipt", value))).getOrElse(walked)
  }

  private def _relative(value: String, label: String): Path = {
    val path = try Paths.get(value) catch { case NonFatal(_) => _failure(s"invalid $label: $value") }
    if (value.isEmpty || path.isAbsolute || path.normalize() != path || path.toString == "." || path.iterator().hasNext && path.getName(0).toString == "..")
      _failure(s"$label must be a normalized relative path: $value")
    path
  }

  private def _physical(path: Path): Path = {
    val absolute = path.toAbsolutePath.normalize()
    val ancestor = Iterator.iterate(Option(absolute))(_.flatMap(value => Option(value.getParent)))
      .takeWhile(_.nonEmpty).flatten.find(value => Files.exists(value, LinkOption.NOFOLLOW_LINKS))
      .getOrElse(_failure(s"path has no existing ancestor: $path"))
    try ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize()
    catch { case NonFatal(error) => _failure(s"cannot resolve path $path: ${error.getMessage}") }
  }

  private def _check_parents(root: Path, parent: Path): Unit = {
    var current = parent
    while (current != null && current.startsWith(root)) {
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
        _failure(s"destination parent is not a direct directory: $current")
      current = current.getParent
    }
  }

  private def _overlaps(left: Path, right: Path): Boolean = left.startsWith(right) || right.startsWith(left)
  private def _failure(message: String): Nothing = CozyDocumentProject._failure("DP-PATH-001", message)
}
