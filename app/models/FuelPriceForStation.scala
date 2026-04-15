package models

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax.*

final case class FuelPriceForStation(
                                    nodeId: String,
                                    publicPhoneNumber: Option[String],
                                    tradingName: String,
                                    fuelPrices: Seq[FuelPrice]
                                    )

object FuelPriceForStation {
  implicit val fuelPriceForStationReads: Reads[FuelPriceForStation] = (
    (JsPath \ "node_id").read[String] and
      (JsPath \ "public_phone_number").readNullable[String] and
      (JsPath \ "trading_name").read[String] and
      (JsPath \ "fuel_prices").read[Seq[FuelPrice]]
    )(FuelPriceForStation.apply)
}