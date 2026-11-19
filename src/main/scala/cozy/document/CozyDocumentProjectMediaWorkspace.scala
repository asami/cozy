package cozy.document

import cozy.media.{CozyMedia, CozyMediaSlideIr}
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}
import scala.util.control.NonFatal

/** Materializes the selected native media inputs into an owned external run. */
private[cozy] object CozyDocumentProjectMediaWorkspace {
  def build(project: Path, workspace: CozyDocumentProjectWorkRoot.Run, target: String, locale: String, processRunner: Option[CozyMedia.ProcessRunner] = None): String =
    buildCandidate(project, workspace, target, locale, processRunner).report

  def buildCandidate(project: Path, workspace: CozyDocumentProjectWorkRoot.Run, target: String, locale: String, processRunner: Option[CozyMedia.ProcessRunner] = None, validateOnly: Boolean = false): CozyDocumentProjectPackage.Candidate = {
    if (!Set("ja", "en").contains(locale))
      _failure("media build requires --locale ja or en")
    val mediafile = project.resolve("media.yaml")
    val descriptor = CozyDocumentProject._load_json(mediafile, "media descriptor")
    val resources = descriptor.hcursor.downField("resources").focus.flatMap(_.asArray).getOrElse(_failure("media resources are missing"))
    val id = if (Set("infographic", "article-pdf", "summary-slides").contains(target)) s"$target-$locale" else target
    def _resource_(name: String): Json =
      resources.find(_.hcursor.get[String]("id").toOption.contains(name)).getOrElse(_failure(s"media target is missing: $name"))
    def _kind_(value: Json): String = value.hcursor.get[String]("build").toOption.getOrElse(_failure("media build is missing"))
    val selected = _resource_(id)
    val cursor = selected.hcursor
    val kind = _kind_(selected)
    if (!Set("svg-to-png", "article-pdf", "summary-slides-pdf").contains(kind) || cursor.get[String]("language").toOption != Some(locale))
      _failure(s"media target does not match selected product and locale: $id/$locale")
    val summary = if (kind == "summary-slides-pdf") Some(cursor.downField("summarySlidesPdf")) else None
    val articleid = summary.map(_.get[String]("articlePdf").toOption.getOrElse(_failure("summary article PDF is missing")))
      .orElse(if (kind == "article-pdf") Some(id) else None)
    val article = articleid.map(_resource_)
    val infographicid = summary.map(_.get[String]("infographic").toOption.getOrElse(_failure("summary infographic is missing")))
      .orElse(article.flatMap(_.hcursor.downField("articlePdf").get[String]("infographic").toOption))
      .orElse(if (kind == "svg-to-png") Some(id) else None)
      .getOrElse(_failure("media infographic is missing"))
    val infographic = _resource_(infographicid)
    if (_kind_(infographic) != "svg-to-png" || infographic.hcursor.get[String]("language").toOption != Some(locale))
      _failure(s"media infographic must be a locale-matched SVG conversion: $infographicid")
    val articleinputs = article.toVector.flatMap { value =>
      val articlecursor = value.hcursor
      if (_kind_(value) != "article-pdf" || articlecursor.get[String]("language").toOption != Some(locale) ||
          articlecursor.downField("articlePdf").get[String]("infographic").toOption != Some(infographicid))
        _failure("article PDF and infographic do not match the selected locale")
      val renderer = articlecursor.downField("articlePdf").downField("renderer")
      _admit_renderer(project, renderer, "simplemodeling-smartdox-pdf", "1", "render-pdf")
      _article_inputs(project, articlecursor.get[String]("source").toOption.getOrElse(_failure("article PDF source is missing")))
    }
    val assets = summary.toVector.flatMap { configuration =>
      if (configuration.get[String]("contract").toOption != Some("slide-ir-v1"))
        _failure("external summary currently requires the slide-ir-v1 contract")
      val profileid = configuration.get[String]("profile").toOption.getOrElse(_failure("summary profile is missing"))
      val renderer = descriptor.hcursor.downField("profiles").downField(profileid).downField("presentation").downField("renderer")
      _admit_renderer(project, renderer, "simplemodeling-summary", "2", "render-summary")
      val source = cursor.get[String]("source").toOption.getOrElse(_failure("summary source is missing"))
      val path = project.resolve(source).normalize()
      if (!path.startsWith(project) || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        _failure(s"summary source is unavailable: $source")
      CozyMediaSlideIr.load(path).assetIds.map(_resource_)
    }
    assets.foreach { value =>
      if (!Set("svg-to-png", "copy", "prebuilt").contains(_kind_(value)))
        _failure("summary asset producer cannot run in the external workspace")
    }
    val declaredoutputs = resources.flatMap { value =>
      value.hcursor.get[String]("output").toOption.map(raw => project.resolve(raw).normalize() -> value)
    }
    val articlefigures = articleinputs.flatMap { path =>
      val producers = declaredoutputs.collect { case (output, value) if output == path => value }
      if (producers.size > 1) _failure(s"article image has multiple producers: $path")
      producers.headOption.map { value =>
        if (!Set("svg-to-png", "copy").contains(_kind_(value)))
          _failure(s"article image producer is not admitted: $path")
        value.hcursor.get[String]("id").toOption.getOrElse(_failure("article image producer id is missing"))
      }
    }.distinct
    val buildids = (Vector(infographicid) ++ assets.map(_.hcursor.get[String]("id").toOption.getOrElse(_failure("summary asset id is missing"))) ++ articlefigures ++ articleid.toVector ++ Vector(id)).distinct
    val projected = buildids.map(_resource_)
    val root = workspace.root.resolve("project.dox")
    val sources = projected.flatMap(_.hcursor.get[String]("source").toOption) ++
      descriptor.hcursor.downField("knowledge").get[String]("source").toOption.toVector ++
      summary.toVector.map { configuration =>
        val profileid = configuration.get[String]("profile").toOption.getOrElse(_failure("summary profile is missing"))
        descriptor.hcursor.downField("profiles").downField(profileid).downField("presentation").get[String]("template").toOption
          .getOrElse(_failure("summary template is missing"))
      }
    val generatedpaths = declaredoutputs.map(_._1).toSet
    val articlefiles = articleinputs.filterNot(generatedpaths.contains).map { value =>
      if (!Files.isRegularFile(value, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(value))
        _failure(s"article image requires a resolvable project-relative input: $value")
      _direct_input(project, project.relativize(value).toString)
    }
    val inputpaths = (sources.map(value => _direct_input(project, value)) ++ articlefiles).distinct
    inputpaths.foreach { value =>
      if (generatedpaths.contains(value)) _failure(s"media source is also a declared generated output: $value")
      if (value.toString.toLowerCase(java.util.Locale.ROOT).endsWith(".svg")) _validate_svg(value)
    }
    // Producers run against a fresh copy: admit every declared output before copying inputs.
    val outputs = projected.flatMap { value =>
      val resource = value.hcursor
      val ordinary = resource.get[String]("output").toOption.toVector
      val config = resource.downField("summarySlidesPdf")
      val sides = if (_kind_(value) == "summary-slides-pdf")
        Vector("slideImages", "montage", "rendererManifest", "pptx").flatMap(name => config.get[String](name).toOption)
      else Vector.empty
      ordinary ++ sides
    }.map { value =>
      val relative = try java.nio.file.Paths.get(value) catch { case NonFatal(_) => _failure(s"invalid media output path: $value") }
      val path = project.resolve(relative).normalize()
      if (relative.isAbsolute || relative.toString.isEmpty || !path.startsWith(project) || path == project)
        _failure(s"media output escapes its project: $value")
      path
    }
    val reserved = inputpaths :+ project.resolve("media.yaml")
    def _overlaps_(left: Path, right: Path): Boolean = left.startsWith(right) || right.startsWith(left)
    outputs.zipWithIndex.foreach { case (path, index) =>
      if (reserved.exists(value => _overlaps_(path, value)) || outputs.take(index).exists(value => _overlaps_(path, value)))
        _failure(s"media output collides with an input or another output: $path")
    }
    Files.createDirectory(root)
    inputpaths.foreach { source =>
      val destination = root.resolve(project.relativize(source))
      Files.createDirectories(destination.getParent)
      Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES)
    }
    val inputs = descriptor.hcursor.downField("receipt").downField("inputs").focus.flatMap(_.asArray).getOrElse(Vector.empty)
    val rewritten = inputs.zipWithIndex.map { case (input, index) =>
      val value = input.hcursor.get[String]("path").toOption.getOrElse(_failure("media receipt input path is missing"))
      val relative = try java.nio.file.Paths.get(value) catch { case NonFatal(_) => _failure(s"invalid media receipt input path: $value") }
      if (relative.isAbsolute) _failure(s"media receipt input must be relative: $value")
      val source = project.resolve(relative).normalize()
      if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
        _failure(s"media receipt input is unavailable: $source")
      val destination = workspace.root.resolve("shared-inputs").resolve(index.toString).resolve(source.getFileName)
      Files.createDirectories(destination.getParent)
      Files.copy(source, destination)
      val name = root.relativize(destination).toString.replace('\\', '/')
      input.mapObject(_.add("path", Json.fromString(name)))
    }
    val result = descriptor.mapObject { fields =>
      val withresources = fields.add("resources", Json.fromValues(projected))
      fields("receipt") match {
        case Some(receipt) => withresources.add("receipt", receipt.mapObject(_.add("inputs", Json.fromValues(rewritten))))
        case None => withresources
      }
    }
    val materialized = root.resolve("media.yaml")
    Files.writeString(materialized, result.noSpaces, StandardCharsets.UTF_8)
    if (validateOnly) return CozyDocumentProjectPackage.Candidate(materialized, "validated media inputs")
    val runner = processRunner.getOrElse(CozyMedia.ProcessRunner.external(workspace, project))
    val report = buildids.map(value => CozyMedia.build(CozyMedia.CommandConfig(materialized, target = Some(value)), runner)).mkString("\n")
    val output = cursor.get[String]("output").toOption.getOrElse(_failure("selected media output is missing"))
    val candidate = root.resolve(output).normalize()
    if (!candidate.startsWith(root) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
      _failure(s"selected media candidate is unavailable: $candidate")
    CozyDocumentProjectPackage.Candidate(candidate, report)
  }

  private def _admit_renderer(project: Path, renderer: io.circe.ACursor, name: String, version: String, filename: String): Unit = {
    val command = renderer.get[Vector[String]]("command").toOption.getOrElse(_failure(s"$name renderer command is missing"))
    if (renderer.get[String]("name").toOption != Some(name) || renderer.get[String]("version").toOption != Some(version) || command.isEmpty)
      _failure(s"renderer has no admitted external-workspace capability: $name")
    val executable = try java.nio.file.Paths.get(command.head) catch { case NonFatal(_) => _failure(s"invalid renderer path: ${command.head}") }
    val resolved = if (executable.isAbsolute) executable else project.resolve(executable).normalize()
    if (!Option(resolved.getFileName).exists(_.toString == filename) || !Files.isRegularFile(resolved) || !Files.isExecutable(resolved))
      _failure(s"renderer is unavailable: $resolved")
  }

  private def _article_inputs(project: Path, value: String): Vector[Path] = {
    val source = _direct_input(project, value)
    val text = Files.readString(source, StandardCharsets.UTF_8)
    val reference = "\\[\\[([^\\]]+)\\]\\]".r
    reference.findAllMatchIn(text).flatMap { matched =>
      val name = matched.group(1).takeWhile(_ != ']')
      val lower = name.toLowerCase(java.util.Locale.ROOT)
      if (lower.matches(".*\\.(png|jpe?g|svg|gif|webp)")) {
        val path = java.nio.file.Paths.get(name)
        val resolved = source.getParent.resolve(path).normalize()
        if (path.isAbsolute || !resolved.startsWith(project))
          _failure(s"article image requires a resolvable project-relative input: $name")
        Some(resolved)
      } else None
    }.toVector
  }

  private def _direct_input(project: Path, value: String): Path = {
    val relative = try java.nio.file.Paths.get(value) catch { case NonFatal(_) => _failure(s"invalid media input path: $value") }
    val path = project.resolve(relative).normalize()
    if (relative.isAbsolute || !path.startsWith(project) || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _failure(s"media input is unavailable: $value")
    val physical = try path.toRealPath() catch { case NonFatal(_) => _failure(s"media input cannot be resolved: $value") }
    if (!physical.startsWith(project.toRealPath())) _failure(s"media input escapes its project: $value")
    path
  }

  private def _validate_svg(path: Path): Unit = {
    val document = try {
      val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
      factory.setNamespaceAware(true)
      factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      factory.setXIncludeAware(false)
      factory.setExpandEntityReferences(false)
      try factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "") catch { case _: IllegalArgumentException => () }
      try factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") catch { case _: IllegalArgumentException => () }
      val builder = factory.newDocumentBuilder()
      builder.setEntityResolver(new org.xml.sax.EntityResolver {
        def resolveEntity(publicId: String, systemId: String): org.xml.sax.InputSource =
          throw new org.xml.sax.SAXException("External XML entity resolution is forbidden")
      })
      builder.parse(path.toFile)
    } catch { case NonFatal(_) => _failure(s"SVG is invalid or unsafe: $path") }

    def _target_(target: String): Unit = {
      val value = target.trim
      if (!value.matches("#[A-Za-z_][A-Za-z0-9_.:-]*") &&
          !value.matches("(?i)data:[a-z0-9.+-]+/[a-z0-9.+-]+;base64,[a-z0-9+/=\\s]+"))
        _failure(s"SVG external or opaque resource is not materialized: $path: $target")
    }
    def _css_(css: String): Unit = {
      // Escapes, comments and imports can hide a URL from a lexical scan.
      if (css.contains("\\") || css.contains("/*") || "(?i)@import\\b".r.findFirstIn(css).nonEmpty)
        _failure(s"SVG contains opaque CSS resource syntax: $path")
      val reference = "(?i)url\\(\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s)'\"]+))\\s*\\)".r
      reference.findAllMatchIn(css).foreach { value =>
        _target_(Option(value.group(1)).orElse(Option(value.group(2))).getOrElse(value.group(3)))
      }
      if ("(?i)url\\s*\\(".r.findFirstIn(reference.replaceAllIn(css, "")).nonEmpty)
        _failure(s"SVG contains opaque CSS URL syntax: $path")
    }
    def _visit_(node: org.w3c.dom.Node): Unit = {
      if (node.getNodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        val attributes = node.getAttributes
        (0 until attributes.getLength).foreach { index =>
          val attribute = attributes.item(index)
          val name = Option(attribute.getLocalName).getOrElse(attribute.getNodeName).toLowerCase(java.util.Locale.ROOT)
          if (name == "href" || name == "src") _target_(attribute.getNodeValue)
          else _css_(attribute.getNodeValue)
        }
        if (Option(node.getLocalName).getOrElse(node.getNodeName).equalsIgnoreCase("style")) _css_(node.getTextContent)
      }
      val children = node.getChildNodes
      (0 until children.getLength).foreach(index => _visit_(children.item(index)))
    }
    _visit_(document.getDocumentElement)
  }

  private def _failure(message: String): Nothing = CozyDocumentProject._failure("DP-OP-001", message)
}
