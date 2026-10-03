package queries

import anorm.*
import anorm.SqlParser.scalar
import cats.data.OptionT
import models.*
import play.api.db.Database
import utils.GeoBoundingBox

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

  def getLatestFuelPricesWithStation(
                                      numberOfResult: Int,
                                      stationsFilter: Seq[String] = Seq.empty
                                    ): Future[Seq[FuelStationWithPrices]] = Future {
    val stationParams: Seq[NamedParameter] =
      stationsFilter.zipWithIndex.map { case (h, i) =>
        NamedParameter(s"station$i", h)
      }

    val inClause =
      if (stationsFilter.nonEmpty) {
        val placeholders =
          stationsFilter.indices.map(i => s"UNHEX({station$i})").mkString(", ")

        s"AND fs.nodeId_bin IN ($placeholders)"
      } else {
        ""
      }

    val allParams: Seq[NamedParameter] =
      NamedParameter("limit", numberOfResult) +: stationParams

    val rows = db.withConnection { implicit conn =>

      /**
       * Finds the {numberOfResult} most recently updated fuel stations, ranked by the most recent
       * priceLastUpdated timestamp among their current E10 or B7 price.
       *
       * A station is only eligible to be ranked if it meets ALL of the following:
       *   - it has a current E10 or B7 price
       *   - that price was last updated within the past 6 months
       *   - it is not permanently or temporarily closed (NULL treated as "not closed")
       *   - its nodeId is included in the given nodeId filter list {stationsFilter}
       *
       * Once the {numberOfResult} most recently updated qualifying stations are selected (by the latest
       * priceLastUpdated among their E10/B7 prices), the result set returns ALL of that station's
       * current fuel prices (every fuel type it sells), not just the E10/B7 price used for ranking.
       *
       * A station is ranked by whichever of its E10/B7 prices was updated more recently —
       * the other one may still be comparatively stale (though within the 6-month cutoff).
       *
       * No cutoff/closure/date filtering is applied to fuel types other than E10/B7 shown in
       * the final result — only to which stations qualify in the first place.
       *
       * Result rows are unordered.
       */
      SQL(
        s"""WITH cheapest_stations AS (
           |    SELECT
           |        fpl.nodeId_bin,
           |        MIN(fpl.price) AS price
           |    FROM fuel_prices_latest AS fpl
           |    JOIN fuel_types AS ft
           |        ON ft.id = fpl.fuelTypeId
           |    JOIN fuel_stations AS fs
           |        ON fs.nodeId_bin = fpl.nodeId_bin
           |    WHERE fpl.priceChangeEffectiveTimestamp >= CURRENT_TIMESTAMP - INTERVAL 6 MONTH
           |      AND ft.name IN ('${FuelType.E10}', '${FuelType.B7_STANDARD}')
           |      AND COALESCE(fs.permanentClosure, 0) = 0
           |      AND COALESCE(fs.temporaryClosure, 0) = 0
           |      $inClause
           |    GROUP BY fpl.nodeId_bin
           |    ORDER BY priceLastUpdated DESC
           |    LIMIT {limit}
           |)
           |SELECT
           |    HEX(fs.nodeId_bin) AS nodeId,
           |    fs.tradingName,
           |    fs.temporaryClosure,
           |    fs.permanentClosure,
           |    fs.isMotorwayServiceStation,
           |    fs.isSupermarketServiceStation,
           |    fs.addressLine1,
           |    fs.addressLine2,
           |    fs.city,
           |    fs.postcode,
           |    fs.latitude,
           |    fs.longitude,
           |    fpl.price,
           |    ft.name AS fuelType,
           |    fpl.priceLastUpdated,
           |    fpl.priceChangeEffectiveTimestamp
           |FROM cheapest_stations AS cs
           |JOIN fuel_stations AS fs
           |    ON fs.nodeId_bin = cs.nodeId_bin
           |JOIN fuel_prices_latest AS fpl
           |    ON fpl.nodeId_bin = cs.nodeId_bin
           |JOIN fuel_types AS ft
           |    ON ft.id = fpl.fuelTypeId
           |ORDER BY fpl.price ASC""".stripMargin
      )
        .on(allParams *)
        .as(FuelStationWithPrices.fuelPriceWithStationInfoParser.*)
    }

    rows
      .groupBy(_.nodeId)
      .flatMap { case (_, stationRows) =>
        val prices = stationRows.flatMap(_.fuelPrices)
        stationRows.headOption.map(_.copy(fuelPrices = prices))
      }
      .toSeq
      .sortBy(_.fuelPrices.map(_.priceLastUpdated).max)(using Ordering[Instant].reverse)
  }(using databaseExecutionContext)

  def getCheapestFuelPricesWithStation(
                                        numberOfResult: Int,
                                        stationsFilter: Seq[String] = Seq.empty
                                      ): Future[Seq[FuelStationWithPrices]] = Future {
    val stationParams: Seq[NamedParameter] =
      stationsFilter.zipWithIndex.map { case (nodeId, i) =>
        NamedParameter(s"station$i", nodeId)
      }

    val inClause =
      if (stationsFilter.nonEmpty) {
        val placeholders =
          stationsFilter.indices.map(i => s"UNHEX({station$i})").mkString(", ")

        s" AND fpl.nodeId_bin IN ($placeholders)"
      } else {
        ""
      }

    val allParams =
      NamedParameter("limit", numberOfResult) +: stationParams

    val rows = db.withConnection { implicit conn =>
      /**
       * Finds the {numberOfResult} cheapest fuel stations, ranked by their lowest current E10 or B7 price.
       *
       * A station is only eligible to be ranked if it meets ALL of the following:
       *   - it has a current E10 or B7 price
       *   - that price was last updated within the past 6 months
       *   - it is not permanently or temporarily closed (NULL treated as "not closed")
       *   - its nodeId is included in the given nodeId filter list {stationsFilter}
       *
       * Once the {numberOfResult} cheapest qualifying stations are selected (by their lowest
       * E10/B7 price), the result set returns ALL of that station's current fuel prices
       * (every fuel type it sells), not just the E10/B7 price used for ranking.
       *
       * No cutoff/closure/date filtering is applied to fuel types other than E10/B7 shown in
       * the final result — only to which stations qualify in the first place.
       *
       * Result rows are unordered.
       */
      SQL(
        s"""WITH cheapest_stations AS (
           |    SELECT
           |        fpl.nodeId_bin,
           |        MIN(fpl.price) AS price
           |    FROM fuel_prices_latest AS fpl
           |    JOIN fuel_types AS ft
           |        ON ft.id = fpl.fuelTypeId
           |    JOIN fuel_stations AS fs
           |        ON fs.nodeId_bin = fpl.nodeId_bin
           |    WHERE fpl.priceChangeEffectiveTimestamp >= CURRENT_TIMESTAMP - INTERVAL 6 MONTH
           |      AND ft.name IN ('${FuelType.E10}', '${FuelType.B7_STANDARD}')
           |      AND COALESCE(fs.permanentClosure, 0) = 0
           |      AND COALESCE(fs.temporaryClosure, 0) = 0
           |      $inClause
           |    GROUP BY fpl.nodeId_bin
           |    ORDER BY price ASC
           |    LIMIT {limit}
           |)
           |SELECT
           |    HEX(fs.nodeId_bin) AS nodeId,
           |    fs.tradingName,
           |    fs.temporaryClosure,
           |    fs.permanentClosure,
           |    fs.isMotorwayServiceStation,
           |    fs.isSupermarketServiceStation,
           |    fs.addressLine1,
           |    fs.addressLine2,
           |    fs.city,
           |    fs.postcode,
           |    fs.latitude,
           |    fs.longitude,
           |    fpl.price,
           |    ft.name AS fuelType,
           |    fpl.priceLastUpdated,
           |    fpl.priceChangeEffectiveTimestamp
           |FROM cheapest_stations AS cs
           |JOIN fuel_stations AS fs
           |    ON fs.nodeId_bin = cs.nodeId_bin
           |JOIN fuel_prices_latest AS fpl
           |    ON fpl.nodeId_bin = cs.nodeId_bin
           |JOIN fuel_types AS ft
           |    ON ft.id = fpl.fuelTypeId
           |ORDER BY fpl.price ASC""".stripMargin
      )
        .on(allParams *)
        .as(FuelStationWithPrices.fuelPriceWithStationInfoParser.*)
    }

    rows
      .groupBy(_.nodeId)
      .flatMap { case (_, stationRows) =>
        val prices = stationRows.flatMap(_.fuelPrices)
        stationRows.headOption.map(_.copy(fuelPrices = prices))
      }
      .toSeq
      .sortBy(_.fuelPrices.map(_.price).min)(using Ordering[Double])
  }(using databaseExecutionContext)

  def getAverageFuelPrices: Future[Seq[AverageFuelPrice]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT
          |    ft.name AS fuelType,
          |    AVG(cp.price) AS averagePrice,
          |    COUNT(*) AS stationCount
          |FROM fuel_prices_latest cp
          |JOIN fuel_types ft
          |  ON ft.id = cp.fuelTypeId
          |JOIN fuel_stations fs
          |  ON fs.nodeId_bin = cp.nodeId_bin
          |WHERE cp.priceLastUpdated >= UTC_TIMESTAMP() - INTERVAL 6 MONTH
          |  AND COALESCE(fs.permanentClosure, 0) = 0
          |  AND COALESCE(fs.temporaryClosure, 0) = 0
          |GROUP BY ft.id, ft.name
          |""".stripMargin
      ).as(AverageFuelPrice.averageFuelPriceParser.*)
    }.sortBy(_.fuelType.ordinal)
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

  def getFuelStations(geoBoundingBox: GeoBoundingBox): Future[Seq[FuelStation]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT *, HEX(nodeId_bin) as nodeId
          |FROM fuel_stations
          |WHERE latitude > {latitude_min} AND latitude < {latitude_max} AND
          |  longitude > {longitude_min} AND longitude < {longitude_max}""".stripMargin)
        .on(
          "latitude_min" -> geoBoundingBox.minLat, 
          "latitude_max" -> geoBoundingBox.maxLat, 
          "longitude_min" -> geoBoundingBox.minLon, 
          "longitude_max" -> geoBoundingBox.maxLon
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

  def findHistoricalPricesForStation(nodeId: String): Future[Option[FuelStationWithPrices]] = Future {
    val rows = db.withConnection { implicit conn =>
      SQL(
        """SELECT fp.*, ft.name AS fuelType, fs.tradingName AS tradingName, HEX(fp.nodeId_bin) as nodeId,
          | fs.temporaryClosure as temporaryClosure, fs.permanentClosure as permanentClosure, fs.isMotorwayServiceStation as isMotorwayServiceStation, fs.isSupermarketServiceStation as isSupermarketServiceStation,
          | fs.addressLine1 as addressLine1, fs.addressLine2 as addressLine2, fs.city as city, fs.postcode as postcode,
          | fs.latitude as latitude, fs.longitude as longitude
          |FROM fuel_prices fp
          |LEFT JOIN fuel_types ft ON fp.fuelTypeId = ft.id
          |LEFT JOIN fuel_stations fs ON fp.nodeId_bin = fs.nodeId_bin
          |WHERE fp.nodeId_bin = UNHEX({nodeId})""".stripMargin
      )
        .on("nodeId" -> nodeId)
        .as(FuelStationWithPrices.fuelPriceWithStationInfoParser.*)
    }

    val prices = rows.flatMap(_.fuelPrices)
    rows.headOption.map(_.copy(fuelPrices = prices))
  }(using databaseExecutionContext)

  def findLatestPricesForStation(nodeId: String): Future[Option[FuelStationWithPrices]] = Future {
    val rows = db.withConnection { implicit conn =>
      SQL(
        """SELECT fp.*, ft.name AS fuelType, fs.tradingName AS tradingName, HEX(fp.nodeId_bin) as nodeId,
          | fs.temporaryClosure as temporaryClosure, fs.permanentClosure as permanentClosure, fs.isMotorwayServiceStation as isMotorwayServiceStation, fs.isSupermarketServiceStation as isSupermarketServiceStation,
          | fs.addressLine1 as addressLine1, fs.addressLine2 as addressLine2, fs.city as city, fs.postcode as postcode,
          | fs.latitude as latitude, fs.longitude as longitude
          |FROM fuel_prices_latest fp
          |LEFT JOIN fuel_types ft ON fp.fuelTypeId = ft.id
          |LEFT JOIN fuel_stations fs ON fp.nodeId_bin = fs.nodeId_bin
          |WHERE fp.nodeId_bin = UNHEX({nodeId})""".stripMargin
      )
        .on("nodeId" -> nodeId)
        .as(FuelStationWithPrices.fuelPriceWithStationInfoParser.*)
    }

    val prices = rows.flatMap(_.fuelPrices)
    rows.headOption.map(_.copy(fuelPrices = prices))
  }(using databaseExecutionContext)

  def findLatestPricesForStations(nodeIds: Seq[String]): Future[Seq[FuelStationWithPrices]] = Future {
    val binaryIds = nodeIds.map(java.util.HexFormat.of().parseHex)

    val rows = db.withConnection { implicit conn =>
      SQL(
        """SELECT fp.*, fs.tradingName AS tradingName, ft.name AS fuelType, HEX(fp.nodeId_bin) as nodeId,
          | fs.temporaryClosure as temporaryClosure, fs.permanentClosure as permanentClosure, fs.isMotorwayServiceStation as isMotorwayServiceStation, fs.isSupermarketServiceStation as isSupermarketServiceStation,
          | fs.addressLine1 as addressLine1, fs.addressLine2 as addressLine2, fs.city as city, fs.postcode as postcode,
          | fs.latitude as latitude, fs.longitude as longitude
          |FROM fuel_prices_latest fp
          |LEFT JOIN fuel_types ft ON fp.fuelTypeId = ft.id
          |LEFT JOIN fuel_stations fs ON fp.nodeId_bin = fs.nodeId_bin
          |WHERE fp.nodeId_bin IN ({nodeIds})""".stripMargin
      )
        .on("nodeIds" -> binaryIds)
        .as(FuelStationWithPrices.fuelPriceWithStationInfoParser.*)
    }

    rows.groupBy(_.nodeId).flatMap { case (_, stationRows) =>
      val prices = stationRows.flatMap(_.fuelPrices)
      stationRows.headOption.map(_.copy(fuelPrices = prices))
    }.toSeq
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
