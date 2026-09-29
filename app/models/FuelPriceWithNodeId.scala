package models

import anorm.*
import anorm.SqlParser.*
import play.api.libs.json.{Json, Writes}

import java.time.Instant

final case class FuelPriceWithNodeId(
                          nodeId: String,
                          price: Double,
                          fuelType: String,
                          priceLastUpdated: Instant,
                          priceChangeEffectiveTimestamp: Instant
                          )

object FuelPriceWithNodeId {
  val writesExport: Writes[FuelPriceWithNodeId] = Json.writes[FuelPriceWithNodeId]

  @SuppressWarnings(Array("org.wartremover.warts.EnumValueOf"))
  val fuelPriceParser: RowParser[FuelPriceWithNodeId] = (
    get[String]("nodeId") ~
    get[Double]("price") ~
      get[String]("fuelType") ~
      get[Instant]("priceLastUpdated") ~
      get[Instant]("priceChangeEffectiveTimestamp")
    ).map {
    case nodeId ~ price ~ fuelType ~ priceLastUpdated ~ priceChangeEffectiveTimestamp =>
      FuelPriceWithNodeId(nodeId, price, fuelType, priceLastUpdated, priceChangeEffectiveTimestamp)
  }
}