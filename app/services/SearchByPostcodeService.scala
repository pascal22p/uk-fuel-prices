package services

import cats.data.EitherT
import cats.implicits.*
import connectors.PostcodesIOConnector
import models.{FuelType, SearchByPostcodeViewModel}
import net.sf.geographiclib.Geodesic
import queries.GetSqlQueries
import uk.gov.hmrc.http.HeaderCarrier
import utils.GeoBoundingBox

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import io.opentelemetry.instrumentation.annotations.WithSpan

@Singleton
class SearchByPostcodeService @Inject()(
    postcodesIOConnector: PostcodesIOConnector,
    getSqlQueries: GetSqlQueries
                                       )(
    implicit ec: ExecutionContext
) {

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  @WithSpan
  def getViewModel(postcode: String, fuelType: FuelType, radius: Double)(implicit hc: HeaderCarrier): EitherT[Future, Throwable, SearchByPostcodeViewModel] = {
    for {
      coordinates <- postcodesIOConnector.getCoordinates(postcode).leftMap(identity[Throwable])
      boundingBox <- EitherT.rightT(GeoBoundingBox.fromRadius(coordinates.latitude, coordinates.longitude, radius * 1.60934))
      fuelStationsCandidates <- EitherT.liftF(getSqlQueries.getFuelStations(boundingBox))
      fuelStations = fuelStationsCandidates.filter { station =>
        Geodesic.WGS84.Inverse(coordinates.latitude, coordinates.longitude, station.location.latitude, station.location.longitude).s12 <= radius * 1.60934 * 1000.0
      }
      fuelStationWithPrices <-
        EitherT.liftF(
          getSqlQueries.findPricesForStations(fuelStations.map(_.nodeId)).map { fuelStationsWithPrices =>
            fuelStationsWithPrices.map { fuelStation =>
              val latestPrice = fuelStation.fuelPrices.filter(_.fuelType == fuelType).maxByOption(_.priceChangeEffectiveTimestamp).toList
              val distance    = Geodesic.WGS84.Inverse(coordinates._1, coordinates._2, fuelStation.location.latitude, fuelStation.location.longitude).s12

              fuelStation.copy(fuelPrices = latestPrice, distance = distance)
            }.toList
          }
        )
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
