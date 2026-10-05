package cozy.modeler

import scala.collection.mutable
import org.goldenport.RAISE
import org.goldenport.kaleidox.model.OperationModel
import org.simplemodeling.model.MPowertype
import play.api.libs.json.Json

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
private[modeler] object DartApiRenderer {
  def render(operations: Vector[DartOperationProjection.Operation], models: DartModelProjection): String = {
    _require_symbols(operations, models)
    val imports = if (operations.exists(operation => operation.input.nonEmpty || operation.output.nonEmpty))
      "import 'models.dart' as _models;\n\n" else ""
    val interfaces = operations.groupBy(operation => operation.component -> operation.service).toVector.
      sortBy(_._1).map { case ((component, service), members) =>
        val typename = members.head.interfaceName
        val metadata = _comment(Json.obj("identity" -> s"$component.$service", "component" -> component, "service" -> service))
        val methods = members.sortBy(_.identity).map { operation =>
          val input = operation.input.map(source => s"_models.${models.typeName(source)} request").getOrElse("")
          val output = operation.output.map(source => s"_models.${models.typeName(source)}").getOrElse("void")
          val kind = operation.definition.kind match {
            case OperationModel.OperationKind.Command => "COMMAND"
            case OperationModel.OperationKind.Query => "QUERY"
          }
          val metadata = _comment(Json.obj("identity" -> operation.identity, "kind" -> kind,
            "input" -> operation.input.map(_.qualifiedName).getOrElse[String]("void"),
            "output" -> operation.output.map(_.qualifiedName).getOrElse[String]("void")))
          s"  $metadata\n  Future<$output> ${operation.methodName}($input);"
        }.mkString("\n\n")
        s"$metadata\nabstract interface class $typename {\n$methods\n}"
      }
    "// @dart=3.0\n// Generated from canonical operation contracts.\n" + imports + interfaces.mkString("\n\n") + "\n"
  }

  private def _require_symbols(operations: Vector[DartOperationProjection.Operation], models: DartModelProjection): Unit = {
    val symbols = mutable.Map.empty[String, String]
    DartNaming.reservedTypes.foreach(name => symbols.update(name, "Dart SDK/technical runtime"))
    models.objects.foreach { source =>
      symbols.update(models.typeName(source), source.qualifiedName)
      source match {
        case classification: MPowertype if models.isSealed(source) =>
          classification.kinds.foreach { kind =>
            symbols.update(models.typeName(source) + DartNaming.typeName(kind.name, source.qualifiedName), s"${source.qualifiedName}.${kind.name}")
          }
        case _ => ()
      }
    }
    operations.groupBy(operation => operation.component -> operation.service).toVector.sortBy(_._1).foreach {
      case ((component, service), members) =>
        val name = members.head.interfaceName
        val location = s"$component.$service"
        symbols.get(name).foreach(previous =>
          RAISE.invalidArgumentFault(s"$location: Dart interface '$name' collides with $previous."))
        symbols.update(name, location)
        members.groupBy(_.methodName).toVector.sortBy(_._1).collectFirst {
          case (method, entries) if entries.size > 1 => method
        }.foreach(method => RAISE.invalidArgumentFault(s"$location: duplicate normalized method '$method'."))
        members.foreach { operation =>
          if (DartNaming.objectMembers(operation.methodName))
            RAISE.invalidArgumentFault(s"${operation.identity}: reserved Object method '${operation.methodName}'.")
        }
    }
  }

  private def _comment(value: play.api.libs.json.JsObject): String =
    "// source " + Json.stringify(value).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
}
