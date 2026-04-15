package models

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax.*
import anorm._
import anorm.SqlParser._


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

  val fuelStationParser: RowParser[FuelStation] = (
    get[String]("nodeId") ~
      get[String]("tradingName") ~
      get[Option[Boolean]]("isSameTradingAndBrandName") ~
      get[String]("brandName") ~
      get[Option[Boolean]]("temporaryClosure") ~
      get[Option[Boolean]]("permanentClosure") ~
      get[Option[Boolean]]("isMotorwayServiceStation") ~
      get[Option[Boolean]]("isSupermarketServiceStation") ~
      get[Option[String]]("addressLine1") ~
      get[Option[String]]("addressLine2") ~
      get[String]("city") ~
      get[Option[String]]("country") ~
      get[Option[String]]("county") ~
      get[String]("postcode") ~
      get[Double]("latitude") ~
      get[Double]("longitude") ~
      get[String]("fuelTypes")
    ).map {
    case nodeId ~ tradingName ~ isSameTradingAndBrandName ~ brandName ~
      temporaryClosure ~ permanentClosure ~ isMotorwayServiceStation ~
      isSupermarketServiceStation ~ addressLine1 ~ addressLine2 ~ city ~
      country ~ county ~ postcode ~ latitude ~ longitude ~ fuelTypes =>
      FuelStation(
        nodeId = nodeId,
        tradingName = tradingName,
        isSameTradingAndBrandName = isSameTradingAndBrandName,
        brandName = brandName,
        temporaryClosure = temporaryClosure,
        permanentClosure = permanentClosure,
        isMotorwayServiceStation = isMotorwayServiceStation,
        isSupermarketServiceStation = isSupermarketServiceStation,
        location = FuelStationLocation(
          addressLine1 = addressLine1,
          addressLine2 = addressLine2,
          city = city,
          country = country,
          county = county,
          postcode = postcode,
          latitude = latitude,
          longitude = longitude
        ),
        fuelTypes = fuelTypes.split(",").toList.filter(_.nonEmpty)
      )
  }
}