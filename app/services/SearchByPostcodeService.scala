package services

import cats.data.EitherT
import cats.implicits.*
import connectors.PostcodesIOConnector
import models.{FuelStationWithPrices, FuelType, SearchByPostcodeViewModel}
import net.sf.geographiclib.Geodesic
import queries.GetSqlQueries
import uk.gov.hmrc.http.HeaderCarrier
import utils.GeoBoundingBox

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SearchByPostcodeService @Inject()(
    postcodesIOConnector: PostcodesIOConnector,
    getSqlQueries: GetSqlQueries
                                       )(
    implicit ec: ExecutionContext
) {
  def getViewModel(postcode: String, fuelType: FuelType, radius: Double)(implicit hc: HeaderCarrier): EitherT[Future, Throwable, SearchByPostcodeViewModel] = {
    for {
      coordinates <- postcodesIOConnector.getCoordinates(postcode).leftMap(identity[Throwable])
      boundingBox <- EitherT.rightT(GeoBoundingBox.fromRadius(coordinates._1, coordinates._2, radius * 1.60934))
      fuelStationsCandidates <- EitherT.liftF(getSqlQueries.getFuelStations(boundingBox))
      fuelStations = fuelStationsCandidates.filter { station =>
        Geodesic.WGS84.Inverse(coordinates._1, coordinates._2, station.location.latitude, station.location.longitude).s12 <= radius * 1.60934 * 1000.0
      }
      fuelStationWithPrices <- EitherT.liftF(fuelStations.traverse { station =>
        getSqlQueries.findPricesForStation(station.nodeId).map { fuelPrices =>
          val latestSelectFuel = fuelPrices.filter(ft => ft.fuelType == fuelType).maxByOption(_.priceChangeEffectiveTimestamp).toList
          FuelStationWithPrices(
            station, 
            latestSelectFuel,
            Geodesic.WGS84.Inverse(coordinates._1, coordinates._2, station.location.latitude, station.location.longitude).s12)
        }
      })
    } yield {
      SearchByPostcodeViewModel(
        fuelStationWithPrices.sortBy(_.fuelPrices.headOption.map(_.price)),
        postcode,
        coordinates,
        radius,
        fuelType)
    }
  }
}
