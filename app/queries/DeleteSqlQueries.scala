package queries

import anorm.*
import models.*
import play.api.db.Database

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
final class DeleteSqlQueries @Inject()(db: Database, databaseExecutionContext: DatabaseExecutionContext)
    extends LoggingWithRequest {

  def deleteStations(since: LocalDateTime): Future[Int] = Future {
    val sqlStatement = "DELETE FROM `fuel_stations` WHERE lastUpdated < {since}"

    db.withConnection { implicit conn =>
      SQL(sqlStatement)
        .on("since" -> since)
        .executeUpdate()
    }
  }(using databaseExecutionContext)

  def deleteFuelPrices(since: LocalDateTime): Future[Int] = Future {
    val sqlStatement = "DELETE FROM `fuel_prices` WHERE lastUpdated < {since}"

    db.withConnection { implicit conn =>
      SQL(sqlStatement)
        .on("since" -> since)
        .executeUpdate()
    }
  }(using databaseExecutionContext)

}
