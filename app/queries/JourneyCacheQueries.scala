package queries

import anorm.*
import models.DatabaseExecutionContext
import play.api.db.Database

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
final class JourneyCacheQueries @Inject()(db: Database, databaseExecutionContext: DatabaseExecutionContext) {

  def getUserAnswers(sessionId: String, key: String = "journey-cache"): Future[Option[(String, String, Instant)]] =
    Future {
      db.withConnection { implicit conn =>
        SQL("""SELECT sessionId, data, lastUpdated
              |FROM fuel_user_answers
              |WHERE sessionId = {sessionId} AND itemKey = {key}""".stripMargin)
          .on("sessionId" -> sessionId, "key" -> key)
          .as(
            (SqlParser.str("sessionId") ~ SqlParser.str("data") ~ SqlParser
              .get[Instant]("lastUpdated")(using anorm.Column.columnToInstant)).singleOpt
          )
          .map { case a ~ b ~ c => (a, b, c) }
      }
    }(using databaseExecutionContext)

  def deleteUserAnswers(sessionId: String, key: String = "journey-cache"): Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL("""DELETE FROM fuel_user_answers
            |WHERE sessionId = {sessionId} AND itemKey = {key}""".stripMargin)
        .on("sessionId" -> sessionId, "key" -> key)
        .executeUpdate()
    }
  }(using databaseExecutionContext)

  def upsertUserAnswers(sessionId: String, data: String, key: String = "journey-cache"): Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL("""REPLACE INTO fuel_user_answers (sessionId, itemKey, data, lastUpdated)
            |VALUES ({sessionId}, {key}, {data}, {lastUpdated})""".stripMargin)
        .on(
          "sessionId"   -> sessionId,
          "data"        -> data,
          "lastUpdated" -> Instant.now,
          "key"         -> key
        )
        .executeUpdate()
    }
  }(using databaseExecutionContext)

  def updateLastUpdated(sessionId: String, key: String = "journey-cache"): Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL("""UPDATE fuel_user_answers
            |SET lastUpdated = {lastUpdated}
            |WHERE sessionId = {sessionId} AND itemKey = {key}""".stripMargin)
        .on(
          "sessionId"   -> sessionId,
          "lastUpdated" -> Instant.now,
          "key"         -> key
        )
        .executeUpdate()
    }
  }(using databaseExecutionContext)

}
