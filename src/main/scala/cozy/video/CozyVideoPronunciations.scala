package cozy.video

import org.goldenport.RAISE
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties
import scala.collection.JavaConverters._

/*
 * @since   Jul. 18, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[video] final case class CozyVideoPronunciations private (
  entries: Map[String, String]
) {
  def applyTo(text: String, overrides: Map[String, String] = Map.empty): String = {
    val effective = (entries ++ overrides).toVector.
      filter { case (surface, _) => surface.nonEmpty }.
      sortBy { case (surface, _) => (-surface.length, surface) }
    val result = new StringBuilder(text.length)
    var offset = 0
    while (offset < text.length) {
      effective.find { case (surface, _) => text.startsWith(surface, offset) } match {
        case Some((surface, reading)) =>
          result.append(reading)
          offset += surface.length
        case None =>
          result.append(text.charAt(offset))
          offset += 1
      }
    }
    result.toString
  }
}

private[video] object CozyVideoPronunciations {
  private val RESOURCE_NAME = "cozy/video/pronunciations.properties"

  lazy val default: CozyVideoPronunciations = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(RESOURCE_NAME)).getOrElse(
      RAISE.invalidArgumentFault(s"Missing Cozy video pronunciation dictionary: $RESOURCE_NAME")
    )
    val reader = new InputStreamReader(stream, StandardCharsets.UTF_8)
    try {
      val properties = new Properties()
      properties.load(reader)
      val entries = properties.stringPropertyNames().asScala.map { surface =>
        surface.trim -> properties.getProperty(surface).trim
      }.filter { case (surface, reading) => surface.nonEmpty && reading.nonEmpty }.toMap
      CozyVideoPronunciations(entries)
    } finally {
      reader.close()
    }
  }
}
