package cozy

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object CozySarPublisher {
  def publish(args: List[String]): Unit =
    _root_.cozy.archive.CozySarPublisher.publish(args)
}
