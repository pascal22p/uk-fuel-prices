package models

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax.*

import java.time.Instant

final case class FuelPriceForStation(
                                    nodeId: String,
                                    fuelPrices: Seq[FuelPrice]
                                    )

object FuelPriceForStation {
  def fuelPriceForStationReads(minValidDate: Instant): Reads[FuelPriceForStation] = {
    implicit val fpReads: Reads[FuelPrice] = FuelPrice.fuelPriceReads(minValidDate)
    (
      (JsPath \ "node_id").read[String] and
        (JsPath \ "fuel_prices").read[Seq[FuelPrice]]
      ) { (nodeId, fuelPrices) =>
      FuelPriceForStation(
        nodeId = nodeId,
        fuelPrices = fuelPrices
      )
    }
  }
}