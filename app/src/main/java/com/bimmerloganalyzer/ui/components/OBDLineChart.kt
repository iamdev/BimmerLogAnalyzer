package com.bimmerloganalyzer.ui.components

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

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
                    setDrawLabels(true)
                }
                axisRight.apply {
                    textColor = Color.LTGRAY
                    gridColor = Color.TRANSPARENT
                    axisLineColor = Color.LTGRAY
                    isEnabled = yLabelRight.isNotEmpty()
                }
                legend.apply {
                    textColor = Color.WHITE
                    isEnabled = true
                }
            }
        },
        update = { chart ->
            val dataSets = series.map { s ->
                LineDataSet(s.entries, s.label).apply {
                    color = s.color
                    setDrawCircles(false)
                    setDrawValues(false)
                    lineWidth = 1.8f
                    mode = LineDataSet.Mode.LINEAR
                    axisDependency = if (s.yAxisRight)
                        com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
                    else
                        com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
                }
            }
            chart.data = LineData(dataSets)
            chart.axisRight.isEnabled = yLabelRight.isNotEmpty()
            chart.invalidate()
        }
    )
}
