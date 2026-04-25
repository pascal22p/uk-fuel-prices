package models

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax.*

final case class FuelStationLocation(
                     addressLine1: Option[String],
                     addressLine2: Option[String],
                     city: String,
                     country: Option[String],
                     county: Option[String],
                     postcode: String,
                     latitude: Double,
                     longitude: Double
                   ) {
  def fullAddress: String = {
    List(addressLine1, addressLine2, Some(city), Some(postcode)).flatten.mkString(", ")
  }
}

object FuelStationLocation {
  implicit val fuelStationLocationReads: Reads[FuelStationLocation] = (
    (JsPath \ "address_line_1").readNullable[String] and
      (JsPath \ "address_line_2").readNullable[String] and
      (JsPath \ "city").read[String] and
      (JsPath \ "country").readNullable[String] and
      (JsPath \ "county").readNullable[String] and
      (JsPath \ "postcode").read[String] and
      (JsPath \ "latitude").read[Double] and
      (JsPath \ "longitude").read[Double]
    )(FuelStationLocation.apply)
}