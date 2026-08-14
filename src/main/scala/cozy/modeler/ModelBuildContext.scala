package cozy.modeler

import org.goldenport.kaleidox.model.{SchemaModel, EntityModel, DataTypeModel}
import org.goldenport.kaleidox.model.{PowertypeModel, StateMachineModel, EventModel}
import org.goldenport.kaleidox.model.ComponentSubsystemModel
import org.goldenport.kaleidox.model.OperationModel
import org.goldenport.kaleidox.model.ServiceModel
import org.goldenport.kaleidox.model.ValueModel
import org.goldenport.kaleidox.model.actor.ActorModel

/*
 * @since Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author ASAMI, Tomoharu
 */
private[modeler] trait ModelBuildContext {
  val schema: SchemaModel
  val entity: EntityModel
  val datatype: DataTypeModel
  val value: ValueModel
  val powertype: PowertypeModel
  val stateMachine: StateMachineModel
  val actor: ActorModel
  val componentSubsystem: ComponentSubsystemModel
  val service: ServiceModel
  val event: EventModel
  val operation: OperationModel
  val predefinedResultCatalog: PredefinedResultCatalog
  val componentStyleCatalog: ComponentStyleCatalog
  val cmlDeclaredTypeNames: Set[String]
  val relationships: Vector[org.simplemodeling.model.MComponent.RelationshipDefinition]
  val operationRelationshipBindings: Map[String, Modeler.OperationRelationshipBinding]
  val serviceOperationLeafContracts: Map[String, Modeler.ServiceOperationLeafContract]
}
