package models

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import cats.syntax.traverse.toTraverseOps
import play.api.libs.json.{JsArray, JsError, JsPath, Reads}
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

  type ValidationResult[A] = ValidatedNel[String, A]

  implicit val seqReads: Reads[ValidationResult[Seq[FuelPriceForStation]]] = Reads { json =>
    json.validate[JsArray].map { array =>
      array.value.toList
        .map { item =>
          item.validate[FuelPriceForStation].fold(
            errors => s"${JsError(errors)} in $item".invalidNel[FuelPriceForStation],
            _.validNel[String]
          )
        }
        .sequence
    }
  }
}