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
    /** Dashed line — used to mark estimated / approximated data. */
    val dashed: Boolean = false,
    /** Draw a circle at each point (e.g. measured dyno samples). */
    val drawCircles: Boolean = false,
    /** Draw only circles, no connecting line. */
    val circlesOnly: Boolean = false,
    val lineWidth: Float = 1.8f,
)

@Composable
fun OBDLineChart(
    series: List<ChartSeries>,
    xLabel: String,
    yLabel: String,
    yLabelRight: String = "",
    /** If non-null, X axis shows HH:mm:ss offset from this start time */
    startTime: LocalDateTime? = null,
    /** X axis represents RPM (dyno charts) — changes marker formatting. */
    xIsRpm: Boolean = false,
    /** Increment to reset zoom/pan back to fit-screen. */
    resetZoomKey: Int = 0,
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
                isScaleXEnabled = true
                isScaleYEnabled = true
                setPinchZoom(true)
                isDoubleTapToZoomEnabled = true
                setDrawGridBackground(false)
                isHighlightPerTapEnabled = true

                // Tap-to-read tooltip
                marker = ChartMarkerView(ctx, startTime, xIsRpm).also { it.chartView = this }

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
            // Marker needs to know current X semantics
            chart.marker = ChartMarkerView(chart.context, startTime, xIsRpm).also { it.chartView = chart }

            // X axis formatter: clock time, RPM, or seconds
            chart.xAxis.valueFormatter = when {
                xIsRpm -> object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}"
                }
                startTime != null -> {
                    val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
                    val startEpochSec = startTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                    object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val epochSec = startEpochSec + value.toLong()
                            return java.time.Instant.ofEpochSecond(epochSec)
                                .atZone(ZoneId.systemDefault()).toLocalDateTime().format(fmt)
                        }
                    }
                }
                else -> object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}s"
                }
            }

            val dataSets = series.map { s ->
                // Attach the series label to each entry so the marker can show it
                s.entries.forEach { if (it.data == null) it.data = s.label }
                LineDataSet(s.entries, s.label).apply {
                    color = s.color
                    setDrawValues(false)
                    lineWidth = s.lineWidth
                    mode = LineDataSet.Mode.LINEAR
                    axisDependency = if (s.yAxisRight) YAxis.AxisDependency.RIGHT
                                     else YAxis.AxisDependency.LEFT

                    if (s.circlesOnly) {
                        setDrawCircles(true)
                        circleRadius = 3f
                        setCircleColor(s.color)
                        setColor(Color.TRANSPARENT) // hide the connecting line
                    } else {
                        setDrawCircles(s.drawCircles)
                        if (s.drawCircles) {
                            circleRadius = 2.5f
                            setCircleColor(s.color)
                        }
                        if (s.dashed) enableDashedLine(12f, 8f, 0f) else disableDashedLine()
                    }
                    setDrawHighlightIndicators(true)
                    highLightColor = Color.WHITE
                }
            }
            chart.data = LineData(dataSets)
            chart.axisRight.isEnabled = yLabelRight.isNotEmpty()

            // Reset zoom when the trigger changes
            val prevKey = chart.tag as? Int ?: 0
            if (resetZoomKey != prevKey) {
                chart.fitScreen()
                chart.tag = resetZoomKey
            }

            chart.invalidate()
        }
    )
}
