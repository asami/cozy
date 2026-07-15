import java.time.Instant
import java.util.Locale
import cats.data.NonEmptyVector
import domain.datatype.LoginName
import io.circe.{Decoder, Json}
import org.goldenport.datatype.{EmailAddress, I18nBrief, I18nDescription, I18nString, I18nSummary, I18nTitle, Identifier, IpAddress, PhoneNumber}
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
        description = I18nDescription("Detail")
      )

      Then("construction succeeds")
      notice.senderName shouldBe "Alice"
      notice.email.value shouldBe "alice@example.com"
      notice.phone.map(_.value) shouldBe Some("+819012345678")
      notice.ipAddress.map(_.value) shouldBe Some("192.0.2.10")
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
      loginname.toDataStore() shouldBe "user_alice"
      read shouldBe LoginName("user_bob")
      decoded shouldBe Right(LoginName("user_carol"))

      And("every construction boundary enforces the declared constraints")
      val short = the[IllegalArgumentException] thrownBy LoginName("user")
      short.getMessage should include("value entries must have length >= 5")
      val pattern = the[IllegalArgumentException] thrownBy LoginName("admin_alice")
      pattern.getMessage should include("value entries must match ^user.+$")
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
  }

  private def _create_notice(
    title: I18nTitle = I18nTitle("Title"),
    headline: I18nBrief = I18nBrief("Lead"),
    summary: I18nSummary = I18nSummary("Brief"),
    description: I18nDescription = I18nDescription("Detail")
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
      email = EmailAddress.parse("alice@EXAMPLE.COM").TAKE,
      phone = Some(PhoneNumber.parse("+81 90-1234-5678").TAKE),
      ipAddress = Some(IpAddress.parse("192.0.2.10").TAKE)
    )

  private def _update_notice(
    descriptiveattributes: DescriptiveAttributesUpdate
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
      email = Update.noop,
      phone = Update.noop,
      ipAddress = Update.noop
    )

  private def _i18n(entries: (Locale, String)*): I18nString =
    I18nString(NonEmptyVector.fromVectorUnsafe(entries.toVector))
}
