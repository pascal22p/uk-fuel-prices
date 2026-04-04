package connectors

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{notFound, ok, urlEqualTo, exactly => wmExactly, postRequestedFor}
import config.AppConfig
import org.mockito.Mockito.when
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsResultException
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import testUtils.{BasePerTestSpec, WireMockHelper}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

class OAuthConnectorSpec extends BasePerTestSpec with WireMockHelper {

  val mockAppConfig: AppConfig = mock[AppConfig]
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  protected override def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        bind[AppConfig].toInstance(mockAppConfig)
      )


  implicit val hc: HeaderCarrier = HeaderCarrier()

  val oAuthResponse: String =
    """
      |{
      |  "success": true,
      |  "data": {
      |    "access_token": "632ab214482946527e7d7e5f522d4019639add5ebd20795b0d5fd8d19b565153",
      |    "token_type": "Bearer",
      |    "expires_in": 3600,
      |    "refresh_token": "7ad38ea6dbcf1123aef61785b0d6a8f3455bb68734080e0befa440c6ca6ee0eb"
      |  },
      |  "message": "Operation successful"
      |}
      |""".stripMargin

  "fuelStations" must {
    "return a token" in {
      lazy val app: Application = localGuiceApplicationBuilder().build()
      lazy val sut: OAuthConnector = app.injector.instanceOf[OAuthConnector]

      when(mockAppConfig.fuelApiHost).thenReturn(s"http://localhost:${server.port()}")
      when(mockAppConfig.clientId).thenReturn(s"client-id")
      when(mockAppConfig.clientSecret).thenReturn(s"client-secret")

      server.stubFor(
        WireMock
          .post(urlEqualTo("/api/v1/oauth/generate_access_token"))
          .withRequestBody(WireMock.equalTo(
            "grant_type=client_credentials&client_id=client-id&client_secret=client-secret"
          ))
          .willReturn(ok(oAuthResponse))
      )

      val result = sut.getValidToken().value.futureValue

      result mustBe Right("632ab214482946527e7d7e5f522d4019639add5ebd20795b0d5fd8d19b565153")

      server.verify(wmExactly(1), postRequestedFor(urlEqualTo("/api/v1/oauth/generate_access_token")))
    }

    "return a token and cache it" in {
      lazy val app: Application = localGuiceApplicationBuilder().build()
      lazy val sut: OAuthConnector = app.injector.instanceOf[OAuthConnector]

      when(mockAppConfig.fuelApiHost).thenReturn(s"http://localhost:${server.port()}")
      when(mockAppConfig.clientId).thenReturn(s"client-id")
      when(mockAppConfig.clientSecret).thenReturn(s"client-secret")

      server.stubFor(
        WireMock
          .post(urlEqualTo("/api/v1/oauth/generate_access_token"))
          .withRequestBody(WireMock.equalTo(
            "grant_type=client_credentials&client_id=client-id&client_secret=client-secret"
          ))
          .willReturn(ok(oAuthResponse))
      )

      val result = (for {
        _ <- sut.getValidToken().value
        _ <- sut.getValidToken().value
        result <- sut.getValidToken().value
      } yield {
        result
      }).futureValue

      result mustBe Right("632ab214482946527e7d7e5f522d4019639add5ebd20795b0d5fd8d19b565153")

      server.verify(wmExactly(1), postRequestedFor(urlEqualTo("/api/v1/oauth/generate_access_token")))
    }

    "return an UpstreamErrorResponse" in {
      lazy val app: Application = localGuiceApplicationBuilder().build()
      lazy val sut: OAuthConnector = app.injector.instanceOf[OAuthConnector]

      when(mockAppConfig.fuelApiHost).thenReturn(s"http://localhost:${server.port()}")
      when(mockAppConfig.clientId).thenReturn(s"client-id")
      when(mockAppConfig.clientSecret).thenReturn(s"client-secret")

      server.stubFor(
        WireMock
          .post(urlEqualTo("/api/v1/oauth/generate_access_token"))
          .withRequestBody(WireMock.equalTo(
            "grant_type=client_credentials&client_id=client-id&client_secret=client-secret"
          ))
          .willReturn(notFound())
      )

      val result = sut.getValidToken().value.futureValue

      result mustBe a[Left[UpstreamErrorResponse, ?]]
    }

    "throw an exception" in {
      lazy val app: Application = localGuiceApplicationBuilder().build()
      lazy val sut: OAuthConnector = app.injector.instanceOf[OAuthConnector]

      when(mockAppConfig.fuelApiHost).thenReturn(s"http://localhost:${server.port()}")
      when(mockAppConfig.clientId).thenReturn(s"client-id")
      when(mockAppConfig.clientSecret).thenReturn(s"client-secret")

      server.stubFor(
        WireMock
          .post(urlEqualTo("/api/v1/oauth/generate_access_token"))
          .withRequestBody(WireMock.equalTo(
            "grant_type=client_credentials&client_id=client-id&client_secret=client-secret"
          ))
          .willReturn(ok("{}"))
      )

      val result = intercept[JsResultException] {
        await(sut.getValidToken().value)
      }

      result mustBe a[JsResultException]
    }
  }

}
