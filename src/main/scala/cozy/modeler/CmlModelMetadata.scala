package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import play.api.libs.json.{JsArray, JsObject, Json}
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.parser.LogicalSection

/*
 * @since   Jun. 23, 2026
 * @version Jun. 23, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlModelMetadata {
  private val _schema = "cozy.cml.model-metadata.v1"

  final case class ModelMetadata(
    source: Source,
    elements: Vector[Element]
  ) {
    def toJson: JsObject =
      Json.obj(
        "schema" -> _schema,
        "source" -> source.toJson,
        "elements" -> JsArray(elements.map(_.toJson))
      )

    def toJsonString: String =
      Json.prettyPrint(toJson)

    def toYamlString: String = {
      val body = elements.map(_.toYaml("  ")).mkString
      s"""schema: ${_schema}
         |source:
         |  path: ${_yaml_scalar(source.path)}
         |  sha256: ${source.sha256}
         |  compiler: ${_yaml_scalar(source.compiler)}
         |  cozyVersion: ${_yaml_scalar(source.cozyversion)}
         |elements:
         |${body}""".stripMargin
    }
  }

  final case class Source(
    path: String,
    sha256: String,
    compiler: String,
    cozyversion: String
  ) {
    def toJson: JsObject =
      Json.obj(
        "path" -> path,
        "sha256" -> sha256,
        "compiler" -> compiler,
        "cozyVersion" -> cozyversion
      )
  }

  final case class Element(
    kind: String,
    name: String,
    termid: String,
    glossarypath: String,
    descriptive: Descriptive,
    narrative: Option[String],
    relationships: Vector[String] = Vector.empty,
    constraints: Vector[String] = Vector.empty,
    implementation: Vector[String] = Vector.empty,
    rdfcandidates: Vector[String] = Vector.empty
  ) {
    def toJson: JsObject =
      Json.obj(
        "kind" -> kind,
        "name" -> name,
        "termId" -> termid,
        "glossaryPath" -> glossarypath,
        "descriptive" -> descriptive.toJson,
        "narrative" -> Json.toJson(narrative.getOrElse("")),
        "relationships" -> Json.toJson(relationships),
        "constraints" -> Json.toJson(constraints),
        "implementation" -> Json.toJson(implementation),
        "rdfCandidates" -> Json.toJson(rdfcandidates)
      )

    def toYaml(indent: String): String = {
      s"""${indent}- kind: ${_yaml_scalar(kind)}
         |${indent}  name: ${_yaml_scalar(name)}
         |${indent}  termId: ${_yaml_scalar(termid)}
         |${indent}  glossaryPath: ${_yaml_scalar(glossarypath)}
         |${indent}  descriptive:
         |${indent}    label: ${_yaml_scalar(descriptive.label)}
         |${indent}    brief: ${_yaml_scalar(descriptive.brief.getOrElse(""))}
         |${indent}    summary: ${_yaml_scalar(descriptive.summary.getOrElse(""))}
         |${indent}    description: ${_yaml_scalar(descriptive.description.getOrElse(""))}
         |${indent}  narrative: ${_yaml_scalar(narrative.getOrElse(""))}
         |${indent}  relationships: ${_yaml_list(relationships)}
         |${indent}  constraints: ${_yaml_list(constraints)}
         |${indent}  implementation: ${_yaml_list(implementation)}
         |${indent}  rdfCandidates: ${_yaml_list(rdfcandidates)}
         |""".stripMargin
    }
  }

  final case class Descriptive(
    label: String,
    brief: Option[String],
    summary: Option[String],
    description: Option[String]
  ) {
    def toJson: JsObject =
      Json.obj(
        "label" -> label,
        "brief" -> Json.toJson(brief.getOrElse("")),
        "summary" -> Json.toJson(summary.getOrElse("")),
        "description" -> Json.toJson(description.getOrElse(""))
      )
  }

  def fromCml(source: Path, glossarycategory: String): ModelMetadata =
    fromCml(source, source.toAbsolutePath.normalize().toString, glossarycategory)

  def fromCml(source: Path, sourcepath: String, glossarycategory: String): ModelMetadata = {
    val normalized = source.toAbsolutePath.normalize()
    ModelMetadata(
      Source(
        path = sourcepath,
        sha256 = sha256(normalized),
        compiler = "cozy-modeler",
        cozyversion = org.simplemodeling.cozy.BuildInfo.version
      ),
      elements = _elements(normalized, glossarycategory)
    )
  }

  def fromKaleidox(model: KaleidoxModel, source: Path, glossarycategory: String): ModelMetadata =
    fromKaleidox(model, source, source.toAbsolutePath.normalize().toString, glossarycategory)

  def fromKaleidox(model: KaleidoxModel, source: Path, sourcepath: String, glossarycategory: String): ModelMetadata = {
    val normalized = source.toAbsolutePath.normalize()
    ModelMetadata(
      Source(
        path = sourcepath,
        sha256 = sha256(normalized),
        compiler = "cozy-modeler",
        cozyversion = org.simplemodeling.cozy.BuildInfo.version
      ),
      elements = _elements(model, glossarycategory)
    )
  }

  def write(source: Path, json: Path, yaml: Path, glossarycategory: String): Unit =
    write(source, json, yaml, source.toAbsolutePath.normalize().toString, glossarycategory)

  def write(source: Path, json: Path, yaml: Path, sourcepath: String, glossarycategory: String): Unit = {
    val metadata = fromCml(source, sourcepath, glossarycategory)
    _write(json, metadata.toJsonString + "\n")
    _write(yaml, metadata.toYamlString)
  }

  def sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var n = in.read(buffer)
      while (n >= 0) {
        if (n > 0)
          digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally {
      in.close()
    }
    digest.digest().map(b => "%02x".format(b & 0xff)).mkString
  }

  private case class Section(kind: String, name: String, lines: Vector[String])

  private def _elements(model: KaleidoxModel, glossarycategory: String): Vector[Element] =
    model.divisions.toVector.flatMap {
      case d: KaleidoxModel.EntityDivision => _logical_section_elements("entity", d.section, glossarycategory)
      case d: KaleidoxModel.ValueDivision => _logical_section_elements("value", d.section, glossarycategory)
      case d: KaleidoxModel.PowertypeDivision => _logical_section_elements("powertype", d.section, glossarycategory)
      case d: KaleidoxModel.StateMachineDivision => _logical_section_elements("statemachine", d.section, glossarycategory)
      case d: KaleidoxModel.ServiceDivision => _logical_section_elements("service", d.section, glossarycategory)
      case d: KaleidoxModel.OperationDivision => _logical_section_elements("operation", d.section, glossarycategory)
      case _ => Vector.empty
    }

  private def _logical_section_elements(kind: String, root: LogicalSection, glossarycategory: String): Vector[Element] =
    root.blocks.sections.toVector.filterNot(s => _narrative_keys.contains(s.keyForModel.toLowerCase(java.util.Locale.ROOT))).map { section =>
      val fields = _logical_fields(section)
      val summary = fields.get("summary").orElse(fields.get("brief"))
      val description = fields.get("description")
      val narrative = fields.get("narrative").orElse(fields.get("remarks")).orElse(_logical_free_narrative(section))
      val slug = _slugify(section.nameForModel)
      Element(
        kind,
        section.nameForModel,
        s"${glossarycategory}:${slug}",
        s"glossary/${glossarycategory}/${slug}.html",
        Descriptive(
          section.nameForModel,
          fields.get("brief"),
          summary,
          description.orElse(summary)
        ),
        narrative.filter(_.trim.nonEmpty),
        relationships = _field_list(fields, "relationship", "relationships"),
        constraints = _field_list(fields, "constraint", "constraints"),
        implementation = _field_list(fields, "implementation"),
        rdfcandidates = _field_list(fields, "rdf", "rdf candidates", "rdfcandidates")
      )
    }.distinct

  private val _narrative_keys = Set(
    "headline",
    "brief",
    "summary",
    "description",
    "lead",
    "content",
    "abstract",
    "remarks",
    "tooltip"
  )

  private def _logical_fields(section: LogicalSection): Map[String, String] =
    section.blocks.sections.toVector.flatMap { child =>
      val text = child.blocks.text.trim
      if (text.isEmpty)
        None
      else
        Some(child.keyForModel.toLowerCase(java.util.Locale.ROOT) -> text)
    }.toMap

  private def _logical_free_narrative(section: LogicalSection): Option[String] = {
    val text = section.blocks.prologue.text.trim
    if (text.isEmpty) None else Some(text)
  }

  private def _elements(source: Path, glossarycategory: String): Vector[Element] = {
    val sections = _sections(source)
    sections.map { section =>
      val fields = _fields(section.lines)
      val summary = fields.get("summary").orElse(fields.get("brief"))
      val description = fields.get("description")
      val free = _free_narrative(section.lines)
      val narrative = fields.get("narrative").orElse(fields.get("remarks")).orElse(free)
      val slug = _slugify(section.name)
      Element(
        kind = section.kind,
        name = section.name,
        termid = s"${glossarycategory}:${slug}",
        glossarypath = s"glossary/${glossarycategory}/${slug}.html",
        descriptive = Descriptive(
          label = section.name,
          brief = fields.get("brief"),
          summary = summary,
          description = description.orElse(summary)
        ),
        narrative = narrative.filter(_.trim.nonEmpty),
        relationships = _field_list(fields, "relationship", "relationships"),
        constraints = _field_list(fields, "constraint", "constraints"),
        implementation = _field_list(fields, "implementation"),
        rdfcandidates = _field_list(fields, "rdf", "rdf candidates", "rdfcandidates")
      )
    }
  }

  private def _sections(source: Path): Vector[Section] = {
    val lines = Files.readAllLines(source, StandardCharsets.UTF_8).asScala.toVector
    val targetkinds = Map(
      "entity" -> "entity",
      "value" -> "value",
      "powertype" -> "powertype",
      "statemachine" -> "statemachine",
      "state-machine" -> "statemachine",
      "state machine" -> "statemachine",
      "service" -> "service",
      "operation" -> "operation"
    )
    var currentkind = Option.empty[String]
    var current = Option.empty[(String, String, Vector[String])]
    var sections = Vector.empty[Section]
    def flush(): Unit =
      current.foreach { case (kind, name, body) =>
        sections :+= Section(kind, name, body)
      }
    lines.foreach { line =>
      val trimmed = line.trim
      if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
        flush()
        current = None
        val rawkind = trimmed.drop(2).trim.toLowerCase(java.util.Locale.ROOT)
        currentkind = targetkinds.get(rawkind)
      } else if (trimmed.startsWith("## ") && !trimmed.startsWith("### ")) {
        flush()
        current = currentkind.flatMap { kind =>
          val name = trimmed.drop(3).trim
          if (name.isEmpty) None else Some((kind, name, Vector.empty[String]))
        }
      } else {
        current = current.map { case (kind, name, body) => (kind, name, body :+ line) }
      }
    }
    flush()
    sections.distinct
  }

  private def _fields(lines: Vector[String]): Map[String, String] = {
    var current = Option.empty[(String, Vector[String])]
    var fields = Vector.empty[(String, String)]
    def flush(): Unit =
      current.foreach { case (name, body) =>
        val text = body.mkString("\n").trim
        if (text.nonEmpty)
          fields :+= name -> text
      }
    lines.foreach { line =>
      val trimmed = line.trim
      if (trimmed.startsWith("### ")) {
        flush()
        current = Some(trimmed.drop(4).trim.toLowerCase(java.util.Locale.ROOT) -> Vector.empty)
      } else {
        current = current.map { case (name, body) => name -> (body :+ line) }
      }
    }
    flush()
    fields.toMap
  }

  private def _free_narrative(lines: Vector[String]): Option[String] = {
    val body = lines.takeWhile(line => !line.trim.startsWith("### ")).mkString("\n").trim
    if (body.isEmpty) None else Some(body)
  }

  private def _field_list(fields: Map[String, String], names: String*): Vector[String] =
    names.toVector.flatMap(name => fields.get(name.toLowerCase(java.util.Locale.ROOT))).flatMap { value =>
      value.linesIterator.map(_.trim.stripPrefix("-").trim).filter(_.nonEmpty)
    }

  private def _slugify(value: String): String =
    value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").
      toLowerCase(java.util.Locale.ROOT).
      replaceAll("[^a-z0-9]+", "-").
      replaceAll("^-+", "").
      replaceAll("-+$", "")

  private def _write(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private def _yaml_list(values: Vector[String]): String =
    if (values.isEmpty)
      "[]"
    else
      values.map(_yaml_scalar).mkString("[", ", ", "]")

  private def _yaml_scalar(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
