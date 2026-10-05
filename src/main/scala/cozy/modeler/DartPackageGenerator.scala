package cozy.modeler

import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.realm.Realm
import org.simplemodeling.model.SimpleModel

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
object DartPackageGenerator {
  case class Options(
    packageName: String,
    typeNames: Vector[String] = Vector.empty,
    operationNames: Vector[String] = Vector.empty,
    sealedTypeNames: Set[String] = Set.empty
  )

  def generate(model: KaleidoxModel, options: Options): Realm = {
    if (model.errors.nonEmpty)
      org.goldenport.RAISE.syntaxErrorFault(model.errors.map(_.en).mkString("; "))
    val packagename = DartNaming.requirePackageName(options.packageName)
    val builder = Modeler.ModelBuilder(model)
    val data = builder.buildValue()
    val operations = DartOperationProjection.select(model, builder, data, options.operationNames)
    val operationroots = operations.flatMap(operation => operation.input.toVector ++ operation.output.toVector).map(_.qualifiedName)
    val roots = (options.typeNames ++ operationroots).distinct.sorted
    val defaultall = options.typeNames.isEmpty && options.operationNames.isEmpty
    val selection = if (defaultall) data else if (roots.isEmpty) SimpleModel(Vector.empty) else data
    val modeloptions = DartModelGenerator.Options(if (defaultall) Vector.empty else roots, options.sealedTypeNames)
    val projection = DartModelProjection.select(selection, modeloptions)
    val models = DartModelRenderer.render(projection)
    val api = DartApiRenderer.render(operations, projection)
    val products = Vector(
      "pubspec.yaml" -> s"name: $packagename\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\n",
      s"lib/$packagename.dart" -> "// @dart=3.0\nexport 'src/models.dart';\nexport 'src/api.dart';\nexport 'src/json.dart';\n",
      "lib/src/models.dart" -> models,
      "lib/src/api.dart" -> api,
      "lib/src/json.dart" -> DartJsonRuntime.source,
      "README.md" -> _readme(packagename, operations, projection)
    )
    val realm = Realm.Builder()
    products.sortBy(_._1).foreach { case (path, source) => realm.set(path, source) }
    realm.build()
  }

  private def _readme(
    packagename: String,
    operations: Vector[DartOperationProjection.Operation],
    projection: DartModelProjection
  ): String = {
    val models = projection.objects.map(source => s"- `${source.qualifiedName}`").mkString("\n")
    val identities = operations.map(operation => s"- `${operation.identity}`").mkString("\n")
    val usage = operations.headOption.map { operation =>
      val input = operation.input.map(source => s", ${projection.typeName(source)} request").getOrElse("")
      val output = operation.output.map(projection.typeName).getOrElse("void")
      val argument = if (operation.input.nonEmpty) "request" else ""
      s"Future<$output> use(${operation.interfaceName} api$input) => api.${operation.methodName}($argument);\n"
    }.getOrElse("// Import the model and technical JSON contracts from this barrel.\n")
    s"# $packagename\n\nGenerated from canonical model and operation contracts. Dart SDK >=3.0.0 <4.0.0; SDK libraries only.\n\n" +
      "Canonical data identities:\n\n" + (if (models.isEmpty) "No data types selected." else models) + "\n\n" +
      "Canonical operation identities:\n\n" + (if (identities.isEmpty) "No operations declared or selected." else identities) + "\n\n" +
      s"```dart\nimport 'package:$packagename/$packagename.dart';\n\n$usage```\n\n" +
      "Implement the generated interfaces in the consumer. Shared requests and responses reuse the same canonical DTO and codec. " +
      "Source void input has no argument; void output returns Future<void>, with no unit DTO. " +
      "Source-declared error values use the same data projection when selected; the source operation contract has no declared error-type slot or checked throws. " +
      "DartJsonDecodingException represents technical decoding failure separately from modeled error values and consumer policy. " +
      "Transport, URLs, authentication and authorization remain consumer responsibilities.\n"
  }
}
