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
 *  version Jul. 12, 2026
 *  version Sep.  7, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
class ScalaGenerator(
  environment: Environment,
  model: SimpleModel,
  compositeStateMachines: Vector[CompositeStateMachineDefinition] = Vector.empty
) {
  private val _transformer = {
    val config = Config.create(environment)
    val context = new Context(environment, config)
    val pcontext = new PContext(context)
    new Scala3RealmTransformer(pcontext)
  }

  def generate(p: MPackage): STree = {
    val r = _transformer.transform(model)
    val compositestatemachines = CompositeStateMachineScalaGenerator.generate(compositeStateMachines)
    val projectionmetadata = CompositeStateMachineProjectionMetadata.canonicalJson(compositeStateMachines)
    val actionproducermetadata = CompositeStateMachineActionProducerMetadata.generate(compositeStateMachines)
    val actionproducermetadatajson = CompositeStateMachineActionProducerMetadata.canonicalJson(compositeStateMachines)
    val actionprogram = CompositeStateMachineActionProgram.generate(compositeStateMachines)
    val actionprogramjson = CompositeStateMachineActionProgram.canonicalJson(compositeStateMachines)
    val metadata = ComponentApiContractMetadata.generate(model) match {
      case Right(document) => document
      case Left(message) => org.goldenport.RAISE.invalidArgumentFault(message)
    }
    val builder = Realm.Builder()
    builder.set(CompositeStateMachineProjectionMetadata.metadataPath, projectionmetadata)
    builder.set(CompositeStateMachineActionProducerMetadata.metadataPath, actionproducermetadatajson)
    builder.set(CompositeStateMachineActionProgram.metadataPath, actionprogramjson)
    if (!metadata.isEmpty)
      builder.set("target/cozy/component-api-model.json", metadata.toCanonicalJson)
    STree(r.realm + compositestatemachines + actionproducermetadata + actionprogram + builder.build())
  }
}
