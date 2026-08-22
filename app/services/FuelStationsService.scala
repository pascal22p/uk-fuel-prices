package services

import cats.data.EitherT
import cats.implicits.*
import connectors.FuelPriceConnector
import models.FuelStation
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.util.Locale
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FuelStationsService @Inject()(
                                fuelPriceConnector: FuelPriceConnector,
                                )(implicit ec: ExecutionContext) extends Logging {

  final def findFuelStations(
                              nodeId: String,
                              batchNumber: Int = 1
                            )(implicit hc: HeaderCarrier)
  : EitherT[Future, UpstreamErrorResponse, Option[FuelStation]] = {

    fuelPriceConnector.fuelStations(batchNumber, None).flatMap {
      case stations if stations.isEmpty =>
        EitherT.rightT(None)

      case stations =>
        stations.find(_.nodeId.toUpperCase(Locale.ENGLISH) == nodeId.toUpperCase(Locale.ENGLISH)) match {
          case Some(station) =>
            EitherT.rightT(Some(station))

          case None =>
            findFuelStations(nodeId, batchNumber + 1)
        }
    }
  }
  
}
