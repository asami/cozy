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

    "preserve v3 artifact identities, report-only rows and completed batch bindings" in {
      Given("a supplied graph with a self-edge, two Cozy artifacts, a metadata-only node and an evicted row")
      val request = _request_v3(false)
      val resolution = request.resolution
      When("the schema is round-tripped and structurally validated")
      Json.fromJson[RequestV3](Json.toJson(request)).get shouldBe request
      validateV3(request)
      Then("the selected runtime subset and unavailable profile diagnostics are accepted")
      resolution.artifacts.count(_.key.module == resolution.requestRoots.head) shouldBe 2
      resolution.reportRows.exists(x => !x.eviction.evicted && x.artifacts.isEmpty) shouldBe true
      resolution.reportRows.exists(_.eviction.evicted) shouldBe true
      val batch = Json.parse(Files.readAllBytes(Paths.get(request.batchRecord))).as[BatchV3]
      batch.members.head.artifacts.size shouldBe 3
      validateV3(request.copy(selection = request.selection.copy(selectionId = "another-consumer")))
    }

    "retain an explicit base request and the reported target details" in {
      Given("a development override with only its selected alias reported")
      val request = _request_v3(false)
      val resolution = request.resolution
      val module = resolution.modules.find(_.development).get
      val index = resolution.reportRows.indexWhere(x => x.module == module.identity && !x.eviction.evicted)
      val row = resolution.reportRows(index)
      val selector = ReportArtifactSelectorV3("selected", "jar", "jar", Some("native"), Map("flavor" -> "native"))
      val exclusion = ExclusionV3("excluded", "dependency", "*", Vector("runtime"), Map("reason" -> "fixture"))
      val target = row.targetModule.copy(configurations = Some("runtime->default"),
        explicitArtifacts = Vector(selector), exclusions = Vector(exclusion), isForce = true,
        isChanging = true, isTransitive = false, extraAttributes = Map("branch" -> "fixture"))
      val changed = row.copy(targetModule = target, resolver = Some("public-resolver"), artifactResolver = Some("artifact-resolver"))
      val supplied = request.copy(resolution = resolution.copy(
        modules = resolution.modules.map(x => if (x.identity == module.identity) x.copy(requestedVersion = x.baseVersion) else x),
        edges = resolution.edges.map(x => if (x.to == module.identity) x.copy(requestedVersion = module.baseVersion) else x),
        reportRows = resolution.reportRows.updated(index, changed),
        exactOverrides = Map(s"${module.organization}:${module.name}" -> module.selectedVersion)))
      When("the supplied request is serialized and checked")
      val roundtrip = Json.fromJson[RequestV3](Json.toJson(supplied)).get
      validateV3(roundtrip)
      Then("the actual row and nondefault report facts survive without a fabricated base row")
      roundtrip shouldBe supplied
      roundtrip.resolution.reportRows.filter(_.module == module.identity).map(_.revision) shouldBe Vector(module.selectedVersion)
      roundtrip.resolution.edges.filter(_.to == module.identity).map(_.requestedVersion).distinct shouldBe Vector(module.baseVersion)
      val wrongbase = supplied.resolution.edges.map(x => if (x.to == module.identity) x.copy(requestedVersion = "foreign") else x)
      intercept[Exception](validateV3(supplied.copy(resolution = supplied.resolution.copy(edges = wrongbase))))
      intercept[Exception](validateV3(supplied.copy(resolution = supplied.resolution.copy(exactOverrides = Map.empty))))
      intercept[Exception](validateV3(supplied.copy(resolution = supplied.resolution.copy(edges = supplied.resolution.edges.filterNot(_.to == module.identity)))))
      val mismatch = changed.copy(targetModule = target.copy(revision = "wrong"))
      intercept[Exception](validateV3(supplied.copy(resolution = supplied.resolution.copy(
        reportRows = supplied.resolution.reportRows.updated(index, mismatch)))))
        .getMessage should include("reported target module")
    }

    "refuse incomplete or contradictory v3 evidence" in {
      Given("a complete supplied v3 graph and immutable batch")
      val request = _request_v3(false)
      val resolution = request.resolution
      val selected = resolution.reportRows.indexWhere(x => !x.eviction.evicted && x.artifacts.nonEmpty)
      val row = resolution.reportRows(selected)
      val missing = row.copy(missingArtifacts = Vector(row.artifacts.head.key))
      val cases = Vector(
        request.copy(action = "run-command"),
        request.copy(selection = request.selection.copy(owner = "foreign")),
        request.copy(resolution = resolution.copy(modules = resolution.modules.dropRight(1))),
        request.copy(resolution = resolution.copy(edges = resolution.edges.dropRight(1))),
        request.copy(resolution = resolution.copy(artifacts = resolution.artifacts.dropRight(1))),
        request.copy(resolution = resolution.copy(reportRows = resolution.reportRows.updated(selected, missing))),
        request.copy(resolution = resolution.copy(exactOverrides = Map("org.simplemodeling:cozy_2.12" -> "foreign"))),
        request.copy(resolution = resolution.copy(modules = resolution.modules :+ resolution.modules.head.copy(selectedVersion = "other-alias"))),
        request.copy(resolution = resolution.copy(modules = resolution.modules.updated(0, resolution.modules.head.copy(requestedVersion = "foreign-alias")))),
        request.copy(resolution = resolution.copy(requestRoots = resolution.requestRoots :+ resolution.modules.last.identity)),
        request.copy(resolution = resolution.copy(modules = resolution.modules.updated(0, resolution.modules.head.copy(baseVersion = "wrong"))))
      )
      When("each missing, foreign or contradictory fact is submitted")
      Then("each is rejected before generation")
      cases.foreach(x => intercept[Exception](validateV3(x)).getMessage should include("DEVELOPMENT_GENERATION_INVALID"))
      intercept[Exception](observeV3(request)).getMessage should include("DEVELOPMENT_GENERATION_INVALID")

      val batchpath = Paths.get(request.batchRecord)
      val original = Files.readAllBytes(batchpath)
      val batch = Json.parse(original).as[BatchV3]
      Files.write(batchpath, Json.stringify(Json.toJson(batch.copy(complete = false))).getBytes(StandardCharsets.UTF_8))
      intercept[Exception](validateV3(request)).getMessage should include("batch")
      Files.write(batchpath, original)
      val sourcefile = Paths.get(batch.members.head.artifacts.last.publicationFile)
      val sourcebytes = Files.readAllBytes(sourcefile)
      Files.delete(sourcefile)
      intercept[Exception](validateV3(request)).getMessage should include("file does not exist")
      Files.write(sourcefile, sourcebytes)
      val descriptor = Paths.get(batch.members.head.module.descriptor)
      val xml = new String(Files.readAllBytes(descriptor), StandardCharsets.UTF_8)
      Files.write(descriptor, xml.replace("conf=\"runtime\"", "conf=\"runtime\" e:flavor=\"foreign\"").getBytes(StandardCharsets.UTF_8))
      intercept[Exception](validateV3(request)).getMessage should include("descriptor artifact set")
    }

    "reject v3 publication paths containing symbolic links" in {
      Given("a completed batch referenced through a symbolic link")
      val request = _request_v3(false)
      val batch = Paths.get(request.batchRecord)
      val link = batch.getParent.resolve("linked-batch.json")
      Files.createSymbolicLink(link, batch.getFileName)
      When("the linked path is submitted")
      Then("validation rejects it before resolving the link")
      intercept[Exception](validateV3(request.copy(batchRecord = link.toString))).getMessage should include("symbolic link")
    }

    "execute fixed v3 generation in a fresh JVM using supplied fixture facts" in {
      Given("real compiled Cozy and backend classes with a test-supplied graph")
      val request = _request_v3(true)
      val root = Paths.get(request.roots.owner)
      val requestfile = root.resolve("v3-request.json")
      Files.write(requestfile, Json.stringify(Json.toJson(request)).getBytes(StandardCharsets.UTF_8))
      val paths = request.resolution.runtimeClasspath.map(key => request.resolution.artifacts.find(_.key == key).get.launchFile)
      val log = root.resolve("v3-child.log")
      When("the fixed v3 bridge generates the actual library CML in a new JVM")
      val process = new ProcessBuilder(Paths.get(sys.props("java.home"), "bin", "java").toString,
        "-cp", paths.mkString(java.io.File.pathSeparator), "cozy.Cozy", "sbt-bridge", "v3", "--request", requestfile.toString)
        .redirectErrorStream(true).redirectOutput(log.toFile).start()
      val exit = process.waitFor()
      Then("actual origins, versions, classpath and generated inventory are reported separately from supplied facts")
      withClue(new String(Files.readAllBytes(log), StandardCharsets.UTF_8)) { exit shouldBe 0 }
      val actual = Json.parse(Files.readAllBytes(Paths.get(request.roots.result))).as[ResultV3]
      actual.outcome shouldBe "generated"
      actual.receivedResolution shouldBe request.resolution
      actual.observation.classpath shouldBe paths
      actual.observation.cozyVersion shouldBe org.simplemodeling.cozy.BuildInfo.version
      actual.observation.simpleModelerVersion shouldBe org.simplemodeling.cozy.BuildInfo.simpleModelerVersion
      val cozyid = request.resolution.requestRoots.head
      val modelerid = request.resolution.requestRoots(1)
      request.resolution.artifacts.filter(_.key.module == cozyid).map(_.launchFile) should contain(actual.observation.cozy.codeSource)
      request.resolution.artifacts.filter(_.key.module == modelerid).map(_.launchFile) should contain(actual.observation.simpleModeler.codeSource)
      actual.observation.cozy.resource should include("cozy/Cozy.class")
      actual.observation.simpleModeler.resource should include("Scala3ClassGeneratorExecutor.class")
      actual.generatedFiles.exists(_.endsWith(".scala")) shouldBe true
      actual.generatedFiles.foreach(x => Files.isRegularFile(Paths.get(request.roots.output).resolve(x)) shouldBe true)
    }
  }

  private def _request_v3(real: Boolean): RequestV3 = {
    val root = _work_directory("v3-").toRealPath()
    val build = Files.createDirectory(root.resolve("build"))
    val source = Files.createDirectory(root.resolve("source"))
    val publication = Files.createDirectory(root.resolve("publication"))
    val input = source.resolve("model.cml")
    Files.copy(Paths.get(sys.props("user.dir"), "src/test/resources/modeler/library/address.cml"), input)
    Files.write(build.resolve("project.yaml"), "generation:\n  target: library\n".getBytes(StandardCharsets.UTF_8))
    def _location_(clazz: Class[_]): Path = Paths.get(clazz.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
    val cozyclass = _location_(classOf[_root_.cozy.Cozy])
    val modelerclass = _location_(Class.forName("org.simplemodeling.SimpleModeler.generator.scala.Scala3ClassGeneratorExecutor"))
    val scalaclass = _location_(classOf[scala.Option[_]])
    val urls = Iterator.iterate(Option(getClass.getClassLoader))(_.flatMap(x => Option(x.getParent)))
      .takeWhile(_.nonEmpty).flatMap(_.toVector).flatMap {
        case loader: java.net.URLClassLoader => loader.getURLs.toVector
        case _ => Vector.empty[java.net.URL]
      }.filter(_.getProtocol == "file").map(x => Paths.get(x.toURI).toRealPath()).toVector
    val paths = if (real) (Vector(cozyclass, modelerclass, scalaclass) ++ urls ++
      sys.props.getOrElse("java.class.path", "").split(java.io.File.pathSeparator).filter(_.nonEmpty)
        .map(x => Paths.get(x).toRealPath()) ++ Vector(_location_(getClass))).distinct
      else Vector(cozyclass, modelerclass, scalaclass)
    val cozyversion = org.simplemodeling.cozy.BuildInfo.version
    val modelerversion = org.simplemodeling.cozy.BuildInfo.simpleModelerVersion
    val modules = paths.zipWithIndex.map { case (path, index) =>
      val (org, name, base, selected) =
        if (path == cozyclass) ("org.simplemodeling", "cozy_2.12", cozyversion, "fixture-private-cozy")
        else if (path == modelerclass) ("org.simplemodeling", "simplemodeler_2.12", modelerversion, modelerversion)
        else if (path == scalaclass) ("org.scala-lang", "scala-library", "2.12.fixture", "2.12.fixture")
        else ("fixture", s"dependency-$index", "1", "1")
      val revision = publication.resolve(org).resolve(name).resolve(selected)
      ModuleV3(org, name, selected, selected, base, path == cozyclass,
        if (path == cozyclass) Some(revision.resolve("ivys/ivy.xml").toString) else None,
        if (path == cozyclass) Some(revision.resolve("control.json").toString) else None)
    }
    val cozy = modules.find(_.development).get
    val modeler = modules.find(_.name == "simplemodeler_2.12").get
    val metadata = ModuleV3("fixture", "metadata", "1", "1", "1", false, None, None)
    val allmodules = modules :+ metadata
    def _copy_jar_(path: Path, target: Path): Unit = {
      Files.createDirectories(target.getParent)
      if (Files.isDirectory(path)) {
        val out = new java.util.jar.JarOutputStream(Files.newOutputStream(target))
        val walk = Files.walk(path)
        try walk.iterator.asScala.filter(Files.isRegularFile(_)).foreach { file =>
          out.putNextEntry(new java.util.jar.JarEntry(path.relativize(file).toString.replace('\\', '/')))
          Files.copy(file, out)
          out.closeEntry()
        } finally { walk.close(); out.close() }
      } else Files.copy(path, target)
    }
    def _empty_jar_(path: Path): Unit = {
      Files.createDirectories(path.getParent)
      val out = new java.util.jar.JarOutputStream(Files.newOutputStream(path))
      out.close()
    }
    val ordinary = modules.zip(paths).map { case (module, path) =>
      val target = publication.resolve(module.organization).resolve(module.name).resolve(module.selectedVersion)
        .resolve("jars").resolve(module.name + ".jar")
      _copy_jar_(path, target)
      val key = ArtifactKeyV3(module.identity, module.name, "jar", "jar", None, Map.empty)
      ArtifactV3(key, target.toString, target.toString, true)
    }
    val revision = publication.resolve(cozy.organization).resolve(cozy.name).resolve(cozy.selectedVersion)
    val classifierpath = revision.resolve("jars").resolve(cozy.name + "-native.jar")
    val sourcespath = revision.resolve("jars").resolve(cozy.name + "-sources.jar")
    _empty_jar_(classifierpath)
    _empty_jar_(sourcespath)
    val classifier = ArtifactKeyV3(cozy.identity, cozy.name, "jar", "jar", Some("native"), Map("e:flavor" -> "native"))
    val sources = ArtifactKeyV3(cozy.identity, cozy.name, "src", "jar", Some("sources"), Map.empty)
    val extra = ArtifactV3(classifier, classifierpath.toString, classifierpath.toString, true)
    val artifacts = ordinary :+ extra
    val edges = allmodules.filterNot(_ == cozy).map(x => Edge(cozy.identity, x.identity, x.selectedVersion)) :+
      Edge(cozy.identity, cozy.identity, cozy.selectedVersion)
    def _caller_(from: String): CallerV3 = CallerV3(from, allmodules.find(_.identity == from).get.selectedVersion,
      Vector("runtime"), Map.empty, Vector.empty, Vector.empty, false, false, true, false)
    def _target_(module: ModuleV3, revision: String): ReportTargetModuleV3 =
      ReportTargetModuleV3(module.organization, module.name, revision, None, Vector.empty, Vector.empty,
        false, false, true, Map.empty)
    val rows = allmodules.map { module =>
      ReportRowV3(module.identity, module.selectedVersion, Vector("runtime"),
        artifacts.filter(_.key.module == module.identity).map(x => ReportArtifactV3(x.key, x.resolverFile)), Vector.empty,
        edges.filter(_.to == module.identity).map(x => _caller_(x.from)), EvictionV3(false, None, Vector.empty), None, Map.empty, _target_(module, module.selectedVersion), None, None)
    } :+ ReportRowV3(modeler.identity, "evicted-revision", Vector("runtime"), Vector.empty, Vector.empty,
      Vector.empty, EvictionV3(true, Some(modeler.selectedVersion), Vector("fixture eviction")), None, Map.empty,
      _target_(modeler, "evicted-revision"), None, None)
    val cozymain = ordinary.find(_.key.module == cozy.identity).get
    val published = PublishedModuleV3(cozy.organization, cozy.name, cozy.selectedVersion, cozy.baseVersion,
      cozy.descriptor.get, cozy.control.get)
    val member = BatchMemberV3(published, Vector(PublishedArtifactV3(cozymain.key, cozymain.launchFile),
      PublishedArtifactV3(classifier, classifierpath.toString), PublishedArtifactV3(sources, sourcespath.toString)))
    val descriptor = Paths.get(published.descriptor)
    Files.createDirectories(descriptor.getParent)
    val xml = s"""<ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra"><info organisation="${cozy.organization}" module="${cozy.name}" revision="${cozy.selectedVersion}"/><publications><artifact name="${cozy.name}" type="jar" ext="jar" conf="runtime"/><artifact name="${cozy.name}" type="jar" ext="jar" e:classifier="native" e:flavor="native"/><artifact name="${cozy.name}" type="src" ext="jar" e:classifier="sources"/></publications></ivy-module>"""
    Files.write(descriptor, xml.getBytes(StandardCharsets.UTF_8))
    val control = Json.obj("scope" -> "scope", "owner" -> "owner", "producerSelectionId" -> "producer", "batch" -> "batch",
      "complete" -> true, "module" -> Json.toJson(published), "artifacts" -> Json.toJson(member.artifacts))
    Files.write(Paths.get(published.control), Json.stringify(control).getBytes(StandardCharsets.UTF_8))
    val batchpath = publication.resolve("batch.json")
    Files.write(batchpath, Json.stringify(Json.toJson(BatchV3("scope", "owner", "batch", "producer", true, Vector(member)))).getBytes(StandardCharsets.UTF_8))
    val resolution = ResolutionV3("fixture-only", Vector(cozy.identity, modeler.identity), Map.empty, Vector("runtime"), rows,
      allmodules, artifacts, edges, artifacts.map(_.key), Json.obj(), Json.obj(),
      Json.obj("appliedProfileTrace" -> Json.obj("status" -> "unavailable", "reason" -> "public report")))
    val output = build.resolve("output")
    RequestV3("v3", "generate-development", Selection("scope", "owner", "consumer", "batch", publication.toString),
      Roots(build.toString, root.toString, source.toString, output.toString, build.resolve("result.json").toString),
      resolution, batchpath.toString, Vector("modeler-scala-value", input.toString, "--save", output.toString),
      Map("sbt.project_dir" -> build.toString, "generation.target" -> "library", "generation.versions.cozy" -> cozyversion,
        "component.namespace" -> "org.simplemodeling", "component.id" -> "SimpleModelingModel"))
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
