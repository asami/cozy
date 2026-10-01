package cozy.publication

import java.nio.file.Path
import org.goldenport.RAISE
import scala.util.control.NonFatal

/*
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectPublicationPreparationCommand {
  object Config {
    def create(args: List[String]): CozyDocumentProjectPublicationPreparation.Config = {
      if (args == null) _invalid("media prepare-publication arguments must be defined")
      val allowed = Set("--project", "--bundle", "--task-root", "--save", "--target", "--site-root", "--site-config")
      var options = Map.empty[String, String]
      var descriptor: Option[String] = None
      var index = 0
      while (index < args.size) {
        val argument = args(index)
        if (argument == null) _invalid("media prepare-publication argument must be defined")
        if (argument.startsWith("-")) {
          val equals = argument.indexOf('=')
          val name = if (equals < 0) argument else argument.take(equals)
          if (!allowed(name)) _invalid(s"Unknown media prepare-publication option: $name")
          if (options.contains(name)) _invalid(s"Duplicate $name")
          val value = if (equals >= 0) argument.drop(equals + 1) else {
            if (index + 1 >= args.size || args(index + 1) == null || args(index + 1).startsWith("-"))
              _invalid(s"Missing value for $name")
            args(index + 1)
          }
          options += name -> _exact_value(value, name)
          index += (if (equals >= 0) 1 else 2)
        } else {
          if (descriptor.nonEmpty) _invalid("media prepare-publication requires exactly one media-file")
          descriptor = Some(_exact_value(argument, "media-file"))
          index += 1
        }
      }
      def _required_(name: String): Path = _host_path(options.getOrElse(name, _invalid(s"Missing $name")), name)
      val descriptorfile = _host_path(descriptor.getOrElse(_invalid("Missing media-file")), "media-file")
      val project = _required_("--project")
      val bundle = _required_("--bundle")
      val taskroot = _required_("--task-root")
      val destination = _required_("--save")
      val sitecontext = (options.get("--site-root"), options.get("--site-config")) match {
        case (None, None) => None -> None
        case (Some(root), Some(config)) =>
          Some(_site_path(root, "--site-root")) -> Some(_site_path(config, "--site-config"))
        case _ => _invalid("Media --site-root and --site-config must be supplied together")
      }
      CozyDocumentProjectPublicationPreparation.Config(
        CozyDocumentProjectPublicationTarget.CurrentRegistrationConfig(
          project,
          CozyDocumentProjectPublicationTarget.RegistrationConfig(
            bundle,
            CozyArticleMediaSiteBinding.Config(descriptorfile, options.get("--target"), sitecontext._1, sitecontext._2)
          )
        ),
        taskroot,
        destination
      )
    }
  }

  def execute(args: List[String]): String = execute(Config.create(args))

  def execute(config: CozyDocumentProjectPublicationPreparation.Config): String = {
    val prepared = CozyDocumentProjectPublicationPreparation.prepare(config)
    (Vector("Cozy Media Prepare Publication", "status: PREPARED", s"root: ${prepared.root}",
      s"export: ${prepared.exportRoot}", "registry:") ++ prepared.registryPaths.map(path => s"  - $path")).mkString("\n")
  }

  private def _exact_value(value: String, label: String): String = {
    if (value == null || value.isEmpty || value != value.trim)
      _invalid(s"$label must be a non-empty exact value")
    value
  }

  private def _host_path(value: String, label: String): Path =
    try Path.of(value).toAbsolutePath.normalize() catch {
      case NonFatal(_) => _invalid(s"$label path is invalid")
    }

  private def _site_path(value: String, label: String): Path = {
    val path = try Path.of(value) catch {
      case NonFatal(_) => _invalid(s"$label path is invalid")
    }
    if (!path.isAbsolute) _invalid(s"$label path must be absolute")
    path.normalize()
  }

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
