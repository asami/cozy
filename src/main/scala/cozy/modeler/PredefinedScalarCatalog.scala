package cozy.modeler

import org.goldenport.record.v2.{CFormat, CMaxLength, CMinLength, Constraint}

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object PredefinedScalarCatalog {
  final case class Entry(
    name: String,
    aliases: Set[String],
    runtimeclassname: Option[String],
    format: Option[String],
    minlength: Option[Int],
    maxlength: Option[Int],
    localized: Boolean,
    attributegroup: Option[String],
    diagnosticsuffixes: Set[String],
    replacementrecommended: Boolean
  ) {
    def constraints: Vector[Constraint] =
      minlength.map(CMinLength.apply).toVector ++
        maxlength.map(CMaxLength.apply).toVector ++
        format.map(CFormat.apply).toVector
  }

  val entries: Vector[Entry] = Vector(
    _object("name", "org.goldenport.datatype.Name", 1, 256, localized = false, Some("NameAttributes"), Set("name", "loginname"), replacementrecommended = false),
    _object("label", "org.goldenport.datatype.I18nLabel", 1, 256, localized = true, Some("NameAttributes"), Set("label")),
    _object("title", "org.goldenport.datatype.I18nTitle", 1, 256, localized = true, Some("NameAttributes"), Set("title")),
    _object("headline", "org.goldenport.datatype.I18nBrief", 1, 512, localized = true, Some("DescriptiveAttributes"), Set("headline")),
    _object("brief", "org.goldenport.datatype.I18nBrief", 1, 512, localized = true, Some("DescriptiveAttributes"), Set("brief")),
    _object("summary", "org.goldenport.datatype.I18nSummary", 1, 2048, localized = true, Some("DescriptiveAttributes"), Set("summary")),
    _object("lead", "org.goldenport.datatype.I18nSummary", 1, 2048, localized = true, Some("DescriptiveAttributes"), Set("lead")),
    _object("abstract", "org.goldenport.datatype.I18nSummary", 1, 2048, localized = true, Some("DescriptiveAttributes"), Set("abstract")),
    _object("remarks", "org.goldenport.datatype.I18nSummary", 1, 2048, localized = true, Some("DescriptiveAttributes"), Set("remarks")),
    _object("description", "org.goldenport.datatype.I18nDescription", 1, 8192, localized = true, Some("DescriptiveAttributes"), Set("description")),
    _object("text", "org.goldenport.datatype.I18nText", 1, 8192, localized = true, None, Set("text", "body", "message"), replacementrecommended = false),
    _object("identifier", "org.goldenport.datatype.Identifier", 1, 1000, localized = false, None, Set("identifier", "externalid", "external subject id"), replacementrecommended = false),
    _object("token", "org.goldenport.datatype.Token", 1, 256, localized = false, None, Set("token", "tokenhash", "passwordhash", "sessionreference", "clientid"), replacementrecommended = false),
    _object("url", "java.net.URL", 1, 2048, localized = false, None, Set("url"), format = Some("url")),
    _object("uri", "java.net.URI", 1, 2048, localized = false, None, Set("uri"), format = Some("uri")),
    _object("urn", "org.goldenport.datatype.Urn", 1, 2048, localized = false, None, Set("urn")),
    _object("locale", "java.util.Locale", 2, 35, localized = false, None, Set("locale")),
    _object("timezone", "java.util.TimeZone", 1, 255, localized = false, None, Set("timezone", "time zone"), aliases = Set("time-zone", "time_zone")),
    _object("ip-address", "org.goldenport.datatype.IpAddress", 2, 45, localized = false, None, Set("ipaddress", "ip address"), aliases = Set("ip", "ipaddress", "ip_address")),
    _object("email", "org.goldenport.datatype.EmailAddress", 3, 254, localized = false, None, Set("email", "emailaddress", "email address"), format = Some("email")),
    _object("phone", "org.goldenport.datatype.PhoneNumber", 8, 16, localized = false, None, Set("phone", "phonenumber", "phone number"), aliases = Set("tel", "e164"), format = Some("phone"))
  )

  private val _entries_by_name: Map[String, Entry] =
    entries.flatMap(x => (x.aliases + x.name).map(name => _normalize(name) -> x)).toMap

  def get(p: String): Option[Entry] =
    _entries_by_name.get(_normalize(p))

  def suggestion(p: String): Option[Entry] = {
    val normalized = _normalize(p)
    entries.find(_.diagnosticsuffixes.exists(suffix => normalized.endsWith(_normalize(suffix))))
  }

  private def _object(
    name: String,
    runtimeclassname: String,
    minlength: Int,
    maxlength: Int,
    localized: Boolean,
    attributegroup: Option[String],
    diagnosticsuffixes: Set[String],
    aliases: Set[String] = Set.empty,
    format: Option[String] = None,
    replacementrecommended: Boolean = true
  ): Entry =
    Entry(name, aliases, Some(runtimeclassname), format, Some(minlength), Some(maxlength), localized, attributegroup, diagnosticsuffixes, replacementrecommended)

  private def _normalize(p: String): String =
    p.trim.toLowerCase(java.util.Locale.ROOT).filter(_.isLetterOrDigit)
}
