package cozy.modeler

import org.simplemodeling.model._
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

/*
 * @since   Oct.  5, 2026
 * @version Oct.  6, 2026
 */
private[modeler] object DartModelRenderer {
  def render(projection: DartModelProjection): String = {
    val declarations = projection.objects.map {
      case source: MPowertype => _classification(projection, source)
      case source => _model(projection, source)
    }
    val imports = if (declarations.isEmpty) "" else "import 'json.dart' as _dartJson;\n\n"
    "// @dart=3.0\n// Generated from canonical model contracts.\n" +
      imports + declarations.mkString("\n\n") + "\n"
  }

  private def _model(projection: DartModelProjection, source: MObject): String = {
    val typename = projection.typeName(source)
    val fields = source.attributes
    val declarations = fields.map { field =>
      val elementtype = projection.elementType(source, field)
      val fieldtype = field.multiplicity match {
        case MZeroOne => elementtype + "?"
        case MZeroMore | MOneMore => s"List<$elementtype>"
        case _ => elementtype
      }
      s"  ${_field_metadata(projection, source, field)}\n  final $fieldtype ${projection.fieldName(source, field)};"
    }.mkString("\n")
    val parameters = fields.map { field =>
      val name = projection.fieldName(source, field)
      val elementtype = projection.elementType(source, field)
      field.multiplicity match {
        case MOne => s"required $elementtype $name"
        case MZeroOne => s"$elementtype? $name"
        case MZeroMore => s"List<$elementtype> $name = const []"
        case MOneMore => s"required List<$elementtype> $name"
      }
    }.mkString(", ")
    val initializers = fields.map { field =>
      val name = projection.fieldName(source, field)
      val expression = _construct(projection, source, field, name)
      s"$name = $expression"
    }.mkString(",\n      ")
    val constructor = if (fields.isEmpty) s"  $typename();" else
      s"  $typename({$parameters})\n      : $initializers;"
    val scalar = projection.isScalar(source)
    val encode = if (scalar) _encode(projection, source, fields.head, projection.fieldName(source, fields.head)) else {
      fields.map { field =>
        s"    ${DartNaming.literal(projection.wireName(source, field))}: ${_encode(projection, source, field, "this." + projection.fieldName(source, field))},"
      }.mkString("<String, Object?>{\n", "\n", "\n  }")
    }
    val omission = if (scalar) "" else {
      val entries = fields.map { field =>
        val expression = "this." + projection.fieldName(source, field)
        val condition = if (field.multiplicity == MZeroOne) s"if ($expression != null) " else ""
        s"    $condition${DartNaming.literal(projection.wireName(source, field))}: ${_encode(projection, source, field, expression, omitnullfields = true)},"
      }.mkString("<String, Object?>{\n", "\n", "\n  }")
      s"  Map<String, Object?> toJsonOmittingNullFields() => $entries;\n\n"
    }
    val decode = if (scalar) {
      s"    return $typename(value: ${_decode_element(projection, source, fields.head, "_value")});"
    } else {
      val arguments = fields.map { field =>
        s"      ${projection.fieldName(source, field)}: ${_decode_field(projection, source, field)},"
      }.mkString("\n")
      s"    final _json = _dartJson.DartJsonCodec.object(_value, ${DartNaming.literal(source.qualifiedName)});\n" +
        (if (fields.isEmpty) s"    return $typename();" else s"    return $typename(\n$arguments\n    );")
    }
    // Empty objects still validate their input; avoid an unused local in Dart.
    val decoded = if (fields.isEmpty) decode.replace("final _json = ", "") else decode
    s"${_type_metadata(source)}\nclass $typename {\n$declarations\n\n$constructor\n\n" +
      s"  Object? toJson() => $encode;\n\n" + omission +
      s"  static $typename fromJson(Object? _value) {\n$decoded\n  }\n}"
  }

  private def _construct(projection: DartModelProjection, source: MObject, field: MAttribute, expression: String): String = {
    val location = DartNaming.literal(projection.location(source, field))
    field.multiplicity match {
      case MZeroOne => s"$expression == null ? null : ${_check_element(projection, source, field, expression, false)}"
      case MZeroMore | MOneMore =>
        val elementtype = projection.elementType(source, field)
        val nonempty = field.multiplicity == MOneMore
        s"_dartJson.DartJsonCodec.immutableList<$elementtype>($expression, $location, " +
          s"(_element) => ${_check_element(projection, source, field, "_element", false)}, nonempty: $nonempty)"
      case _ => _check_element(projection, source, field, expression, false)
    }
  }

  private def _check_element(projection: DartModelProjection, source: MObject, field: MAttribute, expression: String, decoding: Boolean): String = {
    val location = DartNaming.literal(projection.location(source, field))
    field.attributeType match {
      case m: MDataType =>
        projection.elementType(source, field) match {
          case "String" => s"_dartJson.DartJsonCodec.string($expression, $location, decoding: $decoding)"
          case "bool" => s"_dartJson.DartJsonCodec.boolean($expression, $location, decoding: $decoding)"
          case "int" =>
            val (minimum, maximum) = DartModelProjection.integerBounds(m.datatype)
            s"_dartJson.DartJsonCodec.integer($expression, $location, $minimum, $maximum, decoding: $decoding)"
          case "double" => s"_dartJson.DartJsonCodec.floating($expression, $location, decoding: $decoding)"
        }
      case _: MObjectAttributeType =>
        val typename = projection.elementType(source, field)
        if (decoding) s"$typename.fromJson($expression)" else
          s"_dartJson.DartJsonCodec.reference<$typename>($expression, $location)"
    }
  }

  private def _decode_element(projection: DartModelProjection, source: MObject, field: MAttribute, expression: String): String =
    _check_element(projection, source, field, expression, true)

  private def _decode_field(projection: DartModelProjection, source: MObject, field: MAttribute): String = {
    val key = DartNaming.literal(projection.wireName(source, field))
    val location = DartNaming.literal(projection.location(source, field))
    val value = s"_json[$key]"
    val required = s"_dartJson.DartJsonCodec.requiredField(_json, $key, $location)"
    val elementtype = projection.elementType(source, field)
    val callback = s"(_element) => ${_decode_element(projection, source, field, "_element")}"
    field.multiplicity match {
      case MOne => _decode_element(projection, source, field, required)
      case MZeroOne => s"$value == null ? null : ${_decode_element(projection, source, field, value)}"
      case MZeroMore => s"_dartJson.DartJsonCodec.manyField<$elementtype>(_json, $key, $location, $callback)"
      case MOneMore => s"_dartJson.DartJsonCodec.readList<$elementtype>($required, $location, $callback, nonempty: true)"
    }
  }

  private def _encode_element(projection: DartModelProjection, source: MObject, field: MAttribute, expression: String, omitnullfields: Boolean = false): String = {
    val checked = _check_element(projection, source, field, expression, false)
    field.attributeType match {
      case reference: MObjectAttributeType =>
        val target = projection.reference(reference.ref)
        val method = if (omitnullfields && !projection.isScalar(target) && !target.isInstanceOf[MPowertype])
          "toJsonOmittingNullFields" else "toJson"
        s"$checked.$method()"
      case _ => checked
    }
  }

  private def _encode(projection: DartModelProjection, source: MObject, field: MAttribute, expression: String, omitnullfields: Boolean = false): String = {
    field.multiplicity match {
      case MZeroOne => s"$expression == null ? null : ${_encode_element(projection, source, field, expression, omitnullfields)}"
      case MZeroMore | MOneMore =>
        val location = DartNaming.literal(projection.location(source, field))
        val elementtype = projection.elementType(source, field)
        val nonempty = field.multiplicity == MOneMore
        s"_dartJson.DartJsonCodec.encodeList<$elementtype>($expression, $location, " +
          s"(_element) => ${_encode_element(projection, source, field, "_element", omitnullfields)}, nonempty: $nonempty)"
      case _ => _encode_element(projection, source, field, expression, omitnullfields)
    }
  }

  private def _classification(projection: DartModelProjection, source: MPowertype): String = {
    if (source.isKnowledge) _open(projection, source)
    else if (projection.isSealed(source)) _sealed(projection, source)
    else _enum(projection, source)
  }

  private def _enum(projection: DartModelProjection, source: MPowertype): String = {
    val typename = projection.typeName(source)
    val cases = source.kinds.map { kind =>
      val member = DartNaming.memberName(kind.name, source.qualifiedName)
      s"  ${_kind_metadata(source, kind)}\n  $member(${DartNaming.literal(kind.name)}, ${_optional_literal(kind.value)}, ${DartNaming.literal(kind.label)})"
    }.mkString(",\n")
    s"${_type_metadata(source)}\nenum $typename {\n$cases;\n\n" +
      "  final String wireName;\n  final String? sourceValue;\n  final String label;\n" +
      s"  const $typename(this.wireName, this.sourceValue, this.label);\n" +
      "  String toJson() => wireName;\n\n" + _finite_decode(source, typename, kind => s"$typename.${DartNaming.memberName(kind.name, source.qualifiedName)}") + "\n}"
  }

  private def _sealed(projection: DartModelProjection, source: MPowertype): String = {
    val typename = projection.typeName(source)
    def _alternative_(kind: MPowertypeKind): String = typename + DartNaming.typeName(kind.name, source.qualifiedName)
    val base = s"${_type_metadata(source)}\nsealed class $typename {\n  const $typename._();\n" +
      "  String get wireName;\n  String? get sourceValue;\n  String get label;\n  String toJson() => wireName;\n\n" +
      _finite_decode(source, typename, kind => s"const ${_alternative_(kind)}()") + "\n}"
    val alternatives = source.kinds.map { kind =>
      val alternative = _alternative_(kind)
      s"${_kind_metadata(source, kind)}\nfinal class $alternative extends $typename {\n" +
        s"  const $alternative() : super._();\n" +
        s"  @override\n  String get wireName => ${DartNaming.literal(kind.name)};\n" +
        s"  @override\n  String? get sourceValue => ${_optional_literal(kind.value)};\n" +
        s"  @override\n  String get label => ${DartNaming.literal(kind.label)};\n}"
    }
    (base +: alternatives).mkString("\n\n")
  }

  private def _finite_decode(source: MPowertype, typename: String, result: MPowertypeKind => String): String = {
    val location = DartNaming.literal(source.qualifiedName)
    val cases = source.kinds.map(kind => s"      case ${DartNaming.literal(kind.name)}: return ${result(kind)};").mkString("\n")
    s"  static $typename fromJson(Object? _value) {\n" +
      s"    final _name = _dartJson.DartJsonCodec.string(_value, $location, decoding: true);\n" +
      s"    switch (_name) {\n$cases\n      default: return _dartJson.DartJsonCodec.unknownKind($location, _name);\n    }\n  }"
  }

  private def _open(projection: DartModelProjection, source: MPowertype): String = {
    val typename = projection.typeName(source)
    val location = DartNaming.literal(source.qualifiedName)
    val known = source.kinds.map { kind =>
      s"  ${_kind_metadata(source, kind)}\n  static final ${DartNaming.memberName(kind.name, source.qualifiedName)} = $typename(wireName: ${DartNaming.literal(kind.name)});"
    }.mkString("\n")
    def _getter_(name: String, value: MPowertypeKind => Option[String]): String = {
      val cases = source.kinds.map(kind => s"      case ${DartNaming.literal(kind.name)}: return ${_optional_literal(value(kind))};").mkString("\n")
      s"  String? get $name {\n    switch (wireName) {\n$cases\n      default: return null;\n    }\n  }"
    }
    s"${_type_metadata(source)}\nclass $typename {\n  final String wireName;\n" +
      s"  $typename({required String wireName}) : wireName = _dartJson.DartJsonCodec.string(wireName, $location);\n$known\n\n" +
      _getter_("sourceValue", _.value) + "\n" + _getter_("label", kind => Some(kind.label)) +
      s"\n  String toJson() => _dartJson.DartJsonCodec.string(wireName, $location);\n" +
      s"  static $typename fromJson(Object? _value) => $typename(wireName: _dartJson.DartJsonCodec.string(_value, $location, decoding: true));\n}"
  }

  private def _optional_literal(value: Option[String]): String = value.map(DartNaming.literal).getOrElse("null")

  private def _constraints(constraints: Seq[MConstraint]): JsArray = JsArray(constraints.map { constraint =>
    Json.obj("name" -> constraint.name, "value" -> String.valueOf(constraint.value))
  })

  private def _comment(value: JsObject): String = "// source " + Json.stringify(value).
    replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

  private def _type_metadata(source: MObject): String = {
    val constraints = source match {
      case nominal: MNominalDataType => nominal.constraints
      case _ => Nil
    }
    _comment(Json.obj("identity" -> source.qualifiedName, "constraints" -> _constraints(constraints)))
  }

  private def _field_metadata(projection: DartModelProjection, source: MObject, field: MAttribute): String = {
    val identity = field.attributeType match {
      case reference: MObjectAttributeType => DartModelProjection.referenceName(reference.ref)
      case datatype: MDataType => datatype.datatype.name
    }
    _comment(Json.obj("identity" -> projection.location(source, field), "wireName" -> projection.wireName(source, field),
      "type" -> identity, "multiplicity" -> field.multiplicity.mark,
      "constraints" -> _constraints(field.constraints), "typeConstraints" -> _constraints(field.typeConstraints)))
  }

  private def _kind_metadata(source: MPowertype, kind: MPowertypeKind): String =
    _comment(Json.obj("identity" -> s"${source.qualifiedName}.${kind.name}", "wireName" -> kind.name,
      "sourceValue" -> kind.value.map[play.api.libs.json.JsValue](JsString).getOrElse[play.api.libs.json.JsValue](play.api.libs.json.JsNull), "label" -> kind.label))
}
