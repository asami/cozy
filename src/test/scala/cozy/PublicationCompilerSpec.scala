package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.funsuite.AnyFunSuite
import play.api.libs.json.Json

/*
 * @since   May. 12, 2026
 * @version May. 13, 2026
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

    Cozy.main(Array("publish-project", project.toString, s"--save=${out1}", "--kind=sample-single", "--name=sample-publication", "--title=Sample Publication"))
    Cozy.main(Array("publish-project", project.toString, s"--save=${out2}", "--kind=sample-single", "--name=sample-publication", "--title=Sample Publication"))

    val expected = Vector(
      "catalog/projects/sample-publication.yaml",
      "catalog/projects/sample-publication.json",
      "catalog/samples/sample-publication.yaml",
      "catalog/samples/sample-publication.json",
      "samples/sample-publication/metadata.yaml",
      "samples/sample-publication/metadata.json",
      "repository/artifacts/sample-publication.yaml",
      "repository/artifacts/sample-publication.json",
      "maven/artifacts/sample-publication.yaml",
      "maven/artifacts/sample-publication.json",
      "source-manifest/sample-publication.yaml",
      "source-manifest/sample-publication.json"
    )
    expected.foreach { rel =>
      assert(Files.isRegularFile(out1.resolve(rel)), s"missing output: $rel")
      assert(Files.readString(out1.resolve(rel)) == Files.readString(out2.resolve(rel)), s"non-deterministic output: $rel")
    }

    val projectJson = Json.parse(Files.readString(out1.resolve("catalog/projects/sample-publication.json")))
    assert((projectJson \ "project" \ "name").as[String] == "sample-publication")
    assert((projectJson \ "project" \ "title").as[String] == "Sample Publication")
    assert((projectJson \ "project" \ "kind").as[String] == "sample-single")
    assert((projectJson \ "project" \ "organization").as[String] == "org.example")
    assert((projectJson \ "project" \ "version").as[String] == "0.1.0")
    assert((projectJson \ "project" \ "scalaVersion").as[String] == "3.3.7")
    assert((projectJson \ "project" \ "sbtVersion").as[String] == "1.11.7")

    val projectYaml = Files.readString(out1.resolve("catalog/projects/sample-publication.yaml"))
    assert(projectYaml.contains("""name: "sample-publication""""))
    assert(projectYaml.contains("""title: "Sample Publication""""))
    assert(projectYaml.contains("""kind: "sample-single""""))
    assert(!projectYaml.contains("base_directory:"))

    val manifestJson = Json.parse(Files.readString(out1.resolve("source-manifest/sample-publication.json")))
    val paths = (manifestJson \ "files").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    assert(paths.contains("build.sbt"))
    assert(paths.contains("project/build.properties"))
    assert(paths.contains("src/main/scala/Main.scala"))
    assert(!paths.exists(_.startsWith("target/")))
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
    val detected = Json.parse(Files.readString(out.resolve("catalog/projects/car-publication.json")))
    assert((detected \ "project" \ "kind").as[String] == "car")

    Cozy.main(Array("publish-project", project.toString, s"--save=${out}", "--kind=sample-single"))
    val overridden = Json.parse(Files.readString(out.resolve("catalog/projects/car-publication.json")))
    assert((overridden \ "project" \ "kind").as[String] == "sample-single")
  }

  test("publish-project uses .cozy/config.yaml defaults and detects sample collections") {
    val project = base.resolve("target/test-generated/publish-project/configured")
    _delete(project)
    Files.createDirectories(project.resolve(".cozy"))
    Files.createDirectories(project.resolve("samples/01-hello"))
    Files.createDirectories(project.resolve("samples/02-crud"))
    Files.writeString(project.resolve("build.sbt"), "name := \"Configured Samples\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/01-hello/build.sbt"), "name := \"Hello\"\n", StandardCharsets.UTF_8)
    Files.writeString(project.resolve("samples/02-crud/build.sbt"), "name := \"CRUD\"\n", StandardCharsets.UTF_8)
    Files.writeString(
      project.resolve(".cozy/config.yaml"),
      """publication:
        |  name: textus-tutorial
        |  title: Textus Tutorial
        |  path: samples/textus/tutorial
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
    val projectJson = Json.parse(Files.readString(out.resolve("catalog/projects/textus-tutorial.json")))
    assert((projectJson \ "project" \ "name").as[String] == "textus-tutorial")
    assert((projectJson \ "project" \ "title").as[String] == "Textus Tutorial")
    assert((projectJson \ "publication" \ "path").as[String] == "samples/textus/tutorial")
    assert((projectJson \ "project" \ "kind").as[String] == "sample-multi")

    val manifestJson = Json.parse(Files.readString(out.resolve("source-manifest/textus-tutorial.json")))
    val paths = (manifestJson \ "files").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "path").as[String])
    assert(paths.contains("samples/01-hello/build.sbt"))
    assert(paths.contains("samples/02-crud/build.sbt"))
    assert(!paths.exists(_.startsWith("ignored.d/")))
  }

  test("index-warehouse generates Maven, repository, and release metadata") {
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

    Cozy.main(Array(
      "index-warehouse",
      warehouse.toString,
      s"--save=${out}",
      "--name=textus-tutorial",
      "--title=Textus Tutorial",
      "--maven-coordinates=org.example:textus-tutorial_3",
      "--repository-artifacts=car,sar"
    ))

    val mavenJson = Json.parse(Files.readString(out.resolve("maven/artifacts/textus-tutorial.json")))
    assert((mavenJson \ "artifact" \ "status").as[String] == "available")
    val coordinates = (mavenJson \ "artifact" \ "coordinates").as[Vector[play.api.libs.json.JsObject]]
    assert((coordinates.head \ "groupId").as[String] == "org.example")
    assert((coordinates.head \ "artifactId").as[String] == "textus-tutorial_3")
    assert((coordinates.head \ "latestRelease").as[String] == "0.1.0")
    val files = (mavenJson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    val paths = files.map(x => (x \ "path").as[String])
    assert(paths.contains("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0.jar"))
    assert(paths.contains("maven/org/example/textus-tutorial_3/0.1.0/textus-tutorial_3-0.1.0-sources.jar"))
    assert(paths.exists(_.contains("0.2.0-SNAPSHOT")))
    assert(!paths.exists(_.contains("other_3")))
    val mainJar = files.find(x => (x \ "name").as[String] == "textus-tutorial_3-0.1.0.jar").get
    assert((mainJar \ "sha1").as[String] == "abc123")
    assert((mainJar \ "md5").as[String] == "def456")
    assert((mainJar \ "sha256").as[String].nonEmpty)

    val repositoryJson = Json.parse(Files.readString(out.resolve("repository/artifacts/textus-tutorial.json")))
    val repositoryKinds = (repositoryJson \ "artifact" \ "kinds").as[Vector[play.api.libs.json.JsObject]]
    val carKind = repositoryKinds.find(x => (x \ "type").as[String] == "car").get
    assert((carKind \ "latestRelease").as[String] == "0.1.0")
    assert((carKind \ "versions").as[Vector[String]] == Vector("0.0.9", "0.1.0"))
    val repositoryFiles = (repositoryJson \ "artifact" \ "files").as[Vector[play.api.libs.json.JsObject]]
    assert(repositoryFiles.map(x => (x \ "path").as[String]).contains("repository/car/textus-tutorial/0.1.0/textus-tutorial-0.1.0.car"))
    assert(repositoryFiles.map(x => (x \ "path").as[String]).contains("repository/car/textus-tutorial/0.0.9/textus-tutorial-0.0.9.car"))
    assert(repositoryFiles.map(x => (x \ "path").as[String]).contains("repository/sar/textus-tutorial/0.1.0/textus-tutorial-0.1.0.sar"))
    assert(!repositoryFiles.map(x => (x \ "path").as[String]).exists(_.contains("other-module")))

    val releaseJson = Json.parse(Files.readString(out.resolve("releases/textus-tutorial.json")))
    assert((releaseJson \ "release" \ "latest").as[String] == "0.1.0")
    val releaseVersions = (releaseJson \ "release" \ "versions").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "version").as[String])
    assert(releaseVersions.contains("0.0.9"))
    assert(releaseVersions.contains("0.1.0"))
    assert(releaseVersions.contains("0.2.0-SNAPSHOT"))
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
}
