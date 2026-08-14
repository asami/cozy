package cozy.web.arcadia

import java.util.Locale
import org.joda.time.DateTimeZone
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.context.{DateTimeContext, FormatContext}
import org.goldenport.record.v2.XString
import org.goldenport.record.v3.Record
import arcadia._
import arcadia.context.{PlatformContext, PlatformExecutionContext}
import arcadia.controller.ControllerEngine
import arcadia.domain.{DomainModel, PROP_DOMAIN_OBJECT_ID}
import arcadia.model.Model
import arcadia.service.ServiceFacility
import arcadia.view.{TemplateEngineHangar, ViewEngine}

/*
 * @since   Aug. 14, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class EngineSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Engine formatter policy" should {
    "preserve the Engine-created formatter through WebEngine and ViewEngine rendering" in {
      Given("a platform execution context with an explicit formatter context")
      val formatcontext = FormatContext.create(Locale.JAPAN, DateTimeZone.forID("Asia/Tokyo"))
      val platform = PlatformContext.develop
      val platformexecutioncontext = new PlatformExecutionContext.StandalonePlatformExecutionContext(
        platform,
        Locale.JAPAN,
        DateTimeContext.now,
        formatcontext
      )
      val application = WebApplication(
        "formatter",
        None,
        WebApplicationConfig.empty,
        ControllerEngine.Rule.empty,
        ViewEngine.Rule.error,
        DomainModel.empty,
        None
      )
      val webengine = new WebEngine(
        platform,
        TemplateEngineHangar.empty,
        new ServiceFacility(platform, Nil),
        application,
        Nil
      )
      val model = new CaptureModel
      val engine = new Engine(null, webengine, "formatter")

      When("Engine sends its parcel through WebEngine to ViewEngine")
      engine._apply_for_test(platformexecutioncontext, ViewCommand("formatter"), model)

      Then("the final render strategy retains the exact supplied format context")
      (model.renderStrategy.formatter.formatContext eq formatcontext) shouldBe true
    }

    "retain the exact platform format context supplied for a parcel" in {
      Given("a platform-owned format context")
      val formatcontext = FormatContext.create(Locale.JAPAN, DateTimeZone.forID("Asia/Tokyo"))

      When("the Engine formatter policy is applied")
      val formatter = Engine._formatter(formatcontext)

      Then("the formatter retains that exact context instead of the Arcadia default")
      (formatter.formatContext eq formatcontext) shouldBe true
    }

    "keep separately supplied format contexts separately owned" in {
      Given("two independently supplied platform format contexts")
      val formatcontexta = FormatContext.create(Locale.JAPAN, DateTimeZone.forID("Asia/Tokyo"))
      val formatcontextb = FormatContext.create(Locale.US, DateTimeZone.forID("America/New_York"))

      When("each context is applied to its own formatter")
      val formattera = Engine._formatter(formatcontexta)
      val formatterb = Engine._formatter(formatcontextb)

      Then("neither formatter captures a global or the other context")
      (formattera.formatContext eq formatcontexta) shouldBe true
      (formatterb.formatContext eq formatcontextb) shouldBe true
      (formattera.formatContext eq formatterb.formatContext) shouldBe false
    }
  }

  "Engine domain-object ID schema policy" should {
    "use XString for arbitrary opaque IDs" in {
      Given("the Engine ID column policy")
      val idcolumn = Engine._domain_object_id_column.toColumn
      val opaqueid = "provider/record:7f3b?revision=α"

      When("the policy is used for a domain-object ID")
      val idvalue = Engine._domain_object_id_datatype.toInstance(opaqueid)

      Then("the real ID column remains an opaque string without provider-specific typing")
      idcolumn.name shouldBe PROP_DOMAIN_OBJECT_ID
      idcolumn.datatype shouldBe XString
      idvalue shouldBe opaqueid
    }
  }

  private class CaptureModel extends Model {
    private var _render_strategy: Option[arcadia.view.RenderStrategy] = None

    def renderStrategy: arcadia.view.RenderStrategy = _render_strategy.get
    def expiresKind = None
    def toRecord: Record = Record.empty
    def render(strategy: arcadia.view.RenderStrategy) = {
      _render_strategy = Some(strategy)
      scala.xml.Text("formatter")
    }
  }
}
