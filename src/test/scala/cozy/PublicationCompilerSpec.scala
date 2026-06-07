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
 *  version May. 16, 2026
 * @version Jun.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class PublicationCompilerSpec extends AnyFunSuite {
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

  test("publish-project generates deterministic BoK publication metadata from an sbt project") {
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

    Cozy.main(Array("publish-project", project.toString, "--save", out1.toString, "--kind", "sample-single", "--name", "sample-publication", "--title", "Sample Publication"))
    Cozy.main(Array("publish-project", project.toString, "--save", out2.toString, "--kind", "sample-single", "--name", "sample-publication", "--title", "Sample Publication"))

    val expected = Vector(
      "metadata/catalog/projects/sample-publication.json",
      "metadata/catalog/samples/sample-publication.json",
      "metadata/projects/sample-publication/metadata.json",
      "metadata/samples/sample-publication/metadata.json",
      "metadata/source-manifest/sample-publication.json"
    )
    expected.foreach { rel =>
      assert(_entry_exists(out1, rel), s"missing output: $rel")
      assert(_entry(out1, rel) == _entry(out2, rel), s"non-deterministic output: $rel")
    }
    assert(!Files.exists(out1.resolve("repository/artifacts/sample-publication.json")))
    assert(!Files.exists(out1.resolve("repository/artifacts/sample-publication.yaml")))
    assert(!Files.exists(out1.resolve("maven/artifacts/sample-publication.json")))
    assert(!Files.exists(out1.resolve("download/artifacts/sample-publication.json")))
    assert(!Files.exists(out1.resolve("metadata/artifacts/repository/sample-publication.json")))
    assert(!Files.exists(out1.resolve("metadata/artifacts/maven/sample-publication.json")))
    assert(!Files.exists(out1.resolve("metadata/artifacts/download/sample-publication.json")))
    assert(Files.isRegularFile(out1.resolve("sample-publication.json")))

    val bundlejson = _bundle(out1, "sample-publication")
    assert(bundlejson == _bundle(out2, "sample-publication"))
    assert((bundlejson \ "generatedAt").isEmpty)
    assert(!(bundlejson \ "sourcePath").as[String].startsWith("/"))
    val catalogjson = _entry(out1, "metadata/catalog/projects/sample-publication.json")
    assert((catalogjson \ "project" \ "metadata").as[String] == "metadata/projects/sample-publication/metadata")
    val projectjson = _entry(out1, "metadata/projects/sample-publication/metadata.json")
    assert((projectjson \ "project" \ "name").as[String] == "sample-publication")
    assert((projectjson \ "project" \ "title").as[String] == "Sample Publication")
    assert((projectjson \ "project" \ "kind").as[String] == "sample-single")
    assert((projectjson \ "project" \ "organization").as[String] == "org.example")
    assert((projectjson \ "project" \ "version").as[String] == "0.1.0")
    assert((projectjson \ "project" \ "scalaVersion").as[String] == "3.3.7")
    assert((projectjson \ "project" \ "sbtVersion").as[String] == "1.11.7")

    val manifestjson = _entry(out1, "metadata/source-manifest/sample-publication.json")
    val paths = (manifestjson \ "files").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    assert(paths.contains("build.sbt"))
    assert(paths.contains("project/build.properties"))
    assert(paths.contains("src/main/scala/Main.scala"))
    assert(!paths.exists(_.startsWith("target/")))
  }

  test("publish-project rejects colliding sample slugs") {
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
      Cozy.main(Array("publish-project", project.toString, "--save", out.toString, "--kind", "sample-multi"))
    }
    assert(ex.getMessage.contains("Duplicate sample slug"))
  }

  test("publish-project requires project.yaml name to be URL-safe") {
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
      Cozy.main(Array("publish-project", project.toString, "--save", out.toString))
    }
    assert(ex.getMessage.contains("Invalid publication name"))
  }

  test("publish-project does not auto-detect publication.yaml as public metadata") {
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

    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))

    val projectjson = _entry(out, "metadata/projects/fallback-name/metadata.json")
    assert((projectjson \ "project" \ "name").as[String] == "fallback-name")
    assert((projectjson \ "project" \ "title").as[String] == "Fallback Name")
    assert(!_entry_exists(out, "metadata/projects/publication-file-name/metadata.json"))
  }

  test("publish-project rejects reserved publication path roots") {
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
      Cozy.main(Array("publish-project", project.toString, "--save", out.toString))
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
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))
    val projectjson = _entry(out, "metadata/projects/reserved-path/metadata.json")
    assert((projectjson \ "publication" \ "path").as[String] == "textus/tutorial/textus-tutorial")
  }

  test("publish-project auto-detects car projects and supports kind override") {
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

    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))
    val detected = _entry(out, "metadata/projects/car-publication/metadata.json")
    assert((detected \ "project" \ "kind").as[String] == "car")
    val artifact = _entry(out, "metadata/artifacts/repository/car-publication.json")
    assert((artifact \ "artifact" \ "status").as[String] == "planned")
    assert((artifact \ "artifact" \ "files" \\ "warehousePath").map(_.as[String]).contains("repository/car/car-publication/0.1.0/car-publication-0.1.0.car"))
    assert((artifact \ "artifact" \ "files" \\ "publicPath").map(_.as[String]).contains("repository/car/car-publication/0.1.0/car-publication-0.1.0.car"))

    Cozy.main(Array("publish-project", project.toString, "--save", out.toString, "--kind", "sample-single"))
    val overridden = _entry(out, "metadata/projects/car-publication/metadata.json")
    assert((overridden \ "project" \ "kind").as[String] == "sample-single")
  }

  test("publish-project preserves Cozy/CNCF settings and BoK article relationships") {
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

    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))

    val projectjson = _entry(out, "metadata/projects/bok-linked-component/metadata.json")
    val settings = (projectjson \ "project" \ "buildSettings").as[play.api.libs.json.JsObject]
    assert((settings \ "cozyPlugin").as[Boolean])
    assert((settings \ "sbtCozyPlugin").as[Boolean])
    assert((settings \ "cozyPackaging").as[String] == "car")
    assert((settings \ "cncfDependency").as[Boolean])
    assert((settings \ "cncfVersion").as[String] == "0.4.8-SNAPSHOT")
    val articles = (projectjson \ "publication" \ "articles").as[Vector[play.api.libs.json.JsObject]]
    assert(articles.map(x => (x \ "path").as[String]) == Vector(
      "textus/components/bok-linked-component",
      "textus/components/bok-linked-component/reference"
    ))
    assert((articles.head \ "role").as[String] == "primary")
    assert((articles(1) \ "role").as[String] == "page")
    assert((articles(1) \ "title").as[String] == "Reference Page")
  }

  test("publish-project uses conf/cozy and .cozy defaults and detects sample collections") {
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

    Cozy.main(Array("publish-project", project.toString))

    val out = project.resolve("target/custom-publication")
    val catalogjson = _entry(out, "metadata/catalog/projects/textus-tutorial.json")
    assert((catalogjson \ "project" \ "metadata").as[String] == "metadata/projects/textus-tutorial/metadata")
    val catalogsamplejson = _entry(out, "metadata/catalog/samples/textus-tutorial.json")
    assert((catalogsamplejson \ "sample" \ "metadata").as[String] == "metadata/samples/textus-tutorial/metadata")
    assert((catalogsamplejson \ "sample" \ "download" \ "artifact").as[String] == "metadata/artifacts/download/textus-tutorial")
    assert((catalogsamplejson \ "sample" \ "download" \ "types").as[Vector[String]].contains("sample-collection-zip"))
    val projectjson = _entry(out, "metadata/projects/textus-tutorial/metadata.json")
    assert((projectjson \ "project" \ "name").as[String] == "textus-tutorial")
    assert((projectjson \ "project" \ "title").as[String] == "Textus Tutorial")
    assert((projectjson \ "project" \ "summary").as[String] == "Tutorial sample collection.")
    assert((projectjson \ "project" \ "description").as[String] == "Textus tutorial samples for Cozy publication.")
    assert((projectjson \ "project" \ "summary_i18n" \ "ja").as[String] == "Textus と CNCF の基本を実行しながら学ぶサンプル集です。")
    assert((projectjson \ "publication" \ "path").as[String] == "textus/tutorial/textus-tutorial")
    assert((projectjson \ "project" \ "kind").as[String] == "sample-multi")

    val pagesjson = _entry(out, "metadata/publication-pages/textus-tutorial.json")
    val pagepaths = (pagesjson \ "pages").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    assert(pagepaths.contains("textus/tutorial"))
    assert(pagepaths.contains("textus/tutorial/textus-tutorial"))
    val tutorialcatalog = (pagesjson \ "pages").as[Vector[play.api.libs.json.JsObject]].find(x => (x \ "path").as[String] == "textus/tutorial").get
    assert((tutorialcatalog \ "summary_i18n" \ "ja").as[String] == "Textus を学ぶためのチュートリアル一覧です。")

    val manifestjson = _entry(out, "metadata/source-manifest/textus-tutorial.json")
    val paths = (manifestjson \ "files").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    assert(paths.contains("samples/01-hello/build.sbt"))
    assert(paths.contains("samples/02-crud/build.sbt"))
    assert(!paths.exists(_.startsWith("ignored.d/")))

    val collectionmetadata = _entry(out, "metadata/samples/textus-tutorial/metadata.json")
    assert((collectionmetadata \ "download" \ "artifact").as[String] == "metadata/artifacts/download/textus-tutorial")
    assert((collectionmetadata \ "download" \ "types").as[Vector[String]].contains("sample-collection-zip"))
    val samplerefs = (collectionmetadata \ "samples").as[Vector[play.api.libs.json.JsObject]]
    assert(samplerefs.map(x => (x \ "metadata").as[String]).contains("metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata"))

    val samplemetadata = _entry(out, "metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata.json")
    assert((samplemetadata \ "sample" \ "name").as[String] == "01-hello")
    assert((samplemetadata \ "sample" \ "title").as[String] == "Hello Tutorial")
    assert((samplemetadata \ "sample" \ "summary").as[String] == "Hello tutorial summary.")
    assert((samplemetadata \ "sample" \ "description").as[String] == "Hello tutorial description.")
    assert((samplemetadata \ "sample" \ "version").as[String] == "0.1.0")
    assert((samplemetadata \ "sample" \ "download" \ "artifact").as[String] == "metadata/artifacts/download/textus-tutorial")
    assert((samplemetadata \ "sample" \ "download" \ "type").as[String] == "sample-zip")
    assert((samplemetadata \ "sample" \ "download" \ "expectedPath").isEmpty)
    assert((samplemetadata \ "files").isEmpty)
    val downloadartifact = _entry(out, "metadata/artifacts/download/textus-tutorial.json")
    assert((downloadartifact \ "artifact" \ "status").as[String] == "planned")
    assert((downloadartifact \ "project" \ "summary").as[String] == "Tutorial sample collection.")
    assert((downloadartifact \ "artifact" \ "files" \\ "warehousePath").map(_.as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
    assert((downloadartifact \ "artifact" \ "files" \\ "publicPath").map(_.as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
    assert((downloadartifact \ "artifact" \ "files" \\ "warehousePath").map(_.as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip"))
    assert((downloadartifact \ "artifact" \ "files" \\ "publicPath").map(_.as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip"))
    assert(!_entry_exists(out, "metadata/samples/textus-tutorial/items/01-hello/0.1.0/files/build.sbt"))
    assert(!_entry_exists(out, "metadata/samples/textus-tutorial/items/01-hello/0.1.0/files/README.md"))
    val latest = _entry(out, "metadata/samples/textus-tutorial/items/01-hello/latest.json")
    assert((latest \ "latest" \ "version").as[String] == "0.1.0")
    assert((latest \ "latest" \ "metadata").as[String] == "metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata")
    assert(!Files.exists(out.resolve("metadata/catalog/samples/textus-tutorial/01-hello/0.1.0.json")))
    assert(!Files.exists(out.resolve("download/artifacts/textus-tutorial.json")))
    val registrybundle = _bundle(out, "textus-tutorial")
    val registryfiles = (registrybundle \ "entries").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    assert((registrybundle \ "publication" \ "name").as[String] == "textus-tutorial")
    assert((registrybundle \ "publication" \ "path").as[String] == "textus/tutorial/textus-tutorial")
    assert(registryfiles.contains("metadata/publication-pages/textus-tutorial.json"))
    assert(registryfiles.contains("metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata.json"))
    assert(!registryfiles.exists(_.contains("/files/")))
  }

  test("publish-project replaces only files owned by the same publication") {
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
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))
    assert(_entry_exists(out, "metadata/publication-pages/textus-tutorial.json"))
    assert(_entry_exists(out, "metadata/samples/textus-tutorial/items/02-crud/0.1.0/metadata.json"))

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
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString))

    assert(!_entry_exists(out, "metadata/publication-pages/textus-tutorial.json"))
    assert(!_entry_exists(out, "metadata/samples/textus-tutorial/items/02-crud/0.1.0/metadata.json"))
    assert(_entry_exists(out, "metadata/samples/textus-tutorial/items/01-hello/0.1.0/metadata.json"))
    assert(Files.isRegularFile(out.resolve("metadata/projects/other/metadata.json")))
    val manifest = _bundle(out, "textus-tutorial")
    val files = (manifest \ "entries").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    assert(!files.contains("metadata/publication-pages/textus-tutorial.json"))
    assert(!files.contains("metadata/samples/textus-tutorial/items/02-crud/0.1.0/metadata.json"))
  }

  test("publish-project rejects paths owned by another publication bundle") {
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
      Cozy.main(Array("publish-project", project.toString, "--save", out.toString, "--name", "textus-tutorial"))
    }
    assert(ex.getMessage.contains("Publication registry path collision"))
  }

  test("unpublish-project removes only the publication bundle") {
    val project = _base.resolve("target/test-generated/publish-project/unpublish")
    val out = _base.resolve("target/test-generated/publish-project/unpublish-out")
    _delete(project)
    _delete(out)
    Files.createDirectories(project)
    Files.writeString(project.resolve("build.sbt"), "name := \"Unpublish Sample\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)
    Cozy.main(Array("publish-project", project.toString, "--save", out.toString, "--name", "unpublish-sample"))
    Files.createDirectories(out.resolve("metadata/projects/other"))
    Files.writeString(out.resolve("metadata/projects/other/metadata.json"), "{}\n", StandardCharsets.UTF_8)

    Cozy.main(Array("unpublish-project", "--save", out.toString, "--name", "unpublish-sample"))

    assert(!Files.exists(out.resolve("unpublish-sample.json")))
    assert(Files.isRegularFile(out.resolve("metadata/projects/other/metadata.json")))
  }

  test("publish-maven-repository generates Maven repository publication metadata without sbt project files") {
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

    Cozy.main(Array(
      "publish-maven-repository",
      repository.toString,
      "--save", out.toString,
      "--name", "maven-repository",
      "--title", "Maven Repository"
    ))

    val metadata = _entry(out, "metadata/projects/maven-repository/metadata.json")
    assert((metadata \ "project" \ "name").as[String] == "maven-repository")
    assert((metadata \ "project" \ "kind").as[String] == "maven-repository")
    assert(!((metadata \ "project" \ "buildSettings" \ "cozyPlugin").as[Boolean]))
    assert(!((metadata \ "project" \ "buildSettings" \ "cncfDependency").as[Boolean]))
    val mavenjson = _entry(out, "metadata/artifacts/maven/maven-repository.json")
    assert((mavenjson \ "artifact" \ "status").as[String] == "available")
    val coordinates = (mavenjson \ "artifact" \ "coordinates").as[Vector[play.api.libs.json.JsObject]]
    assert((coordinates.head \ "groupId").as[String] == "org.example")
    assert((coordinates.head \ "artifactId").as[String] == "textus-tutorial_3")
    assert((coordinates.head \ "latestRelease").as[String] == "0.1.0")
    val files = (mavenjson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    val paths = files.map(x => (x \ "warehousePath").as[String])
    assert(paths.contains("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar"))
    assert(paths.contains("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0-sources.jar"))
    assert(paths.exists(_.contains("0.2.0-SNAPSHOT")))
    val mainjar = files.find(x => (x \ "name").as[String] == "textus-tutorial_3-0.1.0.jar").get
    assert((mainjar \ "publicPath").as[String] == "repository/maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar")
    assert((mainjar \ "sha1").as[String] == "abc123")
    assert((mainjar \ "md5").as[String] == "def456")
    assert((mainjar \ "sha256").as[String].nonEmpty)
    val releasejson = _entry(out, "metadata/releases/maven-repository.json")
    assert((releasejson \ "release" \ "latest").as[String] == "0.1.0")
    val releaseversions = (releasejson \ "release" \ "versions").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "version").as[String])
    assert(releaseversions.contains("0.1.0"))
    assert(releaseversions.contains("0.2.0-SNAPSHOT"))
  }

  test("publish-project defaults to target/publication") {
    val project = _base.resolve("target/test-generated/publish-project/default-output")
    _delete(project)
    Files.createDirectories(project)
    Files.writeString(project.resolve("build.sbt"), "name := \"Default Output\"\nversion := \"0.1.0\"\n", StandardCharsets.UTF_8)

    Cozy.main(Array("publish-project", project.toString, "--name", "default-output"))

    assert(Files.isRegularFile(project.resolve("target/publication/default-output.json")))
    assert(!Files.exists(project.resolve("target/publish.d")))
  }

  test("distribute-samples writes versioned sample archives under warehouse download") {
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
    Files.writeString(project.resolve("samples/01-hello/script/.scala-build/ignored.class"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/car.d/ignored.car"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/component.d/ignored.yaml"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/component-repository.d/ignored.jar"), "ignored\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/tmprepo/ignored.txt"), "ignored\n", StandardCharsets.UTF_8)

    Cozy.main(Array(
      "distribute-samples",
      project.toString,
      "--warehouse", warehouse.toString,
      "--name", "textus-tutorial",
      "--version", "0.1.0"
    ))

    val zippath = warehouse.resolve("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip")
    assert(Files.isRegularFile(zippath))
    val collectionzippath = warehouse.resolve("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")
    assert(Files.isRegularFile(collectionzippath))
    val collectionzip = new ZipFile(collectionzippath.toFile)
    try {
      val entries = collectionzip.entries().asScala.map(_.getName).toSet
      assert(entries.contains("01-hello/build.sbt"))
      assert(entries.contains("01-hello/README.md"))
      assert(!entries.exists(_.startsWith("01-hello/target/")))
      assert(!entries.exists(_.startsWith("01-hello/script/.scala-build/")))
      assert(!entries.exists(_.startsWith("01-hello/car.d/")))
      assert(!entries.exists(_.startsWith("01-hello/component.d/")))
      assert(!entries.exists(_.startsWith("01-hello/component-repository.d/")))
      assert(!entries.exists(_.startsWith("01-hello/tmprepo/")))
    } finally {
      collectionzip.close()
    }
    val zip = new ZipFile(zippath.toFile)
    try {
      val entries = zip.entries().asScala.map(_.getName).toSet
      assert(entries.contains("build.sbt"))
      assert(entries.contains("README.md"))
      assert(!entries.exists(_.startsWith("target/")))
      assert(!entries.exists(_.startsWith("script/.scala-build/")))
      assert(!entries.exists(_.startsWith("car.d/")))
      assert(!entries.exists(_.startsWith("component.d/")))
      assert(!entries.exists(_.startsWith("component-repository.d/")))
      assert(!entries.exists(_.startsWith("tmprepo/")))
    } finally {
      zip.close()
    }
    val firstsha = _sha256(collectionzippath)
    Cozy.main(Array(
      "distribute-samples",
      project.toString,
      "--warehouse", warehouse.toString,
      "--name", "textus-tutorial",
      "--version", "0.1.0"
    ))
    assert(_sha256(collectionzippath) == firstsha)
  }

  test("distribute-samples dry-run prints planned archives without writing files") {
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
    assert(out.contains("distribute-samples dry-run"))
    assert(out.contains("sample-collection-zip warehousePath=repository/download/textus/tutorial/textus-tutorial/0.2.0-SNAPSHOT/textus-tutorial-0.2.0-SNAPSHOT.zip"))
    assert(out.contains("sample-zip sample=01-hello warehousePath=repository/download/textus/tutorial/textus-tutorial/0.2.0-SNAPSHOT/01-hello/01-hello-0.2.0-SNAPSHOT.zip"))
    assert(!Files.exists(warehouse))
  }

  test("distribute-samples rejects colliding sample slugs") {
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
      Cozy.main(Array(
        "distribute-samples",
        project.toString,
        "--warehouse", warehouse.toString,
        "--name", "textus-tutorial",
        "--version", "0.1.0"
      ))
    }
    assert(ex.getMessage.contains("Duplicate sample slug"))
    assert(!Files.exists(warehouse.resolve("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip")))
  }

  test("index-warehouse checks publication registry artifact consistency without Maven metadata") {
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

    Cozy.main(Array(
      "index-warehouse",
      warehouse.toString,
      "--save", out.toString,
      "--name", "textus-tutorial",
      "--title", "Textus Tutorial",
      "--maven-coordinates", "org.example:textus-tutorial_3",
      "--repository-artifacts", "car,sar"
    ))

    assert(!_entry_exists(out, "metadata/artifacts/maven/textus-tutorial.json"))

    val repositoryjson = _entry(out, "metadata/artifacts/repository/textus-tutorial.json")
    val repositoryfiles = (repositoryjson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    assert((repositoryjson \ "artifact" \ "status").as[String] == "planned")
    assert(repositoryfiles.map(x => (x \ "warehousePath").as[String]).contains("repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"))
    assert(repositoryfiles.map(x => (x \ "publicPath").as[String]).contains("repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"))
    assert(!repositoryfiles.map(x => (x \ "warehousePath").as[String]).exists(_.contains("0.0.9")))

    val downloadjson = _entry(out, "metadata/artifacts/download/textus-tutorial.json")
    assert((downloadjson \ "artifact" \ "status").as[String] == "planned")
    assert((downloadjson \ "project" \ "summary").as[String] == "Tutorial sample collection.")
    val downloadfiles = (downloadjson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    assert(downloadfiles.map(x => (x \ "warehousePath").as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
    assert(downloadfiles.map(x => (x \ "publicPath").as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
    assert(downloadfiles.find(x => (x \ "warehousePath").as[String] == "repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip").exists(x => (x \ "type").as[String] == "sample-collection-zip"))
    assert(downloadfiles.map(x => (x \ "warehousePath").as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip"))
    assert(downloadfiles.map(x => (x \ "publicPath").as[String]).contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/01-hello/01-hello-0.1.0.zip"))
    assert(!downloadfiles.map(x => (x \ "warehousePath").as[String]).contains("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
    assert(!downloadfiles.map(x => (x \ "warehousePath").as[String]).exists(_.contains("other-publication")))

    val releasejson = _entry(out, "metadata/releases/textus-tutorial.json")
    assert((releasejson \ "release" \ "latest").as[String] == "0.1.0")
    val releaseversions = (releasejson \ "release" \ "versions").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "version").as[String])
    assert(releaseversions.contains("0.1.0"))
    assert(!releaseversions.contains("0.2.0-SNAPSHOT"))
  }

  test("index-warehouse keeps legacy download sample paths readable") {
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

    Cozy.main(Array(
      "index-warehouse",
      warehouse.toString,
      "--save", out.toString,
      "--name", "textus-tutorial",
      "--title", "Textus Tutorial"
    ))

    val downloadjson = _entry(out, "metadata/artifacts/download/textus-tutorial.json")
    val downloadfiles = (downloadjson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    assert(downloadfiles.map(x => (x \ "warehousePath").as[String]).contains("download/samples/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
    assert(downloadfiles.map(x => (x \ "warehousePath").as[String]).contains("download/samples/textus-tutorial/01-hello/0.1.0/01-hello-0.1.0.zip"))
  }

  test("index-warehouse reports canonical download metadata with legacy-only files") {
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
      Cozy.main(Array(
        "index-warehouse",
        warehouse.toString,
        "--save", out.toString,
        "--name", "textus-tutorial",
        "--title", "Textus Tutorial"
      ))
    }
    assert(ex.getMessage.contains("Missing download artifact"))
    assert(ex.getMessage.contains("repository/download/textus/tutorial/textus-tutorial/0.1.0/textus-tutorial-0.1.0.zip"))
  }

  test("publish-project fails explicitly for missing project roots") {
    val missing = _base.resolve("target/test-generated/publish-project/missing")
    val out = _base.resolve("target/test-generated/publish-project/missing-out")
    _delete(missing)
    val ex = intercept[Throwable] {
      Cozy.main(Array("publish-project", missing.toString, "--save", out.toString))
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
