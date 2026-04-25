package queries

import anorm.*
import cats.data.OptionT
import models.*
import play.api.db.Database

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
final class GetSqlQueries @Inject()(db: Database, databaseExecutionContext: DatabaseExecutionContext)
    extends LoggingWithRequest {

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

  def findFuelStations(postcode: String): Future[Seq[FuelStation]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT *, HEX(nodeId_bin) as nodeId
          |FROM fuel_stations
          |WHERE postcode LIKE {postcode}""".stripMargin)
        .on("postcode" -> s"$postcode%")
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

}
