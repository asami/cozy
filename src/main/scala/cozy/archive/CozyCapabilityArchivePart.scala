package cozy.archive

import com.fasterxml.jackson.core.{JsonFactory, JsonParser, JsonToken}
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.util.Locale
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.goldenport.RAISE
import play.api.libs.json._
import cozy.modeler.CmlCapabilityProductionRequest._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCapabilityArchivePart {
  final case class Selection(directory: Path, packageCoordinate: String, packageVersion: String)

  def invocation(args: List[String]): (List[String], Option[Selection]) = {
    val names = Set("capability-dir", "capability-package-coordinate", "capability-package-version")
    val values = scala.collection.mutable.Map.empty[String, String]
    val remaining = List.newBuilder[String]
    var rest = args
    while (rest.nonEmpty) {
      val head = rest.head
      val name = head.stripPrefix("--").takeWhile(_ != '=')
      if (head.startsWith("--") && names(name)) {
        if (values.contains(name)) _reject(name, "Duplicate Capability archive option.")
        val pair = if (head.contains("=")) (head.substring(head.indexOf('=') + 1), rest.tail)
          else if (rest.tail.nonEmpty && !rest.tail.head.startsWith("--")) (rest.tail.head, rest.tail.tail)
          else _reject(name, "One literal option value is required.")
        if (!_valid_text(pair._1)) _reject(name, "Nonempty unpadded control-free literal is required.")
        values += name -> pair._1
        rest = pair._2
      } else { remaining += head; rest = rest.tail }
    }
    if (values.isEmpty) remaining.result() -> None
    else if (values.keySet != names) _reject("capability", "All three archive options must be supplied together.")
    else remaining.result() -> Some(Selection(Paths.get(values("capability-dir")),
      values("capability-package-coordinate"), values("capability-package-version")))
  }

  def entries(selection: Selection, coordinate: CozyComponentReleaseCoordinateCodec.Coordinate): Vector[(Path, String)] = {
    val metadata = selection.directory.toAbsolutePath.normalize().resolve(METADATA_PREFIX)
    _directories(metadata)
    val indexpath = metadata.resolve("index.json")
    val indexbytes = _bytes(indexpath, REQUEST_LIMIT)
    val index = _object(_json(_utf8(indexbytes, indexpath), indexpath), Set("schemaVersion", "projections"), indexpath)
    _one(index, "schemaVersion", indexpath)
    val names = (index \ "projections").asOpt[Vector[String]].getOrElse(_reject(indexpath.toString, "Ordered projection filenames are required."))
    if (names.size > SOURCE_COUNT || names.distinct.size != names.size || !names.forall(validFilename))
      _reject(indexpath.toString, "At most 256 unique safe flat projection filenames are required.")
    val stream = Files.walk(metadata)
    try stream.iterator().asScala.filterNot(_ == metadata).foreach { path =>
      if (Files.isSymbolicLink(path) || (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
        _reject(path.toString, "Nonregular or symlink metadata inventory is forbidden.")
      if (path.getFileName.toString.toLowerCase(Locale.ROOT).endsWith(".json") &&
        (path.getParent != metadata || !(names :+ "index.json").contains(path.getFileName.toString) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
        _reject(path.toString, "Unexpected, nested or unlisted metadata JSON is forbidden.")
    } finally stream.close()
    var total = indexbytes.length.toLong
    val resources = names.map { name =>
      val path = metadata.resolve(name)
      val bytes = _bytes(path, SOURCE_LIMIT)
      total += bytes.length
      if (total > TOTAL_LIMIT) _reject(metadata.toString, "Selected metadata exceeds 64MiB.")
      val root = _object(_json(_utf8(bytes, path), path), Set("schemaVersion", "generatedAbiVersion", "producer", "component", "package",
        "source", "capabilities", "declarations", "compatibility"), path)
      _one(root, "schemaVersion", path)
      if (_text(root, "generatedAbiVersion", path) != "cncf.component-capability.v1") _reject(path.toString, "Unsupported Component projection ABI.")
      val producer = _object((root \ "producer").get, Set("id", "version"), path)
      if (_text(producer, "id", path) != "cozy") _reject(path.toString, "Projection producer must be cozy.")
      _text(producer, "version", path)
      val component = _object((root \ "component").get, Set("namespace", "id", "version"), path)
      Vector("namespace", "id", "version").foreach(_text(component, _, path))
      CozyComponentReleaseCoordinateCodec.requireExact(coordinate,
        CozyComponentReleaseCoordinateCodec.readComponent(root, path.toString), path.toString)
      val pkg = _object((root \ "package").get, Set("coordinate", "version"), path)
      if (_text(pkg, "coordinate", path) != selection.packageCoordinate || _text(pkg, "version", path) != selection.packageVersion)
        _reject(path.toString, "Independent package coordinate/version disagree with the explicit selection.")
      path -> (METADATA_PREFIX + name)
    }
    (indexpath -> (METADATA_PREFIX + "index.json")) +: resources
  }

  def requireNoCollisions(entries: Vector[(Path, String)], selected: Vector[(Path, String)]): Unit = {
    val all = entries ++ selected
    val canonical = all.filter(x => x._2.toLowerCase(Locale.ROOT).startsWith(METADATA_PREFIX))
    if (canonical.map(_._2).map(_.toLowerCase(Locale.ROOT)).distinct.size != canonical.size)
      _reject("archive", "Duplicate canonical Capability archive name, including identical content.")
    entries.foreach { case (_, name) =>
      if (name.toLowerCase(Locale.ROOT).startsWith(METADATA_PREFIX) && name.toLowerCase(Locale.ROOT).endsWith(".json"))
        _reject(name, "Existing canonical-prefix JSON collides with the explicit Capability inventory.")
    }
    val selectednames = selected.map(_._2.toLowerCase(Locale.ROOT)).toSet
    if (entries.exists(x => selectednames(x._2.toLowerCase(Locale.ROOT)))) _reject("archive", "Selected archive name collision.")
  }

  private def _valid_text(value: String): Boolean = {
    if (value == null || value.isEmpty) false
    else {
      val points = value.codePoints().toArray
      def _space_(point: Int): Boolean = Character.isWhitespace(point) || Character.isSpaceChar(point)
      !_space_(points.head) && !_space_(points.last) && !points.exists(Character.isISOControl)
    }
  }
  private def _directories(path: Path): Unit =
    Iterator.iterate(path)(_.getParent).takeWhile(_ != null).foreach { item =>
      if (Files.isSymbolicLink(item) || !Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS))
        _reject(item.toString, "Selected archive root and ancestors must be regular directories without symlinks.")
    }
  private def _bytes(path: Path, limit: Int): Array[Byte] = {
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path))
      _reject(path.toString, "Selected metadata must be readable regular files without symlinks.")
    if (Files.size(path) > limit) _reject(path.toString, "Selected metadata exceeds its byte bound.")
    val bytes = Files.readAllBytes(path)
    if (bytes.length > limit) _reject(path.toString, "Selected metadata exceeds its byte bound.")
    bytes
  }
  private def _utf8(bytes: Array[Byte], path: Path): String =
    try StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString
    catch { case NonFatal(_) => _reject(path.toString, "Malformed UTF8 metadata.") }
  private def _json(text: String, path: Path): JsValue = {
    val parser = new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try {
      val token = parser.nextToken()
      if (token == null) _reject(path.toString, "Missing metadata JSON.")
      if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) parser.skipChildren()
      if (parser.nextToken() != null) _reject(path.toString, "Trailing metadata JSON tokens.")
      Json.parse(text)
    } catch { case NonFatal(_) => _reject(path.toString, "Malformed metadata JSON or duplicate key.") }
    finally parser.close()
  }
  private def _object(value: JsValue, fields: Set[String], path: Path): JsObject = value match {
    case obj: JsObject if obj.keys == fields => obj
    case _ => _reject(path.toString, "Exact closed metadata object keys are required.")
  }
  private def _one(value: JsObject, field: String, path: Path): Unit = (value \ field).get match {
    case JsNumber(number) if number == 1 && number.bigDecimal.scale() == 0 => ()
    case _ => _reject(path.toString, "Exactly integer schema version 1 is required.")
  }
  private def _text(value: JsObject, field: String, path: Path): String = (value \ field).get match {
    case JsString(text) if _valid_text(text) => text
    case _ => _reject(path.toString + ":" + field, "Exact nonempty literal metadata text is required.")
  }
  private def _reject(location: String, detail: String): Nothing =
    RAISE.invalidArgumentFault(s"Malformed location=$location detail=$detail")
}
