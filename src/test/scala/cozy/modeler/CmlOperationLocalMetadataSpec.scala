package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import cozy.archive.{CozyCarAbiManifest, CozyComponentReleaseCoordinateCodec}
import cozy.lint.CozyCarAbiLint
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

/*
 * @since   Oct. 22, 2026
 * @version Oct. 22, 2026
 */
class CmlOperationLocalMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CmlModelMetadata.{Descriptive, Element, Field, OperationSurface}
  import CozyCarAbiLint.{AbiField, AbiOperation, AbiService, AbiType}

  "CML operation-local metadata" should {
    "preserve normalized local contracts in metadata and CAR ABI" which {
      "retain named query and Result identities, ordered schemas, constraints and descriptions" in {
        _with_work_directory { dir =>
          Given("a named local query and Result with required, optional and repeated fields")
          val contract = _component + """# SERVICE
            |## Catalog
            |### OPERATION
            |#### searchEntries
            |Searches the catalog in authored order.
            |##### SUMMARY
            |Find catalog entries.
            |##### DESCRIPTION
            |Return matching entries and continuation information.
            |##### TYPE
            |QUERY
            |##### INPUT
            |###### VALUE
            |CatalogQuery
            |###### ATTRIBUTE
            || name | type | multiplicity | min-length | max-length | pattern |
            ||------+------|--------------|------------|------------|---------|
            || term | string | 1 | 2 | 40 | a+ |
            || scope | string | ? | | | |
            || tags | string | * | | | |
            |##### OUTPUT
            |###### VALUE
            |CatalogResult
            |###### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || id | long | 1 |
            || cursor | string | ? |
            || titles | string | + |
            |""".stripMargin
          val expected = Vector(
            _value("CatalogQuery", "catalog-query", Some("query"), Vector(
              Field("term", "string", "1", Vector("min-length=2", "max-length=40", "pattern=a+")),
              Field("scope", "string", "?"), Field("tags", "string", "*")
            )),
            _value("CatalogResult", "catalog-result", None, Vector(
              Field("id", "long", "1"), Field("cursor", "string", "?"), Field("titles", "string", "+")
            ))
          )
          val operations = Vector("Catalog" -> Vector(
            _operation("searchEntries", "search-entries", "QUERY", "CatalogQuery", "CatalogResult").copy(
              descriptive = Descriptive("searchEntries", None, Some("Find catalog entries."), Some("Return matching entries and continuation information.")),
              narrative = Some("Searches the catalog in authored order.")
            )
          ))

          When("Cozy writes JSON and YAML from the actual CML and creates the CAR ABI from that JSON")
          val (metadata, documents, abi) = _project(dir, contract)

          Then("both metadata formats and the parsed ABI retain the complete ordered operation and field contracts")
          _expect_projection(metadata, documents, expected, operations)
          _expect_abi(abi, Vector("Catalog"), Vector(
            AbiOperation(Some("Catalog"), "searchEntries", "QUERY", Some("CatalogQuery"), Some("CatalogResult"), None)
          ), Vector(
            AbiType("CatalogQuery", "value", Vector(
              AbiField("term", "string", Some("1"), true), AbiField("scope", "string", Some("?"), false),
              AbiField("tags", "string", Some("*"), false)
            )),
            AbiType("CatalogResult", "value", Vector(
              AbiField("id", "long", Some("1"), true), AbiField("cursor", "string", Some("?"), false),
              AbiField("titles", "string", Some("+"), true)
            ))
          ))
        }
      }

      "retain anonymous command and Result canonical names" in {
        _with_work_directory { dir =>
          Given("a command whose input and output contain direct ATTRIBUTE schemas")
          val contract = _component + """# SERVICE
            |## Catalog
            |### OPERATION
            |#### createEntry
            |##### TYPE
            |COMMAND
            |##### INPUT
            |###### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || title | string | 1 |
            |##### OUTPUT
            |###### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || accepted | boolean | 1 |
            |""".stripMargin
          val expected = Vector(
            _value("CreateEntryCommand", "create-entry-command", Some("command"), Vector(Field("title", "string", "1"))),
            _value("CreateEntryResult", "create-entry-result", None, Vector(Field("accepted", "boolean", "1")))
          )

          When("Cozy projects the accepted anonymous Values and creates the CAR ABI")
          val (metadata, documents, abi) = _project(dir, contract)

          Then("canonical AST identities and the command input role reach both metadata formats and the ABI")
          _expect_projection(metadata, documents, expected, Vector("Catalog" -> Vector(
            _operation("createEntry", "create-entry", "COMMAND", "CreateEntryCommand", "CreateEntryResult")
          )))
          _expect_abi(abi, Vector("Catalog"), Vector(
            AbiOperation(Some("Catalog"), "createEntry", "COMMAND", Some("CreateEntryCommand"), Some("CreateEntryResult"), None)
          ), Vector(
            AbiType("CreateEntryCommand", "value", Vector(AbiField("title", "string", Some("1"), true))),
            AbiType("CreateEntryResult", "value", Vector(AbiField("accepted", "boolean", Some("1"), true)))
          ))
        }
      }

      "keep the same operation leaf distinct across services with referenced and inline types" in {
        _with_work_directory { dir =>
          Given("two services named Primary and Secondary each defining lookup with different type contracts")
          val contract = _component + """# VALUE
            |## SharedCommand
            |- input-kind :: COMMAND
            |### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || title | string | 1 |
            |## SharedResult
            |### EXTENDS
            |OperationResult
            |### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || total | int | 1 |
            |# SERVICE
            |## Primary
            |### OPERATION
            |#### lookup
            |##### SUMMARY
            |Read the primary catalog.
            |##### TYPE
            |QUERY
            |##### INPUT
            |###### VALUE
            |PrimaryQuery
            |###### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || term | string | ? |
            |##### OUTPUT
            |###### TYPE
            |SharedResult
            |## Secondary
            |### OPERATION
            |#### lookup
            |##### SUMMARY
            |Write the secondary catalog.
            |##### TYPE
            |COMMAND
            |##### INPUT
            |###### TYPE
            |SharedCommand
            |##### OUTPUT
            |###### VALUE
            |SecondaryResult
            |###### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || accepted | boolean | 1 |
            |""".stripMargin
          val expected = Vector(
            _value("SharedCommand", "shared-command", Some("command"), Vector(Field("title", "string", "1"))),
            _value("SharedResult", "shared-result", None, Vector(Field("total", "int", "1"))),
            _value("PrimaryQuery", "primary-query", Some("query"), Vector(Field("term", "string", "?"))),
            _value("SecondaryResult", "secondary-result", None, Vector(Field("accepted", "boolean", "1")))
          )
          val operations = Vector(
            "Primary" -> Vector(_operation("lookup", "lookup", "QUERY", "PrimaryQuery", "SharedResult").copy(
              descriptive = Descriptive("lookup", None, Some("Read the primary catalog."), Some("Read the primary catalog."))
            )),
            "Secondary" -> Vector(_operation("lookup", "lookup", "COMMAND", "SharedCommand", "SecondaryResult").copy(
              descriptive = Descriptive("lookup", None, Some("Write the secondary catalog."), Some("Write the secondary catalog."))
            ))
          )

          When("Cozy projects each operation through its owning service and packages the metadata")
          val (metadata, documents, abi) = _project(dir, contract)

          Then("service ownership prevents leaf-name collisions and top-level Values retain their existing metadata")
          _expect_projection(metadata, documents, expected, operations)
          _expect_abi(abi, Vector("Primary", "Secondary"), Vector(
            AbiOperation(Some("Primary"), "lookup", "QUERY", Some("PrimaryQuery"), Some("SharedResult"), None),
            AbiOperation(Some("Secondary"), "lookup", "COMMAND", Some("SharedCommand"), Some("SecondaryResult"), None)
          ), Vector(
            AbiType("SharedCommand", "value", Vector(AbiField("title", "string", Some("1"), true))),
            AbiType("SharedResult", "value", Vector(AbiField("total", "int", Some("1"), true))),
            AbiType("PrimaryQuery", "value", Vector(AbiField("term", "string", Some("?"), false))),
            AbiType("SecondaryResult", "value", Vector(AbiField("accepted", "boolean", Some("1"), true)))
          ))

          When("the same source is loaded and interpreted through the existing fromKaleidox compatibility route")
          val source = dir.resolve("source.cml")
          val model = KaleidoxModel.load(KaleidoxConfig.default, source.toFile)
          val loaded = CmlModelMetadata.fromKaleidox(model, source, "src/main/cozy/catalog.cml", "concept")
          Then("the compatibility route retains its absent surface and top-level-only elements")
          loaded.surface shouldBe CmlModelMetadata.Surface(None)
          loaded.modelElements.map(_.name) shouldBe Vector("SharedCommand", "SharedResult")
        }
      }

      "preserve nominal field names and optionality inherited from an existing attribute definition" in {
        _with_work_directory { dir =>
          Given("a local nominal field whose descriptive section augments its optional table definition")
          val contract = _component + """# DATATYPE
            |## LookupToken
            |### TYPE
            |string
            |# SERVICE
            |## Catalog
            |### OPERATION
            |#### filterEntries
            |##### TYPE
            |QUERY
            |##### INPUT
            |###### VALUE
            |FilterQuery
            |###### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || token | LookupToken | ? |
            |####### token
            |######## DESCRIPTION
            |Optional nominal catalog selector.
            |##### OUTPUT
            |###### VALUE
            |FilterResult
            |###### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || tokens | LookupToken | * |
            |""".stripMargin
          val expected = Vector(
            Element("datatype", "LookupToken", "concept:lookup-token", "glossary/concept/lookup-token.html",
              Descriptive("LookupToken", None, None, None), None,
              fields = Vector(Field("value", "string", "1")), representation = Some("nominal-scalar"), underlyingtype = Some("string")),
            _value("FilterQuery", "filter-query", Some("query"), Vector(Field("token", "LookupToken", "?"))),
            _value("FilterResult", "filter-result", None, Vector(Field("tokens", "LookupToken", "*")))
          )

          When("Cozy serializes the normalized schema slots and builds the CAR ABI")
          val (metadata, documents, abi) = _project(dir, contract)

          Then("the nominal raw type and inherited optionality remain exact instead of becoming primitive required fields")
          _expect_projection(metadata, documents, expected, Vector("Catalog" -> Vector(
            _operation("filterEntries", "filter-entries", "QUERY", "FilterQuery", "FilterResult")
          )))
          _expect_abi(abi, Vector("Catalog"), Vector(
            AbiOperation(Some("Catalog"), "filterEntries", "QUERY", Some("FilterQuery"), Some("FilterResult"), None)
          ), Vector(
            AbiType("LookupToken", "datatype", Vector(AbiField("value", "string", Some("1"), true))),
            AbiType("FilterQuery", "value", Vector(AbiField("token", "LookupToken", Some("?"), false))),
            AbiType("FilterResult", "value", Vector(AbiField("tokens", "LookupToken", Some("*"), false)))
          ))
        }
      }
    }

    "preserve generated local names and field contracts" in {
      Given("bounded valid operation and field names with scalar, optional and repeated multiplicities")
      val samples = for {
        suffix <- Gen.chooseNum(1, 10000)
        length <- Gen.chooseNum(3, 9)
        letters <- Gen.listOfN(length, Gen.alphaLowerChar)
        datatype <- Gen.oneOf("string", "int", "boolean")
        multiplicity <- Gen.oneOf("1", "?", "*", "+")
      } yield (suffix, letters.mkString, datatype, multiplicity)
      val property = Prop.forAll(samples) { case (suffix, fieldname, datatype, multiplicity) =>
        _with_work_directory { dir =>
          Given("an actual generated CML query with one named input field and one Result field")
          val operationname = s"find${suffix}"
          val inputname = s"Request${suffix}"
          val outputname = s"Response${suffix}"
          val contract = _component + s"""# SERVICE
            |## Catalog
            |### OPERATION
            |#### ${operationname}
            |##### TYPE
            |QUERY
            |##### INPUT
            |###### VALUE
            |${inputname}
            |###### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || ${fieldname} | ${datatype} | ${multiplicity} |
            |##### OUTPUT
            |###### VALUE
            |${outputname}
            |###### ATTRIBUTE
            || name | type | multiplicity |
            ||------+------|--------------|
            || total | int | 1 |
            |""".stripMargin

          When("Cozy writes the generated metadata and the existing ABI parser reads its packaged contract")
          val (metadata, documents, abi) = _project(dir, contract)

          Then("every generated type, field, multiplicity and role is retained in order")
          _expect_projection(metadata, documents, Vector(
            _value(inputname, s"request${suffix}", Some("query"), Vector(Field(fieldname, datatype, multiplicity))),
            _value(outputname, s"response${suffix}", None, Vector(Field("total", "int", "1")))
          ), Vector("Catalog" -> Vector(_operation(operationname, operationname, "QUERY", inputname, outputname))))
          _expect_abi(abi, Vector("Catalog"), Vector(
            AbiOperation(Some("Catalog"), operationname, "QUERY", Some(inputname), Some(outputname), None)
          ), Vector(
            AbiType(inputname, "value", Vector(AbiField(fieldname, datatype, Some(multiplicity), multiplicity == "1" || multiplicity == "+"))),
            AbiType(outputname, "value", Vector(AbiField("total", "int", Some("1"), true)))
          ))
          true
        }
      }

      When("twenty generated metadata contracts are interpreted")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("all twenty contracts succeed without discarded samples")
      checked.passed shouldBe true
      checked.succeeded shouldBe 20
      checked.discarded shouldBe 0
    }
  }

  private val _component = """# COMPONENT
    |## MetadataCatalog
    |### PACKAGE
    |example.catalog
    |""".stripMargin

  private def _with_work_directory[A](body: Path => A): A = {
    val root = Paths.get("target/cml-operation-local-metadata-spec/work")
    Files.createDirectories(root)
    val dir = Files.createTempDirectory(root, "contract-")
    try body(dir)
    finally {
      val paths = Files.walk(dir)
      try paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete(_))
      finally paths.close()
    }
  }

  private def _project(dir: Path, contract: String): (CmlModelMetadata.ModelMetadata, Vector[JsValue], CozyCarAbiLint.AbiManifest) = {
    val source = dir.resolve("source.cml")
    val json = dir.resolve("model-metadata.json")
    val yaml = dir.resolve("model-metadata.yaml")
    Files.writeString(source, contract, StandardCharsets.UTF_8)
    val metadata = CmlModelMetadata.fromCml(source, "src/main/cozy/catalog.cml", "concept")
    CmlModelMetadata.write(source, json, yaml, "src/main/cozy/catalog.cml", "concept")
    val documents = Vector(
      Json.parse(Files.readString(json, StandardCharsets.UTF_8)),
      Json.parse(StructuredDocumentLoader.loadJson(InputSource(yaml.toFile)).take.noSpaces)
    )
    val coordinate = CozyComponentReleaseCoordinateCodec.admit("org.example.textus", "MetadataCatalog", "0.0.1-SNAPSHOT", "local-metadata-spec")
    val manifest = CozyCarAbiManifest.create(Vector(json), coordinate)
    val abi = CozyCarAbiLint.parseManifest(manifest).fold(message => fail(message), identity)
    (metadata, documents, abi)
  }

  private def _value(name: String, slug: String, inputkind: Option[String], fields: Vector[Field]): Element =
    Element("value", name, s"concept:${slug}", s"glossary/concept/${slug}.html", Descriptive(name, None, None, None), None,
      inputkind = inputkind, fields = fields)

  private def _operation(name: String, slug: String, kind: String, inputtype: String, outputtype: String): OperationSurface =
    OperationSurface(name, s"concept:${slug}", s"glossary/concept/${slug}.html", Descriptive(name, None, None, None), None,
      Some(kind), Some(inputtype), Some(outputtype), Vector.empty)

  private def _expect_projection(
    metadata: CmlModelMetadata.ModelMetadata,
    documents: Vector[JsValue],
    elements: Vector[Element],
    operations: Vector[(String, Vector[OperationSurface])]
  ): Unit = {
    metadata.modelElements shouldBe elements
    metadata.surface.component.toVector.flatMap(_.services).map(service => service.name -> service.operations) shouldBe operations
    documents.foreach { document =>
      (document \ "schema").as[String] shouldBe "cozy.cml.model-metadata.v1"
      (document \ "source" \ "path").as[String] shouldBe "src/main/cozy/catalog.cml"
      (document \ "modelElements").as[Vector[JsValue]] shouldBe elements.map(_.toJson)
      (document \ "surface" \ "component" \ "services").as[Vector[JsValue]].map { service =>
        (service \ "name").as[String] -> (service \ "operations").as[Vector[JsValue]]
      } shouldBe operations.map { case (name, entries) => name -> entries.map(_.toJson) }
    }
  }

  private def _expect_abi(
    abi: CozyCarAbiLint.AbiManifest,
    services: Vector[String],
    operations: Vector[AbiOperation],
    types: Vector[AbiType]
  ): Unit = {
    abi.abi.exports.services shouldBe services.map(AbiService)
    abi.abi.exports.operations shouldBe operations
    abi.abi.exports.types shouldBe types
    abi.abi.exports.entities shouldBe Vector.empty
  }
}
