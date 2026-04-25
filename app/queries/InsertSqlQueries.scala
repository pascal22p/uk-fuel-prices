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
    val sqlStatement =
      """INSERT INTO `fuel_stations`
        | (`nodeId_bin`, `tradingName`, `isSameTradingAndBrandName`, `brandName`, `temporaryClosure`, `permanentClosure`, `isMotorwayServiceStation`, `isSupermarketServiceStation`, `fuelTypes`, `addressLine1`, `addressLine2`, `city`, `country`, `county`, `postcode`, `latitude`, `longitude`)
        | VALUES (UNHEX({nodeId}), {tradingName}, {isSameTradingAndBrandName}, {brandName}, {temporaryClosure}, {permanentClosure}, {isMotorwayServiceStation}, {isSupermarketServiceStation}, {fuelTypes}, {addressLine1}, {addressLine2}, {city}, {country}, {county}, {postcode}, {latitude}, {longitude})
        | ON DUPLICATE KEY UPDATE
        |   `tradingName` = VALUES(`tradingName`),
        |   `isSameTradingAndBrandName` = VALUES(`isSameTradingAndBrandName`),
        |   `brandName` = VALUES(`brandName`),
        |   `temporaryClosure` = VALUES(`temporaryClosure`),
        |   `permanentClosure` = VALUES(`permanentClosure`),
        |   `isMotorwayServiceStation` = VALUES(`isMotorwayServiceStation`),
        |   `isSupermarketServiceStation` = VALUES(`isSupermarketServiceStation`),
        |   `fuelTypes` = VALUES(`fuelTypes`),
        |   `addressLine1` = VALUES(`addressLine1`),
        |   `addressLine2` = VALUES(`addressLine2`),
        |   `city` = VALUES(`city`),
        |   `country` = VALUES(`country`),
        |   `county` = VALUES(`county`),
        |   `postcode` = VALUES(`postcode`),
        |   `latitude` = VALUES(`latitude`),
        |   `longitude` = VALUES(`longitude`)
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
    val fuelTypeMap: Map[String, Int] = db.withConnection { implicit conn =>
      SQL(
        """SELECT id, name
          |FROM fuel_types""".stripMargin
      ).as {
        (SqlParser.get[Int]("id") ~ SqlParser.get[String]("name")).map {
          case id ~ name => name -> id
        }.*
      }
    }.toMap

    val sqlStatement =
      """INSERT INTO `fuel_prices`
        | (`nodeId_bin`, `price`, `fuelTypeId`, `priceLastUpdated`, `priceChangeEffectiveTimestamp`)
        | VALUES (UNHEX({nodeId}), {price}, {fuelTypeId}, {priceLastUpdated}, {priceChangeEffectiveTimestamp})
        | ON DUPLICATE KEY UPDATE
        |   `price` = VALUES(`price`)
        """.stripMargin

    val parameters = fuelStations.flatMap { station =>
      station.fuelPrices.map { fuel =>
        Seq[NamedParameter](
          "nodeId" -> station.nodeId,
          "price" -> fuel.price,
          "fuelTypeId" -> fuelTypeMap(s"${fuel.fuelType}"),
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
