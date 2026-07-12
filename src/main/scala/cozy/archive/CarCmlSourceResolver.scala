package cozy.archive

import cozy.config.CozyProjectYamlConfig
import java.nio.file.{Files, InvalidPathException, Path, Paths}
import scala.collection.JavaConverters._

/*
 * Resolves the CML specification used to publish CAR catalog sidecars.
 * Publication and lint share this resolver so a successful lint cannot select
 * a different source from the publisher.
 *
 * @since   Jul. 13, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CarCmlSourceResolver {
  final case class Resolved(
      source: Path,
      projectrelativepath: String
  )

  final case class Issue(
      code: String,
      message: String,
      path: Path
  )

  def projectArtifactId(projectdir: Path): Either[Issue, String] = {
    val root = projectdir.toAbsolutePath.normalize()
    val metadata = CozyProjectYamlConfig.loadProjectMetadata(root)
    metadata
      .value("project.name")
      .orElse(metadata.value("name"))
      .toRight(
        _issue(
          root.resolve("project.yaml"),
          "car.cml.artifact_id.missing",
          "project.yaml must declare project.name or legacy top-level name."
        )
      )
  }

  def resolve(projectdir: Path): Either[Issue, Resolved] =
    projectArtifactId(projectdir).flatMap(_resolve(projectdir, _))

  def resolve(
      projectdir: Path,
      requestedartifactid: String
  ): Either[Issue, Resolved] =
    projectArtifactId(projectdir).flatMap { projectartifactid =>
      if (projectartifactid == requestedartifactid)
        _resolve(projectdir, projectartifactid)
      else
        Left(
          _issue(
            projectdir.resolve("project.yaml"),
            "car.cml.artifact_id.mismatch",
            s"CAR publication name '${requestedartifactid}' must match project.name '${projectartifactid}'."
          )
        )
    }

  private def _resolve(
      projectdir: Path,
      artifactid: String
  ): Either[Issue, Resolved] = {
    val root = projectdir.toAbsolutePath.normalize()
    val metadata = CozyProjectYamlConfig.loadProjectMetadata(root)
    metadata.value("cml.source") match {
      case Some(source) => _resolve_explicit(root, source)
      case None         => _resolve_convention(root, artifactid)
    }
  }

  private def _resolve_explicit(
      root: Path,
      source: String
  ): Either[Issue, Resolved] =
    try {
      val configured = Paths.get(source)
      if (configured.isAbsolute)
        Left(
          _issue(
            root,
            "car.cml.source.outside_project",
            s"cml.source must be relative to the project root: ${source}"
          )
        )
      else {
        val path = root.resolve(configured).toAbsolutePath.normalize()
        if (!path.startsWith(root))
          Left(
            _issue(
              root,
              "car.cml.source.outside_project",
              s"cml.source must stay inside the project root: ${source}"
            )
          )
        else if (!_is_cml(path))
          Left(
            _issue(
              path,
              "car.cml.source.invalid_extension",
              s"cml.source must name a .cml file: ${source}"
            )
          )
        else if (!Files.isRegularFile(path))
          Left(
            _issue(
              path,
              "car.cml.source.not_found",
              s"Configured cml.source does not exist: ${source}"
            )
          )
        else
          _resolved_inside_project(root, path)
      }
    } catch {
      case _: InvalidPathException =>
        Left(
          _issue(
            root,
            "car.cml.source.not_found",
            s"Configured cml.source is not a valid path: ${source}"
          )
        )
    }

  private def _resolve_convention(
      root: Path,
      artifactid: String
  ): Either[Issue, Resolved] = {
    val sourcedir = root.resolve("src/main/cozy")
    val canonical =
      sourcedir.resolve(s"${artifactid}.cml").toAbsolutePath.normalize()
    if (Files.isRegularFile(canonical))
      _resolved_inside_project(root, canonical)
    else {
      val candidates = _cml_candidates(sourcedir)
      candidates match {
        case Vector(source) => _resolved_inside_project(root, source)
        case Vector() =>
          Left(
            _issue(
              sourcedir,
              "car.cml.source.missing",
              s"CAR project has no CML source under ${_display(root, sourcedir)}"
            )
          )
        case xs =>
          val names = xs.map(_display(root, _)).mkString(", ")
          Left(
            _issue(
              sourcedir,
              "car.cml.source.ambiguous",
              s"CAR project has multiple CML sources; set cml.source in project.yaml: ${names}"
            )
          )
      }
    }
  }

  private def _cml_candidates(sourcedir: Path): Vector[Path] =
    if (!Files.isDirectory(sourcedir))
      Vector.empty
    else {
      val stream = Files.walk(sourcedir)
      try {
        stream
          .iterator()
          .asScala
          .filter(path => Files.isRegularFile(path) && _is_cml(path))
          .map(_.toAbsolutePath.normalize())
          .toVector
          .sortBy(_.toString)
      } finally {
        stream.close()
      }
    }

  private def _resolved_inside_project(
      root: Path,
      source: Path
  ): Either[Issue, Resolved] =
    try {
      if (!source.toRealPath().startsWith(root.toRealPath()))
        Left(
          _issue(
            source,
            "car.cml.source.outside_project",
            s"CML source must stay inside the project root: ${_display(root, source)}"
          )
        )
      else
        Right(Resolved(source, _display(root, source)))
    } catch {
      case _: java.io.IOException =>
        Left(
          _issue(
            source,
            "car.cml.source.not_found",
            s"CML source cannot be read: ${_display(root, source)}"
          )
        )
    }

  private def _display(root: Path, path: Path): String =
    root
      .relativize(path.toAbsolutePath.normalize())
      .iterator()
      .asScala
      .map(_.toString)
      .mkString("/")

  private def _is_cml(path: Path): Boolean =
    path.getFileName.toString
      .toLowerCase(java.util.Locale.ROOT)
      .endsWith(".cml")

  private def _issue(path: Path, code: String, message: String): Issue =
    Issue(code, message, path.toAbsolutePath.normalize())
}
