package cozy

/*
 * @since   Jul.  8, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CozyCliPreflight(
  arguments: List[String],
  command: Vector[String],
  outputFormat: CozyOutputFormat,
  outputPolicy: CozyCliOutputPolicy
) {
  def jsonLintCommand: Option[(String, List[String])] =
    if (outputFormat == CozyOutputFormat.Json)
      command.toList match {
        case "lint" :: "build" :: Nil =>
          Some("build" -> _lint_arguments(2))
        case "lint" :: "cml" :: Nil =>
          Some("cml" -> _lint_arguments(2))
        case "lint" :: "abi" :: Nil =>
          Some("abi" -> _lint_arguments(2))
        case "lint" :: "car" :: Nil =>
          Some("car" -> _lint_arguments(2))
        case "lint" :: "repository" :: Nil =>
          Some("repository" -> _lint_arguments(2))
        case "car" :: "lint" :: Nil =>
          Some("car" -> _lint_arguments(2))
        case _ =>
          None
      }
    else
      None

  private def _lint_arguments(drop: Int): List[String] =
    arguments.drop(drop)
}

object CozyCliPreflight {
  def parse(args: Array[String]): CozyCliPreflight = {
    val xs = args.toList
    val format = CozyOutputFormat.parse(xs).getOrElse(CozyOutputFormat.Text)
    CozyCliPreflight(
      xs,
      _command(xs),
      format,
      CozyCliOutputPolicy.forFormat(format)
    )
  }

  private def _command(args: List[String]): Vector[String] =
    args match {
      case "lint" :: subcommand :: _ =>
        Vector("lint", subcommand)
      case "car" :: "lint" :: _ =>
        Vector("car", "lint")
      case head :: _ =>
        Vector(head)
      case Nil =>
        Vector.empty
    }
}

sealed trait CozyOutputFormat
object CozyOutputFormat {
  case object Text extends CozyOutputFormat
  case object Json extends CozyOutputFormat

  def parse(args: List[String]): Option[CozyOutputFormat] =
    args.collectFirst {
      case "--format=json" => Json
    }.orElse(args.sliding(2).collectFirst {
      case "--format" :: "json" :: Nil => Json
    })
}

final case class CozyCliOutputPolicy(
  stdoutMode: StdoutMode,
  logTarget: LogTarget,
  progressEnabled: Boolean,
  bannerEnabled: Boolean
)

object CozyCliOutputPolicy {
  def forFormat(format: CozyOutputFormat): CozyCliOutputPolicy =
    format match {
      case CozyOutputFormat.Json =>
        CozyCliOutputPolicy(
          StdoutMode.MachineJson,
          LogTarget.Stderr,
          progressEnabled = false,
          bannerEnabled = false
        )
      case CozyOutputFormat.Text =>
        CozyCliOutputPolicy(
          StdoutMode.HumanText,
          LogTarget.Stderr,
          progressEnabled = true,
          bannerEnabled = true
        )
    }
}

sealed trait StdoutMode
object StdoutMode {
  case object HumanText extends StdoutMode
  case object MachineJson extends StdoutMode
}

sealed trait LogTarget
object LogTarget {
  case object Stderr extends LogTarget
  case object File extends LogTarget
  case object Disabled extends LogTarget
}

object CozyCliLogging {
  private val _logback_configuration_file_property = "logback.configurationFile"
  private val _logback_status_listener_property = "logback.statusListenerClass"

  def configure(policy: CozyCliOutputPolicy): Unit = {
    _configure_cozy_logback()
    policy.stdoutMode match {
      case StdoutMode.MachineJson =>
        _suppress_logback_status_stdout()
      case StdoutMode.HumanText =>
        ()
    }
  }

  private def _configure_cozy_logback(): Unit =
    if (System.getProperty(_logback_configuration_file_property) == null)
      _resource("cozy-logback.xml").foreach { url =>
        System.setProperty(_logback_configuration_file_property, url.toExternalForm)
      }

  private def _suppress_logback_status_stdout(): Unit =
    if (System.getProperty(_logback_status_listener_property) == null)
      System.setProperty(_logback_status_listener_property, "ch.qos.logback.core.status.NopStatusListener")

  private def _resource(name: String): Option[java.net.URL] =
    Option(Thread.currentThread.getContextClassLoader).flatMap(x => Option(x.getResource(name))).
      orElse(Option(getClass.getClassLoader).flatMap(x => Option(x.getResource(name))))
}
