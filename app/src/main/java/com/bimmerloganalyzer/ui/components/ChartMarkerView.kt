package com.bimmerloganalyzer.ui.components

import android.content.Context
import android.widget.TextView
import com.bimmerloganalyzer.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tooltip shown when the user taps a data point. Renders the X value (clock time
 * if [startTime] is set, RPM/seconds otherwise) and the Y value, plus the series
 * label so multi-line charts are unambiguous.
 */
class ChartMarkerView(
    context: Context,
    private val startTime: LocalDateTime?,
    private val xIsRpm: Boolean,
) : MarkerView(context, R.layout.chart_marker) {

    private val text: TextView = findViewById(R.id.marker_text)
    private val clockFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    override fun refreshContent(e: Entry, highlight: Highlight) {
        val xLabel = when {
            xIsRpm -> "${e.x.toInt()} rpm"
            startTime != null -> {
                val epochSec = startTime.atZone(ZoneId.systemDefault()).toEpochSecond() + e.x.toLong()
                Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault())
                    .toLocalDateTime().format(clockFmt)
            }
            else -> "${e.x.toInt()} s"
        }
        val label = (e.data as? String)?.let { "$it\n" } ?: ""
        text.text = "$label$xLabel\n${"%.1f".format(e.y)}"
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF = MPPointF(-(width / 2f), -height.toFloat())
}
