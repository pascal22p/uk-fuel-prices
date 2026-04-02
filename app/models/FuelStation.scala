package models

import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxValidatedId, toTraverseOps}
import play.api.libs.json.{JsArray, JsError, JsPath, Reads}
import play.api.libs.functional.syntax.*

final case class FuelStation(
                    nodeId: String,
                    tradingName: String,
                    isSameTradingAndBrandName: Option[Boolean],
                    brandName: String,
                    temporaryClosure: Option[Boolean],
                    permanentClosure: Option[Boolean],
                    isMotorwayServiceStation: Option[Boolean],
                    isSupermarketServiceStation: Option[Boolean],
                    location: FuelStationLocation,
                    fuelTypes: List[String]
                  )

object FuelStation {
  implicit val fuelStationReads: Reads[FuelStation] = (
    (JsPath \ "node_id").read[String] and
      (JsPath \ "trading_name").read[String] and
      (JsPath \ "is_same_trading_and_brand_name").readNullable[Boolean] and
      (JsPath \ "brand_name").read[String] and
      (JsPath \ "temporary_closure").readNullable[Boolean] and
      (JsPath \ "permanent_closure").readNullable[Boolean] and
      (JsPath \ "is_motorway_service_station").readNullable[Boolean] and
      (JsPath \ "is_supermarket_service_station").readNullable[Boolean] and
      (JsPath \ "location").read[FuelStationLocation] and
      (JsPath \ "fuel_types").read[List[String]]
    )(FuelStation.apply)

  type ValidationResult[A] = ValidatedNel[String, A]

  implicit val seqReads: Reads[ValidationResult[Seq[FuelStation]]] = Reads { json =>
    json.validate[JsArray].map { array =>
      array.value.toList
        .map { item =>
          item.validate[FuelStation].fold(
            errors => s"${JsError(errors)} in $item".invalidNel[FuelStation],
            _.validNel[String]
          )
        }
        .sequence
    }
  }
}