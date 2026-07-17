package cozy.video

import java.nio.file.{Files, InvalidPathException, Path}
import io.circe.{Decoder, HCursor}
import org.goldenport.RAISE

/*
 * @since   Jul. 18, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoAssets {
  val GENERATED_PLACEHOLDER_LICENSE = "LicenseRef-Cozy-Generated-Placeholder"
  val GENERATED_PLACEHOLDER_PROVENANCE = "cozy:video-scaffold"

  final case class Entry(
    path: String,
    kind: Option[String],
    required: Boolean,
    license: Option[String],
    provenance: Option[String]
  )
  object Entry {
    implicit val decoder: Decoder[Entry] = Decoder.instance { c =>
      c.as[String] match {
        case Right(value) => Right(Entry(value, None, required = false, None, None))
        case Left(_) =>
          for {
            path <- c.downField("path").as[String]
            kind <- c.downField("kind").as[Option[String]]
            required <- c.downField("required").as[Option[Boolean]]
            license <- c.downField("license").as[Option[String]]
            provenance <- c.downField("provenance").as[Option[String]]
          } yield Entry(path, kind, required.getOrElse(false), license, provenance)
      }
    }
  }

  final case class Settings(
    sectionStart: Option[Entry],
    summary: Option[Entry],
    finalPage: Option[Entry]
  )
  object Settings {
    implicit val decoder: Decoder[Settings] = (c: HCursor) =>
      for {
        sectionstart <- _optional_entry(c, "sectionStart", "section-start")
        summary <- c.downField("summary").as[Option[Entry]]
        finalpage <- _optional_entry(c, "finalPage", "final-page")
      } yield Settings(sectionstart, summary, finalpage)
  }

  final case class Resolved(
    role: CozyVideoEffects.Role,
    path: Path,
    kind: String,
    required: Boolean,
    license: String,
    provenance: String,
    status: String,
    requestedPath: Option[Path]
  ) {
    def displayPath(projectroot: Path): String = _display_path(projectroot, path)
    def requestedDisplayPath(projectroot: Path): Option[String] = requestedPath.map(_display_path(projectroot, _))
  }

  def resolve(projectroot: Path, settings: Option[Settings]): Vector[Resolved] =
    settings.toVector.flatMap { value =>
      CozyVideoEffects.Role.ALL.map { role =>
        _resolve(projectroot, role, _entry(value, role))
      }
    }

  private def _entry(settings: Settings, role: CozyVideoEffects.Role): Option[Entry] =
    role match {
      case CozyVideoEffects.Role.SectionStart => settings.sectionStart
      case CozyVideoEffects.Role.Summary => settings.summary
      case CozyVideoEffects.Role.FinalPage => settings.finalPage
    }

  private def _resolve(projectroot: Path, role: CozyVideoEffects.Role, entry: Option[Entry]): Resolved = {
    val root = projectroot.toAbsolutePath.normalize()
    val fallback = _resolve_local_path(root, s"assets/${role.key}.svg", role)
    entry match {
      case Some(configured) =>
        val requested = _resolve_local_path(root, configured.path, role)
        if (_is_readable_project_file(root, requested))
          _resolved_configured(role, requested, configured)
        else if (configured.required)
          RAISE.invalidArgumentFault(
            s"Missing or unreadable required video asset ${role.key}: ${_display_path(root, requested)}"
          )
        else if (_is_readable_project_file(root, fallback))
          _resolved_placeholder(role, fallback, "fallback", Some(requested))
        else
          _resolved_placeholder(role, fallback, "optional-missing", Some(requested))
      case None =>
        if (_is_readable_project_file(root, fallback))
          _resolved_placeholder(role, fallback, "fallback", None)
        else
          _resolved_placeholder(role, fallback, "optional-missing", None)
    }
  }

  private def _resolved_configured(role: CozyVideoEffects.Role, path: Path, entry: Entry): Resolved =
    Resolved(
      role,
      path,
      entry.kind.map(_.trim).filter(_.nonEmpty).getOrElse("project-owned"),
      entry.required,
      entry.license.map(_.trim).filter(_.nonEmpty).getOrElse("unspecified"),
      entry.provenance.map(_.trim).filter(_.nonEmpty).getOrElse("unspecified"),
      "configured",
      None
    )

  private def _resolved_placeholder(
    role: CozyVideoEffects.Role,
    path: Path,
    status: String,
    requestedpath: Option[Path]
  ): Resolved =
    Resolved(
      role,
      path,
      "placeholder",
      required = false,
      GENERATED_PLACEHOLDER_LICENSE,
      GENERATED_PLACEHOLDER_PROVENANCE,
      status,
      requestedpath
    )

  private def _resolve_local_path(root: Path, value: String, role: CozyVideoEffects.Role): Path = {
    val candidate = value.trim
    if (candidate.isEmpty)
      RAISE.invalidArgumentFault(s"Video asset path is empty: ${role.key}")
    if (candidate.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*"))
      RAISE.invalidArgumentFault(s"Video assets must be project-local files, not URLs: ${role.key}=$candidate")
    val relative =
      try Path.of(candidate)
      catch {
        case e: InvalidPathException =>
          RAISE.invalidArgumentFault(s"Invalid video asset path ${role.key}: $candidate (${e.getMessage})")
      }
    if (relative.isAbsolute)
      RAISE.invalidArgumentFault(s"Video assets must use project-relative paths: ${role.key}=$candidate")
    val resolved = root.resolve(relative).normalize()
    if (!resolved.startsWith(root))
      RAISE.invalidArgumentFault(s"Video asset escapes the project root: ${role.key}=$candidate")
    resolved
  }

  private def _is_readable_project_file(root: Path, path: Path): Boolean =
    if (!Files.isRegularFile(path) || !Files.isReadable(path))
      false
    else {
      val realroot = root.toRealPath()
      val realpath = path.toRealPath()
      if (!realpath.startsWith(realroot))
        RAISE.invalidArgumentFault(s"Video asset resolves outside the project root: ${_display_path(root, path)}")
      true
    }

  private def _display_path(projectroot: Path, path: Path): String = {
    val root = projectroot.toAbsolutePath.normalize()
    val normalized = path.toAbsolutePath.normalize()
    if (normalized.startsWith(root)) root.relativize(normalized).toString else normalized.toString
  }

  private def _optional_entry(c: HCursor, camelname: String, kebabname: String): Decoder.Result[Option[Entry]] =
    c.downField(camelname).as[Option[Entry]].flatMap {
      case value @ Some(_) => Right(value)
      case None => c.downField(kebabname).as[Option[Entry]]
    }
}
