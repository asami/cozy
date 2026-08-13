package cozy

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.zip.ZipFile
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   May. 12, 2026
 *  version May. 16, 2026
 *  version Jun. 10, 2026
 *  version Jul. 15, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class PublicationCompilerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()

  private def _entry(root: Path, path: String): play.api.libs.json.JsValue =
    _entry_option(root, path).getOrElse(fail(s"missing publication bundle entry: $path"))

  private def _entry_option(root: Path, path: String): Option[play.api.libs.json.JsValue] =
    _bundles(root).flatMap { bundle =>
      (bundle \ "entries").asOpt[Vector[play.api.libs.json.JsObject]].getOrElse(Vector.empty).collectFirst {
        case entry if (entry \ "path").asOpt[String].contains(path) => (entry \ "metadata").as[play.api.libs.json.JsValue]
      }
    }.headOption

  private def _entry_exists(root: Path, path: String): Boolean =
    _entry_option(root, path).nonEmpty

  private def _bundle(root: Path, name: String): play.api.libs.json.JsValue =
    Json.parse(Files.readString(root.resolve(s"${name}.json"), StandardCharsets.UTF_8))

  private def _write_bundle(root: Path, name: String, entries: Vector[(String, String)]): Unit = {
    Files.createDirectories(root)
    val json = Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> name),
      "entries" -> entries.map {
        case (path, metadata) => Json.obj(
          "path" -> path,
          "key" -> path.stripPrefix("metadata/").replaceFirst("""\.[^.]+$""", ""),
          "metadata" -> Json.parse(metadata)
        )
      }
    )
    Files.writeString(root.resolve(s"${name}.json"), Json.prettyPrint(json) + "\n", StandardCharsets.UTF_8)
  }

  private def _bundles(root: Path): Vector[play.api.libs.json.JsValue] =
    if (!Files.isDirectory(root))
      Vector.empty
    else {
      val stream = Files.list(root)
      try {
        stream.iterator().asScala.toVector.filter(_.getFileName.toString.endsWith(".json")).flatMap { path =>
          val json = Json.parse(Files.readString(path, StandardCharsets.UTF_8))
          if ((json \ "type").asOpt[String].contains("publication-bundle"))
            Some(json)
          else
            None
        }
      } finally {
        stream.close()
      }
    }

  "Cozy publication compiler" should {
    "publish-project publication and registry operations" which {
    "publish-project generates deterministic BoK publication metadata from an sbt project" in {
      Given("an sbt project fixture with deterministic publication inputs")
    val project = _base.resolve("target/test-generated/publish-project/sample")
    val out1 = _base.resolve("target/test-generated/publish-project/out-1")
    val out2 = _base.resolve("target/test-generated/publish-project/out-2")
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
        |    scalaVersion := "3.3.8"
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

      When("publish-project compiles the project publication twice")
    Cozy.main(Array("publish-project", project.toString, "--save", out1.toString, "--kind", "sample-single", "--name", "sample-publication", "--title", "Sample Publication"))
    Cozy.main(Array("publish-project", project.toString, "--save", out2.toString, "--kind", "sample-single", "--name", "sample-publication", "--title", "Sample Publication"))

    val expected = Vector(
      "metadata/catalog/projects/sample-publication.json",
      "metadata/catalog/samples/sample-publication.json",
      "metadata/projects/sample-publication/metadata.json",
      "metadata/samples/sample-publication/metadata.json",
      "metadata/source-manifest/sample-publication.json"
    )
      Then("both publication bundles contain identical deterministic metadata")
    expected.foreach { rel =>
      withClue(s"missing output: $rel") {
        _entry_exists(out1, rel) shouldBe true
      }
      withClue(s"non-deterministic output: $rel") {
        _entry(out1, rel) shouldBe _entry(out2, rel)
      }
    }
    Files.exists(out1.resolve("repository/artifacts/sample-publication.json")) shouldBe false
    Files.exists(out1.resolve("repository/artifacts/sample-publication.yaml")) shouldBe false
    Files.exists(out1.resolve("maven/artifacts/sample-publication.json")) shouldBe false
    Files.exists(out1.resolve("download/artifacts/sample-publication.json")) shouldBe false
    Files.exists(out1.resolve("metadata/artifacts/repository/sample-publication.json")) shouldBe false
    Files.exists(out1.resolve("metadata/artifacts/maven/sample-publication.json")) shouldBe false
    Files.exists(out1.resolve("metadata/artifacts/download/sample-publication.json")) shouldBe false
    Files.isRegularFile(out1.resolve("sample-publication.json")) shouldBe true

    val bundlejson = _bundle(out1, "sample-publication")
    bundlejson shouldBe _bundle(out2, "sample-publication")
    (bundlejson \ "generatedAt").isEmpty shouldBe true
    (bundlejson \ "sourcePath").as[String].startsWith("/") shouldBe false
    val catalogjson = _entry(out1, "metadata/catalog/projects/sample-publication.json")
    (catalogjson \ "project" \ "metadata").as[String] shouldBe "metadata/projects/sample-publication/metadata"
    val projectjson = _entry(out1, "metadata/projects/sample-publication/metadata.json")
    (projectjson \ "project" \ "name").as[String] shouldBe "sample-publication"
    (projectjson \ "project" \ "title").as[String] shouldBe "Sample Publication"
    (projectjson \ "project" \ "kind").as[String] shouldBe "sample-single"
    (projectjson \ "project" \ "organization").as[String] shouldBe "org.example"
    (projectjson \ "project" \ "version").as[String] shouldBe "0.1.0"
    (projectjson \ "project" \ "scalaVersion").as[String] shouldBe "3.3.8"
    (projectjson \ "project" \ "sbtVersion").as[String] shouldBe "1.11.7"

    val manifestjson = _entry(out1, "metadata/source-manifest/sample-publication.json")
    val paths = (manifestjson \ "files").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    paths.contains("build.sbt") shouldBe true
    paths.contains("project/build.properties") shouldBe true
    paths.contains("src/main/scala/Main.scala") shouldBe true
    paths.exists(_.startsWith("target/")) shouldBe false
  }
    "publish-project rejects colliding sample slugs" in {
      Given("a multi-sample project fixture with colliding sample slugs")
    val project = _base.resolve("target/test-generated/publish-project/colliding-samples")
    val out = _base.resolve("target/test-generated/publish-project/colliding-samples-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project.resolve("samples/01 Hello"))
    Files.createDirectories(project.resolve("samples/01-hello"))
    Files.writeString(project.resolve("build.sbt"), "name := \"Samples\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01 Hello/build.sbt"), "name := \"Hello A\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/build.sbt"), "name := \"Hello B\"\n", StandardCharsets.UTF_8)

    val ex = intercept[Throwable] {
      When("publish-project validates the colliding sample slugs")
      Cozy.main(Array("publish-project", project.toString, "--save", out.toString, "--kind", "sample-multi"))
    }
      Then("the duplicate sample slug is reported")
    ex.getMessage.contains("Duplicate sample slug") shouldBe true
  }
    "publish-project requires project.yaml name to be URL-safe" in {
      Given("a project fixture whose configured publication name is invalid")
    val project = _base.resolve("target/test-generated/publish-project/invalid-project-name")
    val out = _base.resolve("target/test-generated/publish-project/invalid-project-name-out")
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
      When("publish-project validates the configured publication name")
      Cozy.main(Array("publish-project", project.toString, "--save", out.toString))
    }
      Then("the invalid publication name is reported")
    ex.getMessage.contains("Invalid publication name") shouldBe true
  }
    "publish-project does not auto-detect publication.yaml as public metadata" in {
      Given("a project fixture containing publication.yaml without project metadata")
    val project = _base.resolve("target/test-generated/publish-project/publication-yaml-ignored")
    val out = _base.resolve("target/test-generated/publish-project/publication-yaml-ignored-out")
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

      When("publish-project reads the project metadata sources")
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))

    val projectjson = _entry(out, "metadata/projects/fallback-name/metadata.json")
      Then("the publication ignores publication.yaml and uses build metadata")
    (projectjson \ "project" \ "name").as[String] shouldBe "fallback-name"
    (projectjson \ "project" \ "title").as[String] shouldBe "Fallback Name"
    _entry_exists(out, "metadata/projects/publication-file-name/metadata.json") shouldBe false
  }
    "publish-project rejects reserved publication path roots" in {
      Given("a project fixture with a reserved publication path")
    val project = _base.resolve("target/test-generated/publish-project/reserved-publication-path")
    val out = _base.resolve("target/test-generated/publish-project/reserved-publication-path-out")
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
      When("publish-project validates the reserved publication path")
      Cozy.main(Array("publish-project", project.toString, "--save", out.toString))
    }
      Then("the reserved publication path is rejected")
    ex.getMessage.contains("Reserved top-level path") shouldBe true

    Given("the same project with an allowed publication path")
    Files.writeString(
      project.resolve("project.yaml"),
      """project:
        |  name: reserved-path
        |  title: Reserved Path
        |  path: textus/tutorial/textus-tutorial
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
      When("publish-project writes the publication with the allowed path")
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))
    val projectjson = _entry(out, "metadata/projects/reserved-path/metadata.json")
      Then("the allowed publication path is recorded")
    (projectjson \ "publication" \ "path").as[String] shouldBe "textus/tutorial/textus-tutorial"
  }
    "publish-project auto-detects car projects and supports kind override" in {
      Given("a Cozy CAR project fixture eligible for kind detection")
    val project = _base.resolve("target/test-generated/publish-project/car")
    val out = _base.resolve("target/test-generated/publish-project/car-out")
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

      When("publish-project detects the CAR project kind")
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))
    val detected = _entry(out, "metadata/projects/car-publication/metadata.json")
      Then("the detected CAR kind and repository artifact metadata are reflected")
    (detected \ "project" \ "kind").as[String] shouldBe "car"
    val artifact = _entry(out, "metadata/artifacts/repository/car-publication.json")
    (artifact \ "artifact" \ "status").as[String] shouldBe "planned"
    (artifact \ "artifact" \ "files" \\ "warehousePath").map(_.as[String]).contains("repository/car/car-publication/0.1.0/car-publication-0.1.0.car") shouldBe true
    (artifact \ "artifact" \ "files" \\ "publicPath").map(_.as[String]).contains("repository/car/car-publication/0.1.0/car-publication-0.1.0.car") shouldBe true

    Given("the detected CAR project with an explicit sample-single kind override")
      When("publish-project applies the kind override")
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString, "--kind", "sample-single"))
    val overridden = _entry(out, "metadata/projects/car-publication/metadata.json")
      Then("the overridden kind is reflected in metadata")
    (overridden \ "project" \ "kind").as[String] shouldBe "sample-single"
  }
    "publish-project preserves Cozy/CNCF settings and BoK article relationships" in {
      Given("a Cozy CAR project with build settings and BoK article metadata")
    val project = _base.resolve("target/test-generated/publish-project/bok-relationships")
    val out = _base.resolve("target/test-generated/publish-project/bok-relationships-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project.resolve("src/main/car"))
    Files.writeString(
      project.resolve("build.sbt"),
      """import org.goldenport.cozy.CozyPlugin.autoImport._
        |
        |lazy val cncfVersion = "0.4.8-SNAPSHOT"
        |
        |lazy val root = project
        |  .in(file("."))
        |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
        |  .settings(
        |    name := "bok-linked-component",
        |    version := "0.1.0",
        |    cozyPackaging := "car",
        |    libraryDependencies += "org.goldenport" %% "goldenport-cncf" % cncfVersion
        |  )
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.writeString(
      project.resolve("project.yaml"),
      """project:
        |  name: bok-linked-component
        |  title: BoK Linked Component
        |  path: textus/components/bok-linked-component
        |publication:
        |  pages:
        |    - path: textus/components/bok-linked-component/reference
        |      title: Reference Page
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

      When("publish-project compiles the Cozy and BoK metadata")
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))

    val projectjson = _entry(out, "metadata/projects/bok-linked-component/metadata.json")
    val settings = (projectjson \ "project" \ "buildSettings").as[play.api.libs.json.JsObject]
      Then("the build settings and article relationships are preserved")
    (settings \ "cozyPlugin").as[Boolean] shouldBe true
    (settings \ "sbtCozyPlugin").as[Boolean] shouldBe true
    (settings \ "cozyPackaging").as[String] shouldBe "car"
    (settings \ "cncfDependency").as[Boolean] shouldBe true
    (settings \ "cncfVersion").as[String] shouldBe "0.4.8-SNAPSHOT"
    val articles = (projectjson \ "publication" \ "articles").as[Vector[play.api.libs.json.JsObject]]
    articles.map(x => (x \ "path").as[String]) shouldBe Vector(
      "textus/components/bok-linked-component",
      "textus/components/bok-linked-component/reference"
    )
    (articles.head \ "role").as[String] shouldBe "primary"
    (articles(1) \ "role").as[String] shouldBe "page"
    (articles(1) \ "title").as[String] shouldBe "Reference Page"
  }
    "publish-project uses conf/cozy and .cozy defaults and detects sample collections" in {
      Given("a configured sample collection with layered Cozy defaults")
    val project = _base.resolve("target/test-generated/publish-project/configured")
    _delete(project)
    Files.createDirectories(project.resolve("conf/cozy"))
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
        |  summary_i18n:
        |    ja: Textus と CNCF の基本を実行しながら学ぶサンプル集です。
        |publication:
        |  pages:
        |    - path: textus/tutorial
        |      title: Textus Tutorials
        |      summary: Tutorial collections for learning Textus.
        |      description: Choose a tutorial collection and follow its samples.
        |      title_i18n:
        |        ja: Textus チュートリアル
        |      summary_i18n:
        |        ja: Textus を学ぶためのチュートリアル一覧です。
        |    - path: textus/tutorial/textus-tutorial
        |      title: Textus Tutorial
        |      summary: Executable Textus and CNCF tutorial sample collection.
        |      description: Tutorial samples for CozyTextus users.
        |      summary_i18n:
        |        ja: Textus と CNCF の基本を実行しながら学ぶサンプル集です。
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
      project.resolve("conf/cozy/config.yaml"),
      """publication:
        |  output: target/shared-publication
        |  samples_dir: samples
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.writeString(
      project.resolve(".cozy/config.yaml"),
      """publication:
        |  output: target/custom-publication
        |  samples_dir: samples
        |  source_manifest:
        |    excludes:
        |      - ignored.d
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.createDirectories(project.resolve("ignored.d"))
    Files.writeString(project.resolve("ignored.d/secret.txt"), "ignored\n", StandardCharsets.UTF_8)

      When("publish-project applies layered defaults and builds the sample collection")
    Cozy.main(Array("publish-project", project.toString))

    val out = project.resolve("target/custom-publication")
    val catalogjson = _entry(out, "metadata/catalog/projects/textus-tutorial.json")
      Then("the layered metadata and sample references are preserved")
    (catalogjson \ "project" \ "metadata").as[String] shouldBe "metadata/projects/textus-tutorial/metadata"
    val catalogsamplejson = _entry(out, "metadata/catalog/samples/textus-tutorial.json")
    (catalogsamplejson \ "sample" \ "metadata").as[String] shouldBe "metadata/samples/textus-tutorial/metadata"
    (catalogsamplejson \ "sample" \ "download" \ "artifact").as[String] shouldBe "metadata/artifacts/download/textus-tutorial"
    (catalogsamplejson \ "sample" \ "download" \ "types").as[Vector[String]].contains("sample-collection-zip") shouldBe true
    val projectjson = _entry(out, "metadata/projects/textus-tutorial/metadata.json")
    (projectjson \ "project" \ "name").as[String] shouldBe "textus-tutorial"
    (projectjson \ "project" \ "title").as[String] shouldBe "Textus Tutorial"
    (projectjson \ "project" \ "summary").as[String] shouldBe "Tutorial sample collection."
    (projectjson \ "project" \ "description").as[String] shouldBe "Textus tutorial samples for Cozy publication."
    (projectjson \ "project" \ "summary_i18n" \ "ja").as[String] shouldBe "Textus と CNCF の基本を実行しながら学ぶサンプル集です。"
    (projectjson \ "publication" \ "path").as[String] shouldBe "textus/tutorial/textus-tutorial"
    (projectjson \ "project" \ "kind").as[String] shouldBe "sample-multi"

    val pagesjson = _entry(out, "metadata/publication-pages/textus-tutorial.json")
    val pagepaths = (pagesjson \ "pages").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    pagepaths.contains("textus/tutorial") shouldBe true
    pagepaths.contains("textus/tutorial/textus-tutorial") shouldBe true
    val tutorialcatalog = (pagesjson \ "pages").as[Vector[play.api.libs.json.JsObject]].find(x => (x \ "path").as[String] == "textus/tutorial").get
    (tutorialcatalog \ "summary_i18n" \ "ja").as[String] shouldBe "Textus を学ぶためのチュートリアル一覧です。"

    val manifestjson = _entry(out, "metadata/source-manifest/textus-tutorial.json")
    val paths = (manifestjson \ "files").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    paths.contains("samples/01-hello/build.sbt") shouldBe true
    paths.contains("samples/02-crud/build.sbt") shouldBe true
    paths.exists(_.startsWith("ignored.d/")) shouldBe false

    val collectionmetadata = _entry(out, "metadata/samples/textus-tutorial/metadata.json")
    (collectionmetadata \ "download" \ "artifact").as[String] shouldBe "metadata/artifacts/download/textus-tutorial"
    (collectionmetadata \ "download" \ "types").as[Vector[String]].contains("sample-collection-zip") shouldBe true
    val samplerefs = (collectionmetadata \ "samples").as[Vector[play.api.libs.json.JsObject]]
    samplerefs.map(x => (x \ "metadata").as[String]).contains("metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata") shouldBe true

    val samplemetadata = _entry(out, "metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata.json")
    (samplemetadata \ "sample" \ "name").as[String] shouldBe "01-hello"
    (samplemetadata \ "sample" \ "title").as[String] shouldBe "Hello Tutorial"
    (samplemetadata \ "sample" \ "summary").as[String] shouldBe "Hello tutorial summary."
    (samplemetadata \ "sample" \ "description").as[String] shouldBe "Hello tutorial description."
    (samplemetadata \ "sample" \ "version").as[String] shouldBe "0.1.0"
    (samplemetadata \ "sample" \ "download" \ "artifact").as[String] shouldBe "metadata/artifacts/download/textus-tutorial"
    (samplemetadata \ "sample" \ "download" \ "type").as[String] shouldBe "sample-zip"
    (samplemetadata \ "sample" \ "download" \ "expectedPath").isEmpty shouldBe true
    (samplemetadata \ "files").isEmpty shouldBe true
    val downloadartifact = _entry(out, "metadata/artifacts/download/textus-tutorial.json")
    (downloadartifact \ "artifact" \ "status").as[String] shouldBe "planned"
    (downloadartifact \ "project" \ "summary").as[String] shouldBe "Tutorial sample collection."
    (downloadartifact \ "artifact" \ "files" \\ "warehousePath").map(_.as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip") shouldBe true
    (downloadartifact \ "artifact" \ "files" \\ "publicPath").map(_.as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip") shouldBe true
    (downloadartifact \ "artifact" \ "files" \\ "warehousePath").map(_.as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip") shouldBe true
    (downloadartifact \ "artifact" \ "files" \\ "publicPath").map(_.as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip") shouldBe true
    _entry_exists(out, "metadata/samples/textus-tutorial/items/01-hello/0.1.0/files/build.sbt") shouldBe false
    _entry_exists(out, "metadata/samples/textus-tutorial/items/01-hello/0.1.0/files/README.md") shouldBe false
    val latest = _entry(out, "metadata/samples/textus-tutorial/items/01-hello/latest.json")
    (latest \ "latest" \ "version").as[String] shouldBe "0.1.0"
    (latest \ "latest" \ "metadata").as[String] shouldBe "metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata"
    Files.exists(out.resolve("metadata/catalog/samples/textus-tutorial/01-hello/0.1.0.json")) shouldBe false
    Files.exists(out.resolve("download/artifacts/textus-tutorial.json")) shouldBe false
    val registrybundle = _bundle(out, "textus-tutorial")
    val registryfiles = (registrybundle \ "entries").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    (registrybundle \ "publication" \ "name").as[String] shouldBe "textus-tutorial"
    (registrybundle \ "publication" \ "path").as[String] shouldBe "textus/tutorial/textus-tutorial"
    registryfiles.contains("metadata/publication-pages/textus-tutorial.json") shouldBe true
    registryfiles.contains("metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata.json") shouldBe true
    registryfiles.exists(_.contains("/files/")) shouldBe false
  }
    "publish-project replaces only files owned by the same publication" in {
      Given("an existing sample publication registry with replaceable entries")
    val project = _base.resolve("target/test-generated/publish-project/registry-replace")
    val out = _base.resolve("target/test-generated/publish-project/registry-replace-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project.resolve("samples/01-hello"))
    Files.createDirectories(project.resolve("samples/02-crud"))
    Files.writeString(project.resolve("build.sbt"), "name := \"Registry Replace\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      project.resolve("project.yaml"),
      """project:
        |  name: textus-tutorial
        |  title: Textus Tutorial
        |  kind: sample-multi
        |  path: textus/tutorial/textus-tutorial
        |publication:
        |  pages:
        |    - path: textus/tutorial
        |      title: Textus Tutorials
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.writeString(project.resolve("samples/01-hello/build.sbt"), "name := \"Hello\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/02-crud/build.sbt"), "name := \"CRUD\"\n", StandardCharsets.UTF_8)
      When("publish-project creates the initial publication registry")
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))
      Then("the initial publication entries are present")
    _entry_exists(out, "metadata/publication-pages/textus-tutorial.json") shouldBe true
    _entry_exists(out, "metadata/samples/textus-tutorial/items/02-crud/0.1.0/metadata.json") shouldBe true

    Given("the same publication after removing one sample and its page metadata")
    _delete(project.resolve("samples/02-crud"))
    Files.writeString(
      project.resolve("project.yaml"),
      """project:
        |  name: textus-tutorial
        |  title: Textus Tutorial
        |  kind: sample-multi
        |  path: textus/tutorial/textus-tutorial
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.createDirectories(out.resolve("metadata/projects/other"))
    Files.writeString(out.resolve("metadata/projects/other/metadata.json"), "{}\n", StandardCharsets.UTF_8)
      When("publish-project replaces the existing publication registry")
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))

      Then("owned entries are removed while unrelated entries remain")
    _entry_exists(out, "metadata/publication-pages/textus-tutorial.json") shouldBe false
    _entry_exists(out, "metadata/samples/textus-tutorial/items/02-crud/0.1.0/metadata.json") shouldBe false
    _entry_exists(out, "metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata.json") shouldBe true
    Files.isRegularFile(out.resolve("metadata/projects/other/metadata.json")) shouldBe true
    val manifest = _bundle(out, "textus-tutorial")
    val files = (manifest \ "entries").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    files.contains("metadata/publication-pages/textus-tutorial.json") shouldBe false
    files.contains("metadata/samples/textus-tutorial/items/02-crud/0.1.0/metadata.json") shouldBe false
  }
    "publish-project rejects paths owned by another publication bundle" in {
      Given("a project whose publication path collides with another bundle")
    val project = _base.resolve("target/test-generated/publish-project/registry-collision")
    val out = _base.resolve("target/test-generated/publish-project/registry-collision-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project)
    Files.createDirectories(out)
    Files.writeString(project.resolve("build.sbt"), "name := \"Textus Tutorial\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      out.resolve("other.json"),
      """{
        |  "schema": "cozy.publish-project.v1",
        |  "type": "publication-bundle",
        |  "publication": {
        |    "name": "other"
        |  },
        |  "entries": [
        |    {
        |      "path": "metadata/catalog/projects/textus-tutorial.json",
        |      "key": "catalog/projects/textus-tutorial",
        |      "metadata": {}
        |    }
        |  ]
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    val ex = intercept[Throwable] {
      When("publish-project checks the colliding publication registry")
      Cozy.main(Array("publish-project", project.toString, "--save", out.toString, "--name", "textus-tutorial"))
    }
      Then("the ownership collision is reported")
    ex.getMessage.contains("Publication registry path collision") shouldBe true
  }
    }
    "unpublish-project operations" which {
    "unpublish-project removes only the publication bundle" in {
      Given("a published bundle and an unrelated registry entry")
    val project = _base.resolve("target/test-generated/publish-project/unpublish")
    val out = _base.resolve("target/test-generated/publish-project/unpublish-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project)
    Files.writeString(project.resolve("build.sbt"), "name := \"Unpublish Sample\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
      When("publish-project creates the bundle for removal")
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString, "--name", "unpublish-sample"))

    Given("the publication bundle alongside an unrelated metadata entry")
    Files.createDirectories(out.resolve("metadata/projects/other"))
    Files.writeString(out.resolve("metadata/projects/other/metadata.json"), "{}\n", StandardCharsets.UTF_8)

      When("unpublish-project removes the requested bundle")
    Cozy.main(Array("unpublish-project", "--save", out.toString, "--name", "unpublish-sample"))

      Then("only the requested publication bundle is removed")
    Files.exists(out.resolve("unpublish-sample.json")) shouldBe false
    Files.isRegularFile(out.resolve("metadata/projects/other/metadata.json")) shouldBe true
  }
    }
    "publish-maven-repository operations" which {
    "publish-maven-repository generates Maven repository publication metadata without sbt project files" in {
      Given("a Maven repository fixture with release and snapshot artifacts")
    val root = _base.resolve("target/test-generated/publish-maven-repository")
    val repository = root.resolve("repository")
    val out = root.resolve("publication")
    _delete(root)
    val artifact = repository.resolve("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar")
    val sources = repository.resolve("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0-sources.jar")
    val snapshot = repository.resolve("maven/org/example/textus-tutorial_3/0.2.0-SNAPSHOT/textus-tutorial_3-0.2.0-SNAPSHOT.jar")
    Files.createDirectories(artifact.getParent)
    Files.writeString(artifact, "binary-010", StandardCharsets.UTF_8)
    Files.writeString(Paths.get(artifact.toString + ".sha1"), "abc123  textus-tutorial_3-0.1.0.jar\n", StandardCharsets.UTF_8)
    Files.writeString(Paths.get(artifact.toString + ".md5"), "def456\n", StandardCharsets.UTF_8)
    Files.writeString(sources, "sources-010", StandardCharsets.UTF_8)
    Files.createDirectories(snapshot.getParent)
    Files.writeString(snapshot, "binary-snapshot", StandardCharsets.UTF_8)

      When("publish-maven-repository indexes the Maven artifacts")
    Cozy.main(Array(
      "publish-maven-repository",
      repository.toString,
      "--save", out.toString,
      "--name", "maven-repository",
      "--title", "Maven Repository"
    ))

    val metadata = _entry(out, "metadata/projects/maven-repository/metadata.json")
      Then("the Maven metadata records release and snapshot artifacts")
    (metadata \ "project" \ "name").as[String] shouldBe "maven-repository"
    (metadata \ "project" \ "kind").as[String] shouldBe "maven-repository"
    ((metadata \ "project" \ "buildSettings" \ "cozyPlugin").as[Boolean]) shouldBe false
    ((metadata \ "project" \ "buildSettings" \ "cncfDependency").as[Boolean]) shouldBe false
    val mavenjson = _entry(out, "metadata/artifacts/maven/maven-repository.json")
    (mavenjson \ "artifact" \ "status").as[String] shouldBe "available"
    val coordinates = (mavenjson \ "artifact" \ "coordinates").as[Vector[play.api.libs.json.JsObject]]
    (coordinates.head \ "groupId").as[String] shouldBe "org.example"
    (coordinates.head \ "artifactId").as[String] shouldBe "textus-tutorial_3"
    (coordinates.head \ "latestRelease").as[String] shouldBe "0.1.0"
    val files = (mavenjson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    val paths = files.map(x => (x \ "warehousePath").as[String])
    paths.contains("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar") shouldBe true
    paths.contains("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0-sources.jar") shouldBe true
    paths.exists(_.contains("0.2.0-SNAPSHOT")) shouldBe true
    val mainjar = files.find(x => (x \ "name").as[String] == "textus-tutorial_3-0.1.0.jar").get
    (mainjar \ "publicPath").as[String] shouldBe "repository/maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar"
    (mainjar \ "sha1").as[String] shouldBe "abc123"
    (mainjar \ "md5").as[String] shouldBe "def456"
    (mainjar \ "sha256").as[String].nonEmpty shouldBe true
    val releasejson = _entry(out, "metadata/releases/maven-repository.json")
    (releasejson \ "release" \ "latest").as[String] shouldBe "0.1.0"
    val releaseversions = (releasejson \ "release" \ "versions").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "version").as[String])
    releaseversions.contains("0.1.0") shouldBe true
    releaseversions.contains("0.2.0-SNAPSHOT") shouldBe true
  }
    }
    "publish-project default-output operations" which {
    "publish-project defaults to target/publication" in {
      Given("a project fixture without an explicit publication output")
    val project = _base.resolve("target/test-generated/publish-project/default-output")
    _delete(project)
    Files.createDirectories(project)
    Files.writeString(project.resolve("build.sbt"), "name := \"Default Output\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)

      When("publish-project writes its default publication output")
    Cozy.main(Array("publish-project", project.toString, "--name", "default-output"))

      Then("the default publication bundle is written under target/publication")
    Files.isRegularFile(project.resolve("target/publication/default-output.json")) shouldBe true
    Files.exists(project.resolve("target/publish.d")) shouldBe false
  }
    }
    "distribute-samples operations" which {
    "distribute-samples writes versioned sample archives under warehouse download" in {
      Given("a sample collection fixture and a target warehouse")
    val root = _base.resolve("target/test-generated/distribute-samples")
    val project = root.resolve("project")
    val warehouse = root.resolve("warehouse")
    _delete(root)
    Files.createDirectories(project.resolve("samples/01-hello/target"))
    Files.createDirectories(project.resolve("samples/01-hello/script/.scala-build"))
    Files.createDirectories(project.resolve("samples/01-hello/car.d"))
    Files.createDirectories(project.resolve("samples/01-hello/component.d"))
    Files.createDirectories(project.resolve("samples/01-hello/component-repository.d"))
    Files.createDirectories(project.resolve("samples/01-hello/tmprepo"))
    Files.createDirectories(project.resolve(".cozy"))
    Files.createDirectories(project.resolve("repository.d"))
    Files.createDirectories(project.resolve("scripts"))
    Files.createDirectories(project.resolve("versions"))
    Files.createDirectories(project.resolve("target"))
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
    Files.writeString(project.resolve("scripts/run-all-samples.sh"), "#!/usr/bin/env bash\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("versions/cncf-version.conf"), "0.4.10\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve(".cozy/config.yaml"), "local: true\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("repository.d/ignored.txt"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("target/root-ignored.txt"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/target/ignored.txt"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/script/.scala-build/ignored.class"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/car.d/ignored.car"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/component.d/ignored.yaml"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/component-repository.d/ignored.jar"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/tmprepo/ignored.txt"), "ignored\n", StandardCharsets.UTF_8)

      When("distribute-samples writes the versioned archives")
    Cozy.main(Array(
      "distribute-samples",
      project.toString,
      "--warehouse", warehouse.toString,
      "--name", "textus-tutorial",
      "--version", "0.1.0"
    ))

    val zippath = warehouse.resolve("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip")
      Then("the sample archives contain only the expected project files")
    Files.isRegularFile(zippath) shouldBe true
    val collectionzippath = warehouse.resolve("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    Files.isRegularFile(collectionzippath) shouldBe true
    val collectionzip = new ZipFile(collectionzippath.toFile)
    try {
      val entries = collectionzip.entries().asScala.map(_.getName).toSet
      entries.contains("samples/01-hello/build.sbt") shouldBe true
      entries.contains("samples/01-hello/README.md") shouldBe true
      entries.contains("scripts/run-all-samples.sh") shouldBe true
      entries.contains("versions/cncf-version.conf") shouldBe true
      entries.exists(_.startsWith("target/")) shouldBe false
      entries.exists(_.startsWith(".cozy/")) shouldBe false
      entries.exists(_.startsWith("repository.d/")) shouldBe false
      entries.exists(_.startsWith("samples/01-hello/target/")) shouldBe false
      entries.exists(_.startsWith("samples/01-hello/script/.scala-build/")) shouldBe false
      entries.exists(_.startsWith("samples/01-hello/car.d/")) shouldBe false
      entries.exists(_.startsWith("samples/01-hello/component.d/")) shouldBe false
      entries.exists(_.startsWith("samples/01-hello/component-repository.d/")) shouldBe false
      entries.exists(_.startsWith("samples/01-hello/tmprepo/")) shouldBe false
    } finally {
      collectionzip.close()
    }
    val zip = new ZipFile(zippath.toFile)
    try {
      val entries = zip.entries().asScala.map(_.getName).toSet
      entries.contains("build.sbt") shouldBe true
      entries.contains("README.md") shouldBe true
      entries.exists(_.startsWith("target/")) shouldBe false
      entries.exists(_.startsWith("script/.scala-build/")) shouldBe false
      entries.exists(_.startsWith("car.d/")) shouldBe false
      entries.exists(_.startsWith("component.d/")) shouldBe false
      entries.exists(_.startsWith("component-repository.d/")) shouldBe false
      entries.exists(_.startsWith("tmprepo/")) shouldBe false
    } finally {
      zip.close()
    }
    val firstsha = _sha256(collectionzippath)
    Given("the generated sample archives before a repeated distribution")
      When("distribute-samples repeats the archive operation")
    Cozy.main(Array(
      "distribute-samples",
      project.toString,
      "--warehouse", warehouse.toString,
      "--name", "textus-tutorial",
      "--version", "0.1.0"
    ))
      Then("the repeated distribution preserves the archive bytes")
    _sha256(collectionzippath) shouldBe firstsha
  }
    "distribute-samples dry-run prints planned archives without writing files" in {
      Given("a sample collection fixture for a dry-run distribution")
    val root = _base.resolve("target/test-generated/distribute-samples-dry-run")
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
      When("distribute-samples renders the dry-run plan")
      Console.withOut(printer) {
        Cozy.main(Array(
          "distribute-samples",
          project.toString,
          "--warehouse", warehouse.toString,
          "--name", "textus-tutorial",
          "--version", "0.2.0-SNAPSHOT",
          "--dry-run"
        ))
      }
    } finally {
      printer.close()
    }

    val out = buffer.toString("UTF-8")
      Then("the dry-run plan is printed without creating warehouse files")
    out.contains("distribute-samples dry-run") shouldBe true
    out.contains("sample-collection-zip warehousePath=repository/download/textus/tutorial/textus-tutorial/0.2.0-SNAPSHOT/textus-tutorial-0.2.0-SNAPSHOT.zip") shouldBe true
    out.contains("sample-zip sample=01-hello warehousePath=repository/download/textus/tutorial/textus-tutorial/0.2.0-SNAPSHOT/01-hello/01-hello-0.2.0-SNAPSHOT.zip") shouldBe true
    Files.exists(warehouse) shouldBe false
  }
    "distribute-samples rejects colliding sample slugs" in {
      Given("a sample collection fixture with colliding sample slugs")
    val root = _base.resolve("target/test-generated/distribute-samples-colliding")
    val project = root.resolve("project")
    val warehouse = root.resolve("warehouse")
    _delete(root)
    Files.createDirectories(project.resolve("samples/01 Hello"))
    Files.createDirectories(project.resolve("samples/01-hello"))
    Files.writeString(project.resolve("build.sbt"), "name := \"Samples\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01 Hello/build.sbt"), "name := \"Hello A\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/build.sbt"), "name := \"Hello B\"\n", StandardCharsets.UTF_8)

    val ex = intercept[Throwable] {
      When("distribute-samples validates the colliding sample slugs")
      Cozy.main(Array(
        "distribute-samples",
        project.toString,
        "--warehouse", warehouse.toString,
        "--name", "textus-tutorial",
        "--version", "0.1.0"
      ))
    }
      Then("the duplicate sample slug is reported without writing an archive")
    ex.getMessage.contains("Duplicate sample slug") shouldBe true
    Files.exists(warehouse.resolve("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")) shouldBe false
  }
    }
    "index-warehouse operations" which {
    "index-warehouse checks publication registry artifact consistency without Maven metadata" in {
      Given("a warehouse and publication registry containing repository and download artifacts")
    val root = _base.resolve("target/test-generated/index-warehouse")
    val warehouse = root.resolve("warehouse")
    val out = root.resolve("publication")
    _delete(root)
    val artifact = warehouse.resolve("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar")
    val sources = warehouse.resolve("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0-sources.jar")
    val snapshot = warehouse.resolve("maven/org/example/textus-tutorial_3/0.2.0-SNAPSHOT/textus-tutorial_3-0.2.0-SNAPSHOT.jar")
    val unrelated = warehouse.resolve("maven/org/example/other_3/9.9.9/other_3-9.9.9.jar")
    val oldcar = warehouse.resolve("repository/car/textus-tutorial/0.0.9/textus-tutorial-0.0.9.car")
    val car = warehouse.resolve("repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car")
    val sar = warehouse.resolve("repository/sar/textus-tutorial/0.1.0/textus-tutorial-0.1.0.sar")
    val unrelatedcar = warehouse.resolve("repository/car/other-module/9.9.9/other-module-9.9.9.car")
    val collectionzip = warehouse.resolve("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    val samplezip = warehouse.resolve("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip")
    val legacycollectionzip = warehouse.resolve("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    val legacysamplezip = warehouse.resolve("download/samples/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip")
    val unrelatedzip = warehouse.resolve("download/samples/other-publication/01-hello/9.9.9/01-hello-9.9.9.zip")
    Files.createDirectories(artifact.getParent)
    Files.writeString(artifact, "binary-010", StandardCharsets.UTF_8)
    Files.writeString(Paths.get(artifact.toString + ".sha1"), "abc123  textus-tutorial_3-0.1.0.jar\n", StandardCharsets.UTF_8)
    Files.writeString(Paths.get(artifact.toString + ".md5"), "def456\n", StandardCharsets.UTF_8)
    Files.writeString(sources, "sources-010", StandardCharsets.UTF_8)
    Files.createDirectories(snapshot.getParent)
    Files.writeString(snapshot, "binary-snapshot", StandardCharsets.UTF_8)
    Files.createDirectories(unrelated.getParent)
    Files.writeString(unrelated, "unrelated", StandardCharsets.UTF_8)
    Files.createDirectories(oldcar.getParent)
    Files.createDirectories(car.getParent)
    Files.writeString(oldcar, "car-009", StandardCharsets.UTF_8)
    Files.writeString(car, "car-010", StandardCharsets.UTF_8)
    Files.createDirectories(sar.getParent)
    Files.writeString(sar, "sar-010", StandardCharsets.UTF_8)
    Files.createDirectories(unrelatedcar.getParent)
    Files.writeString(unrelatedcar, "other-car", StandardCharsets.UTF_8)
    Files.createDirectories(collectionzip.getParent)
    Files.writeString(collectionzip, "sample-collection-zip", StandardCharsets.UTF_8)
    Files.createDirectories(samplezip.getParent)
    Files.writeString(samplezip, "sample-zip", StandardCharsets.UTF_8)
    Files.createDirectories(legacycollectionzip.getParent)
    Files.writeString(legacycollectionzip, "legacy-sample-collection-zip", StandardCharsets.UTF_8)
    Files.createDirectories(legacysamplezip.getParent)
    Files.writeString(legacysamplezip, "legacy-sample-zip", StandardCharsets.UTF_8)
    Files.createDirectories(unrelatedzip.getParent)
    Files.writeString(unrelatedzip, "unrelated-sample-zip", StandardCharsets.UTF_8)
    _write_bundle(out, "textus-tutorial", Vector(
      "metadata/artifacts/repository/textus-tutorial.json" ->
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
      "metadata/artifacts/download/textus-tutorial.json" ->
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
	        |        "warehousePath": "repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip",
	        |        "publicPath": "repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip",
	        |        "type": "sample-collection-zip"
	        |      },
	        |      {
	        |        "warehousePath": "repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip",
	        |        "publicPath": "repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip",
	        |        "type": "sample-zip"
	        |      }
        |    ]
        |  }
        |}
        |""".stripMargin,
      "metadata/samples/textus-tutorial/metadata.json" ->
        """{
        |  "schema": "cozy.publish-project.v1",
        |  "type": "sample-metadata",
        |  "publication": {
        |    "path": "textus/tutorial/textus-tutorial"
        |  }
        |}
        |""".stripMargin
    ))

      When("index-warehouse reconciles the publication registry")
    Cozy.main(Array(
      "index-warehouse",
      warehouse.toString,
      "--save", out.toString,
      "--name", "textus-tutorial",
      "--title", "Textus Tutorial",
      "--maven-coordinates", "org.example:textus-tutorial_3",
      "--repository-artifacts", "car,sar"
    ))

      Then("the registry contains consistent repository and download artifacts")
    _entry_exists(out, "metadata/artifacts/maven/textus-tutorial.json") shouldBe false

    val repositoryjson = _entry(out, "metadata/artifacts/repository/textus-tutorial.json")
    val repositoryfiles = (repositoryjson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    (repositoryjson \ "artifact" \ "status").as[String] shouldBe "planned"
    repositoryfiles.map(x => (x \ "warehousePath").as[String]).contains("repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car") shouldBe true
    repositoryfiles.map(x => (x \ "publicPath").as[String]).contains("repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car") shouldBe true
    repositoryfiles.map(x => (x \ "warehousePath").as[String]).exists(_.contains("0.0.9")) shouldBe false

    val downloadjson = _entry(out, "metadata/artifacts/download/textus-tutorial.json")
    (downloadjson \ "artifact" \ "status").as[String] shouldBe "planned"
    (downloadjson \ "project" \ "summary").as[String] shouldBe "Tutorial sample collection."
    val downloadfiles = (downloadjson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    downloadfiles.map(x => (x \ "warehousePath").as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip") shouldBe true
    downloadfiles.map(x => (x \ "publicPath").as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip") shouldBe true
    downloadfiles.find(x => (x \ "warehousePath").as[String] == "repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip").exists(x => (x \ "type").as[String] == "sample-collection-zip") shouldBe true
    downloadfiles.map(x => (x \ "warehousePath").as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip") shouldBe true
    downloadfiles.map(x => (x \ "publicPath").as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip") shouldBe true
    downloadfiles.map(x => (x \ "warehousePath").as[String]).contains("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip") shouldBe false
    downloadfiles.map(x => (x \ "warehousePath").as[String]).exists(_.contains("other-publication")) shouldBe false

    val releasejson = _entry(out, "metadata/releases/textus-tutorial.json")
    (releasejson \ "release" \ "latest").as[String] shouldBe "0.1.0"
    val releaseversions = (releasejson \ "release" \ "versions").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "version").as[String])
    releaseversions.contains("0.1.0") shouldBe true
    releaseversions.contains("0.2.0-SNAPSHOT") shouldBe false
  }
    "index-warehouse keeps legacy download sample paths readable" in {
      Given("a warehouse containing legacy download sample paths")
    val root = _base.resolve("target/test-generated/index-warehouse-legacy-download")
    val warehouse = root.resolve("warehouse")
    val out = root.resolve("publication")
    _delete(root)
    val collectionzip = warehouse.resolve("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    val samplezip = warehouse.resolve("download/samples/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip")
    Files.createDirectories(collectionzip.getParent)
    Files.writeString(collectionzip, "legacy-sample-collection-zip", StandardCharsets.UTF_8)
    Files.createDirectories(samplezip.getParent)
    Files.writeString(samplezip, "legacy-sample-zip", StandardCharsets.UTF_8)
    _write_bundle(out, "textus-tutorial", Vector(
      "metadata/artifacts/download/textus-tutorial.json" ->
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
        |""".stripMargin
    ))

      When("index-warehouse reads the legacy download paths")
    Cozy.main(Array(
      "index-warehouse",
      warehouse.toString,
      "--save", out.toString,
      "--name", "textus-tutorial",
      "--title", "Textus Tutorial"
    ))

    val downloadjson = _entry(out, "metadata/artifacts/download/textus-tutorial.json")
    val downloadfiles = (downloadjson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
      Then("the legacy download paths remain readable")
    downloadfiles.map(x => (x \ "warehousePath").as[String]).contains("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip") shouldBe true
    downloadfiles.map(x => (x \ "warehousePath").as[String]).contains("download/samples/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip") shouldBe true
  }
    "index-warehouse reports canonical download metadata with legacy-only files" in {
      Given("legacy download files with registry entries expecting canonical paths")
    val root = _base.resolve("target/test-generated/index-warehouse-canonical-download-missing")
    val warehouse = root.resolve("warehouse")
    val out = root.resolve("publication")
    _delete(root)
    val legacycollectionzip = warehouse.resolve("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    val legacysamplezip = warehouse.resolve("download/samples/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip")
    Files.createDirectories(legacycollectionzip.getParent)
    Files.writeString(legacycollectionzip, "legacy-sample-collection-zip", StandardCharsets.UTF_8)
    Files.createDirectories(legacysamplezip.getParent)
    Files.writeString(legacysamplezip, "legacy-sample-zip", StandardCharsets.UTF_8)
    _write_bundle(out, "textus-tutorial", Vector(
      "metadata/artifacts/download/textus-tutorial.json" ->
        """{
        |  "schema": "cozy.publish-project.v1",
        |  "type": "download-artifact",
        |  "artifact": {
        |    "layer": "download",
        |    "status": "planned",
        |    "files": [
        |      { "warehousePath": "repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip" },
        |      { "warehousePath": "repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip" }
        |    ]
        |  }
        |}
        |""".stripMargin
    ))

    val ex = intercept[Throwable] {
      When("index-warehouse resolves the missing canonical download files")
      Cozy.main(Array(
        "index-warehouse",
        warehouse.toString,
        "--save", out.toString,
        "--name", "textus-tutorial",
        "--title", "Textus Tutorial"
      ))
    }
      Then("the missing canonical download artifact is reported")
    ex.getMessage.contains("Missing download artifact") shouldBe true
    ex.getMessage.contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip") shouldBe true
  }
    }
    "publish-project failure operations" which {
    "publish-project fails explicitly for missing project roots" in {
      Given("a missing project root and its publication output path")
    val missing = _base.resolve("target/test-generated/publish-project/missing")
    val out = _base.resolve("target/test-generated/publish-project/missing-out")
    _delete(missing)
    val ex = intercept[Throwable] {
      When("publish-project validates the missing project root")
      Cozy.main(Array("publish-project", missing.toString, "--save", out.toString))
    }
      Then("the missing project directory is reported")
    ex.getMessage.contains("Project directory does not exist") shouldBe true
  }
    }

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
