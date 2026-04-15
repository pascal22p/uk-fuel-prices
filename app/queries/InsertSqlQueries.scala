package queries

import anorm.*
import models.*
import play.api.db.Database

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
final class InsertSqlQueries @Inject()(db: Database, databaseExecutionContext: DatabaseExecutionContext)
    extends LoggingWithRequest {

  def insertStations(fuelStations: Seq[FuelStation]): Future[Int] = Future {
    val sqlStatement = """REPLACE INTO `fuel_stations`
                         | (`nodeId`, `tradingName`, `isSameTradingAndBrandName`, `brandName`, `temporaryClosure`, `permanentClosure`, `isMotorwayServiceStation`, `isSupermarketServiceStation`, `fuelTypes`, `addressLine1`, `addressLine2`, `city`, `country`, `county`, `postcode`, `latitude`, `longitude`)
                         | VALUES ({nodeId}, {tradingName}, {isSameTradingAndBrandName}, {brandName}, {temporaryClosure}, {permanentClosure}, {isMotorwayServiceStation}, {isSupermarketServiceStation}, {fuelTypes}, {addressLine1}, {addressLine2}, {city}, {country}, {county}, {postcode}, {latitude}, {longitude})
          """.stripMargin

    val parameters = fuelStations.map { station =>
      Seq[NamedParameter](
        "nodeId"                        -> station.nodeId,
        "tradingName"                   -> station.tradingName,
        "isSameTradingAndBrandName"     -> station.isSameTradingAndBrandName,
        "brandName"                     -> station.brandName,
        "temporaryClosure"              -> station.temporaryClosure,
        "permanentClosure"              -> station.permanentClosure,
        "isMotorwayServiceStation"      -> station.isMotorwayServiceStation,
        "isSupermarketServiceStation"   -> station.isSupermarketServiceStation,
        "fuelTypes"                    ->  station.fuelTypes.mkString(","),
        "addressLine1"                  -> station.location.addressLine1,
        "addressLine2"                  -> station.location.addressLine2,
        "city"                          -> station.location.city,
        "country"                       -> station.location.country,
        "county"                        -> station.location.county,
        "postcode"                      -> station.location.postcode,
        "latitude"                      -> station.location.latitude,
        "longitude"                     -> station.location.longitude
      )
    }

    db.withConnection { implicit conn =>
      BatchSql(sqlStatement, parameters.head, parameters.tail *).execute()
    }.sum
  }(using databaseExecutionContext)

  def insertFuelPrices(fuelStations: Seq[FuelPriceForStation]): Future[Int] = Future {
    val sqlStatement =
      """REPLACE INTO `fuel_prices`
        | (`nodeId`, `price`, `fuelType`, `priceLastUpdated`, `priceChangeEffectiveTimestamp`)
        | VALUES ({nodeId}, {price}, {fuelType}, {priceLastUpdated}, {priceChangeEffectiveTimestamp})
          """.stripMargin

    val parameters = fuelStations.flatMap { station =>
      station.fuelPrices.map { fuel =>
        Seq[NamedParameter](
          "nodeId" -> station.nodeId,
          "price" -> fuel.price,
          "fuelType" -> s"${fuel.fuelType}",
          "priceLastUpdated" -> fuel.priceLastUpdated,
          "priceChangeEffectiveTimestamp" -> fuel.priceChangeEffectiveTimestamp
        )
      }
    }

    db.withConnection { implicit conn =>
      BatchSql(sqlStatement, parameters.head, parameters.tail *).execute()
    }.sum
  }(using databaseExecutionContext)

}
