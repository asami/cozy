package cozy.modeler

import org.simplemodeling.model._
import org.simplemodeling.SimpleModeler.Config
import org.simplemodeling.SimpleModeler.Context
import org.simplemodeling.SimpleModeler.transformer.maker.PContext
import org.simplemodeling.SimpleModeler.transformers.Scala3RealmTransformer
import org.goldenport.sexpr._
import org.goldenport.cli.Environment

/*
 * @since   May.  5, 2025
 * @version May.  5, 2025
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
    STree(r.realm)
  }
}
