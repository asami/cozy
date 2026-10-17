package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsValue, Json}

/*
 * @since   Jun. 23, 2026
 *  version Jul. 19, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
class CmlModelMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CmlModelMetadataSpec._

  "CML model metadata" should {
    "write source provenance without leaking local absolute paths" in {
      Given("a CML source file under a CAR project")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata")
      val source = dir.resolve("src/main/cozy/sample.cml")
      Files.createDirectories(source.getParent)
      Files.writeString(
        source,
        """# ENTITY
          |
          |## KnowledgeItem
          |
          |### BRIEF
          |
          |Knowledge item brief.
          |
          |### DESCRIPTION
          |
          |Knowledge item description.
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val json = dir.resolve("target/model-metadata.json")
      val yaml = dir.resolve("target/model-metadata.yaml")

      When("Cozy writes machine-readable model metadata for a public catalog")
      CmlModelMetadata.write(source, json, yaml, "src/main/cozy/sample.cml", "concept")
      val metadata = Files.readString(json)
      val yamltext = Files.readString(yaml)
      val parsedjson = Json.parse(metadata)
      val parsedyaml = _load_yaml(yaml)

      Then("the public source path is project-relative and the local workspace path is not leaked")
      metadata should include ("cozy.cml.model-metadata.v1")
      metadata should include ("\"path\" : \"src/main/cozy/sample.cml\"")
      metadata should not include (dir.toAbsolutePath.normalize().toString)
      yamltext should not include (dir.toAbsolutePath.normalize().toString)
      (parsedjson \ "source").as[JsObject] shouldBe _source_json("src/main/cozy/sample.cml")
      (parsedyaml \ "source").as[JsObject] shouldBe _source_json("src/main/cozy/sample.cml")
      (parsedjson \ "schema").as[String] shouldBe "cozy.cml.model-metadata.v1"
      (parsedyaml \ "schema").as[String] shouldBe "cozy.cml.model-metadata.v1"

      And("CML elements expose canonical public schema keys for glossary integration")
      metadata should include ("\"kind\" : \"entity\"")
      metadata should include ("\"name\" : \"KnowledgeItem\"")
      metadata should include ("\"termId\" : \"concept:knowledge-item\"")
      metadata should include ("\"glossaryPath\" : \"glossary/concept/knowledge-item.html\"")
      metadata should include ("\"cozyVersion\"")
      metadata should include ("\"rdfCandidates\"")
      yamltext should include ("termId: \"concept:knowledge-item\"")
      yamltext should include ("fields: []")
      Seq(parsedjson, parsedyaml).foreach { document =>
        val element = (document \ "modelElements").as[Vector[JsValue]].head
        (element \ "termId").as[String] shouldBe "concept:knowledge-item"
        (element \ "glossaryPath").as[String] shouldBe "glossary/concept/knowledge-item.html"
        (element \ "fields").as[Vector[JsValue]] shouldBe Vector.empty
      }
    }

    "preserve source provenance and Entity semantics through both source routes" in {
      Given("a readable Entity CML source and its actually loaded Kaleidox model")
      val dir = _work_directory("source-routes")
      val sourcepath = "src/main/cozy/notice.cml"
      val source = dir.resolve(sourcepath)
      Files.createDirectories(source.getParent)
      Files.writeString(source, _entity_cml("Notice", "A catalog notice."), StandardCharsets.UTF_8)
      val model = KaleidoxModel.load(KaleidoxConfig.default, source.toFile)
      val directjson = dir.resolve("direct.json")
      val directyaml = dir.resolve("direct.yaml")
      val loadedjson = dir.resolve("loaded.json")
      val loadedyaml = dir.resolve("loaded.yaml")
      val expectedsource = CmlModelMetadata.Source(sourcepath, "cozy-modeler", org.simplemodeling.cozy.BuildInfo.version)
      val expectedloaded = CmlModelMetadata.Element(
        "entity", "Notice", "concept:notice", "glossary/concept/notice.html",
        CmlModelMetadata.Descriptive("Notice", None, None, Some("A catalog notice.")), None
      )
      val expecteddirect = expectedloaded.copy(
        fields = Vector(CmlModelMetadata.Field("id", "entityid", "1", Vector.empty))
      )

      When("Cozy projects both source routes and serializes their actual JSON and YAML")
      val direct = CmlModelMetadata.fromCml(source, sourcepath, "concept")
      val loaded = CmlModelMetadata.fromKaleidox(model, source, sourcepath, "concept")
      CmlModelMetadata.write(source, directjson, directyaml, sourcepath, "concept")
      Files.writeString(loadedjson, loaded.toJsonString, StandardCharsets.UTF_8)
      Files.writeString(loadedyaml, loaded.toYamlString, StandardCharsets.UTF_8)
      val documents = Vector(
        (Json.parse(Files.readString(directjson)), expecteddirect),
        (_load_yaml(directyaml), expecteddirect),
        (Json.parse(Files.readString(loadedjson)), expectedloaded),
        (_load_yaml(loadedyaml), expectedloaded)
      )

      Then("both routes retain the three source facts and their existing complete Entity projections")
      model.errors shouldBe Vector.empty
      direct.source shouldBe expectedsource
      loaded.source shouldBe expectedsource
      loaded.surface shouldBe CmlModelMetadata.Surface(None)
      loaded.modelElements shouldBe Vector(expectedloaded)
      direct.modelElements shouldBe Vector(expecteddirect)
      direct.modelElements.map(_.kind) shouldBe Vector("entity")
      direct.modelElements.map(_.name) shouldBe Vector("Notice")
      direct.modelElements.map(_.descriptive.description) shouldBe Vector(Some("A catalog notice."))
      documents.foreach { case (document, expected) =>
        (document \ "source").as[JsObject] shouldBe _source_json(sourcepath)
        (document \ "modelElements").as[Vector[JsValue]].map(element => (element \ "name").as[String]) shouldBe Vector("Notice")
        (document \ "modelElements").as[Vector[JsValue]] shouldBe Vector(expected.toJson)
      }
    }

    "retain exact nonhash provenance and Entity semantics for generated relative sources" in {
      Given("safe relative CML paths with valid Entity names and descriptions")
      val property = Prop.forAll(_source_cases) { sample =>
        Given("an actual generated Entity source under its project-relative path")
        val dir = _work_directory("source-property")
        val source = dir.resolve(sample.sourcepath)
        Files.createDirectories(source.getParent)
        Files.writeString(source, _entity_cml(sample.entityname, sample.description), StandardCharsets.UTF_8)
        val json = dir.resolve("model-metadata.json")
        val yaml = dir.resolve("model-metadata.yaml")

        When("Cozy generates and writes the actual source metadata and both formats are loaded")
        val metadata = CmlModelMetadata.fromCml(source, sample.sourcepath, "concept")
        CmlModelMetadata.write(source, json, yaml, sample.sourcepath, "concept")
        val parsedjson = Json.parse(Files.readString(json))
        val parsedyaml = _load_yaml(yaml)

        Then("each source projection and Entity description retains its generated semantic facts")
        metadata.source shouldBe CmlModelMetadata.Source(
          sample.sourcepath, "cozy-modeler", org.simplemodeling.cozy.BuildInfo.version
        )
        metadata.modelElements.map(_.kind) shouldBe Vector("entity")
        metadata.modelElements.map(_.name) shouldBe Vector(sample.entityname)
        metadata.modelElements.map(_.descriptive.description) shouldBe Vector(Some(sample.description))
        Seq(parsedjson, parsedyaml).foreach { document =>
          (document \ "source").as[JsObject] shouldBe _source_json(sample.sourcepath)
          val elements = (document \ "modelElements").as[Vector[JsValue]]
          elements.size shouldBe 1
          (elements.head \ "kind").as[String] shouldBe "entity"
          (elements.head \ "name").as[String] shouldBe sample.entityname
          (elements.head \ "termId").as[String] shouldBe s"concept:${sample.entityslug}"
          (elements.head \ "descriptive" \ "description").as[String] shouldBe sample.description
        }
        (parsedjson \ "source").as[JsObject] shouldBe (parsedyaml \ "source").as[JsObject]
        true
      }

      When("Cozy evaluates twenty generated relative-source cases")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("the bounded source-provenance property passes")
      checked.passed shouldBe true
      checked.succeeded shouldBe 20
    }

    "reject a missing required source through both routes and preserve prior sidecars" in {
      Given("a loaded Entity model and existing JSON and YAML whose required source was removed")
      val dir = _work_directory("missing-source")
      val sourcepath = "src/main/cozy/notice.cml"
      val source = dir.resolve(sourcepath)
      Files.createDirectories(source.getParent)
      Files.writeString(source, _entity_cml("Notice", "A catalog notice."), StandardCharsets.UTF_8)
      val model = KaleidoxModel.load(KaleidoxConfig.default, source.toFile)
      val json = dir.resolve("model-metadata.json")
      val yaml = dir.resolve("model-metadata.yaml")
      CmlModelMetadata.write(source, json, yaml, sourcepath, "concept")
      val priorjson = Files.readString(json)
      val prioryaml = Files.readString(yaml)
      Files.delete(source)

      When("both metadata routes and the writer require the missing actual source")
      val directfailure = scala.util.Try(CmlModelMetadata.fromCml(source, sourcepath, "concept"))
      val loadedfailure = scala.util.Try(CmlModelMetadata.fromKaleidox(model, source, sourcepath, "concept"))
      val writefailure = scala.util.Try(CmlModelMetadata.write(source, json, yaml, sourcepath, "concept"))
      val currentjson = Files.readString(json)
      val currentyaml = Files.readString(yaml)

      Then("all three requests report the concrete required-source failure")
      model.errors shouldBe Vector.empty
      Seq(directfailure, loadedfailure, writefailure).foreach { result =>
        result.isFailure shouldBe true
        val message = result.failed.get.getMessage
        message should include ("CML_MODEL_SOURCE_UNREADABLE")
        message should include (source.toAbsolutePath.normalize().toString)
        message should include ("provide a readable regular CML source file")
      }

      And("the actual prior JSON and YAML destination contents remain unchanged")
      currentjson shouldBe priorjson
      currentyaml shouldBe prioryaml
    }

    "normalize legacy operation inputs as Value field contracts" in {
      Given("a CML source with entity, command, query, and value attributes")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata-fields")
      val source = dir.resolve("src/main/cozy/sample.cml")
      Files.createDirectories(source.getParent)
      Files.writeString(
        source,
        """# ENTITY
          |
          |## Notice
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || id | entityid | 1 |
          || title | string | ? |
          |
          |# COMMAND
          |
          |## CreateNotice
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || title | string | 1 |
          |
          |# QUERY
          |
          |## GetNotice
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || id | string | 1 |
          |
          |# VALUE
          |
          |## NoticeResult
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || notice | Notice | ? |
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val json = dir.resolve("target/model-metadata.json")
      val yaml = dir.resolve("target/model-metadata.yaml")

      When("Cozy writes the canonical model metadata")
      CmlModelMetadata.write(source, json, yaml, "src/main/cozy/sample.cml", "concept")

      Then("legacy command and query sections use the canonical Value kind and retain their input roles")
      val elements = (Json.parse(Files.readString(json)) \ "modelElements").as[Seq[JsValue]]
      elements.map(x => (x \ "kind").as[String]) should contain allOf ("entity", "value")
      elements.map(x => (x \ "kind").as[String]) should contain noneOf ("command", "query")
      val command = elements.find(x => (x \ "name").as[String] == "CreateNotice").get
      val query = elements.find(x => (x \ "name").as[String] == "GetNotice").get
      (command \ "inputKind").as[String] shouldBe "command"
      (query \ "inputKind").as[String] shouldBe "query"

      And("every field type, multiplicity, and required state remains explicit")
      val entity = elements.find(x => (x \ "name").as[String] == "Notice").get
      val entityfields = (entity \ "fields").as[Seq[JsValue]]
      entityfields.map(x => (x \ "name").as[String]) shouldBe Seq("id", "title")
      (entityfields.head \ "type").as[String] shouldBe "entityid"
      (entityfields.head \ "required").as[Boolean] shouldBe true
      (entityfields(1) \ "multiplicity").as[String] shouldBe "?"
      (entityfields(1) \ "required").as[Boolean] shouldBe false
      Files.readString(yaml) should include ("type: \"entityid\"")
    }

    "emit equivalent model elements for compatibility and canonical operation Values" in {
      Given("legacy command/query inputs and canonical Values that describe the same contracts")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata-operation-value-equivalence")
      val legacy = dir.resolve("legacy.cml")
      val canonical = dir.resolve("canonical.cml")
      Files.writeString(legacy, _legacy_operation_values, StandardCharsets.UTF_8)
      Files.writeString(canonical, _canonical_operation_values, StandardCharsets.UTF_8)

      When("Cozy creates model metadata through the common CML AST")
      val legacymetadata = CmlModelMetadata.fromCml(legacy, "legacy.cml", "concept")
      val canonicalmetadata = CmlModelMetadata.fromCml(canonical, "canonical.cml", "concept")

      Then("source syntax does not change the normalized Value model")
      legacymetadata.modelElements shouldBe canonicalmetadata.modelElements
      canonicalmetadata.modelElements.map(_.inputkind) should contain allOf (Some("command"), Some("query"))

      And("a same-named Entity remains an Entity instead of being normalized as an input Value")
      canonicalmetadata.modelElements.count(x => x.name == "CreateNotice" && x.kind == "entity") shouldBe 1
      canonicalmetadata.modelElements.count(x => x.name == "CreateNotice" && x.kind == "value") shouldBe 1

      And("the structural input-kind property is not emitted as narrative prose")
      canonicalmetadata.modelElements.foreach(_.narrative shouldBe None)
    }

    "preserve nominal scalar identity underlying type and constraints from the CML AST" in {
      Given("a constrained plain DATATYPE declaration")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata-nominal-scalar")
      val source = dir.resolve("src/main/cozy/sample.cml")
      Files.createDirectories(source.getParent)
      Files.writeString(
        source,
        """# DATATYPE
          |
          |## LoginName
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity | min-length | max-length | pattern  |
          ||-------+--------+--------------+------------+------------+----------|
          || value | string | 1            | 5          | 32         | ^user.+$ |
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val json = dir.resolve("target/model-metadata.json")
      val yaml = dir.resolve("target/model-metadata.yaml")

      When("Cozy writes public model metadata")
      CmlModelMetadata.write(source, json, yaml, "src/main/cozy/sample.cml", "concept")

      Then("the AST-backed datatype contract identifies its nominal scalar representation")
      val element = (Json.parse(Files.readString(json)) \ "modelElements").as[Seq[JsValue]].head
      (element \ "kind").as[String] shouldBe "datatype"
      (element \ "name").as[String] shouldBe "LoginName"
      (element \ "representation").as[String] shouldBe "nominal-scalar"
      (element \ "underlyingType").as[String] shouldBe "string"
      (element \ "constraints").as[Seq[String]] should contain theSameElementsAs Seq(
        "min-length=5",
        "max-length=32",
        "pattern=^user.+$"
      )

      And("the scalar value field keeps the same underlying and constraint contract")
      val field = (element \ "fields").as[Seq[JsValue]].head
      (field \ "name").as[String] shouldBe "value"
      (field \ "type").as[String] shouldBe "string"
      (field \ "constraints").as[Seq[String]] should contain theSameElementsAs Seq(
        "min-length=5",
        "max-length=32",
        "pattern=^user.+$"
      )
      Files.readString(yaml) should include ("representation: \"nominal-scalar\"")
      Files.readString(yaml) should include ("underlyingType: \"string\"")
    }

    "preserve typed actors and use cases in the component surface" in {
      Given("a component with local and external actors plus a canonical main flow")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata-actor-use-case")
      val source = dir.resolve("src/main/cozy/sample.cml")
      Files.createDirectories(source.getParent)
      Files.writeString(
        source,
        """# COMPONENT
          |
          |## ArtScene
          |
          |### USE CASE
          |
          |#### personal_planning
          |
          |##### ID
          |
          |UC-ART-001
          |
          |##### PRIMARY ACTOR
          |
          |ExhibitionVisitor
          |
          |##### SUPPORTING ACTOR
          |
          |TextusUserNotification
          |
          |##### SUMMARY
          |
          |Plan one exhibition visit.
          |
          |##### GOAL
          |
          |Choose an exhibition intentionally.
          |
          |##### TRIGGER
          |
          |The visitor opens today's candidates.
          |
          |##### PRIORITY
          |
          |high
          |
          |##### STATUS
          |
          |approved
          |
          |##### MAIN FLOW
          |
          |###### choose_exhibition
          |
          |1. The visitor reviews candidates.
          |2. The visitor records a decision.
          |
          |# ACTOR
          |
          |## ExhibitionVisitor
          |
          |### KIND
          |
          |human
          |
          |### DESCRIPTION
          |
          |A person planning exhibition visits.
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("Cozy writes the canonical model sidecar")
      val metadata = CmlModelMetadata.fromCml(source, "src/main/cozy/sample.cml", "concept")

      Then("the component surface contains the typed local actor")
      val component = metadata.surface.component.getOrElse(fail("Component surface is missing"))
      component.actors shouldBe Vector(CmlModelMetadata.ActorSurface(
        "ExhibitionVisitor",
        Some("human"),
        None,
        Some("A person planning exhibition visits.")
      ))

      And("the use case preserves its contract, resolved actor references, and flow")
      val usecase = component.useCases.headOption.getOrElse(fail("UseCase surface is missing"))
      usecase.name shouldBe "personal_planning"
      usecase.id shouldBe Some("UC-ART-001")
      usecase.trigger shouldBe Some("The visitor opens today's candidates.")
      usecase.priority shouldBe Some("high")
      usecase.status shouldBe Some("approved")
      usecase.actorReferences should contain allOf (
        CmlModelMetadata.ActorReferenceSurface("ExhibitionVisitor", "primary", "actor"),
        CmlModelMetadata.ActorReferenceSurface("TextusUserNotification", "supporting", "external")
      )
      usecase.flows.map(_.kind) shouldBe Vector("main")
      usecase.flows.head.steps shouldBe Vector(
        "The visitor reviews candidates.",
        "The visitor records a decision."
      )

      And("the JSON and YAML sidecars expose the same additive contract")
      metadata.toJsonString should include ("\"targetKind\" : \"external\"")
      metadata.toYamlString should include ("useCases:")
      metadata.toYamlString should include ("id: \"UC-ART-001\"")
      val yaml = dir.resolve("target/model-metadata.yaml")
      Files.createDirectories(yaml.getParent)
      Files.writeString(yaml, metadata.toYamlString, StandardCharsets.UTF_8)
      val parsed = StructuredDocumentLoader.loadJson(InputSource(yaml.toFile)).take
      parsed.hcursor.downField("surface").downField("component").downField("useCases").downN(0).
        downField("id").as[String] shouldBe Right("UC-ART-001")
    }

    "extract operation summary and description from operation child sections" in {
      Given("a CML source file with operation SUMMARY and DESCRIPTION sections")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata-operation")
      val source = dir.resolve("src/main/cozy/sample.cml")
      Files.createDirectories(source.getParent)
      Files.writeString(
        source,
        """# COMPONENT
          |
          |## Sample
          |
          |### SUMMARY
          |
          |Sample component.
          |
          |# SERVICE
          |
          |## Knowledge
          |
          |### SUMMARY
          |
          |Knowledge service.
          |
          |### OPERATION
          |
          |#### ingestKnowledge
          |
          |##### SUMMARY
          |
          |Ingest a knowledge item.
          |
          |##### DESCRIPTION
          |
          |Ingests authored or imported knowledge content.
          |
          |##### TYPE
          |
          |COMMAND
          |
          |##### INPUT
          |
          |###### TYPE
          |
          |IngestKnowledge
          |
          |##### OUTPUT
          |
          |###### TYPE
          |
          |IngestKnowledgeResult
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val json = dir.resolve("target/model-metadata.json")
      val yaml = dir.resolve("target/model-metadata.yaml")

      When("Cozy writes model metadata")
      CmlModelMetadata.write(source, json, yaml, "src/main/cozy/sample.cml", "concept")

      Then("the operation descriptive fields are present in the surface metadata")
      val metadata = Files.readString(json)
      metadata should include ("\"name\" : \"ingestKnowledge\"")
      metadata should include ("\"summary\" : \"Ingest a knowledge item.\"")
      metadata should include ("\"description\" : \"Ingests authored or imported knowledge content.\"")
      metadata should include ("\"operationType\" : \"COMMAND\"")
      metadata should include ("\"inputType\" : \"IngestKnowledge\"")
      metadata should include ("\"outputType\" : \"IngestKnowledgeResult\"")
      Files.readString(yaml) should include ("summary: \"Ingest a knowledge item.\"")
    }

    "preserve direct operation properties in surface metadata" in {
      Given("a CML operation using direct type, input, and output properties")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata-direct-operation-properties")
      val source = dir.resolve("src/main/cozy/sample.cml")
      Files.createDirectories(source.getParent)
      Files.writeString(
        source,
        """# COMPONENT
          |
          |## Sample
          |
          |# SERVICE
          |
          |## Knowledge
          |
          |### OPERATION
          |
          |#### ingestKnowledge
          |
          |- type :: COMMAND
          |- input :: IngestKnowledge
          |- output :: IngestKnowledgeResult
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("Cozy projects the operation through CML model metadata")
      val metadata = CmlModelMetadata.fromCml(source, "src/main/cozy/sample.cml", "concept")
      val operation = metadata.surface.component.toVector.flatMap(_.services).flatMap(_.operations).head

      Then("the direct properties have the same canonical operation contract as section properties")
      operation.operationtype shouldBe Some("COMMAND")
      operation.inputtype shouldBe Some("IngestKnowledge")
      operation.outputtype shouldBe Some("IngestKnowledgeResult")
    }
  }

  private val _legacy_operation_values =
    """# ENTITY
      |
      |## CreateNotice
      |
      |### ATTRIBUTE
      |
      || name | type | multiplicity |
      ||------+------|--------------|
      || id | entityid | 1 |
      |
      |# COMMAND
      |
      |## CreateNotice
      |
      |### ATTRIBUTE
      |
      || name | type | multiplicity |
      ||------+------|--------------|
      || title | title | 1 |
      |
      |# QUERY
      |
      |## GetNotice
      |
      |### ATTRIBUTE
      |
      || name | type | multiplicity |
      ||------+------|--------------|
      || id | string | 1 |
      |""".stripMargin

  private val _canonical_operation_values =
    """# ENTITY
      |
      |## CreateNotice
      |
      |### ATTRIBUTE
      |
      || name | type | multiplicity |
      ||------+------|--------------|
      || id | entityid | 1 |
      |
      |# VALUE
      |
      |## CreateNotice
      |- input-kind :: COMMAND
      |
      |### ATTRIBUTE
      |
      || name | type | multiplicity |
      ||------+------|--------------|
      || title | title | 1 |
      |
      |## GetNotice
      |- input-kind :: QUERY
      |
      |### ATTRIBUTE
      |
      || name | type | multiplicity |
      ||------+------|--------------|
      || id | string | 1 |
      |""".stripMargin
}

private object CmlModelMetadataSpec {
  private final case class SourceCase(
    sourcepath: String,
    entityname: String,
    entityslug: String,
    description: String
  )

  private val _source_cases: Gen[SourceCase] = for {
    directory <- Gen.oneOf("catalog", "knowledge", "public")
    suffix <- Gen.chooseNum(1, 1000)
    entity <- Gen.oneOf(
      ("KnowledgeItem", "knowledge-item"),
      ("Notice", "notice"),
      ("CatalogEntry", "catalog-entry")
    )
    length <- Gen.chooseNum(8, 24)
    letters <- Gen.listOfN(length, Gen.alphaLowerChar)
  } yield SourceCase(
    s"src/main/cozy/${directory}/model-${suffix}.cml",
    entity._1,
    entity._2,
    s"Description ${letters.mkString}."
  )

  private def _work_directory(prefix: String): Path = {
    val root = Paths.get("target/model-metadata-spec/work")
    Files.createDirectories(root)
    Files.createTempDirectory(root, prefix)
  }

  private def _entity_cml(name: String, description: String): String =
    s"""# ENTITY
       |
       |## ${name}
       |
       |### DESCRIPTION
       |
       |${description}
       |
       |### ATTRIBUTE
       |
       || name | type | multiplicity |
       ||------+------|--------------|
       || id | entityid | 1 |
       |""".stripMargin

  private def _source_json(path: String): JsObject =
    Json.obj(
      "path" -> path,
      "compiler" -> "cozy-modeler",
      "cozyVersion" -> org.simplemodeling.cozy.BuildInfo.version
    )

  private def _load_yaml(path: Path): JsValue =
    Json.parse(StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take.noSpaces)
}
