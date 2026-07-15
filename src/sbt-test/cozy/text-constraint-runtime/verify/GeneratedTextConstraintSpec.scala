import java.time.Instant
import java.util.Locale
import cats.data.NonEmptyVector
import org.goldenport.datatype.{I18nBrief, I18nDescription, I18nString, I18nSummary, I18nTitle, Identifier}
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
      val notice = createNotice(
        title = I18nTitle("Title"),
        headline = I18nBrief("Lead"),
        summary = I18nSummary("Brief"),
        description = I18nDescription("Detail")
      )

      Then("construction succeeds")
      notice.senderName shouldBe "Alice"
    }

    "validate every locale entry in create values" in {
      Given("a title whose English entry is valid and Japanese entry is too long")
      val title = I18nTitle(i18n(Locale.ENGLISH -> "Title", Locale.JAPANESE -> "長すぎるタイトル文字列"))

      When("the generated create model is constructed")
      val failure = the[IllegalArgumentException] thrownBy createNotice(title = title)

      Then("the invalid locale entry is rejected")
      failure.getMessage should include("title entries must have length <= 8")
    }

    "validate headline summary and description in update values" in {
      Given("an update with valid multilingual descriptive attributes")
      val valid = DescriptiveAttributesUpdate(
        headline = Update.set(I18nBrief(i18n(Locale.ENGLISH -> "Lead", Locale.JAPANESE -> "見出し"))),
        summary = Update.set(I18nSummary(i18n(Locale.ENGLISH -> "Brief", Locale.JAPANESE -> "概要"))),
        description = Update.set(I18nDescription(i18n(Locale.ENGLISH -> "Detail", Locale.JAPANESE -> "説明")))
      )

      When("the generated update model is constructed")
      val update = updateNotice(valid)

      Then("all valid locale entries are accepted")
      update.descriptiveAttributes shouldBe valid

      And("each descriptive attribute rejects an overlong locale entry")
      Vector(
        DescriptiveAttributesUpdate(headline = Update.set(I18nBrief("Too long headline"))) -> "headline",
        DescriptiveAttributesUpdate(summary = Update.set(I18nSummary("Too long summary"))) -> "summary",
        DescriptiveAttributesUpdate(description = Update.set(I18nDescription("Too long description"))) -> "description"
      ).foreach { case (attributes, field) =>
        val failure = the[IllegalArgumentException] thrownBy updateNotice(attributes)
        failure.getMessage should include(s"$field entries must have length <= 8")
      }
    }

    "ignore no-op update values while validating explicit updates" in {
      Given("an update with no changed text values")

      When("the generated update model is constructed")
      val update = updateNotice(DescriptiveAttributesUpdate())

      Then("no-op values satisfy the constraints")
      update.senderName shouldBe Update.noop[String]
    }
  }

  private def createNotice(
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
      recipientName = "Bob"
    )

  private def updateNotice(
    descriptiveAttributes: DescriptiveAttributesUpdate
  ): domain.entity.update.Notice =
    domain.entity.update.Notice(
      id = Update.noop,
      nameAttributes = NameAttributesUpdate(),
      descriptiveAttributes = descriptiveAttributes,
      contentAttributes = ContentAttributesUpdate(),
      lifecycleAttributes = LifecycleAttributesUpdate(),
      publicationAttributes = PublicationAttributesUpdate(),
      securityAttributes = SecurityAttributesUpdate(),
      resourceAttributes = ResourceAttributesUpdate(),
      auditAttributes = AuditAttributesUpdate(),
      mediaAttributes = MediaAttributesUpdate(),
      contextualAttribute = ContextualAttributesUpdate(),
      senderName = Update.noop,
      recipientName = Update.noop
    )

  private def i18n(entries: (Locale, String)*): I18nString =
    I18nString(NonEmptyVector.fromVectorUnsafe(entries.toVector))
}
