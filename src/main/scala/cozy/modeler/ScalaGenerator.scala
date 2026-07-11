package cozy.modeler

import org.simplemodeling.model._
import org.simplemodeling.SimpleModeler.Config
import org.simplemodeling.SimpleModeler.Context
import org.simplemodeling.SimpleModeler.transformer.maker.PContext
import org.simplemodeling.SimpleModeler.transformers.Scala3RealmTransformer
import org.simplemodeling.SimpleModeler.generator.componentapi.ComponentApiContractMetadata
import org.goldenport.sexpr._
import org.goldenport.cli.Environment
import org.goldenport.realm.Realm

/*
 * @since   May.  5, 2025
 *  version May.  5, 2025
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
class ScalaGenerator(
  environment: Environment,
  model: SimpleModel
) {
  private val _transformer = {
    val config = Config.create(environment)
    val context = new Context(environment, config)
    val pcontext = new PContext(context)
    new Scala3RealmTransformer(pcontext)
  }

  def generate(p: MPackage): STree = {
    val r = _transformer.transform(model)
    val metadata = ComponentApiContractMetadata.generate(model) match {
      case Right(document) => document
      case Left(message) => org.goldenport.RAISE.invalidArgumentFault(message)
    }
    if (metadata.isEmpty)
      STree(r.realm)
    else {
      val builder = Realm.Builder()
      builder.set("target/cozy/component-api-model.json", metadata.toCanonicalJson)
      STree(r.realm + builder.build())
    }
  }
}
