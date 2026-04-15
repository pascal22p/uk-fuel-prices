package services

import cats.data.OptionT
import com.password4j.Password
import models.UserData
import queries.GetSqlQueries

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LoginService @Inject()(mariadbQueries: GetSqlQueries)(
    implicit ec: ExecutionContext
) {
  def getUserData(username: String, password: String): OptionT[Future, UserData] = {
    mariadbQueries.getUserData(username).transform {
      case Some(result) =>
        val verified =
          Password.check(password, result.hashedPassword).withBcrypt()
        if (verified) Some(result) else None
      case _ => None
    }
  }
}
