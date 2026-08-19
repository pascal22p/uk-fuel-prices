package queries

import anorm.*
import anorm.SqlParser.scalar
import cats.data.OptionT
import models.*
import play.api.db.Database
import utils.BoundingBox

import java.time.{Instant, LocalDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
final class GetSqlQueries @Inject()(db: Database, databaseExecutionContext: DatabaseExecutionContext)
    extends LoggingWithRequest {

  def getTotalFuelStations: Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT COUNT(*) as total
          |FROM fuel_stations""".stripMargin)
        .as(SqlParser.scalar[Int].single)
    }
  }(using databaseExecutionContext)

  def getTotalFuelPrices: Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT COUNT(*) as total
          |FROM fuel_prices""".stripMargin)
        .as(SqlParser.scalar[Int].single)
    }
  }(using databaseExecutionContext)

  def getLatestFuelPricesWithStation(numberOfResult: Int): Future[Seq[FuelPriceForStation]] = Future {
    val rows = db.withConnection { implicit conn =>
      SQL(
        s"""SELECT
           |    nodeId,
           |    tradingName,
           |    fuelType,
           |    priceChangeEffectiveTimestamp,
           |    priceLastUpdated,
           |    price
           |FROM (
           |    SELECT
           |        HEX(fp.nodeId_bin) AS nodeId,
           |        fs.tradingName AS tradingName,
           |        ft.name AS fuelType,
           |        fp.priceChangeEffectiveTimestamp AS priceChangeEffectiveTimestamp,
           |        fp.priceLastUpdated AS priceLastUpdated,
           |        fp.price AS price,
           |        ROW_NUMBER() OVER (
           |            PARTITION BY fp.nodeId_bin, fp.fuelTypeId
           |            ORDER BY fp.lastUpdated DESC
           |        ) AS rowNumber
           |    FROM fuel_prices fp
           |    LEFT JOIN fuel_types ft
           |        ON fp.fuelTypeId = ft.id
           |    LEFT JOIN fuel_stations fs
           |        ON fp.nodeId_bin = fs.nodeId_bin
           |) latest
           |WHERE rowNumber = 1
           |ORDER BY priceLastUpdated DESC
           |LIMIT {limit}""".stripMargin
      )
        .on("limit" -> numberOfResult)
        .as(FuelPrice.fuelPriceWithStationInfoParser.*)
    }

    rows
      .groupBy { case (nodeId, tradingName, _) =>
        (nodeId, tradingName)
      }
      .map {
        case ((nodeId, tradingName), rowsPerStation) =>
          FuelPriceForStation(
            nodeId = nodeId,
            publicPhoneNumber = None,
            tradingName = tradingName,
            fuelPrices = rowsPerStation.map(_._3)
          )
      }.toSeq
      .sortBy(_.fuelPrices.map(_.priceLastUpdated).max)(using Ordering[Instant].reverse)
  }(using databaseExecutionContext)

  def getUserData(username: String): OptionT[Future, UserData] = OptionT(Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT *
          |FROM fuel_admins
          |WHERE email = {email}""".stripMargin)
        .on("email" -> username)
        .as(UserData.mysqlParser.singleOpt)
    }
  }(using databaseExecutionContext))

  def getFuelStations(postcode: String): Future[Seq[FuelStation]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT *, HEX(nodeId_bin) as nodeId
          |FROM fuel_stations
          |WHERE postcode LIKE {postcode}""".stripMargin)
        .on("postcode" -> s"$postcode%")
        .as(FuelStation.fuelStationParser.*)
    }
  }(using databaseExecutionContext)

  def getFuelStations(boundingBox: BoundingBox): Future[Seq[FuelStation]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT *, HEX(nodeId_bin) as nodeId
          |FROM fuel_stations
          |WHERE latitude > {latitude_min} AND latitude < {latitude_max} AND
          |  longitude > {longitude_min} AND longitude < {longitude_max}""".stripMargin)
        .on(
          "latitude_min" -> boundingBox.minLat, 
          "latitude_max" -> boundingBox.maxLat, 
          "longitude_min" -> boundingBox.minLon, 
          "longitude_max" -> boundingBox.maxLon
        )
        .as(FuelStation.fuelStationParser.*)
    }
  }(using databaseExecutionContext)
  
  def getFuelStation(nodeId: String): Future[Option[FuelStation]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT *, HEX(nodeId_bin) as nodeId
          |FROM fuel_stations
          |WHERE nodeId_bin = UNHEX({nodeId})""".stripMargin)
        .on("nodeId" -> nodeId)
        .as(FuelStation.fuelStationParser.singleOpt)
    }
  }(using databaseExecutionContext)

  def findPricesForStation(nodeId: String): Future[Seq[FuelPrice]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT fp.*, ft.name AS fuelType
          |FROM fuel_prices fp
          |LEFT JOIN fuel_types ft ON fp.fuelTypeId = ft.id
          |WHERE fp.nodeId_bin = UNHEX({nodeId})""".stripMargin
      )
        .on("nodeId" -> nodeId)
        .as(FuelPrice.fuelPriceParser.*)
    }
  }(using databaseExecutionContext)

  def findPricesForStations(nodeIds: Seq[String]): Future[Map[String, Seq[FuelPrice]]] = Future {
    val binaryIds = nodeIds.map(java.util.HexFormat.of().parseHex)

    val results = db.withConnection { implicit conn =>
      SQL(
        """SELECT fp.*, fs.tradingName AS tradingNme, ft.name AS fuelType, HEX(fp.nodeId_bin) as nodeId
          |FROM fuel_prices fp
          |LEFT JOIN fuel_types ft ON fp.fuelTypeId = ft.id
          |LEFT JOIN fuel_stations fs ON fp.nodeId_bin = fs.nodeId_bin
          |WHERE fp.nodeId_bin IN ({nodeIds})""".stripMargin
      )
        .on("nodeIds" -> binaryIds)
        .as(FuelPrice.fuelPriceWithStationInfoParser.*)
    }
    
    results.groupMap(_._1)(_._3)
  }(using databaseExecutionContext)

  def findAbsentFuelStations(nodeIds: Seq[String]): Future[Seq[String]] = Future {
    val binaryIds = nodeIds.map(java.util.HexFormat.of().parseHex)

    val result = db.withConnection { implicit conn =>
      SQL(
        """SELECT HEX(nodeId_bin) as nodeId
          |FROM fuel_stations
          |WHERE nodeId_bin IN ({nodeIds})""".stripMargin)
        .on("nodeIds" -> binaryIds)
        .as(SqlParser.scalar[String].*)
    }
    nodeIds.filterNot(result.contains)
  }(using databaseExecutionContext)

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def getLastUpdateForLock: Future[Option[LocalDateTime]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT lastUpdate
          |FROM fuel_locks
          |WHERE id = {lockId}
          |FOR UPDATE NOWAIT""".stripMargin)
        .on("lockId" -> LockId.stationsAndPricesLock.toString)
        .as(scalar[LocalDateTime].singleOpt)
    }
  }(using databaseExecutionContext)

}
