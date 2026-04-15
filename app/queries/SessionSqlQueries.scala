package queries

import anorm.*
import anorm.SqlParser.*
import models.*
import play.api.db.Database
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
final class SessionSqlQueries @Inject()(db: Database, databaseExecutionContext: DatabaseExecutionContext) {

  def getSessionData(sessionId: String): Future[Option[Session]] = Future {
    db.withConnection { implicit conn =>
      SQL("""SELECT *
            |FROM fuel_sessions
            |WHERE sessionId = {id}
            |AND timeStamp > CURRENT_TIMESTAMP - INTERVAL '15' MINUTE""".stripMargin)
        .on("id" -> sessionId)
        .as(Session.mysqlParser.singleOpt)
    }
  }(using databaseExecutionContext)

  def putSessionData(session: Session): Future[Option[String]] = Future {
    db.withConnection { implicit conn =>
      SQL("""INSERT INTO fuel_sessions (sessionId, sessionData)
            |VALUES ({id}, {data})
            |ON DUPLICATE KEY UPDATE
            |sessionData = VALUES(sessionData),
            |timeStamp = CURRENT_TIMESTAMP(6);
            |""".stripMargin)
        .on(
          "id"        -> session.sessionId,
          "data"      -> Json.toJson(session.sessionData).toString
        )
        .executeInsert(str(1).singleOpt)
    }
  }(using databaseExecutionContext)

  def updateSessionData(session: Session): Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL("""UPDATE fuel_sessions
            |SET sessionData = {data}
            |WHERE sessionId = {id}
            |""".stripMargin)
        .on("id" -> session.sessionId, "data" -> Json.toJson(session.sessionData).toString)
        .executeUpdate()
    }
  }(using databaseExecutionContext)

  def removeSessionData(session: Session): Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL("""DELETE FROM fuel_sessions
            |WHERE sessionId = {id}
            |""".stripMargin)
        .on("id" -> session.sessionId)
        .executeUpdate()
    }
  }(using databaseExecutionContext)

  def sessionKeepAlive(sessionId: String): Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL("""UPDATE fuel_sessions
            |SET timeStamp = CURRENT_TIMESTAMP
            |WHERE sessionId = {id}""".stripMargin)
        .on("id" -> sessionId)
        .executeUpdate()
    }
  }(using databaseExecutionContext)
}
