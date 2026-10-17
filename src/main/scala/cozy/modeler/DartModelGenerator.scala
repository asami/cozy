package cozy.modeler

import org.goldenport.realm.Realm
import org.simplemodeling.model.SimpleModel

/*
 * @since   Oct.  5, 2026
 * @version Oct.  6, 2026
 */
object DartModelGenerator {
  case class Options(
    typeNames: Vector[String] = Vector.empty,
    sealedTypeNames: Set[String] = Set.empty,
    jsonFieldStyle: DartJsonFieldStyle = DartJsonFieldStyle.Source
  )

  def generate(model: SimpleModel, options: Options = Options()): Realm = {
    val projection = DartModelProjection.select(model, options)
    val models = DartModelRenderer.render(projection)
    val json = DartJsonRuntime.source
    val builder = Realm.Builder()
    builder.set("lib/src/models.dart", models)
    builder.set("lib/src/json.dart", json)
    builder.build()
  }
}
