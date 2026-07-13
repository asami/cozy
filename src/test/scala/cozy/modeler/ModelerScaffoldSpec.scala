package cozy.modeler

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.record.v2.{CFormat, CMaxLength, CMinLength, CRegex}

/*
 * @since   Jun. 23, 2026
 *  version Jun. 27, 2026
 * @version Jul. 14, 2026
 * @author  ASAMI, Tomoharu
 */
class ModelerScaffoldSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "CML modeler scaffold generation" should {
    "generate sbt project scaffolds" which {
      "car-sbt-project generates sbt project scaffold" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/test.dox")
        val out = base.resolve("target/test-generated/car-sbt-project")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array("car-sbt-project", input.toString, "--save", out.toString.toString))

        val buildsbt = out.resolve("build.sbt")
        val projectyaml = out.resolve("project.yaml")
        val pluginssbt = out.resolve("project/plugins.sbt")
        val projectyamlbuild = out.resolve("project/ProjectYamlBuild.scala")
        val buildproperties = out.resolve("project/build.properties")
        val launcher = out.resolve("bin/launcher")
        val commonscript = out.resolve("scripts/cncf-common.sh")
        val updateclasspathscript = out.resolve("scripts/update-runtime-classpath.sh")
        val runserverscript = out.resolve("scripts/run-server.sh")
        val runserverdebugscript = out.resolve("scripts/run-server-debug.sh")
        val webappdescriptor = out.resolve("src/main/web-inf/web.yaml")
        val formdescriptor = out.resolve("src/main/web-inf/form.yaml")
        val sourcecomponentdescriptor = out.resolve("src/main/car/component-descriptor.json")
        val samplecml = out.resolve("src/main/cozy/sample.cml")
        Then("the generated project files satisfy the scaffold contract")
        withClue(s"build.sbt not found: $buildsbt") {
        Files.exists(buildsbt) shouldBe true
      }
        withClue(s"project.yaml not found: $projectyaml") {
        Files.exists(projectyaml) shouldBe true
      }
        withClue(s"ProjectYamlBuild.scala not found: $projectyamlbuild") {
        Files.exists(projectyamlbuild) shouldBe true
      }
        withClue(s"plugins.sbt not found: $pluginssbt") {
        Files.exists(pluginssbt) shouldBe true
      }
        withClue(s"build.properties not found: $buildproperties") {
        Files.exists(buildproperties) shouldBe true
      }
        withClue(s"launcher not found: $launcher") {
        Files.exists(launcher) shouldBe true
      }
        withClue(s"common script not found: $commonscript") {
        Files.exists(commonscript) shouldBe true
      }
        withClue(s"update script not found: $updateclasspathscript") {
        Files.exists(updateclasspathscript) shouldBe true
      }
        withClue(s"server script not found: $runserverscript") {
        Files.exists(runserverscript) shouldBe true
      }
        withClue(s"debug server script not found: $runserverdebugscript") {
        Files.exists(runserverdebugscript) shouldBe true
      }
        withClue(s"web app descriptor not found: $webappdescriptor") {
        Files.exists(webappdescriptor) shouldBe true
      }
        withClue(s"form descriptor not found: $formdescriptor") {
        Files.exists(formdescriptor) shouldBe true
      }
        withClue(s"source component descriptor must be packaging-derived: $sourcecomponentdescriptor") {
        Files.exists(sourcecomponentdescriptor) shouldBe false
      }
        withClue(s"sample model not found: $samplecml") {
        Files.exists(samplecml) shouldBe true
      }
        withClue(s"launcher must be executable: $launcher") {
        Files.isExecutable(launcher) shouldBe true
      }
        withClue(s"update script must be executable: $updateclasspathscript") {
        Files.isExecutable(updateclasspathscript) shouldBe true
      }
        withClue(s"server script must be executable: $runserverscript") {
        Files.isExecutable(runserverscript) shouldBe true
      }
        withClue(s"debug server script must be executable: $runserverdebugscript") {
        Files.isExecutable(runserverdebugscript) shouldBe true
      }
        val buildsbtcontent = Files.readString(buildsbt)
        val projectyamlcontent = Files.readString(projectyaml)
        val projectyamlbuildcontent = Files.readString(projectyamlbuild)
        val pluginssbtcontent = Files.readString(pluginssbt)
        val updateclasspathcontent = Files.readString(updateclasspathscript)
        val runservercontent = Files.readString(runserverscript)
        val runserverdebugcontent = Files.readString(runserverdebugscript)
        val formdescriptorcontent = Files.readString(formdescriptor)
        buildsbtcontent should include ("enablePlugins(org.goldenport.cozy.CozyPlugin)")
        buildsbtcontent should not include ("lazy val packageCar = taskKey[File]")
        buildsbtcontent should not include ("""target.value / "car" / s"${name.value}-${version.value}.car"""")
        buildsbtcontent should not include ("""val cncfVersion = """)
        buildsbtcontent should not include ("configuredVersion")
        buildsbtcontent should not include ("CNCF_SAMPLES_ROOT")
        buildsbtcontent should include ("libraryDependencies ++= ProjectYamlBuild.dependencies")
        buildsbtcontent should include ("project.organization")
        buildsbtcontent should include ("project.component.version")
        buildsbtcontent should include ("build.scalaVersion")
        buildsbtcontent should not include ("cozyPublishCar.value")
        buildsbtcontent should not include ("cozyPublishLocalCar.value")
        buildsbtcontent should include ("""cozyDelegateCommand := Seq("cozy")""")
        buildsbtcontent should not include ("junit-interface")
        buildsbtcontent should not include ("cats-core")
        buildsbtcontent should not include ("kittens")
        buildsbtcontent should not include ("spire")
        buildsbtcontent should not include ("circe-core")
        buildsbtcontent should not include ("cats-testkit")
        buildsbtcontent should not include ("discipline-core")
        buildsbtcontent should not include ("simplemodeling-model")
        buildsbtcontent should not include ("cncf-collaborator-api")
        buildsbtcontent should not include ("dependencyOverrides")
        buildsbtcontent should not include ("simpleModelingModelVersion")
        buildsbtcontent should not include ("cncfCollaboratorApiVersion")
        buildsbtcontent should not include ("object BuildVersion")
        buildsbtcontent should not include ("lazy val cozyBundleFactoryClassName = settingKey[Option[String]]")
        buildsbtcontent should not include ("""Some("domain.impl.ComponentFactory")""")
        buildsbtcontent should not include ("org.goldenport.cncf.component.Component$BundleFactory")
        projectyamlcontent should include ("""scalaVersion: "3.3.8"""")
        projectyamlcontent should include ("org.goldenport::goldenport-cncf:")
        projectyamlcontent should include ("org.scalatest::scalatest:3.2.10")
        projectyamlcontent should include ("manifest_metadata:")
        projectyamlcontent should include ("minimum:")
        projectyamlcontent should include ("tested:")
        projectyamlbuildcontent should include ("object ProjectYamlBuild")
        projectyamlbuildcontent should include ("build.dependencies.$scope")
        updateclasspathcontent should include ("sbt --batch 'export Runtime / fullClasspath'")
        val commonscriptcontent = Files.readString(commonscript)
        commonscriptcontent should include ("CNCF_VERSION_FILE")
        commonscriptcontent should include ("versions/cncf-version.conf")
        runservercontent should include ("$CNCF_LAUNCHER")
        runservercontent should not include ("sbt --batch")
        runserverdebugcontent should include ("-J-agentlib:jdwp")
        runserverdebugcontent should include ("runMain $CNCF_MAIN_CLASS")
        formdescriptorcontent should include ("sample.notice.post-notice")
        formdescriptorcontent should include ("successRedirect: /web/${component}/admin/entities/notice/${result.id}")
        formdescriptorcontent should include ("type: textarea")
        Files.exists(out.resolve("src/main/scala/domain/impl/ComponentFactory.scala")) shouldBe true
        pluginssbtcontent should include ("""addSbtPlugin("org.goldenport" % "sbt-cozy"""")
        pluginssbtcontent should include (""""SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"""")
        pluginssbtcontent should include ("SBT_COZY_VERSION")
        pluginssbtcontent should include ("0.1.14")
        pluginssbtcontent should include ("""addSbtPlugin("org.goldenport" % "sbt-cozy" % sbtCozyVersion)""")
      }

      "car-sbt-project preserves differing project files by writing bak files" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/car-sbt-project-bak")
        delete_recursively(out)
        Files.createDirectories(out.resolve("src/main/scala/domain/impl"))
        val buildsbt = out.resolve("build.sbt")
        val factory = out.resolve("src/main/scala/domain/impl/ComponentFactory.scala")
        Files.writeString(buildsbt, "custom build", StandardCharsets.UTF_8)
        Files.writeString(factory, "custom factory", StandardCharsets.UTF_8)

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array("car-sbt-project", "--save", out.toString.toString))

        Then("the generated project files satisfy the scaffold contract")
        Files.readString(buildsbt) shouldBe "custom build"
        Files.readString(factory) shouldBe "custom factory"
        Files.readString(out.resolve("build.sbt.bak")) should include ("enablePlugins(org.goldenport.cozy.CozyPlugin)")
        Files.readString(Paths.get(factory.toString + ".bak")) should include ("final class ComponentFactory extends Component.BundleFactory")
      }

      "car-sbt-project can skip project and src scaffold files" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/car-sbt-project-skip")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array("car-sbt-project", "--save", out.toString.toString, "--no-project-files"))

        Then("the generated project files satisfy the scaffold contract")
        Files.exists(out.resolve("build.sbt")) shouldBe false
        Files.exists(out.resolve("project/build.properties")) shouldBe false
        Files.exists(out.resolve("src/main/cozy/sample.cml")) shouldBe false
        Files.exists(out.resolve("src/main/car/component-descriptor.json")) shouldBe false
        Files.exists(out.resolve("src/main/web-inf/form.yaml")) shouldBe false
        Files.exists(out.resolve("src/main/scala/domain/impl/ComponentFactory.scala")) shouldBe false
      }

      "car-sbt-project can overwrite project and src scaffold files" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/car-sbt-project-overwrite")
        delete_recursively(out)
        Files.createDirectories(out.resolve("src/main/scala/domain/impl"))
        val buildsbt = out.resolve("build.sbt")
        val factory = out.resolve("src/main/scala/domain/impl/ComponentFactory.scala")
        Files.writeString(buildsbt, "custom build", StandardCharsets.UTF_8)
        Files.writeString(factory, "custom factory", StandardCharsets.UTF_8)

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array("car-sbt-project", "--save", out.toString.toString, "--overwrite-project-files"))

        Then("the generated project files satisfy the scaffold contract")
        Files.readString(buildsbt) should include ("enablePlugins(org.goldenport.cozy.CozyPlugin)")
        Files.readString(factory) should include ("final class ComponentFactory extends Component.BundleFactory")
        Files.exists(out.resolve("build.sbt.bak")) shouldBe false
        Files.exists(Paths.get(factory.toString + ".bak")) shouldBe false
      }

      "car-sbt-project generates CAR+SAR application scaffold when style is car-sar" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/test.dox")
        val out = base.resolve("target/test-generated/car-sar-sbt-project")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array("car-sbt-project", input.toString, "--save", out.toString.toString, "--style", "car-sar"))

        val rootbuild = out.resolve("build.sbt")
        val pluginssbt = out.resolve("project/plugins.sbt")
        val componentmodel = out.resolve("component/src/main/cozy/car-sar-sbt-project.cml")
        val componentweb = out.resolve("component/src/main/web-inf/form.yaml")
        val sourcecomponentdescriptor = out.resolve("component/src/main/car/component-descriptor.json")
        val subsystemdescriptor = out.resolve("subsystem/subsystem-descriptor.yaml")
        val repositorydreadme = out.resolve("repository.d/README.md")
        val subsystemscriptsreadme = out.resolve("subsystem/scripts/README.md")
        val generatedout = out.resolve("component-generated")

        Then("the generated project files satisfy the scaffold contract")
        withClue(s"root build.sbt not found: $rootbuild") {
        Files.exists(rootbuild) shouldBe true
      }
        withClue(s"plugins.sbt not found: $pluginssbt") {
        Files.exists(pluginssbt) shouldBe true
      }
        withClue(s"component model not found: $componentmodel") {
        Files.exists(componentmodel) shouldBe true
      }
        withClue(s"component web descriptor not found: $componentweb") {
        Files.exists(componentweb) shouldBe true
      }
        withClue(s"source component descriptor must be packaging-derived: $sourcecomponentdescriptor") {
        Files.exists(sourcecomponentdescriptor) shouldBe false
      }
        withClue(s"subsystem descriptor not found: $subsystemdescriptor") {
        Files.exists(subsystemdescriptor) shouldBe true
      }
        withClue(s"repository.d README not found: $repositorydreadme") {
        Files.exists(repositorydreadme) shouldBe true
      }
        withClue(s"subsystem scripts README not found: $subsystemscriptsreadme") {
        Files.exists(subsystemscriptsreadme) shouldBe true
      }
        withClue(s"component/build.sbt must not exist under CAR+SAR scaffold") {
        Files.exists(out.resolve("component/build.sbt")) shouldBe false
      }
        withClue(s"subsystem/build.sbt must not exist under CAR+SAR scaffold") {
        Files.exists(out.resolve("subsystem/build.sbt")) shouldBe false
      }

        val rootbuildcontent = Files.readString(rootbuild)
        val subsystemdescriptorcontent = Files.readString(subsystemdescriptor)
        rootbuildcontent should include ("lazy val component = project")
        rootbuildcontent should include ("lazy val subsystem = project")
        rootbuildcontent should not include ("lazy val cozyBundleFactoryClassName = settingKey[Option[String]]")
        rootbuildcontent should include ("libraryDependencies ++= ProjectYamlBuild.dependencies(componentMetadata)")
        rootbuildcontent should include ("build.scalaVersion")
        rootbuildcontent should include ("(component / publish).value")
        rootbuildcontent should include ("(subsystem / publish).value")
        rootbuildcontent should include ("(component / publishLocal).value")
        rootbuildcontent should include ("(subsystem / publishLocal).value")
        rootbuildcontent should include ("val componentpublication =")
        rootbuildcontent should include ("val subsystempublication =")
        rootbuildcontent should not include ("cozyPublishCar.value")
        rootbuildcontent should not include ("cozyPublishSar.value")
        rootbuildcontent should not include ("cozyPublishLocalCar.value")
        rootbuildcontent should not include ("cozyPublishLocalSar.value")
        rootbuildcontent should not include ("simplemodeling-model")
        rootbuildcontent should not include ("cncf-collaborator-api")
        rootbuildcontent should not include ("dependencyOverrides")
        rootbuildcontent should not include ("simpleModelingModelVersion")
        rootbuildcontent should not include ("cncfCollaboratorApiVersion")
        rootbuildcontent should not include ("org.goldenport.cncf.component.Component$BundleFactory")
        rootbuildcontent should include ("cozyGenerateApp")
        rootbuildcontent should include ("project.name")
        subsystemdescriptorcontent should include ("subsystem: car-sar-sbt-project")
        subsystemdescriptorcontent should include ("name: car-sar-sbt-project")
        subsystemdescriptorcontent should include ("name: textus-user-account")
        subsystemdescriptorcontent should include ("version: 0.1.1-SNAPSHOT")
        Files.readString(repositorydreadme) should include ("repository.d/textus-user-account.car")

        cozy.Cozy.main(Array("modeler-scala", componentmodel.toString, "--save", generatedout.toString.toString))
        val generatedscala = Files.find(generatedout, 32, (p, attr) => attr.isRegularFile && p.toString.endsWith(".scala"))
        try {
        withClue(s"generated component model did not produce Scala sources: $componentmodel") {
          generatedscala.findAny().isPresent shouldBe true
        }
      } finally {
        generatedscala.close()
      }
      }

      "car-sbt-project generates sample model scaffold when model file is omitted" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/car-sbt-project-scaffold")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array("car-sbt-project", "--save", out.toString.toString))

        val buildsbt = out.resolve("build.sbt")
        val projectyaml = out.resolve("project.yaml")
        val pluginssbt = out.resolve("project/plugins.sbt")
        val samplecml = out.resolve("src/main/cozy/sample.cml")
        val webdescriptor = out.resolve("src/main/web-inf/form.yaml")
        val sourcecomponentdescriptor = out.resolve("src/main/car/component-descriptor.json")
        Then("the generated project files satisfy the scaffold contract")
        withClue(s"build.sbt not found: $buildsbt") {
        Files.exists(buildsbt) shouldBe true
      }
        withClue(s"project.yaml not found: $projectyaml") {
        Files.exists(projectyaml) shouldBe true
      }
        withClue(s"plugins.sbt not found: $pluginssbt") {
        Files.exists(pluginssbt) shouldBe true
      }
        withClue(s"sample model not found: $samplecml") {
        Files.exists(samplecml) shouldBe true
      }
        withClue(s"web descriptor not found: $webdescriptor") {
        Files.exists(webdescriptor) shouldBe true
      }
        withClue(s"source component descriptor must be packaging-derived: $sourcecomponentdescriptor") {
        Files.exists(sourcecomponentdescriptor) shouldBe false
      }
      }

      "car-sbt-project accepts component scaffold metadata parameters" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/car-sbt-project-scaffold-parameters")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array(
        "car-sbt-project",
        "--save", out.toString.toString,
        "--component", "UserNotification",
        "--package", "org.simplemodeling.textus.usernotification",
        "--organization", "org.textus",
        "--name", "textus-user-notification",
        "--version", "0.1.0-SNAPSHOT",
        "--bounded-context", "user-notification",
        "--domain", "notification",
        "--gitignore",
        "--readme",
        "--tests"
        ))

        val buildsbt = out.resolve("build.sbt")
        val projectyaml = out.resolve("project.yaml")
        val model = out.resolve("src/main/cozy/textus-user-notification.cml")
        val factory = out.resolve("src/main/scala/org/simplemodeling/textus/usernotification/impl/ComponentFactory.scala")
        val readme = out.resolve("README.md")
        val gitignore = out.resolve(".gitignore")
        val spec = out.resolve("src/test/scala/org/simplemodeling/textus/usernotification/ComponentFactorySpec.scala")
        val webdescriptor = out.resolve("src/main/web-inf/form.yaml")
        val sourcecomponentdescriptor = out.resolve("src/main/car/component-descriptor.json")

        Then("the generated project files satisfy the scaffold contract")
        withClue(s"build.sbt not found: $buildsbt") {
        Files.exists(buildsbt) shouldBe true
      }
        withClue(s"project.yaml not found: $projectyaml") {
        Files.exists(projectyaml) shouldBe true
      }
        withClue(s"model not found: $model") {
        Files.exists(model) shouldBe true
      }
        withClue(s"factory not found: $factory") {
        Files.exists(factory) shouldBe true
      }
        withClue(s"README not found: $readme") {
        Files.exists(readme) shouldBe true
      }
        withClue(s".gitignore not found: $gitignore") {
        Files.exists(gitignore) shouldBe true
      }
        withClue(s"ComponentFactorySpec not found: $spec") {
        Files.exists(spec) shouldBe true
      }
        withClue(s"source component descriptor must be packaging-derived: $sourcecomponentdescriptor") {
        Files.exists(sourcecomponentdescriptor) shouldBe false
      }

        val buildsbtcontent = Files.readString(buildsbt)
        val projectyamlcontent = Files.readString(projectyaml)
        val modelcontent = Files.readString(model)
        val factorycontent = Files.readString(factory)
        val webcontent = Files.readString(webdescriptor)
        buildsbtcontent should include ("ProjectYamlBuild.requiredValue")
        buildsbtcontent should include ("libraryDependencies ++= ProjectYamlBuild.dependencies")
        buildsbtcontent should not include ("""organization := "org.textus"""")
        buildsbtcontent should not include ("""name := "textus-user-notification"""")
        buildsbtcontent should not include ("""version := "0.1.0-SNAPSHOT"""")
        buildsbtcontent should not include ("object BuildVersion")
        buildsbtcontent should not include ("""Some("org.simplemodeling.textus.usernotification.impl.ComponentFactory")""")
        projectyamlcontent should include ("""organization: "org.textus"""")
        projectyamlcontent should include ("""name: "textus-user-notification"""")
        projectyamlcontent should include ("""version: "0.1.0-SNAPSHOT"""")
        projectyamlcontent should include ("""boundedContext: "user-notification"""")
        projectyamlcontent should include ("""domain: "notification"""")
        modelcontent should include ("## UserNotification")
        modelcontent should include ("org.simplemodeling.textus.usernotification")
        factorycontent should include ("package org.simplemodeling.textus.usernotification.impl")
        factorycontent should include ("import org.simplemodeling.textus.usernotification.UserNotificationComponent")
        webcontent should include ("textus-user-notification.notice.post-notice")
        Files.readString(gitignore) should include ("target/")
        Files.readString(readme) should include ("textus-user-notification")
        Files.readString(spec) should include ("new impl.ComponentFactory()")
      }

      "init component creates a configured CAR project from config" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/init-component-config")
        delete_recursively(out)
        Files.createDirectories(out.getParent)
        val config = base.resolve("target/test-generated/init-component-config.yaml")
        Files.writeString(
        config,
        """project:
          |  name: textus-knowledge-editor
          |  organization: org.goldenport
          |  component:
          |    name: textus-knowledge-editor
          |    displayName: Textus Knowledge Editor
          |    version: 0.1.0-SNAPSHOT
          |    kind: car
          |  scaffold:
          |    readme: true
          |    tests: true
          |cml:
          |  package: org.goldenport.textus.knowledge.editor
          |  component:
          |    name: TextusKnowledgeEditor
          |""".stripMargin,
        StandardCharsets.UTF_8
        )

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array("init", "component", "--save", out.toString.toString, "--config", config.toString.toString))

        val buildsbt = out.resolve("build.sbt")
        val projectyaml = out.resolve("project.yaml")
        val projectyamlbuild = out.resolve("project/ProjectYamlBuild.scala")
        val model = out.resolve("src/main/cozy/textus-knowledge-editor.cml")
        val factory = out.resolve("src/main/scala/org/goldenport/textus/knowledge/editor/impl/ComponentFactory.scala")
        val spec = out.resolve("src/test/scala/org/goldenport/textus/knowledge/editor/ComponentFactorySpec.scala")
        val readme = out.resolve("README.md")
        val sourcecomponentdescriptor = out.resolve("src/main/car/component-descriptor.json")

        Then("the generated project files satisfy the scaffold contract")
        withClue(s"build.sbt not found: $buildsbt") {
        Files.exists(buildsbt) shouldBe true
      }
        withClue(s"project.yaml not found: $projectyaml") {
        Files.exists(projectyaml) shouldBe true
      }
        withClue(s"ProjectYamlBuild.scala not found: $projectyamlbuild") {
        Files.exists(projectyamlbuild) shouldBe true
      }
        withClue(s"model not found: $model") {
        Files.exists(model) shouldBe true
      }
        withClue(s"factory not found: $factory") {
        Files.exists(factory) shouldBe true
      }
        withClue(s"spec not found: $spec") {
        Files.exists(spec) shouldBe true
      }
        withClue(s"README not found: $readme") {
        Files.exists(readme) shouldBe true
      }
        withClue(s"source component descriptor must be packaging-derived: $sourcecomponentdescriptor") {
        Files.exists(sourcecomponentdescriptor) shouldBe false
      }

        val buildsbtcontent = Files.readString(buildsbt)
        val projectyamlcontent = Files.readString(projectyaml)
        val modelcontent = Files.readString(model)
        buildsbtcontent should include ("ProjectYamlBuild.requiredValue")
        buildsbtcontent should include ("libraryDependencies ++= ProjectYamlBuild.dependencies")
        buildsbtcontent should not include ("""organization := "org.goldenport"""")
        buildsbtcontent should not include ("""name := "textus-knowledge-editor"""")
        buildsbtcontent should not include ("""version := "0.1.0-SNAPSHOT"""")
        buildsbtcontent should not include ("cozyPublishCar.value")
        buildsbtcontent should not include ("cozyPublishLocalCar.value")
        buildsbtcontent should not include ("dependencyOverrides")
        projectyamlcontent should include ("""name: "textus-knowledge-editor"""")
        projectyamlcontent should include ("""title: "Textus Knowledge Editor"""")
        projectyamlcontent should include ("""scalaPackage: "org.goldenport.textus.knowledge.editor"""")
        projectyamlcontent should include ("""scalaVersion: "3.3.8"""")
        projectyamlcontent should include ("org.goldenport::goldenport-cncf:")
        projectyamlcontent should include ("org.scalatest::scalatest:3.2.10")
        projectyamlcontent should include ("manifest_metadata:")
        projectyamlcontent should include ("""minimum: """)
        projectyamlcontent should include ("""modules:""")
        modelcontent should include ("## TextusKnowledgeEditor")
        modelcontent should include ("org.goldenport.textus.knowledge.editor")
      }

      "init component CLI options override config values" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/init-component-override")
        delete_recursively(out)
        Files.createDirectories(out.getParent)
        val config = base.resolve("target/test-generated/init-component-override.yaml")
        Files.writeString(
        config,
        """project:
          |  name: wrong-name
          |  organization: org.wrong
          |  scalaPackage: org.wrong
          |  component:
          |    name: wrong-component
          |    displayName: Wrong Name
          |    version: 0.0.1
          |    kind: car
          |""".stripMargin,
        StandardCharsets.UTF_8
        )

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array(
        "init",
        "component",
        "--save", out.toString.toString,
        "--config", config.toString.toString,
        "--name", "override-artifact",
        "--component-name", "OverrideComponent",
        "--display-name", "Override Component",
        "--organization", "org.override",
        "--package", "org.override.component",
        "--version", "0.2.0-SNAPSHOT"
        ))

        val buildsbtcontent = Files.readString(out.resolve("build.sbt"))
        val projectyamlcontent = Files.readString(out.resolve("project.yaml"))
        val modelcontent = Files.readString(out.resolve("src/main/cozy/override-artifact.cml"))
        Then("the generated project files satisfy the scaffold contract")
        buildsbtcontent should include ("ProjectYamlBuild.requiredValue")
        buildsbtcontent should not include ("""organization := "org.override"""")
        buildsbtcontent should not include ("""name := "override-artifact"""")
        buildsbtcontent should not include ("""version := "0.2.0-SNAPSHOT"""")
        projectyamlcontent should include ("""organization: "org.override"""")
        projectyamlcontent should include ("""name: "override-artifact"""")
        projectyamlcontent should include ("""version: "0.2.0-SNAPSHOT"""")
        projectyamlcontent should include ("""title: "Override Component"""")
        projectyamlcontent should include ("""scalaPackage: "org.override.component"""")
        modelcontent should include ("## OverrideComponent")
        modelcontent should include ("org.override.component")
      }

      "init component accepts service entity and operation scaffold parameters" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/init-component-surface-parameters")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array(
        "init",
        "component",
        "--save", out.toString.toString,
        "--name", "textus-art-scene",
        "--component-name", "ArtScene",
        "--service-name", "ExhibitionCandidate",
        "--entity", "Exhibition",
        "--command-operation", "RegisterFacility",
        "--query-operation", "ListCandidates",
        "--display-name", "Textus Art Scene",
        "--organization", "org.textus",
        "--package", "org.simplemodeling.textus.artscene",
        "--version", "0.1.0-SNAPSHOT"
        ))

        val modelcontent = Files.readString(out.resolve("src/main/cozy/textus-art-scene.cml"))
        val factorycontent = Files.readString(out.resolve("src/main/scala/org/simplemodeling/textus/artscene/impl/ComponentFactory.scala"))
        val webcontent = Files.readString(out.resolve("src/main/web-inf/form.yaml"))
        Then("the generated project files satisfy the scaffold contract")
        modelcontent should include ("## ArtScene")
        modelcontent should include ("## ExhibitionCandidate")
        modelcontent should include ("## Exhibition")
        modelcontent should include ("| name        | name     | 1")
        modelcontent should include ("#### RegisterFacility")
        modelcontent should include ("#### ListCandidates")
        modelcontent should include ("- input :: RegisterFacility")
        modelcontent should include ("- output :: RegisterFacilityResult")
        modelcontent should include ("- input :: ListCandidates")
        modelcontent should include ("- output :: ListCandidatesResult")
        modelcontent should include ("## RegisterFacilityResult")
        modelcontent should include ("## ListCandidatesResult")
        modelcontent should include ("OperationResult")
        factorycontent should include ("override val ExhibitionCandidate: ArtSceneComponent.ExhibitionCandidateServiceFactory")
        factorycontent should include ("final class DefaultExhibitionCandidateServiceFactory")
        factorycontent should include ("override def createRegisterFacilityActionCall")
        factorycontent should include ("override def createListCandidatesActionCall")
        webcontent should include ("textus-art-scene.exhibition-candidate.register-facility")
        webcontent should include ("textus-art-scene.exhibition-candidate.list-candidates")
        webcontent should include ("entity.exhibition")
      }

      "init component reads service entity and operation scaffold parameters from config" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/init-component-surface-config")
        delete_recursively(out)
        Files.createDirectories(out.getParent)
        val config = base.resolve("target/test-generated/init-component-surface-config.yaml")
        Files.writeString(
        config,
        """project:
          |  name: textus-art-scene
          |  organization: org.textus
          |  component:
          |    displayName: Textus Art Scene
          |    version: 0.1.0-SNAPSHOT
          |    kind: car
          |cml:
          |  package: org.simplemodeling.textus.artscene
          |  component:
          |    name: ArtScene
          |  service:
          |    name: ExhibitionCandidate
          |  entity:
          |    name: Exhibition
          |  operation:
          |    command: RegisterFacility
          |    query: ListCandidates
          |""".stripMargin,
        StandardCharsets.UTF_8
        )

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array("init", "component", "--save", out.toString.toString, "--config", config.toString.toString))

        val modelcontent = Files.readString(out.resolve("src/main/cozy/textus-art-scene.cml"))
        Then("the generated project files satisfy the scaffold contract")
        modelcontent should include ("## ArtScene")
        modelcontent should include ("## ExhibitionCandidate")
        modelcontent should include ("## Exhibition")
        modelcontent should include ("| name        | name     | 1")
        modelcontent should include ("#### RegisterFacility")
        modelcontent should include ("#### ListCandidates")
        modelcontent should include ("## RegisterFacilityResult")
        modelcontent should include ("## ListCandidatesResult")
      }

      "init component can create a CAR plus SAR application layout" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/init-component-car-sar")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array(
        "init",
        "component",
        "--save", out.toString.toString,
        "--kind", "car-sar",
        "--name", "textus-knowledge-editor",
        "--component-name", "TextusKnowledgeEditor",
        "--package", "org.goldenport.textus.knowledge.editor"
        ))

        Then("the generated project files satisfy the scaffold contract")
        Files.exists(out.resolve("build.sbt")) shouldBe true
        Files.exists(out.resolve("component/src/main/cozy/textus-knowledge-editor.cml")) shouldBe true
        Files.exists(out.resolve("component/src/main/car/component-descriptor.json")) shouldBe false
        Files.exists(out.resolve("component/src/main/web-inf/web.yaml")) shouldBe true
        Files.exists(out.resolve("component/src/main/web-inf/form.yaml")) shouldBe true
        Files.exists(out.resolve("component/project.yaml")) shouldBe true
        Files.exists(out.resolve("project/ProjectYamlBuild.scala")) shouldBe true
        Files.exists(out.resolve("subsystem/subsystem-descriptor.yaml")) shouldBe true
        val projectyamlcontent = Files.readString(out.resolve("component/project.yaml"))
        projectyamlcontent should include ("""name: "textus-knowledge-editor"""")
        projectyamlcontent should include ("""scalaVersion: "3.3.8"""")
        projectyamlcontent should include ("org.goldenport::goldenport-cncf:")
        projectyamlcontent should include ("""packaging:""")
      }

      "car-sbt-project generates web descriptor scaffold from CML WEB metadata" in {
        Given("a modeler scaffold source or requested project layout")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val out = base.resolve("target/test-generated/car-sbt-project-web-metadata")
        delete_recursively(out)
        Files.createDirectories(out)
        val input = out.resolve("web-metadata.cml")
        Files.writeString(
        input,
        """# COMPONENT
          |
          |## Sample
          |
          |# WEB
          |
          |expose:
          |  sample.notice.post-notice: public
          |form:
          |  sample.notice.post-notice:
          |    enabled: true
          |    stayOnError: true
          |    resultTemplate: |
          |      <article>
          |        <h2>${operation.label}</h2>
          |        <textus-property-list source="result"></textus-property-list>
          |      </article>
          |
          |# SERVICE
          |
          |## Notice
          |""".stripMargin,
        StandardCharsets.UTF_8
        )

        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array("car-sbt-project", input.toString, "--save", out.toString.toString))

        val webdescriptor = out.resolve("src/main/web-inf/form.yaml")
        val content = Files.readString(webdescriptor)
        Then("the generated project files satisfy the scaffold contract")
        content should include ("sample.notice.post-notice: public")
        content should include ("stayOnError: true")
        content should include ("resultTemplate: |")
        content should include ("""<textus-property-list source="result"></textus-property-list>""")
        content should not include ("sample.notice.search-notices")
      }

      "cozy help lists commands without entering repl" in {
        Given("a modeler scaffold source or requested project layout")
        val out = new ByteArrayOutputStream()

        Console.withOut(new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
        When("Cozy generates the project scaffold")
        cozy.Cozy.main(Array("--help"))
      }

        val help = out.toString(StandardCharsets.UTF_8.name())
        Then("the generated project files satisfy the scaffold contract")
        help should include ("Usage:")
        help should include ("Commands:")
        help should include ("init component")
        help should include ("car-sbt-project")
        help should include ("lint build")
        help should include ("lint cml")
        help should include ("lint abi")
        help should include ("lint car")
        help should include ("--service-name")
        help should include ("--command-operation")
        help should include ("--query-operation")
        help should include ("--style car|car-sar")
        help should include ("modeler-scala")
        help should include ("package-car")
        help should include ("sbt-bridge")
        help should include ("With no arguments, cozy starts the interactive REPL.")
        help should not include ("cozy>")
      }

    }
  }
}
