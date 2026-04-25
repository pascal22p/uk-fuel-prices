package services

import cats.data.EitherT
import cats.implicits.*
import connectors.FuelPriceConnector
import models.FuelPriceForStation
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import queries.{DeleteSqlQueries, GetSqlQueries, InsertSqlQueries}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FuelPriceService @Inject()(
                                fuelPriceConnector: FuelPriceConnector,
                                insertSqlQueries: InsertSqlQueries,
                                getSqlQueries: GetSqlQueries,
                                deleteSqlQueries: DeleteSqlQueries
                                )(implicit ec: ExecutionContext) extends Logging {

  final def uploadAllFuelStations(batchNumber: Int = 1, effectiveStartDate : Option[LocalDateTime] = None)
                                 (implicit hc: HeaderCarrier)
  : EitherT[Future, UpstreamErrorResponse, Boolean] = {
    // there is no pagination in the api, so we need to hit every batch until one is not found

    fuelPriceConnector.fuelStations(batchNumber, effectiveStartDate).flatMap {
      case stations if stations.isEmpty => EitherT.rightT(true)

      case stations =>
        EitherT.liftF(insertSqlQueries.insertStations(stations)).flatMap { _ =>
          uploadAllFuelStations(batchNumber + 1, effectiveStartDate)
            .map(_ => true)
        }
    }.transform {
      // not found response. End of the line, returning success.
      case Left(error) if error.statusCode == NOT_FOUND => Right(true)
      case result => result
    }
  }

  final def uploadAllFuelPrices(batchNumber: Int = 1, effectiveStartDate : Option[LocalDateTime] = None)
                                 (implicit hc: HeaderCarrier)
  : EitherT[Future, UpstreamErrorResponse, Boolean] = {
    // there is no pagination in the api, so we need to hit every batch until one is not found

    fuelPriceConnector.fuelPrices(batchNumber, effectiveStartDate).flatMap {
      case fuels if fuels.isEmpty => EitherT.rightT(true)

      case fuels =>
        val sanitisedFuels = fuels.map { fuelPrices =>
          fuelPrices.copy(fuelPrices =
            fuelPrices.fuelPrices.map { fuel =>
              val fixedPrice = fuel.price match {
                case price if price <= 10.0 => price * 100.0
                case price if price >= 1000.0 => price / 10.0
                case price => price
              }
              fuel.copy(price = fixedPrice)
            }
          )
        }

        val nodeIds = sanitisedFuels.map(_.nodeId)
        for {
          absentNodeIds <- EitherT.liftF[Future, UpstreamErrorResponse, Seq[String]](getSqlQueries.findAbsentFuelStations(nodeIds))
          validSanitisedFuels = sanitisedFuels.filterNot(fuel => absentNodeIds.contains(fuel.nodeId))
          _ <- EitherT.liftF[Future, UpstreamErrorResponse, Int](insertSqlQueries.insertFuelPrices(validSanitisedFuels))
          _ <- uploadAllFuelPrices(batchNumber + 1, effectiveStartDate)
        } yield {
          if(absentNodeIds.nonEmpty) logger.error(s"Absent fuel stations: $absentNodeIds")
          true
        }
    }.transform {
      // not found response. End of the line, returning success.
      case Left(error) if error.statusCode == NOT_FOUND => Right(true)
      case result => result
    }
  }

  def getFuelPriceFromPostcode(postcode: String): Future[Seq[FuelPriceForStation]] = {
    getSqlQueries.findFuelStations(postcode).flatMap { stations =>
      stations.traverse { station =>
        getSqlQueries.findPricesForStation(station.nodeId).map { fuelPrices =>
          FuelPriceForStation(station.nodeId, None, station.tradingName, fuelPrices)
        }
      }
    }
  }

  def removeOldFuelStations(since: LocalDateTime): Future[Int] =
    deleteSqlQueries.deleteStations(since)

  def removeOldFuelPrices(since: LocalDateTime): Future[Int] =
    deleteSqlQueries.deleteFuelPrices(since)

}
