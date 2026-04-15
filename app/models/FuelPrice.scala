package models

import anorm.RowParser
import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax.*
import anorm.*
import anorm.SqlParser.*

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.Locale

final case class FuelPrice(
                          price: Double,
                          fuelType: FuelType,
                          priceLastUpdated: Instant,
                          priceChangeEffectiveTimestamp: Instant
                          ) {
  private val formatter = DateTimeFormatter
    .ofPattern("d MMMM yyyy", Locale.ENGLISH)
    .withZone(ZoneId.of("Europe/London"))
  
  def formattedPriceChangeEffectiveTimestamp: String = formatter.format(priceChangeEffectiveTimestamp)

  def formattedPriceLastUpdated: String = formatter.format(priceLastUpdated)
}

object FuelPrice {
  @SuppressWarnings(Array("org.wartremover.warts.EnumValueOf"))
  implicit val fuelPriceReads: Reads[FuelPrice] = (
    (JsPath \ "price").read[Double] and
      (JsPath \ "fuel_type").read[String].map(s => FuelType.valueOf(s)) and
      (JsPath \ "price_last_updated").read[Instant] and
      (JsPath \ "price_change_effective_timestamp").read[Instant]
    )(FuelPrice.apply)

  @SuppressWarnings(Array("org.wartremover.warts.EnumValueOf"))
  val fuelPriceParser: RowParser[FuelPrice] = (
      get[Double]("price") ~
      get[String]("fuelType") ~
      get[Instant]("priceLastUpdated") ~
      get[Instant]("priceChangeEffectiveTimestamp")
    ).map {
    case price ~ fuelType ~ priceLastUpdated ~ priceChangeEffectiveTimestamp =>
      FuelPrice(price, FuelType.valueOf(fuelType), priceLastUpdated, priceChangeEffectiveTimestamp)
  }
}