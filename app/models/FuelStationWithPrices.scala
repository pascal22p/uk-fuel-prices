package models

import net.sf.geographiclib.Geodesic

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
                              fuelPrices: Seq[FuelPrice]
                            ) {
  def distanceFromCentre(centre: (Double, Double)): Double = {
    Geodesic.WGS84.Inverse(centre._1, centre._2, location.latitude, location.longitude).s12
  }
}

object FuelStationWithPrices {
  def apply(station: FuelStation, prices: Seq[FuelPrice]): FuelStationWithPrices = {
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
      prices
    )
  }
}

