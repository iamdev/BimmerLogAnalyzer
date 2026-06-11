package com.bimmerloganalyzer.ui.components

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ChartSeries(
    val label: String,
    val entries: List<Entry>,
    val color: Int,
    val yAxisRight: Boolean = false,
)

@Composable
fun OBDLineChart(
    series: List<ChartSeries>,
    xLabel: String,
    yLabel: String,
    yLabelRight: String = "",
    /** If non-null, X axis shows HH:mm:ss offset from this start time */
    startTime: LocalDateTime? = null,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textColor = Color.LTGRAY
                    gridColor = Color.DKGRAY
                    axisLineColor = Color.LTGRAY
                    granularity = 1f
                    labelRotationAngle = -30f
                }
                axisLeft.apply {
                    textColor = Color.LTGRAY
                    gridColor = Color.DKGRAY
                    axisLineColor = Color.LTGRAY
                }
                axisRight.apply {
                    textColor = Color.LTGRAY
                    gridColor = Color.TRANSPARENT
                    axisLineColor = Color.LTGRAY
                    isEnabled = false
                }
                legend.apply {
                    textColor = Color.WHITE
                    isEnabled = true
                }
            }
        },
        update = { chart ->
            // X axis formatter: show absolute HH:mm:ss if startTime given, else "Xs"
            chart.xAxis.valueFormatter = if (startTime != null) {
                val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
                val startEpochSec = startTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val epochSec = startEpochSec + value.toLong()
                        val dt = java.time.Instant.ofEpochSecond(epochSec)
                            .atZone(ZoneId.systemDefault()).toLocalDateTime()
                        return dt.format(fmt)
                    }
                }
            } else {
                object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}s"
                }
            }

            val dataSets = series.map { s ->
                LineDataSet(s.entries, s.label).apply {
                    color = s.color
                    setDrawCircles(false)
                    setDrawValues(false)
                    lineWidth = 1.8f
                    mode = LineDataSet.Mode.LINEAR
                    axisDependency = if (s.yAxisRight) YAxis.AxisDependency.RIGHT
                                     else YAxis.AxisDependency.LEFT
                }
            }
            chart.data = LineData(dataSets)
            chart.axisRight.isEnabled = yLabelRight.isNotEmpty()
            chart.invalidate()
        }
    )
}
