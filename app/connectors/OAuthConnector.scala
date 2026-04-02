package connectors

import cats.data.EitherT
import config.AppConfig
import models.CachedToken
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import play.api.libs.json.*
import play.api.libs.ws.DefaultBodyWritables.*
import uk.gov.hmrc.http.HttpReads.Implicits.*

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OAuthConnector @Inject()(
                              appConfig: AppConfig,
                              http: HttpClientV2,
                              httpClientResponse: HttpClientResponse
                            )(implicit ec: ExecutionContext) {

  @volatile private var cachedToken: Option[CachedToken] = None

  private val ExpirySafetyBufferMillis = 30 * 1000 // 30 seconds buffer

  def getValidToken()(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, String] = {
    cachedToken match {
      case Some(token) if !isExpired(token) =>
        EitherT.rightT(token.accessToken)

      case _ =>
        fetchNewToken().map { newToken =>
          cachedToken = Some(newToken)
          newToken.accessToken
        }
    }
  }

  private def isExpired(token: CachedToken): Boolean = {
    System.currentTimeMillis() >= token.expiresAt - ExpirySafetyBufferMillis
  }

  private def fetchNewToken()(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, CachedToken] = {

    val formData: Map[String, Seq[String]] = Map(
      "grant_type" -> Seq("client_credentials"),
      "client_id" -> Seq(appConfig.clientId),
      "client_secret" -> Seq(appConfig.clientSecret)
    )

    httpClientResponse.read(http.post(url"${appConfig.tokenUrl}")
      .withBody(formData)
      .execute[Either[UpstreamErrorResponse, HttpResponse]])
      .map { response =>
        val json = response.json
        val accessToken = (json \ "data" \ "access_token").as[String]
        val expiresIn   = (json \ "data" \ "expires_in").as[Int] // seconds

        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

        CachedToken(accessToken, expiresAt)
      }
  }
}