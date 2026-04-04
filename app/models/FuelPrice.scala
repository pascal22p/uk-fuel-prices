package models

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax.*

import java.time.Instant

final case class FuelPrice(
                          price: Double,
                          fuelType: String,
                          priceLastUpdated: Instant,
                          priceChangeEffectiveTimestamp: Instant
                          )

object FuelPrice {
  implicit val fuelPriceReads: Reads[FuelPrice] = (
    (JsPath \ "price").read[Double] and
      (JsPath \ "fuel_type").read[String] and
      (JsPath \ "price_last_updated").read[Instant] and
      (JsPath \ "price_change_effective_timestamp").read[Instant]
    )(FuelPrice.apply)
}