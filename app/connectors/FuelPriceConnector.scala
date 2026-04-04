package connectors

import cats.data.EitherT
import config.AppConfig
import models.{FuelPriceForStation, FuelStation, LoggingWithRequest}
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HttpReads.Implicits.*


@Singleton
class FuelPriceConnector @Inject()(
                                    appConfig: AppConfig,
                                    httpClient: HttpClientV2,
                                    oauthConnector: OAuthConnector,
                                    httpClientResponse: HttpClientResponse)
                                  (implicit ec: ExecutionContext) extends LoggingWithRequest {

  def fuelStations(batchNumber: Int)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Seq[FuelStation]] = {
    val url = s"${appConfig.fuelApiHost}/api/v1/pfs?batch-number=$batchNumber"
    logger.info(s"Calling pfs (Petrol Fuel Station) api with batch number $batchNumber")

    def getPfs(token: String): EitherT[Future, UpstreamErrorResponse, Seq[FuelStation]] = {
      httpClientResponse.read(
          httpClient.get(url"$url")
            .setHeader("Authorization" -> s"Bearer $token")
            .setHeader("Accept" -> "application/json")
            .execute[Either[UpstreamErrorResponse, HttpResponse]]
        ).map { response =>
        response.json
          .as[Seq[JsValue]]
          .zipWithIndex
          .flatMap { case (item, index) =>
            item.validate[FuelStation].fold(
              errors => {
                logger.error(s"Failed at index $index: $errors")
                None
              },
              station => Some(station)
            )
          }
      }
    }

    for {
      token <- oauthConnector.getValidToken()
      fuelStations <- getPfs(token)
    } yield fuelStations
  }

  def fuelPrices(batchNumber: Int)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Seq[FuelPriceForStation]] = {
    val url = s"${appConfig.fuelApiHost}/api/v1/pfs/fuel-prices?batch-number=$batchNumber"
    logger.info(s"Calling fuel prices api with batch number $batchNumber")

    def getFuelPrices(token: String): EitherT[Future, UpstreamErrorResponse, Seq[FuelPriceForStation]] = {
      httpClientResponse.read(
        httpClient.get(url"$url")
          .setHeader("Authorization" -> s"Bearer $token")
          .setHeader("Accept" -> "application/json")
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      ).map { response =>
        response.json
          .as[Seq[JsValue]]
          .zipWithIndex
          .flatMap { case (item, index) =>
            item.validate[FuelPriceForStation].fold(
              errors => {
                logger.error(s"Failed at index $index: $errors")
                None
              },
              station => Some(station)
            )
          }
      }
    }

    for {
      token <- oauthConnector.getValidToken()
      fuelPrices <- getFuelPrices(token)
    } yield fuelPrices
  }

}
