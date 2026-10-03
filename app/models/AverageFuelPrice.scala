package models

import anorm.*
import anorm.SqlParser.*

/** The average of the latest price of one fuel type across all open stations that sell it.
  *
  * @param averagePrice average price in pence per litre
  * @param stationCount number of stations that contributed to the average
  */
final case class AverageFuelPrice(
    fuelType: FuelType,
    averagePrice: Double,
    stationCount: Int
)

object AverageFuelPrice {
  @SuppressWarnings(Array("org.wartremover.warts.EnumValueOf"))
  val averageFuelPriceParser: RowParser[AverageFuelPrice] = (
    get[String]("fuelType") ~
      get[Double]("averagePrice") ~
      get[Int]("stationCount")
  ).map { case fuelType ~ averagePrice ~ stationCount =>
    AverageFuelPrice(FuelType.valueOf(fuelType), averagePrice, stationCount)
  }
}
