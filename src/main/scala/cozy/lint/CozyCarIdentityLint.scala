package cozy.lint

import java.nio.file.Path

import scala.collection.JavaConverters._

import cozy.config.CozyProjectYamlConfig
import org.goldenport.cncf.component.identity.{
  ComponentIdentityMigrationClassifier,
  ComponentIdentityMigrationDecision,
  ComponentIdentityMigrationRequest
}
import org.goldenport.cncf.component.identity.ComponentIdentityMigrationRequest.AuthoredProjection

/*
 * Cozy projection of the shared CNCF Component identity migration decision.
 *
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCarIdentityLint {
  def lint(root: Path): Vector[CozyCarLint.Finding] = {
    val path = root.resolve("project.yaml")
    val config = CozyProjectYamlConfig.loadProjectMetadata(root)
    if (!_is_car(config))
      Vector.empty
    else {
      val classifierresult = ComponentIdentityMigrationClassifier.load()
      if (classifierresult.isFailure) {
        val error = classifierresult.error.orElseThrow()
        Vector(
          CozyCarLint.Finding(
            CozyCarLint.Level.Fail,
            "identity",
            "CAR_COMPONENT_IDENTITY_CLASSIFIER_ERROR",
            s"code=${error.code}; message=${error.message}; actionPath=$path",
            path,
            1
          )
        )
      } else {
        val classifier = classifierresult.value.orElseThrow()
        val request = new ComponentIdentityMigrationRequest(
          config.value("project.namespace").orNull,
          config.value("project.id").orNull,
          config.value("project.name").orNull,
          config.value("project.component.className")
            .orElse(config.value("project.component.name"))
            .orNull,
          config.value("project.component.version").orNull,
          _authored_projections(config).asJava
        )
        val decisionresult = classifier.classify(request)
        if (decisionresult.isFailure) {
          val error = decisionresult.error.orElseThrow()
          Vector(
            CozyCarLint.Finding(
              CozyCarLint.Level.Fail,
              "identity",
              "CAR_COMPONENT_IDENTITY_INVALID",
              s"code=${error.code}; message=${error.message}; effectiveVersion=${config.value("project.component.version").getOrElse("missing")}; identityShape=${_identity_shape(config)}; actionPath=$path",
              path,
              1
            )
          )
        } else {
          val decision = decisionresult.value.orElseThrow()
          Vector(_finding(root, path, config, decision))
        }
      }
    }
  }

  private def _finding(
    root: Path,
    path: Path,
    config: CozyProjectYamlConfig.Config,
    decision: ComponentIdentityMigrationDecision
  ): CozyCarLint.Finding = {
    val (level, code, migrationstatus) = decision.status match {
      case ComponentIdentityMigrationDecision.Status.CANONICAL =>
        (CozyCarLint.Level.Ok, "CAR_COMPONENT_IDENTITY_CANONICAL", "canonical")
      case ComponentIdentityMigrationDecision.Status.MIGRATION_REQUIRED =>
        (CozyCarLint.Level.Fail, "CAR_COMPONENT_IDENTITY_MIGRATION_REQUIRED", "migration-required")
      case ComponentIdentityMigrationDecision.Status.DEFERRED_TO_NEXT_VERSION =>
        (CozyCarLint.Level.Warn, "CAR_COMPONENT_IDENTITY_MIGRATION_DEFERRED", "deferred-to-next-version")
      case ComponentIdentityMigrationDecision.Status.PROJECTION_DISAGREEMENT =>
        (CozyCarLint.Level.Fail, "CAR_COMPONENT_IDENTITY_DISAGREEMENT", "projection-disagreement")
      case ComponentIdentityMigrationDecision.Status.INVENTORY_ERROR =>
        (CozyCarLint.Level.Fail, "CAR_COMPONENT_IDENTITY_INVENTORY_ERROR", "inventory-error")
      case ComponentIdentityMigrationDecision.Status.STRICT_LEGACY =>
        (CozyCarLint.Level.Fail, "CAR_COMPONENT_IDENTITY_INVENTORY_ERROR", "inventory-error")
    }
    val projection = _option(decision.projection)
    val entry = _option(decision.entry)
    val canonical = projection.map(_.qualifiedId()).getOrElse("unknown")
    val unknown = "unknown-until-project.namespace+project.id"
    val owner = entry.map(_.migrationOwner()).getOrElse(
      config.value("project.name").getOrElse(root.getFileName.toString)
    )
    val message = Vector(
      s"effectiveVersion=${decision.release.orElse("missing")}",
      s"identityShape=${_identity_shape(config)}",
      s"canonicalIdentity=$canonical",
      s"expectedOrganization=${projection.map(_.mavenGroupId()).getOrElse(unknown)}",
      s"expectedArtifact=${projection.map(_.mavenArtifactId()).getOrElse(unknown)}",
      s"expectedJvmPackage=${projection.map(_.jvmPackage()).getOrElse(unknown)}",
      s"expectedGeneratedClass=${projection.map(_.generatedClassName()).getOrElse(unknown)}",
      s"expectedPath=${projection.map(_.pathSegment()).getOrElse(unknown)}",
      s"migrationStatus=$migrationstatus",
      s"migrationOwner=$owner",
      s"reason=${decision.reason}",
      s"actionPath=$path"
    ).mkString("; ")
    CozyCarLint.Finding(level, "identity", code, message, path, 1)
  }

  private def _authored_projections(
    config: CozyProjectYamlConfig.Config
  ): Vector[AuthoredProjection] = {
    val evidence = config.mapUnder("project.identity").toVector.flatMap {
      case ("qualified", value) =>
        Some(new AuthoredProjection("qualifiedId", "project.identity.qualified", value))
      case ("organization", value) =>
        Some(new AuthoredProjection("organization", "project.identity.organization", value))
      case ("artifact", value) =>
        Some(new AuthoredProjection("artifact", "project.identity.artifact", value))
      case ("jvmPackage", value) =>
        Some(new AuthoredProjection("jvmPackage", "project.identity.jvmPackage", value))
      case ("generatedClass", value) =>
        Some(new AuthoredProjection("generatedClass", "project.identity.generatedClass", value))
      case ("path", value) =>
        Some(new AuthoredProjection("path", "project.identity.path", value))
      case _ => None
    }
    val legacy = Vector(
      config.value("project.organization").map(
        value => new AuthoredProjection("organization", "project.organization", value)
      ),
      config.value("project.name").map(
        value => new AuthoredProjection("artifact", "project.name", value)
      ),
      config.value("project.scalaPackage").map(
        value => new AuthoredProjection("jvmPackage", "project.scalaPackage", value)
      ),
      config.value("project.component.className").map(
        value => new AuthoredProjection("generatedClass", "project.component.className", value)
      ),
      config.value("project.component.name").map(
        value => new AuthoredProjection("qualifiedId", "project.component.name", value)
      )
    ).flatten
    evidence ++ legacy
  }

  private def _identity_shape(config: CozyProjectYamlConfig.Config): String =
    (config.value("project.namespace"), config.value("project.id")) match {
      case (Some(_), Some(_)) => "canonical"
      case (None, None) => "legacy"
      case _ => "partial-canonical"
    }

  private def _is_car(config: CozyProjectYamlConfig.Config): Boolean =
    Vector(config.value("project.kind"), config.value("packaging.kind"))
      .flatten
      .exists(_.equalsIgnoreCase("car"))

  private def _option[A](value: java.util.Optional[A]): Option[A] =
    if (value.isPresent) Some(value.get) else None
}
