package models

final case class SearchByPostcodeViewModel(fuelStationWithPrices: Seq[FuelStationWithPrices], centrePostcode: String, centreLocation: (Double, Double))
