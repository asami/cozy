package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import play.api.libs.json.{JsArray, JsObject, Json}
import org.smartdox.{Body, Dl, Document, Dox, Fragment, Section => DoxSection}
import org.smartdox.parser.Dox2Parser
import org.goldenport.kaleidox.{CmlSectionFormat, Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.kaleidox.model.OperationModel
import org.goldenport.kaleidox.model.DataTypeModel.DataTypeClass
import org.goldenport.record.v2.{CFormat, CMaxLength, CMinLength, CRegex, Constraint}
import org.goldenport.parser.LogicalSection

/*
 * @since   Jun. 23, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlModelMetadata {
  private val _schema = "cozy.cml.model-metadata.v1"

  final case class ModelMetadata(
    source: Source,
    surface: Surface,
    modelElements: Vector[Element]
  ) {
    def toJson: JsObject =
      Json.obj(
        "schema" -> _schema,
        "source" -> source.toJson,
        "surface" -> surface.toJson,
        "modelElements" -> JsArray(modelElements.map(_.toJson))
      )

    def toJsonString: String =
      Json.prettyPrint(toJson)

    def toYamlString: String = {
      val body = modelElements.map(_.toYaml("  ")).mkString
      s"""schema: ${_schema}
         |source:
         |  path: ${_yaml_scalar(source.path)}
         |  sha256: ${source.sha256}
         |  compiler: ${_yaml_scalar(source.compiler)}
         |  cozyVersion: ${_yaml_scalar(source.cozyversion)}
         |surface:
         |${surface.toYaml("  ")}
         |modelElements:
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

  final case class Surface(
    component: Option[ComponentSurface]
  ) {
    def toJson: JsObject =
      component match {
        case Some(x) => Json.obj("component" -> x.toJson)
        case None => Json.obj("component" -> Json.obj())
      }

    def toYaml(indent: String): String =
      component.map { x =>
        s"""${indent}component:
           |${x.toYaml(indent + "  ")}""".stripMargin
      }.getOrElse(s"${indent}component: {}\n")
  }

  final case class ComponentSurface(
    name: String,
    termid: String,
    glossarypath: String,
    descriptive: Descriptive,
    narrative: Option[String],
    services: Vector[ServiceSurface]
  ) {
    def toJson: JsObject =
      Json.obj(
        "name" -> name,
        "termId" -> termid,
        "glossaryPath" -> glossarypath,
        "descriptive" -> descriptive.toJson,
        "narrative" -> Json.toJson(narrative.getOrElse("")),
        "services" -> JsArray(services.map(_.toJson))
      )

    def toYaml(indent: String): String =
      s"""${indent}name: ${_yaml_scalar(name)}
         |${indent}termId: ${_yaml_scalar(termid)}
         |${indent}glossaryPath: ${_yaml_scalar(glossarypath)}
         |${indent}descriptive:
         |${indent}  label: ${_yaml_scalar(descriptive.label)}
         |${indent}  brief: ${_yaml_scalar(descriptive.brief.getOrElse(""))}
         |${indent}  summary: ${_yaml_scalar(descriptive.summary.getOrElse(""))}
         |${indent}  description: ${_yaml_scalar(descriptive.description.getOrElse(""))}
         |${indent}narrative: ${_yaml_scalar(narrative.getOrElse(""))}
         |${indent}services:
         |${services.map(_.toYaml(indent + "  ")).mkString}""".stripMargin
  }

  final case class ServiceSurface(
    name: String,
    termid: String,
    glossarypath: String,
    descriptive: Descriptive,
    narrative: Option[String],
    operations: Vector[OperationSurface]
  ) {
    def toJson: JsObject =
      Json.obj(
        "name" -> name,
        "termId" -> termid,
        "glossaryPath" -> glossarypath,
        "descriptive" -> descriptive.toJson,
        "narrative" -> Json.toJson(narrative.getOrElse("")),
        "operations" -> JsArray(operations.map(_.toJson))
      )

    def toYaml(indent: String): String =
      s"""${indent}- name: ${_yaml_scalar(name)}
         |${indent}  termId: ${_yaml_scalar(termid)}
         |${indent}  glossaryPath: ${_yaml_scalar(glossarypath)}
         |${indent}  descriptive:
         |${indent}    label: ${_yaml_scalar(descriptive.label)}
         |${indent}    brief: ${_yaml_scalar(descriptive.brief.getOrElse(""))}
         |${indent}    summary: ${_yaml_scalar(descriptive.summary.getOrElse(""))}
         |${indent}    description: ${_yaml_scalar(descriptive.description.getOrElse(""))}
         |${indent}  narrative: ${_yaml_scalar(narrative.getOrElse(""))}
         |${indent}  operations:
         |${operations.map(_.toYaml(indent + "    ")).mkString}""".stripMargin
  }

  final case class OperationSurface(
    name: String,
    termid: String,
    glossarypath: String,
    descriptive: Descriptive,
    narrative: Option[String],
    operationtype: Option[String],
    inputtype: Option[String],
    outputtype: Option[String],
    implementation: Vector[String]
  ) {
    def toJson: JsObject =
      Json.obj(
        "name" -> name,
        "termId" -> termid,
        "glossaryPath" -> glossarypath,
        "descriptive" -> descriptive.toJson,
        "narrative" -> Json.toJson(narrative.getOrElse("")),
        "operationType" -> Json.toJson(operationtype.getOrElse("")),
        "inputType" -> Json.toJson(inputtype.getOrElse("")),
        "outputType" -> Json.toJson(outputtype.getOrElse("")),
        "implementation" -> Json.toJson(implementation)
      )

    def toYaml(indent: String): String =
      s"""${indent}- name: ${_yaml_scalar(name)}
         |${indent}  termId: ${_yaml_scalar(termid)}
         |${indent}  glossaryPath: ${_yaml_scalar(glossarypath)}
         |${indent}  descriptive:
         |${indent}    label: ${_yaml_scalar(descriptive.label)}
         |${indent}    brief: ${_yaml_scalar(descriptive.brief.getOrElse(""))}
         |${indent}    summary: ${_yaml_scalar(descriptive.summary.getOrElse(""))}
         |${indent}    description: ${_yaml_scalar(descriptive.description.getOrElse(""))}
         |${indent}  narrative: ${_yaml_scalar(narrative.getOrElse(""))}
         |${indent}  operationType: ${_yaml_scalar(operationtype.getOrElse(""))}
         |${indent}  inputType: ${_yaml_scalar(inputtype.getOrElse(""))}
         |${indent}  outputType: ${_yaml_scalar(outputtype.getOrElse(""))}
         |${indent}  implementation: ${_yaml_list(implementation)}
         |""".stripMargin
  }

  final case class Element(
    kind: String,
    name: String,
    termid: String,
    glossarypath: String,
    descriptive: Descriptive,
    narrative: Option[String],
    inputkind: Option[String] = None,
    relationships: Vector[String] = Vector.empty,
    constraints: Vector[String] = Vector.empty,
    implementation: Vector[String] = Vector.empty,
    rdfcandidates: Vector[String] = Vector.empty,
    fields: Vector[Field] = Vector.empty,
    representation: Option[String] = None,
    underlyingtype: Option[String] = None
  ) {
    def toJson: JsObject =
      Json.obj(
        "kind" -> kind,
        "name" -> name,
        "termId" -> termid,
        "glossaryPath" -> glossarypath,
        "descriptive" -> descriptive.toJson,
        "narrative" -> Json.toJson(narrative.getOrElse("")),
        "inputKind" -> Json.toJson(inputkind.getOrElse("")),
        "relationships" -> Json.toJson(relationships),
        "constraints" -> Json.toJson(constraints),
        "implementation" -> Json.toJson(implementation),
        "rdfCandidates" -> Json.toJson(rdfcandidates),
        "fields" -> JsArray(fields.map(_.toJson)),
        "representation" -> Json.toJson(representation.getOrElse("")),
        "underlyingType" -> Json.toJson(underlyingtype.getOrElse(""))
      )

    def toYaml(indent: String): String = {
      val fieldsyaml =
        if (fields.isEmpty) "[]"
        else "\n" + fields.map(_.toYaml(indent + "    ")).mkString
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
         |${indent}  inputKind: ${_yaml_scalar(inputkind.getOrElse(""))}
         |${indent}  relationships: ${_yaml_list(relationships)}
         |${indent}  constraints: ${_yaml_list(constraints)}
         |${indent}  implementation: ${_yaml_list(implementation)}
         |${indent}  rdfCandidates: ${_yaml_list(rdfcandidates)}
         |${indent}  representation: ${_yaml_scalar(representation.getOrElse(""))}
         |${indent}  underlyingType: ${_yaml_scalar(underlyingtype.getOrElse(""))}
         |${indent}  fields: ${fieldsyaml}
         |""".stripMargin
    }
  }

  final case class Field(
    name: String,
    typename: String,
    multiplicity: String,
    constraints: Vector[String] = Vector.empty
  ) {
    def required: Boolean =
      !Set("?", "*", "0..1", "0..*").contains(multiplicity.trim)

    def toJson: JsObject =
      Json.obj(
        "name" -> name,
        "type" -> typename,
        "multiplicity" -> multiplicity,
        "required" -> required,
        "constraints" -> Json.toJson(constraints)
      )

    def toYaml(indent: String): String =
      s"""${indent}- name: ${_yaml_scalar(name)}
         |${indent}  type: ${_yaml_scalar(typename)}
         |${indent}  multiplicity: ${_yaml_scalar(multiplicity)}
         |${indent}  required: ${required}
         |${indent}  constraints: ${_yaml_list(constraints)}
         |""".stripMargin
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
    val model = KaleidoxModel.load(KaleidoxConfig.default, normalized.toFile)
    ModelMetadata(
      Source(
        path = sourcepath,
        sha256 = sha256(normalized),
        compiler = "cozy-modeler",
        cozyversion = org.simplemodeling.cozy.BuildInfo.version
      ),
      surface = _surface(normalized, glossarycategory),
      modelElements = _with_ast_contracts(_model_elements(normalized, glossarycategory), model)
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
      surface = Surface(None),
      modelElements = _with_ast_contracts(_model_elements(model, glossarycategory), model)
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

  private def _with_ast_contracts(
    elements: Vector[Element],
    model: KaleidoxModel
  ): Vector[Element] =
    _with_ast_datatype_contracts(_with_ast_operation_value_contracts(elements, model), model)

  private def _with_ast_operation_value_contracts(
    elements: Vector[Element],
    model: KaleidoxModel
  ): Vector[Element] = {
    val legacy = model.takeOperationModel.values.map { value =>
      (_legacy_element_kind(value.kind) -> value.name) -> _input_kind_name(value.kind)
    }.toMap
    val canonical = model.getValueModel.toVector.flatMap(_.classes.values).flatMap { value =>
      value.getProperty("input-kind").flatMap(OperationModel.InputValueKind.parse).map { kind =>
        ("value" -> value.name) -> _input_kind_name(kind)
      }
    }.toMap
    val inputkinds = legacy ++ canonical
    elements.map { element =>
      inputkinds.get(element.kind.trim.toLowerCase(java.util.Locale.ROOT) -> element.name).
        map(kind => element.copy(kind = "value", inputkind = Some(kind))).
        getOrElse(element)
    }
  }

  private def _legacy_element_kind(p: OperationModel.InputValueKind): String = p match {
    case OperationModel.InputValueKind.CommandValue => "command"
    case OperationModel.InputValueKind.QueryValue => "query"
  }

  private def _input_kind_name(p: OperationModel.InputValueKind): String = p match {
    case OperationModel.InputValueKind.CommandValue => "command"
    case OperationModel.InputValueKind.QueryValue => "query"
  }

  private def _with_ast_datatype_contracts(
    elements: Vector[Element],
    model: KaleidoxModel
  ): Vector[Element] = {
    val datatypes = model.takeDataTypeModel.classes
    elements.map { element =>
      if (element.kind == "datatype")
        datatypes.get(element.name).fold(element)(_with_ast_datatype_contract(element, _))
      else
        element
    }
  }

  private def _with_ast_datatype_contract(
    element: Element,
    datatype: DataTypeClass
  ): Element =
    datatype match {
      case m: DataTypeClass.Plain =>
        val constraints = m.constraints.map(_constraint_text).toVector
        element.copy(
          constraints = constraints,
          fields = Vector(Field("value", m.datatype.name, "1", constraints)),
          representation = Some("nominal-scalar"),
          underlyingtype = Some(m.datatype.name)
        )
      case _: DataTypeClass.Complex =>
        element.copy(representation = Some("structured"))
    }

  private def _constraint_text(p: Constraint): String = p match {
    case m: CMinLength => s"min-length=${m.length}"
    case m: CMaxLength => s"max-length=${m.length}"
    case m: CRegex => s"pattern=${m.regex.regex}"
    case m: CFormat => s"format=${m.format}"
    case m => m.label
  }

  private def _model_elements(model: KaleidoxModel, glossarycategory: String): Vector[Element] =
    model.divisions.toVector.flatMap {
      case d: KaleidoxModel.EntityDivision => _logical_section_elements("entity", d.section, glossarycategory)
      case d: KaleidoxModel.ValueDivision => _logical_section_elements("value", d.section, glossarycategory)
      case d: KaleidoxModel.PowertypeDivision => _logical_section_elements("powertype", d.section, glossarycategory)
      case d: KaleidoxModel.StateMachineDivision => _logical_section_elements("statemachine", d.section, glossarycategory)
      case _ => Vector.empty
    } ++ model.takeDataTypeModel.classes.values.toVector.map(_ast_datatype_element(_, glossarycategory))

  private def _ast_datatype_element(
    datatype: DataTypeClass,
    glossarycategory: String
  ): Element = {
    val slug = _slugify(datatype.name)
    val base = Element(
      kind = "datatype",
      name = datatype.name,
      termid = s"${glossarycategory}:${slug}",
      glossarypath = s"glossary/${glossarycategory}/${slug}.html",
      descriptive = Descriptive(datatype.name, None, None, None),
      narrative = None
    )
    _with_ast_datatype_contract(base, datatype)
  }

  private def _component_surface(section: LogicalSection, glossarycategory: String, services: Vector[ServiceSurface]): ComponentSurface = {
    val base = _logical_element("component", section, glossarycategory)
    ComponentSurface(
      base.name,
      base.termid,
      base.glossarypath,
      base.descriptive,
      base.narrative,
      services
    )
  }

  private def _service_surface(section: LogicalSection, glossarycategory: String): ServiceSurface = {
    val base = _logical_element("service", section, glossarycategory)
    val operations = _child_sections(section, "OPERATION").flatMap(_.blocks.sections.toVector).map(_operation_surface(_, glossarycategory))
    ServiceSurface(
      base.name,
      base.termid,
      base.glossarypath,
      base.descriptive,
      base.narrative,
      operations
    )
  }

  private def _operation_surface(section: LogicalSection, glossarycategory: String): OperationSurface = {
    val base = _logical_element("operation", section, glossarycategory)
    OperationSurface(
      base.name,
      base.termid,
      base.glossarypath,
      base.descriptive,
      base.narrative,
      _child_text(section, "TYPE"),
      _child_property(section, "INPUT", "type").orElse(_child_child_text(section, "INPUT", "TYPE")).orElse(_operation_direct_property(section, "input")),
      _child_property(section, "OUTPUT", "type").orElse(_child_child_text(section, "OUTPUT", "TYPE")).orElse(_operation_direct_property(section, "output")).orElse(_operation_direct_property(section, "result")),
      _child_texts(section, "IMPLEMENTATION")
    )
  }

  private def _logical_section_elements(kind: String, root: LogicalSection, glossarycategory: String): Vector[Element] =
    root.blocks.sections.toVector.filterNot(s => _narrative_keys.contains(s.keyForModel.toLowerCase(java.util.Locale.ROOT))).map(_logical_element(kind, _, glossarycategory)).distinct

  private def _logical_element(kind: String, section: LogicalSection, glossarycategory: String): Element = {
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
  }

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

  private def _same_section_key(section: LogicalSection, name: String): Boolean =
    section.keyForModel.equalsIgnoreCase(name) || section.nameForModel.equalsIgnoreCase(name)

  private def _child_sections(section: LogicalSection, childname: String): Vector[LogicalSection] =
    section.blocks.sections.toVector.filter(child => _same_section_key(child, childname))

  private def _child_text(section: LogicalSection, childname: String): Option[String] =
    _child_sections(section, childname).headOption.flatMap { child =>
      child.blocks.text.linesIterator.map(_.trim).find(_.nonEmpty)
    }

  private def _child_texts(section: LogicalSection, childname: String): Vector[String] =
    _child_sections(section, childname).flatMap { child =>
      child.blocks.text.linesIterator.map(_.trim).filter(_.nonEmpty).toVector
    }

  private def _child_property(section: LogicalSection, childname: String, propertyname: String): Option[String] =
    _child_sections(section, childname).headOption.flatMap(child => _property_value(child.blocks.text, propertyname))

  private def _child_child_text(section: LogicalSection, childname: String, grandchildname: String): Option[String] =
    _child_sections(section, childname).headOption.flatMap(_child_text(_, grandchildname))

  private def _operation_direct_property(section: LogicalSection, propertyname: String): Option[String] =
    _property_value(section.blocks.text, propertyname).orElse(_property_value(section.blocks.lines.text, propertyname))

  private def _property_value(text: String, propertyname: String): Option[String] = {
    val prefix = s"${propertyname.toLowerCase} ::"
    text.linesIterator.map(_.trim).flatMap {
      case line if line.startsWith("-") =>
        val s = line.drop(1).trim
        if (s.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
          Some(s.drop(prefix.length).trim)
        else
          None
      case line if line.toLowerCase(java.util.Locale.ROOT).startsWith(prefix) =>
        Some(line.drop(prefix.length).trim)
      case _ =>
        None
    }.find(_.nonEmpty)
  }

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

  private def _surface(source: Path, glossarycategory: String): Surface = {
    val lines = Files.readAllLines(source, StandardCharsets.UTF_8).asScala.toVector
    val component = _raw_named_blocks(lines, 1, "COMPONENT", 2).headOption.map { block =>
      val base = _raw_element("component", block.name, block.lines, glossarycategory)
      ComponentSurface(
        base.name,
        base.termid,
        base.glossarypath,
        base.descriptive,
        base.narrative,
        _raw_services(lines, glossarycategory)
      )
    }
    Surface(component)
  }

  private def _raw_services(lines: Vector[String], glossarycategory: String): Vector[ServiceSurface] =
    _raw_named_blocks(lines, 1, "SERVICE", 2).map { block =>
      val base = _raw_element("service", block.name, block.lines, glossarycategory)
      ServiceSurface(
        base.name,
        base.termid,
        base.glossarypath,
        base.descriptive,
        base.narrative,
        _raw_operations(block.lines, glossarycategory)
      )
    }

  private def _raw_operations(lines: Vector[String], glossarycategory: String): Vector[OperationSurface] =
    _raw_named_blocks(lines, 3, "OPERATION", 4).map { block =>
      val base = _raw_element("operation", block.name, block.lines, glossarycategory, 5)
      OperationSurface(
        base.name,
        base.termid,
        base.glossarypath,
        base.descriptive,
        base.narrative,
        _raw_child_text(block.lines, 5, "TYPE"),
        _raw_child_property(block.lines, 5, "INPUT", "type").orElse(_raw_child_child_text(block.lines, 5, "INPUT", "TYPE")).orElse(_raw_direct_property(block.lines, "input")),
        _raw_child_property(block.lines, 5, "OUTPUT", "type").orElse(_raw_child_child_text(block.lines, 5, "OUTPUT", "TYPE")).orElse(_raw_direct_property(block.lines, "output")).orElse(_raw_direct_property(block.lines, "result")),
        _raw_child_texts(block.lines, 5, "IMPLEMENTATION")
      )
    }

  private case class RawBlock(name: String, lines: Vector[String])

  private def _raw_named_blocks(lines: Vector[String], sectionlevel: Int, sectionname: String, itemlevel: Int): Vector[RawBlock] = {
    val sectionprefix = "#" * sectionlevel + " "
    val itemprefix = "#" * itemlevel + " "
    var insection = false
    var current = Option.empty[(String, Vector[String])]
    var blocks = Vector.empty[RawBlock]
    def _flush_(): Unit =
      current.foreach { case (name, body) => blocks :+= RawBlock(name, body) }
    lines.foreach { line =>
      val trimmed = line.trim
      if (trimmed.startsWith(sectionprefix) && !trimmed.startsWith(sectionprefix + "#")) {
        if (insection)
          _flush_()
        insection = trimmed.drop(sectionprefix.length).trim.equalsIgnoreCase(sectionname)
        current = None
      } else if (insection && trimmed.startsWith(itemprefix) && !trimmed.startsWith(itemprefix + "#")) {
        _flush_()
        val name = trimmed.drop(itemprefix.length).trim
        current = if (name.isEmpty) None else Some(name -> Vector.empty)
      } else if (insection && _heading_level(trimmed).exists(_ <= sectionlevel)) {
        _flush_()
        current = None
        insection = false
      } else if (insection) {
        current = current.map { case (name, body) => name -> (body :+ line) }
      }
    }
    if (insection)
      _flush_()
    blocks.distinct
  }

  private def _heading_level(trimmed: String): Option[Int] = {
    val count = trimmed.takeWhile(_ == '#').length
    if (count > 0 && trimmed.drop(count).startsWith(" ")) Some(count) else None
  }

  private def _raw_element(kind: String, name: String, lines: Vector[String], glossarycategory: String, fieldlevel: Int = 3): Element = {
    val fields = _fields(lines, fieldlevel)
    val summary = fields.get("summary").orElse(fields.get("brief"))
    val description = fields.get("description")
    val narrative = fields.get("narrative").orElse(fields.get("remarks")).orElse(_free_narrative(lines, fieldlevel))
    val slug = _slugify(name)
    Element(
      kind = kind,
      name = name,
      termid = s"${glossarycategory}:${slug}",
      glossarypath = s"glossary/${glossarycategory}/${slug}.html",
      descriptive = Descriptive(
        label = name,
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

  private def _raw_child_text(lines: Vector[String], level: Int, childname: String): Option[String] =
    _raw_child_block(lines, level, childname).flatMap(_.lines.map(_.trim).find(_.nonEmpty))

  private def _raw_child_texts(lines: Vector[String], level: Int, childname: String): Vector[String] =
    _raw_child_block(lines, level, childname).map(_.lines.map(_.trim).filter(_.nonEmpty)).getOrElse(Vector.empty)

  private def _raw_child_property(lines: Vector[String], level: Int, childname: String, propertyname: String): Option[String] =
    _raw_child_block(lines, level, childname).flatMap(block => _property_value(block.lines.mkString("\n"), propertyname))

  private def _raw_child_child_text(lines: Vector[String], level: Int, childname: String, grandchildname: String): Option[String] =
    _raw_child_block(lines, level, childname).flatMap(block => _raw_child_text(block.lines, level + 1, grandchildname))

  private def _raw_direct_property(lines: Vector[String], propertyname: String): Option[String] =
    _property_value(lines.mkString("\n"), propertyname)

  private def _raw_child_block(lines: Vector[String], level: Int, childname: String): Option[RawBlock] =
    _raw_section_block(lines, level, childname)

  private def _raw_section_block(lines: Vector[String], level: Int, childname: String): Option[RawBlock] = {
    val prefix = "#" * level + " "
    val index = lines.indexWhere(line => line.trim.equalsIgnoreCase(prefix + childname))
    if (index < 0)
      None
    else {
      val body = lines.drop(index + 1).takeWhile(line => _heading_level(line.trim).forall(_ > level))
      Some(RawBlock(childname, body))
    }
  }

  private def _model_elements(source: Path, glossarycategory: String): Vector[Element] = {
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
        rdfcandidates = _field_list(fields, "rdf", "rdf candidates", "rdfcandidates"),
        fields = _attribute_fields(section.lines)
      )
    }
  }

  private def _sections(source: Path): Vector[Section] = {
    val lines = Files.readAllLines(source, StandardCharsets.UTF_8).asScala.toVector
    val targetkinds = Map(
      "entity" -> "entity",
      "value" -> "value",
      "datatype" -> "datatype",
      "command" -> "command",
      "query" -> "query",
      "powertype" -> "powertype",
      "statemachine" -> "statemachine",
      "state-machine" -> "statemachine",
      "state machine" -> "statemachine"
    )
    var currentkind = Option.empty[String]
    var current = Option.empty[(String, String, Vector[String])]
    var sections = Vector.empty[Section]
    def _flush_(): Unit =
      current.foreach { case (kind, name, body) =>
        sections :+= Section(kind, name, body)
      }
    lines.foreach { line =>
      val trimmed = line.trim
      if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
        _flush_()
        current = None
        val rawkind = trimmed.drop(2).trim.toLowerCase(java.util.Locale.ROOT)
        currentkind = targetkinds.get(rawkind)
      } else if (trimmed.startsWith("## ") && !trimmed.startsWith("### ")) {
        _flush_()
        current = currentkind.flatMap { kind =>
          val name = trimmed.drop(3).trim
          if (name.isEmpty) None else Some((kind, name, Vector.empty[String]))
        }
      } else {
        current = current.map { case (kind, name, body) => (kind, name, body :+ line) }
      }
    }
    _flush_()
    sections.distinct
  }

  private def _fields(lines: Vector[String], level: Int = 3): Map[String, String] = {
    val prefix = "#" * level + " "
    val childprefix = prefix + "#"
    var current = Option.empty[(String, Vector[String])]
    var fields = Vector.empty[(String, String)]
    def _flush_(): Unit =
      current.foreach { case (name, body) =>
        val text = body.mkString("\n").trim
        if (text.nonEmpty)
          fields :+= name -> text
      }
    lines.foreach { line =>
      val trimmed = line.trim
      if (trimmed.startsWith(prefix) && !trimmed.startsWith(childprefix)) {
        _flush_()
        current = Some(trimmed.drop(prefix.length).trim.toLowerCase(java.util.Locale.ROOT) -> Vector.empty)
      } else {
        current = current.map { case (name, body) => name -> (body :+ line) }
      }
    }
    _flush_()
    fields.toMap
  }

  private def _free_narrative(lines: Vector[String], level: Int = 3): Option[String] = {
    val prefix = "#" * level + " "
    val body = lines.takeWhile(line => !line.trim.startsWith(prefix)).mkString("\n").trim
    val narrative = _ast_narrative(body)
    if (narrative.isEmpty) None else Some(narrative)
  }

  private def _ast_narrative(p: String): String = {
    val dox = Dox2Parser.parse(KaleidoxConfig.default.doxConfig, p)
    _top_level_contents(dox).filterNot(_is_property_node).map(_.toText.trim).filter(_.nonEmpty).mkString("\n").trim
  }

  private def _is_property_node(p: Dox): Boolean =
    p.isInstanceOf[Dl] || CmlSectionFormat.directKeyValues(DoxSection(Nil, List(p))).nonEmpty

  private def _top_level_contents(p: Dox): Vector[Dox] = p match {
    case m: Document => m.body.contents.toVector
    case m: Body => m.contents.toVector
    case m: Fragment => m.contents.toVector
    case m: DoxSection => m.contents.toVector
    case m => Vector(m)
  }

  private def _field_list(fields: Map[String, String], names: String*): Vector[String] =
    names.toVector.flatMap(name => fields.get(name.toLowerCase(java.util.Locale.ROOT))).flatMap { value =>
      value.linesIterator.map(_.trim.stripPrefix("-").trim).filter(_.nonEmpty)
    }

  private def _attribute_fields(lines: Vector[String]): Vector[Field] =
    _raw_section_block(lines, 3, "ATTRIBUTE").toVector.flatMap { block =>
      val rows = block.lines.map(_.trim).filter(line => line.startsWith("|") && line.endsWith("|"))
      rows.headOption.toVector.flatMap { headerline =>
        val headers = _table_cells(headerline).map(_.toLowerCase(java.util.Locale.ROOT))
        val nameindex = headers.indexOf("name")
        val typeindex = headers.indexOf("type")
        val multiplicityindex = headers.indexOf("multiplicity")
        if (nameindex < 0 || typeindex < 0 || multiplicityindex < 0)
          Vector.empty
        else
          rows.drop(1).filterNot(_table_separator).flatMap { row =>
            val cells = _table_cells(row)
            if (cells.length <= Seq(nameindex, typeindex, multiplicityindex).max)
              None
            else {
              val name = cells(nameindex)
              val typename = cells(typeindex)
              val multiplicity = cells(multiplicityindex)
              if (name.nonEmpty && typename.nonEmpty && multiplicity.nonEmpty)
                Some(Field(name, typename, multiplicity))
              else
                None
            }
          }
      }
    }

  private def _table_cells(line: String): Vector[String] =
    line.stripPrefix("|").stripSuffix("|").split("\\|", -1).toVector.map(_.trim)

  private def _table_separator(line: String): Boolean =
    _table_cells(line).forall(cell => cell.nonEmpty && cell.forall(ch => ch == '-' || ch == '+' || ch == ':'))

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
