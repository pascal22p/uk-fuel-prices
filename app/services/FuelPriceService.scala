package services

import cats.data.EitherT
import connectors.FuelPriceConnector
import play.api.http.Status.NOT_FOUND
import queries.InsertSqlQueries
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FuelPriceService @Inject()(
                                fuelPriceConnector: FuelPriceConnector,
                                insertSqlQueries: InsertSqlQueries
                                )(implicit ec: ExecutionContext) {

  final def uploadAllFps(batchNumber: Int = 1)
                  (implicit hc: HeaderCarrier)
  : EitherT[Future, UpstreamErrorResponse, Boolean] = {
    // there is no pagination in the api, so you need to hit every batch until one is not found

    fuelPriceConnector.pfs(batchNumber).flatMap {
      case stations if stations.isEmpty => EitherT.rightT(true)

      case stations =>
        EitherT.liftF(insertSqlQueries.insertStations(stations)).flatMap { _ =>
          uploadAllFps(batchNumber + 1)
            .map(_ => true)
        }
    }.transform {
      // not found response. End of the line, returning success.
      case Left(error) if error.statusCode == NOT_FOUND => Right(true)
      case result => result
    }
  }

}
