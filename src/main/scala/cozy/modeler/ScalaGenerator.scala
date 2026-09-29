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
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
class ScalaGenerator(
  environment: Environment,
  model: SimpleModel,
  compositeStateMachines: Vector[CompositeStateMachineDefinition] = Vector.empty,
  workflows: Vector[WorkflowDefinition] = Vector.empty,
  generationTarget: ModelGenerationTarget = ModelGenerationTarget.Cncf
) {
  private val _transformer = {
    val config = Config.create(environment)
    val context = new Context(environment, config)
    val pcontext = new PContext(context)
    new Scala3RealmTransformer(pcontext)
  }

  def generate(p: MPackage): STree = {
    generationTarget match {
      case ModelGenerationTarget.Library =>
        ModelGenerationTarget.requireLibraryGeneratorInput(model, compositeStateMachines, workflows)
        STree(_transformer.transform(model).realm)
      case ModelGenerationTarget.Cncf =>
        _generate_cncf(_transformer.transform(model).realm)
    }
  }

  private def _generate_cncf(realm: Realm): STree = {
    val compositestatemachines = CompositeStateMachineScalaGenerator.generate(compositeStateMachines)
    val projectionmetadata = CompositeStateMachineProjectionMetadata.canonicalJson(compositeStateMachines)
    val actionproducermetadata = CompositeStateMachineActionProducerMetadata.generate(compositeStateMachines)
    val actionproducermetadatajson = CompositeStateMachineActionProducerMetadata.canonicalJson(compositeStateMachines)
    val actionprogramdefinitions = compositeStateMachines ++ workflows.map(_.compositeStateMachine)
    val actionprogram = CompositeStateMachineActionProgram.generate(actionprogramdefinitions)
    val actionprogramjson = CompositeStateMachineActionProgram.canonicalJson(actionprogramdefinitions)
    val statemachineworkflowabi = StateMachineWorkflowAbiGenerator.generate(workflows)
    val statemachineworkflowabijson = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)
    val hasprovidedapi = StateMachineProvidedApiAbiGenerator.hasProvidedOperations(workflows)
    val providedapiabi = if (hasprovidedapi) Some(StateMachineProvidedApiAbiGenerator.generate(workflows)) else None
    val providedapiabijson = if (hasprovidedapi) Some(StateMachineProvidedApiAbiGenerator.canonicalJson(workflows)) else None
    val hascandidateadmission = CandidateAdmissionProducerAbiGenerator.hasCandidateAdmission(compositeStateMachines, workflows)
    val candidateadmissionproducerabi = if (hascandidateadmission)
      Some(CandidateAdmissionProducerAbiGenerator.generate(compositeStateMachines, workflows))
    else
      None
    val candidateadmissionproducerabijson = if (hascandidateadmission)
      Some(CandidateAdmissionProducerAbiGenerator.canonicalJson(compositeStateMachines, workflows))
    else
      None
    val metadata = ComponentApiContractMetadata.generate(model) match {
      case Right(document) => document
      case Left(message) => org.goldenport.RAISE.invalidArgumentFault(message)
    }
    val builder = Realm.Builder()
    builder.set(CompositeStateMachineProjectionMetadata.metadataPath, projectionmetadata)
    builder.set(CompositeStateMachineActionProducerMetadata.metadataPath, actionproducermetadatajson)
    builder.set(CompositeStateMachineActionProgram.metadataPath, actionprogramjson)
    builder.set(StateMachineWorkflowAbiGenerator.metadataPath, statemachineworkflowabijson)
    providedapiabijson.foreach(value => builder.set(StateMachineProvidedApiAbiGenerator.metadataPath, value))
    candidateadmissionproducerabijson.foreach { value =>
      builder.set(CandidateAdmissionProducerAbiGenerator.metadataPath, value)
    }
    if (!metadata.isEmpty)
      builder.set("target/cozy/component-api-model.json", metadata.toCanonicalJson)
    val generatedrealm = realm + compositestatemachines + actionproducermetadata + actionprogram + statemachineworkflowabi + builder.build()
    val withcandidateadmission = candidateadmissionproducerabi.fold(generatedrealm)(generatedrealm + _)
    STree(providedapiabi.fold(withcandidateadmission)(withcandidateadmission + _))
  }
}
