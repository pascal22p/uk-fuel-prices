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

  def getLatestFuelPricesWithStation(numberOfResult: Int, stationsFilter: Seq[String] = Seq.empty): Future[Seq[FuelStationWithPrices]] = Future {
    val stationParams: Seq[NamedParameter] =
      stationsFilter.zipWithIndex.map { case (h, i) => NamedParameter(s"station$i", h) }

    val inClause = if (stationsFilter.nonEmpty) {
      val placeholders = stationsFilter.indices.map(i => s"UNHEX({station$i})").mkString(", ")
      s"AND fs.nodeId_bin IN ($placeholders)"
    } else {
      ""
    }

    val allParams: Seq[NamedParameter] = NamedParameter("limit", numberOfResult) +: stationParams

    val rows = db.withConnection { implicit conn =>
      /**
       * Finds the {numberOfResult} most recently updated fuel stations, ranked by the most recent update
       * timestamp among their current E10 or B7 price.
       *
       * For each candidate station, only the most recently updated price per fuel type is
       * considered (via ROW_NUMBER() partitioned by station + fuel type, ordered by lastUpdated).
       *
       * A station is only eligible to be ranked if it meets ALL of the following:
       *   - it has a current E10 or B7 price
       *   - that price was last updated within the past 6 months
       *   - it is not permanently or temporarily closed (NULL treated as "not closed")
       *   - its nodeId is included in the given nodeId filter list {stationsFilter}
       *
       * Once the {numberOfResult} most recently updated qualifying stations are selected (by the latest
       * priceLastUpdated among their E10/B7 prices), the result set returns ALL of that
       * station's current fuel prices (every fuel type it sells), not just the E10/B7 price
       * used for ranking.
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
        s"""WITH current_prices AS (
           |    SELECT
           |        fp.nodeId_bin,
           |        fp.fuelTypeId,
           |        fp.price,
           |        fp.priceLastUpdated,
           |        fp.priceChangeEffectiveTimestamp,
           |        ROW_NUMBER() OVER (
           |            PARTITION BY fp.nodeId_bin, fp.fuelTypeId
           |            ORDER BY fp.lastUpdated DESC
           |        ) AS rowNumber
           |    FROM fuel_prices fp
           |),
           |latest AS (
           |    SELECT * FROM current_prices WHERE rowNumber = 1
           |),
           |ranking_prices AS (
           |    SELECT l.*
           |    FROM latest l
           |    JOIN fuel_types ft    ON ft.id = l.fuelTypeId
           |    JOIN fuel_stations fs ON fs.nodeId_bin = l.nodeId_bin
           |    WHERE ft.name IN ('E10', 'B7')
           |      AND l.priceLastUpdated >= UTC_TIMESTAMP() - INTERVAL 6 MONTH
           |      AND COALESCE(fs.permanentClosure, 0) = 0
           |      AND COALESCE(fs.temporaryClosure, 0) = 0
           |      $inClause
           |),
           |most_recent_per_station AS (
           |    SELECT
           |        nodeId_bin,
           |        MAX(priceLastUpdated) AS mostRecentUpdate
           |    FROM ranking_prices
           |    GROUP BY nodeId_bin
           |),
           |top_stations AS (
           |    SELECT nodeId_bin
           |    FROM most_recent_per_station
           |    ORDER BY mostRecentUpdate DESC
           |    LIMIT {limit}
           |)
           |SELECT
           |    HEX(fs.nodeId_bin) AS nodeId,
           |    fs.tradingName,
           |    fs.addressLine1,
           |    fs.addressLine2,
           |    fs.city,
           |    fs.postcode,
           |    ft.name AS fuelType,
           |    l.price,
           |    l.priceChangeEffectiveTimestamp,
           |    l.priceLastUpdated
           |FROM top_stations ts
           |JOIN latest l          ON l.nodeId_bin = ts.nodeId_bin
           |JOIN fuel_stations fs  ON fs.nodeId_bin = ts.nodeId_bin
           |JOIN fuel_types ft     ON ft.id = l.fuelTypeId;
           |""".stripMargin
      )
        .on(allParams*)
        .as(FuelStationWithPrices.fuelPriceWithStationInfoParser.*)
    }

    rows.groupBy(_.nodeId).flatMap { case (_, stationRows) =>
      val prices = stationRows.flatMap(_.fuelPrices)
      stationRows.headOption.map(_.copy(fuelPrices = prices))
    }.toSeq.sortBy(_.fuelPrices.map(_.priceLastUpdated).max)(using Ordering[Instant].reverse)
  }(using databaseExecutionContext)

  def getCheapestFuelPricesWithStation(numberOfResult: Int, stationsFilter: Seq[String] = Seq.empty): Future[Seq[FuelStationWithPrices]] = Future {
    val stationParams: Seq[NamedParameter] =
      stationsFilter.zipWithIndex.map { case (h, i) => NamedParameter(s"station$i", h) }

    val inClause = if (stationsFilter.nonEmpty) {
      val placeholders = stationsFilter.indices.map(i => s"UNHEX({station$i})").mkString(", ")
      s" AND l.nodeId_bin IN ($placeholders)"
    } else {
      ""
    }

    val allParams: Seq[NamedParameter] = NamedParameter("limit", numberOfResult) +: stationParams

    val rows = db.withConnection { implicit conn =>
      /**
       * Finds the {numberOfResult} cheapest fuel stations, ranked by their lowest current E10 or B7 price.
       *
       * For each candidate station, only the most recently updated price per fuel type is
       * considered (via ROW_NUMBER() partitioned by station + fuel type, ordered by lastUpdated).
       *
       * A station is only eligible to be ranked if it meets ALL of the following:
       *   - it has a current E10 or B7 price
       *   - that price was last updated within the past 6 months
       *   - it is not permanently or temporarily closed (NULL treated as "not closed")
       *   - its nodeId is included in the given nodeId filter list from {stationsFilter}
       *
       * Once the {numberOfResult} cheapest qualifying stations are selected (by their lowest E10/B7 price),
       * the result set returns ALL of that station's current fuel prices (every fuel type it
       * sells), not just the E10/B7 price used for ranking.
       *
       * No cutoff/closure/date filtering is applied to fuel types other than E10/B7 shown in
       * the final result — only to which stations qualify in the first place.
       *
       * Result rows are unordered.
       */
      SQL(
        s"""WITH current_prices AS (
           |    SELECT
           |        fp.nodeId_bin,
           |        fp.fuelTypeId,
           |        fp.price,
           |        fp.priceLastUpdated,
           |        fp.priceChangeEffectiveTimestamp,
           |        ROW_NUMBER() OVER (
           |            PARTITION BY fp.nodeId_bin, fp.fuelTypeId
           |            ORDER BY fp.lastUpdated DESC
           |        ) AS rowNumber
           |    FROM fuel_prices fp
           |),
           |latest AS (
           |    SELECT * FROM current_prices WHERE rowNumber = 1
           |),
           |ranking_prices AS (
           |    SELECT l.*
           |    FROM latest l
           |    JOIN fuel_types ft    ON ft.id = l.fuelTypeId
           |    JOIN fuel_stations fs ON fs.nodeId_bin = l.nodeId_bin
           |    WHERE ft.name IN ('E10', 'B7')
           |      AND l.priceLastUpdated >= UTC_TIMESTAMP() - INTERVAL 6 MONTH
           |      AND COALESCE(fs.permanentClosure, 0) = 0
           |      AND COALESCE(fs.temporaryClosure, 0) = 0
           |      $inClause
           |),
           |cheapest_per_station AS (
           |    SELECT
           |        nodeId_bin,
           |        MIN(price) AS cheapestPrice
           |    FROM ranking_prices
           |    GROUP BY nodeId_bin
           |),
           |top_stations AS (
           |    SELECT nodeId_bin
           |    FROM cheapest_per_station
           |    ORDER BY cheapestPrice ASC
           |    LIMIT {limit}
           |)
           |SELECT
           |    HEX(fs.nodeId_bin) AS nodeId,
           |    fs.tradingName,
           |    fs.addressLine1,
           |    fs.addressLine2,
           |    fs.city,
           |    fs.postcode,
           |    ft.name AS fuelType,
           |    l.price,
           |    l.priceChangeEffectiveTimestamp,
           |    l.priceLastUpdated
           |FROM top_stations ts
           |JOIN latest l          ON l.nodeId_bin = ts.nodeId_bin
           |JOIN fuel_stations fs  ON fs.nodeId_bin = ts.nodeId_bin
           |JOIN fuel_types ft     ON ft.id = l.fuelTypeId;
           |""".stripMargin
      )
        .on(allParams *)
        .as(FuelStationWithPrices.fuelPriceWithStationInfoParser.*)
    }

    rows.groupBy(_.nodeId).flatMap { case (_, stationRows) =>
      val prices = stationRows.flatMap(_.fuelPrices)
      stationRows.headOption.map(_.copy(fuelPrices = prices))
    }.toSeq.sortBy(_.fuelPrices.map(_.price).min)(using Ordering[Double])
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

  def findPricesForStation(nodeId: String): Future[Seq[FuelStationWithPrices]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT fp.*, ft.name AS fuelType, fs.tradingName AS tradingName, HEX(fp.nodeId_bin) as nodeId,
          | fs.addressLine1 as addressLine1, fs.addressLine2 as addressLine2, fs.city as city, fs.postcode as postcode
          |FROM fuel_prices fp
          |LEFT JOIN fuel_types ft ON fp.fuelTypeId = ft.id
          |LEFT JOIN fuel_stations fs ON fp.nodeId_bin = fs.nodeId_bin
          |WHERE fp.nodeId_bin = UNHEX({nodeId})""".stripMargin
      )
        .on("nodeId" -> nodeId)
        .as(FuelStationWithPrices.fuelPriceWithStationInfoParser.*)
    }
  }(using databaseExecutionContext)

  def findPricesForStations(nodeIds: Seq[String]): Future[Seq[FuelStationWithPrices]] = Future {
    val binaryIds = nodeIds.map(java.util.HexFormat.of().parseHex)

    val rows = db.withConnection { implicit conn =>
      SQL(
        """SELECT fp.*, fs.tradingName AS tradingName, ft.name AS fuelType, HEX(fp.nodeId_bin) as nodeId,
          | fs.addressLine1 as addressLine1, fs.addressLine2 as addressLine2, fs.city as city, fs.postcode as postcode
          |FROM fuel_prices fp
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
