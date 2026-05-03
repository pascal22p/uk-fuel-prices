package connectors

import cats.data.EitherT
import config.AppConfig
import models.LoggingWithRequest
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class PostcodesIOConnector @Inject()(
                                    appConfig: AppConfig,
                                    httpClient: HttpClientV2,
                                    httpClientResponse: HttpClientResponse)
                                    (implicit ec: ExecutionContext) extends LoggingWithRequest {

  def getCoordinates(postcode: String)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, (Double, Double)] = {
    val url = s"${appConfig.postcodeIOHost}/${postcode.replace(" ", "")}"
    httpClientResponse.read(
      httpClient
        .get(url"$url")
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    ).map { response =>
      val json = response.json
      val latitude = (json \ "result" \ "latitude").as[Double]
      val longitude = (json \ "result" \ "longitude").as[Double]
      (latitude, longitude)
    }
  }
}