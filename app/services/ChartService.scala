package services

import models.FuelStation

import javax.inject.*
import org.knowm.xchart.*
import org.knowm.xchart.style.markers.SeriesMarkers
import org.knowm.xchart.VectorGraphicsEncoder.VectorGraphicsFormat
import play.api.libs.json.{Json, OFormat}

import java.awt.Color
import queries.GetSqlQueries

import java.io.ByteArrayOutputStream
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

  def priceHistoryChart(fuelStation: FuelStation): Future[Array[Byte]] = {

    val colors = List(Color.RED, Color.BLUE, Color.GREEN, Color.BLACK, Color.GRAY)

    getSqlQueries.findPricesForStation(fuelStation.nodeId).map { prices =>

      val chart = new XYChartBuilder()
        .width(2000)
        .height(800)
        .title(s"Fuel Prices for ${fuelStation.tradingName} (${fuelStation.location.postcode})")
        .xAxisTitle("Date")
        .yAxisTitle("Price (pences)")
        .build()

      val styler = chart.getStyler

      // ✅ IMPORTANT: use Date axis mode
      styler.setPlotContentSize(0.9)
      styler.setDatePattern("dd MMM yyyy")
      styler.setXAxisLabelRotation(45)
      styler.setChartFontColor(Color.BLACK)

      styler.setPlotMargin(20)
      styler.setChartTitleVisible(true)

      styler.setChartTitleFont(
        new java.awt.Font("Arial", java.awt.Font.BOLD, 38)
      )

      styler.setAxisTickLabelsFont(
        new java.awt.Font("Arial", java.awt.Font.PLAIN, 38)
      )

      styler.setAxisTitleFont(
        new java.awt.Font("Arial", java.awt.Font.BOLD, 40)
      )

      styler.setLegendFont(
        new java.awt.Font("Arial", java.awt.Font.PLAIN, 38)
      )

      styler.setLegendPosition(org.knowm.xchart.style.Styler.LegendPosition.OutsideS)
      styler.setLegendLayout(org.knowm.xchart.style.Styler.LegendLayout.Horizontal)

      val grouped = prices.groupBy(_.fuelType).toSeq

      grouped.zipWithIndex.foreach { case ((fuelType, pts), idx) =>

        val xData = pts
          .map(_.priceChangeEffectiveTimestamp.toEpochMilli.toDouble)
          .sorted
          .toArray

        val yData = pts
          .sortBy(_.priceChangeEffectiveTimestamp)
          .map(_.price)
          .toArray

        val series = chart.addSeries(fuelType.displayText, xData, yData)

        // line + dots
        series.setMarker(SeriesMarkers.CIRCLE)
        series.setLineColor(colors(idx % colors.size))
        series.setLineWidth(2.0f)
      }

      val baos = new ByteArrayOutputStream()

      VectorGraphicsEncoder.saveVectorGraphic(
        chart,
        baos,
        VectorGraphicsFormat.SVG
      )

      baos.toByteArray
    }
  }

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