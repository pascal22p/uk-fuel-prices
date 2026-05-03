package services

import cats.data.OptionT
import cats.implicits.*
import connectors.PostcodesIOConnector
import models.{FuelStation, FuelStationWithPrices}
import models.journeyCache.UserAnswers
import models.journeyCache.UserAnswersKey.*
import net.sf.geographiclib.Geodesic
import models.SearchByPostcodeViewModel
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
  def getViewModel(journeyCache: UserAnswers)(implicit hc: HeaderCarrier): OptionT[Future, SearchByPostcodeViewModel] = {
    for {
      postcode <- OptionT.fromOption[Future](journeyCache.getOptionalItem(ChoosePostcodeQuestion))
      fuelType <- OptionT.fromOption[Future](journeyCache.getOptionalItem(ChooseFuelTypeQuestion))
      radius <- OptionT.fromOption[Future](journeyCache.getOptionalItem(ChooseRadiusQuestion))
      coordinates <- postcodesIOConnector.getCoordinates(postcode.postcode).toOption
      boundingBox <- OptionT.some[Future](GeoBoundingBox.fromRadius(coordinates._1, coordinates._2, radius.radiusInMiles * 1.60934))
      fuelStationsCandidates <- OptionT.liftF(getSqlQueries.getFuelStations(boundingBox))
      fuelStations: Seq[FuelStation] = fuelStationsCandidates.filter { station =>
        Geodesic.WGS84.Inverse(coordinates._1, coordinates._2, station.location.latitude, station.location.longitude).s12 <= radius.radiusInMiles * 1.60934 * 1000.0
      }
      fuelPrices <- OptionT.liftF(fuelStations.traverse(station => getSqlQueries.findPricesForStation(station.nodeId).map(station.nodeId -> _)).map(_.toMap))
    } yield {
      val fuelStationWithPrices = fuelStations.map { station =>
        FuelStationWithPrices(station, fuelPrices.getOrElse(station.nodeId, Seq.empty).filter(fuel => s"${fuel.fuelType}" == s"$fuelType"))
      }
        .filter(_.fuelPrices.nonEmpty)
        .sortBy(_.fuelPrices.take(1).headOption.map(_.price).getOrElse(0.0))

      SearchByPostcodeViewModel(fuelStationWithPrices, postcode.postcode, coordinates)
    }
  }
}
