package cozy.runtime

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.jar.JarFile
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

/*
 * @since   Nov. 19, 2026
 * @version Nov. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDevelopmentGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyDevelopmentGeneration._

  "development generation" should {
    "round trip the typed request and reject unsupported actions and incomplete graphs" in {
      Given("an explicit selected graph and separate source, build and publication roots")
      val request = _request()
      When("the typed schema is read and structural guards examine altered selections")
      Json.fromJson[Request](Json.toJson(request)).get shouldBe request
      val action = intercept[Exception](validate(request.copy(action = "run-command")))
      val missing = intercept[Exception](validate(request.copy(edges = Vector(Edge(request.modules.head.identity, "missing", "1")))))
      val foreign = intercept[Exception](validate(request.copy(selection = request.selection.copy(scope = "foreign"))))
      val owner = intercept[Exception](validate(request.copy(roots = request.roots.copy(owner = request.roots.build))))
      val version = intercept[Exception](validate(request.copy(modules = request.modules.updated(0, request.modules.head.copy(baseVersion = "wrong")))))
      val path = intercept[Exception](validate(request.copy(arguments = request.arguments.updated(1, "/missing/source.cml"))))
      Then("each invalid selection fails before generation")
      Vector(action, missing, foreign, owner, version, path).foreach(_.getMessage should include("DEVELOPMENT_GENERATION_INVALID"))
    }

    "reject an omitted dependency and an incomplete batch" in {
      val request = _request()
      val omitted = request.copy(modules = request.modules.dropRight(1), edges = request.edges.dropRight(1),
        classpath = request.classpath.dropRight(1))
      intercept[Exception](validate(omitted)).getMessage should include("resolution evidence")
      Files.write(Paths.get(request.resolution), Json.stringify(Json.toJson(Resolution(omitted.modules, omitted.edges))).getBytes(StandardCharsets.UTF_8))
      intercept[Exception](validate(omitted)).getMessage should include("missing published dependency")
      val missingedge = request.copy(edges = request.edges.dropRight(1))
      Files.write(Paths.get(request.resolution), Json.stringify(Json.toJson(Resolution(request.modules, missingedge.edges))).getBytes(StandardCharsets.UTF_8))
      intercept[Exception](validate(missingedge)).getMessage should include("missing published dependency")
      Files.write(Paths.get(request.resolution), Json.stringify(Json.toJson(Resolution(request.modules, request.edges))).getBytes(StandardCharsets.UTF_8))
      val batch = Json.parse(Files.readAllBytes(Paths.get(request.batchRecord))).as[Batch]
      val absent = batch.members.head.copy(name = "absent", artifact = Paths.get(request.selection.publicationRoot, "absent.jar").toString,
        publication = Some(Paths.get(request.selection.publicationRoot, "absent.jar").toString))
      Files.write(Paths.get(request.batchRecord), Json.stringify(Json.toJson(batch.copy(members = batch.members :+ absent))).getBytes(StandardCharsets.UTF_8))
      intercept[Exception](validate(request)).getMessage should include("file does not exist")
    }

    "reject missing or mismatched published dependency metadata" in {
      Given("a complete selected graph bound to Ivy publications")
      val request = _request()
      val descriptor = Paths.get(request.modules(1).artifact).getParent.getParent.resolve("ivys/ivy.xml")
      val original = Files.readAllBytes(descriptor)
      When("the descriptor is missing or has foreign coordinates")
      Files.delete(descriptor)
      val missing = intercept[Exception](validate(request))
      Files.write(descriptor, new String(original, StandardCharsets.UTF_8).replace("simplemodeler_2.12", "foreign").getBytes(StandardCharsets.UTF_8))
      val foreign = intercept[Exception](validate(request))
      Then("the selection is rejected before generation")
      missing.getMessage should include("file does not exist")
      foreign.getMessage should include("Ivy publication coordinates")
    }

    "exclude scala-tool Ivy dependencies from the runtime graph" in {
      Given("a selected backend whose Ivy descriptor declares non-runtime Scala tools")
      val request = _request()
      val descriptor = Paths.get(request.modules(1).artifact).getParent.getParent.resolve("ivys/ivy.xml")
      val xml = new String(Files.readAllBytes(descriptor), StandardCharsets.UTF_8)
      val tools = """<dependency org="org.scala-lang" name="scala-compiler" rev="2.12.18" conf="scala-tool->default"/><dependency org="org.scala-lang" name="scala-library" rev="2.12.18" conf="scala-tool->optional(default)"/>"""
      Files.write(descriptor, xml.replace("</dependencies>", tools + "</dependencies>").getBytes(StandardCharsets.UTF_8))
      When("the selected graph is validated without adding the compiler tool")
      val result = validate(request)
      Then("both tool declarations are excluded from the runtime graph")
      result shouldBe (())
    }

    "reject undeclared graph edges and compound Ivy mappings" in {
      val request = _request()
      val path = Paths.get(request.selection.publicationRoot, "fixture", "extra", "1", "jars", "extra.jar")
      Files.createDirectories(path.getParent)
      Files.write(path, Array[Byte](1))
      val extra = Module("fixture", "extra", "1", "1", "1", path.toString, false, None, None)
      _descriptors(Vector(extra), Vector.empty)
      val modules = request.modules :+ extra
      val edges = request.edges :+ Edge(request.modules.head.identity, extra.identity, "1")
      Files.write(Paths.get(request.resolution), Json.stringify(Json.toJson(Resolution(modules, edges))).getBytes(StandardCharsets.UTF_8))
      intercept[Exception](validate(request.copy(modules = modules, edges = edges, classpath = request.classpath :+ extra.artifact)))
        .getMessage should include("undeclared graph edge")
      Files.write(Paths.get(request.resolution), Json.stringify(Json.toJson(Resolution(request.modules, request.edges))).getBytes(StandardCharsets.UTF_8))
      val descriptor = Paths.get(request.modules(1).artifact).getParent.getParent.resolve("ivys/ivy.xml")
      val xml = new String(Files.readAllBytes(descriptor), StandardCharsets.UTF_8)
      Files.write(descriptor, xml.replace("compile->default(compile)", "scala-tool->default;compile->default(compile)").getBytes(StandardCharsets.UTF_8))
      intercept[Exception](validate(request)).getMessage should include("compound Ivy")
    }

    "accept the original Coursier scala-library artifact with its sibling POM" in {
      val request = _request()
      val cache = Paths.get(sys.props("user.home"), "Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.18/scala-library-2.12.18.jar")
      val original = request.modules.last
      val cached = original.copy(artifact = cache.toString)
      val modules = request.modules.updated(2, cached)
      Files.write(Paths.get(request.resolution), Json.stringify(Json.toJson(Resolution(modules, request.edges))).getBytes(StandardCharsets.UTF_8))
      validate(request.copy(modules = modules, classpath = request.classpath.updated(2, cached.artifact)))
    }

    "enforce Maven POM dependencies and artifact coordinates" in {
      val request = _request()
      val root = Paths.get(request.selection.publicationRoot, "maven", "org", "scala-lang", "scala-library", "2.12.18")
      Files.createDirectories(root)
      val jar = Files.write(root.resolve("scala-library-2.12.18.jar"), Array[Byte](1))
      val pom = root.resolve("scala-library-2.12.18.pom")
      val header = "<project><modelVersion>4.0.0</modelVersion><groupId>org.scala-lang</groupId><artifactId>scala-library</artifactId><version>2.12.18</version>"
      def _write_pom_(body: String): Unit = Files.write(pom, (header + body + "</project>").getBytes(StandardCharsets.UTF_8))
      val modules = request.modules.updated(2, request.modules.last.copy(artifact = jar.toString))
      Files.write(Paths.get(request.resolution), Json.stringify(Json.toJson(Resolution(modules, request.edges))).getBytes(StandardCharsets.UTF_8))
      val updated = request.copy(modules = modules, classpath = request.classpath.updated(2, jar.toString))
      _write_pom_("<dependencies><dependency><groupId>missing</groupId><artifactId>transitive</artifactId><version>1</version></dependency></dependencies>")
      intercept[Exception](validate(updated)).getMessage should include("missing published dependency")
      _write_pom_("")
      validate(updated)
      Files.write(pom, (header.replace("2.12.18", "2.12.17") + "</project>").getBytes(StandardCharsets.UTF_8))
      intercept[Exception](validate(updated)).getMessage should include("Maven POM coordinates")
    }

    "reject Maven relocation in a selected POM or its parent" in {
      Given("a canonical Maven artifact whose graph omits its relocated target")
      val request = _request()
      val repository = Paths.get(request.selection.publicationRoot, "maven")
      val root = repository.resolve("org/scala-lang/scala-library/2.12.18")
      Files.createDirectories(root)
      val jar = Files.write(root.resolve("scala-library-2.12.18.jar"), Array[Byte](1))
      val pom = root.resolve("scala-library-2.12.18.pom")
      val header = "<project><modelVersion>4.0.0</modelVersion><groupId>org.scala-lang</groupId><artifactId>scala-library</artifactId><version>2.12.18</version>"
      val relocation = "<distributionManagement><relocation><groupId>other</groupId><artifactId>target</artifactId><version>1</version></relocation></distributionManagement>"
      val modules = request.modules.updated(2, request.modules.last.copy(artifact = jar.toString))
      Files.write(Paths.get(request.resolution), Json.stringify(Json.toJson(Resolution(modules, request.edges))).getBytes(StandardCharsets.UTF_8))
      val updated = request.copy(modules = modules, classpath = request.classpath.updated(2, jar.toString))
      Files.write(pom, (header + relocation + "</project>").getBytes(StandardCharsets.UTF_8))
      When("validation reads the relocated POM with no declared dependencies")
      Then("it rejects the unsupported relocation rather than accepting the incomplete graph")
      intercept[Exception](validate(updated)).getMessage should include("unsupported Maven relocation")

      val parentroot = repository.resolve("org/example/parent/1")
      Files.createDirectories(parentroot)
      Files.write(parentroot.resolve("parent-1.pom"), ("<project><groupId>org.example</groupId><artifactId>parent</artifactId><version>1</version>" + relocation + "</project>").getBytes(StandardCharsets.UTF_8))
      Files.write(pom, (header + "<parent><groupId>org.example</groupId><artifactId>parent</artifactId><version>1</version></parent></project>").getBytes(StandardCharsets.UTF_8))
      intercept[Exception](validate(updated)).getMessage should include("unsupported Maven relocation")
    }

    "retain requested dependency versions across producer eviction" in {
      Given("a descriptor requesting an older revision selected by the producer at another version")
      val request = _request()
      val edge = request.edges.last.copy(requestedVersion = "2.12.17")
      val edges = request.edges.updated(1, edge)
      _descriptors(request.modules, edges)
      Files.write(Paths.get(request.resolution), Json.stringify(Json.toJson(Resolution(request.modules, edges))).getBytes(StandardCharsets.UTF_8))
      When("the selected graph preserves the descriptor revision on the edge")
      Then("the guard accepts the producer's selected version without resolving versions")
      validate(request.copy(edges = edges))
    }

    "collect actual generator origins and backend artifact version" in {
      Given("the current test JVM and its real generator classes")
      When("the bounded collector observes the JVM")
      val observation = collectObservation()
      val loaded = Class.forName("org.simplemodeling.SimpleModeler.generator.scala.Scala3ClassGeneratorExecutor")
      val backend = Paths.get(loaded.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
      val jar = new JarFile(backend.toFile)
      val version = try jar.getManifest.getMainAttributes.getValue("Implementation-Version") finally jar.close()
      Then("the origin and version come from the executing classes and backend JAR")
      Paths.get(observation.cozy.codeSource).toRealPath() shouldBe Paths.get(classOf[cozy.Cozy].getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
      Paths.get(observation.simpleModeler.codeSource).toRealPath() shouldBe backend
      observation.simpleModeler.resource should include("Scala3ClassGeneratorExecutor.class")
      observation.simpleModelerVersion shouldBe version
      observation.simpleModelerVersion should not be empty
      observation.cozyVersion shouldBe org.simplemodeling.cozy.BuildInfo.version
    }

    "reject an origin that does not identify the selected generator artifacts" in {
      Given("a structurally complete graph with ordinary files as selected artifacts")
      val request = _request()
      validate(request)
      When("the running JVM is observed")
      val error = intercept[Exception](observe(request))
      Then("input records cannot stand in for actual class origin")
      error.getMessage should include("actual class origins differ")
    }

    "execute a successful v2 request in a real generator JVM" in {
      val root = _work_directory("v2-execute-").toRealPath()
      val publication = Files.createDirectory(root.resolve("publication"))
      val build = Files.createDirectory(root.resolve("build"))
      val source = Files.createDirectory(root.resolve("source"))
      val input = source.resolve("address.cml")
      Files.copy(Paths.get(sys.props("user.dir"), "src/test/resources/modeler/library/address.cml"), input)
      Files.write(build.resolve("project.yaml"), "generation:\n  target: library\n".getBytes(StandardCharsets.UTF_8))
      def _location_(c: Class[_]): Path = Paths.get(c.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
      val cozysource = _location_(classOf[cozy.Cozy])
      val modelersource = _location_(Class.forName("org.simplemodeling.SimpleModeler.generator.scala.Scala3ClassGeneratorExecutor"))
      val scalasource = _location_(classOf[scala.Option[_]])
      val urls = Iterator.iterate(Option(getClass.getClassLoader))(_.flatMap(x => Option(x.getParent)))
        .takeWhile(_.nonEmpty).flatMap(_.toVector).flatMap {
          case loader: java.net.URLClassLoader => loader.getURLs.toVector
          case _ => Vector.empty[java.net.URL]
        }.filter(_.getProtocol == "file").map(x => Paths.get(x.toURI).toRealPath()).toVector
      val paths = (urls ++ sys.props.getOrElse("java.class.path", "").split(java.io.File.pathSeparator).filter(_.nonEmpty)
        .map(x => Paths.get(x).toRealPath()) ++ Vector(cozysource, modelersource, scalasource, _location_(getClass))).distinct
      val cozyindex = paths.indexOf(cozysource)
      val modelerindex = paths.indexOf(modelersource)
      val scalaindex = paths.indexOf(scalasource)
      val cozyversion = org.simplemodeling.cozy.BuildInfo.version
      val modelerversion = org.simplemodeling.cozy.BuildInfo.simpleModelerVersion
      val control = publication.resolve("control.json")
      val modules = paths.zipWithIndex.map { case (path, i) =>
        val (org, name, base, selected) =
          if (i == cozyindex) ("org.simplemodeling", "cozy_2.12", cozyversion, "fixture-development")
          else if (i == modelerindex) ("org.simplemodeling", "simplemodeler_2.12", modelerversion, modelerversion)
          else if (i == scalaindex) ("org.scala-lang", "scala-library", "2.12.fixture", "2.12.fixture")
          else ("fixture", s"dependency-$i", "1", "1")
        val jar = publication.resolve(org).resolve(name).resolve(selected).resolve("jars").resolve(s"$name.jar")
        Files.createDirectories(jar.getParent)
        if (Files.isDirectory(path)) {
          val out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar))
          val walk = Files.walk(path)
          try walk.iterator.asScala.filter(Files.isRegularFile(_)).foreach { file =>
            val entry = path.relativize(file).toString.replace('\\', '/')
            out.putNextEntry(new java.util.jar.JarEntry(entry))
            Files.copy(file, out)
            out.closeEntry()
          } finally { walk.close(); out.close() }
        } else Files.copy(path, jar)
        Module(org, name, base, selected, base, jar.toRealPath().toString,
          i == cozyindex, if (i == cozyindex) Some(jar.toRealPath().toString) else None,
          if (i == cozyindex) Some(control.toString) else None)
      }
      val artifacts = modules.map(x => Paths.get(x.artifact))
      val cozymodule = modules(cozyindex)
      val edges = modules.filterNot(_ == cozymodule).map(x => Edge(cozymodule.identity, x.identity, x.requestedVersion))
      _descriptors(modules, edges)
      val selection = Selection("scope", "owner", "consumer", "batch", publication.toString)
      val metadata = Json.obj("scope" -> "scope", "owner" -> "owner", "producerSelectionId" -> "producer", "batch" -> "batch", "complete" -> true,
        "organization" -> cozymodule.organization, "name" -> cozymodule.name, "requestedVersion" -> cozymodule.requestedVersion,
        "selectedVersion" -> cozymodule.selectedVersion, "baseVersion" -> cozymodule.baseVersion, "artifact" -> cozymodule.artifact)
      Files.write(control, Json.stringify(metadata).getBytes(StandardCharsets.UTF_8))
      val batch = publication.resolve("batch.json")
      Files.write(batch, Json.stringify(Json.toJson(Batch("scope", "owner", "batch", "producer", true, Vector(cozymodule)))).getBytes(StandardCharsets.UTF_8))
      val resolution = root.resolve("resolution.json")
      Files.write(resolution, Json.stringify(Json.toJson(Resolution(modules, edges))).getBytes(StandardCharsets.UTF_8))
      val output = build.resolve("output")
      val result = build.resolve("result.json")
      val request = Request("v2", "generate-development", selection, Roots(build.toString, root.toString, source.toString, output.toString, result.toString),
        modules, edges, artifacts.map(_.toString), resolution.toString, batch.toString,
        Vector("modeler-scala-value", input.toString, "--save", output.toString),
        Map("sbt.project_dir" -> build.toString, "generation.target" -> "library", "generation.versions.cozy" -> cozyversion,
          "component.namespace" -> "org.simplemodeling", "component.id" -> "SimpleModelingModel"))
      val requestfile = root.resolve("request.json")
      Files.write(requestfile, Json.stringify(Json.toJson(request)).getBytes(StandardCharsets.UTF_8))
      val log = root.resolve("child.log")
      val javaexecutable = Paths.get(sys.props("java.home"), "bin", "java").toString
      val process = new ProcessBuilder(javaexecutable, "-cp", artifacts.mkString(java.io.File.pathSeparator),
        "cozy.Cozy", "sbt-bridge", "v2", "--request", requestfile.toString).redirectErrorStream(true).redirectOutput(log.toFile).start()
      val exit = process.waitFor()
      withClue(new String(Files.readAllBytes(log), StandardCharsets.UTF_8)) { exit shouldBe 0 }
      val actual = Json.parse(Files.readAllBytes(result)).as[Result]
      actual.outcome shouldBe "generated"
      actual.observation.classpath shouldBe artifacts.map(_.toString)
      Paths.get(actual.observation.cozy.codeSource).toRealPath() shouldBe artifacts(cozyindex)
      Paths.get(actual.observation.simpleModeler.codeSource).toRealPath() shouldBe artifacts(modelerindex)
      actual.observation.simpleModelerVersion shouldBe modelerversion
      actual.generatedFiles.exists(_.endsWith(".scala")) shouldBe true
      actual.generatedFiles.foreach(x => Files.isRegularFile(output.resolve(x)) shouldBe true)
    }

    "run the existing guarded generator on actual CML" in {
      Given("the existing library CML and real current generator")
      val root = _work_directory("generation-")
      val input = Paths.get(sys.props("user.dir"), "src/test/resources/modeler/library/address.cml").toAbsolutePath
      val output = root.resolve("output")
      Files.write(root.resolve("project.yaml"), "generation:\n  target: library\n".getBytes(StandardCharsets.UTF_8))
      val settings = Map("sbt.project_dir" -> root.toString, "generation.target" -> "library",
        "generation.versions.cozy" -> org.simplemodeling.cozy.BuildInfo.version,
        "component.namespace" -> "org.simplemodeling", "component.id" -> "SimpleModelingModel")
      When("the bounded bridge delegate executes the actual generator")
      CozySbtBridge.runDevelopmentGeneration(Vector("modeler-scala-value", input.toString, "--save", output.toString), settings)
      val stream = Files.walk(output)
      val files = try stream.iterator.asScala.filter(_.toString.endsWith(".scala")).toVector finally stream.close()
      Then("real generated Scala files exist; this does not certify a private selected JVM")
      files should not be empty
    }
  }

  private def _work_directory(prefix: String): Path = {
    val parent = Paths.get(sys.props("user.dir"), "target", "cozy-development-generation", "work").toAbsolutePath
    Files.createDirectories(parent)
    Files.createTempDirectory(parent, prefix)
  }

  private def _descriptors(modules: Vector[Module], edges: Vector[Edge]): Unit = {
    modules.foreach { module =>
      val deps = edges.filter(_.from == module.identity).map { edge =>
        val target = modules.find(_.identity == edge.to).get
        s"""<dependency org="${target.organization}" name="${target.name}" rev="${edge.requestedVersion}" conf="compile->default(compile)"/>"""
      }.mkString
      val xml = s"""<ivy-module version="2.0"><info organisation="${module.organization}" module="${module.name}" revision="${module.selectedVersion}"/><configurations><conf name="compile"/><conf name="runtime" extends="compile"/></configurations><publications><artifact name="${module.name}" type="jar" ext="jar" conf="compile"/></publications><dependencies>$deps</dependencies></ivy-module>"""
      val path = Paths.get(module.artifact).getParent.getParent.resolve("ivys/ivy.xml")
      Files.createDirectories(path.getParent)
      Files.write(path, xml.getBytes(StandardCharsets.UTF_8))
    }
  }

  private def _request(): Request = {
    val root = _work_directory("contract-").toRealPath()
    val build = Files.createDirectory(root.resolve("build"))
    val source = Files.createDirectory(root.resolve("source"))
    val publication = Files.createDirectory(root.resolve("publication"))
    val input = Files.write(source.resolve("model.cml"), Array[Byte](1))
    val cozystable = org.simplemodeling.cozy.BuildInfo.version
    val modelerstable = org.simplemodeling.cozy.BuildInfo.simpleModelerVersion
    def _artifact_(org: String, name: String, version: String): Path = {
      val path = publication.resolve(org).resolve(name).resolve(version).resolve("jars").resolve(s"$name.jar")
      Files.createDirectories(path.getParent)
      Files.write(path, Array[Byte](1))
    }
    val cozyartifact = _artifact_("org.simplemodeling", "cozy_2.12", "private-cozy")
    val control = publication.resolve("control.json")
    val cozy = Module("org.simplemodeling", "cozy_2.12", cozystable, "private-cozy", cozystable,
      cozyartifact.toString, true, Some(cozyartifact.toString), Some(control.toString))
    val modeler = Module("org.simplemodeling", "simplemodeler_2.12", modelerstable, modelerstable, modelerstable,
      _artifact_("org.simplemodeling", "simplemodeler_2.12", modelerstable).toString, false, None, None)
    val scala = Module("org.scala-lang", "scala-library", "2.12.18", "2.12.18", "2.12.18",
      _artifact_("org.scala-lang", "scala-library", "2.12.18").toString, false, None, None)
    val selection = Selection("scope", "owner", "selection", "batch", publication.toString)
    val metadata = Json.obj("scope" -> "scope", "owner" -> "owner", "producerSelectionId" -> "producer", "batch" -> "batch", "complete" -> true,
      "organization" -> cozy.organization, "name" -> cozy.name, "requestedVersion" -> cozy.requestedVersion,
      "selectedVersion" -> cozy.selectedVersion, "baseVersion" -> cozy.baseVersion, "artifact" -> cozy.artifact)
    Files.write(control, Json.stringify(metadata).getBytes(StandardCharsets.UTF_8))
    val modules = Vector(cozy, modeler, scala)
    val edges = Vector(Edge(cozy.identity, modeler.identity, modelerstable), Edge(modeler.identity, scala.identity, "2.12.18"))
    _descriptors(modules, edges)
    val resolution = root.resolve("resolution.json")
    Files.write(resolution, Json.stringify(Json.toJson(Resolution(modules, edges))).getBytes(StandardCharsets.UTF_8))
    val batch = publication.resolve("batch.json")
    Files.write(batch, Json.stringify(Json.toJson(Batch("scope", "owner", "batch", "producer", true, Vector(cozy)))).getBytes(StandardCharsets.UTF_8))
    Request("v2", "generate-development", selection,
      Roots(build.toString, root.toString, source.toString, build.resolve("output").toString, build.resolve("result.json").toString),
      modules, edges, Vector(cozy.artifact, modeler.artifact, scala.artifact), resolution.toString, batch.toString,
      Vector("modeler-scala-value", input.toString, "--save", build.resolve("output").toString),
      Map("sbt.project_dir" -> build.toString, "generation.target" -> "library", "generation.versions.cozy" -> cozystable))
  }
}
