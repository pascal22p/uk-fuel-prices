package connectors

import cats.data.EitherT
import models.{FuelStation, LoggingWithRequest}
import models.FuelStation.ValidationResult
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HttpReads.Implicits.*


@Singleton
class FuelPriceConnector @Inject()(
                                    httpClient: HttpClientV2,
                                    oauthConnector: OAuthConnector,
                                    httpClientResponse: HttpClientResponse)
                                  (implicit ec: ExecutionContext) extends LoggingWithRequest {
  def pfs(batchNumber: Int)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Seq[FuelStation]] = {
    val url = s"https://www.fuel-finder.service.gov.uk/api/v1/pfs?batch-number=$batchNumber"
    logger.info(s"Calling pfs (Petrol Fuel Station) api with batch number $batchNumber")

    def getPfs(token: String): EitherT[Future, UpstreamErrorResponse, ValidationResult[Seq[FuelStation]]] = {
      httpClientResponse.read(
          httpClient.get(url"$url")
            .setHeader("Authorization" -> s"Bearer $token")
            .setHeader("Accept" -> "application/json")
            .execute[Either[UpstreamErrorResponse, HttpResponse]]
        ).map(_.json.as[ValidationResult[Seq[FuelStation]]])
    }

    for {
      token <- oauthConnector.getValidToken()
      validation <- getPfs(token)
    } yield {
      validation match {
        case cats.data.Validated.Valid(stations) =>
          stations

        case cats.data.Validated.Invalid(errors) =>
          errors.toList.foreach(error => logger.warn(error))
          Seq.empty
      }
    }

  }
}
