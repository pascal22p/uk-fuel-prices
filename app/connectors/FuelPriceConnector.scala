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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@Singleton
class FuelPriceConnector @Inject()(
                                    appConfig: AppConfig,
                                    httpClient: HttpClientV2,
                                    oauthConnector: OAuthConnector,
                                    httpClientResponse: HttpClientResponse)
                                  (implicit ec: ExecutionContext) extends LoggingWithRequest {

  private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def fuelStations(batchNumber: Int, effectiveStartDate : Option[LocalDateTime] = None)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Seq[FuelStation]] = {
    val startDate = effectiveStartDate.fold("") { date => s"&effective-start-timestamp=${date.format(formatter)}"}
    val url = s"${appConfig.fuelApiHost}/api/v1/pfs?batch-number=$batchNumber$startDate"
    logger.info(s"Calling pfs (Petrol Fuel Station) api at $url")

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

  def fuelPrices(batchNumber: Int, effectiveStartDate: Option[LocalDateTime] = None)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Seq[FuelPriceForStation]] = {
    val startDate = effectiveStartDate.fold("") { date => s"&effective-start-timestamp=${date.format(formatter)}"}
    val url = s"${appConfig.fuelApiHost}/api/v1/pfs/fuel-prices?batch-number=$batchNumber$startDate"
    logger.info(s"Calling fuel prices api at $url")

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
