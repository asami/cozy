package cozy

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.zip.ZipFile
import scala.collection.JavaConverters._
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.Json

/*
 * @since   May. 12, 2026
 * @version May. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class PublicationCompilerSpec extends AnyFunSuite {
  private val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()

  test("publish-project generates deterministic BoK publication metadata from an sbt project") {
    val project = base.resolve("target/test-generated/publish-project/sample")
    val out1 = base.resolve("target/test-generated/publish-project/out-1")
    val out2 = base.resolve("target/test-generated/publish-project/out-2")
    _delete(project.getParent)
    Files.createDirectories(project.resolve("project"))
    Files.createDirectories(project.resolve("src/main/scala"))
    Files.writeString(
      project.resolve("build.sbt"),
      """ThisBuild / organization := "org.example"
        |ThisBuild / version := "0.1.0"
        |
        |lazy val root = project
        |  .in(file("."))
        |  .settings(
        |    name := "sample-publication",
        |    scalaVersion := "3.3.7"
        |  )
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.writeString(project.resolve("project/build.properties"), "sbt.version=1.11.7\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("src/main/scala/Main.scala"), "object Main\n", StandardCharsets.UTF_8)
    Files.createDirectories(project.resolve("target"))
    Files.writeString(project.resolve("target/ignored.txt"), "ignored\n", StandardCharsets.UTF_8)
    Files.createDirectories(out1.resolve("repository/artifacts"))
    Files.writeString(out1.resolve("repository/artifacts/sample-publication.json"), """{"artifact":{"status":"placeholder"}}""", StandardCharsets.UTF_8)
    Files.writeString(out1.resolve("repository/artifacts/sample-publication.yaml"), "artifact:\n  status: placeholder\n", StandardCharsets.UTF_8)

    Cozy.main(Array("publish-project", project.toString, s"--save=${out1}", "--kind=sample-single", "--name=sample-publication", "--title=Sample Publication"))
    Cozy.main(Array("publish-project", project.toString, s"--save=${out2}", "--kind=sample-single", "--name=sample-publication", "--title=Sample Publication"))

    val expected = Vector(
      "metadata/catalog/projects/sample-publication.yaml",
      "metadata/catalog/projects/sample-publication.json",
      "metadata/catalog/samples/sample-publication.yaml",
      "metadata/catalog/samples/sample-publication.json",
      "metadata/projects/sample-publication/metadata.yaml",
      "metadata/projects/sample-publication/metadata.json",
      "metadata/samples/sample-publication/metadata.yaml",
      "metadata/samples/sample-publication/metadata.json",
      "metadata/source-manifest/sample-publication.yaml",
      "metadata/source-manifest/sample-publication.json"
    )
    expected.foreach { rel =>
      assert(Files.isRegularFile(out1.resolve(rel)), s"missing output: $rel")
      assert(Files.readString(out1.resolve(rel)) == Files.readString(out2.resolve(rel)), s"non-deterministic output: $rel")
    }
    assert(!Files.exists(out1.resolve("repository/artifacts/sample-publication.json")))
    assert(!Files.exists(out1.resolve("repository/artifacts/sample-publication.yaml")))
    assert(!Files.exists(out1.resolve("maven/artifacts/sample-publication.json")))
    assert(!Files.exists(out1.resolve("download/artifacts/sample-publication.json")))
    assert(!Files.exists(out1.resolve("metadata/artifacts/repository/sample-publication.json")))
    assert(!Files.exists(out1.resolve("metadata/artifacts/maven/sample-publication.json")))
    assert(!Files.exists(out1.resolve("metadata/artifacts/download/sample-publication.json")))

    val catalogJson = Json.parse(Files.readString(out1.resolve("metadata/catalog/projects/sample-publication.json")))
    assert((catalogJson \ "project" \ "metadata").as[String] == "metadata/projects/sample-publication/metadata")
    val projectJson = Json.parse(Files.readString(out1.resolve("metadata/projects/sample-publication/metadata.json")))
    assert((projectJson \ "project" \ "name").as[String] == "sample-publication")
    assert((projectJson \ "project" \ "title").as[String] == "Sample Publication")
    assert((projectJson \ "project" \ "kind").as[String] == "sample-single")
    assert((projectJson \ "project" \ "organization").as[String] == "org.example")
    assert((projectJson \ "project" \ "version").as[String] == "0.1.0")
    assert((projectJson \ "project" \ "scalaVersion").as[String] == "3.3.7")
    assert((projectJson \ "project" \ "sbtVersion").as[String] == "1.11.7")

    val projectYaml = Files.readString(out1.resolve("metadata/projects/sample-publication/metadata.yaml"))
    assert(projectYaml.contains("""name: "sample-publication""""))
    assert(projectYaml.contains("""title: "Sample Publication""""))
    assert(projectYaml.contains("""kind: "sample-single""""))
    assert(!projectYaml.contains("base_directory:"))

    val manifestJson = Json.parse(Files.readString(out1.resolve("metadata/source-manifest/sample-publication.json")))
    val paths = (manifestJson \ "files").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    assert(paths.contains("build.sbt"))
    assert(paths.contains("project/build.properties"))
    assert(paths.contains("src/main/scala/Main.scala"))
    assert(!paths.exists(_.startsWith("target/")))
  }

  test("publish-project rejects colliding sample slugs") {
    val project = base.resolve("target/test-generated/publish-project/colliding-samples")
    val out = base.resolve("target/test-generated/publish-project/colliding-samples-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project.resolve("samples/01 Hello"))
    Files.createDirectories(project.resolve("samples/01-hello"))
    Files.writeString(project.resolve("build.sbt"), "name := \"Samples\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01 Hello/build.sbt"), "name := \"Hello A\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/build.sbt"), "name := \"Hello B\"\n", StandardCharsets.UTF_8)

    val ex = intercept[Throwable] {
      Cozy.main(Array("publish-project", project.toString, s"--save=${out}", "--kind=sample-multi"))
    }
    assert(ex.getMessage.contains("Duplicate sample slug"))
  }

  test("publish-project requires project.yaml name to be URL-safe") {
    val project = base.resolve("target/test-generated/publish-project/invalid-project-name")
    val out = base.resolve("target/test-generated/publish-project/invalid-project-name-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project)
    Files.writeString(project.resolve("build.sbt"), "name := \"Fallback Name\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      project.resolve("project.yaml"),
      """project:
        |  name: Invalid Name
        |  title: Invalid Name
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    val ex = intercept[Throwable] {
      Cozy.main(Array("publish-project", project.toString, s"--save=${out}"))
    }
    assert(ex.getMessage.contains("Invalid publication name"))
  }

  test("publish-project does not auto-detect publication.yaml as public metadata") {
    val project = base.resolve("target/test-generated/publish-project/publication-yaml-ignored")
    val out = base.resolve("target/test-generated/publish-project/publication-yaml-ignored-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project)
    Files.writeString(project.resolve("build.sbt"), "name := \"Fallback Name\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      project.resolve("publication.yaml"),
      """project:
        |  name: publication-file-name
        |  title: Publication File Title
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    Cozy.main(Array("publish-project", project.toString, s"--save=${out}"))

    val projectJson = Json.parse(Files.readString(out.resolve("metadata/projects/fallback-name/metadata.json")))
    assert((projectJson \ "project" \ "name").as[String] == "fallback-name")
    assert((projectJson \ "project" \ "title").as[String] == "Fallback Name")
    assert(!Files.exists(out.resolve("metadata/projects/publication-file-name/metadata.json")))
  }

  test("publish-project rejects reserved publication path roots") {
    val project = base.resolve("target/test-generated/publish-project/reserved-publication-path")
    val out = base.resolve("target/test-generated/publish-project/reserved-publication-path-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project)
    Files.writeString(project.resolve("build.sbt"), "name := \"Reserved Path\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      project.resolve("project.yaml"),
      """project:
        |  name: reserved-path
        |  title: Reserved Path
        |  path: metadata/foo
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    val ex = intercept[Throwable] {
      Cozy.main(Array("publish-project", project.toString, s"--save=${out}"))
    }
    assert(ex.getMessage.contains("Reserved top-level path"))

    Files.writeString(
      project.resolve("project.yaml"),
      """project:
        |  name: reserved-path
        |  title: Reserved Path
        |  path: textus/tutorial/textus-tutorial
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Cozy.main(Array("publish-project", project.toString, s"--save=${out}"))
    val projectJson = Json.parse(Files.readString(out.resolve("metadata/projects/reserved-path/metadata.json")))
    assert((projectJson \ "publication" \ "path").as[String] == "textus/tutorial/textus-tutorial")
  }

  test("publish-project auto-detects car projects and supports kind override") {
    val project = base.resolve("target/test-generated/publish-project/car")
    val out = base.resolve("target/test-generated/publish-project/car-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project.resolve("src/main/car"))
    Files.writeString(
      project.resolve("build.sbt"),
      """import org.goldenport.cozy.CozyPlugin.autoImport._
        |lazy val root = project
        |  .in(file("."))
        |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
        |  .settings(
        |    name := "car-publication",
        |    version := "0.1.0"
        |  )
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    Cozy.main(Array("publish-project", project.toString, s"--save=${out}"))
    val detected = Json.parse(Files.readString(out.resolve("metadata/projects/car-publication/metadata.json")))
    assert((detected \ "project" \ "kind").as[String] == "car")
    val artifact = Json.parse(Files.readString(out.resolve("metadata/artifacts/repository/car-publication.json")))
    assert((artifact \ "artifact" \ "status").as[String] == "planned")
    assert((artifact \ "artifact" \ "files" \\ "warehousePath").map(_.as[String]).contains("repository/car/car-publication/0.1.0/car-publication-0.1.0.car"))
    assert((artifact \ "artifact" \ "files" \\ "publicPath").map(_.as[String]).contains("repository/car/car-publication/0.1.0/car-publication-0.1.0.car"))

    Cozy.main(Array("publish-project", project.toString, s"--save=${out}", "--kind=sample-single"))
    val overridden = Json.parse(Files.readString(out.resolve("metadata/projects/car-publication/metadata.json")))
    assert((overridden \ "project" \ "kind").as[String] == "sample-single")
  }

  test("publish-project uses .cozy/config.yaml defaults and detects sample collections") {
    val project = base.resolve("target/test-generated/publish-project/configured")
    _delete(project)
    Files.createDirectories(project.resolve(".cozy"))
    Files.createDirectories(project.resolve("samples/01-hello"))
    Files.createDirectories(project.resolve("samples/02-crud"))
    Files.writeString(project.resolve("build.sbt"), "name := \"Configured Samples\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      project.resolve("project.yaml"),
      """project:
        |  name: textus-tutorial
        |  title: Textus Tutorial
        |  kind: sample-multi
        |  path: textus/tutorial/textus-tutorial
        |  summary: Tutorial sample collection.
        |  description: Textus tutorial samples for Cozy publication.
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.writeString(project.resolve("samples/01-hello/build.sbt"), "name := \"Hello\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      project.resolve("samples/01-hello/project.yaml"),
      """project:
        |  title: Hello Tutorial
        |  summary: Hello tutorial summary.
        |  description: Hello tutorial description.
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.writeString(project.resolve("samples/01-hello/README.md"), "# Hello\nREADME fallback should not override project.yaml.\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/02-crud/build.sbt"), "name := \"CRUD\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      project.resolve(".cozy/config.yaml"),
      """publication:
        |  output: target/custom-publish.d
        |  samples_dir: samples
        |  source_manifest:
        |    excludes:
        |      - ignored.d
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.createDirectories(project.resolve("ignored.d"))
    Files.writeString(project.resolve("ignored.d/secret.txt"), "ignored\n", StandardCharsets.UTF_8)

    Cozy.main(Array("publish-project", project.toString))

    val out = project.resolve("target/custom-publish.d")
    val catalogJson = Json.parse(Files.readString(out.resolve("metadata/catalog/projects/textus-tutorial.json")))
    assert((catalogJson \ "project" \ "metadata").as[String] == "metadata/projects/textus-tutorial/metadata")
    val catalogSampleJson = Json.parse(Files.readString(out.resolve("metadata/catalog/samples/textus-tutorial.json")))
    assert((catalogSampleJson \ "sample" \ "metadata").as[String] == "metadata/samples/textus-tutorial/metadata")
    assert((catalogSampleJson \ "sample" \ "download" \ "artifact").as[String] == "metadata/artifacts/download/textus-tutorial")
    assert((catalogSampleJson \ "sample" \ "download" \ "types").as[Vector[String]].contains("sample-collection-zip"))
    val projectJson = Json.parse(Files.readString(out.resolve("metadata/projects/textus-tutorial/metadata.json")))
    assert((projectJson \ "project" \ "name").as[String] == "textus-tutorial")
    assert((projectJson \ "project" \ "title").as[String] == "Textus Tutorial")
    assert((projectJson \ "project" \ "summary").as[String] == "Tutorial sample collection.")
    assert((projectJson \ "project" \ "description").as[String] == "Textus tutorial samples for Cozy publication.")
    assert((projectJson \ "publication" \ "path").as[String] == "textus/tutorial/textus-tutorial")
    assert((projectJson \ "project" \ "kind").as[String] == "sample-multi")

    val manifestJson = Json.parse(Files.readString(out.resolve("metadata/source-manifest/textus-tutorial.json")))
    val paths = (manifestJson \ "files").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    assert(paths.contains("samples/01-hello/build.sbt"))
    assert(paths.contains("samples/02-crud/build.sbt"))
    assert(!paths.exists(_.startsWith("ignored.d/")))

    val collectionMetadata = Json.parse(Files.readString(out.resolve("metadata/samples/textus-tutorial/metadata.json")))
    assert((collectionMetadata \ "download" \ "artifact").as[String] == "metadata/artifacts/download/textus-tutorial")
    assert((collectionMetadata \ "download" \ "types").as[Vector[String]].contains("sample-collection-zip"))
    val sampleRefs = (collectionMetadata \ "samples").as[Vector[play.api.libs.json.JsObject]]
    assert(sampleRefs.map(x => (x \ "metadata").as[String]).contains("metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata"))

    val sampleMetadata = Json.parse(Files.readString(out.resolve("metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata.json")))
    assert((sampleMetadata \ "sample" \ "name").as[String] == "01-hello")
    assert((sampleMetadata \ "sample" \ "title").as[String] == "Hello Tutorial")
    assert((sampleMetadata \ "sample" \ "summary").as[String] == "Hello tutorial summary.")
    assert((sampleMetadata \ "sample" \ "description").as[String] == "Hello tutorial description.")
    assert((sampleMetadata \ "sample" \ "version").as[String] == "0.1.0")
    assert((sampleMetadata \ "sample" \ "download" \ "artifact").as[String] == "metadata/artifacts/download/textus-tutorial")
    assert((sampleMetadata \ "sample" \ "download" \ "type").as[String] == "sample-zip")
    assert((sampleMetadata \ "sample" \ "download" \ "expectedPath").isEmpty)
    assert((sampleMetadata \ "files").isEmpty)
    val sampleMetadataYaml = Files.readString(out.resolve("metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata.yaml"))
    assert(sampleMetadataYaml.contains("""artifact: "metadata/artifacts/download/textus-tutorial""""))
    assert(!sampleMetadataYaml.contains("expected_path:"))
    val downloadArtifact = Json.parse(Files.readString(out.resolve("metadata/artifacts/download/textus-tutorial.json")))
    assert((downloadArtifact \ "artifact" \ "status").as[String] == "planned")
    assert((downloadArtifact \ "project" \ "summary").as[String] == "Tutorial sample collection.")
    assert((downloadArtifact \ "artifact" \ "files" \\ "warehousePath").map(_.as[String]).contains("download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
    assert((downloadArtifact \ "artifact" \ "files" \\ "publicPath").map(_.as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
    assert((downloadArtifact \ "artifact" \ "files" \\ "warehousePath").map(_.as[String]).contains("download/textus/tutorial/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip"))
    assert((downloadArtifact \ "artifact" \ "files" \\ "publicPath").map(_.as[String]).contains("repository/download/textus/tutorial/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip"))
    assert(Files.isRegularFile(out.resolve("metadata/samples/textus-tutorial/items/01-hello/0.1.0/files/build.sbt")))
    assert(Files.isRegularFile(out.resolve("metadata/samples/textus-tutorial/items/01-hello/0.1.0/files/README.md")))
    val latest = Json.parse(Files.readString(out.resolve("metadata/samples/textus-tutorial/items/01-hello/latest.json")))
    assert((latest \ "latest" \ "version").as[String] == "0.1.0")
    assert((latest \ "latest" \ "metadata").as[String] == "metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata")
    assert(!Files.exists(out.resolve("metadata/catalog/samples/textus-tutorial/01-hello/0.1.0.json")))
    assert(!Files.exists(out.resolve("download/artifacts/textus-tutorial.json")))
  }

  test("distribute-samples writes versioned sample archives under warehouse download") {
    val root = base.resolve("target/test-generated/distribute-samples")
    val project = root.resolve("project")
    val warehouse = root.resolve("warehouse")
    _delete(root)
    Files.createDirectories(project.resolve("samples/01-hello/target"))
    Files.writeString(project.resolve("build.sbt"), "name := \"Samples\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      project.resolve("project.yaml"),
      """project:
        |  path: textus/tutorial/textus-tutorial
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.writeString(project.resolve("samples/01-hello/build.sbt"), "name := \"Hello\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/README.md"), "# Hello\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/target/ignored.txt"), "ignored\n", StandardCharsets.UTF_8)

    Cozy.main(Array(
      "distribute-samples",
      project.toString,
      s"--warehouse=${warehouse}",
      "--name=textus-tutorial",
      "--version=0.1.0"
    ))

    val zipPath = warehouse.resolve("download/textus/tutorial/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip")
    assert(Files.isRegularFile(zipPath))
    val collectionZipPath = warehouse.resolve("download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    assert(Files.isRegularFile(collectionZipPath))
    val collectionZip = new ZipFile(collectionZipPath.toFile)
    try {
      val entries = collectionZip.entries().asScala.map(_.getName).toSet
      assert(entries.contains("01-hello/build.sbt"))
      assert(entries.contains("01-hello/README.md"))
      assert(!entries.exists(_.startsWith("01-hello/target/")))
    } finally {
      collectionZip.close()
    }
    val zip = new ZipFile(zipPath.toFile)
    try {
      val entries = zip.entries().asScala.map(_.getName).toSet
      assert(entries.contains("build.sbt"))
      assert(entries.contains("README.md"))
      assert(!entries.exists(_.startsWith("target/")))
    } finally {
      zip.close()
    }
    val firstSha = _sha256(collectionZipPath)
    Cozy.main(Array(
      "distribute-samples",
      project.toString,
      s"--warehouse=${warehouse}",
      "--name=textus-tutorial",
      "--version=0.1.0"
    ))
    assert(_sha256(collectionZipPath) == firstSha)
  }

  test("distribute-samples dry-run prints planned archives without writing files") {
    val root = base.resolve("target/test-generated/distribute-samples-dry-run")
    val project = root.resolve("project")
    val warehouse = root.resolve("warehouse")
    _delete(root)
    Files.createDirectories(project.resolve("samples/01-hello"))
    Files.writeString(project.resolve("build.sbt"), "name := \"Samples\"\nversion := \"0.2.0-SNAPSHOT\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      project.resolve("project.yaml"),
      """project:
        |  path: textus/tutorial/textus-tutorial
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.writeString(project.resolve("samples/01-hello/build.sbt"), "name := \"Hello\"\n", StandardCharsets.UTF_8)

    val buffer = new ByteArrayOutputStream()
    val printer = new PrintStream(buffer, true, "UTF-8")
    try {
      Console.withOut(printer) {
        Cozy.main(Array(
          "distribute-samples",
          project.toString,
          s"--warehouse=${warehouse}",
          "--name=textus-tutorial",
          "--version=0.2.0-SNAPSHOT",
          "--dry-run"
        ))
      }
    } finally {
      printer.close()
    }

    val out = buffer.toString("UTF-8")
    assert(out.contains("distribute-samples dry-run"))
    assert(out.contains("sample-collection-zip warehousePath=download/textus/tutorial/textus-tutorial/0.2.0-SNAPSHOT/textus-tutorial-0.2.0-SNAPSHOT.zip"))
    assert(out.contains("sample-zip sample=01-hello warehousePath=download/textus/tutorial/textus-tutorial/01-hello/0.2.0-SNAPSHOT/01-hello-0.2.0-SNAPSHOT.zip"))
    assert(!Files.exists(warehouse))
  }

  test("distribute-samples rejects colliding sample slugs") {
    val root = base.resolve("target/test-generated/distribute-samples-colliding")
    val project = root.resolve("project")
    val warehouse = root.resolve("warehouse")
    _delete(root)
    Files.createDirectories(project.resolve("samples/01 Hello"))
    Files.createDirectories(project.resolve("samples/01-hello"))
    Files.writeString(project.resolve("build.sbt"), "name := \"Samples\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01 Hello/build.sbt"), "name := \"Hello A\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/build.sbt"), "name := \"Hello B\"\n", StandardCharsets.UTF_8)

    val ex = intercept[Throwable] {
      Cozy.main(Array(
        "distribute-samples",
        project.toString,
        s"--warehouse=${warehouse}",
        "--name=textus-tutorial",
        "--version=0.1.0"
      ))
    }
    assert(ex.getMessage.contains("Duplicate sample slug"))
    assert(!Files.exists(warehouse.resolve("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")))
  }

  test("index-warehouse generates Maven metadata and checks publish.d artifact consistency") {
    val root = base.resolve("target/test-generated/index-warehouse")
    val warehouse = root.resolve("warehouse")
    val out = root.resolve("publish.d")
    _delete(root)
    val artifact = warehouse.resolve("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar")
    val sources = warehouse.resolve("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0-sources.jar")
    val snapshot = warehouse.resolve("maven/org/example/textus-tutorial_3/0.2.0-SNAPSHOT/textus-tutorial_3-0.2.0-SNAPSHOT.jar")
    val unrelated = warehouse.resolve("maven/org/example/other_3/9.9.9/other_3-9.9.9.jar")
    val oldCar = warehouse.resolve("repository/car/textus-tutorial/0.0.9/textus-tutorial-0.0.9.car")
    val car = warehouse.resolve("repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car")
    val sar = warehouse.resolve("repository/sar/textus-tutorial/0.1.0/textus-tutorial-0.1.0.sar")
    val unrelatedCar = warehouse.resolve("repository/car/other-module/9.9.9/other-module-9.9.9.car")
    val collectionZip = warehouse.resolve("download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    val sampleZip = warehouse.resolve("download/textus/tutorial/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip")
    val legacyCollectionZip = warehouse.resolve("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    val legacySampleZip = warehouse.resolve("download/samples/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip")
    val unrelatedZip = warehouse.resolve("download/samples/other-publication/01-hello/9.9.9/01-hello-9.9.9.zip")
    Files.createDirectories(artifact.getParent)
    Files.writeString(artifact, "binary-010", StandardCharsets.UTF_8)
    Files.writeString(Paths.get(artifact.toString + ".sha1"), "abc123  textus-tutorial_3-0.1.0.jar\n", StandardCharsets.UTF_8)
    Files.writeString(Paths.get(artifact.toString + ".md5"), "def456\n", StandardCharsets.UTF_8)
    Files.writeString(sources, "sources-010", StandardCharsets.UTF_8)
    Files.createDirectories(snapshot.getParent)
    Files.writeString(snapshot, "binary-snapshot", StandardCharsets.UTF_8)
    Files.createDirectories(unrelated.getParent)
    Files.writeString(unrelated, "unrelated", StandardCharsets.UTF_8)
    Files.createDirectories(oldCar.getParent)
    Files.createDirectories(car.getParent)
    Files.writeString(oldCar, "car-009", StandardCharsets.UTF_8)
    Files.writeString(car, "car-010", StandardCharsets.UTF_8)
    Files.createDirectories(sar.getParent)
    Files.writeString(sar, "sar-010", StandardCharsets.UTF_8)
    Files.createDirectories(unrelatedCar.getParent)
    Files.writeString(unrelatedCar, "other-car", StandardCharsets.UTF_8)
    Files.createDirectories(collectionZip.getParent)
    Files.writeString(collectionZip, "sample-collection-zip", StandardCharsets.UTF_8)
    Files.createDirectories(sampleZip.getParent)
    Files.writeString(sampleZip, "sample-zip", StandardCharsets.UTF_8)
    Files.createDirectories(legacyCollectionZip.getParent)
    Files.writeString(legacyCollectionZip, "legacy-sample-collection-zip", StandardCharsets.UTF_8)
    Files.createDirectories(legacySampleZip.getParent)
    Files.writeString(legacySampleZip, "legacy-sample-zip", StandardCharsets.UTF_8)
    Files.createDirectories(unrelatedZip.getParent)
    Files.writeString(unrelatedZip, "unrelated-sample-zip", StandardCharsets.UTF_8)
    Files.createDirectories(out.resolve("metadata/artifacts/repository"))
    Files.writeString(
      out.resolve("metadata/artifacts/repository/textus-tutorial.json"),
      """{
        |  "schema": "cozy.publish-project.v1",
        |  "type": "repository-artifact",
        |  "artifact": {
        |    "layer": "repository",
        |    "status": "planned",
        |    "files": [
        |      {
        |        "warehousePath": "repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car",
        |        "publicPath": "repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"
        |      },
        |      {
        |        "warehousePath": "repository/sar/textus-tutorial/0.1.0/textus-tutorial-0.1.0.sar",
        |        "publicPath": "repository/sar/textus-tutorial/0.1.0/textus-tutorial-0.1.0.sar"
        |      }
        |    ]
        |  }
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.createDirectories(out.resolve("metadata/artifacts/download"))
    Files.writeString(
      out.resolve("metadata/artifacts/download/textus-tutorial.json"),
      """{
        |  "schema": "cozy.publish-project.v1",
        |  "type": "download-artifact",
        |  "project": {
        |    "name": "textus-tutorial",
        |    "title": "Textus Tutorial",
        |    "summary": "Tutorial sample collection."
        |  },
        |  "artifact": {
        |    "layer": "download",
	        |    "status": "planned",
	        |    "files": [
	        |      {
	        |        "warehousePath": "download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip",
	        |        "publicPath": "repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip",
	        |        "type": "sample-collection-zip"
	        |      },
	        |      {
	        |        "warehousePath": "download/textus/tutorial/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip",
	        |        "publicPath": "repository/download/textus/tutorial/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip",
	        |        "type": "sample-zip"
	        |      }
        |    ]
        |  }
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.createDirectories(out.resolve("metadata/samples/textus-tutorial"))
    Files.writeString(
      out.resolve("metadata/samples/textus-tutorial/metadata.json"),
      """{
        |  "schema": "cozy.publish-project.v1",
        |  "type": "sample-metadata",
        |  "publication": {
        |    "path": "textus/tutorial/textus-tutorial"
        |  }
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    Cozy.main(Array(
      "index-warehouse",
      warehouse.toString,
      s"--save=${out}",
      "--name=textus-tutorial",
      "--title=Textus Tutorial",
      "--maven-coordinates=org.example:textus-tutorial_3",
      "--repository-artifacts=car,sar"
    ))

    val mavenJson = Json.parse(Files.readString(out.resolve("metadata/artifacts/maven/textus-tutorial.json")))
    assert((mavenJson \ "artifact" \ "status").as[String] == "available")
    val coordinates = (mavenJson \ "artifact" \ "coordinates").as[Vector[play.api.libs.json.JsObject]]
    assert((coordinates.head \ "groupId").as[String] == "org.example")
    assert((coordinates.head \ "artifactId").as[String] == "textus-tutorial_3")
    assert((coordinates.head \ "latestRelease").as[String] == "0.1.0")
    val files = (mavenJson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    val paths = files.map(x => (x \ "warehousePath").as[String])
    assert(paths.contains("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar"))
    assert(paths.contains("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0-sources.jar"))
    assert(paths.exists(_.contains("0.2.0-SNAPSHOT")))
    assert(!paths.exists(_.contains("other_3")))
    val mainJar = files.find(x => (x \ "name").as[String] == "textus-tutorial_3-0.1.0.jar").get
    assert((mainJar \ "publicPath").as[String] == "repository/maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar")
    assert((mainJar \ "sha1").as[String] == "abc123")
    assert((mainJar \ "md5").as[String] == "def456")
    assert((mainJar \ "sha256").as[String].nonEmpty)

    val repositoryJson = Json.parse(Files.readString(out.resolve("metadata/artifacts/repository/textus-tutorial.json")))
    val repositoryFiles = (repositoryJson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    assert((repositoryJson \ "artifact" \ "status").as[String] == "planned")
    assert(repositoryFiles.map(x => (x \ "warehousePath").as[String]).contains("repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"))
    assert(repositoryFiles.map(x => (x \ "publicPath").as[String]).contains("repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"))
    assert(!repositoryFiles.map(x => (x \ "warehousePath").as[String]).exists(_.contains("0.0.9")))

    val downloadJson = Json.parse(Files.readString(out.resolve("metadata/artifacts/download/textus-tutorial.json")))
	    assert((downloadJson \ "artifact" \ "status").as[String] == "planned")
	    assert((downloadJson \ "project" \ "summary").as[String] == "Tutorial sample collection.")
	    val downloadFiles = (downloadJson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
	    assert(downloadFiles.map(x => (x \ "warehousePath").as[String]).contains("download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
	    assert(downloadFiles.map(x => (x \ "publicPath").as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
	    assert(downloadFiles.find(x => (x \ "warehousePath").as[String] == "download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip").exists(x => (x \ "type").as[String] == "sample-collection-zip"))
	    assert(downloadFiles.map(x => (x \ "warehousePath").as[String]).contains("download/textus/tutorial/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip"))
	    assert(downloadFiles.map(x => (x \ "publicPath").as[String]).contains("repository/download/textus/tutorial/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip"))
    assert(!downloadFiles.map(x => (x \ "warehousePath").as[String]).contains("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
    assert(!downloadFiles.map(x => (x \ "warehousePath").as[String]).exists(_.contains("other-publication")))

    val releaseJson = Json.parse(Files.readString(out.resolve("metadata/releases/textus-tutorial.json")))
    assert((releaseJson \ "release" \ "latest").as[String] == "0.1.0")
    val releaseVersions = (releaseJson \ "release" \ "versions").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "version").as[String])
    assert(releaseVersions.contains("0.1.0"))
    assert(releaseVersions.contains("0.2.0-SNAPSHOT"))
  }

  test("index-warehouse keeps legacy download sample paths readable") {
    val root = base.resolve("target/test-generated/index-warehouse-legacy-download")
    val warehouse = root.resolve("warehouse")
    val out = root.resolve("publish.d")
    _delete(root)
    val collectionZip = warehouse.resolve("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    val sampleZip = warehouse.resolve("download/samples/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip")
    Files.createDirectories(collectionZip.getParent)
    Files.writeString(collectionZip, "legacy-sample-collection-zip", StandardCharsets.UTF_8)
    Files.createDirectories(sampleZip.getParent)
    Files.writeString(sampleZip, "legacy-sample-zip", StandardCharsets.UTF_8)
    Files.createDirectories(out.resolve("metadata/artifacts/download"))
    Files.writeString(
      out.resolve("metadata/artifacts/download/textus-tutorial.json"),
      """{
        |  "schema": "cozy.publish-project.v1",
        |  "type": "download-artifact",
        |  "artifact": {
        |    "layer": "download",
        |    "status": "planned",
        |    "files": [
        |      { "warehousePath": "download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip" },
        |      { "warehousePath": "download/samples/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip" }
        |    ]
        |  }
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    Cozy.main(Array(
      "index-warehouse",
      warehouse.toString,
      s"--save=${out}",
      "--name=textus-tutorial",
      "--title=Textus Tutorial"
    ))

    val downloadJson = Json.parse(Files.readString(out.resolve("metadata/artifacts/download/textus-tutorial.json")))
    val downloadFiles = (downloadJson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    assert(downloadFiles.map(x => (x \ "warehousePath").as[String]).contains("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
    assert(downloadFiles.map(x => (x \ "warehousePath").as[String]).contains("download/samples/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip"))
  }

  test("index-warehouse reports canonical download metadata with legacy-only files") {
    val root = base.resolve("target/test-generated/index-warehouse-canonical-download-missing")
    val warehouse = root.resolve("warehouse")
    val out = root.resolve("publish.d")
    _delete(root)
    val legacyCollectionZip = warehouse.resolve("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    val legacySampleZip = warehouse.resolve("download/samples/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip")
    Files.createDirectories(legacyCollectionZip.getParent)
    Files.writeString(legacyCollectionZip, "legacy-sample-collection-zip", StandardCharsets.UTF_8)
    Files.createDirectories(legacySampleZip.getParent)
    Files.writeString(legacySampleZip, "legacy-sample-zip", StandardCharsets.UTF_8)
    Files.createDirectories(out.resolve("metadata/artifacts/download"))
    Files.writeString(
      out.resolve("metadata/artifacts/download/textus-tutorial.json"),
      """{
        |  "schema": "cozy.publish-project.v1",
        |  "type": "download-artifact",
        |  "artifact": {
        |    "layer": "download",
        |    "status": "planned",
        |    "files": [
        |      { "warehousePath": "download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip" },
        |      { "warehousePath": "download/textus/tutorial/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip" }
        |    ]
        |  }
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    val ex = intercept[Throwable] {
      Cozy.main(Array(
        "index-warehouse",
        warehouse.toString,
        s"--save=${out}",
        "--name=textus-tutorial",
        "--title=Textus Tutorial"
      ))
    }
    assert(ex.getMessage.contains("Missing download artifact"))
    assert(ex.getMessage.contains("download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
  }

  test("publish-project fails explicitly for missing project roots") {
    val missing = base.resolve("target/test-generated/publish-project/missing")
    val out = base.resolve("target/test-generated/publish-project/missing-out")
    _delete(missing)
    val ex = intercept[Throwable] {
      Cozy.main(Array("publish-project", missing.toString, s"--save=${out}"))
    }
    assert(ex.getMessage.contains("Project directory does not exist"))
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }

  private def _sha256(path: Path): String = {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    val buffer = new Array[Byte](8192)
    try {
      Iterator.continually(in.read(buffer)).takeWhile(_ != -1).foreach { n =>
        md.update(buffer, 0, n)
      }
    } finally {
      in.close()
    }
    md.digest().map("%02x".format(_)).mkString
  }
}
