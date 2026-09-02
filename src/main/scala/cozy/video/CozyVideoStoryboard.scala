package cozy.video

import com.fasterxml.jackson.core.{JsonFactory, JsonParseException, JsonParser => JacksonParser}
import cozy.media.CozyVisualPage
import cozy.runtime.CozyCliArgs
import io.circe.{Json, JsonObject}
import io.circe.parser
import java.nio.ByteBuffer
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path}
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

/*
 * @since   Aug. 26, 2026
 *  version Aug. 27, 2026
 * @version Sep.  2, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoStoryboard extends CozyVideoStoryboardParsing {
  final case class Storyboard(
    schema: String,
    version: Int,
    scenes: Vector[StoryboardScene]
  )

  final case class StoryboardScene(
    id: String,
    order: Int,
    section: String,
    speaker: String,
    role: String,
    narration: String,
    screen: StoryboardScreenValue,
    caption: String,
    duration: BigDecimal,
    leadSilence: BigDecimal,
    transition: String,
    productionInserts: Vector[StoryboardProductionInsert],
    diagramRefs: Vector[String],
    assetRefs: Vector[String],
    pronunciationNotes: Vector[StoryboardPronunciationNote],
    direction: String
  )

  sealed trait StoryboardScreenValue {
    def heading: String
    def content: String
  }

  final case class StoryboardScreen(
    heading: String,
    content: String
  ) extends StoryboardScreenValue

  final case class StoryboardTextScreen(
    heading: String,
    content: String
  ) extends StoryboardScreenValue

  final case class StoryboardVisualPageScreen(
    source: String,
    catalog: String,
    pageId: String
  ) extends StoryboardScreenValue {
    def heading: String = ""
    def content: String = ""
  }

  final case class StoryboardProductionInsert(
    id: String,
    kind: String,
    value: String
  )

  final case class StoryboardPronunciationNote(
    surface: String,
    reading: String
  )

  final case class StoryboardDiagnostic(
    code: String,
    path: String,
    reason: String,
    location: Option[String] = None
  ) {
    def render: String =
      s"$code $path: $reason" + location.fold("")(x => s" ($x)")
  }

  final case class StoryboardResult(
    storyboard: Option[Storyboard],
    diagnostics: Vector[StoryboardDiagnostic]
  ) {
    def isValid: Boolean = storyboard.isDefined && diagnostics.isEmpty
    def semanticIdentity: Option[String] = storyboard.filter(_ => diagnostics.isEmpty).map(storyboardIdentity)
  }

  final case class StoryboardValidateConfig(input: Path)
  object StoryboardValidateConfig {
    def create(args: List[String]): StoryboardValidateConfig =
      StoryboardValidateConfig(_required_input(args, "validate"))
  }

  final case class StoryboardInspectConfig(input: Path)
  object StoryboardInspectConfig {
    def create(args: List[String]): StoryboardInspectConfig =
      StoryboardInspectConfig(_required_input(args, "inspect"))
  }

  final case class StoryboardConvertConfig(input: Path, output: Path)
  object StoryboardConvertConfig {
    def create(args: List[String]): StoryboardConvertConfig = {
      val parsed = CozyCliArgs.parseStrict(
        org.goldenport.cli.spec.Parameter.argumentFile("input"),
        org.goldenport.cli.spec.Parameter.property("save")
      )(args)
      val input = parsed.argument("input").map(CozyCliArgs.toPath).getOrElse(
        throw new IllegalArgumentException("Missing input for video storyboard convert")
      )
      StoryboardConvertConfig(input, parsed.requiredPathProperty("save"))
    }
  }

  final case class StoryboardMigrateConfig(
    input: Path,
    from: String,
    to: String,
    screen: String,
    output: Path
  )
  object StoryboardMigrateConfig {
    def create(args: List[String]): StoryboardMigrateConfig = {
      val parsed = CozyCliArgs.parseStrict(
        org.goldenport.cli.spec.Parameter.argumentFile("input"),
        org.goldenport.cli.spec.Parameter.property("from"),
        org.goldenport.cli.spec.Parameter.property("to"),
        org.goldenport.cli.spec.Parameter.property("screen"),
        org.goldenport.cli.spec.Parameter.property("save")
      )(args)
      val input = parsed.argument("input").map(CozyCliArgs.toPath).getOrElse(
        throw new IllegalArgumentException("Missing input for video storyboard migrate")
      )
      StoryboardMigrateConfig(
        input,
        parsed.requiredProperty("from"),
        parsed.requiredProperty("to"),
        parsed.requiredProperty("screen"),
        parsed.requiredPathProperty("save")
      )
    }
  }

  def loadStoryboard(source: Path): StoryboardResult = {
    val format = _source_format(source)
    format match {
      case Left(diagnostic) => StoryboardResult(None, Vector(diagnostic))
      case Right(_) if source == null =>
        StoryboardResult(None, Vector(_diagnostic("INPUT_MISSING", "$", "Storyboard input path is required")))
      case Right(_) if !Files.exists(source, LinkOption.NOFOLLOW_LINKS) =>
        StoryboardResult(None, Vector(_diagnostic("INPUT_MISSING", "$", s"Storyboard input does not exist: $source")))
      case Right(_) if Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) =>
        StoryboardResult(None, Vector(_diagnostic("INPUT_NOT_REGULAR", "$", s"Storyboard input must be a direct regular file: $source")))
      case Right(format) =>
        val decoded = try {
          _decode_utf8(Files.readAllBytes(source), source.toString)
        } catch {
          case NonFatal(error) => Left(_diagnostic("INPUT_READ_FAILED", "$", s"Storyboard input cannot be read: ${error.getMessage}"))
        }
        decoded match {
          case Left(diagnostic) => StoryboardResult(None, Vector(diagnostic))
          case Right(text) => parseStoryboard(source, text, format)
        }
    }
  }

  def parseStoryboard(source: Path, text: String): StoryboardResult =
    _source_format(source) match {
      case Left(diagnostic) => StoryboardResult(None, Vector(diagnostic))
      case Right(format) => parseStoryboard(source, text, format)
    }

  def parseStoryboard(source: Path, text: String, format: String): StoryboardResult =
    format match {
      case "json" => _parse_json(text, source)
      case "markdown" => _parse_markdown(text, source)
      case _ => StoryboardResult(None, Vector(_diagnostic("INPUT_FORMAT_UNSUPPORTED", "$", s"Unsupported Storyboard format: $format")))
    }

  def validateStoryboard(storyboard: Storyboard): Vector[StoryboardDiagnostic] =
    _validate_storyboard(storyboard)

  def canonicalStoryboardJson(storyboard: Storyboard): String =
    _storyboard_json(storyboard).noSpaces

  def canonicalStoryboardMarkdown(storyboard: Storyboard): String = {
    if (storyboard.schema != _schema_v1 || storyboard.version != _version_v1)
      throw new IllegalArgumentException("STORYBOARD_V2_JSON_ONLY path=$ reason=Storyboard v2 has no Markdown representation")
    val lines = ArrayBuffer[String]()
    lines += "# Storyboard"
    lines += s"schema: ${_json_string(storyboard.schema)}"
    lines += s"version: ${storyboard.version}"
    storyboard.scenes.foreach { scene =>
      lines += ""
      lines += "## scene"
      lines += s"id: ${_json_string(scene.id)}"
      lines += s"order: ${scene.order}"
      lines += s"section: ${_json_string(scene.section)}"
      lines += s"speaker: ${_json_string(scene.speaker)}"
      lines += s"role: ${_json_string(scene.role)}"
      _literal_lines("narration", scene.narration).foreach(lines += _)
      lines += "screen:"
      val screen = _v1_screen(scene.screen)
      lines += s"  heading: ${_json_string(screen.heading)}"
      _literal_lines("  content", screen.content, "  ").foreach(lines += _)
      lines += s"caption: ${_json_string(scene.caption)}"
      lines += s"duration: ${_decimal_text(scene.duration)}s"
      lines += s"lead-silence: ${_decimal_text(scene.leadSilence)}s"
      lines += s"transition: ${_json_string(scene.transition)}"
      lines += s"production-inserts: ${Json.fromValues(scene.productionInserts.map(_production_insert_json)).noSpaces}"
      lines += s"diagram-refs: ${Json.fromValues(scene.diagramRefs.map(Json.fromString)).noSpaces}"
      lines += s"asset-refs: ${Json.fromValues(scene.assetRefs.map(Json.fromString)).noSpaces}"
      lines += s"pronunciation-notes: ${Json.fromValues(scene.pronunciationNotes.map(_pronunciation_note_json)).noSpaces}"
      _literal_lines("direction", scene.direction).foreach(lines += _)
    }
    lines.mkString("\n") + "\n"
  }

  def storyboardIdentity(storyboard: Storyboard): String =
    "sha256:" + _canonical_sha256_hex(canonicalStoryboardJson(storyboard).getBytes(StandardCharsets.UTF_8))

  def storyboardValidate(config: StoryboardValidateConfig): String =
    _required_storyboard(config.input).storyboard.map { storyboard =>
      Vector(
        s"schema: ${storyboard.schema}",
        s"version: ${storyboard.version}",
        s"scenes: ${storyboard.scenes.size}",
        s"identity: ${storyboardIdentity(storyboard)}"
      ).mkString("\n")
    }.getOrElse("")

  def storyboardInspect(config: StoryboardInspectConfig): String =
    _required_storyboard(config.input).storyboard.map { storyboard =>
      val lines = ArrayBuffer[String](
        s"schema: ${storyboard.schema}",
        s"version: ${storyboard.version}",
        s"scenes: ${storyboard.scenes.size}",
        s"identity: ${storyboardIdentity(storyboard)}"
      )
      storyboard.scenes.foreach { scene =>
        lines += s"scene ${scene.order}: id=${scene.id} section=${scene.section} speaker=${scene.speaker} role=${scene.role} duration=${_decimal_text(scene.duration)} leadSilence=${_decimal_text(scene.leadSilence)} transition=${scene.transition}"
        lines += s"  diagramRefs: ${scene.diagramRefs.mkString(", ")}"
        lines += s"  assetRefs: ${scene.assetRefs.mkString(", ")}"
      }
      lines.mkString("\n")
    }.getOrElse("")

  def storyboardConvert(config: StoryboardConvertConfig): String = {
    val format = _source_format(config.output) match {
      case Left(diagnostic) => throw new IllegalArgumentException(diagnostic.render)
      case Right(value) => value
    }
    val storyboard = _required_storyboard(config.input).storyboard.get
    val content = format match {
      case "json" => canonicalStoryboardJson(storyboard)
      case "markdown" => canonicalStoryboardMarkdown(storyboard)
    }
    Option(config.output.toAbsolutePath.normalize().getParent).foreach(Files.createDirectories(_))
    Files.writeString(config.output, content, StandardCharsets.UTF_8)
    Vector(
      s"output: ${config.output}",
      s"format: $format",
      s"identity: ${storyboardIdentity(storyboard)}"
    ).mkString("\n")
  }

  def storyboardMigrate(config: StoryboardMigrateConfig): String = {
    val result = _required_storyboard(config.input)
    val storyboard = result.storyboard.get
    if (config.from == "v2" && config.to == "v1" && storyboard.scenes.exists(_.screen.isInstanceOf[StoryboardVisualPageScreen]))
      throw new IllegalArgumentException("VISUAL_PAGE_SCREEN_LOSSY path=scenes.screen reason=visual-page screen cannot downgrade to v1")
    if (config.from != "v1" || config.to != "v2" || config.screen != "text")
      throw new IllegalArgumentException("STORYBOARD_MIGRATION_UNSUPPORTED path=$command reason=only --from v1 --to v2 --screen text is supported")
    if (storyboard.schema != _schema_v1 || storyboard.version != _version_v1)
      throw new IllegalArgumentException("STORYBOARD_MIGRATION_SOURCE path=schema reason=--from v1 requires cozy.video.storyboard.v1")
    if (_source_format(config.output) != Right("json"))
      throw new IllegalArgumentException("STORYBOARD_MIGRATION_OUTPUT path=$save reason=v1-to-v2 migration requires a .json output")
    val migrated = Storyboard(
      _schema_v2,
      _version_v2,
      storyboard.scenes.map { scene =>
        val screen = _v1_screen(scene.screen)
        scene.copy(screen = StoryboardTextScreen(screen.heading, screen.content))
      }
    )
    Option(config.output.toAbsolutePath.normalize().getParent).foreach(Files.createDirectories(_))
    Files.writeString(config.output, canonicalStoryboardJson(migrated), StandardCharsets.UTF_8)
    Vector(
      s"output: ${config.output}",
      s"schema: ${migrated.schema}",
      s"identity: ${storyboardIdentity(migrated)}"
    ).mkString("\n")
  }

  private def _required_input(args: List[String], command: String): Path = {
    val parsed = CozyCliArgs.parseStrict(org.goldenport.cli.spec.Parameter.argumentFile("input"))(args)
    parsed.argument("input").map(CozyCliArgs.toPath).getOrElse(
      throw new IllegalArgumentException(s"Missing Storyboard input for video storyboard $command")
    )
  }

  private def _required_storyboard(source: Path): StoryboardResult = {
    val result = loadStoryboard(source)
    if (result.isValid)
      result
    else
      throw new IllegalArgumentException(result.diagnostics.map(_.render).mkString("\n"))
  }

  private def _source_format(source: Path): Either[StoryboardDiagnostic, String] =
    if (source == null)
      Left(_diagnostic("INPUT_MISSING", "$", "Storyboard input path is required"))
    else {
      val name = Option(source.getFileName).map(_.toString).getOrElse("")
      if (name.endsWith(".json")) Right("json")
      else if (name.endsWith(".md")) Right("markdown")
      else Left(_diagnostic("INPUT_FORMAT_UNSUPPORTED", "$", s"Storyboard input must end in .json or .md: $source"))
    }

  private def _decode_utf8(bytes: Array[Byte], source: String): Either[StoryboardDiagnostic, String] =
    try {
      val decoder = StandardCharsets.UTF_8.newDecoder().
        onMalformedInput(CodingErrorAction.REPORT).
        onUnmappableCharacter(CodingErrorAction.REPORT)
      Right(decoder.decode(ByteBuffer.wrap(bytes)).toString)
    } catch {
      case _: CharacterCodingException => Left(_diagnostic("INPUT_ENCODING_INVALID", "$", s"Storyboard input is not valid UTF-8: $source"))
    }

  private def _parse_json(text: String, source: Path): StoryboardResult =
    _strict_json(text, "$", source.toString) match {
      case Left(diagnostic) => StoryboardResult(None, Vector(diagnostic))
      case Right(json) => _project_storyboard(json, source)
    }

  private def _parse_markdown(text: String, source: Path): StoryboardResult = {
    val sourcetext = source.toString
    val normalized = text.replace("\r\n", "\n")
    if (normalized.contains('\r'))
      StoryboardResult(None, Vector(_diagnostic("MARKDOWN_LINE_ENDING_INVALID", "$", "Storyboard Markdown may use LF or CRLF line endings", Some(sourcetext))))
    else {
      val split = normalized.split("\n", -1).toVector
      val lines = if (split.nonEmpty && split.last.isEmpty && normalized.endsWith("\n")) split.dropRight(1) else split
      try {
        var index = 0
        val knownscene = Set(
          "id", "order", "section", "speaker", "role", "narration", "screen", "caption", "duration",
          "lead-silence", "transition", "production-inserts", "diagram-refs", "asset-refs", "pronunciation-notes", "direction"
        )

        def _failure_(code: String, path: String, reason: String): Nothing =
          throw MarkdownFailure(_diagnostic(code, path, reason, Some(sourcetext)))

        def _next_(path: String): String =
          if (index >= lines.size) _failure_("MARKDOWN_FIELD_MISSING", path, "Required Markdown field is missing")
          else {
            val line = lines(index)
            index += 1
            line
          }

        def _field_(name: String, path: String, known: Set[String], indent: String = ""): String = {
          val line = _next_(path)
          val prefix = indent + name + ": "
          if (!line.startsWith(prefix)) {
            val supplied = line.dropWhile(_ == ' ').takeWhile(_ != ':')
            val code =
              if (known.contains(supplied)) "MARKDOWN_FIELD_ORDER"
              else "MARKDOWN_UNKNOWN_FIELD"
            _failure_(code, path, s"Expected $name in the required Storyboard field order")
          }
          line.substring(prefix.length)
        }

        def _marker_(name: String, path: String, known: Set[String], indent: String = ""): Unit = {
          val line = _next_(path)
          if (line != s"$indent$name: |") {
            val supplied = line.dropWhile(_ == ' ').takeWhile(_ != ':')
            val code = if (known.contains(supplied)) "MARKDOWN_LITERAL_INVALID" else "MARKDOWN_UNKNOWN_FIELD"
            _failure_(code, path, s"Expected $name: | literal marker")
          }
        }

        def _literal_(name: String, path: String, known: Set[String], indent: String = ""): String = {
          _marker_(name, path, known, indent)
          val value = ArrayBuffer[String]()
          val contentindent = indent + "  "
          while (index < lines.size && lines(index).startsWith(contentindent)) {
            value += lines(index).drop(contentindent.length)
            index += 1
          }
          value.mkString("\n")
        }

        def _json_string_(name: String, path: String, known: Set[String], indent: String = ""): Json = {
          val value = _field_(name, path, known, indent)
          _strict_json(value, path, sourcetext) match {
            case Left(diagnostic) => throw MarkdownFailure(diagnostic)
            case Right(json) if json.asString.isDefined => json
            case Right(_) => _failure_("MARKDOWN_TYPE_INVALID", path, s"$name must be a JSON string")
          }
        }

        def _json_array_(name: String, path: String, known: Set[String]): Json = {
          val value = _field_(name, path, known)
          _strict_json(value, path, sourcetext) match {
            case Left(diagnostic) => throw MarkdownFailure(diagnostic)
            case Right(json) if json.asArray.isDefined => json
            case Right(_) => _failure_("MARKDOWN_TYPE_INVALID", path, s"$name must be a JSON array")
          }
        }

        def _integer_(name: String, path: String, known: Set[String]): Json = {
          val value = _field_(name, path, known)
          try {
            val number = BigDecimal(value)
            if (!_is_integer(number) || number.isValidInt == false)
              _failure_("MARKDOWN_TYPE_INVALID", path, s"$name must be an integer")
            Json.fromInt(number.toInt)
          } catch {
            case _: NumberFormatException => _failure_("MARKDOWN_TYPE_INVALID", path, s"$name must be an integer")
          }
        }

        def _timing_(name: String, path: String, known: Set[String]): Json = {
          val value = _field_(name, path, known)
          val pattern = "^[0-9]+(?:\\.[0-9]+)?s$".r
          if (!pattern.pattern.matcher(value).matches())
            _failure_("TIMING_FORMAT_INVALID", path, s"$name must be a decimal number of seconds ending in s")
          try Json.fromBigDecimal(BigDecimal(value.dropRight(1)))
          catch {
            case _: NumberFormatException => _failure_("TIMING_FORMAT_INVALID", path, s"$name must be a decimal number of seconds ending in s")
          }
        }

        if (_next_("$") != "# Storyboard")
          _failure_("MARKDOWN_HEADING_INVALID", "$", "Storyboard Markdown must begin with # Storyboard")
        val schema = _json_string_("schema", "schema", Set("schema", "version"))
        val version = _integer_("version", "version", Set("schema", "version"))
        if (_next_("$") != "")
          _failure_("MARKDOWN_RECORD_SEPARATOR_INVALID", "$", "A blank line must separate root fields from the first scene")

        val scenes = ArrayBuffer[Json]()
        while (index < lines.size) {
          if (_next_(s"scenes[${scenes.size}]") != "## scene")
            _failure_("MARKDOWN_HEADING_INVALID", s"scenes[${scenes.size}]", "Each Storyboard scene must begin with ## scene")
          val path = s"scenes[${scenes.size}]"
          val id = _json_string_("id", s"$path.id", knownscene)
          val order = _integer_("order", s"$path.order", knownscene)
          val section = _json_string_("section", s"$path.section", knownscene)
          val speaker = _json_string_("speaker", s"$path.speaker", knownscene)
          val role = _json_string_("role", s"$path.role", knownscene)
          val narration = Json.fromString(_literal_("narration", s"$path.narration", knownscene))
          if (_next_(s"$path.screen") != "screen:")
            _failure_("MARKDOWN_FIELD_ORDER", s"$path.screen", "Expected screen: in the required Storyboard field order")
          val heading = _json_string_("heading", s"$path.screen.heading", Set("heading", "content"), "  ")
          val content = Json.fromString(_literal_("content", s"$path.screen.content", Set("heading", "content"), "  "))
          val caption = _json_string_("caption", s"$path.caption", knownscene)
          val duration = _timing_("duration", s"$path.duration", knownscene)
          val leadsilence = _timing_("lead-silence", s"$path.leadSilence", knownscene)
          val transition = _json_string_("transition", s"$path.transition", knownscene)
          val inserts = _json_array_("production-inserts", s"$path.productionInserts", knownscene)
          val diagrams = _json_array_("diagram-refs", s"$path.diagramRefs", knownscene)
          val assets = _json_array_("asset-refs", s"$path.assetRefs", knownscene)
          val pronunciations = _json_array_("pronunciation-notes", s"$path.pronunciationNotes", knownscene)
          val direction = Json.fromString(_literal_("direction", s"$path.direction", knownscene))
          scenes += Json.obj(
            "id" -> id,
            "order" -> order,
            "section" -> section,
            "speaker" -> speaker,
            "role" -> role,
            "narration" -> narration,
            "screen" -> Json.obj("heading" -> heading, "content" -> content),
            "caption" -> caption,
            "duration" -> duration,
            "leadSilence" -> leadsilence,
            "transition" -> transition,
            "productionInserts" -> inserts,
            "diagramRefs" -> diagrams,
            "assetRefs" -> assets,
            "pronunciationNotes" -> pronunciations,
            "direction" -> direction
          )
          if (index < lines.size) {
            if (_next_("$") != "")
              _failure_("MARKDOWN_RECORD_SEPARATOR_INVALID", path, "A blank line must separate complete Storyboard scene records")
            if (index >= lines.size)
              _failure_("MARKDOWN_ADDITIONAL_CONTENT", "$", "Storyboard Markdown cannot end with a record separator")
          }
        }
        if (schema.asString.contains(_schema_v2))
          StoryboardResult(None, Vector(_diagnostic("STORYBOARD_V2_JSON_ONLY", "schema", "Storyboard v2 is JSON only", Some(sourcetext))))
        else
          _project_storyboard(Json.obj("schema" -> schema, "version" -> version, "scenes" -> Json.fromValues(scenes)), source)
      } catch {
        case MarkdownFailure(diagnostic) => StoryboardResult(None, Vector(diagnostic))
      }
    }
  }

  private def _strict_json(text: String, path: String, source: String): Either[StoryboardDiagnostic, Json] = {
    val input = new JsonFactory().enable(JacksonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try {
      try {
        while (input.nextToken() != null) {}
        parser.parse(text) match {
          case Right(json) => Right(json)
          case Left(error) => Left(_diagnostic("JSON_MALFORMED", path, error.message, Some(source)))
        }
      } catch {
        case error: JsonParseException =>
          val message = Option(error.getOriginalMessage).getOrElse(error.getMessage)
          val code = if (message.contains("Duplicate field")) "JSON_DUPLICATE_FIELD" else "JSON_MALFORMED"
          Left(_diagnostic(code, path, message, Some(source)))
      }
    } finally {
      input.close()
    }
  }

  private def _project_storyboard(json: Json, source: Path): StoryboardResult = {
    val diagnostics = ArrayBuffer[StoryboardDiagnostic]()
    val root = _object(json, "$", diagnostics)
    root.foreach(_required_fields(_, Vector("schema", "version", "scenes"), "$", diagnostics))
    val schema = root.flatMap(_string_field(_, "schema", "schema", diagnostics))
    val version = root.flatMap(_integer_field(_, "version", "version", diagnostics))
    val scenesjson = root.flatMap(_array_field(_, "scenes", "scenes", diagnostics))
    val scenes = scenesjson.flatMap { values =>
      val parsed = values.zipWithIndex.map { case (value, index) =>
        _scene(value, s"scenes[$index]", diagnostics, schema.contains(_schema_v2))
      }
      _sequence(parsed)
    }
    val storyboard = for {
      schemavalue <- schema
      versionvalue <- version
      scenesvalue <- scenes
    } yield Storyboard(schemavalue, versionvalue, scenesvalue)
    storyboard.foreach(value => diagnostics ++= _validate_storyboard(value))
    storyboard.filter(_ => diagnostics.isEmpty).foreach(value => diagnostics ++= _validate_visual_page_screens(value, source))
    StoryboardResult(storyboard.filter(_ => diagnostics.isEmpty), diagnostics.toVector)
  }

  private def _scene(json: Json, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic], v2: Boolean): Option[StoryboardScene] =
    _object(json, path, diagnostics).flatMap { obj =>
      _required_fields(
        obj,
        Vector(
          "id", "order", "section", "speaker", "role", "narration", "screen", "caption", "duration",
          "leadSilence", "transition", "productionInserts", "diagramRefs", "assetRefs", "pronunciationNotes", "direction"
        ),
        path,
        diagnostics
      )
      val id = _string_field(obj, "id", s"$path.id", diagnostics)
      val order = _integer_field(obj, "order", s"$path.order", diagnostics)
      val section = _string_field(obj, "section", s"$path.section", diagnostics)
      val speaker = _string_field(obj, "speaker", s"$path.speaker", diagnostics)
      val role = _string_field(obj, "role", s"$path.role", diagnostics)
      val narration = _string_field(obj, "narration", s"$path.narration", diagnostics)
      val screen = _json_field(obj, "screen", s"$path.screen", diagnostics).flatMap { value =>
        if (v2) _screen_v2(value, s"$path.screen", diagnostics) else _screen(value, s"$path.screen", diagnostics)
      }
      val caption = _string_field(obj, "caption", s"$path.caption", diagnostics)
      val duration = _decimal_field(obj, "duration", s"$path.duration", diagnostics)
      val leadsilence = _decimal_field(obj, "leadSilence", s"$path.leadSilence", diagnostics)
      val transition = _string_field(obj, "transition", s"$path.transition", diagnostics)
      val inserts = _array_field(obj, "productionInserts", s"$path.productionInserts", diagnostics).flatMap { values =>
        _sequence(values.zipWithIndex.map { case (value, index) => _production_insert(value, s"$path.productionInserts[$index]", diagnostics) })
      }
      val diagrams = _array_field(obj, "diagramRefs", s"$path.diagramRefs", diagnostics).flatMap { values =>
        _sequence(values.zipWithIndex.map { case (value, index) => _string(value, s"$path.diagramRefs[$index]", diagnostics) })
      }
      val assets = _array_field(obj, "assetRefs", s"$path.assetRefs", diagnostics).flatMap { values =>
        _sequence(values.zipWithIndex.map { case (value, index) => _string(value, s"$path.assetRefs[$index]", diagnostics) })
      }
      val pronunciations = _array_field(obj, "pronunciationNotes", s"$path.pronunciationNotes", diagnostics).flatMap { values =>
        _sequence(values.zipWithIndex.map { case (value, index) => _pronunciation_note(value, s"$path.pronunciationNotes[$index]", diagnostics) })
      }
      val direction = _string_field(obj, "direction", s"$path.direction", diagnostics)
      for {
        idvalue <- id
        ordervalue <- order
        sectionvalue <- section
        speakervalue <- speaker
        rolevalue <- role
        narrationvalue <- narration
        screenvalue <- screen
        captionvalue <- caption
        durationvalue <- duration
        leadsilencevalue <- leadsilence
        transitionvalue <- transition
        insertsvalue <- inserts
        diagramsvalue <- diagrams
        assetsvalue <- assets
        pronunciationsvalue <- pronunciations
        directionvalue <- direction
      } yield StoryboardScene(
        idvalue,
        ordervalue,
        sectionvalue,
        speakervalue,
        rolevalue,
        narrationvalue,
        screenvalue,
        captionvalue,
        durationvalue,
        leadsilencevalue,
        transitionvalue,
        insertsvalue,
        diagramsvalue,
        assetsvalue,
        pronunciationsvalue,
        directionvalue
      )
    }

  private def _screen(json: Json, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[StoryboardScreen] =
    _object(json, path, diagnostics).flatMap { obj =>
      _required_fields(obj, Vector("heading", "content"), path, diagnostics)
      for {
        heading <- _string_field(obj, "heading", s"$path.heading", diagnostics)
        content <- _string_field(obj, "content", s"$path.content", diagnostics)
      } yield StoryboardScreen(heading, content)
    }

  private def _screen_v2(json: Json, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[StoryboardScreenValue] =
    _object(json, path, diagnostics).flatMap { obj =>
      _string_field(obj, "kind", s"$path.kind", diagnostics).flatMap {
        case "text" =>
          _required_fields_v2(obj, Vector("kind", "heading", "content"), path, diagnostics)
          for {
            heading <- _string_field(obj, "heading", s"$path.heading", diagnostics)
            content <- _string_field(obj, "content", s"$path.content", diagnostics)
          } yield StoryboardTextScreen(heading, content)
        case "visual-page" =>
          _required_fields_v2(obj, Vector("kind", "source", "catalog", "pageId"), path, diagnostics)
          for {
            source <- _string_field(obj, "source", s"$path.source", diagnostics)
            catalog <- _string_field(obj, "catalog", s"$path.catalog", diagnostics)
            pageid <- _string_field(obj, "pageId", s"$path.pageId", diagnostics)
          } yield StoryboardVisualPageScreen(source, catalog, pageid)
        case kind =>
          diagnostics += _diagnostic("SCREEN_KIND_UNSUPPORTED", s"$path.kind", s"Unsupported v2 screen kind $kind")
          None
      }
    }

  private def _production_insert(json: Json, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[StoryboardProductionInsert] =
    _object(json, path, diagnostics).flatMap { obj =>
      _required_fields(obj, Vector("id", "kind", "value"), path, diagnostics)
      for {
        id <- _string_field(obj, "id", s"$path.id", diagnostics)
        kind <- _string_field(obj, "kind", s"$path.kind", diagnostics)
        value <- _string_field(obj, "value", s"$path.value", diagnostics)
      } yield StoryboardProductionInsert(id, kind, value)
    }

  private def _pronunciation_note(json: Json, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[StoryboardPronunciationNote] =
    _object(json, path, diagnostics).flatMap { obj =>
      _required_fields(obj, Vector("surface", "reading"), path, diagnostics)
      for {
        surface <- _string_field(obj, "surface", s"$path.surface", diagnostics)
        reading <- _string_field(obj, "reading", s"$path.reading", diagnostics)
      } yield StoryboardPronunciationNote(surface, reading)
    }

  private def _object(json: Json, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[JsonObject] =
    json.asObject match {
      case Some(value) => Some(value)
      case None =>
        diagnostics += _diagnostic("TYPE_INVALID", path, "Expected a JSON object")
        None
    }

  private def _required_fields(obj: JsonObject, expected: Vector[String], path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Unit = {
    expected.foreach { field =>
      if (obj(field).isEmpty)
        diagnostics += _diagnostic("FIELD_MISSING", _field_path(path, field), s"Required field $field is missing")
    }
    obj.keys.foreach { field =>
      if (!expected.contains(field))
        diagnostics += _diagnostic("FIELD_UNKNOWN", _field_path(path, field), s"Unknown v1 Storyboard field $field")
    }
  }

  private def _required_fields_v2(obj: JsonObject, expected: Vector[String], path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Unit = {
    expected.foreach { field =>
      if (obj(field).isEmpty)
        diagnostics += _diagnostic("FIELD_MISSING", _field_path(path, field), s"Required field $field is missing")
    }
    obj.keys.foreach { field =>
      if (!expected.contains(field))
        diagnostics += _diagnostic("FIELD_UNKNOWN", _field_path(path, field), s"Unknown v2 Storyboard field $field")
    }
  }

  private def _json_field(obj: JsonObject, field: String, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[Json] =
    obj(field) match {
      case Some(value) => Some(value)
      case None =>
        diagnostics += _diagnostic("FIELD_MISSING", path, s"Required field $field is missing")
        None
    }

  private def _string_field(obj: JsonObject, field: String, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[String] =
    _json_field(obj, field, path, diagnostics).flatMap(_string(_, path, diagnostics))

  private def _string(json: Json, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[String] =
    json.asString match {
      case Some(value) => Some(value)
      case None =>
        diagnostics += _diagnostic("TYPE_INVALID", path, "Expected a JSON string")
        None
    }

  private def _array_field(obj: JsonObject, field: String, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[Vector[Json]] =
    _json_field(obj, field, path, diagnostics).flatMap { json =>
      json.asArray match {
        case Some(values) => Some(values)
        case None =>
          diagnostics += _diagnostic("TYPE_INVALID", path, "Expected a JSON array")
          None
      }
    }

  private def _integer_field(obj: JsonObject, field: String, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[Int] =
    _json_field(obj, field, path, diagnostics).flatMap { json =>
      json.asNumber.flatMap(_.toBigDecimal) match {
        case Some(value) if _is_integer(value) && value.isValidInt => Some(value.toInt)
        case _ =>
          diagnostics += _diagnostic("TYPE_INVALID", path, "Expected an integer")
          None
      }
    }

  private def _decimal_field(obj: JsonObject, field: String, path: String, diagnostics: ArrayBuffer[StoryboardDiagnostic]): Option[BigDecimal] =
    _json_field(obj, field, path, diagnostics).flatMap { json =>
      json.asNumber.flatMap(_.toBigDecimal) match {
        case Some(value) => Some(value)
        case None =>
          diagnostics += _diagnostic("TYPE_INVALID", path, "Expected a finite JSON number")
          None
      }
    }

  private def _sequence[A](values: Vector[Option[A]]): Option[Vector[A]] =
    if (values.forall(_.isDefined)) Some(values.flatten) else None

  private def _validate_storyboard(storyboard: Storyboard): Vector[StoryboardDiagnostic] = {
    val diagnostics = ArrayBuffer[StoryboardDiagnostic]()
    if (storyboard.schema == _schema_v1) {
      if (storyboard.version != _version_v1)
        diagnostics += _diagnostic("VERSION_UNSUPPORTED", "version", s"version must be ${_version_v1}")
    } else if (storyboard.schema == _schema_v2) {
      if (storyboard.version != _version_v2)
        diagnostics += _diagnostic("VERSION_UNSUPPORTED", "version", s"version must be ${_version_v2}")
    } else {
      diagnostics += _diagnostic("SCHEMA_UNSUPPORTED", "schema", s"schema must be ${_schema_v1} or ${_schema_v2}")
    }
    if (storyboard.scenes.isEmpty)
      diagnostics += _diagnostic("SCENES_EMPTY", "scenes", "Storyboard must contain at least one scene")
    val ids = scala.collection.mutable.Set[String]()
    val orders = scala.collection.mutable.Set[Int]()
    storyboard.scenes.zipWithIndex.foreach { case (scene, index) =>
      val path = s"scenes[$index]"
      if (!_stable_token(scene.id)) diagnostics += _diagnostic("SCENE_ID_INVALID", s"$path.id", "Scene id must be a non-empty stable token")
      if (!ids.add(scene.id)) diagnostics += _diagnostic("SCENE_ID_DUPLICATE", s"$path.id", s"Duplicate scene id ${scene.id}")
      if (scene.order <= 0) diagnostics += _diagnostic("SCENE_ORDER_INVALID", s"$path.order", "Scene order must be positive")
      if (!orders.add(scene.order)) diagnostics += _diagnostic("SCENE_ORDER_DUPLICATE", s"$path.order", s"Duplicate scene order ${scene.order}")
      if (scene.order != index + 1) diagnostics += _diagnostic("SCENE_ORDER_SEQUENCE", s"$path.order", s"Scene order must agree with sequence position ${index + 1}")
      if (!_stable_token(scene.section)) diagnostics += _diagnostic("SECTION_INVALID", s"$path.section", "Section must be a non-empty stable token")
      if (!_stable_token(scene.speaker)) diagnostics += _diagnostic("SPEAKER_INVALID", s"$path.speaker", "Speaker must be a non-empty stable token")
      if (!_roles.contains(scene.role)) diagnostics += _diagnostic("ROLE_UNSUPPORTED", s"$path.role", s"Unsupported scene role ${scene.role}")
      _validate_screen(storyboard.schema, scene.screen, s"$path.screen", diagnostics)
      if (!_timing_precision(scene.duration) || scene.duration <= 0) diagnostics += _diagnostic("DURATION_INVALID", s"$path.duration", "Duration must be positive with no more than six fractional digits")
      if (!_timing_precision(scene.leadSilence) || scene.leadSilence < 0 || scene.leadSilence > scene.duration) diagnostics += _diagnostic("LEAD_SILENCE_INVALID", s"$path.leadSilence", "Lead silence must be within duration with no more than six fractional digits")
      if (!_transitions.contains(scene.transition)) diagnostics += _diagnostic("TRANSITION_UNSUPPORTED", s"$path.transition", s"Unsupported transition ${scene.transition}")
      val insertids = scala.collection.mutable.Set[String]()
      scene.productionInserts.zipWithIndex.foreach { case (insert, insertindex) =>
        val insertpath = s"$path.productionInserts[$insertindex]"
        if (!_stable_token(insert.id)) diagnostics += _diagnostic("PRODUCTION_INSERT_ID_INVALID", s"$insertpath.id", "Production insert id must be a non-empty stable token")
        if (!insertids.add(insert.id)) diagnostics += _diagnostic("PRODUCTION_INSERT_ID_DUPLICATE", s"$insertpath.id", s"Duplicate production insert id ${insert.id}")
        if (!_insert_kinds.contains(insert.kind)) diagnostics += _diagnostic("PRODUCTION_INSERT_KIND_UNSUPPORTED", s"$insertpath.kind", s"Unsupported production insert kind ${insert.kind}")
        if (insert.value.isEmpty || _has_control_character(insert.value)) diagnostics += _diagnostic("PRODUCTION_INSERT_VALUE_INVALID", s"$insertpath.value", "Production insert value must be non-empty and contain no control character")
      }
      scene.diagramRefs.zipWithIndex.foreach { case (reference, referenceindex) =>
        if (!_safe_reference(reference)) diagnostics += _diagnostic("DIAGRAM_REFERENCE_UNSAFE", s"$path.diagramRefs[$referenceindex]", "Diagram reference must be a safe project-relative POSIX path")
      }
      scene.assetRefs.zipWithIndex.foreach { case (reference, referenceindex) =>
        if (!_safe_reference(reference)) diagnostics += _diagnostic("ASSET_REFERENCE_UNSAFE", s"$path.assetRefs[$referenceindex]", "Asset reference must be a safe project-relative POSIX path")
      }
      scene.pronunciationNotes.zipWithIndex.foreach { case (note, noteindex) =>
        val notepath = s"$path.pronunciationNotes[$noteindex]"
        if (note.surface.isEmpty) diagnostics += _diagnostic("PRONUNCIATION_SURFACE_INVALID", s"$notepath.surface", "Pronunciation surface must be non-empty")
        if (note.reading.isEmpty) diagnostics += _diagnostic("PRONUNCIATION_READING_INVALID", s"$notepath.reading", "Pronunciation reading must be non-empty")
      }
    }
    diagnostics.toVector
  }

  private def _validate_screen(
    schema: String,
    screen: StoryboardScreenValue,
    path: String,
    diagnostics: ArrayBuffer[StoryboardDiagnostic]
  ): Unit = if (schema == _schema_v1) {
    if (!screen.isInstanceOf[StoryboardScreen])
      diagnostics += _diagnostic("SCREEN_V1_INVALID", path, "Storyboard v1 screen must be exactly heading and content")
  } else if (schema == _schema_v2) {
    screen match {
      case StoryboardTextScreen(_, _) => ()
      case StoryboardVisualPageScreen(source, catalog, pageid) =>
        if (!_safe_reference(source))
          diagnostics += _diagnostic("VISUAL_PAGE_SCREEN_SOURCE_UNSAFE", s"$path.source", "Visual Page source must be a safe normalized descriptor-relative POSIX path")
        if (!_safe_reference(catalog))
          diagnostics += _diagnostic("VISUAL_PAGE_SCREEN_CATALOG_UNSAFE", s"$path.catalog", "Visual Page catalog must be a safe normalized descriptor-relative POSIX path")
        if (!_visual_page_id(pageid))
          diagnostics += _diagnostic("VISUAL_PAGE_SCREEN_PAGE_ID_INVALID", s"$path.pageId", "Visual Page pageId must be a non-empty stable token")
      case _ =>
        diagnostics += _diagnostic("SCREEN_V2_INVALID", path, "Storyboard v2 screen must declare kind text or visual-page")
    }
  }

  private def _validate_visual_page_screens(storyboard: Storyboard, source: Path): Vector[StoryboardDiagnostic] = {
    if (storyboard.schema != _schema_v2)
      Vector.empty
    else {
      val diagnostics = ArrayBuffer[StoryboardDiagnostic]()
      val root = Option(source.toAbsolutePath.normalize().getParent).getOrElse(
        source.toAbsolutePath.normalize()
      )
      storyboard.scenes.zipWithIndex.foreach {
        case (scene, index) => scene.screen match {
          case screen: StoryboardVisualPageScreen =>
            val path = s"scenes[$index].screen"
            val visualsource = _direct_visual_page_path(root, screen.source, s"$path.source", "Visual Page source", diagnostics)
            val catalog = _direct_visual_page_path(root, screen.catalog, s"$path.catalog", "Visual Page catalog", diagnostics)
            for {
              sourcepath <- visualsource
              catalogpath <- catalog
            } {
              try {
                CozyVisualPage.load(sourcepath, catalogpath).document match {
                  case set: CozyVisualPage.PageSet =>
                    val matches = set.pages.filter(_.id == screen.pageId)
                    if (matches.isEmpty)
                      diagnostics += _diagnostic("VISUAL_PAGE_SCREEN_PAGE_NOT_FOUND", s"$path.pageId", s"Visual Page pageId does not resolve: ${screen.pageId}")
                    else if (matches.size != 1)
                      diagnostics += _diagnostic("VISUAL_PAGE_SCREEN_PAGE_DUPLICATE", s"$path.pageId", s"Visual Page pageId resolves more than once: ${screen.pageId}")
                  case _ =>
                    diagnostics += _diagnostic("VISUAL_PAGE_SCREEN_SOURCE_INVALID", s"$path.source", "Visual Page source must be a cozy.visual-page-set.v1 document")
                }
              } catch {
                case NonFatal(error) =>
                  diagnostics += _diagnostic(
                    "VISUAL_PAGE_SCREEN_RESOLUTION_INVALID",
                    path,
                    Option(error.getMessage).getOrElse("Visual Page source or catalog cannot be validated")
                  )
              }
            }
          case _ => ()
        }
      }
      diagnostics.toVector
    }
  }

  private def _direct_visual_page_path(
    root: Path,
    reference: String,
    path: String,
    label: String,
    diagnostics: ArrayBuffer[StoryboardDiagnostic]
  ): Option[Path] = {
    if (!_safe_reference(reference)) {
      diagnostics += _diagnostic("VISUAL_PAGE_SCREEN_REFERENCE_UNSAFE", path, s"$label must be a safe normalized descriptor-relative POSIX path")
      None
    } else {
      val candidate = root.resolve(reference).normalize()
      if (!candidate.startsWith(root)) {
        diagnostics += _diagnostic("VISUAL_PAGE_SCREEN_REFERENCE_UNSAFE", path, s"$label escapes the Storyboard source directory")
        None
      } else if (_has_symbolic_link(root, candidate)) {
        diagnostics += _diagnostic("VISUAL_PAGE_SCREEN_INPUT_SYMLINK", path, s"$label must not use a symbolic-link path")
        None
      } else if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
        diagnostics += _diagnostic("VISUAL_PAGE_SCREEN_INPUT_NOT_REGULAR", path, s"$label must be an existing direct regular file")
        None
      } else {
        Some(candidate)
      }
    }
  }

  private def _has_symbolic_link(root: Path, candidate: Path): Boolean = {
    if (Files.isSymbolicLink(root))
      true
    else {
      var current = root
      val iterator = root.relativize(candidate).iterator()
      var found = false
      while (iterator.hasNext && !found) {
        current = current.resolve(iterator.next())
        found = Files.isSymbolicLink(current)
      }
      found
    }
  }

  private def _v1_screen(screen: StoryboardScreenValue): StoryboardScreen = screen match {
    case value: StoryboardScreen => value
    case _ => throw new IllegalArgumentException("SCREEN_V1_INVALID path=screen reason=Storyboard v1 screen must be exactly heading and content")
  }

  private def _stable_token(value: String): Boolean =
    _stable_token_pattern.pattern.matcher(value).matches()

  private def _visual_page_id(value: String): Boolean =
    _visual_page_id_pattern.pattern.matcher(value).matches()

  private def _safe_reference(value: String): Boolean =
    value.nonEmpty &&
      !_has_control_character(value) &&
      !value.startsWith("/") &&
      !value.contains("\\") &&
      !value.contains("..") &&
      !value.contains(":") &&
      !value.contains("?") &&
      !value.contains("#") &&
      _safe_reference_pattern.pattern.matcher(value).matches() &&
      value.split("/", -1).forall(segment => segment != "." && segment != "..")

  private def _has_control_character(value: String): Boolean =
    value.exists(Character.isISOControl)

  private def _field_path(path: String, field: String): String =
    if (path == "$") field else s"$path.$field"

  private def _diagnostic(code: String, path: String, reason: String, location: Option[String] = None): StoryboardDiagnostic =
    StoryboardDiagnostic(code, path, reason, location)

  private final case class MarkdownFailure(diagnostic: StoryboardDiagnostic) extends RuntimeException

  private val _schema_v1 = "cozy.video.storyboard.v1"
  private val _version_v1 = 1
  private val _schema_v2 = "cozy.video.storyboard.v2"
  private val _version_v2 = 2
  private val _roles = Set("narration", "dialogue", "direction", "system")
  private val _transitions = Set("none", "cut", "fade", "dissolve", "wipe")
  private val _insert_kinds = Set("overlay", "cutaway", "pause", "marker", "custom")
  private val _stable_token_pattern = "^[A-Za-z][A-Za-z0-9_-]*$".r
  private val _visual_page_id_pattern = "^[A-Za-z0-9][A-Za-z0-9._-]*$".r
  private val _safe_reference_pattern = "^[A-Za-z0-9][A-Za-z0-9._-]*(/[A-Za-z0-9][A-Za-z0-9._-]*)*$".r
}
