import java.time.Instant
import java.util.Locale
import cats.data.NonEmptyVector
import domain.datatype.LoginName
import io.circe.{Decoder, Json}
import io.circe.parser.parse
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.http.StaticFormAppRenderer
import org.goldenport.cncf.openapi.OpenApiProjector
import org.goldenport.cncf.projection.HelpProjection
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.datatype.{EmailAddress, I18nBrief, I18nDescription, I18nString, I18nSummary, I18nText, I18nTitle, Identifier, IpAddress, PhoneNumber}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}
import org.simplemodeling.model.value.*

final class GeneratedTextConstraintSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Generated CML text constraints" should {
    "accept single-locale create values within every declared bound" in {
      Given("single-locale title and descriptive attributes within their bounds")

      When("the generated create model is constructed")
      val notice = _create_notice(
        title = I18nTitle("Title"),
        headline = I18nBrief("Lead"),
        summary = I18nSummary("Brief"),
        description = I18nDescription("Detail"),
        narrative = I18nText("Notice")
      )

      Then("construction succeeds")
      notice.senderName shouldBe "Alice"
      notice.email.value shouldBe "alice@example.com"
      notice.phone.map(_.value) shouldBe Some("+819012345678")
      notice.ipAddress.map(_.value) shouldBe Some("192.0.2.10")
      notice.narrative.toI18nString.entries.toVector shouldBe Vector(Locale.ROOT -> "Notice")
    }

    "validate every locale entry in plain narrative text" in {
      Given("plain narrative text with English and Japanese entries")
      val valid = I18nText(_i18n(Locale.ENGLISH -> "Notice", Locale.JAPANESE -> "通知本文"))
      val invalid = I18nText(_i18n(Locale.ENGLISH -> "Notice", Locale.JAPANESE -> "長すぎる通知本文文字列"))

      When("the generated create and update models are constructed")
      val created = _create_notice(narrative = valid)
      val updated = _update_notice(
        DescriptiveAttributesUpdate(),
        narrative = Update.set(valid)
      )

      Then("both boundaries preserve every locale entry")
      created.narrative shouldBe valid
      updated.narrative shouldBe Update.set(valid)

      And("both boundaries reject an invalid locale entry")
      val createfailure = the[IllegalArgumentException] thrownBy _create_notice(narrative = invalid)
      createfailure.getMessage should include("narrative entries must have length <= 8")
      val updatefailure = the[IllegalArgumentException] thrownBy _update_notice(
        DescriptiveAttributesUpdate(),
        narrative = Update.set(invalid)
      )
      updatefailure.getMessage should include("narrative entries must have length <= 8")
    }

    "validate every locale entry in create values" in {
      Given("a title whose English entry is valid and Japanese entry is too long")
      val title = I18nTitle(_i18n(Locale.ENGLISH -> "Title", Locale.JAPANESE -> "長すぎるタイトル文字列"))

      When("the generated create model is constructed")
      val failure = the[IllegalArgumentException] thrownBy _create_notice(title = title)

      Then("the invalid locale entry is rejected")
      failure.getMessage should include("title entries must have length <= 8")
    }

    "validate headline summary and description in update values" in {
      Given("an update with valid multilingual descriptive attributes")
      val valid = DescriptiveAttributesUpdate(
        headline = Update.set(I18nBrief(_i18n(Locale.ENGLISH -> "Lead", Locale.JAPANESE -> "見出し"))),
        summary = Update.set(I18nSummary(_i18n(Locale.ENGLISH -> "Brief", Locale.JAPANESE -> "概要"))),
        description = Update.set(I18nDescription(_i18n(Locale.ENGLISH -> "Detail", Locale.JAPANESE -> "説明")))
      )

      When("the generated update model is constructed")
      val update = _update_notice(valid)

      Then("all valid locale entries are accepted")
      update.descriptiveAttributes shouldBe valid

      And("each descriptive attribute rejects an overlong locale entry")
      Vector(
        DescriptiveAttributesUpdate(headline = Update.set(I18nBrief("Too long headline"))) -> "headline",
        DescriptiveAttributesUpdate(summary = Update.set(I18nSummary("Too long summary"))) -> "summary",
        DescriptiveAttributesUpdate(description = Update.set(I18nDescription("Too long description"))) -> "description"
      ).foreach { case (attributes, field) =>
        val failure = the[IllegalArgumentException] thrownBy _update_notice(attributes)
        failure.getMessage should include(s"$field entries must have length <= 8")
      }
    }

    "ignore no-op update values while validating explicit updates" in {
      Given("an update with no changed text values")

      When("the generated update model is constructed")
      val update = _update_notice(DescriptiveAttributesUpdate())

      Then("no-op values satisfy the constraints")
      update.senderName shouldBe Update.noop[String]
    }

    "compile and execute constrained nominal scalar boundaries" in {
      Given("a CML plain DATATYPE with text constraints")

      When("the generated nominal scalar is constructed and decoded from its scalar representation")
      val loginname = LoginName("user_alice")
      val read = summon[org.goldenport.convert.ValueReader[LoginName]].readC("user_bob").TAKE
      val decoded = summon[Decoder[LoginName]].decodeJson(Json.fromString("user_carol"))

      Then("construction, ValueReader, codec, and datastore projection use the nominal scalar contract")
      loginname.toRecord().getString("value") shouldBe Some("user_alice")
      loginname.toDataStore() shouldBe "user_alice"
      read shouldBe LoginName("user_bob")
      decoded shouldBe Right(LoginName("user_carol"))

      And("generated operation request construction preserves the same scalar boundary")
      val request = domain.value.LookupAccountQuery
        .createC(Record.dataAuto("loginName" -> "user_dave"))
        .TAKE
      request.loginName shouldBe LoginName("user_dave")
      domain.value.LookupAccountQuery
        .createC(Record.dataAuto("loginName" -> "invalid"))
        .isSuccess shouldBe false

      And("every construction boundary enforces the declared constraints")
      val short = the[IllegalArgumentException] thrownBy LoginName("user")
      short.getMessage should include("value must have length >= 5")
      val pattern = the[IllegalArgumentException] thrownBy LoginName("admin_alice")
      pattern.getMessage should include("value must match ^user.+$")
      summon[org.goldenport.convert.ValueReader[LoginName]].readC("user_name_too_long").isSuccess shouldBe false
      summon[Decoder[LoginName]].decodeJson(Json.fromString("invalid")).isLeft shouldBe true
    }

    "project nominal scalar constraints through every entity boundary schema" in {
      Given("a generated entity field backed by a constrained nominal scalar")

      When("the compiled canonical schema is inspected")
      val column = domain.entity.Notice.schema.columns.find(_.name.value == "loginName").getOrElse {
        fail("loginName schema column is missing")
      }

      Then("the nominal scalar type constraints are available as Web validation hints")
      column.web.validation.minLength shouldBe Some(5)
      column.web.validation.maxLength shouldBe Some(12)
      column.web.validation.pattern shouldBe Some("^user.+$")

      And("Create, Query, and Update reuse the same canonical schema contract")
      domain.entity.create.Notice.schema shouldBe domain.entity.Notice.schema
      domain.entity.query.Notice.schema shouldBe domain.entity.Notice.schema
      domain.entity.update.Notice.schema shouldBe domain.entity.Notice.schema
    }

    "project one nominal scalar contract through generated operation surfaces" in {
      Given("a generated query operation whose required input uses the constrained LoginName datatype")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val component = new domain.impl.ComponentFactory()
        .create(ComponentCreate(subsystem, ComponentOrigin.Repository("text-constraint-runtime")))
        .primary
      subsystem.add(component)

      When("CNCF projects Help, automatic OpenAPI, and the HTML operation form")
      val selector = s"${component.name}.accountLookup.lookupAccount"
      val help = HelpProjection.projectModel(component, Some(selector))
      val openapi = parse(OpenApiProjector.forSubsystem(subsystem)).fold(
        error => fail(s"OpenAPI JSON parse failed: ${error.getMessage}"),
        identity
      )
      val form = StaticFormAppRenderer()
        .renderOperationForm(subsystem, component.name, "accountLookup", "lookupAccount")
        .map(_.body)
        .getOrElse(fail("generated nominal scalar operation form is missing"))

      Then("Help exposes the nominal datatype and its authored validation contract")
      help.details("argumentDetails") shouldBe Vector(
        "loginName: loginname 1 [min-length=5, max-length=12, pattern=^user.+$]"
      )

      And("automatic OpenAPI projects the same required scalar constraints")
      val request = openapi.hcursor
        .downField("paths")
        .downField("/rest/v1/text-constraint/account-lookup/lookup-account")
        .downField("POST")
        .downField("requestBody")
        .downField("content")
        .downField("application/json")
        .downField("schema")
      request.get[Vector[String]]("required") shouldBe Right(Vector("loginName"))
      val loginname = request.downField("properties").downField("loginName")
      loginname.get[Int]("minLength") shouldBe Right(5)
      loginname.get[Int]("maxLength") shouldBe Right(12)
      loginname.get[String]("pattern") shouldBe Right("^user.+$")

      And("the standard HTML form uses the same required nominal scalar boundary")
      form should include ("""name="loginName"""")
      form should include ("""required""")
      form should include ("""minlength="5"""")
      form should include ("""maxlength="12"""")
      form should include ("""pattern="^user.+$"""")
    }
  }

  private def _create_notice(
    title: I18nTitle = I18nTitle("Title"),
    headline: I18nBrief = I18nBrief("Lead"),
    summary: I18nSummary = I18nSummary("Brief"),
    description: I18nDescription = I18nDescription("Detail"),
    narrative: I18nText = I18nText("Notice")
  ): domain.entity.create.Notice =
    domain.entity.create.Notice(
      id = None,
      nameAttributes = NameAttributes.Builder().withName("notice").withTitle(title).build(),
      descriptiveAttributes = DescriptiveAttributes.Builder()
        .withHeadline(headline)
        .withSummary(summary)
        .withDescription(description)
        .build(),
      contentAttributes = ContentAttributes.empty,
      lifecycleAttributes = LifecycleAttributes(
        Instant.EPOCH,
        Instant.EPOCH,
        Identifier("system"),
        Identifier("system"),
        PostStatus.default,
        Aliveness.default
      ),
      publicationAttributes = PublicationAttributes(None, None, None, None, None),
      securityAttributes = SecurityAttributes.privateOwnedBy("system"),
      resourceAttributes = ResourceAttributes(),
      auditAttributes = AuditAttributes(),
      mediaAttributes = MediaAttributes(None, Vector.empty, Vector.empty, Vector.empty, Vector.empty),
      contextualAttribute = ContextualAttributes(),
      senderName = "Alice",
      recipientName = "Bob",
      loginName = LoginName("user_alice"),
      narrative = narrative,
      email = EmailAddress.parse("alice@EXAMPLE.COM").TAKE,
      phone = Some(PhoneNumber.parse("+81 90-1234-5678").TAKE),
      ipAddress = Some(IpAddress.parse("192.0.2.10").TAKE)
    )

  private def _update_notice(
    descriptiveattributes: DescriptiveAttributesUpdate,
    narrative: Update[I18nText] = Update.noop
  ): domain.entity.update.Notice =
    domain.entity.update.Notice(
      id = Update.noop,
      nameAttributes = NameAttributesUpdate(),
      descriptiveAttributes = descriptiveattributes,
      contentAttributes = ContentAttributesUpdate(),
      lifecycleAttributes = LifecycleAttributesUpdate(),
      publicationAttributes = PublicationAttributesUpdate(),
      securityAttributes = SecurityAttributesUpdate(),
      resourceAttributes = ResourceAttributesUpdate(),
      auditAttributes = AuditAttributesUpdate(),
      mediaAttributes = MediaAttributesUpdate(),
      contextualAttribute = ContextualAttributesUpdate(),
      senderName = Update.noop,
      recipientName = Update.noop,
      loginName = Update.noop,
      narrative = narrative,
      email = Update.noop,
      phone = Update.noop,
      ipAddress = Update.noop
    )

  private def _i18n(entries: (Locale, String)*): I18nString =
    I18nString(NonEmptyVector.fromVectorUnsafe(entries.toVector))
}
