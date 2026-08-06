package cozy.modeler

import java.nio.file.{Files, Path}
import cozy.config.CozyProjectYamlConfig
import cozy.lint.CozyCarLint
import cozy.scaffold.CozyScaffold
import org.goldenport.cncf.component.identity.ComponentIdentityProjection
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  7, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56ProjectIdentityContractSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e5 = afterWord(
    "in spec:phase-56-component-identity-project-contract, example:E5, rules:CID01-R1,CID01-R2, phase:56, slice:CID-01D"
  )
  private val _e5_cid03a = afterWord(
    "in spec:phase-56-component-identity-project-contract, example:E5, rules:CID01-R1,CID01-R2, phase:56, slice:CID-03A"
  )
  private val _e5_cid03b = afterWord(
    "in spec:phase-56-component-identity-project-contract, example:E5, rules:CID01-R1,CID01-R2, phase:56, slice:CID-03B"
  )
  private val _e5_cid03c = afterWord(
    "in spec:phase-56-component-identity-project-contract, example:E5, rules:CID01-R1,CID01-R2, phase:56, slice:CID-03C"
  )
  private val _e6 = afterWord(
    "in spec:phase-56-component-identity-project-contract, example:E6, rules:CID01-R3,CID01-R4, phase:56, slice:CID-01D"
  )
  private val _e6_cid03b = afterWord(
    "in spec:phase-56-component-identity-project-contract, example:E6, rules:CID01-R3,CID01-R4, phase:56, slice:CID-03B"
  )
  private val _e7 = afterWord(
    "in spec:phase-56-component-identity-project-contract, example:E7, rules:CID01-R5,CID01-R6, phase:56, slice:CID-01D"
  )
  private val _identity_codes = Set(
    "CAR_COMPONENT_IDENTITY_MIGRATION_REQUIRED",
    "CAR_COMPONENT_IDENTITY_MIGRATION_DEFERRED",
    "CAR_COMPONENT_IDENTITY_DISAGREEMENT"
  )

  "E5 canonical project identity authoring" should {
    "E5 load one canonical-only project.yaml as project metadata" must _e5_cid03a {
      "E5 loads canonical metadata with exact fields" in {
        Given("E5 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,CID01-R2; Example: E5; one valid canonical-only project.yaml with namespace, id, and version")
        _with_temp_dir("cozy-phase56-cid01-e5-metadata") { directory =>
          val projectfile = directory.resolve("project.yaml")
          Files.writeString(
            projectfile,
            _canonical_project_yaml(
              "org.simplemodeling.textus",
              "UserAccount",
              "1.0.0-SNAPSHOT"
            )
          )

          When("E5 Cozy loads project metadata from that canonical-only file")
          val config = CozyProjectYamlConfig.loadProjectMetadata(directory)

          Then("E5 metadata retains the exact canonical namespace, id, and version")
          config.value("project.namespace") shouldBe Some("org.simplemodeling.textus")
          config.value("project.id") shouldBe Some("UserAccount")
          config.value("project.component.version") shouldBe Some("1.0.0-SNAPSHOT")
          config.value("project.name") shouldBe None
          config.value("project.organization") shouldBe None
          config.value("project.scalaPackage") shouldBe None
          config.value("project.component.name") shouldBe None
          config.value("project.component.className") shouldBe None

          And("E5 canonical project identity admits one shared ComponentId and all six projections")
          config.projectIdentity.map(_.map { identity =>
            (
              identity.componentId.qualifiedName(),
              identity.namespace.value(),
              identity.localId.value(),
              identity.qualifiedId,
              _projection_map(identity.projection)
            )
          }) shouldBe Right(Some(
            (
              "org.simplemodeling.textus.UserAccount",
              "org.simplemodeling.textus",
              "UserAccount",
              "org.simplemodeling.textus.UserAccount",
              Map(
                "qualified" -> "org.simplemodeling.textus.UserAccount",
                "organization" -> "org.simplemodeling.textus",
                "artifact" -> "textus-user-account",
                "jvmPackage" -> "org.simplemodeling.textus.useraccount",
                "generatedClass" -> "UserAccountComponent",
                "path" -> "user-account"
              )
            )
          ))
        }
      }
    }

    "E5 distinguish a legacy project with no canonical identity" must _e5_cid03a {
      "E5 legacy metadata remains absent from canonical identity admission" in {
        Given("E5 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,CID01-R2; Example: E5; one legacy project.yaml without project.namespace or project.id")
        _with_temp_dir("cozy-phase56-cid03a-e5-legacy") { directory =>
          Files.writeString(directory.resolve("project.yaml"), _legacy_project_yaml("0.0.1"))

          When("E5 Cozy admits the legacy project identity")
          val identity = CozyProjectYamlConfig.loadProjectMetadata(directory).projectIdentity

          Then("E5 legacy absence is represented as Right(None)")
          identity shouldBe Right(None)
        }
      }
    }

    "E5 reject loaded YAML null and empty canonical fields with shared required errors" must _e5_cid03a {
      "E5 loaded YAML null and empty namespace or id values retain exact required codes" in {
        Given("E5 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,CID01-R2; Example: E5; loaded project.yaml cases with null or empty canonical namespace and id")
        val cases = Vector(
          ("null-namespace", """project:
            |  namespace: null
            |  id: UserAccount
            |""".stripMargin, "component.identity.namespace.required"),
          ("empty-namespace", """project:
            |  namespace:
            |  id: UserAccount
            |""".stripMargin, "component.identity.namespace.required"),
          ("null-id", """project:
            |  namespace: org.simplemodeling.textus
            |  id: null
            |""".stripMargin, "component.identity.local-id.required"),
          ("empty-id", """project:
            |  namespace: org.simplemodeling.textus
            |  id:
            |""".stripMargin, "component.identity.local-id.required")
        )
        _with_temp_dir("cozy-phase56-cid03a-e5-required") { directory =>
          When("E5 Cozy loads each project.yaml and admits its canonical identity")
          val observed = cases.map { case (label, yaml, _) =>
            val projectdir = directory.resolve(label)
            Files.createDirectories(projectdir)
            Files.writeString(projectdir.resolve("project.yaml"), yaml)
            CozyProjectYamlConfig.loadProjectMetadata(projectdir).projectIdentity.left.map(_.code())
          }

          Then("E5 every authored null or empty field returns its exact shared required error")
          observed shouldBe cases.map { case (_, _, code) => Left(code) }
        }
      }
    }

    "E5 reject partial and invalid canonical identity authoring with shared errors" must _e5_cid03a {
      "E5 partial and invalid canonical fields retain exact shared error codes" in {
        Given("E5 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,CID01-R2; Example: E5; partial and malformed canonical project fields")
        val cases = Vector(
          CozyProjectYamlConfig.Config(Map("project.id" -> "UserAccount"), Map.empty) -> "component.identity.namespace.required",
          CozyProjectYamlConfig.Config(Map("project.namespace" -> "org.simplemodeling.textus"), Map.empty) -> "component.identity.local-id.required",
          CozyProjectYamlConfig.Config(Map("project.namespace" -> "org..textus", "project.id" -> "UserAccount"), Map.empty) -> "component.identity.namespace.segment-format",
          CozyProjectYamlConfig.Config(Map("project.namespace" -> "org.simplemodeling.textus", "project.id" -> "user-account"), Map.empty) -> "component.identity.local-id.format"
        )

        When("E5 Cozy admits each partial or malformed canonical identity")
        val observed = cases.map { case (config, _) => config.projectIdentity.left.map(_.code()) }

        Then("E5 each admission result carries the exact shared error code")
        observed shouldBe cases.map { case (_, code) => Left(code) }
      }
    }

    "E5 keep identity equality independent of version and display metadata" must _e5_cid03a {
      "E5 equal canonical pairs produce equal ProjectIdentity values" in {
        Given("E5 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,CID01-R2; Example: E5; two configs with identical namespace/id and differing version/display/legacy fields")
        val first = CozyProjectYamlConfig.Config(
          Map(
            "project.namespace" -> "org.simplemodeling.textus",
            "project.id" -> "UserAccount",
            "project.component.version" -> "1.0.0-SNAPSHOT",
            "project.name" -> "textus-user-account",
            "project.organization" -> "org.simplemodeling.textus",
            "project.component.name" -> "legacy-component-one",
            "project.component.displayName" -> "Legacy Component One"
          ),
          Map.empty
        )
        val second = CozyProjectYamlConfig.Config(
          Map(
            "project.namespace" -> "org.simplemodeling.textus",
            "project.id" -> "UserAccount",
            "project.component.version" -> "2.0.0",
            "project.name" -> "another-display-name",
            "project.scalaPackage" -> "another.package",
            "project.component.className" -> "AnotherComponent",
            "project.component.name" -> "legacy-component-two",
            "project.component.displayName" -> "Legacy Component Two"
          ),
          Map.empty
        )

        When("E5 Cozy admits both canonical project identities")
        val identities = (first.projectIdentity, second.projectIdentity)

        Then("E5 version, display, and legacy metadata do not affect identity equality")
        identities._1 shouldBe identities._2
      }
    }

    "E5 route the same canonical config through production scaffold generation" must _e5_cid03b {
      "E5 emits canonical identity through the scaffold chain" in {
        Given("E5 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,CID01-R2; Example: E5; one canonical-only config path reused by component init and CAR YAML generation")
        _with_temp_dir("cozy-phase56-cid01-e5-scaffold") { directory =>
          val projectfile = directory.resolve("project.yaml")
          Files.writeString(
            projectfile,
            _canonical_project_yaml(
              "org.simplemodeling.textus",
              "UserAccount",
              "1.0.0-SNAPSHOT"
            )
          )
          val savepath = directory.resolve("generated")

          When("E5 Cozy creates ComponentInitConfig then carProjectYaml and parses the result")
          val init = CozyScaffold.ComponentInitConfig.create(
            List("--save", savepath.toString, "--config", projectfile.toString)
          )
          val factorysource = CozyScaffold.carComponentFactorySource(init.scaffold)
          val generated = CozyProjectYamlConfig.parse(
            CozyScaffold.carProjectYaml(init, CozyScaffold.CarDependencyVersions.default).
              linesIterator.toVector
          )

          Then("E5 the scaffold carries the exact shared identity projections")
          init.projectidentity.map(_.qualifiedId) shouldBe
            Some("org.simplemodeling.textus.UserAccount")
          init.scaffold.componentName shouldBe "UserAccount"
          init.scaffold.artifactName shouldBe "textus-user-account"
          init.scaffold.packageName shouldBe "org.simplemodeling.textus.useraccount"
          init.scaffold.organization shouldBe "org.simplemodeling.textus"
          init.scaffold.version shouldBe "1.0.0-SNAPSHOT"

          And("E5 the generated project identity and legacy-field absence match the contract")
          generated.value("project.namespace") shouldBe Some("org.simplemodeling.textus")
          generated.value("project.id") shouldBe Some("UserAccount")
          generated.value("project.kind") shouldBe Some("car")
          generated.value("project.component.displayName") shouldBe Some("Textus User Account")
          generated.value("project.component.version") shouldBe Some("1.0.0-SNAPSHOT")
          generated.mapUnder("project.identity") shouldBe Map(
            "qualified" -> "org.simplemodeling.textus.UserAccount",
            "organization" -> "org.simplemodeling.textus",
            "artifact" -> "textus-user-account",
            "jvmPackage" -> "org.simplemodeling.textus.useraccount",
            "generatedClass" -> "UserAccountComponent",
            "path" -> "user-account"
          )
          generated.value("project.name") shouldBe None
          generated.value("project.title") shouldBe None
          generated.value("project.organization") shouldBe None
          generated.value("project.scalaPackage") shouldBe None
          generated.value("project.component.name") shouldBe None
          generated.value("project.component.className") shouldBe None

          And("E5 the generated Scala source and path use the shared JVM projection")
          factorysource.linesIterator.toVector should contain (
            "package org.simplemodeling.textus.useraccount.impl"
          )
          factorysource.linesIterator.toVector should contain (
            "import org.simplemodeling.textus.useraccount.UserAccountComponent"
          )
          init.scaffold.scalaPackageDir("src/main/scala").toString shouldBe
            "src/main/scala/org/simplemodeling/textus/useraccount"
        }
      }
    }

    "E5 reject an authored partial canonical identity before scaffold materialization" must _e5_cid03b {
      "E5 rejects partial canonical identity despite usable legacy decoys" in {
        Given("E5 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,CID01-R2; Example: E5; one partial canonical project.yaml with usable legacy identity decoys")
        _with_temp_dir("cozy-phase56-cid03b-e5-partial") { directory =>
          val projectfile = directory.resolve("project.yaml")
          val savepath = directory.resolve("generated")
          Files.writeString(
            projectfile,
            """project:
              |  namespace:
              |  name: legacy-user-account
              |  organization: org.legacy.textus
              |  scalaPackage: org.legacy.textus.useraccount
              |  component:
              |    name: legacy-user-account
              |    className: LegacyUserAccount
              |""".stripMargin
          )

          When("E5 Cozy creates ComponentInitConfig with the partial canonical config and requested save path")
          val failure = intercept[RuntimeException] {
            CozyScaffold.ComponentInitConfig.create(
              List("--save", savepath.toString, "--config", projectfile.toString)
            )
          }

          Then("E5 the exact shared namespace-required error is visible and the save path is not materialized")
          failure.getMessage should include ("component.identity.namespace.required")
          Files.exists(savepath) shouldBe false
        }
      }
    }

    "E5 wire generated canonical builds through one admission evidence setting" must _e5_cid03c {
      "E5 generated single-CAR and CAR+SAR builds share canonical admission and manifest wiring" in {
        Given("E5 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R1,CID01-R2; Example: E5; generated single-CAR and CAR+SAR build templates")

        When("E5 Cozy renders the build and ProjectYamlBuild templates")
        val single = CozyScaffold.carBuildSbt()
        val carsar = CozyScaffold.carSarBuildSbt(
          CozyScaffold.CarScaffoldConfig.create(Nil, java.nio.file.Paths.get("sample"), CozyScaffold.ProjectLayoutStyle.CarSar),
          CozyScaffold.CarDependencyVersions.default
        )
        val helper = CozyScaffold.carProjectYamlBuildScala()

        Then("E5 both build forms obtain canonical coordinates and manifest metadata from one admitted evidence value")
        single should include ("projectIdentityEvidence := ProjectYamlBuild.admitted")
        single should include ("organization := ProjectYamlBuild.organization(projectIdentityEvidence.value")
        single should include ("moduleName := ProjectYamlBuild.moduleName(projectIdentityEvidence.value")
        single should include ("cozyCarName := ProjectYamlBuild.carBaseName(projectIdentityEvidence.value")
        single should include ("cozyManifestMetadata ++= ProjectYamlBuild.manifestMetadata(projectIdentityEvidence.value")
        carsar should include ("componentIdentityEvidence := ProjectYamlBuild.admitted")
        carsar should include ("cozyCarName := ProjectYamlBuild.carBaseName(componentIdentityEvidence.value")
        carsar should include ("cozyManifestMetadata ++= ProjectYamlBuild.manifestMetadata(componentIdentityEvidence.value")
        helper should include ("CozyProjectIdentityContract.requireAdmitted")
        helper should include ("if (evidence.shape == \"canonical\") evidence.manifestMetadata")

        And("E5 direct legacy requirements remain isolated to the legacy fallback helper branch")
        helper should include ("evidence.organization.getOrElse(requiredValue(config, \"project.organization\"))")
        helper should include ("evidence.moduleName.getOrElse(requiredValue(config, \"project.name\"))")
        helper should include ("Map(\"component\" -> requiredValue(config, \"project.component.name\"))")
      }
    }
  }

  "E6 deterministic acronym and digit projections" should {
    "E6 project exact adapter projections through the shared identity ABI" must _e6 {
      "E6 emits literal maps for all acronym and digit IDs through the adapter" in {
        Given("E6 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R3,CID01-R4; Example: E6; canonical inputs project only through the shared identity ABI")
        val cases = Vector(
          (
            "HTTPGateway",
            Map(
              "qualified" -> "org.simplemodeling.textus.HTTPGateway",
              "organization" -> "org.simplemodeling.textus",
              "artifact" -> "textus-http-gateway",
              "jvmPackage" -> "org.simplemodeling.textus.httpgateway",
              "generatedClass" -> "HTTPGatewayComponent",
              "path" -> "http-gateway"
            )
          ),
          (
            "OAuth2Client",
            Map(
              "qualified" -> "org.simplemodeling.textus.OAuth2Client",
              "organization" -> "org.simplemodeling.textus",
              "artifact" -> "textus-oauth2-client",
              "jvmPackage" -> "org.simplemodeling.textus.oauth2client",
              "generatedClass" -> "OAuth2ClientComponent",
              "path" -> "oauth2-client"
            )
          ),
          (
            "HTTP2Gateway",
            Map(
              "qualified" -> "org.simplemodeling.textus.HTTP2Gateway",
              "organization" -> "org.simplemodeling.textus",
              "artifact" -> "textus-http2-gateway",
              "jvmPackage" -> "org.simplemodeling.textus.http2gateway",
              "generatedClass" -> "HTTP2GatewayComponent",
              "path" -> "http2-gateway"
            )
          )
        )

        When("E6 Cozy projects every canonical identity through ProjectIdentityAdapter")
        val observed = cases.map { case (localid, _) =>
          ProjectIdentityAdapter.projection(
            ProjectIdentityInput("org.simplemodeling.textus", localid)
          ).map(_projection_map)
        }

        Then("E6 every shared projection map is emitted exactly")
        observed shouldBe cases.map { case (_, expected) => Right(expected) }
      }
    }

    "E6 preserve shared safe parse failures through the adapter" must _e6 {
      "E6 retains invalid namespace and local ID error codes" in {
        Given("E6 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R3,CID01-R4; Example: E6; invalid canonical namespace and local ID inputs")

        When("E6 Cozy projects invalid canonical identity inputs through ProjectIdentityAdapter")
        val invalidnamespace = ProjectIdentityAdapter.projection(
          ProjectIdentityInput("org..textus", "UserAccount")
        ).left.map(_.code())
        val invalidlocalid = ProjectIdentityAdapter.projection(
          ProjectIdentityInput("org.simplemodeling.textus", "user-account")
        ).left.map(_.code())

        Then("E6 the exact shared error codes are retained")
        invalidnamespace shouldBe Left("component.identity.namespace.segment-format")
        invalidlocalid shouldBe Left("component.identity.local-id.format")
      }
    }

    "E6 project identity projections are exact for HTTPGateway OAuth2Client and HTTP2Gateway" must _e6_cid03b {
      "E6 emits literal maps for all acronym and digit IDs" in {
        Given("E6 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R3,CID01-R4; Example: E6; canonical-only configs and literal expected projection maps for three local ids")
        val cases = Vector(
          (
            "HTTPGateway",
            Map(
              "qualified" -> "org.simplemodeling.textus.HTTPGateway",
              "organization" -> "org.simplemodeling.textus",
              "artifact" -> "textus-http-gateway",
              "jvmPackage" -> "org.simplemodeling.textus.httpgateway",
              "generatedClass" -> "HTTPGatewayComponent",
              "path" -> "http-gateway"
            )
          ),
          (
            "OAuth2Client",
            Map(
              "qualified" -> "org.simplemodeling.textus.OAuth2Client",
              "organization" -> "org.simplemodeling.textus",
              "artifact" -> "textus-oauth2-client",
              "jvmPackage" -> "org.simplemodeling.textus.oauth2client",
              "generatedClass" -> "OAuth2ClientComponent",
              "path" -> "oauth2-client"
            )
          ),
          (
            "HTTP2Gateway",
            Map(
              "qualified" -> "org.simplemodeling.textus.HTTP2Gateway",
              "organization" -> "org.simplemodeling.textus",
              "artifact" -> "textus-http2-gateway",
              "jvmPackage" -> "org.simplemodeling.textus.http2gateway",
              "generatedClass" -> "HTTP2GatewayComponent",
              "path" -> "http2-gateway"
            )
          )
        )
        _with_temp_dir("cozy-phase56-cid01-e6-projection") { directory =>
          val projectfiles = cases.map { case (localid, _) =>
            val projectdir = directory.resolve(localid)
            Files.createDirectories(projectdir)
            val projectfile = projectdir.resolve("project.yaml")
            Files.writeString(
              projectfile,
              _canonical_project_yaml(
                "org.simplemodeling.textus",
                localid,
                "1.0.0-SNAPSHOT"
              )
            )
            (projectdir, projectfile)
          }

          When("E6 Cozy runs the same ComponentInitConfig to carProjectYaml to parse chain for each config")
          val observed = projectfiles.map { case (projectdir, projectfile) =>
            val init = CozyScaffold.ComponentInitConfig.create(
              List("--save", projectdir.resolve("generated").toString, "--config", projectfile.toString)
            )
            CozyProjectYamlConfig.parse(
              CozyScaffold.carProjectYaml(init, CozyScaffold.CarDependencyVersions.default).
                linesIterator.toVector
            ).mapUnder("project.identity")
          }

          Then("E6 every literal projection map is emitted exactly")
          observed shouldBe cases.map(_._2)
        }
      }
    }

    "E6 scoped collision reports reject same-namespace projections and admit different namespaces" must _e6 {
      "E6 evaluates same and different namespace collision scenarios" in {
        Given("E6 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R3,CID01-R4; Example: E6; one same-namespace collision request and one different-namespace request")
        val same = ProjectIdentityContractScenarioRequest.ScopedCollision(
          "same-namespace-http-gateway",
          Vector(
            ProjectIdentityInput("org.simplemodeling.textus", "HTTPGateway"),
            ProjectIdentityInput("org.simplemodeling.textus", "HttpGateway")
          )
        )
        val different = ProjectIdentityContractScenarioRequest.ScopedCollision(
          "different-namespace-http-gateway",
          Vector(
            ProjectIdentityInput("org.simplemodeling.textus", "HTTPGateway"),
            ProjectIdentityInput("org.simplemodeling.other", "HttpGateway")
          )
        )

        When("E6 the production-owned scenario SPI evaluates both scoped collision requests")
        val observed = Vector(
          ProjectIdentityContractScenarioSpi.evaluate(same),
          ProjectIdentityContractScenarioSpi.evaluate(different)
        )

        Then("E6 the same namespace is rejected and the different namespace is admitted")
        observed shouldBe Vector(
          ProjectIdentityContractScenarioReport.Rejected(
            "same-namespace-http-gateway",
            "component.identity.projection-collision"
          ),
          ProjectIdentityContractScenarioReport.Admitted("different-namespace-http-gateway")
        )
      }
    }
  }

  "E7 version-sensitive canonical and legacy lint" should {
    "E7 lint classifies canonical-only migration and legacy disagreement fixtures exactly" must _e7 {
      "E7 classifies migration and disagreement fixtures" in {
        Given("E7 Spec: /Users/asami/src/dev2025/cloud-native-component-framework/docs/notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md; Rules: CID01-R5,CID01-R6; Example: E7; one valid single-mapping fixture matrix with canonical and legacy project shapes")
        val cases = Vector(
          (
            "canonical-only",
            _canonical_project_yaml("org.simplemodeling.textus", "UserAccount", "0.1.0-SNAPSHOT"),
            "0.1.0-SNAPSHOT",
            Some("org.simplemodeling.textus"),
            Some("UserAccount"),
            Vector.empty[(String, String)]
          ),
          (
            "legacy-snapshot",
            _legacy_project_yaml("0.0.1-SNAPSHOT"),
            "0.0.1-SNAPSHOT",
            None,
            None,
            Vector(("CAR_COMPONENT_IDENTITY_MIGRATION_REQUIRED", "FAIL"))
          ),
          (
            "legacy-release",
            _legacy_project_yaml("0.0.1"),
            "0.0.1",
            None,
            None,
            Vector(("CAR_COMPONENT_IDENTITY_MIGRATION_DEFERRED", "WARN"))
          ),
          (
            "advanced-legacy-release",
            _legacy_project_yaml("0.0.2"),
            "0.0.2",
            None,
            None,
            Vector(("CAR_COMPONENT_IDENTITY_MIGRATION_REQUIRED", "FAIL"))
          ),
          (
            "disagreement-snapshot",
            _disagreement_project_yaml("0.0.1-SNAPSHOT"),
            "0.0.1-SNAPSHOT",
            Some("org.simplemodeling.textus"),
            Some("OtherAccount"),
            Vector(("CAR_COMPONENT_IDENTITY_DISAGREEMENT", "FAIL"))
          ),
          (
            "disagreement-release",
            _disagreement_project_yaml("0.0.1"),
            "0.0.1",
            Some("org.simplemodeling.textus"),
            Some("OtherAccount"),
            Vector(("CAR_COMPONENT_IDENTITY_DISAGREEMENT", "FAIL"))
          )
        )
        _with_temp_dir("cozy-phase56-cid01-e7-lint") { directory =>
          When("E7 CozyCarLint lints every fixture with latest version 0.1.0")
          val observed = cases.map { case (label, yaml, _, _, _, _) =>
            val projectdir = directory.resolve(label)
            Files.createDirectories(projectdir)
            Files.writeString(projectdir.resolve("project.yaml"), yaml)
            val config = CozyProjectYamlConfig.loadProjectMetadata(projectdir)
            val findings = CozyCarLint.lint(projectdir, None, true, Some("0.1.0"))
            (
              config.value("project.component.version"),
              config.value("project.namespace"),
              config.value("project.id"),
              findings
            )
          }

          Then("E7 fixture truth retains only each exact version and canonical-field presence")
          observed.map { case (version, namespace, id, _) => (version, namespace, id) } shouldBe
            cases.map { case (_, _, version, namespace, id, _) => (Some(version), namespace, id) }

          And("E7 filtered identity findings match the exact code and level vector")
          pendingUntilFixed {
            observed.map { case (_, _, _, findings) =>
              findings.filter(finding => _identity_codes(finding.code)).
                map(finding => (finding.code, finding.level.name))
            } shouldBe cases.map(_._6)
          }
        }
      }
    }
  }

  private def _canonical_project_yaml(namespace: String, localid: String, version: String): String =
    s"""project:
      |  namespace: $namespace
      |  id: $localid
      |  component:
      |    version: $version
      |""".stripMargin

  private def _projection_map(projection: ComponentIdentityProjection): Map[String, String] =
    Map(
      "qualified" -> projection.qualifiedId(),
      "organization" -> projection.mavenGroupId(),
      "artifact" -> projection.mavenArtifactId(),
      "jvmPackage" -> projection.jvmPackage(),
      "generatedClass" -> projection.generatedClassName(),
      "path" -> projection.pathSegment()
    )

  private def _legacy_project_yaml(version: String): String =
    s"""project:
      |  name: textus-user-account
      |  title: Textus User Account
      |  kind: car
      |  organization: org.simplemodeling.textus
      |  scalaPackage: org.simplemodeling.textus.useraccount
      |  component:
      |    name: textus-user-account
      |    className: UserAccount
      |    displayName: Textus User Account
      |    version: $version
      |""".stripMargin

  private def _disagreement_project_yaml(version: String): String =
    s"""project:
      |  namespace: org.simplemodeling.textus
      |  id: OtherAccount
      |  name: textus-user-account
      |  title: Textus User Account
      |  kind: car
      |  organization: org.simplemodeling.textus
      |  scalaPackage: org.simplemodeling.textus.useraccount
      |  component:
      |    name: textus-user-account
      |    className: UserAccount
      |    displayName: Textus User Account
      |    version: $version
      |""".stripMargin

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Path.of("target", "phase56-project-identity", "work")
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, prefix)
    try body(directory)
    finally _delete_recursively(directory)
  }

  private def _delete_recursively(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.sorted(java.util.Comparator.reverseOrder()).forEach(x => Files.deleteIfExists(x))
      finally stream.close()
    }
  }
}
