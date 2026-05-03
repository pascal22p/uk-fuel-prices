package connectors

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{notFound, ok, urlEqualTo}
import config.AppConfig
import org.mockito.Mockito.when
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsResultException
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

class PostcodesIOConnectorSpec extends BaseSpec with WireMockHelper {

  val mockAppConfig: AppConfig = mock[AppConfig]

  protected override def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        bind[AppConfig].toInstance(mockAppConfig)
      )

  lazy val sut: PostcodesIOConnector = app.injector.instanceOf[PostcodesIOConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val postcodeResponse: String = """{
                                   |  "status": 200,
                                   |  "result": {
                                   |    "postcode": "postcode",
                                   |    "longitude": -0.141563,
                                   |    "latitude": 51.50101
                                   |  }
                                   |}""".stripMargin

  "getCoordinates" must {
    "return a coordinate from a postcode" in {

      when(mockAppConfig.postcodeIOHost).thenReturn(s"http://localhost:${server.port()}")

      server.stubFor(
        WireMock
          .get(urlEqualTo("/AA"))
          .willReturn(ok(postcodeResponse))
      )

      val result = sut.getCoordinates("AA").value.futureValue

      result mustBe Right((51.50101, -0.141563))
    }

    "return an UpstreamErrorResponse" in {

      when(mockAppConfig.postcodeIOHost).thenReturn(s"http://localhost:${server.port()}")

      server.stubFor(
        WireMock
          .get(urlEqualTo("/AA"))
          .willReturn(notFound())
      )

      val result = sut.getCoordinates("AA").value.futureValue

      result mustBe a[Left[UpstreamErrorResponse, ?]]
    }

    "throw an exception" in {

      when(mockAppConfig.postcodeIOHost).thenReturn(s"http://localhost:${server.port()}")

      server.stubFor(
        WireMock
          .get(urlEqualTo("/AA"))
          .willReturn(ok("{}"))
      )

      val result = intercept[JsResultException] {
        await(sut.getCoordinates("AA").value)
      }

      result mustBe a[JsResultException]
    }
  }

}
