package models

import anorm.RowParser
import net.sf.geographiclib.Geodesic
import anorm.*
import anorm.SqlParser.*

import java.time.Instant

final case class FuelStationWithPrices(
                              nodeId: String,
                              tradingName: String,
                              isSameTradingAndBrandName: Option[Boolean],
                              brandName: String,
                              temporaryClosure: Option[Boolean],
                              permanentClosure: Option[Boolean],
                              isMotorwayServiceStation: Option[Boolean],
                              isSupermarketServiceStation: Option[Boolean],
                              location: FuelStationLocation,
                              fuelTypes: List[String],
                              fuelPrices: Seq[FuelPrice],
                              distance: Double
                            ) {
  def distanceFromCentre(centre: GeoLoc): Option[Double] = {
    location.location.map { loc =>
      Geodesic.WGS84.Inverse(centre.latitude, centre.longitude, loc.latitude, loc.longitude).s12
    }
  }
}

object FuelStationWithPrices {
  def apply(station: FuelStation, prices: Seq[FuelPrice], distance: Double): FuelStationWithPrices = {
    new FuelStationWithPrices(
      station.nodeId,
      station.tradingName,
      station.isSameTradingAndBrandName,
      station.brandName,
      station.temporaryClosure,
      station.permanentClosure,
      station.isMotorwayServiceStation,
      station.isSupermarketServiceStation,
      station.location,
      station.fuelTypes,
      prices,
      distance
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.EnumValueOf"))
  val fuelPriceWithStationInfoParser: RowParser[FuelStationWithPrices] = (
    get[String]("nodeId") ~
      get[String]("tradingName") ~
      get[Option[Boolean]]("temporaryClosure") ~
      get[Option[Boolean]]("permanentClosure") ~
      get[Option[Boolean]]("isMotorwayServiceStation") ~
      get[Option[Boolean]]("isSupermarketServiceStation") ~
      get[Option[String]]("addressLine1") ~
      get[Option[String]]("addressLine2") ~
      get[Option[String]]("city") ~
      get[Option[String]]("postcode") ~
      get[Option[Double]]("latitude") ~
      get[Option[Double]]("longitude") ~
      get[Double]("price") ~
      get[String]("fuelType") ~
      get[Instant]("priceLastUpdated") ~
      get[Instant]("priceChangeEffectiveTimestamp")
    ).map {
    case nodeId ~ tradingName ~ temporaryClosure ~ permanentClosure ~ isMotorwayServiceStation ~ isSupermarketServiceStation ~ addressLine1 ~ addressLine2 ~ city ~ postcode ~ latitude ~ longitude ~ price ~ fuelType ~ priceLastUpdated ~ priceChangeEffectiveTimestamp =>
      val loc = (latitude, longitude) match {
        case (Some(lat), Some(lon)) => Some(GeoLoc(lat, lon))
        case _ => None
      }
      FuelStationWithPrices(
        nodeId, tradingName, None, "", temporaryClosure, permanentClosure, isMotorwayServiceStation, isSupermarketServiceStation,
        FuelStationLocation(addressLine1, addressLine2, city.getOrElse(""), None, None, postcode.getOrElse(""), loc),
        List.empty,
        Seq(FuelPrice(price, FuelType.valueOf(fuelType), priceLastUpdated, priceChangeEffectiveTimestamp)),
        0.0
      )
  }
}

