package cozy.media

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import java.util.zip.{CRC32, Deflater, DeflaterOutputStream}
import scala.util.control.NonFatal

/*
 * @since   Aug. 27, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVisualPagePreview {
  private final case class PreviewConfig(input: Path, catalog: Path, html: Path, png: Option[Path])
  private final case class Output(target: Path, bytes: Array[Byte])
  private final case class StagedOutput(target: Path, temporary: Path)
  private final case class Image(width: Int, height: Int, pixels: Array[Int])

  private val _schema = "cozy.visual-page.preview.v1"
  private val _generator = "cozy media visual-page preview"
  private val _white = 0xffffff
  private val _black = 0x20252b
  private val _panel = 0xf4f7fa

  def execute(args: List[String]): String = {
    val config = _config(args)
    _validate_output(config.html, "save", ".html")
    config.png.foreach(_validate_output(_, "png", ".png"))
    config.png.foreach { png =>
      if (png == config.html)
        _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command", "--save and --png must not resolve to the same output target")
    }
    val validated = CozyVisualPage.load(config.input, config.catalog)
    val htmlbytes = _html(validated).getBytes(StandardCharsets.UTF_8)
    val pngbytes = config.png.map(_ => _png(validated))
    val htmlidentity = _identity(htmlbytes)
    val pngidentity = pngbytes.map(_identity)
    val previewidentity = _preview_identity(validated, htmlidentity, pngidentity)
    val outputs = Vector(Output(config.html, htmlbytes)) ++ config.png.zip(pngbytes).map { case (path, bytes) => Output(path, bytes) }
    _replace_all(outputs)
    _report(validated, htmlidentity, pngidentity, previewidentity)
  }

  private def _config(args: List[String]): PreviewConfig = args match {
    case input :: rest if !input.startsWith("--") =>
      val inputpath = _path(input, "$command.input")
      var catalog = Option.empty[Path]
      var html = Option.empty[Path]
      var png = Option.empty[Path]
      var remaining = rest
      while (remaining.nonEmpty) {
        remaining match {
          case option :: value :: tail if option == "--catalog" || option == "--save" || option == "--png" =>
            if (value.startsWith("--")) _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command." + option.drop(2), s"$option requires one value")
            option match {
              case "--catalog" =>
                if (catalog.nonEmpty) _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command.catalog", "--catalog may appear once")
                catalog = Some(_path(value, "$command.catalog"))
              case "--save" =>
                if (html.nonEmpty) _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command.save", "--save may appear once")
                html = Some(_path(value, "$command.save"))
              case "--png" =>
                if (png.nonEmpty) _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command.png", "--png may appear once")
                png = Some(_path(value, "$command.png"))
            }
            remaining = tail
          case option :: tail if option.startsWith("--catalog=") =>
            if (catalog.nonEmpty) _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command.catalog", "--catalog may appear once")
            catalog = Some(_path(option.drop("--catalog=".length), "$command.catalog"))
            remaining = tail
          case option :: tail if option.startsWith("--save=") =>
            if (html.nonEmpty) _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command.save", "--save may appear once")
            html = Some(_path(option.drop("--save=".length), "$command.save"))
            remaining = tail
          case option :: tail if option.startsWith("--png=") =>
            if (png.nonEmpty) _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command.png", "--png may appear once")
            png = Some(_path(option.drop("--png=".length), "$command.png"))
            remaining = tail
          case option :: _ if option.startsWith("--") =>
            _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command", s"unsupported or valueless option: $option")
          case value :: _ =>
            _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command", s"unexpected positional argument: $value")
        }
      }
      PreviewConfig(
        inputpath,
        catalog.getOrElse(_fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command.catalog", "missing --catalog")),
        html.getOrElse(_fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command.save", "missing --save")),
        png
      )
    case Nil => _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command.input", "missing input file")
    case option :: _ => _fail("VISUAL_PAGE_PREVIEW_COMMAND", "$command.input", s"input must precede options: $option")
  }

  private def _html(validated: CozyVisualPage.ValidatedDocument): String = {
    val directions = validated.catalog.relations.map(relation => relation.id -> relation.direction).toMap
    val orderedpages = validated.document.pages.zipWithIndex.map { case (page, index) =>
      s"""<li data-page-id="${_escape(page.id)}">${index + 1}: <code>${_escape(page.id)}</code></li>"""
    }.mkString("\n")
    val pages = validated.document.pages.zipWithIndex.map { case (page, index) => _page_html(page, index, directions) }.mkString("\n")
    Vector(
      "<!doctype html>",
      "<html lang=\"en\">",
      "<head>",
      "<meta charset=\"utf-8\">",
      "<title>Cozy Visual Page Semantic Preview</title>",
      "<style>body{font-family:system-ui,sans-serif;margin:2rem;color:#20252b}main{max-width:72rem;margin:auto}code{font-family:ui-monospace,monospace}.identity,section{border:1px solid #ccd5de;padding:1rem;margin:1rem 0}.identity{display:flex;flex-wrap:wrap;gap:.3rem 1rem}.identity dt{font-weight:600}h1,h2,h3{margin-top:1.25rem}ol,ul{padding-left:1.5rem}.relation{border-left:4px solid #526d82;padding-left:.7rem}</style>",
      "</head>",
      "<body>",
      "<main>",
      "<h1>Cozy Visual Page Semantic Preview</h1>",
      "<dl class=\"identity\">",
      s"<dt>Document schema</dt><dd><code>${_escape(validated.document.schema)}</code></dd>",
      s"<dt>Document ID</dt><dd><code>${_escape(validated.document.id)}</code></dd>",
      s"<dt>Document identity</dt><dd><code>${_escape(validated.documentIdentity)}</code></dd>",
      s"<dt>Catalog ID</dt><dd><code>${_escape(validated.catalog.id)}</code></dd>",
      s"<dt>Catalog revision</dt><dd><code>${validated.catalog.revision}</code></dd>",
      s"<dt>Catalog identity</dt><dd><code>${_escape(validated.catalogIdentity)}</code></dd>",
      "</dl>",
      "<h2>Ordered page IDs</h2>",
      "<ol>",
      orderedpages,
      "</ol>",
      pages,
      "</main>",
      "</body>",
      "</html>",
      ""
    ).mkString("\n")
  }

  private def _page_html(page: CozyVisualPage.Page, index: Int, directions: Map[String, String]): String = {
    val nodes = page.logical.nodes.map { node =>
      val sourcerefs = node.sourceRefs.mkString(",")
      s"""<li data-node-id="${_escape(node.id)}"><code>${_escape(node.id)}</code>; role <code>${_escape(node.role)}</code>; label <q>${_escape(node.label)}</q>; sources <code>${_escape(sourcerefs)}</code></li>"""
    }.mkString("\n")
    val relations = page.logical.relations.map { relation =>
      val direction = directions.getOrElse(relation.relationType, "unresolved")
      val sourcerefs = relation.sourceRefs.mkString(",")
      s"""<li class="relation" data-relation-id="${_escape(relation.id)}"><code>${_escape(relation.id)}</code>; type <code>${_escape(relation.relationType)}</code>; direction <code>${_escape(direction)}</code>; from <code>${_escape(relation.from)}</code>; to <code>${_escape(relation.to)}</code>; sources <code>${_escape(sourcerefs)}</code></li>"""
    }.mkString("\n")
    val parameters = page.visual.parameters.sortBy(_.name).map { parameter =>
      val value = parameter.value match {
        case CozyVisualPage.NodeReference(item) => "node-ref" -> item
        case CozyVisualPage.BooleanParameter(item) => "boolean" -> item.toString
        case CozyVisualPage.StringParameter(item) => "string" -> item
      }
      s"""<li data-parameter-name="${_escape(parameter.name)}"><code>${_escape(parameter.name)}</code>: <code>${_escape(value._1)}</code> <code>${_escape(value._2)}</code></li>"""
    }.mkString("\n")
    Vector(
      s"""<section class="page" data-page-id="${_escape(page.id)}">""",
      s"<h2>Page ${index + 1}: <code>${_escape(page.id)}</code></h2>",
      s"<p>Logical pattern: <code>${_escape(page.logical.pattern)}</code>; visual pattern: <code>${_escape(page.visual.pattern)}</code>.</p>",
      "<h3>Logical nodes</h3>",
      "<ol>",
      nodes,
      "</ol>",
      "<h3>Relations</h3>",
      "<ol>",
      relations,
      "</ol>",
      "<h3>Typed visual parameters</h3>",
      "<ul>",
      parameters,
      "</ul>",
      "</section>"
    ).mkString("\n")
  }

  private def _png(validated: CozyVisualPage.ValidatedDocument): Array[Byte] = {
    val width = 960
    val heightlong = 32L + validated.document.pages.size.toLong * 144L
    if (heightlong > Int.MaxValue || heightlong * width > Int.MaxValue)
      _fail("VISUAL_PAGE_PREVIEW_PNG", "$png", "logical structure is too large for a deterministic PNG")
    val image = Image(width, heightlong.toInt, Array.fill(width * heightlong.toInt)(_white))
    validated.document.pages.zipWithIndex.foreach { case (page, index) => _draw_page(image, page, index) }
    _png_bytes(image)
  }

  private def _draw_page(image: Image, page: CozyVisualPage.Page, index: Int): Unit = {
    val top = 16 + index * 144
    _fill(image, 16, top, image.width - 32, 120, _panel)
    _fill(image, 16, top, image.width - 32, 4, _pattern_color(page.logical.pattern))
    val positions = page.logical.nodes.zipWithIndex.map { case (node, nodeindex) =>
      node.id -> (36 + nodeindex * 112, top + 56)
    }.toMap
    page.logical.relations.foreach { relation =>
      for {
        from <- positions.get(relation.from)
        to <- positions.get(relation.to)
      } _draw_arrow(image, from._1 + 48, from._2 + 24, to._1 + 48, to._2 + 24, _relation_color(relation.relationType))
    }
    page.logical.nodes.foreach { node =>
      val position = positions(node.id)
      _fill(image, position._1, position._2, 96, 48, _black)
      _fill(image, position._1 + 3, position._2 + 3, 90, 42, _node_color(node.role))
    }
  }

  private def _draw_arrow(image: Image, x0: Int, y0: Int, x1: Int, y1: Int, color: Int): Unit = {
    _draw_line(image, x0, y0, x1, y1, color)
    val dx = x1 - x0
    val dy = y1 - y0
    if (math.abs(dx) >= math.abs(dy)) {
      val point = if (dx >= 0) -8 else 8
      _draw_line(image, x1, y1, x1 + point, y1 - 5, color)
      _draw_line(image, x1, y1, x1 + point, y1 + 5, color)
    } else {
      val point = if (dy >= 0) -8 else 8
      _draw_line(image, x1, y1, x1 - 5, y1 + point, color)
      _draw_line(image, x1, y1, x1 + 5, y1 + point, color)
    }
  }

  private def _draw_line(image: Image, x0: Int, y0: Int, x1: Int, y1: Int, color: Int): Unit = {
    var x = x0
    var y = y0
    val dx = math.abs(x1 - x0)
    val sx = if (x0 < x1) 1 else -1
    val dy = -math.abs(y1 - y0)
    val sy = if (y0 < y1) 1 else -1
    var error = dx + dy
    var complete = false
    while (!complete) {
      _pixel(image, x, y, color)
      if (x == x1 && y == y1) complete = true
      else {
        val twice = 2 * error
        if (twice >= dy) {
          error += dy
          x += sx
        }
        if (twice <= dx) {
          error += dx
          y += sy
        }
      }
    }
  }

  private def _fill(image: Image, x: Int, y: Int, width: Int, height: Int, color: Int): Unit = {
    val xstart = math.max(0, x)
    val ystart = math.max(0, y)
    val xend = math.min(image.width, x + width)
    val yend = math.min(image.height, y + height)
    var row = ystart
    while (row < yend) {
      var column = xstart
      while (column < xend) {
        image.pixels(row * image.width + column) = color
        column += 1
      }
      row += 1
    }
  }

  private def _pixel(image: Image, x: Int, y: Int, color: Int): Unit =
    if (x >= 0 && x < image.width && y >= 0 && y < image.height)
      image.pixels(y * image.width + x) = color

  private def _png_bytes(image: Image): Array[Byte] = {
    val raw = new ByteArrayOutputStream
    var row = 0
    while (row < image.height) {
      raw.write(0)
      var column = 0
      while (column < image.width) {
        val color = image.pixels(row * image.width + column)
        raw.write((color >>> 16) & 0xff)
        raw.write((color >>> 8) & 0xff)
        raw.write(color & 0xff)
        column += 1
      }
      row += 1
    }
    val compressed = new ByteArrayOutputStream
    val deflater = new Deflater(9)
    val output = new DeflaterOutputStream(compressed, deflater)
    try {
      output.write(raw.toByteArray)
      output.finish()
    } finally {
      output.close()
    }
    val png = new ByteArrayOutputStream
    png.write(Array[Byte](137.toByte, 80.toByte, 78.toByte, 71.toByte, 13.toByte, 10.toByte, 26.toByte, 10.toByte))
    val header = new ByteArrayOutputStream
    _integer(header, image.width)
    _integer(header, image.height)
    header.write(8)
    header.write(2)
    header.write(0)
    header.write(0)
    header.write(0)
    _chunk(png, "IHDR", header.toByteArray)
    _chunk(png, "IDAT", compressed.toByteArray)
    _chunk(png, "IEND", Array.empty[Byte])
    png.toByteArray
  }

  private def _chunk(output: ByteArrayOutputStream, kind: String, bytes: Array[Byte]): Unit = {
    val name = kind.getBytes(StandardCharsets.US_ASCII)
    _integer(output, bytes.length)
    output.write(name)
    output.write(bytes)
    val crc = new CRC32
    crc.update(name)
    crc.update(bytes)
    _integer(output, crc.getValue.toInt)
  }

  private def _integer(output: ByteArrayOutputStream, value: Int): Unit = {
    output.write((value >>> 24) & 0xff)
    output.write((value >>> 16) & 0xff)
    output.write((value >>> 8) & 0xff)
    output.write(value & 0xff)
  }

  private def _report(
    validated: CozyVisualPage.ValidatedDocument,
    htmlidentity: String,
    pngidentity: Option[String],
    previewidentity: String
  ): String =
    (Vector(
      s"schema: ${_schema}",
      "version: 1",
      s"generator: ${_generator}",
      s"documentIdentity: ${validated.documentIdentity}",
      s"catalogIdentity: ${validated.catalogIdentity}",
      s"htmlIdentity: $htmlidentity"
    ) ++ pngidentity.map(identity => s"pngIdentity: $identity") ++ Vector(
      s"previewIdentity: $previewidentity",
      "status: generated"
    )).mkString("\n")

  private def _preview_identity(
    validated: CozyVisualPage.ValidatedDocument,
    htmlidentity: String,
    pngidentity: Option[String]
  ): String = _identity((Vector(
    s"schema: ${_schema}",
    s"documentIdentity: ${validated.documentIdentity}",
    s"catalogIdentity: ${validated.catalogIdentity}",
    s"htmlIdentity: $htmlidentity"
  ) ++ pngidentity.map(identity => s"pngIdentity: $identity")).mkString("\n").getBytes(StandardCharsets.UTF_8))

  private def _replace_all(outputs: Vector[Output]): Unit = {
    var staged = Vector.empty[StagedOutput]
    try {
      outputs.foreach { output =>
        val parent = Option(output.target.getParent).getOrElse(_fail("VISUAL_PAGE_PREVIEW_SAVE", "$save", "output parent is required"))
        val prefix = Option(output.target.getFileName).map(_.toString).filter(_.length >= 3).getOrElse("vpg")
        val temporary = Files.createTempFile(parent, prefix, ".tmp")
        staged :+= StagedOutput(output.target, temporary)
        Files.write(temporary, output.bytes)
      }
      staged.foreach { output =>
        try Files.move(output.temporary, output.target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        catch {
          case _: AtomicMoveNotSupportedException => _fail("VISUAL_PAGE_PREVIEW_SAVE_ATOMIC", "$save", "filesystem does not support same-directory atomic output replacement")
        }
      }
    } catch {
      case fault: CozyVisualPage.VisualPageFault => throw fault
      case NonFatal(e) => _fail("VISUAL_PAGE_PREVIEW_SAVE", "$save", Option(e.getMessage).getOrElse("cannot replace preview output"))
    } finally staged.foreach(output => Files.deleteIfExists(output.temporary))
  }

  private def _validate_output(path: Path, label: String, suffix: String): Unit = {
    val name = Option(path).flatMap(item => Option(item.getFileName)).map(_.toString).getOrElse("")
    if (!name.endsWith(suffix))
      _fail("VISUAL_PAGE_PREVIEW_OUTPUT_FORMAT", s"$$command.$label", s"output must end in $suffix")
    val parent = Option(path.getParent).getOrElse(_fail("VISUAL_PAGE_PREVIEW_SAVE", s"$$command.$label", "output parent is required"))
    if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent))
      _fail("VISUAL_PAGE_PREVIEW_SAVE", s"$$command.$label", "output parent must be an existing direct directory")
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)))
      _fail("VISUAL_PAGE_PREVIEW_SAVE", s"$$command.$label", "output must be absent or a direct regular non-symlink file")
  }

  private def _path(value: String, path: String): Path = try {
    if (value == null || value.isEmpty || value != value.trim)
      _fail("VISUAL_PAGE_PREVIEW_COMMAND", path, "path must be nonempty and trimmed")
    Paths.get(value).toAbsolutePath.normalize()
  } catch {
    case fault: CozyVisualPage.VisualPageFault => throw fault
    case NonFatal(_) => _fail("VISUAL_PAGE_PREVIEW_COMMAND", path, "path is invalid")
  }

  private def _escape(value: String): String = Option(value).getOrElse("").flatMap {
    case '&' => "&amp;"
    case '<' => "&lt;"
    case '>' => "&gt;"
    case '"' => "&quot;"
    case '\'' => "&#39;"
    case character => character.toString
  }

  private def _identity(bytes: Array[Byte]): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _pattern_color(pattern: String): Int = pattern match {
    case "sequence" => 0x2f6f9f
    case "causal-chain" => 0xa23b3b
    case "dependency-map" => 0x6650a4
    case "mapping" => 0xad651d
    case _ => _black
  }

  private def _relation_color(relationtype: String): Int = relationtype match {
    case "next" => 0x1f77b4
    case "causes" => 0xc23b22
    case "depends-on" => 0x654ea3
    case "enables" => 0x2d8a4e
    case "maps-to" => 0xb66a18
    case _ => _black
  }

  private def _node_color(role: String): Int = {
    val hash = role.foldLeft(0)((seed, character) => seed * 31 + character.toInt) & 0x00ffffff
    0x8090a0 | (hash & 0x3f3f3f)
  }

  private def _fail(code: String, path: String, reason: String): Nothing =
    throw CozyVisualPage.VisualPageFault(code, path, reason)
}
