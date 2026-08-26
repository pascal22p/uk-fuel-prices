package services

import cats.data.{EitherT, OptionT}
import cats.implicits.*
import config.AppConfig
import connectors.FuelPriceConnector
import models.{FuelPriceForStation, FuelStation, FuelStationWithPrices, GeoLoc}
import net.sf.geographiclib.Geodesic
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import queries.GetSqlQueries
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import utils.GeoBoundingBox

import java.util.Locale
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FuelStationsService @Inject()(
                                fuelPriceConnector: FuelPriceConnector,
                                getSqlQueries: GetSqlQueries,
                                appConfig: AppConfig
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
    }.transform {
      // not found response. End of the line, returning success.
      case Left(error) if error.statusCode == NOT_FOUND => Right(None)
      case result => result
    }
  }

  def getLatestFuelPricesWithStation(numberOfResult: Int, geoloc: Option[GeoLoc]): Future[Seq[FuelStationWithPrices]] = {
    val radius = appConfig.localStationsRadius
    (for {
      coordinates <- OptionT.fromOption[Future](geoloc)
      boundingBox <- OptionT.some(GeoBoundingBox.fromRadius(coordinates.latitude, coordinates.longitude, radius * 1.60934))
      fuelStationsCandidates <- OptionT.liftF(getSqlQueries.getFuelStations(boundingBox))
      fuelStations = fuelStationsCandidates.filter { station =>
        Geodesic.WGS84.Inverse(coordinates.latitude, coordinates.longitude, station.location.latitude, station.location.longitude).s12 <= radius * 1.60934 * 1000.0
      }
      lastUpdates <- 
        if(fuelStations.nonEmpty) {
          OptionT.liftF(getSqlQueries.getLatestFuelPricesWithStation(numberOfResult, fuelStations.map(_.nodeId)))
        } else {
          OptionT.some[Future](Seq.empty)
        }
    } yield {
      lastUpdates
    }).getOrElseF(getSqlQueries.getLatestFuelPricesWithStation(numberOfResult, Seq.empty))
  }

}
