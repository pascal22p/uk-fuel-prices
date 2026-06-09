package models

import anorm.*
import anorm.SqlParser.*
import play.api.libs.json.{Json, OFormat}

final case class UserData(id: Int, username: String, hashedPassword: String, favouriteNodeId: String, isAdmin: Boolean)

object UserData {
  implicit val format: OFormat[UserData] = Json.format[UserData]

  val mysqlParser: RowParser[UserData] =
    (get[Int]("id") ~
      get[String]("email") ~
      get[String]("password") ~
      get[String]("favourite_node_id") ~
      get[Boolean]("is_admin")).map {
      case id ~ username ~ hashedPassword ~ favouriteNodeId ~ isAdmin =>
        UserData(id, username, hashedPassword, favouriteNodeId, isAdmin)
    }
}
