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
        """SELECT *
          |FROM fuel_stations
          |WHERE postcode LIKE {postcode}""".stripMargin)
        .on("postcode" -> s"$postcode%")
        .as(FuelStation.fuelStationParser.*)
    }
  }(using databaseExecutionContext)

  def findPricesForStation(station: FuelStation): Future[Seq[FuelPrice]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT *
          |FROM fuel_prices
          |WHERE nodeId = {nodeId}""".stripMargin)
        .on("nodeId" -> station.nodeId)
        .as(FuelPrice.fuelPriceParser.*)
    }
  }(using databaseExecutionContext)

  def findAbsentFuelStations(nodeIds: Seq[String]): Future[Seq[String]] = Future {
    val result = db.withConnection { implicit conn =>
      SQL(
        """SELECT nodeId
          |FROM fuel_stations
          |WHERE nodeId IN ({nodeIds})""".stripMargin)
        .on("nodeIds" -> anorm.SeqParameter(nodeIds, sep = ","))
        .as(SqlParser.scalar[String].*)
    }
    nodeIds.filterNot(result.contains)
  }(using databaseExecutionContext)

}
