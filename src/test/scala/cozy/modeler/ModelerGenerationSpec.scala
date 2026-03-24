package cozy.modeler

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import org.scalatest.funsuite.AnyFunSuite
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.record.v2.{CFormat, CMaxLength, CMinLength, CRegex}

/*
 * @since   May. 17, 2025
 * @version Mar. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class ModelerGenerationSpec extends AnyFunSuite {
  test("modeler-scala generates DomainComponent") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/test.dox")
    val out = base.resolve("target/test-generated/modeler-scala")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("object DomainComponent"))
    assert(!content.contains("exec_from("))
    assert(!content.contains("collectionId: EntityCollectionId = ???"))
    assert(content.contains("extends Component with CollectionTransitionRuleProvider"))
    assert(content.contains("override def stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]] = Vector.empty"))
    assert(content.contains("override def stateMachineDefinitions: Vector[org.goldenport.cncf.statemachine.CmlStateMachineDefinition] = Vector.empty"))
    assert(content.contains("override def aggregateDefinitions: Vector[org.goldenport.cncf.entity.aggregate.AggregateDefinition] = Vector("))
    assert(content.contains("override def viewDefinitions: Vector[org.goldenport.cncf.entity.view.ViewDefinition] = Vector("))
  }

  test("modeler-scala-value generates value model without component") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/test.dox")
    val out = base.resolve("target/test-generated/modeler-scala-value")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala-value", input.toString, s"--save=${out.toString}"))

    val entity = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/entity/Person.scala"
    )
    val query = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/entity/query/Person.scala"
    )
    val domainComponent = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(entity), s"generated entity file not found: $entity")
    assert(Files.exists(query), s"generated query value file not found: $query")
    assert(!Files.exists(domainComponent), s"DomainComponent must not be generated in value mode: $domainComponent")
  }

  test("modeler-scala-value accepts powertype-only cml without component generation") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/powertype-literate.dox")
    val out = base.resolve("target/test-generated/modeler-scala-value-powertype-only")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala-value", input.toString, s"--save=${out.toString}"))

    val buildSbt = out.resolve("build.sbt")
    val countryCode = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/value/CountryCode.scala"
    )
    val addressType = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/value/AddressType.scala"
    )
    val domainComponent = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(buildSbt), s"build.sbt not found: $buildSbt")
    assert(Files.exists(countryCode), s"powertype file not found: $countryCode")
    assert(Files.exists(addressType), s"powertype file not found: $addressType")
    assert(!Files.exists(domainComponent), s"DomainComponent must not be generated in value mode: $domainComponent")
  }

  test("modeler-scala-value accepts statemachine-only cml without component generation") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/statemachine-division-alias.dox")
    val out = base.resolve("target/test-generated/modeler-scala-value-statemachine-only")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala-value", input.toString, s"--save=${out.toString}"))

    val buildSbt = out.resolve("build.sbt")
    val lifecycle = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/statemachine/lifecycle.scala"
    )
    val domainComponent = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(buildSbt), s"build.sbt not found: $buildSbt")
    assert(Files.exists(lifecycle), s"statemachine file not found: $lifecycle")
    assert(!Files.exists(domainComponent), s"DomainComponent must not be generated in value mode: $domainComponent")
  }

  test("modeler-scala expands attributes from SimpleEntity parent") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/simpleentity-parent.dox")
    val out = base.resolve("target/test-generated/modeler-scala-simpleentity-parent")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/entity/Person.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val notGeneratedSimpleEntity = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/entity/SimpleEntity.scala"
    )
    assert(!Files.exists(notGeneratedSimpleEntity), s"SimpleEntity must not be generated: $notGeneratedSimpleEntity")
    val content = Files.readString(generated)
    assert(content.contains("extends org.simplemodeling.model.SimpleEntity with EntityPersistable"))
    assert(content.contains("case class Person(override val id: EntityId"))
    assert(content.contains("nameAttributes: NameAttributes"))
    assert(content.contains("age: Option[Age]"))
    assert(content.contains("PROP_ID"))
    assert(content.contains("PROP_NAME_ATTRIBUTES"))
    assert(content.contains("PROP_AGE"))

    val generatedCreate = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/entity/create/Person.scala"
    )
    assert(Files.exists(generatedCreate), s"generated file not found: $generatedCreate")
    val createContent = Files.readString(generatedCreate)
    assert(createContent.contains("case class Person(override val id: Option[EntityId]"))
    assert(createContent.contains("nameAttributes: NameAttributes"))
    assert(createContent.contains("age: Option[Age]"))
  }

  test("modeler-scala generates toDataStore with db column names") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/db-column-options.dox")
    val out = base.resolve("target/test-generated/modeler-scala-db-column-options")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/entity/Person.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("def toRecord(): Record"))
    assert(content.contains("\"external_id\" -> _to_external_value(id)"))
    assert(content.contains("\"display_name_ext\" -> _to_external_value(displayName)"))
    assert(content.contains("def toDataStore(): Record"))
    assert(content.contains("\"person_id\" -> _to_data_store_value(id)"))
    assert(content.contains("\"person_display_name\" -> _to_data_store_value(displayName)"))
    assert(content.contains("\"age\" -> _to_data_store_value(age)"))
    assert(content.contains("INPUT_KEYS_DISPLAY_NAME"))
    assert(content.contains("\"displayName\""))
    assert(content.contains("\"display_name\""))
    assert(content.contains("def schema(): Schema"))
    assert(content.contains("val schema: org.goldenport.schema.Schema = org.goldenport.schema.Schema("))
    assert(content.contains("org.simplemodeling.model.value.BaseContent.simple(\"displayName\")"))

    val generatedCreate = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/entity/create/Person.scala"
    )
    assert(Files.exists(generatedCreate), s"generated file not found: $generatedCreate")
    val createContent = Files.readString(generatedCreate)
    assert(createContent.contains("val schema: org.goldenport.schema.Schema = domain.entity.Person.schema"))

    val generatedQuery = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/entity/query/Person.scala"
    )
    assert(Files.exists(generatedQuery), s"generated file not found: $generatedQuery")
    val queryContent = Files.readString(generatedQuery)
    assert(queryContent.contains("val schema: org.goldenport.schema.Schema = domain.entity.Person.schema"))

    val generatedUpdate = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/entity/update/Person.scala"
    )
    assert(Files.exists(generatedUpdate), s"generated file not found: $generatedUpdate")
    val updateContent = Files.readString(generatedUpdate)
    assert(updateContent.contains("val schema: org.goldenport.schema.Schema = domain.entity.Person.schema"))
  }

  test("modeler-scala parses StateMachine CML heading syntax") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/statemachine-cml.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-cml")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("override def stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]] = Vector("))
    assert(content.contains("override def stateMachineDefinitions: Vector[org.goldenport.cncf.statemachine.CmlStateMachineDefinition] = Vector("))
    assert(content.contains("name = \"lifecycle\""))
    assert(content.contains("states = Vector(\"Draft\", \"Published\")"))
    assert(content.contains("events = Vector(\"publish\")"))
    assert(content.contains("eventName = \"publish\""))
    assert(content.contains("guard = Some(StateMachineRuleBuilder.guardExpression[Any](\"event.amount > 0\")"))
    assert(content.contains("StateMachineRuleBuilder.updateRule[Any]("))
  }

  test("modeler-scala maps identifier guard to guardRef") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/statemachine-cml-guard-ref.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-cml-guard-ref")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("eventName = \"publish\""))
    assert(content.contains("guard = Some(StateMachineRuleBuilder.guardRef[Any](\"paymentConfirmed\", stateMachineGuardResolver))"))
  }

  test("modeler-scala emits deterministic declaration order for mixed guards") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/statemachine-cml-guard-composite-order.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-cml-guard-composite-order")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("eventName = \"submit\""))
    assert(content.contains("eventName = \"publish\""))
    assert(content.contains("eventName = \"approve\""))
    assert(content.contains("eventName = \"reject\""))
    assert(content.contains("guard = Some(StateMachineRuleBuilder.guardRef[Any](\"paymentConfirmed\", stateMachineGuardResolver))"))
    assert(content.contains("guard = Some(StateMachineRuleBuilder.guardExpression[Any](\"event.amount > 0 && reviewerApproved\")"))
    assert(content.contains("guard = Some(StateMachineRuleBuilder.guardExpression[Any](\"reviewerRejected || event.reasonPresent\")"))
    assert(content.contains("declarationOrder = 0"))
    assert(content.contains("declarationOrder = 1"))
    assert(content.contains("declarationOrder = 2"))
    assert(content.contains("declarationOrder = 3"))
    assert(_count(content, "priority = 0") >= 4)
  }

  test("kaleidox parses POWERTYPE section and ignores narrative subsection") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/powertype-literate.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val powertype = model.takePowertypeModel
    val divisions = model.divisions.map(_.name).mkString(",")
    val errors = model.errors.map(_.toString).mkString("|")
    assert(
      powertype.classes.contains("CountryCode"),
      s"CountryCode missing; actual keys=${powertype.classes.keys.mkString(",")}, divisions=$divisions, errors=$errors"
    )
    assert(
      powertype.classes.contains("AddressType"),
      s"AddressType missing; actual keys=${powertype.classes.keys.mkString(",")}, divisions=$divisions, errors=$errors"
    )
    assert(!powertype.classes.contains("Overview"))
    assert(powertype.classes("CountryCode").packageName == "domain.value")
    assert(powertype.classes("AddressType").packageName == "domain.value")
  }

  test("kaleidox accepts STATE-MACHINE top-level alias") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/statemachine-division-alias.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val sm = model.takeStateMachineModel
    val divisions = model.divisions.map(_.name).mkString(",")
    val errors = model.errors.map(_.toString).mkString("|")
    val lifecycle = sm.getClass("lifecycle").getOrElse {
      fail(s"state machine lifecycle is missing; actual keys=${sm.classes.keys.mkString(",")}, divisions=$divisions, errors=$errors")
    }
    assert(lifecycle.states.exists(_.name == "Draft"))
    assert(lifecycle.states.exists(_.name == "Published"))
  }

  test("kaleidox normalizes attribute constraint metadata to record constraints") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/constraint-metadata.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val schema = model.takeEntityModel.get("CountryCode").getOrElse {
      fail("Entity CountryCode is missing")
    }.schema
    val column = schema.columns.find(_.name == "value").getOrElse {
      fail("Column value is missing")
    }
    assert(column.constraints.exists(_.isInstanceOf[CMinLength]))
    assert(column.constraints.exists(_.isInstanceOf[CMaxLength]))
    assert(column.constraints.exists(_.isInstanceOf[CRegex]))
    assert(column.constraints.exists(_.isInstanceOf[CFormat]))
  }

  test("kaleidox accepts extended format values for CFormat constraints") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/constraint-format-extended.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val schema = model.takeEntityModel.get("ContactProfile").getOrElse {
      fail("Entity ContactProfile is missing")
    }.schema
    val createdAt = schema.columns.find(_.name == "created_at").getOrElse {
      fail("Column created_at is missing")
    }
    val phoneNumber = schema.columns.find(_.name == "phone_number").getOrElse {
      fail("Column phone_number is missing")
    }
    val createdAtFormats = createdAt.constraints.collect { case CFormat(f) => f.toLowerCase }
    val phoneFormats = phoneNumber.constraints.collect { case CFormat(f) => f.toLowerCase }
    assert(createdAtFormats.contains("date-time"))
    assert(phoneFormats.contains("phone"))
  }

  test("kaleidox parses Event metadata in Entity Event section") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/event-metadata.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val entity = model.takeEntityModel.get("Person").getOrElse {
      fail("Entity Person is missing")
    }
    val events = entity.schemaClass.events
    assert(events.size == 3)

    val created = events.find(_.name == "person.created").getOrElse {
      fail("person.created event is missing")
    }
    assert(created.category == "ActionEvent")
    assert(created.kind.contains("created"))
    assert(created.selectors.get("source").contains("crm"))
    assert(created.actionName.contains("person.sync"))
    assert(created.priority == 0)

    val updated = events.find(_.name == "person.updated").getOrElse {
      fail("person.updated event is missing")
    }
    assert(updated.category == "ActionEvent")
    assert(updated.kind.contains("updated"))
    assert(updated.selectors.get("source").contains("crm"))
    assert(updated.actionName.contains("person.sync"))
    assert(updated.priority == 1)

    val shipped = events.find(_.name == "order.shipped").getOrElse {
      fail("order.shipped event is missing")
    }
    assert(shipped.category == "NonActionEvent")
    assert(shipped.kind.contains("shipped"))
    assert(shipped.selectors.isEmpty)
    assert(shipped.actionName.isEmpty)
    assert(shipped.priority == 0)
  }

  test("kaleidox parses Event metadata in YAML section body") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/event-metadata-yaml-body.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val entity = model.takeEntityModel.get("Person").getOrElse {
      fail("Entity Person is missing")
    }
    val created = entity.schemaClass.events.find(_.name == "person.created").getOrElse {
      fail("person.created event is missing")
    }
    assert(created.category == "ActionEvent")
    assert(created.kind.contains("created"))
    assert(created.selectors.get("source").contains("crm"))
    assert(created.actionName.contains("person.sync"))
    assert(created.priority == 2)
  }

  test("kaleidox parses Aggregate/View metadata in Entity section") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/aggregate-view-metadata.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val entity = model.takeEntityModel.get("Person").getOrElse {
      fail("Entity Person is missing")
    }

    val aggregate = entity.aggregate.getOrElse {
      fail("Aggregate is missing")
    }
    assert(aggregate.commands.nonEmpty)
    assert(aggregate.commands.head.name == "createPerson")
    assert(aggregate.commands.head.events.contains("person.created"))
    assert(aggregate.state.exists(_.name == "name"))
    assert(aggregate.invariants.exists(_.name == "nameRequired"))

    val view = entity.view.getOrElse {
      fail("View is missing")
    }
    assert(view.attributes.exists(_.name == "id"))
    assert(view.attributes.exists(_.name == "name"))
    assert(view.queries.exists(_.name == "searchPublished"))
    assert(view.sourceEvents.contains("person.created"))
    assert(view.rebuildable.contains(true))
  }

  test("modeler-scala emits eventReceptionDefinitions from Event section") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/event-metadata.dox")
    val out = base.resolve("target/test-generated/modeler-scala-event-metadata")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("def eventReceptionDefinitions: Vector[org.goldenport.cncf.event.CmlEventDefinition] = Vector("))
    assert(content.contains("name = \"person.created\""))
    assert(content.contains("category = org.goldenport.cncf.event.CmlEventCategory.ActionEvent"))
    assert(content.contains("kind = Some(\"created\")"))
    assert(content.contains("selectors = Map(\"source\" -> \"crm\")"))
    assert(content.contains("actionName = Some(\"person.sync\")"))
    assert(content.contains("priority = 1"))
    assert(content.contains("name = \"order.shipped\""))
    assert(content.contains("category = org.goldenport.cncf.event.CmlEventCategory.NonActionEvent"))
    assert(content.contains("actionName = None"))
  }

  test("kaleidox parses and normalizes OPERATION grammar") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/operation-grammar.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val opmodel = model.takeOperationModel
    val normalized = opmodel.normalizedOperations

    assert(normalized.exists(x => x.name == "createOrder" && x.kind.toString == "Command" && x.inputType == "CreateOrder"))
    assert(normalized.exists(x => x.name == "getOrder" && x.kind.toString == "Query" && x.inputType == "GetOrder"))
    assert(normalized.exists(x => x.name == "savePerson" && x.inputType == "SavePersonInput"))
    assert(normalized.size == 3)
  }

  test("kaleidox parses OPERATION kind marker grammar (COMMAND/QUERY headings)") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/operation-grammar-kind-marker.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val opmodel = model.takeOperationModel
    val normalized = opmodel.normalizedOperations

    assert(normalized.exists(x => x.name == "createOrder" && x.kind.toString == "Command" && x.inputType == "CreateOrder"))
    assert(normalized.exists(x => x.name == "getOrder" && x.kind.toString == "Query" && x.inputType == "GetOrder"))
    assert(normalized.size == 2)
  }

  test("modeler-scala emits operationDefinitions from OPERATION section") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/operation-grammar.dox")
    val out = base.resolve("target/test-generated/modeler-scala-operation-grammar")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("override def operationDefinitions: Vector[org.goldenport.cncf.operation.CmlOperationDefinition] = Vector("))
    assert(content.contains("name = \"createOrder\""))
    assert(content.contains("kind = \"COMMAND\""))
    assert(content.contains("inputType = \"CreateOrder\""))
    assert(content.contains("name = \"savePerson\""))
    assert(content.contains("inputType = \"SavePersonInput\""))
    assert(content.contains("inputValueKind = \"COMMAND_VALUE\""))
  }

  test("kaleidox parses COMPONENT/SUBSYSTEM grammar") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/component-subsystem-grammar.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val cs = model.takeComponentSubsystemModel

    assert(cs.components.nonEmpty)
    val component = cs.components.find(_.name == "person").getOrElse {
      fail("component 'person' is missing")
    }
    assert(component.coordinates.exists(_.asString == "org.simplemodeling.car:person-service:0.1.0"))
    assert(component.componentlets.contains("person_core"))
    assert(component.extensionPoints.contains("transport"))
    assert(component.extensionBindings.get("transport").contains("grpc"))

    val subsystem = cs.subsystems.find(_.name == "identity").getOrElse {
      fail("subsystem 'identity' is missing")
    }
    assert(subsystem.components.exists(_.asString == "org.simplemodeling.car:person-service:0.1.0"))
    assert(subsystem.extensionBindings.get("transport").contains("http"))
    assert(subsystem.config.get("profile").contains("prod"))
  }

  test("modeler-scala emits component/subsystem metadata records") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/component-subsystem-grammar.dox")
    val out = base.resolve("target/test-generated/modeler-scala-component-subsystem-grammar")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/PersonComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("def componentDefinitionRecords: Vector[Record] = Vector("))
    assert(content.contains("def subsystemDefinitionRecords: Vector[Record] = Vector("))
    assert(content.contains("\"name\" -> \"person\""))
    assert(content.contains("\"coordinates\" -> Vector(\"org.simplemodeling.car:person-service:0.1.0\")"))
    assert(content.contains("\"extension_bindings\" -> Record.data("))
    assert(content.contains("\"transport\" -> \"grpc\""))
    assert(content.contains("\"name\" -> \"identity\""))
  }

  test("modeler-scala emits default component metadata when COMPONENT section is missing") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/component-subsystem-default-component.dox")
    val out = base.resolve("target/test-generated/modeler-scala-component-subsystem-default-component")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("def componentDefinitionRecords: Vector[Record] = Vector("))
    assert(content.contains("\"name\" -> \"domain\""))
    assert(content.contains("\"coordinates\" -> Vector.empty"))
    assert(content.contains("\"componentlets\" -> Vector(\"audit_sink\")"))
    assert(content.contains("\"extension_points\" -> Vector(\"observability\")"))
    assert(content.contains("\"extension_bindings\" -> Record.empty"))
  }


  test("kaleidox parses component package override") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/component-subsystem-component-package.dox")
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
    val cs = model.takeComponentSubsystemModel

    val component = cs.components.find(_.name == "domain").getOrElse {
      fail("component 'domain' is missing")
    }
    assert(component.packageName.contains("textus.user.account"))
  }

  test("modeler-scala emits component in configured package") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/component-subsystem-component-package.dox")
    val out = base.resolve("target/test-generated/modeler-scala-component-subsystem-component-package")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/textus/user/account/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("package textus.user.account"))
    assert(content.contains("object DomainComponent"))
  }

  test("modeler-scala rejects operation kind/input value mismatch") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/operation-grammar-invalid-kind-mismatch.dox")
    val out = base.resolve("target/test-generated/modeler-scala-operation-invalid-kind")
    _delete_recursively(out)

    val output = _run_modeler_scala(input, out)
    assert(output.contains("TYPE=COMMAND cannot use query-value input"), s"unexpected output: $output")
  }

  test("modeler-scala rejects invalid subsystem component coordinate") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/component-subsystem-invalid-coordinate.dox")
    val out = base.resolve("target/test-generated/modeler-scala-component-subsystem-invalid-coordinate")
    _delete_recursively(out)

    val output = _run_modeler_scala(input, out)
    assert(output.contains("invalid coordinate"), s"unexpected output: $output")
  }

  test("modeler-scala merges Entity Event and top-level Event definitions") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/event-metadata-mixed.dox")
    val out = base.resolve("target/test-generated/modeler-scala-event-metadata-mixed")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("name = \"person.created\""))
    assert(content.contains("name = \"system.heartbeat\""))
    assert(content.contains("name = \"system.alert\""))
    assert(content.contains("category = org.goldenport.cncf.event.CmlEventCategory.ActionEvent"))
    assert(content.contains("category = org.goldenport.cncf.event.CmlEventCategory.NonActionEvent"))
    assert(content.contains("actionName = Some(\"system.pulse\")"))
    assert(content.contains("selectors = Map(\"level\" -> \"warn\")"))
  }

  test("modeler-scala emits routing/subscription definitions from CML") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/event-routing-subscription.dox")
    val out = base.resolve("target/test-generated/modeler-scala-event-routing-subscription")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("def eventRoutingDefinitions: Vector[org.goldenport.cncf.event.CmlRoutingDefinition] = Vector("))
    assert(content.contains("name = \"crm-route\""))
    assert(content.contains("topic = Some(\"crm.events\")"))
    assert(content.contains("service = Some(\"person\")"))
    assert(content.contains("partition = Some(\"organization\")"))
    assert(content.contains("def eventSubscriptionDefinitions: Vector[org.goldenport.cncf.event.CmlSubscriptionDefinition] = Vector("))
    assert(content.contains("name = \"person-sync\""))
    assert(content.contains("eventName = \"person.created\""))
    assert(content.contains("route = org.goldenport.cncf.event.DispatchRoute.Unicast"))
    assert(content.contains("target = Some(\"targetId\")"))
    assert(content.contains("actionName = \"person.sync\""))
    assert(content.contains("declaredTargetUpperBound = 2"))
    assert(content.contains("activation = Some(org.goldenport.cncf.event.EntityActivationMode.KeepResident)"))
  }

  test("modeler-scala reports missing on in StateMachine transition") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("target/test-generated/modeler-errors/missing-on.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-invalid-missing-on")
    _delete_recursively(out)
    _write(
      input,
      """# Entity
        |
        |## Person
        |
        |### Attribute
        |
        || name | type     | multiplicity |
        ||------+----------+--------------|
        || id   | entityid | 1            |
        || name | name     | 1            |
        |
        |### StateMachine
        |
        |#### lifecycle
        |
        |##### State
        |
        |###### Draft
        |
        |####### Transition
        |- to :: Published
        |- guard :: paymentConfirmed
        |
        |###### Published
        |""".stripMargin
    )

    val output = _run_modeler_scala(input, out)
    assert(output.contains("transition requires on"), s"unexpected output: $output")
    assert(!output.contains("URI is not absolute"), s"unexpected output: $output")
  }

  test("modeler-scala reports unknown transition target in StateMachine") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("target/test-generated/modeler-errors/unknown-target.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-invalid-unknown-target")
    _delete_recursively(out)
    _write(
      input,
      """# Entity
        |
        |## Person
        |
        |### Attribute
        |
        || name | type     | multiplicity |
        ||------+----------+--------------|
        || id   | entityid | 1            |
        || name | name     | 1            |
        |
        |### StateMachine
        |
        |#### lifecycle
        |
        |##### State
        |
        |###### Draft
        |
        |####### Transition
        |- to :: PublishedX
        |- on :: publish
        |
        |###### Published
        |
        |##### Event
        |
        |###### publish
        |""".stripMargin
    )

    val output = _run_modeler_scala(input, out)
    assert(output.contains("target PublishedX is not defined"), s"unexpected output: $output")
    assert(!output.contains("URI is not absolute"), s"unexpected output: $output")
  }

  test("modeler-scala reports undeclared event in StateMachine") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("target/test-generated/modeler-errors/unknown-event.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-invalid-unknown-event")
    _delete_recursively(out)
    _write(
      input,
      """# Entity
        |
        |## Person
        |
        |### Attribute
        |
        || name | type     | multiplicity |
        ||------+----------+--------------|
        || id   | entityid | 1            |
        || name | name     | 1            |
        |
        |### StateMachine
        |
        |#### lifecycle
        |
        |##### State
        |
        |###### Draft
        |
        |####### Transition
        |- to :: Published
        |- on :: publish
        |
        |###### Published
        |
        |##### Event
        |
        |###### approve
        |""".stripMargin
    )

    val output = _run_modeler_scala(input, out)
    assert(output.contains("undeclared event publish"), s"unexpected output: $output")
    assert(!output.contains("URI is not absolute"), s"unexpected output: $output")
  }

  private def _run_modeler_scala(input: Path, out: Path): String = {
    Files.createDirectories(out.getParent)
    val outBuffer = new ByteArrayOutputStream
    val errBuffer = new ByteArrayOutputStream
    val outPs = new PrintStream(outBuffer, true, StandardCharsets.UTF_8.name())
    val errPs = new PrintStream(errBuffer, true, StandardCharsets.UTF_8.name())
    try {
      Console.withOut(outPs) {
        Console.withErr(errPs) {
          cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))
        }
      }
    } finally {
      outPs.close()
      errPs.close()
    }
    outBuffer.toString(StandardCharsets.UTF_8.name()) + "\n" + errBuffer.toString(StandardCharsets.UTF_8.name())
  }

  private def _write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, content)
  }

  private def _delete_recursively(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try
        stream.sorted(Comparator.reverseOrder()).forEach(p => Files.deleteIfExists(p))
      finally
        stream.close()
    }
  }

  private def _count(content: String, token: String): Int = {
    @annotation.tailrec
    def go(index: Int, acc: Int): Int = {
      val i = content.indexOf(token, index)
      if (i < 0)
        acc
      else
        go(i + token.length, acc + 1)
    }
    go(0, 0)
  }
}
