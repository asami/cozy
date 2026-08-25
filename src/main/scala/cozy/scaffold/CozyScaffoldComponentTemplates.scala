package cozy.scaffold

import CozyScaffold.{CarDependencyVersions, CarScaffoldConfig, ComponentInitConfig}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._

/*
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyScaffoldComponentTemplates {
  private val _default_sbt_cozy_version = org.simplemodeling.cozy.BuildInfo.sbtCozyVersion
  private val _default_scala_version = org.simplemodeling.cozy.BuildInfo.scaffoldScalaVersion
  private val _default_cozy_version = org.simplemodeling.cozy.BuildInfo.scaffoldCozyVersion
  private val _default_textus_user_account_version =
    org.simplemodeling.cozy.BuildInfo.scaffoldTextusUserAccountVersion

  private[cozy] def carBuildSbt(
    versions: CarDependencyVersions,
    scaffold: CarScaffoldConfig
  ): String =
    s"""import org.goldenport.cozy.CozyPlugin.autoImport._
      |import org.goldenport.cozy.CozyProjectIdentityEvidence
      |import sbt.Keys.*
      |
      |lazy val projectIdentityEvidence = settingKey[CozyProjectIdentityEvidence]("Admitted project.yaml component identity evidence")
      |
      |lazy val root = project
      |  .in(file("."))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(
      |    projectIdentityEvidence := ProjectYamlBuild.admitted(cozyProjectMetadata.value, scalaBinaryVersion.value),
      |    organization := ProjectYamlBuild.organization(projectIdentityEvidence.value, cozyProjectMetadata.value),
      |    moduleName := ProjectYamlBuild.moduleName(projectIdentityEvidence.value, cozyProjectMetadata.value),
      |    name := moduleName.value,
      |    version := ProjectYamlBuild.version(projectIdentityEvidence.value, cozyProjectMetadata.value),
      |    scalaVersion := ProjectYamlBuild.requiredValue(cozyProjectMetadata.value, "build.scalaVersion"),
      |    useCoursier := false,
      |
      |    resolvers += Resolver.defaultLocal,
      |    resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
      |    resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository"),
      |    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven",
      |    libraryDependencies ++= ProjectYamlBuild.dependencies(cozyProjectMetadata.value),
      |
      |    cozyGeneratorBackend := "cozy",
      |    cozyDelegateProjectDir := None,
      |    cozyCarName := ProjectYamlBuild.carBaseName(projectIdentityEvidence.value, moduleName.value, version.value),
      |    cozyManifestMetadata ++= ProjectYamlBuild.manifestMetadata(projectIdentityEvidence.value, cozyProjectMetadata.value)
      |  )
      |""".stripMargin

  private[cozy] def carProjectYamlBuildScala(): String =
    """import org.goldenport.cozy.{CozyProjectConfig, CozyProjectIdentityContract, CozyProjectIdentityEvidence}
      |import sbt._
      |
      |object ProjectYamlBuild {
      |  def load(file: File): CozyProjectConfig =
      |    CozyProjectConfig.load(file)
      |
      |  def requiredValue(config: CozyProjectConfig, path: String): String =
      |    config.value(path).getOrElse(sys.error(s"$path is required in project.yaml"))
      |
      |  def admitted(config: CozyProjectConfig, scalaBinaryVersion: String): CozyProjectIdentityEvidence =
      |    CozyProjectIdentityContract.requireAdmitted(config, scalaBinaryVersion)
      |
      |  def organization(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): String =
      |    evidence.organization.getOrElse(requiredValue(config, "project.organization"))
      |
      |  def moduleName(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): String =
      |    evidence.moduleName.getOrElse(requiredValue(config, "project.name"))
      |
      |  def version(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): String =
      |    if (evidence.shape == "canonical") evidence.effectiveVersion
      |    else requiredValue(config, "project.component.version")
      |
      |  def carBaseName(evidence: CozyProjectIdentityEvidence, moduleName: String, version: String): String =
      |    evidence.carBaseName.getOrElse(s"$moduleName-$version")
      |
      |  def manifestMetadata(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): Map[String, String] =
      |    if (evidence.shape == "canonical") evidence.manifestMetadata
      |    else config.mapUnder("packaging.car.manifest_metadata") ++
      |      Map("component" -> requiredValue(config, "project.component.name"))
      |
      |  def dependencies(config: CozyProjectConfig): Seq[ModuleID] =
      |    _dependencies(config, "compile", None) ++
      |      _dependencies(config, "test", Some(Test))
      |
      |  private def _dependencies(
      |    config: CozyProjectConfig,
      |    scope: String,
      |    configuration: Option[Configuration]
      |  ): Seq[ModuleID] =
      |    config.list(s"build.dependencies.$scope").map { coordinate =>
      |      val module = _module(coordinate)
      |      configuration.fold(module)(module % _)
      |    }
      |
      |  private def _module(coordinate: String): ModuleID =
      |    coordinate.split(":", -1).toList match {
      |      case organization :: "" :: artifact :: version :: Nil =>
      |        organization %% artifact % version
      |      case organization :: artifact :: version :: Nil =>
      |        organization % artifact % version
      |      case _ =>
      |        sys.error(
      |          s"Invalid project.yaml dependency '$coordinate'; expected organization:artifact:version or organization::artifact:version"
      |        )
      |    }
      |}
      |""".stripMargin

  private[cozy] def carProjectYaml(
    init: ComponentInitConfig,
    versions: CarDependencyVersions
  ): String = {
    val scaffold = init.scaffold
    val projectmetadata = init.projectidentity.fold {
      s"""  name: ${_yaml_string(scaffold.artifactName)}
        |  title: ${_yaml_string(init.displayName)}
        |  kind: car
        |  organization: ${_yaml_string(scaffold.organization)}
        |  scalaPackage: ${_yaml_string(scaffold.packageName)}
        |  component:
        |    name: ${_yaml_string(scaffold.artifactName)}
        |    className: ${_yaml_string(scaffold.componentName)}
        |    displayName: ${_yaml_string(init.displayName)}
        |    version: ${_yaml_string(scaffold.version)}
        |""".stripMargin
    } { identity =>
      val projection = identity.projection
      s"""  namespace: ${_yaml_string(identity.namespace.value())}
        |  id: ${_yaml_string(identity.localId.value())}
        |  kind: car
        |  component:
        |    displayName: ${_yaml_string(init.displayName)}
        |    version: ${_yaml_string(scaffold.version)}
        |  identity:
        |    qualified: ${_yaml_string(projection.qualifiedId())}
        |    organization: ${_yaml_string(projection.mavenGroupId())}
        |    artifact: ${_yaml_string(projection.mavenArtifactId())}
        |    jvmPackage: ${_yaml_string(projection.jvmPackage())}
        |    generatedClass: ${_yaml_string(projection.generatedClassName())}
        |    path: ${_yaml_string(projection.pathSegment())}
        |""".stripMargin
    }
    s"""project:
      |${projectmetadata}
      |build:
      |  scalaVersion: ${_yaml_string(_default_scala_version)}
      |  cozyVersion: ${_yaml_string(_default_cozy_version)}
      |  dependencies:
      |    compile:
      |      - ${_yaml_string(s"org.goldenport::goldenport-cncf:${versions.cncfVersion}")}
      |    test:
      |      - "org.scalatest::scalatest:3.2.19"
      |
      |publication:
      |  source_manifest:
      |    enabled: false
      |
      |packaging:
      |  kind: car
      |  car:
      |    abi:
      |      dependencies: []
      |    manifest_metadata:
      |      boundedContext: ${_yaml_string(scaffold.boundedContext)}
      |      domain: ${_yaml_string(scaffold.domain)}
      |    runtime:
      |      cncf:
      |        minimum: ${_yaml_string(versions.cncfVersion)}
      |        excluded: []
      |        tested:
      |          - ${_yaml_string(versions.cncfVersion)}
      |
      |warehouse:
      |  repository_artifacts:
      |    include:
      |      - car
      |    modules:
      |      - ${_yaml_string(scaffold.artifactName)}
      |""".stripMargin
  }

  private def _yaml_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _json_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private[cozy] def carSarBuildSbt(scaffold: CarScaffoldConfig, versions: CarDependencyVersions): String =
    s"""import org.goldenport.cozy.CozyPlugin.autoImport._
      |import org.goldenport.cozy.CozyProjectIdentityEvidence
      |import sbt.Keys.*
      |
      |lazy val componentMetadata = ProjectYamlBuild.load(file("component/project.yaml"))
      |lazy val componentIdentityEvidence = settingKey[CozyProjectIdentityEvidence]("Admitted component project.yaml identity evidence")
      |
      |lazy val commonSettings = Seq(
      |  componentIdentityEvidence := ProjectYamlBuild.admitted(componentMetadata, scalaBinaryVersion.value),
      |  organization := ProjectYamlBuild.organization(componentIdentityEvidence.value, componentMetadata),
      |  version := ProjectYamlBuild.version(componentIdentityEvidence.value, componentMetadata),
      |  scalaVersion := ProjectYamlBuild.requiredValue(componentMetadata, "build.scalaVersion"),
      |  useCoursier := false,
      |  resolvers += Resolver.defaultLocal,
      |  resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
      |  resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository"),
      |  resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
      |)
      |
      |lazy val root = project
      |  .in(file("."))
      |  .aggregate(component, subsystem)
      |  .settings(commonSettings)
      |  .settings(
      |    moduleName := ProjectYamlBuild.moduleName(componentIdentityEvidence.value, componentMetadata),
      |    name := moduleName.value,
      |    publish := {
      |      val componentpublication = (component / publish).value
      |      val subsystempublication = (subsystem / publish).value
      |      val _ = (componentpublication, subsystempublication)
      |      ()
      |    },
      |    publishLocal := {
      |      val componentpublication = (component / publishLocal).value
      |      val subsystempublication = (subsystem / publishLocal).value
      |      val _ = (componentpublication, subsystempublication)
      |      ()
      |    }
      |  )
      |
      |lazy val component = project
      |  .in(file("component"))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(commonSettings)
      |  .settings(
      |    moduleName := ProjectYamlBuild.moduleName(componentIdentityEvidence.value, componentMetadata),
      |    name := moduleName.value,
      |    cozyGeneratorBackend := "cozy",
      |    libraryDependencies ++= ProjectYamlBuild.dependencies(componentMetadata),
      |    cozyCarName := ProjectYamlBuild.carBaseName(componentIdentityEvidence.value, moduleName.value, version.value),
      |    cozyManifestMetadata ++= ProjectYamlBuild.manifestMetadata(componentIdentityEvidence.value, componentMetadata),
      |    Test / fork := false
      |  )
      |
      |lazy val subsystem = project
      |  .in(file("subsystem"))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(commonSettings)
      |  .settings(
      |    moduleName := ProjectYamlBuild.moduleName(componentIdentityEvidence.value, componentMetadata) + "-subsystem",
      |    name := moduleName.value,
      |    cozyPackaging := "sar",
      |    cozySourceDir := baseDirectory.value,
      |    libraryDependencies ++= ProjectYamlBuild.dependencies(componentMetadata),
      |    Test / fork := false
      |  )
      |
      |addCommandAlias("cozyGenerateApp", "component/cozyGenerate")
      |addCommandAlias("cozyBuildAppCAR", "component/cozyBuildCAR")
      |addCommandAlias("cozyBuildAppSAR", "subsystem/cozyBuildSAR")
      |""".stripMargin

  private[cozy] def carSarReadme(scaffold: CarScaffoldConfig): String =
    s"""# ${scaffold.artifactName}
      |
      |Generated Cozy application scaffold.
      |
      |Directories:
      |- `component/`: Cozy/CML source, generated component code, web assets, CAR packaging
      |- `subsystem/`: subsystem descriptor, external component bindings, SAR packaging
      |
      |Typical workflow:
      |- `sbt component/cozyGenerate`
      |- `sbt component/cozyBuildCAR`
      |- `sbt subsystem/cozyBuildSAR`
      |""".stripMargin

  private[cozy] def carSarSampleCml(scaffold: CarScaffoldConfig): String =
    carSampleCml(scaffold)

  private[cozy] def carSarSubsystemDescriptorYaml(scaffold: CarScaffoldConfig): String =
    s"""subsystem: ${scaffold.artifactName}
      |version: ${scaffold.version}
      |components:
      |  - name: ${scaffold.artifactName}
      |    version: ${scaffold.version}
      |  - name: textus-user-account
      |    version: ${_default_textus_user_account_version}
      |#security:
      |#  authentication:
      |#    convention: enabled
      |#    fallback_privilege: disabled
      |#    providers:
      |#      - name: user-account
      |#        component: textus-user-account
      |#        kind: human
      |#        enabled: true
      |#        priority: 100
      |#        schemes:
      |#          - bearer
      |#        default: true
      |""".stripMargin

  private[cozy] def carSarRepositoryDReadme(appName: String): String =
    s"""# repository.d
      |
      |Development-time packaged dependencies for `${appName}` live here as searchable artifacts.
      |
      |Current expected local setup:
      |- build `textus-user-account` as a CAR
      |- place it here as a symlink
      |- keep `subsystem-descriptor.yaml` coordinates stable
      |
      |Recommended local command:
      |`ln -s /absolute/path/to/textus-user-account-${_default_textus_user_account_version}.car repository.d/textus-user-account.car`
      |
      |Production distribution is repository-first. `repository.d` is the local development and test search staging path.
      |""".stripMargin

  private[cozy] def carSarScriptsReadme(appName: String): String =
    s"""# subsystem/scripts
      |
      |Local subsystem run helpers belong here.
      |
      |The default ${appName} scaffold expects local packaged dependencies under `repository.d` as the local search staging path.
      |For development, stage `textus-user-account` there as a symlink to the built CAR while keeping `subsystem-descriptor.yaml` on the stable repository coordinate.
      |""".stripMargin

  private[cozy] def carPluginsSbt(): String =
    s"""resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
       |resolvers += Resolver.defaultLocal
       |
       |val sbtCozyVersion = sys.props.getOrElse("sbt.cozy.version", sys.env.getOrElse("SBT_COZY_VERSION", "${_default_sbt_cozy_version}"))
       |addSbtPlugin("org.goldenport" % "sbt-cozy" % sbtCozyVersion)
       |""".stripMargin

  private[cozy] def carSampleCml(scaffold: CarScaffoldConfig): String =
    s"""# COMPONENT
      |
      |## ${scaffold.componentName}
      |
      |### PACKAGE
      |
      |${scaffold.packageName}
      |
      |### DESCRIPTION
      |
      |${scaffold.componentName} CAR bundle root.
      |
      |### COMPONENTLET
      |
      |#### public-${scaffold.serviceSlug}
      |
      |#### ${scaffold.serviceSlug}-admin
      |
      |# COMPONENTLET
      |
      |## public-${scaffold.serviceSlug}
      |
      |- component :: ${scaffold.componentName}
      |- kind :: participant
      |
      |### DESCRIPTION
      |
      |Public ${scaffold.serviceName} participant.
      |
      |# COMPONENTLET
      |
      |## ${scaffold.serviceSlug}-admin
      |
      |- component :: ${scaffold.componentName}
      |- kind :: participant
      |
      |### DESCRIPTION
      |
      |Administrative ${scaffold.serviceName} participant.
      |
      |# SERVICE
      |
      |## ${scaffold.serviceName}
      |
      |### DESCRIPTION
      |
      |Operations for ${scaffold.serviceName}.
      |
      |### OPERATION
      |
      |#### ${scaffold.commandOperationMethodName}
      |
      |##### TYPE
      |
      |COMMAND
      |
      |##### INPUT
      |
      |###### TYPE
      |${scaffold.commandOperationClassStem}
      |
      |##### OUTPUT
      |
      |###### TYPE
      |${scaffold.commandOperationClassStem}Result
      |
      |##### IMPLEMENTATION
      |entity-create
      |
      |##### ENTITY
      |${scaffold.entityName}
      |
      |#### ${scaffold.queryOperationMethodName}
      |
      |##### TYPE
      |
      |QUERY
      |
      |##### INPUT
      |
      |###### TYPE
      |${scaffold.queryOperationClassStem}
      |
      |##### OUTPUT
      |
      |###### TYPE
      |${scaffold.queryOperationClassStem}Result
      |
      |##### IMPLEMENTATION
      |entity-search
      |
      |##### ENTITY
      |${scaffold.entityName}
      |
      |# ENTITY
      |
      |## ${scaffold.entityName}
      |
      |### Attribute
      |
      || name        | type     | multiplicity |
      ||-------------+----------+--------------|
      || id          | entityid | 1            |
      || name        | name     | 1            |
      || description | ${scaffold.entityName}Description | ?            |
      |
      |# VALUE
      |
      |## ${scaffold.commandOperationClassStem}
      |
      |- input-kind :: COMMAND
      |
      |### Attribute
      |
      || name        | type   | multiplicity |
      ||-------------+--------+--------------|
      || name        | name   | 1            |
      || description | ${scaffold.entityName}Description | ?            |
      |
      |## ${scaffold.queryOperationClassStem}
      |
      |- input-kind :: QUERY
      |
      |### Attribute
      |
      || name   | type   | multiplicity |
      ||--------+--------+--------------|
      || text   | ${scaffold.entityName}SearchText | ?            |
      || offset | int    | ?            |
      || limit  | int    | ?            |
      |
      |## ${scaffold.entityName}Description
      |
      |### Attribute
      |
      || name  | type   | multiplicity |
      ||-------+--------+--------------|
      || value | string | 1            |
      |
      |## ${scaffold.entityName}SearchText
      |
      |### Attribute
      |
      || name  | type   | multiplicity |
      ||-------+--------+--------------|
      || value | string | 1            |
      |
      |## ${scaffold.commandOperationClassStem}Result
      |
      |### Attribute
      |
      || name | type     | multiplicity |
      ||------+----------+--------------|
      || id   | entityid | 1            |
      || name | name     | 1            |
      |
      |## ${scaffold.queryOperationClassStem}Result
      |
      |### Attribute
      |
      || name  | type                   | multiplicity |
      ||-------+------------------------+--------------|
      || items | ${scaffold.entityName} | *            |
      |""".stripMargin

  private[cozy] def carWebDescriptorYaml(
    modelpath: Option[Path],
    scaffold: CarScaffoldConfig
  ): String =
    modelpath.flatMap(_car_web_descriptor_yaml_from_cml).getOrElse(_default_car_web_descriptor_yaml(scaffold))

  private[cozy] def carWebAppYaml(
    scaffold: CarScaffoldConfig
  ): String =
    s"""apps:
      |  - name: ${scaffold.artifactName}
      |    path: /web/${scaffold.artifactName}
      |    kind: static-form
      |    root: /web/${scaffold.artifactName}
      |    route: /web/{component}/${scaffold.artifactName}
      |""".stripMargin

  private def _default_car_web_descriptor_yaml(scaffold: CarScaffoldConfig): String =
    s"""expose:
      |  ${scaffold.artifactName}.${scaffold.serviceSlug}.${scaffold.commandOperationSlug}: protected
      |  ${scaffold.artifactName}.${scaffold.serviceSlug}.${scaffold.queryOperationSlug}: public
      |form:
      |  ${scaffold.artifactName}.${scaffold.serviceSlug}.${scaffold.commandOperationSlug}:
      |    enabled: true
      |    successRedirect: /web/$${component}/admin/entities/${scaffold.entitySlug}/$${result.id}
      |    stayOnError: true
      |    controls:
      |      description:
      |        type: textarea
      |  ${scaffold.artifactName}.${scaffold.serviceSlug}.${scaffold.queryOperationSlug}:
      |    enabled: true
      |    successRedirect: /web/$${component}/admin/entities/${scaffold.entitySlug}
      |    stayOnError: true
      |admin:
      |  entity.${scaffold.entitySlug}:
      |    totalCount: optional
      |""".stripMargin

  private def _car_web_descriptor_yaml_from_cml(path: Path): Option[String] =
    if (!Files.exists(path))
      None
    else {
      val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
      val start = lines.indexWhere(line => _is_web_heading(line))
      if (start < 0)
        None
      else {
        val body = lines.drop(start + 1).takeWhile(line => !_is_top_level_heading(line))
        val text = body.dropWhile(_.trim.isEmpty).reverse.dropWhile(_.trim.isEmpty).reverse.mkString("\n")
        Option(text).filter(_.trim.nonEmpty).map(_ + "\n")
      }
    }

  private def _is_web_heading(line: String): Boolean =
    line.trim.matches("#+\\s+WEB\\s*")

  private def _is_top_level_heading(line: String): Boolean =
    line.trim.matches("#\\s+.+")

  private[cozy] def carComponentFactorySource(
    scaffold: CarScaffoldConfig
  ): String = {
    val component = scaffold.componentClassStem
    val service = scaffold.serviceClassStem
    val command = scaffold.commandOperationClassStem
    val query = scaffold.queryOperationClassStem
    val mcpreadyservices =
      if (scaffold.mcpReadyService) s"Set(${_scala_string(service)})" else "Set.empty"
    s"""package ${scaffold.packageName}.impl
      |
      |import ${scaffold.packageName}.${component}Component
      |import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId}
      |
      |final class ComponentFactory extends Component.BundleFactory {
      |  def primaryFactory: Component.PrimaryComponentFactory =
      |    ${component}PrimaryFactory
      |
      |  override def componentletFactories: Vector[Component.ComponentletFactory] =
      |    Vector.empty
      |}
      |
      |abstract class ${component}ParticipantFactoryBase extends ${component}Component.Factory {
      |  protected final val shared_services =
      |    Vector(
      |      ${component}Component.${service}Service,
      |      ${component}Component.AggregateService,
      |      ${component}Component.ViewService,
      |      ${component}Component.EntityService
      |    )
      |
      |  protected final def component_core(
      |    name: String,
      |    componentId: ComponentId
      |  ): Component.Core =
      |    spec_create(name, componentId, shared_services)
      |
      |  override val ${service}: ${component}Component.${service}ServiceFactory = Default${service}ServiceFactory()
      |  override val aggregate: ${component}Component.AggregateServiceFactory = AggregateServiceFactoryImpl()
      |  override val view: ${component}Component.ViewServiceFactory = ViewServiceFactoryImpl()
      |  override val entity: ${component}Component.EntityServiceFactory = DefaultEntityServiceFactory()
      |}
      |
      |final class ${component}PrimaryComponent extends ${component}Component {
      |  override def mcpReadyServices: Set[String] =
      |    ${mcpreadyservices}
      |}
      |
      |object ${component}PrimaryFactory extends ${component}ParticipantFactoryBase with Component.PrimaryComponentFactory {
      |  override protected def create_Component(params: ComponentCreate): Component =
      |    new ${component}PrimaryComponent()
      |
      |  override protected def create_Core(
      |    params: ComponentCreate,
      |    comp: Component
      |  ): Component.Core =
      |    component_core(${component}Component.name, ${component}Component.componentId)
      |}
      |
      |final class Default${service}ServiceFactory extends ${component}Component.${service}ServiceFactory {
      |  import ${component}Component.${service}Service.*
      |
      |  override def create${command}ActionCall(
      |    core: org.goldenport.cncf.action.ActionCall.Core,
      |    action: ${command}
      |  ): ${command}ActionCall =
      |    ${command}ActionCall(core, action)
      |
      |  override def create${query}ActionCall(
      |    core: org.goldenport.cncf.action.ActionCall.Core,
      |    action: ${query}
      |  ): ${query}ActionCall =
      |    ${query}ActionCall(core, action)
      |  }
      |
      |object Default${service}ServiceFactory {
      |  def apply(): Default${service}ServiceFactory = new Default${service}ServiceFactory()
      |  }
      |
      |final class DefaultEntityServiceFactory extends ${component}Component.EntityServiceFactory
      |
      |object DefaultEntityServiceFactory {
      |  def apply(): DefaultEntityServiceFactory = new DefaultEntityServiceFactory()
      |  }
      |
      |final class AggregateServiceFactoryImpl extends ${component}Component.AggregateServiceFactory
      |
      |object AggregateServiceFactoryImpl {
      |  def apply(): AggregateServiceFactoryImpl = new AggregateServiceFactoryImpl()
      |}
      |
      |final class ViewServiceFactoryImpl extends ${component}Component.ViewServiceFactory
      |
      |object ViewServiceFactoryImpl {
      |  def apply(): ViewServiceFactoryImpl = new ViewServiceFactoryImpl()
      |}
      |""".stripMargin
  }

  private def _scala_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private[cozy] def carGitignore(): String =
    """target/
      |.bsp/
      |.metals/
      |.scala-build/
      |.idea/
      |.DS_Store
      |""".stripMargin

  private[cozy] def carReadme(scaffold: CarScaffoldConfig): String =
    s"""# ${scaffold.componentName}
      |
      |Generated Cozy CAR component project.
      |
      |Component:
      |- artifact: `${scaffold.artifactName}`
      |- package: `${scaffold.packageName}`
      |- version: `${scaffold.version}`
      |
      |Typical workflow:
      |- `sbt cozyGenerate`
      |- `sbt compile`
      |- `sbt cozyBuildCAR`
      |
      |Generated Scala sources are written under `target/scala-${_default_scala_version}/src_managed/main/scala`.
      |""".stripMargin

  private[cozy] def carComponentFactorySpecSource(scaffold: CarScaffoldConfig): String =
    s"""package ${scaffold.packageName}
      |
      |import org.scalatest.GivenWhenThen
      |import org.scalatest.matchers.should.Matchers
      |import org.scalatest.wordspec.AnyWordSpec
      |
      |final class ComponentFactorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
      |  private val _e1 = afterWord(
      |    "in spec:generated-component-factory, example:E1, rule:COZY-SCAFFOLD-R1, phase:56, slice:CID-03B"
      |  )
      |
      |  "ComponentFactory" should _e1 {
      |    "expose a primary component factory" in {
      |      Given("Spec: generated-component-factory; Rules: COZY-SCAFFOLD-R1; Example: E1; a newly constructed generated component bundle factory")
      |      val factory = new impl.ComponentFactory()
      |
      |      When("the primary factory is requested")
      |      val primary = factory.primaryFactory
      |
      |      Then("the generated primary component boundary is available")
      |      primary should not be null
      |    }
      |  }
      |}
      |""".stripMargin

}
