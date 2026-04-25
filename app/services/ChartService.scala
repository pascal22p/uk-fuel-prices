package services

import javax.inject.*
import play.api.libs.json.{Json, OFormat}
import queries.GetSqlQueries

import scala.concurrent.{ExecutionContext, Future}

final case class ChartPoint(x: Long, y: Double)
object ChartPoint {
  implicit val format: OFormat[ChartPoint] = Json.format[ChartPoint]
}

final case class Series(name: String, data: Seq[ChartPoint])
object Series {
  implicit val format: OFormat[Series] = Json.format[Series]
}

class ChartService @Inject()(getSqlQueries: GetSqlQueries)(implicit ec: ExecutionContext) {

  def priceHistoryData(nodeId: String): Future[Seq[Series]] =
    getSqlQueries.findPricesForStation(nodeId).map { prices =>
      prices
        .groupBy(_.fuelType)
        .toSeq
        .map { case (fuelType, pts) =>
          Series(
            fuelType.displayText,
            pts
              .sortBy(_.priceChangeEffectiveTimestamp)
              .map(p =>
                ChartPoint(
                  p.priceChangeEffectiveTimestamp.toEpochMilli,
                  p.price
                )
              )
          )
        }
    }
}