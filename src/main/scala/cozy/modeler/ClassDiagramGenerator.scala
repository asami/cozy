package cozy.modeler

import org.simplemodeling.model._
import org.simplemodeling.SimpleModeler.{Context => UmlContext, Config => UmlConfig}
import org.simplemodeling.SimpleModeler.generators.uml.{ClassDiagramGenerator => UmlClassDiagramGenerator}
import org.simplemodeling.SimpleModeler.generators.uml.{OverviewPerspective, HilightPerspective, DetailPerspective}
import org.goldenport.sexpr._
import org.goldenport.cli.Environment

/*
 * @since   Sep. 17, 2023
 * @version Oct. 12, 2023
 * @author  ASAMI, Tomoharu
 */
class ClassDiagramGenerator(
  environment: Environment,
  model: SimpleModel
) {
  private val _generator = {
    val config = UmlConfig.create(environment)
    val context = new UmlContext(environment, config)
    new UmlClassDiagramGenerator(context, model)
  }

  def generate(p: MPackage): SImage = {
    val perspective = DetailPerspective // OverviewPerspective // DetailPerspective // HilightPerspective
    val binary = _generator.makeClassDiagramSvg(p, perspective)
    SImage.svg(binary)
  }
}
